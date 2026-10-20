package dev.dexdroid

import java.util.regex.Pattern

/** Exit status and actual shell error lines, excluding embedded dump metric names. */
internal object ShellCommandResult {
    private val ERROR = Pattern.compile("(?m)^[\\t ]*Error:[^\\r\\n]*")

    @JvmStatic
    fun failure(exit: Int, output: String): String? {
        val match = ERROR.matcher(output)
        if (match.find()) {
            // Preserve Java String.trim(), which trims only characters at or below U+0020.
            val line = match.group().trim { it <= ' ' }
            return "Shell command failed (exit " +
                exit +
                "): " +
                line.substring(0, Math.min(240, line.length))
        }
        return if (exit == 0) null else "Shell command failed (exit " + exit + ")"
    }
}
