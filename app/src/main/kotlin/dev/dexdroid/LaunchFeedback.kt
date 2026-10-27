package dev.dexdroid

/** Main-owned displayed operation. Superseding a notice never cancels native work. */
internal class LaunchFeedback {
    class Ticket(val name: String)

    enum class Phase {
        PENDING,
        SUCCESS,
        FAILURE,
        CANCELED,
    }

    data class State(
        val ticket: Ticket,
        val notice: String,
        val phase: Phase,
        val monitorVisible: Boolean = true,
    )

    var state: State? = null
        private set

    var lastResult: String? = null
        private set

    fun begin(name: String, destination: String?, waiting: Boolean): Ticket {
        val ticket = Ticket(name)
        state = State(ticket, opening(ticket, destination, waiting), Phase.PENDING)
        return ticket
    }

    private fun opening(ticket: Ticket, destination: String?, waiting: Boolean): String =
        if (waiting) "Starting desktop · opening ${ticket.name}…"
        else "Opening ${ticket.name}${destination?.let { " on $it" }.orEmpty()}…"

    fun dispatched(ticket: Ticket, destination: String?): Boolean {
        if (state?.ticket !== ticket || state?.phase != Phase.PENDING) return false
        state = State(ticket, opening(ticket, destination, false), Phase.PENDING)
        return true
    }

    fun complete(ticket: Ticket, error: String?): Boolean {
        if (state?.ticket !== ticket || state?.phase != Phase.PENDING) return false
        val notice = if (error == null) "${ticket.name} opened" else "${ticket.name} didn’t open"
        state = State(ticket, notice, if (error == null) Phase.SUCCESS else Phase.FAILURE)
        lastResult = if (error == null) notice else "$notice. $error Open Apps to try again."
        return true
    }

    fun cancel(ticket: Ticket? = state?.ticket): Boolean {
        if (ticket == null || state?.ticket !== ticket || state?.phase != Phase.PENDING)
            return false
        state = State(ticket, "App launch canceled", Phase.CANCELED)
        lastResult =
            "App launch canceled. Already completed app changes may remain. Open Apps to try again."
        return true
    }

    fun expire(ticket: Ticket) {
        val value = state ?: return
        if (value.ticket !== ticket || value.phase == Phase.PENDING) return
        state = if (value.phase == Phase.FAILURE) value.copy(monitorVisible = false) else null
    }

    fun clear() {
        state = null
    }
}
