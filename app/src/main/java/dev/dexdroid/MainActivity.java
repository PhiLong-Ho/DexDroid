package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.*;

public class MainActivity extends Activity {
    private SessionService session;
    private ControllerStyle style;
    private ControllerStyle.StatusCard statusCard;
    private TextView status, monitorStatus, settingsStatus;
    private Button stop;
    private Runnable menuCleanup;
    private Dialog settingsDialog;
    private boolean updatingMenu;
    private final List<Runnable> menuUpdaters = new ArrayList<>();
    private LinearLayout confirmation;
    private Pad pad;
    private final String[] orientations = {
        "Landscape 0°", "Portrait 90°", "Landscape 180°", "Portrait 270°"
    };
    private final ServiceConnection connection =
            new ServiceConnection() {
                public void onServiceConnected(ComponentName n, IBinder b) {
                    session = ((SessionService.LocalBinder) b).get();
                    session.listener = () -> refresh();
                    session.ensureDesktop();
                    refresh();
                }

                public void onServiceDisconnected(ComponentName n) {
                    session = null;
                    refresh();
                }
            };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        style = new ControllerStyle(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(6), dp(14), dp(12));
        root.setBackgroundColor(Color.rgb(8, 8, 8));
        LinearLayout header = row(root);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("DexDroid", 23);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(54), 1));
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(
                iconAction(R.drawable.ic_settings, "Settings", this::options),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        statusCard = new ControllerStyle.StatusCard(this, () -> session);
        LinearLayout.LayoutParams panelLayout = new LinearLayout.LayoutParams(-1, -2);
        panelLayout.setMargins(0, dp(4), 0, dp(10));
        root.addView(statusCard, panelLayout);
        monitorStatus = statusCard.monitor;
        status = statusCard.operation;
        settingsStatus = statusCard.appSettings;
        confirmation = statusCard.confirmation;
        LinearLayout top = row(root);
        iconButton(top, R.drawable.ic_apps, "Apps", this::apps);
        iconButton(top, R.drawable.ic_rotation, "Rotate", this::rotation);
        iconButton(top, R.drawable.ic_scroll, "Scroll", this::scrollConfiguration);
        LinearLayout actions = row(root);
        stop = iconButton(actions, R.drawable.ic_stop, "Stop", this::stopOrStart);
        iconButton(
                actions,
                R.drawable.ic_save,
                "Save app",
                () -> {
                    if (session != null) {
                        pad.cancel();
                        session.saveAppSettings();
                    }
                });
        iconButton(
                actions,
                R.drawable.ic_back,
                "Back",
                () -> {
                    if (session != null) {
                        session.log("Back button pressed");
                        session.key(KeyEvent.KEYCODE_BACK);
                    }
                });
        FrameLayout input = new FrameLayout(this);
        LinearLayout.LayoutParams inputLayout = new LinearLayout.LayoutParams(-1, 0, 1);
        inputLayout.topMargin = dp(12);
        root.addView(input, inputLayout);
        pad = new Pad();
        pad.setTag("touchpad");
        input.addView(pad, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams helpLayout =
                new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END);
        helpLayout.setMargins(0, dp(2), dp(2), 0);
        input.addView(
                iconAction(R.drawable.ic_info, "Touchpad help", this::touchpadHelp), helpLayout);
        input.setMinimumHeight(dp(240));
        ScrollView screen =
                new ScrollView(this) {
                    private boolean padContact;

                    @Override
                    public boolean onInterceptTouchEvent(MotionEvent event) {
                        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                            int[] location = new int[2];
                            pad.getLocationOnScreen(location);
                            Rect area =
                                    new Rect(
                                            location[0],
                                            location[1],
                                            location[0] + pad.getWidth(),
                                            location[1] + pad.getHeight());
                            padContact =
                                    area.contains((int) event.getRawX(), (int) event.getRawY());
                        }
                        if (padContact) {
                            if (event.getActionMasked() == MotionEvent.ACTION_UP
                                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                                padContact = false;
                            return false;
                        }
                        return super.onInterceptTouchEvent(event);
                    }
                };
        screen.setTag("phone-controller-scroll");
        screen.setFillViewport(true);
        screen.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(screen);
        bindService(new Intent(this, SessionService.class), connection, BIND_AUTO_CREATE);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView label(String text, int size) {
        return style.label(text, size);
    }

    private android.graphics.drawable.GradientDrawable surface(int shade, int radius, int border) {
        return style.surface(shade, radius, border);
    }

    private android.graphics.drawable.Drawable ripple(
            android.graphics.drawable.Drawable background) {
        return style.ripple(background);
    }

    private LinearLayout row(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private ImageButton iconAction(int icon, String name, Runnable action) {
        return style.iconAction(icon, name, action);
    }

    private Button button(LinearLayout row, String text, Runnable action) {
        return style.button(row, text, action);
    }

    private void buttonIcon(Button b, int icon, int color) {
        style.buttonIcon(b, icon, color);
    }

    private Button iconButton(LinearLayout row, int icon, String name, Runnable action) {
        return style.iconButton(row, icon, name, action);
    }

    private void refresh() {
        statusCard.refresh();
        if (session == null) {
            refreshMenu();
            return;
        }
        boolean requested = session.desktopRequested() || session.id >= 0 || session.busy;
        stop.setText(requested ? "Stop" : "Start");
        stop.setContentDescription(requested ? "Stop desktop" : "Start desktop");
        stop.setTooltipText(requested ? "Stop desktop" : "Start desktop");
        stop.setTextColor(requested ? Color.BLACK : Color.WHITE);
        stop.setBackground(ripple(surface(requested ? 245 : 20, 10, requested ? 245 : 45)));
        buttonIcon(
                stop,
                requested ? R.drawable.ic_stop : R.drawable.ic_play,
                requested ? Color.BLACK : Color.WHITE);
        refreshMenu();
        pad.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null) refresh();
    }

    @Override
    protected void onDestroy() {
        if (settingsDialog != null) settingsDialog.dismiss();
        if (session != null) {
            pad.cancel();
            session.listener = null;
        }
        unbindService(connection);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        if (pad != null) pad.cancel();
        super.onPause();
    }

    private void stopOrStart() {
        if (session == null) return;
        pad.cancel();
        if (!session.desktopRequested() && session.id < 0 && !session.busy) {
            session.retryDesktop();
            return;
        }
        int controllerTask = getTaskId();
        session.pauseDesktop();
        // Android can bring a former external-display task onto the phone when its
        // display is removed. Keep the user's controller visible after pressing Stop.
        new Handler(Looper.getMainLooper())
                .postDelayed(
                        () -> {
                            if (isFinishing()
                                    || isDestroyed()
                                    || session == null
                                    || session.desktopRequested()) return;
                            try {
                                for (ActivityManager.AppTask task :
                                        ((ActivityManager) getSystemService(ACTIVITY_SERVICE))
                                                .getAppTasks())
                                    if (task.getTaskInfo().taskId == controllerTask) {
                                        task.moveToFront();
                                        break;
                                    }
                            } catch (RuntimeException e) {
                                session.log("Controller focus after Stop: " + e.getMessage());
                            }
                        },
                        250);
    }

    private void touchpadHelp() {
        if (pad != null) pad.cancel();
        new AlertDialog.Builder(this)
                .setTitle("Touchpad gestures")
                .setMessage(
                        "Slide one finger to move the pointer.\nTap to click.\n\nUse two fingers to scroll. Spread or pinch two fingers to zoom.\n\nSwipe three fingers right to go Back.\n\nThe Scroll control sets mode and speed for the active app. Check Settings → Diagnostic log if a gesture does not respond.")
                .setPositiveButton("Got it", null)
                .show();
    }

    private LinearLayout beginMenu(String title, String page, boolean scrollable) {
        if (settingsDialog != null) settingsDialog.dismiss();
        menuUpdaters.clear();
        Dialog dialog = new Dialog(this);
        settingsDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        style.menuPanel(root);
        root.setTag("phone-menu-" + page);
        LinearLayout header = row(root);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = label(title, 22);
        heading.setTag("phone-menu-title");
        heading.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(54), 1));
        ImageButton close = iconAction(R.drawable.ic_close, "Close " + title, dialog::dismiss);
        close.setTag("phone-menu-close");
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setTag("phone-menu-content");
        if (scrollable) {
            ScrollView scroll = new ScrollView(this);
            scroll.setTag("phone-menu-scroll");
            scroll.setFillViewport(true);
            scroll.addView(content);
            root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        } else root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        root.setFocusableInTouchMode(true);
        dialog.setContentView(root);
        root.requestFocus();
        dialog.setOnDismissListener(
                d -> {
                    if (settingsDialog == dialog) {
                        if (menuCleanup != null) {
                            menuCleanup.run();
                            menuCleanup = null;
                        }
                        menuUpdaters.clear();
                        settingsDialog = null;
                    }
                });
        Window window = dialog.getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(.65f);
        window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        dialog.show();
        window.setLayout(-1, -1);
        return content;
    }

    Dialog controllerMenu() {
        return settingsDialog;
    }

    private void rotation() {
        if (session == null) return;
        pad.cancel();
        LinearLayout content = beginMenu("Monitor orientation", "rotation", true);
        section(content, "ORIENTATION");
        RadioGroup choices = new RadioGroup(this);
        content.addView(choices);
        for (int i = 0; i < 4; i++)
            style.choice(choices, orientations[i], "phone-rotation-" + i, 101 + i);
        choices.check(101 + session.rotation);
        choices.setOnCheckedChangeListener(
                (g, id) -> {
                    if (!updatingMenu) {
                        session.rotate(id - 101, true);
                        if (settingsDialog != null) settingsDialog.dismiss();
                    }
                });
        section(content, "APP");
        settingAction(
                content,
                "Save for app",
                () -> activeAppName(),
                () -> {
                    session.rememberApp(true);
                    refreshMenu();
                });
        refreshMenu();
    }

    private String speedName() {
        float speed = session.scrollSpeed();
        return speed < .35f ? "Slow" : speed < .75f ? "Normal" : "Fast";
    }

    private void scrollConfiguration() {
        if (session == null) return;
        pad.cancel();
        LinearLayout content = beginMenu("Scrolling", "scroll", true);
        if (session.activePackage.isEmpty()) {
            content.addView(label("Select an app before configuring scrolling", 13));
            return;
        }
        TextView app = label(activeAppName() + " \u00b7 Saved automatically", 12);
        app.setTextColor(Color.LTGRAY);
        content.addView(app);
        section(content, "MODE");
        RadioGroup mode = new RadioGroup(this);
        content.addView(mode);
        String[] modes = {"Swipe \u00b7 Touchscreen drag", "Wheel \u00b7 Mouse scrolling"};
        for (int i = 0; i < 2; i++)
            style.choice(mode, modes[i], i == 0 ? "phone-swipe" : "phone-wheel", 201 + i);
        mode.check(session.swipeScrolling() ? 201 : 202);
        mode.setOnCheckedChangeListener(
                (g, id) -> {
                    if (!updatingMenu) {
                        session.setSwipeScrolling(id == 201);
                        refreshMenu();
                    }
                });
        section(content, "SPEED");
        RadioGroup speed = new RadioGroup(this);
        content.addView(speed);
        String[] speeds = {"Slow \u00b7 20%", "Normal \u00b7 50%", "Fast \u00b7 100%"};
        for (int i = 0; i < 3; i++) style.choice(speed, speeds[i], "phone-speed-" + i, 211 + i);
        speed.check(session.scrollSpeed() < .35f ? 211 : session.scrollSpeed() < .75f ? 212 : 213);
        speed.setOnCheckedChangeListener(
                (g, id) -> {
                    if (!updatingMenu) {
                        session.prefs
                                .edit()
                                .putFloat(
                                        "scrollSpeed." + session.activePackage,
                                        new float[] {.2f, .5f, 1f}[id - 211])
                                .apply();
                        session.report(
                                "Scroll speed \u00b7 "
                                        + speedName()
                                        + " ("
                                        + Math.round(session.scrollSpeed() * 100)
                                        + "%)");
                        refreshMenu();
                    }
                });
        menuUpdaters.add(
                () -> {
                    mode.check(session.swipeScrolling() ? 201 : 202);
                    speed.check(
                            session.scrollSpeed() < .35f
                                    ? 211
                                    : session.scrollSpeed() < .75f ? 212 : 213);
                });
    }

    private String activeAppName() {
        if (session == null || session.activePackage.isEmpty()) return "No app selected";
        try {
            return getPackageManager()
                    .getApplicationLabel(
                            getPackageManager().getApplicationInfo(session.activePackage, 0))
                    .toString();
        } catch (PackageManager.NameNotFoundException e) {
            return session.activePackage;
        }
    }

    private void refreshSettings() {
        statusCard.refresh();
    }

    private void section(LinearLayout root, String name) {
        TextView t = label(name, 10);
        t.setTextColor(Color.rgb(145, 145, 145));
        t.setLetterSpacing(.14f);
        t.setPadding(dp(4), dp(14), 0, dp(6));
        root.addView(t);
    }

    private LinearLayout menuRow(LinearLayout root) {
        return style.settingRow(root);
    }

    private TextView menuText(LinearLayout line, String title) {
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        line.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        text.addView(label(title, 14));
        TextView value = label("", 12);
        value.setTextColor(Color.rgb(155, 155, 155));
        value.setPadding(0, dp(4), 0, 0);
        text.addView(value);
        return value;
    }

    private void settingAction(
            LinearLayout root, String title, Supplier<String> current, Runnable action) {
        LinearLayout line = menuRow(root);
        TextView value = menuText(line, title);
        TextView arrow = label("›", 24);
        arrow.setTextColor(Color.GRAY);
        arrow.setGravity(Gravity.CENTER);
        line.addView(arrow, new LinearLayout.LayoutParams(dp(32), dp(40)));
        menuUpdaters.add(() -> value.setText(current.get()));
        line.setOnClickListener(v -> action.run());
    }

    private void settingToggle(
            LinearLayout root,
            String title,
            BooleanSupplier enabled,
            Supplier<String> current,
            Consumer<Boolean> change) {
        LinearLayout line = menuRow(root);
        TextView value = menuText(line, title);
        Switch toggle = new Switch(this);
        toggle.setContentDescription(title);
        toggle.setThumbTintList(
                new android.content.res.ColorStateList(
                        new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                        new int[] {Color.WHITE, Color.GRAY}));
        toggle.setTrackTintList(
                new android.content.res.ColorStateList(
                        new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                        new int[] {Color.rgb(95, 95, 95), Color.rgb(45, 45, 45)}));
        line.addView(toggle, new LinearLayout.LayoutParams(dp(52), dp(42)));
        menuUpdaters.add(
                () -> {
                    value.setText(current.get());
                    toggle.setChecked(enabled.getAsBoolean());
                });
        toggle.setOnCheckedChangeListener(
                (b, on) -> {
                    if (!updatingMenu) change.accept(on);
                });
        line.setOnClickListener(v -> toggle.toggle());
    }

    private void refreshMenu() {
        if (session == null) return;
        updatingMenu = true;
        try {
            for (Runnable update : menuUpdaters) update.run();
        } finally {
            updatingMenu = false;
        }
    }

    private void pointerSpeed() {
        if (session == null) return;
        LinearLayout content = beginMenu("Pointer speed", "pointer-speed", true);
        section(content, "SPEED");
        float value = session.prefs.getFloat("speed", 1.6f);
        RadioGroup choices = new RadioGroup(this);
        content.addView(choices);
        for (int i = 0; i < 3; i++)
            style.choice(
                    choices,
                    new String[] {"Slow", "Normal", "Fast"}[i],
                    "phone-pointer-" + i,
                    301 + i);
        choices.check(value < 1.3f ? 301 : value < 2 ? 302 : 303);
        choices.setOnCheckedChangeListener(
                (g, id) -> {
                    session.prefs
                            .edit()
                            .putFloat("speed", new float[] {1, 1.6f, 2.5f}[id - 301])
                            .apply();
                    session.report("Pointer speed changed");
                    settingsDialog.dismiss();
                });
    }

    private void options() {
        if (session == null) return;
        pad.cancel();
        LinearLayout list = beginMenu("Settings", "settings", true);
        if (session.needsRecovery()) {
            section(list, "CONNECTION");
            settingAction(
                    list,
                    "Retry connection",
                    () -> session.needsRecovery() ? "Shizuku unavailable" : "Connected",
                    () -> session.retryDesktop());
            settingAction(
                    list,
                    "Open Shizuku",
                    () -> "Start its wireless debugging service",
                    () -> {
                        Intent launch =
                                getPackageManager()
                                        .getLaunchIntentForPackage("moe.shizuku.privileged.api");
                        if (launch != null) startActivity(launch);
                        else session.report("Install Shizuku to use the desktop");
                    });
        }
        section(list, "DISPLAY");
        settingAction(
                list,
                "Resolution and DPI",
                () ->
                        session.external == null
                                ? "No monitor connected"
                                : session.width()
                                        + " × "
                                        + session.height()
                                        + " · "
                                        + session.dpi
                                        + " UI DPI",
                this::displaySettings);
        settingToggle(
                list,
                "Monitor touch",
                () -> session.monitorTouchEnabled(),
                () -> session.monitorTouchEnabled() ? "On" : "Off",
                on -> session.setMonitorTouchEnabled(on));
        settingToggle(
                list,
                "Monitor edge navigation",
                () -> session.monitorNavigationEnabled(),
                () -> session.monitorNavigationEnabled() ? "On" : "Off",
                on -> session.setMonitorNavigationEnabled(on));
        section(list, "INPUT");
        settingAction(
                list,
                "Pointer speed",
                () -> {
                    float v = session.prefs.getFloat("speed", 1.6f);
                    return v < 1.3f ? "Slow" : v < 2 ? "Normal" : "Fast";
                },
                this::pointerSpeed);
        settingToggle(
                list,
                "Scroll direction",
                () -> session.prefs.getBoolean("reverseScroll", false),
                () -> session.prefs.getBoolean("reverseScroll", false) ? "Reversed" : "Normal",
                on -> {
                    session.prefs.edit().putBoolean("reverseScroll", on).apply();
                    session.report("Scroll direction · " + (on ? "Reversed" : "Normal"));
                });
        settingAction(
                list, "Center pointer", () -> "Recenter on the desktop", () -> session.center());
        section(list, "GENERAL");
        settingAction(
                list,
                "Background operation",
                () ->
                        session.backgroundProtectionEnabled()
                                ? "Allowed · Battery exemption enabled"
                                : "Restricted · Tap to allow",
                () ->
                        startActivity(
                                session.backgroundProtectionEnabled()
                                        ? new Intent(
                                                android.provider.Settings
                                                        .ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        : new Intent(
                                                android.provider.Settings
                                                        .ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                                android.net.Uri.parse(
                                                        "package:" + getPackageName()))));
        settingAction(
                list,
                "Diagnostic log",
                () -> "Gesture and connection event history",
                () -> {
                    settingsDialog.dismiss();
                    diagnostics();
                });
        section(list, "RESET");
        settingAction(
                list,
                "Reset app settings",
                () ->
                        session.activePackage.isEmpty()
                                ? "No app selected"
                                : activeAppName()
                                        + " · "
                                        + (session.prefs.contains("app." + session.activePackage)
                                                        || session.prefs.contains(
                                                                "scrollSpeed."
                                                                        + session.activePackage)
                                                        || session.prefs.contains(
                                                                "swipeScroll."
                                                                        + session.activePackage)
                                                ? "Custom settings"
                                                : "Defaults"),
                () -> session.resetAppSettings());
        settingAction(
                list,
                "Reset monitor profile",
                () ->
                        session.external == null
                                ? "No monitor connected"
                                : session.prefs.contains(session.monitorKey() + ".w")
                                        ? "Saved profile"
                                        : "Default profile",
                () -> {
                    if (session.external == null)
                        session.report("Connect a monitor before resetting its profile");
                    else session.resetMonitor();
                });
        refreshMenu();
    }

    private void diagnostics() {
        if (session == null) return;
        SessionService source = session;
        LinearLayout body = beginMenu("Diagnostic log", "diagnostics", true);
        TextView text = label(source.diagnosticText(), 11);
        text.setTag("phone-diagnostic-text");
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextIsSelectable(true);
        body.addView(text);
        section(body, "ACTIONS");
        settingAction(
                body,
                "Copy log",
                () -> "Copy all diagnostic events",
                () -> {
                    ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                            .setPrimaryClip(
                                    ClipData.newPlainText(
                                            "DexDroid diagnostics", source.diagnosticText()));
                    Toast.makeText(this, "Diagnostic log copied", Toast.LENGTH_SHORT).show();
                });
        settingAction(
                body,
                "Clear log",
                () -> "Clear recorded events",
                () -> {
                    source.clearDiagnostics();
                    text.setText(source.diagnosticText());
                });
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable update =
                new Runnable() {
                    public void run() {
                        String snapshot = source.diagnosticText();
                        if (!snapshot.contentEquals(text.getText())) text.setText(snapshot);
                        handler.postDelayed(this, 500);
                    }
                };
        menuCleanup = () -> handler.removeCallbacks(update);
        handler.post(update);
        refreshMenu();
    }

    private void appOrientation(AppCatalog.Entry app) {
        LinearLayout body = beginMenu(app.name, "app-rotation", true);
        section(body, "THIS LAUNCH ONLY");
        for (int i = 0; i < 4; i++) {
            final int rotation = i;
            settingAction(
                    body,
                    orientations[i],
                    () -> "This launch only",
                    () -> {
                        session.launch(app.component, rotation);
                        settingsDialog.dismiss();
                    });
        }
        refreshMenu();
    }

    private void apps() {
        if (session == null) return;
        pad.cancel();
        List<AppCatalog.Entry> all = AppCatalog.installed(session), filtered = new ArrayList<>(all);
        LinearLayout root = beginMenu("Open on monitor", "apps", false);
        settingsDialog
                .getWindow()
                .clearFlags(
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        EditText search = new EditText(this);
        style.search(search);
        search.setTag("phone-app-search");
        search.setHint("Search installed apps");
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = label("Tap to launch \u00b7 Hold for a one-time orientation", 12);
        hint.setTextColor(Color.LTGRAY);
        hint.setPadding(0, dp(10), 0, dp(10));
        root.addView(hint);
        ListView list = new ListView(this);
        list.setTag("phone-app-list");
        style.appList(list);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
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

                    public View getView(int p, View reused, ViewGroup parent) {
                        AppCatalog.Entry app = filtered.get(p);
                        TextView item =
                                label(
                                        app.name
                                                + "\n"
                                                + (session.prefs.contains(
                                                                "app."
                                                                        + app.component
                                                                                .getPackageName())
                                                        ? orientations[
                                                                session.prefs.getInt(
                                                                        "app."
                                                                                + app.component
                                                                                        .getPackageName(),
                                                                        0)]
                                                        : "Session orientation"),
                                        14);
                        item.setTag("app." + app.component.getPackageName());
                        style.appCard(item, app.icon);
                        return item;
                    }
                };
        list.setAdapter(adapter);
        list.setOnItemClickListener(
                (parent, view, p, id) -> {
                    session.launch(filtered.get(p).component, null);
                    settingsDialog.dismiss();
                });
        list.setOnItemLongClickListener(
                (parent, view, p, id) -> {
                    appOrientation(filtered.get(p));
                    return true;
                });
        search.addTextChangedListener(
                new android.text.TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        String query = s.toString().toLowerCase(Locale.ROOT);
                        filtered.clear();
                        for (AppCatalog.Entry app : all)
                            if (app.name.toLowerCase(Locale.ROOT).contains(query)
                                    || app.component
                                            .getPackageName()
                                            .toLowerCase(Locale.ROOT)
                                            .contains(query)) filtered.add(app);
                        adapter.notifyDataSetChanged();
                    }

                    public void afterTextChanged(android.text.Editable value) {}
                });
        root.requestFocus();
    }

    private void displaySettings() {
        if (session == null) return;
        pad.cancel();
        session.detect();
        if (session.external == null) {
            session.report("Connect an external monitor first");
            return;
        }
        android.view.Display.Mode mode = session.external.getMode();
        int nativeW = mode.getPhysicalWidth(), nativeH = mode.getPhysicalHeight();
        int lowerW = Math.round(nativeW * 2f / 3), lowerH = Math.round(nativeH * 2f / 3);
        LinearLayout root = beginMenu("Resolution and DPI", "display", true);
        settingsDialog
                .getWindow()
                .clearFlags(
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        TextView info = new TextView(this);
        info.setText(session.capabilities());
        info.setTextSize(13);
        root.addView(info);
        String key = session.monitorKey();
        int recommendedW = session.prefs.getInt(key + ".w", nativeW),
                recommendedH = session.prefs.getInt(key + ".h", nativeH),
                recommendedDpi =
                        session.prefs.getInt(
                                key + ".dpi", Geometry.recommendedDpi(Math.min(nativeW, nativeH)));
        int[][] presets = {
            {recommendedW, recommendedH, recommendedDpi},
            {nativeW, nativeH, Geometry.recommendedDpi(Math.min(nativeW, nativeH))},
            {lowerW, lowerH, Geometry.recommendedDpi(Math.min(lowerW, lowerH))}
        };
        String[] names = {"Recommended", "Same as current output • sharp", "Lower rendering load"};
        Spinner preset = new Spinner(this);
        String[] labels = new String[3];
        for (int i = 0; i < 3; i++)
            labels[i] =
                    names[i]
                            + "\n"
                            + presets[i][0]
                            + " × "
                            + presets[i][1]
                            + " • "
                            + presets[i][2]
                            + " UI DPI";
        preset.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        root.addView(preset);
        EditText dimensions = new EditText(this);
        dimensions.setSingleLine();
        dimensions.setHint("Desktop width × height (e.g. 1920x1200)");
        root.addView(dimensions);
        EditText density = new EditText(this);
        density.setSingleLine();
        density.setInputType(2);
        density.setHint("UI DPI: 120–480");
        root.addView(density);
        preset.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                        dimensions.setText(presets[i][0] + "x" + presets[i][1]);
                        density.setText("" + presets[i][2]);
                    }

                    public void onNothingSelected(AdapterView<?> p) {}
                });
        TextView note = new TextView(this);
        note.setText(
                "Different aspect ratios use Fit with bars. Dimensions swap for portrait. UI DPI controls text size, not physical monitor density.");
        root.addView(note);
        section(root, "APPLY");
        settingAction(
                root,
                "Preview resolution",
                () -> "Automatically reverts unless kept",
                () -> {
                    try {
                        String[] values =
                                dimensions
                                        .getText()
                                        .toString()
                                        .toLowerCase(Locale.ROOT)
                                        .replace("\u00d7", "x")
                                        .split("x");
                        if (values.length != 2) throw new IllegalArgumentException();
                        int w = Integer.parseInt(values[0].trim()),
                                h = Integer.parseInt(values[1].trim()),
                                dpi = Integer.parseInt(density.getText().toString());
                        if (w < 480 || h < 480 || w > 4096 || h > 4096 || dpi < 120 || dpi > 480)
                            throw new IllegalArgumentException();
                        session.preview(w, h, dpi);
                        settingsDialog.dismiss();
                    } catch (Exception e) {
                        dimensions.setError("Use dimensions 480-4096 and DPI 120-480");
                    }
                });
        refreshMenu();
    }

    private class Pad extends View {
        private float lastX, lastY, startX, startY, startSpan, total, pinchSpan, anchorX, anchorY;
        private int fingers, kind = 0;
        private boolean multitouch, pinching, swiping;
        private long down, lastFrame;
        private Paint paint = new Paint(3);

        Pad() {
            super(MainActivity.this);
            setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        protected void onDraw(Canvas c) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.rgb(40, 40, 40));
            c.drawRoundRect(1, 1, getWidth() - 1, getHeight() - 1, dp(14), dp(14), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(34, 34, 34));
            for (int x = dp(22); x < getWidth() - dp(12); x += dp(32))
                for (int y = dp(62); y < getHeight() - dp(12); y += dp(32))
                    c.drawCircle(x, y, dp(.65f), paint);
            paint.setTextSize(dp(11));
            paint.setColor(Color.rgb(125, 125, 125));
            c.drawText("Touchpad", dp(16), dp(28), paint);
        }

        private float centerX(MotionEvent e) {
            float x = 0;
            for (int i = 0; i < e.getPointerCount(); i++) x += e.getX(i);
            return x / e.getPointerCount();
        }

        private float centerY(MotionEvent e) {
            float y = 0;
            for (int i = 0; i < e.getPointerCount(); i++) y += e.getY(i);
            return y / e.getPointerCount();
        }

        private float span(MotionEvent e) {
            return (float) Math.hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0));
        }

        private void pinch(int action) {
            float max =
                    Math.min(
                            Math.min(anchorX, session.width() - 1 - anchorX),
                            Math.min(anchorY, session.height() - 1 - anchorY));
            float half = Geometry.clamp(pinchSpan / 2, 1, Math.max(1, max));
            session.touch(
                    action,
                    new float[] {anchorX - half, anchorX + half},
                    new float[] {anchorY, anchorY});
        }

        private void endPinch() {
            if (pinching && session != null) {
                pinch(MotionEvent.ACTION_POINTER_UP | (1 << 8));
                session.touch(
                        MotionEvent.ACTION_UP,
                        new float[] {
                            Geometry.clamp(
                                    anchorX
                                            - Math.min(
                                                    pinchSpan / 2,
                                                    Math.min(
                                                            Math.min(
                                                                    anchorX,
                                                                    session.width() - 1 - anchorX),
                                                            Math.min(
                                                                    anchorY,
                                                                    session.height()
                                                                            - 1
                                                                            - anchorY))),
                                    0,
                                    session.width() - 1)
                        },
                        new float[] {anchorY});
            }
            pinching = false;
        }

        void cancel() {
            if ((pinching || swiping) && session != null) session.cancelPhoneInput();
            pinching = false;
            swiping = false;
            fingers = 0;
            kind = 0;
            multitouch = true;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (session == null || session.id < 0 || session.busy) {
                if (session != null && e.getActionMasked() == MotionEvent.ACTION_DOWN)
                    session.log(
                            "Touchpad ignored: desktop=" + session.id + " busy=" + session.busy);
                cancel();
                return true;
            }
            int action = e.getActionMasked();
            float x = centerX(e), y = centerY(e);
            if (action == MotionEvent.ACTION_DOWN) {
                session.log("Touchpad DOWN: 1 finger");
                session.showTouchpadPointer();
                cancel();
                multitouch = false;
                lastX = startX = x;
                lastY = startY = y;
                fingers = 1;
                total = 0;
                down = e.getEventTime();
            } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
                endPinch();
                if (swiping) {
                    session.cancelPhoneInput();
                    swiping = false;
                }
                multitouch = true;
                fingers = e.getPointerCount();
                session.log(
                        "Touchpad pointers: "
                                + fingers
                                + "; three-finger Back requires right swipe > "
                                + dp(60)
                                + " px");
                kind = 0;
                lastX = startX = x;
                lastY = startY = y;
                if (fingers == 2) startSpan = span(e);
            } else if (action == MotionEvent.ACTION_MOVE) {
                float dx = x - lastX, dy = y - lastY;
                total += Math.abs(dx) + Math.abs(dy);
                lastX = x;
                lastY = y;
                int count = e.getPointerCount();
                if (count == 1 && !multitouch)
                    session.move(
                            dx * session.prefs.getFloat("speed", 1.6f),
                            dy * session.prefs.getFloat("speed", 1.6f));
                else if (count == 2 && fingers == 2) {
                    float currentSpan = span(e);
                    if (kind == 0) {
                        if (Math.abs(currentSpan - startSpan) > dp(12)
                                && Math.abs(currentSpan - startSpan)
                                        > Math.hypot(x - startX, y - startY) * 1.2) {
                            kind = 2;
                            anchorX = Geometry.clamp(session.cx, 2, session.width() - 3);
                            anchorY = Geometry.clamp(session.cy, 2, session.height() - 3);
                            pinchSpan =
                                    Math.max(
                                            2,
                                            Math.min(
                                                    Math.min(session.width(), session.height())
                                                            * .22f,
                                                    2
                                                            * Math.min(
                                                                    Math.min(
                                                                            anchorX,
                                                                            session.width()
                                                                                    - 1
                                                                                    - anchorX),
                                                                    Math.min(
                                                                            anchorY,
                                                                            session.height()
                                                                                    - 1
                                                                                    - anchorY))));
                            session.touch(
                                    0,
                                    new float[] {anchorX - pinchSpan / 2},
                                    new float[] {anchorY});
                            pinch(MotionEvent.ACTION_POINTER_DOWN | (1 << 8));
                            pinching = true;
                            session.log("Gesture recognized: pinch zoom");
                        } else if (Math.hypot(x - startX, y - startY) > dp(10)) {
                            kind = 1;
                            session.log(
                                    "Gesture recognized: two-finger "
                                            + (session.swipeScrolling() ? "swipe" : "wheel scroll")
                                            + " at "
                                            + Math.round(session.scrollSpeed() * 100)
                                            + "%");
                        }
                    }
                    if (e.getEventTime() - lastFrame >= 16) {
                        lastFrame = e.getEventTime();
                        if (kind == 2) {
                            pinchSpan =
                                    Math.min(session.width(), session.height())
                                            * .22f
                                            * currentSpan
                                            / Math.max(1, startSpan);
                            pinch(MotionEvent.ACTION_MOVE);
                        } else if (kind == 1) {
                            float direction =
                                    session.prefs.getBoolean("reverseScroll", false) ? -1 : 1;
                            if (session.swipeScrolling()) {
                                if (!swiping) {
                                    anchorX = session.cx;
                                    anchorY = session.cy;
                                    session.touch(0, new float[] {anchorX}, new float[] {anchorY});
                                    swiping = true;
                                }
                                anchorX =
                                        Geometry.clamp(
                                                anchorX
                                                        + dx
                                                                * 2
                                                                * direction
                                                                * session.scrollSpeed(),
                                                1,
                                                session.width() - 2);
                                anchorY =
                                        Geometry.clamp(
                                                anchorY
                                                        + dy
                                                                * 2
                                                                * direction
                                                                * session.scrollSpeed(),
                                                1,
                                                session.height() - 2);
                                session.touch(2, new float[] {anchorX}, new float[] {anchorY});
                            } else session.wheel(-dx / 35 * direction, -dy / 35 * direction);
                        }
                    }
                }
            } else if (action == MotionEvent.ACTION_POINTER_UP) {
                if (fingers >= 3) {
                    boolean recognized =
                            x - startX > dp(60) && Math.abs(y - startY) < Math.abs(x - startX);
                    session.log(
                            "Three-finger Back "
                                    + (recognized ? "recognized" : "rejected")
                                    + ": dx="
                                    + Math.round(x - startX)
                                    + " dy="
                                    + Math.round(y - startY)
                                    + " threshold="
                                    + dp(60)
                                    + " px rightward");
                    if (recognized) session.key(KeyEvent.KEYCODE_BACK);
                    fingers = 0;
                }
                endPinch();
                if (swiping) {
                    session.touch(1, new float[] {anchorX}, new float[] {anchorY});
                    swiping = false;
                }
                fingers = 0;
            } else if (action == MotionEvent.ACTION_UP) {
                endPinch();
                if (swiping) {
                    session.touch(1, new float[] {anchorX}, new float[] {anchorY});
                    swiping = false;
                }
                if (!multitouch && total < dp(10) && e.getEventTime() - down < 500) {
                    session.log("Gesture recognized: tap");
                    session.click();
                }
                fingers = 0;
            } else if (action == MotionEvent.ACTION_CANCEL) {
                session.log("Touchpad gesture canceled by Android");
                cancel();
            }
            return true;
        }
    }
}
