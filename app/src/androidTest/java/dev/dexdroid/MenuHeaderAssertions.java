package dev.dexdroid;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

/** Observable header geometry and resting pixels, shared by both real menu suites. */
final class MenuHeaderAssertions {
    static String inspect(View root, String prefix) {
        String titleTag = prefix.equals("phone") ? "phone-menu-title" : "monitor-title";
        String actionPrefix = prefix.equals("phone") ? "phone-menu-" : "monitor-";
        TextView title = root.findViewWithTag(titleTag);
        require(title != null && title.isShown(), "menu title is visible");
        float d = title.getResources().getDisplayMetrics().density;
        require(
                title.getWidth() > 0
                        && title.getHeight() >= 53 * d
                        && title.getPaddingLeft() >= 7.5f * d
                        && title.getPaddingRight() >= 7.5f * d
                        && title.getMaxLines() == 1
                        && title.getEllipsize() == android.text.TextUtils.TruncateAt.END,
                "title reserves clearance and truncates without taking icon space");
        ViewGroup header = (ViewGroup) title.getParent();
        int actions = 0;
        for (int i = 0; i < header.getChildCount(); i++) {
            View child = header.getChildAt(i);
            if (!(child instanceof ImageButton)) continue;
            ImageButton button = (ImageButton) child;
            actions++;
            require(
                    button.isShown()
                            && button.isClickable()
                            && button.isFocusable()
                            && button.getWidth() >= 47.5f * d
                            && button.getHeight() >= 47.5f * d,
                    "plain icon keeps an independent accessible 48dp target");
            require(
                    button.getContentDescription() != null
                            && button.getContentDescription().length() > 0
                            && button.getTooltipText() != null,
                    "icon retains its action name and tooltip");
            require(
                    button.getRight() <= title.getLeft() || button.getLeft() >= title.getRight(),
                    "title and action hit regions do not overlap");
            require(button.getDrawable() != null, "header has a real drawable icon");
            require(
                    button.getWidth() - button.getPaddingLeft() - button.getPaddingRight()
                                    <= 24.5f * d
                            && button.getHeight()
                                            - button.getPaddingTop()
                                            - button.getPaddingBottom()
                                    <= 24.5f * d,
                    "glyph fits within a centered 24dp region");
            require(
                    button.getImageTintList() != null
                            && button.getImageTintList()
                                            .getColorForState(
                                                    new int[] {android.R.attr.state_enabled}, 0)
                                    == Color.WHITE
                            && button.getImageTintList().getColorForState(new int[] {}, 0)
                                    != Color.WHITE,
                    "white enabled icon has a distinguishable disabled state");
            Drawable background = button.getBackground();
            require(
                    background instanceof RippleDrawable,
                    "icon retains native interaction feedback");
            require(
                    ((RippleDrawable) background).findDrawableByLayerId(android.R.id.mask) != null,
                    "interaction feedback has an explicit bounded mask");
            int[] state = background.getState().clone();
            Bitmap pixels =
                    Bitmap.createBitmap(
                            button.getWidth(), button.getHeight(), Bitmap.Config.ARGB_8888);
            try {
                background.setState(new int[] {android.R.attr.state_enabled});
                background.jumpToCurrentState();
                background.draw(new Canvas(pixels));
                for (int y = 0; y < pixels.getHeight(); y++)
                    for (int x = 0; x < pixels.getWidth(); x++)
                        require(
                                Color.alpha(pixels.getPixel(x, y)) == 0,
                                "resting header has no fill, disk or border");
            } finally {
                background.setState(state);
                pixels.recycle();
            }
        }
        require(
                actions >= 1 && root.findViewWithTag(actionPrefix + "close") != null,
                "header retains Close and available navigation actions");
        return "PASS "
                + prefix
                + " plain header pixels, icon targets, labels and title clearance\n";
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
