package dev.dexdroid

import android.content.*
import android.graphics.*
import android.graphics.drawable.*
import android.view.*
import android.widget.*
import java.util.function.Supplier

/** Shared visual contract for the phone controller and rotated monitor menus. */
class ControllerStyle(@JvmField val context: Context?) {

    fun dp(value: Float): Int {
        return Math.round(value * context!!.getResources()!!.getDisplayMetrics()!!.density)
    }

    fun label(text: String?, size: Int): TextView {
        val view = TextView(context)
        view.setText(text)
        view.setTextSize(size.toFloat())
        view.setTextColor(Color.WHITE)
        return view
    }

    fun versionInfo(parent: LinearLayout) {
        val version =
            label(
                ("Version " +
                    BuildConfig.VERSION_NAME +
                    " \u00b7 Build " +
                    BuildConfig.VERSION_CODE +
                    (if (BuildConfig.DEBUG) " \u00b7 Debug" else "")),
                12,
            )
        version.setTag("settings-app-version")
        version.setTextColor(Color.rgb(163, 163, 163))
        version.setPadding(dp(4f), dp(4f), dp(4f), dp(8f))
        parent.addView(version)
    }

    fun surface(shade: Int, radius: Int, border: Int): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(Color.rgb(shade, shade, shade))
        drawable.setCornerRadius(dp(radius.toFloat()).toFloat())
        if (border >= 0) drawable.setStroke(dp(1f), Color.rgb(border, border, border))
        return drawable
    }

    fun ripple(background: Drawable?): Drawable {
        return RippleDrawable(
            android.content.res.ColorStateList.valueOf(Color.rgb(80, 80, 80)),
            background,
            null,
        )
    }

    fun iconAction(icon: Int, name: String?, action: Runnable?): ImageButton {
        val button = ImageButton(context)
        button.setImageResource(icon)
        button.setBackground(ripple(surface(8, 24, -1)))
        button.setContentDescription(name)
        button.setTooltipText(name)
        button.setOnClickListener({ v -> action!!.run() })
        return button
    }

    /** Menu chrome stays visually plain without shrinking its independent touch target. */
    fun menuIconAction(icon: Int, name: String?, action: Runnable?): ImageButton {
        val button = iconAction(icon, name, action)
        button.setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE)
        button.setImageTintList(
            android.content.res.ColorStateList(
                arrayOf<IntArray?>(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(Color.GRAY, Color.WHITE),
            )
        )
        button.setBackground(
            RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.rgb(80, 80, 80)),
                android.graphics.drawable.ColorDrawable(Color.TRANSPARENT),
                surface(255, 12, -1),
            )
        )
        return button
    }

    fun menuHeading(heading: TextView) {
        heading.setGravity(Gravity.CENTER_VERTICAL)
        heading.setPadding(dp(8f), 0, dp(8f), 0)
        heading.setSingleLine(true)
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END)
    }

    @JvmOverloads
    fun button(
        row: LinearLayout,
        text: String?,
        action: Runnable?,
        b: Button = Button(context),
    ): Button {
        b.setText(text)
        b.setTextSize(12f)
        b.setTextColor(Color.WHITE)
        b.setAllCaps(false)
        b.setGravity(Gravity.CENTER)
        b.setPadding(dp(2f), dp(6f), dp(2f), dp(6f))
        b.setMinWidth(0)
        b.setMinimumWidth(0)
        b.setMinHeight(0)
        b.setMinimumHeight(0)
        b.setBackground(ripple(surface(20, 10, 45)))
        val layout = LinearLayout.LayoutParams(0, dp(62f), 1f)
        layout.setMargins(dp(3f), dp(4f), dp(3f), dp(4f))
        row.addView(b, layout)
        b.setOnClickListener({ v -> action!!.run() })
        return b
    }

    fun buttonIcon(b: Button, icon: Int, color: Int) {
        val drawable = context!!.getDrawable(icon)!!.mutate()
        drawable.setTint(color)
        drawable.setBounds(0, 0, dp(20f), dp(20f))
        b.setCompoundDrawables(null, drawable, null, null)
        b.setCompoundDrawablePadding(dp(5f))
    }

    fun iconButton(row: LinearLayout?, icon: Int, name: String?, action: Runnable?): Button {
        val b = button(row!!, name, action)
        b.setTextSize(11f)
        buttonIcon(b, icon, Color.WHITE)
        b.setContentDescription(name)
        b.setTooltipText(name)
        return b
    }

    // Shared Settings surfaces for phone and monitor submenus.
    fun menuPanel(panel: LinearLayout) {
        panel.setOrientation(LinearLayout.VERTICAL)
        panel.setPadding(dp(18f), dp(12f), dp(18f), dp(18f))
        panel.setBackground(surface(18, 22, 45))
        panel.setTag("settings-style-panel")
        panel.setForceDarkAllowed(false)
    }

    fun restoreScroll(scroll: ScrollView, position: Int) {
        if (scroll.isLaidOut() && !scroll.isLayoutRequested()) {
            scroll.scrollTo(0, position)
        } else {
            scroll.addOnLayoutChangeListener(
                object : View.OnLayoutChangeListener {
                    public override fun onLayoutChange(
                        v: View?,
                        l: Int,
                        t: Int,
                        r: Int,
                        b: Int,
                        oldL: Int,
                        oldT: Int,
                        oldR: Int,
                        oldB: Int,
                    ) {
                        scroll.removeOnLayoutChangeListener(this)
                        scroll.scrollTo(0, position)
                    }
                }
            )
        }
    }

    fun card(view: TextView) {
        view.setTextColor(Color.WHITE)
        view.setTextSize(14f)
        view.setGravity(Gravity.CENTER_VERTICAL)
        view.setMinHeight(dp(62f))
        view.setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
        view.setBackground(ripple(surface(18, 8, 40)))
    }

    fun settingRow(parent: LinearLayout): LinearLayout {
        val row = LinearLayout(context)
        row.setGravity(Gravity.CENTER_VERTICAL)
        row.setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
        row.setMinimumHeight(dp(62f))
        row.setBackground(ripple(surface(18, 8, 40)))
        val layout = LinearLayout.LayoutParams(-1, -2)
        layout.setMargins(0, dp(3f), 0, dp(3f))
        parent.addView(row, layout)
        return row
    }

    fun appCard(view: TextView?, icon: Drawable?) {
        var icon = icon
        card(view!!)
        val label = view!!.getText()!!.toString()
        val split = label.indexOf('\n')
        if (split >= 0) {
            val styled = android.text.SpannableString(label)
            styled.setSpan(
                android.text.style.ForegroundColorSpan(Color.rgb(155, 155, 155)),
                split + 1,
                label.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            styled.setSpan(
                android.text.style.AbsoluteSizeSpan(12, true),
                split + 1,
                label.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            view!!.setText(styled)
        }
        view!!.setMinHeight(dp(72f))
        if (icon!!.getConstantState() != null)
            icon = icon!!.getConstantState()!!.newDrawable(context!!.getResources()).mutate()
        icon!!.setBounds(0, 0, dp(36f), dp(36f))
        view!!.setCompoundDrawables(icon, null, null, null)
        view!!.setCompoundDrawablePadding(dp(12f))
    }

    fun protectedNotice(prefix: String?, help: Runnable?, dismiss: Runnable?): LinearLayout {
        val row = LinearLayout(context)
        row.setTag(prefix!! + "-protected-notice")
        row.setGravity(Gravity.CENTER_VERTICAL)
        row.setPadding(dp(10f), dp(4f), dp(4f), dp(4f))
        row.setBackground(surface(18, 12, 40))
        val label = label("Protected screen", 14)
        label.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE)
        row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
        val info = iconAction(R.drawable.ic_info, "Protected screen help", help)
        info.setTag(prefix!! + "-protected-help")
        row.addView(info, LinearLayout.LayoutParams(dp(48f), dp(48f)))
        if (dismiss != null) {
            val close = iconAction(R.drawable.ic_close, "Dismiss protected screen notice", dismiss)
            close.setTag(prefix!! + "-protected-dismiss")
            row.addView(close, LinearLayout.LayoutParams(dp(48f), dp(48f)))
        }
        return row
    }

    inner class CatalogStatus(tag: String?) : LinearLayout(context) {
        private val progress: ProgressBar?
        private val message: TextView?

        init {
            setTag(tag)
            setGravity(Gravity.CENTER_VERTICAL)
            setPadding(0, dp(8f), 0, dp(8f))
            progress = ProgressBar(context)
            progress!!.setIndeterminateTintList(
                android.content.res.ColorStateList.valueOf(Color.WHITE)
            )
            addView(progress, LinearLayout.LayoutParams(dp(18f), dp(18f)))
            message = label("Loading apps\u2026", 12)
            message!!.setPadding(dp(10f), 0, 0, 0)
            message!!.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE)
            addView(message, LinearLayout.LayoutParams(0, -2, 1f))
            setVisibility(View.GONE)
        }

        fun update(state: AppCatalog.State, retry: Runnable?) {
            setVisibility(
                if (state.loading || !state.error!!.isEmpty()) View.VISIBLE else View.GONE
            )
            progress!!.setVisibility(if (state.loading) View.VISIBLE else View.GONE)
            message!!.setText(
                if (state.loading)
                    (if (state.apps!!.isEmpty()) "Loading apps\u2026" else "Updating apps\u2026")
                else "Couldn't load apps. Tap to retry."
            )
            setContentDescription(message!!.getText())
            setOnClickListener(if (state.error!!.isEmpty()) null else { v -> retry!!.run() })
            setClickable(!state.error!!.isEmpty())
        }
    }

    fun appList(list: ListView) {
        list.setBackgroundColor(Color.TRANSPARENT)
        list.setCacheColorHint(Color.TRANSPARENT)
        list.setDivider(ColorDrawable(Color.TRANSPARENT))
        list.setDividerHeight(dp(6f))
    }

    fun search(editor: EditText?) {
        card(editor!!)
        editor!!.setMinHeight(dp(50f))
        editor!!.setSingleLine()
        editor!!.setHintTextColor(Color.rgb(155, 155, 155))
        editor!!.setImeOptions(
            (android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH or
                android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI)
        )
    }

    fun choice(parent: LinearLayout, label: String?, tag: String?, id: Int): RadioButton {
        val choice = RadioButton(context)
        choice.setId(id)
        choice.setTag(tag)
        card(choice)
        choice.setText(label)
        choice.setButtonTintList(
            android.content.res.ColorStateList(
                arrayOf<IntArray?>(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.WHITE, Color.GRAY),
            )
        )
        val layout = LinearLayout.LayoutParams(-1, -2)
        layout.setMargins(0, dp(3f), 0, dp(3f))
        parent.addView(choice, layout)
        return choice
    }

    open class StatusCard(
        context: Context?,
        private val source: Supplier<SessionService?>?,
        help: Runnable?,
        prefix: String?,
        desktop: Runnable?,
        setup: Runnable?,
    ) : FrameLayout(context!!) {
        @JvmField val monitor: TextView?
        @JvmField val operation: TextView?
        @JvmField val appSettings: TextView?
        @JvmField val confirmation: LinearLayout?
        private val protection: LinearLayout?
        private val details: LinearLayout?
        private val expansion: LinearLayout?
        private val detailsCue: TextView?
        private val recovery: Button?
        private val desktopAction: ImageButton?
        private val style: ControllerStyle?
        private val rotations: Array<String?>? =
            arrayOf<String?>("Landscape 0°", "Portrait 90°", "Landscape 180°", "Portrait 270°")

        init {
            style = ControllerStyle(context)
            setTag("controller-status-card")
            setBackground(style!!.surface(18, 12, 40))
            expansion = LinearLayout(context)
            expansion!!.setOrientation(LinearLayout.VERTICAL)
            expansion!!.setPadding(
                style!!.dp(14f),
                style!!.dp(12f),
                style!!.dp(14f),
                style!!.dp(12f),
            )
            expansion!!.setTag(prefix!! + "-status-expand")
            expansion!!.setFocusable(true)
            expansion!!.setBackground(style!!.ripple(ColorDrawable(Color.TRANSPARENT)))
            addView(expansion, FrameLayout.LayoutParams(-1, -2))
            monitor = style!!.label("Monitor · Checking…", 14)
            monitor!!.setTypeface(null, Typeface.BOLD)
            val monitorRow = LinearLayout(context)
            monitorRow.setGravity(Gravity.CENTER_VERTICAL)
            val summary = LinearLayout(context)
            summary.setOrientation(LinearLayout.VERTICAL)
            summary.addView(monitor, LinearLayout.LayoutParams(-1, -2))
            detailsCue = style!!.label("Show details", 12)
            detailsCue!!.setTextColor(Color.rgb(155, 155, 155))
            detailsCue!!.setTag(prefix!! + "-status-cue")
            detailsCue!!.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
            summary.addView(detailsCue, LinearLayout.LayoutParams(-1, -2))
            monitorRow.addView(summary, LinearLayout.LayoutParams(0, -2, 1f))
            desktopAction = style!!.iconAction(R.drawable.ic_stop, "Stop desktop", desktop)
            desktopAction!!.setTag(prefix!! + "-stop")
            desktopAction!!.setBackground(
                RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.rgb(80, 80, 80)),
                    ColorDrawable(Color.TRANSPARENT),
                    ColorDrawable(Color.WHITE),
                )
            )
            monitorRow.addView(
                desktopAction,
                LinearLayout.LayoutParams(style!!.dp(48f), style!!.dp(48f)),
            )
            expansion!!.setOnClickListener({ view ->
                val session = source!!.get()
                if (session != null) {
                    val next = !session!!.prefs!!.getBoolean("statusExpanded", false)
                    session!!.prefs!!.edit()!!.putBoolean("statusExpanded", next)!!.apply()
                    session!!.report("Status details " + (if (next) "expanded" else "collapsed"))
                }
            })
            monitor!!.setMaxLines(2)
            monitor!!.setEllipsize(android.text.TextUtils.TruncateAt.END)
            expansion!!.addView(monitorRow)
            details = LinearLayout(context)
            details!!.setOrientation(LinearLayout.VERTICAL)
            details!!.setTag(prefix!! + "-status-details")
            details!!.setVisibility(View.GONE)
            expansion!!.addView(details)
            recovery = Button(context)
            style!!.button(LinearLayout(context), "Shizuku setup", setup, recovery!!)
            recovery!!.setTag(prefix!! + "-shizuku-recovery")
            recovery!!.setVisibility(View.GONE)
            (recovery!!.getParent() as LinearLayout).removeView(recovery)
            expansion!!.addView(recovery, LinearLayout.LayoutParams(-1, style!!.dp(52f)))
            operation = style!!.label("Connecting controller…", 12)
            operation!!.setTextColor(Color.rgb(170, 170, 170))
            operation!!.setMaxLines(3)
            operation!!.setPadding(0, style!!.dp(5f), 0, style!!.dp(5f))
            details!!.addView(operation)
            appSettings = style!!.label("", 12)
            appSettings!!.setTextColor(Color.rgb(170, 170, 170))
            appSettings!!.setTag("monitor-status")
            val appRow = LinearLayout(context)
            appRow.setGravity(Gravity.TOP)
            appRow.addView(appSettings, LinearLayout.LayoutParams(0, -2, 1f))
            details!!.addView(appRow)
            protection = style!!.protectedNotice("status", help, null)
            protection!!.setVisibility(View.GONE)
            expansion!!.addView(protection, LinearLayout.LayoutParams(-1, -2))
            confirmation = LinearLayout(context)
            expansion!!.addView(confirmation, LinearLayout.LayoutParams(-1, -2))
            style!!.button(
                confirmation,
                "Keep",
                { if (source!!.get() != null) source!!.get()!!.confirm(true) },
            )
            style!!.button(
                confirmation,
                "Revert",
                { if (source!!.get() != null) source!!.get()!!.confirm(false) },
            )
            confirmation!!.getChildAt(0)!!.setTag("monitor-keep")
            confirmation!!.getChildAt(1)!!.setTag("monitor-revert")
            confirmation!!.setVisibility(View.GONE)
        }

        private var lastRestoreNotice: String? = null

        open fun refresh() {
            val s = source!!.get()
            val expanded = s != null && s!!.prefs!!.getBoolean("statusExpanded", false)
            val notice = s?.workspaceShortcut?.restoreNotice
            expansion!!.setAccessibilityLiveRegion(
                if (notice != null && notice != lastRestoreNotice)
                    View.ACCESSIBILITY_LIVE_REGION_POLITE
                else View.ACCESSIBILITY_LIVE_REGION_NONE
            )
            lastRestoreNotice = notice
            details!!.setVisibility(if (expanded) View.VISIBLE else View.GONE)
            detailsCue!!.setText(
                if (expanded) "Hide details"
                else
                    notice?.let { context.getString(R.string.workspace_status_cue, it) }
                        ?: "Show details"
            )
            expansion!!.setStateDescription(if (expanded) "Expanded" else "Collapsed")
            expansion!!.setTooltipText(if (expanded) "Collapse status" else "Expand status")
            expansion!!.setEnabled(s != null)
            recovery!!.setVisibility(
                if (
                    s != null &&
                        (s!!.needsRecovery() || s!!.shizukuState == ShizukuSetup.State.MISSING)
                )
                    View.VISIBLE
                else View.GONE
            )
            if (s != null)
                recovery!!.setText(
                    if (s!!.shizukuState == ShizukuSetup.State.MISSING) "Install Shizuku"
                    else "Shizuku setup"
                )
            desktopAction!!.setEnabled(s != null && !s.debuggingOffPending())
            val requested = s != null && (s!!.desktopRequested() || s!!.id >= 0 || s!!.busy)
            val action = if (requested) "Stop desktop" else "Start desktop"
            desktopAction!!.setImageResource(
                if (requested) R.drawable.ic_stop else R.drawable.ic_play
            )
            desktopAction!!.setContentDescription(action)
            desktopAction!!.setTooltipText(action)
            desktopAction!!.setImageTintList(
                android.content.res.ColorStateList.valueOf(Color.WHITE)
            )
            desktopAction!!.setImageAlpha(if (s == null || s.debuggingOffPending()) 100 else 255)
            if (s == null) {
                monitor!!.setText("Monitor · Checking…")
                operation!!.setText("Controller disconnected")
                appSettings!!.setText("")
                protection!!.setVisibility(View.GONE)
                updateExpansionDescription(expanded)
                return
            }
            protection!!.setVisibility(if (s!!.protectedContent) View.VISIBLE else View.GONE)
            var app: String? = "No app selected"
            if (!s!!.activePackage!!.isEmpty())
                try {
                    app =
                        s!!
                            .getPackageManager()!!
                            .getApplicationLabel(
                                s!!.getPackageManager()!!.getApplicationInfo(s!!.activePackage!!, 0)
                            )
                            .toString()
                } catch (e: Exception) {
                    app = s!!.activePackage
                }

            val ready = s!!.id >= 0 && !s!!.busy
            val desktop =
                if (s!!.busy) "Updating"
                else if (ready) "Ready" else if (s!!.desktopRequested()) "Waiting" else "Stopped"
            monitor!!.setText(
                if (s!!.external == null) "Monitor · Disconnected"
                else "Monitor · Connected — " + s!!.external!!.getName()!!
            )
            var summary: String? = s!!.status!!.split(("\\n").toRegex(), 2).toTypedArray()[0]
            if (!s!!.activePackage!!.isEmpty())
                summary = summary!!.replace(s!!.activePackage!!, app!!)
            if (summary!!.startsWith("Desktop ready") || summary == "Shizuku connected")
                summary = ""
            val restoreResult = s.workspaceShortcut.restoreResult
            operation!!.setMaxLines(if (restoreResult != null) Int.MAX_VALUE else 3)
            operation!!.setText(
                listOfNotNull(
                        context.getString(
                            R.string.controller_operation_status,
                            if (s!!.needsRecovery()) "Unavailable" else "Connected",
                            desktop,
                        ),
                        summary.takeUnless { it.isNullOrEmpty() },
                        restoreResult
                            ?.takeUnless { it == summary }
                            ?.let { context.getString(R.string.workspace_last_result, it) },
                    )
                    .joinToString("\n")
            )
            val saved =
                if (s!!.prefs!!.contains("app." + s!!.activePackage!!))
                    rotations!![
                        Math.max(
                            0,
                            Math.min(3, s!!.prefs!!.getInt("app." + s!!.activePackage!!, 0)),
                        )]
                else "Default"
            val speed = s!!.scrollSpeed()
            val name = if (speed < .35f) "Slow" else if (speed < .75f) "Normal" else "Fast"
            appSettings!!.setText(
                context.getString(
                    R.string.controller_app_status,
                    app,
                    s!!.monitorProfileStatus(),
                    if (s.splitEnabled())
                        context.getString(R.string.controller_layout_status, s.layoutStatus())
                    else "",
                    if (s.swipeScrolling()) "Swipe" else "Wheel",
                    name,
                    Math.round(speed * 100),
                    if (s.monitorTouchEnabled()) "On" else "Off",
                    rotations!![s.rotation],
                    saved,
                    if (s.id >= 0)
                        context.getString(
                            R.string.controller_dimensions_status,
                            s.width(),
                            s.height(),
                            s.dpi,
                        )
                    else "",
                )
            )
            confirmation!!.setVisibility(
                if (s!!.awaitingConfirmation()) View.VISIBLE else View.GONE
            )
            updateExpansionDescription(expanded)
        }

        private fun updateExpansionDescription(expanded: Boolean) {
            expansion!!.setContentDescription(
                ((if (expanded) "Collapse status. " else "Expand status. ") +
                    monitor!!.getText() +
                    (if (expanded) ". " + operation!!.getText() + ". " + appSettings!!.getText()
                    else source!!.get()?.workspaceShortcut?.restoreNotice?.let { ". $it" } ?: ""))
            )
        }
    }
}
