package dev.dexdroid

import android.app.*
import android.content.*
import android.os.*
import android.view.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.BooleanSupplier
import java.util.function.LongConsumer
import java.util.function.LongSupplier

/** No test framework dependency; exercises the real non-root Shizuku path. */
open class DeviceChecks : Instrumentation() {
    private var splitFixturePreferences: android.content.SharedPreferences? = null
    private var originalSplitPreferences: Map<String?, *>? = null

    private var session: SessionService? = null
    private var savedPreferences: Map<String?, *>? = null
    private var physicalDisplay: String? = null
    private var controllerOnly: Boolean = false
    private var uiOnly: Boolean = false
    private var legacyNavigationOnly: Boolean = false
    private var splitNavigationOnly: Boolean = false
    private var monitorOnly: Boolean = false
    private var keyboardOnly: Boolean = false
    private var idleOnly: Boolean = false
    private var iconOnly: Boolean = false
    private var profilesOnly: Boolean = false
    private var childOnly: Boolean = false
    private var catalogOnly: Boolean = false
    private var protectedOnly: Boolean = false
    private var splitProbeOnly: Boolean = false
    private var splitOnly: Boolean = false
    private var audioOnly: Boolean = false
    private var debuggingOnly: Boolean = false
    private var diagnosticsOnly: Boolean = false
    private var workspaceOnly: Boolean = false

    private val results: StringBuilder = StringBuilder()
    private var gestureDown: Long = 0
    private var monitorDown: Long = 0
    private var lastInputObservation: DeviceTestSupport.InputObservation? = null
    private var lastReadinessPoll: String? = null

    private fun retainInputFailure(error: Throwable) {
        val sample = lastInputObservation ?: return
        try {
            val dir = java.io.File(getTargetContext()!!.getExternalCacheDir(), "input-readiness")
            if (!dir.isDirectory() && !dir.mkdirs())
                throw java.io.IOException("Cannot create input evidence folder")
            java.io
                .File(dir, "last-failed-sample.txt")
                .writeText(sample.decodedText(), Charsets.UTF_8)
            java.io
                .File(dir, "last-failed-poll.txt")
                .writeText((lastReadinessPoll ?: "not sampled").take(1024), Charsets.UTF_8)
        } catch (capture: Throwable) {
            error.addSuppressed(capture)
        }
    }

    private fun restoreSplitFixture() {
        if (splitFixturePreferences == null || originalSplitPreferences == null) return
        val edit = splitFixturePreferences!!.edit()
        for (key in SplitLayoutPreferences.write(SplitLayout()).keys) {
            val value = originalSplitPreferences!!.get(key)
            if (value is Boolean) edit!!.putBoolean(key, (value as Boolean?)!!)
            else if (value is Float) edit!!.putFloat(key, (value as Float?)!!)
            else if (value is Int) edit!!.putInt(key, (value as Int?)!!)
            else if (value is Long) edit!!.putLong(key, (value as Long?)!!)
            else if (value is String) edit!!.putString(key, value as String?)
            else if (value is Set<*>) edit!!.putStringSet(key, value as Set<String?>?)
            else edit!!.remove(key)
        }
        if (!edit!!.commit()) throw IllegalStateException("Cannot restore saved split preferences")
    }

    private fun restorePreferences() {
        if (session == null || savedPreferences == null) return
        val edit = session!!.prefs!!.edit()!!.clear()
        for (item in savedPreferences!!.entries) {
            val value = item!!.value
            val key = item!!.key
            if (value is Int) edit!!.putInt(key, (value as Int?)!!)
            else if (value is Float) edit!!.putFloat(key, (value as Float?)!!)
            else if (value is Boolean) edit!!.putBoolean(key, (value as Boolean?)!!)
            else if (value is Long) edit!!.putLong(key, (value as Long?)!!)
            else if (value is String) edit!!.putString(key, value as String?)
        }
        edit!!.commit()
    }

