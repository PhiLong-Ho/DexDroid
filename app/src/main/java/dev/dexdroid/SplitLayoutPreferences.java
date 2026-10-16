package dev.dexdroid;

import java.util.LinkedHashMap;
import java.util.Map;

/** Only durable workspace choices; display/task/input identities never enter preferences. */
final class SplitLayoutPreferences {
    static SplitLayout read(Map<String, ?> values) {
        SplitLayout result = new SplitLayout();
        result.enabled = Boolean.TRUE.equals(values.get("split.enabled"));
        result.vertical = Boolean.TRUE.equals(values.get("split.vertical"));
        result.swapped = Boolean.TRUE.equals(values.get("split.swapped"));
        Object ratio = values.get("split.ratio");
        if (ratio instanceof Float && Float.isFinite((Float) ratio))
            result.ratio = Math.max(.25f, Math.min(.75f, (Float) ratio));
        return result;
    }

    static Map<String, Object> write(SplitLayout layout) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("split.enabled", layout.enabled);
        result.put("split.vertical", layout.vertical);
        result.put("split.swapped", layout.swapped);
        result.put(
                "split.ratio",
                Float.isFinite(layout.ratio) ? Math.max(.25f, Math.min(.75f, layout.ratio)) : .5f);
        return result;
    }
}
