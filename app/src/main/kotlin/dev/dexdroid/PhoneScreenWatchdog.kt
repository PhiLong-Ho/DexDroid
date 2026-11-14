package dev.dexdroid

import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Independent shell process and the ONLY physical power writer. Never kill this process for a
 * client timeout: its exclusive lock must survive blocked or unconfirmed restoration.
 */
internal object PhoneScreenWatchdog {
    @Volatile private var reportingEpoch = 0L

    @JvmStatic
    fun main(args: Array<String>) {
        if (Process.myUid() != 2000 || args.isNotEmpty()) exitProcess(1)
        try {
            execute()
        } catch (_: Throwable) {
            status(
                PhoneScreenResult(
                    PhoneScreenResult.State.UNKNOWN,
                    PhoneScreenResult.Reason.FAILED,
                    reportingEpoch,
                )
            )
        }
        // Some ROM native libraries crash during normal app_process shutdown.
        exitProcess(0)
    }

    private class Mailbox {
        val ready = CountDownLatch(1)
        @Volatile var start: PhoneScreenMessage.Start? = null
        @Volatile var lease: PhoneScreenLease? = null
        @Volatile var terminal: PhoneScreenResult.Reason? = null

        @Synchronized
        fun accept(message: PhoneScreenMessage?): Boolean {
            if (terminal != null) return false
            when (message) {
                is PhoneScreenMessage.Start -> {
                    if (
                        start != null ||
                            (message.off &&
                                !PhoneScreenLease.validDeadline(
                                    message.deadline,
                                    SystemClock.elapsedRealtime(),
                                ))
                    ) {
                        close(PhoneScreenResult.Reason.INVALID_REQUEST)
                        return false
                    }
                    start = message
                    if (message.off) lease = PhoneScreenLease(message.epoch, message.deadline)
                    ready.countDown()
                }
                is PhoneScreenMessage.Renew -> {
                    val current = lease
                    if (current == null) {
                        close(PhoneScreenResult.Reason.INVALID_REQUEST)
                        return false
                    }
                    current.renew(message.epoch, message.deadline, SystemClock.elapsedRealtime())
                }
                is PhoneScreenMessage.Close -> {
                    close(message.reason)
                    return false
                }
                null -> {
                    close(PhoneScreenResult.Reason.INVALID_REQUEST)
                    return false
                }
            }
            return true
        }

        @Synchronized
        fun close(reason: PhoneScreenResult.Reason) {
            if (terminal == null) terminal = lease?.reason(SystemClock.elapsedRealtime()) ?: reason
            lease?.close(terminal!!)
            ready.countDown()
        }

        fun reason(): PhoneScreenResult.Reason? =
            terminal ?: lease?.reason(SystemClock.elapsedRealtime())
    }

