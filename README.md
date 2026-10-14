# DexDroid

<img src="assets/dexdroid-icon.png" alt="DexDroid phone and monitor icon" width="96" />

Non-root external-monitor desktop and phone touchpad for Android 11+, using
Shizuku shell privileges. Initial device: Black Shark 4 / JoyUI.

## Use

1. Connect the monitor and start Shizuku through wireless debugging.
2. Open **DexDroid**. It connects to Shizuku and starts the desktop automatically.
   Approve Shizuku authorization on first use. If the monitor is not attached,
   DexDroid waits and starts when it appears.
3. Use **Rotate** to select 0°, 90°, 180°, or 270° for the whole monitor image.
4. Choose **Apps**. Firefox, YouTube ReVanced, Tachiyomi, Chrome, and the retained
   Portrait Desk Test appear near the top when installed. Hold an app for a
   one-time orientation. Use Save app or Settings → Reset app settings for its profile.
5. Slide to move the cursor; tap to click; two fingers scroll or pinch to zoom;
   three fingers swipe right for Android Back. A Back button is also available.
6. **Settings → Resolution and DPI** detects signal modes and selects virtual resolution and UI DPI.
   Preview changes, then Keep or Revert; unconfirmed changes revert in 15 seconds.
7. **Stop** releases the desktop. The notification also provides Stop. The main
   control changes to **Start**; reopen DexDroid or select an app to start again.

The monitor's own touchscreen is enabled by default in version 0.3. Tap,
swipe/drag, long press, and pinch are forwarded to the desktop with four-way
rotation and resolution scaling. Touches in Fit bars are ignored. Options can
toggle monitor touch; a monitor gesture temporarily takes priority over phone
touchpad gestures.

The desktop continues when you press Home, switch phone apps, or close the phone
controller. A foreground notification provides a way back and a Stop action.
Settings → Background operation shows and manages the battery exemption;
it has already been enabled on the development phone. Force stop, reboot, and
Shizuku termination still end the session.

Version 0.4 hides the pointer during monitor touch and restores it only through
phone touchpad input. The main **Scroll speed** button saves Slow, Normal,
or Fast independently for each launched app. Normal is half the previous speed;
Tachiyomi defaults to Slow (one fifth). This also scales swipe-scroll fallback,
while direct monitor swipes retain native Android behavior.

Options include pointer speed and scroll direction; the main mode selector offers swipe scrolling
for apps that ignore mouse wheels. Zoom depends on the target content supporting
native pinch. Rotation is independent of the phone's orientation.

Version 0.5 adds a compact app status panel: scroll mode, speed, remembered
rotation, monitor touch, and scroll direction. Main controls include mode,
speed, Save app, and Reset app. Save app remembers rotation, scroll mode, and
speed together; Reset app restores defaults for that app. Tachiyomi defaults
to Swipe because mouse-wheel steps can skip many reader pages. Connect, Display,
and Center are available in Options in that version.

Version 0.6 connects and starts automatically, including when you select an app
before the desktop is ready. A prominent monitor banner shows disconnected,
waiting, and ready states. The gear opens Options; compact icon controls retain
labels and accessibility descriptions. Manual connection recovery appears only
in Options when needed. **Options → Diagnostic log** shows the latest 250 events
and updates live, with Copy and Clear. It records gesture recognition, three-finger
Back acceptance/rejection with movement thresholds, key dispatch, monitor input,
and connection errors. Logs are held in memory for the controller service's lifetime;
screen and typed content are not recorded. A sent key confirms dispatch through
Shizuku, not that every target app handled it. Shizuku's service must still be
started separately after reboot; DexDroid reconnects when it becomes available.

Version 0.7 introduces a black-and-white controller. Title and Settings stay at
the top, followed by a single compact status panel with slightly emphasized
monitor status. Six equal controls form two rows: Apps, Rotate, Scroll, Back,
Save app, and Stop/Start. **Scroll** combines the current per-app mode and speed
in one panel. The touchpad's **info** icon opens gesture instructions; hints no
longer occupy the main screen. A grouped Settings sheet shows live values for
pointer speed, scroll direction, monitor touch, and background operation. Reset
app/monitor settings and the diagnostic log are available there. Background
permission is managed through Android settings, and its value refreshes on return.

Version 0.7.1 places **Stop/Start, Save app, Back** left-to-right in the second row.
The current implemented feature contract is consolidated in [SPEC.md](SPEC.md).
For subsequent requests, [AGENTS.md](AGENTS.md) requires updating the spec first,
then implementation, then acceptance/E2E tests and verification.

Version 0.8.1 provides navigation and phone-style menus directly on the monitor:

- Swipe inward from either side edge for Back (right edge → left, or left edge → right).
- Swipe up from the bottom edge for the DexDroid main screen/app launcher.
- Swipe down from the top edge for the same title/status/six icon controls as
  the phone controller, without its touchpad. Settings sits immediately left of
  the top-right Close icon; Reset app is available in Settings.
- The bottom-swipe launcher matches the phone app picker, including search,
  icons, saved orientation, and held-app one-time orientation. Close is an icon.

There is no permanent Apps/Controls dock or Resume app footer. Tap outside either
menu to dismiss it without tapping the app underneath. Edge Back dismisses menus
before navigating the app. Side/top/bottom gesture feedback follows your swipe;
menus slide/fade/scale into and out of view with short eased transitions. App-native
Back animations remain app/ROM-dependent. Shared styling keeps phone and monitor
fonts, icons, button geometry, and status cards consistent.

