package dev.dexdroid;

import java.io.*;
import java.util.regex.*;

/** Pure, conservative parser. Window titles and content never leave this reader. */
final class ProtectedWindows {
    enum State {
        UNKNOWN,
        CLEAR,
        PROTECTED
    }

    private static final Pattern WINDOW = Pattern.compile("^\\s*Window #\\d+ Window\\{.*");
    private static final Pattern DISPLAY = Pattern.compile("^\\s*mDisplayId=(\\d+)\\b");
    private static final Pattern FLAGS = Pattern.compile("^\\s*fl=([^\\r\\n}]+)");
    private static final Pattern SECURE = Pattern.compile("(?:^|[ |])(FLAG_)?SECURE(?:$|[ |])");

    private static final class Window {
        int display = -1, flags = -1;
        Boolean surfaced, visible;

        State state(int target) {
            if (display < 0) return State.UNKNOWN;
            if (display != target) return State.CLEAR;
            if (Boolean.FALSE.equals(surfaced) || Boolean.FALSE.equals(visible)) return State.CLEAR;
            if (surfaced == null || visible == null || flags < 0) return State.UNKNOWN;
            return flags == 1 ? State.PROTECTED : State.CLEAR;
        }
    }

    static State read(Reader source, int target) throws IOException {
        if (target <= 0) return State.UNKNOWN;
        BufferedReader reader = new BufferedReader(source);
        boolean header = false, unknown = false, secure = false;
        Window window = null;
        int size = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            if ((size += line.length()) > 1048576) return State.UNKNOWN;
            if (line.contains("DUMP TIMEOUT") || line.contains("Permission Denial"))
                return State.UNKNOWN;
            // JoyUI may omit the newline after its last display diagnostic.
            if (line.endsWith("WINDOW MANAGER WINDOWS (dumpsys window windows)")) header = true;
            if (WINDOW.matcher(line).matches()) {
                if (window != null) {
                    secure |= window.state(target) == State.PROTECTED;
                    unknown |= window.state(target) == State.UNKNOWN;
                }
                window = new Window();
                continue;
            }
            if (window == null) continue;
            Matcher match = DISPLAY.matcher(line);
            if (window.display < 0 && match.find())
                window.display = Integer.parseInt(match.group(1));
            if (line.contains("mHasSurface=")) window.surfaced = line.contains("mHasSurface=true");
            if (line.trim().startsWith("isOnScreen="))
                window.visible = line.trim().equals("isOnScreen=true");
            match = FLAGS.matcher(line);
            if (window.flags < 0 && match.find()) {
                String value = match.group(1).trim();
                try {
                    window.flags =
                            (Long.parseLong(value.replaceFirst("^0x", ""), 16) & 0x2000) != 0
                                    ? 1
                                    : 0;
                } catch (NumberFormatException namedFlags) {
                    if (value.matches("[A-Z_ |]+"))
                        window.flags = SECURE.matcher(value).find() ? 1 : 0;
                }
            }
        }
        if (window != null) {
            secure |= window.state(target) == State.PROTECTED;
            unknown |= window.state(target) == State.UNKNOWN;
        }
        return !header
                ? State.UNKNOWN
                : secure ? State.PROTECTED : unknown ? State.UNKNOWN : State.CLEAR;
    }
}
