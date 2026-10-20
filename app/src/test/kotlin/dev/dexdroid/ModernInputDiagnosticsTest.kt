package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class ModernInputDiagnosticsTest {
    private val emulator =
        "google/sdk_gphone64_x86_64/emu64xa:15/AE3A.240806.043/12960925:userdebug/dev-keys"
    private val xiaomi =
        "Xiaomi/houji_global/houji:15/AQ3A.240627.003/OS2.0.206.0.VNCMIXM:user/release-keys"
    private val child = "dev.dexdroid.test/dev.dexdroid.fixture.NavigationFixtureActivity\$Child"
    private val parent = "dev.dexdroid.test/dev.dexdroid.fixture.NavigationFixtureActivity"
    private val fixture = ModernInputWindow.Target(12, 10362, child)

    private fun selection() =
        ModernInputDiagnostics.Selection(ModernInputDiagnostics.Profile.XIAOMI_SECONDARY_CHILD)

    @Test
    fun onlyExactBuildAndRolePairsArm() {
        assertEquals(
            ModernInputDiagnostics.Profile.EMULATOR_PRIMARY,
            ModernInputDiagnostics.profile(emulator, "dev.dexdroid.DeskService", 35, 2000, 4),
        )
        assertEquals(
            ModernInputDiagnostics.Profile.XIAOMI_SECONDARY_CHILD,
            ModernInputDiagnostics.profile(xiaomi, "dev.dexdroid.SecondaryDeskService", 35, 2000, 5),
        )
        assertNull(ModernInputDiagnostics.profile(xiaomi, "dev.dexdroid.DeskService", 35, 2000, 5))
        assertNull(
            ModernInputDiagnostics.profile(
                emulator,
                "dev.dexdroid.SecondaryDeskService",
                35,
                2000,
                5,
            )
        )
    }

    @Test
    fun wrongBuildPrivilegeVersionAndDisplayDoNotArm() {
        for (fingerprint in listOf("", xiaomi + "-changed", emulator + "-changed")) assertNull(
            ModernInputDiagnostics.profile(
                fingerprint,
                "dev.dexdroid.SecondaryDeskService",
                35,
                2000,
                5,
            )
        )
        for (api in listOf(30, 34, 36)) assertNull(
            ModernInputDiagnostics.profile(
                xiaomi,
                "dev.dexdroid.SecondaryDeskService",
                api,
                2000,
                5,
            )
        )
        for (uid in listOf(0, 10361, 10362)) assertNull(
            ModernInputDiagnostics.profile(xiaomi, "dev.dexdroid.SecondaryDeskService", 35, uid, 5)
        )
        for (display in listOf(-1, 0)) assertNull(
            ModernInputDiagnostics.profile(
                xiaomi,
                "dev.dexdroid.SecondaryDeskService",
                35,
                2000,
                display,
            )
        )
    }

    @Test
    fun intentionalParentRejectionDoesNotConsumeChildCapture() {
        val policy = selection()
        assertFalse(policy.take(ModernInputWindow.Target(12, 10362, parent), 1, 10))
        assertTrue(policy.take(fixture, 2, 10))
        assertFalse(policy.take(fixture, 3, 10))
    }

    @Test
    fun otherUidAndComponentNeverAdmitCapture() {
        val policy = selection()
        assertFalse(policy.take(ModernInputWindow.Target(12, 10361, child), 1, 10))
        assertFalse(policy.take(ModernInputWindow.Target(12, 10362, child + "Other"), 1, 10))
        assertTrue(policy.take(fixture, 2, 10))
    }

    @Test
    fun nullTargetIsOneInconclusiveSample() {
        val policy = selection()
        assertTrue(policy.take(null, 1, 10))
        assertFalse(policy.take(fixture, 2, 10))
    }

    @Test
    fun expiredMatchingSampleCannotBeRearmed() {
        val policy = selection()
        assertFalse(policy.take(fixture, 10, 10))
        assertFalse(policy.take(fixture, 11, 20))
    }

    @Test
    fun emulatorRetainsConsumptionBeforeComponentFiltering() {
        val policy =
            ModernInputDiagnostics.Selection(ModernInputDiagnostics.Profile.EMULATOR_PRIMARY)
        assertFalse(policy.take(fixture, 1, 10))
        assertFalse(
            policy.take(
                ModernInputWindow.Target(1, 10361, "dev.dexdroid/dev.dexdroid.InputTestActivity"),
                2,
                10,
            )
        )
    }
}
