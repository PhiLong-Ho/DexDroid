package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import dev.dexdroid.fixture.LaunchParentActivity;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real legacy host/IPC/navigation regression without unrelated output-resize prerequisites. */
final class LegacyNavigationAcceptance {
    private final Instrumentation runner;
    private final StringBuilder results = new StringBuilder();
    private SessionService session;
    private MainActivity phone;
    private volatile Bundle state;

    LegacyNavigationAcceptance(Instrumentation runner) {
        this.runner = runner;
    }

    private void check(boolean value, String text) {
        if (!value) throw new AssertionError(text);
        results.append("PASS ").append(text).append('\n');
    }

    private void await(BooleanSupplier condition, String text) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 10000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < deadline) Thread.sleep(40);
        check(condition.getAsBoolean(), text);
    }

    private boolean phoneVisible() {
        boolean[] shown = {false};
        runner.runOnMainSync(
                () ->
                        shown[0] =
                                !phone.isDestroyed()
                                        && !phone.isFinishing()
                                        && phone.getWindow().getDecorView().getWindowVisibility()
                                                == View.VISIBLE);
        return shown[0];
    }

    private void showPhone() throws Exception {
        runner.runOnMainSync(
                () ->
                        runner.getTargetContext()
                                .startActivity(
                                        new Intent(runner.getTargetContext(), MainActivity.class)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        ActivityOptions.makeBasic()
                                                .setLaunchDisplayId(0)
                                                .toBundle()));
        await(this::phoneVisible, "phone controller foreground established");
    }

    private void tap() throws Exception {
        await(
                () ->
                        state != null
                                && state.getInt("display") == session.id
                                && state.getInt("targetWidth") > 0
                                && state.getInt("targetHeight") > 0,
                "native navigation frame ready");
        long deadline = SystemClock.uptimeMillis() + 5000;
        boolean windowReady;
        do {
            windowReady =
                    InputWindowState.ready(
                            DeviceTestSupport.shell(runner, "dumpsys input"),
                            session.id,
                            state.getInt("uid"),
                            false);
            if (!windowReady) Thread.sleep(40);
        } while (!windowReady && SystemClock.uptimeMillis() < deadline);
        check(windowReady, "native app window drawn and touchable before contact");
        runner.getUiAutomation().waitForIdle(250, 5000);
        Display.Mode mode = session.external.getMode();
        float[] point =
                Geometry.map(
                        state.getFloat("targetX") / session.width(),
                        state.getFloat("targetY") / session.height(),
                        session.rotation,
                        mode.getPhysicalWidth(),
                        mode.getPhysicalHeight(),
                        session.width(),
                        session.height());
        DeviceTestSupport.usableMonitorSwipe(session, point, point.clone());
        long down = SystemClock.uptimeMillis();
        for (int action : new int[] {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event =
                    MotionEvent.obtain(
                            down, SystemClock.uptimeMillis(), action, point[0], point[1], 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try {
                check(
                        DeviceTestSupport.injectOnDisplay(event, session.external.getDisplayId()),
                        "native host contact accepted");
            } finally {
                event.recycle();
            }
        }
        runner.getUiAutomation().waitForIdle(250, 5000);
    }

    private void page(String page) throws Exception {
        await(() -> state != null && page.equals(state.getString("page")), page + " resumed");
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerLegacyReceiver(
            Context context, BroadcastReceiver receiver, IntentFilter filter) {
        // Android 11/12 lack exported flags; this fixture intentionally uses a different UID.
        context.registerReceiver(receiver, filter);
    }

    String run() throws Exception {
        Context context = runner.getTargetContext();
        CountDownLatch ready = new CountDownLatch(1);
        ServiceConnection binding =
                new ServiceConnection() {
                    public void onServiceConnected(ComponentName n, IBinder binder) {
                        session = ((SessionService.LocalBinder) binder).get();
                        ready.countDown();
                    }

                    public void onServiceDisconnected(ComponentName n) {}
                };
        BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    public void onReceive(Context c, Intent intent) {
                        state = new Bundle(intent.getExtras());
                    }
                };
        boolean bound = false, registered = false;
        try {
            phone = DeviceTestSupport.startPhone(runner);
            bound =
                    context.bindService(
                            new Intent(context, SessionService.class),
                            binding,
                            Context.BIND_AUTO_CREATE);
            check(ready.await(5, TimeUnit.SECONDS), "navigation service bound");
            await(
                    () -> session.id >= 0 && !session.busy,
                    "initial desktop ready without output resize");
            check(!session.splitEnabled(), "isolated single-view legacy input path");
            IntentFilter filter = new IntentFilter(LaunchParentActivity.ACTION);
            if (Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            else registerLegacyReceiver(context, receiver, filter);
            registered = true;
            ComponentName app =
                    new ComponentName(
                            "dev.dexdroid.test", "dev.dexdroid.fixture.NavigationFixtureActivity");
            runner.runOnMainSync(() -> session.launch(app, session.rotation));
            page("navigation-parent");
            int display = session.id, task = state.getInt("task");
            showPhone();
            for (int cycle = 0; cycle < 3; cycle++) {
                tap();
                page("navigation-child");
                check(state.getInt("task") == task, "child remains in same native task");
                check(phoneVisible(), "first child contact preserves phone foreground");
                tap();
                page("navigation-parent");
                check(phoneVisible(), "native Up preserves phone foreground");
                tap();
                page("navigation-child");
                runner.runOnMainSync(() -> session.key(KeyEvent.KEYCODE_BACK));
                page("navigation-parent");
                check(phoneVisible(), "child remote Back preserves phone foreground");
            }
            runner.runOnMainSync(() -> phone.moveTaskToBack(true));
            await(() -> !phoneVisible(), "phone deliberately backgrounded");
            tap();
            page("navigation-child");
            check(!phoneVisible(), "monitor navigation preserves intentional phone backgrounding");
            showPhone();
            tap();
            page("navigation-parent");
            runner.runOnMainSync(() -> session.key(KeyEvent.KEYCODE_BACK));
            await(
                    () -> !session.hasDesktopApp() && !session.busy,
                    "native root Back clears app occupancy");
            check(
                    phoneVisible() && session.id == display,
                    "root Back retains phone controller and desktop");
            runner.runOnMainSync(() -> session.launch(app, session.rotation));
            await(
                    () ->
                            state != null
                                    && "navigation-parent".equals(state.getString("page"))
                                    && state.getInt("task") != task,
                    "relaunch creates a fresh native task");
            showPhone();
            tap();
            page("navigation-child");
            check(phoneVisible(), "new task first contact works after root exit and relaunch");
        } catch (Throwable error) {
            throw new RuntimeException(results + "FAIL legacy native navigation", error);
        } finally {
            try {
                if (session != null) {
                    runner.runOnMainSync(() -> session.stopDesktop());
                    await(() -> session.id < 0, "navigation fixture desktop stopped");
                }
            } finally {
                if (registered) context.unregisterReceiver(receiver);
                if (bound) context.unbindService(binding);
            }
        }
        return results.toString();
    }
}
