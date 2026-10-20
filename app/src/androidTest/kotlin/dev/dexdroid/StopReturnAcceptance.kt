package dev.dexdroid

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.View
import java.io.*
import java.util.concurrent.*
import java.util.function.BooleanSupplier

/** Stop through a real UI action while the actual display cleanup worker is held. */
internal class StopReturnAcceptance {
    companion object {
        @Throws(Exception::class)
        @JvmStatic
        fun run(
            runner: Instrumentation,
            phone: MainActivity?,
            session: SessionService,
            stopAction: Runnable?,
            label: String?,
        ): String {
            val first = session.id
            val second = if (session.split == null) -1 else session.split!!.displayId(1)
            val displays = session.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager?
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            session.desktopWork({
                entered.countDown()
                try {
                    if (!release.await(8, TimeUnit.SECONDS))
                        throw IllegalStateException("Stop test gate timed out")
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            })
            try {
                require(entered.await(3, TimeUnit.SECONDS), "cleanup worker held")
                runner.runOnMainSync(stopAction)
                // Deliberately exceed the former 250ms restore timer before releasing
                // real virtual displays, allowing Android's actual task handoff to run.
                Thread.sleep(700)
                require(displays!!.getDisplay(first) != null, "display cleanup really delayed")
            } finally {
                release.countDown()
            }
            await(
                {
                    (displays!!.getDisplay(first) == null &&
                        (second < 0 || displays!!.getDisplay(second) == null))
                },
                "both owned displays removed",
            )
            await(
                {
                    (phone!!.hasWindowFocus() &&
                        phone!!.getDisplay()!!.getDisplayId() == 0 &&
                        phone!!.getWindow()!!.getDecorView().isShown())
                },
                "existing phone controller foreground after delayed cleanup",
            )
            require(
                session.id < 0 && !session.busy && !session.desktopRequested(),
                "Stop focus return does not restart desktop",
            )
            val bridge = SessionService::class.java!!.getDeclaredField("bridge")
            bridge.setAccessible(true)
            val helper = bridge.get(session) as IDeskService?
            val scope =
                FutureTask<Void?>({
                    require(!helper!!.focusController(-1), "unknown task cannot be focused")
                    val manager =
                        session.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
                    for (task in manager!!.getAppTasks()!!) {
                        val info = task!!.getTaskInfo()
                        val component = info!!.baseIntent.getComponent()
                        if (ComponentName(session, InputTestActivity::class.java!!) == component)
                            require(
                                !helper!!.focusController(info!!.taskId),
                                "a migrated input-test task cannot be focused as controller",
                            )
                    }
                    null
                })
            session.desktopWork(scope)
            scope.get(5, TimeUnit.SECONDS)
            runner.runOnMainSync({
                val root = phone!!.getWindow()!!.getDecorView()
                StatusDisclosureAssertions.inspectDesktop(root, "phone", "Start desktop")
                val settings = description(root, "Settings")
                require(settings != null && settings!!.performClick(), "Settings usable after Stop")
            })
            await(
                { phone!!.controllerMenu() != null && phone!!.controllerMenu()!!.isShowing() },
                "phone Settings opens after Stop",
            )
            runner.runOnMainSync({
                phone!!
                    .controllerMenu()!!
                    .getWindow()!!
                    .getDecorView()
                    .findViewWithTag<View?>("phone-menu-close")!!
                    .performClick()
            })
            await({ phone!!.controllerMenu() == null }, "Settings closes after Stop")
            await(
                BooleanSupplier({ phone!!.hasWindowFocus() }),
                "controller focused after closing stopped Settings",
            )
            val stableUntil = SystemClock.uptimeMillis() + 1000
            while (SystemClock.uptimeMillis() < stableUntil) {
                require(
                    phone!!.hasWindowFocus(),
                    "controller stays foreground after stopped Settings closes",
                )
                Thread.sleep(25)
            }
            val screenshot = runner.getUiAutomation()!!.takeScreenshot()
            require(screenshot != null, "stopped phone screenshot")
            val folder =
                File(
                    runner.getTargetContext()!!.getExternalCacheDir(),
                    if (second < 0) "submenu-acceptance" else "split-acceptance",
                )
            if (!folder.isDirectory() && !folder.mkdirs())
                throw IOException("Stop evidence directory")
            try {
                FileOutputStream(File(folder, "phone-stopped-" + label + ".png"))
                    .use({ output ->
                        screenshot!!.compress(Bitmap.CompressFormat.PNG, 100, output)
                    })
            } finally {
                screenshot!!.recycle()
            }
            return ("PASS " +
                label +
                " Stop after >700ms worker delay: displays removed, existing controller foreground, stopped Start icon transparent/white/48dp and Settings usable\n")
        }

        @Throws(Exception::class)
        @JvmStatic
        fun boundaries(
            runner: Instrumentation,
            phone: MainActivity?,
            session: SessionService?,
        ): String {
            val bridge = SessionService::class.java!!.getDeclaredField("bridge")
            bridge.setAccessible(true)
            val helper = bridge.get(session) as IDeskService?
            val refusal =
                FutureTask<Void?>({
                    try {
                        helper!!.focusController(phone!!.getTaskId())
                        throw AssertionError("active desktop must refuse a stale controller return")
                    } catch (expected: IllegalStateException) {
                        require(
                            expected!!.message!!.contains("restarted"),
                            "active desktop return rejected",
                        )
                    }

                    null
                })
            session!!.desktopWork(refusal)
            refusal.get(5, TimeUnit.SECONDS)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            session!!.desktopWork({
                entered.countDown()
                try {
                    if (!release.await(8, TimeUnit.SECONDS))
                        throw IllegalStateException("Stale Stop gate timed out")
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            })
            try {
                require(entered.await(3, TimeUnit.SECONDS), "stale Stop worker held")
                runner.runOnMainSync({
                    session!!.pauseDesktop()
                    session!!.ensureDesktop()
                })
                runner
                    .getTargetContext()!!
                    .startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        ActivityOptions.makeBasic()!!.setLaunchDisplayId(0)!!.toBundle(),
                    )
                await(
                    { phone!!.getWindow()!!.getDecorView().getWindowVisibility() != View.VISIBLE },
                    "phone Home hides controller before obsolete Stop finishes",
                )
            } finally {
                release.countDown()
            }
            await(
                { session!!.id >= 0 && !session!!.busy },
                "new session starts after obsolete cleanup",
            )
            drain(runner, session!!)
            require(
                phone!!.getWindow()!!.getDecorView().getWindowVisibility() != View.VISIBLE,
                "obsolete Stop completion does not foreground controller over Home/new session",
            )
            val display = session!!.id
            runner.runOnMainSync(Runnable({ session!!.stopDesktop() }))
            val displays = session!!.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager?
            await({ displays!!.getDisplay(display) == null }, "passive cleanup removes display")
            drain(runner, session!!)
            require(
                phone!!.getWindow()!!.getDecorView().getWindowVisibility() != View.VISIBLE,
                "passive cleanup does not foreground controller",
            )
            // Exercise the real MainActivity.onDestroy owner-release boundary while
            // preserving a background task, so a stale moveToFront would be observable.
            val owner = arrayOf<SessionService.Listener?>(null)
            val ownerEntered = CountDownLatch(1)
            val ownerRelease = CountDownLatch(1)
            session!!.desktopWork({
                ownerEntered.countDown()
                try {
                    if (!ownerRelease.await(8, TimeUnit.SECONDS))
                        throw IllegalStateException("Owner Stop gate timed out")
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            })
            require(ownerEntered.await(3, TimeUnit.SECONDS), "owner cleanup worker held")
            try {
                runner.runOnMainSync({
                    owner[0] = session!!.listener
                    require(owner!![0] != null, "live controller owner captured before Stop")
                    session!!.pauseDesktop()
                    session!!.listener = null
                })
            } finally {
                ownerRelease.countDown()
            }
            try {
                drain(runner, session!!)
                require(
                    phone!!.getWindow()!!.getDecorView().getWindowVisibility() != View.VISIBLE,
                    "released UI owner cancels pending Stop return without foregrounding old task",
                )
            } finally {
                runner.runOnMainSync({
                    if (session!!.listener == null) session!!.listener = owner!![0]
                })
            }
            runner
                .getTargetContext()!!
                .startActivity(
                    Intent(runner.getTargetContext(), MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    ActivityOptions.makeBasic()!!.setLaunchDisplayId(0)!!.toBundle(),
                )
            await(
                {
                    (!phone!!.isFinishing() &&
                        !phone!!.isDestroyed() &&
                        (phone!!.getWindow()!!.getDecorView().getWindowVisibility() ==
                            View.VISIBLE))
                },
                "launcher reuses controller after passive teardown",
            )
            await(
                { session!!.id >= 0 && !session!!.busy },
                "explicit launcher reopen starts desktop",
            )
            return "PASS obsolete Stop completion preserves Home/new session; passive teardown stays background; released UI owner cancels Stop return; launcher reuses controller and starts fresh desktop\n"
        }

        @Throws(Exception::class)
        @JvmStatic
        fun failures(
            runner: Instrumentation?,
            phone: MainActivity?,
            session: SessionService?,
            secondary: Boolean,
        ): String {
            val results = StringBuilder()
            for (restart in booleanArrayOf(false, true)) {
                val target = if (secondary) session!!.split else session
                val field =
                    (if (secondary) SplitWorkspace::class.java else SessionService::class.java)
                        .getDeclaredField(if (secondary) "secondBridge" else "bridge")
                field.setAccessible(true)
                val original = field.get(target) as IDeskService?
                require(original != null, "real cleanup bridge available before injected failure")
                val first = session!!.id
                val second = if (session!!.split == null) -1 else session!!.split!!.displayId(1)
                val stopped = CountDownLatch(1)
                val release = CountDownLatch(1)
                val failing =
                    java.lang.reflect.Proxy.newProxyInstance(
                        IDeskService::class.java!!.getClassLoader(),
                        arrayOf<Class<*>?>(IDeskService::class.java),
                        { proxy, method, arguments ->
                            val result: Any?
                            try {
                                result = method!!.invoke(original, *(arguments ?: emptyArray()))
                            } catch (error: java.lang.reflect.InvocationTargetException) {
                                throw error!!.cause!!
                            }

                            if (method!!.getName() == "stop") {
                                stopped.countDown()
                                if (restart && !release.await(8, TimeUnit.SECONDS))
                                    throw IllegalStateException("Cleanup failure gate timed out")
                                throw IllegalStateException("Injected cleanup confirmation failure")
                            }
                            result
                        },
                    ) as IDeskService
                runner!!
                    .getTargetContext()!!
                    .startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        ActivityOptions.makeBasic()!!.setLaunchDisplayId(0)!!.toBundle(),
                    )
                await(
                    { phone!!.getWindow()!!.getDecorView().getWindowVisibility() != View.VISIBLE },
                    "Home hides controller before cleanup failure",
                )
                try {
                    runner!!.runOnMainSync({
                        try {
                            field.set(target, failing)
                        } catch (error: IllegalAccessException) {
                            throw AssertionError(error)
                        }

                        session!!.pauseDesktop()
                    })
                    require(
                        stopped.await(8, TimeUnit.SECONDS),
                        "real bridge Stop reached before injected failure",
                    )
                    if (restart) runner!!.runOnMainSync(Runnable({ session!!.ensureDesktop() }))
                    release.countDown()
                    val displays =
                        session!!.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager?
                    await(
                        {
                            (displays!!.getDisplay(first) == null &&
                                (second < 0 || displays!!.getDisplay(second) == null))
                        },
                        "owned displays released despite reported cleanup failure",
                    )
                    drain(runner!!, session!!)
                    if (restart) {
                        await(
                            { session!!.id >= 0 && !session!!.busy },
                            "new session ready after old cleanup failure",
                        )
                        require(
                            !session!!.status!!.contains("Cleanup unconfirmed"),
                            "obsolete cleanup failure does not replace new status",
                        )
                    } else {
                        await(
                            {
                                (session!!.status!!.contains("Cleanup unconfirmed") &&
                                    session!!.status!!.contains("Reopen DexDroid"))
                            },
                            "cleanup failure exposes uncertainty and recovery guidance",
                        )
                        require(
                            session!!.id < 0 && !session!!.desktopRequested(),
                            "failed cleanup return stays stopped",
                        )
                    }
                    val until = SystemClock.uptimeMillis() + 1000
                    while (SystemClock.uptimeMillis() < until) {
                        require(
                            phone!!.getWindow()!!.getDecorView().getWindowVisibility() !=
                                View.VISIBLE,
                            "failed cleanup never foregrounds the phone controller",
                        )
                        Thread.sleep(25)
                    }
                    require(
                        session!!
                            .diagnosticText()
                            .contains("Injected cleanup confirmation failure"),
                        "injected cleanup result is retained diagnostically",
                    )
                } finally {
                    release.countDown()
                    runner!!.runOnMainSync({
                        try {
                            if (field.get(target) === failing) field.set(target, original)
                        } catch (error: IllegalAccessException) {
                            throw AssertionError(error)
                        }
                    })
                }
                runner!!
                    .getTargetContext()!!
                    .startActivity(
                        Intent(runner!!.getTargetContext(), MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        ActivityOptions.makeBasic()!!.setLaunchDisplayId(0)!!.toBundle(),
                    )
                await(
                    {
                        (!phone!!.isDestroyed() &&
                            (phone!!.getWindow()!!.getDecorView().getWindowVisibility() ==
                                View.VISIBLE) &&
                            session!!.id >= 0 &&
                            !session!!.busy &&
                            (!secondary || session!!.split != null))
                    },
                    "explicit phone recovery restarts usable desktop after cleanup failure",
                )
                results
                    .append("PASS ")
                    .append(if (secondary) "secondary" else "primary")
                    .append(" injected cleanup confirmation fault; no controller foreground; ")
                    .append(
                        if (restart) "obsolete fault preserves new session/status"
                        else "stopped uncertainty and explicit recovery"
                    )
                    .append("; real displays released and launcher recovery works\n")
            }
            return results.toString()
        }

        @Throws(Exception::class)
        private fun drain(runner: Instrumentation, session: SessionService) {
            val done = CountDownLatch(1)
            session.desktopWork({
                android.os
                    .Handler(android.os.Looper.getMainLooper()!!)
                    .post(Runnable({ done.countDown() }))
            })
            require(done.await(8, TimeUnit.SECONDS), "cleanup and main callbacks drained")
            runner.waitForIdleSync()
        }

        private fun description(view: View, label: String?): View? {
            if (android.text.TextUtils.equals(label, view.getContentDescription())) return view
            if (view is android.view.ViewGroup) {
                val group = view as android.view.ViewGroup
                for (i in 0 until group!!.getChildCount()) {
                    val found = description(group!!.getChildAt(i)!!, label)
                    if (found != null) return found
                }
            }
            return null
        }

        @Throws(Exception::class)
        private fun await(condition: BooleanSupplier, message: String?) {
            val end = SystemClock.uptimeMillis() + 10000
            while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(50)
            require(condition.getAsBoolean(), message)
        }

        private fun require(condition: Boolean, message: String?) {
            if (!condition) throw AssertionError(message)
        }
    }
}
