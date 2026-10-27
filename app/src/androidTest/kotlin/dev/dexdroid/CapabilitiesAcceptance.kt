package dev.dexdroid

import android.app.Instrumentation
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Real shared report views. Captures remain private; no app/input/audio probes are performed. */
internal class CapabilitiesAcceptance(private val runner: Instrumentation) {
    private lateinit var session: SessionService
    private lateinit var phone: MainActivity
    private val results = StringBuilder()
    private var copiedReport: String? = null

    private fun progress(message: String) {
        runner.sendStatus(0, Bundle().apply { putString("stream", "PROGRESS $message\n") })
    }

    private fun verify(ok: Boolean, message: String) {
        if (!ok) throw AssertionError(message)
        results.append("PASS $message\n")
        progress(message)
    }

    private fun main(action: () -> Unit) {
        val done = CountDownLatch(1)
        val active = AtomicBoolean(true)
        var error: Throwable? = null
        Handler(Looper.getMainLooper()).post {
            try {
                if (active.get()) action()
            } catch (failure: Throwable) {
                error = failure
            } finally {
                done.countDown()
            }
        }
        if (!done.await(5, TimeUnit.SECONDS)) {
            active.set(false)
            throw AssertionError("Capabilities main callback exceeded five seconds")
        }
        error?.let { throw it }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> read(action: () -> T): T {
        var value: Any? = null
        main { value = action() }
        return value as T
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20000
        while (true) {
            if (condition()) {
                verify(true, message)
                return
            }
            if (SystemClock.uptimeMillis() >= deadline) throw AssertionError(message)
            Thread.sleep(40)
        }
    }

    private fun find(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                find(root.getChildAt(index), predicate)?.let {
                    return it
                }
            }
        }
        return null
    }

    private fun root(prefix: String): View =
        if (prefix == "phone") phone.controllerMenu()!!.window!!.decorView
        else checkNotNull(session.monitorShellView())

    private fun scroll(prefix: String): ScrollView =
        checkNotNull(find(root(prefix)) { it is ScrollView }) as ScrollView

    private fun reveal(view: View) {
        // Let each native parent account for its own scroll, padding and transformations.
        view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
    }

    private fun click(prefix: String, tag: String) {
        await("$tag laid out and enabled") {
            read {
                root(prefix).findViewWithTag<View>(tag)?.let {
                    it.isShown && it.isEnabled && it.isLaidOut && it.width > 0 && it.height > 0
                } == true
            }
        }
        main { reveal(checkNotNull(root(prefix).findViewWithTag<View>(tag))) }
        frame(prefix)
        await("$tag revealed in viewport after framework scrolling") {
            read { root(prefix).findViewWithTag<View>(tag)?.getGlobalVisibleRect(Rect()) == true }
        }
        main {
            val target = checkNotNull(root(prefix).findViewWithTag<View>(tag))
            verify(target.getGlobalVisibleRect(Rect()), "$tag reachable in viewport")
            verify(target.isClickable && target.performClick(), "$tag native row click handled")
        }
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

    private fun page(prefix: String, name: String) {
        await("$prefix page $name ready") {
            read {
                if (prefix == "monitor")
                    session.monitorShellPage() == name && !session.monitorShellAnimating()
                else
                    root(prefix).findViewWithTag<TextView>("phone-menu-title")?.text.toString() ==
                        name
            }
        }
    }

    private fun openPhone() {
        // Only opening existing Settings uses reflection; the report entry is its real visible row.
        main {
            MainActivity::class
                .java
                .getDeclaredMethod("options")
                .apply { isAccessible = true }
                .invoke(phone)
        }
        page("phone", "Settings")
        main {
            var target =
                checkNotNull(
                    find(root("phone")) {
                        it is TextView && it.text.toString() == "Device capabilities"
                    }
                )
            while (!target.isClickable && target.parent is View) target = target.parent as View
            reveal(target)
        }
        frame("phone")
        main {
            var target =
                checkNotNull(
                    find(root("phone")) {
                        it is TextView && it.text.toString() == "Device capabilities"
                    }
                )
            while (!target.isClickable && target.parent is View) target = target.parent as View
            verify(target.getGlobalVisibleRect(Rect()), "phone Settings report entry visible")
            verify(target.isEnabled && target.performClick(), "phone Settings report entry opens")
        }
        page("phone", "Device capabilities")
    }

    private fun openMonitor() {
        main { shell().show("settings") }
        page("monitor", "settings")
        click("monitor", "monitor-capabilities")
        page("monitor", "capabilities")
    }

    private data class State(
        val display: Int,
        val rotation: Int,
        val split: SplitWorkspace?,
        val layout: String,
        val selectedAudio: String,
        val width: Int,
        val height: Int,
        val dpi: Int,
    )

    private fun state(): State = read {
        State(
            session.id,
            session.rotation,
            session.split,
            session.layoutStatus(),
            session.audioState().selected,
            session.baseW,
            session.baseH,
            session.dpi,
        )
    }

    private fun passive(before: State, label: String) {
        verify(
            state() == before,
            "$label preserves desktop, rotation, split, audio and configuration",
        )
    }

    private fun frame(prefix: String) {
        val done = CountDownLatch(1)
        main { root(prefix).postOnAnimation { root(prefix).postOnAnimation { done.countDown() } } }
        if (!done.await(3, TimeUnit.SECONDS))
            throw AssertionError("$prefix menu frames exceeded deadline")
    }

    private fun bottom(prefix: String) {
        main {
            val viewport = scroll(prefix)
            val smooth = viewport.isSmoothScrollingEnabled
            try {
                // This suite checks reachability/layout, not scroll animation. An unfinished
                // fullScroll animation can otherwise overwrite the following reveal request.
                viewport.isSmoothScrollingEnabled = false
                viewport.fullScroll(View.FOCUS_DOWN)
            } finally {
                viewport.isSmoothScrollingEnabled = smooth
            }
        }
        frame(prefix)
        await("$prefix report scroll reaches bottom") {
            read { !scroll(prefix).canScrollVertically(1) }
        }
    }

    private fun panel(prefix: String, refresh: Boolean = true) {
        await("$prefix report metadata settled") { read { !session.capabilityReportRefreshing() } }
        val names =
            listOf("Shizuku", "Monitor", "Desktop", "Input", "Rotation + split", "Audio selection")
        await("$prefix report views reflect current observations") {
            read {
                session.capabilityReport().items.withIndex().all { (index, item) ->
                    root(prefix)
                        .findViewWithTag<TextView>("$prefix-capability-title-$index")
                        ?.text
                        .toString() == "${item.title} · ${item.verdict.label}" &&
                        root(prefix)
                            .findViewWithTag<TextView>("$prefix-capability-detail-$index")
                            ?.text
                            .toString() == item.detail
                }
            }
        }
        frame(prefix)
        for (index in names.indices) {
            val titleTag = "$prefix-capability-title-$index"
            val detailTag = "$prefix-capability-detail-$index"
            await("$prefix ${names[index]} laid out") {
                read {
                    val title = root(prefix).findViewWithTag<TextView>(titleTag)
                    val detail = root(prefix).findViewWithTag<TextView>(detailTag)
                    title?.layout != null && detail?.layout != null && detail.width > 0
                }
            }
            main {
                val title = root(prefix).findViewWithTag<TextView>(titleTag)
                val detail = root(prefix).findViewWithTag<TextView>(detailTag)
                val item = session.capabilityReport().items[index]
                verify(
                    title.text.toString() == "${names[index]} · ${item.verdict.label}",
                    "$prefix ${names[index]} displays current scoped verdict",
                )
                verify(
                    detail.text.toString() == item.detail && detail.text.isNotBlank(),
                    "$prefix ${names[index]} displays current explanation",
                )
                for (text in listOf(title, detail)) {
                    verify(!text.isClickable, "$prefix ${names[index]} report text is passive")
                    val layout = checkNotNull(text.layout)
                    verify(
                        layout.lineCount <= text.maxLines &&
                            (0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 } &&
                            (0 until layout.lineCount).all {
                                layout.getLineLeft(it) >= -1f &&
                                    layout.getLineRight(it) <= layout.width + 1f
                            } &&
                            layout.height <=
                                text.height - text.totalPaddingTop - text.totalPaddingBottom,
                        "$prefix ${names[index]} text wraps without clipping",
                    )
                }
            }
        }
        main {
            verify(
                MenuTextAssertions.check(root(prefix)) > 0,
                "$prefix report text and glyphs decode",
            )
            for (key in listOf("refresh", "diagnostics")) {
                val action = root(prefix).findViewWithTag<View>("$prefix-capability-$key")
                verify(
                    action.isFocusable &&
                        action.height >= 48 * action.resources.displayMetrics.density &&
                        !action.contentDescription.isNullOrBlank(),
                    "$prefix $key has keyboard focus, accessible name and touch target",
                )
            }
            verify(
                root(prefix)
                    .findViewWithTag<View>("$prefix-capability-feedback")
                    .accessibilityLiveRegion == View.ACCESSIBILITY_LIVE_REGION_POLITE,
                "$prefix refresh feedback announces politely",
            )
        }
        bottom(prefix)
        main {
            verify(
                root(prefix)
                    .findViewWithTag<View>("$prefix-capability-detail-5")
                    .getGlobalVisibleRect(Rect()),
                "$prefix final audio explanation reachable",
            )
        }
        if (refresh) {
            val before = state()
            click(prefix, "$prefix-capability-refresh")
            await("$prefix explicit Refresh completes") {
                read { !session.capabilityReportRefreshing() }
            }
            passive(before, "$prefix Refresh")
        }
    }

    private fun diagnosticsAndBack(prefix: String) {
        click(prefix, "$prefix-capability-diagnostics")
        page(prefix, if (prefix == "phone") "Diagnostic log" else "diagnostics")
        click(prefix, "$prefix-log-copy")
        main {
            val manager =
                runner.targetContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val copy = manager.primaryClip?.getItemAt(0)?.text.toString()
            verify(
                copy == session.diagnosticText(),
                "$prefix Copy captures immutable complete current report",
            )
            verify(
                copy.contains(session.capabilityReport().plainText()),
                "$prefix diagnostic export includes six-item report",
            )
            copiedReport = copy
        }
        click(prefix, if (prefix == "phone") "phone-menu-up" else "monitor-up")
        page(prefix, if (prefix == "phone") "Device capabilities" else "capabilities")
    }

    private fun shot(name: String, prefix: String) {
        frame(prefix)
        val window = read {
            if (prefix == "phone") checkNotNull(phone.controllerMenu()?.window)
            else checkNotNull(session.monitorWindow())
        }
        val size = read { window.decorView.width to window.decorView.height }
        val bitmap = Bitmap.createBitmap(size.first, size.second, Bitmap.Config.ARGB_8888)
        try {
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
                "$name PixelCopy captures actual menu window",
            )
            val directory = File(runner.targetContext.externalCacheDir, "capabilities-acceptance")
            verify(
                directory.isDirectory || directory.mkdirs(),
                "$name private capture directory available",
            )
            File(directory, "$name.png").outputStream().use {
                verify(
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it),
                    "$name private capture saved",
                )
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun stable(message: String) =
        await(message) {
            read {
                session.id >= 0 &&
                    !session.busy &&
                    !session.awaitingConfirmation() &&
                    session.monitorWindow() != null
            }
        }

    fun run(): String {
        progress("capabilities phone acquisition starting")
        phone = DeviceTestSupport.startPhoneBounded(runner)
        progress("capabilities phone acquisition completed")
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
            "bind capabilities acceptance session",
        )
        var original: State? = null
        var clipboard: ClipData? = null
        var clipboardSaved = false
        var primary: Throwable? = null
        try {
            verify(connected.await(5, TimeUnit.SECONDS), "capabilities session connected")
            stable("existing desktop ready within twenty seconds")
            original = state()
            main {
                clipboard =
                    (runner.targetContext.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager)
                        .primaryClip
                clipboardSaved = true
            }
            val phoneBefore = state()
            openPhone()
            panel("phone")
            passive(phoneBefore, "phone report open and read")
            main { scroll("phone").scrollTo(0, 0) }
            shot("phone-report-top", "phone")
            bottom("phone")
            shot("phone-report-bottom", "phone")
            diagnosticsAndBack("phone")
            click("phone", "phone-menu-up")
            page("phone", "Settings")
            click("phone", "phone-menu-close")
            verify(
                read { phone.controllerMenu()?.isShowing != true },
                "phone Close dismisses report flow",
            )
            for (rotation in 0..3) {
                main { session.rotate(rotation, false) }
                stable("monitor rotation $rotation operation settled")
                verify(read { session.rotation == rotation }, "monitor rotation $rotation applied")
                main {
                    val current =
                        (runner.targetContext.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager)
                            .primaryClip
                            ?.getItemAt(0)
                            ?.text
                            .toString()
                    verify(
                        current == copiedReport,
                        "copied report unchanged after rotation $rotation",
                    )
                    if (rotation == 1)
                        verify(
                            current != session.diagnosticText(),
                            "copied report remains its earlier snapshot while live rotation context changes",
                        )
                }
                val before = state()
                openMonitor()
                panel("monitor")
                passive(before, "monitor rotation $rotation report open and read")
                main { scroll("monitor").scrollTo(0, 0) }
                shot("monitor-report-r$rotation-top", "monitor")
                bottom("monitor")
                shot("monitor-report-r$rotation-bottom", "monitor")
                diagnosticsAndBack("monitor")
                click("monitor", "monitor-close")
                page("monitor", "closed")
            }
            main { session.rotate(0, false) }
            stable("Fit rotation ready")
            main { session.preview(1920, 1080, 240) }
            await("Fit preview ready") { read { !session.busy && session.awaitingConfirmation() } }
            verify(
                read { session.baseW == 1920 && session.baseH == 1080 && session.dpi == 240 },
                "constrained Fit configuration applied",
            )
            val fit = state()
            openMonitor()
            panel("monitor", refresh = false)
            passive(fit, "Fit report open and read")
            main { scroll("monitor").scrollTo(0, 0) }
            shot("monitor-report-fit", "monitor")
            bottom("monitor")
            shot("monitor-report-fit-bottom", "monitor")
            verify(
                read { session.awaitingConfirmation() },
                "Fit capture completed before automatic rollback",
            )
            click("monitor", "monitor-close")
            main { session.confirm(false) }
            stable("Fit preview rolled back")
            results.append(
                "SCOPE native row clicks, rendered text/layout, focus/name affordances and private PixelCopy captures; no hardware-key, screen-reader, injected input, app response, acoustic or performance verification.\n"
            )
        } catch (failure: Throwable) {
            primary = failure
            // Preserve the actual failure state before Close/rollback changes the rendered menu.
            // Capture failure must never replace the assertion that triggered it.
            if (::session.isInitialized) {
                try {
                    if (
                        read {
                            session.monitorShellPage() != "closed" &&
                                session.monitorWindow() != null
                        }
                    ) {
                        progress(
                            read {
                                val viewport = scroll("monitor")
                                val target =
                                    root("monitor")
                                        .findViewWithTag<View>("monitor-capability-refresh")
                                val bounds = Rect()
                                val visible = target?.getGlobalVisibleRect(bounds) == true
                                "failure monitor geometry: rotation=${session.rotation}, " +
                                    "scrollY=${viewport.scrollY}, viewport=${viewport.width}x${viewport.height}, " +
                                    "refreshVisible=$visible, refreshBounds=$bounds"
                            }
                        )
                        shot("failure-monitor-report", "monitor")
                    }
                } catch (captureFailure: Throwable) {
                    if (captureFailure !== failure) failure.addSuppressed(captureFailure)
                    progress("failure monitor capture unavailable; original assertion retained")
                }
            }
            throw failure
        } finally {
            var cleanupFailure: Throwable? = null
            fun cleanup(action: () -> Unit) {
                try {
                    action()
                } catch (failure: Throwable) {
                    if (cleanupFailure == null) cleanupFailure = failure
                    else cleanupFailure!!.addSuppressed(failure)
                }
            }
            if (::session.isInitialized) {
                cleanup {
                    main {
                        phone.controllerMenu()?.dismiss()
                        if (session.monitorShellPage() != "closed") shell().close()
                        session.confirm(false)
                    }
                    stable("cleanup preview settled")
                }
                original?.let { saved ->
                    cleanup {
                        main { session.rotate(saved.rotation, false) }
                        stable("original rotation restored")
                        verify(
                            state() == saved,
                            "original desktop configuration, split and audio restored",
                        )
                    }
                }
                if (clipboardSaved)
                    cleanup {
                        main {
                            val manager =
                                runner.targetContext.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as ClipboardManager
                            if (clipboard == null) manager.clearPrimaryClip()
                            else manager.setPrimaryClip(clipboard!!)
                        }
                    }
            }
            cleanup { runner.targetContext.unbindService(binding) }
            cleanupFailure?.let { failure ->
                if (primary == null) throw failure
                if (primary !== failure) primary!!.addSuppressed(failure)
            }
        }
        return results.toString()
    }
}
