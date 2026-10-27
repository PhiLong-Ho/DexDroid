package dev.dexdroid

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Presentation only. Monitor hit policy deliberately excludes this entire view. */
internal class LaunchNotice
@JvmOverloads
constructor(context: Context, private val style: ControllerStyle = ControllerStyle(context)) :
    LinearLayout(context) {
    private val spinner = ProgressBar(context)
    private val text: TextView = style.label("", 14)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = false
        isFocusable = false
        setPadding(style.dp(12f), style.dp(10f), style.dp(12f), style.dp(10f))
        background = style.surface(8, 8, 45)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        spinner.indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
        spinner.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(
            spinner,
            LayoutParams(style.dp(18f), style.dp(18f)).apply { marginEnd = style.dp(10f) },
        )
        text.maxLines = 2
        text.ellipsize = TextUtils.TruncateAt.END
        addView(text, LayoutParams(0, -2, 1f))
        visibility = View.GONE
    }

    fun render(message: String?, pending: Boolean) {
        if (text.text.toString() != message.orEmpty()) text.text = message.orEmpty()
        contentDescription = message
        spinner.visibility = if (pending) View.VISIBLE else View.GONE
        visibility = if (message == null) View.GONE else View.VISIBLE
    }
}
