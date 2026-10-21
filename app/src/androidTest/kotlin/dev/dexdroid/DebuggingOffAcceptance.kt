package dev.dexdroid

import android.app.Instrumentation
import android.content.*
import android.graphics.Bitmap
import android.os.*
import android.view.*
import android.widget.TextView
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Non-destructive acceptance: the helper double delegates cleanup, never settings mutation. */
internal class DebuggingOffAcceptance(private val runner: Instrumentation) {
    private lateinit var session: SessionService
    private lateinit var phone: MainActivity
    private val results = StringBuilder()

    private fun main(action: () -> Unit) = runner.runOnMainSync { action() }

    private fun check(ok: Boolean, label: String) {
        if (!ok) throw AssertionError(label)
        results.append("PASS $label\n")
    }

    private fun await(label: String, condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 15000
        while (!condition() && SystemClock.uptimeMillis() < until) Thread.sleep(30)
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

    private fun clickText(root: View, label: String) = main {
        var target = checkNotNull(find(root) { it is TextView && it.text.toString() == label })
        while (!target.isClickable && target.parent is View) target = target.parent as View
        check(target.isEnabled && target.isShown, "enabled menu $label")
        target.performClick()
    }

    private fun phoneRoot() = phone.controllerMenu()!!.window!!.decorView

    private fun click(root: View, tag: String) {
        fun current(): View? =
            if (tag.startsWith("phone-")) phone.controllerMenu()?.window?.decorView else root
        await("laid out $tag") {
            val target = current()?.findViewWithTag<View>(tag)
            target != null &&
                target.isEnabled &&
                target.isShown &&
                target.width > 0 &&
                target.height > 0
        }
        main {
            val target = checkNotNull(current()?.findViewWithTag<View>(tag))
            check(
                target.isEnabled && target.isShown && target.width > 0 && target.height > 0,
                "enabled $tag",
            )
            target.performClick()
        }
    }

    private fun recovery(root: View, prefix: String) {
        val text = root.findViewWithTag<TextView>("$prefix-debugging-off-hidden-recovery")
        val toggle = root.findViewWithTag<View>("$prefix-debugging-off-recovery-toggle")
        val receipt = session.debuggingOffMessage
        val display = session.id
        check(
            text.visibility == View.GONE && toggle.stateDescription == "Collapsed",
            "$prefix future setup starts collapsed",
        )
        click(root, "$prefix-debugging-off-recovery-toggle")
        check(
            text.visibility == View.VISIBLE && toggle.stateDescription == "Expanded",
            "$prefix future setup expands with accessible state",
        )
        check(
            text.text.contains("Build number") &&
                text.text.contains("manufacturer") &&
                text.text.contains("This enables Developer options") &&
                text.text.contains("not a step for turning debugging off") &&
                !text.text.contains("MIUI"),
            "$prefix generic guidance separates deliberate setup from turn-off",
        )
        val scroll =
            checkNotNull(find(root) { it is android.widget.ScrollView })
                as android.widget.ScrollView
        main { scroll.fullScroll(View.FOCUS_DOWN) }
        await("$prefix recovery scroll reaches bottom") { !scroll.canScrollVertically(1) }
        main {
            check(
                text.getGlobalVisibleRect(android.graphics.Rect()),
                "$prefix hidden-page guidance visible at scroll bottom",
            )
        }
        check(
            session.id == display &&
                session.debuggingOffMessage == receipt &&
                !session.debuggingOffPending(),
            "$prefix expanded help preserves desktop and receipt",
        )
    }

    private fun closeRecovery(root: View, prefix: String) {
        val receipt = session.debuggingOffMessage
        val display = session.id
        click(root, "$prefix-debugging-off-recovery-toggle")
        check(
            root.findViewWithTag<View>("$prefix-debugging-off-hidden-recovery").visibility ==
                View.GONE &&
                root
                    .findViewWithTag<View>("$prefix-debugging-off-recovery-toggle")
                    .stateDescription == "Collapsed",
            "$prefix future setup collapses with accessible state",
        )
        check(
            session.id == display &&
                session.debuggingOffMessage == receipt &&
                !session.debuggingOffPending(),
            "$prefix help leaves desktop and receipt unchanged",
        )
    }

    private fun phoneSettings(root: View, prefix: String) {
        val receipt = session.debuggingOffMessage
        val display = session.id
        val monitor =
            Instrumentation.ActivityMonitor(
                IntentFilter(android.provider.Settings.ACTION_SETTINGS),
                null,
                true,
            )
        runner.addMonitor(monitor)
        try {
            check(
                root.findViewWithTag<TextView>("$prefix-debugging-off-manual").text ==
                    "Open phone settings",
                "$prefix generic phone settings action",
            )
            click(root, "$prefix-debugging-off-manual")
            check(monitor.hits == 1, "$prefix requests main Settings rather than hidden page")
            check(
                session.id == display &&
                    session.debuggingOffMessage == receipt &&
                    !session.debuggingOffPending(),
                "$prefix Settings navigation preserves receipt and desktop",
            )
        } finally {
            runner.removeMonitor(monitor)
        }
    }

    private fun openPhone() {
        main {
            checkNotNull(find(phone.window.decorView) { it.contentDescription == "Settings" })
                .performClick()
        }
        clickText(phoneRoot(), "Turn off debugging")
        await("phone confirmation page ready") {
            phoneRoot().findViewWithTag<TextView>("phone-menu-title")?.text == "Turn off debugging"
        }
    }

    private fun shot(name: String, monitor: Boolean = false) {
        Thread.sleep(if (monitor) 250 else 1000)
        runner.waitForIdleSync()
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
            var code = -1
            main {
                PixelCopy.request(
                    window,
                    bitmap,
                    {
                        code = it
                        done.countDown()
                    },
                    Handler(Looper.getMainLooper()),
                )
            }
            check(
                done.await(5, TimeUnit.SECONDS) && code == PixelCopy.SUCCESS,
                "capture monitor $name",
            )
        }
        val directory =
            File(runner.targetContext.externalCacheDir, "debugging-off-acceptance").apply {
                mkdirs()
            }
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it), "capture $name")
        }
        bitmap.recycle()
    }

    private fun shell(): MonitorShell {
        val monitor =
            SessionService::class
                .java
                .getDeclaredField("monitor")
                .apply { isAccessible = true }
                .get(session)
        return monitor.javaClass
            .getDeclaredField("shell")
            .apply { isAccessible = true }
            .get(monitor) as MonitorShell
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
            "bind debugging acceptance session",
        )
        var original: IDeskService? = null
        var previousReceipt: String? = null
        var previousMessage: String? = null
        val bridge =
            SessionService::class.java.getDeclaredField("bridge").apply { isAccessible = true }
        try {
            check(latch.await(5, TimeUnit.SECONDS), "debugging session ready")
            await("desktop ready before cancellation") { session.id >= 0 && !session.busy }
            previousReceipt = session.prefs!!.getString("debuggingOffReceipt", null)
            previousMessage = session.debuggingOffMessage
            original = bridge.get(session) as IDeskService
            val initial = session.id
            openPhone()
            phoneSettings(phoneRoot(), "phone")
            shot("phone-confirmation")
            recovery(phoneRoot(), "phone")
            shot("phone-hidden-recovery")
            closeRecovery(phoneRoot(), "phone")
            click(phoneRoot(), "phone-debugging-off-cancel")
            check(
                phoneRoot().findViewWithTag<TextView>("phone-menu-title").text == "Settings",
                "Cancel returns to Settings",
            )
            check(
                session.id == initial && !session.debuggingOffPending(),
                "Cancel leaves desktop and settings untouched",
            )
            clickText(phoneRoot(), "Turn off debugging")
            click(phoneRoot(), "phone-menu-up")
            check(
                phoneRoot().findViewWithTag<TextView>("phone-menu-title").text == "Settings",
                "Up returns to Settings without confirmation",
            )
            click(phoneRoot(), "phone-menu-close")
            for (rotation in 0..3) {
                main { session.rotate(rotation, false) }
                await("debugging menu rotation $rotation") {
                    !session.busy && session.rotation == rotation
                }
                main { shell().show("settings") }
                await("monitor Settings ready $rotation") {
                    session.monitorShellPage() == "settings" && !session.monitorShellAnimating()
                }
                click(session.monitorShellView()!!, "monitor-debugging-off")
                await("monitor confirmation ready $rotation") {
                    session.monitorShellPage() == "debugging-off" &&
                        !session.monitorShellAnimating()
                }
                main {
                    check(
                        MenuTextAssertions.check(session.monitorShellView()!!) > 0,
                        "readable shared debugging page $rotation",
                    )
                }
                phoneSettings(session.monitorShellView()!!, "monitor")
                shot("monitor-confirmation-r$rotation", true)
                recovery(session.monitorShellView()!!, "monitor")
                shot("monitor-hidden-recovery-r$rotation", true)
                closeRecovery(session.monitorShellView()!!, "monitor")
                click(session.monitorShellView()!!, "monitor-debugging-off-cancel")
                await("monitor Cancel returns to Settings $rotation") {
                    session.monitorShellPage() == "settings" && !session.monitorShellAnimating()
                }
                click(session.monitorShellView()!!, "monitor-close")
                check(
                    session.id >= 0 && !session.debuggingOffPending(),
                    "monitor cancellation keeps session $rotation",
                )
            }
            main { session.rotate(0, false) }
            await("rotation restored") { !session.busy }
            val calls = AtomicInteger()
            val focusAllowed = java.util.concurrent.atomic.AtomicBoolean(false)
            val rejectCleanup = java.util.concurrent.atomic.AtomicBoolean(false)
            val blockCleanup = java.util.concurrent.atomic.AtomicBoolean(false)
            val cleanupEntered = CountDownLatch(1)
            val cleanupRelease = CountDownLatch(1)
            val double =
                Proxy.newProxyInstance(
                    IDeskService::class.java.classLoader,
                    arrayOf(IDeskService::class.java),
                ) { _, method, args ->
                    when (method.name) {
                        "disableDebugging" -> {
                            calls.incrementAndGet()
                            Bundle().apply {
                                putString(
                                    "summary",
                                    "Controlled receipt: three switches unverified; no device settings writes.",
                                )
                            }
                        }
                        "stop" -> {
                            if (blockCleanup.get()) {
                                cleanupEntered.countDown()
                                check(
                                    cleanupRelease.await(5, TimeUnit.SECONDS),
                                    "bounded controlled cleanup hold",
                                )
                            }
                            val stopped = method.invoke(original, *(args ?: emptyArray()))
                            if (rejectCleanup.get())
                                throw IllegalStateException("Controlled cleanup unconfirmed")
                            stopped
                        }
                        "focusController" ->
                            if (focusAllowed.get()) method.invoke(original, *(args ?: emptyArray()))
                            else false
                        else -> method.invoke(original, *(args ?: emptyArray()))
                    }
                } as IDeskService
            main { session.enableSplit(false) }
            await("split ready before confirmed shutdown") {
                session.split != null && !session.busy
            }
            main { bridge.set(session, double) }
            openPhone()
            click(phoneRoot(), "phone-debugging-off-confirm")
            main {
                session.ensureDesktop()
                session.startDesktop()
                session.turnOffDebugging()
            }
            await("unconfirmed focus produces terminal receipt") {
                !session.debuggingOffPending() && session.id < 0
            }
            check(calls.get() == 0, "unconfirmed phone focus withholds all settings writes")
            check(
                session.split == null && session.id < 0,
                "both split panes retired before unknown result",
            )
            check(session.debuggingOffMessage.contains("Could not"), "uncertainty remains visible")
            shot("phone-unconfirmed-result")
            click(phoneRoot(), "phone-menu-close")
            main { session.ensureDesktop() }
            await("deliberate Start resumes after terminal receipt") {
                session.id >= 0 && !session.busy
            }
            focusAllowed.set(true)
            rejectCleanup.set(true)
            openPhone()
            click(phoneRoot(), "phone-debugging-off-confirm")
            await("unconfirmed display cleanup ends attempt") {
                !session.debuggingOffPending() && session.id < 0
            }
            check(calls.get() == 0, "unconfirmed display cleanup withholds settings writes")
            rejectCleanup.set(false)
            click(phoneRoot(), "phone-menu-close")
            main { session.ensureDesktop() }
            await("restart after controlled cleanup uncertainty") {
                session.id >= 0 && !session.busy
            }
            blockCleanup.set(true)
            openPhone()
            click(phoneRoot(), "phone-debugging-off-confirm")
            check(
                cleanupEntered.await(5, TimeUnit.SECONDS),
                "desktop worker blocked before settings entry",
            )
            main {
                val attempt =
                    SessionService::class
                        .java
                        .getDeclaredField("debuggingOff")
                        .apply { isAccessible = true }
                        .get(session)
                val deadline =
                    attempt.javaClass
                        .getDeclaredField("timeout")
                        .apply { isAccessible = true }
                        .get(attempt) as Runnable
                deadline.run()
            }
            await("timeout receipt does not wait for blocked desktop worker") {
                !session.debuggingOffPending()
            }
            cleanupRelease.countDown()
            blockCleanup.set(false)
            Thread.sleep(200)
            check(calls.get() == 0, "late cleanup after timeout never enters settings operation")
            check(
                session.debuggingOffMessage.contains("timed out"),
                "timeout remains unknown rather than successful",
            )
            click(phoneRoot(), "phone-menu-close")
            main { session.ensureDesktop() }
            await("restart after controlled timeout") { session.id >= 0 && !session.busy }
            openPhone()
            main {
                phoneRoot().findViewWithTag<View>("phone-debugging-off-confirm").performClick()
                val attempt =
                    SessionService::class
                        .java
                        .getDeclaredField("debuggingOff")
                        .apply { isAccessible = true }
                        .get(session)
                val deadline =
                    attempt.javaClass
                        .getDeclaredField("timeout")
                        .apply { isAccessible = true }
                        .get(attempt) as Runnable
                deadline.run() // Same main turn: pending receipt cannot have started cleanup.
                check(
                    session.id < 0,
                    "early timeout retires local desktop before receipt continuation",
                )
            }
            await("early timeout saves terminal receipt") { !session.debuggingOffPending() }
            Thread.sleep(150)
            check(calls.get() == 0, "early timeout never enters settings helper")
            check(
                session.prefs!!.getString("debuggingOffReceipt", "")!!.contains("timed out"),
                "late pending marker cannot overwrite terminal timeout receipt",
            )
            click(phoneRoot(), "phone-menu-close")
            main { session.ensureDesktop() }
            await("restart after early timeout") { session.id >= 0 && !session.busy }
            openPhone()
            click(phoneRoot(), "phone-debugging-off-confirm")
            main {
                session.ensureDesktop()
                session.turnOffDebugging()
            }
            await("controlled successful transport saves receipt") {
                !session.debuggingOffPending() &&
                    session.debuggingOffMessage.contains("Controlled receipt")
            }
            check(
                calls.get() == 1,
                "duplicate confirm and Start produce exactly one helper operation",
            )
            check(
                session.id < 0 && !session.desktopRequested(),
                "receipt navigation keeps desktop stopped",
            )
            check(
                session.prefs!!
                    .getString("debuggingOffReceipt", "")!!
                    .contains("Controlled receipt"),
                "terminal receipt persisted before operation released",
            )
            shot("phone-controlled-result")
            return AudioWorkerAcceptance(runner).releaseBarrier() + results.toString()
        } finally {
            main {
                if (original != null) bridge.set(session, original)
                if (previousMessage != null) {
                    SessionService::class
                        .java
                        .getDeclaredField("debuggingOffMessage")
                        .apply { isAccessible = true }
                        .set(session, previousMessage)
                    val edit = session.prefs!!.edit()
                    if (previousReceipt == null) edit.remove("debuggingOffReceipt")
                    else edit.putString("debuggingOffReceipt", previousReceipt)
                    edit.apply()
                }
                phone.controllerMenu()?.dismiss()
                session.ensureDesktop()
            }
            runner.targetContext.unbindService(binding)
        }
    }
}
