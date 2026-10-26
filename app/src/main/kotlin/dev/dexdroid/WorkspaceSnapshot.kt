package dev.dexdroid

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/** Durable values only. Task/display identities and app-owned screen contents are never saved. */
internal data class WorkspaceSnapshot(
    val split: Boolean,
    val vertical: Boolean,
    val swapped: Boolean,
    val ratio: Float,
    val selected: Int,
    val maximized: Int,
    val rotation: Int,
    val first: App?,
    val second: App?,
) {
    data class App(val component: String, val label: String) {
        init {
            require(component.length in 3..512 && component.count { it == '/' } == 1)
            require(component.none { it.isWhitespace() || it.isISOControl() })
            require(
                component.substringBefore('/').isNotEmpty() &&
                    component.substringAfter('/').isNotEmpty()
            )
            require(label.isNotBlank() && label.length <= 200 && label.none { it.isISOControl() })
        }
    }

    init {
        require(rotation in 0..3 && selected in 0..1 && maximized in -1..1)
        require(ratio.isFinite() && ratio >= 0.25f && ratio <= 0.75f)
        require(first != null || second != null)
        require(split || (first != null && second == null && selected == 0 && maximized == -1))
        require(maximized < 0 || maximized == selected)
        require(
            first == null ||
                second == null ||
                first.component.substringBefore('/') != second.component.substringBefore('/')
        )
    }

    fun app(pane: Int): App? = if (pane == 0) first else second

    fun layout(final: Boolean = true): SplitLayout =
        SplitLayout().also {
            it.enabled = split
            it.vertical = vertical
            it.swapped = swapped
            it.ratio = ratio
            it.selected = if (final) selected else 0
            it.maximized = if (final) maximized else -1
        }

    fun summary(): String {
        val apps =
            if (!split) first!!.label
            else {
                val left = if (swapped) second else first
                val right = if (swapped) first else second
                "${if (vertical) "Up" else "Left"}: ${left?.label ?: "Empty"}\n${if (vertical) "Down" else "Right"}: ${right?.label ?: "Empty"}"
            }
        val layout =
            if (!split) "Single view"
            else
                "${if (vertical) "Up/down" else "Left/right"} · ${(ratio * 100).toInt()}/${100 - (ratio * 100).toInt()}"
        return "$apps\n$layout · ${rotation * 90}°" +
            if (split)
                "\nSelected: ${position(selected)} · Fullscreen: ${if (maximized < 0) "Off" else position(maximized)}"
            else ""
    }

    private fun position(pane: Int): String =
        if ((pane == 0) != swapped) {
            if (vertical) "Up" else "Left"
        } else if (vertical) "Down" else "Right"

    fun encode(): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            out.writeBoolean(split)
            out.writeBoolean(vertical)
            out.writeBoolean(swapped)
            out.writeFloat(ratio)
            out.writeInt(selected)
            out.writeInt(maximized)
            out.writeInt(rotation)
            for (app in listOf(first, second)) {
                out.writeBoolean(app != null)
                if (app != null) {
                    out.writeUTF(app.component)
                    out.writeUTF(app.label)
                }
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    companion object {
        fun decode(value: String): WorkspaceSnapshot {
            require(value.length in 1..8192)
            DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(value))).use { input ->
                require(input.readInt() == 1)
                val split = input.readBoolean()
                val vertical = input.readBoolean()
                val swapped = input.readBoolean()
                val ratio = input.readFloat()
                val selected = input.readInt()
                val maximized = input.readInt()
                val rotation = input.readInt()
                fun app(): App? =
                    if (input.readBoolean()) App(input.readUTF(), input.readUTF()) else null
                val result =
                    WorkspaceSnapshot(
                        split,
                        vertical,
                        swapped,
                        ratio,
                        selected,
                        maximized,
                        rotation,
                        app(),
                        app(),
                    )
                require(input.read() == -1)
                return result
            }
        }
    }
}
