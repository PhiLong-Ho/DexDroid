package dev.dexdroid

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** Bounded, in-memory history. Only fixed categories and explicitly allowed fields are retained. */
class DiagnosticLog {
    private val events = ArrayDeque<String>()
    private var cachedSnapshot: String? = null

    /** Returns the safe text that may be sent to logcat, never the original message. */
    @Synchronized
    fun add(event: String?): String {
        val safe = format(event).take(512)
        events.addLast(SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()) + "  " + safe)
        while (events.size > 250) events.removeFirst()
        cachedSnapshot = null
        return safe
    }

    @Synchronized
    fun snapshot(): String {
        cachedSnapshot?.let {
            return it
        }
        return (if (events.isEmpty()) "No events recorded." else events.joinToString("\n")).also {
            cachedSnapshot = it
        }
    }

    @Synchronized
    fun clear() {
        events.clear()
        cachedSnapshot = null
    }

    private fun format(event: String?): String {
        if (event == null) return OMITTED
        // Bound parsing work. Do not truncate or strip controls into an apparently valid template.
        if (event.length <= 1024) {
            if (event in FIXED_EVENTS) return event
            for (pattern in SAFE_FIELDS) if (pattern.matches(event)) return event
        }
        for ((prefix, category) in CATEGORIES) {
            if (event.startsWith(prefix)) return category
        }
        for ((prefix, operation) in FAILURES) {
            if (event.startsWith(prefix)) {
                // Classify only; no substring of an exception or shell result is retained.
                val detail = event.take(1024)
                val reason =
                    when {
                        detail.contains("SecurityException") -> "permission denied"
                        detail.contains("DeadObjectException") ||
                            detail.contains("RemoteException") -> "bridge unavailable"
                        detail.contains("TimeoutException") ||
                            detail.contains("timed out", ignoreCase = true) -> "timed out"
                        detail.contains("IllegalArgumentException") -> "invalid request"
                        else -> "details omitted"
                    }
                return "$operation: $reason"
            }
        }
        return OMITTED
    }

    companion object {
        private const val OMITTED = "Session event: details omitted (unrecognized diagnostic)"
        private const val NUMBER = "-?[0-9]{1,7}"
        private const val DISTANCE = "[0-9]{1,6}(?:\\.[0-9]{1,3})?"
        private const val EDGE = "(?:Controls|Home|Left Back|Right Back)"
        private const val PAGE =
            "(?:home|controls|settings|diagnostics|display|layout|audio|profiles|guide|protected|shizuku|debugging-off)"

        private val SAFE_FIELDS =
            listOf(
                Regex(
                    "Three-finger Back (?:recognized|rejected): dx=$NUMBER dy=$NUMBER threshold=$DISTANCE px rightward"
                ),
                Regex("Key (?:queued|sent): KEYCODE_BACK -> display $NUMBER"),
                Regex("Key ignored: KEYCODE_BACK; desktop (?:unavailable|busy)"),
                Regex("Touchpad ignored: desktop=$NUMBER busy=(?:true|false)"),
                Regex(
                    "Touchpad pointers: [1-9][0-9]?; three-finger Back requires right swipe > $DISTANCE px"
                ),
                Regex(
                    "Monitor touch ignored: enabled=(?:true|false) display=$NUMBER busy=(?:true|false)"
                ),
                Regex(
                    "Monitor touch: ACTION_(?:DOWN|UP|CANCEL|POINTER_DOWN\\([0-9]{1,2}\\)|POINTER_UP\\([0-9]{1,2}\\)) pointers=[1-9][0-9]?"
                ),
                Regex("Monitor edge candidate: $EDGE"),
                Regex("Monitor edge recognized: $EDGE dx=$NUMBER dy=$NUMBER"),
                Regex(
                    "Monitor edge rejected: $EDGE \\((?:multi-touch, Fit bar, timeout, or cancel|perpendicular|short or outward)\\)"
                ),
                Regex("Monitor edge committed: $EDGE"),
                Regex(
                    "Monitor (?:panel opened|panel Back|transition dismiss|transition enter): $PAGE"
                ),
                Regex("Monitor search Back: [01] cancelled=(?:true|false)"),
                Regex("Pane [12] (?:ready|empty|closed\\. Choose Apps\\.)"),
                Regex("App is already in pane [12]"),
                Regex("Explicit Stop: controller=(?:true|false), owner=(?:true|false)"),
                Regex("Gesture recognized: two-finger (?:swipe|wheel scroll) at [0-9]{1,3}%"),
                Regex(
                    "App catalog (?:ready: [0-9]{1,6} entries|failed) in [0-9]{1,9} ms \\(worker\\)"
                ),
            )

        private val FIXED_EVENTS =
            setOf(
                "Controller service created",
                "Shizuku service available",
                "Shizuku connected",
                "Shizuku authorization denied. Retry from Options when ready.",
                "Shizuku stopped. Open Shizuku to restart its service.",
                "Shizuku disconnected. Retry from Options.",
                "Install Shizuku, then start its wireless-debugging service.",
                "Shizuku is stopped. Open Shizuku and start it through wireless debugging.",
                "Requesting Shizuku authorization",
                "Connecting to Shizuku…",
                "Waiting for an external monitor",
                "Starting desktop…",
                "Starting split view…",
                "Split surface lost. Reopen DexDroid.",
                "Split connection lost. Reopen DexDroid.",
                "Split setup timed out. Single view restored.",
                "Finish the current display change first",
                "Wait for split view to finish updating",
                "Split input queue full. Restart desktop.",
                "Too many held keyboard keys. Restart desktop.",
                "Desktop stopped",
                "Queued operation discarded: session changed or bridge unavailable",
                "Monitor startup timed out. Open DexDroid on the phone to retry.",
                "Monitor disconnected. Waiting for reconnection.",
                "Monitor host: independent Activity",
                "Monitor host: Presentation fallback; Android may close it when the phone task is removed",
                "Monitor retained: unchanged physical output after configuration notification",
                "Invalid legacy monitor settings ignored",
                "App destination changed. Choose its pane again.",
                "Wait for the display change to finish",
                "Finish the pending display change first",
                "Connect a monitor and start the desktop first",
                "Start the desktop and finish its display change first",
                "Start desktop and finish any pending change first",
                "Launch an app first",
                "No app on monitor. Choose Apps.",
                "Idle Back ignored",
                "Back button pressed",
                "Touchpad DOWN: 1 finger",
                "Touchpad gesture canceled by Android",
                "Gesture recognized: tap",
                "Gesture recognized: pinch zoom",
                "Monitor touch enabled",
                "Monitor touch disabled",
                "Monitor edge navigation enabled",
                "Monitor edge navigation disabled",
                "Monitor touch canceled: contact outside desktop Fit area",
                "Monitor touch canceled: host focus buffer full",
                "Monitor Home: launcher opened",
                "Monitor search keyboard shown",
                "Monitor search keyboard hidden: Hide",
                "Protected-window detection unavailable",
                "Protected desktop window detected",
                "Protected desktop notice cleared",
                "Pane task state unavailable",
                "Named monitor profile saved",
                "Monitor profile renamed",
                "Monitor profile updated from current display",
                "Monitor profile deleted. Current display unchanged.",
                "Profile no longer exists",
                "Named monitor profile applied",
                "Monitor display settings saved",
                "Monitor association forgotten. Named profiles and current display unchanged.",
                "Display preview awaiting confirmation",
                "Previous desktop settings restored",
                "Display update or rollback failed; desktop stopped. Saved monitor association unchanged.",
                "Split surface layout unavailable. Restart desktop.",
                "Output layout unavailable. Restart desktop.",
                "Split display removed. Reopen DexDroid.",
                "Split input shutdown timed out",
                "Secondary bridge unavailable during cleanup",
                "Active app settings reset to defaults",
                "Swipe scrolling saved for active app",
                "Wheel scrolling saved for active app",
                "Rotation, scroll mode and speed saved for active app",
                "Scroll mode and speed saved. Single-view app rotation retained.",
                "Split view uses workspace rotation. Saved single-view app rotation retained.",
                "Stop controller return cancelled: obsolete owner/session or unconfirmed cleanup",
                "Stop controller focus requested through shell",
                "Stop controller no longer exists; return skipped",
                "Open DexDroid on the phone for the debugging-off result",
                "Turning off debugging. Desktop startup is paused until the result is saved.",
            )

        private val CATEGORIES =
            listOf(
                "Monitor connected:" to "Monitor connected: display details omitted",
                "Desktop ready • " to "Desktop ready: display created",
                "App launch queued:" to "App launch queued: waiting for desktop readiness",
                "Monitor launcher selection:" to "App launch requested: monitor selection",
                "Opened " to "App launch completed: IPC completed; app response unverified",
                "Split view · " to "Split view configured: layout updated",
                "Secondary app closure pending; releasing display:" to
                    "Secondary app closure pending; releasing display: details omitted",
            )

        private val FAILURES =
            listOf(
                "Connection failed:" to "Connection failed",
                "Desktop creation failed:" to "Desktop creation failed",
                "Desktop operation failed:" to "Desktop operation failed",
                "Desktop cleanup failed:" to "Desktop cleanup failed; cleanup unconfirmed",
                "Monitor unavailable:" to "Monitor unavailable",
                "Monitor touch failed:" to "Monitor touch failed",
                "Resize failed; retained previous settings:" to
                    "Resize failed; retained previous settings",
                "Split setup failed." to "Split setup failed",
                "Split setup failed:" to "Split setup failed",
                "Split connection failed:" to "Split connection failed",
                "Split input failed." to "Split input failed",
                "App launch failed:" to "App launch failed",
                "Close failed:" to "App close failed",
                "Pane 1:" to "Pane 1 task state unavailable",
                "Pane 2:" to "Pane 2 task state unavailable",
                "Secondary cleanup failed:" to "Secondary cleanup failed; cleanup unconfirmed",
                "Secondary service unbind failed:" to "Secondary service unbind failed",
                "Desktop task state unavailable:" to "Desktop task state unavailable",
                "Stop controller task lookup:" to "Stop controller task lookup failed",
                "Controller task lookup after Stop:" to "Controller task lookup after Stop failed",
                "Controller return after Stop:" to "Controller return after Stop failed",
            )
    }
}
