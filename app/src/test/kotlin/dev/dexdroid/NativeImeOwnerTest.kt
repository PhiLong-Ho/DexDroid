package dev.dexdroid

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class NativeImeOwnerTest {
    @Test
    fun rapidRequestsKeepOnlyLastIntentBehindBlockedWork() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val done = CountDownLatch(1)
        val calls = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
        val stale = AtomicInteger()
        val owner =
            NativeImeOwner(
                { true },
                { enabled ->
                    calls.add(enabled)
                    if (calls.size == 1) {
                        entered.countDown()
                        check(release.await(2, TimeUnit.SECONDS))
                    }
                    NativeImeResult(NativeImeResult.State.CONFIGURED)
                },
                { null },
            )
        try {
            owner.request(true) { stale.incrementAndGet() }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            repeat(50) { owner.request(true) { stale.incrementAndGet() } }
            owner.request(false) { done.countDown() }
            release.countDown()
            assertTrue(done.await(2, TimeUnit.SECONDS))
            assertEquals(listOf(true, false), calls.toList())
            assertEquals(0, stale.get())
        } finally {
            release.countDown()
            owner.retireAndAwait(2000)
            owner.released()
        }
    }

    @Test
    fun blockedConfigurationCannotHoldReleaseOrWriteAfterRetirement() {
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val cleaned = CountDownLatch(1)
        val writes = AtomicInteger()
        val callbacks = AtomicInteger()
        lateinit var owner: NativeImeOwner
        val lease =
            NativeImeLease(
                object : NativeImeLease.Port {
                    override fun active() = owner.active()

                    override fun owned() = owner.owned()

                    override fun read(): Int {
                        entered.countDown()
                        check(unblock.await(2, TimeUnit.SECONDS))
                        return 1
                    }

                    override fun write(policy: Int, retiring: Boolean) {
                        writes.incrementAndGet()
                    }
                }
            )
        owner =
            NativeImeOwner(
                { true },
                { lease.apply(it) },
                {
                    try {
                        lease.restore(true)
                    } finally {
                        cleaned.countDown()
                    }
                },
            )
        try {
            owner.request(true) { callbacks.incrementAndGet() }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertFalse(owner.retireAndAwait(30))
            owner.released()
            unblock.countDown()
            assertTrue(cleaned.await(2, TimeUnit.SECONDS))
            assertEquals(0, writes.get())
            assertEquals(0, callbacks.get())
        } finally {
            unblock.countDown()
            owner.released()
        }
    }

    @Test
    fun blockedCleanupCannotHoldReleaseOrRestoreAfterRelease() {
        val configured = CountDownLatch(1)
        val cleanupRead = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val cleaned = CountDownLatch(1)
        val writes = AtomicInteger()
        val reads = AtomicInteger()
        lateinit var owner: NativeImeOwner
        var value = 1
        val lease =
            NativeImeLease(
                object : NativeImeLease.Port {
                    override fun active() = owner.active()

                    override fun owned() = owner.owned()

                    override fun read(): Int {
                        if (reads.incrementAndGet() == 3) {
                            cleanupRead.countDown()
                            check(unblock.await(2, TimeUnit.SECONDS))
                        }
                        return value
                    }

                    override fun write(policy: Int, retiring: Boolean) {
                        writes.incrementAndGet()
                        value = policy
                    }
                }
            )
        owner =
            NativeImeOwner(
                { true },
                { lease.apply(it) },
                {
                    try {
                        lease.restore(true)
                    } finally {
                        cleaned.countDown()
                    }
                },
            )
        try {
            owner.request(true) { configured.countDown() }
            assertTrue(configured.await(2, TimeUnit.SECONDS))
            assertFalse(owner.retireAndAwait(30))
            assertTrue(cleanupRead.await(2, TimeUnit.SECONDS))
            owner.released()
            unblock.countDown()
            assertTrue(cleaned.await(2, TimeUnit.SECONDS))
            assertEquals(1, writes.get())
        } finally {
            unblock.countDown()
            owner.released()
        }
    }
}
