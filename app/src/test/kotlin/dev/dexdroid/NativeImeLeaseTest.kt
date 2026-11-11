package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class NativeImeLeaseTest {
    private class Port : NativeImeLease.Port {
        var policy = 1
        var active = true
        var attached = true
        var reads = 0
        val writes = mutableListOf<Int>()
        var afterRead: (() -> Unit)? = null
        var writeFailure: Exception? = null
        var ignoreWrite = false

        override fun active() = active && attached

        override fun owned() = attached

        override fun read(): Int {
            reads++
            val result = policy
            afterRead?.invoke()
            return result
        }

        override fun write(policy: Int, retiring: Boolean) {
            check(attached && (retiring || active))
            writes.add(policy)
            if (!ignoreWrite) this.policy = policy
            writeFailure?.let { throw it }
        }
    }

    @Test
    fun enableReadsBackAndDisableRestoresExactBaseline() {
        val port = Port().apply { policy = 2 }
        val lease = NativeImeLease(port)
        assertEquals(NativeImeResult.State.CONFIGURED, lease.apply(true).state)
        assertEquals(0, port.policy)
        assertEquals(NativeImeResult.State.DEFAULT, lease.apply(false).state)
        assertEquals(2, port.policy)
        assertEquals(listOf(0, 2), port.writes)
    }

    @Test
    fun alreadyLocalDoesNotAcquireAnOverrideOrChangeItAtCleanup() {
        val port = Port().apply { policy = 0 }
        val lease = NativeImeLease(port)
        assertEquals(NativeImeResult.State.CONFIGURED, lease.apply(true).state)
        assertEquals(NativeImeResult.State.DEFAULT, lease.restore(true).state)
        assertEquals(0, port.policy)
        assertTrue(port.writes.isEmpty())
    }

    @Test
    fun externalPolicyWriterIsPreservedDuringRelease() {
        val port = Port()
        val lease = NativeImeLease(port)
        lease.apply(true)
        port.policy = 2
        assertEquals(NativeImeResult.Reason.EXTERNAL_CHANGE, lease.restore(true).reason)
        assertEquals(2, port.policy)
        assertEquals(listOf(0), port.writes)
    }

    @Test
    fun throwingSetterMayHaveMutatedSoCleanupStillRestores() {
        val port = Port().apply { writeFailure = IllegalStateException() }
        val lease = NativeImeLease(port)
        assertEquals(NativeImeResult.Reason.WRITE_FAILED, lease.apply(true).reason)
        assertEquals(0, port.policy)
        port.writeFailure = null
        assertEquals(NativeImeResult.State.DEFAULT, lease.restore(true).state)
        assertEquals(1, port.policy)
    }

    @Test
    fun ignoredSetterIsNotReportedAsConfigured() {
        val port = Port().apply { ignoreWrite = true }
        assertEquals(
            NativeImeResult.Reason.READBACK_MISMATCH,
            NativeImeLease(port).apply(true).reason,
        )
        assertEquals(1, port.policy)
    }

    @Test
    fun permissionRejectionIsExplicitAndDoesNotEscape() {
        val port =
            Port().apply {
                ignoreWrite = true
                writeFailure = NativeImePolicyException(NativeImeResult.Reason.PERMISSION_DENIED)
            }
        val result = NativeImeLease(port).apply(true)
        assertEquals(NativeImeResult.State.UNAVAILABLE, result.state)
        assertEquals(NativeImeResult.Reason.PERMISSION_DENIED, result.reason)
    }

    @Test
    fun unknownPolicyIsInconclusiveAndNeverWritten() {
        val port = Port().apply { policy = 99 }
        assertEquals(NativeImeResult.Reason.READ_FAILED, NativeImeLease(port).apply(true).reason)
        assertTrue(port.writes.isEmpty())
    }

    @Test
    fun retirementDuringBlockedReadPreventsNewOverride() {
        val port = Port().apply { afterRead = { active = false } }
        assertEquals(NativeImeResult.State.RETIRED, NativeImeLease(port).apply(true).state)
        assertTrue(port.writes.isEmpty())
    }

    @Test
    fun releaseDuringCleanupReadPreventsRestoreOnRetiredDisplay() {
        val port = Port()
        val lease = NativeImeLease(port)
        lease.apply(true)
        port.afterRead = { port.attached = false }
        assertEquals(NativeImeResult.State.RETIRED, lease.restore(true).state)
        assertEquals(listOf(0), port.writes)
    }

    @Test
    fun failedRestoreRemainsUnconfirmed() {
        val port = Port()
        val lease = NativeImeLease(port)
        lease.apply(true)
        port.ignoreWrite = true
        assertEquals(NativeImeResult.Reason.CLEANUP_UNCONFIRMED, lease.restore(true).reason)
        assertEquals(0, port.policy)
    }

    @Test
    fun disabledFreshDisplayDoesNotQueryOrMutatePolicy() {
        val port = Port()
        assertEquals(NativeImeResult.State.DEFAULT, NativeImeLease(port).apply(false).state)
        assertEquals(0, port.reads)
        assertTrue(port.writes.isEmpty())
    }
}