    private fun execute() {
        val mailbox = Mailbox()
        Thread(
                {
                    try {
                        while (true) {
                            val line = readLine(System.`in`)
                            if (line == null) {
                                mailbox.close(PhoneScreenResult.Reason.OWNER_LOST)
                                break
                            }
                            if (!mailbox.accept(PhoneScreenMessage.parse(line))) break
                        }
                    } catch (_: Exception) {
                        mailbox.close(PhoneScreenResult.Reason.INVALID_REQUEST)
                    }
                },
                "DexDroid-panel-pipe",
            )
            .apply {
                isDaemon = true
                start()
            }
        if (!mailbox.ready.await(2, TimeUnit.SECONDS))
            mailbox.close(PhoneScreenResult.Reason.CLOSED)
        val request = mailbox.start ?: return
        reportingEpoch = request.epoch
        val panel: Panel
        val lockFile: RandomAccessFile
        try {
            panel = Panel.discover(request)
            lockFile =
                RandomAccessFile(
                    "/data/local/tmp/dexdroid-panel-${panel.boot}-${panel.id}.lock",
                    "rw",
                )
        } catch (_: Exception) {
            status(
                PhoneScreenResult(
                    PhoneScreenResult.State.UNAVAILABLE,
                    PhoneScreenResult.Reason.UNSUPPORTED,
                    request.epoch,
                )
            )
            return
        }
        System.out.println("T ${request.epoch} ${panel.id} ${panel.boot}")
        System.out.flush()
        lockFile.use { file ->
            var exclusive = file.channel.tryLock()
            if (exclusive == null) {
                if (!request.off && request.physicalId != null) {
                    status(
                        PhoneScreenResult(
                            PhoneScreenResult.State.UNKNOWN,
                            PhoneScreenResult.Reason.BUSY,
                            request.epoch,
                        )
                    )
                    // One NORMAL-only replacement waits for the original actuator. Before
                    // acquiring ownership, loss of its client can safely discard this waiter.
                    while (exclusive == null) {
                        val terminal = mailbox.reason()
                        if (terminal != null) {
                            status(
                                PhoneScreenResult(
                                    PhoneScreenResult.State.UNKNOWN,
                                    terminal,
                                    request.epoch,
                                )
                            )
                            return
                        }
                        SystemClock.sleep(200)
                        exclusive = file.channel.tryLock()
                    }
                } else {
                    status(
                        PhoneScreenResult(
                            PhoneScreenResult.State.UNAVAILABLE,
                            PhoneScreenResult.Reason.BUSY,
                            request.epoch,
                        )
                    )
                    return
                }
            }
            exclusive.use {
                // From the first NORMAL attempt onward, only confirmed physical On releases
                // this lock. A throwing native call may already have changed panel state.
                var restoreReason = mailbox.reason() ?: PhoneScreenResult.Reason.NONE
                try {
                    panel.normal()
                    if (panel.readback() == PhoneScreenResult.State.ON) {
                        if (!request.off) {
                            status(
                                PhoneScreenResult(PhoneScreenResult.State.ON, lease = request.epoch)
                            )
                            return
                        }
                        panel.validate(true)
                        if (
                            mailbox.lease?.runOff(
                                SystemClock::elapsedRealtime,
                                panel::off,
                                panel::normal,
                            ) == true
                        ) {
                            // No readback or other native work may get in front of a latched close.
                            if (mailbox.reason() == null) {
                                val state = panel.readback()
                                if (state == PhoneScreenResult.State.OFF) {
                                    status(PhoneScreenResult(state, lease = request.epoch))
                                    while (mailbox.reason() == null) SystemClock.sleep(25)
                                } else {
                                    mailbox.close(PhoneScreenResult.Reason.READBACK)
                                }
                            }
                        }
                    } else {
                        mailbox.close(PhoneScreenResult.Reason.READBACK)
                    }
                } catch (_: Throwable) {
                    mailbox.close(PhoneScreenResult.Reason.FAILED)
                }
                restoreReason = mailbox.reason() ?: restoreReason
                status(
                    PhoneScreenResult(
                        PhoneScreenResult.State.PENDING,
                        PhoneScreenResult.Reason.RESTORING,
                        request.epoch,
                    )
                )
                var backoff = 100L
                while (true) {
                    try {
                        panel.normal() // Same boot/internal token; external output may be gone.
                        if (panel.readback() == PhoneScreenResult.State.ON) {
                            status(
                                PhoneScreenResult(
                                    PhoneScreenResult.State.ON,
                                    restoreReason,
                                    request.epoch,
                                    restoreReason in
                                        setOf(
                                            PhoneScreenResult.Reason.EXPIRED,
                                            PhoneScreenResult.Reason.OWNER_LOST,
                                            PhoneScreenResult.Reason.CLOSED,
                                        ),
                                )
                            )
                            return
                        }
                    } catch (_: Throwable) {
                        // Retain exclusivity. A timeout is never proof that NORMAL completed.
                    }
                    status(
                        PhoneScreenResult(
                            PhoneScreenResult.State.UNKNOWN,
                            PhoneScreenResult.Reason.READBACK,
                            request.epoch,
                        )
                    )
                    SystemClock.sleep(backoff)
                    backoff = (backoff * 2).coerceAtMost(2000L)
                }
            }
        }
    }

