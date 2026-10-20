package dev.dexdroid

import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import java.io.*
import java.util.concurrent.*
import java.util.function.BooleanSupplier

/** Real different-UID FLAG_SECURE transitions, not a simulated detection callback. */
class ProtectionAcceptance(private val runner: Instrumentation?) {
    private val result: StringBuilder = StringBuilder()
    private var session: SessionService? = null
    private var phone: MainActivity? = null
    @Volatile private var fixture: Bundle? = null
    private var folder: File? = null
    private var display: Int = 0

    private fun main(action: Runnable?) {
        runner!!.runOnMainSync(action)
    }

    private fun ui(condition: BooleanSupplier?): Boolean {
        val value = booleanArrayOf(false)
        main({ value[0] = condition!!.getAsBoolean() })
        return value!![0]
    }

    private fun check(value: Boolean, label: String?) {
        if (!value) throw AssertionError(label)
        result.append("PASS ").append(label).append('\n')
    }

    @Throws(Exception::class)
    private fun await(condition: BooleanSupplier, label: String?) {
        val end = SystemClock.uptimeMillis() + 12000
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(40)
        check(condition.getAsBoolean(), label)
    }

    private fun view(tag: String?): View? {
        return session!!.monitorShellView()!!.findViewWithTag<View?>(tag)
    }

    private fun shown(tag: String?): Boolean {
        val v = view(tag)
        return v != null && v!!.isShown()
    }

    @Throws(Exception::class)
    private fun tap(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        for (action in intArrayOf(0, 1)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event!!.setSource(InputDevice.SOURCE_TOUCHSCREEN)
            try {
                check(
                    DeviceTestSupport.injectOnDisplay(event, display),
                    "physical help input " + action,
                )
            } finally {
                event!!.recycle()
            }
            if (action == 0) Thread.sleep(50)
        }
        Thread.sleep(80)
    }

    @Throws(Exception::class)
    private fun click(tag: String?, pointer: Boolean) {
        val rect = Rect()
        await(
            {
                ui({
                    (!session!!.monitorShellAnimating() &&
                        shown(tag) &&
                        view(tag)!!.getGlobalVisibleRect(rect) &&
                        !rect.isEmpty())
                })
            },
            "help target measured: " + tag!!,
        )
        if (pointer) {
            main({
                val target = view(tag)
                var current = target
                var x = target!!.getWidth() / 2f
                var y = target!!.getHeight() / 2f
                while (current !== session!!.monitorShellView()) {
                    x += current!!.getLeft().toFloat()
                    y += current!!.getTop().toFloat()
                    val parent = current!!.getParent() as View?
                    x -= parent!!.getScrollX().toFloat()
                    y -= parent!!.getScrollY().toFloat()
                    current = parent
                }
                session!!.cx = x
                session!!.cy = y
                session!!.click()
            })
        } else tap(rect.exactCenterX(), rect.exactCenterY())
    }

    @Throws(Exception::class)
    private fun appTap(nx: Float, ny: Float) {
        val capturedViewport = DeviceTestSupport.monitorViewport(runner, session)
        val point = capturedViewport.mapNormalizedToScreen(nx, ny)
        display = capturedViewport.externalDisplay
        capturedViewport.verify(runner, session)
        tap(point[0], point[1])
    }

