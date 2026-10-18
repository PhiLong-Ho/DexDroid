package dev.dexdroid.fixture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;

/** Different-UID same-task navigation fixture; absent from all production artifacts. */
public class NavigationFixtureActivity extends Activity {
    private int opens;
    private boolean resumed;
    private String typed = "";
    private Button action;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        action = new Button(this);
        action.setText(this instanceof Child ? "Up to parent" : "Open navigation child");
        action.setAllCaps(false);
        action.setTextSize(24);
        action.setTextColor(0xffffffff);
        action.setBackgroundColor(this instanceof Child ? 0xff143c50 : 0xff502020);
        action.setGravity(Gravity.CENTER);
        action.setOnClickListener(
                v -> {
                    if (this instanceof Child) finish();
                    else {
                        opens++;
                        startActivity(new Intent(this, Child.class));
                    }
                });
        action.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> report());
        setContentView(action);
        inputState(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        inputState(intent);
    }

    private void inputState(Intent intent) {
        boolean ready = intent.getBooleanExtra("input_ready", true);
        if (ready)
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        else getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        action.post(this::report);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        getIntent()
                .putExtra(
                        "touchAction",
                        android.view.MotionEvent.actionToString(event.getActionMasked()));
        boolean handled = super.dispatchTouchEvent(event);
        report();
        return handled;
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        getWindow().getDecorView().post(this::report);
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        android.util.Log.i(
                "DexDroidNavFixture",
                getClass().getSimpleName()
                        + " focus="
                        + focused
                        + " task="
                        + getTaskId()
                        + " display="
                        + getDisplay().getDisplayId()
                        + " time="
                        + android.os.SystemClock.uptimeMillis());
        report();
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        android.util.Log.i(
                "DexDroidNavFixture",
                getClass().getSimpleName()
                        + " key="
                        + event.getKeyCode()
                        + " action="
                        + event.getAction()
                        + " down="
                        + event.getDownTime()
                        + " event="
                        + event.getEventTime()
                        + " flags="
                        + event.getFlags()
                        + " canceled="
                        + event.isCanceled()
                        + " focused="
                        + hasWindowFocus()
                        + " task="
                        + getTaskId()
                        + " display="
                        + getDisplay().getDisplayId());
        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_A) {
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) {
                typed += "a";
                action.setText("Open navigation child ? " + typed);
                report();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void report() {
        if (!resumed) return;
        LaunchParentActivity.report(
                this,
                this instanceof Child ? "navigation-child" : "navigation-parent",
                opens,
                getIntent().getBooleanExtra("input_ready", true) ? typed : "input-pending");
    }

    public static final class Child extends NavigationFixtureActivity {}
}
