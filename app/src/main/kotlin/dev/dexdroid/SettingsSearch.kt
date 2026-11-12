package dev.dexdroid

import java.util.Locale

/** Static discovery metadata only; never reads live values or executes a setting. */
internal object SettingsSearch {
    data class Entry(val id: String, val title: String, val section: String, val aliases: String)

    val entries =
        listOf(
            Entry(
                "audio",
                "Audio output",
                "Audio",
                "sound speaker earbud bluetooth hdmi music media",
            ),
            Entry(
                "app",
                "App settings",
                "Apps",
                "scroll speed swipe wheel sensitivity save reset remember rotation",
            ),
            Entry(
                "workspace",
                "Workspace shortcut",
                "Apps",
                "save restore launch apps layout home",
            ),
            Entry("retry", "Retry connection", "Connection", "shizuku unavailable reconnect"),
            Entry(
                "shizuku",
                "Shizuku setup",
                "Connection",
                "pair pairing permissions install start adb debugging wireless",
            ),
            Entry(
                "developer",
                "Turn off Developer Mode",
                "Connection",
                "developer options dev debugging usb wireless adb bank banking hsbc paylah shutdown",
            ),
            Entry(
                "guide",
                "User guide",
                "Help",
                "help instructions manual touchpad gestures pinch zoom",
            ),
            Entry(
                "profiles",
                "Monitor profiles",
                "Display",
                "screen saved configuration resolution dpi rotation",
            ),
            Entry(
                "display",
                "Resolution and DPI",
                "Display",
                "screen density scale size aspect ratio fit output video",
            ),
            Entry("touch", "Monitor touch", "Display", "touchscreen external direct input"),
            Entry(
                "navigation",
                "Monitor edge navigation",
                "Display",
                "gesture back menu apps controls top bottom swipe",
            ),
            Entry(
                "keyboard",
                "Android keyboard on monitor",
                "Input",
                "native ime typing text input retry placement",
            ),
            Entry("pointer", "Pointer speed", "Input", "mouse touchpad sensitivity cursor"),
            Entry("scroll", "Scroll direction", "Input", "reverse reversed invert wheel"),
            Entry("center", "Center pointer", "Input", "mouse cursor recenter"),
            Entry(
                "capabilities",
                "Device capabilities",
                "General",
                "readiness supported compatibility diagnostics report",
            ),
            Entry(
                "background",
                "Background operation",
                "General",
                "battery power energy exemption foreground",
            ),
            Entry(
                "diagnostics",
                "Diagnostic log",
                "General",
                "debug logs events history copy share gesture report",
            ),
            Entry(
                "protected",
                "Protected screen help",
                "General",
                "blank black drm secure chrome incognito screenshot",
            ),
            Entry("reset", "Reset monitor profile", "Reset", "default clear forget configuration"),
        )

    private fun words(value: String) =
        value.lowercase(Locale.ROOT).trim().split(Regex("\\s+")).filter(String::isNotEmpty)

    fun matches(id: String, query: String): Boolean {
        val entry = entries.first { it.id == id }
        val text = "${entry.title} ${entry.section} ${entry.aliases}".lowercase(Locale.ROOT)
        return words(query).all(text::contains)
    }

    fun ids(query: String): List<String> = entries.filter { matches(it.id, query) }.map { it.id }
}
