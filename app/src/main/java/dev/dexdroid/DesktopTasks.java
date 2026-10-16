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
