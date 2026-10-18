package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.display.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.FrameLayout;
import java.util.*;
import java.util.concurrent.*;
import rikka.shizuku.Shizuku;

/** Owns the desktop independently of the controller Activity. */
public class SessionService extends Service implements DisplayManager.DisplayListener {
    public interface Listener {
        void changed();
    }

    public volatile Listener listener;
    AppCatalog appCatalog;
    volatile SplitWorkspace split;
    ComponentName activeComponent;
    private boolean desktopApp;
    volatile boolean protectedContent;
    public String status = "Connect Shizuku to begin", activePackage = "";
    public volatile int id = -1,
            rotation = 0,
            defaultRotation = 0,
            baseW = 1920,
            baseH = 1200,
            dpi = 240;
    public float cx = 960, cy = 600;
    boolean pointerVisible = false;
    public volatile boolean busy = false;
    public Display external;
    public SharedPreferences prefs;
    ShizukuSetup.State shizukuState = ShizukuSetup.State.STOPPED;
    private IDeskService bridge;
    private Monitor monitor;
    private DisplayManager displays;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker =
            new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    private final IBinder binder = new LocalBinder();
    private volatile int generation = 0;
    private final IBinder sessionOwner = new Binder();
    private boolean bound = false, permissionRequested = false, permissionDenied = false;
    private volatile boolean autoStartRequested = false;
    private ComponentName pendingApp;
    private Integer pendingOrientation;
    private final DiagnosticLog diagnostics = new DiagnosticLog();
    private int[] pendingProfile;
    private String profileKey;
    private String detectedIdentity, pendingMonitorIdentity;
    private MonitorProfiles.Profile pendingNamedProfile;
    private int pendingMonitorDisplay = -1;
    private boolean rollbackRequested, revertingProfile;
    private float[] contactsX, contactsY;
    private MotionEvent monitorContacts;
    private boolean monitorBlocked;
    private PowerManager.WakeLock sessionWakeLock;
    private final Runnable rollback = () -> confirm(false);
    private Shizuku.UserServiceArgs args;

    public class LocalBinder extends Binder {
        public SessionService get() {
            return SessionService.this;
        }
    }

    private final Shizuku.OnRequestPermissionResultListener permission =
            (code, result) ->
                    main.post(
                            () -> {
                                if (code == 71) {
                                    permissionRequested = false;
                                    permissionDenied = result != PackageManager.PERMISSION_GRANTED;
                                    if (!permissionDenied) connect();
                                    else
                                        report(
                                                "Shizuku authorization denied. Retry from Options when ready.");
                                }
                            });
    private final Shizuku.OnBinderReceivedListener received =
            () ->
                    main.post(
                            () -> {
                                log("Shizuku service available");
                                if (autoStartRequested) connect();
                            });
    private final Shizuku.OnBinderDeadListener death =
            () ->
                    main.post(
                            () -> {
                                bridge = null;
                                bound = false;
                                stopDesktop();
                                shizukuState = ShizukuSetup.State.STOPPED;
                                report("Shizuku stopped. Open Shizuku to restart its service.");
                            });
    private final ServiceConnection connection =
            new ServiceConnection() {
                public void onServiceConnected(ComponentName name, IBinder value) {
                    main.post(
                            () -> {
                                bridge = IDeskService.Stub.asInterface(value);
                                shizukuState = ShizukuSetup.State.CONNECTED;
                                report("Shizuku connected");
                                if (autoStartRequested) startDesktop();
                            });
                }

                public void onServiceDisconnected(ComponentName name) {
                    main.post(
                            () -> {
                                bridge = null;
                                bound = false;
                                stopDesktop();
                                refreshShizukuState();
                                report("Shizuku disconnected. Retry from Options.");
                            });
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("desktop", 0);
        appCatalog = new AppCatalog(this, this::log);
        prefs.edit().remove("androidKeyboardMonitor").apply();
        defaultRotation = prefs.getInt("rotation", 0);
        rotation = defaultRotation;
        args =
                new Shizuku.UserServiceArgs(new ComponentName(this, DeskService.class))
                        .daemon(false)
                        .processNameSuffix("desktop")
                        .debuggable(true)
                        .version(BuildConfig.VERSION_CODE);
        sessionWakeLock =
                ((PowerManager) getSystemService(POWER_SERVICE))
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DexDroid:desktop");
        sessionWakeLock.setReferenceCounted(false);
        displays = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        displays.registerDisplayListener(this, main);
        Shizuku.addRequestPermissionResultListener(permission);
        Shizuku.addBinderDeadListener(death);
        Shizuku.addBinderReceivedListenerSticky(received);
        detect();
        log("Controller service created");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "stop".equals(intent.getAction())) pauseDesktop();
        return START_NOT_STICKY;
    }

    public void log(String message) {
        diagnostics.add(message);
        android.util.Log.i("DexDroid", message);
    }

    public String diagnosticText() {
        return "DexDroid "
                + BuildConfig.VERSION_NAME
                + " | Android "
                + Build.VERSION.RELEASE
                + " | "
                + Build.MODEL
                + "\nMonitor: "
                + (external == null ? "disconnected" : external.getName())
                + " | Virtual display: "
                + id
                + "\nDesktop: "
                + width()
                + "x"
                + height()
                + " @ "
                + dpi
                + " DPI | Rotation: "
                + (rotation * 90)
                + "°\nShizuku bridge: "
                + (bridge == null ? "disconnected" : "connected")
                + " | Busy: "
                + busy
                + "\nLast 250 events, memory only. Key sent means IPC completed, not proof an app handled it.\n\n"
                + diagnostics.snapshot();
    }

    public void clearDiagnostics() {
        diagnostics.clear();
    }

    public boolean needsRecovery() {
        return bridge == null;
    }

    public boolean desktopRequested() {
        return autoStartRequested;
    }

    public boolean hasDesktopApp() {
        return desktopApp;
    }

    boolean splitEnabled() {
        return split != null;
    }

    SplitLayout savedSplitLayout() {
        return SplitLayoutPreferences.read(prefs.getAll());
    }

    void rememberSplitLayout(SplitLayout layout) {
        SharedPreferences.Editor edit = prefs.edit();
        for (Map.Entry<String, Object> item : SplitLayoutPreferences.write(layout).entrySet()) {
            if (item.getValue() instanceof Boolean)
                edit.putBoolean(item.getKey(), (Boolean) item.getValue());
            else edit.putFloat(item.getKey(), (Float) item.getValue());
        }
        edit.apply();
    }

    static final class AppSelection {
        final SplitWorkspace workspace;
        final int generation, display, pane;
        final long serial;

        AppSelection(SplitWorkspace workspace, int generation, int display, int pane, long serial) {
            this.workspace = workspace;
            this.generation = generation;
            this.display = display;
            this.pane = pane;
            this.serial = serial;
        }
    }

    private AppSelection appSelection;
    private long appSelectionSerial;
    private boolean appSelectionPreview;

    AppSelection beginAppSelection(int pane) {
        return beginAppSelection(pane, true);
    }

    AppSelection beginAppSelection(int pane, boolean select) {
        if (split != null && (busy || awaitingConfirmation() || pane < 0 || pane > 1)) return null;
        cancelAppSelection();
        if (split != null && select) split.select(pane);
        appSelection =
                new AppSelection(
                        split, generation, id, split == null ? 0 : pane, ++appSelectionSerial);
        notifyUi();
        return appSelection;
    }

    AppSelection resumeAppSelection(AppSelection origin) {
        if (origin == null
                || origin.generation != generation
                || origin.display != id
                || id < 0
                || origin.serial != appSelectionSerial
                || appSelection != null
                || busy
                || awaitingConfirmation()) return null;
        return beginAppSelection(origin.pane, false);
    }

    boolean currentAppSelection(AppSelection choice) {
        return choice != null
                && appSelection == choice
                && choice.workspace == split
                && choice.generation == generation
                && (choice.display == id || choice.display < 0);
    }

    SplitLayout.Box appSelectionBox() {
        if (!appSelectionPreview || !currentAppSelection(appSelection) || id < 0) return null;
        if (split != null && split.state().maximized >= 0)
            return new SplitLayout.Box(0, 0, width(), height());
        return split == null
                ? new SplitLayout.Box(0, 0, width(), height())
                : split.state().pane(appSelection.pane, width(), height(), split.gap());
    }

    void finishAppSelection(AppSelection choice) {
        if (choice != null && appSelection == choice) cancelAppSelection();
    }

    void cancelAppSelection() {
        if (appSelection == null) return;
        appSelection = null;
        appSelectionPreview = false;
        notifyUi();
    }

    void previewAppSelection(AppSelection choice, boolean pressed) {
        if (!currentAppSelection(choice)) return;
        appSelectionPreview = pressed;
        notifyUi();
    }

    boolean launchSelection(AppSelection choice, ComponentName component, Integer rotation) {
        if (!currentAppSelection(choice)) {
            report("App destination changed. Choose its pane again.");
            return false;
        }
        if (id >= 0 && (busy || awaitingConfirmation())) {
            report("Wait for the display change to finish");
            return false;
        }
        finishAppSelection(choice);
        if (split == null) launch(component, rotation);
        else split.launch(component, rotation, choice.pane);
        return true;
    }

    String layoutStatus() {
        return split == null ? "Single view" : split.summary();
    }

    IDeskService primaryBridge() {
        return bridge;
    }

    IBinder splitOwner() {
        return sessionOwner;
    }

    void desktopWork(Runnable work) {
        worker.execute(work);
    }

