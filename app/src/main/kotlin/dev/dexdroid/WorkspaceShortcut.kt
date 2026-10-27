package dev.dexdroid

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** Service-owned saved record and bounded off-main eligibility checks. No external command API. */
internal class WorkspaceShortcut(private val session: SessionService) : AutoCloseable {
    private val prefs = session.getSharedPreferences("workspace_shortcut", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "DexDroid-workspace") }
    @Volatile private var epoch = 0L
    @Volatile private var closed = false
    private var working = false
    var message = "Save the current monitor workspace, then add its phone Home screen shortcut."
        private set

    var restoreNotice: String? = null
        private set

    var restoreResult: String? = null
        private set

    private val expireNotice = Runnable {
        restoreNotice = null
        if (!closed) session.notifyUi()
    }

    var saved: WorkspaceSnapshot? =
        try {
            prefs.getString("snapshot", null)?.let(WorkspaceSnapshot::decode)
        } catch (_: Exception) {
            message = "Saved workspace is invalid. Clear it and save the current workspace again."
            null
        }
        private set

    private val timeout = Runnable {
        cancel(
            "Workspace restoration timed out. Check the phone status and tap the shortcut to retry."
        )
    }
    private val restore: WorkspaceRestore =
        WorkspaceRestore(
            object : WorkspaceRestore.Driver {
                override fun validate(snapshot: WorkspaceSnapshot, done: (String?) -> Unit) {
                    if (working) {
                        done("App check is still running. Try again shortly.")
                        return
                    }
                    val token = epoch
                    working = true
                    worker.execute {
                        val error =
                            try {
                                listOfNotNull(snapshot.first, snapshot.second)
                                    .firstOrNull {
                                        !eligible(ComponentName.unflattenFromString(it.component)!!)
                                    }
                                    ?.let {
                                        "${it.label} is unavailable. Reinstall it or update the saved workspace."
                                    }
                            } catch (_: Exception) {
                                "Saved apps could not be checked. Try again, or update the saved workspace in Settings."
                            }
                        main.post {
                            working = false
                            if (!closed && epoch == token) done(error)
                            session.notifyUi()
                        }
                    }
                }

                override fun ready() = session.workspaceReady()

                override fun replace(done: (String?) -> Unit) = session.replaceWorkspace(done)

                override fun start(snapshot: WorkspaceSnapshot, done: (String?) -> Unit) =
                    session.startWorkspace(snapshot, done)

                override fun launch(
                    app: WorkspaceSnapshot.App,
                    pane: Int,
                    done: (String?) -> Unit,
                ) = session.launchWorkspaceApp(app, pane, done)

                override fun finish(snapshot: WorkspaceSnapshot, done: (String?) -> Unit) =
                    session.finishWorkspace(snapshot, done)

                override fun changed(message: String) {
                    restoreFeedback(message)
                    if (!restoreActive()) main.removeCallbacks(timeout)
                }
            }
        )

    private fun restoreActive() = restore.active

    private var names: Map<String, String> = emptyMap()
    private val cancelCatalog =
        session.appCatalog!!.observe { state ->
            if (!closed) {
                val next = linkedMapOf<String, String>()
                for (app in state?.apps.orEmpty()) {
                    val component = app?.component ?: continue
                    val label =
                        app.name
                            .orEmpty()
                            .filterNot { it.isISOControl() }
                            .take(200)
                            .ifBlank { component.packageName }
                    next[component.flattenToString()] = label
                    next.putIfAbsent(component.packageName, label)
                }
                names = next
                session.notifyUi()
            }
        }

    fun currentSummary(snapshot: WorkspaceSnapshot): String {
        fun app(value: WorkspaceSnapshot.App?): WorkspaceSnapshot.App? =
            value?.let {
                it.copy(
                    label =
                        names[it.component] ?: names[it.component.substringBefore('/')] ?: it.label
                )
            }
        return snapshot.copy(first = app(snapshot.first), second = app(snapshot.second)).summary()
    }

    val active: Boolean
        get() = restore.active

    val busy: Boolean
        get() = working || active

    fun activationGuard(): () -> Boolean {
        val token = epoch
        return { !closed && token == epoch }
    }

    fun sessionChanged() {
        if (!closed) restore.ready()
    }

    fun save() {
        if (busy) return
        val capture = session.workspaceSnapshot()
        if (capture == null) {
            feedback("Start a stable workspace with at least one app before saving.")
            return
        }
        cancel()
        val token = ++epoch
        val currentOwnership = session.workspaceCaptureGuard()
        working = true
        feedback("Checking current workspace apps…")
        session.confirmWorkspaceCapture(capture) { confirmed ->
            if (closed || token != epoch || confirmed == null) {
                working = false
                if (!closed && token == epoch)
                    feedback(
                        "Current workspace could not be confirmed. Wait for apps to finish opening and try Save again."
                    )
                else if (!closed) session.notifyUi()
                return@confirmWorkspaceCapture
            }
            worker.execute {
                var snapshot: WorkspaceSnapshot? = null
                val error =
                    try {
                        fun resolve(app: WorkspaceSnapshot.App?): WorkspaceSnapshot.App? {
                            if (app == null) return null
                            val observed = ComponentName.unflattenFromString(app.component)!!
                            val candidates = launcherEntries(observed.packageName)
                            val entry =
                                candidates.firstOrNull { it == observed }
                                    ?: candidates.singleOrNull()
                                    ?: throw IllegalArgumentException(
                                        "App launch entry is unavailable or ambiguous. Open its normal launcher entry and try saving again."
                                    )
                            val label =
                                session.packageManager
                                    .getActivityInfo(entry, 0)
                                    .loadLabel(session.packageManager)
                                    .toString()
                                    .filterNot { it.isISOControl() }
                                    .take(200)
                                    .ifBlank { entry.packageName }
                            return WorkspaceSnapshot.App(entry.flattenToString(), label)
                        }
                        snapshot =
                            confirmed.copy(
                                first = resolve(confirmed.first),
                                second = resolve(confirmed.second),
                            )
                        null
                    } catch (error: Exception) {
                        error.message ?: "Current apps could not be saved."
                    }
                main.post {
                    working = false
                    if (closed) return@post
                    if (token != epoch) {
                        session.notifyUi()
                        return@post
                    }
                    if (error != null) {
                        feedback(error)
                        return@post
                    }
                    if (!currentOwnership() || capture != session.workspaceSnapshot()) {
                        feedback(
                            "Workspace changed while checking apps. Save again when it is stable."
                        )
                        return@post
                    }
                    saved = snapshot
                    prefs.edit().putString("snapshot", snapshot!!.encode()).apply()
                    feedback(
                        updatePinned()
                            ?: "Workspace saved. Add it to the phone Home screen or update it after arranging your apps."
                    )
                }
            }
        }
    }

    fun clear() {
        cancel()
        saved = null
        prefs.edit().remove("snapshot").apply()
        feedback(
            updatePinned()
                ?: "Saved workspace cleared. Remove any remaining icon from your phone Home screen manually."
        )
    }

    fun request() {
        if (closed || active) return
        session.clearLaunchFeedback()
        val snapshot = saved
        if (snapshot == null) {
            restoreFeedback("No workspace saved. Open Settings → Workspace shortcut to save one.")
            return
        }
        epoch++
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, 60000)
        restore.request(snapshot)
    }

    fun cancel(reason: String = "Workspace restoration canceled") {
        epoch++
        if (working && !restore.active && !closed)
            feedback("Workspace changed while checking apps. Save again when it is stable.")
        main.removeCallbacks(timeout)
        restore.cancel(reason)
        session.cancelWorkspaceStartup()
    }

    private fun feedback(value: String) {
        message = value
        session.notifyUi()
    }

    private fun restoreFeedback(value: String) {
        message = value
        restoreResult = value
        session.status = value
        restoreNotice =
            when {
                value.startsWith("Checking") || value.startsWith("Rechecking") ->
                    "Checking saved apps…"
                value.startsWith("Waiting") -> "Waiting for monitor/Shizuku…"
                value.startsWith("Restoring") -> "Restoring workspace…"
                value == "Workspace restored" -> value
                value.startsWith("Workspace partly") -> "Workspace partly restored"
                value.startsWith("Workspace not changed") ||
                    value.startsWith("No workspace saved") -> "Workspace not restored"
                value.contains("canceled", ignoreCase = true) -> "Restoration canceled"
                else -> "Workspace needs attention"
            }
        main.removeCallbacks(expireNotice)
        if (!closed && !active) main.postDelayed(expireNotice, 10000)
        if (!closed) session.notifyUi()
    }

    private fun launcherEntries(packageName: String): List<ComponentName> =
        session.packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(packageName),
                0,
            )
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .distinct()
            .filter(::eligible)

    private fun eligible(component: ComponentName): Boolean {
        if (component.packageName == session.packageName) return false
        val pm = session.packageManager
        val info =
            try {
                pm.getActivityInfo(component, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                return false
            }
        return info.exported &&
            info.enabled &&
            info.applicationInfo.enabled &&
            pm.getComponentEnabledSetting(component) !in
                listOf(
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
                ) &&
            pm.getApplicationEnabledSetting(component.packageName) !in
                listOf(
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
                ) &&
            (info.permission == null ||
                session.checkSelfPermission(info.permission) ==
                    PackageManager.PERMISSION_GRANTED) &&
            pm.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setPackage(component.packageName),
                    0,
                )
                .any {
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name) == component
                }
    }

    private fun info(): ShortcutInfo =
        ShortcutInfo.Builder(session, ID)
            .setActivity(ComponentName(session, MainActivity::class.java))
            .setShortLabel("Restore workspace")
            .setLongLabel("Restore workspace")
            .setIcon(Icon.createWithResource(session, R.mipmap.ic_launcher))
            .setIntent(Intent(session, WorkspaceShortcutActivity::class.java).setAction(RESTORE))
            .build()

    private fun updatePinned(): String? =
        try {
            val manager = session.getSystemService(ShortcutManager::class.java)
            if (manager != null && !manager.updateShortcuts(listOf(info())))
                "Workspace saved state updated. The launcher label could not be refreshed; use Add to phone Home screen again."
            else null
        } catch (_: RuntimeException) {
            "Workspace saved state updated. The phone launcher could not be refreshed; try Add to phone Home screen again."
        }

    fun pin(activity: Activity) {
        if (saved == null) {
            feedback("Save a workspace first.")
            return
        }
        val result =
            try {
                val manager = activity.getSystemService(ShortcutManager::class.java)
                if (manager == null || !manager.isRequestPinShortcutSupported)
                    "This phone launcher does not support adding shortcuts. Your workspace is still saved."
                else if (manager.requestPinShortcut(info(), null))
                    "Shortcut requested. Confirm on the phone if prompted, then check all Home screen pages. If no icon appears, check shortcut permissions in phone App info and try again. Your workspace stays saved."
                else "The phone launcher did not accept the request. Your workspace is still saved."
            } catch (_: RuntimeException) {
                "Could not request the shortcut. Try from DexDroid on the phone. Your workspace is still saved."
            }
        feedback(result)
    }

    fun pinOpenFailed() {
        feedback(
            "Could not open shortcut setup on the phone. Open DexDroid > Settings > Workspace shortcut on the phone and try again."
        )
    }

    override fun close() {
        closed = true
        cancelCatalog.run()
        cancel()
        main.removeCallbacks(expireNotice)
        worker.shutdown()
    }

    companion object {
        const val ID = "saved-workspace"
        const val RESTORE = "dev.dexdroid.action.RESTORE_WORKSPACE"
        const val PIN = "dev.dexdroid.action.PIN_WORKSPACE"
    }
}
