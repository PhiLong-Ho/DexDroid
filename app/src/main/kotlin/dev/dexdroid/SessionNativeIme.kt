package dev.dexdroid

import android.os.Handler
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** One bounded dispatch worker; helpers own WM work. No queries or callbacks per keystroke. */
internal class SessionNativeIme(private val main: Handler, private val changed: () -> Unit) {
    private val state = NativeImeRequests()
    private val lock = Any()
    private var pending: NativeImeRequests.Request? = null
    private var running = false
    @Volatile private var closed = false
    private var deadline: Runnable? = null
    private val worker =
        ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(1)) { work
            ->
            Thread(work, "DexDroid-ime-dispatch")
        }

    fun configure(
        targets: List<NativeImeRequests.Target>,
        enabled: Boolean,
        retry: Boolean = false,
    ) {
        if (closed) return
        val request = state.configure(targets, enabled, retry) ?: return
        deadline?.let(main::removeCallbacks)
        deadline = null
        if (targets.isNotEmpty()) {
            val timeout = Runnable { if (!closed && state.timeout(request.serial)) changed() }
            deadline = timeout
            main.postDelayed(timeout, 2000)
        }
        synchronized(lock) {
            pending = request.takeIf { it.targets.isNotEmpty() }
            if (pending != null && !running) {
                running = true
                worker.execute { drain() }
            }
        }
        changed()
    }

    private fun drain() {
        while (true) {
            val request =
                synchronized(lock) {
                    pending.also {
                        pending = null
                        if (it == null) running = false
                    }
                } ?: return
            for (target in request.targets) {
                if (closed) return
                val listener =
                    object : IKeyboardPolicyListener.Stub() {
                        override fun onResult(display: Int, serial: Long, value: Int, reason: Int) {
                            if (display != target.display || serial != request.serial) return
                            val result =
                                NativeImeResult(
                                    NativeImeResult.State.entries.getOrNull(value)
                                        ?: NativeImeResult.State.FAILED,
                                    NativeImeResult.Reason.entries.getOrNull(reason)
                                        ?: NativeImeResult.Reason.TRANSPORT,
                                )
                            deliver(target, request.serial, result)
                        }
                    }
                try {
                    (target.owner as IDeskService).keyboardPolicy(
                        target.display,
                        request.enabled,
                        request.serial,
                        listener,
                    )
                } catch (_: Exception) {
                    deliver(
                        target,
                        request.serial,
                        NativeImeResult(
                            NativeImeResult.State.FAILED,
                            NativeImeResult.Reason.TRANSPORT,
                        ),
                    )
                }
            }
        }
    }

    private fun deliver(target: NativeImeRequests.Target, serial: Long, result: NativeImeResult) {
        main.post {
            if (!closed && state.accept(target, serial, result)) {
                if (state.results.none { it.state == NativeImeResult.State.PENDING }) {
                    deadline?.let(main::removeCallbacks)
                    deadline = null
                }
                changed()
            }
        }
    }

    fun summary(): String {
        val prefix = if (state.request.enabled) "On · " else "Off · "
        if (state.results.isEmpty())
            return if (state.request.enabled) prefix + "Start the desktop to check availability"
            else prefix + "Android controls keyboard location"
        val results = state.results
        val descriptions = results.map { it.description() }.distinct()
        return prefix +
            if (descriptions.size == 1) {
                (if (results.size > 1) "Both apps · " else "") + descriptions.single()
            } else "First app: ${results[0].description()} · Other app: ${results[1].description()}"
    }

    fun canRetry() = state.results.any { it.state == NativeImeResult.State.FAILED }

    fun results() = state.results.toList()

    fun close() {
        closed = true
        state.configure(emptyList(), false, true)
        deadline?.let(main::removeCallbacks)
        deadline = null
        synchronized(lock) { pending = null }
        worker.shutdownNow()
    }
}
