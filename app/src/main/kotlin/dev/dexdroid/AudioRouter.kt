package dev.dexdroid

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Process
import java.util.UUID

/** Primary helper only. Calls use shell identity and a lock independent of input. */
internal class AudioRouter {
    private var epoch = -1L
    private var owner: IBinder? = null
    private var backend: PlatformAudio? = null
    private var lease: AudioPolicyLease<Any>? = null
    private var selected = ""
    private var message = ""
    private var uncertain = false
    private val endpoints = linkedMapOf<String, Pair<String, AudioDeviceInfo>>()
    private var death: IBinder.DeathRecipient? = null
    private var confirmed = false

    private inline fun <T> shell(action: () -> T): T {
        val identity = Binder.clearCallingIdentity()
        try {
            return action()
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    @Synchronized
    fun begin(token: Long, client: IBinder): SessionAudioState = shell {
        if (epoch == token && owner === client) return@shell snapshot()
        if (owner != null) finish()
        epoch = token
        owner = client
        val recipient =
            IBinder.DeathRecipient {
                Thread({ ownerDied(token, client) }, "DexDroid-audio-owner-cleanup").start()
            }
        death = recipient
        client.linkToDeath(recipient, 0)
        selected = ""
        message = ""
        uncertain = false
        confirmed = false
        try {
            backend = PlatformAudio()
            lease = AudioPolicyLease(backend!!, backend!!::samePolicy)
            snapshot()
        } catch (error: Exception) {
            backend = null
            lease = null
            message = capabilityError(error)
            SessionAudioState(message = message)
        }
    }

    @Synchronized
    fun query(token: Long): SessionAudioState = shell {
        require(token == epoch && owner?.isBinderAlive == true) { "Audio session ended" }
        snapshot()
    }

    @Synchronized
    fun select(token: Long, key: String): SessionAudioState = shell {
        require(token == epoch && owner?.isBinderAlive == true) { "Audio session ended" }
        val platform = checkNotNull(backend) { "Audio output control unavailable" }
        val policy = checkNotNull(lease)
        confirmed = false
        try {
            refreshEndpoints(platform)
            if (key.isEmpty()) {
                val released = policy.release()
                selected = ""
                message = if (released) "Using system media output." else externalChange()
            } else {
                val endpoint =
                    endpoints[key]?.second
                        ?: throw IllegalStateException("That output is no longer available")
                policy.select(platform.attributes(endpoint))
                selected = key
                message = "Default media output preference confirmed."
            }
            uncertain = false
            confirmed = true
        } catch (error: Exception) {
            message = "Couldn't change the default output. " + safeError(error)
            uncertain = policy.owned()
            if (!policy.owned()) selected = ""
        }
        snapshot()
    }

    @Synchronized
    fun end(token: Long): SessionAudioState = shell {
        if (token != epoch) return@shell SessionAudioState()
        try {
            finish()
            SessionAudioState(message = "No desktop session. Audio setting released.")
        } catch (error: Exception) {
            uncertain = true
            SessionAudioState(
                message =
                    "Couldn't confirm that the audio setting was released. Use Android sound settings or restart the phone.",
                uncertain = true,
            )
        }
    }

    @Synchronized
    private fun ownerDied(token: Long, client: IBinder) {
        if (owner === client && epoch == token) end(token)
    }

    @Synchronized
    fun shutdown() {
        end(epoch)
    }

    private fun finish() {
        // Retain recovery state on failure; a new session may not silently replace it.
        lease?.release()
        death?.let { owner?.unlinkToDeath(it, 0) }
        death = null
        owner = null
        backend = null
        lease = null
        endpoints.clear()
        selected = ""
        epoch = -1
    }

    private fun snapshot(): SessionAudioState {
        val platform =
            backend
                ?: return SessionAudioState(
                    message =
                        message.ifEmpty {
                            "Audio output control unavailable. This device may not grant shell audio-routing permission."
                        }
                )
        try {
            if (lease?.reconcile() == false) {
                selected = ""
                message = externalChange()
                confirmed = false
            }
            refreshEndpoints(platform)
            if (selected.isNotEmpty() && selected !in endpoints) {
                lease?.release()
                selected = ""
                message = "Selected output disconnected. Using system output."
                confirmed = false
            }
            return SessionAudioState(
                true,
                selected,
                platform.route(),
                message,
                endpoints.map { (key, value) ->
                    SessionAudioState.Output(
                        key,
                        displayName(value.second),
                        kind(value.second.type),
                    )
                },
                uncertain = uncertain,
                confirmed =
                    confirmed &&
                        (selected.isEmpty() || platform.routeMatches(endpoints[selected]?.second)),
            )
        } catch (error: Exception) {
            return SessionAudioState(
                selected = selected,
                message = "Audio output control unavailable. " + safeError(error),
                uncertain = lease?.owned() == true,
            )
        }
    }

    private fun refreshEndpoints(platform: PlatformAudio) {
        val devices =
            platform.audio
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.isSink && kind(it.type).isNotEmpty() }
                .take(64)
        val previous = endpoints.toMap()
        endpoints.clear()
        for (device in devices) {
            val identity = "${device.type}:${device.address.ifEmpty { "port-${device.id}" }}"
            val old = previous.entries.firstOrNull { it.value.first == identity }
            val key = old?.key ?: UUID.randomUUID().toString()
            endpoints[key] = identity to device
        }
    }

    private fun externalChange() = "Output changed outside DexDroid. Following system output."

    private fun capabilityError(error: Exception): String =
        "Audio output control unavailable. " + safeError(error)

    private fun safeError(error: Throwable): String {
        var cause = error
        while (cause.cause != null) cause = cause.cause!!
        return when (cause) {
            is SecurityException -> "Android did not grant shell audio-routing permission."
            is ReflectiveOperationException ->
                "This Android version does not expose the required audio API."
            else -> "Android could not confirm the audio policy."
        }
    }

    private fun displayName(device: AudioDeviceInfo): String =
        if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) "Phone speaker"
        else
            device.productName.toString().take(100).let {
                if (it.isBlank() || it.startsWith("soc:") || it.startsWith("qcom"))
                    kind(device.type)
                else it
            }

