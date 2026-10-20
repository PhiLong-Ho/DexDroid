package dev.dexdroid

import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.*
import java.util.function.BooleanSupplier

/** Real rotated monitor and phone-pointer interaction with the first-launch surface. */
class IdleAcceptance(private val runner: Instrumentation?) {
    private var plannedViewport: DeviceTestSupport.MonitorViewport? = null
    private var contactViewport: DeviceTestSupport.MonitorViewport? = null
    private val results: StringBuilder = StringBuilder()
    private var session: SessionService? = null
    private var binding: ServiceConnection? = null
    private var bound: Boolean = false
    private var preferences: Map<String?, *>? = null
    private var down: Long = 0
    private var phone: MainActivity? = null
    private var resumedPhone: MainActivity? = null
    private val controllerLifecycle: Application.ActivityLifecycleCallbacks =
        object : Application.ActivityLifecycleCallbacks {
            public override fun onActivityCreated(activity: Activity, state: Bundle?) {}

            public override fun onActivityStarted(activity: Activity) {}

            public override fun onActivityResumed(activity: Activity) {
                if (activity is MainActivity) resumedPhone = activity as MainActivity
            }

            public override fun onActivityPaused(activity: Activity) {
                if (activity === resumedPhone) resumedPhone = null
            }

            public override fun onActivityStopped(activity: Activity) {}

            public override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}

