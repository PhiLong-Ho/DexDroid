package dev.dexdroid;

import java.util.*;
import java.util.regex.*;

/** Test setup only; never included in release artifacts. */
final class FixtureTaskIsolation {
    private static final String COMPONENT =
            "dev\\.dexdroid\\.test/(?:dev\\.dexdroid\\.fixture\\.|\\.fixture\\.)(?:LaunchParentActivity|LaunchReaderActivity)";

    static List<DisplayTasks.Root> ownedRoots(String report) {
        if (report.contains("Error:")
                || report.contains("DUMP TIMEOUT")
                || report.contains("Permission Denial"))
            throw new IllegalStateException("Fixture task snapshot unavailable");
        List<DisplayTasks.Root> parsed =
                DisplayTasks.parse(
                        report, "dev.dexdroid.test/dev.dexdroid.fixture.LaunchParentActivity");
        if (parsed.isEmpty())
            throw new IllegalStateException("No recognized current-user task snapshot");
        List<DisplayTasks.Root> owned = new ArrayList<>();
        String[] blocks = report.split("(?m)(?=^(?:Stack|RootTask) )");
        Pattern known = Pattern.compile(COMPONENT + "(?=\\s|$)");
        Pattern task =
                Pattern.compile(
                        "(?m)^\\s*taskId=(\\d+):\\s+" + COMPONENT + "\\s+userId=0\\b[^\\n]*$");
        Pattern header = Pattern.compile("^(?:Stack|RootTask) id=(\\d+)\\b");
        for (String block : blocks) {
            if (!known.matcher(block).find()) continue;
            Matcher head = header.matcher(block), entry = task.matcher(block);
            if (!head.find() || !entry.find())
                throw new IllegalStateException("Unrecognized fixture task ownership");
            int id = Integer.parseInt(head.group(1)), taskId = Integer.parseInt(entry.group(1));
            Matcher allTasks = Pattern.compile("(?m)^\\s*taskId=").matcher(block);
            int count = 0;
            while (allTasks.find()) count++;
            DisplayTasks.Root match = null;
            for (DisplayTasks.Root root : parsed)
                if (root.id == id) {
                    if (match != null) throw new IllegalStateException("Duplicate fixture root");
                    match = root;
                }
            if (match == null
                    || count != 1
                    || match.tasks != 1
                    || match.taskId != taskId
                    || !match.matches
                    || entry.find())
                throw new IllegalStateException("Shared or ambiguous fixture root");
            for (DisplayTasks.Root previous : owned)
                if (previous.id == id || previous.taskId == taskId)
                    throw new IllegalStateException("Duplicate fixture root or task");
            owned.add(match);
        }
        return owned;
    }
}
