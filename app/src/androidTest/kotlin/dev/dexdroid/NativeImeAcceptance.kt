package dev.dexdroid

import android.app.Instrumentation
import android.content.*
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual selected IME, a different-UID editor and physical monitor touch; no injected text. */
internal class NativeImeAcceptance(
    private val runner: Instrumentation,
    private val splitOnly: Boolean = false,
) {
    private lateinit var session: SessionService
    private lateinit var phone: MainActivity
    private var pane = 0
    private val results = StringBuilder()
    private val reports = ConcurrentHashMap<String, Bundle>()
    private var keySnapshot = "No keyboard geometry observed"
    private var fixture =
        ComponentName("dev.dexdroid.test", "dev.dexdroid.fixture.NativeImeFixtureActivity")
    private val firstFixture = fixture
    private val otherFixture =
        ComponentName("dev.dexdroid", "dev.dexdroid.NativeImeDebugEditorActivity")

    private fun main(action: () -> Unit) {
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        Handler(Looper.getMainLooper()).post {
            try {
                action()
            } catch (error: Throwable) {
                failure = error
            } finally {
                done.countDown()
            }
        }
        check(done.await(10, TimeUnit.SECONDS)) { "Native IME main callback deadline" }
        failure?.let { throw it }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> read(action: () -> T): T {
        var value: Any? = null
        main { value = action() }
        return value as T
    }

    private fun verify(value: Boolean, message: String) {
        check(value) { message }
        results.append("PASS $message\n")
        runner.sendStatus(0, Bundle().apply { putString("stream", "PROGRESS $message\n") })
    }

    private fun await(message: String, condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 20000
        while (!condition() && SystemClock.uptimeMillis() < until) Thread.sleep(40)
        verify(condition(), message)
    }

    private fun report() = reports[fixture.flattenToString()]

    private fun targetDisplay() = read { session.split?.displayId(pane) ?: session.id }

    private fun targetBounds() = read {
        val box = session.split?.dimensions(pane, session.width(), session.height(), false)
        Rect(0, 0, box?.width ?: session.width(), box?.height ?: session.height())
    }

    private fun stable() =
        await("desktop settled for native IME") {
            read { session.id >= 0 && !session.busy && !session.awaitingConfirmation() }
        }

    private fun contact(x: Float, y: Float, screenCoordinates: Boolean = false) {
        val viewport = DeviceTestSupport.monitorViewport(runner, session)
        val box = read { session.split?.dimensions(pane, session.width(), session.height(), false) }
        val mapped =
            if (screenCoordinates) floatArrayOf(x, y)
            else viewport.mapVirtualToScreen(x + (box?.x ?: 0), y + (box?.y ?: 0))
        runner.sendStatus(
            0,
            Bundle().apply {
                putString(
                    "stream",
                    "PROGRESS contact virtual=($x,$y) physical=${mapped.contentToString()} viewport=$viewport\n",
                )
            },
        )
        val down = SystemClock.uptimeMillis()
        var downEventTime = down
        try {
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                if (action == MotionEvent.ACTION_UP) {
                    val remaining = downEventTime + 80 - SystemClock.uptimeMillis()
                    if (remaining > 0) Thread.sleep(remaining)
                }
                viewport.verify(runner, session)
                val eventTime = SystemClock.uptimeMillis()
                if (action == MotionEvent.ACTION_DOWN) downEventTime = eventTime
                val event =
                    MotionEvent.obtain(
                        down,
                        eventTime,
                        action,
                        1,
                        arrayOf(
                            MotionEvent.PointerProperties().apply {
                                id = 1
                                toolType = MotionEvent.TOOL_TYPE_FINGER
                            }
                        ),
                        arrayOf(
                            MotionEvent.PointerCoords().apply {
                                this.x = mapped[0]
                                this.y = mapped[1]
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
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try {
                    verify(
                        DeviceTestSupport.injectOnDisplay(event, viewport.externalDisplay),
                        "native IME physical contact $action admitted",
                    )
                    runner.sendStatus(
                        0,
                        Bundle().apply {
                            putString(
                                "stream",
                                "PROGRESS contact action=$action event_ms=$eventTime admitted_ms=${SystemClock.uptimeMillis()} down_event_ms=$downEventTime\n",
                            )
                        },
                    )
                } finally {
                    event.recycle()
                }
            }
        } catch (error: Throwable) {
            try {
                DeviceTestSupport.cancelInjectedMonitor(runner)
            } catch (cancel: Throwable) {
                error.addSuppressed(cancel)
            }
            throw error
        }
    }

    private fun nativeKey(label: String): Rect? {
        // IME display migration can leave a stable but stale cached node rectangle.
        if (Build.VERSION.SDK_INT >= 34) check(runner.uiAutomation.clearCache())
        val windows = runner.uiAutomation.windowsOnAllDisplays
        val id = targetDisplay()
        for (index in 0 until windows.size()) {
            if (windows.keyAt(index) != id) continue
            for (window in windows.valueAt(index)) {
                if (window.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
                val root = window.root ?: continue
                var visited = 0
                val matches = mutableListOf<Rect>()
                fun find(node: AccessibilityNodeInfo, depth: Int) {
                    if (++visited > 1000 || depth > 24) return
                    if (
                        node.isVisibleToUser &&
                            node.contentDescription?.toString()?.equals(label, true) == true
                    ) {
                        // Gboard reuses virtual node IDs when moving between displays.
                        // Refresh matching candidates before trusting their label or bounds.
                        if (
                            node.refresh() &&
                                node.isVisibleToUser &&
                                node.contentDescription?.toString()?.equals(label, true) == true
                        ) {
                            val bounds = Rect()
                            node.getBoundsInScreen(bounds)
                            if (!bounds.isEmpty) matches.add(bounds)
                        }
                    }
                    for (child in 0 until node.childCount) {
                        val next = node.getChild(child) ?: continue
                        find(next, depth + 1)
                    }
                }
                find(root, 0)
                val rootBounds = Rect()
                root.getBoundsInScreen(rootBounds)
                val windowBounds = Rect()
                window.getBoundsInScreen(windowBounds)
                keySnapshot =
                    "display=$id target=${targetBounds()} window=$windowBounds root=$rootBounds label=$label matches=$matches"
                // Scoped English-Gboard fixture: compact letter keys, rather than larger
                // word-suggestion targets with a matching accessibility description.
                matches
                    .minByOrNull { it.width() * it.height() }
                    ?.let {
                        return it
                    }
            }
        }
        return null
    }

    private fun shot(name: String, selectedWindow: Window? = null) {
        val window = selectedWindow ?: read { session.monitorWindow()!! }
        val size = read { window.decorView.width to window.decorView.height }
        val bitmap = Bitmap.createBitmap(size.first, size.second, Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        var code = -1
        main {
            PixelCopy.request(
                window,
                bitmap,
                {
                    code = it
                    copied.countDown()
                },
                Handler(Looper.getMainLooper()),
            )
        }
        verify(
            copied.await(5, TimeUnit.SECONDS) && code == PixelCopy.SUCCESS,
            "$name actual monitor capture",
        )
        val folder = File(runner.targetContext.externalCacheDir, "native-ime-acceptance")
        check(folder.isDirectory || folder.mkdirs())
        File(folder, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun launch() {
        reports.remove(fixture.flattenToString())
        var completed = false
        var error: String? = null
        main {
            session.launch(
                fixture,
                session.rotation,
                {
                    error = it
                    completed = true
                },
            )
        }
        await("native editor launch completes") { read { completed } }
        verify(error == null, "native editor placed successfully: ${error ?: "confirmed"}")
        await("separate UID editor reports managed display") {
            report()?.getInt("display") == targetDisplay()
        }
    }

    private fun typeNative(label: String, expected: String, marker: String) {
        var key: Rect? = null
        var sameSince = 0L
        await("selected IME $label key settles inside managed display $marker") {
            val next = nativeKey(label)
            val inside = next != null && targetBounds().contains(next)
            if (!inside || next != key) {
                key = next.takeIf { inside }
                sameSince = SystemClock.uptimeMillis()
                false
            } else SystemClock.uptimeMillis() - sameSince >= 200
        }
        val started = SystemClock.uptimeMillis()
        contact(checkNotNull(key).exactCenterX(), checkNotNull(key).exactCenterY())
        await("real monitor $label key edits exact separate UID app $marker") {
            report()?.getString("text")?.equals(expected, true) == true
        }
        results.append(
            "MEASURE $marker native_key_contact_to_fixture_observation_ms=${SystemClock.uptimeMillis() - started}\n"
        )
        Thread.sleep(350)
        verify(
            report()?.getBoolean("imeVisible") == true,
            "native IME persists after $label contact $marker",
        )
    }

    private fun openEditor(marker: String) {
        await("native editor layout ready $marker") {
            report()?.getFloat("editorX", -1f)?.let { it > 0 } == true
        }
        val state = checkNotNull(report())
        contact(state.getFloat("editorX"), state.getFloat("editorY"))
        await("actual native editor receives monitor contact $marker") {
            (report()?.getInt("contacts") ?: 0) > state.getInt("contacts")
        }
        await("actual native editor gains input focus $marker") {
            report()?.getBoolean("editorFocused") == true
        }
        await("real IME visible in app insets $marker") {
            report()?.getBoolean("imeVisible") == true
        }
    }

    private fun checkTyping(rotation: Int) {
        main { session.rotate(rotation, false) }
        stable()
        launch()
        shot("before-editor-r$rotation")
        openEditor("r$rotation")
        val previousText = report()?.getString("text").orEmpty()
        typeNative("a", previousText + "a", "r$rotation-first")
        typeNative("a", previousText + "aa", "r$rotation-second")
        typeNative("Delete", previousText + "a", "r$rotation-delete")
        DeviceTestSupport.shell(runner, "input keyboard keyevent KEYCODE_B")
        await("global display-unspecified keyboard reaches native editor r$rotation") {
            report()?.getString("text")?.equals(previousText + "ab", true) == true
        }
        shot("native-keyboard-settled-r$rotation")
        main { session.key(KeyEvent.KEYCODE_BACK) }
        await("first Back hides actual IME r$rotation") {
            report()?.getBoolean("imeVisible") == false
        }
        Thread.sleep(350) // Observe late navigation after the asynchronous key/insets callback.
        verify(
            report()?.getBoolean("destroyed") == false,
            "first Back does not navigate app r$rotation",
        )
        main { session.key(KeyEvent.KEYCODE_BACK) }
        await("second Back follows ordinary Activity navigation r$rotation") {
            report()?.getBoolean("destroyed") == true
        }
        await("native editor exit reconciles before next rotation r$rotation") {
            !read { session.hasDesktopApp() } &&
                DisplayTasks.parse(
                        DeviceTestSupport.shell(runner, "am stack list"),
                        fixture.flattenToString(),
                    )
                    .none { it?.display == read { session.id } && it.exactComponent }
        }
    }

    private fun shell(): MonitorShell {
        val monitor =
            SessionService::class
                .java
                .getDeclaredField("monitor")
                .apply { isAccessible = true }
                .get(session)
        return monitor.javaClass.getDeclaredField("shell").get(monitor) as MonitorShell
    }

    private fun tapSetting(view: View, onPhone: Boolean) {
        val rect = Rect()
        main {
            verify(view.getGlobalVisibleRect(rect), "actual Settings touch target visible")
            val origin = IntArray(2)
            view.rootView.getLocationOnScreen(origin)
            rect.offset(origin[0], origin[1])
        }
        if (!onPhone) {
            contact(rect.exactCenterX(), rect.exactCenterY(), true)
            return
        }
        val down = SystemClock.uptimeMillis()
        var active = false
        try {
            for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                if (action == MotionEvent.ACTION_UP)
                    Thread.sleep((down + 80 - SystemClock.uptimeMillis()).coerceAtLeast(0))
                val event =
                    MotionEvent.obtain(
                            down,
                            SystemClock.uptimeMillis(),
                            action,
                            rect.exactCenterX(),
                            rect.exactCenterY(),
                            0,
                        )
                        .apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                try {
                    verify(
                        runner.uiAutomation.injectInputEvent(event, true),
                        "actual phone Settings contact $action admitted",
                    )
                    active = action == MotionEvent.ACTION_DOWN
                } finally {
                    event.recycle()
                }
            }
        } finally {
            if (active) {
                val cancel =
                    MotionEvent.obtain(
                            down,
                            SystemClock.uptimeMillis(),
                            MotionEvent.ACTION_CANCEL,
                            rect.exactCenterX(),
                            rect.exactCenterY(),
                            0,
                        )
                        .apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                try {
                    runner.uiAutomation.injectInputEvent(cancel, true)
                } finally {
                    cancel.recycle()
                }
            }
        }
    }

    private fun menuTrial() {
        val ownedWindow = read { session.monitorWindow()!! }
        val host =
            checkNotNull(ownedWindow.context as? MonitorActivity) { "Activity-host trial required" }
        val task = read { host.taskId }
        val beforeText = reports.mapValues { it.value.getString("text") }
        val started = SystemClock.uptimeMillis()
        main { shell().show("settings") }
        await("monitor Settings restores keyboard focus eligibility") {
            read {
                session.monitorShellPage() == "settings" &&
                    !session.monitorShellAnimating() &&
                    session.monitorWindow()!!.attributes.flags and
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0
            }
        }
        results.append(
            "MEASURE modal_ready_ms=${SystemClock.uptimeMillis() - started} window_focus=${read { ownedWindow.decorView.hasWindowFocus() }}\n"
        )
        verify(
            read { ownedWindow.decorView.hasWindowFocus() },
            "owned monitor window has focus at menu readiness before touch",
        )
        verify(
            read { session.monitorWindow() === ownedWindow && host.taskId == task },
            "menu focus handoff retains owned host and task",
        )
        verify(
            report()?.getBoolean("heldProbeKey") == false,
            "native held key cancelled before any menu contact",
        )
        DeviceTestSupport.shell(runner, "input keyboard keyevent KEYCODE_B")
        Thread.sleep(350)
        verify(
            beforeText.all { (component, text) -> reports[component]?.getString("text") == text },
            "global B does not edit either app behind ready Settings before touch",
        )
        main {
            MainActivity::class
                .java
                .getDeclaredMethod("options")
                .apply { isAccessible = true }
                .invoke(phone)
        }
        await("both Settings native keyboard controls present") {
            read {
                phone
                    .controllerMenu()
                    ?.window
                    ?.decorView
                    ?.findViewWithTag<View>("phone-native-keyboard-switch") != null &&
                    session
                        .monitorShellView()
                        ?.findViewWithTag<View>("monitor-native-keyboard-switch") != null
            }
        }
        val phoneWindow = read { phone.controllerMenu()!!.window!! }
        val phoneSwitch = read {
            phoneWindow.decorView.findViewWithTag<android.widget.Switch>(
                "phone-native-keyboard-switch"
            )
        }
        val monitorSwitch = read {
            session
                .monitorShellView()!!
                .findViewWithTag<android.widget.Switch>("monitor-native-keyboard-switch")
        }
        main {
            for (toggle in listOf(phoneSwitch, monitorSwitch)) {
                val row = toggle.parent as View
                row.requestRectangleOnScreen(Rect(0, 0, row.width, row.height), true)
            }
        }
        Thread.sleep(200)
        verify(
            read { phoneSwitch.isChecked && monitorSwitch.isChecked },
            "both native keyboard switches show current On",
        )
        shot("phone-native-keyboard-settings", phoneWindow)
        shot("monitor-native-keyboard-settings")
        tapSetting(monitorSwitch, false)
        await("monitor touch releases both policies and updates phone") {
            read {
                !monitorSwitch.isChecked &&
                    session.nativeKeyboardResults().all {
                        it.state == NativeImeResult.State.DEFAULT
                    }
            }
        }
        tapSetting(phoneSwitch, true)
        await("phone touch configures both policies and updates monitor") {
            read {
                phoneSwitch.isChecked &&
                    session.nativeKeyboardResults().all {
                        it.state == NativeImeResult.State.CONFIGURED
                    }
            }
        }
        main {
            phone.controllerMenu()!!.dismiss()
            shell().close()
        }
        await("menu closure finishes before native typing resumes") {
            read { session.monitorShellPage() == "closed" && !session.monitorShellAnimating() }
        }
    }

    private fun legacyFallbackTrial(originalDisplay: Int, originalFocusBit: Int) {
        fun unavailable() =
            session.nativeKeyboardResults().let { results ->
                results.isNotEmpty() &&
                    results.all {
                        it.state == NativeImeResult.State.UNAVAILABLE &&
                            it.reason == NativeImeResult.Reason.LEGACY_ANDROID
                    }
            }
        if (read { session.splitEnabled() }) {
            main { session.split!!.end() }
            stable()
        }
        await("Android11 explicitly reports legacy native keyboard unavailability") {
            read { unavailable() }
        }
        verify(
            read { !session.nativeKeyboardPassiveHost() },
            "Android11 does not acquire the native keyboard host lease",
        )
        main {
            shell().show("settings")
            MainActivity::class
                .java
                .getDeclaredMethod("options")
                .apply { isAccessible = true }
                .invoke(phone)
        }
        await("Android11 both Settings keyboard controls ready") {
            read {
                !session.monitorShellAnimating() &&
                    phone
                        .controllerMenu()
                        ?.window
                        ?.decorView
                        ?.findViewWithTag<View>("phone-native-keyboard-switch") != null &&
                    session
                        .monitorShellView()
                        ?.findViewWithTag<View>("monitor-native-keyboard-switch") != null
            }
        }
        val phoneWindow = read { phone.controllerMenu()!!.window!! }
        val phoneSwitch = read {
            phoneWindow.decorView.findViewWithTag<android.widget.Switch>(
                "phone-native-keyboard-switch"
            )
        }
        val monitorSwitch = read {
            session
                .monitorShellView()!!
                .findViewWithTag<android.widget.Switch>("monitor-native-keyboard-switch")
        }
        main {
            for (toggle in listOf(phoneSwitch, monitorSwitch)) {
                val row = toggle.parent as View
                row.requestRectangleOnScreen(Rect(0, 0, row.width, row.height), true)
            }
        }
        await("Android11 unavailable explanation shown on both controllers") {
            read {
                phoneSwitch.isChecked &&
                    monitorSwitch.isChecked &&
                    phoneWindow.decorView
                        .findViewWithTag<android.widget.TextView>("phone-native-keyboard-status")
                        .text
                        .contains("Unavailable on Android 11") &&
                    session
                        .monitorShellView()!!
                        .findViewWithTag<android.widget.TextView>("monitor-native-keyboard-status")
                        .text
                        .contains("Unavailable on Android 11")
            }
        }
        shot("android11-phone-keyboard-unavailable", phoneWindow)
        shot("android11-monitor-keyboard-unavailable")
        tapSetting(monitorSwitch, false)
        await("Android11 monitor Off releases request and updates phone") {
            read {
                !phoneSwitch.isChecked &&
                    session.nativeKeyboardResults().all {
                        it.state == NativeImeResult.State.DEFAULT
                    }
            }
        }
        tapSetting(phoneSwitch, true)
        await("Android11 phone On updates monitor with honest unavailable status") {
            read { monitorSwitch.isChecked && unavailable() }
        }
        verify(
            read {
                session.id == originalDisplay &&
                    session.monitorWindow()!!.attributes.flags and
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == originalFocusBit &&
                    !session.nativeKeyboardPassiveHost()
            },
            "Android11 option preserves desktop identity and host focus eligibility",
        )
        main {
            phone.controllerMenu()!!.dismiss()
            shell().close()
        }
        await("Android11 menus close before app contact") {
            read { !session.monitorShellAnimating() && session.monitorShellPage() == "closed" }
        }
        launch()
        await("Android11 synthetic editor layout ready") {
            (report()?.getFloat("editorX") ?: 0f) > 0f
        }
        val before = checkNotNull(report())
        contact(before.getFloat("editorX"), before.getFloat("editorY"))
        await("Android11 real monitor contact reaches editor while native placement unavailable") {
            (report()?.getInt("contacts") ?: 0) > before.getInt("contacts")
        }
        val windows = runner.uiAutomation.windowsOnAllDisplays
        val imeDisplays =
            (0 until windows.size())
                .filter { index ->
                    windows.valueAt(index).any {
                        it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
                    }
                }
                .map(windows::keyAt)
        results.append(
            "OBSERVE Android11 IME window displays=$imeDisplays, managed=${read { session.id }}; window metadata alone is not rendering proof\n"
        )
        shot("android11-monitor-editor")
        runner.uiAutomation.takeScreenshot()?.let { bitmap ->
            try {
                val folder = File(runner.targetContext.externalCacheDir, "native-ime-acceptance")
                File(folder, "android11-phone-keyboard-routing.png").outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally {
                bitmap.recycle()
            }
        }
        verify(
            read { session.id == originalDisplay && !session.busy && unavailable() },
            "Android11 desktop remains usable with explicit unavailable placement",
        )
    }

    private fun splitTrial() {
        main { session.rotate(0, false) }
        stable()
        main { session.enableSplit(false) }
        await("two native keyboard owners configured") {
            read {
                session.split?.readyForWorkspace() == true &&
                    session.nativeKeyboardResults().size == 2 &&
                    session.nativeKeyboardResults().all {
                        it.state == NativeImeResult.State.CONFIGURED
                    }
            }
        }
        for (next in 0..1) {
            pane = next
            fixture = if (pane == 0) firstFixture else otherFixture
            main { session.split!!.select(pane) }
            launch()
        }
        for (next in listOf(0, 1, 0, 1)) {
            pane = next
            fixture = if (pane == 0) firstFixture else otherFixture
            val other = if (pane == 0) otherFixture else firstFixture
            val otherBefore = reports[other.flattenToString()]?.getString("text")
            openEditor("split-$pane")
            val before = report()?.getString("text").orEmpty()
            typeNative("a", before + "a", "split-$pane-first")
            typeNative("a", before + "aa", "split-$pane-second")
            DeviceTestSupport.shell(runner, "input keyboard keyevent KEYCODE_B")
            await("global keyboard follows native split focus pane $pane") {
                report()?.getString("text")?.equals(before + "aab", true) == true
            }
            verify(
                reports[other.flattenToString()]?.getString("text") == otherBefore,
                "native keyboard does not edit other split pane",
            )
            shot("split-native-keyboard-$pane")
        }
        val oldUps = checkNotNull(report()).getInt("probeKeyUps")
        val oldCancelled = checkNotNull(report()).getInt("probeKeyCancelled")
        runner.uiAutomation
            .executeShellCommand("input keyboard keyevent --duration 5000 KEYCODE_F12")
            .use { heldCommand ->
                await("native held key observed before menu") {
                    report()?.getBoolean("heldProbeKey") == true
                }
                menuTrial()
                java.io.FileInputStream(heldCommand.fileDescriptor).use { it.readBytes() }
            }
        await("global key cancelled on menu focus handoff") {
            report()?.getBoolean("heldProbeKey") == false &&
                report()?.getInt("probeKeyCancelled") == oldCancelled + 1
        }
        verify(
            report()?.getInt("probeKeyUps") == oldUps + 1,
            "late native release does not duplicate cancellation",
        )
        openEditor("split-retap-after-menus")
        typeNative("a", report()?.getString("text").orEmpty() + "a", "split-after-menus")
        val keyBehindMenu = checkNotNull(nativeKey("p"))
        val textsBeforeMenu = reports.mapValues { it.value.getString("text") }
        val contactsBeforeMenu = reports.mapValues { it.value.getInt("contacts") }
        val viewport = DeviceTestSupport.monitorViewport(runner, session)
        val box = read {
            checkNotNull(session.split!!.dimensions(pane, session.width(), session.height(), false))
        }
        val exposed =
            viewport.mapVirtualToScreen(
                keyBehindMenu.exactCenterX() + box.x,
                keyBehindMenu.exactCenterY() + box.y,
            )
        main { shell().show("settings") }
        await("Settings modal settles over native app") {
            read { session.monitorShellPage() == "settings" && !session.monitorShellAnimating() }
        }
        if (report()?.getBoolean("imeVisible") == true)
            verify(
                nativeKey("p") == keyBehindMenu,
                "visible background native key retains its measured bounds",
            )
        else
            results.append(
                "SCOPE Android hid app IME when menu took focus; outside point tests app contact isolation, not an exposed key\n"
            )
        val panelBounds = Rect()
        main {
            val panel = session.monitorShellView()!!.findViewWithTag<View>("monitor-panel")
            verify(panel.getGlobalVisibleRect(panelBounds), "Settings panel bounds visible")
            val origin = IntArray(2)
            panel.rootView.getLocationOnScreen(origin)
            panelBounds.offset(origin[0], origin[1])
        }
        verify(
            !panelBounds.contains(exposed[0].toInt(), exposed[1].toInt()),
            "native key exposed outside modal panel",
        )
        viewport.verify(runner, session)
        contact(exposed[0], exposed[1], true)
        await("outside native-key touch dismisses Settings") {
            read { session.monitorShellPage() == "closed" && !session.monitorShellAnimating() }
        }
        Thread.sleep(350)
        verify(
            textsBeforeMenu.all { (component, text) ->
                reports[component]?.getString("text") == text
            },
            "modal dismissal does not type into either editor",
        )
        verify(
            contactsBeforeMenu.all { (component, count) ->
                reports[component]?.getInt("contacts") == count
            },
            "modal dismissal does not contact either app",
        )
        openEditor("split-retap-after-outside")
        typeNative("a", report()?.getString("text").orEmpty() + "a", "split-after-outside")
        DeviceTestSupport.shell(runner, "input keyboard keyevent KEYCODE_BACK")
        await("global keyboard Back hides active split IME") {
            report()?.getBoolean("imeVisible") == false
        }
        verify(
            report()?.getBoolean("destroyed") == false,
            "global Back does not close split app while hiding IME",
        )
        main { session.split!!.end() }
        await("secondary keyboard owner retired on Single") {
            read {
                session.split == null && !session.busy && session.nativeKeyboardResults().size == 1
            }
        }
        pane = 0
        fixture = firstFixture
    }

    private fun fitTrial() {
        val original = read { Triple(session.baseW, session.baseH, session.dpi) }
        fun apply(width: Int, height: Int, dpi: Int) {
            main { session.preview(width, height, dpi) }
            await("Fit configuration preview ready") {
                read { session.awaitingConfirmation() && !session.busy }
            }
            main { session.confirm(true) }
            stable()
        }
        try {
            apply(1920, 1080, 240)
            checkTyping(0)
            shot("native-keyboard-fit-after-exit")
        } finally {
            apply(original.first, original.second, original.third)
        }
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun run(): String {
        phone = DeviceTestSupport.startPhoneBounded(runner)
        val connected = CountDownLatch(1)
        val binding =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    session = (binder as SessionService.LocalBinder).get()
                    connected.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }
        verify(
            runner.targetContext.bindService(
                Intent(runner.targetContext, SessionService::class.java),
                binding,
                Context.BIND_AUTO_CREATE,
            ),
            "bind native IME acceptance",
        )
        verify(connected.await(5, TimeUnit.SECONDS), "native IME service connected")
        val enabled = read { session.nativeMonitorKeyboardEnabled() }
        val rotation = read { session.rotation }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val component = intent?.getStringExtra("component") ?: return
                    val expectedUid =
                        if (component == otherFixture.flattenToString())
                            runner.targetContext.applicationInfo.uid
                        else runner.context.applicationInfo.uid
                    if (intent.getIntExtra("uid", -1) != expectedUid) return
                    if (
                        component in
                            setOf(firstFixture.flattenToString(), otherFixture.flattenToString())
                    )
                        reports[component] = Bundle(checkNotNull(intent.extras))
                }
            }
        if (Build.VERSION.SDK_INT >= 33)
            runner.targetContext.registerReceiver(
                receiver,
                IntentFilter(dev.dexdroid.fixture.NativeImeFixtureActivity.ACTION),
                Context.RECEIVER_EXPORTED,
            )
        else
            runner.targetContext.registerReceiver(
                receiver,
                IntentFilter(dev.dexdroid.fixture.NativeImeFixtureActivity.ACTION),
            )
        val info = runner.uiAutomation.serviceInfo
        info.flags =
            info.flags or
                android.accessibilityservice.AccessibilityServiceInfo
                    .FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        runner.uiAutomation.serviceInfo = info
        try {
            stable()
            val originalDisplay = read { session.id }
            val originalFocusBit = read {
                session.monitorWindow()!!.attributes.flags and
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            main { session.setNativeMonitorKeyboardEnabled(true) }
            if (Build.VERSION.SDK_INT >= 34 && !read { session.nativeKeyboardFocusMode }) {
                val previousDisplay = read { session.id }
                main {
                    session.stopDesktop()
                    session.ensureDesktop()
                }
                await("explicit diagnostic restart creates native keyboard focus mode") {
                    read {
                        session.id >= 0 &&
                            session.id != previousDisplay &&
                            session.nativeKeyboardFocusMode &&
                            !session.busy
                    }
                }
            }
            await("native IME policy completes independently of desktop") {
                read {
                    session.nativeKeyboardResults().isNotEmpty() &&
                        session.nativeKeyboardResults().none {
                            it.state == NativeImeResult.State.PENDING
                        }
                }
            }
            if (Build.VERSION.SDK_INT == 30) {
                legacyFallbackTrial(originalDisplay, originalFocusBit)
                return results.toString()
            }
            verify(
                read {
                    session.nativeKeyboardResults().all {
                        it.state == NativeImeResult.State.CONFIGURED
                    }
                },
                "native IME policy readback configured",
            )
            if (!splitOnly) {
                for (next in 0..3) checkTyping(next)
                fitTrial()
            }
            splitTrial()
            val typingDisplay = read { session.id }
            main {
                session.stopDesktop()
                session.ensureDesktop()
            }
            await("fresh native-focus desktop isolates child regression from editor fixtures") {
                read { session.id >= 0 && session.id != typingDisplay && !session.busy }
            }
            if (!splitOnly) {
                val children = ChildLaunchAcceptance(runner).run(phone)
                val childEvidence =
                    File(runner.targetContext.externalCacheDir, "native-ime-acceptance")
                check(childEvidence.isDirectory || childEvidence.mkdirs())
                File(childEvidence, "child-results.txt").writeText(children, Charsets.UTF_8)
                results
                    .append("NATIVE KEYBOARD CREATION MODE: NATIVE CHILD/HANDOFF REGRESSION\n")
                    .append(children)
                verify(
                    !children.contains("\nFAIL "),
                    "native keyboard creation mode preserves child navigation, phone handoff and real app exit",
                )
            } else results.append("SCOPE split-only: single/rotation/Fit/child scenarios not run\n")
            main { session.setNativeMonitorKeyboardEnabled(false) }
            await("disabling restores native policy baseline") {
                read {
                    session.nativeKeyboardResults().all {
                        it.state == NativeImeResult.State.DEFAULT
                    }
                }
            }
            verify(
                read { session.id >= 0 && !session.busy },
                "keyboard option does not stop desktop",
            )
            return results.toString()
        } catch (error: Throwable) {
            try {
                val folder = File(runner.targetContext.externalCacheDir, "native-ime-acceptance")
                check(folder.isDirectory || folder.mkdirs())
                File(folder, "fixture-state.txt").writeText(report().toString(), Charsets.UTF_8)
                File(folder, "results-before-failure.txt")
                    .writeText(results.toString(), Charsets.UTF_8)
                File(folder, "keyboard-geometry.txt").writeText(keySnapshot, Charsets.UTF_8)
                File(folder, "diagnostic-context.txt")
                    .writeText(read { session.diagnosticText() }, Charsets.UTF_8)
                for (service in listOf("input_method", "window", "input")) File(
                        folder,
                        "$service.txt",
                    )
                    .writeText(
                        DeviceTestSupport.shell(runner, "dumpsys -t 2 $service").orEmpty(),
                        Charsets.UTF_8,
                    )
                if (read { session.id >= 0 && session.monitorWindow() != null })
                    shot("diagnostic-before-cleanup")
            } catch (diagnostic: Throwable) {
                error.addSuppressed(diagnostic)
            }
            throw error
        } finally {
            main {
                phone.controllerMenu()?.dismiss()
                if (session.monitorShellPage() != "stopped") shell().close()
                session.setNativeMonitorKeyboardEnabled(enabled)
                session.rotate(rotation, false)
            }
            runner.targetContext.unregisterReceiver(receiver)
            runner.targetContext.unbindService(binding)
        }
    }
}
