package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class DebuggingPolicyTest {
    private class Access : DebuggingPolicy.Access {
        val writes = mutableListOf<String>()
        val values = DebuggingPolicy.keys.associateWith { "1" }.toMutableMap()
        var denied: String? = null
        var unreadable: String? = null
        var daemonState = "running"

        override fun disable(key: String) {
            writes += key
            if (key == denied) throw SecurityException("Denied")
            values[key] = "0"
        }

        override fun read(key: String): String {
            if (key == unreadable) throw SecurityException("Unreadable")
            return values.getValue(key)
        }

        override fun daemon() = daemonState
    }

    @Test
    fun disablesOnlyThreeFixedKeysWithoutRelyingOnMasterCascade() {
        val access = Access()
        access.daemonState = "stopped"
        val result = DebuggingPolicy.disable(access)
        assertEquals(
            listOf("adb_wifi_enabled", "development_settings_enabled", "adb_enabled"),
            access.writes,
        )
        assertEquals(listOf("Off verified", "Off verified", "Off verified"), result.flags)
        assertEquals("Stopped verified", result.daemon)
    }

    @Test
    fun deniedMasterStillDisablesUsbAndReportsPartial() {
        val access = Access()
        access.denied = "development_settings_enabled"
        val result = DebuggingPolicy.disable(access)
        assertEquals(listOf("Off verified", "Still on", "Off verified"), result.flags)
        assertEquals(3, access.writes.size)
        assertEquals("Shutdown not confirmed", result.daemon)
    }

    @Test
    fun missingOrUnreadableValuesCannotBecomeSuccess() {
        val access = Access()
        access.denied = "adb_enabled"
        access.values["adb_enabled"] = "null"
        access.unreadable = "adb_wifi_enabled"
        val result = DebuggingPolicy.disable(access)
        assertEquals(listOf("Could not verify", "Off verified", "Could not verify"), result.flags)
    }

    @Test
    fun alreadyOffDoesNotEnableOrAlterPairing() {
        val access = Access()
        access.values.keys.toList().forEach { access.values[it] = "0" }
        access.values["adb_allowed_connection_time"] = "777"
        val result = DebuggingPolicy.disable(access)
        assertTrue(result.flags.all { it == "Off verified" })
        assertEquals("777", access.values["adb_allowed_connection_time"])
        assertEquals(3, access.writes.size)
    }

    @Test
    fun failureEverywhereIsUnknownAndStillAttemptsOnlyAllowedWrites() {
        val writes = mutableListOf<String>()
        val result =
            DebuggingPolicy.disable(
                object : DebuggingPolicy.Access {
                    override fun disable(key: String) {
                        writes += key
                        throw IllegalStateException()
                    }

                    override fun read(key: String): String = throw IllegalStateException()

                    override fun daemon(): String = throw IllegalStateException()
                }
            )
        assertEquals(DebuggingPolicy.keys, writes)
        assertTrue(result.flags.all { it == "Could not verify" })
        assertEquals("Could not verify", result.daemon)
    }
}
