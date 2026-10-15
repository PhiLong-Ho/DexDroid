package dev.dexdroid.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/** Private singleTask child opened by its own application, matching a reader transition. */
public class LaunchReaderActivity extends Activity {
    private int taps;
    private TextView target;
    private String touchDebug = "";
    private boolean contactDown;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) taps = state.getInt("taps");
        TextView view = new TextView(this);
        target = view;
        view.setText("Private reader: " + getIntent().getStringExtra("payload"));
        view.setTextSize(24);
        view.setTextColor(0xffffffff);
        view.setBackgroundColor(0xff143c50);
        view.setGravity(Gravity.CENTER);
        view.setOnClickListener(
                v -> {
                    taps++;
                    report();
                });
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> report());
        setContentView(view);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        getIntent()
                .putExtra(
                        "touchAction",
                        android.view.MotionEvent.actionToString(event.getActionMasked()))
                .putExtra("touchX", event.getX())
                .putExtra("touchY", event.getY());
        if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
            touchDebug = "";
            contactDown = true;
            getIntent().putExtra("focusLostDuringTouch", false);
            getIntent()
                    .putExtra(
                            "downDispatchDelayMs",
                            android.os.SystemClock.uptimeMillis() - event.getEventTime());
        }
        String before =
                " a="
                        + event.getActionMasked()
                        + " fitm="
                        + target.isFocusableInTouchMode()
                        + " f="
                        + target.isFocused()
                        + " p="
                        + target.isPressed()
                        + " flags="
                        + event.getFlags()
                        + " wf="
                        + hasWindowFocus();
        boolean handled = super.dispatchTouchEvent(event);
        touchDebug += before + " -> f=" + target.isFocused() + " p=" + target.isPressed() + ";";
        getIntent().putExtra("touchDebug", touchDebug);
        if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP
                || event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL)
            contactDown = false;
        getIntent().putExtra("touchHandled", handled);
        report();
        return handled;
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().getDecorView().post(this::report);
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused && contactDown) getIntent().putExtra("focusLostDuringTouch", true);
        if (focused) report();
    }

    private void report() {
        LaunchParentActivity.report(this, "reader", taps, getIntent().getStringExtra("payload"));
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("taps", taps);
        super.onSaveInstanceState(state);
    }
}
