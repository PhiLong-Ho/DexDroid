package dev.dexdroid

/** Pure, privacy-safe projection of current prerequisites and narrowly scoped observations. */
internal object DeviceCapabilities {
    enum class Verdict(val label: String) {
        AVAILABLE("Available"),
        CONFIRMED("Confirmed this session"),
        UNAVAILABLE("Unavailable"),
        NOT_CHECKED("Not checked"),
    }

    enum class Shizuku {
        MISSING,
        STOPPED,
        PERMISSION,
        READY,
        CONNECTED,
        UNKNOWN,
    }

    enum class InputObservation {
        NONE,
        TOUCH_ACCEPTED,
        SCROLL_ACCEPTED,
        REJECTED,
        FAILED,
    }

    enum class AudioUnavailableReason {
        DENIED,
        UNSUPPORTED,
        OTHER,
    }

    data class Audio(
        val available: Boolean = false,
        val confirmed: Boolean = false,
        val pending: Boolean = false,
        val uncertain: Boolean = false,
        val unavailableReason: AudioUnavailableReason? = null,
        val outputCount: Int? = null,
    )

    data class Mode(val width: Int, val height: Int, val refreshHz: Float)

    data class Monitor(
        val present: Boolean = false,
        val signalWidth: Int? = null,
        val signalHeight: Int? = null,
        val refreshHz: Float? = null,
        val physicalDpi: Float? = null,
        val modes: List<Mode> = emptyList(),
        val modesTruncated: Boolean = false,
    )

    data class Snapshot(
        val shizuku: Shizuku = Shizuku.UNKNOWN,
        val monitor: Monitor = Monitor(),
        val desktopActive: Boolean = false,
        val virtualWidth: Int? = null,
        val virtualHeight: Int? = null,
        val virtualDpi: Int? = null,
        val rotation: Int? = null,
        val busy: Boolean = false,
        val input: InputObservation = InputObservation.NONE,
        val rotationConfigured: Boolean = false,
        val splitConfigured: Boolean = false,
        val audio: Audio = Audio(),
    )

    data class Item(val title: String, val verdict: Verdict, val detail: String)

    class Report internal constructor(val items: List<Item>) {
        /** Every word comes from fixed text or validated numbers, never platform names/errors. */
        fun plainText(): String =
            buildString {
                    append("Device capabilities\n")
                    items.forEach {
                        append(it.title).append(": ").append(it.verdict.label).append('\n')
                        append(it.detail).append('\n')
                    }
                }
                .trimEnd()
    }

    fun report(snapshot: Snapshot): Report =
        Report(
            listOf(
                shizuku(snapshot.shizuku),
                monitor(snapshot.monitor),
                desktop(snapshot),
                input(snapshot),
                rotationAndSplit(snapshot),
                audio(snapshot.audio, snapshot.desktopActive),
            )
        )

    private fun shizuku(state: Shizuku): Item =
        when (state) {
            Shizuku.MISSING ->
                Item(
                    "Shizuku",
                    Verdict.UNAVAILABLE,
                    "Shizuku is missing. Install and start Shizuku.",
                )
            Shizuku.STOPPED ->
                Item("Shizuku", Verdict.UNAVAILABLE, "Shizuku is stopped. Start Shizuku.")
            Shizuku.PERMISSION ->
                Item(
                    "Shizuku",
                    Verdict.UNAVAILABLE,
                    "Permission is missing. Allow DexDroid in Shizuku.",
                )
            Shizuku.READY ->
                Item(
                    "Shizuku",
                    Verdict.AVAILABLE,
                    "Shizuku is ready; the desktop helper is not connected.",
                )
            Shizuku.CONNECTED ->
                Item(
                    "Shizuku",
                    Verdict.CONFIRMED,
                    "The desktop helper connected this session. This does not confirm app response.",
                )
            Shizuku.UNKNOWN ->
                Item("Shizuku", Verdict.NOT_CHECKED, "Shizuku readiness has not been established.")
        }

    private fun monitor(value: Monitor): Item {
        if (!value.present)
            return Item(
                "Monitor",
                Verdict.UNAVAILABLE,
                "No monitor is connected. Connect a monitor.",
            )
        val detail = buildString {
            append("Monitor detected. Reported physical signal: ")
            append(dimensions(value.signalWidth, value.signalHeight) ?: "unknown")
            append(value.refreshHz.validRate()?.let { " at ${it.displayNumber()} Hz" }.orEmpty())
            append(". Reported physical DPI: ")
            append(
                value.physicalDpi.validPhysicalDpi()?.let { "${it.displayNumber()} (axis average)" }
                    ?: "unknown"
            )
            append(". Reported modes: ")
            val modes =
                value.modes
                    .take(MAX_MODES)
                    .mapNotNull { mode ->
                        val size = dimensions(mode.width, mode.height)
                        val rate = mode.refreshHz.validRate()
                        if (size != null && rate != null) "$size at ${rate.displayNumber()} Hz"
                        else null
                    }
                    .distinct()
            append(modes.joinToString(", ").ifEmpty { "unknown" })
            if (value.modesTruncated || value.modes.size > MAX_MODES)
                append(" (limited to the first $MAX_MODES reported modes)")
            append(". Reported metadata does not confirm rendered pixels.")
        }
        return Item("Monitor", Verdict.AVAILABLE, detail)
    }