    void reconfigureSplit() {
        configure(baseW, baseH, dpi, rotation, null);
    }

    void reconfigureSplit(Runnable after) {
        configure(baseW, baseH, dpi, rotation, after);
    }

    void splitGeometryChanged() {
        if (monitor != null) monitor.transform();
    }

    void splitApps(int pane) {
        if (monitor != null && monitor.shell != null) monitor.shell.chooseApp(pane);
    }

    void splitPrimaryState(boolean occupied, boolean secure) {
        desktopApp = occupied;
        protectedContent = secure;
        notifyUi();
    }

    void splitStateChanged() {
        if (split == null) return;
        desktopApp = split.anyApp();
        protectedContent = split.anyProtected();
        activePackage = split.selectedPackage();
        activeComponent = split.selectedComponent();
        notifyUi();
    }

    void enableSplit() {
        enableSplit(null);
    }

    void enableSplit(Boolean vertical) {
        if (split != null || busy || id < 0 || monitor == null || awaitingConfirmation()) {
            report("Start the desktop and finish its display change first");
            return;
        }
        cancelInput();
        busy = true;
        try {
            split = new SplitWorkspace(this, monitor.desktopLayer, monitor.texture, vertical);
            monitor.transform();
            report("Starting split view…");
        } catch (RuntimeException error) {
            stopDesktop();
            report("Split setup failed. Reopen DexDroid: " + error.getMessage());
        }
    }

    boolean hardwareKey(KeyEvent event) {
        return split != null
                && (monitor == null || monitor.shell == null || !monitor.shell.isOpen())
                && split.hardwareKey(event);
    }

    boolean hardwarePointer(MotionEvent event, float width, float height) {
        if (split == null || (monitor != null && monitor.shell != null && monitor.shell.isOpen()))
            return false;
        return split.physical(event, width, height);
    }

    void controllerPointer(MotionEvent event, float width, float height) {
        if (split == null
                || id < 0
                || busy
                || physicalMonitorInputActive()
                || width <= 0
                || height <= 0) return;
        MotionEvent mapped = MotionEvent.obtain(event);
        try {
            Matrix scale = new Matrix();
            scale.setScale(width() / width, height() / height);
            mapped.transform(scale);
            cx = Geometry.clamp(mapped.getX(), 0, width() - 1);
            cy = Geometry.clamp(mapped.getY(), 0, height() - 1);
            showTouchpadPointer();
            if (monitor != null && monitor.shell != null && monitor.shell.controllerPointer(mapped))
                return;
            split.phonePointer(mapped);
        } finally {
            mapped.recycle();
        }
    }

    public void ensureDesktop() {
        autoStartRequested = true;
        if (bridge == null) connect();
        else startDesktop();
    }

    public void retryDesktop() {
        permissionDenied = false;
        permissionRequested = false;
        ensureDesktop();
    }

    public void pauseDesktop() {
        Listener controllerOwner = listener;
        ActivityManager.AppTask controller = controllerOwner == null ? null : controllerTask();
        log(
                "Explicit Stop: controller="
                        + (controller != null)
                        + ", owner="
                        + (controllerOwner != null));
        autoStartRequested = false;
        pendingApp = null;
        pendingOrientation = null;
        Cleanup cleanup = stopDesktopWithResult();
        int stoppedGeneration = generation;
        // The serial worker owns both cleanup operations and the return. A normal
        // AppTask request can be silently refused after a migrated app backgrounds
        // us, so the existing shell focuses only the captured live controller task.
        IDeskService target = bridge;
        if (controller != null && target != null) {
            final int taskId;
            try {
                taskId = controller.getTaskInfo().taskId;
            } catch (RuntimeException error) {
                log("Stop controller task lookup: " + error.getMessage());
                return;
            }
            worker.execute(
                    () -> {
                        if (generation != stoppedGeneration
                                || autoStartRequested
                                || listener != controllerOwner
                                || !cleanup.succeeded) {
                            log(
                                    "Stop controller return cancelled: obsolete owner/session or unconfirmed cleanup");
                            return;
                        }
                        try {
                            boolean focused = target.focusController(taskId);
                            log(
                                    focused
                                            ? "Stop controller focus requested through shell"
                                            : "Stop controller no longer exists; return skipped");
                        } catch (Exception error) {
                            log("Controller return after Stop: " + error.getMessage());
                            main.post(
                                    () -> {
                                        if (generation == stoppedGeneration
                                                && !autoStartRequested
                                                && listener == controllerOwner) {
                                            status =
                                                    "Desktop stopped. Reopen DexDroid on the phone.";
                                            notifyUi();
                                        }
                                    });
                        }
                    });
        }
    }

    private ActivityManager.AppTask controllerTask() {
        try {
            ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            for (ActivityManager.AppTask task : manager.getAppTasks()) {
                ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                ComponentName base = info.baseIntent.getComponent();
                if (info.numActivities > 0
                        && base != null
                        && base.equals(new ComponentName(this, MainActivity.class))) return task;
            }
        } catch (RuntimeException error) {
            log("Controller task lookup after Stop: " + error.getMessage());
        }
        return null;
    }

    public void report(String message) {
        report(message, message);
    }

    private void report(String message, String diagnosticMessage) {
        log(diagnosticMessage);
        main.post(
                () -> {
                    status = message;
                    notifyUi();
                });
    }

    public void notifyUi() {
        if (listener != null) listener.changed();
        if (monitor != null && monitor.shell != null) monitor.shell.refresh();
    }

    void refreshShizukuState() {
        shizukuState = ShizukuSetup.detect(this, bridge != null);
    }

    public void connect() {
        try {
            if (!Shizuku.pingBinder()) {
                refreshShizukuState();
                report(
                        getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                        == null
                                ? "Install Shizuku, then start its wireless-debugging service."
                                : "Shizuku is stopped. Open Shizuku and start it through wireless debugging.");
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                shizukuState = ShizukuSetup.State.PERMISSION;
                if (!permissionRequested && !permissionDenied) {
                    permissionRequested = true;
                    log("Requesting Shizuku authorization");
                    Shizuku.requestPermission(71);
                }
                return;
            }
            shizukuState = bridge != null ? ShizukuSetup.State.CONNECTED : ShizukuSetup.State.READY;
            if (bridge != null) {
                if (autoStartRequested) startDesktop();
                return;
            }
            if (bound) return;
            bound = true;
            Shizuku.bindUserService(args, connection);
            report("Connecting to Shizuku…");
        } catch (Throwable e) {
            bound = false;
            permissionRequested = false;
            report("Connection failed: " + e.getMessage());
        }
    }

    public void detect() {
        external = null;
        for (Display display : displays.getDisplays()) {
            int flags = display.getFlags();
            if (display.getDisplayId() != Display.DEFAULT_DISPLAY
                    && !display.getName().contains("DexDroid")
                    && !display.getName().contains("PortraitDesk")
                    && (display.getName().contains("HDMI")
                            || (flags & Display.FLAG_PRESENTATION) != 0)) {
                external = display;
                break;
            }
        }
        detectedIdentity = null;
        if (external != null) {
            String unique = null;
            try {
                unique = (String) Display.class.getMethod("getUniqueId").invoke(external);
            } catch (Exception unavailable) {
                // Hidden on some ROMs; capability matching remains usable without bypassing policy.
            }
            ArrayList<String> modes = new ArrayList<>();
            for (Display.Mode mode : external.getSupportedModes())
                modes.add(
                        mode.getPhysicalWidth()
                                + "x"
                                + mode.getPhysicalHeight()
                                + "@"
                                + Math.round(mode.getRefreshRate()));
            detectedIdentity = MonitorProfiles.identity(unique, external.getName(), modes);
        }
        notifyUi();
    }

    String monitorIdentity() {
        return external == null ? null : detectedIdentity;
    }

    String monitorMatchingDescription() {
        if (external == null) return "Connect a monitor to remember its profile";
        return detectedIdentity != null && detectedIdentity.startsWith("physical.")
                ? "Matches Android's physical monitor identity. Port/adapter changes may require reassignment."
                : "Matches monitor name and reported modes. Identical monitors share this binding; switch profiles manually if needed.";
    }

    MonitorProfiles monitorProfiles() {
        return new MonitorProfiles(new MonitorProfilePreferences(prefs));
    }

    MonitorProfiles.Configuration recommendedMonitorConfiguration() {
        MonitorProfiles.Configuration remembered = monitorProfiles().remembered(monitorIdentity());
        if (remembered != null) return remembered;
        int w = external == null ? 1920 : external.getMode().getPhysicalWidth();
        int h = external == null ? 1200 : external.getMode().getPhysicalHeight();
        float scale = Math.min(1f, 4096f / Math.max(w, h));
        w = Math.max(480, Math.round(w * scale));
        h = Math.max(480, Math.round(h * scale));
        return new MonitorProfiles.Configuration(
                w,
                h,
                Geometry.recommendedDpi(Math.min(w, h)),
                Math.floorMod(prefs.getInt("rotation", 0), 4));
    }

    String monitorProfileStatus() {
        MonitorProfiles.Profile profile = monitorProfiles().bound(monitorIdentity());
        if (profile == null)
            return monitorProfiles().remembered(monitorIdentity()) == null
                    ? "No saved profile"
                    : "Custom monitor settings";
        boolean modified =
                id >= 0
                        && !profile.configuration.equals(
                                new MonitorProfiles.Configuration(
                                        baseW, baseH, dpi, defaultRotation));
        return profile.name + (modified ? " (modified)" : "");
    }

