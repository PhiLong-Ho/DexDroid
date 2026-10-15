package dev.dexdroid;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Host coverage of coupled rotation, Fit and input coordinates without shell privileges. */
@RunWith(Parameterized.class)
public class GeometryTest {
    @Parameterized.Parameters(name = "rotation={0}, desktop={1}x{2}")
    public static Collection<Object[]> cases() {
        Collection<Object[]> cases = new ArrayList<>();
        for (int rotation = 0; rotation < 4; rotation++) {
            for (int[] resolution : new int[][] {{1920, 1200}, {1280, 800}, {1920, 1080}}) {
                cases.add(new Object[] {rotation, resolution[0], resolution[1]});
            }
        }
        return cases;
    }

    private final int rotation;
    private final int width;
    private final int height;

    public GeometryTest(int rotation, int width, int height) {
        this.rotation = rotation;
        this.width = rotation % 2 == 0 ? width : height;
        this.height = rotation % 2 == 0 ? height : width;
    }

    @Test
    public void centerStaysAtPhysicalCenter() {
        float[] center = Geometry.map(.5f, .5f, rotation, 1920, 1200, width, height);
        assertArrayEquals(new float[] {960, 600}, center, .01f);
    }

    @Test
    public void interiorGridRoundTripsInEveryRotationAndFit() {
        for (int x = 1; x < 20; x++) {
            for (int y = 1; y < 20; y++) {
                float u = x / 20f;
                float v = y / 20f;
                float[] physical = Geometry.map(u, v, rotation, 1920, 1200, width, height);
                float[] desktop =
                        Geometry.unmap(
                                physical[0], physical[1], rotation, 1920, 1200, width, height);
                assertNotNull(desktop);
                assertArrayEquals(new float[] {u * width, v * height}, desktop, .01f);
            }
        }
    }

    @Test
    public void touchesOutsidePhysicalDisplayAreRejected() {
        assertNull(Geometry.unmap(-1, 600, rotation, 1920, 1200, width, height));
        assertNull(Geometry.unmap(1921, 600, rotation, 1920, 1200, width, height));
        assertNull(Geometry.unmap(960, -1, rotation, 1920, 1200, width, height));
        assertNull(Geometry.unmap(960, 1201, rotation, 1920, 1200, width, height));
    }

    @Test
    public void fitBarsRejectContactsAndContentRemainsInteractive() {
        // Physical aspect is independent of upright desktop orientation.
        float[] edge1 = Geometry.map(0, 0, rotation, 1920, 1200, width, height);
        float[] edge2 = Geometry.map(1, 1, rotation, 1920, 1200, width, height);
        float top = Math.min(edge1[1], edge2[1]);
        float left = Math.min(edge1[0], edge2[0]);
        if (top > .1f) {
            assertNull(Geometry.unmap(960, top / 2, rotation, 1920, 1200, width, height));
            assertNull(Geometry.unmap(960, 1200 - top / 2, rotation, 1920, 1200, width, height));
        }
        if (left > .1f) {
            assertNull(Geometry.unmap(left / 2, 600, rotation, 1920, 1200, width, height));
            assertNull(Geometry.unmap(1920 - left / 2, 600, rotation, 1920, 1200, width, height));
        }
        assertNotNull(Geometry.unmap(960, 600, rotation, 1920, 1200, width, height));
    }

    @Test
    public void unavailablePhysicalSizeRejectsInput() {
        assertNull(Geometry.unmap(0, 0, rotation, 0, 1200, width, height));
        assertNull(Geometry.unmap(0, 0, rotation, 1920, 0, width, height));
    }

    @Test
    public void densityRecommendationIsBoundedAndMonotonic() {
        int previous = 120;
        for (int shortEdge = 0; shortEdge <= 5000; shortEdge += 5) {
            int dpi = Geometry.recommendedDpi(shortEdge);
            assertTrue(dpi >= previous && dpi >= 120 && dpi <= 480);
            assertEquals(0, dpi % 20);
            previous = dpi;
        }
        assertEquals(160, Geometry.recommendedDpi(800));
        assertEquals(240, Geometry.recommendedDpi(1200));
    }
}
