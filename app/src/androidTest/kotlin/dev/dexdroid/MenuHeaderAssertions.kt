package dev.dexdroid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView

/** Observable header geometry and resting pixels, shared by both real menu suites. */
internal class MenuHeaderAssertions {
    companion object {
        @JvmStatic
        fun inspect(root: View, prefix: String): String {
            val titleTag = if (prefix == "phone") "phone-menu-title" else "monitor-title"
            val actionPrefix = if (prefix == "phone") "phone-menu-" else "monitor-"
            val title = root.findViewWithTag<TextView?>(titleTag)
            require(title != null && title!!.isShown(), "menu title is visible")
            val d = title!!.getResources()!!.getDisplayMetrics()!!.density
            require(
                (title!!.getWidth() > 0 &&
                    title!!.getHeight() >= 53 * d &&
                    title!!.getPaddingLeft() >= 7.5f * d &&
                    title!!.getPaddingRight() >= 7.5f * d &&
                    title!!.getMaxLines() == 1 &&
                    title!!.getEllipsize() == android.text.TextUtils.TruncateAt.END),
                "title reserves clearance and truncates without taking icon space",
            )
            val header = title!!.getParent() as ViewGroup?
            var actions = 0
            for (i in 0 until header!!.getChildCount()) {
                val child = header!!.getChildAt(i)
                if (!(child is ImageButton)) continue
                val button = child as ImageButton?
                actions++
                require(
                    (button!!.isShown() &&
                        button!!.isClickable() &&
                        button!!.isFocusable() &&
                        button!!.getWidth() >= 47.5f * d &&
                        button!!.getHeight() >= 47.5f * d),
                    "plain icon keeps an independent accessible 48dp target",
                )
                require(
                    (button!!.getContentDescription() != null &&
                        button!!.getContentDescription()!!.length > 0 &&
                        button!!.getTooltipText() != null),
                    "icon retains its action name and tooltip",
                )
                require(
                    button!!.getRight() <= title!!.getLeft() ||
                        button!!.getLeft() >= title!!.getRight(),
                    "title and action hit regions do not overlap",
                )
                require(button!!.getDrawable() != null, "header has a real drawable icon")
                require(
                    ((button!!.getWidth() -
                        button!!.getPaddingLeft() -
                        button!!.getPaddingRight() <= 24.5f * d) &&
                        ((button!!.getHeight() -
                            button!!.getPaddingTop() -
                            button!!.getPaddingBottom()) <= 24.5f * d)),
                    "glyph fits within a centered 24dp region",
                )
                require(
                    (button!!.getImageTintList() != null &&
                        (button!!
                            .getImageTintList()!!
                            .getColorForState(intArrayOf(android.R.attr.state_enabled), 0) ==
                            Color.WHITE) &&
                        (button!!.getImageTintList()!!.getColorForState(intArrayOf(), 0) !=
                            Color.WHITE)),
                    "white enabled icon has a distinguishable disabled state",
                )
                val background = button!!.getBackground()
                require(background is RippleDrawable, "icon retains native interaction feedback")
                require(
                    (background as RippleDrawable).findDrawableByLayerId(android.R.id.mask) != null,
                    "interaction feedback has an explicit bounded mask",
                )
                val state = background!!.getState().clone()
                val pixels =
                    Bitmap.createBitmap(
                        button!!.getWidth(),
                        button!!.getHeight(),
                        Bitmap.Config.ARGB_8888,
                    )
                try {
                    background!!.setState(intArrayOf(android.R.attr.state_enabled))
                    background!!.jumpToCurrentState()
                    background!!.draw(Canvas(pixels))
                    for (y in 0 until pixels.getHeight()) for (x in
                        0 until pixels.getWidth()) require(
                        Color.alpha(pixels.getPixel(x, y)) == 0,
                        "resting header has no fill, disk or border",
                    )
                } finally {
                    background!!.setState(state!!)
                    pixels.recycle()
                }
            }
            require(
                actions >= 1 && root.findViewWithTag<View?>(actionPrefix + "close") != null,
                "header retains Close and available navigation actions",
            )
            return ("PASS " +
                prefix +
                " plain header pixels, icon targets, labels and title clearance\n")
        }

        private fun require(ok: Boolean, message: String?) {
            if (!ok) throw AssertionError(message)
        }
    }
}
