package dev.dexdroid

import java.util.*
import java.util.regex.*

/** Test setup only; never included in release artifacts. */
internal class FixtureTaskIsolation {
    companion object {
        private val COMPONENT: String =
            "dev\\.dexdroid\\.test/(?:dev\\.dexdroid\\.fixture\\.|\\.fixture\\.)(?:LaunchParentActivity|LaunchReaderActivity)"

        @JvmStatic
        fun ownedRoots(report: String): List<DisplayTasks.Root?> {
            if (
                (report.contains("Error:") ||
                    report.contains("DUMP TIMEOUT") ||
                    report.contains("Permission Denial"))
            )
                throw IllegalStateException("Fixture task snapshot unavailable")
            val parsed =
                DisplayTasks.parse(
                    report,
                    "dev.dexdroid.test/dev.dexdroid.fixture.LaunchParentActivity",
                )
            if (parsed.isEmpty())
                throw IllegalStateException("No recognized current-user task snapshot")
            val owned = ArrayList<DisplayTasks.Root?>()
            val blocks =
                report
                    .split(("(?m)(?=^(?:Stack|RootTask) )").toRegex())
                    .dropLastWhile({ it.isEmpty() })
                    .toTypedArray()
            val known = Pattern.compile(COMPONENT + "(?=\\s|$)")
            val task =
                Pattern.compile(
                    "(?m)^\\s*taskId=(\\d+):\\s+" +
                        COMPONENT +
                        "\\s+(?:bounds=\\[-?\\d+,-?\\d+\\]\\[-?\\d+,-?\\d+\\]\\s+)?userId=0\\b[^\\n]*$"
                )
            val header = Pattern.compile("^(?:Stack|RootTask) id=(\\d+)\\b")
            for (block in blocks) {
                if (!known.matcher(block!!).find()) continue
                val head = header.matcher(block!!)
                val entry = task.matcher(block!!)
                if (!head.find() || !entry.find())
                    throw IllegalStateException("Unrecognized fixture task ownership")
                val id = Integer.parseInt(head.group(1)!!)
                val taskId = Integer.parseInt(entry.group(1)!!)
                val allTasks = Pattern.compile("(?m)^\\s*taskId=").matcher(block!!)
                var count = 0
                while (allTasks.find()) count++
                var match: DisplayTasks.Root? = null
                for (root in parsed) if (root!!.id == id) {
                    if (match != null) throw IllegalStateException("Duplicate fixture root")
                    match = root
                }
                if (
                    (match == null ||
                        count != 1 ||
                        match!!.tasks != 1 ||
                        match!!.taskId != taskId ||
                        !match!!.matches ||
                        entry.find())
                )
                    throw IllegalStateException("Shared or ambiguous fixture root")
                for (previous in owned) if (previous!!.id == id || previous!!.taskId == taskId)
                    throw IllegalStateException("Duplicate fixture root or task")
                owned.add(match)
            }
            return owned
        }
    }
}
