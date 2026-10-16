package dev.dexdroid;

import android.view.MotionEvent;

/** Rebase child-local samples to a new display, including native raw/mouse cursor coordinates. */
final class InputEvents {
    static MotionEvent wheel(float x, float y, float horizontal, float vertical) {
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent.PointerProperties pointer = new MotionEvent.PointerProperties();
        pointer.id = 0;
        pointer.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x;
        coords.y = y;
        coords.setAxisValue(MotionEvent.AXIS_HSCROLL, horizontal);
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL, vertical);
        return MotionEvent.obtain(
                now,
                now,
                MotionEvent.ACTION_SCROLL,
                1,
                new MotionEvent.PointerProperties[] {pointer},
                new MotionEvent.PointerCoords[] {coords},
                0,
                0,
                1,
                1,
                0,
                0,
                android.view.InputDevice.SOURCE_MOUSE,
                0);
    }

    @android.annotation.SuppressLint("PrivateApi")
    static MotionEvent rebase(MotionEvent event) throws Exception {
        int count = event.getPointerCount();
        if (count < 1 || count > 16) throw new IllegalArgumentException("Invalid contacts");
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            event.getPointerProperties(i, properties[i]);
            coords[i] = new MotionEvent.PointerCoords();
            event.getPointerCoords(i, coords[i]);
        }
        MotionEvent copy =
                MotionEvent.obtain(
                        event.getDownTime(),
                        event.getEventTime(),
                        event.getAction(),
                        count,
                        properties,
                        coords,
                        event.getMetaState(),
                        event.getButtonState(),
                        event.getXPrecision(),
                        event.getYPrecision(),
                        // A destination gesture uses the same synthetic device as
                        // the existing shell bridge, rather than a source display device.
                        0,
                        event.getEdgeFlags(),
                        event.getSource(),
                        InputEventFlags.motion(event.getFlags(), android.os.Build.VERSION.SDK_INT));
        try {
            if (event.getActionButton() != 0)
                MotionEvent.class
                        .getMethod("setActionButton", int.class)
                        .invoke(copy, event.getActionButton());
            return copy;
        } catch (Exception error) {
            copy.recycle();
            throw error;
        }
    }

    private InputEvents() {}
}
