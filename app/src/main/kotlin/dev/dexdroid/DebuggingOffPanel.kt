package dev.dexdroid

import android.app.ActivityOptions
import android.content.Intent
import android.provider.Settings
import android.view.Display
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** One shared confirmation/result page; opening or dismissing never changes settings. */
internal class DebuggingOffPanel(
    private val session: SessionService,
    private val style: ControllerStyle,
    parent: LinearLayout,
    prefix: String,
    cancel: () -> Unit,
) {
    private val result: TextView
    private val confirm: Button
    private val cancelAction: Button

    init {
        parent.addView(
            style.label("Turn off USB debugging, Wireless debugging and Developer options.", 16)
        )
        parent.addView(
            style.label(
                "This stops the DexDroid desktop and may stop Shizuku, computer ADB and other apps using them. " +
                    "Saved pairing and app authorizations are retained. Banking-app acceptance is not guaranteed. " +
                    "Results continue on the phone after the monitor closes.",
                14,
            )
        )
        result = style.label("", 14)
        result.tag = "$prefix-debugging-off-result"
        result.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        parent.addView(result)
        val actions = LinearLayout(parent.context)
        parent.addView(actions)
        cancelAction = style.button(actions, "Cancel", { cancel() })
        cancelAction.tag = "$prefix-debugging-off-cancel"
        confirm = style.button(actions, "Stop & turn off", { session.turnOffDebugging() })
        confirm.tag = "$prefix-debugging-off-confirm"
        val launchFeedback = style.label("", 14)
        launchFeedback.tag = "$prefix-debugging-off-launch-feedback"
        launchFeedback.visibility = View.GONE
        launchFeedback.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        parent.addView(launchFeedback)
        val manual = LinearLayout(parent.context)
        parent.addView(manual)
        style
            .button(
                manual,
                "Open phone settings",
                {
                    try {
                        parent.context.startActivity(
                            Intent(Settings.ACTION_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            ActivityOptions.makeBasic()
                                .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                                .toBundle(),
                        )
                    } catch (_: RuntimeException) {
                        launchFeedback.setText(R.string.debugging_manual_fallback)
                        launchFeedback.visibility = View.VISIBLE
                    }
                },
            )
            .tag = "$prefix-debugging-off-manual"
        parent.addView(style.label(parent.context.getString(R.string.debugging_manual_checks), 14))
        val recovery =
            style.label(parent.context.getString(R.string.debugging_hidden_options_recovery), 14)
        recovery.tag = "$prefix-debugging-off-hidden-recovery"
        recovery.visibility = View.GONE
        val recoveryRow = style.settingRow(parent)
        recoveryRow.tag = "$prefix-debugging-off-recovery-toggle"
        val recoveryTitle = style.label("Use the desktop again", 14)
        recoveryRow.addView(recoveryTitle, LinearLayout.LayoutParams(0, -2, 1f))
        val cue = style.label("Show", 12)
        recoveryRow.addView(cue)
        recoveryRow.isFocusable = true
        recoveryRow.contentDescription = "Use the desktop again"
        recoveryRow.stateDescription = "Collapsed"
        recoveryRow.setOnClickListener {
            val expanded = recovery.visibility != View.VISIBLE
            recovery.visibility = if (expanded) View.VISIBLE else View.GONE
            cue.text = if (expanded) "Hide" else "Show"
            recoveryRow.stateDescription = if (expanded) "Expanded" else "Collapsed"
        }
        parent.addView(recovery)
        refresh()
    }

    fun refresh() {
        result.text = session.debuggingOffMessage
        confirm.isEnabled = !session.debuggingOffPending()
        cancelAction.isEnabled = !session.debuggingOffPending()
        confirm.text =
            if (session.debuggingOffPending()) "Turning off..."
            else if (session.id >= 0) "Stop & turn off" else "Turn off"
    }
}
