package dev.dexdroid

import android.app.ActivityOptions
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.view.Display
import android.view.View.*
import android.widget.*
import java.util.function.Consumer
import java.util.function.Supplier
import rikka.shizuku.Shizuku

/** User-initiated manager/download navigation; never starts a privileged server. */
internal class ShizukuSetup {
    enum class State
    private constructor(@JvmField val label: String?, @JvmField val action: String?) {
        MISSING("Not installed", "Install Shizuku"),
        STOPPED("Service stopped", "Open Shizuku"),
        PERMISSION("Authorization required", "Authorize DexDroid"),
        READY("Running; ready to connect", "Connect Shizuku"),
        CONNECTED("Connected", "Open Shizuku"),
    }

    enum class Destination {
        MANAGER,
        DOWNLOAD,
    }

    fun interface Navigator {
        fun open(destination: Destination?)
    }

    class Panel(
        private val context: Context?,
        private val source: Supplier<State?>?,
        retry: Runnable?,
        navigator: Navigator?,
        prefix: String?,
    ) {
        @JvmField val view: LinearLayout?
        private val stateText: TextView?
        private val feedback: TextView?
        private val primary: Button?
        private val managerRow: LinearLayout?

        constructor(
            context: Context?,
            session: SessionService,
            prefix: String?,
        ) : this(
            context,
            { session.shizukuState },
            Runnable({ session.retryDesktop() }),
            { destination -> open(context, destination) },
            prefix,
        ) {}

        init {
            view = LinearLayout(context)
            view!!.setOrientation(LinearLayout.VERTICAL)
            val style = ControllerStyle(context)
            stateText = style.label("", 16)
            stateText!!.setTag(prefix!! + "-shizuku-state")
            view!!.addView(stateText)
            val instructions =
                style.label(
                    ("1. Install Shizuku, then open it on the phone.\n\n" +
                        "2. On Android 11+, enable Developer options > Wireless debugging. " +
                        "In Shizuku, pair with the code shown by Android, then tap Start. " +
                        "You can also start it with computer ADB.\n\n" +
                        "3. Return to DexDroid and allow its Shizuku permission prompt. " +
                        "A requested desktop reconnects automatically. Use Connect if offered, or Start in status to resume a stopped desktop. If authorization was denied, manage DexDroid authorization in Shizuku.\n\n" +
                        "After a reboot, start Shizuku again. DexDroid cannot silently start " +
                        "this privileged service, pair, install apps or grant permission " +
                        "on a non-root phone."),
                    14,
                )
            instructions.setPadding(0, style.dp(14f), 0, style.dp(14f))
            view!!.addView(instructions)
            feedback = style.label("", 14)
            feedback!!.setTag(prefix!! + "-shizuku-feedback")
            feedback!!.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE)
            feedback!!.setVisibility(GONE)
            view!!.addView(feedback)
            val actions = LinearLayout(context)
            view!!.addView(actions)
            primary =
                style.button(
                    actions,
                    "",
                    {
                        val state = source!!.get()
                        if (state == State.PERMISSION || state == State.READY) retry!!.run()
                        else
                            launch(
                                navigator,
                                if (state == State.MISSING) Destination.DOWNLOAD
                                else Destination.MANAGER,
                            )
                    },
                )
            primary!!.setTag(prefix!! + "-shizuku-action")
            managerRow = LinearLayout(context)
            view!!.addView(managerRow)
            style
                .button(managerRow, "Open Shizuku", { launch(navigator, Destination.MANAGER) })
                .setTag(prefix!! + "-shizuku-manager")
            refresh()
        }

        private fun launch(navigator: Navigator?, destination: Destination?) {
            try {
                navigator!!.open(destination)
                feedback!!.setVisibility(GONE)
            } catch (e: RuntimeException) {
                feedback!!.setText(
                    if (destination == Destination.DOWNLOAD)
                        "Cannot open a browser. Visit https://shizuku.rikka.app/download/ on the phone."
                    else "Cannot open Shizuku. Install or enable its app on the phone, then retry."
                )
                feedback!!.setVisibility(VISIBLE)
            }
        }

        fun refresh() {
            val state = source!!.get()
            stateText!!.setText(context!!.getString(R.string.shizuku_status_format, state!!.label))
            primary!!.setText(state!!.action)
            managerRow!!.setVisibility(
                if (state == State.PERMISSION || state == State.READY) VISIBLE else GONE
            )
        }
    }

    companion object {
        @JvmField val PACKAGE: String = "moe.shizuku.privileged.api"
        @JvmField val DOWNLOAD: String = "https://shizuku.rikka.app/download/"

        @JvmStatic
        fun classify(
            installed: Boolean,
            running: Boolean,
            authorized: Boolean,
            connected: Boolean,
        ): State {
            if (!installed) return State.MISSING
            if (!running) return State.STOPPED
            if (!authorized) return State.PERMISSION
            return if (connected) State.CONNECTED else State.READY
        }

        @JvmStatic
        fun detect(context: Context, connected: Boolean): State? {
            try {
                context.getPackageManager()!!.getApplicationInfo(PACKAGE, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                return State.MISSING
            }

            return probe(
                {
                    val running = Shizuku.pingBinder()
                    classify(
                        true,
                        running,
                        (running &&
                            (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)),
                        connected,
                    )
                },
                { error ->
                    android.util.Log.w("DexDroid", "Shizuku status check unavailable", error)
                },
            )
        }

        @JvmStatic
        fun probe(read: Supplier<State?>?, report: Consumer<RuntimeException?>?): State? {
            try {
                return read!!.get()
            } catch (e: RuntimeException) {
                report!!.accept(e)
                return State.STOPPED
            }
        }

        @JvmStatic
        fun open(context: Context?, destination: Destination?) {
            val intent =
                if (destination == Destination.DOWNLOAD)
                    Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD))
                else context!!.getPackageManager()!!.getLaunchIntentForPackage(PACKAGE)
            if (intent == null) throw ActivityNotFoundException("Shizuku manager is unavailable")
            val options = ActivityOptions.makeBasic()
            options!!.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            context!!.startActivity(
                intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                options!!.toBundle(),
            )
        }
    }
}
