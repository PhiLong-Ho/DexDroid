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
    private IdleDesktop idle;
    private ControllerStyle style;
    private SearchEditor searchEditor;
    private MonitorSearchKeyboard searchKeyboard;
    private ListView searchResults;
    private final EdgeFeedback feedback;
    private boolean transitioning, outsideTouch, outsideEligible;
    private float outsideX, outsideY;
    private int transitionSerial;
    private volatile String page = "closed";
    private final ArrayDeque<String> history = new ArrayDeque<>();
    private final List<Runnable> updates = new ArrayList<>();
    private boolean updating, physicalUi, phoneUi, consuming;
    private String candidate = "";
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
        return !page.equals("closed") || transitioning || idleVisible();
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

    boolean physical(MotionEvent event, float width, float height) {
        if (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) return false;
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
            physicalUi = isOpen() || transitioning || idleVisible();
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
            if (p != null && session.monitorNavigationEnabled() && !session.busy) {
                startX = p[0];
                startY = p[1];
                float zone = dp(20);
                candidate =
                        startY < zone
                                ? "Controls"
                                : startY > session.height() - zone
                                        ? "Home"
                                        : startX < zone
                                                ? "Left Back"
                                                : startX > session.width() - zone
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
                    if (recognized.equals("Home")) home();
                    else if (recognized.equals("Controls")) show("controls");
                    else if (isOpen()) back();
                    else session.key(KeyEvent.KEYCODE_BACK);
                    feedback.finish(true);
                    consuming = action != MotionEvent.ACTION_UP;
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
                if (action == MotionEvent.ACTION_UP && outsideEligible) close();
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
        feedback.clear();
    }

    void destroy() {
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
        history.clear();
        history.push("closed");
        page = "home";
        build();
        enter();
        session.log("Monitor Home: launcher opened");
    }

    void show(String next) {
        if (!page.equals(next)) {
            history.push(page);
            page = next;
        }
        build();
        enter();
        session.log("Monitor panel opened: " + next);
    }

    void close() {
        if (page.equals("closed")) return;
        String old = page;
        dismissSearchKeyboard("close");
        root.clearFocus();
        history.clear();
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

    void back() {
        String next = history.isEmpty() ? "closed" : history.pop();
        if (next.equals("closed")) close();
        else {
            page = next;
            build();
            enter();
        }
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
                style.iconAction(
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
        ControllerStyle.StatusCard card = new ControllerStyle.StatusCard(context, () -> session);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(10));
        parent.addView(card, lp);
        updates.add(card::refresh);
    }

    private void launcher(LinearLayout parent) {
        List<AppCatalog.Entry> apps = AppCatalog.installed(session);
        List<AppCatalog.Entry> filtered = new ArrayList<>(apps);
        SearchEditor search = new SearchEditor(context);
        searchEditor = search;
        search.setTag("monitor-app-search");
        style.search(search);
        search.setHint("Search installed apps");
        parent.addView(search);
        TextView hint = text("", 12);
        hint.setPadding(0, dp(10), 0, dp(10));
        hint.setTextColor(Color.LTGRAY);
        hint.setText("Tap to launch · Hold for a one-time orientation");
        parent.addView(hint);
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
                        TextView view =
                                text(
                                        app.name
                                                + "\n"
                                                + (session.prefs.contains(
                                                                "app."
                                                                        + app.component
                                                                                .getPackageName())
                                                        ? rotations[
                                                                session.prefs.getInt(
                                                                        "app."
                                                                                + app.component
                                                                                        .getPackageName(),
                                                                        0)]
                                                        : "Session orientation"),
                                        15);
                        view.setTag("app." + app.component.getPackageName());
                        style.appCard(view, app.icon);
                        return view;
                    }
                };
        list.setAdapter(adapter);
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
                    close();
                    session.launch(app.component, null);
                });
        list.setOnItemLongClickListener(
                (p, v, index, id) -> {
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
    }

    private ComponentName pendingApp;
    private String pendingAppName = "";

    private void build() {
        if (context == null) return;
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
                    if (isOpen()) close();
                });
        root.setClickable(isOpen());
        panel = null;
        scrim = null;
        idle = new IdleDesktop(context, this::home);
        idle.setVisibility(
                page.equals("closed") && !session.hasDesktopApp() ? View.VISIBLE : View.GONE);
        root.addView(idle, new FrameLayout.LayoutParams(-1, -1));
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
            TextView heading =
                    text(
                            page.equals("home")
                                    ? "Open on monitor"
                                    : page.equals("controls")
                                            ? "DexDroid"
                                            : page.equals("rotation")
                                                    ? "Monitor orientation"
                                                    : page.equals("scroll")
                                                            ? "Scrolling"
                                                            : page.equals("app-rotation")
                                                                    ? pendingAppName
                                                                    : page.equals("display")
                                                                            ? "Resolution and DPI"
                                                                            : page.equals(
                                                                                            "profiles")
                                                                                    ? "Monitor profiles"
                                                                                    : page.equals(
                                                                                                    "diagnostics")
                                                                                            ? "Diagnostic log"
                                                                                            : "Settings",
                            page.equals("controls") ? 23 : 22);
            heading.setTag("monitor-title");
            heading.setGravity(Gravity.CENTER_VERTICAL);
            header.addView(heading, new LinearLayout.LayoutParams(0, dp(54), 1));
            if (page.equals("controls"))
                header.addView(
                        icon(
                                R.drawable.ic_settings,
                                "Settings",
                                "monitor-settings",
                                () -> show("settings")),
                        new LinearLayout.LayoutParams(dp(48), dp(48)));
            header.addView(
                    icon(R.drawable.ic_close, "Close", "monitor-close", this::close),
                    new LinearLayout.LayoutParams(dp(48), dp(48)));
            if (page.equals("home")) launcher(panel);
            else {
                ScrollView scroll = new ScrollView(context);
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
                            R.drawable.ic_scroll,
                            "Scroll",
                            "monitor-scroll",
                            () -> show("scroll"));
                    LinearLayout second = row(body);
                    second.setTag("monitor-row-2");
                    Button stop =
                            iconButton(
                                    second,
                                    R.drawable.ic_stop,
                                    "Stop",
                                    "monitor-stop",
                                    () -> session.pauseDesktop());
                    stop.setBackground(style.ripple(style.surface(245, 10, 245)));
                    style.buttonIcon(stop, R.drawable.ic_stop, Color.BLACK);
                    stop.setTextColor(Color.BLACK);
                    stop.setForceDarkAllowed(false);
                    iconButton(
                            second,
                            R.drawable.ic_save,
                            "Save app",
                            "monitor-save",
                            () -> session.saveAppSettings());
                    iconButton(second, R.drawable.ic_back, "Back", "monitor-back", this::back);
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
                                    back();
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
                                    close();
                                    if (app != null) session.launch(app, selected);
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
                } else if (page.equals("settings")) {
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
                            () -> {
                                float v = session.prefs.getFloat("speed", 1.6f);
                                session.prefs
                                        .edit()
                                        .putFloat("speed", v < 1.3f ? 1.6f : v < 2 ? 2.5f : 1)
                                        .apply();
                                session.report("Pointer speed changed from monitor");
                            });
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
                            "Reset app settings",
                            "monitor-reset",
                            () ->
                                    appName()
                                            + " · "
                                            + (session.prefs.contains(
                                                            "app." + session.activePackage)
                                                    ? "Saved"
                                                    : "Defaults"),
                            session::resetAppSettings);
                    item(
                            body,
                            "Reset monitor profile",
                            "monitor-reset-profile",
                            () -> session.monitorProfileStatus(),
                            session::resetMonitor);
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
                if (!page.equals("controls")) {
                    LinearLayout keep = row(body);
                    keep.setTag("monitor-confirmation");
                    button(keep, "Keep", "monitor-keep", () -> session.confirm(true));
                    button(keep, "Revert", "monitor-revert", () -> session.confirm(false));
                    updates.add(
                            () ->
                                    keep.setVisibility(
                                            session.awaitingConfirmation()
                                                    ? View.VISIBLE
                                                    : View.GONE));
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
                    page.equals("closed") && !transitioning && !session.hasDesktopApp()
                            ? View.VISIBLE
                            : View.GONE);
    }

    void refresh() {
        syncIdleVisibility();
        updating = true;
        try {
            for (Runnable update : updates) update.run();
        } finally {
            updating = false;
        }
    }
}
