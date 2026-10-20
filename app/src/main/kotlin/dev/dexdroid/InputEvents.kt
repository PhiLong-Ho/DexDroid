package dev.dexdroid

import android.view.MotionEvent

/** Rebase child-local samples to a new display, including native raw/mouse cursor coordinates. */
object InputEvents {
    @JvmStatic
    fun wheel(x: Float, y: Float, horizontal: Float, vertical: Float): MotionEvent? {
        val now = android.os.SystemClock.uptimeMillis()
        val pointer = MotionEvent.PointerProperties()
        pointer.id = 0
        pointer.toolType = MotionEvent.TOOL_TYPE_MOUSE
        val coords = MotionEvent.PointerCoords()
        coords.x = x
        coords.y = y
        coords.setAxisValue(MotionEvent.AXIS_HSCROLL, horizontal)
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL, vertical)
        return MotionEvent.obtain(
            now,
            now,
            MotionEvent.ACTION_SCROLL,
            1,
            arrayOf<MotionEvent.PointerProperties?>(pointer),
            arrayOf<MotionEvent.PointerCoords?>(coords),
            0,
            0,
            1f,
            1f,
            0,
            0,
            android.view.InputDevice.SOURCE_MOUSE,
            0,
        )
    }

    @android.annotation.SuppressLint("PrivateApi")
    @Throws(Exception::class)
    @JvmStatic
    fun rebase(event: MotionEvent): MotionEvent? {
        val count = event.getPointerCount()
        if (count < 1 || count > 16) throw IllegalArgumentException("Invalid contacts")
        val properties = arrayOfNulls<MotionEvent.PointerProperties?>(count)
        val coords = arrayOfNulls<MotionEvent.PointerCoords?>(count)
        for (i in 0 until count) {
            properties[i] = MotionEvent.PointerProperties()
            event.getPointerProperties(i, properties[i])
            coords[i] = MotionEvent.PointerCoords()
            event.getPointerCoords(i, coords[i])
        }
        val copy =
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
                InputEventFlags.motion(event.getFlags(), android.os.Build.VERSION.SDK_INT),
            )
        try {
            if (event.getActionButton() != 0)
                MotionEvent::class
                    .java!!
                    .getMethod("setActionButton", Int::class.javaPrimitiveType)
                    .invoke(copy, event.getActionButton())
            return copy
        } catch (error: Exception) {
            copy!!.recycle()
            throw error
        }
    }
}
