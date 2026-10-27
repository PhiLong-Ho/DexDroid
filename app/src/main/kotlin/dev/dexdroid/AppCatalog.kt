package dev.dexdroid

import android.content.*
import android.content.pm.*
import android.graphics.drawable.Drawable
import android.os.*
import java.util.*
import java.util.concurrent.*
import java.util.function.Consumer

class AppCatalog(
    context: Context,
    private val source: Source?,
    private val log: Consumer<String?>?,
) : AutoCloseable {

    private val context: Context?
    private val main: Handler = Handler(Looper.getMainLooper()!!)
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val listeners: MutableSet<Consumer<State?>?> = HashSet<Consumer<State?>?>()
    private var cache: List<Entry?>? = emptyList<Entry?>()
    private var revision: Int = 0
    private var attempted: Int = -1
    private var running: Boolean = false
    private var closed: Boolean = false
    private var error: String? = ""
    private val packages: BroadcastReceiver =
        object : BroadcastReceiver() {
            public override fun onReceive(ignored: Context?, intent: Intent?) {
                invalidate()
            }
        }

    open class Entry(
        @JvmField val name: String?,
        @JvmField val component: ComponentName?,
        i: Drawable,
    ) {
        @JvmField val icon: Drawable?

        init {
            if (i.getConstantState() == null) {
                // Snapshot non-cloneable icons off the UI thread, so each density's
                // row can receive independent bounds without mutating the shared cache.
                val bitmap =
                    android.graphics.Bitmap.createBitmap(
                        128,
                        128,
                        android.graphics.Bitmap.Config.ARGB_8888,
                    )
                i.setBounds(0, 0, 128, 128)
                i.draw(android.graphics.Canvas(bitmap))
                icon =
                    android.graphics.drawable.BitmapDrawable(
                        android.content.res.Resources.getSystem(),
                        bitmap,
                    )
            } else icon = i
        }
    }

    class State(
        @JvmField val apps: List<Entry?>?,
        @JvmField val loading: Boolean,
        @JvmField val error: String?,
    )

    fun interface Source {
        @Throws(Exception::class) fun load(): List<Entry?>?
    }

    constructor(
        context: Context?,
        log: Consumer<String?>?,
    ) : this(context!!, { installed(context!!.getApplicationContext()) }, log) {}

    init {
        this.context = context.getApplicationContext()
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_PACKAGE_ADDED)
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED)
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED)
        filter.addDataScheme("package")
        if (Build.VERSION.SDK_INT >= 33)
            this.context!!.registerReceiver(packages, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerLegacy(filter)
        load()
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerLegacy(filter: IntentFilter?) {
        context!!.registerReceiver(packages, filter)
    }

    fun observe(listener: Consumer<State?>?): Runnable {
        if (closed) return Runnable {}
        listeners.add(listener)
        listener!!.accept(state())
        load()
        return Runnable { listeners.remove(listener) }
    }

    fun invalidate() {
        if (closed) return
        revision++
        load()
    }

    /** Main-thread cached label; accepting a launch must never query PackageManager. */
    fun label(component: ComponentName): String =
        cache
            .orEmpty()
            .firstOrNull { it?.component == component }
            ?.name
            ?.filterNot { it.isISOControl() }
            ?.take(200)
            ?.takeIf { it.isNotBlank() } ?: component.packageName

    private fun state(): State {
        return State(cache, running, error)
    }

    private fun publish() {
        val current = state()
        for (listener in ArrayList<Consumer<State?>?>(listeners)) listener!!.accept(current)
    }

    private fun load() {
        if (closed || running || attempted == revision) return
        running = true
        error = ""
        val requested = revision
        publish()
        worker.execute({
            val start = SystemClock.elapsedRealtime()
            var loaded: List<Entry?>? = null
            var failure: String? = ""
            try {
                loaded = Collections.unmodifiableList<Entry?>(ArrayList<Entry?>(source!!.load()!!))
            } catch (failed: Exception) {
                failure = failed!!.toString()
            }

            val result = loaded
            val problem = failure
            val duration = SystemClock.elapsedRealtime() - start
            main.post({
                if (closed) return@post
                running = false
                if (revision != requested) {
                    load()
                    return@post
                }
                attempted = requested
                error = problem
                if (result != null) cache = result
                log!!.accept(
                    ("App catalog " +
                        (if (problem!!.isEmpty()) "ready: " + cache!!.size + " entries"
                        else "failed") +
                        " in " +
                        duration +
                        " ms (worker)")
                )
                publish()
            })
        })
    }

    public override fun close() {
        if (closed) return
        closed = true
        listeners.clear()
        context!!.unregisterReceiver(packages)
        worker.shutdownNow()
    }

    companion object {

        @JvmStatic
        fun installed(context: Context?): List<Entry?> {
            if (Looper.myLooper() == Looper.getMainLooper())
                throw IllegalStateException("App discovery must run off the UI thread")
            val pm = context!!.getPackageManager()
            val apps = ArrayList<Entry?>()
            val seen = HashSet<String?>()
            for (r in
                pm!!.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                    0,
                )) {
                val pkg = r!!.activityInfo!!.packageName
                if (pkg == context!!.getPackageName() || !seen.add(pkg)) continue
                apps.add(
                    Entry(
                        r!!.loadLabel(pm!!).toString(),
                        ComponentName(pkg!!, r!!.activityInfo!!.name!!),
                        r!!.loadIcon(pm)!!,
                    )
                )
            }
            if (BuildConfig.DEBUG && "debug" == BuildConfig.BUILD_TYPE)
                apps.add(
                    Entry(
                        "DexDroid input test",
                        ComponentName(
                            context!!.getPackageName()!!,
                            "dev.dexdroid.InputTestActivity",
                        ),
                        context!!.getApplicationInfo()!!.loadIcon(pm)!!,
                    )
                )
            val favorites =
                HashSet<String?>(
                    Arrays.asList<String?>(
                        "dev.dexdroid",
                        "dev.portraitdesk",
                        "org.mozilla.firefox",
                        "app.revanced.android.youtube",
                        "eu.kanade.tachiyomi",
                        "com.android.chrome",
                    )
                )
            apps.sortWith({ a, b ->
                val order =
                    java.lang.Boolean.compare(
                        favorites.contains(b!!.component!!.getPackageName()),
                        favorites.contains(a!!.component!!.getPackageName()),
                    )
                if (order != 0) order else a!!.name!!.compareTo(b!!.name!!, ignoreCase = true)
            })
            return apps
        }
    }
}
