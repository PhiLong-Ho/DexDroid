package dev.dexdroid

import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import java.io.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import java.util.function.*
import org.json.*

/** Visible pending/error/cache behavior with a real PM scan behind a controlled worker gate. */
class CatalogAcceptance(private val runner: Instrumentation?) {
    private val result: StringBuilder = StringBuilder()
    private var session: SessionService? = null
    private var phone: MainActivity? = null
    private val phoneDraw: MutableList<Long> = ArrayList<Long>()
    private val monitorDraw: MutableList<Long> = ArrayList<Long>()
    private val scans: MutableList<Long> = ArrayList<Long>()
    private var folder: File? = null

    private fun main(action: Runnable?) {
        runner!!.runOnMainSync(action)
    }

    private fun check(value: Boolean, label: String?) {
        if (!value) throw AssertionError(label)
        result.append("PASS ").append(label).append('\n')
    }

    @Throws(Exception::class)
    private fun await(condition: BooleanSupplier, label: String?) {
        val end = SystemClock.uptimeMillis() + 10000
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(25)
        check(condition.getAsBoolean(), label)
    }

    private fun ui(condition: BooleanSupplier?): Boolean {
        val value = booleanArrayOf(false)
        main({ value[0] = condition!!.getAsBoolean() })
        return value!![0]
    }

    private fun phoneRoot(): View? {
        return if (phone!!.controllerMenu() == null) null
        else phone!!.controllerMenu()!!.getWindow()!!.getDecorView()
    }

    private fun monitorRoot(): View? {
        return session!!.monitorShellView()
    }

    private fun find(root: View?, tag: String?): View? {
        return if (root == null) null else root!!.findViewWithTag<View?>(tag)
    }

    private fun description(root: View, label: String): View? {
        if (label == java.lang.String.valueOf(root.getContentDescription())) return root
        if (root is ViewGroup)
            for (i in 0 until (root as ViewGroup).getChildCount()) {
                val found = description((root as ViewGroup).getChildAt(i)!!, label)
                if (found != null) return found
            }
        return null
    }

    @Throws(Exception::class)
    private fun drawn(open: Runnable?, root: Supplier<View?>?): Long {
        val done = CountDownLatch(1)
        val elapsed = longArrayOf(-1)
        main({
            val start = SystemClock.elapsedRealtimeNanos()
            open!!.run()
            val view = root!!.get()
            check(view != null, "menu shell created synchronously")
            val observer =
                object : ViewTreeObserver.OnDrawListener {
                    public override fun onDraw() {
                        if (elapsed!![0] < 0) {
                            elapsed[0] = SystemClock.elapsedRealtimeNanos() - start
                            done.countDown()
                        }
                        view!!.post({
                            if (view!!.getViewTreeObserver()!!.isAlive())
                                view!!.getViewTreeObserver()!!.removeOnDrawListener(this)
                        })
                    }
                }
            view!!.getViewTreeObserver()!!.addOnDrawListener(observer)
            view!!.invalidate()
        })
        check(done.await(3, TimeUnit.SECONDS), "menu rendered while worker can be pending")
        return elapsed!![0] / 1000000
    }

    private fun phoneOpen() {
        description(phone!!.getWindow()!!.getDecorView(), "Apps")!!.performClick()
    }

    private fun monitorOpen() {
        find(monitorRoot(), "monitor-idle-apps")!!.performClick()
    }

    @Throws(Exception::class)
    private fun phoneClose() {
        main({ find(phoneRoot(), "phone-menu-close")!!.performClick() })
        await(
            { ui({ phone!!.controllerMenu() == null }) },
            "phone closes while discovery may be pending",
        )
    }

    @Throws(Exception::class)
    private fun monitorClose() {
        main({ find(monitorRoot(), "monitor-close")!!.performClick() })
        await(
            {
                ui({
                    (session!!.monitorShellPage() == "closed" && !session!!.monitorShellAnimating())
                })
            },
            "monitor closes while discovery may be pending",
        )
    }

