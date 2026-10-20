package dev.dexdroid

import android.app.Instrumentation
import android.content.*
import android.graphics.Bitmap
import android.os.*
import android.view.*
import android.widget.*
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.*
import java.util.function.BooleanSupplier

/** Native profile menus and real display preview/Keep/Revert/restart acceptance. */
class MonitorProfilesAcceptance(private val runner: Instrumentation?) {
    private val results: StringBuilder = StringBuilder()
    private var session: SessionService? = null
    private var phone: MainActivity? = null
    private var original: Map<String?, *>? = null
    private var connection: ServiceConnection? = null
    private var bound: Boolean = false
    private var gestureViewport: DeviceTestSupport.MonitorViewport? = null
    private var down: Long = 0

    private var gestureOffsetX: Float = 0.toFloat()
    private var gestureOffsetY: Float = 0.toFloat()

    private fun main(action: Runnable?) {
        runner!!.runOnMainSync(action)
    }

    private fun check(ok: Boolean, label: String?) {
        if (!ok) throw AssertionError(label)
        results.append("PASS ").append(label).append('\n')
    }

    private fun checkMain(condition: BooleanSupplier, label: String?) {
        main { check(condition.asBoolean, label) }
    }

    private fun boundCurrent(): MonitorProfiles.Profile? {
        val identity = session!!.monitorIdentity()
        check(identity != null, "current monitor identity available")
        return session!!.monitorProfiles().bound(identity)
    }

    private fun boundTo(id: String?): Boolean {
        val profile = boundCurrent()
        return profile != null && id!!.equals(profile.id)
    }

    @Throws(Exception::class)
    private fun await(condition: BooleanSupplier, label: String?) {
        val end = SystemClock.uptimeMillis() + 10000
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(50)
        check(condition.getAsBoolean(), label)
    }

    private fun text(root: View?, value: String?): View? {
        if (root is TextView && value!!.contentEquals((root as TextView).getText()!!)) return root
        if (root is ViewGroup)
            for (i in 0 until (root as ViewGroup).getChildCount()) {
                val found = text((root as ViewGroup).getChildAt(i), value)
                if (found != null) return found
            }
        return null
    }

    private fun description(root: View, value: String): View? {
        if (
            value.contentEquals(
                if (root.getContentDescription() == null) "" else root.getContentDescription()
            )
        )
            return root
        if (root is ViewGroup)
            for (i in 0 until (root as ViewGroup).getChildCount()) {
                val found = description((root as ViewGroup).getChildAt(i)!!, value)
                if (found != null) return found
            }
        return null
    }

    private fun phoneRoot(): View {
        return phone!!.controllerMenu()!!.getWindow()!!.getDecorView()
    }

    private fun click(root: View, tag: String?) {
        val view = root.findViewWithTag<View?>(tag)
        check(view != null && view!!.isEnabled(), "enabled profile action " + tag!!)
        check(view!!.performClick(), "activated " + tag!!)
    }

