package dev.dexdroid

import android.app.Activity
import android.graphics.*
import android.os.Bundle
import android.view.*

/** Native touch/scroll/zoom diagnostic without external content or accounts. */
open class InputTestActivity : Activity() {

    public override fun onBackPressed() {
        backEvents++
        android.util.Log.i("DexDroidTest", "Back received on external test")
    }

    public override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(TestView())
    }

    private open inner class TestView : View(this@InputTestActivity) {
        @JvmField var p: Paint? = Paint(3)
        @JvmField var zoom: Float = 1f
        @JvmField var offset: Float = 0f
        @JvmField var startSpan: Float = 0f
        @JvmField var startZoom: Float = 1f
        @JvmField var event: String? = "Tap targets • wheel scroll • pinch to zoom"
        @JvmField var taps: Int = 0

        init {
            setBackgroundColor(Color.rgb(18, 32, 55))
        }

        protected override fun onDraw(c: Canvas) {
            p!!.setColor(Color.WHITE)
            p!!.setTextSize(26 * getResources()!!.getDisplayMetrics()!!.density)
            c.drawText("DexDroid input test", 32f, 70f, p!!)
            p!!.setTextSize(16 * getResources()!!.getDisplayMetrics()!!.density)
            c.drawText(
                getWidth().toString() +
                    " × " +
                    getHeight() +
                    " · display " +
                    getDisplay()!!.getDisplayId(),
                32f,
                115f,
                p!!,
            )
            c.drawText(
                ("Taps " +
                    taps +
                    " · zoom " +
                    String.format(java.util.Locale.US, "%.2f", zoom) +
                    " · scroll " +
                    Math.round(offset)),
                32f,
                160f,
                p!!,
            )
            c.drawText(event!!, 32f, 205f, p!!)
            p!!.setColor(Color.rgb(94, 234, 212))
            val targets =
                arrayOf<FloatArray?>(
                    floatArrayOf(.1f, .25f),
                    floatArrayOf(.9f, .25f),
                    floatArrayOf(.5f, .5f),
                    floatArrayOf(.1f, .9f),
                    floatArrayOf(.9f, .9f),
                )
            for (t in targets!!) c.drawCircle(
                t!![0] * getWidth(),
                t!![1] * getHeight(),
                35 * zoom,
                p!!,
            )
            p!!.setColor(Color.YELLOW)
            c.drawLine(
                0f,
                getHeight() / 2 + offset,
                getWidth().toFloat(),
                getHeight() / 2 + offset,
                p!!,
            )
        }

        public override fun onTouchEvent(e: MotionEvent): Boolean {
            touchEvents++
            lastDownTime = e.getDownTime()
            if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) cancelEvents++
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                lastUpX = e.getRawX()
                lastUpY = e.getRawY()
                lastUpDownTime = e.getDownTime()
            }
            if (e.getPointerCount() == 2) {
                lastPointer0 = e.getPointerId(0)
                lastPointer1 = e.getPointerId(1)
                pinchEvents++
                val span =
                    Math.hypot(
                            (e.getX(1) - e.getX(0)).toDouble(),
                            (e.getY(1) - e.getY(0)).toDouble(),
                        )
                        .toFloat()
                if (e.getActionMasked() == 5) {
                    startSpan = span
                    startZoom = zoom
                } else if (e.getActionMasked() == 2 && startSpan > 0)
                    zoom = Geometry.clamp(startZoom * span / startSpan, .2f, 4f)
                event = "Native pinch · pointers " + e.getPointerId(0) + ", " + e.getPointerId(1)
            } else if (e.getActionMasked() == 1) {
                taps++
                event = "Tap " + Math.round(e.getX()) + ", " + Math.round(e.getY())
            }
            android.util.Log.i(
                "DexDroidTest",
                ("action=" +
                    e.getAction() +
                    " count=" +
                    e.getPointerCount() +
                    " x=" +
                    e.getX() +
                    " y=" +
                    e.getY() +
                    " zoom=" +
                    zoom),
            )
            invalidate()
            return true
        }

        public override fun onGenericMotionEvent(e: MotionEvent): Boolean {
            if (e.getActionMasked() == 8) {
                lastWheelH = e.getAxisValue(MotionEvent.AXIS_HSCROLL)
                lastWheelV = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
                wheelEvents++
                offset += e.getAxisValue(MotionEvent.AXIS_VSCROLL) * 40
                event =
                    ("Wheel V=" +
                        e.getAxisValue(MotionEvent.AXIS_VSCROLL) +
                        " H=" +
                        e.getAxisValue(MotionEvent.AXIS_HSCROLL))
                android.util.Log.i("DexDroidTest", event!!)
                invalidate()
                return true
            }
            return super.onGenericMotionEvent(e)
        }
    }

    companion object {
        @Volatile @JvmField var touchEvents: Int = 0
        @Volatile @JvmField var pinchEvents: Int = 0
        @Volatile @JvmField var wheelEvents: Int = 0
        @Volatile @JvmField var backEvents: Int = 0
        @Volatile @JvmField var cancelEvents: Int = 0
        @Volatile @JvmField var lastPointer0: Int = -1
        @Volatile @JvmField var lastPointer1: Int = -1
        // Absolute display coordinates, including any system-bar window inset.
        @Volatile @JvmField var lastUpX: Float = -1f
        @Volatile @JvmField var lastUpY: Float = -1f
        @Volatile @JvmField var lastWheelH: Float = 0f
        @Volatile @JvmField var lastWheelV: Float = 0f
        @Volatile @JvmField var lastDownTime: Long = -1
        @Volatile @JvmField var lastUpDownTime: Long = -1
    }
}
