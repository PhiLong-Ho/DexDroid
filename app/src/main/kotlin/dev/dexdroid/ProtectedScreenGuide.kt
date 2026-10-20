package dev.dexdroid

import android.widget.*

/** Shared, offline instructions. No browser launch or configuration changes. */
internal class ProtectedScreenGuide {
    companion object {
        @JvmField val TITLE: String = "Protected screen help"
        @JvmField
        val TEXT: String? =
            ("Why the screen is blank\n" +
                "Some apps protect individual windows from screenshots and external capture. " +
                "DexDroid cannot show those windows through its current display connection. " +
                "This can include Chrome incognito, but detection does not identify private browsing.\n\n" +
                "Optional Chrome workaround\n" +
                "1. Open a normal Chrome tab on the phone.\n" +
                "2. Enter chrome://flags/#incognito-screenshot\n" +
                "3. Set Incognito Screenshot to Enabled, then tap Relaunch.\n" +
                "4. Reopen Chrome through DexDroid and test a new incognito tab.\n\n" +
                "Privacy consequence\n" +
                "This allows incognito screenshots and thumbnails throughout Chrome, not only in DexDroid. " +
                "DexDroid never changes this setting for you.\n\n" +
                "Undo\n" +
                "Return to the same flag, choose Default, then Relaunch.\n\n" +
                "The flag may be missing or managed by your organization. This workaround is not guaranteed. " +
                "Other protected apps may have no supported workaround. A black screen alone is not proof of protection.")

        @JvmStatic
        fun add(style: ControllerStyle, parent: LinearLayout, prefix: String?) {
            val text = style.label(TEXT, 14)
            text.setTag(prefix!! + "-protected-guide")
            text.setTextIsSelectable(true)
            parent.addView(text)
        }
    }
}
