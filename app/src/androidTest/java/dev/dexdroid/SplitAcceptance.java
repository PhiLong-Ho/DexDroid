package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.*;
import dev.dexdroid.fixture.LaunchParentActivity;
import dev.dexdroid.fixture.SplitProbeFixture;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Product session, real shell IPC, native cross-UID fixture and actual display input. */
final class SplitAcceptance {
    private final Instrumentation runner;
    private final boolean navigationOnly;
    private final StringBuilder results = new StringBuilder();
    private SessionService session;
    private MainActivity phone;
    private volatile Bundle state;
    private volatile Bundle child;
    private volatile Bundle protection;
    private String frameEvidence = "not sampled";
    private long down;

    SplitAcceptance(Instrumentation runner) {
        this(runner, false);
    }

    SplitAcceptance(Instrumentation runner, boolean navigationOnly) {
        this.runner = runner;
        this.navigationOnly = navigationOnly;
    }

    private void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        results.append("PASS ").append(label).append('\n');
    }

    private void await(BooleanSupplier condition, String label) throws Exception {
        long end = SystemClock.uptimeMillis() + 15000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(40);
        check(condition.getAsBoolean(), label);
    }

    private void ready(String label) throws Exception {
        await(() -> session.id >= 0 && !session.busy, label);
        if (session.split != null && session.split.displayId(1) >= 0) {
            for (int pane = 0; pane < 2; pane++) {
                final int selected = pane;
                await(
                        () -> {
                            android.view.Display display =
                                    ((DisplayManager)
                                                    runner.getTargetContext()
                                                            .getSystemService(
                                                                    Context.DISPLAY_SERVICE))
                                            .getDisplay(session.split.displayId(selected));
                            if (display == null) return false;
                            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
                            display.getRealMetrics(metrics);
                            SplitLayout.Box expected =
                                    session.split.dimensions(
                                            selected, session.width(), session.height(), false);
                            return metrics.widthPixels == expected.width
                                    && metrics.heightPixels == expected.height
                                    && metrics.densityDpi == session.dpi;
                        },
                        "actual pane " + (pane + 1) + " dimensions and DPI: " + label);
            }
        }
        runner.getUiAutomation().waitForIdle(250, 5000);
    }

    private void launch(ComponentName app) throws Exception {
        int target =
                session.splitEnabled()
                        ? session.split.displayId(session.split.selected())
                        : session.id;
        runner.runOnMainSync(
                () -> {
                    session.status = "Acceptance pending";
                    session.launch(app, session.splitEnabled() ? null : 0);
                });
        await(
                () -> {
                    if (session.busy) return false;
                    try {
                        for (DisplayTasks.Root root :
                                DisplayTasks.parse(
                                        DeviceTestSupport.shell(runner, "am stack list"),
                                        app.flattenToString()))
                            if (root.display == target && root.matches && root.visible) return true;
                        return false;
                    } catch (Exception failure) {
                        throw new IllegalStateException(
                                "Cannot verify native app placement", failure);
                    }
                },
                "verified pane launch " + app.flattenToShortString());
        runner.getUiAutomation().waitForIdle(250, 5000);
    }

    private float[] physical(float x, float y) {
        View view = session.monitorWindow().getDecorView();
        return Geometry.map(
                x / session.width(),
                y / session.height(),
                session.rotation,
                view.getWidth(),
                view.getHeight(),
                session.width(),
                session.height());
    }

    private void pointer(int action, float[] p, int source, float wheel) throws Exception {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_SCROLL) down = now;
        MotionEvent.PointerProperties property = new MotionEvent.PointerProperties();
        property.id = 7;
        property.toolType =
                source == InputDevice.SOURCE_MOUSE
                        ? MotionEvent.TOOL_TYPE_MOUSE
                        : MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords coord = new MotionEvent.PointerCoords();
        coord.x = p[0];
        coord.y = p[1];
        coord.pressure = 1;
        coord.setAxisValue(MotionEvent.AXIS_VSCROLL, wheel);
        MotionEvent event =
                MotionEvent.obtain(
                        down,
                        now,
                        action,
                        1,
                        new MotionEvent.PointerProperties[] {property},
                        new MotionEvent.PointerCoords[] {coord},
                        0,
                        source == InputDevice.SOURCE_MOUSE && action != MotionEvent.ACTION_UP
                                ? MotionEvent.BUTTON_PRIMARY
                                : 0,
                        1,
                        1,
                        -1,
                        0,
                        source,
                        0);
        try {
            check(
                    DeviceTestSupport.injectOnDisplay(event, session.external.getDisplayId()),
                    "physical host injection " + action + "/" + source);
        } finally {
            event.recycle();
        }
    }

    private void tap(float[] p, int source) throws Exception {
        pointer(MotionEvent.ACTION_DOWN, p, source, 0);
        Thread.sleep(80);
        pointer(MotionEvent.ACTION_UP, p, source, 0);
    }

    private SplitLayout.Box box(int pane) {
        return session.split
                .state()
                .pane(pane, session.width(), session.height(), session.split.gap());
    }

    private void nativeReady() throws Exception {
        await(
                () ->
                        state.getInt("width") == box(0).width
                                && state.getInt("height") == box(0).height
                                && state.getInt("layoutWidth") == box(0).width
                                && state.getInt("layoutBottom") == box(0).height,
                "native child layout matches resized window");
    }

    private void nativeClick(int source) throws Exception {
        nativeReady();
        int clicks = state.getInt("clicks");
        SplitLayout.Box box = box(0);
        tap(physical(box.x + state.getFloat("buttonX"), box.y + state.getFloat("buttonY")), source);
        await(
                () -> state.getInt("clicks") == clicks + 1,
                "native button receives one click " + source);
        check(session.split.selected() == 0, "physical click selects first app");
    }

    private void inputTap() throws Exception {
        inputTap(1);
    }

    private void inputTap(int pane) throws Exception {
        await(() -> inputFrameReady(pane), "native app frame ready before first contact");
        SplitLayout.Box box = box(pane);
        float x = box.x + box.width * .5f, y = box.y + box.height * .55f;
        tap(physical(x, y), InputDevice.SOURCE_TOUCHSCREEN);
        long expected = down;
        await(
                () -> InputTestActivity.lastUpDownTime == expected,
                "app in pane " + pane + " receives physical tap");
        check(
                Math.abs(InputTestActivity.lastUpX - box.width * .5f) < 2
                        && Math.abs(InputTestActivity.lastUpY - box.height * .55f) < 2,
                "pane-local raw coordinates aligned");
        check(!session.pointerVisible, "physical pane input hides pointer");
        await(() -> inputFrameReady(pane), "native app frame retains circular target after resize");
    }

    private boolean inputFrameReady(int pane) {
        boolean[] ready = {false};
        runner.runOnMainSync(
                () -> {
                    TextureView view =
                            session.monitorInputView().findViewWithTag("split-texture-" + pane);
                    Bitmap image = view == null ? null : view.getBitmap();
                    if (image == null) return;
                    try {
                        // The pinch scenario legitimately enlarges the circles. Check
                        // their position/aspect, using the central unclipped target and
                        // crossing its one-pixel yellow scroll ruler vertically.
                        int x = Math.round(image.getWidth() * .5f),
                                y = Math.round(image.getHeight() * .5f),
                                row = y + 4;
                        frameEvidence =
                                "bitmap="
                                        + image.getWidth()
                                        + "x"
                                        + image.getHeight()
                                        + " center="
                                        + Integer.toHexString(image.getPixel(x, row));
                        if (!targetPixel(image, x, row)) return;
                        int left = x, right = x, top = y, bottom = y;
                        while (targetPixel(image, left - 1, row)) left--;
                        while (targetPixel(image, right + 1, row)) right++;
                        while (verticalTargetPixel(image, x, top - 1)) top--;
                        while (verticalTargetPixel(image, x, bottom + 1)) bottom++;
                        int width = right - left + 1, height = bottom - top + 1;
                        frameEvidence += " target=" + width + "x" + height;
                        ready[0] =
                                width >= 64
                                        && width <= 285
                                        && height >= 64
                                        && height <= 285
                                        && Math.abs(width - height) <= 4;
                    } finally {
                        image.recycle();
                    }
                });
        return ready[0];
    }

    private boolean firstFrameReady() {
        boolean[] ready = {false};
        runner.runOnMainSync(
                () -> {
                    TextureView view =
                            session.monitorInputView().findViewWithTag("split-texture-0");
                    Bitmap image = view.getBitmap();
                    if (image == null) return;
                    try {
                        int w = image.getWidth(), h = image.getHeight();
                        ready[0] =
                                image.getPixel(8, h - 8) == Color.rgb(18, 18, 18)
                                        && image.getPixel(w - 8, h - 8) == Color.rgb(18, 18, 18)
                                        && image.getPixel(w / 4, h * 9 / 10)
                                                == Color.rgb(40, 40, 40);
                    } finally {
                        image.recycle();
                    }
                });
        return ready[0];
    }

    private static boolean targetPixel(Bitmap image, int x, int y) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) return false;
        int color = image.getPixel(x, y);
        return Math.abs(Color.red(color) - 94) <= 4
                && Math.abs(Color.green(color) - 234) <= 4
                && Math.abs(Color.blue(color) - 212) <= 4;
    }

    private static boolean verticalTargetPixel(Bitmap image, int x, int y) {
        if (targetPixel(image, x, y)) return true;
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) return false;
        int color = image.getPixel(x, y);
        // Include the antialiased blend between teal fill and yellow ruler.
        return Color.red(color) >= 90 && Color.green(color) >= 230 && Color.blue(color) <= 216;
    }

    private Dialog phoneDialog() {
        try {
            java.lang.reflect.Field field = MainActivity.class.getDeclaredField("settingsDialog");
            field.setAccessible(true);
            return (Dialog) field.get(phone);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private void monitorLayout(int rotation) throws Exception {
        float[] start = physical(session.width() * .5f, 4),
                end = physical(session.width() * .5f, session.height() * .3f);
        pointer(0, start, InputDevice.SOURCE_TOUCHSCREEN, 0);
        Thread.sleep(35);
        pointer(2, end, InputDevice.SOURCE_TOUCHSCREEN, 0);
        pointer(1, end, InputDevice.SOURCE_TOUCHSCREEN, 0);
        await(
                () ->
                        session.monitorShellPage().equals("controls")
                                && !session.monitorShellAnimating(),
                "edge opens controls in split " + rotation);
        runner.runOnMainSync(
                () -> session.monitorShellView().findViewWithTag("monitor-layout").performClick());
        await(
                () ->
                        session.monitorShellPage().equals("layout")
                                && !session.monitorShellAnimating(),
                "monitor Layout opens " + rotation);
        runner.runOnMainSync(
                () ->
                        check(
                                MenuTextAssertions.check(session.monitorShellView()) > 0,
                                "monitor Layout text and native icons valid " + rotation));
        capture("monitor-layout-r" + rotation, true);
        runner.runOnMainSync(() -> layoutTargets(session.monitorShellView(), true));
        controllerMenuTap("layout-target-0", "home");
        check(
                session.appSelectionBox() == null,
                "monitor launcher opens without persistent highlight");
        View action = launchButton(true, 0);
        buttonContact(action, MotionEvent.ACTION_DOWN);
        checkDestination(
                session.split.state().paneAtPosition(0), "monitor spatial action " + rotation);
        capture("monitor-destination-r" + rotation, true);
        capture("monitor-combined-r" + rotation, true);
        buttonContact(action, MotionEvent.ACTION_CANCEL);
        check(session.appSelectionBox() == null, "monitor action cancel clears highlight");
        runner.runOnMainSync(
                () -> session.monitorShellView().findViewWithTag("monitor-up").performClick());
        await(
                () ->
                        session.monitorShellPage().equals("layout")
                                && !session.monitorShellAnimating(),
                "monitor picker header returns to Layout " + rotation);
        controllerMenuTap("layout-target-0", "home");
        if (rotation == 0) {
            pointer(0, physical(session.width() * .5f, 4), InputDevice.SOURCE_TOUCHSCREEN, 0);
            pointer(
                    2,
                    physical(session.width() * .5f, session.height() * .3f),
                    InputDevice.SOURCE_TOUCHSCREEN,
                    0);
            pointer(
                    1,
                    physical(session.width() * .5f, session.height() * .3f),
                    InputDevice.SOURCE_TOUCHSCREEN,
                    0);
            await(
                    () ->
                            session.monitorShellPage().equals("controls")
                                    && !session.monitorShellAnimating(),
                    "controls replace active app picker");
            check(session.appSelectionBox() == null, "replacement controls clear picker highlight");
            runner.runOnMainSync(() -> session.key(KeyEvent.KEYCODE_BACK));
            await(
                    () ->
                            session.monitorShellPage().equals("closed")
                                    && !session.monitorShellAnimating(),
                    "Back from fresh Controls root cannot reopen cancelled Apps");
        }
        if (session.monitorShellPage().equals("home")) {
            tap(
                    physical(session.width() * .04f, session.height() * .5f),
                    InputDevice.SOURCE_TOUCHSCREEN);
            await(
                    () ->
                            session.monitorShellPage().equals("closed")
                                    && !session.monitorShellAnimating(),
                    "targeted picker outside closes complete flow");
        }
        if (!session.monitorShellPage().equals("closed"))
            tap(
                    physical(session.width() * .04f, session.height() * .5f),
                    InputDevice.SOURCE_TOUCHSCREEN);
        await(
                () ->
                        session.monitorShellPage().equals("closed")
                                && !session.monitorShellAnimating(),
                "whole modal flow dismisses outside without app click " + rotation);
        check(session.appSelectionBox() == null, "monitor cancel clears destination " + rotation);
    }

    private void layoutTargets(View root, boolean enabled) {
        android.widget.LinearLayout targets = root.findViewWithTag("layout-targets");
        android.widget.Button first = root.findViewWithTag("layout-target-0"),
                second = root.findViewWithTag("layout-target-1");
        SplitLayout layout = enabled ? session.split.state() : new SplitLayout();
        check(
                first.isShown() && second.isShown() == enabled,
                "Layout has exactly one or two app targets");
        check(
                first.getText().toString().equals(layout.openLabel(0)),
                "first target names spatial destination");
        check(
                root.findViewWithTag("layout-pane-0") == null
                        && root.findViewWithTag("layout-apps") == null,
                "Layout removes separate select-pane and open-app steps");
        if (enabled) {
            check(
                    second.getText().toString().equals(layout.openLabel(1)),
                    "second target names spatial destination");
            check(
                    targets.getOrientation()
                            == (layout.vertical
                                    ? android.widget.LinearLayout.VERTICAL
                                    : android.widget.LinearLayout.HORIZONTAL),
                    "app target arrangement follows split direction");
            check(
                    layout.vertical
                            ? first.getTop() < second.getTop()
                            : first.getLeft() < second.getLeft(),
                    "app targets match physical order");
        } else
            check(
                    !root.findViewWithTag("layout-direction").isShown(),
                    "single view hides split-only settings");
    }

    private void checkDestination(int pane, String label) throws Exception {
        await(() -> session.appSelectionBox() != null, "native pressed feedback visible: " + label);
        SplitLayout.Box actual = session.appSelectionBox(), expected = box(pane);
        check(
                actual != null
                        && actual.x == expected.x
                        && actual.y == expected.y
                        && actual.width == expected.width
                        && actual.height == expected.height,
                "highlight matches captured destination: " + label);
    }

    private void phoneTarget(int position) throws Exception {
        await(
                () -> {
                    boolean[] ready = {false};
                    runner.runOnMainSync(
                            () -> {
                                Dialog dialog = phoneDialog();
                                if (dialog == null || !dialog.isShowing()) return;
                                View decor = dialog.getWindow().getDecorView();
                                View target = decor.findViewWithTag("layout-target-" + position);
                                ready[0] =
                                        decor.isShown()
                                                && decor.hasWindowFocus()
                                                && !decor.isLayoutRequested()
                                                && target != null
                                                && target.isShown()
                                                && target.isEnabled()
                                                && target.getWidth() > 0
                                                && target.getHeight() > 0
                                                && !target.isLayoutRequested();
                            });
                    return ready[0];
                },
                "phone launch target has a ready native input window");
        Rect bounds = new Rect();
        runner.runOnMainSync(
                () -> {
                    View target =
                            phoneDialog()
                                    .getWindow()
                                    .getDecorView()
                                    .findViewWithTag("layout-target-" + position);
                    check(
                            target != null
                                    && target.isShown()
                                    && target.isEnabled()
                                    && target.getGlobalVisibleRect(bounds),
                            "phone launch target visible and enabled");
                    int[] origin = new int[2];
                    target.getRootView().getLocationOnScreen(origin);
                    bounds.offset(origin[0], origin[1]);
                    check(
                            bounds.width() > 0 && bounds.height() > 0,
                            "phone target has nonempty clipped screen bounds");
                    results.append("PHONE_TARGET pane=")
                            .append(position)
                            .append(" root_origin=")
                            .append(origin[0])
                            .append(',')
                            .append(origin[1])
                            .append(" bounds=")
                            .append(bounds.toShortString())
                            .append('\n');
                });
        runner.getUiAutomation().waitForIdle(250, 5000);
        DeviceTestSupport.shell(
                runner, "input -d 0 tap " + bounds.centerX() + " " + bounds.centerY());
        await(
                () ->
                        phoneDialog() != null
                                && phoneDialog()
                                                .getWindow()
                                                .getDecorView()
                                                .findViewWithTag("phone-menu-apps")
                                        != null,
                "one phone target tap opens app picker");
    }

    private View launchButton(boolean monitor, int position) throws Exception {
        View root = monitor ? session.monitorShellView() : phoneDialog().getWindow().getDecorView();
        android.widget.EditText search =
                root.findViewWithTag(monitor ? "monitor-app-search" : "phone-app-search");
        runner.runOnMainSync(() -> search.setText("DexDroid input test"));
        View[] button = {null};
        await(
                () -> {
                    if (button[0] == null)
                        runner.runOnMainSync(
                                () -> {
                                    View candidate =
                                            root.findViewWithTag("launch.dev.dexdroid." + position);
                                    if (candidate != null
                                            && candidate.isShown()
                                            && candidate.getWidth() > 0
                                            && candidate.getHeight() > 0) button[0] = candidate;
                                });
                    return button[0] != null;
                },
                "spatial launch button available " + position);
        return button[0];
    }

    private void buttonContact(View button, int action) {
        runner.runOnMainSync(
                () -> {
                    long now = SystemClock.uptimeMillis();
                    MotionEvent event =
                            MotionEvent.obtain(
                                    now,
                                    now,
                                    action,
                                    button.getWidth() / 2f,
                                    button.getHeight() / 2f,
                                    0);
                    try {
                        button.dispatchTouchEvent(event);
                    } finally {
                        event.recycle();
                    }
                });
    }

    private void launchSecondFromLayout() throws Exception {
        Rect before = new Rect();
        runner.runOnMainSync(
                () ->
                        phone.getWindow()
                                .getDecorView()
                                .findViewWithTag("touchpad")
                                .getGlobalVisibleRect(before));
        menu("openApps");
        check(
                phoneDialog().getWindow().getDecorView().findViewWithTag("phone-menu-apps") != null,
                "main Apps directly opens combined launcher in split");
        check(session.appSelectionBox() == null, "open launcher has no persistent highlight");
        View right = launchButton(false, 1);
        buttonContact(right, MotionEvent.ACTION_DOWN);
        await(() -> session.appSelectionBox() != null, "pressed action displays destination");
        checkDestination(1, "pressed phone right action");
        capture("phone-combined-launcher", false);
        capture("monitor-phone-destination", true);
        buttonContact(right, MotionEvent.ACTION_CANCEL);
        await(
                () -> session.appSelectionBox() == null,
                "cancelled action clears destination without launch");
        runner.runOnMainSync(
                () -> {
                    SessionService.AppSelection replacement = session.beginAppSelection(0);
                    right.performClick();
                    check(
                            session.currentAppSelection(replacement),
                            "stale row action cannot replace newer picker ownership");
                    session.finishAppSelection(replacement);
                });
        closePhoneMenu();
        menu("layoutSettings");
        phoneTarget(1);
        runner.runOnMainSync(
                () ->
                        phoneDialog()
                                .getWindow()
                                .getDecorView()
                                .findViewWithTag("phone-menu-up")
                                .performClick());
        await(
                () ->
                        phoneDialog() != null
                                && phoneDialog()
                                                .getWindow()
                                                .getDecorView()
                                                .findViewWithTag("phone-menu-layout")
                                        != null,
                "picker header returns to Layout");
        phoneTarget(1);
        DeviceTestSupport.shell(runner, "input -d 0 keyevent 4");
        await(
                () ->
                        phoneDialog() != null
                                && phoneDialog()
                                                .getWindow()
                                                .getDecorView()
                                                .findViewWithTag("phone-menu-layout")
                                        != null,
                "picker system Back returns to Layout");
        closePhoneMenu();
        menu("openApps");
        View target = launchButton(false, 1);
        runner.runOnMainSync(target::performClick);
        ComponentName input = new ComponentName(runner.getTargetContext(), InputTestActivity.class);
        await(
                () ->
                        !session.busy
                                && session.split.selectedComponent() != null
                                && session.split.selectedComponent().equals(input),
                "spatial row action launches chosen app");
        check(
                session.split.selected() == 1 && session.appSelectionBox() == null,
                "row launches right and clears destination");
        await(() -> phoneDialog() == null, "successful launch dismisses picker");
        Rect after = new Rect();
        runner.runOnMainSync(
                () ->
                        phone.getWindow()
                                .getDecorView()
                                .findViewWithTag("touchpad")
                                .getGlobalVisibleRect(after));
        check(before.equals(after), "combined launcher preserves exact controller touchpad bounds");
        inputTap();
        menu("openApps");
        View owner = phoneDialog().getWindow().getDecorView();
        android.widget.EditText search = owner.findViewWithTag("phone-app-search");
        View staleModeAction = launchButton(false, 1);
        runner.runOnMainSync(
                () -> {
                    search.setText("DexDroid input test");
                    owner.findViewWithTag("launcher-mode-2").performClick();
                });
        ready("launcher changes to up-down in place");
        await(
                () ->
                        owner.findViewWithTag("launch.dev.dexdroid.0")
                                        instanceof android.widget.Button
                                && ((android.widget.Button)
                                                owner.findViewWithTag("launch.dev.dexdroid.0"))
                                        .getText()
                                        .toString()
                                        .equals("Up"),
                "vertical launcher updates spatial labels without replacing search");
        check(
                search.getText().toString().equals("DexDroid input test")
                        && owner == phoneDialog().getWindow().getDecorView(),
                "mode change retains dialog and search query");
        runner.runOnMainSync(staleModeAction::performClick);
        check(
                owner == phoneDialog().getWindow().getDecorView() && !session.busy,
                "retired row callback cannot launch after in-place mode change");
        capture("phone-combined-up-down", false);
        closePhoneMenu();
        runner.runOnMainSync(() -> session.split.swap());
        ready("swapped launcher layout");
        menu("openApps");
        View up = launchButton(false, 0);
        buttonContact(up, MotionEvent.ACTION_DOWN);
        checkDestination(1, "Up resolves swapped spatial pane");
        buttonContact(up, MotionEvent.ACTION_CANCEL);
        closePhoneMenu();
        runner.runOnMainSync(() -> session.split.swap());
        ready("launcher order restored");
        runner.runOnMainSync(() -> session.split.direction(false));
        ready("launcher direction restored");
    }

    private void pendingModeOwnership() throws Exception {
        menu("openApps");
        View root = phoneDialog().getWindow().getDecorView();
        runner.runOnMainSync(
                () -> {
                    int mode = session.split.state().vertical ? 1 : 2;
                    root.findViewWithTag("launcher-mode-" + mode).performClick();
                    check(session.busy, "phone mode change is pending before Stop");
                    session.pauseDesktop();
                });
        await(() -> session.id < 0 && !session.busy, "pending mode stopped");
        android.widget.ListView list = root.findViewWithTag("phone-app-list");
        await(() -> list.getAdapter().getCount() > 0, "retained stopped picker has app rows");
        runner.runOnMainSync(
                () -> {
                    View row = list.getAdapter().getView(0, null, list);
                    list.performItemClick(row, 0, list.getAdapter().getItemId(0));
                    check(
                            session.id < 0 && !session.busy,
                            "stopped pending launcher cannot restart desktop");
                    check(phoneDialog() != null, "obsolete row does not dismiss retained picker");
                    SessionService.AppSelection replacement = session.beginAppSelection(0);
                    list.performItemClick(row, 0, list.getAdapter().getItemId(0));
                    check(
                            session.currentAppSelection(replacement),
                            "stopped pending launcher cannot adopt replacement picker");
                    session.finishAppSelection(replacement);
                });
        closePhoneMenu();
        runner.runOnMainSync(session::ensureDesktop);
        ready("desktop restarts after pending mode cancellation");
        await(() -> session.splitEnabled(), "confirmed split restored after cancelled mode");

        LauncherControls[] pending = {null};
        runner.runOnMainSync(
                () -> {
                    pending[0] = new LauncherControls(session, phone, 1);
                    android.widget.LinearLayout host = new android.widget.LinearLayout(phone);
                    pending[0].addModes(host);
                    int mode = session.split.state().vertical ? 1 : 2;
                    host.findViewWithTag("launcher-mode-" + mode).performClick();
                    check(session.busy, "detached owner mode operation is pending");
                });
        ready("mode completes before retired owner refresh");
        runner.runOnMainSync(
                () -> {
                    SessionService.AppSelection replacement = session.beginAppSelection(0);
                    session.finishAppSelection(replacement);
                    pending[0].refresh();
                    check(
                            !pending[0].current(),
                            "completed mode cannot revive owner after newer picker closes");
                    check(
                            !pending[0].launch(
                                    new ComponentName(phone, InputTestActivity.class), null, 1),
                            "retired mode owner cannot launch into live replacement session");
                    SessionService.AppSelection current = session.beginAppSelection(0);
                    pending[0].refresh();
                    pending[0].close();
                    check(
                            session.currentAppSelection(current),
                            "retired mode cleanup preserves current picker");
                    session.finishAppSelection(current);
                });
    }

    private void openMonitorApps() throws Exception {
        float[] start = physical(session.width() * .5f, session.height() - 4),
                end = physical(session.width() * .5f, session.height() * .65f);
        pointer(0, start, InputDevice.SOURCE_TOUCHSCREEN, 0);
        pointer(2, end, InputDevice.SOURCE_TOUCHSCREEN, 0);
        pointer(1, end, InputDevice.SOURCE_TOUCHSCREEN, 0);
        await(
                () -> session.monitorShellPage().equals("home") && !session.monitorShellAnimating(),
                "fullscreen bottom edge opens Apps");
    }

    private void fullscreenLauncher() throws Exception {
        for (int surface = 0; surface < 2; surface++) {
            for (int order = 0; order < 2; order++) {
                if (session.split.state().maximized >= 0) {
                    runner.runOnMainSync(() -> session.split.maximize());
                    ready("restore before fullscreen launcher fixture");
                }
                for (int pane = 0; pane < 2; pane++) {
                    final int p = pane;
                    runner.runOnMainSync(() -> session.split.select(p));
                    runner.runOnMainSync(() -> session.split.closeApp());
                    ready("empty fullscreen launcher pane " + pane);
                }
                if (session.split.state().swapped != (order == 1)) {
                    runner.runOnMainSync(() -> session.split.swap());
                    ready("fullscreen launcher order");
                }
                int target = session.split.state().paneAtPosition(1), visible = 1 - target;
                runner.runOnMainSync(() -> session.split.select(visible));
                runner.runOnMainSync(() -> session.split.maximize());
                ready("opposite pane fullscreen");
                boolean monitor = surface == 1;
                if (monitor) openMonitorApps();
                else menu("openApps");
                View action = launchButton(monitor, 1);
                buttonContact(action, MotionEvent.ACTION_DOWN);
                await(() -> session.appSelectionBox() != null, "fullscreen launch preview");
                check(
                        !session.busy && session.split.state().maximized == visible,
                        "pressing hidden destination does not resize/select");
                check(
                        session.appSelectionBox().width == session.width()
                                && session.appSelectionBox().height == session.height(),
                        "fullscreen destination preview covers resulting output");
                buttonContact(action, MotionEvent.ACTION_CANCEL);
                check(
                        !session.busy
                                && session.appSelectionBox() == null
                                && session.split.state().maximized == visible,
                        "cancel hidden destination leaves fullscreen untouched");
                if (order == 0) runner.runOnMainSync(action::performClick);
                else {
                    buttonContact(action, MotionEvent.ACTION_DOWN);
                    buttonContact(action, MotionEvent.ACTION_UP);
                }
                ComponentName app = new ComponentName(phone, InputTestActivity.class);
                await(
                        () -> !session.busy && app.equals(session.split.selectedComponent()),
                        "one fullscreen destination action completes launch");
                int display = session.split.displayId(target);
                await(
                        () -> {
                            try {
                                for (DisplayTasks.Root task :
                                        DisplayTasks.parse(
                                                DeviceTestSupport.shell(runner, "am stack list"),
                                                app.flattenToString()))
                                    if (task.display == display && task.matches && task.visible)
                                        return true;
                                return false;
                            } catch (Exception error) {
                                throw new IllegalStateException(error);
                            }
                        },
                        "fullscreen launch reaches actual captured display");
                check(
                        session.split.state().maximized == target,
                        "hidden launch reveals target fullscreen through Swap");
                if (monitor)
                    await(
                            () -> session.monitorShellPage().equals("closed"),
                            "monitor fullscreen launch closes picker");
                else await(() -> phoneDialog() == null, "phone fullscreen launch closes picker");
                inputTap(target);
                capture("fullscreen-launch-s" + surface + "-swap" + order, true);
            }
        }
        runner.runOnMainSync(() -> session.split.closeApp());
        ready("empty visible pane before pending fullscreen launch");
        int target = 1 - session.split.state().maximized;
        menu("openApps");
        View action = launchButton(false, session.split.state().swapped ? 1 - target : target);
        runner.runOnMainSync(
                () -> {
                    action.performClick();
                    check(session.busy, "hidden-pane launch resize is pending before Stop");
                    session.pauseDesktop();
                });
        check(session.id < 0 && !session.busy, "Stop cancels pending hidden-pane launch");
        runner.runOnMainSync(session::ensureDesktop);
        ready("restart after hidden-pane launch cancellation");
        await(() -> session.splitEnabled(), "split restored after pending launch Stop");
        check(!session.split.anyApp(), "pending hidden-pane app is not launched after restart");
    }

    private void emptyBackgroundTransitions() throws Exception {
        for (int rotation = 0; rotation < 4; rotation++) {
            final int r = rotation;
            runner.runOnMainSync(() -> session.rotate(r, false));
            ready("empty background rotation " + r);
            for (int direction = 0; direction < 2; direction++) {
                final boolean vertical = direction == 1;
                runner.runOnMainSync(() -> session.split.direction(vertical));
                ready("empty direction transition");
                runner.runOnMainSync(() -> session.split.ratio(vertical ? .25f : .65f));
                ready("empty ratio transition");
                runner.runOnMainSync(() -> session.split.swap());
                ready("empty swap transition");
                capture("empty-transition-r" + r + "-d" + direction, true);
                for (int pane = 0; pane < 2; pane++) {
                    final int index = pane;
                    SplitLayout.Box bounds = box(pane);
                    runner.runOnMainSync(
                            () -> {
                                View idle =
                                        session.monitorInputView()
                                                .findViewWithTag("split-idle-" + index);
                                View icon =
                                        session.monitorInputView()
                                                .findViewWithTag("split-idle-apps-" + index);
                                check(
                                        idle.isShown()
                                                && idle.getWidth() == bounds.width
                                                && idle.getHeight() == bounds.height,
                                        "idle background matches transitioned pane bounds "
                                                + r
                                                + "/"
                                                + vertical
                                                + "/"
                                                + index
                                                + " actual="
                                                + idle.getWidth()
                                                + "x"
                                                + idle.getHeight()
                                                + " expected="
                                                + bounds.width
                                                + "x"
                                                + bounds.height
                                                + " shown="
                                                + idle.isShown()
                                                + " visibility="
                                                + idle.getVisibility()
                                                + " parent="
                                                + ((View) idle.getParent()).getWidth()
                                                + "x"
                                                + ((View) idle.getParent()).getHeight()
                                                + " requested="
                                                + idle.getLayoutParams().width
                                                + "x"
                                                + idle.getLayoutParams().height);
                                check(
                                        Math.abs(
                                                                icon.getX()
                                                                        + icon.getWidth() / 2f
                                                                        - bounds.width / 2f)
                                                        <= 1
                                                && Math.abs(
                                                                icon.getY()
                                                                        + icon.getHeight() / 2f
                                                                        - bounds.height / 2f)
                                                        <= 1,
                                        "idle icon remains centered after transition");
                            });
                    Bitmap actual =
                            BitmapFactory.decodeFile(
                                    new File(
                                                    runner.getTargetContext().getExternalCacheDir(),
                                                    "split-acceptance/empty-transition-r"
                                                            + r
                                                            + "-d"
                                                            + direction
                                                            + ".png")
                                            .toString());
                    Bitmap expected =
                            Bitmap.createBitmap(
                                    bounds.width, bounds.height, Bitmap.Config.ARGB_8888);
                    runner.runOnMainSync(
                            () -> {
                                View old =
                                        session.monitorInputView()
                                                .findViewWithTag("split-idle-" + index);
                                IdleDesktop reference = new IdleDesktop(old.getContext(), () -> {});
                                reference.measure(
                                        View.MeasureSpec.makeMeasureSpec(
                                                bounds.width, View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(
                                                bounds.height, View.MeasureSpec.EXACTLY));
                                reference.layout(0, 0, bounds.width, bounds.height);
                                reference.draw(new Canvas(expected));
                            });
                    try {
                        for (float[] point : new float[][] {{.2f, .3f}, {.8f, .7f}}) {
                            int x = Math.round(bounds.width * point[0]),
                                    y = Math.round(bounds.height * point[1]);
                            float[] mapped = physical(bounds.x + x, bounds.y + y);
                            int observed =
                                    actual.getPixel(Math.round(mapped[0]), Math.round(mapped[1]));
                            int wanted = expected.getPixel(x, y);
                            check(
                                    Math.abs(Color.red(observed) - Color.red(wanted)) <= 2
                                            && Math.abs(Color.green(observed) - Color.green(wanted))
                                                    <= 2
                                            && Math.abs(Color.blue(observed) - Color.blue(wanted))
                                                    <= 2,
                                    "actual empty-pane background matches fresh canvas after transition "
                                            + r
                                            + "/"
                                            + direction
                                            + "/"
                                            + pane);
                        }
                    } finally {
                        actual.recycle();
                        expected.recycle();
                    }
                }
                runner.runOnMainSync(() -> session.split.maximize());
                ready("empty fullscreen transition");
                capture("empty-fullscreen-r" + r + "-d" + direction, true);
                runner.runOnMainSync(() -> session.split.maximize());
                ready("empty restore transition");
            }
        }
        runner.runOnMainSync(() -> session.rotate(0, false));
        ready("empty transitions return landscape");
        menu("openApps");
        Dialog owner = phoneDialog();
        for (int mode : new int[] {0, 1, 0, 2, 1}) {
            applyLauncherMode(false, owner, mode);
        }
        launcherFullscreenModes(false);
        closePhoneMenu();
    }

    private void monitorLauncherModes() throws Exception {
        pointer(
                0,
                physical(session.width() * .5f, session.height() - 4),
                InputDevice.SOURCE_TOUCHSCREEN,
                0);
        pointer(
                2,
                physical(session.width() * .5f, session.height() * .65f),
                InputDevice.SOURCE_TOUCHSCREEN,
                0);
        pointer(
                1,
                physical(session.width() * .5f, session.height() * .65f),
                InputDevice.SOURCE_TOUCHSCREEN,
                0);
        await(
                () -> session.monitorShellPage().equals("home") && !session.monitorShellAnimating(),
                "bottom gesture directly opens combined split launcher");
        for (int mode : new int[] {0, 1, 0, 2, 1}) {
            applyLauncherMode(true, null, mode);
            capture("monitor-launcher-mode-" + mode, true);
            launcherPixels("monitor-launcher-mode-" + mode, mode, true, null);
        }
        launcherFullscreenModes(true);
        launchButton(true, 1);
        int chosen = session.split.state().paneAtPosition(1);
        controllerMenuTap("launch.dev.dexdroid.1", "closed");
        ready("monitor row launches chosen app");
        check(
                session.split.selected() == chosen
                        && session.split.selectedComponent() != null
                        && session.split
                                .selectedComponent()
                                .getClassName()
                                .equals(InputTestActivity.class.getName()),
                "monitor row launches into explicit spatial destination");
        runner.runOnMainSync(() -> session.split.direction(true));
        ready("mixed occupancy direction transition");
        runner.runOnMainSync(() -> session.split.ratio(.35f));
        ready("mixed occupancy ratio transition");
        int emptyPane = 1 - chosen;
        runner.runOnMainSync(
                () -> {
                    View idle =
                            session.monitorInputView().findViewWithTag("split-idle-" + emptyPane);
                    SplitLayout.Box bounds = box(emptyPane);
                    check(
                            idle.isShown()
                                    && idle.getWidth() == bounds.width
                                    && idle.getHeight() == bounds.height,
                            "mixed occupancy idle follows resized pane");
                });
        capture("monitor-mixed-background-transition", true);
        runner.runOnMainSync(() -> session.split.closeApp());
        ready("monitor launcher fixture closed");
    }

    private void chooseLauncherMode(boolean monitor, Dialog owner, int mode) throws Exception {
        if (monitor) {
            float[] target = menuPoint("launcher-mode-" + mode);
            tap(physical(target[0], target[1]), InputDevice.SOURCE_TOUCHSCREEN);
        } else {
            runner.runOnMainSync(
                    () ->
                            owner.getWindow()
                                    .getDecorView()
                                    .findViewWithTag("launcher-mode-" + mode)
                                    .performClick());
        }
    }

    private void applyLauncherMode(boolean monitor, Dialog owner, int mode) throws Exception {
        String surface = monitor ? "monitor" : "phone";
        chooseLauncherMode(monitor, owner, mode);
        await(
                () ->
                        !session.busy
                                && (mode == 0
                                        ? !session.splitEnabled()
                                        : session.splitEnabled()
                                                && session.split.state().vertical == (mode == 2)
                                                && session.split.state().maximized < 0),
                surface + " mode immediately applies visible layout " + mode);
        ready(surface + " direct launcher mode " + mode);
        check(
                monitor ? session.monitorShellPage().equals("home") : owner == phoneDialog(),
                surface + " direct mode retains launcher");
        check(
                session.prefs.getBoolean("split.enabled", false) == (mode != 0),
                surface + " direct mode persists enabled state");
        if (mode != 0)
            check(
                    session.prefs.getBoolean("split.vertical", false) == (mode == 2),
                    surface + " direct mode persists direction");
        runner.runOnMainSync(
                () -> {
                    View root =
                            monitor ? session.monitorShellView() : owner.getWindow().getDecorView();
                    View selected = root.findViewWithTag("launcher-mode-" + mode);
                    check(
                            selected.isSelected() && selected.isEnabled(),
                            surface + " applied mode selected and operable");
                    SplitWorkspace workspace = session.split;
                    selected.performClick();
                    check(
                            !session.busy && session.split == workspace,
                            surface
                                    + " repeated visible mode does not resize or recreate workspace");
                });
    }

    private Dialog openModeLauncher(boolean monitor) throws Exception {
        if (!monitor) {
            menu("openApps");
            return phoneDialog();
        }
        pointer(
                0,
                physical(session.width() * .5f, session.height() - 4),
                InputDevice.SOURCE_TOUCHSCREEN,
                0);
        pointer(
                2,
                physical(session.width() * .5f, session.height() * .65f),
                InputDevice.SOURCE_TOUCHSCREEN,
                0);
        pointer(
                1,
                physical(session.width() * .5f, session.height() * .65f),
                InputDevice.SOURCE_TOUCHSCREEN,
                0);
        await(
                () -> session.monitorShellPage().equals("home") && !session.monitorShellAnimating(),
                "bottom gesture opens mode launcher");
        return null;
    }

    private void closeModeLauncher(boolean monitor) throws Exception {
        if (monitor) controllerMenuTap("monitor-close", "closed");
        else closePhoneMenu();
    }

    private void launcherFullscreenModes(boolean monitor) throws Exception {
        String surface = monitor ? "monitor" : "phone";
        closeModeLauncher(monitor);
        launcherModeRollback(monitor);
        for (int rotation = 0; rotation < 4; rotation++) {
            final int r = rotation;
            runner.runOnMainSync(() -> session.rotate(r, false));
            ready(surface + " launcher fullscreen mode rotation " + r);
            for (int mode : new int[] {1, 2}) {
                Dialog owner = openModeLauncher(monitor);
                applyLauncherMode(monitor, owner, mode);
                closeModeLauncher(monitor);
                runner.runOnMainSync(() -> session.split.maximize());
                ready(surface + " selected split direction fullscreen " + mode + "/" + r);
                check(
                        session.split.state().maximized >= 0,
                        surface + " fullscreen precondition holds");
                owner = openModeLauncher(monitor);
                applyLauncherMode(monitor, owner, mode);
                runner.runOnMainSync(
                        () -> {
                            for (int pane = 0; pane < 2; pane++)
                                check(
                                        session.monitorInputView()
                                                .findViewWithTag("split-pane-" + pane)
                                                .isShown(),
                                        surface + " choosing direction reveals pane " + pane);
                        });
                String captureName = surface + "-mode-restored-r" + r + "-m" + mode;
                capture(captureName, monitor);
                // These pixel bounds are screen-aligned at rotation zero. The other
                // rotations retain raw captures for explicit rendered inspection.
                if (r == 0) launcherPixels(captureName, mode, monitor, owner);
                closeModeLauncher(monitor);
            }
        }
        runner.runOnMainSync(() -> session.rotate(0, false));
        ready(surface + " launcher modes return landscape");
        Dialog owner = openModeLauncher(monitor);
        applyLauncherMode(monitor, owner, 1);
    }

    private void launcherModeRollback(boolean monitor) throws Exception {
        String surface = monitor ? "monitor" : "phone";
        for (int mode : new int[] {1, 2}) {
            Dialog owner = openModeLauncher(monitor);
            applyLauncherMode(monitor, owner, mode);
            closeModeLauncher(monitor);
            runner.runOnMainSync(() -> session.split.maximize());
            ready(surface + " rollback fullscreen precondition " + mode);
            SplitWorkspace workspace = session.split;
            int maximized = workspace.state().maximized;
            int first = session.id, second = workspace.displayId(1);
            java.util.Map<String, ?> saved = session.prefs.getAll();
            owner = openModeLauncher(monitor);
            Dialog retainedOwner = owner;
            java.lang.reflect.Field field = SessionService.class.getDeclaredField("bridge");
            field.setAccessible(true);
            IDeskService original = session.primaryBridge();
            java.util.concurrent.atomic.AtomicInteger resizeCalls =
                    new java.util.concurrent.atomic.AtomicInteger();
            IDeskService failing =
                    (IDeskService)
                            java.lang.reflect.Proxy.newProxyInstance(
                                    IDeskService.class.getClassLoader(),
                                    new Class<?>[] {IDeskService.class},
                                    (proxy, method, arguments) -> {
                                        if (method.getName().equals("resize")
                                                && resizeCalls.incrementAndGet() == 1)
                                            throw new android.os.RemoteException(
                                                    "Acceptance injected restore resize failure");
                                        try {
                                            return method.invoke(original, arguments);
                                        } catch (
                                                java.lang.reflect.InvocationTargetException error) {
                                            throw error.getCause();
                                        }
                                    });
            try {
                runner.runOnMainSync(
                        () -> {
                            try {
                                field.set(session, failing);
                            } catch (IllegalAccessException error) {
                                throw new AssertionError(error);
                            }
                        });
                chooseLauncherMode(monitor, owner, mode);
                await(
                        () ->
                                !session.busy
                                        && session.status.contains(
                                                "Resize failed; retained previous settings"),
                        surface + " injected mode resize fails with successful rollback " + mode);
                ready(surface + " rolled-back native fullscreen dimensions " + mode);
                check(
                        resizeCalls.get() == 2,
                        surface + " one rejected resize and real rollback resize");
                check(
                        session.split == workspace
                                && workspace.state().maximized == maximized
                                && session.id == first
                                && workspace.displayId(1) == second,
                        surface
                                + " rollback preserves fullscreen workspace and display identities");
                check(
                        saved.equals(session.prefs.getAll()),
                        surface + " rejected mode preserves saved preferences");
                runner.runOnMainSync(
                        () -> {
                            View root =
                                    monitor
                                            ? session.monitorShellView()
                                            : retainedOwner.getWindow().getDecorView();
                            for (int i = 0; i < 3; i++)
                                check(
                                        !root.findViewWithTag("launcher-mode-" + i).isEnabled(),
                                        surface + " rejected mode picker remains inert " + i);
                        });
                View action = launchButton(monitor, 0);
                runner.runOnMainSync(action::performClick);
                check(
                        monitor
                                ? session.monitorShellPage().equals("home")
                                : phoneDialog() == owner,
                        surface + " rejected mode picker cannot launch or dismiss");
                check(
                        !session.busy && workspace.state().maximized == maximized,
                        surface + " rejected picker app action cannot reveal hidden pane");
                capture(surface + "-mode-rollback-m" + mode, monitor);
            } finally {
                runner.runOnMainSync(
                        () -> {
                            try {
                                if (field.get(session) == failing) field.set(session, original);
                            } catch (IllegalAccessException error) {
                                throw new AssertionError(error);
                            }
                        });
                closeModeLauncher(monitor);
            }
            owner = openModeLauncher(monitor);
            applyLauncherMode(monitor, owner, mode);
            closeModeLauncher(monitor);
        }
    }

    private void launcherPixels(String name, int mode, boolean monitor, Dialog owner)
            throws Exception {
        Rect[] regions = new Rect[4];
        runner.runOnMainSync(
                () -> {
                    View root =
                            monitor ? session.monitorShellView() : owner.getWindow().getDecorView();
                    float density = root.getResources().getDisplayMetrics().density;
                    for (int i = 0; i < 3; i++) {
                        regions[i] = new Rect();
                        check(
                                root.findViewWithTag("launcher-mode-" + i)
                                        .getGlobalVisibleRect(regions[i]),
                                "mode label has visible bounds");
                        if (!monitor) {
                            View button = root.findViewWithTag("launcher-mode-" + i);
                            int[] screen = new int[2];
                            button.getLocationOnScreen(screen);
                            regions[i].set(
                                    screen[0],
                                    screen[1],
                                    screen[0] + button.getWidth(),
                                    screen[1] + button.getHeight());
                        }
                        regions[i].inset(Math.round(10 * density), Math.round(10 * density));
                    }
                    android.widget.ListView list =
                            root.findViewWithTag(monitor ? "monitor-app-list" : "phone-app-list");
                    View first = list.getChildAt(0);
                    View label =
                            first instanceof android.widget.TextView
                                    ? first
                                    : ((ViewGroup) first).getChildAt(0);
                    regions[3] = new Rect();
                    check(
                            label.getGlobalVisibleRect(regions[3]),
                            "app label has visible bounds after mode change");
                    if (!monitor) {
                        int[] screen = new int[2];
                        label.getLocationOnScreen(screen);
                        regions[3].set(
                                screen[0],
                                screen[1],
                                screen[0] + label.getWidth(),
                                screen[1] + label.getHeight());
                    }
                    regions[3].left += Math.round(64 * density);
                    regions[3].right -= Math.round(8 * density);
                });
        Bitmap image =
                BitmapFactory.decodeFile(
                        new File(
                                        runner.getTargetContext().getExternalCacheDir(),
                                        "split-acceptance/" + name + ".png")
                                .toString());
        try {
            for (int i = 0; i < regions.length; i++) {
                Rect region = regions[i];
                int ink = 0;
                for (int y = region.top; y < region.bottom; y++)
                    for (int x = region.left; x < region.right; x++) {
                        int pixel = image.getPixel(x, y);
                        boolean dark = i < 3 && i == mode;
                        if (dark
                                ? Color.red(pixel) < 70
                                        && Color.green(pixel) < 70
                                        && Color.blue(pixel) < 70
                                : Color.red(pixel) > 200
                                        && Color.green(pixel) > 200
                                        && Color.blue(pixel) > 200) ink++;
                    }
                check(
                        ink > 10,
                        "actual rendered launcher label ink after mode " + mode + " region " + i);
            }
        } finally {
            image.recycle();
        }
    }

    private void keyRelay() throws Exception {
        nativeReady();
        SplitLayout.Box box = box(0);
        tap(
                physical(box.x + state.getFloat("editorX"), box.y + state.getFloat("editorY")),
                InputDevice.SOURCE_MOUSE);
        String before = state.getString("text", "");
        int initialUps = state.getInt("keyUps");
        long time = SystemClock.uptimeMillis();
        runner.runOnMainSync(
                () -> {
                    for (int action : new int[] {KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP})
                        check(
                                session.hardwareKey(
                                        new KeyEvent(
                                                time,
                                                time,
                                                action,
                                                KeyEvent.KEYCODE_A,
                                                0,
                                                KeyEvent.META_SHIFT_ON,
                                                -1,
                                                0,
                                                0,
                                                InputDevice.SOURCE_KEYBOARD)),
                                "controller receives complete hardware key " + action);
                });
        await(
                () ->
                        state.getString("text", "").equals(before + "A")
                                && state.getInt("keyUps") == initialUps + 1,
                "native EditText receives shifted key through product relay (component boundary)");
        int ups = state.getInt("keyUps");
        runner.runOnMainSync(
                () -> {
                    session.hardwareKey(
                            new KeyEvent(
                                    time,
                                    time,
                                    0,
                                    KeyEvent.KEYCODE_SHIFT_LEFT,
                                    0,
                                    0,
                                    -1,
                                    0,
                                    0,
                                    InputDevice.SOURCE_KEYBOARD));
                    session.split.select(1);
                    session.hardwareKey(
                            new KeyEvent(
                                    time,
                                    time,
                                    1,
                                    KeyEvent.KEYCODE_SHIFT_LEFT,
                                    0,
                                    0,
                                    -1,
                                    0,
                                    0,
                                    InputDevice.SOURCE_KEYBOARD));
                });
        await(
                () -> state.getInt("keyUps") == ups + 1,
                "held key release reaches original app after pane selection");
        runner.runOnMainSync(() -> session.split.select(0));
        int downs = state.getInt("keyDowns"), releases = state.getInt("keyUps");
        runner.runOnMainSync(
                () ->
                        session.hardwareKey(
                                new KeyEvent(
                                        time,
                                        time,
                                        0,
                                        KeyEvent.KEYCODE_SHIFT_LEFT,
                                        0,
                                        0,
                                        -1,
                                        0,
                                        0,
                                        InputDevice.SOURCE_KEYBOARD)));
        await(
                () -> state.getInt("keyDowns") == downs + 1,
                "held modifier reaches app before menu opens");
        long cancelRequested = SystemClock.uptimeMillis();
        menu("layoutSettings");
        await(() -> state.getInt("keyUps") == releases + 1, "phone menu cancels app-held modifier");
        check(
                (state.getInt("keyUpFlags") & KeyEvent.FLAG_CANCELED) != 0,
                "menu cancellation marks key release cancelled");
        check(
                state.getLong("keyUpTime") >= cancelRequested && state.getInt("keyUpRepeat") == 0,
                "cancelled key release has fresh timestamp and no repeat");
        closePhoneMenu();
        runner.runOnMainSync(
                () ->
                        check(
                                session.hardwareKey(
                                        new KeyEvent(
                                                time,
                                                time,
                                                1,
                                                KeyEvent.KEYCODE_SHIFT_LEFT,
                                                0,
                                                0,
                                                -1,
                                                0,
                                                0,
                                                InputDevice.SOURCE_KEYBOARD)),
                                "late physical modifier release consumed"));
        session.split.fence();
        Thread.sleep(150);
        check(
                state.getInt("keyUps") == releases + 1,
                "menu does not duplicate original key release");
    }

    private void paneApps() throws Exception {
        SplitLayout.Box pane = box(0);
        tap(
                physical(pane.x + pane.width / 2f, pane.y + pane.height / 2f),
                InputDevice.SOURCE_TOUCHSCREEN);
        await(
                () -> session.monitorShellPage().equals("home") && !session.monitorShellAnimating(),
                "empty pane Apps opens monitor chooser");
        runner.runOnMainSync(
                () -> {
                    android.widget.TextView hint =
                            session.monitorShellView().findViewWithTag("monitor-app-target");
                    check(
                            hint.getText().toString().contains("beside an app"),
                            "monitor chooser offers explicit row destinations");
                    check(
                            session.monitorShellView()
                                    .findViewWithTag("monitor-app-search")
                                    .isShown(),
                            "monitor chooser search available in split");
                });
        capture("monitor-pane-apps", true);
        tap(
                physical(session.width() * .04f, session.height() * .5f),
                InputDevice.SOURCE_TOUCHSCREEN);
        await(
                () ->
                        session.monitorShellPage().equals("closed")
                                && !session.monitorShellAnimating(),
                "pane chooser dismisses outside");
        menu("apps");
        runner.runOnMainSync(
                () -> {
                    android.widget.TextView hint =
                            phoneDialog()
                                    .getWindow()
                                    .getDecorView()
                                    .findViewWithTag("phone-app-target");
                    check(
                            hint.getText().toString().contains("beside an app"),
                            "phone chooser offers same row destinations");
                });
        capture("phone-pane-apps", false);
        closePhoneMenu();
    }

    private void multi(int action, int[] ids, float[][] logical) throws Exception {
        long now = SystemClock.uptimeMillis();
        if (action == 0) down = now;
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[ids.length];
        for (int i = 0; i < ids.length; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = ids[i];
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            float[] p = physical(logical[i][0], logical[i][1]);
            coords[i].x = p[0];
            coords[i].y = p[1];
            coords[i].pressure = 1;
        }
        MotionEvent event =
                MotionEvent.obtain(
                        down,
                        now,
                        action,
                        ids.length,
                        props,
                        coords,
                        0,
                        0,
                        1,
                        1,
                        -1,
                        0,
                        InputDevice.SOURCE_TOUCHSCREEN,
                        0);
        try {
            check(
                    DeviceTestSupport.injectOnDisplay(event, session.external.getDisplayId()),
                    "multi-contact host injection " + action);
        } finally {
            event.recycle();
        }
    }

    private void inputOwnership() throws Exception {
        SplitLayout.Box pane = box(1);
        float x = pane.x + pane.width * .5f, y = pane.y + pane.height * .65f;
        int pinch = InputTestActivity.pinchEvents, cancels = InputTestActivity.cancelEvents;
        multi(0, new int[] {3}, new float[][] {{x - 30, y}});
        multi(
                MotionEvent.ACTION_POINTER_DOWN | (1 << 8),
                new int[] {3, 9},
                new float[][] {{x - 30, y}, {x + 30, y}});
        multi(2, new int[] {3, 9}, new float[][] {{x - 50, y}, {x + 50, y}});
        await(
                () -> InputTestActivity.pinchEvents > pinch,
                "two physical contacts reach selected pane");
        check(
                InputTestActivity.lastPointer0 == 3 && InputTestActivity.lastPointer1 == 9,
                "native pointer IDs retained");
        runner.runOnMainSync(
                () -> {
                    session.touch(0, new float[] {50}, new float[] {50});
                    session.touch(1, new float[] {50}, new float[] {50});
                });
        check(!session.pointerVisible, "physical contacts block phone pointer input");
        multi(
                MotionEvent.ACTION_POINTER_UP | (1 << 8),
                new int[] {3, 9},
                new float[][] {{x - 50, y}, {x + 50, y}});
        runner.runOnMainSync(session::cancelInput);
        await(
                () -> InputTestActivity.cancelEvents == cancels + 1,
                "remaining contact cancelled once after one finger lifts");
        // Product cancellation ends the relayed app stream, not the shell's
        // physical host contact. Release pointer 3 before the next gesture.
        multi(MotionEvent.ACTION_UP, new int[] {3}, new float[][] {{x - 50, y}});
        check(
                InputTestActivity.cancelEvents == cancels + 1,
                "physical release does not duplicate downstream cancellation");
        runner.runOnMainSync(
                () -> {
                    session.split.select(1);
                    session.cx = x;
                    session.cy = y;
                    session.prefs
                            .edit()
                            .putFloat("scrollSpeed." + session.activePackage, .2f)
                            .apply();
                    session.wheel(0, 1);
                });
        await(
                () -> Math.abs(InputTestActivity.lastWheelV - .2f) < .001f,
                "phone wheel uses selected app speed");
        runner.runOnMainSync(
                () -> {
                    session.prefs
                            .edit()
                            .putInt("app.dev.dexdroid", 2)
                            .putInt("app.dev.dexdroid.test", 3)
                            .apply();
                    session.saveAppSettings();
                });
        check(
                session.prefs.getInt("app.dev.dexdroid", -1) == 2
                        && session.prefs.getInt("app.dev.dexdroid.test", -1) == 3,
                "split Save retains both single-view orientations");
        runner.runOnMainSync(session::resetAppSettings);
        check(
                !session.prefs.contains("app.dev.dexdroid")
                        && session.prefs.getInt("app.dev.dexdroid.test", -1) == 3,
                "reset changes only selected app preferences");
    }

    private void dragDivider() throws Exception {
        SplitLayout.Box bar =
                session.split
                        .state()
                        .divider(session.width(), session.height(), session.split.gap());
        check(bar.width == Math.round(2 * session.dpi / 160f), "visible divider is two logical dp");
        float[]
                start =
                        physical(
                                bar.x + bar.width * .5f + 4 * session.dpi / 160f,
                                bar.y + bar.height * .5f),
                end = physical(session.width() * .6f, session.height() * .5f);
        pointer(0, start, InputDevice.SOURCE_TOUCHSCREEN, 0);
        pointer(2, end, InputDevice.SOURCE_TOUCHSCREEN, 0);
        check(!session.busy, "divider move does not resize native displays per pixel");
        pointer(1, end, InputDevice.SOURCE_TOUCHSCREEN, 0);
        await(
                () -> Math.abs(session.split.state().ratio - .6f) < .002f,
                "divider release updates ratio after native host dispatch");
        ready("divider release commits resize");
        check(Math.abs(session.split.state().ratio - .6f) < .002f, "divider ratio follows gesture");
        nativeClick(InputDevice.SOURCE_MOUSE);
        inputTap();
    }

    private float[] dividerCenter() {
        SplitLayout.Box bar =
                session.split
                        .state()
                        .divider(session.width(), session.height(), session.split.gap());
        return new float[] {bar.x + bar.width * .5f, bar.y + bar.height * .5f};
    }

    private void phoneContact(int action, float[] point, boolean touchpad) {
        runner.runOnMainSync(
                () -> {
                    if (touchpad) {
                        long now = SystemClock.uptimeMillis();
                        MotionEvent event =
                                MotionEvent.obtain(now, now, action, point[0], point[1], 0);
                        try {
                            session.controllerPointer(event, session.width(), session.height());
                        } finally {
                            event.recycle();
                        }
                    } else {
                        session.touch(action, new float[] {point[0]}, new float[] {point[1]});
                    }
                });
    }

    private void phoneAppTap() throws Exception {
        SplitLayout.Box pane = box(1);
        float[] point = {pane.x + pane.width * .5f, pane.y + pane.height * .65f};
        long started = SystemClock.uptimeMillis();
        phoneContact(MotionEvent.ACTION_DOWN, point, false);
        phoneContact(MotionEvent.ACTION_UP, point, false);
        await(
                () -> InputTestActivity.lastUpDownTime >= started,
                "phone native app input remains usable after divider gesture");
    }

    private void phoneDivider() throws Exception {
        float original = session.split.state().ratio;
        float[] start = dividerCenter();
        phoneContact(MotionEvent.ACTION_DOWN, start, false);
        check(!session.split.physicalActive(), "phone divider does not claim physical ownership");
        phoneContact(MotionEvent.ACTION_UP, start, false);
        ready("phone divider tap releases");
        check(
                Math.abs(session.split.state().ratio - original) < .003f,
                "divider tap retains ratio");
        phoneAppTap();
        for (boolean touchpad : new boolean[] {false, true}) {
            float ratio = touchpad ? .55f : .45f;
            float[] end = {session.width() * ratio, session.height() * .5f};
            phoneContact(MotionEvent.ACTION_DOWN, dividerCenter(), touchpad);
            phoneContact(MotionEvent.ACTION_MOVE, end, touchpad);
            check(!session.busy, "phone divider preview defers native resize until release");
            phoneContact(MotionEvent.ACTION_UP, end, touchpad);
            ready("phone divider release commits " + touchpad);
            check(
                    Math.abs(session.split.state().ratio - ratio) < .002f,
                    "phone divider release follows gesture " + touchpad);
            phoneAppTap();
        }
        original = session.split.state().ratio;
        float[] abandoned = {session.width() * .4f, session.height() * .5f};
        phoneContact(MotionEvent.ACTION_DOWN, dividerCenter(), false);
        phoneContact(MotionEvent.ACTION_MOVE, abandoned, false);
        nativeClick(InputDevice.SOURCE_TOUCHSCREEN);
        phoneContact(MotionEvent.ACTION_UP, abandoned, false);
        check(
                !session.busy && Math.abs(session.split.state().ratio - original) < .002f,
                "physical takeover cancels phone divider without committing preview");
        phoneAppTap();
        phoneContact(MotionEvent.ACTION_DOWN, dividerCenter(), true);
        phoneContact(MotionEvent.ACTION_MOVE, abandoned, true);
        runner.runOnMainSync(session::cancelInput);
        phoneContact(MotionEvent.ACTION_UP, abandoned, true);
        check(
                !session.busy && Math.abs(session.split.state().ratio - original) < .002f,
                "cancelled touchpad divider cannot commit from a late release");
        phoneAppTap();
    }

    private Presentation monitorHost() throws Exception {
        java.lang.reflect.Field field = SessionService.class.getDeclaredField("monitor");
        field.setAccessible(true);
        return (Presentation) field.get(session);
    }

    private void controllerMouse(int action, float[] point, float wheel) {
        MotionEvent.PointerProperties property = new MotionEvent.PointerProperties();
        property.id = 0;
        property.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = point[0] / 2;
        coords.y = point[1] / 2;
        coords.pressure = 1;
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL, wheel);
        long now = SystemClock.uptimeMillis();
        MotionEvent event =
                MotionEvent.obtain(
                        now,
                        now,
                        action,
                        1,
                        new MotionEvent.PointerProperties[] {property},
                        new MotionEvent.PointerCoords[] {coords},
                        0,
                        action == MotionEvent.ACTION_DOWN ? MotionEvent.BUTTON_PRIMARY : 0,
                        1,
                        1,
                        -1,
                        0,
                        InputDevice.SOURCE_MOUSE,
                        0);
        try {
            session.controllerPointer(event, session.width() / 2f, session.height() / 2f);
        } finally {
            event.recycle();
        }
    }

    private float[] menuPoint(String tag) {
        Rect rect = new Rect();
        runner.runOnMainSync(
                () ->
                        check(
                                session.monitorShellView()
                                        .findViewWithTag(tag)
                                        .getGlobalVisibleRect(rect),
                                "controller pointer target visible " + tag));
        View decor = session.monitorWindow().getDecorView();
        return Geometry.unmap(
                rect.exactCenterX(),
                rect.exactCenterY(),
                session.rotation,
                decor.getWidth(),
                decor.getHeight(),
                session.width(),
                session.height());
    }

    private void controllerMenuTap(String tag, String page) throws Exception {
        float[] point = menuPoint(tag);
        runner.runOnMainSync(
                () -> {
                    controllerMouse(MotionEvent.ACTION_DOWN, point, 0);
                    controllerMouse(MotionEvent.ACTION_UP, point, 0);
                });
        await(
                () -> session.monitorShellPage().equals(page) && !session.monitorShellAnimating(),
                "phone mouse operates visible monitor menu " + tag);
    }

    private void openControls() throws Exception {
        pointer(0, physical(session.width() * .5f, 4), InputDevice.SOURCE_TOUCHSCREEN, 0);
        pointer(
                2,
                physical(session.width() * .5f, session.height() * .3f),
                InputDevice.SOURCE_TOUCHSCREEN,
                0);
        pointer(
                1,
                physical(session.width() * .5f, session.height() * .3f),
                InputDevice.SOURCE_TOUCHSCREEN,
                0);
        await(
                () ->
                        session.monitorShellPage().equals("controls")
                                && !session.monitorShellAnimating(),
                "controls ready for phone mouse routing");
    }

    private void controllerPointerOwnership() throws Exception {
        nativeReady();
        Presentation host = monitorHost();
        SplitLayout.Box first = box(0), second = box(1);
        float[]
                appButton =
                        {first.x + state.getFloat("buttonX"), first.y + state.getFloat("buttonY")},
                phoneTarget = {second.x + second.width * .5f, second.y + second.height * .65f};
        if (Build.VERSION.SDK_INT == 30) {
            int clicks = state.getInt("clicks"),
                    touches = InputTestActivity.touchEvents,
                    wheels = InputTestActivity.wheelEvents;
            float[] physicalButton = physical(appButton[0], appButton[1]);
            runner.runOnMainSync(
                    () -> {
                        session.cancelInput();
                        session.hideMonitorPointer();
                        float x = session.cx, y = session.cy;
                        long now = SystemClock.uptimeMillis();
                        MotionEvent event =
                                MotionEvent.obtain(
                                        now, now, 0, physicalButton[0], physicalButton[1], 0);
                        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                        try {
                            check(
                                    host.dispatchTouchEvent(event),
                                    "native focus wait accepts physical DOWN");
                            controllerMouse(0, phoneTarget, 0);
                            controllerMouse(1, phoneTarget, 0);
                            controllerMouse(MotionEvent.ACTION_SCROLL, phoneTarget, -3);
                            check(
                                    session.cx == x && session.cy == y && !session.pointerVisible,
                                    "pending physical focus blocks controller mouse and wheel");
                            event.setAction(MotionEvent.ACTION_UP);
                            check(
                                    host.dispatchTouchEvent(event),
                                    "native focus wait accepts physical UP");
                        } finally {
                            event.recycle();
                        }
                    });
            await(
                    () -> state.getInt("clicks") == clicks + 1,
                    "buffered physical click reaches native app");
            check(
                    InputTestActivity.touchEvents == touches
                            && InputTestActivity.wheelEvents == wheels,
                    "buffered physical sequence excludes controller pointer from other app");
        }
        openControls();
        float[] title = menuPoint("monitor-title"), physicalTitle = physical(title[0], title[1]);
        int ups = state.getInt("ups"), wheels = state.getInt("wheels");
        runner.runOnMainSync(
                () -> {
                    long now = SystemClock.uptimeMillis();
                    MotionEvent event =
                            MotionEvent.obtain(now, now, 0, physicalTitle[0], physicalTitle[1], 0);
                    event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                    try {
                        check(host.dispatchTouchEvent(event), "physical menu owns contact");
                        float x = session.cx, y = session.cy;
                        controllerMouse(0, appButton, 0);
                        controllerMouse(1, appButton, 0);
                        controllerMouse(MotionEvent.ACTION_SCROLL, appButton, -3);
                        check(
                                session.cx == x && session.cy == y && !session.pointerVisible,
                                "physical menu contact blocks controller pointer and wheel");
                        event.setAction(MotionEvent.ACTION_UP);
                        host.dispatchTouchEvent(event);
                    } finally {
                        event.recycle();
                    }
                });
        controllerMenuTap("monitor-layout", "layout");
        android.widget.ScrollView scroll =
                session.monitorShellView().findViewWithTag("monitor-menu-scroll");
        check(scroll != null, "monitor Layout exposes scrollable content");
        int before = scroll.getScrollY();
        runner.runOnMainSync(
                () ->
                        controllerMouse(
                                MotionEvent.ACTION_SCROLL,
                                new float[] {session.width() / 2f, session.height() / 2f},
                                -6));
        await(() -> scroll.getScrollY() > before, "controller wheel scrolls monitor modal");
        float[] outside = {40, session.height() / 2f};
        runner.runOnMainSync(
                () -> {
                    controllerMouse(0, outside, 0);
                    controllerMouse(1, outside, 0);
                });
        await(
                () ->
                        session.monitorShellPage().equals("closed")
                                && !session.monitorShellAnimating(),
                "controller outside click dismisses monitor modal");
        Thread.sleep(300);
        check(
                state.getInt("ups") == ups && state.getInt("wheels") == wheels,
                "controller modal clicks and wheels never reach underlying app");
        phoneAppTap();
    }

    private void performance() throws Exception {
        long[] samples = new long[100];
        for (int i = -10; i < samples.length; i++) {
            nativeClick(InputDevice.SOURCE_TOUCHSCREEN);
            check(
                    state.getLong("downSampleTime") == down,
                    "latency sample belongs to current native contact");
            if (i >= 0) samples[i] = state.getLong("downLatencyMs");
        }
        long[] sorted = samples.clone();
        java.util.Arrays.sort(sorted);
        org.json.JSONObject data = new org.json.JSONObject();
        data.put(
                "boundary",
                "injected physical-host DOWN eventTime to different-UID Activity dispatchTouchEvent; excludes rendering and broadcast/await time");
        data.put("input", "framework-injected touchscreen, not physical finger");
        data.put("warmup", 10);
        data.put("count", samples.length);
        data.put("p50_ms", sorted[49]);
        data.put("p95_ms", sorted[94]);
        data.put("rotation", session.rotation);
        data.put("width", session.width());
        data.put("height", session.height());
        data.put("dpi", session.dpi);
        org.json.JSONArray raw = new org.json.JSONArray();
        for (long value : samples) raw.put(value);
        data.put("samples_ms", raw);
        File folder = new File(runner.getTargetContext().getExternalCacheDir(), "split-acceptance");
        if (!folder.isDirectory() && !folder.mkdirs())
            throw new IOException("Performance directory");
        try (java.io.Writer writer =
                new java.io.OutputStreamWriter(
                        new FileOutputStream(new File(folder, "performance.json")),
                        java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write(data.toString(2));
        }
        results.append("MEASURE native DOWN dispatch latency, n=100 warmup=10 p50=")
                .append(sorted[49])
                .append(" ms p95=")
                .append(sorted[94])
                .append(" ms; excludes rendering.\n");
    }

    private void capture(String name, boolean monitor) throws Exception {
        Thread.sleep(200);
        Bitmap bitmap;
        if (monitor) {
            Window window = session.monitorWindow();
            View view = window.getDecorView();
            bitmap =
                    Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            CountDownLatch latch = new CountDownLatch(1);
            int[] code = {-1};
            runner.runOnMainSync(
                    () ->
                            PixelCopy.request(
                                    window,
                                    bitmap,
                                    value -> {
                                        code[0] = value;
                                        latch.countDown();
                                    },
                                    new Handler(Looper.getMainLooper())));
            check(
                    latch.await(5, TimeUnit.SECONDS) && code[0] == PixelCopy.SUCCESS,
                    "monitor capture " + name);
        } else bitmap = runner.getUiAutomation().takeScreenshot();
        check(bitmap != null, "screenshot available " + name);
        File folder = new File(runner.getTargetContext().getExternalCacheDir(), "split-acceptance");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Evidence directory");
        try (FileOutputStream stream = new FileOutputStream(new File(folder, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } finally {
            bitmap.recycle();
        }
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void legacyReceiver(Context context, BroadcastReceiver receiver) {
        if (Build.VERSION.SDK_INT >= 33) throw new IllegalStateException("Modern receiver");
        context.registerReceiver(receiver, fixtureFilter());
    }

    private IntentFilter fixtureFilter() {
        IntentFilter filter = new IntentFilter(SplitProbeFixture.ACTION);
        filter.addAction(LaunchParentActivity.ACTION);
        filter.addAction("dev.dexdroid.test.PROTECTION_STATE");
        return filter;
    }

    private void navigationClick(int pane) throws Exception {
        await(
                () ->
                        child != null
                                && child.getInt("display") == session.split.displayId(pane)
                                && child.getInt("targetWidth") > 0
                                && child.getInt("targetHeight") > 0,
                "navigation target laid out in pane " + pane);
        runner.getUiAutomation().waitForIdle(250, 5000);
        SplitLayout.Box bounds = box(pane);
        tap(
                physical(
                        bounds.x + child.getFloat("targetX"), bounds.y + child.getFloat("targetY")),
                InputDevice.SOURCE_TOUCHSCREEN);
        runner.getUiAutomation().waitForIdle(250, 5000);
    }

    private void showController() throws Exception {
        runner.runOnMainSync(
                () ->
                        runner.getTargetContext()
                                .startActivity(
                                        new Intent(runner.getTargetContext(), MainActivity.class)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        ActivityOptions.makeBasic()
                                                .setLaunchDisplayId(0)
                                                .toBundle()));
        await(this::phoneVisible, "controller is visible before native navigation");
    }

    private void closePane(int pane) throws Exception {
        runner.runOnMainSync(
                () -> {
                    session.split.select(pane);
                    session.split.closeApp();
                });
        ready("pane close for isolated navigation fixture");
        await(
                () ->
                        session.monitorInputView()
                                .findViewWithTag("split-idle-apps-" + pane)
                                .isShown(),
                "isolated pane is empty");
    }

    private void navigationKeepsPhoneForeground(ComponentName original) throws Exception {
        ComponentName navigation =
                new ComponentName(
                        "dev.dexdroid.test", "dev.dexdroid.fixture.NavigationFixtureActivity");
        ComponentName input = new ComponentName(runner.getTargetContext(), InputTestActivity.class);
        int first = session.id, second = session.split.displayId(1);
        closePane(0);
        for (int pane = 0; pane < 2; pane++) {
            final int selected = pane;
            if (pane == 1) {
                closePane(1);
                runner.runOnMainSync(() -> session.split.select(0));
                launch(input);
            }
            runner.runOnMainSync(() -> session.split.select(selected));
            launch(navigation);
            await(
                    () ->
                            child != null
                                    && "navigation-parent".equals(child.getString("page"))
                                    && child.getInt("display") == session.split.displayId(selected),
                    "native navigation root ready");
            showController();
            int task = child.getInt("task");
            for (int cycle = 0; cycle < 2; cycle++) {
                navigationClick(pane);
                await(
                        () -> "navigation-child".equals(child.getString("page")),
                        "same-task child entered");
                check(child.getInt("task") == task, "native child stays in parent task");
                check(phoneVisible(), "child entry preserves phone foreground");
                navigationClick(pane);
                await(
                        () -> "navigation-parent".equals(child.getString("page")),
                        "native Up returns to parent");
                check(phoneVisible(), "child Up preserves phone controller");
                navigationClick(pane);
                await(
                        () -> "navigation-child".equals(child.getString("page")),
                        "child reopened before Back");
                runner.runOnMainSync(
                        () -> {
                            session.split.select(selected);
                            session.key(KeyEvent.KEYCODE_BACK);
                        });
                await(
                        () -> "navigation-parent".equals(child.getString("page")),
                        "remote Back returns to parent");
                check(phoneVisible(), "child Back preserves phone controller");
                inputTap(1 - pane);
                check(phoneVisible(), "surviving other pane input preserves phone foreground");
            }
            runner.runOnMainSync(() -> phone.moveTaskToBack(true));
            await(() -> !phoneVisible(), "phone deliberately leaves controller");
            navigationClick(pane);
            await(
                    () -> "navigation-child".equals(child.getString("page")),
                    "new component accepts monitor input with phone backgrounded");
            check(!phoneVisible(), "monitor input respects intentional phone backgrounding");
            showController();
            navigationClick(pane);
            await(
                    () -> "navigation-parent".equals(child.getString("page")),
                    "Up works after phone controller returns");
            runner.runOnMainSync(
                    () -> {
                        session.split.select(selected);
                        session.key(KeyEvent.KEYCODE_A);
                    });
            await(
                    () -> "a".equals(child.getString("payload")),
                    "ordinary key reaches native parent after controller return and Up");
            check(phoneVisible(), "ordinary monitor key preserves phone controller");
            runner.runOnMainSync(
                    () -> {
                        session.split.select(selected);
                        session.key(KeyEvent.KEYCODE_BACK);
                    });
            await(
                    () ->
                            session.monitorInputView()
                                    .findViewWithTag("split-idle-apps-" + selected)
                                    .isShown(),
                    "native root Back empties only its pane");
            check(phoneVisible(), "root Back preserves controller");
            check(
                    session.id == first && session.split.displayId(1) == second,
                    "navigation preserves both owned display identities");
            inputTap(1 - pane);
            SplitLayout.Box empty = box(pane);
            tap(
                    physical(empty.x + empty.width / 2f, empty.y + empty.height / 2f),
                    InputDevice.SOURCE_TOUCHSCREEN);
            await(
                    () ->
                            session.monitorShellPage().equals("home")
                                    && !session.monitorShellAnimating(),
                    "root-exited pane Apps stays usable");
            runner.runOnMainSync(
                    () ->
                            session.monitorShellView()
                                    .findViewWithTag("monitor-close")
                                    .performClick());
            runner.runOnMainSync(() -> session.split.select(selected));
            launch(navigation);
            await(
                    () ->
                            "navigation-parent".equals(child.getString("page"))
                                    && child.getInt("display") == session.split.displayId(selected),
                    "root-exited pane relaunches");
            closePane(pane);
        }
        closePane(0);
        runner.runOnMainSync(() -> session.split.select(1));
        launch(input);
        runner.runOnMainSync(() -> session.split.select(0));
        launch(original);
        showController();
    }

    private void readerAndHandoff() throws Exception {
        ComponentName parent =
                new ComponentName("dev.dexdroid.test", "dev.dexdroid.fixture.LaunchParentActivity");
        runner.runOnMainSync(() -> session.split.select(0));
        launch(parent);
        await(
                () ->
                        child != null
                                && "parent".equals(child.getString("page"))
                                && child.getInt("display") == session.id,
                "parent owns first pane");
        SplitLayout.Box pane = box(0);
        await(
                () -> child.getInt("targetWidth") > 0 && child.getInt("targetHeight") > 0,
                "parent content laid out before native touch");
        capture("monitor-parent", true);
        float[] center =
                physical(pane.x + child.getFloat("targetX"), pane.y + child.getFloat("targetY"));
        tap(center, InputDevice.SOURCE_TOUCHSCREEN);
        await(
                () ->
                        "reader".equals(child.getString("page"))
                                && child.getInt("display") == session.id,
                "private reader child stays in first pane");
        runner.getUiAutomation().waitForIdle(250, 5000);
        await(
                () ->
                        child.getInt("windowWidth") == pane.width
                                && child.getInt("windowHeight") == pane.height,
                "private reader window laid out");
        capture("monitor-private-reader", true);
        center = physical(pane.x + child.getFloat("targetX"), pane.y + child.getFloat("targetY"));
        tap(center, InputDevice.SOURCE_TOUCHSCREEN);
        await(
                () -> "reader".equals(child.getString("page")) && child.getInt("count") == 1,
                "private reader receives native tap in split");
        inputTap();
        DisplayTasks.Root moved = null;
        for (DisplayTasks.Root root :
                DisplayTasks.parse(
                        DeviceTestSupport.shell(runner, "am stack list"), parent.flattenToString()))
            if (root.display == session.id && root.taskId == child.getInt("task")) moved = root;
        check(moved != null && moved.tasks == 1, "handoff owns an isolated app root");
        int handoffTask = child.getInt("task"), handoffOpens = child.getInt("count");
        String output =
                DeviceTestSupport.shell(
                        runner, "am start -W --display 0 -n " + parent.flattenToString());
        check(!output.contains("Error"), "Android accepts ordinary phone app launch");
        await(
                () -> child.getInt("display") == 0 && child.getInt("task") == handoffTask,
                "phone launch reuses reader application task");
        check(child.getInt("count") >= handoffOpens, "phone handoff preserves application state");
        await(
                () -> session.monitorInputView().findViewWithTag("split-idle-apps-0").isShown(),
                "departed app pixels clear only its own pane");
        inputTap();
        capture("monitor-handoff", true);
        runner.runOnMainSync(
                () ->
                        runner.getTargetContext()
                                .startActivity(
                                        new Intent(runner.getTargetContext(), MainActivity.class)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        ActivityOptions.makeBasic()
                                                .setLaunchDisplayId(0)
                                                .toBundle()));
        await(this::phoneVisible, "phone controller restored after handoff");
    }

    private void protectedPane() throws Exception {
        runner.runOnMainSync(() -> session.split.select(0));
        launch(
                new ComponentName(
                        "dev.dexdroid.test", "dev.dexdroid.fixture.ProtectedFixtureActivity"));
        SplitLayout.Box pane = box(0);
        await(
                () ->
                        protection != null
                                && protection.getInt("display") == session.id
                                && protection.getInt("windowWidth") == pane.width
                                && protection.getInt("windowHeight") == pane.height,
                "normal protection fixture laid out before native touch");
        capture("monitor-before-protect", true);
        tap(
                physical(
                        pane.x + protection.getFloat("targetX"),
                        pane.y + protection.getFloat("targetY")),
                InputDevice.SOURCE_TOUCHSCREEN);
        await(() -> session.protectedContent, "protected pane detected");
        await(
                () ->
                        session.monitorShellView()
                                .findViewWithTag("monitor-protected-notice")
                                .isShown(),
                "protected pane exposes compact Help");
        runner.runOnMainSync(
                () -> {
                    View notice =
                            session.monitorShellView().findViewWithTag("monitor-protected-notice");
                    check(
                            notice.getLeft() >= pane.x
                                    && notice.getRight() <= pane.x + pane.width
                                    && notice.getTop() >= pane.y
                                    && notice.getBottom() <= pane.y + pane.height,
                            "protection notice stays within its own pane");
                });
        inputTap();
        capture("monitor-protected-pane", true);
        runner.runOnMainSync(
                () -> {
                    session.split.select(0);
                    session.split.closeApp();
                });
        ready("protected app closed independently");
        await(() -> !session.protectedContent, "closing protected pane clears its notice");
        inputTap();
    }

    private void historyHandoff(ComponentName fixture) throws Exception {
        DisplayTasks.Root moved = null;
        for (DisplayTasks.Root root :
                DisplayTasks.parse(
                        DeviceTestSupport.shell(runner, "am stack list"),
                        fixture.flattenToString()))
            if (root.display == session.id && root.matches) moved = root;
        check(moved != null && moved.tasks == 1, "history fixture owns isolated foreground task");
        int handoffTask = moved.taskId;
        String launchOutput =
                DeviceTestSupport.shell(
                        runner, "am start -W --display 0 -n " + fixture.flattenToString());
        check(!launchOutput.contains("Error"), "Android accepts history app phone launch");
        await(
                () -> {
                    if (state.getInt("display") != 0) return false;
                    try {
                        for (DisplayTasks.Root root :
                                DisplayTasks.parse(
                                        DeviceTestSupport.shell(runner, "am stack list"),
                                        fixture.flattenToString()))
                            if (root.display == 0 && root.matches && root.taskId == handoffTask)
                                return true;
                    } catch (Exception error) {
                        throw new RuntimeException(error);
                    }
                    return false;
                },
                "ordinary phone launch preserves history task");
        await(
                () -> session.split.selectedPackage().equals("dev.dexdroid"),
                "underlying app becomes selected after foreground task leaves");
        runner.getUiAutomation().waitForIdle(250, 5000);
        check(session.hasDesktopApp(), "existing task remains live instead of empty Apps overlay");
        File folder = new File(runner.getTargetContext().getExternalCacheDir(), "split-acceptance");
        if (!folder.isDirectory()) folder.mkdirs();
        try (FileOutputStream output =
                new FileOutputStream(new File(folder, "history-before-input.txt"))) {
            output.write(
                    DeviceTestSupport.shell(runner, "dumpsys input")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        SplitLayout.Box pane = box(0);
        tap(
                physical(pane.x + pane.width * .5f, pane.y + pane.height * .6f),
                InputDevice.SOURCE_TOUCHSCREEN);
        long expected = down;
        await(
                () -> InputTestActivity.lastUpDownTime == expected,
                "input reaches underlying app after handoff");
        runner.runOnMainSync(() -> session.split.closeApp());
        ready("history pane closed");
        launch(fixture);
        await(
                () -> state.getInt("display") == session.id,
                "native fixture restored after history scenario");
    }

    private boolean secondaryHelperGone() {
        try {
            for (String line : DeviceTestSupport.shell(runner, "ps -A -o NAME").split("\n"))
                if (line.trim().equals("dev.dexdroid:desktop-secondary")) return false;
            return true;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private boolean phoneVisible() {
        boolean[] visible = {false};
        runner.runOnMainSync(
                () -> {
                    View decor = phone.getWindow().getDecorView();
                    Rect bounds = new Rect();
                    // A managed app can own global key focus while this phone window is visible.
                    visible[0] =
                            !phone.isFinishing()
                                    && !phone.isDestroyed()
                                    && decor.getDisplay() != null
                                    && decor.getDisplay().getDisplayId() == 0
                                    && decor.getWindowVisibility() == View.VISIBLE
                                    && decor.isShown()
                                    && decor.getGlobalVisibleRect(bounds)
                                    && !bounds.isEmpty();
                });
        return visible[0];
    }

    private void menu(String method) {
        try {
            runner.runOnMainSync(
                    () ->
                            runner.getTargetContext()
                                    .startActivity(
                                            new Intent(
                                                            runner.getTargetContext(),
                                                            MainActivity.class)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            ActivityOptions.makeBasic()
                                                    .setLaunchDisplayId(0)
                                                    .toBundle()));
            await(this::phoneVisible, "phone visible before opening " + method);
            java.lang.reflect.Method call = MainActivity.class.getDeclaredMethod(method);
            call.setAccessible(true);
            runner.runOnMainSync(
                    () -> {
                        try {
                            call.invoke(phone);
                        } catch (Exception error) {
                            throw new IllegalStateException(error);
                        }
                    });
            await(
                    () ->
                            phoneDialog() != null
                                    && phoneDialog().getWindow().getDecorView().isShown(),
                    "phone menu visible " + method);
            runner.getUiAutomation().waitForIdle(250, 5000);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private void closePhoneMenu() {
        try {
            java.lang.reflect.Field field = MainActivity.class.getDeclaredField("settingsDialog");
            field.setAccessible(true);
            runner.runOnMainSync(
                    () -> {
                        try {
                            Dialog dialog = (Dialog) field.get(phone);
                            if (dialog != null) dialog.dismiss();
                        } catch (Exception error) {
                            throw new IllegalStateException(error);
                        }
                    });
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    String run() {
        Context context = runner.getTargetContext();
        CountDownLatch connected = new CountDownLatch(1);
        boolean bound = false, registered = false;
        android.content.SharedPreferences saved = null;
        java.util.Map<String, ?> preferences = null;
        ServiceConnection binding =
                new ServiceConnection() {
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        session = ((SessionService.LocalBinder) binder).get();
                        connected.countDown();
                    }

                    public void onServiceDisconnected(ComponentName name) {}
                };
        BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    public void onReceive(Context ignored, Intent intent) {
                        if ("dev.dexdroid.test.PROTECTION_STATE".equals(intent.getAction()))
                            protection = new Bundle(intent.getExtras());
                        if (LaunchParentActivity.ACTION.equals(intent.getAction()))
                            child = new Bundle(intent.getExtras());
                        if ("Left".equals(intent.getStringExtra("pane")))
                            state = new Bundle(intent.getExtras());
                    }
                };
        try {
            File evidence = new File(context.getExternalCacheDir(), "split-acceptance");
            for (String name :
                    new String[] {
                        "performance.json",
                        "history-before-input.txt",
                        "failure-input.txt",
                        "failure-window-windows.txt",
                        "failure-display.txt"
                    }) {
                File stale = new File(evidence, name);
                if (stale.exists() && !stale.delete())
                    throw new IOException("Cannot clear stale split evidence: " + name);
            }
            if (Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(receiver, fixtureFilter(), Context.RECEIVER_EXPORTED);
            else legacyReceiver(context, receiver);
            registered = true;
            phone = DeviceTestSupport.startPhone(runner);
            bound =
                    context.bindService(
                            new Intent(context, SessionService.class),
                            binding,
                            Context.BIND_AUTO_CREATE);
            check(connected.await(5, TimeUnit.SECONDS), "product controller bound");
            ready("single desktop ready");
            saved = session.prefs;
            preferences = new java.util.HashMap<>(saved.getAll());
            runner.runOnMainSync(() -> session.rotate(0, false));
            ready("initial landscape ready");
            ComponentName fixture =
                    new ComponentName(
                            "dev.dexdroid.test", "dev.dexdroid.fixture.SplitProbeFixture$Left");
            launch(new ComponentName(context, InputTestActivity.class));
            launch(fixture);
            await(() -> state != null, "cross-UID native fixture reports");
            check(
                    state.getInt("uid") != android.os.Process.myUid(),
                    "native fixture has independent UID");
            if (navigationOnly) {
                runner.runOnMainSync(() -> session.enableSplit(false));
                await(
                        () ->
                                session.split != null
                                        && session.split.displayId(1) >= 0
                                        && !session.busy,
                        "navigation split display ready");
                runner.runOnMainSync(() -> session.split.select(1));
                launch(new ComponentName(context, InputTestActivity.class));
                for (int rotation = 0; rotation < 4; rotation++) {
                    final int r = rotation;
                    runner.runOnMainSync(() -> session.rotate(r, false));
                    ready("navigation rotation " + r);
                    navigationKeepsPhoneForeground(fixture);
                    await(this::firstFrameReady, "restored native fixture frame settled");
                    await(() -> inputFrameReady(1), "surviving input fixture frame settled");
                    capture("navigation-r" + r, true);
                }
                return results.toString();
            }
            menu("layoutSettings");
            runner.runOnMainSync(
                    () -> layoutTargets(phoneDialog().getWindow().getDecorView(), false));
            capture("phone-layout-off", false);
            phoneTarget(0);
            check(session.appSelectionBox() == null, "single picker has no persistent highlight");
            closePhoneMenu();
            await(() -> session.appSelectionBox() == null, "single picker cancel clears highlight");
            menu("layoutSettings");
            runner.runOnMainSync(
                    () ->
                            phoneDialog()
                                    .getWindow()
                                    .getDecorView()
                                    .findViewWithTag("layout-toggle")
                                    .performClick());
            closePhoneMenu();
            await(
                    () -> session.split != null && session.split.displayId(1) >= 0 && !session.busy,
                    "second managed display ready");
            int first = session.id, second = session.split.displayId(1);
            check(first != second, "two distinct live display IDs");
            runner.runOnMainSync(
                    () -> {
                        for (int pane = 0; pane < 2; pane++) {
                            android.widget.FrameLayout decoration =
                                    session.monitorInputView()
                                            .findViewWithTag("split-pane-" + pane);
                            check(
                                    decoration.getForeground() == null,
                                    "pane has no focus outline " + pane);
                        }
                    });
            historyHandoff(fixture);
            runner.runOnMainSync(() -> session.split.select(1));
            launchSecondFromLayout();
            navigationKeepsPhoneForeground(fixture);
            await(() -> state.getInt("width") == box(0).width, "first app resized into pane");
            capture("monitor-two-apps-r0", true);
            menu("layoutSettings");
            capture("phone-layout-on", false);
            closePhoneMenu();
            for (int rotation = 0; rotation < 4; rotation++) {
                final int r = rotation;
                runner.runOnMainSync(
                        () -> {
                            session.rotate(r, false);
                            // A task/status update can arrive before the remote resize completes.
                            session.split.layoutViews();
                            int w = r % 2 == 0 ? session.baseW : session.baseH;
                            int h = r % 2 == 0 ? session.baseH : session.baseW;
                            SplitLayout.Box expected = session.split.dimensions(1, w, h, false);
                            View texture =
                                    session.monitorInputView().findViewWithTag("split-texture-1");
                            check(
                                    texture.getLayoutParams().width == expected.width
                                            && texture.getLayoutParams().height == expected.height,
                                    "status relayout preserves staged surface geometry " + r);
                        });
                ready("split rotation " + r);
                await(
                        () ->
                                state.getInt("width") == box(0).width
                                        && state.getInt("height") == box(0).height,
                        "native app dimensions follow rotation " + r);
                nativeClick(InputDevice.SOURCE_TOUCHSCREEN);
                nativeClick(InputDevice.SOURCE_MOUSE);
                inputTap();
                await(
                        this::firstFrameReady,
                        "first app frame fills native pane after rotation " + r);
                capture("monitor-two-apps-r" + r, true);
                monitorLayout(r);
            }
            runner.runOnMainSync(() -> session.rotate(0, false));
            ready("landscape restored");
            keyRelay();
            performance();
            inputOwnership();
            dragDivider();
            phoneDivider();
            controllerPointerOwnership();
            runner.runOnMainSync(() -> session.split.ratio(1f / 3));
            ready("one-third resize committed");
            await(() -> state.getInt("width") == box(0).width, "native buffer reflects ratio");
            nativeClick(InputDevice.SOURCE_MOUSE);
            inputTap();
            runner.runOnMainSync(() -> session.split.swap());
            ready("swap committed");
            nativeClick(InputDevice.SOURCE_MOUSE);
            inputTap();
            check(
                    session.id == first && session.split.displayId(1) == second,
                    "swap preserves app display IDs");
            runner.runOnMainSync(
                    () -> {
                        session.split.select(0);
                        session.split.maximize();
                    });
            ready("selected pane fullscreen");
            await(
                    () -> state.getInt("width") == session.width(),
                    "fullscreen native display actually resized");
            nativeClick(InputDevice.SOURCE_MOUSE);
            runner.runOnMainSync(() -> session.split.maximize());
            ready("restore split");
            runner.runOnMainSync(() -> session.split.direction(true));
            ready("top-bottom committed");
            await(() -> state.getInt("height") == box(0).height, "top-bottom native height");
            inputTap();
            capture("monitor-top-bottom", true);
            int oldW = session.baseW, oldH = session.baseH, oldDpi = session.dpi;
            runner.runOnMainSync(() -> session.preview(1920, 1080, 160));
            ready("Fit DPI preview committed");
            check(session.awaitingConfirmation(), "split preview awaits confirmation");
            inputTap();
            runner.runOnMainSync(() -> session.confirm(false));
            ready("both pane preview reverted");
            check(
                    session.baseW == oldW && session.baseH == oldH && session.dpi == oldDpi,
                    "resolution and DPI restored");
            runner.runOnMainSync(() -> session.split.direction(false));
            ready("left-right restored");
            await(() -> state.getInt("height") == box(0).height, "fixture ready after preview");
            nativeReady();
            SplitLayout.Box left = box(0);
            int wheels = state.getInt("wheels");
            pointer(
                    MotionEvent.ACTION_SCROLL,
                    physical(left.x + state.getFloat("padX"), left.y + state.getFloat("padY")),
                    InputDevice.SOURCE_MOUSE,
                    -.5f);
            await(
                    () -> state.getInt("wheels") == wheels + 1,
                    "native mouse wheel targets pointed pane");
            check(state.getFloat("wheelV") == -.5f, "hardware wheel amount preserved");
            int moves = state.getInt("moves"), ups = state.getInt("ups");
            float[] start =
                    physical(left.x + state.getFloat("padX"), left.y + state.getFloat("padY"));
            SplitLayout.Box right = box(1);
            float[] end = physical(right.x + right.width * .5f, right.y + right.height * .7f);
            pointer(MotionEvent.ACTION_DOWN, start, InputDevice.SOURCE_MOUSE, 0);
            Thread.sleep(100);
            pointer(MotionEvent.ACTION_MOVE, end, InputDevice.SOURCE_MOUSE, 0);
            pointer(MotionEvent.ACTION_UP, end, InputDevice.SOURCE_MOUSE, 0);
            await(
                    () -> state.getInt("moves") > moves && state.getInt("ups") == ups + 1,
                    "drag crossing divider releases original app");
            runner.runOnMainSync(() -> session.split.select(1));
            SplitLayout.Box target = box(1);
            long phoneDown = SystemClock.uptimeMillis();
            runner.runOnMainSync(
                    () -> {
                        session.touch(
                                0,
                                new float[] {target.x + target.width / 2f},
                                new float[] {target.height * .6f});
                        session.touch(
                                1,
                                new float[] {target.x + target.width / 2f},
                                new float[] {target.height * .6f});
                    });
            await(
                    () -> InputTestActivity.lastUpDownTime >= phoneDown,
                    "phone touchpad pipeline targets selected pane");
            int backs = InputTestActivity.backEvents;
            runner.runOnMainSync(() -> session.key(KeyEvent.KEYCODE_BACK));
            await(() -> InputTestActivity.backEvents == backs + 1, "Back affects selected pane");
            runner.runOnMainSync(() -> session.launch(fixture, null));
            check(session.split.selected() == 0, "opening existing app selects its original pane");
            runner.runOnMainSync(() -> phone.moveTaskToBack(true));
            Thread.sleep(250);
            nativeClick(InputDevice.SOURCE_MOUSE);
            inputTap();
            runner.runOnMainSync(
                    () ->
                            context.startActivity(
                                    new Intent(context, MainActivity.class)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle()));
            await(() -> phoneVisible() && !session.busy, "controller reopened with both panes");
            runner.runOnMainSync(() -> session.split.select(0));
            runner.runOnMainSync(() -> session.split.closeApp());
            ready("close selected app completed");
            await(
                    () -> session.monitorInputView().findViewWithTag("split-idle-apps-0").isShown(),
                    "closed pane offers Apps");
            inputTap();
            capture("monitor-one-empty", true);
            paneApps();
            readerAndHandoff();
            protectedPane();
            java.lang.reflect.Field bindingArgs = SplitWorkspace.class.getDeclaredField("args");
            bindingArgs.setAccessible(true);
            rikka.shizuku.Shizuku.UserServiceArgs retiredBinding =
                    (rikka.shizuku.Shizuku.UserServiceArgs) bindingArgs.get(session.split);
            SplitLayout remembered = session.split.state();
            runner.runOnMainSync(() -> session.split.end());
            ready("single view restored");
            await(() -> !session.splitEnabled(), "split ended");
            check(!session.savedSplitLayout().enabled, "explicit End remembers single view");
            check(
                    ((DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE))
                                    .getDisplay(second)
                            == null,
                    "second display removed after End");
            await(this::secondaryHelperGone, "End terminates the unbound secondary shell process");
            runner.runOnMainSync(session::enableSplit);
            await(
                    () -> session.split != null && !session.busy && session.split.displayId(1) >= 0,
                    "fresh split session starts");
            int last = session.split.displayId(1);
            check(
                    session.split.state().vertical == remembered.vertical
                            && session.split.state().swapped == remembered.swapped
                            && Math.abs(session.split.state().ratio - remembered.ratio) < .001f,
                    "enabling split restores last confirmed geometry");
            SplitWorkspace fresh = session.split;
            runner.runOnMainSync(() -> fresh.select(1));
            launch(fixture);
            await(() -> state.getInt("display") == last, "fresh secondary owns native fixture");
            await(
                    () ->
                            state.getInt("layoutWidth") == box(1).width
                                    && state.getInt("layoutBottom") == box(1).height,
                    "fresh secondary native content laid out");
            // Reproduce an obsolete cleanup callback using the actual Shizuku removal API.
            // remove=true identifies the service by args, not by the ServiceConnection.
            rikka.shizuku.Shizuku.unbindUserService(retiredBinding, null, true);
            Thread.sleep(500);
            check(
                    session.split == fresh && fresh.displayId(1) == last && session.id == first,
                    "late retired binding removal preserves current split displays");
            int freshClicks = state.getInt("clicks");
            SplitLayout.Box freshPane = box(1);
            tap(
                    physical(
                            freshPane.x + state.getFloat("buttonX"),
                            freshPane.y + state.getFloat("buttonY")),
                    InputDevice.SOURCE_TOUCHSCREEN);
            await(
                    () -> state.getInt("clicks") == freshClicks + 1,
                    "current secondary receives native input after retired binding removal");
            capture("monitor-stale-helper-retained", true);
            SessionService.AppSelection[] retiredChoice = {null};
            runner.runOnMainSync(() -> retiredChoice[0] = session.beginAppSelection(1));
            float[] stopEdge = physical(session.width() * .5f, 4);
            float[] stopInside = physical(session.width() * .5f, session.height() * .3f);
            pointer(0, stopEdge, InputDevice.SOURCE_TOUCHSCREEN, 0);
            Thread.sleep(35);
            pointer(2, stopInside, InputDevice.SOURCE_TOUCHSCREEN, 0);
            pointer(1, stopInside, InputDevice.SOURCE_TOUCHSCREEN, 0);
            await(
                    () ->
                            session.monitorShellPage().equals("controls")
                                    && !session.monitorShellAnimating(),
                    "monitor Controls ready before explicit Stop");
            results.append(
                    StopReturnAcceptance.run(
                            runner,
                            phone,
                            session,
                            () ->
                                    session.monitorShellView()
                                            .findViewWithTag("monitor-stop")
                                            .performClick(),
                            "split"));
            await(
                    () ->
                            ((DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE))
                                                    .getDisplay(first)
                                            == null
                                    && ((DisplayManager)
                                                            context.getSystemService(
                                                                    Context.DISPLAY_SERVICE))
                                                    .getDisplay(last)
                                            == null,
                    "Stop removes both displays");
            await(this::secondaryHelperGone, "Stop leaves no secondary shell process");
            check(session.savedSplitLayout().enabled, "Stop retains split enabled preference");
            runner.runOnMainSync(session::ensureDesktop);
            await(
                    () ->
                            session.split != null
                                    && session.id != first
                                    && !session.busy
                                    && session.split.displayId(1) >= 0,
                    "restart restores split with fresh displays");
            check(!session.split.anyApp(), "restart does not relaunch previous apps");
            check(
                    session.split.state().vertical == remembered.vertical
                            && session.split.state().swapped == remembered.swapped
                            && Math.abs(session.split.state().ratio - remembered.ratio) < .001f,
                    "restart retains confirmed direction ratio and order");
            check(session.appSelectionBox() == null, "restart retains no picker highlight");
            runner.runOnMainSync(
                    () ->
                            check(
                                    !session.launchSelection(retiredChoice[0], fixture, null),
                                    "retired picker cannot launch into restored session"));
            runner.runOnMainSync(
                    () -> {
                        for (int pane = 0; pane < 2; pane++) {
                            check(
                                    session.monitorInputView().findViewWithTag("split-idle-" + pane)
                                            instanceof IdleDesktop,
                                    "empty pane reuses single-screen background " + pane);
                            check(
                                    session.monitorInputView()
                                            .findViewWithTag("split-idle-apps-" + pane)
                                            .isShown(),
                                    "empty pane centered Apps visible " + pane);
                        }
                    });
            capture("monitor-restored-empty-panes", true);
            emptyBackgroundTransitions();
            monitorLauncherModes();
            pendingModeOwnership();
            fullscreenLauncher();
            results.append(StopReturnAcceptance.failures(runner, phone, session, true));
            int restoredFirst = session.id, restoredSecond = session.split.displayId(1);
            runner.runOnMainSync(session::pauseDesktop);
            await(
                    () ->
                            ((DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE))
                                                    .getDisplay(restoredFirst)
                                            == null
                                    && ((DisplayManager)
                                                            context.getSystemService(
                                                                    Context.DISPLAY_SERVICE))
                                                    .getDisplay(restoredSecond)
                                            == null,
                    "restored split stops both fresh displays");
            await(this::secondaryHelperGone, "restored split cleanup terminates secondary");
            results.append(
                    "GAP physical Bluetooth typing/shortcuts, Android 16/17, fault injection, frame pacing and touch-to-photon timing require separate review.\n");
        } catch (Throwable error) {
            results.append("INPUT lastDown=")
                    .append(InputTestActivity.lastDownTime)
                    .append(" lastUpDown=")
                    .append(InputTestActivity.lastUpDownTime)
                    .append(" expected=")
                    .append(down)
                    .append('\n');
            results.append("STATE ").append(state).append('\n');
            results.append("CHILD ").append(child).append('\n');
            results.append("FRAME ").append(frameEvidence).append('\n');
            try {
                try {
                    capture("failure-monitor", true);
                } catch (Exception unavailable) {
                    results.append("MONITOR CAPTURE ").append(unavailable).append('\n');
                }
                try {
                    capture("failure-phone", false);
                } catch (Exception unavailable) {
                    results.append("PHONE CAPTURE ").append(unavailable).append('\n');
                }
                File folder = new File(context.getExternalCacheDir(), "split-acceptance");
                for (String name : new String[] {"input", "window windows", "display"}) {
                    try (FileOutputStream output =
                            new FileOutputStream(
                                    new File(
                                            folder,
                                            "failure-" + name.replace(' ', '-') + ".txt"))) {
                        output.write(
                                DeviceTestSupport.shell(runner, "dumpsys " + name)
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
                results.append(DeviceTestSupport.shell(runner, "am stack list"));
            } catch (Exception diagnostic) {
                results.append("DIAGNOSTIC ").append(diagnostic);
            }
            StringWriter stack = new StringWriter();
            error.printStackTrace(new PrintWriter(stack));
            results.append("FAIL ").append(stack);
        } finally {
            if (session != null) runner.runOnMainSync(session::pauseDesktop);
            if (saved != null && preferences != null) {
                android.content.SharedPreferences.Editor edit = saved.edit().clear();
                for (java.util.Map.Entry<String, ?> entry : preferences.entrySet()) {
                    Object value = entry.getValue();
                    String key = entry.getKey();
                    if (value instanceof Integer) edit.putInt(key, (Integer) value);
                    else if (value instanceof Float) edit.putFloat(key, (Float) value);
                    else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
                    else if (value instanceof Long) edit.putLong(key, (Long) value);
                    else if (value instanceof String) edit.putString(key, (String) value);
                }
                edit.commit();
            }
            if (registered) context.unregisterReceiver(receiver);
            if (bound) context.unbindService(binding);
        }
        return results.toString();
    }
}
