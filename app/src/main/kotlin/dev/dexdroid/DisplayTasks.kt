package dev.dexdroid

import java.util.ArrayList
import java.util.regex.Pattern

/** Task placement evidence from legacy Stack and modern RootTask shell reports. */
internal class DisplayTasks {
    class Root {
        @JvmField var id: Int = 0
        @JvmField var display: Int = 0
        @JvmField var tasks: Int = 0
        @JvmField var taskId: Int = 0
        @JvmField var matches: Boolean = false
        @JvmField var visible: Boolean = false
        @JvmField var fullscreen: Boolean = false
        @JvmField var exactComponent: Boolean = false
        // Null distinguishes missing/ambiguous native evidence from confirmed hidden.
        @JvmField var nativeVisible: Boolean? = null
        @JvmField var nativeTaskRows: Int = 0
        @JvmField var nativeUserZero: Boolean = false
    }

    companion object {
        @JvmStatic
        fun parse(report: String?, component: String): List<Root?> {
            val pkg = component.substring(0, component.indexOf('/'))
            var activity: String? = component.substring(component.indexOf('/') + 1)
            if (activity!!.startsWith(".")) activity = pkg + activity!!
            val header =
                Pattern.compile(
                    "^(?:Stack|RootTask) id=(\\d+)\\b.*\\bdisplayId=(\\d+)\\b.*\\buserId=(\\d+)\\b"
                )
            val task = Pattern.compile("^\\s*taskId=(\\d+):.*\\buserId=0\\b.*")
            val componentField = Pattern.compile("^\\s*taskId=\\d+:\\s+(\\S+)(?=\\s|$)")
            val visibilityField = Pattern.compile("(?:^|\\s)visible(?:=\\S*|(?=\\s|$))")
            val userField = Pattern.compile("(?:^|\\s)userId(?:=\\S*|(?=\\s|$))")
            val taskField = Pattern.compile("(?:^|\\s)taskId(?:=\\S*|(?=\\s|$))")
            val roots = ArrayList<Root?>()
            var root: Root? = null
            for (line in
                report!!.split(("\n").toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()) {
                val match = header.matcher(line!!)
                if (line!!.startsWith("Stack ") || line!!.startsWith("RootTask ")) {
                    root = null
                    if (match.find() && match.group(3) == "0") {
                        root = Root()
                        root!!.id = Integer.parseInt(match.group(1)!!)
                        root!!.display = Integer.parseInt(match.group(2)!!)
                        root!!.nativeUserZero = singleField(userField.matcher(line)) == "userId=0"
                        roots.add(root)
                    }
                } else if (root != null) {
                    if (
                        line.trimStart().startsWith("RootTask ") ||
                            line.trimStart().startsWith("Stack ")
                    )
                        root!!.nativeUserZero = false
                    if (line!!.trim({ it <= ' ' }).startsWith("configuration="))
                        root!!.fullscreen = line!!.contains("mWindowingMode=fullscreen")
                    if (line.trimStart().startsWith("taskId")) root!!.nativeTaskRows++
                    val entry = task.matcher(line!!)
                    if (!entry.matches()) continue
                    root!!.tasks++
                    val nativeComponent = componentField.matcher(line)
                    if (nativeComponent.find()) {
                        val name = nativeComponent.group(1)!!
                        val normalized =
                            if (name.startsWith(pkg + "/."))
                                pkg + "/" + pkg + name.substring(pkg.length + 1)
                            else name
                        if (normalized == pkg + "/" + activity) {
                            root!!.exactComponent = true
                            if (
                                singleField(userField.matcher(line)) == "userId=0" &&
                                    singleField(taskField.matcher(line)) ==
                                        "taskId=" + entry.group(1) + ":"
                            ) {
                                root!!.nativeVisible =
                                    when (singleField(visibilityField.matcher(line))) {
                                        "visible=true" -> true
                                        "visible=false" -> false
                                        else -> null
                                    }
                            }
                        }
                    }
                    val target =
                        (line!!.contains(pkg + "/") &&
                            (pkg != "dev.dexdroid" || line!!.contains(activity!!)))
                    if (target) {
                        val visible = line!!.contains("visible=true")
                        if (!root!!.matches || visible)
                            root!!.taskId = Integer.parseInt(entry.group(1)!!)
                        root!!.matches = true
                        root!!.visible = root!!.visible or visible
                    }
                }
            }
            return roots
        }

        private fun singleField(matcher: java.util.regex.Matcher): String? {
            if (!matcher.find()) return null
            val value = matcher.group().trim()
            return if (matcher.find()) null else value
        }
    }
}
