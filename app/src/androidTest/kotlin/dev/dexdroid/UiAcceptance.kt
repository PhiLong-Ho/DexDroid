package dev.dexdroid

import android.app.*
import android.content.*
import android.os.*
import android.view.*
import android.widget.Button
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier

/** Real controller interactions and external-app navigation, not resource snapshots. */
open class UiAcceptance(private val runner: Instrumentation?) {

    private val targetContext: Context?
        get() {
            return runner!!.getTargetContext()
        }

    private var session: SessionService? = null
    private var controller: Activity? = null
    private var binding: ServiceConnection? = null
    private var bound: Boolean = false
    private val results: StringBuilder = StringBuilder()

    private fun runOnMainSync(action: Runnable?) {
        runner!!.runOnMainSync(action)
    }

    private fun check(value: Boolean, message: String?) {
        if (!value) throw AssertionError(message)
        results.append("PASS ").append(message).append('\n')
    }

    @Throws(Exception::class)
    private fun await(predicate: BooleanSupplier, message: String?) {
        val end = SystemClock.uptimeMillis() + 10000
        while (!predicate.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(50)
        check(predicate.getAsBoolean(), message)
    }

    private fun find(root: View?, text: String?): Button? {
        if (root is Button && text!!.contentEquals((root as Button).getText()!!))
            return root as Button?
        if (root is ViewGroup) {
            val group = root as ViewGroup?
            for (i in 0 until group!!.getChildCount()) {
                val found = find(group!!.getChildAt(i), text)
                if (found != null) return found
            }
        }
        return null
    }

    private fun button(text: String?): Button {
        val found = find(controller!!.getWindow()!!.getDecorView(), text)
        check(found != null && found!!.isShown(), text!! + " visible")
        return found!!
    }

    private fun position(view: View): IntArray {
        val out = IntArray(2)
        view.getLocationOnScreen(out)
        return out
    }

    @Throws(Exception::class)
    open fun run(): String {
        val originalExpansion = booleanArrayOf(false, false, false)
        try {
            controller = DeviceTestSupport.startPhone(runner!!)
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
                targetContext!!.bindService(
                    Intent(targetContext, SessionService::class.java),
                    binding!!,
                    Context.BIND_AUTO_CREATE,
                )
            check(connected.await(5, TimeUnit.SECONDS), "controller service bound")
            await({ session!!.id >= 0 && !session!!.busy }, "automatic desktop ready")
            runOnMainSync({
                originalExpansion[2] = true
                originalExpansion[0] = session!!.prefs!!.contains("statusExpanded")
                originalExpansion[1] = session!!.prefs!!.getBoolean("statusExpanded", false)
                session!!.prefs!!.edit()!!.remove("statusExpanded")!!.apply()
            })
            await(
                {
                    (controller!!
                        .getWindow()!!
                        .getDecorView()
                        .findViewWithTag<View?>("phone-status-details")!!
                        .getVisibility() == View.GONE)
                },
                "status defaults to compact summary",
            )
            val compactHeight =
                controller!!
                    .getWindow()!!
                    .getDecorView()
                    .findViewWithTag<View?>("controller-status-card")!!
                    .getHeight()
            val compactPad =
                controller!!
                    .getWindow()!!
                    .getDecorView()
                    .findViewWithTag<View?>("touchpad")!!
                    .getHeight()
            runOnMainSync({
                val target =
                    controller!!
                        .getWindow()!!
                        .getDecorView()
                        .findViewWithTag<View?>("phone-status-expand")
                check(
                    target!!.performAccessibilityAction(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,
                        null,
                    ),
                    "native accessibility action expands status",
                )
            })
            await(
                {
                    (controller!!
                        .getWindow()!!
                        .getDecorView()
                        .findViewWithTag<View?>("controller-status-card")!!
                        .getHeight() > compactHeight)
                },
                "expansion reveals details and increases status height",
            )
            check(
                (controller!!
                    .getWindow()!!
                    .getDecorView()
                    .findViewWithTag<View?>("touchpad")!!
                    .getHeight() < compactPad),
                "collapse gives usable height back to touchpad",
            )
            runOnMainSync({
                val target =
                    controller!!
                        .getWindow()!!
                        .getDecorView()
                        .findViewWithTag<View?>("phone-status-expand")
                StatusDisclosureAssertions.inspectCue(
                    controller!!.getWindow()!!.getDecorView(),
                    "phone",
                    true,
                )
                check(
                    "Expanded".contentEquals(target!!.getStateDescription()!!),
                    "expanded status exposes accessible state",
                )
                check(target!!.requestFocusFromTouch(), "keyboard navigation can focus status body")
                check(
                    (target!!.dispatchKeyEvent(
                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
                    ) &&
                        target!!.dispatchKeyEvent(
                            KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
                        )),
                    "native keyboard Enter activates status disclosure",
                )
            })
            await(
                {
                    (controller!!
                        .getWindow()!!
                        .getDecorView()
                        .findViewWithTag<View?>("controller-status-card")!!
                        .getHeight() == compactHeight)
                },
                "collapse restores compact geometry",
            )
            check(
                !session!!.prefs!!.getBoolean("statusExpanded", true),
                "collapsed choice persisted",
            )
            runOnMainSync({
                val apps = button("Apps")
                val rotate = button("Rotate")
                val layout = button("Layout")
                val root = controller!!.getWindow()!!.getDecorView()
                check(
                    MenuTextAssertions.check(root) > 0,
                    "phone text encoding and glyph availability",
                )
                val a = position(apps)
                val r = position(rotate)
                val l = position(layout)
                check(
                    a[1] == r[1] && r[1] == l[1] && a[0] < r[0] && r[0] < l[0],
                    "one primary row Apps Rotate Layout",
                )
                check(root.findViewWithTag<View?>("phone-row-2") == null, "no second primary row")
                for (control in arrayOf<Button?>(rotate, layout)) check(
                    (Math.abs(control!!.getWidth() - apps.getWidth()) <= 1 &&
                        control!!.getHeight() == apps.getHeight()),
                    "primary controls equal size",
                )
                val stop = root.findViewWithTag<View?>("phone-stop")
                val expansion = root.findViewWithTag<View?>("phone-status-expand")
                val status = root.findViewWithTag<View?>("controller-status-card")
                check(
                    root.findViewWithTag<View?>("phone-app-settings") == null,
                    "status has no app-options shortcut",
                )
                check(
                    stop is android.widget.ImageButton,
                    "desktop remains an accessible status action",
                )
                results.append(StatusDisclosureAssertions.inspect(root, "phone"))
                val back = root.findViewWithTag<View?>("phone-back")
                val hint = root.findViewWithTag<View?>("touchpad-help")
                val pad = root.findViewWithTag<View?>("touchpad")
                val scroll = root.findViewWithTag<View?>("phone-scroll")
                val title = root.findViewWithTag<View?>("touchpad-title")
                val heading = root.findViewWithTag<View?>("touchpad-heading")
                val d = back!!.getResources()!!.getDisplayMetrics()!!.density
                val bp = position(back!!)
                val hp = position(hint!!)
                val pp = position(pad!!)
                val sp = position(scroll!!)
                val tp = position(title!!)
                check(
                    (back is android.widget.ImageButton &&
                        hint is android.widget.ImageButton &&
                        scroll is android.widget.ImageButton),
                    "touchpad uses accessible icon actions",
                )
                check(
                    (Math.abs(bp[1] - pp[1]) <= 4 * d &&
                        (Math.abs(((bp[0] + back!!.getWidth()) - (pp[0] + pad!!.getWidth()))) <=
                            4 * d) &&
                        Math.abs(sp[0] - pp[0]) <= 4 * d),
                    "Scroll and Back balance opposite touchpad edges",
                )
                val gp = position(heading!!)
                check(
                    ((Math.abs(
                        ((gp[0] + heading!!.getWidth() / 2f) - (pp[0] + pad!!.getWidth() / 2f))
                    ) <= d) &&
                        Math.abs(tp[0] + title!!.getWidth() - hp[0]) <= d &&
                        sp[0] + scroll!!.getWidth() <= gp[0] &&
                        hp[0] + hint!!.getWidth() <= bp[0]),
                    "Touchpad and adjacent Info form centered nonoverlapping group",
                )
                for (action in arrayOf<View?>(back, hint, scroll, stop, expansion)) check(
                    (action!!.getWidth() >= 47 * d &&
                        action!!.getHeight() >= 47 * d &&
                        action!!.getContentDescription() != null),
                    "compact icon retains 48dp accessible target",
                )
            })
            results.append(SetupPanelAcceptance.run(runner!!, controller as MainActivity?))
            results.append(PhoneMenuAcceptance(runner, controller as MainActivity?, session).run())
            val test = ComponentName(targetContext!!, InputTestActivity::class.java!!)
            runOnMainSync({ session!!.launch(test, null) })
            await({ !session!!.busy }, "external app configuration ready")
            Thread.sleep(600)
            results.append(
                StopReturnAcceptance.run(
                    runner!!,
                    controller as MainActivity?,
                    session!!,
                    {
                        controller!!
                            .getWindow()!!
                            .getDecorView()
                            .findViewWithTag<View?>("phone-stop")!!
                            .performClick()
                    },
                    "single",
                )
            )
            check(
                !session!!.prefs!!.getBoolean("statusExpanded", false),
                "Stop leaves status collapsed",
            )
            await(
                {
                    (session!!.id < 0 &&
                        !session!!.desktopRequested() &&
                        controller!!.hasWindowFocus())
                },
                "Stop releases desktop and preserves controller focus",
            )
            runOnMainSync({
                val start =
                    controller!!.getWindow()!!.getDecorView().findViewWithTag<View?>("phone-stop")
                check(
                    "Start desktop".contentEquals(start!!.getContentDescription()!!),
                    "status action changes to Start after Stop",
                )
                start!!.performClick()
            })
            await({ session!!.id >= 0 && !session!!.busy }, "Start resumes desktop")
            runOnMainSync({ session!!.launch(test, null) })
            await({ !session!!.busy }, "resumed app configuration ready")
            Thread.sleep(600)
            val before = InputTestActivity.backEvents
            runOnMainSync({
                controller!!
                    .getWindow()!!
                    .getDecorView()
                    .findViewWithTag<View?>("phone-back")!!
                    .performClick()
            })
            await(
                { InputTestActivity.backEvents > before },
                "touchpad-corner Back reaches external app after Start",
            )
            // Keyboard focus can belong to the external display while the phone's
            // controller remains visible. Assert visibility/lifecycle, not global focus.
            runOnMainSync({
                check(
                    (!controller!!.isFinishing() &&
                        !controller!!.isDestroyed() &&
                        (controller!!.getWindow()!!.getDecorView().getWindowVisibility() ==
                            View.VISIBLE) &&
                        controller!!
                            .getWindow()!!
                            .getDecorView()
                            .findViewWithTag<View?>("phone-back")!!
                            .isShown()),
                    "Back keeps phone controller open",
                )
            })
            results.append(
                StopReturnAcceptance.failures(runner, controller as MainActivity?, session, false)
            )
            results.append(
                StopReturnAcceptance.boundaries(runner!!, controller as MainActivity?, session)
            )
            return results.toString()
        } finally {
            if (session != null && originalExpansion!![2])
                runOnMainSync({
                    val editor = session!!.prefs!!.edit()
                    if (originalExpansion!![0])
                        editor!!.putBoolean("statusExpanded", originalExpansion!![1])
                    else editor!!.remove("statusExpanded")
                    editor!!.apply()
                })
            if (bound) targetContext!!.unbindService(binding!!)
        }
    }
}
