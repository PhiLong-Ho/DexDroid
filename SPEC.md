# DexDroid — App Specification

Status: initial DexDroid implementation installed for device experiments.
Core rotation, touchpad gestures, display resizing, profile persistence, and
resolution rollback have passed device checks. Version 0.3 adds external-monitor
touchscreen input and protected background operation at the user's request.
Device checks cover native monitor taps and pinch at all four rotations, edge
coordinates, lower-resolution scaling, and continued input after the controller
is destroyed and all activity bindings are removed.

Version 0.4: show the desktop pointer only after phone touchpad input; hide it
immediately on monitor touch and leave it hidden after that gesture ends.
Remember Slow/Normal/Fast scroll speed per launched app for mouse-wheel and
touchpad swipe fallback. Default to half the original speed, with Tachiyomi at
one fifth; direct monitor touch remains unscaled.

Version 0.5: provide a compact current-app settings panel showing scroll mode,
speed, remembered rotation, monitor touch, and scroll direction. Put mode,
speed, Save app, and Reset app on the main controller; move Connect, Display,
and Center into Options. Save persists rotation/mode/speed together; Reset
clears that app profile and applies defaults. Scroll mode is remembered per
app, with Tachiyomi defaulting to native swipe to avoid large wheel page jumps.

Version 0.6: opening DexDroid automatically authorizes/connects to a running
Shizuku service and starts a desktop once a monitor is available. App selections
queue until ready; selecting an app after Stop resumes the desktop. Reconnection
waits for monitor/Shizuku availability. Starting Shizuku itself after reboot remains
an external prerequisite. Stop remains available in Options and the notification.
Show manual retry/Open Shizuku only in Options when the bridge is unavailable.
Use a prominent top monitor-state banner and a gear icon for Options; compact
action icons retain labels, content descriptions, and tooltips.

Provide Options → Diagnostic log with a live, bounded 250-event memory history,
Copy, and Clear. Record connection/session events, ignored input, pointer counts,
tap/pinch/scroll recognition, and three-finger Back recognized/rejected outcomes
including horizontal/vertical distance and the required threshold. Record key
queuing and completed Shizuku IPC; never claim that IPC completion proves the
target app consumed Back. Do not log screen content or keyboard text. Logs last
only for the controller service's lifetime.

Version 0.7 UI: use a monochrome theme throughout the controller and its dialogs.
Keep title/Settings at the top; place all monitor, session, app, rotation, scrolling,
and operation-message text in a single compact status panel. Monitor status uses
slightly brighter/bolder text, not a separate large colored banner. Below it,
show two equal three-button rows: Apps/Rotate/Scroll and Back/Save app/Stop.
Stop becomes Start when paused. Combine scroll mode and speed in one panel with
current selections and immediate per-app persistence. Put gesture instructions
behind a touchpad info icon; do not show them by default. Replace the plain menu
with a grouped bottom settings sheet. Show current pointer speed, scroll direction,
monitor touch, background battery exemption, and saved profile state. Keep reset
actions and diagnostic logging in Settings. Toggle/value rows update live;
system-managed background permission refreshes after returning from Android settings.

## 1. Purpose

Turn a non-root Android phone into a desktop device for an external monitor.
The phone provides touchpad or gesture input. Apps run on the monitor, with
output rotated to match the monitor's physical orientation independently of
the phone's orientation.

Initial target: Black Shark 4, Android 11 / JoyUI, USB-C video output, and a
1920 × 1200 monitor. The monitor does not rotate the incoming signal itself.
Shizuku supplies the required shell privileges without rooting the phone.

## 2. Proven baseline

- A public virtual display can be created through a Shizuku user service.
- A 1200 × 1920 virtual desktop renders rotated across the full 1920 × 1200
  physical output, avoiding JoyUI's portrait letterboxing.
- Chrome launches fullscreen on that virtual display.
- The phone retains a separate touchpad interface.
- Phone touchpad input reaches the virtual display; a test-button click was
  verified in device logs.
- The user reports the rotated display and touchpad work as expected.

