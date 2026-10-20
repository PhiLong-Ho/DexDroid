package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

open class InputEventFlagsTest {
    @Test
    open fun preservesSecurityAndGeneratedGestureFlags() {
        for (api in intArrayOf(30, 31, 33, 36, 37)) for (flag in
            intArrayOf(0, 1, 2, 3, 8, 11)) assertEquals(
            flag.toLong(),
            InputEventFlags.motion(flag or 0x20000, api).toLong(),
        )
    }

    @Test
    open fun removesSourceWindowAndVendorRoutingMarks() {
        for (api in intArrayOf(30, 31, 33, 36, 37)) assertEquals(
            3,
            InputEventFlags.motion(-0x3ffdfff9, api).toLong(),
        )
    }

    @Test
    open fun cancellationStateIsRetainedOnlyOnSupportingPlatforms() {
        assertEquals(3, InputEventFlags.motion(35, 30).toLong())
        assertEquals(35, InputEventFlags.motion(35, 33).toLong())
        assertEquals(35, InputEventFlags.motion(0x20023, 37).toLong())
    }
}
