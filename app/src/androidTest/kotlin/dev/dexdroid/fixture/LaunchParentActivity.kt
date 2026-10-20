package dev.dexdroid.fixture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

/** Normal different-UID test application, never part of the installed product. */
open class LaunchParentActivity : Activity() {
    private var opens: Int = 0
    private var resumed: Boolean = false

    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (state != null) opens = state!!.getInt("opens")
        val view = TextView(this)
        view.setText("Open private reader")
        view.setTextSize(24f)
        view.setTextColor(-0x1)
        view.setBackgroundColor(-0xafdfe0)
        view.setGravity(Gravity.CENTER)
        view.setOnClickListener({ v ->
            opens++
            startActivity(
                Intent(this, LaunchReaderActivity::class.java)
                    .addFlags((Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    .putExtra("payload", "reader-state-" + opens)
            )
        })
        view.addOnLayoutChangeListener({ v, l, t, r, b, ol, ot, or, ob ->
            if (resumed) report(this, "parent", opens, "")
        })
        setContentView(view)
    }

    public override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        getIntent()!!
            .putExtra(
                "touchAction",
                android.view.MotionEvent.actionToString(event.getActionMasked()),
            )
            .putExtra("touchX", event.getX())
            .putExtra("touchY", event.getY())
        val handled = super.dispatchTouchEvent(event)
        getIntent()!!.putExtra("touchHandled", handled)
        report(this, "parent", opens, "")
        return handled
    }

    protected override fun onResume() {
        super.onResume()
        resumed = true
        report(this, "parent", opens, "")
    }

    public override fun onWindowFocusChanged(focused: Boolean) {
        super.onWindowFocusChanged(focused)
        if (focused && resumed) report(this, "parent", opens, "")
    }

    protected override fun onPause() {
        resumed = false
        super.onPause()
    }

    public override fun onBackPressed() {
        report(this, "exit", opens, "")
        finish()
    }

    public override fun onConfigurationChanged(configuration: android.content.res.Configuration) {
        super.onConfigurationChanged(configuration)
        if (resumed) report(this, "parent", opens, "")
    }

    protected override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (resumed) report(this, "parent", opens, "")
    }

    protected override fun onSaveInstanceState(state: Bundle) {
        state.putInt("opens", opens)
        super.onSaveInstanceState(state)
    }

    companion object {
        @JvmField val ACTION: String = "dev.dexdroid.test.CHILD_LAUNCH_STATE"

        @JvmStatic
        fun report(activity: Activity, page: String?, count: Int, payload: String?) {
            val container = activity.findViewById<android.view.ViewGroup?>(android.R.id.content)
            val target =
                if (container!!.getChildCount() > 0) container!!.getChildAt(0) else container
            val at = IntArray(2)
            target!!.getLocationOnScreen(at)
            activity.sendBroadcast(
                Intent(ACTION)
                    .setPackage("dev.dexdroid")
                    .putExtra("page", page)
                    .putExtra(
                        "focusLostDuringTouch",
                        activity.getIntent()!!.getBooleanExtra("focusLostDuringTouch", false),
                    )
                    .putExtra(
                        "downDispatchDelayMs",
                        activity.getIntent()!!.getLongExtra("downDispatchDelayMs", -1),
                    )
                    .putExtra("touchDebug", activity.getIntent()!!.getStringExtra("touchDebug"))
                    .putExtra("touchAction", activity.getIntent()!!.getStringExtra("touchAction"))
                    .putExtra("touchX", activity.getIntent()!!.getFloatExtra("touchX", -1f))
                    .putExtra("touchY", activity.getIntent()!!.getFloatExtra("touchY", -1f))
                    .putExtra(
                        "touchHandled",
                        activity.getIntent()!!.getBooleanExtra("touchHandled", false),
                    )
                    .putExtra("focused", activity.hasWindowFocus())
                    .putExtra("windowWidth", activity.getWindow()!!.getDecorView().getWidth())
                    .putExtra("windowHeight", activity.getWindow()!!.getDecorView().getHeight())
                    .putExtra("targetWidth", target!!.getWidth())
                    .putExtra("targetHeight", target!!.getHeight())
                    .putExtra("targetX", at[0] + target!!.getWidth() / 2f)
                    .putExtra("targetY", at[1] + target!!.getHeight() / 2f)
                    .putExtra("display", activity.getDisplay()!!.getDisplayId())
                    .putExtra("uid", android.os.Process.myUid())
                    .putExtra("task", activity.getTaskId())
                    .putExtra("count", count)
                    .putExtra("payload", payload)
            )
        }
    }
}
