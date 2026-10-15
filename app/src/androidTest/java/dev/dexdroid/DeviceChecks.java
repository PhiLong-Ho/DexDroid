package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** No test framework dependency; exercises the real non-root Shizuku path. */
public class DeviceChecks extends Instrumentation {
    private SessionService session;
    private java.util.Map<String, ?> savedPreferences;
    private String physicalDisplay;
    private boolean controllerOnly;
    private boolean uiOnly;
    private boolean monitorOnly;
    private boolean keyboardOnly;
    private boolean idleOnly;
    private boolean iconOnly;
    private boolean profilesOnly;
    private boolean childOnly;
    private boolean catalogOnly;
    private boolean protectedOnly;

    private void restorePreferences() {
        if (session == null || savedPreferences == null) return;
        android.content.SharedPreferences.Editor edit = session.prefs.edit().clear();
        for (java.util.Map.Entry<String, ?> item : savedPreferences.entrySet()) {
            Object value = item.getValue();
            String key = item.getKey();
            if (value instanceof Integer) edit.putInt(key, (Integer) value);
            else if (value instanceof Float) edit.putFloat(key, (Float) value);
            else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else if (value instanceof Long) edit.putLong(key, (Long) value);
            else if (value instanceof String) edit.putString(key, (String) value);
        }
        edit.commit();
    }

    private StringBuilder results = new StringBuilder();
    private long gestureDown;
    private long monitorDown;

