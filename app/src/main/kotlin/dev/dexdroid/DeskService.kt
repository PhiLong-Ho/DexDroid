package dev.dexdroid

import android.content.Context
import android.hardware.display.*
import android.os.*
import android.view.*
import java.io.*
import java.lang.reflect.Method
import java.util.*
import java.util.regex.*

/** Runs as Android shell through Shizuku, never as root. */
open class DeskService : IDeskService.Stub() {
    private val audioDelegate = lazy { AudioRouter() }
    private val audioRouter by audioDelegate

    private fun primaryAudio(): AudioRouter {
        check(javaClass == DeskService::class.java) { "Audio belongs to the primary helper" }
        return audioRouter
    }

    override fun audioBegin(epoch: Long, owner: IBinder): Bundle =
        primaryAudio().begin(epoch, owner).bundle()

    override fun audioQuery(epoch: Long): Bundle = primaryAudio().query(epoch).bundle()

    override fun audioSelect(epoch: Long, key: String): Bundle =
        primaryAudio().select(epoch, key).bundle()

    override fun audioEnd(epoch: Long): Bundle = primaryAudio().end(epoch).bundle()

    private var display: VirtualDisplay? = null
    private var displaySurface: Surface? = null
    private val outputName: String? = "DexDroid Desktop " + android.os.Process.myPid()
    private var taskObserver: DesktopTasks? = null
    private var legacyInput: LegacyInputWindow? = null
    private var modernInput: ModernInputWindow? = null
    private var desktopListener: IDesktopListener? = null
    private var inputPending: Boolean = false
    private var blockedContact: Boolean = false
    private val blockedKeys: MutableSet<Long?> = LinkedHashSet<Long?>()
    private var protectionObserver: ProtectedWindowObserver? = null
    private var input: Any? = null
    private var inject: Method? = null
    private var setId: Method? = null
    private var down: Long = 0
    private var owner: IBinder? = null
    private val ownerDeath: IBinder.DeathRecipient = IBinder.DeathRecipient { stop() }

