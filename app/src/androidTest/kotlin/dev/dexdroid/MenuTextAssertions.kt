package dev.dexdroid

import android.view.*
import android.widget.TextView

/** Checks decoded, displayed text rather than source strings or click behavior. */
internal class MenuTextAssertions {
    companion object {
        private val BROKEN: Array<String?>? =
            arrayOf<String?>(
                "\ufffd",
                "\u00c2\u00b0",
                "\u00c2\u00b7",
                "\u00c3\u2014",
                "\u00e2\u20ac",
                "\u00c3\u00a2",
                "\u00ef\u00bf\u00bd",
            )
        private val SYMBOLS: String = "\u00b0\u00b7\u00d7\u2026\u203a\u2022\u2014\u21e7\u232b"

        private fun clean(value: CharSequence?) {
            if (value == null) return
            val text = value!!.toString()
            for (broken in BROKEN!!) if (text.contains(broken!!))
                throw AssertionError("Corrupted displayed text: " + text)
        }

        @JvmStatic
        fun check(view: View): Int {
            var count = 0
            clean(view.getContentDescription())
            if (view is TextView) {
                val text = view as TextView
                clean(text!!.getText())
                clean(text!!.getHint())
                val value =
                    text!!.getText()!!.toString() + java.lang.String.valueOf(text!!.getHint())
                for (i in 0 until SYMBOLS.length) {
                    val glyph = SYMBOLS.substring(i, i + 1)
                    if (value!!.contains(glyph) && !text!!.getPaint()!!.hasGlyph(glyph))
                        throw AssertionError(
                            "Missing font glyph U+" + Integer.toHexString(glyph.get(0).toInt())
                        )
                }
                count++
            }
            if (view is ViewGroup) {
                val group = view as ViewGroup
                for (i in 0 until group!!.getChildCount()) count += check(group!!.getChildAt(i)!!)
            }
            return count
        }

        @JvmStatic
        fun contains(view: View?, exact: String?): Boolean {
            if (view is TextView && (view as TextView).getText()!!.toString().contains(exact!!))
                return true
            if (view is ViewGroup) {
                val group = view as ViewGroup?
                for (i in 0 until group!!.getChildCount()) if (
                    contains(group!!.getChildAt(i), exact)
                )
                    return true
            }
            return false
        }
    }
}
