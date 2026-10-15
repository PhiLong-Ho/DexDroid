# Feature Specification: Named monitor profiles

**Feature Branch**: `feature/monitor-profiles`

**Created**: 2026-10-15

**Status**: Implemented; host/native checks passed on Black Shark 4. Both layouts approved by user; remaining scenario gaps are recorded.

**Input**: Create multiple monitor profiles, switch/apply them to different
monitors, and remember/reapply the profile matched to a monitor automatically.

## User Scenarios & Testing

### User Story 1 - Save and switch display setups (Priority: P1)

Save the current working resolution, UI DPI and monitor orientation under a name,
then switch setups from either screen's Settings > Monitor profiles.

**Why this priority**: Profiles must be usable without repeated configuration.

**Independent Test**: Save two different configurations, select each, verify its
preview and Keep, and inspect the same named selection on both screens.

**Acceptance Scenarios**:

1. **Given** a connected ready desktop, **When** Save current creates a uniquely
   named profile, **Then** its current configuration and monitor binding persist.
2. **Given** two profiles, **When** one is applied, **Then** its settings preview;
   Keep saves the binding, while Revert/15-second timeout restores the prior
   display/default orientation and binding.
3. **Given** a saved profile, **When** Rename or Update from current is selected,
   **Then** only that profile changes. Empty/duplicate/overlong names are rejected.
4. **Given** a saved profile, **When** Delete is confirmed, **Then** its bindings
   clear and the active display is left unchanged.

### User Story 2 - Recognize a returning monitor (Priority: P1)

**Why this priority**: Plugging in a monitor should restore its known setup.

**Independent Test**: Bind different profiles to two monitor identities, restart
the store/session and reconnect each; verify restored dimensions/DPI/orientation.

**Acceptance Scenarios**:

1. **Given** a valid remembered binding, **When** that monitor reconnects or the
   desktop restarts, **Then** its selected profile is reapplied automatically.
2. **Given** an unknown monitor or invalid/deleted profile, **When** startup occurs,
   **Then** safe output defaults apply without rewriting other bindings/profiles.
3. **Given** valid legacy per-monitor settings, **When** first encountered,
   **Then** they import once as a named profile with the previous default rotation.
4. **Given** indistinguishable reported identities, **When** the profile menu opens,
   **Then** it discloses the shared identity and offers manual switching/forgetting.
5. **Given** a bound monitor, **When** Forget is selected, **Then** its association
   and legacy fallback/working settings clear, and named profiles remain available.
6. **Given** a named binding or a previously forgotten monitor, **When** ordinary
   Resolution/DPI changes are confirmed, **Then** those working settings survive
   restart without editing a named snapshot. Applying/saving a profile clears the
   override; updating the bound profile clears this monitor's override.
7. **Given** confirmed working settings, **When** manual default rotation succeeds,
   **Then** that monitor restores the new orientation after restart, while named
   snapshots remain unchanged. Failed rotations do not commit a new default.
8. **Given** a stopped desktop with runtime dimensions from another setup,
   **When** Rotate selects a default for the currently detected monitor,
   **Then** its remembered/recommended dimensions and DPI are preserved on Start.

### Edge Cases

- Pending preview, busy/startup, no monitor or stopped desktop: no create/update/
  apply commits; explain the condition and preserve saved state.
- Resize failure, Stop, monitor removal or service recreation during preview:
  cancel the proposal; never bind unconfirmed settings to a different monitor.
  Stop releases the shell display before its backing texture, off the UI thread.
- Per-app rotation and one-time app launch overrides continue to take precedence
  on launch, without overwriting the monitor profile.
- Invalid persisted records are skipped safely. A deleted association never
  silently reimports old settings. Names are UTF-8 and remain readable.
- Phone uses its normal keyboard; monitor names use local keyboard input because
  the ROM cannot reliably place the Android IME on the monitor Presentation.
  Typing beyond 48 characters is safely filtered, including selection replacement.

## Requirements

### Functional Requirements

- **FR-001**: Implement baseline FR-020 / DX-MONITOR-001 for profile management.
- **FR-002**: Implement baseline FR-021 / DX-MONITOR-002 for transactional apply.
- **FR-003**: Implement baseline FR-022 / DX-MONITOR-003 for monitor matching.

### Key Entities

- **Monitor profile**: Stable profile ID, unique display name, dimensions, UI DPI,
  and default rotation; a snapshot until explicitly updated.
- **Monitor binding**: Persistent monitor identity to profile ID, separate from
  Android's temporary display ID and app-specific settings.
- **Working configuration**: Separately confirmed per-monitor display values,
  taking precedence over the named snapshot until a profile is applied/saved or
  the bound profile is updated; Forget clears it with the binding.
- **Profile preview**: Prior display/default rotation, proposed profile and monitor
  identity; committed only on Keep, otherwise canceled without changing binding.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Two profiles can be independently managed and reapplied after store/
  service recreation; two distinct monitor identities retain distinct bindings.
- **SC-002**: Keep commits one binding. Revert/timeout/failure/disconnect commits
  none and retains valid input/rotation mapping.
- **SC-003**: Phone/monitor menus match the shared Settings style with current
  selection, reachable actions, name input, Close/Back and outside dismissal.
- **SC-004**: Host tests cover persistence/validation/matching/migration; native
  acceptance covers the shared menus and service preview lifecycle. Unrun physical,
  visual, third-party-app and identical-hardware cases remain explicit gaps.

## Assumptions

- Desktop resolution is virtual; profile application does not switch the physical
  HDMI signal mode. Fit remains the existing automatic aspect-preserving mapping.
- Identity availability depends on JoyUI/adapter/EDID. No distinction is promised
  for monitors reporting the same identity, or identifiers changing across ports.
- Root is not required. One external desktop is active at a time.