Directions follow the upright rendered desktop in all four rotations. Gesture
Home opens DexDroid's launcher and keeps the external app underneath; it does not
send Home to the phone. The monitor's native menus also support phone pointer,
tap, and scrolling. Shell controls remain usable when app-touch forwarding is off.
**Settings → Monitor edge navigation** can disable edge interception. Ordinary
non-navigation touch and pinch keep their native behavior. Monitor Settings shows
current values and provides display presets/DPI, pointer speed, touch/direction/
navigation toggles, diagnostics, and profile reset. Android's background permission
is labeled **Configure on phone** because it is managed by the system.

## Build and device checks

Java 17+, Android SDK 35, Gradle wrapper 8.10.2, AGP 8.8.0.
Open this folder in Android Studio and let it configure `local.properties`, or
create that file with your Android SDK path (for example,
`sdk.dir=C:/Users/your-name/AppData/Local/Android/Sdk` on Windows).
The SDK path, build output, local screenshots, and signing keys are excluded
from Git. The Gradle wrapper is included.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument --no-hidden-api-checks -w dev.dexdroid.test/dev.dexdroid.DeviceChecks
```

Device checks require a monitor, unlocked phone, running Shizuku, and prior
DexDroid authorization. They change temporary session settings and test saved
preferences, then reset their monitor/app profile changes. They do not clear app
data. Instrumentation terminates its target process when finished; reopen DexDroid
afterward. See `device-checks.txt` for the 237 passing monitor checks on version 0.7,
`ui-checks-v0.7.txt` for UI verification, and `controller-checks-v0.6.txt` for the
earlier checks without a monitor.

Without a monitor, connection, launch queuing, and diagnostic checks can be run with:

```powershell
adb shell am instrument --no-hidden-api-checks -w -e controllerOnly true dev.dexdroid.test/dev.dexdroid.DeviceChecks
```

With a monitor, run the focused controller acceptance/E2E suite (visible button
order/sizes, Stop focus, Start recovery, and external Back delivery) with:

```powershell
adb shell am instrument --no-hidden-api-checks -w -e uiOnly true dev.dexdroid.test/dev.dexdroid.DeviceChecks
```

Version 0.7.1 passed all 29 focused checks; see `ui-acceptance-v0.7.1.txt`.

Run monitor gesture/shell acceptance with an unlocked phone, running Shizuku,
and the touchscreen monitor attached:

```powershell
adb shell am instrument --no-hidden-api-checks -w -e monitorOnly true dev.dexdroid.test/dev.dexdroid.DeviceChecks
```

The test restores saved preferences and ends the desktop through monitor Stop.
Reopen DexDroid to resume. Tests inject events on the actual physical display and
check Back delivery, launcher/panel selection, rotated controls, gesture rejection,
menu input isolation, phone pointer interaction, settings, Fit bars, and background
operation. Other touchscreen/ROM combinations still need validation.

Version 0.8 passed 251 monitor acceptance checks and 237 desktop/input regression
checks. See `monitor-acceptance-v0.8.txt` and `device-checks-v0.8.txt`.

Version 0.8.1 passed 554 monitor checks, 29 phone-controller UI checks, and 237
desktop/input regression checks on Black Shark 4 / Android 11 / JoyUI (820 total).
See `monitor-acceptance-v0.8.1.txt`, `ui-acceptance-v0.8.1.txt`, and
`device-checks-v0.8.1.txt`. Coverage includes shared styling/text contrast in all
four rotations and rendered Stop-label pixels, launcher search and held-app orientation, outside/Fit-bar dismissal,
live gesture feedback and settled transitions, and background operation. Build and
lint passed; no lint errors, existing warnings remain. The menus were also checked
visually on the connected display.

## Current limits

- Touchpad mode ships first. Direct gesture mapping and keyboard relay remain
  optional future work in [SPEC.md](SPEC.md).
- Monitor touch requires Android to associate the external touchscreen with the
  HDMI display. The connected `wch.cn TouchScreen` is associated correctly on
  this Black Shark / JoyUI setup; other connections need validation.
- Android reports the monitor's signal modes. Physical signal switching is not
  yet validated; desktop resolution selection is implemented independently.
- Physical DPI reports on this monitor are implausible and labeled unknown.
  UI DPI is a layout setting, not a measurement of the panel.
- Monitor profiles match name and a sorted capability fingerprint. Identical
  monitors may share a profile; a reset option is available.
- Apps can impose orientation/resize restrictions; media/DRM compatibility varies.
  Only apps opened through DexDroid apply saved per-app orientations.
- Monitor unplug/replug uses new display IDs; automatic sessions resume on
  reconnection. A stopped Shizuku service must be restarted, particularly after
  a phone reboot. Other devices' reconnect behavior still needs validation.
- This is an initial debug build, not a signed store release.

The development phone also retains the separate `dev.portraitdesk` test package.
It is not required to build or run DexDroid and is not included in this repository.

## Icon

Version 0.5.1 adds a cyan monitor and violet phone icon with adaptive launcher
masking, plus a monochrome themed icon for Android 13+. Editable artwork is in
[assets/dexdroid-icon.svg](assets/dexdroid-icon.svg); Android vector resources
are in `app/src/main/res`. No image generation service is required.
