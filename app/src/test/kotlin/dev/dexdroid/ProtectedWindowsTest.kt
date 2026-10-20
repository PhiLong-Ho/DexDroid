package dev.dexdroid

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Test

open class ProtectedWindowsTest {
    private fun window(
        n: Int,
        display: Int,
        flags: String?,
        surface: Boolean,
        visible: Boolean,
    ): String {
        return ("  Window #" +
            n +
            " Window{opaque private-title-not-retained}:\n" +
            "    mDisplayId=" +
            display +
            " taskId=12\n" +
            "    mAttrs={ ty=BASE_APPLICATION\n      fl=" +
            flags +
            "\n      pfl=2000}\n" +
            "    mHasSurface=" +
            surface +
            " isReadyForDisplay()=true\n" +
            "    isOnScreen=" +
            visible +
            "\n")
    }

    @Throws(Exception::class)
    private fun read(windows: String?): ProtectedWindows.State? {
        return ProtectedWindows.read(
            StringReader("WINDOW MANAGER WINDOWS (dumpsys window windows)\n" + windows!!),
            7,
        )
    }

    @Test
    @Throws(Exception::class)
    open fun secureVisibleDesktopWindowIsDetected() {
        assertEquals(ProtectedWindows.State.PROTECTED, read(window(0, 7, "81812100", true, true)))
    }

    @Test
    open fun nativeJoinedVisibilityHasTheSameMeaningAsStandalone() {
        for (visible in arrayOf(true, false)) {
            val standalone = window(0, 7, "2000", true, visible)
            val joined =
                standalone.replace(
                    "    isOnScreen=",
                    "    mForceSeamlesslyRotate=false seamlesslyRotate: pending=null    isOnScreen=",
                )
            assertEquals(read(standalone), read(joined))
        }
    }

    @Test
    open fun nativeJoinedVisibilityRetainsDisplayAndSurfaceRestrictions() {
        val windows =
            window(0, 0, "2000", true, true) +
                window(1, 8, "2000", true, true) +
                window(2, 7, "2000", false, true)
        assertEquals(
            ProtectedWindows.State.CLEAR,
            read(
                windows.replace(
                    "    isOnScreen=",
                    "    mForceSeamlesslyRotate=false seamlesslyRotate: pending=null    isOnScreen=",
                )
            ),
        )
    }

    @Test
    open fun ambiguousVisibilityCannotConfirmOrClearProtection() {
        val prefix = window(0, 7, "2000", true, true).replace("    isOnScreen=true\n", "")
        for (visibility in
            arrayOf(
                "isOnScreen=tru",
                "isOnScreen=unknown",
                "isOnScreen=falseish",
                "isOnScreen=false extra",
                "isOnScreen=false isOnScreen=true",
                "isOnScreen=true\nisOnScreen=false",
                "isOnScreen=false\nisOnScreen=false",
                "isOnScreen=true\nisOnScreen=unknown",
                "unrelated isOnScreen=true",
                "isVisible=true",
                "mForceSeamlesslyRotate=false seamlesslyRotate: pending=null isOnScreen=",
                "mForceSeamlesslyRotate=false seamlesslyRotate: pending=null " +
                    "isOnScreen=false isOnScreen=true",
            )) {
            assertEquals(
                visibility,
                ProtectedWindows.State.UNKNOWN,
                read(prefix + visibility + "\n"),
            )
        }
    }

    @Test
    open fun joinedVisibilityAndOemJoinedHeaderWorkTogether() {
        val header =
            " mIsLeashReadyForDispatching=true " +
                "WINDOW MANAGER WINDOWS (dumpsys window windows)\n"
        val windows =
            window(0, 7, "SECURE", true, true)
                .replace(
                    "    isOnScreen=",
                    "    mForceSeamlesslyRotate=false seamlesslyRotate: pending=null    isOnScreen=",
                )
        assertEquals(
            ProtectedWindows.State.PROTECTED,
            ProtectedWindows.read(StringReader(header + windows), 7),
        )
    }

