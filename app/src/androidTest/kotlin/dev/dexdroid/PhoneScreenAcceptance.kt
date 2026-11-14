package dev.dexdroid

import android.app.Instrumentation
import android.content.*
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.*
import android.view.*
import android.widget.Switch
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real primary helper/panel, native monitor-button contacts and controller lifecycle. */
internal class PhoneScreenAcceptance(private val runner: Instrumentation) {
    private lateinit var phone: MainActivity
    private lateinit var session: SessionService
    private val results = StringBuilder()

    private fun main(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else runner.runOnMainSync(action)
    }

    private fun <T> read(action: () -> T): T {
        var value: T? = null
        main { value = action() }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private fun verify(value: Boolean, message: String) {
        check(value) { message }
        results.append("PASS $message\n")
        runner.sendStatus(0, Bundle().apply { putString("stream", "PROGRESS $message\n") })
    }

    private fun await(message: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 20000
        while (!condition() && SystemClock.elapsedRealtime() < end) Thread.sleep(50)
        verify(
            condition(),
            message +
                if (condition()) ""
                else
                    read {
                        "; phone=${session.phoneScreenStatus().message}; result=${session.phoneScreenStatus().result}; desktop=${session.status}"
                    },
        )
    }

    private fun stable() =
        await("external desktop ready") {
            read { session.id >= 0 && !session.busy && session.monitorWindow() != null }
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

    private fun controls() {
        main { shell().show("controls") }
        await("monitor Controls visible") {
            read { session.monitorShellPage() == "controls" && !session.monitorShellAnimating() }
        }
    }

    private fun button() = read {
        checkNotNull(session.monitorShellView()?.findViewWithTag<View>("monitor-phone-screen"))
    }

    private fun guard(): android.app.Dialog? = read {
        MainActivity::class
            .java
            .getDeclaredField("phoneScreenGuard")
            .apply { isAccessible = true }
            .get(phone) as? android.app.Dialog
    }

    private fun guardedPhoneContacts() {
        val before = read { session.cx to session.cy }
        val down = SystemClock.uptimeMillis()
        for (action in
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)) {
            val event =
                MotionEvent.obtain(
                    down,
                    SystemClock.uptimeMillis(),
                    action,
                    if (action == MotionEvent.ACTION_DOWN) 600f else 700f,
                    1335f,
                    0,
                )
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            // Monitor focus must not redirect this phone-guard acceptance contact.
            MotionEvent::class
                .java
                .getMethod("setDisplayId", Int::class.javaPrimitiveType)
                .invoke(event, android.view.Display.DEFAULT_DISPLAY)
            try {
                verify(
                    runner.uiAutomation.injectInputEvent(event, true),
                    "native phone guard contact $action",
                )
            } finally {
                event.recycle()
            }
            Thread.sleep(60)
        }
        verify(
            read { session.cx to session.cy } == before &&
                read { session.monitorShellPage() == "controls" },
            "phone guard consumes complete stream without pad or menu forwarding",
        )
    }

    private fun tapMonitorButton() {
        await("monitor phone-screen action enabled") {
            read { button().isShown && button().isEnabled }
        }
        val area = Rect()
        main {
            verify(button().getGlobalVisibleRect(area), "phone-screen action has a visible target")
        }
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event =
                MotionEvent.obtain(
                    down,
                    SystemClock.uptimeMillis(),
                    action,
                    area.exactCenterX(),
                    area.exactCenterY(),
                    0,
                )
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                verify(
                    DeviceTestSupport.injectOnDisplay(event, read { session.external!!.displayId }),
                    "native monitor screen-control contact $action",
                )
            } finally {
                event.recycle()
            }
            Thread.sleep(60)
        }
    }

    private fun confirmedOn() =
        await("internal panel confirmed on and phone guard released") {
            read {
                !session.phoneScreenStatus().possibleOff &&
                    !session.phoneScreenStatus().pending &&
                    session.phoneScreenStatus().result.state == PhoneScreenResult.State.ON
            }
        }

    private fun off() {
        tapMonitorButton()
        await("internal panel confirmed off") {
            read {
                session.phoneScreenStatus().result.state == PhoneScreenResult.State.OFF &&
                    !session.phoneScreenStatus().pending
            }
        }
        verify(
            read { button().contentDescription == "Turn phone screen on" && button().isEnabled },
            "monitor action becomes accessible restore",
        )
    }

    private fun shot(name: String, phoneWindow: Boolean = false, settings: Boolean = false) {
        val window = read {
            if (settings) phone.controllerMenu()!!.window!!
            else if (phoneWindow) phone.window else session.monitorWindow()!!
        }
        val frames = CountDownLatch(1)
        main {
            window.decorView.postOnAnimation {
                window.decorView.postOnAnimation {
                    window.decorView.postOnAnimation { frames.countDown() }
                }
            }
        }
        check(frames.await(3, TimeUnit.SECONDS)) { "Capture frame did not settle" }
        val size = read { window.decorView.width to window.decorView.height }
        val bitmap = Bitmap.createBitmap(size.first, size.second, Bitmap.Config.ARGB_8888)
        try {
            val done = CountDownLatch(1)
            var outcome = -1
            main {
                PixelCopy.request(
                    window,
                    bitmap,
                    {
                        outcome = it
                        done.countDown()
                    },
                    Handler(Looper.getMainLooper()),
                )
            }
            verify(
                done.await(5, TimeUnit.SECONDS) && outcome == PixelCopy.SUCCESS,
                "$name rendered capture",
            )
            val directory = File(runner.targetContext.externalCacheDir, "phone-screen-acceptance")
            check(directory.isDirectory || directory.mkdirs())
            File(directory, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }

    fun run(): String {
        phone = DeviceTestSupport.startPhoneBounded(runner)
        val connected = CountDownLatch(1)
        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    session = (binder as SessionService.LocalBinder).get()
                    connected.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }
        check(
            runner.targetContext.bindService(
                Intent(runner.targetContext, SessionService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        )
        var saved = false
        var oldRotation = 0
        try {
            verify(connected.await(5, TimeUnit.SECONDS), "phone-screen session bound")
            stable()
            saved = read { session.phoneScreenButtonEnabled() }
            oldRotation = read { session.rotation }
            main { session.setPhoneScreenButtonEnabled(false) }
            verify(
                read {
                    phone
                        .findViewById<View>(android.R.id.content)
                        .findViewWithTag<View>("phone-phone-screen")
                        .visibility == View.GONE
                },
                "optional phone icon hidden when disabled",
            )
            main {
                MainActivity::class
                    .java
                    .getDeclaredMethod("options")
                    .apply { isAccessible = true }
                    .invoke(phone)
                val toggle =
                    phone
                        .controllerMenu()!!
                        .window!!
                        .decorView
                        .findViewWithTag<Switch>("phone-phone-screen-switch")
                verify(
                    toggle != null && !toggle.isChecked,
                    "phone Settings shows current Off visibility",
                )
                toggle.performClick()
                verify(toggle.isChecked, "native phone visibility switch changes checked state")
            }
            await("setting enables verified control without turning phone off") {
                read { session.canTurnPhoneScreenOff() }
            }
            verify(
                read { !session.phoneScreenStatus().possibleOff },
                "visibility preference alone leaves panel on",
            )
            shot("phone-settings", true, true)
            main { phone.controllerMenu()?.dismiss() }
            shot("phone-enabled", true)
            controls()
            var guardReady = false
            // Keep this inspect-only pre-OFF fixture owned like real service preparation.
            // Otherwise an unrelated UI refresh correctly dismisses an unowned guard.
            val preparation =
                SessionService::class.java.getDeclaredField("phoneScreenPreparing").apply {
                    isAccessible = true
                }
            main {
                preparation.set(session, Any())
                phone.preparePhoneScreenOff { guardReady = it }
            }
            await("touch-modal phone guard ready before any OFF") { read { guardReady } }
            val windowId = read { guard()!!.window!!.decorView.windowId }
            val perDisplayKeyboardFocus = windowId.isFocused
            verify(
                read { phone.phoneScreenGuardReady() },
                "attached modal guard owns phone control bounds (keyboard focus=$perDisplayKeyboardFocus)",
            )
            guardedPhoneContacts()
            main { session.restorePhoneScreen() }
            for (rotation in 0..3) {
                main { session.rotate(rotation, false) }
                stable()
                controls()
                main {
                    val action = button()
                    val density = action.resources.displayMetrics.density
                    verify(
                        action.width >= 48 * density - 1 && action.height >= 48 * density - 1,
                        "rotation $rotation retains independent 48dp screen-control target",
                    )
                }
                shot("monitor-controls-r$rotation")
                val display = read { session.id }
                off()
                if (rotation == 0) {
                    val before = read { session.cx to session.cy }
                    val whenDown = SystemClock.uptimeMillis()
                    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                        val event =
                            MotionEvent.obtain(
                                whenDown,
                                SystemClock.uptimeMillis(),
                                action,
                                600f,
                                1335f,
                                0,
                            )
                        event.source = InputDevice.SOURCE_TOUCHSCREEN
                        try {
                            verify(
                                runner.uiAutomation.injectInputEvent(event, true),
                                "native phone contact reaches guarded window $action",
                            )
                        } finally {
                            event.recycle()
                        }
                        Thread.sleep(60)
                    }
                    verify(
                        read { session.cx to session.cy } == before &&
                            read { session.monitorShellPage() == "controls" },
                        "phone guard prevents contact forwarding or menu click-through",
                    )
                }
                verify(
                    read { session.id == display && session.monitorWindow() != null },
                    "rotation $rotation keeps external desktop while phone dark",
                )
                shot("monitor-off-r$rotation")
                tapMonitorButton()
                confirmedOn()
                verify(
                    read { session.id == display },
                    "rotation $rotation explicit restore preserves desktop identity",
                )
            }
            main { session.rotate(0, false) }
            stable()
            val originalGeometry = read { intArrayOf(session.baseW, session.baseH, session.dpi) }
            main { session.preview(1920, 1080, 240) }
            await("Fit preview ready") { read { !session.busy && session.awaitingConfirmation() } }
            main { session.confirm(true) }
            stable()
            controls()
            off()
            shot("monitor-off-fit")
            tapMonitorButton()
            confirmedOn()
            main { shell().show("settings") }
            await("monitor Settings visible") {
                read {
                    session.monitorShellPage() == "settings" && !session.monitorShellAnimating()
                }
            }
            main {
                val row =
                    session
                        .monitorShellView()!!
                        .findViewWithTag<View>("monitor-phone-screen-setting")
                row.requestRectangleOnScreen(Rect(0, 0, row.width, row.height), true)
            }
            shot("monitor-settings-fit")
            main { session.preview(originalGeometry[0], originalGeometry[1], originalGeometry[2]) }
            await("original geometry preview ready") {
                read { !session.busy && session.awaitingConfirmation() }
            }
            main { session.confirm(true) }
            stable()
            controls()
            off()
            main {
                session.setPhoneScreenButtonEnabled(false)
                verify(
                    button().visibility == View.VISIBLE,
                    "hiding preference retains recovery during restoration",
                )
            }
            confirmedOn()
            verify(
                read { button().visibility == View.GONE },
                "restore icon hides only after panel On confirmation",
            )
            main { session.setPhoneScreenButtonEnabled(true) }
            await("screen control ready again") { read { session.canTurnPhoneScreenOff() } }
            main {
                session.togglePhoneScreen()
                session.restorePhoneScreen()
            }
            repeat(20) {
                Thread.sleep(50)
                verify(
                    read { !session.phoneScreenStatus().possibleOff },
                    "cancelled guard preparation cannot admit a late OFF $it",
                )
            }
            controls()
            off()
            val beforeFocusLoss = read { session.id }
            main { guard()!!.dismiss() }
            confirmedOn()
            verify(
                read { session.id == beforeFocusLoss },
                "losing the phone touch-guard window restores without stopping desktop",
            )
            controls()
            off()
            main { session.pauseDesktop() }
            confirmedOn()
            verify(read { session.id < 0 }, "Stop restores phone and releases desktop")
            main { session.retryDesktop() }
            stable()
            await("new desktop checks phone-screen control") {
                read { session.canTurnPhoneScreenOff() }
            }
            controls()
            off()
            val previous = read { session.id }
            main { phone.moveTaskToBack(true) }
            confirmedOn()
            verify(
                read { session.id == previous },
                "phone background restores panel while desktop continues",
            )
            phone = DeviceTestSupport.startPhoneBounded(runner)
            stable()
            shot("phone-restored", true)
        } finally {
            if (::session.isInitialized) {
                main { session.restorePhoneScreen() }
                confirmedOn()
                main {
                    session.setPhoneScreenButtonEnabled(saved)
                    session.rotate(oldRotation, false)
                }
            }
            runner.targetContext.unbindService(connection)
        }
        return results.toString()
    }
}
