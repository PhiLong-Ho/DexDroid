# DexDroid â€” App Specification

Status: DexDroid 0.9.1 installed; launcher icon device-verified.
Functional behavior retains the 0.9 verification recorded below.
The current implementation contract below supersedes
historical UI descriptions. Future changes follow `AGENTS.md`: update this spec,
implement, then write/update and run acceptance/E2E tests.

## Current implementation contract

### Minimal launcher icon - 0.9.1

- Replace the colored phone/monitor mark with a minimalist black-and-white icon:
  a recognizable Android robot diving diagonally, head first, into a monitor.
  Use a simple monitor outline/stand, solid robot, transparent eye cutouts, and
  no lettering, gradients, shadows, or accent colors.
- Keep the silhouette legible at launcher size and inside circle, rounded-square,
  and squircle adaptive masks. The foreground and Android 13+ monochrome layer
  use the same artwork; the standard icon has a black background.
- Update the editable SVG, README preview PNG, launcher resources, and version.
- After implementation, verify the packaged adaptive icon and monochrome rendering
  on Android, mask clipping, contrast, and small-size rendering. Inspect the native
  icon visually and install the new build on the connected phone.
- Verified on the connected Android 11 phone: all 29 native icon checks passed
  (installed adaptive resources, identical foreground/monochrome silhouette,
  transparent eyes after tinting, unclipped circle/rounded-square/squircle/square
  masks, grayscale contrast, and 48/64/96 px rendering). Native previews visually
  inspected; README PNG exported from the packaged vector. Build and lint passed
  (0 errors, 34 warnings). Record: `icon-acceptance-v0.9.1.txt`. Functional suites
  were not rerun for this resource-only change.

### Idle desktop - 0.9

- Before an app opens on the current desktop, show a minimal monochrome background
  on the monitor with a centered Apps icon. Rotate/scale the background and 76 dp
  touch target with the desktop. Touch or phone-pointer activation opens the
  existing launcher. Closing a menu before selecting an app returns to idle.
- Hide the shortcut/background after a successful app launch, leaving edge gestures
  available. A failed launch retains idle. A fresh desktop begins in idle again.
  Back without an opened app does nothing.
- Acceptance: real icon touch and pointer activation, centered geometry and rendered
  monochrome pixels in all four rotations and Fit, outside/menu dismissal, failed
  and successful launches, input after launch, and fresh desktop state.
- Android keyboard on monitor is deferred. The snapshot/cropped-keyboard mirror and
  phone search editor prototype are removed: their latency fails the user's
  requirement for performance comparable to normal Android input or the built-in
  launcher keys. Do not ship this toggle or describe the prototype as supported.
  Keep the responsive built-in launcher keyboard. Android 11/JoyUI rejects native
  IME placement on the untrusted virtual display; a future approach must prove
  near-instant rendering/input on this non-root device before inclusion.

- Verified on the connected Black Shark 4 / Android 11 / JoyUI: 341 idle-screen
  assertions, 1,258 search assertions, 554 monitor assertions, 29 phone UI assertions,
  and 237 desktop/input regression assertions passed (2,419 total). Records:
  `idle-acceptance-v0.9.txt`, `search-acceptance-v0.9.txt`,
  `monitor-acceptance-v0.9.txt`, `ui-acceptance-v0.9.txt`, and `device-checks-v0.9.txt`.
  Build and lint passed (0 errors, 34 warnings). The canceled IME prototype's failed
  checks are not included as release verification; its code/toggle/tests are removed.

### Monitor navigation and desktop shell â€” 0.8.2

- The monitor has its own Apps/main screen and control panel, operated without
  bringing the phone controller to the foreground. Remove the persistent dock:
  no Apps/Controls or other shell buttons appear over an open external app.
- Single-finger inward swipe from the right edge toward the left invokes Android
  Back on the external app. The matching inward swipe from the left edge also
  supports Back. Swipe up from the bottom edge opens DexDroid's main screen/app
  launcher; swipe down from the top edge opens its monitor control panel.
  Main screen means DexDroid's launcher; do not send Android Home to the phone.
