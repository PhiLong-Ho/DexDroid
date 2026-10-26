package dev.dexdroid

/**
 * Capacity-one main-thread sequence. Drivers report real completion; callbacks cannot advance
 * twice.
 */
internal class WorkspaceRestore(private val driver: Driver) {
    interface Driver {
        fun validate(snapshot: WorkspaceSnapshot, done: (String?) -> Unit)

        fun ready(): Boolean

        fun replace(done: (String?) -> Unit)

        fun start(snapshot: WorkspaceSnapshot, done: (String?) -> Unit)

        fun launch(app: WorkspaceSnapshot.App, pane: Int, done: (String?) -> Unit)

        fun finish(snapshot: WorkspaceSnapshot, done: (String?) -> Unit)

        fun changed(message: String)
    }

    private class Operation(val snapshot: WorkspaceSnapshot) {
        var step = 0
        var waiting = false
        var replaced = false
    }

    private var operation: Operation? = null
    val active: Boolean
        get() = operation != null

    val snapshot: WorkspaceSnapshot?
        get() = operation?.snapshot

    fun request(snapshot: WorkspaceSnapshot) {
        if (active) return
        val op = Operation(snapshot)
        operation = op
        driver.changed("Checking saved workspace apps…")
        step(op, { driver.validate(snapshot, it) }) {
            op.waiting = true
            driver.changed("Waiting for monitor and Shizuku. Open Settings for connection help.")
            ready()
        }
    }

    fun ready() {
        val op = operation ?: return
        if (!op.waiting || !driver.ready()) return
        op.waiting = false
        driver.changed("Rechecking saved apps before replacing the workspace…")
        step(op, { driver.validate(op.snapshot, it) }) {
            if (!driver.ready()) {
                op.waiting = true
                driver.changed(
                    "Waiting for monitor and Shizuku. Open Settings for connection help."
                )
            } else replace(op)
        }
    }

    private fun replace(op: Operation) {
        op.replaced = true
        driver.changed("Restoring workspace · replacing current arrangement…")
        step(op, { driver.replace(it) }) {
            step(op, { driver.start(op.snapshot, it) }) {
                // Launch the selected pane last so ordinary Android focus agrees with routing.
                val order =
                    if (op.snapshot.split) listOf(1 - op.snapshot.selected, op.snapshot.selected)
                    else listOf(0)
                launchNext(op, order, 0)
            }
        }
    }

    private fun launchNext(op: Operation, order: List<Int>, index: Int) {
        if (operation !== op) return
        if (index == order.size) {
            step(op, { driver.finish(op.snapshot, it) }) {
                operation = null
                driver.changed("Workspace restored")
            }
            return
        }
        val pane = order[index]
        val app = op.snapshot.app(pane)
        if (app == null) {
            launchNext(op, order, index + 1)
            return
        }
        driver.changed("Restoring workspace · opening pane ${pane + 1}…")
        step(op, { driver.launch(app, pane, it) }) { launchNext(op, order, index + 1) }
    }

    private fun step(op: Operation, action: ((String?) -> Unit) -> Unit, next: () -> Unit) {
        if (operation !== op) return
        val serial = ++op.step
        action { error ->
            if (operation !== op || serial != op.step) return@action
            op.step++
            if (error == null) next()
            else {
                operation = null
                driver.changed(
                    (if (op.replaced) "Workspace partly restored. "
                    else "Workspace not changed. ") + error
                )
            }
        }
    }

    fun cancel(message: String = "Workspace restoration canceled") {
        if (operation == null) return
        operation = null
        driver.changed(message)
    }
}
