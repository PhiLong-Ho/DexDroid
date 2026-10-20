package dev.dexdroid

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.os.*
import android.view.*
import android.widget.*
import java.util.*
import java.util.concurrent.*
import rikka.shizuku.Shizuku

/** Explicit debug-only experiment; not reachable through the product launcher or release build. */
class SplitProbeActivity : Activity(), DisplayManager.DisplayListener {
    @JvmField val panes: Array<Pane?> = arrayOfNulls<Pane?>(2)
    @JvmField val main: Handler = Handler(Looper.getMainLooper()!!)
    private val worker: ThreadPoolExecutor =
        ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable?>(128))
    private val owner: IBinder = Binder()
    private val heldKeys: MutableMap<Int?, Pane?> = HashMap<Int?, Pane?>()
    private val shizukuReady: Shizuku.OnBinderReceivedListener? =
        Shizuku.OnBinderReceivedListener { main.post(Runnable({ this.start() })) }
    private val permission: Shizuku.OnRequestPermissionResultListener? =
        Shizuku.OnRequestPermissionResultListener { request, result -> start() }
    private var displays: DisplayManager? = null
    @JvmField var presentation: Presentation? = null
    @JvmField var desktop: LinearLayout? = null
    @JvmField var status: TextView? = null
    private var style: ControllerStyle? = null
    private var selected: Int = 0
    private var contact: Int = -1
    private var ratio: Float = .5f
    @Volatile @JvmField var stopping: Boolean = false
    private var started: Boolean = false
    private var controller: ServiceConnection? = null
    private var controllerBound: Boolean = false
    private var waitingForFocus: Boolean = false
    private val pendingInput: ArrayDeque<PendingInput?> = ArrayDeque<PendingInput?>()
    private val focusTimeout: Runnable? = Runnable({ this.releasePending() })

    private inner class PendingInput(@JvmField val pane: Pane?, @JvmField val event: InputEvent?) {

        fun discard() {
            if (event is MotionEvent) (event as MotionEvent).recycle()
        }
    }

    private fun releasePending() {
        if (!waitingForFocus || stopping) return
        waitingForFocus = false
        main.removeCallbacks(focusTimeout!!)
        val ready = ArrayDeque<PendingInput?>(pendingInput)
        pendingInput.clear()
        android.util.Log.i(
            "DexDroidSplitProbe",
            "Legacy host focus settled; releasing " + ready.size + " input events",
        )
        while (!ready.isEmpty()) {
            val pending = ready.removeFirst()
            pending!!.pane!!.send(pending!!.event)
        }
    }

    public override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        style = ControllerStyle(this)
        val root = LinearLayout(this)
        root.setOrientation(LinearLayout.VERTICAL)
        root.setPadding(style!!.dp(16f), style!!.dp(32f), style!!.dp(16f), style!!.dp(16f))
        root.setBackgroundColor(Color.rgb(8, 8, 8))
        root.addView(style!!.label("DexDroid | Two-pane experiment", 22))
        status = style!!.label("Waiting for Shizuku and monitor", 13)
        root.addView(status)
        val focus = LinearLayout(this)
        root.addView(focus)
        style!!.button(focus, "Left app", { choose(0) })
        style!!.button(focus, "Right app", { choose(1) })
        val actions = LinearLayout(this)
        root.addView(actions)
        style!!.button(actions, "Split ratio", { setRatio(if (ratio == .5f) .35f else .5f) })
        style!!.button(actions, "Back", { keyCode(KeyEvent.KEYCODE_BACK) })
        style!!.button(actions, "Stop", { stopExperiment(true) })
        root.addView(
            style!!.label(
                ("Touch a pane to select it. Hardware keys go to that pane.\n" +
                    "Select Left/Right app to choose another app.\n" +
                    "Debug experiment: two displays, no saved product settings."),
                14,
            )
        )
        val pointer =
            object : View(this) {
                private fun route(event: MotionEvent?): Boolean {
                    if (desktop == null || desktop!!.getWidth() < 1) return true
                    val x = event!!.getX() * desktop!!.getWidth() / Math.max(1, getWidth())
                    val y = event!!.getY() * desktop!!.getHeight() / Math.max(1, getHeight())
                    val pane = paneAt(x)
                    val copy = MotionEvent.obtain(event)
                    val scale = Matrix()
                    scale.setScale(
                        desktop!!.getWidth() / Math.max(1, getWidth()).toFloat(),
                        desktop!!.getHeight() / Math.max(1, getHeight()).toFloat(),
                    )
                    copy!!.transform(scale)
                    copy!!.offsetLocation((-pane!!.frame!!.getLeft()).toFloat(), 0f)
                    pane!!.route(copy)
                    copy!!.recycle()
                    return true
                }

                public override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
                    return route(event)
                }

                public override fun onTouchEvent(event: MotionEvent): Boolean {
                    if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick()
                    return route(event)
                }

                public override fun performClick(): Boolean {
                    super.performClick()
                    return true
                }

                public override fun onHoverEvent(event: MotionEvent?): Boolean {
                    return route(event)
                }
            }
        pointer.setBackground(style!!.surface(18, 12, 45))
        pointer.setContentDescription("Hardware pointer area mapped to external desktop")
        root.addView(style!!.label("Hardware pointer area (maps phone to monitor)", 12))
        root.addView(pointer, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        displays = getSystemService<DisplayManager?>(DisplayManager::class.java!!)
        displays!!.registerDisplayListener(this, main)
        Shizuku.addBinderReceivedListenerSticky(shizukuReady!!)
        Shizuku.addRequestPermissionResultListener(permission!!)
    }

    private fun report(message: String?) {
        main.post({ status!!.setText(message) })
        android.util.Log.i("DexDroidSplitProbe", message!!)
    }

    private inner class Work(@JvmField val task: Runnable?, @JvmField val discard: Runnable?) :
        Runnable {

        public override fun run() {
            if (stopping) {
                discard!!.run()
                return
            }
            try {
                task!!.run()
            } catch (error: Exception) {
                report("Experiment failed: " + error!!)
                main.post({ stopExperiment(false) })
            }
        }
    }

    private fun execute(task: Runnable?, discard: Runnable? = Runnable {}) {
        if (stopping) {
            discard!!.run()
            return
        }
        try {
            worker.execute(Work(task, discard))
        } catch (full: RejectedExecutionException) {
            discard!!.run()
            report("Input queue full; stopping experiment instead of replaying stale input")
            stopExperiment(false)
        }
    }

    private fun start() {
        if (started || stopping || isFinishing() || !Shizuku.pingBinder()) return
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(401)
            return
        }
        val external = displays!!.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        var monitor: Display? = null
        for (display in external!!) if (!display!!.getName()!!.contains("DexDroid")) {
            monitor = display
            break
        }
        if (monitor == null) {
            report("Connect external monitor")
            return
        }
        started = true
        val target = monitor
        controller =
            object : ServiceConnection {
                public override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    (binder as SessionService.LocalBinder).get().pauseDesktop()
                    awaitProductStop(target, SystemClock.uptimeMillis() + 10000)
                }

                public override fun onServiceDisconnected(name: ComponentName?) {}
            }
        controllerBound =
            bindService(
                Intent(this, SessionService::class.java),
                controller!!,
                Context.BIND_AUTO_CREATE,
            )
        if (!controllerBound) report("Could not pause product desktop")
    }

    private fun awaitProductStop(target: Display?, deadline: Long) {
        if (stopping) return
        for (display in displays!!.getDisplays()!!) if (
            display!!.getName()!!.startsWith("DexDroid Desktop")
        ) {
            if (SystemClock.uptimeMillis() >= deadline) {
                report("Product desktop did not stop")
                return
            }
            main.postDelayed({ awaitProductStop(target, deadline) }, 50)
            return
        }
        showMonitor(target)
    }

    private fun showMonitor(target: Display?) {
        presentation =
            object : Presentation(this, target, R.style.DexDroidTheme) {
                public override fun onWindowFocusChanged(focused: Boolean) {
                    super.onWindowFocusChanged(focused)
                    if (focused) releasePending()
                }

                public override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    return routeKey(event) || super.dispatchKeyEvent(event)
                }
            }
        desktop = LinearLayout(presentation!!.getContext())
        desktop!!.setBackgroundColor(Color.BLACK)
        desktop!!.setOrientation(LinearLayout.HORIZONTAL)
        for (i in 0..1) {
            panes[i] = Pane(i, presentation!!.getContext())
            desktop!!.addView(panes[i]!!.frame, LinearLayout.LayoutParams(0, -1, .5f))
        }
        presentation!!.setContentView(desktop!!)
        presentation!!
            .getWindow()!!
            .addFlags(
                (WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            )
        presentation!!.setOnDismissListener({ dialog -> stopExperiment(false) })
        presentation!!.show()
        presentation!!.getWindow()!!.getDecorView().setSystemUiVisibility(5894)
        report("Creating two independent app displays")
    }

    fun paneAt(x: Float): Pane? {
        return if (contact >= 0) panes[contact]
        else panes[if (x < panes[0]!!.texture!!.getWidth()) 0 else 1]
    }

    fun select(index: Int) {
        selected = index
        for (i in 0..1) if (panes[i] != null) {
            val border = android.graphics.drawable.GradientDrawable()
            border.setColor(Color.TRANSPARENT)
            border.setStroke(
                style!!.dp(2f),
                if (i == selected) Color.WHITE else Color.rgb(45, 45, 45),
            )
            panes[i]!!.frame!!.setForeground(border)
        }
        if (panes[index] != null && panes[index]!!.id > 0)
            report(
                ("Selected " +
                    (if (index == 0) "left" else "right") +
                    " | display " +
                    panes[index]!!.id +
                    " | " +
                    panes[index]!!.width +
                    "x" +
                    panes[index]!!.height)
            )
    }

    fun setRatio(value: Float) {
        if (stopping || desktop == null || contact >= 0 || !heldKeys.isEmpty()) return
        ratio = value
        for (i in 0..1) {
            val layout = panes[i]!!.frame!!.getLayoutParams() as LinearLayout.LayoutParams?
            layout!!.weight = if (i == 0) ratio else 1 - ratio
            panes[i]!!.frame!!.setLayoutParams(layout)
        }
    }

    private fun choose(index: Int) {
        select(index)
        if (panes[index] == null) return
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        execute({
            val apps = getPackageManager()!!.queryIntentActivities(launcher, 0)
            apps.sortWith(
                Comparator.comparing<ResolveInfo?, String?>(
                    { app -> app!!.loadLabel(getPackageManager()!!).toString() },
                    String.CASE_INSENSITIVE_ORDER,
                )
            )
            val labels = arrayOfNulls<String?>(apps.size)
            for (i in apps.indices) labels[i] =
                apps.get(i)!!.loadLabel(getPackageManager()!!).toString()
            main.post({
                if (stopping) return@post
                AlertDialog.Builder(this)
                    .setTitle(if (index == 0) "Left app" else "Right app")!!
                    .setItems(
                        labels,
                        { dialog, which ->
                            val app = apps.get(which)!!.activityInfo
                            launch(
                                index,
                                ComponentName(app!!.packageName!!, app!!.name!!).flattenToString(),
                            )
                        },
                    )!!
                    .setNegativeButton("Close", null)!!
                    .show()
            })
        })
    }

    fun launch(index: Int, component: String?) {
        val pane = panes[index]
        if (pane == null || pane!!.id < 1) return
        execute({
            try {
                pane!!.bridge!!.launch(pane!!.id, component)
            } catch (error: RemoteException) {
                throw IllegalStateException(error)
            }

            report((if (index == 0) "Left: " else "Right: ") + component!!)
        })
    }

    public override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        return routeKey(event) || super.dispatchKeyEvent(event)
    }

    fun routeKey(event: KeyEvent?): Boolean {
        if (stopping || panes[selected] == null || panes[selected]!!.id < 1) return false
        if (
            (event!!.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP ||
                event!!.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN ||
                event!!.getKeyCode() == KeyEvent.KEYCODE_POWER)
        )
            return false
        // Soft IME text/composition is intentionally outside this raw hardware-key experiment.
        if (
            (!event!!.isFromSource(InputDevice.SOURCE_KEYBOARD) &&
                event!!.getKeyCode() != KeyEvent.KEYCODE_BACK)
        )
            return false
        var pane = heldKeys.get(event!!.getKeyCode())
        if (pane == null) pane = panes[selected]
        if (event!!.getAction() == KeyEvent.ACTION_DOWN) heldKeys.put(event!!.getKeyCode(), pane)
        if (event!!.getAction() == KeyEvent.ACTION_UP) heldKeys.remove(event!!.getKeyCode())
        pane!!.send(KeyEvent(event))
        return true
    }

    private fun keyCode(code: Int) {
        val pane = panes[selected]
        if (pane == null) return
        execute({
            try {
                pane!!.bridge!!.key(pane!!.id, code)
            } catch (e: RemoteException) {
                throw IllegalStateException(e)
            }
        })
    }

    fun stopExperiment(finish: Boolean) {
        if (stopping) return
        stopping = true
        heldKeys.clear()
        main.removeCallbacks(focusTimeout!!)
        waitingForFocus = false
        while (!pendingInput.isEmpty()) pendingInput.removeFirst()!!.discard()
        // Input operations check stopping at execution; cleanup remains ordered behind in-flight
        // work.
        while (true) {
            val pending = worker.getQueue()!!.poll() ?: break
            (pending as Work).discard!!.run()
        }
        worker.execute({
            for (pane in panes) {
                if (pane == null) continue
                try {
                    if (pane!!.bridge != null) pane!!.bridge!!.stop()
                } catch (error: Exception) {
                    report("Cleanup error: " + error!!)
                }

                if (pane!!.surface != null) pane!!.surface!!.release()
            }
            main.post({
                if (presentation != null) presentation!!.dismiss()
                for (pane in panes) {
                    if (pane == null) continue
                    if (pane!!.args != null && pane!!.connection != null)
                        Shizuku.unbindUserService(pane!!.args!!, pane!!.connection!!, true)
                    pane!!.id = -1
                    if (pane!!.retained != null) pane!!.retained!!.release()
                }
                if (finish) finish()
            })
        })
        worker.shutdown()
        report("Experiment stopped; reopen DexDroid to restore the normal desktop")
    }

    public override fun onDisplayAdded(id: Int) {
        start()
    }

    public override fun onDisplayChanged(id: Int) {}

    public override fun onDisplayRemoved(id: Int) {
        if (presentation != null && presentation!!.getDisplay()!!.getDisplayId() == id)
            stopExperiment(true)
    }

    public override fun onDestroy() {
        stopExperiment(false)
        displays!!.unregisterDisplayListener(this)
        Shizuku.removeBinderReceivedListener(shizukuReady!!)
        Shizuku.removeRequestPermissionResultListener(permission!!)
        if (controllerBound) unbindService(controller!!)
        super.onDestroy()
    }

    inner class Pane(@JvmField val index: Int, context: Context?) :
        TextureView.SurfaceTextureListener {
        @JvmField val texture: PaneTexture?
        @JvmField val frame: FrameLayout?
        @Volatile @JvmField var id: Int = -1
        @Volatile @JvmField var width: Int = 0
        @Volatile @JvmField var height: Int = 0
        @JvmField var surface: Surface? = null
        @JvmField var retained: SurfaceTexture? = null
        @JvmField var bridge: IDeskService? = null
        @JvmField var args: Shizuku.UserServiceArgs? = null
        @JvmField var connection: ServiceConnection? = null

        init {
            frame = FrameLayout(context!!)
            texture = PaneTexture(context)
            frame!!.addView(texture, FrameLayout.LayoutParams(-1, -1))
            texture!!.setSurfaceTextureListener(this)
            texture!!.setContentDescription(
                if (index == 0) "Left managed display" else "Right managed display"
            )
        }

        inner class PaneTexture(context: Context?) : TextureView(context!!) {

            public override fun performClick(): Boolean {
                super.performClick()
                select(index)
                return true
            }

            public override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick()
                return route(event)
            }

            public override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
                return route(event)
            }

            public override fun onHoverEvent(event: MotionEvent?): Boolean {
                return route(event)
            }
        }

        fun route(event: MotionEvent?): Boolean {
            if (stopping || id < 1) return true
            val action = event!!.getActionMasked()
            if (action == MotionEvent.ACTION_DOWN) {
                contact = index
                select(index)
                if (
                    (Build.VERSION.SDK_INT == 30 &&
                        !waitingForFocus &&
                        !presentation!!.getWindow()!!.getDecorView().hasWindowFocus())
                ) {
                    waitingForFocus = true
                    main.postDelayed(focusTimeout!!, 100)
                }
            }
            if (action == MotionEvent.ACTION_SCROLL) select(index)
            val target = if (contact >= 0) panes[contact] else this
            val copy = MotionEvent.obtain(event)
            if (target != this)
                copy!!.offsetLocation(
                    (frame!!.getLeft() - target!!.frame!!.getLeft()).toFloat(),
                    0f,
                )
            target!!.send(copy)
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) contact = -1
            return true
        }

        fun send(event: InputEvent?) {
            if (waitingForFocus) {
                val pending = PendingInput(this, event)
                if (pendingInput.size >= 32) {
                    pending.discard()
                    report("Legacy focus buffer full; stopping experiment")
                    stopExperiment(false)
                } else pendingInput.addLast(pending)
                return
            }
            val target = id
            execute(
                {
                    val data = Parcel.obtain()
                    val reply = Parcel.obtain()
                    try {
                        data.writeInterfaceToken(SplitProbeService.PROTOCOL)
                        data.writeInt(target)
                        data.writeInt(if (event is MotionEvent) 1 else 2)
                        event!!.writeToParcel(data, 0)
                        if (
                            !bridge!!.asBinder()!!.transact(SplitProbeService.INPUT, data, reply, 0)
                        )
                            throw IllegalStateException("Prototype transaction rejected")
                        reply.readException()
                    } catch (error: RemoteException) {
                        throw IllegalStateException(error)
                    } finally {
                        data.recycle()
                        reply.recycle()
                        if (event is MotionEvent) (event as MotionEvent).recycle()
                    }
                },
                { if (event is MotionEvent) (event as MotionEvent).recycle() },
            )
        }

        public override fun onSurfaceTextureAvailable(source: SurfaceTexture, w: Int, h: Int) {
            width = w
            height = h
            retained = source
            surface = Surface(source)
            args =
                Shizuku.UserServiceArgs(
                        ComponentName(
                            this@SplitProbeActivity,
                            if (index == 0) SplitProbeService.Left::class.java
                            else SplitProbeService.Right::class.java,
                        )
                    )
                    .daemon(false)!!
                    .processNameSuffix("split-probe-" + index)!!
                    .debuggable(true)!!
                    .version(1)
            connection =
                object : ServiceConnection {
                    public override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        if (stopping) {
                            Shizuku.unbindUserService(args!!, this, true)
                            return
                        }
                        bridge = IDeskService.Stub.asInterface(binder)
                        execute({
                            try {
                                id =
                                    bridge!!.create(
                                        surface,
                                        width,
                                        height,
                                        240,
                                        owner,
                                        object : IDesktopListener.Stub() {
                                            public override fun onProtectionState(
                                                display: Int,
                                                state: Int,
                                            ) {}

                                            public override fun onTaskState(
                                                display: Int,
                                                occupied: Boolean,
                                                component: String?,
                                                error: String?,
                                            ) {}
                                        },
                                    )
                                report(
                                    ("Pane " +
                                        index +
                                        " ready | display " +
                                        id +
                                        " | " +
                                        width +
                                        "x" +
                                        height)
                                )
                                val app =
                                    getIntent()!!.getStringExtra(
                                        if (index == 0) "left" else "right"
                                    )
                                if (app != null) main.post({ launch(index, app) })
                            } catch (error: RemoteException) {
                                throw IllegalStateException(error)
                            }
                        })
                    }

                    public override fun onServiceDisconnected(name: ComponentName?) {
                        report("Pane bridge disconnected")
                        stopExperiment(false)
                    }
                }
            Shizuku.bindUserService(args!!, connection!!)
        }

        public override fun onSurfaceTextureSizeChanged(source: SurfaceTexture, w: Int, h: Int) {
            width = w
            height = h
            if (id > 0)
                execute({
                    try {
                        bridge!!.resize(surface, w, h, 240)
                    } catch (error: RemoteException) {
                        throw IllegalStateException(error)
                    }
                })
        }

        public override fun onSurfaceTextureDestroyed(source: SurfaceTexture): Boolean {
            retained = source
            stopExperiment(false)
            return false
        }

        public override fun onSurfaceTextureUpdated(source: SurfaceTexture) {}
    }
}
