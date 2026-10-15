package dev.dexdroid;

import android.content.Context;
import android.graphics.Color;
import android.text.InputFilter;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One profile-management view shared by the portrait phone and rotated monitor shell. */
@android.annotation.SuppressLint("ViewConstructor")
final class MonitorProfilesPanel extends LinearLayout {
    private final SessionService session;
    private final ControllerStyle style;
    private final boolean monitor;
    private final List<View> runningActions = new ArrayList<>(), editActions = new ArrayList<>();
    private EditText name;
    private TextView status, message;
    private String snapshotIdentity;
    private Button keep, revert;
    private View forget;
    private String selectedId, deleteArmed;
    private Map<String, String> snapshot;
    private boolean creating;

    MonitorProfilesPanel(Context context, SessionService session, boolean monitor) {
        super(context);
        this.session = session;
        this.monitor = monitor;
        style = new ControllerStyle(context);
        setTag("profiles-panel");
        setOrientation(VERTICAL);
        setForceDarkAllowed(false);
        creating = session.monitorProfiles().all().isEmpty();
        render(null);
    }

    private TextView text(String value, String tag) {
        TextView view = style.label(value, 12);
        view.setTag(tag);
        view.setTextColor(Color.LTGRAY);
        view.setPadding(0, style.dp(6), 0, style.dp(6));
        addView(view);
        return view;
    }

    private View action(
            String title, String tag, int icon, boolean needsDesktop, Runnable command) {
        LinearLayout row = style.settingRow(this);
        ImageView image = new ImageView(getContext());
        image.setImageResource(icon);
        image.setColorFilter(Color.WHITE);
        row.addView(image, new LayoutParams(style.dp(24), style.dp(24)));
        TextView label = style.label(title, 14);
        label.setPadding(style.dp(12), 0, 0, 0);
        row.addView(label, new LayoutParams(0, -2, 1));
        row.setTag(tag);
        row.setContentDescription(title);
        row.setFocusable(true);
        row.setOnClickListener(v -> command.run());
        (needsDesktop ? runningActions : editActions).add(row);
        return row;
    }

    private String nextName() {
        List<MonitorProfiles.Profile> profiles = session.monitorProfiles().all();
        for (int number = 1; ; number++) {
            String proposed = "Profile " + number;
            if (profiles.stream().noneMatch(profile -> profile.name.equalsIgnoreCase(proposed)))
                return proposed;
        }
    }

