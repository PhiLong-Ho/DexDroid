package dev.dexdroid.fixture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Separate-UID native editor. No custom keyboard, direct test text entry or product shipping. */
open class NativeImeFixtureActivity : Activity() {
    private lateinit var editor: EditText
    private lateinit var receipt: TextView
    private var destroyed = false
    private var contacts = 0
    private var heldProbeKey = false
    private var probeKeyUps = 0
    private var probeKeyCancelled = 0

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_F12) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                heldProbeKey = true
            } else if (event.action == android.view.KeyEvent.ACTION_UP) {
                heldProbeKey = false
                probeKeyUps++
                if (event.isCanceled) probeKeyCancelled++
            }
        }
        val handled = super.dispatchKeyEvent(event)
        if (::editor.isInitialized) report()
        return handled
    }

    override fun onCreate(state: Bundle?) {
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(state)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        val body =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xff14202a.toInt())
                isFocusableInTouchMode = true
            }
        body.addView(
            TextView(this).apply {
                text = "Native keyboard fixture"
                textSize = 22f
                setTextColor(-1)
                setPadding(24, 16, 24, 16)
            }
        )
        editor =
            EditText(this).apply {
                id = android.R.id.edit
                hint = "Tap to type synthetic text"
                setSingleLine(true)
                inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_URI
                textSize = 24f
                setTextColor(-1)
                setHintTextColor(0xffcccccc.toInt())
                setPadding(24, 20, 24, 20)
            }
        body.addView(editor, LinearLayout.LayoutParams(-1, -2))
        receipt =
            TextView(this).apply {
                text = "Text: (empty)"
                textSize = 22f
                setTextColor(-1)
                setPadding(24, 16, 24, 16)
            }
        body.addView(receipt)
        setContentView(body)
        body.requestFocus()
        editor.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    receipt.text = "Text: " + s.toString()
                    report()
                }

                override fun afterTextChanged(s: Editable?) {}
            }
        )
        body.setOnApplyWindowInsetsListener { _, insets ->
            body.post { report() }
            insets
        }
        editor.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> report() }
        editor.setOnFocusChangeListener { _, _ -> report() }
        body.post { report() }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { report() }
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) contacts++
        val handled = super.dispatchTouchEvent(event)
        if (::editor.isInitialized) report()
        return handled
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::editor.isInitialized) report()
    }

    override fun onConfigurationChanged(configuration: android.content.res.Configuration) {
        super.onConfigurationChanged(configuration)
        window.decorView.post { report() }
    }

    override fun onDestroy() {
        destroyed = true
        report()
        super.onDestroy()
    }

    private fun report() {
        if (!::editor.isInitialized) return
        val at = IntArray(2)
        editor.getLocationOnScreen(at)
        val insets = window.decorView.rootWindowInsets
        sendBroadcast(
            Intent(ACTION)
                .setPackage("dev.dexdroid")
                .putExtra("uid", android.os.Process.myUid())
                .putExtra("display", display?.displayId ?: -1)
                .putExtra("component", componentName.flattenToString())
                .putExtra("task", taskId)
                .putExtra("text", editor.text.toString())
                .putExtra("destroyed", destroyed)
                .putExtra("contacts", contacts)
                .putExtra("heldProbeKey", heldProbeKey)
                .putExtra("probeKeyUps", probeKeyUps)
                .putExtra("probeKeyCancelled", probeKeyCancelled)
                .putExtra("editorFocused", editor.hasFocus())
                .putExtra("windowFocused", hasWindowFocus())
                .putExtra("imeVisible", insets?.isVisible(WindowInsets.Type.ime()) == true)
                .putExtra("imeHeight", insets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0)
                .putExtra("editorX", at[0] + editor.width / 2f)
                .putExtra("editorY", at[1] + editor.height / 2f)
                .putExtra("editorBottom", at[1] + editor.height)
                .putExtra("windowHeight", window.decorView.height)
        )
    }

    companion object {
        const val ACTION = "dev.dexdroid.test.NATIVE_IME_STATE"
    }
}