    @Throws(RemoteException::class)
    public override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == rikka.shizuku.ShizukuApiConstants.USER_SERVICE_TRANSACTION_destroy) {
            if (audioDelegate.isInitialized()) audioRouter.shutdown()
            stop()
            System.exit(0)
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    @Throws(Exception::class)
    private fun init() {
        if (input != null) return
        val cls =
            try {
                Class.forName("android.hardware.input.InputManagerGlobal")
            } catch (legacy: ClassNotFoundException) {
                Class.forName("android.hardware.input.InputManager")
            }

        input = cls!!.getMethod("getInstance").invoke(null)
        inject =
            cls!!.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType,
            )
        setId = MotionEvent::class.java!!.getMethod("setDisplayId", Int::class.javaPrimitiveType)
    }

    private fun check(id: Int) {
        if (display == null || display!!.getDisplay()!!.getDisplayId() != id)
            throw IllegalStateException("Desktop disconnected")
    }

    // SUPPORTS_TOUCH (64) is a hidden Android 11 flag required by the shell display.
    @android.annotation.SuppressLint("WrongConstant")
    @Synchronized
    public override fun create(
        surface: Surface?,
        w: Int,
        h: Int,
        dpi: Int,
        client: IBinder?,
        listener: IDesktopListener?,
    ): Int {
        try {
            stop()
            displaySurface = surface
            owner = client
            owner!!.linkToDeath(ownerDeath!!, 0)
            if (Looper.myLooper() == null) Looper.prepare()
            val cls = Class.forName("android.app.ActivityThread")
            val thread = cls.getMethod("systemMain").invoke(null)
            val context =
                (cls.getMethod("getSystemContext").invoke(thread) as Context).createPackageContext(
                    "com.android.shell",
                    Context.CONTEXT_IGNORE_SECURITY,
                )
            display =
                (context!!.getSystemService(Context.DISPLAY_SERVICE!!) as DisplayManager)
                    .createVirtualDisplay(
                        outputName!!,
                        w,
                        h,
                        dpi,
                        surface,
                        (DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                            64 or
                            (if (Build.VERSION.SDK_INT >= 31) TRUSTED_DISPLAY else 0) or
                            (if (Build.VERSION.SDK_INT >= 34) OWN_FOCUS else 0)),
                    )
            if (display == null) throw IllegalStateException("Display creation rejected")
            init()
            val id = display!!.getDisplay()!!.getDisplayId()
            desktopListener = listener
            if (Build.VERSION.SDK_INT == 30)
                legacyInput =
                    LegacyInputWindow(context, id, LegacyInputWindow.Command { this.command(*it) })
            else
                modernInput =
                    ModernInputWindow(context, id, LegacyInputWindow.Command { this.command(*it) })
            if (BuildConfig.DEBUG && modernInput != null) {
                try {
                    modernInput!!.diagnostic =
                        Class.forName("dev.dexdroid.ModernInputDiagnostics")
                            .getMethod("attach", String::class.java, Int::class.javaPrimitiveType)
                            .invoke(null, javaClass.name, id) as ModernInputWindow.Diagnostic?
                } catch (_: Throwable) {
                    /* Optional debug capture cannot affect desktop startup. */
                }
            }
            protectionObserver =
                ProtectedWindowObserver(
                    id,
                    { state ->
                        try {
                            listener!!.onProtectionState(id, state!!.ordinal)
                        } catch (disconnected: RemoteException) {
                            android.util.Log.w("DexDroid", "Protection observer disconnected")
                        }
                    },
                )
            taskObserver =
                DesktopTasks(
                    id,
                    { occupied, component, error ->
                        val observer = protectionObserver
                        if (observer != null && error!!.isEmpty()) observer!!.occupied(occupied)
                        try {
                            listener!!.onTaskState(id, occupied, component, error)
                        } catch (disconnected: RemoteException) {
                            android.util.Log.w(
                                "DexDroid",
                                "Desktop observer disconnected",
                                disconnected,
                            )
                        }
                    },
                )
            return id
        } catch (e: Throwable) {
            stop()
            throw IllegalStateException(e)
        }
    }

    @Synchronized
    public override fun resize(surface: Surface?, w: Int, h: Int, dpi: Int) {
        val old = displaySurface
        try {
            if (display == null) throw IllegalStateException("Start desktop first")
            display!!.setSurface(null)
            if (Build.VERSION.SDK_INT == 30) awaitOutputDetached()
            display!!.resize(w, h, dpi)
            display!!.setSurface(surface)
            if (legacyInput != null) legacyInput!!.invalidate()
            displaySurface = surface
            if (old != null && old !== surface) old!!.release()
        } catch (error: Exception) {
            android.util.Log.w("DexDroid", "Output resize cause", error)
            throw IllegalStateException("Output resize failed", error)
        } finally {
            if (surface !== displaySurface) surface!!.release()
        }
    }

    @Throws(Exception::class)
    private fun awaitOutputDetached() {
        val deadline = SystemClock.uptimeMillis() + 1500
        do {
            val compositor = command("/system/bin/dumpsys", "-t", "1", "SurfaceFlinger")
            if (
                (!compositor.contains("Composition Display State:") ||
                    compositor.contains("DUMP TIMEOUT"))
            )
                throw IllegalStateException("Compositor state unavailable")
            if (!compositor.contains("[\"" + outputName + "\"]")) return
            Thread.sleep(12)
        } while (SystemClock.uptimeMillis() < deadline)
        throw IllegalStateException("Compositor output did not detach")
    }

    @Synchronized
    public override fun touch(id: Int, action: Int, xs: FloatArray?, ys: FloatArray?) {
        try {
            check(id)
            init()
            if (xs!!.size < 1 || xs!!.size > 2 || xs!!.size != ys!!.size)
                throw IllegalArgumentException("Invalid contacts")
            val now = SystemClock.uptimeMillis()
            if (action == MotionEvent.ACTION_DOWN) down = now
            val props = arrayOfNulls<MotionEvent.PointerProperties?>(xs!!.size)
            val coords = arrayOfNulls<MotionEvent.PointerCoords?>(xs!!.size)
            for (i in xs!!.indices) {
                props[i] = MotionEvent.PointerProperties()
                props[i]!!.id = i
                props[i]!!.toolType = MotionEvent.TOOL_TYPE_FINGER
                coords[i] = MotionEvent.PointerCoords()
                coords[i]!!.x = xs!![i]
                coords[i]!!.y = ys!![i]
                coords[i]!!.pressure = 1f
                coords[i]!!.size = 1f
            }
            val event =
                MotionEvent.obtain(
                    down,
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
                setId!!.invoke(event, id)
                if ((!(inject!!.invoke(input, event, 0) as Boolean))!!)
                    throw IllegalStateException(
                        "Input rejected: check USB debugging security permission"
                    )
            } finally {
                event!!.recycle()
            }
        } catch (e: Throwable) {
            throw IllegalStateException(e)
        }
    }

    @Synchronized
    public override fun scroll(id: Int, x: Float, y: Float, horizontal: Float, vertical: Float) {
        try {
            check(id)
            init()
            val now = SystemClock.uptimeMillis()
            val p = MotionEvent.PointerProperties()
            p.id = 0
            p.toolType = MotionEvent.TOOL_TYPE_MOUSE
            val c = MotionEvent.PointerCoords()
            c.x = x
            c.y = y
            c.setAxisValue(MotionEvent.AXIS_HSCROLL, horizontal)
            c.setAxisValue(MotionEvent.AXIS_VSCROLL, vertical)
            val event =
                MotionEvent.obtain(
                    now,
                    now,
                    MotionEvent.ACTION_SCROLL,
                    1,
                    arrayOf<MotionEvent.PointerProperties?>(p),
                    arrayOf<MotionEvent.PointerCoords?>(c),
                    0,
                    0,
                    1f,
                    1f,
                    0,
                    0,
                    InputDevice.SOURCE_MOUSE,
                    0,
                )
            try {
                setId!!.invoke(event, id)
                if ((!(inject!!.invoke(input, event, 0) as Boolean))!!)
                    throw IllegalStateException("Scroll injection rejected")
            } finally {
                event!!.recycle()
            }
        } catch (e: Throwable) {
            throw IllegalStateException(e)
        }
    }

    @Synchronized
    public override fun relayTouch(id: Int, event: MotionEvent?) {
        try {
            check(id)
            init()
            if (
                (event == null ||
                    event!!.getPointerCount() < 1 ||
                    event!!.getPointerCount() > 16 ||
                    !event!!.isFromSource(InputDevice.SOURCE_TOUCHSCREEN))
            )
                throw IllegalArgumentException("Invalid monitor touch")
            setId!!.invoke(event, id)
            if ((!(inject!!.invoke(input, event, 0) as Boolean))!!)
                throw IllegalStateException("Monitor touch injection rejected")
        } catch (e: Throwable) {
            throw IllegalStateException(e)
        } finally {
            if (event != null) event!!.recycle()
        }
    }

    @Throws(Exception::class)
    private fun command(vararg arguments: String?): String {
        val process = ProcessBuilder(*arguments!!).redirectErrorStream(true).start()
        val result = StringBuilder()
        BufferedReader(InputStreamReader(process.getInputStream()!!))
            .use({ reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    result.append(line).append('\n')
                }
            })
        val failure = ShellCommandResult.failure(process.waitFor(), result.toString())
        if (failure != null) throw IllegalStateException(failure)
        return result.toString()
    }

    @Synchronized
    public override fun relayMotion(id: Int, event: MotionEvent?) {
        var copy: MotionEvent? = null
        try {
            check(id)
            init()
            if (
                (event == null ||
                    ((!event!!.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) &&
                        !event!!.isFromSource(InputDevice.SOURCE_MOUSE) &&
                        !event!!.isFromSource(InputDevice.SOURCE_TOUCHPAD))))
            )
                throw IllegalArgumentException("Unsupported input source")
            val action = event!!.getActionMasked()
            if (action == MotionEvent.ACTION_DOWN) {
                blockedContact = !readyForInput(id, false)
            } else if (action == MotionEvent.ACTION_SCROLL && !readyForInput(id, false)) return
            if (blockedContact) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                    blockedContact = false
                return
            }
            copy = InputEvents.rebase(event!!)
            setId!!.invoke(copy, id)
            if (
                (!(inject!!.invoke(input, copy, if (action == MotionEvent.ACTION_DOWN) 1 else 0)
                    as Boolean))!!
            ) {
                if (legacyInput != null && action == MotionEvent.ACTION_DOWN) {
                    blockedContact = true
                    legacyInput!!.invalidate()
                    pendingInput(id)
                    return
                }
                if (modernInput != null && action == MotionEvent.ACTION_DOWN) {
                    val tuple =
                        if (modernInput!!.diagnostic != null) {
                            "action=${copy!!.action} down=${copy!!.downTime} time=${copy!!.eventTime} " +
                                "source=${copy!!.source} device=${copy!!.deviceId} display=$id accepted=false " +
                                (0 until copy!!.pointerCount).joinToString(";") {
                                    "${copy!!.getPointerId(it)}:${copy!!.getX(it)},${copy!!.getY(it)}"
                                }
                        } else null
                    val component = modernInput!!.pendingComponent(tuple)
                    if (component != null) {
                        // Modern dispatch can retain verifier state even when no
                        // window accepted DOWN. Release that exact injected contact
                        // before suppressing its remaining physical events.
                        check(id)
                        copy!!.setAction(MotionEvent.ACTION_CANCEL)
                        modernInput!!.diagnosticEvent("cancel-attempt nanos=${System.nanoTime()}")
                        val cancelled =
                            try {
                                inject!!.invoke(input, copy, 0) as Boolean
                            } catch (error: Throwable) {
                                modernInput!!.diagnosticEvent(
                                    "cancel-error=${error.javaClass.name}"
                                )
                                throw error
                            }
                        modernInput!!.diagnosticEvent(
                            "cancel-result=$cancelled nanos=${System.nanoTime()}"
                        )
                        if (!cancelled)
                            throw IllegalStateException("Pending pointer cancellation rejected")
                        blockedContact = true
                        pendingInput(id, component)
                        return
                    }
                }
                throw IllegalStateException("Pointer input rejected")
            }
            if (modernInput != null && action == MotionEvent.ACTION_DOWN && inputPending) {
                inputPending = false
                if (taskObserver != null) taskObserver!!.refreshCurrent()
            }
        } catch (error: Exception) {
            throw IllegalStateException(error)
        } finally {
            if (copy != null) copy!!.recycle()
            if (event != null) event!!.recycle()
            modernInput?.flushDiagnostic()
        }
    }

    @Throws(RemoteException::class)
    private fun pendingInput(id: Int, component: String? = legacyInput!!.component()) {
        inputPending = true
        if (desktopListener != null)
            desktopListener!!.onTaskState(id, true, component, "App is still opening. Try again.")
    }

    @Throws(Exception::class)
    private fun readyForInput(id: Int, keyboard: Boolean): Boolean {
        if (legacyInput == null) return true
        if (!legacyInput!!.ready(keyboard)) {
            if (legacyInput!!.hasTask()) pendingInput(id)
            else if (taskObserver != null) taskObserver!!.refresh()
            return false
        }
        if (inputPending && desktopListener != null)
            desktopListener!!.onTaskState(id, true, legacyInput!!.component(), "")
        inputPending = false
        return true
    }

    @Synchronized
    public override fun relayKey(id: Int, event: KeyEvent?) {
        fun trace(stage: String) {
            if (BuildConfig.DEBUG && KeyDiagnostics.synthetic(legacyInput?.component(), event))
                KeyDiagnostics.record(
                    "shell-key",
                    "display=$id stage=$stage ${KeyDiagnostics.tuple(event!!)}",
                )
        }
        try {
            check(id)
            init()
            if (event == null || !event!!.isFromSource(InputDevice.SOURCE_KEYBOARD))
                throw IllegalArgumentException("Unsupported key source")
            val key =
                (event!!.getDeviceId().toLong() shl 32) or
                    (event!!.getKeyCode().toLong() and 0xffffffffL)
            if (event!!.getAction() == KeyEvent.ACTION_DOWN && event!!.getRepeatCount() == 0) {
                blockedKeys.remove(key)
                if (!readyForInput(id, true)) {
                    if (blockedKeys.size >= 64) throw IllegalStateException("Too many pending keys")
                    blockedKeys.add(key)
                    trace("pending-down-rejected")
                    return
                }
            }
            if (blockedKeys.contains(key)) {
                if (event!!.getAction() == KeyEvent.ACTION_UP) blockedKeys.remove(key)
                trace("blocked-key")
                return
            }
            // Android 11 cancels held native keys itself when global focus leaves
            // this display. Do not deliver a second cancelled UP from a phone menu.
            if (
                legacyInput != null &&
                    event!!.getAction() == KeyEvent.ACTION_UP &&
                    (event!!.getFlags() and KeyEvent.FLAG_CANCELED) != 0
            ) {
                val focused = legacyInput!!.focused()
                trace("cancel-focus-result-$focused")
                if (!focused) {
                    trace("cancel-suppressed")
                    return
                }
            }
            val copy = KeyEvent(event)
            KeyEvent::class
                .java!!
                .getMethod("setDisplayId", Int::class.javaPrimitiveType)
                .invoke(copy, id)
            trace("inject-start")
            val accepted = inject!!.invoke(input, copy, 0) as Boolean
            trace("inject-submitted-$accepted")
            if (!accepted) throw IllegalStateException("Keyboard input rejected")
        } catch (error: Exception) {
            trace("exception-" + error.javaClass.simpleName)
            throw IllegalStateException(error)
        }
    }

    @Synchronized
    public override fun closeApps(id: Int) {
        try {
            check(id)
            for (root in appStacks("dev.dexdroid/.InputTestActivity")) if (root!!.display == id)
                command("/system/bin/am", "stack", "remove", Integer.toString(root!!.id))
            for (root in appStacks("dev.dexdroid/.InputTestActivity")) if (
                root!!.display == id && root!!.tasks > 0
            )
                throw IllegalStateException("App tasks remain on the selected display")
            if (taskObserver != null) taskObserver!!.refresh()
        } catch (error: Exception) {
            throw IllegalStateException(error)
        }
    }

    @Throws(Exception::class)
    private fun appStacks(component: String?): List<DisplayTasks.Root?> {
        return DisplayTasks.parse(command("/system/bin/am", "stack", "list"), component!!)
    }

    // Shell-only Android 11 fallback; modern verified fullscreen avoids this removed API.
    @android.annotation.SuppressLint("PrivateApi")
    @Throws(Exception::class)
    private fun fullscreen(stack: DisplayTasks.Root) {
        // Modern am start --windowingMode 1 already establishes fullscreen. The legacy
        // forcing method was removed; only invoke it when placement is not confirmed.
        if (stack.fullscreen) return
        val manager =
            Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
        Class.forName("android.app.IActivityTaskManager")
            .getMethod(
                "setTaskWindowingMode",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
            .invoke(manager, stack.taskId, 1, true)
    }

    @Synchronized
    public override fun launch(id: Int, component: String?): String {
        try {
            check(id)
            if (!component!!.matches(("[A-Za-z0-9._/$]+").toRegex()))
                throw IllegalArgumentException("Invalid app component")
            if (Build.VERSION.SDK_INT >= 31) {
                val target = ForegroundLaunch.eligible(appStacks(component), id)
                if (target != null) {
                    val capturedOwner = owner
                    if (capturedOwner == null || !capturedOwner.isBinderAlive)
                        throw IllegalStateException("Desktop owner disconnected before app launch")
                    val operation = ForegroundLaunch.prepare(id, component)
                    if (operation != null) {
                        ForegroundLaunch.complete(operation) {
                            check(id)
                            owner === capturedOwner &&
                                capturedOwner.isBinderAlive &&
                                ForegroundLaunch.unchanged(target, appStacks(component))
                        }
                        if (taskObserver != null) taskObserver!!.refresh()
                        return "Native foreground launch completed: " + component
                    }
                    android.util.Log.w(
                        "DexDroid",
                        "Native foreground completion unavailable; original launch route retained",
                    )
                }
            }
            var result: String =
                command(
                    "/system/bin/am",
                    "start",
                    "--display",
                    (id).toString(),
                    "--windowingMode",
                    "1",
                    "-n",
                    component,
                )
            var misplaced: DisplayTasks.Root? = null
            for (stack in appStacks(component)) {
                if (!stack!!.matches) continue
                if (stack!!.display == id) {
                    fullscreen(stack!!)
                    if (taskObserver != null) taskObserver!!.refresh()
                    return result
                }
                if (misplaced == null || stack!!.visible) misplaced = stack
            }
            if (misplaced == null)
                throw IllegalStateException(
                    "Android did not place this app on the desktop. It may not support secondary displays."
                )
            if (misplaced!!.tasks != 1)
                throw IllegalStateException(
                    "App is in a shared task stack; cannot safely move it to the desktop."
                )
            command(
                "/system/bin/am",
                "display",
                "move-stack",
                (misplaced!!.id).toString(),
                (id).toString(),
            )
            result +=
                command(
                    "/system/bin/am",
                    "start",
                    "--display",
                    (id).toString(),
                    "--windowingMode",
                    "1",
                    "-n",
                    component,
                )
            for (stack in appStacks(component)) if (stack!!.matches && stack!!.display == id) {
                fullscreen(stack!!)
                if (taskObserver != null) taskObserver!!.refresh()
                return result
            }
            throw IllegalStateException("Android rejected moving this app to the desktop.")
        } catch (e: Throwable) {
            throw IllegalStateException(e)
        }
    }

    @Synchronized
    public override fun key(id: Int, code: Int) {
        try {
            check(id)
            init()
            val now = SystemClock.uptimeMillis()
            for (action in intArrayOf(0, 1)) {
                val event = KeyEvent(now, now, action, code, 0)
                KeyEvent::class
                    .java!!
                    .getMethod("setDisplayId", Int::class.javaPrimitiveType)
                    .invoke(event, id)
                if ((!(inject!!.invoke(input, event, 0) as Boolean))!!)
                    throw IllegalStateException("Key injection rejected")
            }
        } catch (e: Throwable) {
            throw IllegalStateException(e)
        }
    }

    @Synchronized
    public override fun focusController(taskId: Int): Boolean {
        if (display != null)
            throw IllegalStateException("Desktop restarted before controller return")
        try {
            return DesktopTasks.focusController(taskId)
        } catch (error: Exception) {
            throw IllegalStateException("Controller focus return failed: " + error!!, error)
        }
    }

    @Synchronized
    public override fun stop() {
        var cleanupFailure: RuntimeException? = null
        legacyInput = null
        modernInput = null
        desktopListener = null
        blockedContact = false
        inputPending = blockedContact
        blockedKeys.clear()
        if (protectionObserver != null) {
            protectionObserver!!.close()
            protectionObserver = null
        }
        if (taskObserver != null) {
            taskObserver!!.close()
            taskObserver = null
        }
        if (display != null) {
            if (Build.VERSION.SDK_INT >= 36) {
                val target = display!!.getDisplay()!!.getDisplayId()
                try {
                    for (root in appStacks("dev.dexdroid/.InputTestActivity")) if (
                        root!!.display == target
                    )
                        command("/system/bin/am", "stack", "remove", Integer.toString(root!!.id))
                    // Confirm removal before DisplayController loses its layout. Modern
                    // SystemUI can otherwise receive late compat info for a removed display.
                    for (root in appStacks("dev.dexdroid/.InputTestActivity")) if (
                        root!!.display == target
                    )
                        throw IllegalStateException("Desktop task cleanup incomplete")
                } catch (error: Exception) {
                    android.util.Log.w(
                        "DexDroid",
                        "Task cleanup before display release failed",
                        error,
                    )
                }
            }
            val retired = display!!.getDisplay()!!.getDisplayId()
            display!!.release()
            display = null
            try {
                DesktopTasks.awaitRemoval(retired)
            } catch (error: Exception) {
                android.util.Log.w("DexDroid", "Retired display handoff confirmation failed", error)
                cleanupFailure = IllegalStateException("Desktop task handoff failed", error)
            }
        }
        if (displaySurface != null) {
            displaySurface!!.release()
            displaySurface = null
        }
        if (owner != null) {
            owner!!.unlinkToDeath(ownerDeath!!, 0)
            owner = null
        }
        if (cleanupFailure != null) throw cleanupFailure
    }

    companion object {
        // Hidden framework flag, stable since Android 12; checked by DisplayManagerService.
        // Only the authorized shell creates this display using ADD_TRUSTED_DISPLAY.
        private val TRUSTED_DISPLAY: Int = 1 shl 10
        // Framework per-display focus flag, available from Android 14.
        private val OWN_FOCUS: Int = 1 shl 14
    }
}