    private fun status(result: PhoneScreenResult) {
        System.out.println(
            "S ${result.state.name} ${result.reason.name} ${result.lease} ${if (result.safeAutoRestore) 1 else 0}"
        )
        System.out.flush()
    }

    /** Never BufferedReader.readLine(): a malformed peer must not allocate an unbounded line. */
    private fun readLine(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value == -1) {
                check(bytes.size() == 0)
                return null
            }
            if (value == 10) return bytes.toString(Charsets.US_ASCII.name())
            check(value in 32..126 && bytes.size() < PhoneScreenMessage.MAX_LINE)
            bytes.write(value)
        }
    }

    // Shell-only optional platform capability; identity/preflight failures admit no OFF.
    @android.annotation.SuppressLint("PrivateApi")
    private class Panel(
        val id: Long,
        val boot: String,
        private val external: Long?,
        private val display: Int,
        private val global: Any,
        private val controls: Class<*>,
        private val token: IBinder,
        private val power: Method,
    ) {
        fun validate(requireExternal: Boolean) {
            check(readBoot() == boot)
            val info = info(global, 0)
            check(info.getClassField("type") == 1 && info.getClassField("uniqueId") == "local:$id")
            val ids = controls.getMethod("getPhysicalDisplayIds").invoke(null) as LongArray
            check(ids.count { it == id } == 1)
            check(
                controls
                    .getMethod("getPhysicalDisplayToken", Long::class.javaPrimitiveType)
                    .invoke(null, id) == token
            )
            if (requireExternal) {
                check(display > 0)
                info(global, display)
                check(external != null && external != id && ids.contains(external))
                check(externalIds(global).contains(external))
            }
        }

        fun normal() {
            validate(false)
            power.invoke(null, token, 2)
        }

        fun off() {
            // The caller has just revalidated identity and atomically admitted the one OFF.
            power.invoke(null, token, 0)
        }

        fun readback(): PhoneScreenResult.State {
            val targeted = dump(listOf("dumpsys", "-t", "1", "SurfaceFlinger", "--displays"))
            val state = PhoneScreenReadback.parse(targeted.first, id, targeted.second)
            if (state != PhoneScreenResult.State.UNKNOWN) return state
            // Older releases lack --displays; full dumps remain strictly bounded/private.
            if (android.os.Build.VERSION.SDK_INT > 30) return state
            val legacy = dump(listOf("dumpsys", "-t", "1", "SurfaceFlinger"))
            return PhoneScreenReadback.parse(legacy.first, id, legacy.second)
        }

        companion object {
            @Volatile private var stalledRead: Thread? = null

            fun discover(request: PhoneScreenMessage.Start): Panel {
                val display = request.display
                val off = request.off
                val global =
                    Class.forName("android.hardware.display.DisplayManagerGlobal")
                        .getMethod("getInstance")
                        .invoke(null)!!
                val internal = info(global, 0)
                check(internal.getClassField("type") == 1)
                val unique = internal.getClassField("uniqueId") as String
                check(unique.matches(Regex("local:[0-9]+")))
                val id = unique.removePrefix("local:").toLong()
                val boot = readBoot()
                check(request.acceptsIdentity(boot, id))
                val controls = controls()
                val external =
                    if (off) externalIds(global).filter { it != id }.singleOrNull() else null
                val token =
                    controls
                        .getMethod("getPhysicalDisplayToken", Long::class.javaPrimitiveType)
                        .invoke(null, id) as? IBinder ?: error("No token")
                val power =
                    Class.forName("android.view.SurfaceControl")
                        .getMethod(
                            "setDisplayPowerMode",
                            IBinder::class.java,
                            Int::class.javaPrimitiveType,
                        )
                return Panel(id, boot, external, display, global, controls, token, power).also {
                    it.validate(off)
                }
            }

            // main() permits shell UID 2000 only. Missing/restricted native fallback
            // APIs fail capability discovery before any OFF request is admitted.
            @android.annotation.SuppressLint("BlockedPrivateApi")
            private fun controls(): Class<*> {
                val surface = Class.forName("android.view.SurfaceControl")
                try {
                    surface.getMethod("getPhysicalDisplayToken", Long::class.javaPrimitiveType)
                    return surface
                } catch (_: NoSuchMethodException) {
                    val create =
                        Class.forName("com.android.internal.os.ClassLoaderFactory")
                            .getDeclaredMethod(
                                "createClassLoader",
                                String::class.java,
                                String::class.java,
                                String::class.java,
                                ClassLoader::class.java,
                                Int::class.javaPrimitiveType,
                                Boolean::class.javaPrimitiveType,
                                String::class.java,
                            )
                    val loader =
                        create.invoke(
                            null,
                            System.getenv("SYSTEMSERVERCLASSPATH"),
                            null,
                            null,
                            ClassLoader.getSystemClassLoader(),
                            0,
                            true,
                            null,
                        ) as ClassLoader
                    val control = loader.loadClass("com.android.server.display.DisplayControl")
                    val load =
                        Runtime::class
                            .java
                            .getDeclaredMethod(
                                "loadLibrary0",
                                Class::class.java,
                                String::class.java,
                            )
                    load.isAccessible = true
                    load.invoke(Runtime.getRuntime(), control, "android_servers")
                    return control
                }
            }

            private fun info(global: Any, display: Int): Any =
                global.javaClass
                    .getMethod("getDisplayInfo", Int::class.javaPrimitiveType)
                    .invoke(global, display) ?: error("Missing display")

            private fun externalIds(global: Any): List<Long> {
                val ids =
                    try {
                        global.javaClass.getMethod("getDisplayIds").invoke(global) as IntArray
                    } catch (_: NoSuchMethodException) {
                        global.javaClass
                            .getMethod("getDisplayIds", Boolean::class.javaPrimitiveType)
                            .invoke(global, false) as IntArray
                    }
                return ids.map { info(global, it) }
                    .filter { it.getClassField("type") == 2 }
                    .mapNotNull {
                        (it.getClassField("uniqueId") as? String)
                            ?.takeIf { unique -> unique.startsWith("local:") }
                            ?.removePrefix("local:")
                            ?.toLongOrNull()
                    }
            }

            private fun Any.getClassField(name: String): Any? = javaClass.getField(name).get(this)

            private fun readBoot(): String =
                File("/proc/sys/kernel/random/boot_id").readText(Charsets.US_ASCII).trim().also {
                    check(it.matches(Regex("[a-f0-9-]{36}")))
                }

            private fun dump(command: List<String>): Pair<String, Boolean> {
                // If even a killed dump has a stuck pipe, retain one reader rather than
                // allocating another thread/process on every restoration retry.
                if (stalledRead?.isAlive == true) return "" to false
                val process = ProcessBuilder(command).redirectErrorStream(true).start()
                val output = ByteArrayOutputStream()
                val complete = AtomicBoolean(true)
                val reader =
                    Thread(
                            {
                                try {
                                    val buffer = ByteArray(4096)
                                    process.inputStream.use { stream ->
                                        while (true) {
                                            val count = stream.read(buffer)
                                            if (count < 0) break
                                            if (
                                                output.size() + count >
                                                    PhoneScreenReadback.MAX_BYTES
                                            ) {
                                                complete.set(false)
                                                break
                                            }
                                            output.write(buffer, 0, count)
                                        }
                                    }
                                } catch (_: Exception) {
                                    complete.set(false)
                                }
                            },
                            "DexDroid-panel-readback",
                        )
                        .apply {
                            isDaemon = true
                            start()
                        }
                val ended = process.waitFor(1250, TimeUnit.MILLISECONDS)
                if (!ended) process.destroyForcibly()
                reader.join(150)
                if (reader.isAlive || !ended || (ended && process.exitValue() != 0))
                    complete.set(false)
                if (reader.isAlive) stalledRead = reader
                return if (reader.isAlive) "" to false
                else output.toString(Charsets.UTF_8.name()) to complete.get()
            }
        }
    }
}
