package dev.dexdroid

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import java.io.ByteArrayOutputStream

/** Primary helper adapter. Binder calls only update a bounded mailbox or read cached state. */
internal class PhoneScreenPower(
    private val context: Context,
    private val currentDisplay: () -> Int,
) {
    private val guard = Any()
    private val productUid = context.packageManager.getApplicationInfo("dev.dexdroid", 0).uid
    private var retired = false
    private var active: Child? = null
    private var lastTarget: PhoneScreenMessage.Start? = null
    @Volatile private var result = PhoneScreenResult(PhoneScreenResult.State.UNKNOWN)

    fun command(
        command: Int,
        display: Int,
        lease: Long,
        deadline: Long,
        owner: IBinder?,
        expectedBoot: String? = null,
        expectedPhysical: Long = -1,
    ): Bundle {
        if (Process.myUid() != 2000 || Binder.getCallingUid() != productUid)
            return PhoneScreenResult(
                    PhoneScreenResult.State.UNAVAILABLE,
                    PhoneScreenResult.Reason.INVALID_REQUEST,
                )
                .toBundle()
        synchronized(guard) {
            val child = active
            if (lease <= 0 || owner == null || !owner.isBinderAlive)
                return invalid(lease).toBundle()
            val requestedTarget =
                if (PhoneScreenResult.validReceipt(expectedBoot, expectedPhysical))
                    PhoneScreenMessage.Start(false, 0, lease, 0, expectedPhysical, expectedBoot)
                else null
            if (command == PhoneScreenResult.POLL) {
                if (child != null && (child.epoch != lease || child.owner !== owner))
                    return invalid(lease).toBundle()
                return if (result.lease == lease) snapshot(child).toBundle()
                else
                    PhoneScreenResult(
                            PhoneScreenResult.State.UNKNOWN,
                            PhoneScreenResult.Reason.READBACK,
                            lease,
                        )
                        .toBundle()
            }
            if (command == PhoneScreenResult.ON && child != null) {
                if (child.epoch != lease || child.owner !== owner) return invalid(lease).toBundle()
                if (
                    requestedTarget != null &&
                        child.target != null &&
                        requestedTarget != child.target
                )
                    return invalid(lease).toBundle()
                if (result.state == PhoneScreenResult.State.ON) return snapshot(child).toBundle()
                if (child.ended) {
                    // Recovery may start another NORMAL-only child, but its same target lock
                    // cannot admit OFF or race the unresolved old native actuator.
                    val target =
                        child.target ?: requestedTarget ?: return snapshot(child).toBundle()
                    val recovery = Child(false, 0, child.epoch, 0, owner, true, target)
                    active = recovery
                    result =
                        PhoneScreenResult(
                            PhoneScreenResult.State.PENDING,
                            PhoneScreenResult.Reason.RESTORING,
                            child.epoch,
                            boot = target.boot!!,
                            physical = target.physicalId!!,
                        )
                    recovery.start()
                    return result.toBundle()
                }
                // An existing NORMAL-only recovery already expresses ON. Retrying the
                // button must not cancel its wait for the old actuator's exclusive lock.
                if (child.recovery) return snapshot(child).toBundle()
                child.close(PhoneScreenResult.Reason.NONE)
                result =
                    snapshot(child)
                        .copy(
                            state = PhoneScreenResult.State.PENDING,
                            reason = PhoneScreenResult.Reason.RESTORING,
                        )
                return result.toBundle()
            }
            if (command == PhoneScreenResult.RENEW) {
                if (
                    retired ||
                        child == null ||
                        child.epoch != lease ||
                        child.owner !== owner ||
                        display != child.display ||
                        display != currentDisplay()
                )
                    return invalid(lease).toBundle()
                if (child.policy?.renew(lease, deadline, SystemClock.elapsedRealtime()) == true)
                    child.renew(deadline)
                return snapshot(child).toBundle()
            }
            if (
                command !in
                    setOf(PhoneScreenResult.OFF, PhoneScreenResult.ON, PhoneScreenResult.CHECK)
            )
                return invalid(lease).toBundle()
            if (child != null) {
                if (
                    command == PhoneScreenResult.OFF &&
                        (!child.off || result.state == PhoneScreenResult.State.ON)
                ) {
                    return PhoneScreenResult(
                            PhoneScreenResult.State.PENDING,
                            PhoneScreenResult.Reason.BUSY,
                            lease,
                        )
                        .toBundle()
                }
                if (
                    command == PhoneScreenResult.OFF &&
                        (child.epoch != lease || child.owner !== owner)
                )
                    return invalid(lease).toBundle()
                return snapshot(child).toBundle()
            }
            if (retired && command != PhoneScreenResult.ON)
                return PhoneScreenResult(
                        PhoneScreenResult.State.UNAVAILABLE,
                        PhoneScreenResult.Reason.CLOSED,
                        lease,
                    )
                    .toBundle()
            val off = command == PhoneScreenResult.OFF
            if (
                off &&
                    (display <= 0 ||
                        display != currentDisplay() ||
                        lease <= 0 ||
                        requestedTarget == null ||
                        !PhoneScreenLease.validDeadline(deadline, SystemClock.elapsedRealtime()))
            )
                return invalid(lease).toBundle()
            val recoveryTarget =
                if (command == PhoneScreenResult.ON)
                    requestedTarget ?: lastTarget?.copy(epoch = lease)
                else null
            if (command == PhoneScreenResult.ON && recoveryTarget == null)
                return PhoneScreenResult(
                        PhoneScreenResult.State.UNKNOWN,
                        PhoneScreenResult.Reason.READBACK,
                        lease,
                    )
                    .toBundle()
            val next =
                Child(
                    off,
                    display,
                    lease,
                    if (off) deadline else 0,
                    owner,
                    command == PhoneScreenResult.ON,
                    if (off) requestedTarget else recoveryTarget,
                )
            active = next
            result =
                PhoneScreenResult(
                    PhoneScreenResult.State.PENDING,
                    PhoneScreenResult.Reason.CHECKING,
                    next.epoch,
                    boot = next.target?.boot ?: "",
                    physical = next.target?.physicalId ?: -1,
                )
            next.start()
            return result.toBundle()
        }
    }

    /** Must run BEFORE DeskService's global lock, stop work or owner-death cleanup. */
    fun closeFirst() {
        synchronized(guard) {
            retired = true
            active?.let {
                it.close(PhoneScreenResult.Reason.CLOSED)
                result =
                    snapshot(it)
                        .copy(
                            state = PhoneScreenResult.State.PENDING,
                            reason = PhoneScreenResult.Reason.RESTORING,
                            safeAutoRestore = false,
                        )
            }
        }
    }

    fun close() = closeFirst()

    private fun snapshot(child: Child?): PhoneScreenResult {
        if (
            child?.policy?.reason(SystemClock.elapsedRealtime()) != null &&
                result.state in setOf(PhoneScreenResult.State.OFF, PhoneScreenResult.State.PENDING)
        ) {
            return PhoneScreenResult(
                PhoneScreenResult.State.UNKNOWN,
                child.policy.reason(SystemClock.elapsedRealtime())!!,
                child.epoch,
                boot = child.target?.boot ?: "",
                physical = child.target?.physicalId ?: -1,
            )
        }
        return result.copy(
            boot = child?.target?.boot ?: result.boot,
            physical = child?.target?.physicalId ?: result.physical,
        )
    }

    private fun invalid(lease: Long = 0): PhoneScreenResult {
        val child = active
        val uncertain =
            child != null &&
                (child.off || child.recovery) &&
                result.state !in
                    setOf(PhoneScreenResult.State.ON, PhoneScreenResult.State.UNAVAILABLE)
        return PhoneScreenResult(
            if (uncertain) PhoneScreenResult.State.UNKNOWN else PhoneScreenResult.State.UNAVAILABLE,
            PhoneScreenResult.Reason.INVALID_REQUEST,
            lease,
            boot = child?.target?.boot ?: "",
            physical = child?.target?.physicalId ?: -1,
        )
    }

    private inner class Child(
        val off: Boolean,
        val display: Int,
        val epoch: Long,
        val initialDeadline: Long,
        val owner: IBinder?,
        val recovery: Boolean = false,
        initialTarget: PhoneScreenMessage.Start? = null,
    ) {
        val policy = if (off) PhoneScreenLease(epoch, initialDeadline) else null
        private val mailbox = Object()
        private var renewal = 0L
        @Volatile private var closed: PhoneScreenResult.Reason? = null
        @Volatile var ended = false
        @Volatile var target: PhoneScreenMessage.Start? = initialTarget
        private val death = IBinder.DeathRecipient { close(PhoneScreenResult.Reason.OWNER_LOST) }

        fun start() {
            try {
                owner?.linkToDeath(death, 0)
            } catch (_: Exception) {
                close(PhoneScreenResult.Reason.OWNER_LOST)
            }
            Thread({ run() }, "DexDroid-panel-start").apply {
                isDaemon = true
                start()
            }
        }

        fun close(reason: PhoneScreenResult.Reason) {
            synchronized(mailbox) {
                if (closed == null) closed = reason
                policy?.close(closed!!)
                renewal = 0
                mailbox.notifyAll()
            }
        }

        fun renew(deadline: Long) {
            synchronized(mailbox) {
                if (closed == null) {
                    renewal = maxOf(renewal, deadline)
                    mailbox.notifyAll()
                }
            }
        }

        private fun run() {
            var launched: java.lang.Process? = null
            try {
                val apk = context.packageManager.getApplicationInfo("dev.dexdroid", 0).sourceDir
                val builder =
                    ProcessBuilder(
                        "/system/bin/app_process",
                        "/system/bin",
                        "dev.dexdroid.PhoneScreenWatchdog",
                    )
                builder.environment()["CLASSPATH"] = apk
                val process = builder.start()
                launched = process
                Thread({ discard(process.errorStream) }, "DexDroid-panel-stderr").apply {
                    isDaemon = true
                    start()
                }
                Thread({ write(process) }, "DexDroid-panel-write").apply {
                    isDaemon = true
                    start()
                }
                read(process)
                // No destroy()/waitFor(): this child may still own a blocked native restore.
            } catch (_: Exception) {
                publish(
                    PhoneScreenResult(
                        if (launched == null) PhoneScreenResult.State.UNAVAILABLE
                        else PhoneScreenResult.State.UNKNOWN,
                        PhoneScreenResult.Reason.FAILED,
                        epoch,
                    )
                )
            } finally {
                ended = true
                owner?.let { runCatching { it.unlinkToDeath(death, 0) } }
                synchronized(guard) {
                    if (
                        active === this &&
                            result.state in
                                setOf(
                                    PhoneScreenResult.State.ON,
                                    PhoneScreenResult.State.UNAVAILABLE,
                                )
                    )
                        active = null
                }
            }
        }

        private fun write(process: java.lang.Process) {
            try {
                process.outputStream.bufferedWriter(Charsets.US_ASCII).use { writer ->
                    val initial =
                        synchronized(mailbox) {
                            if (closed != null) "CLOSE ${closed!!.name}"
                            else if (off)
                                "OFF $display $epoch $initialDeadline ${target!!.physicalId} ${target!!.boot}"
                            else if (recovery)
                                "RESTORE $epoch ${target!!.physicalId} ${target!!.boot}"
                            else "CHECK 0 $epoch 0"
                        }
                    writer.write(initial + "\n")
                    writer.flush()
                    if (initial.startsWith("CLOSE")) {
                        // No initial OFF was ever sent, so this cancellation cannot have
                        // changed physical power. Recovery of an earlier OFF stays UNKNOWN.
                        publish(
                            PhoneScreenResult(PhoneScreenResult.State.UNAVAILABLE, closed!!, epoch)
                        )
                        return
                    }
                    while (true) {
                        val line =
                            synchronized(mailbox) {
                                while (closed == null && renewal == 0L) mailbox.wait()
                                if (closed != null) "CLOSE ${closed!!.name}"
                                else "RENEW $epoch $renewal".also { renewal = 0 }
                            }
                        writer.write(line + "\n")
                        writer.flush()
                        if (line.startsWith("CLOSE")) return
                    }
                }
            } catch (_: Exception) {
                // Closing the pipe is terminal in the watchdog; never kill its actuator.
                runCatching { process.outputStream.close() }
                close(PhoneScreenResult.Reason.OWNER_LOST)
            }
        }

        private fun read(process: java.lang.Process) {
            process.inputStream.use { input ->
                val bytes = ByteArrayOutputStream()
                while (true) {
                    val value = input.read()
                    if (value < 0) break
                    if (value == 10) {
                        val parts = bytes.toString(Charsets.US_ASCII.name()).split(' ')
                        bytes.reset()
                        if (parts.firstOrNull() == "T") {
                            val identity =
                                if (parts.size == 4)
                                    PhoneScreenMessage.parse(
                                        "RESTORE ${parts[1]} ${parts[2]} ${parts[3]}"
                                    ) as? PhoneScreenMessage.Start
                                else null
                            if (
                                identity == null ||
                                    identity.epoch != epoch ||
                                    (target != null && target != identity)
                            ) {
                                close(PhoneScreenResult.Reason.OWNER_LOST)
                            } else {
                                target = identity
                                synchronized(guard) {
                                    if (active === this) {
                                        lastTarget = identity
                                        result =
                                            result.copy(
                                                boot = identity.boot!!,
                                                physical = identity.physicalId!!,
                                            )
                                    }
                                }
                            }
                            continue
                        }
                        val received =
                            try {
                                require(
                                    parts.size == 5 &&
                                        parts[0] == "S" &&
                                        parts[3].toLong() == epoch &&
                                        parts[4] in setOf("0", "1")
                                )
                                PhoneScreenResult(
                                    PhoneScreenResult.State.valueOf(parts[1]),
                                    PhoneScreenResult.Reason.valueOf(parts[2]),
                                    epoch,
                                    parts[4] == "1",
                                )
                            } catch (_: Exception) {
                                null
                            }
                        if (received == null) {
                            close(PhoneScreenResult.Reason.OWNER_LOST)
                            publish(
                                PhoneScreenResult(
                                    PhoneScreenResult.State.UNKNOWN,
                                    PhoneScreenResult.Reason.FAILED,
                                    epoch,
                                )
                            )
                        } else publish(received)
                    } else if (value in 32..126 && bytes.size() < PhoneScreenMessage.MAX_LINE) {
                        bytes.write(value)
                    } else {
                        close(PhoneScreenResult.Reason.OWNER_LOST)
                        // Discard any untrusted output without buffering it or blocking restore.
                        discard(input)
                        break
                    }
                }
            }
            synchronized(guard) {
                if (
                    active === this &&
                        result.state !in
                            setOf(PhoneScreenResult.State.ON, PhoneScreenResult.State.UNAVAILABLE)
                ) {
                    result =
                        result.copy(
                            state = PhoneScreenResult.State.UNKNOWN,
                            reason = PhoneScreenResult.Reason.OWNER_LOST,
                        )
                }
            }
            close(PhoneScreenResult.Reason.OWNER_LOST)
        }

        private fun publish(value: PhoneScreenResult) {
            synchronized(guard) {
                if (active !== this) return
                val received =
                    value.copy(boot = target?.boot ?: "", physical = target?.physicalId ?: -1)
                result =
                    if (recovery && value.state == PhoneScreenResult.State.UNAVAILABLE) {
                        received.copy(state = PhoneScreenResult.State.UNKNOWN)
                    } else if (closed != null && value.state == PhoneScreenResult.State.OFF) {
                        received.copy(
                            state = PhoneScreenResult.State.PENDING,
                            reason = PhoneScreenResult.Reason.RESTORING,
                        )
                    } else received
                if (value.state == PhoneScreenResult.State.ON) close(PhoneScreenResult.Reason.NONE)
            }
        }

        private fun discard(input: java.io.InputStream) {
            try {
                input.use { stream ->
                    val buffer = ByteArray(4096)
                    while (stream.read(buffer) >= 0) {
                        /* bounded discard */
                    }
                }
            } catch (_: Exception) {
                /* No raw shell errors cross the product boundary. */
            }
        }
    }
}
