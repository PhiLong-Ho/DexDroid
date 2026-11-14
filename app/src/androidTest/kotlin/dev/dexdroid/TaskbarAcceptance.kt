package dev.dexdroid

import android.app.Instrumentation
import android.content.*
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.*
import android.view.*
import android.widget.FrameLayout
import android.widget.Switch
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Native monitor contacts and display geometry for the reserved taskbar. */
internal class TaskbarAcceptance(private val runner: Instrumentation) {
    private lateinit var session: SessionService
    private lateinit var phone: MainActivity
    private val results = StringBuilder()
    private val directory by lazy {
        File(runner.targetContext.externalCacheDir, "taskbar-acceptance").apply {
            check(isDirectory || mkdirs())
        }
    }

    private fun main(action: () -> Unit) = DeviceTestSupport.onMain(runner, action)

    private fun <T> read(action: () -> T): T {
        var value: T? = null
        main { value = action() }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private fun verify(value: Boolean, message: String) {
        check(value) { message }
        results.append("PASS $message\n")
        runner.sendStatus(0, Bundle().apply { putString("stream", "PROGRESS $message\n") })
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
        verify(condition(), message)
    }

    private fun ready() =
        await("taskbar desktop ready") {
            read {
                session.id >= 0 &&
                    !session.busy &&
                    session.monitorWindow() != null &&
                    session.monitorShellView()?.isLayoutRequested == false
            }
        }

    private fun page(expected: String) =
        await("taskbar menu settled: $expected") {
            read {
                session.monitorShellPage() == expected &&
                    !session.monitorShellAnimating() &&
                    session.monitorShellView()?.isLayoutRequested == false &&
                    (expected == "closed" || view("monitor-close").isShown)
            }
        }

    private fun view(tag: String): View =
        checkNotNull(session.monitorShellView()?.findViewWithTag<View>(tag)) { "Missing $tag" }

    private fun monitor(): Any =
        SessionService::class
            .java
            .getDeclaredField("monitor")
            .apply { isAccessible = true }
            .get(session)

    private fun shell(): MonitorShell {
        val monitor = monitor()
        return monitor.javaClass
            .getDeclaredField("shell")
            .apply { isAccessible = true }
            .get(monitor) as MonitorShell
    }

    private fun bounds(tag: String): Rect = read {
        val target = view(tag)
        Rect().also {
            check(target.isShown && target.isEnabled && target.getGlobalVisibleRect(it)) {
                "No visible taskbar target: $tag"
            }
        }
    }

    private fun tap(point: FloatArray, source: Int = InputDevice.SOURCE_TOUCHSCREEN) {
        val viewport = DeviceTestSupport.monitorViewport(runner, session)
        viewport.verify(runner, session)
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val property =
                MotionEvent.PointerProperties().apply {
                    id = 7
                    toolType =
                        if (source == InputDevice.SOURCE_MOUSE) MotionEvent.TOOL_TYPE_MOUSE
                        else MotionEvent.TOOL_TYPE_FINGER
                }
            val coordinate =
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
                    arrayOf(property),
                    arrayOf(coordinate),
                    0,
                    if (source == InputDevice.SOURCE_MOUSE && action == MotionEvent.ACTION_DOWN)
                        MotionEvent.BUTTON_PRIMARY
                    else 0,
                    1f,
                    1f,
                    -1,
                    0,
                    source,
                    0,
                )
            try {
                verify(
                    DeviceTestSupport.injectOnDisplay(event, viewport.externalDisplay),
                    "taskbar native contact $action source=$source",
                )
            } finally {
                event.recycle()
            }
            if (action == MotionEvent.ACTION_DOWN) Thread.sleep(60)
        }
    }

    private fun click(tag: String, source: Int = InputDevice.SOURCE_TOUCHSCREEN) {
        val area = bounds(tag)
        tap(floatArrayOf(area.exactCenterX(), area.exactCenterY()), source)
    }

    private fun pointerClick(tag: String) {
        main {
            val target = view(tag)
            val global = Matrix()
            val rootGlobal = Matrix()
            val inverse = Matrix()
            target.transformMatrixToGlobal(global)
            session.monitorShellView()!!.transformMatrixToGlobal(rootGlobal)
            check(rootGlobal.invert(inverse))
            val point = floatArrayOf(target.width / 2f, target.height / 2f)
            global.mapPoints(point)
            inverse.mapPoints(point)
            session.cx = point[0]
            session.cy = point[1]
            session.click()
        }
    }

    private fun mouseGeneric(point: FloatArray, action: Int) {
        val viewport = DeviceTestSupport.monitorViewport(runner, session)
        val property =
            MotionEvent.PointerProperties().apply {
                id = 7
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
        val coordinate =
            MotionEvent.PointerCoords().apply {
                x = point[0]
                y = point[1]
                setAxisValue(MotionEvent.AXIS_VSCROLL, -1f)
            }
        val now = SystemClock.uptimeMillis()
        val event =
            MotionEvent.obtain(
                now,
                now,
                action,
                1,
                arrayOf(property),
                arrayOf(coordinate),
                0,
                0,
                1f,
                1f,
                -1,
                0,
                InputDevice.SOURCE_MOUSE,
                0,
            )
        try {
            viewport.verify(runner, session)
            verify(
                DeviceTestSupport.injectOnDisplay(event, viewport.externalDisplay),
                "native taskbar mouse generic action $action",
            )
        } finally {
            event.recycle()
        }
    }

    private fun nativeDimensions(label: String) {
        val manager =
            runner.targetContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        await("$label native display equals reserved app area") {
            val expected = read { intArrayOf(session.id, session.appWidth(), session.appHeight()) }
            val point = Point()
            manager.getDisplay(expected[0])?.getRealSize(point)
            point.x == expected[1] && point.y == expected[2]
        }
        // Native framework dimensions are the assertion; retain the independent shell report
        // privately for diagnosing OEM display/reporting differences, never as public evidence.
        File(directory, "$label-display.txt")
            .writeText(DeviceTestSupport.shell(runner, "dumpsys display").orEmpty(), Charsets.UTF_8)
    }

    private fun partition(label: String, edges: Boolean) {
        ready()
        await("$label committed taskbar layout") {
            read {
                val bar = view("monitor-taskbar")
                val idle = view("monitor-idle")
                val layout = bar.layoutParams as FrameLayout.LayoutParams
                session.monitorNavigationEnabled() == edges &&
                    session.appWidth() == session.width() &&
                    session.height() - session.appHeight() ==
                        (if (edges) 0
                        else Math.round(minOf(session.width(), session.height()) * .04f)) &&
                    bar.visibility == (if (edges) View.GONE else View.VISIBLE) &&
                    layout.topMargin == session.appHeight() &&
                    idle.layoutParams.width == session.appWidth() &&
                    idle.layoutParams.height == session.appHeight() &&
                    (!idle.isShown ||
                        (idle.width == session.appWidth() && idle.height == session.appHeight())) &&
                    (edges || bar.height == session.height() - session.appHeight())
            }
        }
        nativeDimensions(label)
        if (!edges)
            main {
                val bar = view("monitor-taskbar")
                val group = view("monitor-taskbar-actions")
                val apps = view("monitor-taskbar-apps")
                val controls = view("monitor-taskbar-controls")
                val minimum = Math.round(minOf(session.width(), session.height()) * .04f)
                verify(
                    apps is android.widget.ImageButton &&
                        controls is android.widget.ImageButton &&
                        apps.contentDescription == "Apps" &&
                        controls.contentDescription == "Controls" &&
                        apps.tooltipText == "Apps" &&
                        controls.tooltipText == "Controls",
                    "$label taskbar is icon-only with named accessible and tooltip actions",
                )
                verify(
                    group.width == minimum * 2 + Math.round(minimum / 6f),
                    "$label taskbar group scales only with output",
                )
                verify(
                    kotlin.math.abs(group.left + group.width / 2f - bar.width / 2f) <= 1f,
                    "$label taskbar actions are centered together",
                )
                verify(
                    apps.width == minimum &&
                        controls.width == minimum &&
                        apps.height == minimum &&
                        controls.height == minimum,
                    "$label taskbar targets equal the fixed ratio height",
                )
                verify(
                    controls.left - (apps.left + apps.width) == Math.round(minimum / 6f),
                    "$label taskbar gap is one sixth of strip height",
                )
                verify(
                    apps.width - apps.paddingLeft - apps.paddingRight == Math.round(minimum / 2f) &&
                        controls.width - controls.paddingLeft - controls.paddingRight ==
                            Math.round(minimum / 2f) &&
                        (apps as android.widget.ImageButton).scaleType ==
                            android.widget.ImageView.ScaleType.FIT_CENTER &&
                        (controls as android.widget.ImageButton).scaleType ==
                            android.widget.ImageView.ScaleType.FIT_CENTER,
                    "$label taskbar glyph bounds scale independently of app DPI",
                )
            }
        main {
            val owner = monitor()
            val texture =
                owner.javaClass.getDeclaredField("texture").apply { isAccessible = true }.get(owner)
                    as TextureView
            verify(
                texture.width == session.appWidth() && texture.height == session.appHeight(),
                "$label native texture ends at app boundary",
            )
        }
    }

    /** Fixture readiness only: observe one owned window before the original app contact. */
    private fun nativeFixtureReady() {
        val component = ComponentName(runner.targetContext, InputTestActivity::class.java)
        val expected = read { intArrayOf(session.id, session.appWidth(), session.appHeight()) }
        var task = -1
        await("synthetic task available for input readiness") {
            task = DeviceTestSupport.nativeInputTask(runner, expected[0], component)
            task >= 0
        }
        val capturedTask = task
        val observation = DeviceTestSupport.InputObservation()
        var positive = 0
        try {
            await("synthetic native input window settled before original contact") {
                check(
                    read {
                        session.id == expected[0] &&
                            !session.busy &&
                            session.appWidth() == expected[1] &&
                            session.appHeight() == expected[2]
                    }
                )
                val ready =
                    DeviceTestSupport.settledInputWindow(
                        runner,
                        expected[0],
                        capturedTask,
                        component,
                        runner.targetContext.applicationInfo.uid,
                        expected[1],
                        expected[2],
                        observation,
                    )
                positive = if (ready) positive + 1 else 0
                positive >= 2
            }
        } finally {
            File(directory, "native-window-readiness.txt")
                .writeText(observation.decodedText(), Charsets.UTF_8)
        }
    }

    private fun capture(name: String, window: Window = read { session.monitorWindow()!! }) {
        val frames = CountDownLatch(1)
        main {
            window.decorView.postOnAnimation {
                window.decorView.postOnAnimation { frames.countDown() }
            }
        }
        check(frames.await(3, TimeUnit.SECONDS)) { "Taskbar capture did not settle" }
        val size = read { window.decorView.width to window.decorView.height }
        val bitmap = Bitmap.createBitmap(size.first, size.second, Bitmap.Config.ARGB_8888)
        try {
            val done = CountDownLatch(1)
            var outcome = -1
            main {
                PixelCopy.request(
                    window,
                    bitmap,
                    {
                        outcome = it
                        done.countDown()
                    },
                    Handler(Looper.getMainLooper()),
                )
            }
            verify(
                done.await(5, TimeUnit.SECONDS) && outcome == PixelCopy.SUCCESS,
                "$name captured",
            )
            File(directory, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun fresh() {
        main { session.pauseDesktop() }
        await("previous desktop released") { read { session.id < 0 && !session.busy } }
        main { session.retryDesktop() }
        ready()
        page("closed")
    }

    private fun centerVisible(enabled: Boolean, split: Boolean = false) {
        await("center Apps visibility=$enabled split=$split") {
            read {
                val tags =
                    if (split) listOf("split-idle-apps-0", "split-idle-apps-1")
                    else listOf("monitor-idle-apps")
                tags.all { tag ->
                    val target =
                        if (split) session.monitorWindow()!!.decorView.findViewWithTag<View>(tag)
                        else view(tag)
                    target != null &&
                        target.visibility == (if (enabled) View.VISIBLE else View.GONE)
                }
            }
        }
    }

    private fun phoneCenterSwitch(root: View): Switch? {
        if (root is Switch && root.contentDescription == "Show center Apps button") return root
        if (root is ViewGroup)
            for (i in 0 until root.childCount) {
                phoneCenterSwitch(root.getChildAt(i))?.let {
                    return it
                }
            }
        return null
    }

    /** Local visibility and absolute screen coordinates have different origins for a Dialog. */
    private fun phoneTargetBounds(toggle: Switch): Rect? {
        val decor = phone.controllerMenu()?.window?.decorView ?: return null
        val row = toggle.parent as? View ?: return null
        for (target in listOf(decor, row, toggle)) {
            if (
                !target.isAttachedToWindow ||
                    !target.isShown ||
                    !target.isLaidOut ||
                    target.isLayoutRequested ||
                    target.width <= 0 ||
                    target.height <= 0
            )
                return null
        }
        if (!toggle.isEnabled || toggle.display?.displayId != 0) return null
        for (target in listOf(row, toggle)) {
            val visible = Rect()
            if (
                !target.getLocalVisibleRect(visible) ||
                    visible != Rect(0, 0, target.width, target.height)
            )
                return null
        }
        val position = IntArray(2)
        toggle.getLocationOnScreen(position)
        return Rect(
            position[0],
            position[1],
            position[0] + toggle.width,
            position[1] + toggle.height,
        )
    }

    private fun phoneFrames(window: Window) {
        val settled = CountDownLatch(1)
        main {
            window.decorView.postOnAnimation {
                window.decorView.postOnAnimation { settled.countDown() }
            }
        }
        check(settled.await(3, TimeUnit.SECONDS)) { "Phone setting frames did not settle" }
    }

    private fun stablePhoneTarget(toggle: Switch, window: Window): Rect {
        val deadline = SystemClock.elapsedRealtime() + 20000
        while (SystemClock.elapsedRealtime() < deadline) {
            val before = read { phoneTargetBounds(toggle) }
            if (before == null) {
                Thread.sleep(50)
                continue
            }
            phoneFrames(window)
            val after = read { phoneTargetBounds(toggle) }
            if (after == before) return checkNotNull(after)
        }
        error("Phone setting must be fully visible and stable across two frames")
    }

    private fun phoneTargetDiagnostic(toggle: Switch, name: String) {
        val report = read {
            val screen = IntArray(2)
            val inWindow = IntArray(2)
            val visible = Rect()
            toggle.getLocationOnScreen(screen)
            toggle.getLocationInWindow(inWindow)
            toggle.getLocalVisibleRect(visible)
            "screen=${screen.contentToString()}; window=${inWindow.contentToString()}; " +
                "size=${toggle.width}x${toggle.height}; localVisible=$visible; " +
                "focused=${toggle.hasWindowFocus()}; layoutPending=${toggle.isLayoutRequested}; " +
                "checked=${toggle.isChecked}; preference=${session.centerAppsEnabled()}\n"
        }
        File(directory, "$name.txt").writeText(report, Charsets.UTF_8)
    }

    private fun splitDimensions(vertical: Boolean) {
        ready()
        val manager =
            runner.targetContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        for (pane in 0..1) {
            await("split vertical=$vertical pane=$pane native display fits app area") {
                val expected = read {
                    val split = session.split ?: return@read null
                    if (split.state().vertical != vertical) return@read null
                    val box =
                        split.dimensions(pane, session.appWidth(), session.appHeight(), false)!!
                    intArrayOf(split.displayId(pane), box.width, box.height)
                }
                if (expected == null) false
                else {
                    val point = Point()
                    manager.getDisplay(expected[0])?.getRealSize(point)
                    point.x == expected[1] && point.y == expected[2]
                }
            }
            await("split vertical=$vertical pane=$pane texture and decoration exclude bar") {
                read {
                    val decor = session.monitorWindow()!!.decorView
                    val texture = decor.findViewWithTag<View>("split-texture-$pane")
                    val idle = decor.findViewWithTag<View>("split-idle-$pane")
                    val decoration = idle?.parent as? View
                    val box =
                        session.split!!.dimensions(
                            pane,
                            session.appWidth(),
                            session.appHeight(),
                            false,
                        )!!
                    texture != null &&
                        decoration != null &&
                        texture.width == box.width &&
                        texture.height == box.height &&
                        texture.top + texture.height <= session.appHeight() &&
                        decoration.top + decoration.height <= session.appHeight() &&
                        decoration.width == box.width &&
                        decoration.height == box.height
                }
            }
        }
        File(directory, "split-$vertical-display.txt")
            .writeText(DeviceTestSupport.shell(runner, "dumpsys display").orEmpty(), Charsets.UTF_8)
    }

    private fun sharedCenterSetting() {
        main { shell().show("settings") }
        page("settings")
        main {
            val toggle = view("monitor-center-apps")
            toggle.requestRectangleOnScreen(Rect(0, 0, toggle.width, toggle.height), true)
        }
        await("monitor center Apps switch visible") {
            read { view("monitor-center-apps").getGlobalVisibleRect(Rect()) }
        }
        verify(
            read { !(view("monitor-center-apps") as Switch).isChecked },
            "monitor Settings reflects current center Apps Off",
        )
        click("monitor-center-apps")
        await("monitor Settings enables shared center preference") {
            read { session.centerAppsEnabled() }
        }
        click("monitor-close")
        page("closed")
        main {
            MainActivity::class
                .java
                .getDeclaredMethod("options")
                .apply { isAccessible = true }
                .invoke(phone)
        }
        await("phone center Apps switch attached") {
            read { phone.controllerMenu()?.window?.decorView?.let(::phoneCenterSwitch) != null }
        }
        val toggle = read { phoneCenterSwitch(phone.controllerMenu()!!.window!!.decorView)!! }
        verify(read { toggle.isChecked }, "phone Settings reflects monitor center Apps On")
        val phoneWindow = read { phone.controllerMenu()!!.window!! }
        await("phone center Apps switch measured") {
            read {
                toggle.isAttachedToWindow &&
                    toggle.isLaidOut &&
                    !toggle.isLayoutRequested &&
                    toggle.width > 0 &&
                    toggle.height > 0
            }
        }
        main {
            val row = toggle.parent as View
            row.requestRectangleOnScreen(Rect(0, 0, row.width, row.height), true)
        }
        try {
            stablePhoneTarget(toggle, phoneWindow)
            capture("phone-center-setting-before", phoneWindow)
            // Capture itself crosses frames; take a fresh absolute-screen target afterward.
            val area = stablePhoneTarget(toggle, phoneWindow)
            phoneTargetDiagnostic(toggle, "phone-center-setting-before")
            verify(
                read { phoneTargetBounds(toggle) } == area,
                "phone center Apps target remains fully visible at native screen coordinates",
            )
            val down = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event =
                    MotionEvent.obtain(
                        down,
                        SystemClock.uptimeMillis(),
                        action,
                        area.exactCenterX(),
                        area.exactCenterY(),
                        0,
                    )
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                // Explicitly address the phone instead of inheriting the last monitor display.
                MotionEvent::class
                    .java
                    .getMethod("setDisplayId", Int::class.javaPrimitiveType)
                    .invoke(event, 0)
                try {
                    verify(
                        runner.uiAutomation.injectInputEvent(event, true),
                        "native phone center setting contact $action",
                    )
                } finally {
                    event.recycle()
                }
                if (action == MotionEvent.ACTION_DOWN) Thread.sleep(60)
            }
            await("phone Settings disables shared center preference") {
                read { !session.centerAppsEnabled() }
            }
        } catch (error: Throwable) {
            try {
                phoneTargetDiagnostic(toggle, "phone-center-setting-failure")
                capture("phone-center-setting-failure", phoneWindow)
            } catch (captureError: Throwable) {
                error.addSuppressed(captureError)
            }
            throw error
        }
        main { phone.controllerMenu()?.dismiss() }
        centerVisible(false)
        main { shell().show("settings") }
        page("settings")
        verify(
            read { !(view("monitor-center-apps") as Switch).isChecked },
            "monitor Settings reflects phone center Apps Off",
        )
        main { shell().close() }
        page("closed")
    }

    fun run(): String {
        phone = DeviceTestSupport.startPhoneBounded(runner)
        val connected = CountDownLatch(1)
        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    session = (binder as SessionService.LocalBinder).get()
                    connected.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }
        check(
            runner.targetContext.bindService(
                Intent(runner.targetContext, SessionService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        )
        var original: Map<String, *>? = null
        var geometry: IntArray? = null
        var component: ComponentName? = null
        try {
            verify(connected.await(5, TimeUnit.SECONDS), "taskbar session bound")
            ready()
            verify(
                read { !session.splitEnabled() && !session.awaitingConfirmation() },
                "taskbar suite starts from a settled single desktop",
            )
            original = read { session.prefs!!.all.toMap() }
            geometry = read {
                intArrayOf(session.baseW, session.baseH, session.dpi, session.rotation)
            }
            component = read { session.activeComponent }
            DeviceTestSupport.startInputDriver(runner)
            main { session.setMonitorNavigationEnabled(false) }
            ready()
            fresh()
            partition("startup-off", false)
            main { session.setCenterAppsEnabled(false) }
            centerVisible(false)
            verify(
                read { view("monitor-idle").isShown },
                "hiding center Apps retains idle background",
            )
            fresh()
            centerVisible(false)
            verify(
                read { !session.centerAppsEnabled() },
                "center Apps preference survives desktop restart",
            )
            sharedCenterSetting()
            for (rotation in 0..3) {
                main { session.rotate(rotation, false) }
                partition("idle-r$rotation", false)
                click("monitor-taskbar-apps")
                page("home")
                capture("apps-r$rotation")
                click("monitor-close")
                page("closed")
                click("monitor-taskbar-controls")
                page("controls")
                capture("controls-r$rotation")
                click("monitor-close")
                page("closed")
                val viewport = DeviceTestSupport.monitorViewport(runner, session)
                val blank = read {
                    viewport.mapVirtualToScreen(
                        session.width() * .05f,
                        (session.appHeight() + session.height()) * .5f,
                    )
                }
                tap(blank)
                page("closed")
                verify(
                    read { !session.hasDesktopApp() },
                    "blank taskbar never launches app r$rotation",
                )
                capture("idle-r$rotation")
            }
            var referencePixels: List<Int>? = null
            main { session.rotate(0, false) }
            ready()
            for (density in listOf(240, 320, 120)) {
                main { session.preview(1920, 1200, density) }
                await("ratio DPI$density preview ready") {
                    read { !session.busy && session.awaitingConfirmation() }
                }
                main { session.confirm(true) }
                partition("ratio-dpi-$density", false)
                val pixels = read {
                    val apps = view("monitor-taskbar-apps")
                    listOf(
                        view("monitor-taskbar").height,
                        apps.width,
                        apps.width - apps.paddingLeft - apps.paddingRight,
                        view("monitor-taskbar-actions").width,
                    )
                }
                if (referencePixels == null) referencePixels = pixels
                verify(
                    pixels == referencePixels,
                    "taskbar and glyph pixels stay identical at DPI$density",
                )
                capture("ratio-dpi-$density")
            }
            main { session.setMonitorTouchEnabled(false) }
            click("monitor-taskbar-controls")
            page("controls")
            click("monitor-close")
            page("closed")
            verify(
                read { !session.monitorTouchEnabled() },
                "shell Controls works with app touch Off",
            )
            pointerClick("monitor-taskbar-apps")
            page("home")
            pointerClick("monitor-close")
            page("closed")
            click("monitor-taskbar-controls", InputDevice.SOURCE_MOUSE)
            page("controls")
            click("monitor-close", InputDevice.SOURCE_MOUSE)
            page("closed")
            main {
                session.setMonitorTouchEnabled(true)
                session.rotate(0, false)
            }
            ready()
            main {
                session.launch(
                    ComponentName(runner.targetContext, InputTestActivity::class.java),
                    0,
                )
            }
            await("synthetic app launched") { read { !session.busy && session.hasDesktopApp() } }
            partition("native-app", false)
            nativeFixtureReady()
            val touches = InputTestActivity.touchEvents
            val appViewport = DeviceTestSupport.monitorViewport(runner, session)
            tap(
                read {
                    appViewport.mapVirtualToScreen(
                        session.appWidth() * .5f,
                        session.appHeight() - 30f * session.dpi / 160f,
                    )
                }
            )
            await("native content near lower boundary accepts input before taskbar exclusion") {
                InputTestActivity.touchEvents > touches
            }
            val before = InputTestActivity.touchEvents
            click("monitor-taskbar-apps")
            page("home")
            click("monitor-close")
            page("closed")
            val viewport = DeviceTestSupport.monitorViewport(runner, session)
            val blank = read {
                viewport.mapVirtualToScreen(
                    session.width() * .05f,
                    (session.appHeight() + session.height()) / 2f,
                )
            }
            tap(blank)
            val wheels = InputTestActivity.wheelEvents
            mouseGeneric(blank, MotionEvent.ACTION_HOVER_MOVE)
            mouseGeneric(blank, MotionEvent.ACTION_SCROLL)
            page("closed")
            verify(
                InputTestActivity.touchEvents == before,
                "taskbar and menu close have no app click-through",
            )
            verify(
                InputTestActivity.wheelEvents == wheels,
                "taskbar consumes native mouse wheel without app delivery",
            )
            capture("native-app")
            main { session.setMonitorNavigationEnabled(true) }
            partition("edges-on", true)
            main { session.setMonitorNavigationEnabled(false) }
            partition("edges-off-again", false)
            main {
                session.preview(session.baseW, session.baseH, session.dpi)
                session.setMonitorNavigationEnabled(true)
            }
            await("taskbar preview ready") {
                read { !session.busy && session.awaitingConfirmation() }
            }
            verify(
                read { !session.monitorNavigationEnabled() },
                "busy preview rejects navigation change",
            )
            main { session.setMonitorNavigationEnabled(true) }
            verify(
                read { !session.monitorNavigationEnabled() },
                "pending preview preserves navigation preference",
            )
            main { session.confirm(false) }
            partition("preview-reverted", false)
            main { session.preview(1920, 1080, 240) }
            await("taskbar Fit preview ready") {
                read { !session.busy && session.awaitingConfirmation() }
            }
            main { session.confirm(true) }
            partition("fit-1920-1080", false)
            capture("fit-1920-1080")
            fresh()
            main { session.enableSplit(false) }
            splitDimensions(false)
            centerVisible(false, true)
            main { session.setCenterAppsEnabled(true) }
            centerVisible(true, true)
            capture("split-center-on")
            main { session.setCenterAppsEnabled(false) }
            centerVisible(false, true)
            capture("split-center-off")
            main { session.split!!.direction(true) }
            splitDimensions(true)
            centerVisible(false, true)
            capture("split-up-down")
            results.append(
                "SCOPE Split native input, phone-screen recovery and performance are separate suites; captures require visual review.\n"
            )
        } catch (error: Throwable) {
            throw RuntimeException(results.toString() + "FAIL taskbar acceptance", error)
        } finally {
            try {
                DeviceTestSupport.cancelInjectedMonitor(runner)
                if (original != null && geometry != null) {
                    val saved = checkNotNull(geometry)
                    val savedPreferences = checkNotNull(original)
                    main {
                        phone.controllerMenu()?.dismiss()
                        if (session.awaitingConfirmation()) session.confirm(false)
                    }
                    ready()
                    main { session.pauseDesktop() }
                    await("taskbar fixture desktop released") {
                        read { session.id < 0 && !session.busy }
                    }
                    main {
                        val edit = session.prefs!!.edit().clear()
                        for ((key, value) in savedPreferences) when (value) {
                            is Boolean -> edit.putBoolean(key, value)
                            is Int -> edit.putInt(key, value)
                            is Long -> edit.putLong(key, value)
                            is Float -> edit.putFloat(key, value)
                            is String -> edit.putString(key, value)
                            is Set<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                edit.putStringSet(key, value as Set<String>)
                            }
                        }
                        check(edit.commit())
                        session.baseW = saved[0]
                        session.baseH = saved[1]
                        session.dpi = saved[2]
                        session.rotation = saved[3]
                        session.retryDesktop()
                    }
                    ready()
                    component?.let { previous ->
                        main { session.launch(previous, saved[3]) }
                        ready()
                    }
                }
            } finally {
                runner.targetContext.unbindService(connection)
            }
        }
        return results.toString()
    }
}
