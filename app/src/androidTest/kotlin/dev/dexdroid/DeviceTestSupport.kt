package dev.dexdroid

import android.app.Activity
import android.app.ActivityOptions
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.view.InputEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

/** Explicit phone display and captured shell results across Android test runtimes. */
internal class DeviceTestSupport {
    /** One invocation-owned decoded-text sample, never used to admit input. */
    class InputObservation {
        private val fields = linkedMapOf<String, String>()

        fun retain(name: String, value: String?) {
            require(
                name in
                    setOf(
                        "stage",
                        "expected",
                        "task-before-report",
                        "task-after-report",
                        "input",
                        "legacy-window",
                        "parser",
                        "matching-window",
                    )
            )
            val text = value ?: "<null>"
            fields[name] =
                if (text.length <= 262144) text
                else text.take(262144) + "\nTRUNCATED: original decoded characters=" + text.length
        }

        fun decodedText(): String =
            "schema=1; decoded UTF-8 text, not original stdout bytes; unqueried fields remain explicit; truncation is inconclusive\n" +
                listOf(
                        "stage",
                        "expected",
                        "task-before-report",
                        "task-after-report",
                        "input",
                        "legacy-window",
                        "parser",
                        "matching-window",
                    )
                    .joinToString("\n") { it + ":\n" + (fields[it] ?: "<unqueried>") }
    }

    data class MonitorViewport(
        val window: Window,
        val activityHost: Boolean,
        val frame: View,
        val externalDisplay: Int,
        val desktopDisplay: Int,
        val rotation: Int,
        val desktopWidth: Int,
        val desktopHeight: Int,
        val dpi: Int,
        val width: Int,
        val height: Int,
        val originX: Int,
        val originY: Int,
    ) {
        fun mapNormalizedToScreen(u: Float, v: Float): FloatArray {
            val point =
                Geometry.map(
                    u,
                    v,
                    rotation,
                    width.toFloat(),
                    height.toFloat(),
                    desktopWidth,
                    desktopHeight,
                )
            point[0] += originX
            point[1] += originY
            return point
        }

        fun mapVirtualToScreen(x: Float, y: Float): FloatArray =
            mapNormalizedToScreen(x / desktopWidth, y / desktopHeight)

        fun unmapScreen(x: Float, y: Float): FloatArray? =
            Geometry.unmap(
                x - originX,
                y - originY,
                rotation,
                width.toFloat(),
                height.toFloat(),
                desktopWidth,
                desktopHeight,
            )

        fun screenBounds(): android.graphics.Rect =
            android.graphics.Rect(originX, originY, originX + width, originY + height)

        fun verify(runner: Instrumentation?, session: SessionService?) {
            if (monitorViewport(runner, session) != this)
                throw AssertionError("Monitor viewport changed before contact or capture")
        }
    }

    companion object {
        @JvmStatic
        fun onMain(runner: Instrumentation?, action: () -> Unit) {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) action()
            else runner!!.runOnMainSync { action() }
        }

        @JvmStatic
        fun monitorViewport(runner: Instrumentation?, session: SessionService?): MonitorViewport {
            var captured: MonitorViewport? = null
            onMain(runner) {
                val current = session ?: throw AssertionError("Monitor session missing")
                val window =
                    current.monitorWindow() ?: throw AssertionError("Monitor window missing")
                val content = window.decorView.findViewById<ViewGroup>(android.R.id.content)
                if (content == null || content.childCount != 1)
                    throw AssertionError("Monitor must have one content frame")
                val frame = content.getChildAt(0)
                val external = current.external ?: throw AssertionError("Monitor display missing")
                if (
                    frame !is android.widget.FrameLayout ||
                        !frame.isAttachedToWindow ||
                        !frame.isShown ||
                        frame.width <= 0 ||
                        frame.height <= 0 ||
                        frame.display?.displayId != external.displayId ||
                        current.id < 0 ||
                        current.busy
                )
                    throw AssertionError("Current monitor content frame unavailable")
                val local = IntArray(2)
                frame.getLocationInWindow(local)
                if (local[0] != 0 || local[1] != 0)
                    throw AssertionError(
                        "Unsupported monitor window-local content origin: ${local.contentToString()}"
                    )
                val origin = IntArray(2)
                frame.getLocationOnScreen(origin)
                captured =
                    MonitorViewport(
                        window,
                        window.callback is MonitorActivity,
                        frame,
                        external.displayId,
                        current.id,
                        current.rotation,
                        current.width(),
                        current.height(),
                        current.dpi,
                        frame.width,
                        frame.height,
                        origin[0],
                        origin[1],
                    )
            }
            return captured ?: throw AssertionError("Monitor viewport not captured")
        }

