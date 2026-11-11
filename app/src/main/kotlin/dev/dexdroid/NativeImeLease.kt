package dev.dexdroid

/** Policy configuration is separate from visible keyboard or text-delivery evidence. */
internal data class NativeImeResult(val state: State, val reason: Reason = Reason.NONE) {
    enum class State {
        PENDING,
        CONFIGURED,
        DEFAULT,
        UNAVAILABLE,
        FAILED,
        RETIRED,
    }

    enum class Reason {
        NONE,
        LEGACY_ANDROID,
        API_MISSING,
        PERMISSION_DENIED,
        UNTRUSTED_DISPLAY,
        READ_FAILED,
        WRITE_FAILED,
        READBACK_MISMATCH,
        DISPLAY_GONE,
        EXTERNAL_CHANGE,
        CLEANUP_UNCONFIRMED,
        TIMEOUT,
        TRANSPORT,
        CREATION_MODE,
    }

    fun description(): String =
        when (state) {
            State.PENDING -> "Checking this desktop…"
            State.CONFIGURED -> "Placement configured · Tap an app text field"
            State.DEFAULT -> "Android controls keyboard location"
            State.RETIRED -> "No current desktop"
            State.UNAVAILABLE ->
                when (reason) {
                    Reason.CREATION_MODE ->
                        "Applies on next desktop start · Finish your work, then use Stop and Start on the phone"
                    Reason.LEGACY_ANDROID ->
                        "Unavailable on Android 11 · Android controls keyboard location"
                    Reason.PERMISSION_DENIED -> "Unavailable · Android denied keyboard placement"
                    Reason.UNTRUSTED_DISPLAY ->
                        "Unavailable · This display cannot host the keyboard"
                    else -> "Unavailable · This Android version does not expose keyboard placement"
                }
            State.FAILED ->
                when (reason) {
                    Reason.TIMEOUT -> "Not confirmed · An earlier check may still finish"
                    Reason.EXTERNAL_CHANGE -> "Not applied · Keyboard policy changed elsewhere"
                    else -> "Could not apply keyboard placement"
                }
        }
}

internal class NativeImePolicyException(val reason: NativeImeResult.Reason) : RuntimeException()

/** One disposable display's baseline. All calls are serialized on its policy worker. */
internal class NativeImeLease(private val port: Port) {
    interface Port {
        fun active(): Boolean

        fun owned(): Boolean

        fun read(): Int

        fun write(policy: Int, retiring: Boolean)
    }

    private var baseline: Int? = null
    private var attempted = false

    fun apply(enabled: Boolean): NativeImeResult {
        if (!port.active()) return retired()
        if (!enabled) return restore(false)
        val before =
            try {
                read()
            } catch (error: Exception) {
                return failure(error, NativeImeResult.Reason.READ_FAILED)
            }
        if (!port.active()) return retired()
        // A new explicit request may acquire the current baseline after a prior release.
        if (baseline == null) baseline = before
        if (attempted && before != LOCAL) {
            baseline = null
            attempted = false
            return failed(NativeImeResult.Reason.EXTERNAL_CHANGE)
        }
        if (before == LOCAL) return NativeImeResult(NativeImeResult.State.CONFIGURED)
        if (!port.active()) return retired()
        attempted = true // A throwing setter may already have changed the native policy.
        try {
            port.write(LOCAL, false)
        } catch (error: Exception) {
            return failure(error, NativeImeResult.Reason.WRITE_FAILED)
        }
        if (!port.active()) return retired()
        val after =
            try {
                read()
            } catch (error: Exception) {
                return failure(error, NativeImeResult.Reason.READ_FAILED)
            }
        if (!port.active()) return retired()
        return if (after == LOCAL) NativeImeResult(NativeImeResult.State.CONFIGURED)
        else failed(NativeImeResult.Reason.READBACK_MISMATCH)
    }

    fun restore(retiring: Boolean): NativeImeResult {
        if (!port.owned()) return retired()
        if (!attempted) {
            baseline = null
            return NativeImeResult(NativeImeResult.State.DEFAULT)
        }
        val prior = baseline ?: return failed(NativeImeResult.Reason.CLEANUP_UNCONFIRMED)
        val current =
            try {
                read()
            } catch (_: Exception) {
                return failed(NativeImeResult.Reason.CLEANUP_UNCONFIRMED)
            }
        if (!port.owned() || (!retiring && !port.active())) return retired()
        if (current != LOCAL) {
            baseline = null
            attempted = false
            return NativeImeResult(
                NativeImeResult.State.DEFAULT,
                NativeImeResult.Reason.EXTERNAL_CHANGE,
            )
        }
        try {
            port.write(prior, retiring)
        } catch (_: Exception) {
            return failed(NativeImeResult.Reason.CLEANUP_UNCONFIRMED)
        }
        if (!port.owned() || (!retiring && !port.active())) return retired()
        val restored =
            try {
                read()
            } catch (_: Exception) {
                return failed(NativeImeResult.Reason.CLEANUP_UNCONFIRMED)
            }
        if (!port.owned() || (!retiring && !port.active())) return retired()
        if (restored != prior) return failed(NativeImeResult.Reason.CLEANUP_UNCONFIRMED)
        baseline = null
        attempted = false
        return NativeImeResult(NativeImeResult.State.DEFAULT)
    }

    private fun read(): Int = port.read().also { check(it in 0..2) { "Unknown keyboard policy" } }

    private fun failure(error: Exception, fallback: NativeImeResult.Reason): NativeImeResult {
        val reason = (error as? NativeImePolicyException)?.reason ?: fallback
        return if (reason == NativeImeResult.Reason.PERMISSION_DENIED)
            NativeImeResult(NativeImeResult.State.UNAVAILABLE, reason)
        else failed(reason)
    }

    private fun failed(reason: NativeImeResult.Reason) =
        NativeImeResult(NativeImeResult.State.FAILED, reason)

    private fun retired() =
        NativeImeResult(NativeImeResult.State.RETIRED, NativeImeResult.Reason.DISPLAY_GONE)

    companion object {
        const val LOCAL = 0
    }
}
