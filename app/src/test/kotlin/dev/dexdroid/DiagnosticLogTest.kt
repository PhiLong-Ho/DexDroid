package dev.dexdroid

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class DiagnosticLogTest {
    @Test
    fun gestureMeasurementsAndNavigationDispatchRemainUseful() {
        val log = DiagnosticLog()
        val events =
            listOf(
                "Three-finger Back recognized: dx=94 dy=-3 threshold=60.0 px rightward",
                "Three-finger Back rejected: dx=8 dy=20 threshold=60 px rightward",
                "Key queued: KEYCODE_BACK -> display 4",
                "Key sent: KEYCODE_BACK -> display 4",
                "Touchpad ignored: desktop=-1 busy=false",
                "Monitor edge recognized: Right Back dx=-94 dy=3",
                "Monitor edge rejected: Home (short or outward)",
            )
        for (event in events) assertEquals(event, log.add(event))
        for (event in events) assertTrue(log.snapshot().contains(event))
        assertFalse(log.snapshot().contains("app handled"))
    }

    @Test
    fun namesErrorsAndUncontrolledStringsNeverReachStoredOrReturnedText() {
        val log = DiagnosticLog()
        val secret = "PERSONAL_SECRET https://example.test/private?q=typed\n\r\t\u0000\u202E"
        val inputs =
            listOf(
                null,
                secret,
                "Monitor connected: $secret",
                "App launch queued: dev.personal/.Private$secret",
                "Opened dev.personal • $secret",
                "Desktop operation failed: java.lang.SecurityException: $secret",
                "Desktop task state unavailable: $secret",
                "Named monitor profile saved$secret",
                "Three-finger Back recognized: dx=94 dy=3 threshold=60 px rightward$secret",
                "Key sent: KEYCODE_A -> display 4",
                "Key sent: KEYCODE_BACK -> display 4\n$secret",
                "Touchpad ignored: desktop=-1 busy=false\u0000",
            )
        for (input in inputs) {
            val safe = log.add(input)
            assertFalse(safe.contains("PERSONAL_SECRET"))
            assertFalse(safe.contains("https:"))
            assertFalse(safe.contains("dev.personal"))
            assertFalse(safe.contains("KEYCODE_A"))
            assertFalse(safe.any { it.isISOControl() || it == '\u202E' })
        }
        val snapshot = log.snapshot()
        assertFalse(snapshot.contains("PERSONAL_SECRET"))
        assertFalse(snapshot.contains("https:"))
        assertTrue(snapshot.contains("Desktop operation failed: permission denied"))
        assertTrue(
            snapshot.contains("App launch completed: IPC completed; app response unverified")
        )
        assertTrue(snapshot.contains("details omitted"))
    }

    @Test
    fun failureCategoriesRetainReasonsWithoutRawExceptionDetails() {
        val log = DiagnosticLog()
        assertEquals(
            "Connection failed: bridge unavailable",
            log.add("Connection failed: android.os.DeadObjectException: private device name"),
        )
        assertEquals(
            "Monitor unavailable: timed out",
            log.add("Monitor unavailable: java.util.concurrent.TimeoutException: private value"),
        )
        assertEquals(
            "Resize failed; retained previous settings: invalid request",
            log.add("Resize failed; retained previous settings: IllegalArgumentException: private"),
        )
        assertEquals(
            "Desktop cleanup failed; cleanup unconfirmed: details omitted",
            log.add("Desktop cleanup failed: arbitrary private shell output"),
        )
        assertEquals(
            "Split setup failed: details omitted",
            log.add("Split setup failed: private component or shell output"),
        )
        assertEquals(
            "App launch failed: bridge unavailable",
            log.add("App launch failed: android.os.RemoteException: private app name"),
        )
        assertEquals("Pane 2 task state unavailable: details omitted", log.add("Pane 2: private"))
    }

    @Test
    fun oversizedAndMalformedFieldsCannotExpandHistoryOrForgeRecords() {
        val log = DiagnosticLog()
        val oversized = "x".repeat(200000)
        for (i in 0 until 300) {
            val safe = log.add("Desktop operation failed: $oversized")
            assertTrue(safe.length <= 512)
        }
        for (event in
            listOf(
                "Key sent: KEYCODE_BACK -> display " + "9".repeat(2000),
                "Three-finger Back rejected: dx=NaN dy=Infinity threshold=60 px rightward",
                "Touchpad ignored: desktop=-1 busy=false\nDesktop stopped",
            )) {
            assertTrue(log.add(event).contains("details omitted"))
        }
        assertEquals(250, log.snapshot().lines().size)
        assertTrue(log.snapshot().length < 140000)
        assertFalse(log.snapshot().contains("\nDesktop stopped"))
        assertFalse(log.snapshot().contains("NaN"))
    }

    @Test
    fun oldestEventsAreEvictedAndClearAllowsFreshEvents() {
        val log = DiagnosticLog()
        for (i in 0 until 300) {
            log.add("Three-finger Back rejected: dx=$i dy=0 threshold=60 px rightward")
        }
        val snapshot = log.snapshot()
        assertEquals(250, snapshot.lines().size)
        assertFalse(snapshot.contains("dx=49 dy="))
        assertTrue(snapshot.contains("dx=50 dy="))
        assertTrue(snapshot.contains("dx=299 dy="))
        assertSame(snapshot, log.snapshot())
        log.clear()
        assertEquals("No events recorded.", log.snapshot())
        log.add("Desktop stopped")
        assertTrue(log.snapshot().contains("Desktop stopped"))
        assertFalse(log.snapshot().contains("dx="))
        // An already copied/shared snapshot stays immutable after Clear or subsequent events.
        assertTrue(snapshot.contains("dx=299 dy="))
    }

    @Test
    fun concurrentWritersAndReadersKeepCompleteBoundedRecords() {
        val log = DiagnosticLog()
        val workers = Executors.newFixedThreadPool(4)
        try {
            val tasks =
                (0 until 4).map {
                    workers.submit {
                        repeat(200) {
                            log.add("Key sent: KEYCODE_BACK -> display 4")
                            val snapshot = log.snapshot()
                            assertTrue(snapshot.length < 140000)
                            assertTrue(snapshot.lines().size <= 250)
                        }
                    }
                }
            for (task in tasks) task.get(10, TimeUnit.SECONDS)
            assertEquals(250, log.snapshot().lines().size)
            for (line in log.snapshot().lines()) {
                assertTrue(line.matches(Regex("[0-9:.]{12}  Key sent: KEYCODE_BACK -> display 4")))
            }
        } finally {
            workers.shutdownNow()
        }
    }
}
