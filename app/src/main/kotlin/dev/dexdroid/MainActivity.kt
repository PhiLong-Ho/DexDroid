package dev.dexdroid

import android.app.*
import android.content.*
import android.content.pm.*
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import java.util.*
import java.util.function.*

open class MainActivity : Activity() {
    private var session: SessionService? = null
    private var style: ControllerStyle? = null
    private var statusCard: ControllerStyle.StatusCard? = null
    private var status: TextView? = null
    private var monitorStatus: TextView? = null
    private var settingsStatus: TextView? = null
    private val menuNavigation: MenuNavigation<MenuEntry?> = MenuNavigation<MenuEntry?>()
    private var switchingMenu: Boolean = false
    private var restoringMenu: Boolean = false

    private var menuCleanup: Runnable? = null
    private var settingsDialog: Dialog? = null
    private var updatingMenu: Boolean = false
    private val uiListener: SessionService.Listener? = SessionService.Listener({ this.refresh() })
    private val menuUpdaters: MutableList<Runnable?> = ArrayList<Runnable?>()
    private var confirmation: LinearLayout? = null
    private var pad: Pad? = null
    private val orientations: Array<String?>? =
        arrayOf<String?>("Landscape 0°", "Portrait 90°", "Landscape 180°", "Portrait 270°")
    private val connection: ServiceConnection =
        object : ServiceConnection {
            public override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
                session = (b as SessionService.LocalBinder).get()
                session!!.listener = uiListener
                session!!.ensureDesktop()
                refresh()
            }

