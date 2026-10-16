package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.*;
import dev.dexdroid.fixture.SplitProbeFixture;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Standalone native acceptance entry; deliberately excluded from normal product suites. */
public final class SplitProbeChecks {
    private final Instrumentation runner;

    SplitProbeChecks(Instrumentation runner) {
        this.runner = runner;
    }

    private final StringBuilder results = new StringBuilder();
    private final java.util.concurrent.atomic.AtomicReferenceArray<Bundle> states =
            new java.util.concurrent.atomic.AtomicReferenceArray<>(2);

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerLegacy(BroadcastReceiver receiver) {
        if (Build.VERSION.SDK_INT >= 33)
            throw new IllegalStateException("Legacy receiver on modern OS");
        runner.getTargetContext()
                .registerReceiver(receiver, new IntentFilter(SplitProbeFixture.ACTION));
    }

    private SplitProbeActivity probe;
    private int hostDisplay;
    private long down;

    private void check(boolean value, String label) {
        if (!value) throw new AssertionError(label + " | states=" + states.toString());
        results.append("PASS ").append(label).append('\n');
    }

    private void await(BooleanSupplier condition, String label) throws Exception {
        long end = SystemClock.uptimeMillis() + 12000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(35);
        check(condition.getAsBoolean(), label);
    }

    private float[] point(int index, String target) {
        float[] value = new float[2];
        runner.runOnMainSync(
                () -> {
                    View view = probe.panes[index].texture;
                    int[] location = new int[2];
                    view.getLocationOnScreen(location);
                    value[0] =
                            location[0]
                                    + states.get(index).getFloat(target + "X")
                                            * view.getWidth()
                                            / states.get(index).getInt("width");
                    value[1] =
                            location[1]
                                    + states.get(index).getFloat(target + "Y")
                                            * view.getHeight()
                                            / states.get(index).getInt("height");
                });
        return value;
    }

    private void mouse(int action, float[] point, int buttons, float vertical, float horizontal)
            throws Exception {
        pointer(action, point, buttons, vertical, horizontal, InputDevice.SOURCE_MOUSE);
    }

