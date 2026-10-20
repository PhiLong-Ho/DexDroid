package dev.dexdroid

import android.app.*
import android.content.*
import android.content.pm.ActivityInfo
import android.graphics.*
import android.graphics.drawable.*
import android.os.*
import android.view.*
import android.widget.*
import java.io.*
import java.util.*
import java.util.concurrent.*
import java.util.function.BooleanSupplier

/** Exercises real phone menu windows, keyboard resizing, selected state and rotation. */
class PhoneMenuAcceptance(
    private val runner: Instrumentation?,
    private val phone: MainActivity?,
    private val session: SessionService?,
) {
    private val results: StringBuilder = StringBuilder()
    private var modernRotation: Boolean = false
    private var ownerSampleCount: Long = 0
    private var nativeBackAttempt: Int? = null
    private var capturedTaskIdentity: Int? = null
    private var lastOwnerObservation: String = "unqueried"

    private fun observeMenu(page: String?, ready: Boolean): Boolean {
        val result = booleanArrayOf(false)
        main({
            if (capturedTaskIdentity == null) capturedTaskIdentity = phone!!.getTaskId()
            val dialog = phone!!.controllerMenu()
            val window = dialog?.getWindow()
            val decor = window?.getDecorView()
            // Keep the same predicates and captured Activity. Diagnostics never select an owner.
            result[0] =
                if (ready) {
                    val required = decor!!
                    required.isShown() && required.hasWindowFocus() && !required.isLayoutRequested()
                } else {
                    dialog != null && decor!!.findViewWithTag<View?>("phone-menu-" + page!!) != null
                }
            val tags =
                arrayOf(
                    "settings",
                    "pointer-speed",
                    "display",
                    "profiles",
                    "diagnostics",
                    "apps",
                    "scroll",
                    "rotation",
                    "app-settings",
                    "protected-help",
                )
            val present = tags.filter { decor?.findViewWithTag<View?>("phone-menu-" + it) != null }
            val ime = decor?.getRootWindowInsets()?.isVisible(WindowInsets.Type.ime())
            ownerSampleCount++
            lastOwnerObservation =
                "schema=1; sample=" +
                    ownerSampleCount +
                    "; backCycle=" +
                    nativeBackAttempt +
                    "; uptime=" +
                    SystemClock.uptimeMillis() +
                    "; phase=" +
                    (if (ready) "back-ready" else "page-" + page) +
                    "; predicate=" +
                    result[0] +
                    "; activity=" +
                    System.identityHashCode(phone) +
                    "; capturedTask=" +
                    capturedTaskIdentity +
                    "; display=" +
                    phone!!.getDisplay()?.getDisplayId() +
                    "; finishing=" +
                    phone!!.isFinishing() +
                    "; destroyed=" +
                    phone!!.isDestroyed() +
                    "; dialog=" +
                    (dialog?.let { System.identityHashCode(it) } ?: "null") +
                    "; showing=" +
                    dialog?.isShowing() +
                    "; window=" +
                    (window?.let { System.identityHashCode(it) } ?: "null") +
                    "; root=" +
                    (decor?.let { System.identityHashCode(it) } ?: "null") +
                    "; token=" +
                    (decor?.getWindowToken()?.let { System.identityHashCode(it) } ?: "null") +
                    "; shown=" +
                    decor?.isShown() +
                    "; focus=" +
                    decor?.hasWindowFocus() +
                    "; layout=" +
                    decor?.isLayoutRequested() +
                    "; ime=" +
                    ime +
                    "; tags=" +
                    present.joinToString(",")
        })
        return result[0]
    }

    private fun retainOwnerFailure() {
        val dir = File(runner!!.getTargetContext()!!.getExternalCacheDir(), "submenu-acceptance")
        if (!dir.isDirectory() && !dir.mkdirs())
            throw IOException("Cannot create owner evidence folder")
        require(lastOwnerObservation.length <= 8192)
        File(dir, "phone-owner-failure.txt").writeText(lastOwnerObservation + "\n", Charsets.UTF_8)
    }

    private fun main(r: Runnable?) {
        runner!!.runOnMainSync(r)
    }

    private fun check(ok: Boolean, label: String?) {
        if (!ok) throw AssertionError(label)
        results.append("PASS ").append(label).append('\n')
    }

    @Throws(Exception::class)
    private fun await(p: BooleanSupplier, label: String?) {
        val until = SystemClock.uptimeMillis() + 10000
        while (!p.getAsBoolean() && SystemClock.uptimeMillis() < until) Thread.sleep(50)
        check(p.getAsBoolean(), label)
    }

    private fun text(root: View?, value: String?): View? {
        if (root is TextView && value!!.contentEquals((root as TextView).getText()!!)) return root
        if (root is ViewGroup) {
            val group = root as ViewGroup?
            for (i in 0 until group!!.getChildCount()) {
                val found = text(group!!.getChildAt(i), value)
                if (found != null) return found
            }
        }
        return null
    }

    private fun description(root: View, value: String): View? {
        if (
            value.contentEquals(
                if (root.getContentDescription() == null) "" else root.getContentDescription()
            )
        )
            return root
        if (root is ViewGroup) {
            val group = root as ViewGroup
            for (i in 0 until group!!.getChildCount()) {
                val found = description(group!!.getChildAt(i)!!, value)
                if (found != null) return found
            }
        }
        return null
    }

    private fun root(): View {
        return phone!!.controllerMenu()!!.getWindow()!!.getDecorView()
    }

    @Throws(Exception::class)
    private fun open(button: String?) {
        main({
            val found =
                if (button == "Scroll")
                    phone!!.getWindow()!!.getDecorView().findViewWithTag<View?>("phone-scroll")
                else text(phone!!.getWindow()!!.getDecorView(), button)
            check(found != null, "phone submenu trigger exists: " + button!!)
            found!!.performClick()
        })
        await(
            { phone!!.controllerMenu() != null && phone!!.controllerMenu()!!.isShowing() },
            "phone submenu opens: " + button!!,
        )
        Thread.sleep(200)
    }

    private fun clickText(label: String?) {
        main({
            val found = text(root(), label)
            check(found != null, "phone menu label exists: " + label!!)
            var target = found
            while (!target!!.isClickable() && target!!.getParent() is View) target =
                target!!.getParent() as View?
            check(target!!.isClickable(), "phone menu action is clickable: " + label!!)
            target!!.performClick()
        })
    }

    @Throws(Exception::class)
    private fun close() {
        main({
            val dialog = phone!!.controllerMenu()
            check(dialog != null, "phone submenu available to close")
            dialog!!
                .getWindow()!!
                .getDecorView()
                .findViewWithTag<View?>("phone-menu-close")!!
                .performClick()
        })
        await({ phone!!.controllerMenu() == null }, "phone Close icon dismisses submenu")
    }

    @Throws(Exception::class)
    private fun inspect(page: String?) {
        main({
            val panel = root().findViewWithTag<View?>("phone-menu-" + page!!)
            val close = root().findViewWithTag<View?>("phone-menu-close")
            check(panel != null && panel!!.isShown(), "phone Settings-style panel: " + page!!)
            results.append(MenuHeaderAssertions.inspect(root(), "phone"))
            check(
                close is ImageButton && close!!.isShown(),
                "phone submenu fixed icon Close: " + page!!,
            )
            val insets = root().getRootWindowInsets()
            check(insets != null, "phone menu receives actual window insets: " + page!!)
            val safe =
                insets!!.getInsets(
                    (WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                )
            val position = IntArray(2)
            close!!.getLocationOnScreen(position)
            val screenHeight =
                phone!!.getWindowManager()!!.getCurrentWindowMetrics().getBounds().height()
            check(
                (position[1] >= safe.top &&
                    (position[1] + close!!.getHeight() <= screenHeight - safe.bottom)),
                "phone Close stays clear of system bars and cutout: " + page!!,
            )
            check(
                MenuTextAssertions.check(root()) > 0,
                "phone submenu decoded text and glyphs: " + page!!,
            )
            val area = Rect()
            root().getWindowVisibleDisplayFrame(area)
            check(
                panel!!.getHeight() >= area.height() * .94,
                "phone submenu uses full available height: " + page!!,
            )
            check(
                (panel!!.getWidth() > panel!!.getHeight() * .3 &&
                    panel!!.getHeight() > panel!!.getWidth()),
                "phone submenu remains portrait: " + page!!,
            )
        })
    }

    private fun card(view: View, label: String?) {
        check(
            view is TextView && view.getBackground() is RippleDrawable,
            "Settings row surface: " + label!!,
        )
        val text = view as TextView
        val d = view.getResources()!!.getDisplayMetrics()!!.density
        check(
            text!!.getMinHeight() >= 61 * d && text!!.getCurrentTextColor() == Color.WHITE,
            "Settings row size and white text: " + label!!,
        )
        val background = (view.getBackground() as RippleDrawable).getDrawable(0)
        check(
            (background is GradientDrawable &&
                Math.abs((background as GradientDrawable).getCornerRadius() / d - 8) < 1),
            "Settings row rounded geometry: " + label!!,
        )
    }

    @Throws(Exception::class)
    private fun shot(name: String?) {
        Thread.sleep(200)
        val bitmap = runner!!.getUiAutomation()!!.takeScreenshot()
        check(bitmap != null, "captured phone menu: " + name!!)
        try {
            val dir =
                File(runner!!.getTargetContext()!!.getExternalCacheDir(), "submenu-acceptance")
            if (!dir.isDirectory() && !dir.mkdirs())
                throw IOException("Cannot create screenshot folder")
            FileOutputStream(File(dir, name!! + ".png"))
                .use({ stream ->
                    check(
                        bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, stream),
                        "exported phone menu: " + name!!,
                    )
                })
        } finally {
            bitmap!!.recycle()
        }
    }

    @Throws(Exception::class)
    private fun tap(view: View?) {
        val rect = Rect()
        main({
            check(view!!.getGlobalVisibleRect(rect), "phone touch target visible")
            val origin = IntArray(2)
            view!!.getRootView()!!.getLocationOnScreen(origin)
            rect.offset(origin[0], origin[1])
            check(
                rect.width() > 0 && rect.height() > 0,
                "phone rendered target screen bounds " + rect.toShortString(),
            )
        })
        val now = SystemClock.uptimeMillis()
        for (action in intArrayOf(0, 1)) {
            val e =
                MotionEvent.obtain(
                    now,
                    SystemClock.uptimeMillis(),
                    action,
                    rect.exactCenterX(),
                    rect.exactCenterY(),
                    0,
                )
            e!!.setSource(InputDevice.SOURCE_TOUCHSCREEN)
            check(
                runner!!.getUiAutomation()!!.injectInputEvent(e, true),
                "real phone menu tap " + action,
            )
            e!!.recycle()
        }
        Thread.sleep(150)
    }

    @Throws(Exception::class)
    private fun phoneRotation(mode: String?) {
        val command =
            if (modernRotation) "wm user-rotation -d 0 " + mode!!
            else
                ("wm set-user-rotation " +
                    mode!!
                        .split((" ").toRegex())
                        .dropLastWhile({ it.isEmpty() })
                        .toTypedArray()[0] +
                    " -d 0" +
                    (if (mode!!.startsWith("lock ")) " " + mode!!.substring(5) else ""))
        val result = DeviceTestSupport.shell(runner, command)
        check(
            !result!!.contains("Unknown command") && !result!!.contains("Error:"),
            "phone rotation command is supported",
        )
    }

    @Throws(Exception::class)
    private fun touchpadDrag() {
        val point = IntArray(2)
        val scrollY = intArrayOf(0)
        val before = FloatArray(2)
        main({
            val scroll =
                phone!!
                    .getWindow()!!
                    .getDecorView()
                    .findViewWithTag<ScrollView?>("phone-controller-scroll")
            val pad = scroll!!.findViewWithTag<View?>("touchpad")
            pad!!.getLocationOnScreen(point)
            point[0] += pad!!.getWidth() / 2
            point[1] +=
                (pad!!.getHeight() -
                    Math.round(24 * pad!!.getResources()!!.getDisplayMetrics()!!.density))
            scrollY[0] = scroll!!.getScrollY()
            session!!.center()
            before[0] = session!!.cx
            before[1] = session!!.cy
        })
        val down = SystemClock.uptimeMillis()
        for (i in 0..2) {
            val event =
                MotionEvent.obtain(
                    down,
                    SystemClock.uptimeMillis(),
                    intArrayOf(0, 2, 1)[i],
                    (point[0] + (if (i == 0) 0 else 40)).toFloat(),
                    (point[1] - (if (i == 0) 0 else 80)).toFloat(),
                    0,
                )
            event!!.setSource(InputDevice.SOURCE_TOUCHSCREEN)
            check(
                runner!!.getUiAutomation()!!.injectInputEvent(event, true),
                "real touchpad drag event " + i,
            )
            event!!.recycle()
            Thread.sleep(40)
        }
        main({
            val scroll =
                phone!!
                    .getWindow()!!
                    .getDecorView()
                    .findViewWithTag<ScrollView?>("phone-controller-scroll")
            check(
                scroll!!.getScrollY() == scrollY!![0],
                "parent scroll does not steal touchpad drag",
            )
            check(
                (Math.abs(session!!.cx - before[0]) > 10 &&
                    Math.abs(session!!.cy - before[1]) > 10),
                "touchpad drag still moves monitor pointer",
            )
        })
    }

    private fun hintWindow(): View? {
        for (window in android.view.inspector.WindowInspector.getGlobalWindowViews()) if (
            (window!!.getDisplay() != null &&
                window!!.getDisplay()!!.getDisplayId() == 0 &&
                window!!.isShown() &&
                text(window, "Touchpad gestures") != null)
        )
            return window
        return null
    }

    @Throws(Exception::class)
    private fun touchpadActions() {
        val decor = phone!!.getWindow()!!.getDecorView()
        val backs = InputTestActivity.backEvents
        val touches = InputTestActivity.touchEvents
        val x = session!!.cx
        val y = session!!.cy
        tap(decor.findViewWithTag<View?>("phone-back"))
        await(
            { InputTestActivity.backEvents == backs + 1 },
            "touchpad Back sends exactly one native Back",
        )
        check(
            InputTestActivity.touchEvents == touches && session!!.cx == x && session!!.cy == y,
            "Back icon does not leak a touchpad gesture",
        )
        shot("phone-touchpad-actions")
        tap(decor.findViewWithTag<View?>("touchpad-help"))
        await({ hintWindow() != null }, "touchpad hint opens gesture instructions on phone display")
        check(
            InputTestActivity.touchEvents == touches && session!!.cx == x && session!!.cy == y,
            "hint icon does not leak a touchpad gesture",
        )
        shot("phone-touchpad-hint")
        val gotIt = text(hintWindow(), "Got it")
        check(gotIt != null, "phone hint dismiss action exists")
        tap(gotIt)
        await(
            { hintWindow() == null },
            "phone hint dismiss action works independently of monitor focus",
        )
        tap(decor.findViewWithTag<View?>("touchpad-title"))
        check(
            (InputTestActivity.touchEvents == touches &&
                session!!.cx == x &&
                session!!.cy == y &&
                phone!!.controllerMenu() == null),
            "blank touchpad header consumes taps without pointer/app input",
        )
        tap(decor.findViewWithTag<View?>("phone-scroll"))
        expectPage("scroll")
        check(
            InputTestActivity.touchEvents == touches && session!!.cx == x && session!!.cy == y,
            "Scroll icon opens configuration without pad input",
        )
        close()
        open("Layout")
        inspect("layout")
        shot("phone-layout-controls")
        close()
    }

    @Throws(Exception::class)
    private fun expectPage(page: String?) {
        await({ observeMenu(page, false) }, "phone navigation reaches " + page!!)
    }

    private fun up() {
        main({
            val action = root().findViewWithTag<View?>("phone-menu-up")
            check(action != null && action!!.isShown(), "nested page has leading Up")
            action!!.performClick()
        })
    }

    @Throws(Exception::class)
    private fun queuedBackOwnership() {
        clickText("Pointer speed")
        expectPage("pointer-speed")
        main({
            val owner = phone!!.controllerMenu()
            owner!!.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
            owner!!.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        })
        runner!!.waitForIdleSync()
        expectPage("settings")
        check(phone!!.controllerMenu()!!.isShowing(), "duplicate pending Back navigates only once")
        clickText("Pointer speed")
        expectPage("pointer-speed")
        val stale = arrayOf<Dialog?>(null)
        main({
            stale[0] = phone!!.controllerMenu()
            stale!![0]!!.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
            root().findViewWithTag<View?>("phone-menu-close")!!.performClick()
        })
        runner!!.waitForIdleSync()
        check(phone!!.controllerMenu() == null, "Close invalidates queued Back without reopening")
        main({ description(phone!!.getWindow()!!.getDecorView(), "Settings")!!.performClick() })
        expectPage("settings")
        clickText("Pointer speed")
        expectPage("pointer-speed")
        main({
            val owner = phone!!.controllerMenu()
            owner!!.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
            root().findViewWithTag<View?>("phone-menu-up")!!.performClick()
            stale!![0]!!.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        })
        runner!!.waitForIdleSync()
        expectPage("settings")
        check(phone!!.controllerMenu()!!.isShowing(), "replaced and stale dialog Back stays inert")
    }

    @Throws(Exception::class)
    private fun navigationFlow() {
        main({ description(phone!!.getWindow()!!.getDecorView(), "Settings")!!.performClick() })
        main({
            (root().findViewWithTag<View?>("phone-menu-scroll") as ScrollView).fullScroll(
                View.FOCUS_DOWN
            )
        })
        await(
            {
                val view = root().findViewWithTag<ScrollView?>("phone-menu-scroll")
                (view!!.getScrollY() ==
                    Math.max(
                        0,
                        ((view!!.getChildAt(0)!!.getHeight() - view!!.getHeight()) +
                            view!!.getPaddingTop() +
                            view!!.getPaddingBottom()),
                    ))
            },
            "Settings smooth scroll settles before recording parent position",
        )
        val scroll = (root().findViewWithTag<View?>("phone-menu-scroll") as ScrollView).getScrollY()
        check(scroll > 0, "Settings parent is scrolled before child navigation")
        clickText("Diagnostic log")
        expectPage("diagnostics")
        main({
            val action = root().findViewWithTag<View?>("phone-menu-up")
            val title = root().findViewWithTag<View?>("phone-menu-title")
            val close = root().findViewWithTag<View?>("phone-menu-close")
            check(
                action!!.getLeft() < title!!.getLeft() && title!!.getLeft() < close!!.getLeft(),
                "nested header separates leading Up and trailing Close",
            )
        })
        shot("phone-navigation-diagnostics")
        up()
        expectPage("settings")
        await(
            {
                ((root().findViewWithTag<View?>("phone-menu-scroll") as ScrollView).getScrollY() ==
                    scroll)
            },
            "return retains Settings scroll position expected=" + scroll,
        )
        for (child in
            arrayOf<Array<String?>?>(
                arrayOf<String?>("Pointer speed", "pointer-speed"),
                arrayOf<String?>("Resolution and DPI", "display"),
                arrayOf<String?>("Monitor profiles", "profiles"),
                arrayOf<String?>("Protected screen help", "protected-help"),
                arrayOf<String?>("App settings", "app-settings"),
            )) {
            clickText(child!![0])
            expectPage(child!![1])
            up()
            expectPage("settings")
        }
        val width = session!!.baseW
        val height = session!!.baseH
        val dpi = session!!.dpi
        clickText("Resolution and DPI")
        expectPage("display")
        main({
            (root().findViewWithTag<View?>("phone-display-size") as EditText).setText(
                width.toString() + "x" + height
            )
            (root().findViewWithTag<View?>("phone-display-dpi") as EditText).setText(
                Integer.toString(dpi)
            )
        })
        clickText("Preview resolution")
        expectPage("settings")
        await(
            { session!!.awaitingConfirmation() && !session!!.busy },
            "preview returns to parent with pending confirmation",
        )
        main({
            check(
                (root().findViewWithTag<View?>("phone-menu-keep")!!.isShown() &&
                    root().findViewWithTag<View?>("phone-menu-revert")!!.isShown()),
                "Keep and Revert remain reachable below parent menu header",
            )
        })
        shot("phone-navigation-preview")
        main({ root().findViewWithTag<View?>("phone-menu-revert")!!.performClick() })
        await(
            {
                (!session!!.awaitingConfirmation() &&
                    !session!!.busy &&
                    session!!.baseW == width &&
                    session!!.baseH == height &&
                    session!!.dpi == dpi)
            },
            "parent menu Revert restores display configuration",
        )
        for (attempt in 0..2) {
            nativeBackAttempt = attempt
            clickText("Pointer speed")
            expectPage("pointer-speed")
            await({ observeMenu(null, true) }, "native Back owner window is ready")
            DeviceTestSupport.shell(runner, "input -d 0 keyevent 4")
            expectPage("settings")
        }
        nativeBackAttempt = null
        queuedBackOwnership()
        clickText("Pointer speed")
        main({ root().findViewWithTag<View?>("phone-pointer-0")!!.performClick() })
        expectPage("settings")
        check(
            session!!.prefs!!.getFloat("speed", 0f) == 1f,
            "immediate choice returns to Settings with value applied",
        )
        clickText("Diagnostic log")
        close()
        open("Apps")
        check(
            root().findViewWithTag<View?>("phone-menu-up") == null,
            "fresh Apps root has no stale Settings parent",
        )
        close()
        main({ description(phone!!.getWindow()!!.getDecorView(), "Settings")!!.performClick() })
        expectPage("settings")
        clickText("App settings")
        expectPage("app-settings")
        shot("phone-app-settings")
        clickText("Scrolling")
        expectPage("scroll")
        up()
        expectPage("app-settings")
        clickText("Save app settings")
        check(
            session!!.prefs!!.getInt("app." + session!!.activePackage!!, -1) == session!!.rotation,
            "Save remains usable in app-specific options",
        )
        clickText("Reset app settings")
        check(
            !session!!.prefs!!.contains("app." + session!!.activePackage!!),
            "Reset remains usable in app-specific options",
        )
        await({ !session!!.busy }, "app reset configuration settles before next launcher")
        up()
        expectPage("settings")
        close()
    }

    @Throws(Exception::class)
    private fun statusRecoveryFlow() {
        val controller = phone!!.getWindow()!!.getDecorView()
        val card = controller.findViewWithTag<ControllerStyle.StatusCard?>("controller-status-card")
        val before = session!!.shizukuState
        try {
            main({
                session!!.shizukuState = ShizukuSetup.State.MISSING
                card!!.refresh()
            })
            await(
                {
                    val action = controller.findViewWithTag<View?>("phone-shizuku-recovery")
                    (action!!.isShown() &&
                        action!!.getGlobalVisibleRect(Rect()) &&
                        action!!.getHeight() > 0)
                },
                "missing-manager fixture keeps install prompt visible with status collapsed",
            )
            check(
                controller.findViewWithTag<View?>("phone-status-details")!!.getVisibility() ==
                    View.GONE,
                "install prompt does not require expanding details",
            )
            shot("phone-shizuku-install-prompt")
            tap(controller.findViewWithTag<View?>("phone-shizuku-recovery"))
            expectPage("shizuku")
            check(
                !session!!.prefs!!.getBoolean("statusExpanded", false),
                "recovery button does not toggle disclosure",
            )
            check(
                session!!.shizukuState == ShizukuSetup.State.CONNECTED,
                "opening setup rechecks real installed/running manager after stale state fixture",
            )
            close()
        } finally {
            main({
                session!!.shizukuState = before
                card!!.refresh()
            })
        }
        tap(card!!.monitor)
        await(
            { controller.findViewWithTag<View?>("phone-status-details")!!.isShown() },
            "expanded status renders",
        )
        shot("phone-status-expanded")
        tap(controller.findViewWithTag<View?>("phone-status-cue"))
        await(
            {
                (controller.findViewWithTag<View?>("phone-status-details")!!.getVisibility() ==
                    View.GONE)
            },
            "status restores collapsed after evidence capture",
        )
    }

    @Throws(Exception::class)
    private fun setupGuideFlow() {
        main({ description(phone!!.getWindow()!!.getDecorView(), "Settings")!!.performClick() })
        clickText("User guide")
        expectPage("user-guide")
        for (i in 0..7) check(
            root().findViewWithTag<View?>("guide-section-" + i) != null,
            "offline phone guide section " + i,
        )
        main({
            (root().findViewWithTag<View?>("phone-menu-scroll") as ScrollView).fullScroll(
                View.FOCUS_DOWN
            )
        })
        await(
            {
                val scroll = root().findViewWithTag<ScrollView?>("phone-menu-scroll")
                (scroll!!.getScrollY() ==
                    Math.max(0, scroll!!.getChildAt(0)!!.getHeight() - scroll!!.getHeight()))
            },
            "guide bottom settles",
        )
        val position =
            (root().findViewWithTag<View?>("phone-menu-scroll") as ScrollView).getScrollY()
        shot("phone-user-guide")
        clickText("Protected screen help")
        expectPage("protected-help")
        up()
        expectPage("user-guide")
        await(
            {
                ((root().findViewWithTag<View?>("phone-menu-scroll") as ScrollView).getScrollY() ==
                    position)
            },
            "guide scroll survives related help return",
        )
        clickText("Shizuku setup")
        expectPage("shizuku")
        check(
            (root().findViewWithTag<View?>("phone-shizuku-state") as TextView)
                .getText()!!
                .toString()
                .contains("Connected"),
            "setup shows actual connected service",
        )
        shot("phone-shizuku-setup")
        up()
        expectPage("user-guide")
        up()
        expectPage("settings")
        close()
        check(
            !session!!.prefs!!.getBoolean("statusExpanded", true),
            "menu round trips preserve collapsed status",
        )
    }

    @Throws(Exception::class)
    fun run(): String {
        val preferences = HashMap<String?, Any?>(session!!.prefs!!.getAll()!!)
        modernRotation = DeviceTestSupport.shell(runner, "wm help")!!.contains("  user-rotation ")
        val previousRotationMode =
            if (modernRotation)
                DeviceTestSupport.shell(runner, "wm user-rotation -d 0")!!.trim({ it <= ' ' })
            else null
        val originalRotation = session!!.rotation
        val userRotation =
            android.provider.Settings.System.getInt(
                phone!!.getContentResolver(),
                android.provider.Settings.System.USER_ROTATION,
                0,
            )
        val autoRotation =
            android.provider.Settings.System.getInt(
                phone!!.getContentResolver(),
                android.provider.Settings.System.ACCELEROMETER_ROTATION,
                0,
            )
        try {
            check(
                phone!!.getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                "phone controller requests portrait",
            )
            val rotation = session!!.rotation
            phoneRotation("lock 1")
            await(
                {
                    try {
                        return@await if (modernRotation)
                            DeviceTestSupport.shell(runner, "wm user-rotation -d 0")!!.trim({
                                it <= ' '
                            }) == "lock 1"
                        else
                            (android.provider.Settings.System.getInt(
                                phone!!.getContentResolver(),
                                android.provider.Settings.System.USER_ROTATION,
                                0,
                            ) == 1)
                    } catch (error: Exception) {
                        throw RuntimeException(error)
                    }
                },
                "landscape rotation requested on phone display only",
            )
            await(
                {
                    (phone!!.getResources()!!.getConfiguration()!!.orientation ==
                        android.content.res.Configuration.ORIENTATION_PORTRAIT)
                },
                "phone stays portrait when device rotates",
            )
            check(session!!.rotation == rotation, "phone rotation does not rotate monitor")
            phoneRotation("lock 0")
            main({ description(phone!!.getWindow()!!.getDecorView(), "Settings")!!.performClick() })
            inspect("settings")
            main({
                val version =
                    root().findViewWithTag<android.widget.TextView?>("settings-app-version")
                try {
                    val installed =
                        phone!!.getPackageManager()!!.getPackageInfo(phone!!.getPackageName()!!, 0)
                    check(
                        (version != null &&
                            version!!.isShown() &&
                            version!!.getText()!!.toString().contains(installed!!.versionName!!) &&
                            version!!
                                .getText()!!
                                .toString()
                                .contains(("Build " + installed!!.getLongVersionCode()))),
                        "phone Settings shows installed version and build immediately",
                    )
                } catch (error: android.content.pm.PackageManager.NameNotFoundException) {
                    throw AssertionError(error)
                }
            })
            main({
                val scroll = root().findViewWithTag<ScrollView?>("phone-menu-scroll")
                check(scroll != null, "phone Settings content scrolls vertically")
                scroll!!.fullScroll(View.FOCUS_DOWN)
            })
            Thread.sleep(150)
            main({
                val rect = Rect()
                check(
                    text(root(), "Reset monitor profile")!!.getGlobalVisibleRect(rect),
                    "last Settings action reachable by scrolling",
                )
                val header = root().findViewWithTag<View?>("phone-menu-close")
                check(
                    header!!.getGlobalVisibleRect(rect),
                    "Close remains visible after Settings scroll",
                )
            })
            shot("phone-settings")
            close()
            val test = ComponentName(runner!!.getTargetContext()!!, InputTestActivity::class.java!!)
            val testWindow =
                runner!!.addMonitor(InputTestActivity::class.java!!.getName(), null, false)
            try {
                main({ session!!.launch(test, 0) })
                await(
                    {
                        val ready = booleanArrayOf(false)
                        main({
                            val target = testWindow!!.getLastActivity()
                            ready[0] =
                                (!session!!.busy &&
                                    session!!.rotation == 0 &&
                                    session!!.hasDesktopApp() &&
                                    target != null &&
                                    !target!!.isDestroyed() &&
                                    (target!!.getDisplay()!!.getDisplayId() == session!!.id) &&
                                    target!!.getWindow()!!.getDecorView().isShown() &&
                                    (target!!.getWindow()!!.getDecorView().getWidth() > 0) &&
                                    (target!!.getWindow()!!.getDecorView().getHeight() > 0))
                        })
                        ready!![0]
                    },
                    "phone submenu native test window ready on owned display",
                )
            } finally {
                runner!!.removeMonitor(testWindow)
            }
            touchpadActions()
            open("Rotate")
            inspect("rotation")
            main({
                for (i in 0..3) {
                    val choice = root().findViewWithTag<RadioButton?>("phone-rotation-" + i)
                    card(choice!!, "phone rotation " + i)
                    check(
                        choice!!.isChecked() == (i == session!!.rotation),
                        "phone current rotation selected " + i,
                    )
                }
            })
            shot("phone-rotation")
            main({ root().findViewWithTag<View?>("phone-rotation-1")!!.performClick() })
            await(
                { !session!!.busy && session!!.rotation == 1 && phone!!.controllerMenu() == null },
                "phone rotation selection applies and closes",
            )
            open("Scroll")
            inspect("scroll")
            main({
                for (tag in
                    arrayOf<String?>(
                        "phone-swipe",
                        "phone-wheel",
                        "phone-speed-0",
                        "phone-speed-1",
                        "phone-speed-2",
                    )) card(root().findViewWithTag<View?>(tag)!!, tag)
                root().findViewWithTag<View?>("phone-swipe")!!.performClick()
                check(
                    (phone!!.controllerMenu() != null &&
                        root().findViewWithTag<View?>("phone-swipe") != null &&
                        root().findViewWithTag<View?>("phone-swipe")!!.isShown()),
                    "scroll mode applies while combined settings stay open",
                )
                root().findViewWithTag<View?>("phone-speed-0")!!.performClick()
                check(
                    (phone!!.controllerMenu() != null &&
                        root().findViewWithTag<View?>("phone-speed-0") != null &&
                        root().findViewWithTag<View?>("phone-speed-0")!!.isShown()),
                    "scroll speed applies while combined settings stay open",
                )
                check(
                    session!!.swipeScrolling() && session!!.scrollSpeed() == .2f,
                    "phone scrolling selections update app settings",
                )
            })
            shot("phone-scroll")
            close()
            open("Scroll")
            main({
                check(
                    ((root().findViewWithTag<View?>("phone-swipe") as RadioButton).isChecked() &&
                        (root().findViewWithTag<View?>("phone-speed-0") as RadioButton)
                            .isChecked()),
                    "phone scrolling selection persists on reopen",
                )
            })
            close()
            navigationFlow()
            setupGuideFlow()
            statusRecoveryFlow()
            open("Apps")
            inspect("apps")
            await(
                {
                    val ready = booleanArrayOf(false)
                    main({
                        val apps = root().findViewWithTag<ListView?>("phone-app-list")
                        ready[0] = apps != null && apps!!.getCount() > 3
                    })
                    ready!![0]
                },
                "async phone app catalog ready",
            )
            val editor = arrayOf<EditText?>(null)
            val count = intArrayOf(0)
            val beforeHeight = intArrayOf(0)
            main({
                editor[0] = root().findViewWithTag<EditText?>("phone-app-search")
                val list = root().findViewWithTag<ListView?>("phone-app-list")
                count[0] = list!!.getCount()
                beforeHeight[0] = root().getHeight()
                check(count!![0] > 3, "phone launcher contains installed apps")
                card(list!!.getChildAt(0)!!, "phone app entry")
                check(
                    (list!!.getChildAt(0) as TextView).getCompoundDrawables()[0] != null,
                    "phone app entry keeps app icon",
                )
                check(
                    (list!!.getChildAt(0) as TextView).getText() is android.text.Spanned,
                    "phone app caption shares Settings subtitle styling",
                )
                editor!![0]!!.setText("DexDroid input test")
            })
            Thread.sleep(150)
            main({
                check(
                    ((root().findViewWithTag<View?>("phone-app-list") as ListView).getCount() == 1),
                    "phone Apps search filters installed list",
                )
            })
            tap(editor!![0])
            await(
                {
                    (root().getRootWindowInsets() != null &&
                        root().getRootWindowInsets()!!.isVisible(WindowInsets.Type.ime()))
                },
                ("phone Apps uses Android keyboard; editorFocus=" +
                    editor!![0]!!.hasFocus() +
                    " windowFocus=" +
                    editor!![0]!!.hasWindowFocus()),
            )
            main({
                val rect = Rect()
                check(
                    root().findViewWithTag<View?>("phone-menu-close")!!.getGlobalVisibleRect(rect),
                    "phone Apps Close reachable with keyboard",
                )
                check(
                    root().findViewWithTag<View?>("app.dev.dexdroid")!!.getGlobalVisibleRect(rect),
                    "phone Apps result reachable with keyboard",
                )
            })
            shot("phone-apps-keyboard")
            DeviceTestSupport.shell(runner, "input -d 0 keyevent 4")
            await(
                {
                    (phone!!.controllerMenu() != null &&
                        root().findViewWithTag<View?>("phone-menu-apps") != null &&
                        !root().getRootWindowInsets()!!.isVisible(WindowInsets.Type.ime()))
                },
                "first phone Back hides IME and retains Apps",
            )
            main({
                val list = root().findViewWithTag<ListView?>("phone-app-list")
                list!!.performItemClick(list!!.getChildAt(0), 0, list!!.getItemIdAtPosition(0))
            })
            await(
                { phone!!.controllerMenu() == null && !session!!.busy },
                "phone Apps launches selected result",
            )
            open("Apps")
            main({
                (root().findViewWithTag<View?>("phone-app-search") as EditText).setText(
                    "DexDroid input test"
                )
            })
            Thread.sleep(100)
            main({
                val list = root().findViewWithTag<ListView?>("phone-app-list")
                check(
                    list!!
                        .getOnItemLongClickListener()!!
                        .onItemLongClick(
                            list,
                            list!!.getChildAt(0),
                            0,
                            list!!.getItemIdAtPosition(0),
                        ),
                    "phone app long press opens orientation",
                )
            })
            inspect("app-rotation")
            shot("phone-app-rotation")
            up()
            expectPage("apps")
            check(
                (root().findViewWithTag<View?>("phone-app-search") as EditText)
                    .getText()!!
                    .toString() == "DexDroid input test",
                "Apps query survives orientation Up",
            )
            await(
                {
                    (root().findViewWithTag<View?>("phone-app-list") as ListView).getChildCount() >
                        0
                },
                "restored Apps result available",
            )
            main({
                val list = root().findViewWithTag<ListView?>("phone-app-list")
                list!!
                    .getOnItemLongClickListener()!!
                    .onItemLongClick(list, list!!.getChildAt(0), 0, list!!.getItemIdAtPosition(0))
            })
            expectPage("app-rotation")
            val saved = session!!.prefs!!.getInt("app.dev.dexdroid", -1)
            clickText("Portrait 270\u00b0")
            await(
                { phone!!.controllerMenu() == null && !session!!.busy && session!!.rotation == 3 },
                "phone held app applies one-time orientation",
            )
            check(
                session!!.prefs!!.getInt("app.dev.dexdroid", -1) == saved,
                "phone one-time orientation keeps saved rotation",
            )
            main({ description(phone!!.getWindow()!!.getDecorView(), "Settings")!!.performClick() })
            clickText("Pointer speed")
            inspect("pointer-speed")
            main({
                card(root().findViewWithTag<View?>("phone-pointer-1")!!, "phone pointer speed")
            })
            close()
            main({ description(phone!!.getWindow()!!.getDecorView(), "Settings")!!.performClick() })
            clickText("Resolution and DPI")
            inspect("display")
            close()
            main({ description(phone!!.getWindow()!!.getDecorView(), "Settings")!!.performClick() })
            clickText("Diagnostic log")
            inspect("diagnostics")
            close()
            main({
                val controller =
                    phone!!
                        .getWindow()!!
                        .getDecorView()
                        .findViewWithTag<ScrollView?>("phone-controller-scroll")
                check(
                    controller != null && controller!!.isFillViewport(),
                    "main phone controller supports vertical overflow scrolling",
                )
                val pad = controller!!.findViewWithTag<View?>("touchpad")
                check(
                    (pad!!.getHeight() >=
                        230 * pad!!.getResources()!!.getDisplayMetrics()!!.density),
                    "main touchpad retains usable height",
                )
            })
            touchpadDrag()
            return results.toString()
        } catch (error: Throwable) {
            try {
                retainOwnerFailure()
            } catch (capture: Throwable) {
                error.addSuppressed(capture)
            }
            try {
                shot("phone-menu-failure")
            } catch (capture: Throwable) {
                error!!.addSuppressed(capture!!)
            }

            throw RuntimeException(results.toString() + "FAIL phone menu acceptance", error)
        } finally {
            phoneRotation(
                if (modernRotation) previousRotationMode
                else if (autoRotation == 1) "free" else "lock " + userRotation
            )
            main({
                if (phone!!.controllerMenu() != null) phone!!.controllerMenu()!!.dismiss()
                val edit = session!!.prefs!!.edit()!!.clear()
                for (e in preferences.entries) {
                    val v = e!!.value
                    val k = e!!.key
                    if (v is Boolean) edit!!.putBoolean(k, (v as Boolean?)!!)
                    else if (v is Int) edit!!.putInt(k, (v as Int?)!!)
                    else if (v is Float) edit!!.putFloat(k, (v as Float?)!!)
                    else if (v is String) edit!!.putString(k, v as String?)
                    else if (v is Long) edit!!.putLong(k, (v as Long?)!!)
                }
                edit!!.commit()
                if (!session!!.busy) session!!.rotate(originalRotation, false)
            })
        }
    }
}
