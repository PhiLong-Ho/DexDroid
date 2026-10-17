package dev.dexdroid;

import android.app.Activity;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;

/** Native touch/scroll/zoom diagnostic without external content or accounts. */
public class InputTestActivity extends Activity {
    public static volatile int touchEvents = 0, pinchEvents = 0, wheelEvents = 0, backEvents = 0;
    public static volatile int cancelEvents = 0, lastPointer0 = -1, lastPointer1 = -1;
    // Absolute display coordinates, including any system-bar window inset.
    public static volatile float lastUpX = -1, lastUpY = -1, lastWheelH = 0, lastWheelV = 0;
    public static volatile long lastDownTime = -1, lastUpDownTime = -1;

    @Override
    public void onBackPressed() {
        backEvents++;
        android.util.Log.i("DexDroidTest", "Back received on external test");
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(new TestView());
    }

    private class TestView extends View {
        Paint p = new Paint(3);
        float zoom = 1, offset = 0, startSpan = 0, startZoom = 1;
        String event = "Tap targets • wheel scroll • pinch to zoom";
        int taps = 0;

        TestView() {
            super(InputTestActivity.this);
            setBackgroundColor(Color.rgb(18, 32, 55));
        }

        @Override
        protected void onDraw(Canvas c) {
            p.setColor(Color.WHITE);
            p.setTextSize(26 * getResources().getDisplayMetrics().density);
            c.drawText("DexDroid input test", 32, 70, p);
            p.setTextSize(16 * getResources().getDisplayMetrics().density);
            c.drawText(
                    getWidth() + " × " + getHeight() + " · display " + getDisplay().getDisplayId(),
                    32,
                    115,
                    p);
            c.drawText(
                    "Taps "
                            + taps
                            + " · zoom "
                            + String.format(java.util.Locale.US, "%.2f", zoom)
                            + " · scroll "
                            + Math.round(offset),
                    32,
                    160,
                    p);
            c.drawText(event, 32, 205, p);
            p.setColor(Color.rgb(94, 234, 212));
            float[][] targets = {{.1f, .25f}, {.9f, .25f}, {.5f, .5f}, {.1f, .9f}, {.9f, .9f}};
            for (float[] t : targets)
                c.drawCircle(t[0] * getWidth(), t[1] * getHeight(), 35 * zoom, p);
            p.setColor(Color.YELLOW);
            c.drawLine(0, getHeight() / 2 + offset, getWidth(), getHeight() / 2 + offset, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            touchEvents++;
            lastDownTime = e.getDownTime();
            if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) cancelEvents++;
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                lastUpX = e.getRawX();
                lastUpY = e.getRawY();
                lastUpDownTime = e.getDownTime();
            }
            if (e.getPointerCount() == 2) {
                lastPointer0 = e.getPointerId(0);
                lastPointer1 = e.getPointerId(1);
                pinchEvents++;
                float span = (float) Math.hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0));
                if (e.getActionMasked() == 5) {
                    startSpan = span;
                    startZoom = zoom;
                } else if (e.getActionMasked() == 2 && startSpan > 0)
                    zoom = Geometry.clamp(startZoom * span / startSpan, .2f, 4);
                event = "Native pinch · pointers " + e.getPointerId(0) + ", " + e.getPointerId(1);
            } else if (e.getActionMasked() == 1) {
                taps++;
                event = "Tap " + Math.round(e.getX()) + ", " + Math.round(e.getY());
            }
            android.util.Log.i(
                    "DexDroidTest",
                    "action="
                            + e.getAction()
                            + " count="
                            + e.getPointerCount()
                            + " x="
                            + e.getX()
                            + " y="
                            + e.getY()
                            + " zoom="
                            + zoom);
            invalidate();
            return true;
        }

        @Override
        public boolean onGenericMotionEvent(MotionEvent e) {
            if (e.getActionMasked() == 8) {
                lastWheelH = e.getAxisValue(MotionEvent.AXIS_HSCROLL);
                lastWheelV = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
                wheelEvents++;
                offset += e.getAxisValue(MotionEvent.AXIS_VSCROLL) * 40;
                event =
                        "Wheel V="
                                + e.getAxisValue(MotionEvent.AXIS_VSCROLL)
                                + " H="
                                + e.getAxisValue(MotionEvent.AXIS_HSCROLL);
                android.util.Log.i("DexDroidTest", event);
                invalidate();
                return true;
            }
            return super.onGenericMotionEvent(e);
        }
    }
}
