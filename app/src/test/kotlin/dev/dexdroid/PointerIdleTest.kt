package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class PointerIdleTest {
    @Test
    fun oldServiceHoldCannotReleaseRecreatedPolicyOwner() {
        val old = PointerIdle().begin(PointerIdle.Source.PAD, 0)
        val fresh = PointerIdle()
        val current = fresh.begin(PointerIdle.Source.PAD, 100)
        assertFalse(fresh.release(PointerIdle.Source.PAD, old, 200))
        assertNull(fresh.deadline())
        assertTrue(fresh.release(PointerIdle.Source.PAD, current, 300))
    }

    @Test
    fun idleDeadlineAndRenewalRejectOldWork() {
        val p = PointerIdle()
        assertNull(p.deadline())
        p.activity(100)
        val old = p.revision
        assertFalse(p.due(5099, old))
        assertTrue(p.due(5100, old))
        p.activity(5101)
        assertFalse(p.due(6000, old))
        assertEquals(10101L, p.deadline())
    }

    @Test
    fun stationaryAndOverlappingHoldsStartIdleOnlyOnLastRelease() {
        val p = PointerIdle()
        val pad = p.begin(PointerIdle.Source.PAD, 0)
        val contact = p.begin(PointerIdle.Source.RELAY, 50)
        assertNull(p.deadline())
        assertFalse(p.due(60000, p.revision))
        p.release(PointerIdle.Source.RELAY, contact, 60000)
        assertNull(p.deadline())
        p.release(PointerIdle.Source.PAD, pad, 61000)
        assertEquals(66000L, p.deadline())
    }

    @Test
    fun replacedControllerCannotReleaseNewContact() {
        val p = PointerIdle()
        val old = p.begin(PointerIdle.Source.CONTROLLER, 0)
        val fresh = p.begin(PointerIdle.Source.CONTROLLER, 50)
        assertFalse(p.release(PointerIdle.Source.CONTROLLER, old, 60))
        assertNull(p.deadline())
        assertTrue(p.release(PointerIdle.Source.CONTROLLER, fresh, 70))
        assertEquals(5070L, p.deadline())
    }

    @Test
    fun takeoverAndTeardownInvalidateDeadlinesAndAbandonedPad() {
        val p = PointerIdle()
        val pad = p.begin(PointerIdle.Source.PAD, 0)
        val old = p.revision
        p.clear()
        assertFalse(p.release(PointerIdle.Source.PAD, pad, 6000))
        assertFalse(p.due(6000, old))
        assertNull(p.deadline())
        p.activity(7000)
        assertFalse(p.due(12000, old))
        assertTrue(p.due(12000, p.revision))
    }
}
