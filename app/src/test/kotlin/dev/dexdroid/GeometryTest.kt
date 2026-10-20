package dev.dexdroid

import java.util.ArrayList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Host coverage of coupled rotation, Fit and input coordinates without shell privileges. */
@RunWith(Parameterized::class)
open class GeometryTest(private val rotation: Int, width: Int, height: Int) {
    private val width: Int
    private val height: Int

    init {
        this.width = if (rotation % 2 == 0) width else height
        this.height = if (rotation % 2 == 0) height else width
    }

    @Test
    open fun centerStaysAtPhysicalCenter() {
        val center = Geometry.map(.5f, .5f, rotation, 1920f, 1200f, width, height)
        assertArrayEquals(floatArrayOf(960f, 600f), center, .01f)
    }

    @Test
    open fun logicalViewportMayExceedMonitorSignalHeight() {
        // HyperOS can expose a 1242px logical viewport for a 1200px monitor signal.
        val center = Geometry.map(.5f, .5f, rotation, 1920f, 1242f, width, height)
        assertArrayEquals(floatArrayOf(960f, 621f), center, .01f)
        val desktop = Geometry.unmap(960f, 621f, rotation, 1920f, 1242f, width, height)
        assertNotNull(desktop)
        assertArrayEquals(floatArrayOf(width / 2f, height / 2f), desktop, .01f)
        assertNull(Geometry.unmap(960f, 20f, rotation, 1920f, 1242f, width, height))
    }

    @Test
    open fun logicalViewportKeepsEdgeGestureInsideContent() {
        val u = .6f
        val v = 6f / height
        val screen = Geometry.map(u, v, rotation, 1920f, 1242f, width, height)
        val delivered = Geometry.unmap(screen[0], screen[1], rotation, 1920f, 1242f, width, height)
        assertNotNull(delivered)
        assertArrayEquals(floatArrayOf(u * width, 6f), delivered, .01f)
        val signalPoint = Geometry.map(u, v, rotation, 1920f, 1200f, width, height)
        val wrong =
            Geometry.unmap(signalPoint[0], signalPoint[1], rotation, 1920f, 1242f, width, height)
        assertTrue(
            wrong == null || Math.abs(wrong[1] - 6f) >= 2 || Math.abs(wrong[0] - u * width) >= 2
        )
    }

    @Test
    open fun interiorGridRoundTripsInEveryRotationAndFit() {
        for (x in 1..19) {
            for (y in 1..19) {
                val u = x / 20f
                val v = y / 20f
                val physical = Geometry.map(u, v, rotation, 1920f, 1200f, width, height)
                val desktop =
                    Geometry.unmap(physical[0], physical[1], rotation, 1920f, 1200f, width, height)
                assertNotNull(desktop)
                assertArrayEquals(floatArrayOf(u * width, v * height), desktop, .01f)
            }
        }
    }

    @Test
    open fun touchesOutsidePhysicalDisplayAreRejected() {
        assertNull(Geometry.unmap(-1f, 600f, rotation, 1920f, 1200f, width, height))
        assertNull(Geometry.unmap(1921f, 600f, rotation, 1920f, 1200f, width, height))
        assertNull(Geometry.unmap(960f, -1f, rotation, 1920f, 1200f, width, height))
        assertNull(Geometry.unmap(960f, 1201f, rotation, 1920f, 1200f, width, height))
    }

    @Test
    open fun fitBarsRejectContactsAndContentRemainsInteractive() {
        // Physical aspect is independent of upright desktop orientation.
        val edge1 = Geometry.map(0f, 0f, rotation, 1920f, 1200f, width, height)
        val edge2 = Geometry.map(1f, 1f, rotation, 1920f, 1200f, width, height)
        val top = Math.min(edge1[1], edge2[1])
        val left = Math.min(edge1[0], edge2[0])
        if (top > .1f) {
            assertNull(Geometry.unmap(960f, top / 2, rotation, 1920f, 1200f, width, height))
            assertNull(Geometry.unmap(960f, 1200 - top / 2, rotation, 1920f, 1200f, width, height))
        }
        if (left > .1f) {
            assertNull(Geometry.unmap(left / 2, 600f, rotation, 1920f, 1200f, width, height))
            assertNull(Geometry.unmap(1920 - left / 2, 600f, rotation, 1920f, 1200f, width, height))
        }
        assertNotNull(Geometry.unmap(960f, 600f, rotation, 1920f, 1200f, width, height))
    }

    @Test
    open fun unavailablePhysicalSizeRejectsInput() {
        assertNull(Geometry.unmap(0f, 0f, rotation, 0f, 1200f, width, height))
        assertNull(Geometry.unmap(0f, 0f, rotation, 1920f, 0f, width, height))
    }

    @Test
    open fun densityRecommendationIsBoundedAndMonotonic() {
        var previous = 120
        var shortEdge = 0
        while (shortEdge <= 5000) {
            val dpi = Geometry.recommendedDpi(shortEdge)
            assertTrue(dpi >= previous && dpi >= 120 && dpi <= 480)
            assertEquals(0, (dpi % 20).toLong())
            previous = dpi
            shortEdge += 5
        }
        assertEquals(160, Geometry.recommendedDpi(800).toLong())
        assertEquals(240, Geometry.recommendedDpi(1200).toLong())
    }

    companion object {
        @Parameterized.Parameters(name = "rotation={0}, desktop={1}x{2}")
        open @JvmStatic fun cases(): Collection<Array<Any?>?> {
            val cases = ArrayList<Array<Any?>?>()
            for (rotation in 0..3) {
                for (resolution in
                    arrayOf<IntArray?>(
                        intArrayOf(1920, 1200),
                        intArrayOf(1280, 800),
                        intArrayOf(1920, 1080),
                    )) {
                    cases.add(arrayOf<Any?>(rotation, resolution!![0], resolution!![1]))
                }
            }
            return cases
        }
    }
}
