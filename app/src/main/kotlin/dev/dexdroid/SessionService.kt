package dev.dexdroid

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.display.*
import android.os.*
import android.util.DisplayMetrics
import android.view.*
import android.widget.FrameLayout
import java.util.*
import java.util.concurrent.*
import java.util.function.Consumer
import rikka.shizuku.Shizuku

/** Owns the desktop independently of the controller Activity. */
open class SessionService : Service(), DisplayManager.DisplayListener {

    @Volatile @JvmField var listener: Listener? = null
    @JvmField var appCatalog: AppCatalog? = null
    @Volatile @JvmField var split: SplitWorkspace? = null
    @JvmField var activeComponent: ComponentName? = null
    private var desktopApp: Boolean = false
    @Volatile @JvmField var protectedContent: Boolean = false
    @JvmField var status: String? = "Connect Shizuku to begin"
    @JvmField var activePackage: String? = ""
    @Volatile @JvmField var id: Int = -1
    @Volatile @JvmField var rotation: Int = 0
    @Volatile @JvmField var defaultRotation: Int = 0
    @Volatile @JvmField var baseW: Int = 1920
    @Volatile @JvmField var baseH: Int = 1200
    @Volatile @JvmField var dpi: Int = 240
    @JvmField var cx: Float = 960f
    @JvmField var cy: Float = 600f
    @JvmField var pointerVisible: Boolean = false
    @Volatile @JvmField var busy: Boolean = false
    @JvmField var external: Display? = null
    @JvmField var prefs: SharedPreferences? = null
    @JvmField internal var shizukuState: ShizukuSetup.State? = ShizukuSetup.State.STOPPED
    private var bridge: IDeskService? = null
    private var monitor: Monitor? = null
    private var displays: DisplayManager? = null
    private val main: Handler = Handler(Looper.getMainLooper()!!)
    private val sessionAudio = SessionAudio(main) { notifyUi() }

    internal fun audioState(): SessionAudioState = sessionAudio.state

    internal fun chooseAudio(key: String) = sessionAudio.select(key)

    internal fun refreshAudio() = sessionAudio.refresh()

