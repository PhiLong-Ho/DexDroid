package dev.dexdroid

import android.content.ComponentName
import android.os.*
import java.lang.reflect.*
import java.util.*

/** Shell-only, event-driven occupancy of one virtual desktop. Never reparents tasks. */
@android.annotation.SuppressLint("PrivateApi") // Runs only in the authorized shell helper.
class DesktopTasks
@Throws(Exception::class)
constructor(private val display: Int, private val listener: Listener?) : AutoCloseable {
    private val thread: HandlerThread = HandlerThread("DexDroid-task-state")
    private val handler: Handler?
    private val manager: Any?
    private val callback: Any?
    private val callbackType: Class<*>?
    private val roots: Method?
    private val events: MutableSet<Int?> = HashSet<Int?>()
    @Volatile private var closed: Boolean = false
    private var pending: Boolean = false
    private var last: Boolean? = null
    private var lastError: String? = null
    private var lastComponent: String? = null

    fun interface Listener {
        fun changed(occupied: Boolean, component: String?, error: String?)
    }

    init {
        if (android.os.Process.myUid() != 2000)
            throw SecurityException("Desktop task observation requires the authorized shell")
        manager =
            Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
        callbackType = Class.forName(DESCRIPTOR)
        val stub = Class.forName(DESCRIPTOR + "\$Stub")
        for (name in
            arrayOf<String?>(
                "onTaskStackChanged",
                "onTaskCreated",
                "onTaskRemoved",
                "onTaskMovedToFront",
                "onTaskDisplayChanged",
                "onTaskFocusChanged",
                "onTaskVisibilityChanged",
            )) {
            try {
                val code = stub.getDeclaredField("TRANSACTION_" + name!!)
                code.setAccessible(true)
                events.add(code.getInt(null))
            } catch (absentOnThisVersion: NoSuchFieldException) {
                // Stack-change notifications are available on every supported platform.
            }
        }
        if (events.isEmpty()) throw IllegalStateException("Task events unavailable")
        val service = Class.forName("android.app.IActivityTaskManager")
        roots =
            service.getMethod(
                if (Build.VERSION.SDK_INT >= 31) "getAllRootTaskInfos" else "getAllStackInfos"
            )
        thread.start()
        handler = Handler(thread.getLooper()!!)
        val endpoint =
            object : Binder() {
                @Throws(RemoteException::class)
                protected override fun onTransact(
                    code: Int,
                    data: Parcel,
                    reply: Parcel?,
                    flags: Int,
                ): Boolean {
                    if (code == IBinder.INTERFACE_TRANSACTION) {
                        if (reply != null) reply!!.writeString(DESCRIPTOR)
                        return true
                    }
                    if (
                        code >= IBinder.FIRST_CALL_TRANSACTION &&
                            code <= IBinder.LAST_CALL_TRANSACTION
                    ) {
                        data.enforceInterface(DESCRIPTOR)
                        // All platform listener methods are one-way void notifications. Their
                        // payload is deliberately unused; always query authoritative current
                        // state.
                        if (events.contains(code)) refresh()
                        return true
                    }
                    return super.onTransact(code, data, reply, flags)
                }
            }
        callback =
            Proxy.newProxyInstance(
                callbackType!!.getClassLoader(),
                arrayOf<Class<*>?>(callbackType),
                { proxy, method, args ->
                    when (method!!.getName()) {
                        "asBinder" -> endpoint
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args!![0]
                        "toString" -> "DexDroid desktop task listener"
                        else -> {
                            refresh()
                            null
                        }
                    }
                },
            )
        try {
            service.getMethod("registerTaskStackListener", callbackType).invoke(manager, callback)
            refresh()
        } catch (error: Exception) {
            closed = true
            thread.quitSafely()
            throw error
        }
    }

    /** Fresh owned-display observation; independent of coalesced listener delivery. */
    fun snapshot(): Bundle {
        check(!closed) { "Task observer is closed" }
        val value = roots!!.invoke(manager)
        if (!(value is List<*>)) throw IllegalStateException("Invalid task snapshot")
        var occupied = false
        var component: String? = ""
        var visibleChosen = false
        for (root in (value as List<*>?)!!) {
            val type = root!!.javaClass
            if (type!!.getField("displayId").getInt(root) != display) continue
            val top = type!!.getField("topActivity").get(root) as ComponentName?
            val tasks =
                type!!
                    .getField(if (Build.VERSION.SDK_INT >= 31) "childTaskIds" else "taskIds")
                    .get(root) as IntArray?
            occupied = occupied or (top != null && tasks != null && tasks!!.size > 0)
            if (top != null && tasks != null && tasks!!.size > 0) {
                val visible =
                    try {
                        type!!.getField("visible").getBoolean(root)
                    } catch (absent: NoSuchFieldException) {
                        false
                    }

                if (component!!.isEmpty() || (visible && !visibleChosen)) {
                    component = top!!.flattenToString()
                    visibleChosen = visible
                }
            }
        }
        return Bundle().apply {
            putBoolean("occupied", occupied)
            putString("component", component)
            putString("error", "")
        }
    }

    @Synchronized
    fun refresh() {
        if (closed || pending) return
        pending = true
        handler!!.postDelayed(
            {
                synchronized(this) { pending = false }
                if (closed) return@postDelayed
                try {
                    val snapshot = snapshot()
                    val occupied = snapshot.getBoolean("occupied")
                    val component = snapshot.getString("component", "")
                    if (
                        (!closed &&
                            ((last == null ||
                                last != occupied ||
                                lastError != null ||
                                component != lastComponent)))
                    ) {
                        last = occupied
                        lastComponent = component
                        lastError = null
                        listener!!.changed(occupied, component, "")
                    }
                } catch (error: Throwable) {
                    val message = error!!.toString()
                    if (!closed && message != lastError) {
                        lastError = message
                        listener!!.changed(false, "", message)
                    }
                }
            },
            150,
        )
    }

    fun refreshCurrent() {
        handler!!.post({
            if (closed) return@post
            last = null
            refresh()
        })
    }

    public override fun close() {
        closed = true
        handler!!.removeCallbacksAndMessages(null)
        try {
            Class.forName("android.app.IActivityTaskManager")
                .getMethod("unregisterTaskStackListener", callbackType)
                .invoke(manager, callback)
        } catch (error: Exception) {
            android.util.Log.w("DexDroid", "Task listener unregister failed", error)
        }

        thread.quitSafely()
    }

    companion object {

        private val DESCRIPTOR: String = "android.app.ITaskStackListener"

        // DisplayManager.release returns before ActivityTaskManager processes removal.
        // Stop workers must observe that handoff before restoring the phone task.
        @Throws(Exception::class)
        @JvmStatic
        fun awaitRemoval(retiredDisplay: Int) {
            val manager =
                Class.forName("android.app.ActivityTaskManager")
                    .getMethod("getService")
                    .invoke(null)
            val snapshot =
                Class.forName("android.app.IActivityTaskManager")
                    .getMethod(
                        if (Build.VERSION.SDK_INT >= 31) "getAllRootTaskInfos"
                        else "getAllStackInfos"
                    )
            val deadline = SystemClock.uptimeMillis() + 2000
            do {
                val value = snapshot.invoke(manager)
                if (!(value is List<*>))
                    throw IllegalStateException("Invalid cleanup task snapshot")
                var retained = false
                for (root in (value as List<*>?)!!) retained =
                    retained or
                        (root!!.javaClass.getField("displayId").getInt(root) == retiredDisplay)
                if (!retained) {
                    awaitWindowTransactions()
                    return
                }
                if (SystemClock.uptimeMillis() >= deadline)
                    throw IllegalStateException(
                        "Android task handoff after display removal timed out"
                    )
                Thread.sleep(25)
            } while (true)
        }

        // Task metadata changes before WindowManager finishes the reparent animation
        // and commits its input windows. Focusing during that interval can strand the
        // phone at a black, unfocused window on JoyUI. Use the platform transaction
        // barrier instead of a timer or repeated foreground requests. Runs on shell IPC.
        @Throws(Exception::class)
        private fun awaitWindowTransactions() {
            // Shizuku's app_process helper has no application-global initialization.
            // API 37 WindowManagerGlobal consults ApplicationSharedMemory before lookup.
            val binder =
                Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String::class.java)
                    .invoke(null, "window") as IBinder?
            if (binder == null) throw IllegalStateException("Window service unavailable")
            val window =
                Class.forName("android.view.IWindowManager\$Stub")
                    .getMethod("asInterface", IBinder::class.java)
                    .invoke(null, binder)
            if (window == null) throw IllegalStateException("Window service interface unavailable")
            val type = Class.forName("android.view.IWindowManager")
            try {
                type
                    .getMethod("syncInputTransactions", Boolean::class.javaPrimitiveType)
                    .invoke(window, true)
            } catch (android11: NoSuchMethodException) {
                type.getMethod("syncInputTransactions").invoke(window)
            }
        }

        // A normal AppTask.moveToFront may silently fail background-start policy after
        // display removal foregrounds its former app. Only focus an existing phone
        // controller task; never launch an Activity or trust an arbitrary task ID.
        @Throws(Exception::class)
        @JvmStatic
        fun focusController(taskId: Int): Boolean {
            if (android.os.Process.myUid() != 2000)
                throw SecurityException("Controller return requires the authorized shell")
            val manager =
                Class.forName("android.app.ActivityTaskManager")
                    .getMethod("getService")
                    .invoke(null)
            val service = Class.forName("android.app.IActivityTaskManager")
            val value =
                service
                    .getMethod(
                        if (Build.VERSION.SDK_INT >= 31) "getAllRootTaskInfos"
                        else "getAllStackInfos"
                    )
                    .invoke(manager)
            if (!(value is List<*>)) throw IllegalStateException("Invalid controller task snapshot")
            val controller = ComponentName("dev.dexdroid", "dev.dexdroid.MainActivity")
            for (root in (value as List<*>?)!!) {
                val type = root!!.javaClass
                if (
                    (type!!.getField("displayId").getInt(root) != 0 ||
                        controller != type!!.getField("topActivity").get(root))
                )
                    continue
                val tasks =
                    type!!
                        .getField(if (Build.VERSION.SDK_INT >= 31) "childTaskIds" else "taskIds")
                        .get(root) as IntArray?
                val names =
                    type!!
                        .getField(
                            if (Build.VERSION.SDK_INT >= 31) "childTaskNames" else "taskNames"
                        )
                        .get(root) as Array<String?>?
                if (
                    (tasks == null ||
                        tasks!!.size != 1 ||
                        tasks!![0] != taskId ||
                        names == null ||
                        names!!.size != 1 ||
                        controller != ComponentName.unflattenFromString(names!![0]!!))
                )
                    continue
                service
                    .getMethod(
                        "moveTaskToFront",
                        Class.forName("android.app.IApplicationThread"),
                        String::class.java,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Bundle::class.java,
                    )
                    .invoke(
                        manager,
                        null,
                        "com.android.shell",
                        taskId,
                        0,
                        android.app.ActivityOptions.makeBasic()!!.setLaunchDisplayId(0)!!.toBundle(),
                    )
                awaitWindowTransactions()
                return true
            }
            return false
        }
    }
}