        /** Establish one declared fixture precondition, never an in-scenario repair. */
        @Throws(Exception::class)
        @JvmStatic
        fun isolateNavigationFixtures(runner: Instrumentation?, record: (String) -> Unit) {
            fun verify(value: Boolean, message: String) {
                if (!value) throw AssertionError(message)
                record("PASS " + message + '\n')
            }
            val before = shell(runner, "am stack list")
            val captured = FixtureTaskIsolation.ownedRoots(before!!)
            for (owned in captured) {
                var current: DisplayTasks.Root? = null
                for (root in FixtureTaskIsolation.ownedRoots(shell(runner, "am stack list")!!)) if (
                    root!!.id == owned!!.id
                )
                    current = root
                verify(
                    current != null &&
                        current!!.taskId == owned!!.taskId &&
                        current!!.display == owned!!.display,
                    "fixture-only root identity unchanged before removal " + owned!!.id,
                )
                val removed = shell(runner, "am stack remove " + owned!!.id)
                verify(
                    removed!!.trim({ it <= ' ' }).isEmpty(),
                    "fixture-only root removal accepted " + owned!!.id,
                )
                record(
                    "SETUP removed fixture root=" +
                        owned!!.id +
                        " task=" +
                        owned!!.taskId +
                        " display=" +
                        owned!!.display +
                        '\n'
                )
            }
            val deadline = SystemClock.uptimeMillis() + 5000
            var absent: Boolean
            do {
                val after = shell(runner, "am stack list")
                absent = FixtureTaskIsolation.ownedRoots(after!!).isEmpty()
                for (owned in captured) absent =
                    absent and
                        !java.util.regex.Pattern.compile("(?m)^\\s*taskId=" + owned!!.taskId + ":")
                            .matcher(after!!)
                            .find()
                if (absent) break
                Thread.sleep(50)
            } while (SystemClock.uptimeMillis() < deadline)
            verify(absent, "fresh child fixture established before first product launch")
        }

        @Volatile private var inputDriver: IBinder? = null
        private var inputArgs: Shizuku.UserServiceArgs? = null
        private var inputConnection: ServiceConnection? = null
        private val injectedContacts: MutableMap<Int?, MotionEvent?> =
            java.util.LinkedHashMap<Int?, MotionEvent?>()

