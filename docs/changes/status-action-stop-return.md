# Change: Plain status action and reliable Stop return

## Contract (written before implementation)

- Requirement IDs: FR-001 / DX-UI-001; FR-017 / DX-LIFE-002.
- Play/Stop shares the status surface: white icon, transparent resting background,
  independent 48 dp target, bounded ripple and current accessible action name.
- Explicit Stop returns the existing phone controller after both pane cleanup and
  Android task migration; Settings and Start remain usable, with no implicit restart.
- Newer startup/teardown generations or released/replaced UI owners invalidate old restoration. Generic passive
  cleanup does not foreground the controller or create one. Existing unrelated phone
  tasks are not closed.
- Affected surfaces: phone status and monitor Controls; single/split sessions.
- Spec owners: [baseline](../../SPEC.md), [controller UI](../specs/controller-ui.md),
  [desktop session](../specs/desktop-session.md), [design](../DESIGN.md).

## Implementation

- ControllerStyle shares one transparent masked ripple and white stateful icon.
- SessionService captures the existing MainActivity app task and restores it after
  its serial cleanup worker, guarded by generation, pending-start state and captured live UI owner.
- DeskService waits for the retired display to disappear from Android task metadata;
  VirtualDisplay.release alone precedes task migration. DesktopTasks uses a two-second
  retry deadline on the shell worker; Binder-call stalls are not bounded by that clock.
  Failures release remaining owned resources, propagate and are logged by the service.
- Primary and secondary cleanup results are aggregated before restoration. A failed input drain or final Stop/handoff
  fault suppresses task movement and provides current-session uncertainty/reopen
  guidance; obsolete faults cannot replace a newer session status.
- An asynchronous app-close warning may fall back to display release; foreground
  return still requires the final task-handoff barrier to succeed.
- Removed MainActivity's 250 ms focus timer. No startup Intent is used for restoration.
- No lint baseline, display sizing, gesture mapping or navigation layout changes.
- No new interaction-latency claim. This work needs correctness checks of cleanup
  completion; the retry deadline is not a measured latency distribution.

## Acceptance

Current outcomes, commands, source fingerprints, screenshots and limitations are
recorded in the [versioned evidence](../../testresults/README.md).
This is an unreleased change after 0.12.0, retaining version name/build 0.12.0/33.
The connected phone receives a debug test build; the published release is unchanged.

- Native UI checks cover running/Start white icons and transparent backgrounds,
  independent targets/actions, state labels and remembered disclosure.
- Single and split checks hold the actual cleanup worker for over 700 ms, remove
  owned displays, require foreground stability after opening/closing Settings and
  capture the actual phone screen. Existing restart and fresh input checks follow.
- Lifecycle boundaries cover a new Start before old cleanup completes, Home before
  old callback delivery, passive teardown and explicit launcher reopening.
- Primary/secondary bridge-reported faults are injected after real Stop. Tests require
  no foreground return, released displays, recovery status and explicit relaunch; an
  old fault while a newer Start is pending must preserve the new session/status.
  This does not simulate an actual Android metadata timeout or stalled Binder.
- Monitor PixelCopy verifies white icon contrast on dark status in four rotations.
- Earlier null-label and singleTask test-driver errors are retained. An initially
  reported UI pass is rejected by its screenshot showing the external app on the
  phone; the corrected test now requires sustained foreground after Settings closes.
- Android 16/17/OEMs, actual cable/Shizuku loss, missing-controller and notification
  paths, TalkBack, broader font/DPI/Fit/peripheral combinations and full physical
  performance remain unverified. No new golden baseline or human visual approval.
- Publication/release is outside this change request. Candidate reviews are required
  before any commit/push by the repository workflow; no approval is inferred.
