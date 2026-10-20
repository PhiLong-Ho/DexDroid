package dev.dexdroid

import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import java.util.*
import java.util.concurrent.*
import java.util.function.Consumer
import rikka.shizuku.Shizuku

/**
 * Service-owned second display, pane state and bounded input. Views belong to the existing host.
 */
class SplitWorkspace(
    private val session: SessionService?,
    private val layer: FrameLayout?,
    private val primary: TextureView?,
    vertical: Boolean?,
) {
    private val secondary: TextureView?
    private val decoration: Array<FrameLayout?> = arrayOfNulls<FrameLayout?>(2)
    private val empty: Array<IdleDesktop?> = arrayOfNulls<IdleDesktop?>(2)
    private val divider: View?
    private val main: Handler = Handler(Looper.getMainLooper()!!)
    private val input: ThreadPoolExecutor =
        ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable?>(64))
    private val apps: Array<ComponentName?> = arrayOfNulls<ComponentName?>(2)
    private val packages: Array<String?>? = arrayOf<String?>("", "")
    private val occupied: BooleanArray = BooleanArray(2)
    private val protectedPane: BooleanArray = BooleanArray(2)
    private val inputPending: BooleanArray = BooleanArray(2)
    private val keys: MutableMap<Long?, HeldKey?> = HashMap<Long?, HeldKey?>()
    private val cancelledKeys: MutableSet<Long?> = LinkedHashSet<Long?>()
    private val primaryId: Int
    private val args: Shizuku.UserServiceArgs?
    private val connection: ServiceConnection?
    private var layout: SplitLayout? = SplitLayout()
    private var previous: SplitLayout? = null
    private var secondBridge: IDeskService? = null
    @Volatile private var secondId: Int = -1
    private var secondTexture: SurfaceTexture? = null
    private var secondSurface: Surface? = null
    @Volatile private var closed: Boolean = false
    private var bound: Boolean = false
    private var dividerHeld: Boolean = false
    private var dividerPhone: Boolean = false
    private var dividerPreview: Float = 0.toFloat()
    private var physicalContact: MotionEvent? = null
    private var phoneContact: MotionEvent? = null
    private var physicalPane: Int = -1
    private var phonePane: Int = -1
    private var uiPane: Int = -1
    private var uiPhone: Boolean = false
    private var phoneDown: Long = 0
    private var cancelSurfaceLayout: Runnable? = null
    private var stagedSurfaces: Array<SplitLayout.Box?>? = null
    private var stagedLayout: SplitLayout? = null
    private var stagedWidth: Int = 0
    private var stagedHeight: Int = 0
    private var stagedDpi: Int = 0
    private var controlsDpi: Int = -1

    init {
        primary!!.setTag("split-texture-0")
        primaryId = session!!.id
        apps[0] = session!!.activeComponent
        packages!![0] = session!!.activePackage
        occupied[0] = session!!.hasDesktopApp()
        protectedPane[0] = session!!.protectedContent
        layout = session!!.savedSplitLayout()
        previous = layout!!.copy()
        previous!!.enabled = false
        if (vertical != null) layout!!.vertical = vertical!!
        layout!!.enabled = true
        secondary = TextureView(layer!!.getContext()!!)
        secondary!!.setTag("split-texture-1")
        secondary!!.setSurfaceTextureListener(
            object : TextureView.SurfaceTextureListener {
                public override fun onSurfaceTextureAvailable(
                    texture: SurfaceTexture,
                    w: Int,
                    h: Int,
                ) {
                    secondTexture = texture
                    secondSurface = Surface(texture)
                    if (!current()) return
                    bound = true
                    try {
                        Shizuku.bindUserService(args!!, connection!!)
                    } catch (error: RuntimeException) {
                        session!!.busy = false
                        detach(true)
                        session!!.report("Split connection failed: " + error!!.message)
                    }
                }

                public override fun onSurfaceTextureSizeChanged(
                    texture: SurfaceTexture,
                    w: Int,
                    h: Int,
                ) {}

                public override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                    if (!closed && current()) {
                        session!!.stopDesktop()
                        session!!.report("Split surface lost. Reopen DexDroid.")
                    }
                    return false
                }

                public override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
            }
        )
        layer!!.addView(secondary, FrameLayout.LayoutParams(1, 1))
        for (i in 0..1) {
            decoration[i] = FrameLayout(layer!!.getContext()!!)
            decoration[i]!!.setTag("split-pane-" + i)
            layer!!.addView(decoration[i], FrameLayout.LayoutParams(1, 1))
        }
        divider = View(layer!!.getContext())
        divider!!.setTag("split-divider")
        divider!!.setBackgroundColor(Color.rgb(45, 45, 45))
        divider!!.setContentDescription(
            "Resize split panes; use Layout settings for accessible presets"
        )
        divider!!.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        layer!!.addView(divider, FrameLayout.LayoutParams(1, 1))
        args =
            Shizuku.UserServiceArgs(ComponentName(session!!, SecondaryDeskService::class.java!!))
                .tag("split-" + UUID.randomUUID())!!
                .daemon(false)!!
                .processNameSuffix("desktop-secondary")!!
                .debuggable(BuildConfig.DEBUG)!!
                .version(BuildConfig.VERSION_CODE)
        connection =
            object : ServiceConnection {
                public override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (!current()) {
                        if (bound) {
                            bound = false
                            Shizuku.unbindUserService(args!!, this, true)
                        }
                        return
                    }
                    secondBridge = IDeskService.Stub.asInterface(binder)
                    val box = dimensions(1, session!!.width(), session!!.height(), false)
                    session!!.desktopWork({
                        try {
                            val created =
                                secondBridge!!.create(
                                    secondSurface,
                                    box!!.width,
                                    box!!.height,
                                    session!!.dpi,
                                    session!!.splitOwner(),
                                    object : IDesktopListener.Stub() {
                                        public override fun onProtectionState(
                                            display: Int,
                                            state: Int,
                                        ) {
                                            main.post({
                                                if ((current() && (display == secondId)))
                                                    protection(1, state)
                                            })
                                        }

                                        public override fun onTaskState(
                                            display: Int,
                                            value: Boolean,
                                            component: String?,
                                            error: String?,
                                        ) {
                                            main.post({
                                                if ((current() && (display == secondId)))
                                                    occupancy(1, value, component, error)
                                            })
                                        }
                                    },
                                )
                            main.post({
                                if (!current()) return@post
                                secondId = created
                                session!!.reconfigureSplit()
                            })
                        } catch (error: Exception) {
                            main.post({
                                if (current()) {
                                    session!!.busy = false
                                    detach(true)
                                    session!!.report(("Split setup failed: " + error!!.message))
                                }
                            })
                        }
                    })
                }

                public override fun onServiceDisconnected(name: ComponentName?) {
                    if (current()) {
                        session!!.stopDesktop()
                        session!!.report("Split connection lost. Reopen DexDroid.")
                    }
                }
            }
        main.postDelayed(
            {
                if (current() && secondId < 0) {
                    session!!.busy = false
                    detach(true)
                    session!!.report("Split setup timed out. Single view restored.")
                }
            },
            10000,
        )
    }

    fun current(): Boolean {
        return !closed && session!!.split == this && session!!.id == primaryId
    }

    fun state(): SplitLayout {
        return layout!!.copy()
    }

    fun selected(): Int {
        return layout!!.selected
    }

    fun displayId(pane: Int): Int {
        return if (pane == 0) primaryId else secondId
    }

    fun ownsDisplay(id: Int): Boolean {
        return id == primaryId || (secondId >= 0 && id == secondId)
    }

    fun bridge(pane: Int): IDeskService? {
        return if (pane == 0) session!!.primaryBridge() else secondBridge
    }

    fun selectedPackage(): String? {
        return packages!![layout!!.selected]
    }

    fun selectedComponent(): ComponentName? {
        return apps[layout!!.selected]
    }

    fun anyApp(): Boolean {
        return occupied[0] || occupied[1]
    }

    fun anyProtected(): Boolean {
        return protectedPane[0] || protectedPane[1]
    }

    fun protectedBox(): SplitLayout.Box? {
        for (pane in intArrayOf(layout!!.selected, 1 - layout!!.selected)) {
            val box = layout!!.pane(pane, session!!.width(), session!!.height(), gap())
            if (protectedPane[pane] && box.width > 0 && box.height > 0) return box
        }
        return null
    }

    fun physicalActive(): Boolean {
        return (physicalContact != null ||
            (uiPane >= 0 && !uiPhone) ||
            (dividerHeld && !dividerPhone))
    }

    fun gap(): Int {
        return Math.max(1, Math.round(2 * session!!.dpi / 160f))
    }

    fun summary(): String {
        return ((if (layout!!.vertical) "Top/bottom" else "Left/right") +
            " · " +
            Math.round(layout!!.ratio * 100) +
            ":" +
            Math.round((1 - layout!!.ratio) * 100) +
            " · Pane " +
            (layout!!.selected + 1) +
            (if (layout!!.maximized >= 0) " fullscreen" else ""))
    }

    @JvmOverloads
    fun dimensions(
        pane: Int,
        width: Int,
        height: Int,
        old: Boolean,
        dpi: Int = session!!.dpi,
    ): SplitLayout.Box? {
        val value = if (old && previous != null) previous else layout
        return value!!.display(pane, width, height, Math.max(1, Math.round(2 * dpi / 160f)))
    }

    private fun place(view: View, box: SplitLayout.Box) {
        val old = view.getLayoutParams() as FrameLayout.LayoutParams?
        if (
            (old!!.width != box.width ||
                old!!.height != box.height ||
                old!!.leftMargin != box.x ||
                old!!.topMargin != box.y)
        ) {
            val next = FrameLayout.LayoutParams(Math.max(1, box.width), Math.max(1, box.height))
            next.leftMargin = box.x
            next.topMargin = box.y
            view.setLayoutParams(next)
        }
    }

    fun layoutViews() {
        if (closed) return
        val density = if (stagedSurfaces == null) session!!.dpi else stagedDpi
        if (controlsDpi != density) {
            controlsDpi = density
            val config =
                android.content.res.Configuration(layer!!.getResources()!!.getConfiguration())
            config.densityDpi = controlsDpi
            val context =
                ContextThemeWrapper(
                    layer!!.getContext()!!.createConfigurationContext(config),
                    R.style.DexDroidTheme,
                )
            for (i in 0..1) {
                val pane = i
                decoration[i]!!.removeAllViews()
                empty[i] =
                    IdleDesktop(
                        context,
                        {
                            select(pane)
                            session!!.splitApps(pane)
                        },
                        "split-idle-" + i,
                        "split-idle-apps-" + i,
                    )
                decoration[i]!!.addView(empty[i], FrameLayout.LayoutParams(-1, -1))
            }
        }
        val w = if (stagedSurfaces == null) session!!.width() else stagedWidth
        val h = if (stagedSurfaces == null) session!!.height() else stagedHeight
        val visibleLayout = if (stagedSurfaces == null) layout else stagedLayout
        val visibleGap = Math.max(1, Math.round(2 * density / 160f))
        for (i in 0..1) {
            val visible = visibleLayout!!.pane(i, w, h, visibleGap)
            var nativeBox = dimensions(i, w, h, false)
            val texture = if (i == 0) primary else secondary
            if (stagedSurfaces != null) nativeBox = stagedSurfaces!![i]
            place(texture!!, nativeBox!!)
            texture!!.setAlpha((if (visible.width > 0 && occupied[i]) 1 else 0).toFloat())
            if (texture!!.getSurfaceTexture() != null)
                texture!!
                    .getSurfaceTexture()!!
                    .setDefaultBufferSize(nativeBox!!.width, nativeBox!!.height)
            place(decoration[i]!!, visible)
            // Stage decoration and canvas together with native textures, including rollback.
            place(empty[i]!!, SplitLayout.Box(0, 0, visible.width, visible.height))
            decoration[i]!!.setVisibility(if (visible.width > 0) View.VISIBLE else View.GONE)
            decoration[i]!!.setBackgroundColor(Color.TRANSPARENT)
            empty[i]!!.setVisibility(if (occupied[i]) View.GONE else View.VISIBLE)
            decoration[i]!!.setForeground(null)
        }
        val bar = visibleLayout!!.divider(w, h, visibleGap)
        place(divider!!, bar)
        divider!!.setVisibility(if (bar.width > 0) View.VISIBLE else View.GONE)
        divider!!.setTranslationX(0f)
        divider!!.setTranslationY(0f)
    }

    fun select(pane: Int) {
        if (!current() || pane < 0 || pane > 1 || session!!.busy) return
        cancelPhone()
        cancelPhysical()
        if (layout!!.maximized >= 0 && layout!!.maximized != pane) {
            val next = layout!!.copy()
            next.selected = pane
            next.maximized = pane
            request(next)
            return
        }
        selectInternal(pane)
    }

    private fun selectInternal(pane: Int) {
        if (pane < 0 || pane > 1) return
        layout!!.selected = pane
        session!!.splitStateChanged()
        layoutViews()
    }

    fun direction(vertical: Boolean) {
        val next = layout!!.copy()
        next.vertical = vertical
        next.maximized = -1
        request(next)
    }

    fun ratio(ratio: Float) {
        val next = layout!!.copy()
        next.ratio = Math.max(.25f, Math.min(.75f, ratio))
        request(next)
    }

    fun swap() {
        val next = layout!!.copy()
        next.swapped = !next.swapped
        request(next)
    }

    fun maximize() {
        val next = layout!!.copy()
        next.maximized = if (layout!!.maximized < 0) layout!!.selected else -1
        request(next)
    }

    fun end() {
        val next = layout!!.copy()
        next.enabled = false
        next.maximized = -1
        next.selected = 0
        request(next)
    }

    private fun request(next: SplitLayout?, after: Runnable? = null) {
        if (!current() || session!!.busy || session!!.awaitingConfirmation()) {
            session!!.report("Finish the current display change first")
            return
        }
        session!!.cancelAppSelection()
        cancelInput()
        previous = layout!!.copy()
        layout = next
        session!!.reconfigureSplit(after)
    }

    @Throws(Exception::class)
    fun fence() {
        val barrier = input.submit({})
        barrier.get(5, TimeUnit.SECONDS)
    }

    @Throws(Exception::class)
    fun resizeSecond(width: Int, height: Int, dpi: Int, old: Boolean) {
        if (secondId < 0 || secondSurface == null || (!layout!!.enabled && !old)) return
        val box = dimensions(1, width, height, old, dpi)
        secondTexture!!.setDefaultBufferSize(box!!.width, box!!.height)
        val resized = Surface(secondTexture)
        try {
            secondBridge!!.resize(resized, box!!.width, box!!.height, dpi)
        } finally {
            resized.release()
        }
    }

    /** TextureView changes its producer size during layout; attach only after that change. */
    fun afterSurfaceLayout(
        width: Int,
        height: Int,
        dpi: Int,
        old: Boolean,
        ready: Runnable?,
        failed: Runnable?,
    ) {
        if (cancelSurfaceLayout != null) cancelSurfaceLayout!!.run()
        val first = dimensions(0, width, height, old, dpi)
        val second = dimensions(1, width, height, old, dpi)
        // Task callbacks can relayout views before SessionService commits the new dimensions.
        // Keep producer and view sizes together for the entire remote resize, not just pre-draw.
        stagedSurfaces = arrayOf<SplitLayout.Box?>(first, second)
        stagedWidth = width
        stagedHeight = height
        stagedDpi = dpi
        stagedLayout = if (old && previous != null) previous else layout
        layoutViews()
        val observer = layer!!.getViewTreeObserver()
        val done = booleanArrayOf(false)
        val timeout = arrayOf<Runnable?>(null)
        val listener = arrayOf<ViewTreeObserver.OnPreDrawListener?>(null)
        val cancel = Runnable cancel@{
            if (done!![0]) return@cancel
            done[0] = true
            if (timeout!![0] != null) main.removeCallbacks(timeout!![0]!!)
            if (observer!!.isAlive() && listener!![0] != null)
                observer!!.removeOnPreDrawListener(listener!![0])
            cancelSurfaceLayout = null
        }
        cancelSurfaceLayout = cancel
        timeout[0] = Runnable {
            cancel!!.run()
            if (current()) failed!!.run()
        }
        listener[0] =
            ViewTreeObserver.OnPreDrawListener preDraw@{
                if (!current()) {
                    cancel!!.run()
                    return@preDraw true
                }
                if (
                    (primary!!.getWidth() != first!!.width ||
                        primary!!.getHeight() != first!!.height ||
                        secondary!!.getWidth() != second!!.width ||
                        secondary!!.getHeight() != second!!.height)
                )
                    return@preDraw true
                for (i in 0..1) {
                    val visible =
                        stagedLayout!!.pane(
                            i,
                            width,
                            height,
                            Math.max(1, Math.round(2 * dpi / 160f)),
                        )
                    if (
                        (visible.width > 0 &&
                            visible.height > 0 &&
                            ((decoration[i]!!.getWidth() != visible.width ||
                                decoration[i]!!.getHeight() != visible.height ||
                                ((empty[i]!!.getVisibility() == View.VISIBLE &&
                                    ((empty[i]!!.getWidth() != visible.width ||
                                        (empty[i]!!.getHeight() != visible.height))))))))
                    )
                        return@preDraw true
                }
                cancel!!.run()
                try {
                    primary!!
                        .getSurfaceTexture()!!
                        .setDefaultBufferSize(first!!.width, first!!.height)
                    secondary!!
                        .getSurfaceTexture()!!
                        .setDefaultBufferSize(second!!.width, second!!.height)
                    ready!!.run()
                } catch (unavailable: RuntimeException) {
                    failed!!.run()
                }

                true
            }
        observer!!.addOnPreDrawListener(listener!![0])
        main.postDelayed(timeout!![0]!!, 2000)
        layer!!.requestLayout()
        layer!!.invalidate()
    }

    fun configured(success: Boolean) {
        if (!current()) return
        stagedSurfaces = null
        stagedLayout = null
        if (!success && previous != null) layout = previous
        previous = null
        if (success) session!!.rememberSplitLayout(layout)
        if (!layout!!.enabled) {
            detach(true)
            return
        }
        session!!.splitStateChanged()
        layoutViews()
        if (success) session!!.report("Split view · " + summary())
    }

    fun occupancy(pane: Int, value: Boolean, error: String?) {
        occupancy(pane, value, "", error)
    }

    fun occupancy(pane: Int, value: Boolean, component: String?, error: String?) {
        if (!current()) return
        if (!error!!.isEmpty()) {
            session!!.log("Pane task state unavailable")
            inputPending[pane] = true
            session!!.report("Pane " + (pane + 1) + ": " + error)
            return
        }
        if (inputPending[pane]) {
            inputPending[pane] = false
            session!!.report("Pane " + (pane + 1) + (if (value) " ready" else " empty"))
        }
        val top = ComponentName.unflattenFromString(component!!)
        val changed = top != null && top != apps[pane]
        if (occupied[pane] == value && !changed) return
        if (changed) {
            if (top!!.getPackageName() != packages!![pane]) cancelKeys()
            apps[pane] = top
            packages!![pane] = top!!.getPackageName()
        }
        occupied[pane] = value
        if (!value) {
            cancelInput()
            apps[pane] = null
            packages!![pane] = ""
            protectedPane[pane] = false
        }
        session!!.splitStateChanged()
        layoutViews()
    }

    fun protection(pane: Int, state: Int) {
        if (!current() || state == ProtectedWindows.State.UNKNOWN.ordinal) return
        protectedPane[pane] = occupied[pane] && state == ProtectedWindows.State.PROTECTED.ordinal
        session!!.splitStateChanged()
    }

    @JvmOverloads
    fun launch(component: ComponentName?, orientation: Int?, pane: Int = layout!!.selected) {
        if (!current() || session!!.busy || secondId < 0 || session!!.awaitingConfirmation()) {
            session!!.report("Wait for split view to finish updating")
            return
        }
        val other = 1 - pane
        if (component!!.getPackageName() == packages!![other] && occupied[other]) {
            select(other)
            session!!.report("App is already in pane " + (other + 1))
            return
        }
        if (layout!!.maximized >= 0 && layout!!.maximized != pane) {
            val next = layout!!.copy()
            next.maximized = pane
            next.selected = next.maximized
            request(
                next,
                {
                    if (current() && !session!!.busy && layout!!.maximized == pane)
                        launch(component, orientation, pane)
                },
            )
            return
        }
        cancelInput()
        selectInternal(pane)
        session!!.busy = true
        val target = bridge(pane)
        val id = displayId(pane)
        session!!.desktopWork({
            try {
                fence()
                target!!.launch(id, component!!.flattenToString())
                main.post({
                    if (!current()) return@post
                    apps[pane] = component
                    packages!![pane] = component!!.getPackageName()
                    occupied[pane] = true
                    session!!.busy = false
                    session!!.splitStateChanged()
                    layoutViews()
                    session!!.report(
                        ("Opened app in pane " +
                            (pane + 1) +
                            (if (orientation != null) ". Split view uses workspace orientation."
                            else ""))
                    )
                })
            } catch (error: Exception) {
                main.post({
                    if (current()) {
                        session!!.busy = false
                        session!!.report("App launch failed: " + error!!.message)
                    }
                })
            }
        })
    }

    fun closeApp() {
        if (!current() || session!!.busy || session!!.awaitingConfirmation()) return
        cancelInput()
        session!!.busy = true
        val pane = layout!!.selected
        val id = displayId(pane)
        val target = bridge(pane)
        session!!.desktopWork({
            try {
                fence()
                target!!.closeApps(id)
                main.post({
                    if (current()) {
                        session!!.busy = false
                        occupancy(pane, false, "")
                        session!!.report("Pane " + (pane + 1) + " closed. Choose Apps.")
                    }
                })
            } catch (error: Exception) {
                main.post({
                    if (current()) {
                        session!!.busy = false
                        session!!.report("Close failed: " + error!!.message)
                    }
                })
            }
        })
    }

    private fun submit(action: Runnable?, discard: Runnable?, diagnostic: String? = null) {
        if (!current()) {
            if (diagnostic != null) KeyDiagnostics.record("queue-rejected-closed", diagnostic)
            discard!!.run()
            return
        }
        try {
            input.execute({
                try {
                    // Accepted events drain before either owned display is stopped.
                    // In particular, queued cancellation must survive detach().
                    action!!.run()
                } catch (error: Exception) {
                    main.post({
                        if (current()) {
                            session!!.stopDesktop()
                            session!!.report(
                                ("Split input failed. Restart desktop: " + error!!.message)
                            )
                        }
                    })
                } finally {
                    discard!!.run()
                }
            })
            if (diagnostic != null) KeyDiagnostics.record("queue-accepted", diagnostic)
        } catch (full: RejectedExecutionException) {
            if (diagnostic != null) KeyDiagnostics.record("queue-rejected-full", diagnostic)
            discard!!.run()
            main.post({
                if (current()) {
                    session!!.stopDesktop()
                    session!!.report("Split input queue full. Restart desktop.")
                }
            })
        }
    }

    private fun sendMotion(pane: Int, event: MotionEvent) {
        val id = displayId(pane)
        val target = bridge(pane)
        submit(
            {
                try {
                    target!!.relayMotion(id, event)
                } catch (e: RemoteException) {
                    throw IllegalStateException(e)
                }
            },
            Runnable({ event.recycle() }),
        )
    }

    private fun sendKey(pane: Int, event: KeyEvent?) {
        val id = displayId(pane)
        val target = bridge(pane)
        val diagnostic =
            BuildConfig.DEBUG && KeyDiagnostics.synthetic(apps[pane]?.flattenToString(), event)
        // Capture mutable ownership on the main owner, not on the input worker.
        val record =
            if (diagnostic)
                "workspace=${System.identityHashCode(this)} pane=$pane display=$id closed=$closed held=${keys.size} cancelled=${cancelledKeys.size} queue=${input.queue.size} ${KeyDiagnostics.tuple(event!!)}"
            else ""
        if (diagnostic) KeyDiagnostics.record("queue-request", record)
        submit(
            {
                if (diagnostic) KeyDiagnostics.record("queue-execute", record)
                try {
                    target!!.relayKey(id, event)
                    if (diagnostic) KeyDiagnostics.record("queue-complete", record)
                } catch (e: RemoteException) {
                    if (diagnostic) KeyDiagnostics.record("queue-error", record)
                    throw IllegalStateException(e)
                }
            },
            { if (diagnostic) KeyDiagnostics.record("queue-release", record) },
            if (diagnostic) record else null,
        )
    }

    fun traceCancellation(origin: String) {
        if (
            BuildConfig.DEBUG &&
                keys.values.any {
                    KeyDiagnostics.synthetic(apps[it!!.pane]?.flattenToString(), it.down)
                }
        )
            KeyDiagnostics.record(
                "cancel-request",
                "origin=$origin workspace=${System.identityHashCode(this)} held=${keys.size} cancelled=${cancelledKeys.size} queue=${input.queue.size} closed=$closed",
            )
    }

    private class HeldKey(@JvmField val pane: Int, @JvmField val down: KeyEvent?)

    fun hardwareKey(event: KeyEvent?): Boolean {
        if (
            (!current() ||
                session!!.busy ||
                !event!!.isFromSource(InputDevice.SOURCE_KEYBOARD) ||
                (event!!.getFlags() and KeyEvent.FLAG_SOFT_KEYBOARD) != 0)
        )
            return false
        val code = event!!.getKeyCode()
        if (
            (code == KeyEvent.KEYCODE_VOLUME_UP ||
                code == KeyEvent.KEYCODE_VOLUME_DOWN ||
                code == KeyEvent.KEYCODE_POWER ||
                code == KeyEvent.KEYCODE_HOME ||
                code == KeyEvent.KEYCODE_APP_SWITCH)
        )
            return false
        val key = (event!!.getDeviceId().toLong() shl 32) or (code.toLong() and 0xffffffffL)
        if (event!!.getAction() == KeyEvent.ACTION_UP && cancelledKeys.remove(key)) return true
        val held = keys.get(key)
        if (held == null && event!!.getAction() == KeyEvent.ACTION_UP) return true
        val pane = if (held == null) layout!!.selected else held!!.pane
        if (!occupied[pane]) return false
        if (event!!.getAction() == KeyEvent.ACTION_DOWN) {
            cancelledKeys.remove(key)
            if (keys.size >= 64 && held == null) {
                session!!.stopDesktop()
                session!!.report("Too many held keyboard keys. Restart desktop.")
                return true
            }
            keys.put(key, HeldKey(pane, KeyEvent(event)))
        } else if (event!!.getAction() == KeyEvent.ACTION_UP) keys.remove(key)
        sendKey(pane, KeyEvent(event))
        return true
    }

    fun keyCode(code: Int) {
        val pane = layout!!.selected
        if (!occupied[pane]) return
        val now = SystemClock.uptimeMillis()
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) sendKey(
            pane,
            KeyEvent(now, now, action, code, 0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD),
        )
    }

    private fun inverse(physicalW: Float, physicalH: Float): Matrix {
        val w = session!!.width()
        val h = session!!.height()
        val src = floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat())
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
                    w,
                    h,
                )
            dst[i * 2] = p[0]
            dst[i * 2 + 1] = p[1]
        }
        val forward = Matrix()
        val result = Matrix()
        forward.setPolyToPoly(src, 0, dst, 0, 4)
        if (!forward.invert(result)) throw IllegalStateException("Invalid workspace geometry")
        return result
    }

    fun physical(event: MotionEvent?, width: Float, height: Float): Boolean {
        if (!current()) return true
        if (
            (!event!!.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) &&
                !event!!.isFromSource(InputDevice.SOURCE_MOUSE) &&
                !event!!.isFromSource(InputDevice.SOURCE_TOUCHPAD))
        )
            return false
        if (
            event!!.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) && !session!!.monitorTouchEnabled()
        ) {
            cancelPhysical()
            return true
        }
        if (session!!.busy) {
            cancelPhysical()
            return true
        }
        for (i in 0 until event!!.getPointerCount()) if (
            (Geometry.unmap(
                event!!.getX(i),
                event!!.getY(i),
                session!!.rotation,
                width,
                height,
                session!!.width(),
                session!!.height(),
            ) == null)
        ) {
            cancelPhysical()
            return true
        }
        val mapped = MotionEvent.obtain(event)
        mapped!!.transform(inverse(width, height))
        route(mapped!!, false)
        mapped!!.recycle()
        return true
    }

    fun phonePointer(event: MotionEvent?) {
        if (!current() || session!!.busy || physicalActive()) return
        route(event!!, true)
    }

    fun phoneTouch(action: Int, xs: FloatArray?, ys: FloatArray?) {
        if (
            (!current() ||
                session!!.busy ||
                physicalActive() ||
                xs!!.size < 1 ||
                xs!!.size != ys!!.size)
        )
            return
        val now = SystemClock.uptimeMillis()
        if ((action and 255) == MotionEvent.ACTION_DOWN) phoneDown = now
        val props = arrayOfNulls<MotionEvent.PointerProperties?>(xs!!.size)
        val coords = arrayOfNulls<MotionEvent.PointerCoords?>(xs!!.size)
        for (i in xs!!.indices) {
            props[i] = MotionEvent.PointerProperties()
            props[i]!!.id = i
            props[i]!!.toolType = MotionEvent.TOOL_TYPE_FINGER
            coords[i] = MotionEvent.PointerCoords()
            coords[i]!!.x = xs!![i]
            coords[i]!!.y = ys!![i]
            coords[i]!!.pressure = 1f
        }
        val event =
            MotionEvent.obtain(
                phoneDown,
                now,
                action,
                xs!!.size,
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
        route(event!!, true)
        event!!.recycle()
    }

    fun wheel(horizontal: Float, vertical: Float) {
        if (!current() || session!!.busy || physicalActive()) return
        val pane = layout!!.selected
        if (!occupied[pane]) return
        val box = layout!!.pane(pane, session!!.width(), session!!.height(), gap())
        val x = Geometry.clamp(session!!.cx - box.x, 0f, (box.width - 1).toFloat())
        val y = Geometry.clamp(session!!.cy - box.y, 0f, (box.height - 1).toFloat())
        val speed = session!!.scrollSpeed()
        sendMotion(pane, InputEvents.wheel(x, y, horizontal * speed, vertical * speed)!!)
    }

    private fun route(event: MotionEvent, phone: Boolean) {
        val action = event.getActionMasked()
        val w = session!!.width()
        val h = session!!.height()
        if (action == MotionEvent.ACTION_DOWN) {
            if (phone) cancelPhone()
            else {
                cancelPhysical()
                cancelPhone()
                session!!.hideMonitorPointer()
            }
            val grab = Math.max(gap(), Math.round(12 * session!!.dpi / 160f))
            val hit =
                if (layout!!.dividerGrab(w, h, gap(), grab).contains(event.getX(), event.getY())) -1
                else layout!!.hit(event.getX(), event.getY(), w, h, gap())
            if (hit < 0) {
                if (layout!!.dividerGrab(w, h, gap(), grab).contains(event.getX(), event.getY())) {
                    dividerHeld = true
                    dividerPhone = phone
                    dividerPreview = layout!!.ratio
                }
                return
            }
            selectInternal(hit)
            if (!occupied[hit]) {
                uiPane = hit
                uiPhone = phone
            } else if (phone) phonePane = hit else physicalPane = hit
        }
        if (dividerHeld) {
            if (phone != dividerPhone) return
            if (action == MotionEvent.ACTION_MOVE) {
                dividerPreview =
                    Geometry.clamp(
                        (if (layout!!.vertical) event.getY() / h else event.getX() / w),
                        .25f,
                        .75f,
                    )
                val bar = layout!!.divider(w, h, gap())
                if (layout!!.vertical) divider!!.setTranslationY(dividerPreview * h - bar.y)
                else divider!!.setTranslationX(dividerPreview * w - bar.x)
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (action == MotionEvent.ACTION_UP)
                    dividerPreview =
                        Geometry.clamp(
                            (if (layout!!.vertical) event.getY() / h else event.getX() / w),
                            .25f,
                            .75f,
                        )
                dividerHeld = false
                divider!!.setTranslationX(0f)
                divider!!.setTranslationY(0f)
                if (action == MotionEvent.ACTION_UP) ratio(dividerPreview)
            }
            return
        }
        if (uiPane >= 0 && phone == uiPhone) {
            val box = layout!!.pane(uiPane, w, h, gap())
            val local = MotionEvent.obtain(event)
            local!!.offsetLocation((-box.x).toFloat(), (-box.y).toFloat())
            decoration[uiPane]!!.dispatchTouchEvent(local)
            local!!.recycle()
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) uiPane = -1
            return
        }
        var pane = if (phone) phonePane else physicalPane
        val hover =
            (action == MotionEvent.ACTION_HOVER_ENTER ||
                action == MotionEvent.ACTION_HOVER_MOVE ||
                action == MotionEvent.ACTION_HOVER_EXIT ||
                action == MotionEvent.ACTION_SCROLL)
        if (pane < 0 && hover) pane = layout!!.hit(event.getX(), event.getY(), w, h, gap())
        if (pane < 0 || !occupied[pane]) return
        if (!hover && action != MotionEvent.ACTION_MOVE)
            session!!.log(
                ("Split contact: pane " +
                    (pane + 1) +
                    " " +
                    MotionEvent.actionToString(event.getAction()) +
                    " pointers=" +
                    event.getPointerCount())
            )
        if (action == MotionEvent.ACTION_SCROLL) selectInternal(pane)
        val box = layout!!.pane(pane, w, h, gap())
        val local = MotionEvent.obtain(event)
        local!!.offsetLocation((-box.x).toFloat(), (-box.y).toFloat())
        sendMotion(pane, local!!)
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (phone) {
                if (phoneContact != null) phoneContact!!.recycle()
                phoneContact = null
                phonePane = -1
            } else {
                if (physicalContact != null) physicalContact!!.recycle()
                physicalContact = null
                physicalPane = -1
            }
        } else if (!hover) {
            if (phone) {
                if (phoneContact != null) phoneContact!!.recycle()
                phoneContact = remainingContact(event)
            } else {
                if (physicalContact != null) physicalContact!!.recycle()
                physicalContact = remainingContact(event)
            }
        }
    }

    fun cancelPhone() {
        cancelContact(true)
    }

    private fun remainingContact(event: MotionEvent): MotionEvent? {
        if (event.getActionMasked() != MotionEvent.ACTION_POINTER_UP)
            return MotionEvent.obtainNoHistory(event)
        val count = event.getPointerCount() - 1
        var next = 0
        val properties = arrayOfNulls<MotionEvent.PointerProperties?>(count)
        val coords = arrayOfNulls<MotionEvent.PointerCoords?>(count)
        for (i in 0 until event.getPointerCount()) {
            if (i == event.getActionIndex()) continue
            properties[next] = MotionEvent.PointerProperties()
            coords[next] = MotionEvent.PointerCoords()
            event.getPointerProperties(i, properties[next])
            event.getPointerCoords(i, coords[next])
            next++
        }
        return MotionEvent.obtain(
            event.getDownTime(),
            event.getEventTime(),
            MotionEvent.ACTION_MOVE,
            count,
            properties,
            coords,
            event.getMetaState(),
            event.getButtonState(),
            event.getXPrecision(),
            event.getYPrecision(),
            event.getDeviceId(),
            event.getEdgeFlags(),
            event.getSource(),
            event.getFlags(),
        )
    }

    fun cancelMonitorGesture() {
        cancelPhysical()
    }

    private fun cancelPhysical() {
        cancelContact(false)
    }

    private fun cancelContact(phone: Boolean) {
        if (dividerHeld && dividerPhone == phone) {
            dividerHeld = false
            divider!!.setTranslationX(0f)
            divider!!.setTranslationY(0f)
        }
        val held = if (phone) phoneContact else physicalContact
        val pane = if (phone) phonePane else physicalPane
        if (held != null) {
            if (pane >= 0) {
                val cancel = MotionEvent.obtainNoHistory(held)
                cancel!!.setAction(MotionEvent.ACTION_CANCEL)
                val box = layout!!.pane(pane, session!!.width(), session!!.height(), gap())
                cancel!!.offsetLocation((-box.x).toFloat(), (-box.y).toFloat())
                sendMotion(pane, cancel!!)
            }
            held!!.recycle()
        }
        if (phone) {
            phoneContact = null
            phonePane = -1
        } else {
            physicalContact = null
            physicalPane = -1
        }
        if (uiPane >= 0 && uiPhone == phone) {
            val cancel =
                MotionEvent.obtain(
                    SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_CANCEL,
                    0f,
                    0f,
                    0,
                )
            decoration[uiPane]!!.dispatchTouchEvent(cancel)
            cancel!!.recycle()
            uiPane = -1
        }
    }

    fun cancelInput() {
        cancelPhone()
        cancelPhysical()
        dividerHeld = false
        cancelKeys()
    }

    fun cancelKeys() {
        traceCancellation("workspace")
        for (item in keys.entries) {
            val held = item!!.value
            sendKey(
                held!!.pane,
                KeyEvent(
                    held!!.down!!.getDownTime(),
                    SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_UP,
                    held!!.down!!.getKeyCode(),
                    0,
                    held!!.down!!.getMetaState(),
                    held!!.down!!.getDeviceId(),
                    held!!.down!!.getScanCode(),
                    held!!.down!!.getFlags() or KeyEvent.FLAG_CANCELED,
                    held!!.down!!.getSource(),
                ),
            )
            cancelledKeys.add(item!!.key)
            if (cancelledKeys.size > 64) cancelledKeys.remove(cancelledKeys.iterator().next())
        }
        keys.clear()
    }

    @JvmOverloads
    fun detach(keepPrimary: Boolean, completion: Consumer<Boolean?>? = null) {
        if (closed) return
        session!!.cancelAppSelection()
        if (cancelSurfaceLayout != null) cancelSurfaceLayout!!.run()
        cancelInput()
        closed = true
        if (session!!.split == this) session!!.split = null
        for (frame in decoration) layer!!.removeView(frame)
        layer!!.removeView(divider)
        layer!!.removeView(secondary)
        primary!!.setAlpha(1f)
        if (keepPrimary) {
            session!!.activePackage = packages!![0]
            session!!.activeComponent = apps[0]
            session!!.splitPrimaryState(occupied[0], protectedPane[0])
            session!!.splitGeometryChanged()
        }
        input.shutdown()
        val target = secondBridge
        val id = secondId
        session!!.desktopWork({
            var cleaned = true
            try {
                if (!input.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleaned = false
                    session!!.log("Split input shutdown timed out")
                }
                if (target != null) {
                    try {
                        if (id >= 0) target!!.closeApps(id)
                    } catch (closeWarning: Exception) {
                        // App removal is asynchronous on some OEMs. The
                        // final Stop still must confirm display/task removal.
                        session!!.log(
                            ("Secondary app closure pending; releasing display: " +
                                closeWarning!!.message)
                        )
                    }

                    target!!.stop()
                }
            } catch (error: Exception) {
                cleaned = false
                session!!.log("Secondary cleanup failed: " + error!!.message)
                try {
                    if (target != null) target!!.stop()
                } catch (ignored: Exception) {
                    session!!.log("Secondary bridge unavailable during cleanup")
                }
            } finally {
                if (completion != null) completion!!.accept(cleaned)
                if (secondSurface != null) secondSurface!!.release()
                main.post({
                    if (bound) {
                        bound = false
                        try {
                            Shizuku.unbindUserService(args!!, connection, true)
                        } catch (error: RuntimeException) {
                            session!!.log(("Secondary service unbind failed: " + error!!.message))
                        }
                    }
                    if (secondTexture != null) secondTexture!!.release()
                })
            }
        })
        session!!.notifyUi()
    }
}
