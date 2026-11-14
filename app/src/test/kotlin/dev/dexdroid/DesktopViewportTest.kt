package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class DesktopViewportTest {
    @Test
    fun taskbarReservesNativeAppSpaceWithoutChangingOutput() {
        val area = DesktopViewport(1920, 1200, 240, false)
        assertEquals(1920, area.outputWidth)
        assertEquals(1200, area.outputHeight)
        assertEquals(48, area.barHeight)
        assertEquals(1152, area.appHeight)
        assertTrue(area.containsApp(1919f, 1151f))
        assertFalse(area.containsApp(960f, 1152f))
        assertFalse(area.containsApp(Float.NaN, 5f))
        assertFalse(area.containsApp(-1f, 5f))
        assertFalse(area.containsApp(5f, Float.POSITIVE_INFINITY))
    }

    @Test
    fun gesturesRestoreFullViewportAndDpiCannotChangeReservedSize() {
        for (dpi in listOf(120, 160, 213, 240, 320)) {
            val reserved = DesktopViewport(1280, 800, dpi, false)
            assertEquals(32, reserved.barHeight)
            assertEquals(800, reserved.appHeight + reserved.barHeight)
            val full = DesktopViewport(1280, 800, dpi, true)
            assertEquals(0, full.barHeight)
            assertEquals(800, full.appHeight)
        }
    }

    @Test
    fun ratioTracksResolutionAndRemainsStableWhenOutputRotates() {
        for ((w, h, expected) in
            listOf(
                Triple(1920, 1200, 48),
                Triple(1920, 1080, 43),
                Triple(1280, 800, 32),
                Triple(3840, 2160, 86),
            )) {
            assertEquals(expected, DesktopViewport(w, h, 240, false).barHeight)
            assertEquals(expected, DesktopViewport(h, w, 320, false).barHeight)
        }
    }

    @Test
    fun appAndTaskbarUseSameFullOutputTransformAcrossRotationAndFit() {
        for (rotation in 0..3) for ((baseW, baseH) in listOf(1920 to 1200, 1920 to 1080)) {
            val w = if (rotation % 2 == 0) baseW else baseH
            val h = if (rotation % 2 == 0) baseH else baseW
            val area = DesktopViewport(w, h, 240, false)
            for (point in
                listOf(
                    1f to 1f,
                    (w - 2f) to (area.appHeight - 2f),
                    w / 2f to (area.appHeight + area.barHeight / 2f),
                )) {
                val physical =
                    Geometry.map(point.first / w, point.second / h, rotation, 1920f, 1200f, w, h)
                val output =
                    Geometry.unmap(physical[0], physical[1], rotation, 1920f, 1200f, w, h)!!
                assertArrayEquals(floatArrayOf(point.first, point.second), output, .01f)
                assertEquals(point.second < area.appHeight, area.containsApp(output[0], output[1]))
            }
        }
    }

    @Test
    fun invalidPartitionCannotCreateZeroOrNegativeNativeDisplay() {
        for ((w, h, dpi) in
            listOf(Triple(0, 1200, 160), Triple(1920, 1, 160), Triple(1920, 1200, 0))) {
            assertThrows(IllegalArgumentException::class.java) { DesktopViewport(w, h, dpi, false) }
        }
    }
}
