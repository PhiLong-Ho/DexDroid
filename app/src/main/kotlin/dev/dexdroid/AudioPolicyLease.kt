package dev.dexdroid

/** Owns only a preferred-policy change, never the underlying audio device. */
internal class AudioPolicyLease<T>(
    private val backend: Backend<T>,
    private val same: (List<T>, List<T>) -> Boolean = { a, b -> a == b },
) {
    interface Backend<T> {
        fun read(): List<T>

        fun write(devices: List<T>): Boolean
    }

    private var original: List<T>? = null
    private var candidates: List<List<T>> = emptyList()

    fun owned(): Boolean = candidates.isNotEmpty()

    private fun retainIfOurs(actual: List<T>): Boolean {
        if (candidates.any { same(actual, it) }) {
            candidates = listOf(actual.toList())
            return true
        }
        original = null
        candidates = emptyList()
        return false
    }

    /** False means an external writer took over; never fight that writer. */
    fun reconcile(): Boolean {
        if (!owned()) return true
        return retainIfOurs(backend.read())
    }

    fun select(device: T) {
        reconcile()
        if (original == null) original = backend.read().toList()
        val requested = listOf(device)
        // Keep a recovery candidate even if the setter throws after changing policy.
        val previous = candidates
        candidates = listOf(requested) + previous
        try {
            val accepted = backend.write(requested)
            val actual = backend.read()
            if (!same(actual, requested)) {
                retainIfOurs(actual)
                throw IllegalStateException("Android did not confirm the requested preference")
            }
            candidates = listOf(requested)
            if (!accepted)
                throw IllegalStateException("Android reported an uncertain routing result")
        } catch (error: Exception) {
            // A thrown setter can fail before OR after mutation. Check both the attempted
            // and previous owned policy, retaining both if the read is unavailable.
            try {
                retainIfOurs(backend.read())
            } catch (_: Exception) {}
            throw error
        }
    }

    /** Compare before restore: a later external preference always wins. */
    fun release(): Boolean {
        if (!owned()) return true
        if (!retainIfOurs(backend.read())) return false
        val restore = checkNotNull(original)
        val accepted = backend.write(restore)
        if (!same(backend.read(), restore) || !accepted)
            throw IllegalStateException("Could not confirm that the audio setting was released")
        original = null
        candidates = emptyList()
        return true
    }
}
