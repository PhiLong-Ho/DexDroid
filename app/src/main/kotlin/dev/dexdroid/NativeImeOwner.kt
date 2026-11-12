package dev.dexdroid

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** One active policy operation and one replaceable intent, including asynchronous cleanup. */
internal class NativeImeOwner(
    private val current: () -> Boolean,
    private val configure: (Boolean) -> NativeImeResult,
    private val cleanup: () -> NativeImeResult?,
) {
    private enum class Life {
        ACTIVE,
        RETIRING,
        RELEASED,
    }

    private data class Request(
        val version: Long,
        val enabled: Boolean,
        val callback: (NativeImeResult) -> Unit,
    )

    private val lock = Any()
    @Volatile private var life = Life.ACTIVE
    private var pending: Request? = null
    private var version = 0L
    private var running = false
    private val worker =
        ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(1)) { work
            ->
            Thread(work, "DexDroid-ime-policy")
        }
    private val retired = CountDownLatch(1)
    @Volatile private var cleanupConfirmed = true

    fun active() = life == Life.ACTIVE && current()

    fun owned() = life != Life.RELEASED && current()

    fun retiring() = life == Life.RETIRING && owned()

    fun request(enabled: Boolean, callback: (NativeImeResult) -> Unit) {
        synchronized(lock) {
            if (life != Life.ACTIVE) return
            pending = Request(++version, enabled, callback)
            if (!running) {
                running = true
                worker.execute { drain() }
            }
        }
    }

    private fun drain() {
        while (true) {
            val decision =
                synchronized(lock) {
                    if (life != Life.ACTIVE) Pair(null, true)
                    else
                        Pair(
                            pending.also {
                                pending = null
                                if (it == null) running = false
                            },
                            false,
                        )
                }
            if (decision.second) {
                try {
                    val result = cleanup()
                    cleanupConfirmed =
                        result == null || result.state == NativeImeResult.State.DEFAULT
                } catch (_: Exception) {
                    cleanupConfirmed = false
                } finally {
                    retired.countDown()
                    worker.shutdown()
                }
                return
            }
            val request = decision.first ?: return
            val result =
                try {
                    configure(request.enabled)
                } catch (_: Exception) {
                    NativeImeResult(NativeImeResult.State.FAILED, NativeImeResult.Reason.TRANSPORT)
                }
            val publish =
                synchronized(lock) {
                    life == Life.ACTIVE && version == request.version && current()
                }
            if (publish)
                try {
                    request.callback(result)
                } catch (_: Exception) {
                    /* Client death is independent of display lifetime. */
                }
        }
    }

    /** The caller can release the display after this finite wait, even if WM IPC is blocked. */
    fun retireAndAwait(timeoutMs: Long = 120): Boolean {
        synchronized(lock) {
            if (life == Life.ACTIVE) {
                life = Life.RETIRING
                pending = null
                version++
                if (!running) {
                    running = true
                    worker.execute { drain() }
                }
            }
        }
        return try {
            retired.await(timeoutMs, TimeUnit.MILLISECONDS) && cleanupConfirmed
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    fun released() {
        life = Life.RELEASED
    }
}
