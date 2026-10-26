# Feature Specification: DexDroid External Desktop

**Feature Branch**: `main` (existing application baseline)

**Created**: 2026-10-15 (consolidated specification)

**Status**: Maintained baseline after 0.12.0, including the unreleased plain
status action and Stop-return fix; verification scope is recorded separately
from requirements. Publication follows SC-010/012/013/014 and the release workflow.

**Input**: Give a non-root Android phone an independently rotated external
desktop, with phone touchpad and monitor touch control, consistent menus,
remembered app settings, and verifiable behavior.

**Previous release**: 0.15.0 packages the FR-019 diagnostic improvement
and explicit debugging-off flow after published 0.14.0 audio/setup guidance.
Acceptance targets Xiaomi 14 / Android 16 with a physical monitor, using controlled
non-mutating shutdown tests. Actual complete flag readback and HSBC/PayLah warning
acceptance remain unverified; neither bank compatibility nor a broad OS/OEM
matrix is claimed. Ordinary Stop remains independent of debugging shutdown.

Structure adapted from the [GitHub Spec Kit feature template](https://github.com/github/spec-kit/blob/3238fa9285694dc539aeb9829b337bfd6aed7317/templates/spec-template.md).
Product behavior lives here; [design](docs/DESIGN.md),
[architecture](docs/ARCHITECTURE.md), [quality](docs/QUALITY.md),
[release history](CHANGELOG.md), and [test evidence](testresults/README.md)
are maintained separately. Stable `DX-*` IDs in [requirements.json](docs/requirements.json)
map the requirements below to suites and known gaps; a mapping is not a test pass.
The [feature spec index](docs/specs/README.md) decomposes existing behavior into
reviewable feature contracts without renumbering this baseline or claiming a new
verification run. Use the [device testing runbook](docs/TESTING.md) to reproduce checks.

Future priorities are tracked in the [lightweight product backlog](docs/BACKLOG.md).
Backlog entries are proposals, not implemented requirements or compatibility promises;
promote selected work into this contract before implementation.

**0.15.0 diagnostics increment (FR-019 / DX-DIAG-001):** Settings → Diagnostic log
uses one shared phone/monitor panel. Current version/platform, connection, display,
rotation, Fit, layout and input configuration precede Copy log, Share log and
Clear log, followed by readable selectable history. Actions remain ahead of long
history. Copy/Share freeze one report; Share opens the phone's Android chooser and
failure leaves Copy available. History is memory-only, at most 250 bounded events;
uncontrolled strings, app/profile/display names, typed content, URLs and raw shell/
exception output are omitted from the report. Known gesture measurements and
navigation-key dispatch outcomes remain distinguishable; dispatch does not prove
an app handled input. Current context is separate from historical events. Refresh
does not replace unchanged history or disturb an active text selection. Clear
removes events, retains current context and permits later events. Existing menu
Up/Close/outside behavior is preserved; opening logs does not probe or reconfigure
the device. Release acceptance targets Xiaomi 14 with its real monitor; no new
cross-OEM or banking-app compatibility claim is implied.

Audio acceptance must observe live, advancing separate-UID playback through
foreground/configuration changes. A stopped fixture or stale frame count is not
route evidence. System/Stop restoration is independently checked against the
pre-override preferred-policy baseline; HDMI is not the universal System default.

**Current release candidate**: 0.16.0 adds one saved workspace shortcut
(FR-038 / DX-SHORTCUT-001). Settings → Workspace shortcut saves the current apps,
empty panes, Single/Split direction, ratio, pane order, selection, maximization and
whole-output rotation. A Home screen tap replaces the current monitor arrangement
with that snapshot after validating its apps. Resolution/DPI use the connected
monitor profile; audio/scroll settings remain independent. App-internal pages,
documents and unsaved content are not captured. It adds no general automation API
or plugin runtime. Settings placement and this scope are authorized by the user's
confirmation and subsequent instruction to proceed on 2026-10-24.
Acceptance is scoped to Xiaomi 14 with its real monitor plus required host gates;
other devices/OS versions are unverified for this increment.

## User Scenarios & Testing

### User Story 1 - Start and resume an external desktop (Priority: P1)

As a phone owner, I connect a monitor and open DexDroid to use a desktop without
rooting the phone or repeatedly configuring its connection.

**Why this priority**: Every external-app and input workflow depends on a usable session.

**Independent Test**: Connect a monitor, start and authorize Shizuku, then open
DexDroid; verify desktop readiness, background operation, Stop and restart.

**Acceptance Scenarios**:

1. **Given** an attached monitor and authorized running Shizuku, **When** DexDroid
   opens, **Then** it connects and starts the desktop automatically.
2. **Given** no monitor, **When** DexDroid opens, **Then** it shows a waiting state
   and starts when a supported monitor appears.
3. **Given** missing, stopped or unauthorized Shizuku, **When** connection fails,
   **Then** status explains the state and Settings offers recovery actions.
4. **Given** a running desktop, **When** the phone controller is backgrounded or
   closed, **Then** monitor input and menus continue and the notification offers Stop.
5. **Given** a running desktop, **When** Stop is selected, **Then** the desktop
   and active contacts end, the controller remains usable, and Start becomes available.
6. **Given** a stopped session, **When** Start, an app selection or reopening
   DexDroid requests startup, **Then** the desktop resumes when dependencies are ready.
7. **Given** a backgrounded phone controller, **When** the launcher or notification
   reopens it on display 0, **Then** one controller is reused, receives fresh phone
   input, and retains the same external desktop and working monitor input.

### User Story 2 - Launch apps in the monitor's physical orientation (Priority: P1)

As a monitor user, I select landscape, portrait, or either flipped orientation
and launch an app independently of the phone's rotation.

**Why this priority**: Correct full-output orientation is the central desktop capability.

**Independent Test**: Launch the input test in each orientation and verify its
image, center/corner input, saved orientation and one-time launch override.

**Acceptance Scenarios**:

1. **Given** a 1920x1200 physical output, **When** 90 or 270 degrees is selected,
   **Then** the 1200x1920 upright desktop is rotated into that output proportionally.
2. **Given** any selected monitor orientation, **When** the phone rotates,
   **Then** the phone controller stays portrait and monitor orientation is unchanged.
3. **Given** an app with a saved rotation, **When** it is tapped in Apps,
   **Then** the saved rotation takes precedence over the session default.
4. **Given** a saved app rotation, **When** a held app is launched with a one-time
   rotation, **Then** that choice applies once without overwriting the saved profile.
5. **Given** a busy or unavailable desktop, **When** an app is selected,
   **Then** the latest requested selection is queued until startup is ready.
6. **Given** a rejected secondary-display launch, **When** the launch fails,
   **Then** DexDroid reports failure and retains a usable launcher/idle state.

### User Story 3 - Control apps from the phone or monitor (Priority: P1)

As a desktop user, I point, tap, scroll, zoom and go Back using the phone pad
or the monitor touchscreen without stuck contacts or competing input.

**Why this priority**: App placement alone is insufficient without reliable control.

**Independent Test**: Use observable input targets for phone and physical-monitor
events in all rotations, including Fit bars and simultaneous input attempts.

**Acceptance Scenarios**:

1. **Given** phone touchpad mode, **When** one finger moves or taps,
   **Then** the pointer moves or clicks at its current desktop location.
2. **Given** supported content, **When** two fingers scroll or pinch,
   **Then** wheel/swipe scrolling or native pinch occurs without an accidental tap.
3. **Given** the external input test, **When** Back is pressed or a qualifying
   three-finger right swipe occurs, **Then** the external app receives one Back.
4. **Given** mapped monitor touch, **When** users tap, drag, long press or pinch,
   **Then** coordinates and contact sequencing match the rotated desktop.
5. **Given** a monitor contact, **When** phone input also arrives,
   **Then** monitor input takes priority until release; leaving the controller
   cancels phone contacts without canceling the monitor gesture.
6. **Given** monitor touch, **When** the gesture is handled, **Then** the cursor
   hides; subsequent phone touchpad input restores it.

### User Story 4 - Use matching menus on either screen (Priority: P1)

As a user of either screen, I find the same status, controls and option values
in a minimal black-and-white interface.

**Why this priority**: Consistent navigation prevents conflicting settings and actions.

**Independent Test**: Exercise Apps, Rotate, Scroll, Settings and their child
pages on both surfaces, including keyboard, scroll bottom and selected states.

**Acceptance Scenarios**:

1. **Given** either controller surface, **When** its controls open, **Then** title,
   Settings, status and a single equal Apps/Rotate/Layout/Audio row match the shared design contract.
2. **Given** phone Settings or a submenu, **When** it opens, **Then** it fills the
   app window, keeps its header/Close (and nested Up) fixed and scrolls overflowing content.
3. **Given** phone Apps search, **When** Android's keyboard opens, **Then** Close,
   search and app results remain reachable.
4. **Given** constrained phone height, **When** status/controls overflow,
   **Then** the page scrolls while pad gestures retain control of the desktop.
5. **Given** a monitor menu, **When** an outside or Fit-bar tap occurs,
   **Then** the entire menu closes without a click reaching the underlying app.
6. **Given** an option change, **When** the menu is reopened on either screen,
   **Then** current values and selected states reflect the same authoritative settings.

7. **Given** Settings or any menu/submenu on either screen, **When** its header is
   displayed, **Then** Back, Settings (where present) and Close use plain white icons
   with transparent resting backgrounds, independent 48 dp targets and bounded
   press/focus feedback. Titles keep 8 dp horizontal clearance and ellipsize long
   text without displacing icons; existing navigation and dismissal are unchanged.

### User Story 5 - Navigate directly on the monitor (Priority: P1)

As a touchscreen-monitor user, I use edge gestures to go Back, open Apps or show
controls without bringing the phone controller forward.

**Why this priority**: The external desktop must remain independently operable.

**Independent Test**: Inject real edge and non-edge contacts in all orientations;
verify one action, app-input isolation, rejection and settled feedback.

**Acceptance Scenarios**:

1. **Given** an open app, **When** an inward swipe qualifies from either side,
   **Then** one Android Back is sent relative to the upright desktop after release.
   Crossing the threshold previews/recognizes the action; cancellation before
   release sends no Back.
2. **Given** an open app, **When** bottom-up or top-down edge navigation qualifies,
   **Then** Apps or Controls opens as a fresh root respectively; the phone receives no Home action.
3. **Given** a short, outward, perpendicular, multi-touch or Fit-bar start,
   **When** it fails recognition, **Then** no navigation occurs and feedback settles.
4. **Given** an open submenu, **When** Back is requested, **Then** it navigates
   the shell before sending Back to the external app.
5. **Given** no app has successfully opened in this desktop, **When** the idle
   Apps icon is activated by touch or phone pointer, **Then** the launcher opens.
6. **Given** a successful app launch, **When** menus close, **Then** no permanent
   Apps/Controls dock or Resume button obscures the app.

### User Story 6 - Search apps with a usable monitor keyboard (Priority: P1)

As a monitor user, I type an app name directly in its launcher search without
depending on a delayed phone-keyboard mirror.

**Why this priority**: Search must work where users interact with the launcher.

**Independent Test**: Tap search, type/edit/hide/reopen using monitor keys, phone
pointer and hardware text keys; repeat across rotations and display layouts.

**Acceptance Scenarios**:

1. **Given** Apps just opened, **When** no search interaction occurs,
   **Then** a keyboard is not summoned automatically.
2. **Given** explicit search focus, **When** local keys edit the query,
   **Then** the native field and matching name/package results update together.
3. **Given** a query or selected text, **When** Delete, Clear, Shift or character
   input occurs, **Then** editing respects the selection and requested character.
4. **Given** an existing query, **When** Hide then search is tapped,
   **Then** the keyboard reopens with the query and results preserved.
5. **Given** active search, **When** the user leaves/closes the launcher or the
   display stops/reconfigures, **Then** keyboard/editor state is cleaned up.

### User Story 7 - Configure display and per-app behavior (Priority: P2)

As a monitor owner, I select readable resolution/UI size and save each app's
rotation and scrolling without losing a working display configuration.

**Why this priority**: Safe personalization improves compatibility and readability.

**Independent Test**: Preview native, lower-load and Fit configurations; exercise
Keep/Revert/timeout and two different app profiles.

**Acceptance Scenarios**:

1. **Given** monitor capabilities, **When** display settings open, **Then** signal
   modes, virtual resolution and logical UI DPI are distinguished; implausible
   physical DPI is unknown.
2. **Given** no valid saved profile, **When** settings are recommended,
   **Then** a reliable preferred mode or the working current output is used.
3. **Given** a display preview, **When** it is rejected or unconfirmed for 15
   seconds, **Then** the last working resolution and DPI return.
4. **Given** a preview, **When** Keep is selected, **Then** the confirmed monitor
   profile is remembered without overwriting per-app rotation.
5. **Given** two apps with different settings, **When** Save app or Reset app is
   used, **Then** only that app's rotation/scroll profile changes.
6. **Given** Tachiyomi without an override, **When** it launches,
   **Then** scrolling defaults to Swipe at 20%; other apps default to Wheel at 50%.

### User Story 8 - Diagnose and maintain predictable behavior (Priority: P2)

As a user or contributor, I inspect gesture/connection outcomes and trace each
change to requirements, checks and reviewable evidence.

**Why this priority**: Failures and coverage limits must be understandable and reproducible.

**Independent Test**: Trigger accepted/rejected gestures, inspect/copy/clear the
log, then validate a change's requirement references and evidence layout.

**Acceptance Scenarios**:

1. **Given** diagnostic logging, **When** a three-finger Back is accepted/rejected,
   **Then** its movement and threshold are visible without typed/screen content.
2. **Given** completed key dispatch, **When** diagnostics reports it,
   **Then** it distinguishes dispatch from proof that an app handled the key.
3. **Given** a requested change, **When** it is developed, **Then** spec updates
   precede code, acceptance updates follow code, and actual evidence/gaps are recorded.
4. **Given** project documentation, **When** a reader needs setup, release changes
   or evidence, **Then** concise README, changelog and versioned results provide
   distinct, linked entry points with no broken local references.
5. **Given** an added, changed or removed test, **When** checks run, **Then** the
   requirement coverage report must be refreshed with its test inventory and
   source fingerprint; scenarios, evidence and uncovered gaps stay explicit.
6. **Given** a completed candidate, **When** publication is requested, **Then** two
   fresh independent agents review its spec, implementation, tests and evidence.
   Both must approve the same candidate before pushing or running a release;
   later content changes require two fresh reviews again.

### User Story 9 - Download a versioned Android release (Priority: P2)

As a user or maintainer, I download an installable APK and an app bundle from a
GitHub release and see a concise summary of that version's changes.

**Why this priority**: Reproducible distribution makes builds accessible without local tooling.

**Independent Test**: Run the release workflow against a matching version tag;
inspect the published assets, signatures, checksums and changelog-derived notes.

**Acceptance Scenarios**:

1. **Given** a pushed version tag matching the app and a nonempty changelog entry,
   **When** checks pass, **Then** signed APK/AAB files and checksums are attached
   to the corresponding GitHub release with a short version summary.
2. **Given** a mismatched/malformed tag, missing notes, missing signing inputs or
   failed checks, **When** release automation runs, **Then** no release is published.
3. **Given** a publication interrupted at draft stage, **When** it is retried,
   **Then** the draft can complete; an already published release is not overwritten.
4. **Given** a new version, **When** it is built, **Then** the persistent release
   certificate is reused and private signing material stays outside Git/artifacts/logs.
5. **Given** a successful release, **When** users open its notes, **Then** a linked
   Markdown release report shows requirement coverage and gaps, actual test counts
   and pass rates with explicit denominators, skipped/unrun checks, and changelog,
   release, source and workflow evidence links. A missing or invalid report blocks
   publication; historical device results are never counted as current passes.

### Edge Cases

- Monitor disconnect/reconnect: cancel input, remove stale surfaces/IDs and
  rediscover the current display before restarting. Never inject into a stale ID.
- Shizuku death, denied permission, reboot or Force stop: report dependency loss;
  require supported recovery rather than promising an uninterrupted session.
- Fit bars: ignore starts outside content and cancel contacts that leave it.
- Rotation/resolution changes during contact: cancel/freeze input, update image
  and hit mapping together, and keep app tasks when supported.
- Unsupported app, DRM, orientation/resize restriction or task placement: disclose
  failure/compatibility limits without clearing data or claiming successful placement.
- Long names, empty results, large text, selected/disabled rows and IME resizing:
  retain readable text, scrolling and reachable actions; list untested variants.
- Indistinguishable monitor identities share a binding; expose manual selection
  and forgetting while retaining the named profile collection.
- Simultaneous input, extra pointer lifts and canceled gestures must never leave
  contacts held, produce an accidental tap or trigger duplicate navigation.
- Save/reset with no selected app must not corrupt another app's preferences.
- Named monitor profiles retain resolution, UI DPI and default rotation. Applying
  one requires Keep/Revert before changing the monitor association. Disconnect,
  Stop, timeout or a failed resize must retain the previous association.
- Monitor identity uses an available stable physical identifier, otherwise the
  reported name/mode fingerprint. Indistinguishable fingerprints share a binding;
  show this limitation and allow manual switching/forgetting without data loss.

## Requirements

### Functional Requirements

- **FR-001**: Phone/monitor controls MUST share monochrome surfaces, spacing,
  typography, vector icon actions and live values. All menu headers MUST use plain
  white 24 dp icons with transparent resting backgrounds, bounded feedback and
  independent 48 dp targets; one-line titles MUST retain 8 dp horizontal clearance
  and end ellipsis. Existing Up/Close navigation MUST be preserved. The primary actions MUST be one
  equal-size row of Apps/Rotate/Layout/Audio. Desktop Start/Stop MUST be a stateful icon
  in the status card, using a plain white Play/Stop icon with a transparent resting
  background, no circle/border, a 48 dp independent target and bounded pressed ripple.
  Save/Reset app MUST live in Settings > App settings;
  scrolling is also available there on both screens. The phone touchpad header MUST place a
  Scroll icon left, a centered group containing Touchpad then Info, and remote
  Back at the right. Info MUST sit beside the label it explains, with independent
  hit targets; the complete label/Info group is centered between opposing actions.
  Header taps MUST NOT generate pad gestures; visual icons retain at least 48 dp
  targets. The monitor omits the touchpad; Settings stays in its controls header.
  Status defaults to a collapsed summary of monitor state and its desktop
  action, with no triple-dot app-options shortcut. The monitor-status area MUST
  be a keyboard/accessibility-operable expand/collapse target at least 48 dp high,
  independent of Start/Stop and exceptional action buttons. A leading-aligned
  secondary text cue MUST read "Show details" when collapsed and "Hide details"
  when expanded, directly beneath the monitor summary inside the existing header.
  FR-038 permits a temporary restore notice before the collapsed "Show details" cue.
  The cue shares the status target; it MUST NOT add an arrow, separate button or
  disclosure row. Start/Stop remains the sole trailing icon. A normal collapsed
  card MUST fit within 80 dp with independent 48 dp action targets; long monitor
  names and larger text may grow without clipping.
  Expanded details retain Shizuku, desktop, operation, selected app, scrolling,
  touch, rotation/saved state and resolution/UI
  DPI. The shared expansion preference survives menus/recreation/restart and
  synchronizes both surfaces. Collapsing MUST reduce panel height and enlarge the
  phone pad where space permits. Preview Keep/Revert, protected-content guidance
  and actionable Shizuku recovery MUST remain visible when needed. (DX-UI-001)
- **FR-002**: The phone controller MUST remain portrait. Settings and submenus MUST
  fill its app window with fixed headers, scrollable overflow and IME-safe Apps
  results. Phone visibility is independent of keyboard focus on a managed display;
  opening visible phone controls MUST NOT require taking focus from an external app.
  Main-page scrolling MUST preserve touchpad gestures. Nested menus MUST
  have a leading Back/Up arrow and a separate trailing Close icon. Back/Up and
  system Back return one visited menu level; Close dismisses the entire menu flow.
  System Back first dismisses an open keyboard. Returning preserves parent Settings
  scroll position and Apps query; opening a main shortcut starts a fresh root flow.
  Selecting Monitor orientation or Pointer speed applies the value and returns to
  its parent (or controller at a root), without duplicate history or stale picker
  adoption. Multi-setting/action pages (Scrolling, Layout, Settings and App settings)
  stay open for successive adjustments/actions; Up/Back returns when finished.
  Pending display Keep/Revert
  controls remain reachable beneath the menu header after parent navigation, with
  one confirmation area per surface/menu. (DX-PHONE-001)
- **FR-003**: Labels, punctuation, glyphs and icon descriptions MUST remain readable
  without encoding corruption. Source/document encoding MUST be UTF-8. (DX-TEXT-001)
- **FR-004**: The launcher icon MUST depict a monochrome Android robot diving into
  a monitor, with legible adaptive/themed/small-size silhouettes and transparent
  eye cutouts. (DX-ICON-001)
- **FR-005**: A fresh or newly empty desktop MUST show a minimal background and
  centered Apps icon, covering any retained app frame. Failed launches retain idle;
  successful launches hide it while app tasks remain. (DX-IDLE-001)
- **FR-006**: Monitor Apps MUST offer local selection-aware search keys for letters,
  Shift, digits, spaces, period/hyphen/underscore, Delete, Clear and Hide; support
  touch, phone pointer and hardware text keys; preserve hidden queries and clean
  up focus on exit. No phone IME mirror or global IME changes ship. (DX-KEYBOARD-001)
  Search hardware Back MUST be handled before phone IME processing: first hide
  visible local keys, then navigate one shell level or dismiss a root on a later
  valid release. A cancelled release MUST not navigate or hide keys; no handled
  Back reaches the underlying app.
  Dismissing local keys MUST also hide any served system IME and focus a
  non-editor on that menu, preserving query/results. A subsequent distinct Back
  MUST navigate without an extra press being consumed by the phone IME.
  Noninteractive shell focus MUST NOT tint the desktop or change the menu scrim;
  actionable controls retain their normal focus feedback.
  Monitor Apps search MUST retain native editable selection, accessibility and
  hardware text/editing keys without supplying a system IME input connection.
  Hardware typing MUST NOT summon a system keyboard on either screen or consume
  the first Back in an unrelated IME. Phone search and managed-app IMEs retain
  their existing behavior.
- **FR-007**: Upright-relative side-edge Back, bottom Apps and top Controls MUST
  work in all four rotations. Recognition starts within 20 logical dp of an edge,
  moves inward at least 60 dp with 1.4x dominant displacement within 1.5 seconds,
  and prioritizes top/bottom at corners. Exactly one action occurs. (DX-NAV-001)
- **FR-008**: Invalid/disabled/multi-touch edge starts MUST not navigate. Modal
  shell contacts MUST not reach the app; outside/Fit taps dismiss without
  click-through, while blank inside taps retain the menu. Back navigates shell
  pages first. App-touch and edge-navigation toggles are independent; shell
  controls remain operable when app-touch forwarding is disabled. Nested monitor
  pages MUST show a leading Back/Up arrow and a trailing Close icon. Back returns
  one valid visited page; Close/outside/Fit dismissal closes the whole overlay.
  Back first hides a visible local search/name keyboard. Parent scroll positions
  and Apps queries survive return; obsolete picker destinations are skipped, and
  root flow, Stop and teardown clear navigation ownership. (DX-NAV-002)
- **FR-009**: Edge progress and menu transitions MUST provide short, settling
  feedback, cancel app contact before takeover, and clear on Stop/reconfiguration.
  App/ROM Back animation is outside this guarantee. (DX-NAV-003)
- **FR-010**: Phone pad MUST support relative movement, tap, two-finger wheel/swipe
  scroll, native pinch, visible Back and three-finger right-swipe Back. Three-finger
  recognition requires more than 60 dp horizontal movement and dominance over
  vertical movement on a pointer lift. Pointer speed is 1.0/1.6/2.5; direction is
  configurable. Pointer/pinch contacts remain within desktop bounds. (DX-INPUT-001)
- **FR-011**: Monitor touch MUST default on, map tap/drag/long press/pinch through
  rotation/Fit, preserve IDs/timing/order, take priority over phone input and avoid
  recursive relay. Monitor input hides the cursor; phone input restores it.
  Canceled/disconnected sessions MUST release contacts. (DX-INPUT-002)
- **FR-012**: Scroll mode and 20/50/100% speed MUST show current values and persist
  per app immediately. Tachiyomi defaults to Swipe/20%; others Wheel/50%. Direction
  and speed affect phone scrolling, not native monitor swipes. (DX-SCROLL-001)
- **FR-013**: Launch rotation precedence MUST be one-time choice, saved app choice,
  then session default. Save app stores rotation/mode/speed; Reset app restores
  defaults. One-time launch MUST not overwrite saved preferences or a monitor
  profile. Only DexDroid-selected apps receive these launch rules. (DX-PROFILE-001)
- **FR-014**: Display settings MUST distinguish Android-reported physical modes,
  virtual resolution and logical UI DPI. Unknown/implausible physical DPI MUST be
  labeled unknown. Recommendation restores a valid confirmed profile, otherwise
  uses reliable preferred/current output with a bounded UI-size recommendation;
  largest advertised mode MUST not be assumed native. (DX-DISPLAY-001)
- **FR-015**: Users MUST select 0/90/180/270-degree whole-output rotation, desktop
  dimensions 480-4096 px and UI DPI 120-480. Portrait swaps virtual dimensions;
  Fit preserves aspect ratio. Preview MUST offer Keep/Revert and automatic
  15-second rollback; only confirmed monitor profiles persist. Input/image mapping
  updates together, preserving the display/task where supported. (DX-DISPLAY-002)
  Shell validation MUST reject nonzero exits and actual line-leading `Error:`
  messages, while accepting valid embedded metrics such as `mError: 0 ns`.
  Unknown compositor state and output-detachment failures remain fail-closed.
  Resize and rollback diagnostics MUST preserve bounded operation/failure causes
  without embedding entire command reports.
  Settled post-configuration input acceptance (rotation, Fit and resolution
  preview/revert) MUST verify the captured app task and
  current resized native input window, with no overlapping transition snapshot or
  unknown occluder above it, before sending one contact. View/display dimensions
  alone do not establish readiness; early transition contacts remain diagnostics.
  Readiness polling MUST NOT retry contact, extend delivery deadlines or replace
  coordinate, rejection and automatic rollback assertions.
- **FR-016**: Desktop, monitor touch and menus MUST survive normal phone Home/app
  switches/controller destruction. Returning through the launcher/notification MUST reuse a single phone controller,
  restore responsive phone controls, and retain desktop/input continuity.
  Background status MUST reflect actual system
  exemption and refresh on return. Explicit Stop or dependency loss ends session
  resources. Reboot/Force stop survival is not promised. (DX-LIFE-001)
- **FR-017**: Opening DexDroid MUST connect to available Shizuku and request desktop
  startup, waiting for a monitor as needed. First-use authorization and missing/
  stopped/denied states MUST be explained. Latest pending app selection is queued.
  Explicit Stop MUST return the existing phone controller to the foreground after
  both display cleanup operations, Android task migration and the resulting window/input
  transition complete, including single/split apps and slow
  cleanup. Returning focus MUST survive late third-party app resume requests: in
  single view, launch Chrome, press phone Stop and leave the phone untouched;
  DexDroid MUST remain the focused phone Activity for at least 15 seconds without
  opening a menu or receiving another controller launch. Repeat from a reused
  Chrome task. This acceptance MUST run without target app instrumentation, which
  can exempt background task return from normal platform restrictions. The
  authorized shell MAY focus only the captured existing display-0 MainActivity
  task after confirmed cleanup; arbitrary, shared or restarted desktop tasks MUST
  be rejected. It MUST remain stopped and its menus/Start MUST work. Restoration is
  scoped to that stopped generation and live controller owner; a newer Start,
  released/replaced UI owner or teardown invalidates it.
  Shell-side window/input transition confirmation MUST acquire the window service
  through its Binder interface without requiring application-global initialization
  in the Shizuku helper. Missing services, transaction failures and unconfirmed
  removal MUST remain explicit cleanup failures on Android 11 through 17.
  An app-close warning MAY fall back to a display release that confirms task
  migration. Failed final input/display cleanup or unconfirmed task migration in either pane MUST
  suppress foreground restoration and report cleanup uncertainty with phone-reopen
  recovery guidance; obsolete failures MUST NOT replace a newer session status.
  Passive disconnect/dependency loss MUST NOT foreground the controller. Do not
  close unrelated phone tasks or create a controller if none exists.
  Start/reopen/launch can resume. An Activity
  monitor host that has not attached within five seconds MUST fail startup with
  session-scoped teardown, release the wake lock and show foreground-reopen recovery
  guidance. Stop/attachment MUST cancel that deadline; stale deadlines and late
  hosts MUST not attach to or stop a newer session. Rejected service start or
  foreground promotion MUST be caught by the same startup recovery boundary,
  clearing busy/resources and offering foreground retry without a main-thread
  crash. Reopening the existing phone controller through the launcher MUST retry
  failed startup without requiring a new service binding. Recovery acceptance
  MUST verify the same visible bound phone controller with native Settings/Close
  input and fresh monitor/app input, without requiring simultaneous window focus
  on the phone and independent monitor host. Explicit Stop retains its strict
  phone foreground/focus acceptance above. Resuming a controller
  with a pending desktop request MUST retry; focus restoration after explicit
  Stop MUST keep the desktop stopped until Start, app launch or launcher reopen.
  (DX-LIFE-002)
- **FR-018**: Apps MUST show launchable icons/names, name/package search, favorite
  priority and one-time orientation. Launches target fullscreen external placement,
  reuse/move tasks where supported and preserve app data. Existing Chrome, Firefox,
  ReVanced, Tachiyomi and test apps appear when installed. Failures are visible.
  Monitor Apps MUST have icon Close and no Resume footer/dock. (DX-LAUNCH-001)
- **FR-019**: Diagnostics MUST show up to 250 in-memory events with live refresh,
  Copy/Clear/Close, connection/errors, input and accepted/rejected navigation
  details. No typed/screen content is recorded; dispatch completion MUST not be
  presented as proof of app handling. Settings MUST expose current pointer,
  direction, touch, navigation, background and profile values. (DX-DIAG-001)
- **FR-020**: Users MUST create, name, rename, update and delete multiple monitor
  profiles containing desktop dimensions, UI DPI and default whole-output rotation
  (0/90/180/270). Both surfaces MUST expose the same profile list/current binding
  and actions in the shared Settings style. Names must be nonempty, bounded and
  unique ignoring case. Typing at the name limit MUST remain safe on both keyboards.
  Editing a profile MUST be explicit; app overrides and
  unrelated profiles remain unchanged. (DX-MONITOR-001)
- **FR-021**: Applying a named profile to a connected running desktop MUST use the
  existing 15-second Keep/Revert preview for all saved display values. Only Keep
  remembers the current monitor's selected profile. Revert, timeout, resize
  failure, disconnect and Stop MUST preserve the old binding and cancel pending
  changes. Rejected concurrent operations MUST leave settings untouched. Deleting
  a profile clears its bindings without resizing the active desktop. Stop MUST
  release the shell virtual display before releasing its backing texture, without
  blocking the UI thread. (DX-MONITOR-002)
- **FR-022**: Startup/reconnect MUST reapply a valid saved monitor binding, retaining
  session-scoped display IDs only in memory. Prefer an accessible stable physical
  identity, falling back to name/sorted capabilities with the limitation shown.
  Unrecognized/invalid/missing profiles use safe output defaults. Migrate valid
  legacy per-monitor settings once without replacing named profiles. Forgetting
  a monitor removes its binding/legacy fallback and confirmed working settings,
  retaining named profiles. Ordinary Resolution/DPI Keep MUST persist a separate
  per-monitor working configuration across restarts, including after Forget or
  migration, without modifying a named snapshot. Applying/saving a profile clears
  this working override; explicitly updating the bound profile clears the current
  monitor's override. Successful manual default rotation MUST also update this
  monitor's working configuration so the choice survives restart, without editing
  the named snapshot. Rotation while stopped MUST preserve the currently detected
  monitor's remembered/recommended dimensions and DPI, never values left over
  from another monitor. Saved
  app/one-time launch rotation still takes precedence over the monitor default.
  Profile selection MUST be visible in status on both surfaces. (DX-MONITOR-003)
- **FR-023**: Android 16 and 17 compatibility MUST be exercised on identified
  emulator builds while retaining Android 11 as the minimum OS. The application
  MUST target API 36 or later, declare its foreground session use correctly,
  keep phone/menu controls clear of system bars/cutouts and preserve Back actions.
  Notification/controller recovery MUST open on the phone's default display.
  Successful external-app launch MUST verify placement using the platform's task
  information, hide the idle background without opening a menu, and expose the
  rendered app and input without a menu workaround. Unsupported/failed placement MUST report an
  error and retain usable controls without falsely reporting success. Legacy and
  modern task formats and fullscreen APIs MUST be supported explicitly.
  Shell input initialization MUST support the modern input-manager singleton
  and the legacy Android 11 API without granting application root privileges.
  On modern Android, displays that permit Activity tasks MUST use an independent
  Activity host, retain app/input when the phone task is destroyed, reject stale
  hosts and close on Stop/disconnect. Presentation-only OEM displays MUST use a
  Presentation fallback after checking public Activity launch eligibility; they
  MUST NOT fail desktop startup merely because Activity hosting is forbidden.
  The fallback MUST report its host limitation: Android may remove its window
  when the associated phone task is destroyed. Background and task-destruction
  outcomes MUST be tested and recorded separately for each host type. If Android
  removes a fallback window, its desktop/input resources MUST stop cleanly and
  reopening the phone controller MUST recover a usable desktop. Public immersive/side-gesture exclusion APIs MUST keep
  rotated monitor navigation usable in both Activity and Presentation hosts while
  respecting mandatory system gestures.
  Its recognition band MUST include the inner edge beside reserved system bars;
  gestures starting in Android-owned bars remain system actions. Edge navigation
  MUST commit after physical release, with the input dispatch completed before
  sending app keys; canceled gestures MUST NOT execute pending navigation.
  On Android 12+, the authorized shell MUST request a trusted virtual desktop
  using its existing ADD_TRUSTED_DISPLAY permission so a separately installed app
  can open its own non-exported child Activity on that desktop. Child navigation
  and Back MUST retain the desktop display, selected rotation and app state,
  including when the phone controller is backgrounded. The app MUST NOT obtain
  this privileged permission itself, grant it to third-party apps, or repeatedly
  steal tasks from the phone. Android 11 display creation MUST retain its legacy
  flags. On Android 14+, the trusted desktop MUST retain its own focus so returning
  to the phone controller restores phone input without unfocusing the desktop.
  A rejected trusted-display request MUST report a creation error.
  Acceptance MUST use a different-UID app with a private child Activity, not only
  DexDroid's own Input Test; observe placement, visible pixels and delivered input.
  Acceptance using the Parent/Reader fixture family MUST establish a fresh fixture
  once during initial setup, before any controller/scenario app launch, by removing
  only captured, unambiguous roots containing one known Parent/Reader fixture task,
  verifying removal before launch. Child and full split acceptance MUST share
  this guarded setup; isolation MUST NOT run within navigation/handoff or after
  a failure. Later navigation/relaunch MUST preserve state.
  Native menu acceptance MUST capture ready owner/root/search together on main,
  set its query once, and reject owner/search replacement while waiting for its
  original action. Readiness and action discovery MUST share the original suite
  deadline; acceptance MUST NOT retry the user's input to hide a missing target.
  Display-targeted native test input MUST run through an isolated debug-only shell
  driver and Android InputDispatcher, preserving actual widget/relay delivery and
  continuous gesture timestamps without UIAutomation animation waits. It MUST
  reject default/desktop display targets, remain absent from release builds and
  leave the app-under-test hidden-API policies unchanged.
  Modern teardown MUST remove tasks only from its own virtual display before
  releasing that display; APK upgrades MUST refresh the shell-service code.
  Fresh-install target metadata and first-launch warning observations MUST be
  retained; emulator results do not certify OEM/Play Protect warnings, physical
  HDMI, or all third-party apps. (DX-COMPAT-001)

- **FR-024**: Phone and monitor Settings MUST show the installed app's version
  name and build number immediately below the header. Both use one shared style
  and value source; debug builds MUST identify themselves. This information is
  read-only and visible without opening another submenu. (DX-VERSION-001)

- **FR-025**: When an app moves from the virtual desktop to the phone, exits, or
  loses its last desktop task, DexDroid MUST observe actual task occupancy and
  restore the existing idle launcher over retained pixels within two seconds
  after the task transition settles. It MUST cancel held desktop contacts, hide
  the pointer, clear the absent app selection, and keep edge menus usable. An
  unrelated app remaining on the desktop MUST stay visible. Reopening an app
  through DexDroid MUST restore external placement and hide idle only after
  successful placement. Task observation MUST be event-driven, coalesced, scoped
  to the current display/session, stopped on teardown, and perform no Binder
  query on the UI thread. Query failure MUST be diagnosed without falsely
  declaring the desktop empty. DexDroid MUST NOT force phone tasks back or promise
  independent simultaneous instances of apps that reuse one task. A delayed launch
  result MUST publish success or failure only to the same live generation, display,
  bridge and single/split owner that submitted it. Stop, restart or a switch to
  split MUST NOT be followed by stale launch status or restored app occupancy.
  (DX-HANDOFF-001)

- **FR-026**: Apps on both phone and monitor MUST open the shared menu shell
  promptly without querying packages or loading icons on the UI thread. While
  no app list is ready, it MUST show a small monochrome indeterminate indicator
  and Loading apps text, with Close/Back/outside dismissal still responsive.
  Both surfaces MUST share a session-owned cached catalog and one in-flight load.
  Warm opens MUST reuse the cache. Installed/removed/changed packages invalidate
  the cache; refresh MUST retain the search query and show a pending state. Load
  failures MUST show a retry action rather than an indefinite spinner. Dismissed,
  rebuilt or destroyed menus MUST unsubscribe; late callbacks MUST not reopen
  or mutate obsolete UI. Icon drawables MUST not share mutable sizing across
  screen densities. Cold/pending and warm accepted-action-to-draw timings MUST
  be recorded with explicit sample counts and p50/p95 scope. (DX-CATALOG-001)
- **FR-027**: Protected app windows, including Chrome incognito where Android
  enforces FLAG_SECURE, MUST be documented as unsupported by the non-secure
  desktop transport. DexDroid MUST NOT disable app privacy settings, grant itself
  privileged secure-capture permissions, or claim to render protected content
  without a verified supported path. A Chrome-provided screenshot opt-in may
  only be changed after explicit user choice explaining screenshots/thumbnails;
  any resulting verification MUST state that privacy configuration. (DX-SECURE-001)
- **FR-028**: While the desktop contains an app, the authorized shell MAY inspect
  visible-window security metadata at a bounded two-second interval off the input
  and UI threads. Detection MUST be restricted to the current virtual display,
  require a visible surfaced FLAG_SECURE window, ignore phone/hidden windows and
  treat unavailable/unrecognized metadata as unknown, never as incognito proof.
  The complete window-list header MUST also be recognized when OEM diagnostic
  output omits the preceding newline; incomplete window fields remain unknown.
  A complete `isOnScreen` field MUST be recognized on its own line and on the
  native seamless-rotation diagnostic line used by Android 14. Missing, malformed,
  truncated, repeated or conflicting visibility fields MUST remain unknown;
  unrelated text and other visibility fields MUST NOT establish visibility.
  Unknown retains the last confirmed state until a valid observation or teardown.
  No screenshots, page text, URLs or window titles may be retained or sent to UI.
  A confirmed protected window MUST show a minimal dismissible monitor notice and
  a phone status Help action. Help MUST reuse the shared menu style on both
  surfaces, explain the transport limitation and optional Chrome flag, its global
  screenshot/thumbnail consequence, undo steps and possible absence/policy limits.
  The guide MUST also be reachable from Settings when detection is unavailable.
  Dismissal MUST last for that protected episode, ordinary windows MUST clear the
  notice, and stale display/session callbacks MUST be ignored. Stop/disconnect
  MUST stop inspection. No browser settings may change automatically. Direct
  monitor/phone-pointer Help and outside dismissal MUST not click through to the
  app; controls/Back must remain available in four rotations. Acceptance must finish
  after desktop teardown, with no pending virtual-surface recreation when the
  instrumentation process exits. (DX-PROTECTED-001)
- **FR-029**: Android 11 direct monitor touch MUST not clear an app's pressed
  state by changing legacy global window focus between relayed DOWN and UP.
  Targeted monitor input and child/parent navigation MUST preserve the phone's
  current foreground Activity. Input readiness MUST focus an existing task on its
  owned display without performing a Recents launch or bringing phone Home forward.
  Before accepting a newly ready target, task/display/component/UID identity MUST
  still match the captured target; stale work is discarded for a later fresh contact.
  Legacy key admission MUST accept a visible owned native window after focusing
  that task even when the ROM reports keyboard focus only on the phone. Cancellation
  ownership remains strict; this does not authorize replay to a departed target.
  In split view, child Up/Back and root Back/finish MUST affect only that pane;
  another pane and the desktop remain live, with empty-pane Apps/relaunch available.
  When the physical host is awaiting focus, initial contacts MUST be buffered
  off the app input stream until the host's focus callback, with a 100 ms bounded
  settling fallback for OEM Presentation hosts that do not receive that callback.
  The buffer MUST be bounded, coalesce moves, preserve original contact ordering
  and timestamps and never block the UI. Overflow, gesture takeover, a busy or
  obsolete desktop and teardown MUST discard pending input and prevent stray UP
  events; no automatic tap retry. Edge takeover MUST cancel an already-relayed app
  contact or discard the entire unreleased buffer, without delivering an app UP.
  Buffered physical contacts MUST immediately own
  input arbitration: phone touch, pointer and wheel input cannot enter during the
  focus wait. Every shared monitor-input cancellation MUST discard queued contacts
  and its timeout, including navigation/touch-setting changes before release.
  Record the actual relay delay for diagnostics.
  Android versions with independent display focus retain their existing path.
  Acceptance MUST include semantic reader clicks, four rotations, phone background,
  normal direct touch, multi-touch and monitor menus. (DX-FOCUS-001)

- **FR-030**: An explicitly launched, debug-only two-pane feasibility prototype
  MAY run two independent app displays side by side without changing the normal
  product launcher, desktop flow or saved preferences. Pane selection MUST route
  forwarded keyboard events (including modifiers/repeats), pointer clicks,
  dragging and wheel events to the selected/contact-owning pane; a contact MUST
  not jump between panes. Resize MUST change the app's actual display dimensions.
  Stop/host loss MUST release both displays and pending input. Record native
  injected-event checks separately from physical keyboard/mouse/touchpad evidence;
  no hardware reliability or Android-version compatibility is inferred from a
  simulated event or emulator. See the [prototype contract](docs/specs/two-pane-prototype.md).
  This experiment does not ship in release APKs. (DX-SPLIT-001)

- **FR-031**: Layout on both phone and monitor MUST offer single view and two
  managed app panes, left/right or top/bottom, with a 25–75% adjustable divider,
  equal/one-third/two-thirds presets, Swap, and fullscreen/restore of the selected
  pane. Resizing changes actual app display dimensions. Close app clears only the
  selected pane; End split explicitly keeps pane 1 and closes pane 2. Starting
  another app targets the selected pane; choosing an app already in the other
  pane selects it rather than stealing its task. Empty panes offer the existing
  Apps picker. Shared monochrome controls show current layout and selection.
  Layout MUST offer one Open app target in single view and two directly actionable
  targets in split view, arranged left/right or above/below and labeled Open left/
  Open right or Open up/Open down by their actual position, including swapped panes.
  A target opens its app picker in one action, without a separate pane-selection
    step. The main Apps action on either surface MUST open a combined app launcher
    directly, without an intermediate Layout or pane-selection menu. Its shared
    Single / Left-right / Up-down selector changes mode in place. Tapping Left/right
    or Up/down MUST immediately enable and show two panes in that direction,
    without a separate Split view toggle or app selection. This also restores both
    panes when the chosen direction is already selected but one pane is fullscreen.
    Tapping Single MUST immediately end split view, keeping pane 1 and closing
    pane 2 under the existing End contract. Successful choices MUST be remembered;
    disabled/busy/confirmation and stale picker guards still apply. A failed mode
    change MUST retain the previous saved layout and leave that picker inert until
    reopened, including rollback to fullscreen in the same direction. Each app row has
    explicit Left/Right or Up/Down launch actions in split, resolving actual positions
    through Swap. Capturing or pressing a destination MUST NOT start a display change.
    Launching into a hidden fullscreen pane MUST reveal that pane and launch after
    its resize succeeds, without a second tap; failed resize or Stop/disconnect
    MUST discard that pending launch. Single view retains normal row launch and one-time orientation.
    The launcher is a temporary menu; the controller grid and touchpad bounds MUST
    remain unchanged. Advanced ratio/Swap/fullscreen controls remain in Layout.
    A picker opened from Layout MUST return there on Back/Up; Close dismisses all menus;
    a root launcher closes to the controller/desktop. A completed launch dismisses it.
  The visible divider MUST be 2 logical dp, with a 12 dp minimum drag band centered
  around it and clipped to the workspace. Empty panes MUST reuse the single-view
  minimal background and centered Apps style. (DX-WINDOWS-001)
  Pane selection MUST remain visible in status/menu values without a selected-pane
    focus outline or highlight over app content. Only while a split launch action is
    pressed, its destination MUST have a subtle monochrome highlight (the full output
    in fullscreen mode), removed on
    release, drag cancellation, selection, picker replacement, reconfiguration or teardown. The
  picker MUST retain its explicit pane destination even if current selection changes;
  obsolete pickers MUST not launch into a different pane or newer desktop session.
  An in-place mode change MUST retain its originating session and picker ownership.
  Stop/disconnect, a replaced picker, or a failed mode change MUST leave that old
  launcher inert; completion MUST NOT acquire ownership from a replacement session.
- **FR-032**: Split view MUST retain independent input ownership for each pane.
  Physical touch takes priority over phone gestures; a held contact cannot jump
  across the divider. Hardware mouse buttons, hover/drag and wheel axes and
  keyboard modifiers/repeats/releases MUST retain their meaning.
  Divider gestures MUST retain their phone or monitor origin until release or
  cancellation. Phone divider tap/drag MUST release normally and leave phone input
  usable; physical takeover MUST cancel a pending phone divider without committing it.
  Settled post-divider input acceptance MUST verify the current pane task and its
  resized native touchable window before sending one contact; native fixture
  acceptance also requires its rendered frame at the current pane geometry first.
  Display metrics alone
  do not establish app readiness. Immediate pending-window contacts remain separate
  recovery scenarios and MUST NOT be replayed as settled-input acceptance.
  Mouse/touchpad events received on the phone controller MUST obey the same
  physical-contact and pending-focus ownership as phone gestures. Monitor menus
  MUST consume their complete pointer streams, hover and wheel events without
  activating an app underneath; phone pointer input MUST operate the visible menu.
  Menus MUST cancel held modifiers without duplicating a cancellation Android
  already delivered when native focus left that app.
  Phone pointer, Back, scroll mode/speed and save/reset target the selected app. A hardware
  wheel keeps native axis values. System-reserved keys and general IME placement
  remain Android-controlled; no delayed keyboard mirror is introduced. Physical
  peripheral reports and injected-event evidence remain separate. (DX-WINDOWS-002)
- **FR-033**: The existing foreground service MUST own both panes independently
  of the phone Activity. Four workspace rotations, Fit and resolution/DPI preview
  and rollback apply to both.
  Successful split on/off, direction, ratio and swap-order changes MUST persist
  across controller closure, process/session recreation, Stop/Start and reconnect.
  Explicit End split saves single view while retaining the last split geometry.
  Restore uses fresh displays and empty panes, without relaunching saved app tasks.
  Do not persist display IDs, app-selection highlights, held input, selected pane
  or temporary fullscreen state. Invalid saved values use bounded safe defaults;
  failed setup/resize or rejected changes MUST not overwrite confirmed settings.
  Native app frames MUST settle at the new pane geometry without retained stretch
  or crop from an earlier layout; visual acceptance checks actual rendered content.
  Task/status callbacks during resize MUST preserve the staged surface geometry
  until the resize or rollback completes.
  Texture view bounds MUST match native app buffer bounds while rotation and Fit
  transform their containing workspace.
    Empty-pane backgrounds, centered launch icons and captions MUST relayout and
    redraw within each pane after direction/ratio/Swap/fullscreen/rotation changes,
    including repeated empty-to-occupied transitions, without stale tiles or crops.
    Decorative captions MUST hide when they cannot fit clear of the centered Apps
    target. In-place launcher mode changes MUST settle with readable app labels,
    icons and mode names, not only working invisible hit targets.
  On legacy compositors, reattaching an output MUST wait for the old compositor
  output to detach, with a bounded worker wait and clean rollback/Stop on failure.
  In split view saved per-app orientation does not unexpectedly rotate the shared
  workspace; Save app changes scroll preferences
  and retains its saved single-view orientation. App departure/phone handoff clears
  only its pane, including stale pixels and protection state.
  If Android reveals an older task still in that pane, current app/settings MUST
  follow that task instead; only an empty pane shows Apps. Stop, disconnect,
  failed setup, Shizuku loss and obsolete callbacks MUST clean up both displays,
  surfaces, contacts, held keys and bounded pending work. (DX-WINDOWS-003)
  A secondary helper binding MUST belong to one split session. Late removal of
  an earlier binding MUST NOT stop a new session's helper or displays.
  On Android 11, phone configuration/inset notifications MUST NOT tear down
  either single or split view while the physical monitor's actual size, density
  and rotation remain unchanged. Real output changes/removal still allow teardown.
  Shizuku's destroy transaction MUST stop and terminate an unbound shell helper;
  repeated sessions MUST NOT accumulate orphan helper processes.
  Legacy input routing MUST wait for an owned app input window after task changes
  or resize, on the input worker with a bounded wait. A pending window MUST show
  a retry status and suppress that whole contact/key sequence, without stopping
  the other pane or redirecting input to the phone.
  On Android 12+, a rejected initial pointer DOWN MAY be recovered only when
  fresh, recognized input-window evidence confirms the same owned top task,
  component, UID and display has no admissible touch window. Discard its whole
  contact through UP/CANCEL, retain both panes and show the existing retry status.
  Before suppressing that stream, cancel its rejected injected DOWN on the exact
  owned display to release dispatcher contact state; cleanup rejection remains
  an explicit failure. This cancellation MUST NOT replay any app action.
  A fresh successful contact clears that status from authoritative task state;
  never replay a contact. Inspect only after rejection, never on every touch.
  Unknown/truncated metadata, identity races, permission errors and rejection
  despite a ready window MUST remain explicit failures. Stop/replacement and
  cancellation before resize MUST invalidate pending contact ownership.
  Android 11 split contacts MUST allow native focus transitions to settle before
  forwarding DOWN; this uses a bounded asynchronous buffer and remains measured
  separately from steady app dispatch.


- **FR-034**: Both Settings menus MUST expose Shizuku setup with installed/stopped,
  authorization-required and connected states, and current primary action. Missing
  Shizuku MUST produce an actionable install prompt even with status collapsed;
  installation opens the official download page only after a user action. Installed
  Shizuku can be opened on the phone; authorization/reconnection uses the existing
  Shizuku API and resumes the requested desktop when ready. Non-root service startup
  requires Shizuku's wireless-debugging/ADB procedure, including after reboot; do
  not claim or attempt silent startup, pairing, installation or permission grants.
  External launch failures stay in the setup page with useful feedback. No repeated
  automatic external-app/browser launches or background setup prompts. (DX-SETUP-001)
  Shizuku authorization/pairing and shell-process startup are separate. Guidance
  MUST explain reboot startup and that a smaller helper cannot turn shell/system
  privileges into a permanent runtime grant. Debug/developer settings are never
  silently disabled; banking-app acceptance is not guaranteed (DX-SETUP-001).

- **FR-035**: Settings on phone and monitor MUST include the same concise offline
  User guide, covering setup/reboot, app launch/orientation, split and resize,
  touchpad gestures, monitor navigation, keyboard, scrolling, saved app settings,
  display preview/Fit/DPI, monitor profiles, background/Stop and diagnostics/protected
  content limitations. Guide/setup pages follow shared Settings styling, scrolling
  and Up/Close/keyboard navigation. Related help returns to the guide. (DX-GUIDE-001)

- **FR-036**: Both controllers MUST expose a frequent-use Audio action in the
  existing four-action maximum row, plus Settings > Audio output. During a desktop
  session, users MAY select System or an available individual media output
  (monitor audio, speaker, headphones or Bluetooth when supported). The selection
  is a phone-wide default media policy, shared by both panes, not per-display
  isolation. Calls, microphone input, volume, casts and app-explicit routes are
  outside this feature. Only actual detected sinks are offered; a connected
  monitor alone is not evidence of an audio endpoint. Capability is probed under
  the actual privileged caller; denied/unsupported devices show an explanation,
  not a successful-looking selector. No universal Android 11+ routing guarantee.
  Requested preference and Android's anticipated media route MUST be distinguished
  from audible playback. Changes are asynchronous with pending/error states and
  stale completion rejection. Choice survives controller/menu/display/split
  recreation within the session; new sessions start at System. System and session
  end attempt to release only DexDroid's owned override and restore the original
  preferred policy, without overwriting an observed later external choice.
  Monitor disconnect ends the audio session; phone backgrounding does not.
  Missing selected outputs show unavailable/fallback honestly. Helper crash or
  lost privilege can prevent cleanup; surface recovery guidance and never claim
  guaranteed rollback. No debug flags, Bluetooth settings or volume are changed.
  Refresh outputs MUST remain visible with usable bounds and enabled activation
  on both surfaces, including rotated/Fit menus, without leaving the picker.
  See [session audio](docs/specs/session-audio.md), DX-AUDIO-001.

- **FR-037**: Settings on phone and monitor MUST offer a separate, explicitly
  confirmed Turn off debugging action. Ordinary Stop remains unchanged. Before
  changing settings, it MUST stop input, both desktop panes, release the owned
  audio override and return the existing phone controller after confirmed cleanup.
  Unconfirmed cleanup MUST prevent settings writes. While pending, duplicate
  requests and desktop startup MUST be blocked. Using the existing authorized
  Shizuku shell helper, it MAY only turn off wireless debugging, Developer options
  and USB debugging; no persistent settings permission is granted to DexDroid.
  Results MUST distinguish shell-verified Off, Still on and Could not verify for
  each switch, and separately report unverified ADB daemon shutdown. Transport or
  process loss MUST NOT imply success; an interrupted receipt remains unknown
  on the phone. Direct master-flag writes MUST NOT assume Android Settings' cascade.
  Pairing and app authorization MUST be retained. Never silently re-enable any
  flag, restart Shizuku or promise banking-app acceptance. Missing privilege offers
  an Open phone settings action and generic manual instructions. A collapsed
  Use the desktop again section MUST explain deliberate future re-enabling when
  Developer options is hidden; manufacturer-specific paths belong in the guide.
  Opening Settings or expanding help MUST NOT change debugging flags. Clearly
  distinguish future setup from turning debugging off; an inaccessible page does
  not verify switch values.
  A navigation error MUST preserve the last attempt's observations.
  Warn that other ADB/Shizuku
  apps may be interrupted. HSBC/PayLah checks use warning screens without login
  and private local evidence. See [debugging off](docs/specs/debugging-off.md),
  DX-DEBUGGING-001.

- **FR-038**: Settings on both surfaces MUST offer one saved Workspace shortcut.
  Save/Update captures a stable current monitor workspace with at least one app:
  canonical launchable app entries per occupied pane, explicit empty panes,
  Single/Split direction, ratio, pane mapping, selected/maximized pane and output
  rotation. Save, Clear and canceled pinning MUST NOT change the running workspace.
  Save MUST reject outstanding app launches, including overlapping Single-view
  requests, and confirm current owned-display occupancy off the UI thread before
  committing. A queued, failed or discarded launch MUST NOT save its requested app
  speculatively; unknown confirmation preserves the previous saved workspace and
  offers retry. Subsequent workspace edits invalidate an in-flight save.
  Add to phone Home screen requests Android launcher pinning. The launcher may add
  the icon directly or ask for confirmation. Setup MUST explain checking all Home
  pages and manual app shortcut permissions if no icon appears. Submission MUST
  NOT be reported as successful installation. A shortcut tap reuses the phone
  controller, waits for monitor/Shizuku readiness and prevalidates all apps before
  replacing the current arrangement, including panes saved as empty. Restore MUST
  await confirmed cleanup, layout and each app launch, then restore final focus and
  maximization. An intermediate app-closure warning MAY proceed only when final
  Stop confirms retired display/task removal; failed final confirmation MUST block
  replacement. Current monitor resolution/DPI and existing audio/scroll preferences
  remain authoritative; the saved rotation does not rewrite monitor/app profiles.
  Pending/restoring/partial/failure states MUST be explicit. While status is collapsed,
  a short restore notice precedes " · Show details" in the existing cue; active
  notices remain until completion and terminal notices expire after 10 seconds.
  Expanded status retains the full last shortcut result independently of routine
  status updates. Semantic notice changes are accessible without repeated refresh
  announcements. Stop, manual app/layout/
  display edits, Clear/Update, disconnect, debugging shutdown and destruction cancel
  obsolete work. Duplicate pending taps MUST coalesce; no in-flight activation or
  runtime display/task identity persists. Missing/disabled/private/unlaunchable app
  entries MUST produce recovery rather than an arbitrary fallback or phone launch.
  Missing/disabled entry lookup failures MUST identify the saved app and explain
  reinstall/update recovery rather than exposing raw package-manager identifiers.
  Private shortcut entry points MUST NOT accept external target/command arguments.
  One tap explicitly authorizes replacing the arrangement; setup explains this and
  that app-internal pages/documents/history cannot be restored by DexDroid.

### Non-Functional Requirements

- **NFR-001**: Input, search and menus MUST remain responsive. Performance claims
  require measured timing boundaries, sample counts and p50/p95/frame evidence
  under a declared setup. Proposed budgets remain unverified until measured;
  eventual-completion timeouts are insufficient. (DX-PERF-001)
- **NFR-002**: Before user-facing layout, navigation, controls, feedback or setup
  changes are implemented, a UI expert MUST review the intended task flow for
  usability and friction and present a concrete proposal to the user. Unresolved
  user-visible requirements after that review MUST be clarified with the user
  before dependent implementation, unless the user explicitly delegates the choice.
  Record the settled decision and revisit material departures. This preflight is
  separate from rendered acceptance and the two final code reviews.
  UI changes MUST retain the shared design language and be checked on
  rendered phone/monitor states, all affected rotations, Fit/DPI and input/keyboard
  paths. Pixel evidence, interaction checks and visual review remain distinct;
  baselines MUST not be silently approved. (DX-VISUAL-001)
- **NFR-003**: Changes MUST follow spec, implementation, then acceptance/E2E updates,
  with formatting/lint/build/host gates, affected device suites, traceable IDs and
  explicit unrun/manual/deferred gaps. New lint findings fail beyond auditable
  existing debt. Device runners MUST retain raw Android instrumentation output,
  require its final success code and reject missing status/crashes even if ADB
  exits successfully. Architecture and operational guidance stay outside this product
  specification. Maintainers MUST have an indexed onboarding route from setup to
  a first validated change. Architecture and owning feature docs MUST maintain
  readable high-level diagrams when boundaries, state flow or navigation change;
  diagram source and narrative MUST agree. (DX-MAINT-001)
- **NFR-004**: README MUST remain a concise setup/install/quick-use entry point;
  release changes belong in CHANGELOG. Raw logs/screenshots and provenance MUST
  be generated under ignored `testresults/<version>/runs/<run>/` folders, then
  archived outside the source tree with unchanged bytes, per-file hashes and an
  archive checksum. Future Git changes retain concise summaries, provenance,
  archive descriptors/locations and release reports; raw run trees and ZIPs MUST
  NOT be newly committed. Routine CI uploads MUST include only their current run.
  Availability/retention and local-only, failed, partial and unreviewed states MUST
  remain explicit. An archive verifier MUST reject missing/changed/unexpected or
  unsafe members without extracting them, including Windows-forbidden filename
  characters that could alias another member during extraction and reserved device
  names (including superscript COM/LPT digits and spaces before extensions).
  Archive creation MUST NOT delete source
  evidence or silently overwrite an existing archive. Preserve existing historical
  log bytes and valid references; no history rewrite or migration is implied.
  Specs MUST retain this
  story/requirements/outcomes structure. Existing features MUST have indexed
  feature contracts linked to stable baseline IDs, mapped tests and explicit
  verification gaps. Testing guidance MUST explain portable device selection,
  prerequisites, runnable suites, evidence and the limits of CI/emulation.
  (DX-DOC-001)
- **NFR-005**: Release automation MUST validate tag/app version/changelog, run host
  quality checks, build and verify signed APK/AAB artifacts with one persistent
  release key, and publish versioned assets/checksums with concise changelog notes.
  A durable current-run host evidence ZIP and descriptor MUST join the release
  assets/checksums; validate archive contents, source/report bindings and per-file
  hashes before any publication API call. Missing or mismatched archives block
  publication. Failed/routine runs use current-run Actions artifacts with disclosed
  retention; packaging alone does not promote device/visual/performance status.
  Failures MUST prevent publication; retries may repair drafts but MUST not
  overwrite published assets. Signing material MUST stay private. Manual dispatch
  uses an existing tag and the same gates. (DX-RELEASE-001)
- **NFR-006**: Requirement coverage MUST list stable requirement IDs, acceptance
  criteria, mapped test cases, scenario dimensions and explicit gaps. Every test
  addition, modification or removal MUST refresh the generated inventory and
  source fingerprints; local/CI checks MUST reject stale coverage. Inventory order
  and fingerprints MUST be identical on Windows and Linux for the same source,
  including mixed-case paths and LF/CRLF checkout differences. Mapping is
  distinct from execution, visual approval and measured performance. (DX-COVERAGE-001)
- **NFR-007**: Before each push and release, two newly created independent review
  agents MUST inspect the completed candidate and both explicitly approve. Record
  their distinct identities, scope, verdicts and candidate digest. Missing,
  rejected or stale approvals MUST block publication. Any candidate content
  change except recording those approvals invalidates them. Review records are
  auditable attestations, not cryptographic proof of agent identity. (DX-REVIEW-001)
- **NFR-008**: Every future release MUST generate a versioned Markdown report from
  that build's structured test results and current requirement coverage. Include
  passed/failed/error/skipped counts, pass-rate denominator, mapped/partial/manual/
  deferred requirement counts and gaps, and explicit device/visual/performance
  states. Link the exact changelog, GitHub release, source commit and workflow run.
  Retain the report under `testresults/<version>/runs/<run>/`, attach it and its
  structured evidence to the release with checksums, and link it in release notes.
  Missing, malformed, mismatched or unsuccessful required host evidence MUST block
  publication. Test pass rate MUST NOT imply full requirement coverage. (DX-REPORT-001)
- **NFR-009**: `main` MUST require a pull request and an up-to-date successful
  GitHub Actions `host` check, including the exact-candidate two-agent approval
  gate. Enforce protection for administrators; disallow force pushes and branch
  deletion, and require review conversations to be resolved. Future changes MUST
  use branches/PRs. Agent approvals remain the auditable CI attestations defined
  in NFR-007; GitHub human approval slots are not agent identities. (DX-BRANCH-001)

- **NFR-011**: Coding work MUST follow the dedicated coding-practice guide: record
  affected requirements and a conservative test plan, use targeted iteration checks,
  complete required host and applicable device/visual checks before delivery, and
  freeze source/tests/docs/coverage/evidence before two fresh independent reviews.
  Reuse valid source/configuration-bound evidence without claiming new execution;
  changed content still invalidates approvals. Record available durations and rerun
  reasons, distinguish proposed automation from shipped tooling, and reconcile
  reusable workflow improvements with the template repository. (DX-PRACTICE-001)


- **NFR-012**: Local development commands MUST provide live byte-preserving stdout
  and stderr files, bounded structured progress/status and a read-only independent
  viewer. Heartbeat, output activity, semantic progress, warnings and final outcomes
  MUST remain distinct. Enforce explicit command deadlines, preserve partial logs,
  fail on capture errors/limits, and cancel owned processes with bounded cleanup.
  Device timeout/cancellation MUST never become success; retain controller restore
  outcomes. Silence only warns, observation never starts/retries work, stale/missing
  state stays unknown, and unknown scenario totals never become percentages.
  Keep everything local with no model calls, cloud service or raw prompt export.
  Final monitoring faults, including after the last command, MUST prevent a passing
  runner result and exit code; controller restoration still runs before finalization.
  Supervisor evidence MUST use a namespace distinct from all suite result folders,
  including the existing monitor suite, so observation cannot block suite execution.
  (DX-OBSERVE-001)

- **NFR-013**: Maintainers MUST have a source-linked whole-system/component overview
  and an indexed overview and sequence diagram for each feature owner, including
  lifecycle/failure boundaries. Each task MUST record architectural impact before
  implementation. Changes that may affect ownership, interfaces, process/thread
  boundaries, lifecycle, persistence, shared geometry/input or delivery contracts
  require a fresh architecture expert's source-based approach review before
  dependent work. Resolve material uncertainty with the user; revisit material
  departures. Keep diagrams reconciled and inspect rendered output. This review
  is separate from UI preflight and both final candidate approvals. Documentation
  describes implementation/contract, not proof of runtime correctness. Every new
  version candidate, including patch, prerelease and documentation-only
  releases, MUST receive a fresh architecture expert's explicit approval after
  checks/evidence and before two final reviewers and release authorization steps.
  Task-level N/A MUST NOT waive release review. Record cumulative release scope,
  exact pre-attestation snapshot, actual reviewer/verdict, findings and gaps; block
  tag creation/push, release dispatch, signing and publication without approval.
  The verdict record is included in the final canonical candidate; do not add a
  digest exclusion. Changed inputs require renewed architecture review. (DX-ARCH-001)

- **NFR-010**: Personal application screenshots, device dumps/logs and account
  content MUST remain private local evidence and MUST NOT enter Git or public
  artifacts. Public host uploads MUST include only their current run and pass an
  indexed-path and actual-input privacy guard; rejected inputs MUST block upload.
  Intentional public UI references MUST use reviewed synthetic fixtures. Privacy
  withdrawal MUST be explicit and never count as verified evidence; retained
  historical files MUST still pass original integrity checks. (DX-PRIVACY-001)

- **NFR-014**: The Kotlin migration MUST convert all maintained Android application,
  debug and test sources and Gradle build scripts while preserving the existing
  product contract, package/entry-point identities, signing identity and stored
  settings. Generated AIDL Java and non-Android tooling retain their native formats.
  Independently launched test fixtures MUST retain their separate application UID
  and carry their required Kotlin runtime in the test APK, using the same pinned
  version as the app. Production artifacts MUST still exclude diagnostic fixtures.
  The debug shell snapshot entry MUST authorize shell UID/arguments before
  preparing its process-main Looper for accessibility, reject incompatible thread
  Looper state and preserve callback-thread cleanup before XML output. It MUST
  not initialize an Application, dispatch a new main loop or inject input. Its
  standalone check MUST verify the installed APK, invoke once within a finite
  bound, reject invalid/non-controller display-0 XML and retain failure evidence.
  Preserve checked failures, input ordering/cancellation, display generations,
  lifecycle/rollback, phone/monitor behavior and appearance. Each phase MUST pass
  applicable host and emulator regression before continuing, except for the
  specifically documented API 30 emulator failures shared by the Java-model
  control and current harness: the retained AOSP first-Controls profile MOVE
  timeout after phone save/rename/dismiss, separately evidenced preview/Revert
  profile MOVE timeouts, and the
  Google-image split controller-return window missing after reader handoff.
  Those exceptions remain failed/outstanding, including the blocked scenario tails,
  permit bounded conversion progress only after independent architecture review,
  and never cover new failures or other input/keyboard/child/Stop checks. Recheck
  affected owners and execute the final ordinary emulator campaign. A residual
  failure requires explicit final architecture and both final review decisions;
  it cannot be reported as passed. Final acceptance
  includes one planned full Android 11 monitor campaign (or an explicitly
  user-selected replacement device) after the final phase,
  with necessary failure fixes/retests. Publish the whole migration in one version
  after the stable Java release. Coverage discovery, source fingerprints and
  production exclusion guards MUST include Kotlin, including companion/nested
  classes and file facades; missing or ambiguous inventory MUST fail explicitly.
  Successful native assertion descriptions MUST avoid reserved failure tokens
  recognized by the strict runner; this does not relax outcome classification.
  Focused navigation acceptance MUST keep the Android11 readiness predicate on
  API30 and select existing strict modern task/window readiness on API31+. Bind
  one immutable parent-or-child fixture identity, task, UID, display and geometry
  per tap, preserving the five-second/40ms prerequisite and all native navigation
  outcomes. Before remote Back on API31+, require the captured fixture window to
  report focus within five seconds at 40ms polling, then atomically revalidate
  page/task/UID/display/geometry and inject once. Preserve API30 existing key
  semantics; this prerequisite does not claim to repair product key dispatch.
  Pointer-only NOT_FOCUSABLE admission remains API33-specific. Retain
  bounded consumed failure observations in private host evidence with run-clock freshness;
  allow the failure-only file to be absent on success and record collection errors
  separately from native outcomes, without replacing primary failures or
  claiming missing historical reports were recovered. Reuse unaffected campaigns
  only with unchanged executed dependencies and original APK/run attribution.
  An explicitly armed debug-only Xiaomi API35 secondary-child diagnostic MAY
  retain one existing rejected-DOWN decision sample for the exact reviewed build,
  shell UID2000 and synthetic fixture UID10362. Skip intentional parent-pending
  rejections without consuming the child sample; retain null/unknown/changed/error
  outcomes. Preserve the emulator profile, nonce/boot/expiry, exclusive owner and
  bounded private output. No new input query, focus, retry, classifier or cancel
  behavior is authorized by capture. Missing/truncated output is inconclusive;
  diagnostic timing does not establish ordinary performance. Production artifacts
  MUST exclude the sink. Revalidate updated debug APK/signature/UID before use.
  Request publication MUST refuse existing files and dangling links. A reviewed
  noclobber publisher may replace unsupported hard links; admit no consumer until
  successful exit and exact protected-file validation. Partial/colliding writes
  are terminal; do not overwrite, retry or delete mismatched content.
  Protected diagnostic files MUST use binary-preserving reads with bounded size,
  stable owner/mode/type/path and matching remote/local SHA-256 before and after
  transfer. Shell text newline conversion MUST NOT be normalized into acceptance.
  Known Android 17 failures remain separately documented, not counted as passes.
  The final migration release uses the completed Android 15/API 35 emulator and
  selected Xiaomi14 Android15 hardware evidence. The user's 2026-10-20 decision
  ends further Android12/API31, Android13/API33 and Android14/API34 testing for
  this release; retain their failures, capability blocks and unrun tails as
  community-verification gaps. Keep exact original source/APK/image/boot bindings
  and separate native, collection, visual and performance outcomes. Targeted
  test-readiness corrections require focused Android15 verification; they do not
  authorize another broad platform campaign or imply physical Android11 coverage.
  Native Chrome Stop may wait for one otherwise valid label to acquire a clickable
  target within its existing deadline. It must never inject before full action
  guards pass; disabled, foreign, obscured, malformed or ambiguous actions remain
  failures and a failed native input is never retried.
  Capability admission MUST distinguish package permission metadata from the
  actual shell caller's PID/UID permission check. A package denial alone is
  inconclusive. A bounded read-only shell probe may measure the framework's
  caller permission path, retaining exact build/boot/UID/tool bindings and
  independent cleanup; no grant, root, display or input bypass is permitted.
  Native monitor fixtures MUST share a main-thread snapshot of the actual logical
  host frame, origin, window/display/configuration and density. Use one snapshot
  for each finite contact and revalidate before DOWN; do not wait, retry or adapt
  mid-contact. Origin-aware screen injection, inverse mapping, PixelCopy extents,
  bitmap sample coordinates and layout center/bounds MUST use matching spaces.
  Preserve signal-mode status assertions, independent coordinate expectations,
  existing tolerances, deadlines and intentional stale/busy/cancellation scenarios.
  Native acceptance setup MUST wait within a finite bound for Shizuku's fully
  initialized service interface before checking authorization, remove readiness
  listeners on every exit, and fail explicitly on timeout or denied permission.
  Native profile assertions MUST observe UI-owned identity, binding and related
  session conditions coherently on the UI thread; an absent monitor cannot satisfy
  an expected-unbound assertion. Test profile writes use the same UI ownership.
  Phone page/Back readiness acceptance MUST evaluate its existing predicates on
  the main thread against the captured Activity, preserving deadlines, labels
  and input. Retain bounded content-free owner identity on failure before cleanup;
  do not reacquire owners or add focus/input retries. Failed input readiness may
  retain bounded decoded text from its existing queries without changing their
  order/count or acceptance. Samples are invocation-owned, mark unqueried/truncated
  scope and flush privately before teardown; independent writes cannot replace
  the original failure. Optional observation does not affect other callers.
  DeviceChecks may opt into API33 pointer-only readiness for exactly `0x0` or
  lone `NOT_FOCUSABLE`, which does not itself block touch targeting. Preserve all
  ownership/geometry/obstruction/final-task and native delivery assertions; defaults,
  other callers, unknown/combined flags and keyboard readiness remain unchanged.
  Monitor keyboard filtering acceptance MUST capture its shell/editor/list/adapter
  owner on main before typing. Within the existing ten-second readiness wait,
  require the expected query, adapter count and attached/shown/measured matching
  row visibly inside that same list in one main-thread observation. Reject owner
  replacement; retain the subsequent rendered-row and native input/selection
  assertions. Count-only readiness cannot establish child rendering. Poll off
  main with no input replay, fixed delay or extended deadline; retain only the
  bounded latest failed observation without masking the original failure.
  Post-configuration input acceptance MUST keep its captured task/display/geometry
  identity and unchanged readiness predicate. Require two consecutive positive
  observations within the original ten-second polling deadline, resetting the
  streak on false rather than failing early after a transient positive. Retain
  the fifty-millisecond interval, propagate query errors, and record the final
  consumed sample plus bounded streak/elapsed context. Native query duration is
  separate from the polling deadline; no input replay, owner reacquisition,
  product readiness relaxation or deadline extension is permitted.
  Child-launch acceptance MUST isolate its parent/reader/exit reports from other
  same-UID fixture families sharing the report action. Unrelated reports cannot
  overwrite the observation; expected reports with wrong placement or dimensions
  must still fail the existing assertions, and missing reports must time out.
  Successful navigation acceptance MUST exit its known fixture child/root through
  native navigation and verify the captured task is absent before stopping the
  desktop, so later suites do not inherit a child Activity as their launch target.
  Failed scenarios retain their evidence and unconditional service/input cleanup.
  Split launcher geometry acceptance MUST establish a visible, laid-out controller
  and a successful nonempty baseline rectangle before opening the picker. After
  dismissal, take one immediate successful nonempty rectangle and retain exact
  equality; never poll until the rectangles match or reset view state to pass.
  Build-version metadata MUST have one strict owner shared by release and device
  evidence tools. Require one app script and one direct literal version pair in
  its independent android/defaultConfig block; reject ambiguous/computed values,
  unsupported mutations and invalid syntax before output creation or device work.
  The reader's bounded source contract does not replace binary metadata checks.
  Physical verification of a production install MUST preserve its user/UID and
  signing identity without uninstalling or clearing data. Capture current
  preferences after a same-key debuggable update but before deliberate app/service
  execution; retain process/component evidence and reject observed execution
  before capture. Disclose that inaccessible pre-update bytes cannot be compared.
  Restore verified saved contents while processes are stopped. Require a bounded,
  uniquely marked execution-free interval from before force-stop through verified
  production replacement, with process absence around replay and after replacement.
  Failed or incomplete observations cannot admit restoration success or launch;
  retain the snapshot for state-specific recovery. Private operation/input hashes
  MUST be bound by a tracked reviewed contract. Retain primary and secondary
  failures separately, and never report incomplete restoration as pass.
  Failed binary preference replay MUST use an independently reviewed state-specific
  recovery generation and retain original records. Do not launch malformed data.
  Protect staging before transfer and require exact archive hashes and restored
  member bytes; the diagnosed host shell-stdin normalization path is prohibited.
  A separately reviewed active-controller recovery MAY use a native Stop action
  from the already visible display-0 controller, with unchanged user/UID/process/
  activity/task/window identity and unobscured enabled action revalidated after
  the shell hierarchy dump. Global keyboard focus on the monitor is allowed.
  Never launch or force focus to admit this action. Require owned-display absence
  before the execution-free replay interval; retain changed current bytes without
  treating them as the saved source. Metadata-only source rebinding MUST preserve
  every runtime source and APK, isolate the exact requirement delta, and retain
  the original generation unchanged.
  A failing hardware key-cancellation scenario MUST be diagnosed without changing
  routing or weakening its original assertions. Debug-only synthetic traces are
  bounded to128records of512bytes per process, reuse existing relay/focus RPCs,
  and exclude text/unrelated app identities. Fixture callback history is bounded
  to32entries per instance and carries task/display/UID/serial/current focus.
  One initial per-instance task metadata capture is permitted and cached; it does
  not prove later task freshness, which remains the existing readiness owner.
  Each key sequence uses fresh down-time and each event fresh event-time. Preserve
  the original15second cancellation deadline; latch failure before independent
  late-release cleanup and rethrow it afterward. Missing/truncated data is
  inconclusive. The user's2026-10-19 direction ends automatic restoration work;
  retain its failures and backups, continue migration verification without claiming
  successful preference/controller recovery. In-place updates still preserve app
  identity/data; no product uninstall or data clearing is authorized.
  If the final Android11 device becomes unavailable, a user-selected replacement
  MAY run a separately device-bound hardware campaign after live admission and
  independent procedure review. Record its actual Android/OEM version; its results
  MUST NOT satisfy the unrun Android11 hardware path. A positively absent product,
  instrumentation and broker may be freshly installed from verified frozen APKs;
  establish and retain their distinct UIDs before subsequent operations. Reusing
  verified signed APK bytes does not reuse an old device's operation admission.
  Native monitor-input acceptance MUST generate display-injected points using
  the current measured logical monitor content viewport and its display origin,
  not the monitor signal's pixel mode. Bind measurements to the current host and
  desktop configuration; reject stale/invalid geometry without contact retries.
  Preserve independent intended virtual coordinates and the original less-than-
  two-pixel alignment assertion. Keep synthetic fixed-size Geometry cases distinct
  from measured native injection, including a1920x1242 logical/1920x1200 signal
  regression. Failure diagnostics distinguish injected, intended and received
  coordinates before ordinary cleanup; no runtime mapping change is implied.
  (DX-MIGRATION-001)

### Key Entities

- **Desktop session**: Readiness, attached monitor, selected app, session orientation,
  resolution/UI size and active input ownership. Initial orientation is landscape;
  subsequent sessions remember the chosen default.
- **Monitor capabilities/profile**: Named snapshots of desktop dimensions/UI DPI/
  default rotation; monitor identity-to-profile bindings. Use an accessible stable
  physical identity or a disclosed capability fingerprint; temporary Android
  display IDs are never persisted. Bind only confirmed profile applications.
- **App profile**: App package, saved rotation, scroll mode/speed and defaults;
  one-time launch choice does not replace the profile.
- **Input sequence**: Source, contacts, timing, mapping and cancellation ownership.
- **Diagnostic event**: Bounded timestamped recognition/connection/dispatch outcome.
- **Requirement/evidence record**: Stable ID, scenarios, mapped tests, configuration,
  results, provenance and outstanding coverage; distinct from release notes.

## Success Criteria

### Measurable Outcomes

- **SC-001**: In the supported setup, users can open the desktop and launch the
  input test without a manual Connect step when dependencies are ready.
- **SC-002**: At 0/90/180/270 degrees, center and four corner targets activate
  within the acceptance suite's 2-pixel tolerance; Fit-bar taps activate no content.
- **SC-003**: Required tap, scroll, pinch and Back interactions reach the external
  test app without an accidental tap, duplicate Back or held contact.
- **SC-004**: Every affected menu exposes correct selected/current state on both
  surfaces; phone Close/results remain reachable with keyboard and scrolling.
- **SC-005**: An unconfirmed display change returns to its prior configuration
  after the 15-second preview; confirmed profiles and app overrides stay independent.
- **SC-006**: Normal phone-controller closure preserves monitor interaction; Stop
  releases the desktop and permits a fresh usable session.
- **SC-007**: Each changed requirement has test/review evidence or an explicit gap;
  the affected coupled scenario matrix and visual review are recorded. Assertion
  totals alone never certify completion.
- **SC-008**: Local search feedback, pointer feedback, menu readiness and frame
  pacing have reproducible distributions before numerical performance claims.
  Current proposed limits/protocol are in [QUALITY.md](docs/QUALITY.md).
- **SC-009**: Documentation checks resolve all local links and evidence entries,
  verify archived raw-log hashes, and leave no release test logs at repository root.
- **SC-010**: A successful tagged workflow produces one signed APK, one signed AAB,
  a checksum manifest and build metadata on the matching GitHub release. Their
  downloaded hashes match; notes describe only that version and include changelog
  links. Invalid inputs or check failures produce no published release.
- **SC-011**: Changing, adding or deleting a test makes coverage validation fail
  until its inventory is regenerated; every requirement retains its scenario
  dimensions, mapped checks and explicit unverified/manual/deferred gaps.
- **SC-012**: Missing, duplicate, rejected or stale reviewer approvals reject a
  candidate push/release. Two actual fresh approvals for the same candidate permit
  publication after the other gates pass.
- **SC-013**: Each newly published release has a downloadable, checksummed report
  linked from its notes, with reproducible coverage totals, measured host pass
  rates, explicit skipped/unrun gaps, and working version-specific changelog,
  release and workflow links. Invalid/missing evidence prevents publication.
  The [0.12.1 acceptance plan](docs/changes/release-0.12.1.md) separates current
  Android 16 monitor checks, prior Android 11 evidence and production-install
  verification; pending device checks cannot be presented as release passes.
  Native Stop evidence MUST explicitly target the primary physical phone display
  when capturing screenshots; a monitor capture or warning-prefixed stream is not
  valid phone visual evidence.
- **SC-014**: GitHub reports `main` as protected with required PRs, strict required
  `host` checks from GitHub Actions, administrator enforcement, resolved
  conversations and no force pushes/deletions. A PR cannot merge until the CI
  gate validates both agent approvals and host checks for its current candidate.

## Assumptions

- Initial supported hardware is Black Shark 4 / Android 11 / JoyUI and a 1920x1200
  external monitor. USB-C video output/cable support and Android touchscreen-display
  association are prerequisites; other ROM/monitor combinations require validation.
- Shizuku provides non-root shell privileges and is started separately after reboot.
  DexDroid does not require network access for its core desktop/input features.
- The physical monitor does not rotate its signal. Four discrete output transforms
  are supported; the user selects the physical orientation.
- The primary Android user is supported. Apps can restrict external placement,
  resizing, DRM or pinch behavior. No universal third-party compatibility is claimed.
- Native Android IME placement on the current untrusted virtual display is blocked;
  monitor search uses local keys. General external-app text entry retains platform
  behavior. A delayed phone-IME mirror is not a shipped feature.
- Approved golden screenshots and controlled latency baselines are still pending.
  See [test evidence](testresults/README.md) and the requirement map for actual scope.

### Deferred Features

- **DFR-001**: Absolute/direct phone gesture mode and its visible mode switch are
  optional future work; relative touchpad mode remains the current input mode.
  Any future switch must cancel contacts and preserve app/orientation. (DX-OPTIONAL-001)
- Browser Forward, arbitrary app overflow menus, phone drag/long press/secondary
  click, general phone IME mirroring, freeform window management, orientation thumbnails,
  physical signal switching and arbitrary-angle/automatic monitor rotation are deferred.
- Locked-device operation, guaranteed DRM playback, simultaneous independent
  instances of every app and replacement of Android's window manager are out of scope.

## Additional acceptance constraints

Android 17 compatibility is experimental for 0.12.5. The tested API 37 image
CE2A.260420.019 has a framework stale-display recovery crash during teardown with
the default emulator renderer; software-renderer child/idle passes do not waive
that limitation. Android 11 hardware and Android 16 emulator acceptance remain
separate results. Preserve this known failure in subsequent compatibility and
migration comparisons rather than claiming universal Android 11–17 support.

Public release archive packaging MUST inspect uncompressed current-host input before sealing. Publication MUST reject forbidden evidence members even when archive integrity hashes match. This extends NFR-010 / DX-PRIVACY-001.

Native Stop regression acceptance (FR-017 / DX-LIFE-002): the shell-only driver
MUST inspect display 0 explicitly, select one visible/enabled DexDroid action
within a clickable ancestor, reject ambiguous targets or higher overlapping
foreign windows, disconnect its read-only automation connection before one
`input -d 0 tap`, and never launch/focus the controller after Stop. Its dump helper
MUST be debug-only, shell-UID guarded, and have no injection/permission-adoption
or app instrumentation. Connection/thread cleanup failures MUST fail the check.
Before each native tap, the driver MUST allow a bounded readiness interval for
the requested action to appear in fresh, fully disconnected read-only snapshots.
It MUST retain each attempted snapshot and fail on timeout. Ambiguous, obscured
or otherwise unsafe targets MUST fail immediately; only action absence may wait.
The driver MUST issue at most one input tap for the action and MUST NOT retry
input or connect another dump helper after Stop.

Split picker acceptance (FR-031 / DX-WINDOWS-001): a single native phone tap
MUST use the clipped target bounds converted from root coordinates to display-0
screen coordinates, after the target is shown/measured/enabled. Preserve the
one-tap app-picker outcome; do not replace input with programmatic clicks.

After phone submenu replacement, native picker acceptance MUST wait until the current dialog has a shown/measured, laid-out input window with focus before measuring and injecting its one tap. A newly constructed view tag alone is not native-window readiness.

Split native-input acceptance MUST await the rendered fixture frame at current pane geometry before its first contact, rather than treating task placement as input readiness. The contact remains a single native event sequence with no retry.

Phone-handoff acceptance (FR-025) MUST open the exported app entry on display 0 through Android Activity start, preserving the reused task/state and actual idle/remaining-app rendering and input assertions. Privileged root-task migration is not an equivalent user-launch trigger; retain its failures separately as unresolved platform diagnostics. Private readers MUST NOT be directly launched by the shell.

Remaining-app handoff diagnosis (FR-025 / DX-HANDOFF-001) MUST distinguish the
managed display's task/window metadata, monitor-window capture and independently
identified emulator/monitor output. Retain bounded paired frame evidence and
identities before cleanup; unknown targets or failed capture remain unverified.
A separately named minimal two-app/phone-launch scenario may isolate the failure
without rotation/Fit setup, while preserving the original full child scenario.
After frame verification, one fresh native monitor contact MUST reach the same
remaining task/display without retry. Repair MUST preserve app tasks/state,
desktop identity, input ownership and lifecycle; it MUST NOT steal the phone task
back, restart an app or recreate the desktop to manufacture a passing frame.
  Opening the phone copy while a managed app launch is still transitioning MUST
  preserve that same visibility/input contract. A launch-transition correction
  MUST use the shared single/split placement path and preserve task reuse,
  fullscreen verification, failure reporting and existing edge-gesture animations.
  A completion-aware foreground launch of an existing background desktop task
  MUST report success only after its own native finish event and fresh same-task,
  fullscreen target-display verification. Missing completion or ownership changes
  MUST be diagnosed without retrying that start or reclaiming a phone task.
  Already-visible launches MUST remain usable without waiting for a nonexistent
  transition callback. Pre-completion platform overlap remains a separate acceptance
  scenario; a completed-launch pass MUST NOT be reported as universal race repair.

Fixture-only handoff setup MUST recognize the observed optional native
`bounds=[x1,y1][x2,y2]` field before a task's `userId=0`, including signed integer
coordinates. It MUST retain exact fixture-family/current-user/sole-task identity,
parsed root/task checks, duplicate rejection and revalidation before removal.
Unknown tokens and malformed geometry MUST NOT broaden destructive test setup.

Synthetic split ownership acceptance MUST release its remaining physical host contact after asserting downstream cancellation. Product cancellation alone does not end the shell-injected host stream. A later gesture starts only after the exact remaining pointer has been released; rejected injection must still fail without retries.

The debug native Stop snapshot helper MUST allow bounded discovery of display-0 accessibility windows inside its existing connection after registering window retrieval. Android event quiet alone is not readiness. Empty enumeration may wait at most two seconds; oversized/invalid window data, traversal or cleanup failures remain fatal. Discovery precedes the independent bounded traversal and does not change focus or inject input.

Phone dialog Back handling (FR-002) MUST consume the key and defer its navigation or IME-hide action until the old window key dispatch completes. Only the captured current, showing dialog in a live Activity may act; replacement, Close or destruction invalidates pending Back. At most one Back continuation per dialog may be pending. Decide keyboard-hide versus parent navigation at receipt so the first Back still only hides the IME. Stale callbacks must not reopen menus or affect replacement windows.


## Production diagnostic exclusion

Rejected-DOWN diagnosis for FR-033 MAY use a separately armed, debug-only shell
capture on an identified emulator. It MUST reuse the exact target/window/target
queries consumed by the original decision, including null, changed, unknown and
exception paths, without extra queries, input retries or classification changes.
Retain one bounded immutable sample and at most sixteen decision events for one
bound helper/request; never retain MotionEvent, Context or Binder objects. Request
ownership, boot, build, nonce, expiry and output paths MUST be validated. Diagnostic
failures MUST NOT replace the original return/exception. File output follows the
original classification/cancellation decisions and records its own duration;
such a run establishes consumed evidence, not equivalent timing. This private
facility MUST be absent from production and requires no user-facing control.

- Official APK/AAB builds MUST omit development input-test/probe Activities and
  their executable classes. Phone and monitor Apps menus MUST contain no synthetic
  DexDroid input-test entry. Explicit launch of its old component MUST fail.
- Debug builds MUST retain the input-test Activity and catalog entry so acceptance
  tests remain runnable. Task parsing/cleanup guards for legacy component names
  remain valid without making those components launchable in production.
- Updating an installed official build MUST remove the old diagnostic component
  while retaining application settings and Shizuku configuration. Release package
  validation MUST reject accidentally included diagnostic code or declarations.
  These acceptance constraints extend FR-018/DX-LAUNCH-001 and NFR-005/DX-RELEASE-001.
