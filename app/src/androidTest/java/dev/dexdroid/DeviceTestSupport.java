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
