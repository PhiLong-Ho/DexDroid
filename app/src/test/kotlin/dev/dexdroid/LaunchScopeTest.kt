package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class LaunchScopeTest {
    @Test
    fun currentSingleViewAcceptsItsResult() {
        val owner = Any()
        assertTrue(LaunchScope(4, 8, owner).current(4, 8, owner, false))
    }

    @Test
    fun stopAndRestartCannotReuseAnOldResultEvenWithTheSameDisplay() {
        val owner = Any()
        val scope = LaunchScope(4, 8, owner)
        assertFalse(scope.current(5, -1, owner, false))
        assertFalse(scope.current(6, 8, owner, false))
        assertFalse(scope.current(4, 9, owner, false))
    }

    @Test
    fun replacementBridgeSplitAndMissingOwnerRejectOldResults() {
        val owner = Any()
        val scope = LaunchScope(4, 8, owner)
        assertFalse(scope.current(4, 8, Any(), false))
        assertFalse(scope.current(4, 8, owner, true))
        assertFalse(scope.current(4, 8, null, false))
        assertFalse(LaunchScope(4, 8, null).current(4, 8, null, false))
        assertFalse(LaunchScope(4, -1, owner).current(4, -1, owner, false))
    }
}
