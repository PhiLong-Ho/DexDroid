package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real rotated monitor and phone-pointer interaction with the first-launch surface. */
final class IdleAcceptance {
    private final Instrumentation runner;
    private final StringBuilder results = new StringBuilder();
    private SessionService session;
    private ServiceConnection binding;
    private boolean bound;
    private Map<String, ?> preferences;
    private long down;
    private MainActivity phone;
    private MainActivity resumedPhone;
    private final Application.ActivityLifecycleCallbacks controllerLifecycle =
            new Application.ActivityLifecycleCallbacks() {
                public void onActivityCreated(Activity activity, Bundle state) {}

                public void onActivityStarted(Activity activity) {}

                public void onActivityResumed(Activity activity) {
                    if (activity instanceof MainActivity) resumedPhone = (MainActivity) activity;
                }

                public void onActivityPaused(Activity activity) {
                    if (activity == resumedPhone) resumedPhone = null;
                }

                public void onActivityStopped(Activity activity) {}

                public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

                public void onActivityDestroyed(Activity activity) {}
            };

    IdleAcceptance(Instrumentation runner) {
        this.runner = runner;
    }

    private void main(Runnable action) {
        runner.runOnMainSync(action);
    }

    private void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        results.append("PASS ").append(label).append('\n');
    }

    private void await(BooleanSupplier predicate, String label) throws Exception {
        long until = SystemClock.uptimeMillis() + 10000;
        while (!predicate.getAsBoolean() && SystemClock.uptimeMillis() < until) Thread.sleep(50);
        check(predicate.getAsBoolean(), label);
    }

    private void phoneTap(View view) throws Exception {
        phoneTap(view, true);
    }

    private void phoneTap(View view, boolean requireFocus) throws Exception {
        check(view != null, "phone input target exists");
        await(
                () -> {
                    boolean[] ready = {false};
                    main(
                            () ->
                                    ready[0] =
                                            view.isShown()
                                                    && view.getWidth() > 0
                                                    && view.getHeight() > 0
                                                    && !view.isLayoutRequested()
                                                    && (!requireFocus || view.hasWindowFocus()));
                    return ready[0];
                },
                "phone input target measured and focused");
        int[] point = new int[2];
        main(
                () -> {
                    view.getLocationOnScreen(point);
                    point[0] += view.getWidth() / 2;
                    point[1] += view.getHeight() / 2;
                    check(view.isShown(), "phone input target visible");
                });
        long start = SystemClock.uptimeMillis();
        for (int action : new int[] {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event =
                    MotionEvent.obtain(
                            start, SystemClock.uptimeMillis(), action, point[0], point[1], 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try {
                if (!requireFocus)
                    MotionEvent.class.getMethod("setDisplayId", int.class).invoke(event, 0);
                check(
                        runner.getUiAutomation().injectInputEvent(event, true),
                        "real phone input delivered");
            } finally {
                event.recycle();
            }
            if (action == MotionEvent.ACTION_DOWN) Thread.sleep(50);
        }
    }

    private View description(View root, String value) {
        if (value.equals(String.valueOf(root.getContentDescription()))) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = description(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void resumePhone(int rotation) throws Exception {
        int runningDisplay = session.id;
        main(() -> phone.moveTaskToBack(true));
        // Observe completion, not a fixed sleep that races Android's TO_BACK transition.
        await(
                () -> {
                    boolean[] hidden = {false};
                    main(
                            () ->
                                    hidden[0] =
                                            phone.getWindow().getDecorView().getWindowVisibility()
                                                    != View.VISIBLE);
                    return hidden[0];
                },
                "phone background transition completed " + rotation);
        check(
                session.id == runningDisplay && session.hasDesktopApp(),
                "external app survives controller background " + rotation);
        String opened =
                DeviceTestSupport.shell(
                        runner,
                        "am start -W --display 0 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n dev.dexdroid/.MainActivity");
        check(!opened.contains("Error"), "phone launcher open accepted " + rotation);
        await(
                () -> phone.hasWindowFocus(),
                "phone controller resumes after background " + rotation);
        check(
                !phone.isDestroyed() && phone.getDisplay().getDisplayId() == 0,
                "same phone controller reused " + rotation);
        phoneTap(description(phone.getWindow().getDecorView(), "Settings"));
        await(
                () -> phone.controllerMenu() != null,
                "phone Settings accepts real input after resume " + rotation);
        View[] close = {null};
        await(
                () -> {
                    main(
                            () -> {
                                Dialog menu = phone.controllerMenu();
                                close[0] =
                                        menu == null
                                                ? null
                                                : menu.getWindow()
                                                        .getDecorView()
                                                        .findViewWithTag("phone-menu-close");
                            });
                    return close[0] != null;
                },
                "phone Settings contents attached " + rotation);
        phoneTap(close[0]);
        await(
                () -> phone.controllerMenu() == null,
                "phone Close accepts real input after resume " + rotation);
        await(
                () -> phone.hasWindowFocus(),
                "phone controller focus restored after Close " + rotation);
        check(
                session.id == runningDisplay,
                "controller resume retains desktop display " + rotation);
        int before = InputTestActivity.touchEvents;
        float[] point = physical(session.width() * .5f, session.height() * .5f);
        tap(point[0], point[1]);
        await(
                () -> InputTestActivity.touchEvents > before,
                "fresh external input after phone resume " + rotation);
    }

    private void restore() {
        if (preferences == null || session == null) return;
        android.content.SharedPreferences.Editor edit = session.prefs.edit().clear();
        for (Map.Entry<String, ?> item : preferences.entrySet()) {
            Object value = item.getValue();
            String key = item.getKey();
            if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) edit.putInt(key, (Integer) value);
            else if (value instanceof Float) edit.putFloat(key, (Float) value);
            else if (value instanceof String) edit.putString(key, (String) value);
            else if (value instanceof Long) edit.putLong(key, (Long) value);
        }
        edit.commit();
    }

    private View view(String tag) {
        View root = session.monitorShellView();
        return root == null ? null : root.findViewWithTag(tag);
    }

    private void page(String expected) throws Exception {
        await(
                () -> {
                    boolean[] settled = {false};
                    main(
                            () -> {
                                View root = session.monitorShellView();
                                if (root == null
                                        || !expected.equals(session.monitorShellPage())
                                        || session.monitorShellAnimating()
                                        || !root.isLaidOut()
                                        || root.isLayoutRequested()) return;
                                if (!expected.equals("closed")) {
                                    View panel = view("monitor-panel"),
                                            close = view("monitor-close");
                                    if (panel == null
                                            || close == null
                                            || !panel.isLaidOut()
                                            || panel.isLayoutRequested()
                                            || !close.isShown()
                                            || close.getWidth() == 0
                                            || close.getHeight() == 0
                                            || close.isLayoutRequested()) return;
                                }
                                settled[0] = true;
                            });
                    return settled[0];
                },
                "idle menu settled " + expected);
    }

    private void fresh() throws Exception {
        main(session::pauseDesktop);
        await(() -> session.id < 0 && session.monitorShellView() == null, "old desktop released");
        main(session::ensureDesktop);
        await(
                () -> session.id >= 0 && !session.busy && session.monitorShellView() != null,
                "fresh desktop ready");
        Thread.sleep(300);
        check(!session.hasDesktopApp(), "fresh desktop has not launched an app");
        page("closed");
    }

    private void event(int action, float x, float y) throws Exception {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) down = now;
        MotionEvent event = MotionEvent.obtain(down, now, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            check(
                    DeviceTestSupport.injectOnDisplay(event, session.external.getDisplayId()),
                    "idle physical monitor event " + action);
        } finally {
            event.recycle();
        }
    }

    private void tap(float x, float y) throws Exception {
        event(MotionEvent.ACTION_DOWN, x, y);
        event(MotionEvent.ACTION_UP, x, y);
        Thread.sleep(80);
    }

    private float[] physical(float x, float y) {
        Display.Mode mode = session.external.getMode();
        return Geometry.map(
                x / session.width(),
                y / session.height(),
                session.rotation,
                mode.getPhysicalWidth(),
                mode.getPhysicalHeight(),
                session.width(),
                session.height());
    }

    private Rect bounds(String tag) {
        Rect rect = new Rect();
        main(
                () -> {
                    View target = view(tag);
                    check(
                            target != null && target.isShown() && target.getGlobalVisibleRect(rect),
                            "idle control visible " + tag);
                });
        return rect;
    }

    private void click(String tag) throws Exception {
        Rect rect = bounds(tag);
        tap(rect.exactCenterX(), rect.exactCenterY());
    }

    private void pointerClick(String tag) throws Exception {
        main(
                () -> {
                    View target = view(tag), root = session.monitorShellView();
                    check(target != null && target.isShown(), "idle pointer target visible " + tag);
                    Rect visible = new Rect();
                    Matrix toGlobal = new Matrix(),
                            rootToGlobal = new Matrix(),
                            toLogical = new Matrix();
                    target.transformMatrixToGlobal(toGlobal);
                    root.transformMatrixToGlobal(rootToGlobal);
                    boolean invertible = rootToGlobal.invert(toLogical);
                    check(
                            target.getWidth() > 0
                                    && target.getHeight() > 0
                                    && target.getGlobalVisibleRect(visible)
                                    && !visible.isEmpty()
                                    && invertible,
                            "idle pointer target has measured hittable bounds " + tag);
                    // Include actual animation/scroll transforms rather than assuming every
                    // ancestor has only its layout left/top offsets.
                    float[] center = {target.getWidth() / 2f, target.getHeight() / 2f};
                    toGlobal.mapPoints(center);
                    toLogical.mapPoints(center);
                    session.cx = center[0];
                    session.cy = center[1];
                    int[] delivered = {0, 0};
                    target.setOnTouchListener(
                            (v, event) -> {
                                if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
                                    delivered[0]++;
                                if (event.getActionMasked() == MotionEvent.ACTION_UP)
                                    delivered[1]++;
                                return false;
                            });
                    try {
                        session.click();
                    } finally {
                        target.setOnTouchListener(null);
                    }
                    check(
                            delivered[0] == 1 && delivered[1] == 1,
                            "phone pointer dispatch reaches the target once for DOWN and UP "
                                    + tag);
                });
        Thread.sleep(80);
    }

    private void idle(boolean shown) {
        main(
                () -> {
                    View idle = view("monitor-idle"), apps = view("monitor-idle-apps");
                    check(
                            idle != null && apps instanceof ImageButton,
                            "native idle background and icon exist");
                    check(
                            idle.isShown() == shown && apps.isShown() == shown,
                            "idle visibility " + shown);
                    if (shown) {
                        ImageButton icon = (ImageButton) apps;
                        check(
                                icon.getDrawable() != null
                                        && "Apps".contentEquals(icon.getContentDescription()),
                                "idle icon has Apps description and drawable");
                        float density = icon.getResources().getDisplayMetrics().density;
                        check(
                                Math.abs(icon.getWidth() / density - 76) < 1
                                        && Math.abs(icon.getHeight() / density - 76) < 1,
                                "idle Apps target is 76 dp square");
                        check(
                                !idle.isForceDarkAllowed(),
                                "idle monochrome surface preserves its colors");
                    }
                });
    }

    private void centered() {
        Rect rect = bounds("monitor-idle-apps");
        Display.Mode mode = session.external.getMode();
        check(
                Math.abs(rect.exactCenterX() - mode.getPhysicalWidth() / 2f) < 2
                        && Math.abs(rect.exactCenterY() - mode.getPhysicalHeight() / 2f) < 2,
                "idle Apps centered in physical rotation " + session.rotation);
        main(
                () -> {
                    View idle = view("monitor-idle");
                    check(
                            idle.getWidth() == session.width()
                                    && idle.getHeight() == session.height(),
                            "idle background fills logical rotated desktop");
                });
    }

    private void rendered() throws Exception {
        Display.Mode mode = session.external.getMode();
        Bitmap bitmap =
                Bitmap.createBitmap(
                        mode.getPhysicalWidth(), mode.getPhysicalHeight(), Bitmap.Config.ARGB_8888);
        CountDownLatch copied = new CountDownLatch(1);
        int[] result = {-1};
        try {
            main(
                    () ->
                            PixelCopy.request(
                                    session.monitorWindow(),
                                    bitmap,
                                    value -> {
                                        result[0] = value;
                                        copied.countDown();
                                    },
                                    new Handler(Looper.getMainLooper())));
            check(
                    copied.await(5, TimeUnit.SECONDS) && result[0] == PixelCopy.SUCCESS,
                    "idle rendering captured");
            for (float[] logical :
                    new float[][] {
                        {session.width() * .1f, session.height() * .5f},
                        {session.width() * .9f, session.height() * .5f}
                    }) {
                float[] p = physical(logical[0], logical[1]);
                int color = bitmap.getPixel(Math.round(p[0]), Math.round(p[1]));
                check(
                        Color.red(color) == Color.green(color)
                                && Color.red(color) == Color.blue(color)
                                && Color.red(color) < 40,
                        "rendered idle background is dark monochrome");
            }
            Rect rect = bounds("monitor-idle-apps");
            int white = 0, dark = 0;
            for (int y = rect.top; y < rect.bottom; y++)
                for (int x = rect.left; x < rect.right; x++) {
                    int color = bitmap.getPixel(x, y);
                    if (Color.red(color) > 200
                            && Color.green(color) > 200
                            && Color.blue(color) > 200) white++;
                    if (Color.red(color) < 50 && Color.green(color) < 50 && Color.blue(color) < 50)
                        dark++;
                }
            check(white > 20 && dark > white, "rendered idle Apps icon is white on charcoal");
        } finally {
            bitmap.recycle();
        }
    }

    private void renderedApp(int rotation) throws Exception {
        Display.Mode mode = session.external.getMode();
        Bitmap bitmap =
                Bitmap.createBitmap(
                        mode.getPhysicalWidth(), mode.getPhysicalHeight(), Bitmap.Config.ARGB_8888);
        try {
            long deadline = SystemClock.uptimeMillis() + 5000;
            boolean rendered = false;
            int color = 0;
            do {
                CountDownLatch copied = new CountDownLatch(1);
                int[] code = {-1};
                main(
                        () ->
                                PixelCopy.request(
                                        session.monitorWindow(),
                                        bitmap,
                                        result -> {
                                            code[0] = result;
                                            copied.countDown();
                                        },
                                        new Handler(Looper.getMainLooper())));
                if (!copied.await(5, TimeUnit.SECONDS) || code[0] != PixelCopy.SUCCESS)
                    throw new AssertionError("PixelCopy app capture failed: " + code[0]);
                float[] sample = physical(session.width() * .3f, session.height() * .7f);
                color = bitmap.getPixel(Math.round(sample[0]), Math.round(sample[1]));
                rendered =
                        Color.blue(color) > Color.red(color) + 20
                                && Color.green(color) > Color.red(color) + 5;
                if (!rendered) Thread.sleep(50);
            } while (!rendered && SystemClock.uptimeMillis() < deadline);
            File folder =
                    new File(
                            runner.getTargetContext().getExternalCacheDir(),
                            "compatibility-acceptance");
            check(folder.isDirectory() || folder.mkdirs(), "compatibility screenshot directory");
            try (FileOutputStream stream =
                    new FileOutputStream(
                            new File(folder, "app-before-menu-r" + rotation + ".png"))) {
                check(
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream),
                        "saved visible app screenshot " + rotation);
            }
            if (android.os.Build.VERSION.SDK_INT == 30) {
                File internal =
                        new File(
                                runner.getTargetContext().getCacheDir(),
                                "app-before-menu-r" + rotation + ".png");
                try (FileOutputStream stream = new FileOutputStream(internal)) {
                    check(
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream),
                            "saved legacy app-private capture " + rotation);
                }
            }
            check(
                    rendered,
                    "rendered test-app pixels replace monochrome idle overlay "
                            + rotation
                            + " color="
                            + Integer.toHexString(color));
        } finally {
            bitmap.recycle();
        }
    }

    /** Reject the platform foreground adapter on the real bound service. */
    private void foregroundRejectionRecovery() throws Exception {
        main(session::pauseDesktop);
        java.lang.reflect.Field adapter =
                SessionService.class.getDeclaredField("foregroundPromotion");
        adapter.setAccessible(true);
        SessionService.ForegroundPromotion original =
                (SessionService.ForegroundPromotion) adapter.get(session);
        main(
                () -> {
                    try {
                        adapter.set(
                                session,
                                (SessionService.ForegroundPromotion)
                                        notification -> {
                                            throw new IllegalStateException(
                                                    "Controlled foreground startup rejection");
                                        });
                        session.ensureDesktop();
                        check(
                                session.id < 0 && !session.busy && !session.sessionAwake(),
                                "foreground rejection clears pending session and wake lock");
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException(e);
                    } finally {
                        try {
                            adapter.set(session, original);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
        await(
                () -> {
                    boolean[] explained = {false};
                    main(
                            () ->
                                    explained[0] =
                                            session.status.contains(
                                                            "Controlled foreground startup rejection")
                                                    && session.status.contains(
                                                            "Open DexDroid on the phone"));
                    return explained[0];
                },
                "foreground rejection explains phone-reopen recovery without escaping callback");
        reopenController("foreground rejection", true);
    }

    /** Fault-inject the state after an Activity request returned without a host attachment. */
    private void missingHostRecovery() throws Exception {
        main(session::stopDesktop);
        java.lang.reflect.Field workerField = SessionService.class.getDeclaredField("worker");
        workerField.setAccessible(true);
        ((java.util.concurrent.ExecutorService) workerField.get(session))
                .submit(() -> {})
                .get(10, TimeUnit.SECONDS);
        Class<?> type = Class.forName("dev.dexdroid.SessionService$Monitor");
        java.lang.reflect.Constructor<?> constructor =
                type.getDeclaredConstructor(SessionService.class, Display.class);
        constructor.setAccessible(true);
        java.lang.reflect.Field monitorField = SessionService.class.getDeclaredField("monitor");
        java.lang.reflect.Field generationField =
                SessionService.class.getDeclaredField("generation");
        java.lang.reflect.Field wakeField =
                SessionService.class.getDeclaredField("sessionWakeLock");
        java.lang.reflect.Field deadlineField =
                SessionService.class.getDeclaredField("monitorStartupDeadline");
        java.lang.reflect.Field tokenField = type.getDeclaredField("token");
        for (java.lang.reflect.Field f :
                new java.lang.reflect.Field[] {
                    monitorField, generationField, wakeField, deadlineField, tokenField
                }) f.setAccessible(true);
        Object[] pending = {null};
        Runnable[] staleDeadline = {null};
        int[] oldGeneration = {-1};
        MonitorActivity[] late = {null};
        main(
                () -> {
                    try {
                        late[0] = new MonitorActivity();
                        pending[0] = constructor.newInstance(session, session.external);
                        monitorField.set(session, pending[0]);
                        session.busy = true;
                        ((PowerManager.WakeLock) wakeField.get(session)).acquire();
                        oldGeneration[0] = generationField.getInt(session);
                        late[0].setIntent(
                                new Intent()
                                        .putExtra(
                                                "monitorToken",
                                                (String) tokenField.get(pending[0])));
                        session.awaitMonitorHost();
                        staleDeadline[0] = (Runnable) deadlineField.get(session);
                        check(
                                session.id < 0 && session.busy && session.sessionAwake(),
                                "unattached host remains pending before deadline");
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException(e);
                    }
                });
        long started = SystemClock.uptimeMillis();
        await(
                () -> {
                    boolean[] recovered = {false};
                    main(
                            () ->
                                    recovered[0] =
                                            !session.busy
                                                    && session.id < 0
                                                    && !session.sessionAwake()
                                                    && session.status.contains(
                                                            "Open DexDroid on the phone"));
                    return recovered[0];
                },
                "unattached host times out with cleanup and foreground recovery guidance");
        check(
                SystemClock.uptimeMillis() - started >= 4500,
                "startup waits its bounded five-second attachment interval");
        main(
                () -> {
                    check(
                            !session.attachMonitor(late[0], oldGeneration[0]),
                            "late host rejected after timeout");
                });
        reopenController("unattached host timeout", true);
        int newDisplay = session.id;
        main(
                () -> {
                    try {
                        check(
                                !session.attachMonitor(late[0], oldGeneration[0]),
                                "old generation cannot attach to recovered desktop");
                        check(
                                !session.attachMonitor(late[0], generationField.getInt(session)),
                                "old monitor token cannot attach to recovered desktop");
                        staleDeadline[0].run();
                        check(
                                session.id == newDisplay && !session.busy && session.sessionAwake(),
                                "stale host timeout cannot stop recovered desktop");
                        check(
                                deadlineField.get(session) == null,
                                "startup deadline cleared after teardown");
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private void reopenController(String reason, boolean background) throws Exception {
        if (background) {
            main(() -> phone.moveTaskToBack(true));
            await(
                    () -> phone.getWindow().getDecorView().getWindowVisibility() != View.VISIBLE,
                    "controller backgrounded before launcher recovery: " + reason);
        }
        String opened =
                DeviceTestSupport.shell(
                        runner,
                        "am start -W --display 0 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n dev.dexdroid/.MainActivity");
        check(!opened.contains("Error"), "launcher accepts recovery request: " + reason);
        try {
            await(
                    () -> {
                        boolean[] ready = {false};
                        main(
                                () ->
                                        ready[0] =
                                                !phone.isDestroyed()
                                                        && resumedPhone == phone
                                                        && phone.getWindow()
                                                                        .getDecorView()
                                                                        .getWindowVisibility()
                                                                == View.VISIBLE
                                                        && session.listener
                                                                == controllerListener(phone)
                                                        && session.id >= 0
                                                        && !session.busy
                                                        && session.monitorShellView() != null);
                        return ready[0];
                    },
                    "same visible bound controller launcher recovery: " + reason);
            verifyRecoveredInput(reason);
        } catch (Throwable failure) {
            try {
                captureRecoveryFailure(reason);
            } catch (Throwable captureFailure) {
                failure.addSuppressed(captureFailure);
            }
            throw failure;
        }
        check(
                phone.getDisplay().getDisplayId() == 0 && session.desktopRequested(),
                "launcher recovery retains phone placement and desktop request: " + reason);
    }

    private Object controllerListener(MainActivity activity) {
        try {
            java.lang.reflect.Field listener = MainActivity.class.getDeclaredField("uiListener");
            listener.setAccessible(true);
            return listener.get(activity);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Controller listener observation unavailable", error);
        }
    }

    private void verifyRecoveredInput(String reason) throws Exception {
        int display = session.id;
        captureRecoveryState(reason, "recovery-before-phone");
        phoneTap(description(phone.getWindow().getDecorView(), "Settings"), false);
        await(
                () -> phone.controllerMenu() != null && phone.controllerMenu().isShowing(),
                "recovered phone Settings responds to native contact: " + reason);
        View[] close = {null};
        main(
                () ->
                        close[0] =
                                phone.controllerMenu()
                                        .getWindow()
                                        .getDecorView()
                                        .findViewWithTag("phone-menu-close"));
        phoneTap(close[0], false);
        await(
                () -> phone.controllerMenu() == null,
                "recovered phone Close responds to native contact: " + reason);
        captureRecoveryState(reason, "recovery-after-phone");
        main(() -> session.setMonitorTouchEnabled(true));
        click("monitor-idle-apps");
        page("home");
        click("monitor-app-search");
        await(
                () ->
                        view("monitor-search-keyboard") != null
                                && view("monitor-search-keyboard").isShown(),
                "recovered monitor search responds to fresh native contact: " + reason);
        String query = "input test";
        for (char letter : query.toCharArray())
            click("monitor-key-" + (letter == ' ' ? "space" : String.valueOf(letter)));
        await(
                () ->
                        query.contentEquals(((EditText) view("monitor-app-search")).getText())
                                && view("app.dev.dexdroid") != null,
                "recovered monitor local keys filter fixture: " + reason);
        click("app.dev.dexdroid");
        page("closed");
        await(
                () -> !session.busy && session.activePackage.equals("dev.dexdroid"),
                "recovered fixture launched: " + reason);
        ComponentName fixture =
                new ComponentName(runner.getTargetContext(), InputTestActivity.class);
        int uid = runner.getTargetContext().getApplicationInfo().uid;
        int width = session.width(), height = session.height();
        long deadline = SystemClock.uptimeMillis() + 10000;
        int task;
        do {
            task = DeviceTestSupport.nativeInputTask(runner, display, fixture);
            if (task >= 0) break;
            Thread.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        check(task >= 0, "recovered fixture owns one current native task: " + reason);
        boolean settled;
        do {
            settled =
                    session.id == display
                            && !session.busy
                            && session.width() == width
                            && session.height() == height
                            && DeviceTestSupport.settledInputWindow(
                                    runner, display, task, fixture, uid, width, height);
            if (settled) break;
            Thread.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        check(settled, "recovered fixture native window settled before contact: " + reason);
        float[] point = physical(session.width() * .5f, session.height() * .5f);
        event(MotionEvent.ACTION_DOWN, point[0], point[1]);
        long contact = down;
        Thread.sleep(50);
        event(MotionEvent.ACTION_UP, point[0], point[1]);
        await(
                () ->
                        InputTestActivity.lastUpDownTime == contact
                                && Math.abs(InputTestActivity.lastUpX - width * .5f) < 2
                                && Math.abs(InputTestActivity.lastUpY - height * .5f) < 2,
                "recovered fixture completes exact native contact at mapped center: " + reason);
        check(
                session.id == display && session.desktopRequested() && session.sessionAwake(),
                "observable recovery retains desktop ownership: " + reason);
        captureRecoveryState(reason, "recovery-after-monitor");
    }

    private String controllerState(MainActivity activity) {
        if (activity == null) return "none";
        String owner;
        try {
            java.lang.reflect.Field listener = MainActivity.class.getDeclaredField("uiListener");
            listener.setAccessible(true);
            owner = Boolean.toString(session.listener == listener.get(activity));
        } catch (ReflectiveOperationException error) {
            owner = "unavailable:" + error.getClass().getSimpleName();
        }
        return "identity="
                + Integer.toHexString(System.identityHashCode(activity))
                + " destroyed="
                + activity.isDestroyed()
                + " finishing="
                + activity.isFinishing()
                + " task="
                + activity.getTaskId()
                + " display="
                + activity.getDisplay().getDisplayId()
                + " visibility="
                + activity.getWindow().getDecorView().getWindowVisibility()
                + " focus="
                + activity.hasWindowFocus()
                + " listenerOwner="
                + owner;
    }

    /** Private failure artifacts are captured before restore/teardown changes native focus. */
    private void captureRecoveryFailure(String reason) throws Exception {
        captureRecoveryState(reason, "recovery");
    }

    private void captureRecoveryState(String reason, String prefix) throws Exception {
        String[] state = {""};
        main(
                () ->
                        state[0] =
                                "reason="
                                        + reason
                                        + "\ncaptured="
                                        + controllerState(phone)
                                        + "\nresumed="
                                        + controllerState(resumedPhone)
                                        + "\nsameInstance="
                                        + (phone == resumedPhone)
                                        + "\nid="
                                        + session.id
                                        + " busy="
                                        + session.busy
                                        + " shell="
                                        + (session.monitorShellView() != null)
                                        + " requested="
                                        + session.desktopRequested()
                                        + " awake="
                                        + session.sessionAwake()
                                        + "\n");
        results.append("RECOVERY STATE ").append(state[0]);
        File folder =
                new File(
                        runner.getTargetContext().getExternalCacheDir(),
                        "compatibility-acceptance");
        if (!folder.isDirectory() && !folder.mkdirs())
            throw new java.io.IOException("Recovery diagnostic directory unavailable");
        writeRecoveryFile(folder, prefix + "-state.txt", state[0]);
        Exception captureFailure = null;
        for (String service : new String[] {"window", "input"}) {
            try {
                writeRecoveryFile(
                        folder,
                        prefix + "-" + service + ".txt",
                        DeviceTestSupport.shell(runner, "dumpsys -t 1 " + service));
            } catch (Exception error) {
                if (captureFailure == null) captureFailure = error;
                else captureFailure.addSuppressed(error);
            }
        }
        if (captureFailure != null) throw captureFailure;
    }

    private void writeRecoveryFile(File folder, String name, String contents) throws Exception {
        try (FileOutputStream stream = new FileOutputStream(new File(folder, name))) {
            stream.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private void stoppedControllerRecovery() throws Exception {
        phoneTap(description(phone.getWindow().getDecorView(), "Stop desktop"), false);
        await(
                () ->
                        session.id < 0
                                && !session.busy
                                && !session.desktopRequested()
                                && phone.hasWindowFocus(),
                "explicit Stop restores controller focus without pending startup");
        Thread.sleep(750);
        check(
                session.id < 0 && !session.desktopRequested() && !session.sessionAwake(),
                "controller focus restoration keeps explicit Stop stopped");
        reopenController("explicit Stop while controller visible", false);
    }

    String run() throws Exception {
        try {
            File diagnostics =
                    new File(
                            runner.getTargetContext().getExternalCacheDir(),
                            "compatibility-acceptance");
            for (String prefix :
                    new String[] {
                        "recovery",
                        "recovery-before-phone",
                        "recovery-after-phone",
                        "recovery-after-monitor"
                    })
                for (String suffix : new String[] {"state", "window", "input"}) {
                    String name = prefix + "-" + suffix + ".txt";
                    File previous = new File(diagnostics, name);
                    if (previous.exists() && !previous.delete())
                        throw new java.io.IOException(
                                "Previous recovery diagnostic not removed: " + name);
                }
            phone = DeviceTestSupport.startPhone(runner);
            main(
                    () -> {
                        resumedPhone = phone;
                        phone.getApplication()
                                .registerActivityLifecycleCallbacks(controllerLifecycle);
                    });
            CountDownLatch connected = new CountDownLatch(1);
            binding =
                    new ServiceConnection() {
                        public void onServiceConnected(ComponentName n, IBinder b) {
                            session = ((SessionService.LocalBinder) b).get();
                            connected.countDown();
                        }

                        public void onServiceDisconnected(ComponentName n) {}
                    };
            bound =
                    runner.getTargetContext()
                            .bindService(
                                    new Intent(runner.getTargetContext(), SessionService.class),
                                    binding,
                                    Context.BIND_AUTO_CREATE);
            check(connected.await(5, TimeUnit.SECONDS), "idle controller bound");
            await(() -> session.id >= 0 && !session.busy, "initial desktop ready");
            preferences = new HashMap<>(session.prefs.getAll());
            foregroundRejectionRecovery();
            missingHostRecovery();
            stoppedControllerRecovery();
            fresh();
            int idleDisplay = session.id;
            main(() -> session.key(KeyEvent.KEYCODE_BACK));
            page("closed");
            check(
                    session.id == idleDisplay && !session.hasDesktopApp(),
                    "idle Back preserves the unused desktop");
            main(
                    () -> {
                        session.setMonitorNavigationEnabled(true);
                        session.setMonitorTouchEnabled(true);
                    });
            for (int r = 0; r < 4; r++) {
                final int rotation = r;
                main(() -> session.rotate(rotation, false));
                await(
                        () -> !session.busy && session.rotation == rotation,
                        "idle rotation ready " + r);
                Thread.sleep(250);
                idle(true);
                centered();
                rendered();
                click("monitor-idle-apps");
                page("home");
                idle(false);
                check(!session.hasDesktopApp(), "opening Apps does not mark an app launched");
                click("monitor-close");
                page("closed");
                idle(true);
                pointerClick("monitor-idle-apps");
                page("home");
                check(session.pointerVisible, "phone pointer restores cursor on idle launcher");
                pointerClick("monitor-close");
                page("closed");
                idle(true);
                click("monitor-idle-apps");
                page("home");
                float[] outside = physical(4 * session.dpi / 160f, session.height() * .5f);
                DeviceTestSupport.usableMonitorOutside(session, outside);
                tap(outside[0], outside[1]);
                page("closed");
                idle(true);
                float[] blank = physical(session.width() * .25f, session.height() * .5f);
                tap(blank[0], blank[1]);
                page("closed");
                check(!session.hasDesktopApp(), "blank idle tap does not select an app");
                check(!session.pointerVisible, "physical idle tap hides phone pointer");
                main(
                        () -> {
                            session.cx = session.width() * .25f;
                            session.cy = session.height() * .5f;
                            session.click();
                            session.wheel(0, -2);
                        });
                page("closed");
                idle(true);
            }
            main(
                    () -> {
                        session.clearDiagnostics();
                        session.launch(
                                new ComponentName("dev.dexdroid", "dev.dexdroid.DoesNotExist"),
                                null);
                    });
            await(
                    () -> session.diagnosticText().contains("Desktop operation failed:"),
                    "rejected launch reports an error");
            check(!session.hasDesktopApp(), "rejected launch retains idle state");
            idle(true);
            click("monitor-idle-apps");
            page("home");
            click("app.dev.dexdroid");
            page("closed");
            await(
                    () -> !session.busy && session.hasDesktopApp(),
                    "successful monitor app selection clears first-launch state");
            idle(false);
            main(session::notifyUi);
            idle(false);
            for (int r = 0; r < 4; r++) {
                fresh();
                final int selected = r;
                main(
                        () ->
                                session.launch(
                                        new ComponentName(
                                                runner.getTargetContext(), InputTestActivity.class),
                                        selected));
                await(
                        () ->
                                !session.busy
                                        && session.hasDesktopApp()
                                        && session.rotation == selected,
                        "visible launch without opening monitor menus " + r);
                page("closed");
                idle(false);
                Thread.sleep(300);
                renderedApp(r);
                int touches = InputTestActivity.touchEvents;
                float[] target = physical(session.width() * .3f, session.height() * .7f);
                tap(target[0], target[1]);
                await(
                        () -> InputTestActivity.touchEvents > touches,
                        "direct app input before menus " + r);
                int backs = InputTestActivity.backEvents;
                main(() -> session.key(KeyEvent.KEYCODE_BACK));
                await(
                        () -> InputTestActivity.backEvents > backs,
                        "external app Back on current Android " + r);
                resumePhone(r);
            }
            fresh();
            idle(true);
            centered();
            main(() -> session.preview(1920, 1080, 240));
            await(() -> !session.busy && session.baseH == 1080, "idle Fit preview ready");
            Thread.sleep(250);
            idle(true);
            centered();
            tap(960, 45);
            page("closed");
            idle(true);
            click("monitor-idle-apps");
            page("home");
            tap(960, 45);
            page("closed");
            idle(true);
            main(() -> session.confirm(false));
            await(() -> !session.busy, "idle Fit preview reverted");
            restore();
            return results.toString();
        } catch (Throwable error) {
            throw new RuntimeException(results + "FAIL idle acceptance", error);
        } finally {
            if (phone != null)
                main(
                        () ->
                                phone.getApplication()
                                        .unregisterActivityLifecycleCallbacks(controllerLifecycle));
            restore();
            if (bound) runner.getTargetContext().unbindService(binding);
        }
    }
}
