package dev.dexdroid

import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.view.*
import android.widget.*
import java.util.ArrayList

/** Shared launcher mode/actions; each menu owns a session-scoped choice, never a display ID. */
class LauncherControls(private val session: SessionService?, context: Context?, pane: Int) {
    private val style: ControllerStyle?
    private var choice: SessionService.AppSelection? = null
    private var modeOrigin: SessionService.AppSelection? = null
    private var modeWorkspace: SplitWorkspace? = null
    private var requestedMode: Int = 0
    private val modes: MutableList<Button?> = ArrayList<Button?>()
    private var changingMode: Boolean = false
    private var closed: Boolean = false
    private var lastMode: Int = -1
    private var revision: Int = 0
    private var changed: Runnable? = Runnable {}
    private var container: LinearLayout? = null

    init {
        style = ControllerStyle(context)
        choice = session!!.beginAppSelection(pane, false)
    }

    fun current(): Boolean {
        return !closed && session!!.currentAppSelection(choice)
    }

    fun close() {
        closed = true
        session!!.finishAppSelection(choice)
    }

    fun addModes(parent: LinearLayout) {
        container = parent
        modes.clear()
        val row = LinearLayout(style!!.context)
        row.setTag("launcher-modes")
        parent.addView(row, LinearLayout.LayoutParams(-1, -2))
        val names = arrayOf<String?>("Single", "Left/right", "Up/down")
        for (i in names!!.indices) {
            val mode = i
            val button = style!!.button(row, names!![i], { mode(mode) })
            button.setTag("launcher-mode-" + i)
            (button.getLayoutParams() as LinearLayout.LayoutParams).height = style!!.dp(48f)
            modes.add(button)
        }
    }

    fun onChanged(callback: Runnable?) {
        changed = callback
        refresh()
    }

    private fun mode(): Int {
        return if (session!!.split == null) 0 else if (session!!.split!!.state().vertical) 2 else 1
    }

    private fun mode(requested: Int) {
        if (
            (!current() ||
                session!!.busy ||
                session!!.awaitingConfirmation() ||
                session!!.id < 0 ||
                ((requested == mode() &&
                    (session!!.split == null || session!!.split!!.state().maximized < 0))))
        )
            return
        revision++
        modeOrigin = choice
        requestedMode = requested
        session!!.finishAppSelection(choice)
        choice = null
        if (requested == 0) session!!.split!!.end()
        else if (session!!.split == null) session!!.enableSplit(requested == 2)
        else session!!.split!!.direction(requested == 2)
        modeWorkspace = if (requested == 0) null else session!!.split
        changingMode = true
        refresh()
    }

    fun refresh() {
        if (closed) return
        var completed = false
        if (changingMode && !session!!.busy) {
            changingMode = false
            if (
                (session!!.split == modeWorkspace &&
                    mode() == requestedMode &&
                    (session!!.split == null || session!!.split!!.state().maximized < 0))
            )
                choice = session!!.resumeAppSelection(modeOrigin)
            modeOrigin = null
            completed = true
        }
        val mode = mode()
        for (i in modes.indices) {
            val button = modes.get(i)
            val selected = i == mode
            if (button!!.isSelected() != selected) {
                button!!.setTextColor(if (selected) Color.BLACK else Color.WHITE)
                button!!.setBackground(
                    style!!.ripple(style!!.surface(if (selected) 240 else 20, 10, 45))
                )
            }
            button!!.setSelected(selected)
            button!!.setContentDescription(
                button!!.getText()!!.toString() + (if (selected) ", selected" else ", off")
            )
            button!!.setEnabled(
                (current() &&
                    !session!!.busy &&
                    !session!!.awaitingConfirmation() &&
                    session!!.id >= 0)
            )
        }
        if (mode != lastMode || completed) {
            lastMode = mode
            changed!!.run()
            if (container != null) {
                container!!.requestLayout()
                container!!.invalidate()
            }
        }
    }

    private fun pane(position: Int): Int {
        return if (session!!.split == null) 0
        else session!!.split!!.state().paneAtPosition(position)
    }

    fun preview(position: Int, pressed: Boolean) {
        if (!current()) return
        if (pressed && !session!!.busy && !session!!.awaitingConfirmation()) {
            choice = session!!.beginAppSelection(pane(position), false)
        }
        session!!.previewAppSelection(choice, pressed)
    }

    fun launch(app: ComponentName?, rotation: Int?, position: Int): Boolean {
        if (!current() || session!!.busy || session!!.awaitingConfirmation()) {
            session!!.report("App destination changed. Reopen Apps or wait for the display.")
            return false
        }
        // Accessibility/keyboard clicks also capture an explicit current spatial destination.
        if (choice!!.pane != pane(position))
            choice = session!!.beginAppSelection(pane(position), false)
        return session!!.launchSelection(choice, app, rotation)
    }

    fun appRow(app: AppCatalog.Entry, orientation: String?, success: Runnable?): View {
        val rowRevision = revision
        val label = style!!.label(app.name + "\n" + orientation, 14)
        style!!.appCard(label, app.icon)
        if (!session!!.splitEnabled()) {
            label.setTag("app." + app.component!!.getPackageName())
            return label
        }
        val row = LinearLayout(style!!.context)
        row.setTag("app." + app.component!!.getPackageName())
        row.setGravity(Gravity.CENTER_VERTICAL)
        row.setBackground(style!!.surface(18, 8, 40))
        label.setBackgroundColor(Color.TRANSPARENT)
        label.setMaxLines(3)
        row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
        val vertical = session!!.split!!.state().vertical
        for (i in 0..1) {
            val position = i
            val target =
                if (vertical) (if (i == 0) "Up" else "Down") else (if (i == 0) "Left" else "Right")
            val button =
                style!!.button(
                    row,
                    target,
                    {
                        if ((rowRevision == revision && launch(app.component, null, position)))
                            success!!.run()
                    },
                    DestinationButton(style!!.context, position),
                )
            button.setTag("launch." + app.component!!.getPackageName() + "." + i)
            button.setContentDescription(
                "Open " + app.name + " " + target.lowercase(java.util.Locale.ROOT!!)
            )
            val lp = button.getLayoutParams() as LinearLayout.LayoutParams?
            lp!!.width = style!!.dp(56f)
            lp!!.height = style!!.dp(56f)
            lp!!.weight = 0f
        }
        return row
    }

    private inner class DestinationButton(context: Context?, private val position: Int) :
        Button(context) {
        private val rowRevision: Int
        private val ready: Boolean

        init {
            rowRevision = revision
            ready = true
        }

        public override fun setPressed(pressed: Boolean) {
            super.setPressed(pressed)
            if (ready && rowRevision == revision) preview(position, pressed)
        }
    }
}
