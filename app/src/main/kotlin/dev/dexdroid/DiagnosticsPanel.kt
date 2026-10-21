package dev.dexdroid

import android.app.ActivityOptions
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.view.Display
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** Shared, passive view of service-owned history. No new probes or background collection. */
internal class DiagnosticsPanel(
    private val session: SessionService,
    private val style: ControllerStyle,
    parent: LinearLayout,
    private val prefix: String,
) {
    private val context = parent.context
    private val current = style.label("", 14)
    private val history = style.label("", 14)
    private val feedback = style.label("", 14)

    init {
        parent.addView(style.label("CURRENT CONTEXT", 12))
        current.tag = "$prefix-diagnostic-context"
        parent.addView(current)
        parent.addView(style.label("Current state is separate from the events below.", 12))
        action(parent, "Copy log", "Copy the current context and recorded events", "copy") {
            try {
                val report = session.diagnosticText()
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("DexDroid diagnostics", report))
                feedback.setText(R.string.diagnostic_copied)
            } catch (_: RuntimeException) {
                feedback.setText(R.string.diagnostic_copy_failed)
            }
        }
        action(parent, "Share log", "Opens Android sharing on the phone", "share") {
            try {
                val report = session.diagnosticText()
                val send =
                    Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, report)
                context.startActivity(
                    Intent.createChooser(send, "Share diagnostic log")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    ActivityOptions.makeBasic()
                        .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                        .toBundle(),
                )
                feedback.setText(R.string.diagnostic_share_opened)
            } catch (_: RuntimeException) {
                feedback.setText(R.string.diagnostic_share_failed)
            }
        }
        action(
            parent,
            "Clear log",
            "Remove recorded events; new events can still appear",
            "clear",
        ) {
            session.clearDiagnostics()
            history.clearFocus()
            history.text = session.diagnosticEvents()
            feedback.setText(R.string.diagnostic_cleared)
        }
        feedback.tag = "$prefix-diagnostic-feedback"
        feedback.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        parent.addView(feedback)
        parent.addView(style.label("EVENTS · LATEST 250 · MEMORY ONLY", 12))
        parent.addView(
            style.label(
                "Raw names, typed text and uncontrolled error details are omitted. Key sent means dispatch completed, not proof an app handled it.",
                12,
            )
        )
        history.tag = "$prefix-diagnostic-text"
        history.typeface = Typeface.MONOSPACE
        history.setTextIsSelectable(true)
        parent.addView(history)
        refresh()
    }

    private fun action(
        parent: LinearLayout,
        title: String,
        detail: String,
        tag: String,
        run: () -> Unit,
    ) {
        val row = style.settingRow(parent)
        row.tag = "$prefix-log-$tag"
        row.isFocusable = true
        row.contentDescription = title
        val labels = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(style.label(title, 14))
        labels.addView(style.label(detail, 12))
        row.addView(labels, LinearLayout.LayoutParams(-1, -2))
        row.setOnClickListener { run() }
    }

    fun refresh() {
        val report = session.diagnosticContext()
        if (current.text.toString() != report) current.text = report
        val events = session.diagnosticEvents()
        if (
            history.text.toString() == events ||
                (history.selectionStart >= 0 && history.selectionStart != history.selectionEnd)
        )
            return
        var ancestor = history.parent
        while (ancestor != null && ancestor !is ScrollView) ancestor = ancestor.parent
        val scroll = ancestor as? ScrollView
        val position = scroll?.scrollY ?: 0
        history.text = events
        if (scroll != null) style.restoreScroll(scroll, position)
    }
}
