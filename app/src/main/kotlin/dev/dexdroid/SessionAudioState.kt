package dev.dexdroid

import android.os.Bundle

/** Bounded Binder summary. Endpoint addresses remain inside the shell helper. */
internal data class SessionAudioState(
    val available: Boolean = false,
    val selected: String = "",
    val route: String = "Unknown",
    val message: String = "Start the desktop to choose its media output.",
    val outputs: List<Output> = emptyList(),
    val pending: Boolean = false,
    val uncertain: Boolean = false,
    val confirmed: Boolean = false,
) {
    data class Output(val key: String, val name: String, val kind: String)

    fun bundle(): Bundle =
        Bundle().apply {
            putBoolean("available", available)
            putString("selected", selected)
            putString("route", route)
            putString("message", message)
            putBoolean("uncertain", uncertain)
            putBoolean("confirmed", confirmed)
            putStringArrayList("keys", ArrayList(outputs.map { it.key }))
            putStringArrayList("names", ArrayList(outputs.map { it.name }))
            putStringArrayList("kinds", ArrayList(outputs.map { it.kind }))
        }

    companion object {
        fun from(bundle: Bundle): SessionAudioState {
            val keys = bundle.getStringArrayList("keys") ?: arrayListOf()
            val names = bundle.getStringArrayList("names") ?: arrayListOf()
            val kinds = bundle.getStringArrayList("kinds") ?: arrayListOf()
            require(keys.size <= 64 && keys.size == names.size && keys.size == kinds.size)
            return SessionAudioState(
                bundle.getBoolean("available"),
                bundle.getString("selected", ""),
                bundle.getString("route", "Unknown"),
                bundle.getString("message", "Audio output control unavailable."),
                keys.indices.map { Output(keys[it], names[it], kinds[it]) },
                uncertain = bundle.getBoolean("uncertain"),
                confirmed = bundle.getBoolean("confirmed"),
            )
        }
    }
}
