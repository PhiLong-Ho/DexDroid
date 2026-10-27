package dev.dexdroid

/** One deferred request preserves its callbacks/guard across desktop startup. */
internal class PendingLaunch<T>(
    val component: T,
    val rotation: Int?,
    val completed: ((String?) -> Unit)?,
    val allowed: (() -> Boolean)?,
    val discarded: (() -> Unit)?,
    val started: (() -> Unit)?,
) {
    private var transferred = false

    fun deliver(dispatch: (PendingLaunch<T>) -> Unit) {
        if (transferred) return
        transferred = true
        dispatch(this)
    }

    fun discard() {
        if (transferred) return
        transferred = true
        discarded?.invoke()
    }
}
