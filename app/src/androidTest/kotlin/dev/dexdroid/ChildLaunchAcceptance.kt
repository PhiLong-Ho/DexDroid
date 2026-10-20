package dev.dexdroid

import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import dev.dexdroid.fixture.LaunchParentActivity
import java.io.*
import java.util.concurrent.*
import java.util.function.BooleanSupplier

/** Real cross-UID Activity navigation; shell launches only the public parent. */
class ChildLaunchAcceptance(private val runner: Instrumentation?) {
    private val results: StringBuilder = StringBuilder()
    private var session: SessionService? = null
    @Volatile private var state: Bundle? = null
    private var fixtureUid: Int = 0
    @Volatile private var reportProbe: CountDownLatch? = null
    private var reportProbeToken: String? = null
    @Volatile private var reportProbeUnchanged: Boolean = false

    @Throws(Exception::class)
    private fun reportIsolation(context: Context) {
        val expected = state!!
        for (page in arrayOf("navigation-parent", "navigation-child", "unknown", null, "parent")) {
            reportProbeToken = "child-report-" + SystemClock.uptimeMillis() + "-" + page
            reportProbeUnchanged = false
            reportProbe = CountDownLatch(1)
            try {
                context.sendBroadcast(
                    Intent(LaunchParentActivity.ACTION)
                        .setPackage(context.packageName)
                        .putExtra("uid", if ("parent" == page) -1 else fixtureUid)
                        .putExtra("page", page)
                        .putExtra("display", -777)
                        .putExtra("reportProbe", reportProbeToken)
                )
                check(
                    reportProbe!!.await(5, TimeUnit.SECONDS),
                    "unrelated report delivered: " + page,
                )
                check(
                    reportProbeUnchanged,
                    "unrelated report cannot overwrite child observation: " + page,
                )
                val current = state
                check(
                    current != null &&
                        "parent" == current.getString("page") &&
                        current.getInt("display") == expected.getInt("display") &&
                        current.getInt("task") == expected.getInt("task"),
                    "legitimate parent identity retained: " + page,
                )
            } finally {
                reportProbe = null
            }
        }
    }

    private fun check(value: Boolean, message: String?) {
        if (!value) throw AssertionError(message)
        results.append("PASS ").append(message).append('\n')
    }

