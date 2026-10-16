package dev.dexdroid;

import android.view.View;

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
        return "PASS "
                + prefix
                + " compact status height "
                + card.getHeight() / density
                + "dp; centered decorative chevron and accessible shared target\n";
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
