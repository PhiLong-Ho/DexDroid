package dev.dexdroid

import android.app.Instrumentation
import android.content.*
import android.graphics.Bitmap
import android.os.*
import android.view.*
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Focused physical audio policy, separate-UID route and shared-menu acceptance. */
internal class AudioAcceptance(private val runner: Instrumentation) {
    private lateinit var session: SessionService
    private lateinit var phone: MainActivity
    private val results = StringBuilder()
    private var controlsCaptured = false
    private var savedPreferences: Map<String, *>? = null

    private fun main(action: () -> Unit) = runner.runOnMainSync { action() }

    private fun check(value: Boolean, label: String) {
        if (!value) {
            val detail =
                if (::session.isInitialized)
                    session.audioState().let {
                        " available=${it.available} pending=${it.pending} uncertain=${it.uncertain} selectedEmpty=${it.selected.isEmpty()} message=${it.message}"
                    }
                else ""
            throw AssertionError(label + detail)
        }
        results.append("PASS $label\n")
    }

    private fun await(label: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 12000
        while (!condition() && SystemClock.uptimeMillis() < end) Thread.sleep(50)
        check(condition(), label)
    }

    private fun find(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup)
            for (i in 0 until root.childCount) find(root.getChildAt(i), predicate)?.let {
                return it
            }
        return null
    }

    private fun clickText(root: View, text: String) = main {
        var target = checkNotNull(find(root) { it is TextView && it.text.toString() == text })
        while (!target.isClickable && target.parent is View) target = target.parent as View
        check(
            target.isShown && target.isEnabled && target.isClickable,
            "clickable menu label $text",
        )
        target.performClick()
    }

    private fun controls(root: View, prefix: String) = main {
        val row = root.findViewWithTag<LinearLayout>("$prefix-row-1")
        check(row.childCount == 4, "$prefix has four controls in one row")
        val first = row.getChildAt(0)
        for (i in 0 until row.childCount) {
            val item = row.getChildAt(i)
            check(
                kotlin.math.abs(item.width - first.width) <= 1 && item.height == first.height,
                "$prefix equal control size $i",
            )
            check(
                item.height / root.resources.displayMetrics.density >= 48,
                "$prefix accessible control height $i",
            )
        }
    }

    private fun refreshAction(root: View, prefix: String) {
        main {
            val button = root.findViewWithTag<View>("$prefix-audio-refresh")
            val visible = android.graphics.Rect()
            check(
                button.isShown &&
                    button.isEnabled &&
                    button.getGlobalVisibleRect(visible) &&
                    visible.width() > 0 &&
                    visible.height() > 0,
                "$prefix Refresh outputs visibly usable",
            )
            val density = button.resources.displayMetrics.density
            check(
                button.width / density >= 48 && button.height / density >= 48,
                "$prefix Refresh outputs accessible bounds",
            )
            button.performClick()
        }
        check(
            if (prefix == "phone") phone.controllerMenu()?.isShowing == true
            else session.monitorShellPage() == "audio",
            "$prefix refresh keeps Audio picker open",
        )
    }

    private fun phoneRoot() = phone.window.decorView

    private fun click(root: View, tag: String) = main {
        val view = root.findViewWithTag<View>(tag)
        check(view != null && view.isShown && view.isEnabled, "visible enabled $tag")
        view.performClick()
        check(view.isClickable, "activate clickable $tag")
    }

    private fun shot(name: String, monitor: Boolean = false) {
        Thread.sleep(if (monitor) 200 else 1000)
        runner.waitForIdleSync()
        val directory =
            File(runner.targetContext.externalCacheDir, "audio-acceptance").apply { mkdirs() }
        val bitmap: Bitmap
        if (!monitor) bitmap = checkNotNull(runner.uiAutomation.takeScreenshot())
        else {
            val window = session.monitorWindow()!!
            bitmap =
                Bitmap.createBitmap(
                    window.decorView.width,
                    window.decorView.height,
                    Bitmap.Config.ARGB_8888,
                )
            val done = CountDownLatch(1)
            var result = -1
            main {
                PixelCopy.request(
                    window,
                    bitmap,
                    {
                        result = it
                        done.countDown()
                    },
                    Handler(Looper.getMainLooper()),
                )
            }
            check(
                done.await(5, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS,
                "monitor PixelCopy $name",
            )
        }
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it), "capture $name")
        }
        bitmap.recycle()
    }

    private fun routeType(type: Int) {
        val fixtureUid =
            runner.targetContext.packageManager.getApplicationInfo("dev.dexdroid.test", 0).uid
        check(
            fixtureUid != android.os.Process.myUid(),
            "synthetic playback uses a separate app UID",
        )
        var previousFrames = -1L
        var observedProgress = false
        var last = "No fixture sample"
        try {
            await("active separate-UID PCM reports route type $type with advancing frames") {
                // await checks once more after success; retain the two-sample observation.
                if (observedProgress) return@await true
                val text =
                    DeviceTestSupport.shell(
                        runner,
                        "dumpsys activity dev.dexdroid.test/dev.dexdroid.fixture.AudioFixtureActivity",
                    ) ?: ""
                last =
                    text.lineSequence().firstOrNull { it.contains("AUDIO_FIXTURE uid=") }?.take(512)
                        ?: "Fixture dump missing"
                val frames =
                    Regex("frames=([0-9]+)").find(last)?.groupValues?.get(1)?.toLongOrNull() ?: -1
                val live =
                    last.contains("uid=$fixtureUid routedType=$type ") &&
                        last.contains("error=none running=true playState=3 ")
                val advanced = live && previousFrames >= 0 && frames > previousFrames
                previousFrames = if (live) frames else -1
                observedProgress = advanced
                advanced
            }
        } catch (failure: AssertionError) {
            throw AssertionError(
                "${failure.message}; last synthetic playback observation: $last",
                failure,
            )
        }
    }

    /** Independent shell observation; never equates preferred policy with an audible route. */
    private fun preferredPolicy(): String {
        val dump = DeviceTestSupport.shell(runner, "dumpsys media.audio_policy") ?: ""
        val start = dump.indexOf("Device role per product strategy dump:")
        val end = dump.indexOf("Device role per capture preset dump:", start.coerceAtLeast(0))
        check(start >= 0 && end > start, "independent preferred audio policy observation available")
        return dump
            .substring(start, end)
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun openMonitorAudio() {
        // Native edge gesture opens the existing controls flow; subsequent clicks target actual
        // widgets.
        val viewport = DeviceTestSupport.monitorViewport(runner, session)
        val start = viewport.mapVirtualToScreen(session.width() * .6f, 4f * session.dpi / 160f)
        val end = viewport.mapVirtualToScreen(session.width() * .6f, 100f * session.dpi / 160f)
        DeviceTestSupport.usableMonitorSwipe(runner, session, start, end)
        val down = SystemClock.uptimeMillis()
        for ((action, point) in
            listOf(
                MotionEvent.ACTION_DOWN to start,
                MotionEvent.ACTION_MOVE to end,
                MotionEvent.ACTION_UP to end,
            )) {
            val event =
                MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point[0], point[1], 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(
                DeviceTestSupport.injectOnDisplay(event, viewport.externalDisplay),
                "inject audio controls edge $action",
            )
            event.recycle()
            Thread.sleep(80)
        }
        await("monitor controls ready") {
            session.monitorShellPage() == "controls" && !session.monitorShellAnimating()
        }
        if (!controlsCaptured) {
            controls(session.monitorShellView()!!, "monitor")
            shot("monitor-controls-audio", true)
            controlsCaptured = true
        }
        click(session.monitorShellView()!!, "monitor-audio")
        await("monitor audio ready") {
            session.monitorShellPage() == "audio" && !session.monitorShellAnimating()
        }
        refreshAction(session.monitorShellView()!!, "monitor")
    }

    fun run(): String {
        val latch = CountDownLatch(1)
        val binding =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    session = (binder as SessionService.LocalBinder).get()
                    latch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }
        phone = DeviceTestSupport.startPhone(runner) as MainActivity
        check(
            runner.targetContext.bindService(
                Intent(runner.targetContext, SessionService::class.java),
                binding,
                Context.BIND_AUTO_CREATE,
            ),
            "bind audio session",
        )
        try {
            check(latch.await(5, TimeUnit.SECONDS), "audio service ready")
            await("desktop and audio capability ready") {
                session.id >= 0 &&
                    !session.busy &&
                    session.audioState().available &&
                    !session.audioState().pending
            }
            savedPreferences = HashMap(session.prefs!!.all)
            main {
                session.setMonitorTouchEnabled(true)
                session.setMonitorNavigationEnabled(true)
            }
            val originalRotation = session.rotation
            val speaker = session.audioState().outputs.single { it.kind == "Built-in speaker" }
            val hdmi = session.audioState().outputs.first { it.kind == "HDMI output" }
            val padHeight = phoneRoot().findViewWithTag<View>("touchpad").height
            main {
                session.launch(
                    ComponentName("dev.dexdroid.test", "dev.dexdroid.fixture.AudioFixtureActivity"),
                    null,
                )
            }
            await("synthetic media fixture launched") { session.hasDesktopApp() && !session.busy }
            routeType(9)
            val originalPolicy = preferredPolicy()
            controls(phoneRoot(), "phone")
            shot("phone-controls-audio")
            main {
                checkNotNull(find(phoneRoot()) { it.contentDescription == "Settings" })
                    .performClick()
            }
            await("phone Settings opens") { phone.controllerMenu()?.isShowing == true }
            clickText(phone.controllerMenu()!!.window!!.decorView, "Audio output")
            await("nested phone Audio title") {
                phone
                    .controllerMenu()
                    ?.window
                    ?.decorView
                    ?.findViewWithTag<TextView>("phone-menu-title")
                    ?.text == "Audio output"
            }
            click(phone.controllerMenu()!!.window!!.decorView, "phone-menu-up")
            await("Audio Up returns to Settings") {
                phone
                    .controllerMenu()
                    ?.window
                    ?.decorView
                    ?.findViewWithTag<TextView>("phone-menu-title")
                    ?.text == "Settings"
            }
            click(phone.controllerMenu()!!.window!!.decorView, "phone-menu-close")
            click(phoneRoot(), "phone-audio")
            await("phone audio picker ready") { phone.controllerMenu()?.isShowing == true }
            refreshAction(phone.controllerMenu()!!.window!!.decorView, "phone")
            shot("phone-audio-system")
            click(phone.controllerMenu()!!.window!!.decorView, "phone-audio-${speaker.key}")
            await("speaker preference confirmed") {
                !session.audioState().pending &&
                    session.audioState().selected == speaker.key &&
                    !session.audioState().uncertain
            }
            routeType(2)
            await("phone shortcut returns to controller") { phone.controllerMenu() == null }
            check(
                phoneRoot().findViewWithTag<View>("touchpad").height == padHeight,
                "audio shortcut preserves touchpad bounds",
            )
            openMonitorAudio()
            shot("monitor-audio-speaker-r0", true)
            main {
                val group =
                    session
                        .monitorShellView()!!
                        .findViewWithTag<RadioGroup>("monitor-audio-choices")
                val selected = group.findViewById<RadioButton>(group.checkedRadioButtonId)
                check(
                    selected.text.toString().contains("Phone speaker"),
                    "monitor shares confirmed phone choice",
                )
            }
            click(session.monitorShellView()!!, "monitor-audio-${hdmi.key}")
            await("HDMI preference confirmed") {
                !session.audioState().pending &&
                    session.audioState().selected == hdmi.key &&
                    !session.audioState().uncertain
            }
            routeType(9)
            await("monitor selection returns one level") {
                session.monitorShellPage() == "controls"
            }
            // Closing the menu is independent of policy ownership.
            click(session.monitorShellView()!!, "monitor-close")
            for (rotation in 0..3) {
                main { session.rotate(rotation, false) }
                await("audio desktop rotation $rotation ready") {
                    !session.busy && session.rotation == rotation
                }
                check(
                    session.audioState().selected == hdmi.key,
                    "rotation $rotation preserves session media preference",
                )
                routeType(9)
                openMonitorAudio()
                shot("monitor-audio-r$rotation", true)
                main {
                    check(
                        MenuTextAssertions.check(session.monitorShellView()!!) > 0,
                        "audio text glyphs r$rotation",
                    )
                }
                click(session.monitorShellView()!!, "monitor-close")
            }
            main { session.rotate(0, false) }
            await("Fit preparation rotation") { !session.busy }
            main { session.preview(1920, 1080, 240) }
            await("audio Fit preview ready") { !session.busy && session.awaitingConfirmation() }
            openMonitorAudio()
            shot("monitor-audio-fit-r0", true)
            main {
                val panel = session.monitorShellView()!!.findViewWithTag<View>("monitor-panel")
                check(panel.width > 0 && panel.height > 0, "audio Fit panel has usable bounds")
            }
            click(session.monitorShellView()!!, "monitor-close")
            main { session.confirm(false) }
            await("audio Fit rollback ready") { !session.busy && !session.awaitingConfirmation() }
            check(
                session.audioState().selected == hdmi.key,
                "Fit preview preserves audio selection",
            )
            main { session.chooseAudio("") }
            await("System releases session override") {
                !session.audioState().pending &&
                    session.audioState().selected.isEmpty() &&
                    !session.audioState().uncertain
            }
            routeType(9)
            check(
                preferredPolicy() == originalPolicy,
                "System restores independently observed preferred-policy baseline",
            )
            main { session.chooseAudio(speaker.key) }
            await("speaker selected before Stop") {
                !session.audioState().pending && session.audioState().selected == speaker.key
            }
            routeType(2)
            main { session.pauseDesktop() }
            await("Stop confirms audio release") {
                !session.audioState().pending &&
                    session.audioState().selected.isEmpty() &&
                    !session.audioState().uncertain
            }
            check(
                preferredPolicy() == originalPolicy,
                "Stop restores independently observed preferred-policy baseline",
            )
            main { session.retryDesktop() }
            await("new session starts with System") {
                session.id >= 0 &&
                    !session.busy &&
                    session.audioState().available &&
                    !session.audioState().pending &&
                    session.audioState().selected.isEmpty()
            }
            main { session.rotate(originalRotation, false) }
            await("restore rotation") { !session.busy }
            return results.toString()
        } finally {
            if (::session.isInitialized) {
                await("audio operation settled before cleanup") { !session.audioState().pending }
                main { session.chooseAudio("") }
                await("final audio policy cleanup") {
                    !session.audioState().pending &&
                        !session.audioState().uncertain &&
                        session.audioState().selected.isEmpty()
                }
            }
            savedPreferences?.let { original ->
                main {
                    val edit = session.prefs!!.edit()
                    for (key in listOf("monitorTouch", "monitorNavigation")) {
                        val value = original[key]
                        if (value is Boolean) edit.putBoolean(key, value) else edit.remove(key)
                    }
                    edit.apply()
                }
            }
            runner.targetContext.unbindService(binding)
        }
    }
}
