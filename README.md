# DexDroid

<img src="assets/dexdroid-icon.png" alt="Android diving into a monitor" width="80" />

A non-root Android external desktop with independent monitor rotation, phone
touchpad, touchscreen navigation and remembered app settings.

## Requirements

- Android 11+, a working external-video connection and a monitor.
- Running [Shizuku](https://shizuku.rikka.app/) with DexDroid authorized.
- Initially tested on Black Shark 4 / JoyUI with a 1920x1200 touchscreen monitor.
- The Kotlin release is primarily verified on Android15 emulator/Xiaomi14.
  Other OS versions need community testing; see [verification limits](docs/changes/kotlin-migration-findings.md).
- Android 17 remains [experimental](docs/TROUBLESHOOTING.md#android-17-experimental-support).

## Build and install

Download the APK from [GitHub Releases](https://github.com/PhiLong-Ho/DexDroid/releases),
or build it locally:

Open this folder in Android Studio, configure Android SDK 36, and use JDK 17+.
The project uses Kotlin and Gradle Kotlin DSL; see the [migration record](docs/changes/kotlin-migration.md).
Connect an ADB-authorized phone, then run:

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows use `.\gradlew.bat` instead of `./gradlew`. This installs a debug build.
Diagnostic test screens are included only in debug builds; official releases omit them.
Developers: start with [onboarding](docs/ONBOARDING.md); use
[CONTRIBUTING.md](CONTRIBUTING.md) for commands and [coding practice](docs/CODING_PRACTICE.md) for the daily loop.
Device tests and supervisor-wrapped host commands print an attach command for the
[local status viewer](docs/LOCAL_MONITORING.md).

## Quick guide

1. Connect the monitor, start Shizuku, then open DexDroid. Authorize it on first use;
   the desktop starts automatically when the monitor and Shizuku are ready.
2. Choose **Rotate** for landscape, portrait or either flipped orientation.
   **Apps** launches an app; hold an app for a one-time orientation.
3. Use the phone pad: move/tap to point/click, two fingers to scroll or pinch,
   three fingers right for Back. The pad header has **Scroll** left, **Touchpad + Info**
   centered and **Back** right. Tap Info for gesture help.
   **Settings → App settings** has Scroll, Save and Reset.
4. On the monitor, swipe inward from either side for Back, up from the bottom for
   Apps, or down from the top for Controls. On modern Android start just inside
   system-bar areas. Monitor search supports local keys and hardware editing
   without opening the phone keyboard. Back hides the keys
   first; the next Back returns or closes the menu.
5. **Settings** shows the installed version/build, resolution/UI DPI, input options,
   profiles, diagnostics, Shizuku setup and an offline **User guide**.
   Use Show details / Hide details in monitor status to expand or collapse it.
   Expansion is remembered; Start/Stop remains separate.
   The plain status Start/Stop icon controls the desktop. Stop returns to the
   existing phone controller after cleanup, including Chrome task handoff; normal
   phone backgrounding keeps the desktop running. Menu headers use plain icons: Up returns one level; X closes the whole menu flow.
6. **Settings > Monitor profiles** saves the current resolution/DPI/orientation.
   Apply a profile and **Keep** to remember it for that monitor; use **Update from
   current** to change a saved snapshot. Recognized monitors restore it on startup.

7. In **Apps**, tap **Left-right / Up-down** to enable split immediately,
   including from fullscreen, or **Single** to return to one pane. In split view,
   tap **Left/Right** or **Up/Down** beside an app to open it on that side.
   **Layout** contains ratio, swap and fullscreen/restore controls; drag the divider
   to resize. A pressed launch action gently highlights its destination.
   Layout settings survive reopening. Ending split keeps pane 1 and closes pane 2.

Monitor touch depends on Android's display association. App resizing/DRM support
varies. General Android keyboard mirroring is not included. Shizuku needs restarting
after a phone reboot. See [Troubleshooting](docs/TROUBLESHOOTING.md) for protected
Chrome incognito output and the optional user-controlled Chrome flag.

[Specification](SPEC.md) · [Changelog](CHANGELOG.md) ·
[Code and feature atlas](docs/architecture/README.md) | [Feature specs](docs/specs/README.md) · [Run tests](docs/TESTING.md) ·
[Test results](testresults/README.md) · [Design](docs/DESIGN.md) ·
[Releasing](docs/RELEASING.md) · [Codex workflow](docs/CODEX.md)

Test evidence: [privacy policy](docs/PRIVACY.md). Personal app captures stay local;
public artifacts contain only guarded host results and reviewed synthetic fixtures.

Every version release requires an architecture expert and two final reviewers;
see [release procedure](docs/RELEASING.md#release-architecture-review).
