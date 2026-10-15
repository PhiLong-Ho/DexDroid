package dev.dexdroid;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Native profile menus and real display preview/Keep/Revert/restart acceptance. */
final class MonitorProfilesAcceptance {
    private final Instrumentation runner;
    private final StringBuilder results = new StringBuilder();
    private SessionService session;
    private MainActivity phone;
    private Map<String, ?> original;
    private ServiceConnection connection;
    private boolean bound;
    private long down;

    MonitorProfilesAcceptance(Instrumentation runner) {
        this.runner = runner;
    }

    private void main(Runnable action) {
        runner.runOnMainSync(action);
    }

    private void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        results.append("PASS ").append(label).append('\n');
    }

    private void await(BooleanSupplier condition, String label) throws Exception {
        long end = SystemClock.uptimeMillis() + 10000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(50);
        check(condition.getAsBoolean(), label);
    }

    private View text(View root, String value) {
        if (root instanceof TextView && value.contentEquals(((TextView) root).getText()))
            return root;
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                View found = text(((ViewGroup) root).getChildAt(i), value);
                if (found != null) return found;
            }
        return null;
    }

    private View description(View root, String value) {
        if (value.contentEquals(
                root.getContentDescription() == null ? "" : root.getContentDescription()))
            return root;
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                View found = description(((ViewGroup) root).getChildAt(i), value);
                if (found != null) return found;
            }
        return null;
    }

    private View phoneRoot() {
        return phone.controllerMenu().getWindow().getDecorView();
    }

    private void click(View root, String tag) {
        View view = root.findViewWithTag(tag);
        check(view != null && view.isEnabled(), "enabled profile action " + tag);
        check(view.performClick(), "activated " + tag);
    }

    private float gestureOffsetX, gestureOffsetY;

    private void event(int action, float u, float v) throws Exception {
        if (action == MotionEvent.ACTION_DOWN) down = SystemClock.uptimeMillis();
        Display.Mode mode = session.external.getMode();
        float[] point =
                Geometry.map(
                        u,
                        v,
                        session.rotation,
                        mode.getPhysicalWidth(),
                        mode.getPhysicalHeight(),
                        session.width(),
                        session.height());
        if (action == MotionEvent.ACTION_DOWN) {
            float[] start = point.clone(), end = point.clone();
            DeviceTestSupport.usableMonitorSwipe(session, start, end);
            gestureOffsetX = start[0] - point[0];
            gestureOffsetY = start[1] - point[1];
        }
        point[0] += gestureOffsetX;
        point[1] += gestureOffsetY;
        MotionEvent.PointerProperties property = new MotionEvent.PointerProperties();
        property.id = 0;
        property.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = point[0];
        coords.y = point[1];
        coords.pressure = 1;
        coords.size = 1;
        MotionEvent event =
                MotionEvent.obtain(
                        down,
                        SystemClock.uptimeMillis(),
                        action,
                        1,
                        new MotionEvent.PointerProperties[] {property},
                        new MotionEvent.PointerCoords[] {coords},
                        0,
                        0,
                        1,
                        1,
                        0,
                        0,
                        InputDevice.SOURCE_TOUCHSCREEN,
                        0);
        try {
            check(
                    DeviceTestSupport.injectOnDisplay(event, session.external.getDisplayId()),
                    "physical profile menu event " + action);
        } finally {
            event.recycle();
        }
    }

    private void controls() throws Exception {
        event(0, .5f, .001f);
        Thread.sleep(30);
        event(2, .5f, .30f);
        event(1, .5f, .30f);
        await(
                () ->
                        session.monitorShellPage().equals("controls")
                                && !session.monitorShellAnimating(),
                "controls edge navigation");
        main(() -> click(session.monitorShellView(), "monitor-settings"));
        await(
                () ->
                        session.monitorShellPage().equals("settings")
                                && !session.monitorShellAnimating(),
                "monitor settings");
        main(() -> click(session.monitorShellView(), "monitor-profiles"));
        await(
                () ->
                        session.monitorShellPage().equals("profiles")
                                && !session.monitorShellAnimating(),
                "monitor profile submenu");
    }

    private void screenshot(String name, boolean monitor) throws Exception {
        Thread.sleep(150);
        Bitmap bitmap;
        if (monitor) {
            Display.Mode mode = session.external.getMode();
            bitmap =
                    Bitmap.createBitmap(
                            mode.getPhysicalWidth(),
                            mode.getPhysicalHeight(),
                            Bitmap.Config.ARGB_8888);
            CountDownLatch done = new CountDownLatch(1);
            int[] code = {-1};
            main(
                    () ->
                            PixelCopy.request(
                                    session.monitorWindow(),
                                    bitmap,
                                    result -> {
                                        code[0] = result;
                                        done.countDown();
                                    },
                                    new Handler(Looper.getMainLooper())));
            check(
                    done.await(5, TimeUnit.SECONDS) && code[0] == PixelCopy.SUCCESS,
                    "captured rendered monitor profile menu");
        } else bitmap = runner.getUiAutomation().takeScreenshot();
        check(bitmap != null, "profile screenshot available");
        try {
            File folder =
                    new File(
                            runner.getTargetContext().getExternalCacheDir(),
                            "monitor-profile-acceptance");
            check(folder.isDirectory() || folder.mkdirs(), "profile screenshot directory");
            try (FileOutputStream stream = new FileOutputStream(new File(folder, name + ".png"))) {
                check(
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream),
                        "profile screenshot exported " + name);
            }
        } finally {
            bitmap.recycle();
        }
    }

    private void restore() {
        if (session == null || original == null) return;
        main(
                () -> {
                    session.pauseDesktop();
                    android.content.SharedPreferences.Editor edit = session.prefs.edit().clear();
                    for (Map.Entry<String, ?> entry : original.entrySet()) {
                        Object value = entry.getValue();
                        String key = entry.getKey();
                        if (value instanceof String) edit.putString(key, (String) value);
                        else if (value instanceof Integer) edit.putInt(key, (Integer) value);
                        else if (value instanceof Float) edit.putFloat(key, (Float) value);
                        else if (value instanceof Long) edit.putLong(key, (Long) value);
                        else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
                    }
                    edit.commit();
                });
    }

    String run() throws Exception {
        try {
            phone = DeviceTestSupport.startPhone(runner);
            CountDownLatch ready = new CountDownLatch(1);
            connection =
                    new ServiceConnection() {
                        public void onServiceConnected(ComponentName name, IBinder binder) {
                            session = ((SessionService.LocalBinder) binder).get();
                            ready.countDown();
                        }

                        public void onServiceDisconnected(ComponentName name) {}
                    };
            bound =
                    runner.getTargetContext()
                            .bindService(
                                    new Intent(runner.getTargetContext(), SessionService.class),
                                    connection,
                                    Context.BIND_AUTO_CREATE);
            check(ready.await(5, TimeUnit.SECONDS), "profile service connected");
            await(() -> session.id >= 0 && !session.busy, "profile desktop ready");
            original = new HashMap<>(session.prefs.getAll());
            main(
                    () ->
                            session.launch(
                                    new ComponentName(
                                            runner.getTargetContext(), InputTestActivity.class),
                                    null));
            await(
                    () ->
                            !session.busy
                                    && session.hasDesktopApp()
                                    && session.activePackage.equals("dev.dexdroid"),
                    "deterministic input-test backdrop for profile screenshots");
            Thread.sleep(500);
            String title = "Profile acceptance " + SystemClock.uptimeMillis();
            int before = session.monitorProfiles().all().size();
            main(() -> description(phone.getWindow().getDecorView(), "Settings").performClick());
            main(
                    () ->
                            ((View) text(phoneRoot(), "Monitor profiles").getParent().getParent())
                                    .performClick());
            await(
                    () ->
                            phone.controllerMenu() != null
                                    && phoneRoot().findViewWithTag("profiles-panel") != null,
                    "phone profile submenu opens");
            main(
                    () -> {
                        check(
                                phoneRoot().findViewWithTag("phone-menu-scroll")
                                        instanceof ScrollView,
                                "phone profiles scroll with fixed header");
                        if (phoneRoot().findViewWithTag("profile-create") == null)
                            click(phoneRoot(), "profile-new");
                        EditText editor = phoneRoot().findViewWithTag("profile-name");
                        editor.setText("");
                        click(phoneRoot(), "profile-create");
                        check(
                                session.monitorProfiles().all().size() == before,
                                "empty profile name does not persist");
                        editor.setText(title);
                        click(phoneRoot(), "profile-create");
                    });
            await(
                    () -> session.monitorProfiles().all().size() == before + 1,
                    "phone create persists named profile");
            MonitorProfiles.Profile saved =
                    session.monitorProfiles().bound(session.monitorIdentity());
            check(
                    saved != null && saved.name.equals(title),
                    "saved profile remembers current monitor");
            screenshot("phone-profiles", false);
            main(
                    () -> {
                        ((EditText) phoneRoot().findViewWithTag("profile-name"))
                                .setText(title + " renamed");
                        click(phoneRoot(), "profile-rename");
                        check(
                                session.monitorProfiles()
                                        .find(saved.id)
                                        .name
                                        .equals(title + " renamed"),
                                "phone rename retains the profile identity");
                    });
            main(() -> phone.controllerMenu().dismiss());
            main(
                    () -> {
                        session.setMonitorNavigationEnabled(true);
                        session.setMonitorTouchEnabled(true);
                    });
            controls();
            main(
                    () -> {
                        check(
                                MenuTextAssertions.check(session.monitorShellView()) > 0,
                                "monitor profile text and glyphs valid");
                        check(
                                session.monitorShellView()
                                                .findViewWithTag("profile-row-" + saved.id)
                                        != null,
                                "phone-created profile appears on monitor");
                        click(session.monitorShellView(), "profile-new");
                        EditText editor =
                                session.monitorShellView().findViewWithTag("profile-name");
                        editor.performClick();
                        check(
                                session.monitorShellView()
                                                .findViewWithTag("profile-keyboard")
                                                .getVisibility()
                                        == View.VISIBLE,
                                "monitor profile name local keyboard opens");
                        int length = editor.length();
                        click(session.monitorShellView(), "monitor-key-q");
                        check(
                                editor.length() == length + 1,
                                "monitor profile name keyboard edits immediately");
                        String fullName = new String(new char[48]).replace('\0', 'x');
                        editor.setText(fullName);
                        editor.setSelection(48);
                        click(session.monitorShellView(), "monitor-key-q");
                        check(
                                fullName.contentEquals(editor.getText())
                                        && editor.getSelectionStart() == 48,
                                "name limit safely rejects another local key");
                        editor.setSelection(0, 1);
                        click(session.monitorShellView(), "monitor-key-q");
                        check(
                                editor.length() == 48
                                        && editor.getText().charAt(0) == 'q'
                                        && editor.getSelectionStart() == 1,
                                "local key replaces selection at name limit");
                    });
            screenshot("monitor-profiles-keyboard", true);
            main(() -> session.key(KeyEvent.KEYCODE_BACK));
            await(
                    () -> session.monitorShellPage().equals("settings"),
                    "profile Back returns to monitor settings");
            main(() -> session.key(KeyEvent.KEYCODE_BACK));
            main(() -> session.key(KeyEvent.KEYCODE_BACK));
            await(
                    () ->
                            session.monitorShellPage().equals("closed")
                                    && !session.monitorShellAnimating(),
                    "profile monitor menus close");
            MonitorProfiles.Profile alternate =
                    session.monitorProfiles()
                            .create(
                                    title + " alternate",
                                    new MonitorProfiles.Configuration(1280, 800, 160, 0),
                                    null);
            for (int r = 0; r < 4; r++) {
                final int selected = r;
                main(
                        () -> {
                            session.monitorProfiles()
                                    .update(
                                            alternate.id,
                                            new MonitorProfiles.Configuration(
                                                    1280, 800, 160, selected));
                        });
                controls();
                main(
                        () -> {
                            click(session.monitorShellView(), "profile-row-" + alternate.id);
                            click(session.monitorShellView(), "profile-apply");
                        });
                await(
                        () ->
                                !session.busy
                                        && session.rotation == selected
                                        && session.baseW == 1280,
                        "profile preview geometry " + r);
                check(
                        session.monitorProfiles()
                                .bound(session.monitorIdentity())
                                .id
                                .equals(saved.id),
                        "preview does not commit association " + r);
                screenshot("monitor-profiles-r" + r, true);
                main(() -> click(session.monitorShellView(), "profiles-revert"));
                await(
                        () -> !session.busy && !session.awaitingConfirmation(),
                        "profile Revert settles " + r);
                check(
                        session.rotation == saved.configuration.rotation
                                && session.defaultRotation == saved.configuration.rotation,
                        "profile Revert restores output and default orientation");
                main(() -> session.key(KeyEvent.KEYCODE_BACK));
                main(() -> session.key(KeyEvent.KEYCODE_BACK));
                main(() -> session.key(KeyEvent.KEYCODE_BACK));
                await(
                        () ->
                                session.monitorShellPage().equals("closed")
                                        && !session.monitorShellAnimating(),
                        "profile menu closed after Revert");
            }
            main(() -> session.applyMonitorProfile(alternate.id));
            await(() -> !session.busy && session.awaitingConfirmation(), "profile ready to Keep");
            main(() -> session.confirm(true));
            check(
                    session.monitorProfiles()
                            .bound(session.monitorIdentity())
                            .id
                            .equals(alternate.id),
                    "Keep commits selected monitor binding");
            main(
                    () -> {
                        session.pauseDesktop();
                        session.ensureDesktop();
                    });
            await(
                    () -> session.id >= 0 && !session.busy,
                    "desktop restarts with saved monitor profile");
            check(
                    session.baseW == 1280
                            && session.baseH == 800
                            && session.dpi == 160
                            && session.rotation == 3,
                    "restart automatically reapplies profile geometry and rotation");
            ordinaryKeepAndRestart("bound profile");
            check(
                    session.monitorProfiles().find(alternate.id).configuration.width == 1280,
                    "ordinary Keep preserves named snapshot");
            main(() -> session.applyMonitorProfile(alternate.id));
            await(
                    () -> !session.busy && session.awaitingConfirmation(),
                    "reapply resets working override");
            main(() -> session.confirm(true));
            main(() -> session.applyMonitorProfile(saved.id));
            await(() -> !session.busy && session.awaitingConfirmation(), "timeout preview ready");
            Thread.sleep(16000);
            await(
                    () -> !session.busy && !session.awaitingConfirmation(),
                    "profile preview automatically reverts after 15 seconds");
            check(
                    session.monitorProfiles()
                                    .bound(session.monitorIdentity())
                                    .id
                                    .equals(alternate.id)
                            && session.rotation == 3,
                    "timeout retains prior binding and orientation");
            main(
                    () -> {
                        session.applyMonitorProfile(saved.id);
                        session.pauseDesktop();
                        session.ensureDesktop();
                    });
            await(() -> session.id >= 0 && !session.busy, "Stop during preview restarts safely");
            check(
                    !session.awaitingConfirmation()
                            && session.monitorProfiles()
                                    .bound(session.monitorIdentity())
                                    .id
                                    .equals(alternate.id),
                    "Stop cancels unconfirmed profile binding");
            main(() -> session.resetMonitor());
            check(
                    session.monitorProfiles().bound(session.monitorIdentity()) == null
                            && session.monitorProfiles().find(alternate.id) != null,
                    "Forget clears association and retains named profiles");
            ordinaryKeepAndRestart("after Forget");
            check(
                    session.monitorProfiles().bound(session.monitorIdentity()) == null,
                    "ordinary Keep after Forget does not invent named binding");
            main(() -> session.rotate(1, true));
            await(() -> !session.busy && session.rotation == 1, "manual default rotation settles");
            main(
                    () -> {
                        session.pauseDesktop();
                        session.ensureDesktop();
                    });
            await(() -> session.id >= 0 && !session.busy, "restart after manual default rotation");
            check(
                    session.rotation == 1
                            && session.defaultRotation == 1
                            && session.baseW == 1440
                            && session.baseH == 900,
                    "manual default rotation survives working-settings restart");
            main(
                    () -> {
                        session.pauseDesktop();
                        // Seed remembered settings different from the stopped runtime, as on
                        // monitor change.
                        session.monitorProfiles().bind(session.monitorIdentity(), alternate.id);
                        session.rotate(saved.configuration.rotation, true);
                        session.ensureDesktop();
                    });
            await(() -> session.id >= 0 && !session.busy, "restart after stopped default rotation");
            check(
                    session.baseW == 1280
                            && session.baseH == 800
                            && session.dpi == 160
                            && session.rotation == saved.configuration.rotation,
                    "stopped rotation preserves detected monitor settings instead of stale runtime");
            check(
                    session.monitorProfiles().find(alternate.id).configuration.rotation == 3,
                    "stopped rotation does not edit named snapshot");
            main(
                    () -> {
                        check(
                                !session.diagnosticText().contains(title),
                                "profile names are not copied into diagnostic logs");
                        check(
                                Objects.equals(
                                        session.prefs.getAll().get("rotation"),
                                        saved.configuration.rotation),
                                "only explicit manual rotation rewrites global default");
                        for (Map.Entry<String, ?> entry : original.entrySet())
                            if (entry.getKey().startsWith("app.")
                                    || entry.getKey().startsWith("scrollSpeed.")
                                    || entry.getKey().startsWith("swipeScroll."))
                                check(
                                        Objects.equals(
                                                entry.getValue(),
                                                session.prefs.getAll().get(entry.getKey())),
                                        "app override preserved " + entry.getKey());
                        description(phone.getWindow().getDecorView(), "Settings").performClick();
                        ((View) text(phoneRoot(), "Monitor profiles").getParent().getParent())
                                .performClick();
                        click(phoneRoot(), "profile-row-" + saved.id);
                        click(phoneRoot(), "profile-delete");
                        check(
                                session.monitorProfiles().find(saved.id) != null,
                                "delete requires explicit confirmation");
                        click(phoneRoot(), "profile-delete");
                        check(
                                session.monitorProfiles().find(saved.id) == null
                                        && session.monitorProfiles().find(alternate.id) != null,
                                "confirmed delete preserves other named profiles");
                        phone.controllerMenu().dismiss();
                    });
            return results.toString();
        } catch (Throwable failure) {
            throw new RuntimeException(results + "FAIL monitor profile acceptance", failure);
        } finally {
            restore();
            if (session != null) {
                await(
                        () -> {
                            android.hardware.display.DisplayManager displays =
                                    (android.hardware.display.DisplayManager)
                                            runner.getTargetContext()
                                                    .getSystemService(Context.DISPLAY_SERVICE);
                            for (Display display : displays.getDisplays())
                                if (display.getName().equals("DexDroid Desktop")) return false;
                            return true;
                        },
                        "profile cleanup releases virtual display before instrumentation exits");
            }
            if (bound) runner.getTargetContext().unbindService(connection);
        }
    }

    private void ordinaryKeepAndRestart(String state) throws Exception {
        main(() -> session.preview(1440, 900, 180));
        await(() -> !session.busy && session.awaitingConfirmation(), "ordinary preview " + state);
        main(() -> session.confirm(true));
        main(
                () -> {
                    session.pauseDesktop();
                    session.ensureDesktop();
                });
        await(() -> session.id >= 0 && !session.busy, "ordinary Keep restart " + state);
        check(
                session.baseW == 1440 && session.baseH == 900 && session.dpi == 180,
                "ordinary Keep restores confirmed working settings " + state);
    }
}
