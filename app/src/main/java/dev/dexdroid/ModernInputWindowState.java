package dev.dexdroid;

import java.util.*;
import java.util.regex.*;

/** Conservative current InputDispatcher evidence. Unknown reports never authorize recovery. */
final class ModernInputWindowState {
    enum State {
        READY,
        PENDING,
        UNKNOWN
    }

    private static final Pattern SECTION = Pattern.compile("(?m)^  Display: (\\d+)\\s*$");
    private static final Pattern BOUNDARY = Pattern.compile("(?m)^  \\S");
    private static final Pattern FRAME =
            Pattern.compile("frame=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]");
    private static final Set<String> FLAGS =
            new HashSet<>(
                    Arrays.asList(
                            "NOT_VISIBLE",
                            "NOT_TOUCHABLE",
                            "NOT_FOCUSABLE",
                            "NO_INPUT_CHANNEL",
                            "PAUSE_DISPATCHING",
                            "DROP_INPUT",
                            "DROP_INPUT_IF_OBSCURED",
                            "TRUSTED_OVERLAY",
                            "PREVENT_SPLITTING",
                            "SLIPPERY",
                            "WATCH_OUTSIDE_TOUCH",
                            "SPY",
                            "INTERCEPTS_STYLUS",
                            "DUPLICATE_TOUCH_TO_WALLPAPER",
                            "IS_WALLPAPER",
                            "DISABLE_USER_ACTIVITY",
                            "SENSITIVE_FOR_PRIVACY"));

    private ModernInputWindowState() {}

    static State inspect(String report, int display, int uid) {
        if (report == null
                || display <= 0
                || uid < 1000
                || report.contains("DUMP TIMEOUT")
                || report.contains("Permission Denial")
                || report.contains("Error:")) return State.UNKNOWN;
        int start = report.indexOf("Input Dispatcher State:\n");
        if (start < 0) return State.UNKNOWN;
        String current = report.substring(start);
        if (!current.contains("\n  DispatchEnabled: true\n")
                || !current.contains("\n  DispatchFrozen: false\n")) return State.UNKNOWN;
        Matcher sections = SECTION.matcher(current);
        while (sections.find()) {
            if (!sections.group(1).equals(Integer.toString(display))) continue;
            int from = sections.end();
            Matcher boundary = BOUNDARY.matcher(current);
            if (!boundary.find(from)) return State.UNKNOWN;
            String section = current.substring(from, boundary.start());
            if (!section.contains("\n    logicalSize=") || !section.contains("\n    Windows:"))
                return State.UNKNOWN;
            String windows = section.substring(section.indexOf("\n    Windows:") + 13);
            boolean ready = false;
            for (String line : windows.split("\n")) {
                if (!line.matches("\\s*\\d+:.*")) {
                    String detail = line.trim();
                    if (detail.isEmpty()
                            || detail.equals("<none>")
                            || detail.startsWith("transform (")
                            || detail.matches("[-0-9.]+\\s+[-0-9.]+\\s+[-0-9.]+")) continue;
                    return State.UNKNOWN;
                }
                if (!line.contains("name=")
                        || !line.contains(", displayId=" + display + ",")
                        || !line.contains("dispatchingTimeout=")
                        || !line.contains("touchOcclusionMode=")) return State.UNKNOWN;
                Matcher owner = Pattern.compile("\\bownerUid=(\\d+),").matcher(line);
                if (!owner.find()) return State.UNKNOWN;
                if (!owner.group(1).equals(Integer.toString(uid))) continue;
                Matcher config = Pattern.compile("\\binputConfig=([^,]+),").matcher(line);
                Matcher frame = FRAME.matcher(line);
                if (!config.find()
                        || !frame.find()
                        || !line.contains("applicationInfo.name=")
                        || !line.contains("applicationInfo.token=")
                        || !line.contains("touchableRegion=")) return State.UNKNOWN;
                Set<String> flags = new HashSet<>();
                if (!config.group(1).equals("0x0")) {
                    for (String flag : config.group(1).split("\\s*\\|\\s*")) {
                        if (!FLAGS.contains(flag)) return State.UNKNOWN;
                        flags.add(flag);
                    }
                }
                boolean blocked =
                        flags.contains("NOT_VISIBLE")
                                || flags.contains("NOT_TOUCHABLE")
                                || flags.contains("NO_INPUT_CHANNEL")
                                || flags.contains("PAUSE_DISPATCHING")
                                || flags.contains("DROP_INPUT");
                if (flags.contains("DROP_INPUT_IF_OBSCURED") || flags.contains("SPY"))
                    return State.UNKNOWN;
                boolean app =
                        !line.contains("applicationInfo.name=,")
                                && !line.contains("applicationInfo.token=<null>")
                                && !line.contains("applicationInfo.token=0x0,");
                boolean area =
                        Integer.parseInt(frame.group(3)) > Integer.parseInt(frame.group(1))
                                && Integer.parseInt(frame.group(4))
                                        > Integer.parseInt(frame.group(2))
                                && !line.contains("touchableRegion=<empty>");
                ready |= app && area && !blocked;
            }
            return ready ? State.READY : State.PENDING;
        }
        return State.UNKNOWN;
    }
}
