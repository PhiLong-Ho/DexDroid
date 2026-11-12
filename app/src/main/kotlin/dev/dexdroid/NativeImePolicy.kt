package dev.dexdroid

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.VirtualDisplay
import android.os.Binder
import android.os.Build
import android.os.Process
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** All window-manager calls, including cleanup, belong to this disposable display worker. */
internal class NativeImePolicy(
    private val context: Context,
    private val display: VirtualDisplay,
    private val nativeKeyboardFocus: Boolean,
    private val current: () -> Boolean,
) {
    val displayId = display.display.displayId
    private var lease: NativeImeLease? = null // worker only
    private val owner = NativeImeOwner(current, { apply(it) }, { lease?.restore(true) })

    fun request(enabled: Boolean, callback: (NativeImeResult) -> Unit) {
        owner.request(enabled, callback)
    }

    private fun apply(enabled: Boolean): NativeImeResult {
        if (!owner.active()) return NativeImeResult(NativeImeResult.State.RETIRED)
        if (!enabled && lease == null) return NativeImeResult(NativeImeResult.State.DEFAULT)
        if (Build.VERSION.SDK_INT < 31)
            return NativeImeResult(
                NativeImeResult.State.UNAVAILABLE,
                NativeImeResult.Reason.LEGACY_ANDROID,
            )
        if (
            context.checkPermission(
                "android.permission.INTERNAL_SYSTEM_WINDOW",
                Process.myPid(),
                Process.myUid(),
            ) != PackageManager.PERMISSION_GRANTED
        )
            return NativeImeResult(
                NativeImeResult.State.UNAVAILABLE,
                NativeImeResult.Reason.PERMISSION_DENIED,
            )
        // Display.FLAG_TRUSTED is different from the virtual-display creation flag.
        if (display.display.flags and (1 shl 7) == 0)
            return NativeImeResult(
                NativeImeResult.State.UNAVAILABLE,
                NativeImeResult.Reason.UNTRUSTED_DISPLAY,
            )
        if (lease == null) {
            val port =
                try {
                    WindowPort()
                } catch (_: ReflectiveOperationException) {
                    return NativeImeResult(
                        NativeImeResult.State.UNAVAILABLE,
                        NativeImeResult.Reason.API_MISSING,
                    )
                }
            lease = NativeImeLease(port)
        }
        if (enabled && Build.VERSION.SDK_INT >= 34 && !nativeKeyboardFocus)
            return NativeImeResult(
                NativeImeResult.State.UNAVAILABLE,
                NativeImeResult.Reason.CREATION_MODE,
            )
        return lease!!.apply(enabled)
    }

    // Authorized shell-only WM capability; missing/restricted APIs return optional unavailability.
    @android.annotation.SuppressLint("PrivateApi")
    private inner class WindowPort : NativeImeLease.Port {
        private val manager =
            Class.forName("android.view.WindowManagerGlobal")
                .getMethod("getWindowManagerService")
                .apply { isAccessible = true }
                .invoke(null)!!
        private val getter =
            manager.javaClass.getMethod("getDisplayImePolicy", Int::class.javaPrimitiveType).apply {
                isAccessible = true
            }
        private val setter =
            manager.javaClass
                .getMethod(
                    "setDisplayImePolicy",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                .apply { isAccessible = true }

        override fun active() = owner.active()

        override fun owned() = owner.owned()

        override fun read() = call(getter, displayId) as Int

        override fun write(policy: Int, retiring: Boolean) {
            check(if (retiring) owner.retiring() else active())
            call(setter, displayId, policy)
        }

        private fun call(method: Method, vararg arguments: Any): Any? {
            check(owned()) { "Keyboard display retired" }
            val identity = Binder.clearCallingIdentity()
            try {
                return method.invoke(manager, *arguments)
            } catch (error: InvocationTargetException) {
                if (error.targetException is SecurityException)
                    throw NativeImePolicyException(NativeImeResult.Reason.PERMISSION_DENIED)
                throw IllegalStateException("Keyboard policy operation failed")
            } finally {
                Binder.restoreCallingIdentity(identity)
            }
        }
    }

    /** No WM IPC on the stopping thread; a blocked worker cannot hold display release. */
    fun retireAndAwait(): Boolean {
        return owner.retireAndAwait()
    }

    fun released() {
        owner.released()
    }

    companion object {
        /**
         * Shell-side static eligibility only: never obtains WM service or reads/writes a policy.
         */
        @android.annotation.SuppressLint("PrivateApi")
        fun staticallyEligible(context: Context): Boolean {
            if (
                Build.VERSION.SDK_INT < 31 ||
                    context.checkPermission(
                        "android.permission.INTERNAL_SYSTEM_WINDOW",
                        Process.myPid(),
                        Process.myUid(),
                    ) != PackageManager.PERMISSION_GRANTED
            )
                return false
            return try {
                val api = Class.forName("android.view.IWindowManager")
                api.getMethod("getDisplayImePolicy", Int::class.javaPrimitiveType)
                api.getMethod(
                    "setDisplayImePolicy",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                true
            } catch (_: ReflectiveOperationException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
    }
}