    @Throws(Exception::class)
    private fun await(condition: BooleanSupplier, message: String?) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < deadline) Thread.sleep(40)
        check(condition.getAsBoolean(), message)
    }

    @Throws(Exception::class)
    private fun isolateFixtureTasks() {
        DeviceTestSupport.isolateNavigationFixtures(runner) { results.append(it) }
    }

    @Throws(Exception::class)
    private fun tapCenter() {
        val view = session!!.monitorWindow()!!.getDecorView()
        val down = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event =
                MotionEvent.obtain(
                    down,
                    SystemClock.uptimeMillis(),
                    action,
                    view.getWidth() / 2f,
                    view.getHeight() / 2f,
                    0,
                )
            event!!.setSource(InputDevice.SOURCE_TOUCHSCREEN)
            try {
                check(
                    DeviceTestSupport.injectOnDisplay(event, view.getDisplay()!!.getDisplayId()),
                    "fixture physical input delivered " + action,
                )
            } finally {
                event!!.recycle()
            }
            if (action == MotionEvent.ACTION_DOWN) Thread.sleep(50)
        }
    }

    private fun page(page: String?): Boolean {
        val current = state
        return current != null && page == current!!.getString("page")
    }

    @Throws(Exception::class)
    private fun launch(component: ComponentName, rotation: Int) {
        // Clear the previous operation's observation. Reparenting before the new
        // success status races the shell's still-running placement verifier.
        runner!!.runOnMainSync({
            session!!.status = "Acceptance awaiting launch completion"
            session!!.launch(component, rotation)
        })
        await(
            {
                val placed = booleanArrayOf(false)
                runner!!.runOnMainSync({
                    placed[0] =
                        (!session!!.busy &&
                            session!!.status!!.startsWith(("Opened " + component.getPackageName())))
                })
                placed!![0]
            },
            "verified launch completed: " + component.flattenToShortString(),
        )
    }

    @Throws(Exception::class)
    private fun emptyRendered(rotation: Int) {
        val empty = booleanArrayOf(false)
        val until = SystemClock.uptimeMillis() + 2000
        do {
            runner!!.runOnMainSync({
                val root = session!!.monitorShellView()
                val apps =
                    if (root == null) null else root!!.findViewWithTag<View?>("monitor-idle-apps")
                empty[0] =
                    (!session!!.hasDesktopApp() &&
                        session!!.activePackage!!.isEmpty() &&
                        apps != null &&
                        apps!!.isShown())
            })
            if (!empty!![0]) Thread.sleep(25)
        } while (!empty!![0] && SystemClock.uptimeMillis() < until)
        check(empty!![0], "idle Apps and empty state restored within two seconds " + rotation)
        check(!session!!.pointerVisible, "empty desktop pointer hidden " + rotation)
        val window = session!!.monitorWindow()
        val image =
            Bitmap.createBitmap(
                window!!.getDecorView().getWidth(),
                window!!.getDecorView().getHeight(),
                Bitmap.Config.ARGB_8888,
            )
        try {
            var idle = false
            val deadline = SystemClock.uptimeMillis() + 2000
            do {
                val copied = CountDownLatch(1)
                val status = intArrayOf(-1)
                runner!!.runOnMainSync({
                    PixelCopy.request(
                        window!!,
                        image,
                        { code ->
                            status[0] = code
                            copied.countDown()
                        },
                        Handler(Looper.getMainLooper()!!),
                    )
                })
                check(
                    copied.await(3, TimeUnit.SECONDS) && status!![0] == PixelCopy.SUCCESS,
                    "captured handoff monitor pixels",
                )
                val sample =
                    Geometry.map(
                        .25f,
                        .7f,
                        session!!.rotation,
                        image.getWidth().toFloat(),
                        image.getHeight().toFloat(),
                        session!!.width(),
                        session!!.height(),
                    )
                val color = image.getPixel(Math.round(sample[0]), Math.round(sample[1]))
                idle = Math.abs(Color.red(color) - Color.blue(color)) < 8 && Color.red(color) < 60
                if (!idle) Thread.sleep(30)
            } while (!idle && SystemClock.uptimeMillis() < deadline)
            val folder =
                File(runner!!.getTargetContext()!!.getExternalCacheDir(), "child-launch-acceptance")
            check(folder.isDirectory() || folder.mkdirs(), "handoff capture directory")
            FileOutputStream(File(folder, "handoff-idle-r" + rotation + ".png"))
                .use({ stream -> image.compress(Bitmap.CompressFormat.PNG, 100, stream) })
            check(idle, "idle pixels cover the departed app frame " + rotation)
        } finally {
            image.recycle()
        }
    }

    @Throws(Exception::class)
    private fun moveParentToPhone(parent: ComponentName, desktop: Int, task: Int) {
        var selected: DisplayTasks.Root? = null
        for (root in
            DisplayTasks.parse(
                DeviceTestSupport.shell(runner, "am stack list"),
                parent.flattenToString(),
            )) if (root!!.display == desktop && root!!.matches && root!!.taskId == task)
            selected = root
        check(selected != null && selected!!.tasks == 1, "isolated fixture root can move to phone")
        // Exercise the user's phone-launch path. Forced root migration can leave a
        // stale SurfaceFlinger parent on Samsung even when task metadata says 0.
        val output =
            DeviceTestSupport.shell(
                runner,
                "am start -W --display 0 -n " + parent.flattenToString(),
            )
        check(!output!!.contains("Error"), "Android accepts ordinary phone app launch")
        await(
            { page("parent") && state!!.getInt("display") == 0 },
            "phone owns fixture after movement",
        )
        check(state!!.getInt("task") == task, "phone movement preserves fixture task and state")
    }

    // API 30-32 have no RECEIVER_EXPORTED flag. This test intentionally receives
    // lifecycle reports from its different-UID fixture, never production input.
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerLegacyReceiver(context: Context?, receiver: BroadcastReceiver?) {
        if (Build.VERSION.SDK_INT >= 33)
            throw IllegalStateException("Legacy receiver on modern Android")
        context!!.registerReceiver(receiver, IntentFilter(LaunchParentActivity.ACTION))
    }

    @Throws(Exception::class)
    private fun rendered(
        reader: Boolean,
        rotation: Int,
        scenario: String = if (reader) "reader" else "parent",
    ) {
        if (scenario != "remaining-app")
            await(
                {
                    (state != null &&
                        state!!.getInt("windowWidth") == session!!.width() &&
                        state!!.getInt("windowHeight") == session!!.height())
                },
                ("fixture has resized window before rendered/input checks " +
                    scenario +
                    " " +
                    rotation),
            )
        // Match normal UI automation's settled-window boundary before testing
        // content/input. Android 11 does not give this desktop independent focus.
        runner!!.getUiAutomation()!!.waitForIdle(500, 5000)
        val window = session!!.monitorWindow()
        val decor = window!!.getDecorView()
        val image =
            Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888)
        var visible = false
        try {
            val deadline = SystemClock.uptimeMillis() + 5000
            do {
                val copied = CountDownLatch(1)
                val status = intArrayOf(-1)
                runner!!.runOnMainSync({
                    PixelCopy.request(
                        window!!,
                        image,
                        { result ->
                            status[0] = result
                            copied.countDown()
                        },
                        Handler(Looper.getMainLooper()!!),
                    )
                })
                check(
                    copied.await(5, TimeUnit.SECONDS) && status!![0] == PixelCopy.SUCCESS,
                    "captured actual monitor window",
                )
                val sample =
                    Geometry.map(
                        .25f,
                        .7f,
                        session!!.rotation,
                        image.getWidth().toFloat(),
                        image.getHeight().toFloat(),
                        session!!.width(),
                        session!!.height(),
                    )
                val color = image.getPixel(Math.round(sample[0]), Math.round(sample[1]))
                visible =
                    if (reader)
                        (Color.blue(color) > Color.red(color) + 20 &&
                            (Color.green(color) >
                                (Color.red(color) + (if (scenario == "remaining-app") 5 else 15))))
                    else Color.red(color) > Color.blue(color) + 20
                if (!visible) Thread.sleep(50)
            } while (!visible && SystemClock.uptimeMillis() < deadline)
            val folder =
                File(runner!!.getTargetContext()!!.getExternalCacheDir(), "child-launch-acceptance")
            check(folder.isDirectory() || folder.mkdirs(), "child launch evidence directory")
            FileOutputStream(File(folder, scenario + "-r" + rotation + ".png"))
                .use({ stream ->
                    check(
                        image.compress(Bitmap.CompressFormat.PNG, 100, stream),
                        "saved monitor pixels",
                    )
                })
            if (Build.VERSION.SDK_INT == 30) {
                FileOutputStream(
                        File(
                            runner!!.getTargetContext()!!.getCacheDir(),
                            "child-" + scenario + "-r" + rotation + ".png",
                        )
                    )
                    .use({ stream ->
                        check(
                            image.compress(Bitmap.CompressFormat.PNG, 100, stream),
                            "saved legacy private-cache frame",
                        )
                    })
            }
            if (!visible) {
                results
                    .append("PLACEMENT AT RENDER ERROR\n")
                    .append(DeviceTestSupport.shell(runner, "am stack list"))
                for (service in
                    arrayOf<String?>(
                        "window windows",
                        "activity activities",
                        "SurfaceFlinger",
                        "display",
                    )) {
                    val dump = DeviceTestSupport.shell(runner, "dumpsys -t 5 " + service!!)
                    FileOutputStream(
                            File(
                                folder,
                                (scenario +
                                    "-r" +
                                    rotation +
                                    "-" +
                                    service!!
                                        .split((" ").toRegex())
                                        .dropLastWhile({ it.isEmpty() })
                                        .toTypedArray()[0] +
                                    ".txt"),
                            )
                        )
                        .use({ stream ->
                            stream.write(
                                dump!!.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                            )
                        })
                }
                val displays =
                    DeviceTestSupport.shell(runner, "dumpsys SurfaceFlinger --display-id")
                val physical =
                    java.util.regex.Pattern.compile("Display (\\d+) \\(HWC display 1\\)")
                        .matcher(displays!!)
                if (physical.find()) {
                    val id = physical.group(1)
                    if (!physical.find())
                        DeviceTestSupport.shell(
                            runner,
                            ("screencap -p -d " +
                                id +
                                " " +
                                folder.getAbsolutePath() +
                                "/" +
                                scenario +
                                "-r" +
                                rotation +
                                "-physical.png"),
                        )
                }
            }
            check(visible, "rendered " + scenario + " rotation " + rotation)
        } finally {
            image.recycle()
        }
    }

    @Throws(Exception::class)
    fun run(): String {
        val context = runner!!.getTargetContext()
        var phone: MainActivity? = null
        var bound = false
        var registered = false
        val connected = CountDownLatch(1)
        val binding =
            object : ServiceConnection {
                public override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    session = (binder as SessionService.LocalBinder).get()
                    connected.countDown()
                }

                public override fun onServiceDisconnected(name: ComponentName?) {}
            }
        val receiver =
            object : BroadcastReceiver() {
                public override fun onReceive(ignored: Context?, intent: Intent) {
                    val before = state
                    val page = intent.getStringExtra("page")
                    if (
                        LaunchParentActivity.ACTION == intent.action &&
                            intent.getIntExtra("uid", -1) == fixtureUid &&
                            ("parent" == page || "reader" == page || "exit" == page)
                    )
                        state = Bundle(intent.extras)
                    // Preserve the synchronous probe outcome before any later legitimate report.
                    val delivered = reportProbe
                    if (
                        delivered != null &&
                            reportProbeToken!!.equals(intent.getStringExtra("reportProbe"))
                    ) {
                        reportProbeUnchanged = before != null && state === before
                        delivered.countDown()
                    }
                }
            }
        try {
            fixtureUid =
                context!!.getPackageManager()!!.getApplicationInfo("dev.dexdroid.test", 0).uid
            check(
                fixtureUid != android.os.Process.myUid(),
                "fixture runs under a different application UID",
            )
            val parent =
                ComponentName("dev.dexdroid.test", "dev.dexdroid.fixture.LaunchParentActivity")
            val reader =
                ComponentName("dev.dexdroid.test", "dev.dexdroid.fixture.LaunchReaderActivity")
            check(
                !context!!.getPackageManager()!!.getActivityInfo(reader, 0).exported,
                "reader Activity is private to its own application",
            )
            isolateFixtureTasks()
            if (Build.VERSION.SDK_INT >= 33)
                context!!.registerReceiver(
                    receiver,
                    IntentFilter(LaunchParentActivity.ACTION),
                    Context.RECEIVER_EXPORTED,
                )
            else registerLegacyReceiver(context, receiver)
            registered = true
            phone = DeviceTestSupport.startPhone(runner!!)
            bound =
                context!!.bindService(
                    Intent(context, SessionService::class.java),
                    binding,
                    Context.BIND_AUTO_CREATE,
                )
            check(connected.await(5, TimeUnit.SECONDS), "child launch controller bound")
            await({ session!!.id >= 0 && !session!!.busy }, "desktop ready")
            val controller = phone
            for (rotation in 0..3) {
                val selected = rotation
                state = null
                launch(parent, selected)
                await(
                    { page("parent") && !session!!.busy && session!!.rotation == selected },
                    "public parent launched " + rotation,
                )
                val desktop = session!!.id
                check(state!!.getInt("display") == desktop, "parent placed on desktop " + rotation)
                val task = state!!.getInt("task")
                if (rotation == 0) reportIsolation(context!!)
                val opens = state!!.getInt("count")
                rendered(false, rotation)
                if (rotation % 2 == 1) {
                    runner!!.runOnMainSync({ controller!!.moveTaskToBack(true) })
                    Thread.sleep(200)
                }
                tapCenter()
                await({ page("reader") }, "application opened its own private reader " + rotation)
                check(
                    state!!.getInt("display") == desktop,
                    ("private reader stays on desktop " +
                        rotation +
                        ": actual=" +
                        state!!.getInt("display") +
                        " expected=" +
                        desktop),
                )
                check(state!!.getInt("task") == task, "reader retains parent task " + rotation)
                check(
                    ("reader-state-" + (opens + 1)) == state!!.getString("payload"),
                    "reader intent state retained " + rotation,
                )
                rendered(true, rotation)
                val taps = state!!.getInt("count")
                tapCenter()
                await(
                    { page("reader") && state!!.getInt("count") > taps },
                    "fresh physical-display input reaches reader " + rotation,
                )
                check(
                    !state!!.getBoolean("focusLostDuringTouch"),
                    "reader keeps focus state through contact " + rotation,
                )
                val dispatchDelay = state!!.getLong("downDispatchDelayMs", -1)
                check(dispatchDelay >= 0, "fixture records native DOWN-to-dispatch interval")
                results
                    .append("METRIC physical_down_to_reader_dispatch_ms=")
                    .append(dispatchDelay)
                    .append(" rotation=")
                    .append(rotation)
                    .append('\n')
                state = null
                runner!!.runOnMainSync({ session!!.key(KeyEvent.KEYCODE_BACK) })
                await({ page("parent") }, "Back returns to parent " + rotation)
                check(
                    state!!.getInt("display") == desktop && state!!.getInt("task") == task,
                    "Back preserves external display and original parent task " + rotation,
                )
                check(
                    state!!.getInt("count") == opens + 1,
                    "parent navigation state survives " + rotation,
                )
                check(
                    session!!.id == desktop && session!!.rotation == selected,
                    "desktop and selected rotation unchanged " + rotation,
                )
                rendered(false, rotation)
                moveParentToPhone(parent, desktop, task)
                emptyRendered(rotation)
                check(state!!.getInt("display") == 0, "idle does not steal phone task " + rotation)
                // The restored launcher must remain interactive without controller focus.
                tapCenter()
                await(
                    {
                        ("home" == session!!.monitorShellPage() &&
                            !session!!.monitorShellAnimating())
                    },
                    "restored idle Apps opens " + rotation,
                )
                runner!!.runOnMainSync({ session!!.key(KeyEvent.KEYCODE_BACK) })
                await(
                    {
                        ("closed" == session!!.monitorShellPage() &&
                            !session!!.monitorShellAnimating())
                    },
                    "restored launcher Back closes " + rotation,
                )
                launch(parent, selected)
                await(
                    {
                        (page("parent") &&
                            state!!.getInt("display") == desktop &&
                            session!!.hasDesktopApp())
                    },
                    "explicit relaunch restores desktop " + rotation,
                )
                check(
                    state!!.getInt("count") == opens + 1,
                    "relaunch preserves parent state " + rotation,
                )
                rendered(false, rotation)
            }
            runner!!.runOnMainSync({ session!!.preview(1920, 1080, 240) })
            await({ !session!!.busy && session!!.baseH == 1080 }, "handoff Fit preview ready")
            rendered(false, session!!.rotation, "fit-parent")
            moveParentToPhone(parent, session!!.id, state!!.getInt("task"))
            emptyRendered(5)
            val fitRotation = session!!.rotation
            runner!!.runOnMainSync({ session!!.launch(parent, fitRotation) })
            check(
                session!!.awaitingConfirmation() && !session!!.hasDesktopApp(),
                "Fit preview queues relaunch without hiding idle",
            )
            runner!!.runOnMainSync({ session!!.confirm(false) })
            await(
                {
                    (page("parent") &&
                        state!!.getInt("display") == session!!.id &&
                        session!!.hasDesktopApp())
                },
                "queued Fit relaunch restores desktop after Revert",
            )
            rendered(false, fitRotation, "fit-reverted-relaunch")
            await({ !session!!.busy && session!!.baseH == 1200 }, "handoff Fit reverted")
            // Removing one of two desktop apps must not cover the remaining app.
            launch(ComponentName(context!!, InputTestActivity::class.java!!), 0)
            await({ !session!!.busy && session!!.rotation == 0 }, "remaining-app desktop ready")
            Thread.sleep(250)
            state = null
            launch(parent, 0)
            await(
                { page("parent") && state!!.getInt("display") == session!!.id },
                "second app placed above remaining app",
            )
            moveParentToPhone(parent, session!!.id, state!!.getInt("task"))
            rendered(true, 0, "remaining-app")
            check(session!!.hasDesktopApp(), "remaining desktop app keeps idle hidden")
            val remainingComponent = ComponentName(context, InputTestActivity::class.java)
            val remainingDisplay = session!!.id
            val remainingTask =
                DeviceTestSupport.nativeInputTask(runner, remainingDisplay, remainingComponent)
            check(remainingTask >= 0, "remaining app has one visible native task")
            val contacts = InputTestActivity.touchEvents
            val contactStarted = SystemClock.uptimeMillis()
            tapCenter()
            await(
                {
                    InputTestActivity.touchEvents >= contacts + 2 &&
                        InputTestActivity.lastUpDownTime >= contactStarted
                },
                "fresh remaining-app physical monitor contact received",
            )
            check(
                InputTestActivity.touchEvents == contacts + 2 &&
                    InputTestActivity.lastDownTime == InputTestActivity.lastUpDownTime,
                "remaining app receives exactly one complete contact without retry",
            )
            check(
                Math.abs(InputTestActivity.lastUpX - session!!.width() / 2f) < 2 &&
                    Math.abs(InputTestActivity.lastUpY - session!!.height() / 2f) < 2,
                "remaining-app physical contact maps to desktop center",
            )
            check(
                session!!.id == remainingDisplay &&
                    DeviceTestSupport.nativeInputTask(
                        runner,
                        remainingDisplay,
                        remainingComponent,
                    ) == remainingTask &&
                    page("parent") &&
                    state!!.getInt("display") == 0,
                "remaining contact preserves task/display and leaves departed app on phone",
            )
            // Isolate a fresh desktop to check a genuine app exit as well as reparenting.
            val previous = session!!.id
            runner!!.runOnMainSync({
                session!!.pauseDesktop()
                session!!.ensureDesktop()
            })
            await(
                { session!!.id >= 0 && session!!.id != previous && !session!!.busy },
                "fresh exit-check desktop ready",
            )
            state = null
            launch(parent, 0)
            await(
                {
                    (page("parent") &&
                        state!!.getInt("display") == session!!.id &&
                        session!!.hasDesktopApp())
                },
                "exit-check parent placed",
            )
            rendered(false, 0, "exit-parent")
            runner!!.runOnMainSync({ session!!.key(KeyEvent.KEYCODE_BACK) })
            await({ page("exit") }, "fixture received Back and requested a real app exit")
            emptyRendered(6)
            return results.toString()
        } catch (error: Throwable) {
            return (results.toString() +
                "STATE " +
                state +
                " desktop=" +
                (if (session == null) "unbound"
                else
                    (session!!.id.toString() +
                        " rotation=" +
                        session!!.rotation +
                        " busy=" +
                        session!!.busy +
                        " " +
                        session!!.status)) +
                "\nFAIL " +
                android.util.Log.getStackTraceString(error))
        } finally {
            if (registered) context!!.unregisterReceiver(receiver)
            if (session != null) {
                runner!!.runOnMainSync({
                    session!!.confirm(false)
                    session!!.launch(
                        ComponentName(context!!, InputTestActivity::class.java!!),
                        null,
                    )
                })
            }
            if (bound) context!!.unbindService(binding)
        }
    }
}
