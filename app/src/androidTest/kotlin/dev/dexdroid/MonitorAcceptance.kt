package dev.dexdroid

import android.app.*
import android.content.*
import android.graphics.Rect
import android.os.*
import android.view.*
import java.util.*
import java.util.concurrent.*
import java.util.function.BooleanSupplier

/** Injects real physical-monitor events; checks external app and native shell outcomes. */
class MonitorAcceptance(private val runner: Instrumentation?) {
    private var session: SessionService? = null
    private var binding: ServiceConnection? = null
    private var phone: Activity? = null
    private var bound: Boolean = false
    private var preferences: Map<String?, *>? = null
    private var originalRotation: Int = 0
    private var down: Long = 0
    private var plannedViewport: DeviceTestSupport.MonitorViewport? = null
    private var contactViewport: DeviceTestSupport.MonitorViewport? = null
    private val results: StringBuilder = StringBuilder()

    private fun main(action: Runnable?) {
        runner!!.runOnMainSync(action)
    }

    private fun physicalMonitor(): Display? {
        val captured = arrayOf<Display?>(null)
        val snapshot = Runnable {
            val display = session!!.external
            if (display == null || !display!!.isValid())
                throw AssertionError("Physical monitor unavailable on main thread")
            captured[0] = display
        }
        if (Looper.myLooper() == Looper.getMainLooper()) snapshot!!.run() else main(snapshot)
        return captured!![0]
    }

    private fun check(ok: Boolean, label: String?) {
        if (!ok) throw AssertionError(label)
        results.append("PASS ").append(label).append('\n')
    }

    @Throws(Exception::class)
    private fun await(predicate: BooleanSupplier, label: String?) {
        val until = SystemClock.uptimeMillis() + 10000
        while (!predicate.getAsBoolean() && SystemClock.uptimeMillis() < until) Thread.sleep(50)
        check(predicate.getAsBoolean(), label)
    }

    private fun restore() {
        if (preferences == null) return
        val edit = session!!.prefs!!.edit()!!.clear()
        for (entry in preferences!!.entries) {
            val v = entry!!.value
            val k = entry!!.key
            if (v is Boolean) edit!!.putBoolean(k, (v as Boolean?)!!)
            else if (v is Int) edit!!.putInt(k, (v as Int?)!!)
            else if (v is Float) edit!!.putFloat(k, (v as Float?)!!)
            else if (v is String) edit!!.putString(k, v as String?)
            else if (v is Long) edit!!.putLong(k, (v as Long?)!!)
        }
        edit!!.commit()
    }