    private boolean profileEditable(boolean needsDesktop) {
        if (busy || awaitingConfirmation()) {
            report("Finish the pending display change first");
            return false;
        }
        if (needsDesktop && (external == null || id < 0 || monitorIdentity() == null)) {
            report("Connect a monitor and start the desktop first");
            return false;
        }
        return true;
    }

    String saveMonitorProfile(String name) {
        if (!profileEditable(true)) return null;
        try {
            MonitorProfiles.Profile profile =
                    monitorProfiles()
                            .create(
                                    name,
                                    new MonitorProfiles.Configuration(baseW, baseH, dpi, rotation),
                                    monitorIdentity());
            monitorProfiles().importLegacy(monitorIdentity(), monitorKey(), null);
            defaultRotation = rotation;
            report("Saved monitor profile: " + profile.name, "Named monitor profile saved");
            notifyUi();
            return profile.id;
        } catch (IllegalArgumentException e) {
            report(e.getMessage());
            return null;
        }
    }

    void renameMonitorProfile(String id, String name) {
        if (!profileEditable(false)) return;
        try {
            monitorProfiles().rename(id, name);
            report("Monitor profile renamed");
        } catch (IllegalArgumentException e) {
            report(e.getMessage());
        }
        notifyUi();
    }

    void updateMonitorProfile(String profileId) {
        if (!profileEditable(true)) return;
        try {
            monitorProfiles()
                    .update(
                            profileId,
                            new MonitorProfiles.Configuration(baseW, baseH, dpi, rotation));
            MonitorProfiles.Profile boundProfile = monitorProfiles().bound(monitorIdentity());
            if (boundProfile != null && boundProfile.id.equals(profileId)) {
                defaultRotation = rotation;
                monitorProfiles().clearWorking(monitorIdentity());
            }
            report("Monitor profile updated from current display");
        } catch (IllegalArgumentException e) {
            report(e.getMessage());
        }
        notifyUi();
    }

    void deleteMonitorProfile(String profileId) {
        if (!profileEditable(false)) return;
        monitorProfiles().delete(profileId);
        report("Monitor profile deleted. Current display unchanged.");
        notifyUi();
    }

    void applyMonitorProfile(String profileId) {
        if (!profileEditable(true)) return;
        MonitorProfiles.Profile profile = monitorProfiles().find(profileId);
        if (profile == null) {
            report("Profile no longer exists");
            return;
        }
        beginPreview(profile.configuration, profile);
    }

    public String monitorKey() {
        if (external == null) return "none";
        ArrayList<String> modes = new ArrayList<>();
        for (Display.Mode mode : external.getSupportedModes())
            modes.add(
                    mode.getPhysicalWidth()
                            + "x"
                            + mode.getPhysicalHeight()
                            + "@"
                            + Math.round(mode.getRefreshRate()));
        Collections.sort(modes);
        return "monitor."
                + external.getName()
                + "."
                + Integer.toHexString(modes.toString().hashCode());
    }

    public String capabilities() {
        if (external == null) return "No external monitor detected";
        Display.Mode mode = external.getMode();
        DisplayMetrics metrics = new DisplayMetrics();
        external.getRealMetrics(metrics);
        String physical =
                (metrics.xdpi >= 30
                                && metrics.xdpi <= 600
                                && metrics.ydpi >= 30
                                && metrics.ydpi <= 600)
                        ? String.format(Locale.US, "%.0f / %.0f", metrics.xdpi, metrics.ydpi)
                        : "unknown (unreliable report)";
        StringBuilder text =
                new StringBuilder(
                        external.getName()
                                + "\nCurrent output: "
                                + mode.getPhysicalWidth()
                                + " × "
                                + mode.getPhysicalHeight()
                                + " @ "
                                + Math.round(mode.getRefreshRate())
                                + " Hz\nPhysical DPI: "
                                + physical
                                + "\nReported signal modes:\n");
        for (Display.Mode m : external.getSupportedModes())
            text.append(m.getPhysicalWidth())
                    .append(" × ")
                    .append(m.getPhysicalHeight())
                    .append(" @ ")
                    .append(Math.round(m.getRefreshRate()))
                    .append(" Hz\n");
        return text
                + "\nExternal touch: "
                + externalTouchDevices()
                + "\nDesktop resolution and UI DPI are independently adjustable. Signal-mode switching is not yet validated on JoyUI. "
                + monitorMatchingDescription();
    }

    public int width() {
        return rotation % 2 == 0 ? baseW : baseH;
    }

    public int height() {
        return rotation % 2 == 0 ? baseH : baseW;
    }

    interface ForegroundPromotion {
        void start(Notification notification);
    }

    private ForegroundPromotion foregroundPromotion = this::promoteForeground;

    private void promoteForeground(Notification notification) {
        startService(new Intent(this, SessionService.class));
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(
                    1,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, notification);
    }

    private Runnable monitorStartupDeadline;

    void awaitMonitorHost() {
        cancelMonitorStartupDeadline();
        Monitor expected = monitor;
        int token = generation;
        monitorStartupDeadline =
                () -> {
                    if (monitor == expected
                            && expected != null
                            && token == generation
                            && expected.host == null) {
                        stopDesktop();
                        report("Monitor startup timed out. Open DexDroid on the phone to retry.");
                    }
                };
        main.postDelayed(monitorStartupDeadline, 5000);
    }

    private void cancelMonitorStartupDeadline() {
        if (monitorStartupDeadline != null) main.removeCallbacks(monitorStartupDeadline);
        monitorStartupDeadline = null;
    }

    public void startDesktop() {
        if (bridge == null) {
            ensureDesktop();
            return;
        }
        if (monitor != null) return;
        detect();
        if (external == null) {
            report("Waiting for an external monitor");
            return;
        }
        String key = monitorKey();
        if (prefs.contains(key + ".w")) {
            MonitorProfiles.Configuration legacy = null;
            try {
                legacy =
                        new MonitorProfiles.Configuration(
                                prefs.getInt(key + ".w", 0),
                                prefs.getInt(key + ".h", 0),
                                prefs.getInt(key + ".dpi", 0),
                                Math.floorMod(prefs.getInt("rotation", 0), 4));
            } catch (IllegalArgumentException | ClassCastException invalid) {
                log("Invalid legacy monitor settings ignored");
            }
            monitorProfiles().importLegacy(monitorIdentity(), key, legacy);
        }
        MonitorProfiles.Configuration configuration = recommendedMonitorConfiguration();
        baseW = configuration.width;
        baseH = configuration.height;
        dpi = configuration.dpi;
        rotation = defaultRotation = configuration.rotation;
        cx = width() / 2f;
        cy = height() / 2f;
        pointerVisible = false;
        busy = true;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(
                    new NotificationChannel(
                            "desktop", "Desktop session", NotificationManager.IMPORTANCE_LOW));
            PendingIntent open =
                    PendingIntent.getActivity(
                            this,
                            0,
                            new Intent(this, MainActivity.class),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE,
                            ActivityOptions.makeBasic()
                                    .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                                    .toBundle());
            PendingIntent stop =
                    PendingIntent.getService(
                            this,
                            1,
                            new Intent(this, SessionService.class).setAction("stop"),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification =
                    new Notification.Builder(this, "desktop")
                            .setSmallIcon(android.R.drawable.ic_menu_view)
                            .setContentTitle("DexDroid desktop running")
                            .setContentText("Tap to return to the touchpad")
                            .setContentIntent(open)
                            .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
                            .setOngoing(true)
                            .build();
            foregroundPromotion.start(notification);
            sessionWakeLock.acquire();
            desktopApp = false;
            activePackage = "";
            activeComponent = null;
            monitor = new Monitor(external);
            Intent hostIntent =
                    new Intent(this, MonitorActivity.class)
                            .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                            .putExtra("generation", generation)
                            .putExtra("monitorToken", monitor.token);
            if (Build.VERSION.SDK_INT >= 36
                    && getSystemService(ActivityManager.class)
                            .isActivityStartAllowedOnDisplay(
                                    this, external.getDisplayId(), hostIntent)) {
                log("Monitor host: independent Activity");
                startActivity(
                        hostIntent,
                        ActivityOptions.makeBasic()
                                .setLaunchDisplayId(external.getDisplayId())
                                .toBundle());
                awaitMonitorHost();
            } else {
                if (Build.VERSION.SDK_INT >= 36)
                    log(
                            "Monitor host: Presentation fallback; Android may close it when the phone task is removed");
                monitor.show();
            }
            report("Starting desktop…");
        } catch (Throwable e) {
            stopDesktop();
            report(
                    "Monitor unavailable: "
                            + e.getMessage()
                            + ". Open DexDroid on the phone to retry.");
        }
    }

    interface Work {
        void run(IDeskService target) throws Exception;
    }

    private void queue(Work action) {
        IDeskService target = bridge;
        int token = generation;
        worker.execute(
                () -> {
                    if (token != generation || target == null) {
                        log("Queued operation discarded: session changed or bridge unavailable");
                        return;
                    }
                    try {
                        action.run(target);
                    } catch (Throwable e) {
                        report("Desktop operation failed: " + e.getMessage());
                    }
                });
    }

    private boolean physicalMonitorInputActive() {
        return (split != null && split.physicalActive())
                || monitorContacts != null
                || (monitor != null
                        && (monitor.waitingForFocus
                                || (monitor.shell != null && monitor.shell.physicalActive())));
    }

