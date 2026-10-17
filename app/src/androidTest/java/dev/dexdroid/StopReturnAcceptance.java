package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.os.SystemClock;
import android.view.View;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Stop through a real UI action while the actual display cleanup worker is held. */
final class StopReturnAcceptance {
    static String run(
            Instrumentation runner,
            MainActivity phone,
            SessionService session,
            Runnable stopAction,
            String label)
            throws Exception {
        int first = session.id;
        int second = session.split == null ? -1 : session.split.displayId(1);
        DisplayManager displays =
                (DisplayManager) session.getSystemService(Context.DISPLAY_SERVICE);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        session.desktopWork(
                () -> {
                    entered.countDown();
                    try {
                        if (!release.await(8, TimeUnit.SECONDS))
                            throw new IllegalStateException("Stop test gate timed out");
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                });
        try {
            require(entered.await(3, TimeUnit.SECONDS), "cleanup worker held");
            runner.runOnMainSync(stopAction);
            // Deliberately exceed the former 250ms restore timer before releasing
            // real virtual displays, allowing Android's actual task handoff to run.
            Thread.sleep(700);
            require(displays.getDisplay(first) != null, "display cleanup really delayed");
        } finally {
            release.countDown();
        }
        await(
                () ->
                        displays.getDisplay(first) == null
                                && (second < 0 || displays.getDisplay(second) == null),
                "both owned displays removed");
        await(
                () ->
                        phone.hasWindowFocus()
                                && phone.getDisplay().getDisplayId() == 0
                                && phone.getWindow().getDecorView().isShown(),
                "existing phone controller foreground after delayed cleanup");
        require(
                session.id < 0 && !session.busy && !session.desktopRequested(),
                "Stop focus return does not restart desktop");
        java.lang.reflect.Field bridge = SessionService.class.getDeclaredField("bridge");
        bridge.setAccessible(true);
        IDeskService helper = (IDeskService) bridge.get(session);
        FutureTask<Void> scope =
                new FutureTask<>(
                        () -> {
                            require(!helper.focusController(-1), "unknown task cannot be focused");
                            ActivityManager manager =
                                    (ActivityManager)
                                            session.getSystemService(Context.ACTIVITY_SERVICE);
                            for (ActivityManager.AppTask task : manager.getAppTasks()) {
                                ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                                ComponentName component = info.baseIntent.getComponent();
                                if (new ComponentName(session, InputTestActivity.class)
                                        .equals(component))
                                    require(
                                            !helper.focusController(info.taskId),
                                            "a migrated input-test task cannot be focused as controller");
                            }
                            return null;
                        });
        session.desktopWork(scope);
        scope.get(5, TimeUnit.SECONDS);
        runner.runOnMainSync(
                () -> {
                    View root = phone.getWindow().getDecorView();
                    StatusDisclosureAssertions.inspectDesktop(root, "phone", "Start desktop");
                    View settings = description(root, "Settings");
                    require(
                            settings != null && settings.performClick(),
                            "Settings usable after Stop");
                });
        await(
                () -> phone.controllerMenu() != null && phone.controllerMenu().isShowing(),
                "phone Settings opens after Stop");
        runner.runOnMainSync(
                () ->
                        phone.controllerMenu()
                                .getWindow()
                                .getDecorView()
                                .findViewWithTag("phone-menu-close")
                                .performClick());
        await(() -> phone.controllerMenu() == null, "Settings closes after Stop");
        await(phone::hasWindowFocus, "controller focused after closing stopped Settings");
        long stableUntil = SystemClock.uptimeMillis() + 1000;
        while (SystemClock.uptimeMillis() < stableUntil) {
            require(
                    phone.hasWindowFocus(),
                    "controller stays foreground after stopped Settings closes");
            Thread.sleep(25);
        }
        Bitmap screenshot = runner.getUiAutomation().takeScreenshot();
        require(screenshot != null, "stopped phone screenshot");
        File folder =
                new File(
                        runner.getTargetContext().getExternalCacheDir(),
                        second < 0 ? "submenu-acceptance" : "split-acceptance");
        if (!folder.isDirectory() && !folder.mkdirs())
            throw new IOException("Stop evidence directory");
        try (FileOutputStream output =
                new FileOutputStream(new File(folder, "phone-stopped-" + label + ".png"))) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally {
            screenshot.recycle();
        }
        return "PASS "
                + label
                + " Stop after >700ms worker delay: displays removed, existing controller foreground, stopped Start icon transparent/white/48dp and Settings usable\n";
    }

    static String boundaries(Instrumentation runner, MainActivity phone, SessionService session)
            throws Exception {
        java.lang.reflect.Field bridge = SessionService.class.getDeclaredField("bridge");
        bridge.setAccessible(true);
        IDeskService helper = (IDeskService) bridge.get(session);
        FutureTask<Void> refusal =
                new FutureTask<>(
                        () -> {
                            try {
                                helper.focusController(phone.getTaskId());
                                throw new AssertionError(
                                        "active desktop must refuse a stale controller return");
                            } catch (IllegalStateException expected) {
                                require(
                                        expected.getMessage().contains("restarted"),
                                        "active desktop return rejected");
                            }
                            return null;
                        });
        session.desktopWork(refusal);
        refusal.get(5, TimeUnit.SECONDS);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        session.desktopWork(
                () -> {
                    entered.countDown();
                    try {
                        if (!release.await(8, TimeUnit.SECONDS))
                            throw new IllegalStateException("Stale Stop gate timed out");
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                });
        try {
            require(entered.await(3, TimeUnit.SECONDS), "stale Stop worker held");
            runner.runOnMainSync(
                    () -> {
                        session.pauseDesktop();
                        session.ensureDesktop();
                    });
            runner.getTargetContext()
                    .startActivity(
                            new Intent(Intent.ACTION_MAIN)
                                    .addCategory(Intent.CATEGORY_HOME)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());
            await(
                    () -> phone.getWindow().getDecorView().getWindowVisibility() != View.VISIBLE,
                    "phone Home hides controller before obsolete Stop finishes");
        } finally {
            release.countDown();
        }
        await(() -> session.id >= 0 && !session.busy, "new session starts after obsolete cleanup");
        drain(runner, session);
        require(
                phone.getWindow().getDecorView().getWindowVisibility() != View.VISIBLE,
                "obsolete Stop completion does not foreground controller over Home/new session");
        int display = session.id;
        runner.runOnMainSync(session::stopDesktop);
        DisplayManager displays =
                (DisplayManager) session.getSystemService(Context.DISPLAY_SERVICE);
        await(() -> displays.getDisplay(display) == null, "passive cleanup removes display");
        drain(runner, session);
        require(
                phone.getWindow().getDecorView().getWindowVisibility() != View.VISIBLE,
                "passive cleanup does not foreground controller");
        // Exercise the real MainActivity.onDestroy owner-release boundary while
        // preserving a background task, so a stale moveToFront would be observable.
        SessionService.Listener[] owner = {null};
        CountDownLatch ownerEntered = new CountDownLatch(1), ownerRelease = new CountDownLatch(1);
        session.desktopWork(
                () -> {
                    ownerEntered.countDown();
                    try {
                        if (!ownerRelease.await(8, TimeUnit.SECONDS))
                            throw new IllegalStateException("Owner Stop gate timed out");
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                });
        require(ownerEntered.await(3, TimeUnit.SECONDS), "owner cleanup worker held");
        try {
            runner.runOnMainSync(
                    () -> {
                        owner[0] = session.listener;
                        require(owner[0] != null, "live controller owner captured before Stop");
                        session.pauseDesktop();
                        session.listener = null;
                    });
        } finally {
            ownerRelease.countDown();
        }
        try {
            drain(runner, session);
            require(
                    phone.getWindow().getDecorView().getWindowVisibility() != View.VISIBLE,
                    "released UI owner cancels pending Stop return without foregrounding old task");
        } finally {
            runner.runOnMainSync(
                    () -> {
                        if (session.listener == null) session.listener = owner[0];
                    });
        }
        runner.getTargetContext()
                .startActivity(
                        new Intent(runner.getTargetContext(), MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());
        await(
                () ->
                        !phone.isFinishing()
                                && !phone.isDestroyed()
                                && phone.getWindow().getDecorView().getWindowVisibility()
                                        == View.VISIBLE,
                "launcher reuses controller after passive teardown");
        await(() -> session.id >= 0 && !session.busy, "explicit launcher reopen starts desktop");
        return "PASS obsolete Stop completion preserves Home/new session; passive teardown stays background; released UI owner cancels Stop return; launcher reuses controller and starts fresh desktop\n";
    }

    static String failures(
            Instrumentation runner, MainActivity phone, SessionService session, boolean secondary)
            throws Exception {
        StringBuilder results = new StringBuilder();
        for (boolean restart : new boolean[] {false, true}) {
            Object target = secondary ? session.split : session;
            java.lang.reflect.Field field =
                    (secondary ? SplitWorkspace.class : SessionService.class)
                            .getDeclaredField(secondary ? "secondBridge" : "bridge");
            field.setAccessible(true);
            IDeskService original = (IDeskService) field.get(target);
            require(original != null, "real cleanup bridge available before injected failure");
            int first = session.id,
                    second = session.split == null ? -1 : session.split.displayId(1);
            CountDownLatch stopped = new CountDownLatch(1), release = new CountDownLatch(1);
            IDeskService failing =
                    (IDeskService)
                            java.lang.reflect.Proxy.newProxyInstance(
                                    IDeskService.class.getClassLoader(),
                                    new Class<?>[] {IDeskService.class},
                                    (proxy, method, arguments) -> {
                                        Object result;
                                        try {
                                            result = method.invoke(original, arguments);
                                        } catch (
                                                java.lang.reflect.InvocationTargetException error) {
                                            throw error.getCause();
                                        }
                                        if (method.getName().equals("stop")) {
                                            stopped.countDown();
                                            if (restart && !release.await(8, TimeUnit.SECONDS))
                                                throw new IllegalStateException(
                                                        "Cleanup failure gate timed out");
                                            throw new IllegalStateException(
                                                    "Injected cleanup confirmation failure");
                                        }
                                        return result;
                                    });
            runner.getTargetContext()
                    .startActivity(
                            new Intent(Intent.ACTION_MAIN)
                                    .addCategory(Intent.CATEGORY_HOME)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());
            await(
                    () -> phone.getWindow().getDecorView().getWindowVisibility() != View.VISIBLE,
                    "Home hides controller before cleanup failure");
            try {
                runner.runOnMainSync(
                        () -> {
                            try {
                                field.set(target, failing);
                            } catch (IllegalAccessException error) {
                                throw new AssertionError(error);
                            }
                            session.pauseDesktop();
                        });
                require(
                        stopped.await(8, TimeUnit.SECONDS),
                        "real bridge Stop reached before injected failure");
                if (restart) runner.runOnMainSync(session::ensureDesktop);
                release.countDown();
                DisplayManager displays =
                        (DisplayManager) session.getSystemService(Context.DISPLAY_SERVICE);
                await(
                        () ->
                                displays.getDisplay(first) == null
                                        && (second < 0 || displays.getDisplay(second) == null),
                        "owned displays released despite reported cleanup failure");
                drain(runner, session);
                if (restart) {
                    await(
                            () -> session.id >= 0 && !session.busy,
                            "new session ready after old cleanup failure");
                    require(
                            !session.status.contains("Cleanup unconfirmed"),
                            "obsolete cleanup failure does not replace new status");
                } else {
                    await(
                            () ->
                                    session.status.contains("Cleanup unconfirmed")
                                            && session.status.contains("Reopen DexDroid"),
                            "cleanup failure exposes uncertainty and recovery guidance");
                    require(
                            session.id < 0 && !session.desktopRequested(),
                            "failed cleanup return stays stopped");
                }
                long until = SystemClock.uptimeMillis() + 1000;
                while (SystemClock.uptimeMillis() < until) {
                    require(
                            phone.getWindow().getDecorView().getWindowVisibility() != View.VISIBLE,
                            "failed cleanup never foregrounds the phone controller");
                    Thread.sleep(25);
                }
                require(
                        session.diagnosticText().contains("Injected cleanup confirmation failure"),
                        "injected cleanup result is retained diagnostically");
            } finally {
                release.countDown();
                runner.runOnMainSync(
                        () -> {
                            try {
                                if (field.get(target) == failing) field.set(target, original);
                            } catch (IllegalAccessException error) {
                                throw new AssertionError(error);
                            }
                        });
            }
            runner.getTargetContext()
                    .startActivity(
                            new Intent(runner.getTargetContext(), MainActivity.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());
            await(
                    () ->
                            !phone.isDestroyed()
                                    && phone.getWindow().getDecorView().getWindowVisibility()
                                            == View.VISIBLE
                                    && session.id >= 0
                                    && !session.busy
                                    && (!secondary || session.split != null),
                    "explicit phone recovery restarts usable desktop after cleanup failure");
            results.append("PASS ")
                    .append(secondary ? "secondary" : "primary")
                    .append(" injected cleanup confirmation fault; no controller foreground; ")
                    .append(
                            restart
                                    ? "obsolete fault preserves new session/status"
                                    : "stopped uncertainty and explicit recovery")
                    .append("; real displays released and launcher recovery works\n");
        }
        return results.toString();
    }

    private static void drain(Instrumentation runner, SessionService session) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        session.desktopWork(
                () ->
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .post(done::countDown));
        require(done.await(8, TimeUnit.SECONDS), "cleanup and main callbacks drained");
        runner.waitForIdleSync();
    }

    private static View description(View view, String label) {
        if (android.text.TextUtils.equals(label, view.getContentDescription())) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = description(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void await(BooleanSupplier condition, String message) throws Exception {
        long end = SystemClock.uptimeMillis() + 10000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(50);
        require(condition.getAsBoolean(), message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
