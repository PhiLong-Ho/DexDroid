package dev.dexdroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/** Offline, passive sketches. Native labels wrap independently of the drawn contact/arrow marks. */
internal class GuideDiagram(style: ControllerStyle, kind: Kind) : LinearLayout(style.context) {
    /** Native tooling can construct the same offline touchpad preview without a service. */
    constructor(context: Context) : this(ControllerStyle(context), Kind.TOUCHPAD)

    enum class Kind {
        TOUCHPAD,
        NAVIGATION,
        ROTATION,
        SPLIT,
    }

    init {
        orientation = VERTICAL
        tag = "guide-diagram-${kind.name.lowercase(java.util.Locale.ROOT)}"
        setPadding(style.dp(8f), style.dp(8f), style.dp(8f), style.dp(8f))
        background = style.surface(18, 10, 45)
        val descriptions =
            when (kind) {
                Kind.TOUCHPAD ->
                    listOf(
                        "One finger: move pointer",
                        "Tap: click",
                        "Two fingers: scroll",
                        "Two fingers: pinch to zoom",
                        "Three fingers right: Back",
                    )
                Kind.NAVIGATION ->
                    listOf("Either side edge: Back", "Bottom edge: Apps", "Top edge: Controls")
                Kind.ROTATION ->
                    listOf(
                        "0 degrees: landscape",
                        "90 degrees: portrait",
                        "180 degrees: flipped landscape",
                        "270 degrees: flipped portrait",
                    )
                Kind.SPLIT ->
                    listOf("Left / right panes", "Up / down panes", "Drag the divider to resize")
            }
        descriptions.forEachIndexed { index, description ->
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(Sketch(style, kind, index), LayoutParams(style.dp(72f), style.dp(44f)))
            row.addView(style.readingLabel(description), LayoutParams(0, -2, 1f))
            val layout = LayoutParams(-1, -2)
            layout.setMargins(0, style.dp(4f), 0, style.dp(4f))
            addView(row, layout)
        }
        layoutParams = LayoutParams(-1, -2).apply { setMargins(0, style.dp(4f), 0, style.dp(12f)) }
    }

    private class Sketch(val style: ControllerStyle, val kind: Kind, val index: Int) :
        View(style.context) {
        constructor(context: Context) : this(ControllerStyle(context), Kind.TOUCHPAD, 0)

        private val navigationBox = RectF(9f, 5f, 62f, 39f)
        private val splitBox = RectF(9f, 6f, 62f, 38f)
        private val rotationBox =
            if (index % 2 == 1) RectF(22f, 4f, 47f, 40f) else RectF(9f, 9f, 61f, 35f)
        private val pen =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                strokeWidth = 1.6f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        private fun arrow(c: Canvas, x: Float, y: Float, dx: Float, dy: Float) {
            c.drawLine(x, y, x + dx, y + dy, pen)
            val length = kotlin.math.sqrt(dx * dx + dy * dy)
            if (length == 0f) return
            val ux = dx / length
            val uy = dy / length
            c.drawLine(x + dx, y + dy, x + dx - ux * 5 + uy * 3, y + dy - uy * 5 - ux * 3, pen)
            c.drawLine(x + dx, y + dy, x + dx - ux * 5 - uy * 3, y + dy - uy * 5 + ux * 3, pen)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.save()
            canvas.scale(width / 72f, height / 44f)
            when (kind) {
                Kind.TOUCHPAD -> {
                    val count =
                        when (index) {
                            2,
                            3 -> 2
                            4 -> 3
                            else -> 1
                        }
                    repeat(count) { canvas.drawCircle(10f + it * 8, 22f, 2.4f, pen) }
                    when (index) {
                        1 -> canvas.drawCircle(10f, 22f, 7f, pen)
                        2 -> {
                            arrow(canvas, 46f, 23f, 0f, -13f)
                            arrow(canvas, 46f, 25f, 0f, 9f)
                        }
                        3 -> {
                            arrow(canvas, 49f, 22f, -10f, 0f)
                            arrow(canvas, 52f, 22f, 10f, 0f)
                        }
                        else -> arrow(canvas, 37f, 22f, 25f, 0f)
                    }
                }
                Kind.NAVIGATION -> {
                    canvas.drawRoundRect(navigationBox, 3f, 3f, pen)
                    when (index) {
                        0 -> {
                            arrow(canvas, 10f, 22f, 15f, 0f)
                            arrow(canvas, 61f, 22f, -15f, 0f)
                        }
                        1 -> arrow(canvas, 36f, 38f, 0f, -17f)
                        else -> arrow(canvas, 36f, 6f, 0f, 17f)
                    }
                }
                Kind.ROTATION -> {
                    canvas.drawRoundRect(rotationBox, 2f, 2f, pen)
                    if (index < 2) arrow(canvas, 35f, 29f, 0f, -14f)
                    else arrow(canvas, 35f, 15f, 0f, 14f)
                }
                Kind.SPLIT -> {
                    canvas.drawRoundRect(splitBox, 2f, 2f, pen)
                    if (index == 1) canvas.drawLine(9f, 22f, 62f, 22f, pen)
                    else canvas.drawLine(35f, 6f, 35f, 38f, pen)
                    if (index == 2) {
                        arrow(canvas, 30f, 22f, -12f, 0f)
                        arrow(canvas, 40f, 22f, 12f, 0f)
                    }
                }
            }
            canvas.restore()
        }
    }
}
