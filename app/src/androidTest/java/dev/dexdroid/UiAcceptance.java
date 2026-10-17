package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.Button;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Real controller interactions and external-app navigation, not resource snapshots. */
public class UiAcceptance {
    private final Instrumentation runner;

    public UiAcceptance(Instrumentation runner) {
        this.runner = runner;
    }

    private Context getTargetContext() {
        return runner.getTargetContext();
    }

    private void runOnMainSync(Runnable action) {
        runner.runOnMainSync(action);
    }

    private SessionService session;
    private Activity controller;
    private ServiceConnection binding;
    private boolean bound;
    private final StringBuilder results = new StringBuilder();

    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        results.append("PASS ").append(message).append('\n');
    }

    private void await(BooleanSupplier predicate, String message) throws Exception {
        long end = SystemClock.uptimeMillis() + 10000;
        while (!predicate.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(50);
        check(predicate.getAsBoolean(), message);
    }

    private Button find(View root, String text) {
        if (root instanceof Button && text.contentEquals(((Button) root).getText()))
            return (Button) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = find(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Button button(String text) {
        Button found = find(controller.getWindow().getDecorView(), text);
        check(found != null && found.isShown(), text + " visible");
        return found;
    }

    private int[] position(View view) {
        int[] out = new int[2];
        view.getLocationOnScreen(out);
        return out;
    }

    public String run() throws Exception {
        boolean[] originalExpansion = {false, false, false};
        try {
            controller = DeviceTestSupport.startPhone(runner);
            CountDownLatch connected = new CountDownLatch(1);
            binding =
                    new ServiceConnection() {
                        public void onServiceConnected(ComponentName n, IBinder b) {
                            session = ((SessionService.LocalBinder) b).get();
                            connected.countDown();
                        }

                        public void onServiceDisconnected(ComponentName n) {}
                    };
            bound =
                    getTargetContext()
                            .bindService(
                                    new Intent(getTargetContext(), SessionService.class),
                                    binding,
                                    Context.BIND_AUTO_CREATE);
            check(connected.await(5, TimeUnit.SECONDS), "controller service bound");
            await(() -> session.id >= 0 && !session.busy, "automatic desktop ready");
            runOnMainSync(
                    () -> {
                        originalExpansion[2] = true;
                        originalExpansion[0] = session.prefs.contains("statusExpanded");
                        originalExpansion[1] = session.prefs.getBoolean("statusExpanded", false);
                        session.prefs.edit().remove("statusExpanded").apply();
                    });
            await(
                    () ->
                            controller
                                            .getWindow()
                                            .getDecorView()
                                            .findViewWithTag("phone-status-details")
                                            .getVisibility()
                                    == View.GONE,
                    "status defaults to compact summary");
            int compactHeight =
                    controller
                            .getWindow()
                            .getDecorView()
                            .findViewWithTag("controller-status-card")
                            .getHeight();
            int compactPad =
                    controller.getWindow().getDecorView().findViewWithTag("touchpad").getHeight();
            runOnMainSync(
                    () -> {
                        View target =
                                controller
                                        .getWindow()
                                        .getDecorView()
                                        .findViewWithTag("phone-status-expand");
                        check(
                                target.performAccessibilityAction(
                                        android.view.accessibility.AccessibilityNodeInfo
                                                .ACTION_CLICK,
                                        null),
                                "native accessibility action expands status");
                    });
            await(
                    () ->
                            controller
                                            .getWindow()
                                            .getDecorView()
                                            .findViewWithTag("controller-status-card")
                                            .getHeight()
                                    > compactHeight,
                    "expansion reveals details and increases status height");
            check(
                    controller.getWindow().getDecorView().findViewWithTag("touchpad").getHeight()
                            < compactPad,
                    "collapse gives usable height back to touchpad");
            runOnMainSync(
                    () -> {
                        View target =
                                controller
                                        .getWindow()
                                        .getDecorView()
                                        .findViewWithTag("phone-status-expand");
                        StatusDisclosureAssertions.inspectCue(
                                controller.getWindow().getDecorView(), "phone", true);
                        check(
                                "Expanded".contentEquals(target.getStateDescription()),
                                "expanded status exposes accessible state");
                        check(
                                target.requestFocusFromTouch(),
                                "keyboard navigation can focus status body");
                        check(
                                target.dispatchKeyEvent(
                                                new KeyEvent(
                                                        KeyEvent.ACTION_DOWN,
                                                        KeyEvent.KEYCODE_ENTER))
                                        && target.dispatchKeyEvent(
                                                new KeyEvent(
                                                        KeyEvent.ACTION_UP,
                                                        KeyEvent.KEYCODE_ENTER)),
                                "native keyboard Enter activates status disclosure");
                    });
            await(
                    () ->
                            controller
                                            .getWindow()
                                            .getDecorView()
                                            .findViewWithTag("controller-status-card")
                                            .getHeight()
                                    == compactHeight,
                    "collapse restores compact geometry");
            check(!session.prefs.getBoolean("statusExpanded", true), "collapsed choice persisted");
            runOnMainSync(
                    () -> {
                        Button apps = button("Apps"),
                                rotate = button("Rotate"),
                                layout = button("Layout");
                        View root = controller.getWindow().getDecorView();
                        check(
                                MenuTextAssertions.check(root) > 0,
                                "phone text encoding and glyph availability");
                        int[] a = position(apps), r = position(rotate), l = position(layout);
                        check(
                                a[1] == r[1] && r[1] == l[1] && a[0] < r[0] && r[0] < l[0],
                                "one primary row Apps Rotate Layout");
                        check(root.findViewWithTag("phone-row-2") == null, "no second primary row");
                        for (Button control : new Button[] {rotate, layout})
                            check(
                                    Math.abs(control.getWidth() - apps.getWidth()) <= 1
                                            && control.getHeight() == apps.getHeight(),
                                    "primary controls equal size");
                        View stop = root.findViewWithTag("phone-stop"),
                                expansion = root.findViewWithTag("phone-status-expand"),
                                status = root.findViewWithTag("controller-status-card");
                        check(
                                root.findViewWithTag("phone-app-settings") == null,
                                "status has no app-options shortcut");
                        check(
                                stop instanceof android.widget.ImageButton,
                                "desktop remains an accessible status action");
                        results.append(StatusDisclosureAssertions.inspect(root, "phone"));
                        View back = root.findViewWithTag("phone-back"),
                                hint = root.findViewWithTag("touchpad-help"),
                                pad = root.findViewWithTag("touchpad"),
                                scroll = root.findViewWithTag("phone-scroll"),
                                title = root.findViewWithTag("touchpad-title"),
                                heading = root.findViewWithTag("touchpad-heading");
                        float d = back.getResources().getDisplayMetrics().density;
                        int[] bp = position(back),
                                hp = position(hint),
                                pp = position(pad),
                                sp = position(scroll),
                                tp = position(title);
                        check(
                                back instanceof android.widget.ImageButton
                                        && hint instanceof android.widget.ImageButton
                                        && scroll instanceof android.widget.ImageButton,
                                "touchpad uses accessible icon actions");
                        check(
                                Math.abs(bp[1] - pp[1]) <= 4 * d
                                        && Math.abs(
                                                        bp[0]
                                                                + back.getWidth()
                                                                - (pp[0] + pad.getWidth()))
                                                <= 4 * d
                                        && Math.abs(sp[0] - pp[0]) <= 4 * d,
                                "Scroll and Back balance opposite touchpad edges");
                        int[] gp = position(heading);
                        check(
                                Math.abs(
                                                        gp[0]
                                                                + heading.getWidth() / 2f
                                                                - (pp[0] + pad.getWidth() / 2f))
                                                <= d
                                        && Math.abs(tp[0] + title.getWidth() - hp[0]) <= d
                                        && sp[0] + scroll.getWidth() <= gp[0]
                                        && hp[0] + hint.getWidth() <= bp[0],
                                "Touchpad and adjacent Info form centered nonoverlapping group");
                        for (View action : new View[] {back, hint, scroll, stop, expansion})
                            check(
                                    action.getWidth() >= 47 * d
                                            && action.getHeight() >= 47 * d
                                            && action.getContentDescription() != null,
                                    "compact icon retains 48dp accessible target");
                    });
            results.append(SetupPanelAcceptance.run(runner, (MainActivity) controller));
            results.append(
                    new PhoneMenuAcceptance(runner, (MainActivity) controller, session).run());
            ComponentName test = new ComponentName(getTargetContext(), InputTestActivity.class);
            runOnMainSync(() -> session.launch(test, null));
            await(() -> !session.busy, "external app configuration ready");
            Thread.sleep(600);
            results.append(
                    StopReturnAcceptance.run(
                            runner,
                            (MainActivity) controller,
                            session,
                            () ->
                                    controller
                                            .getWindow()
                                            .getDecorView()
                                            .findViewWithTag("phone-stop")
                                            .performClick(),
                            "single"));
            check(
                    !session.prefs.getBoolean("statusExpanded", false),
                    "Stop leaves status collapsed");
            await(
                    () ->
                            session.id < 0
                                    && !session.desktopRequested()
                                    && controller.hasWindowFocus(),
                    "Stop releases desktop and preserves controller focus");
            runOnMainSync(
                    () -> {
                        View start =
                                controller.getWindow().getDecorView().findViewWithTag("phone-stop");
                        check(
                                "Start desktop".contentEquals(start.getContentDescription()),
                                "status action changes to Start after Stop");
                        start.performClick();
                    });
            await(() -> session.id >= 0 && !session.busy, "Start resumes desktop");
            runOnMainSync(() -> session.launch(test, null));
            await(() -> !session.busy, "resumed app configuration ready");
            Thread.sleep(600);
            int before = InputTestActivity.backEvents;
            runOnMainSync(
                    () ->
                            controller
                                    .getWindow()
                                    .getDecorView()
                                    .findViewWithTag("phone-back")
                                    .performClick());
            await(
                    () -> InputTestActivity.backEvents > before,
                    "touchpad-corner Back reaches external app after Start");
            // Keyboard focus can belong to the external display while the phone's
            // controller remains visible. Assert visibility/lifecycle, not global focus.
            runOnMainSync(
                    () ->
                            check(
                                    !controller.isFinishing()
                                            && !controller.isDestroyed()
                                            && controller
                                                            .getWindow()
                                                            .getDecorView()
                                                            .getWindowVisibility()
                                                    == View.VISIBLE
                                            && controller
                                                    .getWindow()
                                                    .getDecorView()
                                                    .findViewWithTag("phone-back")
                                                    .isShown(),
                                    "Back keeps phone controller open"));
            results.append(
                    StopReturnAcceptance.failures(
                            runner, (MainActivity) controller, session, false));
            results.append(
                    StopReturnAcceptance.boundaries(runner, (MainActivity) controller, session));
            return results.toString();
        } finally {
            if (session != null && originalExpansion[2])
                runOnMainSync(
                        () -> {
                            android.content.SharedPreferences.Editor editor = session.prefs.edit();
                            if (originalExpansion[0])
                                editor.putBoolean("statusExpanded", originalExpansion[1]);
                            else editor.remove("statusExpanded");
                            editor.apply();
                        });
            if (bound) getTargetContext().unbindService(binding);
        }
    }
}
