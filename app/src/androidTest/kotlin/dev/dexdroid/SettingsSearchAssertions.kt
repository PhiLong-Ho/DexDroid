package dev.dexdroid

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView

/** Observe existing native row targets and feedback, not a synthetic search screen. */
internal object SettingsSearchAssertions {
    fun visible(root: View): Set<String> = buildSet {
        fun visit(view: View) {
            val key = view.getTag(R.id.settings_search_id) as? String
            if (key != null && view.isShown) add(key)
            if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
        }
        visit(root)
    }

    fun query(root: View, prefix: String, query: String, expected: Set<String>) {
        val editor = root.findViewWithTag<EditText>("$prefix-settings-search")
        editor.setText(query)
        require(visible(root) == expected) {
            "$prefix '$query' targets: ${visible(root)}, expected $expected"
        }
        if (query.isNotBlank() && expected.isNotEmpty()) {
            val feedback =
                root.findViewWithTag<TextView>("$prefix-settings-results").text.toString()
            require(
                feedback == if (expected.size == 1) "1 setting" else "${expected.size} settings"
            )
        }
        require(
            root
                .findViewWithTag<View>(
                    if (prefix == "phone") "phone-menu-close" else "monitor-close"
                )
                .isShown
        )
    }

    fun noResults(root: View, prefix: String) {
        require(visible(root).isEmpty())
        require(
            root
                .findViewWithTag<TextView>("$prefix-settings-results")
                .text
                .contains("No matching settings")
        )
        require(root.findViewWithTag<View>("$prefix-settings-clear").isShown)
    }
}