    private void monitorGesture(int action, float[] xs, float[] ys, int[] ids) throws Exception {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) monitorDown = now;
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[xs.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[xs.length];
        for (int i = 0; i < xs.length; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = ids[i];
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = xs[i];
            coords[i].y = ys[i];
            coords[i].pressure = 1;
            coords[i].size = 1;
        }
        MotionEvent event =
                MotionEvent.obtain(
                        monitorDown,
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
        try {
            check(
                    DeviceTestSupport.injectOnDisplay(event, session.external.getDisplayId()),
                    "physical monitor event " + action);
        } finally {
            event.recycle();
        }
    }

    private void monitorTap(float u, float v) throws Exception {
        float[] p =
                Geometry.map(u, v, session.rotation, 1920, 1200, session.width(), session.height());
        float expectedX = u * session.width(), expectedY = v * session.height();
        if (session.monitorWindow().getCallback() instanceof MonitorActivity) {
            DeviceTestSupport.usableMonitorSwipe(session, p, p.clone());
            float[] mapped =
                    Geometry.unmap(
                            p[0],
                            p[1],
                            session.rotation,
                            1920,
                            1200,
                            session.width(),
                            session.height());
            if (mapped == null) throw new AssertionError("Tap must remain inside usable desktop");
            expectedX = mapped[0];
            expectedY = mapped[1];
        }
        monitorGesture(0, new float[] {p[0]}, new float[] {p[1]}, new int[] {7});
        monitorGesture(1, new float[] {p[0]}, new float[] {p[1]}, new int[] {7});
        long expectedDown = monitorDown;
        await(() -> InputTestActivity.lastUpDownTime == expectedDown, "monitor tap delivered");
        check(!session.pointerVisible, "monitor touch hides pointer");
        check(
                Math.abs(InputTestActivity.lastUpX - expectedX) < 2
                        && Math.abs(InputTestActivity.lastUpY - expectedY) < 2,
                "monitor tap coordinate alignment");
    }

    private void gesture(View pad, int action, float[] xs, float[] ys) {
        long now = SystemClock.uptimeMillis();
        if (action == 0) gestureDown = now;
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[xs.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[xs.length];
        for (int i = 0; i < xs.length; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = i;
            props[i].toolType = 1;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = xs[i];
            coords[i].y = ys[i];
            coords[i].pressure = 1;
        }
        MotionEvent e =
                MotionEvent.obtain(
                        gestureDown,
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
        runOnMainSync(() -> pad.dispatchTouchEvent(e));
        e.recycle();
    }

    private void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        results.append("PASS ").append(name).append('\n');
    }

    private void await(BooleanSupplier condition, String name) throws Exception {
        long end = SystemClock.uptimeMillis() + 10000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(50);
        check(condition.getAsBoolean(), name);
    }

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        physicalDisplay = args.getString("physicalDisplay");
        controllerOnly = "true".equals(args.getString("controllerOnly"));
        uiOnly = "true".equals(args.getString("uiOnly"));
        monitorOnly = "true".equals(args.getString("monitorOnly"));
        keyboardOnly = "true".equals(args.getString("keyboardOnly"));
        idleOnly = "true".equals(args.getString("idleOnly"));
        iconOnly = "true".equals(args.getString("iconOnly"));
        profilesOnly = "true".equals(args.getString("profilesOnly"));
        childOnly = "true".equals(args.getString("childOnly"));
        catalogOnly = "true".equals(args.getString("catalogOnly"));
        protectedOnly = "true".equals(args.getString("protectedOnly"));
        start();
    }

    @Override
    public void runOnMainSync(Runnable action) {
        Throwable[] failure = new Throwable[1];
        super.runOnMainSync(
                () -> {
                    try {
                        action.run();
                    } catch (Throwable error) {
                        failure[0] = error;
                    }
                });
        if (failure[0] != null)
            throw new AssertionError("Main-thread acceptance action failed", failure[0]);
    }

    @Override
    public void finish(int code, Bundle results) {
        try {
            DeviceTestSupport.stopDesktopBeforeExit(this);
        } catch (Exception error) {
            results.putString(
                    "stream",
                    results.getString("stream", "")
                            + "\nFAIL desktop teardown before instrumentation exit: "
                            + error);
            code = Activity.RESULT_CANCELED;
        }
        try {
            DeviceTestSupport.cancelInjectedMonitor(this);
        } catch (Exception error) {
            android.util.Log.w("DexDroidTest", "Fixture touch cleanup failed", error);
        }
        try {
            DeviceTestSupport.stopInputDriver();
        } catch (Exception error) {
            android.util.Log.w("DexDroidTest", "Test input driver cleanup failed", error);
        }
        super.finish(code, results);
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            // Connect accessibility before opening windows, rather than during the first
            // monitor touch while the input/IME focus transaction is still changing.
            getUiAutomation();
            DeviceTestSupport.cancelInjectedMonitor(this);
            if (!controllerOnly && !iconOnly) DeviceTestSupport.startInputDriver(this);
            if (protectedOnly) {
                result.putString("stream", new ProtectionAcceptance(this).run());
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (catalogOnly) {
                result.putString("stream", new CatalogAcceptance(this).run());
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (childOnly) {
                String output = new ChildLaunchAcceptance(this).run();
                result.putString("stream", output);
                finish(
                        output.contains("FAIL ") ? Activity.RESULT_CANCELED : Activity.RESULT_OK,
                        result);
                return;
            }
            if (profilesOnly) {
                result.putString("stream", new MonitorProfilesAcceptance(this).run());
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (iconOnly) {
                result.putString("stream", new IconAcceptance(this).run());
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (uiOnly) {
                result.putString("stream", new UiAcceptance(this).run());
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (monitorOnly) {
                result.putString("stream", new MonitorAcceptance(this).run());
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (idleOnly) {
                result.putString("stream", new IdleAcceptance(this).run());
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (keyboardOnly) {
                result.putString("stream", new MonitorSearchAcceptance(this).run());
                finish(Activity.RESULT_OK, result);
                return;
            }
            for (int r = 0; r < 4; r++) {
                int w = r % 2 == 0 ? 1920 : 1200, h = r % 2 == 0 ? 1200 : 1920;
                float[] center = Geometry.map(.5f, .5f, r, 1920, 1200, w, h);
                check(
                        Math.abs(center[0] - 960) < .01 && Math.abs(center[1] - 600) < .01,
                        "rotation " + r + " center");
                float[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
                for (float[] c : corners) {
                    float[] p = Geometry.map(c[0], c[1], r, 1920, 1200, w, h);
                    check(
                            (p[0] == 0 || p[0] == 1920) && (p[1] == 0 || p[1] == 1200),
                            "rotation " + r + " corner");
                }
            }
            check(
                    Geometry.recommendedDpi(1200) == 240 && Geometry.recommendedDpi(800) == 160,
                    "density recommendations");
            float[] fit = Geometry.map(0, 0, 0, 1920, 1200, 1920, 1080);
            check(fit[1] == 60, "16:9 Fit bars on 16:10 monitor");
            Activity activity = DeviceTestSupport.startPhone(this);
            CountDownLatch connected = new CountDownLatch(1);
            ServiceConnection connection =
                    new ServiceConnection() {
                        public void onServiceConnected(ComponentName n, IBinder b) {
                            session = ((SessionService.LocalBinder) b).get();
                            connected.countDown();
                        }

                        public void onServiceDisconnected(ComponentName n) {}
                    };
            getTargetContext()
                    .bindService(
                            new Intent(getTargetContext(), SessionService.class),
                            connection,
                            Context.BIND_AUTO_CREATE);
            check(connected.await(5, TimeUnit.SECONDS), "controller bind");
            savedPreferences = new java.util.HashMap<>(session.prefs.getAll());
            if (controllerOnly) {
                await(
                        () -> !session.needsRecovery(),
                        "Shizuku connects automatically without Start button");
                check(
                        session.external == null && session.id < 0,
                        "no desktop created on phone without monitor");
                ComponentName queued =
                        new ComponentName(getTargetContext(), InputTestActivity.class);
                runOnMainSync(() -> session.launch(queued, null));
                check(
                        session.diagnosticText().contains("App launch queued:"),
                        "app launch queued while monitor unavailable");
                View controllerPad =
                        activity.getWindow().getDecorView().findViewWithTag("touchpad");
                gesture(controllerPad, 0, new float[] {100}, new float[] {300});
                gesture(controllerPad, 1, new float[] {100}, new float[] {300});
                check(
                        session.diagnosticText().contains("Touchpad ignored: desktop=-1"),
                        "diagnostics explain unavailable desktop input");
                runOnMainSync(() -> session.pauseDesktop());
                session.clearDiagnostics();
                for (int i = 0; i < 300; i++) session.log("ring-check-" + i + ";");
                check(
                        !session.diagnosticText().contains("ring-check-0;")
                                && session.diagnosticText().contains("ring-check-299;"),
                        "diagnostic history is bounded");
                session.clearDiagnostics();
                check(
                        session.diagnosticText().contains("No events recorded."),
                        "diagnostics clear");
                runOnMainSync(() -> activity.finish());
                getTargetContext().unbindService(connection);
                result.putString("stream", results.toString());
                finish(Activity.RESULT_OK, result);
                return;
            }
            await(
                    () -> session.id >= 0 && !session.busy,
                    "automatic Shizuku virtual desktop creation");
            if (android.os.Build.VERSION.SDK_INT >= 36) {
                int[] physicalMonitor = {-1};
                runOnMainSync(
                        () ->
                                physicalMonitor[0] =
                                        session.monitorWindow()
                                                .getDecorView()
                                                .getDisplay()
                                                .getDisplayId());
                boolean permitsActivity =
                        getTargetContext()
                                .getSystemService(android.app.ActivityManager.class)
                                .isActivityStartAllowedOnDisplay(
                                        getTargetContext(),
                                        physicalMonitor[0],
                                        new Intent(getTargetContext(), MonitorActivity.class));
                check(
                        permitsActivity
                                == (session.monitorWindow().getCallback()
                                        instanceof MonitorActivity),
                        "monitor host matches public display launch eligibility");
                if (!permitsActivity)
                    check(
                            session.diagnosticText().contains("Presentation fallback"),
                            "Presentation fallback reports phone-task lifetime limitation");
            }
            int display = session.id;
            ComponentName inputTest =
                    new ComponentName(getTargetContext(), InputTestActivity.class);
            ActivityMonitor targetWindow =
                    addMonitor(InputTestActivity.class.getName(), null, false);
            runOnMainSync(() -> session.launch(inputTest, null));
            Thread.sleep(1000);
            runOnMainSync(
                    () -> {
                        session.prefs
                                .edit()
                                .putBoolean("swipeScroll", false)
                                .remove("swipeScroll.dev.dexdroid")
                                .remove("swipeScroll.eu.kanade.tachiyomi")
                                .remove("scrollSpeed.eu.kanade.tachiyomi")
                                .apply();
                        String original = session.activePackage;
                        session.activePackage = "eu.kanade.tachiyomi";
                        check(session.swipeScrolling(), "Tachiyomi defaults to native swipe");
                        session.setSwipeScrolling(false);
                        session.prefs
                                .edit()
                                .putFloat("scrollSpeed." + session.activePackage, .5f)
                                .apply();
                        session.saveAppSettings();
                        check(
                                !session.swipeScrolling()
                                        && session.prefs.getInt("app." + session.activePackage, -1)
                                                == session.rotation,
                                "Save app persists scroll mode and rotation");
                        session.activePackage = "dev.dexdroid";
                        check(!session.swipeScrolling(), "scroll mode isolated between apps");
                        session.activePackage = "eu.kanade.tachiyomi";
                        session.resetAppSettings();
                        check(
                                session.swipeScrolling()
                                        && session.scrollSpeed() == .2f
                                        && !session.prefs.contains("app." + session.activePackage),
                                "Reset app restores defaults and clears rotation");
                        session.activePackage = original;
                        session.notifyUi();
                    });
            await(() -> !session.busy, "app settings ready");
            for (int r = 0; r < 4; r++) {
                final int rotation = r;
                runOnMainSync(() -> session.rotate(rotation, true));
                await(() -> !session.busy && session.rotation == rotation, "resize rotation " + r);
                check(session.id == display, "task-preserving display ID " + r);
                await(
                        () -> {
                            android.view.Display d =
                                    ((android.hardware.display.DisplayManager)
                                                    getTargetContext()
                                                            .getSystemService(
                                                                    Context.DISPLAY_SERVICE))
                                            .getDisplay(session.id);
                            return d != null
                                    && d.getMode().getPhysicalWidth() == session.width()
                                    && d.getMode().getPhysicalHeight() == session.height();
                        },
                        "actual display dimensions " + r);
                Thread.sleep(500);
                if (physicalDisplay != null) {
                    try (android.os.ParcelFileDescriptor capture =
                                    getUiAutomation()
                                            .executeShellCommand(
                                                    "screencap -d "
                                                            + physicalDisplay
                                                            + " -p /sdcard/dexdroid-check-r"
                                                            + r
                                                            + ".png");
                            java.io.InputStream stream =
                                    new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                                            capture)) {
                        while (stream.read() != -1) {}
                    }
                }
                await(
                        () -> {
                            Activity target = targetWindow.getLastActivity();
                            return target != null
                                    && !target.isDestroyed()
                                    && target.getDisplay().getDisplayId() == session.id
                                    && target.getWindow().getDecorView().isShown()
                                    && target.getWindow().getDecorView().getWidth()
                                            == session.width()
                                    && target.getWindow().getDecorView().getHeight()
                                            == session.height();
                        },
                        "target app window matches resized desktop " + r);
                getUiAutomation().waitForIdle(500, 10000);
                int before = InputTestActivity.touchEvents;
                runOnMainSync(
                        () -> {
                            session.center();
                            session.click();
                        });
                await(() -> InputTestActivity.touchEvents > before, "targeted tap " + r);
                monitorTap(.5f, .5f);
                monitorTap(.1f, .1f);
                monitorTap(.9f, .1f);
                monitorTap(.1f, .9f);
                monitorTap(.9f, .9f);
                if (r == 1) monitorTap(.5f, 1);
                else if (r == 2 || r == 3) monitorTap(1, .5f);
                else monitorTap(0, .5f);
            }
            int pinch = InputTestActivity.pinchEvents;
            runOnMainSync(
                    () -> {
                        session.touch(0, new float[] {400}, new float[] {500});
                        session.touch(5 | 256, new float[] {400, 600}, new float[] {500, 500});
                        session.touch(2, new float[] {350, 650}, new float[] {500, 500});
                        session.touch(6 | 256, new float[] {350, 650}, new float[] {500, 500});
                        session.touch(1, new float[] {350}, new float[] {500});
                    });
            await(
                    () -> InputTestActivity.pinchEvents >= pinch + 3,
                    "native two-pointer zoom ordering");
            int wheel = InputTestActivity.wheelEvents;
            runOnMainSync(
                    () -> {
                        session.prefs
                                .edit()
                                .putFloat("scrollSpeed." + session.activePackage, .2f)
                                .apply();
                        session.wheel(1, 1);
                    });
            await(
                    () -> InputTestActivity.wheelEvents > wheel,
                    "horizontal and vertical wheel input");
            check(
                    Math.abs(InputTestActivity.lastWheelH - .2f) < .001f
                            && Math.abs(InputTestActivity.lastWheelV - .2f) < .001f,
                    "per-app scroll speed applied to both axes");
            check(session.pointerVisible, "touchpad wheel restores pointer");
            runOnMainSync(
                    () ->
                            session.prefs
                                    .edit()
                                    .putBoolean("swipeScroll", false)
                                    .putBoolean("reverseScroll", false)
                                    .apply());
            View pad = activity.getWindow().getDecorView().findViewWithTag("touchpad");
            check(pad != null, "phone touchpad exists");
            int tapsBefore = InputTestActivity.touchEvents;
            gesture(pad, 0, new float[] {300}, new float[] {300});
            gesture(pad, 1, new float[] {300}, new float[] {300});
            await(() -> InputTestActivity.touchEvents > tapsBefore, "phone tap recognizer");
            check(session.pointerVisible, "phone tap restores pointer");
            int pinchBefore = InputTestActivity.pinchEvents;
            gesture(pad, 0, new float[] {300}, new float[] {300});
            gesture(pad, 5 | 256, new float[] {300, 500}, new float[] {300, 300});
            Thread.sleep(25);
            gesture(pad, 2, new float[] {260, 540}, new float[] {300, 300});
            Thread.sleep(25);
            gesture(pad, 2, new float[] {220, 580}, new float[] {300, 300});
            gesture(pad, 6 | 256, new float[] {220, 580}, new float[] {300, 300});
            gesture(pad, 1, new float[] {220}, new float[] {300});
            await(() -> InputTestActivity.pinchEvents >= pinchBefore + 3, "phone pinch recognizer");
            int wheelBefore = InputTestActivity.wheelEvents;
            gesture(pad, 0, new float[] {300}, new float[] {300});
            gesture(pad, 5 | 256, new float[] {300, 500}, new float[] {300, 300});
            Thread.sleep(25);
            gesture(pad, 2, new float[] {300, 500}, new float[] {360, 360});
            gesture(pad, 6 | 256, new float[] {300, 500}, new float[] {360, 360});
            gesture(pad, 1, new float[] {300}, new float[] {360});
            await(() -> InputTestActivity.wheelEvents > wheelBefore, "phone scroll recognizer");
            int backBefore = InputTestActivity.backEvents;
            gesture(pad, 0, new float[] {100}, new float[] {300});
            gesture(pad, 5 | 256, new float[] {100, 200}, new float[] {300, 300});
            gesture(pad, 5 | 512, new float[] {100, 200, 300}, new float[] {300, 300, 300});
            gesture(pad, 2, new float[] {350, 450, 550}, new float[] {300, 300, 300});
            gesture(pad, 6 | 512, new float[] {350, 450, 550}, new float[] {300, 300, 300});
            gesture(pad, 6 | 256, new float[] {350, 450}, new float[] {300, 300});
            gesture(pad, 1, new float[] {350}, new float[] {300});
            await(() -> InputTestActivity.backEvents > backBefore, "three finger Back recognizer");
            await(
                    () ->
                            session.diagnosticText().contains("Three-finger Back recognized")
                                    && session.diagnosticText().contains("Key sent: KEYCODE_BACK"),
                    "three-finger recognition and Back dispatch logged");
            int rejectedBack = InputTestActivity.backEvents;
            gesture(pad, 0, new float[] {100}, new float[] {300});
            gesture(pad, 5 | 256, new float[] {100, 200}, new float[] {300, 300});
            gesture(pad, 5 | 512, new float[] {100, 200, 300}, new float[] {300, 300, 300});
            gesture(pad, 2, new float[] {110, 210, 310}, new float[] {300, 300, 300});
            gesture(pad, 6 | 512, new float[] {110, 210, 310}, new float[] {300, 300, 300});
            gesture(pad, 6 | 256, new float[] {110, 210}, new float[] {300, 300});
            gesture(pad, 1, new float[] {110}, new float[] {300});
            Thread.sleep(150);
            check(
                    InputTestActivity.backEvents == rejectedBack
                            && session.diagnosticText().contains("Three-finger Back rejected"),
                    "short three-finger movement rejected and explained");
            int nativePinch = InputTestActivity.pinchEvents;
            monitorGesture(0, new float[] {800}, new float[] {600}, new int[] {7});
            monitorGesture(
                    5 | 256, new float[] {800, 1120}, new float[] {600, 600}, new int[] {7, 11});
            monitorGesture(2, new float[] {700, 1220}, new float[] {600, 600}, new int[] {7, 11});
            monitorGesture(
                    6 | 256, new float[] {700, 1220}, new float[] {600, 600}, new int[] {7, 11});
            monitorGesture(1, new float[] {700}, new float[] {600}, new int[] {7});
            await(
                    () ->
                            InputTestActivity.pinchEvents >= nativePinch + 3
                                    && InputTestActivity.lastUpDownTime == monitorDown,
                    "physical monitor pinch forwarding and release");
            check(
                    InputTestActivity.lastPointer0 == 7 && InputTestActivity.lastPointer1 == 11,
                    "monitor pointer IDs preserved");
            check(InputTestActivity.lastDownTime == monitorDown, "monitor down time preserved");
            int arbitration = InputTestActivity.touchEvents;
            monitorGesture(0, new float[] {960}, new float[] {600}, new int[] {7});
            await(
                    () ->
                            InputTestActivity.touchEvents > arbitration
                                    && InputTestActivity.lastDownTime == monitorDown,
                    "monitor contact active");
            int monitorHeld = InputTestActivity.touchEvents;
            runOnMainSync(() -> session.click());
            Thread.sleep(200);
            check(
                    InputTestActivity.touchEvents == monitorHeld,
                    "phone clicks suppressed during monitor gesture");
            monitorGesture(1, new float[] {960}, new float[] {600}, new int[] {7});
            await(() -> InputTestActivity.touchEvents > monitorHeld, "monitor gesture ends");
            runOnMainSync(() -> session.preview(1920, 1080, 240));
            await(() -> !session.busy && session.baseH == 1080, "Fit resolution preview");
            int ignored = InputTestActivity.touchEvents;
            monitorGesture(0, new float[] {960}, new float[] {5}, new int[] {7});
            monitorGesture(1, new float[] {960}, new float[] {5}, new int[] {7});
            Thread.sleep(200);
            check(InputTestActivity.touchEvents == ignored, "Fit bars reject monitor touch");
            int canceled = InputTestActivity.cancelEvents;
            monitorGesture(0, new float[] {960}, new float[] {600}, new int[] {7});
            await(
                    () -> InputTestActivity.lastDownTime == monitorDown,
                    "Fit content contact delivered before moving outside");
            monitorGesture(2, new float[] {960}, new float[] {5}, new int[] {7});
            monitorGesture(1, new float[] {960}, new float[] {5}, new int[] {7});
            await(
                    () -> InputTestActivity.cancelEvents > canceled,
                    "monitor contact canceled on leaving content");
            runOnMainSync(() -> session.confirm(false));
            await(() -> !session.busy && session.baseH == 1200, "Fit preview reverted");
            runOnMainSync(() -> session.setMonitorTouchEnabled(false));
            int disabled = InputTestActivity.touchEvents;
            monitorGesture(0, new float[] {960}, new float[] {600}, new int[] {7});
            monitorGesture(1, new float[] {960}, new float[] {600}, new int[] {7});
            Thread.sleep(200);
            check(InputTestActivity.touchEvents == disabled, "monitor touch disable control");
            runOnMainSync(() -> session.setMonitorTouchEnabled(true));
            int originalW = session.baseW, originalH = session.baseH, originalDpi = session.dpi;
            runOnMainSync(() -> session.preview(1280, 800, 160));
            await(() -> !session.busy && session.baseW == 1280, "lower resolution preview");
            monitorTap(.5f, .5f);
            monitorTap(.1f, .1f);
            monitorTap(.9f, .9f);
            Thread.sleep(16000);
            await(
                    () ->
                            !session.busy
                                    && session.baseW == originalW
                                    && session.baseH == originalH
                                    && session.dpi == originalDpi,
                    "15 second automatic rollback");
            runOnMainSync(() -> session.preview(1280, 800, 160));
            await(() -> !session.busy && session.baseW == 1280, "second preview");
            runOnMainSync(() -> session.confirm(true));
            check(
                    session.prefs.getInt(session.monitorKey() + ".w", 0) == 1280,
                    "confirmed monitor profile persistence");
            runOnMainSync(() -> session.rememberApp(true));
            check(
                    session.prefs.getInt("app.dev.dexdroid", -1) == session.rotation,
                    "saved app orientation");
            MonitorActivity oldHost =
                    session.monitorWindow().getCallback() instanceof MonitorActivity
                            ? (MonitorActivity) session.monitorWindow().getCallback()
                            : null;
            if (oldHost != null) {
                Intent originalIntent = oldHost.getIntent();
                runOnMainSync(
                        () -> {
                            try {
                                Intent missingToken = new Intent(originalIntent);
                                missingToken.removeExtra("monitorToken");
                                oldHost.setIntent(missingToken);
                                check(
                                        !session.attachMonitor(
                                                oldHost,
                                                originalIntent.getIntExtra("generation", -1)),
                                        "monitor host without current session token is rejected");
                            } finally {
                                oldHost.setIntent(originalIntent);
                            }
                        });
            }
            runOnMainSync(
                    () -> {
                        session.rememberApp(false);
                        session.resetMonitor();
                        session.pauseDesktop();
                    });
            await(() -> session.id == -1, "Stop releases desktop");
            if (oldHost != null)
                check(oldHost.isFinishing(), "Stop closes independent monitor host");
            runOnMainSync(() -> session.launch(inputTest, null));
            await(
                    () -> session.id >= 0 && !session.busy,
                    "app launch automatically resumes desktop");
            check(
                    session.baseW == 1920 && session.baseH == 1200,
                    "current output recommendation after reset");
            if (oldHost != null) {
                int replacement = session.id;
                runOnMainSync(
                        () -> {
                            check(
                                    !session.attachMonitor(
                                            oldHost,
                                            oldHost.getIntent().getIntExtra("generation", -1)),
                                    "stale monitor host cannot attach to replacement session");
                            session.detachMonitor(oldHost);
                        });
                check(
                        session.id == replacement,
                        "stale host teardown cannot stop replacement session");
            }
            runOnMainSync(() -> session.launch(inputTest, null));
            Thread.sleep(600);
            runOnMainSync(
                    () -> {
                        session.rotate(0, true);
                        session.report("Device checks passed. Ready to experiment.");
                    });
            await(() -> !session.busy, "final landscape");
            int runningId = session.id;
            int backgroundBefore = InputTestActivity.touchEvents;
            monitorGesture(0, new float[] {960}, new float[] {600}, new int[] {7});
            await(
                    () ->
                            InputTestActivity.touchEvents > backgroundBefore
                                    && InputTestActivity.lastDownTime == monitorDown,
                    "monitor gesture before backgrounding");
            runOnMainSync(() -> activity.moveTaskToBack(true));
            Thread.sleep(800);
            check(
                    session.id == runningId
                            && session.monitorInputView() != null
                            && session.sessionAwake(),
                    "desktop persists while phone controller is backgrounded");
            monitorGesture(2, new float[] {1000}, new float[] {640}, new int[] {7});
            monitorGesture(1, new float[] {1000}, new float[] {640}, new int[] {7});
            await(
                    () -> InputTestActivity.lastUpDownTime == monitorDown,
                    "monitor touch works in background: "
                            + session.diagnosticText()
                            + " cancels="
                            + InputTestActivity.cancelEvents
                            + " lastUp="
                            + InputTestActivity.lastUpDownTime
                            + " expected="
                            + monitorDown);
            monitorTap(.1f, .1f);
            check(
                    session.id == runningId,
                    "fully injected fresh touch while phone is backgrounded");
            boolean independentHost =
                    session.monitorWindow().getCallback() instanceof MonitorActivity;
            runOnMainSync(() -> activity.finish());
            getTargetContext().unbindService(connection);
            if (independentHost || android.os.Build.VERSION.SDK_INT < 36) {
                Thread.sleep(800);
                check(
                        session.id == runningId
                                && session.monitorInputView() != null
                                && session.sessionAwake(),
                        "desktop persists with controller destroyed and no bindings");
                monitorTap(.1f, .1f);
                check(session.id == runningId, "fresh touch after controller destruction");
            } else {
                Thread.sleep(800);
                if (session.id == runningId) {
                    check(
                            session.monitorInputView() != null && session.sessionAwake(),
                            "OEM retains Presentation resources after controller destruction");
                    monitorTap(.1f, .1f);
                    check(
                            session.id == runningId,
                            "retained OEM Presentation delivers fresh touch");
                } else {
                    await(
                            () ->
                                    session.id < 0
                                            && session.monitorInputView() == null
                                            && !session.sessionAwake(),
                            "Presentation-only host releases desktop after phone task removal");
                    DeviceTestSupport.startPhone(this);
                    await(
                            () -> session.id >= 0 && !session.busy,
                            "Presentation fallback recovers on controller reopen");
                    runOnMainSync(() -> session.launch(inputTest, null));
                    await(
                            () -> !session.busy && session.hasDesktopApp(),
                            "fallback app launch after recovery");
                    Thread.sleep(500);
                    monitorTap(.1f, .1f);
                }
            }
            restorePreferences();
            result.putString("stream", results.toString());
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            restorePreferences();
            result.putString("stream", results + "FAIL " + android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
