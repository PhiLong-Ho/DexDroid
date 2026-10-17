package dev.dexdroid;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

/** Observable collapsed disclosure geometry shared by phone and rotated monitor checks. */
final class StatusDisclosureAssertions {
    static String inspect(View root, String prefix) {
        View card = root.findViewWithTag("controller-status-card");
        View target = root.findViewWithTag(prefix + "-status-expand");
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
        inspectCue(root, prefix, false);
        inspectDesktop(root, prefix, "Stop desktop");
        return "PASS "
                + prefix
                + " compact status height "
                + card.getHeight() / density
                + "dp; text disclosure cue and accessible shared target\n";
    }

    static void inspectCue(View root, String prefix, boolean expanded) {
        TextView cue = root.findViewWithTag(prefix + "-status-cue");
        View action = root.findViewWithTag(prefix + "-stop");
        View target = root.findViewWithTag(prefix + "-status-expand");
        require(cue != null && cue.isShown(), "status text cue is visible");
        require(
                (expanded ? "Hide details" : "Show details").contentEquals(cue.getText()),
                "cue describes the available expansion action");
        require(
                !cue.isClickable()
                        && !cue.isFocusable()
                        && cue.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        && cue.getBackground() == null,
                "cue belongs to the existing disclosure target without a duplicate button");
        require(
                root.findViewWithTag(prefix + "-status-chevron") == null,
                "status has no competing chevron");
        android.view.ViewGroup card = root.findViewWithTag("controller-status-card");
        android.graphics.Rect cueBounds =
                new android.graphics.Rect(0, 0, cue.getWidth(), cue.getHeight());
        android.graphics.Rect buttonBounds =
                new android.graphics.Rect(0, 0, action.getWidth(), action.getHeight());
        android.graphics.Rect bodyBounds =
                new android.graphics.Rect(0, 0, target.getWidth(), target.getHeight());
        // Compare in the card's coordinate space: screen axes rotate with the monitor host.
        card.offsetDescendantRectToMyCoords(cue, cueBounds);
        card.offsetDescendantRectToMyCoords(action, buttonBounds);
        card.offsetDescendantRectToMyCoords(target, bodyBounds);
        require(
                bodyBounds.contains(cueBounds) && cueBounds.right <= buttonBounds.left,
                "cue stays in summary area clear of independent desktop action");
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
