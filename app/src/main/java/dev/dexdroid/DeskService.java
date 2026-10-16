package dev.dexdroid;

import android.content.Context;
import android.hardware.display.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.*;

/** Runs as Android shell through Shizuku, never as root. */
public class DeskService extends IDeskService.Stub {
    // Hidden framework flag, stable since Android 12; checked by DisplayManagerService.
    // Only the authorized shell creates this display using ADD_TRUSTED_DISPLAY.
    private static final int TRUSTED_DISPLAY = 1 << 10;
    // Framework per-display focus flag, available from Android 14.
    private static final int OWN_FOCUS = 1 << 14;
    private VirtualDisplay display;
    private Surface displaySurface;
    private final String outputName = "DexDroid Desktop " + android.os.Process.myPid();
    private DesktopTasks taskObserver;
    private LegacyInputWindow legacyInput;
    private IDesktopListener desktopListener;
    private boolean inputPending, blockedContact;
    private final Set<Long> blockedKeys = new LinkedHashSet<>();
    private ProtectedWindowObserver protectionObserver;
    private Object input;
    private Method inject, setId;
    private long down;
    private IBinder owner;
    private final IBinder.DeathRecipient ownerDeath = () -> stop();

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code == rikka.shizuku.ShizukuApiConstants.USER_SERVICE_TRANSACTION_destroy) {
            stop();
            System.exit(0);
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }

    private void init() throws Exception {
        if (input != null) return;
        Class<?> cls;
        try {
            cls = Class.forName("android.hardware.input.InputManagerGlobal");
        } catch (ClassNotFoundException legacy) {
            cls = Class.forName("android.hardware.input.InputManager");
        }
        input = cls.getMethod("getInstance").invoke(null);
        inject = cls.getMethod("injectInputEvent", InputEvent.class, int.class);
        setId = MotionEvent.class.getMethod("setDisplayId", int.class);
    }

    private void check(int id) {
        if (display == null || display.getDisplay().getDisplayId() != id)
            throw new IllegalStateException("Desktop disconnected");
    }

    // SUPPORTS_TOUCH (64) is a hidden Android 11 flag required by the shell display.
    @android.annotation.SuppressLint("WrongConstant")
    @Override
    public synchronized int create(
            Surface surface, int w, int h, int dpi, IBinder client, IDesktopListener listener) {
        try {
            stop();
            displaySurface = surface;
            owner = client;
            owner.linkToDeath(ownerDeath, 0);
            if (Looper.myLooper() == null) Looper.prepare();
            Class<?> cls = Class.forName("android.app.ActivityThread");
            Object thread = cls.getMethod("systemMain").invoke(null);
            Context context =
                    ((Context) cls.getMethod("getSystemContext").invoke(thread))
                            .createPackageContext(
                                    "com.android.shell", Context.CONTEXT_IGNORE_SECURITY);
            display =
                    ((DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE))
                            .createVirtualDisplay(
                                    outputName,
                                    w,
                                    h,
                                    dpi,
                                    surface,
                                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                                            | 64
                                            | (Build.VERSION.SDK_INT >= 31 ? TRUSTED_DISPLAY : 0)
                                            | (Build.VERSION.SDK_INT >= 34 ? OWN_FOCUS : 0));
            if (display == null) throw new IllegalStateException("Display creation rejected");
            init();
            int id = display.getDisplay().getDisplayId();
            desktopListener = listener;
            if (Build.VERSION.SDK_INT == 30)
                legacyInput = new LegacyInputWindow(context, id, this::command);
            protectionObserver =
                    new ProtectedWindowObserver(
                            id,
                            state -> {
                                try {
                                    listener.onProtectionState(id, state.ordinal());
                                } catch (RemoteException disconnected) {
                                    android.util.Log.w(
                                            "DexDroid", "Protection observer disconnected");
                                }
                            });
            taskObserver =
                    new DesktopTasks(
                            id,
                            (occupied, component, error) -> {
                                ProtectedWindowObserver observer = protectionObserver;
                                if (observer != null && error.isEmpty())
                                    observer.occupied(occupied);
                                try {
                                    listener.onTaskState(id, occupied, component, error);
                                } catch (RemoteException disconnected) {
                                    android.util.Log.w(
                                            "DexDroid",
                                            "Desktop observer disconnected",
                                            disconnected);
                                }
                            });
            return id;
        } catch (Throwable e) {
            stop();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void resize(Surface surface, int w, int h, int dpi) {
        Surface old = displaySurface;
        try {
            if (display == null) throw new IllegalStateException("Start desktop first");
            display.setSurface(null);
            if (Build.VERSION.SDK_INT == 30) awaitOutputDetached();
            display.resize(w, h, dpi);
            display.setSurface(surface);
            if (legacyInput != null) legacyInput.invalidate();
            displaySurface = surface;
            if (old != null && old != surface) old.release();
        } catch (Exception error) {
            throw new IllegalStateException("Output resize failed", error);
        } finally {
            if (surface != displaySurface) surface.release();
        }
    }

    private void awaitOutputDetached() throws Exception {
        long deadline = SystemClock.uptimeMillis() + 1500;
        do {
            String compositor = command("/system/bin/dumpsys", "-t", "1", "SurfaceFlinger");
            if (!compositor.contains("Composition Display State:")
                    || compositor.contains("DUMP TIMEOUT"))
                throw new IllegalStateException("Compositor state unavailable");
            if (!compositor.contains("[\"" + outputName + "\"]")) return;
            Thread.sleep(12);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new IllegalStateException("Compositor output did not detach");
    }

    @Override
    public synchronized void touch(int id, int action, float[] xs, float[] ys) {
        try {
            check(id);
            init();
            if (xs.length < 1 || xs.length > 2 || xs.length != ys.length)
                throw new IllegalArgumentException("Invalid contacts");
            long now = SystemClock.uptimeMillis();
            if (action == MotionEvent.ACTION_DOWN) down = now;
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
                coords[i].size = 1;
            }
            MotionEvent event =
                    MotionEvent.obtain(
                            down,
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
                setId.invoke(event, id);
                if (!(Boolean) inject.invoke(input, event, 0))
                    throw new IllegalStateException(
                            "Input rejected: check USB debugging security permission");
            } finally {
                event.recycle();
            }
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void scroll(int id, float x, float y, float horizontal, float vertical) {
        try {
            check(id);
            init();
            long now = SystemClock.uptimeMillis();
            MotionEvent.PointerProperties p = new MotionEvent.PointerProperties();
            p.id = 0;
            p.toolType = MotionEvent.TOOL_TYPE_MOUSE;
            MotionEvent.PointerCoords c = new MotionEvent.PointerCoords();
            c.x = x;
            c.y = y;
            c.setAxisValue(MotionEvent.AXIS_HSCROLL, horizontal);
            c.setAxisValue(MotionEvent.AXIS_VSCROLL, vertical);
            MotionEvent event =
                    MotionEvent.obtain(
                            now,
                            now,
                            MotionEvent.ACTION_SCROLL,
                            1,
                            new MotionEvent.PointerProperties[] {p},
                            new MotionEvent.PointerCoords[] {c},
                            0,
                            0,
                            1,
                            1,
                            0,
                            0,
                            InputDevice.SOURCE_MOUSE,
                            0);
            try {
                setId.invoke(event, id);
                if (!(Boolean) inject.invoke(input, event, 0))
                    throw new IllegalStateException("Scroll injection rejected");
            } finally {
                event.recycle();
            }
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void relayTouch(int id, MotionEvent event) {
        try {
            check(id);
            init();
            if (event == null
                    || event.getPointerCount() < 1
                    || event.getPointerCount() > 16
                    || !event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN))
                throw new IllegalArgumentException("Invalid monitor touch");
            setId.invoke(event, id);
            if (!(Boolean) inject.invoke(input, event, 0))
                throw new IllegalStateException("Monitor touch injection rejected");
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        } finally {
            if (event != null) event.recycle();
        }
    }

    private String command(String... arguments) throws Exception {
        java.lang.Process process = new ProcessBuilder(arguments).redirectErrorStream(true).start();
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append('\n');
        }
        if (process.waitFor() != 0 || result.toString().contains("Error:"))
            throw new IllegalStateException(result.toString());
        return result.toString();
    }

    @Override
    public synchronized void relayMotion(int id, MotionEvent event) {
        MotionEvent copy = null;
        try {
            check(id);
            init();
            if (event == null
                    || (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)
                            && !event.isFromSource(InputDevice.SOURCE_MOUSE)
                            && !event.isFromSource(InputDevice.SOURCE_TOUCHPAD)))
                throw new IllegalArgumentException("Unsupported input source");
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                blockedContact = !readyForInput(id, false);
            } else if (action == MotionEvent.ACTION_SCROLL && !readyForInput(id, false)) return;
            if (blockedContact) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                    blockedContact = false;
                return;
            }
            copy = InputEvents.rebase(event);
            setId.invoke(copy, id);
            if (!(Boolean) inject.invoke(input, copy, action == MotionEvent.ACTION_DOWN ? 1 : 0)) {
                if (legacyInput != null && action == MotionEvent.ACTION_DOWN) {
                    blockedContact = true;
                    legacyInput.invalidate();
                    pendingInput(id);
                    return;
                }
                throw new IllegalStateException("Pointer input rejected");
            }
        } catch (Exception error) {
            throw new IllegalStateException(error);
        } finally {
            if (copy != null) copy.recycle();
            if (event != null) event.recycle();
        }
    }

    private void pendingInput(int id) throws RemoteException {
        inputPending = true;
        if (desktopListener != null)
            desktopListener.onTaskState(
                    id, true, legacyInput.component(), "App is still opening. Try again.");
    }

    private boolean readyForInput(int id, boolean keyboard) throws Exception {
        if (legacyInput == null) return true;
        if (!legacyInput.ready(keyboard)) {
            if (legacyInput.hasTask()) pendingInput(id);
            else if (taskObserver != null) taskObserver.refresh();
            return false;
        }
        if (inputPending && desktopListener != null)
            desktopListener.onTaskState(id, true, legacyInput.component(), "");
        inputPending = false;
        return true;
    }

    @Override
    public synchronized void relayKey(int id, KeyEvent event) {
        try {
            check(id);
            init();
            if (event == null || !event.isFromSource(InputDevice.SOURCE_KEYBOARD))
                throw new IllegalArgumentException("Unsupported key source");
            long key = ((long) event.getDeviceId() << 32) | (event.getKeyCode() & 0xffffffffL);
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                blockedKeys.remove(key);
                if (!readyForInput(id, true)) {
                    if (blockedKeys.size() >= 64)
                        throw new IllegalStateException("Too many pending keys");
                    blockedKeys.add(key);
                    return;
                }
            }
            if (blockedKeys.contains(key)) {
                if (event.getAction() == KeyEvent.ACTION_UP) blockedKeys.remove(key);
                return;
            }
            // Android 11 cancels held native keys itself when global focus leaves
            // this display. Do not deliver a second cancelled UP from a phone menu.
            if (legacyInput != null
                    && event.getAction() == KeyEvent.ACTION_UP
                    && (event.getFlags() & KeyEvent.FLAG_CANCELED) != 0
                    && !legacyInput.focused()) return;
            KeyEvent copy = new KeyEvent(event);
            KeyEvent.class.getMethod("setDisplayId", int.class).invoke(copy, id);
            if (!(Boolean) inject.invoke(input, copy, 0))
                throw new IllegalStateException("Keyboard input rejected");
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    @Override
    public synchronized void closeApps(int id) {
        try {
            check(id);
            for (DisplayTasks.Root root : appStacks("dev.dexdroid/.InputTestActivity"))
                if (root.display == id)
                    command("/system/bin/am", "stack", "remove", Integer.toString(root.id));
            for (DisplayTasks.Root root : appStacks("dev.dexdroid/.InputTestActivity"))
                if (root.display == id && root.tasks > 0)
                    throw new IllegalStateException("App tasks remain on the selected display");
            if (taskObserver != null) taskObserver.refresh();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private List<DisplayTasks.Root> appStacks(String component) throws Exception {
        return DisplayTasks.parse(command("/system/bin/am", "stack", "list"), component);
    }

    // Shell-only Android 11 fallback; modern verified fullscreen avoids this removed API.
    @android.annotation.SuppressLint("PrivateApi")
    private void fullscreen(DisplayTasks.Root stack) throws Exception {
        // Modern am start --windowingMode 1 already establishes fullscreen. The legacy
        // forcing method was removed; only invoke it when placement is not confirmed.
        if (stack.fullscreen) return;
        Object manager =
                Class.forName("android.app.ActivityTaskManager")
                        .getMethod("getService")
                        .invoke(null);
        Class.forName("android.app.IActivityTaskManager")
                .getMethod("setTaskWindowingMode", int.class, int.class, boolean.class)
                .invoke(manager, stack.taskId, 1, true);
    }

    @Override
    public synchronized String launch(int id, String component) {
        try {
            check(id);
            if (!component.matches("[A-Za-z0-9._/$]+"))
                throw new IllegalArgumentException("Invalid app component");
            String result =
                    command(
                            "/system/bin/am",
                            "start",
                            "--display",
                            String.valueOf(id),
                            "--windowingMode",
                            "1",
                            "-n",
                            component);
            DisplayTasks.Root misplaced = null;
            for (DisplayTasks.Root stack : appStacks(component)) {
                if (!stack.matches) continue;
                if (stack.display == id) {
                    fullscreen(stack);
                    if (taskObserver != null) taskObserver.refresh();
                    return result;
                }
                if (misplaced == null || stack.visible) misplaced = stack;
            }
            if (misplaced == null)
                throw new IllegalStateException(
                        "Android did not place this app on the desktop. It may not support secondary displays.");
            if (misplaced.tasks != 1)
                throw new IllegalStateException(
                        "App is in a shared task stack; cannot safely move it to the desktop.");
            command(
                    "/system/bin/am",
                    "display",
                    "move-stack",
                    String.valueOf(misplaced.id),
                    String.valueOf(id));
            result +=
                    command(
                            "/system/bin/am",
                            "start",
                            "--display",
                            String.valueOf(id),
                            "--windowingMode",
                            "1",
                            "-n",
                            component);
            for (DisplayTasks.Root stack : appStacks(component))
                if (stack.matches && stack.display == id) {
                    fullscreen(stack);
                    if (taskObserver != null) taskObserver.refresh();
                    return result;
                }
            throw new IllegalStateException("Android rejected moving this app to the desktop.");
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void key(int id, int code) {
        try {
            check(id);
            init();
            long now = SystemClock.uptimeMillis();
            for (int action : new int[] {0, 1}) {
                KeyEvent event = new KeyEvent(now, now, action, code, 0);
                KeyEvent.class.getMethod("setDisplayId", int.class).invoke(event, id);
                if (!(Boolean) inject.invoke(input, event, 0))
                    throw new IllegalStateException("Key injection rejected");
            }
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void stop() {
        legacyInput = null;
        desktopListener = null;
        inputPending = blockedContact = false;
        blockedKeys.clear();
        if (protectionObserver != null) {
            protectionObserver.close();
            protectionObserver = null;
        }
        if (taskObserver != null) {
            taskObserver.close();
            taskObserver = null;
        }
        if (display != null) {
            if (Build.VERSION.SDK_INT >= 36) {
                int target = display.getDisplay().getDisplayId();
                try {
                    for (DisplayTasks.Root root : appStacks("dev.dexdroid/.InputTestActivity"))
                        if (root.display == target)
                            command("/system/bin/am", "stack", "remove", Integer.toString(root.id));
                    // Confirm removal before DisplayController loses its layout. Modern
                    // SystemUI can otherwise receive late compat info for a removed display.
                    for (DisplayTasks.Root root : appStacks("dev.dexdroid/.InputTestActivity"))
                        if (root.display == target)
                            throw new IllegalStateException("Desktop task cleanup incomplete");
                } catch (Exception error) {
                    android.util.Log.w(
                            "DexDroid", "Task cleanup before display release failed", error);
                }
            }
            display.release();
            display = null;
        }
        if (displaySurface != null) {
            displaySurface.release();
            displaySurface = null;
        }
        if (owner != null) {
            owner.unlinkToDeath(ownerDeath, 0);
            owner = null;
        }
    }
}