            public override fun onServiceDisconnected(n: ComponentName?) {
                session = null
                refresh()
            }
        }

    private class MenuEntry(@JvmField val page: String?, @JvmField val render: Runnable?) {
        @JvmField var scroll: Int = 0
        @JvmField var query: String? = null
        @JvmField var selectionValid: BooleanSupplier? = BooleanSupplier { false }
    }

    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        style = ControllerStyle(this)
        getWindow()!!.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = LinearLayout(this)
        root.setOrientation(LinearLayout.VERTICAL)
        root.setPadding(dp(14f), dp(6f), dp(14f), dp(12f))
        root.setBackgroundColor(Color.rgb(8, 8, 8))
        val header = row(root)
        header.setGravity(Gravity.CENTER_VERTICAL)
        val title = label("DexDroid", 23)
        header.addView(title, LinearLayout.LayoutParams(0, dp(54f), 1f))
        title.setGravity(Gravity.CENTER_VERTICAL)
        header.addView(
            iconAction(
                R.drawable.ic_settings,
                "Settings",
                { rootMenu(Runnable({ this.options() })) },
            ),
            LinearLayout.LayoutParams(dp(48f), dp(48f)),
        )
        statusCard =
            ControllerStyle.StatusCard(
                this,
                { session },
                { rootMenu(Runnable({ this.protectedHelp() })) },
                "phone",
                Runnable({ this.stopOrStart() }),
                { rootMenu(Runnable({ this.shizukuSetup() })) },
            )
        val panelLayout = LinearLayout.LayoutParams(-1, -2)
        panelLayout.setMargins(0, dp(4f), 0, dp(10f))
        root.addView(statusCard, panelLayout)
        monitorStatus = statusCard!!.monitor
        status = statusCard!!.operation
        settingsStatus = statusCard!!.appSettings
        confirmation = statusCard!!.confirmation
        val top = row(root)
        top.setTag("phone-row-1")
        iconButton(top, R.drawable.ic_apps, "Apps", Runnable({ this.openApps() }))
        iconButton(
            top,
            R.drawable.ic_rotation,
            "Rotate",
            { rootMenu(Runnable({ this.rotation() })) },
        )
        iconButton(
                top,
                R.drawable.ic_layout,
                "Layout",
                { rootMenu(Runnable({ this.layoutSettings() })) },
            )
            .setTag("phone-layout")
        val input = FrameLayout(this)
        val inputLayout = LinearLayout.LayoutParams(-1, 0, 1f)
        inputLayout.topMargin = dp(12f)
        root.addView(input, inputLayout)
        pad = Pad()
        pad!!.setTag("touchpad")
        input.addView(pad, FrameLayout.LayoutParams(-1, -1))
        val padActions = FrameLayout(this)
        padActions.setTag("touchpad-actions")
        padActions.setClickable(true)
        val padHeading = LinearLayout(this)
        padHeading.setTag("touchpad-heading")
        padHeading.setGravity(Gravity.CENTER_VERTICAL)
        val padTitle = label("Touchpad", 11)
        padTitle.setTag("touchpad-title")
        padTitle.setTextColor(Color.rgb(125, 125, 125))
        padTitle.setGravity(Gravity.CENTER)
        padHeading.addView(padTitle, LinearLayout.LayoutParams(-2, -1))
        val scroll =
            iconAction(
                R.drawable.ic_scroll,
                "Scroll settings",
                { rootMenu(Runnable({ this.scrollConfiguration() })) },
            )
        scroll.setTag("phone-scroll")
        padActions.addView(scroll, FrameLayout.LayoutParams(dp(48f), dp(48f), Gravity.START))
        val back =
            iconAction(
                R.drawable.ic_back,
                "Back on monitor",
                {
                    if (session != null) {
                        pad!!.cancel()
                        session!!.log("Back button pressed")
                        session!!.key(KeyEvent.KEYCODE_BACK)
                    }
                },
            )
        back.setTag("phone-back")
        padActions.addView(back, FrameLayout.LayoutParams(dp(48f), dp(48f), Gravity.END))
        val hint =
            iconAction(R.drawable.ic_info, "Touchpad help", Runnable({ this.touchpadHelp() }))
        hint.setTag("touchpad-help")
        padHeading.addView(hint, LinearLayout.LayoutParams(dp(48f), dp(48f)))
        padActions.addView(padHeading, FrameLayout.LayoutParams(-2, dp(48f), Gravity.CENTER))
        val padActionsLayout = FrameLayout.LayoutParams(-1, dp(48f), Gravity.TOP)
        padActionsLayout.setMargins(dp(2f), dp(2f), dp(2f), 0)
        input.addView(padActions, padActionsLayout)
        input.setMinimumHeight(dp(240f))
        val screen =
            object : ScrollView(this) {
                private var padContact: Boolean = false

                public override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        val location = IntArray(2)
                        pad!!.getLocationOnScreen(location)
                        val area =
                            Rect(
                                location[0],
                                location[1],
                                location[0] + pad!!.getWidth(),
                                location[1] + pad!!.getHeight(),
                            )
                        padContact = area.contains(event.getRawX().toInt(), event.getRawY().toInt())
                    }
                    if (padContact) {
                        if (
                            (event.getActionMasked() == MotionEvent.ACTION_UP ||
                                event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                        )
                            padContact = false
                        return false
                    }
                    return super.onInterceptTouchEvent(event)
                }
            }
        screen.setTag("phone-controller-scroll")
        screen.setFillViewport(true)
        screen.addView(root, FrameLayout.LayoutParams(-1, -2))
        ScreenInsets.apply(getWindow()!!, screen)
        setContentView(screen)
        bindService(Intent(this, SessionService::class.java), connection, Context.BIND_AUTO_CREATE)
        if (
            (android.os.Build.VERSION.SDK_INT >= 33 &&
                (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED))
        ) {
            val preferences = getSharedPreferences("desktop", Context.MODE_PRIVATE)
            if (!preferences!!.getBoolean("notificationPermissionAsked", false)) {
                preferences!!.edit()!!.putBoolean("notificationPermissionAsked", true)!!.apply()
                requestPermissions(
                    arrayOf<String?>(android.Manifest.permission.POST_NOTIFICATIONS),
                    92,
                )
            }
        }
    }

    private fun dp(value: Float): Int {
        return Math.round(value * getResources()!!.getDisplayMetrics()!!.density)
    }

    private fun label(text: String?, size: Int): TextView {
        return style!!.label(text, size)
    }

    private fun surface(
        shade: Int,
        radius: Int,
        border: Int,
    ): android.graphics.drawable.GradientDrawable {
        return style!!.surface(shade, radius, border)
    }

    private fun ripple(
        background: android.graphics.drawable.Drawable?
    ): android.graphics.drawable.Drawable {
        return style!!.ripple(background)
    }

    private fun row(root: LinearLayout): LinearLayout {
        val row = LinearLayout(this)
        root.addView(row, LinearLayout.LayoutParams(-1, -2))
        return row
    }

    private fun iconAction(icon: Int, name: String?, action: Runnable?): ImageButton {
        return style!!.iconAction(icon, name, action)
    }

    private fun button(row: LinearLayout?, text: String?, action: Runnable?): Button {
        return style!!.button(row!!, text, action)
    }

    private fun buttonIcon(b: Button?, icon: Int, color: Int) {
        style!!.buttonIcon(b!!, icon, color)
    }

    private fun iconButton(
        row: LinearLayout?,
        icon: Int,
        name: String?,
        action: Runnable?,
    ): Button {
        return style!!.iconButton(row, icon, name, action)
    }

    private fun refresh() {
        statusCard!!.refresh()
        if (session == null) {
            refreshMenu()
            return
        }
        refreshMenu()
        pad!!.invalidate()
    }

    protected override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (session != null) session!!.ensureDesktop()
    }

    protected override fun onResume() {
        super.onResume()
        if (session != null) {
            session!!.listener = uiListener
            session!!.refreshShizukuState()
            // A failed startup retains the request. Stop clears it, so restoring
            // controller focus after Stop must not immediately restart the desktop.
            if (session!!.desktopRequested()) session!!.ensureDesktop()
        }
        if (status != null) refresh()
    }

    protected override fun onDestroy() {
        if (settingsDialog != null) settingsDialog!!.dismiss()
        if (session != null) {
            pad!!.cancel()
            if (session!!.listener === uiListener) session!!.listener = null
        }
        unbindService(connection)
        super.onDestroy()
    }

    protected override fun onPause() {
        if (pad != null) pad!!.cancel()
        super.onPause()
    }

    private fun stopOrStart() {
        if (session == null) return
        pad!!.cancel()
        if (!session!!.desktopRequested() && session!!.id < 0 && !session!!.busy) {
            session!!.retryDesktop()
            return
        }
        session!!.pauseDesktop()
    }

    private fun touchpadHelp() {
        if (pad != null) pad!!.cancel()
        AlertDialog.Builder(this)
            .setTitle("Touchpad gestures")!!
            .setMessage(
                "Slide one finger to move the pointer.\nTap to click.\n\nUse two fingers to scroll. Spread or pinch two fingers to zoom.\n\nSwipe three fingers right to go Back.\n\nThe Scroll control sets mode and speed for the active app. Check Settings → Diagnostic log if a gesture does not respond."
            )!!
            .setPositiveButton("Got it", null)!!
            .show()
    }

    private fun beginMenu(
        title: String?,
        page: String?,
        scrollable: Boolean,
        render: Runnable?,
    ): LinearLayout {
        val previous = menuNavigation.current()
        if (previous != null && settingsDialog != null && !restoringMenu) {
            val decor = settingsDialog!!.getWindow()!!.getDecorView()
            val scroll = decor.findViewWithTag<ScrollView?>("phone-menu-scroll")
            if (scroll != null) previous!!.scroll = scroll!!.getScrollY()
            val query = decor.findViewWithTag<EditText?>("phone-app-search")
            if (query != null) previous!!.query = query!!.getText()!!.toString()
        }
        val entry = MenuEntry(page, render)
        if (restoringMenu) menuNavigation.replace(entry) else menuNavigation.open(entry)
        if (session != null && session!!.split != null) session!!.split!!.cancelKeys()
        if (menuCleanup != null) {
            menuCleanup!!.run()
            menuCleanup = null
        }
        switchingMenu = true
        if (settingsDialog != null) settingsDialog!!.dismiss()
        switchingMenu = false
        menuUpdaters.clear()
        val dialog = Dialog(this)
        settingsDialog = dialog
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = LinearLayout(this)
        style!!.menuPanel(root)
        root.setTag("phone-menu-" + page!!)
        val header = row(root)
        header.setGravity(Gravity.CENTER_VERTICAL)
        if (
            menuNavigation.hasParent({ e ->
                ((e!!.page != "apps" && e!!.page != "app-rotation") ||
                    (page == "app-rotation" && e!!.page == "apps"))
            })
        ) {
            val up =
                style!!.menuIconAction(R.drawable.ic_back, "Back", Runnable({ this.menuBack() }))
            up.setTag("phone-menu-up")
            header.addView(up, LinearLayout.LayoutParams(dp(48f), dp(48f)))
        }
        val heading = label(title, 22)
        heading.setTag("phone-menu-title")
        style!!.menuHeading(heading)
        header.addView(heading, LinearLayout.LayoutParams(0, dp(54f), 1f))
        val close =
            style!!.menuIconAction(
                R.drawable.ic_close,
                "Close menus",
                Runnable({ this.closeMenus() }),
            )
        close.setTag("phone-menu-close")
        header.addView(close, LinearLayout.LayoutParams(dp(48f), dp(48f)))
        if (page != "profiles") {
            val keep = row(root)
            keep.setTag("phone-menu-confirmation")
            button(keep, "Keep", { session!!.confirm(true) }).setTag("phone-menu-keep")
            button(keep, "Revert", { session!!.confirm(false) }).setTag("phone-menu-revert")
            val update = Runnable {
                keep.setVisibility(
                    if (session != null && session!!.awaitingConfirmation()) View.VISIBLE
                    else View.GONE
                )
            }
            update!!.run()
            menuUpdaters.add(update)
        }
        val content = LinearLayout(this)
        content.setOrientation(LinearLayout.VERTICAL)
        content.setTag("phone-menu-content")
        if (scrollable) {
            val scroll = ScrollView(this)
            scroll.setTag("phone-menu-scroll")
            scroll.setFillViewport(true)
            scroll.addView(content)
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        } else root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.setFocusableInTouchMode(true)
        dialog.setContentView(root)
        root.requestFocus()
        dialog.setOnDismissListener({ d ->
            if (settingsDialog === dialog) {
                if (menuCleanup != null) {
                    menuCleanup!!.run()
                    menuCleanup = null
                }
                menuUpdaters.clear()
                settingsDialog = null
                if (!switchingMenu) menuNavigation.clear()
            }
        })
        val backPending = booleanArrayOf(false)
        dialog.setOnKeyListener({ d, key, event ->
            if (key != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
            if (
                (settingsDialog !== dialog || !dialog.isShowing() || isFinishing() || isDestroyed())
            )
                return@setOnKeyListener true
            if (
                (event!!.getAction() == KeyEvent.ACTION_UP &&
                    !event!!.isCanceled() &&
                    !backPending!![0])
            ) {
                val decor = dialog.getWindow()!!.getDecorView()
                val insets = decor.getRootWindowInsets()
                val hideIme = insets != null && insets!!.isVisible(WindowInsets.Type.ime())
                backPending[0] = true
                // Leave the old window's key dispatch before replacing its dialog.
                decor.post({
                    backPending[0] = false
                    if (
                        (settingsDialog !== dialog ||
                            !dialog.isShowing() ||
                            isFinishing() ||
                            isDestroyed())
                    )
                        return@post
                    if (hideIme) {
                        (getSystemService(Context.INPUT_METHOD_SERVICE)
                                as android.view.inputmethod.InputMethodManager)
                            .hideSoftInputFromWindow(decor.getWindowToken(), 0)
                    } else menuBack()
                })
            }
            true
        })
        val window = dialog.getWindow()
        window!!.setBackgroundDrawableResource(android.R.color.transparent)
        window!!.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window!!.setDimAmount(.65f)
        window!!.setSoftInputMode(
            (WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        )
        dialog.show()
        window!!.setLayout(-1, -1)
        ScreenInsets.apply(window!!, root)
        return content
    }

    private fun rootMenu(render: Runnable) {
        menuNavigation.clear()
        render.run()
    }

    private fun canReturn(entry: MenuEntry): Boolean {
        if (entry.page != "apps" && entry.page != "app-rotation") return true
        val current = menuNavigation.current()
        return (current != null &&
            current!!.page == "app-rotation" &&
            current!!.selectionValid!!.getAsBoolean() &&
            entry.page == "apps")
    }

    private fun closeMenus() {
        menuNavigation.clear()
        if (settingsDialog != null) settingsDialog!!.dismiss()
    }

    private fun menuBack() {
        val target = menuNavigation.back(Predicate<MenuEntry?>({ this.canReturn(it) }))
        if (target == null) {
            closeMenus()
            return
        }
        restoringMenu = true
        try {
            target!!.render!!.run()
        } finally {
            restoringMenu = false
        }
        if (settingsDialog == null) return
        val decor = settingsDialog!!.getWindow()!!.getDecorView()
        val query = decor.findViewWithTag<EditText?>("phone-app-search")
        if (query != null && target!!.query != null) query!!.setText(target!!.query)
        val scroll = decor.findViewWithTag<ScrollView?>("phone-menu-scroll")
        if (scroll != null) style!!.restoreScroll(scroll!!, target!!.scroll)
    }

    private fun appSettings() {
        if (session == null) return
        pad!!.cancel()
        val body = beginMenu("App settings", "app-settings", true, Runnable({ this.appSettings() }))
        body.addView(label(activeAppName(), 14))
        settingAction(
            body,
            "Scrolling",
            {
                if (session!!.swipeScrolling()) "Swipe \u00b7 " + speedName()
                else "Wheel \u00b7 " + speedName()
            },
            Runnable({ this.scrollConfiguration() }),
        )
        settingAction(
            body,
            "Save app settings",
            { "Save rotation and current app preferences" },
            Runnable(session!!::saveAppSettings),
        )
        settingAction(
            body,
            "Reset app settings",
            { "Restore defaults for " + activeAppName()!! },
            Runnable(session!!::resetAppSettings),
        )
        refreshMenu()
    }

    open fun controllerMenu(): Dialog? {
        return settingsDialog
    }

    private fun layoutSettings() {
        if (session == null) return
        pad!!.cancel()
        val content = beginMenu("Layout", "layout", true, Runnable({ this.layoutSettings() }))
        val panel = LayoutPanel(session, this, content, IntConsumer({ this.apps(it) }))
        menuUpdaters.add(Runnable({ panel.refresh() }))
    }

    public override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (
            (session != null &&
                (settingsDialog == null || !settingsDialog!!.isShowing()) &&
                session!!.hardwareKey(event))
        )
            return true
        return super.dispatchKeyEvent(event)
    }

    private fun rotation() {
        if (session == null) return
        pad!!.cancel()
        val content =
            beginMenu("Monitor orientation", "rotation", true, Runnable({ this.rotation() }))
        section(content, "ORIENTATION")
        val choices = RadioGroup(this)
        content.addView(choices)
        for (i in 0..3) style!!.choice(choices, orientations!![i], "phone-rotation-" + i, 101 + i)
        choices.check(101 + session!!.rotation)
        choices.setOnCheckedChangeListener({ g, id ->
            if (!updatingMenu) {
                session!!.rotate(id - 101, true)
                menuBack()
            }
        })
        section(content, "APP")
        settingAction(
            content,
            "Save for app",
            { activeAppName() },
            {
                session!!.rememberApp(true)
                refreshMenu()
            },
        )
        refreshMenu()
    }

    private fun speedName(): String {
        val speed = session!!.scrollSpeed()
        return if (speed < .35f) "Slow" else if (speed < .75f) "Normal" else "Fast"
    }

    private fun scrollConfiguration() {
        if (session == null) return
        pad!!.cancel()
        val content =
            beginMenu("Scrolling", "scroll", true, Runnable({ this.scrollConfiguration() }))
        if (session!!.activePackage!!.isEmpty()) {
            content.addView(label("Select an app before configuring scrolling", 13))
            return
        }
        val app = label(activeAppName()!! + " \u00b7 Saved automatically", 12)
        app.setTextColor(Color.LTGRAY)
        content.addView(app)
        section(content, "MODE")
        val mode = RadioGroup(this)
        content.addView(mode)
        val modes =
            arrayOf<String?>("Swipe \u00b7 Touchscreen drag", "Wheel \u00b7 Mouse scrolling")
        for (i in 0..1) style!!.choice(
            mode,
            modes!![i],
            if (i == 0) "phone-swipe" else "phone-wheel",
            201 + i,
        )
        mode.check(if (session!!.swipeScrolling()) 201 else 202)
        mode.setOnCheckedChangeListener({ g, id ->
            if (!updatingMenu) {
                session!!.setSwipeScrolling(id == 201)
                refreshMenu()
            }
        })
        section(content, "SPEED")
        val speed = RadioGroup(this)
        content.addView(speed)
        val speeds = arrayOf<String?>("Slow \u00b7 20%", "Normal \u00b7 50%", "Fast \u00b7 100%")
        for (i in 0..2) style!!.choice(speed, speeds!![i], "phone-speed-" + i, 211 + i)
        speed.check(
            if (session!!.scrollSpeed() < .35f) 211
            else if (session!!.scrollSpeed() < .75f) 212 else 213
        )
        speed.setOnCheckedChangeListener({ g, id ->
            if (!updatingMenu) {
                session!!
                    .prefs!!
                    .edit()!!
                    .putFloat(
                        "scrollSpeed." + session!!.activePackage!!,
                        floatArrayOf(.2f, .5f, 1f)[id - 211],
                    )!!
                    .apply()
                session!!.report(
                    ("Scroll speed \u00b7 " +
                        speedName() +
                        " (" +
                        Math.round(session!!.scrollSpeed() * 100) +
                        "%)")
                )
                refreshMenu()
            }
        })
        menuUpdaters.add({
            mode.check(if (session!!.swipeScrolling()) 201 else 202)
            speed.check(
                if (session!!.scrollSpeed() < .35f) 211
                else if (session!!.scrollSpeed() < .75f) 212 else 213
            )
        })
    }

    private fun activeAppName(): String? {
        if (session == null || session!!.activePackage!!.isEmpty()) return "No app selected"
        try {
            return getPackageManager()!!
                .getApplicationLabel(
                    getPackageManager()!!.getApplicationInfo(session!!.activePackage!!, 0)
                )
                .toString()
        } catch (e: PackageManager.NameNotFoundException) {
            return session!!.activePackage
        }
    }

    private fun refreshSettings() {
        statusCard!!.refresh()
    }

    private fun section(root: LinearLayout, name: String?) {
        val t = label(name, 10)
        t.setTextColor(Color.rgb(145, 145, 145))
        t.setLetterSpacing(.14f)
        t.setPadding(dp(4f), dp(14f), 0, dp(6f))
        root.addView(t)
    }

    private fun menuRow(root: LinearLayout?): LinearLayout {
        return style!!.settingRow(root!!)
    }

    private fun menuText(line: LinearLayout, title: String?): TextView {
        val text = LinearLayout(this)
        text.setOrientation(LinearLayout.VERTICAL)
        line.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        text.addView(label(title, 14))
        val value = label("", 12)
        value.setTextColor(Color.rgb(155, 155, 155))
        value.setPadding(0, dp(4f), 0, 0)
        text.addView(value)
        return value
    }

    private fun settingAction(
        root: LinearLayout?,
        title: String?,
        current: Supplier<String?>?,
        action: Runnable?,
    ) {
        val line = menuRow(root)
        val value = menuText(line, title)
        val arrow = label("›", 24)
        arrow.setTextColor(Color.GRAY)
        arrow.setGravity(Gravity.CENTER)
        line.addView(arrow, LinearLayout.LayoutParams(dp(32f), dp(40f)))
        menuUpdaters.add({ value.setText(current!!.get()) })
        line.setOnClickListener({ v -> action!!.run() })
    }

    private fun settingToggle(
        root: LinearLayout?,
        title: String?,
        enabled: BooleanSupplier?,
        current: Supplier<String?>?,
        change: Consumer<Boolean?>?,
    ) {
        val line = menuRow(root)
        val value = menuText(line, title)
        val toggle = Switch(this)
        toggle.setContentDescription(title)
        toggle.setThumbTintList(
            android.content.res.ColorStateList(
                arrayOf<IntArray?>(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.WHITE, Color.GRAY),
            )
        )
        toggle.setTrackTintList(
            android.content.res.ColorStateList(
                arrayOf<IntArray?>(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.rgb(95, 95, 95), Color.rgb(45, 45, 45)),
            )
        )
        line.addView(toggle, LinearLayout.LayoutParams(dp(52f), dp(42f)))
        menuUpdaters.add({
            value.setText(current!!.get())
            toggle.setChecked(enabled!!.getAsBoolean())
        })
        toggle.setOnCheckedChangeListener({ b, on -> if (!updatingMenu) change!!.accept(on) })
        line.setOnClickListener({ v -> toggle.toggle() })
    }

    private fun refreshMenu() {
        if (session == null) return
        updatingMenu = true
        try {
            for (update in menuUpdaters) update!!.run()
        } finally {
            updatingMenu = false
        }
    }

    private fun pointerSpeed() {
        if (session == null) return
        val content =
            beginMenu("Pointer speed", "pointer-speed", true, Runnable({ this.pointerSpeed() }))
        section(content, "SPEED")
        val value = session!!.prefs!!.getFloat("speed", 1.6f)
        val choices = RadioGroup(this)
        content.addView(choices)
        for (i in 0..2) style!!.choice(
            choices,
            arrayOf<String?>("Slow", "Normal", "Fast")[i],
            "phone-pointer-" + i,
            301 + i,
        )
        choices.check(if (value < 1.3f) 301 else if (value < 2) 302 else 303)
        choices.setOnCheckedChangeListener({ g, id ->
            if (updatingMenu) return@setOnCheckedChangeListener
            session!!
                .prefs!!
                .edit()!!
                .putFloat("speed", floatArrayOf(1f, 1.6f, 2.5f)[id - 301])!!
                .apply()
            session!!.report("Pointer speed changed")
            menuBack()
        })
        menuUpdaters.add({
            val current = session!!.prefs!!.getFloat("speed", 1.6f)
            choices.check(if (current < 1.3f) 301 else if (current < 2) 302 else 303)
        })
    }

    private fun options() {
        if (session == null) return
        pad!!.cancel()
        val list = beginMenu("Settings", "settings", true, Runnable({ this.options() }))
        style!!.versionInfo(list)
        settingAction(
            list,
            "App settings",
            Supplier<String?>({ this.activeAppName() }),
            Runnable({ this.appSettings() }),
        )
        if (session!!.needsRecovery()) {
            section(list, "CONNECTION")
            settingAction(
                list,
                "Retry connection",
                { if (session!!.needsRecovery()) "Shizuku unavailable" else "Connected" },
                { session!!.retryDesktop() },
            )
        }
        settingAction(
            list,
            "Shizuku setup",
            { session!!.shizukuState!!.label },
            Runnable({ this.shizukuSetup() }),
        )
        settingAction(
            list,
            "User guide",
            { "Setup, controls and troubleshooting" },
            Runnable({ this.userGuide() }),
        )
        section(list, "DISPLAY")
        settingAction(
            list,
            "Monitor profiles",
            { session!!.monitorProfileStatus() },
            Runnable({ this.monitorProfiles() }),
        )
        settingAction(
            list,
            "Resolution and DPI",
            {
                if (session!!.external == null) "No monitor connected"
                else
                    (session!!.width().toString() +
                        " × " +
                        session!!.height() +
                        " · " +
                        session!!.dpi +
                        " UI DPI")
            },
            Runnable({ this.displaySettings() }),
        )
        settingToggle(
            list,
            "Monitor touch",
            { session!!.monitorTouchEnabled() },
            { if (session!!.monitorTouchEnabled()) "On" else "Off" },
            { on -> session!!.setMonitorTouchEnabled(on!!) },
        )
        settingToggle(
            list,
            "Monitor edge navigation",
            { session!!.monitorNavigationEnabled() },
            { if (session!!.monitorNavigationEnabled()) "On" else "Off" },
            { on -> session!!.setMonitorNavigationEnabled(on!!) },
        )
        section(list, "INPUT")
        settingAction(
            list,
            "Pointer speed",
            {
                val v = session!!.prefs!!.getFloat("speed", 1.6f)
                if (v < 1.3f) "Slow" else if (v < 2) "Normal" else "Fast"
            },
            Runnable({ this.pointerSpeed() }),
        )
        settingToggle(
            list,
            "Scroll direction",
            { session!!.prefs!!.getBoolean("reverseScroll", false) },
            { if (session!!.prefs!!.getBoolean("reverseScroll", false)) "Reversed" else "Normal" },
            { on ->
                session!!.prefs!!.edit()!!.putBoolean("reverseScroll", on!!)!!.apply()
                session!!.report("Scroll direction · " + (if (on) "Reversed" else "Normal"))
            },
        )
        settingAction(list, "Center pointer", { "Recenter on the desktop" }, { session!!.center() })
        section(list, "GENERAL")
        settingAction(
            list,
            "Background operation",
            {
                if (session!!.backgroundProtectionEnabled()) "Allowed · Battery exemption enabled"
                else "Restricted · Tap to allow"
            },
            {
                startActivity(
                    if (session!!.backgroundProtectionEnabled())
                        Intent(
                            android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                        )
                    else
                        Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:" + getPackageName()!!),
                        )
                )
            },
        )
        settingAction(
            list,
            "Diagnostic log",
            { "Gesture and connection event history" },
            { diagnostics() },
        )
        settingAction(
            list,
            "Protected screen help",
            { "Blank apps and Chrome incognito" },
            Runnable({ this.protectedHelp() }),
        )
        section(list, "RESET")
        settingAction(
            list,
            "Reset monitor profile",
            {
                if (session!!.external == null) "No monitor connected"
                else session!!.monitorProfileStatus()
            },
            {
                if (session!!.external == null)
                    session!!.report("Connect a monitor before resetting its profile")
                else session!!.resetMonitor()
            },
        )
        refreshMenu()
    }

    private fun shizukuSetup() {
        if (session == null) return
        pad!!.cancel()
        session!!.refreshShizukuState()
        val content = beginMenu("Shizuku setup", "shizuku", true, Runnable({ this.shizukuSetup() }))
        val panel = ShizukuSetup.Panel(this, session!!, "phone")
        content.addView(panel.view)
        menuUpdaters.add(Runnable({ panel.refresh() }))
    }

    private fun userGuide() {
        if (session == null) return
        pad!!.cancel()
        val content = beginMenu("User guide", "user-guide", true, Runnable({ this.userGuide() }))
        UserGuide.add(style, content)
        settingAction(
            content,
            "Shizuku setup",
            { session!!.shizukuState!!.label },
            Runnable({ this.shizukuSetup() }),
        )
        settingAction(
            content,
            "Protected screen help",
            { "Blank apps and Chrome incognito" },
            Runnable({ this.protectedHelp() }),
        )
    }

    private fun protectedHelp() {
        if (pad != null) pad!!.cancel()
        ProtectedScreenGuide.add(
            style!!,
            beginMenu(
                ProtectedScreenGuide.TITLE,
                "protected-help",
                true,
                Runnable({ this.protectedHelp() }),
            ),
            "phone",
        )
    }

    private fun monitorProfiles() {
        if (session == null) return
        pad!!.cancel()
        val content =
            beginMenu("Monitor profiles", "profiles", true, Runnable({ this.monitorProfiles() }))
        val profiles = MonitorProfilesPanel(this, session!!, false)
        content.addView(profiles)
        menuUpdaters.add(Runnable({ profiles.refresh() }))
        refreshMenu()
    }

    private fun diagnostics() {
        if (session == null) return
        val source = session
        val body =
            beginMenu("Diagnostic log", "diagnostics", true, Runnable({ this.diagnostics() }))
        val text = label(source!!.diagnosticText(), 11)
        text.setTag("phone-diagnostic-text")
        text.setTypeface(Typeface.MONOSPACE)
        text.setTextIsSelectable(true)
        body.addView(text)
        section(body, "ACTIONS")
        settingAction(
            body,
            "Copy log",
            { "Copy all diagnostic events" },
            {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                    ClipData.newPlainText("DexDroid diagnostics", source!!.diagnosticText())!!
                )
                Toast.makeText(this, "Diagnostic log copied", Toast.LENGTH_SHORT)!!.show()
            },
        )
        settingAction(
            body,
            "Clear log",
            { "Clear recorded events" },
            {
                source!!.clearDiagnostics()
                text.setText(source!!.diagnosticText())
            },
        )
        val handler = Handler(Looper.getMainLooper()!!)
        val update =
            object : Runnable {
                public override fun run() {
                    val snapshot = source!!.diagnosticText()
                    if (!snapshot.contentEquals(text.getText()!!)) text.setText(snapshot)
                    handler.postDelayed(this, 500)
                }
            }
        menuCleanup = Runnable { handler.removeCallbacks(update) }
        handler.post(update)
        refreshMenu()
    }

    private fun openApps() {
        rootMenu(Runnable({ this.apps() }))
    }

    private fun appOrientation(app: AppCatalog.Entry, pane: Int, parent: Runnable?) {
        val body = beginMenu(app.name, "app-rotation", true, { appOrientation(app, pane, parent) })
        val choice = session!!.beginAppSelection(pane)
        menuNavigation.current()!!.selectionValid = BooleanSupplier {
            session != null && session!!.currentAppSelection(choice)
        }
        menuCleanup = Runnable { session!!.finishAppSelection(choice) }
        section(body, "THIS LAUNCH ONLY")
        for (i in 0..3) {
            val rotation = i
            settingAction(
                body,
                orientations!![i],
                { "This launch only" },
                {
                    if (session!!.launchSelection(choice, app.component, rotation))
                        settingsDialog!!.dismiss()
                },
            )
        }
        refreshMenu()
    }

    private fun apps() {
        apps(if (session != null && session!!.split != null) session!!.split!!.selected() else 0)
    }

    private fun apps(pane: Int) {
        if (session == null) return
        val fromLayout =
            (settingsDialog != null &&
                (settingsDialog!!
                    .getWindow()!!
                    .getDecorView()
                    .findViewWithTag<View?>("phone-menu-layout") != null))
        apps(pane, fromLayout)
    }

    private fun apps(pane: Int, fromLayout: Boolean) {
        if (session == null) return
        pad!!.cancel()
        val all = ArrayList<AppCatalog.Entry?>()
        val filtered = ArrayList<AppCatalog.Entry?>()
        val root = beginMenu("Open on monitor", "apps", false, { apps(pane, fromLayout) })
        val owner = settingsDialog
        val launcher = LauncherControls(session!!, this, pane)
        menuNavigation.current()!!.selectionValid = BooleanSupplier({ launcher.current() })
        launcher.addModes(root)
        val catalog = session!!.appCatalog
        settingsDialog!!
            .getWindow()!!
            .clearFlags(
                (WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            )
        settingsDialog!!
            .getWindow()!!
            .setSoftInputMode(
                (WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
            )
        val search = EditText(this)
        search.setOnClickListener({ v ->
            search.post({
                if (
                    (search.isAttachedToWindow() &&
                        search.hasFocus() &&
                        search.getWindowInsetsController() != null)
                )
                    search.getWindowInsetsController()!!.show(WindowInsets.Type.ime())
            })
        })
        style!!.search(search)
        search.setTag("phone-app-search")
        search.setHint("Search installed apps")
        root.addView(search, LinearLayout.LayoutParams(-1, -2))
        val hint =
            label(
                if (session!!.splitEnabled()) "Choose Left/Right or Up/Down beside an app"
                else "Tap to launch \u00b7 Hold for a one-time orientation",
                12,
            )
        hint.setTextColor(Color.LTGRAY)
        hint.setTag("phone-app-target")
        hint.setPadding(0, dp(10f), 0, dp(10f))
        root.addView(hint)
        val loading = style!!.CatalogStatus("phone-app-loading")
        root.addView(loading, LinearLayout.LayoutParams(-1, -2))
        val list = ListView(this)
        list.setTag("phone-app-list")
        style!!.appList(list)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        val adapter =
            object : BaseAdapter() {
                public override fun getCount(): Int {
                    return filtered.size
                }

                public override fun getItem(p: Int): Any? {
                    return filtered.get(p)
                }

                public override fun getItemId(p: Int): Long {
                    return p.toLong()
                }

                public override fun getView(p: Int, reused: View?, parent: ViewGroup?): View {
                    val app = filtered.get(p)
                    return launcher.appRow(
                        app!!,
                        (if (
                            session!!.prefs!!.contains("app." + app!!.component!!.getPackageName())
                        )
                            orientations!![
                                session!!
                                    .prefs!!
                                    .getInt("app." + app!!.component!!.getPackageName(), 0)]
                        else "Session orientation"),
                        Runnable({ owner!!.dismiss() }),
                    )
                }
            }
        list.setAdapter(adapter)
        launcher.onChanged(Runnable({ adapter.notifyDataSetChanged() }))
        menuUpdaters.add(Runnable({ launcher.refresh() }))
        menuUpdaters.add({
            hint.setText(
                if (session!!.busy) "Updating layout…"
                else if (session!!.splitEnabled()) "Choose Left/Right or Up/Down beside an app"
                else "Tap to launch · Hold for a one-time orientation"
            )
        })
        list.setOnItemClickListener({ parent, view, p, id ->
            if (
                (!session!!.splitEnabled() && launcher.launch(filtered.get(p)!!.component, null, 0))
            )
                owner!!.dismiss()
        })
        list.setOnItemLongClickListener({ parent, view, p, id ->
            if (session!!.splitEnabled()) return@setOnItemLongClickListener true
            if (launcher.current())
                appOrientation(filtered.get(p)!!, pane, { apps(pane, fromLayout) })
            else session!!.report("App destination changed. Choose its pane again.")
            true
        })
        search.addTextChangedListener(
            object : android.text.TextWatcher {
                public override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                public override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    val query = s!!.toString().lowercase(Locale.ROOT!!)
                    filtered.clear()
                    for (app in all) if (
                        (app!!.name!!.lowercase(Locale.ROOT!!).contains(query) ||
                            app!!
                                .component!!
                                .getPackageName()
                                .lowercase(Locale.ROOT!!)
                                .contains(query))
                    )
                        filtered.add(app)
                    adapter.notifyDataSetChanged()
                }

                public override fun afterTextChanged(value: android.text.Editable?) {}
            }
        )
        val catalogCleanup =
            catalog!!.observe({ state ->
                if (settingsDialog !== owner) return@observe
                loading.update(state!!, Runnable({ catalog!!.invalidate() }))
                val position = list.onSaveInstanceState()
                all.clear()
                all.addAll(state!!.apps!!)
                val query = search.getText()!!.toString()!!.lowercase(Locale.ROOT!!)
                filtered.clear()
                for (app in all) if (
                    (app!!.name!!.lowercase(Locale.ROOT!!).contains(query) ||
                        app!!.component!!.getPackageName().lowercase(Locale.ROOT!!).contains(query))
                )
                    filtered.add(app)
                adapter.notifyDataSetChanged()
                list.onRestoreInstanceState(position)
            })
        menuCleanup = Runnable {
            catalogCleanup.run()
            launcher.close()
        }
        root.requestFocus()
    }

    private fun displaySettings() {
        if (session == null) return
        pad!!.cancel()
        session!!.detect()
        if (session!!.external == null) {
            session!!.report("Connect an external monitor first")
            return
        }
        val mode = session!!.external!!.getMode()
        val nativeW = mode!!.getPhysicalWidth()
        val nativeH = mode!!.getPhysicalHeight()
        val lowerW = Math.round(nativeW * 2f / 3)
        val lowerH = Math.round(nativeH * 2f / 3)
        val root =
            beginMenu("Resolution and DPI", "display", true, Runnable({ this.displaySettings() }))
        settingsDialog!!
            .getWindow()!!
            .clearFlags(
                (WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            )
        val info = TextView(this)
        info.setText(session!!.capabilities())
        info.setTextSize(13f)
        root.addView(info)
        val recommended = session!!.recommendedMonitorConfiguration()
        val recommendedW = recommended.width
        val recommendedH = recommended.height
        val recommendedDpi = recommended.dpi
        val presets =
            arrayOf<IntArray?>(
                intArrayOf(recommendedW, recommendedH, recommendedDpi),
                intArrayOf(nativeW, nativeH, Geometry.recommendedDpi(Math.min(nativeW, nativeH))),
                intArrayOf(lowerW, lowerH, Geometry.recommendedDpi(Math.min(lowerW, lowerH))),
            )
        val names =
            arrayOf<String?>(
                "Recommended",
                "Same as current output • sharp",
                "Lower rendering load",
            )
        val preset = Spinner(this)
        val labels = arrayOfNulls<String?>(3)
        for (i in 0..2) labels[i] =
            (names!![i] +
                "\n" +
                presets!![i]!![0] +
                " × " +
                presets!![i]!![1] +
                " • " +
                presets!![i]!![2] +
                " UI DPI")
        preset.setAdapter(
            ArrayAdapter<String?>(this, android.R.layout.simple_spinner_dropdown_item, labels)
        )
        root.addView(preset)
        val dimensions = EditText(this)
        dimensions.setTag("phone-display-size")
        dimensions.setSingleLine()
        dimensions.setHint("Desktop width × height (e.g. 1920x1200)")
        root.addView(dimensions)
        val density = EditText(this)
        density.setTag("phone-display-dpi")
        density.setSingleLine()
        density.setInputType(2)
        density.setHint("UI DPI: 120–480")
        root.addView(density)
        preset.setOnItemSelectedListener(
            object : android.widget.AdapterView.OnItemSelectedListener {
                public override fun onItemSelected(p: AdapterView<*>?, v: View?, i: Int, id: Long) {
                    dimensions.setText(presets!![i]!![0].toString() + "x" + presets!![i]!![1])
                    density.setText("" + presets!![i]!![2])
                }

                public override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        )
        val note = TextView(this)
        note.setText(
            "Different aspect ratios use Fit with bars. Dimensions swap for portrait. UI DPI controls text size, not physical monitor density."
        )
        root.addView(note)
        section(root, "APPLY")
        settingAction(
            root,
            "Preview resolution",
            { "Automatically reverts unless kept" },
            {
                try {
                    val values =
                        dimensions
                            .getText()!!
                            .toString()!!
                            .lowercase(Locale.ROOT!!)
                            .replace("\u00d7", "x")
                            .split(("x").toRegex())
                            .dropLastWhile({ it.isEmpty() })
                            .toTypedArray()
                    if (values.size != 2) throw IllegalArgumentException()
                    val w = Integer.parseInt(values[0].trim({ it <= ' ' }))
                    val h = Integer.parseInt(values[1].trim({ it <= ' ' }))
                    val dpi = Integer.parseInt(density.getText()!!.toString()!!)
                    if (w < 480 || h < 480 || w > 4096 || h > 4096 || dpi < 120 || dpi > 480)
                        throw IllegalArgumentException()
                    session!!.preview(w, h, dpi)
                    menuBack()
                } catch (e: Exception) {
                    dimensions.setError("Use dimensions 480-4096 and DPI 120-480")
                }
            },
        )
        refreshMenu()
    }

    private open inner class Pad : View(this@MainActivity) {

        private var lastX: Float = 0.toFloat()
        private var lastY: Float = 0.toFloat()
        private var startX: Float = 0.toFloat()
        private var startY: Float = 0.toFloat()
        private var startSpan: Float = 0.toFloat()
        private var total: Float = 0.toFloat()
        private var pinchSpan: Float = 0.toFloat()
        private var anchorX: Float = 0.toFloat()
        private var anchorY: Float = 0.toFloat()
        private var fingers: Int = 0
        private var kind: Int = 0
        private var multitouch: Boolean = false
        private var pinching: Boolean = false
        private var swiping: Boolean = false
        private var down: Long = 0
        private var lastFrame: Long = 0
        private val paint: Paint = Paint(3)

        public override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
            if (
                (session != null &&
                    session!!.splitEnabled() &&
                    ((event!!.isFromSource(InputDevice.SOURCE_MOUSE) ||
                        event!!.isFromSource(InputDevice.SOURCE_TOUCHPAD))))
            ) {
                session!!.controllerPointer(event, getWidth().toFloat(), getHeight().toFloat())
                return true
            }
            return super.onGenericMotionEvent(event)
        }

        init {
            setBackgroundColor(Color.TRANSPARENT)
        }

        protected override fun onDraw(c: Canvas) {
            paint.setStyle(Paint.Style.STROKE)
            paint.setStrokeWidth(dp(1f).toFloat())
            paint.setColor(Color.rgb(40, 40, 40))
            c.drawRoundRect(
                1f,
                1f,
                (getWidth() - 1).toFloat(),
                (getHeight() - 1).toFloat(),
                dp(14f).toFloat(),
                dp(14f).toFloat(),
                paint,
            )
            paint.setStyle(Paint.Style.FILL)
            paint.setColor(Color.rgb(34, 34, 34))
            var x = dp(22f)
            while (x < getWidth() - dp(12f)) {
                {
                    var y = dp(62f)
                    while (y < getHeight() - dp(12f)) {
                        c.drawCircle(x.toFloat(), y.toFloat(), dp(.65f).toFloat(), paint)
                        y += dp(32f)
                    }
                }
                x += dp(32f)
            }
            paint.setTextSize(dp(11f).toFloat())
            paint.setColor(Color.rgb(125, 125, 125))
        }

        private fun centerX(e: MotionEvent): Float {
            var x = 0f
            for (i in 0 until e.getPointerCount()) x += e.getX(i)
            return x / e.getPointerCount()
        }

        private fun centerY(e: MotionEvent): Float {
            var y = 0f
            for (i in 0 until e.getPointerCount()) y += e.getY(i)
            return y / e.getPointerCount()
        }

        private fun span(e: MotionEvent): Float {
            return Math.hypot(
                    (e.getX(1) - e.getX(0)).toDouble(),
                    (e.getY(1) - e.getY(0)).toDouble(),
                )
                .toFloat()
        }

        private fun pinch(action: Int) {
            val max =
                Math.min(
                    Math.min(anchorX, session!!.width().toFloat() - 1f - anchorX),
                    Math.min(anchorY, session!!.height().toFloat() - 1f - anchorY),
                )
            val half = Geometry.clamp(pinchSpan / 2, 1f, Math.max(1f, max))
            session!!.touch(
                action,
                floatArrayOf(anchorX - half, anchorX + half),
                floatArrayOf(anchorY, anchorY),
            )
        }

        private fun endPinch() {
            if (pinching && session != null) {
                pinch(MotionEvent.ACTION_POINTER_UP or (1 shl 8))
                session!!.touch(
                    MotionEvent.ACTION_UP,
                    floatArrayOf(
                        Geometry.clamp(
                            (anchorX -
                                Math.min(
                                    pinchSpan / 2,
                                    Math.min(
                                        Math.min(
                                            anchorX,
                                            session!!.width().toFloat() - 1f - anchorX,
                                        ),
                                        Math.min(
                                            anchorY,
                                            (session!!.height().toFloat() - 1f - anchorY),
                                        ),
                                    ),
                                )),
                            0f,
                            (session!!.width() - 1).toFloat(),
                        )
                    ),
                    floatArrayOf(anchorY),
                )
            }
            pinching = false
        }

        open fun cancel() {
            if ((pinching || swiping) && session != null) session!!.cancelPhoneInput()
            pinching = false
            swiping = false
            fingers = 0
            kind = 0
            multitouch = true
        }

        public override fun onTouchEvent(e: MotionEvent?): Boolean {
            if (
                (session != null &&
                    session!!.splitEnabled() &&
                    e!!.isFromSource(InputDevice.SOURCE_MOUSE))
            ) {
                session!!.controllerPointer(e, getWidth().toFloat(), getHeight().toFloat())
                return true
            }
            if (session == null || session!!.id < 0 || session!!.busy) {
                if (session != null && e!!.getActionMasked() == MotionEvent.ACTION_DOWN)
                    session!!.log(
                        "Touchpad ignored: desktop=" + session!!.id + " busy=" + session!!.busy
                    )
                cancel()
                return true
            }
            val action = e!!.getActionMasked()
            val x = centerX(e!!)
            val y = centerY(e!!)
            if (action == MotionEvent.ACTION_DOWN) {
                session!!.log("Touchpad DOWN: 1 finger")
                session!!.showTouchpadPointer()
                cancel()
                multitouch = false
                startX = x
                lastX = startX
                startY = y
                lastY = startY
                fingers = 1
                total = 0f
                down = e!!.getEventTime()
            } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
                endPinch()
                if (swiping) {
                    session!!.cancelPhoneInput()
                    swiping = false
                }
                multitouch = true
                fingers = e!!.getPointerCount()
                session!!.log(
                    ("Touchpad pointers: " +
                        fingers +
                        "; three-finger Back requires right swipe > " +
                        dp(60f) +
                        " px")
                )
                kind = 0
                startX = x
                lastX = startX
                startY = y
                lastY = startY
                if (fingers == 2) startSpan = span(e!!)
            } else if (action == MotionEvent.ACTION_MOVE) {
                val dx = x - lastX
                val dy = y - lastY
                total += Math.abs(dx) + Math.abs(dy)
                lastX = x
                lastY = y
                val count = e!!.getPointerCount()
                if (count == 1 && !multitouch)
                    session!!.move(
                        dx * session!!.prefs!!.getFloat("speed", 1.6f),
                        dy * session!!.prefs!!.getFloat("speed", 1.6f),
                    )
                else if (count == 2 && fingers == 2) {
                    val currentSpan = span(e!!)
                    if (kind == 0) {
                        if (
                            (Math.abs(currentSpan - startSpan) > dp(12f) &&
                                (Math.abs(currentSpan - startSpan) >
                                    Math.hypot((x - startX).toDouble(), (y - startY).toDouble()) *
                                        1.2))
                        ) {
                            kind = 2
                            anchorX =
                                Geometry.clamp(session!!.cx, 2f, (session!!.width() - 3).toFloat())
                            anchorY =
                                Geometry.clamp(session!!.cy, 2f, (session!!.height() - 3).toFloat())
                            pinchSpan =
                                Math.max(
                                    2f,
                                    Math.min(
                                        (Math.min(session!!.width(), session!!.height()) * .22f),
                                        (2 *
                                            Math.min(
                                                Math.min(
                                                    anchorX,
                                                    (session!!.width().toFloat() - 1f - anchorX),
                                                ),
                                                Math.min(
                                                    anchorY,
                                                    (session!!.height().toFloat() - 1f - anchorY),
                                                ),
                                            )),
                                    ),
                                )
                            session!!.touch(
                                0,
                                floatArrayOf(anchorX - pinchSpan / 2),
                                floatArrayOf(anchorY),
                            )
                            pinch(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8))
                            pinching = true
                            session!!.log("Gesture recognized: pinch zoom")
                        } else if (
                            Math.hypot((x - startX).toDouble(), (y - startY).toDouble()) > dp(10f)
                        ) {
                            kind = 1
                            session!!.log(
                                ("Gesture recognized: two-finger " +
                                    (if (session!!.swipeScrolling()) "swipe" else "wheel scroll") +
                                    " at " +
                                    Math.round(session!!.scrollSpeed() * 100) +
                                    "%")
                            )
                        }
                    }
                    if (e!!.getEventTime() - lastFrame >= 16) {
                        lastFrame = e!!.getEventTime()
                        if (kind == 2) {
                            pinchSpan =
                                ((Math.min(session!!.width(), session!!.height()).toFloat() *
                                    .22f *
                                    currentSpan) / Math.max(1f, startSpan))
                            pinch(MotionEvent.ACTION_MOVE)
                        } else if (kind == 1) {
                            val direction =
                                (if (session!!.prefs!!.getBoolean("reverseScroll", false)) -1
                                    else 1)
                                    .toFloat()
                            if (session!!.swipeScrolling()) {
                                if (!swiping) {
                                    anchorX = session!!.cx
                                    anchorY = session!!.cy
                                    session!!.touch(0, floatArrayOf(anchorX), floatArrayOf(anchorY))
                                    swiping = true
                                }
                                anchorX =
                                    Geometry.clamp(
                                        (anchorX + (dx * 2f * direction * session!!.scrollSpeed())),
                                        1f,
                                        (session!!.width() - 2).toFloat(),
                                    )
                                anchorY =
                                    Geometry.clamp(
                                        (anchorY + (dy * 2f * direction * session!!.scrollSpeed())),
                                        1f,
                                        (session!!.height() - 2).toFloat(),
                                    )
                                session!!.touch(2, floatArrayOf(anchorX), floatArrayOf(anchorY))
                            } else session!!.wheel(-dx / 35 * direction, -dy / 35 * direction)
                        }
                    }
                }
            } else if (action == MotionEvent.ACTION_POINTER_UP) {
                if (fingers >= 3) {
                    val recognized =
                        x - startX > dp(60f) && Math.abs(y - startY) < Math.abs(x - startX)
                    session!!.log(
                        ("Three-finger Back " +
                            (if (recognized) "recognized" else "rejected") +
                            ": dx=" +
                            Math.round(x - startX) +
                            " dy=" +
                            Math.round(y - startY) +
                            " threshold=" +
                            dp(60f) +
                            " px rightward")
                    )
                    if (recognized) session!!.key(KeyEvent.KEYCODE_BACK)
                    fingers = 0
                }
                endPinch()
                if (swiping) {
                    session!!.touch(1, floatArrayOf(anchorX), floatArrayOf(anchorY))
                    swiping = false
                }
                fingers = 0
            } else if (action == MotionEvent.ACTION_UP) {
                endPinch()
                if (swiping) {
                    session!!.touch(1, floatArrayOf(anchorX), floatArrayOf(anchorY))
                    swiping = false
                }
                if (!multitouch && total < dp(10f) && e!!.getEventTime() - down < 500) {
                    session!!.log("Gesture recognized: tap")
                    session!!.click()
                }
                fingers = 0
            } else if (action == MotionEvent.ACTION_CANCEL) {
                session!!.log("Touchpad gesture canceled by Android")
                cancel()
            }
            return true
        }
    }
}
