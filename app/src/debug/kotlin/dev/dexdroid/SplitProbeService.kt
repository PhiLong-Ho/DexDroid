package dev.dexdroid

import android.os.*
import android.view.*
import java.lang.reflect.Method

/** Experimental shell bridge. Distinct component names give each pane its own process/display. */
@android.annotation.SuppressLint("PrivateApi")
open class SplitProbeService : DeskService() {
    private var owned: Int = -1
    private var input: Any? = null
    private var inject: Method? = null

    class Left : SplitProbeService()

    class Right : SplitProbeService()

    @Synchronized
    public override fun create(
        surface: Surface?,
        width: Int,
        height: Int,
        dpi: Int,
        owner: IBinder?,
        listener: IDesktopListener?,
    ): Int {
        owned = super.create(surface, width, height, dpi, owner, listener)
        return owned
    }

    @Synchronized
    public override fun stop() {
        owned = -1
        super.stop()
    }

    @Synchronized
    @Throws(RemoteException::class)
    public override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code != INPUT) return super.onTransact(code, data, reply, flags)
        data.enforceInterface(PROTOCOL)
        val display = data.readInt()
        val kind = data.readInt()
        var motion: MotionEvent? = null
        val identity = Binder.clearCallingIdentity()
        try {
            if (display < 1 || display != owned)
                throw IllegalStateException("Obsolete prototype display")
            if (android.os.Process.myUid() != 2000)
                throw SecurityException("Prototype requires Shizuku shell")
            if (input == null) {
                var manager: Class<*>?
                try {
                    manager = Class.forName("android.hardware.input.InputManagerGlobal")
                } catch (legacy: ClassNotFoundException) {
                    manager = Class.forName("android.hardware.input.InputManager")
                }

                input = manager!!.getMethod("getInstance").invoke(null)
                inject =
                    manager!!.getMethod(
                        "injectInputEvent",
                        InputEvent::class.java,
                        Int::class.javaPrimitiveType,
                    )
            }
            val event: InputEvent?
            if (kind == 1) {
                val received = MotionEvent.CREATOR.createFromParcel(data)
                try {
                    motion = normalized(received!!)
                } finally {
                    received!!.recycle()
                }
                event = motion
            } else if (kind == 2) {
                val key = KeyEvent.CREATOR.createFromParcel(data)
                if (key!!.getAction() == KeyEvent.ACTION_DOWN) focusTask(display)
                event = key
            } else throw IllegalArgumentException("Unknown prototype input")
            event!!
                .javaClass
                .getMethod("setDisplayId", Int::class.javaPrimitiveType)
                .invoke(event, display)
            if (!(inject!!.invoke(input, event, 0) as Boolean?)!!)
                throw IllegalStateException("Prototype input rejected")
            reply!!.writeNoException()
        } catch (error: Exception) {
            reply!!.writeException(IllegalStateException("Prototype relay failed", error))
        } finally {
            if (motion != null) motion!!.recycle()
            Binder.restoreCallingIdentity(identity)
        }
        return true
    }

    companion object {
        @JvmField val PROTOCOL: String = "dev.dexdroid.splitprobe.input"
        @JvmField val INPUT: Int = IBinder.FIRST_CALL_TRANSACTION + 100

        @Throws(Exception::class)
        private fun normalized(event: MotionEvent): MotionEvent? {
            val count = event.getPointerCount()
            if (count < 1 || count > 16)
                throw IllegalArgumentException("Invalid prototype contacts")
            val properties = arrayOfNulls<MotionEvent.PointerProperties?>(count)
            val coords = arrayOfNulls<MotionEvent.PointerCoords?>(count)
            for (i in 0 until count) {
                properties[i] = MotionEvent.PointerProperties()
                event.getPointerProperties(i, properties[i])
                coords[i] = MotionEvent.PointerCoords()
                event.getPointerCoords(i, coords[i])
            }
            // A child View's local offsets are not a new display coordinate system.
            // Rebase raw coordinates AND mouse cursor coordinates before reinjection.
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
                    event.getDeviceId(),
                    event.getEdgeFlags(),
                    event.getSource(),
                    event.getFlags(),
                )
            if (event.getActionButton() != 0)
                MotionEvent::class
                    .java!!
                    .getMethod("setActionButton", Int::class.javaPrimitiveType)
                    .invoke(copy, event.getActionButton())
            return copy
        }

        @Throws(Exception::class)
        private fun focusTask(display: Int) {
            val tasks =
                Class.forName("android.app.ActivityTaskManager")
                    .getMethod("getService")
                    .invoke(null)
            val api = Class.forName("android.app.IActivityTaskManager")
            val query =
                if (Build.VERSION.SDK_INT >= 31) "getAllRootTaskInfos" else "getAllStackInfos"
            for (root in (api.getMethod(query).invoke(tasks) as List<*>?)!!) {
                if (root!!.javaClass.getField("displayId").getInt(root) != display) continue
                val ids = root!!.javaClass.getField("taskIds").get(root) as IntArray?
                if (ids != null && ids!!.size > 0) {
                    api.getMethod("setFocusedTask", Int::class.javaPrimitiveType)
                        .invoke(tasks, ids!![ids!!.size - 1])
                    return
                }
            }
            throw IllegalStateException("No focusable task in prototype pane")
        }
    }
}
