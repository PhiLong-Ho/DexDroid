package dev.dexdroid

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.os.*
import android.view.*
import android.widget.*
import java.util.*
import java.util.concurrent.*
import java.util.function.BooleanSupplier

/** Real external-display and phone-pointer interactions with the monitor search UI. */
class MonitorSearchAcceptance(private val runner: Instrumentation?) {
    private var plannedViewport: DeviceTestSupport.MonitorViewport? = null
    private var contactViewport: DeviceTestSupport.MonitorViewport? = null
    private val results: StringBuilder = StringBuilder()
    private var session: SessionService? = null
    private var phone: Activity? = null
    private var binding: ServiceConnection? = null
    private var bound: Boolean = false
    private var preferences: Map<String?, *>? = null
    private var down: Long = 0

    private fun main(action: Runnable?) {
        runner!!.runOnMainSync(action)
    }

    private fun <T> observe(action: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return action()
        val result = ArrayList<T>(1)
        main({ result.add(action()) })
        return result[0]
    }

    private class FilterOwner(
        val shell: View,
        val editor: EditText,
        val list: ListView,
        val adapter: ListAdapter,
    )

    private fun filterOwner(): FilterOwner = observe {
        val shell = requireNotNull(session!!.monitorShellView())
        val input = requireNotNull(editor())
        val list = requireNotNull(view("monitor-app-list") as ListView?)
        FilterOwner(shell, input, list, requireNotNull(list.adapter))
    }

    private fun awaitFilteredRow(owner: FilterOwner, query: String, label: String) {
        var latest = "not observed"
        var ownerChanged = false
        try {
            await(
                {
                    observe {
                        val same =
                            session!!.monitorShellView() === owner.shell &&
                                editor() === owner.editor &&
                                view("monitor-app-list") === owner.list &&
                                owner.list.adapter === owner.adapter
                        if (!same) ownerChanged = true
                        val row =
                            if (same) owner.list.findViewWithTag<View>("app.dev.dexdroid") else null
                        val visible = Rect()
                        val within = Rect()
                        val rowVisible =
                            row != null &&
                                row.isAttachedToWindow &&
                                row.isShown &&
                                row.width > 0 &&
                                row.height > 0 &&
                                !row.isLayoutRequested &&
                                row.getGlobalVisibleRect(visible) &&
                                owner.list.getGlobalVisibleRect(within) &&
                                within.contains(visible)
                        val queryMatches = same && query.contentEquals(owner.editor.text)
                        val count = if (same) owner.adapter.count else -1
                        latest =
                            "sameOwner=$same; ownerChanged=$ownerChanged; queryMatches=$queryMatches; count=$count; rowVisible=$rowVisible"
                        same &&
                            !ownerChanged &&
                            queryMatches &&
                            count == 1 &&
                            rowVisible &&
                            owner.list.isAttachedToWindow &&
                            !owner.list.isLayoutRequested
                    }
                },
                label,
            )
        } catch (failure: Throwable) {
            try {
                val dir =
                    java.io.File(
                        requireNotNull(runner!!.targetContext.externalCacheDir),
                        "keyboard-readiness",
                    )
                if (!dir.isDirectory && !dir.mkdirs())
                    throw java.io.IOException("Cannot create keyboard evidence folder")
                java.io
                    .File(dir, "last-failed-sample.txt")
                    .writeText(latest.take(1024), Charsets.UTF_8)
            } catch (capture: Throwable) {
                failure.addSuppressed(capture)
            }
            throw failure
        }
    }

    private fun check(ok: Boolean, label: String?) {
        if (!ok) throw AssertionError(label)
        results.append("PASS ").append(label).append('\n')
    }

