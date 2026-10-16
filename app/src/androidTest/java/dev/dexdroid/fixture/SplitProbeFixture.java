package dev.dexdroid.fixture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;

/** Two different-UID native input fixtures; neither is included in a production APK. */
public class SplitProbeFixture extends Activity {
    public static final String ACTION = "dev.dexdroid.splitprobe.STATE";

    public static final class Left extends SplitProbeFixture {}

    public static final class Right extends SplitProbeFixture {}

    private final Bundle observed = new Bundle();
    private EditText editor;
    private Button button;
    private TextView pad;
    private LinearLayout root;
    private int clicks, wheels, moves, ups, cancels, keyDowns, keyUps;
    private boolean contact;

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            contact = true;
            observed.putBoolean("focusLostDuringContact", false);
            observed.putLong(
                    "downLatencyMs", android.os.SystemClock.uptimeMillis() - event.getEventTime());
            observed.putLong("downSampleTime", event.getEventTime());
        }
        observed.putInt("lastTouchAction", event.getActionMasked());
        observed.putBoolean("touchWindowFocus", hasWindowFocus());
        observed.putFloat("touchX", event.getX());
        observed.putFloat("touchY", event.getY());
        observed.putFloat("touchRawX", event.getRawX());
        observed.putFloat("touchRawY", event.getRawY());
        observed.putLong("touchTime", event.getEventTime());
        boolean handled = super.dispatchTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) contact = false;
        report();
        return handled;
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused && contact) observed.putBoolean("focusLostDuringContact", true);
        report();
    }

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) {
            observed.putAll(saved);
            clicks = saved.getInt("clicks");
            wheels = saved.getInt("wheels");
            moves = saved.getInt("moves");
            ups = saved.getInt("ups");
            cancels = saved.getInt("cancels");
            keyDowns = saved.getInt("keyDowns");
            keyUps = saved.getInt("keyUps");
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 18, 18));
        root.setPadding(24, 24, 24, 24);
        TextView title = new TextView(this);
        title.setText(getClass().getSimpleName() + " | independent app fixture");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        root.addView(title);
        editor = new EditText(this);
        editor.setSingleLine(true);
        editor.setTextColor(Color.WHITE);
        editor.setHintTextColor(Color.LTGRAY);
        editor.setHint("Hardware keyboard input");
        if (saved != null) editor.setText(saved.getString("text", ""));
        editor.setShowSoftInputOnFocus(false);
        root.addView(editor, new LinearLayout.LayoutParams(-1, 100));
        button = new Button(this);
        button.setText("Native click target");
        root.addView(button, new LinearLayout.LayoutParams(-1, 120));
        button.setOnClickListener(
                view -> {
                    clicks++;
                    button.setText("Native clicks: " + clicks);
                    report();
                });
        pad = new TextView(this);
        pad.setText("Pointer / scroll / drag area");
        pad.setGravity(Gravity.CENTER);
        pad.setTextColor(Color.WHITE);
        pad.setBackgroundColor(Color.rgb(40, 40, 40));
        root.addView(pad, new LinearLayout.LayoutParams(-1, 0, 1));
        pad.setOnTouchListener(
                (view, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_MOVE) moves++;
                    if (event.getActionMasked() == MotionEvent.ACTION_UP) ups++;
                    if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) cancels++;
                    observed.putInt("pointerSource", event.getSource());
                    observed.putInt("pointerButtons", event.getButtonState());
                    if (event.getButtonState() != 0)
                        observed.putInt("lastHeldButtons", event.getButtonState());
                    observed.putFloat("lastX", event.getX());
                    report();
                    return true;
                });
        pad.setOnGenericMotionListener(
                (view, event) -> {
                    if (event.getActionMasked() != MotionEvent.ACTION_SCROLL) return false;
                    wheels++;
                    observed.putFloat("wheelV", event.getAxisValue(MotionEvent.AXIS_VSCROLL));
                    observed.putFloat("wheelH", event.getAxisValue(MotionEvent.AXIS_HSCROLL));
                    observed.putInt("wheelSource", event.getSource());
                    report();
                    return true;
                });
        editor.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence text, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence text, int start, int before, int count) {
                        report();
                    }

                    @Override
                    public void afterTextChanged(Editable text) {}
                });
        root.getViewTreeObserver()
                .addOnPreDrawListener(
                        () -> {
                            geometry();
                            report();
                            return true;
                        });
        setContentView(root);
        editor.requestFocus();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        report();
        state.putAll(observed);
        super.onSaveInstanceState(state);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            keyDowns++;
            observed.putInt("keyMeta", event.getMetaState());
            observed.putInt("keyRepeat", event.getRepeatCount());
            observed.putInt("keyCode", event.getKeyCode());
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            keyUps++;
            observed.putInt("keyUpFlags", event.getFlags());
            observed.putLong("keyUpTime", event.getEventTime());
            observed.putInt("keyUpRepeat", event.getRepeatCount());
        }
        boolean handled = super.dispatchKeyEvent(event);
        report();
        return handled;
    }

    private void report() {
        if (editor == null || button == null || pad == null) return;
        observed.putString("pane", getClass().getSimpleName());
        observed.putInt("uid", android.os.Process.myUid());
        observed.putInt("display", getDisplay().getDisplayId());
        observed.putInt("clicks", clicks);
        observed.putInt("wheels", wheels);
        observed.putInt("moves", moves);
        observed.putInt("ups", ups);
        observed.putInt("cancels", cancels);
        observed.putInt("keyDowns", keyDowns);
        observed.putInt("keyUps", keyUps);
        observed.putString("text", editor.getText().toString());
        String caption = "Scroll " + wheels + " | drag moves " + moves + " | release " + ups;
        if (!caption.contentEquals(pad.getText())) pad.setText(caption);
        sendBroadcast(new Intent(ACTION).setPackage("dev.dexdroid").putExtras(observed));
    }

    // Publish one post-layout snapshot. Focus/config callbacks can observe the
    // new decor size before its children move; mixing those coordinates misses targets.
    private void geometry() {
        observed.putInt("width", getWindow().getDecorView().getWidth());
        observed.putInt("height", getWindow().getDecorView().getHeight());
        observed.putInt("layoutWidth", root.getWidth());
        int[] rootLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        observed.putInt("layoutBottom", rootLocation[1] + root.getHeight());
        for (View view : new View[] {button, editor, pad}) {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            String prefix = view == button ? "button" : view == editor ? "editor" : "pad";
            observed.putFloat(prefix + "X", location[0] + view.getWidth() / 2f);
            observed.putFloat(prefix + "Y", location[1] + view.getHeight() / 2f);
        }
    }
}
