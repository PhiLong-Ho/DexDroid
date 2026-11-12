package dev.dexdroid

import android.view.Window
import android.view.WindowManager

/** Owns only the physical host's focusable bit; menus and teardown release the lease. */
internal class NativeImeHostFocus {
    private var window: Window? = null
    private var priorNotFocusable = false

    fun update(current: Window?, passive: Boolean) {
        if (current !== window || !passive) release()
        if (!passive || current == null) return
        if (window == null) {
            window = current
            priorNotFocusable =
                current.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0
        }
        if (current.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0)
            current.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    fun release() {
        val old = window ?: return
        window = null
        if (
            !priorNotFocusable &&
                old.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0
        )
            old.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }
}