The initial implementation also verifies four-way rotation with a stable virtual
display ID, display-targeted taps, native pinch injection, scroll events, phone
gesture recognition, saved orientation/profile settings, and 15-second rollback.
Physical captures verify proportional output and cursor alignment in all four
orientations. Broad app compatibility, unplug/replug behavior, and physical
signal switching still require further testing.

## 3. Product scope

### Required for the first usable release

1. External-display detection and session start/stop.
2. App picker that launches installed apps on the external desktop.
3. Fullscreen launching by default.
4. Four output orientations: landscape, portrait, flipped landscape, and
   flipped portrait.
5. A persistent session orientation used for every app launch by default.
6. Touchpad mode, including tap, scroll, zoom, and Android Back.
7. A visible pointer and controls to recenter it.
8. Optional saved orientation per app, with explicit save/reset actions.
9. Clear connection, permission, and failure states.
10. Detect monitor-reported resolutions and refresh rates, recommend desktop
    resolution and UI density, and allow resolution selection.
11. Remember confirmed resolution/density settings for each monitor.
12. Direct touchscreen control on a supported external monitor, including tap,
    swipe/drag, long press, and native multi-touch pinch.

### Optional after the required features pass device testing

- Direct gesture mode and a quick switch between input modes.
- Browser Forward.
- Gesture to open the app picker.
- Keyboard relay from the phone to the active monitor app.
- Drag, long press, secondary click, and recent-app switching.
- Freeform windows and window arranging.

Direct gesture mode is desirable, but touchpad mode is the initial default
because relative pointing is already proven on this device. Required gestures
must work in touchpad mode even if direct gesture mode is deferred.

## 4. User flow

1. Connect the monitor. Open DexDroid on the phone.
2. If Shizuku is unavailable, show the steps to start or authorize it.
3. Connect to Shizuku and start the desktop automatically. Wait for an attached
   monitor if necessary; resolution and UI size remain adjustable in Options.
4. Choose the monitor orientation from the Rotate control's four labeled choices.
5. Open the app picker and choose an app. Launch it fullscreen on the virtual
   display, applying its saved orientation or the session default.
6. Use the phone's input surface. Keep Back, app picker, rotation, and input mode
   on the main controller; keep Stop and diagnostic logging accessible in Options.
7. Stop the desktop or disconnect the monitor. Release the session and restore
   any physical-display settings changed by this app.

First launch defaults to landscape. Subsequent sessions restore the last
selected session orientation. Phone auto-rotation must not change monitor
orientation. Physically rotating the monitor requires a selection in the app;
automatic monitor-orientation sensing is not required.

## 5. Rotation and display model

Rotation changes the complete output image, not merely the app's requested
orientation. Maintain a landscape physical output and render the virtual
desktop with the appropriate transform.

For a native 1920 × 1200 monitor:

| Profile | Virtual desktop | Output transform |
| --- | --- | --- |
| Landscape | 1920 × 1200 | 0° |
| Portrait | 1200 × 1920 | 90° |
| Flipped landscape | 1920 × 1200 | 180° |
| Flipped portrait | 1200 × 1920 | 270° |

The degree values describe rotation within the monitor's native landscape
signal. Show orientation previews so the user can identify the correct
direction without interpreting degree conventions. These are four discrete
orientations, not continuous arbitrary-angle rotation.

- Discover display IDs at runtime; never assume the monitor is display 7.
- Detect current physical mode and supported monitor dimensions. Swap virtual
  width/height for portrait. Do not assume the current mode is the panel's native
  mode unless a reliable preferred/native indication is available.
- Use one shared transform definition for rendering, pointer placement, and
  input mapping. Test all four orientations against the same corner targets.
- Keep text and imagery proportional; avoid stretching, clipping, or narrow
  portrait letterboxing.
- Provide resolution and density controls as defined below. The prototype's
  240 DPI is a useful initial UI-size recommendation for this 1920 × 1200 monitor,
  not a measurement of its physical pixel density.
