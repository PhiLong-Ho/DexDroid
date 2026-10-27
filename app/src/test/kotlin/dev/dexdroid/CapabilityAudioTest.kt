package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class CapabilityAudioTest {
    @Test
    fun onlyKnownSettledRejectionSuppliesUnavailableReason() {
        val denied =
            SessionAudioState(
                message =
                    "Audio output control unavailable. Android did not grant shell audio-routing permission."
            )
        assertEquals(
            DeviceCapabilities.AudioUnavailableReason.DENIED,
            denied.capabilityFacts(true).unavailableReason,
        )
        assertNull(denied.copy(uncertain = true).capabilityFacts(true).unavailableReason)
        assertNull(denied.copy(pending = true).capabilityFacts(true).unavailableReason)
        assertNull(denied.capabilityFacts(false).unavailableReason)
    }

    @Test
    fun failedTransportOrPolicyConfirmationStaysUnknown() {
        val failed =
            SessionAudioState(
                message =
                    "Audio output control unavailable. Android could not confirm the audio policy."
            )
        assertNull(failed.capabilityFacts(true).unavailableReason)
        assertFalse(failed.capabilityFacts(true).confirmed)
        val uncertain = failed.copy(uncertain = true).capabilityFacts(true)
        val row = DeviceCapabilities.report(DeviceCapabilities.Snapshot(audio = uncertain)).items[5]
        assertEquals(DeviceCapabilities.Verdict.NOT_CHECKED, row.verdict)
    }

    @Test
    fun policyConfirmationDoesNotSurviveInactiveDesktop() {
        val selected = SessionAudioState(available = true, confirmed = true)
        assertTrue(selected.capabilityFacts(true).confirmed)
        assertFalse(selected.capabilityFacts(false).confirmed)
        assertFalse(selected.capabilityFacts(false).available)
    }
}
