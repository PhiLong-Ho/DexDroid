package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import rikka.shizuku.Shizuku;

/** Explicit debug-only experiment; not reachable through the product launcher or release build. */
public final class SplitProbeActivity extends Activity implements DisplayManager.DisplayListener {
    final Pane[] panes = new Pane[2];
    final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker =
            new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(128));
    private final IBinder owner = new Binder();
    private final Map<Integer, Pane> heldKeys = new HashMap<>();
    private final Shizuku.OnBinderReceivedListener shizukuReady = () -> main.post(this::start);
    private final Shizuku.OnRequestPermissionResultListener permission =
            (request, result) -> start();
    private DisplayManager displays;
    Presentation presentation;
    LinearLayout desktop;
    TextView status;
    private ControllerStyle style;
    private int selected, contact = -1;
    private float ratio = .5f;
    volatile boolean stopping;
    private boolean started;
    private ServiceConnection controller;
    private boolean controllerBound;
    private boolean waitingForFocus;
    private final ArrayDeque<PendingInput> pendingInput = new ArrayDeque<>();
    private final Runnable focusTimeout = this::releasePending;

    private final class PendingInput {
        final Pane pane;
        final InputEvent event;

        PendingInput(Pane pane, InputEvent event) {
            this.pane = pane;
            this.event = event;
        }

        void discard() {
            if (event instanceof MotionEvent) ((MotionEvent) event).recycle();
        }
    }

    private void releasePending() {
        if (!waitingForFocus || stopping) return;
        waitingForFocus = false;
        main.removeCallbacks(focusTimeout);
        ArrayDeque<PendingInput> ready = new ArrayDeque<>(pendingInput);
        pendingInput.clear();
        android.util.Log.i(
                "DexDroidSplitProbe",
                "Legacy host focus settled; releasing " + ready.size() + " input events");
        while (!ready.isEmpty()) {
            PendingInput pending = ready.removeFirst();
            pending.pane.send(pending.event);
        }
    }

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        style = new ControllerStyle(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(style.dp(16), style.dp(32), style.dp(16), style.dp(16));
        root.setBackgroundColor(Color.rgb(8, 8, 8));
        root.addView(style.label("DexDroid | Two-pane experiment", 22));
        status = style.label("Waiting for Shizuku and monitor", 13);
        root.addView(status);
        LinearLayout focus = new LinearLayout(this);
        root.addView(focus);
        style.button(focus, "Left app", () -> choose(0));
        style.button(focus, "Right app", () -> choose(1));
        LinearLayout actions = new LinearLayout(this);
        root.addView(actions);
        style.button(actions, "Split ratio", () -> setRatio(ratio == .5f ? .35f : .5f));
        style.button(actions, "Back", () -> keyCode(KeyEvent.KEYCODE_BACK));
        style.button(actions, "Stop", () -> stopExperiment(true));
        root.addView(
                style.label(
                        "Touch a pane to select it. Hardware keys go to that pane.\n"
                                + "Select Left/Right app to choose another app.\n"
                                + "Debug experiment: two displays, no saved product settings.",
                        14));
        View pointer =
                new View(this) {
                    private boolean route(MotionEvent event) {
                        if (desktop == null || desktop.getWidth() < 1) return true;
                        float x = event.getX() * desktop.getWidth() / Math.max(1, getWidth());
                        float y = event.getY() * desktop.getHeight() / Math.max(1, getHeight());
                        Pane pane = paneAt(x);
                        MotionEvent copy = MotionEvent.obtain(event);
                        Matrix scale = new Matrix();
                        scale.setScale(
                                desktop.getWidth() / (float) Math.max(1, getWidth()),
                                desktop.getHeight() / (float) Math.max(1, getHeight()));
                        copy.transform(scale);
                        copy.offsetLocation(-pane.frame.getLeft(), 0);
                        pane.route(copy);
                        copy.recycle();
                        return true;
                    }

                    @Override
                    public boolean onGenericMotionEvent(MotionEvent event) {
                        return route(event);
                    }

                    @Override
                    public boolean onTouchEvent(MotionEvent event) {
                        if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
                        return route(event);
                    }

                    @Override
                    public boolean performClick() {
                        super.performClick();
                        return true;
                    }

                    @Override
                    public boolean onHoverEvent(MotionEvent event) {
                        return route(event);
                    }
                };
        pointer.setBackground(style.surface(18, 12, 45));
        pointer.setContentDescription("Hardware pointer area mapped to external desktop");
        root.addView(style.label("Hardware pointer area (maps phone to monitor)", 12));
        root.addView(pointer, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        displays = getSystemService(DisplayManager.class);
        displays.registerDisplayListener(this, main);
        Shizuku.addBinderReceivedListenerSticky(shizukuReady);
        Shizuku.addRequestPermissionResultListener(permission);
    }

    private void report(String message) {
        main.post(() -> status.setText(message));
        android.util.Log.i("DexDroidSplitProbe", message);
    }

    private final class Work implements Runnable {
        final Runnable task, discard;

        Work(Runnable task, Runnable discard) {
            this.task = task;
            this.discard = discard;
        }

        @Override
        public void run() {
            if (stopping) {
                discard.run();
                return;
            }
            try {
                task.run();
            } catch (Exception error) {
                report("Experiment failed: " + error);
                main.post(() -> stopExperiment(false));
            }
        }
    }

    private void execute(Runnable task) {
        execute(task, () -> {});
    }

    private void execute(Runnable task, Runnable discard) {
        if (stopping) {
            discard.run();
            return;
        }
        try {
            worker.execute(new Work(task, discard));
        } catch (RejectedExecutionException full) {
            discard.run();
            report("Input queue full; stopping experiment instead of replaying stale input");
            stopExperiment(false);
        }
    }

    private void start() {
        if (started || stopping || isFinishing() || !Shizuku.pingBinder()) return;
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(401);
            return;
        }
        Display[] external = displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        Display monitor = null;
        for (Display display : external)
            if (!display.getName().contains("DexDroid")) {
                monitor = display;
                break;
            }
        if (monitor == null) {
            report("Connect external monitor");
            return;
        }
        started = true;
        final Display target = monitor;
        controller =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        ((SessionService.LocalBinder) binder).get().pauseDesktop();
                        awaitProductStop(target, SystemClock.uptimeMillis() + 10000);
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {}
                };
        controllerBound =
                bindService(new Intent(this, SessionService.class), controller, BIND_AUTO_CREATE);
        if (!controllerBound) report("Could not pause product desktop");
    }

    private void awaitProductStop(Display target, long deadline) {
        if (stopping) return;
        for (Display display : displays.getDisplays())
            if (display.getName().startsWith("DexDroid Desktop")) {
                if (SystemClock.uptimeMillis() >= deadline) {
                    report("Product desktop did not stop");
                    return;
                }
                main.postDelayed(() -> awaitProductStop(target, deadline), 50);
                return;
            }
        showMonitor(target);
    }

    private void showMonitor(Display target) {
        presentation =
                new Presentation(this, target, R.style.DexDroidTheme) {
                    @Override
                    public void onWindowFocusChanged(boolean focused) {
                        super.onWindowFocusChanged(focused);
                        if (focused) releasePending();
                    }

                    @Override
                    public boolean dispatchKeyEvent(KeyEvent event) {
                        return routeKey(event) || super.dispatchKeyEvent(event);
                    }
                };
        desktop = new LinearLayout(presentation.getContext());
        desktop.setBackgroundColor(Color.BLACK);
        desktop.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 2; i++) {
            panes[i] = new Pane(i, presentation.getContext());
            desktop.addView(panes[i].frame, new LinearLayout.LayoutParams(0, -1, .5f));
        }
        presentation.setContentView(desktop);
        presentation
                .getWindow()
                .addFlags(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
                                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        presentation.setOnDismissListener(dialog -> stopExperiment(false));
        presentation.show();
        presentation.getWindow().getDecorView().setSystemUiVisibility(5894);
        report("Creating two independent app displays");
    }

    Pane paneAt(float x) {
        return contact >= 0 ? panes[contact] : panes[x < panes[0].texture.getWidth() ? 0 : 1];
    }

    void select(int index) {
        selected = index;
        for (int i = 0; i < 2; i++)
            if (panes[i] != null) {
                android.graphics.drawable.GradientDrawable border =
                        new android.graphics.drawable.GradientDrawable();
                border.setColor(Color.TRANSPARENT);
                border.setStroke(style.dp(2), i == selected ? Color.WHITE : Color.rgb(45, 45, 45));
                panes[i].frame.setForeground(border);
            }
        if (panes[index] != null && panes[index].id > 0)
            report(
                    "Selected "
                            + (index == 0 ? "left" : "right")
                            + " | display "
                            + panes[index].id
                            + " | "
                            + panes[index].width
                            + "x"
                            + panes[index].height);
    }

    void setRatio(float value) {
        if (stopping || desktop == null || contact >= 0 || !heldKeys.isEmpty()) return;
        ratio = value;
        for (int i = 0; i < 2; i++) {
            LinearLayout.LayoutParams layout =
                    (LinearLayout.LayoutParams) panes[i].frame.getLayoutParams();
            layout.weight = i == 0 ? ratio : 1 - ratio;
            panes[i].frame.setLayoutParams(layout);
        }
    }

    private void choose(int index) {
        select(index);
        if (panes[index] == null) return;
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        execute(
                () -> {
                    List<android.content.pm.ResolveInfo> apps =
                            getPackageManager().queryIntentActivities(launcher, 0);
                    apps.sort(
                            Comparator.comparing(
                                    app -> app.loadLabel(getPackageManager()).toString(),
                                    String.CASE_INSENSITIVE_ORDER));
                    String[] labels = new String[apps.size()];
                    for (int i = 0; i < apps.size(); i++)
                        labels[i] = apps.get(i).loadLabel(getPackageManager()).toString();
                    main.post(
                            () -> {
                                if (stopping) return;
                                new AlertDialog.Builder(this)
                                        .setTitle(index == 0 ? "Left app" : "Right app")
                                        .setItems(
                                                labels,
                                                (dialog, which) -> {
                                                    android.content.pm.ActivityInfo app =
                                                            apps.get(which).activityInfo;
                                                    launch(
                                                            index,
                                                            new ComponentName(
                                                                            app.packageName,
                                                                            app.name)
                                                                    .flattenToString());
                                                })
                                        .setNegativeButton("Close", null)
                                        .show();
                            });
                });
    }

    void launch(int index, String component) {
        Pane pane = panes[index];
        if (pane == null || pane.id < 1) return;
        execute(
                () -> {
                    try {
                        pane.bridge.launch(pane.id, component);
                    } catch (RemoteException error) {
                        throw new IllegalStateException(error);
                    }
                    report((index == 0 ? "Left: " : "Right: ") + component);
                });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return routeKey(event) || super.dispatchKeyEvent(event);
    }

    boolean routeKey(KeyEvent event) {
        if (stopping || panes[selected] == null || panes[selected].id < 1) return false;
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP
                || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN
                || event.getKeyCode() == KeyEvent.KEYCODE_POWER) return false;
        // Soft IME text/composition is intentionally outside this raw hardware-key experiment.
        if (!event.isFromSource(InputDevice.SOURCE_KEYBOARD)
                && event.getKeyCode() != KeyEvent.KEYCODE_BACK) return false;
        Pane pane = heldKeys.get(event.getKeyCode());
        if (pane == null) pane = panes[selected];
        if (event.getAction() == KeyEvent.ACTION_DOWN) heldKeys.put(event.getKeyCode(), pane);
        if (event.getAction() == KeyEvent.ACTION_UP) heldKeys.remove(event.getKeyCode());
        pane.send(new KeyEvent(event));
        return true;
    }

    private void keyCode(int code) {
        Pane pane = panes[selected];
        if (pane == null) return;
        execute(
                () -> {
                    try {
                        pane.bridge.key(pane.id, code);
                    } catch (RemoteException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    void stopExperiment(boolean finish) {
        if (stopping) return;
        stopping = true;
        heldKeys.clear();
        main.removeCallbacks(focusTimeout);
        waitingForFocus = false;
        while (!pendingInput.isEmpty()) pendingInput.removeFirst().discard();
        // Input operations check stopping at execution; cleanup remains ordered behind in-flight
        // work.
        Runnable pending;
        while ((pending = worker.getQueue().poll()) != null) ((Work) pending).discard.run();
        worker.execute(
                () -> {
                    for (Pane pane : panes) {
                        if (pane == null) continue;
                        try {
                            if (pane.bridge != null) pane.bridge.stop();
                        } catch (Exception error) {
                            report("Cleanup error: " + error);
                        }
                        if (pane.surface != null) pane.surface.release();
                    }
                    main.post(
                            () -> {
                                if (presentation != null) presentation.dismiss();
                                for (Pane pane : panes) {
                                    if (pane == null) continue;
                                    if (pane.args != null && pane.connection != null)
                                        Shizuku.unbindUserService(pane.args, pane.connection, true);
                                    pane.id = -1;
                                    if (pane.retained != null) pane.retained.release();
                                }
                                if (finish) finish();
                            });
                });
        worker.shutdown();
        report("Experiment stopped; reopen DexDroid to restore the normal desktop");
    }

    @Override
    public void onDisplayAdded(int id) {
        start();
    }

    @Override
    public void onDisplayChanged(int id) {}

    @Override
    public void onDisplayRemoved(int id) {
        if (presentation != null && presentation.getDisplay().getDisplayId() == id)
            stopExperiment(true);
    }

    @Override
    public void onDestroy() {
        stopExperiment(false);
        displays.unregisterDisplayListener(this);
        Shizuku.removeBinderReceivedListener(shizukuReady);
        Shizuku.removeRequestPermissionResultListener(permission);
        if (controllerBound) unbindService(controller);
        super.onDestroy();
    }

    final class Pane implements TextureView.SurfaceTextureListener {
        final int index;
        final PaneTexture texture;
        final FrameLayout frame;
        volatile int id = -1, width, height;
        Surface surface;
        SurfaceTexture retained;
        IDeskService bridge;
        Shizuku.UserServiceArgs args;
        ServiceConnection connection;

        Pane(int index, Context context) {
            this.index = index;
            frame = new FrameLayout(context);
            texture = new PaneTexture(context);
            frame.addView(texture, new FrameLayout.LayoutParams(-1, -1));
            texture.setSurfaceTextureListener(this);
            texture.setContentDescription(
                    index == 0 ? "Left managed display" : "Right managed display");
        }

        final class PaneTexture extends TextureView {
            PaneTexture(Context context) {
                super(context);
            }

            @Override
            public boolean performClick() {
                super.performClick();
                select(index);
                return true;
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
                return route(event);
            }

            @Override
            public boolean onGenericMotionEvent(MotionEvent event) {
                return route(event);
            }

            @Override
            public boolean onHoverEvent(MotionEvent event) {
                return route(event);
            }
        }

        boolean route(MotionEvent event) {
            if (stopping || id < 1) return true;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                contact = index;
                select(index);
                if (Build.VERSION.SDK_INT == 30
                        && !waitingForFocus
                        && !presentation.getWindow().getDecorView().hasWindowFocus()) {
                    waitingForFocus = true;
                    main.postDelayed(focusTimeout, 100);
                }
            }
            if (action == MotionEvent.ACTION_SCROLL) select(index);
            Pane target = contact >= 0 ? panes[contact] : this;
            MotionEvent copy = MotionEvent.obtain(event);
            if (target != this) copy.offsetLocation(frame.getLeft() - target.frame.getLeft(), 0);
            target.send(copy);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                contact = -1;
            return true;
        }

        void send(InputEvent event) {
            if (waitingForFocus) {
                PendingInput pending = new PendingInput(this, event);
                if (pendingInput.size() >= 32) {
                    pending.discard();
                    report("Legacy focus buffer full; stopping experiment");
                    stopExperiment(false);
                } else pendingInput.addLast(pending);
                return;
            }
            int target = id;
            execute(
                    () -> {
                        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
                        try {
                            data.writeInterfaceToken(SplitProbeService.PROTOCOL);
                            data.writeInt(target);
                            data.writeInt(event instanceof MotionEvent ? 1 : 2);
                            event.writeToParcel(data, 0);
                            if (!bridge.asBinder()
                                    .transact(SplitProbeService.INPUT, data, reply, 0))
                                throw new IllegalStateException("Prototype transaction rejected");
                            reply.readException();
                        } catch (RemoteException error) {
                            throw new IllegalStateException(error);
                        } finally {
                            data.recycle();
                            reply.recycle();
                            if (event instanceof MotionEvent) ((MotionEvent) event).recycle();
                        }
                    },
                    () -> {
                        if (event instanceof MotionEvent) ((MotionEvent) event).recycle();
                    });
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture source, int w, int h) {
            width = w;
            height = h;
            retained = source;
            surface = new Surface(source);
            args =
                    new Shizuku.UserServiceArgs(
                                    new ComponentName(
                                            SplitProbeActivity.this,
                                            index == 0
                                                    ? SplitProbeService.Left.class
                                                    : SplitProbeService.Right.class))
                            .daemon(false)
                            .processNameSuffix("split-probe-" + index)
                            .debuggable(true)
                            .version(1);
            connection =
                    new ServiceConnection() {
                        @Override
                        public void onServiceConnected(ComponentName name, IBinder binder) {
                            if (stopping) {
                                Shizuku.unbindUserService(args, this, true);
                                return;
                            }
                            bridge = IDeskService.Stub.asInterface(binder);
                            execute(
                                    () -> {
                                        try {
                                            id =
                                                    bridge.create(
                                                            surface,
                                                            width,
                                                            height,
                                                            240,
                                                            owner,
                                                            new IDesktopListener.Stub() {
                                                                @Override
                                                                public void onProtectionState(
                                                                        int display, int state) {}

                                                                @Override
                                                                public void onTaskState(
                                                                        int display,
                                                                        boolean occupied,
                                                                        String component,
                                                                        String error) {}
                                                            });
                                            report(
                                                    "Pane "
                                                            + index
                                                            + " ready | display "
                                                            + id
                                                            + " | "
                                                            + width
                                                            + "x"
                                                            + height);
                                            String app =
                                                    getIntent()
                                                            .getStringExtra(
                                                                    index == 0 ? "left" : "right");
                                            if (app != null) main.post(() -> launch(index, app));
                                        } catch (RemoteException error) {
                                            throw new IllegalStateException(error);
                                        }
                                    });
                        }

                        @Override
                        public void onServiceDisconnected(ComponentName name) {
                            report("Pane bridge disconnected");
                            stopExperiment(false);
                        }
                    };
            Shizuku.bindUserService(args, connection);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture source, int w, int h) {
            width = w;
            height = h;
            if (id > 0)
                execute(
                        () -> {
                            try {
                                bridge.resize(surface, w, h, 240);
                            } catch (RemoteException error) {
                                throw new IllegalStateException(error);
                            }
                        });
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture source) {
            retained = source;
            stopExperiment(false);
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture source) {}
    }
}