- Freeze input during a rotation transition; cancel any active touch sequence.
- Prefer resizing the existing virtual display and updating the render surface
  so app tasks survive. Recreating it is a fallback, not the default.
- If a ROM requires session recreation, explain the interruption before a
  rotation change and avoid claiming seamless app-state preservation.

Output rotation is independent of individual app orientation requests. Apps
that refuse resizing or display placement may still show compatibility layouts;
the app cannot guarantee that every Android app will reflow or fill the screen.

### Monitor capabilities, recommendations, and resolution selection

Keep three concepts distinct in the display settings:

| Setting | Meaning | Behavior |
| --- | --- | --- |
| Monitor output mode | Physical signal resolution and refresh rate | Detect modes reported by Android; changing the signal is device-dependent |
| Desktop resolution | Pixel dimensions rendered by the virtual desktop | User-selectable in the first release; scale proportionally into the physical output |
| UI size / logical DPI | Android layout density controlling text and control sizes | User-selectable; independent of actual monitor pixel density |

Detection requirements:

- Query the external display's supported modes, current mode, resolution, refresh
  rate, aspect ratio, and reported display metrics when attaching/reconnecting.
- Show capabilities as reported by Android, not guaranteed capabilities beyond
  the phone, cable, adapter, and monitor connection.
- Display name and a stable display identity are preferred for saved settings;
  use a capability fingerprint fallback, with a reset option when identity is
  ambiguous. Do not persist transient numeric display IDs.
- Android exposes logical DPI and reported X/Y physical DPI. There is no list of
  "supported DPI values" like a list of signal modes. Validate physical metrics
  for plausibility; missing or implausible values must be labeled unknown, not
  used as a trustworthy physical measurement.

Recommendation rules:

- First restore a saved, confirmed monitor profile if its settings remain valid.
- Otherwise recommend the reliably identified preferred/native mode. If that
  cannot be established, use the working current physical mode, labeled Current
  output; do not silently choose the largest advertised mode as native.
- Recommend a virtual desktop matching that mode's pixel dimensions, swapped
  for portrait, for a sharp image at the correct aspect ratio.
- Recommend logical DPI for readable desktop UI, rather than copying reported
  physical DPI. An initial heuristic targets roughly 800 logical dp across the
  shorter desktop edge: `dpi = 160 × shorterPixels / 800`, rounded to a sensible
  preset and bounded to the tested density range. This gives 240 DPI at 1200 px.
  Treat the heuristic as a tunable product default, not a hardware capability.
- Offer a lower-resolution performance preset with the same aspect ratio.
  Preserve approximately the same logical layout by adjusting density alongside
  resolution. Never change UI size without showing the resulting choice.
- Explain recommendations in user terms: sharper picture, larger controls, or
  lower rendering load. Include a preview and let the user override them.

Resolution picker requirements:

- Provide Recommended, same-as-output, and lower-resolution desktop presets.
- List exact desktop dimensions, aspect ratio, and resulting logical DPI.
- For the current monitor, examples are 1920 × 1200 and a lower-load 1280 × 800
  desktop, with portrait equivalents 1200 × 1920 and 800 × 1280. A virtual preset
  need not be an advertised physical signal mode; label it as desktop resolution.
- Offer monitor-reported physical modes separately, including their refresh rates.
  Physical-mode requests are best-effort: verify the actual active mode afterward.
  If JoyUI ignores/rejects the request, retain the working signal and explain that
  desktop resolution remains adjustable. Never report an unapplied signal change
  as success or require physical-mode switching for the initial release.
- Preserve aspect ratio by default. If the user selects a different aspect ratio,
  use Fit with intentional bars and explain that the image will not fill the
  monitor. Do not stretch it; remap input to the actual displayed content area.
- Apply desktop resolution and density together, cancel active input, clamp or
  proportionally reposition the pointer, and update rendering/input transforms.
- Prefer resizing the virtual display without terminating user apps. Clearly
  disclose any required recreation and preserve recoverable session state.
- For display changes, retain the last working configuration and present Keep /
  Revert on the phone with a 15-second timeout. Failed or unconfirmed changes
  revert automatically. Save a profile only after the user confirms it.
