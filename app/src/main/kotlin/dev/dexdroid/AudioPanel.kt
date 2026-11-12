package dev.dexdroid

import android.view.View
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView

/** Shared phone/monitor picker, including pending and unsupported states. */
internal class AudioPanel(
    private val session: SessionService,
    private val style: ControllerStyle,
    parent: LinearLayout,
    private val prefix: String,
    private val confirmed: () -> Unit,
) {
    private val route: TextView
    private val status: TextView
    private val choices: RadioGroup
    private val rows = linkedMapOf<String, android.widget.RadioButton>()
    private var rendered: List<SessionAudioState.Output>? = null
    private var updating = false
    private var requested: String? = null

    init {
        style.readingHeading(parent, "Session media output", R.drawable.ic_audio)
        style.paragraph(
            parent,
            "Changes this phone's default media output during the desktop session.",
        )
        style.paragraph(parent, "Calls and outputs selected inside apps may differ.")
        route = style.readingLabel("Android media route: Unknown")
        style.readingSpacing(route)
        route.tag = "$prefix-audio-route"
        parent.addView(route)
        status = style.readingLabel("")
        style.readingSpacing(status)
        status.tag = "$prefix-audio-status"
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        parent.addView(status)
        choices = RadioGroup(parent.context)
        choices.tag = "$prefix-audio-choices"
        parent.addView(choices)
        choices.setOnCheckedChangeListener { _, id ->
            if (!updating)
                rows.entries
                    .firstOrNull { it.value.id == id }
                    ?.let {
                        requested = it.key
                        session.chooseAudio(it.key)
                    }
        }
        val actions = LinearLayout(parent.context)
        parent.addView(actions, LinearLayout.LayoutParams(-1, -2))
        style.button(actions, "Refresh outputs", { session.refreshAudio() }).tag =
            "$prefix-audio-refresh"
        refresh()
        session.refreshAudio()
    }

    fun refresh() {
        val state = session.audioState()
        route.text = route.context.getString(R.string.audio_route_format, state.route)
        status.text = state.message
        updating = true
        if (rendered != state.outputs) {
            val focused = rows.entries.firstOrNull { it.value.hasFocus() }?.key
            rendered = state.outputs
            choices.removeAllViews()
            rows.clear()
            val system =
                style.choice(
                    choices,
                    "System default\nUse Android's media output setting",
                    "$prefix-audio-system",
                    View.generateViewId(),
                )
            rows[""] = system
            for (output in state.outputs) {
                rows[output.key] =
                    style.choice(
                        choices,
                        "${output.name}\n${output.kind}",
                        "$prefix-audio-${output.key}",
                        View.generateViewId(),
                    )
            }
            focused?.let { rows[it]?.requestFocus() }
        }
        rows.forEach { (key, row) ->
            row.isEnabled = state.available && !state.pending
            row.isChecked = key == state.selected && !state.uncertain
        }
        updating = false
        if (requested != null && !state.pending && state.confirmed && requested == state.selected) {
            requested = null
            route.post { confirmed() }
        }
    }
}
