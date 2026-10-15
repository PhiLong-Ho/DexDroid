# Task handoff and phone-controller resume

## Problem and contract

On Samsung Android 16, opening Carousell on the phone reuses its external task.
The virtual desktop becomes empty, but TextureView retains its last image and
the launch-success flag continues to hide idle. The prior compatibility report
also retains an intermittent controller-focus assertion failure after background.

The baseline spec now requires responsive singleton controller recovery and an
event-driven, session-scoped idle restoration when the last task leaves (FR-005,
FR-016, FR-025 / DX-HANDOFF-001). Another app remaining on the desktop stays visible.
Phone tasks must not be automatically stolen back. Query errors are unknown state,
not proof of emptiness.

## Implementation

Build 22 introduces the shell DesktopTasks observer and IDesktopListener. It
coalesces platform notifications, reads authoritative modern/legacy task metadata
off the UI thread, suppresses duplicate state and tears down with the display.
SessionService applies generation/display-checked updates; empty state cancels
contacts, clears the absent selection, hides the cursor and restores existing idle.
The phone controller uses singleTask and listener ownership/resume reattachment.
Privileged reflection is confined to the UID-2000 shell observer; its narrow
PrivateApi suppression is documented in source, with no lint-baseline rewrite.

## Acceptance and evidence

The new child acceptance reproduces the missing idle transition against build 21
using a build-22 test APK. It retains actual failed raw evidence. The deterministic
fixture moves its own task with the shell, separately from real Carousell testing.
Post-change checks cover four rotations, pixels, usable restored launcher, state
and relaunch, Fit preview's queued-launch contract, a remaining app, and last-app
exit. Idle acceptance observes completed background transitions and measured UI
targets before real phone input; focus, same-controller reuse and external input
remain explicit assertions. Original fixed-delay failures are not reclassified.

Samsung build 22 passes all eight suites in one full run, plus a real Carousell
phone-launch/idle restoration smoke check. Android 17 idle/resume passes, but the
latest child run still loses an intermittent rotation-2 fixture tap; an earlier
pass is not reliable full acceptance. Remaining-app captures also expose separate
size/crop margins. Current Android 11 helper regression remains unrun. See the
[scoped report](../../testresults/0.11.1/task-handoff-and-resume/README.md) for
exact APK/source identity, raw failed attempts, captures and limits. No publication
is part of this local fix.
