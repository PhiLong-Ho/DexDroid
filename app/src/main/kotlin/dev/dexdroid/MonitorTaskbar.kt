package dev.dexdroid

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout

/** Reserved monitor chrome; its parent owns output rotation, Fit and modal input. */
class MonitorTaskbar
@JvmOverloads
constructor(
    context: Context,
    openApps: Runnable = Runnable {},
    openControls: Runnable = Runnable {},
) : FrameLayout(context) {
    private val style = ControllerStyle(context)
    private var pixelSize = 0
    private val actions =
        LinearLayout(context).apply {
            tag = "monitor-taskbar-actions"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    init {
        tag = "monitor-taskbar"
        setForceDarkAllowed(false)
        // Empty space is shell-owned too, so it cannot reach an app behind the strip.
        isClickable = true
        background = ColorDrawable(Color.BLACK)
        addView(actions, LayoutParams(LayoutParams.WRAP_CONTENT, 0, Gravity.CENTER))
        addAction("Apps", R.drawable.ic_apps, "monitor-taskbar-apps", openApps)
        addAction("Controls", R.drawable.ic_controls, "monitor-taskbar-controls", openControls)
    }

    private fun addAction(label: String, icon: Int, actionTag: String, action: Runnable) {
        val button = style.menuIconAction(icon, label, action)
        button.tag = actionTag
        button.tooltipText = label
        actions.addView(button, LinearLayout.LayoutParams(0, 0))
    }

    /** The viewport supplies pixels; app density must not alter taskbar glyph or target size. */
    fun configureSize(height: Int) {
        if (height <= 0 || height == pixelSize) return
        pixelSize = height
        val glyph = Math.round(height / 2f)
        val inset = (height - glyph) / 2
        actions.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, height, Gravity.CENTER)
        for (index in 0 until actions.childCount) {
            val button = actions.getChildAt(index) as ImageButton
            button.layoutParams =
                LinearLayout.LayoutParams(height, height).apply {
                    if (index > 0) marginStart = Math.round(height / 6f)
                }
            button.setPadding(inset, inset, height - glyph - inset, height - glyph - inset)
            button.scaleType = ImageView.ScaleType.FIT_CENTER
        }
    }
}
