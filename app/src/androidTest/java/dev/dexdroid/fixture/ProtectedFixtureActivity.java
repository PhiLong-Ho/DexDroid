package dev.dexdroid.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;

/** An ordinary app toggles FLAG_SECURE on the same window, without task changes. */
public class ProtectedFixtureActivity extends Activity {
    private boolean secure;
    private int taps;
    private TextView label;
    private final android.content.BroadcastReceiver control =
            new android.content.BroadcastReceiver() {
                public void onReceive(
                        android.content.Context context, android.content.Intent intent) {
                    if (intent.getIntExtra("display", -1) != getDisplay().getDisplayId()) return;
                    secure = intent.getBooleanExtra("secure", false);
                    update();
                }
            };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        registerControl();
        secure = getIntent().getBooleanExtra("secure", false);
        label = new TextView(this);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(24);
        label.setTextColor(0xffffffff);
        label.setOnClickListener(
                v -> {
                    secure = !secure;
                    taps++;
                    update();
                });
        setContentView(label);
        update();
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerControl() {
        android.content.IntentFilter filter =
                new android.content.IntentFilter("dev.dexdroid.test.SET_PROTECTION");
        if (android.os.Build.VERSION.SDK_INT >= 33)
            registerReceiver(control, filter, android.content.Context.RECEIVER_EXPORTED);
        else registerReceiver(control, filter);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(control);
        super.onDestroy();
    }

    private void update() {
        if (secure) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        label.setText(secure ? "Protected fixture" : "Normal fixture: tap to protect");
        label.setBackgroundColor(0xff143c50);
        sendBroadcast(
                new android.content.Intent("dev.dexdroid.test.PROTECTION_STATE")
                        .setPackage("dev.dexdroid")
                        .putExtra("secure", secure)
                        .putExtra("taps", taps)
                        .putExtra("display", getDisplay().getDisplayId())
                        .putExtra("task", getTaskId()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        label.post(this::update);
    }
}
