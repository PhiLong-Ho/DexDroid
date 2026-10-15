# Feature Specification: Modern Android compatibility

**Feature Branch**: `fix/modern-android-compatibility`

**Created**: 2026-10-15

**Status**: Implemented; final emulator acceptance and human layout review in progress.

**Input**: Check Android 16/17 emulators, investigate an older-Android warning,
and fix external apps hidden behind the idle background until Controls opens.

## User Scenarios & Testing

### User Story 1 - Launch visible external apps (Priority: P1)

**Why this priority**: Successful launch is unusable if the idle surface covers it.

**Independent Test**: On each OS, start an unused desktop and launch Input Test
at all four rotations; inspect rendered pixels and send input before any menu.

**Acceptance Scenarios**:

1. **Given** an idle desktop, **When** an app is successfully placed there,
   **Then** the idle surface disappears and app pixels/input work without a
   top-edge gesture or any other menu interaction.
2. **Given** a rejected launch, **When** placement fails, **Then** the failure is
   reported and controls remain usable; an unused desktop retains its idle view.
3. **Given** legacy Stack or modern RootTask reports, **When** placement is
   checked, **Then** display/task identity and fullscreen state are interpreted
   without invoking an unavailable API unnecessarily or moving shared tasks.

### User Story 2 - Start a modern foreground session (Priority: P1)

**Why this priority**: Raising the target SDK requires actual runtime migration.

**Independent Test**: Install on clean Android 16 and 17 emulators, inspect SDK
metadata/first launch, start Shizuku as shell, and background/resume the phone UI.

**Acceptance Scenarios**:

1. **Given** a fresh installation, **When** it opens, **Then** target API is at
   least 36 and no deprecated-target dialog appears on the tested emulator.
2. **Given** an authorized bridge and secondary display, **When** a session starts,
   **Then** its foreground service starts with a declared appropriate type;
   notification permission denial leaves the desktop usable.
3. **Given** visible system bars/cutouts or a shown keyboard, **When** phone
   controls/menus open, **Then** header/Close and scrollable content remain usable.
4. **Given** a running desktop, **When** the phone controller backgrounds/resumes
   or Back is used, **Then** the external app, input and menus continue correctly.
   Hardware Back closes the monitor menu once and reaches the app only when closed.

### User Story 3 - Keep an independent monitor host (Priority: P1)

**Independent Test**: Destroy the phone controller with no service bindings;
assert the same desktop remains visible and accepts monitor input, then Stop
closes its host. A stale host must not attach to a newer session.

**Acceptance Scenarios**:

1. **Given** Android 16/17 Presentation host-task policy, **When** the phone task
   is destroyed, **Then** an independent monitor Activity retains the surface,
   app and input without overlay permissions or policy bypass.
2. **Given** Stop/disconnect or a newer session, **When** an old host attaches or
   is destroyed, **Then** it cannot replace or stop the current session.

3. **Given** an OEM monitor that permits Presentations but cannot host Activity
   tasks, **When** desktop startup runs, **Then** public launch eligibility selects
   a Presentation fallback, app pixels and input work, and diagnostic status
   identifies the fallback's task-lifetime limitation. Backgrounding the phone
   and destroying its task are recorded separately, without claiming independent
   host coverage for a Presentation-only monitor.

### User Story 4 - Navigate inside third-party apps (Priority: P1)

**Independent Test**: Launch a different-UID fixture, tap its control to open a
non-exported singleTask child, interact with it, then Back to its parent. Repeat
at all four rotations and with the phone controller backgrounded. Retain a
pre-fix failure and post-fix outcomes. Check real Tachiyomi separately.

**Acceptance Scenarios**:

1. **Given** a third-party app placed by the authorized shell, **When** the app
   opens its own private reader/detail Activity, **Then** both the task and child
   remain on the virtual desktop, its pixels replace the parent's, and fresh
   monitor input reaches the child.
2. **Given** the child is open, **When** Back is requested, **Then** the original
   parent returns on the same desktop without relaunching or discarding its state.
