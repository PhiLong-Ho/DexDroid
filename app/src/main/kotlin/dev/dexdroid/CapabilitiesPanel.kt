package dev.dexdroid

import android.view.View
import android.widget.LinearLayout

/** Shared passive report; all work and observations belong to the service. */
internal class CapabilitiesPanel(
    private val session: SessionService,
    private val style: ControllerStyle,
    parent: LinearLayout,
    private val prefix: String,
    diagnostics: () -> Unit,
) {
    private val lease = session.openCapabilityReport()
    private val feedback = reportText("", 12)
    private val rows = mutableListOf<Pair<android.widget.TextView, android.widget.TextView>>()
    private val refresh: View

    init {
        parent.addView(
            reportText(
                "Current prerequisites and observations. These do not guarantee every app will work.",
                12,
            )
        )
        refresh =
            action(
                parent,
                "Refresh",
                "Read current metadata without changing the desktop",
                "refresh",
            ) {
                session.refreshCapabilityReport()
            }
        action(
            parent,
            "Diagnostic log",
            "Copy or share this report and recorded events",
            "diagnostics",
            diagnostics,
        )
        feedback.tag = "$prefix-capability-feedback"
        feedback.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        parent.addView(feedback)
        repeat(6) { index ->
            val heading = reportText("", 14).apply { tag = "$prefix-capability-title-$index" }
            val detail = reportText("", 12).apply { tag = "$prefix-capability-detail-$index" }
            parent.addView(heading)
            parent.addView(detail)
            rows.add(heading to detail)
        }
        refresh()
    }

    /** Keep passive text clear of the native scrollbar without moving action targets. */
    private fun reportText(value: String, size: Int) =
        style.label(value, size).apply {
            setPadding(paddingLeft, paddingTop, paddingRight + style.dp(8f), paddingBottom)
        }

    private fun action(
        parent: LinearLayout,
        title: String,
        detail: String,
        key: String,
        run: () -> Unit,
    ): View {
        val row = style.settingRow(parent)
        row.tag = "$prefix-capability-$key"
        row.contentDescription = title
        row.isFocusable = true
        val labels = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(style.label(title, 14))
        labels.addView(style.label(detail, 12))
        row.addView(labels, LinearLayout.LayoutParams(-1, -2))
        row.setOnClickListener { run() }
        return row
    }

    fun refresh() {
        session.capabilityReport().items.forEachIndexed { index, item ->
            val (heading, detail) = rows[index]
            val title = "${item.title} · ${item.verdict.label}"
            if (heading.text.toString() != title) heading.text = title
            if (detail.text.toString() != item.detail) detail.text = item.detail
        }
        val message = session.capabilityReportFeedback()
        if (feedback.text.toString() != message) feedback.text = message
        refresh.isEnabled = !session.capabilityReportRefreshing()
    }

    fun close() {
        session.closeCapabilityReport(lease)
    }
}
