package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class WorkspaceRestoreTest {
    private class Driver : WorkspaceRestore.Driver {
        val actions = mutableListOf<String>()
        val messages = mutableListOf<String>()
        val callbacks = ArrayDeque<(String?) -> Unit>()
        var available = true

        private fun action(name: String, done: (String?) -> Unit) {
            actions.add(name)
            callbacks.add(done)
        }

        override fun validate(snapshot: WorkspaceSnapshot, done: (String?) -> Unit) =
            action("validate", done)

        override fun ready() = available

        override fun replace(done: (String?) -> Unit) = action("replace", done)

        override fun start(snapshot: WorkspaceSnapshot, done: (String?) -> Unit) =
            action("start", done)

        override fun launch(app: WorkspaceSnapshot.App, pane: Int, done: (String?) -> Unit) =
            action("app$pane", done)

        override fun finish(snapshot: WorkspaceSnapshot, done: (String?) -> Unit) =
            action("finish", done)

        override fun changed(message: String) {
            messages.add(message)
        }

        fun complete(error: String? = null) = callbacks.removeFirst().invoke(error)
    }

    private val snapshot =
        WorkspaceSnapshot(
            true,
            false,
            true,
            .6f,
            0,
            -1,
            1,
            WorkspaceSnapshot.App("fixture.reader/.Main", "Reader"),
            WorkspaceSnapshot.App("fixture.browser/.Main", "Browser"),
        )

    @Test
    fun waitsForRealCompletionsAndLaunchesSelectedPaneLast() {
        val driver = Driver()
        val restore = WorkspaceRestore(driver)
        restore.request(snapshot)
        assertEquals(listOf("validate"), driver.actions)
        repeat(7) { driver.complete() }
        assertEquals(
            listOf("validate", "validate", "replace", "start", "app1", "app0", "finish"),
            driver.actions,
        )
        assertFalse(restore.active)
        assertEquals("Workspace restored", driver.messages.last())
    }

    @Test
    fun missingDependenciesWaitWithoutReplacingAndDuplicateTapsCoalesce() {
        val driver = Driver().apply { available = false }
        val restore = WorkspaceRestore(driver)
        restore.request(snapshot)
        driver.complete()
        repeat(100) {
            restore.request(snapshot)
            restore.ready()
        }
        assertEquals(listOf("validate"), driver.actions)
        driver.available = true
        restore.ready()
        restore.ready()
        assertEquals(listOf("validate", "validate"), driver.actions)
        driver.complete()
        assertEquals(listOf("validate", "validate", "replace"), driver.actions)
    }

    @Test
    fun validationFailureDoesNotChangeExistingWorkspace() {
        val driver = Driver()
        val restore = WorkspaceRestore(driver)
        restore.request(snapshot)
        driver.complete("Reader is missing")
        assertEquals(listOf("validate"), driver.actions)
        assertFalse(restore.active)
        assertEquals("Workspace not changed. Reader is missing", driver.messages.last())
    }

    @Test
    fun secondAppFailureStopsSequenceAndReportsPartialResult() {
        val driver = Driver()
        val restore = WorkspaceRestore(driver)
        restore.request(snapshot)
        repeat(5) { driver.complete() }
        driver.complete("Reader launch failed")
        assertEquals(
            listOf("validate", "validate", "replace", "start", "app1", "app0"),
            driver.actions,
        )
        assertFalse(restore.active)
        assertEquals("Workspace partly restored. Reader launch failed", driver.messages.last())
    }

    @Test
    fun stopAtEveryBoundaryPreventsStaleContinuationAndFreshRequestIsIndependent() {
        for (boundary in 0..6) {
            val driver = Driver()
            val restore = WorkspaceRestore(driver)
            restore.request(snapshot)
            repeat(boundary) { driver.complete() }
            val count = driver.actions.size
            restore.cancel()
            driver.complete()
            assertEquals(count, driver.actions.size)
            restore.request(snapshot.copy(rotation = 2))
            assertEquals(count + 1, driver.actions.size)
            assertEquals("validate", driver.actions.last())
        }
    }

    @Test
    fun emptyPaneIsNotLaunchedAndDuplicateCallbackCannotAdvanceTwice() {
        val driver = Driver()
        val restore = WorkspaceRestore(driver)
        restore.request(snapshot.copy(first = null))
        val validate = driver.callbacks.first()
        driver.complete()
        validate(null)
        repeat(5) { driver.complete() }
        assertEquals(
            listOf("validate", "validate", "replace", "start", "app1", "finish"),
            driver.actions,
        )
        assertEquals("Workspace restored", driver.messages.last())
    }

    @Test
    fun cleanupFailurePreventsNewDisplaysAndAppLaunches() {
        val driver = Driver()
        val restore = WorkspaceRestore(driver)
        restore.request(snapshot)
        driver.complete()
        driver.complete()
        driver.complete("Cleanup unconfirmed")
        assertEquals(listOf("validate", "validate", "replace"), driver.actions)
        assertFalse(restore.active)
    }

    @Test
    fun appDisappearingWhileWaitingCannotTriggerDestructiveReplacement() {
        val driver = Driver().apply { available = false }
        val restore = WorkspaceRestore(driver)
        restore.request(snapshot)
        driver.complete()
        driver.available = true
        restore.ready()
        driver.complete("Reader was removed while waiting")
        assertEquals(listOf("validate", "validate"), driver.actions)
        assertFalse(restore.active)
        assertEquals(
            "Workspace not changed. Reader was removed while waiting",
            driver.messages.last(),
        )
    }
}