3. **Given** Android 12+ shell permissions, **When** the desktop is created,
   **Then** the shell requests a trusted display. Permission rejection reports a
   creation error. Android 11 retains its legacy display flags.
4. **Given** a phone app unrelated to the desktop, **When** it is launched on the
   phone, **Then** DexDroid does not monitor/repeatedly reparent it as a workaround.

### User Story 5 - Return to responsive controls and an accurate desktop (Priority: P1)

**Independent Test**: Repeatedly background and reopen the phone controller after
physical monitor input; verify default-display focus and a real phone action.
Move a different-UID desktop task to the phone, inspect rendered idle pixels,
then relaunch it externally. Repeat in four rotations and Fit.

**Acceptance Scenarios**:

1. **Given** a completed controller background transition, **When** the launcher
   reopens DexDroid, **Then** a single controller accepts fresh phone input while
   the existing desktop remains usable. Tests observe both background completion
   and foreground completion instead of racing an unfinished move-to-back.
2. **Given** the last desktop task, **When** the same app is opened on the phone
   and Android moves it, **Then** the monitor shows the existing idle launcher,
   never a frozen prior frame, while the phone retains its task and state.
3. **Given** another app task still on the desktop, **When** a phone launch removes
   one task, **Then** the remaining desktop app remains visible.
4. **Given** a stopped/recreated desktop or failed occupancy query, **When** old
   callbacks arrive, **Then** they cannot change the current desktop state.

### Edge Cases

- Permission denial, Shizuku unavailable, launch rejection and stopped desktop.
- Existing tasks, multiple tasks in a root, other users, missing task metadata,
  fullscreen already selected and legacy-only fullscreen forcing.
- Modern edge gestures begin inside the observed Android system-bar boundary;
  the recognition band includes that inner edge, while touches in Android-owned
  bars keep system behavior. Full swipe travel and outcome assertions remain.
- Four monitor rotations and a mismatched aspect-ratio Fit preview.
- Emulator secondary displays simulate a monitor; physical HDMI, OEM task rules,
  installer/Play Protect reputation checks and exhaustive third-party behavior
  remain separate verification gaps.

## Requirements

### Functional Requirements

- **FR-001**: Implement baseline FR-023 / DX-COMPAT-001 and FR-025 / DX-HANDOFF-001.
- **FR-002**: Preserve baseline launch, idle, input, menu and lifecycle contracts.

### Key Entities

- **Task snapshot**: Root/stack ID, display/user, task membership, visibility and
  fullscreen evidence used to verify placement and avoid unsafe task movement.
- **Compatibility run**: OS/API/fingerprint, emulator/display configuration,
  installed APK hashes, shell privilege setup, outcomes and rendered captures.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Android 16 and 17 reproduce the baseline launch defect or record
  precisely why it differs; corrected builds pass visible launch/input in all
  four orientations before Controls is opened.
- **SC-002**: Pure task-format tests and affected native suites pass; retained
  evidence distinguishes actual rendering from service state/assertion counts.
- **SC-003**: Modern target, foreground startup, notification denial, insets and
  Back behavior are checked without claiming physical/OEM certification.

## Assumptions

- Non-root application; Shizuku runs as the ordinary authorized ADB shell user.
- Android 16/17 Google APIs x86_64 images with a simulated 1920x1200 monitor.
- Follow-up physical coverage uses Samsung SM-S928B Android 16 and its 1920x1200 HDMI monitor; this does not certify other OEM task policies.
- Target migration is not a Play Store eligibility or developer-verification claim.

### Bounded host-attachment recovery

For FR-017 / DX-LIFE-002, an accepted Activity request without a host attachment
must time out after five seconds, tear down pending session/wake-lock state and
offer phone-reopen recovery. Stop/attachment cancels the deadline. A late host's
generation/token and an obsolete timeout must not affect the recovered desktop.
Controlled component fault injection covers this state; actual OS background-launch
denial remains a distinct modern-device scenario.

Launcher reopening must recover through the existing bound controller. Resuming a
pending request retries; focus restoration after explicit Stop remains stopped.
Native acceptance exercises launcher intents rather than direct service retry.