- Per-app rotation swaps the selected desktop width/height when needed; it does
  not overwrite the monitor's saved resolution, density, or signal mode.

Detection and physical DPI accuracy depend on the ROM and monitor metadata.
Actual physical-signal switching and the recommendation heuristic require
device validation before being described as supported.

## 6. Input modes and gestures

### Touchpad mode — required and default

The phone surface controls a cursor in virtual-desktop coordinates. Movement
is relative; lifting and repositioning a finger must not move the cursor.

| Phone action | Monitor behavior | Priority |
| --- | --- | --- |
| One-finger slide | Move pointer | Required |
| One-finger tap | Tap at pointer | Required |
| Two-finger slide | Scroll vertically/horizontally | Required |
| Two-finger pinch/spread | Zoom around pointer | Required |
| Three-finger swipe right | Android Back | Required |
| Three-finger swipe left | Forward where supported | Optional |
| Three-finger swipe up | Open app picker on phone | Optional |
| Tap, then hold and slide | Drag at pointer | Optional |
| Long press / two-finger tap | Long press / secondary click | Optional |

Always provide a visible Back button as an alternative to the gesture. Cursor
speed and scroll direction are adjustable. Clamp the pointer to desktop bounds.
Gesture thresholds must distinguish tap, movement, scroll, and pinch without
accidental taps when extra fingers are placed or lifted.

Zoom requires a real two-pointer touchscreen sequence on the virtual display,
not simulated repeated clicks or a generic zoom key. Anchor its center at the
cursor and keep both contacts within display bounds. Honor proper pointer IDs,
down times, action indices, and event ordering. Clamp pinch span near edges.
Zoom takes effect only in apps/content that support pinch-to-zoom.

For scrolling, prefer mouse-wheel input when it behaves correctly in the target
app. Support a touchscreen-swipe fallback for apps that ignore wheel events.
Choose and test the fallback without changing the user's selected orientation.

### Direct gesture mode — optional second mode

Map the phone's normalized input surface to the entire upright virtual desktop:

- Tap selects the corresponding monitor location.
- One-finger swipe becomes a touchscreen swipe, as on Android.
- Two-finger pinch/spread becomes a native pinch gesture at the mapped location.
- Three-finger swipe right invokes Back; retain the visible Back button.

Use a bounded input surface whose aspect ratio matches the virtual desktop, or
clearly show the active mapping area. Do not silently distort touch geometry.
Consider a lightweight monitor preview later; it is not required for this mode.

A visible mode toggle is required if direct gesture mode ships. Switching modes
cancels active gestures, keeps the active app and orientation, and briefly labels
the new mode. Remember the user's last input mode globally.

### Navigation semantics

- Back means Android Back on the active external app, not the phone activity.
- Forward is optional and app-dependent. It must not be described as a universal
  Android navigation action; unsupported apps should safely do nothing or show
  an unavailable state.
- App menu means DexDroid's app picker, not an arbitrary app's overflow menu.
- Use three-finger navigation initially to avoid stealing ordinary one-finger
  swipes and pinch gestures from the target app.

### External monitor touchscreen — implemented in version 0.3

The user reports that direct touch on the connected monitor works in screen
mirroring but was unusable with the initial DexDroid virtual desktop. The user
has now requested implementation. The monitor touchscreen is a separate input
source, enabled by default, with an enable/disable control under Options.

- Detect whether the external display has an associated touchscreen; distinguish
  monitor touch from phone input and unrelated USB pointing devices.
- Route physical-monitor touches to the active virtual desktop using the inverse
  of the same rendering transform. Account for all four output rotations,
  selected desktop resolution, scaling, and Fit offsets/bars.
- Support tap, drag/swipe/scroll, long press, and native multi-touch pinch. Preserve
  pointer IDs, action indices, timestamps, and DOWN/MOVE/UP/CANCEL ordering.
- Touches in Fit bars must not activate desktop content. Cancel active contacts
  when rotation/resolution changes, the monitor disconnects, or the session stops.
