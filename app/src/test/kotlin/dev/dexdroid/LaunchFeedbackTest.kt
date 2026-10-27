package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class LaunchFeedbackTest {
    @Test
    fun acceptanceRemainsPendingUntilActualCompletion() {
        val feedback = LaunchFeedback()
        val ticket = feedback.begin("Firefox", "left", false)
        assertEquals("Opening Firefox on left…", feedback.state!!.notice)
        feedback.expire(ticket)
        assertEquals(LaunchFeedback.Phase.PENDING, feedback.state!!.phase)
        assertNull(feedback.lastResult)
        assertTrue(feedback.complete(ticket, null))
        assertEquals("Firefox opened", feedback.state!!.notice)
        feedback.expire(ticket)
        assertNull(feedback.state)
        assertEquals("Firefox opened", feedback.lastResult)
    }

    @Test
    fun supersededCompletionAndExpiryCannotChangeNewLaunch() {
        val feedback = LaunchFeedback()
        val first = feedback.begin("First", null, false)
        val second = feedback.begin("Second", "bottom", false)
        assertFalse(feedback.complete(first, "Old error"))
        assertFalse(feedback.cancel(first))
        feedback.expire(first)
        assertEquals("Opening Second on bottom…", feedback.state!!.notice)
        assertTrue(feedback.complete(second, null))
        assertFalse(feedback.complete(second, "Duplicate late error"))
        assertEquals("Second opened", feedback.lastResult)
    }

    @Test
    fun failureKeepsPhoneRecoveryAfterMonitorNoticeExpires() {
        val feedback = LaunchFeedback()
        val ticket = feedback.begin("Calendar", "right", false)
        feedback.complete(ticket, "Android rejected the app.")
        assertTrue(feedback.state!!.monitorVisible)
        feedback.expire(ticket)
        assertFalse(feedback.state!!.monitorVisible)
        assertEquals("Calendar didn’t open", feedback.state!!.notice)
        assertTrue(
            feedback.lastResult!!.contains("Android rejected the app. Open Apps to try again.")
        )
    }

    @Test
    fun deferredDispatchAndCancellationRemainSameOperation() {
        val feedback = LaunchFeedback()
        val ticket = feedback.begin("Settings", null, true)
        assertTrue(feedback.state!!.notice.startsWith("Starting desktop"))
        assertTrue(feedback.dispatched(ticket, "top"))
        assertEquals("Opening Settings on top…", feedback.state!!.notice)
        assertTrue(feedback.cancel(ticket))
        assertFalse(feedback.complete(ticket, null))
        assertFalse(feedback.dispatched(ticket, "left"))
        feedback.expire(ticket)
        assertNull(feedback.state)
        assertTrue(feedback.lastResult!!.contains("Already completed app changes may remain"))
    }

    @Test
    fun pendingRecordTransfersCompletionGuardAndIdentityOnce() {
        val feedback = LaunchFeedback()
        val ticket = feedback.begin("App", null, true)
        var authorized = true
        var callbacks = 0
        val request =
            PendingLaunch(
                "app/component",
                3,
                { error ->
                    callbacks++
                    feedback.complete(ticket, error)
                },
                { authorized },
                { feedback.cancel(ticket) },
                { feedback.dispatched(ticket, "left") },
            )
        var sends = 0
        request.deliver {
            sends++
            assertEquals("app/component", it.component)
            assertEquals(3, it.rotation)
            assertTrue(it.allowed!!())
            it.started!!()
            assertEquals("Opening App on left…", feedback.state!!.notice)
            authorized = false
            assertFalse(it.allowed())
            it.completed!!("Not permitted now.")
        }
        request.deliver { sends++ }
        request.discard()
        assertEquals(1, sends)
        assertEquals(1, callbacks)
        assertEquals(LaunchFeedback.Phase.FAILURE, feedback.state!!.phase)
    }

    @Test
    fun replacedPendingRequestCannotRetireNewerFeedback() {
        val feedback = LaunchFeedback()
        val old = feedback.begin("Old", null, true)
        val pending = PendingLaunch("old", null, null, null, { feedback.cancel(old) }, null)
        val current = feedback.begin("New", null, true)
        pending.discard()
        pending.deliver { fail("Retired pending work dispatched") }
        assertSame(current, feedback.state!!.ticket)
        assertEquals(LaunchFeedback.Phase.PENDING, feedback.state!!.phase)
    }
}