            public override fun onActivityDestroyed(activity: Activity) {}
        }

    private fun main(action: Runnable?) {
        runner!!.runOnMainSync(action)
    }

    private fun check(ok: Boolean, label: String?) {
        if (!ok) throw AssertionError(label)
        results.append("PASS ").append(label).append('\n')
    }

    @Throws(Exception::class)
    private fun await(predicate: BooleanSupplier, label: String?) {
        val until = SystemClock.uptimeMillis() + 10000
        while (!predicate.getAsBoolean() && SystemClock.uptimeMillis() < until) Thread.sleep(50)
        check(predicate.getAsBoolean(), label)
    }

    @Throws(Exception::class)
    private fun phoneTap(view: View?, requireFocus: Boolean = true) {
        check(view != null, "phone input target exists")
        await(
            {
                val ready = booleanArrayOf(false)
                main({
                    ready[0] =
                        (view!!.isShown() &&
                            view!!.getWidth() > 0 &&
                            view!!.getHeight() > 0 &&
                            !view!!.isLayoutRequested() &&
                            (!requireFocus || view!!.hasWindowFocus()))
                })
                ready!![0]
            },
            "phone input target measured and focused",
        )
        val point = IntArray(2)
        main({
            view!!.getLocationOnScreen(point)
            point[0] += view!!.getWidth() / 2
            point[1] += view!!.getHeight() / 2
            check(view!!.isShown(), "phone input target visible")
        })
        val start = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event =
                MotionEvent.obtain(
                    start,
                    SystemClock.uptimeMillis(),
                    action,
                    point[0].toFloat(),
                    point[1].toFloat(),
                    0,
                )
            event!!.setSource(InputDevice.SOURCE_TOUCHSCREEN)
            try {
                if (!requireFocus)
                    MotionEvent::class
                        .java!!
                        .getMethod("setDisplayId", Int::class.javaPrimitiveType)
                        .invoke(event, 0)
                check(
                    runner!!.getUiAutomation()!!.injectInputEvent(event, true),
                    "real phone input delivered",
                )
            } finally {
                event!!.recycle()
            }
            if (action == MotionEvent.ACTION_DOWN) Thread.sleep(50)
        }
    }

    private fun description(root: View, value: String): View? {
        if (value == java.lang.String.valueOf(root.getContentDescription())) return root
        if (root is ViewGroup) {
            val group = root as ViewGroup
            for (i in 0 until group!!.getChildCount()) {
                val found = description(group!!.getChildAt(i)!!, value)
                if (found != null) return found
            }
        }
        return null
    }

    @Throws(Exception::class)
    private fun resumePhone(rotation: Int) {
        val runningDisplay = session!!.id
        main({ phone!!.moveTaskToBack(true) })
        // Observe completion, not a fixed sleep that races Android's TO_BACK transition.
        await(
            {
                val hidden = booleanArrayOf(false)
                main({
                    hidden[0] =
                        (phone!!.getWindow()!!.getDecorView().getWindowVisibility() != View.VISIBLE)
                })
                hidden!![0]
            },
            "phone background transition completed " + rotation,
        )
        check(
            session!!.id == runningDisplay && session!!.hasDesktopApp(),
            "external app survives controller background " + rotation,
        )
        val opened =
            DeviceTestSupport.shell(
                runner,
                "am start -W --display 0 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n dev.dexdroid/.MainActivity",
            )
        check(!opened!!.contains("Error"), "phone launcher open accepted " + rotation)
        await({ phone!!.hasWindowFocus() }, "phone controller resumes after background " + rotation)
        check(
            !phone!!.isDestroyed() && phone!!.getDisplay()!!.getDisplayId() == 0,
            "same phone controller reused " + rotation,
        )
        phoneTap(description(phone!!.getWindow()!!.getDecorView(), "Settings"))
        await(
            { phone!!.controllerMenu() != null },
            "phone Settings accepts real input after resume " + rotation,
        )
        val close = arrayOf<View?>(null)
        await(
            {
                main({
                    val menu = phone!!.controllerMenu()
                    close[0] =
                        if (menu == null) null
                        else
                            menu!!
                                .getWindow()!!
                                .getDecorView()
                                .findViewWithTag<View?>("phone-menu-close")
                })
                close!![0] != null
            },
            "phone Settings contents attached " + rotation,
        )
        phoneTap(close!![0])
        await(
            { phone!!.controllerMenu() == null },
            "phone Close accepts real input after resume " + rotation,
        )
        await(
            { phone!!.hasWindowFocus() },
            "phone controller focus restored after Close " + rotation,
        )
        check(
            session!!.id == runningDisplay,
            "controller resume retains desktop display " + rotation,
        )
        val before = InputTestActivity.touchEvents
        val point = physical(session!!.width() * .5f, session!!.height() * .5f)
        tap(point[0], point[1])
        await(
            { InputTestActivity.touchEvents > before },
            "fresh external input after phone resume " + rotation,
        )
    }

    private fun restore() {
        if (preferences == null || session == null) return
        val edit = session!!.prefs!!.edit()!!.clear()
        for (item in preferences!!.entries) {
            val value = item!!.value
            val key = item!!.key
            if (value is Boolean) edit!!.putBoolean(key, (value as Boolean?)!!)
            else if (value is Int) edit!!.putInt(key, (value as Int?)!!)
            else if (value is Float) edit!!.putFloat(key, (value as Float?)!!)
            else if (value is String) edit!!.putString(key, value as String?)
            else if (value is Long) edit!!.putLong(key, (value as Long?)!!)
        }
        edit!!.commit()
    }

    private fun view(tag: String?): View? {
        val root = session!!.monitorShellView()
        return if (root == null) null else root!!.findViewWithTag<View?>(tag)
    }

    @Throws(Exception::class)
    private fun page(expected: String?) {
        await(
            {
                val settled = booleanArrayOf(false)
                main({
                    val root = session!!.monitorShellView()
                    if (
                        (root == null ||
                            expected != session!!.monitorShellPage() ||
                            session!!.monitorShellAnimating() ||
                            !root!!.isLaidOut() ||
                            root!!.isLayoutRequested())
                    )
                        return@main
                    if (expected != "closed") {
                        val panel = view("monitor-panel")
                        val close = view("monitor-close")
                        if (
                            (panel == null ||
                                close == null ||
                                !panel!!.isLaidOut() ||
                                panel!!.isLayoutRequested() ||
                                !close!!.isShown() ||
                                close!!.getWidth() == 0 ||
                                close!!.getHeight() == 0 ||
                                close!!.isLayoutRequested())
                        )
                            return@main
                    }
                    settled[0] = true
                })
                settled!![0]
            },
            "idle menu settled " + expected!!,
        )
    }

    @Throws(Exception::class)
    private fun fresh() {
        main(Runnable(session!!::pauseDesktop))
        await({ session!!.id < 0 && session!!.monitorShellView() == null }, "old desktop released")
        main(Runnable(session!!::ensureDesktop))
        await(
            { session!!.id >= 0 && !session!!.busy && session!!.monitorShellView() != null },
            "fresh desktop ready",
        )
        Thread.sleep(300)
        check(!session!!.hasDesktopApp(), "fresh desktop has not launched an app")
        page("closed")
    }

    @Throws(Exception::class)
    private fun event(action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) {
            down = now
            contactViewport = plannedViewport ?: viewport()
            contactViewport!!.verify(runner, session)
            plannedViewport = null
        }
        val event = MotionEvent.obtain(down, now, action, x, y, 0)
        event!!.setSource(InputDevice.SOURCE_TOUCHSCREEN)
        try {
            check(
                DeviceTestSupport.injectOnDisplay(event, contactViewport!!.externalDisplay),
                "idle physical monitor event " + action,
            )
        } finally {
            event!!.recycle()
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                contactViewport = null
        }
    }

    @Throws(Exception::class)
    private fun tap(x: Float, y: Float) {
        event(MotionEvent.ACTION_DOWN, x, y)
        event(MotionEvent.ACTION_UP, x, y)
        Thread.sleep(80)
    }

    private fun viewport(): DeviceTestSupport.MonitorViewport =
        DeviceTestSupport.monitorViewport(runner, session)

    private fun physical(x: Float, y: Float): FloatArray {
        val captured = contactViewport ?: plannedViewport ?: viewport()
        val point = captured.mapVirtualToScreen(x, y)
        if (contactViewport == null) {
            captured.verify(runner, session)
            plannedViewport = captured
        }
        return point
    }

    private fun bounds(tag: String?): Rect {
        val rect = Rect()
        main({
            val target = view(tag)
            check(
                target != null && target!!.isShown() && target!!.getGlobalVisibleRect(rect),
                "idle control visible " + tag!!,
            )
        })
        return rect
    }

    @Throws(Exception::class)
    private fun click(tag: String?) {
        val rect = bounds(tag)
        tap(rect.exactCenterX(), rect.exactCenterY())
    }

    @Throws(Exception::class)
    private fun pointerClick(tag: String?) {
        main({
            val target = view(tag)
            val root = session!!.monitorShellView()
            check(target != null && target!!.isShown(), "idle pointer target visible " + tag!!)
            val visible = Rect()
            val toGlobal = Matrix()
            val rootToGlobal = Matrix()
            val toLogical = Matrix()
            target!!.transformMatrixToGlobal(toGlobal)
            root!!.transformMatrixToGlobal(rootToGlobal)
            val invertible = rootToGlobal.invert(toLogical)
            check(
                (target!!.getWidth() > 0 &&
                    target!!.getHeight() > 0 &&
                    target!!.getGlobalVisibleRect(visible) &&
                    !visible.isEmpty() &&
                    invertible),
                "idle pointer target has measured hittable bounds " + tag!!,
            )
            // Include actual animation/scroll transforms rather than assuming every
            // ancestor has only its layout left/top offsets.
            val center = floatArrayOf(target!!.getWidth() / 2f, target!!.getHeight() / 2f)
            toGlobal.mapPoints(center)
            toLogical.mapPoints(center)
            session!!.cx = center!![0]
            session!!.cy = center!![1]
            val delivered = intArrayOf(0, 0)
            target!!.setOnTouchListener({ v, event ->
                if (event!!.getActionMasked() == MotionEvent.ACTION_DOWN) delivered!![0]++
                if (event!!.getActionMasked() == MotionEvent.ACTION_UP) delivered!![1]++
                false
            })
            try {
                session!!.click()
            } finally {
                target!!.setOnTouchListener(null)
            }
            check(
                delivered!![0] == 1 && delivered!![1] == 1,
                ("phone pointer dispatch reaches the target once for DOWN and UP " + tag!!),
            )
        })
        Thread.sleep(80)
    }

    private fun idle(shown: Boolean) {
        main({
            val idle = view("monitor-idle")
            val apps = view("monitor-idle-apps")
            check(idle != null && apps is ImageButton, "native idle background and icon exist")
            check(
                idle!!.isShown() == shown && apps!!.isShown() == shown,
                "idle visibility " + shown,
            )
            if (shown) {
                val icon = apps as ImageButton?
                check(
                    (icon!!.getDrawable() != null &&
                        "Apps".contentEquals(icon!!.getContentDescription()!!)),
                    "idle icon has Apps description and drawable",
                )
                val density = icon!!.getResources()!!.getDisplayMetrics()!!.density
                check(
                    (Math.abs(icon!!.getWidth() / density - 76) < 1 &&
                        Math.abs(icon!!.getHeight() / density - 76) < 1),
                    "idle Apps target is 76 dp square",
                )
                check(!idle!!.isForceDarkAllowed(), "idle monochrome surface preserves its colors")
            }
        })
    }

    private fun centered() {
        val rect = bounds("monitor-idle-apps")
        val capturedViewport = viewport()
        check(
            (Math.abs(rect.exactCenterX() - capturedViewport.screenBounds().exactCenterX()) < 2 &&
                Math.abs(rect.exactCenterY() - capturedViewport.screenBounds().exactCenterY()) < 2),
            "idle Apps centered in physical rotation " + session!!.rotation,
        )
        main({
            val idle = view("monitor-idle")
            check(
                (idle!!.getWidth() == session!!.width() &&
                    idle!!.getHeight() == session!!.height()),
                "idle background fills logical rotated desktop",
            )
        })
    }

    @Throws(Exception::class)
    private fun rendered() {
        val capturedViewport = viewport()
        val bitmap =
            Bitmap.createBitmap(
                capturedViewport.width,
                capturedViewport.height,
                Bitmap.Config.ARGB_8888,
            )
        val copied = CountDownLatch(1)
        val result = intArrayOf(-1)
        try {
            main({
                capturedViewport.verify(runner, session)
                PixelCopy.request(
                    session!!.monitorWindow()!!,
                    bitmap,
                    { value ->
                        result[0] = value
                        copied.countDown()
                    },
                    Handler(Looper.getMainLooper()!!),
                )
            })
            check(
                copied.await(5, TimeUnit.SECONDS) && result!![0] == PixelCopy.SUCCESS,
                "idle rendering captured",
            )
            for (logical in
                arrayOf<FloatArray?>(
                    floatArrayOf(session!!.width() * .1f, session!!.height() * .5f),
                    floatArrayOf(session!!.width() * .9f, session!!.height() * .5f),
                )) {
                val p = capturedViewport.mapVirtualToScreen(logical!![0], logical!![1])
                val color =
                    bitmap.getPixel(
                        Math.round(p[0] - capturedViewport.originX),
                        Math.round(p[1] - capturedViewport.originY),
                    )
                check(
                    (Color.red(color) == Color.green(color) &&
                        Color.red(color) == Color.blue(color) &&
                        Color.red(color) < 40),
                    "rendered idle background is dark monochrome",
                )
            }
            val rect = bounds("monitor-idle-apps")
            rect.offset(-capturedViewport.originX, -capturedViewport.originY)
            var white = 0
            var dark = 0
            for (y in rect.top until rect.bottom) for (x in rect.left until rect.right) {
                val color = bitmap.getPixel(x, y)
                if ((Color.red(color) > 200 && Color.green(color) > 200 && Color.blue(color) > 200))
                    white++
                if (Color.red(color) < 50 && Color.green(color) < 50 && Color.blue(color) < 50)
                    dark++
            }
            check(white > 20 && dark > white, "rendered idle Apps icon is white on charcoal")
        } finally {
            bitmap.recycle()
        }
    }

    @Throws(Exception::class)
    private fun renderedApp(rotation: Int) {
        val capturedViewport = viewport()
        val bitmap =
            Bitmap.createBitmap(
                capturedViewport.width,
                capturedViewport.height,
                Bitmap.Config.ARGB_8888,
            )
        try {
            val deadline = SystemClock.uptimeMillis() + 5000
            var rendered = false
            var color = 0
            do {
                val copied = CountDownLatch(1)
                val code = intArrayOf(-1)
                main({
                    capturedViewport.verify(runner, session)
                    PixelCopy.request(
                        session!!.monitorWindow()!!,
                        bitmap,
                        { result ->
                            code[0] = result
                            copied.countDown()
                        },
                        Handler(Looper.getMainLooper()!!),
                    )
                })
                if (!copied.await(5, TimeUnit.SECONDS) || code!![0] != PixelCopy.SUCCESS)
                    throw AssertionError("PixelCopy app capture failed: " + code!![0])
                val sample = capturedViewport.mapNormalizedToScreen(.3f, .7f)
                color =
                    bitmap.getPixel(
                        Math.round(sample[0] - capturedViewport.originX),
                        Math.round(sample[1] - capturedViewport.originY),
                    )
                rendered =
                    (Color.blue(color) > Color.red(color) + 20 &&
                        Color.green(color) > Color.red(color) + 5)
                if (!rendered) Thread.sleep(50)
            } while (!rendered && SystemClock.uptimeMillis() < deadline)
            val folder =
                File(
                    runner!!.getTargetContext()!!.getExternalCacheDir(),
                    "compatibility-acceptance",
                )
            check(folder.isDirectory() || folder.mkdirs(), "compatibility screenshot directory")
            FileOutputStream(File(folder, "app-before-menu-r" + rotation + ".png"))
                .use({ stream ->
                    check(
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream),
                        "saved visible app screenshot " + rotation,
                    )
                })
            if (android.os.Build.VERSION.SDK_INT == 30) {
                val internal =
                    File(
                        runner!!.getTargetContext()!!.getCacheDir(),
                        "app-before-menu-r" + rotation + ".png",
                    )
                FileOutputStream(internal)
                    .use({ stream ->
                        check(
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream),
                            "saved legacy app-private capture " + rotation,
                        )
                    })
            }
            check(
                rendered,
                ("rendered test-app pixels replace monochrome idle overlay " +
                    rotation +
                    " color=" +
                    Integer.toHexString(color)),
            )
        } finally {
            bitmap.recycle()
        }
    }

    /** Reject the platform foreground adapter on the real bound service. */
    @Throws(Exception::class)
    private fun foregroundRejectionRecovery() {
        main(Runnable(session!!::pauseDesktop))
        val adapter = SessionService::class.java!!.getDeclaredField("foregroundPromotion")
        adapter.setAccessible(true)
        val original = adapter.get(session) as SessionService.ForegroundPromotion?
        main({
            try {
                adapter.set(
                    session,
                    SessionService.ForegroundPromotion { _ ->
                        throw IllegalStateException("Controlled foreground startup rejection")
                    },
                )
                session!!.ensureDesktop()
                check(
                    session!!.id < 0 && !session!!.busy && !session!!.sessionAwake(),
                    "foreground rejection clears pending session and wake lock",
                )
            } catch (e: ReflectiveOperationException) {
                throw RuntimeException(e)
            } finally {
                try {
                    adapter.set(session, original)
                } catch (e: IllegalAccessException) {
                    throw RuntimeException(e)
                }
            }
        })
        await(
            {
                val explained = booleanArrayOf(false)
                main({
                    explained[0] =
                        (session!!.status!!.contains("Controlled foreground startup rejection") &&
                            session!!.status!!.contains("Open DexDroid on the phone"))
                })
                explained!![0]
            },
            "foreground rejection explains phone-reopen recovery without escaping callback",
        )
        reopenController("foreground rejection", true)
    }

    /** Fault-inject the state after an Activity request returned without a host attachment. */
    @Throws(Exception::class)
    private fun missingHostRecovery() {
        main(Runnable(session!!::stopDesktop))
        val workerField = SessionService::class.java!!.getDeclaredField("worker")
        workerField.setAccessible(true)
        (workerField.get(session) as java.util.concurrent.ExecutorService)
            .submit({})
            .get(10, TimeUnit.SECONDS)
        val type = Class.forName("dev.dexdroid.SessionService\$Monitor")
        val constructor =
            type.getDeclaredConstructor(SessionService::class.java, Display::class.java)
        constructor.setAccessible(true)
        val monitorField = SessionService::class.java!!.getDeclaredField("monitor")
        val generationField = SessionService::class.java!!.getDeclaredField("generation")
        val wakeField = SessionService::class.java!!.getDeclaredField("sessionWakeLock")
        val deadlineField = SessionService::class.java!!.getDeclaredField("monitorStartupDeadline")
        val tokenField = type.getDeclaredField("token")
        for (f in
            arrayOf<java.lang.reflect.Field?>(
                monitorField,
                generationField,
                wakeField,
                deadlineField,
                tokenField,
            )) f!!.setAccessible(true)
        val pending = arrayOf<Any?>(null)
        val staleDeadline = arrayOf<Runnable?>(null)
        val oldGeneration = intArrayOf(-1)
        val late = arrayOf<MonitorActivity?>(null)
        main({
            try {
                late[0] = MonitorActivity()
                pending[0] = constructor.newInstance(session, session!!.external)
                monitorField.set(session, pending!![0])
                session!!.busy = true
                (wakeField.get(session) as PowerManager.WakeLock).acquire()
                oldGeneration[0] = generationField.getInt(session)
                late!![0]!!.setIntent(
                    Intent().putExtra("monitorToken", tokenField.get(pending!![0]) as String?)
                )
                session!!.awaitMonitorHost()
                staleDeadline[0] = deadlineField.get(session) as Runnable?
                check(
                    session!!.id < 0 && session!!.busy && session!!.sessionAwake(),
                    "unattached host remains pending before deadline",
                )
            } catch (e: ReflectiveOperationException) {
                throw RuntimeException(e)
            }
        })
        val started = SystemClock.uptimeMillis()
        await(
            {
                val recovered = booleanArrayOf(false)
                main({
                    recovered[0] =
                        (!session!!.busy &&
                            session!!.id < 0 &&
                            !session!!.sessionAwake() &&
                            session!!.status!!.contains("Open DexDroid on the phone"))
                })
                recovered!![0]
            },
            "unattached host times out with cleanup and foreground recovery guidance",
        )
        check(
            SystemClock.uptimeMillis() - started >= 4500,
            "startup waits its bounded five-second attachment interval",
        )
        main({
            check(
                !session!!.attachMonitor(late!![0], oldGeneration!![0]),
                "late host rejected after timeout",
            )
        })
        reopenController("unattached host timeout", true)
        val newDisplay = session!!.id
        main({
            try {
                check(
                    !session!!.attachMonitor(late!![0], oldGeneration!![0]),
                    "old generation cannot attach to recovered desktop",
                )
                check(
                    !session!!.attachMonitor(late!![0], generationField.getInt(session)),
                    "old monitor token cannot attach to recovered desktop",
                )
                staleDeadline!![0]!!.run()
                check(
                    session!!.id == newDisplay && !session!!.busy && session!!.sessionAwake(),
                    "stale host timeout cannot stop recovered desktop",
                )
                check(deadlineField.get(session) == null, "startup deadline cleared after teardown")
            } catch (e: ReflectiveOperationException) {
                throw RuntimeException(e)
            }
        })
    }

    @Throws(Exception::class)
    private fun reopenController(reason: String?, background: Boolean) {
        if (background) {
            main({ phone!!.moveTaskToBack(true) })
            await(
                { phone!!.getWindow()!!.getDecorView().getWindowVisibility() != View.VISIBLE },
                "controller backgrounded before launcher recovery: " + reason!!,
            )
        }
        val opened =
            DeviceTestSupport.shell(
                runner,
                "am start -W --display 0 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n dev.dexdroid/.MainActivity",
            )
        check(!opened!!.contains("Error"), "launcher accepts recovery request: " + reason!!)
        try {
            await(
                {
                    val ready = booleanArrayOf(false)
                    main({
                        ready[0] =
                            (!phone!!.isDestroyed() &&
                                resumedPhone === phone &&
                                (phone!!.getWindow()!!.getDecorView().getWindowVisibility() ==
                                    View.VISIBLE) &&
                                (session!!.listener === controllerListener(phone)) &&
                                session!!.id >= 0 &&
                                !session!!.busy &&
                                session!!.monitorShellView() != null)
                    })
                    ready!![0]
                },
                "same visible bound controller launcher recovery: " + reason!!,
            )
            verifyRecoveredInput(reason)
        } catch (failure: Throwable) {
            try {
                captureRecoveryFailure(reason)
            } catch (captureFailure: Throwable) {
                failure!!.addSuppressed(captureFailure!!)
            }

            throw failure
        }

        check(
            phone!!.getDisplay()!!.getDisplayId() == 0 && session!!.desktopRequested(),
            "launcher recovery retains phone placement and desktop request: " + reason!!,
        )
    }

    private fun controllerListener(activity: MainActivity?): Any? {
        try {
            val listener = MainActivity::class.java!!.getDeclaredField("uiListener")
            listener.setAccessible(true)
            return listener.get(activity)
        } catch (error: ReflectiveOperationException) {
            throw IllegalStateException("Controller listener observation unavailable", error)
        }
    }

    @Throws(Exception::class)
    private fun verifyRecoveredInput(reason: String?) {
        val display = session!!.id
        captureRecoveryState(reason, "recovery-before-phone")
        phoneTap(description(phone!!.getWindow()!!.getDecorView(), "Settings"), false)
        await(
            { phone!!.controllerMenu() != null && phone!!.controllerMenu()!!.isShowing() },
            "recovered phone Settings responds to native contact: " + reason!!,
        )
        val close = arrayOf<View?>(null)
        main({
            close[0] =
                phone!!
                    .controllerMenu()!!
                    .getWindow()!!
                    .getDecorView()
                    .findViewWithTag<View?>("phone-menu-close")
        })
        phoneTap(close!![0], false)
        await(
            { phone!!.controllerMenu() == null },
            "recovered phone Close responds to native contact: " + reason!!,
        )
        captureRecoveryState(reason, "recovery-after-phone")
        main({ session!!.setMonitorTouchEnabled(true) })
        click("monitor-idle-apps")
        page("home")
        click("monitor-app-search")
        await(
            {
                (view("monitor-search-keyboard") != null &&
                    view("monitor-search-keyboard")!!.isShown())
            },
            "recovered monitor search responds to fresh native contact: " + reason!!,
        )
        val query = "input test"
        for (letter in query.toCharArray()) click(
            "monitor-key-" + (if (letter == ' ') "space" else (letter).toString())
        )
        await(
            {
                (query.contentEquals((view("monitor-app-search") as EditText).getText()!!) &&
                    view("app.dev.dexdroid") != null)
            },
            "recovered monitor local keys filter fixture: " + reason!!,
        )
        click("app.dev.dexdroid")
        page("closed")
        await(
            { !session!!.busy && session!!.activePackage == "dev.dexdroid" },
            "recovered fixture launched: " + reason!!,
        )
        val fixture = ComponentName(runner!!.getTargetContext()!!, InputTestActivity::class.java!!)
        val uid = runner!!.getTargetContext()!!.getApplicationInfo()!!.uid
        val width = session!!.width()
        val height = session!!.height()
        val deadline = SystemClock.uptimeMillis() + 10000
        var task: Int
        do {
            task = DeviceTestSupport.nativeInputTask(runner, display, fixture)
            if (task >= 0) break
            Thread.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        check(task >= 0, "recovered fixture owns one current native task: " + reason!!)
        var settled: Boolean
        do {
            settled =
                (session!!.id == display &&
                    !session!!.busy &&
                    session!!.width() == width &&
                    session!!.height() == height &&
                    DeviceTestSupport.settledInputWindow(
                        runner,
                        display,
                        task,
                        fixture,
                        uid,
                        width,
                        height,
                    ))
            if (settled) break
            Thread.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        check(settled, "recovered fixture native window settled before contact: " + reason!!)
        val point = physical(session!!.width() * .5f, session!!.height() * .5f)
        event(MotionEvent.ACTION_DOWN, point[0], point[1])
        val contact = down
        Thread.sleep(50)
        event(MotionEvent.ACTION_UP, point[0], point[1])
        await(
            {
                (InputTestActivity.lastUpDownTime == contact &&
                    Math.abs(InputTestActivity.lastUpX - width * .5f) < 2 &&
                    Math.abs(InputTestActivity.lastUpY - height * .5f) < 2)
            },
            "recovered fixture completes exact native contact at mapped center: " + reason!!,
        )
        check(
            session!!.id == display && session!!.desktopRequested() && session!!.sessionAwake(),
            "observable recovery retains desktop ownership: " + reason!!,
        )
        captureRecoveryState(reason, "recovery-after-monitor")
    }

    private fun controllerState(activity: MainActivity?): String {
        if (activity == null) return "none"
        var owner: String?
        try {
            val listener = MainActivity::class.java!!.getDeclaredField("uiListener")
            listener.setAccessible(true)
            owner = java.lang.Boolean.toString(session!!.listener === listener.get(activity))
        } catch (error: ReflectiveOperationException) {
            owner = "unavailable:" + error!!.javaClass.getSimpleName()
        }

        return ("identity=" +
            Integer.toHexString(System.identityHashCode(activity)) +
            " destroyed=" +
            activity!!.isDestroyed() +
            " finishing=" +
            activity!!.isFinishing() +
            " task=" +
            activity!!.getTaskId() +
            " display=" +
            activity!!.getDisplay()!!.getDisplayId() +
            " visibility=" +
            activity!!.getWindow()!!.getDecorView().getWindowVisibility() +
            " focus=" +
            activity!!.hasWindowFocus() +
            " listenerOwner=" +
            owner)
    }

    /** Private failure artifacts are captured before restore/teardown changes native focus. */
    @Throws(Exception::class)
    private fun captureRecoveryFailure(reason: String?) {
        captureRecoveryState(reason, "recovery")
    }

    @Throws(Exception::class)
    private fun captureRecoveryState(reason: String?, prefix: String?) {
        val state = arrayOf<String?>("")
        main({
            state[0] =
                ("reason=" +
                    reason +
                    "\ncaptured=" +
                    controllerState(phone) +
                    "\nresumed=" +
                    controllerState(resumedPhone) +
                    "\nsameInstance=" +
                    (phone === resumedPhone) +
                    "\nid=" +
                    session!!.id +
                    " busy=" +
                    session!!.busy +
                    " shell=" +
                    (session!!.monitorShellView() != null) +
                    " requested=" +
                    session!!.desktopRequested() +
                    " awake=" +
                    session!!.sessionAwake() +
                    "\n")
        })
        results.append("RECOVERY STATE ").append(state!![0])
        val folder =
            File(runner!!.getTargetContext()!!.getExternalCacheDir(), "compatibility-acceptance")
        if (!folder.isDirectory() && !folder.mkdirs())
            throw java.io.IOException("Recovery diagnostic directory unavailable")
        writeRecoveryFile(folder, prefix!! + "-state.txt", state!![0]!!)
        var captureFailure: Exception? = null
        for (service in arrayOf<String?>("window", "input")) {
            try {
                writeRecoveryFile(
                    folder,
                    prefix + "-" + service + ".txt",
                    DeviceTestSupport.shell(runner, "dumpsys -t 1 " + service!!)!!,
                )
            } catch (error: Exception) {
                if (captureFailure == null) captureFailure = error
                else captureFailure!!.addSuppressed(error!!)
            }
        }
        if (captureFailure != null) throw captureFailure
    }

    @Throws(Exception::class)
    private fun writeRecoveryFile(folder: File?, name: String?, contents: String) {
        FileOutputStream(File(folder, name!!))
            .use({ stream ->
                stream.write(contents.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
            })
    }

    @Throws(Exception::class)
    private fun stoppedControllerRecovery() {
        phoneTap(description(phone!!.getWindow()!!.getDecorView(), "Stop desktop"), false)
        await(
            {
                (session!!.id < 0 &&
                    !session!!.busy &&
                    !session!!.desktopRequested() &&
                    phone!!.hasWindowFocus())
            },
            "explicit Stop restores controller focus without pending startup",
        )
        Thread.sleep(750)
        check(
            session!!.id < 0 && !session!!.desktopRequested() && !session!!.sessionAwake(),
            "controller focus restoration keeps explicit Stop stopped",
        )
        reopenController("explicit Stop while controller visible", false)
    }

    @Throws(Exception::class)
    fun run(): String {
        try {
            val diagnostics =
                File(
                    runner!!.getTargetContext()!!.getExternalCacheDir(),
                    "compatibility-acceptance",
                )
            for (prefix in
                arrayOf<String?>(
                    "recovery",
                    "recovery-before-phone",
                    "recovery-after-phone",
                    "recovery-after-monitor",
                )) for (suffix in arrayOf<String?>("state", "window", "input")) {
                val name = prefix + "-" + suffix + ".txt"
                val previous = File(diagnostics, name!!)
                if (previous.exists() && !previous.delete())
                    throw java.io.IOException("Previous recovery diagnostic not removed: " + name!!)
            }
            phone = DeviceTestSupport.startPhone(runner!!)
            main({
                resumedPhone = phone
                phone!!.getApplication()!!.registerActivityLifecycleCallbacks(controllerLifecycle)
            })
            val connected = CountDownLatch(1)
            binding =
                object : ServiceConnection {
                    public override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
                        session = (b as SessionService.LocalBinder).get()
                        connected.countDown()
                    }

                    public override fun onServiceDisconnected(n: ComponentName?) {}
                }
            bound =
                runner!!
                    .getTargetContext()!!
                    .bindService(
                        Intent(runner!!.getTargetContext(), SessionService::class.java),
                        binding!!,
                        Context.BIND_AUTO_CREATE,
                    )
            check(connected.await(5, TimeUnit.SECONDS), "idle controller bound")
            await({ session!!.id >= 0 && !session!!.busy }, "initial desktop ready")
            preferences = HashMap<String?, Any?>(session!!.prefs!!.getAll()!!)
            foregroundRejectionRecovery()
            missingHostRecovery()
            stoppedControllerRecovery()
            fresh()
            val idleDisplay = session!!.id
            main({ session!!.key(KeyEvent.KEYCODE_BACK) })
            page("closed")
            check(
                session!!.id == idleDisplay && !session!!.hasDesktopApp(),
                "idle Back preserves the unused desktop",
            )
            main({
                session!!.setMonitorNavigationEnabled(true)
                session!!.setMonitorTouchEnabled(true)
            })
            for (r in 0..3) {
                val rotation = r
                main({ session!!.rotate(rotation, false) })
                await(
                    { !session!!.busy && session!!.rotation == rotation },
                    "idle rotation ready " + r,
                )
                Thread.sleep(250)
                idle(true)
                centered()
                rendered()
                click("monitor-idle-apps")
                page("home")
                idle(false)
                check(!session!!.hasDesktopApp(), "opening Apps does not mark an app launched")
                click("monitor-close")
                page("closed")
                idle(true)
                pointerClick("monitor-idle-apps")
                page("home")
                check(session!!.pointerVisible, "phone pointer restores cursor on idle launcher")
                pointerClick("monitor-close")
                page("closed")
                idle(true)
                click("monitor-idle-apps")
                page("home")
                val outside = physical(4 * session!!.dpi / 160f, session!!.height() * .5f)
                DeviceTestSupport.usableMonitorOutside(runner, session, outside)
                tap(outside[0], outside[1])
                page("closed")
                idle(true)
                val blank = physical(session!!.width() * .25f, session!!.height() * .5f)
                tap(blank[0], blank[1])
                page("closed")
                check(!session!!.hasDesktopApp(), "blank idle tap does not select an app")
                check(!session!!.pointerVisible, "physical idle tap hides phone pointer")
                main({
                    session!!.cx = session!!.width() * .25f
                    session!!.cy = session!!.height() * .5f
                    session!!.click()
                    session!!.wheel(0f, -2f)
                })
                page("closed")
                idle(true)
            }
            main({
                session!!.clearDiagnostics()
                session!!.launch(ComponentName("dev.dexdroid", "dev.dexdroid.DoesNotExist"), null)
            })
            await(
                { session!!.diagnosticText().contains("Desktop operation failed:") },
                "rejected launch reports an error",
            )
            check(!session!!.hasDesktopApp(), "rejected launch retains idle state")
            idle(true)
            click("monitor-idle-apps")
            page("home")
            click("app.dev.dexdroid")
            page("closed")
            await(
                { !session!!.busy && session!!.hasDesktopApp() },
                "successful monitor app selection clears first-launch state",
            )
            idle(false)
            main(Runnable(session!!::notifyUi))
            idle(false)
            for (r in 0..3) {
                fresh()
                val selected = r
                main({
                    session!!.launch(
                        ComponentName(
                            runner!!.getTargetContext()!!,
                            InputTestActivity::class.java!!,
                        ),
                        selected,
                    )
                })
                await(
                    {
                        (!session!!.busy &&
                            session!!.hasDesktopApp() &&
                            session!!.rotation == selected)
                    },
                    "visible launch without opening monitor menus " + r,
                )
                page("closed")
                idle(false)
                Thread.sleep(300)
                renderedApp(r)
                val touches = InputTestActivity.touchEvents
                val target = physical(session!!.width() * .3f, session!!.height() * .7f)
                tap(target[0], target[1])
                await(
                    { InputTestActivity.touchEvents > touches },
                    "direct app input before menus " + r,
                )
                val backs = InputTestActivity.backEvents
                main({ session!!.key(KeyEvent.KEYCODE_BACK) })
                await(
                    { InputTestActivity.backEvents > backs },
                    "external app Back on current Android " + r,
                )
                resumePhone(r)
            }
            fresh()
            idle(true)
            centered()
            main({ session!!.preview(1920, 1080, 240) })
            await({ !session!!.busy && session!!.baseH == 1080 }, "idle Fit preview ready")
            Thread.sleep(250)
            idle(true)
            centered()
            tap(960f, 45f)
            page("closed")
            idle(true)
            click("monitor-idle-apps")
            page("home")
            tap(960f, 45f)
            page("closed")
            idle(true)
            main({ session!!.confirm(false) })
            await({ !session!!.busy }, "idle Fit preview reverted")
            restore()
            return results.toString()
        } catch (error: Throwable) {
            throw RuntimeException(results.toString() + "FAIL idle acceptance", error)
        } finally {
            if (phone != null)
                main({
                    phone!!
                        .getApplication()!!
                        .unregisterActivityLifecycleCallbacks(controllerLifecycle)
                })
            restore()
            if (bound) runner!!.getTargetContext()!!.unbindService(binding!!)
        }
    }
}
