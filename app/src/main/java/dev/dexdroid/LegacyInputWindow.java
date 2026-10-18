package dev.dexdroid;

import android.content.*;
import android.os.*;
import java.util.*;

/** Shell-worker-only task activation/readiness; never waits on either UI thread. */
@android.annotation.SuppressLint("PrivateApi")
final class LegacyInputWindow {
    interface Command {
        String run(String... args) throws Exception;
    }

    private final Context context;
    private final int display;
    private final Command command;
    private final Object tasks;
    private final Class<?> api;
    private int readyTask = -1;
    private String readyComponent = "", component = "";
    private boolean hasTask;
    private int readyUid = -1;

    LegacyInputWindow(Context context, int display, Command command) throws Exception {
        this.context = context;
        this.display = display;
        this.command = command;
        tasks =
                Class.forName("android.app.ActivityTaskManager")
                        .getMethod("getService")
                        .invoke(null);
        api = Class.forName("android.app.IActivityTaskManager");
    }

    void invalidate() {
        readyTask = -1;
    }

    String component() {
        return component;
    }

    boolean hasTask() {
        return hasTask;
    }

    boolean focused() throws Exception {
        return InputWindowState.ready(
                command.run("/system/bin/dumpsys", "-t", "1", "input"), display, readyUid, true);
    }

    private List<?> roots() throws Exception {
        return (List<?>) api.getMethod("getAllStackInfos").invoke(tasks);
    }

    boolean ready(boolean keyboard) throws Exception {
        Object chosen = null;
        boolean visibleChosen = false;
        for (Object root : roots()) {
            Class<?> type = root.getClass();
            if (type.getField("displayId").getInt(root) != display) continue;
            int[] ids = (int[]) type.getField("taskIds").get(root);
            if (ids == null || ids.length == 0 || type.getField("topActivity").get(root) == null)
                continue;
            boolean visible = false;
            try {
                visible = type.getField("visible").getBoolean(root);
            } catch (NoSuchFieldException optional) {
                /* Legacy ROM field is optional. */
            }
            if (chosen == null || (visible && !visibleChosen)) {
                chosen = root;
                visibleChosen = visible;
            }
        }
        hasTask = chosen != null;
        if (!hasTask) {
            component = "";
            return false;
        }
        Class<?> type = chosen.getClass();
        int[] ids = (int[]) type.getField("taskIds").get(chosen);
        int selected = ids[ids.length - 1];
        ComponentName top = (ComponentName) type.getField("topActivity").get(chosen);
        component = top.flattenToString();
        int uid = context.getPackageManager().getApplicationInfo(top.getPackageName(), 0).uid;
        boolean changed =
                selected != readyTask || !component.equals(readyComponent) || uid != readyUid;
        if (changed) invalidate();
        // Recents activation brings phone Home forward on legacy ROMs, even when
        // the selected task remains on this display. Launch paths own activation;
        // input readiness only focuses the task already discovered here.
        if (changed || keyboard) {
            api.getMethod("setFocusedTask", int.class).invoke(tasks, selected);
            if (!currentTarget(selected, component, uid)) {
                invalidate();
                return false;
            }
        }
        if (!changed) return true;
        long deadline = SystemClock.uptimeMillis() + 600;
        do {
            // Some legacy ROMs deliver display-targeted keys while FocusedWindows
            // still names the phone. Ownership + a live native window admit input;
            // focused() retains the stricter cancellation ownership check.
            boolean windowReady =
                    InputWindowState.ready(
                            command.run("/system/bin/dumpsys", "-t", "1", "input"),
                            display,
                            uid,
                            false);
            if (!currentTarget(selected, component, uid)) {
                invalidate();
                return false;
            }
            if (windowReady) {
                readyTask = selected;
                readyUid = uid;
                readyComponent = component;
                return true;
            }
            Thread.sleep(12);
        } while (SystemClock.uptimeMillis() < deadline);
        invalidate();
        return false;
    }

    private boolean currentTarget(int selected, String expectedComponent, int expectedUid)
            throws Exception {
        for (Object root : roots()) {
            Class<?> type = root.getClass();
            if (type.getField("displayId").getInt(root) != display) continue;
            int[] ids = (int[]) type.getField("taskIds").get(root);
            if (ids == null || ids.length == 0 || ids[ids.length - 1] != selected) continue;
            ComponentName top = (ComponentName) type.getField("topActivity").get(root);
            return top != null
                    && expectedComponent.equals(top.flattenToString())
                    && context.getPackageManager().getApplicationInfo(top.getPackageName(), 0).uid
                            == expectedUid;
        }
        hasTask = false;
        return false;
    }
}
