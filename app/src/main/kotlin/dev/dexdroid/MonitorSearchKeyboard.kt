package dev.dexdroid

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import java.util.ArrayList
import java.util.Locale

/** Local keyboard rendered and rotated with monitor search and profile name editors. */
@android.annotation.SuppressLint(
    "ViewConstructor"
) // Constructed in code with its live editor and Hide callback.
class MonitorSearchKeyboard(
    context: Context?,
    private val editor: EditText?,
    private val hidden: Runnable?,
) : LinearLayout(context) {
    private val style: ControllerStyle?
    private val letters: MutableList<Button?> = ArrayList<Button?>()
    private val rows: MutableList<LinearLayout?> = ArrayList<LinearLayout?>()
    private val shiftButton: Button?
    private var shifted: Boolean = false

    init {
        style = ControllerStyle(context)
        setTag("monitor-search-keyboard")
        setOrientation(LinearLayout.VERTICAL)
        setPadding(0, style!!.dp(4f), 0, 0)
        setVisibility(View.GONE)
        val numbers = row()
        var value = '1'
        while (value <= '9') {
            character(numbers, (value).toString(), (value).toString())
            value++
        }
        character(numbers, "0", "0")
        val top = row()
        for (value in "qwertyuiop".toCharArray()) letter(top, value)
        val middle = row()
        for (value in "asdfghjkl".toCharArray()) letter(middle, value)
        character(middle, "-", "hyphen")
        val lower = row()
        shiftButton = key(lower, "⇧", "shift", 1.5f, { setShifted(!shifted) })
        shiftButton!!.setContentDescription("Shift, off")
        for (value in "zxcvbnm".toCharArray()) letter(lower, value)
        key(lower, "⌫", "delete", 1.5f, Runnable({ this.delete() })).setContentDescription("Delete")
        val actions = row()
        character(actions, ".", "period")
        character(actions, "_", "underscore")
        key(actions, "Space", "space", 3f, Runnable({ this.space() }))
        key(
            actions,
            "Clear",
            "clear",
            1.5f,
            {
                editor!!.setText("")
                editor!!.setSelection(0)
            },
        )
        key(
            actions,
            "Hide",
            "hide",
            1.5f,
            {
                setVisibility(View.GONE)
                hidden!!.run()
            },
        )
    }

    private fun row(): LinearLayout {
        val row = LinearLayout(getContext())
        row.setOrientation(LinearLayout.HORIZONTAL)
        rows.add(row)
        addView(row, LinearLayout.LayoutParams(-1, style!!.dp(38f)))
        return row
    }

    private fun key(
        row: LinearLayout,
        label: String?,
        name: String?,
        weight: Float,
        action: Runnable?,
    ): Button {
        val key = Button(getContext())
        key.setTag("monitor-key-" + name!!)
        key.setText(label)
        key.setTextSize((if (label!!.length > 2) 11 else 14).toFloat())
        key.setTextColor(Color.WHITE)
        key.setAllCaps(false)
        key.setGravity(Gravity.CENTER)
        key.setMinWidth(0)
        key.setMinimumWidth(0)
        key.setMinHeight(0)
        key.setMinimumHeight(0)
        key.setPadding(0, 0, 0, 0)
        key.setFocusable(false)
        key.setFocusableInTouchMode(false)
        key.setBackground(style!!.ripple(style!!.surface(20, 5, 45)))
        key.setContentDescription(label)
        val lp = LinearLayout.LayoutParams(0, -1, weight)
        lp.setMargins(style!!.dp(1f), style!!.dp(2f), style!!.dp(1f), style!!.dp(2f))
        row.addView(key, lp)
        key.setOnClickListener({ v -> action!!.run() })
        return key
    }

    private fun character(row: LinearLayout?, value: String?, name: String?) {
        key(row!!, value, name, 1f, { insert(value) })
    }

    private fun letter(row: LinearLayout?, value: Char) {
        val lower = (value).toString()
        val button =
            key(
                row!!,
                lower,
                lower,
                1f,
                {
                    insert(if (shifted) lower.uppercase(Locale.ROOT!!) else lower)
                    if (shifted) setShifted(false)
                },
            )
        letters.add(button)
    }

    private fun setShifted(enabled: Boolean) {
        shifted = enabled
        for (letter in letters) {
            val value = letter!!.getTag()!!.toString()!!.substring("monitor-key-".length)
            letter!!.setText(if (enabled) value.uppercase(Locale.ROOT!!) else value)
            letter!!.setContentDescription(letter!!.getText())
        }
        shiftButton!!.setTextColor(if (enabled) Color.BLACK else Color.WHITE)
        shiftButton!!.setBackground(
            style!!.ripple(style!!.surface(if (enabled) 245 else 20, 5, 45))
        )
        shiftButton!!.setContentDescription("Shift, " + (if (enabled) "on" else "off"))
    }

    private fun space() {
        insert(" ")
    }

    private fun selectionStart(): Int {
        return Math.max(0, Math.min(editor!!.length(), editor!!.getSelectionStart()))
    }

    private fun selectionEnd(): Int {
        return Math.max(0, Math.min(editor!!.length(), editor!!.getSelectionEnd()))
    }

    private fun insert(value: String?) {
        val text = editor!!.getText()
        val a = selectionStart()
        val b = selectionEnd()
        val start = Math.min(a, b)
        val end = Math.max(a, b)
        val previousLength = text!!.length
        text!!.replace(start, end, value)
        val acceptedLength = text!!.length - previousLength + end - start
        editor!!.setSelection(Math.min(text!!.length, start + Math.max(0, acceptedLength)))
    }

    private fun delete() {
        val text = editor!!.getText()
        val a = selectionStart()
        val b = selectionEnd()
        var start = Math.min(a, b)
        val end = Math.max(a, b)
        if (start == end && start > 0) start = Character.offsetByCodePoints(text!!, start, -1)
        if (start != end) {
            text!!.delete(start, end)
            editor!!.setSelection(start)
        }
    }

    fun showWithin(availableListHeight: Int) {
        val rowHeight =
            Math.min(
                style!!.dp(38f),
                Math.max(style!!.dp(24f), (availableListHeight - style!!.dp(56f)) / 5),
            )
        for (row in rows) {
            val lp = row!!.getLayoutParams() as LinearLayout.LayoutParams?
            lp!!.height = rowHeight
            row!!.setLayoutParams(lp)
        }
        setVisibility(View.VISIBLE)
    }
}
