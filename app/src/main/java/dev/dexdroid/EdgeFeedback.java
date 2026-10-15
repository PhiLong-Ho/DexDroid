package dev.dexdroid;

import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import java.util.function.IntUnaryOperator;

/** Transient, non-interactive Android-style navigation affordance in desktop pixels. */
final class EdgeFeedback extends View {
    private final IntUnaryOperator dp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String kind = "";
    private float x, y, progress, opacity = 1, pulse;
    private ValueAnimator animator;

    EdgeFeedback(Context context, IntUnaryOperator dp) {
        super(context);
        this.dp = dp;
        setTag("monitor-edge-feedback");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(GONE);
    }

    void start(String value, float x, float y) {
        clear();
        kind = value;
        this.x = x;
        this.y = y;
        progress = 0;
        opacity = 1;
        setVisibility(VISIBLE);
        invalidate();
    }

    void progress(float value) {
        progress = Geometry.clamp(value, 0, 1);
        invalidate();
    }

    float progress() {
        return progress;
    }

    void finish(boolean accepted) {
        if (kind.isEmpty()) return;
        if (animator != null) animator.cancel();
        if (accepted) progress = 1;
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(accepted ? 190 : 140);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(
                a -> {
                    float t = (float) a.getAnimatedValue();
                    opacity = 1 - t;
                    pulse = accepted ? (float) Math.sin(t * Math.PI) * .12f : 0;
                    invalidate();
                });
        animator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator a) {
                        if (a == animator) {
                            kind = "";
                            setVisibility(GONE);
                            animator = null;
                        }
                    }
                });
        animator.start();
    }

    void clear() {
        ValueAnimator old = animator;
        animator = null;
        if (old != null) old.cancel();
        kind = "";
        pulse = 0;
        setVisibility(GONE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (kind.isEmpty()) return;
        boolean vertical = kind.equals("Home") || kind.equals("Controls");
        float cx =
                vertical
                        ? Geometry.clamp(x, dp.applyAsInt(42), getWidth() - dp.applyAsInt(42))
                        : kind.equals("Right Back")
                                ? getWidth() - dp.applyAsInt(16) - progress * dp.applyAsInt(28)
                                : dp.applyAsInt(16) + progress * dp.applyAsInt(28);
        float cy =
                vertical
                        ? (kind.equals("Home")
                                ? getHeight() - dp.applyAsInt(16) - progress * dp.applyAsInt(24)
                                : dp.applyAsInt(16) + progress * dp.applyAsInt(24))
                        : Geometry.clamp(y, dp.applyAsInt(42), getHeight() - dp.applyAsInt(42));
        canvas.save();
        canvas.scale(1 + pulse, 1 + pulse, cx, cy);
        float halfW = dp.applyAsInt(vertical ? 28 : 18), halfH = dp.applyAsInt(vertical ? 18 : 28);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(Math.round(220 * opacity), 24, 24, 24));
        canvas.drawRoundRect(
                cx - halfW,
                cy - halfH,
                cx + halfW,
                cy + halfH,
                dp.applyAsInt(22),
                dp.applyAsInt(22),
                paint);
        paint.setColor(Color.argb(Math.round(255 * opacity), 255, 255, 255));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp.applyAsInt(2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        Path path = new Path();
        float size = dp.applyAsInt(5);
        if (vertical) {
            float direction = kind.equals("Home") ? -1 : 1;
            path.moveTo(cx - size, cy - direction * size / 2);
            path.lineTo(cx, cy + direction * size / 2);
            path.lineTo(cx + size, cy - direction * size / 2);
        } else {
            path.moveTo(cx + size / 2, cy - size);
            path.lineTo(cx - size / 2, cy);
            path.lineTo(cx + size / 2, cy + size);
        }
        canvas.drawPath(path, paint);
        canvas.restore();
    }
}
