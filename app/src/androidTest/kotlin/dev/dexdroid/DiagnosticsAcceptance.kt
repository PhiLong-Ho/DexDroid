package dev.dexdroid

import android.app.Activity
import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.text.Selection
import android.text.Spannable
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real controller views and exports; chooser interception never sends a report to another app. */
internal class DiagnosticsAcceptance(private val runner: Instrumentation) {
    private lateinit var session: SessionService
    private lateinit var phone: MainActivity
    private val results = StringBuilder()

    private fun main(action: () -> Unit) = runner.runOnMainSync { action() }

    private fun check(ok: Boolean, label: String) {
        if (!ok) throw AssertionError(label)
        results.append("PASS $label\n")
    }

    private fun await(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (!condition() && SystemClock.uptimeMillis() < deadline) Thread.sleep(30)
        check(condition(), label)
    }

    private fun find(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                find(root.getChildAt(index), predicate)?.let {
                    return it
                }
            }
        }
        return null
    }

    private fun root(prefix: String): View =
        if (prefix == "phone") phone.controllerMenu()!!.window!!.decorView
        else session.monitorShellView()!!

    private fun history(prefix: String): TextView =
        root(prefix).findViewWithTag("$prefix-diagnostic-text")

    private fun scroll(prefix: String): ScrollView =
        checkNotNull(find(root(prefix)) { it is ScrollView }) as ScrollView

    private fun reveal(view: View) {
        var ancestor = view.parent
        while (ancestor is View) {
            if (ancestor is ScrollView) {
                val bounds = Rect(0, 0, view.width, view.height)
                ancestor.offsetDescendantRectToMyCoords(view, bounds)
                ancestor.scrollTo(0, bounds.top)
                return
            }
            ancestor = ancestor.parent
        }
    }

    private fun click(prefix: String, tag: String) {
        await("laid out $tag") {
            val view = root(prefix).findViewWithTag<View>(tag)
            view != null && view.isShown && view.isEnabled && view.width > 0 && view.height > 0
        }
        main {
            val target = checkNotNull(root(prefix).findViewWithTag<View>(tag))
            reveal(target)
        }
        runner.waitForIdleSync()
        main {
            val target = checkNotNull(root(prefix).findViewWithTag<View>(tag))
            val visible = Rect()
            check(target.getGlobalVisibleRect(visible), "$tag reachable in scroll viewport")
            check(target.isEnabled && target.isClickable, "$tag actionable")
            target.performClick()
        }
    }

    private fun clickText(prefix: String, label: String) {
        main {
            var target =
                checkNotNull(find(root(prefix)) { it is TextView && it.text.toString() == label })
            while (!target.isClickable && target.parent is View) target = target.parent as View
            reveal(target)
            check(target.isEnabled && target.isShown, "enabled $prefix $label")
            target.performClick()
        }
    }

    private fun shell(): MonitorShell {
        val monitor =
            SessionService::class
                .java
                .getDeclaredField("monitor")
                .apply { isAccessible = true }
                .get(session)
        return monitor.javaClass
            .getDeclaredField("shell")
            .apply { isAccessible = true }
            .get(monitor) as MonitorShell
    }

    private fun page(name: String) =
        await("monitor page $name") {
            session.monitorShellPage() == name && !session.monitorShellAnimating()
        }

    private fun openPhone() {
        main {
            checkNotNull(find(phone.window.decorView) { it.contentDescription == "Settings" })
                .performClick()
        }
        clickText("phone", "Diagnostic log")
        await("phone diagnostic page ready") {
            root("phone").findViewWithTag<TextView>("phone-menu-title")?.text == "Diagnostic log"
        }
    }

    private fun openMonitor() {
        main { shell().show("settings") }
        page("settings")
        click("monitor", "monitor-diagnostics")
        page("diagnostics")
    }

    private fun fixture(display: Int) = "Key sent: KEYCODE_BACK -> display $display"

    private fun populate() {
        main {
            session.clearDiagnostics()
            repeat(300) { session.log(fixture(it)) }
        }
    }

    private fun panel(prefix: String) {
        await("$prefix bounded latest event visible") {
            history(prefix).text.contains("display 299")
        }
        main {
            val text = history(prefix)
            check(
                !text.text.contains("display 0\n") && text.text.lines().size <= 250,
                "$prefix oldest event evicted and history bounded",
            )
            check(
                text.isTextSelectable &&
                    text.textSize / text.resources.displayMetrics.scaledDensity >= 13.5f,
                "$prefix readable selectable event history",
            )
            val context = root(prefix).findViewWithTag<TextView>("$prefix-diagnostic-context")
            check(
                context.text.toString() == session.diagnosticContext(),
                "$prefix current context matches session",
            )
            check(
                context.text.contains("Rotation") && context.text.contains("Bridge"),
                "$prefix display and bridge context visible",
            )
            for (action in listOf("copy", "share", "clear")) {
                val target = root(prefix).findViewWithTag<View>("$prefix-log-$action")
                check(
                    target.parent === text.parent &&
                        (target.parent as ViewGroup).indexOfChild(target) <
                            (text.parent as ViewGroup).indexOfChild(text),
                    "$prefix $action precedes long history",
                )
                check(
                    target.height >= 48 * target.resources.displayMetrics.density &&
                        target.isFocusable,
                    "$prefix $action preserves touch target and keyboard focus",
                )
            }
            check(MenuTextAssertions.check(root(prefix)) > 0, "$prefix diagnostic text renders")
            scroll(prefix).fullScroll(View.FOCUS_DOWN)
        }
        await("$prefix full history scroll reaches bottom") {
            !scroll(prefix).canScrollVertically(1)
        }
        click(prefix, "$prefix-log-copy")
        main {
            val clipboard =
                runner.targetContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            check(
                clipboard.primaryClip?.getItemAt(0)?.text.toString() == session.diagnosticText(),
                "$prefix Copy exports full shared report",
            )
            check(
                root(prefix)
                    .findViewWithTag<TextView>("$prefix-diagnostic-feedback")
                    .text
                    .contains("copied"),
                "$prefix Copy feedback visible",
            )
        }
    }

    private fun selection(prefix: String) {
        var selected: String? = null
        var position = 0
        main {
            val text = history(prefix)
            reveal(text)
            text.requestFocus()
            Selection.setSelection(text.text as Spannable, 0, 6)
            selected = text.text.toString()
            position = scroll(prefix).scrollY
            session.log(fixture(401))
        }
        Thread.sleep(1200) // Cross both controllers' scheduled refresh intervals.
        main {
            val text = history(prefix)
            check(
                text.text.toString() == selected &&
                    text.selectionStart == 0 &&
                    text.selectionEnd == 6,
                "$prefix live refresh preserves active selection",
            )
            check(
                scroll(prefix).scrollY == position,
                "$prefix live refresh preserves reading position",
            )
            Selection.removeSelection(text.text as Spannable)
        }
        await("$prefix refresh resumes after selection ends") {
            history(prefix).text.contains("display 401")
        }
    }

    private fun share(prefix: String) {
        var captured: Intent? = null
        var fail = false
        val monitor =
            object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent?): Instrumentation.ActivityResult? {
                    if (intent?.action != Intent.ACTION_CHOOSER) return null
                    captured = Intent(intent)
                    if (fail) throw ActivityNotFoundException("Controlled chooser unavailable")
                    return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                }
            }
        runner.addMonitor(monitor)
        try {
            var expected = ""
            main { expected = session.diagnosticText() }
            click(prefix, "$prefix-log-share")
            main {
                val chooser = checkNotNull(captured)
                val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                check(
                    send.action == Intent.ACTION_SEND &&
                        send.type == "text/plain" &&
                        send.getStringExtra(Intent.EXTRA_TEXT) == expected,
                    "$prefix Share captures same report as Copy",
                )
                check(
                    chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0,
                    "$prefix sharing uses a separate chooser task",
                )
                session.log(fixture(402))
                check(
                    send.getStringExtra(Intent.EXTRA_TEXT) == expected,
                    "$prefix shared snapshot stays immutable after new events",
                )
                check(
                    root(prefix)
                        .findViewWithTag<TextView>("$prefix-diagnostic-feedback")
                        .text
                        .contains("opened on the phone"),
                    "$prefix canceled chooser leaves usable diagnostic page",
                )
            }
            fail = true
            click(prefix, "$prefix-log-share")
            main {
                check(
                    root(prefix)
                        .findViewWithTag<TextView>("$prefix-diagnostic-feedback")
                        .text
                        .contains("Use Copy log instead"),
                    "$prefix unavailable sharing explains Copy recovery",
                )
            }
            click(prefix, "$prefix-log-copy")
        } finally {
            runner.removeMonitor(monitor)
        }
    }

    private fun clear(prefix: String) {
        click(prefix, "$prefix-log-clear")
        await("$prefix Clear shows empty history") {
            history(prefix).text.toString() == "No events recorded."
        }
        main {
            check(
                root(prefix)
                    .findViewWithTag<TextView>("$prefix-diagnostic-feedback")
                    .text
                    .contains("cleared"),
                "$prefix Clear confirms removal",
            )
            check(
                root(prefix)
                    .findViewWithTag<TextView>("$prefix-diagnostic-context")
                    .text
                    .toString() == session.diagnosticContext(),
                "$prefix Clear preserves current context",
            )
            session.log(fixture(403))
        }
        await("$prefix Clear does not disable subsequent events") {
            history(prefix).text.contains("display 403")
        }
    }

    private fun shot(name: String, monitor: Boolean = false) {
        Thread.sleep(if (monitor) 250 else 500)
        runner.waitForIdleSync()
        val bitmap: Bitmap
        if (!monitor) bitmap = checkNotNull(runner.uiAutomation.takeScreenshot())
        else {
            val window = checkNotNull(session.monitorWindow())
            bitmap =
                Bitmap.createBitmap(
                    window.decorView.width,
                    window.decorView.height,
                    Bitmap.Config.ARGB_8888,
                )
            val done = CountDownLatch(1)
            var code = -1
            main {
                PixelCopy.request(
                    window,
                    bitmap,
                    {
                        code = it
                        done.countDown()
                    },
                    Handler(Looper.getMainLooper()),
                )
            }
            check(
                done.await(5, TimeUnit.SECONDS) && code == PixelCopy.SUCCESS,
                "capture monitor $name",
            )
        }
        val directory =
            File(runner.targetContext.externalCacheDir, "diagnostics-acceptance").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it), "capture $name")
        }
        bitmap.recycle()
    }

    private fun outside() {
        val viewport = DeviceTestSupport.monitorViewport(runner, session)
        val point = viewport.mapVirtualToScreen(4 * session.dpi / 160f, session.height() * .5f)
        DeviceTestSupport.usableMonitorOutside(runner, session, point)
        viewport.verify(runner, session)
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val pointer =
                MotionEvent.PointerProperties().apply {
                    id = 7
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            val coordinates =
                MotionEvent.PointerCoords().apply {
                    x = point[0]
                    y = point[1]
                    pressure = 1f
                }
            val event =
                MotionEvent.obtain(
                    down,
                    SystemClock.uptimeMillis(),
                    action,
                    1,
                    arrayOf(pointer),
                    arrayOf(coordinates),
                    0,
                    0,
                    1f,
                    1f,
                    0,
                    0,
                    InputDevice.SOURCE_TOUCHSCREEN,
                    0,
                )
            try {
                check(
                    DeviceTestSupport.injectOnDisplay(event, viewport.externalDisplay),
                    "native diagnostic outside contact $action",
                )
            } finally {
                event.recycle()
            }
        }
        page("closed")
        check(session.id == viewport.desktopDisplay, "outside dismissal preserves desktop")
    }

    fun run(): String {
        val ready = CountDownLatch(1)
        val binding =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    session = (binder as SessionService.LocalBinder).get()
                    ready.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }
        phone = DeviceTestSupport.startPhone(runner)
        check(
            runner.targetContext.bindService(
                Intent(runner.targetContext, SessionService::class.java),
                binding,
                Context.BIND_AUTO_CREATE,
            ),
            "bind diagnostic acceptance session",
        )
        var originalRotation = -1
        var preferences: Map<String, *>? = null
        var clipboard: ClipData? = null
        var primaryFailure: Throwable? = null
        try {
            check(ready.await(5, TimeUnit.SECONDS), "diagnostic session ready")
            await("desktop ready for diagnostics") {
                session.id >= 0 && !session.busy && !session.awaitingConfirmation()
            }
            preferences = HashMap(session.prefs!!.all)
            main {
                clipboard =
                    (runner.targetContext.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager)
                        .primaryClip
            }
            originalRotation = session.rotation
            DeviceTestSupport.startInputDriver(runner)
            populate()
            openPhone()
            panel("phone")
            selection("phone")
            share("phone")
            main { scroll("phone").scrollTo(0, 0) }
            shot("phone-log")
            clear("phone")
            click("phone", "phone-menu-up")
            check(
                root("phone").findViewWithTag<TextView>("phone-menu-title").text == "Settings",
                "phone Up returns to Settings",
            )
            click("phone", "phone-menu-close")
            check(
                phone.controllerMenu()?.isShowing != true,
                "phone Close dismisses diagnostic flow",
            )
            for (rotation in 0..3) {
                main { session.rotate(rotation, false) }
                await("diagnostic rotation $rotation ready") {
                    !session.busy && session.rotation == rotation
                }
                populate()
                openMonitor()
                panel("monitor")
                if (rotation == 0) {
                    selection("monitor")
                    share("monitor")
                }
                main { scroll("monitor").scrollTo(0, 0) }
                shot("monitor-log-r$rotation", true)
                clear("monitor")
                click("monitor", "monitor-up")
                page("settings")
                click("monitor", "monitor-diagnostics")
                page("diagnostics")
                if (rotation == 0) outside()
                else {
                    click("monitor", "monitor-close")
                    page("closed")
                }
            }
            main { session.rotate(0, false) }
            await("diagnostic Fit rotation ready") { !session.busy && session.rotation == 0 }
            main { session.preview(1920, 1080, 240) }
            await("diagnostic Fit preview ready") {
                !session.busy && session.awaitingConfirmation()
            }
            populate()
            openMonitor()
            panel("monitor")
            main { scroll("monitor").scrollTo(0, 0) }
            shot("monitor-log-fit", true)
            click("monitor", "monitor-close")
            main { session.confirm(false) }
            await("diagnostic Fit rollback ready") {
                !session.busy && !session.awaitingConfirmation()
            }
            return results.toString()
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            var cleanupFailure: Throwable? = null
            fun cleanup(action: () -> Unit) {
                try {
                    action()
                } catch (failure: Throwable) {
                    if (cleanupFailure == null) cleanupFailure = failure
                    else if (cleanupFailure !== failure) cleanupFailure!!.addSuppressed(failure)
                }
            }
            if (::session.isInitialized && originalRotation >= 0) {
                cleanup {
                    main {
                        phone.controllerMenu()?.dismiss()
                        if (session.monitorShellPage() != "closed") shell().close()
                        session.confirm(false)
                    }
                    await("diagnostic cleanup preview settled") {
                        !session.busy && !session.awaitingConfirmation()
                    }
                    main { session.rotate(originalRotation, false) }
                    await("diagnostic original rotation restored") {
                        !session.busy && session.rotation == originalRotation
                    }
                }
                cleanup {
                    main {
                        preferences?.let { previous ->
                            val edit = session.prefs!!.edit().clear()
                            for ((key, value) in previous) when (value) {
                                is Boolean -> edit.putBoolean(key, value)
                                is Int -> edit.putInt(key, value)
                                is Long -> edit.putLong(key, value)
                                is Float -> edit.putFloat(key, value)
                                is String -> edit.putString(key, value)
                                is Set<*> ->
                                    edit.putStringSet(key, value.filterIsInstance<String>().toSet())
                            }
                            check(edit.commit(), "diagnostic original preferences restored")
                        }
                    }
                }
                cleanup {
                    main {
                        val manager =
                            runner.targetContext.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                        if (clipboard == null) manager.clearPrimaryClip()
                        else manager.setPrimaryClip(clipboard!!)
                        session.clearDiagnostics()
                    }
                }
                cleanup { DeviceTestSupport.cancelInjectedMonitor(runner) }
            }
            cleanup { runner.targetContext.unbindService(binding) }
            cleanupFailure?.let { failure ->
                val primary = primaryFailure
                if (primary == null) throw failure
                if (primary !== failure) primary.addSuppressed(failure)
            }
        }
    }
}
