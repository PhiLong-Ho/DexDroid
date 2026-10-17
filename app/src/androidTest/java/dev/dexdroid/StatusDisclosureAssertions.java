package dev.dexdroid;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageButton;

/** Observable collapsed disclosure geometry shared by phone and rotated monitor checks. */
final class StatusDisclosureAssertions {
    static String inspect(View root, String prefix) {
        View card = root.findViewWithTag("controller-status-card");
        View target = root.findViewWithTag(prefix + "-status-expand");
        View chevron = root.findViewWithTag(prefix + "-status-chevron");
        float density = card.getResources().getDisplayMetrics().density;
        require(card.getHeight() / density <= 80.5f, "normal collapsed status fits within 80dp");
        require(
                target.isClickable()
                        && target.isFocusable()
                        && target.getWidth() >= 48 * density
                        && target.getHeight() >= 48 * density,
                "status body is a keyboard-accessible 48dp disclosure target");
        require(
                "Collapsed".contentEquals(target.getStateDescription())
                        && target.getContentDescription().toString().startsWith("Expand status"),
                "collapsed disclosure has accessible action and state");
        require(
                !chevron.isClickable()
                        && !chevron.isFocusable()
                        && chevron.getImportantForAccessibility()
                                == View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        && chevron.getBackground() == null
                        && chevron.getWidth() <= 24 * density
                        && chevron.getHeight() <= 16 * density,
                "small chevron has no circle or duplicate accessibility target");
        require(
                Math.abs(chevron.getLeft() + chevron.getWidth() / 2f - card.getWidth() / 2f)
                                <= density
                        && card.getHeight() - chevron.getBottom() <= 6 * density
                        && chevron.getLeft() >= target.getLeft()
                        && chevron.getRight() <= target.getRight()
                        && chevron.getTop() >= target.getTop()
                        && chevron.getBottom() <= target.getBottom(),
                "bottom-centered chevron lies inside the same status tap area");
        inspectDesktop(root, prefix, "Stop desktop");
        return "PASS "
                + prefix
                + " compact status height "
                + card.getHeight() / density
                + "dp; centered decorative chevron and accessible shared target\n";
    }

    static void inspectDesktop(View root, String prefix, String action) {
        ImageButton button = root.findViewWithTag(prefix + "-stop");
        float density = root.getResources().getDisplayMetrics().density;
        require(
                button.isShown() && button.isClickable() && button.isEnabled(),
                "desktop action is visible and independently operable");
        require(
                button.getWidth() >= 48 * density && button.getHeight() >= 48 * density,
                "plain desktop action retains 48dp target");
        require(
                action.contentEquals(button.getContentDescription()),
                "plain desktop action has current accessible name");
        require(
                button.getImageTintList().getDefaultColor() == Color.WHITE,
                "desktop action remains white in both states");
        button.getBackground().jumpToCurrentState();
        Bitmap resting =
                Bitmap.createBitmap(button.getWidth(), button.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            button.getBackground().draw(new Canvas(resting));
            for (int y = 0; y < resting.getHeight(); y++)
                for (int x = 0; x < resting.getWidth(); x++)
                    require(
                            Color.alpha(resting.getPixel(x, y)) == 0,
                            "desktop action has no resting circle, fill or border");
        } finally {
            resting.recycle();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
