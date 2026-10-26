package dev.dexdroid

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.view.Display
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Shared one-time setup. The service owns snapshots and restoration; views own no launch state. */
internal class WorkspaceShortcutPanel(
    private val session: SessionService,
    private val style: ControllerStyle,
    parent: LinearLayout,
    private val prefix: String,
) {
    private val current = style.label("", 14)
    private val saved = style.label("", 14)
    private val status = style.label("", 14)
    private val saveTitle = style.label("Save current workspace", 14)
    private val save: View
    private val pin: View
    private val clear: View

    init {
        parent.addView(style.label("CURRENT WORKSPACE", 12))
        current.tag = "$prefix-workspace-current"
        parent.addView(current)
        save =
            action(parent, saveTitle, "Capture the current apps, layout and rotation", "save") {
                session.workspaceShortcut.save()
            }
        parent.addView(style.label("SAVED WORKSPACE", 12))
        saved.tag = "$prefix-workspace-saved"
        parent.addView(saved)
        pin =
            action(
                parent,
                style.label("Add to phone Home screen", 14),
                "Phone launcher may ask for confirmation",
                "pin",
            ) {
                val context = parent.context
                if (prefix == "phone" && context is Activity) session.workspaceShortcut.pin(context)
                else
                    try {
                        context.startActivity(
                            Intent(context, WorkspaceShortcutActivity::class.java)
                                .setAction(WorkspaceShortcut.PIN)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            ActivityOptions.makeBasic()
                                .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                                .toBundle(),
                        )
                    } catch (_: RuntimeException) {
                        session.workspaceShortcut.pinOpenFailed()
                    }
            }
        clear =
            action(
                parent,
                style.label("Clear saved workspace", 14),
                "Cancel pending restoration; keep the current arrangement",
                "clear",
            ) {
                session.workspaceShortcut.clear()
            }
        val warning =
            style.label(
                "Restoring replaces the current monitor arrangement, including panes saved as empty. Apps reopen or resume normally; their internal pages and unsaved content are not saved. Resolution/DPI follow the connected monitor profile. Audio and scroll settings stay independent.",
                12,
            )
        warning.tag = "$prefix-workspace-warning"
        parent.addView(warning)
        status.tag = "$prefix-workspace-status"
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        parent.addView(status)
        refresh()
    }

    private fun action(
        parent: LinearLayout,
        title: TextView,
        detail: String,
        key: String,
        run: () -> Unit,
    ): View {
        val row = style.settingRow(parent)
        row.tag = "$prefix-workspace-$key"
        row.isFocusable = true
        val labels = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(title)
        labels.addView(style.label(detail, 12))
        row.addView(labels, LinearLayout.LayoutParams(-1, -2))
        row.setOnClickListener { run() }
        return row
    }

    private fun text(view: TextView, value: String) {
        if (view.text.toString() != value) view.text = value
    }

    private fun enabled(view: View, value: Boolean) {
        view.isEnabled = value
        view.alpha = if (value) 1f else .5f
    }

    fun refresh() {
        val owner = session.workspaceShortcut
        val snapshot = session.workspaceSnapshot()
        text(
            current,
            snapshot?.let(owner::currentSummary)
                ?: "Start a stable workspace with at least one app. Saving is unavailable during transitions or an unknown app state.",
        )
        text(saved, owner.saved?.summary() ?: "No workspace saved")
        text(
            saveTitle,
            if (owner.saved == null) "Save current workspace" else "Update saved workspace",
        )
        save.contentDescription = saveTitle.text
        pin.contentDescription = "Add to phone Home screen"
        clear.contentDescription = "Clear saved workspace"
        enabled(save, snapshot != null && !owner.busy)
        enabled(pin, owner.saved != null && !owner.busy)
        enabled(clear, owner.saved != null || owner.active)
        text(status, owner.message)
    }
}