    @Throws(Exception::class)
    private fun monitorGesture(action: Int, xs: FloatArray?, ys: FloatArray?, ids: IntArray?) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) monitorDown = now
        val props = arrayOfNulls<MotionEvent.PointerProperties?>(xs!!.size)
        val coords = arrayOfNulls<MotionEvent.PointerCoords?>(xs!!.size)
        for (i in xs!!.indices) {
            props[i] = MotionEvent.PointerProperties()
            props[i]!!.id = ids!![i]
            props[i]!!.toolType = MotionEvent.TOOL_TYPE_FINGER
            coords[i] = MotionEvent.PointerCoords()
            coords[i]!!.x = xs!![i]
            coords[i]!!.y = ys!![i]
            coords[i]!!.pressure = 1f
            coords[i]!!.size = 1f
        }
        val event =
            MotionEvent.obtain(
                monitorDown,
                now,
                action,
                xs!!.size,
                props,
                coords,
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
                DeviceTestSupport.injectOnDisplay(event, session!!.external!!.getDisplayId()),
                "physical monitor event " + action,
            )
        } finally {
            event!!.recycle()
        }
    }

    @Throws(Exception::class)
    private fun awaitSettledInput(
        display: Int,
        nativeInputTask: Int,
        inputTest: ComponentName,
        nativeInputUid: Int,
        label: String,
    ) {
        val inputWidth = session!!.width()
        val inputHeight = session!!.height()
        lastInputObservation = null
        lastReadinessPoll = "not sampled"
        try {
            awaitReadiness(
                {
                    val observation = DeviceTestSupport.InputObservation()
                    lastInputObservation = observation
                    observation.retain("stage", "session-before; " + label)
                    if (
                        (session!!.id != display ||
                            session!!.busy ||
                            session!!.width() != inputWidth ||
                            session!!.height() != inputHeight)
                    )
                        return@awaitReadiness false
                    try {
                        val inputReady =
                            DeviceTestSupport.settledInputWindow(
                                this,
                                display,
                                nativeInputTask,
                                inputTest,
                                nativeInputUid,
                                inputWidth,
                                inputHeight,
                                observation,
                                android.os.Build.VERSION.SDK_INT == 33,
                            )
                        if (!inputReady) return@awaitReadiness false
                        val sessionReady =
                            session!!.id == display &&
                                !session!!.busy &&
                                session!!.width() == inputWidth &&
                                session!!.height() == inputHeight
                        observation.retain(
                            "stage",
                            if (sessionReady) "settled" else "session-after",
                        )
                        return@awaitReadiness sessionReady
                    } catch (error: Exception) {
                        throw IllegalStateException(
                            "Cannot verify settled post-configuration input",
                            error,
                        )
                    }
                },
                label,
            )
        } catch (error: Throwable) {
            retainInputFailure(error)
            throw error
        } finally {
            lastInputObservation = null
            lastReadinessPoll = null
        }
    }

    private fun pollReadiness(
        condition: BooleanSupplier,
        clock: LongSupplier,
        pause: LongConsumer,
        sample: BiConsumer<Int, Long>,
    ): Boolean {
        val start = clock.asLong
        var streak = 0
        while (clock.asLong - start < 10000) {
            val ready = condition.asBoolean
            val elapsed = clock.asLong - start
            streak = if (ready) streak + 1 else 0
            sample.accept(streak, elapsed)
            if (elapsed >= 10000) return false
            if (streak == 2) return true
            pause.accept(minOf(50L, 10000 - elapsed))
        }
        return false
    }

    private fun awaitReadiness(condition: BooleanSupplier, label: String) {
        check(
            pollReadiness(
                condition,
                LongSupplier { SystemClock.uptimeMillis() },
                LongConsumer { Thread.sleep(it) },
                BiConsumer { streak, elapsed ->
                    lastReadinessPoll =
                        "positiveStreak=$streak; elapsedMillis=$elapsed; pollingDeadlineMillis=10000; query duration is independent"
                },
            ),
            label,
        )
    }

    private fun verifyReadinessPolling() {
        for ((values, expected, label) in
            listOf(
                Triple(listOf(true, true), true, "settled readiness admits two positive samples"),
                Triple(
                    listOf(true, false, true, true),
                    true,
                    "transient readiness resets streak and continues polling",
                ),
                Triple(
                    listOf(false),
                    false,
                    "persistent unready state exhausts original polling deadline",
                ),
            )) {
            var now = 0L
            var calls = 0
            val admitted =
                pollReadiness(
                    BooleanSupplier { values.getOrElse(calls++) { false } },
                    LongSupplier { now },
                    LongConsumer { now += it },
                    BiConsumer { _, _ -> },
                )
            check(
                admitted == expected && (if (expected) calls == values.size else now == 10000L),
                label,
            )
        }
        var now = 0L
        val late =
            pollReadiness(
                BooleanSupplier {
                    now += 10000
                    true
                },
                LongSupplier { now },
                LongConsumer { now += it },
                BiConsumer { _, _ -> },
            )
        check(!late, "readiness finishing at deadline cannot extend acceptance")
        val failure = IllegalStateException("synthetic readiness query failure")
        var actual: Throwable? = null
        var calls = 0
        now = 0L
        try {
            pollReadiness(
                BooleanSupplier { if (++calls == 2) throw failure else false },
                LongSupplier { now },
                LongConsumer { now += it },
                BiConsumer { _, _ -> },
            )
        } catch (error: Throwable) {
            actual = error
        }
        check(
            actual === failure && calls == 2,
            "readiness query exception propagates without retry",
        )
    }

    private fun monitorViewport(): DeviceTestSupport.MonitorViewport =
        DeviceTestSupport.monitorViewport(this, session)

    @Throws(Exception::class)
    private fun monitorTap(u: Float, v: Float) {
        val viewport = monitorViewport()
        val p = viewport.mapNormalizedToScreen(u, v)
        var expectedX = u * viewport.desktopWidth
        var expectedY = v * viewport.desktopHeight
        if (viewport.activityHost) {
            runOnMainSync {
                if (session!!.monitorWindow() !== viewport.window)
                    throw AssertionError("Monitor host changed before edge adjustment")
                DeviceTestSupport.usableMonitorSwipe(this, session, p, p.clone())
            }
            val mapped = viewport.unmapScreen(p[0], p[1])
            if (mapped == null) throw AssertionError("Tap must remain inside usable desktop")
            expectedX = mapped!![0]
            expectedY = mapped!![1]
        }
        if (monitorViewport() != viewport)
            throw AssertionError("Monitor viewport changed before contact")
        monitorGesture(0, floatArrayOf(p[0]), floatArrayOf(p[1]), intArrayOf(7))
        monitorGesture(1, floatArrayOf(p[0]), floatArrayOf(p[1]), intArrayOf(7))
        val expectedDown = monitorDown
        await({ InputTestActivity.lastUpDownTime == expectedDown }, "monitor tap delivered")
        check(!session!!.pointerVisible, "monitor touch hides pointer")
        check(
            (Math.abs(InputTestActivity.lastUpX - expectedX) < 2 &&
                Math.abs(InputTestActivity.lastUpY - expectedY) < 2),
            "monitor tap coordinate alignment expected=($expectedX,$expectedY) " +
                "actual=(${InputTestActivity.lastUpX},${InputTestActivity.lastUpY}) " +
                "injected=(${p[0]},${p[1]}) viewport=${viewport.width}x${viewport.height} " +
                "origin=(${viewport.originX},${viewport.originY}) rotation=${viewport.rotation} " +
                "external=${viewport.externalDisplay} desktop=${viewport.desktopDisplay}",
        )
    }

    private fun gesture(pad: View?, action: Int, xs: FloatArray?, ys: FloatArray?) {
        val now = SystemClock.uptimeMillis()
        if (action == 0) gestureDown = now
        val props = arrayOfNulls<MotionEvent.PointerProperties?>(xs!!.size)
        val coords = arrayOfNulls<MotionEvent.PointerCoords?>(xs!!.size)
        for (i in xs!!.indices) {
            props[i] = MotionEvent.PointerProperties()
            props[i]!!.id = i
            props[i]!!.toolType = 1
            coords[i] = MotionEvent.PointerCoords()
            coords[i]!!.x = xs!![i]
            coords[i]!!.y = ys!![i]
            coords[i]!!.pressure = 1f
        }
        val e =
            MotionEvent.obtain(
                gestureDown,
                now,
                action,
                xs!!.size,
                props,
                coords,
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0,
            )
        runOnMainSync({ pad!!.dispatchTouchEvent(e) })
        e!!.recycle()
    }

    private fun check(condition: Boolean, name: String?) {
        if (!condition) throw AssertionError(name)
        results.append("PASS ").append(name).append('\n')
    }

    @Throws(Exception::class)
    private fun await(condition: BooleanSupplier, name: String?) {
        val end = SystemClock.uptimeMillis() + 10000
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(50)
        check(condition.getAsBoolean(), name)
    }

    public override fun onCreate(args: Bundle?) {
        super.onCreate(args)
        physicalDisplay = args!!.getString("physicalDisplay")
        controllerOnly = "true" == args!!.getString("controllerOnly")
        uiOnly = "true" == args!!.getString("uiOnly")
        legacyNavigationOnly = "true" == args!!.getString("legacyNavigationOnly")
        splitNavigationOnly = "true" == args!!.getString("splitNavigationOnly")
        monitorOnly = "true" == args!!.getString("monitorOnly")
        keyboardOnly = "true" == args!!.getString("keyboardOnly")
        idleOnly = "true" == args!!.getString("idleOnly")
        iconOnly = "true" == args!!.getString("iconOnly")
        profilesOnly = "true" == args!!.getString("profilesOnly")
        childOnly = "true" == args!!.getString("childOnly")
        catalogOnly = "true" == args!!.getString("catalogOnly")
        protectedOnly = "true" == args!!.getString("protectedOnly")
        splitProbeOnly = "true" == args!!.getString("splitProbeOnly")
        splitOnly = "true" == args!!.getString("splitOnly")
        audioOnly = "true" == args!!.getString("audioOnly")
        debuggingOnly = "true" == args!!.getString("debuggingOnly")
        diagnosticsOnly = "true" == args!!.getString("diagnosticsOnly")
        workspaceOnly = "true" == args!!.getString("workspaceOnly")
        start()
    }

    public override fun runOnMainSync(action: Runnable?) {
        val failure = arrayOfNulls<Throwable?>(1)
        super.runOnMainSync({
            try {
                action!!.run()
            } catch (error: Throwable) {
                failure[0] = error
            }
        })
        if (failure[0] != null)
            throw AssertionError("Main-thread acceptance action failed", failure[0])
    }

    public override fun finish(code: Int, results: Bundle?) {
        var code = code
        try {
            DeviceTestSupport.cancelInjectedMonitor(this)
        } catch (error: Exception) {
            results!!.putString(
                "stream",
                (results!!.getString("stream", "") +
                    "\nFAIL injected contact cleanup before desktop teardown: " +
                    error),
            )
            code = Activity.RESULT_CANCELED
        }

        try {
            DeviceTestSupport.stopDesktopBeforeExit(this)
        } catch (error: Exception) {
            results!!.putString(
                "stream",
                (results!!.getString("stream", "") +
                    "\nFAIL desktop teardown before instrumentation exit: " +
                    error),
            )
            code = Activity.RESULT_CANCELED
        }

        try {
            DeviceTestSupport.stopInputDriver()
        } catch (error: Exception) {
            android.util.Log.w("DexDroidTest", "Test input driver cleanup failed", error)
        }

        try {
            restoreSplitFixture()
        } catch (error: Exception) {
            results!!.putString(
                "stream",
                results!!.getString("stream", "") + "\nFAIL split fixture restoration: " + error,
            )
            code = Activity.RESULT_CANCELED
        }

        super.finish(code, results)
    }

    public override fun onStart() {
        val result = Bundle()
        try {
            if (workspaceOnly)
                sendStatus(
                    0,
                    Bundle().apply { putString("stream", "PROGRESS workspace fixture setup\n") },
                )
            splitFixturePreferences = getTargetContext()!!.getSharedPreferences("desktop", 0)
            originalSplitPreferences =
                java.util.HashMap<String?, Any?>(splitFixturePreferences!!.getAll()!!)
            val fixture = splitFixturePreferences!!.edit()
            for (key in SplitLayoutPreferences.write(SplitLayout()).keys) fixture!!.remove(key)
            if (!fixture!!.commit())
                throw IllegalStateException("Cannot establish split fixture defaults")
            // Connect accessibility before opening windows, rather than during the first
            // monitor touch while the input/IME focus transaction is still changing.
            getUiAutomation()
            if (workspaceOnly)
                sendStatus(
                    0,
                    Bundle().apply {
                        putString("stream", "PROGRESS workspace accessibility connected\n")
                    },
                )
            DeviceTestSupport.cancelInjectedMonitor(this)
            if (!controllerOnly && !iconOnly) DeviceTestSupport.startInputDriver(this)
            if (workspaceOnly)
                sendStatus(
                    0,
                    Bundle().apply {
                        putString("stream", "PROGRESS workspace input driver connected\n")
                    },
                )
            if (diagnosticsOnly) {
                result.putString("stream", DiagnosticsAcceptance(this).run())
                finish(Activity.RESULT_OK, result)
                return
            }
            if (workspaceOnly) {
                result.putString("stream", WorkspaceShortcutAcceptance(this).run())
                finish(Activity.RESULT_OK, result)
                return
            }
            if (debuggingOnly) {
                result.putString("stream", DebuggingOffAcceptance(this).run())
                finish(Activity.RESULT_OK, result)
                return
            }
            if (audioOnly) {
                result.putString(
                    "stream",
                    AudioWorkerAcceptance(this).run() +
                        AudioWorkerAcceptance(this).retryAfterUnconfirmedEnd() +
                        AudioAcceptance(this).run(),
                )
                finish(Activity.RESULT_OK, result)
                return
            }
            if (legacyNavigationOnly) {
                result.putString("stream", LegacyNavigationAcceptance(this).run())
                finish(Activity.RESULT_OK, result)
                return
            }
            if (splitProbeOnly) {
                val output = SplitProbeChecks(this).run()
                result.putString("stream", output)
                finish(
                    if (output.contains("FAIL ")) Activity.RESULT_CANCELED else Activity.RESULT_OK,
                    result,
                )
                return
            }
            if (splitOnly || splitNavigationOnly) {
                val output = SplitAcceptance(this, splitNavigationOnly).run()
                result.putString("stream", output)
                finish(
                    if (output.contains("FAIL ")) Activity.RESULT_CANCELED else Activity.RESULT_OK,
                    result,
                )
                return
            }
            if (protectedOnly) {
                result.putString("stream", ProtectionAcceptance(this).run())
                finish(Activity.RESULT_OK, result)
                return
            }
            if (catalogOnly) {
                result.putString("stream", CatalogAcceptance(this).run())
                finish(Activity.RESULT_OK, result)
                return
            }
            if (childOnly) {
                val output = ChildLaunchAcceptance(this).run()
                result.putString("stream", output)
                finish(
                    if (output.contains("FAIL ")) Activity.RESULT_CANCELED else Activity.RESULT_OK,
                    result,
                )
                return
            }
            if (profilesOnly) {
                result.putString("stream", MonitorProfilesAcceptance(this).run())
                finish(Activity.RESULT_OK, result)
                return
            }
            if (iconOnly) {
                result.putString("stream", IconAcceptance(this).run())
                finish(Activity.RESULT_OK, result)
                return
            }
            if (uiOnly) {
                result.putString("stream", UiAcceptance(this).run())
                finish(Activity.RESULT_OK, result)
                return
            }
            if (monitorOnly) {
                result.putString("stream", MonitorAcceptance(this).run())
                finish(Activity.RESULT_OK, result)
                return
            }
            if (idleOnly) {
                result.putString("stream", IdleAcceptance(this).run())
                finish(Activity.RESULT_OK, result)
                return
            }
            if (keyboardOnly) {
                result.putString("stream", MonitorSearchAcceptance(this).run())
                finish(Activity.RESULT_OK, result)
                return
            }
            verifyReadinessPolling()
            for (r in 0..3) {
                val w = if (r % 2 == 0) 1920 else 1200
                val h = if (r % 2 == 0) 1200 else 1920
                val center = Geometry.map(.5f, .5f, r, 1920f, 1200f, w, h)
                check(
                    Math.abs(center[0] - 960) < .01 && Math.abs(center[1] - 600) < .01,
                    "rotation " + r + " center",
                )
                val corners =
                    arrayOf<FloatArray?>(
                        floatArrayOf(0f, 0f),
                        floatArrayOf(1f, 0f),
                        floatArrayOf(1f, 1f),
                        floatArrayOf(0f, 1f),
                    )
                for (c in corners!!) {
                    val p = Geometry.map(c!![0], c!![1], r, 1920f, 1200f, w, h)
                    check(
                        (p[0] == 0f || p[0] == 1920f) && (p[1] == 0f || p[1] == 1200f),
                        "rotation " + r + " corner",
                    )
                }
            }
            check(
                Geometry.recommendedDpi(1200) == 240 && Geometry.recommendedDpi(800) == 160,
                "density recommendations",
            )
            val fit = Geometry.map(0f, 0f, 0, 1920f, 1200f, 1920, 1080)
            check(fit[1] == 60f, "16:9 Fit bars on 16:10 monitor")
            val activity = DeviceTestSupport.startPhone(this)
            val connected = CountDownLatch(1)
            val connection =
                object : ServiceConnection {
                    public override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
                        session = (b as SessionService.LocalBinder).get()
                        connected.countDown()
                    }

                    public override fun onServiceDisconnected(n: ComponentName?) {}
                }
            getTargetContext()!!.bindService(
                Intent(getTargetContext(), SessionService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
            check(connected.await(5, TimeUnit.SECONDS), "controller bind")
            savedPreferences = java.util.HashMap<String?, Any?>(session!!.prefs!!.getAll()!!)
            if (controllerOnly) {
                await(
                    { !session!!.needsRecovery() },
                    "Shizuku connects automatically without Start button",
                )
                check(
                    session!!.external == null && session!!.id < 0,
                    "no desktop created on phone without monitor",
                )
                val queued = ComponentName(getTargetContext()!!, InputTestActivity::class.java!!)
                runOnMainSync({ session!!.launch(queued, null) })
                check(
                    session!!.diagnosticText().contains("App launch queued:"),
                    "app launch queued while monitor unavailable",
                )
                val controllerPad =
                    activity.getWindow()!!.getDecorView().findViewWithTag<View?>("touchpad")
                gesture(controllerPad, 0, floatArrayOf(100f), floatArrayOf(300f))
                gesture(controllerPad, 1, floatArrayOf(100f), floatArrayOf(300f))
                check(
                    session!!.diagnosticText().contains("Touchpad ignored: desktop=-1"),
                    "diagnostics explain unavailable desktop input",
                )
                runOnMainSync({ session!!.pauseDesktop() })
                session!!.clearDiagnostics()
                for (i in 0..299) session!!.log("Key queued: KEYCODE_BACK -> display " + i)
                check(
                    (!session!!
                        .diagnosticText()
                        .contains("Key queued: KEYCODE_BACK -> display 0\n") &&
                        session!!
                            .diagnosticText()
                            .contains("Key queued: KEYCODE_BACK -> display 299")),
                    "diagnostic history is bounded",
                )
                session!!.clearDiagnostics()
                check(
                    session!!.diagnosticText().contains("No events recorded."),
                    "diagnostics clear",
                )
                runOnMainSync({ activity.finish() })
                getTargetContext()!!.unbindService(connection)
                result.putString("stream", results.toString())
                finish(Activity.RESULT_OK, result)
                return
            }
            await(
                { session!!.id >= 0 && !session!!.busy },
                "automatic Shizuku virtual desktop creation",
            )
            if (android.os.Build.VERSION.SDK_INT >= 36) {
                val physicalMonitor = intArrayOf(-1)
                runOnMainSync({
                    physicalMonitor[0] =
                        session!!.monitorWindow()!!.getDecorView().getDisplay()!!.getDisplayId()
                })
                val permitsActivity =
                    getTargetContext()!!
                        .getSystemService<android.app.ActivityManager?>(
                            android.app.ActivityManager::class.java!!
                        )!!
                        .isActivityStartAllowedOnDisplay(
                            getTargetContext()!!,
                            physicalMonitor!![0],
                            Intent(getTargetContext(), MonitorActivity::class.java),
                        )
                check(
                    (permitsActivity ==
                        (session!!.monitorWindow()!!.getCallback() is MonitorActivity)),
                    "monitor host matches public display launch eligibility",
                )
                if (!permitsActivity)
                    check(
                        session!!.diagnosticText().contains("Presentation fallback"),
                        "Presentation fallback reports phone-task lifetime limitation",
                    )
            }
            val display = session!!.id
            val inputTest = ComponentName(getTargetContext()!!, InputTestActivity::class.java!!)
            val targetWindow = addMonitor(InputTestActivity::class.java!!.getName(), null, false)
            runOnMainSync({ session!!.launch(inputTest, null) })
            Thread.sleep(1000)
            runOnMainSync({
                session!!
                    .prefs!!
                    .edit()!!
                    .putBoolean("swipeScroll", false)!!
                    .remove("swipeScroll.dev.dexdroid")!!
                    .remove("swipeScroll.eu.kanade.tachiyomi")!!
                    .remove("scrollSpeed.eu.kanade.tachiyomi")!!
                    .apply()
                val original = session!!.activePackage
                session!!.activePackage = "eu.kanade.tachiyomi"
                check(session!!.swipeScrolling(), "Tachiyomi defaults to native swipe")
                session!!.setSwipeScrolling(false)
                session!!
                    .prefs!!
                    .edit()!!
                    .putFloat("scrollSpeed." + session!!.activePackage!!, .5f)!!
                    .apply()
                session!!.saveAppSettings()
                check(
                    (!session!!.swipeScrolling() &&
                        (session!!.prefs!!.getInt("app." + session!!.activePackage!!, -1) ==
                            session!!.rotation)),
                    "Save app persists scroll mode and rotation",
                )
                session!!.activePackage = "dev.dexdroid"
                check(!session!!.swipeScrolling(), "scroll mode isolated between apps")
                session!!.activePackage = "eu.kanade.tachiyomi"
                session!!.resetAppSettings()
                check(
                    (session!!.swipeScrolling() &&
                        session!!.scrollSpeed() == .2f &&
                        !session!!.prefs!!.contains("app." + session!!.activePackage!!)),
                    "Reset app restores defaults and clears rotation",
                )
                session!!.activePackage = original
                session!!.notifyUi()
            })
            await({ !session!!.busy }, "app settings ready")
            val nativeInputTask = DeviceTestSupport.nativeInputTask(this, display, inputTest)
            check(nativeInputTask >= 0, "rotation input captures one current native app task")
            val nativeInputUid = getTargetContext()!!.getApplicationInfo()!!.uid
            for (r in 0..3) {
                val rotation = r
                runOnMainSync({ session!!.rotate(rotation, true) })
                await({ !session!!.busy && session!!.rotation == rotation }, "resize rotation " + r)
                check(session!!.id == display, "task-preserving display ID " + r)
                await(
                    {
                        val d =
                            (getTargetContext()!!.getSystemService(Context.DISPLAY_SERVICE!!)
                                    as android.hardware.display.DisplayManager)
                                .getDisplay(session!!.id)
                        (d != null &&
                            d!!.getMode()!!.getPhysicalWidth() == session!!.width() &&
                            d!!.getMode()!!.getPhysicalHeight() == session!!.height())
                    },
                    "actual display dimensions " + r,
                )
                Thread.sleep(500)
                if (physicalDisplay != null) {
                    getUiAutomation()!!
                        .executeShellCommand(
                            ("screencap -d " +
                                physicalDisplay +
                                " -p /sdcard/dexdroid-check-r" +
                                r +
                                ".png")
                        )!!
                        .use({ capture ->
                            android.os.ParcelFileDescriptor.AutoCloseInputStream(capture)
                                .use({ stream -> while (stream.read() != -1) {} })
                        })
                }
                await(
                    {
                        val target = targetWindow!!.getLastActivity()
                        (target != null &&
                            !target!!.isDestroyed() &&
                            target!!.getDisplay()!!.getDisplayId() == session!!.id &&
                            target!!.getWindow()!!.getDecorView().isShown() &&
                            (target!!.getWindow()!!.getDecorView().getWidth() ==
                                session!!.width()) &&
                            (target!!.getWindow()!!.getDecorView().getHeight() ==
                                session!!.height()))
                    },
                    "target app window matches resized desktop " + r,
                )
                getUiAutomation()!!.waitForIdle(500, 10000)
                awaitSettledInput(
                    display,
                    nativeInputTask,
                    inputTest,
                    nativeInputUid,
                    "current native input window unobscured after rotation " + r,
                )
                val before = InputTestActivity.touchEvents
                runOnMainSync({
                    session!!.center()
                    session!!.click()
                })
                await({ InputTestActivity.touchEvents > before }, "targeted tap " + r)
                monitorTap(.5f, .5f)
                monitorTap(.1f, .1f)
                monitorTap(.9f, .1f)
                monitorTap(.1f, .9f)
                monitorTap(.9f, .9f)
                if (r == 1) monitorTap(.5f, 1f)
                else if (r == 2 || r == 3) monitorTap(1f, .5f) else monitorTap(0f, .5f)
            }
            val pinch = InputTestActivity.pinchEvents
            runOnMainSync({
                session!!.touch(0, floatArrayOf(400f), floatArrayOf(500f))
                session!!.touch(5 or 256, floatArrayOf(400f, 600f), floatArrayOf(500f, 500f))
                session!!.touch(2, floatArrayOf(350f, 650f), floatArrayOf(500f, 500f))
                session!!.touch(6 or 256, floatArrayOf(350f, 650f), floatArrayOf(500f, 500f))
                session!!.touch(1, floatArrayOf(350f), floatArrayOf(500f))
            })
            await(
                { InputTestActivity.pinchEvents >= pinch + 3 },
                "native two-pointer zoom ordering",
            )
            val wheel = InputTestActivity.wheelEvents
            runOnMainSync({
                session!!
                    .prefs!!
                    .edit()!!
                    .putFloat("scrollSpeed." + session!!.activePackage!!, .2f)!!
                    .apply()
                session!!.wheel(1f, 1f)
            })
            await({ InputTestActivity.wheelEvents > wheel }, "horizontal and vertical wheel input")
            check(
                (Math.abs(InputTestActivity.lastWheelH - .2f) < .001f &&
                    Math.abs(InputTestActivity.lastWheelV - .2f) < .001f),
                "per-app scroll speed applied to both axes",
            )
            check(session!!.pointerVisible, "touchpad wheel restores pointer")
            runOnMainSync({
                session!!
                    .prefs!!
                    .edit()!!
                    .putBoolean("swipeScroll", false)!!
                    .putBoolean("reverseScroll", false)!!
                    .apply()
            })
            val pad = activity.getWindow()!!.getDecorView().findViewWithTag<View?>("touchpad")
            check(pad != null, "phone touchpad exists")
            val tapsBefore = InputTestActivity.touchEvents
            gesture(pad, 0, floatArrayOf(300f), floatArrayOf(300f))
            gesture(pad, 1, floatArrayOf(300f), floatArrayOf(300f))
            await({ InputTestActivity.touchEvents > tapsBefore }, "phone tap recognizer")
            check(session!!.pointerVisible, "phone tap restores pointer")
            val pinchBefore = InputTestActivity.pinchEvents
            gesture(pad, 0, floatArrayOf(300f), floatArrayOf(300f))
            gesture(pad, 5 or 256, floatArrayOf(300f, 500f), floatArrayOf(300f, 300f))
            Thread.sleep(25)
            gesture(pad, 2, floatArrayOf(260f, 540f), floatArrayOf(300f, 300f))
            Thread.sleep(25)
            gesture(pad, 2, floatArrayOf(220f, 580f), floatArrayOf(300f, 300f))
            gesture(pad, 6 or 256, floatArrayOf(220f, 580f), floatArrayOf(300f, 300f))
            gesture(pad, 1, floatArrayOf(220f), floatArrayOf(300f))
            await({ InputTestActivity.pinchEvents >= pinchBefore + 3 }, "phone pinch recognizer")
            val wheelBefore = InputTestActivity.wheelEvents
            gesture(pad, 0, floatArrayOf(300f), floatArrayOf(300f))
            gesture(pad, 5 or 256, floatArrayOf(300f, 500f), floatArrayOf(300f, 300f))
            Thread.sleep(25)
            gesture(pad, 2, floatArrayOf(300f, 500f), floatArrayOf(360f, 360f))
            gesture(pad, 6 or 256, floatArrayOf(300f, 500f), floatArrayOf(360f, 360f))
            gesture(pad, 1, floatArrayOf(300f), floatArrayOf(360f))
            await({ InputTestActivity.wheelEvents > wheelBefore }, "phone scroll recognizer")
            val backBefore = InputTestActivity.backEvents
            gesture(pad, 0, floatArrayOf(100f), floatArrayOf(300f))
            gesture(pad, 5 or 256, floatArrayOf(100f, 200f), floatArrayOf(300f, 300f))
            gesture(pad, 5 or 512, floatArrayOf(100f, 200f, 300f), floatArrayOf(300f, 300f, 300f))
            gesture(pad, 2, floatArrayOf(350f, 450f, 550f), floatArrayOf(300f, 300f, 300f))
            gesture(pad, 6 or 512, floatArrayOf(350f, 450f, 550f), floatArrayOf(300f, 300f, 300f))
            gesture(pad, 6 or 256, floatArrayOf(350f, 450f), floatArrayOf(300f, 300f))
            gesture(pad, 1, floatArrayOf(350f), floatArrayOf(300f))
            await({ InputTestActivity.backEvents > backBefore }, "three finger Back recognizer")
            await(
                {
                    (session!!.diagnosticText().contains("Three-finger Back recognized") &&
                        session!!.diagnosticText().contains("Key sent: KEYCODE_BACK"))
                },
                "three-finger recognition and Back dispatch logged",
            )
            val rejectedBack = InputTestActivity.backEvents
            gesture(pad, 0, floatArrayOf(100f), floatArrayOf(300f))
            gesture(pad, 5 or 256, floatArrayOf(100f, 200f), floatArrayOf(300f, 300f))
            gesture(pad, 5 or 512, floatArrayOf(100f, 200f, 300f), floatArrayOf(300f, 300f, 300f))
            gesture(pad, 2, floatArrayOf(110f, 210f, 310f), floatArrayOf(300f, 300f, 300f))
            gesture(pad, 6 or 512, floatArrayOf(110f, 210f, 310f), floatArrayOf(300f, 300f, 300f))
            gesture(pad, 6 or 256, floatArrayOf(110f, 210f), floatArrayOf(300f, 300f))
            gesture(pad, 1, floatArrayOf(110f), floatArrayOf(300f))
            Thread.sleep(150)
            check(
                (InputTestActivity.backEvents == rejectedBack &&
                    session!!.diagnosticText().contains("Three-finger Back rejected")),
                "short three-finger movement rejected and explained",
            )
            val nativePinch = InputTestActivity.pinchEvents
            monitorGesture(0, floatArrayOf(800f), floatArrayOf(600f), intArrayOf(7))
            monitorGesture(
                5 or 256,
                floatArrayOf(800f, 1120f),
                floatArrayOf(600f, 600f),
                intArrayOf(7, 11),
            )
            monitorGesture(
                2,
                floatArrayOf(700f, 1220f),
                floatArrayOf(600f, 600f),
                intArrayOf(7, 11),
            )
            monitorGesture(
                6 or 256,
                floatArrayOf(700f, 1220f),
                floatArrayOf(600f, 600f),
                intArrayOf(7, 11),
            )
            monitorGesture(1, floatArrayOf(700f), floatArrayOf(600f), intArrayOf(7))
            await(
                {
                    (InputTestActivity.pinchEvents >= nativePinch + 3 &&
                        InputTestActivity.lastUpDownTime == monitorDown)
                },
                "physical monitor pinch forwarding and release",
            )
            check(
                InputTestActivity.lastPointer0 == 7 && InputTestActivity.lastPointer1 == 11,
                "monitor pointer IDs preserved",
            )
            check(InputTestActivity.lastDownTime == monitorDown, "monitor down time preserved")
            val arbitration = InputTestActivity.touchEvents
            monitorGesture(0, floatArrayOf(960f), floatArrayOf(600f), intArrayOf(7))
            await(
                {
                    (InputTestActivity.touchEvents > arbitration &&
                        InputTestActivity.lastDownTime == monitorDown)
                },
                "monitor contact active",
            )
            val monitorHeld = InputTestActivity.touchEvents
            runOnMainSync({ session!!.click() })
            Thread.sleep(200)
            check(
                InputTestActivity.touchEvents == monitorHeld,
                "phone clicks suppressed during monitor gesture",
            )
            monitorGesture(1, floatArrayOf(960f), floatArrayOf(600f), intArrayOf(7))
            await({ InputTestActivity.touchEvents > monitorHeld }, "monitor gesture ends")
            val fitPreviewRotation = session!!.rotation
            val fitPreviewDeadline = SystemClock.uptimeMillis() + 15000
            val fitPreviewCurrent = BooleanSupplier {
                SystemClock.uptimeMillis() < fitPreviewDeadline &&
                    session!!.id == display &&
                    !session!!.busy &&
                    session!!.rotation == fitPreviewRotation &&
                    session!!.baseW == 1920 &&
                    session!!.baseH == 1080 &&
                    session!!.dpi == 240
            }
            runOnMainSync({ session!!.preview(1920, 1080, 240) })
            await({ !session!!.busy && session!!.baseH == 1080 }, "Fit resolution preview")
            awaitSettledInput(
                display,
                nativeInputTask,
                inputTest,
                nativeInputUid,
                "current native input window unobscured after Fit preview",
            )
            check(fitPreviewCurrent.getAsBoolean(), "Fit preview current before bar contact")
            val ignored = InputTestActivity.touchEvents
            monitorGesture(0, floatArrayOf(960f), floatArrayOf(5f), intArrayOf(7))
            monitorGesture(1, floatArrayOf(960f), floatArrayOf(5f), intArrayOf(7))
            Thread.sleep(200)
            check(InputTestActivity.touchEvents == ignored, "Fit bars reject monitor touch")
            check(fitPreviewCurrent.getAsBoolean(), "Fit preview current after bar contact")
            check(fitPreviewCurrent.getAsBoolean(), "Fit preview current before content contact")
            val canceled = InputTestActivity.cancelEvents
            monitorGesture(0, floatArrayOf(960f), floatArrayOf(600f), intArrayOf(7))
            await(
                { InputTestActivity.lastDownTime == monitorDown },
                "Fit content contact delivered before moving outside",
            )
            check(fitPreviewCurrent.getAsBoolean(), "Fit preview current after content delivery")
            monitorGesture(2, floatArrayOf(960f), floatArrayOf(5f), intArrayOf(7))
            monitorGesture(1, floatArrayOf(960f), floatArrayOf(5f), intArrayOf(7))
            await(
                { InputTestActivity.cancelEvents > canceled },
                "monitor contact canceled on leaving content",
            )
            check(
                fitPreviewCurrent.getAsBoolean(),
                "Fit preview current after outside cancellation",
            )
            runOnMainSync({ session!!.confirm(false) })
            await({ !session!!.busy && session!!.baseH == 1200 }, "Fit preview reverted")
            runOnMainSync({ session!!.setMonitorTouchEnabled(false) })
            val disabled = InputTestActivity.touchEvents
            monitorGesture(0, floatArrayOf(960f), floatArrayOf(600f), intArrayOf(7))
            monitorGesture(1, floatArrayOf(960f), floatArrayOf(600f), intArrayOf(7))
            Thread.sleep(200)
            check(InputTestActivity.touchEvents == disabled, "monitor touch disable control")
            runOnMainSync({ session!!.setMonitorTouchEnabled(true) })
            val originalW = session!!.baseW
            val originalH = session!!.baseH
            val originalDpi = session!!.dpi
            val lowerPreviewRotation = session!!.rotation
            val lowerPreviewDeadline = SystemClock.uptimeMillis() + 15000
            val lowerPreviewCurrent = BooleanSupplier {
                SystemClock.uptimeMillis() < lowerPreviewDeadline &&
                    session!!.id == display &&
                    !session!!.busy &&
                    session!!.rotation == lowerPreviewRotation &&
                    session!!.baseW == 1280 &&
                    session!!.baseH == 800 &&
                    session!!.dpi == 160
            }
            runOnMainSync({ session!!.preview(1280, 800, 160) })
            await({ !session!!.busy && session!!.baseW == 1280 }, "lower resolution preview")
            awaitSettledInput(
                display,
                nativeInputTask,
                inputTest,
                nativeInputUid,
                "current native input window unobscured after lower resolution preview",
            )
            check(lowerPreviewCurrent.getAsBoolean(), "lower preview current before tap .5f, .5f")
            monitorTap(.5f, .5f)
            check(lowerPreviewCurrent.getAsBoolean(), "lower preview current after tap .5f, .5f")
            check(lowerPreviewCurrent.getAsBoolean(), "lower preview current before tap .1f, .1f")
            monitorTap(.1f, .1f)
            check(lowerPreviewCurrent.getAsBoolean(), "lower preview current after tap .1f, .1f")
            check(lowerPreviewCurrent.getAsBoolean(), "lower preview current before tap .9f, .9f")
            monitorTap(.9f, .9f)
            check(lowerPreviewCurrent.getAsBoolean(), "lower preview current after tap .9f, .9f")
            Thread.sleep(16000)
            await(
                {
                    (!session!!.busy &&
                        session!!.baseW == originalW &&
                        session!!.baseH == originalH &&
                        session!!.dpi == originalDpi)
                },
                "15 second automatic rollback",
            )
            runOnMainSync({ session!!.preview(1280, 800, 160) })
            await({ !session!!.busy && session!!.baseW == 1280 }, "second preview")
            runOnMainSync({ session!!.confirm(true) })
            check(
                session!!.prefs!!.getInt(session!!.monitorKey() + ".w", 0) == 1280,
                "confirmed monitor profile persistence",
            )
            runOnMainSync({ session!!.rememberApp(true) })
            check(
                session!!.prefs!!.getInt("app.dev.dexdroid", -1) == session!!.rotation,
                "saved app orientation",
            )
            val oldHost =
                if (session!!.monitorWindow()!!.getCallback() is MonitorActivity)
                    session!!.monitorWindow()!!.getCallback() as MonitorActivity?
                else null
            if (oldHost != null) {
                val originalIntent = oldHost!!.getIntent()
                runOnMainSync({
                    try {
                        val missingToken = Intent(originalIntent)
                        missingToken.removeExtra("monitorToken")
                        oldHost!!.setIntent(missingToken)
                        check(
                            !session!!.attachMonitor(
                                oldHost,
                                originalIntent!!.getIntExtra("generation", -1),
                            ),
                            "monitor host without current session token is rejected",
                        )
                    } finally {
                        oldHost!!.setIntent(originalIntent)
                    }
                })
            }
            runOnMainSync({
                session!!.rememberApp(false)
                session!!.resetMonitor()
                session!!.pauseDesktop()
            })
            await({ session!!.id == -1 }, "Stop releases desktop")
            if (oldHost != null)
                check(oldHost!!.isFinishing(), "Stop closes independent monitor host")
            runOnMainSync({ session!!.launch(inputTest, null) })
            await(
                { session!!.id >= 0 && !session!!.busy },
                "app launch automatically resumes desktop",
            )
            check(
                session!!.baseW == 1920 && session!!.baseH == 1200,
                "current output recommendation after reset",
            )
            if (oldHost != null) {
                val replacement = session!!.id
                runOnMainSync({
                    check(
                        !session!!.attachMonitor(
                            oldHost,
                            oldHost!!.getIntent()!!.getIntExtra("generation", -1),
                        ),
                        "stale monitor host cannot attach to replacement session",
                    )
                    session!!.detachMonitor(oldHost)
                })
                check(
                    session!!.id == replacement,
                    "stale host teardown cannot stop replacement session",
                )
            }
            runOnMainSync({ session!!.launch(inputTest, null) })
            Thread.sleep(600)
            runOnMainSync({
                session!!.rotate(0, true)
                session!!.report("Device checks passed. Ready to experiment.")
            })
            await({ !session!!.busy }, "final landscape")
            val runningId = session!!.id
            val backgroundBefore = InputTestActivity.touchEvents
            monitorGesture(0, floatArrayOf(960f), floatArrayOf(600f), intArrayOf(7))
            await(
                {
                    (InputTestActivity.touchEvents > backgroundBefore &&
                        InputTestActivity.lastDownTime == monitorDown)
                },
                "monitor gesture before backgrounding",
            )
            runOnMainSync({ activity.moveTaskToBack(true) })
            Thread.sleep(800)
            check(
                (session!!.id == runningId &&
                    session!!.monitorInputView() != null &&
                    session!!.sessionAwake()),
                "desktop persists while phone controller is backgrounded",
            )
            monitorGesture(2, floatArrayOf(1000f), floatArrayOf(640f), intArrayOf(7))
            monitorGesture(1, floatArrayOf(1000f), floatArrayOf(640f), intArrayOf(7))
            await(
                { InputTestActivity.lastUpDownTime == monitorDown },
                ("monitor touch works in background: " +
                    session!!.diagnosticText() +
                    " cancels=" +
                    InputTestActivity.cancelEvents +
                    " lastUp=" +
                    InputTestActivity.lastUpDownTime +
                    " expected=" +
                    monitorDown),
            )
            monitorTap(.1f, .1f)
            check(
                session!!.id == runningId,
                "fully injected fresh touch while phone is backgrounded",
            )
            val independentHost = session!!.monitorWindow()!!.getCallback() is MonitorActivity
            runOnMainSync({ activity.finish() })
            getTargetContext()!!.unbindService(connection)
            if (independentHost || android.os.Build.VERSION.SDK_INT < 36) {
                Thread.sleep(800)
                check(
                    (session!!.id == runningId &&
                        session!!.monitorInputView() != null &&
                        session!!.sessionAwake()),
                    "desktop persists with controller destroyed and no bindings",
                )
                monitorTap(.1f, .1f)
                check(session!!.id == runningId, "fresh touch after controller destruction")
            } else {
                Thread.sleep(800)
                if (session!!.id == runningId) {
                    check(
                        session!!.monitorInputView() != null && session!!.sessionAwake(),
                        "OEM retains Presentation resources after controller destruction",
                    )
                    monitorTap(.1f, .1f)
                    check(
                        session!!.id == runningId,
                        "retained OEM Presentation delivers fresh touch",
                    )
                } else {
                    await(
                        {
                            (session!!.id < 0 &&
                                session!!.monitorInputView() == null &&
                                !session!!.sessionAwake())
                        },
                        "Presentation-only host releases desktop after phone task removal",
                    )
                    DeviceTestSupport.startPhone(this)
                    await(
                        { session!!.id >= 0 && !session!!.busy },
                        "Presentation fallback recovers on controller reopen",
                    )
                    runOnMainSync({ session!!.launch(inputTest, null) })
                    await(
                        { !session!!.busy && session!!.hasDesktopApp() },
                        "fallback app launch after recovery",
                    )
                    Thread.sleep(500)
                    monitorTap(.1f, .1f)
                }
            }
            restorePreferences()
            result.putString("stream", results.toString())
            finish(Activity.RESULT_OK, result)
        } catch (e: Throwable) {
            restorePreferences()
            result.putString(
                "stream",
                results.toString() + "FAIL " + android.util.Log.getStackTraceString(e),
            )
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
