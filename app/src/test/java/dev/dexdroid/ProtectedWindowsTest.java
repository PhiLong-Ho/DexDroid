package dev.dexdroid;

import static org.junit.Assert.assertEquals;

import java.io.StringReader;
import org.junit.Test;

public class ProtectedWindowsTest {
    private String window(int n, int display, String flags, boolean surface, boolean visible) {
        return "  Window #"
                + n
                + " Window{opaque private-title-not-retained}:\n"
                + "    mDisplayId="
                + display
                + " taskId=12\n"
                + "    mAttrs={ ty=BASE_APPLICATION\n      fl="
                + flags
                + "\n      pfl=2000}\n"
                + "    mHasSurface="
                + surface
                + " isReadyForDisplay()=true\n"
                + "    isOnScreen="
                + visible
                + "\n";
    }

    private ProtectedWindows.State read(String windows) throws Exception {
        return ProtectedWindows.read(
                new StringReader("WINDOW MANAGER WINDOWS (dumpsys window windows)\n" + windows), 7);
    }

    @Test
    public void secureVisibleDesktopWindowIsDetected() throws Exception {
        assertEquals(ProtectedWindows.State.PROTECTED, read(window(0, 7, "81812100", true, true)));
    }

    @Test
    public void phoneAndOtherDesktopProtectionAreIgnored() throws Exception {
        assertEquals(
                ProtectedWindows.State.CLEAR,
                read(
                        window(0, 0, "2000", true, true)
                                + window(1, 8, "2000", true, true)
                                + window(2, 7, "81810100", true, true)));
    }

    @Test
    public void hiddenAndSurfacelessSecureWindowsAreIgnored() throws Exception {
        assertEquals(
                ProtectedWindows.State.CLEAR,
                read(window(0, 7, "2000", true, false) + window(1, 7, "2000", false, true)));
    }

    @Test
    public void privateFlagsNeverCountAsWindowProtection() throws Exception {
        assertEquals(ProtectedWindows.State.CLEAR, read(window(0, 7, "0", true, true)));
    }

    @Test
    public void symbolicAndPrefixedHexFlagsAreSupported() throws Exception {
        for (String flags :
                new String[] {
                    "NOT_FOCUSABLE SECURE HARDWARE_ACCELERATED",
                    "FLAG_SECURE|FLAG_FULLSCREEN",
                    "0x2000"
                })
            assertEquals(ProtectedWindows.State.PROTECTED, read(window(0, 7, flags, true, true)));
    }

    @Test
    public void malformedVisibleFlagsAndMissingVisibilityAreUnknown() throws Exception {
        assertEquals(
                ProtectedWindows.State.UNKNOWN, read(window(0, 7, "<unknown-format>", true, true)));
        assertEquals(
                ProtectedWindows.State.UNKNOWN,
                read(window(0, 7, "2000", true, true).replace("    isOnScreen=true\n", "")));
    }

    @Test
    public void permissionTimeoutAndMissingHeaderAreUnknown() throws Exception {
        assertEquals(ProtectedWindows.State.UNKNOWN, read("Permission Denial"));
        assertEquals(ProtectedWindows.State.UNKNOWN, read("DUMP TIMEOUT"));
        assertEquals(
                ProtectedWindows.State.UNKNOWN, ProtectedWindows.read(new StringReader(""), 7));
    }

    @Test
    public void confirmedProtectionWinsOverAnotherUnrecognizedWindow() throws Exception {
        assertEquals(
                ProtectedWindows.State.PROTECTED,
                read(window(0, 7, "bad-format", true, true) + window(1, 7, "2000", true, true)));
    }

    @Test
    public void emptyVisibleListIsClearAndDefaultTargetIsRejected() throws Exception {
        assertEquals(ProtectedWindows.State.CLEAR, read(""));
        assertEquals(
                ProtectedWindows.State.UNKNOWN,
                ProtectedWindows.read(new StringReader(window(0, 0, "2000", true, true)), 0));
    }

    @Test
    public void oemDisplayDiagnosticWithoutNewlineDoesNotFreezeProtection() throws Exception {
        String header =
                "WINDOW MANAGER DISPLAY CONTENTS (dumpsys window displays)\n"
                        + " mIsLeashReadyForDispatching=true mImeOverrideFrame=Rect(0, 0 - 0, 0)"
                        + "WINDOW MANAGER WINDOWS (dumpsys window windows)\n";
        for (String flags : new String[] {"LAYOUT_IN_SCREEN SECURE", "LAYOUT_IN_SCREEN"}) {
            assertEquals(
                    flags.contains("SECURE")
                            ? ProtectedWindows.State.PROTECTED
                            : ProtectedWindows.State.CLEAR,
                    ProtectedWindows.read(
                            new StringReader(header + window(0, 7, flags, true, true)), 7));
        }
        assertEquals(
                ProtectedWindows.State.UNKNOWN,
                ProtectedWindows.read(
                        new StringReader(
                                header.replace(
                                                "WINDOW MANAGER WINDOWS (dumpsys window windows)",
                                                "WINDOW MANAGER WINDOWS")
                                        + window(0, 7, "LAYOUT_IN_SCREEN", true, true)),
                        7));
    }
}
