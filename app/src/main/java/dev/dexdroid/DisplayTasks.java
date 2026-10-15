package dev.dexdroid;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Task placement evidence from legacy Stack and modern RootTask shell reports. */
final class DisplayTasks {
    static final class Root {
        int id, display, tasks, taskId;
        boolean matches, visible, fullscreen;
    }

    static List<Root> parse(String report, String component) {
        String pkg = component.substring(0, component.indexOf('/'));
        String activity = component.substring(component.indexOf('/') + 1);
        if (activity.startsWith(".")) activity = pkg + activity;
        Pattern
                header =
                        Pattern.compile(
                                "^(?:Stack|RootTask) id=(\\d+)\\b.*\\bdisplayId=(\\d+)\\b.*\\buserId=(\\d+)\\b"),
                task = Pattern.compile("^\\s*taskId=(\\d+):.*\\buserId=0\\b.*");
        List<Root> roots = new ArrayList<>();
        Root root = null;
        for (String line : report.split("\n")) {
            Matcher match = header.matcher(line);
            if (line.startsWith("Stack ") || line.startsWith("RootTask ")) {
                root = null;
                if (match.find() && match.group(3).equals("0")) {
                    root = new Root();
                    root.id = Integer.parseInt(match.group(1));
                    root.display = Integer.parseInt(match.group(2));
                    roots.add(root);
                }
            } else if (root != null) {
                if (line.trim().startsWith("configuration="))
                    root.fullscreen = line.contains("mWindowingMode=fullscreen");
                Matcher entry = task.matcher(line);
                if (!entry.matches()) continue;
                root.tasks++;
                boolean target =
                        line.contains(pkg + "/")
                                && (!pkg.equals("dev.dexdroid") || line.contains(activity));
                if (target) {
                    boolean visible = line.contains("visible=true");
                    if (!root.matches || visible) root.taskId = Integer.parseInt(entry.group(1));
                    root.matches = true;
                    root.visible |= visible;
                }
            }
        }
        return roots;
    }
}
