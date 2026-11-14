package dev.dexdroid

import android.os.Bundle

/** Fixed, content-free transport shared by the controller and the shell helper. */
internal data class PhoneScreenResult(
    val state: State,
    val reason: Reason = Reason.NONE,
    val lease: Long = 0,
    val safeAutoRestore: Boolean = false,
    val boot: String = "",
    val physical: Long = -1,
) {
    val hasReceipt: Boolean
        get() = validReceipt(boot, physical)

    enum class State {
        ON,
        OFF,
        PENDING,
        UNKNOWN,
        UNAVAILABLE,
    }

    enum class Reason {
        NONE,
        CHECKING,
        RESTORING,
        EXPIRED,
        CLOSED,
        UNSUPPORTED,
        INVALID_REQUEST,
        OWNER_LOST,
        BUSY,
        READBACK,
        FAILED,
    }

    fun toBundle(): Bundle =
        Bundle().apply {
            putString("state", state.name)
            putString("reason", reason.name)
            putLong("lease", lease)
            putBoolean("safeAutoRestore", safeAutoRestore)
            putString("boot", boot)
            putLong("physical", physical)
        }

    // Device identity is a runtime recovery receipt, never diagnostic/log content.
    override fun toString(): String =
        "PhoneScreenResult(state=$state, reason=$reason, lease=$lease, safeAutoRestore=$safeAutoRestore, hasReceipt=$hasReceipt)"

    companion object {
        const val OFF = 1
        const val RENEW = 2
        const val ON = 3
        const val POLL = 4
        const val CHECK = 5
        private val KEYS = setOf("state", "reason", "lease", "safeAutoRestore", "boot", "physical")

        fun validReceipt(boot: String?, physical: Long): Boolean =
            physical >= 0 &&
                boot?.matches(
                    Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                ) == true

        @Suppress(
            "DEPRECATION"
        ) // Raw typed values are required; Bundle getters otherwise default/coerce.
        fun fromBundle(bundle: Bundle?): PhoneScreenResult =
            try {
                requireNotNull(bundle)
                require(bundle.keySet() == KEYS)
                fromFields(KEYS.associateWith { bundle.get(it) })
            } catch (_: Exception) {
                PhoneScreenResult(State.UNKNOWN, Reason.FAILED)
            }

        /** Shared pure schema check so missing/wrong-typed IPC fields never become ON. */
        fun fromFields(fields: Map<String, *>): PhoneScreenResult =
            try {
                require(fields.keys == KEYS)
                val state = State.valueOf(fields["state"] as? String ?: error("State type"))
                val reason = Reason.valueOf(fields["reason"] as? String ?: error("Reason type"))
                val lease = fields["lease"] as? Long ?: error("Lease type")
                val safety = fields["safeAutoRestore"] as? Boolean ?: error("Safety type")
                val boot = fields["boot"] as? String ?: error("Boot type")
                val physical = fields["physical"] as? Long ?: error("Physical type")
                require(lease > 0)
                require((boot.isEmpty() && physical == -1L) || validReceipt(boot, physical))
                require(state !in setOf(State.ON, State.OFF) || validReceipt(boot, physical))
                require(
                    !safety ||
                        (state == State.ON &&
                            reason in setOf(Reason.EXPIRED, Reason.CLOSED, Reason.OWNER_LOST))
                )
                PhoneScreenResult(state, reason, lease, safety, boot, physical)
            } catch (_: Exception) {
                PhoneScreenResult(State.UNKNOWN, Reason.FAILED)
            }
    }
}