    @Test
    @Throws(Exception::class)
    open fun phoneAndOtherDesktopProtectionAreIgnored() {
        assertEquals(
            ProtectedWindows.State.CLEAR,
            read(
                (window(0, 0, "2000", true, true) +
                    window(1, 8, "2000", true, true) +
                    window(2, 7, "81810100", true, true))
            ),
        )
    }

    @Test
    @Throws(Exception::class)
    open fun hiddenAndSurfacelessSecureWindowsAreIgnored() {
        assertEquals(
            ProtectedWindows.State.CLEAR,
            read(window(0, 7, "2000", true, false) + window(1, 7, "2000", false, true)),
        )
    }

    @Test
    @Throws(Exception::class)
    open fun privateFlagsNeverCountAsWindowProtection() {
        assertEquals(ProtectedWindows.State.CLEAR, read(window(0, 7, "0", true, true)))
    }

    @Test
    @Throws(Exception::class)
    open fun symbolicAndPrefixedHexFlagsAreSupported() {
        for (flags in
            arrayOf<String?>(
                "NOT_FOCUSABLE SECURE HARDWARE_ACCELERATED",
                "FLAG_SECURE|FLAG_FULLSCREEN",
                "0x2000",
            )) assertEquals(ProtectedWindows.State.PROTECTED, read(window(0, 7, flags, true, true)))
    }

    @Test
    @Throws(Exception::class)
    open fun malformedVisibleFlagsAndMissingVisibilityAreUnknown() {
        assertEquals(
            ProtectedWindows.State.UNKNOWN,
            read(window(0, 7, "<unknown-format>", true, true)),
        )
        assertEquals(
            ProtectedWindows.State.UNKNOWN,
            read(window(0, 7, "2000", true, true).replace("    isOnScreen=true\n", "")),
        )
    }

    @Test
    @Throws(Exception::class)
    open fun permissionTimeoutAndMissingHeaderAreUnknown() {
        assertEquals(ProtectedWindows.State.UNKNOWN, read("Permission Denial"))
        assertEquals(ProtectedWindows.State.UNKNOWN, read("DUMP TIMEOUT"))
        assertEquals(ProtectedWindows.State.UNKNOWN, ProtectedWindows.read(StringReader(""), 7))
    }

    @Test
    @Throws(Exception::class)
    open fun confirmedProtectionWinsOverAnotherUnrecognizedWindow() {
        assertEquals(
            ProtectedWindows.State.PROTECTED,
            read(window(0, 7, "bad-format", true, true) + window(1, 7, "2000", true, true)),
        )
    }

    @Test
    @Throws(Exception::class)
    open fun emptyVisibleListIsClearAndDefaultTargetIsRejected() {
        assertEquals(ProtectedWindows.State.CLEAR, read(""))
        assertEquals(
            ProtectedWindows.State.UNKNOWN,
            ProtectedWindows.read(StringReader(window(0, 0, "2000", true, true)), 0),
        )
    }

    @Test
    @Throws(Exception::class)
    open fun oemDisplayDiagnosticWithoutNewlineDoesNotFreezeProtection() {
        val header =
            ("WINDOW MANAGER DISPLAY CONTENTS (dumpsys window displays)\n" +
                " mIsLeashReadyForDispatching=true mImeOverrideFrame=Rect(0, 0 - 0, 0)" +
                "WINDOW MANAGER WINDOWS (dumpsys window windows)\n")
        for (flags in arrayOf<String?>("LAYOUT_IN_SCREEN SECURE", "LAYOUT_IN_SCREEN")) {
            assertEquals(
                if (flags!!.contains("SECURE")) ProtectedWindows.State.PROTECTED
                else ProtectedWindows.State.CLEAR,
                ProtectedWindows.read(StringReader(header!! + window(0, 7, flags, true, true)), 7),
            )
        }
        assertEquals(
            ProtectedWindows.State.UNKNOWN,
            ProtectedWindows.read(
                StringReader(
                    (header!!.replace(
                        "WINDOW MANAGER WINDOWS (dumpsys window windows)",
                        "WINDOW MANAGER WINDOWS",
                    ) + window(0, 7, "LAYOUT_IN_SCREEN", true, true))
                ),
                7,
            ),
        )
    }
}
