# Feature Specification: Phone screen power

**Feature Branch**: `feat/phone-panel-power-prototype`

**Created**: 2026-11-13

**Status**: Approved for implementation; acceptance pending

**Input**: Turn the phone screen off without stopping the external desktop or
allowing accidental phone touches. Offer an optional in-app control and explicit
monitor-side restoration. The user approved true panel off with button restoration
after physical double-tap and Power prototypes failed on the Xiaomi.

## User Scenarios & Testing

### User Story 1 - Keep using the monitor with the phone dark (Priority: P1)

**Why this priority**: Prevent accidental phone input while using a touch monitor.

**Independent Test**: On an unlocked phone with an active external desktop, turn
off only the phone panel, verify monitor frames/input and lock state, then restore.

**Acceptance Scenarios**:

1. **Given** a running desktop, **When** the user turns the internal
   panel off, **Then** the monitor remains powered and the phone remains unlocked.
2. **Given** a dark phone, **When** monitor input is sent, **Then** visible output
   updates; command submission or a retained screenshot alone does not prove this.
3. **Given** an interrupted owner, **When** its renewable safety lease
   expires, **Then** the independent owner requests restoration of the same internal panel.
   Actual restoration is observed separately from the request's return value.

### User Story 2 - Restore the phone from the monitor (Priority: P1)

**Why this priority**: Recovery must work without searching for a hidden control.

**Independent Test**: Turn the phone panel off, open monitor Controls, select Turn
phone screen on and verify the phone controller resumes without desktop changes.

**Acceptance Scenarios**:

1. **Given** a dark phone, **When** Turn phone screen on is selected on the monitor,
   **Then** the same internal panel is restored and the external desktop continues.
2. **Given** a pending/uncertain result or disabled visibility preference, **When**
   monitor Controls opens, **Then** restore remains reachable and failure is readable.
3. **Given** phone-off mode, **When** Stop, disconnect, controller backgrounding or
   owner loss occurs, **Then** restore is requested before/independently of desktop
   cleanup; expired leases admit no later OFF work and do not renew themselves.
4. **Given** the default Off preference, **When** the app starts or the setting is
   enabled, **Then** the phone remains lit until the user explicitly selects screen off.
5. **Given** pending/off/uncertain state, **When** the phone receives touches, **Then**
   they do not operate the external app. System lock/Power behavior stays intact.

### Edge Cases

- An ambiguous internal display identity prevents power changes.
- Do not request blind phone touches while the production touchpad is exposed.
- Stop, monitor loss, helper loss and pending transitions need independent recovery
  tests; a timed prototype does not establish production recovery.
- Real panel power-off and a black but powered touch guard are distinct modes.

## Experience review

UI expert `/root/phone_screen_off_ui` recommends an optional plain icon before
Settings in the title row, plus screen-on in monitor Controls. After reviewing the
failed physical wake trials, the user instructed "Implement true phone screen off,
restored using button". The existing recommendation is the implementation default;
status/main actions/touchpad layout stay unchanged. A follow-up expert review
approved pending/disabled/recovery behavior. Settings shows the preference, current
phone state and reason, with explicit restore/retry available as needed.

## Architecture links

The [change record](../changes/phone-screen-power.md) retains the prototype and
production preflight separately. The [phone-screen architecture](../architecture/features/phone-screen-power.md)
owns the new service/helper/independent-actuator lease and failure ordering.

## Requirements

- **FR-044 / DX-PANEL-001**: Optional explicit selective panel power, monitor-button
  restoration, shared pending/unknown/failure feedback, phone input blocking,
  terminal cleanup and expiring independent recovery; no automatic off on startup.
- Never disable authentication, remap Power, change security settings, loop over
  all physical display tokens or treat a successful command exit as visible proof.

## Success Criteria

- Separate observed results for selective off/restore, fresh monitor input/output,
  double-tap touch delivery and physical Power. Missing physical input is unverified.
- Record device/ROM, methods, timing, raw private evidence and unresolved states.

## Assumptions

Only the connected Xiaomi 14 / Android 16 is initially available. Android 11 and
other ROMs require later verification. Implementation/testing is authorized; no
publication is requested in this increment.

## Feasibility outcome on Xiaomi Android 16

Selective internal-panel off and explicit restoration work in the bounded private
prototype while leaving the external output active. Injected double taps reach
the guard, but the user-confirmed physical double-tap trial does not wake it.
Physical Power performs normal system sleep/lock and interrupts external output.
Neither wake method is supported by this tested approach on this device. A
monitor-side screen-on action remains the proposed recovery; product ownership,
lifecycle restoration and rendered UI acceptance have not been implemented.
