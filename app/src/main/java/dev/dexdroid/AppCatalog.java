package dev.dexdroid;

import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

final class AppCatalog implements AutoCloseable {
    static class Entry {
        final String name;
        final ComponentName component;
        final Drawable icon;

        Entry(String n, ComponentName c, Drawable i) {
            name = n;
            component = c;
            if (i.getConstantState() == null) {
                // Snapshot non-cloneable icons off the UI thread, so each density's
                // row can receive independent bounds without mutating the shared cache.
                android.graphics.Bitmap bitmap =
                        android.graphics.Bitmap.createBitmap(
                                128, 128, android.graphics.Bitmap.Config.ARGB_8888);
                i.setBounds(0, 0, 128, 128);
                i.draw(new android.graphics.Canvas(bitmap));
                icon =
                        new android.graphics.drawable.BitmapDrawable(
                                android.content.res.Resources.getSystem(), bitmap);
            } else icon = i;
        }
    }

    static final class State {
        final List<Entry> apps;
        final boolean loading;
        final String error;

        State(List<Entry> apps, boolean loading, String error) {
            this.apps = apps;
            this.loading = loading;
            this.error = error;
        }
    }

    interface Source {
        List<Entry> load() throws Exception;
    }

    private final Context context;
    private final Source source;
    private final Consumer<String> log;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Set<Consumer<State>> listeners = new HashSet<>();
    private List<Entry> cache = Collections.emptyList();
    private int revision, attempted = -1;
    private boolean running, closed;
    private String error = "";
    private final BroadcastReceiver packages =
            new BroadcastReceiver() {
                public void onReceive(Context ignored, Intent intent) {
                    invalidate();
                }
            };

    AppCatalog(Context context, Consumer<String> log) {
        this(context, () -> installed(context.getApplicationContext()), log);
    }

    AppCatalog(Context context, Source source, Consumer<String> log) {
        this.context = context.getApplicationContext();
        this.source = source;
        this.log = log;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33)
            this.context.registerReceiver(packages, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerLegacy(filter);
        load();
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerLegacy(IntentFilter filter) {
        context.registerReceiver(packages, filter);
    }

    Runnable observe(Consumer<State> listener) {
        if (closed) return () -> {};
        listeners.add(listener);
        listener.accept(state());
        load();
        return () -> listeners.remove(listener);
    }

    void invalidate() {
        if (closed) return;
        revision++;
        load();
    }

    private State state() {
        return new State(cache, running, error);
    }

    private void publish() {
        State current = state();
        for (Consumer<State> listener : new ArrayList<>(listeners)) listener.accept(current);
    }

    private void load() {
        if (closed || running || attempted == revision) return;
        running = true;
        error = "";
        int requested = revision;
        publish();
        worker.execute(
                () -> {
                    long start = SystemClock.elapsedRealtime();
                    List<Entry> loaded = null;
                    String failure = "";
                    try {
                        loaded = Collections.unmodifiableList(new ArrayList<>(source.load()));
                    } catch (Exception failed) {
                        failure = failed.toString();
                    }
                    List<Entry> result = loaded;
                    String problem = failure;
                    long duration = SystemClock.elapsedRealtime() - start;
                    main.post(
                            () -> {
                                if (closed) return;
                                running = false;
                                if (revision != requested) {
                                    load();
                                    return;
                                }
                                attempted = requested;
                                error = problem;
                                if (result != null) cache = result;
                                log.accept(
                                        "App catalog "
                                                + (problem.isEmpty()
                                                        ? "ready: " + cache.size() + " entries"
                                                        : "failed")
                                                + " in "
                                                + duration
                                                + " ms (worker)");
                                publish();
                            });
                });
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        listeners.clear();
        context.unregisterReceiver(packages);
        worker.shutdownNow();
    }

    static List<Entry> installed(Context context) {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("App discovery must run off the UI thread");
        PackageManager pm = context.getPackageManager();
        List<Entry> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo r :
                pm.queryIntentActivities(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(context.getPackageName()) || !seen.add(pkg)) continue;
            apps.add(
                    new Entry(
                            r.loadLabel(pm).toString(),
                            new ComponentName(pkg, r.activityInfo.name),
                            r.loadIcon(pm)));
        }
        if (BuildConfig.DEBUG && "debug".equals(BuildConfig.BUILD_TYPE))
            apps.add(
                    new Entry(
                            "DexDroid input test",
                            new ComponentName(
                                    context.getPackageName(), "dev.dexdroid.InputTestActivity"),
                            context.getApplicationInfo().loadIcon(pm)));
        Set<String> favorites =
                new HashSet<>(
                        Arrays.asList(
                                "dev.dexdroid",
                                "dev.portraitdesk",
                                "org.mozilla.firefox",
                                "app.revanced.android.youtube",
                                "eu.kanade.tachiyomi",
                                "com.android.chrome"));
        apps.sort(
                (a, b) -> {
                    int order =
                            Boolean.compare(
                                    favorites.contains(b.component.getPackageName()),
                                    favorites.contains(a.component.getPackageName()));
                    return order != 0 ? order : a.name.compareToIgnoreCase(b.name);
                });
        return apps;
    }
}
