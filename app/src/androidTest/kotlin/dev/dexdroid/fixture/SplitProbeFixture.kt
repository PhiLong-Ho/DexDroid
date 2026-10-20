package dev.dexdroid.fixture

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.*
import android.view.*
import android.widget.*

/** Two different-UID native input fixtures; neither is included in a production APK. */
open class SplitProbeFixture : Activity() {

    private val observed: Bundle = Bundle()
    private var editor: EditText? = null
    private var button: Button? = null
    private var pad: TextView? = null
    private var root: LinearLayout? = null
    private var clicks: Int = 0
    private var wheels: Int = 0
    private var moves: Int = 0
    private var ups: Int = 0
    private var cancels: Int = 0
    private var keyDowns: Int = 0
    private var keyUps: Int = 0
    private var contact: Boolean = false
    private val instance =
        "${android.os.Process.myPid()}:${System.identityHashCode(this)}:${android.os.SystemClock.elapsedRealtimeNanos()}"
    private var fixtureTask = -1
    private var reportSerial = 0L
    private var callbackSerial = 0L
    private val callbacks = java.util.ArrayDeque<String>()

    private fun callback(kind: String) {
        callbackSerial++
        if (callbacks.size == 32) callbacks.removeFirst()
        callbacks.addLast("$callbackSerial t=${android.os.SystemClock.uptimeMillis()} $kind")
        // Independent lifecycle evidence, including destroy without a later report.
        synchronized(SplitProbeFixture::class.java) {
            if (keyTraceActive && traceRecords < 128) {
                try {
                    val line =
                        if (traceRecords == 127) "TRACE_TRUNCATED"
                        else
                            "instance=$instance task=$fixtureTask display=${display?.displayId ?: -1} uid=${android.os.Process.myUid()} callback=$callbackSerial t=${android.os.SystemClock.uptimeMillis()} $kind"
                    android.util.Log.d(
                        "DexDroidKeyFixtureDiag",
                        if (line.length <= 512 && line.all { it.code in 32..126 }) line
                        else "RECORD_TRUNCATED",
                    )
                } catch (_: RuntimeException) {
                    // Missing evidence is inconclusive; never replace the Activity callback.
                }
                traceRecords++
            }
        }
    }

    protected override fun onResume() {
        super.onResume()
        callback("resume")
    }

    protected override fun onPause() {
        callback("pause")
        super.onPause()
    }

    protected override fun onDestroy() {
        callback("destroy")
        super.onDestroy()
    }

    class Left : SplitProbeFixture()

    class Right : SplitProbeFixture()

