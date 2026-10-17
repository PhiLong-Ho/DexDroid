package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.*;

/** Native monitor widgets laid out in virtual pixels, rotated with the desktop. */
final class MonitorShell {
    final FrameLayout root;
    private final SessionService session;
    private final Context displayContext;
    private Context context;
    private LinearLayout panel;
    private View scrim;
    private View protectionNotice;
    private boolean protectionDismissed;
    private IdleDesktop idle;
    private DestinationHighlight destination;
    private LauncherControls launcherActions;
    private ControllerStyle style;
    private SearchEditor searchEditor;
    private MonitorSearchKeyboard searchKeyboard;
    private ListView searchResults;
    private Runnable catalogCleanup;
    private final EdgeFeedback feedback;
    private boolean transitioning, outsideTouch, outsideEligible;
    private float outsideX, outsideY;
    private int transitionSerial;
    private volatile String page = "closed";
    private final MenuNavigation<String> navigation = new MenuNavigation<>();
    private final Map<String, Integer> scrollPositions = new HashMap<>();
    private ScrollView contentScroll;
    private String renderedPage = "", launcherQuery = "";
    private boolean discardViewState;
    private final List<Runnable> updates = new ArrayList<>();
    private boolean updating, physicalUi, phoneUi, consuming;
    private String candidate = "", pendingEdge = "";
    private float startX, startY;
    private long physicalDown, phoneDown;
    private int lastW, lastH, lastDpi, lastRotation = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick =
            new Runnable() {
                public void run() {
                    if (!page.equals("closed")) {
                        refresh();
                        handler.postDelayed(this, 750);
                    }
                }
            };
    private final String[] rotations = {
        "Landscape 0°", "Portrait 90°", "Landscape 180°", "Portrait 270°"
    };

    MonitorShell(SessionService service, Context context) {
        session = service;
        displayContext = context;
        root = new FrameLayout(context);
        root.setForceDarkAllowed(false);
        root.setClipChildren(false);
        root.setFocusableInTouchMode(true);
        feedback = new EdgeFeedback(context, v -> dp(v));
    }

    private int dp(float value) {
        return Math.round(value * session.dpi / 160f);
    }

    void layout(int physicalW, int physicalH) {
        int w = session.width(), h = session.height();
        if (lastW != w
                || lastH != h
                || lastDpi != session.dpi
                || lastRotation != session.rotation) {
            lastW = w;
            lastH = h;
            lastDpi = session.dpi;
            lastRotation = session.rotation;
            Configuration config =
                    new Configuration(displayContext.getResources().getConfiguration());
            config.densityDpi = session.dpi;
            context =
                    new ContextThemeWrapper(
                            displayContext.createConfigurationContext(config),
                            R.style.DexDroidTheme);
            style = new ControllerStyle(context);
            root.setLayoutParams(new FrameLayout.LayoutParams(w, h));
            build();
        }
        float scale =
                Math.min(
                        (float) physicalW / (session.rotation % 2 == 0 ? w : h),
                        (float) physicalH / (session.rotation % 2 == 0 ? h : w));
        root.setPivotX(w / 2f);
        root.setPivotY(h / 2f);
        root.setScaleX(scale);
        root.setScaleY(scale);
        root.setRotation(session.rotation * 90f);
        root.setX((physicalW - w) / 2f);
        root.setY((physicalH - h) / 2f);
    }

    String page() {
        return page;
    }

    boolean physicalActive() {
        return physicalUi || consuming;
    }

    private RectF bounds(View child) {
        RectF rect = new RectF(0, 0, child.getWidth(), child.getHeight());
        child.getMatrix().mapRect(rect);
        rect.offset(child.getLeft(), child.getTop());
        return rect;
    }

    private boolean idleVisible() {
        return idle != null && idle.getVisibility() == View.VISIBLE;
    }

    private boolean hit(float x, float y) {
        return !page.equals("closed") || transitioning || idleVisible() || protectionHit(x, y);
    }

    private boolean protectionHit(float x, float y) {
        return protectionNotice != null
                && protectionNotice.isShown()
                && bounds(protectionNotice).contains(x, y);
    }

    boolean animating() {
        return transitioning;
    }

    private Matrix inverse(float physicalW, float physicalH) {
        float[]
                src =
                        {
                            0,
                            0,
                            session.width(),
                            0,
                            session.width(),
                            session.height(),
                            0,
                            session.height()
                        },
                dst = new float[8];
        float[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int i = 0; i < 4; i++) {
            float[] p =
                    Geometry.map(
                            corners[i][0],
                            corners[i][1],
                            session.rotation,
                            physicalW,
                            physicalH,
                            session.width(),
                            session.height());
            dst[i * 2] = p[0];
            dst[i * 2 + 1] = p[1];
        }
        Matrix forward = new Matrix(), inverse = new Matrix();
        forward.setPolyToPoly(src, 0, dst, 0, 4);
        forward.invert(inverse);
        return inverse;
    }

