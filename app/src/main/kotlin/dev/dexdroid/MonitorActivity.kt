package dev.dexdroid

import android.app.Activity
import android.content.*
import android.os.*
import android.view.*

/** Independent physical-monitor window; the service owns its desktop state. */
class MonitorActivity : Activity() {
    private var session: SessionService? = null
    private var bound: Boolean = false
    private var menuFocusSerial = 0L
    private var pendingMenuFocus: Runnable? = null
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
        // A reorder must reuse the owned host. Never attach an unexpected duplicate.
        if (intent.getBooleanExtra(MENU_FOCUS, false)) {
            finish()
            return
        }
        bound =
            bindService(
                Intent(this, SessionService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        if (!bound) finish()
    }

    internal fun invalidateMenuFocus() {
        menuFocusSerial++
        pendingMenuFocus?.let { window.decorView.removeCallbacks(it) }
        pendingMenuFocus = null
    }

    internal fun requestMenuFocus() {
        invalidateMenuFocus()
        val owner = session ?: return
        val serial = menuFocusSerial
        val ownedWindow = window
        val ownedTask = taskId
        val output = display?.displayId ?: return
        val generation = intent.getIntExtra("generation", -1)
        val token = intent.getStringExtra("monitorToken") ?: return
        pendingMenuFocus = Runnable {
            pendingMenuFocus = null
            if (
                serial != menuFocusSerial ||
                    isFinishing ||
                    isDestroyed ||
                    session !== owner ||
                    !owner.ownsMonitorHost(this) ||
                    !owner.nativeKeyboardPassiveHost() ||
                    window !== ownedWindow ||
                    taskId != ownedTask ||
                    display?.displayId != output ||
                    intent.getIntExtra("generation", -1) != generation ||
                    intent.getStringExtra("monitorToken") != token ||
                    owner.monitorShellPage() in setOf("closed", "stopped")
            )
                return@Runnable
            try {
                val request =
                    Intent(this, MonitorActivity::class.java)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION
                        )
                        .putExtra(MENU_FOCUS, true)
                        .putExtra("generation", generation)
                        .putExtra("monitorToken", token)
                        .putExtra("menuFocusSerial", serial)
                        .putExtra("menuFocusTask", ownedTask)
                        .putExtra("menuFocusDisplay", output)
                val options =
                    android.app.ActivityOptions.makeBasic().apply { launchDisplayId = output }
                startActivity(request, options.toBundle())
            } catch (_: RuntimeException) {
                owner.log("Monitor menu keyboard focus was not confirmed")
            }
        }
        // Run after the contact/key that opened the modal has completed dispatch.
        ownedWindow.decorView.post(pendingMenuFocus)
    }

    override fun onNewIntent(request: Intent) {
        super.onNewIntent(request)
        if (!request.getBooleanExtra(MENU_FOCUS, false)) return
        val owner = session ?: return
        if (
            isFinishing ||
                isDestroyed ||
                !owner.ownsMonitorHost(this) ||
                request.getIntExtra("generation", -1) != intent.getIntExtra("generation", -2) ||
                request.getStringExtra("monitorToken") != intent.getStringExtra("monitorToken") ||
                request.getLongExtra("menuFocusSerial", -1) != menuFocusSerial ||
                request.getIntExtra("menuFocusTask", -1) != taskId ||
                request.getIntExtra("menuFocusDisplay", -1) != display?.displayId ||
                owner.monitorShellPage() in setOf("closed", "stopped")
        )
            return
        // Do not replace the original host identity or reopen a menu from a late intent.
        owner.log("Monitor menu keyboard focus request delivered")
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
        invalidateMenuFocus()
        if (session != null) session!!.detachMonitor(this)
        if (bound) unbindService(connection)
        super.onDestroy()
    }

    companion object {
        private const val MENU_FOCUS = "dev.dexdroid.nativeImeMenuFocus"
    }
}
