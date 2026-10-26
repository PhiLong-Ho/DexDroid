package dev.dexdroid

import android.app.Activity
import android.app.ActivityOptions
import android.app.Application
import android.app.Instrumentation
import android.content.*
import android.graphics.Bitmap
import android.os.*
import android.view.*
import android.widget.TextView
import dev.dexdroid.fixture.LaunchParentActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Physical output, public fixture entry and normal Settings app. All captures stay private. */
internal class WorkspaceShortcutAcceptance(private val runner: Instrumentation) {
    private lateinit var session: SessionService
    private lateinit var phone: MainActivity
    private val results = StringBuilder()
    private val fixture =
        ComponentName("dev.dexdroid.test", "dev.dexdroid.fixture.NavigationFixtureActivity")
    @Volatile private var state: Bundle? = null

    private fun main(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
            return
        }
        val completed = CountDownLatch(1)
        var failure: Throwable? = null
        Handler(Looper.getMainLooper()).post {
            try {
                action()
            } catch (error: Throwable) {
                failure = error
            } finally {
                completed.countDown()
            }
        }
        if (!completed.await(10, TimeUnit.SECONDS)) {
            val stack = Looper.getMainLooper().thread.stackTrace.joinToString("\n")
            android.util.Log.e(
                "DexDroidWorkspaceTest",
                "Main-thread wait exceeded 10 seconds\n$stack",
            )
            throw AssertionError("Main-thread wait exceeded 10 seconds: $stack")
        }
        failure?.let { throw it }
    }

    private fun progress(label: String) {
        android.util.Log.i("DexDroidWorkspaceTest", label)
        runner.sendStatus(0, Bundle().apply { putString("stream", "PROGRESS $label\n") })
    }

    private val lifecycle =
        object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is MainActivity) phone = activity
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {}

            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        }

    private fun startPhone(): MainActivity {
        progress("opening display-0 controller")
        main {
            runner.targetContext.startActivity(
                Intent(runner.targetContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle(),
            )
        }
        await("phone controller resumed on display 0") {
            read {
                ::phone.isInitialized &&
                    !phone.isDestroyed &&
                    !phone.isFinishing &&
                    phone.window.decorView.isAttachedToWindow &&
                    phone.display?.displayId == 0
            }
        }
        return phone
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> read(action: () -> T): T {
        var value: Any? = null
        main { value = action() }
        return value as T
    }

    private fun check(ok: Boolean, label: String) {
        if (!ok) throw AssertionError(label)
        results.append("PASS $label\n")
        progress(label)
    }

    private fun await(label: String, condition: () -> Boolean) {
        progress("waiting: $label")
        val deadline = SystemClock.uptimeMillis() + 25000
        while (!condition() && SystemClock.uptimeMillis() < deadline) Thread.sleep(40)
        check(condition(), label)
    }

    private fun stable() =
        await("workspace desktop stable") {
            read { session.id >= 0 && !session.busy && !session.awaitingConfirmation() }
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

    private fun find(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup)
            for (i in 0 until root.childCount) find(root.getChildAt(i), predicate)?.let {
                return it
            }
        return null
    }

    private fun phoneRoot() = phone.controllerMenu()!!.window!!.decorView

    private fun openPhone() {
        main {
            phone.controllerMenu()?.dismiss()
            find(phone.window.decorView) { it.contentDescription == "Settings" }!!.performClick()
            var target =
                find(phoneRoot()) { it is TextView && it.text.toString() == "Workspace shortcut" }!!
            while (!target.isClickable && target.parent is View) target = target.parent as View
            target.performClick()
        }
        await("phone workspace page ready") {
            read {
                phoneRoot().findViewWithTag<TextView>("phone-menu-title")?.text ==
                    "Workspace shortcut"
            }
        }
    }

    private fun clickPhone(tag: String) {
        await("$tag laid out") {
            read { phoneRoot().findViewWithTag<View>(tag)?.height?.let { it > 0 } == true }
        }
        main {
            val target = phoneRoot().findViewWithTag<View>(tag)
            target.requestRectangleOnScreen(
                android.graphics.Rect(0, 0, target.width, target.height),
                true,
            )
        }
        runner.waitForIdleSync()
        main {
            val target = phoneRoot().findViewWithTag<View>(tag)
            check(
                target.isEnabled &&
                    target.isClickable &&
                    target.getGlobalVisibleRect(android.graphics.Rect()),
                "reachable enabled $tag",
            )
            check(
                target.height >= 48 * target.resources.displayMetrics.density,
                "$tag accessible target height",
            )
            target.performClick()
        }
    }

    private fun launch(component: ComponentName, pane: Int? = null) {
        var done = false
        var failure: String? = null
        main {
            val complete: (String?) -> Unit = {
                failure = it
                done = true
            }
            if (pane == null) session.launch(component, null, complete)
            else session.split!!.launch(component, null, pane, complete)
        }
        await("fixture app launch completes") { read { done } }
        if (failure != null) {
            val detail = read { "${session.status}\n${session.diagnosticEvents().takeLast(6000)}" }
            throw AssertionError("fixture placement failed: $failure\n$detail")
        }
        check(true, "fixture placement succeeded")
    }

    private fun save(): WorkspaceSnapshot {
        openPhone()
        clickPhone("phone-workspace-save")
        await("workspace saved after app validation") {
            read { !session.workspaceShortcut.busy && session.workspaceShortcut.saved != null }
        }
        main {
            check(
                session.workspaceShortcut.message.startsWith("Workspace saved"),
                "Save reports a confirmed snapshot update",
            )
        }
        return read { session.workspaceShortcut.saved!! }
    }

    private fun activate() {
        val started = SystemClock.uptimeMillis()
        val prior = read { session.workspaceShortcut.activationGuard() }
        main {
            phone.controllerMenu()?.dismiss()
            runner.targetContext.startActivity(
                Intent(runner.targetContext, WorkspaceShortcutActivity::class.java)
                    .setAction(WorkspaceShortcut.RESTORE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle(),
            )
        }
        try {
            await("private shortcut entry delivers a fresh activation") { !prior() }
            await("private shortcut entry completes ordered restoration") {
                read { !session.workspaceShortcut.active }
            }
            main {
                check(
                    session.workspaceShortcut.message == "Workspace restored",
                    "restore outcome: ${session.workspaceShortcut.message}",
                )
            }
        } catch (error: Throwable) {
            val detail = read {
                "message=${session.workspaceShortcut.message}; status=${session.status}; active=${session.workspaceShortcut.active}; busy=${session.busy}; id=${session.id}; layout=${session.layoutStatus()}"
            }
            throw AssertionError("Shortcut acceptance stopped: $detail", error)
        }
        results.append(
            "MEASURE activation_to_restored_ms=${SystemClock.uptimeMillis() - started}\n"
        )
        phone = startPhone()
        stable()
    }

    private fun verify(saved: WorkspaceSnapshot, requestedRotation: Int = saved.rotation) {
        main {
            check(
                saved.rotation == requestedRotation,
                "snapshot stores independently requested rotation $requestedRotation",
            )
            check(
                session.rotation == requestedRotation,
                "requested whole-output rotation $requestedRotation restored",
            )
            val expectedWidth = if (requestedRotation % 2 == 0) session.baseW else session.baseH
            val expectedHeight = if (requestedRotation % 2 == 0) session.baseH else session.baseW
            check(
                session.width() == expectedWidth && session.height() == expectedHeight,
                "restored canvas axes match requested rotation $requestedRotation",
            )
            check(session.splitEnabled() == saved.split, "saved single/split mode restored")
            if (saved.split) {
                val actual = session.split!!.state()
                val expected = saved.layout()
                check(
                    actual.vertical == expected.vertical &&
                        actual.swapped == expected.swapped &&
                        kotlin.math.abs(actual.ratio - expected.ratio) < .002f,
                    "saved direction ratio and pane mapping restored",
                )
                check(
                    actual.selected == expected.selected && actual.maximized == expected.maximized,
                    "saved selection and fullscreen restored",
                )
            }
        }
        for (pane in 0..if (saved.split) 1 else 0) {
            val app = saved.app(pane)
            if (app == null) continue
            val display = read { session.split?.displayId(pane) ?: session.id }
            val component = ComponentName.unflattenFromString(app.component)!!
            await("saved app has native task on pane $pane") {
                if (saved.maximized >= 0 && saved.maximized != pane) {
                    val report = DeviceTestSupport.shell(runner, "am stack list")
                    DisplayTasks.parse(report, component.flattenToString()).count {
                        it != null && it.display == display && it.matches && it.tasks == 1
                    } == 1
                } else DeviceTestSupport.nativeInputTask(runner, display, component) >= 0
            }
        }
    }

    private fun touchFixture(pane: Int, returnToParent: Boolean = true) {
        val display = read { session.split?.displayId(pane) ?: session.id }
        val task = DeviceTestSupport.nativeInputTask(runner, display, fixture)
        val dimensions = read {
            session.split?.dimensions(pane, session.width(), session.height(), false)
                ?: SplitLayout.Box(0, 0, session.width(), session.height())
        }
        val uid = runner.context.applicationInfo.uid
        await("restored fixture has current native input admission") {
            DeviceTestSupport.settledInputWindow(
                runner,
                display,
                task,
                fixture,
                uid,
                dimensions.width,
                dimensions.height,
            )
        }
        val viewport = DeviceTestSupport.monitorViewport(runner, session)
        val box = read {
            session.split
                ?.state()
                ?.pane(pane, session.width(), session.height(), session.split!!.gap())
                ?: SplitLayout.Box(0, 0, session.width(), session.height())
        }
        val point = viewport.mapVirtualToScreen(box.x + box.width * .5f, box.y + box.height * .5f)
        state = null
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val props =
                MotionEvent.PointerProperties().apply {
                    id = 1
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            val coords =
                MotionEvent.PointerCoords().apply {
                    x = point[0]
                    y = point[1]
                    pressure = 1f
                }
            val event =
                MotionEvent.obtain(
                    now,
                    SystemClock.uptimeMillis(),
                    action,
                    1,
                    arrayOf(props),
                    arrayOf(coords),
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
                    DeviceTestSupport.injectOnDisplay(event, viewport.externalDisplay),
                    "restored fixture physical monitor contact $action",
                )
            } finally {
                event.recycle()
            }
        }
        await("restored app accepts touch and opens private child") {
            state?.getString("page") == "navigation-child" &&
                state?.getInt("display") == display &&
                state?.getInt("task") == task
        }
        check(
            state?.getInt("windowWidth") == dimensions.width &&
                state?.getInt("windowHeight") == dimensions.height,
            "native fixture window dimensions match restored rotated pane",
        )
        if (!returnToParent) return
        main { session.key(KeyEvent.KEYCODE_BACK) }
        await("Back returns restored fixture to public parent") {
            state?.getString("page") == "navigation-parent" &&
                state?.getInt("display") == display &&
                state?.getInt("task") == task
        }
    }

    private fun shot(name: String, monitor: Boolean = false) {
        val window = read {
            if (monitor) session.monitorWindow()!!
            else phone.controllerMenu()?.window ?: phone.window
        }
        await("capture window laid out: $name") {
            read {
                window.decorView.isAttachedToWindow &&
                    window.decorView.isLaidOut &&
                    window.decorView.width > 0 &&
                    window.decorView.height > 0
            }
        }
        // Allow the existing dialog transition to settle, then copy the actual target window.
        Thread.sleep(200)
        val bitmap =
            Bitmap.createBitmap(
                window.decorView.width,
                window.decorView.height,
                Bitmap.Config.ARGB_8888,
            )
        val latch = CountDownLatch(1)
        var code = -1
        main {
            PixelCopy.request(
                window,
                bitmap,
                {
                    code = it
                    latch.countDown()
                },
                Handler(Looper.getMainLooper()),
            )
        }
        check(
            latch.await(5, TimeUnit.SECONDS) && code == PixelCopy.SUCCESS,
            "monitor workspace capture $name",
        )
        val dir =
            File(runner.targetContext.externalCacheDir, "workspace-shortcut-acceptance").apply {
                mkdirs()
            }
        File(dir, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it), "workspace capture $name")
        }
        bitmap.recycle()
    }

    private fun canceledReplacementReleasesResources(
        retry: Boolean = false,
        failCleanup: Boolean = false,
    ) {
        val actual = read { session.primaryBridge()!! }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val proxy =
            java.lang.reflect.Proxy.newProxyInstance(
                IDeskService::class.java.classLoader,
                arrayOf(IDeskService::class.java),
            ) { _, method, args ->
                if (method.name == "stop") {
                    entered.countDown()
                    kotlin.check(release.await(12, TimeUnit.SECONDS)) {
                        "controlled cleanup exceeded deadline"
                    }
                }
                try {
                    val result = method.invoke(actual, *(args ?: emptyArray()))
                    if (method.name == "stop" && failCleanup)
                        throw android.os.RemoteException("controlled old cleanup failure")
                    result
                } catch (error: java.lang.reflect.InvocationTargetException) {
                    throw error.targetException
                } finally {
                    if (method.name == "stop") stopped.countDown()
                }
            } as IDeskService
        val bridge =
            SessionService::class.java.getDeclaredField("bridge").apply { isAccessible = true }
        val expanded = read { session.prefs!!.getBoolean("statusExpanded", false) }
        try {
            main {
                session.prefs!!.edit().putBoolean("statusExpanded", false).apply()
                bridge.set(session, proxy)
                session.workspaceShortcut.request()
            }
            check(entered.await(8, TimeUnit.SECONDS), "replacement reaches controlled cleanup gap")
            phone = startPhone()
            main {
                check(
                    phone.window.decorView
                        .findViewWithTag<TextView>("phone-status-cue")
                        .text
                        .toString()
                        .startsWith("Restoring workspace…"),
                    "active restore remains visible in collapsed status",
                )
                check(
                    session.id < 0 && session.monitorWindow() == null,
                    "controller reopen cannot create a competing restore host",
                )
                if (retry) session.rotate(session.rotation, false)
                else session.workspaceShortcut.clear()
                val wake =
                    SessionService::class
                        .java
                        .getDeclaredField("sessionWakeLock")
                        .apply { isAccessible = true }
                        .get(session) as? PowerManager.WakeLock
                check(wake?.isHeld != true, "Clear releases retained replacement wake lock")
                check(
                    !session.workspaceShortcut.active,
                    "Clear cancels restore sequence in cleanup gap",
                )
            }
            if (retry) {
                main { session.workspaceShortcut.request() }
                await("fresh activation waits for old cleanup barrier") {
                    read {
                        session.workspaceShortcut.active &&
                            session.workspaceShortcut.message.startsWith("Waiting for monitor")
                    }
                }
            }
            release.countDown()
            check(stopped.await(8, TimeUnit.SECONDS), "old cleanup completes after cancellation")
            if (retry && !failCleanup) {
                await("fresh activation resumes when canceled cleanup releases barrier") {
                    read {
                        !session.workspaceShortcut.active &&
                            session.workspaceShortcut.message == "Workspace restored"
                    }
                }
                verify(read { session.workspaceShortcut.saved!! })
                return
            }
            if (retry)
                await("unconfirmed old cleanup blocks waiting retry") {
                    read {
                        !session.workspaceShortcut.active &&
                            session.workspaceShortcut.message.contains("cleanup is unconfirmed")
                    }
                }
            await("canceled retained audio lease released") {
                read { !session.audioState().pending }
            }
            main {
                check(
                    session.id < 0 && session.monitorWindow() == null,
                    "late cleanup cannot recreate canceled workspace",
                )
            }
        } finally {
            release.countDown()
            main {
                bridge.set(session, actual)
                session.prefs!!.edit().putBoolean("statusExpanded", expanded).apply()
                session.notifyUi()
            }
        }
        main { session.ensureDesktop() }
        stable()
    }

    private fun provisionalClosureRequiresConfirmedStop() {
        val actual = read { session.primaryBridge()!! }
        var warned = 0
        var stopped = 0
        val proxy =
            java.lang.reflect.Proxy.newProxyInstance(
                IDeskService::class.java.classLoader,
                arrayOf(IDeskService::class.java),
            ) { _, method, args ->
                if (method.name == "closeApps") {
                    warned++
                    throw android.os.RemoteException("controlled provisional app closure warning")
                }
                try {
                    val result = method.invoke(actual, *(args ?: emptyArray()))
                    if (method.name == "stop") stopped++
                    result
                } catch (error: java.lang.reflect.InvocationTargetException) {
                    throw error.targetException
                }
            } as IDeskService
        val bridge =
            SessionService::class.java.getDeclaredField("bridge").apply { isAccessible = true }
        val retired = read { session.id }
        try {
            main {
                bridge.set(session, proxy)
                session.workspaceShortcut.request()
            }
            await("provisional closure warning reaches final restore outcome") {
                read { !session.workspaceShortcut.active }
            }
            main {
                check(
                    warned > 0 && stopped > 0,
                    "actual Stop confirms cleanup after provisional closure warning",
                )
                check(
                    session.workspaceShortcut.message == "Workspace restored",
                    "confirmed Stop permits restore after provisional closure warning: ${session.workspaceShortcut.message}",
                )
                check(session.id != retired, "replacement owns a fresh display after final Stop")
            }
            val displays =
                runner.targetContext.getSystemService(
                    android.hardware.display.DisplayManager::class.java
                )
            check(
                displays.getDisplay(retired) == null,
                "retired primary display is absent before accepting replacement",
            )
            verify(read { session.workspaceShortcut.saved!! })
            touchFixture(0)
        } finally {
            main { bridge.set(session, actual) }
        }
    }

    private fun canceledQueuedLaunchCannotOpenApp() {
        main { session.split?.end() }
        stable()
        for (split in listOf(false, true)) {
            if (split) {
                main { session.enableSplit(false) }
                stable()
            }
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val drained = CountDownLatch(1)
            var completed = false
            main {
                session.desktopWork {
                    entered.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }
            check(
                entered.await(3, TimeUnit.SECONDS),
                "controlled session worker blocks saved launch",
            )
            val pane = if (split) 1 else 0
            val display = read { session.split?.displayId(pane) ?: session.id }
            try {
                main {
                    session.launchWorkspaceApp(
                        WorkspaceSnapshot.App(fixture.flattenToString(), "Fixture"),
                        pane,
                    ) {
                        completed = true
                    }
                    session.workspaceShortcut.clear()
                    session.desktopWork { drained.countDown() }
                }
                release.countDown()
                check(drained.await(5, TimeUnit.SECONDS), "canceled launch worker drained")
                await("canceled queued launch releases busy state") { read { !session.busy } }
                check(!completed, "canceled saved launch has no success callback")
                check(
                    DeviceTestSupport.nativeInputTask(runner, display, fixture) < 0,
                    "Clear prevents queued native app launch in ${if (split) "Split" else "Single"}",
                )
            } finally {
                release.countDown()
            }
        }
    }

    private fun pendingOrdinaryLaunchCannotBeSaved(settings: ComponentName) {
        main { session.split?.end() }
        stable()
        launch(fixture)
        val previous = save()
        val actual = read { session.primaryBridge()!! }
        val unknown = java.util.concurrent.atomic.AtomicBoolean(false)
        val proxy =
            java.lang.reflect.Proxy.newProxyInstance(
                IDeskService::class.java.classLoader,
                arrayOf(IDeskService::class.java),
            ) { _, method, args ->
                if (method.name == "launch")
                    throw IllegalStateException("Controlled ordinary launch rejection")
                if (method.name == "workspaceTask" && unknown.get())
                    throw IllegalStateException("Controlled unknown occupancy")
                try {
                    method.invoke(actual, *(args ?: emptyArray()))
                } catch (error: java.lang.reflect.InvocationTargetException) {
                    throw error.targetException
                }
            } as IDeskService
        val field =
            SessionService::class.java.getDeclaredField("bridge").apply { isAccessible = true }
        val firstEntered = CountDownLatch(1)
        val firstRelease = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val secondRelease = CountDownLatch(1)
        val firstDone = CountDownLatch(1)
        val secondDone = CountDownLatch(1)
        try {
            main {
                phone.controllerMenu()?.dismiss()
                field.set(session, proxy)
                session.desktopWork {
                    firstEntered.countDown()
                    firstRelease.await(10, TimeUnit.SECONDS)
                }
            }
            check(
                firstEntered.await(3, TimeUnit.SECONDS),
                "ordinary launch worker deliberately blocked",
            )
            main {
                session.launch(settings, session.rotation, { firstDone.countDown() })
                session.desktopWork {
                    secondEntered.countDown()
                    secondRelease.await(10, TimeUnit.SECONDS)
                }
                session.launch(settings, session.rotation, { secondDone.countDown() })
                check(
                    session.workspaceSnapshot() == null,
                    "overlapping ordinary launches prevent speculative capture",
                )
                session.workspaceShortcut.save()
                check(
                    session.workspaceShortcut.saved == previous,
                    "pending ordinary launch preserves saved workspace",
                )
                check(
                    session.activeComponent?.packageName == fixture.packageName,
                    "requested app is not committed occupancy",
                )
            }
            firstRelease.countDown()
            check(
                firstDone.await(5, TimeUnit.SECONDS),
                "first controlled launch reaches rejection completion",
            )
            check(secondEntered.await(3, TimeUnit.SECONDS), "second ordinary launch remains queued")
            main {
                check(
                    session.workspaceSnapshot() == null,
                    "first completion cannot clear newer ordinary launch guard",
                )
                session.workspaceShortcut.save()
                check(
                    session.workspaceShortcut.saved == previous,
                    "queued second launch cannot overwrite shortcut",
                )
            }
            secondRelease.countDown()
            check(
                secondDone.await(5, TimeUnit.SECONDS),
                "second controlled launch reaches rejection completion",
            )
            await("rejected ordinary launches release capture guard") {
                read { session.workspaceSnapshot() != null }
            }
            check(
                DeviceTestSupport.nativeInputTask(runner, read { session.id }, fixture) >= 0,
                "prior fixture remains native after rejected ordinary launches",
            )
            unknown.set(true)
            main { session.workspaceShortcut.save() }
            await("unknown native capture reports retry") {
                read { !session.workspaceShortcut.busy }
            }
            main {
                check(
                    session.workspaceShortcut.saved == previous,
                    "unknown native capture preserves saved workspace",
                )
                check(
                    session.workspaceShortcut.message.contains("could not be confirmed"),
                    "unknown native capture offers actionable retry",
                )
            }
            unknown.set(false)
            val recovered = save()
            check(
                recovered.first?.component == previous.first?.component,
                "fresh native capture saves actual prior app after failures",
            )
        } finally {
            firstRelease.countDown()
            secondRelease.countDown()
            main { field.set(session, actual) }
        }
    }

    private fun delayedSaveCannotOutliveMutation() {
        for (restart in listOf(false, true)) {
            launch(fixture)
            val previous = save()
            val executor =
                WorkspaceShortcut::class
                    .java
                    .getDeclaredField("worker")
                    .apply { isAccessible = true }
                    .get(session.workspaceShortcut) as java.util.concurrent.ExecutorService
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val confirmed = CountDownLatch(1)
            executor.execute {
                entered.countDown()
                release.await(15, TimeUnit.SECONDS)
            }
            check(entered.await(3, TimeUnit.SECONDS), "eligibility worker deliberately delayed")
            try {
                main {
                    phone.controllerMenu()?.dismiss()
                    session.workspaceShortcut.save()
                    session.desktopWork {
                        Handler(Looper.getMainLooper()).post { confirmed.countDown() }
                    }
                }
                check(
                    confirmed.await(5, TimeUnit.SECONDS),
                    "native capture precedes delayed eligibility",
                )
                if (restart) {
                    main {
                        session.pauseDesktop()
                        session.ensureDesktop()
                    }
                    stable()
                }
                launch(fixture)
                release.countDown()
                await(
                    "obsolete delayed save settles after ${if (restart) "Stop and recreate" else "same-app launch"}"
                ) {
                    read { !session.workspaceShortcut.busy }
                }
                main {
                    check(
                        session.workspaceShortcut.saved == previous,
                        "workspace mutation preserves previous saved record",
                    )
                    check(
                        session.workspaceShortcut.message.contains("Workspace changed"),
                        "obsolete save reports cancellation instead of success",
                    )
                }
            } finally {
                release.countDown()
            }
        }
    }

    private fun rejectedShortcutFeedback() {
        val expanded = read { session.prefs!!.getBoolean("statusExpanded", false) }
        try {
            main {
                phone.controllerMenu()?.dismiss()
                session.prefs!!.edit().putBoolean("statusExpanded", false).apply()
                session.workspaceShortcut.request()
            }
            await("rejected shortcut visible without expanding status") {
                read {
                    phone.window.decorView.findViewWithTag<TextView>("phone-status-cue").text ==
                        "Workspace not restored · Show details"
                }
            }
            main {
                val header = phone.window.decorView.findViewWithTag<View>("phone-status-expand")
                check(
                    header.contentDescription.toString().contains("Workspace not restored"),
                    "collapsed restore result accessible",
                )
                header.performClick()
            }
            await("full shortcut result survives expansion report") {
                read {
                    find(phone.window.decorView) {
                        it is TextView &&
                            it.isShown &&
                            it.text.toString().contains("No workspace saved.")
                    } != null
                }
            }
            main {
                phone.window.decorView.findViewWithTag<View>("phone-status-expand").performClick()
            }
            await("terminal notice expires without recurring refresh extension") {
                read {
                    phone.window.decorView.findViewWithTag<TextView>("phone-status-cue").text ==
                        "Show details"
                }
            }
            main {
                check(
                    session.workspaceShortcut.restoreResult?.startsWith("No workspace saved.") ==
                        true,
                    "notice expiry retains full last result",
                )
            }
        } finally {
            main {
                session.prefs!!.edit().putBoolean("statusExpanded", expanded).apply()
                session.notifyUi()
            }
        }
    }

    private fun fitPreviewPage() {
        main {
            phone.controllerMenu()?.dismiss()
            session.rotate(0, false)
        }
        stable()
        main { session.preview(1920, 1080, 240) }
        await("Fit preview ready for workspace page") {
            read { !session.busy && session.awaitingConfirmation() }
        }
        try {
            openPhone()
            main {
                check(
                    !phoneRoot().findViewWithTag<View>("phone-workspace-save").isEnabled,
                    "phone cannot save unconfirmed Fit preview",
                )
            }
            shot("phone-fit-preview")
            main {
                phone.controllerMenu()?.dismiss()
                shell().show("workspace-shortcut")
            }
            await("Fit monitor workspace page ready") {
                read {
                    session.monitorShellPage() == "workspace-shortcut" &&
                        !session.monitorShellAnimating()
                }
            }
            main {
                check(
                    !session
                        .monitorShellView()!!
                        .findViewWithTag<View>("monitor-workspace-save")
                        .isEnabled,
                    "monitor cannot save unconfirmed Fit preview",
                )
                check(
                    MenuTextAssertions.check(session.monitorShellView()!!) > 0,
                    "Fit workspace text renders",
                )
            }
            shot("monitor-fit-preview", true)
        } finally {
            main {
                shell().close()
                session.confirm(false)
            }
            stable()
        }
    }

    fun run(): String {
        val ready = CountDownLatch(1)
        val binding =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    session = (binder as SessionService.LocalBinder).get()
                    ready.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }
        val application = runner.targetContext.applicationContext as Application
        main { application.registerActivityLifecycleCallbacks(lifecycle) }
        phone = startPhone()
        check(
            runner.targetContext.bindService(
                Intent(runner.targetContext, SessionService::class.java),
                binding,
                Context.BIND_AUTO_CREATE,
            ),
            "bind workspace acceptance",
        )
        check(ready.await(5, TimeUnit.SECONDS), "workspace service connected")
        val prefs =
            runner.targetContext.getSharedPreferences("workspace_shortcut", Context.MODE_PRIVATE)
        val original = read { session.workspaceShortcut.saved }
        val encoded = prefs.getString("snapshot", null)
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.getIntExtra("uid", -1) == runner.context.applicationInfo.uid)
                        state = Bundle(intent.extras!!)
                }
            }
        runner.targetContext.registerReceiver(
            receiver,
            IntentFilter(LaunchParentActivity.ACTION),
            Context.RECEIVER_EXPORTED,
        )
        var failure: Throwable? = null
        try {
            stable()
            main { session.workspaceShortcut.clear() }
            rejectedShortcutFeedback()
            openPhone()
            main {
                check(
                    !phoneRoot().findViewWithTag<View>("phone-workspace-pin").isEnabled,
                    "pin disabled without saved workspace",
                )
            }
            shot("phone-empty")
            val settings =
                runner.targetContext.packageManager
                    .getLaunchIntentForPackage("com.android.settings")!!
                    .component!!
            for (rotation in 0..3) {
                main {
                    phone.controllerMenu()?.dismiss()
                    session.pauseDesktop()
                    session.ensureDesktop()
                }
                stable()
                main { if (session.split != null) session.split!!.end() }
                stable()
                launch(fixture)
                main { session.rotate(rotation, false) }
                stable()
                main {
                    check(
                        session.rotation == rotation,
                        "requested rotation $rotation is active before snapshot",
                    )
                }
                await("fixture metadata stable before capture") {
                    read { session.workspaceSnapshot() != null }
                }
                val single = save()
                if (rotation == 0) {
                    main {
                        phone.controllerMenu()?.dismiss()
                        session.pauseDesktop()
                    }
                    await("cold shortcut starts from stopped desktop") {
                        read { session.id < 0 && !session.busy }
                    }
                }
                activate()
                verify(single, rotation)
                touchFixture(0)
                main { session.enableSplit(rotation % 2 == 1) }
                stable()
                if (read { session.split!!.state().maximized >= 0 }) {
                    main { session.split!!.maximize() }
                    stable()
                }
                launch(settings, 1)
                main { session.split!!.ratio(.6f) }
                stable()
                if (read { session.split!!.state().swapped != (rotation % 2 == 1) }) {
                    main { session.split!!.swap() }
                    stable()
                }
                main { session.split!!.select(0) }
                if (rotation == 3) {
                    main { session.split!!.maximize() }
                    stable()
                }
                touchFixture(0, returnToParent = false)
                await("two-app workspace metadata stable") {
                    read {
                        session.workspaceSnapshot()?.first?.component ==
                            fixture.flattenToString() + "\$Child"
                    }
                }
                val split = save()
                main {
                    check(
                        split.rotation == rotation,
                        "split snapshot retains independently requested rotation $rotation",
                    )
                    check(
                        split.vertical == (rotation % 2 == 1) &&
                            split.swapped == (rotation % 2 == 1) &&
                            kotlin.math.abs(split.ratio - .6f) < .002f &&
                            split.selected == 0 &&
                            split.maximized == if (rotation == 3) 0 else -1,
                        "snapshot matches independently requested split configuration",
                    )
                    check(
                        split.first?.component == fixture.flattenToString(),
                        "private child canonicalizes to fixture launcher",
                    )
                }
                activate()
                verify(split, rotation)
                touchFixture(0)
                openPhone()
                shot("phone-saved-r$rotation")
                main {
                    phone.controllerMenu()?.dismiss()
                    shell().show("settings")
                }
                await("monitor Settings ready") {
                    read {
                        session.monitorShellPage() == "settings" && !session.monitorShellAnimating()
                    }
                }
                main {
                    session
                        .monitorShellView()!!
                        .findViewWithTag<View>("monitor-workspace-shortcut")
                        .performClick()
                }
                await("monitor workspace page ready") {
                    read {
                        session.monitorShellPage() == "workspace-shortcut" &&
                            !session.monitorShellAnimating()
                    }
                }
                main {
                    check(
                        MenuTextAssertions.check(session.monitorShellView()!!) > 0,
                        "monitor workspace text renders r$rotation",
                    )
                }
                shot("monitor-saved-r$rotation", true)
                main { shell().back() }
                await("monitor Up returns Settings") {
                    read {
                        session.monitorShellPage() == "settings" && !session.monitorShellAnimating()
                    }
                }
                main { shell().close() }
            }
            fitPreviewPage()
            main { if (session.split!!.state().maximized >= 0) session.split!!.maximize() }
            stable()
            main {
                session.split!!.select(1)
                session.split!!.closeApp()
            }
            stable()
            await("empty pane observed") { read { session.workspaceSnapshot()?.second == null } }
            val emptiedDisplay = read { session.split!!.displayId(1) }
            await("closed Settings task is natively absent before refill fixture setup") {
                DisplayTasks.parse(
                        DeviceTestSupport.shell(runner, "am stack list"),
                        settings.flattenToString(),
                    )
                    .none { it != null && it.display == emptiedDisplay && it.tasks > 0 }
            }
            val empty = save()
            launch(settings, 1)
            activate()
            verify(empty)
            await("saved empty pane replaces previously occupied pane") {
                read { session.workspaceSnapshot()?.second == null }
            }
            val before = session.id
            val disabled =
                DeviceTestSupport.shell(
                    runner,
                    "run-as dev.dexdroid.test /system/bin/pm disable --user 0 ${fixture.flattenToString()}",
                )
            check(
                runner.targetContext.packageManager.getComponentEnabledSetting(fixture) ==
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                "unavailable-app fixture is actually disabled: $disabled",
            )
            try {
                main { session.workspaceShortcut.request() }
                await("missing saved app rejected") { read { !session.workspaceShortcut.active } }
                main {
                    check(
                        session.workspaceShortcut.message.startsWith("Workspace not changed") &&
                            session.workspaceShortcut.message.contains(
                                "${empty.first!!.label} is unavailable."
                            ) &&
                            session.workspaceShortcut.message.contains(
                                "Reinstall it or update the saved workspace."
                            ) &&
                            !session.workspaceShortcut.message.contains("ComponentInfo"),
                        "unavailable saved app outcome: ${session.workspaceShortcut.message}",
                    )
                }
                check(session.id == before, "rejected preflight retains current display")
            } finally {
                DeviceTestSupport.shell(
                    runner,
                    "run-as dev.dexdroid.test /system/bin/pm enable --user 0 ${fixture.flattenToString()}",
                )
            }
            provisionalClosureRequiresConfirmedStop()
            canceledReplacementReleasesResources(retry = true)
            canceledReplacementReleasesResources(retry = true, failCleanup = true)
            canceledReplacementReleasesResources()
            canceledQueuedLaunchCannotOpenApp()
            pendingOrdinaryLaunchCannotBeSaved(settings)
            delayedSaveCannotOutliveMutation()
            launch(fixture)
            await("fixture ready after retained cleanup cancellation") {
                read { session.workspaceSnapshot() != null }
            }
            save()
            main {
                session.workspaceShortcut.request()
                session.pauseDesktop()
            }
            await("Stop cancels saved restoration and validation") {
                read {
                    !session.workspaceShortcut.active &&
                        !session.workspaceShortcut.busy &&
                        session.id < 0
                }
            }
            main { session.ensureDesktop() }
            stable()
            openPhone()
            clickPhone("phone-workspace-clear")
            main {
                check(
                    session.workspaceShortcut.saved == null,
                    "Clear removes saved snapshot without changing desktop",
                )
            }
            shot("phone-cleared")
            return results.toString()
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try {
                main {
                    session.workspaceShortcut.cancel()
                    prefs
                        .edit()
                        .apply {
                            if (encoded == null) remove("snapshot")
                            else putString("snapshot", encoded)
                        }
                        .apply()
                    WorkspaceShortcut::class
                        .java
                        .getDeclaredField("saved")
                        .apply { isAccessible = true }
                        .set(session.workspaceShortcut, original)
                    phone.controllerMenu()?.dismiss()
                    session.ensureDesktop()
                }
            } catch (cleanup: Throwable) {
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            } finally {
                runner.targetContext.unregisterReceiver(receiver)
                runner.targetContext.unbindService(binding)
                main { application.unregisterActivityLifecycleCallbacks(lifecycle) }
            }
        }
    }
}
