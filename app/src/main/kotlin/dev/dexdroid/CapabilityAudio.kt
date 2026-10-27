package dev.dexdroid

/** Published state only. Uncertain/failed reads are never known capability rejection. */
internal fun SessionAudioState.capabilityFacts(active: Boolean): DeviceCapabilities.Audio {
    if (!active) return DeviceCapabilities.Audio()
    val reason =
        if (available || pending || uncertain) null
        else
            when (message) {
                "Audio output control unavailable. Android did not grant shell audio-routing permission." ->
                    DeviceCapabilities.AudioUnavailableReason.DENIED
                "Audio output control unavailable. This Android version does not expose the required audio API." ->
                    DeviceCapabilities.AudioUnavailableReason.UNSUPPORTED
                else -> null
            }
    return DeviceCapabilities.Audio(
        available,
        confirmed,
        pending,
        uncertain,
        reason,
        if (available) outputs.size else null,
    )
}