    private fun loading(monitor: Boolean): Boolean {
        val row =
            find(
                if (monitor) monitorRoot() else phoneRoot(),
                if (monitor) "monitor-app-loading" else "phone-app-loading",
            )
        return row != null && row!!.isShown()
    }

    private fun count(monitor: Boolean): Int {
        val list =
            find(
                if (monitor) monitorRoot() else phoneRoot(),
                if (monitor) "monitor-app-list" else "phone-app-list",
            )
                as ListView?
        return if (list == null) -1 else list!!.getCount()
    }

    @Throws(Exception::class)
    private fun shot(name: String?, monitor: Boolean) {
        // Visual evidence follows presentation/enter animation; first-draw timings
        // above intentionally use their earlier OnDraw boundary instead.
        Thread.sleep(250)
        val bitmap: Bitmap?
        if (monitor) {
            val window = session!!.monitorWindow()
            val view = window!!.getDecorView()
            bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888)
            val copied = CountDownLatch(1)
            val code = intArrayOf(-1)
            main({
                PixelCopy.request(
                    window!!,
                    bitmap!!,
                    { value ->
                        code[0] = value
                        copied.countDown()
                    },
                    Handler(Looper.getMainLooper()!!),
                )
            })
            check(
                copied.await(3, TimeUnit.SECONDS) && code!![0] == PixelCopy.SUCCESS,
                "monitor pending pixels captured",
            )
        } else bitmap = runner!!.getUiAutomation()!!.takeScreenshot()
        check(bitmap != null, "catalog pixels captured")
        try {
            FileOutputStream(File(folder, name!! + ".png"))
                .use({ out -> bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, out) })
        } finally {
            bitmap!!.recycle()
        }
    }

    @Throws(Exception::class)
    private fun times(values: List<Long>?): JSONObject {
        val sorted = ArrayList<Long>(values!!)
        Collections.sort<Long>(sorted)
        return JSONObject()
            .put("samples_ms", JSONArray(values))
            .put("n", values!!.size)
            .put("p50_ms", sorted.get(Math.ceil(sorted.size * .5).toInt() - 1))
            .put("p95_ms", sorted.get(Math.ceil(sorted.size * .95).toInt() - 1))
    }

    private class Gate {
        @JvmField val entered: CountDownLatch = CountDownLatch(1)
        @JvmField val release: CountDownLatch = CountDownLatch(1)
    }

    @Throws(Exception::class)
    fun run(): String {
        val context = runner!!.getTargetContext()
        val bound = CountDownLatch(1)
        val connection =
            object : ServiceConnection {
                public override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    session = (binder as SessionService.LocalBinder).get()
                    bound.countDown()
                }

                public override fun onServiceDisconnected(name: ComponentName?) {}
            }
        var original: AppCatalog? = null
        var test: AppCatalog? = null
        val gate = AtomicReference<Gate?>(Gate())
        val fail = AtomicBoolean()
        val calls = AtomicInteger()
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        var connected = false
        try {
            val discovered = AppCatalog.installed(context)
            val diagnostic = ComponentName(context!!, InputTestActivity::class.java!!)
            val diagnostics =
                discovered.stream()!!.filter({ entry -> entry!!.component == diagnostic })!!.count()
            check(diagnostics == 1L, "debug catalog retains exactly one input diagnostic")
            check(
                context!!.getPackageManager()!!.getActivityInfo(diagnostic, 0).exported,
                "debug input diagnostic component remains launchable",
            )
            phone = DeviceTestSupport.startPhone(runner!!)
            connected =
                context!!.bindService(
                    Intent(context, SessionService::class.java),
                    connection,
                    Context.BIND_AUTO_CREATE,
                )
            check(bound.await(5, TimeUnit.SECONDS), "catalog service bound")
            await({ session!!.id >= 0 && !session!!.busy }, "catalog desktop ready")
            folder = File(context!!.getExternalCacheDir(), "catalog-acceptance")
            check(folder!!.isDirectory() || folder!!.mkdirs(), "catalog evidence directory")
            main({
                session!!.pauseDesktop()
                session!!.ensureDesktop()
            })
            await({ session!!.id >= 0 && !session!!.busy }, "fresh idle catalog desktop ready")
            original = session!!.appCatalog
            val created = arrayOf<AppCatalog?>(null)
            main({
                created[0] =
                    AppCatalog(
                        context!!,
                        AppCatalog.Source load@{
                            calls.incrementAndGet()
                            maximum.accumulateAndGet(
                                active.incrementAndGet(),
                                IntBinaryOperator({ i, i1 -> Math.max(i, i1) }),
                            )
                            try {
                                if (Looper.myLooper() == Looper.getMainLooper())
                                    throw IllegalStateException("Discovery ran on UI thread")
                                val current = gate.get()
                                current!!.entered.countDown()
                                if (!current!!.release.await(20, TimeUnit.SECONDS))
                                    throw IOException("Acceptance worker gate timed out")
                                if (fail.getAndSet(false))
                                    throw IOException("Controlled discovery failure")
                                val start = SystemClock.elapsedRealtime()
                                val entries = AppCatalog.installed(context)
                                scans.add(SystemClock.elapsedRealtime() - start)
                                return@load entries
                            } finally {
                                active.decrementAndGet()
                            }
                        },
                        Consumer<String?>(session!!::log),
                    )
            })
            test = created!![0]
            val catalog = test
            main({ session!!.appCatalog = catalog })
            check(
                gate.get()!!.entered.await(3, TimeUnit.SECONDS),
                "real discovery source waits on worker",
            )
            val coldPhone =
                drawn(Runnable({ this.phoneOpen() }), Supplier<View?>({ this.phoneRoot() }))
            val coldMonitor =
                drawn(Runnable({ this.monitorOpen() }), Supplier<View?>({ this.monitorRoot() }))
            check(
                ui({ loading(false) && loading(true) }),
                "both surfaces clearly show pending discovery",
            )
            check(
                ui({ count(false) == 0 && count(true) == 0 }),
                "pending list has no fake launch targets",
            )
            shot("phone-loading", false)
            shot("monitor-loading-r0", true)
            main({ (find(phoneRoot(), "phone-app-search") as EditText).setText("input test") })
            main({
                (find(monitorRoot(), "monitor-app-search") as EditText).setText("dev.dexdroid")
            })
            phoneClose()
            drawn(Runnable({ this.phoneOpen() }), Supplier<View?>({ this.phoneRoot() }))
            main({ (find(phoneRoot(), "phone-app-search") as EditText).setText("input test") })
            check(calls.get() == 1, "both surfaces and reopen share one in-flight discovery")
            gate.get()!!.release.countDown()
            await(
                {
                    ui({
                        (!loading(false) && !loading(true) && count(false) == 1 && count(true) == 1)
                    })
                },
                "async results preserve both typed queries",
            )
            check(maximum.get() == 1, "discovery is single flight")
            phoneClose()
            monitorClose()
            for (i in 0..9) {
                phoneDraw.add(
                    drawn(Runnable({ this.phoneOpen() }), Supplier<View?>({ this.phoneRoot() }))
                )
                check(
                    ui({ count(false) > 3 && !loading(false) }),
                    "warm phone has cached apps immediately",
                )
                phoneClose()
            }
            for (rotation in 0..3) {
                val r = rotation
                main({ session!!.rotate(r, false) })
                await({ !session!!.busy && session!!.rotation == r }, "catalog rotation ready " + r)
                for (i in 0..2) {
                    monitorDraw.add(
                        drawn(
                            Runnable({ this.monitorOpen() }),
                            Supplier<View?>({ this.monitorRoot() }),
                        )
                    )
                    check(
                        ui({ count(true) > 3 && !loading(true) }),
                        "warm monitor has cached apps immediately",
                    )
                    monitorClose()
                }
            }
            check(calls.get() == 1, "warm opens and rotation perform no package rescans")
            val refresh = Gate()
            gate.set(refresh)
            main(Runnable({ catalog!!.invalidate() }))
            check(refresh.entered.await(3, TimeUnit.SECONDS), "refresh runs on worker")
            drawn(Runnable({ this.phoneOpen() }), Supplier<View?>({ this.phoneRoot() }))
            drawn(Runnable({ this.monitorOpen() }), Supplier<View?>({ this.monitorRoot() }))
            main({
                (find(phoneRoot(), "phone-app-search") as EditText).setText("input test")
                (find(monitorRoot(), "monitor-app-search") as EditText).setText("dev.dexdroid")
            })
            check(
                ui({ (loading(false) && loading(true) && count(false) == 1 && count(true) == 1) }),
                "refresh retains usable cached search results with pending indicator",
            )
            shot("monitor-updating-r3", true)
            refresh.release.countDown()
            await(
                { ui({ !loading(false) && !loading(true) }) },
                "refresh finishes without rebuilding editors",
            )
            check(
                ui({ count(false) == 1 && count(true) == 1 }),
                "refresh preserves queries and results",
            )
            fail.set(true)
            main(Runnable({ catalog!!.invalidate() }))
            await(
                {
                    ui({
                        java.lang.String.valueOf(
                                find(phoneRoot(), "phone-app-loading")!!.getContentDescription()
                            )
                            .contains("retry")
                    })
                },
                "discovery error shows retry instead of an endless spinner",
            )
            shot("phone-error", false)
            val retry = Gate()
            gate.set(retry)
            main({ find(phoneRoot(), "phone-app-loading")!!.performClick() })
            check(retry.entered.await(3, TimeUnit.SECONDS), "retry starts another background load")
            phoneClose()
            monitorClose()
            retry.release.countDown()
            await({ active.get() == 0 }, "retry worker completes after dismissal")
            Thread.sleep(100)
            check(
                ui({
                    (phone!!.controllerMenu() == null && session!!.monitorShellPage() == "closed")
                }),
                "late results never reopen dismissed menus",
            )
            val timing =
                JSONObject()
                    .put(
                        "boundary",
                        "Accepted native Apps action to first OnDraw; excludes physical/ADB input and animation completion",
                    )
                    .put("device", Build.MODEL)
                    .put("android", Build.VERSION.RELEASE)
                    .put("cold_phone_first_draw_ms", coldPhone)
                    .put("cold_monitor_first_draw_ms", coldMonitor)
                    .put(
                        "cold_note",
                        "Controlled gate keeps worker pending; these are acknowledgment times, not artificial source latency",
                    )
                    .put("warm_phone", times(phoneDraw))
                    .put("warm_monitor_four_rotations", times(monitorDraw))
                    .put("actual_worker_scan", times(scans))
                    .put("maximum_parallel_loads", maximum.get())
            FileOutputStream(File(folder, "timings.json"))
                .use({ out ->
                    out.write(
                        (timing.toString(2) + "\n").toByteArray(
                            java.nio.charset.StandardCharsets.UTF_8
                        )
                    )
                })
            result.append("TIMINGS ").append(timing).append('\n')
            return result.toString()
        } catch (failure: Throwable) {
            throw RuntimeException(result.toString() + "FAIL catalog acceptance", failure)
        } finally {
            gate.get()!!.release.countDown()
            val keep = original
            val temporary = test
            main({
                if (phone != null && phone!!.controllerMenu() != null)
                    phone!!.controllerMenu()!!.dismiss()
                if (session != null && keep != null) session!!.appCatalog = keep
                if (temporary != null) temporary!!.close()
            })
            if (connected) context!!.unbindService(connection)
        }
    }
}
