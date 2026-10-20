package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class AudioPolicyLeaseTest {
    private class Policy(var devices: List<String> = emptyList()) :
        AudioPolicyLease.Backend<String> {
        val writes = mutableListOf<List<String>>()
        var reject = false
        var unreadable = false
        var throwBefore = false
        var throwAfter = false
        var loseReadAfterWrite = false

        override fun read(): List<String> {
            check(!unreadable) { "Audio service unavailable" }
            return devices
        }

        override fun write(devices: List<String>): Boolean {
            writes.add(devices.toList())
            check(!throwBefore) { "Setter stopped before mutation" }
            if (reject) return false
            this.devices = devices.toList()
            if (loseReadAfterWrite) unreadable = true
            check(!throwAfter) { "Setter stopped after mutation" }
            return true
        }
    }

    @Test
    fun repeatedSelectionRestoresOriginalMultidevicePolicy() {
        val policy = Policy(listOf("earbuds", "speaker"))
        val lease = AudioPolicyLease(policy)
        lease.select("hdmi")
        lease.select("speaker")
        assertTrue(lease.release())
        assertEquals(listOf("earbuds", "speaker"), policy.devices)
        assertFalse(lease.owned())
    }

    @Test
    fun emptyBaselineIsRemovedAndRepeatedReleaseIsHarmless() {
        val policy = Policy()
        val lease = AudioPolicyLease(policy)
        lease.select("hdmi")
        assertTrue(lease.release())
        assertTrue(lease.release())
        assertEquals(listOf(listOf("hdmi"), emptyList<String>()), policy.writes)
    }

    @Test
    fun externalTakeoverIsNeverOverwritten() {
        val policy = Policy()
        val lease = AudioPolicyLease(policy)
        lease.select("hdmi")
        policy.devices = listOf("earbuds")
        assertFalse(lease.release())
        assertEquals(listOf("earbuds"), policy.devices)
        assertEquals(1, policy.writes.size)
    }

    @Test
    fun newExplicitSelectionAfterTakeoverRestoresTheNewBaseline() {
        val policy = Policy()
        val lease = AudioPolicyLease(policy)
        lease.select("hdmi")
        policy.devices = listOf("earbuds")
        assertFalse(lease.reconcile())
        lease.select("speaker")
        lease.release()
        assertEquals(listOf("earbuds"), policy.devices)
    }

    @Test
    fun rejectedSwitchPreservesPriorOwnedPolicy() {
        val policy = Policy()
        val lease = AudioPolicyLease(policy)
        lease.select("hdmi")
        policy.reject = true
        assertThrows(IllegalStateException::class.java) { lease.select("speaker") }
        assertTrue(lease.owned())
        policy.reject = false
        lease.release()
        assertTrue(policy.devices.isEmpty())
    }

    @Test
    fun unconfirmedReleaseRetainsRecoveryState() {
        val policy = Policy()
        val lease = AudioPolicyLease(policy)
        lease.select("hdmi")
        policy.reject = true
        assertThrows(IllegalStateException::class.java) { lease.release() }
        assertTrue(lease.owned())
        policy.reject = false
        lease.release()
        assertFalse(lease.owned())
    }

    @Test
    fun lostReadAccessDoesNotPretendCleanupSucceeded() {
        val policy = Policy()
        val lease = AudioPolicyLease(policy)
        lease.select("hdmi")
        policy.unreadable = true
        assertThrows(IllegalStateException::class.java) { lease.release() }
        assertTrue(lease.owned())
        policy.unreadable = false
        lease.release()
        assertTrue(policy.devices.isEmpty())
    }

    @Test
    fun normalizedReadbackStillOwnsAndRestoresPolicy() {
        val policy = Policy()
        val lease =
            AudioPolicyLease(policy) { a, b ->
                a.map { it.substringBefore(':') } == b.map { it.substringBefore(':') }
            }
        lease.select("hdmi:named-with-profiles")
        policy.devices = listOf("hdmi:metadata-stripped-by-android")
        assertTrue(lease.reconcile())
        assertTrue(lease.release())
        assertTrue(policy.devices.isEmpty())
    }

    @Test
    fun thrownSwitchBeforeMutationRetainsPriorOverrideForCleanup() {
        val policy = Policy()
        val lease = AudioPolicyLease(policy)
        lease.select("hdmi")
        policy.throwBefore = true
        assertThrows(IllegalStateException::class.java) { lease.select("speaker") }
        policy.throwBefore = false
        assertTrue(lease.reconcile())
        assertTrue(lease.release())
        assertTrue(policy.devices.isEmpty())
    }

    @Test
    fun thrownSwitchAfterMutationRetainsAttemptedOverrideForCleanup() {
        val policy = Policy()
        val lease = AudioPolicyLease(policy)
        lease.select("hdmi")
        policy.throwAfter = true
        assertThrows(IllegalStateException::class.java) { lease.select("speaker") }
        policy.throwAfter = false
        assertTrue(lease.release())
        assertTrue(policy.devices.isEmpty())
    }

    @Test
    fun unreadableSwitchKeepsBothPossibleOwnedPolicies() {
        for (actuallySwitched in listOf(true, false)) {
            val policy = Policy()
            val lease = AudioPolicyLease(policy)
            lease.select("hdmi")
            policy.loseReadAfterWrite = true
            assertThrows(IllegalStateException::class.java) { lease.select("speaker") }
            if (!actuallySwitched) policy.devices = listOf("hdmi")
            policy.unreadable = false
            policy.loseReadAfterWrite = false
            assertTrue(lease.release())
            assertTrue(policy.devices.isEmpty())
        }
    }
}
