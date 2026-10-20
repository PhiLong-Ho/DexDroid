package dev.dexdroid

import android.content.Context
import android.graphics.Color
import android.view.*
import android.widget.*
import java.util.*
import java.util.function.*

/** The same current-state menu tree on the phone and monitor. */
class LayoutPanel(
    private val session: SessionService?,
    context: Context?,
    parent: LinearLayout?,
    openApps: IntConsumer?,
) {
    private val style: ControllerStyle?
    private val updates: MutableList<Runnable?> = ArrayList<Runnable?>()
    private var refreshing: Boolean = false
    private var dragging: Boolean = false

    init {
        var parent = parent
        style = ControllerStyle(context)
        row(
            parent,
            R.drawable.ic_layout,
            "Split view",
            "layout-toggle",
            {
                if (session!!.splitEnabled()) "On · End keeps pane 1 and closes pane 2"
                else "Off · Use two apps together"
            },
            { true },
            { if (session!!.split == null) session!!.enableSplit() else session!!.split!!.end() },
        )
        val targets = LinearLayout(context)
        targets.setTag("layout-targets")
        parent!!.addView(targets, LinearLayout.LayoutParams(-1, -2))
        for (i in 0..1) {
            val position = i
            val target =
                style!!.button(
                    targets,
                    "Open app",
                    {
                        if (session!!.busy || session!!.awaitingConfirmation()) return@button
                        val state =
                            if (session!!.split == null) SplitLayout()
                            else session!!.split!!.state()
                        openApps!!.accept(if (state.enabled) state.paneAtPosition(position) else 0)
                    },
                )
            target.setTag("layout-target-" + i)
            style!!.buttonIcon(target, R.drawable.ic_apps, Color.WHITE)
            updates.add({
                val state =
                    if (session!!.split == null) SplitLayout() else session!!.split!!.state()
                target.setVisibility(
                    if (position == 0 || state.enabled) View.VISIBLE else View.GONE
                )
                target.setText(state.openLabel(position))
                target.setContentDescription(state.openLabel(position))
                target.setEnabled(
                    (session!!.id >= 0 && !session!!.busy && !session!!.awaitingConfirmation())
                )
                targets.setOrientation(
                    if (state.vertical && state.enabled) LinearLayout.VERTICAL
                    else LinearLayout.HORIZONTAL
                )
                val lp =
                    LinearLayout.LayoutParams(
                        if (state.vertical && state.enabled) -1 else 0,
                        style!!.dp(72f),
                        (if (state.vertical && state.enabled) 0 else 1).toFloat(),
                    )
                lp.setMargins(style!!.dp(3f), style!!.dp(4f), style!!.dp(3f), style!!.dp(4f))
                val old = target.getLayoutParams() as LinearLayout.LayoutParams?
                if (
                    (old!!.width != lp.width ||
                        old!!.height != lp.height ||
                        old!!.weight != lp.weight)
                )
                    target.setLayoutParams(lp)
            })
        }
        val splitControls = LinearLayout(context)
        splitControls.setOrientation(LinearLayout.VERTICAL)
        parent!!.addView(splitControls)
        updates.add({
            splitControls.setVisibility(if (session!!.splitEnabled()) View.VISIBLE else View.GONE)
        })
        parent = splitControls
        row(
            parent,
            R.drawable.ic_rotation,
            "Direction",
            "layout-direction",
            {
                if (session!!.split != null && session!!.split!!.state().vertical) "Top / bottom"
                else "Left / right"
            },
            BooleanSupplier({ session!!.splitEnabled() }),
            { session!!.split!!.direction(!session!!.split!!.state().vertical) },
        )
        val ratio = style!!.label("Split ratio", 14)
        parent!!.addView(ratio)
        val slider = SeekBar(context)
        slider.setTag("layout-ratio")
        slider.setMin(25)
        slider.setMax(75)
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.WHITE))
        slider.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE))
        slider.setContentDescription("Split ratio, 25 to 75 percent")
        parent!!.addView(slider, LinearLayout.LayoutParams(-1, style!!.dp(48f)))
        updates.add({
            val value =
                if (session!!.split == null) 50
                else Math.round(session!!.split!!.state().ratio * 100)
            if (!dragging) slider.setProgress(value)
            slider.setEnabled(
                (session!!.split != null && !session!!.busy && !session!!.awaitingConfirmation())
            )
            if (!dragging)
                ratio.setText(context!!.getString(R.string.split_ratio, value, 100 - value))
        })
        slider.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                public override fun onProgressChanged(
                    view: SeekBar?,
                    progress: Int,
                    user: Boolean,
                ) {
                    if (user) {
                        ratio.setText(
                            context!!.getString(R.string.split_ratio, progress, 100 - progress)
                        )
                        if (!dragging && session!!.split != null)
                            session!!.split!!.ratio(progress / 100f)
                    }
                }

                public override fun onStartTrackingTouch(view: SeekBar?) {
                    dragging = true
                }

                public override fun onStopTrackingTouch(view: SeekBar?) {
                    dragging = false
                    if (session!!.split != null)
                        session!!.split!!.ratio(view!!.getProgress() / 100f)
                    refresh()
                }
            }
        )
        val presets = LinearLayout(context)
        parent!!.addView(presets)
        for (value in floatArrayOf(1f / 3, .5f, 2f / 3)) {
            val button =
                style!!.button(
                    presets,
                    if (value == .5f) "1:1" else if (value < .5f) "1:2" else "2:1",
                    { if (session!!.split != null) session!!.split!!.ratio(value) },
                )
            button.setTag(
                if (value == .5f) "layout-equal"
                else if (value < .5f) "layout-third" else "layout-two-thirds"
            )
            updates.add({
                button.setEnabled(
                    (session!!.split != null &&
                        !session!!.busy &&
                        !session!!.awaitingConfirmation())
                )
            })
        }
        row(
            parent,
            R.drawable.ic_swap,
            "Swap panes",
            "layout-swap",
            { "Exchange app positions" },
            BooleanSupplier({ session!!.splitEnabled() }),
            { session!!.split!!.swap() },
        )
        row(
            parent,
            R.drawable.ic_fullscreen,
            "Fullscreen / restore",
            "layout-maximize",
            {
                if (session!!.split != null && session!!.split!!.state().maximized >= 0)
                    "Restore split view"
                else "Expand selected pane"
            },
            BooleanSupplier({ session!!.splitEnabled() }),
            { session!!.split!!.maximize() },
        )
        row(
            parent,
            R.drawable.ic_close,
            "Close app",
            "layout-close",
            { "Clear selected pane; keep the other app" },
            BooleanSupplier({ session!!.splitEnabled() }),
            { session!!.split!!.closeApp() },
        )
        val hint =
            style!!.label(
                "Workspace orientation applies to both panes. Saved single-view app orientations are retained. Apps may restart when resized.",
                12,
            )
        hint.setTextColor(Color.rgb(155, 155, 155))
        hint.setPadding(0, style!!.dp(8f), 0, style!!.dp(8f))
        parent!!.addView(hint)
        refresh()
    }

    private fun row(
        parent: LinearLayout?,
        icon: Int,
        title: String?,
        tag: String?,
        current: Supplier<String?>?,
        available: BooleanSupplier?,
        action: Runnable?,
    ) {
        val row = style!!.settingRow(parent!!)
        row.setTag(tag)
        val image = ImageView(style!!.context)
        image.setImageResource(icon)
        row.addView(image, LinearLayout.LayoutParams(style!!.dp(24f), style!!.dp(24f)))
        val labels = LinearLayout(style!!.context)
        labels.setOrientation(LinearLayout.VERTICAL)
        labels.setPadding(style!!.dp(12f), 0, 0, 0)
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        labels.addView(style!!.label(title, 14))
        val value = style!!.label("", 12)
        value.setTextColor(Color.rgb(155, 155, 155))
        labels.addView(value)
        updates.add({
            value.setText(current!!.get())
            row.setContentDescription(title + ", " + current!!.get())
            val enabled =
                (available!!.getAsBoolean() && !session!!.busy && !session!!.awaitingConfirmation())
            row.setEnabled(enabled)
            row.setAlpha(if (enabled) 1f else .45f)
        })
        row.setOnClickListener({ view ->
            if (
                (!refreshing &&
                    available!!.getAsBoolean() &&
                    !session!!.busy &&
                    !session!!.awaitingConfirmation())
            ) {
                action!!.run()
                refresh()
            }
        })
    }

    fun refresh() {
        refreshing = true
        try {
            for (update in updates) update!!.run()
        } finally {
            refreshing = false
        }
    }
}
