package dev.dexdroid.fixture

import android.app.Activity
import android.os.Bundle
import android.view.*
import android.widget.TextView

/** An ordinary app toggles FLAG_SECURE on the same window, without task changes. */
open class ProtectedFixtureActivity : Activity() {
    private var secure: Boolean = false
    private var taps: Int = 0
    private var label: TextView? = null
    private val control: android.content.BroadcastReceiver =
        object : android.content.BroadcastReceiver() {
            public override fun onReceive(
                context: android.content.Context?,
                intent: android.content.Intent,
            ) {
                if (intent.getIntExtra("display", -1) != getDisplay()!!.getDisplayId()) return
                secure = intent.getBooleanExtra("secure", false)
                update()
            }
        }

    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        registerControl()
        secure = getIntent()!!.getBooleanExtra("secure", false)
        label = TextView(this)
        label!!.setGravity(Gravity.CENTER)
        label!!.setTextSize(24f)
        label!!.setTextColor(-0x1)
        label!!.setOnClickListener({ v ->
            secure = !secure
            taps++
            update()
        })
        setContentView(label)
        label!!.addOnLayoutChangeListener({ v, l, t, r, b, ol, ot, or, ob -> reportState() })
        update()
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerControl() {
        val filter = android.content.IntentFilter("dev.dexdroid.test.SET_PROTECTION")
        if (android.os.Build.VERSION.SDK_INT >= 33)
            registerReceiver(control, filter, android.content.Context.RECEIVER_EXPORTED)
        else registerReceiver(control, filter)
    }

    protected override fun onDestroy() {
        unregisterReceiver(control)
        super.onDestroy()
    }

    private fun update() {
        if (secure) getWindow()!!.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else getWindow()!!.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        label!!.setText(if (secure) "Protected fixture" else "Normal fixture: tap to protect")
        label!!.setBackgroundColor(-0xebc3b0)
        reportState()
    }

    public override fun onWindowFocusChanged(focused: Boolean) {
        super.onWindowFocusChanged(focused)
        if (label != null) reportState()
    }

    private fun reportState() {
        val at = IntArray(2)
        label!!.getLocationOnScreen(at)
        sendBroadcast(
            android.content
                .Intent("dev.dexdroid.test.PROTECTION_STATE")
                .setPackage("dev.dexdroid")
                .putExtra("secure", secure)
                .putExtra("taps", taps)
                .putExtra("display", getDisplay()!!.getDisplayId())
                .putExtra("windowWidth", getWindow()!!.getDecorView().getWidth())
                .putExtra("windowHeight", getWindow()!!.getDecorView().getHeight())
                .putExtra("focused", hasWindowFocus())
                .putExtra("targetWidth", label!!.getWidth())
                .putExtra("targetHeight", label!!.getHeight())
                .putExtra("targetX", at[0] + label!!.getWidth() / 2f)
                .putExtra("targetY", at[1] + label!!.getHeight() / 2f)
                .putExtra("task", getTaskId())
        )
    }

    protected override fun onResume() {
        super.onResume()
        label!!.post(Runnable({ this.update() }))
    }
}
