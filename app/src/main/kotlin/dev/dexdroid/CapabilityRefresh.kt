package dev.dexdroid

/** Main-owned bounded read state. A timed-out worker remains occupied until it actually exits. */
internal class CapabilityRefresh<T> {
    enum class Phase {
        IDLE,
        READING,
        READY,
        FAILED,
    }

    class Request internal constructor(val owner: Any) {
        internal var valid = true
    }

    private var owner: Any? = null
    private val pages = mutableSetOf<Any>()
    private var active: Request? = null
    private var requested = false
    private var closed = false
    var value: T? = null
        private set

    var phase = Phase.IDLE
        private set

    val visible
        get() = pages.isNotEmpty()

    val blocked
        get() = active?.valid == false

    fun changeOwner(next: Any) {
        if (closed || owner === next) return
        owner = next
        value = null
        active?.valid = false
        phase = Phase.IDLE
        requested = visible
    }

    fun attach(): Any = Any().also { if (!closed) pages.add(it) }

    fun detach(page: Any) {
        pages.remove(page)
        if (!visible) {
            active?.valid = false
            requested = false
            if (phase != Phase.FAILED) phase = if (value == null) Phase.IDLE else Phase.READY
        }
    }

    fun request(): Request? {
        if (closed || !visible || owner == null) return null
        // Same-owner presses join the existing read, never add work behind it.
        if (active?.let { it.valid && it.owner === owner } != true) requested = true
        return takePending()
    }

    fun takePending(): Request? {
        if (closed || !visible || !requested || active != null || owner == null) return null
        requested = false
        return Request(owner!!).also {
            active = it
            phase = Phase.READING
        }
    }

    fun timeout(request: Request): Boolean {
        if (closed || active !== request || !request.valid || owner !== request.owner) return false
        request.valid = false
        phase = Phase.FAILED
        return true
    }

    fun complete(request: Request, result: T?): Request? {
        if (closed || active !== request) return null
        active = null
        if (request.valid && visible && owner === request.owner) {
            if (result != null) value = result
            phase = if (result == null) Phase.FAILED else Phase.READY
        }
        return takePending()
    }

    fun close() {
        closed = true
        active?.valid = false
        requested = false
        pages.clear()
        value = null
        phase = Phase.IDLE
    }
}
