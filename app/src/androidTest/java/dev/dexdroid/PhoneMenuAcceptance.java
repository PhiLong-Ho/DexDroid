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
                    View found = text(phone.getWindow().getDecorView(), button);
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
                    check(
                            close instanceof ImageButton && close.isShown(),
                            "phone submenu fixed icon Close: " + page);
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
                    int[] point = new int[2];
                    view.getLocationOnScreen(point);
                    rect.set(
                            point[0],
                            point[1],
                            point[0] + view.getWidth(),
                            point[1] + view.getHeight());
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
            MotionEvent.class.getMethod("setDisplayId", int.class).invoke(e, 0);
            check(
                    runner.getUiAutomation().injectInputEvent(e, true),
                    "real phone menu tap " + action);
            e.recycle();
        }
        Thread.sleep(150);
    }

    private void phoneRotation(String mode) throws Exception {
        try (android.os.ParcelFileDescriptor fd =
                        runner.getUiAutomation()
                                .executeShellCommand(
                                        "wm set-user-rotation "
                                                + mode.split(" ")[0]
                                                + " -d 0"
                                                + (mode.startsWith("lock ")
                                                        ? " " + mode.substring(5)
                                                        : ""));
                FileInputStream input = new FileInputStream(fd.getFileDescriptor())) {
            while (input.read() != -1) {}
        }
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
            MotionEvent.class.getMethod("setDisplayId", int.class).invoke(event, 0);
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

    String run() throws Exception {
        Map<String, ?> preferences = new HashMap<>(session.prefs.getAll());
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
            check(
                    android.provider.Settings.System.getInt(
                                    phone.getContentResolver(),
                                    android.provider.Settings.System.USER_ROTATION,
                                    0)
                            == 1,
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
            main(() -> session.launch(test, 0));
            await(() -> !session.busy && session.rotation == 0, "phone submenu test app ready");
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
                        root().findViewWithTag("phone-speed-0").performClick();
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
            open("Apps");
            inspect("apps");
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
                    "phone Apps uses Android keyboard");
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
            throw new RuntimeException(results + "FAIL phone menu acceptance", error);
        } finally {
            phoneRotation(autoRotation == 1 ? "free" : "lock " + userRotation);
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