    private float[] edgeZones(float width, float height) {
        float zone = dp(20);
        float[] edges = {zone, zone, zone, zone}; // top, right, bottom, left
        if (android.os.Build.VERSION.SDK_INT < 31) return edges;
        WindowInsets window = session.monitorWindow().getDecorView().getRootWindowInsets();
        if (window == null) return edges;
        Insets bars =
                window.getInsetsIgnoringVisibility(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        float scale =
                Math.min(
                        width / (session.rotation % 2 == 0 ? session.width() : session.height()),
                        height / (session.rotation % 2 == 0 ? session.height() : session.width()));
        float contentW = scale * (session.rotation % 2 == 0 ? session.width() : session.height());
        float contentH = scale * (session.rotation % 2 == 0 ? session.height() : session.width());
        float[] physical = {
            Math.max(0, bars.top - (height - contentH) / 2),
            Math.max(0, bars.right - (width - contentW) / 2),
            Math.max(0, bars.bottom - (height - contentH) / 2),
            Math.max(0, bars.left - (width - contentW) / 2)
        };
        for (int i = 0; i < 4; i++) edges[i] += physical[(i + session.rotation) % 4] / scale;
        return edges;
    }

    boolean physical(MotionEvent event, float width, float height) {
        if (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)
                && !event.isFromSource(InputDevice.SOURCE_MOUSE)) return false;
        int action = event.getActionMasked();
        float[] p =
                Geometry.unmap(
                        event.getX(),
                        event.getY(),
                        session.rotation,
                        width,
                        height,
                        session.width(),
                        session.height());
        if (action == MotionEvent.ACTION_DOWN) {
            cancelAll();
            session.hideMonitorPointer();
            physicalDown = event.getEventTime();
            physicalUi =
                    isOpen()
                            || transitioning
                            || idleVisible()
                            || (p != null && protectionHit(p[0], p[1]));
            outsideTouch =
                    (isOpen() || transitioning)
                            && (p == null || panel == null || !bounds(panel).contains(p[0], p[1]));
            outsideX = event.getX();
            outsideY = event.getY();
            outsideEligible = outsideTouch;
            if (physicalUi) {
                session.cancelPhoneInput();
                session.cancelMonitorGesture();
            }
            if (p != null
                    && event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)
                    && session.monitorNavigationEnabled()
                    && !session.busy) {
                startX = p[0];
                startY = p[1];
                float[] zones = edgeZones(width, height);
                candidate =
                        startY < zones[0]
                                ? "Controls"
                                : startY > session.height() - zones[2]
                                        ? "Home"
                                        : startX < zones[3]
                                                ? "Left Back"
                                                : startX > session.width() - zones[1]
                                                        ? "Right Back"
                                                        : "";
                if (!candidate.isEmpty()) {
                    session.log("Monitor edge candidate: " + candidate);
                    feedback.start(candidate, startX, startY);
                }
            }
        }
        if (consuming) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                consuming = false;
                physicalUi = false;
                String actionName = pendingEdge;
                pendingEdge = "";
                if (action == MotionEvent.ACTION_UP) commitEdge(actionName);
                else feedback.finish(false);
            }
            return true;
        }
        if (!candidate.isEmpty()) {
            if (event.getPointerCount() != 1
                    || p == null
                    || action == MotionEvent.ACTION_CANCEL
                    || event.getEventTime() - physicalDown > 1500) {
                session.log(
                        "Monitor edge rejected: "
                                + candidate
                                + " (multi-touch, Fit bar, timeout, or cancel)");
                candidate = "";
                feedback.finish(false);
            } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                float dx = p[0] - startX, dy = p[1] - startY;
                boolean vertical = candidate.equals("Home") || candidate.equals("Controls");
                float inward =
                        candidate.equals("Home")
                                ? -dy
                                : candidate.equals("Controls")
                                        ? dy
                                        : candidate.equals("Right Back") ? -dx : dx;
                float cross = vertical ? Math.abs(dx) : Math.abs(dy);
                feedback.progress(inward / Math.max(1, dp(60)));
                if (inward >= dp(60) && inward >= cross * 1.4f) {
                    String recognized = candidate;
                    candidate = "";
                    session.cancelMonitorGesture();
                    if (physicalUi) {
                        MotionEvent cancel = MotionEvent.obtain(event);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        dispatchPhysical(cancel, width, height);
                        cancel.recycle();
                    }
                    session.log(
                            "Monitor edge recognized: "
                                    + recognized
                                    + " dx="
                                    + Math.round(dx)
                                    + " dy="
                                    + Math.round(dy));
                    pendingEdge = recognized;
                    consuming = action != MotionEvent.ACTION_UP;
                    if (!consuming) {
                        pendingEdge = "";
                        commitEdge(recognized);
                    }
                    physicalUi = false;
                    return true;
                }
                if (cross > dp(30) && cross > Math.abs(inward) * 1.4f) {
                    session.log("Monitor edge rejected: " + candidate + " (perpendicular)");
                    candidate = "";
                    feedback.finish(false);
                } else if (action == MotionEvent.ACTION_UP) {
                    session.log("Monitor edge rejected: " + candidate + " (short or outward)");
                    candidate = "";
                    feedback.finish(false);
                }
            }
        }
        boolean owned = physicalUi;
        if (owned) {
            if (outsideTouch) {
                float distance =
                        (float) Math.hypot(event.getX() - outsideX, event.getY() - outsideY);
                if (event.getPointerCount() != 1 || distance > dp(10) * root.getScaleX())
                    outsideEligible = false;
                if (action == MotionEvent.ACTION_UP && outsideEligible) dismissPanel();
            } else dispatchPhysical(event, width, height);
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
            physicalUi = false;
        return owned;
    }

    private void dispatchPhysical(MotionEvent original, float w, float h) {
        if (page.equals("closed") && transitioning) return;
        MotionEvent mapped = MotionEvent.obtain(original);
        mapped.transform(inverse(w, h));
        root.dispatchTouchEvent(mapped);
        mapped.recycle();
    }

    boolean controllerPointer(MotionEvent event) {
        int action = event.getActionMasked();
        boolean generic =
                action == MotionEvent.ACTION_SCROLL
                        || action == MotionEvent.ACTION_HOVER_ENTER
                        || action == MotionEvent.ACTION_HOVER_MOVE
                        || action == MotionEvent.ACTION_HOVER_EXIT
                        || action == MotionEvent.ACTION_BUTTON_PRESS
                        || action == MotionEvent.ACTION_BUTTON_RELEASE;
        if (generic) {
            if (!hit(event.getX(), event.getY())) return false;
            if (!page.equals("closed") || !transitioning) root.dispatchGenericMotionEvent(event);
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN) phoneUi = hit(event.getX(), event.getY());
        if (!phoneUi) return false;
        if (!page.equals("closed") || !transitioning) root.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) phoneUi = false;
        return true;
    }

    boolean phone(int action, float[] xs, float[] ys) {
        int masked = action & 255;
        if (masked == MotionEvent.ACTION_DOWN) {
            phoneUi = hit(xs[0], ys[0]);
            phoneDown = SystemClock.uptimeMillis();
        }
        if (!phoneUi) return false;
        if (page.equals("closed") && transitioning) {
            if (masked == MotionEvent.ACTION_UP || masked == MotionEvent.ACTION_CANCEL)
                phoneUi = false;
            return true;
        }
        MotionEvent event = event(phoneDown, action, xs, ys);
        root.dispatchTouchEvent(event);
        event.recycle();
        if (masked == MotionEvent.ACTION_UP || masked == MotionEvent.ACTION_CANCEL) phoneUi = false;
        return true;
    }

    boolean wheel(float x, float y, float dx, float dy) {
        if (!hit(x, y)) return false;
        MotionEvent.PointerProperties prop = new MotionEvent.PointerProperties();
        prop.id = 0;
        prop.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x;
        coords.y = y;
        coords.setAxisValue(MotionEvent.AXIS_HSCROLL, dx);
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL, dy);
        long now = SystemClock.uptimeMillis();
        MotionEvent event =
                MotionEvent.obtain(
                        now,
                        now,
                        MotionEvent.ACTION_SCROLL,
                        1,
                        new MotionEvent.PointerProperties[] {prop},
                        new MotionEvent.PointerCoords[] {coords},
                        0,
                        0,
                        1,
                        1,
                        0,
                        0,
                        InputDevice.SOURCE_MOUSE,
                        0);
        root.dispatchGenericMotionEvent(event);
        event.recycle();
        return true;
    }

    private MotionEvent event(long down, int action, float[] xs, float[] ys) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[xs.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[xs.length];
        for (int i = 0; i < xs.length; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = i;
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = xs[i];
            coords[i].y = ys[i];
            coords[i].pressure = 1;
        }
        return MotionEvent.obtain(
                down,
                SystemClock.uptimeMillis(),
                action,
                xs.length,
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
    }

    void cancelPhone() {
        if (phoneUi) {
            MotionEvent cancel =
                    event(phoneDown, MotionEvent.ACTION_CANCEL, new float[] {0}, new float[] {0});
            root.dispatchTouchEvent(cancel);
            cancel.recycle();
        }
        phoneUi = false;
    }

    private void commitEdge(String action) {
        if (action.isEmpty()) return;
        int display = session.id;
        feedback.finish(true);
        // Finish the physical UP dispatch before a trusted desktop receives keys.
        // Otherwise Android can cancel the key while changing focus between displays.
        root.postOnAnimation(
                () -> {
                    if (session.id != display || session.monitorShellView() != root) return;
                    session.log("Monitor edge committed: " + action);
                    if (action.equals("Home")) home();
                    else if (action.equals("Controls")) show("controls");
                    else if (isOpen()) back();
                    else session.key(KeyEvent.KEYCODE_BACK);
                });
    }

    void cancelAll() {
        cancelPhone();
        if (physicalUi) {
            MotionEvent cancel =
                    event(
                            physicalDown,
                            MotionEvent.ACTION_CANCEL,
                            new float[] {0},
                            new float[] {0});
            root.dispatchTouchEvent(cancel);
            cancel.recycle();
        }
        physicalUi = false;
        outsideTouch = false;
        consuming = false;
        candidate = "";
        pendingEdge = "";
        feedback.clear();
    }

    void destroy() {
        navigation.clear();
        scrollPositions.clear();
        page = "closed";
        finishAppSelection();
        stopCatalog();
        dismissSearchKeyboard("destroy");
        cancelAll();
        transitionSerial++;
        transitioning = false;
        if (panel != null) panel.animate().cancel();
        handler.removeCallbacks(tick);
    }

    boolean isOpen() {
        return !page.equals("closed");
    }

    void home() {
        navigation.clear();
        scrollPositions.clear();
        launcherQuery = "";
        discardViewState = true;
        chooseApp(0);
    }

    private void finishAppSelection() {
        if (launcherActions != null) launcherActions.close();
        launcherActions = null;
    }

    void chooseApp(int pane) {
        if (session.splitEnabled()) session.cancelInput();
        finishAppSelection();
        launcherActions = new LauncherControls(session, context, pane);
        if (!launcherActions.current()) return;
        navigation.open("home");
        page = "home";
        build();
        enter();
        session.log("Monitor Home: launcher opened");
    }

    void show(String next) {
        if (next.equals("shizuku")) session.refreshShizukuState();
        if (next.equals("controls")) {
            navigation.clear();
            scrollPositions.clear();
            launcherQuery = "";
            discardViewState = true;
        }
        if (!next.equals("home") && !next.equals("app-rotation")) finishAppSelection();
        if (session.splitEnabled()) session.cancelInput();
        navigation.open(next);
        page = next;
        build();
        enter();
        session.log("Monitor panel opened: " + next);
    }

    void close() {
        finishAppSelection();
        if (page.equals("closed")) return;
        stopCatalog();
        String old = page;
        dismissSearchKeyboard("close");
        root.clearFocus();
        navigation.clear();
        scrollPositions.clear();
        launcherQuery = "";
        discardViewState = true;
        page = "closed";
        handler.removeCallbacks(tick);
        if (panel == null) {
            build();
            return;
        }
        int serial = ++transitionSerial;
        transitioning = true;
        panel.animate().cancel();
        if (scrim != null) scrim.animate().alpha(0).setDuration(160).start();
        panel.animate()
                .alpha(0)
                .translationY(dp(old.equals("home") ? 24 : -24))
                .scaleX(.98f)
                .scaleY(.98f)
                .setDuration(160)
                .setInterpolator(new android.view.animation.PathInterpolator(.4f, 0, 1, 1))
                .withEndAction(
                        () -> {
                            if (serial == transitionSerial) build();
                        })
                .start();
        session.log("Monitor transition dismiss: " + old);
    }

    private boolean canReturn(String next) {
        return !next.equals("closed")
                && (!(next.equals("home") || next.equals("app-rotation"))
                        || (launcherActions != null && launcherActions.current()));
    }

    private void dismissPanel() {
        close();
    }

    private boolean hideLocalKeyboard() {
        View keyboard = root.findViewWithTag("monitor-search-keyboard");
        if (keyboard == null || keyboard.getVisibility() != View.VISIBLE)
            keyboard = root.findViewWithTag("profile-keyboard");
        if (keyboard == null || keyboard.getVisibility() != View.VISIBLE) return false;
        keyboard.setVisibility(View.GONE);
        root.clearFocus();
        return true;
    }

    void back() {
        if (!hideLocalKeyboard()) navigateBack();
    }

    private void navigateBack() {
        String next = navigation.back(this::canReturn);
        if (next == null) {
            close();
            return;
        }
        if (!next.equals("home") && !next.equals("app-rotation")) finishAppSelection();
        page = next;
        build();
        enter();
        session.log("Monitor panel Back: " + page);
    }

    private void enter() {
        if (panel == null) return;
        int serial = ++transitionSerial;
        transitioning = true;
        panel.setAlpha(0);
        panel.setTranslationY(dp(page.equals("home") ? 24 : -24));
        panel.setScaleX(.97f);
        panel.setScaleY(.97f);
        if (scrim != null) {
            scrim.setAlpha(0);
            scrim.animate().alpha(1).setDuration(200).start();
        }
        panel.animate()
                .alpha(1)
                .translationY(0)
                .scaleX(1)
                .scaleY(1)
                .setDuration(220)
                .setInterpolator(new android.view.animation.PathInterpolator(.2f, 0, 0, 1))
                .withEndAction(
                        () -> {
                            if (serial == transitionSerial) transitioning = false;
                        })
                .start();
        session.log("Monitor transition enter: " + page);
    }

    private android.graphics.drawable.Drawable surface(int shade) {
        return style.surface(shade, 12, 40);
    }

    private TextView text(String value, int size) {
        return style.label(value, size);
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private void section(LinearLayout parent, String value) {
        TextView view = text(value, 10);
        view.setTextColor(Color.rgb(145, 145, 145));
        view.setLetterSpacing(.14f);
        view.setPadding(dp(4), dp(14), 0, dp(6));
        parent.addView(view);
    }

    private Button button(LinearLayout row, String label, String tag, Runnable action) {
        Button b =
                style.button(
                        row,
                        label,
                        () -> {
                            if (isOpen()) action.run();
                        });
        b.setTag(tag);
        return b;
    }

    private Button iconButton(
            LinearLayout row, int icon, String label, String tag, Runnable action) {
        Button b =
                style.iconButton(
                        row,
                        icon,
                        label,
                        () -> {
                            if (isOpen()) action.run();
                        });
        b.setTag(tag);
        return b;
    }

    private ImageButton icon(int icon, String name, String tag, Runnable action) {
        ImageButton b =
                style.menuIconAction(
                        icon,
                        name,
                        () -> {
                            if (isOpen()) action.run();
                        });
        b.setTag(tag);
        return b;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(context);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private void item(
            LinearLayout parent,
            String label,
            String tag,
            java.util.function.Supplier<String> value,
            Runnable action) {
        LinearLayout line = style.settingRow(parent);
        line.setTag(tag);
        LinearLayout labels = column();
        line.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        labels.addView(text(label, 14));
        TextView current = text("", 12);
        current.setTextColor(Color.rgb(155, 155, 155));
        current.setPadding(0, dp(4), 0, 0);
        labels.addView(current);
        TextView arrow = text("›", 24);
        arrow.setTextColor(Color.GRAY);
        arrow.setGravity(Gravity.CENTER);
        line.addView(arrow, new LinearLayout.LayoutParams(dp(32), dp(40)));
        updates.add(
                () -> {
                    current.setText(value.get());
                    line.setContentDescription(label + ", " + value.get());
                });
        line.setOnClickListener(
                v -> {
                    if (isOpen()) {
                        action.run();
                        refresh();
                    }
                });
    }

    private void toggle(
            LinearLayout parent,
            String label,
            String tag,
            BooleanSupplier enabled,
            Consumer<Boolean> action) {
        Switch view = new Switch(context);
        view.setTag(tag);
        style.card(view);
        view.setThumbTintList(
                new android.content.res.ColorStateList(
                        new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                        new int[] {Color.WHITE, Color.GRAY}));
        view.setTrackTintList(
                new android.content.res.ColorStateList(
                        new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                        new int[] {Color.rgb(95, 95, 95), Color.rgb(45, 45, 45)}));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.setMargins(0, dp(3), 0, dp(3));
        parent.addView(view, layout);
        updates.add(
                () -> {
                    view.setText(label + " · " + (enabled.getAsBoolean() ? "On" : "Off"));
                    view.setChecked(enabled.getAsBoolean());
                });
        view.setOnCheckedChangeListener(
                (b, on) -> {
                    if (!updating && isOpen()) {
                        action.accept(on);
                        refresh();
                    }
                });
    }

    private String appName() {
        if (session.activePackage.isEmpty()) return "No app selected";
        if (session.activePackage.equals(session.getPackageName())) return "DexDroid input test";
        try {
            return session.getPackageManager()
                    .getApplicationLabel(
                            session.getPackageManager()
                                    .getApplicationInfo(session.activePackage, 0))
                    .toString();
        } catch (Exception e) {
            return session.activePackage;
        }
    }

    private String speed() {
        return session.scrollSpeed() < .35f
                ? "Slow 20%"
                : session.scrollSpeed() < .75f ? "Normal 50%" : "Fast 100%";
    }

    /** The ROM's system IME is hidden behind Presentation and cannot follow its rotation. */
    private final class SearchEditor extends EditText {
        SearchEditor(Context context) {
            super(context);
            setShowSoftInputOnFocus(false);
            setImeOptions(
                    android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                            | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        }

        @Override
        public boolean onKeyPreIme(int code, KeyEvent event) {
            if (code == KeyEvent.KEYCODE_BACK && searchEditor == this && page.equals("home")) {
                session.log(
                        "Monitor search Back: "
                                + event.getAction()
                                + " cancelled="
                                + event.isCanceled());
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled())
                    session.key(KeyEvent.KEYCODE_BACK);
                return true;
            }
            return super.onKeyPreIme(code, event);
        }

        void requestKeyboard() {
            if (searchEditor != this || !page.equals("home") || searchKeyboard == null) return;
            requestFocus();
            hideSystemKeyboard(this);
            if (searchKeyboard.getVisibility() != View.VISIBLE) {
                searchKeyboard.showWithin(searchResults.getHeight());
                session.log("Monitor search keyboard shown");
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            boolean handled = super.onTouchEvent(event);
            if (handled && event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
            return handled;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            requestKeyboard();
            return true;
        }
    }

    private void hideSystemKeyboard(EditText editor) {
        ((android.view.inputmethod.InputMethodManager)
                        editor.getContext().getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(editor.getWindowToken(), 0);
    }

    private void dismissSearchKeyboard(String reason) {
        SearchEditor editor = searchEditor;
        if (editor == null) return;
        hideSystemKeyboard(editor);
        if (searchKeyboard != null && searchKeyboard.getVisibility() == View.VISIBLE) {
            searchKeyboard.setVisibility(View.GONE);
            session.log("Monitor search keyboard hidden: " + reason);
        }
        editor.clearFocus();
        searchEditor = null;
        searchKeyboard = null;
        searchResults = null;
    }

    private void status(LinearLayout parent) {
        ControllerStyle.StatusCard card =
                new ControllerStyle.StatusCard(
                        context,
                        () -> session,
                        () -> show("protected-help"),
                        "monitor",
                        () -> {
                            if (session.desktopRequested() || session.id >= 0 || session.busy)
                                session.pauseDesktop();
                            else session.retryDesktop();
                        },
                        () -> show("shizuku"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(10));
        parent.addView(card, lp);
        updates.add(card::refresh);
    }

    private void launcher(LinearLayout parent) {
        if (launcherActions == null) return;
        LauncherControls launcher = launcherActions;
        launcher.addModes(parent);
        updates.add(launcher::refresh);
        List<AppCatalog.Entry> apps = new ArrayList<>(), filtered = new ArrayList<>();
        AppCatalog catalog = session.appCatalog;
        SearchEditor search = new SearchEditor(context);
        searchEditor = search;
        search.setTag("monitor-app-search");
        style.search(search);
        search.setHint("Search installed apps");
        parent.addView(search);
        TextView hint = text("", 12);
        hint.setPadding(0, dp(10), 0, dp(10));
        hint.setTag("monitor-app-target");
        hint.setTextColor(Color.LTGRAY);
        hint.setText(
                session.splitEnabled()
                        ? "Choose Left/Right or Up/Down beside an app"
                        : "Tap to launch · Hold for a one-time orientation");
        parent.addView(hint);
        ControllerStyle.CatalogStatus loading = style.new CatalogStatus("monitor-app-loading");
        parent.addView(loading, new LinearLayout.LayoutParams(-1, -2));
        ListView list = new ListView(context);
        searchResults = list;
        list.setTag("monitor-app-list");
        style.appList(list);
        BaseAdapter adapter =
                new BaseAdapter() {
                    public int getCount() {
                        return filtered.size();
                    }

                    public Object getItem(int p) {
                        return filtered.get(p);
                    }

                    public long getItemId(int p) {
                        return p;
                    }

                    public View getView(int p, View old, ViewGroup group) {
                        AppCatalog.Entry app = filtered.get(p);
                        return launcher.appRow(
                                app,
                                (session.prefs.contains("app." + app.component.getPackageName())
                                        ? rotations[
                                                session.prefs.getInt(
                                                        "app." + app.component.getPackageName(), 0)]
                                        : "Session orientation"),
                                MonitorShell.this::close);
                    }
                };
        list.setAdapter(adapter);
        launcher.onChanged(adapter::notifyDataSetChanged);
        updates.add(
                () ->
                        hint.setText(
                                session.busy
                                        ? "Updating layout…"
                                        : session.splitEnabled()
                                                ? "Choose Left/Right or Up/Down beside an app"
                                                : "Tap to launch · Hold for a one-time orientation"));
        parent.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        searchKeyboard =
                new MonitorSearchKeyboard(
                        context, search, () -> session.log("Monitor search keyboard hidden: Hide"));
        parent.addView(searchKeyboard, new LinearLayout.LayoutParams(-1, -2));
        list.setOnItemClickListener(
                (p, v, index, id) -> {
                    AppCatalog.Entry app = filtered.get(index);
                    session.log(
                            "Monitor launcher selection: " + app.component.flattenToShortString());
                    if (!session.splitEnabled() && launcher.launch(app.component, null, 0)) close();
                });
        list.setOnItemLongClickListener(
                (p, v, index, id) -> {
                    if (session.splitEnabled() || !launcher.current()) return true;
                    AppCatalog.Entry app = filtered.get(index);
                    pendingApp = app.component;
                    pendingAppName = app.name;
                    show("app-rotation");
                    return true;
                });
        search.addTextChangedListener(
                new android.text.TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        String query = s.toString().toLowerCase(Locale.ROOT);
                        filtered.clear();
                        for (AppCatalog.Entry app : apps)
                            if (app.name.toLowerCase(Locale.ROOT).contains(query)
                                    || app.component
                                            .getPackageName()
                                            .toLowerCase(Locale.ROOT)
                                            .contains(query)) filtered.add(app);
                        adapter.notifyDataSetChanged();
                    }

                    public void afterTextChanged(android.text.Editable value) {}
                });
        catalogCleanup =
                catalog.observe(
                        state -> {
                            if (!page.equals("home") || searchEditor != search) return;
                            loading.update(state, catalog::invalidate);
                            Parcelable position = list.onSaveInstanceState();
                            apps.clear();
                            apps.addAll(state.apps);
                            String query = search.getText().toString().toLowerCase(Locale.ROOT);
                            filtered.clear();
                            for (AppCatalog.Entry app : apps)
                                if (app.name.toLowerCase(Locale.ROOT).contains(query)
                                        || app.component
                                                .getPackageName()
                                                .toLowerCase(Locale.ROOT)
                                                .contains(query)) filtered.add(app);
                            adapter.notifyDataSetChanged();
                            list.onRestoreInstanceState(position);
                        });
        search.setText(launcherQuery);
    }

    private void stopCatalog() {
        if (catalogCleanup != null) {
            catalogCleanup.run();
            catalogCleanup = null;
        }
    }

    private ComponentName pendingApp;
    private String pendingAppName = "";

    private void build() {
        if (context == null) return;
        if (!discardViewState) {
            if (contentScroll != null)
                scrollPositions.put(renderedPage, contentScroll.getScrollY());
            if (searchEditor != null) launcherQuery = searchEditor.getText().toString();
        }
        discardViewState = false;
        contentScroll = null;
        renderedPage = page;
        stopCatalog();
        dismissSearchKeyboard("menu or display rebuild");
        handler.removeCallbacks(tick);
        transitionSerial++;
        transitioning = false;
        if (panel != null) panel.animate().cancel();
        updates.clear();
        root.removeAllViews();
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setOnClickListener(
                v -> {
                    if (isOpen()) dismissPanel();
                });
        root.setClickable(isOpen());
        panel = null;
        scrim = null;
        idle = new IdleDesktop(context, this::home);
        idle.setVisibility(
                page.equals("closed") && !session.splitEnabled() && !session.hasDesktopApp()
                        ? View.VISIBLE
                        : View.GONE);
        root.addView(idle, new FrameLayout.LayoutParams(-1, -1));
        destination = new DestinationHighlight(context, session);
        root.addView(destination, new FrameLayout.LayoutParams(-1, -1));
        protectionNotice =
                style.protectedNotice(
                        "monitor",
                        () -> {
                            protectionDismissed = true;
                            show("protected-help");
                        },
                        () -> {
                            protectionDismissed = true;
                            protectionNotice.setVisibility(View.GONE);
                        });
        FrameLayout.LayoutParams noticeLayout =
                new FrameLayout.LayoutParams(
                        Math.min(dp(400), session.width() - dp(48)),
                        -2,
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        noticeLayout.bottomMargin = dp(24);
        root.addView(protectionNotice, noticeLayout);
        if (!page.equals("closed")) {
            scrim = new View(context);
            scrim.setTag("monitor-scrim");
            scrim.setBackgroundColor(Color.argb(140, 0, 0, 0));
            root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
            panel = column();
            panel.setTag("monitor-panel");
            panel.setClickable(true);
            panel.setPadding(dp(14), dp(6), dp(14), dp(12));
            panel.setBackground(style.surface(8, 14, 40));
            if (!page.equals("controls")) style.menuPanel(panel);
            panel.setTag("monitor-panel");
            int height =
                    page.equals("controls") ? -2 : Math.min(dp(560), session.height() - dp(48));
            FrameLayout.LayoutParams lp =
                    new FrameLayout.LayoutParams(
                            Math.min(dp(400), session.width() - dp(48)), height, Gravity.CENTER);
            root.addView(panel, lp);
            LinearLayout header = row(panel);
            header.setGravity(Gravity.CENTER_VERTICAL);
            if (navigation.hasParent(this::canReturn))
                header.addView(
                        icon(R.drawable.ic_back, "Back", "monitor-up", this::navigateBack),
                        new LinearLayout.LayoutParams(dp(48), dp(48)));
            TextView heading =
                    text(
                            page.equals("protected-help")
                                    ? ProtectedScreenGuide.TITLE
                                    : page.equals("layout")
                                            ? "Layout"
                                            : page.equals("home")
                                                    ? "Open on monitor"
                                                    : page.equals("controls")
                                                            ? "DexDroid"
                                                            : page.equals("rotation")
                                                                    ? "Monitor orientation"
                                                                    : page.equals("scroll")
                                                                            ? "Scrolling"
                                                                            : page.equals(
                                                                                            "app-rotation")
                                                                                    ? pendingAppName
                                                                                    : page.equals(
                                                                                                    "display")
                                                                                            ? "Resolution and DPI"
                                                                                            : page
                                                                                                            .equals(
                                                                                                                    "profiles")
                                                                                                    ? "Monitor profiles"
                                                                                                    : page
                                                                                                                    .equals(
                                                                                                                            "diagnostics")
                                                                                                            ? "Diagnostic log"
                                                                                                            : "Settings",
                            page.equals("controls") ? 23 : 22);
            if (page.equals("app-settings")) heading.setText(R.string.app_settings_title);
            if (page.equals("pointer-speed")) heading.setText(R.string.pointer_speed_title);
            if (page.equals("shizuku")) heading.setText(R.string.shizuku_setup_title);
            if (page.equals("user-guide")) heading.setText(R.string.user_guide_title);
            heading.setTag("monitor-title");
            style.menuHeading(heading);
            header.addView(heading, new LinearLayout.LayoutParams(0, dp(54), 1));
            if (page.equals("controls")) {
                header.addView(
                        icon(
                                R.drawable.ic_settings,
                                "Settings",
                                "monitor-settings",
                                () -> show("settings")),
                        new LinearLayout.LayoutParams(dp(48), dp(48)));
            }
            header.addView(
                    icon(R.drawable.ic_close, "Close menus", "monitor-close", this::dismissPanel),
                    new LinearLayout.LayoutParams(dp(48), dp(48)));
            if (!page.equals("controls") && !page.equals("profiles")) {
                LinearLayout keep = row(panel);
                keep.setTag("monitor-confirmation");
                button(keep, "Keep", "monitor-keep", () -> session.confirm(true));
                button(keep, "Revert", "monitor-revert", () -> session.confirm(false));
                keep.setVisibility(session.awaitingConfirmation() ? View.VISIBLE : View.GONE);
                updates.add(
                        () ->
                                keep.setVisibility(
                                        session.awaitingConfirmation() ? View.VISIBLE : View.GONE));
            }
            if (page.equals("home")) launcher(panel);
            else {
                ScrollView scroll = new ScrollView(context);
                scroll.setTag("monitor-menu-scroll");
                contentScroll = scroll;
                int savedScroll = scrollPositions.getOrDefault(page, 0);
                style.restoreScroll(scroll, savedScroll);
                LinearLayout body = column();
                scroll.addView(body);
                panel.addView(
                        scroll,
                        page.equals("controls")
                                ? new LinearLayout.LayoutParams(-1, -2)
                                : new LinearLayout.LayoutParams(-1, 0, 1));
                if (page.equals("controls")) {
                    status(body);
                    LinearLayout first = row(body);
                    first.setTag("monitor-row-1");
                    iconButton(first, R.drawable.ic_apps, "Apps", "monitor-apps", this::home);
                    iconButton(
                            first,
                            R.drawable.ic_rotation,
                            "Rotate",
                            "monitor-rotate",
                            () -> show("rotation"));
                    iconButton(
                            first,
                            R.drawable.ic_layout,
                            "Layout",
                            "monitor-layout",
                            () -> show("layout"));
                } else if (page.equals("protected-help")) {
                    ProtectedScreenGuide.add(style, body, "monitor");
                } else if (page.equals("rotation")) {
                    RadioGroup choices = new RadioGroup(context);
                    for (int i = 0; i < 4; i++) {
                        style.choice(choices, rotations[i], "monitor-rotation-" + i, 101 + i);
                    }
                    body.addView(choices);
                    choices.check(101 + session.rotation);
                    choices.setOnCheckedChangeListener(
                            (g, id) -> {
                                if (!updating) {
                                    session.rotate(id - 101, true);
                                    navigateBack();
                                }
                            });
                } else if (page.equals("app-rotation")) {
                    for (int i = 0; i < 4; i++) {
                        final int selected = i;
                        item(
                                body,
                                rotations[i],
                                "monitor-once-" + i,
                                () -> "This launch only",
                                () -> {
                                    ComponentName app = pendingApp;
                                    if (app != null
                                            && launcherActions != null
                                            && launcherActions.launch(app, selected, 0)) close();
                                });
                    }
                } else if (page.equals("scroll")) {
                    if (session.activePackage.isEmpty())
                        body.addView(text("Select an app before configuring scrolling", 13));
                    else {
                        TextView name = text(appName() + " · Saved automatically", 12);
                        name.setTextColor(Color.LTGRAY);
                        body.addView(name);
                        section(body, "MODE");
                        RadioGroup modes = new RadioGroup(context);
                        for (int i = 0; i < 2; i++) {
                            style.choice(
                                    modes,
                                    i == 0
                                            ? "Swipe \u00b7 Touchscreen drag"
                                            : "Wheel \u00b7 Mouse scrolling",
                                    i == 0 ? "monitor-swipe" : "monitor-wheel",
                                    201 + i);
                        }
                        body.addView(modes);
                        modes.check(session.swipeScrolling() ? 201 : 202);
                        modes.setOnCheckedChangeListener(
                                (g, id) -> {
                                    if (!updating) session.setSwipeScrolling(id == 201);
                                });
                        section(body, "SPEED");
                        RadioGroup rates = new RadioGroup(context);
                        for (int i = 0; i < 3; i++) {
                            style.choice(
                                    rates,
                                    new String[] {
                                                "Slow \u00b7 20%",
                                                "Normal \u00b7 50%",
                                                "Fast \u00b7 100%"
                                            }
                                            [i],
                                    "monitor-speed-" + i,
                                    211 + i);
                        }
                        body.addView(rates);
                        rates.check(
                                session.scrollSpeed() < .35f
                                        ? 211
                                        : session.scrollSpeed() < .75f ? 212 : 213);
                        rates.setOnCheckedChangeListener(
                                (g, id) -> {
                                    if (!updating) {
                                        session.prefs
                                                .edit()
                                                .putFloat(
                                                        "scrollSpeed." + session.activePackage,
                                                        new float[] {.2f, .5f, 1f}[id - 211])
                                                .apply();
                                        session.report("Monitor scroll speed saved");
                                    }
                                });
                        updates.add(
                                () -> {
                                    modes.check(session.swipeScrolling() ? 201 : 202);
                                    rates.check(
                                            session.scrollSpeed() < .35f
                                                    ? 211
                                                    : session.scrollSpeed() < .75f ? 212 : 213);
                                });
                    }
                } else if (page.equals("pointer-speed")) {
                    RadioGroup choices = new RadioGroup(context);
                    body.addView(choices);
                    for (int i = 0; i < 3; i++)
                        style.choice(
                                choices,
                                new String[] {"Slow", "Normal", "Fast"}[i],
                                "monitor-pointer-" + i,
                                301 + i);
                    float current = session.prefs.getFloat("speed", 1.6f);
                    choices.check(current < 1.3f ? 301 : current < 2 ? 302 : 303);
                    choices.setOnCheckedChangeListener(
                            (group, id) -> {
                                if (updating) return;
                                session.prefs
                                        .edit()
                                        .putFloat("speed", new float[] {1, 1.6f, 2.5f}[id - 301])
                                        .apply();
                                session.report("Pointer speed changed");
                                navigateBack();
                            });
                    updates.add(
                            () -> {
                                float value = session.prefs.getFloat("speed", 1.6f);
                                choices.check(value < 1.3f ? 301 : value < 2 ? 302 : 303);
                            });
                } else if (page.equals("app-settings")) {
                    body.addView(text(appName(), 14));
                    item(body, "Scrolling", "monitor-scroll", this::speed, () -> show("scroll"));
                    item(
                            body,
                            "Save app settings",
                            "monitor-save",
                            () -> "Save rotation and current app preferences",
                            session::saveAppSettings);
                    item(
                            body,
                            "Reset app settings",
                            "monitor-reset",
                            () -> "Restore defaults for " + appName(),
                            session::resetAppSettings);
                } else if (page.equals("settings")) {
                    style.versionInfo(body);
                    item(
                            body,
                            "App settings",
                            "monitor-app-settings",
                            this::appName,
                            () -> show("app-settings"));
                    item(
                            body,
                            "Shizuku setup",
                            "monitor-shizuku",
                            () -> session.shizukuState.label,
                            () -> show("shizuku"));
                    item(
                            body,
                            "User guide",
                            "monitor-user-guide",
                            () -> "Setup, controls and troubleshooting",
                            () -> show("user-guide"));
                    section(body, "DISPLAY");
                    item(
                            body,
                            "Monitor profiles",
                            "monitor-profiles",
                            session::monitorProfileStatus,
                            () -> show("profiles"));
                    item(
                            body,
                            "Resolution and DPI",
                            "monitor-display",
                            () ->
                                    session.width()
                                            + " × "
                                            + session.height()
                                            + " · "
                                            + session.dpi
                                            + " DPI",
                            () -> show("display"));
                    toggle(
                            body,
                            "Monitor touch",
                            "monitor-touch",
                            () -> session.monitorTouchEnabled(),
                            session::setMonitorTouchEnabled);
                    toggle(
                            body,
                            "Edge navigation",
                            "monitor-navigation",
                            () -> session.monitorNavigationEnabled(),
                            session::setMonitorNavigationEnabled);
                    section(body, "INPUT");
                    item(
                            body,
                            "Pointer speed",
                            "monitor-pointer-speed",
                            () -> {
                                float v = session.prefs.getFloat("speed", 1.6f);
                                return v < 1.3 ? "Slow" : v < 2 ? "Normal" : "Fast";
                            },
                            () -> show("pointer-speed"));
                    toggle(
                            body,
                            "Reverse scroll direction",
                            "monitor-reverse",
                            () -> session.prefs.getBoolean("reverseScroll", false),
                            on -> {
                                session.prefs.edit().putBoolean("reverseScroll", on).apply();
                                session.report("Scroll direction changed from monitor");
                            });
                    item(
                            body,
                            "Center pointer",
                            "monitor-center",
                            () -> "Recenter on desktop",
                            session::center);
                    section(body, "GENERAL");
                    item(
                            body,
                            "Protected screen help",
                            "monitor-protected-settings",
                            () -> "Blank apps and Chrome incognito",
                            () -> show("protected-help"));
                    item(
                            body,
                            "Background operation",
                            "monitor-background",
                            () ->
                                    (session.backgroundProtectionEnabled()
                                                    ? "Allowed"
                                                    : "Restricted")
                                            + " · Configure on phone",
                            () -> {
                                Intent intent =
                                        session.backgroundProtectionEnabled()
                                                ? new Intent(
                                                        android.provider.Settings
                                                                .ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                : new Intent(
                                                        android.provider.Settings
                                                                .ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                                        android.net.Uri.parse(
                                                                "package:"
                                                                        + session
                                                                                .getPackageName()));
                                ActivityOptions options = ActivityOptions.makeBasic();
                                options.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
                                session.startActivity(
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        options.toBundle());
                            });
                    item(
                            body,
                            "Diagnostic log",
                            "monitor-diagnostics",
                            () -> "Gesture and connection events",
                            () -> show("diagnostics"));
                    item(
                            body,
                            "Reset monitor profile",
                            "monitor-reset-profile",
                            () -> session.monitorProfileStatus(),
                            session::resetMonitor);
                } else if (page.equals("shizuku")) {
                    ShizukuSetup.Panel setup = new ShizukuSetup.Panel(context, session, "monitor");
                    body.addView(setup.view);
                    updates.add(setup::refresh);
                } else if (page.equals("user-guide")) {
                    UserGuide.add(style, body);
                    item(
                            body,
                            "Shizuku setup",
                            "monitor-guide-shizuku",
                            () -> session.shizukuState.label,
                            () -> show("shizuku"));
                    item(
                            body,
                            "Protected screen help",
                            "monitor-guide-protected",
                            () -> "Blank apps and Chrome incognito",
                            () -> show("protected-help"));
                } else if (page.equals("layout")) {
                    LayoutPanel layoutPanel =
                            new LayoutPanel(session, context, body, this::chooseApp);
                    updates.add(layoutPanel::refresh);
                } else if (page.equals("profiles")) {
                    MonitorProfilesPanel profiles =
                            new MonitorProfilesPanel(context, session, true);
                    body.addView(profiles);
                    updates.add(profiles::refresh);
                } else if (page.equals("display")) {
                    body.addView(text(session.capabilities(), 12));
                    if (session.external != null) {
                        Display.Mode mode = session.external.getMode();
                        item(
                                body,
                                "Same as output",
                                "monitor-native",
                                () -> mode.getPhysicalWidth() + " × " + mode.getPhysicalHeight(),
                                () ->
                                        session.preview(
                                                mode.getPhysicalWidth(),
                                                mode.getPhysicalHeight(),
                                                Geometry.recommendedDpi(
                                                        Math.min(
                                                                mode.getPhysicalWidth(),
                                                                mode.getPhysicalHeight()))));
                        item(
                                body,
                                "Lower rendering load",
                                "monitor-low",
                                () ->
                                        Math.round(mode.getPhysicalWidth() * 2f / 3)
                                                + " × "
                                                + Math.round(mode.getPhysicalHeight() * 2f / 3),
                                () -> {
                                    int w = Math.round(mode.getPhysicalWidth() * 2f / 3),
                                            h = Math.round(mode.getPhysicalHeight() * 2f / 3);
                                    session.preview(w, h, Geometry.recommendedDpi(Math.min(w, h)));
                                });
                        for (int value : new int[] {160, 240, 320})
                            item(
                                    body,
                                    "UI DPI " + value,
                                    "monitor-dpi-" + value,
                                    () -> session.dpi == value ? "Selected" : "",
                                    () -> session.preview(session.baseW, session.baseH, value));
                    }
                } else if (page.equals("diagnostics")) {
                    TextView log = text("", 11);
                    log.setTypeface(Typeface.MONOSPACE);
                    log.setTextIsSelectable(true);
                    body.addView(log);
                    updates.add(() -> log.setText(session.diagnosticText()));
                    LinearLayout actions = row(body);
                    button(
                            actions,
                            "Copy",
                            "monitor-log-copy",
                            () ->
                                    ((ClipboardManager)
                                                    session.getSystemService(
                                                            Context.CLIPBOARD_SERVICE))
                                            .setPrimaryClip(
                                                    ClipData.newPlainText(
                                                            "DexDroid diagnostics",
                                                            session.diagnosticText())));
                    button(actions, "Clear", "monitor-log-clear", session::clearDiagnostics);
                }
            }
        }
        root.addView(feedback, new FrameLayout.LayoutParams(-1, -1));
        root.requestFocus();
        refresh();
        if (isOpen()) handler.postDelayed(tick, 750);
    }

    private void syncIdleVisibility() {
        if (idle != null)
            idle.setVisibility(
                    page.equals("closed")
                                    && !transitioning
                                    && !session.splitEnabled()
                                    && !session.hasDesktopApp()
                            ? View.VISIBLE
                            : View.GONE);
    }

    // Pane bounds are physical workspace pixels, independent of locale reading order.
    @android.annotation.SuppressLint("RtlHardcoded")
    void refresh() {
        if (destination != null) destination.invalidate();
        syncIdleVisibility();
        if (!session.protectedContent) protectionDismissed = false;
        if (protectionNotice != null) {
            SplitLayout.Box protectedBox =
                    session.split == null ? null : session.split.protectedBox();
            FrameLayout.LayoutParams position;
            if (protectedBox != null) {
                int width = Math.max(dp(120), Math.min(dp(400), protectedBox.width - dp(24)));
                position = new FrameLayout.LayoutParams(width, -2, Gravity.TOP | Gravity.LEFT);
                position.leftMargin = protectedBox.x + (protectedBox.width - width) / 2;
                position.topMargin = protectedBox.y + Math.max(0, protectedBox.height - dp(80));
            } else {
                position =
                        new FrameLayout.LayoutParams(
                                Math.min(dp(400), session.width() - dp(48)),
                                -2,
                                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                position.bottomMargin = dp(24);
            }
            FrameLayout.LayoutParams old =
                    (FrameLayout.LayoutParams) protectionNotice.getLayoutParams();
            if (old.width != position.width
                    || old.gravity != position.gravity
                    || old.leftMargin != position.leftMargin
                    || old.topMargin != position.topMargin
                    || old.bottomMargin != position.bottomMargin)
                protectionNotice.setLayoutParams(position);
            protectionNotice.setVisibility(
                    page.equals("closed")
                                    && session.protectedContent
                                    && !protectionDismissed
                                    && (!session.splitEnabled() || protectedBox != null)
                            ? View.VISIBLE
                            : View.GONE);
        }
        updating = true;
        try {
            for (Runnable update : updates) update.run();
        } finally {
            updating = false;
        }
    }
}
