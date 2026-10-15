package dev.dexdroid;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local keyboard rendered and rotated with monitor search and profile name editors. */
@android.annotation.SuppressLint(
        "ViewConstructor") // Constructed in code with its live editor and Hide callback.
final class MonitorSearchKeyboard extends LinearLayout {
    private final EditText editor;
    private final ControllerStyle style;
    private final Runnable hidden;
    private final List<Button> letters = new ArrayList<>();
    private final List<LinearLayout> rows = new ArrayList<>();
    private Button shiftButton;
    private boolean shifted;

    MonitorSearchKeyboard(Context context, EditText editor, Runnable hidden) {
        super(context);
        this.editor = editor;
        this.hidden = hidden;
        style = new ControllerStyle(context);
        setTag("monitor-search-keyboard");
        setOrientation(VERTICAL);
        setPadding(0, style.dp(4), 0, 0);
        setVisibility(GONE);
        LinearLayout numbers = row();
        for (char value = '1'; value <= '9'; value++)
            character(numbers, String.valueOf(value), String.valueOf(value));
        character(numbers, "0", "0");
        LinearLayout top = row();
        for (char value : "qwertyuiop".toCharArray()) letter(top, value);
        LinearLayout middle = row();
        for (char value : "asdfghjkl".toCharArray()) letter(middle, value);
        character(middle, "-", "hyphen");
        LinearLayout lower = row();
        shiftButton = key(lower, "⇧", "shift", 1.5f, () -> setShifted(!shifted));
        shiftButton.setContentDescription("Shift, off");
        for (char value : "zxcvbnm".toCharArray()) letter(lower, value);
        key(lower, "⌫", "delete", 1.5f, this::delete).setContentDescription("Delete");
        LinearLayout actions = row();
        character(actions, ".", "period");
        character(actions, "_", "underscore");
        key(actions, "Space", "space", 3, this::space);
        key(
                actions,
                "Clear",
                "clear",
                1.5f,
                () -> {
                    editor.setText("");
                    editor.setSelection(0);
                });
        key(
                actions,
                "Hide",
                "hide",
                1.5f,
                () -> {
                    setVisibility(GONE);
                    hidden.run();
                });
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        rows.add(row);
        addView(row, new LayoutParams(-1, style.dp(38)));
        return row;
    }

    private Button key(LinearLayout row, String label, String name, float weight, Runnable action) {
        Button key = new Button(getContext());
        key.setTag("monitor-key-" + name);
        key.setText(label);
        key.setTextSize(label.length() > 2 ? 11 : 14);
        key.setTextColor(Color.WHITE);
        key.setAllCaps(false);
        key.setGravity(Gravity.CENTER);
        key.setMinWidth(0);
        key.setMinimumWidth(0);
        key.setMinHeight(0);
        key.setMinimumHeight(0);
        key.setPadding(0, 0, 0, 0);
        key.setFocusable(false);
        key.setFocusableInTouchMode(false);
        key.setBackground(style.ripple(style.surface(20, 5, 45)));
        key.setContentDescription(label);
        LayoutParams lp = new LayoutParams(0, -1, weight);
        lp.setMargins(style.dp(1), style.dp(2), style.dp(1), style.dp(2));
        row.addView(key, lp);
        key.setOnClickListener(v -> action.run());
        return key;
    }

    private void character(LinearLayout row, String value, String name) {
        key(row, value, name, 1, () -> insert(value));
    }

    private void letter(LinearLayout row, char value) {
        String lower = String.valueOf(value);
        Button button =
                key(
                        row,
                        lower,
                        lower,
                        1,
                        () -> {
                            insert(shifted ? lower.toUpperCase(Locale.ROOT) : lower);
                            if (shifted) setShifted(false);
                        });
        letters.add(button);
    }

    private void setShifted(boolean enabled) {
        shifted = enabled;
        for (Button letter : letters) {
            String value = letter.getTag().toString().substring("monitor-key-".length());
            letter.setText(enabled ? value.toUpperCase(Locale.ROOT) : value);
            letter.setContentDescription(letter.getText());
        }
        shiftButton.setTextColor(enabled ? Color.BLACK : Color.WHITE);
        shiftButton.setBackground(style.ripple(style.surface(enabled ? 245 : 20, 5, 45)));
        shiftButton.setContentDescription("Shift, " + (enabled ? "on" : "off"));
    }

    private void space() {
        insert(" ");
    }

    private int selectionStart() {
        return Math.max(0, Math.min(editor.length(), editor.getSelectionStart()));
    }

    private int selectionEnd() {
        return Math.max(0, Math.min(editor.length(), editor.getSelectionEnd()));
    }

    private void insert(String value) {
        Editable text = editor.getText();
        int a = selectionStart(), b = selectionEnd(), start = Math.min(a, b), end = Math.max(a, b);
        int previousLength = text.length();
        text.replace(start, end, value);
        int acceptedLength = text.length() - previousLength + end - start;
        editor.setSelection(Math.min(text.length(), start + Math.max(0, acceptedLength)));
    }

    private void delete() {
        Editable text = editor.getText();
        int a = selectionStart(), b = selectionEnd(), start = Math.min(a, b), end = Math.max(a, b);
        if (start == end && start > 0) start = Character.offsetByCodePoints(text, start, -1);
        if (start != end) {
            text.delete(start, end);
            editor.setSelection(start);
        }
    }

    void showWithin(int availableListHeight) {
        int rowHeight =
                Math.min(
                        style.dp(38),
                        Math.max(style.dp(24), (availableListHeight - style.dp(56)) / 5));
        for (LinearLayout row : rows) {
            LayoutParams lp = (LayoutParams) row.getLayoutParams();
            lp.height = rowHeight;
            row.setLayoutParams(lp);
        }
        setVisibility(VISIBLE);
    }
}
