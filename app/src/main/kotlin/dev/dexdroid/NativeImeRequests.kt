package dev.dexdroid

/** Main-owned policy observations; native text, app names and keyboard contents never enter it. */
internal class NativeImeRequests {
    class Target(val owner: Any, val display: Int)

    data class Request(val serial: Long, val enabled: Boolean, val targets: List<Target>)

    private var serial = 0L
    var request = Request(0, false, emptyList())
        private set

    var results: List<NativeImeResult> = emptyList()
        private set

    fun configure(targets: List<Target>, enabled: Boolean, retry: Boolean = false): Request? {
        require(targets.size <= 2 && targets.all { it.display >= 0 })
        val same =
            request.enabled == enabled &&
                targets.size == request.targets.size &&
                targets.indices.all {
                    targets[it].owner === request.targets[it].owner &&
                        targets[it].display == request.targets[it].display
                }
        if (same && !retry) return null
        request = Request(++serial, enabled, targets.toList())
        results = targets.map { NativeImeResult(NativeImeResult.State.PENDING) }
        return request
    }

    fun accept(target: Target, token: Long, result: NativeImeResult): Boolean {
        if (token != request.serial) return false
        val index =
            request.targets.indexOfFirst {
                it.owner === target.owner && it.display == target.display
            }
        if (index < 0) return false
        results = results.toMutableList().also { it[index] = result }
        return true
    }

    fun timeout(token: Long): Boolean {
        if (token != request.serial || results.none { it.state == NativeImeResult.State.PENDING })
            return false
        results =
            results.map {
                if (it.state == NativeImeResult.State.PENDING)
                    NativeImeResult(NativeImeResult.State.FAILED, NativeImeResult.Reason.TIMEOUT)
                else it
            }
        return true
    }
}
