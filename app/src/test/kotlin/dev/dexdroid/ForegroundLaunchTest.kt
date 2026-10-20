package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class ForegroundLaunchTest {
    private fun root(visible: Boolean? = false): DisplayTasks.Root =
        DisplayTasks.Root().apply {
            id = 7
            taskId = 9
            display = 4
            tasks = 1
            nativeTaskRows = 1
            nativeUserZero = true
            matches = true
            exactComponent = true
            nativeVisible = visible
            fullscreen = true
        }

    private class Attempt(
        val result: Int = 2,
        val finished: Boolean = true,
        val interrupted: Boolean = false,
    ) : ForegroundLaunch.Operation {
        var starts = 0
        var waits = 0
        var closes = 0

        override fun start(): Int {
            starts++
            return result
        }

        override fun awaitCompletion(): Boolean {
            waits++
            if (interrupted) throw InterruptedException("Cancelled native completion")
            return finished
        }

        override fun close() {
            closes++
        }
    }

    @Test
    fun onlyConfirmedSoleHiddenExactFullscreenTaskUsesCompletion() {
        assertEquals(ForegroundLaunch.Target(7, 9, 4), ForegroundLaunch.eligible(listOf(root()), 4))
        for (invalid in
            listOf(
                root(true),
                root(null),
                root().apply { exactComponent = false },
                root().apply { tasks = 2 },
                root().apply { fullscreen = false },
                root().apply { display = 0 },
            )) {
            assertNull(ForegroundLaunch.eligible(listOf(invalid), 4))
        }
        assertNull(ForegroundLaunch.eligible(listOf(root(), root()), 4))
        assertNull(
            ForegroundLaunch.eligible(listOf(root(), root().apply { exactComponent = false }), 4)
        )
    }

    @Test
    fun completionRequiresSameVisibleTaskAndDisplay() {
        val target = ForegroundLaunch.Target(7, 9, 4)
        val attempt = Attempt()
        ForegroundLaunch.complete(attempt) {
            ForegroundLaunch.unchanged(target, listOf(root(true)))
        }
        assertEquals(1, attempt.starts)
        assertEquals(1, attempt.waits)
        assertEquals(1, attempt.closes)
        for (changed in
            listOf(
                root(false),
                root(null),
                root(true).apply { display = 0 },
                root(true).apply { taskId = 10 },
                root(true).apply { id = 8 },
                root(true).apply { exactComponent = false },
                root(true).apply { fullscreen = false },
                root(true).apply { tasks = 2 },
            )) {
            assertFalse(ForegroundLaunch.unchanged(target, listOf(changed)))
        }
        assertFalse(ForegroundLaunch.unchanged(target, listOf(root(true), root(true))))
    }

    @Test
    fun missingCallbackDoesNotInferCompletionFromStartCodeOrRetry() {
        for (code in listOf(0, 2, 3)) {
            val attempt = Attempt(result = code, finished = false)
            var validated = false
            assertThrows(IllegalStateException::class.java) {
                ForegroundLaunch.complete(attempt) {
                    validated = true
                    true
                }
            }
            assertFalse(validated)
            assertEquals(1, attempt.starts)
            assertEquals(1, attempt.waits)
            assertEquals(1, attempt.closes)
        }
    }

    @Test
    fun rejectedStartAndLostOwnershipTerminateWithoutAnotherStart() {
        val rejected = Attempt(result = -92)
        assertThrows(IllegalStateException::class.java) {
            ForegroundLaunch.complete(rejected) {
                fail("Rejected start must not validate")
                false
            }
        }
        assertEquals(1, rejected.starts)
        assertEquals(0, rejected.waits)
        assertEquals(1, rejected.closes)
        val moved = Attempt()
        assertThrows(IllegalStateException::class.java) {
            ForegroundLaunch.complete(moved) { false }
        }
        assertEquals(1, moved.starts)
        assertEquals(1, moved.waits)
        assertEquals(1, moved.closes)
    }

    @Test
    fun interruptedCompletionClosesBeforeRestoringInterrupt() {
        val attempt = Attempt(interrupted = true)
        try {
            assertThrows(InterruptedException::class.java) {
                ForegroundLaunch.complete(attempt) {
                    fail("Interrupted launch must not validate")
                    false
                }
            }
            assertEquals(1, attempt.starts)
            assertEquals(1, attempt.closes)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }
}
