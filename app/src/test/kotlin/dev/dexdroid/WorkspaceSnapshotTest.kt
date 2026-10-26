package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class WorkspaceSnapshotTest {
    private val reader = WorkspaceSnapshot.App("fixture.reader/.Main", "Reader")
    private val browser = WorkspaceSnapshot.App("fixture.browser/.Main", "Browser")

    @Test
    fun savedWorkspaceRoundTripsEveryRotationAndPreservesHiddenAndEmptyPanes() {
        for (rotation in 0..3) for (vertical in listOf(false, true)) {
            val full =
                WorkspaceSnapshot(true, vertical, true, .65f, 1, 1, rotation, reader, browser)
            assertEquals(full, WorkspaceSnapshot.decode(full.encode()))
            val empty = full.copy(first = null, maximized = -1)
            assertEquals(empty, WorkspaceSnapshot.decode(empty.encode()))
            assertNull(empty.app(0))
            assertEquals(browser, empty.app(1))
        }
        val single = WorkspaceSnapshot(false, false, false, .5f, 0, -1, 3, reader, null)
        assertEquals(single, WorkspaceSnapshot.decode(single.encode()))
    }

    @Test
    fun restorationStartsUnmaximizedThenRestoresFinalLogicalSelection() {
        val snapshot = WorkspaceSnapshot(true, true, true, .6f, 1, 1, 2, reader, browser)
        val start = snapshot.layout(false)
        assertTrue(start.enabled)
        assertTrue(start.vertical)
        assertTrue(start.swapped)
        assertEquals(.6f, start.ratio, 0f)
        assertEquals(-1, start.maximized)
        val final = snapshot.layout()
        assertEquals(1, final.selected)
        assertEquals(1, final.maximized)
        start.ratio = .3f
        assertEquals(.6f, snapshot.ratio, 0f)
        assertTrue(snapshot.summary().contains("Up: Browser"))
    }

    @Test
    fun malformedUnknownAndTrailingSavedRecordsAreRejected() {
        val good = WorkspaceSnapshot(false, false, false, .5f, 0, -1, 0, reader, null).encode()
        for (bad in
            listOf(
                "",
                "not/base64!",
                "AAAAAg==",
                good.dropLast(8),
                good + "AAAA",
                "A".repeat(8193),
            )) {
            assertThrows(Exception::class.java) { WorkspaceSnapshot.decode(bad) }
        }
    }

    @Test
    fun impossibleOrUnsafeWorkspaceValuesAreRejected() {
        fun sample(
            ratio: Float = .5f,
            selected: Int = 0,
            max: Int = -1,
            rotation: Int = 0,
            first: WorkspaceSnapshot.App? = reader,
            second: WorkspaceSnapshot.App? = browser,
        ) = WorkspaceSnapshot(true, false, false, ratio, selected, max, rotation, first, second)
        assertThrows(IllegalArgumentException::class.java) { sample(ratio = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { sample(ratio = .9f) }
        assertThrows(IllegalArgumentException::class.java) { sample(selected = 2) }
        assertThrows(IllegalArgumentException::class.java) { sample(max = 1) }
        assertThrows(IllegalArgumentException::class.java) { sample(rotation = 4) }
        assertThrows(IllegalArgumentException::class.java) { sample(first = null, second = null) }
        assertThrows(IllegalArgumentException::class.java) { sample(second = reader) }
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceSnapshot.App("bad component", "App")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceSnapshot.App("fixture/.Main", "App\nContent")
        }
    }
}
