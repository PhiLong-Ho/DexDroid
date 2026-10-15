package dev.dexdroid;

import android.graphics.Insets;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;

/** Keep native phone controls clear of system bars, cutouts and the Android IME. */
final class ScreenInsets {
    static void apply(Window window, View content) {
        int left = content.getPaddingLeft(),
                top = content.getPaddingTop(),
                right = content.getPaddingRight(),
                bottom = content.getPaddingBottom();
        window.setDecorFitsSystemWindows(false);
        content.setOnApplyWindowInsetsListener(
                (view, insets) -> {
                    Insets bars =
                            insets.getInsets(
                                    WindowInsets.Type.systemBars()
                                            | WindowInsets.Type.displayCutout());
                    Insets keyboard = insets.getInsets(WindowInsets.Type.ime());
                    view.setPadding(
                            left + bars.left,
                            top + bars.top,
                            right + bars.right,
                            bottom + Math.max(bars.bottom, keyboard.bottom));
                    return insets;
                });
        content.post(content::requestApplyInsets);
    }
}
