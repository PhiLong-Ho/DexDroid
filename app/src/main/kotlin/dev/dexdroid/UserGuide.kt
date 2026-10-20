package dev.dexdroid

import android.widget.LinearLayout

/** One concise offline guide for phone and monitor Settings. */
internal class UserGuide {
    companion object {
        private val SECTIONS: Array<Array<String?>?>? =
            arrayOf<Array<String?>?>(
                arrayOf<String?>(
                    "Start the desktop",
                    "Connect a video-capable monitor. Start Shizuku on the phone and authorize DexDroid. The desktop connects automatically. After reboot, start Shizuku again using wireless debugging or computer ADB.",
                ),
                arrayOf<String?>(
                    "Apps and split view",
                    "Apps opens the launcher. Tap Left/right or Up/down to enable and show both panes immediately, including from fullscreen. Tap Single to keep pane 1 and end split; use an app's spatial button to choose its pane. Hold an app in Single for one-time orientation. Layout offers ratio, swap and fullscreen/restore; drag the divider to resize. Layout choices are remembered.",
                ),
                arrayOf<String?>(
                    "Phone touchpad",
                    "Slide one finger to point; tap to click. Two fingers scroll or pinch to zoom. Swipe three fingers right for Back. The pad header has Scroll, remote Back and Info. Physical monitor touch hides the pointer; pad input shows it.",
                ),
                arrayOf<String?>(
                    "Monitor and keyboard",
                    "Swipe inward from either side for Back, up from the bottom for Apps, or down from the top for Controls. Start just inside system bars. Apps search uses a local keyboard on the monitor. General Android keyboard mirroring is unavailable; hardware keys go to the focused pane.",
                ),
                arrayOf<String?>(
                    "Rotation and scrolling",
                    "Rotate chooses 0, 90, 180 or 270 degrees independently of the phone. Settings > App settings contains Save/Reset. Scroll chooses swipe or wheel and speed; swipe is often better for paged readers. Settings also has pointer speed and reverse scrolling.",
                ),
                arrayOf<String?>(
                    "Display and profiles",
                    "Settings adjusts desktop resolution/UI DPI, not necessarily the monitor signal mode. Fit preserves proportions and adds bars where needed. Keep a preview within 15 seconds or it reverts. Monitor profiles save setups and remember matching monitors; identical reported identities may share a binding.",
                ),
                arrayOf<String?>(
                    "Background and status",
                    "Tap monitor status to show/hide details; Show details / Hide details shares its tap area. The plain Start/Stop icon is separate. Stop ends the desktop and returns to the existing phone controller after cleanup. Background operation keeps the monitor running while the phone controller is away. Save/Reset and current settings follow the selected app or split pane.",
                ),
                arrayOf<String?>(
                    "Audio output",
                    "Audio chooses a connected media output for this desktop session. It changes the phone-wide default media route, including both panes. System default releases DexDroid's override. Calls, microphone input, casting and output choices inside apps may differ. Android media route is Android's anticipated route, not proof of audible sound. Stop or monitor disconnect attempts to restore the previous preferred policy; a helper crash or lost privilege can prevent confirmation. Check Android sound settings or restart the phone if cleanup is uncertain. Audio control is unavailable on devices that do not grant shell audio-routing access.",
                ),
                arrayOf<String?>(
                    "Shizuku and banking apps",
                    "Pairing and DexDroid authorization can be remembered, but non-root Shizuku needs authorized ADB startup again after reboot. A smaller helper still needs ADB, root or manufacturer privileges; a one-time ordinary app permission cannot grant shell access permanently. Computer ADB is an alternative startup path, not a promise that banking apps will accept the device. Shizuku warns that disabling USB debugging or Developer options can stop its service. DexDroid does not change those settings or bypass banking checks. Use your bank's supported requirements; stop the desktop before changing debugging settings.",
                ),
                arrayOf<String?>(
                    "Menus and troubleshooting",
                    "Up returns to the previous menu; Back hides the keyboard first. X/outside closes all menus. Settings has the installed version and Diagnostic log. Protected apps can blank output; Protected screen help explains Chrome incognito and its optional flag. App resizing, DRM and monitor touch depend on Android/OEM support.",
                ),
            )

        @JvmStatic
        fun add(style: ControllerStyle?, body: LinearLayout?) {
            for (i in SECTIONS!!.indices) {
                val section = style!!.label(SECTIONS!![i]!![0] + "\n\n" + SECTIONS!![i]!![1], 14)
                section.setTag("guide-section-" + i)
                section.setPadding(
                    style!!.dp(12f),
                    style!!.dp(12f),
                    style!!.dp(12f),
                    style!!.dp(12f),
                )
                section.setBackground(style!!.surface(18, 8, 40))
                val layout = LinearLayout.LayoutParams(-1, -2)
                layout.setMargins(0, style!!.dp(6f), 0, style!!.dp(6f))
                body!!.addView(section, layout)
            }
        }
    }
}
