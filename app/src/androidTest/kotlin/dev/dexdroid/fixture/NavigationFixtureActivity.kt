package dev.dexdroid.fixture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button

/** Different-UID same-task navigation fixture; absent from all production artifacts. */
open class NavigationFixtureActivity : Activity() {
    private var opens: Int = 0
    private var resumed: Boolean = false
    private var typed: String? = ""
    private var action: Button? = null

    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        action = Button(this)
        action!!.setText(if (this is Child) "Up to parent" else "Open navigation child")
        action!!.setAllCaps(false)
        action!!.setTextSize(24f)
        action!!.setTextColor(-0x1)
        action!!.setBackgroundColor(if (this is Child) -0xebc3b0 else -0xafdfe0)
        action!!.setGravity(Gravity.CENTER)
        action!!.setOnClickListener({ v ->
            if (this is Child) finish()
            else {
                opens++
                startActivity(Intent(this, Child::class.java))
            }
        })
        action!!.addOnLayoutChangeListener({ v, l, t, r, b, ol, ot, or, ob -> report() })
        setContentView(action)
        inputState(getIntent()!!)
    }

    protected override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        inputState(intent!!)
    }

    private fun inputState(intent: Intent) {
        val ready = intent.getBooleanExtra("input_ready", true)
        if (ready)
            getWindow()!!.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        else getWindow()!!.addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        action!!.post(Runnable({ this.report() }))
    }

    public override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        getIntent()!!.putExtra(
            "touchAction",
            android.view.MotionEvent.actionToString(event.getActionMasked()),
        )
        val handled = super.dispatchTouchEvent(event)
        report()
        return handled
    }

    protected override fun onResume() {
        super.onResume()
        resumed = true
        getWindow()!!.getDecorView().post(Runnable({ this.report() }))
    }

    protected override fun onPause() {
        resumed = false
        super.onPause()
    }

    public override fun onWindowFocusChanged(focused: Boolean) {
        super.onWindowFocusChanged(focused)
        android.util.Log.i(
            "DexDroidNavFixture",
            (javaClass.getSimpleName() +
                " focus=" +
                focused +
                " task=" +
                getTaskId() +
                " display=" +
                getDisplay()!!.getDisplayId() +
                " time=" +
                android.os.SystemClock.uptimeMillis()),
        )
        report()
    }

    public override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        android.util.Log.i(
            "DexDroidNavFixture",
            (javaClass.getSimpleName() +
                " key=" +
                event.getKeyCode() +
                " action=" +
                event.getAction() +
                " down=" +
                event.getDownTime() +
                " event=" +
                event.getEventTime() +
                " flags=" +
                event.getFlags() +
                " canceled=" +
                event.isCanceled() +
                " focused=" +
                hasWindowFocus() +
                " task=" +
                getTaskId() +
                " display=" +
                getDisplay()!!.getDisplayId()),
        )
        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_A) {
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) {
                typed += "a"
                action!!.setText("Open navigation child ? " + typed!!)
                report()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun report() {
        if (!resumed) return
        LaunchParentActivity.report(
            this,
            if (this is Child) "navigation-child" else "navigation-parent",
            opens,
            if (getIntent()!!.getBooleanExtra("input_ready", true)) typed else "input-pending",
        )
    }

    class Child : NavigationFixtureActivity()
}