- Navigation is relative to the upright rendered desktop, not the monitor's fixed
  landscape signal. Use inverse Fit/rotation mapping for 0/90/180/270 degrees and
  selected desktop resolution/DPI. Fit-bar starts never trigger navigation.
- Initial recognition: start within 20 logical dp of an edge, move inward at
  least 60 dp, have primary displacement at least 1.4 times perpendicular movement,
  and cross the threshold within 1.5 seconds. Prioritize top/bottom over side edges at corners.
  Multi-touch, outward/short/primarily perpendicular motion, and canceled input do
  not trigger navigation. Send exactly one action per gesture; cancel any active
  app/UI touch before taking over and consume the remainder until release.
- Ordinary taps/swipes and pinch outside qualifying edge gestures keep native
  behavior. A live Settings toggle enables/disables monitor edge navigation.
  Monitor-touch forwarding can be disabled independently; DexDroid shell controls
  remain usable so the user can re-enable it. Physical input hides the cursor;
  phone touchpad input restores it. Phone pointer/tap/scroll can operate shell UI.
- Monitor launcher matches the phone app picker: Open on monitor title, search,
  tap/hold hint, installed-app icons/names/saved orientation, favorite priority,
  and input test. Replace text Close with a top-right Close icon; remove Resume app.
  Tap launches with saved rotation; hold offers a one-time rotation. Closing leaves
  the external app underneath. Home does not send Android Home to the phone.
- Monitor controls use shared phone styling: DexDroid title; Settings icon immediately
  left of a top-right Close icon; same compact status card and equal icon/label rows
  Apps/Rotate/Scroll above Stop/Save app/Back. Omit the touchpad area and hints.
  Disable automatic dark-mode color inversion for the monitor overlay so the
  highlighted Stop button retains a black label/icon on its white background.
  Reset app stays in Settings. Rotation/scroll selectors show current values and
  use the same service/preferences as the phone controller.
- Both menus float with outside space. Outside taps, including Fit bars, dismiss
  the entire menu and never click through to the app. Inside-card blank taps retain
  it. Close icons dismiss all shell pages; edge Back still pops a submenu/menu
  before sending one Back to the external app. Phone pointer/tap/scroll remain supported.
- Add Android-style feedback: a transient Back chevron/bubble or top/bottom indicator
  follows edge progress, commits on recognition, and fades/retracts on rejection.
  Menus enter/exit with short eased slide/fade/scale transitions from their relevant
  edge. Feedback/animation rotate with the desktop, produce no extra Back, and clear
  on resize, Stop, and disconnect. Native app Back animation is app/ROM-dependent;
  do not claim Android predictive Back support.
- Monitor Settings provides current pointer speed, direction, monitor touch, edge
  navigation, background exemption, display presets/DPI, app/monitor resets, centering,
  and diagnostics. Android-managed background permission opens on the phone and
  is explicitly labeled. Resolution preview Keep/Revert and automatic rollback
  remain available on the monitor. Stop ends the monitor session/UI; resume from phone.
- Shell widgets and hit targets rotate/scale with rendered output, remain native
  Android views, and consume their contacts instead of forwarding clicks into an
  app underneath. Modal menus block underlying input. Phone Back and edge Back
  dismiss an open shell submenu before navigating the external app. Shell state
  survives phone-controller destruction because SessionService owns it.
- Diagnostics record edge candidates, recognized/rejected/canceled outcomes and
  resulting Back/Home/Controls actions, menu transitions, and monitor selections.
- Acceptance/E2E after implementation: real monitor events in all four rotations
  for right-edge Back, bottom Home, top Controls; native edge taps, short/outward/
  vertical/multi-touch rejection and disabled navigation; Fit bars; no stuck contacts
  or underlying menu clicks; real monitor app selection and control actions; phone
  pointer interaction, background controller destruction, settings state, Stop and
  existing native input/rotation/profile regression checks.
