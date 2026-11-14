package dev.dexdroid

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch

/** Shared optional visibility setting and explicit recovery with current state. */
internal class PhoneScreenPanel(
    private val session: SessionService,
    style: ControllerStyle,
    parent: LinearLayout,
    prefix: String,
) {
    private val row = style.settingRow(parent, R.drawable.ic_phone_screen_off)
    private val toggle = Switch(parent.context)
    private val status = style.label("", 12)
    private val recovery = style.settingRow(parent, R.drawable.ic_phone_screen_on)
    private val retry = style.settingRow(parent, R.drawable.ic_reset)
    private var updating = false

    init {
        row.tag = "$prefix-phone-screen-setting"
        val labels = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(style.label("Show phone screen button", 14))
        status.setTextColor(Color.rgb(155, 155, 155))
        status.setPadding(0, style.dp(4f), 0, 0)
        status.tag = "$prefix-phone-screen-status"
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        labels.addView(status)
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        toggle.tag = "$prefix-phone-screen-switch"
        toggle.contentDescription = "Show phone screen button"
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        toggle.thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.GRAY))
        toggle.trackTintList =
            ColorStateList(states, intArrayOf(Color.rgb(95, 95, 95), Color.rgb(45, 45, 45)))
        row.addView(toggle, LinearLayout.LayoutParams(style.dp(52f), style.dp(48f)))
        row.setOnClickListener { toggle.toggle() }
        toggle.setOnCheckedChangeListener { _, value ->
            if (!updating) session.setPhoneScreenButtonEnabled(value)
        }
        recovery.tag = "$prefix-phone-screen-restore"
        recovery.addView(style.label("Turn phone screen on", 14))
        recovery.setOnClickListener { session.restorePhoneScreen() }
        retry.tag = "$prefix-phone-screen-retry"
        retry.addView(style.label("Retry phone screen control", 14))
        retry.setOnClickListener { session.retryPhoneScreen() }
        refresh()
    }

    fun refresh() {
        updating = true
        toggle.isChecked = session.phoneScreenButtonEnabled()
        updating = false
        val current = session.phoneScreenStatus()
        val text =
            (if (toggle.isChecked) "Button On · " else "Button Off · ") +
                session.phoneScreenExplanation()
        if (status.text.toString() != text) status.text = text
        recovery.visibility = if (current.possibleOff) View.VISIBLE else View.GONE
        recovery.isEnabled = !current.restoring || !current.pending
        retry.visibility =
            if (
                toggle.isChecked &&
                    !current.possibleOff &&
                    !current.pending &&
                    current.result.state in
                        listOf(PhoneScreenResult.State.UNKNOWN, PhoneScreenResult.State.UNAVAILABLE)
            )
                View.VISIBLE
            else View.GONE
    }
}
