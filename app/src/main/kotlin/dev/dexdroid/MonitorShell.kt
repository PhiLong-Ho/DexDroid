package dev.dexdroid

import android.app.*
import android.content.*
import android.content.res.*
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import java.util.*
import java.util.function.*

/** Native monitor widgets laid out in virtual pixels, rotated with the desktop. */
class MonitorShell(private val session: SessionService?, private val displayContext: Context?) {
    @JvmField val root: FrameLayout?
    private var context: Context? = null
    private var panel: LinearLayout? = null
    private var scrim: View? = null
    private var protectionNotice: View? = null
    private var launchNotice: LaunchNotice? = null
    private var protectionDismissed: Boolean = false
    private var idle: IdleDesktop? = null
    private var taskbar: MonitorTaskbar? = null
    private var destination: DestinationHighlight? = null
    private var launcherActions: LauncherControls? = null
    private var style: ControllerStyle? = null
    private var searchEditor: SearchEditor? = null
    private var searchKeyboard: MonitorSearchKeyboard? = null
    private var searchResults: ListView? = null
    private var catalogCleanup: Runnable? = null
    private var capabilitiesPanel: CapabilitiesPanel? = null
    private val feedback: EdgeFeedback?
    private var transitioning: Boolean = false
    private var outsideTouch: Boolean = false
    private var outsideEligible: Boolean = false
    private var outsideX: Float = 0.toFloat()
    private var outsideY: Float = 0.toFloat()
    private var transitionSerial: Int = 0
    @Volatile private var page: String? = "closed"
    private val navigation: MenuNavigation<String?> = MenuNavigation<String?>()
    private val scrollPositions: MutableMap<String?, Int?> = HashMap<String?, Int?>()
    private var contentScroll: ScrollView? = null
    private var renderedPage: String? = ""
    private var launcherQuery: String? = ""
    private var settingsQuery = ""
    private var settingsSearch: SettingsSearchPanel? = null
    private var discardViewState: Boolean = false
    private val updates: MutableList<Runnable?> = ArrayList<Runnable?>()
    private var updating: Boolean = false
    private var physicalUi: Boolean = false
    private var phoneUi: Boolean = false
    private var consuming: Boolean = false
    private var candidate: String? = ""
    private var pendingEdge: String? = ""
    private var startX: Float = 0.toFloat()
    private var startY: Float = 0.toFloat()
    private var physicalDown: Long = 0
    private var phoneDown: Long = 0
    private var lastW: Int = 0
    private var lastH: Int = 0
    private var lastDpi: Int = 0
    private var lastRotation: Int = -1
    private val handler: Handler = Handler(Looper.getMainLooper()!!)
    private val tick: Runnable =
        object : Runnable {
            public override fun run() {
                if (page != "closed") {
                    refresh()
                    handler.postDelayed(this, 750)
                }
            }
        }
    private val rotations: Array<String?>? =
        arrayOf<String?>("Landscape 0°", "Portrait 90°", "Landscape 180°", "Portrait 270°")

    val isOpen: Boolean
        get() {
            return page != "closed"
        }

    private var pendingApp: ComponentName? = null
    private var pendingAppName: String? = ""

    init {
        root = FrameLayout(displayContext!!)
        root!!.setForceDarkAllowed(false)
        root!!.setClipChildren(false)
        root!!.setFocusableInTouchMode(true)
        root!!.setDefaultFocusHighlightEnabled(false)
        feedback = EdgeFeedback(displayContext, { v -> dp(v.toFloat()) })
    }

    private fun dp(value: Float): Int {
        return Math.round(value * session!!.dpi / 160f)
    }

    fun layout(physicalW: Int, physicalH: Int) {
        val w = session!!.width()
        val h = session!!.height()
        if (
            (lastW != w ||
                lastH != h ||
                lastDpi != session!!.dpi ||
                lastRotation != session!!.rotation)
        ) {
            lastW = w
            lastH = h
            lastDpi = session!!.dpi
            lastRotation = session!!.rotation
            val config = Configuration(displayContext!!.getResources()!!.getConfiguration())
            config.densityDpi = session!!.dpi
            context =
                ContextThemeWrapper(
                    displayContext!!.createConfigurationContext(config),
                    R.style.DexDroidTheme,
                )
            style = ControllerStyle(context)
            root!!.setLayoutParams(FrameLayout.LayoutParams(w, h))
            build()
        }
        syncViewport()
        val scale =
            Math.min(
                physicalW.toFloat() / (if (session!!.rotation % 2 == 0) w else h),
                physicalH.toFloat() / (if (session!!.rotation % 2 == 0) h else w),
            )
        root!!.setPivotX(w / 2f)
        root!!.setPivotY(h / 2f)
        root!!.setScaleX(scale)
        root!!.setScaleY(scale)
        root!!.setRotation(session!!.rotation * 90f)
        root!!.setX((physicalW - w) / 2f)
        root!!.setY((physicalH - h) / 2f)
    }

    fun page(): String? {
        return page
    }

    fun physicalActive(): Boolean {
        return physicalUi || consuming
    }

    private fun bounds(child: View): RectF {
        val rect = RectF(0f, 0f, child.getWidth().toFloat(), child.getHeight().toFloat())
        child.getMatrix()!!.mapRect(rect)
        rect.offset(child.getLeft().toFloat(), child.getTop().toFloat())
        return rect
    }

    private fun idleVisible(): Boolean {
        return idle != null && idle!!.getVisibility() == View.VISIBLE
    }

    private fun hit(x: Float, y: Float): Boolean {
        return page != "closed" ||
            transitioning ||
            (idleVisible() && session!!.viewport().containsApp(x, y)) ||
            taskbarHit(x, y) ||
            protectionHit(x, y)
    }

    private fun taskbarHit(x: Float, y: Float): Boolean {
        return !session!!.monitorNavigationEnabled() &&
            x >= 0 &&
            x < session!!.width() &&
            y >= session!!.appHeight() &&
            y < session!!.height()
    }

    private fun protectionHit(x: Float, y: Float): Boolean {
        return (protectionNotice != null &&
            protectionNotice!!.isShown() &&
            bounds(protectionNotice!!).contains(x, y))
    }

    fun animating(): Boolean {
        return transitioning
    }

    private fun inverse(physicalW: Float, physicalH: Float): Matrix {
        val src =
            floatArrayOf(
                0f,
                0f,
                session!!.width().toFloat(),
                0f,
                session!!.width().toFloat(),
                session!!.height().toFloat(),
                0f,
                session!!.height().toFloat(),
            )
        val dst = FloatArray(8)
        val corners =
            arrayOf<FloatArray?>(
                floatArrayOf(0f, 0f),
                floatArrayOf(1f, 0f),
                floatArrayOf(1f, 1f),
                floatArrayOf(0f, 1f),
            )
        for (i in 0..3) {
            val p =
                Geometry.map(
                    corners!![i]!![0],
                    corners!![i]!![1],
                    session!!.rotation,
                    physicalW,
                    physicalH,
                    session!!.width(),
                    session!!.height(),
                )
            dst[i * 2] = p[0]
            dst[i * 2 + 1] = p[1]
        }
        val forward = Matrix()
        val inverse = Matrix()
        forward.setPolyToPoly(src, 0, dst, 0, 4)
        forward.invert(inverse)
        return inverse
    }

    private fun edgeZones(width: Float, height: Float): FloatArray? {
        val zone = dp(20f).toFloat()
        val edges = floatArrayOf(zone, zone, zone, zone) // top, right, bottom, left
        if (android.os.Build.VERSION.SDK_INT < 31) return edges
        val window = session!!.monitorWindow()!!.getDecorView().getRootWindowInsets()
        if (window == null) return edges
        val bars =
            window!!.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
        val scale =
            Math.min(
                width /
                    (if (session!!.rotation % 2 == 0) session!!.width() else session!!.height()),
                height /
                    (if (session!!.rotation % 2 == 0) session!!.height() else session!!.width()),
            )
        val contentW =
            scale * (if (session!!.rotation % 2 == 0) session!!.width() else session!!.height())
        val contentH =
            scale * (if (session!!.rotation % 2 == 0) session!!.height() else session!!.width())
        val physical =
            floatArrayOf(
                Math.max(0f, bars.top - (height - contentH) / 2),
                Math.max(0f, bars.right - (width - contentW) / 2),
                Math.max(0f, bars.bottom - (height - contentH) / 2),
                Math.max(0f, bars.left - (width - contentW) / 2),
            )
        for (i in 0..3) edges[i] += physical!![(i + session!!.rotation) % 4] / scale
        return edges
    }

