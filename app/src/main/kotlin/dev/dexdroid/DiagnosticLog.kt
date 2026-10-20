package dev.dexdroid

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** Bounded, in-memory event history; never records screen or typed content. */
class DiagnosticLog {
    private val events: ArrayDeque<String?> = ArrayDeque<String?>()

    @Synchronized
    fun add(event: String?) {
        events.addLast(SimpleDateFormat("HH:mm:ss.SSS", Locale.US!!).format(Date()) + "  " + event)
        while (events.size > 250) events.removeFirst()
    }

    @Synchronized
    fun snapshot(): String {
        return if (events.isEmpty()) "No events recorded." else events.joinToString("\n")
    }

    @Synchronized
    fun clear() {
        events.clear()
    }
}
