package dev.dexdroid

/** Absolute app-clock deadlines: a delayed renewal never creates a new five-second lease. */
internal class PhoneScreenLease(val epoch: Long, initialDeadline: Long) {
    private var deadline = initialDeadline
    private var terminal: PhoneScreenResult.Reason? = null
    private var offStarted = false

    @Synchronized
    fun renew(epoch: Long, absoluteDeadline: Long, now: Long): Boolean {
        expire(now)
        if (terminal != null || epoch != this.epoch || !validDeadline(absoluteDeadline, now))
            return false
        if (absoluteDeadline <= deadline) return false
        deadline = absoluteDeadline
        return true
    }

    @Synchronized
    fun close(reason: PhoneScreenResult.Reason) {
        if (terminal == null) terminal = reason
    }

    @Synchronized
    fun reason(now: Long): PhoneScreenResult.Reason? {
        expire(now)
        return terminal
    }

    /** Marks the sole native OFF as in flight without holding this lock across native work. */
    @Synchronized
    fun beginOff(now: Long): Boolean {
        expire(now)
        if (terminal != null || offStarted || !validDeadline(deadline, now)) return false
        offStarted = true
        return true
    }

    /** A close received during a blocked native OFF puts NORMAL immediately after it. */
    fun runOff(now: () -> Long, off: () -> Unit, restore: () -> Unit): Boolean {
        if (!beginOff(now())) return false
        try {
            off()
        } finally {
            if (reason(now()) != null) restore()
        }
        return true
    }

    private fun expire(now: Long) {
        if (terminal == null && now >= deadline) terminal = PhoneScreenResult.Reason.EXPIRED
    }

    companion object {
        const val MAX_LEASE_MS = 5000L

        fun validDeadline(deadline: Long, now: Long): Boolean =
            deadline > now && deadline - now in 1..MAX_LEASE_MS
    }
}

/** The private pipe accepts one initial request, renewals, and terminal close only. */
internal sealed class PhoneScreenMessage {
    data class Start(
        val off: Boolean,
        val display: Int,
        val epoch: Long,
        val deadline: Long,
        val physicalId: Long? = null,
        val boot: String? = null,
    ) : PhoneScreenMessage() {
        fun acceptsIdentity(actualBoot: String, actualPhysical: Long): Boolean =
            PhoneScreenResult.validReceipt(actualBoot, actualPhysical) &&
                if (physicalId == null && boot == null) !off
                else
                    PhoneScreenResult.validReceipt(boot, physicalId ?: -1) &&
                        boot == actualBoot &&
                        physicalId == actualPhysical
    }

    data class Renew(val epoch: Long, val deadline: Long) : PhoneScreenMessage()

    data class Close(val reason: PhoneScreenResult.Reason) : PhoneScreenMessage()

    companion object {
        const val MAX_LINE = 128

        fun parse(line: String): PhoneScreenMessage? {
            if (
                line.length > MAX_LINE ||
                    line.any {
                        it != ' ' &&
                            it !in 'A'..'Z' &&
                            it !in 'a'..'f' &&
                            it !in '0'..'9' &&
                            it != '_' &&
                            it != '-'
                    }
            )
                return null
            val parts = line.split(' ')
            return when {
                parts.size == 4 && parts[0] == "RESTORE" -> {
                    val epoch = parts[1].toLongOrNull() ?: return null
                    val physical = parts[2].toLongOrNull() ?: return null
                    if (epoch <= 0 || !PhoneScreenResult.validReceipt(parts[3], physical))
                        return null
                    Start(false, 0, epoch, 0, physical, parts[3])
                }
                parts.size == 6 && parts[0] == "OFF" -> {
                    val display = parts[1].toIntOrNull() ?: return null
                    val epoch = parts[2].toLongOrNull() ?: return null
                    val deadline = parts[3].toLongOrNull() ?: return null
                    val physical = parts[4].toLongOrNull() ?: return null
                    if (
                        display <= 0 ||
                            epoch <= 0 ||
                            deadline <= 0 ||
                            !PhoneScreenResult.validReceipt(parts[5], physical)
                    )
                        return null
                    Start(true, display, epoch, deadline, physical, parts[5])
                }
                parts.size == 4 && parts[0] == "CHECK" -> {
                    val display = parts[1].toIntOrNull() ?: return null
                    val epoch = parts[2].toLongOrNull() ?: return null
                    val deadline = parts[3].toLongOrNull() ?: return null
                    if (display != 0 || epoch <= 0 || deadline != 0L) return null
                    Start(false, 0, epoch, 0)
                }
                parts.size == 3 && parts[0] == "RENEW" -> {
                    val epoch = parts[1].toLongOrNull() ?: return null
                    val deadline = parts[2].toLongOrNull() ?: return null
                    if (epoch <= 0 || deadline <= 0) return null
                    Renew(epoch, deadline)
                }
                parts.size == 2 && parts[0] == "CLOSE" -> {
                    val reason =
                        runCatching { PhoneScreenResult.Reason.valueOf(parts[1]) }.getOrNull()
                            ?: return null
                    if (
                        reason !in
                            setOf(
                                PhoneScreenResult.Reason.NONE,
                                PhoneScreenResult.Reason.CLOSED,
                                PhoneScreenResult.Reason.OWNER_LOST,
                            )
                    )
                        return null
                    Close(reason)
                }
                else -> null
            }
        }
    }
}

/** Physical compositor state only. DMS logical display state can remain ON while dark. */
internal object PhoneScreenReadback {
    const val MAX_BYTES = 256 * 1024

    fun parse(text: String, physicalId: Long, complete: Boolean): PhoneScreenResult.State {
        if (!complete || text.length > MAX_BYTES) return PhoneScreenResult.State.UNKNOWN
        var target = false
        var sections = 0
        val powers = mutableListOf<String>()
        for (line in text.lineSequence()) {
            // Full legacy dumps contain other headings mentioning Display. Only the exact
            // compositor display section owns a powerMode line; never correlate by proximity.
            if (line.startsWith("Display ") || line.startsWith("Virtual Display ")) {
                target = line.trimEnd() == "Display $physicalId"
                if (target) sections++
            } else if (line.isNotBlank() && !line.first().isWhitespace()) {
                target = false
            } else if (target && line.trimStart().startsWith("powerMode=")) {
                powers += line.trim().removePrefix("powerMode=")
            }
        }
        if (sections != 1 || powers.size != 1) return PhoneScreenResult.State.UNKNOWN
        return when (powers.single()) {
            "On",
            "2" -> PhoneScreenResult.State.ON
            "Off",
            "0" -> PhoneScreenResult.State.OFF
            else -> PhoneScreenResult.State.UNKNOWN
        }
    }
}
