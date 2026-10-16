package dev.dexdroid;

import static org.junit.Assert.*;

import org.junit.Test;

public class SplitLayoutTest {
    @Test
    public void launchTargetsFollowSpatialPositionThroughDirectionAndSwap() {
        SplitLayout layout = new SplitLayout();
        assertEquals("Open app", layout.openLabel(0));
        layout.enabled = true;
        for (boolean vertical : new boolean[] {false, true})
            for (boolean swapped : new boolean[] {false, true}) {
                layout.vertical = vertical;
                layout.swapped = swapped;
                assertEquals(vertical ? "Open up" : "Open left", layout.openLabel(0));
                assertEquals(vertical ? "Open down" : "Open right", layout.openLabel(1));
                assertEquals(swapped ? 1 : 0, layout.paneAtPosition(0));
                SplitLayout.Box first = layout.pane(layout.paneAtPosition(0), 1920, 1200, 4);
                assertEquals(0, first.x);
                assertEquals(0, first.y);
            }
    }

    @Test
    public void thinDividerRetainsCenteredClippedGrabBandWithoutChangingAppBounds() {
        SplitLayout layout = new SplitLayout();
        layout.enabled = true;
        for (boolean vertical : new boolean[] {false, true}) {
            layout.vertical = vertical;
            SplitLayout.Box line = layout.divider(1920, 1200, 4),
                    grab = layout.dividerGrab(1920, 1200, 4, 24);
            assertEquals(4, vertical ? line.height : line.width);
            assertEquals(24, vertical ? grab.height : grab.width);
            assertEquals(line.x + line.width / 2f, grab.x + grab.width / 2f, 0);
            assertEquals(line.y + line.height / 2f, grab.y + grab.height / 2f, 0);
            assertTrue(grab.contains(line.x, line.y));
            SplitLayout.Box clipped = layout.dividerGrab(20, 16, 2, 100);
            assertTrue(clipped.x >= 0 && clipped.y >= 0);
            assertTrue(clipped.x + clipped.width <= 20 && clipped.y + clipped.height <= 16);
        }
        layout.maximized = 0;
        assertEquals(0, layout.dividerGrab(1920, 1200, 4, 24).width);
        layout.enabled = false;
        layout.maximized = -1;
        assertEquals(0, layout.dividerGrab(1920, 1200, 4, 24).height);
    }

    @Test
    public void panesAndDividerPartitionWorkspaceForDirectionsRatiosAndSwap() {
        for (boolean vertical : new boolean[] {false, true})
            for (boolean swapped : new boolean[] {false, true})
                for (float ratio : new float[] {0, .25f, 1f / 3, .5f, .75f, 1}) {
                    SplitLayout layout = new SplitLayout();
                    layout.enabled = true;
                    layout.vertical = vertical;
                    layout.swapped = swapped;
                    layout.ratio = ratio;
                    SplitLayout.Box a = layout.pane(0, 1920, 1200, 18),
                            b = layout.pane(1, 1920, 1200, 18),
                            bar = layout.divider(1920, 1200, 18);
                    assertEquals(
                            1920 * 1200,
                            a.width * a.height + b.width * b.height + bar.width * bar.height);
                    assertEquals(
                            0, layout.hit(a.x + a.width / 2f, a.y + a.height / 2f, 1920, 1200, 18));
                    assertEquals(
                            1, layout.hit(b.x + b.width / 2f, b.y + b.height / 2f, 1920, 1200, 18));
                    assertEquals(
                            -1,
                            layout.hit(
                                    bar.x + bar.width / 2f,
                                    bar.y + bar.height / 2f,
                                    1920,
                                    1200,
                                    18));
                    assertFalse(a.contains(b.x + b.width / 2f, b.y + b.height / 2f));
                }
    }

    @Test
    public void maximizeKeepsHiddenNativeBufferAndRestoreRetainsLayout() {
        SplitLayout layout = new SplitLayout();
        layout.enabled = true;
        layout.ratio = .33f;
        layout.swapped = true;
        layout.selected = 1;
        SplitLayout before = layout.copy();
        layout.maximized = 1;
        assertEquals(1920, layout.pane(1, 1920, 1200, 18).width);
        assertEquals(0, layout.pane(0, 1920, 1200, 18).width);
        assertTrue(layout.display(0, 1920, 1200, 18).width > 0);
        assertEquals(1, layout.hit(100, 100, 1920, 1200, 18));
        layout.maximized = -1;
        assertEquals(before.pane(0, 1920, 1200, 18).x, layout.pane(0, 1920, 1200, 18).x);
        assertEquals(before.ratio, layout.ratio, 0);
        assertTrue(layout.swapped);
    }

    @Test
    public void paneTargetsRoundTripThroughFourRotationsAndFit() {
        for (int r = 0; r < 4; r++)
            for (int baseH : new int[] {1200, 1080})
                for (boolean vertical : new boolean[] {false, true}) {
                    int w = r % 2 == 0 ? 1920 : baseH, h = r % 2 == 0 ? baseH : 1920;
                    SplitLayout layout = new SplitLayout();
                    layout.enabled = true;
                    layout.vertical = vertical;
                    for (int pane = 0; pane < 2; pane++) {
                        SplitLayout.Box box = layout.pane(pane, w, h, 18);
                        for (float u : new float[] {.1f, .5f, .9f})
                            for (float v : new float[] {.1f, .5f, .9f}) {
                                float x = box.x + box.width * u, y = box.y + box.height * v;
                                float[] physical = Geometry.map(x / w, y / h, r, 1920, 1200, w, h);
                                float[] back =
                                        Geometry.unmap(
                                                physical[0], physical[1], r, 1920, 1200, w, h);
                                assertNotNull(back);
                                assertEquals(x, back[0], .001f);
                                assertEquals(y, back[1], .001f);
                                assertEquals(pane, layout.hit(back[0], back[1], w, h, 18));
                            }
                    }
                }
    }

    @Test
    public void oversizedGapStillPartitionsSmallWorkspace() {
        SplitLayout layout = new SplitLayout();
        layout.enabled = true;
        SplitLayout.Box a = layout.pane(0, 80, 60, 100),
                b = layout.pane(1, 80, 60, 100),
                bar = layout.divider(80, 60, 100);
        assertEquals(80, a.width + b.width + bar.width);
        assertEquals(a.width, bar.x);
        assertEquals(bar.x + bar.width, b.x);
        assertEquals(-1, layout.hit(-1, 30, 80, 60, 100));
        assertEquals(-1, layout.hit(80, 30, 80, 60, 100));
    }
}
