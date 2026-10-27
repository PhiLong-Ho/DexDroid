package dev.dexdroid

import android.os.Handler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Owns only read-only metadata work; neither the desktop nor its input/audio workers. */
internal class CapabilityReader(private val main: Handler, private val changed: () -> Unit) {
    data class Metadata(
        val shizuku: DeviceCapabilities.Shizuku,
        val monitor: DeviceCapabilities.Monitor?,
        val checkedAt: Long,
        val partial: Boolean,
    )

    private val reads = CapabilityRefresh<Metadata>()
    private val worker =
        ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(1))
    private var source: (() -> Metadata)? = null
    private var deadline: Runnable? = null
    private var closed = false
    val metadata
        get() = reads.value

    val observedShizuku
        get() =
            when (reads.phase) {
                CapabilityRefresh.Phase.READY -> metadata?.shizuku
                CapabilityRefresh.Phase.FAILED -> DeviceCapabilities.Shizuku.UNKNOWN
                else -> null
            }

    val failed
        get() = reads.phase == CapabilityRefresh.Phase.FAILED

    val refreshing
        get() = reads.phase == CapabilityRefresh.Phase.READING || reads.blocked

    fun owner(next: Any, read: () -> Metadata) {
        source = read
        reads.changeOwner(next)
        start(reads.takePending())
    }

    fun open(): Any = reads.attach().also { refresh() }

    fun refresh() {
        start(reads.request())
        changed()
    }

    fun closePage(page: Any) {
        reads.detach(page)
        if (!reads.visible) {
            deadline?.let(main::removeCallbacks)
            deadline = null
        }
    }

    fun feedback(): String {
        val previous =
            metadata?.let {
                "Read at ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(it.checkedAt))}."
            }
        if (reads.blocked)
            return "An earlier read has not finished. No additional read can start until it exits. " +
                (previous?.let { "Showing earlier observations: $it" }
                    ?: "Current metadata remains Not checked.")
        return when (reads.phase) {
            CapabilityRefresh.Phase.IDLE ->
                "Not checked. Open or refresh this report to read current metadata."
            CapabilityRefresh.Phase.READING ->
                "Refreshing… " + (previous?.let { "Earlier observations: $it" } ?: "")
            CapabilityRefresh.Phase.FAILED ->
                "Could not refresh. " +
                    (previous?.let { "Showing earlier observations: $it" }
                        ?: "Missing observations remain Not checked.")
            CapabilityRefresh.Phase.READY ->
                previous.orEmpty() +
                    if (metadata?.partial == true)
                        " Some metadata could not be read; those facts remain Not checked."
                    else " Current session observations only."
        }
    }

    private fun start(request: CapabilityRefresh.Request?) {
        if (request == null || closed) return
        val read = checkNotNull(source)
        val timeout = Runnable { if (reads.timeout(request)) changed() }
        deadline?.let(main::removeCallbacks)
        deadline = timeout
        main.postDelayed(timeout, 2000)
        try {
            worker.execute {
                val result =
                    try {
                        read()
                    } catch (_: Exception) {
                        null
                    }
                main.post {
                    main.removeCallbacks(timeout)
                    if (closed) return@post
                    if (deadline === timeout) deadline = null
                    val next = reads.complete(request, result)
                    changed()
                    start(next)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            main.removeCallbacks(timeout)
            reads.complete(request, null)
            changed()
        }
    }

    fun close() {
        closed = true
        reads.close()
        deadline?.let(main::removeCallbacks)
        deadline = null
        source = null
        worker.shutdownNow()
    }
}