    private fun shellMargins(): IntArray {
        val pixels = IntArray(4)
        main({
            val root = session!!.monitorShellView()
            val bitmap =
                Bitmap.createBitmap(root!!.getWidth(), root!!.getHeight(), Bitmap.Config.ARGB_8888)
            root!!.draw(Canvas(bitmap))
            val x = Math.max(1, root!!.getWidth() / 100)
            val y = Math.max(1, root!!.getHeight() / 100)
            pixels[0] = bitmap.getPixel(x, y)
            pixels[1] = bitmap.getPixel(root!!.getWidth() - x - 1, y)
            pixels[2] = bitmap.getPixel(x, root!!.getHeight() - y - 1)
            pixels[3] = bitmap.getPixel(root!!.getWidth() - x - 1, root!!.getHeight() - y - 1)
            bitmap.recycle()
        })
        return pixels
    }

    @Throws(Exception::class)
    private fun await(predicate: BooleanSupplier, label: String?) {
        val end = SystemClock.uptimeMillis() + 10000
        while (!predicate.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(50)
        check(predicate.getAsBoolean(), label)
    }

    private fun restore() {
        if (preferences == null || session == null) return
        val edit = session!!.prefs!!.edit()!!.clear()
        for (entry in preferences!!.entries) {
            val value = entry!!.value
            val key = entry!!.key
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

    private fun editor(): EditText? {
        return view("monitor-app-search") as EditText?
    }

    private fun keyboardVisible(): Boolean {
        val keyboard = view("monitor-search-keyboard")
        return keyboard != null && keyboard!!.isShown()
    }

    @Throws(Exception::class)
    private fun page(page: String?) {
        await(
            { page == session!!.monitorShellPage() && !session!!.monitorShellAnimating() },
            "search UI page settled: " + page!!,
        )
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
            // Display discovery clears/replaces external on the main thread. Read
            // its completed state there rather than racing that transient update.
            val display = intArrayOf(-1)
            main({
                check(session!!.external != null, "physical monitor remains connected")
                display[0] = contactViewport!!.externalDisplay
                if (action == MotionEvent.ACTION_DOWN) {
                    val host = session!!.monitorInputView()
                    results
                        .append("STATE native DOWN ")
                        .append(x)
                        .append(',')
                        .append(y)
                        .append(" host=")
                        .append(host!!.getWidth())
                        .append('x')
                        .append(host!!.getHeight())
                        .append(" logical=")
                        .append(session!!.width())
                        .append('x')
                        .append(session!!.height())
                        .append(" display=")
                        .append(display!![0])
                        .append('\n')
                }
            })
            check(
                DeviceTestSupport.injectOnDisplay(event, display!![0]),
                "search physical monitor event " + action,
            )
        } finally {
            event!!.recycle()
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                contactViewport = null
        }
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

    @Throws(Exception::class)
    private fun tap(x: Float, y: Float) {
        event(MotionEvent.ACTION_DOWN, x, y)
        Thread.sleep(50) // Model a finite physical contact, not a zero-duration injected tap.
        event(MotionEvent.ACTION_UP, x, y)
        Thread.sleep(35)
    }

    @Throws(Exception::class)
    private fun swipe(x: Float, y: Float, endX: Float, endY: Float) {
        val capturedViewport = viewport()
        val start = capturedViewport.mapVirtualToScreen(x, y)
        val end = capturedViewport.mapVirtualToScreen(endX, endY)
        DeviceTestSupport.usableMonitorSwipe(runner, session, start, end)
        capturedViewport.verify(runner, session)
        plannedViewport = capturedViewport
        event(0, start[0], start[1])
        Thread.sleep(30)
        event(2, end[0], end[1])
        event(1, end[0], end[1])
        Thread.sleep(100)
    }

    private fun bounds(tag: String?): Rect {
        val rect = Rect()
        main({
            val target = view(tag)
            check(
                (target != null &&
                    target!!.isShown() &&
                    target!!.getGlobalVisibleRect(rect) &&
                    !rect.isEmpty()),
                "search control visible: " + tag!!,
            )
        })
        return rect
    }

    private fun fullBounds(tag: String?): Rect {
        val result = Rect()
        main({
            val target = view(tag)
            check(
                (target != null &&
                    target!!.isShown() &&
                    target!!.getWidth() > 0 &&
                    target!!.getHeight() > 0),
                "search layout control present: " + tag!!,
            )
            val matrix = Matrix()
            target!!.transformMatrixToGlobal(matrix)
            val full = RectF(0f, 0f, target!!.getWidth().toFloat(), target!!.getHeight().toFloat())
            matrix.mapRect(full)
            full.roundOut(result)
        })
        return result
    }

    // Rotation/scaling and roundOut can share a one-pixel boundary without a real
    // overlap. Larger intersections reveal obscured layout rather than rounding.
    private fun overlaps(a: Rect?, b: Rect?): Boolean {
        val intersection = Rect(a)
        return intersection.intersect(b!!) && intersection.width() > 1 && intersection.height() > 1
    }

    @Throws(Exception::class)
    private fun click(tag: String?) {
        await(
            {
                (!session!!.monitorShellAnimating() &&
                    !session!!.monitorShellView()!!.isLayoutRequested() &&
                    view(tag) != null &&
                    !view(tag)!!.isLayoutRequested())
            },
            "search layout settled before measuring " + tag!!,
        )
        val rect = bounds(tag)
        tap(rect.exactCenterX(), rect.exactCenterY())
    }

    @Throws(Exception::class)
    private fun pointerClick(tag: String?) {
        main({
            val target = view(tag)
            check(target != null && target!!.isShown(), "phone pointer control visible: " + tag!!)
            var x = 0f
            var y = 0f
            var current = target
            while (current !== session!!.monitorShellView()) {
                x += current!!.getLeft().toFloat()
                y += current!!.getTop().toFloat()
                val parent = current!!.getParent() as View?
                x -= parent!!.getScrollX().toFloat()
                y -= parent!!.getScrollY().toFloat()
                current = parent
            }
            session!!.cx = x + target!!.getWidth() / 2f
            session!!.cy = y + target!!.getHeight() / 2f
            session!!.click()
        })
        Thread.sleep(50)
    }

    private fun keyTag(value: Char): String {
        return ("monitor-key-" +
            (if (value == ' ') "space"
            else if (value == '.') "period"
            else if (value == '-') "hyphen"
            else if (value == '_') "underscore" else (value).toString()))
    }

    @Throws(Exception::class)
    private fun type(text: String) {
        for (value in text.toCharArray()) {
            val expected = arrayOfNulls<String?>(1)
            main({
                val input = editor()
                val start = Math.min(input!!.getSelectionStart(), input!!.getSelectionEnd())
                val end = Math.max(input!!.getSelectionStart(), input!!.getSelectionEnd())
                val letter =
                    if (value == ' ') " "
                    else (view(keyTag(value)) as android.widget.Button).getText()!!.toString()
                expected[0] =
                    (input!!.getText()!!.subSequence(0, start).toString() +
                        letter +
                        input!!.getText()!!.subSequence(end, input!!.length()))
            })
            click(keyTag(value))
            text(expected[0], "physical key updates editor " + value)
        }
    }

    @Throws(Exception::class)
    private fun text(expected: String?, label: String?) {
        await(
            { observe { editor() != null && expected!!.contentEquals(editor()!!.getText()!!) } },
            label + ": " + expected,
        )
    }

    private fun count(): Int {
        return observe {
            val list = view("monitor-app-list") as ListView?
            if (list == null) -1 else list.getAdapter()!!.getCount()
        }
    }

    @Throws(Exception::class)
    private fun home() {
        val d = session!!.dpi / 160f
        swipe(
            session!!.width() * .6f,
            session!!.height() - 4 * d,
            session!!.width() * .6f,
            session!!.height() - 100 * d,
        )
        page("home")
    }

    @Throws(Exception::class)
    private fun controls() {
        val d = session!!.dpi / 160f
        swipe(session!!.width() * .6f, 4 * d, session!!.width() * .6f, 100 * d)
        page("controls")
    }

    @Throws(Exception::class)
    private fun startSearch(): EditText? {
        home()
        await({ count() > 1 }, "asynchronous app catalog ready before keyboard filtering")
        main({
            check(
                editor() != null && !editor()!!.hasFocus(),
                "opening launcher does not focus search",
            )
            check(!keyboardVisible(), "opening launcher does not summon keyboard")
            check(editor()!!.length() == 0, "reopened launcher starts with an empty query")
        })
        click("monitor-app-search")
        await(
            { keyboardVisible() && editor()!!.hasFocus() },
            "real search tap focuses editor and shows keyboard",
        )
        return editor()
    }

    private fun noInput(old: EditText?, label: String?) {
        main({
            check(!keyboardVisible(), label!! + " hides keyboard")
            check(!old!!.hasFocus(), label!! + " releases old editor focus")
        })
    }

    @Throws(Exception::class)
    private fun close(old: EditText?, label: String?) {
        click("monitor-close")
        page("closed")
        noInput(old, label)
    }

    @Throws(Exception::class)
    private fun ready() {
        await({ session!!.id >= 0 && !session!!.busy }, "search desktop ready")
        Thread.sleep(300)
    }

    @Throws(Exception::class)
    private fun key(code: Int, modifiers: Int = 0) {
        val keyViewport = viewport()
        keyViewport.verify(runner, session)
        val now = SystemClock.uptimeMillis()
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val event =
                KeyEvent(
                    now,
                    SystemClock.uptimeMillis(),
                    action,
                    code,
                    0,
                    modifiers,
                    KeyCharacterMap.VIRTUAL_KEYBOARD,
                    0,
                    0,
                    InputDevice.SOURCE_KEYBOARD,
                )
            check(
                DeviceTestSupport.injectOnDisplay(event, keyViewport.externalDisplay),
                "monitor hardware key " + KeyEvent.keyCodeToString(code) + " " + action,
            )
        }
        Thread.sleep(100)
    }

    @Throws(Exception::class)
    private fun shiftArrow() {
        val now = SystemClock.uptimeMillis()
        val shift = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        check(
            DeviceTestSupport.injectOnDisplay(
                KeyEvent(
                    now,
                    now,
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_SHIFT_LEFT,
                    0,
                    shift,
                    KeyCharacterMap.VIRTUAL_KEYBOARD,
                    0,
                    0,
                    InputDevice.SOURCE_KEYBOARD,
                ),
                session!!.external!!.getDisplayId(),
            ),
            "hardware Shift held before selection arrow",
        )
        try {
            key(KeyEvent.KEYCODE_DPAD_RIGHT, shift)
        } finally {
            check(
                DeviceTestSupport.injectOnDisplay(
                    KeyEvent(
                        now,
                        SystemClock.uptimeMillis(),
                        KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_SHIFT_LEFT,
                        0,
                        0,
                        KeyCharacterMap.VIRTUAL_KEYBOARD,
                        0,
                        0,
                        InputDevice.SOURCE_KEYBOARD,
                    ),
                    session!!.external!!.getDisplayId(),
                ),
                "hardware Shift released after selection arrow",
            )
        }
    }

    @Throws(Exception::class)
    private fun layout() {
        val search = fullBounds("monitor-app-search")
        val close = fullBounds("monitor-close")
        val list = fullBounds("monitor-app-list")
        val keyboard = fullBounds("monitor-search-keyboard")
        check(
            (!overlaps(search, keyboard) &&
                !overlaps(close, keyboard) &&
                !overlaps(list, keyboard)),
            ("keyboard leaves search, Close and results unobscured at rotation " +
                session!!.rotation),
        )
        val capturedViewport = viewport()
        val screen = capturedViewport.screenBounds()
        check(
            (screen.contains(keyboard) &&
                screen.contains(search) &&
                screen.contains(close) &&
                screen.contains(list)),
            "full search UI stays inside physical monitor at rotation " + session!!.rotation,
        )
        for (name in
            arrayOf<String?>(
                "q",
                "p",
                "a",
                "hyphen",
                "shift",
                "delete",
                "period",
                "underscore",
                "space",
                "clear",
                "hide",
                "1",
                "0",
            )) bounds("monitor-key-" + name!!)
    }

    @Throws(Exception::class)
    fun run(): String {
        try {
            phone = DeviceTestSupport.startPhone(runner!!)
            val connected = CountDownLatch(1)
            binding =
                object : ServiceConnection {
                    public override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        session = (binder as SessionService.LocalBinder).get()
                        connected.countDown()
                    }

                    public override fun onServiceDisconnected(name: ComponentName?) {}
                }
            bound =
                runner!!
                    .getTargetContext()!!
                    .bindService(
                        Intent(runner!!.getTargetContext(), SessionService::class.java),
                        binding!!,
                        Context.BIND_AUTO_CREATE,
                    )
            check(connected.await(5, TimeUnit.SECONDS), "search controller bound")
            ready()
            preferences = HashMap<String?, Any?>(session!!.prefs!!.getAll()!!)
            main({
                session!!.setMonitorNavigationEnabled(true)
                session!!.setMonitorTouchEnabled(true)
            })
            val test = ComponentName(runner!!.getTargetContext()!!, InputTestActivity::class.java!!)
            for (rotation in 0..3) {
                val selected = rotation
                main({ session!!.launch(test, selected) })
                await(
                    { !session!!.busy && session!!.rotation == selected },
                    "search rotation " + rotation,
                )
                Thread.sleep(300)
                val old = startSearch()
                val underlying = InputTestActivity.touchEvents
                layout()
                val all = count()
                check(all > 1, "unfiltered launcher contains multiple apps")
                val filterOwner = filterOwner()
                type("input test")
                text("input test", "physical keys enter letters and a space")
                awaitFilteredRow(filterOwner, "input test", "typed query filters to input test")
                check(observe { view("app.dev.dexdroid") != null }, "filtered app row is rendered")
                click("monitor-key-delete")
                text("input tes", "Delete removes one character")
                click("monitor-key-clear")
                text("", "Clear empties the query")
                await({ count() == all }, "Clear restores all installed apps")
                type("zzzxq")
                text("zzzxq", "no-match query is entered through keys")
                await({ count() == 0 }, "no-match search displays no rows")
                click("monitor-key-clear")
                click("monitor-key-shift")
                click("monitor-key-i")
                type("nput test")
                text("Input test", "Shift capitalizes one letter")
                await({ count() == 1 }, "case-insensitive search recovers filtered app")
                click("monitor-key-clear")
                type("a9.-_")
                text("a9.-_", "numbers and package punctuation are editable")
                click("monitor-key-hide")
                await({ !keyboardVisible() }, "Hide dismisses the search keyboard")
                text("a9.-_", "Hide preserves the query")
                click("monitor-app-search")
                await(
                    BooleanSupplier({ this.keyboardVisible() }),
                    "retapping search restores keyboard",
                )
                click("monitor-key-clear")
                type("input test")
                check(
                    InputTestActivity.touchEvents == underlying,
                    "search and keyboard touches never click underlying app",
                )
                click("app.dev.dexdroid")
                page("closed")
                noInput(old, "app selection")
                ready()
                check(
                    session!!.activePackage == "dev.dexdroid",
                    "filtered app selection retains input test target",
                )
            }
            main({ session!!.rotate(0, false) })
            await({ !session!!.busy && session!!.rotation == 0 }, "dismissal tests landscape")
            Thread.sleep(300)
            var old = startSearch()
            type("abc")
            text("abc", "selection-edit fixture is typed through physical keys")
            // Cursor/range positions are fixtures for keyboard editing behavior; these
            // assertions do not claim to exercise long-press selection or ActionMode UI.
            main({ editor()!!.setSelection(1, 2) })
            click("monitor-key-delete")
            text("ac", "Delete removes the selected range")
            main({ editor()!!.setSelection(1) })
            click("monitor-key-z")
            text("azc", "character inserts at the existing cursor")
            main({ editor()!!.setSelection(0, 2) })
            click("monitor-key-b")
            text("bc", "character replaces a selected range")
            key(KeyEvent.KEYCODE_DEL)
            text("c", "hardware Delete edits at the insertion cursor")
            key(KeyEvent.KEYCODE_X)
            text("xc", "hardware text respects the existing cursor")
            key(KeyEvent.KEYCODE_DEL)
            text("c", "hardware Delete removes the typed character")
            close(old, "selection-edit Close")
            old = startSearch()
            close(old, "Close icon")
            old = startSearch()
            var underlying = InputTestActivity.touchEvents
            val outside = physical(4 * session!!.dpi / 160f, session!!.height() * .5f)
            DeviceTestSupport.usableMonitorOutside(runner, session, outside)
            tap(outside[0], outside[1])
            page("closed")
            noInput(old, "outside tap")
            check(
                InputTestActivity.touchEvents == underlying,
                "keyboard outside dismissal has no click-through",
            )
            old = startSearch()
            var back = InputTestActivity.backEvents
            val d = session!!.dpi / 160f
            swipe(
                session!!.width() - 4 * d,
                session!!.height() * .4f,
                session!!.width() - 100 * d,
                session!!.height() * .4f,
            )
            page("home")
            check(!keyboardVisible(), "first edge Back hides keys without closing Apps")
            swipe(
                session!!.width() - 4 * d,
                session!!.height() * .4f,
                session!!.width() - 100 * d,
                session!!.height() * .4f,
            )
            page("closed")
            noInput(old, "edge Back")
            check(
                InputTestActivity.backEvents == back,
                "edge Back closes launcher without reaching app",
            )
            old = startSearch()
            val preImeBacks = InputTestActivity.backEvents
            val preIme = old
            main({
                val now = SystemClock.uptimeMillis()
                check(
                    preIme!!.dispatchKeyEventPreIme(
                        KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0)
                    ),
                    "search consumes pre-IME Back down",
                )
                check(
                    preIme!!.dispatchKeyEventPreIme(
                        KeyEvent(
                            now,
                            now,
                            KeyEvent.ACTION_UP,
                            KeyEvent.KEYCODE_BACK,
                            0,
                            0,
                            -1,
                            0,
                            KeyEvent.FLAG_CANCELED,
                            InputDevice.SOURCE_KEYBOARD,
                        )
                    ),
                    "search consumes cancelled pre-IME Back release",
                )
                check(
                    session!!.monitorShellPage() == "home" && keyboardVisible(),
                    "cancelled pre-IME Back leaves search open",
                )
                check(
                    preIme!!.dispatchKeyEventPreIme(
                        KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0)
                    ),
                    "search consumes next pre-IME Back down",
                )
                check(
                    preIme!!.dispatchKeyEventPreIme(
                        KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0)
                    ),
                    "search handles valid pre-IME Back release",
                )
            })
            page("home")
            check(!keyboardVisible(), "first pre-IME Back hides keys without closing Apps")
            key(KeyEvent.KEYCODE_BACK)
            page("closed")
            noInput(old, "pre-IME Back")
            check(
                InputTestActivity.backEvents == preImeBacks,
                "pre-IME Back does not reach underlying app",
            )
            old = startSearch()
            key(KeyEvent.KEYCODE_X)
            text("x", "hardware typing reaches focused monitor search")
            key(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
            await(
                { editor()!!.getSelectionStart() == 0 && editor()!!.getSelectionEnd() == 1 },
                "hardware Ctrl+A selects search text",
            )
            key(KeyEvent.KEYCODE_Y)
            text("y", "hardware text replaces selected query")
            key(KeyEvent.KEYCODE_DEL)
            text("", "hardware Delete edits query")
            key(KeyEvent.KEYCODE_X)
            key(KeyEvent.KEYCODE_DPAD_LEFT)
            shiftArrow()
            await(
                {
                    (Math.min(editor()!!.getSelectionStart(), editor()!!.getSelectionEnd()) == 0 &&
                        (Math.max(editor()!!.getSelectionStart(), editor()!!.getSelectionEnd()) ==
                            1))
                },
                "hardware Shift+arrow selects query range",
            )
            main({
                val node = editor()!!.createAccessibilityNodeInfo()
                check(
                    node!!.isEditable() && "x".contentEquals(node!!.getText()!!),
                    "local search remains an accessible editable text node",
                )
                val selection = Bundle()
                selection.putInt(
                    android.view.accessibility.AccessibilityNodeInfo
                        .ACTION_ARGUMENT_SELECTION_START_INT,
                    1,
                )
                selection.putInt(
                    android.view.accessibility.AccessibilityNodeInfo
                        .ACTION_ARGUMENT_SELECTION_END_INT,
                    1,
                )
                check(
                    editor()!!.performAccessibilityAction(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_SELECTION,
                        selection,
                    ),
                    "accessible text selection remains usable",
                )
                check(
                    (editor()!!.getSelectionStart() == 1 && editor()!!.getSelectionEnd() == 1),
                    "accessible action changes the selection",
                )
                selection.putInt(
                    android.view.accessibility.AccessibilityNodeInfo
                        .ACTION_ARGUMENT_SELECTION_START_INT,
                    0,
                )
                check(
                    editor()!!.performAccessibilityAction(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_SELECTION,
                        selection,
                    ),
                    "accessible action selects the query for replacement",
                )
                check(
                    (editor()!!.onCreateInputConnection(android.view.inputmethod.EditorInfo()) ==
                        null),
                    "local search supplies no editable system IME connection",
                )
            })
            key(KeyEvent.KEYCODE_X)
            text("x", "hardware typing retains accessible selection replacement")
            Thread.sleep(500)
            main({
                val monitor = session!!.monitorShellView()!!.getRootWindowInsets()
                val handset = phone!!.getWindow()!!.getDecorView().getRootWindowInsets()
                check(
                    (monitor != null &&
                        handset != null &&
                        !monitor!!.isVisible(WindowInsets.Type.ime()) &&
                        !handset!!.isVisible(WindowInsets.Type.ime())),
                    "hardware search typing never summons system IME on either screen before Back",
                )
            })
            val searchMargins = shellMargins()
            val running = session!!.id
            back = InputTestActivity.backEvents
            key(KeyEvent.KEYCODE_BACK)
            page("home")
            check(!keyboardVisible(), "first hardware Back hides keys without closing Apps")
            text("x", "hiding local keys preserves hardware search query")
            await(
                {
                    val root = session!!.monitorShellView()
                    val focused = if (root == null) null else root!!.findFocus()
                    focused != null && !(focused is EditText)
                },
                "hidden local keyboard leaves focus on a non-editor",
            )
            await(
                {
                    val monitor = session!!.monitorShellView()!!.getRootWindowInsets()
                    val handset = phone!!.getWindow()!!.getDecorView().getRootWindowInsets()
                    (monitor != null &&
                        handset != null &&
                        !monitor!!.isVisible(WindowInsets.Type.ime()) &&
                        !handset!!.isVisible(WindowInsets.Type.ime()))
                },
                "first hardware Back leaves system IME hidden on both screens",
            )
            check(
                Arrays.equals(searchMargins, shellMargins()),
                "hiding local keys preserves menu scrim pixels without focus tint",
            )
            key(KeyEvent.KEYCODE_BACK)
            page("closed")
            for (pixel in shellMargins()) check(
                Color.alpha(pixel) == 0,
                "closed occupied shell leaves desktop transparent after Back",
            )
            noInput(old, "hardware Back")
            check(
                session!!.id == running && session!!.sessionAwake(),
                "hardware Back keeps monitor session running",
            )
            check(
                InputTestActivity.backEvents == back,
                "hardware Back closes menu without app Back",
            )
            key(KeyEvent.KEYCODE_BACK)
            val expectedBack = back + 1
            await(
                { InputTestActivity.backEvents == expectedBack },
                "closed-monitor hardware Back reaches app exactly once",
            )
            check(session!!.id == running, "closed-monitor hardware Back preserves desktop")
            old = startSearch()
            controls()
            noInput(old, "opening Controls")
            click("monitor-settings")
            page("settings")
            check(!keyboardVisible(), "Settings contains no stale search keyboard")
            click("monitor-close")
            page("closed")
            old = startSearch()
            type("input test")
            val held = bounds("app.dev.dexdroid")
            event(0, held.exactCenterX(), held.exactCenterY())
            Thread.sleep(650)
            event(1, held.exactCenterX(), held.exactCenterY())
            page("app-rotation")
            noInput(old, "held-app rotation submenu")
            click("monitor-once-0")
            page("closed")
            ready()
            home()
            pointerClick("monitor-app-search")
            await(
                { keyboardVisible() && editor()!!.hasFocus() },
                "phone pointer opens monitor search keyboard",
            )
            old = editor()
            underlying = InputTestActivity.touchEvents
            for (value in "input test".toCharArray()) pointerClick(keyTag(value))
            text("input test", "phone pointer types monitor search")
            await({ count() == 1 }, "phone pointer typing filters installed apps")
            check(session!!.pointerVisible, "phone pointer typing displays its cursor")
            check(
                InputTestActivity.touchEvents == underlying,
                "phone pointer search has no underlying clicks",
            )
            pointerClick("monitor-close")
            page("closed")
            noInput(old, "phone pointer Close")
            old = startSearch()
            main({ session!!.rotate(2, false) })
            await(
                { !session!!.busy && session!!.rotation == 2 },
                "same-size 180-degree reconfiguration ready",
            )
            Thread.sleep(300)
            noInput(old, "same-size rotation reconfiguration")
            page("home")
            check(editor() !== old, "same-size rotation replaces stale search editor")
            check(editor()!!.length() == 0, "same-size rotation clears stale query")
            click("monitor-app-search")
            await(
                BooleanSupplier({ this.keyboardVisible() }),
                "search works after same-size reconfiguration",
            )
            layout()
            old = editor()
            close(old, "reconfigured Close")
            main({ session!!.rotate(0, false) })
            await({ !session!!.busy && session!!.rotation == 0 }, "Fit search landscape")
            main({ session!!.preview(1920, 1080, 240) })
            await(
                { !session!!.busy && session!!.baseH == 1080 && session!!.dpi == 240 },
                "Fit 240-DPI search preview ready",
            )
            Thread.sleep(300)
            old = startSearch()
            layout()
            type("input test")
            text("input test", "Fit/DPI keyboard typing")
            await({ count() == 1 }, "Fit/DPI search filters apps")
            tap(960f, 45f)
            page("closed")
            noInput(old, "Fit-bar outside dismissal")
            main({ session!!.confirm(false) })
            ready()
            main({ session!!.preview(1280, 800, 160) })
            await(
                { !session!!.busy && session!!.baseW == 1280 && session!!.dpi == 160 },
                "160-DPI search preview ready",
            )
            Thread.sleep(300)
            old = startSearch()
            layout()
            type("input test")
            text("input test", "lower-resolution keyboard typing")
            close(old, "lower-resolution Close")
            main({ session!!.confirm(false) })
            ready()
            main({ phone!!.finish() })
            Thread.sleep(300)
            old = startSearch()
            type("input test")
            text("input test", "background monitor keyboard types without phone controller")
            await({ count() == 1 }, "background monitor search filters apps")
            check(session!!.sessionAwake(), "background keyboard preserves active session")
            main(Runnable(session!!::pauseDesktop))
            await(
                { session!!.id < 0 && session!!.monitorShellView() == null },
                "Stop releases active keyboard and monitor shell",
            )
            noInput(old, "Stop")
            restore()
            return results.toString()
        } catch (error: Throwable) {
            val state =
                try {
                    observe {
                        val input = editor()
                        "STATE query=" +
                            (input?.text?.toString() ?: "<no editor>") +
                            " rotation=" +
                            session?.rotation +
                            " page=" +
                            session?.monitorShellPage() +
                            " keyboard=" +
                            keyboardVisible() +
                            " selection=" +
                            (if (input == null) "none"
                            else "${input.selectionStart}:${input.selectionEnd}")
                    }
                } catch (capture: Throwable) {
                    error.addSuppressed(capture)
                    "STATE unavailable"
                }
            throw RuntimeException(
                results.toString() + state + "\nFAIL monitor search acceptance",
                error,
            )
        } finally {
            restore()
            if (bound) runner!!.getTargetContext()!!.unbindService(binding!!)
        }
    }
}
