package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class DeviceCapabilitiesTest {
    @Test
    fun freshReadinessLossAndRecoveryRetiresEvidenceWithoutChangingCachedOwner() {
        val ledger = CapabilityEvidenceLedger()
        val cachedOwner = Any()
        val original = ledger.updateOwner(cachedOwner)
        ledger.updateReadiness(DeviceCapabilities.Shizuku.CONNECTED)
        assertTrue(
            ledger.record(
                original,
                CapabilityEvidenceLedger.Observation.Input(
                    DeviceCapabilities.InputObservation.TOUCH_ACCEPTED
                ),
            )
        )
        ledger.updateReadiness(DeviceCapabilities.Shizuku.PERMISSION)
        ledger.updateReadiness(DeviceCapabilities.Shizuku.CONNECTED)
        val current = ledger.updateOwner(cachedOwner)
        assertNotSame(original, current)
        assertEquals(DeviceCapabilities.InputObservation.NONE, ledger.facts().input)
        assertFalse(
            ledger.record(original, CapabilityEvidenceLedger.Observation.RotationConfigured)
        )
        assertFalse(
            ledger.record(
                original,
                CapabilityEvidenceLedger.Observation.Input(
                    DeviceCapabilities.InputObservation.FAILED
                ),
            )
        )
        ledger.updateReadiness(DeviceCapabilities.Shizuku.UNKNOWN)
        assertFalse(ledger.record(current, CapabilityEvidenceLedger.Observation.SplitConfigured))
    }

    private val ready =
        DeviceCapabilities.Snapshot(
            shizuku = DeviceCapabilities.Shizuku.CONNECTED,
            monitor = DeviceCapabilities.Monitor(present = true),
            desktopActive = true,
            virtualWidth = 1920,
            virtualHeight = 1080,
            virtualDpi = 160,
            rotation = 0,
        )

    @Test
    fun reportAlwaysHasSixOrderedItemsAndMissingDependenciesExplainRecovery() {
        val report =
            DeviceCapabilities.report(
                DeviceCapabilities.Snapshot(shizuku = DeviceCapabilities.Shizuku.MISSING)
            )
        assertEquals(
            listOf("Shizuku", "Monitor", "Desktop", "Input", "Rotation + split", "Audio selection"),
            report.items.map { it.title },
        )
        assertEquals(DeviceCapabilities.Verdict.UNAVAILABLE, report.items[0].verdict)
        assertTrue(report.items[0].detail.contains("Install and start"))
        assertEquals(DeviceCapabilities.Verdict.UNAVAILABLE, report.items[1].verdict)
        assertTrue(report.items[1].detail.contains("Connect a monitor"))
        assertEquals(DeviceCapabilities.Verdict.NOT_CHECKED, report.items[5].verdict)
    }

    @Test
    fun connectedPrerequisitesAndSavedGeometryDoNotProveInputOrLayout() {
        val report = DeviceCapabilities.report(ready)
        assertEquals(DeviceCapabilities.Verdict.NOT_CHECKED, report.items[3].verdict)
        assertTrue(report.items[3].detail.contains("remain unverified"))
        assertEquals(DeviceCapabilities.Verdict.NOT_CHECKED, report.items[4].verdict)
        assertTrue(report.items[4].detail.contains("Rotation: not checked. Split: not checked."))
        assertTrue(report.items[2].detail.contains("does not confirm visible pixels"))
    }

    @Test
    fun optionalAudioDenialLeavesDesktopAndInputIndependent() {
        val report =
            DeviceCapabilities.report(
                ready.copy(
                    input = DeviceCapabilities.InputObservation.TOUCH_ACCEPTED,
                    audio =
                        DeviceCapabilities.Audio(
                            unavailableReason = DeviceCapabilities.AudioUnavailableReason.DENIED
                        ),
                )
            )
        assertEquals(DeviceCapabilities.Verdict.CONFIRMED, report.items[2].verdict)
        assertEquals(DeviceCapabilities.Verdict.CONFIRMED, report.items[3].verdict)
        assertEquals(DeviceCapabilities.Verdict.UNAVAILABLE, report.items[5].verdict)
        assertTrue(report.items[5].detail.contains("optional"))
        assertTrue(report.items[3].detail.contains("App response has not been checked"))
    }

    @Test
    fun rotationAndSplitRemainSeparateObservations() {
        val rotation = DeviceCapabilities.report(ready.copy(rotationConfigured = true)).items[4]
        assertEquals(DeviceCapabilities.Verdict.CONFIRMED, rotation.verdict)
        assertTrue(
            rotation.detail.contains(
                "Rotation: configuration completed this session. Split: not checked."
            )
        )
        val split = DeviceCapabilities.report(ready.copy(splitConfigured = true)).items[4]
        assertTrue(
            split.detail.contains(
                "Rotation: not checked. Split: configuration completed this session."
            )
        )
        assertTrue(split.detail.contains("does not confirm rendered layout"))
    }

    @Test
    fun inactiveDesktopDoesNotReuseInputOrConfigurationClaims() {
        val report =
            DeviceCapabilities.report(
                ready.copy(
                    desktopActive = false,
                    input = DeviceCapabilities.InputObservation.TOUCH_ACCEPTED,
                    rotationConfigured = true,
                    splitConfigured = true,
                )
            )
        assertEquals(DeviceCapabilities.Verdict.AVAILABLE, report.items[2].verdict)
        assertEquals(DeviceCapabilities.Verdict.NOT_CHECKED, report.items[3].verdict)
        assertEquals(DeviceCapabilities.Verdict.NOT_CHECKED, report.items[4].verdict)
    }

    @Test
    fun dependencyLossCannotRetainInputConfirmation() {
        val report =
            DeviceCapabilities.report(
                ready.copy(
                    shizuku = DeviceCapabilities.Shizuku.STOPPED,
                    input = DeviceCapabilities.InputObservation.SCROLL_ACCEPTED,
                    splitConfigured = true,
                )
            )
        assertEquals(DeviceCapabilities.Verdict.UNAVAILABLE, report.items[2].verdict)
        assertEquals(DeviceCapabilities.Verdict.UNAVAILABLE, report.items[3].verdict)
        assertEquals(DeviceCapabilities.Verdict.UNAVAILABLE, report.items[4].verdict)
        assertTrue(report.items[3].detail.contains("Start Shizuku"))
    }

    @Test
    fun unknownPrerequisitesAndDispatchFailureRemainInconclusive() {
        val unknown =
            DeviceCapabilities.report(
                ready.copy(
                    shizuku = DeviceCapabilities.Shizuku.UNKNOWN,
                    input = DeviceCapabilities.InputObservation.TOUCH_ACCEPTED,
                    rotationConfigured = true,
                )
            )
        listOf(0, 2, 3, 4).forEach { index ->
            assertEquals(DeviceCapabilities.Verdict.NOT_CHECKED, unknown.items[index].verdict)
        }
        val failed =
            DeviceCapabilities.report(
                    ready.copy(input = DeviceCapabilities.InputObservation.FAILED)
                )
                .items[3]
        assertEquals(DeviceCapabilities.Verdict.NOT_CHECKED, failed.verdict)
        assertTrue(failed.detail.contains("cause has not been established"))
    }

    @Test
    fun verifiedScrollAndRejectionDescribeOnlyExactObservedOperation() {
        val scroll =
            DeviceCapabilities.report(
                    ready.copy(input = DeviceCapabilities.InputObservation.SCROLL_ACCEPTED)
                )
                .items[3]
        assertTrue(scroll.detail.contains("accepted a scroll injection"))
        assertFalse(scroll.detail.contains("accepted a touch"))
        val rejected =
            DeviceCapabilities.report(
                    ready.copy(input = DeviceCapabilities.InputObservation.REJECTED)
                )
                .items[3]
        assertEquals(DeviceCapabilities.Verdict.UNAVAILABLE, rejected.verdict)
        assertTrue(rejected.detail.contains("rejected an input injection"))
    }

    @Test
    fun invalidMetadataIsUnknownAndValidMetadataStaysPhysicallyDistinct() {
        val bad =
            ready.copy(
                monitor =
                    DeviceCapabilities.Monitor(
                        true,
                        -1,
                        Int.MAX_VALUE,
                        Float.NaN,
                        Float.POSITIVE_INFINITY,
                        listOf(DeviceCapabilities.Mode(0, 1080, 60f)),
                    ),
                virtualDpi = -1,
                rotation = 400,
            )
        val report = DeviceCapabilities.report(bad)
        assertTrue(report.items[1].detail.contains("physical signal: unknown"))
        assertTrue(report.items[1].detail.contains("physical DPI: unknown"))
        assertTrue(report.items[1].detail.contains("modes: unknown"))
        assertTrue(report.items[2].detail.contains("logical UI DPI: unknown; rotation: unknown"))
        assertFalse(report.plainText().contains("NaN"))
        assertFalse(report.plainText().contains("Infinity"))
        val valid =
            DeviceCapabilities.report(
                    ready.copy(
                        monitor = DeviceCapabilities.Monitor(true, 3840, 2160, 60.00004f, 144f)
                    )
                )
                .plainText()
        assertTrue(valid.contains("physical signal: 3840 × 2160 at 60 Hz"))
        assertTrue(
            DeviceCapabilities.report(
                    ready.copy(monitor = DeviceCapabilities.Monitor(true, 1920, 1200, 59.94006f))
                )
                .plainText()
                .contains("59.94 Hz")
        )
        assertTrue(valid.contains("Virtual resolution: 1920 × 1080; logical UI DPI: 160"))
    }

    @Test
    fun exportedSnapshotIsBoundedAndContainsOnlyFixedTextAndNumericFacts() {
        val report =
            DeviceCapabilities.report(
                ready.copy(
                    monitor =
                        DeviceCapabilities.Monitor(
                            true,
                            modes = (1..1000).map { DeviceCapabilities.Mode(it, 100, 60f) },
                        ),
                    audio = DeviceCapabilities.Audio(available = true, outputCount = Int.MAX_VALUE),
                )
            )
        val text = report.plainText()
        assertTrue(text.length < 4000)
        assertFalse(text.contains("17 × 100"))
        assertTrue(text.contains("limited to the first 16 reported modes"))
        assertFalse(text.contains(Int.MAX_VALUE.toString()))
        assertEquals(text, report.plainText())
        assertFalse(text.contains("@"))
    }

    @Test
    fun exportedReportDoesNotChangeWhenMetadataSourceChanges() {
        val modes = mutableListOf(DeviceCapabilities.Mode(1920, 1080, 60f))
        val snapshot = ready.copy(monitor = DeviceCapabilities.Monitor(true, modes = modes))
        val report = DeviceCapabilities.report(snapshot)
        val exported = report.plainText()
        modes.clear()
        modes.add(DeviceCapabilities.Mode(640, 480, 30f))
        assertEquals(exported, report.plainText())
        assertTrue(exported.contains("1920 × 1080 at 60 Hz"))
        assertFalse(exported.contains("640 × 480"))
    }

    @Test
    fun pendingOrUncertainAudioCannotClaimPolicyConfirmationOrAudibility() {
        listOf(
                DeviceCapabilities.Audio(confirmed = true, pending = true),
                DeviceCapabilities.Audio(confirmed = true, uncertain = true),
            )
            .forEach { audio ->
                assertEquals(
                    DeviceCapabilities.Verdict.NOT_CHECKED,
                    DeviceCapabilities.report(ready.copy(audio = audio)).items[5].verdict,
                )
            }
        assertEquals(
            DeviceCapabilities.Verdict.NOT_CHECKED,
            DeviceCapabilities.report(
                    ready.copy(audio = DeviceCapabilities.Audio(confirmed = true))
                )
                .items[5]
                .verdict,
        )
        val confirmed =
            DeviceCapabilities.report(
                    ready.copy(audio = DeviceCapabilities.Audio(available = true, confirmed = true))
                )
                .items[5]
        assertEquals(DeviceCapabilities.Verdict.CONFIRMED, confirmed.verdict)
        assertTrue(confirmed.detail.contains("does not confirm physical routing or audible sound"))
    }

    @Test
    fun staleSuccessAndFailureAreRejectedEvenWhenOwnerReturnsToSameValue() {
        val ledger = CapabilityEvidenceLedger()
        val firstA = ledger.updateOwner("A")
        assertTrue(
            ledger.record(
                firstA,
                CapabilityEvidenceLedger.Observation.Input(
                    DeviceCapabilities.InputObservation.TOUCH_ACCEPTED
                ),
            )
        )
        val b = ledger.updateOwner("B")
        assertEquals(CapabilityEvidenceLedger.Facts(), ledger.facts())
        val secondA = ledger.updateOwner("A")
        assertNotSame(firstA, secondA)
        assertFalse(
            ledger.record(
                firstA,
                CapabilityEvidenceLedger.Observation.Input(
                    DeviceCapabilities.InputObservation.TOUCH_ACCEPTED
                ),
            )
        )
        assertFalse(
            ledger.record(
                b,
                CapabilityEvidenceLedger.Observation.Input(
                    DeviceCapabilities.InputObservation.REJECTED
                ),
            )
        )
        assertEquals(CapabilityEvidenceLedger.Facts(), ledger.facts())
        assertTrue(ledger.record(secondA, CapabilityEvidenceLedger.Observation.SplitConfigured))
        assertFalse(ledger.facts().rotationConfigured)
        assertTrue(ledger.facts().splitConfigured)
    }

    @Test
    fun explicitResetAdvancesTicketAndClearsAllThreeFactsWithoutChangingOwner() {
        val ledger = CapabilityEvidenceLedger()
        val before = ledger.updateOwner("same")
        ledger.record(
            before,
            CapabilityEvidenceLedger.Observation.Input(
                DeviceCapabilities.InputObservation.SCROLL_ACCEPTED
            ),
        )
        ledger.record(before, CapabilityEvidenceLedger.Observation.RotationConfigured)
        ledger.record(before, CapabilityEvidenceLedger.Observation.SplitConfigured)
        assertEquals(DeviceCapabilities.InputObservation.SCROLL_ACCEPTED, ledger.facts().input)
        val after = ledger.invalidate()
        assertSame(after, ledger.updateOwner("same"))
        assertNotSame(before, after)
        assertFalse(
            ledger.record(
                before,
                CapabilityEvidenceLedger.Observation.Input(
                    DeviceCapabilities.InputObservation.REJECTED
                ),
            )
        )
        assertEquals(CapabilityEvidenceLedger.Facts(), ledger.facts())
        assertTrue(ledger.record(after, CapabilityEvidenceLedger.Observation.RotationConfigured))
    }
}
