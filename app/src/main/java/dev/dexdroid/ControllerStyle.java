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

    void versionInfo(LinearLayout parent) {
        TextView version =
                label(
                        "Version "
                                + BuildConfig.VERSION_NAME
                                + " \u00b7 Build "
                                + BuildConfig.VERSION_CODE
                                + (BuildConfig.DEBUG ? " \u00b7 Debug" : ""),
                        12);
        version.setTag("settings-app-version");
        version.setTextColor(Color.rgb(163, 163, 163));
        version.setPadding(dp(4), dp(4), dp(4), dp(8));
        parent.addView(version);
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

    /** Menu chrome stays visually plain without shrinking its independent touch target. */
    ImageButton menuIconAction(int icon, String name, Runnable action) {
        ImageButton button = iconAction(icon, name, action);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setImageTintList(
                new android.content.res.ColorStateList(
                        new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
                        new int[] {Color.GRAY, Color.WHITE}));
        button.setBackground(
                new RippleDrawable(
                        android.content.res.ColorStateList.valueOf(Color.rgb(80, 80, 80)),
                        new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT),
                        surface(255, 12, -1)));
        return button;
    }

    void menuHeading(TextView heading) {
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(8), 0, dp(8), 0);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
    }

    Button button(LinearLayout row, String text, Runnable action) {
        return button(row, text, action, new Button(context));
    }

    Button button(LinearLayout row, String text, Runnable action, Button b) {
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

    void restoreScroll(ScrollView scroll, int position) {
        if (scroll.isLaidOut() && !scroll.isLayoutRequested()) {
            scroll.scrollTo(0, position);
        } else {
            scroll.addOnLayoutChangeListener(
                    new View.OnLayoutChangeListener() {
                        @Override
                        public void onLayoutChange(
                                View v,
                                int l,
                                int t,
                                int r,
                                int b,
                                int oldL,
                                int oldT,
                                int oldR,
                                int oldB) {
                            scroll.removeOnLayoutChangeListener(this);
                            scroll.scrollTo(0, position);
                        }
                    });
        }
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
        if (icon.getConstantState() != null)
            icon = icon.getConstantState().newDrawable(context.getResources()).mutate();
        icon.setBounds(0, 0, dp(36), dp(36));
        view.setCompoundDrawables(icon, null, null, null);
        view.setCompoundDrawablePadding(dp(12));
    }

    LinearLayout protectedNotice(String prefix, Runnable help, Runnable dismiss) {
        LinearLayout row = new LinearLayout(context);
        row.setTag(prefix + "-protected-notice");
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(4), dp(4), dp(4));
        row.setBackground(surface(18, 12, 40));
        TextView label = label("Protected screen", 14);
        label.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton info = iconAction(R.drawable.ic_info, "Protected screen help", help);
        info.setTag(prefix + "-protected-help");
        row.addView(info, new LinearLayout.LayoutParams(dp(48), dp(48)));
        if (dismiss != null) {
            ImageButton close =
                    iconAction(R.drawable.ic_close, "Dismiss protected screen notice", dismiss);
            close.setTag(prefix + "-protected-dismiss");
            row.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        return row;
    }

    final class CatalogStatus extends LinearLayout {
        private final ProgressBar progress;
        private final TextView message;

        CatalogStatus(String tag) {
            super(context);
            setTag(tag);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(0, dp(8), 0, dp(8));
            progress = new ProgressBar(context);
            progress.setIndeterminateTintList(
                    android.content.res.ColorStateList.valueOf(Color.WHITE));
            addView(progress, new LinearLayout.LayoutParams(dp(18), dp(18)));
            message = label("Loading apps\u2026", 12);
            message.setPadding(dp(10), 0, 0, 0);
            message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            addView(message, new LinearLayout.LayoutParams(0, -2, 1));
            setVisibility(GONE);
        }

        void update(AppCatalog.State state, Runnable retry) {
            setVisibility(state.loading || !state.error.isEmpty() ? VISIBLE : GONE);
            progress.setVisibility(state.loading ? VISIBLE : GONE);
            message.setText(
                    state.loading
                            ? (state.apps.isEmpty() ? "Loading apps\u2026" : "Updating apps\u2026")
                            : "Couldn't load apps. Tap to retry.");
            setContentDescription(message.getText());
            setOnClickListener(state.error.isEmpty() ? null : v -> retry.run());
            setClickable(!state.error.isEmpty());
        }
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

    static class StatusCard extends FrameLayout {
        final TextView monitor, operation, appSettings;
        final LinearLayout confirmation;
        private final Supplier<SessionService> source;
        private final LinearLayout protection, details;
        private final LinearLayout expansion;
        private final TextView detailsCue;
        private final Button recovery;
        private final ImageButton desktopAction;
        private final ControllerStyle style;
        private final String[] rotations = {
            "Landscape 0°", "Portrait 90°", "Landscape 180°", "Portrait 270°"
        };

        StatusCard(
                Context context,
                Supplier<SessionService> source,
                Runnable help,
                String prefix,
                Runnable desktop,
                Runnable setup) {
            super(context);
            this.source = source;
            style = new ControllerStyle(context);
            setTag("controller-status-card");
            setBackground(style.surface(18, 12, 40));
            expansion = new LinearLayout(context);
            expansion.setOrientation(LinearLayout.VERTICAL);
            expansion.setPadding(style.dp(14), style.dp(12), style.dp(14), style.dp(12));
            expansion.setTag(prefix + "-status-expand");
            expansion.setFocusable(true);
            expansion.setBackground(style.ripple(new ColorDrawable(Color.TRANSPARENT)));
            addView(expansion, new FrameLayout.LayoutParams(-1, -2));
            monitor = style.label("Monitor · Checking…", 14);
            monitor.setTypeface(null, Typeface.BOLD);
            LinearLayout monitorRow = new LinearLayout(context);
            monitorRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout summary = new LinearLayout(context);
            summary.setOrientation(LinearLayout.VERTICAL);
            summary.addView(monitor, new LinearLayout.LayoutParams(-1, -2));
            detailsCue = style.label("Show details", 12);
            detailsCue.setTextColor(Color.rgb(155, 155, 155));
            detailsCue.setTag(prefix + "-status-cue");
            detailsCue.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            summary.addView(detailsCue, new LinearLayout.LayoutParams(-1, -2));
            monitorRow.addView(summary, new LinearLayout.LayoutParams(0, -2, 1));
            desktopAction = style.iconAction(R.drawable.ic_stop, "Stop desktop", desktop);
            desktopAction.setTag(prefix + "-stop");
            desktopAction.setBackground(
                    new RippleDrawable(
                            android.content.res.ColorStateList.valueOf(Color.rgb(80, 80, 80)),
                            new ColorDrawable(Color.TRANSPARENT),
                            new ColorDrawable(Color.WHITE)));
            monitorRow.addView(
                    desktopAction, new LinearLayout.LayoutParams(style.dp(48), style.dp(48)));
            expansion.setOnClickListener(
                    view -> {
                        SessionService session = source.get();
                        if (session != null) {
                            boolean next = !session.prefs.getBoolean("statusExpanded", false);
                            session.prefs.edit().putBoolean("statusExpanded", next).apply();
                            session.report("Status details " + (next ? "expanded" : "collapsed"));
                        }
                    });
            monitor.setMaxLines(2);
            monitor.setEllipsize(android.text.TextUtils.TruncateAt.END);
            expansion.addView(monitorRow);
            details = new LinearLayout(context);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setTag(prefix + "-status-details");
            details.setVisibility(GONE);
            expansion.addView(details);
            recovery = new Button(context);
            style.button(new LinearLayout(context), "Shizuku setup", setup, recovery);
            recovery.setTag(prefix + "-shizuku-recovery");
            recovery.setVisibility(GONE);
            ((LinearLayout) recovery.getParent()).removeView(recovery);
            expansion.addView(recovery, new LinearLayout.LayoutParams(-1, style.dp(52)));
            operation = style.label("Connecting controller…", 12);
            operation.setTextColor(Color.rgb(170, 170, 170));
            operation.setMaxLines(3);
            operation.setPadding(0, style.dp(5), 0, style.dp(5));
            details.addView(operation);
            appSettings = style.label("", 12);
            appSettings.setTextColor(Color.rgb(170, 170, 170));
            appSettings.setTag("monitor-status");
            LinearLayout appRow = new LinearLayout(context);
            appRow.setGravity(Gravity.TOP);
            appRow.addView(appSettings, new LinearLayout.LayoutParams(0, -2, 1));
            details.addView(appRow);
            protection = style.protectedNotice("status", help, null);
            protection.setVisibility(GONE);
            expansion.addView(protection, new LinearLayout.LayoutParams(-1, -2));
            confirmation = new LinearLayout(context);
            expansion.addView(confirmation, new LinearLayout.LayoutParams(-1, -2));
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
            boolean expanded = s != null && s.prefs.getBoolean("statusExpanded", false);
            details.setVisibility(expanded ? VISIBLE : GONE);
            detailsCue.setText(expanded ? "Hide details" : "Show details");
            expansion.setStateDescription(expanded ? "Expanded" : "Collapsed");
            expansion.setTooltipText(expanded ? "Collapse status" : "Expand status");
            expansion.setEnabled(s != null);
            recovery.setVisibility(
                    s != null && (s.needsRecovery() || s.shizukuState == ShizukuSetup.State.MISSING)
                            ? VISIBLE
                            : GONE);
            if (s != null)
                recovery.setText(
                        s.shizukuState == ShizukuSetup.State.MISSING
                                ? "Install Shizuku"
                                : "Shizuku setup");
            desktopAction.setEnabled(s != null);
            boolean requested = s != null && (s.desktopRequested() || s.id >= 0 || s.busy);
            String action = requested ? "Stop desktop" : "Start desktop";
            desktopAction.setImageResource(requested ? R.drawable.ic_stop : R.drawable.ic_play);
            desktopAction.setContentDescription(action);
            desktopAction.setTooltipText(action);
            desktopAction.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
            desktopAction.setImageAlpha(s == null ? 100 : 255);
            if (s == null) {
                monitor.setText("Monitor · Checking…");
                operation.setText("Controller disconnected");
                appSettings.setText("");
                protection.setVisibility(GONE);
                updateExpansionDescription(expanded);
                return;
            }
            protection.setVisibility(s.protectedContent ? VISIBLE : GONE);
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
                            + "\nProfile · "
                            + s.monitorProfileStatus()
                            + (s.splitEnabled() ? "\nLayout · " + s.layoutStatus() : "")
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
            updateExpansionDescription(expanded);
        }

        private void updateExpansionDescription(boolean expanded) {
            expansion.setContentDescription(
                    (expanded ? "Collapse status. " : "Expand status. ")
                            + monitor.getText()
                            + (expanded
                                    ? ". " + operation.getText() + ". " + appSettings.getText()
                                    : ""));
        }
    }
}
