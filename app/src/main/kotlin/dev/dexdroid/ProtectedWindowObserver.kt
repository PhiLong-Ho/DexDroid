package dev.dexdroid

import java.io.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.*
import java.util.function.Consumer

/** Shell-only metadata check, isolated from input, task queries and the UI. */
internal class ProtectedWindowObserver(display: Int, listener: Consumer<ProtectedWindows.State?>?) :
    AutoCloseable {
    private val worker: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var occupied: Boolean = false
    @Volatile private var closed: Boolean = false
    @Volatile private var process: Process? = null
    @Volatile private var last: ProtectedWindows.State? = null
    @Volatile private var occupancyEpoch: Int = 0

    init {
        if (android.os.Process.myUid() != 2000) throw SecurityException("Authorized shell required")
        worker.scheduleWithFixedDelay(
            {
                if (closed || !occupied) return@scheduleWithFixedDelay
                val epoch = occupancyEpoch
                var state: ProtectedWindows.State? = ProtectedWindows.State.UNKNOWN
                try {
                    val scan =
                        ProcessBuilder("/system/bin/dumpsys", "-t", "2", "window", "visible-apps")
                            .redirectErrorStream(true)
                            .start()
                    process = scan
                    if (closed) return@scheduleWithFixedDelay
                    InputStreamReader(scan.getInputStream()!!, StandardCharsets.UTF_8)
                        .use({ reader -> state = ProtectedWindows.read(reader, display) })
                    if (!scan.waitFor(1, TimeUnit.SECONDS) || scan.exitValue() != 0)
                        state = ProtectedWindows.State.UNKNOWN
                } catch (unavailable: Exception) {
                    state = ProtectedWindows.State.UNKNOWN
                } finally {
                    val scan = process
                    if (scan != null) scan!!.destroy()
                    process = null
                }
                if (!closed && occupied && epoch == occupancyEpoch && state != last) {
                    last = state
                    listener!!.accept(state)
                }
            },
            0,
            2,
            TimeUnit.SECONDS,
        )
    }

    fun occupied(value: Boolean) {
        if (occupied != value) {
            occupancyEpoch++
            last = null
            occupied = value
        }
    }

    public override fun close() {
        closed = true
        val scan = process
        if (scan != null) scan!!.destroy()
        worker.shutdownNow()
    }
}
