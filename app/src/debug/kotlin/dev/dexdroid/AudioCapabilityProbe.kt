package dev.dexdroid

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Process
import org.json.JSONObject

/** Read-only shell probe. No addresses, product names, package content or policy writes. */
@android.annotation.SuppressLint(
    "PrivateApi"
) // Read-only capability probe, guarded by the actual shell UID.
object AudioCapabilityProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        check(Process.myUid() == 2000) { "Shell-only diagnostic" }
        if (android.os.Looper.myLooper() == null) {
            if (android.os.Looper.getMainLooper() == null) android.os.Looper.prepareMainLooper()
            else android.os.Looper.prepare()
        }
        val result = JSONObject().put("sdk", Build.VERSION.SDK_INT).put("uid", Process.myUid())
        fun unwrap(error: Throwable): Throwable = error.cause?.let(::unwrap) ?: error
        fun stage(name: String, action: () -> Any?) {
            try {
                result.put(name, action() ?: JSONObject.NULL)
            } catch (error: Throwable) {
                result.put(name, "error:${unwrap(error).javaClass.simpleName}")
            }
        }
        try {
            val type = Class.forName("android.app.ActivityThread")
            val thread =
                type.getMethod("currentActivityThread").invoke(null)
                    ?: type.getMethod("systemMain").invoke(null)
            val context =
                (type.getMethod("getSystemContext").invoke(thread) as Context).createPackageContext(
                    "com.android.shell",
                    Context.CONTEXT_IGNORE_SECURITY,
                )
            stage("routingPermission") {
                context.checkPermission(
                    "android.permission.MODIFY_AUDIO_ROUTING",
                    Process.myPid(),
                    Process.myUid(),
                ) == PackageManager.PERMISSION_GRANTED
            }
            val audio = context.getSystemService(AudioManager::class.java)
            stage("outputTypes") {
                org.json.JSONArray(
                    audio
                        .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                        .filter { it.isSink }
                        .map { it.type }
                        .sorted()
                )
            }
            val strategyType = Class.forName("android.media.audiopolicy.AudioProductStrategy")
            val deviceType = Class.forName("android.media.AudioDeviceAttributes")
            result.put(
                "attributeConstructors",
                org.json.JSONArray(
                    deviceType.constructors
                        .map {
                            it.parameterTypes.joinToString(",") { parameter ->
                                parameter.simpleName
                            }
                        }
                        .sorted()
                ),
            )
            val plural = Build.VERSION.SDK_INT >= 31
            val getter =
                if (plural) "getPreferredDevicesForStrategy" else "getPreferredDeviceForStrategy"
            val setter =
                if (plural) "setPreferredDevicesForStrategy" else "setPreferredDeviceForStrategy"
            stage("getterPresent") {
                AudioManager::class.java.getMethod(getter, strategyType)
                true
            }
            stage("setterPresent") {
                AudioManager::class
                    .java
                    .getMethod(setter, strategyType, if (plural) List::class.java else deviceType)
                true
            }
            stage("removePresent") {
                AudioManager::class.java.getMethod("removePreferredDeviceForStrategy", strategyType)
                true
            }
            stage("attributesConstructor") {
                deviceType.getConstructor(AudioDeviceInfo::class.java)
                true
            }
            stage("attributeIdentity") {
                listOf("getRole", "getType", "getAddress").all { deviceType.getMethod(it) != null }
            }
            stage("optionalRouteGetter") {
                AudioManager::class
                    .java
                    .getMethod("getDevicesForAttributes", AudioAttributes::class.java)
                true
            }
            val media =
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            val strategies =
                strategyType.getMethod("getAudioProductStrategies").invoke(null) as List<*>
            val matching =
                strategies.filterNotNull().filter {
                    strategyType
                        .getMethod("supportsAudioAttributes", AudioAttributes::class.java)
                        .invoke(it, media) == true
                }
            result.put("mediaStrategyCount", matching.size)
            if (matching.size == 1)
                stage("preferredRead") {
                    val value =
                        AudioManager::class
                            .java
                            .getMethod(getter, strategyType)
                            .invoke(audio, matching.single())
                    if (value is List<*>) value.size else if (value == null) 0 else 1
                }
        } catch (error: Throwable) {
            result.put("probeError", unwrap(error).javaClass.simpleName)
        }
        println("AUDIO_CAPABILITY " + result.toString())
        kotlin.system.exitProcess(0)
    }
}
