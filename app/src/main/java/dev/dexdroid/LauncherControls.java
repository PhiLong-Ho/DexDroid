package dev.dexdroid;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/** Shared launcher mode/actions; each menu owns a session-scoped choice, never a display ID. */
final class LauncherControls {
    private final SessionService session;
    private final ControllerStyle style;
    private SessionService.AppSelection choice;
    private SessionService.AppSelection modeOrigin;
    private SplitWorkspace modeWorkspace;
    private int requestedMode;
    private final List<Button> modes = new ArrayList<>();
    private boolean changingMode, closed;
    private int lastMode = -1;
    private int revision;
    private Runnable changed = () -> {};
    private LinearLayout container;

    LauncherControls(SessionService session, Context context, int pane) {
        this.session = session;
        style = new ControllerStyle(context);
        choice = session.beginAppSelection(pane, false);
    }

    boolean current() {
        return !closed && session.currentAppSelection(choice);
    }

    void close() {
        closed = true;
        session.finishAppSelection(choice);
    }

    void addModes(LinearLayout parent) {
        container = parent;
        modes.clear();
        LinearLayout row = new LinearLayout(style.context);
        row.setTag("launcher-modes");
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        String[] names = {"Single", "Left/right", "Up/down"};
        for (int i = 0; i < names.length; i++) {
            final int mode = i;
            Button button = style.button(row, names[i], () -> mode(mode));
            button.setTag("launcher-mode-" + i);
            ((LinearLayout.LayoutParams) button.getLayoutParams()).height = style.dp(48);
            modes.add(button);
        }
    }

    void onChanged(Runnable callback) {
        changed = callback;
        refresh();
    }

    private int mode() {
        return session.split == null ? 0 : session.split.state().vertical ? 2 : 1;
    }

    private void mode(int requested) {
        if (!current()
                || session.busy
                || session.awaitingConfirmation()
                || session.id < 0
                || (requested == mode()
                        && (session.split == null || session.split.state().maximized < 0))) return;
        revision++;
        modeOrigin = choice;
        requestedMode = requested;
        session.finishAppSelection(choice);
        choice = null;
        if (requested == 0) session.split.end();
        else if (session.split == null) session.enableSplit(requested == 2);
        else session.split.direction(requested == 2);
        modeWorkspace = requested == 0 ? null : session.split;
        changingMode = true;
        refresh();
    }

    void refresh() {
        if (closed) return;
        boolean completed = false;
        if (changingMode && !session.busy) {
            changingMode = false;
            if (session.split == modeWorkspace
                    && mode() == requestedMode
                    && (session.split == null || session.split.state().maximized < 0))
                choice = session.resumeAppSelection(modeOrigin);
            modeOrigin = null;
            completed = true;
        }
        int mode = mode();
        for (int i = 0; i < modes.size(); i++) {
            Button button = modes.get(i);
            boolean selected = i == mode;
            if (button.isSelected() != selected) {
                button.setTextColor(selected ? Color.BLACK : Color.WHITE);
                button.setBackground(style.ripple(style.surface(selected ? 240 : 20, 10, 45)));
            }
            button.setSelected(selected);
            button.setContentDescription(button.getText() + (selected ? ", selected" : ", off"));
            button.setEnabled(
                    current()
                            && !session.busy
                            && !session.awaitingConfirmation()
                            && session.id >= 0);
        }
        if (mode != lastMode || completed) {
            lastMode = mode;
            changed.run();
            if (container != null) {
                container.requestLayout();
                container.invalidate();
            }
        }
    }

    private int pane(int position) {
        return session.split == null ? 0 : session.split.state().paneAtPosition(position);
    }

    void preview(int position, boolean pressed) {
        if (!current()) return;
        if (pressed && !session.busy && !session.awaitingConfirmation()) {
            choice = session.beginAppSelection(pane(position), false);
        }
        session.previewAppSelection(choice, pressed);
    }

    boolean launch(ComponentName app, Integer rotation, int position) {
        if (!current() || session.busy || session.awaitingConfirmation()) {
            session.report("App destination changed. Reopen Apps or wait for the display.");
            return false;
        }
        // Accessibility/keyboard clicks also capture an explicit current spatial destination.
        if (choice.pane != pane(position))
            choice = session.beginAppSelection(pane(position), false);
        return session.launchSelection(choice, app, rotation);
    }

    View appRow(AppCatalog.Entry app, String orientation, Runnable success) {
        int rowRevision = revision;
        TextView label = style.label(app.name + "\n" + orientation, 14);
        style.appCard(label, app.icon);
        if (!session.splitEnabled()) {
            label.setTag("app." + app.component.getPackageName());
            return label;
        }
        LinearLayout row = new LinearLayout(style.context);
        row.setTag("app." + app.component.getPackageName());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(style.surface(18, 8, 40));
        label.setBackgroundColor(Color.TRANSPARENT);
        label.setMaxLines(3);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        boolean vertical = session.split.state().vertical;
        for (int i = 0; i < 2; i++) {
            final int position = i;
            String target = vertical ? (i == 0 ? "Up" : "Down") : (i == 0 ? "Left" : "Right");
            Button button =
                    style.button(
                            row,
                            target,
                            () -> {
                                if (rowRevision == revision
                                        && launch(app.component, null, position)) success.run();
                            },
                            new DestinationButton(style.context, position));
            button.setTag("launch." + app.component.getPackageName() + "." + i);
            button.setContentDescription(
                    "Open " + app.name + " " + target.toLowerCase(java.util.Locale.ROOT));
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) button.getLayoutParams();
            lp.width = style.dp(56);
            lp.height = style.dp(56);
            lp.weight = 0;
        }
        return row;
    }

    private final class DestinationButton extends Button {
        private final int position;
        private final int rowRevision;
        private boolean ready;

        DestinationButton(Context context, int position) {
            super(context);
            this.position = position;
            rowRevision = revision;
            ready = true;
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            if (ready && rowRevision == revision) preview(position, pressed);
        }
    }
}
