package dev.dexdroid;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

/** Atomic, isolated persistence; app overrides and legacy preferences are not rewritten. */
final class MonitorProfilePreferences implements MonitorProfiles.Store {
    private static final String PREFIX = "monitorProfiles.";
    private final SharedPreferences preferences;

    MonitorProfilePreferences(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public Map<String, String> read() {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet())
            if (entry.getKey().startsWith(PREFIX) && entry.getValue() instanceof String)
                result.put(entry.getKey().substring(PREFIX.length()), (String) entry.getValue());
        return result;
    }

    public void write(Map<String, String> values) {
        SharedPreferences.Editor edit = preferences.edit();
        for (String key : preferences.getAll().keySet())
            if (key.startsWith(PREFIX)) edit.remove(key);
        for (Map.Entry<String, String> entry : values.entrySet())
            edit.putString(PREFIX + entry.getKey(), entry.getValue());
        edit.apply();
    }
}
