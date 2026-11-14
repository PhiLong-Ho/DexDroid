package dev.dexdroid

/** One partition of full logical output; physical rotation/Fit always uses output dimensions. */
data class DesktopViewport(
    val outputWidth: Int,
    val outputHeight: Int,
    val dpi: Int,
    val edgesEnabled: Boolean,
) {
    val barHeight: Int =
        if (edgesEnabled) 0 else Math.round(minOf(outputWidth, outputHeight) * .04f)
    val appWidth: Int = outputWidth
    val appHeight: Int = outputHeight - barHeight

    init {
        require(
            outputWidth > 0 &&
                outputHeight > 0 &&
                dpi > 0 &&
                appHeight > 0 &&
                (edgesEnabled || barHeight > 0)
        ) {
            "Output must leave positive app space above the taskbar"
        }
    }

    fun containsApp(x: Float, y: Float): Boolean =
        x.isFinite() && y.isFinite() && x >= 0 && y >= 0 && x < appWidth && y < appHeight
}
