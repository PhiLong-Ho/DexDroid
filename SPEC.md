# Feature Specification: DexDroid External Desktop

**Feature Branch**: `main` (existing application baseline)

**Created**: 2026-10-15 (consolidated specification)

**Status**: Maintained baseline for 0.10.1; coverage gaps remain in the requirement map.

**Input**: Give a non-root Android phone an independently rotated external
desktop, with phone touchpad and monitor touch control, consistent menus,
remembered app settings, and verifiable behavior.

Structure adapted from the [GitHub Spec Kit feature template](https://github.com/github/spec-kit/blob/3238fa9285694dc539aeb9829b337bfd6aed7317/templates/spec-template.md).
Product behavior lives here; [design](docs/DESIGN.md),
[architecture](docs/ARCHITECTURE.md), [quality](docs/QUALITY.md),
[release history](CHANGELOG.md), and [test evidence](testresults/README.md)
are maintained separately. Stable `DX-*` IDs in [requirements.json](docs/requirements.json)
map the requirements below to suites and known gaps; a mapping is not a test pass.

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
   Settings, status and equal button rows match the shared design contract.
2. **Given** phone Settings or a submenu, **When** it opens, **Then** it fills the
   app window, keeps the Close header fixed and scrolls overflowing content.
3. **Given** phone Apps search, **When** Android's keyboard opens, **Then** Close,
   search and app results remain reachable.
4. **Given** constrained phone height, **When** status/controls overflow,
   **Then** the page scrolls while pad gestures retain control of the desktop.
5. **Given** a monitor menu, **When** an outside or Fit-bar tap occurs,
   **Then** the entire menu closes without a click reaching the underlying app.
6. **Given** an option change, **When** the menu is reopened on either screen,
   **Then** current values and selected states reflect the same authoritative settings.

### User Story 5 - Navigate directly on the monitor (Priority: P1)

As a touchscreen-monitor user, I use edge gestures to go Back, open Apps or show
controls without bringing the phone controller forward.

**Why this priority**: The external desktop must remain independently operable.

**Independent Test**: Inject real edge and non-edge contacts in all orientations;
verify one action, app-input isolation, rejection and settled feedback.

**Acceptance Scenarios**:

1. **Given** an open app, **When** an inward swipe qualifies from either side,
   **Then** one Android Back is sent relative to the upright desktop.
2. **Given** an open app, **When** bottom-up or top-down edge navigation qualifies,
   **Then** Apps or Controls opens respectively; the phone receives no Home action.
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
- Identical monitor names/capability fingerprints can share a profile; expose reset.
- Simultaneous input, extra pointer lifts and canceled gestures must never leave
  contacts held, produce an accidental tap or trigger duplicate navigation.
- Save/reset with no selected app must not corrupt another app's preferences.

## Requirements

### Functional Requirements

- **FR-001**: Phone/monitor controls MUST share monochrome surfaces, spacing,
  typography, vector icon actions and live values. Rows MUST be Apps/Rotate/Scroll
  then Stop-or-Start/Save app/Back. The monitor omits the touchpad; Settings sits
  left of Close. Status includes monitor, Shizuku, desktop, operation, last selected
  app, scrolling, touch, rotation/saved state and resolution/UI DPI. (DX-UI-001)
- **FR-002**: The phone controller MUST remain portrait. Settings and submenus MUST
  fill its app window with fixed headers, scrollable overflow and IME-safe Apps
  results. Main-page scrolling MUST preserve touchpad gestures. (DX-PHONE-001)
- **FR-003**: Labels, punctuation, glyphs and icon descriptions MUST remain readable
  without encoding corruption. Source/document encoding MUST be UTF-8. (DX-TEXT-001)
- **FR-004**: The launcher icon MUST depict a monochrome Android robot diving into
  a monitor, with legible adaptive/themed/small-size silhouettes and transparent
  eye cutouts. (DX-ICON-001)
- **FR-005**: A fresh no-app desktop MUST show a minimal background and centered
  Apps icon. Failed launches retain idle; successful launches hide it. (DX-IDLE-001)
- **FR-006**: Monitor Apps MUST offer local selection-aware search keys for letters,
  Shift, digits, spaces, period/hyphen/underscore, Delete, Clear and Hide; support
  touch, phone pointer and hardware text keys; preserve hidden queries and clean
  up focus on exit. No phone IME mirror or global IME changes ship. (DX-KEYBOARD-001)
- **FR-007**: Upright-relative side-edge Back, bottom Apps and top Controls MUST
  work in all four rotations. Recognition starts within 20 logical dp of an edge,
  moves inward at least 60 dp with 1.4x dominant displacement within 1.5 seconds,
  and prioritizes top/bottom at corners. Exactly one action occurs. (DX-NAV-001)
- **FR-008**: Invalid/disabled/multi-touch edge starts MUST not navigate. Modal
  shell contacts MUST not reach the app; outside/Fit taps dismiss without
  click-through, while blank inside taps retain the menu. Back navigates shell
  pages first. App-touch and edge-navigation toggles are independent; shell
  controls remain operable when app-touch forwarding is disabled. (DX-NAV-002)
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
- **FR-016**: Desktop, monitor touch and menus MUST survive normal phone Home/app
  switches/controller destruction. Background status MUST reflect actual system
  exemption and refresh on return. Explicit Stop or dependency loss ends session
  resources. Reboot/Force stop survival is not promised. (DX-LIFE-001)
- **FR-017**: Opening DexDroid MUST connect to available Shizuku and request desktop
  startup, waiting for a monitor as needed. First-use authorization and missing/
  stopped/denied states MUST be explained. Latest pending app selection is queued.
  Stop retains usable controller focus; Start/reopen/launch can resume. (DX-LIFE-002)
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

### Non-Functional Requirements

- **NFR-001**: Input, search and menus MUST remain responsive. Performance claims
  require measured timing boundaries, sample counts and p50/p95/frame evidence
  under a declared setup. Proposed budgets remain unverified until measured;
  eventual-completion timeouts are insufficient. (DX-PERF-001)
- **NFR-002**: UI changes MUST retain the shared design language and be checked on
  rendered phone/monitor states, all affected rotations, Fit/DPI and input/keyboard
  paths. Pixel evidence, interaction checks and visual review remain distinct;
  baselines MUST not be silently approved. (DX-VISUAL-001)
- **NFR-003**: Changes MUST follow spec, implementation, then acceptance/E2E updates,
  with formatting/lint/build/host gates, affected device suites, traceable IDs and
  explicit unrun/manual/deferred gaps. New lint findings fail beyond auditable
  existing debt. Architecture and operational guidance stay outside this product
  specification. (DX-MAINT-001)
- **NFR-004**: README MUST remain a concise setup/install/quick-use entry point;
  release changes belong in CHANGELOG. Raw logs/screenshots and provenance MUST
  live under `testresults/<version>/`, with runs separated by timestamp. Preserve
  historical log bytes and valid local references. Specs MUST retain this
  story/requirements/outcomes structure. (DX-DOC-001)
- **NFR-005**: Release automation MUST validate tag/app version/changelog, run host
  quality checks, build and verify signed APK/AAB artifacts with one persistent
  release key, and publish versioned assets/checksums with concise changelog notes.
  Failures MUST prevent publication; retries may repair drafts but MUST not
  overwrite published assets. Signing material MUST stay private. Manual dispatch
  uses an existing tag and the same gates. (DX-RELEASE-001)
- **NFR-006**: Requirement coverage MUST list stable requirement IDs, acceptance
  criteria, mapped test cases, scenario dimensions and explicit gaps. Every test
  addition, modification or removal MUST refresh the generated inventory and
  source fingerprints; local/CI checks MUST reject stale coverage. Mapping is
  distinct from execution, visual approval and measured performance. (DX-COVERAGE-001)
- **NFR-007**: Before each push and release, two newly created independent review
  agents MUST inspect the completed candidate and both explicitly approve. Record
  their distinct identities, scope, verdicts and candidate digest. Missing,
  rejected or stale approvals MUST block publication. Any candidate content
  change except recording those approvals invalidates them. Review records are
  auditable attestations, not cryptographic proof of agent identity. (DX-REVIEW-001)

### Key Entities

- **Desktop session**: Readiness, attached monitor, selected app, session orientation,
  resolution/UI size and active input ownership. Initial orientation is landscape;
  subsequent sessions remember the chosen default.
- **Monitor capabilities/profile**: Reported name/modes/metrics, capability identity
  and confirmed desktop dimensions/DPI; transient display IDs are not identity.
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
  click, general keyboard relay, freeform window management, orientation thumbnails,
  physical signal switching and arbitrary-angle/automatic monitor rotation are deferred.
- Locked-device operation, guaranteed DRM playback, simultaneous independent
  instances of every app and replacement of Android's window manager are out of scope.
