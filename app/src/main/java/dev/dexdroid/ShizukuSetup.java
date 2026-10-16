package dev.dexdroid;

import static android.view.View.*;

import android.app.ActivityOptions;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.Display;
import android.widget.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import rikka.shizuku.Shizuku;

/** User-initiated manager/download navigation; never starts a privileged server. */
final class ShizukuSetup {
    static final String PACKAGE = "moe.shizuku.privileged.api";
    static final String DOWNLOAD = "https://shizuku.rikka.app/download/";

    enum State {
        MISSING("Not installed", "Install Shizuku"),
        STOPPED("Service stopped", "Open Shizuku"),
        PERMISSION("Authorization required", "Authorize DexDroid"),
        READY("Running; ready to connect", "Connect Shizuku"),
        CONNECTED("Connected", "Open Shizuku");
        final String label, action;

        State(String label, String action) {
            this.label = label;
            this.action = action;
        }
    }

    static State classify(
            boolean installed, boolean running, boolean authorized, boolean connected) {
        if (!installed) return State.MISSING;
        if (!running) return State.STOPPED;
        if (!authorized) return State.PERMISSION;
        return connected ? State.CONNECTED : State.READY;
    }

    static State detect(Context context, boolean connected) {
        try {
            context.getPackageManager().getApplicationInfo(PACKAGE, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return State.MISSING;
        }
        return probe(
                () -> {
                    boolean running = Shizuku.pingBinder();
                    return classify(
                            true,
                            running,
                            running
                                    && Shizuku.checkSelfPermission()
                                            == PackageManager.PERMISSION_GRANTED,
                            connected);
                },
                error -> android.util.Log.w("DexDroid", "Shizuku status check unavailable", error));
    }

    static State probe(Supplier<State> read, Consumer<RuntimeException> report) {
        try {
            return read.get();
        } catch (RuntimeException e) {
            report.accept(e);
            return State.STOPPED;
        }
    }

    enum Destination {
        MANAGER,
        DOWNLOAD
    }

    interface Navigator {
        void open(Destination destination);
    }

    static void open(Context context, Destination destination) {
        Intent intent =
                destination == Destination.DOWNLOAD
                        ? new Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD))
                        : context.getPackageManager().getLaunchIntentForPackage(PACKAGE);
        if (intent == null) throw new ActivityNotFoundException("Shizuku manager is unavailable");
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle());
    }

    static final class Panel {
        final LinearLayout view;
        private final Context context;
        private final Supplier<State> source;
        private final TextView stateText, feedback;
        private final Button primary;
        private final LinearLayout managerRow;

        Panel(Context context, SessionService session, String prefix) {
            this(
                    context,
                    () -> session.shizukuState,
                    session::retryDesktop,
                    destination -> open(context, destination),
                    prefix);
        }

        Panel(
                Context context,
                Supplier<State> source,
                Runnable retry,
                Navigator navigator,
                String prefix) {
            this.context = context;
            this.source = source;
            view = new LinearLayout(context);
            view.setOrientation(LinearLayout.VERTICAL);
            ControllerStyle style = new ControllerStyle(context);
            stateText = style.label("", 16);
            stateText.setTag(prefix + "-shizuku-state");
            view.addView(stateText);
            TextView instructions =
                    style.label(
                            "1. Install Shizuku, then open it on the phone.\n\n"
                                    + "2. On Android 11+, enable Developer options > Wireless debugging. "
                                    + "In Shizuku, pair with the code shown by Android, then tap Start. "
                                    + "You can also start it with computer ADB.\n\n"
                                    + "3. Return to DexDroid and allow its Shizuku permission prompt. "
                                    + "A requested desktop reconnects automatically. Use Connect if offered, or Start in status to resume a stopped desktop. If authorization was denied, manage DexDroid authorization in Shizuku.\n\n"
                                    + "After a reboot, start Shizuku again. DexDroid cannot silently start "
                                    + "this privileged service, pair, install apps or grant permission "
                                    + "on a non-root phone.",
                            14);
            instructions.setPadding(0, style.dp(14), 0, style.dp(14));
            view.addView(instructions);
            feedback = style.label("", 14);
            feedback.setTag(prefix + "-shizuku-feedback");
            feedback.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
            feedback.setVisibility(GONE);
            view.addView(feedback);
            LinearLayout actions = new LinearLayout(context);
            view.addView(actions);
            primary =
                    style.button(
                            actions,
                            "",
                            () -> {
                                State state = source.get();
                                if (state == State.PERMISSION || state == State.READY) retry.run();
                                else
                                    launch(
                                            navigator,
                                            state == State.MISSING
                                                    ? Destination.DOWNLOAD
                                                    : Destination.MANAGER);
                            });
            primary.setTag(prefix + "-shizuku-action");
            managerRow = new LinearLayout(context);
            view.addView(managerRow);
            style.button(managerRow, "Open Shizuku", () -> launch(navigator, Destination.MANAGER))
                    .setTag(prefix + "-shizuku-manager");
            refresh();
        }

        private void launch(Navigator navigator, Destination destination) {
            try {
                navigator.open(destination);
                feedback.setVisibility(GONE);
            } catch (RuntimeException e) {
                feedback.setText(
                        destination == Destination.DOWNLOAD
                                ? "Cannot open a browser. Visit https://shizuku.rikka.app/download/ on the phone."
                                : "Cannot open Shizuku. Install or enable its app on the phone, then retry.");
                feedback.setVisibility(VISIBLE);
            }
        }

        void refresh() {
            State state = source.get();
            stateText.setText(context.getString(R.string.shizuku_status_format, state.label));
            primary.setText(state.action);
            managerRow.setVisibility(
                    state == State.PERMISSION || state == State.READY ? VISIBLE : GONE);
        }
    }
}
