package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Exercises real phone menu windows, keyboard resizing, selected state and rotation. */
final class PhoneMenuAcceptance {
    private final Instrumentation runner;
    private final MainActivity phone;
    private final SessionService session;
    private final StringBuilder results = new StringBuilder();
    private boolean modernRotation;

    PhoneMenuAcceptance(Instrumentation runner, MainActivity phone, SessionService session) {
        this.runner = runner;
        this.phone = phone;
        this.session = session;
    }

    private void main(Runnable r) {
        runner.runOnMainSync(r);
    }

    private void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        results.append("PASS ").append(label).append('\n');
    }

    private void await(BooleanSupplier p, String label) throws Exception {
        long until = SystemClock.uptimeMillis() + 10000;
        while (!p.getAsBoolean() && SystemClock.uptimeMillis() < until) Thread.sleep(50);
        check(p.getAsBoolean(), label);
    }

    private View text(View root, String value) {
        if (root instanceof TextView && value.contentEquals(((TextView) root).getText()))
            return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = text(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private View description(View root, String value) {
        if (value.contentEquals(
                root.getContentDescription() == null ? "" : root.getContentDescription()))
            return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = description(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private View root() {
        return phone.controllerMenu().getWindow().getDecorView();
    }

    private void open(String button) throws Exception {
        main(
                () -> {
                    View found =
                            button.equals("Scroll")
                                    ? phone.getWindow()
                                            .getDecorView()
                                            .findViewWithTag("phone-scroll")
                                    : text(phone.getWindow().getDecorView(), button);
                    check(found != null, "phone submenu trigger exists: " + button);
                    found.performClick();
                });
        await(
                () -> phone.controllerMenu() != null && phone.controllerMenu().isShowing(),
                "phone submenu opens: " + button);
        Thread.sleep(200);
    }

    private void clickText(String label) {
        main(
                () -> {
                    View found = text(root(), label);
                    check(found != null, "phone menu label exists: " + label);
                    View target = found;
                    while (!target.isClickable() && target.getParent() instanceof View)
                        target = (View) target.getParent();
                    check(target.isClickable(), "phone menu action is clickable: " + label);
                    target.performClick();
                });
    }

    private void close() throws Exception {
        main(
                () -> {
                    Dialog dialog = phone.controllerMenu();
                    check(dialog != null, "phone submenu available to close");
                    dialog.getWindow()
                            .getDecorView()
                            .findViewWithTag("phone-menu-close")
                            .performClick();
                });
        await(() -> phone.controllerMenu() == null, "phone Close icon dismisses submenu");
    }

    private void inspect(String page) throws Exception {
        main(
                () -> {
                    View panel = root().findViewWithTag("phone-menu-" + page),
                            close = root().findViewWithTag("phone-menu-close");
                    check(panel != null && panel.isShown(), "phone Settings-style panel: " + page);
                    results.append(MenuHeaderAssertions.inspect(root(), "phone"));
                    check(
                            close instanceof ImageButton && close.isShown(),
                            "phone submenu fixed icon Close: " + page);
                    WindowInsets insets = root().getRootWindowInsets();
                    check(insets != null, "phone menu receives actual window insets: " + page);
                    Insets safe =
                            insets.getInsets(
                                    WindowInsets.Type.systemBars()
                                            | WindowInsets.Type.displayCutout());
                    int[] position = new int[2];
                    close.getLocationOnScreen(position);
                    int screenHeight =
                            phone.getWindowManager().getCurrentWindowMetrics().getBounds().height();
                    check(
                            position[1] >= safe.top
                                    && position[1] + close.getHeight()
                                            <= screenHeight - safe.bottom,
                            "phone Close stays clear of system bars and cutout: " + page);
                    check(
                            MenuTextAssertions.check(root()) > 0,
                            "phone submenu decoded text and glyphs: " + page);
                    Rect area = new Rect();
                    root().getWindowVisibleDisplayFrame(area);
                    check(
                            panel.getHeight() >= area.height() * .94,
                            "phone submenu uses full available height: " + page);
                    check(
                            panel.getWidth() > panel.getHeight() * .3
                                    && panel.getHeight() > panel.getWidth(),
                            "phone submenu remains portrait: " + page);
                });
    }

    private void card(View view, String label) {
        check(
                view instanceof TextView && view.getBackground() instanceof RippleDrawable,
                "Settings row surface: " + label);
        TextView text = (TextView) view;
        float d = view.getResources().getDisplayMetrics().density;
        check(
                text.getMinHeight() >= 61 * d && text.getCurrentTextColor() == Color.WHITE,
                "Settings row size and white text: " + label);
        Drawable background = ((RippleDrawable) view.getBackground()).getDrawable(0);
        check(
                background instanceof GradientDrawable
                        && Math.abs(((GradientDrawable) background).getCornerRadius() / d - 8) < 1,
                "Settings row rounded geometry: " + label);
    }

    private void shot(String name) throws Exception {
        Thread.sleep(200);
        Bitmap bitmap = runner.getUiAutomation().takeScreenshot();
        check(bitmap != null, "captured phone menu: " + name);
        try {
            File dir =
                    new File(runner.getTargetContext().getExternalCacheDir(), "submenu-acceptance");
            if (!dir.isDirectory() && !dir.mkdirs())
                throw new IOException("Cannot create screenshot folder");
            try (FileOutputStream stream = new FileOutputStream(new File(dir, name + ".png"))) {
                check(
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream),
                        "exported phone menu: " + name);
            }
        } finally {
            bitmap.recycle();
        }
    }

    private void tap(View view) throws Exception {
        Rect rect = new Rect();
        main(
                () -> {
                    check(view.getGlobalVisibleRect(rect), "phone touch target visible");
                    int[] origin = new int[2];
                    view.getRootView().getLocationOnScreen(origin);
                    rect.offset(origin[0], origin[1]);
                    check(
                            rect.width() > 0 && rect.height() > 0,
                            "phone rendered target screen bounds " + rect.toShortString());
                });
        long now = SystemClock.uptimeMillis();
        for (int action : new int[] {0, 1}) {
            MotionEvent e =
                    MotionEvent.obtain(
                            now,
                            SystemClock.uptimeMillis(),
                            action,
                            rect.exactCenterX(),
                            rect.exactCenterY(),
                            0);
            e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            check(
                    runner.getUiAutomation().injectInputEvent(e, true),
                    "real phone menu tap " + action);
            e.recycle();
        }
        Thread.sleep(150);
    }

    private void phoneRotation(String mode) throws Exception {
        String command =
                modernRotation
                        ? "wm user-rotation -d 0 " + mode
                        : "wm set-user-rotation "
                                + mode.split(" ")[0]
                                + " -d 0"
                                + (mode.startsWith("lock ") ? " " + mode.substring(5) : "");
        String result = DeviceTestSupport.shell(runner, command);
        check(
                !result.contains("Unknown command") && !result.contains("Error:"),
                "phone rotation command is supported");
    }

    private void touchpadDrag() throws Exception {
        int[] point = new int[2], scrollY = {0};
        float[] before = new float[2];
        main(
                () -> {
                    ScrollView scroll =
                            phone.getWindow()
                                    .getDecorView()
                                    .findViewWithTag("phone-controller-scroll");
                    View pad = scroll.findViewWithTag("touchpad");
                    pad.getLocationOnScreen(point);
                    point[0] += pad.getWidth() / 2;
                    point[1] +=
                            pad.getHeight()
                                    - Math.round(
                                            24 * pad.getResources().getDisplayMetrics().density);
                    scrollY[0] = scroll.getScrollY();
                    session.center();
                    before[0] = session.cx;
                    before[1] = session.cy;
                });
        long down = SystemClock.uptimeMillis();
        for (int i = 0; i < 3; i++) {
            MotionEvent event =
                    MotionEvent.obtain(
                            down,
                            SystemClock.uptimeMillis(),
                            new int[] {0, 2, 1}[i],
                            point[0] + (i == 0 ? 0 : 40),
                            point[1] - (i == 0 ? 0 : 80),
                            0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            check(
                    runner.getUiAutomation().injectInputEvent(event, true),
                    "real touchpad drag event " + i);
            event.recycle();
            Thread.sleep(40);
        }
        main(
                () -> {
                    ScrollView scroll =
                            phone.getWindow()
                                    .getDecorView()
                                    .findViewWithTag("phone-controller-scroll");
                    check(
                            scroll.getScrollY() == scrollY[0],
                            "parent scroll does not steal touchpad drag");
                    check(
                            Math.abs(session.cx - before[0]) > 10
                                    && Math.abs(session.cy - before[1]) > 10,
                            "touchpad drag still moves monitor pointer");
                });
    }

    private View hintWindow() {
        for (View window : android.view.inspector.WindowInspector.getGlobalWindowViews())
            if (window.getDisplay() != null
                    && window.getDisplay().getDisplayId() == 0
                    && window.isShown()
                    && text(window, "Touchpad gestures") != null) return window;
        return null;
    }

    private void touchpadActions() throws Exception {
        View decor = phone.getWindow().getDecorView();
        int backs = InputTestActivity.backEvents, touches = InputTestActivity.touchEvents;
        float x = session.cx, y = session.cy;
        tap(decor.findViewWithTag("phone-back"));
        await(
                () -> InputTestActivity.backEvents == backs + 1,
                "touchpad Back sends exactly one native Back");
        check(
                InputTestActivity.touchEvents == touches && session.cx == x && session.cy == y,
                "Back icon does not leak a touchpad gesture");
        shot("phone-touchpad-actions");
        tap(decor.findViewWithTag("touchpad-help"));
        await(
                () -> hintWindow() != null,
                "touchpad hint opens gesture instructions on phone display");
        check(
                InputTestActivity.touchEvents == touches && session.cx == x && session.cy == y,
                "hint icon does not leak a touchpad gesture");
        shot("phone-touchpad-hint");
        View gotIt = text(hintWindow(), "Got it");
        check(gotIt != null, "phone hint dismiss action exists");
        tap(gotIt);
        await(
                () -> hintWindow() == null,
                "phone hint dismiss action works independently of monitor focus");
        tap(decor.findViewWithTag("touchpad-title"));
        check(
                InputTestActivity.touchEvents == touches
                        && session.cx == x
                        && session.cy == y
                        && phone.controllerMenu() == null,
                "blank touchpad header consumes taps without pointer/app input");
        tap(decor.findViewWithTag("phone-scroll"));
        expectPage("scroll");
        check(
                InputTestActivity.touchEvents == touches && session.cx == x && session.cy == y,
                "Scroll icon opens configuration without pad input");
        close();
        open("Layout");
        inspect("layout");
        shot("phone-layout-controls");
        close();
    }

    private void expectPage(String page) throws Exception {
        await(
                () ->
                        phone.controllerMenu() != null
                                && root().findViewWithTag("phone-menu-" + page) != null,
                "phone navigation reaches " + page);
    }

    private void up() {
        main(
                () -> {
                    View action = root().findViewWithTag("phone-menu-up");
                    check(action != null && action.isShown(), "nested page has leading Up");
                    action.performClick();
                });
    }

    private void queuedBackOwnership() throws Exception {
        clickText("Pointer speed");
        expectPage("pointer-speed");
        main(
                () -> {
                    Dialog owner = phone.controllerMenu();
                    owner.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
                    owner.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
                });
        runner.waitForIdleSync();
        expectPage("settings");
        check(phone.controllerMenu().isShowing(), "duplicate pending Back navigates only once");
        clickText("Pointer speed");
        expectPage("pointer-speed");
        Dialog[] stale = {null};
        main(
                () -> {
                    stale[0] = phone.controllerMenu();
                    stale[0].dispatchKeyEvent(
                            new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
                    root().findViewWithTag("phone-menu-close").performClick();
                });
        runner.waitForIdleSync();
        check(phone.controllerMenu() == null, "Close invalidates queued Back without reopening");
        main(() -> description(phone.getWindow().getDecorView(), "Settings").performClick());
        expectPage("settings");
        clickText("Pointer speed");
        expectPage("pointer-speed");
        main(
                () -> {
                    Dialog owner = phone.controllerMenu();
                    owner.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
                    root().findViewWithTag("phone-menu-up").performClick();
                    stale[0].dispatchKeyEvent(
                            new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
                });
        runner.waitForIdleSync();
        expectPage("settings");
        check(phone.controllerMenu().isShowing(), "replaced and stale dialog Back stays inert");
    }

    private void navigationFlow() throws Exception {
        main(() -> description(phone.getWindow().getDecorView(), "Settings").performClick());
        main(
                () ->
                        ((ScrollView) root().findViewWithTag("phone-menu-scroll"))
                                .fullScroll(View.FOCUS_DOWN));
        await(
                () -> {
                    ScrollView view = root().findViewWithTag("phone-menu-scroll");
                    return view.getScrollY()
                            == Math.max(
                                    0,
                                    view.getChildAt(0).getHeight()
                                            - view.getHeight()
                                            + view.getPaddingTop()
                                            + view.getPaddingBottom());
                },
                "Settings smooth scroll settles before recording parent position");
        int scroll = ((ScrollView) root().findViewWithTag("phone-menu-scroll")).getScrollY();
        check(scroll > 0, "Settings parent is scrolled before child navigation");
        clickText("Diagnostic log");
        expectPage("diagnostics");
        main(
                () -> {
                    View action = root().findViewWithTag("phone-menu-up"),
                            title = root().findViewWithTag("phone-menu-title"),
                            close = root().findViewWithTag("phone-menu-close");
                    check(
                            action.getLeft() < title.getLeft() && title.getLeft() < close.getLeft(),
                            "nested header separates leading Up and trailing Close");
                });
        shot("phone-navigation-diagnostics");
        up();
        expectPage("settings");
        await(
                () ->
                        ((ScrollView) root().findViewWithTag("phone-menu-scroll")).getScrollY()
                                == scroll,
                "return retains Settings scroll position expected=" + scroll);
        for (String[] child :
                new String[][] {
                    {"Pointer speed", "pointer-speed"},
                    {"Resolution and DPI", "display"},
                    {"Monitor profiles", "profiles"},
                    {"Protected screen help", "protected-help"},
                    {"App settings", "app-settings"}
                }) {
            clickText(child[0]);
            expectPage(child[1]);
            up();
            expectPage("settings");
        }
        int width = session.baseW, height = session.baseH, dpi = session.dpi;
        clickText("Resolution and DPI");
        expectPage("display");
        main(
                () -> {
                    ((EditText) root().findViewWithTag("phone-display-size"))
                            .setText(width + "x" + height);
                    ((EditText) root().findViewWithTag("phone-display-dpi"))
                            .setText(Integer.toString(dpi));
                });
        clickText("Preview resolution");
        expectPage("settings");
        await(
                () -> session.awaitingConfirmation() && !session.busy,
                "preview returns to parent with pending confirmation");
        main(
                () ->
                        check(
                                root().findViewWithTag("phone-menu-keep").isShown()
                                        && root().findViewWithTag("phone-menu-revert").isShown(),
                                "Keep and Revert remain reachable below parent menu header"));
        shot("phone-navigation-preview");
        main(() -> root().findViewWithTag("phone-menu-revert").performClick());
        await(
                () ->
                        !session.awaitingConfirmation()
                                && !session.busy
                                && session.baseW == width
                                && session.baseH == height
                                && session.dpi == dpi,
                "parent menu Revert restores display configuration");
        for (int attempt = 0; attempt < 3; attempt++) {
            clickText("Pointer speed");
            expectPage("pointer-speed");
            await(
                    () ->
                            root().isShown()
                                    && root().hasWindowFocus()
                                    && !root().isLayoutRequested(),
                    "native Back owner window is ready");
            DeviceTestSupport.shell(runner, "input -d 0 keyevent 4");
            expectPage("settings");
        }
        queuedBackOwnership();
        clickText("Pointer speed");
        main(() -> root().findViewWithTag("phone-pointer-0").performClick());
        expectPage("settings");
        check(
                session.prefs.getFloat("speed", 0) == 1f,
                "immediate choice returns to Settings with value applied");
        clickText("Diagnostic log");
        close();
        open("Apps");
        check(
                root().findViewWithTag("phone-menu-up") == null,
                "fresh Apps root has no stale Settings parent");
        close();
        main(() -> description(phone.getWindow().getDecorView(), "Settings").performClick());
        expectPage("settings");
        clickText("App settings");
        expectPage("app-settings");
        shot("phone-app-settings");
        clickText("Scrolling");
        expectPage("scroll");
        up();
        expectPage("app-settings");
        clickText("Save app settings");
        check(
                session.prefs.getInt("app." + session.activePackage, -1) == session.rotation,
                "Save remains usable in app-specific options");
        clickText("Reset app settings");
        check(
                !session.prefs.contains("app." + session.activePackage),
                "Reset remains usable in app-specific options");
        await(() -> !session.busy, "app reset configuration settles before next launcher");
        up();
        expectPage("settings");
        close();
    }

    private void statusRecoveryFlow() throws Exception {
        View controller = phone.getWindow().getDecorView();
        ControllerStyle.StatusCard card = controller.findViewWithTag("controller-status-card");
        ShizukuSetup.State before = session.shizukuState;
        try {
            main(
                    () -> {
                        session.shizukuState = ShizukuSetup.State.MISSING;
                        card.refresh();
                    });
            await(
                    () -> {
                        View action = controller.findViewWithTag("phone-shizuku-recovery");
                        return action.isShown()
                                && action.getGlobalVisibleRect(new Rect())
                                && action.getHeight() > 0;
                    },
                    "missing-manager fixture keeps install prompt visible with status collapsed");
            check(
                    controller.findViewWithTag("phone-status-details").getVisibility() == View.GONE,
                    "install prompt does not require expanding details");
            shot("phone-shizuku-install-prompt");
            tap(controller.findViewWithTag("phone-shizuku-recovery"));
            expectPage("shizuku");
            check(
                    !session.prefs.getBoolean("statusExpanded", false),
                    "recovery button does not toggle disclosure");
            check(
                    session.shizukuState == ShizukuSetup.State.CONNECTED,
                    "opening setup rechecks real installed/running manager after stale state fixture");
            close();
        } finally {
            main(
                    () -> {
                        session.shizukuState = before;
                        card.refresh();
                    });
        }
        tap(card.monitor);
        await(
                () -> controller.findViewWithTag("phone-status-details").isShown(),
                "expanded status renders");
        shot("phone-status-expanded");
        tap(controller.findViewWithTag("phone-status-cue"));
        await(
                () ->
                        controller.findViewWithTag("phone-status-details").getVisibility()
                                == View.GONE,
                "status restores collapsed after evidence capture");
    }

    private void setupGuideFlow() throws Exception {
        main(() -> description(phone.getWindow().getDecorView(), "Settings").performClick());
        clickText("User guide");
        expectPage("user-guide");
        for (int i = 0; i < 8; i++)
            check(
                    root().findViewWithTag("guide-section-" + i) != null,
                    "offline phone guide section " + i);
        main(
                () ->
                        ((ScrollView) root().findViewWithTag("phone-menu-scroll"))
                                .fullScroll(View.FOCUS_DOWN));
        await(
                () -> {
                    ScrollView scroll = root().findViewWithTag("phone-menu-scroll");
                    return scroll.getScrollY()
                            == Math.max(0, scroll.getChildAt(0).getHeight() - scroll.getHeight());
                },
                "guide bottom settles");
        int position = ((ScrollView) root().findViewWithTag("phone-menu-scroll")).getScrollY();
        shot("phone-user-guide");
        clickText("Protected screen help");
        expectPage("protected-help");
        up();
        expectPage("user-guide");
        await(
                () ->
                        ((ScrollView) root().findViewWithTag("phone-menu-scroll")).getScrollY()
                                == position,
                "guide scroll survives related help return");
        clickText("Shizuku setup");
        expectPage("shizuku");
        check(
                ((TextView) root().findViewWithTag("phone-shizuku-state"))
                        .getText()
                        .toString()
                        .contains("Connected"),
                "setup shows actual connected service");
        shot("phone-shizuku-setup");
        up();
        expectPage("user-guide");
        up();
        expectPage("settings");
        close();
        check(
                !session.prefs.getBoolean("statusExpanded", true),
                "menu round trips preserve collapsed status");
    }

    String run() throws Exception {
        Map<String, ?> preferences = new HashMap<>(session.prefs.getAll());
        modernRotation = DeviceTestSupport.shell(runner, "wm help").contains("  user-rotation ");
        String previousRotationMode =
                modernRotation
                        ? DeviceTestSupport.shell(runner, "wm user-rotation -d 0").trim()
                        : null;
        int originalRotation = session.rotation;
        int
                userRotation =
                        android.provider.Settings.System.getInt(
                                phone.getContentResolver(),
                                android.provider.Settings.System.USER_ROTATION,
                                0),
                autoRotation =
                        android.provider.Settings.System.getInt(
                                phone.getContentResolver(),
                                android.provider.Settings.System.ACCELEROMETER_ROTATION,
                                0);
        try {
            check(
                    phone.getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                    "phone controller requests portrait");
            int rotation = session.rotation;
            phoneRotation("lock 1");
            await(
                    () -> {
                        try {
                            return modernRotation
                                    ? DeviceTestSupport.shell(runner, "wm user-rotation -d 0")
                                            .trim()
                                            .equals("lock 1")
                                    : android.provider.Settings.System.getInt(
                                                    phone.getContentResolver(),
                                                    android.provider.Settings.System.USER_ROTATION,
                                                    0)
                                            == 1;
                        } catch (Exception error) {
                            throw new RuntimeException(error);
                        }
                    },
                    "landscape rotation requested on phone display only");
            await(
                    () ->
                            phone.getResources().getConfiguration().orientation
                                    == android.content.res.Configuration.ORIENTATION_PORTRAIT,
                    "phone stays portrait when device rotates");
            check(session.rotation == rotation, "phone rotation does not rotate monitor");
            phoneRotation("lock 0");
            main(() -> description(phone.getWindow().getDecorView(), "Settings").performClick());
            inspect("settings");
            main(
                    () -> {
                        android.widget.TextView version =
                                root().findViewWithTag("settings-app-version");
                        try {
                            android.content.pm.PackageInfo installed =
                                    phone.getPackageManager()
                                            .getPackageInfo(phone.getPackageName(), 0);
                            check(
                                    version != null
                                            && version.isShown()
                                            && version.getText()
                                                    .toString()
                                                    .contains(installed.versionName)
                                            && version.getText()
                                                    .toString()
                                                    .contains(
                                                            "Build "
                                                                    + installed
                                                                            .getLongVersionCode()),
                                    "phone Settings shows installed version and build immediately");
                        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
                            throw new AssertionError(error);
                        }
                    });
            main(
                    () -> {
                        ScrollView scroll = root().findViewWithTag("phone-menu-scroll");
                        check(scroll != null, "phone Settings content scrolls vertically");
                        scroll.fullScroll(View.FOCUS_DOWN);
                    });
            Thread.sleep(150);
            main(
                    () -> {
                        Rect rect = new Rect();
                        check(
                                text(root(), "Reset monitor profile").getGlobalVisibleRect(rect),
                                "last Settings action reachable by scrolling");
                        View header = root().findViewWithTag("phone-menu-close");
                        check(
                                header.getGlobalVisibleRect(rect),
                                "Close remains visible after Settings scroll");
                    });
            shot("phone-settings");
            close();
            ComponentName test =
                    new ComponentName(runner.getTargetContext(), InputTestActivity.class);
            Instrumentation.ActivityMonitor testWindow =
                    runner.addMonitor(InputTestActivity.class.getName(), null, false);
            try {
                main(() -> session.launch(test, 0));
                await(
                        () -> {
                            boolean[] ready = {false};
                            main(
                                    () -> {
                                        Activity target = testWindow.getLastActivity();
                                        ready[0] =
                                                !session.busy
                                                        && session.rotation == 0
                                                        && session.hasDesktopApp()
                                                        && target != null
                                                        && !target.isDestroyed()
                                                        && target.getDisplay().getDisplayId()
                                                                == session.id
                                                        && target.getWindow()
                                                                .getDecorView()
                                                                .isShown()
                                                        && target.getWindow()
                                                                        .getDecorView()
                                                                        .getWidth()
                                                                > 0
                                                        && target.getWindow()
                                                                        .getDecorView()
                                                                        .getHeight()
                                                                > 0;
                                    });
                            return ready[0];
                        },
                        "phone submenu native test window ready on owned display");
            } finally {
                runner.removeMonitor(testWindow);
            }
            touchpadActions();
            open("Rotate");
            inspect("rotation");
            main(
                    () -> {
                        for (int i = 0; i < 4; i++) {
                            RadioButton choice = root().findViewWithTag("phone-rotation-" + i);
                            card(choice, "phone rotation " + i);
                            check(
                                    choice.isChecked() == (i == session.rotation),
                                    "phone current rotation selected " + i);
                        }
                    });
            shot("phone-rotation");
            main(() -> root().findViewWithTag("phone-rotation-1").performClick());
            await(
                    () -> !session.busy && session.rotation == 1 && phone.controllerMenu() == null,
                    "phone rotation selection applies and closes");
            open("Scroll");
            inspect("scroll");
            main(
                    () -> {
                        for (String tag :
                                new String[] {
                                    "phone-swipe",
                                    "phone-wheel",
                                    "phone-speed-0",
                                    "phone-speed-1",
                                    "phone-speed-2"
                                }) card(root().findViewWithTag(tag), tag);
                        root().findViewWithTag("phone-swipe").performClick();
                        check(
                                phone.controllerMenu() != null
                                        && root().findViewWithTag("phone-swipe") != null
                                        && root().findViewWithTag("phone-swipe").isShown(),
                                "scroll mode applies while combined settings stay open");
                        root().findViewWithTag("phone-speed-0").performClick();
                        check(
                                phone.controllerMenu() != null
                                        && root().findViewWithTag("phone-speed-0") != null
                                        && root().findViewWithTag("phone-speed-0").isShown(),
                                "scroll speed applies while combined settings stay open");
                        check(
                                session.swipeScrolling() && session.scrollSpeed() == .2f,
                                "phone scrolling selections update app settings");
                    });
            shot("phone-scroll");
            close();
            open("Scroll");
            main(
                    () ->
                            check(
                                    ((RadioButton) root().findViewWithTag("phone-swipe"))
                                                    .isChecked()
                                            && ((RadioButton)
                                                            root().findViewWithTag("phone-speed-0"))
                                                    .isChecked(),
                                    "phone scrolling selection persists on reopen"));
            close();
            navigationFlow();
            setupGuideFlow();
            statusRecoveryFlow();
            open("Apps");
            inspect("apps");
            await(
                    () -> {
                        boolean[] ready = {false};
                        main(
                                () -> {
                                    ListView apps = root().findViewWithTag("phone-app-list");
                                    ready[0] = apps != null && apps.getCount() > 3;
                                });
                        return ready[0];
                    },
                    "async phone app catalog ready");
            EditText[] editor = {null};
            int[] count = {0}, beforeHeight = {0};
            main(
                    () -> {
                        editor[0] = root().findViewWithTag("phone-app-search");
                        ListView list = root().findViewWithTag("phone-app-list");
                        count[0] = list.getCount();
                        beforeHeight[0] = root().getHeight();
                        check(count[0] > 3, "phone launcher contains installed apps");
                        card(list.getChildAt(0), "phone app entry");
                        check(
                                ((TextView) list.getChildAt(0)).getCompoundDrawables()[0] != null,
                                "phone app entry keeps app icon");
                        check(
                                ((TextView) list.getChildAt(0)).getText()
                                        instanceof android.text.Spanned,
                                "phone app caption shares Settings subtitle styling");
                        editor[0].setText("DexDroid input test");
                    });
            Thread.sleep(150);
            main(
                    () ->
                            check(
                                    ((ListView) root().findViewWithTag("phone-app-list")).getCount()
                                            == 1,
                                    "phone Apps search filters installed list"));
            tap(editor[0]);
            await(
                    () ->
                            root().getRootWindowInsets() != null
                                    && root().getRootWindowInsets()
                                            .isVisible(WindowInsets.Type.ime()),
                    "phone Apps uses Android keyboard; editorFocus="
                            + editor[0].hasFocus()
                            + " windowFocus="
                            + editor[0].hasWindowFocus());
            main(
                    () -> {
                        Rect rect = new Rect();
                        check(
                                root().findViewWithTag("phone-menu-close")
                                        .getGlobalVisibleRect(rect),
                                "phone Apps Close reachable with keyboard");
                        check(
                                root().findViewWithTag("app.dev.dexdroid")
                                        .getGlobalVisibleRect(rect),
                                "phone Apps result reachable with keyboard");
                    });
            shot("phone-apps-keyboard");
            DeviceTestSupport.shell(runner, "input -d 0 keyevent 4");
            await(
                    () ->
                            phone.controllerMenu() != null
                                    && root().findViewWithTag("phone-menu-apps") != null
                                    && !root().getRootWindowInsets()
                                            .isVisible(WindowInsets.Type.ime()),
                    "first phone Back hides IME and retains Apps");
            main(
                    () -> {
                        ListView list = root().findViewWithTag("phone-app-list");
                        list.performItemClick(list.getChildAt(0), 0, list.getItemIdAtPosition(0));
                    });
            await(
                    () -> phone.controllerMenu() == null && !session.busy,
                    "phone Apps launches selected result");
            open("Apps");
            main(
                    () ->
                            ((EditText) root().findViewWithTag("phone-app-search"))
                                    .setText("DexDroid input test"));
            Thread.sleep(100);
            main(
                    () -> {
                        ListView list = root().findViewWithTag("phone-app-list");
                        check(
                                list.getOnItemLongClickListener()
                                        .onItemLongClick(
                                                list,
                                                list.getChildAt(0),
                                                0,
                                                list.getItemIdAtPosition(0)),
                                "phone app long press opens orientation");
                    });
            inspect("app-rotation");
            shot("phone-app-rotation");
            up();
            expectPage("apps");
            check(
                    ((EditText) root().findViewWithTag("phone-app-search"))
                            .getText()
                            .toString()
                            .equals("DexDroid input test"),
                    "Apps query survives orientation Up");
            await(
                    () -> ((ListView) root().findViewWithTag("phone-app-list")).getChildCount() > 0,
                    "restored Apps result available");
            main(
                    () -> {
                        ListView list = root().findViewWithTag("phone-app-list");
                        list.getOnItemLongClickListener()
                                .onItemLongClick(
                                        list, list.getChildAt(0), 0, list.getItemIdAtPosition(0));
                    });
            expectPage("app-rotation");
            int saved = session.prefs.getInt("app.dev.dexdroid", -1);
            clickText("Portrait 270\u00b0");
            await(
                    () -> phone.controllerMenu() == null && !session.busy && session.rotation == 3,
                    "phone held app applies one-time orientation");
            check(
                    session.prefs.getInt("app.dev.dexdroid", -1) == saved,
                    "phone one-time orientation keeps saved rotation");
            main(() -> description(phone.getWindow().getDecorView(), "Settings").performClick());
            clickText("Pointer speed");
            inspect("pointer-speed");
            main(() -> card(root().findViewWithTag("phone-pointer-1"), "phone pointer speed"));
            close();
            main(() -> description(phone.getWindow().getDecorView(), "Settings").performClick());
            clickText("Resolution and DPI");
            inspect("display");
            close();
            main(() -> description(phone.getWindow().getDecorView(), "Settings").performClick());
            clickText("Diagnostic log");
            inspect("diagnostics");
            close();
            main(
                    () -> {
                        ScrollView controller =
                                phone.getWindow()
                                        .getDecorView()
                                        .findViewWithTag("phone-controller-scroll");
                        check(
                                controller != null && controller.isFillViewport(),
                                "main phone controller supports vertical overflow scrolling");
                        View pad = controller.findViewWithTag("touchpad");
                        check(
                                pad.getHeight()
                                        >= 230 * pad.getResources().getDisplayMetrics().density,
                                "main touchpad retains usable height");
                    });
            touchpadDrag();
            return results.toString();
        } catch (Throwable error) {
            try {
                shot("phone-menu-failure");
            } catch (Throwable capture) {
                error.addSuppressed(capture);
            }
            throw new RuntimeException(results + "FAIL phone menu acceptance", error);
        } finally {
            phoneRotation(
                    modernRotation
                            ? previousRotationMode
                            : autoRotation == 1 ? "free" : "lock " + userRotation);
            main(
                    () -> {
                        if (phone.controllerMenu() != null) phone.controllerMenu().dismiss();
                        android.content.SharedPreferences.Editor edit =
                                session.prefs.edit().clear();
                        for (Map.Entry<String, ?> e : preferences.entrySet()) {
                            Object v = e.getValue();
                            String k = e.getKey();
                            if (v instanceof Boolean) edit.putBoolean(k, (Boolean) v);
                            else if (v instanceof Integer) edit.putInt(k, (Integer) v);
                            else if (v instanceof Float) edit.putFloat(k, (Float) v);
                            else if (v instanceof String) edit.putString(k, (String) v);
                            else if (v instanceof Long) edit.putLong(k, (Long) v);
                        }
                        edit.commit();
                        if (!session.busy) session.rotate(originalRotation, false);
                    });
        }
    }
}