    public override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            contact = true
            observed.putBoolean("focusLostDuringContact", false)
            observed.putLong(
                "downLatencyMs",
                android.os.SystemClock.uptimeMillis() - event.getEventTime(),
            )
            observed.putLong("downSampleTime", event.getEventTime())
        }
        observed.putInt("lastTouchAction", event.getActionMasked())
        observed.putBoolean("touchWindowFocus", hasWindowFocus())
        observed.putFloat("touchX", event.getX())
        observed.putFloat("touchY", event.getY())
        observed.putFloat("touchRawX", event.getRawX())
        observed.putFloat("touchRawY", event.getRawY())
        observed.putLong("touchTime", event.getEventTime())
        val handled = super.dispatchTouchEvent(event)
        if (
            (event.getActionMasked() == MotionEvent.ACTION_UP ||
                event.getActionMasked() == MotionEvent.ACTION_CANCEL)
        )
            contact = false
        report()
        return handled
    }

    public override fun onWindowFocusChanged(focused: Boolean) {
        super.onWindowFocusChanged(focused)
        callback("focus=$focused")
        if (!focused && contact) observed.putBoolean("focusLostDuringContact", true)
        report()
    }

    public override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        fixtureTask = taskId
        callback("create restored=${saved != null}")
        if (saved != null) {
            observed.putAll(saved)
            clicks = saved!!.getInt("clicks")
            wheels = saved!!.getInt("wheels")
            moves = saved!!.getInt("moves")
            ups = saved!!.getInt("ups")
            cancels = saved!!.getInt("cancels")
            keyDowns = saved!!.getInt("keyDowns")
            keyUps = saved!!.getInt("keyUps")
        }
        getWindow()!!.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        root = LinearLayout(this)
        root!!.setOrientation(LinearLayout.VERTICAL)
        root!!.setBackgroundColor(Color.rgb(18, 18, 18))
        root!!.setPadding(24, 24, 24, 24)
        val title = TextView(this)
        title.setText(javaClass.getSimpleName() + " | independent app fixture")
        title.setTextColor(Color.WHITE)
        title.setTextSize(22f)
        root!!.addView(title)
        editor = EditText(this)
        editor!!.setSingleLine(true)
        editor!!.setTextColor(Color.WHITE)
        editor!!.setHintTextColor(Color.LTGRAY)
        editor!!.setHint("Hardware keyboard input")
        if (saved != null) editor!!.setText(saved!!.getString("text", ""))
        editor!!.setShowSoftInputOnFocus(false)
        root!!.addView(editor, LinearLayout.LayoutParams(-1, 100))
        button = Button(this)
        button!!.setText("Native click target")
        root!!.addView(button, LinearLayout.LayoutParams(-1, 120))
        button!!.setOnClickListener({ view ->
            clicks++
            button!!.setText("Native clicks: " + clicks)
            report()
        })
        pad = TextView(this)
        pad!!.setText("Pointer / scroll / drag area")
        pad!!.setGravity(Gravity.CENTER)
        pad!!.setTextColor(Color.WHITE)
        pad!!.setBackgroundColor(Color.rgb(40, 40, 40))
        root!!.addView(pad, LinearLayout.LayoutParams(-1, 0, 1f))
        pad!!.setOnTouchListener({ view, event ->
            if (event!!.getActionMasked() == MotionEvent.ACTION_MOVE) moves++
            if (event!!.getActionMasked() == MotionEvent.ACTION_UP) ups++
            if (event!!.getActionMasked() == MotionEvent.ACTION_CANCEL) cancels++
            observed.putInt("pointerSource", event!!.getSource())
            observed.putInt("pointerButtons", event!!.getButtonState())
            if (event!!.getButtonState() != 0)
                observed.putInt("lastHeldButtons", event!!.getButtonState())
            observed.putFloat("lastX", event!!.getX())
            report()
            true
        })
        pad!!.setOnGenericMotionListener({ view, event ->
            if (event!!.getActionMasked() != MotionEvent.ACTION_SCROLL)
                return@setOnGenericMotionListener false
            wheels++
            observed.putFloat("wheelV", event!!.getAxisValue(MotionEvent.AXIS_VSCROLL))
            observed.putFloat("wheelH", event!!.getAxisValue(MotionEvent.AXIS_HSCROLL))
            observed.putInt("wheelSource", event!!.getSource())
            report()
            true
        })
        editor!!.addTextChangedListener(
            object : TextWatcher {
                public override fun beforeTextChanged(
                    text: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                public override fun onTextChanged(
                    text: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    report()
                }

                public override fun afterTextChanged(text: Editable?) {}
            }
        )
        root!!
            .getViewTreeObserver()!!
            .addOnPreDrawListener({
                geometry()
                report()
                true
            })
        setContentView(root)
        editor!!.requestFocus()
    }

    protected override fun onSaveInstanceState(state: Bundle) {
        report()
        state.putAll(observed)
        super.onSaveInstanceState(state)
    }

    public override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (
            event.deviceId == -1 &&
                event.isFromSource(InputDevice.SOURCE_KEYBOARD) &&
                event.keyCode in intArrayOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_SHIFT_LEFT)
        )
            keyTraceActive = true
        callback(
            "key action=${event.action} code=${event.keyCode} source=${event.source} device=${event.deviceId} scan=${event.scanCode} down=${event.downTime} event=${event.eventTime} flags=${event.flags} repeat=${event.repeatCount}"
        )
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            keyDowns++
            observed.putInt("keyMeta", event.getMetaState())
            observed.putInt("keyRepeat", event.getRepeatCount())
            observed.putInt("keyCode", event.getKeyCode())
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            keyUps++
            observed.putInt("keyUpFlags", event.getFlags())
            observed.putLong("keyUpTime", event.getEventTime())
            observed.putInt("keyUpRepeat", event.getRepeatCount())
        }
        val handled = super.dispatchKeyEvent(event)
        report()
        return handled
    }

    private fun report() {
        if (editor == null || button == null || pad == null) return
        observed.putString("pane", javaClass.getSimpleName())
        observed.putString("instance", instance)
        observed.putInt("task", fixtureTask)
        observed.putLong("reportSerial", ++reportSerial)
        observed.putLong("callbackSerial", callbackSerial)
        observed.putLong("callbackFirstSerial", callbackSerial - callbacks.size + 1)
        observed.putStringArrayList("callbackHistory", ArrayList(callbacks))
        observed.putBoolean("currentWindowFocus", hasWindowFocus())
        observed.putInt("uid", android.os.Process.myUid())
        observed.putInt("display", getDisplay()!!.getDisplayId())
        observed.putInt("clicks", clicks)
        observed.putInt("wheels", wheels)
        observed.putInt("moves", moves)
        observed.putInt("ups", ups)
        observed.putInt("cancels", cancels)
        observed.putInt("keyDowns", keyDowns)
        observed.putInt("keyUps", keyUps)
        observed.putString("text", editor!!.getText()!!.toString())
        val caption = "Scroll " + wheels + " | drag moves " + moves + " | release " + ups
        if (!caption!!.contentEquals(pad!!.getText()!!)) pad!!.setText(caption)
        sendBroadcast(Intent(ACTION).setPackage("dev.dexdroid").putExtras(observed))
    }

    // Publish one post-layout snapshot. Focus/config callbacks can observe the
    // new decor size before its children move; mixing those coordinates misses targets.
    private fun geometry() {
        observed.putInt("width", getWindow()!!.getDecorView().getWidth())
        observed.putInt("height", getWindow()!!.getDecorView().getHeight())
        observed.putInt("layoutWidth", root!!.getWidth())
        val rootLocation = IntArray(2)
        root!!.getLocationOnScreen(rootLocation)
        observed.putInt("layoutBottom", rootLocation[1] + root!!.getHeight())
        for (view in arrayOf<View?>(button, editor, pad)) {
            val location = IntArray(2)
            view!!.getLocationOnScreen(location)
            val prefix = if (view === button) "button" else if (view === editor) "editor" else "pad"
            observed.putFloat(prefix + "X", location[0] + view!!.getWidth() / 2f)
            observed.putFloat(prefix + "Y", location[1] + view!!.getHeight() / 2f)
        }
    }

    companion object {
        private var traceRecords = 0
        private var keyTraceActive = false

        @JvmField val ACTION: String = "dev.dexdroid.splitprobe.STATE"
    }
}