        @Throws(Exception::class)
        @JvmStatic
        fun startInputDriver(runner: Instrumentation?) {
            if (inputDriver != null && inputDriver!!.isBinderAlive()) return
            // Preserve the accepted initialized-service readiness correction.
            val serviceReady = CountDownLatch(1)
            val serviceListener = Shizuku.OnBinderReceivedListener { serviceReady.countDown() }
            try {
                Shizuku.addBinderReceivedListenerSticky(serviceListener)
                if (
                    !serviceReady.await(5, TimeUnit.SECONDS) ||
                        Shizuku.checkSelfPermission() !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                )
                    throw IllegalStateException("Test input requires running, authorized Shizuku")
            } finally {
                Shizuku.removeBinderReceivedListener(serviceListener)
            }
            val ready = CountDownLatch(1)
            inputConnection =
                object : ServiceConnection {
                    public override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        inputDriver = binder
                        ready.countDown()
                    }

                    public override fun onServiceDisconnected(name: ComponentName?) {
                        inputDriver = null
                    }
                }
            inputArgs =
                Shizuku.UserServiceArgs(
                        ComponentName(runner!!.getTargetContext()!!, "dev.dexdroid.TestInputDriver")
                    )
                    .daemon(false)!!
                    .processNameSuffix("test-input")!!
                    .debuggable(true)!!
                    .version(BuildConfig.VERSION_CODE)
            Shizuku.bindUserService(inputArgs!!, inputConnection!!)
            if (!ready.await(10, TimeUnit.SECONDS))
                throw IllegalStateException("Shell test input driver did not bind")
        }

        @Throws(Exception::class)
        @JvmStatic
        fun injectOnDisplay(event: InputEvent?, display: Int): Boolean {
            return synchronized(DeviceTestSupport::class.java) {
                val driver = inputDriver
                if (driver == null)
                    throw IllegalStateException("Shell test input driver unavailable")
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken("dev.dexdroid.TestInputDriver")
                    data.writeInt(display)
                    data.writeInt(
                        if (event is MotionEvent)
                            (if (event!!.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) 3
                            else 1)
                        else 2
                    )
                    event!!.writeToParcel(data, 0)
                    if (!driver!!.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0))
                        throw IllegalStateException("Shell test input transaction rejected")
                    reply.readException()
                    val accepted = reply.readInt() == 1
                    if (
                        (accepted &&
                            event is MotionEvent &&
                            event!!.isFromSource(android.view.InputDevice.SOURCE_TOUCHSCREEN))
                    ) {
                        val motion = event as MotionEvent?
                        val previous = injectedContacts.remove(display)
                        if (previous != null) previous!!.recycle()
                        val action = motion!!.getActionMasked()
                        if (
                            action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL
                        ) {
                            val skip =
                                if (action == MotionEvent.ACTION_POINTER_UP)
                                    motion!!.getActionIndex()
                                else -1
                            injectedContacts.put(
                                display,
                                contactCopy(
                                    motion!!,
                                    skip,
                                    MotionEvent.ACTION_MOVE,
                                    motion!!.getFlags(),
                                ),
                            )
                        }
                    }
                    return@synchronized accepted
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
        }

        private fun contactCopy(
            event: MotionEvent,
            skip: Int,
            action: Int,
            flags: Int,
        ): MotionEvent? {
            val count = event.getPointerCount() - (if (skip >= 0) 1 else 0)
            val properties = arrayOfNulls<MotionEvent.PointerProperties?>(count)
            val coordinates = arrayOfNulls<MotionEvent.PointerCoords?>(count)
            var source = 0
            var target = 0
            while (source < event.getPointerCount()) {
                if (source == skip) {
                    source++
                    continue
                }
                properties[target] = MotionEvent.PointerProperties()
                coordinates[target] = MotionEvent.PointerCoords()
                event.getPointerProperties(source, properties[target])
                event.getPointerCoords(source, coordinates[target])
                target++
                source++
            }
            return MotionEvent.obtain(
                event.getDownTime(),
                SystemClock.uptimeMillis(),
                action,
                count,
                properties,
                coordinates,
                event.getMetaState(),
                event.getButtonState(),
                event.getXPrecision(),
                event.getYPrecision(),
                event.getDeviceId(),
                event.getEdgeFlags(),
                event.getSource(),
                flags,
            )
        }

        @Throws(Exception::class)
        @JvmStatic
        fun stopDesktopBeforeExit(runner: Instrumentation) {
            // Instrumentation kills its target process on finish. Do not race an OEM
            // setDisplaySurface traversal with a dying TextureView producer.
            val connected = CountDownLatch(1)
            val service = arrayOf<SessionService?>(null)
            val binding =
                object : ServiceConnection {
                    public override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        service[0] = (binder as SessionService.LocalBinder).get()
                        connected.countDown()
                    }

                    public override fun onServiceDisconnected(name: ComponentName?) {}
                }
            val context = runner.getTargetContext()
            val bound =
                context!!.bindService(
                    Intent(context, SessionService::class.java),
                    binding,
                    android.content.Context.BIND_AUTO_CREATE,
                )
            if (!bound) throw IllegalStateException("Desktop teardown bind rejected")
            try {
                if (!connected.await(5, TimeUnit.SECONDS))
                    throw IllegalStateException("Desktop teardown bind timed out")
                runner.runOnMainSync({ service!![0]!!.pauseDesktop() })
                val field = SessionService::class.java!!.getDeclaredField("worker")
                field.setAccessible(true)
                val queue = field.get(service!![0]) as java.util.concurrent.ExecutorService?
                // Fence pending create/resize/stop work, including an operation which
                // has not registered its display with DisplayManager yet.
                queue!!.submit({}).get(10, TimeUnit.SECONDS)
                val displays =
                    context!!.getSystemService(android.content.Context.DISPLAY_SERVICE!!)
                        as android.hardware.display.DisplayManager?
                val end = SystemClock.uptimeMillis() + 10000
                var found: Boolean
                do {
                    found = false
                    for (display in displays!!.getDisplays()!!) found =
                        found or display!!.getName()!!.startsWith("DexDroid Desktop")
                    if (found) Thread.sleep(40)
                } while (found && SystemClock.uptimeMillis() < end)
                if (found) throw IllegalStateException("Virtual desktop still present at test exit")
                // A second main-thread turn drains teardown callbacks before exit.
                runner.runOnMainSync({})
            } finally {
                context!!.unbindService(binding)
            }
        }

        @JvmStatic
        fun stopInputDriver() {
            if (inputArgs != null && inputConnection != null)
                Shizuku.unbindUserService(inputArgs!!, inputConnection, true)
            inputDriver = null
            inputArgs = null
            inputConnection = null
        }

        @JvmStatic
        fun startPhone(runner: Instrumentation): MainActivity {
            return runner.startActivitySync(
                Intent(runner.getTargetContext(), MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ActivityOptions.makeBasic()!!.setLaunchDisplayId(0)!!.toBundle(),
            ) as MainActivity
        }

        /**
         * Fresh controller UI only: avoid startActivitySync waiting forever on singleTask reuse.
         */
        @JvmStatic
        fun startPhoneBounded(runner: Instrumentation): MainActivity {
            val app = runner.targetContext.applicationContext as android.app.Application
            val ready = java.util.concurrent.CountDownLatch(1)
            val active = java.util.concurrent.atomic.AtomicBoolean(true)
            val launchError = java.util.concurrent.atomic.AtomicReference<Throwable?>()
            var phone: MainActivity? = null
            val callback =
                object : android.app.Application.ActivityLifecycleCallbacks {
                    override fun onActivityResumed(activity: Activity) {
                        if (active.get() && activity is MainActivity) {
                            phone = activity
                            ready.countDown()
                        }
                    }

                    override fun onActivityCreated(activity: Activity, state: Bundle?) {}

                    override fun onActivityStarted(activity: Activity) {}

                    override fun onActivityPaused(activity: Activity) {}

                    override fun onActivityStopped(activity: Activity) {}

                    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}

                    override fun onActivityDestroyed(activity: Activity) {}
                }
            val installed = CountDownLatch(1)
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.post {
                app.registerActivityLifecycleCallbacks(callback)
                installed.countDown()
            }
            try {
                check(installed.await(5, TimeUnit.SECONDS)) {
                    "Controller callback setup exceeded deadline"
                }
                runner.sendStatus(
                    0,
                    Bundle().apply {
                        putString("stream", "PROGRESS controller callbacks installed\n")
                    },
                )
                // Avoid synchronous instrumentation/shell waits before the finite resume deadline.
                handler.post {
                    if (!active.get()) return@post
                    try {
                        val options = ActivityOptions.makeBasic().apply { launchDisplayId = 0 }
                        runner.targetContext.startActivity(
                            Intent(runner.targetContext, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            options.toBundle(),
                        )
                    } catch (error: Throwable) {
                        launchError.set(error)
                        ready.countDown()
                    }
                }
                check(ready.await(15, java.util.concurrent.TimeUnit.SECONDS)) {
                    "Controller did not resume within15seconds"
                }
                launchError.get()?.let { throw AssertionError("Controller launch rejected", it) }
                return checkNotNull(phone)
            } finally {
                active.set(false)
                handler.post { app.unregisterActivityLifecycleCallbacks(callback) }
            }
        }

        /**
         * Existing split fixtures use window-local coordinates; reject other origins explicitly.
         */
        @JvmStatic
        fun requireZeroMonitorOrigin(runner: Instrumentation?, session: SessionService?) {
            onMain(runner) {
                val origin = IntArray(2)
                session!!.monitorWindow()!!.decorView.getLocationOnScreen(origin)
                if (origin[0] != 0 || origin[1] != 0)
                    throw AssertionError(
                        "Split fixture requires zero monitor screen origin: ${origin.contentToString()}"
                    )
            }
        }

        @JvmStatic
        fun usableMonitorSwipe(
            runner: Instrumentation?,
            session: SessionService?,
            start: FloatArray?,
            end: FloatArray?,
        ) {
            onMain(runner) {
                if (android.os.Build.VERSION.SDK_INT < 31) return@onMain
                val decor = session!!.monitorWindow()!!.getDecorView()
                val window = decor.getRootWindowInsets()
                if (window == null) throw AssertionError("Monitor window insets unavailable")
                val bars =
                    window!!.getInsetsIgnoringVisibility(
                        (android.view.WindowInsets.Type.systemBars() or
                            android.view.WindowInsets.Type.displayCutout())
                    )
                val origin = IntArray(2)
                decor.getLocationOnScreen(origin)
                val x =
                    Math.max(
                        (origin[0] + bars.left + 1).toFloat(),
                        Math.min(
                            start!![0],
                            (origin[0] + decor.getWidth() - bars.right - 1).toFloat(),
                        ),
                    )
                val y =
                    Math.max(
                        (origin[1] + bars.top + 1).toFloat(),
                        Math.min(
                            start!![1],
                            (origin[1] + decor.getHeight() - bars.bottom - 1).toFloat(),
                        ),
                    )
                end!![0] += x - start!![0]
                end!![1] += y - start!![1]
                start[0] = x
                start[1] = y
            }
        }

        @JvmStatic
        fun usableMonitorOutside(
            runner: Instrumentation?,
            session: SessionService?,
            point: FloatArray?,
        ) {
            onMain(runner) {
                usableMonitorSwipe(runner, session, point, point!!.clone())
                val panel = session!!.monitorShellView()!!.findViewWithTag<View?>("monitor-panel")
                val bounds = android.graphics.Rect()
                if (
                    (panel == null ||
                        !panel!!.getGlobalVisibleRect(bounds) ||
                        bounds.contains(point!![0].toInt(), point!![1].toInt()))
                )
                    throw AssertionError(
                        "Outside-dismissal point must remain outside the measured panel"
                    )
            }
        }

        @Throws(Exception::class)
        @JvmStatic
        fun cancelInjectedMonitor(runner: Instrumentation?) {
            return synchronized(DeviceTestSupport::class.java) {
                for (display in java.util.ArrayList<Int?>(injectedContacts.keys)) {
                    val active = injectedContacts.get(display)
                    val cancel =
                        contactCopy(
                            active!!,
                            -1,
                            MotionEvent.ACTION_CANCEL,
                            active!!.getFlags() or MotionEvent.FLAG_CANCELED,
                        )
                    try {
                        if (!injectOnDisplay(cancel, display!!))
                            throw IllegalStateException(
                                "Injected contact cancellation rejected on " + display
                            )
                    } finally {
                        cancel!!.recycle()
                    }
                }
            }
        }

        @Throws(Exception::class)
        @JvmStatic
        @JvmOverloads
        fun nativeInputTask(
            runner: Instrumentation?,
            display: Int,
            component: ComponentName,
            observation: InputObservation? = null,
            reportName: String = "task-before-report",
        ): Int {
            var task = -1
            val report = shell(runner, "am stack list")
            observation?.retain(reportName, report)
            for (root in DisplayTasks.parse(report, component.flattenToString())) {
                if (root!!.display != display || !root!!.matches || !root!!.visible) continue
                if (root!!.tasks != 1 || task >= 0) return -1
                task = root!!.taskId
            }
            return task
        }

        /**
         * Test prerequisite only: never focuses, injects, retries, or authorizes production input.
         */
        @Throws(Exception::class)
        @JvmStatic
        @JvmOverloads
        fun settledInputWindow(
            runner: Instrumentation?,
            display: Int,
            task: Int,
            component: ComponentName?,
            uid: Int,
            width: Int,
            height: Int,
            observation: InputObservation? = null,
            api33PointerReadiness: Boolean = false,
        ): Boolean {
            observation?.retain(
                "expected",
                "task=" +
                    task +
                    "; display=" +
                    display +
                    "; component=" +
                    component!!.flattenToString() +
                    "; uid=" +
                    uid +
                    "; width=" +
                    width +
                    "; height=" +
                    height,
            )
            observation?.retain("stage", "task-before")
            if (task < 0 || nativeInputTask(runner, display, component!!, observation) != task)
                return false
            observation?.retain("stage", "input-query")
            val report = shell(runner, "dumpsys -t 1 input")
            observation?.retain("input", report)
            if (report!!.contains("DUMP TIMEOUT") || report!!.contains("Permission Denial"))
                return false
            val modern = android.os.Build.VERSION.SDK_INT >= 31
            val admitted =
                if (modern)
                    (ModernInputWindowState.inspect(report, display, uid) ==
                        ModernInputWindowState.State.READY)
                else InputWindowState.ready(report, display, uid, false)
            observation?.retain(
                "parser",
                if (admitted) "admitted" else "not admitted (unknown or pending)",
            )
            observation?.retain("stage", "parser")
            if (!admitted) return false
            val legacyWindows = if (modern) null else shell(runner, "dumpsys -t 1 window windows")
            if (!modern) observation?.retain("legacy-window", legacyWindows)
            observation?.retain("stage", "matching-window")
            val frame = "frame=[0,0][" + width + "," + height + "]"
            var matchingWindow = false
            for (line in
                report!!.split(("\n").toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()) {
                if (
                    (!line!!.contains("name=") ||
                        !line!!.contains(component!!.flattenToString()) ||
                        !line!!.contains(", displayId=" + display + ",") ||
                        !line!!.contains("ownerUid=" + uid + ",") ||
                        !line!!.contains(frame!!) ||
                        !line!!.contains("touchableRegion=[0,0][" + width + "," + height + "]"))
                )
                    continue
                if (modern) {
                    // API33 can add lone NOT_FOCUSABLE due to display keyboard-focus policy.
                    // Only an explicit pointer-only caller admits it; every ownership,
                    // geometry, obstruction and native delivery assertion remains required.
                    matchingWindow =
                        (line!!.contains(" t" + task + "}") &&
                            (line!!.contains("inputConfig=0x0,") ||
                                (api33PointerReadiness &&
                                    android.os.Build.VERSION.SDK_INT == 33 &&
                                    line!!.contains("inputConfig=NOT_FOCUSABLE,"))) &&
                            modernWindowUnobscured(report, line, display, width, height))
                } else {
                    matchingWindow =
                        (legacyWindowTaskMatches(
                            legacyWindows,
                            line,
                            display,
                            task,
                            component!!.flattenToString(),
                            component!!.flattenToShortString(),
                            uid,
                        ) &&
                            line!!.contains("visible=true") &&
                            line!!.contains("paused=false") &&
                            line!!.contains("type=0x00000001") &&
                            InputWindowState.ready(
                                "  Display: " + display + "\n" + line + "\n",
                                display,
                                uid,
                                false,
                            ))
                }
                if (matchingWindow) break
            }
            observation?.retain("matching-window", matchingWindow.toString())
            if (!matchingWindow) return false
            observation?.retain("stage", "task-after")
            return nativeInputTask(
                runner,
                display,
                component!!,
                observation,
                "task-after-report",
            ) == task
        }

        private fun legacyWindowTaskMatches(
            windows: String?,
            inputWindow: String?,
            display: Int,
            task: Int,
            component: String?,
            shortComponent: String?,
            uid: Int,
        ): Boolean {
            if (
                (windows == null ||
                    windows!!.contains("DUMP TIMEOUT") ||
                    windows!!.contains("Permission Denial"))
            )
                return false
            val identity =
                java.util.regex.Pattern.compile("Window\\{[^}]+\\}").matcher(inputWindow!!)
            if (!identity.find()) return false
            val header =
                java.util.regex.Pattern.compile(
                        ("(?m)^  Window #\\d+ " +
                            java.util.regex.Pattern.quote(identity.group()!!) +
                            ":\\s*$")
                    )
                    .matcher(windows!!)
            if (!header.find()) return false
            val next = java.util.regex.Pattern.compile("(?m)^  Window #\\d+ ").matcher(windows!!)
            val end = if (next.find(header.end())) next.start() else windows!!.length
            val block = windows!!.substring(header.end(), end)
            if (
                (!java.util.regex.Pattern.compile("\\bmDisplayId=" + display + "\\b")
                    .matcher(block)
                    .find() ||
                    !java.util.regex.Pattern.compile("\\bmOwnerUid=" + uid + "\\b")
                        .matcher(block)
                        .find())
            )
                return false
            for (line in
                block.split(("\n").toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()) {
                if (
                    (line!!.contains("mActivityRecord=ActivityRecord{") &&
                        line!!.contains(" t" + task + "}") &&
                        ((line!!.contains(" " + component + " ") ||
                            line!!.contains(" " + shortComponent + " "))))
                )
                    return true
            }
            return false
        }

        private fun modernWindowUnobscured(
            report: String?,
            target: String?,
            display: Int,
            width: Int,
            height: Int,
        ): Boolean {
            val section =
                java.util.regex.Pattern.compile("(?m)^  Display: " + display + "\\s*$")
                    .matcher(report!!)
            if (!section.find()) return false
            val boundary = java.util.regex.Pattern.compile("(?m)^  \\S").matcher(report!!)
            if (!boundary.find(section.end())) return false
            val windows = report!!.substring(section.end(), boundary.start())
            val row = java.util.regex.Pattern.compile("^\\s*(\\d+): name=.*")
            val targetRow = row.matcher(target!!)
            if (!targetRow.matches() || !windows.contains(target!!)) return false
            val targetOrder = Integer.parseInt(targetRow.group(1)!!)
            val orders = java.util.HashSet<Int?>()
            // Modern InputDispatcher explicitly numbers windows in top-to-bottom order.
            // Use those ordinals within this display, never unrelated dump line order.
            for (line in
                windows.split(("\n").toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()) {
                val order = row.matcher(line!!)
                if (!order.matches()) continue
                val index = Integer.parseInt(order.group(1)!!)
                if (!orders.add(index)) return false
                if (index >= targetOrder) continue
                val config =
                    java.util.regex.Pattern.compile("\\binputConfig=([^,]+),").matcher(line!!)
                val alpha = java.util.regex.Pattern.compile("\\balpha=([0-9.]+),").matcher(line!!)
                val frame =
                    java.util.regex.Pattern.compile(
                            "\\bframe=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]"
                        )
                        .matcher(line!!)
                if (!config.find() || !alpha.find() || !frame.find()) return false
                val flags =
                    java.util.HashSet<String?>(
                        java.util.Arrays.asList<String?>(
                            *config
                                .group(1)!!
                                .split(("\\s*\\|\\s*").toRegex())
                                .dropLastWhile({ it.isEmpty() })
                                .toTypedArray()
                        )
                    )
                if (
                    flags.contains("NOT_VISIBLE") ||
                        java.lang.Float.parseFloat(alpha.group(1)!!) == 0f
                )
                    continue
                val left = Integer.parseInt(frame.group(1)!!)
                val top = Integer.parseInt(frame.group(2)!!)
                val right = Integer.parseInt(frame.group(3)!!)
                val bottom = Integer.parseInt(frame.group(4)!!)
                if (
                    (right <= left ||
                        bottom <= top ||
                        left >= width ||
                        top >= height ||
                        right <= 0 ||
                        bottom <= 0)
                )
                    continue
                if (
                    (flags.contains("TRUSTED_OVERLAY") &&
                        ((flags.contains("SPY") ||
                            flags.contains("NOT_TOUCHABLE") ||
                            flags.contains("NO_INPUT_CHANNEL"))))
                )
                    continue
                // This includes system transition snapshots with NO_INPUT_CHANNEL:
                // they can block untrusted injected touch despite having no touch region.
                return false
            }
            return orders.contains(targetOrder)
        }

        @Throws(Exception::class)
        @JvmStatic
        fun shell(runner: Instrumentation?, command: String?): String? {
            runner!!
                .getUiAutomation()!!
                .executeShellCommand(command)!!
                .use({ descriptor ->
                    FileInputStream(descriptor!!.getFileDescriptor()!!)
                        .use({ input ->
                            ByteArrayOutputStream()
                                .use({ output ->
                                    val buffer = ByteArray(4096)
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count == -1) break
                                        output.write(buffer, 0, count)
                                    }
                                    return output.toString(StandardCharsets.UTF_8.name())
                                })
                        })
                })
        }
    }
}
