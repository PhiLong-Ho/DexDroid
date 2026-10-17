package dev.dexdroid;

import android.content.ComponentName;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;

/** Shell-only, event-driven occupancy of one virtual desktop. Never reparents tasks. */
@android.annotation.SuppressLint("PrivateApi") // Runs only in the authorized shell helper.
final class DesktopTasks implements AutoCloseable {
    interface Listener {
        void changed(boolean occupied, String component, String error);
    }

    private static final String DESCRIPTOR = "android.app.ITaskStackListener";
    private final HandlerThread thread = new HandlerThread("DexDroid-task-state");
    private final Handler handler;
    private final int display;
    private final Listener listener;
    private final Object manager, callback;
    private final Class<?> callbackType;
    private final Method roots;
    private final Set<Integer> events = new HashSet<>();
    private volatile boolean closed;
    private boolean pending;
    private Boolean last;
    private String lastError;
    private String lastComponent;

    DesktopTasks(int display, Listener listener) throws Exception {
        if (android.os.Process.myUid() != 2000)
            throw new SecurityException("Desktop task observation requires the authorized shell");
        this.display = display;
        this.listener = listener;
        manager =
                Class.forName("android.app.ActivityTaskManager")
                        .getMethod("getService")
                        .invoke(null);
        callbackType = Class.forName(DESCRIPTOR);
        Class<?> stub = Class.forName(DESCRIPTOR + "$Stub");
        for (String name :
                new String[] {
                    "onTaskStackChanged",
                    "onTaskCreated",
                    "onTaskRemoved",
                    "onTaskMovedToFront",
                    "onTaskDisplayChanged",
                    "onTaskFocusChanged",
                    "onTaskVisibilityChanged"
                }) {
            try {
                Field code = stub.getDeclaredField("TRANSACTION_" + name);
                code.setAccessible(true);
                events.add(code.getInt(null));
            } catch (NoSuchFieldException absentOnThisVersion) {
                // Stack-change notifications are available on every supported platform.
            }
        }
        if (events.isEmpty()) throw new IllegalStateException("Task events unavailable");
        Class<?> service = Class.forName("android.app.IActivityTaskManager");
        roots =
                service.getMethod(
                        Build.VERSION.SDK_INT >= 31 ? "getAllRootTaskInfos" : "getAllStackInfos");
        thread.start();
        handler = new Handler(thread.getLooper());
        Binder endpoint =
                new Binder() {
                    @Override
                    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                            throws RemoteException {
                        if (code == INTERFACE_TRANSACTION) {
                            if (reply != null) reply.writeString(DESCRIPTOR);
                            return true;
                        }
                        if (code >= FIRST_CALL_TRANSACTION && code <= LAST_CALL_TRANSACTION) {
                            data.enforceInterface(DESCRIPTOR);
                            // All platform listener methods are one-way void notifications. Their
                            // payload is deliberately unused; always query authoritative current
                            // state.
                            if (events.contains(code)) refresh();
                            return true;
                        }
                        return super.onTransact(code, data, reply, flags);
                    }
                };
        callback =
                Proxy.newProxyInstance(
                        callbackType.getClassLoader(),
                        new Class<?>[] {callbackType},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "asBinder":
                                    return endpoint;
                                case "hashCode":
                                    return System.identityHashCode(proxy);
                                case "equals":
                                    return proxy == args[0];
                                case "toString":
                                    return "DexDroid desktop task listener";
                                default:
                                    refresh();
                                    return null;
                            }
                        });
        try {
            service.getMethod("registerTaskStackListener", callbackType).invoke(manager, callback);
            refresh();
        } catch (Exception error) {
            closed = true;
            thread.quitSafely();
            throw error;
        }
    }

    // DisplayManager.release returns before ActivityTaskManager processes removal.
    // Stop workers must observe that handoff before restoring the phone task.
    static void awaitRemoval(int retiredDisplay) throws Exception {
        Object manager =
                Class.forName("android.app.ActivityTaskManager")
                        .getMethod("getService")
                        .invoke(null);
        Method snapshot =
                Class.forName("android.app.IActivityTaskManager")
                        .getMethod(
                                Build.VERSION.SDK_INT >= 31
                                        ? "getAllRootTaskInfos"
                                        : "getAllStackInfos");
        long deadline = SystemClock.uptimeMillis() + 2000;
        do {
            Object value = snapshot.invoke(manager);
            if (!(value instanceof List))
                throw new IllegalStateException("Invalid cleanup task snapshot");
            boolean retained = false;
            for (Object root : (List<?>) value)
                retained |= root.getClass().getField("displayId").getInt(root) == retiredDisplay;
            if (!retained) {
                awaitWindowTransactions();
                return;
            }
            if (SystemClock.uptimeMillis() >= deadline)
                throw new IllegalStateException(
                        "Android task handoff after display removal timed out");
            Thread.sleep(25);
        } while (true);
    }

    // Task metadata changes before WindowManager finishes the reparent animation
    // and commits its input windows. Focusing during that interval can strand the
    // phone at a black, unfocused window on JoyUI. Use the platform transaction
    // barrier instead of a timer or repeated foreground requests. Runs on shell IPC.
    private static void awaitWindowTransactions() throws Exception {
        Object window =
                Class.forName("android.view.WindowManagerGlobal")
                        .getMethod("getWindowManagerService")
                        .invoke(null);
        Class<?> type = Class.forName("android.view.IWindowManager");
        try {
            type.getMethod("syncInputTransactions", boolean.class).invoke(window, true);
        } catch (NoSuchMethodException android11) {
            type.getMethod("syncInputTransactions").invoke(window);
        }
    }

    // A normal AppTask.moveToFront may silently fail background-start policy after
    // display removal foregrounds its former app. Only focus an existing phone
    // controller task; never launch an Activity or trust an arbitrary task ID.
    static boolean focusController(int taskId) throws Exception {
        if (android.os.Process.myUid() != 2000)
            throw new SecurityException("Controller return requires the authorized shell");
        Object manager =
                Class.forName("android.app.ActivityTaskManager")
                        .getMethod("getService")
                        .invoke(null);
        Class<?> service = Class.forName("android.app.IActivityTaskManager");
        Object value =
                service.getMethod(
                                Build.VERSION.SDK_INT >= 31
                                        ? "getAllRootTaskInfos"
                                        : "getAllStackInfos")
                        .invoke(manager);
        if (!(value instanceof List))
            throw new IllegalStateException("Invalid controller task snapshot");
        ComponentName controller = new ComponentName("dev.dexdroid", "dev.dexdroid.MainActivity");
        for (Object root : (List<?>) value) {
            Class<?> type = root.getClass();
            if (type.getField("displayId").getInt(root) != 0
                    || !controller.equals(type.getField("topActivity").get(root))) continue;
            int[] tasks =
                    (int[])
                            type.getField(Build.VERSION.SDK_INT >= 31 ? "childTaskIds" : "taskIds")
                                    .get(root);
            String[] names =
                    (String[])
                            type.getField(
                                            Build.VERSION.SDK_INT >= 31
                                                    ? "childTaskNames"
                                                    : "taskNames")
                                    .get(root);
            if (tasks == null
                    || tasks.length != 1
                    || tasks[0] != taskId
                    || names == null
                    || names.length != 1
                    || !controller.equals(ComponentName.unflattenFromString(names[0]))) continue;
            service.getMethod(
                            "moveTaskToFront",
                            Class.forName("android.app.IApplicationThread"),
                            String.class,
                            int.class,
                            int.class,
                            Bundle.class)
                    .invoke(
                            manager,
                            null,
                            "com.android.shell",
                            taskId,
                            0,
                            android.app.ActivityOptions.makeBasic()
                                    .setLaunchDisplayId(0)
                                    .toBundle());
            awaitWindowTransactions();
            return true;
        }
        return false;
    }

    synchronized void refresh() {
        if (closed || pending) return;
        pending = true;
        handler.postDelayed(
                () -> {
                    synchronized (this) {
                        pending = false;
                    }
                    if (closed) return;
                    try {
                        Object value = roots.invoke(manager);
                        if (!(value instanceof List))
                            throw new IllegalStateException("Invalid task snapshot");
                        boolean occupied = false;
                        String component = "";
                        boolean visibleChosen = false;
                        for (Object root : (List<?>) value) {
                            Class<?> type = root.getClass();
                            if (type.getField("displayId").getInt(root) != display) continue;
                            ComponentName top =
                                    (ComponentName) type.getField("topActivity").get(root);
                            int[] tasks =
                                    (int[])
                                            type.getField(
                                                            Build.VERSION.SDK_INT >= 31
                                                                    ? "childTaskIds"
                                                                    : "taskIds")
                                                    .get(root);
                            occupied |= top != null && tasks != null && tasks.length > 0;
                            if (top != null && tasks != null && tasks.length > 0) {
                                boolean visible;
                                try {
                                    visible = type.getField("visible").getBoolean(root);
                                } catch (NoSuchFieldException absent) {
                                    visible = false;
                                }
                                if (component.isEmpty() || (visible && !visibleChosen)) {
                                    component = top.flattenToString();
                                    visibleChosen = visible;
                                }
                            }
                        }
                        if (!closed
                                && (last == null
                                        || last != occupied
                                        || lastError != null
                                        || !component.equals(lastComponent))) {
                            last = occupied;
                            lastComponent = component;
                            lastError = null;
                            listener.changed(occupied, component, "");
                        }
                    } catch (Throwable error) {
                        String message = error.toString();
                        if (!closed && !message.equals(lastError)) {
                            lastError = message;
                            listener.changed(false, "", message);
                        }
                    }
                },
                150);
    }

    @Override
    public void close() {
        closed = true;
        handler.removeCallbacksAndMessages(null);
        try {
            Class.forName("android.app.IActivityTaskManager")
                    .getMethod("unregisterTaskStackListener", callbackType)
                    .invoke(manager, callback);
        } catch (Exception error) {
            android.util.Log.w("DexDroid", "Task listener unregister failed", error);
        }
        thread.quitSafely();
    }
}