    private fun kind(type: Int): String =
        when (type) {
            2 -> "Built-in speaker"
            3,
            4 -> "Wired headphones"
            8,
            26,
            27,
            30 -> "Bluetooth"
            9,
            10,
            29 -> "HDMI output"
            11,
            12,
            22 -> "USB audio"
            13 -> "Dock audio"
            19 -> "Auxiliary output"
            else -> ""
        }

    /** No hard-coded strategy ID; resolve MEDIA support from the running platform. */
    @android.annotation.SuppressLint(
        "PrivateApi"
    ) // Shell-only, probed; failure disables this feature.
    private class PlatformAudio : AudioPolicyLease.Backend<Any> {
        val audio: AudioManager
        private val strategyClass = Class.forName("android.media.audiopolicy.AudioProductStrategy")
        private val deviceClass = Class.forName("android.media.AudioDeviceAttributes")
        private val strategy: Any
        private val media =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

        init {
            check(Process.myUid() == 2000) { "Requires shell service" }
            val threadClass = Class.forName("android.app.ActivityThread")
            val thread =
                threadClass.getMethod("currentActivityThread").invoke(null)
                    ?: threadClass.getMethod("systemMain").invoke(null)
            val context =
                (threadClass.getMethod("getSystemContext").invoke(thread) as Context)
                    .createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY)
            if (
                context.checkPermission(
                    "android.permission.MODIFY_AUDIO_ROUTING",
                    Process.myPid(),
                    Process.myUid(),
                ) != PackageManager.PERMISSION_GRANTED
            )
                throw SecurityException()
            audio = context.getSystemService(AudioManager::class.java)
            val strategies =
                strategyClass.getMethod("getAudioProductStrategies").invoke(null) as List<*>
            val matches =
                strategies.filterNotNull().filter {
                    strategyClass
                        .getMethod("supportsAudioAttributes", AudioAttributes::class.java)
                        .invoke(it, media) == true
                }
            check(matches.size == 1) { "Ambiguous MEDIA strategy" }
            strategy = matches.single()
            read() // Probe actual privileged getter before exposing an editable selector.
        }