    private void pointer(
            int action, float[] point, int buttons, float vertical, float horizontal, int source)
            throws Exception {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_SCROLL) down = now;
        MotionEvent.PointerProperties property = new MotionEvent.PointerProperties();
        property.id = 0;
        property.toolType =
                source == InputDevice.SOURCE_MOUSE
                        ? MotionEvent.TOOL_TYPE_MOUSE
                        : MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords coord = new MotionEvent.PointerCoords();
        coord.x = point[0];
        coord.y = point[1];
        coord.pressure = buttons == 0 && source == InputDevice.SOURCE_MOUSE ? 0 : 1;
        coord.setAxisValue(MotionEvent.AXIS_VSCROLL, vertical);
        coord.setAxisValue(MotionEvent.AXIS_HSCROLL, horizontal);
        MotionEvent event =
                MotionEvent.obtain(
                        down,
                        now,
                        action,
                        1,
                        new MotionEvent.PointerProperties[] {property},
                        new MotionEvent.PointerCoords[] {coord},
                        0,
                        buttons,
                        1,
                        1,
                        -1,
                        0,
                        source,
                        0);
        try {
            check(
                    DeviceTestSupport.injectOnDisplay(event, hostDisplay),
                    "host pointer injection " + action + "/" + source);
        } finally {
            event.recycle();
        }
    }

    private void click(int index, String target) throws Exception {
        float[] p = point(index, target);
        mouse(MotionEvent.ACTION_DOWN, p, MotionEvent.BUTTON_PRIMARY, 0, 0);
        Thread.sleep(100);
        mouse(MotionEvent.ACTION_UP, p, 0, 0, 0);
        Thread.sleep(100);
    }

    private void key(int action, int code, int repeat, int meta) throws Exception {
        long now = SystemClock.uptimeMillis();
        KeyEvent event =
                new KeyEvent(
                        now,
                        now,
                        action,
                        code,
                        repeat,
                        meta,
                        -1,
                        0,
                        0,
                        InputDevice.SOURCE_KEYBOARD);
        check(
                DeviceTestSupport.injectOnDisplay(event, hostDisplay),
                "host key injection " + code + "/" + action);
    }

    private void type(int index, int code, int meta, String expected) throws Exception {
        click(index, "editor");
        key(KeyEvent.ACTION_DOWN, code, 0, meta);
        key(KeyEvent.ACTION_UP, code, 0, meta);
        await(
                () -> expected.equals(states.get(index).getString("text")),
                "native EditText typed in pane " + index);
    }

    private void capture(String name) throws Exception {
        Thread.sleep(300);
        Window window = probe.presentation.getWindow();
        Bitmap image =
                Bitmap.createBitmap(
                        window.getDecorView().getWidth(),
                        window.getDecorView().getHeight(),
                        Bitmap.Config.ARGB_8888);
        CountDownLatch copied = new CountDownLatch(1);
        int[] result = {-1};
        runner.runOnMainSync(
                () ->
                        PixelCopy.request(
                                window,
                                image,
                                value -> {
                                    result[0] = value;
                                    copied.countDown();
                                },
                                new Handler(Looper.getMainLooper())));
        check(
                copied.await(5, TimeUnit.SECONDS) && result[0] == PixelCopy.SUCCESS,
                "monitor PixelCopy " + name);
        File folder =
                new File(
                        runner.getTargetContext().getExternalFilesDir(null),
                        "split-probe-acceptance");
        if (!folder.isDirectory() && !folder.mkdirs())
            throw new IOException("Cannot create evidence folder");
        try (FileOutputStream output = new FileOutputStream(new File(folder, name + ".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally {
            image.recycle();
        }
    }

    public String run() {
        BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        states.set(
                                "Left".equals(intent.getStringExtra("pane")) ? 0 : 1,
                                intent.getExtras());
                    }
                };
        try {
            if (Build.VERSION.SDK_INT >= 33)
                runner.getTargetContext()
                        .registerReceiver(
                                receiver,
                                new IntentFilter(SplitProbeFixture.ACTION),
                                Context.RECEIVER_EXPORTED);
            else registerLegacy(receiver);
            DeviceTestSupport.startInputDriver(runner);
            String packageName = runner.getContext().getPackageName();
            probe =
                    (SplitProbeActivity)
                            runner.startActivitySync(
                                    new Intent(runner.getTargetContext(), SplitProbeActivity.class)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            .putExtra(
                                                    "left",
                                                    packageName
                                                            + "/dev.dexdroid.fixture.SplitProbeFixture$Left")
                                            .putExtra(
                                                    "right",
                                                    packageName
                                                            + "/dev.dexdroid.fixture.SplitProbeFixture$Right"),
                                    ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());
            await(
                    () ->
                            states.get(0) != null
                                    && states.get(1) != null
                                    && states.get(0).getInt("width") > 100
                                    && states.get(1).getInt("width") > 100,
                    "two native apps rendered and reported");
            hostDisplay = probe.presentation.getDisplay().getDisplayId();
            check(
                    states.get(0).getInt("display") != states.get(1).getInt("display"),
                    "independent app display IDs");
            check(
                    states.get(0).getInt("uid")
                            != runner.getTargetContext().getApplicationInfo().uid,
                    "fixture runs under different app UID");
            check(
                    states.get(0).getInt("display") == probe.panes[0].id
                            && states.get(1).getInt("display") == probe.panes[1].id,
                    "verified pane placement");
            capture("monitor-initial");
            type(0, KeyEvent.KEYCODE_L, KeyEvent.META_SHIFT_ON, "L");
            check(
                    states.get(1).getString("text").isEmpty(),
                    "left typing does not change right editor");
            type(1, KeyEvent.KEYCODE_R, 0, "r");
            check(
                    "L".equals(states.get(0).getString("text")),
                    "right typing does not change left editor");
            key(
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_F1,
                    3,
                    KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON);
            await(
                    () ->
                            states.get(1).getInt("keyCode") == KeyEvent.KEYCODE_F1
                                    && states.get(1).getInt("keyRepeat") == 3,
                    "hardware key repeat preserved");
            check(
                    (states.get(1).getInt("keyMeta")
                                    & (KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON))
                            == (KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON),
                    "Ctrl/Alt modifiers preserved");
            key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F1, 0, 0);
            Thread.sleep(100);
            int leftKeys = states.get(0).getInt("keyUps"),
                    rightKeys = states.get(1).getInt("keyUps");
            key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F2, 0, 0);
            await(
                    () -> states.get(1).getInt("keyCode") == KeyEvent.KEYCODE_F2,
                    "held key starts in right pane");
            runner.runOnMainSync(() -> probe.select(0));
            key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F2, 0, 0);
            await(
                    () -> states.get(1).getInt("keyUps") == rightKeys + 1,
                    "held key release stays with original pane");
            check(
                    states.get(0).getInt("keyUps") == leftKeys,
                    "selection does not split key sequence");
            for (int i = 0; i < 2; i++) {
                final int pane = i;
                int otherClicks = states.get(1 - i).getInt("clicks");
                click(i, "button");
                await(
                        () -> states.get(pane).getInt("clicks") == 1,
                        "semantic native mouse click pane " + i);
                check(
                        !states.get(pane).getBoolean("focusLostDuringContact"),
                        "native click retains focus during contact " + i);
                check(
                        states.get(1 - i).getInt("clicks") == otherClicks,
                        "no mouse click in other pane " + i);
                int before = states.get(i).getInt("wheels");
                mouse(MotionEvent.ACTION_SCROLL, point(i, "pad"), 0, -1.5f, .5f);
                await(
                        () -> states.get(pane).getInt("wheels") == before + 1,
                        "wheel routed to pane " + i);
                check(
                        states.get(i).getFloat("wheelV") == -1.5f
                                && states.get(i).getFloat("wheelH") == .5f
                                && states.get(i).getInt("wheelSource") == InputDevice.SOURCE_MOUSE,
                        "wheel axes and mouse source preserved " + i);
            }
            int rightMoves = states.get(1).getInt("moves"), rightUps = states.get(1).getInt("ups");
            mouse(MotionEvent.ACTION_DOWN, point(0, "pad"), MotionEvent.BUTTON_PRIMARY, 0, 0);
            mouse(MotionEvent.ACTION_MOVE, point(1, "pad"), MotionEvent.BUTTON_PRIMARY, 0, 0);
            mouse(MotionEvent.ACTION_UP, point(1, "pad"), 0, 0, 0);
            await(
                    () -> states.get(0).getInt("moves") > 0 && states.get(0).getInt("ups") > 0,
                    "drag crossing divider stays with left pane");
            check(
                    states.get(1).getInt("moves") == rightMoves
                            && states.get(1).getInt("ups") == rightUps,
                    "cross-divider drag never enters other app");
            check(
                    states.get(0).getInt("pointerSource") == InputDevice.SOURCE_MOUSE
                            && states.get(0).getInt("lastHeldButtons")
                                    == MotionEvent.BUTTON_PRIMARY,
                    "drag mouse source and primary button preserved");
            capture("monitor-equal");
            int leftWidth = states.get(0).getInt("width"),
                    rightWidth = states.get(1).getInt("width");
            runner.runOnMainSync(() -> probe.setRatio(.35f));
            await(
                    () ->
                            states.get(0).getInt("width") < leftWidth
                                    && states.get(1).getInt("width") > rightWidth,
                    "split changes actual app display dimensions");
            for (int i = 0; i < 2; i++) {
                final int pane = i;
                click(i, "button");
                await(
                        () -> states.get(pane).getInt("clicks") == 2,
                        "resized native click alignment " + i);
            }
            for (int i = 0; i < 2; i++) {
                final int pane = i;
                float[] p = point(i, "button");
                pointer(MotionEvent.ACTION_DOWN, p, 0, 0, 0, InputDevice.SOURCE_TOUCHSCREEN);
                Thread.sleep(100);
                pointer(MotionEvent.ACTION_UP, p, 0, 0, 0, InputDevice.SOURCE_TOUCHSCREEN);
                await(
                        () -> states.get(pane).getInt("clicks") == 3,
                        "native touchscreen click after resize " + i);
                check(
                        !states.get(pane).getBoolean("focusLostDuringContact"),
                        "touch contact retains focus after resize " + i);
            }
            capture("monitor-resized");
            String[] expected = {"L", "r"};
            for (int iteration = 0; iteration < 5; iteration++) {
                for (int i = 0; i < 2; i++) {
                    expected[i] += "a";
                    type(i, KeyEvent.KEYCODE_A, 0, expected[i]);
                }
            }
            check(
                    "Laaaaa".equals(states.get(0).getString("text"))
                            && "raaaaa".equals(states.get(1).getString("text")),
                    "ten alternating pane selections retain all typed characters");
            results.append("CONFIG Android ")
                    .append(Build.VERSION.RELEASE)
                    .append(" ")
                    .append(Build.MODEL)
                    .append(" | host ")
                    .append(hostDisplay)
                    .append(" | injected framework events, not physical peripherals\n");
        } catch (Throwable error) {
            results.append("FAIL ")
                    .append(android.util.Log.getStackTraceString(error))
                    .append('\n');
        } finally {
            try {
                if (probe != null) {
                    int left = probe.panes[0] == null ? -1 : probe.panes[0].id;
                    int right = probe.panes[1] == null ? -1 : probe.panes[1].id;
                    runner.runOnMainSync(() -> probe.stopExperiment(false));
                    DisplayManager manager =
                            runner.getTargetContext().getSystemService(DisplayManager.class);
                    await(
                            () ->
                                    manager.getDisplay(left) == null
                                            && manager.getDisplay(right) == null,
                            "Stop releases both app displays");
                    runner.runOnMainSync(() -> probe.finish());
                    runner.runOnMainSync(() -> {});
                }
            } catch (Throwable error) {
                results.append("FAIL cleanup ").append(error).append('\n');
            }
            DeviceTestSupport.stopInputDriver();
            runner.getTargetContext().unregisterReceiver(receiver);
        }
        return results.toString();
    }
}
