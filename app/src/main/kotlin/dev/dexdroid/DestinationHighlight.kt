package dev.dexdroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Temporary app-choice destination, behind menus and with no input ownership. */
class DestinationHighlight(context: Context?, private val session: SessionService?) :
    View(context) {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    constructor(context: Context?) : this(context, null) {}

    init {
        setTag("app-destination-highlight")
        setForceDarkAllowed(false)
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
    }

    protected override fun onDraw(canvas: Canvas) {
        val box = if (session == null) null else session!!.appSelectionBox()
        if (box == null || box!!.width <= 0 || box!!.height <= 0) return
        paint.setStyle(Paint.Style.FILL)
        paint.setColor(Color.argb(14, 255, 255, 255))
        canvas.drawRect(
            box!!.x.toFloat(),
            box!!.y.toFloat(),
            (box!!.x + box!!.width).toFloat(),
            (box!!.y + box!!.height).toFloat(),
            paint,
        )
        val stroke = Math.max(1f, session!!.dpi / 160f)
        paint.setStyle(Paint.Style.STROKE)
        paint.setStrokeWidth(stroke)
        paint.setColor(Color.rgb(96, 96, 96))
        canvas.drawRect(
            box!!.x + stroke / 2,
            box!!.y + stroke / 2,
            box!!.x + box!!.width - stroke / 2,
            box!!.y + box!!.height - stroke / 2,
            paint,
        )
    }
}
