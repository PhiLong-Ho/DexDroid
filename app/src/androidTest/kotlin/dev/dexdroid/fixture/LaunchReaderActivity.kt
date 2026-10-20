package dev.dexdroid.fixture

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

/** Private singleTask child opened by its own application, matching a reader transition. */
open class LaunchReaderActivity : Activity() {
    private var taps: Int = 0
    private var target: TextView? = null
    private var touchDebug: String? = ""
    private var contactDown: Boolean = false

    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (state != null) taps = state!!.getInt("taps")
        val view = TextView(this)
        target = view
        view.setText("Private reader: " + getIntent()!!.getStringExtra("payload")!!)
        view.setTextSize(24f)
        view.setTextColor(-0x1)
        view.setBackgroundColor(-0xebc3b0)
        view.setGravity(Gravity.CENTER)
        view.setOnClickListener({ v ->
            taps++
            report()
        })
        view.addOnLayoutChangeListener({ v, l, t, r, b, ol, ot, or, ob -> report() })
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
        if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
            touchDebug = ""
            contactDown = true
            getIntent()!!.putExtra("focusLostDuringTouch", false)
            getIntent()!!.putExtra(
                "downDispatchDelayMs",
                android.os.SystemClock.uptimeMillis() - event.getEventTime(),
            )
        }
        val before =
            (" a=" +
                event.getActionMasked() +
                " fitm=" +
                target!!.isFocusableInTouchMode() +
                " f=" +
                target!!.isFocused() +
                " p=" +
                target!!.isPressed() +
                " flags=" +
                event.getFlags() +
                " wf=" +
                hasWindowFocus())
        val handled = super.dispatchTouchEvent(event)
        touchDebug += before + " -> f=" + target!!.isFocused() + " p=" + target!!.isPressed() + ";"
        getIntent()!!.putExtra("touchDebug", touchDebug)
        if (
            (event.getActionMasked() == android.view.MotionEvent.ACTION_UP ||
                event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL)
        )
            contactDown = false
        getIntent()!!.putExtra("touchHandled", handled)
        report()
        return handled
    }

    protected override fun onResume() {
        super.onResume()
        getWindow()!!.getDecorView().post(Runnable({ this.report() }))
    }

    public override fun onWindowFocusChanged(focused: Boolean) {
        super.onWindowFocusChanged(focused)
        if (!focused && contactDown) getIntent()!!.putExtra("focusLostDuringTouch", true)
        if (focused) report()
    }

    private fun report() {
        LaunchParentActivity.report(this, "reader", taps, getIntent()!!.getStringExtra("payload"))
    }

    protected override fun onSaveInstanceState(state: Bundle) {
        state.putInt("taps", taps)
        super.onSaveInstanceState(state)
    }
}
