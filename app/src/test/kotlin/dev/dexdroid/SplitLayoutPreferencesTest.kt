package dev.dexdroid

import java.util.HashMap
import org.junit.Assert.*
import org.junit.Test

open class SplitLayoutPreferencesTest {
    @Test
    open fun freshPreferencesUseSingleViewAndEqualLeftRight() {
        val value = SplitLayoutPreferences.read(HashMap<String?, Any?>())
        assertFalse(value.enabled)
        assertFalse(value.vertical)
        assertFalse(value.swapped)
        assertEquals(.5f, value.ratio, 0f)
    }

    @Test
    open fun restartRestoresGeometryWithoutTransientSelectionOrFullscreen() {
        val value = SplitLayout()
        value.swapped = true
        value.vertical = value.swapped
        value.enabled = value.vertical
        value.ratio = .33f
        value.maximized = 1
        value.selected = value.maximized
        val stored = SplitLayoutPreferences.write(value)
        assertEquals(4, stored.size.toLong())
        val restored = SplitLayoutPreferences.read(stored)
        assertTrue(restored.enabled && restored.vertical && restored.swapped)
        assertEquals(.33f, restored.ratio, 0f)
        assertEquals(0, restored.selected.toLong())
        assertEquals(-1, restored.maximized.toLong())
    }

    @Test
    open fun explicitEndRetainsLastGeometryForNextEnable() {
        val value = SplitLayout()
        value.enabled = false
        value.swapped = true
        value.vertical = value.swapped
        value.ratio = .75f
        val restored = SplitLayoutPreferences.read(SplitLayoutPreferences.write(value))
        assertFalse(restored.enabled)
        assertTrue(restored.vertical && restored.swapped)
        assertEquals(.75f, restored.ratio, 0f)
    }

    @Test
    open fun malformedOrNonFinitePreferencesUseSafeDefaults() {
        val stored = HashMap<String?, Any?>()
        stored.put("split.enabled", "true")
        stored.put("split.vertical", 1)
        stored.put("split.swapped", null)
        for (invalid in
            arrayOf<Any?>("0.3", 50, java.lang.Float.NaN, java.lang.Float.POSITIVE_INFINITY)) {
            stored.put("split.ratio", invalid)
            val restored = SplitLayoutPreferences.read(stored)
            assertFalse(restored.enabled || restored.vertical || restored.swapped)
            assertEquals(.5f, restored.ratio, 0f)
        }
    }

    @Test
    open fun extremeSavedRatiosStayWithinSupportedBounds() {
        val stored = HashMap<String?, Any?>()
        stored.put("split.ratio", -5f)
        assertEquals(.25f, SplitLayoutPreferences.read(stored).ratio, 0f)
        stored.put("split.ratio", 5f)
        assertEquals(.75f, SplitLayoutPreferences.read(stored).ratio, 0f)
    }
}
