package dev.dexdroid

import android.animation.*
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.function.IntUnaryOperator

/** Transient, non-interactive Android-style navigation affordance in desktop pixels. */
class EdgeFeedback(context: Context?, private val dp: IntUnaryOperator?) : View(context) {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var kind: String? = ""
    private var x: Float = 0.toFloat()
    private var y: Float = 0.toFloat()
    private var progress: Float = 0.toFloat()
    private var opacity: Float = 1f
    private var pulse: Float = 0.toFloat()
    private var animator: ValueAnimator? = null

    init {
        setTag("monitor-edge-feedback")
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        setVisibility(View.GONE)
    }

    fun start(value: String?, x: Float, y: Float) {
        clear()
        kind = value
        this.x = x
        this.y = y
        progress = 0f
        opacity = 1f
        setVisibility(View.VISIBLE)
        invalidate()
    }

    fun progress(value: Float) {
        progress = Geometry.clamp(value, 0f, 1f)
        invalidate()
    }

    fun progress(): Float {
        return progress
    }

    fun finish(accepted: Boolean) {
        if (kind!!.isEmpty()) return
        if (animator != null) animator!!.cancel()
        if (accepted) progress = 1f
        animator = ValueAnimator.ofFloat(0f, 1f)
        animator!!.setDuration((if (accepted) 190 else 140).toLong())
        animator!!.setInterpolator(DecelerateInterpolator())
        animator!!.addUpdateListener({ a ->
            val t = a!!.getAnimatedValue() as Float
            opacity = 1 - t
            pulse = if (accepted) Math.sin(t * Math.PI).toFloat() * .12f else 0f
            invalidate()
        })
        animator!!.addListener(
            object : AnimatorListenerAdapter() {
                public override fun onAnimationEnd(a: Animator) {
                    if (a === animator) {
                        kind = ""
                        setVisibility(View.GONE)
                        animator = null
                    }
                }
            }
        )
        animator!!.start()
    }

    fun clear() {
        val old = animator
        animator = null
        if (old != null) old!!.cancel()
        kind = ""
        pulse = 0f
        setVisibility(View.GONE)
        invalidate()
    }

    protected override fun onDraw(canvas: Canvas) {
        if (kind!!.isEmpty()) return
        val vertical = kind == "Home" || kind == "Controls"
        val cx =
            if (vertical)
                Geometry.clamp(
                    x,
                    dp!!.applyAsInt(42).toFloat(),
                    (getWidth() - dp!!.applyAsInt(42)).toFloat(),
                )
            else if (kind == "Right Back")
                getWidth().toFloat() -
                    dp!!.applyAsInt(16).toFloat() -
                    progress * dp!!.applyAsInt(28)
            else dp!!.applyAsInt(16) + progress * dp!!.applyAsInt(28)
        val cy =
            if (vertical)
                (if (kind == "Home")
                    getHeight().toFloat() -
                        dp!!.applyAsInt(16).toFloat() -
                        progress * dp!!.applyAsInt(24)
                else dp!!.applyAsInt(16) + progress * dp!!.applyAsInt(24))
            else
                Geometry.clamp(
                    y,
                    dp!!.applyAsInt(42).toFloat(),
                    (getHeight() - dp!!.applyAsInt(42)).toFloat(),
                )
        canvas.save()
        canvas.scale(1 + pulse, 1 + pulse, cx, cy)
        val halfW = dp!!.applyAsInt(if (vertical) 28 else 18).toFloat()
        val halfH = dp!!.applyAsInt(if (vertical) 18 else 28).toFloat()
        paint.setStyle(Paint.Style.FILL)
        paint.setColor(Color.argb(Math.round(220 * opacity), 24, 24, 24))
        canvas.drawRoundRect(
            cx - halfW,
            cy - halfH,
            cx + halfW,
            cy + halfH,
            dp!!.applyAsInt(22).toFloat(),
            dp!!.applyAsInt(22).toFloat(),
            paint,
        )
        paint.setColor(Color.argb(Math.round(255 * opacity), 255, 255, 255))
        paint.setStyle(Paint.Style.STROKE)
        paint.setStrokeWidth(dp!!.applyAsInt(2).toFloat())
        paint.setStrokeCap(Paint.Cap.ROUND)
        val path = Path()
        val size = dp!!.applyAsInt(5).toFloat()
        if (vertical) {
            val direction = (if (kind == "Home") -1 else 1).toFloat()
            path.moveTo(cx - size, cy - direction * size / 2)
            path.lineTo(cx, cy + direction * size / 2)
            path.lineTo(cx + size, cy - direction * size / 2)
        } else {
            path.moveTo(cx + size / 2, cy - size)
            path.lineTo(cx - size / 2, cy)
            path.lineTo(cx + size / 2, cy + size)
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}
