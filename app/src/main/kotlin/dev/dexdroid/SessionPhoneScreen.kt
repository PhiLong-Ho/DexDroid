package dev.dexdroid

import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Service-owned intent. The privileged independent actuator owns actual panel power. */
internal class SessionPhoneScreen(
    private val main: Handler,
    private val keepOffAllowed: () -> Boolean,
    private val changed: () -> Unit,
) {
    data class Status(
        val result: PhoneScreenResult =
            PhoneScreenResult(PhoneScreenResult.State.UNKNOWN, PhoneScreenResult.Reason.CHECKING),
        val possibleOff: Boolean = false,
        val pending: Boolean = false,
        val restoring: Boolean = false,
        val message: String = "Enable the button to check phone screen control",
    )

    private class Target(
        val bridge: IDeskService,
        val display: Int,
        val boot: String = "",
        val physical: Long = -1,
        val owner: IBinder = Binder(),
        val lease: Long = SystemClock.elapsedRealtimeNanos().coerceAtLeast(1),
    )

    private data class Request(
        val target: Target,
        val command: Int,
        val serial: Long,
        val deadline: Long,
    )

    var status = Status()
        private set

    private var target: Target? = null
    private var desiredOff = false
    private var serial = 0L
    private var lastRequest = 0L
    private var closed = false
    private val lock = Any()
    private var pending: Request? = null
    private var running = false
    private val worker =
        ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(1)) {
            Thread(it, "DexDroid-phone-screen")
        }
    private val tick = Runnable { poll() }

    fun check(bridge: IDeskService, display: Int) {
        if (closed || status.possibleOff || status.pending) return
        target = Target(bridge, display)
        desiredOff = false
        serial++
        lastRequest = SystemClock.elapsedRealtime()
        status = Status(pending = true, message = "Checking phone screen control…")
        submit(PhoneScreenResult.CHECK)
        schedule()
        changed()
    }

    fun off(bridge: IDeskService, display: Int) {
        if (closed || status.possibleOff || status.pending || !status.result.hasReceipt) return
        target = Target(bridge, display, status.result.boot, status.result.physical)
        desiredOff = true
        serial++
        lastRequest = SystemClock.elapsedRealtime()
        status =
            status.copy(
                possibleOff = true,
                pending = true,
                restoring = false,
                message = "Turning phone screen off…",
            )
        submit(PhoneScreenResult.OFF)
        schedule()
        changed()
    }

    fun restore(retry: Boolean = false) {
        if (closed || target == null || (!status.possibleOff && !status.pending)) return
        if (status.restoring && !retry) return
        desiredOff = false // Stop renewing immediately, even if dispatch is blocked.
        serial++
        lastRequest = SystemClock.elapsedRealtime()
        status = status.copy(pending = true, restoring = true, message = "Turning phone screen on…")
        submit(PhoneScreenResult.ON)
        schedule()
        changed()
    }

    fun reconnect(bridge: IDeskService, display: Int) {
        val old = target ?: return
        if (!status.possibleOff || (old.bridge === bridge && old.display == display)) return
        target = Target(bridge, display, old.boot, old.physical, old.owner, old.lease)
        desiredOff = false
        serial++
        lastRequest = SystemClock.elapsedRealtime()
        status =
            status.copy(
                pending = true,
                restoring = true,
                message = "Reconnecting phone screen recovery…",
            )
        submit(PhoneScreenResult.ON)
        schedule()
        changed()
    }

    fun belongsTo(bridge: IDeskService?, display: Int): Boolean =
        target?.let { it.bridge === bridge && it.display == display } ?: true

    fun recoveryLease(): Long = target?.lease ?: 0

    private fun schedule() {
        main.removeCallbacks(tick)
        if (!closed && target != null) main.postDelayed(tick, 1000)
    }

    private fun poll() {
        if (closed || target == null) return
        if (desiredOff && !keepOffAllowed()) {
            restore()
            return
        }
        if (status.pending && SystemClock.elapsedRealtime() - lastRequest >= 3000) {
            if (desiredOff) {
                desiredOff = false
                submit(PhoneScreenResult.ON)
            }
            status =
                status.copy(
                    pending = false,
                    message =
                        if (status.possibleOff)
                            "Phone screen state unconfirmed · Use Turn phone screen on"
                        else "Phone screen control check unconfirmed · Retry",
                )
            changed()
        }
        submit(
            if (!desiredOff) PhoneScreenResult.POLL
            else if (status.result.reason == PhoneScreenResult.Reason.BUSY) PhoneScreenResult.OFF
            else PhoneScreenResult.RENEW
        )
        schedule()
    }

    private fun submit(command: Int) {
        val current = target ?: return
        val request = Request(current, command, serial, SystemClock.elapsedRealtime() + 5000)
        synchronized(lock) {
            val previous = pending
            val passive = command == PhoneScreenResult.RENEW || command == PhoneScreenResult.POLL
            if (
                passive &&
                    previous != null &&
                    previous.command in
                        listOf(PhoneScreenResult.OFF, PhoneScreenResult.ON, PhoneScreenResult.CHECK)
            )
                return
            pending = request
            if (!running) {
                running = true
                worker.execute { drain() }
            }
        }
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
            val result =
                try {
                    PhoneScreenResult.fromBundle(
                        request.target.bridge.phoneScreen(
                            request.command,
                            request.target.display,
                            request.target.lease,
                            request.deadline,
                            request.target.owner,
                            request.target.boot.takeIf(String::isNotEmpty),
                            request.target.physical,
                        )
                    )
                } catch (_: Exception) {
                    PhoneScreenResult(
                        PhoneScreenResult.State.UNKNOWN,
                        PhoneScreenResult.Reason.FAILED,
                        request.target.lease,
                    )
                }
            main.post { accept(request, result) }
        }
    }

    private fun accept(request: Request, result: PhoneScreenResult) {
        if (closed || target !== request.target || serial != request.serial) return
        if (result.lease != request.target.lease) return
        if (
            result.state == PhoneScreenResult.State.ON &&
                (!result.hasReceipt ||
                    (request.target.physical >= 0 &&
                        (result.boot != request.target.boot ||
                            result.physical != request.target.physical)))
        ) {
            status =
                status.copy(
                    pending = false,
                    message = "Phone screen recovery identity unconfirmed · Retry",
                )
            desiredOff = false
            changed()
            return
        }
        val value =
            when (result.state) {
                PhoneScreenResult.State.ON -> {
                    desiredOff = false
                    target = null
                    main.removeCallbacks(tick)
                    Status(
                        result,
                        message =
                            if (result.safeAutoRestore) "Phone screen restored for safety"
                            else "Phone screen on",
                    )
                }
                PhoneScreenResult.State.OFF ->
                    status.copy(
                        result = result,
                        possibleOff = true,
                        pending = !desiredOff,
                        message =
                            if (desiredOff) "Phone screen off · Restore in monitor Controls"
                            else "Turning phone screen on…",
                    )
                PhoneScreenResult.State.PENDING -> status.copy(result = result)
                PhoneScreenResult.State.UNAVAILABLE -> {
                    desiredOff = false
                    if (status.possibleOff) {
                        if (!status.restoring) submit(PhoneScreenResult.ON)
                        status.copy(
                            result = result,
                            pending = false,
                            restoring = true,
                            message = "Phone screen recovery unconfirmed · Use Turn phone screen on",
                        )
                    } else {
                        target = null
                        main.removeCallbacks(tick)
                        Status(
                            result,
                            message =
                                when (result.reason) {
                                    PhoneScreenResult.Reason.BUSY ->
                                        "Unavailable · Previous phone screen recovery is still running"
                                    PhoneScreenResult.Reason.INVALID_REQUEST ->
                                        "Unavailable · Start an unlocked external desktop first"
                                    else ->
                                        "Unavailable · This Android device does not expose verified phone screen control"
                                },
                        )
                    }
                }
                PhoneScreenResult.State.UNKNOWN -> {
                    if (desiredOff) {
                        desiredOff = false
                        submit(PhoneScreenResult.ON)
                    }
                    status.copy(
                        result = result,
                        pending = false,
                        restoring = status.possibleOff,
                        message =
                            if (status.possibleOff)
                                "Phone screen state unconfirmed · Use Turn phone screen on"
                            else "Could not verify phone screen control · Retry",
                    )
                }
            }
        if (value != status) {
            status = value
            changed()
        }
    }

    fun close() {
        if (closed) return
        restore()
        closed = true
        main.removeCallbacks(tick)
        // Graceful dispatch still delivers the terminal command; never interrupt/kill the actuator.
        worker.shutdown()
    }
}