- Prevent injected virtual touches from being captured and forwarded recursively.
  Define how simultaneous monitor and phone input are arbitrated without stuck
  contacts; retain a user-facing enable/disable control for monitor touch.
- A monitor DOWN cancels a phone touch sequence. Phone clicks, pinch/drag events,
  and wheel input are ignored until the monitor gesture finishes; phone input
  resumes with a new DOWN. Leaving or closing the phone controller cancels only
  phone input and must not interrupt an active monitor gesture.
- Receive touchscreen events on the physical monitor presentation and forward
  them through Shizuku to the virtual display. Validate JoyUI's routing and USB touch-display
  association before promising compatibility; some monitors expose touch as a
  separate HID connection.
- Acceptance: center and corner taps align in 0°/90°/180°/270°; drag and pinch work
  at native and lower desktop resolutions; Fit bars reject input; phone controls
  remain usable; unplug/Stop leaves no active contacts.

## 7. Per-app orientation

Store orientation by package name, locally on the phone. Default behavior is
Use session orientation. The user may select one of the four profiles and choose
Remember for this app, or reset it to Use session orientation.

Orientation precedence on launch:

1. A one-time orientation explicitly chosen for that launch.
2. The app's saved orientation, when enabled.
3. The session default orientation.

- Launch-time overrides do not overwrite saved preferences automatically.
- Changing rotation while an app is active offers an explicit save action.
- A saved per-app rotation does not overwrite the global session default.
- Reopening a running task through the app picker applies its orientation.
- Switching tasks outside DexDroid may defer profile application unless
  reliable foreground-app detection is implemented; do not pretend it is automatic.
- In the first fullscreen release, rotation applies to the whole desktop.
  Simultaneous windows cannot each rotate the physical monitor independently.

## 8. App launching

- List launchable apps with icon, name, search, and optional favorites.
- Resolve launch components rather than hardcoding Chrome or Firefox activities.
- Launch on the virtual display in fullscreen mode by default.
- Preserve existing app data and sessions. Do not force-stop or clear apps merely
  to launch or rotate them.
- Reuse or move an existing task when Android allows it. Explain unsupported
  placement instead of silently opening the app on the phone.
- Determine support for secondary displays and handle rejected launches.
- Initially support the primary Android user profile. Other profiles require
  explicit future support and testing.

## 9. Architecture

- Phone activity: session controls, app picker, and gesture recognition.
- Session controller: external-display discovery, virtual-display lifecycle,
  orientation, density, and active-app state.
- Monitor presentation: render the virtual-display surface and pointer with
  one of four transforms.
- Shizuku service: virtual-display creation, display-targeted input injection,
  and app/task launch operations.
- Input pipeline: recognize gestures, convert coordinates, and serialize events.
- Local preferences: session default, per-app rotation, pointer speed, scroll
  direction, input mode, and confirmed per-monitor resolution/density profiles.
- Capability/recommendation module: monitor-reported modes and metrics, stable
  profile matching, UI-density heuristic, and verified physical-mode requests.

Keep rendering independent of input recognition. Use a bounded input queue;
coalesce pointer movement without dropping gesture starts, ends, or cancellations.
Never inject an event with an absent/stale target display ID.

## 10. Lifecycle, permissions, and recovery

- Show Shizuku missing, stopped, unauthorized, and disconnected as separate states.
- Shizuku generally needs restarting after a phone reboot on non-root devices.
- Verify whether JoyUI's USB debugging security option is needed for input.
- Request only permissions used by implemented features. No Internet permission
  is needed for the core desktop/input app.
- Keep a running session alive through normal phone-activity transitions using
  the appropriate Android foreground-service lifecycle and a Stop action.
- The started foreground service owns the monitor presentation and survives
  Home, app switching, and closing/removing the phone controller task. A partial
  wake lock is held only during a running desktop and released on Stop,
  disconnect, or service destruction. Options exposes Android's background
  battery exemption request; the development device is exempted for testing.
