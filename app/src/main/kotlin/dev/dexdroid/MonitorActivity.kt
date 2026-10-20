package dev.dexdroid

import android.app.Activity
import android.content.*
import android.os.*
import android.view.*

/** Independent physical-monitor window; the service owns its desktop state. */
class MonitorActivity : Activity() {
    private var session: SessionService? = null
    private var bound: Boolean = false
    private val connection: ServiceConnection =
        object : ServiceConnection {
            public override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (isFinishing() || isDestroyed()) return
                session = (binder as SessionService.LocalBinder).get()
                if (
                    !session!!.attachMonitor(
                        this@MonitorActivity,
                        getIntent()!!.getIntExtra("generation", -1),
                    )
                )
                    finish()
            }

            public override fun onServiceDisconnected(name: ComponentName?) {
                session = null
                finish()
            }
        }

    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        bound =
            bindService(
                Intent(this, SessionService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        if (!bound) finish()
    }

    fun immersive() {
        getWindow()!!.setDecorFitsSystemWindows(false)
        val controller = getWindow()!!.getInsetsController()
        if (controller != null) {
            controller!!.setSystemBarsBehavior(
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            )
            controller!!.hide(WindowInsets.Type.systemBars())
        }
    }

    public override fun onWindowFocusChanged(focused: Boolean) {
        super.onWindowFocusChanged(focused)
        if (focused) immersive()
    }

    public override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (session == null || !session!!.ownsMonitorHost(this)) return true
        return session!!.monitorHostTouch(this, event) || super.dispatchTouchEvent(event)
    }

    public override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (
            (event.getKeyCode() == KeyEvent.KEYCODE_BACK &&
                session != null &&
                session!!.ownsMonitorHost(this))
        ) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled())
                session!!.key(KeyEvent.KEYCODE_BACK)
            return true
        }
        return ((session != null &&
            session!!.ownsMonitorHost(this) &&
            session!!.hardwareKey(event)) || super.dispatchKeyEvent(event))
    }

    public override fun dispatchGenericMotionEvent(event: MotionEvent?): Boolean {
        val view = getWindow()!!.getDecorView()
        return (((session != null &&
            session!!.ownsMonitorHost(this) &&
            session!!.hardwarePointer(
                event,
                view.getWidth().toFloat(),
                view.getHeight().toFloat(),
            ))) || super.dispatchGenericMotionEvent(event))
    }

    public override fun onBackPressed() {
        if (session != null && session!!.ownsMonitorHost(this)) session!!.key(KeyEvent.KEYCODE_BACK)
    }

    public override fun onDestroy() {
        if (session != null) session!!.detachMonitor(this)
        if (bound) unbindService(connection)
        super.onDestroy()
    }
}
