package dev.dexdroid;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.view.*;

/** Independent physical-monitor window; the service owns its desktop state. */
public final class MonitorActivity extends Activity {
    private SessionService session;
    private boolean bound;
    private final ServiceConnection connection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder binder) {
                    if (isFinishing() || isDestroyed()) return;
                    session = ((SessionService.LocalBinder) binder).get();
                    if (!session.attachMonitor(
                            MonitorActivity.this, getIntent().getIntExtra("generation", -1)))
                        finish();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    session = null;
                    finish();
                }
            };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        bound = bindService(new Intent(this, SessionService.class), connection, BIND_AUTO_CREATE);
        if (!bound) finish();
    }

    void immersive() {
        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsets.Type.systemBars());
        }
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) immersive();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (session == null || !session.ownsMonitorHost(this)) return true;
        return session.monitorHostTouch(this, event) || super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                && session != null
                && session.ownsMonitorHost(this)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled())
                session.key(KeyEvent.KEYCODE_BACK);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (session != null && session.ownsMonitorHost(this)) session.key(KeyEvent.KEYCODE_BACK);
    }

    @Override
    public void onDestroy() {
        if (session != null) session.detachMonitor(this);
        if (bound) unbindService(connection);
        super.onDestroy();
    }
}