    private fun desktop(value: Snapshot): Item {
        val size = dimensions(value.virtualWidth, value.virtualHeight)
        val dpi = value.virtualDpi?.takeIf { it in 72..1280 }
        val rotation = value.rotation?.takeIf { it in 0..3 }?.times(90)
        val configuration =
            "Virtual resolution: ${size ?: "unknown"}; logical UI DPI: ${dpi ?: "unknown"}; " +
                "rotation: ${rotation?.let { "$it degrees" } ?: "unknown"}."
        val missing = missingPrerequisite(value)
        if (missing != null) return Item("Desktop", Verdict.UNAVAILABLE, "$missing $configuration")
        if (value.shizuku == Shizuku.UNKNOWN)
            return Item(
                "Desktop",
                Verdict.NOT_CHECKED,
                "Shizuku readiness has not been established. $configuration",
            )
        if (value.desktopActive && value.shizuku != Shizuku.CONNECTED)
            return Item(
                "Desktop",
                Verdict.NOT_CHECKED,
                "The desktop helper is not connected. $configuration",
            )
        if (value.desktopActive)
            return Item(
                "Desktop",
                Verdict.CONFIRMED,
                "Desktop display created this session. $configuration " +
                    (if (value.busy) "A desktop operation is in progress. " else "") +
                    "This does not confirm visible pixels or app response.",
            )
        return when {
            value.busy ->
                Item(
                    "Desktop",
                    Verdict.NOT_CHECKED,
                    "A desktop operation is in progress. $configuration",
                )
            else ->
                Item(
                    "Desktop",
                    Verdict.AVAILABLE,
                    "Desktop prerequisites are available; start the desktop to observe it. $configuration",
                )
        }
    }

    private fun input(value: Snapshot): Item {
        val missing = missingPrerequisite(value)
        if (missing != null) return Item("Input", Verdict.UNAVAILABLE, missing)
        if (!value.desktopActive)
            return Item(
                "Input",
                Verdict.NOT_CHECKED,
                "Start the desktop before observing input. No app response has been checked.",
            )
        if (value.shizuku != Shizuku.CONNECTED)
            return Item(
                "Input",
                Verdict.NOT_CHECKED,
                "The desktop helper is not connected. No current input confirmation.",
            )
        return when (value.input) {
            InputObservation.NONE ->
                Item(
                    "Input",
                    Verdict.NOT_CHECKED,
                    "No accepted touchpad input has been observed this session. App response, monitor touch and keyboard input remain unverified.",
                )
            InputObservation.TOUCH_ACCEPTED ->
                Item(
                    "Input",
                    Verdict.CONFIRMED,
                    "Android accepted a touch injection this session. App response has not been checked.",
                )
            InputObservation.SCROLL_ACCEPTED ->
                Item(
                    "Input",
                    Verdict.CONFIRMED,
                    "Android accepted a scroll injection this session. App response has not been checked.",
                )
            InputObservation.REJECTED ->
                Item(
                    "Input",
                    Verdict.UNAVAILABLE,
                    "Android rejected an input injection this session. Reconnect the desktop and try the ordinary input again.",
                )
            InputObservation.FAILED ->
                Item(
                    "Input",
                    Verdict.NOT_CHECKED,
                    "Input dispatch failed; the cause has not been established. No accepted injection or app response is confirmed.",
                )
        }
    }

    private fun rotationAndSplit(value: Snapshot): Item {
        val missing = missingPrerequisite(value)
        if (missing != null)
            return Item(
                "Rotation + split",
                Verdict.UNAVAILABLE,
                "$missing Rotation: not checked. Split: not checked.",
            )
        if (!value.desktopActive)
            return Item(
                "Rotation + split",
                Verdict.NOT_CHECKED,
                "Rotation: not checked. Split: not checked. Start the desktop; saved settings do not prove either operation.",
            )
        if (value.shizuku != Shizuku.CONNECTED)
            return Item(
                "Rotation + split",
                Verdict.NOT_CHECKED,
                "The desktop helper is not connected. Rotation: not checked. Split: not checked.",
            )
        val rotation =
            if (value.rotationConfigured) "configuration completed this session" else "not checked"
        val split =
            if (value.splitConfigured) "configuration completed this session" else "not checked"
        return Item(
            "Rotation + split",
            if (value.rotationConfigured || value.splitConfigured) Verdict.CONFIRMED
            else Verdict.NOT_CHECKED,
            "Rotation: $rotation. Split: $split. Configuration completion does not confirm rendered layout or app response.",
        )
    }

