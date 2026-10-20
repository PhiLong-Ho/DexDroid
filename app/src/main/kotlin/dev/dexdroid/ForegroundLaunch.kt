package dev.dexdroid

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Native completion for a confirmed background task; never reparents a departed task. */
internal object ForegroundLaunch {
    data class Target(val root: Int, val task: Int, val display: Int)

    interface Operation : AutoCloseable {
        fun start(): Int

        fun awaitCompletion(): Boolean
    }

    fun eligible(roots: List<DisplayTasks.Root?>, display: Int): Target? {
        if (display <= 0) return null
        val matching = roots.filterNotNull().filter { it.display == display && it.exactComponent }
        if (matching.size != 1) return null
        val root = matching[0]
        if (
            !root.matches ||
                root.tasks != 1 ||
                root.nativeTaskRows != 1 ||
                !root.nativeUserZero ||
                !root.fullscreen ||
                root.nativeVisible != false ||
                roots.count { it?.id == root.id } != 1
        )
            return null
        return Target(root.id, root.taskId, display)
    }

    fun unchanged(target: Target, roots: List<DisplayTasks.Root?>): Boolean {
        val matching =
            roots.filterNotNull().filter { it.display == target.display && it.exactComponent }
        if (matching.size != 1) return false
        val root = matching[0]
        return root.id == target.root &&
            root.taskId == target.task &&
            root.tasks == 1 &&
            root.nativeTaskRows == 1 &&
            root.nativeUserZero &&
            root.matches &&
            root.fullscreen &&
            root.nativeVisible == true &&
            roots.count { it?.id == root.id } == 1
    }

    fun complete(operation: Operation, placement: () -> Boolean) {
        try {
            operation.use {
                val result = it.start()
                if (result != 0 && result != 2 && result != 3)
                    throw IllegalStateException("Android rejected foreground launch ($result)")
                if (!it.awaitCompletion())
                    throw IllegalStateException(
                        "App launch completion unavailable. Choose the app again."
                    )
                if (!placement())
                    throw IllegalStateException(
                        "App placement changed during launch. Phone task left in place."
                    )
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        }
    }

    /** Missing platform capability permits the original route only before any start RPC. */
    @android.annotation.SuppressLint("PrivateApi")
    fun prepare(display: Int, component: String): Operation? {
        if (android.os.Process.myUid() != 2000)
            throw SecurityException("Native foreground launch requires Android shell")
        try {
            val threadType = Class.forName("android.app.ActivityThread")
            val thread =
                threadType.getMethod("currentActivityThread").invoke(null)
                    ?: throw IllegalStateException("Shell activity context unavailable")
            val context =
                (threadType.getMethod("getSystemContext").invoke(thread) as Context)
                    .createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY)
            if (
                context.checkSelfPermission("android.permission.START_TASKS_FROM_RECENTS") !=
                    PackageManager.PERMISSION_GRANTED
            )
                return null
            val started = Class.forName("android.app.ActivityOptions\$OnAnimationStartedListener")
            val finished = Class.forName("android.app.ActivityOptions\$OnAnimationFinishedListener")
            val factory =
                ActivityOptions::class
                    .java
                    .getMethod(
                        "makeCustomTaskAnimation",
                        Context::class.java,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Handler::class.java,
                        started,
                        finished,
                    )
            val windowing =
                ActivityOptions::class
                    .java
                    .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
            val contract = Class.forName("android.app.IActivityManager")
            val start =
                contract.getMethod(
                    "startActivityAsUserWithFeature",
                    Class.forName("android.app.IApplicationThread"),
                    String::class.java,
                    String::class.java,
                    Intent::class.java,
                    String::class.java,
                    IBinder::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Class.forName("android.app.ProfilerInfo"),
                    Bundle::class.java,
                    Int::class.javaPrimitiveType,
                )
            val service =
                Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)
                    ?: throw IllegalStateException("Shell activity manager unavailable")
            val entry =
                ComponentName.unflattenFromString(component)
                    ?: throw IllegalArgumentException("Invalid foreground component")
            return NativeOperation(
                context,
                display,
                entry,
                finished,
                factory,
                windowing,
                service,
                start,
            )
        } catch (missing: ClassNotFoundException) {
            return null
        } catch (missing: NoSuchMethodException) {
            return null
        }
    }

    private class NativeOperation(
        private val context: Context,
        private val display: Int,
        private val component: ComponentName,
        private val listenerType: Class<*>,
        private val factory: Method,
        private val windowing: Method,
        private val service: Any,
        private val startMethod: Method,
    ) : Operation {
        private val finished = CountDownLatch(1)
        private val callbacks = HandlerThread("DexDroid-launch-finish")
        private var started = false

        override fun start(): Int {
            check(!started) { "Foreground launch already submitted" }
            started = true
            callbacks.start()
            val handler = Handler(callbacks.looper)
            val listener =
                Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) {
                    proxy,
                    method,
                    args ->
                    when (method.name) {
                        "onAnimationFinished" -> {
                            finished.countDown()
                            null
                        }
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args?.get(0)
                        "toString" -> "DexDroid launch completion"
                        else -> throw IllegalStateException("Unknown native completion method")
                    }
                }
            val options =
                factory.invoke(null, context, 0, 0, handler, null, listener) as ActivityOptions
            options.setLaunchDisplayId(display)
            windowing.invoke(options, 1)
            val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return startMethod.invoke(
                service,
                null,
                "com.android.shell",
                null,
                intent,
                null,
                null,
                null,
                0,
                0,
                null,
                options.toBundle(),
                -2,
            ) as Int
        }

        override fun awaitCompletion(): Boolean = finished.await(2, TimeUnit.SECONDS)

        override fun close() {
            if (!started) return
            callbacks.quitSafely()
            callbacks.join(1000)
            check(!callbacks.isAlive) { "Foreground callback thread did not stop" }
        }
    }
}