    @Throws(Exception::class)
    private fun event(action: Int, u: Float, v: Float) {
        if (action == MotionEvent.ACTION_DOWN) down = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN)
            gestureViewport = DeviceTestSupport.monitorViewport(runner, session)
        val capturedViewport =
            gestureViewport ?: throw AssertionError("Profile contact missing viewport")
        val point = capturedViewport.mapNormalizedToScreen(u, v)
        if (action == MotionEvent.ACTION_DOWN) {
            val start = point.clone()
            val end = point.clone()
            DeviceTestSupport.usableMonitorSwipe(runner, session, start, end)
            gestureOffsetX = start!![0] - point[0]
            gestureOffsetY = start!![1] - point[1]
        }
        point[0] += gestureOffsetX
        point[1] += gestureOffsetY
        if (action == MotionEvent.ACTION_DOWN) capturedViewport.verify(runner, session)
        val property = MotionEvent.PointerProperties()
        property.id = 0
        property.toolType = MotionEvent.TOOL_TYPE_FINGER
        val coords = MotionEvent.PointerCoords()
        coords.x = point[0]
        coords.y = point[1]
        coords.pressure = 1f
        coords.size = 1f
        val event =
            MotionEvent.obtain(
                down,
                SystemClock.uptimeMillis(),
                action,
                1,
                arrayOf<MotionEvent.PointerProperties?>(property),
                arrayOf<MotionEvent.PointerCoords?>(coords),
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0,
            )
        try {
            check(
                DeviceTestSupport.injectOnDisplay(event, capturedViewport.externalDisplay),
                "physical profile menu event " + action,
            )
        } finally {
            event!!.recycle()
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                gestureViewport = null
        }
    }

    @Throws(Exception::class)
    private fun controls() {
        event(0, .5f, .001f)
        Thread.sleep(30)
        event(2, .5f, .30f)
        event(1, .5f, .30f)
        await(
            { (session!!.monitorShellPage() == "controls" && !session!!.monitorShellAnimating()) },
            "controls edge navigation",
        )
        main({ click(session!!.monitorShellView()!!, "monitor-settings") })
        await(
            { (session!!.monitorShellPage() == "settings" && !session!!.monitorShellAnimating()) },
            "monitor settings",
        )
        main({ click(session!!.monitorShellView()!!, "monitor-profiles") })
        await(
            { (session!!.monitorShellPage() == "profiles" && !session!!.monitorShellAnimating()) },
            "monitor profile submenu",
        )
    }

    @Throws(Exception::class)
    private fun screenshot(name: String?, monitor: Boolean) {
        Thread.sleep(150)
        val bitmap: Bitmap?
        if (monitor) {
            val capturedViewport = DeviceTestSupport.monitorViewport(runner, session)
            bitmap =
                Bitmap.createBitmap(
                    capturedViewport.width,
                    capturedViewport.height,
                    Bitmap.Config.ARGB_8888,
                )
            val done = CountDownLatch(1)
            val code = intArrayOf(-1)
            main({
                capturedViewport.verify(runner, session)
                PixelCopy.request(
                    session!!.monitorWindow()!!,
                    bitmap!!,
                    { result ->
                        code[0] = result
                        done.countDown()
                    },
                    Handler(Looper.getMainLooper()!!),
                )
            })
            check(
                done.await(5, TimeUnit.SECONDS) && code!![0] == PixelCopy.SUCCESS,
                "captured rendered monitor profile menu",
            )
        } else bitmap = runner!!.getUiAutomation()!!.takeScreenshot()
        check(bitmap != null, "profile screenshot available")
        try {
            val folder =
                File(
                    runner!!.getTargetContext()!!.getExternalCacheDir(),
                    "monitor-profile-acceptance",
                )
            check(folder.isDirectory() || folder.mkdirs(), "profile screenshot directory")
            FileOutputStream(File(folder, name!! + ".png"))
                .use({ stream ->
                    check(
                        bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, stream),
                        "profile screenshot exported " + name!!,
                    )
                })
        } finally {
            bitmap!!.recycle()
        }
    }

    private fun restore() {
        if (session == null || original == null) return
        main({
            session!!.pauseDesktop()
            val edit = session!!.prefs!!.edit()!!.clear()
            for (entry in original!!.entries) {
                val value = entry!!.value
                val key = entry!!.key
                if (value is String) edit!!.putString(key, value as String?)
                else if (value is Int) edit!!.putInt(key, (value as Int?)!!)
                else if (value is Float) edit!!.putFloat(key, (value as Float?)!!)
                else if (value is Long) edit!!.putLong(key, (value as Long?)!!)
                else if (value is Boolean) edit!!.putBoolean(key, (value as Boolean?)!!)
            }
            edit!!.commit()
        })
    }

    @Throws(Exception::class)
    fun run(): String {
        try {
            phone = DeviceTestSupport.startPhone(runner!!)
            val ready = CountDownLatch(1)
            connection =
                object : ServiceConnection {
                    public override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        session = (binder as SessionService.LocalBinder).get()
                        ready.countDown()
                    }

                    public override fun onServiceDisconnected(name: ComponentName?) {}
                }
            bound =
                runner!!
                    .getTargetContext()!!
                    .bindService(
                        Intent(runner!!.getTargetContext(), SessionService::class.java),
                        connection!!,
                        Context.BIND_AUTO_CREATE,
                    )
            check(ready.await(5, TimeUnit.SECONDS), "profile service connected")
            await({ session!!.id >= 0 && !session!!.busy }, "profile desktop ready")
            original = HashMap<String?, Any?>(session!!.prefs!!.getAll()!!)
            main({
                session!!.launch(
                    ComponentName(runner!!.getTargetContext()!!, InputTestActivity::class.java!!),
                    null,
                )
            })
            await(
                {
                    (!session!!.busy &&
                        session!!.hasDesktopApp() &&
                        session!!.activePackage == "dev.dexdroid")
                },
                "deterministic input-test backdrop for profile screenshots",
            )
            Thread.sleep(500)
            val title = "Profile acceptance " + SystemClock.uptimeMillis()
            val before = session!!.monitorProfiles().all().size
            main({ description(phone!!.getWindow()!!.getDecorView(), "Settings")!!.performClick() })
            main({
                (text(phoneRoot(), "Monitor profiles")!!.getParent()!!.getParent() as View)
                    .performClick()
            })
            await(
                {
                    (phone!!.controllerMenu() != null &&
                        phoneRoot().findViewWithTag<View?>("profiles-panel") != null)
                },
                "phone profile submenu opens",
            )
            main({
                check(
                    phoneRoot().findViewWithTag<View?>("phone-menu-scroll") is ScrollView,
                    "phone profiles scroll with fixed header",
                )
                if (phoneRoot().findViewWithTag<View?>("profile-create") == null)
                    click(phoneRoot(), "profile-new")
                val editor = phoneRoot().findViewWithTag<EditText?>("profile-name")
                editor!!.setText("")
                click(phoneRoot(), "profile-create")
                check(
                    session!!.monitorProfiles().all().size == before,
                    "empty profile name does not persist",
                )
                editor!!.setText(title)
                click(phoneRoot(), "profile-create")
            })
            await(
                { session!!.monitorProfiles().all().size == before + 1 },
                "phone create persists named profile",
            )
            val savedResult = arrayOfNulls<MonitorProfiles.Profile>(1)
            main { savedResult[0] = boundCurrent() }
            val saved = savedResult[0]
            check(saved != null && saved!!.name == title, "saved profile remembers current monitor")
            screenshot("phone-profiles", false)
            main({
                (phoneRoot().findViewWithTag<View?>("profile-name") as EditText).setText(
                    title!! + " renamed"
                )
                click(phoneRoot(), "profile-rename")
                check(
                    session!!.monitorProfiles().find(saved!!.id)!!.name == title!! + " renamed",
                    "phone rename retains the profile identity",
                )
            })
            main({ phone!!.controllerMenu()!!.dismiss() })
            main({
                session!!.setMonitorNavigationEnabled(true)
                session!!.setMonitorTouchEnabled(true)
            })
            controls()
            main({
                check(
                    MenuTextAssertions.check(session!!.monitorShellView()!!) > 0,
                    "monitor profile text and glyphs valid",
                )
                check(
                    (session!!
                        .monitorShellView()!!
                        .findViewWithTag<View?>("profile-row-" + saved!!.id!!) != null),
                    "phone-created profile appears on monitor",
                )
                click(session!!.monitorShellView()!!, "profile-new")
                val editor =
                    session!!.monitorShellView()!!.findViewWithTag<EditText?>("profile-name")
                editor!!.performClick()
                check(
                    (session!!
                        .monitorShellView()!!
                        .findViewWithTag<View?>("profile-keyboard")!!
                        .getVisibility() == View.VISIBLE),
                    "monitor profile name local keyboard opens",
                )
                val length = editor!!.length()
                click(session!!.monitorShellView()!!, "monitor-key-q")
                check(
                    editor!!.length() == length + 1,
                    "monitor profile name keyboard edits immediately",
                )
                val fullName = String(CharArray(48)).replace('\u0000', 'x')
                editor!!.setText(fullName)
                editor!!.setSelection(48)
                click(session!!.monitorShellView()!!, "monitor-key-q")
                check(
                    (fullName.contentEquals(editor!!.getText()!!) &&
                        editor!!.getSelectionStart() == 48),
                    "name limit safely rejects another local key",
                )
                editor!!.setSelection(0, 1)
                click(session!!.monitorShellView()!!, "monitor-key-q")
                check(
                    (editor!!.length() == 48 &&
                        editor!!.getText()!!.get(0) == 'q' &&
                        editor!!.getSelectionStart() == 1),
                    "local key replaces selection at name limit",
                )
            })
            screenshot("monitor-profiles-keyboard", true)
            main({ session!!.key(KeyEvent.KEYCODE_BACK) })
            await(
                {
                    (session!!.monitorShellPage() == "profiles" &&
                        (session!!
                            .monitorShellView()!!
                            .findViewWithTag<View?>("profile-keyboard")!!
                            .getVisibility() == View.GONE))
                },
                "first profile Back hides name keys and retains editor page",
            )
            main({ session!!.key(KeyEvent.KEYCODE_BACK) })
            await(
                { session!!.monitorShellPage() == "settings" },
                "profile Back returns to monitor settings",
            )
            main({ session!!.key(KeyEvent.KEYCODE_BACK) })
            main({ session!!.key(KeyEvent.KEYCODE_BACK) })
            await(
                {
                    (session!!.monitorShellPage() == "closed" && !session!!.monitorShellAnimating())
                },
                "profile monitor menus close",
            )
            val alternateResult = arrayOfNulls<MonitorProfiles.Profile>(1)
            main {
                alternateResult[0] =
                    session!!
                        .monitorProfiles()
                        .create(
                            title!! + " alternate",
                            MonitorProfiles.Configuration(1280, 800, 160, 0),
                            null,
                        )
            }
            val alternate = alternateResult[0]!!
            for (r in 0..3) {
                val selected = r
                main({
                    session!!
                        .monitorProfiles()
                        .update(
                            alternate.id,
                            MonitorProfiles.Configuration(1280, 800, 160, selected),
                        )
                })
                controls()
                main({
                    click(session!!.monitorShellView()!!, "profile-row-" + alternate.id!!)
                    click(session!!.monitorShellView()!!, "profile-apply")
                })
                await(
                    {
                        (!session!!.busy &&
                            session!!.rotation == selected &&
                            session!!.baseW == 1280)
                    },
                    "profile preview geometry " + r,
                )
                checkMain({ boundTo(saved!!.id) }, "preview does not commit association " + r)
                screenshot("monitor-profiles-r" + r, true)
                main({ click(session!!.monitorShellView()!!, "profiles-revert") })
                await(
                    { !session!!.busy && !session!!.awaitingConfirmation() },
                    "profile Revert settles " + r,
                )
                check(
                    (session!!.rotation == saved!!.configuration!!.rotation &&
                        session!!.defaultRotation == saved!!.configuration!!.rotation),
                    "profile Revert restores output and default orientation",
                )
                main({ session!!.key(KeyEvent.KEYCODE_BACK) })
                main({ session!!.key(KeyEvent.KEYCODE_BACK) })
                main({ session!!.key(KeyEvent.KEYCODE_BACK) })
                await(
                    {
                        (session!!.monitorShellPage() == "closed" &&
                            !session!!.monitorShellAnimating())
                    },
                    "profile menu closed after Revert",
                )
            }
            main({ session!!.applyMonitorProfile(alternate.id) })
            await({ !session!!.busy && session!!.awaitingConfirmation() }, "profile ready to Keep")
            main({ session!!.confirm(true) })
            checkMain({ boundTo(alternate.id) }, "Keep commits selected monitor binding")
            main({
                session!!.pauseDesktop()
                session!!.ensureDesktop()
            })
            await(
                { session!!.id >= 0 && !session!!.busy },
                "desktop restarts with saved monitor profile",
            )
            check(
                (session!!.baseW == 1280 &&
                    session!!.baseH == 800 &&
                    session!!.dpi == 160 &&
                    session!!.rotation == 3),
                "restart automatically reapplies profile geometry and rotation",
            )
            ordinaryKeepAndRestart("bound profile")
            check(
                session!!.monitorProfiles().find(alternate.id)!!.configuration!!.width == 1280,
                "ordinary Keep preserves named snapshot",
            )
            main({ session!!.applyMonitorProfile(alternate.id) })
            await(
                { !session!!.busy && session!!.awaitingConfirmation() },
                "reapply resets working override",
            )
            main({ session!!.confirm(true) })
            main({ session!!.applyMonitorProfile(saved!!.id) })
            await({ !session!!.busy && session!!.awaitingConfirmation() }, "timeout preview ready")
            Thread.sleep(16000)
            await(
                { !session!!.busy && !session!!.awaitingConfirmation() },
                "profile preview automatically reverts after 15 seconds",
            )
            checkMain(
                { boundTo(alternate.id) && session!!.rotation == 3 },
                "timeout retains prior binding and orientation",
            )
            main({
                session!!.applyMonitorProfile(saved!!.id)
                session!!.pauseDesktop()
                session!!.ensureDesktop()
            })
            await({ session!!.id >= 0 && !session!!.busy }, "Stop during preview restarts safely")
            checkMain(
                { !session!!.awaitingConfirmation() && boundTo(alternate.id) },
                "Stop cancels unconfirmed profile binding",
            )
            main({ session!!.resetMonitor() })
            checkMain(
                {
                    boundCurrent() == null && session!!.monitorProfiles().find(alternate.id) != null
                },
                "Forget clears association and retains named profiles",
            )
            ordinaryKeepAndRestart("after Forget")
            checkMain(
                { boundCurrent() == null },
                "ordinary Keep after Forget does not invent named binding",
            )
            main({ session!!.rotate(1, true) })
            await({ !session!!.busy && session!!.rotation == 1 }, "manual default rotation settles")
            main({
                session!!.pauseDesktop()
                session!!.ensureDesktop()
            })
            await({ session!!.id >= 0 && !session!!.busy }, "restart after manual default rotation")
            check(
                (session!!.rotation == 1 &&
                    session!!.defaultRotation == 1 &&
                    session!!.baseW == 1440 &&
                    session!!.baseH == 900),
                "manual default rotation survives working-settings restart",
            )
            main({
                session!!.pauseDesktop()
                // Seed remembered settings different from the stopped runtime, as on
                // monitor change.
                session!!.monitorProfiles().bind(session!!.monitorIdentity(), alternate.id)
                session!!.rotate(saved!!.configuration!!.rotation, true)
                session!!.ensureDesktop()
            })
            await(
                { session!!.id >= 0 && !session!!.busy },
                "restart after stopped default rotation",
            )
            check(
                (session!!.baseW == 1280 &&
                    session!!.baseH == 800 &&
                    session!!.dpi == 160 &&
                    session!!.rotation == saved!!.configuration!!.rotation),
                "stopped rotation preserves detected monitor settings instead of stale runtime",
            )
            check(
                session!!.monitorProfiles().find(alternate.id)!!.configuration!!.rotation == 3,
                "stopped rotation does not edit named snapshot",
            )
            main({
                check(
                    !session!!.diagnosticText().contains(title!!),
                    "profile names are not copied into diagnostic logs",
                )
                check(
                    session!!.prefs!!.getAll()!!.get("rotation") ==
                        saved!!.configuration!!.rotation,
                    "only explicit manual rotation rewrites global default",
                )
                for (entry in original!!.entries) if (
                    (entry!!.key!!.startsWith("app.") ||
                        entry!!.key!!.startsWith("scrollSpeed.") ||
                        entry!!.key!!.startsWith("swipeScroll."))
                )
                    check(
                        entry!!.value == session!!.prefs!!.getAll()!!.get(entry!!.key),
                        "app override preserved " + entry!!.key,
                    )
                description(phone!!.getWindow()!!.getDecorView(), "Settings")!!.performClick()
                (text(phoneRoot(), "Monitor profiles")!!.getParent()!!.getParent() as View)
                    .performClick()
                click(phoneRoot(), "profile-row-" + saved!!.id!!)
                click(phoneRoot(), "profile-delete")
                check(
                    session!!.monitorProfiles().find(saved!!.id) != null,
                    "delete requires explicit confirmation",
                )
                click(phoneRoot(), "profile-delete")
                check(
                    (session!!.monitorProfiles().find(saved!!.id) == null &&
                        session!!.monitorProfiles().find(alternate.id) != null),
                    "confirmed delete preserves other named profiles",
                )
                phone!!.controllerMenu()!!.dismiss()
            })
            return results.toString()
        } catch (failure: Throwable) {
            throw RuntimeException(results.toString() + "FAIL monitor profile acceptance", failure)
        } finally {
            restore()
            if (session != null) {
                await(
                    {
                        val displays =
                            runner!!
                                .getTargetContext()!!
                                .getSystemService(Context.DISPLAY_SERVICE!!)
                                as android.hardware.display.DisplayManager?
                        for (display in displays!!.getDisplays()!!) if (
                            display!!.getName()!!.startsWith("DexDroid Desktop")
                        )
                            return@await false
                        true
                    },
                    "profile cleanup releases virtual display before instrumentation exits",
                )
            }
            if (bound) runner!!.getTargetContext()!!.unbindService(connection!!)
        }
    }

    @Throws(Exception::class)
    private fun ordinaryKeepAndRestart(state: String?) {
        main({ session!!.preview(1440, 900, 180) })
        await(
            { !session!!.busy && session!!.awaitingConfirmation() },
            "ordinary preview " + state!!,
        )
        main({ session!!.confirm(true) })
        main({
            session!!.pauseDesktop()
            session!!.ensureDesktop()
        })
        await({ session!!.id >= 0 && !session!!.busy }, "ordinary Keep restart " + state!!)
        check(
            session!!.baseW == 1440 && session!!.baseH == 900 && session!!.dpi == 180,
            "ordinary Keep restores confirmed working settings " + state!!,
        )
    }
}
