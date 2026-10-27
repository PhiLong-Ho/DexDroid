package dev.dexdroid

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Display
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Only the publisher/Android launcher can enter. No target or command comes from Intent extras. */
class WorkspaceShortcutActivity : Activity() {
    private var bound = false
    private var delivered = false
    private var resumed = false
    private var session: SessionService? = null
    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (isFinishing || isDestroyed || delivered) return
                session = (binder as SessionService.LocalBinder).get()
                deliver()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                session = null
            }
        }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        delivered = state?.getBoolean("delivered") == true
        if (
            delivered || intent.action !in listOf(WorkspaceShortcut.RESTORE, WorkspaceShortcut.PIN)
        ) {
            openController()
            finish()
            return
        }
        if (intent.action == WorkspaceShortcut.RESTORE) showAcknowledgment()
        bound = bindService(Intent(this, SessionService::class.java), connection, BIND_AUTO_CREATE)
        if (!bound) {
            openController()
            finish()
        }
    }

    private fun showAcknowledgment() {
        val style = ControllerStyle(this)
        val canvas =
            FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.rgb(8, 8, 8)) }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(style.label("DexDroid", 22).apply { gravity = Gravity.CENTER })
        val notice =
            LaunchNotice(this, style).apply {
                tag = "workspace-entry-acknowledgment"
                render("Starting saved workspace…", true)
            }
        content.addView(notice)
        canvas.addView(
            content,
            FrameLayout.LayoutParams(
                minOf(style.dp(400f), resources.displayMetrics.widthPixels - style.dp(32f)),
                -2,
                Gravity.CENTER,
            ),
        )
        setContentView(canvas)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        deliver()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    private fun deliver() {
        val owner = session ?: return
        if (!resumed || delivered || isFinishing || isDestroyed) return
        delivered = true
        if (intent.action == WorkspaceShortcut.PIN) owner.workspaceShortcut.pin(this)
        else {
            owner.workspaceShortcut.request()
            openController()
        }
        finish()
    }

    private fun openController() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle(),
        )
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putBoolean("delivered", delivered)
        super.onSaveInstanceState(out)
    }

    override fun onDestroy() {
        if (bound) unbindService(connection)
        bound = false
        super.onDestroy()
    }
}
