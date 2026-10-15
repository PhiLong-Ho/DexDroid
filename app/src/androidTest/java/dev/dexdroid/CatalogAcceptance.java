package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.json.*;

/** Visible pending/error/cache behavior with a real PM scan behind a controlled worker gate. */
final class CatalogAcceptance {
    private final Instrumentation runner;
    private final StringBuilder result = new StringBuilder();
    private SessionService session;
    private MainActivity phone;
    private final List<Long> phoneDraw = new ArrayList<>(),
            monitorDraw = new ArrayList<>(),
            scans = new ArrayList<>();
    private File folder;

    CatalogAcceptance(Instrumentation runner) {
        this.runner = runner;
    }

    private void main(Runnable action) {
        runner.runOnMainSync(action);
    }

    private void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        result.append("PASS ").append(label).append('\n');
    }

    private void await(BooleanSupplier condition, String label) throws Exception {
        long end = SystemClock.uptimeMillis() + 10000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(25);
        check(condition.getAsBoolean(), label);
    }

    private boolean ui(BooleanSupplier condition) {
        boolean[] value = {false};
        main(() -> value[0] = condition.getAsBoolean());
        return value[0];
    }

    private View phoneRoot() {
        return phone.controllerMenu() == null
                ? null
                : phone.controllerMenu().getWindow().getDecorView();
    }

    private View monitorRoot() {
        return session.monitorShellView();
    }

    private View find(View root, String tag) {
        return root == null ? null : root.findViewWithTag(tag);
    }

    private View description(View root, String label) {
        if (label.equals(String.valueOf(root.getContentDescription()))) return root;
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                View found = description(((ViewGroup) root).getChildAt(i), label);
                if (found != null) return found;
            }
        return null;
    }

    private long drawn(Runnable open, Supplier<View> root) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        long[] elapsed = {-1};
        main(
                () -> {
                    long start = SystemClock.elapsedRealtimeNanos();
                    open.run();
                    View view = root.get();
                    check(view != null, "menu shell created synchronously");
                    ViewTreeObserver.OnDrawListener observer =
                            new ViewTreeObserver.OnDrawListener() {
                                public void onDraw() {
                                    if (elapsed[0] < 0) {
                                        elapsed[0] = SystemClock.elapsedRealtimeNanos() - start;
                                        done.countDown();
                                    }
                                    view.post(
                                            () -> {
                                                if (view.getViewTreeObserver().isAlive())
                                                    view.getViewTreeObserver()
                                                            .removeOnDrawListener(this);
                                            });
                                }
                            };
                    view.getViewTreeObserver().addOnDrawListener(observer);
                    view.invalidate();
                });
        check(done.await(3, TimeUnit.SECONDS), "menu rendered while worker can be pending");
        return elapsed[0] / 1000000;
    }

    private void phoneOpen() {
        description(phone.getWindow().getDecorView(), "Apps").performClick();
    }

    private void monitorOpen() {
        find(monitorRoot(), "monitor-idle-apps").performClick();
    }

    private void phoneClose() throws Exception {
        main(() -> find(phoneRoot(), "phone-menu-close").performClick());
        await(
                () -> ui(() -> phone.controllerMenu() == null),
                "phone closes while discovery may be pending");
    }

    private void monitorClose() throws Exception {
        main(() -> find(monitorRoot(), "monitor-close").performClick());
        await(
                () ->
                        ui(
                                () ->
                                        session.monitorShellPage().equals("closed")
                                                && !session.monitorShellAnimating()),
                "monitor closes while discovery may be pending");
    }

    private boolean loading(boolean monitor) {
        View row =
                find(
                        monitor ? monitorRoot() : phoneRoot(),
                        monitor ? "monitor-app-loading" : "phone-app-loading");
        return row != null && row.isShown();
    }

    private int count(boolean monitor) {
        ListView list =
                (ListView)
                        find(
                                monitor ? monitorRoot() : phoneRoot(),
                                monitor ? "monitor-app-list" : "phone-app-list");
        return list == null ? -1 : list.getCount();
    }

    private void shot(String name, boolean monitor) throws Exception {
        // Visual evidence follows presentation/enter animation; first-draw timings
        // above intentionally use their earlier OnDraw boundary instead.
        Thread.sleep(250);
        Bitmap bitmap;
        if (monitor) {
            Window window = session.monitorWindow();
            View view = window.getDecorView();
            bitmap =
                    Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            CountDownLatch copied = new CountDownLatch(1);
            int[] code = {-1};
            main(
                    () ->
                            PixelCopy.request(
                                    window,
                                    bitmap,
                                    value -> {
                                        code[0] = value;
                                        copied.countDown();
                                    },
                                    new Handler(Looper.getMainLooper())));
            check(
                    copied.await(3, TimeUnit.SECONDS) && code[0] == PixelCopy.SUCCESS,
                    "monitor pending pixels captured");
        } else bitmap = runner.getUiAutomation().takeScreenshot();
        check(bitmap != null, "catalog pixels captured");
        try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            bitmap.recycle();
        }
    }

    private static JSONObject times(List<Long> values) throws Exception {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return new JSONObject()
                .put("samples_ms", new JSONArray(values))
                .put("n", values.size())
                .put("p50_ms", sorted.get((int) Math.ceil(sorted.size() * .5) - 1))
                .put("p95_ms", sorted.get((int) Math.ceil(sorted.size() * .95) - 1));
    }

    private static final class Gate {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    }

    String run() throws Exception {
        Context context = runner.getTargetContext();
        CountDownLatch bound = new CountDownLatch(1);
        ServiceConnection connection =
                new ServiceConnection() {
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        session = ((SessionService.LocalBinder) binder).get();
                        bound.countDown();
                    }

                    public void onServiceDisconnected(ComponentName name) {}
                };
        AppCatalog original = null, test = null;
        AtomicReference<Gate> gate = new AtomicReference<>(new Gate());
        AtomicBoolean fail = new AtomicBoolean();
        AtomicInteger calls = new AtomicInteger(),
                active = new AtomicInteger(),
                maximum = new AtomicInteger();
        boolean connected = false;
        try {
            phone = DeviceTestSupport.startPhone(runner);
            connected =
                    context.bindService(
                            new Intent(context, SessionService.class),
                            connection,
                            Context.BIND_AUTO_CREATE);
            check(bound.await(5, TimeUnit.SECONDS), "catalog service bound");
            await(() -> session.id >= 0 && !session.busy, "catalog desktop ready");
            folder = new File(context.getExternalCacheDir(), "catalog-acceptance");
            check(folder.isDirectory() || folder.mkdirs(), "catalog evidence directory");
            main(
                    () -> {
                        session.pauseDesktop();
                        session.ensureDesktop();
                    });
            await(() -> session.id >= 0 && !session.busy, "fresh idle catalog desktop ready");
            original = session.appCatalog;
            AppCatalog[] created = {null};
            main(
                    () ->
                            created[0] =
                                    new AppCatalog(
                                            context,
                                            () -> {
                                                calls.incrementAndGet();
                                                maximum.accumulateAndGet(
                                                        active.incrementAndGet(), Math::max);
                                                try {
                                                    if (Looper.myLooper() == Looper.getMainLooper())
                                                        throw new IllegalStateException(
                                                                "Discovery ran on UI thread");
                                                    Gate current = gate.get();
                                                    current.entered.countDown();
                                                    if (!current.release.await(
                                                            20, TimeUnit.SECONDS))
                                                        throw new IOException(
                                                                "Acceptance worker gate timed out");
                                                    if (fail.getAndSet(false))
                                                        throw new IOException(
                                                                "Controlled discovery failure");
                                                    long start = SystemClock.elapsedRealtime();
                                                    List<AppCatalog.Entry> entries =
                                                            AppCatalog.installed(context);
                                                    scans.add(
                                                            SystemClock.elapsedRealtime() - start);
                                                    return entries;
                                                } finally {
                                                    active.decrementAndGet();
                                                }
                                            },
                                            session::log));
            test = created[0];
            AppCatalog catalog = test;
            main(() -> session.appCatalog = catalog);
            check(
                    gate.get().entered.await(3, TimeUnit.SECONDS),
                    "real discovery source waits on worker");
            long coldPhone = drawn(this::phoneOpen, this::phoneRoot);
            long coldMonitor = drawn(this::monitorOpen, this::monitorRoot);
            check(
                    ui(() -> loading(false) && loading(true)),
                    "both surfaces clearly show pending discovery");
            check(
                    ui(() -> count(false) == 0 && count(true) == 0),
                    "pending list has no fake launch targets");
            shot("phone-loading", false);
            shot("monitor-loading-r0", true);
            main(() -> ((EditText) find(phoneRoot(), "phone-app-search")).setText("input test"));
            main(
                    () ->
                            ((EditText) find(monitorRoot(), "monitor-app-search"))
                                    .setText("dev.dexdroid"));
            phoneClose();
            drawn(this::phoneOpen, this::phoneRoot);
            main(() -> ((EditText) find(phoneRoot(), "phone-app-search")).setText("input test"));
            check(calls.get() == 1, "both surfaces and reopen share one in-flight discovery");
            gate.get().release.countDown();
            await(
                    () ->
                            ui(
                                    () ->
                                            !loading(false)
                                                    && !loading(true)
                                                    && count(false) == 1
                                                    && count(true) == 1),
                    "async results preserve both typed queries");
            check(maximum.get() == 1, "discovery is single flight");
            phoneClose();
            monitorClose();
            for (int i = 0; i < 10; i++) {
                phoneDraw.add(drawn(this::phoneOpen, this::phoneRoot));
                check(
                        ui(() -> count(false) > 3 && !loading(false)),
                        "warm phone has cached apps immediately");
                phoneClose();
            }
            for (int rotation = 0; rotation < 4; rotation++) {
                final int r = rotation;
                main(() -> session.rotate(r, false));
                await(() -> !session.busy && session.rotation == r, "catalog rotation ready " + r);
                for (int i = 0; i < 3; i++) {
                    monitorDraw.add(drawn(this::monitorOpen, this::monitorRoot));
                    check(
                            ui(() -> count(true) > 3 && !loading(true)),
                            "warm monitor has cached apps immediately");
                    monitorClose();
                }
            }
            check(calls.get() == 1, "warm opens and rotation perform no package rescans");
            Gate refresh = new Gate();
            gate.set(refresh);
            main(catalog::invalidate);
            check(refresh.entered.await(3, TimeUnit.SECONDS), "refresh runs on worker");
            drawn(this::phoneOpen, this::phoneRoot);
            drawn(this::monitorOpen, this::monitorRoot);
            main(
                    () -> {
                        ((EditText) find(phoneRoot(), "phone-app-search")).setText("input test");
                        ((EditText) find(monitorRoot(), "monitor-app-search"))
                                .setText("dev.dexdroid");
                    });
            check(
                    ui(
                            () ->
                                    loading(false)
                                            && loading(true)
                                            && count(false) == 1
                                            && count(true) == 1),
                    "refresh retains usable cached search results with pending indicator");
            shot("monitor-updating-r3", true);
            refresh.release.countDown();
            await(
                    () -> ui(() -> !loading(false) && !loading(true)),
                    "refresh finishes without rebuilding editors");
            check(
                    ui(() -> count(false) == 1 && count(true) == 1),
                    "refresh preserves queries and results");
            fail.set(true);
            main(catalog::invalidate);
            await(
                    () ->
                            ui(
                                    () ->
                                            String.valueOf(
                                                            find(phoneRoot(), "phone-app-loading")
                                                                    .getContentDescription())
                                                    .contains("retry")),
                    "discovery error shows retry instead of an endless spinner");
            shot("phone-error", false);
            Gate retry = new Gate();
            gate.set(retry);
            main(() -> find(phoneRoot(), "phone-app-loading").performClick());
            check(retry.entered.await(3, TimeUnit.SECONDS), "retry starts another background load");
            phoneClose();
            monitorClose();
            retry.release.countDown();
            await(() -> active.get() == 0, "retry worker completes after dismissal");
            Thread.sleep(100);
            check(
                    ui(
                            () ->
                                    phone.controllerMenu() == null
                                            && session.monitorShellPage().equals("closed")),
                    "late results never reopen dismissed menus");
            JSONObject timing =
                    new JSONObject()
                            .put(
                                    "boundary",
                                    "Accepted native Apps action to first OnDraw; excludes physical/ADB input and animation completion")
                            .put("device", Build.MODEL)
                            .put("android", Build.VERSION.RELEASE)
                            .put("cold_phone_first_draw_ms", coldPhone)
                            .put("cold_monitor_first_draw_ms", coldMonitor)
                            .put(
                                    "cold_note",
                                    "Controlled gate keeps worker pending; these are acknowledgment times, not artificial source latency")
                            .put("warm_phone", times(phoneDraw))
                            .put("warm_monitor_four_rotations", times(monitorDraw))
                            .put("actual_worker_scan", times(scans))
                            .put("maximum_parallel_loads", maximum.get());
            try (FileOutputStream out = new FileOutputStream(new File(folder, "timings.json"))) {
                out.write(
                        (timing.toString(2) + "\n")
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            result.append("TIMINGS ").append(timing).append('\n');
            return result.toString();
        } catch (Throwable failure) {
            throw new RuntimeException(result + "FAIL catalog acceptance", failure);
        } finally {
            gate.get().release.countDown();
            AppCatalog keep = original, temporary = test;
            main(
                    () -> {
                        if (phone != null && phone.controllerMenu() != null)
                            phone.controllerMenu().dismiss();
                        if (session != null && keep != null) session.appCatalog = keep;
                        if (temporary != null) temporary.close();
                    });
            if (connected) context.unbindService(connection);
        }
    }
}