    @Throws(Exception::class)
    private fun event(action: Int, points: Array<FloatArray?>?) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) {
            down = now
            contactViewport = plannedViewport ?: viewport()
            contactViewport!!.verify(runner, session)
            plannedViewport = null
        }
        val props = arrayOfNulls<MotionEvent.PointerProperties?>(points!!.size)
        val coords = arrayOfNulls<MotionEvent.PointerCoords?>(points!!.size)
        for (i in points!!.indices) {
            props[i] = MotionEvent.PointerProperties()
            props[i]!!.id = i + 7
            props[i]!!.toolType = MotionEvent.TOOL_TYPE_FINGER
            coords[i] = MotionEvent.PointerCoords()
            coords[i]!!.x = points!![i]!![0]
            coords[i]!!.y = points!![i]!![1]
            coords[i]!!.pressure = 1f
        }
        val e =
            MotionEvent.obtain(
                down,
                now,
                action,
                points!!.size,
                props,
                coords,
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
                DeviceTestSupport.injectOnDisplay(e, contactViewport!!.externalDisplay),
                "physical monitor event " + action,
            )
        } finally {
            e!!.recycle()
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                contactViewport = null
        }
    }

    private fun viewport(): DeviceTestSupport.MonitorViewport =
        DeviceTestSupport.monitorViewport(runner, session)

    private fun physical(x: Float, y: Float): FloatArray {
        val captured = contactViewport ?: plannedViewport ?: viewport()
        val point = captured.mapVirtualToScreen(x, y)
        if (contactViewport == null) {
            captured.verify(runner, session)
            plannedViewport = captured
        }
        return point
    }

    @Throws(Exception::class)
    private fun injectedContactCleanup() {
        val point = physical(session!!.width() * .5f, session!!.height() * .6f)
        val cancelled = InputTestActivity.cancelEvents
        event(MotionEvent.ACTION_DOWN, arrayOf<FloatArray?>(point))
        event(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            arrayOf<FloatArray?>(point, floatArrayOf(point[0] + 30, point[1] + 30)),
        )
        event(
            MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            arrayOf<FloatArray?>(point, floatArrayOf(point[0] + 30, point[1] + 30)),
        )
        DeviceTestSupport.cancelInjectedMonitor(runner)
        contactViewport = null
        plannedViewport = null
        await(
            { InputTestActivity.cancelEvents > cancelled },
            "fixture cleanup cancels remaining nonzero pointer after pointer-up",
        )
        val previousUp = InputTestActivity.lastUpDownTime
        event(MotionEvent.ACTION_DOWN, arrayOf<FloatArray?>(point))
        event(MotionEvent.ACTION_UP, arrayOf<FloatArray?>(point))
        await(
            { InputTestActivity.lastUpDownTime > previousUp },
            "fresh native touch accepted after exact fixture cancellation",
        )
    }

    @Throws(Exception::class)
    private fun swipe(x: Float, y: Float, endX: Float, endY: Float) {
        val capturedViewport = viewport()
        val start = capturedViewport.mapVirtualToScreen(x, y)
        val end = capturedViewport.mapVirtualToScreen(endX, endY)
        DeviceTestSupport.usableMonitorSwipe(runner, session, start, end)
        capturedViewport.verify(runner, session)
        plannedViewport = capturedViewport
        event(0, arrayOf<FloatArray?>(start))
        Thread.sleep(30)
        event(2, arrayOf<FloatArray?>(end))
        event(1, arrayOf<FloatArray?>(end))
        Thread.sleep(100)
    }

    @Throws(Exception::class)
    private fun tap(x: Float, y: Float) {
        event(0, arrayOf<FloatArray?>(floatArrayOf(x, y)))
        event(1, arrayOf<FloatArray?>(floatArrayOf(x, y)))
        Thread.sleep(100)
    }

    @Throws(Exception::class)
    private fun click(tag: String?) {
        val rect = Rect()
        main({
            val root = session!!.monitorShellView()
            val view = if (root == null) null else root!!.findViewWithTag<View?>(tag)
            check(view != null && view!!.isShown(), "monitor control exists: " + tag!!)
            if (!view!!.getGlobalVisibleRect(rect)) {
                var parent = view!!.getParent()
                while (parent is View) {
                    if (parent is android.widget.ScrollView) {
                        val target = Rect()
                        view!!.getDrawingRect(target)
                        (parent as android.widget.ScrollView).offsetDescendantRectToMyCoords(
                            view,
                            target,
                        )
                        (parent as android.widget.ScrollView).scrollTo(0, target.top)
                        break
                    }
                    parent = parent!!.getParent()
                }
            }
        })
        Thread.sleep(50)
        main({
            check(
                session!!
                    .monitorShellView()!!
                    .findViewWithTag<View?>(tag)!!
                    .getGlobalVisibleRect(rect),
                "monitor control visible: " + tag!!,
            )
        })
        tap(rect.exactCenterX(), rect.exactCenterY())
    }

    private fun menuCard(view: View, label: String?) {
        check(
            (view is android.widget.TextView &&
                view.getBackground() is android.graphics.drawable.RippleDrawable),
            "monitor Settings card: " + label!!,
        )
        val text = view as android.widget.TextView
        val density = view.getResources()!!.getDisplayMetrics()!!.density
        check(
            (text!!.getMinHeight() >= 61 * density &&
                text!!.getCurrentTextColor() == android.graphics.Color.WHITE),
            "monitor Settings row sizing and contrast: " + label!!,
        )
        val background =
            (view.getBackground() as android.graphics.drawable.RippleDrawable).getDrawable(0)
        check(
            (background is android.graphics.drawable.GradientDrawable &&
                (Math.abs(
                    (((background as android.graphics.drawable.GradientDrawable).getCornerRadius() /
                        density) - 8)
                ) < 1)),
            "monitor Settings rounded row: " + label!!,
        )
    }

    private fun menuStyle(page: String) {
        val root = session!!.monitorShellView()
        if (page == "closed" || page == "controls") return
        val panel = root!!.findViewWithTag<View?>("monitor-panel")
        val density = panel!!.getResources()!!.getDisplayMetrics()!!.density
        val surface = panel!!.getBackground() as android.graphics.drawable.GradientDrawable?
        check(
            (surface!!.getColor()!!.getDefaultColor() == android.graphics.Color.rgb(18, 18, 18) &&
                Math.abs(surface!!.getCornerRadius() / density - 22) < 1),
            "monitor submenu uses shared Settings surface: " + page,
        )
        if (page == "rotation") {
            for (i in 0..3) {
                val button =
                    root!!.findViewWithTag<android.widget.RadioButton?>("monitor-rotation-" + i)
                menuCard(button!!, "rotation " + i)
                check(
                    button!!.isChecked() == (i == session!!.rotation),
                    "monitor current orientation selected " + i,
                )
            }
        }
        if (page == "scroll" && !session!!.activePackage!!.isEmpty()) {
            for (tag in
                arrayOf<String?>(
                    "monitor-swipe",
                    "monitor-wheel",
                    "monitor-speed-0",
                    "monitor-speed-1",
                    "monitor-speed-2",
                )) menuCard(root!!.findViewWithTag<View?>(tag)!!, tag)
            check(
                (root!!.findViewWithTag<View?>(
                        if (session!!.swipeScrolling()) "monitor-swipe" else "monitor-wheel"
                    ) as android.widget.RadioButton)
                    .isChecked(),
                "monitor current scroll mode selected",
            )
        }
        if (page == "home") {
            val list = root!!.findViewWithTag<android.widget.ListView?>("monitor-app-list")
            if (list!!.getChildCount() > 0) {
                menuCard(list!!.getChildAt(0)!!, "app entry")
                check(
                    ((list!!.getChildAt(0) as android.widget.TextView).getCompoundDrawables()[0] !=
                        null),
                    "monitor app card retains app icon",
                )
                check(
                    (list!!.getChildAt(0) as android.widget.TextView).getText()
                        is android.text.Spanned,
                    "monitor app caption shares Settings subtitle styling",
                )
            }
        }
    }

    @Throws(Exception::class)
    private fun page(value: String) {
        await(
            { session!!.monitorShellPage() == value && !session!!.monitorShellAnimating() },
            "monitor page/animation settled " + value,
        )
        if (value != "closed")
            main({
                check(
                    MenuTextAssertions.check(session!!.monitorShellView()!!) > 0,
                    "decoded menu labels and available font glyphs: " + value,
                )
                menuStyle(value)
                results.append(
                    MenuHeaderAssertions.inspect(session!!.monitorShellView()!!, "monitor")
                )
                if (value == "settings") {
                    val version =
                        session!!
                            .monitorShellView()!!
                            .findViewWithTag<android.widget.TextView?>("settings-app-version")
                    try {
                        val installed =
                            runner!!
                                .getTargetContext()!!
                                .getPackageManager()!!
                                .getPackageInfo("dev.dexdroid", 0)
                        check(
                            (version != null &&
                                version!!.isShown() &&
                                version!!
                                    .getText()!!
                                    .toString()
                                    .contains(installed!!.versionName!!) &&
                                version!!
                                    .getText()!!
                                    .toString()
                                    .contains(("Build " + installed!!.getLongVersionCode()))),
                            "monitor Settings shows installed version and build immediately",
                        )
                    } catch (error: android.content.pm.PackageManager.NameNotFoundException) {
                        throw AssertionError(error)
                    }
                }
            })
    }

    private fun phoneButton(root: View?, label: String?): android.widget.Button? {
        if (
            (root is android.widget.Button &&
                label!!.contentEquals((root as android.widget.Button).getText()!!))
        )
            return root as android.widget.Button?
        if (root is ViewGroup)
            for (i in 0 until (root as ViewGroup).getChildCount()) {
                val found = phoneButton((root as ViewGroup).getChildAt(i), label)
                if (found != null) return found
            }
        return null
    }

    private fun parity() {
        main({
            val root = session!!.monitorShellView()
            check(
                !root!!.isForceDarkAllowed(),
                "monitor overlay disables automatic color inversion",
            )
            check(root!!.findViewWithTag<View?>("touchpad") == null, "monitor omits phone touchpad")
            check(root!!.findViewWithTag<View?>("monitor-resume") == null, "no Resume app button")
            val close = root!!.findViewWithTag<View?>("monitor-close")
            val settings = root!!.findViewWithTag<View?>("monitor-settings")
            check(
                (close is android.widget.ImageButton &&
                    settings is android.widget.ImageButton &&
                    settings!!.getLeft() < close!!.getLeft()),
                "Settings icon immediately left of Close icon",
            )
            val tags =
                arrayOf<String?>(
                    "monitor-apps",
                    "monitor-rotate",
                    "monitor-layout",
                    "monitor-audio",
                )
            val labels = arrayOf<String?>("Apps", "Rotate", "Layout", "Audio")
            val first = root!!.findViewWithTag<android.widget.LinearLayout?>("monitor-row-1")
            check(
                root!!.findViewWithTag<View?>("monitor-row-2") == null,
                "monitor has one primary action row",
            )
            for (i in tags!!.indices) {
                val monitor = root!!.findViewWithTag<android.widget.Button?>(tags!![i])
                val phone = phoneButton(this.phone!!.getWindow()!!.getDecorView(), labels!![i])
                check(
                    (monitor != null &&
                        phone != null &&
                        monitor!!.getCompoundDrawables()[1] != null),
                    "shared icon control: " + labels!![i],
                )
                val md = monitor!!.getResources()!!.getDisplayMetrics()!!.density
                val pd = phone!!.getResources()!!.getDisplayMetrics()!!.density
                check(
                    (Math.abs(monitor!!.getHeight() / md - phone!!.getHeight() / pd) < 1 &&
                        (Math.abs(
                            ((monitor!!.getTextSize() /
                                monitor!!.getResources()!!.getDisplayMetrics()!!.scaledDensity) -
                                (phone!!.getTextSize() /
                                    phone!!.getResources()!!.getDisplayMetrics()!!.scaledDensity))
                        ) < .1)),
                    "phone/monitor size and typography match: " + labels!![i],
                )
                check(first!!.getChildAt(i) === monitor, "phone-style button order: " + labels!![i])
                check(
                    monitor!!.getCurrentTextColor() == phone!!.getCurrentTextColor(),
                    "phone/monitor text contrast matches: " + labels!![i],
                )
            }
            check(
                root!!.findViewWithTag<View?>("controller-status-card")
                    is ControllerStyle.StatusCard,
                "shared phone/monitor status card",
            )
            val stop = root!!.findViewWithTag<View?>("monitor-stop")
            val expansion = root!!.findViewWithTag<View?>("monitor-status-expand")
            check(
                (stop is android.widget.ImageButton &&
                    root!!.findViewWithTag<View?>("monitor-app-settings") == null),
                "status retains desktop action without app-options shortcut",
            )
            results.append(StatusDisclosureAssertions.inspect(root!!, "monitor"))
            for (action in arrayOf<View?>(stop, expansion)) {
                val d = action!!.getResources()!!.getDisplayMetrics()!!.density
                check(
                    (action!!.getWidth() >= 47 * d &&
                        action!!.getHeight() >= 47 * d &&
                        action!!.getContentDescription() != null),
                    "status icon has 48dp accessible target",
                )
            }
        })
    }

    @Throws(Exception::class)
    private fun navigationFlow(rotation: Int) {
        check(
            (session!!
                .monitorShellView()!!
                .findViewWithTag<View?>("monitor-status-details")!!
                .getVisibility() == View.GONE),
            "shared collapsed status on monitor " + rotation,
        )
        click("monitor-status-cue")
        await(
            {
                session!!
                    .monitorShellView()!!
                    .findViewWithTag<View?>("monitor-status-details")!!
                    .isShown()
            },
            "expanded monitor details",
        )
        main({
            StatusDisclosureAssertions.inspectCue(session!!.monitorShellView()!!, "monitor", true)
        })
        textScreenshot("status-expanded-r" + rotation)
        check(
            session!!.prefs!!.getBoolean("statusExpanded", false),
            "expansion preference shared " + rotation,
        )
        click("monitor-status-expand")
        click("monitor-settings")
        page("settings")
        click("monitor-user-guide")
        page("user-guide")
        for (i in 0..7) check(
            session!!.monitorShellView()!!.findViewWithTag<View?>("guide-section-" + i) != null,
            "offline monitor guide section " + i,
        )
        main({
            (session!!.monitorShellView()!!.findViewWithTag<View?>("monitor-menu-scroll")
                    as android.widget.ScrollView)
                .fullScroll(View.FOCUS_DOWN)
        })
        await(
            {
                val scroll =
                    session!!
                        .monitorShellView()!!
                        .findViewWithTag<android.widget.ScrollView?>("monitor-menu-scroll")
                (scroll!!.getScrollY() ==
                    Math.max(0, scroll!!.getChildAt(0)!!.getHeight() - scroll!!.getHeight()))
            },
            "monitor guide bottom settles",
        )
        val guidePosition =
            (session!!.monitorShellView()!!.findViewWithTag<View?>("monitor-menu-scroll")
                    as android.widget.ScrollView)
                .getScrollY()
        textScreenshot("user-guide-r" + rotation)
        val previousSetupState = session!!.shizukuState
        try {
            main({ session!!.shizukuState = ShizukuSetup.State.MISSING })
            click("monitor-guide-shizuku")
            page("shizuku")
            check(
                session!!.shizukuState == ShizukuSetup.State.CONNECTED,
                "monitor setup refreshes stale manager state " + rotation,
            )
        } finally {
            main({ session!!.shizukuState = previousSetupState })
        }
        textScreenshot("shizuku-r" + rotation)
        click("monitor-up")
        page("user-guide")
        await(
            {
                ((session!!.monitorShellView()!!.findViewWithTag<View?>("monitor-menu-scroll")
                        as android.widget.ScrollView)
                    .getScrollY() == guidePosition)
            },
            "monitor guide scroll retained " + rotation,
        )
        click("monitor-guide-protected")
        page("protected-help")
        click("monitor-up")
        page("user-guide")
        click("monitor-up")
        page("settings")
        click("monitor-up")
        page("controls")
        click("monitor-settings")
        page("settings")
        click("monitor-pointer-speed")
        page("pointer-speed")
        val selected = if (session!!.prefs!!.getFloat("speed", 1.6f) < 1.3f) 1 else 0
        click("monitor-pointer-" + selected)
        page("settings")
        check(
            session!!.prefs!!.getFloat("speed", 0f) == (if (selected == 0) 1f else 1.6f),
            "monitor pointer choice applies and returns to Settings " + rotation,
        )
        for (child in
            arrayOf<Array<String?>?>(
                arrayOf<String?>("monitor-profiles", "profiles"),
                arrayOf<String?>("monitor-display", "display"),
                arrayOf<String?>("monitor-diagnostics", "diagnostics"),
                arrayOf<String?>("monitor-protected-settings", "protected-help"),
            )) {
            click(child!![0])
            page(child!![1]!!)
            main({
                val root = session!!.monitorShellView()
                val up = root!!.findViewWithTag<View?>("monitor-up")
                val title = root!!.findViewWithTag<View?>("monitor-title")
                val close = root!!.findViewWithTag<View?>("monitor-close")
                check(
                    (up != null &&
                        up!!.getLeft() < title!!.getLeft() &&
                        title!!.getLeft() < close!!.getLeft()),
                    "monitor nested header has Up/title/Close " + rotation,
                )
            })
            if (child!![1] == "diagnostics") textScreenshot("navigation-diagnostics-r" + rotation)
            click("monitor-up")
            page("settings")
        }
        main({
            (session!!.monitorShellView()!!.findViewWithTag<View?>("monitor-menu-scroll")
                    as android.widget.ScrollView)
                .fullScroll(View.FOCUS_DOWN)
        })
        await(
            {
                val view =
                    session!!
                        .monitorShellView()!!
                        .findViewWithTag<android.widget.ScrollView?>("monitor-menu-scroll")
                (view!!.getScrollY() ==
                    Math.max(
                        0,
                        ((view!!.getChildAt(0)!!.getHeight() - view!!.getHeight()) +
                            view!!.getPaddingTop() +
                            view!!.getPaddingBottom()),
                    ))
            },
            "monitor parent smooth scroll settles",
        )
        val before =
            (session!!.monitorShellView()!!.findViewWithTag<View?>("monitor-menu-scroll")
                    as android.widget.ScrollView)
                .getScrollY()
        check(before > 0, "monitor Settings can scroll before child")
        click("monitor-diagnostics")
        page("diagnostics")
        main({ session!!.key(KeyEvent.KEYCODE_BACK) })
        page("settings")
        await(
            {
                ((session!!.monitorShellView()!!.findViewWithTag<View?>("monitor-menu-scroll")
                        as android.widget.ScrollView)
                    .getScrollY() == before)
            },
            "monitor parent scroll retained on hardware Back " + rotation,
        )
        click("monitor-up")
        page("controls")
        click("monitor-settings")
        page("settings")
        click("monitor-app-settings")
        page("app-settings")
        textScreenshot("app-settings-r" + rotation)
        click("monitor-scroll")
        page("scroll")
        val oldSwipe = session!!.swipeScrolling()
        val oldSpeed = session!!.scrollSpeed()
        click(if (oldSwipe) "monitor-wheel" else "monitor-swipe")
        page("scroll")
        check(
            session!!.swipeScrolling() != oldSwipe,
            "combined monitor page stays open with mode applied " + rotation,
        )
        val speedChoice = if (oldSpeed < .35f) 1 else 0
        click("monitor-speed-" + speedChoice)
        page("scroll")
        check(
            session!!.scrollSpeed() == (if (speedChoice == 0) .2f else .5f),
            "combined monitor page stays open with speed applied " + rotation,
        )
        click(if (oldSwipe) "monitor-swipe" else "monitor-wheel")
        click("monitor-speed-" + (if (oldSpeed < .35f) 0 else if (oldSpeed < .75f) 1 else 2))
        click("monitor-up")
        page("app-settings")
        click("monitor-up")
        page("settings")
        click("monitor-close")
        page("closed")
        controlsGesture()
        check(
            session!!.monitorShellView()!!.findViewWithTag<View?>("monitor-up") == null,
            "fresh Controls root has no stale submenu parents " + rotation,
        )
    }

    private fun noChrome() {
        main({
            val root = session!!.monitorShellView()
            check(
                (root!!.findViewWithTag<View?>("monitor-apps") == null &&
                    root!!.findViewWithTag<View?>("monitor-controls") == null &&
                    root!!.findViewWithTag<View?>("monitor-resume") == null &&
                    root!!.findViewWithTag<View?>("monitor-panel") == null),
                "closed app has no persistent shell controls",
            )
        })
    }

    @Throws(Exception::class)
    private fun renderedContrast() {
        val button = Rect()
        main({
            check(
                session!!
                    .monitorShellView()!!
                    .findViewWithTag<View?>("monitor-stop")!!
                    .getGlobalVisibleRect(button),
                "Stop render target visible",
            )
        })
        val capturedViewport = viewport()
        val bitmap =
            android.graphics.Bitmap.createBitmap(
                capturedViewport.width,
                capturedViewport.height,
                android.graphics.Bitmap.Config.ARGB_8888,
            )
        val captured = CountDownLatch(1)
        val result = intArrayOf(-1)
        try {
            main({
                capturedViewport.verify(runner, session)
                PixelCopy.request(
                    session!!.monitorWindow()!!,
                    bitmap,
                    { value ->
                        result[0] = value
                        captured.countDown()
                    },
                    Handler(Looper.getMainLooper()!!),
                )
            })
            check(
                captured.await(5, TimeUnit.SECONDS) && result!![0] == PixelCopy.SUCCESS,
                "physical monitor rendering captured",
            )
            var dark = 0
            var light = 0
            button.offset(-capturedViewport.originX, -capturedViewport.originY)
            for (y in button.top until button.bottom) for (x in button.left until button.right) {
                val pixel = bitmap.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                if (r < 40 && g < 40 && b < 40) dark++
                if (r > 200 && g > 200 && b > 200) light++
            }
            check(
                light > 10 && dark > light * 3,
                "rendered Stop has white ink on the dark status surface without a filled circle",
            )
        } finally {
            bitmap.recycle()
        }
    }

    @Throws(Exception::class)
    private fun outside() {
        val count = InputTestActivity.touchEvents
        val p = physical(4 * session!!.dpi / 160f, session!!.height() * .5f)
        DeviceTestSupport.usableMonitorOutside(runner, session, p)
        tap(p[0], p[1])
        page("closed")
        check(
            InputTestActivity.touchEvents == count,
            "outside dismissal does not click underlying app",
        )
        noChrome()
    }

    @Throws(Exception::class)
    private fun resume() {
        while (session!!.monitorShellPage() != "closed") {
            main({ session!!.key(KeyEvent.KEYCODE_BACK) })
            Thread.sleep(80)
        }
        page("closed")
    }

    @Throws(Exception::class)
    private fun homeGesture() {
        val d = session!!.dpi / 160f
        swipe(
            session!!.width() * .6f,
            session!!.height() - 4 * d,
            session!!.width() * .6f,
            session!!.height() - 100 * d,
        )
        page("home")
    }

    @Throws(Exception::class)
    private fun controlsGesture() {
        val d = session!!.dpi / 160f
        swipe(session!!.width() * .6f, 4 * d, session!!.width() * .6f, 100 * d)
        page("controls")
    }

    private fun exactText(expected: String?) {
        main({
            check(
                MenuTextAssertions.contains(session!!.monitorShellView(), expected),
                "exact displayed menu text: " + expected!!,
            )
        })
    }

    @Throws(Exception::class)
    private fun textScreenshot(name: String?) {
        val capturedViewport = viewport()
        val bitmap =
            android.graphics.Bitmap.createBitmap(
                capturedViewport.width,
                capturedViewport.height,
                android.graphics.Bitmap.Config.ARGB_8888,
            )
        val done = CountDownLatch(1)
        val result = intArrayOf(-1)
        try {
            main({
                capturedViewport.verify(runner, session)
                PixelCopy.request(
                    session!!.monitorWindow()!!,
                    bitmap,
                    { v ->
                        result[0] = v
                        done.countDown()
                    },
                    Handler(Looper.getMainLooper()!!),
                )
            })
            check(
                done.await(5, TimeUnit.SECONDS) && result!![0] == PixelCopy.SUCCESS,
                "physical menu screenshot: " + name!!,
            )
            val directory =
                java.io.File(
                    runner!!.getTargetContext()!!.getExternalCacheDir(),
                    "menu-text-acceptance",
                )
            check(
                directory.isDirectory() || directory.mkdirs(),
                "menu screenshot directory available",
            )
            java.io
                .FileOutputStream(java.io.File(directory, name!! + ".png"))
                .use({ stream ->
                    check(
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream),
                        "exported menu screenshot: " + name!!,
                    )
                })
        } finally {
            bitmap.recycle()
        }
    }

    @Throws(Exception::class)
    private fun textMenus(test: ComponentName?) {
        main({ session!!.launch(test, 0) })
        await(
            { !session!!.busy && session!!.rotation == 0 },
            "text inspection landscape desktop ready",
        )
        Thread.sleep(500)
        controlsGesture()
        exactText("Monitor \u00b7 Connected \u2014 ")
        exactText("Shizuku \u00b7 Connected")
        exactText("Landscape 0\u00b0")
        exactText(session!!.width().toString() + " \u00d7 " + session!!.height())
        main({
            check(
                MenuTextAssertions.check(phone!!.getWindow()!!.getDecorView()) > 0,
                "phone shared status text and font glyphs decoded",
            )
        })
        textScreenshot("controls")
        click("monitor-rotate")
        page("rotation")
        for (value in
            arrayOf<String?>(
                "Landscape 0\u00b0",
                "Portrait 90\u00b0",
                "Landscape 180\u00b0",
                "Portrait 270\u00b0",
            )) exactText(value)
        textScreenshot("rotation")
        resume()
        controlsGesture()
        click("monitor-settings")
        page("settings")
        click("monitor-app-settings")
        page("app-settings")
        click("monitor-scroll")
        page("scroll")
        for (value in
            arrayOf<String?>(
                "Swipe \u00b7 Touchscreen drag",
                "Wheel \u00b7 Mouse scrolling",
                "Slow \u00b7 20%",
                "Normal \u00b7 50%",
                "Fast \u00b7 100%",
            )) exactText(value)
        textScreenshot("scroll")
        resume()
        controlsGesture()
        click("monitor-settings")
        page("settings")
        exactText("\u203a")
        exactText("Monitor touch \u00b7 On")
        exactText(
            (session!!.width().toString() +
                " \u00d7 " +
                session!!.height() +
                " \u00b7 " +
                session!!.dpi +
                " DPI")
        )
        textScreenshot("settings")
        click("monitor-display")
        page("display")
        val currentOutput = physicalMonitor()!!.getMode()
        exactText(
            ("Current output: " +
                currentOutput!!.getPhysicalWidth() +
                " \u00d7 " +
                currentOutput!!.getPhysicalHeight())
        )
        exactText("\u203a")
        textScreenshot("display")
        main({ session!!.key(KeyEvent.KEYCODE_BACK) })
        page("settings")
        click("monitor-diagnostics")
        page("diagnostics")
        textScreenshot("diagnostics")
        resume()
        homeGesture()
        exactText("Tap to launch \u00b7 Hold for a one-time orientation")
        main({
            (session!!.monitorShellView()!!.findViewWithTag<View?>("monitor-app-search")
                    as android.widget.EditText)
                .setText("DexDroid input test")
        })
        Thread.sleep(100)
        click("monitor-app-search")
        page("home")
        exactText("\u21e7")
        exactText("\u232b")
        textScreenshot("apps")
        val held = Rect()
        main({
            check(
                session!!
                    .monitorShellView()!!
                    .findViewWithTag<View?>("app.dev.dexdroid")!!
                    .getGlobalVisibleRect(held),
                "app text inspection target visible",
            )
        })
        event(0, arrayOf<FloatArray?>(floatArrayOf(held.exactCenterX(), held.exactCenterY())))
        Thread.sleep(650)
        event(1, arrayOf<FloatArray?>(floatArrayOf(held.exactCenterX(), held.exactCenterY())))
        page("app-rotation")
        click("monitor-up")
        page("home")
        check(
            (session!!.monitorShellView()!!.findViewWithTag<View?>("monitor-app-search")
                    as android.widget.EditText)
                .getText()!!
                .toString() == "DexDroid input test",
            "monitor Apps query survives orientation Up",
        )
        await(
            { session!!.monitorShellView()!!.findViewWithTag<View?>("app.dev.dexdroid") != null },
            "restored monitor app result available",
        )
        main({
            session!!
                .monitorShellView()!!
                .findViewWithTag<View?>("app.dev.dexdroid")!!
                .getGlobalVisibleRect(held)
        })
        event(0, arrayOf<FloatArray?>(floatArrayOf(held.exactCenterX(), held.exactCenterY())))
        Thread.sleep(650)
        event(1, arrayOf<FloatArray?>(floatArrayOf(held.exactCenterX(), held.exactCenterY())))
        page("app-rotation")

        for (value in
            arrayOf<String?>(
                "Landscape 0\u00b0",
                "Portrait 90\u00b0",
                "Landscape 180\u00b0",
                "Portrait 270\u00b0",
                "\u203a",
            )) exactText(value)
        textScreenshot("app-rotation")
        resume()
    }

    /** Force the legacy focus-wait boundary within one main turn, before its timer can run. */
    @Throws(Exception::class)
    private fun legacyFocusArbitration() {
        val rotation = session!!.rotation
        if (Build.VERSION.SDK_INT != 30) return
        val field = SessionService::class.java!!.getDeclaredField("monitor")
        field.setAccessible(true)
        val host = field.get(session) as Presentation?
        val decor = host!!.getWindow()!!.getDecorView()
        val point = physical(session!!.width() * .5f, session!!.height() * .5f)
        for (mode in 0..3) {
            val cancellation = mode
            val titleBounds = Rect()
            main({
                val titles = ArrayList<View?>()
                phone!!
                    .getWindow()!!
                    .getDecorView()
                    .findViewsWithText(titles, "DexDroid", View.FIND_VIEWS_WITH_TEXT)
                val title =
                    titles
                        .stream()!!
                        .filter({ v ->
                            (v is android.widget.TextView &&
                                (v as android.widget.TextView).getText()!!.toString() == "DexDroid")
                        })!!
                        .findFirst()
                        .orElseThrow()
                check(
                    title!!.getGlobalVisibleRect(titleBounds),
                    "phone title focus target measured",
                )
            })
            DeviceTestSupport.shell(
                runner,
                "input -d 0 tap " + titleBounds.centerX() + " " + titleBounds.centerY(),
            )
            await({ !decor.hasWindowFocus() }, "real phone contact leaves monitor awaiting focus")
            val touches = InputTestActivity.touchEvents
            val wheels = InputTestActivity.wheelEvents
            main({
                session!!.cancelInput()
                session!!.hideMonitorPointer()
                val x = session!!.cx
                val y = session!!.cy
                val time = SystemClock.uptimeMillis()
                val downEvent =
                    MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, point[0], point[1], 0)
                downEvent!!.setSource(InputDevice.SOURCE_TOUCHSCREEN)
                val upEvent = MotionEvent.obtain(downEvent)
                upEvent!!.setAction(MotionEvent.ACTION_UP)
                try {
                    check(
                        host!!.dispatchTouchEvent(downEvent!!),
                        "focus-wait accepts physical DOWN " + rotation,
                    )
                    session!!.click()
                    session!!.wheel(0f, 12f)
                    session!!.move(35f, 35f)
                    session!!.showTouchpadPointer()
                    check(
                        session!!.cx == x && session!!.cy == y && !session!!.pointerVisible,
                        ("buffered physical DOWN blocks phone pointer/click/wheel " +
                            rotation +
                            "/" +
                            cancellation),
                    )
                    check(
                        host!!.dispatchTouchEvent(upEvent!!),
                        "focus-wait accepts physical UP " + rotation,
                    )
                    if (cancellation == 0) session!!.cancelInput()
                    else if (cancellation == 1) {
                        session!!.setMonitorNavigationEnabled(false)
                        session!!.setMonitorNavigationEnabled(true)
                    } else if (cancellation == 2) {
                        session!!.setMonitorTouchEnabled(false)
                        session!!.setMonitorTouchEnabled(true)
                    }
                    // A focus callback and the scheduled fallback must not resurrect a
                    // canceled tap.
                    host!!.onWindowFocusChanged(true)
                } finally {
                    downEvent!!.recycle()
                    upEvent!!.recycle()
                }
            })
            // Fence the display worker, then allow the original 100 ms timeout to expire.
            val workerField = SessionService::class.java!!.getDeclaredField("worker")
            workerField.setAccessible(true)
            (workerField.get(session) as java.util.concurrent.ExecutorService)
                .submit({})
                .get(5, TimeUnit.SECONDS)
            if (cancellation == 3) {
                await(
                    { InputTestActivity.touchEvents >= touches + 2 },
                    "focus callback relays valid buffered contact " + rotation,
                )
            }
            Thread.sleep(180)
            check(
                (InputTestActivity.touchEvents == touches + (if (cancellation == 3) 2 else 0) &&
                    InputTestActivity.wheelEvents == wheels),
                ("canceled buffer never replays; valid release exactly once " +
                    rotation +
                    "/" +
                    cancellation),
            )
            main({ session!!.move(1f, 1f) })
            check(
                session!!.pointerVisible,
                "phone pointer resumes after focus buffer ends " + rotation,
            )
        }
    }

    @Throws(Exception::class)
    fun run(): String {
        try {
            phone = DeviceTestSupport.startPhone(runner!!)
            val ready = CountDownLatch(1)
            binding =
                object : ServiceConnection {
                    public override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
                        session = (b as SessionService.LocalBinder).get()
                        ready.countDown()
                    }

                    public override fun onServiceDisconnected(n: ComponentName?) {}
                }
            bound =
                runner!!
                    .getTargetContext()!!
                    .bindService(
                        Intent(runner!!.getTargetContext(), SessionService::class.java),
                        binding!!,
                        Context.BIND_AUTO_CREATE,
                    )
            check(ready.await(5, TimeUnit.SECONDS), "monitor controller bound")
            await({ session!!.id >= 0 && !session!!.busy }, "automatic monitor desktop ready")
            preferences = HashMap<String?, Any?>(session!!.prefs!!.getAll()!!)
            session!!.prefs!!.edit()!!.remove("statusExpanded")!!.apply()
            originalRotation = session!!.rotation
            main({
                session!!.setMonitorNavigationEnabled(true)
                session!!.setMonitorTouchEnabled(true)
            })
            val test = ComponentName(runner!!.getTargetContext()!!, InputTestActivity::class.java!!)
            textMenus(test)
            for (r in 0..3) {
                val selected = r
                main({ session!!.launch(test, selected) })
                await(
                    { !session!!.busy && session!!.rotation == selected },
                    "monitor rotation " + r,
                )
                Thread.sleep(500)
                legacyFocusArbitration()
                if (r == 0) injectedContactCleanup()
                val d = session!!.dpi / 160f
                val back = InputTestActivity.backEvents
                val cancel = InputTestActivity.cancelEvents
                val touchesBeforeEdge = InputTestActivity.touchEvents
                val upBeforeEdge = InputTestActivity.lastUpDownTime
                val capturedViewport = viewport()
                val start =
                    capturedViewport.mapVirtualToScreen(
                        session!!.width() - 4 * d,
                        session!!.height() * .4f,
                    )
                val preview =
                    capturedViewport.mapVirtualToScreen(
                        session!!.width() - 30 * d,
                        session!!.height() * .4f,
                    )
                val committed =
                    capturedViewport.mapVirtualToScreen(
                        session!!.width() - 100 * d,
                        session!!.height() * .4f,
                    )
                val originalX = start[0]
                val originalY = start[1]
                DeviceTestSupport.usableMonitorSwipe(runner, session, start, preview)
                committed[0] += start[0] - originalX
                committed[1] += start[1] - originalY
                capturedViewport.verify(runner, session)
                plannedViewport = capturedViewport
                event(0, arrayOf<FloatArray?>(start))
                event(2, arrayOf<FloatArray?>(preview))
                main({
                    val feedback =
                        session!!
                            .monitorShellView()!!
                            .findViewWithTag<EdgeFeedback?>("monitor-edge-feedback")
                    check(
                        (feedback!!.isShown() &&
                            feedback!!.progress() > 0 &&
                            feedback!!.progress() < 1),
                        "live pre-threshold Back feedback " + selected,
                    )
                })
                check(
                    InputTestActivity.backEvents == back,
                    "feedback preview does not send Back " + r,
                )
                event(2, arrayOf<FloatArray?>(committed))
                event(1, arrayOf<FloatArray?>(committed))
                await(
                    { InputTestActivity.backEvents == back + 1 },
                    "right-edge Back exactly once rotation " + r,
                )
                await(
                    {
                        (InputTestActivity.touchEvents == touchesBeforeEdge ||
                            InputTestActivity.cancelEvents > cancel)
                    },
                    "edge Back discards unreleased contact or cancels relayed contact " + r,
                )
                await(
                    {
                        (session!!
                            .monitorShellView()!!
                            .findViewWithTag<View?>("monitor-edge-feedback")!!
                            .getVisibility() == View.GONE)
                    },
                    "committed feedback clears " + r,
                )
                check(
                    InputTestActivity.lastUpDownTime == upBeforeEdge,
                    "recognized edge Back never delivers app UP " + r,
                )
                check(!session!!.pointerVisible, "edge input hides cursor " + r)
                noChrome()
                val afterBack = InputTestActivity.backEvents
                capturedViewport.verify(runner, session)
                plannedViewport = capturedViewport
                event(0, arrayOf<FloatArray?>(start))
                event(2, arrayOf<FloatArray?>(committed))
                main({
                    check(
                        InputTestActivity.backEvents == afterBack,
                        "recognized Back waits for physical release " + selected,
                    )
                })
                event(MotionEvent.ACTION_CANCEL, arrayOf<FloatArray?>(committed))
                Thread.sleep(200)
                check(
                    InputTestActivity.backEvents == afterBack,
                    "canceled recognized Back does not navigate " + r,
                )
                noChrome()
                homeGesture()
                main({
                    val root = session!!.monitorShellView()
                    check(
                        root!!.findViewWithTag<View?>("monitor-close")
                            is android.widget.ImageButton,
                        "launcher Close is an icon " + selected,
                    )
                    check(
                        root!!.findViewWithTag<View?>("monitor-resume") == null,
                        "launcher has no Resume footer " + selected,
                    )
                    val search =
                        root!!.findViewWithTag<android.widget.EditText?>("monitor-app-search")
                    check(search != null, "phone-style launcher search exists " + selected)
                    search!!.setText("DexDroid input test")
                })
                Thread.sleep(100)
                main({
                    check(
                        ((session!!.monitorShellView()!!.findViewWithTag<View?>("monitor-app-list")
                                as android.widget.ListView)
                            .getAdapter()!!
                            .getCount() == 1),
                        "launcher search filters real app list " + selected,
                    )
                })
                click("app.dev.dexdroid")
                page("closed")
                await(
                    { session!!.activePackage == "dev.dexdroid" && !session!!.busy },
                    "monitor launcher targets external app " + r,
                )
                Thread.sleep(300)
                main({ session!!.rotate(selected, false) })
                await(
                    { !session!!.busy && session!!.rotation == selected },
                    "control panel test orientation " + r,
                )
                Thread.sleep(300)
                controlsGesture()
                parity()
                navigationFlow(r)
                renderedContrast()
                click("monitor-title")
                page("controls")
                click("monitor-settings")
                page("settings")
                click("monitor-touch")
                check(!session!!.monitorTouchEnabled(), "monitor settings touch Off " + r)
                click("monitor-touch")
                check(session!!.monitorTouchEnabled(), "monitor settings touch On " + r)
                resume()
                controlsGesture()
                outside()
                homeGesture()
                outside()
                // Launcher selection applies the app's saved/default rotation; re-establish
                // this test's explicit orientation before the next edge gesture.
                main({ session!!.rotate(selected, false) })
                await(
                    { !session!!.busy && session!!.rotation == selected },
                    "restore test rotation " + r,
                )
                Thread.sleep(300)
            }
            homeGesture()
            val saved = session!!.prefs!!.getInt("app.dev.dexdroid", -1)
            val held = Rect()
            main({
                check(
                    session!!
                        .monitorShellView()!!
                        .findViewWithTag<View?>("app.dev.dexdroid")!!
                        .getGlobalVisibleRect(held),
                    "held-app target visible",
                )
            })
            event(0, arrayOf<FloatArray?>(floatArrayOf(held.exactCenterX(), held.exactCenterY())))
            Thread.sleep(650)
            event(1, arrayOf<FloatArray?>(floatArrayOf(held.exactCenterX(), held.exactCenterY())))
            page("app-rotation")
            click("monitor-once-1")
            page("closed")
            await(
                { !session!!.busy && session!!.rotation == 1 },
                "held app applies one-time rotation",
            )
            check(
                session!!.prefs!!.getInt("app.dev.dexdroid", -1) == saved,
                "held-app override does not overwrite saved rotation",
            )
            val d = session!!.dpi / 160f
            val back = InputTestActivity.backEvents
            swipe(
                session!!.width() - 4 * d,
                session!!.height() * .4f,
                session!!.width() - 20 * d,
                session!!.height() * .4f,
            )
            check(InputTestActivity.backEvents == back, "short edge swipe rejected")
            swipe(
                session!!.width() - 12 * d,
                session!!.height() * .4f,
                session!!.width() - 2 * d,
                session!!.height() * .4f,
            )
            check(InputTestActivity.backEvents == back, "outward edge swipe rejected")
            swipe(
                session!!.width() - 4 * d,
                session!!.height() * .3f,
                session!!.width() - 5 * d,
                session!!.height() * .5f,
            )
            check(
                InputTestActivity.backEvents == back,
                "vertical side-edge swipe remains app input",
            )
            event(
                0,
                arrayOf<FloatArray?>(physical(session!!.width() - 4 * d, session!!.height() * .4f)),
            )
            event(
                5 or 256,
                arrayOf<FloatArray?>(
                    physical(session!!.width() - 4 * d, session!!.height() * .4f),
                    physical(session!!.width() - 30 * d, session!!.height() * .5f),
                ),
            )
            event(
                2,
                arrayOf<FloatArray?>(
                    physical(session!!.width() - 100 * d, session!!.height() * .4f),
                    physical(session!!.width() - 120 * d, session!!.height() * .5f),
                ),
            )
            event(
                6 or 256,
                arrayOf<FloatArray?>(
                    physical(session!!.width() - 100 * d, session!!.height() * .4f),
                    physical(session!!.width() - 120 * d, session!!.height() * .5f),
                ),
            )
            event(
                1,
                arrayOf<FloatArray?>(
                    physical(session!!.width() - 100 * d, session!!.height() * .4f)
                ),
            )
            Thread.sleep(150)
            check(InputTestActivity.backEvents == back, "multi-touch edge gesture rejected")
            main({ session!!.setMonitorNavigationEnabled(false) })
            swipe(
                session!!.width() - 4 * d,
                session!!.height() * .4f,
                session!!.width() - 100 * d,
                session!!.height() * .4f,
            )
            check(
                InputTestActivity.backEvents == back,
                "disabled edge navigation leaves native input",
            )
            main({ session!!.setMonitorNavigationEnabled(true) })
            swipe(4 * d, session!!.height() * .4f, 100 * d, session!!.height() * .4f)
            await({ InputTestActivity.backEvents == back + 1 }, "left-edge inward Back")
            homeGesture()
            val underlying = InputTestActivity.touchEvents
            click("monitor-close")
            page("closed")
            check(
                InputTestActivity.touchEvents == underlying,
                "launcher close does not click underlying app",
            )
            controlsGesture()
            val count = intArrayOf(InputTestActivity.touchEvents)
            main({
                session!!.cx = 4 * session!!.dpi / 160f
                session!!.cy = session!!.height() * .5f
                session!!.click()
            })
            page("closed")
            check(
                InputTestActivity.touchEvents == count!![0],
                "phone pointer outside dismissal has no click-through",
            )
            controlsGesture()
            click("monitor-settings")
            page("settings")
            click("monitor-app-settings")
            page("app-settings")
            main({
                val button = session!!.monitorShellView()!!.findViewWithTag<View?>("monitor-scroll")
                var x = 0f
                var y = 0f
                var current = button
                while (current !== session!!.monitorShellView()) {
                    x += current!!.getLeft().toFloat()
                    y += current!!.getTop().toFloat()
                    current = current!!.getParent() as View?
                }
                session!!.cx = x + button!!.getWidth() / 2f
                session!!.cy = y + button!!.getHeight() / 2f
                session!!.click()
            })
            page("scroll")
            check(session!!.pointerVisible, "phone pointer operates monitor icons")
            click("monitor-swipe")
            check(session!!.swipeScrolling(), "monitor selects native swipe mode")
            click("monitor-speed-0")
            check(session!!.scrollSpeed() == .2f, "monitor saves scroll speed")
            resume()
            homeGesture()
            val appList = arrayOf<android.widget.ListView?>(null)
            val first = intArrayOf(-1)
            main({
                appList[0] =
                    session!!.monitorShellView()!!.findViewWithTag<View?>("monitor-app-list")
                        as android.widget.ListView?
                first[0] = appList!![0]!!.getFirstVisiblePosition()
                var x = 0f
                var y = 0f
                var current: View? = appList!![0]
                while (current !== session!!.monitorShellView()) {
                    x += current!!.getLeft().toFloat()
                    y += current!!.getTop().toFloat()
                    val parent = current!!.getParent() as View?
                    x -= parent!!.getScrollX().toFloat()
                    y -= parent!!.getScrollY().toFloat()
                    current = parent
                }
                session!!.cx = x + appList!![0]!!.getWidth() / 2f
                session!!.cy = y + appList!![0]!!.getHeight() / 2f
                session!!.wheel(0f, -20f)
            })
            await(
                { appList!![0]!!.getFirstVisiblePosition() > first!![0] },
                "phone wheel scrolls native monitor launcher",
            )
            check(session!!.pointerVisible, "phone menu scrolling restores cursor")
            resume()
            controlsGesture()
            val rotateTo = (session!!.rotation + 1) % 4
            click("monitor-rotate")
            page("rotation")
            click("monitor-rotation-" + rotateTo)
            await(
                { !session!!.busy && session!!.rotation == rotateTo },
                "monitor Rotate changes upright desktop",
            )
            page("controls")
            click("monitor-settings")
            page("settings")
            click("monitor-app-settings")
            page("app-settings")
            click("monitor-save")
            check(
                session!!.prefs!!.getInt("app.dev.dexdroid", -1) == session!!.rotation,
                "monitor Save persists rotation",
            )
            click("monitor-reset")
            check(
                !session!!.prefs!!.contains("app.dev.dexdroid"),
                "monitor Reset clears app rotation",
            )
            await({ !session!!.busy }, "reset rotation complete")
            resume()
            main({ session!!.rotate(0, false) })
            await({ !session!!.busy && session!!.rotation == 0 }, "Fit test landscape")
            main({ session!!.preview(1920, 1080, 240) })
            await({ !session!!.busy && session!!.baseH == 1080 }, "Fit test preview")
            event(0, arrayOf<FloatArray?>(floatArrayOf(960f, 45f)))
            event(2, arrayOf<FloatArray?>(floatArrayOf(960f, 250f)))
            event(1, arrayOf<FloatArray?>(floatArrayOf(960f, 250f)))
            Thread.sleep(100)
            page("closed")
            controlsGesture()
            main({
                val root = session!!.monitorShellView()
                val card =
                    root!!.findViewWithTag<ControllerStyle.StatusCard?>("controller-status-card")
                check(
                    (card!!.confirmation!!.isShown() &&
                        root!!.findViewWithTag<View?>("monitor-confirmation") == null),
                    "resolution preview uses one shared status-card confirmation row",
                )
            })
            tap(960f, 45f)
            page("closed")
            check(
                (session!!.diagnosticText().contains("Monitor edge recognized") &&
                    session!!.diagnosticText().contains("Monitor transition") &&
                    session!!.diagnosticText().contains("Monitor edge rejected")),
                "edge and animation outcomes in diagnostic log",
            )
            main({ session!!.confirm(false) })
            await({ !session!!.busy }, "Fit reverted")
            main({ phone!!.finish() })
            Thread.sleep(300)
            homeGesture()
            click("app.dev.dexdroid")
            page("closed")
            await(
                { !session!!.busy && session!!.activePackage == "dev.dexdroid" },
                "background launcher app ready",
            )
            Thread.sleep(300)
            controlsGesture()
            check(session!!.sessionAwake(), "monitor shell works with phone controller destroyed")
            click("monitor-stop")
            await(
                { session!!.id < 0 && session!!.monitorShellView() == null },
                "monitor Stop releases shell and display",
            )
            restore()
            return results.toString()
        } catch (error: Throwable) {
            throw RuntimeException(results.toString() + "FAIL monitor acceptance", error)
        } finally {
            restore()
            if (bound) runner!!.getTargetContext()!!.unbindService(binding!!)
        }
    }
}
