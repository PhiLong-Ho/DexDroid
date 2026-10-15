package dev.dexdroid.fixture;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/** Normal different-UID test application, never part of the installed product. */
public class LaunchParentActivity extends Activity {
    public static final String ACTION = "dev.dexdroid.test.CHILD_LAUNCH_STATE";
    private int opens;
    private boolean resumed;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) opens = state.getInt("opens");
        TextView view = new TextView(this);
        view.setText("Open private reader");
        view.setTextSize(24);
        view.setTextColor(0xffffffff);
        view.setBackgroundColor(0xff502020);
        view.setGravity(Gravity.CENTER);
        view.setOnClickListener(
                v -> {
                    opens++;
                    startActivity(
                            new Intent(this, LaunchReaderActivity.class)
                                    .addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK
                                                    | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    .putExtra("payload", "reader-state-" + opens));
                });
        view.addOnLayoutChangeListener(
                (v, l, t, r, b, ol, ot, or, ob) -> {
                    if (resumed) report(this, "parent", opens, "");
                });
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
        boolean handled = super.dispatchTouchEvent(event);
        getIntent().putExtra("touchHandled", handled);
        report(this, "parent", opens, "");
        return handled;
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        report(this, "parent", opens, "");
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && resumed) report(this, "parent", opens, "");
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        report(this, "exit", opens, "");
        finish();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (resumed) report(this, "parent", opens, "");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (resumed) report(this, "parent", opens, "");
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("opens", opens);
        super.onSaveInstanceState(state);
    }

    static void report(Activity activity, String page, int count, String payload) {
        activity.sendBroadcast(
                new Intent(ACTION)
                        .setPackage("dev.dexdroid")
                        .putExtra("page", page)
                        .putExtra(
                                "focusLostDuringTouch",
                                activity.getIntent().getBooleanExtra("focusLostDuringTouch", false))
                        .putExtra(
                                "downDispatchDelayMs",
                                activity.getIntent().getLongExtra("downDispatchDelayMs", -1))
                        .putExtra("touchDebug", activity.getIntent().getStringExtra("touchDebug"))
                        .putExtra("touchAction", activity.getIntent().getStringExtra("touchAction"))
                        .putExtra("touchX", activity.getIntent().getFloatExtra("touchX", -1))
                        .putExtra("touchY", activity.getIntent().getFloatExtra("touchY", -1))
                        .putExtra(
                                "touchHandled",
                                activity.getIntent().getBooleanExtra("touchHandled", false))
                        .putExtra("focused", activity.hasWindowFocus())
                        .putExtra("windowWidth", activity.getWindow().getDecorView().getWidth())
                        .putExtra("windowHeight", activity.getWindow().getDecorView().getHeight())
                        .putExtra("display", activity.getDisplay().getDisplayId())
                        .putExtra("uid", android.os.Process.myUid())
                        .putExtra("task", activity.getTaskId())
                        .putExtra("count", count)
                        .putExtra("payload", payload));
    }
}