    private void render(String draft) {
        removeAllViews();
        name = null;
        forget = null;
        runningActions.clear();
        editActions.clear();
        snapshot = new MonitorProfilePreferences(session.prefs).read();
        snapshotIdentity = session.monitorIdentity();
        MonitorProfiles.Profile selected = session.monitorProfiles().find(selectedId);
        if (selected == null) selectedId = null;
        status = text("", "profiles-status");
        text(session.monitorMatchingDescription(), "profiles-matching");
        message = text("", "profiles-message");
        LinearLayout confirmation = new LinearLayout(getContext());
        keep = style.button(confirmation, "Keep", () -> session.confirm(true));
        keep.setTag("profiles-keep");
        revert = style.button(confirmation, "Revert", () -> session.confirm(false));
        revert.setTag("profiles-revert");
        addView(confirmation);
        if (selected == null && !creating) {
            profileList();
            refresh();
            return;
        }
        name = new EditText(getContext());
        style.search(name);
        name.setTag("profile-name");
        name.setHint("Profile name");
        name.setContentDescription("Profile name");
        name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(48)});
        name.setImeOptions(
                android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                        | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        name.setText(draft != null ? draft : selected == null ? nextName() : selected.name);
        addView(name, new LayoutParams(-1, -2));
        if (monitor) {
            name.setShowSoftInputOnFocus(false);
            EditText editor = name;
            MonitorSearchKeyboard keyboard =
                    new MonitorSearchKeyboard(getContext(), editor, editor::clearFocus);
            keyboard.setTag("profile-keyboard");
            addView(keyboard, new LayoutParams(-1, -2));
            name.setOnClickListener(v -> keyboard.showWithin(style.dp(240)));
            name.setOnFocusChangeListener(
                    (v, focused) -> {
                        if (focused) keyboard.showWithin(style.dp(240));
                    });
        }
        if (selected == null) {
            action(
                    "Save current as profile",
                    "profile-create",
                    R.drawable.ic_save,
                    true,
                    () -> {
                        String id = session.saveMonitorProfile(name.getText().toString());
                        if (id != null) {
                            selectedId = id;
                            creating = false;
                            render(null);
                        } else
                            name.setError("Check the name and finish any pending display change");
                    });
        } else {
            String id = selected.id;
            text(selected.configuration.summary(), "profile-configuration");
            action(
                    "Apply profile",
                    "profile-apply",
                    R.drawable.ic_rotation,
                    true,
                    () -> session.applyMonitorProfile(id));
            action(
                    "Rename profile",
                    "profile-rename",
                    R.drawable.ic_settings,
                    false,
                    () -> {
                        session.renameMonitorProfile(id, name.getText().toString());
                        deleteArmed = null;
                        render(null);
                    });
            action(
                    "Update from current",
                    "profile-update",
                    R.drawable.ic_save,
                    true,
                    () -> {
                        session.updateMonitorProfile(id);
                        deleteArmed = null;
                        render(null);
                    });
            action(
                    id.equals(deleteArmed) ? "Confirm delete profile" : "Delete profile",
                    "profile-delete",
                    R.drawable.ic_close,
                    false,
                    () -> {
                        if (id.equals(deleteArmed)) {
                            session.deleteMonitorProfile(id);
                            selectedId = null;
                            creating = false;
                            deleteArmed = null;
                        } else deleteArmed = id;
                        render(null);
                    });
        }
        action(
                "All profiles",
                "profile-list",
                R.drawable.ic_apps,
                false,
                () -> {
                    selectedId = null;
                    creating = false;
                    deleteArmed = null;
                    render(null);
                });
        refresh();
    }

    private void select(String id, boolean create) {
        selectedId = id;
        creating = create;
        deleteArmed = null;
        render(null);
        post(
                () -> {
                    android.view.ViewParent parent = getParent();
                    while (parent instanceof View) {
                        if (parent instanceof android.widget.ScrollView) {
                            ((android.widget.ScrollView) parent).fullScroll(View.FOCUS_UP);
                            break;
                        }
                        parent = parent.getParent();
                    }
                });
    }

    private void profileList() {
        action("New profile", "profile-new", R.drawable.ic_save, false, () -> select(null, true));
        List<MonitorProfiles.Profile> profiles = session.monitorProfiles().all();
        if (profiles.isEmpty())
            text(
                    "No profiles yet. Configure the desktop, then save its current settings.",
                    "profiles-empty");
        MonitorProfiles.Profile bound = session.monitorProfiles().bound(session.monitorIdentity());
        for (MonitorProfiles.Profile profile : profiles) {
            LinearLayout row = style.settingRow(this);
            row.setTag("profile-row-" + profile.id);
            row.setSelected(bound != null && bound.id.equals(profile.id));
            TextView label =
                    style.label(
                            profile.name
                                    + "\n"
                                    + profile.configuration.summary()
                                    + (bound != null && bound.id.equals(profile.id)
                                            ? "\nRemembered for this monitor"
                                            : ""),
                            14);
            row.addView(label, new LayoutParams(0, -2, 1));
            ImageButton edit =
                    style.iconAction(
                            R.drawable.ic_settings,
                            "Edit " + profile.name,
                            () -> select(profile.id, false));
            row.addView(edit, new LayoutParams(style.dp(48), style.dp(48)));
            row.setOnClickListener(v -> select(profile.id, false));
            row.setContentDescription(profile.name + ", " + profile.configuration.summary());
        }
        forget =
                action(
                        "Forget this monitor",
                        "profile-forget",
                        R.drawable.ic_close,
                        false,
                        session::resetMonitor);
    }

    void refresh() {
        Map<String, String> current = new MonitorProfilePreferences(session.prefs).read();
        if (!current.equals(snapshot)
                || !java.util.Objects.equals(snapshotIdentity, session.monitorIdentity())) {
            boolean editing = name != null && name.hasFocus();
            String draft = editing ? name.getText().toString() : null;
            render(draft);
            if (editing && name != null) name.requestFocus();
            return;
        }
        status.setText(
                getContext()
                        .getString(
                                R.string.monitor_profile_status, session.monitorProfileStatus()));
        message.setText(session.status.split("\n", 2)[0]);
        boolean pending = session.awaitingConfirmation();
        keep.setVisibility(pending ? View.VISIBLE : View.GONE);
        revert.setVisibility(pending ? View.VISIBLE : View.GONE);
        keep.setEnabled(pending && !session.busy);
        revert.setEnabled(pending);
        boolean mutable = !session.busy && !pending;
        for (View button : editActions) button.setEnabled(mutable);
        for (View button : runningActions)
            button.setEnabled(mutable && session.external != null && session.id >= 0);
        if (forget != null) forget.setEnabled(mutable && session.external != null);
        if (name != null) name.setEnabled(mutable);
        for (View action : editActions) action.setAlpha(action.isEnabled() ? 1f : .45f);
        for (View action : runningActions) action.setAlpha(action.isEnabled() ? 1f : .45f);
    }
}
