package dev.dexdroid

import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.*

/** An uncluttered first-launch desktop; its parent supplies rotation and Fit. */
class IdleDesktop
@JvmOverloads
constructor(
    context: Context?,
    openApps: Runnable?,
    tag: String? = "monitor-idle",
    appTag: String? = "monitor-idle-apps",
) : FrameLayout(context!!) {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val style: ControllerStyle?
    private var backdrop: Shader? = null

    init {
        style = ControllerStyle(context)
        setTag(tag)
        setWillNotDraw(false)
        setClickable(true)
        setForceDarkAllowed(false)
        val apps = style!!.iconAction(R.drawable.ic_apps, "Apps", openApps)
        apps.setTag(appTag)
        apps.setScaleType(ImageView.ScaleType.CENTER_INSIDE)
        apps.setPadding(style!!.dp(22f), style!!.dp(22f), style!!.dp(22f), style!!.dp(22f))
        apps.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE))
        apps.setBackground(style!!.ripple(style!!.surface(18, 24, 45)))
        addView(apps, FrameLayout.LayoutParams(style!!.dp(76f), style!!.dp(76f), Gravity.CENTER))
    }

    protected override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        backdrop =
            LinearGradient(
                0f,
                0f,
                w.toFloat(),
                h.toFloat(),
                intArrayOf(Color.rgb(7, 7, 7), Color.rgb(19, 19, 19)),
                null,
                Shader.TileMode.CLAMP,
            )
    }

    protected override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.setShader(backdrop)
        paint.setStyle(Paint.Style.FILL)
        canvas.drawRect(0f, 0f, getWidth().toFloat(), getHeight().toFloat(), paint)
        paint.setShader(null)
        // Sparse architectural lines keep the first-launch surface calm and monochrome.
        paint.setColor(Color.rgb(28, 28, 28))
        paint.setStrokeWidth(style!!.dp(1f).toFloat())
        val inset = style!!.dp(32f).toFloat()
        val line = Math.min(getWidth(), getHeight()) * .16f
        canvas.drawLine(inset, inset, inset + line, inset, paint)
        canvas.drawLine(inset, inset, inset, inset + line, paint)
        canvas.drawLine(
            getWidth() - inset,
            getHeight() - inset,
            getWidth().toFloat() - inset - line,
            getHeight() - inset,
            paint,
        )
        canvas.drawLine(
            getWidth() - inset,
            getHeight() - inset,
            getWidth() - inset,
            getHeight().toFloat() - inset - line,
            paint,
        )
        paint.setColor(Color.rgb(96, 96, 96))
        paint.setTextSize(style!!.dp(12f).toFloat())
        paint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL))
        paint.setTextAlign(Paint.Align.CENTER)
        val baseline = (getHeight() - style!!.dp(30f)).toFloat()
        if (
            (baseline + paint.ascent() >= getHeight() / 2f + style!!.dp(46f) &&
                paint.measureText("DexDroid") <= getWidth() - style!!.dp(32f))
        )
            canvas.drawText("DexDroid", getWidth() / 2f, baseline, paint)
    }
}
