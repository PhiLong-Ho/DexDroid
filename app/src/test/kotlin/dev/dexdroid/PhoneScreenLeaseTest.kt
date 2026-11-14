package dev.dexdroid

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class PhoneScreenLeaseTest {
    @Test
    fun renewalUsesAbsoluteAppDeadlineAndCannotRebaseDelayedWork() {
        val lease = PhoneScreenLease(8, 5000)
        assertTrue(lease.renew(8, 5900, 1000))
        assertFalse(lease.renew(8, 5900, 2000))
        assertFalse(lease.renew(8, 5800, 2000))
        assertNull(lease.reason(5899))
        assertEquals(PhoneScreenResult.Reason.EXPIRED, lease.reason(5900))
    }

    @Test
    fun expiredAndTerminalLeasesCannotBeRevivedByQueuedRenewals() {
        val expired = PhoneScreenLease(1, 1000)
        assertFalse(expired.renew(1, 5000, 1000))
        assertFalse(expired.beginOff(1001))
        val closed = PhoneScreenLease(2, 5000)
        closed.close(PhoneScreenResult.Reason.OWNER_LOST)
        assertFalse(closed.renew(2, 6000, 2000))
        assertFalse(closed.beginOff(2000))
        assertEquals(PhoneScreenResult.Reason.OWNER_LOST, closed.reason(2000))
    }

    @Test
    fun wrongOwnerEpochAndOverlongDeadlineDoNotExtendLiveLease() {
        val lease = PhoneScreenLease(10, 4000)
        assertFalse(lease.renew(11, 5000, 1000))
        assertFalse(lease.renew(10, 6001, 1000))
        assertEquals(PhoneScreenResult.Reason.EXPIRED, lease.reason(4000))
        assertFalse(PhoneScreenLease.validDeadline(Long.MAX_VALUE, -1))
    }

    @Test
    fun closeBeforeActuationAdmitsNoOff() {
        val lease = PhoneScreenLease(1, 5000)
        lease.close(PhoneScreenResult.Reason.CLOSED)
        assertFalse(
            lease.runOff({ 1 }, { fail("Closed lease wrote OFF") }, { fail("No OFF was admitted") })
        )
    }

    @Test
    fun closeDuringBlockedOffIsNonblockingAndNormalIsTheNextPowerCall() {
        val lease = PhoneScreenLease(1, 5000)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val done = CountDownLatch(1)
        val calls = mutableListOf<String>()
        val thread = Thread {
            try {
                lease.runOff(
                    { 10 },
                    {
                        calls += "OFF"
                        entered.countDown()
                        check(release.await(2, TimeUnit.SECONDS))
                    },
                    { calls += "NORMAL" },
                )
            } finally {
                done.countDown()
            }
        }
        thread.start()
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        try {
            // This must return while the simulated native OFF is still blocked.
            lease.close(PhoneScreenResult.Reason.CLOSED)
            assertFalse(lease.renew(1, 6000, 1000))
        } finally {
            release.countDown()
        }
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals(listOf("OFF", "NORMAL"), calls)
        assertFalse(lease.beginOff(20))
    }

    @Test
    fun expiryDuringOffRestoresBeforeAnySubsequentWork() {
        var now = 50L
        val lease = PhoneScreenLease(1, 100)
        val calls = mutableListOf<String>()
        assertTrue(
            lease.runOff(
                { now },
                {
                    calls += "OFF"
                    now = 100
                },
                { calls += "NORMAL" },
            )
        )
        assertEquals(listOf("OFF", "NORMAL"), calls)
        assertFalse(lease.runOff({ now }, { fail("Repeated OFF") }, {}))
    }
}