- 0.8.1 acceptance after implementation: matching phone/monitor style and six-button
  order/sizes/text contrast (including a PixelCopy check of the rendered Stop label),
  Settings-left-of-Close icon header, launcher search/rotation/selection,
  no dock/Resume, outside dismissal via physical touch and phone pointer without
  click-through, Fit bars and internal taps, live pre-threshold feedback and settled
  transitions in all rotations, one Back per gesture, background lifecycle/regression,
  held-app one-time rotation without overwriting saved settings, and one Keep/Revert
  row in the shared status card during resolution preview.
  Verified on Black Shark 4 / Android 11 / JoyUI with the connected touchscreen:
  554 monitor acceptance checks, 29 phone-controller UI checks, and 237 desktop/input
  regression checks passed (820 total). Results: `monitor-acceptance-v0.8.1.txt`,
  `ui-acceptance-v0.8.1.txt`, and `device-checks-v0.8.1.txt`. Build and lint passed
  (no lint errors; existing warnings remain). The controller and monitor launcher/
  control panel were visually inspected. Other touchscreen associations/ROMs,
  native menu accessibility with assistive technology, and arbitrary apps still
  require separate validation.

### Monitor search keyboard

- Tapping the monitor launcher's search field must focus a real editable field and
  show a usable keyboard. Typing, deletion, spaces, and clearing the query update
  the visible app list; support both physical monitor touch and the phone pointer.
- Keep the keyboard and search usable in all four desktop rotations and Fit/DPI
  configurations. Use the compact native DexDroid search keys, rotating/scaling
  with the launcher widgets. The JoyUI system IME is unsuitable for this rotated
  Presentation, and the experimental selected-IME mirror failed the latency target.
  Do not change global keyboard settings. These keys are for DexDroid app search.
- Provide letters, shift, numbers, spaces, period/hyphen/underscore for package
  searches, delete (including a selected range), Clear, and Hide keyboard. Keep
  the query and filtered results when hiding it; tapping search reopens it.
  Hardware key events may still edit the focused native search field. Use editable
  selection-aware text operations and readable monochrome key labels.
- Opening the launcher alone does not focus its search editor or automatically
  summon a keyboard. Explicit search interaction starts text input.
  Keep the search field, app results, and Close icon reachable while typing.
- Closing the menu by its icon, outside tap, app selection, or edge Back clears its
  text-input focus and dismisses its keyboard. Moving to Controls/settings or a
  one-time app rotation selector also releases launcher text input. Stop, disconnect,
  and display reconfiguration must not leave a keyboard or stale editor behind.
  Hardware Back on the monitor follows shell/app Back navigation and must not
  dismiss the session's Presentation or stop the desktop.
- Acceptance/E2E must tap search through real display input and verify focus,
  keyboard visibility/usability, actual text entry and filtered results, deletion/
  clearing/no-match recovery, and dismissal/reopening. Cover monitor touch and phone
  pointer, rotated/Fit layouts, submenu actions, background operation, and existing
  edge Back/input isolation. Setting EditText text directly is insufficient to prove
  that a user can search. Investigate and fix uncovered UI failures before recording
  the new version as verified.
- Verified on the connected Black Shark 4 / Android 11 / JoyUI: 1,258 focused search
  assertions, 554 monitor assertions, 29 phone-controller UI assertions, and 237
  desktop/input regression assertions passed (2,078 total). Records:
  `search-acceptance-v0.8.2.txt`, `monitor-acceptance-v0.8.2.txt`,
  `ui-acceptance-v0.8.2.txt`, and `device-checks-v0.8.2.txt`.
  The focused suite uses real monitor key taps and display-targeted hardware keys;
  it does not populate queries with `setText`. Full transformed view bounds check
  keyboard/search/results/Close visibility instead of relying on clipped rectangles.
  Cursor/range fixtures verify selection-aware editing, not Android's long-press
  selection/ActionMode UI. Build and lint passed (no errors; existing warnings remain).
  The keyboard and real typed filtering were also inspected visually on the monitor.

### Controller and settings

- Black-and-white theme across the controller and dialogs. Title and Settings
  gear at the top; one compact status panel beneath it, then two equal three-button
  rows, then the touchpad. Monitor status is slightly brighter/bolder than other
  status text. No separate colored monitor banner or persistent gesture instructions.
