package dev.dexdroid

/** Logical workspace geometry; display IDs and Android views never enter this model. */
class SplitLayout {
    @JvmField var enabled: Boolean = false
    @JvmField var vertical: Boolean = false
    @JvmField var swapped: Boolean = false
    @JvmField var selected: Int = 0
    @JvmField var maximized: Int = -1
    @JvmField var ratio: Float = .5f

    fun paneAtPosition(position: Int): Int {
        return if (swapped) 1 - position else position
    }

    fun openLabel(position: Int): String {
        if (!enabled) return "Open app"
        return if (vertical) (if (position == 0) "Open up" else "Open down")
        else (if (position == 0) "Open left" else "Open right")
    }

    fun copy(): SplitLayout {
        val value = SplitLayout()
        value.enabled = enabled
        value.vertical = vertical
        value.swapped = swapped
        value.selected = selected
        value.maximized = maximized
        value.ratio = ratio
        return value
    }

    class Box(
        @JvmField val x: Int,
        @JvmField val y: Int,
        @JvmField val width: Int,
        @JvmField val height: Int,
    ) {

        fun contains(px: Float, py: Float): Boolean {
            return px >= x && py >= y && px < x + width && py < y + height
        }
    }

    fun normal(pane: Int, width: Int, height: Int, gap: Int): Box {
        if (!enabled && pane == 0) return Box(0, 0, width, height)
        val span = if (vertical) height else width
        val divider = Math.min(Math.max(1, gap), Math.max(1, span / 8))
        val first =
            Math.max(1, Math.round((span - divider) * Math.max(.25f, Math.min(.75f, ratio))))
        val slot = if (swapped) 1 - pane else pane
        val start = if (slot == 0) 0 else first + divider
        val size = if (slot == 0) first else Math.max(1, span - start)
        return if (vertical) Box(0, start, width, size) else Box(start, 0, size, height)
    }

    fun pane(pane: Int, width: Int, height: Int, gap: Int): Box {
        if (maximized >= 0)
            return if (pane == maximized) Box(0, 0, width, height) else Box(0, 0, 0, 0)
        return normal(pane, width, height, gap)
    }

    fun display(pane: Int, width: Int, height: Int, gap: Int): Box {
        val visible = pane(pane, width, height, gap)
        return if (visible.width == 0) normal(pane, width, height, gap) else visible
    }

    fun hit(x: Float, y: Float, width: Int, height: Int, gap: Int): Int {
        for (i in 0..1) if (pane(i, width, height, gap).contains(x, y)) return i
        return -1
    }

    fun divider(width: Int, height: Int, gap: Int): Box {
        var gap = gap
        if (maximized >= 0 || !enabled) return Box(0, 0, 0, 0)
        gap = Math.min(Math.max(1, gap), Math.max(1, (if (vertical) height else width) / 8))
        val first = normal(if (swapped) 1 else 0, width, height, gap)
        return if (vertical) Box(0, first.height, width, gap) else Box(first.width, 0, gap, height)
    }

    fun dividerGrab(width: Int, height: Int, gap: Int, minimum: Int): Box {
        val line = divider(width, height, gap)
        if (line.width == 0 || line.height == 0) return line
        if (vertical) {
            val size = Math.min(height, Math.max(line.height, minimum))
            val top = Math.max(0, Math.min(height - size, line.y + (line.height - size) / 2))
            return Box(0, top, width, size)
        }
        val size = Math.min(width, Math.max(line.width, minimum))
        val left = Math.max(0, Math.min(width - size, line.x + (line.width - size) / 2))
        return Box(left, 0, size, height)
    }
}