        override fun read(): List<Any> =
            if (Build.VERSION.SDK_INT >= 31) {
                @Suppress("UNCHECKED_CAST")
                (AudioManager::class
                        .java
                        .getMethod("getPreferredDevicesForStrategy", strategyClass)
                        .invoke(audio, strategy) as List<Any>)
                    .toList()
            } else {
                listOfNotNull(
                    AudioManager::class
                        .java
                        .getMethod("getPreferredDeviceForStrategy", strategyClass)
                        .invoke(audio, strategy)
                )
            }

        override fun write(devices: List<Any>): Boolean {
            val method =
                when {
                    devices.isEmpty() -> "removePreferredDeviceForStrategy"
                    Build.VERSION.SDK_INT >= 31 -> "setPreferredDevicesForStrategy"
                    else -> "setPreferredDeviceForStrategy"
                }
            val types =
                if (devices.isEmpty()) arrayOf(strategyClass)
                else
                    arrayOf(
                        strategyClass,
                        if (Build.VERSION.SDK_INT >= 31) List::class.java else deviceClass,
                    )
            val args =
                if (devices.isEmpty()) arrayOf(strategy)
                else
                    arrayOf(
                        strategy,
                        if (Build.VERSION.SDK_INT >= 31) devices else devices.single(),
                    )
            return AudioManager::class.java.getMethod(method, *types).invoke(audio, *args) == true
        }

        fun attributes(device: AudioDeviceInfo): Any =
            deviceClass.getConstructor(AudioDeviceInfo::class.java).newInstance(device)

        // Getter/setter attributes may differ in names, profiles and descriptors.
        // Device role, type and address identify policy ownership; retain full originals for
        // restore.
        fun samePolicy(first: List<Any>, second: List<Any>): Boolean {
            fun identity(value: Any) =
                listOf("getRole", "getType", "getAddress").map {
                    deviceClass.getMethod(it).invoke(value).toString()
                }
            return first.map(::identity).toSet() == second.map(::identity).toSet()
        }

        fun routeMatches(device: AudioDeviceInfo?): Boolean =
            try {
                if (device == null) false
                else {
                    val routes =
                        AudioManager::class
                            .java
                            .getMethod("getDevicesForAttributes", AudioAttributes::class.java)
                            .invoke(audio, media) as List<*>
                    samePolicy(routes.filterNotNull(), listOf(attributes(device)))
                }
            } catch (_: Exception) {
                true /* Unobserved route, not an audible claim. */
            }

        fun route(): String =
            try {
                @Suppress("UNCHECKED_CAST")
                val routes =
                    AudioManager::class
                        .java
                        .getMethod("getDevicesForAttributes", AudioAttributes::class.java)
                        .invoke(audio, media) as List<Any>
                routes
                    .take(8)
                    .joinToString(", ") { value ->
                        val type = deviceClass.getMethod("getType").invoke(value) as Int
                        when (type) {
                            2 -> "Phone speaker"
                            8,
                            26,
                            27,
                            30 -> "Bluetooth"
                            9,
                            10,
                            29 -> "HDMI output"
                            3,
                            4 -> "Wired headphones"
                            11,
                            12,
                            22 -> "USB audio"
                            else -> "Other output"
                        }
                    }
                    .ifEmpty { "Unknown" }
            } catch (_: Exception) {
                "Unknown"
            }
    }
}
