package dev.dexdroid;

import java.util.regex.*;

/** Exit status and actual shell error lines, excluding embedded dump metric names. */
final class ShellCommandResult {
    private static final Pattern ERROR = Pattern.compile("(?m)^[\\t ]*Error:[^\\r\\n]*");

    private ShellCommandResult() {}

    static String failure(int exit, String output) {
        Matcher match = ERROR.matcher(output);
        if (match.find()) {
            String line = match.group().trim();
            return "Shell command failed (exit "
                    + exit
                    + "): "
                    + line.substring(0, Math.min(240, line.length()));
        }
        return exit == 0 ? null : "Shell command failed (exit " + exit + ")";
    }
}
