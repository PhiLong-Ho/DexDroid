package dev.dexdroid

import android.content.*
import android.os.*
import java.util.*

/** Shell-worker-only task activation/readiness; never waits on either UI thread. */
@android.annotation.SuppressLint("PrivateApi")
class LegacyInputWindow
@Throws(Exception::class)
constructor(
    private val context: Context?,
    private val display: Int,
    private val command: Command?,
) {
    private val tasks: Any?
    private val api: Class<*>?
    private var readyTask: Int = -1
    private var readyComponent: String? = ""
    private var component: String? = ""
    private var hasTask: Boolean = false
    private var readyUid: Int = -1

    fun interface Command {
        @Throws(Exception::class) fun run(vararg args: String?): String?
    }

    init {
        tasks =
            Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
        api = Class.forName("android.app.IActivityTaskManager")
    }

    fun invalidate() {
        readyTask = -1
    }

    fun component(): String? {
        return component
    }

    fun hasTask(): Boolean {
        return hasTask
    }

    @Throws(Exception::class)
    fun focused(): Boolean {
        val started = SystemClock.uptimeMillis()
        val report = command!!.run("/system/bin/dumpsys", "-t", "1", "input")
        val ready = InputWindowState.ready(report, display, readyUid, true)
        if (
            BuildConfig.DEBUG &&
                component?.startsWith("dev.dexdroid.test/dev.dexdroid.fixture.SplitProbeFixture") ==
                    true
        ) {
            val native = InputWindowState.ready(report, display, readyUid, false)
            val start = report?.indexOf("  FocusedWindows:") ?: -1
            val end = report?.indexOf("  TouchStates:", start) ?: -1
            val entry =
                start >= 0 &&
                    end > start &&
                    java.util.regex.Pattern.compile("(?m)^\\s*displayId=" + display + ", name=")
                        .matcher(report!!.substring(start, end))
                        .find()
            KeyDiagnostics.record(
                "legacy-focus",
                "display=$display task=$readyTask uid=$readyUid start=$started end=${SystemClock.uptimeMillis()} native=$native focusedEntry=$entry headers=${start >= 0 && end > start} strict=$ready",
            )
        }
        return ready
    }

    @Throws(Exception::class)
    private fun roots(): List<*>? {
        return api!!.getMethod("getAllStackInfos").invoke(tasks) as List<*>?
    }

    @Throws(Exception::class)
    fun ready(keyboard: Boolean): Boolean {
        var chosen: Any? = null
        var visibleChosen = false
        for (root in roots()!!) {
            val type = root!!.javaClass
            if (type!!.getField("displayId").getInt(root) != display) continue
            val ids = type!!.getField("taskIds").get(root) as IntArray?
            if (ids == null || ids!!.size == 0 || type!!.getField("topActivity").get(root) == null)
                continue
            var visible = false
            try {
                visible = type!!.getField("visible").getBoolean(root)
            } catch (optional: NoSuchFieldException) {
                /* Legacy ROM field is optional. */
            }

            if (chosen == null || (visible && !visibleChosen)) {
                chosen = root
                visibleChosen = visible
            }
        }
        hasTask = chosen != null
        if (!hasTask) {
            component = ""
            return false
        }
        val type = chosen!!.javaClass
        val ids = type!!.getField("taskIds").get(chosen) as IntArray?
        val selected = ids!![ids!!.size - 1]
        val top = type!!.getField("topActivity").get(chosen) as ComponentName?
        component = top!!.flattenToString()
        val uid = context!!.getPackageManager()!!.getApplicationInfo(top!!.getPackageName(), 0).uid
        val changed = selected != readyTask || component != readyComponent || uid != readyUid
        if (changed) invalidate()
        // Recents activation brings phone Home forward on legacy ROMs, even when
        // the selected task remains on this display. Launch paths own activation;
        // input readiness only focuses the task already discovered here.
        if (changed || keyboard) {
            api!!.getMethod("setFocusedTask", Int::class.javaPrimitiveType).invoke(tasks, selected)
            if (!currentTarget(selected, component, uid)) {
                invalidate()
                return false
            }
        }
        if (!changed) return true
        val deadline = SystemClock.uptimeMillis() + 600
        do {
            // Some legacy ROMs deliver display-targeted keys while FocusedWindows
            // still names the phone. Ownership + a live native window admit input;
            // focused() retains the stricter cancellation ownership check.
            val windowReady =
                InputWindowState.ready(
                    command!!.run("/system/bin/dumpsys", "-t", "1", "input"),
                    display,
                    uid,
                    false,
                )
            if (!currentTarget(selected, component, uid)) {
                invalidate()
                return false
            }
            if (windowReady) {
                readyTask = selected
                readyUid = uid
                readyComponent = component
                return true
            }
            Thread.sleep(12)
        } while (SystemClock.uptimeMillis() < deadline)
        invalidate()
        return false
    }

    @Throws(Exception::class)
    private fun currentTarget(
        selected: Int,
        expectedComponent: String?,
        expectedUid: Int,
    ): Boolean {
        for (root in roots()!!) {
            val type = root!!.javaClass
            if (type!!.getField("displayId").getInt(root) != display) continue
            val ids = type!!.getField("taskIds").get(root) as IntArray?
            if (ids == null || ids!!.size == 0 || ids!![ids!!.size - 1] != selected) continue
            val top = type!!.getField("topActivity").get(root) as ComponentName?
            return (top != null &&
                expectedComponent == top!!.flattenToString() &&
                (context!!
                    .getPackageManager()!!
                    .getApplicationInfo(top!!.getPackageName(), 0)
                    .uid == expectedUid))
        }
        hasTask = false
        return false
    }
}
