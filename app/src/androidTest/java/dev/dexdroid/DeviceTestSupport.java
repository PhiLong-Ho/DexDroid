package dev.dexdroid;

import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import rikka.shizuku.Shizuku;

/** Explicit phone display and captured shell results across Android test runtimes. */
final class DeviceTestSupport {
    private static volatile IBinder inputDriver;
    private static Shizuku.UserServiceArgs inputArgs;
    private static ServiceConnection inputConnection;
    private static final java.util.Map<Integer, MotionEvent> injectedContacts =
            new java.util.LinkedHashMap<>();

    static void startInputDriver(Instrumentation runner) throws Exception {
        if (inputDriver != null && inputDriver.isBinderAlive()) return;
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (!Shizuku.pingBinder() && SystemClock.uptimeMillis() < deadline) Thread.sleep(25);
        if (!Shizuku.pingBinder()
                || Shizuku.checkSelfPermission()
                        != android.content.pm.PackageManager.PERMISSION_GRANTED)
            throw new IllegalStateException("Test input requires running, authorized Shizuku");
        CountDownLatch ready = new CountDownLatch(1);
        inputConnection =
                new ServiceConnection() {
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        inputDriver = binder;
                        ready.countDown();
                    }

                    public void onServiceDisconnected(ComponentName name) {
                        inputDriver = null;
                    }
                };
        inputArgs =
                new Shizuku.UserServiceArgs(
                                new ComponentName(
                                        runner.getTargetContext(), "dev.dexdroid.TestInputDriver"))
                        .daemon(false)
                        .processNameSuffix("test-input")
                        .debuggable(true)
                        .version(BuildConfig.VERSION_CODE);
        Shizuku.bindUserService(inputArgs, inputConnection);
        if (!ready.await(10, TimeUnit.SECONDS))
            throw new IllegalStateException("Shell test input driver did not bind");
    }

    static synchronized boolean injectOnDisplay(InputEvent event, int display) throws Exception {
        IBinder driver = inputDriver;
        if (driver == null) throw new IllegalStateException("Shell test input driver unavailable");
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("dev.dexdroid.TestInputDriver");
            data.writeInt(display);
            data.writeInt(
                    event instanceof MotionEvent
                            ? (event.isFromSource(android.view.InputDevice.SOURCE_MOUSE) ? 3 : 1)
                            : 2);
            event.writeToParcel(data, 0);
            if (!driver.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0))
                throw new IllegalStateException("Shell test input transaction rejected");
            reply.readException();
            boolean accepted = reply.readInt() == 1;
            if (accepted
                    && event instanceof MotionEvent
                    && event.isFromSource(android.view.InputDevice.SOURCE_TOUCHSCREEN)) {
                MotionEvent motion = (MotionEvent) event;
                MotionEvent previous = injectedContacts.remove(display);
                if (previous != null) previous.recycle();
                int action = motion.getActionMasked();
                if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) {
                    int skip =
                            action == MotionEvent.ACTION_POINTER_UP ? motion.getActionIndex() : -1;
                    injectedContacts.put(
                            display,
                            contactCopy(motion, skip, MotionEvent.ACTION_MOVE, motion.getFlags()));
                }
            }
            return accepted;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static MotionEvent contactCopy(MotionEvent event, int skip, int action, int flags) {
        int count = event.getPointerCount() - (skip >= 0 ? 1 : 0);
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[count];
        for (int source = 0, target = 0; source < event.getPointerCount(); source++) {
            if (source == skip) continue;
            properties[target] = new MotionEvent.PointerProperties();
            coordinates[target] = new MotionEvent.PointerCoords();
            event.getPointerProperties(source, properties[target]);
            event.getPointerCoords(source, coordinates[target]);
            target++;
        }
        return MotionEvent.obtain(
                event.getDownTime(),
                SystemClock.uptimeMillis(),
                action,
                count,
                properties,
                coordinates,
                event.getMetaState(),
                event.getButtonState(),
                event.getXPrecision(),
                event.getYPrecision(),
                event.getDeviceId(),
                event.getEdgeFlags(),
                event.getSource(),
                flags);
    }

    static void stopDesktopBeforeExit(Instrumentation runner) throws Exception {
        // Instrumentation kills its target process on finish. Do not race an OEM
        // setDisplaySurface traversal with a dying TextureView producer.
        CountDownLatch connected = new CountDownLatch(1);
        SessionService[] service = {null};
        ServiceConnection binding =
                new ServiceConnection() {
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        service[0] = ((SessionService.LocalBinder) binder).get();
                        connected.countDown();
                    }

                    public void onServiceDisconnected(ComponentName name) {}
                };
        android.content.Context context = runner.getTargetContext();
        boolean bound =
                context.bindService(
                        new Intent(context, SessionService.class),
                        binding,
                        android.content.Context.BIND_AUTO_CREATE);
        if (!bound) throw new IllegalStateException("Desktop teardown bind rejected");
        try {
            if (!connected.await(5, TimeUnit.SECONDS))
                throw new IllegalStateException("Desktop teardown bind timed out");
            runner.runOnMainSync(() -> service[0].pauseDesktop());
            java.lang.reflect.Field field = SessionService.class.getDeclaredField("worker");
            field.setAccessible(true);
            java.util.concurrent.ExecutorService queue =
                    (java.util.concurrent.ExecutorService) field.get(service[0]);
            // Fence pending create/resize/stop work, including an operation which
            // has not registered its display with DisplayManager yet.
            queue.submit(() -> {}).get(10, TimeUnit.SECONDS);
            android.hardware.display.DisplayManager displays =
                    (android.hardware.display.DisplayManager)
                            context.getSystemService(android.content.Context.DISPLAY_SERVICE);
            long end = SystemClock.uptimeMillis() + 10000;
            boolean found;
            do {
                found = false;
                for (android.view.Display display : displays.getDisplays())
                    found |= display.getName().startsWith("DexDroid Desktop");
                if (found) Thread.sleep(40);
            } while (found && SystemClock.uptimeMillis() < end);
            if (found)
                throw new IllegalStateException("Virtual desktop still present at test exit");
            // A second main-thread turn drains teardown callbacks before exit.
            runner.runOnMainSync(() -> {});
        } finally {
            context.unbindService(binding);
        }
    }

    static void stopInputDriver() {
        if (inputArgs != null && inputConnection != null)
            Shizuku.unbindUserService(inputArgs, inputConnection, true);
        inputDriver = null;
        inputArgs = null;
        inputConnection = null;
    }

    static MainActivity startPhone(Instrumentation runner) {
        return (MainActivity)
                runner.startActivitySync(
                        new Intent(runner.getTargetContext(), MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());
    }

    static void usableMonitorSwipe(SessionService session, float[] start, float[] end) {
        if (android.os.Build.VERSION.SDK_INT < 31) return;
        android.view.View decor = session.monitorWindow().getDecorView();
        android.view.WindowInsets window = decor.getRootWindowInsets();
        if (window == null) throw new AssertionError("Monitor window insets unavailable");
        android.graphics.Insets bars =
                window.getInsetsIgnoringVisibility(
                        android.view.WindowInsets.Type.systemBars()
                                | android.view.WindowInsets.Type.displayCutout());
        float x = Math.max(bars.left + 1, Math.min(start[0], decor.getWidth() - bars.right - 1));
        float y = Math.max(bars.top + 1, Math.min(start[1], decor.getHeight() - bars.bottom - 1));
        end[0] += x - start[0];
        end[1] += y - start[1];
        start[0] = x;
        start[1] = y;
    }

    static void usableMonitorOutside(SessionService session, float[] point) {
        usableMonitorSwipe(session, point, point.clone());
        android.view.View panel = session.monitorShellView().findViewWithTag("monitor-panel");
        android.graphics.Rect bounds = new android.graphics.Rect();
        if (panel == null
                || !panel.getGlobalVisibleRect(bounds)
                || bounds.contains((int) point[0], (int) point[1]))
            throw new AssertionError(
                    "Outside-dismissal point must remain outside the measured panel");
    }

    static synchronized void cancelInjectedMonitor(Instrumentation runner) throws Exception {
        for (int display : new java.util.ArrayList<>(injectedContacts.keySet())) {
            MotionEvent active = injectedContacts.get(display);
            MotionEvent cancel =
                    contactCopy(
                            active,
                            -1,
                            MotionEvent.ACTION_CANCEL,
                            active.getFlags() | MotionEvent.FLAG_CANCELED);
            try {
                if (!injectOnDisplay(cancel, display))
                    throw new IllegalStateException(
                            "Injected contact cancellation rejected on " + display);
            } finally {
                cancel.recycle();
            }
        }
    }

    static int nativeInputTask(Instrumentation runner, int display, ComponentName component)
            throws Exception {
        int task = -1;
        for (DisplayTasks.Root root :
                DisplayTasks.parse(shell(runner, "am stack list"), component.flattenToString())) {
            if (root.display != display || !root.matches || !root.visible) continue;
            if (root.tasks != 1 || task >= 0) return -1;
            task = root.taskId;
        }
        return task;
    }

    /** Test prerequisite only: never focuses, injects, retries, or authorizes production input. */
    static boolean settledInputWindow(
            Instrumentation runner,
            int display,
            int task,
            ComponentName component,
            int uid,
            int width,
            int height)
            throws Exception {
        if (task < 0 || nativeInputTask(runner, display, component) != task) return false;
        String report = shell(runner, "dumpsys -t 1 input");
        if (report.contains("DUMP TIMEOUT") || report.contains("Permission Denial")) return false;
        boolean modern = android.os.Build.VERSION.SDK_INT >= 31;
        boolean admitted =
                modern
                        ? ModernInputWindowState.inspect(report, display, uid)
                                == ModernInputWindowState.State.READY
                        : InputWindowState.ready(report, display, uid, false);
        if (!admitted) return false;
        String legacyWindows = modern ? null : shell(runner, "dumpsys -t 1 window windows");
        String frame = "frame=[0,0][" + width + "," + height + "]";
        boolean matchingWindow = false;
        for (String line : report.split("\n")) {
            if (!line.contains("name=")
                    || !line.contains(component.flattenToString())
                    || !line.contains(", displayId=" + display + ",")
                    || !line.contains("ownerUid=" + uid + ",")
                    || !line.contains(frame)
                    || !line.contains("touchableRegion=[0,0][" + width + "," + height + "]"))
                continue;
            if (modern) {
                // The fullscreen diagnostic has no routing flags. A changed or
                // unknown configuration cannot establish this settled prerequisite.
                matchingWindow =
                        line.contains(" t" + task + "}")
                                && line.contains("inputConfig=0x0,")
                                && modernWindowUnobscured(report, line, display, width, height);
            } else {
                matchingWindow =
                        legacyWindowTaskMatches(
                                        legacyWindows,
                                        line,
                                        display,
                                        task,
                                        component.flattenToString(),
                                        component.flattenToShortString(),
                                        uid)
                                && line.contains("visible=true")
                                && line.contains("paused=false")
                                && line.contains("type=0x00000001")
                                && InputWindowState.ready(
                                        "  Display: " + display + "\n" + line + "\n",
                                        display,
                                        uid,
                                        false);
            }
            if (matchingWindow) break;
        }
        return matchingWindow && nativeInputTask(runner, display, component) == task;
    }

    private static boolean legacyWindowTaskMatches(
            String windows,
            String inputWindow,
            int display,
            int task,
            String component,
            String shortComponent,
            int uid) {
        if (windows == null
                || windows.contains("DUMP TIMEOUT")
                || windows.contains("Permission Denial")) return false;
        java.util.regex.Matcher identity =
                java.util.regex.Pattern.compile("Window\\{[^}]+\\}").matcher(inputWindow);
        if (!identity.find()) return false;
        java.util.regex.Matcher header =
                java.util.regex.Pattern.compile(
                                "(?m)^  Window #\\d+ "
                                        + java.util.regex.Pattern.quote(identity.group())
                                        + ":\\s*$")
                        .matcher(windows);
        if (!header.find()) return false;
        java.util.regex.Matcher next =
                java.util.regex.Pattern.compile("(?m)^  Window #\\d+ ").matcher(windows);
        int end = next.find(header.end()) ? next.start() : windows.length();
        String block = windows.substring(header.end(), end);
        if (!java.util.regex.Pattern.compile("\\bmDisplayId=" + display + "\\b")
                        .matcher(block)
                        .find()
                || !java.util.regex.Pattern.compile("\\bmOwnerUid=" + uid + "\\b")
                        .matcher(block)
                        .find()) return false;
        for (String line : block.split("\n")) {
            if (line.contains("mActivityRecord=ActivityRecord{")
                    && line.contains(" t" + task + "}")
                    && (line.contains(" " + component + " ")
                            || line.contains(" " + shortComponent + " "))) return true;
        }
        return false;
    }

    private static boolean modernWindowUnobscured(
            String report, String target, int display, int width, int height) {
        java.util.regex.Matcher section =
                java.util.regex.Pattern.compile("(?m)^  Display: " + display + "\\s*$")
                        .matcher(report);
        if (!section.find()) return false;
        java.util.regex.Matcher boundary =
                java.util.regex.Pattern.compile("(?m)^  \\S").matcher(report);
        if (!boundary.find(section.end())) return false;
        String windows = report.substring(section.end(), boundary.start());
        java.util.regex.Pattern row = java.util.regex.Pattern.compile("^\\s*(\\d+): name=.*");
        java.util.regex.Matcher targetRow = row.matcher(target);
        if (!targetRow.matches() || !windows.contains(target)) return false;
        int targetOrder = Integer.parseInt(targetRow.group(1));
        java.util.Set<Integer> orders = new java.util.HashSet<>();
        // Modern InputDispatcher explicitly numbers windows in top-to-bottom order.
        // Use those ordinals within this display, never unrelated dump line order.
        for (String line : windows.split("\n")) {
            java.util.regex.Matcher order = row.matcher(line);
            if (!order.matches()) continue;
            int index = Integer.parseInt(order.group(1));
            if (!orders.add(index)) return false;
            if (index >= targetOrder) continue;
            java.util.regex.Matcher config =
                    java.util.regex.Pattern.compile("\\binputConfig=([^,]+),").matcher(line);
            java.util.regex.Matcher alpha =
                    java.util.regex.Pattern.compile("\\balpha=([0-9.]+),").matcher(line);
            java.util.regex.Matcher frame =
                    java.util.regex.Pattern.compile(
                                    "\\bframe=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]")
                            .matcher(line);
            if (!config.find() || !alpha.find() || !frame.find()) return false;
            java.util.Set<String> flags =
                    new java.util.HashSet<>(
                            java.util.Arrays.asList(config.group(1).split("\\s*\\|\\s*")));
            if (flags.contains("NOT_VISIBLE") || Float.parseFloat(alpha.group(1)) == 0) continue;
            int left = Integer.parseInt(frame.group(1)), top = Integer.parseInt(frame.group(2));
            int right = Integer.parseInt(frame.group(3)), bottom = Integer.parseInt(frame.group(4));
            if (right <= left
                    || bottom <= top
                    || left >= width
                    || top >= height
                    || right <= 0
                    || bottom <= 0) continue;
            if (flags.contains("TRUSTED_OVERLAY")
                    && (flags.contains("SPY")
                            || flags.contains("NOT_TOUCHABLE")
                            || flags.contains("NO_INPUT_CHANNEL"))) continue;
            // This includes system transition snapshots with NO_INPUT_CHANNEL:
            // they can block untrusted injected touch despite having no touch region.
            return false;
        }
        return orders.contains(targetOrder);
    }

    static String shell(Instrumentation runner, String command) throws Exception {
        try (ParcelFileDescriptor descriptor =
                        runner.getUiAutomation().executeShellCommand(command);
                FileInputStream input = new FileInputStream(descriptor.getFileDescriptor());
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
