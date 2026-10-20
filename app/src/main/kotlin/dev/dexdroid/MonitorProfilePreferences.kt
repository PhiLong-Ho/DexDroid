package dev.dexdroid

import android.content.SharedPreferences
import java.util.HashMap

/** Atomic, isolated persistence; app overrides and legacy preferences are not rewritten. */
class MonitorProfilePreferences(private val preferences: SharedPreferences?) :
    MonitorProfiles.Store {

    public override fun read(): Map<String?, String?> {
        val result = HashMap<String?, String?>()
        for (entry in preferences!!.getAll()!!.entries) if (
            entry!!.key.startsWith(PREFIX) && entry!!.value is String
        )
            result.put(entry!!.key.substring(PREFIX.length), entry!!.value as String)
        return result
    }

    public override fun write(values: Map<String?, String?>?) {
        val edit = preferences!!.edit()
        for (key in preferences!!.getAll()!!.keys) if (key!!.startsWith(PREFIX)) edit!!.remove(key)
        for (entry in values!!.entries) edit!!.putString(PREFIX + entry!!.key, entry!!.value)
        edit!!.apply()
    }

    companion object {
        private val PREFIX: String = "monitorProfiles."
    }
}
