package dev.dexdroid

import android.widget.LinearLayout

/** One static offline guide; both surfaces render the same ordered content and passive sketches. */
internal class UserGuide {
    private sealed interface Block {
        data class Paragraph(val text: String) : Block

        data class Heading(val text: String) : Block

        data class Items(val text: List<String>, val numbered: Boolean = false) : Block

        data class Diagram(val kind: GuideDiagram.Kind) : Block
    }

    private data class Topic(val title: String, val icon: Int, val blocks: List<Block>)

    companion object {
        private fun paragraph(text: String) = Block.Paragraph(text)

        private fun heading(text: String) = Block.Heading(text)

        private fun bullets(vararg text: String) = Block.Items(text.toList())

        private fun steps(vararg text: String) = Block.Items(text.toList(), true)

        private fun diagram(kind: GuideDiagram.Kind) = Block.Diagram(kind)

        private val topics =
            listOf(
                Topic(
                    "Start the desktop",
                    R.drawable.ic_play,
                    listOf(
                        steps(
                            "Connect a video-capable monitor to the phone.",
                            "Start Shizuku on the phone and authorize DexDroid.",
                            "Open DexDroid. The desktop connects automatically when its dependencies are ready.",
                        ),
                        heading("After a reboot"),
                        paragraph(
                            "Start Shizuku again using wireless debugging or computer ADB. Pairing and app authorization may be remembered; startup still needs to be repeated."
                        ),
                    ),
                ),
                Topic(
                    "Apps and split view",
                    R.drawable.ic_layout,
                    listOf(
                        steps(
                            "Open Apps to choose an app. While it opens, status and a noninteractive monitor notice show progress.",
                            "Choose Left-right or Up-down to enable both panes immediately, including from fullscreen. Use an app's spatial launch button to choose its pane.",
                            "Choose Single to keep pane 1 and end split view.",
                        ),
                        diagram(GuideDiagram.Kind.SPLIT),
                        bullets(
                            "Layout has the ratio, swap and fullscreen/restore controls. Drag the divider to resize. Layout choices are remembered.",
                            "In Single, hold an app for a one-time orientation.",
                        ),
                        heading("If an app does not open"),
                        paragraph(
                            "Expand status to read the error, then use Apps to try again. A launch notice reports progress; it does not guarantee that an app accepted the launch."
                        ),
                    ),
                ),
                Topic(
                    "Workspace shortcut",
                    R.drawable.ic_save,
                    listOf(
                        heading("Save and add to Home screen"),
                        steps(
                            "Arrange one app or two split panes.",
                            "Open Settings > Workspace shortcut and choose Save current workspace.",
                            "Choose Add to phone Home screen. The launcher may add the icon immediately or ask you to confirm. Check all Home screen pages.",
                        ),
                        heading("If the icon does not appear"),
                        paragraph(
                            "Check DexDroid's shortcut permission in phone App info, then retry. On the tested Xiaomi launcher: App info > Other permissions > Home screen shortcuts > Allow. Labels and availability vary by phone and launcher. Canceling or declining keeps the saved workspace."
                        ),
                        heading("Restore and update"),
                        bullets(
                            "Tap Restore workspace to replace the current monitor arrangement with the saved apps, empty panes, layout and rotation. The shortcut acknowledges startup while connecting.",
                            "Use Update saved workspace to capture later changes explicitly.",
                            "Clear keeps the running arrangement. Remove any remaining Home screen icon manually in the phone launcher.",
                        ),
                        heading("What is not saved"),
                        paragraph(
                            "App-internal pages, documents and unsaved content are not captured. Resolution/DPI follow the connected monitor profile. Audio and scroll settings remain independent."
                        ),
                        heading("If restoration fails"),
                        paragraph(
                            "Missing apps fail preflight before replacement. A later failure can leave a partially restored workspace and reports that outcome. Stop cancels remaining work."
                        ),
                    ),
                ),
                Topic(
                    "Phone touchpad",
                    R.drawable.ic_touchpad,
                    listOf(
                        diagram(GuideDiagram.Kind.TOUCHPAD),
                        bullets(
                            "Slide one finger to point; tap to click.",
                            "Two fingers scroll or pinch to zoom.",
                            "Swipe three fingers right for Back.",
                        ),
                        heading("Header actions"),
                        paragraph(
                            "Scroll is at the left; remote Back is at the right. Info beside Touchpad explains the gestures."
                        ),
                        heading("Pointer visibility"),
                        paragraph(
                            "The touchpad pointer fades after five idle seconds. Move or touch the pad to show it immediately; the first tap still clicks. It stays visible while a gesture is held. Physical monitor touch hides it immediately. Android's separate mouse cursor keeps its platform behavior."
                        ),
                    ),
                ),
                Topic(
                    "Monitor and keyboard",
                    R.drawable.ic_navigation,
                    listOf(
                        diagram(GuideDiagram.Kind.NAVIGATION),
                        paragraph(
                            "Swipe inward from either side for Back, up from the bottom for Apps, or down from the top for Controls. Start just inside system bars."
                        ),
                        heading("Typing"),
                        bullets(
                            "Apps search uses local keys on the monitor.",
                            "Hardware keys go to the focused pane.",
                            "Eligible Android 12+ setups can request the selected Android keyboard for managed app fields. See Android keyboard on monitor below.",
                            "General phone-keyboard mirroring is not included. Android 11 retains its existing keyboard routing.",
                        ),
                    ),
                ),
                Topic(
                    "Rotation and scrolling",
                    R.drawable.ic_rotation,
                    listOf(
                        diagram(GuideDiagram.Kind.ROTATION),
                        paragraph(
                            "Rotate selects the monitor's physical orientation independently of the phone."
                        ),
                        heading("Remember an app's settings"),
                        paragraph(
                            "Settings > App settings contains Save and Reset. Saved settings follow the selected app or split pane."
                        ),
                        heading("Choose a scroll mode"),
                        bullets(
                            "Scroll chooses swipe or wheel and speed. Swipe is often better for paged readers.",
                            "Settings also has pointer speed and scroll direction.",
                        ),
                    ),
                ),
                Topic(
                    "Display and profiles",
                    R.drawable.ic_display,
                    listOf(
                        bullets(
                            "Settings adjusts desktop resolution and UI DPI, not necessarily the monitor's physical signal mode.",
                            "Fit preserves proportions and adds bars where needed.",
                            "Keep a preview within 15 seconds or it reverts.",
                        ),
                        heading("Remember a monitor"),
                        paragraph(
                            "Monitor profiles save setups and remember matching monitors. Identical reported monitor identities may share a binding."
                        ),
                    ),
                ),
                Topic(
                    "Background and status",
                    R.drawable.ic_background,
                    listOf(
                        bullets(
                            "Tap monitor status to show or hide details. Show details / Hide details shares the status tap area.",
                            "The plain Start/Stop icon is a separate action. Stop ends the desktop and returns to the existing phone controller after cleanup.",
                            "Background operation keeps the monitor running while the phone controller is away.",
                            "Save/Reset and current settings follow the selected app or split pane.",
                        )
                    ),
                ),
                Topic(
                    "Device capabilities",
                    R.drawable.ic_display,
                    listOf(
                        paragraph(
                            "Settings > Device capabilities reports Shizuku, Monitor, Desktop, Input, Rotation + split and Audio selection."
                        ),
                        heading("Read the verdict"),
                        bullets(
                            "Available: prerequisites were found. This is not universal app compatibility.",
                            "Confirmed this session: only the named operation was observed.",
                            "Unavailable: a known restriction is explained. Not checked: evidence is insufficient.",
                        ),
                        heading("Refresh and limitations"),
                        bullets(
                            "Refresh reads metadata without changing settings, apps or audio. It has a short deadline and keeps unknown facts explicit. No guided tests run automatically.",
                            "Reported physical signal and DPI differ from virtual resolution and UI DPI.",
                            "Ordinary operations can add scoped confirmations. Reconnecting, changing layout or stopping invalidates old observations.",
                            "Input dispatch does not prove an app responded. Audio policy does not prove audible sound.",
                        ),
                        paragraph(
                            "Diagnostic log includes this report when you explicitly choose Copy or Share."
                        ),
                    ),
                ),
                Topic(
                    "Audio output",
                    R.drawable.ic_audio,
                    listOf(
                        steps(
                            "Open Audio and choose an available connected media output for this desktop session.",
                            "Choose System default to release DexDroid's override.",
                        ),
                        heading("What changes"),
                        paragraph(
                            "The phone-wide default media route changes, including both panes. Calls, microphone input, casting and output choices inside apps may differ. Android media route is Android's anticipated route, not proof of audible sound."
                        ),
                        heading("Cleanup and unavailable output"),
                        paragraph(
                            "Stop or monitor disconnect attempts to restore the previous preferred policy. A helper crash or lost privilege can prevent confirmation. Check Android sound settings or restart the phone if cleanup is uncertain. Audio control is unavailable on devices that do not grant shell audio-routing access."
                        ),
                    ),
                ),
                Topic(
                    "Android keyboard on monitor",
                    R.drawable.ic_keyboard,
                    listOf(
                        steps(
                            "Open Settings > Input > Android keyboard on monitor.",
                            "Turn the option on and follow the current placement status. The feature starts Off unless you saved a choice.",
                            "If Applies on next desktop start appears, finish your work, then use Stop and Start on the phone. No open app is restarted automatically.",
                            "Once placement is configured, tap a text field in the managed app.",
                        ),
                        heading("Placement is not proof of typing"),
                        paragraph(
                            "Placement configured means the policy was read back. It does not prove a keyboard is visible or an app received text. On supported Android 12+ trusted displays, the selected Android keyboard edits the app directly without a phone keyboard mirror or text synchronization. Android and the selected keyboard control appearance, insets and focus."
                        ),
                        heading("Split view and menus"),
                        bullets(
                            "A single native keyboard follows the focused split editor.",
                            "A dimmed app keyboard may remain behind an open menu. Outside tapping dismisses the menu without typing. Retap the app field after closing.",
                            "Apps search and profile-name entry keep the local monitor keys. Hardware keyboards remain supported.",
                        ),
                        heading("Off, unavailable and retry"),
                        bullets(
                            "Off releases DexDroid's override to Android's prior routing; it does not force the phone. Creation-time focus behavior resets at the next desktop start.",
                            "Unavailable leaves the desktop running. Android 11 keeps its existing routing because this feature cannot create a trusted keyboard display there.",
                            "Retry appears only after a failed check and does not replay input.",
                            "Stop or display removal attempts to restore the prior placement policy. Lost privilege or a timed-out platform call can prevent confirmation.",
                        ),
                    ),
                ),
                Topic(
                    "Shizuku and banking apps",
                    R.drawable.ic_setup,
                    listOf(
                        heading("Remembered pairing versus startup"),
                        paragraph(
                            "Pairing and DexDroid authorization can be remembered. Non-root Shizuku still needs authorized ADB startup after reboot. A smaller helper also needs ADB, root or manufacturer privileges; a one-time ordinary app permission cannot permanently grant shell access."
                        ),
                        heading("Debugging and banking checks"),
                        bullets(
                            "Computer ADB is another startup path, not a promise of banking-app acceptance. Shizuku warns that disabling USB debugging or Developer options can stop its service.",
                            "Ordinary Stop does not change debugging. Settings > Turn off Developer Mode is a separate confirmed action.",
                            "That action stops the desktop, releases session audio and uses current Shizuku access to turn off Wireless debugging, Developer options and USB debugging. Pairing and app authorizations are kept.",
                            "Results continue on the phone. Unknown or partial results need manual checking in Developer options.",
                            "DexDroid has no persistent settings permission and never re-enables these switches. Another app or later Shizuku startup may enable them again. Bank acceptance is not guaranteed or bypassed.",
                        ),
                        heading("Use the desktop again later"),
                        paragraph(
                            "Open phone settings opens the main Settings page for manual checks. Use the desktop again expands generic future setup instructions. If Developer options is hidden, open About phone and repeatedly tap Build number or the manufacturer's version entry; then follow Shizuku setup. This is deliberate later setup, not part of disabling debugging for banking."
                        ),
                        paragraph(
                            "Optional Xiaomi example: Settings > About phone > Detailed info and specs; repeatedly tap OS/MIUI version, then open Additional settings > Developer options. Paths and labels vary by phone. Follow your bank's supported requirements."
                        ),
                    ),
                ),
                Topic(
                    "Menus and troubleshooting",
                    R.drawable.ic_info,
                    listOf(
                        bullets(
                            "Up returns to the previous menu. Back hides an open keyboard first.",
                            "X closes the whole menu flow. On the monitor, tapping outside does the same without passing that touch to an app.",
                            "Settings shows the installed version and Diagnostic log.",
                            "Search settings finds related terms such as battery, scroll speed or USB debugging. Results open the existing page or control; Clear shows all settings again.",
                            "Returning from a child page keeps your search. Closing Settings clears it. Tap the search field to show the phone's Android keyboard or the monitor's local keys.",
                        ),
                        heading("Diagnostic log"),
                        bullets(
                            "Current context is separate from recorded events. Copy log copies one report. Share log opens Android sharing on the phone.",
                            "Clear log removes history; new events may appear. Raw names and uncontrolled details are omitted. Dispatch does not prove an app handled input.",
                        ),
                        heading("Blank or unsupported apps"),
                        paragraph(
                            "Protected apps can blank output. Protected screen help explains Chrome incognito and its optional flag. App resizing, DRM and monitor touch depend on Android/OEM support."
                        ),
                    ),
                ),
            )

        @JvmStatic
        fun add(style: ControllerStyle?, body: LinearLayout?) {
            val theme = requireNotNull(style)
            val parent = requireNotNull(body)
            topics.forEachIndexed { index, topic ->
                val section =
                    LinearLayout(parent.context).apply {
                        orientation = LinearLayout.VERTICAL
                        tag = "guide-section-$index"
                        setPadding(theme.dp(12f), 0, theme.dp(12f), theme.dp(12f))
                        background = theme.surface(18, 8, 40)
                    }
                parent.addView(
                    section,
                    LinearLayout.LayoutParams(-1, -2).apply {
                        setMargins(0, theme.dp(6f), 0, theme.dp(6f))
                    },
                )
                theme.readingHeading(section, topic.title, topic.icon).tag = "guide-heading-$index"
                topic.blocks.forEach { block ->
                    when (block) {
                        is Block.Paragraph -> theme.paragraph(section, block.text)
                        is Block.Heading -> theme.readingHeading(section, block.text)
                        is Block.Items -> theme.instructionList(section, block.text, block.numbered)
                        is Block.Diagram -> section.addView(GuideDiagram(theme, block.kind))
                    }
                }
            }
        }
    }
}
