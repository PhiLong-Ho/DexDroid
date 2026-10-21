package dev.dexdroid

/** Fixed one-way policy. The master flag is not assumed to cascade like Android Settings UI. */
internal object DebuggingPolicy {
    val keys = listOf("adb_wifi_enabled", "development_settings_enabled", "adb_enabled")
    val labels = listOf("Wireless debugging", "Developer options", "USB debugging")

    interface Access {
        fun disable(key: String)

        fun read(key: String): String

        fun daemon(): String
    }

    data class Result(val flags: List<String>, val daemon: String) {
        val summary: String
            get() =
                labels.zip(flags).joinToString("\n") { (name, value) -> "$name: $value" } +
                    "\nADB daemon: $daemon"
    }

    fun disable(access: Access): Result {
        // A failed write must not suppress attempts to turn off the other two flags.
        for (key in keys) try {
            access.disable(key)
        } catch (_: Exception) {
            /* Read back below. */
        }
        val flags =
            keys.map { key ->
                try {
                    when (access.read(key).trim()) {
                        "0" -> "Off verified"
                        "1" -> "Still on"
                        else -> "Could not verify"
                    }
                } catch (_: Exception) {
                    "Could not verify"
                }
            }
        val daemon =
            try {
                when (access.daemon().trim()) {
                    "stopped" -> "Stopped verified"
                    "running",
                    "restarting",
                    "stopping" -> "Shutdown not confirmed"
                    else -> "Could not verify"
                }
            } catch (_: Exception) {
                "Could not verify"
            }
        return Result(flags, daemon)
    }
}