    private fun audio(value: Audio, desktopActive: Boolean): Item {
        val outputs =
            value.outputCount
                ?.takeIf { it in 0..64 }
                ?.let { " Reported selectable outputs: $it." }
                .orEmpty()
        val optional = " Audio selection is optional; it does not determine desktop availability."
        val result =
            when {
                value.unavailableReason != null -> {
                    val reason =
                        when (value.unavailableReason) {
                            AudioUnavailableReason.DENIED ->
                                "Android denied audio policy access. Use the system audio controls."
                            AudioUnavailableReason.UNSUPPORTED ->
                                "Audio policy selection is unsupported here. Use the system audio controls."
                            AudioUnavailableReason.OTHER ->
                                "Audio policy selection is unavailable. Use the system audio controls."
                        }
                    Item("Audio selection", Verdict.UNAVAILABLE, reason)
                }
                value.pending ->
                    Item(
                        "Audio selection",
                        Verdict.NOT_CHECKED,
                        "Audio policy work is pending; no current confirmation.",
                    )
                value.uncertain ->
                    Item(
                        "Audio selection",
                        Verdict.NOT_CHECKED,
                        "Current audio policy is uncertain; no current confirmation.",
                    )
                value.confirmed && value.available ->
                    Item(
                        "Audio selection",
                        Verdict.CONFIRMED,
                        "Audio policy readback confirmed the selection this session. This does not confirm physical routing or audible sound.",
                    )
                value.available ->
                    Item(
                        "Audio selection",
                        Verdict.AVAILABLE,
                        "Audio policy selection is available; no selection has been confirmed this session.",
                    )
                else ->
                    Item(
                        "Audio selection",
                        Verdict.NOT_CHECKED,
                        "Audio policy availability has not been established." +
                            if (desktopActive) "" else " Start the desktop to observe availability.",
                    )
            }
        return result.copy(detail = result.detail + outputs + optional)
    }

    private fun missingPrerequisite(value: Snapshot): String? =
        when {
            !value.monitor.present -> "Connect a monitor."
            value.shizuku == Shizuku.MISSING -> "Install and start Shizuku."
            value.shizuku == Shizuku.STOPPED -> "Start Shizuku."
            value.shizuku == Shizuku.PERMISSION -> "Allow DexDroid in Shizuku."
            else -> null
        }

    private fun dimensions(width: Int?, height: Int?): String? =
        if (width != null && height != null && width in 1..32768 && height in 1..32768)
            "$width × $height"
        else null

    private fun Float?.validRate(): Float? = this?.takeIf { it.isFinite() && it in 1f..1000f }

    private fun Float.displayNumber(): String =
        String.format(java.util.Locale.US, "%.2f", this).trimEnd('0').trimEnd('.')

    private fun Float?.validPhysicalDpi(): Float? =
        this?.takeIf { it.isFinite() && it in 30f..600f }

    private const val MAX_MODES = 16
}

/** Memory-only facts owned by a complete current-session/geometry key supplied by the service. */
internal class CapabilityEvidenceLedger {
    class Ticket internal constructor()

    sealed class Observation {
        data class Input(val value: DeviceCapabilities.InputObservation) : Observation()

        data object RotationConfigured : Observation()

        data object SplitConfigured : Observation()
    }

    data class Facts(
        val input: DeviceCapabilities.InputObservation = DeviceCapabilities.InputObservation.NONE,
        val rotationConfigured: Boolean = false,
        val splitConfigured: Boolean = false,
    )

    private var owner: Any? = null
    private var ownerSet = false
    private var ticket = Ticket()
    private var observations = Facts()
    private var readiness: DeviceCapabilities.Shizuku? = null

    /** Equal immutable keys reuse their ticket; every changed key clears the three facts. */
    @Synchronized
    fun updateOwner(key: Any?): Ticket {
        if (!ownerSet || owner != key) {
            owner = key
            ownerSet = true
            readiness = null
            invalidate()
        }
        return ticket
    }

    /** Clears observations and advances identity even when the current key remains equal. */
    @Synchronized
    fun invalidate(): Ticket {
        ticket = Ticket()
        observations = Facts()
        return ticket
    }

    /** Fresh readiness loss/recovery cannot resurrect facts under unchanged cached owners. */
    @Synchronized
    fun updateReadiness(next: DeviceCapabilities.Shizuku): Ticket {
        if (readiness != next) {
            if (readiness != null || next != DeviceCapabilities.Shizuku.CONNECTED) invalidate()
            readiness = next
        }
        return ticket
    }

    @Synchronized
    fun record(expected: Ticket, observation: Observation): Boolean {
        if (ticket !== expected || !ownerSet) return false
        observations =
            when (observation) {
                is Observation.Input -> observations.copy(input = observation.value)
                Observation.RotationConfigured -> observations.copy(rotationConfigured = true)
                Observation.SplitConfigured -> observations.copy(splitConfigured = true)
            }
        return true
    }

    @Synchronized fun facts(): Facts = observations
}
