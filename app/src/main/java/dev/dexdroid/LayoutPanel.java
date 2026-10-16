package dev.dexdroid;

import android.content.Context;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.*;

/** The same current-state menu tree on the phone and monitor. */
final class LayoutPanel {
    private final SessionService session;
    private final ControllerStyle style;
    private final List<Runnable> updates = new ArrayList<>();
    private boolean refreshing, dragging;

    LayoutPanel(
            SessionService session, Context context, LinearLayout parent, IntConsumer openApps) {
        this.session = session;
        style = new ControllerStyle(context);
        row(
                parent,
                R.drawable.ic_layout,
                "Split view",
                "layout-toggle",
                () ->
                        session.splitEnabled()
                                ? "On · End keeps pane 1 and closes pane 2"
                                : "Off · Use two apps together",
                () -> true,
                () -> {
                    if (session.split == null) session.enableSplit();
                    else session.split.end();
                });
        LinearLayout targets = new LinearLayout(context);
        targets.setTag("layout-targets");
        parent.addView(targets, new LinearLayout.LayoutParams(-1, -2));
        for (int i = 0; i < 2; i++) {
            final int position = i;
            Button target =
                    style.button(
                            targets,
                            "Open app",
                            () -> {
                                if (session.busy || session.awaitingConfirmation()) return;
                                SplitLayout state =
                                        session.split == null
                                                ? new SplitLayout()
                                                : session.split.state();
                                openApps.accept(state.enabled ? state.paneAtPosition(position) : 0);
                            });
            target.setTag("layout-target-" + i);
            style.buttonIcon(target, R.drawable.ic_apps, Color.WHITE);
            updates.add(
                    () -> {
                        SplitLayout state =
                                session.split == null ? new SplitLayout() : session.split.state();
                        target.setVisibility(
                                position == 0 || state.enabled ? View.VISIBLE : View.GONE);
                        target.setText(state.openLabel(position));
                        target.setContentDescription(state.openLabel(position));
                        target.setEnabled(
                                session.id >= 0
                                        && !session.busy
                                        && !session.awaitingConfirmation());
                        targets.setOrientation(
                                state.vertical && state.enabled
                                        ? LinearLayout.VERTICAL
                                        : LinearLayout.HORIZONTAL);
                        LinearLayout.LayoutParams lp =
                                new LinearLayout.LayoutParams(
                                        state.vertical && state.enabled ? -1 : 0,
                                        style.dp(72),
                                        state.vertical && state.enabled ? 0 : 1);
                        lp.setMargins(style.dp(3), style.dp(4), style.dp(3), style.dp(4));
                        LinearLayout.LayoutParams old =
                                (LinearLayout.LayoutParams) target.getLayoutParams();
                        if (old.width != lp.width
                                || old.height != lp.height
                                || old.weight != lp.weight) target.setLayoutParams(lp);
                    });
        }
        LinearLayout splitControls = new LinearLayout(context);
        splitControls.setOrientation(LinearLayout.VERTICAL);
        parent.addView(splitControls);
        updates.add(
                () ->
                        splitControls.setVisibility(
                                session.splitEnabled() ? View.VISIBLE : View.GONE));
        parent = splitControls;
        row(
                parent,
                R.drawable.ic_rotation,
                "Direction",
                "layout-direction",
                () ->
                        session.split != null && session.split.state().vertical
                                ? "Top / bottom"
                                : "Left / right",
                session::splitEnabled,
                () -> session.split.direction(!session.split.state().vertical));
        TextView ratio = style.label("Split ratio", 14);
        parent.addView(ratio);
        SeekBar slider = new SeekBar(context);
        slider.setTag("layout-ratio");
        slider.setMin(25);
        slider.setMax(75);
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        slider.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        slider.setContentDescription("Split ratio, 25 to 75 percent");
        parent.addView(slider, new LinearLayout.LayoutParams(-1, style.dp(48)));
        updates.add(
                () -> {
                    int value =
                            session.split == null
                                    ? 50
                                    : Math.round(session.split.state().ratio * 100);
                    if (!dragging) slider.setProgress(value);
                    slider.setEnabled(
                            session.split != null
                                    && !session.busy
                                    && !session.awaitingConfirmation());
                    if (!dragging)
                        ratio.setText(context.getString(R.string.split_ratio, value, 100 - value));
                });
        slider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar view, int progress, boolean user) {
                        if (user) {
                            ratio.setText(
                                    context.getString(
                                            R.string.split_ratio, progress, 100 - progress));
                            if (!dragging && session.split != null)
                                session.split.ratio(progress / 100f);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar view) {
                        dragging = true;
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar view) {
                        dragging = false;
                        if (session.split != null) session.split.ratio(view.getProgress() / 100f);
                        refresh();
                    }
                });
        LinearLayout presets = new LinearLayout(context);
        parent.addView(presets);
        for (float value : new float[] {1f / 3, .5f, 2f / 3}) {
            Button button =
                    style.button(
                            presets,
                            value == .5f ? "1:1" : value < .5f ? "1:2" : "2:1",
                            () -> {
                                if (session.split != null) session.split.ratio(value);
                            });
            button.setTag(
                    value == .5f
                            ? "layout-equal"
                            : value < .5f ? "layout-third" : "layout-two-thirds");
            updates.add(
                    () ->
                            button.setEnabled(
                                    session.split != null
                                            && !session.busy
                                            && !session.awaitingConfirmation()));
        }
        row(
                parent,
                R.drawable.ic_swap,
                "Swap panes",
                "layout-swap",
                () -> "Exchange app positions",
                session::splitEnabled,
                () -> session.split.swap());
        row(
                parent,
                R.drawable.ic_fullscreen,
                "Fullscreen / restore",
                "layout-maximize",
                () ->
                        session.split != null && session.split.state().maximized >= 0
                                ? "Restore split view"
                                : "Expand selected pane",
                session::splitEnabled,
                () -> session.split.maximize());
        row(
                parent,
                R.drawable.ic_close,
                "Close app",
                "layout-close",
                () -> "Clear selected pane; keep the other app",
                session::splitEnabled,
                () -> session.split.closeApp());
        TextView hint =
                style.label(
                        "Workspace orientation applies to both panes. Saved single-view app orientations are retained. Apps may restart when resized.",
                        12);
        hint.setTextColor(Color.rgb(155, 155, 155));
        hint.setPadding(0, style.dp(8), 0, style.dp(8));
        parent.addView(hint);
        refresh();
    }

    private void row(
            LinearLayout parent,
            int icon,
            String title,
            String tag,
            Supplier<String> current,
            BooleanSupplier available,
            Runnable action) {
        LinearLayout row = style.settingRow(parent);
        row.setTag(tag);
        ImageView image = new ImageView(style.context);
        image.setImageResource(icon);
        row.addView(image, new LinearLayout.LayoutParams(style.dp(24), style.dp(24)));
        LinearLayout labels = new LinearLayout(style.context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(style.dp(12), 0, 0, 0);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        labels.addView(style.label(title, 14));
        TextView value = style.label("", 12);
        value.setTextColor(Color.rgb(155, 155, 155));
        labels.addView(value);
        updates.add(
                () -> {
                    value.setText(current.get());
                    row.setContentDescription(title + ", " + current.get());
                    boolean enabled =
                            available.getAsBoolean()
                                    && !session.busy
                                    && !session.awaitingConfirmation();
                    row.setEnabled(enabled);
                    row.setAlpha(enabled ? 1 : .45f);
                });
        row.setOnClickListener(
                view -> {
                    if (!refreshing
                            && available.getAsBoolean()
                            && !session.busy
                            && !session.awaitingConfirmation()) {
                        action.run();
                        refresh();
                    }
                });
    }

    void refresh() {
        refreshing = true;
        try {
            for (Runnable update : updates) update.run();
        } finally {
            refreshing = false;
        }
    }
}
