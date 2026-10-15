package dev.dexdroid;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Shell-only metadata check, isolated from input, task queries and the UI. */
final class ProtectedWindowObserver implements AutoCloseable {
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean occupied, closed;
    private volatile Process process;
    private volatile ProtectedWindows.State last;
    private volatile int occupancyEpoch;

    ProtectedWindowObserver(int display, Consumer<ProtectedWindows.State> listener) {
        if (android.os.Process.myUid() != 2000)
            throw new SecurityException("Authorized shell required");
        worker.scheduleWithFixedDelay(
                () -> {
                    if (closed || !occupied) return;
                    int epoch = occupancyEpoch;
                    ProtectedWindows.State state = ProtectedWindows.State.UNKNOWN;
                    try {
                        Process scan =
                                new ProcessBuilder(
                                                "/system/bin/dumpsys",
                                                "-t",
                                                "2",
                                                "window",
                                                "visible-apps")
                                        .redirectErrorStream(true)
                                        .start();
                        process = scan;
                        if (closed) return;
                        try (Reader reader =
                                new InputStreamReader(
                                        scan.getInputStream(), StandardCharsets.UTF_8)) {
                            state = ProtectedWindows.read(reader, display);
                        }
                        if (!scan.waitFor(1, TimeUnit.SECONDS) || scan.exitValue() != 0)
                            state = ProtectedWindows.State.UNKNOWN;
                    } catch (Exception unavailable) {
                        state = ProtectedWindows.State.UNKNOWN;
                    } finally {
                        Process scan = process;
                        if (scan != null) scan.destroy();
                        process = null;
                    }
                    if (!closed && occupied && epoch == occupancyEpoch && state != last) {
                        last = state;
                        listener.accept(state);
                    }
                },
                0,
                2,
                TimeUnit.SECONDS);
    }

    void occupied(boolean value) {
        if (occupied != value) {
            occupancyEpoch++;
            last = null;
            occupied = value;
        }
    }

    @Override
    public void close() {
        closed = true;
        Process scan = process;
        if (scan != null) scan.destroy();
        worker.shutdownNow();
    }
}
