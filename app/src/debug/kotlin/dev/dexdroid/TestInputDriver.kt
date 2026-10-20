package dev.dexdroid

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.*
import android.view.*
import java.lang.reflect.Method
import rikka.shizuku.ShizukuApiConstants

/** Shell-only test input, isolated from product code and absent from release artifacts. */
@android.annotation.SuppressLint("PrivateApi")
class TestInputDriver : Binder() {
    private var context: Context? = null
    private var input: Any? = null
    private var inject: Method? = null

    @Throws(Exception::class)
    private fun init() {
        if (context != null) return
        if (android.os.Process.myUid() != 2000)
            throw SecurityException("Test input requires authorized shell")
        if (Looper.myLooper() == null) Looper.prepare()
        val thread = Class.forName("android.app.ActivityThread")
        context =
            thread.getMethod("getSystemContext").invoke(thread.getMethod("systemMain").invoke(null))
                as Context?
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

    @Synchronized
    @Throws(RemoteException::class)
    protected override fun onTransact(
        code: Int,
        data: Parcel,
        reply: Parcel?,
        flags: Int,
    ): Boolean {
        if (code == ShizukuApiConstants.USER_SERVICE_TRANSACTION_destroy) {
            System.exit(0)
            return true
        }
        if (code != IBinder.FIRST_CALL_TRANSACTION)
            return super.onTransact(code, data, reply, flags)
        data.enforceInterface(DESCRIPTOR)
        val target = data.readInt()
        val kind = data.readInt()
        val identity = Binder.clearCallingIdentity()
        var motion: MotionEvent? = null
        try {
            init()
            val display =
                context!!
                    .getSystemService<DisplayManager?>(DisplayManager::class.java!!)!!
                    .getDisplay(target)
            if (
                (display == null ||
                    target == Display.DEFAULT_DISPLAY ||
                    display!!.getName()!!.contains("DexDroid") ||
                    display!!.getName()!!.contains("PortraitDesk") ||
                    (display!!.getFlags() and Display.FLAG_PRESENTATION) == 0)
            )
                throw SecurityException(
                    "Test input target must be an external presentation monitor"
                )
            val event: InputEvent?
            if (kind == 1 || kind == 3) {
                motion = MotionEvent.CREATOR.createFromParcel(data)
                if (
                    (!motion!!.isFromSource(
                        if (kind == 1) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_MOUSE
                    ) || motion!!.getPointerCount() < 1 || motion!!.getPointerCount() > 16)
                )
                    throw IllegalArgumentException("Invalid test monitor touch")
                event = motion
            } else if (kind == 2) {
                event = KeyEvent.CREATOR.createFromParcel(data)
                if (!event!!.isFromSource(InputDevice.SOURCE_KEYBOARD))
                    throw IllegalArgumentException("Invalid test keyboard source")
            } else throw IllegalArgumentException("Invalid test event kind")
            event!!
                .javaClass
                .getMethod("setDisplayId", Int::class.javaPrimitiveType)
                .invoke(event, target)
            val accepted = (inject!!.invoke(input, event, 2) as Boolean?)!!
            reply!!.writeNoException()
            reply!!.writeInt(if (accepted!!) 1 else 0)
        } catch (error: Exception) {
            reply!!.writeException(IllegalStateException("Shell test input failed", error))
        } finally {
            if (motion != null) motion!!.recycle()
            Binder.restoreCallingIdentity(identity)
        }
        return true
    }

    companion object {
        private val DESCRIPTOR: String = "dev.dexdroid.TestInputDriver"
    }
}
