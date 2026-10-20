package dev.dexdroid

import android.os.Binder
import android.os.Handler
import java.util.concurrent.Executors

/** One worker, one coalesced intent. Retiring a session always releases its old helper first. */
internal class SessionAudio(private val main: Handler, private val changed: () -> Unit) {
    @Volatile
    var state = SessionAudioState()
        private set

    private class Target(val bridge: IDeskService, val epoch: Long)

    private val lock = Any()
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "DexDroid-audio") }
    private val owner = Binder()
    private var serial = 0L
    private var epoch = 0L
    private var desired: Target? = null
    @Volatile private var active: Target? = null // worker only writes
    private var selection: String? = null
    private var refresh = false
    private var working = false
    private var closing = false
    private var cleanupUncertain = false // worker only
    private val poll = Runnable { refresh() }

    fun begin(bridge: IDeskService) {
        synchronized(lock) {
            if (desired?.bridge === bridge) return
            desired = Target(bridge, ++epoch)
            selection = null
            refresh = true
            serial++
            state = SessionAudioState(message = "Checking audio outputs...", pending = true)
            schedule()
        }
        changed()
    }

    fun select(key: String) {
        synchronized(lock) {
            if (desired == null || state.pending || !state.available || closing) return
            selection = key
            serial++
            state = state.copy(pending = true, message = "Switching default media output...")
            schedule()
        }
        changed()
    }

    fun refresh() {
        synchronized(lock) {
            if (desired == null || closing) return
            refresh = true
            schedule()
        }
    }

    fun end() {
        main.removeCallbacks(poll)
        synchronized(lock) {
            if (desired == null && active == null) return
            desired = null
            selection = null
            refresh = false
            serial++
            state =
                SessionAudioState(message = "Releasing session audio setting...", pending = true)
            schedule()
        }
        changed()
    }

    fun close() {
        end()
        synchronized(lock) {
            closing = true
            schedule()
        }
    }

    private fun schedule() {
        if (working) return
        working = true
        worker.execute { drain() }
    }

    private fun drain() {
        while (true) {
            val target: Target?
            val choice: String?
            val request: Long
            synchronized(lock) {
                target = desired
                choice = selection
                request = serial
                if (active == target && choice == null && !refresh) {
                    working = false
                    if (closing) worker.shutdown()
                    return
                }
                selection = null
                refresh = false
            }
            var result: SessionAudioState
            var transportError = false
            try {
                if (active != target) {
                    active?.let {
                        val ended = SessionAudioState.from(it.bridge.audioEnd(it.epoch))
                        if (ended.uncertain)
                            throw IllegalStateException("Audio cleanup unconfirmed")
                        cleanupUncertain = false
                    }
                    active = null
                    if (target != null) {
                        result =
                            SessionAudioState.from(target.bridge.audioBegin(target.epoch, owner))
                        active = target
                    } else {
                        result =
                            SessionAudioState(
                                message =
                                    if (cleanupUncertain) cleanupMessage()
                                    else "No desktop session. Audio setting released.",
                                uncertain = cleanupUncertain,
                            )
                    }
                } else {
                    result =
                        if (target == null) SessionAudioState()
                        else
                            SessionAudioState.from(
                                if (choice == null) target.bridge.audioQuery(target.epoch)
                                else target.bridge.audioSelect(target.epoch, choice)
                            )
                }
                cleanupUncertain = result.uncertain
            } catch (_: Exception) {
                // A dead helper may have left a global preference behind.
                cleanupUncertain = true
                // Keep the actual old epoch until its end is acknowledged. Never relabel
                // a failed old cleanup or a never-started new epoch as the active owner.
                transportError = true
                result = SessionAudioState(message = cleanupMessage(), uncertain = true)
            }
            val snapshot =
                if (cleanupUncertain)
                    result.copy(uncertain = true, message = result.message + " " + cleanupMessage())
                else result
            main.post {
                synchronized(lock) {
                    if (request != serial || desired != target || closing) return@post
                    state = snapshot
                }
                changed()
                main.removeCallbacks(poll)
                if (target != null) main.postDelayed(poll, 2000)
            }
            if (transportError)
                synchronized(lock) {
                    // Retry only on a new/coalesced intent or the bounded poll, not a busy loop.
                    if (request == serial && desired === target && !refresh && selection == null) {
                        working = false
                        if (closing) worker.shutdown()
                        return
                    }
                }
        }
    }

    private fun cleanupMessage() =
        "Couldn't confirm that the previous audio setting was released. Check Android sound settings or restart the phone."
}