- First row: **Apps | Rotate | Scroll**. Second row: **Stop/Start | Save app | Back**.
  Stop and Back are swapped relative to 0.7. All six controls have equal dimensions,
  visible labels, accessibility descriptions, and tooltips.
- The status panel shows monitor connection/name, Shizuku availability, desktop
  state, operation messages/instructions, current app, scroll mode/speed, monitor
  touch state, session/saved orientation, and active resolution/UI DPI. Active app
  means the last app selected through DexDroid, not global foreground detection.
- The touchpad info icon opens gesture instructions only on demand. A grouped,
  scrollable bottom Settings sheet replaces the plain options list.
- Settings groups expose display resolution/DPI, monitor touch, pointer speed,
  scroll direction, pointer centering, background operation, diagnostics, and
  app/monitor resets. Pointer speed, direction, monitor touch, background battery
  exemption, and saved-profile state show current values. Toggle changes update
  live; background exemption is system-managed and refreshes on return from Android.
- Pointer speed is Slow/Normal/Fast (1.0/1.6/2.5). Scroll direction is Normal/Reversed.
  Reset app settings is in Settings; Save app remains on the main controller.

### Automatic start, launching, and lifecycle

- Opening DexDroid connects to a running Shizuku server, requests first-use
  authorization, and starts the desktop when an external monitor is available.
  App selection while unavailable/busy queues the latest selection until ready.
- Shizuku itself must be started separately after reboot. Missing/stopped/denied
  authorization states are explained; retry and Open Shizuku appear in Settings
  when the bridge is unavailable. Binder/monitor availability triggers connection
  and startup while the controller service is active and automatic start is requested.
- Stop cancels input, releases the virtual display/presentation/wake lock, and
  changes to Start. Main-screen Stop retains controller focus if Android brings
  a former external app onto the phone. Start, reopening DexDroid, or selecting
  an app resumes startup. The foreground notification also provides Stop.
- A foreground SessionService owns the monitor independently of the phone
  activity. Home, switching apps, and destroying/unbinding the controller preserve
  a running desktop and monitor touch. Force stop, reboot, Shizuku loss, and vendor
  power management can end it. Battery exemption is queried, not assumed granted.
- App picker supports icons, search, favorite priority, tap to launch, and hold
  for one-time orientation. Favorites include Chrome, Firefox, YouTube ReVanced,
  Tachiyomi, the input test, and Portrait Desk Test when installed. Existing tasks
  are reused/moved and put in fullscreen task mode where JoyUI permits it;
  arbitrary app compatibility and DRM playback are not guaranteed.

### Display, profiles, and input

- Four whole-output rotations: 0/90/180/270 degrees, independent of phone rotation.
  Resizing preserves the virtual display ID; rendering, pointer, and inverse monitor
  touch mapping share rotation/Fit geometry. Input is canceled/frozen during changes.
- Android-reported current/supported signal modes, refresh rate, and physical DPI
  are displayed; unreliable DPI is labeled unknown. Physical signal switching,
  arbitrary-angle rotation, and automatic physical-monitor sensing are deferred.
- Recommended/same-as-output/lower-load/custom virtual resolution and UI DPI are
  implemented. Custom dimensions are 480â€“4096 px and DPI 120â€“480. Mismatched aspect
  ratios use Fit bars. Preview offers Keep/Revert and a 15-second rollback; only
  confirmed profiles are persisted, keyed by monitor name/capability fingerprint.
- Orientation precedence: one-time override, saved per-app rotation, session default.
  Save app persists rotation, scroll mode, and speed together. Reset removes that
  app's profile and restores defaults. App selection never changes monitor profiles.
- Phone input: relative pointer movement, tap, two-finger wheel/native swipe scroll,
  native two-pointer pinch, visible Back, and three-finger right-swipe Back.
  Back requires horizontal movement greater than 60 dp and greater than vertical
  displacement when a pointer lifts. No accidental tap after multi-touch.
- One Scroll panel shows current mode and Slow/Normal/Fast speed (20/50/100%),
  persisted immediately per app. Tachiyomi defaults to native Swipe at 20%; other
  apps default to Wheel at 50% unless configured. Speed/direction affect phone
  scrolling; direct monitor swipes retain native behavior.
