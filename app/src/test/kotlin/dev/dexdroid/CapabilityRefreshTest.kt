package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class CapabilityRefreshTest {
    private fun ready(): CapabilityRefresh<String> =
        CapabilityRefresh<String>().apply {
            changeOwner(Any())
            attach()
        }

    @Test
    fun repeatedRefreshJoinsOneReadAndDoesNotQueueAnother() {
        val state = ready()
        val request = state.request()!!
        repeat(100) { assertNull(state.request()) }
        assertNull(state.complete(request, "observed"))
        assertEquals("observed", state.value)
        assertEquals(CapabilityRefresh.Phase.READY, state.phase)
        assertNull(state.takePending())
    }

    @Test
    fun deadlineRetainsSameOwnerEarlierValueAndRejectsLateSuccess() {
        val state = ready()
        state.complete(state.request()!!, "earlier")
        val later = state.request()!!
        assertTrue(state.timeout(later))
        assertTrue(state.blocked)
        assertEquals(CapabilityRefresh.Phase.FAILED, state.phase)
        assertEquals("earlier", state.value)
        state.complete(later, "late")
        assertEquals("earlier", state.value)
        assertEquals(CapabilityRefresh.Phase.FAILED, state.phase)
    }

    @Test
    fun ownerReplacementClearsReadingsAndCoalescesOneNewOwnerRead() {
        val state = ready()
        val old = state.request()!!
        repeat(50) {
            state.changeOwner(Any())
            assertNull(state.request())
        }
        assertNull(state.value)
        assertTrue(state.blocked)
        val next = state.complete(old, "obsolete")!!
        assertNull(state.value)
        assertNull(state.complete(next, "current"))
        assertEquals("current", state.value)
    }

    @Test
    fun oldFailureCannotDowngradeReplacement() {
        val state = ready()
        val old = state.request()!!
        state.changeOwner(Any())
        val next = state.complete(old, null)!!
        state.complete(next, "current")
        state.complete(old, null)
        assertEquals(CapabilityRefresh.Phase.READY, state.phase)
        assertEquals("current", state.value)
    }

    @Test
    fun closeLastPageRejectsLateResultAndDoesNotLaunchReplacementWork() {
        val state = CapabilityRefresh<String>()
        state.changeOwner(Any())
        val page = state.attach()
        val request = state.request()!!
        state.detach(page)
        assertNull(state.complete(request, "late"))
        assertNull(state.value)
        assertNull(state.request())
    }

    @Test
    fun closingOneSurfaceKeepsOtherSurfaceReadAlive() {
        val state = ready()
        val second = state.attach()
        val request = state.request()!!
        state.detach(second)
        state.complete(request, "still visible")
        assertEquals("still visible", state.value)
    }

    @Test
    fun timeoutDoesNotCreateReplacementWorkerUntilOriginalReadEnds() {
        val state = ready()
        val timedOut = state.request()!!
        state.timeout(timedOut)
        repeat(100) { assertNull(state.request()) }
        val next = state.complete(timedOut, "ignored")!!
        assertNull(state.value)
        state.complete(next, "retry")
        assertEquals("retry", state.value)
    }

    @Test
    fun failedRefreshKeepsEarlierReadingWithFailureAfterPageClose() {
        val state = CapabilityRefresh<String>()
        state.changeOwner(Any())
        val page = state.attach()
        state.complete(state.request()!!, "earlier")
        state.complete(state.request()!!, null)
        state.detach(page)
        assertEquals("earlier", state.value)
        assertEquals(CapabilityRefresh.Phase.FAILED, state.phase)
    }

    @Test
    fun serviceDestructionRejectsLateSuccessFailureAndFutureRefresh() {
        val state = ready()
        val pending = state.request()!!
        state.close()
        assertNull(state.complete(pending, "late"))
        assertNull(state.complete(pending, null))
        state.attach()
        assertNull(state.request())
        assertNull(state.value)
    }
}
