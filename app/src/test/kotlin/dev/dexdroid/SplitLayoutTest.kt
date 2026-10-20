package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

open class SplitLayoutTest {
    @Test
    open fun launchTargetsFollowSpatialPositionThroughDirectionAndSwap() {
        val layout = SplitLayout()
        assertEquals("Open app", layout.openLabel(0))
        layout.enabled = true
        for (vertical in booleanArrayOf(false, true)) for (swapped in booleanArrayOf(false, true)) {
            layout.vertical = vertical
            layout.swapped = swapped
            assertEquals(if (vertical) "Open up" else "Open left", layout.openLabel(0))
            assertEquals(if (vertical) "Open down" else "Open right", layout.openLabel(1))
            assertEquals((if (swapped) 1 else 0).toLong(), layout.paneAtPosition(0).toLong())
            val first = layout.pane(layout.paneAtPosition(0), 1920, 1200, 4)
            assertEquals(0, first.x.toLong())
            assertEquals(0, first.y.toLong())
        }
    }

    @Test
    open fun thinDividerRetainsCenteredClippedGrabBandWithoutChangingAppBounds() {
        val layout = SplitLayout()
        layout.enabled = true
        for (vertical in booleanArrayOf(false, true)) {
            layout.vertical = vertical
            val line = layout.divider(1920, 1200, 4)
            val grab = layout.dividerGrab(1920, 1200, 4, 24)
            assertEquals(4, (if (vertical) line.height else line.width).toLong())
            assertEquals(24, (if (vertical) grab.height else grab.width).toLong())
            assertEquals(line.x + line.width / 2f, grab.x + grab.width / 2f, 0f)
            assertEquals(line.y + line.height / 2f, grab.y + grab.height / 2f, 0f)
            assertTrue(grab.contains(line.x.toFloat(), line.y.toFloat()))
            val clipped = layout.dividerGrab(20, 16, 2, 100)
            assertTrue(clipped.x >= 0 && clipped.y >= 0)
            assertTrue(clipped.x + clipped.width <= 20 && clipped.y + clipped.height <= 16)
        }
        layout.maximized = 0
        assertEquals(0, layout.dividerGrab(1920, 1200, 4, 24).width.toLong())
        layout.enabled = false
        layout.maximized = -1
        assertEquals(0, layout.dividerGrab(1920, 1200, 4, 24).height.toLong())
    }

    @Test
    open fun panesAndDividerPartitionWorkspaceForDirectionsRatiosAndSwap() {
        for (vertical in booleanArrayOf(false, true)) for (swapped in
            booleanArrayOf(false, true)) for (ratio in
            floatArrayOf(0f, .25f, 1f / 3, .5f, .75f, 1f)) {
            val layout = SplitLayout()
            layout.enabled = true
            layout.vertical = vertical
            layout.swapped = swapped
            layout.ratio = ratio
            val a = layout.pane(0, 1920, 1200, 18)
            val b = layout.pane(1, 1920, 1200, 18)
            val bar = layout.divider(1920, 1200, 18)
            assertEquals(
                (1920 * 1200).toLong(),
                (a.width * a.height + b.width * b.height + bar.width * bar.height).toLong(),
            )
            assertEquals(
                0,
                layout.hit(a.x + a.width / 2f, a.y + a.height / 2f, 1920, 1200, 18).toLong(),
            )
            assertEquals(
                1,
                layout.hit(b.x + b.width / 2f, b.y + b.height / 2f, 1920, 1200, 18).toLong(),
            )
            assertEquals(
                -1,
                layout.hit(bar.x + bar.width / 2f, bar.y + bar.height / 2f, 1920, 1200, 18).toLong(),
            )
            assertFalse(a.contains(b.x + b.width / 2f, b.y + b.height / 2f))
        }
    }

    @Test
    open fun maximizeKeepsHiddenNativeBufferAndRestoreRetainsLayout() {
        val layout = SplitLayout()
        layout.enabled = true
        layout.ratio = .33f
        layout.swapped = true
        layout.selected = 1
        val before = layout.copy()
        layout.maximized = 1
        assertEquals(1920, layout.pane(1, 1920, 1200, 18).width.toLong())
        assertEquals(0, layout.pane(0, 1920, 1200, 18).width.toLong())
        assertTrue(layout.display(0, 1920, 1200, 18).width > 0)
        assertEquals(1, layout.hit(100f, 100f, 1920, 1200, 18).toLong())
        layout.maximized = -1
        assertEquals(
            before.pane(0, 1920, 1200, 18).x.toLong(),
            layout.pane(0, 1920, 1200, 18).x.toLong(),
        )
        assertEquals(before.ratio, layout.ratio, 0f)
        assertTrue(layout.swapped)
    }

    @Test
    open fun paneTargetsRoundTripThroughFourRotationsAndFit() {
        for (r in 0..3) for (baseH in intArrayOf(1200, 1080)) for (vertical in
            booleanArrayOf(false, true)) {
            val w = if (r % 2 == 0) 1920 else baseH
            val h = if (r % 2 == 0) baseH else 1920
            val layout = SplitLayout()
            layout.enabled = true
            layout.vertical = vertical
            for (pane in 0..1) {
                val box = layout.pane(pane, w, h, 18)
                for (u in floatArrayOf(.1f, .5f, .9f)) for (v in floatArrayOf(.1f, .5f, .9f)) {
                    val x = box.x + box.width * u
                    val y = box.y + box.height * v
                    val physical = Geometry.map(x / w, y / h, r, 1920f, 1200f, w, h)
                    val back = Geometry.unmap(physical[0], physical[1], r, 1920f, 1200f, w, h)
                    assertNotNull(back)
                    assertEquals(x, back!![0], .001f)
                    assertEquals(y, back!![1], .001f)
                    assertEquals(pane.toLong(), layout.hit(back!![0], back!![1], w, h, 18).toLong())
                }
            }
        }
    }

    @Test
    open fun oversizedGapStillPartitionsSmallWorkspace() {
        val layout = SplitLayout()
        layout.enabled = true
        val a = layout.pane(0, 80, 60, 100)
        val b = layout.pane(1, 80, 60, 100)
        val bar = layout.divider(80, 60, 100)
        assertEquals(80, (a.width + b.width + bar.width).toLong())
        assertEquals(a.width.toLong(), bar.x.toLong())
        assertEquals((bar.x + bar.width).toLong(), b.x.toLong())
        assertEquals(-1, layout.hit(-1f, 30f, 80, 60, 100).toLong())
        assertEquals(-1, layout.hit(80f, 30f, 80, 60, 100).toLong())
    }
}
