package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real external-display and phone-pointer interactions with the monitor search UI. */
final class MonitorSearchAcceptance {
    private final Instrumentation runner;
    private final StringBuilder results = new StringBuilder();
    private SessionService session;
    private Activity phone;
    private ServiceConnection binding;
    private boolean bound;
    private Map<String, ?> preferences;
    private long down;

    MonitorSearchAcceptance(Instrumentation runner) {
        this.runner = runner;
    }

    private void main(Runnable action) {
        runner.runOnMainSync(action);
    }

    private void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        results.append("PASS ").append(label).append('\n');
    }

    private int[] shellMargins() {
        int[] pixels = new int[4];
        main(
                () -> {
                    View root = session.monitorShellView();
                    Bitmap bitmap =
                            Bitmap.createBitmap(
                                    root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
                    root.draw(new Canvas(bitmap));
                    int x = Math.max(1, root.getWidth() / 100);
                    int y = Math.max(1, root.getHeight() / 100);
                    pixels[0] = bitmap.getPixel(x, y);
                    pixels[1] = bitmap.getPixel(root.getWidth() - x - 1, y);
                    pixels[2] = bitmap.getPixel(x, root.getHeight() - y - 1);
                    pixels[3] = bitmap.getPixel(root.getWidth() - x - 1, root.getHeight() - y - 1);
                    bitmap.recycle();
                });
        return pixels;
    }

    private void await(BooleanSupplier predicate, String label) throws Exception {
        long end = SystemClock.uptimeMillis() + 10000;
        while (!predicate.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(50);
        check(predicate.getAsBoolean(), label);
    }

    private void restore() {
        if (preferences == null || session == null) return;
        android.content.SharedPreferences.Editor edit = session.prefs.edit().clear();
        for (Map.Entry<String, ?> entry : preferences.entrySet()) {
            Object value = entry.getValue();
            String key = entry.getKey();
            if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) edit.putInt(key, (Integer) value);
            else if (value instanceof Float) edit.putFloat(key, (Float) value);
            else if (value instanceof String) edit.putString(key, (String) value);
            else if (value instanceof Long) edit.putLong(key, (Long) value);
        }
        edit.commit();
    }

    private View view(String tag) {
        View root = session.monitorShellView();
        return root == null ? null : root.findViewWithTag(tag);
    }

    private EditText editor() {
        return (EditText) view("monitor-app-search");
    }

    private boolean keyboardVisible() {
        View keyboard = view("monitor-search-keyboard");
        return keyboard != null && keyboard.isShown();
    }

    private void page(String page) throws Exception {
        await(
                () -> page.equals(session.monitorShellPage()) && !session.monitorShellAnimating(),
                "search UI page settled: " + page);
    }

    private void event(int action, float x, float y) throws Exception {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) down = now;
        MotionEvent event = MotionEvent.obtain(down, now, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            // Display discovery clears/replaces external on the main thread. Read
            // its completed state there rather than racing that transient update.
            int[] display = {-1};
            main(
                    () -> {
                        check(session.external != null, "physical monitor remains connected");
                        display[0] = session.external.getDisplayId();
                        if (action == MotionEvent.ACTION_DOWN) {
                            View host = session.monitorInputView();
                            results.append("STATE native DOWN ")
                                    .append(x)
                                    .append(',')
                                    .append(y)
                                    .append(" host=")
                                    .append(host.getWidth())
                                    .append('x')
                                    .append(host.getHeight())
                                    .append(" logical=")
                                    .append(session.width())
                                    .append('x')
                                    .append(session.height())
                                    .append(" display=")
                                    .append(display[0])
                                    .append('\n');
                        }
                    });
            check(
                    DeviceTestSupport.injectOnDisplay(event, display[0]),
                    "search physical monitor event " + action);
        } finally {
            event.recycle();
        }
    }

    private float[] physical(float x, float y) {
        Display.Mode mode = session.external.getMode();
        return Geometry.map(
                x / session.width(),
                y / session.height(),
                session.rotation,
                mode.getPhysicalWidth(),
                mode.getPhysicalHeight(),
                session.width(),
                session.height());
    }

    private void tap(float x, float y) throws Exception {
        event(MotionEvent.ACTION_DOWN, x, y);
        Thread.sleep(50); // Model a finite physical contact, not a zero-duration injected tap.
        event(MotionEvent.ACTION_UP, x, y);
        Thread.sleep(35);
    }

    private void swipe(float x, float y, float endX, float endY) throws Exception {
        float[] start = physical(x, y), end = physical(endX, endY);
        DeviceTestSupport.usableMonitorSwipe(session, start, end);
        event(0, start[0], start[1]);
        Thread.sleep(30);
        event(2, end[0], end[1]);
        event(1, end[0], end[1]);
        Thread.sleep(100);
    }

    private Rect bounds(String tag) {
        Rect rect = new Rect();
        main(
                () -> {
                    View target = view(tag);
                    check(
                            target != null
                                    && target.isShown()
                                    && target.getGlobalVisibleRect(rect)
                                    && !rect.isEmpty(),
                            "search control visible: " + tag);
                });
        return rect;
    }

    private Rect fullBounds(String tag) {
        Rect result = new Rect();
        main(
                () -> {
                    View target = view(tag);
                    check(
                            target != null
                                    && target.isShown()
                                    && target.getWidth() > 0
                                    && target.getHeight() > 0,
                            "search layout control present: " + tag);
                    Matrix matrix = new Matrix();
                    target.transformMatrixToGlobal(matrix);
                    RectF full = new RectF(0, 0, target.getWidth(), target.getHeight());
                    matrix.mapRect(full);
                    full.roundOut(result);
                });
        return result;
    }

    // Rotation/scaling and roundOut can share a one-pixel boundary without a real
    // overlap. Larger intersections reveal obscured layout rather than rounding.
    private boolean overlaps(Rect a, Rect b) {
        Rect intersection = new Rect(a);
        return intersection.intersect(b) && intersection.width() > 1 && intersection.height() > 1;
    }

    private void click(String tag) throws Exception {
        await(
                () ->
                        !session.monitorShellAnimating()
                                && !session.monitorShellView().isLayoutRequested()
                                && view(tag) != null
                                && !view(tag).isLayoutRequested(),
                "search layout settled before measuring " + tag);
        Rect rect = bounds(tag);
        tap(rect.exactCenterX(), rect.exactCenterY());
    }

    private void pointerClick(String tag) throws Exception {
        main(
                () -> {
                    View target = view(tag);
                    check(
                            target != null && target.isShown(),
                            "phone pointer control visible: " + tag);
                    float x = 0, y = 0;
                    View current = target;
                    while (current != session.monitorShellView()) {
                        x += current.getLeft();
                        y += current.getTop();
                        View parent = (View) current.getParent();
                        x -= parent.getScrollX();
                        y -= parent.getScrollY();
                        current = parent;
                    }
                    session.cx = x + target.getWidth() / 2f;
                    session.cy = y + target.getHeight() / 2f;
                    session.click();
                });
        Thread.sleep(50);
    }

    private String keyTag(char value) {
        return "monitor-key-"
                + (value == ' '
                        ? "space"
                        : value == '.'
                                ? "period"
                                : value == '-'
                                        ? "hyphen"
                                        : value == '_' ? "underscore" : String.valueOf(value));
    }

    private void type(String text) throws Exception {
        for (char value : text.toCharArray()) {
            String[] expected = new String[1];
            main(
                    () -> {
                        EditText input = editor();
                        int start = Math.min(input.getSelectionStart(), input.getSelectionEnd());
                        int end = Math.max(input.getSelectionStart(), input.getSelectionEnd());
                        String letter =
                                value == ' '
                                        ? " "
                                        : ((android.widget.Button) view(keyTag(value)))
                                                .getText()
                                                .toString();
                        expected[0] =
                                input.getText().subSequence(0, start).toString()
                                        + letter
                                        + input.getText().subSequence(end, input.length());
                    });
            click(keyTag(value));
            text(expected[0], "physical key updates editor " + value);
        }
    }

    private void text(String expected, String label) throws Exception {
        await(
                () -> editor() != null && expected.contentEquals(editor().getText()),
                label + ": " + expected);
    }

    private int count() {
        ListView list = (ListView) view("monitor-app-list");
        return list == null ? -1 : list.getAdapter().getCount();
    }

    private void home() throws Exception {
        float d = session.dpi / 160f;
        swipe(
                session.width() * .6f,
                session.height() - 4 * d,
                session.width() * .6f,
                session.height() - 100 * d);
        page("home");
    }

    private void controls() throws Exception {
        float d = session.dpi / 160f;
        swipe(session.width() * .6f, 4 * d, session.width() * .6f, 100 * d);
        page("controls");
    }

    private EditText startSearch() throws Exception {
        home();
        await(() -> count() > 1, "asynchronous app catalog ready before keyboard filtering");
        main(
                () -> {
                    check(
                            editor() != null && !editor().hasFocus(),
                            "opening launcher does not focus search");
                    check(!keyboardVisible(), "opening launcher does not summon keyboard");
                    check(editor().length() == 0, "reopened launcher starts with an empty query");
                });
        click("monitor-app-search");
        await(
                () -> keyboardVisible() && editor().hasFocus(),
                "real search tap focuses editor and shows keyboard");
        return editor();
    }

    private void noInput(EditText old, String label) {
        main(
                () -> {
                    check(!keyboardVisible(), label + " hides keyboard");
                    check(!old.hasFocus(), label + " releases old editor focus");
                });
    }

    private void close(EditText old, String label) throws Exception {
        click("monitor-close");
        page("closed");
        noInput(old, label);
    }

    private void ready() throws Exception {
        await(() -> session.id >= 0 && !session.busy, "search desktop ready");
        Thread.sleep(300);
    }

    private void key(int code) throws Exception {
        key(code, 0);
    }

    private void key(int code, int modifiers) throws Exception {
        long now = SystemClock.uptimeMillis();
        for (int action : new int[] {KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            KeyEvent event =
                    new KeyEvent(
                            now,
                            SystemClock.uptimeMillis(),
                            action,
                            code,
                            0,
                            modifiers,
                            KeyCharacterMap.VIRTUAL_KEYBOARD,
                            0,
                            0,
                            InputDevice.SOURCE_KEYBOARD);
            check(
                    DeviceTestSupport.injectOnDisplay(event, session.external.getDisplayId()),
                    "monitor hardware key " + KeyEvent.keyCodeToString(code) + " " + action);
        }
        Thread.sleep(100);
    }

    private void shiftArrow() throws Exception {
        long now = SystemClock.uptimeMillis();
        int shift = KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        check(
                DeviceTestSupport.injectOnDisplay(
                        new KeyEvent(
                                now,
                                now,
                                KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_SHIFT_LEFT,
                                0,
                                shift,
                                KeyCharacterMap.VIRTUAL_KEYBOARD,
                                0,
                                0,
                                InputDevice.SOURCE_KEYBOARD),
                        session.external.getDisplayId()),
                "hardware Shift held before selection arrow");
        try {
            key(KeyEvent.KEYCODE_DPAD_RIGHT, shift);
        } finally {
            check(
                    DeviceTestSupport.injectOnDisplay(
                            new KeyEvent(
                                    now,
                                    SystemClock.uptimeMillis(),
                                    KeyEvent.ACTION_UP,
                                    KeyEvent.KEYCODE_SHIFT_LEFT,
                                    0,
                                    0,
                                    KeyCharacterMap.VIRTUAL_KEYBOARD,
                                    0,
                                    0,
                                    InputDevice.SOURCE_KEYBOARD),
                            session.external.getDisplayId()),
                    "hardware Shift released after selection arrow");
        }
    }

    private void layout() throws Exception {
        Rect search = fullBounds("monitor-app-search"),
                close = fullBounds("monitor-close"),
                list = fullBounds("monitor-app-list"),
                keyboard = fullBounds("monitor-search-keyboard");
        check(
                !overlaps(search, keyboard)
                        && !overlaps(close, keyboard)
                        && !overlaps(list, keyboard),
                "keyboard leaves search, Close and results unobscured at rotation "
                        + session.rotation);
        Display.Mode mode = session.external.getMode();
        Rect screen = new Rect(0, 0, mode.getPhysicalWidth(), mode.getPhysicalHeight());
        check(
                screen.contains(keyboard)
                        && screen.contains(search)
                        && screen.contains(close)
                        && screen.contains(list),
                "full search UI stays inside physical monitor at rotation " + session.rotation);
        for (String name :
                new String[] {
                    "q",
                    "p",
                    "a",
                    "hyphen",
                    "shift",
                    "delete",
                    "period",
                    "underscore",
                    "space",
                    "clear",
                    "hide",
                    "1",
                    "0"
                }) bounds("monitor-key-" + name);
    }

    String run() throws Exception {
        try {
            phone = DeviceTestSupport.startPhone(runner);
            CountDownLatch connected = new CountDownLatch(1);
            binding =
                    new ServiceConnection() {
                        public void onServiceConnected(ComponentName name, IBinder binder) {
                            session = ((SessionService.LocalBinder) binder).get();
                            connected.countDown();
                        }

                        public void onServiceDisconnected(ComponentName name) {}
                    };
            bound =
                    runner.getTargetContext()
                            .bindService(
                                    new Intent(runner.getTargetContext(), SessionService.class),
                                    binding,
                                    Context.BIND_AUTO_CREATE);
            check(connected.await(5, TimeUnit.SECONDS), "search controller bound");
            ready();
            preferences = new HashMap<>(session.prefs.getAll());
            main(
                    () -> {
                        session.setMonitorNavigationEnabled(true);
                        session.setMonitorTouchEnabled(true);
                    });
            ComponentName test =
                    new ComponentName(runner.getTargetContext(), InputTestActivity.class);
            for (int rotation = 0; rotation < 4; rotation++) {
                final int selected = rotation;
                main(() -> session.launch(test, selected));
                await(
                        () -> !session.busy && session.rotation == selected,
                        "search rotation " + rotation);
                Thread.sleep(300);
                EditText old = startSearch();
                int underlying = InputTestActivity.touchEvents;
                layout();
                int all = count();
                check(all > 1, "unfiltered launcher contains multiple apps");
                type("input test");
                text("input test", "physical keys enter letters and a space");
                await(() -> count() == 1, "typed query filters to input test");
                check(view("app.dev.dexdroid") != null, "filtered app row is rendered");
                click("monitor-key-delete");
                text("input tes", "Delete removes one character");
                click("monitor-key-clear");
                text("", "Clear empties the query");
                await(() -> count() == all, "Clear restores all installed apps");
                type("zzzxq");
                text("zzzxq", "no-match query is entered through keys");
                await(() -> count() == 0, "no-match search displays no rows");
                click("monitor-key-clear");
                click("monitor-key-shift");
                click("monitor-key-i");
                type("nput test");
                text("Input test", "Shift capitalizes one letter");
                await(() -> count() == 1, "case-insensitive search recovers filtered app");
                click("monitor-key-clear");
                type("a9.-_");
                text("a9.-_", "numbers and package punctuation are editable");
                click("monitor-key-hide");
                await(() -> !keyboardVisible(), "Hide dismisses the search keyboard");
                text("a9.-_", "Hide preserves the query");
                click("monitor-app-search");
                await(this::keyboardVisible, "retapping search restores keyboard");
                click("monitor-key-clear");
                type("input test");
                check(
                        InputTestActivity.touchEvents == underlying,
                        "search and keyboard touches never click underlying app");
                click("app.dev.dexdroid");
                page("closed");
                noInput(old, "app selection");
                ready();
                check(
                        session.activePackage.equals("dev.dexdroid"),
                        "filtered app selection retains input test target");
            }
            main(() -> session.rotate(0, false));
            await(() -> !session.busy && session.rotation == 0, "dismissal tests landscape");
            Thread.sleep(300);
            EditText old = startSearch();
            type("abc");
            text("abc", "selection-edit fixture is typed through physical keys");
            // Cursor/range positions are fixtures for keyboard editing behavior; these
            // assertions do not claim to exercise long-press selection or ActionMode UI.
            main(() -> editor().setSelection(1, 2));
            click("monitor-key-delete");
            text("ac", "Delete removes the selected range");
            main(() -> editor().setSelection(1));
            click("monitor-key-z");
            text("azc", "character inserts at the existing cursor");
            main(() -> editor().setSelection(0, 2));
            click("monitor-key-b");
            text("bc", "character replaces a selected range");
            key(KeyEvent.KEYCODE_DEL);
            text("c", "hardware Delete edits at the insertion cursor");
            key(KeyEvent.KEYCODE_X);
            text("xc", "hardware text respects the existing cursor");
            key(KeyEvent.KEYCODE_DEL);
            text("c", "hardware Delete removes the typed character");
            close(old, "selection-edit Close");
            old = startSearch();
            close(old, "Close icon");
            old = startSearch();
            int underlying = InputTestActivity.touchEvents;
            float[] outside = physical(4 * session.dpi / 160f, session.height() * .5f);
            DeviceTestSupport.usableMonitorOutside(session, outside);
            tap(outside[0], outside[1]);
            page("closed");
            noInput(old, "outside tap");
            check(
                    InputTestActivity.touchEvents == underlying,
                    "keyboard outside dismissal has no click-through");
            old = startSearch();
            int back = InputTestActivity.backEvents;
            float d = session.dpi / 160f;
            swipe(
                    session.width() - 4 * d,
                    session.height() * .4f,
                    session.width() - 100 * d,
                    session.height() * .4f);
            page("home");
            check(!keyboardVisible(), "first edge Back hides keys without closing Apps");
            swipe(
                    session.width() - 4 * d,
                    session.height() * .4f,
                    session.width() - 100 * d,
                    session.height() * .4f);
            page("closed");
            noInput(old, "edge Back");
            check(
                    InputTestActivity.backEvents == back,
                    "edge Back closes launcher without reaching app");
            old = startSearch();
            int preImeBacks = InputTestActivity.backEvents;
            EditText preIme = old;
            main(
                    () -> {
                        long now = SystemClock.uptimeMillis();
                        check(
                                preIme.dispatchKeyEventPreIme(
                                        new KeyEvent(
                                                now,
                                                now,
                                                KeyEvent.ACTION_DOWN,
                                                KeyEvent.KEYCODE_BACK,
                                                0)),
                                "search consumes pre-IME Back down");
                        check(
                                preIme.dispatchKeyEventPreIme(
                                        new KeyEvent(
                                                now,
                                                now,
                                                KeyEvent.ACTION_UP,
                                                KeyEvent.KEYCODE_BACK,
                                                0,
                                                0,
                                                -1,
                                                0,
                                                KeyEvent.FLAG_CANCELED,
                                                InputDevice.SOURCE_KEYBOARD)),
                                "search consumes cancelled pre-IME Back release");
                        check(
                                session.monitorShellPage().equals("home") && keyboardVisible(),
                                "cancelled pre-IME Back leaves search open");
                        check(
                                preIme.dispatchKeyEventPreIme(
                                        new KeyEvent(
                                                now,
                                                now,
                                                KeyEvent.ACTION_DOWN,
                                                KeyEvent.KEYCODE_BACK,
                                                0)),
                                "search consumes next pre-IME Back down");
                        check(
                                preIme.dispatchKeyEventPreIme(
                                        new KeyEvent(
                                                now,
                                                now,
                                                KeyEvent.ACTION_UP,
                                                KeyEvent.KEYCODE_BACK,
                                                0)),
                                "search handles valid pre-IME Back release");
                    });
            page("home");
            check(!keyboardVisible(), "first pre-IME Back hides keys without closing Apps");
            key(KeyEvent.KEYCODE_BACK);
            page("closed");
            noInput(old, "pre-IME Back");
            check(
                    InputTestActivity.backEvents == preImeBacks,
                    "pre-IME Back does not reach underlying app");
            old = startSearch();
            key(KeyEvent.KEYCODE_X);
            text("x", "hardware typing reaches focused monitor search");
            key(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON);
            await(
                    () -> editor().getSelectionStart() == 0 && editor().getSelectionEnd() == 1,
                    "hardware Ctrl+A selects search text");
            key(KeyEvent.KEYCODE_Y);
            text("y", "hardware text replaces selected query");
            key(KeyEvent.KEYCODE_DEL);
            text("", "hardware Delete edits query");
            key(KeyEvent.KEYCODE_X);
            key(KeyEvent.KEYCODE_DPAD_LEFT);
            shiftArrow();
            await(
                    () ->
                            Math.min(editor().getSelectionStart(), editor().getSelectionEnd()) == 0
                                    && Math.max(
                                                    editor().getSelectionStart(),
                                                    editor().getSelectionEnd())
                                            == 1,
                    "hardware Shift+arrow selects query range");
            main(
                    () -> {
                        android.view.accessibility.AccessibilityNodeInfo node =
                                editor().createAccessibilityNodeInfo();
                        check(
                                node.isEditable() && "x".contentEquals(node.getText()),
                                "local search remains an accessible editable text node");
                        Bundle selection = new Bundle();
                        selection.putInt(
                                android.view.accessibility.AccessibilityNodeInfo
                                        .ACTION_ARGUMENT_SELECTION_START_INT,
                                1);
                        selection.putInt(
                                android.view.accessibility.AccessibilityNodeInfo
                                        .ACTION_ARGUMENT_SELECTION_END_INT,
                                1);
                        check(
                                editor().performAccessibilityAction(
                                                android.view.accessibility.AccessibilityNodeInfo
                                                        .ACTION_SET_SELECTION,
                                                selection),
                                "accessible text selection remains usable");
                        check(
                                editor().getSelectionStart() == 1
                                        && editor().getSelectionEnd() == 1,
                                "accessible action changes the selection");
                        selection.putInt(
                                android.view.accessibility.AccessibilityNodeInfo
                                        .ACTION_ARGUMENT_SELECTION_START_INT,
                                0);
                        check(
                                editor().performAccessibilityAction(
                                                android.view.accessibility.AccessibilityNodeInfo
                                                        .ACTION_SET_SELECTION,
                                                selection),
                                "accessible action selects the query for replacement");
                        check(
                                editor().onCreateInputConnection(
                                                        new android.view.inputmethod.EditorInfo())
                                        == null,
                                "local search supplies no editable system IME connection");
                    });
            key(KeyEvent.KEYCODE_X);
            text("x", "hardware typing retains accessible selection replacement");
            Thread.sleep(500);
            main(
                    () -> {
                        WindowInsets monitor = session.monitorShellView().getRootWindowInsets();
                        WindowInsets handset =
                                phone.getWindow().getDecorView().getRootWindowInsets();
                        check(
                                monitor != null
                                        && handset != null
                                        && !monitor.isVisible(WindowInsets.Type.ime())
                                        && !handset.isVisible(WindowInsets.Type.ime()),
                                "hardware search typing never summons system IME on either screen before Back");
                    });
            int[] searchMargins = shellMargins();
            int running = session.id;
            back = InputTestActivity.backEvents;
            key(KeyEvent.KEYCODE_BACK);
            page("home");
            check(!keyboardVisible(), "first hardware Back hides keys without closing Apps");
            text("x", "hiding local keys preserves hardware search query");
            await(
                    () -> {
                        View root = session.monitorShellView();
                        View focused = root == null ? null : root.findFocus();
                        return focused != null && !(focused instanceof EditText);
                    },
                    "hidden local keyboard leaves focus on a non-editor");
            await(
                    () -> {
                        WindowInsets monitor = session.monitorShellView().getRootWindowInsets();
                        WindowInsets handset =
                                phone.getWindow().getDecorView().getRootWindowInsets();
                        return monitor != null
                                && handset != null
                                && !monitor.isVisible(WindowInsets.Type.ime())
                                && !handset.isVisible(WindowInsets.Type.ime());
                    },
                    "first hardware Back leaves system IME hidden on both screens");
            check(
                    Arrays.equals(searchMargins, shellMargins()),
                    "hiding local keys preserves menu scrim pixels without focus tint");
            key(KeyEvent.KEYCODE_BACK);
            page("closed");
            for (int pixel : shellMargins())
                check(
                        Color.alpha(pixel) == 0,
                        "closed occupied shell leaves desktop transparent after Back");
            noInput(old, "hardware Back");
            check(
                    session.id == running && session.sessionAwake(),
                    "hardware Back keeps monitor session running");
            check(
                    InputTestActivity.backEvents == back,
                    "hardware Back closes menu without app Back");
            key(KeyEvent.KEYCODE_BACK);
            final int expectedBack = back + 1;
            await(
                    () -> InputTestActivity.backEvents == expectedBack,
                    "closed-monitor hardware Back reaches app exactly once");
            check(session.id == running, "closed-monitor hardware Back preserves desktop");
            old = startSearch();
            controls();
            noInput(old, "opening Controls");
            click("monitor-settings");
            page("settings");
            check(!keyboardVisible(), "Settings contains no stale search keyboard");
            click("monitor-close");
            page("closed");
            old = startSearch();
            type("input test");
            Rect held = bounds("app.dev.dexdroid");
            event(0, held.exactCenterX(), held.exactCenterY());
            Thread.sleep(650);
            event(1, held.exactCenterX(), held.exactCenterY());
            page("app-rotation");
            noInput(old, "held-app rotation submenu");
            click("monitor-once-0");
            page("closed");
            ready();
            home();
            pointerClick("monitor-app-search");
            await(
                    () -> keyboardVisible() && editor().hasFocus(),
                    "phone pointer opens monitor search keyboard");
            old = editor();
            underlying = InputTestActivity.touchEvents;
            for (char value : "input test".toCharArray()) pointerClick(keyTag(value));
            text("input test", "phone pointer types monitor search");
            await(() -> count() == 1, "phone pointer typing filters installed apps");
            check(session.pointerVisible, "phone pointer typing displays its cursor");
            check(
                    InputTestActivity.touchEvents == underlying,
                    "phone pointer search has no underlying clicks");
            pointerClick("monitor-close");
            page("closed");
            noInput(old, "phone pointer Close");
            old = startSearch();
            main(() -> session.rotate(2, false));
            await(
                    () -> !session.busy && session.rotation == 2,
                    "same-size 180-degree reconfiguration ready");
            Thread.sleep(300);
            noInput(old, "same-size rotation reconfiguration");
            page("home");
            check(editor() != old, "same-size rotation replaces stale search editor");
            check(editor().length() == 0, "same-size rotation clears stale query");
            click("monitor-app-search");
            await(this::keyboardVisible, "search works after same-size reconfiguration");
            layout();
            old = editor();
            close(old, "reconfigured Close");
            main(() -> session.rotate(0, false));
            await(() -> !session.busy && session.rotation == 0, "Fit search landscape");
            main(() -> session.preview(1920, 1080, 240));
            await(
                    () -> !session.busy && session.baseH == 1080 && session.dpi == 240,
                    "Fit 240-DPI search preview ready");
            Thread.sleep(300);
            old = startSearch();
            layout();
            type("input test");
            text("input test", "Fit/DPI keyboard typing");
            await(() -> count() == 1, "Fit/DPI search filters apps");
            tap(960, 45);
            page("closed");
            noInput(old, "Fit-bar outside dismissal");
            main(() -> session.confirm(false));
            ready();
            main(() -> session.preview(1280, 800, 160));
            await(
                    () -> !session.busy && session.baseW == 1280 && session.dpi == 160,
                    "160-DPI search preview ready");
            Thread.sleep(300);
            old = startSearch();
            layout();
            type("input test");
            text("input test", "lower-resolution keyboard typing");
            close(old, "lower-resolution Close");
            main(() -> session.confirm(false));
            ready();
            main(() -> phone.finish());
            Thread.sleep(300);
            old = startSearch();
            type("input test");
            text("input test", "background monitor keyboard types without phone controller");
            await(() -> count() == 1, "background monitor search filters apps");
            check(session.sessionAwake(), "background keyboard preserves active session");
            main(session::pauseDesktop);
            await(
                    () -> session.id < 0 && session.monitorShellView() == null,
                    "Stop releases active keyboard and monitor shell");
            noInput(old, "Stop");
            restore();
            return results.toString();
        } catch (Throwable error) {
            String query = editor() == null ? "<no editor>" : editor().getText().toString();
            throw new RuntimeException(
                    results
                            + "STATE query="
                            + query
                            + " rotation="
                            + session.rotation
                            + " page="
                            + session.monitorShellPage()
                            + " keyboard="
                            + keyboardVisible()
                            + " selection="
                            + (editor() == null
                                    ? "none"
                                    : editor().getSelectionStart()
                                            + ":"
                                            + editor().getSelectionEnd())
                            + "\nFAIL monitor search acceptance",
                    error);
        } finally {
            restore();
            if (bound) runner.getTargetContext().unbindService(binding);
        }
    }
}
