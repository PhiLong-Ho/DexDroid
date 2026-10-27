package dev.dexdroid

import android.app.Instrumentation
import android.content.*
import android.graphics.Bitmap
import android.os.*
import android.view.*
import android.widget.TextView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Focused real-display feedback and native touch-through, using only synthetic app content. */
internal class LaunchFeedbackAcceptance(
    private val runner: Instrumentation,
    private val realApps: Boolean = false,
) {
    private lateinit var session: SessionService
    private lateinit var phone: MainActivity
    private val results = StringBuilder()
    private val fixture =
        ComponentName("dev.dexdroid.test", "dev.dexdroid.fixture.NoticeTouchFixtureActivity")
    @Volatile private var fixtureState: Bundle? = null

    private fun main(action: () -> Unit) {
        val done = CountDownLatch(1)
        var error: Throwable? = null
        Handler(Looper.getMainLooper()).post {
            try {
                action()
            } catch (failure: Throwable) {
                error = failure
            } finally {
                done.countDown()
            }
        }
        check(done.await(10, TimeUnit.SECONDS)) { "Main callback exceeded deadline" }
        error?.let { throw it }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> read(action: () -> T): T {
        var value: Any? = null
        main { value = action() }
        return value as T
    }

    private fun verify(ok: Boolean, message: String) {
        check(ok) { message }
        results.append("PASS $message\n")
        runner.sendStatus(0, Bundle().apply { putString("stream", "PROGRESS $message\n") })
    }

    private fun await(message: String, condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 25000
        while (!condition() && SystemClock.uptimeMillis() < until) Thread.sleep(40)
        verify(condition(), message)
    }

    private fun stable() =
        await("desktop ready for feedback check") {
            read { session.id >= 0 && !session.busy && !session.awaitingConfirmation() }
        }

    private fun visibleText(value: String): Boolean = read {
        val text = phone.window.decorView.findViewWithTag<TextView>("phone-status-operation")
        val offset = text.text.toString().indexOf(value)
        val layout = text.layout
        val rect = android.graphics.Rect()
        if (offset < 0 || layout == null || !text.getGlobalVisibleRect(rect)) false
        else {
            val line = layout.getLineForOffset(offset)
            val location = IntArray(2)
            text.getLocationOnScreen(location)
            val top = location[1] + text.totalPaddingTop + layout.getLineTop(line)
            val bottom = location[1] + text.totalPaddingTop + layout.getLineBottom(line)
            line < text.maxLines && top >= rect.top && bottom <= rect.bottom
        }
    }

    private fun toggleStatusDetails(expanded: Boolean) {
        main {
            verify(
                phone.window.decorView.findViewWithTag<View>("phone-status-expand").performClick(),
                "status disclosure click handled",
            )
        }
        await(if (expanded) "status details expanded" else "status details collapsed") {
            read {
                phone.window.decorView.findViewWithTag<View>("phone-status-operation").isShown ==
                    expanded
            }
        }
    }

    private fun launchFixture() {
        var done = false
        var error: String? = null
        main {
            session.launch(
                fixture,
                session.rotation,
                {
                    error = it
                    done = true
                },
            )
        }
        await("synthetic fixture launch completed") { read { done } }
        verify(error == null, "synthetic fixture placement confirmed")
    }

    private fun shot(name: String, monitor: Boolean) {
        val window = read { if (monitor) session.monitorWindow()!! else phone.window }
        await("$name window laid out") {
            read { window.decorView.isLaidOut && window.decorView.width > 0 }
        }
        val bitmap =
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
        verify(
            done.await(5, TimeUnit.SECONDS) && code == PixelCopy.SUCCESS,
            "$name copies actual target window",
        )
        val folder =
            File(
                runner.targetContext.externalCacheDir,
                if (realApps) "launch-feedback-real-apps" else "launch-feedback-acceptance",
            )
        check(folder.isDirectory || folder.mkdirs())
        File(folder, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun contactThroughNotice() {
        val id = read { session.id }
        await("fullscreen fixture reports clickable content on managed display") {
            fixtureState?.let {
                it.getString("page") == "navigation-parent" &&
                    it.getInt("display") == id &&
                    it.getInt("targetWidth") > 0 &&
                    it.getInt("targetHeight") > 0
            } == true
        }
        val content = Bundle(checkNotNull(fixtureState))
        val task = DeviceTestSupport.nativeInputTask(runner, id, fixture)
        val dimensions = read {
            session.split?.dimensions(0, session.width(), session.height(), false)
                ?: SplitLayout.Box(0, 0, session.width(), session.height())
        }
        await("fixture input admitted before notice contact") {
            DeviceTestSupport.settledInputWindow(
                runner,
                id,
                task,
                fixture,
                runner.context.applicationInfo.uid,
                dimensions.width,
                dimensions.height,
            )
        }
        val point = read {
            val notice = session.monitorShellView()!!.findViewWithTag<View>("monitor-launch-notice")
            verify(
                notice.isShown && !notice.isClickable && !notice.isFocusable,
                "notice is visible and does not own touch or focus",
            )
            val pane =
                session.split
                    ?.state()
                    ?.pane(0, session.width(), session.height(), session.split!!.gap())
                    ?: SplitLayout.Box(0, 0, session.width(), session.height())
            val left = maxOf(notice.left, pane.x)
            val right = minOf(notice.right, pane.x + pane.width)
            val top = maxOf(notice.top, pane.y)
            val bottom = minOf(notice.bottom, pane.y + pane.height)
            verify(right > left && bottom > top, "notice intersects actual fixture pane")
            val x = (left + right) * .5f
            val y = (top + bottom) * .5f
            val localX = x - pane.x
            val localY = y - pane.y
            verify(
                kotlin.math.abs(localX - content.getFloat("targetX")) <
                    content.getInt("targetWidth") * .5f &&
                    kotlin.math.abs(localY - content.getFloat("targetY")) <
                        content.getInt("targetHeight") * .5f,
                "notice contact lies inside native clickable content",
            )
            floatArrayOf(x, y)
        }
        val viewport = DeviceTestSupport.monitorViewport(runner, session)
        val screen = viewport.mapVirtualToScreen(point[0], point[1])
        fixtureState = null
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event =
                MotionEvent.obtain(
                    down,
                    SystemClock.uptimeMillis(),
                    action,
                    1,
                    arrayOf(
                        MotionEvent.PointerProperties().apply {
                            this.id = 1
                            toolType = MotionEvent.TOOL_TYPE_FINGER
                        }
                    ),
                    arrayOf(
                        MotionEvent.PointerCoords().apply {
                            x = screen[0]
                            y = screen[1]
                            pressure = 1f
                        }
                    ),
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
                verify(
                    DeviceTestSupport.injectOnDisplay(event, viewport.externalDisplay),
                    "physical notice contact $action injected",
                )
            } finally {
                event.recycle()
            }
        }
        await("contact beneath notice opens actual native child") {
            fixtureState?.getString("page") == "navigation-child" &&
                fixtureState?.getInt("display") == id
        }
        main { session.key(KeyEvent.KEYCODE_BACK) }
        await("native Back returns after notice contact") {
            fixtureState?.getString("page") == "navigation-parent"
        }
    }

    private fun pendingAtRotation(rotation: Int, split: Boolean = false) {
        main { if (!split) session.rotate(rotation, false) }
        stable()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        main {
            session.desktopWork {
                entered.countDown()
                release.await(20, TimeUnit.SECONDS)
            }
        }
        verify(entered.await(3, TimeUnit.SECONDS), "native work controlled before acceptance")
        val padHeight = read { phone.window.decorView.findViewWithTag<View>("touchpad").height }
        try {
            val started = SystemClock.uptimeMillis()
            main {
                val choice = session.beginAppSelection(0, false)
                verify(
                    session.launchSelection(choice, fixture, session.rotation),
                    "ordinary picker request accepted",
                )
                verify(session.operationPending(), "acceptance immediately exposes pending state")
                val cue = phone.window.decorView.findViewWithTag<TextView>("phone-status-cue")
                verify(
                    cue.text.toString().startsWith("Opening "),
                    "phone collapsed cue acknowledges before native work",
                )
                verify(
                    cue.text.toString().contains("Show details"),
                    "status disclosure remains available",
                )
            }
            await("monitor pending notice laid out") {
                read {
                    session
                        .monitorShellView()!!
                        .findViewWithTag<View>("monitor-launch-notice")
                        .let { it.isShown && it.width > 0 && it.height > 0 }
                }
            }
            results.append(
                "MEASURE controlled_acceptance_to_notice_observation_ms=${SystemClock.uptimeMillis() - started}\n"
            )
            shot("monitor-pending-${if (split) "split" else "r$rotation"}", true)
            main {
                verify(
                    phone.window.decorView.findViewWithTag<View>("touchpad").height == padHeight,
                    "collapsed pending feedback preserves touchpad height",
                )
            }
            if (rotation == 0) {
                shot("phone-pending-${if (split) "split" else "single"}", false)
                toggleStatusDetails(true)
                main {
                    val operation =
                        phone.window.decorView.findViewWithTag<TextView>("phone-status-operation")
                    verify(
                        operation.isShown && operation.text.toString().contains("Opening "),
                        "expanded status exposes current pending work",
                    )
                }
                await("expanded pending notice line is inside visible bounds") {
                    visibleText("Opening ")
                }
                shot("phone-pending-expanded-${if (split) "split" else "single"}", false)
                toggleStatusDetails(false)
            }
        } finally {
            release.countDown()
        }
        await("real completion settles only accepted notice") {
            read {
                !session.operationPending() &&
                    session.operationNotice()?.endsWith(" opened") == true
            }
        }
        contactThroughNotice()
    }

    private fun rejectedLaunch() {
        val actual = read { session.primaryBridge()!! }
        val field =
            SessionService::class.java.getDeclaredField("bridge").apply { isAccessible = true }
        val proxy =
            java.lang.reflect.Proxy.newProxyInstance(
                IDeskService::class.java.classLoader,
                arrayOf(IDeskService::class.java),
            ) { _, method, args ->
                if (method.name == "launch")
                    throw IllegalStateException("Controlled native rejection")
                try {
                    method.invoke(actual, *(args ?: emptyArray()))
                } catch (failure: java.lang.reflect.InvocationTargetException) {
                    throw failure.targetException
                }
            } as IDeskService
        try {
            main {
                field.set(session, proxy)
                verify(
                    session.launchSelection(
                        session.beginAppSelection(0, false),
                        fixture,
                        session.rotation,
                    ),
                    "rejection fixture accepted",
                )
            }
            await("native rejection produces recovery notice") {
                read {
                    !session.operationPending() &&
                        session.operationNotice()?.contains("didn’t open") == true
                }
            }
            main {
                verify(
                    session.launchResult()?.contains("Open Apps to try again") == true,
                    "full rejection recovery retained",
                )
            }
            toggleStatusDetails(true)
            main {
                val operation =
                    phone.window.decorView.findViewWithTag<TextView>("phone-status-operation")
                verify(
                    operation.isShown &&
                        operation.text.toString().contains("Open Apps to try again"),
                    "expanded rejection recovery is visible",
                )
            }
            await("expanded retry instruction is inside visible bounds") {
                visibleText("Open Apps to try again")
            }
            shot("phone-rejected", false)
            shot("monitor-rejected", true)
            toggleStatusDetails(false)
        } finally {
            main { field.set(session, actual) }
        }
    }

    private fun requestedApps(): String {
        val names =
            listOf(
                "Settings" to "com.android.settings",
                "Clock" to "com.android.deskclock",
                "Chrome" to "com.android.chrome",
                "Calculator" to "com.miui.calculator",
                "Musicolet" to "in.krosbits.musicolet",
            )
        fun open(name: String, pkg: String, pane: Int) {
            val component =
                checkNotNull(
                    runner.targetContext.packageManager.getLaunchIntentForPackage(pkg)?.component
                )
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            main {
                session.desktopWork {
                    entered.countDown()
                    release.await(15, TimeUnit.SECONDS)
                }
            }
            verify(entered.await(3, TimeUnit.SECONDS), "$name controlled pending window ready")
            try {
                main {
                    verify(
                        session.launchSelection(
                            session.beginAppSelection(pane, false),
                            component,
                            session.rotation,
                        ),
                        "$name accepted on requested monitor pane",
                    )
                    verify(
                        session.operationPending(),
                        "$name pending feedback precedes native work",
                    )
                    verify(
                        phone.window.decorView
                            .findViewWithTag<TextView>("phone-status-cue")
                            .text
                            .toString()
                            .startsWith("Opening "),
                        "$name visible phone acknowledgment",
                    )
                }
                await("$name monitor acknowledgment visible") {
                    read {
                        session
                            .monitorShellView()!!
                            .findViewWithTag<View>("monitor-launch-notice")
                            .isShown
                    }
                }
                shot(
                    "${name.lowercase()}-pending-${if (session.splitEnabled()) "split" else "single"}",
                    true,
                )
            } finally {
                release.countDown()
            }
            await("$name native completion acknowledged") {
                read {
                    !session.operationPending() &&
                        session.operationNotice()?.endsWith(" opened") == true
                }
            }
            val display = read { session.split?.displayId(pane) ?: session.id }
            await("$name package owns actual desktop task") {
                val dump = DeviceTestSupport.shell(runner, "am stack list").orEmpty()
                dump.split(Regex("(?=Stack id=|RootTask id=)")).any {
                    Regex("displayId=$display(?:\\s|$)")
                        .containsMatchIn(it.substringBefore('\n')) && it.contains("$pkg/")
                }
            }
            Thread.sleep(
                250
            ) // Capture settling only; not an application delay or performance measurement.
            shot(
                "${name.lowercase()}-opened-${if (session.splitEnabled()) "split" else "single"}",
                true,
            )
        }
        main { session.rotate(0, false) }
        stable()
        for ((name, pkg) in names) open(name, pkg, 0)
        main { session.enableSplit(false) }
        stable()
        open("Settings", "com.android.settings", 0)
        open("Clock", "com.android.deskclock", 1)
        return results.toString()
    }

    private fun stoppedThenDeferred() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        main {
            session.desktopWork {
                entered.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
        }
        verify(entered.await(3, TimeUnit.SECONDS), "stop fixture worker controlled")
        try {
            main {
                session.launchSelection(
                    session.beginAppSelection(0, false),
                    fixture,
                    session.rotation,
                )
                session.pauseDesktop()
                verify(
                    !session.operationPending(),
                    "explicit Stop retires pending feedback immediately",
                )
            }
        } finally {
            release.countDown()
        }
        main {
            verify(
                session.launchSelection(
                    session.beginAppSelection(0, false),
                    fixture,
                    session.rotation,
                ),
                "deferred picker request accepted",
            )
            verify(session.operationPending(), "deferred startup retains pending notice")
        }
        await("deferred startup preserves real completion callback") {
            read {
                session.id >= 0 &&
                    !session.busy &&
                    !session.operationPending() &&
                    session.operationNotice()?.endsWith(" opened") == true
            }
        }
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun run(): String {
        val connected = CountDownLatch(1)
        val binding =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    session = (binder as SessionService.LocalBinder).get()
                    connected.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }
        phone = DeviceTestSupport.startPhoneBounded(runner)
        verify(
            runner.targetContext.bindService(
                Intent(runner.targetContext, SessionService::class.java),
                binding,
                Context.BIND_AUTO_CREATE,
            ),
            "bind feedback acceptance",
        )
        verify(connected.await(5, TimeUnit.SECONDS), "feedback service connected")
        val expanded = read { session.prefs!!.getBoolean("statusExpanded", false) }
        val originalRotation = read { session.rotation }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.getIntExtra("uid", -1) == runner.context.applicationInfo.uid)
                        fixtureState = intent.extras
                }
            }
        if (Build.VERSION.SDK_INT >= 33)
            runner.targetContext.registerReceiver(
                receiver,
                IntentFilter(dev.dexdroid.fixture.LaunchParentActivity.ACTION),
                Context.RECEIVER_EXPORTED,
            )
        else
            runner.targetContext.registerReceiver(
                receiver,
                IntentFilter(dev.dexdroid.fixture.LaunchParentActivity.ACTION),
            )
        try {
            stable()
            main {
                session.prefs!!.edit().putBoolean("statusExpanded", false).apply()
                session.setMonitorTouchEnabled(true)
                session.split?.end()
            }
            stable()
            if (realApps) return requestedApps()
            launchFixture()
            for (rotation in 0..3) pendingAtRotation(rotation)
            main {
                session.rotate(0, false)
                session.clearLaunchFeedback()
            }
            stable()
            rejectedLaunch()
            main { session.enableSplit(false) }
            stable()
            pendingAtRotation(0, true)
            main { session.split!!.end() }
            stable()
            stoppedThenDeferred()
            return results.toString()
        } finally {
            main {
                session.prefs!!.edit().putBoolean("statusExpanded", expanded).apply()
                session.rotate(originalRotation, false)
                session.retireLaunchFeedback()
            }
            runner.targetContext.unregisterReceiver(receiver)
            runner.targetContext.unbindService(binding)
        }
    }
}
