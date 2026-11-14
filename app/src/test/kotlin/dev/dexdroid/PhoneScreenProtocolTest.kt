package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class PhoneScreenProtocolTest {
    private val boot = "12345678-1234-abcd-5678-123456789abc"

    @Test
    fun fixedProtocolAcceptsOnlyTypedInitialRenewAndTerminalCommands() {
        assertEquals(
            PhoneScreenMessage.Start(true, 7, 12, 5000, 77, boot),
            PhoneScreenMessage.parse("OFF 7 12 5000 77 $boot"),
        )
        assertEquals(PhoneScreenMessage.Renew(12, 6000), PhoneScreenMessage.parse("RENEW 12 6000"))
        assertEquals(
            PhoneScreenMessage.Close(PhoneScreenResult.Reason.OWNER_LOST),
            PhoneScreenMessage.parse("CLOSE OWNER_LOST"),
        )
        assertEquals(
            PhoneScreenMessage.Start(false, 0, 12, 0),
            PhoneScreenMessage.parse("CHECK 0 12 0"),
        )
    }

    @Test
    fun malformedOverlongAndShellLikeMessagesAreRejected() {
        val bad =
            listOf(
                "OFF 0 1 5000",
                "OFF 7 1 5000",
                "OFF 7 1 5000 77 wrong-boot",
                "OFF 7 1 5000 -1 $boot",
                "OFF 7 0 5000",
                "OFF 7 1 -1",
                "OFF  7 1 5000",
                "OFF 7 1 5000 extra",
                "OFF 7 1 5000\nOFF 7 2 6000",
                "RENEW 1 1; reboot",
                "CLOSE READBACK",
                "CLOSE",
                "CLOSE NONE ",
                "CHECK 9 0 0",
                "CHECK 0 0 0",
                "CHECK 0 0 1",
                "OFF 7 999999999999999999999 5000",
                "x".repeat(PhoneScreenMessage.MAX_LINE + 1),
            )
        bad.forEach { assertNull(it, PhoneScreenMessage.parse(it)) }
    }

    @Test
    fun recoveryIsConstrainedToCapturedBootAndPhysicalIdentity() {
        assertEquals(
            PhoneScreenMessage.Start(false, 0, 12, 0, 77, boot),
            PhoneScreenMessage.parse("RESTORE 12 77 $boot"),
        )
        assertNull(PhoneScreenMessage.parse("RESTORE 12 77 another-device"))
        assertNull(PhoneScreenMessage.parse("RESTORE 12 -77 $boot"))
        assertNull(PhoneScreenMessage.parse("RESTORE 0 77 $boot"))
    }

    @Test
    fun offAndReplacementRestoreRejectDifferentBootOrPanel() {
        val off = PhoneScreenMessage.parse("OFF 7 12 5000 77 $boot") as PhoneScreenMessage.Start
        val restore = PhoneScreenMessage.parse("RESTORE 12 77 $boot") as PhoneScreenMessage.Start
        for (request in listOf(off, restore)) {
            assertTrue(request.acceptsIdentity(boot, 77))
            assertFalse(request.acceptsIdentity(boot, 78))
            assertFalse(request.acceptsIdentity("aaaaaaaa-1234-abcd-5678-123456789abc", 77))
        }
        assertFalse(PhoneScreenMessage.Start(true, 7, 12, 5000).acceptsIdentity(boot, 77))
    }
}
