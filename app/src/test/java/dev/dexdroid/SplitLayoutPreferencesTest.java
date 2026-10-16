package dev.dexdroid;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class SplitLayoutPreferencesTest {
    @Test
    public void freshPreferencesUseSingleViewAndEqualLeftRight() {
        SplitLayout value = SplitLayoutPreferences.read(new HashMap<>());
        assertFalse(value.enabled);
        assertFalse(value.vertical);
        assertFalse(value.swapped);
        assertEquals(.5f, value.ratio, 0);
    }

    @Test
    public void restartRestoresGeometryWithoutTransientSelectionOrFullscreen() {
        SplitLayout value = new SplitLayout();
        value.enabled = value.vertical = value.swapped = true;
        value.ratio = .33f;
        value.selected = value.maximized = 1;
        Map<String, Object> stored = SplitLayoutPreferences.write(value);
        assertEquals(4, stored.size());
        SplitLayout restored = SplitLayoutPreferences.read(stored);
        assertTrue(restored.enabled && restored.vertical && restored.swapped);
        assertEquals(.33f, restored.ratio, 0);
        assertEquals(0, restored.selected);
        assertEquals(-1, restored.maximized);
    }

    @Test
    public void explicitEndRetainsLastGeometryForNextEnable() {
        SplitLayout value = new SplitLayout();
        value.enabled = false;
        value.vertical = value.swapped = true;
        value.ratio = .75f;
        SplitLayout restored = SplitLayoutPreferences.read(SplitLayoutPreferences.write(value));
        assertFalse(restored.enabled);
        assertTrue(restored.vertical && restored.swapped);
        assertEquals(.75f, restored.ratio, 0);
    }

    @Test
    public void malformedOrNonFinitePreferencesUseSafeDefaults() {
        Map<String, Object> stored = new HashMap<>();
        stored.put("split.enabled", "true");
        stored.put("split.vertical", 1);
        stored.put("split.swapped", null);
        for (Object invalid : new Object[] {"0.3", 50, Float.NaN, Float.POSITIVE_INFINITY}) {
            stored.put("split.ratio", invalid);
            SplitLayout restored = SplitLayoutPreferences.read(stored);
            assertFalse(restored.enabled || restored.vertical || restored.swapped);
            assertEquals(.5f, restored.ratio, 0);
        }
    }

    @Test
    public void extremeSavedRatiosStayWithinSupportedBounds() {
        Map<String, Object> stored = new HashMap<>();
        stored.put("split.ratio", -5f);
        assertEquals(.25f, SplitLayoutPreferences.read(stored).ratio, 0);
        stored.put("split.ratio", 5f);
        assertEquals(.75f, SplitLayoutPreferences.read(stored).ratio, 0);
    }
}
