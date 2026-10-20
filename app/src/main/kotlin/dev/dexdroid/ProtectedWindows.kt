package dev.dexdroid

import java.io.*
import java.util.regex.*

/** Pure, conservative parser. Window titles and content never leave this reader. */
internal class ProtectedWindows {

    enum class State {
        UNKNOWN,
        CLEAR,
        PROTECTED,
    }

    private class Window {
        var display: Int = -1
        var flags: Int = -1
        var surfaced: Boolean? = null
        var visible: Boolean? = null
        var visibilitySeen: Boolean = false
        var visibilityInvalid: Boolean = false

        fun visibility(line: String) {
            val fields = VISIBILITY_FIELD.matcher(line)
            if (!fields.find()) return
            if (visibilitySeen || fields.find()) visibilityInvalid = true
            visibilitySeen = true
            val match = VISIBILITY.matcher(line)
            visible =
                if (!visibilityInvalid && match.matches()) match.group(1) == "true"
                else {
                    visibilityInvalid = true
                    null
                }
        }

        fun state(target: Int): State {
            if (display < 0) return State.UNKNOWN
            if (display != target) return State.CLEAR
            if (java.lang.Boolean.FALSE == surfaced || java.lang.Boolean.FALSE == visible)
                return State.CLEAR
            if (surfaced == null || visible == null || flags < 0) return State.UNKNOWN
            return if (flags == 1) State.PROTECTED else State.CLEAR
        }
    }

    companion object {
        private val WINDOW: Pattern = Pattern.compile("^\\s*Window #\\d+ Window\\{.*")
        private val DISPLAY: Pattern = Pattern.compile("^\\s*mDisplayId=(\\d+)\\b")
        private val FLAGS: Pattern = Pattern.compile("^\\s*fl=([^\\r\\n}]+)")
        private val SECURE: Pattern = Pattern.compile("(?:^|[ |])(FLAG_)?SECURE(?:$|[ |])")
        private val VISIBILITY_FIELD: Pattern = Pattern.compile("\\bisOnScreen\\b")
        private val VISIBILITY: Pattern =
            Pattern.compile(
                "^\\s*(?:mForceSeamlesslyRotate=(?:true|false)\\s+" +
                    "seamlesslyRotate: pending=.+\\s+)?isOnScreen=(true|false)\\s*$"
            )

        @Throws(IOException::class)
        @JvmStatic
        fun read(source: Reader?, target: Int): State {
            if (target <= 0) return State.UNKNOWN
            val reader = BufferedReader(source!!)
            var header = false
            var unknown = false
            var secure = false
            var window: Window? = null
            var size = 0
            while (true) {
                val line = reader.readLine() ?: break
                size += line.length
                if (size > 1048576) return State.UNKNOWN
                if (line!!.contains("DUMP TIMEOUT") || line!!.contains("Permission Denial"))
                    return State.UNKNOWN
                // JoyUI may omit the newline after its last display diagnostic.
                if (line!!.endsWith("WINDOW MANAGER WINDOWS (dumpsys window windows)"))
                    header = true
                if (WINDOW.matcher(line!!).matches()) {
                    if (window != null) {
                        secure = secure or (window!!.state(target) == State.PROTECTED)
                        unknown = unknown or (window!!.state(target) == State.UNKNOWN)
                    }
                    window = Window()
                    continue
                }
                if (window == null) continue
                var match: Matcher? = DISPLAY.matcher(line!!)
                if (window!!.display < 0 && match!!.find())
                    window!!.display = Integer.parseInt(match!!.group(1)!!)
                if (line!!.contains("mHasSurface="))
                    window!!.surfaced = line!!.contains("mHasSurface=true")
                window!!.visibility(line)
                match = FLAGS.matcher(line!!)
                if (window!!.flags < 0 && match!!.find()) {
                    val value = match!!.group(1)!!.trim({ it <= ' ' })
                    try {
                        window!!.flags =
                            if (
                                (java.lang.Long.parseLong(
                                    value.replaceFirst(("^0x").toRegex(), ""),
                                    16,
                                ) and 0x2000) != 0L
                            )
                                1
                            else 0
                    } catch (namedFlags: NumberFormatException) {
                        if (value.matches(("[A-Z_ |]+").toRegex()))
                            window!!.flags = if (SECURE.matcher(value).find()) 1 else 0
                    }
                }
            }
            if (window != null) {
                secure = secure or (window!!.state(target) == State.PROTECTED)
                unknown = unknown or (window!!.state(target) == State.UNKNOWN)
            }
            return if (!header) State.UNKNOWN
            else if (secure) State.PROTECTED else if (unknown) State.UNKNOWN else State.CLEAR
        }
    }
}
