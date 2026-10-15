package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real different-UID FLAG_SECURE transitions, not a simulated detection callback. */
final class ProtectionAcceptance {
    private final Instrumentation runner;
    private final StringBuilder result = new StringBuilder();
    private SessionService session;
    private MainActivity phone;
    private volatile Bundle fixture;
    private File folder;
    private int display;

    ProtectionAcceptance(Instrumentation runner) {
        this.runner = runner;
    }

    private void main(Runnable action) {
        runner.runOnMainSync(action);
    }

    private boolean ui(BooleanSupplier condition) {
        boolean[] value = {false};
        main(() -> value[0] = condition.getAsBoolean());
        return value[0];
    }

    private void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        result.append("PASS ").append(label).append('\n');
    }

    private void await(BooleanSupplier condition, String label) throws Exception {
        long end = SystemClock.uptimeMillis() + 12000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(40);
        check(condition.getAsBoolean(), label);
    }

    private View view(String tag) {
        return session.monitorShellView().findViewWithTag(tag);
    }

    private boolean shown(String tag) {
        View v = view(tag);
        return v != null && v.isShown();
    }

    private void tap(float x, float y) throws Exception {
        long down = SystemClock.uptimeMillis();
        for (int action : new int[] {0, 1}) {
            MotionEvent event =
                    MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try {
                check(
                        DeviceTestSupport.injectOnDisplay(event, display),
                        "physical help input " + action);
            } finally {
                event.recycle();
            }
            if (action == 0) Thread.sleep(50);
        }
        Thread.sleep(80);
    }

    private void click(String tag, boolean pointer) throws Exception {
        Rect rect = new Rect();
        await(
                () ->
                        ui(
                                () ->
                                        !session.monitorShellAnimating()
                                                && shown(tag)
                                                && view(tag).getGlobalVisibleRect(rect)
                                                && !rect.isEmpty()),
                "help target measured: " + tag);
        if (pointer) {
            main(
                    () -> {
                        View target = view(tag), current = target;
                        float x = target.getWidth() / 2f, y = target.getHeight() / 2f;
                        while (current != session.monitorShellView()) {
                            x += current.getLeft();
                            y += current.getTop();
                            View parent = (View) current.getParent();
                            x -= parent.getScrollX();
                            y -= parent.getScrollY();
                            current = parent;
                        }
                        session.cx = x;
                        session.cy = y;
                        session.click();
                    });
        } else tap(rect.exactCenterX(), rect.exactCenterY());
    }

    private void appTap(float nx, float ny) throws Exception {
        float[][] point = {null};
        main(
                () -> {
                    Display.Mode mode = session.external.getMode();
                    display = session.external.getDisplayId();
                    point[0] =
                            Geometry.map(
                                    nx,
                                    ny,
                                    session.rotation,
                                    mode.getPhysicalWidth(),
                                    mode.getPhysicalHeight(),
                                    session.width(),
                                    session.height());
                });
        tap(point[0][0], point[0][1]);
    }

    private void secure(boolean value) throws Exception {
        shell(
                "am broadcast -a dev.dexdroid.test.SET_PROTECTION -p dev.dexdroid.test --ei display "
                        + session.id
                        + " --ez secure "
                        + value);
        await(
                () ->
                        fixture != null
                                && fixture.getBoolean("secure") == value
                                && fixture.getInt("display") == session.id,
                "fixture changed actual window protection to " + value);
        try {
            await(
                    () -> session.protectedContent == value,
                    "shell detects same-window protection " + value);
        } catch (Throwable error) {
            String dump = DeviceTestSupport.shell(runner, "dumpsys -t 2 window visible-apps");
            result.append("PARSER STATE ")
                    .append(ProtectedWindows.read(new StringReader(dump), session.id))
                    .append('\n');
            for (String line : dump.split("\n")) {
                String field = line.trim();
                if (field.startsWith("Window #")) result.append("WINDOW\n");
                else if (field.startsWith("mDisplayId=")
                        || field.startsWith("fl=")
                        || field.startsWith("mHasSurface=")
                        || field.startsWith("isOnScreen=")
                        || field.contains("DUMP TIMEOUT")) result.append(field).append('\n');
            }
            throw error;
        }
    }

    private void shot(String name, boolean monitor) throws Exception {
        Thread.sleep(300);
        Bitmap bitmap;
        if (monitor) {
            Window window = session.monitorWindow();
            View decor = window.getDecorView();
            bitmap =
                    Bitmap.createBitmap(
                            decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
            CountDownLatch copied = new CountDownLatch(1);
            int[] code = {-1};
            main(
                    () ->
                            PixelCopy.request(
                                    window,
                                    bitmap,
                                    value -> {
                                        code[0] = value;
                                        copied.countDown();
                                    },
                                    new Handler(Looper.getMainLooper())));
            check(
                    copied.await(3, TimeUnit.SECONDS) && code[0] == PixelCopy.SUCCESS,
                    "help pixels captured");
        } else bitmap = runner.getUiAutomation().takeScreenshot();
        check(bitmap != null, "help image exists");
        try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            bitmap.recycle();
        }
    }

    private void guide(View root, String prefix) {
        TextView text = root.findViewWithTag(prefix + "-protected-guide");
        check(text != null && text.isShown(), prefix + " guide is visible");
        String content = text.getText().toString();
        check(content.equals(ProtectedScreenGuide.TEXT), prefix + " uses identical offline guide");
        for (String required :
                new String[] {
                    "chrome://flags/#incognito-screenshot",
                    "Enabled",
                    "Relaunch",
                    "screenshots and thumbnails",
                    "Default",
                    "not guaranteed",
                    "does not identify private browsing"
                }) check(content.contains(required), prefix + " instructions contain " + required);
    }

    private void shell(String command) throws Exception {
        try (ParcelFileDescriptor fd = runner.getUiAutomation().executeShellCommand(command);
                FileInputStream input = new FileInputStream(fd.getFileDescriptor())) {
            byte[] bytes = new byte[4096];
            while (input.read(bytes) >= 0) {}
        }
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void register(Context context, BroadcastReceiver receiver) {
        IntentFilter filter = new IntentFilter("dev.dexdroid.test.PROTECTION_STATE");
        if (Build.VERSION.SDK_INT >= 33)
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        else context.registerReceiver(receiver, filter);
    }

    String run() throws Exception {
        Context context = runner.getTargetContext();
        CountDownLatch bound = new CountDownLatch(1);
        ServiceConnection binding =
                new ServiceConnection() {
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        session = ((SessionService.LocalBinder) binder).get();
                        bound.countDown();
                    }

                    public void onServiceDisconnected(ComponentName name) {}
                };
        BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    public void onReceive(Context ignored, Intent intent) {
                        fixture = intent.getExtras();
                    }
                };
        boolean connected = false, registered = false;
        try {
            phone = DeviceTestSupport.startPhone(runner);
            connected =
                    context.bindService(
                            new Intent(context, SessionService.class),
                            binding,
                            Context.BIND_AUTO_CREATE);
            check(bound.await(5, TimeUnit.SECONDS), "protection controller bound");
            await(() -> session.id >= 0 && !session.busy, "protection desktop ready");
            register(context, receiver);
            registered = true;
            main(() -> display = session.external.getDisplayId());
            folder = new File(context.getExternalCacheDir(), "protection-acceptance");
            check(folder.isDirectory() || folder.mkdirs(), "help evidence directory");
            ComponentName component =
                    new ComponentName(
                            "dev.dexdroid.test", "dev.dexdroid.fixture.ProtectedFixtureActivity");
            for (int rotation = 0; rotation < 4; rotation++) {
                final int selected = rotation;
                main(
                        () -> {
                            session.status = "Acceptance awaiting protected fixture";
                            session.launch(component, selected);
                        });
                await(
                        () ->
                                !session.busy
                                        && session.rotation == selected
                                        && session.status.startsWith("Opened dev.dexdroid.test"),
                        "protected fixture placement " + rotation);
                await(
                        () ->
                                fixture != null
                                        && fixture.getInt("display") == session.id
                                        && !fixture.getBoolean("secure"),
                        "normal fixture present");
                check(!session.protectedContent, "ordinary app is not labeled protected");
                int task = fixture.getInt("task");
                secure(true);
                check(
                        fixture.getInt("task") == task,
                        "same task changes protection without task navigation");
                await(
                        () -> ui(() -> shown("monitor-protected-notice")),
                        "minimal protected notice appears");
                shot("monitor-notice-r" + rotation, true);
                int taps = fixture.getInt("taps");
                click("monitor-protected-help", rotation % 2 == 1);
                await(
                        () ->
                                ui(
                                        () ->
                                                session.monitorShellPage().equals("protected-help")
                                                        && !session.monitorShellAnimating()),
                        "monitor help opens");
                main(() -> guide(session.monitorShellView(), "monitor"));
                shot("monitor-guide-r" + rotation, true);
                appTap(.1f, .5f);
                await(
                        () ->
                                ui(
                                        () ->
                                                session.monitorShellPage().equals("closed")
                                                        && !session.monitorShellAnimating()),
                        "outside tap closes help");
                check(
                        fixture.getInt("taps") == taps,
                        "Help and outside dismissal never reach protected app");
                check(
                        ui(() -> !shown("monitor-protected-notice")),
                        "acknowledged notice stays dismissed");
                if (rotation == 0) {
                    main(
                            () ->
                                    phone.getWindow()
                                            .getDecorView()
                                            .findViewWithTag("status-protected-help")
                                            .performClick());
                    await(
                            () -> ui(() -> phone.controllerMenu() != null),
                            "phone status opens help");
                    main(() -> guide(phone.controllerMenu().getWindow().getDecorView(), "phone"));
                    shot("phone-guide", false);
                    main(
                            () ->
                                    ((ScrollView)
                                                    phone.controllerMenu()
                                                            .getWindow()
                                                            .getDecorView()
                                                            .findViewWithTag("phone-menu-scroll"))
                                            .fullScroll(View.FOCUS_DOWN));
                    shot("phone-guide-bottom", false);
                    main(
                            () ->
                                    phone.controllerMenu()
                                            .getWindow()
                                            .getDecorView()
                                            .findViewWithTag("phone-menu-close")
                                            .performClick());
                }
                secure(false);
                secure(true);
                await(
                        () -> ui(() -> shown("monitor-protected-notice")),
                        "new protected episode restores notice");
                click("monitor-protected-dismiss", false);
                Thread.sleep(2300);
                check(
                        session.protectedContent && ui(() -> !shown("monitor-protected-notice")),
                        "dismissal survives next observation");
                secure(false);
            }
            fixture = null;
            shell(
                    "am start --display 0 --activity-multiple-task -n "
                            + component.flattenToString()
                            + " --ez secure true");
            await(
                    () ->
                            fixture != null
                                    && fixture.getInt("display") == 0
                                    && fixture.getBoolean("secure"),
                    "protected phone-only window is present");
            int phoneTask = fixture.getInt("task");
            Thread.sleep(2300);
            check(!session.protectedContent, "phone-only protection never triggers monitor notice");
            shell("am stack remove " + phoneTask);
            // startActivitySync waits for a new Activity; this singleTask controller
            // already exists. Restore it through the normal launcher instead.
            shell("am start --display 0 -n dev.dexdroid/.MainActivity");
            await(
                    () -> ui(() -> phone.hasWindowFocus()),
                    "existing controller restored after phone-only fixture");
            main(() -> session.pauseDesktop());
            check(!session.protectedContent, "Stop clears protection state");
            Thread.sleep(2300);
            check(!session.protectedContent, "stopped session rejects late inspection");
            main(() -> session.ensureDesktop());
            await(() -> session.id >= 0 && !session.busy, "desktop restored after protected test");
            return result.toString();
        } catch (Throwable problem) {
            throw new RuntimeException(result + "FAIL protected screen acceptance", problem);
        } finally {
            if (registered) context.unregisterReceiver(receiver);
            if (session != null)
                main(
                        () -> {
                            if (phone.controllerMenu() != null) phone.controllerMenu().dismiss();
                            session.pauseDesktop();
                        });
            if (connected) context.unbindService(binding);
        }
    }
}
