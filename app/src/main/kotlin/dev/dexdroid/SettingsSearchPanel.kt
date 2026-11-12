package dev.dexdroid

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/** Per-menu view bindings. Filtering preserves native controls and their live updaters. */
internal class SettingsSearchPanel(
    private val style: ControllerStyle,
    val editor: EditText,
    host: LinearLayout,
    private val scroll: ScrollView,
    prefix: String,
    private val onQuery: (String) -> Unit,
) {
    private data class Binding(val id: String, val view: View, val section: View?)

    private val bindings = mutableListOf<Binding>()
    private val sections = mutableListOf<View>()
    private var currentSection: View? = null
    private var active = true
    private val clear =
        style.menuIconAction(
            R.drawable.ic_close,
            "Clear settings search",
            {
                editor.setText("")
                editor.requestFocus()
            },
        )
    private val feedback = style.label("", 12)
    private val watcher: TextWatcher

    init {
        style.versionInfo(host)
        val row =
            LinearLayout(host.context).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        host.addView(row, LinearLayout.LayoutParams(-1, -2))
        style.search(editor)
        editor.tag = "$prefix-settings-search"
        editor.hint = "Search settings"
        editor.contentDescription = "Search settings"
        editor.filters = arrayOf(android.text.InputFilter.LengthFilter(200))
        val icon = host.context.getDrawable(R.drawable.ic_search)!!.mutate()
        icon.setBounds(0, 0, style.dp(20f), style.dp(20f))
        editor.setCompoundDrawables(icon, null, null, null)
        editor.compoundDrawablePadding = style.dp(8f)
        editor.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        row.addView(editor, LinearLayout.LayoutParams(0, -2, 1f))
        clear.tag = "$prefix-settings-clear"
        row.addView(clear, LinearLayout.LayoutParams(style.dp(48f), style.dp(48f)))
        feedback.tag = "$prefix-settings-results"
        feedback.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        host.addView(feedback, LinearLayout.LayoutParams(-1, -2))
        watcher =
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!active) return
                    onQuery(editor.text.toString())
                    filter()
                    scroll.scrollTo(0, 0)
                }

                override fun afterTextChanged(s: Editable?) {}
            }
        editor.addTextChangedListener(watcher)
        editor.setOnEditorActionListener { _, _, _ -> true }
    }

    fun section(view: View) {
        currentSection = view
        sections.add(view)
    }

    fun bind(id: String, view: View) {
        val entry = SettingsSearch.entries.first { it.id == id }
        view.setTag(R.id.settings_search_id, entry.id)
        bindings.add(Binding(id, view, currentSection))
    }

    fun restore(query: String) {
        editor.setText(query)
        editor.setSelection(editor.length())
        filter()
    }

    private fun filter() {
        val query = editor.text.toString()
        var count = 0
        for (binding in bindings) {
            val matches = SettingsSearch.matches(binding.id, query)
            binding.view.visibility = if (matches) View.VISIBLE else View.GONE
            if (matches) count++
        }
        for (section in sections) section.visibility =
            if (bindings.any { it.section === section && it.view.visibility == View.VISIBLE })
                View.VISIBLE
            else View.GONE
        clear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
        feedback.visibility = if (query.isNotBlank()) View.VISIBLE else View.GONE
        feedback.text =
            if (count == 0) "No matching settings. Try another term or clear the search."
            else if (count == 1) "1 setting" else "$count settings"
    }

    fun close() {
        active = false
        editor.removeTextChangedListener(watcher)
        editor.setOnEditorActionListener(null)
        clear.setOnClickListener(null)
    }
}