    public void touch(int action, float[] xs, float[] ys) {
        if (id < 0 || busy || physicalMonitorInputActive()) return;
        if (split != null) {
            showTouchpadPointer();
            if (monitor != null && monitor.shell != null && monitor.shell.phone(action, xs, ys))
                return;
            split.phoneTouch(action, xs, ys);
            return;
        }
        int masked = action & 255;
        if (masked == MotionEvent.ACTION_DOWN) {
            showTouchpadPointer();
            cancelPhoneInput();
        }
        if (monitor != null && monitor.shell != null && monitor.shell.phone(action, xs, ys)) return;
        if (masked != MotionEvent.ACTION_DOWN && contactsX == null) return;
        if (masked == MotionEvent.ACTION_MOVE && worker.getQueue().size() > 32) return;
        if (masked == MotionEvent.ACTION_UP || masked == MotionEvent.ACTION_CANCEL) {
            contactsX = null;
            contactsY = null;
        } else if (masked == MotionEvent.ACTION_POINTER_UP) {
            int remaining = ((action >> 8) & 255) == 0 ? 1 : 0;
            contactsX = new float[] {xs[remaining]};
            contactsY = new float[] {ys[remaining]};
        } else {
            contactsX = xs.clone();
            contactsY = ys.clone();
        }
        int target = id;
        queue(s -> s.touch(target, action, xs, ys));
    }

    public void click() {
        touch(0, new float[] {cx}, new float[] {cy});
        touch(1, new float[] {cx}, new float[] {cy});
    }

    public float scrollSpeed() {
        return prefs.getFloat(
                "scrollSpeed." + activePackage,
                "eu.kanade.tachiyomi".equals(activePackage) ? .2f : .5f);
    }

    public boolean swipeScrolling() {
        return prefs.getBoolean(
                "swipeScroll." + activePackage,
                "eu.kanade.tachiyomi".equals(activePackage)
                        || prefs.getBoolean("swipeScroll", false));
    }

    public void setSwipeScrolling(boolean swipe) {
        if (activePackage.isEmpty()) {
            report("Launch an app first");
            return;
        }
        cancelPhoneInput();
        prefs.edit().putBoolean("swipeScroll." + activePackage, swipe).apply();
        report(
                swipe
                        ? "Swipe scrolling saved for active app"
                        : "Wheel scrolling saved for active app");
    }

    public void saveAppSettings() {
        if (activePackage.isEmpty()) {
            report("Launch an app first");
            return;
        }
        SharedPreferences.Editor saved = prefs.edit();
        if (split == null) saved.putInt("app." + activePackage, rotation);
        saved.putFloat("scrollSpeed." + activePackage, scrollSpeed())
                .putBoolean("swipeScroll." + activePackage, swipeScrolling())
                .apply();
        report(
                split == null
                        ? "Rotation, scroll mode and speed saved for active app"
                        : "Scroll mode and speed saved. Single-view app rotation retained.");
    }

    public void resetAppSettings() {
        if (activePackage.isEmpty()) {
            report("Launch an app first");
            return;
        }
        if (busy) {
            report("Wait for the display change to finish");
            return;
        }
        cancelPhoneInput();
        prefs.edit()
                .remove("app." + activePackage)
                .remove("scrollSpeed." + activePackage)
                .remove("swipeScroll." + activePackage)
                .apply();
        if (split == null && id >= 0 && rotation != defaultRotation) rotate(defaultRotation, false);
        report("Active app settings reset to defaults");
    }

    public void showTouchpadPointer() {
        if (id < 0 || busy || physicalMonitorInputActive()) return;
        pointerVisible = true;
        if (monitor != null && monitor.cursor != null) monitor.cursor.invalidate();
    }

    void hideMonitorPointer() {
        pointerVisible = false;
        if (monitor != null && monitor.cursor != null) monitor.cursor.invalidate();
    }

    public void wheel(float dx, float dy) {
        if (id < 0 || busy || physicalMonitorInputActive() || worker.getQueue().size() > 32) return;
        if (split != null) {
            showTouchpadPointer();
            if (monitor != null
                    && monitor.shell != null
                    && monitor.shell.wheel(cx, cy, dx * scrollSpeed(), dy * scrollSpeed())) return;
            split.wheel(dx, dy);
            return;
        }
        int target = id;
        float x = cx, y = cy;
        showTouchpadPointer();
        float speed = scrollSpeed();
        if (monitor != null
                && monitor.shell != null
                && monitor.shell.wheel(x, y, dx * speed, dy * speed)) return;
        queue(s -> s.scroll(target, x, y, dx * speed, dy * speed));
    }

    public void key(int key) {
        if (id < 0 || busy) {
            log(
                    "Key ignored: "
                            + KeyEvent.keyCodeToString(key)
                            + "; desktop "
                            + (id < 0 ? "unavailable" : "busy"));
            return;
        }
        if (key == KeyEvent.KEYCODE_BACK
                && monitor != null
                && monitor.shell != null
                && monitor.shell.isOpen()) {
            monitor.shell.back();
            return;
        }
        if (key == KeyEvent.KEYCODE_BACK && !desktopApp) {
            log("Idle Back ignored");
            return;
        }
        if (split != null) {
            split.keyCode(key);
            return;
        }
        int target = id;
        log("Key queued: " + KeyEvent.keyCodeToString(key) + " -> display " + target);
        queue(
                s -> {
                    s.key(target, key);
                    log("Key sent: " + KeyEvent.keyCodeToString(key) + " -> display " + target);
                });
    }

    public void move(float dx, float dy) {
        if (physicalMonitorInputActive()) return;
        showTouchpadPointer();
        cx = Geometry.clamp(cx + dx, 0, width() - 1);
        cy = Geometry.clamp(cy + dy, 0, height() - 1);
        if (monitor != null && monitor.cursor != null) monitor.cursor.invalidate();
    }

    public void center() {
        if (split != null) {
            SplitLayout.Box box =
                    split.state().pane(split.selected(), width(), height(), split.gap());
            cx = box.x + box.width / 2f;
            cy = box.y + box.height / 2f;
            move(0, 0);
            return;
        }
        cx = width() / 2f;
        cy = height() / 2f;
        move(0, 0);
    }

    public void cancelPhoneInput() {
        if (split != null) split.cancelPhone();
        if (monitor != null && monitor.shell != null) monitor.shell.cancelPhone();
        if (id >= 0 && contactsX != null) {
            int target = id;
            float[] x = contactsX, y = contactsY;
            contactsX = null;
            contactsY = null;
            queue(s -> s.touch(target, MotionEvent.ACTION_CANCEL, x, y));
        }
    }

    public void cancelInput() {
        if (split != null) split.cancelInput();
        cancelPhoneInput();
        cancelMonitorInput();
        if (monitor != null && monitor.shell != null) monitor.shell.cancelAll();
    }

    void cancelMonitorGesture() {
        cancelMonitorInput();
    }

    public boolean monitorNavigationEnabled() {
        return prefs.getBoolean("monitorNavigation", true);
    }

    public void setMonitorNavigationEnabled(boolean enabled) {
        cancelInput();
        prefs.edit().putBoolean("monitorNavigation", enabled).apply();
        report(enabled ? "Monitor edge navigation enabled" : "Monitor edge navigation disabled");
    }

    private void relay(MotionEvent event) {
        int target = id, token = generation;
        IDeskService service = bridge;
        if ((event.getActionMasked() == MotionEvent.ACTION_MOVE && worker.getQueue().size() > 32)
                || target < 0) {
            event.recycle();
            return;
        }
        worker.execute(
                () -> {
                    try {
                        if (token == generation && service != null)
                            service.relayTouch(target, event);
                    } catch (Throwable e) {
                        report("Monitor touch failed: " + e.getMessage());
                    } finally {
                        event.recycle();
                    }
                });
    }

    private void cancelMonitorInput() {
        if (monitor != null) monitor.discardPendingTouch();
        if (split != null) split.cancelMonitorGesture();
        if (monitorContacts != null) {
            MotionEvent cancel = MotionEvent.obtainNoHistory(monitorContacts);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            relay(cancel);
            monitorContacts.recycle();
            monitorContacts = null;
        }
        monitorBlocked = true;
    }

    public void setMonitorTouchEnabled(boolean enabled) {
        cancelMonitorInput();
        prefs.edit().putBoolean("monitorTouch", enabled).apply();
        report(enabled ? "Monitor touch enabled" : "Monitor touch disabled");
    }

    public boolean monitorTouchEnabled() {
        return prefs.getBoolean("monitorTouch", true);
    }

