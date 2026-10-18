package dev.dexdroid;

import static org.junit.Assert.*;

import org.junit.Test;

public class ShellCommandResultTest {
    @Test
    public void validDumpMetricsDoNotBecomeShellFailures() {
        assertNull(
                ShellCommandResult.failure(
                        0, "Composition Display State:\nmError: 0 ns (sqrt=0.0)\n"));
        assertNull(ShellCommandResult.failure(0, "description=Error: marker\n"));
        assertNull(ShellCommandResult.failure(0, ""));
    }

    @Test
    public void actualErrorLinesFailEvenWithSuccessfulExit() {
        for (String output :
                new String[] {
                    "Error: denied",
                    "normal\nError: missing activity\n",
                    "normal\n\t  Error: unavailable\r\n"
                }) {
            assertNotNull(ShellCommandResult.failure(0, output));
        }
    }

    @Test
    public void failedExitAndDiagnosticLengthRemainBounded() {
        assertNotNull(ShellCommandResult.failure(1, ""));
        assertNotNull(ShellCommandResult.failure(7, "ordinary output"));
        assertTrue(
                ShellCommandResult.failure(
                                        0,
                                        "Error: " + new String(new char[1000]).replace('\0', 'x'))
                                .length()
                        < 300);
    }
}
