package dev.dexdroid

import java.util.*
import java.util.regex.*

/** Conservative current InputDispatcher evidence. Unknown reports never authorize recovery. */
object ModernInputWindowState {

    private val SECTION: Pattern = Pattern.compile("(?m)^  Display: (\\d+)\\s*$")
    private val BOUNDARY: Pattern = Pattern.compile("(?m)^  \\S")
    private val FRAME: Pattern =
        Pattern.compile("frame=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]")
    private val FLAGS: Set<String?> =
        HashSet<String?>(
            Arrays.asList<String?>(
                "NOT_VISIBLE",
                "NOT_TOUCHABLE",
                "NOT_FOCUSABLE",
                "NO_INPUT_CHANNEL",
                "PAUSE_DISPATCHING",
                "DROP_INPUT",
                "DROP_INPUT_IF_OBSCURED",
                "TRUSTED_OVERLAY",
                "PREVENT_SPLITTING",
                "SLIPPERY",
                "WATCH_OUTSIDE_TOUCH",
                "SPY",
                "INTERCEPTS_STYLUS",
                "DUPLICATE_TOUCH_TO_WALLPAPER",
                "IS_WALLPAPER",
                "DISABLE_USER_ACTIVITY",
                "SENSITIVE_FOR_PRIVACY",
            )
        )

    enum class State {
        READY,
        PENDING,
        UNKNOWN,
    }

    @JvmStatic
    fun inspect(report: String?, display: Int, uid: Int): State {
        if (
            (report == null ||
                display <= 0 ||
                uid < 1000 ||
                report!!.contains("DUMP TIMEOUT") ||
                report!!.contains("Permission Denial") ||
                report!!.contains("Error:"))
        )
            return State.UNKNOWN
        val start = report!!.indexOf("Input Dispatcher State:\n")
        if (start < 0) return State.UNKNOWN
        val current = report!!.substring(start)
        if (
            (!current.contains("\n  DispatchEnabled: true\n") ||
                !current.contains("\n  DispatchFrozen: false\n"))
        )
            return State.UNKNOWN
        val sections = SECTION.matcher(current)
        while (sections.find()) {
            if (sections.group(1) != Integer.toString(display)) continue
            val from = sections.end()
            val boundary = BOUNDARY.matcher(current)
            if (!boundary.find(from)) return State.UNKNOWN
            val section = current.substring(from, boundary.start())
            if (!section.contains("\n    logicalSize=") || !section.contains("\n    Windows:"))
                return State.UNKNOWN
            val windows = section.substring(section.indexOf("\n    Windows:") + 13)
            var ready = false
            for (line in
                windows.split(("\n").toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()) {
                if (!line!!.matches(("\\s*\\d+:.*").toRegex())) {
                    val detail = line!!.trim({ it <= ' ' })
                    if (
                        (detail.isEmpty() ||
                            detail == "<none>" ||
                            detail.startsWith("transform (") ||
                            detail.matches(("[-0-9.]+\\s+[-0-9.]+\\s+[-0-9.]+").toRegex()))
                    )
                        continue
                    return State.UNKNOWN
                }
                if (
                    (!line!!.contains("name=") ||
                        !line!!.contains(", displayId=" + display + ",") ||
                        !line!!.contains("dispatchingTimeout=") ||
                        !line!!.contains("touchOcclusionMode="))
                )
                    return State.UNKNOWN
                val owner = Pattern.compile("\\bownerUid=(\\d+),").matcher(line!!)
                if (!owner.find()) return State.UNKNOWN
                if (owner.group(1) != Integer.toString(uid)) continue
                val config = Pattern.compile("\\binputConfig=([^,]+),").matcher(line!!)
                val frame = FRAME.matcher(line!!)
                if (
                    (!config.find() ||
                        !frame.find() ||
                        !line!!.contains("applicationInfo.name=") ||
                        !line!!.contains("applicationInfo.token=") ||
                        !line!!.contains("touchableRegion="))
                )
                    return State.UNKNOWN
                val flags = HashSet<String?>()
                if (config.group(1) != "0x0") {
                    for (flag in
                        config
                            .group(1)!!
                            .split(("\\s*\\|\\s*").toRegex())
                            .dropLastWhile({ it.isEmpty() })
                            .toTypedArray()) {
                        if (!FLAGS.contains(flag)) return State.UNKNOWN
                        flags.add(flag)
                    }
                }
                val blocked =
                    (flags.contains("NOT_VISIBLE") ||
                        flags.contains("NOT_TOUCHABLE") ||
                        flags.contains("NO_INPUT_CHANNEL") ||
                        flags.contains("PAUSE_DISPATCHING") ||
                        flags.contains("DROP_INPUT"))
                if (flags.contains("DROP_INPUT_IF_OBSCURED") || flags.contains("SPY"))
                    return State.UNKNOWN
                val app =
                    (!line!!.contains("applicationInfo.name=,") &&
                        !line!!.contains("applicationInfo.token=<null>") &&
                        !line!!.contains("applicationInfo.token=0x0,"))
                val area =
                    (Integer.parseInt(frame.group(3)!!) > Integer.parseInt(frame.group(1)!!) &&
                        (Integer.parseInt(frame.group(4)!!) > Integer.parseInt(frame.group(2)!!)) &&
                        !line!!.contains("touchableRegion=<empty>"))
                ready = ready or (app && area && !blocked)
            }
            return if (ready) State.READY else State.PENDING
        }
        return State.UNKNOWN
    }
}
