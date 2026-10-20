package dev.dexdroid

import android.view.View
import android.view.Window
import android.view.WindowInsets

/** Keep native phone controls clear of system bars, cutouts and the Android IME. */
internal class ScreenInsets {
    companion object {
        @JvmStatic
        fun apply(window: Window, content: View) {
            val left = content.getPaddingLeft()
            val top = content.getPaddingTop()
            val right = content.getPaddingRight()
            val bottom = content.getPaddingBottom()
            window.setDecorFitsSystemWindows(false)
            content.setOnApplyWindowInsetsListener({ view, insets ->
                val bars =
                    insets!!.getInsets(
                        (WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    )
                val keyboard = insets!!.getInsets(WindowInsets.Type.ime())
                view!!.setPadding(
                    left + bars.left,
                    top + bars.top,
                    right + bars.right,
                    bottom + Math.max(bars.bottom, keyboard.bottom),
                )
                insets
            })
            content.post(Runnable({ content.requestApplyInsets() }))
        }
    }
}