    private val worker: ThreadPoolExecutor =
        ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue<Runnable?>())
    private val binder: IBinder = LocalBinder()
    @Volatile private var generation: Int = 0
    private val sessionOwner: IBinder = Binder()
    private var bound: Boolean = false
    private var permissionRequested: Boolean = false
    private var permissionDenied: Boolean = false
    @Volatile private var autoStartRequested: Boolean = false
    private var pendingApp: ComponentName? = null
    private var pendingOrientation: Int? = null
    private val diagnostics: DiagnosticLog = DiagnosticLog()
    private var pendingProfile: IntArray? = null
    private var profileKey: String? = null
    private var detectedIdentity: String? = null
    private var pendingMonitorIdentity: String? = null
    private var pendingNamedProfile: MonitorProfiles.Profile? = null
    private var pendingMonitorDisplay: Int = -1
    private var rollbackRequested: Boolean = false
    private var revertingProfile: Boolean = false
    private var contactsX: FloatArray? = null
    private var contactsY: FloatArray? = null
    private var monitorContacts: MotionEvent? = null
    private var monitorBlocked: Boolean = false
    private var sessionWakeLock: PowerManager.WakeLock? = null
    private val rollback: Runnable? = Runnable { confirm(false) }
    private var args: Shizuku.UserServiceArgs? = null

    private val permission: Shizuku.OnRequestPermissionResultListener? =
        Shizuku.OnRequestPermissionResultListener { code, result ->
            main.post({
                if (code == 71) {
                    permissionRequested = false
                    permissionDenied = result != PackageManager.PERMISSION_GRANTED
                    if (!permissionDenied) connect()
                    else report("Shizuku authorization denied. Retry from Options when ready.")
                }
            })
        }
    private val received: Shizuku.OnBinderReceivedListener? =
        Shizuku.OnBinderReceivedListener {
            main.post({
                log("Shizuku service available")
                if (autoStartRequested) connect()
            })
        }
    private val death: Shizuku.OnBinderDeadListener? =
        Shizuku.OnBinderDeadListener {
            main.post({
                bridge = null
                bound = false
                stopDesktop()
                shizukuState = ShizukuSetup.State.STOPPED
                report("Shizuku stopped. Open Shizuku to restart its service.")
            })
        }
    private val connection: ServiceConnection =
        object : ServiceConnection {
            public override fun onServiceConnected(name: ComponentName?, value: IBinder?) {
                main.post({
                    bridge = IDeskService.Stub.asInterface(value)
                    shizukuState = ShizukuSetup.State.CONNECTED
                    report("Shizuku connected")
                    if (autoStartRequested) startDesktop()
                })
            }

            public override fun onServiceDisconnected(name: ComponentName?) {
                main.post({
                    bridge = null
                    bound = false
                    stopDesktop()
                    refreshShizukuState()
                    report("Shizuku disconnected. Retry from Options.")
                })
            }
        }

    private var appSelection: AppSelection? = null
    private var appSelectionSerial: Long = 0
    private var appSelectionPreview: Boolean = false

    private var foregroundPromotion: ForegroundPromotion? =
        ForegroundPromotion({ this.promoteForeground(it) })

    private var monitorStartupDeadline: Runnable? = null

    fun interface Listener {
        fun changed()
    }

    open inner class LocalBinder : Binder() {
        open fun get(): SessionService {
            return this@SessionService
        }
    }

    public override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("desktop", 0)
        appCatalog = AppCatalog(this, Consumer<String?>({ this.log(it) }))
        prefs!!.edit()!!.remove("androidKeyboardMonitor")!!.apply()
        defaultRotation = prefs!!.getInt("rotation", 0)
        rotation = defaultRotation
        args =
            Shizuku.UserServiceArgs(ComponentName(this, DeskService::class.java!!))
                .daemon(false)!!
                .processNameSuffix("desktop")!!
                .debuggable(true)!!
                .version(BuildConfig.VERSION_CODE)
        sessionWakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DexDroid:desktop",
            )
        sessionWakeLock!!.setReferenceCounted(false)
        displays = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager?
        displays!!.registerDisplayListener(this, main)
        Shizuku.addRequestPermissionResultListener(permission!!)
        Shizuku.addBinderDeadListener(death!!)
        Shizuku.addBinderReceivedListenerSticky(received!!)
        detect()
        log("Controller service created")
    }

    public override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    public override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && "stop" == intent!!.getAction()) pauseDesktop()
        return Service.START_NOT_STICKY
    }

    open fun log(message: String?) {
        diagnostics.add(message)
        android.util.Log.i("DexDroid", message!!)
    }

    open fun diagnosticText(): String {
        return ("DexDroid " +
            BuildConfig.VERSION_NAME +
            " | Android " +
            Build.VERSION.RELEASE +
            " | " +
            Build.MODEL +
            "\nMonitor: " +
            (if (external == null) "disconnected" else external!!.getName()) +
            " | Virtual display: " +
            id +
            "\nDesktop: " +
            width() +
            "x" +
            height() +
            " @ " +
            dpi +
            " DPI | Rotation: " +
            (rotation * 90) +
            "°\nShizuku bridge: " +
            (if (bridge == null) "disconnected" else "connected") +
            " | Busy: " +
            busy +
            "\nLast 250 events, memory only. Key sent means IPC completed, not proof an app handled it.\n\n" +
            diagnostics.snapshot())
    }

    open fun clearDiagnostics() {
        diagnostics.clear()
    }

    open fun needsRecovery(): Boolean {
        return bridge == null
    }

    open fun desktopRequested(): Boolean {
        return autoStartRequested
    }

    open fun hasDesktopApp(): Boolean {
        return desktopApp
    }

    open fun splitEnabled(): Boolean {
        return split != null
    }

    open fun savedSplitLayout(): SplitLayout {
        return SplitLayoutPreferences.read(prefs!!.getAll()!!)
    }

    open fun rememberSplitLayout(layout: SplitLayout?) {
        val edit = prefs!!.edit()
        for (item in SplitLayoutPreferences.write(layout!!).entries) {
            if (item!!.value is Boolean) edit!!.putBoolean(item!!.key, item!!.value as Boolean)
            else edit!!.putFloat(item!!.key, item!!.value as Float)
        }
        edit!!.apply()
    }

    class AppSelection(
        @JvmField val workspace: SplitWorkspace?,
        @JvmField val generation: Int,
        @JvmField val display: Int,
        @JvmField val pane: Int,
        @JvmField val serial: Long,
    )

    @JvmOverloads
    open fun beginAppSelection(pane: Int, select: Boolean = true): AppSelection? {
        if (split != null && (busy || awaitingConfirmation() || pane < 0 || pane > 1)) return null
        cancelAppSelection()
        if (split != null && select) split!!.select(pane)
        appSelection =
            AppSelection(
                split,
                generation,
                id,
                if (split == null) 0 else pane,
                ++appSelectionSerial,
            )
        notifyUi()
        return appSelection
    }

    open fun resumeAppSelection(origin: AppSelection?): AppSelection? {
        if (
            (origin == null ||
                origin!!.generation != generation ||
                origin!!.display != id ||
                id < 0 ||
                origin!!.serial != appSelectionSerial ||
                appSelection != null ||
                busy ||
                awaitingConfirmation())
        )
            return null
        return beginAppSelection(origin!!.pane, false)
    }

    open fun currentAppSelection(choice: AppSelection?): Boolean {
        return (choice != null &&
            appSelection == choice &&
            choice!!.workspace == split &&
            choice!!.generation == generation &&
            (choice!!.display == id || choice!!.display < 0))
    }

    open fun appSelectionBox(): SplitLayout.Box? {
        if (!appSelectionPreview || !currentAppSelection(appSelection) || id < 0) return null
        if (split != null && split!!.state().maximized >= 0)
            return SplitLayout.Box(0, 0, width(), height())
        return if (split == null) SplitLayout.Box(0, 0, width(), height())
        else split!!.state().pane(appSelection!!.pane, width(), height(), split!!.gap())
    }

    open fun finishAppSelection(choice: AppSelection?) {
        if (choice != null && appSelection == choice) cancelAppSelection()
    }

    open fun cancelAppSelection() {
        if (appSelection == null) return
        appSelection = null
        appSelectionPreview = false
        notifyUi()
    }

    open fun previewAppSelection(choice: AppSelection?, pressed: Boolean) {
        if (!currentAppSelection(choice)) return
        appSelectionPreview = pressed
        notifyUi()
    }

    open fun launchSelection(
        choice: AppSelection?,
        component: ComponentName?,
        rotation: Int?,
    ): Boolean {
        if (!currentAppSelection(choice)) {
            report("App destination changed. Choose its pane again.")
            return false
        }
        if (id >= 0 && (busy || awaitingConfirmation())) {
            report("Wait for the display change to finish")
            return false
        }
        finishAppSelection(choice)
        if (split == null) launch(component, rotation)
        else split!!.launch(component, rotation, choice!!.pane)
        return true
    }

    open fun layoutStatus(): String {
        return if (split == null) "Single view" else split!!.summary()
    }

    open fun primaryBridge(): IDeskService? {
        return bridge
    }

    open fun splitOwner(): IBinder {
        return sessionOwner
    }

    open fun desktopWork(work: Runnable?) {
        worker.execute(work!!)
    }

    open fun reconfigureSplit() {
        configure(baseW, baseH, dpi, rotation, null)
    }

    open fun reconfigureSplit(after: Runnable?) {
        configure(baseW, baseH, dpi, rotation, after)
    }

    open fun splitGeometryChanged() {
        if (monitor != null) monitor!!.transform()
    }

    open fun splitApps(pane: Int) {
        if (monitor != null && monitor!!.shell != null) monitor!!.shell!!.chooseApp(pane)
    }

    open fun splitPrimaryState(occupied: Boolean, secure: Boolean) {
        desktopApp = occupied
        protectedContent = secure
        notifyUi()
    }

    open fun splitStateChanged() {
        if (split == null) return
        desktopApp = split!!.anyApp()
        protectedContent = split!!.anyProtected()
        activePackage = split!!.selectedPackage()
        activeComponent = split!!.selectedComponent()
        notifyUi()
    }

    @JvmOverloads
    open fun enableSplit(vertical: Boolean? = null) {
        if (split != null || busy || id < 0 || monitor == null || awaitingConfirmation()) {
            report("Start the desktop and finish its display change first")
            return
        }
        cancelInput()
        busy = true
        try {
            split = SplitWorkspace(this, monitor!!.desktopLayer, monitor!!.texture!!, vertical)
            monitor!!.transform()
            report("Starting split view…")
        } catch (error: RuntimeException) {
            stopDesktop()
            report("Split setup failed. Reopen DexDroid: " + error!!.message)
        }
    }

    open fun hardwareKey(event: KeyEvent?): Boolean {
        return (split != null &&
            (monitor == null || monitor!!.shell == null || !monitor!!.shell!!.isOpen) &&
            split!!.hardwareKey(event))
    }

    open fun hardwarePointer(event: MotionEvent?, width: Float, height: Float): Boolean {
        if (
            split == null ||
                (monitor != null && monitor!!.shell != null && monitor!!.shell!!.isOpen)
        )
            return false
        return split!!.physical(event, width, height)
    }

    open fun controllerPointer(event: MotionEvent?, width: Float, height: Float) {
        if (
            (split == null ||
                id < 0 ||
                busy ||
                physicalMonitorInputActive() ||
                width <= 0 ||
                height <= 0)
        )
            return
        val mapped = MotionEvent.obtain(event)
        try {
            val scale = Matrix()
            scale.setScale(width() / width, height() / height)
            mapped!!.transform(scale)
            cx = Geometry.clamp(mapped!!.getX(), 0f, (width() - 1).toFloat())
            cy = Geometry.clamp(mapped!!.getY(), 0f, (height() - 1).toFloat())
            showTouchpadPointer()
            if (
                monitor != null &&
                    monitor!!.shell != null &&
                    monitor!!.shell!!.controllerPointer(mapped!!)
            )
                return
            split!!.phonePointer(mapped)
        } finally {
            mapped!!.recycle()
        }
    }

    open fun ensureDesktop() {
        autoStartRequested = true
        if (bridge == null) connect() else startDesktop()
    }

    open fun retryDesktop() {
        permissionDenied = false
        permissionRequested = false
        ensureDesktop()
    }

    open fun pauseDesktop() {
        val controllerOwner = listener
        val controller = if (controllerOwner == null) null else controllerTask()
        log(
            ("Explicit Stop: controller=" +
                (controller != null) +
                ", owner=" +
                (controllerOwner != null))
        )
        autoStartRequested = false
        pendingApp = null
        pendingOrientation = null
        val cleanup = stopDesktopWithResult()
        val stoppedGeneration = generation
        // The serial worker owns both cleanup operations and the return. A normal
        // AppTask request can be silently refused after a migrated app backgrounds
        // us, so the existing shell focuses only the captured live controller task.
        val target = bridge
        if (controller != null && target != null) {
            val taskId: Int
            try {
                taskId = controller!!.getTaskInfo()!!.taskId
            } catch (error: RuntimeException) {
                log("Stop controller task lookup: " + error!!.message)
                return
            }

            worker.execute({
                if (
                    (generation != stoppedGeneration ||
                        autoStartRequested ||
                        listener !== controllerOwner ||
                        !cleanup.succeeded)
                ) {
                    log(
                        "Stop controller return cancelled: obsolete owner/session or unconfirmed cleanup"
                    )
                    return@execute
                }
                try {
                    val focused = target!!.focusController(taskId)
                    log(
                        if (focused) "Stop controller focus requested through shell"
                        else "Stop controller no longer exists; return skipped"
                    )
                } catch (error: Exception) {
                    log("Controller return after Stop: " + error!!.message)
                    main.post({
                        if (
                            (generation == stoppedGeneration &&
                                !autoStartRequested &&
                                listener === controllerOwner)
                        ) {
                            status = "Desktop stopped. Reopen DexDroid on the phone."
                            notifyUi()
                        }
                    })
                }
            })
        }
    }

    private fun controllerTask(): ActivityManager.AppTask? {
        try {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
            for (task in manager!!.getAppTasks()!!) {
                val info = task!!.getTaskInfo()
                val base = info!!.baseIntent.getComponent()
                if (
                    (info!!.numActivities > 0 &&
                        base != null &&
                        base == ComponentName(this, MainActivity::class.java!!))
                )
                    return task
            }
        } catch (error: RuntimeException) {
            log("Controller task lookup after Stop: " + error!!.message)
        }

        return null
    }

    open fun report(message: String?) {
        report(message, message)
    }

    private fun report(message: String?, diagnosticMessage: String?) {
        log(diagnosticMessage)
        main.post({
            status = message
            notifyUi()
        })
    }

    open fun notifyUi() {
        if (listener != null) listener!!.changed()
        if (monitor != null && monitor!!.shell != null) monitor!!.shell!!.refresh()
    }

    open fun refreshShizukuState() {
        shizukuState = ShizukuSetup.detect(this, bridge != null)
    }

    open fun connect() {
        try {
            if (!Shizuku.pingBinder()) {
                refreshShizukuState()
                report(
                    if (
                        (getPackageManager()!!.getLaunchIntentForPackage(
                            "moe.shizuku.privileged.api"
                        ) == null)
                    )
                        "Install Shizuku, then start its wireless-debugging service."
                    else "Shizuku is stopped. Open Shizuku and start it through wireless debugging."
                )
                return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                shizukuState = ShizukuSetup.State.PERMISSION
                if (!permissionRequested && !permissionDenied) {
                    permissionRequested = true
                    log("Requesting Shizuku authorization")
                    Shizuku.requestPermission(71)
                }
                return
            }
            shizukuState =
                if (bridge != null) ShizukuSetup.State.CONNECTED else ShizukuSetup.State.READY
            if (bridge != null) {
                if (autoStartRequested) startDesktop()
                return
            }
            if (bound) return
            bound = true
            Shizuku.bindUserService(args!!, connection)
            report("Connecting to Shizuku…")
        } catch (e: Throwable) {
            bound = false
            permissionRequested = false
            report("Connection failed: " + e!!.message)
        }
    }

    open fun detect() {
        external = null
        for (display in displays!!.getDisplays()!!) {
            val flags = display!!.getFlags()
            if (
                (display!!.getDisplayId() != Display.DEFAULT_DISPLAY &&
                    !display!!.getName()!!.contains("DexDroid") &&
                    !display!!.getName()!!.contains("PortraitDesk") &&
                    ((display!!.getName()!!.contains("HDMI") ||
                        (flags and Display.FLAG_PRESENTATION) != 0)))
            ) {
                external = display
                break
            }
        }
        detectedIdentity = null
        if (external != null) {
            var unique: String? = null
            try {
                unique = Display::class.java!!.getMethod("getUniqueId").invoke(external) as String?
            } catch (unavailable: Exception) {
                // Hidden on some ROMs; capability matching remains usable without bypassing policy.
            }

            val modes = ArrayList<String>()
            for (mode in external!!.getSupportedModes()!!) modes.add(
                (mode!!.getPhysicalWidth().toString() +
                    "x" +
                    mode!!.getPhysicalHeight() +
                    "@" +
                    Math.round(mode!!.getRefreshRate()))
            )
            detectedIdentity = MonitorProfiles.identity(unique, external!!.getName(), modes)
        }
        notifyUi()
    }

    open fun monitorIdentity(): String? {
        return if (external == null) null else detectedIdentity
    }

    open fun monitorMatchingDescription(): String {
        if (external == null) return "Connect a monitor to remember its profile"
        return if (detectedIdentity != null && detectedIdentity!!.startsWith("physical."))
            "Matches Android's physical monitor identity. Port/adapter changes may require reassignment."
        else
            "Matches monitor name and reported modes. Identical monitors share this binding; switch profiles manually if needed."
    }

    open fun monitorProfiles(): MonitorProfiles {
        return MonitorProfiles(MonitorProfilePreferences(prefs))
    }

    open fun recommendedMonitorConfiguration(): MonitorProfiles.Configuration {
        val remembered = monitorProfiles().remembered(monitorIdentity())
        if (remembered != null) return remembered
        var w = if (external == null) 1920 else external!!.getMode()!!.getPhysicalWidth()
        var h = if (external == null) 1200 else external!!.getMode()!!.getPhysicalHeight()
        val scale = Math.min(1f, 4096f / Math.max(w, h))
        w = Math.max(480, Math.round(w * scale))
        h = Math.max(480, Math.round(h * scale))
        return MonitorProfiles.Configuration(
            w,
            h,
            Geometry.recommendedDpi(Math.min(w, h)),
            Math.floorMod(prefs!!.getInt("rotation", 0), 4),
        )
    }

    open fun monitorProfileStatus(): String {
        val profile = monitorProfiles().bound(monitorIdentity())
        if (profile == null)
            return if (monitorProfiles().remembered(monitorIdentity()) == null) "No saved profile"
            else "Custom monitor settings"
        val modified =
            (id >= 0 &&
                profile!!.configuration !=
                    MonitorProfiles.Configuration(baseW, baseH, dpi, defaultRotation))
        return profile!!.name!! + (if (modified) " (modified)" else "")
    }

    private fun profileEditable(needsDesktop: Boolean): Boolean {
        if (busy || awaitingConfirmation()) {
            report("Finish the pending display change first")
            return false
        }
        if (needsDesktop && (external == null || id < 0 || monitorIdentity() == null)) {
            report("Connect a monitor and start the desktop first")
            return false
        }
        return true
    }

    open fun saveMonitorProfile(name: String?): String? {
        if (!profileEditable(true)) return null
        try {
            val profile =
                monitorProfiles()
                    .create(
                        name,
                        MonitorProfiles.Configuration(baseW, baseH, dpi, rotation),
                        monitorIdentity(),
                    )
            monitorProfiles().importLegacy(monitorIdentity(), monitorKey(), null)
            defaultRotation = rotation
            report("Saved monitor profile: " + profile.name!!, "Named monitor profile saved")
            notifyUi()
            return profile.id
        } catch (e: IllegalArgumentException) {
            report(e!!.message)
            return null
        }
    }

    open fun renameMonitorProfile(id: String?, name: String?) {
        if (!profileEditable(false)) return
        try {
            monitorProfiles().rename(id, name)
            report("Monitor profile renamed")
        } catch (e: IllegalArgumentException) {
            report(e!!.message)
        }

        notifyUi()
    }

    open fun updateMonitorProfile(profileId: String?) {
        if (!profileEditable(true)) return
        try {
            monitorProfiles()
                .update(profileId, MonitorProfiles.Configuration(baseW, baseH, dpi, rotation))
            val boundProfile = monitorProfiles().bound(monitorIdentity())
            if (boundProfile != null && boundProfile!!.id == profileId) {
                defaultRotation = rotation
                monitorProfiles().clearWorking(monitorIdentity())
            }
            report("Monitor profile updated from current display")
        } catch (e: IllegalArgumentException) {
            report(e!!.message)
        }

        notifyUi()
    }

    open fun deleteMonitorProfile(profileId: String?) {
        if (!profileEditable(false)) return
        monitorProfiles().delete(profileId)
        report("Monitor profile deleted. Current display unchanged.")
        notifyUi()
    }

    open fun applyMonitorProfile(profileId: String?) {
        if (!profileEditable(true)) return
        val profile = monitorProfiles().find(profileId)
        if (profile == null) {
            report("Profile no longer exists")
            return
        }
        beginPreview(profile!!.configuration!!, profile)
    }

    open fun monitorKey(): String {
        if (external == null) return "none"
        val modes = ArrayList<String>()
        for (mode in external!!.getSupportedModes()!!) modes.add(
            (mode!!.getPhysicalWidth().toString() +
                "x" +
                mode!!.getPhysicalHeight() +
                "@" +
                Math.round(mode!!.getRefreshRate()))
        )
        Collections.sort<String>(modes)
        return ("monitor." +
            external!!.getName() +
            "." +
            Integer.toHexString(modes.toString()!!.hashCode()))
    }

    open fun capabilities(): String {
        if (external == null) return "No external monitor detected"
        val mode = external!!.getMode()
        val metrics = DisplayMetrics()
        external!!.getRealMetrics(metrics)
        val physical =
            if (
                ((metrics.xdpi >= 30 &&
                    metrics.xdpi <= 600 &&
                    metrics.ydpi >= 30 &&
                    metrics.ydpi <= 600))
            )
                String.format(Locale.US, "%.0f / %.0f", metrics.xdpi, metrics.ydpi)
            else "unknown (unreliable report)"
        val text =
            StringBuilder(
                (external!!.getName() +
                    "\nCurrent output: " +
                    mode!!.getPhysicalWidth() +
                    " × " +
                    mode!!.getPhysicalHeight() +
                    " @ " +
                    Math.round(mode!!.getRefreshRate()) +
                    " Hz\nPhysical DPI: " +
                    physical +
                    "\nReported signal modes:\n")
            )
        for (m in external!!.getSupportedModes()!!) text
            .append(m!!.getPhysicalWidth())
            .append(" × ")
            .append(m!!.getPhysicalHeight())
            .append(" @ ")
            .append(Math.round(m!!.getRefreshRate()))
            .append(" Hz\n")
        return (text.toString() +
            "\nExternal touch: " +
            externalTouchDevices() +
            "\nDesktop resolution and UI DPI are independently adjustable. Signal-mode switching is not yet validated on JoyUI. " +
            monitorMatchingDescription())
    }

    open fun width(): Int {
        return if (rotation % 2 == 0) baseW else baseH
    }

    open fun height(): Int {
        return if (rotation % 2 == 0) baseH else baseW
    }

    fun interface ForegroundPromotion {
        fun start(notification: Notification?)
    }

    private fun promoteForeground(notification: Notification?) {
        startService(Intent(this, SessionService::class.java))
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(
                1,
                notification!!,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        else startForeground(1, notification)
    }

    open fun awaitMonitorHost() {
        cancelMonitorStartupDeadline()
        val expected = monitor
        val token = generation
        monitorStartupDeadline = Runnable {
            if (
                (monitor === expected &&
                    expected != null &&
                    token == generation &&
                    expected!!.host == null)
            ) {
                stopDesktop()
                report("Monitor startup timed out. Open DexDroid on the phone to retry.")
            }
        }
        main.postDelayed(monitorStartupDeadline!!, 5000)
    }

    private fun cancelMonitorStartupDeadline() {
        if (monitorStartupDeadline != null) main.removeCallbacks(monitorStartupDeadline!!)
        monitorStartupDeadline = null
    }

    open fun startDesktop() {
        if (bridge == null) {
            ensureDesktop()
            return
        }
        if (monitor != null) return
        detect()
        if (external == null) {
            report("Waiting for an external monitor")
            return
        }
        val key = monitorKey()
        if (prefs!!.contains(key + ".w")) {
            var legacy: MonitorProfiles.Configuration? = null
            try {
                legacy =
                    MonitorProfiles.Configuration(
                        prefs!!.getInt(key + ".w", 0),
                        prefs!!.getInt(key + ".h", 0),
                        prefs!!.getInt(key + ".dpi", 0),
                        Math.floorMod(prefs!!.getInt("rotation", 0), 4),
                    )
            } catch (invalid: IllegalArgumentException) {
                log("Invalid legacy monitor settings ignored")
            } catch (invalid: ClassCastException) {
                log("Invalid legacy monitor settings ignored")
            }

            monitorProfiles().importLegacy(monitorIdentity(), key, legacy)
        }
        val configuration = recommendedMonitorConfiguration()
        baseW = configuration.width
        baseH = configuration.height
        dpi = configuration.dpi
        defaultRotation = configuration.rotation
        rotation = defaultRotation
        cx = width() / 2f
        cy = height() / 2f
        pointerVisible = false
        busy = true
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            nm!!.createNotificationChannel(
                NotificationChannel(
                    "desktop",
                    "Desktop session",
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
            val open =
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ActivityOptions.makeBasic()!!.setLaunchDisplayId(Display.DEFAULT_DISPLAY)!!
                        .toBundle(),
                )
            val stop =
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, SessionService::class.java).setAction("stop"),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val notification =
                Notification.Builder(this, "desktop")
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .setContentTitle("DexDroid desktop running")
                    .setContentText("Tap to return to the touchpad")
                    .setContentIntent(open)
                    .addAction(Notification.Action.Builder(null, "Stop", stop).build())
                    .setOngoing(true)
                    .build()
            foregroundPromotion!!.start(notification)
            sessionWakeLock!!.acquire()
            desktopApp = false
            activePackage = ""
            activeComponent = null
            monitor = Monitor(external)
            val hostIntent =
                Intent(this, MonitorActivity::class.java)
                    .addFlags((Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
                    .putExtra("generation", generation)
                    .putExtra("monitorToken", monitor!!.token)
            if (
                (Build.VERSION.SDK_INT >= 36 &&
                    getSystemService<ActivityManager?>(ActivityManager::class.java!!)!!
                        .isActivityStartAllowedOnDisplay(
                            this,
                            external!!.getDisplayId(),
                            hostIntent,
                        ))
            ) {
                log("Monitor host: independent Activity")
                startActivity(
                    hostIntent,
                    ActivityOptions.makeBasic()!!.setLaunchDisplayId(external!!.getDisplayId())!!
                        .toBundle(),
                )
                awaitMonitorHost()
            } else {
                if (Build.VERSION.SDK_INT >= 36)
                    log(
                        "Monitor host: Presentation fallback; Android may close it when the phone task is removed"
                    )
                monitor!!.show()
            }
            report("Starting desktop…")
        } catch (e: Throwable) {
            stopDesktop()
            report(
                ("Monitor unavailable: " + e!!.message + ". Open DexDroid on the phone to retry.")
            )
        }
    }

    fun interface Work {
        @Throws(Exception::class) fun run(target: IDeskService?)
    }

    private fun queue(action: Work?) {
        val target = bridge
        val token = generation
        worker.execute({
            if (token != generation || target == null) {
                log("Queued operation discarded: session changed or bridge unavailable")
                return@execute
            }
            try {
                action!!.run(target)
            } catch (e: Throwable) {
                report("Desktop operation failed: " + e!!.message)
            }
        })
    }

    private fun physicalMonitorInputActive(): Boolean {
        return ((split != null && split!!.physicalActive()) ||
            monitorContacts != null ||
            ((monitor != null &&
                ((monitor!!.waitingForFocus ||
                    (monitor!!.shell != null && monitor!!.shell!!.physicalActive()))))))
    }

    open fun touch(action: Int, xs: FloatArray?, ys: FloatArray?) {
        if (id < 0 || busy || physicalMonitorInputActive()) return
        if (split != null) {
            showTouchpadPointer()
            if (
                monitor != null &&
                    monitor!!.shell != null &&
                    monitor!!.shell!!.phone(action, xs, ys)
            )
                return
            split!!.phoneTouch(action, xs, ys)
            return
        }
        val masked = action and 255
        if (masked == MotionEvent.ACTION_DOWN) {
            showTouchpadPointer()
            cancelPhoneInput()
        }
        if (monitor != null && monitor!!.shell != null && monitor!!.shell!!.phone(action, xs, ys))
            return
        if (masked != MotionEvent.ACTION_DOWN && contactsX == null) return
        if (masked == MotionEvent.ACTION_MOVE && worker.getQueue()!!.size > 32) return
        if (masked == MotionEvent.ACTION_UP || masked == MotionEvent.ACTION_CANCEL) {
            contactsX = null
            contactsY = null
        } else if (masked == MotionEvent.ACTION_POINTER_UP) {
            val remaining = if (((action shr 8) and 255) == 0) 1 else 0
            contactsX = floatArrayOf(xs!![remaining])
            contactsY = floatArrayOf(ys!![remaining])
        } else {
            contactsX = xs!!.clone()
            contactsY = ys!!.clone()
        }
        val target = id
        queue({ s -> s!!.touch(target, action, xs, ys) })
    }

    open fun click() {
        touch(0, floatArrayOf(cx), floatArrayOf(cy))
        touch(1, floatArrayOf(cx), floatArrayOf(cy))
    }

    open fun scrollSpeed(): Float {
        return prefs!!.getFloat(
            "scrollSpeed." + activePackage!!,
            if ("eu.kanade.tachiyomi" == activePackage) .2f else .5f,
        )
    }

    open fun swipeScrolling(): Boolean {
        return prefs!!.getBoolean(
            "swipeScroll." + activePackage!!,
            ("eu.kanade.tachiyomi" == activePackage || prefs!!.getBoolean("swipeScroll", false)),
        )
    }

    open fun setSwipeScrolling(swipe: Boolean) {
        if (activePackage!!.isEmpty()) {
            report("Launch an app first")
            return
        }
        cancelPhoneInput()
        prefs!!.edit()!!.putBoolean("swipeScroll." + activePackage!!, swipe)!!.apply()
        report(
            if (swipe) "Swipe scrolling saved for active app"
            else "Wheel scrolling saved for active app"
        )
    }

    open fun saveAppSettings() {
        if (activePackage!!.isEmpty()) {
            report("Launch an app first")
            return
        }
        val saved = prefs!!.edit()
        if (split == null) saved!!.putInt("app." + activePackage!!, rotation)
        saved!!
            .putFloat("scrollSpeed." + activePackage!!, scrollSpeed())!!
            .putBoolean("swipeScroll." + activePackage!!, swipeScrolling())!!
            .apply()
        report(
            if (split == null) "Rotation, scroll mode and speed saved for active app"
            else "Scroll mode and speed saved. Single-view app rotation retained."
        )
    }

    open fun resetAppSettings() {
        if (activePackage!!.isEmpty()) {
            report("Launch an app first")
            return
        }
        if (busy) {
            report("Wait for the display change to finish")
            return
        }
        cancelPhoneInput()
        prefs!!
            .edit()!!
            .remove("app." + activePackage!!)!!
            .remove("scrollSpeed." + activePackage!!)!!
            .remove("swipeScroll." + activePackage!!)!!
            .apply()
        if (split == null && id >= 0 && rotation != defaultRotation) rotate(defaultRotation, false)
        report("Active app settings reset to defaults")
    }

    open fun showTouchpadPointer() {
        if (id < 0 || busy || physicalMonitorInputActive()) return
        pointerVisible = true
        if (monitor != null && monitor!!.cursor != null) monitor!!.cursor!!.invalidate()
    }

    open fun hideMonitorPointer() {
        pointerVisible = false
        if (monitor != null && monitor!!.cursor != null) monitor!!.cursor!!.invalidate()
    }

    open fun wheel(dx: Float, dy: Float) {
        if (id < 0 || busy || physicalMonitorInputActive() || worker.getQueue()!!.size > 32) return
        if (split != null) {
            showTouchpadPointer()
            if (
                (monitor != null &&
                    monitor!!.shell != null &&
                    monitor!!.shell!!.wheel(cx, cy, dx * scrollSpeed(), dy * scrollSpeed()))
            )
                return
            split!!.wheel(dx, dy)
            return
        }
        val target = id
        val x = cx
        val y = cy
        showTouchpadPointer()
        val speed = scrollSpeed()
        if (
            (monitor != null &&
                monitor!!.shell != null &&
                monitor!!.shell!!.wheel(x, y, dx * speed, dy * speed))
        )
            return
        queue({ s -> s!!.scroll(target, x, y, dx * speed, dy * speed) })
    }

    open fun key(key: Int) {
        if (id < 0 || busy) {
            log(
                ("Key ignored: " +
                    KeyEvent.keyCodeToString(key) +
                    "; desktop " +
                    (if (id < 0) "unavailable" else "busy"))
            )
            return
        }
        if (
            (key == KeyEvent.KEYCODE_BACK &&
                monitor != null &&
                monitor!!.shell != null &&
                monitor!!.shell!!.isOpen)
        ) {
            monitor!!.shell!!.back()
            return
        }
        if (key == KeyEvent.KEYCODE_BACK && !desktopApp) {
            log("Idle Back ignored")
            return
        }
        if (split != null) {
            split!!.keyCode(key)
            return
        }
        val target = id
        log("Key queued: " + KeyEvent.keyCodeToString(key) + " -> display " + target)
        queue({ s ->
            s!!.key(target, key)
            log("Key sent: " + KeyEvent.keyCodeToString(key) + " -> display " + target)
        })
    }

    open fun move(dx: Float, dy: Float) {
        if (physicalMonitorInputActive()) return
        showTouchpadPointer()
        cx = Geometry.clamp(cx + dx, 0f, (width() - 1).toFloat())
        cy = Geometry.clamp(cy + dy, 0f, (height() - 1).toFloat())
        if (monitor != null && monitor!!.cursor != null) monitor!!.cursor!!.invalidate()
    }

    open fun center() {
        if (split != null) {
            val box = split!!.state().pane(split!!.selected(), width(), height(), split!!.gap())
            cx = box.x + box.width / 2f
            cy = box.y + box.height / 2f
            move(0f, 0f)
            return
        }
        cx = width() / 2f
        cy = height() / 2f
        move(0f, 0f)
    }

    open fun cancelPhoneInput() {
        if (split != null) split!!.cancelPhone()
        if (monitor != null && monitor!!.shell != null) monitor!!.shell!!.cancelPhone()
        if (id >= 0 && contactsX != null) {
            val target = id
            val x = contactsX
            val y = contactsY
            contactsX = null
            contactsY = null
            queue({ s -> s!!.touch(target, MotionEvent.ACTION_CANCEL, x, y) })
        }
    }

    open fun cancelInput() {
        if (BuildConfig.DEBUG) split?.traceCancellation("session-generation-$generation")
        if (split != null) split!!.cancelInput()
        cancelPhoneInput()
        cancelMonitorInput()
        if (monitor != null && monitor!!.shell != null) monitor!!.shell!!.cancelAll()
    }

    open fun cancelMonitorGesture() {
        cancelMonitorInput()
    }

    open fun monitorNavigationEnabled(): Boolean {
        return prefs!!.getBoolean("monitorNavigation", true)
    }

    open fun setMonitorNavigationEnabled(enabled: Boolean) {
        cancelInput()
        prefs!!.edit()!!.putBoolean("monitorNavigation", enabled)!!.apply()
        report(
            if (enabled) "Monitor edge navigation enabled" else "Monitor edge navigation disabled"
        )
    }

    private fun relay(event: MotionEvent) {
        val target = id
        val token = generation
        val service = bridge
        if (
            ((event.getActionMasked() == MotionEvent.ACTION_MOVE &&
                worker.getQueue()!!.size > 32) || target < 0)
        ) {
            event.recycle()
            return
        }
        worker.execute({
            try {
                if (token == generation && service != null) service!!.relayTouch(target, event)
            } catch (e: Throwable) {
                report("Monitor touch failed: " + e!!.message)
            } finally {
                event.recycle()
            }
        })
    }

    private fun cancelMonitorInput() {
        if (monitor != null) monitor!!.discardPendingTouch()
        if (split != null) split!!.cancelMonitorGesture()
        if (monitorContacts != null) {
            val cancel = MotionEvent.obtainNoHistory(monitorContacts)
            cancel!!.setAction(MotionEvent.ACTION_CANCEL)
            relay(cancel!!)
            monitorContacts!!.recycle()
            monitorContacts = null
        }
        monitorBlocked = true
    }

    open fun setMonitorTouchEnabled(enabled: Boolean) {
        cancelMonitorInput()
        prefs!!.edit()!!.putBoolean("monitorTouch", enabled)!!.apply()
        report(if (enabled) "Monitor touch enabled" else "Monitor touch disabled")
    }

    open fun monitorTouchEnabled(): Boolean {
        return prefs!!.getBoolean("monitorTouch", true)
    }

    /** Only the physical monitor window calls this. Injected events target the virtual display. */
    open fun monitorTouch(original: MotionEvent?, physicalW: Float, physicalH: Float): Boolean {
        if (split != null) return split!!.physical(original, physicalW, physicalH)
        if (!original!!.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) || external == null)
            return false
        val action = original!!.getActionMasked()
        if (action == MotionEvent.ACTION_DOWN) {
            pointerVisible = false
            if (monitor != null && monitor!!.cursor != null) monitor!!.cursor!!.invalidate()
        }
        if (action == MotionEvent.ACTION_DOWN)
            android.util.Log.i(
                "DexDroidTouch",
                ("physical DOWN target=" +
                    id +
                    " busy=" +
                    busy +
                    " enabled=" +
                    monitorTouchEnabled() +
                    " size=" +
                    physicalW +
                    "x" +
                    physicalH),
            )
        if (action == MotionEvent.ACTION_DOWN) {
            cancelMonitorInput()
            monitorBlocked = false
        }
        if (!monitorTouchEnabled() || id < 0 || busy) {
            if (action == MotionEvent.ACTION_DOWN)
                log(
                    ("Monitor touch ignored: enabled=" +
                        monitorTouchEnabled() +
                        " display=" +
                        id +
                        " busy=" +
                        busy)
                )
            cancelMonitorInput()
            return true
        }
        if (monitorBlocked) return true
        if (action != MotionEvent.ACTION_DOWN && monitorContacts == null) return true
        for (i in 0 until original!!.getPointerCount()) if (
            (Geometry.unmap(
                original!!.getX(i),
                original!!.getY(i),
                rotation,
                physicalW,
                physicalH,
                width(),
                height(),
            ) == null)
        ) {
            log("Monitor touch canceled: contact outside desktop Fit area")
            cancelMonitorInput()
            return true
        }
        if (action == MotionEvent.ACTION_DOWN) cancelPhoneInput()
        val src =
            floatArrayOf(
                0f,
                0f,
                width().toFloat(),
                0f,
                width().toFloat(),
                height().toFloat(),
                0f,
                height().toFloat(),
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
                    rotation,
                    physicalW,
                    physicalH,
                    width(),
                    height(),
                )
            dst[i * 2] = p[0]
            dst[i * 2 + 1] = p[1]
        }
        val forward = Matrix()
        val inverse = Matrix()
        forward.setPolyToPoly(src, 0, dst, 0, 4)
        if (!forward.invert(inverse)) {
            cancelMonitorInput()
            return true
        }
        var mapped = MotionEvent.obtain(original)
        mapped!!.transform(inverse)
        mapped = boundMonitorContacts(mapped!!)
        cx = Geometry.clamp(mapped!!.getX(), 0f, (width() - 1).toFloat())
        cy = Geometry.clamp(mapped!!.getY(), 0f, (height() - 1).toFloat())
        if (monitor != null && monitor!!.cursor != null) monitor!!.cursor!!.invalidate()
        if (monitorContacts != null) monitorContacts!!.recycle()
        monitorContacts = remainingMonitorContacts(mapped!!)
        if (action != MotionEvent.ACTION_MOVE)
            log(
                ("Monitor touch: " +
                    MotionEvent.actionToString(original!!.getAction()) +
                    " pointers=" +
                    original!!.getPointerCount())
            )
        relay(mapped!!)
        return true
    }

    /** An exact rotated screen edge can map to width/height; use the last valid pixel. */
    private fun boundMonitorContacts(event: MotionEvent): MotionEvent? {
        var needsClamp = false
        for (i in 0 until event.getPointerCount()) if (
            (event.getX(i) < 0 ||
                event.getY(i) < 0 ||
                event.getX(i) > width() - 1 ||
                event.getY(i) > height() - 1)
        )
            needsClamp = true
        if (!needsClamp) return event
        val count = event.getPointerCount()
        val props = arrayOfNulls<MotionEvent.PointerProperties?>(count)
        val coords = arrayOfNulls<MotionEvent.PointerCoords?>(count)
        for (i in 0 until count) {
            props[i] = MotionEvent.PointerProperties()
            coords[i] = MotionEvent.PointerCoords()
            event.getPointerProperties(i, props[i])
            event.getPointerCoords(i, coords[i])
            coords[i]!!.x = Geometry.clamp(coords[i]!!.x, 0f, (width() - 1).toFloat())
            coords[i]!!.y = Geometry.clamp(coords[i]!!.y, 0f, (height() - 1).toFloat())
        }
        val bounded =
            MotionEvent.obtain(
                event.getDownTime(),
                event.getEventTime(),
                event.getAction(),
                count,
                props,
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
        event.recycle()
        return bounded
    }

    private fun remainingMonitorContacts(event: MotionEvent): MotionEvent? {
        val action = event.getActionMasked()
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) return null
        if (action != MotionEvent.ACTION_POINTER_UP) return MotionEvent.obtainNoHistory(event)
        val count = event.getPointerCount() - 1
        val props = arrayOfNulls<MotionEvent.PointerProperties?>(count)
        val coords = arrayOfNulls<MotionEvent.PointerCoords?>(count)
        var i = 0
        var out = 0
        while (i < event.getPointerCount()) {
            if (i == event.getActionIndex()) {
                i++
                continue
            }
            props[out] = MotionEvent.PointerProperties()
            coords[out] = MotionEvent.PointerCoords()
            event.getPointerProperties(i, props[out])
            event.getPointerCoords(i, coords[out])
            out++
            i++
        }
        return MotionEvent.obtain(
            event.getDownTime(),
            event.getEventTime(),
            MotionEvent.ACTION_MOVE,
            count,
            props,
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

    open fun externalTouchDevices(): String {
        val names = StringBuilder()
        for (deviceId in InputDevice.getDeviceIds()!!) {
            val device = InputDevice.getDevice(deviceId)
            if (
                (device != null &&
                    device!!.isExternal() &&
                    device!!.supportsSource(InputDevice.SOURCE_TOUCHSCREEN))
            ) {
                if (names.length > 0) names.append(", ")
                names.append(device!!.getName())
            }
        }
        return if (names.length == 0) "No external touchscreen detected" else names.toString()
    }

    open fun monitorInputView(): View? {
        val window = monitorWindow()
        return if (window == null) null else window!!.getDecorView()
    }

    open fun monitorShellView(): View? {
        return if (monitor == null || monitor!!.shell == null) null else monitor!!.shell!!.root
    }

    open fun monitorShellPage(): String? {
        return if (monitor == null || monitor!!.shell == null) "stopped"
        else monitor!!.shell!!.page()
    }

    open fun monitorShellAnimating(): Boolean {
        return monitor != null && monitor!!.shell != null && monitor!!.shell!!.animating()
    }

    open fun monitorWindow(): Window? {
        return if (monitor == null) null
        else if (monitor!!.host != null) monitor!!.host!!.getWindow() else monitor!!.getWindow()
    }

    open fun sessionAwake(): Boolean {
        return sessionWakeLock != null && sessionWakeLock!!.isHeld()
    }

    open fun backgroundProtectionEnabled(): Boolean {
        return (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(getPackageName())
    }

    open fun rotate(selected: Int, saveDefault: Boolean) {
        if (selected < 0 || selected > 3 || busy || awaitingConfirmation()) return
        val current =
            if (id < 0 && external != null) recommendedMonitorConfiguration()
            else MonitorProfiles.Configuration(baseW, baseH, dpi, rotation)
        configure(
            current.width,
            current.height,
            current.dpi,
            selected,
            if (saveDefault)
                Runnable {
                    defaultRotation = selected
                    prefs!!.edit()!!.putInt("rotation", selected)!!.apply()
                    if (monitorIdentity() != null)
                        monitorProfiles()
                            .remember(
                                monitorIdentity(),
                                MonitorProfiles.Configuration(baseW, baseH, dpi, selected),
                            )
                    notifyUi()
                }
            else null,
        )
    }

    private fun configure(bw: Int, bh: Int, density: Int, selected: Int, after: Runnable?) {
        cancelAppSelection()
        if (id < 0) {
            baseW = bw
            baseH = bh
            dpi = density
            rotation = selected
            if (after != null) after!!.run()
            notifyUi()
            return
        }
        cancelInput()
        busy = true
        val u = cx / width()
        val v = cy / height()
        val oldW = baseW
        val oldH = baseH
        val oldDpi = dpi
        val oldRotation = rotation
        val newW = if (selected % 2 == 0) bw else bh
        val newH = if (selected % 2 == 0) bh else bw
        val token = generation
        val target = bridge
        val splitting = split
        val newPane =
            if (splitting == null) SplitLayout.Box(0, 0, newW, newH)
            else splitting!!.dimensions(0, newW, newH, false, density)
        val oldDesktopW = if (oldRotation % 2 == 0) oldW else oldH
        val oldDesktopH = if (oldRotation % 2 == 0) oldH else oldW
        val oldPane =
            if (splitting == null) SplitLayout.Box(0, 0, oldDesktopW, oldDesktopH)
            else splitting!!.dimensions(0, oldDesktopW, oldDesktopH, true, oldDpi)
        val startResize = Runnable startResize@{
            if (token != generation) return@startResize
            val resizedSurface = monitor!!.outputSurface(newPane!!.width, newPane!!.height)
            worker.execute({
                try {
                    if (splitting != null) splitting!!.fence()
                    target!!.resize(resizedSurface, newPane!!.width, newPane!!.height, density)
                    if (splitting != null) splitting!!.resizeSecond(newW, newH, density, false)
                    main.post({
                        if (token != generation) return@post
                        monitor!!.stagedHeight = 0
                        monitor!!.stagedWidth = monitor!!.stagedHeight
                        baseW = bw
                        baseH = bh
                        dpi = density
                        rotation = selected
                        cx = u * newW
                        cy = v * newH
                        monitor!!
                            .texture!!
                            .getSurfaceTexture()!!
                            .setDefaultBufferSize(newPane!!.width, newPane!!.height)
                        if (splitting != null) splitting!!.configured(true)
                        monitor!!.transform()
                        main.postDelayed(
                            {
                                if (token != generation) return@postDelayed
                                busy = false
                                notifyUi()
                                if (after != null) after!!.run()
                                launchPending()
                            },
                            250,
                        )
                    })
                } catch (e: Throwable) {
                    android.util.Log.w("DexDroid", "Display update failed", e)
                    var restored: Boolean
                    try {
                        if (splitting != null) {
                            val laidOut = CountDownLatch(1)
                            val prepared = booleanArrayOf(false)
                            main.post({
                                if (token != generation) {
                                    laidOut.countDown()
                                    return@post
                                }
                                splitting!!.afterSurfaceLayout(
                                    oldDesktopW,
                                    oldDesktopH,
                                    oldDpi,
                                    true,
                                    {
                                        prepared[0] = true
                                        laidOut.countDown()
                                    },
                                    Runnable({ laidOut.countDown() }),
                                )
                            })
                            if (!laidOut.await(3, TimeUnit.SECONDS) || !prepared!![0])
                                throw IllegalStateException("Rollback surfaces unavailable")
                            // Rollback has already staged the old native texture
                            // geometry.
                            val rollbackSurface = Surface(monitor!!.texture!!.getSurfaceTexture())
                            try {
                                target!!.resize(
                                    rollbackSurface,
                                    oldPane!!.width,
                                    oldPane!!.height,
                                    oldDpi,
                                )
                            } finally {
                                rollbackSurface.release()
                            }
                        } else {
                            val replaced = CountDownLatch(1)
                            val prepared = booleanArrayOf(false)
                            main.post({
                                if (token != generation) {
                                    replaced.countDown()
                                    return@post
                                }
                                monitor!!.afterOutputLayout(
                                    oldPane!!.width,
                                    oldPane!!.height,
                                    {
                                        prepared[0] = true
                                        replaced.countDown()
                                    },
                                    Runnable({ replaced.countDown() }),
                                )
                            })
                            if ((!replaced.await(3, TimeUnit.SECONDS) || !prepared!![0]))
                                throw IllegalStateException("Rollback surface unavailable")
                            val rollbackSurface = Surface(monitor!!.texture!!.getSurfaceTexture())
                            try {
                                target!!.resize(
                                    rollbackSurface,
                                    oldPane!!.width,
                                    oldPane!!.height,
                                    oldDpi,
                                )
                            } finally {
                                rollbackSurface.release()
                            }
                        }

                        if (splitting != null)
                            splitting!!.resizeSecond(oldDesktopW, oldDesktopH, oldDpi, true)
                        restored = true
                    } catch (failedRollback: Throwable) {
                        android.util.Log.w("DexDroid", "Display rollback failed", failedRollback)
                        restored = false
                    }

                    val rollbackRestored = restored
                    main.post({
                        if (token != generation) return@post
                        if (!rollbackRestored || revertingProfile) {
                            stopDesktop()
                            report(
                                "Display update or rollback failed; desktop stopped. Saved monitor association unchanged."
                            )
                            return@post
                        }
                        busy = false
                        monitor!!.stagedHeight = 0
                        monitor!!.stagedWidth = monitor!!.stagedHeight
                        if (splitting != null) splitting!!.configured(false)
                        if (
                            (monitor != null && (monitor!!.texture!!.getSurfaceTexture() != null))
                        ) {
                            monitor!!
                                .texture!!
                                .getSurfaceTexture()!!
                                .setDefaultBufferSize(oldPane!!.width, oldPane!!.height)
                            monitor!!.transform()
                        }
                        report(("Resize failed; retained previous settings: " + e!!.message))
                        if (pendingProfile != null) {
                            defaultRotation = pendingProfile!![4]
                            pendingProfile = null
                            pendingNamedProfile = null
                            rollbackRequested = false
                            main.removeCallbacks(rollback!!)
                        }
                        notifyUi()
                        launchPending()
                    })
                } finally {
                    resizedSurface.release()
                }
            })
        }
        if (splitting != null)
            splitting!!.afterSurfaceLayout(
                newW,
                newH,
                density,
                false,
                startResize,
                {
                    if (token == generation) {
                        stopDesktop()
                        report("Split surface layout unavailable. Restart desktop.")
                    }
                },
            )
        else
            monitor!!.afterOutputLayout(
                newPane!!.width,
                newPane!!.height,
                startResize,
                {
                    if (token == generation) {
                        stopDesktop()
                        report("Output layout unavailable. Restart desktop.")
                    }
                },
            )
    }

    private fun launchPending() {
        if (pendingApp != null && id >= 0 && !busy && !awaitingConfirmation()) {
            val app = pendingApp
            val orientation = pendingOrientation
            pendingApp = null
            pendingOrientation = null
            launch(app, orientation)
        }
    }

    open fun launch(component: ComponentName?, oneTime: Int?) {
        if (split != null) {
            split!!.launch(component, oneTime)
            return
        }
        if (id < 0 || busy || awaitingConfirmation()) {
            pendingApp = component
            pendingOrientation = oneTime
            log("App launch queued: " + component!!.flattenToShortString())
            ensureDesktop()
            return
        }
        activeComponent = component
        activePackage = component!!.getPackageName()
        val packageName = activePackage
        val selected =
            if (oneTime != null) oneTime
            else prefs!!.getInt("app." + activePackage!!, defaultRotation)
        notifyUi()
        val start = Runnable {
            val target = id
            val scope = LaunchScope(generation, target, bridge)
            val description =
                "Opened " +
                    packageName +
                    " • " +
                    width() +
                    " × " +
                    height() +
                    " • " +
                    dpi +
                    " UI DPI\n"
            queue({ s ->
                if (!scope.current(generation, id, bridge, split != null)) return@queue
                try {
                    val result = s!!.launch(target, component!!.flattenToString())
                    main.post({
                        if (scope.current(generation, id, bridge, split != null)) {
                            desktopApp = true
                            status = description + result!!.trim({ it <= ' ' })
                            log(status)
                            notifyUi()
                        }
                    })
                } catch (error: Throwable) {
                    main.post({
                        if (scope.current(generation, id, bridge, split != null)) {
                            status = "Desktop operation failed: " + error.message
                            log(status)
                            notifyUi()
                        }
                    })
                }
            })
        }
        if (selected != rotation) configure(baseW, baseH, dpi, selected, start) else start!!.run()
    }

    open fun rememberApp(remember: Boolean) {
        if (split != null && remember) {
            report("Split view uses workspace rotation. Saved single-view app rotation retained.")
            return
        }
        if (activePackage!!.isEmpty()) {
            report("Launch an app first")
            return
        }
        val edit = prefs!!.edit()
        if (remember) edit!!.putInt("app." + activePackage!!, rotation)
        else edit!!.remove("app." + activePackage!!)
        edit!!.apply()
        report(
            if (remember) "Orientation saved for " + activePackage!!
            else "App now uses the session orientation"
        )
    }

    open fun awaitingConfirmation(): Boolean {
        return pendingProfile != null
    }

    open fun preview(bw: Int, bh: Int, density: Int) {
        if (busy || id < 0 || pendingProfile != null) {
            report("Start desktop and finish any pending change first")
            return
        }
        try {
            beginPreview(MonitorProfiles.Configuration(bw, bh, density, rotation), null)
        } catch (invalid: IllegalArgumentException) {
            report(invalid!!.message)
        }
    }

    private fun beginPreview(
        configuration: MonitorProfiles.Configuration,
        named: MonitorProfiles.Profile?,
    ) {
        pendingProfile = intArrayOf(baseW, baseH, dpi, rotation, defaultRotation)
        profileKey = monitorKey()
        pendingMonitorIdentity = monitorIdentity()
        pendingMonitorDisplay = external!!.getDisplayId()
        pendingNamedProfile = named
        rollbackRequested = false
        configure(
            configuration.width,
            configuration.height,
            configuration.dpi,
            configuration.rotation,
            {
                if (pendingProfile == null) return@configure
                if (rollbackRequested) {
                    confirm(false)
                    return@configure
                }
                report(
                    ((if (named == null) "Keep this display setup?"
                    else "Keep profile " + named!!.name + "?") +
                        " Automatically reverts in 15 seconds."),
                    "Display preview awaiting confirmation",
                )
                main.postDelayed(rollback!!, 15000)
                notifyUi()
            },
        )
    }

    open fun confirm(keep: Boolean) {
        var keep = keep
        if (pendingProfile == null) return
        if (busy) {
            if (!keep) {
                rollbackRequested = true
                main.removeCallbacks(rollback!!)
            }
            return
        }
        if (
            (keep &&
                ((external == null ||
                    external!!.getDisplayId() != pendingMonitorDisplay ||
                    monitorIdentity() != pendingMonitorIdentity)))
        )
            keep = false
        main.removeCallbacks(rollback!!)
        val previous = pendingProfile
        val named = pendingNamedProfile
        if (keep && named != null && monitorProfiles().find(named!!.id) == null) keep = false
        pendingProfile = null
        pendingNamedProfile = null
        rollbackRequested = false
        if (keep) {
            if (named != null) {
                monitorProfiles().bind(pendingMonitorIdentity, named!!.id)
                monitorProfiles().importLegacy(pendingMonitorIdentity, profileKey, null)
                defaultRotation = named!!.configuration!!.rotation
            } else {
                monitorProfiles()
                    .remember(
                        pendingMonitorIdentity,
                        MonitorProfiles.Configuration(baseW, baseH, dpi, defaultRotation),
                    )
                monitorProfiles().importLegacy(pendingMonitorIdentity, profileKey, null)
            }
            prefs!!
                .edit()!!
                .putInt(profileKey!! + ".w", baseW)!!
                .putInt(profileKey!! + ".h", baseH)!!
                .putInt(profileKey!! + ".dpi", dpi)!!
                .apply()
            report(
                if (named == null) "Monitor display settings saved"
                else "Monitor profile applied: " + named!!.name!!,
                if (named == null) "Monitor display settings saved"
                else "Named monitor profile applied",
            )
            launchPending()
        } else {
            defaultRotation = previous!![4]
            revertingProfile = true
            configure(
                previous!![0],
                previous!![1],
                previous!![2],
                previous!![3],
                {
                    revertingProfile = false
                    report("Previous desktop settings restored")
                },
            )
        }
        notifyUi()
    }

    open fun resetMonitor() {
        if (external == null || !profileEditable(false)) return
        val key = monitorKey()
        monitorProfiles().forget(monitorIdentity())
        monitorProfiles().importLegacy(monitorIdentity(), key, null)
        prefs!!.edit()!!.remove(key + ".w")!!.remove(key + ".h")!!.remove(key + ".dpi")!!.apply()
        report("Monitor association forgotten. Named profiles and current display unchanged.")
        notifyUi()
    }

    private class Cleanup {
        @Volatile @JvmField var succeeded: Boolean = true
    }

    open fun stopDesktop() {
        stopDesktopWithResult()
    }

    private fun stopDesktopWithResult(): Cleanup {
        val cleanup = Cleanup()
        sessionAudio.end()
        val hadSplit = split != null
        cancelAppSelection()
        cancelMonitorStartupDeadline()
        desktopApp = false
        protectedContent = false
        cancelInput()
        if (split != null)
            split!!.detach(false, { success -> if (!success!!) cleanup.succeeded = false })
        generation++
        val stoppedGeneration = generation
        id = -1
        busy = false
        main.removeCallbacks(rollback!!)
        if (pendingProfile != null) {
            baseW = pendingProfile!![0]
            baseH = pendingProfile!![1]
            dpi = pendingProfile!![2]
            rotation = pendingProfile!![3]
            defaultRotation = pendingProfile!![4]
        }
        pendingProfile = null
        pendingNamedProfile = null
        rollbackRequested = false
        revertingProfile = false
        val old = monitor
        if (old != null) {
            monitor = null
            if (old!!.shell != null) old!!.shell!!.destroy()
            old!!.discardPendingTouch()
            if (old!!.cancelOutputLayout != null) old!!.cancelOutputLayout!!.run()
            old!!.dismiss()
            if (old!!.host != null) old!!.host!!.finish()
        }
        report("Desktop stopped")
        val target = bridge
        if (target != null || old != null || hadSplit)
            worker.execute({
                try {
                    if (target != null) target!!.stop()
                } catch (error: Throwable) {
                    cleanup.succeeded = false
                    log("Desktop cleanup failed: " + error!!.message)
                } finally {
                    if (old != null && old!!.retainedSurface != null)
                        old!!.retainedSurface!!.release()
                    if (!cleanup.succeeded)
                        main.post({
                            if ((generation != stoppedGeneration || autoStartRequested)) return@post
                            status =
                                "Desktop stopped. Cleanup unconfirmed. Reopen DexDroid on the phone."
                            notifyUi()
                        })
                }
            })
        if (sessionWakeLock != null && sessionWakeLock!!.isHeld()) sessionWakeLock!!.release()
        stopForeground(true)
        stopSelf()
        return cleanup
    }

    public override fun onDisplayAdded(added: Int) {
        detect()
        if (external != null && external!!.getDisplayId() == added) {
            log("Monitor connected: " + external!!.getName()!!)
            if (autoStartRequested) ensureDesktop()
        }
    }

    public override fun onDisplayRemoved(removed: Int) {
        if (split != null && split!!.ownsDisplay(removed)) {
            stopDesktop()
            report("Split display removed. Reopen DexDroid.")
        }
        if (external != null && external!!.getDisplayId() == removed) {
            stopDesktop()
            report("Monitor disconnected. Waiting for reconnection.")
        }
        detect()
    }

    public override fun onDisplayChanged(changed: Int) {
        if (external != null && external!!.getDisplayId() == changed) {
            val previousIdentity = monitorIdentity()
            detect()
            if (monitor != null && previousIdentity != monitorIdentity()) {
                stopDesktop()
                if (autoStartRequested) startDesktop()
                return
            }
        }
        if (monitor != null && monitor!!.getDisplay()!!.getDisplayId() == changed)
            monitor!!.transform()
    }

    public override fun onDestroy() {
        autoStartRequested = false
        appCatalog!!.close()
        stopDesktop()
        sessionAudio.close()
        displays!!.unregisterDisplayListener(this)
        Shizuku.removeRequestPermissionResultListener(permission!!)
        Shizuku.removeBinderDeadListener(death!!)
        Shizuku.removeBinderReceivedListener(received!!)
        if (bound)
            try {
                Shizuku.unbindUserService(args!!, connection, true)
            } catch (ignored: Throwable) {}

        worker.shutdown()
        super.onDestroy()
    }

    open fun attachMonitor(host: MonitorActivity?, token: Int): Boolean {
        if (
            (monitor == null ||
                token != generation ||
                monitor!!.token != host!!.getIntent()!!.getStringExtra("monitorToken") ||
                external == null ||
                host!!.getDisplay()!!.getDisplayId() != external!!.getDisplayId() ||
                (monitor!!.host != null && monitor!!.host != host))
        )
            return false
        monitor!!.host = host
        cancelMonitorStartupDeadline()
        host!!
            .getWindow()!!
            .addFlags(
                (WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            )
        host!!.immersive()
        val content = monitor!!.content()
        host!!.setContentView(content)
        return true
    }

    open fun detachMonitor(host: MonitorActivity?) {
        if (monitor != null && monitor!!.host == host) stopDesktop()
    }

    open fun ownsMonitorHost(host: MonitorActivity?): Boolean {
        return monitor != null && monitor!!.host == host
    }

    open fun monitorHostTouch(host: MonitorActivity?, event: MotionEvent?): Boolean {
        return ownsMonitorHost(host) && monitor!!.handleTouch(event)
    }

    private open inner class Monitor(display: Display?) :
        Presentation(this@SessionService, display, R.style.DexDroidTheme),
        TextureView.SurfaceTextureListener {
        @JvmField val token: String? = UUID.randomUUID().toString()
        @JvmField val initialOutput: DisplayMetrics = DisplayMetrics()
        @JvmField val outputRotation: Int
        @JvmField var host: MonitorActivity? = null
        @JvmField var texture: TextureView? = null
        @JvmField var retainedSurface: SurfaceTexture? = null
        @JvmField var stagedWidth: Int = 0
        @JvmField var stagedHeight: Int = 0
        @JvmField var cancelOutputLayout: Runnable? = null
        @JvmField var cursor: View? = null
        @JvmField var frame: FrameLayout? = null
        @JvmField var desktopLayer: FrameLayout? = null
        @JvmField var shell: MonitorShell? = null

        private val pendingTouch: ArrayDeque<MotionEvent?> = ArrayDeque<MotionEvent?>()
        var waitingForFocus: Boolean = false
        private val focusTimeout: Runnable? = Runnable { releasePendingTouch(false) }
        private val focusSettled: Runnable? = Runnable { releasePendingTouch(true) }

        init {
            display!!.getRealMetrics(initialOutput)
            outputRotation = display!!.getRotation()
        }

        public override fun cancel() {
            // Android 11 Presentation compares resource/app-inset metrics and can
            // cancel after a phone-only rotation with forced monitor density.
            // Our pixel layout handles unchanged output; explicit Stop uses dismiss().
            if (
                (Build.VERSION.SDK_INT == 30 &&
                    monitor === this &&
                    frame != null &&
                    getDisplay()!!.isValid())
            ) {
                val current = DisplayMetrics()
                getDisplay()!!.getRealMetrics(current)
                if (
                    (current.widthPixels == initialOutput.widthPixels &&
                        current.heightPixels == initialOutput.heightPixels &&
                        current.densityDpi == initialOutput.densityDpi &&
                        getDisplay()!!.getRotation() == outputRotation)
                ) {
                    log(
                        "Monitor retained: unchanged physical output after configuration notification"
                    )
                    transform()
                    return
                }
            }
            super.cancel()
        }

        public override fun onBackPressed() {
            this@SessionService.key(KeyEvent.KEYCODE_BACK)
        }

        public override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled())
                    this@SessionService.key(KeyEvent.KEYCODE_BACK)
                return true
            }
            return hardwareKey(event) || super.dispatchKeyEvent(event)
        }

        public override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
            return ((frame != null &&
                hardwarePointer(
                    event,
                    frame!!.getWidth().toFloat(),
                    frame!!.getHeight().toFloat(),
                )) || super.dispatchGenericMotionEvent(event))
        }

        protected override fun onCreate(state: Bundle?) {
            super.onCreate(state)
            getWindow()!!.addFlags(
                (WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            )
            getWindow()!!.getDecorView().setSystemUiVisibility(5894)
            setContentView(content()!!)
        }

        open fun content(): FrameLayout? {
            if (frame != null) return frame
            frame = FrameLayout(getContext())
            frame!!.setClipChildren(false)
            frame!!.setBackgroundColor(Color.BLACK)
            texture = TextureView(getContext())
            texture!!.setSurfaceTextureListener(this)
            desktopLayer = FrameLayout(getContext())
            desktopLayer!!.setBackgroundColor(Color.BLACK)
            desktopLayer!!.addView(texture, FrameLayout.LayoutParams(-1, -1))
            frame!!.addView(desktopLayer, FrameLayout.LayoutParams(-1, -1))
            shell = MonitorShell(this@SessionService, getContext())
            frame!!.addView(shell!!.root, FrameLayout.LayoutParams(width(), height()))
            frame!!.addOnLayoutChangeListener({ v, l, t, r, b, ol, ot, or, ob ->
                transform()
                val window = if (host != null) host!!.getWindow() else getWindow()
                val edge =
                    Math.round((24 * getContext().getResources()!!.getDisplayMetrics()!!.density))
                window!!.setSystemGestureExclusionRects(
                    java.util.Arrays.asList<Rect?>(
                        Rect(0, 0, edge, v!!.getHeight()),
                        Rect(Math.max(0, v!!.getWidth() - edge), 0, v!!.getWidth(), v!!.getHeight()),
                    )
                )
            })
            cursor =
                object : View(getContext()) {
                    protected override fun onDraw(canvas: Canvas) {
                        if (!pointerVisible) return
                        val point =
                            Geometry.map(
                                cx / width(),
                                cy / height(),
                                this@SessionService.rotation,
                                getWidth().toFloat(),
                                getHeight().toFloat(),
                                width(),
                                height(),
                            )
                        val p = Paint(3)
                        p.setColor(Color.BLACK)
                        canvas.drawCircle(point[0], point[1], 13f, p)
                        p.setColor(Color.rgb(94, 234, 212))
                        canvas.drawCircle(point[0], point[1], 9f, p)
                    }
                }
            frame!!.addView(cursor, FrameLayout.LayoutParams(-1, -1))
            return frame
        }

        fun discardPendingTouch() {
            waitingForFocus = false
            main.removeCallbacks(focusTimeout!!)
            main.removeCallbacks(focusSettled!!)
            while (!pendingTouch.isEmpty()) pendingTouch.removeFirst()!!.recycle()
        }

        private fun holdTouch(event: MotionEvent) {
            val last = pendingTouch.peekLast()
            if (
                (event.getActionMasked() == MotionEvent.ACTION_MOVE &&
                    last != null &&
                    last!!.getActionMasked() == MotionEvent.ACTION_MOVE)
            )
                pendingTouch.removeLast()!!.recycle()
            if (pendingTouch.size >= 16) {
                discardPendingTouch()
                monitorBlocked = true
                log("Monitor touch canceled: host focus buffer full")
                return
            }
            pendingTouch.addLast(MotionEvent.obtain(event)!!)
        }

        public override fun onWindowFocusChanged(focused: Boolean) {
            super.onWindowFocusChanged(focused)
            if (focused) {
                if (split != null && waitingForFocus) main.postDelayed(focusSettled!!, 16)
                else releasePendingTouch(true)
            }
        }

        private fun releasePendingTouch(focused: Boolean) {
            if (!waitingForFocus) return
            waitingForFocus = false
            main.removeCallbacks(focusTimeout!!)
            main.removeCallbacks(focusSettled!!)
            if (monitor !== this || texture == null || busy || id < 0) {
                discardPendingTouch()
                monitorBlocked = true
                return
            }
            val first = pendingTouch.peekFirst()
            val delay =
                if (first == null) 0 else SystemClock.uptimeMillis() - first!!.getEventTime()
            log(
                ("Legacy monitor contact relay: " +
                    (if (focused) "host focus" else "settled fallback") +
                    " after " +
                    delay +
                    " ms")
            )
            // Shared cancellation during DOWN initialization must not recycle the
            // rest of this already-released batch. All dispatch stays on main.
            val released = ArrayDeque<MotionEvent?>(pendingTouch)
            pendingTouch.clear()
            while (!released.isEmpty()) {
                val event = released.removeFirst()
                try {
                    monitorTouch(event, frame!!.getWidth().toFloat(), frame!!.getHeight().toFloat())
                } finally {
                    event!!.recycle()
                }
            }
        }

        open fun handleTouch(event: MotionEvent?): Boolean {
            if (texture != null) {
                if (
                    shell!!.physical(
                        event!!,
                        frame!!.getWidth().toFloat(),
                        frame!!.getHeight().toFloat(),
                    )
                ) {
                    discardPendingTouch()
                    return true
                }
                if (
                    (Build.VERSION.SDK_INT == 30 &&
                        ((event!!.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) ||
                            ((split != null && event!!.isFromSource(InputDevice.SOURCE_MOUSE))))))
                ) {
                    if (
                        (event!!.getActionMasked() == MotionEvent.ACTION_DOWN &&
                            (split != null || !getWindow()!!.getDecorView().hasWindowFocus()))
                    ) {
                        discardPendingTouch()
                        cancelPhoneInput()
                        waitingForFocus = true
                        if (split != null && getWindow()!!.getDecorView().hasWindowFocus())
                            main.postDelayed(focusSettled!!, 16)
                        else main.postDelayed(focusTimeout!!, 100)
                    }
                    if (waitingForFocus) {
                        holdTouch(event!!)
                        return true
                    }
                }
                if (
                    monitorTouch(event, frame!!.getWidth().toFloat(), frame!!.getHeight().toFloat())
                )
                    return true
            }
            return false
        }

        public override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            return handleTouch(event) || super.dispatchTouchEvent(event)
        }

        open fun transform() {
            if (texture == null) return
            val w = frame!!.getWidth()
            val h = frame!!.getHeight()
            if (w == 0 || h == 0) return
            val dw = if (stagedWidth > 0) stagedWidth else width()
            val dh = if (stagedHeight > 0) stagedHeight else height()
            val lp = desktopLayer!!.getLayoutParams() as FrameLayout.LayoutParams?
            if (lp!!.width != dw || lp!!.height != dh)
                desktopLayer!!.setLayoutParams(FrameLayout.LayoutParams(dw, dh))
            val scale =
                Math.min(
                    w.toFloat() / (if (rotation % 2 == 0) dw else dh),
                    h.toFloat() / (if (rotation % 2 == 0) dh else dw),
                )
            desktopLayer!!.setPivotX(dw / 2f)
            desktopLayer!!.setPivotY(dh / 2f)
            desktopLayer!!.setScaleX(scale)
            desktopLayer!!.setScaleY(scale)
            desktopLayer!!.setRotation(rotation * 90f)
            desktopLayer!!.setX((w - dw) / 2f)
            desktopLayer!!.setY((h - dh) / 2f)
            texture!!.setTransform(Matrix())
            if (split != null) split!!.layoutViews()
            else {
                val size = texture!!.getLayoutParams() as FrameLayout.LayoutParams?
                if (
                    (size!!.width != dw ||
                        size!!.height != dh ||
                        size!!.leftMargin != 0 ||
                        size!!.topMargin != 0)
                )
                    texture!!.setLayoutParams(FrameLayout.LayoutParams(dw, dh))
            }
            shell!!.layout(w, h)
            cursor!!.invalidate()
        }

        open fun afterOutputLayout(width: Int, height: Int, ready: Runnable?, failed: Runnable?) {
            if (cancelOutputLayout != null) cancelOutputLayout!!.run()
            stagedWidth = width
            stagedHeight = height
            transform()
            val observer = frame!!.getViewTreeObserver()
            val done = booleanArrayOf(false)
            val timeout = arrayOf<Runnable?>(null)
            val listener = arrayOf<ViewTreeObserver.OnPreDrawListener?>(null)
            val cancel = Runnable cancel@{
                if (done!![0]) return@cancel
                done[0] = true
                if (timeout!![0] != null) main.removeCallbacks(timeout!![0]!!)
                if (observer!!.isAlive()) observer!!.removeOnPreDrawListener(listener!![0])
                cancelOutputLayout = null
            }
            cancelOutputLayout = cancel
            timeout[0] = Runnable {
                cancel!!.run()
                if (monitor === this) failed!!.run()
            }
            listener[0] =
                ViewTreeObserver.OnPreDrawListener preDraw@{
                    if (monitor !== this) {
                        cancel!!.run()
                        return@preDraw true
                    }
                    if (texture!!.getWidth() != width || texture!!.getHeight() != height)
                        return@preDraw true
                    cancel!!.run()
                    try {
                        ready!!.run()
                    } catch (error: RuntimeException) {
                        failed!!.run()
                    }

                    true
                }
            observer!!.addOnPreDrawListener(listener!![0])
            main.postDelayed(timeout!![0]!!, 2000)
            frame!!.requestLayout()
            frame!!.invalidate()
        }

        open fun outputSurface(width: Int, height: Int): Surface {
            texture!!.getSurfaceTexture()!!.setDefaultBufferSize(width, height)
            return Surface(texture!!.getSurfaceTexture())
        }

        public override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {
            retainedSurface = surface
            if (monitor !== this) {
                surface.release()
                return
            }
            surface.setDefaultBufferSize(width(), height())
            transform()
            val target = Surface(surface)
            val token = generation
            val service = bridge
            worker.execute({
                try {
                    val created =
                        service!!.create(
                            target,
                            width(),
                            height(),
                            dpi,
                            sessionOwner,
                            object : IDesktopListener.Stub() {
                                public override fun onProtectionState(display: Int, state: Int) {
                                    main.post({
                                        if ((token != generation || id != display)) return@post
                                        if (split != null) {
                                            split!!.protection(0, state)
                                            return@post
                                        }
                                        if ((state == ProtectedWindows.State.UNKNOWN.ordinal)) {
                                            log("Protected-window detection unavailable")
                                            return@post
                                        }
                                        val detected =
                                            (desktopApp &&
                                                (state == ProtectedWindows.State.PROTECTED.ordinal))
                                        if (protectedContent == detected) return@post
                                        protectedContent = detected
                                        log(
                                            if (detected) "Protected desktop window detected"
                                            else "Protected desktop notice cleared"
                                        )
                                        notifyUi()
                                    })
                                }

                                public override fun onTaskState(
                                    display: Int,
                                    occupied: Boolean,
                                    component: String?,
                                    error: String?,
                                ) {
                                    main.post({
                                        if ((token != generation || id != display)) return@post
                                        if (split != null) {
                                            split!!.occupancy(0, occupied, component, error)
                                            return@post
                                        }
                                        if (!error!!.isEmpty()) {
                                            log(("Desktop task state unavailable: " + error!!))
                                            return@post
                                        }
                                        val top = ComponentName.unflattenFromString(component!!)
                                        val appChanged = (top != null && top != activeComponent)
                                        if ((desktopApp == occupied && !appChanged)) return@post
                                        if (appChanged) {
                                            activeComponent = top
                                            activePackage = top!!.getPackageName()
                                        }
                                        desktopApp = occupied
                                        if (!occupied) {
                                            protectedContent = false
                                            cancelInput()
                                            hideMonitorPointer()
                                            activePackage = ""
                                            activeComponent = null
                                            report("No app on monitor. Choose Apps.")
                                        }
                                        notifyUi()
                                    })
                                }
                            },
                        )
                    main.post({
                        if (token != generation) return@post
                        id = created
                        if (service != null) sessionAudio.begin(service)
                        busy = false
                        report(
                            ("Desktop ready • " +
                                width() +
                                " × " +
                                height() +
                                " • " +
                                dpi +
                                " UI DPI. Choose Apps.")
                        )
                        if (savedSplitLayout().enabled) enableSplit() else launchPending()
                    })
                } catch (e: Throwable) {
                    main.post({
                        if (token == generation) {
                            stopDesktop()
                            report("Desktop creation failed: " + e!!.message)
                        }
                    })
                } finally {
                    target.release()
                }
            })
        }

        public override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) {
            transform()
        }

        public override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            if (monitor === this) {
                stopDesktop()
            }
            // stopDesktop releases this texture after the shell display is detached.
            return false
        }

        public override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }
}
