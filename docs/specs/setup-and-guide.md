# Feature Specification: Shizuku setup and offline user guide

**Feature Branch**: `feature/status-setup-guide`

**Created**: 2026-10-17

**Status**: Implementation contract; verification remains scoped in coverage.

**Input**: Recover missing/stopped Shizuku and learn the full app from Settings.

## User Scenarios & Testing

### User Story 1 - Prepare Shizuku (Priority: P1)

**Why this priority**: The desktop cannot start without its privileged service.

**Independent Test**: Inspect missing, stopped, unauthorized and connected setup
states and their user-initiated actions; preserve a working device during tests.

**Acceptance Scenarios**:

1. **Given** missing Shizuku, **When** DexDroid opens, **Then** collapsed status
   exposes install/setup guidance; Install opens the official download page.
2. **Given** installed Shizuku, **When** Open is used, **Then** its manager opens on
   the phone. Running-but-unauthorized users can request authorization; an existing
   requested desktop connects when the authorized binder becomes available.
3. **Given** non-root startup or reboot, **When** setup is read, **Then** it explains
   pairing/start through Shizuku wireless debugging or computer ADB. No silent start,
   install, permission grant or repeated external launches are attempted.
4. **Given** stale cached manager state, **When** setup opens on either surface,
   **Then** it rechecks installation/binder/authorization instead of offering an
   outdated action. The manager shortcut stays available while authorizing/reconnecting.
5. **Given** a missing activity/browser or denied launch, **When** the user opens
   a setup destination, **Then** actionable feedback remains in the setup page.

### User Story 2 - Learn controls in context (Priority: P1)

**Why this priority**: Less prominent controls need discoverable instructions.

**Independent Test**: Open Settings > User guide on both screens, scroll to its
last section and visit/return from related help in four monitor rotations.

**Acceptance Scenarios**:

1. **Given** either Settings menu, **When** User guide opens, **Then** the same
   concise offline text covers setup, app launch/orientation, split/resize, gestures,
   keyboard, scroll, app settings, display/profiles, background/Stop and diagnostics.
2. **Given** related setup/protected help, **When** Up is used, **Then** the guide
   returns at its old scroll position; X dismisses the entire modal flow.

### Edge Cases

- Browser/manager launch may be denied on an OEM or while backgrounded. Report the
  failure and preserve menu state; opening a manager never equals service startup.
- Package visibility must allow explicit Shizuku detection on Android 11+.
- Offline guide requires no network; only explicit external download/help actions do.

## Requirements

### Functional Requirements

- **FR-034** / DX-SETUP-001: State-aware actionable Shizuku setup and honest non-root
  startup limits; see the authoritative [baseline](../../SPEC.md).
- **FR-035** / DX-GUIDE-001: Shared full-feature concise offline guide and related
  navigation; status collapse remains owned by FR-001 / DX-UI-001.

### Key Entities

- **Shizuku setup state**: missing, stopped, authorization required, ready or connected.
- **User guide**: shared offline sections and related setup/protected-help destinations.

## Success Criteria

### Measurable Outcomes

- Both surfaces expose the same instructions/actions and follow shared Up/Close rules.
- Collapsed status cannot hide setup recovery, confirmation or protected-output help.
- Tests distinguish controlled state/launch-failure fixtures from real installation,
  wireless pairing, permission dialogs and reboot checks that were actually performed.

## Assumptions

- Official [Shizuku API requirements](https://github.com/RikkaApps/Shizuku-API#requirements)
  and [startup guide](https://shizuku.rikka.app/guide/setup/) describe non-root startup.
- No local ADB client, root service or silent Shizuku startup is introduced.
- [Coverage](../coverage.md) records checks/gaps; rendered approval and performance
  evidence remain separate from a mapping or successful build.
