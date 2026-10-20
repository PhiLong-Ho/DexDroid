package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

open class ShellCommandResultTest {
    @Test
    open fun validDumpMetricsDoNotBecomeShellFailures() {
        assertNull(
            ShellCommandResult.failure(0, "Composition Display State:\nmError: 0 ns (sqrt=0.0)\n")
        )
        assertNull(ShellCommandResult.failure(0, "description=Error: marker\n"))
        assertNull(ShellCommandResult.failure(0, ""))
    }

    @Test
    open fun actualErrorLinesFailEvenWithSuccessfulExit() {
        for (output in
            arrayOf(
                "Error: denied",
                "normal\nError: missing activity\n",
                "normal\n\t  Error: unavailable\r\n",
            )) {
            assertNotNull(ShellCommandResult.failure(0, output))
        }
    }

    @Test
    open fun failedExitAndDiagnosticLengthRemainBounded() {
        assertNotNull(ShellCommandResult.failure(1, ""))
        assertNotNull(ShellCommandResult.failure(7, "ordinary output"))
        assertTrue(
            ShellCommandResult.failure(
                    0,
                    "Error: " + String(CharArray(1000)).replace('\u0000', 'x'),
                )!!
                .length < 300
        )
    }
}