- Background support does not promise survival after Force stop, reboot,
  Shizuku termination, or every vendor power-saving action. Such interruptions
  release the display and require a clean restart rather than stale input.
- Handle monitor unplug/replug, display-ID changes, phone orientation changes,
  Shizuku death, and app/background lifecycle events.
- On interruption, cancel input, release surfaces/displays, remove overlays,
  and prevent stuck touches or buttons.
- Restore physical-display settings this app changed, when the same display is
  still attached. Leave unrelated phone settings alone.
- Stop must not force-stop user apps or delete their state. Task relocation on
  virtual-display removal depends on Android and needs device testing.

## 11. Acceptance tests

| Test | Pass condition |
| --- | --- |
| Four physical orientations | Upright output fills the physically positioned monitor at 0°, 90°, 180°, and 270°, without stretched content or portrait letterboxing |
| Phone independence | Rotating the phone does not change monitor rotation or corrupt input mapping |
| Default launch | Each app opened from the picker uses the session profile unless overridden |
| Capability detection | The picker matches Android's current/supported physical modes; unreliable physical DPI is labeled unknown |
| Recommendation | Recommended desktop dimensions follow a valid saved profile or reliable preferred/current mode; logical DPI is presented as UI size |
| Selected resolution | Native and lower-resolution desktops render proportionally in all four orientations; taps and zoom map correctly after resizing |
| Signal request | Applied output mode is verified; ignored/rejected requests retain a usable desktop and show an honest result |
| Aspect-ratio choice | Fit bars are intentional for a mismatched aspect ratio; image and input remain proportional |
| Revert and memory | Unconfirmed changes revert in 15 seconds; confirmed monitor profiles survive reconnects and process restarts |
| Tap accuracy | Center and four corner targets activate correctly in all four rotations |
| Back | Gesture and button navigate the external app; the phone controller remains open |
| Scroll | Vertical and horizontal gestures work in Chrome and a native scrollable test screen |
| Zoom | Pinch in/out works in a zoomable test surface and supported browser content, with no accidental tap or stuck contact |
| Per-app memory | Two apps retain different saved profiles after app/session restart; reset returns to the session default |
| Active rotation | App content and input remain aligned after rotation; interruption is disclosed if unavoidable |
| Optional direct mode | Mapping, swipe, and pinch work after switching modes without changing app or rotation |
| Disconnect/Stop | No leaked virtual displays, stuck inputs, or orphaned monitor overlays |
| Recovery | Reconnect uses current display IDs and offers a clean session restart |
| Unsupported app | A useful error or documented compatibility layout appears; no false success |

Use a purpose-built input test screen to record pointer IDs, mapped coordinates,
scroll, pinch, and navigation events. Real-app checks supplement those tests.

## 12. Implementation sequence

1. Stabilize the prototype lifecycle and add all four transforms with shared
   coordinate-mapping tests.
2. Add fullscreen app picker, session orientation, and per-app saved profiles.
3. Add monitor capability detection, recommended resolution/UI size, desktop
   resolution selection, confirmed monitor profiles, and rollback.
4. Complete touchpad tap, Back, scroll, and native pinch with device validation.
5. Add direct gesture mode if its geometry and gesture conflicts prove workable.
6. Test best-effort physical signal-mode selection; add optional navigation and
   keyboard relay, then expand compatibility testing.

## 13. Boundaries

This is a desktop/input app built on Android and Shizuku, not a replacement OS
window manager. DRM playback, locked-device operation, guaranteed behavior for
all apps, simultaneous independent instances of every app, and automatic sensing
of physical monitor rotation are outside the initial release.

## 14. Display API references

- [Display.Mode](https://developer.android.com/reference/android/view/Display.Mode):
  physical mode dimensions and refresh rate.
- [DisplayMetrics](https://developer.android.com/reference/android/util/DisplayMetrics):
  logical density and reported physical X/Y DPI.
- [VirtualDisplay](https://developer.android.com/reference/android/hardware/display/VirtualDisplay):
  resizing virtual resolution and density separately from the physical signal.
