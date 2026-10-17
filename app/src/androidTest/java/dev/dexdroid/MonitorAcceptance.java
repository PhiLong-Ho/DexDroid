package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Injects real physical-monitor events; checks external app and native shell outcomes. */
final class MonitorAcceptance {
    private final Instrumentation runner;
    private SessionService session;
    private ServiceConnection binding;
    private Activity phone;
    private boolean bound;
    private Map<String, ?> preferences;
    private int originalRotation;
    private long down;
    private final StringBuilder results = new StringBuilder();

    MonitorAcceptance(Instrumentation runner) {
        this.runner = runner;
    }

    private void main(Runnable action) {
        runner.runOnMainSync(action);
    }

    private Display physicalMonitor() {
        Display[] captured = {null};
        Runnable snapshot =
                () -> {
                    Display display = session.external;
                    if (display == null || !display.isValid())
                        throw new AssertionError("Physical monitor unavailable on main thread");
                    captured[0] = display;
                };
        if (Looper.myLooper() == Looper.getMainLooper()) snapshot.run();
        else main(snapshot);
        return captured[0];
    }

    private void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        results.append("PASS ").append(label).append('\n');
    }

    private void await(BooleanSupplier predicate, String label) throws Exception {
        long until = SystemClock.uptimeMillis() + 10000;
        while (!predicate.getAsBoolean() && SystemClock.uptimeMillis() < until) Thread.sleep(50);
        check(predicate.getAsBoolean(), label);
    }

    private void restore() {
        if (preferences == null) return;
        android.content.SharedPreferences.Editor edit = session.prefs.edit().clear();
        for (Map.Entry<String, ?> entry : preferences.entrySet()) {
            Object v = entry.getValue();
            String k = entry.getKey();
            if (v instanceof Boolean) edit.putBoolean(k, (Boolean) v);
            else if (v instanceof Integer) edit.putInt(k, (Integer) v);
            else if (v instanceof Float) edit.putFloat(k, (Float) v);
            else if (v instanceof String) edit.putString(k, (String) v);
            else if (v instanceof Long) edit.putLong(k, (Long) v);
        }
        edit.commit();
    }

    private void event(int action, float[][] points) throws Exception {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) down = now;
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[points.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[points.length];
        for (int i = 0; i < points.length; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = i + 7;
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = points[i][0];
            coords[i].y = points[i][1];
            coords[i].pressure = 1;
        }
        MotionEvent e =
                MotionEvent.obtain(
                        down,
                        now,
                        action,
                        points.length,
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
            check(
                    DeviceTestSupport.injectOnDisplay(e, physicalMonitor().getDisplayId()),
                    "physical monitor event " + action);
        } finally {
            e.recycle();
        }
    }

    private float[] physical(float x, float y) {
        Display.Mode mode = physicalMonitor().getMode();
        return Geometry.map(
                x / session.width(),
                y / session.height(),
                session.rotation,
                mode.getPhysicalWidth(),
                mode.getPhysicalHeight(),
                session.width(),
                session.height());
    }

    private void injectedContactCleanup() throws Exception {
        float[] point = physical(session.width() * .5f, session.height() * .6f);
        int cancelled = InputTestActivity.cancelEvents;
        event(MotionEvent.ACTION_DOWN, new float[][] {point});
        event(
                MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new float[][] {point, {point[0] + 30, point[1] + 30}});
        event(
                MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new float[][] {point, {point[0] + 30, point[1] + 30}});
        DeviceTestSupport.cancelInjectedMonitor(runner);
        await(
                () -> InputTestActivity.cancelEvents > cancelled,
                "fixture cleanup cancels remaining nonzero pointer after pointer-up");
        long previousUp = InputTestActivity.lastUpDownTime;
        event(MotionEvent.ACTION_DOWN, new float[][] {point});
        event(MotionEvent.ACTION_UP, new float[][] {point});
        await(
                () -> InputTestActivity.lastUpDownTime > previousUp,
                "fresh native touch accepted after exact fixture cancellation");
    }

    private void swipe(float x, float y, float endX, float endY) throws Exception {
        float[] start = physical(x, y), end = physical(endX, endY);
        DeviceTestSupport.usableMonitorSwipe(session, start, end);
        event(0, new float[][] {start});
        Thread.sleep(30);
        event(2, new float[][] {end});
        event(1, new float[][] {end});
        Thread.sleep(100);
    }

    private void tap(float x, float y) throws Exception {
        event(0, new float[][] {{x, y}});
        event(1, new float[][] {{x, y}});
        Thread.sleep(100);
    }

    private void click(String tag) throws Exception {
        Rect rect = new Rect();
        main(
                () -> {
                    View root = session.monitorShellView();
                    View view = root == null ? null : root.findViewWithTag(tag);
                    check(view != null && view.isShown(), "monitor control exists: " + tag);
                    if (!view.getGlobalVisibleRect(rect)) {
                        ViewParent parent = view.getParent();
                        while (parent instanceof View) {
                            if (parent instanceof android.widget.ScrollView) {
                                Rect target = new Rect();
                                view.getDrawingRect(target);
                                ((android.widget.ScrollView) parent)
                                        .offsetDescendantRectToMyCoords(view, target);
                                ((android.widget.ScrollView) parent).scrollTo(0, target.top);
                                break;
                            }
                            parent = parent.getParent();
                        }
                    }
                });
        Thread.sleep(50);
        main(
                () ->
                        check(
                                session.monitorShellView()
                                        .findViewWithTag(tag)
                                        .getGlobalVisibleRect(rect),
                                "monitor control visible: " + tag));
        tap(rect.exactCenterX(), rect.exactCenterY());
    }

    private void menuCard(View view, String label) {
        check(
                view instanceof android.widget.TextView
                        && view.getBackground() instanceof android.graphics.drawable.RippleDrawable,
                "monitor Settings card: " + label);
        android.widget.TextView text = (android.widget.TextView) view;
        float density = view.getResources().getDisplayMetrics().density;
        check(
                text.getMinHeight() >= 61 * density
                        && text.getCurrentTextColor() == android.graphics.Color.WHITE,
                "monitor Settings row sizing and contrast: " + label);
        android.graphics.drawable.Drawable background =
                ((android.graphics.drawable.RippleDrawable) view.getBackground()).getDrawable(0);
        check(
                background instanceof android.graphics.drawable.GradientDrawable
                        && Math.abs(
                                        ((android.graphics.drawable.GradientDrawable) background)
                                                                .getCornerRadius()
                                                        / density
                                                - 8)
                                < 1,
                "monitor Settings rounded row: " + label);
    }

    private void menuStyle(String page) {
        View root = session.monitorShellView();
        if (page.equals("closed") || page.equals("controls")) return;
        View panel = root.findViewWithTag("monitor-panel");
        float density = panel.getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable surface =
                (android.graphics.drawable.GradientDrawable) panel.getBackground();
        check(
                surface.getColor().getDefaultColor() == android.graphics.Color.rgb(18, 18, 18)
                        && Math.abs(surface.getCornerRadius() / density - 22) < 1,
                "monitor submenu uses shared Settings surface: " + page);
        if (page.equals("rotation")) {
            for (int i = 0; i < 4; i++) {
                android.widget.RadioButton button = root.findViewWithTag("monitor-rotation-" + i);
                menuCard(button, "rotation " + i);
                check(
                        button.isChecked() == (i == session.rotation),
                        "monitor current orientation selected " + i);
            }
        }
        if (page.equals("scroll") && !session.activePackage.isEmpty()) {
            for (String tag :
                    new String[] {
                        "monitor-swipe",
                        "monitor-wheel",
                        "monitor-speed-0",
                        "monitor-speed-1",
                        "monitor-speed-2"
                    }) menuCard(root.findViewWithTag(tag), tag);
            check(
                    ((android.widget.RadioButton)
                                    root.findViewWithTag(
                                            session.swipeScrolling()
                                                    ? "monitor-swipe"
                                                    : "monitor-wheel"))
                            .isChecked(),
                    "monitor current scroll mode selected");
        }
        if (page.equals("home")) {
            android.widget.ListView list = root.findViewWithTag("monitor-app-list");
            if (list.getChildCount() > 0) {
                menuCard(list.getChildAt(0), "app entry");
                check(
                        ((android.widget.TextView) list.getChildAt(0)).getCompoundDrawables()[0]
                                != null,
                        "monitor app card retains app icon");
                check(
                        ((android.widget.TextView) list.getChildAt(0)).getText()
                                instanceof android.text.Spanned,
                        "monitor app caption shares Settings subtitle styling");
            }
        }
    }

    private void page(String value) throws Exception {
        await(
                () -> session.monitorShellPage().equals(value) && !session.monitorShellAnimating(),
                "monitor page/animation settled " + value);
        if (!value.equals("closed"))
            main(
                    () -> {
                        check(
                                MenuTextAssertions.check(session.monitorShellView()) > 0,
                                "decoded menu labels and available font glyphs: " + value);
                        menuStyle(value);
                        results.append(
                                MenuHeaderAssertions.inspect(
                                        session.monitorShellView(), "monitor"));
                        if (value.equals("settings")) {
                            android.widget.TextView version =
                                    session.monitorShellView()
                                            .findViewWithTag("settings-app-version");
                            try {
                                android.content.pm.PackageInfo installed =
                                        runner.getTargetContext()
                                                .getPackageManager()
                                                .getPackageInfo("dev.dexdroid", 0);
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
                                        "monitor Settings shows installed version and build immediately");
                            } catch (
                                    android.content.pm.PackageManager.NameNotFoundException error) {
                                throw new AssertionError(error);
                            }
                        }
                    });
    }

    private android.widget.Button phoneButton(View root, String label) {
        if (root instanceof android.widget.Button
                && label.contentEquals(((android.widget.Button) root).getText()))
            return (android.widget.Button) root;
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                android.widget.Button found = phoneButton(((ViewGroup) root).getChildAt(i), label);
                if (found != null) return found;
            }
        return null;
    }

    private void parity() {
        main(
                () -> {
                    View root = session.monitorShellView();
                    check(
                            !root.isForceDarkAllowed(),
                            "monitor overlay disables automatic color inversion");
                    check(root.findViewWithTag("touchpad") == null, "monitor omits phone touchpad");
                    check(root.findViewWithTag("monitor-resume") == null, "no Resume app button");
                    View close = root.findViewWithTag("monitor-close"),
                            settings = root.findViewWithTag("monitor-settings");
                    check(
                            close instanceof android.widget.ImageButton
                                    && settings instanceof android.widget.ImageButton
                                    && settings.getLeft() < close.getLeft(),
                            "Settings icon immediately left of Close icon");
                    String[] tags = {"monitor-apps", "monitor-rotate", "monitor-layout"},
                            labels = {"Apps", "Rotate", "Layout"};
                    android.widget.LinearLayout first = root.findViewWithTag("monitor-row-1");
                    check(
                            root.findViewWithTag("monitor-row-2") == null,
                            "monitor has one primary action row");
                    for (int i = 0; i < tags.length; i++) {
                        android.widget.Button monitor = root.findViewWithTag(tags[i]),
                                phone =
                                        phoneButton(
                                                this.phone.getWindow().getDecorView(), labels[i]);
                        check(
                                monitor != null
                                        && phone != null
                                        && monitor.getCompoundDrawables()[1] != null,
                                "shared icon control: " + labels[i]);
                        float md = monitor.getResources().getDisplayMetrics().density,
                                pd = phone.getResources().getDisplayMetrics().density;
                        check(
                                Math.abs(monitor.getHeight() / md - phone.getHeight() / pd) < 1
                                        && Math.abs(
                                                        monitor.getTextSize()
                                                                        / monitor.getResources()
                                                                                .getDisplayMetrics()
                                                                                .scaledDensity
                                                                - phone.getTextSize()
                                                                        / phone.getResources()
                                                                                .getDisplayMetrics()
                                                                                .scaledDensity)
                                                < .1,
                                "phone/monitor size and typography match: " + labels[i]);
                        check(
                                first.getChildAt(i) == monitor,
                                "phone-style button order: " + labels[i]);
                        check(
                                monitor.getCurrentTextColor() == phone.getCurrentTextColor(),
                                "phone/monitor text contrast matches: " + labels[i]);
                    }
                    check(
                            root.findViewWithTag("controller-status-card")
                                    instanceof ControllerStyle.StatusCard,
                            "shared phone/monitor status card");
                    View stop = root.findViewWithTag("monitor-stop"),
                            expansion = root.findViewWithTag("monitor-status-expand");
                    check(
                            stop instanceof android.widget.ImageButton
                                    && root.findViewWithTag("monitor-app-settings") == null,
                            "status retains desktop action without app-options shortcut");
                    results.append(StatusDisclosureAssertions.inspect(root, "monitor"));
                    for (View action : new View[] {stop, expansion}) {
                        float d = action.getResources().getDisplayMetrics().density;
                        check(
                                action.getWidth() >= 47 * d
                                        && action.getHeight() >= 47 * d
                                        && action.getContentDescription() != null,
                                "status icon has 48dp accessible target");
                    }
                });
    }

    private void navigationFlow(int rotation) throws Exception {
        check(
                session.monitorShellView().findViewWithTag("monitor-status-details").getVisibility()
                        == View.GONE,
                "shared collapsed status on monitor " + rotation);
        click("monitor-status-chevron");
        await(
                () ->
                        session.monitorShellView()
                                .findViewWithTag("monitor-status-details")
                                .isShown(),
                "expanded monitor details");
        textScreenshot("status-expanded-r" + rotation);
        check(
                session.prefs.getBoolean("statusExpanded", false),
                "expansion preference shared " + rotation);
        click("monitor-status-expand");
        click("monitor-settings");
        page("settings");
        click("monitor-user-guide");
        page("user-guide");
        for (int i = 0; i < 8; i++)
            check(
                    session.monitorShellView().findViewWithTag("guide-section-" + i) != null,
                    "offline monitor guide section " + i);
        main(
                () ->
                        ((android.widget.ScrollView)
                                        session.monitorShellView()
                                                .findViewWithTag("monitor-menu-scroll"))
                                .fullScroll(View.FOCUS_DOWN));
        await(
                () -> {
                    android.widget.ScrollView scroll =
                            session.monitorShellView().findViewWithTag("monitor-menu-scroll");
                    return scroll.getScrollY()
                            == Math.max(0, scroll.getChildAt(0).getHeight() - scroll.getHeight());
                },
                "monitor guide bottom settles");
        int guidePosition =
                ((android.widget.ScrollView)
                                session.monitorShellView().findViewWithTag("monitor-menu-scroll"))
                        .getScrollY();
        textScreenshot("user-guide-r" + rotation);
        ShizukuSetup.State previousSetupState = session.shizukuState;
        try {
            main(() -> session.shizukuState = ShizukuSetup.State.MISSING);
            click("monitor-guide-shizuku");
            page("shizuku");
            check(
                    session.shizukuState == ShizukuSetup.State.CONNECTED,
                    "monitor setup refreshes stale manager state " + rotation);
        } finally {
            main(() -> session.shizukuState = previousSetupState);
        }
        textScreenshot("shizuku-r" + rotation);
        click("monitor-up");
        page("user-guide");
        await(
                () ->
                        ((android.widget.ScrollView)
                                                session.monitorShellView()
                                                        .findViewWithTag("monitor-menu-scroll"))
                                        .getScrollY()
                                == guidePosition,
                "monitor guide scroll retained " + rotation);
        click("monitor-guide-protected");
        page("protected-help");
        click("monitor-up");
        page("user-guide");
        click("monitor-up");
        page("settings");
        click("monitor-up");
        page("controls");
        click("monitor-settings");
        page("settings");
        click("monitor-pointer-speed");
        page("pointer-speed");
        int selected = session.prefs.getFloat("speed", 1.6f) < 1.3f ? 1 : 0;
        click("monitor-pointer-" + selected);
        page("settings");
        check(
                session.prefs.getFloat("speed", 0) == (selected == 0 ? 1f : 1.6f),
                "monitor pointer choice applies and returns to Settings " + rotation);
        for (String[] child :
                new String[][] {
                    {"monitor-profiles", "profiles"},
                    {"monitor-display", "display"},
                    {"monitor-diagnostics", "diagnostics"},
                    {"monitor-protected-settings", "protected-help"}
                }) {
            click(child[0]);
            page(child[1]);
            main(
                    () -> {
                        View root = session.monitorShellView(),
                                up = root.findViewWithTag("monitor-up"),
                                title = root.findViewWithTag("monitor-title"),
                                close = root.findViewWithTag("monitor-close");
                        check(
                                up != null
                                        && up.getLeft() < title.getLeft()
                                        && title.getLeft() < close.getLeft(),
                                "monitor nested header has Up/title/Close " + rotation);
                    });
            if (child[1].equals("diagnostics"))
                textScreenshot("navigation-diagnostics-r" + rotation);
            click("monitor-up");
            page("settings");
        }
        main(
                () ->
                        ((android.widget.ScrollView)
                                        session.monitorShellView()
                                                .findViewWithTag("monitor-menu-scroll"))
                                .fullScroll(View.FOCUS_DOWN));
        await(
                () -> {
                    android.widget.ScrollView view =
                            session.monitorShellView().findViewWithTag("monitor-menu-scroll");
                    return view.getScrollY()
                            == Math.max(
                                    0,
                                    view.getChildAt(0).getHeight()
                                            - view.getHeight()
                                            + view.getPaddingTop()
                                            + view.getPaddingBottom());
                },
                "monitor parent smooth scroll settles");
        int before =
                ((android.widget.ScrollView)
                                session.monitorShellView().findViewWithTag("monitor-menu-scroll"))
                        .getScrollY();
        check(before > 0, "monitor Settings can scroll before child");
        click("monitor-diagnostics");
        page("diagnostics");
        main(() -> session.key(KeyEvent.KEYCODE_BACK));
        page("settings");
        await(
                () ->
                        ((android.widget.ScrollView)
                                                session.monitorShellView()
                                                        .findViewWithTag("monitor-menu-scroll"))
                                        .getScrollY()
                                == before,
                "monitor parent scroll retained on hardware Back " + rotation);
        click("monitor-up");
        page("controls");
        click("monitor-settings");
        page("settings");
        click("monitor-app-settings");
        page("app-settings");
        textScreenshot("app-settings-r" + rotation);
        click("monitor-scroll");
        page("scroll");
        boolean oldSwipe = session.swipeScrolling();
        float oldSpeed = session.scrollSpeed();
        click(oldSwipe ? "monitor-wheel" : "monitor-swipe");
        page("scroll");
        check(
                session.swipeScrolling() != oldSwipe,
                "combined monitor page stays open with mode applied " + rotation);
        int speedChoice = oldSpeed < .35f ? 1 : 0;
        click("monitor-speed-" + speedChoice);
        page("scroll");
        check(
                session.scrollSpeed() == (speedChoice == 0 ? .2f : .5f),
                "combined monitor page stays open with speed applied " + rotation);
        click(oldSwipe ? "monitor-swipe" : "monitor-wheel");
        click("monitor-speed-" + (oldSpeed < .35f ? 0 : oldSpeed < .75f ? 1 : 2));
        click("monitor-up");
        page("app-settings");
        click("monitor-up");
        page("settings");
        click("monitor-close");
        page("closed");
        controlsGesture();
        check(
                session.monitorShellView().findViewWithTag("monitor-up") == null,
                "fresh Controls root has no stale submenu parents " + rotation);
    }

    private void noChrome() {
        main(
                () -> {
                    View root = session.monitorShellView();
                    check(
                            root.findViewWithTag("monitor-apps") == null
                                    && root.findViewWithTag("monitor-controls") == null
                                    && root.findViewWithTag("monitor-resume") == null
                                    && root.findViewWithTag("monitor-panel") == null,
                            "closed app has no persistent shell controls");
                });
    }

    private void renderedContrast() throws Exception {
        Rect button = new Rect();
        main(
                () ->
                        check(
                                session.monitorShellView()
                                        .findViewWithTag("monitor-stop")
                                        .getGlobalVisibleRect(button),
                                "Stop render target visible"));
        Display.Mode mode = physicalMonitor().getMode();
        android.graphics.Bitmap bitmap =
                android.graphics.Bitmap.createBitmap(
                        mode.getPhysicalWidth(),
                        mode.getPhysicalHeight(),
                        android.graphics.Bitmap.Config.ARGB_8888);
        CountDownLatch captured = new CountDownLatch(1);
        int[] result = {-1};
        try {
            main(
                    () ->
                            PixelCopy.request(
                                    session.monitorWindow(),
                                    bitmap,
                                    value -> {
                                        result[0] = value;
                                        captured.countDown();
                                    },
                                    new Handler(Looper.getMainLooper())));
            check(
                    captured.await(5, TimeUnit.SECONDS) && result[0] == PixelCopy.SUCCESS,
                    "physical monitor rendering captured");
            int dark = 0, light = 0;
            for (int y = button.top; y < button.bottom; y++)
                for (int x = button.left; x < button.right; x++) {
                    int pixel = bitmap.getPixel(x, y),
                            r = android.graphics.Color.red(pixel),
                            g = android.graphics.Color.green(pixel),
                            b = android.graphics.Color.blue(pixel);
                    if (r < 40 && g < 40 && b < 40) dark++;
                    if (r > 200 && g > 200 && b > 200) light++;
                }
            check(
                    light > 10 && dark > light * 3,
                    "rendered Stop has white ink on the dark status surface without a filled circle");
        } finally {
            bitmap.recycle();
        }
    }

    private void outside() throws Exception {
        int count = InputTestActivity.touchEvents;
        float[] p = physical(4 * session.dpi / 160f, session.height() * .5f);
        DeviceTestSupport.usableMonitorOutside(session, p);
        tap(p[0], p[1]);
        page("closed");
        check(
                InputTestActivity.touchEvents == count,
                "outside dismissal does not click underlying app");
        noChrome();
    }

    private void resume() throws Exception {
        while (!session.monitorShellPage().equals("closed")) {
            main(() -> session.key(KeyEvent.KEYCODE_BACK));
            Thread.sleep(80);
        }
        page("closed");
    }

    private void homeGesture() throws Exception {
        float d = session.dpi / 160f;
        swipe(
                session.width() * .6f,
                session.height() - 4 * d,
                session.width() * .6f,
                session.height() - 100 * d);
        page("home");
    }

    private void controlsGesture() throws Exception {
        float d = session.dpi / 160f;
        swipe(session.width() * .6f, 4 * d, session.width() * .6f, 100 * d);
        page("controls");
    }

    private void exactText(String expected) {
        main(
                () ->
                        check(
                                MenuTextAssertions.contains(session.monitorShellView(), expected),
                                "exact displayed menu text: " + expected));
    }

    private void textScreenshot(String name) throws Exception {
        Display.Mode mode = physicalMonitor().getMode();
        android.graphics.Bitmap bitmap =
                android.graphics.Bitmap.createBitmap(
                        mode.getPhysicalWidth(),
                        mode.getPhysicalHeight(),
                        android.graphics.Bitmap.Config.ARGB_8888);
        CountDownLatch done = new CountDownLatch(1);
        int[] result = {-1};
        try {
            main(
                    () ->
                            PixelCopy.request(
                                    session.monitorWindow(),
                                    bitmap,
                                    v -> {
                                        result[0] = v;
                                        done.countDown();
                                    },
                                    new Handler(Looper.getMainLooper())));
            check(
                    done.await(5, TimeUnit.SECONDS) && result[0] == PixelCopy.SUCCESS,
                    "physical menu screenshot: " + name);
            java.io.File directory =
                    new java.io.File(
                            runner.getTargetContext().getExternalCacheDir(),
                            "menu-text-acceptance");
            check(
                    directory.isDirectory() || directory.mkdirs(),
                    "menu screenshot directory available");
            try (java.io.FileOutputStream stream =
                    new java.io.FileOutputStream(new java.io.File(directory, name + ".png"))) {
                check(
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream),
                        "exported menu screenshot: " + name);
            }
        } finally {
            bitmap.recycle();
        }
    }

    private void textMenus(ComponentName test) throws Exception {
        main(() -> session.launch(test, 0));
        await(
                () -> !session.busy && session.rotation == 0,
                "text inspection landscape desktop ready");
        Thread.sleep(500);
        controlsGesture();
        exactText("Monitor \u00b7 Connected \u2014 ");
        exactText("Shizuku \u00b7 Connected");
        exactText("Landscape 0\u00b0");
        exactText(session.width() + " \u00d7 " + session.height());
        main(
                () ->
                        check(
                                MenuTextAssertions.check(phone.getWindow().getDecorView()) > 0,
                                "phone shared status text and font glyphs decoded"));
        textScreenshot("controls");
        click("monitor-rotate");
        page("rotation");
        for (String value :
                new String[] {
                    "Landscape 0\u00b0",
                    "Portrait 90\u00b0",
                    "Landscape 180\u00b0",
                    "Portrait 270\u00b0"
                }) exactText(value);
        textScreenshot("rotation");
        resume();
        controlsGesture();
        click("monitor-settings");
        page("settings");
        click("monitor-app-settings");
        page("app-settings");
        click("monitor-scroll");
        page("scroll");
        for (String value :
                new String[] {
                    "Swipe \u00b7 Touchscreen drag",
                    "Wheel \u00b7 Mouse scrolling",
                    "Slow \u00b7 20%",
                    "Normal \u00b7 50%",
                    "Fast \u00b7 100%"
                }) exactText(value);
        textScreenshot("scroll");
        resume();
        controlsGesture();
        click("monitor-settings");
        page("settings");
        exactText("\u203a");
        exactText("Monitor touch \u00b7 On");
        exactText(
                session.width()
                        + " \u00d7 "
                        + session.height()
                        + " \u00b7 "
                        + session.dpi
                        + " DPI");
        textScreenshot("settings");
        click("monitor-display");
        page("display");
        Display.Mode currentOutput = physicalMonitor().getMode();
        exactText(
                "Current output: "
                        + currentOutput.getPhysicalWidth()
                        + " \u00d7 "
                        + currentOutput.getPhysicalHeight());
        exactText("\u203a");
        textScreenshot("display");
        main(() -> session.key(KeyEvent.KEYCODE_BACK));
        page("settings");
        click("monitor-diagnostics");
        page("diagnostics");
        textScreenshot("diagnostics");
        resume();
        homeGesture();
        exactText("Tap to launch \u00b7 Hold for a one-time orientation");
        main(
                () ->
                        ((android.widget.EditText)
                                        session.monitorShellView()
                                                .findViewWithTag("monitor-app-search"))
                                .setText("DexDroid input test"));
        Thread.sleep(100);
        click("monitor-app-search");
        page("home");
        exactText("\u21e7");
        exactText("\u232b");
        textScreenshot("apps");
        Rect held = new Rect();
        main(
                () ->
                        check(
                                session.monitorShellView()
                                        .findViewWithTag("app.dev.dexdroid")
                                        .getGlobalVisibleRect(held),
                                "app text inspection target visible"));
        event(0, new float[][] {{held.exactCenterX(), held.exactCenterY()}});
        Thread.sleep(650);
        event(1, new float[][] {{held.exactCenterX(), held.exactCenterY()}});
        page("app-rotation");
        click("monitor-up");
        page("home");
        check(
                ((android.widget.EditText)
                                session.monitorShellView().findViewWithTag("monitor-app-search"))
                        .getText()
                        .toString()
                        .equals("DexDroid input test"),
                "monitor Apps query survives orientation Up");
        await(
                () -> session.monitorShellView().findViewWithTag("app.dev.dexdroid") != null,
                "restored monitor app result available");
        main(
                () ->
                        session.monitorShellView()
                                .findViewWithTag("app.dev.dexdroid")
                                .getGlobalVisibleRect(held));
        event(0, new float[][] {{held.exactCenterX(), held.exactCenterY()}});
        Thread.sleep(650);
        event(1, new float[][] {{held.exactCenterX(), held.exactCenterY()}});
        page("app-rotation");

        for (String value :
                new String[] {
                    "Landscape 0\u00b0",
                    "Portrait 90\u00b0",
                    "Landscape 180\u00b0",
                    "Portrait 270\u00b0",
                    "\u203a"
                }) exactText(value);
        textScreenshot("app-rotation");
        resume();
    }

    /** Force the legacy focus-wait boundary within one main turn, before its timer can run. */
    private void legacyFocusArbitration() throws Exception {
        int rotation = session.rotation;
        if (Build.VERSION.SDK_INT != 30) return;
        java.lang.reflect.Field field = SessionService.class.getDeclaredField("monitor");
        field.setAccessible(true);
        Presentation host = (Presentation) field.get(session);
        View decor = host.getWindow().getDecorView();
        float[] point = physical(session.width() * .5f, session.height() * .5f);
        for (int mode = 0; mode < 4; mode++) {
            final int cancellation = mode;
            Rect titleBounds = new Rect();
            main(
                    () -> {
                        ArrayList<View> titles = new ArrayList<>();
                        phone.getWindow()
                                .getDecorView()
                                .findViewsWithText(titles, "DexDroid", View.FIND_VIEWS_WITH_TEXT);
                        View title =
                                titles.stream()
                                        .filter(
                                                v ->
                                                        v instanceof android.widget.TextView
                                                                && ((android.widget.TextView) v)
                                                                        .getText()
                                                                        .toString()
                                                                        .equals("DexDroid"))
                                        .findFirst()
                                        .orElseThrow();
                        check(
                                title.getGlobalVisibleRect(titleBounds),
                                "phone title focus target measured");
                    });
            DeviceTestSupport.shell(
                    runner,
                    "input -d 0 tap " + titleBounds.centerX() + " " + titleBounds.centerY());
            await(
                    () -> !decor.hasWindowFocus(),
                    "real phone contact leaves monitor awaiting focus");
            int touches = InputTestActivity.touchEvents, wheels = InputTestActivity.wheelEvents;
            main(
                    () -> {
                        session.cancelInput();
                        session.hideMonitorPointer();
                        float x = session.cx, y = session.cy;
                        long time = SystemClock.uptimeMillis();
                        MotionEvent downEvent =
                                MotionEvent.obtain(
                                        time, time, MotionEvent.ACTION_DOWN, point[0], point[1], 0);
                        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                        MotionEvent upEvent = MotionEvent.obtain(downEvent);
                        upEvent.setAction(MotionEvent.ACTION_UP);
                        try {
                            check(
                                    host.dispatchTouchEvent(downEvent),
                                    "focus-wait accepts physical DOWN " + rotation);
                            session.click();
                            session.wheel(0, 12);
                            session.move(35, 35);
                            session.showTouchpadPointer();
                            check(
                                    session.cx == x && session.cy == y && !session.pointerVisible,
                                    "buffered physical DOWN blocks phone pointer/click/wheel "
                                            + rotation
                                            + "/"
                                            + cancellation);
                            check(
                                    host.dispatchTouchEvent(upEvent),
                                    "focus-wait accepts physical UP " + rotation);
                            if (cancellation == 0) session.cancelInput();
                            else if (cancellation == 1) {
                                session.setMonitorNavigationEnabled(false);
                                session.setMonitorNavigationEnabled(true);
                            } else if (cancellation == 2) {
                                session.setMonitorTouchEnabled(false);
                                session.setMonitorTouchEnabled(true);
                            }
                            // A focus callback and the scheduled fallback must not resurrect a
                            // canceled tap.
                            host.onWindowFocusChanged(true);
                        } finally {
                            downEvent.recycle();
                            upEvent.recycle();
                        }
                    });
            // Fence the display worker, then allow the original 100 ms timeout to expire.
            java.lang.reflect.Field workerField = SessionService.class.getDeclaredField("worker");
            workerField.setAccessible(true);
            ((java.util.concurrent.ExecutorService) workerField.get(session))
                    .submit(() -> {})
                    .get(5, TimeUnit.SECONDS);
            if (cancellation == 3) {
                await(
                        () -> InputTestActivity.touchEvents >= touches + 2,
                        "focus callback relays valid buffered contact " + rotation);
            }
            Thread.sleep(180);
            check(
                    InputTestActivity.touchEvents == touches + (cancellation == 3 ? 2 : 0)
                            && InputTestActivity.wheelEvents == wheels,
                    "canceled buffer never replays; valid release exactly once "
                            + rotation
                            + "/"
                            + cancellation);
            main(() -> session.move(1, 1));
            check(
                    session.pointerVisible,
                    "phone pointer resumes after focus buffer ends " + rotation);
        }
    }

    String run() throws Exception {
        try {
            phone = DeviceTestSupport.startPhone(runner);
            CountDownLatch ready = new CountDownLatch(1);
            binding =
                    new ServiceConnection() {
                        public void onServiceConnected(ComponentName n, IBinder b) {
                            session = ((SessionService.LocalBinder) b).get();
                            ready.countDown();
                        }

                        public void onServiceDisconnected(ComponentName n) {}
                    };
            bound =
                    runner.getTargetContext()
                            .bindService(
                                    new Intent(runner.getTargetContext(), SessionService.class),
                                    binding,
                                    Context.BIND_AUTO_CREATE);
            check(ready.await(5, TimeUnit.SECONDS), "monitor controller bound");
            await(() -> session.id >= 0 && !session.busy, "automatic monitor desktop ready");
            preferences = new HashMap<>(session.prefs.getAll());
            session.prefs.edit().remove("statusExpanded").apply();
            originalRotation = session.rotation;
            main(
                    () -> {
                        session.setMonitorNavigationEnabled(true);
                        session.setMonitorTouchEnabled(true);
                    });
            ComponentName test =
                    new ComponentName(runner.getTargetContext(), InputTestActivity.class);
            textMenus(test);
            for (int r = 0; r < 4; r++) {
                final int selected = r;
                main(() -> session.launch(test, selected));
                await(() -> !session.busy && session.rotation == selected, "monitor rotation " + r);
                Thread.sleep(500);
                legacyFocusArbitration();
                if (r == 0) injectedContactCleanup();
                float d = session.dpi / 160f;
                int back = InputTestActivity.backEvents, cancel = InputTestActivity.cancelEvents;
                int touchesBeforeEdge = InputTestActivity.touchEvents;
                long upBeforeEdge = InputTestActivity.lastUpDownTime;
                float[] start = physical(session.width() - 4 * d, session.height() * .4f),
                        preview = physical(session.width() - 30 * d, session.height() * .4f),
                        committed = physical(session.width() - 100 * d, session.height() * .4f);
                float originalX = start[0], originalY = start[1];
                DeviceTestSupport.usableMonitorSwipe(session, start, preview);
                committed[0] += start[0] - originalX;
                committed[1] += start[1] - originalY;
                event(0, new float[][] {start});
                event(2, new float[][] {preview});
                main(
                        () -> {
                            EdgeFeedback feedback =
                                    session.monitorShellView()
                                            .findViewWithTag("monitor-edge-feedback");
                            check(
                                    feedback.isShown()
                                            && feedback.progress() > 0
                                            && feedback.progress() < 1,
                                    "live pre-threshold Back feedback " + selected);
                        });
                check(
                        InputTestActivity.backEvents == back,
                        "feedback preview does not send Back " + r);
                event(2, new float[][] {committed});
                event(1, new float[][] {committed});
                await(
                        () -> InputTestActivity.backEvents == back + 1,
                        "right-edge Back exactly once rotation " + r);
                await(
                        () ->
                                InputTestActivity.touchEvents == touchesBeforeEdge
                                        || InputTestActivity.cancelEvents > cancel,
                        "edge Back discards unreleased contact or cancels relayed contact " + r);
                await(
                        () ->
                                session.monitorShellView()
                                                .findViewWithTag("monitor-edge-feedback")
                                                .getVisibility()
                                        == View.GONE,
                        "committed feedback clears " + r);
                check(
                        InputTestActivity.lastUpDownTime == upBeforeEdge,
                        "recognized edge Back never delivers app UP " + r);
                check(!session.pointerVisible, "edge input hides cursor " + r);
                noChrome();
                int afterBack = InputTestActivity.backEvents;
                event(0, new float[][] {start});
                event(2, new float[][] {committed});
                main(
                        () ->
                                check(
                                        InputTestActivity.backEvents == afterBack,
                                        "recognized Back waits for physical release " + selected));
                event(MotionEvent.ACTION_CANCEL, new float[][] {committed});
                Thread.sleep(200);
                check(
                        InputTestActivity.backEvents == afterBack,
                        "canceled recognized Back does not navigate " + r);
                noChrome();
                homeGesture();
                main(
                        () -> {
                            View root = session.monitorShellView();
                            check(
                                    root.findViewWithTag("monitor-close")
                                            instanceof android.widget.ImageButton,
                                    "launcher Close is an icon " + selected);
                            check(
                                    root.findViewWithTag("monitor-resume") == null,
                                    "launcher has no Resume footer " + selected);
                            android.widget.EditText search =
                                    root.findViewWithTag("monitor-app-search");
                            check(search != null, "phone-style launcher search exists " + selected);
                            search.setText("DexDroid input test");
                        });
                Thread.sleep(100);
                main(
                        () ->
                                check(
                                        ((android.widget.ListView)
                                                                session.monitorShellView()
                                                                        .findViewWithTag(
                                                                                "monitor-app-list"))
                                                        .getAdapter()
                                                        .getCount()
                                                == 1,
                                        "launcher search filters real app list " + selected));
                click("app.dev.dexdroid");
                page("closed");
                await(
                        () -> session.activePackage.equals("dev.dexdroid") && !session.busy,
                        "monitor launcher targets external app " + r);
                Thread.sleep(300);
                main(() -> session.rotate(selected, false));
                await(
                        () -> !session.busy && session.rotation == selected,
                        "control panel test orientation " + r);
                Thread.sleep(300);
                controlsGesture();
                parity();
                navigationFlow(r);
                renderedContrast();
                click("monitor-title");
                page("controls");
                click("monitor-settings");
                page("settings");
                click("monitor-touch");
                check(!session.monitorTouchEnabled(), "monitor settings touch Off " + r);
                click("monitor-touch");
                check(session.monitorTouchEnabled(), "monitor settings touch On " + r);
                resume();
                controlsGesture();
                outside();
                homeGesture();
                outside();
                // Launcher selection applies the app's saved/default rotation; re-establish
                // this test's explicit orientation before the next edge gesture.
                main(() -> session.rotate(selected, false));
                await(
                        () -> !session.busy && session.rotation == selected,
                        "restore test rotation " + r);
                Thread.sleep(300);
            }
            homeGesture();
            int saved = session.prefs.getInt("app.dev.dexdroid", -1);
            Rect held = new Rect();
            main(
                    () ->
                            check(
                                    session.monitorShellView()
                                            .findViewWithTag("app.dev.dexdroid")
                                            .getGlobalVisibleRect(held),
                                    "held-app target visible"));
            event(0, new float[][] {{held.exactCenterX(), held.exactCenterY()}});
            Thread.sleep(650);
            event(1, new float[][] {{held.exactCenterX(), held.exactCenterY()}});
            page("app-rotation");
            click("monitor-once-1");
            page("closed");
            await(
                    () -> !session.busy && session.rotation == 1,
                    "held app applies one-time rotation");
            check(
                    session.prefs.getInt("app.dev.dexdroid", -1) == saved,
                    "held-app override does not overwrite saved rotation");
            float d = session.dpi / 160f;
            int back = InputTestActivity.backEvents;
            swipe(
                    session.width() - 4 * d,
                    session.height() * .4f,
                    session.width() - 20 * d,
                    session.height() * .4f);
            check(InputTestActivity.backEvents == back, "short edge swipe rejected");
            swipe(
                    session.width() - 12 * d,
                    session.height() * .4f,
                    session.width() - 2 * d,
                    session.height() * .4f);
            check(InputTestActivity.backEvents == back, "outward edge swipe rejected");
            swipe(
                    session.width() - 4 * d,
                    session.height() * .3f,
                    session.width() - 5 * d,
                    session.height() * .5f);
            check(
                    InputTestActivity.backEvents == back,
                    "vertical side-edge swipe remains app input");
            event(0, new float[][] {physical(session.width() - 4 * d, session.height() * .4f)});
            event(
                    5 | 256,
                    new float[][] {
                        physical(session.width() - 4 * d, session.height() * .4f),
                        physical(session.width() - 30 * d, session.height() * .5f)
                    });
            event(
                    2,
                    new float[][] {
                        physical(session.width() - 100 * d, session.height() * .4f),
                        physical(session.width() - 120 * d, session.height() * .5f)
                    });
            event(
                    6 | 256,
                    new float[][] {
                        physical(session.width() - 100 * d, session.height() * .4f),
                        physical(session.width() - 120 * d, session.height() * .5f)
                    });
            event(1, new float[][] {physical(session.width() - 100 * d, session.height() * .4f)});
            Thread.sleep(150);
            check(InputTestActivity.backEvents == back, "multi-touch edge gesture rejected");
            main(() -> session.setMonitorNavigationEnabled(false));
            swipe(
                    session.width() - 4 * d,
                    session.height() * .4f,
                    session.width() - 100 * d,
                    session.height() * .4f);
            check(
                    InputTestActivity.backEvents == back,
                    "disabled edge navigation leaves native input");
            main(() -> session.setMonitorNavigationEnabled(true));
            swipe(4 * d, session.height() * .4f, 100 * d, session.height() * .4f);
            await(() -> InputTestActivity.backEvents == back + 1, "left-edge inward Back");
            homeGesture();
            int underlying = InputTestActivity.touchEvents;
            click("monitor-close");
            page("closed");
            check(
                    InputTestActivity.touchEvents == underlying,
                    "launcher close does not click underlying app");
            controlsGesture();
            int[] count = {InputTestActivity.touchEvents};
            main(
                    () -> {
                        session.cx = 4 * session.dpi / 160f;
                        session.cy = session.height() * .5f;
                        session.click();
                    });
            page("closed");
            check(
                    InputTestActivity.touchEvents == count[0],
                    "phone pointer outside dismissal has no click-through");
            controlsGesture();
            click("monitor-settings");
            page("settings");
            click("monitor-app-settings");
            page("app-settings");
            main(
                    () -> {
                        View button = session.monitorShellView().findViewWithTag("monitor-scroll");
                        float x = 0, y = 0;
                        View current = button;
                        while (current != session.monitorShellView()) {
                            x += current.getLeft();
                            y += current.getTop();
                            current = (View) current.getParent();
                        }
                        session.cx = x + button.getWidth() / 2f;
                        session.cy = y + button.getHeight() / 2f;
                        session.click();
                    });
            page("scroll");
            check(session.pointerVisible, "phone pointer operates monitor icons");
            click("monitor-swipe");
            check(session.swipeScrolling(), "monitor selects native swipe mode");
            click("monitor-speed-0");
            check(session.scrollSpeed() == .2f, "monitor saves scroll speed");
            resume();
            homeGesture();
            android.widget.ListView[] appList = {null};
            int[] first = {-1};
            main(
                    () -> {
                        appList[0] =
                                (android.widget.ListView)
                                        session.monitorShellView()
                                                .findViewWithTag("monitor-app-list");
                        first[0] = appList[0].getFirstVisiblePosition();
                        float x = 0, y = 0;
                        View current = appList[0];
                        while (current != session.monitorShellView()) {
                            x += current.getLeft();
                            y += current.getTop();
                            View parent = (View) current.getParent();
                            x -= parent.getScrollX();
                            y -= parent.getScrollY();
                            current = parent;
                        }
                        session.cx = x + appList[0].getWidth() / 2f;
                        session.cy = y + appList[0].getHeight() / 2f;
                        session.wheel(0, -20);
                    });
            await(
                    () -> appList[0].getFirstVisiblePosition() > first[0],
                    "phone wheel scrolls native monitor launcher");
            check(session.pointerVisible, "phone menu scrolling restores cursor");
            resume();
            controlsGesture();
            int rotateTo = (session.rotation + 1) % 4;
            click("monitor-rotate");
            page("rotation");
            click("monitor-rotation-" + rotateTo);
            await(
                    () -> !session.busy && session.rotation == rotateTo,
                    "monitor Rotate changes upright desktop");
            page("controls");
            click("monitor-settings");
            page("settings");
            click("monitor-app-settings");
            page("app-settings");
            click("monitor-save");
            check(
                    session.prefs.getInt("app.dev.dexdroid", -1) == session.rotation,
                    "monitor Save persists rotation");
            click("monitor-reset");
            check(!session.prefs.contains("app.dev.dexdroid"), "monitor Reset clears app rotation");
            await(() -> !session.busy, "reset rotation complete");
            resume();
            main(() -> session.rotate(0, false));
            await(() -> !session.busy && session.rotation == 0, "Fit test landscape");
            main(() -> session.preview(1920, 1080, 240));
            await(() -> !session.busy && session.baseH == 1080, "Fit test preview");
            event(0, new float[][] {{960, 45}});
            event(2, new float[][] {{960, 250}});
            event(1, new float[][] {{960, 250}});
            Thread.sleep(100);
            page("closed");
            controlsGesture();
            main(
                    () -> {
                        View root = session.monitorShellView();
                        ControllerStyle.StatusCard card =
                                root.findViewWithTag("controller-status-card");
                        check(
                                card.confirmation.isShown()
                                        && root.findViewWithTag("monitor-confirmation") == null,
                                "resolution preview uses one shared status-card confirmation row");
                    });
            tap(960, 45);
            page("closed");
            check(
                    session.diagnosticText().contains("Monitor edge recognized")
                            && session.diagnosticText().contains("Monitor transition")
                            && session.diagnosticText().contains("Monitor edge rejected"),
                    "edge and animation outcomes in diagnostic log");
            main(() -> session.confirm(false));
            await(() -> !session.busy, "Fit reverted");
            main(() -> phone.finish());
            Thread.sleep(300);
            homeGesture();
            click("app.dev.dexdroid");
            page("closed");
            await(
                    () -> !session.busy && session.activePackage.equals("dev.dexdroid"),
                    "background launcher app ready");
            Thread.sleep(300);
            controlsGesture();
            check(session.sessionAwake(), "monitor shell works with phone controller destroyed");
            click("monitor-stop");
            await(
                    () -> session.id < 0 && session.monitorShellView() == null,
                    "monitor Stop releases shell and display");
            restore();
            return results.toString();
        } catch (Throwable error) {
            throw new RuntimeException(results + "FAIL monitor acceptance", error);
        } finally {
            restore();
            if (bound) runner.getTargetContext().unbindService(binding);
        }
    }
}
