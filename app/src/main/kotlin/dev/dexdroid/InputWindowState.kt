package dev.dexdroid

import java.util.regex.*

/** Android 11 input-dispatcher evidence, independent of Activity/task visibility. */
object InputWindowState {

    @JvmStatic
    fun ready(report: String?, display: Int, uid: Int, keyboard: Boolean): Boolean {
        if (display <= 0 || uid < 1000) return false
        val sections =
            report!!
                .split(("(?m)^  Display: ").toRegex())
                .dropLastWhile({ it.isEmpty() })
                .toTypedArray()
        var window = false
        for (section in sections) {
            val end = section!!.indexOf('\n')
            if (
                end < 0 ||
                    section!!.substring(0, end).trim({ it <= ' ' }) != Integer.toString(display)
            )
                continue
            for (line in
                section!!.split(("\n").toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()) {
                if (
                    (!line!!.contains("name=") ||
                        !line!!.contains("visible=true") ||
                        line!!.contains("paused=true"))
                )
                    continue
                val owner = Pattern.compile("\\bownerUid=(\\d+)\\b").matcher(line!!)
                var actual = -1
                while (owner.find()) actual = Integer.parseInt(owner.group(1)!!)
                if (actual != uid || line!!.contains("touchableRegion=<empty>")) continue
                val type = Pattern.compile("\\btype=0x([0-9a-fA-F]+)\\b").matcher(line!!)
                if (!type.find()) continue
                val windowType = java.lang.Long.parseLong(type.group(1)!!, 16)
                if (windowType < 1 || windowType >= 2000) continue
                val flags = Pattern.compile("\\bflags=0x([0-9a-fA-F]+)\\b").matcher(line!!)
                if (!flags.find() || (java.lang.Long.parseLong(flags.group(1)!!, 16) and 16) != 0L)
                    continue
                val frame =
                    Pattern.compile("\\bframe=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]")
                        .matcher(line!!)
                if (
                    (frame.find() &&
                        Integer.parseInt(frame.group(3)!!) > Integer.parseInt(frame.group(1)!!) &&
                        Integer.parseInt(frame.group(4)!!) > Integer.parseInt(frame.group(2)!!))
                )
                    window = true
            }
        }
        if (!window || !keyboard) return window
        val start = report!!.indexOf("  FocusedWindows:")
        val end = report!!.indexOf("  TouchStates:", start)
        return (start >= 0 &&
            end > start &&
            Pattern.compile("(?m)^\\s*displayId=" + display + ", name=")
                .matcher(report!!.substring(start, end))
                .find())
    }
}
