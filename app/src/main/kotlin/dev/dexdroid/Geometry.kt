package dev.dexdroid

/** Pure coordinate model shared by the rendered surface and the cursor. */
object Geometry {
    @JvmStatic
    fun map(
        u: Float,
        v: Float,
        rotation: Int,
        width: Float,
        height: Float,
        desktopW: Int,
        desktopH: Int,
    ): FloatArray {
        val aspect =
            if ((rotation % 2 == 0)) desktopW.toFloat() / desktopH
            else desktopH.toFloat() / desktopW
        val contentW = Math.min(width, height * aspect)
        val contentH = contentW / aspect
        var x = u
        var y = v
        when (rotation) {
            1 -> {
                x = 1 - v
                y = u
            }
            2 -> {
                x = 1 - u
                y = 1 - v
            }
            3 -> {
                x = v
                y = 1 - u
            }
        }
        return floatArrayOf(
            (width - contentW) / 2 + x * contentW,
            (height - contentH) / 2 + y * contentH,
        )
    }

    @JvmStatic
    fun clamp(value: Float, min: Float, max: Float): Float {
        return Math.max(min, Math.min(max, value))
    }

    /** Physical monitor point to virtual pixels; bars return null. */
    @JvmStatic
    fun unmap(
        x: Float,
        y: Float,
        rotation: Int,
        width: Float,
        height: Float,
        desktopW: Int,
        desktopH: Int,
    ): FloatArray? {
        if (width <= 0 || height <= 0) return null
        val aspect =
            if ((rotation % 2 == 0)) desktopW.toFloat() / desktopH
            else desktopH.toFloat() / desktopW
        val contentW = Math.min(width, height * aspect)
        val contentH = contentW / aspect
        val u = (x - (width - contentW) / 2) / contentW
        val v = (y - (height - contentH) / 2) / contentH
        if (u < 0 || v < 0 || u >= 1 || v >= 1) return null
        var a = u
        var b = v
        when (rotation) {
            1 -> {
                a = v
                b = 1 - u
            }
            2 -> {
                a = 1 - u
                b = 1 - v
            }
            3 -> {
                a = 1 - v
                b = u
            }
        }
        return floatArrayOf(
            clamp(a * desktopW, 0f, (desktopW - 1).toFloat()),
            clamp(b * desktopH, 0f, (desktopH - 1).toFloat()),
        )
    }

    @JvmStatic
    fun recommendedDpi(shortEdge: Int): Int {
        return Math.max(120, Math.min(480, Math.round(shortEdge.toFloat() / 5f / 20f) * 20))
    }
}