    /** Only the physical monitor window calls this. Injected events target the virtual display. */
    boolean monitorTouch(MotionEvent original, float physicalW, float physicalH) {
        if (split != null) return split.physical(original, physicalW, physicalH);
        if (!original.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) || external == null)
            return false;
        int action = original.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pointerVisible = false;
            if (monitor != null && monitor.cursor != null) monitor.cursor.invalidate();
        }
        if (action == MotionEvent.ACTION_DOWN)
            android.util.Log.i(
                    "DexDroidTouch",
                    "physical DOWN target="
                            + id
                            + " busy="
                            + busy
                            + " enabled="
                            + monitorTouchEnabled()
                            + " size="
                            + physicalW
                            + "x"
                            + physicalH);
        if (action == MotionEvent.ACTION_DOWN) {
            cancelMonitorInput();
            monitorBlocked = false;
        }
        if (!monitorTouchEnabled() || id < 0 || busy) {
            if (action == MotionEvent.ACTION_DOWN)
                log(
                        "Monitor touch ignored: enabled="
                                + monitorTouchEnabled()
                                + " display="
                                + id
                                + " busy="
                                + busy);
            cancelMonitorInput();
            return true;
        }
        if (monitorBlocked) return true;
        if (action != MotionEvent.ACTION_DOWN && monitorContacts == null) return true;
        for (int i = 0; i < original.getPointerCount(); i++)
            if (Geometry.unmap(
                            original.getX(i),
                            original.getY(i),
                            rotation,
                            physicalW,
                            physicalH,
                            width(),
                            height())
                    == null) {
                log("Monitor touch canceled: contact outside desktop Fit area");
                cancelMonitorInput();
                return true;
            }
        if (action == MotionEvent.ACTION_DOWN) cancelPhoneInput();
        float[] src = {0, 0, width(), 0, width(), height(), 0, height()}, dst = new float[8];
        float[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int i = 0; i < 4; i++) {
            float[] p =
                    Geometry.map(
                            corners[i][0],
                            corners[i][1],
                            rotation,
                            physicalW,
                            physicalH,
                            width(),
                            height());
            dst[i * 2] = p[0];
            dst[i * 2 + 1] = p[1];
        }
        Matrix forward = new Matrix(), inverse = new Matrix();
        forward.setPolyToPoly(src, 0, dst, 0, 4);
        if (!forward.invert(inverse)) {
            cancelMonitorInput();
            return true;
        }
        MotionEvent mapped = MotionEvent.obtain(original);
        mapped.transform(inverse);
        mapped = boundMonitorContacts(mapped);
        cx = Geometry.clamp(mapped.getX(), 0, width() - 1);
        cy = Geometry.clamp(mapped.getY(), 0, height() - 1);
        if (monitor != null && monitor.cursor != null) monitor.cursor.invalidate();
        if (monitorContacts != null) monitorContacts.recycle();
        monitorContacts = remainingMonitorContacts(mapped);
        if (action != MotionEvent.ACTION_MOVE)
            log(
                    "Monitor touch: "
                            + MotionEvent.actionToString(original.getAction())
                            + " pointers="
                            + original.getPointerCount());
        relay(mapped);
        return true;
    }

    /** An exact rotated screen edge can map to width/height; use the last valid pixel. */
    private MotionEvent boundMonitorContacts(MotionEvent event) {
        boolean needsClamp = false;
        for (int i = 0; i < event.getPointerCount(); i++)
            if (event.getX(i) < 0
                    || event.getY(i) < 0
                    || event.getX(i) > width() - 1
                    || event.getY(i) > height() - 1) needsClamp = true;
        if (!needsClamp) return event;
        int count = event.getPointerCount();
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            props[i] = new MotionEvent.PointerProperties();
            coords[i] = new MotionEvent.PointerCoords();
            event.getPointerProperties(i, props[i]);
            event.getPointerCoords(i, coords[i]);
            coords[i].x = Geometry.clamp(coords[i].x, 0, width() - 1);
            coords[i].y = Geometry.clamp(coords[i].y, 0, height() - 1);
        }
        MotionEvent bounded =
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
                        event.getFlags());
        event.recycle();
        return bounded;
    }

    private MotionEvent remainingMonitorContacts(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) return null;
        if (action != MotionEvent.ACTION_POINTER_UP) return MotionEvent.obtainNoHistory(event);
        int count = event.getPointerCount() - 1;
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        for (int i = 0, out = 0; i < event.getPointerCount(); i++) {
            if (i == event.getActionIndex()) continue;
            props[out] = new MotionEvent.PointerProperties();
            coords[out] = new MotionEvent.PointerCoords();
            event.getPointerProperties(i, props[out]);
            event.getPointerCoords(i, coords[out]);
            out++;
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
                event.getFlags());
    }

    public String externalTouchDevices() {
        StringBuilder names = new StringBuilder();
        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device != null
                    && device.isExternal()
                    && device.supportsSource(InputDevice.SOURCE_TOUCHSCREEN)) {
                if (names.length() > 0) names.append(", ");
                names.append(device.getName());
            }
        }
        return names.length() == 0 ? "No external touchscreen detected" : names.toString();
    }

    View monitorInputView() {
        Window window = monitorWindow();
        return window == null ? null : window.getDecorView();
    }

    View monitorShellView() {
        return monitor == null || monitor.shell == null ? null : monitor.shell.root;
    }

    String monitorShellPage() {
        return monitor == null || monitor.shell == null ? "stopped" : monitor.shell.page();
    }

    boolean monitorShellAnimating() {
        return monitor != null && monitor.shell != null && monitor.shell.animating();
    }

    Window monitorWindow() {
        return monitor == null
                ? null
                : monitor.host != null ? monitor.host.getWindow() : monitor.getWindow();
    }

    boolean sessionAwake() {
        return sessionWakeLock != null && sessionWakeLock.isHeld();
    }

    public boolean backgroundProtectionEnabled() {
        return ((PowerManager) getSystemService(POWER_SERVICE))
                .isIgnoringBatteryOptimizations(getPackageName());
    }

    public void rotate(int selected, boolean saveDefault) {
        if (selected < 0 || selected > 3 || busy || awaitingConfirmation()) return;
        MonitorProfiles.Configuration current =
                id < 0 && external != null
                        ? recommendedMonitorConfiguration()
                        : new MonitorProfiles.Configuration(baseW, baseH, dpi, rotation);
        configure(
                current.width,
                current.height,
                current.dpi,
                selected,
                saveDefault
                        ? () -> {
                            defaultRotation = selected;
                            prefs.edit().putInt("rotation", selected).apply();
                            if (monitorIdentity() != null)
                                monitorProfiles()
                                        .remember(
                                                monitorIdentity(),
                                                new MonitorProfiles.Configuration(
                                                        baseW, baseH, dpi, selected));
                            notifyUi();
                        }
                        : null);
    }

    private void configure(int bw, int bh, int density, int selected, Runnable after) {
        cancelAppSelection();
        if (id < 0) {
            baseW = bw;
            baseH = bh;
            dpi = density;
            rotation = selected;
            if (after != null) after.run();
            notifyUi();
            return;
        }
        cancelInput();
        busy = true;
        float u = cx / width(), v = cy / height();
        int oldW = baseW, oldH = baseH, oldDpi = dpi, oldRotation = rotation;
        int newW = selected % 2 == 0 ? bw : bh, newH = selected % 2 == 0 ? bh : bw;
        int token = generation;
        IDeskService target = bridge;
        SplitWorkspace splitting = split;
        SplitLayout.Box newPane =
                splitting == null
                        ? new SplitLayout.Box(0, 0, newW, newH)
                        : splitting.dimensions(0, newW, newH, false, density);
        int oldDesktopW = oldRotation % 2 == 0 ? oldW : oldH,
                oldDesktopH = oldRotation % 2 == 0 ? oldH : oldW;
        SplitLayout.Box oldPane =
                splitting == null
                        ? new SplitLayout.Box(0, 0, oldDesktopW, oldDesktopH)
                        : splitting.dimensions(0, oldDesktopW, oldDesktopH, true, oldDpi);
        Runnable startResize =
                () -> {
                    if (token != generation) return;
                    Surface resizedSurface = monitor.outputSurface(newPane.width, newPane.height);
                    worker.execute(
                            () -> {
                                try {
                                    if (splitting != null) splitting.fence();
                                    target.resize(
                                            resizedSurface, newPane.width, newPane.height, density);
                                    if (splitting != null)
                                        splitting.resizeSecond(newW, newH, density, false);
                                    main.post(
                                            () -> {
                                                if (token != generation) return;
                                                monitor.stagedWidth = monitor.stagedHeight = 0;
                                                baseW = bw;
                                                baseH = bh;
                                                dpi = density;
                                                rotation = selected;
                                                cx = u * newW;
                                                cy = v * newH;
                                                monitor.texture
                                                        .getSurfaceTexture()
                                                        .setDefaultBufferSize(
                                                                newPane.width, newPane.height);
                                                if (splitting != null) splitting.configured(true);
                                                monitor.transform();
                                                main.postDelayed(
                                                        () -> {
                                                            if (token != generation) return;
                                                            busy = false;
                                                            notifyUi();
                                                            if (after != null) after.run();
                                                            launchPending();
                                                        },
                                                        250);
                                            });
                                } catch (Throwable e) {
                                    boolean restored;
                                    try {
                                        if (splitting != null) {
                                            CountDownLatch laidOut = new CountDownLatch(1);
                                            boolean[] prepared = {false};
                                            main.post(
                                                    () -> {
                                                        if (token != generation) {
                                                            laidOut.countDown();
                                                            return;
                                                        }
                                                        splitting.afterSurfaceLayout(
                                                                oldDesktopW,
                                                                oldDesktopH,
                                                                oldDpi,
                                                                true,
                                                                () -> {
                                                                    prepared[0] = true;
                                                                    laidOut.countDown();
                                                                },
                                                                laidOut::countDown);
                                                    });
                                            if (!laidOut.await(3, TimeUnit.SECONDS) || !prepared[0])
                                                throw new IllegalStateException(
                                                        "Rollback surfaces unavailable");
                                            // Rollback has already staged the old native texture
                                            // geometry.
                                            Surface rollbackSurface =
                                                    new Surface(
                                                            monitor.texture.getSurfaceTexture());
                                            try {
                                                target.resize(
                                                        rollbackSurface,
                                                        oldPane.width,
                                                        oldPane.height,
                                                        oldDpi);
                                            } finally {
                                                rollbackSurface.release();
                                            }
                                        } else {
                                            CountDownLatch replaced = new CountDownLatch(1);
                                            boolean[] prepared = {false};
                                            main.post(
                                                    () -> {
                                                        if (token != generation) {
                                                            replaced.countDown();
                                                            return;
                                                        }
                                                        monitor.afterOutputLayout(
                                                                oldPane.width,
                                                                oldPane.height,
                                                                () -> {
                                                                    prepared[0] = true;
                                                                    replaced.countDown();
                                                                },
                                                                replaced::countDown);
                                                    });
                                            if (!replaced.await(3, TimeUnit.SECONDS)
                                                    || !prepared[0])
                                                throw new IllegalStateException(
                                                        "Rollback surface unavailable");
                                            Surface rollbackSurface =
                                                    new Surface(
                                                            monitor.texture.getSurfaceTexture());
                                            try {
                                                target.resize(
                                                        rollbackSurface,
                                                        oldPane.width,
                                                        oldPane.height,
                                                        oldDpi);
                                            } finally {
                                                rollbackSurface.release();
                                            }
                                        }

                                        if (splitting != null)
                                            splitting.resizeSecond(
                                                    oldDesktopW, oldDesktopH, oldDpi, true);
                                        restored = true;
                                    } catch (Throwable failedRollback) {
                                        restored = false;
                                    }
                                    boolean rollbackRestored = restored;
                                    main.post(
                                            () -> {
                                                if (token != generation) return;
                                                if (!rollbackRestored || revertingProfile) {
                                                    stopDesktop();
                                                    report(
                                                            "Display update or rollback failed; desktop stopped. Saved monitor association unchanged.");
                                                    return;
                                                }
                                                busy = false;
                                                monitor.stagedWidth = monitor.stagedHeight = 0;
                                                if (splitting != null) splitting.configured(false);
                                                if (monitor != null
                                                        && monitor.texture.getSurfaceTexture()
                                                                != null) {
                                                    monitor.texture
                                                            .getSurfaceTexture()
                                                            .setDefaultBufferSize(
                                                                    oldPane.width, oldPane.height);
                                                    monitor.transform();
                                                }
                                                report(
                                                        "Resize failed; retained previous settings: "
                                                                + e.getMessage());
                                                if (pendingProfile != null) {
                                                    defaultRotation = pendingProfile[4];
                                                    pendingProfile = null;
                                                    pendingNamedProfile = null;
                                                    rollbackRequested = false;
                                                    main.removeCallbacks(rollback);
                                                }
                                                notifyUi();
                                                launchPending();
                                            });
                                } finally {
                                    resizedSurface.release();
                                }
                            });
                };
        if (splitting != null)
            splitting.afterSurfaceLayout(
                    newW,
                    newH,
                    density,
                    false,
                    startResize,
                    () -> {
                        if (token == generation) {
                            stopDesktop();
                            report("Split surface layout unavailable. Restart desktop.");
                        }
                    });
        else
            monitor.afterOutputLayout(
                    newPane.width,
                    newPane.height,
                    startResize,
                    () -> {
                        if (token == generation) {
                            stopDesktop();
                            report("Output layout unavailable. Restart desktop.");
                        }
                    });
    }

    private void launchPending() {
        if (pendingApp != null && id >= 0 && !busy && !awaitingConfirmation()) {
            ComponentName app = pendingApp;
            Integer orientation = pendingOrientation;
            pendingApp = null;
            pendingOrientation = null;
            launch(app, orientation);
        }
    }

    public void launch(ComponentName component, Integer oneTime) {
        if (split != null) {
            split.launch(component, oneTime);
            return;
        }
        if (id < 0 || busy || awaitingConfirmation()) {
            pendingApp = component;
            pendingOrientation = oneTime;
            log("App launch queued: " + component.flattenToShortString());
            ensureDesktop();
            return;
        }
        activeComponent = component;
        activePackage = component.getPackageName();
        String packageName = activePackage;
        int selected =
                oneTime != null ? oneTime : prefs.getInt("app." + activePackage, defaultRotation);
        notifyUi();
        Runnable start =
                () -> {
                    int target = id;
                    queue(
                            s -> {
                                String result = s.launch(target, component.flattenToString());
                                main.post(
                                        () -> {
                                            if (id == target) {
                                                desktopApp = true;
                                                notifyUi();
                                            }
                                        });
                                report(
                                        "Opened "
                                                + packageName
                                                + " • "
                                                + width()
                                                + " × "
                                                + height()
                                                + " • "
                                                + dpi
                                                + " UI DPI\n"
                                                + result.trim());
                            });
                };
        if (selected != rotation) configure(baseW, baseH, dpi, selected, start);
        else start.run();
    }

    public void rememberApp(boolean remember) {
        if (split != null && remember) {
            report("Split view uses workspace rotation. Saved single-view app rotation retained.");
            return;
        }
        if (activePackage.isEmpty()) {
            report("Launch an app first");
            return;
        }
        SharedPreferences.Editor edit = prefs.edit();
        if (remember) edit.putInt("app." + activePackage, rotation);
        else edit.remove("app." + activePackage);
        edit.apply();
        report(
                remember
                        ? "Orientation saved for " + activePackage
                        : "App now uses the session orientation");
    }

    public boolean awaitingConfirmation() {
        return pendingProfile != null;
    }

    public void preview(int bw, int bh, int density) {
        if (busy || id < 0 || pendingProfile != null) {
            report("Start desktop and finish any pending change first");
            return;
        }
        try {
            beginPreview(new MonitorProfiles.Configuration(bw, bh, density, rotation), null);
        } catch (IllegalArgumentException invalid) {
            report(invalid.getMessage());
        }
    }

    private void beginPreview(
            MonitorProfiles.Configuration configuration, MonitorProfiles.Profile named) {
        pendingProfile = new int[] {baseW, baseH, dpi, rotation, defaultRotation};
        profileKey = monitorKey();
        pendingMonitorIdentity = monitorIdentity();
        pendingMonitorDisplay = external.getDisplayId();
        pendingNamedProfile = named;
        rollbackRequested = false;
        configure(
                configuration.width,
                configuration.height,
                configuration.dpi,
                configuration.rotation,
                () -> {
                    if (pendingProfile == null) return;
                    if (rollbackRequested) {
                        confirm(false);
                        return;
                    }
                    report(
                            (named == null
                                            ? "Keep this display setup?"
                                            : "Keep profile " + named.name + "?")
                                    + " Automatically reverts in 15 seconds.",
                            "Display preview awaiting confirmation");
                    main.postDelayed(rollback, 15000);
                    notifyUi();
                });
    }

    public void confirm(boolean keep) {
        if (pendingProfile == null) return;
        if (busy) {
            if (!keep) {
                rollbackRequested = true;
                main.removeCallbacks(rollback);
            }
            return;
        }
        if (keep
                && (external == null
                        || external.getDisplayId() != pendingMonitorDisplay
                        || !Objects.equals(monitorIdentity(), pendingMonitorIdentity)))
            keep = false;
        main.removeCallbacks(rollback);
        int[] previous = pendingProfile;
        MonitorProfiles.Profile named = pendingNamedProfile;
        if (keep && named != null && monitorProfiles().find(named.id) == null) keep = false;
        pendingProfile = null;
        pendingNamedProfile = null;
        rollbackRequested = false;
        if (keep) {
            if (named != null) {
                monitorProfiles().bind(pendingMonitorIdentity, named.id);
                monitorProfiles().importLegacy(pendingMonitorIdentity, profileKey, null);
                defaultRotation = named.configuration.rotation;
            } else {
                monitorProfiles()
                        .remember(
                                pendingMonitorIdentity,
                                new MonitorProfiles.Configuration(
                                        baseW, baseH, dpi, defaultRotation));
                monitorProfiles().importLegacy(pendingMonitorIdentity, profileKey, null);
            }
            prefs.edit()
                    .putInt(profileKey + ".w", baseW)
                    .putInt(profileKey + ".h", baseH)
                    .putInt(profileKey + ".dpi", dpi)
                    .apply();
            report(
                    named == null
                            ? "Monitor display settings saved"
                            : "Monitor profile applied: " + named.name,
                    named == null
                            ? "Monitor display settings saved"
                            : "Named monitor profile applied");
            launchPending();
        } else {
            defaultRotation = previous[4];
            revertingProfile = true;
            configure(
                    previous[0],
                    previous[1],
                    previous[2],
                    previous[3],
                    () -> {
                        revertingProfile = false;
                        report("Previous desktop settings restored");
                    });
        }
        notifyUi();
    }

    public void resetMonitor() {
        if (external == null || !profileEditable(false)) return;
        String key = monitorKey();
        monitorProfiles().forget(monitorIdentity());
        monitorProfiles().importLegacy(monitorIdentity(), key, null);
        prefs.edit().remove(key + ".w").remove(key + ".h").remove(key + ".dpi").apply();
        report("Monitor association forgotten. Named profiles and current display unchanged.");
        notifyUi();
    }

    private static final class Cleanup {
        volatile boolean succeeded = true;
    }

    public void stopDesktop() {
        stopDesktopWithResult();
    }

    private Cleanup stopDesktopWithResult() {
        Cleanup cleanup = new Cleanup();
        boolean hadSplit = split != null;
        cancelAppSelection();
        cancelMonitorStartupDeadline();
        desktopApp = false;
        protectedContent = false;
        cancelInput();
        if (split != null)
            split.detach(
                    false,
                    success -> {
                        if (!success) cleanup.succeeded = false;
                    });
        generation++;
        int stoppedGeneration = generation;
        id = -1;
        busy = false;
        main.removeCallbacks(rollback);
        if (pendingProfile != null) {
            baseW = pendingProfile[0];
            baseH = pendingProfile[1];
            dpi = pendingProfile[2];
            rotation = pendingProfile[3];
            defaultRotation = pendingProfile[4];
        }
        pendingProfile = null;
        pendingNamedProfile = null;
        rollbackRequested = false;
        revertingProfile = false;
        Monitor old = monitor;
        if (old != null) {
            monitor = null;
            if (old.shell != null) old.shell.destroy();
            old.discardPendingTouch();
            if (old.cancelOutputLayout != null) old.cancelOutputLayout.run();
            old.dismiss();
            if (old.host != null) old.host.finish();
        }
        report("Desktop stopped");
        IDeskService target = bridge;
        if (target != null || old != null || hadSplit)
            worker.execute(
                    () -> {
                        try {
                            if (target != null) target.stop();
                        } catch (Throwable error) {
                            cleanup.succeeded = false;
                            log("Desktop cleanup failed: " + error.getMessage());
                        } finally {
                            if (old != null && old.retainedSurface != null)
                                old.retainedSurface.release();
                            if (!cleanup.succeeded)
                                main.post(
                                        () -> {
                                            if (generation != stoppedGeneration
                                                    || autoStartRequested) return;
                                            status =
                                                    "Desktop stopped. Cleanup unconfirmed. Reopen DexDroid on the phone.";
                                            notifyUi();
                                        });
                        }
                    });
        if (sessionWakeLock != null && sessionWakeLock.isHeld()) sessionWakeLock.release();
        stopForeground(true);
        stopSelf();
        return cleanup;
    }

    @Override
    public void onDisplayAdded(int added) {
        detect();
        if (external != null && external.getDisplayId() == added) {
            log("Monitor connected: " + external.getName());
            if (autoStartRequested) ensureDesktop();
        }
    }

    @Override
    public void onDisplayRemoved(int removed) {
        if (split != null && split.ownsDisplay(removed)) {
            stopDesktop();
            report("Split display removed. Reopen DexDroid.");
        }
        if (external != null && external.getDisplayId() == removed) {
            stopDesktop();
            report("Monitor disconnected. Waiting for reconnection.");
        }
        detect();
    }

    @Override
    public void onDisplayChanged(int changed) {
        if (external != null && external.getDisplayId() == changed) {
            String previousIdentity = monitorIdentity();
            detect();
            if (monitor != null && !Objects.equals(previousIdentity, monitorIdentity())) {
                stopDesktop();
                if (autoStartRequested) startDesktop();
                return;
            }
        }
        if (monitor != null && monitor.getDisplay().getDisplayId() == changed) monitor.transform();
    }

    @Override
    public void onDestroy() {
        autoStartRequested = false;
        appCatalog.close();
        stopDesktop();
        displays.unregisterDisplayListener(this);
        Shizuku.removeRequestPermissionResultListener(permission);
        Shizuku.removeBinderDeadListener(death);
        Shizuku.removeBinderReceivedListener(received);
        if (bound)
            try {
                Shizuku.unbindUserService(args, connection, true);
            } catch (Throwable ignored) {
            }
        worker.shutdown();
        super.onDestroy();
    }

    boolean attachMonitor(MonitorActivity host, int token) {
        if (monitor == null
                || token != generation
                || !monitor.token.equals(host.getIntent().getStringExtra("monitorToken"))
                || external == null
                || host.getDisplay().getDisplayId() != external.getDisplayId()
                || (monitor.host != null && monitor.host != host)) return false;
        monitor.host = host;
        cancelMonitorStartupDeadline();
        host.getWindow()
                .addFlags(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
                                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        host.immersive();
        FrameLayout content = monitor.content();
        host.setContentView(content);
        return true;
    }

    void detachMonitor(MonitorActivity host) {
        if (monitor != null && monitor.host == host) stopDesktop();
    }

    boolean ownsMonitorHost(MonitorActivity host) {
        return monitor != null && monitor.host == host;
    }

    boolean monitorHostTouch(MonitorActivity host, MotionEvent event) {
        return ownsMonitorHost(host) && monitor.handleTouch(event);
    }

    private class Monitor extends Presentation implements TextureView.SurfaceTextureListener {
        final String token = UUID.randomUUID().toString();
        final DisplayMetrics initialOutput = new DisplayMetrics();
        final int outputRotation;
        MonitorActivity host;
        TextureView texture;
        SurfaceTexture retainedSurface;
        int stagedWidth, stagedHeight;
        Runnable cancelOutputLayout;
        View cursor;
        FrameLayout frame, desktopLayer;
        MonitorShell shell;

        Monitor(Display display) {
            super(SessionService.this, display, R.style.DexDroidTheme);
            display.getRealMetrics(initialOutput);
            outputRotation = display.getRotation();
        }

        @Override
        public void cancel() {
            // Android 11 Presentation compares resource/app-inset metrics and can
            // cancel after a phone-only rotation with forced monitor density.
            // Our pixel layout handles unchanged output; explicit Stop uses dismiss().
            if (Build.VERSION.SDK_INT == 30
                    && monitor == this
                    && frame != null
                    && getDisplay().isValid()) {
                DisplayMetrics current = new DisplayMetrics();
                getDisplay().getRealMetrics(current);
                if (current.widthPixels == initialOutput.widthPixels
                        && current.heightPixels == initialOutput.heightPixels
                        && current.densityDpi == initialOutput.densityDpi
                        && getDisplay().getRotation() == outputRotation) {
                    log(
                            "Monitor retained: unchanged physical output after configuration notification");
                    transform();
                    return;
                }
            }
            super.cancel();
        }

        @Override
        public void onBackPressed() {
            SessionService.this.key(KeyEvent.KEYCODE_BACK);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled())
                    SessionService.this.key(KeyEvent.KEYCODE_BACK);
                return true;
            }
            return hardwareKey(event) || super.dispatchKeyEvent(event);
        }

        @Override
        public boolean dispatchGenericMotionEvent(MotionEvent event) {
            return (frame != null && hardwarePointer(event, frame.getWidth(), frame.getHeight()))
                    || super.dispatchGenericMotionEvent(event);
        }

        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            getWindow()
                    .addFlags(
                            WindowManager.LayoutParams.FLAG_FULLSCREEN
                                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().getDecorView().setSystemUiVisibility(5894);
            setContentView(content());
        }

        FrameLayout content() {
            if (frame != null) return frame;
            frame = new FrameLayout(getContext());
            frame.setClipChildren(false);
            frame.setBackgroundColor(Color.BLACK);
            texture = new TextureView(getContext());
            texture.setSurfaceTextureListener(this);
            desktopLayer = new FrameLayout(getContext());
            desktopLayer.setBackgroundColor(Color.BLACK);
            desktopLayer.addView(texture, new FrameLayout.LayoutParams(-1, -1));
            frame.addView(desktopLayer, new FrameLayout.LayoutParams(-1, -1));
            shell = new MonitorShell(SessionService.this, getContext());
            frame.addView(shell.root, new FrameLayout.LayoutParams(width(), height()));
            frame.addOnLayoutChangeListener(
                    (v, l, t, r, b, ol, ot, or, ob) -> {
                        transform();
                        Window window = host != null ? host.getWindow() : getWindow();
                        int edge =
                                Math.round(
                                        24
                                                * getContext()
                                                        .getResources()
                                                        .getDisplayMetrics()
                                                        .density);
                        window.setSystemGestureExclusionRects(
                                java.util.Arrays.asList(
                                        new Rect(0, 0, edge, v.getHeight()),
                                        new Rect(
                                                Math.max(0, v.getWidth() - edge),
                                                0,
                                                v.getWidth(),
                                                v.getHeight())));
                    });
            cursor =
                    new View(getContext()) {
                        @Override
                        protected void onDraw(Canvas canvas) {
                            if (!pointerVisible) return;
                            float[] point =
                                    Geometry.map(
                                            cx / width(),
                                            cy / height(),
                                            rotation,
                                            getWidth(),
                                            getHeight(),
                                            width(),
                                            height());
                            Paint p = new Paint(3);
                            p.setColor(Color.BLACK);
                            canvas.drawCircle(point[0], point[1], 13, p);
                            p.setColor(Color.rgb(94, 234, 212));
                            canvas.drawCircle(point[0], point[1], 9, p);
                        }
                    };
            frame.addView(cursor, new FrameLayout.LayoutParams(-1, -1));
            return frame;
        }

        private final ArrayDeque<MotionEvent> pendingTouch = new ArrayDeque<>();
        private boolean waitingForFocus;
        private final Runnable focusTimeout = () -> releasePendingTouch(false);
        private final Runnable focusSettled = () -> releasePendingTouch(true);

        private void discardPendingTouch() {
            waitingForFocus = false;
            main.removeCallbacks(focusTimeout);
            main.removeCallbacks(focusSettled);
            while (!pendingTouch.isEmpty()) pendingTouch.removeFirst().recycle();
        }

        private void holdTouch(MotionEvent event) {
            MotionEvent last = pendingTouch.peekLast();
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                    && last != null
                    && last.getActionMasked() == MotionEvent.ACTION_MOVE)
                pendingTouch.removeLast().recycle();
            if (pendingTouch.size() >= 16) {
                discardPendingTouch();
                monitorBlocked = true;
                log("Monitor touch canceled: host focus buffer full");
                return;
            }
            pendingTouch.addLast(MotionEvent.obtain(event));
        }

        @Override
        public void onWindowFocusChanged(boolean focused) {
            super.onWindowFocusChanged(focused);
            if (focused) {
                if (split != null && waitingForFocus) main.postDelayed(focusSettled, 16);
                else releasePendingTouch(true);
            }
        }

        private void releasePendingTouch(boolean focused) {
            if (!waitingForFocus) return;
            waitingForFocus = false;
            main.removeCallbacks(focusTimeout);
            main.removeCallbacks(focusSettled);
            if (monitor != this || texture == null || busy || id < 0) {
                discardPendingTouch();
                monitorBlocked = true;
                return;
            }
            MotionEvent first = pendingTouch.peekFirst();
            long delay = first == null ? 0 : SystemClock.uptimeMillis() - first.getEventTime();
            log(
                    "Legacy monitor contact relay: "
                            + (focused ? "host focus" : "settled fallback")
                            + " after "
                            + delay
                            + " ms");
            // Shared cancellation during DOWN initialization must not recycle the
            // rest of this already-released batch. All dispatch stays on main.
            ArrayDeque<MotionEvent> released = new ArrayDeque<>(pendingTouch);
            pendingTouch.clear();
            while (!released.isEmpty()) {
                MotionEvent event = released.removeFirst();
                try {
                    monitorTouch(event, frame.getWidth(), frame.getHeight());
                } finally {
                    event.recycle();
                }
            }
        }

        boolean handleTouch(MotionEvent event) {
            if (texture != null) {
                if (shell.physical(event, frame.getWidth(), frame.getHeight())) {
                    discardPendingTouch();
                    return true;
                }
                if (Build.VERSION.SDK_INT == 30
                        && (event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)
                                || (split != null
                                        && event.isFromSource(InputDevice.SOURCE_MOUSE)))) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                            && (split != null || !getWindow().getDecorView().hasWindowFocus())) {
                        discardPendingTouch();
                        cancelPhoneInput();
                        waitingForFocus = true;
                        if (split != null && getWindow().getDecorView().hasWindowFocus())
                            main.postDelayed(focusSettled, 16);
                        else main.postDelayed(focusTimeout, 100);
                    }
                    if (waitingForFocus) {
                        holdTouch(event);
                        return true;
                    }
                }
                if (monitorTouch(event, frame.getWidth(), frame.getHeight())) return true;
            }
            return false;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            return handleTouch(event) || super.dispatchTouchEvent(event);
        }

        void transform() {
            if (texture == null) return;
            int w = frame.getWidth(), h = frame.getHeight();
            if (w == 0 || h == 0) return;
            int dw = stagedWidth > 0 ? stagedWidth : width();
            int dh = stagedHeight > 0 ? stagedHeight : height();
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) desktopLayer.getLayoutParams();
            if (lp.width != dw || lp.height != dh)
                desktopLayer.setLayoutParams(new FrameLayout.LayoutParams(dw, dh));
            float scale =
                    Math.min(
                            (float) w / (rotation % 2 == 0 ? dw : dh),
                            (float) h / (rotation % 2 == 0 ? dh : dw));
            desktopLayer.setPivotX(dw / 2f);
            desktopLayer.setPivotY(dh / 2f);
            desktopLayer.setScaleX(scale);
            desktopLayer.setScaleY(scale);
            desktopLayer.setRotation(rotation * 90f);
            desktopLayer.setX((w - dw) / 2f);
            desktopLayer.setY((h - dh) / 2f);
            texture.setTransform(new Matrix());
            if (split != null) split.layoutViews();
            else {
                FrameLayout.LayoutParams size =
                        (FrameLayout.LayoutParams) texture.getLayoutParams();
                if (size.width != dw
                        || size.height != dh
                        || size.leftMargin != 0
                        || size.topMargin != 0)
                    texture.setLayoutParams(new FrameLayout.LayoutParams(dw, dh));
            }
            shell.layout(w, h);
            cursor.invalidate();
        }

        void afterOutputLayout(int width, int height, Runnable ready, Runnable failed) {
            if (cancelOutputLayout != null) cancelOutputLayout.run();
            stagedWidth = width;
            stagedHeight = height;
            transform();
            ViewTreeObserver observer = frame.getViewTreeObserver();
            boolean[] done = {false};
            Runnable[] timeout = {null};
            ViewTreeObserver.OnPreDrawListener[] listener = {null};
            Runnable cancel =
                    () -> {
                        if (done[0]) return;
                        done[0] = true;
                        if (timeout[0] != null) main.removeCallbacks(timeout[0]);
                        if (observer.isAlive()) observer.removeOnPreDrawListener(listener[0]);
                        cancelOutputLayout = null;
                    };
            cancelOutputLayout = cancel;
            timeout[0] =
                    () -> {
                        cancel.run();
                        if (monitor == this) failed.run();
                    };
            listener[0] =
                    () -> {
                        if (monitor != this) {
                            cancel.run();
                            return true;
                        }
                        if (texture.getWidth() != width || texture.getHeight() != height)
                            return true;
                        cancel.run();
                        try {
                            ready.run();
                        } catch (RuntimeException error) {
                            failed.run();
                        }
                        return true;
                    };
            observer.addOnPreDrawListener(listener[0]);
            main.postDelayed(timeout[0], 2000);
            frame.requestLayout();
            frame.invalidate();
        }

        Surface outputSurface(int width, int height) {
            texture.getSurfaceTexture().setDefaultBufferSize(width, height);
            return new Surface(texture.getSurfaceTexture());
        }

        public void onSurfaceTextureAvailable(SurfaceTexture surface, int w, int h) {
            retainedSurface = surface;
            if (monitor != this) {
                surface.release();
                return;
            }
            surface.setDefaultBufferSize(width(), height());
            transform();
            Surface target = new Surface(surface);
            int token = generation;
            IDeskService service = bridge;
            worker.execute(
                    () -> {
                        try {
                            int created =
                                    service.create(
                                            target,
                                            width(),
                                            height(),
                                            dpi,
                                            sessionOwner,
                                            new IDesktopListener.Stub() {
                                                public void onProtectionState(
                                                        int display, int state) {
                                                    main.post(
                                                            () -> {
                                                                if (token != generation
                                                                        || id != display) return;
                                                                if (split != null) {
                                                                    split.protection(0, state);
                                                                    return;
                                                                }
                                                                if (state
                                                                        == ProtectedWindows.State
                                                                                .UNKNOWN
                                                                                .ordinal()) {
                                                                    log(
                                                                            "Protected-window detection unavailable");
                                                                    return;
                                                                }
                                                                boolean detected =
                                                                        desktopApp
                                                                                && state
                                                                                        == ProtectedWindows
                                                                                                .State
                                                                                                .PROTECTED
                                                                                                .ordinal();
                                                                if (protectedContent == detected)
                                                                    return;
                                                                protectedContent = detected;
                                                                log(
                                                                        detected
                                                                                ? "Protected desktop window detected"
                                                                                : "Protected desktop notice cleared");
                                                                notifyUi();
                                                            });
                                                }

                                                public void onTaskState(
                                                        int display,
                                                        boolean occupied,
                                                        String component,
                                                        String error) {
                                                    main.post(
                                                            () -> {
                                                                if (token != generation
                                                                        || id != display) return;
                                                                if (split != null) {
                                                                    split.occupancy(
                                                                            0, occupied, component,
                                                                            error);
                                                                    return;
                                                                }
                                                                if (!error.isEmpty()) {
                                                                    log(
                                                                            "Desktop task state unavailable: "
                                                                                    + error);
                                                                    return;
                                                                }
                                                                ComponentName top =
                                                                        ComponentName
                                                                                .unflattenFromString(
                                                                                        component);
                                                                boolean appChanged =
                                                                        top != null
                                                                                && !top.equals(
                                                                                        activeComponent);
                                                                if (desktopApp == occupied
                                                                        && !appChanged) return;
                                                                if (appChanged) {
                                                                    activeComponent = top;
                                                                    activePackage =
                                                                            top.getPackageName();
                                                                }
                                                                desktopApp = occupied;
                                                                if (!occupied) {
                                                                    protectedContent = false;
                                                                    cancelInput();
                                                                    hideMonitorPointer();
                                                                    activePackage = "";
                                                                    activeComponent = null;
                                                                    report(
                                                                            "No app on monitor. Choose Apps.");
                                                                }
                                                                notifyUi();
                                                            });
                                                }
                                            });
                            main.post(
                                    () -> {
                                        if (token != generation) return;
                                        id = created;
                                        busy = false;
                                        report(
                                                "Desktop ready • "
                                                        + width()
                                                        + " × "
                                                        + height()
                                                        + " • "
                                                        + dpi
                                                        + " UI DPI. Choose Apps.");
                                        if (savedSplitLayout().enabled) enableSplit();
                                        else launchPending();
                                    });
                        } catch (Throwable e) {
                            main.post(
                                    () -> {
                                        if (token == generation) {
                                            stopDesktop();
                                            report("Desktop creation failed: " + e.getMessage());
                                        }
                                    });
                        } finally {
                            target.release();
                        }
                    });
        }

        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int w, int h) {
            transform();
        }

        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            if (monitor == this) {
                stopDesktop();
            }
            // stopDesktop releases this texture after the shell display is detached.
            return false;
        }

        public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    }
}
