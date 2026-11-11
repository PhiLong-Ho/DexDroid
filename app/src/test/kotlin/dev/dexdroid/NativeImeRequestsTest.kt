package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class NativeImeRequestsTest {
    @Test
    fun unchangedConfigurationDoesNotCreateMorePolicyWork() {
        val model = NativeImeRequests()
        val owner = Any()
        assertNotNull(model.configure(listOf(NativeImeRequests.Target(owner, 3)), true))
        assertNull(model.configure(listOf(NativeImeRequests.Target(owner, 3)), true))
    }

    @Test
    fun oldOwnerCannotConfirmReusedDisplayNumber() {
        val model = NativeImeRequests()
        val old = NativeImeRequests.Target(Any(), 3)
        val request = model.configure(listOf(old), true)!!
        model.configure(listOf(NativeImeRequests.Target(Any(), 3)), true)
        assertFalse(
            model.accept(old, request.serial, NativeImeResult(NativeImeResult.State.CONFIGURED))
        )
        assertEquals(NativeImeResult.State.PENDING, model.results.single().state)
    }

    @Test
    fun oldRequestCannotOverwriteNewPreference() {
        val model = NativeImeRequests()
        val target = NativeImeRequests.Target(Any(), 3)
        val old = model.configure(listOf(target), true)!!
        val next = model.configure(listOf(target), false)!!
        assertFalse(
            model.accept(target, old.serial, NativeImeResult(NativeImeResult.State.CONFIGURED))
        )
        assertTrue(
            model.accept(target, next.serial, NativeImeResult(NativeImeResult.State.DEFAULT))
        )
        assertFalse(model.request.enabled)
    }

    @Test
    fun oneUnavailableSplitPaneDoesNotInvalidateOtherPane() {
        val model = NativeImeRequests()
        val targets = listOf(NativeImeRequests.Target(Any(), 3), NativeImeRequests.Target(Any(), 4))
        val request = model.configure(targets, true)!!
        model.accept(targets[0], request.serial, NativeImeResult(NativeImeResult.State.CONFIGURED))
        model.accept(
            targets[1],
            request.serial,
            NativeImeResult(
                NativeImeResult.State.UNAVAILABLE,
                NativeImeResult.Reason.PERMISSION_DENIED,
            ),
        )
        assertEquals(
            listOf(NativeImeResult.State.CONFIGURED, NativeImeResult.State.UNAVAILABLE),
            model.results.map { it.state },
        )
    }

    @Test
    fun timeoutPreservesActualOutcomeAndAllowsLateCurrentReadback() {
        val model = NativeImeRequests()
        val targets = listOf(NativeImeRequests.Target(Any(), 3), NativeImeRequests.Target(Any(), 4))
        val request = model.configure(targets, true)!!
        model.accept(targets[0], request.serial, NativeImeResult(NativeImeResult.State.CONFIGURED))
        assertTrue(model.timeout(request.serial))
        assertEquals(NativeImeResult.State.CONFIGURED, model.results[0].state)
        assertEquals(NativeImeResult.Reason.TIMEOUT, model.results[1].reason)
        assertTrue(
            model.accept(
                targets[1],
                request.serial,
                NativeImeResult(NativeImeResult.State.CONFIGURED),
            )
        )
    }

    @Test
    fun retiringSessionRejectsLateCallbacksAndTimeout() {
        val model = NativeImeRequests()
        val target = NativeImeRequests.Target(Any(), 3)
        val old = model.configure(listOf(target), true)!!
        model.configure(emptyList(), true)
        assertFalse(
            model.accept(target, old.serial, NativeImeResult(NativeImeResult.State.CONFIGURED))
        )
        assertFalse(model.timeout(old.serial))
        assertTrue(model.results.isEmpty())
    }
}
