package dev.dexdroid;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import rikka.shizuku.Shizuku;

/**
 * Service-owned second display, pane state and bounded input. Views belong to the existing host.
 */
final class SplitWorkspace {
    private final SessionService session;
    private final FrameLayout layer;
    private final TextureView primary, secondary;
    private final FrameLayout[] decoration = new FrameLayout[2];
    private final IdleDesktop[] empty = new IdleDesktop[2];
    private final View divider;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor input =
            new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64));
    private final ComponentName[] apps = new ComponentName[2];
    private final String[] packages = {"", ""};
    private final boolean[] occupied = new boolean[2],
            protectedPane = new boolean[2],
            inputPending = new boolean[2];
    private final Map<Long, HeldKey> keys = new HashMap<>();
    private final Set<Long> cancelledKeys = new LinkedHashSet<>();
    private final int primaryId;
    private final Shizuku.UserServiceArgs args;
    private final ServiceConnection connection;
    private SplitLayout layout = new SplitLayout(), previous;
    private IDeskService secondBridge;
    private volatile int secondId = -1;
    private SurfaceTexture secondTexture;
    private Surface secondSurface;
    private volatile boolean closed;
    private boolean bound, dividerHeld, dividerPhone;
    private float dividerPreview;
    private MotionEvent physicalContact, phoneContact;
    private int physicalPane = -1, phonePane = -1, uiPane = -1;
    private boolean uiPhone;
    private long phoneDown;
    private Runnable cancelSurfaceLayout;
    private SplitLayout.Box[] stagedSurfaces;
    private SplitLayout stagedLayout;
    private int stagedWidth, stagedHeight, stagedDpi;
    private int controlsDpi = -1;

    SplitWorkspace(
            SessionService service, FrameLayout layer, TextureView primary, Boolean vertical) {
        session = service;
        this.layer = layer;
        this.primary = primary;
        primary.setTag("split-texture-0");
        primaryId = service.id;
        apps[0] = service.activeComponent;
        packages[0] = service.activePackage;
        occupied[0] = service.hasDesktopApp();
        protectedPane[0] = service.protectedContent;
        layout = service.savedSplitLayout();
        previous = layout.copy();
        previous.enabled = false;
        if (vertical != null) layout.vertical = vertical;
        layout.enabled = true;
        secondary = new TextureView(layer.getContext());
        secondary.setTag("split-texture-1");
        secondary.setSurfaceTextureListener(
                new TextureView.SurfaceTextureListener() {
                    @Override
                    public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) {
                        secondTexture = texture;
                        secondSurface = new Surface(texture);
                        if (!current()) return;
                        bound = true;
                        try {
                            Shizuku.bindUserService(args, connection);
                        } catch (RuntimeException error) {
                            session.busy = false;
                            detach(true);
                            session.report("Split connection failed: " + error.getMessage());
                        }
                    }

                    @Override
                    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) {}

                    @Override
                    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                        if (!closed && current()) {
                            session.stopDesktop();
                            session.report("Split surface lost. Reopen DexDroid.");
                        }
                        return false;
                    }

                    @Override
                    public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
                });
        layer.addView(secondary, new FrameLayout.LayoutParams(1, 1));
        for (int i = 0; i < 2; i++) {
            decoration[i] = new FrameLayout(layer.getContext());
            decoration[i].setTag("split-pane-" + i);
            layer.addView(decoration[i], new FrameLayout.LayoutParams(1, 1));
        }
        divider = new View(layer.getContext());
        divider.setTag("split-divider");
        divider.setBackgroundColor(Color.rgb(45, 45, 45));
        divider.setContentDescription(
                "Resize split panes; use Layout settings for accessible presets");
        divider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        layer.addView(divider, new FrameLayout.LayoutParams(1, 1));
        args =
                new Shizuku.UserServiceArgs(new ComponentName(service, SecondaryDeskService.class))
                        .tag("split-" + UUID.randomUUID())
                        .daemon(false)
                        .processNameSuffix("desktop-secondary")
                        .debuggable(BuildConfig.DEBUG)
                        .version(BuildConfig.VERSION_CODE);
        connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        if (!current()) {
                            if (bound) {
                                bound = false;
                                Shizuku.unbindUserService(args, this, true);
                            }
                            return;
                        }
                        secondBridge = IDeskService.Stub.asInterface(binder);
                        SplitLayout.Box box =
                                dimensions(1, session.width(), session.height(), false);
                        session.desktopWork(
                                () -> {
                                    try {
                                        int created =
                                                secondBridge.create(
                                                        secondSurface,
                                                        box.width,
                                                        box.height,
                                                        session.dpi,
                                                        session.splitOwner(),
                                                        new IDesktopListener.Stub() {
                                                            @Override
                                                            public void onProtectionState(
                                                                    int display, int state) {
                                                                main.post(
                                                                        () -> {
                                                                            if (current()
                                                                                    && display
                                                                                            == secondId)
                                                                                protection(
                                                                                        1, state);
                                                                        });
                                                            }

                                                            @Override
                                                            public void onTaskState(
                                                                    int display,
                                                                    boolean value,
                                                                    String component,
                                                                    String error) {
                                                                main.post(
                                                                        () -> {
                                                                            if (current()
                                                                                    && display
                                                                                            == secondId)
                                                                                occupancy(
                                                                                        1, value,
                                                                                        component,
                                                                                        error);
                                                                        });
                                                            }
                                                        });
                                        main.post(
                                                () -> {
                                                    if (!current()) return;
                                                    secondId = created;
                                                    session.reconfigureSplit();
                                                });
                                    } catch (Exception error) {
                                        main.post(
                                                () -> {
                                                    if (current()) {
                                                        session.busy = false;
                                                        detach(true);
                                                        session.report(
                                                                "Split setup failed: "
                                                                        + error.getMessage());
                                                    }
                                                });
                                    }
                                });
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        if (current()) {
                            session.stopDesktop();
                            session.report("Split connection lost. Reopen DexDroid.");
                        }
                    }
                };
        main.postDelayed(
                () -> {
                    if (current() && secondId < 0) {
                        session.busy = false;
                        detach(true);
                        session.report("Split setup timed out. Single view restored.");
                    }
                },
                10000);
    }

    boolean current() {
        return !closed && session.split == this && session.id == primaryId;
    }

    SplitLayout state() {
        return layout.copy();
    }

    int selected() {
        return layout.selected;
    }

    int displayId(int pane) {
        return pane == 0 ? primaryId : secondId;
    }

    boolean ownsDisplay(int id) {
        return id == primaryId || (secondId >= 0 && id == secondId);
    }

    IDeskService bridge(int pane) {
        return pane == 0 ? session.primaryBridge() : secondBridge;
    }

    String selectedPackage() {
        return packages[layout.selected];
    }

    ComponentName selectedComponent() {
        return apps[layout.selected];
    }

    boolean anyApp() {
        return occupied[0] || occupied[1];
    }

    boolean anyProtected() {
        return protectedPane[0] || protectedPane[1];
    }

    SplitLayout.Box protectedBox() {
        for (int pane : new int[] {layout.selected, 1 - layout.selected}) {
            SplitLayout.Box box = layout.pane(pane, session.width(), session.height(), gap());
            if (protectedPane[pane] && box.width > 0 && box.height > 0) return box;
        }
        return null;
    }

    boolean physicalActive() {
        return physicalContact != null
                || (uiPane >= 0 && !uiPhone)
                || (dividerHeld && !dividerPhone);
    }

    int gap() {
        return Math.max(1, Math.round(2 * session.dpi / 160f));
    }

    String summary() {
        return (layout.vertical ? "Top/bottom" : "Left/right")
                + " · "
                + Math.round(layout.ratio * 100)
                + ":"
                + Math.round((1 - layout.ratio) * 100)
                + " · Pane "
                + (layout.selected + 1)
                + (layout.maximized >= 0 ? " fullscreen" : "");
    }

    SplitLayout.Box dimensions(int pane, int width, int height, boolean old) {
        return dimensions(pane, width, height, old, session.dpi);
    }

    SplitLayout.Box dimensions(int pane, int width, int height, boolean old, int dpi) {
        SplitLayout value = old && previous != null ? previous : layout;
        return value.display(pane, width, height, Math.max(1, Math.round(2 * dpi / 160f)));
    }

    private static void place(View view, SplitLayout.Box box) {
        FrameLayout.LayoutParams old = (FrameLayout.LayoutParams) view.getLayoutParams();
        if (old.width != box.width
                || old.height != box.height
                || old.leftMargin != box.x
                || old.topMargin != box.y) {
            FrameLayout.LayoutParams next =
                    new FrameLayout.LayoutParams(Math.max(1, box.width), Math.max(1, box.height));
            next.leftMargin = box.x;
            next.topMargin = box.y;
            view.setLayoutParams(next);
        }
    }

    void layoutViews() {
        if (closed) return;
        int density = stagedSurfaces == null ? session.dpi : stagedDpi;
        if (controlsDpi != density) {
            controlsDpi = density;
            android.content.res.Configuration config =
                    new android.content.res.Configuration(layer.getResources().getConfiguration());
            config.densityDpi = controlsDpi;
            Context context =
                    new ContextThemeWrapper(
                            layer.getContext().createConfigurationContext(config),
                            R.style.DexDroidTheme);
            for (int i = 0; i < 2; i++) {
                final int pane = i;
                decoration[i].removeAllViews();
                empty[i] =
                        new IdleDesktop(
                                context,
                                () -> {
                                    select(pane);
                                    session.splitApps(pane);
                                },
                                "split-idle-" + i,
                                "split-idle-apps-" + i);
                decoration[i].addView(empty[i], new FrameLayout.LayoutParams(-1, -1));
            }
        }
        int w = stagedSurfaces == null ? session.width() : stagedWidth;
        int h = stagedSurfaces == null ? session.height() : stagedHeight;
        SplitLayout visibleLayout = stagedSurfaces == null ? layout : stagedLayout;
        int visibleGap = Math.max(1, Math.round(2 * density / 160f));
        for (int i = 0; i < 2; i++) {
            SplitLayout.Box visible = visibleLayout.pane(i, w, h, visibleGap),
                    nativeBox = dimensions(i, w, h, false);
            TextureView texture = i == 0 ? primary : secondary;
            if (stagedSurfaces != null) nativeBox = stagedSurfaces[i];
            place(texture, nativeBox);
            texture.setAlpha(visible.width > 0 && occupied[i] ? 1 : 0);
            if (texture.getSurfaceTexture() != null)
                texture.getSurfaceTexture().setDefaultBufferSize(nativeBox.width, nativeBox.height);
            place(decoration[i], visible);
            // Stage decoration and canvas together with native textures, including rollback.
            place(empty[i], new SplitLayout.Box(0, 0, visible.width, visible.height));
            decoration[i].setVisibility(visible.width > 0 ? View.VISIBLE : View.GONE);
            decoration[i].setBackgroundColor(Color.TRANSPARENT);
            empty[i].setVisibility(occupied[i] ? View.GONE : View.VISIBLE);
            decoration[i].setForeground(null);
        }
        SplitLayout.Box bar = visibleLayout.divider(w, h, visibleGap);
        place(divider, bar);
        divider.setVisibility(bar.width > 0 ? View.VISIBLE : View.GONE);
        divider.setTranslationX(0);
        divider.setTranslationY(0);
    }

    void select(int pane) {
        if (!current() || pane < 0 || pane > 1 || session.busy) return;
        cancelPhone();
        cancelPhysical();
        if (layout.maximized >= 0 && layout.maximized != pane) {
            SplitLayout next = layout.copy();
            next.selected = pane;
            next.maximized = pane;
            request(next);
            return;
        }
        selectInternal(pane);
    }

    private void selectInternal(int pane) {
        if (pane < 0 || pane > 1) return;
        layout.selected = pane;
        session.splitStateChanged();
        layoutViews();
    }

    void direction(boolean vertical) {
        SplitLayout next = layout.copy();
        next.vertical = vertical;
        next.maximized = -1;
        request(next);
    }

    void ratio(float ratio) {
        SplitLayout next = layout.copy();
        next.ratio = Math.max(.25f, Math.min(.75f, ratio));
        request(next);
    }

    void swap() {
        SplitLayout next = layout.copy();
        next.swapped = !next.swapped;
        request(next);
    }

    void maximize() {
        SplitLayout next = layout.copy();
        next.maximized = layout.maximized < 0 ? layout.selected : -1;
        request(next);
    }

    void end() {
        SplitLayout next = layout.copy();
        next.enabled = false;
        next.maximized = -1;
        next.selected = 0;
        request(next);
    }

    private void request(SplitLayout next) {
        request(next, null);
    }

    private void request(SplitLayout next, Runnable after) {
        if (!current() || session.busy || session.awaitingConfirmation()) {
            session.report("Finish the current display change first");
            return;
        }
        session.cancelAppSelection();
        cancelInput();
        previous = layout.copy();
        layout = next;
        session.reconfigureSplit(after);
    }

    void fence() throws Exception {
        Future<?> barrier = input.submit(() -> {});
        barrier.get(5, TimeUnit.SECONDS);
    }

    void resizeSecond(int width, int height, int dpi, boolean old) throws Exception {
        if (secondId < 0 || secondSurface == null || (!layout.enabled && !old)) return;
        SplitLayout.Box box = dimensions(1, width, height, old, dpi);
        secondTexture.setDefaultBufferSize(box.width, box.height);
        Surface resized = new Surface(secondTexture);
        try {
            secondBridge.resize(resized, box.width, box.height, dpi);
        } finally {
            resized.release();
        }
    }

    /** TextureView changes its producer size during layout; attach only after that change. */
    void afterSurfaceLayout(
            int width, int height, int dpi, boolean old, Runnable ready, Runnable failed) {
        if (cancelSurfaceLayout != null) cancelSurfaceLayout.run();
        SplitLayout.Box first = dimensions(0, width, height, old, dpi),
                second = dimensions(1, width, height, old, dpi);
        // Task callbacks can relayout views before SessionService commits the new dimensions.
        // Keep producer and view sizes together for the entire remote resize, not just pre-draw.
        stagedSurfaces = new SplitLayout.Box[] {first, second};
        stagedWidth = width;
        stagedHeight = height;
        stagedDpi = dpi;
        stagedLayout = old && previous != null ? previous : layout;
        layoutViews();
        ViewTreeObserver observer = layer.getViewTreeObserver();
        boolean[] done = {false};
        Runnable[] timeout = {null};
        ViewTreeObserver.OnPreDrawListener[] listener = {null};
        Runnable cancel =
                () -> {
                    if (done[0]) return;
                    done[0] = true;
                    if (timeout[0] != null) main.removeCallbacks(timeout[0]);
                    if (observer.isAlive() && listener[0] != null)
                        observer.removeOnPreDrawListener(listener[0]);
                    cancelSurfaceLayout = null;
                };
        cancelSurfaceLayout = cancel;
        timeout[0] =
                () -> {
                    cancel.run();
                    if (current()) failed.run();
                };
        listener[0] =
                () -> {
                    if (!current()) {
                        cancel.run();
                        return true;
                    }
                    if (primary.getWidth() != first.width
                            || primary.getHeight() != first.height
                            || secondary.getWidth() != second.width
                            || secondary.getHeight() != second.height) return true;
                    for (int i = 0; i < 2; i++) {
                        SplitLayout.Box visible =
                                stagedLayout.pane(
                                        i, width, height, Math.max(1, Math.round(2 * dpi / 160f)));
                        if (visible.width > 0
                                && visible.height > 0
                                && (decoration[i].getWidth() != visible.width
                                        || decoration[i].getHeight() != visible.height
                                        || (empty[i].getVisibility() == View.VISIBLE
                                                && (empty[i].getWidth() != visible.width
                                                        || empty[i].getHeight()
                                                                != visible.height)))) return true;
                    }
                    cancel.run();
                    try {
                        primary.getSurfaceTexture().setDefaultBufferSize(first.width, first.height);
                        secondary
                                .getSurfaceTexture()
                                .setDefaultBufferSize(second.width, second.height);
                        ready.run();
                    } catch (RuntimeException unavailable) {
                        failed.run();
                    }
                    return true;
                };
        observer.addOnPreDrawListener(listener[0]);
        main.postDelayed(timeout[0], 2000);
        layer.requestLayout();
        layer.invalidate();
    }

    void configured(boolean success) {
        if (!current()) return;
        stagedSurfaces = null;
        stagedLayout = null;
        if (!success && previous != null) layout = previous;
        previous = null;
        if (success) session.rememberSplitLayout(layout);
        if (!layout.enabled) {
            detach(true);
            return;
        }
        session.splitStateChanged();
        layoutViews();
        if (success) session.report("Split view · " + summary());
    }

    void occupancy(int pane, boolean value, String error) {
        occupancy(pane, value, "", error);
    }

    void occupancy(int pane, boolean value, String component, String error) {
        if (!current()) return;
        if (!error.isEmpty()) {
            session.log("Pane task state unavailable");
            inputPending[pane] = true;
            session.report("Pane " + (pane + 1) + ": " + error);
            return;
        }
        if (inputPending[pane]) {
            inputPending[pane] = false;
            session.report("Pane " + (pane + 1) + (value ? " ready" : " empty"));
        }
        ComponentName top = ComponentName.unflattenFromString(component);
        boolean changed = top != null && !top.equals(apps[pane]);
        if (occupied[pane] == value && !changed) return;
        if (changed) {
            if (!top.getPackageName().equals(packages[pane])) cancelKeys();
            apps[pane] = top;
            packages[pane] = top.getPackageName();
        }
        occupied[pane] = value;
        if (!value) {
            cancelInput();
            apps[pane] = null;
            packages[pane] = "";
            protectedPane[pane] = false;
        }
        session.splitStateChanged();
        layoutViews();
    }

    void protection(int pane, int state) {
        if (!current() || state == ProtectedWindows.State.UNKNOWN.ordinal()) return;
        protectedPane[pane] = occupied[pane] && state == ProtectedWindows.State.PROTECTED.ordinal();
        session.splitStateChanged();
    }

    void launch(ComponentName component, Integer orientation) {
        launch(component, orientation, layout.selected);
    }

    void launch(ComponentName component, Integer orientation, int pane) {
        if (!current() || session.busy || secondId < 0 || session.awaitingConfirmation()) {
            session.report("Wait for split view to finish updating");
            return;
        }
        int other = 1 - pane;
        if (component.getPackageName().equals(packages[other]) && occupied[other]) {
            select(other);
            session.report("App is already in pane " + (other + 1));
            return;
        }
        if (layout.maximized >= 0 && layout.maximized != pane) {
            SplitLayout next = layout.copy();
            next.selected = next.maximized = pane;
            request(
                    next,
                    () -> {
                        if (current() && !session.busy && layout.maximized == pane)
                            launch(component, orientation, pane);
                    });
            return;
        }
        cancelInput();
        selectInternal(pane);
        session.busy = true;
        final IDeskService target = bridge(pane);
        final int id = displayId(pane);
        session.desktopWork(
                () -> {
                    try {
                        fence();
                        target.launch(id, component.flattenToString());
                        main.post(
                                () -> {
                                    if (!current()) return;
                                    apps[pane] = component;
                                    packages[pane] = component.getPackageName();
                                    occupied[pane] = true;
                                    session.busy = false;
                                    session.splitStateChanged();
                                    layoutViews();
                                    session.report(
                                            "Opened app in pane "
                                                    + (pane + 1)
                                                    + (orientation != null
                                                            ? ". Split view uses workspace orientation."
                                                            : ""));
                                });
                    } catch (Exception error) {
                        main.post(
                                () -> {
                                    if (current()) {
                                        session.busy = false;
                                        session.report("App launch failed: " + error.getMessage());
                                    }
                                });
                    }
                });
    }

    void closeApp() {
        if (!current() || session.busy || session.awaitingConfirmation()) return;
        cancelInput();
        session.busy = true;
        int pane = layout.selected, id = displayId(pane);
        IDeskService target = bridge(pane);
        session.desktopWork(
                () -> {
                    try {
                        fence();
                        target.closeApps(id);
                        main.post(
                                () -> {
                                    if (current()) {
                                        session.busy = false;
                                        occupancy(pane, false, "");
                                        session.report(
                                                "Pane " + (pane + 1) + " closed. Choose Apps.");
                                    }
                                });
                    } catch (Exception error) {
                        main.post(
                                () -> {
                                    if (current()) {
                                        session.busy = false;
                                        session.report("Close failed: " + error.getMessage());
                                    }
                                });
                    }
                });
    }

    private void submit(Runnable action, Runnable discard) {
        if (!current()) {
            discard.run();
            return;
        }
        try {
            input.execute(
                    () -> {
                        try {
                            // Accepted events drain before either owned display is stopped.
                            // In particular, queued cancellation must survive detach().
                            action.run();
                        } catch (Exception error) {
                            main.post(
                                    () -> {
                                        if (current()) {
                                            session.stopDesktop();
                                            session.report(
                                                    "Split input failed. Restart desktop: "
                                                            + error.getMessage());
                                        }
                                    });
                        } finally {
                            discard.run();
                        }
                    });
        } catch (RejectedExecutionException full) {
            discard.run();
            main.post(
                    () -> {
                        if (current()) {
                            session.stopDesktop();
                            session.report("Split input queue full. Restart desktop.");
                        }
                    });
        }
    }

    private void sendMotion(int pane, MotionEvent event) {
        final int id = displayId(pane);
        final IDeskService target = bridge(pane);
        submit(
                () -> {
                    try {
                        target.relayMotion(id, event);
                    } catch (RemoteException e) {
                        throw new IllegalStateException(e);
                    }
                },
                event::recycle);
    }

    private void sendKey(int pane, KeyEvent event) {
        final int id = displayId(pane);
        final IDeskService target = bridge(pane);
        submit(
                () -> {
                    try {
                        target.relayKey(id, event);
                    } catch (RemoteException e) {
                        throw new IllegalStateException(e);
                    }
                },
                () -> {});
    }

    private static final class HeldKey {
        final int pane;
        final KeyEvent down;

        HeldKey(int pane, KeyEvent down) {
            this.pane = pane;
            this.down = down;
        }
    }

    boolean hardwareKey(KeyEvent event) {
        if (!current()
                || session.busy
                || !event.isFromSource(InputDevice.SOURCE_KEYBOARD)
                || (event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) != 0) return false;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_VOLUME_UP
                || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_POWER
                || code == KeyEvent.KEYCODE_HOME
                || code == KeyEvent.KEYCODE_APP_SWITCH) return false;
        long key = ((long) event.getDeviceId() << 32) | (code & 0xffffffffL);
        if (event.getAction() == KeyEvent.ACTION_UP && cancelledKeys.remove(key)) return true;
        HeldKey held = keys.get(key);
        if (held == null && event.getAction() == KeyEvent.ACTION_UP) return true;
        int pane = held == null ? layout.selected : held.pane;
        if (!occupied[pane]) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            cancelledKeys.remove(key);
            if (keys.size() >= 64 && held == null) {
                session.stopDesktop();
                session.report("Too many held keyboard keys. Restart desktop.");
                return true;
            }
            keys.put(key, new HeldKey(pane, new KeyEvent(event)));
        } else if (event.getAction() == KeyEvent.ACTION_UP) keys.remove(key);
        sendKey(pane, new KeyEvent(event));
        return true;
    }

    void keyCode(int code) {
        int pane = layout.selected;
        if (!occupied[pane]) return;
        long now = SystemClock.uptimeMillis();
        for (int action : new int[] {KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP})
            sendKey(
                    pane,
                    new KeyEvent(
                            now, now, action, code, 0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD));
    }

    private Matrix inverse(float physicalW, float physicalH) {
        int w = session.width(), h = session.height();
        float[] src = {0, 0, w, 0, w, h, 0, h}, dst = new float[8];
        float[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int i = 0; i < 4; i++) {
            float[] p =
                    Geometry.map(
                            corners[i][0],
                            corners[i][1],
                            session.rotation,
                            physicalW,
                            physicalH,
                            w,
                            h);
            dst[i * 2] = p[0];
            dst[i * 2 + 1] = p[1];
        }
        Matrix forward = new Matrix(), result = new Matrix();
        forward.setPolyToPoly(src, 0, dst, 0, 4);
        if (!forward.invert(result)) throw new IllegalStateException("Invalid workspace geometry");
        return result;
    }

    boolean physical(MotionEvent event, float width, float height) {
        if (!current()) return true;
        if (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)
                && !event.isFromSource(InputDevice.SOURCE_MOUSE)
                && !event.isFromSource(InputDevice.SOURCE_TOUCHPAD)) return false;
        if (event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) && !session.monitorTouchEnabled()) {
            cancelPhysical();
            return true;
        }
        if (session.busy) {
            cancelPhysical();
            return true;
        }
        for (int i = 0; i < event.getPointerCount(); i++)
            if (Geometry.unmap(
                            event.getX(i),
                            event.getY(i),
                            session.rotation,
                            width,
                            height,
                            session.width(),
                            session.height())
                    == null) {
                cancelPhysical();
                return true;
            }
        MotionEvent mapped = MotionEvent.obtain(event);
        mapped.transform(inverse(width, height));
        route(mapped, false);
        mapped.recycle();
        return true;
    }

    void phonePointer(MotionEvent event) {
        if (!current() || session.busy || physicalActive()) return;
        route(event, true);
    }

    void phoneTouch(int action, float[] xs, float[] ys) {
        if (!current()
                || session.busy
                || physicalActive()
                || xs.length < 1
                || xs.length != ys.length) return;
        long now = SystemClock.uptimeMillis();
        if ((action & 255) == MotionEvent.ACTION_DOWN) phoneDown = now;
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[xs.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[xs.length];
        for (int i = 0; i < xs.length; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = i;
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = xs[i];
            coords[i].y = ys[i];
            coords[i].pressure = 1;
        }
        MotionEvent event =
                MotionEvent.obtain(
                        phoneDown,
                        now,
                        action,
                        xs.length,
                        props,
                        coords,
                        0,
                        0,
                        1,
                        1,
                        0,
                        0,
                        InputDevice.SOURCE_TOUCHSCREEN,
                        0);
        route(event, true);
        event.recycle();
    }

    void wheel(float horizontal, float vertical) {
        if (!current() || session.busy || physicalActive()) return;
        int pane = layout.selected;
        if (!occupied[pane]) return;
        SplitLayout.Box box = layout.pane(pane, session.width(), session.height(), gap());
        float x = Geometry.clamp(session.cx - box.x, 0, box.width - 1),
                y = Geometry.clamp(session.cy - box.y, 0, box.height - 1),
                speed = session.scrollSpeed();
        sendMotion(pane, InputEvents.wheel(x, y, horizontal * speed, vertical * speed));
    }

    private void route(MotionEvent event, boolean phone) {
        int action = event.getActionMasked();
        int w = session.width(), h = session.height();
        if (action == MotionEvent.ACTION_DOWN) {
            if (phone) cancelPhone();
            else {
                cancelPhysical();
                cancelPhone();
                session.hideMonitorPointer();
            }
            int grab = Math.max(gap(), Math.round(12 * session.dpi / 160f));
            int hit =
                    layout.dividerGrab(w, h, gap(), grab).contains(event.getX(), event.getY())
                            ? -1
                            : layout.hit(event.getX(), event.getY(), w, h, gap());
            if (hit < 0) {
                if (layout.dividerGrab(w, h, gap(), grab).contains(event.getX(), event.getY())) {
                    dividerHeld = true;
                    dividerPhone = phone;
                    dividerPreview = layout.ratio;
                }
                return;
            }
            selectInternal(hit);
            if (!occupied[hit]) {
                uiPane = hit;
                uiPhone = phone;
            } else if (phone) phonePane = hit;
            else physicalPane = hit;
        }
        if (dividerHeld) {
            if (phone != dividerPhone) return;
            if (action == MotionEvent.ACTION_MOVE) {
                dividerPreview =
                        Geometry.clamp(
                                (layout.vertical ? event.getY() / h : event.getX() / w),
                                .25f,
                                .75f);
                SplitLayout.Box bar = layout.divider(w, h, gap());
                if (layout.vertical) divider.setTranslationY(dividerPreview * h - bar.y);
                else divider.setTranslationX(dividerPreview * w - bar.x);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (action == MotionEvent.ACTION_UP)
                    dividerPreview =
                            Geometry.clamp(
                                    (layout.vertical ? event.getY() / h : event.getX() / w),
                                    .25f,
                                    .75f);
                dividerHeld = false;
                divider.setTranslationX(0);
                divider.setTranslationY(0);
                if (action == MotionEvent.ACTION_UP) ratio(dividerPreview);
            }
            return;
        }
        if (uiPane >= 0 && phone == uiPhone) {
            SplitLayout.Box box = layout.pane(uiPane, w, h, gap());
            MotionEvent local = MotionEvent.obtain(event);
            local.offsetLocation(-box.x, -box.y);
            decoration[uiPane].dispatchTouchEvent(local);
            local.recycle();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) uiPane = -1;
            return;
        }
        int pane = phone ? phonePane : physicalPane;
        boolean hover =
                action == MotionEvent.ACTION_HOVER_ENTER
                        || action == MotionEvent.ACTION_HOVER_MOVE
                        || action == MotionEvent.ACTION_HOVER_EXIT
                        || action == MotionEvent.ACTION_SCROLL;
        if (pane < 0 && hover) pane = layout.hit(event.getX(), event.getY(), w, h, gap());
        if (pane < 0 || !occupied[pane]) return;
        if (!hover && action != MotionEvent.ACTION_MOVE)
            session.log(
                    "Split contact: pane "
                            + (pane + 1)
                            + " "
                            + MotionEvent.actionToString(event.getAction())
                            + " pointers="
                            + event.getPointerCount());
        if (action == MotionEvent.ACTION_SCROLL) selectInternal(pane);
        SplitLayout.Box box = layout.pane(pane, w, h, gap());
        MotionEvent local = MotionEvent.obtain(event);
        local.offsetLocation(-box.x, -box.y);
        sendMotion(pane, local);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (phone) {
                if (phoneContact != null) phoneContact.recycle();
                phoneContact = null;
                phonePane = -1;
            } else {
                if (physicalContact != null) physicalContact.recycle();
                physicalContact = null;
                physicalPane = -1;
            }
        } else if (!hover) {
            if (phone) {
                if (phoneContact != null) phoneContact.recycle();
                phoneContact = remainingContact(event);
            } else {
                if (physicalContact != null) physicalContact.recycle();
                physicalContact = remainingContact(event);
            }
        }
    }

    void cancelPhone() {
        cancelContact(true);
    }

    private MotionEvent remainingContact(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_POINTER_UP)
            return MotionEvent.obtainNoHistory(event);
        int count = event.getPointerCount() - 1, next = 0;
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i == event.getActionIndex()) continue;
            properties[next] = new MotionEvent.PointerProperties();
            coords[next] = new MotionEvent.PointerCoords();
            event.getPointerProperties(i, properties[next]);
            event.getPointerCoords(i, coords[next]);
            next++;
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
                event.getFlags());
    }

    void cancelMonitorGesture() {
        cancelPhysical();
    }

    private void cancelPhysical() {
        cancelContact(false);
    }

    private void cancelContact(boolean phone) {
        if (dividerHeld && dividerPhone == phone) {
            dividerHeld = false;
            divider.setTranslationX(0);
            divider.setTranslationY(0);
        }
        MotionEvent held = phone ? phoneContact : physicalContact;
        int pane = phone ? phonePane : physicalPane;
        if (held != null) {
            if (pane >= 0) {
                MotionEvent cancel = MotionEvent.obtainNoHistory(held);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                SplitLayout.Box box = layout.pane(pane, session.width(), session.height(), gap());
                cancel.offsetLocation(-box.x, -box.y);
                sendMotion(pane, cancel);
            }
            held.recycle();
        }
        if (phone) {
            phoneContact = null;
            phonePane = -1;
        } else {
            physicalContact = null;
            physicalPane = -1;
        }
        if (uiPane >= 0 && uiPhone == phone) {
            MotionEvent cancel =
                    MotionEvent.obtain(
                            SystemClock.uptimeMillis(),
                            SystemClock.uptimeMillis(),
                            MotionEvent.ACTION_CANCEL,
                            0,
                            0,
                            0);
            decoration[uiPane].dispatchTouchEvent(cancel);
            cancel.recycle();
            uiPane = -1;
        }
    }

    void cancelInput() {
        cancelPhone();
        cancelPhysical();
        dividerHeld = false;
        cancelKeys();
    }

    void cancelKeys() {
        for (Map.Entry<Long, HeldKey> item : keys.entrySet()) {
            HeldKey held = item.getValue();
            sendKey(
                    held.pane,
                    new KeyEvent(
                            held.down.getDownTime(),
                            SystemClock.uptimeMillis(),
                            KeyEvent.ACTION_UP,
                            held.down.getKeyCode(),
                            0,
                            held.down.getMetaState(),
                            held.down.getDeviceId(),
                            held.down.getScanCode(),
                            held.down.getFlags() | KeyEvent.FLAG_CANCELED,
                            held.down.getSource()));
            cancelledKeys.add(item.getKey());
            if (cancelledKeys.size() > 64) cancelledKeys.remove(cancelledKeys.iterator().next());
        }
        keys.clear();
    }

    void detach(boolean keepPrimary) {
        if (closed) return;
        session.cancelAppSelection();
        if (cancelSurfaceLayout != null) cancelSurfaceLayout.run();
        cancelInput();
        closed = true;
        if (session.split == this) session.split = null;
        for (FrameLayout frame : decoration) layer.removeView(frame);
        layer.removeView(divider);
        layer.removeView(secondary);
        primary.setAlpha(1);
        if (keepPrimary) {
            session.activePackage = packages[0];
            session.activeComponent = apps[0];
            session.splitPrimaryState(occupied[0], protectedPane[0]);
            session.splitGeometryChanged();
        }
        input.shutdown();
        final IDeskService target = secondBridge;
        final int id = secondId;
        session.desktopWork(
                () -> {
                    try {
                        if (!input.awaitTermination(5, TimeUnit.SECONDS))
                            session.log("Split input shutdown timed out");
                        if (target != null) {
                            if (id >= 0) target.closeApps(id);
                            target.stop();
                        }
                    } catch (Exception error) {
                        session.log("Secondary cleanup failed: " + error.getMessage());
                        try {
                            if (target != null) target.stop();
                        } catch (Exception ignored) {
                            session.log("Secondary bridge unavailable during cleanup");
                        }
                    } finally {
                        if (secondSurface != null) secondSurface.release();
                        main.post(
                                () -> {
                                    if (bound) {
                                        bound = false;
                                        try {
                                            Shizuku.unbindUserService(args, connection, true);
                                        } catch (RuntimeException error) {
                                            session.log(
                                                    "Secondary service unbind failed: "
                                                            + error.getMessage());
                                        }
                                    }
                                    if (secondTexture != null) secondTexture.release();
                                });
                    }
                });
        session.notifyUi();
    }
}
