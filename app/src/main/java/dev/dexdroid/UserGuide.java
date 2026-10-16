package dev.dexdroid;

import android.widget.LinearLayout;
import android.widget.TextView;

/** One concise offline guide for phone and monitor Settings. */
final class UserGuide {
    private static final String[][] SECTIONS = {
        {
            "Start the desktop",
            "Connect a video-capable monitor. Start Shizuku on the phone and authorize DexDroid. The desktop connects automatically. After reboot, start Shizuku again using wireless debugging or computer ADB."
        },
        {
            "Apps and split view",
            "Apps opens the launcher. Choose Single, Left/right or Up/down; use an app's spatial button to choose its pane. Hold an app in Single for one-time orientation. Layout offers ratio, swap and fullscreen/restore; drag the divider to resize. Layout choices are remembered."
        },
        {
            "Phone touchpad",
            "Slide one finger to point; tap to click. Two fingers scroll or pinch to zoom. Swipe three fingers right for Back. The pad header has Scroll, remote Back and Info. Physical monitor touch hides the pointer; pad input shows it."
        },
        {
            "Monitor and keyboard",
            "Swipe inward from either side for Back, up from the bottom for Apps, or down from the top for Controls. Start just inside system bars. Apps search uses a local keyboard on the monitor. General Android keyboard mirroring is unavailable; hardware keys go to the focused pane."
        },
        {
            "Rotation and scrolling",
            "Rotate chooses 0, 90, 180 or 270 degrees independently of the phone. Settings > App settings contains Save/Reset. Scroll chooses swipe or wheel and speed; swipe is often better for paged readers. Settings also has pointer speed and reverse scrolling."
        },
        {
            "Display and profiles",
            "Settings adjusts desktop resolution/UI DPI, not necessarily the monitor signal mode. Fit preserves proportions and adds bars where needed. Keep a preview within 15 seconds or it reverts. Monitor profiles save setups and remember matching monitors; identical reported identities may share a binding."
        },
        {
            "Background and status",
            "Tap monitor status to show/hide details; the small bottom chevron shares its tap area. Start/Stop is separate and Stop ends the desktop. Background operation keeps the monitor running while the phone controller is away. Save/Reset and current settings follow the selected app or split pane."
        },
        {
            "Menus and troubleshooting",
            "Up returns to the previous menu; Back hides the keyboard first. X/outside closes all menus. Settings has the installed version and Diagnostic log. Protected apps can blank output; Protected screen help explains Chrome incognito and its optional flag. App resizing, DRM and monitor touch depend on Android/OEM support."
        }
    };

    static void add(ControllerStyle style, LinearLayout body) {
        for (int i = 0; i < SECTIONS.length; i++) {
            TextView section = style.label(SECTIONS[i][0] + "\n\n" + SECTIONS[i][1], 14);
            section.setTag("guide-section-" + i);
            section.setPadding(style.dp(12), style.dp(12), style.dp(12), style.dp(12));
            section.setBackground(style.surface(18, 8, 40));
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
            layout.setMargins(0, style.dp(6), 0, style.dp(6));
            body.addView(section, layout);
        }
    }
}
