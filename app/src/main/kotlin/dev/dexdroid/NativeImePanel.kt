package dev.dexdroid

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch

/** Shared current-value control. Policy configuration never claims that a keyboard rendered. */
internal class NativeImePanel(
    private val session: SessionService,
    style: ControllerStyle,
    parent: LinearLayout,
    prefix: String,
) {
    private val row = style.settingRow(parent)
    private val toggle = Switch(parent.context)
    private val status = style.label("", 12)
    private val retry = style.settingRow(parent)
    private var updating = false

    init {
        row.tag = "$prefix-native-keyboard"
        val labels = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(style.label("Android keyboard on monitor", 14))
        status.setTextColor(Color.rgb(155, 155, 155))
        status.setPadding(0, style.dp(4f), 0, 0)
        status.tag = "$prefix-native-keyboard-status"
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        labels.addView(status)
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        toggle.tag = "$prefix-native-keyboard-switch"
        toggle.contentDescription = "Android keyboard on monitor"
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        toggle.thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.GRAY))
        toggle.trackTintList =
            ColorStateList(states, intArrayOf(Color.rgb(95, 95, 95), Color.rgb(45, 45, 45)))
        row.addView(toggle, LinearLayout.LayoutParams(style.dp(52f), style.dp(48f)))
        row.setOnClickListener { toggle.toggle() }
        toggle.setOnCheckedChangeListener { _, enabled ->
            if (!updating) session.setNativeMonitorKeyboardEnabled(enabled)
        }
        retry.tag = "$prefix-native-keyboard-retry"
        retry.isFocusable = true
        retry.contentDescription = "Retry keyboard placement"
        retry.addView(style.label("Retry keyboard placement", 14))
        retry.setOnClickListener { session.retryNativeKeyboard() }
        refresh()
    }

    fun refresh() {
        updating = true
        toggle.isChecked = session.nativeMonitorKeyboardEnabled()
        updating = false
        val value = session.nativeKeyboardStatus()
        if (status.text.toString() != value) status.text = value
        retry.visibility = if (session.nativeKeyboardCanRetry()) View.VISIBLE else View.GONE
    }
}
