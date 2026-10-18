package dev.dexdroid;

import android.content.*;
import java.util.*;

/** Reactive shell-worker diagnosis only after a rejected initial DOWN; never focuses or waits. */
@android.annotation.SuppressLint("PrivateApi")
final class ModernInputWindow {
    static final class Target {
        final int task, uid;
        final String component;

        Target(int task, int uid, String component) {
            this.task = task;
            this.uid = uid;
            this.component = component;
        }

        boolean same(Target other) {
            return other != null
                    && task == other.task
                    && uid == other.uid
                    && component.equals(other.component);
        }
    }

    interface Source {
        Target target() throws Exception;

        String windows() throws Exception;
    }

    private final int display;
    private final Source source;

    ModernInputWindow(int display, Source source) {
        this.display = display;
        this.source = source;
    }

    ModernInputWindow(Context context, int display, LegacyInputWindow.Command command)
            throws Exception {
        this.display = display;
        Object manager =
                Class.forName("android.app.ActivityTaskManager")
                        .getMethod("getService")
                        .invoke(null);
        Class<?> api = Class.forName("android.app.IActivityTaskManager");
        source =
                new Source() {
                    @Override
                    public Target target() throws Exception {
                        Object chosen = null;
                        boolean visibleChosen = false;
                        for (Object root :
                                (List<?>) api.getMethod("getAllRootTaskInfos").invoke(manager)) {
                            Class<?> type = root.getClass();
                            if (type.getField("displayId").getInt(root) != display) continue;
                            int[] ids = (int[]) type.getField("childTaskIds").get(root);
                            if (ids == null
                                    || ids.length == 0
                                    || type.getField("topActivity").get(root) == null) continue;
                            boolean visible = type.getField("visible").getBoolean(root);
                            if (chosen == null || (visible && !visibleChosen)) {
                                chosen = root;
                                visibleChosen = visible;
                            }
                        }
                        if (chosen == null) return null;
                        Class<?> type = chosen.getClass();
                        int[] ids = (int[]) type.getField("childTaskIds").get(chosen);
                        ComponentName top =
                                (ComponentName) type.getField("topActivity").get(chosen);
                        return new Target(
                                ids[ids.length - 1],
                                context.getPackageManager()
                                        .getApplicationInfo(top.getPackageName(), 0)
                                        .uid,
                                top.flattenToString());
                    }

                    @Override
                    public String windows() throws Exception {
                        return command.run("/system/bin/dumpsys", "-t", "1", "input");
                    }
                };
    }

    String pendingComponent() throws Exception {
        Target before = source.target();
        if (before == null) return null;
        ModernInputWindowState.State state =
                ModernInputWindowState.inspect(source.windows(), display, before.uid);
        Target after = source.target();
        return before.same(after) && state == ModernInputWindowState.State.PENDING
                ? before.component
                : null;
    }
}
