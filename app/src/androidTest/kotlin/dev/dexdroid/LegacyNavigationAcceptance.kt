package dev.dexdroid

import android.app.*
import android.content.*
import android.os.*
import android.view.*
import dev.dexdroid.fixture.LaunchParentActivity
import java.util.concurrent.*
import java.util.function.BooleanSupplier

/** Real legacy host/IPC/navigation regression without unrelated output-resize prerequisites. */
class LegacyNavigationAcceptance(private val runner: Instrumentation?) {
    private val results: StringBuilder = StringBuilder()
    private var session: SessionService? = null
    private var phone: MainActivity? = null
    @Volatile private var state: Bundle? = null

    private fun check(value: Boolean, text: String?) {
        if (!value) throw AssertionError(text)
        results.append("PASS ").append(text).append('\n')
    }

    @Throws(Exception::class)
    private fun await(condition: BooleanSupplier, text: String?) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < deadline) Thread.sleep(40)
        check(condition.getAsBoolean(), text)
    }

    private fun phoneVisible(): Boolean {
        val shown = booleanArrayOf(false)
        runner!!.runOnMainSync({
            shown[0] =
                (!phone!!.isDestroyed() &&
                    !phone!!.isFinishing() &&
                    (phone!!.getWindow()!!.getDecorView().getWindowVisibility() == View.VISIBLE))
        })
        return shown!![0]
    }

    private fun fixtureTaskAbsent(task: Int, app: ComponentName): Boolean {
        try {
            val snapshot = DeviceTestSupport.shell(runner, "am stack list")!!
            if (
                snapshot.contains("Error:") ||
                    snapshot.contains("DUMP TIMEOUT") ||
                    snapshot.contains("Permission Denial") ||
                    DisplayTasks.parse(snapshot, app.flattenToString()).isEmpty()
            )
                throw IllegalStateException("Navigation cleanup task snapshot unavailable")
            return !java.util.regex.Pattern.compile("(?m)^\\s*taskId=" + task + ":")
                .matcher(snapshot)
                .find()
        } catch (error: Exception) {
            throw IllegalStateException("Cannot observe navigation fixture cleanup", error)
        }
    }

    @Throws(Exception::class)
    private fun showPhone() {
        runner!!.runOnMainSync({
            runner!!
                .getTargetContext()!!
                .startActivity(
                    Intent(runner!!.getTargetContext(), MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    ActivityOptions.makeBasic()!!.setLaunchDisplayId(0)!!.toBundle(),
                )
        })
        await(BooleanSupplier({ this.phoneVisible() }), "phone controller foreground established")
    }

    @Throws(Exception::class)
    private fun tap() {
        await(
            {
                (state != null &&
                    state!!.getInt("display") == session!!.id &&
                    state!!.getInt("targetWidth") > 0 &&
                    state!!.getInt("targetHeight") > 0)
            },
            "native navigation frame ready",
        )
        val expected = Bundle(state ?: throw AssertionError("Navigation fixture state missing"))
        val component =
            ComponentName(
                "dev.dexdroid.test",
                when (expected.getString("page")) {
                    "navigation-parent" -> "dev.dexdroid.fixture.NavigationFixtureActivity"
                    "navigation-child" -> "dev.dexdroid.fixture.NavigationFixtureActivity\$Child"
                    else -> throw AssertionError("Unexpected navigation fixture page")
                },
            )
        val geometry = IntArray(6)
        runner!!.runOnMainSync {
            geometry[0] = session!!.id
            geometry[1] = session!!.rotation
            geometry[2] = session!!.dpi
            geometry[3] = session!!.width()
            geometry[4] = session!!.height()
            geometry[5] = session!!.external?.displayId ?: -1
        }
        check(expected.getInt("display") == geometry[0], "captured navigation display")
        val deadline = SystemClock.uptimeMillis() + 5000
        var windowReady: Boolean
        var observation: DeviceTestSupport.InputObservation? = null
        lateinit var capturedViewport: DeviceTestSupport.MonitorViewport
        lateinit var point: FloatArray
        try {
            do {
                val current = DeviceTestSupport.InputObservation()
                observation = current
                windowReady =
                    if (Build.VERSION.SDK_INT <= 30) {
                        val report = DeviceTestSupport.shell(runner, "dumpsys input")
                        current.retain("input", report)
                        InputWindowState.ready(report, session!!.id, expected.getInt("uid"), false)
                    } else {
                        DeviceTestSupport.settledInputWindow(
                            runner,
                            geometry[0],
                            expected.getInt("task"),
                            component,
                            expected.getInt("uid"),
                            geometry[3],
                            geometry[4],
                            current,
                            Build.VERSION.SDK_INT == 33,
                        )
                    }
                if (!windowReady) Thread.sleep(40)
            } while (!windowReady && SystemClock.uptimeMillis() < deadline)
            check(windowReady, "native app window drawn and touchable before contact")
            runner!!.getUiAutomation()!!.waitForIdle(250, 5000)
            val current =
                state ?: throw AssertionError("Navigation state disappeared before contact")
            if (current.getString("page") != expected.getString("page"))
                throw AssertionError("Navigation page changed before contact")
            for (name in
                arrayOf(
                    "task",
                    "uid",
                    "display",
                    "targetWidth",
                    "targetHeight",
                    "windowWidth",
                    "windowHeight",
                )) if (current.getInt(name) != expected.getInt(name))
                throw AssertionError("Navigation fixture changed before contact: " + name)
            for (name in arrayOf("targetX", "targetY")) if (
                current.getFloat(name) != expected.getFloat(name)
            )
                throw AssertionError("Navigation fixture changed before contact: " + name)
            capturedViewport = DeviceTestSupport.monitorViewport(runner, session)
            if (
                !geometry.contentEquals(
                    intArrayOf(
                        capturedViewport.desktopDisplay,
                        capturedViewport.rotation,
                        capturedViewport.dpi,
                        capturedViewport.desktopWidth,
                        capturedViewport.desktopHeight,
                        capturedViewport.externalDisplay,
                    )
                )
            )
                throw AssertionError("Navigation geometry changed before contact")
            point =
                capturedViewport.mapNormalizedToScreen(
                    expected.getFloat("targetX") / capturedViewport.desktopWidth,
                    expected.getFloat("targetY") / capturedViewport.desktopHeight,
                )
            DeviceTestSupport.usableMonitorSwipe(runner, session, point, point.clone())
            capturedViewport.verify(runner, session)
        } catch (error: Throwable) {
            try {
                val directory =
                    java.io.File(
                        runner!!.targetContext.externalCacheDir,
                        "legacy-navigation-readiness",
                    )
                if (!directory.isDirectory && !directory.mkdirs())
                    throw IllegalStateException("Cannot retain navigation readiness")
                java.io
                    .File(directory, "consumed.txt")
                    .writeText(
                        "geometry=${geometry.contentToString()}\n" +
                            (observation?.decodedText() ?: "No readiness query completed"),
                        Charsets.UTF_8,
                    )
            } catch (writeError: Throwable) {
                error.addSuppressed(writeError)
            }
            throw error
        }
        val down = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event =
                MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point[0], point[1], 0)
            event!!.setSource(InputDevice.SOURCE_TOUCHSCREEN)
            try {
                check(
                    DeviceTestSupport.injectOnDisplay(event, capturedViewport.externalDisplay),
                    "native host contact accepted",
                )
            } finally {
                event!!.recycle()
            }
        }
        runner!!.getUiAutomation()!!.waitForIdle(250, 5000)
    }

    private fun back() {
        if (Build.VERSION.SDK_INT <= 30) {
            runner!!.runOnMainSync { session!!.key(KeyEvent.KEYCODE_BACK) }
            return
        }
        val expected = Bundle(state ?: throw AssertionError("Navigation state missing before Back"))
        check(
            expected.getString("page") in setOf("navigation-parent", "navigation-child"),
            "captured Back fixture page",
        )
        val geometry = IntArray(6)
        fun captureGeometry(): IntArray =
            intArrayOf(
                session!!.id,
                session!!.rotation,
                session!!.dpi,
                session!!.width(),
                session!!.height(),
                session!!.external?.displayId ?: -1,
            )
        runner!!.runOnMainSync { captureGeometry().copyInto(geometry) }
        check(expected.getInt("display") == geometry[0], "captured Back display")
        fun matches(current: Bundle): Boolean =
            current.getString("page") == expected.getString("page") &&
                arrayOf("task", "uid", "display", "windowWidth", "windowHeight").all {
                    current.getInt(it) == expected.getInt(it)
                }
        val deadline = SystemClock.uptimeMillis() + 5000
        var ready: Boolean
        do {
            val current = state
            ready = current != null && matches(current) && current.getBoolean("focused")
            if (!ready) Thread.sleep(40)
        } while (!ready && SystemClock.uptimeMillis() < deadline)
        check(ready, "captured native navigation window focused before Back")
        runner!!.runOnMainSync {
            val current = state ?: throw AssertionError("Navigation state disappeared before Back")
            if (
                !matches(current) ||
                    !current.getBoolean("focused") ||
                    !geometry.contentEquals(captureGeometry())
            )
                throw AssertionError("Navigation focus or geometry changed before Back")
            session!!.key(KeyEvent.KEYCODE_BACK)
        }
    }

    @Throws(Exception::class)
    private fun page(page: String?) {
        await({ state != null && page == state!!.getString("page") }, page!! + " resumed")
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerLegacyReceiver(
        context: Context,
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
    ) {
        // Android 11/12 lack exported flags; this fixture intentionally uses a different UID.
        context.registerReceiver(receiver, filter)
    }

    @Throws(Exception::class)
    fun run(): String {
        val context = runner!!.getTargetContext()
        val ready = CountDownLatch(1)
        val binding =
            object : ServiceConnection {
                public override fun onServiceConnected(n: ComponentName?, binder: IBinder?) {
                    session = (binder as SessionService.LocalBinder).get()
                    ready.countDown()
                }

                public override fun onServiceDisconnected(n: ComponentName?) {}
            }
        val receiver =
            object : BroadcastReceiver() {
                public override fun onReceive(c: Context?, intent: Intent) {
                    state = Bundle(intent.getExtras())
                }
            }
        var bound = false
        var failure: Throwable? = null
        var registered = false
        try {
            phone = DeviceTestSupport.startPhone(runner!!)
            bound =
                context!!.bindService(
                    Intent(context, SessionService::class.java),
                    binding,
                    Context.BIND_AUTO_CREATE,
                )
            check(ready.await(5, TimeUnit.SECONDS), "navigation service bound")
            await(
                { session!!.id >= 0 && !session!!.busy },
                "initial desktop ready without output resize",
            )
            check(!session!!.splitEnabled(), "isolated single-view legacy input path")
            val filter = IntentFilter(LaunchParentActivity.ACTION)
            if (Build.VERSION.SDK_INT >= 33)
                context!!.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else registerLegacyReceiver(context!!, receiver, filter)
            registered = true
            val app =
                ComponentName("dev.dexdroid.test", "dev.dexdroid.fixture.NavigationFixtureActivity")
            runner!!.runOnMainSync({ session!!.launch(app, session!!.rotation) })
            page("navigation-parent")
            val display = session!!.id
            val task = state!!.getInt("task")
            showPhone()
            for (cycle in 0..2) {
                tap()
                page("navigation-child")
                check(state!!.getInt("task") == task, "child remains in same native task")
                check(phoneVisible(), "first child contact preserves phone foreground")
                tap()
                page("navigation-parent")
                check(phoneVisible(), "native Up preserves phone foreground")
                tap()
                page("navigation-child")
                back()
                page("navigation-parent")
                check(phoneVisible(), "child remote Back preserves phone foreground")
            }
            runner!!.runOnMainSync({ phone!!.moveTaskToBack(true) })
            await({ !phoneVisible() }, "phone deliberately backgrounded")
            tap()
            page("navigation-child")
            check(!phoneVisible(), "monitor navigation preserves intentional phone backgrounding")
            showPhone()
            tap()
            page("navigation-parent")
            back()
            await(
                { !session!!.hasDesktopApp() && !session!!.busy },
                "native root Back clears app occupancy",
            )
            check(
                phoneVisible() && session!!.id == display,
                "root Back retains phone controller and desktop",
            )
            runner!!.runOnMainSync({ session!!.launch(app, session!!.rotation) })
            await(
                {
                    (state != null &&
                        "navigation-parent" == state!!.getString("page") &&
                        state!!.getInt("task") != task)
                },
                "relaunch creates a fresh native task",
            )
            showPhone()
            tap()
            page("navigation-child")
            check(phoneVisible(), "new task first contact works after root exit and relaunch")
            val freshTask = state!!.getInt("task")
            check(
                freshTask >= 0 &&
                    state!!.getInt("uid") ==
                        context!!.packageManager.getApplicationInfo("dev.dexdroid.test", 0).uid &&
                    state!!.getInt("display") == display &&
                    session!!.id == display,
                "final child belongs to captured navigation task and desktop",
            )
            tap()
            page("navigation-parent")
            check(
                state!!.getInt("task") == freshTask && state!!.getInt("display") == display,
                "cleanup Up retains captured navigation task and desktop",
            )
            back()
            await(
                { !session!!.hasDesktopApp() && !session!!.busy },
                "cleanup root Back clears navigation occupancy",
            )
            await(
                { fixtureTaskAbsent(freshTask, app) },
                "captured navigation task absent before Stop",
            )
        } catch (error: Throwable) {
            val wrapped =
                RuntimeException(results.toString() + "FAIL legacy native navigation", error)
            failure = wrapped
            throw wrapped
        } finally {
            var teardown: Throwable? = null
            try {
                if (session != null) {
                    runner!!.runOnMainSync { session!!.stopDesktop() }
                    await({ session!!.id < 0 }, "navigation fixture desktop stopped")
                }
            } catch (error: Throwable) {
                teardown = error
            }
            try {
                if (registered) context!!.unregisterReceiver(receiver)
            } catch (error: Throwable) {
                if (teardown == null) teardown = error else teardown!!.addSuppressed(error)
            }
            try {
                if (bound) context!!.unbindService(binding)
            } catch (error: Throwable) {
                if (teardown == null) teardown = error else teardown!!.addSuppressed(error)
            }
            if (teardown != null) {
                if (failure != null) failure!!.addSuppressed(teardown!!)
                else throw IllegalStateException("Navigation fixture teardown failed", teardown)
            }
        }
        return results.toString()
    }
}
