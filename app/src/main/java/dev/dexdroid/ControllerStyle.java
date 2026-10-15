package dev.dexdroid;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.function.Supplier;

/** Shared visual contract for the phone controller and rotated monitor menus. */
final class ControllerStyle {
    final Context context;

    ControllerStyle(Context context) {
        this.context = context;
    }

    int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    TextView label(String text, int size) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        return view;
    }

    GradientDrawable surface(int shade, int radius, int border) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(shade, shade, shade));
        drawable.setCornerRadius(dp(radius));
        if (border >= 0) drawable.setStroke(dp(1), Color.rgb(border, border, border));
        return drawable;
    }

    Drawable ripple(Drawable background) {
        return new RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.rgb(80, 80, 80)),
                background,
                null);
    }

    ImageButton iconAction(int icon, String name, Runnable action) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(icon);
        button.setBackground(ripple(surface(8, 24, -1)));
        button.setContentDescription(name);
        button.setTooltipText(name);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    Button button(LinearLayout row, String text, Runnable action) {
        Button b = new Button(context);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(2), dp(6), dp(2), dp(6));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setBackground(ripple(surface(20, 10, 45)));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, dp(62), 1);
        layout.setMargins(dp(3), dp(4), dp(3), dp(4));
        row.addView(b, layout);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    void buttonIcon(Button b, int icon, int color) {
        Drawable drawable = context.getDrawable(icon).mutate();
        drawable.setTint(color);
        drawable.setBounds(0, 0, dp(20), dp(20));
        b.setCompoundDrawables(null, drawable, null, null);
        b.setCompoundDrawablePadding(dp(5));
    }

    Button iconButton(LinearLayout row, int icon, String name, Runnable action) {
        Button b = button(row, name, action);
        b.setTextSize(11);
        buttonIcon(b, icon, Color.WHITE);
        b.setContentDescription(name);
        b.setTooltipText(name);
        return b;
    }

    // Shared Settings surfaces for phone and monitor submenus.
    void menuPanel(LinearLayout panel) {
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(12), dp(18), dp(18));
        panel.setBackground(surface(18, 22, 45));
        panel.setTag("settings-style-panel");
        panel.setForceDarkAllowed(false);
    }

    void card(TextView view) {
        view.setTextColor(Color.WHITE);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setMinHeight(dp(62));
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        view.setBackground(ripple(surface(18, 8, 40)));
    }

    LinearLayout settingRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setMinimumHeight(dp(62));
        row.setBackground(ripple(surface(18, 8, 40)));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.setMargins(0, dp(3), 0, dp(3));
        parent.addView(row, layout);
        return row;
    }

    void appCard(TextView view, Drawable icon) {
        card(view);
        String label = view.getText().toString();
        int split = label.indexOf('\n');
        if (split >= 0) {
            android.text.SpannableString styled = new android.text.SpannableString(label);
            styled.setSpan(
                    new android.text.style.ForegroundColorSpan(Color.rgb(155, 155, 155)),
                    split + 1,
                    label.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(
                    new android.text.style.AbsoluteSizeSpan(12, true),
                    split + 1,
                    label.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            view.setText(styled);
        }
        view.setMinHeight(dp(72));
        icon.setBounds(0, 0, dp(36), dp(36));
        view.setCompoundDrawables(icon, null, null, null);
        view.setCompoundDrawablePadding(dp(12));
    }

    void appList(ListView list) {
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(6));
    }

    void search(EditText editor) {
        card(editor);
        editor.setMinHeight(dp(50));
        editor.setSingleLine();
        editor.setHintTextColor(Color.rgb(155, 155, 155));
        editor.setImeOptions(
                android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                        | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    }

    RadioButton choice(LinearLayout parent, String label, String tag, int id) {
        RadioButton choice = new RadioButton(context);
        choice.setId(id);
        choice.setTag(tag);
        card(choice);
        choice.setText(label);
        choice.setButtonTintList(
                new android.content.res.ColorStateList(
                        new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                        new int[] {Color.WHITE, Color.GRAY}));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.setMargins(0, dp(3), 0, dp(3));
        parent.addView(choice, layout);
        return choice;
    }

    static class StatusCard extends LinearLayout {
        final TextView monitor, operation, appSettings;
        final LinearLayout confirmation;
        private final Supplier<SessionService> source;
        private final String[] rotations = {
            "Landscape 0°", "Portrait 90°", "Landscape 180°", "Portrait 270°"
        };

        StatusCard(Context context, Supplier<SessionService> source) {
            super(context);
            this.source = source;
            ControllerStyle style = new ControllerStyle(context);
            setTag("controller-status-card");
            setOrientation(VERTICAL);
            setPadding(style.dp(14), style.dp(12), style.dp(14), style.dp(12));
            setBackground(style.surface(18, 12, 40));
            monitor = style.label("Monitor · Checking…", 14);
            monitor.setTypeface(null, Typeface.BOLD);
            addView(monitor);
            operation = style.label("Connecting controller…", 12);
            operation.setTextColor(Color.rgb(170, 170, 170));
            operation.setMaxLines(3);
            operation.setPadding(0, style.dp(5), 0, style.dp(5));
            addView(operation);
            appSettings = style.label("", 12);
            appSettings.setTextColor(Color.rgb(170, 170, 170));
            appSettings.setTag("monitor-status");
            addView(appSettings);
            confirmation = new LinearLayout(context);
            addView(confirmation, new LayoutParams(-1, -2));
            style.button(
                    confirmation,
                    "Keep",
                    () -> {
                        if (source.get() != null) source.get().confirm(true);
                    });
            style.button(
                    confirmation,
                    "Revert",
                    () -> {
                        if (source.get() != null) source.get().confirm(false);
                    });
            confirmation.getChildAt(0).setTag("monitor-keep");
            confirmation.getChildAt(1).setTag("monitor-revert");
            confirmation.setVisibility(GONE);
        }

        void refresh() {
            SessionService s = source.get();
            if (s == null) {
                monitor.setText("Monitor · Checking…");
                operation.setText("Controller disconnected");
                appSettings.setText("");
                return;
            }
            String app = "No app selected";
            if (!s.activePackage.isEmpty())
                try {
                    app =
                            s.getPackageManager()
                                    .getApplicationLabel(
                                            s.getPackageManager()
                                                    .getApplicationInfo(s.activePackage, 0))
                                    .toString();
                } catch (Exception e) {
                    app = s.activePackage;
                }
            boolean ready = s.id >= 0 && !s.busy;
            String desktop =
                    s.busy
                            ? "Updating"
                            : ready ? "Ready" : s.desktopRequested() ? "Waiting" : "Stopped";
            monitor.setText(
                    s.external == null
                            ? "Monitor · Disconnected"
                            : "Monitor · Connected — " + s.external.getName());
            String summary = s.status.split("\\n", 2)[0];
            if (!s.activePackage.isEmpty()) summary = summary.replace(s.activePackage, app);
            if (summary.startsWith("Desktop ready") || summary.equals("Shizuku connected"))
                summary = "";
            operation.setText(
                    "Shizuku · "
                            + (s.needsRecovery() ? "Unavailable" : "Connected")
                            + "   Desktop · "
                            + desktop
                            + (summary.isEmpty() ? "" : "\n" + summary));
            String saved =
                    s.prefs.contains("app." + s.activePackage)
                            ? rotations[
                                    Math.max(
                                            0,
                                            Math.min(
                                                    3,
                                                    s.prefs.getInt("app." + s.activePackage, 0)))]
                            : "Default";
            float speed = s.scrollSpeed();
            String name = speed < .35f ? "Slow" : speed < .75f ? "Normal" : "Fast";
            appSettings.setText(
                    "App · "
                            + app
                            + "\nScroll · "
                            + (s.swipeScrolling() ? "Swipe" : "Wheel")
                            + " · "
                            + name
                            + " "
                            + Math.round(speed * 100)
                            + "%   Touch · "
                            + (s.monitorTouchEnabled() ? "On" : "Off")
                            + "\nRotation · "
                            + rotations[s.rotation]
                            + "   Saved · "
                            + saved
                            + (s.id >= 0
                                    ? "\n"
                                            + s.width()
                                            + " × "
                                            + s.height()
                                            + " · "
                                            + s.dpi
                                            + " UI DPI"
                                    : ""));
            confirmation.setVisibility(s.awaitingConfirmation() ? VISIBLE : GONE);
        }
    }
}
