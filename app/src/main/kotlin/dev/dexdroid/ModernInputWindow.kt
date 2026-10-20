package dev.dexdroid

import android.content.*
import java.util.*

/** Reactive shell-worker diagnosis only after a rejected initial DOWN; never focuses or waits. */
@android.annotation.SuppressLint("PrivateApi")
class ModernInputWindow {

    private val display: Int
    private val source: Source?
    var diagnostic: Diagnostic? = null

    /** Optional private debug observer. All callbacks are secondary to input behavior. */
    interface Diagnostic {
        fun sample(value: Sample)

        fun event(value: String)

        fun flush()
    }

    data class Sample(
        val display: Int,
        val before: Target?,
        val windowsQueried: Boolean,
        val report: String?,
        val state: ModernInputWindowState.State?,
        val afterQueried: Boolean,
        val after: Target?,
        val stage: String,
        val errorClass: String?,
        val rejectedDown: String?,
        val startNanos: Long,
        val endNanos: Long,
    )

    fun diagnosticEvent(value: String) {
        try {
            diagnostic?.event(value)
        } catch (_: Throwable) {
            /* Secondary observer only. */
        }
    }

    fun flushDiagnostic() {
        try {
            diagnostic?.flush()
        } catch (_: Throwable) {
            /* Preserve primary input outcome. */
        }
    }

    class Target(
        @JvmField val task: Int,
        @JvmField val uid: Int,
        @JvmField val component: String?,
    ) {

        fun same(other: Target?): Boolean {
            return (other != null &&
                task == other!!.task &&
                uid == other!!.uid &&
                component!!.equals(other!!.component))
        }
    }

    interface Source {
        @Throws(Exception::class) fun target(): Target?

        @Throws(Exception::class) fun windows(): String?
    }

    constructor(display: Int, source: Source?) {
        this.display = display
        this.source = source
    }

    @Throws(Exception::class)
    constructor(context: Context?, display: Int, command: LegacyInputWindow.Command?) {
        this.display = display
        val manager =
            Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
        val api = Class.forName("android.app.IActivityTaskManager")
        source =
            object : Source {
                @Throws(Exception::class)
                public override fun target(): Target? {
                    var chosen: Any? = null
                    var visibleChosen = false
                    for (root in
                        (api.getMethod("getAllRootTaskInfos").invoke(manager) as List<*>?)!!) {
                        val type = root!!.javaClass
                        if (type!!.getField("displayId").getInt(root) != display) continue
                        val ids = type!!.getField("childTaskIds").get(root) as IntArray?
                        if (
                            (ids == null ||
                                ids!!.size == 0 ||
                                type!!.getField("topActivity").get(root) == null)
                        )
                            continue
                        val visible = type!!.getField("visible").getBoolean(root)
                        if (chosen == null || (visible && !visibleChosen)) {
                            chosen = root
                            visibleChosen = visible
                        }
                    }
                    if (chosen == null) return null
                    val type = chosen!!.javaClass
                    val ids = type!!.getField("childTaskIds").get(chosen) as IntArray?
                    val top = type!!.getField("topActivity").get(chosen) as ComponentName?
                    return Target(
                        ids!![ids!!.size - 1],
                        context!!
                            .getPackageManager()!!
                            .getApplicationInfo(top!!.getPackageName(), 0)
                            .uid,
                        top!!.flattenToString(),
                    )
                }

                @Throws(Exception::class)
                public override fun windows(): String? {
                    return command!!.run("/system/bin/dumpsys", "-t", "1", "input")
                }
            }
    }

    @JvmOverloads
    @Throws(Exception::class)
    fun pendingComponent(rejectedDown: String? = null): String? {
        val observer = diagnostic
        val start = if (observer != null) System.nanoTime() else 0
        var before: Target? = null
        var report: String? = null
        var state: ModernInputWindowState.State? = null
        var after: Target? = null
        var windowsQueried = false
        var afterQueried = false
        var stage = "before-target"
        var errorClass: String? = null
        try {
            before = source!!.target()
            if (before == null) {
                stage = "before-null"
                return null
            }
            stage = "windows"
            windowsQueried = true
            report = source!!.windows()
            stage = "inspect"
            state = ModernInputWindowState.inspect(report, display, before.uid)
            stage = "after-target"
            afterQueried = true
            after = source!!.target()
            val stable = before.same(after)
            stage = if (!stable) "target-changed" else "decision-$state"
            return if (stable && state == ModernInputWindowState.State.PENDING) before.component
            else null
        } catch (error: Throwable) {
            errorClass = error.javaClass.name
            throw error
        } finally {
            if (observer != null) {
                try {
                    observer.sample(
                        Sample(
                            display,
                            before,
                            windowsQueried,
                            report,
                            state,
                            afterQueried,
                            after,
                            stage,
                            errorClass,
                            rejectedDown,
                            start,
                            System.nanoTime(),
                        )
                    )
                } catch (_: Throwable) {
                    /* Never replace the original result or failure. */
                }
            }
        }
    }
}
