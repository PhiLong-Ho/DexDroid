package dev.dexdroid;

import static org.junit.Assert.*;

import org.junit.Test;

public class InputEventFlagsTest {
    @Test
    public void preservesSecurityAndGeneratedGestureFlags() {
        for (int api : new int[] {30, 31, 33, 36, 37})
            for (int flag : new int[] {0, 1, 2, 3, 8, 11})
                assertEquals(flag, InputEventFlags.motion(flag | 0x20000, api));
    }

    @Test
    public void removesSourceWindowAndVendorRoutingMarks() {
        for (int api : new int[] {30, 31, 33, 36, 37})
            assertEquals(3, InputEventFlags.motion(0xC0020007, api));
    }

    @Test
    public void cancellationStateIsRetainedOnlyOnSupportingPlatforms() {
        assertEquals(3, InputEventFlags.motion(35, 30));
        assertEquals(35, InputEventFlags.motion(35, 33));
        assertEquals(35, InputEventFlags.motion(0x20023, 37));
    }
}