    fun physical(event: MotionEvent, width: Float, height: Float): Boolean {
        if (
            (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) &&
                !event.isFromSource(InputDevice.SOURCE_MOUSE))
        )
            return false
        val action = event.getActionMasked()
        val p =
            Geometry.unmap(
                event.getX(),
                event.getY(),
                session!!.rotation,
                width,
                height,
                session!!.width(),
                session!!.height(),
            )
        if (action == MotionEvent.ACTION_DOWN) {
            cancelAll()
            session!!.hideMonitorPointer()
            physicalDown = event.getEventTime()
            physicalUi = (isOpen || transitioning || (p != null && hit(p!![0], p!![1])))
            outsideTouch =
                ((isOpen || transitioning) &&
                    (p == null || panel == null || !bounds(panel!!).contains(p!![0], p!![1])))
            outsideX = event.getX()
            outsideY = event.getY()
            outsideEligible = outsideTouch
            if (physicalUi) {
                session!!.cancelPhoneInput()
                session!!.cancelMonitorGesture()
            }
            if (
                (p != null &&
                    event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) &&
                    session!!.monitorNavigationEnabled() &&
                    !session!!.busy)
            ) {
                startX = p!![0]
                startY = p!![1]
                val zones = edgeZones(width, height)
                candidate =
                    if (startY < zones!![0]) "Controls"
                    else if (startY > session!!.height() - zones!![2]) "Home"
                    else if (startX < zones!![3]) "Left Back"
                    else if (startX > session!!.width() - zones!![1]) "Right Back" else ""
                if (!candidate!!.isEmpty()) {
                    session!!.log("Monitor edge candidate: " + candidate!!)
                    feedback!!.start(candidate, startX, startY)
                }
            }
        }
        if (consuming) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                consuming = false
                physicalUi = false
                val actionName = pendingEdge
                pendingEdge = ""
                if (action == MotionEvent.ACTION_UP) commitEdge(actionName!!)
                else feedback!!.finish(false)
            }
            return true
        }
        if (!candidate!!.isEmpty()) {
            if (
                (event.getPointerCount() != 1 ||
                    p == null ||
                    action == MotionEvent.ACTION_CANCEL ||
                    event.getEventTime() - physicalDown > 1500)
            ) {
                session!!.log(
                    ("Monitor edge rejected: " +
                        candidate +
                        " (multi-touch, Fit bar, timeout, or cancel)")
                )
                candidate = ""
                feedback!!.finish(false)
            } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                val dx = p!![0] - startX
                val dy = p!![1] - startY
                val vertical = candidate == "Home" || candidate == "Controls"
                val inward =
                    if (candidate == "Home") -dy
                    else if (candidate == "Controls") dy
                    else if (candidate == "Right Back") -dx else dx
                val cross = if (vertical) Math.abs(dx) else Math.abs(dy)
                feedback!!.progress(inward / Math.max(1, dp(60f)))
                if (inward >= dp(60f) && inward >= cross * 1.4f) {
                    val recognized = candidate
                    candidate = ""
                    session!!.cancelMonitorGesture()
                    if (physicalUi) {
                        val cancel = MotionEvent.obtain(event)
                        cancel!!.setAction(MotionEvent.ACTION_CANCEL)
                        dispatchPhysical(cancel, width, height)
                        cancel!!.recycle()
                    }
                    session!!.log(
                        ("Monitor edge recognized: " +
                            recognized +
                            " dx=" +
                            Math.round(dx) +
                            " dy=" +
                            Math.round(dy))
                    )
                    pendingEdge = recognized
                    consuming = action != MotionEvent.ACTION_UP
                    if (!consuming) {
                        pendingEdge = ""
                        commitEdge(recognized!!)
                    }
                    physicalUi = false
                    return true
                }
                if (cross > dp(30f) && cross > Math.abs(inward) * 1.4f) {
                    session!!.log("Monitor edge rejected: " + candidate + " (perpendicular)")
                    candidate = ""
                    feedback!!.finish(false)
                } else if (action == MotionEvent.ACTION_UP) {
                    session!!.log("Monitor edge rejected: " + candidate + " (short or outward)")
                    candidate = ""
                    feedback!!.finish(false)
                }
            }
        }
        val owned = physicalUi
        if (owned) {
            if (outsideTouch) {
                val distance =
                    Math.hypot(
                            (event.getX() - outsideX).toDouble(),
                            (event.getY() - outsideY).toDouble(),
                        )
                        .toFloat()
                if (event.getPointerCount() != 1 || distance > dp(10f) * root!!.getScaleX())
                    outsideEligible = false
                if (action == MotionEvent.ACTION_UP && outsideEligible) dismissPanel()
            } else dispatchPhysical(event, width, height)
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
            physicalUi = false
        return owned
    }

    private fun dispatchPhysical(original: MotionEvent?, w: Float, h: Float) {
        if (page == "closed" && transitioning) return
        val mapped = MotionEvent.obtain(original)
        mapped!!.transform(inverse(w, h))
        root!!.dispatchTouchEvent(mapped)
        mapped!!.recycle()
    }

    /** Hardware hover, mouse buttons and wheel use the same full-output shell hit model. */
    fun physicalGeneric(event: MotionEvent, width: Float, height: Float): Boolean {
        if (
            Geometry.unmap(
                event.x,
                event.y,
                session!!.rotation,
                width,
                height,
                session!!.width(),
                session!!.height(),
            ) == null
        )
            return false
        val mapped = MotionEvent.obtain(event)
        try {
            mapped.transform(inverse(width, height))
            return controllerPointer(mapped)
        } finally {
            mapped.recycle()
        }
    }

    fun controllerPointer(event: MotionEvent): Boolean {
        val action = event.getActionMasked()
        val generic =
            (action == MotionEvent.ACTION_SCROLL ||
                action == MotionEvent.ACTION_HOVER_ENTER ||
                action == MotionEvent.ACTION_HOVER_MOVE ||
                action == MotionEvent.ACTION_HOVER_EXIT ||
                action == MotionEvent.ACTION_BUTTON_PRESS ||
                action == MotionEvent.ACTION_BUTTON_RELEASE)
        if (generic) {
            if (!hit(event.getX(), event.getY())) return false
            if (page != "closed" || !transitioning) root!!.dispatchGenericMotionEvent(event)
            return true
        }
        if (action == MotionEvent.ACTION_DOWN) phoneUi = hit(event.getX(), event.getY())
        if (!phoneUi) return false
        if (page != "closed" || !transitioning) root!!.dispatchTouchEvent(event)
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) phoneUi = false
        return true
    }

    fun phone(action: Int, xs: FloatArray?, ys: FloatArray?): Boolean {
        val masked = action and 255
        if (masked == MotionEvent.ACTION_DOWN) {
            phoneUi = hit(xs!![0], ys!![0])
            phoneDown = SystemClock.uptimeMillis()
        }
        if (!phoneUi) return false
        if (page == "closed" && transitioning) {
            if (masked == MotionEvent.ACTION_UP || masked == MotionEvent.ACTION_CANCEL)
                phoneUi = false
            return true
        }
        val event = event(phoneDown, action, xs!!, ys)
        root!!.dispatchTouchEvent(event)
        event!!.recycle()
        if (masked == MotionEvent.ACTION_UP || masked == MotionEvent.ACTION_CANCEL) phoneUi = false
        return true
    }

    fun wheel(x: Float, y: Float, dx: Float, dy: Float): Boolean {
        if (!hit(x, y)) return false
        val prop = MotionEvent.PointerProperties()
        prop.id = 0
        prop.toolType = MotionEvent.TOOL_TYPE_MOUSE
        val coords = MotionEvent.PointerCoords()
        coords.x = x
        coords.y = y
        coords.setAxisValue(MotionEvent.AXIS_HSCROLL, dx)
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL, dy)
        val now = SystemClock.uptimeMillis()
        val event =
            MotionEvent.obtain(
                now,
                now,
                MotionEvent.ACTION_SCROLL,
                1,
                arrayOf<MotionEvent.PointerProperties?>(prop),
                arrayOf<MotionEvent.PointerCoords?>(coords),
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_MOUSE,
                0,
            )
        root!!.dispatchGenericMotionEvent(event)
        event!!.recycle()
        return true
    }

    private fun event(down: Long, action: Int, xs: FloatArray, ys: FloatArray?): MotionEvent? {
        val props = arrayOfNulls<MotionEvent.PointerProperties?>(xs.size)
        val coords = arrayOfNulls<MotionEvent.PointerCoords?>(xs.size)
        for (i in xs.indices) {
            props[i] = MotionEvent.PointerProperties()
            props[i]!!.id = i
            props[i]!!.toolType = MotionEvent.TOOL_TYPE_FINGER
            coords[i] = MotionEvent.PointerCoords()
            coords[i]!!.x = xs[i]
            coords[i]!!.y = ys!![i]
            coords[i]!!.pressure = 1f
        }
        return MotionEvent.obtain(
            down,
            SystemClock.uptimeMillis(),
            action,
            xs.size,
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
    }

    fun cancelPhone() {
        if (phoneUi) {
            val cancel =
                event(phoneDown, MotionEvent.ACTION_CANCEL, floatArrayOf(0f), floatArrayOf(0f))
            root!!.dispatchTouchEvent(cancel)
            cancel!!.recycle()
        }
        phoneUi = false
    }

    private fun commitEdge(action: String) {
        if (action.isEmpty()) return
        val display = session!!.id
        feedback!!.finish(true)
        // Finish the physical UP dispatch before a trusted desktop receives keys.
        // Otherwise Android can cancel the key while changing focus between displays.
        root!!.postOnAnimation({
            if (session!!.id != display || session!!.monitorShellView() !== root)
                return@postOnAnimation
            session!!.log("Monitor edge committed: " + action)
            if (action == "Home") home()
            else if (action == "Controls") show("controls")
            else if (isOpen) back() else session!!.key(KeyEvent.KEYCODE_BACK)
        })
    }

    fun cancelAll() {
        cancelPhone()
        if (physicalUi) {
            val cancel =
                event(physicalDown, MotionEvent.ACTION_CANCEL, floatArrayOf(0f), floatArrayOf(0f))
            root!!.dispatchTouchEvent(cancel)
            cancel!!.recycle()
        }
        physicalUi = false
        outsideTouch = false
        consuming = false
        candidate = ""
        pendingEdge = ""
        feedback!!.clear()
    }

    fun destroy() {
        settingsSearch?.close()
        settingsSearch = null
        settingsQuery = ""
        session!!.invalidateNativeKeyboardMenuFocus(this)
        capabilitiesPanel?.close()
        capabilitiesPanel = null
        navigation.clear()
        scrollPositions.clear()
        page = "closed"
        finishAppSelection()
        stopCatalog()
        dismissSearchKeyboard("destroy")
        cancelAll()
        transitionSerial++
        transitioning = false
        if (panel != null) panel!!.animate()!!.cancel()
        handler.removeCallbacks(tick)
    }

    fun home() {
        navigation.clear()
        scrollPositions.clear()
        launcherQuery = ""
        settingsQuery = ""
        discardViewState = true
        chooseApp(0)
    }

    private fun finishAppSelection() {
        if (launcherActions != null) launcherActions!!.close()
        launcherActions = null
    }

    fun chooseApp(pane: Int) {
        val opening = !isOpen
        if (session!!.splitEnabled()) session!!.cancelInput()
        finishAppSelection()
        launcherActions = LauncherControls(session!!, context, pane)
        if (!launcherActions!!.current()) return
        navigation.open("home")
        page = "home"
        build()
        enter()
        if (opening) session!!.requestNativeKeyboardMenuFocus(this)
        session!!.log("Monitor Home: launcher opened")
    }

    fun show(next: String) {
        val opening = !isOpen
        if (next == "shizuku") session!!.refreshShizukuState()
        if (next == "controls") {
            navigation.clear()
            scrollPositions.clear()
            launcherQuery = ""
            settingsQuery = ""
            discardViewState = true
        }
        if (next != "home" && next != "app-rotation") finishAppSelection()
        if (session!!.splitEnabled()) session!!.cancelInput()
        navigation.open(next)
        page = next
        build()
        enter()
        if (opening) session!!.requestNativeKeyboardMenuFocus(this)
        session!!.log("Monitor panel opened: " + next)
    }

    fun close() {
        settingsSearch?.close()
        settingsSearch = null
        session!!.invalidateNativeKeyboardMenuFocus(this)
        capabilitiesPanel?.close()
        capabilitiesPanel = null
        finishAppSelection()
        if (page == "closed") return
        stopCatalog()
        val old = page
        dismissSearchKeyboard("close")
        root!!.clearFocus()
        navigation.clear()
        scrollPositions.clear()
        launcherQuery = ""
        settingsQuery = ""
        discardViewState = true
        page = "closed"
        handler.removeCallbacks(tick)
        if (panel == null) {
            build()
            return
        }
        val serial = ++transitionSerial
        transitioning = true
        panel!!.animate()!!.cancel()
        if (scrim != null) scrim!!.animate()!!.alpha(0f).setDuration(160).start()
        panel!!
            .animate()!!
            .alpha(0f)
            .translationY(dp((if (old == "home") 24 else -24).toFloat()).toFloat())
            .scaleX(.98f)
            .scaleY(.98f)
            .setDuration(160)
            .setInterpolator(android.view.animation.PathInterpolator(.4f, 0f, 1f, 1f))
            .withEndAction({ if (serial == transitionSerial) build() })
            .start()
        session!!.log("Monitor transition dismiss: " + old!!)
    }

    private fun canReturn(next: String): Boolean {
        return (next != "closed" &&
            ((!(next == "home" || next == "app-rotation") ||
                (launcherActions != null && launcherActions!!.current()))))
    }

    private fun dismissPanel() {
        close()
    }

    private fun hideLocalKeyboard(): Boolean {
        var keyboard = root!!.findViewWithTag<View?>("monitor-search-keyboard")
        if (keyboard == null || keyboard!!.getVisibility() != View.VISIBLE)
            keyboard = root!!.findViewWithTag<View?>("profile-keyboard")
        if (keyboard == null || keyboard!!.getVisibility() != View.VISIBLE) return false
        val focused = root!!.findFocus()
        if (focused != null) hideSystemKeyboard(focused!!)
        keyboard!!.setVisibility(View.GONE)
        root!!.requestFocus()
        return true
    }

    fun back() {
        if (!hideLocalKeyboard()) navigateBack()
    }

    private fun navigateBack() {
        val next = navigation.back(Predicate<String?>({ this.canReturn(it) }))
        if (next == null) {
            close()
            return
        }
        if (next != "home" && next != "app-rotation") finishAppSelection()
        page = next
        build()
        enter()
        session!!.log("Monitor panel Back: " + page!!)
    }

    private fun enter() {
        if (panel == null) return
        val serial = ++transitionSerial
        transitioning = true
        panel!!.setAlpha(0f)
        panel!!.setTranslationY(dp((if (page == "home") 24 else -24).toFloat()).toFloat())
        panel!!.setScaleX(.97f)
        panel!!.setScaleY(.97f)
        if (scrim != null) {
            scrim!!.setAlpha(0f)
            scrim!!.animate()!!.alpha(1f).setDuration(200).start()
        }
        panel!!
            .animate()!!
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .setInterpolator(android.view.animation.PathInterpolator(.2f, 0f, 0f, 1f))
            .withEndAction({ if (serial == transitionSerial) transitioning = false })
            .start()
        session!!.log("Monitor transition enter: " + page!!)
    }

    private fun surface(shade: Int): android.graphics.drawable.Drawable {
        return style!!.surface(shade, 12, 40)
    }

    private fun text(value: String?, size: Int): TextView {
        return style!!.label(value, size)
    }

    private fun column(): LinearLayout {
        val view = LinearLayout(context)
        view.setOrientation(LinearLayout.VERTICAL)
        return view
    }

    private fun section(parent: LinearLayout, value: String?) {
        val view = text(value, 10)
        view.setTextColor(Color.rgb(145, 145, 145))
        view.setLetterSpacing(.14f)
        view.setPadding(dp(4f), dp(14f), 0, dp(6f))
        parent.addView(view)
    }

    private fun button(
        row: LinearLayout?,
        label: String?,
        tag: String?,
        action: Runnable?,
    ): Button {
        val b = style!!.button(row!!, label, { if (isOpen) action!!.run() })
        b.setTag(tag)
        return b
    }

    private fun iconButton(
        row: LinearLayout?,
        icon: Int,
        label: String?,
        tag: String?,
        action: Runnable?,
    ): Button {
        val b = style!!.iconButton(row, icon, label, { if (isOpen) action!!.run() })
        b.setTag(tag)
        return b
    }

    private fun icon(icon: Int, name: String?, tag: String?, action: Runnable?): ImageButton {
        val b = style!!.menuIconAction(icon, name, { if (isOpen) action!!.run() })
        b.setTag(tag)
        return b
    }

    private fun row(parent: LinearLayout): LinearLayout {
        val row = LinearLayout(context)
        parent.addView(row, LinearLayout.LayoutParams(-1, -2))
        return row
    }

    private fun item(
        parent: LinearLayout?,
        label: String?,
        tag: String?,
        value: java.util.function.Supplier<String?>?,
        action: Runnable?,
        icon: Int = 0,
    ) {
        val line = style!!.settingRow(parent!!, icon)
        line.setTag(tag)
        val labels = column()
        line.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        labels.addView(text(label, 14))
        val current = text("", 12)
        current.setTextColor(Color.rgb(155, 155, 155))
        current.setPadding(0, dp(4f), 0, 0)
        labels.addView(current)
        val arrow = ImageView(context)
        arrow.setImageResource(R.drawable.ic_chevron)
        arrow.tag = "menu-action-chevron"
        arrow.setImageTintList(android.content.res.ColorStateList.valueOf(Color.GRAY))
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        arrow.setPadding(dp(4f), dp(8f), dp(4f), dp(8f))
        line.addView(arrow, LinearLayout.LayoutParams(dp(32f), dp(40f)))
        updates.add({
            current.setText(value!!.get())
            line.setContentDescription(label + ", " + value!!.get())
        })
        line.setOnClickListener({ v ->
            if (isOpen) {
                action!!.run()
                refresh()
            }
        })
    }

    private fun toggle(
        parent: LinearLayout,
        label: String?,
        tag: String?,
        enabled: BooleanSupplier?,
        action: Consumer<Boolean?>?,
        icon: Int = 0,
    ) {
        val line = style!!.settingRow(parent, icon)
        val labels = column()
        labels.addView(text(label, 14))
        val current =
            text("", 12).apply {
                setTextColor(Color.rgb(155, 155, 155))
                setPadding(0, dp(4f), 0, 0)
            }
        labels.addView(current)
        line.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        val view = Switch(context)
        view.setTag(tag)
        view.setContentDescription(label)
        view.setThumbTintList(
            android.content.res.ColorStateList(
                arrayOf<IntArray?>(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.WHITE, Color.GRAY),
            )
        )
        view.setTrackTintList(
            android.content.res.ColorStateList(
                arrayOf<IntArray?>(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.rgb(95, 95, 95), Color.rgb(45, 45, 45)),
            )
        )
        line.addView(view, LinearLayout.LayoutParams(dp(52f), dp(48f)))
        line.setOnClickListener { view.toggle() }
        updates.add({
            current.text =
                if (tag == "monitor-reverse") {
                    if (enabled!!.getAsBoolean()) "Reversed" else "Normal"
                } else if (enabled!!.getAsBoolean()) "On" else "Off"
            view.setChecked(enabled!!.getAsBoolean())
        })
        view.setOnCheckedChangeListener({ b, on ->
            if (!updating && isOpen) {
                action!!.accept(on)
                refresh()
            }
        })
    }

    private fun appName(): String? {
        if (session!!.activePackage!!.isEmpty()) return "No app selected"
        if (session!!.activePackage == session!!.getPackageName()) return "DexDroid input test"
        try {
            return session!!
                .getPackageManager()!!
                .getApplicationLabel(
                    session!!.getPackageManager()!!.getApplicationInfo(session!!.activePackage!!, 0)
                )
                .toString()
        } catch (e: Exception) {
            return session!!.activePackage
        }
    }

    private fun speed(): String {
        return if (session!!.scrollSpeed() < .35f) "Slow 20%"
        else if (session!!.scrollSpeed() < .75f) "Normal 50%" else "Fast 100%"
    }

    /** The ROM's system IME is hidden behind Presentation and cannot follow its rotation. */
    private inner class SearchEditor(context: Context?) : EditText(context) {
        var ownerPage = "home"

        init {
            setShowSoftInputOnFocus(false)
            setImeOptions(
                (android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH or
                    android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI)
            )
        }

        public override fun onCreateInputConnection(
            info: android.view.inputmethod.EditorInfo?
        ): android.view.inputmethod.InputConnection? {
            return null
        }

        public override fun onKeyPreIme(code: Int, event: KeyEvent?): Boolean {
            if (code == KeyEvent.KEYCODE_BACK && searchEditor == this && page == ownerPage) {
                session!!.log(
                    ("Monitor search Back: " +
                        event!!.getAction() +
                        " cancelled=" +
                        event!!.isCanceled())
                )
                if (event!!.getAction() == KeyEvent.ACTION_UP && !event!!.isCanceled())
                    session!!.key(KeyEvent.KEYCODE_BACK)
                return true
            }
            return super.onKeyPreIme(code, event)
        }

        fun requestKeyboard() {
            if (searchEditor != this || page != ownerPage || searchKeyboard == null) return
            requestFocus()
            hideSystemKeyboard(this)
            if (searchKeyboard!!.getVisibility() != View.VISIBLE) {
                searchKeyboard!!.showWithin(
                    if (ownerPage == "settings") contentScroll!!.height else searchResults!!.height
                )
                session!!.log("Monitor search keyboard shown")
            }
        }

        public override fun onTouchEvent(event: MotionEvent?): Boolean {
            val handled = super.onTouchEvent(event)
            if (handled && event!!.getActionMasked() == MotionEvent.ACTION_UP) performClick()
            return handled
        }

        public override fun performClick(): Boolean {
            super.performClick()
            requestKeyboard()
            return true
        }
    }

    private fun hideSystemKeyboard(editor: View) {
        (editor.getContext()!!.getSystemService(Context.INPUT_METHOD_SERVICE!!)
                as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(editor.getWindowToken(), 0)
    }

    private fun dismissSearchKeyboard(reason: String?) {
        val editor = searchEditor
        if (editor == null) return
        hideSystemKeyboard(editor!!)
        if (searchKeyboard != null && searchKeyboard!!.getVisibility() == View.VISIBLE) {
            searchKeyboard!!.setVisibility(View.GONE)
            session!!.log("Monitor search keyboard hidden: " + reason!!)
        }
        editor!!.clearFocus()
        searchEditor = null
        searchKeyboard = null
        searchResults = null
    }

    private fun status(parent: LinearLayout) {
        val card =
            ControllerStyle.StatusCard(
                context,
                { session },
                { show("protected-help") },
                "monitor",
                {
                    if (session!!.desktopRequested() || session!!.id >= 0 || session!!.busy)
                        session!!.pauseDesktop()
                    else session!!.retryDesktop()
                },
                { show("shizuku") },
            )
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, dp(4f), 0, dp(10f))
        parent.addView(card, lp)
        updates.add(Runnable({ card.refresh() }))
    }

    private fun launcher(parent: LinearLayout?) {
        if (launcherActions == null) return
        val launcher = launcherActions
        launcher!!.addModes(parent!!)
        updates.add(Runnable({ launcher!!.refresh() }))
        val apps = ArrayList<AppCatalog.Entry?>()
        val filtered = ArrayList<AppCatalog.Entry?>()
        val catalog = session!!.appCatalog
        val search = SearchEditor(context)
        searchEditor = search
        search.setTag("monitor-app-search")
        style!!.search(search)
        search.setHint("Search installed apps")
        parent!!.addView(search)
        val hint = text("", 12)
        hint.setPadding(0, dp(10f), 0, dp(10f))
        hint.setTag("monitor-app-target")
        hint.setTextColor(Color.LTGRAY)
        hint.setText(
            if (session!!.splitEnabled()) "Choose Left/Right or Up/Down beside an app"
            else "Tap to launch · Hold for a one-time orientation"
        )
        parent!!.addView(hint)
        val loading = style!!.CatalogStatus("monitor-app-loading")
        parent!!.addView(loading, LinearLayout.LayoutParams(-1, -2))
        val list = ListView(context)
        searchResults = list
        list.setTag("monitor-app-list")
        style!!.appList(list)
        val adapter =
            object : BaseAdapter() {
                public override fun getCount(): Int {
                    return filtered.size
                }

                public override fun getItem(p: Int): Any? {
                    return filtered.get(p)
                }

                public override fun getItemId(p: Int): Long {
                    return p.toLong()
                }

                public override fun getView(p: Int, old: View?, group: ViewGroup?): View {
                    val app = filtered.get(p)
                    return launcher!!.appRow(
                        app!!,
                        (if (
                            session!!.prefs!!.contains("app." + app!!.component!!.getPackageName())
                        )
                            rotations!![
                                session!!
                                    .prefs!!
                                    .getInt("app." + app!!.component!!.getPackageName(), 0)]
                        else "Session orientation"),
                        Runnable({ this@MonitorShell.close() }),
                    )
                }
            }
        list.setAdapter(adapter)
        launcher!!.onChanged(Runnable({ adapter.notifyDataSetChanged() }))
        updates.add({
            hint.setText(
                if (session!!.busy) "Updating layout…"
                else if (session!!.splitEnabled()) "Choose Left/Right or Up/Down beside an app"
                else "Tap to launch · Hold for a one-time orientation"
            )
        })
        parent!!.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        searchKeyboard =
            MonitorSearchKeyboard(
                context,
                search,
                { session!!.log("Monitor search keyboard hidden: Hide") },
            )
        parent!!.addView(searchKeyboard, LinearLayout.LayoutParams(-1, -2))
        list.setOnItemClickListener({ p, v, index, id ->
            val app = filtered.get(index)
            session!!.log("Monitor launcher selection: " + app!!.component!!.flattenToShortString())
            if (!session!!.splitEnabled() && launcher!!.launch(app!!.component, null, 0)) close()
        })
        list.setOnItemLongClickListener({ p, v, index, id ->
            if (session!!.splitEnabled() || !launcher!!.current())
                return@setOnItemLongClickListener true
            val app = filtered.get(index)
            pendingApp = app!!.component
            pendingAppName = app!!.name
            show("app-rotation")
            true
        })
        search.addTextChangedListener(
            object : android.text.TextWatcher {
                public override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                public override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    val query = s!!.toString().lowercase(Locale.ROOT!!)
                    filtered.clear()
                    for (app in apps) if (
                        (app!!.name!!.lowercase(Locale.ROOT!!).contains(query) ||
                            app!!
                                .component!!
                                .getPackageName()
                                .lowercase(Locale.ROOT!!)
                                .contains(query))
                    )
                        filtered.add(app)
                    adapter.notifyDataSetChanged()
                }

                public override fun afterTextChanged(value: android.text.Editable?) {}
            }
        )
        catalogCleanup =
            catalog!!.observe({ state ->
                if (page != "home" || searchEditor != search) return@observe
                loading.update(state!!, Runnable({ catalog!!.invalidate() }))
                val position = list.onSaveInstanceState()
                apps.clear()
                apps.addAll(state!!.apps!!)
                val query = search.getText()!!.toString()!!.lowercase(Locale.ROOT!!)
                filtered.clear()
                for (app in apps) if (
                    (app!!.name!!.lowercase(Locale.ROOT!!).contains(query) ||
                        app!!.component!!.getPackageName().lowercase(Locale.ROOT!!).contains(query))
                )
                    filtered.add(app)
                adapter.notifyDataSetChanged()
                list.onRestoreInstanceState(position)
            })
        search.setText(launcherQuery)
    }

    private fun stopCatalog() {
        if (catalogCleanup != null) {
            catalogCleanup!!.run()
            catalogCleanup = null
        }
    }

    private fun build() {
        if (context == null) return
        session!!.syncNativeKeyboardHost(this)
        if (!discardViewState) {
            if (contentScroll != null)
                scrollPositions.put(renderedPage, contentScroll!!.getScrollY())
            if (searchEditor != null) {
                if (renderedPage == "settings") settingsQuery = searchEditor!!.text.toString()
                else if (renderedPage == "home") launcherQuery = searchEditor!!.text.toString()
            }
        }
        discardViewState = false
        contentScroll = null
        renderedPage = page
        stopCatalog()
        settingsSearch?.close()
        settingsSearch = null
        dismissSearchKeyboard("menu or display rebuild")
        handler.removeCallbacks(tick)
        transitionSerial++
        transitioning = false
        if (panel != null) panel!!.animate()!!.cancel()
        updates.clear()
        capabilitiesPanel?.close()
        capabilitiesPanel = null
        root!!.removeAllViews()
        root!!.setBackgroundColor(Color.TRANSPARENT)
        root!!.setOnClickListener({ v -> if (isOpen) dismissPanel() })
        root!!.setClickable(isOpen)
        panel = null
        scrim = null
        idle = IdleDesktop(context, Runnable({ this.home() }))
        idle!!.setVisibility(
            if (page == "closed" && !session!!.splitEnabled() && !session!!.hasDesktopApp())
                View.VISIBLE
            else View.GONE
        )
        root!!.addView(idle, FrameLayout.LayoutParams(session!!.appWidth(), session!!.appHeight()))
        destination = DestinationHighlight(context, session)
        root!!.addView(
            destination,
            FrameLayout.LayoutParams(session!!.appWidth(), session!!.appHeight()),
        )
        taskbar = MonitorTaskbar(context!!, Runnable { home() }, Runnable { show("controls") })
        root!!.addView(taskbar, taskbarLayout())
        launchNotice = LaunchNotice(context!!, style!!).apply { tag = "monitor-launch-notice" }
        root.addView(
            launchNotice,
            FrameLayout.LayoutParams(
                    minOf(dp(400f), (session!!.width() - dp(32f)).coerceAtLeast(0)),
                    -2,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                )
                .apply { topMargin = dp(16f) },
        )
        protectionNotice =
            style!!.protectedNotice(
                "monitor",
                {
                    protectionDismissed = true
                    show("protected-help")
                },
                {
                    protectionDismissed = true
                    protectionNotice!!.setVisibility(View.GONE)
                },
            )
        val noticeLayout =
            FrameLayout.LayoutParams(
                Math.min(dp(400f), session!!.width() - dp(48f)),
                -2,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            )
        noticeLayout.bottomMargin = dp(24f) + session!!.viewport().barHeight
        root!!.addView(protectionNotice, noticeLayout)
        if (page != "closed") {
            scrim = View(context)
            scrim!!.setTag("monitor-scrim")
            scrim!!.setBackgroundColor(Color.argb(140, 0, 0, 0))
            scrim!!.setOnClickListener { dismissPanel() }
            root!!.addView(scrim, FrameLayout.LayoutParams(-1, -1))
            panel = column()
            panel!!.setTag("monitor-panel")
            panel!!.setClickable(true)
            panel!!.setPadding(dp(14f), dp(6f), dp(14f), dp(12f))
            panel!!.setBackground(style!!.surface(8, 14, 40))
            if (page != "controls") style!!.menuPanel(panel!!)
            panel!!.setTag("monitor-panel")
            val height =
                if (page == "controls") -2 else Math.min(dp(560f), session!!.appHeight() - dp(48f))
            val lp =
                FrameLayout.LayoutParams(
                    Math.min(dp(400f), session!!.width() - dp(48f)),
                    height,
                    Gravity.CENTER,
                )
            lp.bottomMargin = session!!.viewport().barHeight
            root!!.addView(panel, lp)
            val header = row(panel!!)
            header.setGravity(Gravity.CENTER_VERTICAL)
            if (navigation.hasParent(Predicate<String?>({ this.canReturn(it) })))
                header.addView(
                    icon(
                        R.drawable.ic_back,
                        "Back",
                        "monitor-up",
                        Runnable({ this.navigateBack() }),
                    ),
                    LinearLayout.LayoutParams(dp(48f), dp(48f)),
                )
            val heading =
                text(
                    if (page == "protected-help") ProtectedScreenGuide.TITLE
                    else if (page == "layout") "Layout"
                    else if (page == "audio") "Audio output"
                    else if (page == "debugging-off") "Turn off Developer Mode"
                    else if (page == "home") "Open on monitor"
                    else if (page == "controls") "DexDroid"
                    else if (page == "rotation") "Monitor orientation"
                    else if (page == "scroll") "Scrolling"
                    else if (page == "app-rotation") pendingAppName
                    else if (page == "display") "Resolution and DPI"
                    else if (page == "profiles") "Monitor profiles"
                    else if (page == "workspace-shortcut") "Workspace shortcut"
                    else if (page == "capabilities") "Device capabilities"
                    else if (page == "diagnostics") "Diagnostic log" else "Settings",
                    if (page == "controls") 23 else 22,
                )
            if (page == "app-settings") heading.setText(R.string.app_settings_title)
            if (page == "pointer-speed") heading.setText(R.string.pointer_speed_title)
            if (page == "shizuku") heading.setText(R.string.shizuku_setup_title)
            if (page == "user-guide") heading.setText(R.string.user_guide_title)
            heading.setTag("monitor-title")
            style!!.menuHeading(heading)
            header.addView(heading, LinearLayout.LayoutParams(0, dp(54f), 1f))
            if (page == "controls") {
                val phoneScreenButton = PhoneScreenButton(style!!, { session }, "monitor")
                header.addView(phoneScreenButton.view, LinearLayout.LayoutParams(dp(48f), dp(48f)))
                updates.add(Runnable { phoneScreenButton.refresh() })
                header.addView(
                    icon(
                        R.drawable.ic_settings,
                        "Settings",
                        "monitor-settings",
                        { show("settings") },
                    ),
                    LinearLayout.LayoutParams(dp(48f), dp(48f)),
                )
            }
            header.addView(
                icon(
                    R.drawable.ic_close,
                    "Close menus",
                    "monitor-close",
                    Runnable({ this.dismissPanel() }),
                ),
                LinearLayout.LayoutParams(dp(48f), dp(48f)),
            )
            if (page != "controls" && page != "profiles") {
                val keep = row(panel!!)
                keep.setTag("monitor-confirmation")
                button(keep, "Keep", "monitor-keep", { session!!.confirm(true) })
                button(keep, "Revert", "monitor-revert", { session!!.confirm(false) })
                keep.setVisibility(
                    if (session!!.awaitingConfirmation()) View.VISIBLE else View.GONE
                )
                updates.add({
                    keep.setVisibility(
                        if (session!!.awaitingConfirmation()) View.VISIBLE else View.GONE
                    )
                })
            }
            if (page == "home") launcher(panel)
            else {
                val scroll = ScrollView(context)
                scroll.setTag("monitor-menu-scroll")
                contentScroll = scroll
                val savedScroll =
                    (scrollPositions as java.util.Map<String?, Int?>?)!!.getOrDefault(page, 0)
                style!!.restoreScroll(scroll, savedScroll!!)
                val body = column()
                scroll.addView(body)
                panel!!.addView(
                    scroll,
                    if (page == "controls") LinearLayout.LayoutParams(-1, -2)
                    else LinearLayout.LayoutParams(-1, 0, 1f),
                )
                if (page == "controls") {
                    status(body)
                    val first = row(body)
                    first.setTag("monitor-row-1")
                    iconButton(
                        first,
                        R.drawable.ic_apps,
                        "Apps",
                        "monitor-apps",
                        Runnable({ this.home() }),
                    )
                    iconButton(
                        first,
                        R.drawable.ic_rotation,
                        "Rotate",
                        "monitor-rotate",
                        { show("rotation") },
                    )
                    iconButton(
                        first,
                        R.drawable.ic_layout,
                        "Layout",
                        "monitor-layout",
                        { show("layout") },
                    )
                    iconButton(
                        first,
                        R.drawable.ic_audio,
                        "Audio",
                        "monitor-audio",
                        { show("audio") },
                    )
                } else if (page == "audio") {
                    val menuOwner = panel
                    val audioPanel =
                        AudioPanel(session!!, style!!, body, "monitor") {
                            if (page == "audio" && panel === menuOwner) navigateBack()
                        }
                    updates.add(Runnable { audioPanel.refresh() })
                } else if (page == "protected-help") {
                    ProtectedScreenGuide.add(style!!, body, "monitor")
                } else if (page == "rotation") {
                    val choices = RadioGroup(context)
                    for (i in 0..3) {
                        style!!.choice(choices, rotations!![i], "monitor-rotation-" + i, 101 + i)
                    }
                    body.addView(choices)
                    choices.check(101 + session!!.rotation)
                    choices.setOnCheckedChangeListener({ g, id ->
                        if (!updating) {
                            session!!.rotate(id - 101, true)
                            navigateBack()
                        }
                    })
                } else if (page == "app-rotation") {
                    for (i in 0..3) {
                        val selected = i
                        item(
                            body,
                            rotations!![i],
                            "monitor-once-" + i,
                            { "This launch only" },
                            {
                                val app = pendingApp
                                if (
                                    (app != null &&
                                        launcherActions != null &&
                                        launcherActions!!.launch(app, selected, 0))
                                )
                                    close()
                            },
                        )
                    }
                } else if (page == "scroll") {
                    if (session!!.activePackage!!.isEmpty())
                        body.addView(text("Select an app before configuring scrolling", 13))
                    else {
                        val name = text(appName()!! + " · Saved automatically", 12)
                        name.setTextColor(Color.LTGRAY)
                        body.addView(name)
                        section(body, "MODE")
                        val modes = RadioGroup(context)
                        for (i in 0..1) {
                            style!!.choice(
                                modes,
                                if (i == 0) "Swipe \u00b7 Touchscreen drag"
                                else "Wheel \u00b7 Mouse scrolling",
                                if (i == 0) "monitor-swipe" else "monitor-wheel",
                                201 + i,
                            )
                        }
                        body.addView(modes)
                        modes.check(if (session!!.swipeScrolling()) 201 else 202)
                        modes.setOnCheckedChangeListener({ g, id ->
                            if (!updating) session!!.setSwipeScrolling(id == 201)
                        })
                        section(body, "SPEED")
                        val rates = RadioGroup(context)
                        for (i in 0..2) {
                            style!!.choice(
                                rates,
                                arrayOf<String?>(
                                    "Slow \u00b7 20%",
                                    "Normal \u00b7 50%",
                                    "Fast \u00b7 100%",
                                )[i],
                                "monitor-speed-" + i,
                                211 + i,
                            )
                        }
                        body.addView(rates)
                        rates.check(
                            if (session!!.scrollSpeed() < .35f) 211
                            else if (session!!.scrollSpeed() < .75f) 212 else 213
                        )
                        rates.setOnCheckedChangeListener({ g, id ->
                            if (!updating) {
                                session!!
                                    .prefs!!
                                    .edit()!!
                                    .putFloat(
                                        "scrollSpeed." + session!!.activePackage!!,
                                        floatArrayOf(.2f, .5f, 1f)[id - 211],
                                    )!!
                                    .apply()
                                session!!.report("Monitor scroll speed saved")
                            }
                        })
                        updates.add({
                            modes.check(if (session!!.swipeScrolling()) 201 else 202)
                            rates.check(
                                if (session!!.scrollSpeed() < .35f) 211
                                else if (session!!.scrollSpeed() < .75f) 212 else 213
                            )
                        })
                    }
                } else if (page == "pointer-speed") {
                    val choices = RadioGroup(context)
                    body.addView(choices)
                    for (i in 0..2) style!!.choice(
                        choices,
                        arrayOf<String?>("Slow", "Normal", "Fast")[i],
                        "monitor-pointer-" + i,
                        301 + i,
                    )
                    val current = session!!.prefs!!.getFloat("speed", 1.6f)
                    choices.check(if (current < 1.3f) 301 else if (current < 2) 302 else 303)
                    choices.setOnCheckedChangeListener({ group, id ->
                        if (updating) return@setOnCheckedChangeListener
                        session!!
                            .prefs!!
                            .edit()!!
                            .putFloat("speed", floatArrayOf(1f, 1.6f, 2.5f)[id - 301])!!
                            .apply()
                        session!!.report("Pointer speed changed")
                        navigateBack()
                    })
                    updates.add({
                        val value = session!!.prefs!!.getFloat("speed", 1.6f)
                        choices.check(if (value < 1.3f) 301 else if (value < 2) 302 else 303)
                    })
                } else if (page == "app-settings") {
                    body.addView(text(appName(), 14))
                    item(
                        body,
                        "Scrolling",
                        "monitor-scroll",
                        {
                            (if (session!!.swipeScrolling()) "Swipe" else "Wheel") + " · " + speed()
                        },
                        { show("scroll") },
                        icon = R.drawable.ic_scroll,
                    )
                    item(
                        body,
                        "Save app settings",
                        "monitor-save",
                        { "Save rotation and current app preferences" },
                        Runnable({ session!!.saveAppSettings() }),
                        icon = R.drawable.ic_save,
                    )
                    item(
                        body,
                        "Reset app settings",
                        "monitor-reset",
                        { "Restore defaults for " + appName()!! },
                        Runnable({ session!!.resetAppSettings() }),
                        icon = R.drawable.ic_reset,
                    )
                } else if (page == "settings") {
                    val fixed = column()
                    panel!!.addView(
                        fixed,
                        panel!!.indexOfChild(scroll),
                        LinearLayout.LayoutParams(-1, -2),
                    )
                    val editor = SearchEditor(context).apply { ownerPage = "settings" }
                    searchEditor = editor
                    val search =
                        SettingsSearchPanel(style!!, editor, fixed, scroll, "monitor") {
                            settingsQuery = it
                        }
                    settingsSearch = search
                    searchKeyboard =
                        MonitorSearchKeyboard(context, editor) {
                            if (page == "settings" && searchEditor === editor) root!!.requestFocus()
                        }
                    panel!!.addView(searchKeyboard, LinearLayout.LayoutParams(-1, -2))
                    item(
                        body,
                        "Audio output",
                        "monitor-audio-settings",
                        { session!!.audioState().route },
                        { show("audio") },
                        icon = R.drawable.ic_audio,
                    )
                    search.bind("audio", body.getChildAt(body.childCount - 1))
                    item(
                        body,
                        "App settings",
                        "monitor-app-settings",
                        java.util.function.Supplier<String?>({ this.appName() }),
                        { show("app-settings") },
                        icon = R.drawable.ic_apps,
                    )
                    search.bind("app", body.getChildAt(body.childCount - 1))
                    item(
                        body,
                        "Workspace shortcut",
                        "monitor-workspace-shortcut",
                        {
                            if (session!!.workspaceShortcut.saved == null) "No workspace saved"
                            else "Saved apps, layout and rotation"
                        },
                        { show("workspace-shortcut") },
                        icon = R.drawable.ic_save,
                    )
                    search.bind("workspace", body.getChildAt(body.childCount - 1))
                    item(
                        body,
                        "Shizuku setup",
                        "monitor-shizuku",
                        { session!!.shizukuState!!.label },
                        { show("shizuku") },
                        icon = R.drawable.ic_setup,
                    )
                    search.bind("shizuku", body.getChildAt(body.childCount - 1))
                    item(
                        body,
                        "Turn off Developer Mode",
                        "monitor-debugging-off",
                        { "Stop desktop and disable debugging" },
                        { show("debugging-off") },
                        icon = R.drawable.ic_stop,
                    )
                    search.bind("developer", body.getChildAt(body.childCount - 1))
                    item(
                        body,
                        "User guide",
                        "monitor-user-guide",
                        { "Setup, controls and troubleshooting" },
                        { show("user-guide") },
                        icon = R.drawable.ic_guide,
                    )
                    search.bind("guide", body.getChildAt(body.childCount - 1))
                    section(body, "DISPLAY")
                    search.section(body.getChildAt(body.childCount - 1))
                    item(
                        body,
                        "Monitor profiles",
                        "monitor-profiles",
                        java.util.function.Supplier<String?>({ session!!.monitorProfileStatus() }),
                        { show("profiles") },
                        icon = R.drawable.ic_display,
                    )
                    search.bind("profiles", body.getChildAt(body.childCount - 1))
                    item(
                        body,
                        "Resolution and DPI",
                        "monitor-display",
                        {
                            (session!!.width().toString() +
                                " × " +
                                session!!.height() +
                                " · " +
                                session!!.dpi +
                                " UI DPI")
                        },
                        { show("display") },
                        icon = R.drawable.ic_display,
                    )
                    search.bind("display", body.getChildAt(body.childCount - 1))
                    val phoneScreenGroup = column()
                    body.addView(phoneScreenGroup)
                    val phoneScreenPanel =
                        PhoneScreenPanel(session!!, style!!, phoneScreenGroup, "monitor")
                    search.bind("phone-screen", phoneScreenGroup)
                    updates.add(Runnable { phoneScreenPanel.refresh() })
                    toggle(
                        body,
                        "Monitor touch",
                        "monitor-touch",
                        { session!!.monitorTouchEnabled() },
                        Consumer<Boolean?>({ session!!.setMonitorTouchEnabled(it) }),
                        icon = R.drawable.ic_touchpad,
                    )
                    search.bind("touch", body.getChildAt(body.childCount - 1))
                    toggle(
                        body,
                        "Monitor edge navigation",
                        "monitor-navigation",
                        { session!!.monitorNavigationEnabled() },
                        Consumer<Boolean?>({ session!!.setMonitorNavigationEnabled(it) }),
                        icon = R.drawable.ic_navigation,
                    )
                    search.bind("navigation", body.getChildAt(body.childCount - 1))
                    toggle(
                        body,
                        "Show center Apps button",
                        "monitor-center-apps",
                        { session!!.centerAppsEnabled() },
                        { enabled -> session!!.setCenterAppsEnabled(enabled!!) },
                        icon = R.drawable.ic_apps,
                    )
                    search.bind("center-apps", body.getChildAt(body.childCount - 1))
                    section(body, "INPUT")
                    search.section(body.getChildAt(body.childCount - 1))
                    val keyboardGroup = column()
                    body.addView(keyboardGroup)
                    val nativeKeyboard =
                        NativeImePanel(session!!, style!!, keyboardGroup, "monitor")
                    search.bind("keyboard", keyboardGroup)
                    updates.add(nativeKeyboard::refresh)
                    item(
                        body,
                        "Pointer speed",
                        "monitor-pointer-speed",
                        {
                            val v = session!!.prefs!!.getFloat("speed", 1.6f)
                            if (v < 1.3) "Slow" else if (v < 2) "Normal" else "Fast"
                        },
                        { show("pointer-speed") },
                        icon = R.drawable.ic_speed,
                    )
                    search.bind("pointer", body.getChildAt(body.childCount - 1))
                    toggle(
                        body,
                        "Scroll direction",
                        "monitor-reverse",
                        { session!!.prefs!!.getBoolean("reverseScroll", false) },
                        { on ->
                            session!!.prefs!!.edit()!!.putBoolean("reverseScroll", on!!)!!.apply()
                            session!!.report("Scroll direction changed from monitor")
                        },
                        icon = R.drawable.ic_scroll,
                    )
                    search.bind("scroll", body.getChildAt(body.childCount - 1))
                    item(
                        body,
                        "Center pointer",
                        "monitor-center",
                        { "Recenter on desktop" },
                        Runnable({ session!!.center() }),
                        icon = R.drawable.ic_pointer,
                    )
                    search.bind("center", body.getChildAt(body.childCount - 1))
                    section(body, "GENERAL")
                    search.section(body.getChildAt(body.childCount - 1))
                    item(
                        body,
                        "Device capabilities",
                        "monitor-capabilities",
                        { "Current readiness and observed results" },
                        { show("capabilities") },
                        icon = R.drawable.ic_display,
                    )
                    search.bind("capabilities", body.getChildAt(body.childCount - 1))
                    item(
                        body,
                        "Protected screen help",
                        "monitor-protected-settings",
                        { "Blank apps and Chrome incognito" },
                        { show("protected-help") },
                        icon = R.drawable.ic_info,
                    )
                    search.bind("protected", body.getChildAt(body.childCount - 1))
                    item(
                        body,
                        "Background operation",
                        "monitor-background",
                        {
                            ((if (session!!.backgroundProtectionEnabled()) "Allowed"
                            else "Restricted") + " · Configure on phone")
                        },
                        {
                            val intent =
                                if (session!!.backgroundProtectionEnabled())
                                    Intent(
                                        android.provider.Settings
                                            .ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                                    )
                                else
                                    Intent(
                                        android.provider.Settings
                                            .ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        android.net.Uri.parse(
                                            ("package:" + session!!.getPackageName()!!)
                                        ),
                                    )
                            val options = ActivityOptions.makeBasic()
                            options!!.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                            session!!.startActivity(
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                options!!.toBundle(),
                            )
                        },
                        icon = R.drawable.ic_background,
                    )
                    search.bind("background", body.getChildAt(body.childCount - 1))
                    item(
                        body,
                        "Diagnostic log",
                        "monitor-diagnostics",
                        { "Gesture and connection events" },
                        { show("diagnostics") },
                        icon = R.drawable.ic_diagnostics,
                    )
                    search.bind("diagnostics", body.getChildAt(body.childCount - 1))
                    item(
                        body,
                        "Reset monitor profile",
                        "monitor-reset-profile",
                        { session!!.monitorProfileStatus() },
                        Runnable({ session!!.resetMonitor() }),
                        icon = R.drawable.ic_reset,
                    )
                    search.bind("reset", body.getChildAt(body.childCount - 1))
                    search.restore(settingsQuery)
                } else if (page == "debugging-off") {
                    val shutdown =
                        DebuggingOffPanel(session!!, style!!, body, "monitor") { navigateBack() }
                    updates.add(Runnable { shutdown.refresh() })
                } else if (page == "shizuku") {
                    val setup = ShizukuSetup.Panel(context, session!!, "monitor")
                    body.addView(setup.view)
                    updates.add(Runnable({ setup.refresh() }))
                } else if (page == "user-guide") {
                    UserGuide.add(style, body)
                    item(
                        body,
                        "Shizuku setup",
                        "monitor-guide-shizuku",
                        { session!!.shizukuState!!.label },
                        { show("shizuku") },
                        icon = R.drawable.ic_setup,
                    )
                    item(
                        body,
                        "Protected screen help",
                        "monitor-guide-protected",
                        { "Blank apps and Chrome incognito" },
                        { show("protected-help") },
                        icon = R.drawable.ic_info,
                    )
                } else if (page == "layout") {
                    val layoutPanel =
                        LayoutPanel(session, context, body, IntConsumer({ this.chooseApp(it) }))
                    updates.add(Runnable({ layoutPanel.refresh() }))
                } else if (page == "profiles") {
                    val profiles = MonitorProfilesPanel(context, session!!, true)
                    body.addView(profiles)
                    updates.add(Runnable({ profiles.refresh() }))
                } else if (page == "display") {
                    body.addView(text(session!!.capabilities(), 12))
                    if (session!!.external != null) {
                        val mode = session!!.external!!.getMode()
                        item(
                            body,
                            "Same as output",
                            "monitor-native",
                            {
                                mode!!.getPhysicalWidth().toString() +
                                    " × " +
                                    mode!!.getPhysicalHeight()
                            },
                            {
                                session!!.preview(
                                    mode!!.getPhysicalWidth(),
                                    mode!!.getPhysicalHeight(),
                                    Geometry.recommendedDpi(
                                        Math.min(
                                            mode!!.getPhysicalWidth(),
                                            mode!!.getPhysicalHeight(),
                                        )
                                    ),
                                )
                            },
                            icon = R.drawable.ic_display,
                        )
                        item(
                            body,
                            "Lower rendering load",
                            "monitor-low",
                            {
                                (Math.round(mode!!.getPhysicalWidth() * 2f / 3).toString() +
                                    " × " +
                                    Math.round(mode!!.getPhysicalHeight() * 2f / 3))
                            },
                            {
                                val w = Math.round(mode!!.getPhysicalWidth() * 2f / 3)
                                val h = Math.round(mode!!.getPhysicalHeight() * 2f / 3)
                                session!!.preview(w, h, Geometry.recommendedDpi(Math.min(w, h)))
                            },
                            icon = R.drawable.ic_display,
                        )
                        for (value in intArrayOf(160, 240, 320)) item(
                            body,
                            "UI DPI " + value,
                            "monitor-dpi-" + value,
                            { if (session!!.dpi == value) "Selected" else "" },
                            { session!!.preview(session!!.baseW, session!!.baseH, value) },
                            icon = R.drawable.ic_display,
                        )
                    }
                } else if (page == "workspace-shortcut") {
                    val panel = WorkspaceShortcutPanel(session!!, style!!, body, "monitor")
                    updates.add { panel.refresh() }
                } else if (page == "diagnostics") {
                    val panel = DiagnosticsPanel(session!!, style!!, body, "monitor")
                    updates.add({ panel.refresh() })
                } else if (page == "capabilities") {
                    val report =
                        CapabilitiesPanel(session!!, style!!, body, "monitor") {
                            show("diagnostics")
                        }
                    capabilitiesPanel = report
                    updates.add { report.refresh() }
                }
            }
        }
        root!!.addView(feedback, FrameLayout.LayoutParams(-1, -1))
        root!!.requestFocus()
        refresh()
        if (isOpen) handler.postDelayed(tick, 750)
    }

    private fun syncIdleVisibility() {
        idle?.setAppsVisible(session!!.centerAppsEnabled())
        if (idle != null)
            idle!!.setVisibility(
                if (
                    (page == "closed" &&
                        !transitioning &&
                        !session!!.splitEnabled() &&
                        !session!!.hasDesktopApp())
                )
                    View.VISIBLE
                else View.GONE
            )
    }

    private fun taskbarLayout(): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(session!!.width(), session!!.viewport().barHeight).apply {
            topMargin = session!!.appHeight()
        }
    }

    /** Apply committed viewport/preferences without rebuilding menus or changing focus. */
    fun relayout() {
        syncViewport()
        refresh()
    }

    private fun syncViewport() {
        val w = session!!.appWidth()
        val h = session!!.appHeight()
        for (view in arrayOf<View?>(idle, destination)) {
            val old = view?.layoutParams ?: continue
            if (old.width != w || old.height != h)
                view.layoutParams = FrameLayout.LayoutParams(w, h)
        }
        taskbar?.let { bar ->
            bar.configureSize(session!!.viewport().barHeight)
            bar.visibility = if (session!!.monitorNavigationEnabled()) View.GONE else View.VISIBLE
            val position = taskbarLayout()
            val old = bar.layoutParams as FrameLayout.LayoutParams
            if (
                old.width != position.width ||
                    old.height != position.height ||
                    old.topMargin != position.topMargin
            )
                bar.layoutParams = position
        }
        panel?.let { menu ->
            val position = menu.layoutParams as FrameLayout.LayoutParams
            val height = if (page == "controls") -2 else Math.min(dp(560f), h - dp(48f))
            if (
                position.height != height || position.bottomMargin != session!!.viewport().barHeight
            ) {
                position.height = height
                position.bottomMargin = session!!.viewport().barHeight
                menu.layoutParams = position
            }
        }
        idle?.setAppsVisible(session!!.centerAppsEnabled())
    }

    // Pane bounds are physical workspace pixels, independent of locale reading order.
    @android.annotation.SuppressLint("RtlHardcoded")
    fun refresh() {
        syncViewport()
        session!!.syncNativeKeyboardHost(this)
        launchNotice?.render(
            if (page == "closed" && !transitioning) session!!.monitorOperationNotice() else null,
            session!!.operationPending(),
        )
        if (destination != null) destination!!.invalidate()
        syncIdleVisibility()
        if (!session!!.protectedContent) protectionDismissed = false
        if (protectionNotice != null) {
            val protectedBox =
                if (session!!.split == null) null else session!!.split!!.protectedBox()
            val position: FrameLayout.LayoutParams?
            if (protectedBox != null) {
                val width = Math.max(dp(120f), Math.min(dp(400f), protectedBox!!.width - dp(24f)))
                position = FrameLayout.LayoutParams(width, -2, Gravity.TOP or Gravity.LEFT)
                position!!.leftMargin = protectedBox!!.x + (protectedBox!!.width - width) / 2
                position!!.topMargin =
                    protectedBox!!.y + Math.max(0, protectedBox!!.height - dp(80f))
            } else {
                position =
                    FrameLayout.LayoutParams(
                        Math.min(dp(400f), session!!.width() - dp(48f)),
                        -2,
                        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                    )
                position!!.bottomMargin = dp(24f) + session!!.viewport().barHeight
            }
            val old = protectionNotice!!.getLayoutParams() as FrameLayout.LayoutParams?
            if (
                (old!!.width != position!!.width ||
                    old!!.gravity != position!!.gravity ||
                    old!!.leftMargin != position!!.leftMargin ||
                    old!!.topMargin != position!!.topMargin ||
                    old!!.bottomMargin != position!!.bottomMargin)
            )
                protectionNotice!!.setLayoutParams(position)
            protectionNotice!!.setVisibility(
                if (
                    (page == "closed" &&
                        session!!.protectedContent &&
                        !protectionDismissed &&
                        (!session!!.splitEnabled() || protectedBox != null))
                )
                    View.VISIBLE
                else View.GONE
            )
        }
        updating = true
        try {
            for (update in updates) update!!.run()
        } finally {
            updating = false
        }
    }
}