- Monitor touch is enabled by default. Physical tap/swipe/drag/long press/pinch
  events are mapped into the virtual desktop, preserving IDs, timing, and ordering.
  Fit-bar contacts are ignored/canceled. Monitor gestures cancel/suppress phone
  touch gestures until released; leaving the phone controller cancels only phone input.
- The cursor is hidden initially and after monitor touch; phone touchpad input
  restores it. External touchscreen association remains dependent on Android/ROM.

### Diagnostics and verification

- Settings â†’ Diagnostic log shows a live, bounded 250-event in-memory history with
  Copy/Clear/Close. Events cover connections/errors, ignored input, pointer counts,
  tap/pinch/scroll recognition, three-finger Back recognition/rejection with dx/dy
  and threshold, and Back key queuing/completed Shizuku IPC. No screen/typed content.
  IPC completion does not prove a target app consumed the key.
- `device-checks.txt` records 237 passing 0.7 desktop/input checks; `ui-checks-v0.7.txt`
  records UI verification. Diagnostic test coordinates use absolute display values
  so Android system-bar insets do not distort alignment assertions.
- 0.7.1 acceptance/E2E: verify both rows in visible screen coordinates, equal control
  sizes, Stop/Start on the left and Back on the right; click Stop and verify released
  desktop/controller focus/Start label; click Start and verify readiness; click Back
  and verify the external input test receives it while the phone controller stays open.
  Verified on the connected Black Shark 4: all 29 focused checks passed; build and
  lint passed. Results are recorded in `ui-acceptance-v0.7.1.txt`.
- Deferred: direct gesture mode, Forward, general hardware-key relay, phone drag/long press,
  secondary click, window management, orientation thumbnails, and physical signal
  switching. Existing sections below include target requirements for those features;
  they must not be described as shipped or fully validated.

## Implementation history

- 0.3: external monitor touchscreen forwarding and background session ownership.
- 0.4: pointer visibility follows input source; per-app scroll speed.
- 0.5: per-app mode/speed/rotation save and reset; Tachiyomi native-swipe default.
- 0.5.1: adaptive launcher icon and monochrome themed icon for Android 13+.
- 0.6: automatic Shizuku/desktop connection, queued launching, gesture diagnostics.
- 0.7: monochrome controller, combined scrolling panel, grouped live settings,
  on-demand gesture help, and foreground-preserving Stop.
- 0.7.1: Stop/Start on the second row's left and Back on its right; automated
  visible-layout and Stop/Start/Back acceptance test.
- 0.8: monitor edge navigation, local launcher, control panel, and settings.
- 0.8.1: shared phone-style icon menus, no dock/Resume, outside dismissal, and
  animated edge feedback/menu transitions.
- 0.8.2: monitor search keyboard, real tap/type/edit/lifecycle UI tests, and hardware
  Back handling that preserves the desktop.
- 0.9: centered monochrome idle launcher; slow Android-IME mirror experiment removed.
- 0.9.1: minimal black-and-white Android-diving-into-monitor launcher icon;
  native adaptive/mask/themed/small-size acceptance verified.

## 1. Purpose

Turn a non-root Android phone into a desktop device for an external monitor.
The phone provides touchpad or gesture input. Apps run on the monitor, with
output rotated to match the monitor's physical orientation independently of
the phone's orientation.

Initial target: Black Shark 4, Android 11 / JoyUI, USB-C video output, and a
1920 Ã— 1200 monitor. The monitor does not rotate the incoming signal itself.
Shizuku supplies the required shell privileges without rooting the phone.

## 2. Proven baseline

- A public virtual display can be created through a Shizuku user service.
- A 1200 Ã— 1920 virtual desktop renders rotated across the full 1920 Ã— 1200
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
7. A pointer shown for phone input, hidden for monitor touch, with a center control.
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
   monitor if necessary; resolution and UI size remain adjustable in Settings.
4. Choose the monitor orientation from the Rotate control's four labeled choices.
5. Open the app picker and choose an app. Launch it fullscreen on the virtual
   display, applying its saved orientation or the session default.
