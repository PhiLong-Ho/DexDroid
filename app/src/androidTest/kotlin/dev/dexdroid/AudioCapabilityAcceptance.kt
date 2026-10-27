package dev.dexdroid

import android.app.Instrumentation
import android.content.*
import android.os.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** Capability outcome, not a routing/acoustic pass; capable-device AudioAcceptance stays strict. */
internal class AudioCapabilityAcceptance(private val runner: Instrumentation) {
    fun run(): String {
        var session: SessionService? = null
        val ready = CountDownLatch(1)
        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    session = (binder as SessionService.LocalBinder).get()
                    ready.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }
        DeviceTestSupport.startPhoneBounded(runner)
        check(
            runner.targetContext.bindService(
                Intent(runner.targetContext, SessionService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        )
        try {
            check(ready.await(5, TimeUnit.SECONDS))
            val until = SystemClock.uptimeMillis() + 25000
            while (
                (session!!.id < 0 || session!!.audioState().pending) &&
                    SystemClock.uptimeMillis() < until
            ) Thread.sleep(50)
            check(session!!.id >= 0 && !session!!.audioState().pending) {
                "Audio capability did not settle"
            }
            val path = runner.targetContext.applicationInfo.sourceDir
            check(path.matches(Regex("[A-Za-z0-9._/+=~-]+"))) { "Unexpected APK path" }
            val output =
                DeviceTestSupport.shell(
                    runner,
                    "CLASSPATH=$path /system/bin/app_process /system/bin dev.dexdroid.AudioCapabilityProbe",
                )!!
            val line =
                output.lineSequence().firstOrNull { it.startsWith("AUDIO_CAPABILITY ") }
                    ?: error("Shell capability output missing")
            val probe = JSONObject(line.removePrefix("AUDIO_CAPABILITY "))
            check(probe.getInt("uid") == 2000 && probe.getInt("sdk") == Build.VERSION.SDK_INT)
            val state = session!!.audioState()
            if (state.available) {
                check(probe.optBoolean("routingPermission")) {
                    "Selector advertised without shell permission"
                }
                listOf(
                        "getterPresent",
                        "setterPresent",
                        "removePresent",
                        "attributesConstructor",
                        "attributeIdentity",
                    )
                    .forEach {
                        check(probe.optBoolean(it)) {
                            "Selector advertised with absent required API $it"
                        }
                    }
                check(state.outputs.size <= 64)
                check(probe.optInt("mediaStrategyCount", -1) == 1) {
                    "MEDIA strategy not independently resolved"
                }
                check(
                    probe.opt("preferredRead") is Number && probe.optInt("preferredRead", -1) >= 0
                ) {
                    "Preferred policy read not independently confirmed"
                }
                check(!probe.has("probeError")) { "Available capability diagnostic is incomplete" }
            } else {
                check(state.message.isNotBlank() && !state.confirmed) {
                    "Unavailable capability must retain explanation, never success"
                }
            }
            val summary =
                JSONObject()
                    .put("available", state.available)
                    .put("pending", state.pending)
                    .put("uncertain", state.uncertain)
                    .put("confirmed", state.confirmed)
                    .put("outputCount", state.outputs.size)
                    .put("outputKinds", org.json.JSONArray(state.outputs.map { it.kind }))
                    .put("message", state.message)
            val diagnosis =
                if (
                    probe.has("probeError") ||
                        probe.opt("routingPermission") !is Boolean ||
                        probe.opt("outputTypes") !is org.json.JSONArray
                )
                    "UNKNOWN diagnostic stages are incomplete; no cause inferred."
                else
                    "OBSERVATION read-only capability and output discovery stages recorded separately."
            return "PASS diagnostic executed under the actual shell UID and SDK\n" +
                (if (state.available)
                    "PASS editable capability matches independently observed required stages\n"
                else "PASS unavailable product state retains explanation and no confirmation\n") +
                diagnosis +
                "\n" +
                line +
                "\nAUDIO_PRODUCT " +
                summary +
                "\n" +
                "SCOPE No selection or acoustic claim; capable-device media routing suite remains separate.\n"
        } finally {
            runner.targetContext.unbindService(connection)
        }
    }
}
