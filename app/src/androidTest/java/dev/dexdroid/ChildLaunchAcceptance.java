package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import dev.dexdroid.fixture.LaunchParentActivity;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real cross-UID Activity navigation; shell launches only the public parent. */
final class ChildLaunchAcceptance {
    private final Instrumentation runner;
    private final StringBuilder results = new StringBuilder();
    private SessionService session;
    private volatile Bundle state;
    private int fixtureUid;

    ChildLaunchAcceptance(Instrumentation runner) {
        this.runner = runner;
    }

    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        results.append("PASS ").append(message).append('\n');
    }

    private void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 10000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < deadline) Thread.sleep(40);
        check(condition.getAsBoolean(), message);
    }

    private void isolateFixtureTasks() throws Exception {
        String before = DeviceTestSupport.shell(runner, "am stack list");
        java.util.List<DisplayTasks.Root> captured = FixtureTaskIsolation.ownedRoots(before);
        for (DisplayTasks.Root owned : captured) {
            DisplayTasks.Root current = null;
            for (DisplayTasks.Root root :
                    FixtureTaskIsolation.ownedRoots(
                            DeviceTestSupport.shell(runner, "am stack list")))
                if (root.id == owned.id) current = root;
            check(
                    current != null
                            && current.taskId == owned.taskId
                            && current.display == owned.display,
                    "fixture-only root identity unchanged before removal " + owned.id);
            String removed = DeviceTestSupport.shell(runner, "am stack remove " + owned.id);
            check(removed.trim().isEmpty(), "fixture-only root removal accepted " + owned.id);
            results.append("SETUP removed fixture root=")
                    .append(owned.id)
                    .append(" task=")
                    .append(owned.taskId)
                    .append(" display=")
                    .append(owned.display)
                    .append('\n');
        }
        long deadline = SystemClock.uptimeMillis() + 5000;
        boolean absent;
        do {
            String after = DeviceTestSupport.shell(runner, "am stack list");
            absent = FixtureTaskIsolation.ownedRoots(after).isEmpty();
            for (DisplayTasks.Root owned : captured)
                absent &=
                        !java.util.regex.Pattern.compile("(?m)^\\s*taskId=" + owned.taskId + ":")
                                .matcher(after)
                                .find();
            if (absent) break;
            Thread.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        check(absent, "fresh child fixture established before first product launch");
    }

    private void tapCenter() throws Exception {
        View view = session.monitorWindow().getDecorView();
        long down = SystemClock.uptimeMillis();
        for (int action : new int[] {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event =
                    MotionEvent.obtain(
                            down,
                            SystemClock.uptimeMillis(),
                            action,
                            view.getWidth() / 2f,
                            view.getHeight() / 2f,
                            0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try {
                check(
                        DeviceTestSupport.injectOnDisplay(event, view.getDisplay().getDisplayId()),
                        "fixture physical input delivered " + action);
            } finally {
                event.recycle();
            }
            if (action == MotionEvent.ACTION_DOWN) Thread.sleep(50);
        }
    }

    private boolean page(String page) {
        Bundle current = state;
        return current != null && page.equals(current.getString("page"));
    }

    private void launch(ComponentName component, int rotation) throws Exception {
        // Clear the previous operation's observation. Reparenting before the new
        // success status races the shell's still-running placement verifier.
        runner.runOnMainSync(
                () -> {
                    session.status = "Acceptance awaiting launch completion";
                    session.launch(component, rotation);
                });
        await(
                () -> {
                    boolean[] placed = {false};
                    runner.runOnMainSync(
                            () ->
                                    placed[0] =
                                            !session.busy
                                                    && session.status.startsWith(
                                                            "Opened "
                                                                    + component.getPackageName()));
                    return placed[0];
                },
                "verified launch completed: " + component.flattenToShortString());
    }

    private void emptyRendered(int rotation) throws Exception {
        boolean[] empty = {false};
        long until = SystemClock.uptimeMillis() + 2000;
        do {
            runner.runOnMainSync(
                    () -> {
                        View root = session.monitorShellView();
                        View apps = root == null ? null : root.findViewWithTag("monitor-idle-apps");
                        empty[0] =
                                !session.hasDesktopApp()
                                        && session.activePackage.isEmpty()
                                        && apps != null
                                        && apps.isShown();
                    });
            if (!empty[0]) Thread.sleep(25);
        } while (!empty[0] && SystemClock.uptimeMillis() < until);
        check(empty[0], "idle Apps and empty state restored within two seconds " + rotation);
        check(!session.pointerVisible, "empty desktop pointer hidden " + rotation);
        Window window = session.monitorWindow();
        Bitmap image =
                Bitmap.createBitmap(
                        window.getDecorView().getWidth(),
                        window.getDecorView().getHeight(),
                        Bitmap.Config.ARGB_8888);
        try {
            boolean idle = false;
            long deadline = SystemClock.uptimeMillis() + 2000;
            do {
                CountDownLatch copied = new CountDownLatch(1);
                int[] status = {-1};
                runner.runOnMainSync(
                        () ->
                                PixelCopy.request(
                                        window,
                                        image,
                                        code -> {
                                            status[0] = code;
                                            copied.countDown();
                                        },
                                        new Handler(Looper.getMainLooper())));
                check(
                        copied.await(3, TimeUnit.SECONDS) && status[0] == PixelCopy.SUCCESS,
                        "captured handoff monitor pixels");
                float[] sample =
                        Geometry.map(
                                .25f,
                                .7f,
                                session.rotation,
                                image.getWidth(),
                                image.getHeight(),
                                session.width(),
                                session.height());
                int color = image.getPixel(Math.round(sample[0]), Math.round(sample[1]));
                idle = Math.abs(Color.red(color) - Color.blue(color)) < 8 && Color.red(color) < 60;
                if (!idle) Thread.sleep(30);
            } while (!idle && SystemClock.uptimeMillis() < deadline);
            File folder =
                    new File(
                            runner.getTargetContext().getExternalCacheDir(),
                            "child-launch-acceptance");
            check(folder.isDirectory() || folder.mkdirs(), "handoff capture directory");
            try (FileOutputStream stream =
                    new FileOutputStream(new File(folder, "handoff-idle-r" + rotation + ".png"))) {
                image.compress(Bitmap.CompressFormat.PNG, 100, stream);
            }
            check(idle, "idle pixels cover the departed app frame " + rotation);
        } finally {
            image.recycle();
        }
    }

    private void moveParentToPhone(ComponentName parent, int desktop, int task) throws Exception {
        DisplayTasks.Root selected = null;
        for (DisplayTasks.Root root :
                DisplayTasks.parse(
                        DeviceTestSupport.shell(runner, "am stack list"), parent.flattenToString()))
            if (root.display == desktop && root.matches && root.taskId == task) selected = root;
        check(selected != null && selected.tasks == 1, "isolated fixture root can move to phone");
        // Exercise the user's phone-launch path. Forced root migration can leave a
        // stale SurfaceFlinger parent on Samsung even when task metadata says 0.
        String output =
                DeviceTestSupport.shell(
                        runner, "am start -W --display 0 -n " + parent.flattenToString());
        check(!output.contains("Error"), "Android accepts ordinary phone app launch");
        await(
                () -> page("parent") && state.getInt("display") == 0,
                "phone owns fixture after movement");
        check(state.getInt("task") == task, "phone movement preserves fixture task and state");
    }

    // API 30-32 have no RECEIVER_EXPORTED flag. This test intentionally receives
    // lifecycle reports from its different-UID fixture, never production input.
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerLegacyReceiver(Context context, BroadcastReceiver receiver) {
        if (Build.VERSION.SDK_INT >= 33)
            throw new IllegalStateException("Legacy receiver on modern Android");
        context.registerReceiver(receiver, new IntentFilter(LaunchParentActivity.ACTION));
    }

    private void rendered(boolean reader, int rotation) throws Exception {
        rendered(reader, rotation, reader ? "reader" : "parent");
    }

    private void rendered(boolean reader, int rotation, String scenario) throws Exception {
        if (!scenario.equals("remaining-app"))
            await(
                    () ->
                            state != null
                                    && state.getInt("windowWidth") == session.width()
                                    && state.getInt("windowHeight") == session.height(),
                    "fixture has resized window before rendered/input checks "
                            + scenario
                            + " "
                            + rotation);
        // Match normal UI automation's settled-window boundary before testing
        // content/input. Android 11 does not give this desktop independent focus.
        runner.getUiAutomation().waitForIdle(500, 5000);
        Window window = session.monitorWindow();
        View decor = window.getDecorView();
        Bitmap image =
                Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
        boolean visible = false;
        try {
            long deadline = SystemClock.uptimeMillis() + 5000;
            do {
                CountDownLatch copied = new CountDownLatch(1);
                int[] status = {-1};
                runner.runOnMainSync(
                        () ->
                                PixelCopy.request(
                                        window,
                                        image,
                                        result -> {
                                            status[0] = result;
                                            copied.countDown();
                                        },
                                        new Handler(Looper.getMainLooper())));
                check(
                        copied.await(5, TimeUnit.SECONDS) && status[0] == PixelCopy.SUCCESS,
                        "captured actual monitor window");
                float[] sample =
                        Geometry.map(
                                .25f,
                                .7f,
                                session.rotation,
                                image.getWidth(),
                                image.getHeight(),
                                session.width(),
                                session.height());
                int color = image.getPixel(Math.round(sample[0]), Math.round(sample[1]));
                visible =
                        reader
                                ? Color.blue(color) > Color.red(color) + 20
                                        && Color.green(color)
                                                > Color.red(color)
                                                        + (scenario.equals("remaining-app")
                                                                ? 5
                                                                : 15)
                                : Color.red(color) > Color.blue(color) + 20;
                if (!visible) Thread.sleep(50);
            } while (!visible && SystemClock.uptimeMillis() < deadline);
            File folder =
                    new File(
                            runner.getTargetContext().getExternalCacheDir(),
                            "child-launch-acceptance");
            check(folder.isDirectory() || folder.mkdirs(), "child launch evidence directory");
            try (FileOutputStream stream =
                    new FileOutputStream(new File(folder, scenario + "-r" + rotation + ".png"))) {
                check(
                        image.compress(Bitmap.CompressFormat.PNG, 100, stream),
                        "saved monitor pixels");
            }
            if (Build.VERSION.SDK_INT == 30) {
                try (FileOutputStream stream =
                        new FileOutputStream(
                                new File(
                                        runner.getTargetContext().getCacheDir(),
                                        "child-" + scenario + "-r" + rotation + ".png"))) {
                    check(
                            image.compress(Bitmap.CompressFormat.PNG, 100, stream),
                            "saved legacy private-cache frame");
                }
            }
            if (!visible) {
                results.append("PLACEMENT AT RENDER ERROR\n")
                        .append(DeviceTestSupport.shell(runner, "am stack list"));
                for (String service :
                        new String[] {
                            "window windows", "activity activities", "SurfaceFlinger --list"
                        }) {
                    String dump = DeviceTestSupport.shell(runner, "dumpsys -t 5 " + service);
                    try (FileOutputStream stream =
                            new FileOutputStream(
                                    new File(
                                            folder,
                                            scenario
                                                    + "-r"
                                                    + rotation
                                                    + "-"
                                                    + service.split(" ")[0]
                                                    + ".txt"))) {
                        stream.write(dump.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
                String displays =
                        DeviceTestSupport.shell(runner, "dumpsys SurfaceFlinger --display-id");
                java.util.regex.Matcher physical =
                        java.util.regex.Pattern.compile("Display (\\d+) \\(HWC display 1\\)")
                                .matcher(displays);
                if (physical.find()) {
                    String id = physical.group(1);
                    if (!physical.find())
                        DeviceTestSupport.shell(
                                runner,
                                "screencap -p -d "
                                        + id
                                        + " "
                                        + folder.getAbsolutePath()
                                        + "/"
                                        + scenario
                                        + "-r"
                                        + rotation
                                        + "-physical.png");
                }
            }
            check(visible, "rendered " + scenario + " rotation " + rotation);
        } finally {
            image.recycle();
        }
    }

    String run() throws Exception {
        Context context = runner.getTargetContext();
        MainActivity phone = null;
        boolean bound = false, registered = false;
        CountDownLatch connected = new CountDownLatch(1);
        ServiceConnection binding =
                new ServiceConnection() {
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        session = ((SessionService.LocalBinder) binder).get();
                        connected.countDown();
                    }

                    public void onServiceDisconnected(ComponentName name) {}
                };
        BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    public void onReceive(Context ignored, Intent intent) {
                        if (intent.getIntExtra("uid", -1) == fixtureUid)
                            state = new Bundle(intent.getExtras());
                    }
                };
        try {
            fixtureUid = context.getPackageManager().getApplicationInfo("dev.dexdroid.test", 0).uid;
            check(
                    fixtureUid != android.os.Process.myUid(),
                    "fixture runs under a different application UID");
            ComponentName parent =
                    new ComponentName(
                            "dev.dexdroid.test", "dev.dexdroid.fixture.LaunchParentActivity");
            ComponentName reader =
                    new ComponentName(
                            "dev.dexdroid.test", "dev.dexdroid.fixture.LaunchReaderActivity");
            check(
                    !context.getPackageManager().getActivityInfo(reader, 0).exported,
                    "reader Activity is private to its own application");
            isolateFixtureTasks();
            if (Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(
                        receiver,
                        new IntentFilter(LaunchParentActivity.ACTION),
                        Context.RECEIVER_EXPORTED);
            else registerLegacyReceiver(context, receiver);
            registered = true;
            phone = DeviceTestSupport.startPhone(runner);
            bound =
                    context.bindService(
                            new Intent(context, SessionService.class),
                            binding,
                            Context.BIND_AUTO_CREATE);
            check(connected.await(5, TimeUnit.SECONDS), "child launch controller bound");
            await(() -> session.id >= 0 && !session.busy, "desktop ready");
            final MainActivity controller = phone;
            for (int rotation = 0; rotation < 4; rotation++) {
                final int selected = rotation;
                state = null;
                launch(parent, selected);
                await(
                        () -> page("parent") && !session.busy && session.rotation == selected,
                        "public parent launched " + rotation);
                int desktop = session.id;
                check(state.getInt("display") == desktop, "parent placed on desktop " + rotation);
                int task = state.getInt("task");
                int opens = state.getInt("count");
                rendered(false, rotation);
                if (rotation % 2 == 1) {
                    runner.runOnMainSync(() -> controller.moveTaskToBack(true));
                    Thread.sleep(200);
                }
                tapCenter();
                await(
                        () -> page("reader"),
                        "application opened its own private reader " + rotation);
                check(
                        state.getInt("display") == desktop,
                        "private reader stays on desktop "
                                + rotation
                                + ": actual="
                                + state.getInt("display")
                                + " expected="
                                + desktop);
                check(state.getInt("task") == task, "reader retains parent task " + rotation);
                check(
                        ("reader-state-" + (opens + 1)).equals(state.getString("payload")),
                        "reader intent state retained " + rotation);
                rendered(true, rotation);
                int taps = state.getInt("count");
                tapCenter();
                await(
                        () -> page("reader") && state.getInt("count") > taps,
                        "fresh physical-display input reaches reader " + rotation);
                check(
                        !state.getBoolean("focusLostDuringTouch"),
                        "reader keeps focus state through contact " + rotation);
                long dispatchDelay = state.getLong("downDispatchDelayMs", -1);
                check(dispatchDelay >= 0, "fixture records native DOWN-to-dispatch interval");
                results.append("METRIC physical_down_to_reader_dispatch_ms=")
                        .append(dispatchDelay)
                        .append(" rotation=")
                        .append(rotation)
                        .append('\n');
                state = null;
                runner.runOnMainSync(() -> session.key(KeyEvent.KEYCODE_BACK));
                await(() -> page("parent"), "Back returns to parent " + rotation);
                check(
                        state.getInt("display") == desktop && state.getInt("task") == task,
                        "Back preserves external display and original parent task " + rotation);
                check(
                        state.getInt("count") == opens + 1,
                        "parent navigation state survives " + rotation);
                check(
                        session.id == desktop && session.rotation == selected,
                        "desktop and selected rotation unchanged " + rotation);
                rendered(false, rotation);
                moveParentToPhone(parent, desktop, task);
                emptyRendered(rotation);
                check(state.getInt("display") == 0, "idle does not steal phone task " + rotation);
                // The restored launcher must remain interactive without controller focus.
                tapCenter();
                await(
                        () ->
                                "home".equals(session.monitorShellPage())
                                        && !session.monitorShellAnimating(),
                        "restored idle Apps opens " + rotation);
                runner.runOnMainSync(() -> session.key(KeyEvent.KEYCODE_BACK));
                await(
                        () ->
                                "closed".equals(session.monitorShellPage())
                                        && !session.monitorShellAnimating(),
                        "restored launcher Back closes " + rotation);
                launch(parent, selected);
                await(
                        () ->
                                page("parent")
                                        && state.getInt("display") == desktop
                                        && session.hasDesktopApp(),
                        "explicit relaunch restores desktop " + rotation);
                check(
                        state.getInt("count") == opens + 1,
                        "relaunch preserves parent state " + rotation);
                rendered(false, rotation);
            }
            runner.runOnMainSync(() -> session.preview(1920, 1080, 240));
            await(() -> !session.busy && session.baseH == 1080, "handoff Fit preview ready");
            rendered(false, session.rotation, "fit-parent");
            moveParentToPhone(parent, session.id, state.getInt("task"));
            emptyRendered(5);
            int fitRotation = session.rotation;
            runner.runOnMainSync(() -> session.launch(parent, fitRotation));
            check(
                    session.awaitingConfirmation() && !session.hasDesktopApp(),
                    "Fit preview queues relaunch without hiding idle");
            runner.runOnMainSync(() -> session.confirm(false));
            await(
                    () ->
                            page("parent")
                                    && state.getInt("display") == session.id
                                    && session.hasDesktopApp(),
                    "queued Fit relaunch restores desktop after Revert");
            rendered(false, fitRotation, "fit-reverted-relaunch");
            await(() -> !session.busy && session.baseH == 1200, "handoff Fit reverted");
            // Removing one of two desktop apps must not cover the remaining app.
            launch(new ComponentName(context, InputTestActivity.class), 0);
            await(() -> !session.busy && session.rotation == 0, "remaining-app desktop ready");
            Thread.sleep(250);
            state = null;
            launch(parent, 0);
            await(
                    () -> page("parent") && state.getInt("display") == session.id,
                    "second app placed above remaining app");
            moveParentToPhone(parent, session.id, state.getInt("task"));
            rendered(true, 0, "remaining-app");
            check(session.hasDesktopApp(), "remaining desktop app keeps idle hidden");
            // Isolate a fresh desktop to check a genuine app exit as well as reparenting.
            int previous = session.id;
            runner.runOnMainSync(
                    () -> {
                        session.pauseDesktop();
                        session.ensureDesktop();
                    });
            await(
                    () -> session.id >= 0 && session.id != previous && !session.busy,
                    "fresh exit-check desktop ready");
            state = null;
            launch(parent, 0);
            await(
                    () ->
                            page("parent")
                                    && state.getInt("display") == session.id
                                    && session.hasDesktopApp(),
                    "exit-check parent placed");
            rendered(false, 0, "exit-parent");
            runner.runOnMainSync(() -> session.key(KeyEvent.KEYCODE_BACK));
            await(() -> page("exit"), "fixture received Back and requested a real app exit");
            emptyRendered(6);
            return results.toString();
        } catch (Throwable error) {
            return results
                    + "STATE "
                    + state
                    + " desktop="
                    + (session == null
                            ? "unbound"
                            : session.id
                                    + " rotation="
                                    + session.rotation
                                    + " busy="
                                    + session.busy
                                    + " "
                                    + session.status)
                    + "\nFAIL "
                    + android.util.Log.getStackTraceString(error);
        } finally {
            if (registered) context.unregisterReceiver(receiver);
            if (session != null) {
                runner.runOnMainSync(
                        () -> {
                            session.confirm(false);
                            session.launch(
                                    new ComponentName(context, InputTestActivity.class), null);
                        });
            }
            if (bound) context.unbindService(binding);
        }
    }
}
