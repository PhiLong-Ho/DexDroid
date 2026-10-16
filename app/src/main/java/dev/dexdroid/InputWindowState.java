package dev.dexdroid;

import java.util.regex.*;

/** Android 11 input-dispatcher evidence, independent of Activity/task visibility. */
final class InputWindowState {
    private InputWindowState() {}

    static boolean ready(String report, int display, int uid, boolean keyboard) {
        if (display <= 0 || uid < 1000) return false;
        String[] sections = report.split("(?m)^  Display: ");
        boolean window = false;
        for (String section : sections) {
            int end = section.indexOf('\n');
            if (end < 0 || !section.substring(0, end).trim().equals(Integer.toString(display)))
                continue;
            for (String line : section.split("\n")) {
                if (!line.contains("name=")
                        || !line.contains("visible=true")
                        || line.contains("paused=true")) continue;
                Matcher owner = Pattern.compile("\\bownerUid=(\\d+)\\b").matcher(line);
                int actual = -1;
                while (owner.find()) actual = Integer.parseInt(owner.group(1));
                if (actual != uid || line.contains("touchableRegion=<empty>")) continue;
                Matcher type = Pattern.compile("\\btype=0x([0-9a-fA-F]+)\\b").matcher(line);
                if (!type.find()) continue;
                long windowType = Long.parseLong(type.group(1), 16);
                if (windowType < 1 || windowType >= 2000) continue;
                Matcher flags = Pattern.compile("\\bflags=0x([0-9a-fA-F]+)\\b").matcher(line);
                if (!flags.find() || (Long.parseLong(flags.group(1), 16) & 16) != 0) continue;
                Matcher frame =
                        Pattern.compile("\\bframe=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]")
                                .matcher(line);
                if (frame.find()
                        && Integer.parseInt(frame.group(3)) > Integer.parseInt(frame.group(1))
                        && Integer.parseInt(frame.group(4)) > Integer.parseInt(frame.group(2)))
                    window = true;
            }
        }
        if (!window || !keyboard) return window;
        int start = report.indexOf("  FocusedWindows:"),
                end = report.indexOf("  TouchStates:", start);
        return start >= 0
                && end > start
                && Pattern.compile("(?m)^\\s*displayId=" + display + ", name=")
                        .matcher(report.substring(start, end))
                        .find();
    }
}
