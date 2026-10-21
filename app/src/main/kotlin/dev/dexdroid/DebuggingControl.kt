package dev.dexdroid

import android.os.Bundle
import java.util.concurrent.TimeUnit

/** Shell-owned fixed argv only: never exported as an arbitrary command interface. */
internal object DebuggingControl {
    fun disable(): Bundle {
        val result =
            DebuggingPolicy.disable(
                object : DebuggingPolicy.Access {
                    override fun disable(key: String) {
                        check(key in DebuggingPolicy.keys)
                        command("settings", "put", "global", key, "0")
                    }

                    override fun read(key: String): String {
                        check(key in DebuggingPolicy.keys)
                        return command("settings", "get", "global", key)
                    }

                    override fun daemon(): String {
                        // Flag observers stop adbd asynchronously. Bound this observation
                        // independently.
                        repeat(15) {
                            val state = command("getprop", "init.svc.adbd").trim()
                            if (state == "stopped") return state
                            Thread.sleep(100)
                        }
                        return command("getprop", "init.svc.adbd")
                    }
                }
            )
        return Bundle().apply {
            putString("summary", result.summary)
            putBoolean(
                "verified",
                result.flags.all { it == "Off verified" } && result.daemon == "Stopped verified",
            )
        }
    }

    private fun command(vararg args: String): String {
        val process = ProcessBuilder(args.toList()).redirectErrorStream(true).start()
        try {
            check(process.waitFor(2, TimeUnit.SECONDS)) { "Settings operation timed out" }
            // These fixed get/put/getprop commands have small bounded output.
            val bytes = process.inputStream.use { it.readBytes() }
            check(bytes.size <= 4096 && process.exitValue() == 0) { "Settings operation rejected" }
            return bytes.toString(Charsets.UTF_8).trim()
        } finally {
            process.destroy()
        }
    }
}
