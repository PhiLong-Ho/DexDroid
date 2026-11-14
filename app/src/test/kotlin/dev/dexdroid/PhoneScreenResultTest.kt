package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class PhoneScreenResultTest {
    private val boot = "12345678-1234-abcd-5678-123456789abc"

    private fun fields(): Map<String, Any> =
        mapOf(
            "state" to "ON",
            "reason" to "NONE",
            "lease" to 12L,
            "safeAutoRestore" to false,
            "boot" to boot,
            "physical" to 77L,
        )

    @Test
    fun completeTypedSchemaRetainsExactLeaseAndRecoveryReceipt() {
        val result = PhoneScreenResult.fromFields(fields())
        assertEquals(
            PhoneScreenResult(PhoneScreenResult.State.ON, lease = 12, boot = boot, physical = 77),
            result,
        )
        assertTrue(result.hasReceipt)
        assertEquals(
            PhoneScreenResult.State.OFF,
            PhoneScreenResult.fromFields(fields() + ("state" to "OFF")).state,
        )
    }

    @Test
    fun everyMissingOrWrongTypedFieldFailsClosedWithoutGetterDefaults() {
        fields().keys.forEach { key ->
            assertEquals(
                key,
                PhoneScreenResult.State.UNKNOWN,
                PhoneScreenResult.fromFields(fields() - key).state,
            )
        }
        val wrong =
            mapOf<String, Any>(
                "state" to 1,
                "reason" to false,
                "lease" to 12,
                "safeAutoRestore" to "false",
                "boot" to 77L,
                "physical" to 77,
            )
        wrong.forEach { (key, value) ->
            assertEquals(
                key,
                PhoneScreenResult.State.UNKNOWN,
                PhoneScreenResult.fromFields(fields() + (key to value)).state,
            )
        }
        assertEquals(
            PhoneScreenResult.State.UNKNOWN,
            PhoneScreenResult.fromFields(fields() + ("extra" to true)).state,
        )
    }

    @Test
    fun invalidEpochReceiptEnumAndSafetyClaimsCannotBecomeConfirmedOn() {
        val bad =
            listOf(
                fields() + ("lease" to 0L),
                fields() + ("lease" to -1L),
                fields() + ("boot" to ""),
                fields() + ("physical" to -1L),
                fields() + mapOf("boot" to "", "physical" to -1L),
                fields() + ("state" to "on"),
                fields() + ("reason" to "other"),
                fields() + ("safeAutoRestore" to true),
            )
        bad.forEach {
            assertEquals(PhoneScreenResult.State.UNKNOWN, PhoneScreenResult.fromFields(it).state)
        }
        val safety =
            PhoneScreenResult.fromFields(
                fields() + mapOf("reason" to "EXPIRED", "safeAutoRestore" to true)
            )
        assertEquals(PhoneScreenResult.State.ON, safety.state)
        assertTrue(safety.safeAutoRestore)
    }

    @Test
    fun recoveryReceiptIsRuntimeOnlyAndAbsentReceiptCanDescribePending() {
        val pending =
            PhoneScreenResult.fromFields(
                fields() + mapOf("state" to "PENDING", "boot" to "", "physical" to -1L)
            )
        assertEquals(PhoneScreenResult.State.PENDING, pending.state)
        assertFalse(pending.hasReceipt)
        val log = PhoneScreenResult.fromFields(fields()).toString()
        assertFalse(log.contains(boot))
        assertFalse(log.contains("77"))
    }
}
