package dev.dexdroid

/** Main-thread policy with an injected monotonic time; no Android, queues or persisted state. */
internal class PointerIdle {
    enum class Source {
        PAD,
        RELAY,
        CONTROLLER,
    }

    class Hold internal constructor()

    private val holds = mutableMapOf<Source, Hold>()
    private var lastActivity: Long? = null
    var revision = 0L
        private set

    fun activity(now: Long) {
        lastActivity = now
        revision++
    }

    fun begin(source: Source, now: Long): Hold {
        val token = Hold()
        holds[source] = token
        activity(now)
        return token
    }

    fun release(source: Source, token: Hold, now: Long): Boolean {
        if (holds[source] != token) return false
        holds.remove(source)
        activity(now)
        return true
    }

    fun clear() {
        holds.clear()
        lastActivity = null
        revision++
    }

    fun deadline(): Long? = if (holds.isEmpty()) lastActivity?.plus(IDLE_MS) else null

    fun due(now: Long, expectedRevision: Long) =
        expectedRevision == revision && deadline()?.let { now >= it } == true

    companion object {
        const val IDLE_MS = 5000L
        const val FADE_MS = 200L
    }
}
