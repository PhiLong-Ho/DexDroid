package dev.dexdroid

import android.app.*
import android.content.*
import android.graphics.*
import android.hardware.display.DisplayManager
import android.os.*
import android.view.*
import dev.dexdroid.fixture.LaunchParentActivity
import dev.dexdroid.fixture.SplitProbeFixture
import java.io.*
import java.util.concurrent.*
import java.util.function.BooleanSupplier

/** Product session, real shell IPC, native cross-UID fixture and actual display input. */
class SplitAcceptance
@JvmOverloads
constructor(private val runner: Instrumentation?, private val navigationOnly: Boolean = false) {
    private val results: StringBuilder = StringBuilder()
    private var session: SessionService? = null
    private var phone: MainActivity? = null
    @Volatile private var state: Bundle? = null
    @Volatile private var child: Bundle? = null
    @Volatile private var protection: Bundle? = null
    @Volatile private var keyFixtureIdentity: String? = null
    @Volatile private var keyIdentityChanged = false
    private var keyReportSerial = 0L
    private val keyReportChanges = java.util.ArrayDeque<String>()

    private fun fixtureIdentity(report: Bundle): String =
        "${report.getString("instance")}:${report.getInt("task")}:${report.getInt("display")}:${report.getInt("uid")}"

    private fun keyObservation(label: String) {
        val report = state ?: return
        results
            .append("KEY_DIAGNOSTIC ")
            .append(label)
            .append(" identity=")
            .append(fixtureIdentity(report))
            .append(" serial=")
            .append(report.getLong("reportSerial"))
            .append(" focused=")
            .append(report.getBoolean("currentWindowFocus"))
            .append(" downs=")
            .append(report.getInt("keyDowns"))
            .append(" ups=")
            .append(report.getInt("keyUps"))
            .append(" callbacks=")
            .append(report.getStringArrayList("callbackHistory"))
            .append('\n')
        synchronized(keyReportChanges) {
            for (change in keyReportChanges) results
                .append("KEY_REPORT_CHANGE ")
                .append(change)
                .append('\n')
        }
    }

    private var frameEvidence: String? = "not sampled"
    private var down: Long = 0
    // Test setup admission only; ready()/launch() invalidate it before new transitions.
    private var settledFixtureWorkspace: SplitWorkspace? = null
    private var settledFixtureGeometry: String? = null
    private var settledFixtureTask: Int = -1

    private fun check(value: Boolean, label: String?) {
        if (!value) throw AssertionError(label)
        results.append("PASS ").append(label).append('\n')
    }

    @Throws(Exception::class)
    private fun await(condition: BooleanSupplier, label: String?) {
        val end = SystemClock.uptimeMillis() + 15000
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(40)
        check(condition.getAsBoolean(), label)
    }

    @Throws(Exception::class)
    private fun ready(label: String?) {
        settledFixtureGeometry = null
        settledFixtureTask = -1
        await({ session!!.id >= 0 && !session!!.busy }, label)
        if (session!!.split != null && session!!.split!!.displayId(1) >= 0) {
            for (pane in 0..1) {
                val selected = pane
                await(
                    {
                        val display =
                            (runner!!
                                    .getTargetContext()!!
                                    .getSystemService(Context.DISPLAY_SERVICE!!) as DisplayManager)
                                .getDisplay(session!!.split!!.displayId(selected))
                        if (display == null) return@await false
                        val metrics = android.util.DisplayMetrics()
                        display!!.getRealMetrics(metrics)
                        val expected =
                            session!!
                                .split!!
                                .dimensions(selected, session!!.width(), session!!.height(), false)
                        (metrics.widthPixels == expected!!.width &&
                            metrics.heightPixels == expected!!.height &&
                            metrics.densityDpi == session!!.dpi)
                    },
                    "actual pane " + (pane + 1) + " dimensions and DPI: " + label,
                )
            }
        }
        runner!!.getUiAutomation()!!.waitForIdle(250, 5000)
    }

    @Throws(Exception::class)
    private fun launch(app: ComponentName) {
        settledFixtureGeometry = null
        settledFixtureTask = -1
        val target =
            if (session!!.splitEnabled()) session!!.split!!.displayId(session!!.split!!.selected())
            else session!!.id
        runner!!.runOnMainSync({
            session!!.status = "Acceptance pending"
            session!!.launch(app, if (session!!.splitEnabled()) null else 0)
        })
        await(
            {
                if (session!!.busy) return@await false
                try {
                    for (root in
                        DisplayTasks.parse(
                            DeviceTestSupport.shell(runner, "am stack list"),
                            app.flattenToString(),
                        )) if (root!!.display == target && root!!.matches && root!!.visible)
                        return@await true
                    return@await false
                } catch (failure: Exception) {
                    throw IllegalStateException("Cannot verify native app placement", failure)
                }
            },
            "verified pane launch " + app.flattenToShortString(),
        )
        runner!!.getUiAutomation()!!.waitForIdle(250, 5000)
    }

    private fun physical(x: Float, y: Float): FloatArray {
        DeviceTestSupport.requireZeroMonitorOrigin(runner, session)
        val view = session!!.monitorWindow()!!.getDecorView()
        return Geometry.map(
            x / session!!.width(),
            y / session!!.height(),
            session!!.rotation,
            view.getWidth().toFloat(),
            view.getHeight().toFloat(),
            session!!.width(),
            session!!.height(),
        )
    }

    @Throws(Exception::class)
    private fun pointer(action: Int, p: FloatArray?, source: Int, wheel: Float) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_SCROLL) down = now
        val property = MotionEvent.PointerProperties()
        property.id = 7
        property.toolType =
            if (source == InputDevice.SOURCE_MOUSE) MotionEvent.TOOL_TYPE_MOUSE
            else MotionEvent.TOOL_TYPE_FINGER
        val coord = MotionEvent.PointerCoords()
        coord.x = p!![0]
        coord.y = p!![1]
        coord.pressure = 1f
        coord.setAxisValue(MotionEvent.AXIS_VSCROLL, wheel)
        val event =
            MotionEvent.obtain(
                down,
                now,
                action,
                1,
                arrayOf<MotionEvent.PointerProperties?>(property),
                arrayOf<MotionEvent.PointerCoords?>(coord),
                0,
                if (source == InputDevice.SOURCE_MOUSE && action != MotionEvent.ACTION_UP)
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
            check(
                DeviceTestSupport.injectOnDisplay(event, session!!.external!!.getDisplayId()),
                "physical host injection " + action + "/" + source,
            )
        } finally {
            event!!.recycle()
        }
    }

    @Throws(Exception::class)
    private fun tap(p: FloatArray?, source: Int) {
        pointer(MotionEvent.ACTION_DOWN, p, source, 0f)
        Thread.sleep(80)
        pointer(MotionEvent.ACTION_UP, p, source, 0f)
    }

    private fun box(pane: Int): SplitLayout.Box {
        return session!!
            .split!!
            .state()
            .pane(pane, session!!.width(), session!!.height(), session!!.split!!.gap())
    }

    @Throws(Exception::class)
    private fun nativeReady() {
        await(
            {
                (state!!.getInt("width") == box(0).width &&
                    state!!.getInt("height") == box(0).height &&
                    state!!.getInt("layoutWidth") == box(0).width &&
                    state!!.getInt("layoutBottom") == box(0).height)
            },
            "native child layout matches resized window",
        )
        val workspace = session!!.split
        val display = workspace!!.displayId(0)
        val uid = state!!.getInt("uid")
        val dimensions = workspace!!.dimensions(0, session!!.width(), session!!.height(), false)
        val geometry =
            (display.toString() +
                ":" +
                uid +
                ":" +
                dimensions!!.width +
                ":" +
                dimensions!!.height +
                ":" +
                session!!.rotation +
                ":" +
                session!!.dpi)
        if (
            (settledFixtureWorkspace == workspace &&
                settledFixtureTask >= 0 &&
                geometry == settledFixtureGeometry)
        )
            return
        val component =
            ComponentName("dev.dexdroid.test", "dev.dexdroid.fixture.SplitProbeFixture\$Left")
        val task = DeviceTestSupport.nativeInputTask(runner, display, component)
        check(task >= 0, "native fixture captures one current task before settled contact")
        await(
            {
                try {
                    return@await (session!!.split == workspace &&
                        !session!!.busy &&
                        workspace!!.displayId(0) == display &&
                        state!!.getInt("display") == display &&
                        state!!.getInt("uid") == uid &&
                        DeviceTestSupport.settledInputWindow(
                            runner,
                            display,
                            task,
                            component,
                            uid,
                            dimensions!!.width,
                            dimensions!!.height,
                        ) &&
                        session!!.split == workspace &&
                        !session!!.busy &&
                        workspace!!.displayId(0) == display &&
                        (workspace!!
                            .dimensions(0, session!!.width(), session!!.height(), false)!!
                            .width == dimensions!!.width) &&
                        (workspace!!
                            .dimensions(0, session!!.width(), session!!.height(), false)!!
                            .height == dimensions!!.height))
                } catch (error: Exception) {
                    throw IllegalStateException("Cannot verify settled native fixture input", error)
                }
            },
            "native fixture has current resized unobscured touchable window",
        )
        await(
            BooleanSupplier({ this.firstFrameReady() }),
            "current native fixture frame settled before contact",
        )
        settledFixtureWorkspace = workspace
        settledFixtureTask = task
        settledFixtureGeometry = geometry
    }

    @Throws(Exception::class)
    private fun nativeClick(source: Int) {
        nativeReady()
        val clicks = state!!.getInt("clicks")
        val box = box(0)
        tap(
            physical(box.x + state!!.getFloat("buttonX"), box.y + state!!.getFloat("buttonY")),
            source,
        )
        await(
            { state!!.getInt("clicks") == clicks + 1 },
            "native button receives one click " + source,
        )
        check(session!!.split!!.selected() == 0, "physical click selects first app")
    }

    @Throws(Exception::class)
    private fun inputTap(pane: Int = 1) {
        await({ inputFrameReady(pane) }, "native app frame ready before first contact")
        val box = box(pane)
        val x = box.x + box.width * .5f
        val y = box.y + box.height * .55f
        tap(physical(x, y), InputDevice.SOURCE_TOUCHSCREEN)
        val expected = down
        await(
            { InputTestActivity.lastUpDownTime == expected },
            "app in pane " + pane + " receives physical tap",
        )
        check(
            (Math.abs(InputTestActivity.lastUpX - box.width * .5f) < 2 &&
                Math.abs(InputTestActivity.lastUpY - box.height * .55f) < 2),
            "pane-local raw coordinates aligned",
        )
        check(!session!!.pointerVisible, "physical pane input hides pointer")
        await({ inputFrameReady(pane) }, "native app frame retains circular target after resize")
    }

    private fun inputFrameReady(pane: Int): Boolean {
        val ready = booleanArrayOf(false)
        runner!!.runOnMainSync({
            val view =
                session!!
                    .monitorInputView()!!
                    .findViewWithTag<TextureView?>("split-texture-" + pane)
            val image = if (view == null) null else view!!.getBitmap()
            if (image == null) return@runOnMainSync
            try {
                // The pinch scenario legitimately enlarges the circles. Check
                // their position/aspect, using the central unclipped target and
                // crossing its one-pixel yellow scroll ruler vertically.
                val x = Math.round(image!!.getWidth() * .5f)
                val y = Math.round(image!!.getHeight() * .5f)
                val row = y + 4
                frameEvidence =
                    ("bitmap=" +
                        image!!.getWidth() +
                        "x" +
                        image!!.getHeight() +
                        " center=" +
                        Integer.toHexString(image!!.getPixel(x, row)))
                if (!targetPixel(image, x, row)) return@runOnMainSync
                var left = x
                var right = x
                var top = y
                var bottom = y
                while (targetPixel(image, left - 1, row)) left--
                while (targetPixel(image, right + 1, row)) right++
                while (verticalTargetPixel(image, x, top - 1)) top--
                while (verticalTargetPixel(image, x, bottom + 1)) bottom++
                val width = right - left + 1
                val height = bottom - top + 1
                frameEvidence += " target=" + width + "x" + height
                ready[0] =
                    (width >= 64 &&
                        width <= 285 &&
                        height >= 64 &&
                        height <= 285 &&
                        Math.abs(width - height) <= 4)
            } finally {
                image!!.recycle()
            }
        })
        return ready!![0]
    }

    private fun firstFrameReady(): Boolean {
        val ready = booleanArrayOf(false)
        runner!!.runOnMainSync({
            val view =
                session!!.monitorInputView()!!.findViewWithTag<TextureView?>("split-texture-0")
            val image = view!!.getBitmap()
            if (image == null) return@runOnMainSync
            try {
                val w = image!!.getWidth()
                val h = image!!.getHeight()
                val expected =
                    session!!.split!!.dimensions(0, session!!.width(), session!!.height(), false)
                ready[0] =
                    (!session!!.busy &&
                        w == expected!!.width &&
                        h == expected!!.height &&
                        view!!.getWidth() == expected!!.width &&
                        view!!.getHeight() == expected!!.height &&
                        state!!.getInt("display") == session!!.split!!.displayId(0) &&
                        state!!.getInt("width") == expected!!.width &&
                        state!!.getInt("height") == expected!!.height &&
                        image!!.getPixel(8, h - 8) == Color.rgb(18, 18, 18) &&
                        image!!.getPixel(w - 8, h - 8) == Color.rgb(18, 18, 18) &&
                        (image!!.getPixel(w / 4, h * 9 / 10) == Color.rgb(40, 40, 40)))
            } finally {
                image!!.recycle()
            }
        })
        return ready!![0]
    }

    private fun targetPixel(image: Bitmap?, x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x >= image!!.getWidth() || y >= image!!.getHeight()) return false
        val color = image!!.getPixel(x, y)
        return (Math.abs(Color.red(color) - 94) <= 4 &&
            Math.abs(Color.green(color) - 234) <= 4 &&
            Math.abs(Color.blue(color) - 212) <= 4)
    }

    private fun verticalTargetPixel(image: Bitmap?, x: Int, y: Int): Boolean {
        if (targetPixel(image, x, y)) return true
        if (x < 0 || y < 0 || x >= image!!.getWidth() || y >= image!!.getHeight()) return false
        val color = image!!.getPixel(x, y)
        // Include the antialiased blend between teal fill and yellow ruler.
        return Color.red(color) >= 90 && Color.green(color) >= 230 && Color.blue(color) <= 216
    }

    private fun phoneDialog(): Dialog? {
        try {
            val field = MainActivity::class.java!!.getDeclaredField("settingsDialog")
            field.setAccessible(true)
            return field.get(phone) as Dialog?
        } catch (error: Exception) {
            throw IllegalStateException(error)
        }
    }

    @Throws(Exception::class)
    private fun monitorLayout(rotation: Int) {
        val start = physical(session!!.width() * .5f, 4f)
        val end = physical(session!!.width() * .5f, session!!.height() * .3f)
        DeviceTestSupport.usableMonitorSwipe(runner, session, start, end)
        pointer(0, start, InputDevice.SOURCE_TOUCHSCREEN, 0f)
        Thread.sleep(35)
        pointer(2, end, InputDevice.SOURCE_TOUCHSCREEN, 0f)
        pointer(1, end, InputDevice.SOURCE_TOUCHSCREEN, 0f)
        await(
            { (session!!.monitorShellPage() == "controls" && !session!!.monitorShellAnimating()) },
            "edge opens controls in split " + rotation,
        )
        runner!!.runOnMainSync({
            session!!.monitorShellView()!!.findViewWithTag<View?>("monitor-layout")!!.performClick()
        })
        await(
            { (session!!.monitorShellPage() == "layout" && !session!!.monitorShellAnimating()) },
            "monitor Layout opens " + rotation,
        )
        runner!!.runOnMainSync({
            check(
                MenuTextAssertions.check(session!!.monitorShellView()!!) > 0,
                "monitor Layout text and native icons valid " + rotation,
            )
        })
        capture("monitor-layout-r" + rotation, true)
        runner!!.runOnMainSync({ layoutTargets(session!!.monitorShellView()!!, true) })
        controllerMenuTap("layout-target-0", "home")
        check(
            session!!.appSelectionBox() == null,
            "monitor launcher opens without persistent highlight",
        )
        val action = launchButton(true, 0)
        buttonContact(action, MotionEvent.ACTION_DOWN)
        checkDestination(
            session!!.split!!.state().paneAtPosition(0),
            "monitor spatial action " + rotation,
        )
        capture("monitor-destination-r" + rotation, true)
        capture("monitor-combined-r" + rotation, true)
        buttonContact(action, MotionEvent.ACTION_CANCEL)
        check(session!!.appSelectionBox() == null, "monitor action cancel clears highlight")
        runner!!.runOnMainSync({
            session!!.monitorShellView()!!.findViewWithTag<View?>("monitor-up")!!.performClick()
        })
        await(
            { (session!!.monitorShellPage() == "layout" && !session!!.monitorShellAnimating()) },
            "monitor picker header returns to Layout " + rotation,
        )
        controllerMenuTap("layout-target-0", "home")
        if (rotation == 0) {
            val edgeStart = physical(session!!.width() * .5f, 4f)
            val edgeEnd = physical(session!!.width() * .5f, session!!.height() * .3f)
            DeviceTestSupport.usableMonitorSwipe(runner, session, edgeStart, edgeEnd)
            pointer(0, edgeStart, InputDevice.SOURCE_TOUCHSCREEN, 0f)
            pointer(2, edgeEnd, InputDevice.SOURCE_TOUCHSCREEN, 0f)
            pointer(1, edgeEnd, InputDevice.SOURCE_TOUCHSCREEN, 0f)
            await(
                {
                    (session!!.monitorShellPage() == "controls" &&
                        !session!!.monitorShellAnimating())
                },
                "controls replace active app picker",
            )
            check(
                session!!.appSelectionBox() == null,
                "replacement controls clear picker highlight",
            )
            runner!!.runOnMainSync({ session!!.key(KeyEvent.KEYCODE_BACK) })
            await(
                {
                    (session!!.monitorShellPage() == "closed" && !session!!.monitorShellAnimating())
                },
                "Back from fresh Controls root cannot reopen cancelled Apps",
            )
        }
        if (session!!.monitorShellPage() == "home") {
            tap(
                physical(session!!.width() * .04f, session!!.height() * .5f),
                InputDevice.SOURCE_TOUCHSCREEN,
            )
            await(
                {
                    (session!!.monitorShellPage() == "closed" && !session!!.monitorShellAnimating())
                },
                "targeted picker outside closes complete flow",
            )
        }
        if (session!!.monitorShellPage() != "closed")
            tap(
                physical(session!!.width() * .04f, session!!.height() * .5f),
                InputDevice.SOURCE_TOUCHSCREEN,
            )
        await(
            { (session!!.monitorShellPage() == "closed" && !session!!.monitorShellAnimating()) },
            "whole modal flow dismisses outside without app click " + rotation,
        )
        check(session!!.appSelectionBox() == null, "monitor cancel clears destination " + rotation)
    }

    private fun layoutTargets(root: View, enabled: Boolean) {
        val targets = root.findViewWithTag<android.widget.LinearLayout?>("layout-targets")
        val first = root.findViewWithTag<android.widget.Button?>("layout-target-0")
        val second = root.findViewWithTag<android.widget.Button?>("layout-target-1")
        val layout = if (enabled) session!!.split!!.state() else SplitLayout()
        check(
            first!!.isShown() && second!!.isShown() == enabled,
            "Layout has exactly one or two app targets",
        )
        check(
            first!!.getText()!!.toString() == layout.openLabel(0),
            "first target names spatial destination",
        )
        check(
            (root.findViewWithTag<View?>("layout-pane-0") == null &&
                root.findViewWithTag<View?>("layout-apps") == null),
            "Layout removes separate select-pane and open-app steps",
        )
        if (enabled) {
            check(
                second!!.getText()!!.toString() == layout.openLabel(1),
                "second target names spatial destination",
            )
            check(
                (targets!!.getOrientation() ==
                    (if (layout.vertical) android.widget.LinearLayout.VERTICAL
                    else android.widget.LinearLayout.HORIZONTAL)),
                "app target arrangement follows split direction",
            )
            check(
                if (layout.vertical) first!!.getTop() < second!!.getTop()
                else first!!.getLeft() < second!!.getLeft(),
                "app targets match physical order",
            )
        } else
            check(
                !root.findViewWithTag<View?>("layout-direction")!!.isShown(),
                "single view hides split-only settings",
            )
    }

    @Throws(Exception::class)
    private fun checkDestination(pane: Int, label: String?) {
        await(
            { session!!.appSelectionBox() != null },
            "native pressed feedback visible: " + label!!,
        )
        val actual = session!!.appSelectionBox()
        val expected = box(pane)
        check(
            (actual != null &&
                actual!!.x == expected.x &&
                actual!!.y == expected.y &&
                actual!!.width == expected.width &&
                actual!!.height == expected.height),
            "highlight matches captured destination: " + label!!,
        )
    }

    @Throws(Exception::class)
    private fun phoneTarget(position: Int) {
        await(
            {
                val ready = booleanArrayOf(false)
                runner!!.runOnMainSync({
                    val dialog = phoneDialog()
                    if (dialog == null || !dialog!!.isShowing()) return@runOnMainSync
                    val decor = dialog!!.getWindow()!!.getDecorView()
                    val target = decor.findViewWithTag<View?>("layout-target-" + position)
                    ready[0] =
                        (decor.isShown() &&
                            decor.hasWindowFocus() &&
                            !decor.isLayoutRequested() &&
                            target != null &&
                            target!!.isShown() &&
                            target!!.isEnabled() &&
                            target!!.getWidth() > 0 &&
                            target!!.getHeight() > 0 &&
                            !target!!.isLayoutRequested())
                })
                ready!![0]
            },
            "phone launch target has a ready native input window",
        )
        val bounds = Rect()
        runner!!.runOnMainSync({
            val target =
                phoneDialog()!!
                    .getWindow()!!
                    .getDecorView()
                    .findViewWithTag<View?>("layout-target-" + position)
            check(
                (target != null &&
                    target!!.isShown() &&
                    target!!.isEnabled() &&
                    target!!.getGlobalVisibleRect(bounds)),
                "phone launch target visible and enabled",
            )
            val origin = IntArray(2)
            target!!.getRootView()!!.getLocationOnScreen(origin)
            bounds.offset(origin[0], origin[1])
            check(
                bounds.width() > 0 && bounds.height() > 0,
                "phone target has nonempty clipped screen bounds",
            )
            results
                .append("PHONE_TARGET pane=")
                .append(position)
                .append(" root_origin=")
                .append(origin[0])
                .append(',')
                .append(origin[1])
                .append(" bounds=")
                .append(bounds.toShortString())
                .append('\n')
        })
        runner!!.getUiAutomation()!!.waitForIdle(250, 5000)
        DeviceTestSupport.shell(
            runner,
            "input -d 0 tap " + bounds.centerX() + " " + bounds.centerY(),
        )
        await(
            {
                (phoneDialog() != null &&
                    (phoneDialog()!!
                        .getWindow()!!
                        .getDecorView()
                        .findViewWithTag<View?>("phone-menu-apps") != null))
            },
            "one phone target tap opens app picker",
        )
    }

    @Throws(Exception::class)
    private fun launchButton(monitor: Boolean, position: Int): View? {
        val owner = session
        var capturedDialog: Dialog? = null
        var root: View? = null
        var search: android.widget.EditText? = null
        var queried = false
        val button = arrayOf<View?>(null)
        await(
            {
                runner!!.runOnMainSync({
                    if (session !== owner) throw AssertionError("Picker session replaced")
                    val dialog = if (monitor) null else phoneDialog()
                    val currentRoot =
                        if (monitor) session!!.monitorShellView() else dialog?.window?.decorView
                    val currentSearch =
                        currentRoot?.findViewWithTag<android.widget.EditText?>(
                            if (monitor) "monitor-app-search" else "phone-app-search"
                        )
                    val ready =
                        currentRoot != null &&
                            currentRoot.isAttachedToWindow &&
                            currentSearch != null &&
                            currentSearch.isShown &&
                            (if (monitor)
                                session!!.monitorShellPage() == "home" &&
                                    !session!!.monitorShellAnimating()
                            else
                                dialog?.isShowing == true &&
                                    currentRoot.findViewWithTag<View?>("phone-menu-apps") != null)
                    if (!queried && ready) {
                        capturedDialog = dialog
                        root = currentRoot
                        search = currentSearch
                        search!!.setText("DexDroid input test")
                        queried = true
                    }
                    if (queried) {
                        if (
                            !ready ||
                                root !== currentRoot ||
                                search !== currentSearch ||
                                capturedDialog !== dialog
                        )
                            throw AssertionError("Captured picker or search replaced")
                        val candidate =
                            root!!.findViewWithTag<View?>("launch.dev.dexdroid." + position)
                        button[0] =
                            if (
                                (candidate != null &&
                                    candidate!!.isShown() &&
                                    candidate!!.isEnabled &&
                                    candidate!!.getWidth() > 0 &&
                                    candidate!!.getHeight() > 0)
                            )
                                candidate
                            else null
                    }
                })
                button!![0] != null
            },
            "spatial launch button available " + position,
        )
        return button!![0]
    }

    private fun buttonContact(button: View?, action: Int) {
        runner!!.runOnMainSync({
            val now = SystemClock.uptimeMillis()
            val event =
                MotionEvent.obtain(
                    now,
                    now,
                    action,
                    button!!.getWidth() / 2f,
                    button!!.getHeight() / 2f,
                    0,
                )
            try {
                button!!.dispatchTouchEvent(event)
            } finally {
                event!!.recycle()
            }
        })
    }

    @Throws(Exception::class)
    private fun launchSecondFromLayout() {
        restorePhoneController("touchpad baseline")
        val before = Rect()
        await(
            {
                val valid = booleanArrayOf(false)
                runner!!.runOnMainSync {
                    val decor = phone!!.window.decorView
                    val content = decor.findViewById<ViewGroup>(android.R.id.content)
                    val controller = content.getChildAt(0)
                    val touchpad = decor.findViewWithTag<View>("touchpad")
                    valid[0] =
                        decor.isLaidOut &&
                            !decor.isLayoutRequested &&
                            controller != null &&
                            controller.isLaidOut &&
                            !controller.isLayoutRequested &&
                            touchpad.isShown &&
                            touchpad.isLaidOut &&
                            !touchpad.isLayoutRequested &&
                            touchpad.getGlobalVisibleRect(before) &&
                            !before.isEmpty
                }
                valid[0]
            },
            "controller touchpad baseline has completed visible layout",
        )
        menu("openApps")
        check(
            phoneDialog()!!
                .getWindow()!!
                .getDecorView()
                .findViewWithTag<View?>("phone-menu-apps") != null,
            "main Apps directly opens combined launcher in split",
        )
        check(session!!.appSelectionBox() == null, "open launcher has no persistent highlight")
        val right = launchButton(false, 1)
        buttonContact(right, MotionEvent.ACTION_DOWN)
        await({ session!!.appSelectionBox() != null }, "pressed action displays destination")
        checkDestination(1, "pressed phone right action")
        capture("phone-combined-launcher", false)
        capture("monitor-phone-destination", true)
        buttonContact(right, MotionEvent.ACTION_CANCEL)
        await(
            { session!!.appSelectionBox() == null },
            "cancelled action clears destination without launch",
        )
        runner!!.runOnMainSync({
            val replacement = session!!.beginAppSelection(0)
            right!!.performClick()
            check(
                session!!.currentAppSelection(replacement),
                "stale row action cannot replace newer picker ownership",
            )
            session!!.finishAppSelection(replacement)
        })
        closePhoneMenu()
        menu("layoutSettings")
        phoneTarget(1)
        runner!!.runOnMainSync({
            phoneDialog()!!
                .getWindow()!!
                .getDecorView()
                .findViewWithTag<View?>("phone-menu-up")!!
                .performClick()
        })
        await(
            {
                (phoneDialog() != null &&
                    (phoneDialog()!!
                        .getWindow()!!
                        .getDecorView()
                        .findViewWithTag<View?>("phone-menu-layout") != null))
            },
            "picker header returns to Layout",
        )
        phoneTarget(1)
        DeviceTestSupport.shell(runner, "input -d 0 keyevent 4")
        await(
            {
                (phoneDialog() != null &&
                    (phoneDialog()!!
                        .getWindow()!!
                        .getDecorView()
                        .findViewWithTag<View?>("phone-menu-layout") != null))
            },
            "picker system Back returns to Layout",
        )
        closePhoneMenu()
        menu("openApps")
        val target = launchButton(false, 1)
        runner!!.runOnMainSync(Runnable({ target!!.performClick() }))
        val input = ComponentName(runner!!.getTargetContext()!!, InputTestActivity::class.java!!)
        await(
            {
                (!session!!.busy &&
                    session!!.split!!.selectedComponent() != null &&
                    session!!.split!!.selectedComponent() == input)
            },
            "spatial row action launches chosen app",
        )
        check(
            session!!.split!!.selected() == 1 && session!!.appSelectionBox() == null,
            "row launches right and clears destination",
        )
        await({ phoneDialog() == null }, "successful launch dismisses picker")
        val after = Rect()
        val afterVisible = booleanArrayOf(false)
        runner!!.runOnMainSync {
            val touchpad = phone!!.window.decorView.findViewWithTag<View>("touchpad")
            afterVisible[0] =
                touchpad.isShown && touchpad.getGlobalVisibleRect(after) && !after.isEmpty
        }
        results
            .append("STATE controller touchpad before=")
            .append(before)
            .append(" after=")
            .append(after)
            .append('\n')
        check(afterVisible[0], "dismissed picker leaves a visible nonempty controller touchpad")
        check(before == after, "combined launcher preserves exact controller touchpad bounds")
        inputTap()
        menu("openApps")
        val owner = phoneDialog()!!.getWindow()!!.getDecorView()
        val search = owner.findViewWithTag<android.widget.EditText?>("phone-app-search")
        val staleModeAction = launchButton(false, 1)
        runner!!.runOnMainSync({
            search!!.setText("DexDroid input test")
            owner.findViewWithTag<View?>("launcher-mode-2")!!.performClick()
        })
        ready("launcher changes to up-down in place")
        await(
            {
                (owner.findViewWithTag<View?>("launch.dev.dexdroid.0") is android.widget.Button &&
                    (owner.findViewWithTag<View?>("launch.dev.dexdroid.0") as android.widget.Button)
                        .getText()!!
                        .toString() == "Up")
            },
            "vertical launcher updates spatial labels without replacing search",
        )
        check(
            (search!!.getText()!!.toString() == "DexDroid input test" &&
                owner === phoneDialog()!!.getWindow()!!.getDecorView()),
            "mode change retains dialog and search query",
        )
        runner!!.runOnMainSync(Runnable({ staleModeAction!!.performClick() }))
        check(
            owner === phoneDialog()!!.getWindow()!!.getDecorView() && !session!!.busy,
            "retired row callback cannot launch after in-place mode change",
        )
        capture("phone-combined-up-down", false)
        closePhoneMenu()
        runner!!.runOnMainSync({ session!!.split!!.swap() })
        ready("swapped launcher layout")
        menu("openApps")
        val up = launchButton(false, 0)
        buttonContact(up, MotionEvent.ACTION_DOWN)
        checkDestination(1, "Up resolves swapped spatial pane")
        buttonContact(up, MotionEvent.ACTION_CANCEL)
        closePhoneMenu()
        runner!!.runOnMainSync({ session!!.split!!.swap() })
        ready("launcher order restored")
        runner!!.runOnMainSync({ session!!.split!!.direction(false) })
        ready("launcher direction restored")
    }

    @Throws(Exception::class)
    private fun pendingModeOwnership() {
        menu("openApps")
        val root = phoneDialog()!!.getWindow()!!.getDecorView()
        runner!!.runOnMainSync({
            val mode = if (session!!.split!!.state().vertical) 1 else 2
            root.findViewWithTag<View?>("launcher-mode-" + mode)!!.performClick()
            check(session!!.busy, "phone mode change is pending before Stop")
            session!!.pauseDesktop()
        })
        await({ session!!.id < 0 && !session!!.busy }, "pending mode stopped")
        val list = root.findViewWithTag<android.widget.ListView?>("phone-app-list")
        await({ list!!.getAdapter()!!.getCount() > 0 }, "retained stopped picker has app rows")
        runner!!.runOnMainSync({
            val row = list!!.getAdapter()!!.getView(0, null, list)
            list!!.performItemClick(row, 0, list!!.getAdapter()!!.getItemId(0))
            check(
                session!!.id < 0 && !session!!.busy,
                "stopped pending launcher cannot restart desktop",
            )
            check(phoneDialog() != null, "obsolete row does not dismiss retained picker")
            val replacement = session!!.beginAppSelection(0)
            list!!.performItemClick(row, 0, list!!.getAdapter()!!.getItemId(0))
            check(
                session!!.currentAppSelection(replacement),
                "stopped pending launcher cannot adopt replacement picker",
            )
            session!!.finishAppSelection(replacement)
        })
        closePhoneMenu()
        runner!!.runOnMainSync(Runnable(session!!::ensureDesktop))
        ready("desktop restarts after pending mode cancellation")
        await({ session!!.splitEnabled() }, "confirmed split restored after cancelled mode")

        val pending = arrayOf<LauncherControls?>(null)
        runner!!.runOnMainSync({
            pending[0] = LauncherControls(session!!, phone, 1)
            val host = android.widget.LinearLayout(phone)
            pending!![0]!!.addModes(host)
            val mode = if (session!!.split!!.state().vertical) 1 else 2
            host.findViewWithTag<View?>("launcher-mode-" + mode)!!.performClick()
            check(session!!.busy, "detached owner mode operation is pending")
        })
        ready("mode completes before retired owner refresh")
        runner!!.runOnMainSync({
            val replacement = session!!.beginAppSelection(0)
            session!!.finishAppSelection(replacement)
            pending!![0]!!.refresh()
            check(
                !pending!![0]!!.current(),
                "completed mode cannot revive owner after newer picker closes",
            )
            check(
                !pending!![0]!!.launch(
                    ComponentName(phone!!, InputTestActivity::class.java!!),
                    null,
                    1,
                ),
                "retired mode owner cannot launch into live replacement session",
            )
            val current = session!!.beginAppSelection(0)
            pending!![0]!!.refresh()
            pending!![0]!!.close()
            check(
                session!!.currentAppSelection(current),
                "retired mode cleanup preserves current picker",
            )
            session!!.finishAppSelection(current)
        })
    }

    @Throws(Exception::class)
    private fun openMonitorApps() {
        val start = physical(session!!.width() * .5f, (session!!.height() - 4).toFloat())
        val end = physical(session!!.width() * .5f, session!!.height() * .65f)
        DeviceTestSupport.usableMonitorSwipe(runner, session, start, end)
        pointer(0, start, InputDevice.SOURCE_TOUCHSCREEN, 0f)
        pointer(2, end, InputDevice.SOURCE_TOUCHSCREEN, 0f)
        pointer(1, end, InputDevice.SOURCE_TOUCHSCREEN, 0f)
        await(
            { session!!.monitorShellPage() == "home" && !session!!.monitorShellAnimating() },
            "fullscreen bottom edge opens Apps",
        )
    }

    @Throws(Exception::class)
    private fun fullscreenLauncher() {
        for (surface in 0..1) {
            for (order in 0..1) {
                if (session!!.split!!.state().maximized >= 0) {
                    runner!!.runOnMainSync({ session!!.split!!.maximize() })
                    ready("restore before fullscreen launcher fixture")
                }
                for (pane in 0..1) {
                    val p = pane
                    runner!!.runOnMainSync({ session!!.split!!.select(p) })
                    runner!!.runOnMainSync({ session!!.split!!.closeApp() })
                    ready("empty fullscreen launcher pane " + pane)
                }
                if (session!!.split!!.state().swapped != (order == 1)) {
                    runner!!.runOnMainSync({ session!!.split!!.swap() })
                    ready("fullscreen launcher order")
                }
                val target = session!!.split!!.state().paneAtPosition(1)
                val visible = 1 - target
                runner!!.runOnMainSync({ session!!.split!!.select(visible) })
                runner!!.runOnMainSync({ session!!.split!!.maximize() })
                ready("opposite pane fullscreen")
                val monitor = surface == 1
                if (monitor) openMonitorApps() else menu("openApps")
                val action = launchButton(monitor, 1)
                buttonContact(action, MotionEvent.ACTION_DOWN)
                await({ session!!.appSelectionBox() != null }, "fullscreen launch preview")
                check(
                    !session!!.busy && session!!.split!!.state().maximized == visible,
                    "pressing hidden destination does not resize/select",
                )
                check(
                    (session!!.appSelectionBox()!!.width == session!!.width() &&
                        session!!.appSelectionBox()!!.height == session!!.height()),
                    "fullscreen destination preview covers resulting output",
                )
                buttonContact(action, MotionEvent.ACTION_CANCEL)
                check(
                    (!session!!.busy &&
                        session!!.appSelectionBox() == null &&
                        session!!.split!!.state().maximized == visible),
                    "cancel hidden destination leaves fullscreen untouched",
                )
                if (order == 0) runner!!.runOnMainSync(Runnable({ action!!.performClick() }))
                else {
                    buttonContact(action, MotionEvent.ACTION_DOWN)
                    buttonContact(action, MotionEvent.ACTION_UP)
                }
                val app = ComponentName(phone!!, InputTestActivity::class.java!!)
                await(
                    { !session!!.busy && app == session!!.split!!.selectedComponent() },
                    "one fullscreen destination action completes launch",
                )
                val display = session!!.split!!.displayId(target)
                await(
                    {
                        try {
                            for (task in
                                DisplayTasks.parse(
                                    DeviceTestSupport.shell(runner, "am stack list"),
                                    app.flattenToString(),
                                )) if (
                                task!!.display == display && task!!.matches && task!!.visible
                            )
                                return@await true
                            return@await false
                        } catch (error: Exception) {
                            throw IllegalStateException(error)
                        }
                    },
                    "fullscreen launch reaches actual captured display",
                )
                check(
                    session!!.split!!.state().maximized == target,
                    "hidden launch reveals target fullscreen through Swap",
                )
                if (monitor)
                    await(
                        { session!!.monitorShellPage() == "closed" },
                        "monitor fullscreen launch closes picker",
                    )
                else await({ phoneDialog() == null }, "phone fullscreen launch closes picker")
                inputTap(target)
                capture("fullscreen-launch-s" + surface + "-swap" + order, true)
            }
        }
        runner!!.runOnMainSync({ session!!.split!!.closeApp() })
        ready("empty visible pane before pending fullscreen launch")
        val target = 1 - session!!.split!!.state().maximized
        menu("openApps")
        val action =
            launchButton(false, if (session!!.split!!.state().swapped) 1 - target else target)
        runner!!.runOnMainSync({
            action!!.performClick()
            check(session!!.busy, "hidden-pane launch resize is pending before Stop")
            session!!.pauseDesktop()
        })
        check(session!!.id < 0 && !session!!.busy, "Stop cancels pending hidden-pane launch")
        runner!!.runOnMainSync(Runnable(session!!::ensureDesktop))
        ready("restart after hidden-pane launch cancellation")
        await({ session!!.splitEnabled() }, "split restored after pending launch Stop")
        check(!session!!.split!!.anyApp(), "pending hidden-pane app is not launched after restart")
    }

    @Throws(Exception::class)
    private fun emptyBackgroundTransitions() {
        for (rotation in 0..3) {
            val r = rotation
            runner!!.runOnMainSync({ session!!.rotate(r, false) })
            ready("empty background rotation " + r)
            for (direction in 0..1) {
                val vertical = direction == 1
                runner!!.runOnMainSync({ session!!.split!!.direction(vertical) })
                ready("empty direction transition")
                runner!!.runOnMainSync({ session!!.split!!.ratio(if (vertical) .25f else .65f) })
                ready("empty ratio transition")
                runner!!.runOnMainSync({ session!!.split!!.swap() })
                ready("empty swap transition")
                capture("empty-transition-r" + r + "-d" + direction, true)
                for (pane in 0..1) {
                    val index = pane
                    val bounds = box(pane)
                    runner!!.runOnMainSync({
                        val idle =
                            session!!
                                .monitorInputView()!!
                                .findViewWithTag<View?>("split-idle-" + index)
                        val icon =
                            session!!
                                .monitorInputView()!!
                                .findViewWithTag<View?>("split-idle-apps-" + index)
                        check(
                            (idle!!.isShown() &&
                                idle!!.getWidth() == bounds.width &&
                                idle!!.getHeight() == bounds.height),
                            ("idle background matches transitioned pane bounds " +
                                r +
                                "/" +
                                vertical +
                                "/" +
                                index +
                                " actual=" +
                                idle!!.getWidth() +
                                "x" +
                                idle!!.getHeight() +
                                " expected=" +
                                bounds.width +
                                "x" +
                                bounds.height +
                                " shown=" +
                                idle!!.isShown() +
                                " visibility=" +
                                idle!!.getVisibility() +
                                " parent=" +
                                (idle!!.getParent() as View).getWidth() +
                                "x" +
                                (idle!!.getParent() as View).getHeight() +
                                " requested=" +
                                idle!!.getLayoutParams()!!.width +
                                "x" +
                                idle!!.getLayoutParams()!!.height),
                        )
                        check(
                            ((Math.abs(
                                ((icon!!.getX() + icon!!.getWidth() / 2f) - bounds.width / 2f)
                            ) <= 1) &&
                                (Math.abs(
                                    ((icon!!.getY() + icon!!.getHeight() / 2f) - bounds.height / 2f)
                                ) <= 1)),
                            "idle icon remains centered after transition",
                        )
                    })
                    val actual =
                        BitmapFactory.decodeFile(
                            File(
                                    runner!!.getTargetContext()!!.getExternalCacheDir(),
                                    ("split-acceptance/empty-transition-r" +
                                        r +
                                        "-d" +
                                        direction +
                                        ".png"),
                                )
                                .toString()
                        )
                    val expected =
                        Bitmap.createBitmap(bounds.width, bounds.height, Bitmap.Config.ARGB_8888)
                    runner!!.runOnMainSync({
                        val old =
                            session!!
                                .monitorInputView()!!
                                .findViewWithTag<View?>("split-idle-" + index)
                        val reference = IdleDesktop(old!!.getContext(), {})
                        reference.measure(
                            View.MeasureSpec.makeMeasureSpec(
                                bounds.width,
                                View.MeasureSpec.EXACTLY,
                            ),
                            View.MeasureSpec.makeMeasureSpec(
                                bounds.height,
                                View.MeasureSpec.EXACTLY,
                            ),
                        )
                        reference.layout(0, 0, bounds.width, bounds.height)
                        reference.draw(Canvas(expected))
                    })
                    try {
                        for (point in
                            arrayOf<FloatArray?>(floatArrayOf(.2f, .3f), floatArrayOf(.8f, .7f))) {
                            val x = Math.round(bounds.width * point!![0])
                            val y = Math.round(bounds.height * point!![1])
                            val mapped =
                                physical((bounds.x + x).toFloat(), (bounds.y + y).toFloat())
                            val observed =
                                actual!!.getPixel(Math.round(mapped[0]), Math.round(mapped[1]))
                            val wanted = expected.getPixel(x, y)
                            check(
                                (Math.abs(Color.red(observed) - Color.red(wanted)) <= 2 &&
                                    (Math.abs(Color.green(observed) - Color.green(wanted)) <= 2) &&
                                    (Math.abs(Color.blue(observed) - Color.blue(wanted)) <= 2)),
                                ("actual empty-pane background matches fresh canvas after transition " +
                                    r +
                                    "/" +
                                    direction +
                                    "/" +
                                    pane),
                            )
                        }
                    } finally {
                        actual!!.recycle()
                        expected.recycle()
                    }
                }
                runner!!.runOnMainSync({ session!!.split!!.maximize() })
                ready("empty fullscreen transition")
                capture("empty-fullscreen-r" + r + "-d" + direction, true)
                runner!!.runOnMainSync({ session!!.split!!.maximize() })
                ready("empty restore transition")
            }
        }
        runner!!.runOnMainSync({ session!!.rotate(0, false) })
        ready("empty transitions return landscape")
        menu("openApps")
        val owner = phoneDialog()
        for (mode in intArrayOf(0, 1, 0, 2, 1)) {
            applyLauncherMode(false, owner, mode)
        }
        launcherFullscreenModes(false)
        closePhoneMenu()
    }

    @Throws(Exception::class)
    private fun monitorLauncherModes() {
        openMonitorApps()
        await(
            { session!!.monitorShellPage() == "home" && !session!!.monitorShellAnimating() },
            "bottom gesture directly opens combined split launcher",
        )
        for (mode in intArrayOf(0, 1, 0, 2, 1)) {
            applyLauncherMode(true, null, mode)
            capture("monitor-launcher-mode-" + mode, true)
            launcherPixels("monitor-launcher-mode-" + mode, mode, true, null)
        }
        launcherFullscreenModes(true)
        launchButton(true, 1)
        val chosen = session!!.split!!.state().paneAtPosition(1)
        controllerMenuTap("launch.dev.dexdroid.1", "closed")
        ready("monitor row launches chosen app")
        check(
            (session!!.split!!.selected() == chosen &&
                session!!.split!!.selectedComponent() != null &&
                session!!.split!!.selectedComponent()!!.getClassName() ==
                    InputTestActivity::class.java!!.getName()),
            "monitor row launches into explicit spatial destination",
        )
        runner!!.runOnMainSync({ session!!.split!!.direction(true) })
        ready("mixed occupancy direction transition")
        runner!!.runOnMainSync({ session!!.split!!.ratio(.35f) })
        ready("mixed occupancy ratio transition")
        val emptyPane = 1 - chosen
        runner!!.runOnMainSync({
            val idle =
                session!!.monitorInputView()!!.findViewWithTag<View?>("split-idle-" + emptyPane)
            val bounds = box(emptyPane)
            check(
                (idle!!.isShown() &&
                    idle!!.getWidth() == bounds.width &&
                    idle!!.getHeight() == bounds.height),
                "mixed occupancy idle follows resized pane",
            )
        })
        capture("monitor-mixed-background-transition", true)
        runner!!.runOnMainSync({ session!!.split!!.closeApp() })
        ready("monitor launcher fixture closed")
    }

    @Throws(Exception::class)
    private fun chooseLauncherMode(monitor: Boolean, owner: Dialog?, mode: Int) {
        if (monitor) {
            val target = menuPoint("launcher-mode-" + mode)
            tap(physical(target!![0], target!![1]), InputDevice.SOURCE_TOUCHSCREEN)
        } else {
            runner!!.runOnMainSync({
                owner!!
                    .getWindow()!!
                    .getDecorView()
                    .findViewWithTag<View?>("launcher-mode-" + mode)!!
                    .performClick()
            })
        }
    }

    @Throws(Exception::class)
    private fun applyLauncherMode(monitor: Boolean, owner: Dialog?, mode: Int) {
        val surface = if (monitor) "monitor" else "phone"
        chooseLauncherMode(monitor, owner, mode)
        await(
            {
                (!session!!.busy &&
                    (if (mode == 0) !session!!.splitEnabled()
                    else
                        (session!!.splitEnabled() &&
                            session!!.split!!.state().vertical == (mode == 2) &&
                            session!!.split!!.state().maximized < 0)))
            },
            surface + " mode immediately applies visible layout " + mode,
        )
        ready(surface + " direct launcher mode " + mode)
        check(
            if (monitor) session!!.monitorShellPage() == "home" else owner === phoneDialog(),
            surface + " direct mode retains launcher",
        )
        check(
            session!!.prefs!!.getBoolean("split.enabled", false) == (mode != 0),
            surface + " direct mode persists enabled state",
        )
        if (mode != 0)
            check(
                session!!.prefs!!.getBoolean("split.vertical", false) == (mode == 2),
                surface + " direct mode persists direction",
            )
        runner!!.runOnMainSync({
            val root =
                if (monitor) session!!.monitorShellView() else owner!!.getWindow()!!.getDecorView()
            val selected = root!!.findViewWithTag<View?>("launcher-mode-" + mode)
            check(
                selected!!.isSelected() && selected!!.isEnabled(),
                surface + " applied mode selected and operable",
            )
            val workspace = session!!.split
            selected!!.performClick()
            check(
                !session!!.busy && session!!.split == workspace,
                (surface + " repeated visible mode does not resize or recreate workspace"),
            )
        })
    }

    @Throws(Exception::class)
    private fun openModeLauncher(monitor: Boolean): Dialog? {
        if (!monitor) {
            menu("openApps")
            return phoneDialog()
        }
        openMonitorApps()
        await(
            { session!!.monitorShellPage() == "home" && !session!!.monitorShellAnimating() },
            "bottom gesture opens mode launcher",
        )
        return null
    }

    @Throws(Exception::class)
    private fun closeModeLauncher(monitor: Boolean) {
        if (monitor) controllerMenuTap("monitor-close", "closed") else closePhoneMenu()
    }

    @Throws(Exception::class)
    private fun launcherFullscreenModes(monitor: Boolean) {
        val surface = if (monitor) "monitor" else "phone"
        closeModeLauncher(monitor)
        launcherModeRollback(monitor)
        for (rotation in 0..3) {
            val r = rotation
            runner!!.runOnMainSync({ session!!.rotate(r, false) })
            ready(surface + " launcher fullscreen mode rotation " + r)
            for (mode in intArrayOf(1, 2)) {
                var owner = openModeLauncher(monitor)
                applyLauncherMode(monitor, owner, mode)
                closeModeLauncher(monitor)
                runner!!.runOnMainSync({ session!!.split!!.maximize() })
                ready(surface + " selected split direction fullscreen " + mode + "/" + r)
                check(
                    session!!.split!!.state().maximized >= 0,
                    surface + " fullscreen precondition holds",
                )
                owner = openModeLauncher(monitor)
                applyLauncherMode(monitor, owner, mode)
                runner!!.runOnMainSync({
                    for (pane in 0..1) check(
                        session!!
                            .monitorInputView()!!
                            .findViewWithTag<View?>("split-pane-" + pane)!!
                            .isShown(),
                        surface + " choosing direction reveals pane " + pane,
                    )
                })
                val captureName = surface + "-mode-restored-r" + r + "-m" + mode
                capture(captureName, monitor)
                // These pixel bounds are screen-aligned at rotation zero. The other
                // rotations retain raw captures for explicit rendered inspection.
                if (r == 0) launcherPixels(captureName, mode, monitor, owner)
                closeModeLauncher(monitor)
            }
        }
        runner!!.runOnMainSync({ session!!.rotate(0, false) })
        ready(surface + " launcher modes return landscape")
        val owner = openModeLauncher(monitor)
        applyLauncherMode(monitor, owner, 1)
    }

    @Throws(Exception::class)
    private fun launcherModeRollback(monitor: Boolean) {
        val surface = if (monitor) "monitor" else "phone"
        for (mode in intArrayOf(1, 2)) {
            var owner = openModeLauncher(monitor)
            applyLauncherMode(monitor, owner, mode)
            closeModeLauncher(monitor)
            runner!!.runOnMainSync({ session!!.split!!.maximize() })
            ready(surface + " rollback fullscreen precondition " + mode)
            val workspace = session!!.split
            val maximized = workspace!!.state().maximized
            val first = session!!.id
            val second = workspace!!.displayId(1)
            val saved = session!!.prefs!!.getAll()
            owner = openModeLauncher(monitor)
            val retainedOwner = owner
            val field = SessionService::class.java!!.getDeclaredField("bridge")
            field.setAccessible(true)
            val original = session!!.primaryBridge()
            val resizeCalls = java.util.concurrent.atomic.AtomicInteger()
            val failing =
                java.lang.reflect.Proxy.newProxyInstance(
                    IDeskService::class.java!!.getClassLoader(),
                    arrayOf<Class<*>?>(IDeskService::class.java),
                    { proxy, method, arguments ->
                        if ((method!!.getName() == "resize" && resizeCalls.incrementAndGet() == 1))
                            throw android.os.RemoteException(
                                "Acceptance injected restore resize failure"
                            )
                        try {
                            return@newProxyInstance method!!.invoke(
                                original,
                                *(arguments ?: emptyArray()),
                            )
                        } catch (error: java.lang.reflect.InvocationTargetException) {
                            throw error!!.cause!!
                        }
                    },
                ) as IDeskService
            try {
                runner!!.runOnMainSync({
                    try {
                        field.set(session, failing)
                    } catch (error: IllegalAccessException) {
                        throw AssertionError(error)
                    }
                })
                chooseLauncherMode(monitor, owner, mode)
                await(
                    {
                        (!session!!.busy &&
                            session!!
                                .status!!
                                .contains("Resize failed; retained previous settings"))
                    },
                    surface + " injected mode resize fails with successful rollback " + mode,
                )
                ready(surface + " rolled-back native fullscreen dimensions " + mode)
                check(
                    resizeCalls.get() == 2,
                    surface + " one rejected resize and real rollback resize",
                )
                check(
                    (session!!.split == workspace &&
                        workspace!!.state().maximized == maximized &&
                        session!!.id == first &&
                        workspace!!.displayId(1) == second),
                    (surface + " rollback preserves fullscreen workspace and display identities"),
                )
                check(
                    saved == session!!.prefs!!.getAll(),
                    surface + " rejected mode preserves saved preferences",
                )
                runner!!.runOnMainSync({
                    val root =
                        if (monitor) session!!.monitorShellView()
                        else retainedOwner!!.getWindow()!!.getDecorView()
                    for (i in 0..2) check(
                        !root!!.findViewWithTag<View?>("launcher-mode-" + i)!!.isEnabled(),
                        surface + " rejected mode picker remains inert " + i,
                    )
                })
                val action = launchButton(monitor, 0)
                runner!!.runOnMainSync(Runnable({ action!!.performClick() }))
                check(
                    if (monitor) session!!.monitorShellPage() == "home"
                    else phoneDialog() === owner,
                    surface + " rejected mode picker cannot launch or dismiss",
                )
                check(
                    !session!!.busy && workspace!!.state().maximized == maximized,
                    surface + " rejected picker app action cannot reveal hidden pane",
                )
                capture(surface + "-mode-rollback-m" + mode, monitor)
            } finally {
                runner!!.runOnMainSync({
                    try {
                        if (field.get(session) === failing) field.set(session, original)
                    } catch (error: IllegalAccessException) {
                        throw AssertionError(error)
                    }
                })
                closeModeLauncher(monitor)
            }
            owner = openModeLauncher(monitor)
            applyLauncherMode(monitor, owner, mode)
            closeModeLauncher(monitor)
        }
    }

    @Throws(Exception::class)
    private fun launcherPixels(name: String?, mode: Int, monitor: Boolean, owner: Dialog?) {
        val regions = arrayOfNulls<Rect?>(4)
        runner!!.runOnMainSync({
            val root =
                if (monitor) session!!.monitorShellView() else owner!!.getWindow()!!.getDecorView()
            val density = root!!.getResources()!!.getDisplayMetrics()!!.density
            for (i in 0..2) {
                regions[i] = Rect()
                check(
                    root!!
                        .findViewWithTag<View?>("launcher-mode-" + i)!!
                        .getGlobalVisibleRect(regions[i]),
                    "mode label has visible bounds",
                )
                if (!monitor) {
                    val button = root!!.findViewWithTag<View?>("launcher-mode-" + i)
                    val screen = IntArray(2)
                    button!!.getLocationOnScreen(screen)
                    regions[i]!!.set(
                        screen[0],
                        screen[1],
                        screen[0] + button!!.getWidth(),
                        screen[1] + button!!.getHeight(),
                    )
                }
                regions[i]!!.inset(Math.round(10 * density), Math.round(10 * density))
            }
            val list =
                root!!.findViewWithTag<android.widget.ListView?>(
                    if (monitor) "monitor-app-list" else "phone-app-list"
                )
            val first = list!!.getChildAt(0)
            val label =
                if (first is android.widget.TextView) first else (first as ViewGroup).getChildAt(0)
            regions[3] = Rect()
            check(
                label!!.getGlobalVisibleRect(regions[3]),
                "app label has visible bounds after mode change",
            )
            if (!monitor) {
                val screen = IntArray(2)
                label!!.getLocationOnScreen(screen)
                regions[3]!!.set(
                    screen[0],
                    screen[1],
                    screen[0] + label!!.getWidth(),
                    screen[1] + label!!.getHeight(),
                )
            }
            regions[3]!!.left += Math.round(64 * density)
            regions[3]!!.right -= Math.round(8 * density)
        })
        val image =
            BitmapFactory.decodeFile(
                File(
                        runner!!.getTargetContext()!!.getExternalCacheDir(),
                        "split-acceptance/" + name + ".png",
                    )
                    .toString()
            )
        try {
            for (i in regions.indices) {
                val region = regions[i]
                var ink = 0
                for (y in region!!.top until region!!.bottom) for (x in
                    region!!.left until region!!.right) {
                    val pixel = image!!.getPixel(x, y)
                    val dark = i < 3 && i == mode
                    if (
                        if (dark)
                            (Color.red(pixel) < 70 &&
                                Color.green(pixel) < 70 &&
                                Color.blue(pixel) < 70)
                        else
                            (Color.red(pixel) > 200 &&
                                Color.green(pixel) > 200 &&
                                Color.blue(pixel) > 200)
                    )
                        ink++
                }
                check(
                    ink > 10,
                    "actual rendered launcher label ink after mode " + mode + " region " + i,
                )
            }
        } finally {
            image!!.recycle()
        }
    }

    @Throws(Exception::class)
    private fun keyRelay() {
        nativeReady()
        val box = box(0)
        tap(
            physical(box.x + state!!.getFloat("editorX"), box.y + state!!.getFloat("editorY")),
            InputDevice.SOURCE_MOUSE,
        )
        val before = state!!.getString("text", "")
        val initialUps = state!!.getInt("keyUps")
        check(
            state!!.getString("instance") != null && state!!.getInt("task") == settledFixtureTask,
            "key fixture binds the current native task and instance",
        )
        val baselineCallback = state!!.getLong("callbackSerial")
        runner!!.runOnMainSync({
            keyIdentityChanged = false
            keyFixtureIdentity = fixtureIdentity(state!!)
            keyReportSerial = state!!.getLong("reportSerial")
            synchronized(keyReportChanges) { keyReportChanges.clear() }
        })
        keyObservation("before-letter")
        val letterDown = SystemClock.uptimeMillis()
        runner!!.runOnMainSync({
            for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) check(
                session!!.hardwareKey(
                    KeyEvent(
                        letterDown,
                        SystemClock.uptimeMillis(),
                        action,
                        KeyEvent.KEYCODE_A,
                        0,
                        KeyEvent.META_SHIFT_ON,
                        -1,
                        0,
                        0,
                        InputDevice.SOURCE_KEYBOARD,
                    )
                ),
                "controller receives complete hardware key " + action,
            )
        })
        await(
            {
                (state!!.getString("text", "") == before!! + "A" &&
                    state!!.getInt("keyUps") == initialUps + 1)
            },
            "native EditText receives shifted key through product relay (component boundary)",
        )
        keyObservation("after-letter")
        val ups = state!!.getInt("keyUps")
        val selectionDown = SystemClock.uptimeMillis()
        runner!!.runOnMainSync({
            session!!.hardwareKey(
                KeyEvent(
                    selectionDown,
                    SystemClock.uptimeMillis(),
                    0,
                    KeyEvent.KEYCODE_SHIFT_LEFT,
                    0,
                    0,
                    -1,
                    0,
                    0,
                    InputDevice.SOURCE_KEYBOARD,
                )
            )
            session!!.split!!.select(1)
            session!!.hardwareKey(
                KeyEvent(
                    selectionDown,
                    SystemClock.uptimeMillis(),
                    1,
                    KeyEvent.KEYCODE_SHIFT_LEFT,
                    0,
                    0,
                    -1,
                    0,
                    0,
                    InputDevice.SOURCE_KEYBOARD,
                )
            )
        })
        await(
            { state!!.getInt("keyUps") == ups + 1 },
            "held key release reaches original app after pane selection",
        )
        runner!!.runOnMainSync({ session!!.split!!.select(0) })
        keyObservation("after-selection")
        val menuDown = SystemClock.uptimeMillis()
        val downs = state!!.getInt("keyDowns")
        val releases = state!!.getInt("keyUps")
        runner!!.runOnMainSync({
            session!!.hardwareKey(
                KeyEvent(
                    menuDown,
                    SystemClock.uptimeMillis(),
                    0,
                    KeyEvent.KEYCODE_SHIFT_LEFT,
                    0,
                    0,
                    -1,
                    0,
                    0,
                    InputDevice.SOURCE_KEYBOARD,
                )
            )
        })
        await(
            { state!!.getInt("keyDowns") == downs + 1 },
            "held modifier reaches app before menu opens",
        )
        var primary: Throwable? = null
        try {
            keyObservation("before-menu")
            val cancelRequested = SystemClock.uptimeMillis()
            menu("layoutSettings")
            await(
                { state!!.getInt("keyUps") == releases + 1 },
                "phone menu cancels app-held modifier",
            )
            check(
                (state!!.getInt("keyUpFlags") and KeyEvent.FLAG_CANCELED) != 0,
                "menu cancellation marks key release cancelled",
            )
            check(
                state!!.getLong("keyUpTime") >= cancelRequested &&
                    state!!.getInt("keyUpRepeat") == 0,
                "cancelled key release has fresh timestamp and no repeat",
            )
        } catch (failure: Throwable) {
            primary = failure
            keyObservation("original-deadline-failure")
        }
        // The original outcome is fixed. Late delivery is diagnostic cleanup only.
        try {
            closePhoneMenu()
            runner!!.runOnMainSync({
                check(
                    session!!.hardwareKey(
                        KeyEvent(
                            menuDown,
                            SystemClock.uptimeMillis(),
                            1,
                            KeyEvent.KEYCODE_SHIFT_LEFT,
                            0,
                            0,
                            -1,
                            0,
                            0,
                            InputDevice.SOURCE_KEYBOARD,
                        )
                    ),
                    "late physical modifier release consumed",
                )
            })
            session!!.split!!.fence()
            Thread.sleep(150)
            check(
                state!!.getInt("keyUps") == releases + 1,
                "menu does not duplicate original key release",
            )
        } catch (cleanup: Throwable) {
            if (primary == null) primary = cleanup else primary!!.addSuppressed(cleanup)
        } finally {
            keyObservation("after-late-release")
            val retained =
                !keyIdentityChanged &&
                    fixtureIdentity(state!!) == keyFixtureIdentity &&
                    state!!.getLong("callbackFirstSerial") <= baselineCallback + 1
            results.append("KEY_DIAGNOSTIC historyComplete=").append(retained).append('\n')
            if (!retained) {
                val incomplete =
                    AssertionError("key diagnostic fixture identity/history incomplete")
                if (primary == null) primary = incomplete else primary!!.addSuppressed(incomplete)
            }
            runner!!.runOnMainSync({ keyFixtureIdentity = null })
        }
        if (primary != null) throw primary!!
    }

    @Throws(Exception::class)
    private fun paneApps() {
        val pane = box(0)
        tap(
            physical(pane.x + pane.width / 2f, pane.y + pane.height / 2f),
            InputDevice.SOURCE_TOUCHSCREEN,
        )
        await(
            { session!!.monitorShellPage() == "home" && !session!!.monitorShellAnimating() },
            "empty pane Apps opens monitor chooser",
        )
        runner!!.runOnMainSync({
            val hint =
                session!!
                    .monitorShellView()!!
                    .findViewWithTag<android.widget.TextView?>("monitor-app-target")
            check(
                hint!!.getText()!!.toString().contains("beside an app"),
                "monitor chooser offers explicit row destinations",
            )
            check(
                session!!
                    .monitorShellView()!!
                    .findViewWithTag<View?>("monitor-app-search")!!
                    .isShown(),
                "monitor chooser search available in split",
            )
        })
        capture("monitor-pane-apps", true)
        tap(
            physical(session!!.width() * .04f, session!!.height() * .5f),
            InputDevice.SOURCE_TOUCHSCREEN,
        )
        await(
            { (session!!.monitorShellPage() == "closed" && !session!!.monitorShellAnimating()) },
            "pane chooser dismisses outside",
        )
        menu("apps")
        runner!!.runOnMainSync({
            val hint =
                phoneDialog()!!
                    .getWindow()!!
                    .getDecorView()
                    .findViewWithTag<android.widget.TextView?>("phone-app-target")
            check(
                hint!!.getText()!!.toString().contains("beside an app"),
                "phone chooser offers same row destinations",
            )
        })
        capture("phone-pane-apps", false)
        closePhoneMenu()
    }

    @Throws(Exception::class)
    private fun multi(action: Int, ids: IntArray?, logical: Array<FloatArray?>?) {
        val now = SystemClock.uptimeMillis()
        if (action == 0) down = now
        val props = arrayOfNulls<MotionEvent.PointerProperties?>(ids!!.size)
        val coords = arrayOfNulls<MotionEvent.PointerCoords?>(ids!!.size)
        for (i in ids!!.indices) {
            props[i] = MotionEvent.PointerProperties()
            props[i]!!.id = ids!![i]
            props[i]!!.toolType = MotionEvent.TOOL_TYPE_FINGER
            coords[i] = MotionEvent.PointerCoords()
            val p = physical(logical!![i]!![0], logical!![i]!![1])
            coords[i]!!.x = p[0]
            coords[i]!!.y = p[1]
            coords[i]!!.pressure = 1f
        }
        val event =
            MotionEvent.obtain(
                down,
                now,
                action,
                ids!!.size,
                props,
                coords,
                0,
                0,
                1f,
                1f,
                -1,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0,
            )
        try {
            check(
                DeviceTestSupport.injectOnDisplay(event, session!!.external!!.getDisplayId()),
                "multi-contact host injection " + action,
            )
        } finally {
            event!!.recycle()
        }
    }

    @Throws(Exception::class)
    private fun inputOwnership() {
        val pane = box(1)
        val x = pane.x + pane.width * .5f
        val y = pane.y + pane.height * .65f
        val pinch = InputTestActivity.pinchEvents
        val cancels = InputTestActivity.cancelEvents
        multi(0, intArrayOf(3), arrayOf<FloatArray?>(floatArrayOf(x - 30, y)))
        multi(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),
            intArrayOf(3, 9),
            arrayOf<FloatArray?>(floatArrayOf(x - 30, y), floatArrayOf(x + 30, y)),
        )
        multi(
            2,
            intArrayOf(3, 9),
            arrayOf<FloatArray?>(floatArrayOf(x - 50, y), floatArrayOf(x + 50, y)),
        )
        await(
            { InputTestActivity.pinchEvents > pinch },
            "two physical contacts reach selected pane",
        )
        check(
            InputTestActivity.lastPointer0 == 3 && InputTestActivity.lastPointer1 == 9,
            "native pointer IDs retained",
        )
        runner!!.runOnMainSync({
            session!!.touch(0, floatArrayOf(50f), floatArrayOf(50f))
            session!!.touch(1, floatArrayOf(50f), floatArrayOf(50f))
        })
        check(!session!!.pointerVisible, "physical contacts block phone pointer input")
        multi(
            MotionEvent.ACTION_POINTER_UP or (1 shl 8),
            intArrayOf(3, 9),
            arrayOf<FloatArray?>(floatArrayOf(x - 50, y), floatArrayOf(x + 50, y)),
        )
        runner!!.runOnMainSync(Runnable(session!!::cancelInput))
        await(
            { InputTestActivity.cancelEvents == cancels + 1 },
            "remaining contact cancelled once after one finger lifts",
        )
        // Product cancellation ends the relayed app stream, not the shell's
        // physical host contact. Release pointer 3 before the next gesture.
        multi(MotionEvent.ACTION_UP, intArrayOf(3), arrayOf<FloatArray?>(floatArrayOf(x - 50, y)))
        check(
            InputTestActivity.cancelEvents == cancels + 1,
            "physical release does not duplicate downstream cancellation",
        )
        runner!!.runOnMainSync({
            session!!.split!!.select(1)
            session!!.cx = x
            session!!.cy = y
            session!!
                .prefs!!
                .edit()!!
                .putFloat("scrollSpeed." + session!!.activePackage!!, .2f)!!
                .apply()
            session!!.wheel(0f, 1f)
        })
        await(
            { Math.abs(InputTestActivity.lastWheelV - .2f) < .001f },
            "phone wheel uses selected app speed",
        )
        runner!!.runOnMainSync({
            session!!
                .prefs!!
                .edit()!!
                .putInt("app.dev.dexdroid", 2)!!
                .putInt("app.dev.dexdroid.test", 3)!!
                .apply()
            session!!.saveAppSettings()
        })
        check(
            (session!!.prefs!!.getInt("app.dev.dexdroid", -1) == 2 &&
                session!!.prefs!!.getInt("app.dev.dexdroid.test", -1) == 3),
            "split Save retains both single-view orientations",
        )
        runner!!.runOnMainSync(Runnable(session!!::resetAppSettings))
        check(
            (!session!!.prefs!!.contains("app.dev.dexdroid") &&
                session!!.prefs!!.getInt("app.dev.dexdroid.test", -1) == 3),
            "reset changes only selected app preferences",
        )
    }

    @Throws(Exception::class)
    private fun dragDivider() {
        val bar =
            session!!
                .split!!
                .state()
                .divider(session!!.width(), session!!.height(), session!!.split!!.gap())
        check(
            bar.width == Math.round(2 * session!!.dpi / 160f),
            "visible divider is two logical dp",
        )
        val start =
            physical(
                bar.x.toFloat() + bar.width * .5f + 4 * session!!.dpi / 160f,
                bar.y + bar.height * .5f,
            )
        val end = physical(session!!.width() * .6f, session!!.height() * .5f)
        pointer(0, start, InputDevice.SOURCE_TOUCHSCREEN, 0f)
        pointer(2, end, InputDevice.SOURCE_TOUCHSCREEN, 0f)
        check(!session!!.busy, "divider move does not resize native displays per pixel")
        pointer(1, end, InputDevice.SOURCE_TOUCHSCREEN, 0f)
        await(
            { Math.abs(session!!.split!!.state().ratio - .6f) < .002f },
            "divider release updates ratio after native host dispatch",
        )
        ready("divider release commits resize")
        check(
            Math.abs(session!!.split!!.state().ratio - .6f) < .002f,
            "divider ratio follows gesture",
        )
        nativeClick(InputDevice.SOURCE_MOUSE)
        inputTap()
    }

    private fun dividerCenter(): FloatArray {
        val bar =
            session!!
                .split!!
                .state()
                .divider(session!!.width(), session!!.height(), session!!.split!!.gap())
        return floatArrayOf(bar.x + bar.width * .5f, bar.y + bar.height * .5f)
    }

    private fun phoneContact(action: Int, point: FloatArray?, touchpad: Boolean) {
        runner!!.runOnMainSync({
            if (touchpad) {
                val now = SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(now, now, action, point!![0], point!![1], 0)
                try {
                    session!!.controllerPointer(
                        event,
                        session!!.width().toFloat(),
                        session!!.height().toFloat(),
                    )
                } finally {
                    event!!.recycle()
                }
            } else {
                session!!.touch(action, floatArrayOf(point!![0]), floatArrayOf(point!![1]))
            }
        })
    }

    @Throws(Exception::class)
    private fun settledPhoneInputWindow(
        workspace: SplitWorkspace?,
        display: Int,
        task: Int,
        component: ComponentName?,
        uid: Int,
        width: Int,
        height: Int,
    ): Boolean {
        return (session!!.split == workspace &&
            !session!!.busy &&
            workspace!!.displayId(1) == display &&
            DeviceTestSupport.settledInputWindow(
                runner,
                display,
                task,
                component,
                uid,
                width,
                height,
            ) &&
            session!!.split == workspace &&
            !session!!.busy &&
            workspace!!.displayId(1) == display &&
            workspace!!.dimensions(1, session!!.width(), session!!.height(), false)!!.width ==
                width &&
            (workspace!!.dimensions(1, session!!.width(), session!!.height(), false)!!.height ==
                height))
    }

    @Throws(Exception::class)
    private fun phoneAppTap() {
        val workspace = session!!.split
        val display = workspace!!.displayId(1)
        val component =
            ComponentName(runner!!.getTargetContext()!!, InputTestActivity::class.java!!)
        val uid = runner!!.getTargetContext()!!.getApplicationInfo()!!.uid
        val task = DeviceTestSupport.nativeInputTask(runner, display, component)
        check(task >= 0, "post-divider phone input captures one current native app task")
        val dimensions = workspace!!.dimensions(1, session!!.width(), session!!.height(), false)
        await(
            {
                try {
                    return@await settledPhoneInputWindow(
                        workspace,
                        display,
                        task,
                        component,
                        uid,
                        dimensions!!.width,
                        dimensions!!.height,
                    )
                } catch (error: Exception) {
                    throw IllegalStateException("Cannot verify post-divider native input", error)
                }
            },
            "post-divider phone input has current resized native touchable window",
        )
        val pane = box(1)
        val point = floatArrayOf(pane.x + pane.width * .5f, pane.y + pane.height * .65f)
        val started = SystemClock.uptimeMillis()
        phoneContact(MotionEvent.ACTION_DOWN, point, false)
        phoneContact(MotionEvent.ACTION_UP, point, false)
        await(
            { InputTestActivity.lastUpDownTime >= started },
            "phone native app input remains usable after divider gesture",
        )
    }

    @Throws(Exception::class)
    private fun phoneDivider() {
        var original = session!!.split!!.state().ratio
        val start = dividerCenter()
        phoneContact(MotionEvent.ACTION_DOWN, start, false)
        check(
            !session!!.split!!.physicalActive(),
            "phone divider does not claim physical ownership",
        )
        phoneContact(MotionEvent.ACTION_UP, start, false)
        ready("phone divider tap releases")
        check(
            Math.abs(session!!.split!!.state().ratio - original) < .003f,
            "divider tap retains ratio",
        )
        phoneAppTap()
        for (touchpad in booleanArrayOf(false, true)) {
            val ratio = if (touchpad) .55f else .45f
            val end = floatArrayOf(session!!.width() * ratio, session!!.height() * .5f)
            phoneContact(MotionEvent.ACTION_DOWN, dividerCenter(), touchpad)
            phoneContact(MotionEvent.ACTION_MOVE, end, touchpad)
            check(!session!!.busy, "phone divider preview defers native resize until release")
            phoneContact(MotionEvent.ACTION_UP, end, touchpad)
            ready("phone divider release commits " + touchpad)
            check(
                Math.abs(session!!.split!!.state().ratio - ratio) < .002f,
                "phone divider release follows gesture " + touchpad,
            )
            phoneAppTap()
        }
        original = session!!.split!!.state().ratio
        val abandoned = floatArrayOf(session!!.width() * .4f, session!!.height() * .5f)
        phoneContact(MotionEvent.ACTION_DOWN, dividerCenter(), false)
        phoneContact(MotionEvent.ACTION_MOVE, abandoned, false)
        nativeClick(InputDevice.SOURCE_TOUCHSCREEN)
        phoneContact(MotionEvent.ACTION_UP, abandoned, false)
        check(
            !session!!.busy && Math.abs(session!!.split!!.state().ratio - original) < .002f,
            "physical takeover cancels phone divider without committing preview",
        )
        phoneAppTap()
        phoneContact(MotionEvent.ACTION_DOWN, dividerCenter(), true)
        phoneContact(MotionEvent.ACTION_MOVE, abandoned, true)
        runner!!.runOnMainSync(Runnable(session!!::cancelInput))
        phoneContact(MotionEvent.ACTION_UP, abandoned, true)
        check(
            !session!!.busy && Math.abs(session!!.split!!.state().ratio - original) < .002f,
            "cancelled touchpad divider cannot commit from a late release",
        )
        phoneAppTap()
    }

    @Throws(Exception::class)
    private fun monitorHost(): Presentation? {
        val field = SessionService::class.java!!.getDeclaredField("monitor")
        field.setAccessible(true)
        return field.get(session) as Presentation?
    }

    private fun controllerMouse(action: Int, point: FloatArray, wheel: Float) {
        val property = MotionEvent.PointerProperties()
        property.id = 0
        property.toolType = MotionEvent.TOOL_TYPE_MOUSE
        val coords = MotionEvent.PointerCoords()
        coords.x = point[0] / 2
        coords.y = point[1] / 2
        coords.pressure = 1f
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL, wheel)
        val now = SystemClock.uptimeMillis()
        val event =
            MotionEvent.obtain(
                now,
                now,
                action,
                1,
                arrayOf<MotionEvent.PointerProperties?>(property),
                arrayOf<MotionEvent.PointerCoords?>(coords),
                0,
                if (action == MotionEvent.ACTION_DOWN) MotionEvent.BUTTON_PRIMARY else 0,
                1f,
                1f,
                -1,
                0,
                InputDevice.SOURCE_MOUSE,
                0,
            )
        try {
            session!!.controllerPointer(event, session!!.width() / 2f, session!!.height() / 2f)
        } finally {
            event!!.recycle()
        }
    }

    private fun menuPoint(tag: String?): FloatArray? {
        val rect = Rect()
        runner!!.runOnMainSync({
            check(
                session!!
                    .monitorShellView()!!
                    .findViewWithTag<View?>(tag)!!
                    .getGlobalVisibleRect(rect),
                "controller pointer target visible " + tag!!,
            )
        })
        DeviceTestSupport.requireZeroMonitorOrigin(runner, session)
        val decor = session!!.monitorWindow()!!.getDecorView()
        return Geometry.unmap(
            rect.exactCenterX(),
            rect.exactCenterY(),
            session!!.rotation,
            decor.getWidth().toFloat(),
            decor.getHeight().toFloat(),
            session!!.width(),
            session!!.height(),
        )
    }

    @Throws(Exception::class)
    private fun controllerMenuTap(tag: String?, page: String?) {
        val point = menuPoint(tag)
        runner!!.runOnMainSync({
            controllerMouse(MotionEvent.ACTION_DOWN, point!!, 0f)
            controllerMouse(MotionEvent.ACTION_UP, point!!, 0f)
        })
        await(
            { session!!.monitorShellPage() == page && !session!!.monitorShellAnimating() },
            "phone mouse operates visible monitor menu " + tag!!,
        )
    }

    @Throws(Exception::class)
    private fun openControls() {
        val start = physical(session!!.width() * .5f, 4f)
        val end = physical(session!!.width() * .5f, session!!.height() * .3f)
        DeviceTestSupport.usableMonitorSwipe(runner, session, start, end)
        pointer(0, start, InputDevice.SOURCE_TOUCHSCREEN, 0f)
        pointer(2, end, InputDevice.SOURCE_TOUCHSCREEN, 0f)
        pointer(1, end, InputDevice.SOURCE_TOUCHSCREEN, 0f)
        await(
            { (session!!.monitorShellPage() == "controls" && !session!!.monitorShellAnimating()) },
            "controls ready for phone mouse routing",
        )
    }

    @Throws(Exception::class)
    private fun controllerPointerOwnership() {
        nativeReady()
        val host = monitorHost()
        val first = box(0)
        val second = box(1)
        val appButton =
            floatArrayOf(
                first.x + state!!.getFloat("buttonX"),
                first.y + state!!.getFloat("buttonY"),
            )
        val phoneTarget =
            floatArrayOf(second.x + second.width * .5f, second.y + second.height * .65f)
        if (Build.VERSION.SDK_INT == 30) {
            val clicks = state!!.getInt("clicks")
            val touches = InputTestActivity.touchEvents
            val wheels = InputTestActivity.wheelEvents
            val physicalButton = physical(appButton!![0], appButton!![1])
            runner!!.runOnMainSync({
                session!!.cancelInput()
                session!!.hideMonitorPointer()
                val x = session!!.cx
                val y = session!!.cy
                val now = SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(now, now, 0, physicalButton[0], physicalButton[1], 0)
                event!!.setSource(InputDevice.SOURCE_TOUCHSCREEN)
                try {
                    check(
                        host!!.dispatchTouchEvent(event!!),
                        "native focus wait accepts physical DOWN",
                    )
                    controllerMouse(0, phoneTarget!!, 0f)
                    controllerMouse(1, phoneTarget!!, 0f)
                    controllerMouse(MotionEvent.ACTION_SCROLL, phoneTarget!!, -3f)
                    check(
                        session!!.cx == x && session!!.cy == y && !session!!.pointerVisible,
                        "pending physical focus blocks controller mouse and wheel",
                    )
                    event!!.setAction(MotionEvent.ACTION_UP)
                    check(
                        host!!.dispatchTouchEvent(event!!),
                        "native focus wait accepts physical UP",
                    )
                } finally {
                    event!!.recycle()
                }
            })
            await(
                { state!!.getInt("clicks") == clicks + 1 },
                "buffered physical click reaches native app",
            )
            check(
                (InputTestActivity.touchEvents == touches &&
                    InputTestActivity.wheelEvents == wheels),
                "buffered physical sequence excludes controller pointer from other app",
            )
        }
        openControls()
        val title = menuPoint("monitor-title")
        val physicalTitle = physical(title!![0], title!![1])
        val ups = state!!.getInt("ups")
        val wheels = state!!.getInt("wheels")
        runner!!.runOnMainSync({
            val now = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(now, now, 0, physicalTitle[0], physicalTitle[1], 0)
            event!!.setSource(InputDevice.SOURCE_TOUCHSCREEN)
            try {
                check(host!!.dispatchTouchEvent(event!!), "physical menu owns contact")
                val x = session!!.cx
                val y = session!!.cy
                controllerMouse(0, appButton!!, 0f)
                controllerMouse(1, appButton!!, 0f)
                controllerMouse(MotionEvent.ACTION_SCROLL, appButton!!, -3f)
                check(
                    session!!.cx == x && session!!.cy == y && !session!!.pointerVisible,
                    "physical menu contact blocks controller pointer and wheel",
                )
                event!!.setAction(MotionEvent.ACTION_UP)
                host!!.dispatchTouchEvent(event!!)
            } finally {
                event!!.recycle()
            }
        })
        controllerMenuTap("monitor-layout", "layout")
        val scroll =
            session!!
                .monitorShellView()!!
                .findViewWithTag<android.widget.ScrollView?>("monitor-menu-scroll")
        check(scroll != null, "monitor Layout exposes scrollable content")
        val before = scroll!!.getScrollY()
        runner!!.runOnMainSync({
            controllerMouse(
                MotionEvent.ACTION_SCROLL,
                floatArrayOf(session!!.width() / 2f, session!!.height() / 2f),
                -6f,
            )
        })
        await({ scroll!!.getScrollY() > before }, "controller wheel scrolls monitor modal")
        val outside = floatArrayOf(40f, session!!.height() / 2f)
        runner!!.runOnMainSync({
            controllerMouse(0, outside!!, 0f)
            controllerMouse(1, outside!!, 0f)
        })
        await(
            { (session!!.monitorShellPage() == "closed" && !session!!.monitorShellAnimating()) },
            "controller outside click dismisses monitor modal",
        )
        Thread.sleep(300)
        check(
            state!!.getInt("ups") == ups && state!!.getInt("wheels") == wheels,
            "controller modal clicks and wheels never reach underlying app",
        )
        phoneAppTap()
    }

    @Throws(Exception::class)
    private fun performance() {
        val samples = LongArray(100)
        for (i in -10 until samples.size) {
            nativeClick(InputDevice.SOURCE_TOUCHSCREEN)
            check(
                state!!.getLong("downSampleTime") == down,
                "latency sample belongs to current native contact",
            )
            if (i >= 0) samples[i] = state!!.getLong("downLatencyMs")
        }
        val sorted = samples.clone()
        java.util.Arrays.sort(sorted!!)
        val data = org.json.JSONObject()
        data.put(
            "boundary",
            "injected physical-host DOWN eventTime to different-UID Activity dispatchTouchEvent; excludes rendering and broadcast/await time",
        )
        data.put("input", "framework-injected touchscreen, not physical finger")
        data.put("warmup", 10)
        data.put("count", samples.size)
        data.put("p50_ms", sorted!![49])
        data.put("p95_ms", sorted!![94])
        data.put("rotation", session!!.rotation)
        data.put("width", session!!.width())
        data.put("height", session!!.height())
        data.put("dpi", session!!.dpi)
        val raw = org.json.JSONArray()
        for (value in samples) raw.put(value)
        data.put("samples_ms", raw)
        val folder = File(runner!!.getTargetContext()!!.getExternalCacheDir(), "split-acceptance")
        if (!folder.isDirectory() && !folder.mkdirs()) throw IOException("Performance directory")
        java.io
            .OutputStreamWriter(
                FileOutputStream(File(folder, "performance.json")),
                java.nio.charset.StandardCharsets.UTF_8,
            )
            .use({ writer -> writer.write(data.toString(2)) })
        results
            .append("MEASURE native DOWN dispatch latency, n=100 warmup=10 p50=")
            .append(sorted!![49])
            .append(" ms p95=")
            .append(sorted!![94])
            .append(" ms; excludes rendering.\n")
    }

    @Throws(Exception::class)
    private fun capture(name: String?, monitor: Boolean) {
        Thread.sleep(200)
        val bitmap: Bitmap?
        if (monitor) {
            val window = session!!.monitorWindow()
            val view = window!!.getDecorView()
            bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888)
            val latch = CountDownLatch(1)
            val code = intArrayOf(-1)
            runner!!.runOnMainSync({
                PixelCopy.request(
                    window!!,
                    bitmap!!,
                    { value ->
                        code[0] = value
                        latch.countDown()
                    },
                    Handler(Looper.getMainLooper()!!),
                )
            })
            check(
                latch.await(5, TimeUnit.SECONDS) && code!![0] == PixelCopy.SUCCESS,
                "monitor capture " + name!!,
            )
        } else bitmap = runner!!.getUiAutomation()!!.takeScreenshot()
        check(bitmap != null, "screenshot available " + name!!)
        val folder = File(runner!!.getTargetContext()!!.getExternalCacheDir(), "split-acceptance")
        if (!folder.isDirectory() && !folder.mkdirs()) throw IOException("Evidence directory")
        try {
            FileOutputStream(File(folder, name!! + ".png"))
                .use({ stream -> bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, stream) })
        } finally {
            bitmap!!.recycle()
        }
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun legacyReceiver(context: Context?, receiver: BroadcastReceiver?) {
        if (Build.VERSION.SDK_INT >= 33) throw IllegalStateException("Modern receiver")
        context!!.registerReceiver(receiver, fixtureFilter())
    }

    private fun fixtureFilter(): IntentFilter {
        val filter = IntentFilter(SplitProbeFixture.ACTION)
        filter.addAction(LaunchParentActivity.ACTION)
        filter.addAction("dev.dexdroid.test.PROTECTION_STATE")
        return filter
    }

    @Throws(Exception::class)
    private fun navigationClick(pane: Int) {
        await(
            {
                (child != null &&
                    child!!.getInt("display") == session!!.split!!.displayId(pane) &&
                    child!!.getInt("targetWidth") > 0 &&
                    child!!.getInt("targetHeight") > 0)
            },
            "navigation target laid out in pane " + pane,
        )
        runner!!.getUiAutomation()!!.waitForIdle(250, 5000)
        if (Build.VERSION.SDK_INT >= 31) {
            val activity =
                ("dev.dexdroid.test/dev.dexdroid.fixture.NavigationFixtureActivity" +
                    (if ("navigation-child" == child!!.getString("page")) "\$Child" else ""))
            await(
                {
                    try {
                        val report = DeviceTestSupport.shell(runner, "dumpsys input")
                        for (line in
                            report!!
                                .split(("\n").toRegex())
                                .dropLastWhile({ it.isEmpty() })
                                .toTypedArray()) {
                            if (
                                (!line!!.contains("applicationInfo.name=ActivityRecord{") ||
                                    !line!!.contains(activity!! + " ") ||
                                    !line!!.contains(
                                        (", displayId=" + session!!.split!!.displayId(pane) + ",")
                                    ))
                            )
                                continue
                            val window =
                                ("Input Dispatcher State:\n  DispatchEnabled: true\n  DispatchFrozen: false\n  Display: " +
                                    session!!.split!!.displayId(pane) +
                                    "\n    logicalSize=fixture\n    Windows:\n" +
                                    line +
                                    "\n  Connections:\n")
                            if (
                                (ModernInputWindowState.inspect(
                                    window,
                                    session!!.split!!.displayId(pane),
                                    child!!.getInt("uid"),
                                ) == ModernInputWindowState.State.READY)
                            )
                                return@await true
                        }
                        return@await false
                    } catch (error: Exception) {
                        throw IllegalStateException(error)
                    }
                },
                "current native activity admits touch before navigation click",
            )
        }
        val bounds = box(pane)
        tap(
            physical(
                bounds.x + child!!.getFloat("targetX"),
                bounds.y + child!!.getFloat("targetY"),
            ),
            InputDevice.SOURCE_TOUCHSCREEN,
        )
        runner!!.getUiAutomation()!!.waitForIdle(250, 5000)
    }

    @Throws(Exception::class)
    private fun showController() {
        runner!!.runOnMainSync({
            runner!!
                .getTargetContext()!!
                .startActivity(
                    Intent(runner!!.getTargetContext(), MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    ActivityOptions.makeBasic()!!.setLaunchDisplayId(0)!!.toBundle(),
                )
        })
        await(
            BooleanSupplier({ this.phoneVisible() }),
            "controller is visible before native navigation",
        )
    }

    @Throws(Exception::class)
    private fun closePane(pane: Int) {
        runner!!.runOnMainSync({
            session!!.split!!.select(pane)
            session!!.split!!.closeApp()
        })
        ready("pane close for isolated navigation fixture")
        await(
            {
                session!!
                    .monitorInputView()!!
                    .findViewWithTag<View?>("split-idle-apps-" + pane)!!
                    .isShown()
            },
            "isolated pane is empty",
        )
    }

    @Throws(Exception::class)
    private fun navigationKeyReady(pane: Int, display: Int, task: Int, uid: Int, page: String?) {
        await(
            {
                (child != null &&
                    session!!.split!!.displayId(pane) == display &&
                    child!!.getInt("display") == display &&
                    child!!.getInt("task") == task &&
                    child!!.getInt("uid") == uid &&
                    page == child!!.getString("page") &&
                    child!!.getBoolean("focused") &&
                    child!!.getInt("targetWidth") > 0 &&
                    child!!.getInt("targetHeight") > 0)
            },
            "current native navigation keyboard destination focused: " + page!!,
        )
    }

    @Throws(Exception::class)
    private fun navigationKeepsPhoneForeground(original: ComponentName?) {
        val navigation =
            ComponentName("dev.dexdroid.test", "dev.dexdroid.fixture.NavigationFixtureActivity")
        val input = ComponentName(runner!!.getTargetContext()!!, InputTestActivity::class.java!!)
        val first = session!!.id
        val second = session!!.split!!.displayId(1)
        closePane(0)
        for (pane in 0..1) {
            val selected = pane
            if (pane == 1) {
                closePane(1)
                runner!!.runOnMainSync({ session!!.split!!.select(0) })
                launch(input)
            }
            runner!!.runOnMainSync({ session!!.split!!.select(selected) })
            launch(navigation)
            await(
                {
                    (child != null &&
                        "navigation-parent" == child!!.getString("page") &&
                        child!!.getInt("display") == session!!.split!!.displayId(selected))
                },
                "native navigation root ready",
            )
            showController()
            val task = child!!.getInt("task")
            val navigationDisplay = session!!.split!!.displayId(pane)
            val navigationUid = child!!.getInt("uid")
            if (Build.VERSION.SDK_INT >= 31) pendingNativeContact(pane, navigation)
            for (cycle in 0..1) {
                navigationClick(pane)
                await(
                    { "navigation-child" == child!!.getString("page") },
                    "same-task child entered",
                )
                check(child!!.getInt("task") == task, "native child stays in parent task")
                check(phoneVisible(), "child entry preserves phone foreground")
                navigationClick(pane)
                await(
                    { "navigation-parent" == child!!.getString("page") },
                    "native Up returns to parent",
                )
                check(phoneVisible(), "child Up preserves phone controller")
                navigationClick(pane)
                await(
                    { "navigation-child" == child!!.getString("page") },
                    "child reopened before Back",
                )
                if (Build.VERSION.SDK_INT >= 31)
                    navigationKeyReady(
                        pane,
                        navigationDisplay,
                        task,
                        navigationUid,
                        "navigation-child",
                    )
                runner!!.runOnMainSync({
                    session!!.split!!.select(selected)
                    session!!.key(KeyEvent.KEYCODE_BACK)
                })
                await(
                    { "navigation-parent" == child!!.getString("page") },
                    "remote Back returns to parent",
                )
                check(phoneVisible(), "child Back preserves phone controller")
                inputTap(1 - pane)
                check(phoneVisible(), "surviving other pane input preserves phone foreground")
            }
            runner!!.runOnMainSync({ phone!!.moveTaskToBack(true) })
            await({ !phoneVisible() }, "phone deliberately leaves controller")
            navigationClick(pane)
            await(
                { "navigation-child" == child!!.getString("page") },
                "new component accepts monitor input with phone backgrounded",
            )
            check(!phoneVisible(), "monitor input respects intentional phone backgrounding")
            showController()
            navigationClick(pane)
            await(
                { "navigation-parent" == child!!.getString("page") },
                "Up works after phone controller returns",
            )
            if (Build.VERSION.SDK_INT >= 31)
                navigationKeyReady(
                    pane,
                    navigationDisplay,
                    task,
                    navigationUid,
                    "navigation-parent",
                )
            runner!!.runOnMainSync({
                session!!.split!!.select(selected)
                session!!.key(KeyEvent.KEYCODE_A)
            })
            await(
                { "a" == child!!.getString("payload") },
                "ordinary key reaches native parent after controller return and Up",
            )
            check(phoneVisible(), "ordinary monitor key preserves phone controller")
            if (Build.VERSION.SDK_INT >= 31)
                navigationKeyReady(
                    pane,
                    navigationDisplay,
                    task,
                    navigationUid,
                    "navigation-parent",
                )
            runner!!.runOnMainSync({
                session!!.split!!.select(selected)
                session!!.key(KeyEvent.KEYCODE_BACK)
            })
            await(
                {
                    session!!
                        .monitorInputView()!!
                        .findViewWithTag<View?>("split-idle-apps-" + selected)!!
                        .isShown()
                },
                "native root Back empties only its pane",
            )
            check(phoneVisible(), "root Back preserves controller")
            check(
                session!!.id == first && session!!.split!!.displayId(1) == second,
                "navigation preserves both owned display identities",
            )
            inputTap(1 - pane)
            val empty = box(pane)
            tap(
                physical(empty.x + empty.width / 2f, empty.y + empty.height / 2f),
                InputDevice.SOURCE_TOUCHSCREEN,
            )
            await(
                { (session!!.monitorShellPage() == "home" && !session!!.monitorShellAnimating()) },
                "root-exited pane Apps stays usable",
            )
            runner!!.runOnMainSync({
                session!!
                    .monitorShellView()!!
                    .findViewWithTag<View?>("monitor-close")!!
                    .performClick()
            })
            runner!!.runOnMainSync({ session!!.split!!.select(selected) })
            launch(navigation)
            await(
                {
                    ("navigation-parent" == child!!.getString("page") &&
                        child!!.getInt("display") == session!!.split!!.displayId(selected))
                },
                "root-exited pane relaunches",
            )
            closePane(pane)
        }
        closePane(0)
        runner!!.runOnMainSync({ session!!.split!!.select(1) })
        launch(input)
        runner!!.runOnMainSync({ session!!.split!!.select(0) })
        launch(original!!)
        showController()
    }

    @Throws(Exception::class)
    private fun pendingNativeContact(pane: Int, fixture: ComponentName) {
        val first = session!!.id
        val second = session!!.split!!.displayId(1)
        val count = child!!.getInt("count")
        val command =
            ("am start --display " +
                session!!.split!!.displayId(pane) +
                " --windowingMode 1 --activity-single-top -n " +
                fixture.flattenToString() +
                " --ez input_ready ")
        DeviceTestSupport.shell(runner, command!! + "false")
        await(
            { "input-pending" == child!!.getString("payload") },
            "native fixture deliberately delays touch admission",
        )
        await(
            {
                try {
                    return@await (ModernInputWindowState.inspect(
                        DeviceTestSupport.shell(runner, "dumpsys input"),
                        session!!.split!!.displayId(pane),
                        runner!!.getContext()!!.getApplicationInfo()!!.uid,
                    ) == ModernInputWindowState.State.PENDING)
                } catch (error: Exception) {
                    throw IllegalStateException(error)
                }
            },
            "native input dispatcher confirms pending touch window",
        )
        val pendingPane = box(pane)
        val pendingPoint =
            physical(
                pendingPane.x + child!!.getFloat("targetX"),
                pendingPane.y + child!!.getFloat("targetY"),
            )
        pointer(MotionEvent.ACTION_DOWN, pendingPoint, InputDevice.SOURCE_TOUCHSCREEN, 0f)
        val spyAccepted = booleanArrayOf(false)
        await(
            {
                if (session!!.status!!.contains("Try again")) {
                    spyAccepted[0] = false
                    return@await true
                }
                try {
                    spyAccepted[0] =
                        spyOnlyContact(
                            DeviceTestSupport.shell(runner, "dumpsys input"),
                            session!!.split!!.displayId(pane),
                            down,
                        )
                    return@await spyAccepted!![0]
                } catch (error: Exception) {
                    throw IllegalStateException(error)
                }
            },
            "early native contact reports retry or verified OEM spy-only acceptance",
        )
        if (spyAccepted!![0])
            results.append(
                "SKIP rejected-DOWN recovery on this configuration: OEM system SPY accepted the contact without an app target; held-contact and fresh-retry assertions still run.\n"
            )
        else
            check(
                session!!.status!!.contains("Try again"),
                "rejected native DOWN reports retry without stopping desktop",
            )
        check(
            session!!.id == first && session!!.split!!.displayId(1) == second,
            "pending native contact retains both displays",
        )
        check(
            ("navigation-parent" == child!!.getString("page") && child!!.getInt("count") == count),
            "pending complete contact does not open child",
        )
        DeviceTestSupport.shell(runner, command!! + "true")
        await(
            { "input-pending" != child!!.getString("payload") },
            "native fixture allows fresh contact",
        )
        await(
            {
                try {
                    return@await (ModernInputWindowState.inspect(
                        DeviceTestSupport.shell(runner, "dumpsys input"),
                        session!!.split!!.displayId(pane),
                        runner!!.getContext()!!.getApplicationInfo()!!.uid,
                    ) == ModernInputWindowState.State.READY)
                } catch (error: Exception) {
                    throw IllegalStateException(error)
                }
            },
            "native window ready before fresh retry",
        )
        pointer(MotionEvent.ACTION_MOVE, pendingPoint, InputDevice.SOURCE_TOUCHSCREEN, 0f)
        pointer(MotionEvent.ACTION_UP, pendingPoint, InputDevice.SOURCE_TOUCHSCREEN, 0f)
        runner!!.getUiAutomation()!!.waitForIdle(250, 5000)
        check(
            child!!.getString("touchAction") == null,
            "pending MOVE and UP cannot reach newly ready native window",
        )
        check(
            "navigation-parent" == child!!.getString("page"),
            "suppressed contact never replays after window becomes ready",
        )
        inputTap(1 - pane)
        navigationClick(pane)
        await(
            { "navigation-child" == child!!.getString("page") },
            "fresh retry reaches native child",
        )
        check(child!!.getInt("count") == 0, "fresh retry creates one child instance")
        if (!spyAccepted!![0])
            await(
                { !session!!.status!!.contains("Try again") },
                "fresh successful contact clears retry status",
            )
        else
            check(
                !session!!.status!!.contains("Try again"),
                "accepted spy-only contact leaves no false retry status",
            )
        navigationClick(pane)
        await(
            { "navigation-parent" == child!!.getString("page") },
            "pending test returns to native parent",
        )
        check(
            child!!.getInt("count") == count + 1,
            "only one fresh contact opened the native child",
        )
        showController()
    }

    private fun spyOnlyContact(report: String?, display: Int, contactDown: Long): Boolean {
        val touched =
            java.util.regex.Pattern.compile(
                    ("(?ms)^    LogicalDisplayId " +
                        display +
                        ":\\n(.*?)(?=^    LogicalDisplayId |^  \\S|\\z)")
                )
                .matcher(report!!)
        if (!touched.find()) return false
        val state = touched.group(1)
        val name = "PointerEventDispatcherOverlay" + display
        if (
            (!state!!.contains("name='" + name + "',") ||
                state!!.contains("FOREGROUND") ||
                state!!.split(("name='").toRegex()).toTypedArray().size != 2 ||
                !state!!.contains("downTimeInTarget=" + contactDown * 1000000L + ","))
        )
            return false
        for (line in
            report!!.split(("\n").toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()) {
            if (
                (line!!.contains("name=" + name + ",") &&
                    line!!.contains(", displayId=" + display + ",") &&
                    line!!.contains("SPY") &&
                    line!!.contains("ownerUid=1000,"))
            )
                return true
        }
        return false
    }

    @Throws(Exception::class)
    private fun readerAndHandoff() {
        val parent = ComponentName("dev.dexdroid.test", "dev.dexdroid.fixture.LaunchParentActivity")
        runner!!.runOnMainSync({ session!!.split!!.select(0) })
        launch(parent)
        await(
            {
                (child != null &&
                    "parent" == child!!.getString("page") &&
                    child!!.getInt("display") == session!!.id)
            },
            "parent owns first pane",
        )
        val pane = box(0)
        await(
            { child!!.getInt("targetWidth") > 0 && child!!.getInt("targetHeight") > 0 },
            "parent content laid out before native touch",
        )
        capture("monitor-parent", true)
        var center: FloatArray? =
            physical(pane.x + child!!.getFloat("targetX"), pane.y + child!!.getFloat("targetY"))
        tap(center, InputDevice.SOURCE_TOUCHSCREEN)
        await(
            {
                ("reader" == child!!.getString("page") && child!!.getInt("display") == session!!.id)
            },
            "private reader child stays in first pane",
        )
        runner!!.getUiAutomation()!!.waitForIdle(250, 5000)
        await(
            {
                (child!!.getInt("windowWidth") == pane.width &&
                    child!!.getInt("windowHeight") == pane.height)
            },
            "private reader window laid out",
        )
        capture("monitor-private-reader", true)
        center =
            physical(pane.x + child!!.getFloat("targetX"), pane.y + child!!.getFloat("targetY"))
        tap(center, InputDevice.SOURCE_TOUCHSCREEN)
        await(
            { "reader" == child!!.getString("page") && child!!.getInt("count") == 1 },
            "private reader receives native tap in split",
        )
        inputTap()
        var moved: DisplayTasks.Root? = null
        for (root in
            DisplayTasks.parse(
                DeviceTestSupport.shell(runner, "am stack list"),
                parent.flattenToString(),
            )) if (root!!.display == session!!.id && root!!.taskId == child!!.getInt("task"))
            moved = root
        check(moved != null && moved!!.tasks == 1, "handoff owns an isolated app root")
        val handoffTask = child!!.getInt("task")
        val handoffOpens = child!!.getInt("count")
        val output =
            DeviceTestSupport.shell(
                runner,
                "am start -W --display 0 -n " + parent.flattenToString(),
            )
        check(!output!!.contains("Error"), "Android accepts ordinary phone app launch")
        await(
            { child!!.getInt("display") == 0 && child!!.getInt("task") == handoffTask },
            "phone launch reuses reader application task",
        )
        check(child!!.getInt("count") >= handoffOpens, "phone handoff preserves application state")
        await(
            {
                session!!
                    .monitorInputView()!!
                    .findViewWithTag<View?>("split-idle-apps-0")!!
                    .isShown()
            },
            "departed app pixels clear only its own pane",
        )
        inputTap()
        capture("monitor-handoff", true)
        runner!!.runOnMainSync({
            runner!!
                .getTargetContext()!!
                .startActivity(
                    Intent(runner!!.getTargetContext(), MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    ActivityOptions.makeBasic()!!.setLaunchDisplayId(0)!!.toBundle(),
                )
        })
        await(BooleanSupplier({ this.phoneVisible() }), "phone controller restored after handoff")
    }

    @Throws(Exception::class)
    private fun protectedPane() {
        runner!!.runOnMainSync({ session!!.split!!.select(0) })
        launch(ComponentName("dev.dexdroid.test", "dev.dexdroid.fixture.ProtectedFixtureActivity"))
        val pane = box(0)
        await(
            {
                (protection != null &&
                    protection!!.getInt("display") == session!!.id &&
                    protection!!.getInt("windowWidth") == pane.width &&
                    protection!!.getInt("windowHeight") == pane.height)
            },
            "normal protection fixture laid out before native touch",
        )
        capture("monitor-before-protect", true)
        tap(
            physical(
                pane.x + protection!!.getFloat("targetX"),
                pane.y + protection!!.getFloat("targetY"),
            ),
            InputDevice.SOURCE_TOUCHSCREEN,
        )
        await({ session!!.protectedContent }, "protected pane detected")
        await(
            {
                session!!
                    .monitorShellView()!!
                    .findViewWithTag<View?>("monitor-protected-notice")!!
                    .isShown()
            },
            "protected pane exposes compact Help",
        )
        runner!!.runOnMainSync({
            val notice =
                session!!.monitorShellView()!!.findViewWithTag<View?>("monitor-protected-notice")
            check(
                (notice!!.getLeft() >= pane.x &&
                    notice!!.getRight() <= pane.x + pane.width &&
                    notice!!.getTop() >= pane.y &&
                    notice!!.getBottom() <= pane.y + pane.height),
                "protection notice stays within its own pane",
            )
        })
        inputTap()
        capture("monitor-protected-pane", true)
        runner!!.runOnMainSync({
            session!!.split!!.select(0)
            session!!.split!!.closeApp()
        })
        ready("protected app closed independently")
        await({ !session!!.protectedContent }, "closing protected pane clears its notice")
        inputTap()
    }

    @Throws(Exception::class)
    private fun historyHandoff(fixture: ComponentName) {
        var moved: DisplayTasks.Root? = null
        for (root in
            DisplayTasks.parse(
                DeviceTestSupport.shell(runner, "am stack list"),
                fixture.flattenToString(),
            )) if (root!!.display == session!!.id && root!!.matches) moved = root
        check(moved != null && moved!!.tasks == 1, "history fixture owns isolated foreground task")
        val handoffTask = moved!!.taskId
        val launchOutput =
            DeviceTestSupport.shell(
                runner,
                "am start -W --display 0 -n " + fixture.flattenToString(),
            )
        check(!launchOutput!!.contains("Error"), "Android accepts history app phone launch")
        await(
            {
                if (state!!.getInt("display") != 0) return@await false
                try {
                    for (root in
                        DisplayTasks.parse(
                            DeviceTestSupport.shell(runner, "am stack list"),
                            fixture.flattenToString(),
                        )) if (
                        root!!.display == 0 && root!!.matches && root!!.taskId == handoffTask
                    )
                        return@await true
                } catch (error: Exception) {
                    throw RuntimeException(error)
                }

                false
            },
            "ordinary phone launch preserves history task",
        )
        await(
            { session!!.split!!.selectedPackage() == "dev.dexdroid" },
            "underlying app becomes selected after foreground task leaves",
        )
        runner!!.getUiAutomation()!!.waitForIdle(250, 5000)
        check(session!!.hasDesktopApp(), "existing task remains live instead of empty Apps overlay")
        val folder = File(runner!!.getTargetContext()!!.getExternalCacheDir(), "split-acceptance")
        if (!folder.isDirectory()) folder.mkdirs()
        FileOutputStream(File(folder, "history-before-input.txt"))
            .use({ output ->
                output.write(
                    DeviceTestSupport.shell(runner, "dumpsys input")!!.toByteArray(
                        java.nio.charset.StandardCharsets.UTF_8
                    )
                )
            })
        val pane = box(0)
        tap(
            physical(pane.x + pane.width * .5f, pane.y + pane.height * .6f),
            InputDevice.SOURCE_TOUCHSCREEN,
        )
        val expected = down
        await(
            { InputTestActivity.lastUpDownTime == expected },
            "input reaches underlying app after handoff",
        )
        runner!!.runOnMainSync({ session!!.split!!.closeApp() })
        ready("history pane closed")
        launch(fixture)
        await(
            { state!!.getInt("display") == session!!.id },
            "native fixture restored after history scenario",
        )
    }

    private fun secondaryHelperGone(): Boolean {
        try {
            for (line in
                DeviceTestSupport.shell(runner, "ps -A -o NAME")!!.split(("\n").toRegex())
                    .dropLastWhile({ it.isEmpty() })
                    .toTypedArray()) if (
                line!!.trim({ it <= ' ' }) == "dev.dexdroid:desktop-secondary"
            )
                return false
            return true
        } catch (error: Exception) {
            throw IllegalStateException(error)
        }
    }

    private fun phoneVisible(): Boolean {
        val visible = booleanArrayOf(false)
        runner!!.runOnMainSync({
            val decor = phone!!.getWindow()!!.getDecorView()
            val bounds = Rect()
            // A managed app can own global key focus while this phone window is visible.
            visible[0] =
                (!phone!!.isFinishing() &&
                    !phone!!.isDestroyed() &&
                    decor.getDisplay() != null &&
                    decor.getDisplay()!!.getDisplayId() == 0 &&
                    decor.getWindowVisibility() == View.VISIBLE &&
                    decor.isShown() &&
                    decor.getGlobalVisibleRect(bounds) &&
                    !bounds.isEmpty())
        })
        return visible!![0]
    }

    @Throws(Exception::class)
    private fun restorePhoneController(purpose: String) {
        runner!!.runOnMainSync({
            runner!!
                .getTargetContext()!!
                .startActivity(
                    Intent(runner!!.getTargetContext(), MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    ActivityOptions.makeBasic()!!.setLaunchDisplayId(0)!!.toBundle(),
                )
        })
        await(BooleanSupplier({ this.phoneVisible() }), "phone visible before " + purpose)
    }

    private fun menu(method: String?) {
        try {
            restorePhoneController("opening " + method!!)
            val call = MainActivity::class.java!!.getDeclaredMethod(method)
            call.setAccessible(true)
            runner!!.runOnMainSync({
                try {
                    call.invoke(phone)
                } catch (error: Exception) {
                    throw IllegalStateException(error)
                }
            })
            await(
                {
                    (phoneDialog() != null &&
                        phoneDialog()!!.getWindow()!!.getDecorView().isShown())
                },
                "phone menu visible " + method!!,
            )
            runner!!.getUiAutomation()!!.waitForIdle(250, 5000)
        } catch (error: Exception) {
            throw IllegalStateException(error)
        }
    }

    private fun closePhoneMenu() {
        try {
            val field = MainActivity::class.java!!.getDeclaredField("settingsDialog")
            field.setAccessible(true)
            runner!!.runOnMainSync({
                try {
                    val dialog = field.get(phone) as Dialog?
                    if (dialog != null) dialog!!.dismiss()
                } catch (error: Exception) {
                    throw IllegalStateException(error)
                }
            })
        } catch (error: Exception) {
            throw IllegalStateException(error)
        }
    }

    fun run(): String {
        val context = runner!!.getTargetContext()
        val connected = CountDownLatch(1)
        var bound = false
        var registered = false
        var saved: android.content.SharedPreferences? = null
        var preferences: Map<String?, *>? = null
        val binding =
            object : ServiceConnection {
                public override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    session = (binder as SessionService.LocalBinder).get()
                    connected.countDown()
                }

                public override fun onServiceDisconnected(name: ComponentName?) {}
            }
        val receiver =
            object : BroadcastReceiver() {
                public override fun onReceive(ignored: Context?, intent: Intent) {
                    if ("dev.dexdroid.test.PROTECTION_STATE" == intent.getAction())
                        protection = Bundle(intent.getExtras())
                    if (LaunchParentActivity.ACTION == intent.getAction())
                        child = Bundle(intent.getExtras())
                    if ("Left" == intent.getStringExtra("pane")) {
                        val incoming = Bundle(intent.getExtras())
                        val expected = keyFixtureIdentity
                        if (expected != null) {
                            val actual = fixtureIdentity(incoming)
                            val serial = incoming.getLong("reportSerial")
                            if (actual != expected) keyIdentityChanged = true
                            if (actual != expected || serial <= keyReportSerial) {
                                synchronized(keyReportChanges) {
                                    if (keyReportChanges.size == 32) keyReportChanges.removeFirst()
                                    keyReportChanges.addLast(
                                        "expected=$expected actual=$actual serial=$serial previous=$keyReportSerial"
                                    )
                                }
                                return
                            }
                            keyReportSerial = serial
                        }
                        state = incoming
                    }
                }
            }
        try {
            val evidence = File(context!!.getExternalCacheDir(), "split-acceptance")
            for (name in
                arrayOf<String?>(
                    "performance.json",
                    "history-before-input.txt",
                    "failure-input.txt",
                    "failure-window-windows.txt",
                    "failure-display.txt",
                )) {
                val stale = File(evidence, name!!)
                if (stale.exists() && !stale.delete())
                    throw IOException("Cannot clear stale split evidence: " + name!!)
            }
            if (!navigationOnly)
                DeviceTestSupport.isolateNavigationFixtures(runner) { results.append(it) }
            if (Build.VERSION.SDK_INT >= 33)
                context!!.registerReceiver(receiver, fixtureFilter(), Context.RECEIVER_EXPORTED)
            else legacyReceiver(context, receiver)
            registered = true
            phone = DeviceTestSupport.startPhone(runner!!)
            bound =
                context!!.bindService(
                    Intent(context, SessionService::class.java),
                    binding,
                    Context.BIND_AUTO_CREATE,
                )
            check(connected.await(5, TimeUnit.SECONDS), "product controller bound")
            ready("single desktop ready")
            saved = session!!.prefs
            preferences = java.util.HashMap<String?, Any?>(saved!!.getAll()!!)
            runner!!.runOnMainSync({ session!!.rotate(0, false) })
            ready("initial landscape ready")
            val fixture =
                ComponentName("dev.dexdroid.test", "dev.dexdroid.fixture.SplitProbeFixture\$Left")
            launch(ComponentName(context!!, InputTestActivity::class.java!!))
            launch(fixture)
            await({ state != null }, "cross-UID native fixture reports")
            check(
                state!!.getInt("uid") != android.os.Process.myUid(),
                "native fixture has independent UID",
            )
            if (navigationOnly) {
                runner!!.runOnMainSync({ session!!.enableSplit(false) })
                await(
                    {
                        (session!!.split != null &&
                            session!!.split!!.displayId(1) >= 0 &&
                            !session!!.busy)
                    },
                    "navigation split display ready",
                )
                runner!!.runOnMainSync({ session!!.split!!.select(1) })
                launch(ComponentName(context!!, InputTestActivity::class.java!!))
                for (rotation in 0..3) {
                    val r = rotation
                    runner!!.runOnMainSync({ session!!.rotate(r, false) })
                    ready("navigation rotation " + r)
                    navigationKeepsPhoneForeground(fixture)
                    await(
                        BooleanSupplier({ this.firstFrameReady() }),
                        "restored native fixture frame settled",
                    )
                    await({ inputFrameReady(1) }, "surviving input fixture frame settled")
                    capture("navigation-r" + r, true)
                }
                return results.toString()
            }
            menu("layoutSettings")
            runner!!.runOnMainSync({
                layoutTargets(phoneDialog()!!.getWindow()!!.getDecorView(), false)
            })
            capture("phone-layout-off", false)
            phoneTarget(0)
            check(session!!.appSelectionBox() == null, "single picker has no persistent highlight")
            closePhoneMenu()
            await({ session!!.appSelectionBox() == null }, "single picker cancel clears highlight")
            menu("layoutSettings")
            runner!!.runOnMainSync({
                phoneDialog()!!
                    .getWindow()!!
                    .getDecorView()
                    .findViewWithTag<View?>("layout-toggle")!!
                    .performClick()
            })
            closePhoneMenu()
            await(
                {
                    session!!.split != null &&
                        session!!.split!!.displayId(1) >= 0 &&
                        !session!!.busy
                },
                "second managed display ready",
            )
            val first = session!!.id
            val second = session!!.split!!.displayId(1)
            check(first != second, "two distinct live display IDs")
            runner!!.runOnMainSync({
                for (pane in 0..1) {
                    val decoration =
                        session!!
                            .monitorInputView()!!
                            .findViewWithTag<android.widget.FrameLayout?>("split-pane-" + pane)
                    check(decoration!!.getForeground() == null, "pane has no focus outline " + pane)
                }
            })
            historyHandoff(fixture)
            runner!!.runOnMainSync({ session!!.split!!.select(1) })
            launchSecondFromLayout()
            navigationKeepsPhoneForeground(fixture)
            await({ state!!.getInt("width") == box(0).width }, "first app resized into pane")
            capture("monitor-two-apps-r0", true)
            menu("layoutSettings")
            capture("phone-layout-on", false)
            closePhoneMenu()
            for (rotation in 0..3) {
                val r = rotation
                runner!!.runOnMainSync({
                    session!!.rotate(r, false)
                    // A task/status update can arrive before the remote resize completes.
                    session!!.split!!.layoutViews()
                    val w = if (r % 2 == 0) session!!.baseW else session!!.baseH
                    val h = if (r % 2 == 0) session!!.baseH else session!!.baseW
                    val expected = session!!.split!!.dimensions(1, w, h, false)
                    val texture =
                        session!!.monitorInputView()!!.findViewWithTag<View?>("split-texture-1")
                    check(
                        (texture!!.getLayoutParams()!!.width == expected!!.width &&
                            texture!!.getLayoutParams()!!.height == expected!!.height),
                        "status relayout preserves staged surface geometry " + r,
                    )
                })
                ready("split rotation " + r)
                await(
                    {
                        (state!!.getInt("width") == box(0).width &&
                            state!!.getInt("height") == box(0).height)
                    },
                    "native app dimensions follow rotation " + r,
                )
                await(
                    BooleanSupplier({ this.firstFrameReady() }),
                    "first app frame fills native pane before contact after rotation " + r,
                )
                nativeClick(InputDevice.SOURCE_TOUCHSCREEN)
                nativeClick(InputDevice.SOURCE_MOUSE)
                inputTap()
                capture("monitor-two-apps-r" + r, true)
                monitorLayout(r)
            }
            runner!!.runOnMainSync({ session!!.rotate(0, false) })
            ready("landscape restored")
            keyRelay()
            performance()
            inputOwnership()
            dragDivider()
            phoneDivider()
            controllerPointerOwnership()
            runner!!.runOnMainSync({ session!!.split!!.ratio(1f / 3) })
            ready("one-third resize committed")
            await({ state!!.getInt("width") == box(0).width }, "native buffer reflects ratio")
            nativeClick(InputDevice.SOURCE_MOUSE)
            inputTap()
            runner!!.runOnMainSync({ session!!.split!!.swap() })
            ready("swap committed")
            nativeClick(InputDevice.SOURCE_MOUSE)
            inputTap()
            check(
                session!!.id == first && session!!.split!!.displayId(1) == second,
                "swap preserves app display IDs",
            )
            runner!!.runOnMainSync({
                session!!.split!!.select(0)
                session!!.split!!.maximize()
            })
            ready("selected pane fullscreen")
            await(
                { state!!.getInt("width") == session!!.width() },
                "fullscreen native display actually resized",
            )
            nativeClick(InputDevice.SOURCE_MOUSE)
            runner!!.runOnMainSync({ session!!.split!!.maximize() })
            ready("restore split")
            runner!!.runOnMainSync({ session!!.split!!.direction(true) })
            ready("top-bottom committed")
            await({ state!!.getInt("height") == box(0).height }, "top-bottom native height")
            inputTap()
            capture("monitor-top-bottom", true)
            val oldW = session!!.baseW
            val oldH = session!!.baseH
            val oldDpi = session!!.dpi
            runner!!.runOnMainSync({ session!!.preview(1920, 1080, 160) })
            ready("Fit DPI preview committed")
            check(session!!.awaitingConfirmation(), "split preview awaits confirmation")
            inputTap()
            runner!!.runOnMainSync({ session!!.confirm(false) })
            ready("both pane preview reverted")
            check(
                session!!.baseW == oldW && session!!.baseH == oldH && session!!.dpi == oldDpi,
                "resolution and DPI restored",
            )
            runner!!.runOnMainSync({ session!!.split!!.direction(false) })
            ready("left-right restored")
            await({ state!!.getInt("height") == box(0).height }, "fixture ready after preview")
            nativeReady()
            val left = box(0)
            val wheels = state!!.getInt("wheels")
            pointer(
                MotionEvent.ACTION_SCROLL,
                physical(left.x + state!!.getFloat("padX"), left.y + state!!.getFloat("padY")),
                InputDevice.SOURCE_MOUSE,
                -.5f,
            )
            await(
                { state!!.getInt("wheels") == wheels + 1 },
                "native mouse wheel targets pointed pane",
            )
            check(state!!.getFloat("wheelV") == -.5f, "hardware wheel amount preserved")
            val moves = state!!.getInt("moves")
            val ups = state!!.getInt("ups")
            val start =
                physical(left.x + state!!.getFloat("padX"), left.y + state!!.getFloat("padY"))
            val right = box(1)
            val end = physical(right.x + right.width * .5f, right.y + right.height * .7f)
            pointer(MotionEvent.ACTION_DOWN, start, InputDevice.SOURCE_MOUSE, 0f)
            Thread.sleep(100)
            pointer(MotionEvent.ACTION_MOVE, end, InputDevice.SOURCE_MOUSE, 0f)
            pointer(MotionEvent.ACTION_UP, end, InputDevice.SOURCE_MOUSE, 0f)
            await(
                { state!!.getInt("moves") > moves && state!!.getInt("ups") == ups + 1 },
                "drag crossing divider releases original app",
            )
            runner!!.runOnMainSync({ session!!.split!!.select(1) })
            val target = box(1)
            val phoneDown = SystemClock.uptimeMillis()
            runner!!.runOnMainSync({
                session!!.touch(
                    0,
                    floatArrayOf(target.x + target.width / 2f),
                    floatArrayOf(target.height * .6f),
                )
                session!!.touch(
                    1,
                    floatArrayOf(target.x + target.width / 2f),
                    floatArrayOf(target.height * .6f),
                )
            })
            await(
                { InputTestActivity.lastUpDownTime >= phoneDown },
                "phone touchpad pipeline targets selected pane",
            )
            val backs = InputTestActivity.backEvents
            runner!!.runOnMainSync({ session!!.key(KeyEvent.KEYCODE_BACK) })
            await({ InputTestActivity.backEvents == backs + 1 }, "Back affects selected pane")
            runner!!.runOnMainSync({ session!!.launch(fixture, null) })
            check(
                session!!.split!!.selected() == 0,
                "opening existing app selects its original pane",
            )
            runner!!.runOnMainSync({ phone!!.moveTaskToBack(true) })
            Thread.sleep(250)
            nativeClick(InputDevice.SOURCE_MOUSE)
            inputTap()
            runner!!.runOnMainSync({
                context!!.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    ActivityOptions.makeBasic()!!.setLaunchDisplayId(0)!!.toBundle(),
                )
            })
            await({ phoneVisible() && !session!!.busy }, "controller reopened with both panes")
            runner!!.runOnMainSync({ session!!.split!!.select(0) })
            runner!!.runOnMainSync({ session!!.split!!.closeApp() })
            ready("close selected app completed")
            await(
                {
                    session!!
                        .monitorInputView()!!
                        .findViewWithTag<View?>("split-idle-apps-0")!!
                        .isShown()
                },
                "closed pane offers Apps",
            )
            inputTap()
            capture("monitor-one-empty", true)
            paneApps()
            readerAndHandoff()
            protectedPane()
            val bindingArgs = SplitWorkspace::class.java!!.getDeclaredField("args")
            bindingArgs.setAccessible(true)
            val retiredBinding =
                bindingArgs.get(session!!.split) as rikka.shizuku.Shizuku.UserServiceArgs?
            val remembered = session!!.split!!.state()
            runner!!.runOnMainSync({ session!!.split!!.end() })
            ready("single view restored")
            await({ !session!!.splitEnabled() }, "split ended")
            check(!session!!.savedSplitLayout().enabled, "explicit End remembers single view")
            check(
                ((context!!.getSystemService(Context.DISPLAY_SERVICE!!) as DisplayManager)
                    .getDisplay(second) == null),
                "second display removed after End",
            )
            await(
                BooleanSupplier({ this.secondaryHelperGone() }),
                "End terminates the unbound secondary shell process",
            )
            runner!!.runOnMainSync(Runnable(session!!::enableSplit))
            await(
                {
                    session!!.split != null &&
                        !session!!.busy &&
                        session!!.split!!.displayId(1) >= 0
                },
                "fresh split session starts",
            )
            val last = session!!.split!!.displayId(1)
            check(
                (session!!.split!!.state().vertical == remembered.vertical &&
                    session!!.split!!.state().swapped == remembered.swapped &&
                    Math.abs(session!!.split!!.state().ratio - remembered.ratio) < .001f),
                "enabling split restores last confirmed geometry",
            )
            val fresh = session!!.split
            runner!!.runOnMainSync({ fresh!!.select(1) })
            launch(fixture)
            await({ state!!.getInt("display") == last }, "fresh secondary owns native fixture")
            await(
                {
                    (state!!.getInt("layoutWidth") == box(1).width &&
                        state!!.getInt("layoutBottom") == box(1).height)
                },
                "fresh secondary native content laid out",
            )
            // Reproduce an obsolete cleanup callback using the actual Shizuku removal API.
            // remove=true identifies the service by args, not by the ServiceConnection.
            rikka.shizuku.Shizuku.unbindUserService(retiredBinding!!, null, true)
            Thread.sleep(500)
            check(
                session!!.split == fresh && fresh!!.displayId(1) == last && session!!.id == first,
                "late retired binding removal preserves current split displays",
            )
            val freshClicks = state!!.getInt("clicks")
            val freshPane = box(1)
            tap(
                physical(
                    freshPane.x + state!!.getFloat("buttonX"),
                    freshPane.y + state!!.getFloat("buttonY"),
                ),
                InputDevice.SOURCE_TOUCHSCREEN,
            )
            await(
                { state!!.getInt("clicks") == freshClicks + 1 },
                "current secondary receives native input after retired binding removal",
            )
            capture("monitor-stale-helper-retained", true)
            val retiredChoice = arrayOf<SessionService.AppSelection?>(null)
            runner!!.runOnMainSync({ retiredChoice[0] = session!!.beginAppSelection(1) })
            val stopEdge = physical(session!!.width() * .5f, 4f)
            val stopInside = physical(session!!.width() * .5f, session!!.height() * .3f)
            DeviceTestSupport.usableMonitorSwipe(runner, session, stopEdge, stopInside)
            pointer(0, stopEdge, InputDevice.SOURCE_TOUCHSCREEN, 0f)
            Thread.sleep(35)
            pointer(2, stopInside, InputDevice.SOURCE_TOUCHSCREEN, 0f)
            pointer(1, stopInside, InputDevice.SOURCE_TOUCHSCREEN, 0f)
            await(
                {
                    (session!!.monitorShellPage() == "controls" &&
                        !session!!.monitorShellAnimating())
                },
                "monitor Controls ready before explicit Stop",
            )
            results.append(
                StopReturnAcceptance.run(
                    runner!!,
                    phone,
                    session!!,
                    {
                        session!!
                            .monitorShellView()!!
                            .findViewWithTag<View?>("monitor-stop")!!
                            .performClick()
                    },
                    "split",
                )
            )
            await(
                {
                    (((context!!.getSystemService(Context.DISPLAY_SERVICE!!) as DisplayManager)
                        .getDisplay(first) == null) &&
                        ((context!!.getSystemService(Context.DISPLAY_SERVICE!!) as DisplayManager)
                            .getDisplay(last) == null))
                },
                "Stop removes both displays",
            )
            await(
                BooleanSupplier({ this.secondaryHelperGone() }),
                "Stop leaves no secondary shell process",
            )
            check(session!!.savedSplitLayout().enabled, "Stop retains split enabled preference")
            runner!!.runOnMainSync(Runnable(session!!::ensureDesktop))
            await(
                {
                    (session!!.split != null &&
                        session!!.id != first &&
                        !session!!.busy &&
                        session!!.split!!.displayId(1) >= 0)
                },
                "restart restores split with fresh displays",
            )
            check(!session!!.split!!.anyApp(), "restart does not relaunch previous apps")
            check(
                (session!!.split!!.state().vertical == remembered.vertical &&
                    session!!.split!!.state().swapped == remembered.swapped &&
                    Math.abs(session!!.split!!.state().ratio - remembered.ratio) < .001f),
                "restart retains confirmed direction ratio and order",
            )
            check(session!!.appSelectionBox() == null, "restart retains no picker highlight")
            runner!!.runOnMainSync({
                check(
                    !session!!.launchSelection(retiredChoice!![0], fixture, null),
                    "retired picker cannot launch into restored session",
                )
            })
            runner!!.runOnMainSync({
                for (pane in 0..1) {
                    check(
                        session!!.monitorInputView()!!.findViewWithTag<View?>("split-idle-" + pane)
                            is IdleDesktop,
                        "empty pane reuses single-screen background " + pane,
                    )
                    check(
                        session!!
                            .monitorInputView()!!
                            .findViewWithTag<View?>("split-idle-apps-" + pane)!!
                            .isShown(),
                        "empty pane centered Apps visible " + pane,
                    )
                }
            })
            capture("monitor-restored-empty-panes", true)
            emptyBackgroundTransitions()
            monitorLauncherModes()
            pendingModeOwnership()
            fullscreenLauncher()
            results.append(StopReturnAcceptance.failures(runner, phone, session, true))
            val restoredFirst = session!!.id
            val restoredSecond = session!!.split!!.displayId(1)
            runner!!.runOnMainSync(Runnable(session!!::pauseDesktop))
            await(
                {
                    (((context!!.getSystemService(Context.DISPLAY_SERVICE!!) as DisplayManager)
                        .getDisplay(restoredFirst) == null) &&
                        ((context!!.getSystemService(Context.DISPLAY_SERVICE!!) as DisplayManager)
                            .getDisplay(restoredSecond) == null))
                },
                "restored split stops both fresh displays",
            )
            await(
                BooleanSupplier({ this.secondaryHelperGone() }),
                "restored split cleanup terminates secondary",
            )
            results.append(
                "GAP physical Bluetooth typing/shortcuts, Android 16/17, fault injection, frame pacing and touch-to-photon timing require separate review.\n"
            )
        } catch (error: Throwable) {
            results
                .append("INPUT lastDown=")
                .append(InputTestActivity.lastDownTime)
                .append(" lastUpDown=")
                .append(InputTestActivity.lastUpDownTime)
                .append(" expected=")
                .append(down)
                .append('\n')
            results.append("STATE ").append(state).append('\n')
            results.append("CHILD ").append(child).append('\n')
            results.append("FRAME ").append(frameEvidence).append('\n')
            try {
                try {
                    capture("failure-monitor", true)
                } catch (unavailable: Exception) {
                    results.append("MONITOR CAPTURE ").append(unavailable).append('\n')
                }

                try {
                    capture("failure-phone", false)
                } catch (unavailable: Exception) {
                    results.append("PHONE CAPTURE ").append(unavailable).append('\n')
                }

                val folder = File(context!!.getExternalCacheDir(), "split-acceptance")
                for (name in arrayOf<String?>("input", "window windows", "display")) {
                    FileOutputStream(File(folder, "failure-" + name!!.replace(' ', '-') + ".txt"))
                        .use({ output ->
                            output.write(
                                DeviceTestSupport.shell(runner, "dumpsys " + name!!)!!.toByteArray(
                                    java.nio.charset.StandardCharsets.UTF_8
                                )
                            )
                        })
                }
                results.append(DeviceTestSupport.shell(runner, "am stack list"))
            } catch (diagnostic: Exception) {
                results.append("DIAGNOSTIC ").append(diagnostic)
            }

            val stack = StringWriter()
            error!!.printStackTrace(PrintWriter(stack))
            results.append("FAIL ").append(stack)
        } finally {
            if (session != null) runner!!.runOnMainSync(Runnable(session!!::pauseDesktop))
            if (saved != null && preferences != null) {
                val edit = saved!!.edit()!!.clear()
                for (entry in preferences!!.entries) {
                    val value = entry!!.value
                    val key = entry!!.key
                    if (value is Int) edit!!.putInt(key, (value as Int?)!!)
                    else if (value is Float) edit!!.putFloat(key, (value as Float?)!!)
                    else if (value is Boolean) edit!!.putBoolean(key, (value as Boolean?)!!)
                    else if (value is Long) edit!!.putLong(key, (value as Long?)!!)
                    else if (value is String) edit!!.putString(key, value as String?)
                }
                edit!!.commit()
            }
            if (registered) context!!.unregisterReceiver(receiver)
            if (bound) context!!.unbindService(binding)
        }
        return results.toString()
    }
}