    @Throws(Exception::class)
    private fun secure(value: Boolean) {
        shell(
            ("am broadcast -a dev.dexdroid.test.SET_PROTECTION -p dev.dexdroid.test --ei display " +
                session!!.id +
                " --ez secure " +
                value)
        )
        await(
            {
                (fixture != null &&
                    fixture!!.getBoolean("secure") == value &&
                    fixture!!.getInt("display") == session!!.id)
            },
            "fixture changed actual window protection to " + value,
        )
        try {
            await(
                { session!!.protectedContent == value },
                "shell detects same-window protection " + value,
            )
        } catch (error: Throwable) {
            val dump = DeviceTestSupport.shell(runner, "dumpsys -t 2 window visible-apps")
            result
                .append("PARSER STATE ")
                .append(ProtectedWindows.read(StringReader(dump!!), session!!.id))
                .append('\n')
            for (line in
                dump!!.split(("\n").toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()) {
                val field = line!!.trim({ it <= ' ' })
                if (field.startsWith("Window #")) result.append("WINDOW\n")
                else if (
                    (field.startsWith("mDisplayId=") ||
                        field.startsWith("fl=") ||
                        field.startsWith("mHasSurface=") ||
                        field.startsWith("isOnScreen=") ||
                        field.contains("DUMP TIMEOUT"))
                )
                    result.append(field).append('\n')
            }
            throw error
        }
    }

    @Throws(Exception::class)
    private fun shot(name: String?, monitor: Boolean) {
        Thread.sleep(300)
        val bitmap: Bitmap?
        if (monitor) {
            val window = session!!.monitorWindow()
            val decor = window!!.getDecorView()
            bitmap =
                Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888)
            val copied = CountDownLatch(1)
            val code = intArrayOf(-1)
            main({
                PixelCopy.request(
                    window!!,
                    bitmap!!,
                    { value ->
                        code[0] = value
                        copied.countDown()
                    },
                    Handler(Looper.getMainLooper()!!),
                )
            })
            check(
                copied.await(3, TimeUnit.SECONDS) && code!![0] == PixelCopy.SUCCESS,
                "help pixels captured",
            )
        } else bitmap = runner!!.getUiAutomation()!!.takeScreenshot()
        check(bitmap != null, "help image exists")
        try {
            FileOutputStream(File(folder, name!! + ".png"))
                .use({ out -> bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, out) })
        } finally {
            bitmap!!.recycle()
        }
    }

    private fun guide(root: View, prefix: String?) {
        val text = root.findViewWithTag<TextView?>(prefix!! + "-protected-guide")
        check(text != null && text!!.isShown(), prefix!! + " guide is visible")
        val content = text!!.getText()!!.toString()
        check(content == ProtectedScreenGuide.TEXT, prefix!! + " uses identical offline guide")
        for (required in
            arrayOf<String?>(
                "chrome://flags/#incognito-screenshot",
                "Enabled",
                "Relaunch",
                "screenshots and thumbnails",
                "Default",
                "not guaranteed",
                "does not identify private browsing",
            )) check(content.contains(required!!), prefix + " instructions contain " + required)
    }

    @Throws(Exception::class)
    private fun shell(command: String?) {
        runner!!
            .getUiAutomation()!!
            .executeShellCommand(command)!!
            .use({ fd ->
                FileInputStream(fd!!.getFileDescriptor()!!)
                    .use({ input ->
                        val bytes = ByteArray(4096)
                        while (input.read(bytes) >= 0) {}
                    })
            })
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun register(context: Context?, receiver: BroadcastReceiver?) {
        val filter = IntentFilter("dev.dexdroid.test.PROTECTION_STATE")
        if (Build.VERSION.SDK_INT >= 33)
            context!!.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else context!!.registerReceiver(receiver, filter)
    }

    @Throws(Exception::class)
    fun run(): String {
        val context = runner!!.getTargetContext()
        val bound = CountDownLatch(1)
        val binding =
            object : ServiceConnection {
                public override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    session = (binder as SessionService.LocalBinder).get()
                    bound.countDown()
                }

                public override fun onServiceDisconnected(name: ComponentName?) {}
            }
        val receiver =
            object : BroadcastReceiver() {
                public override fun onReceive(ignored: Context?, intent: Intent) {
                    fixture = intent.getExtras()
                }
            }
        var connected = false
        var registered = false
        try {
            phone = DeviceTestSupport.startPhone(runner!!)
            connected =
                context!!.bindService(
                    Intent(context, SessionService::class.java),
                    binding,
                    Context.BIND_AUTO_CREATE,
                )
            check(bound.await(5, TimeUnit.SECONDS), "protection controller bound")
            await({ session!!.id >= 0 && !session!!.busy }, "protection desktop ready")
            register(context, receiver)
            registered = true
            main({ display = session!!.external!!.getDisplayId() })
            folder = File(context!!.getExternalCacheDir(), "protection-acceptance")
            check(folder!!.isDirectory() || folder!!.mkdirs(), "help evidence directory")
            val component =
                ComponentName("dev.dexdroid.test", "dev.dexdroid.fixture.ProtectedFixtureActivity")
            for (rotation in 0..3) {
                val selected = rotation
                main({
                    session!!.status = "Acceptance awaiting protected fixture"
                    session!!.launch(component, selected)
                })
                await(
                    {
                        (!session!!.busy &&
                            session!!.rotation == selected &&
                            session!!.status!!.startsWith("Opened dev.dexdroid.test"))
                    },
                    "protected fixture placement " + rotation,
                )
                await(
                    {
                        (fixture != null &&
                            fixture!!.getInt("display") == session!!.id &&
                            !fixture!!.getBoolean("secure"))
                    },
                    "normal fixture present",
                )
                check(!session!!.protectedContent, "ordinary app is not labeled protected")
                val task = fixture!!.getInt("task")
                secure(true)
                check(
                    fixture!!.getInt("task") == task,
                    "same task changes protection without task navigation",
                )
                await(
                    { ui({ shown("monitor-protected-notice") }) },
                    "minimal protected notice appears",
                )
                shot("monitor-notice-r" + rotation, true)
                val taps = fixture!!.getInt("taps")
                click("monitor-protected-help", rotation % 2 == 1)
                await(
                    {
                        ui({
                            (session!!.monitorShellPage() == "protected-help" &&
                                !session!!.monitorShellAnimating())
                        })
                    },
                    "monitor help opens",
                )
                main({ guide(session!!.monitorShellView()!!, "monitor") })
                shot("monitor-guide-r" + rotation, true)
                appTap(.1f, .5f)
                await(
                    {
                        ui({
                            (session!!.monitorShellPage() == "closed" &&
                                !session!!.monitorShellAnimating())
                        })
                    },
                    "outside tap closes help",
                )
                check(
                    fixture!!.getInt("taps") == taps,
                    "Help and outside dismissal never reach protected app",
                )
                check(
                    ui({ !shown("monitor-protected-notice") }),
                    "acknowledged notice stays dismissed",
                )
                if (rotation == 0) {
                    main({
                        phone!!
                            .getWindow()!!
                            .getDecorView()
                            .findViewWithTag<View?>("status-protected-help")!!
                            .performClick()
                    })
                    await({ ui({ phone!!.controllerMenu() != null }) }, "phone status opens help")
                    main({
                        guide(phone!!.controllerMenu()!!.getWindow()!!.getDecorView(), "phone")
                    })
                    shot("phone-guide", false)
                    main({
                        (phone!!
                                .controllerMenu()!!
                                .getWindow()!!
                                .getDecorView()
                                .findViewWithTag<View?>("phone-menu-scroll") as ScrollView)
                            .fullScroll(View.FOCUS_DOWN)
                    })
                    shot("phone-guide-bottom", false)
                    main({
                        phone!!
                            .controllerMenu()!!
                            .getWindow()!!
                            .getDecorView()
                            .findViewWithTag<View?>("phone-menu-close")!!
                            .performClick()
                    })
                }
                secure(false)
                secure(true)
                await(
                    { ui({ shown("monitor-protected-notice") }) },
                    "new protected episode restores notice",
                )
                click("monitor-protected-dismiss", false)
                Thread.sleep(2300)
                check(
                    session!!.protectedContent && ui({ !shown("monitor-protected-notice") }),
                    "dismissal survives next observation",
                )
                secure(false)
            }
            fixture = null
            shell(
                ("am start --display 0 --activity-multiple-task -n " +
                    component.flattenToString() +
                    " --ez secure true")
            )
            await(
                {
                    (fixture != null &&
                        fixture!!.getInt("display") == 0 &&
                        fixture!!.getBoolean("secure"))
                },
                "protected phone-only window is present",
            )
            val phoneTask = fixture!!.getInt("task")
            Thread.sleep(2300)
            check(
                !session!!.protectedContent,
                "phone-only protection never triggers monitor notice",
            )
            shell("am stack remove " + phoneTask)
            // startActivitySync waits for a new Activity; this singleTask controller
            // already exists. Restore it through the normal launcher instead.
            shell("am start --display 0 -n dev.dexdroid/.MainActivity")
            await(
                { ui({ phone!!.hasWindowFocus() }) },
                "existing controller restored after phone-only fixture",
            )
            main({ session!!.pauseDesktop() })
            check(!session!!.protectedContent, "Stop clears protection state")
            Thread.sleep(2300)
            check(!session!!.protectedContent, "stopped session rejects late inspection")
            main({ session!!.ensureDesktop() })
            await({ session!!.id >= 0 && !session!!.busy }, "desktop restored after protected test")
            return result.toString()
        } catch (problem: Throwable) {
            throw RuntimeException(result.toString() + "FAIL protected screen acceptance", problem)
        } finally {
            if (registered) context!!.unregisterReceiver(receiver)
            if (session != null)
                main({
                    if (phone!!.controllerMenu() != null) phone!!.controllerMenu()!!.dismiss()
                    session!!.pauseDesktop()
                })
            if (connected) context!!.unbindService(binding)
        }
    }
}