6. Use the phone's input surface. Keep Back, app picker, rotation, and input mode
   on the main controller, with Stop/Start on the left and Back on the right of
   the second row. Keep diagnostic logging and reset actions in Settings.
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

For a native 1920 Ã— 1200 monitor:

| Profile | Virtual desktop | Output transform |
| --- | --- | --- |
| Landscape | 1920 Ã— 1200 | 0Â° |
| Portrait | 1200 Ã— 1920 | 90Â° |
| Flipped landscape | 1920 Ã— 1200 | 180Â° |
| Flipped portrait | 1200 Ã— 1920 | 270Â° |

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
  240 DPI is a useful initial UI-size recommendation for this 1920 Ã— 1200 monitor,
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
  shorter desktop edge: `dpi = 160 Ã— shorterPixels / 800`, rounded to a sensible
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
- For the current monitor, examples are 1920 Ã— 1200 and a lower-load 1280 Ã— 800
  desktop, with portrait equivalents 1200 Ã— 1920 and 800 Ã— 1280. A virtual preset
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

### Touchpad mode â€” required and default

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

### Direct gesture mode â€” optional second mode

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

### External monitor touchscreen â€” implemented in version 0.3

The user reports that direct touch on the connected monitor works in screen
mirroring but was unusable with the initial DexDroid virtual desktop. The user
has now requested implementation. The monitor touchscreen is a separate input
source, enabled by default, with an enable/disable control under Settings.

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
- Acceptance: center and corner taps align in 0Â°/90Â°/180Â°/270Â°; drag and pinch work
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
  disconnect, or service destruction. Settings shows the actual Android background
  battery exemption status and provides access to its system permission controls.
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
| Four physical orientations | Upright output fills the physically positioned monitor at 0Â°, 90Â°, 180Â°, and 270Â°, without stretched content or portrait letterboxing |
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
| Controller layout | Apps/Rotate/Scroll above Stop-or-Start/Save app/Back; six equal sizes and no persistent gesture hint |
| Stop focus and resume | Clicking left-hand Stop releases the desktop but retains phone controller focus; the same control becomes Start and resumes the desktop |
| Current settings | Pointer speed, direction, monitor touch, and battery exemption reflect actual values; toggles update live and system values refresh on return |
| Combined scrolling | One Scroll panel exposes selected mode and speed; selections persist per app after reopening |
| Gesture diagnostics | Accepted/rejected three-finger Back includes movement/threshold; completed Back IPC is logged without claiming app consumption |
| Monitor edge navigation | Right/left inward Back, bottom-up launcher, and top-down Controls work in all four rotations without changing the phone app |
| Gesture rejection | Short/outward/perpendicular/multi-touch/Fit-bar/disabled starts do not cause navigation; regular native touches remain usable |
| Monitor shell | Launcher and full status/control/settings panels work with monitor touch and phone pointer, respect profiles, and block clicks into underlying apps |
| Monitor UI parity | Controls share the phone's status card and six icon buttons; Settings sits left of Close; launcher has search/rotation labels/icon Close and no Resume |
| Monitor search input | Real monitor search taps show the rotated native search keyboard; real keys filter apps, edit/delete/clear, preserve hidden queries, and stay usable in all four rotations and Fit/DPI layouts |
| Idle desktop | Centered Apps icon on a monochrome background works with touch and phone pointer in all rotations and Fit; menu dismissal returns to idle, successful launch hides it, rejected launch retains it |
| Search lifecycle | Close/outside/app selection/Back/submenu/reconfiguration/Stop clean up editor and keyboard; phone pointer and background search work; hardware Back preserves the desktop |
| Outside dismissal | Both menus dismiss on outside/Fit-bar taps without app input; internal blank taps retain them; phone pointer dismissal also works |
| Animated navigation | Live pre-threshold feedback causes no Back, recognized Back fires once, indicators/transitions settle in each rotation, and closed apps have no shell buttons |
| Background shell | Monitor navigation, launcher, and controls remain usable after the phone controller is destroyed |

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
