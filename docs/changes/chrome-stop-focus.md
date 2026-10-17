# Change: Native Chrome Stop returns to the phone controller

## Contract

- FR-017 / DX-LIFE-002: launch Chrome in Single view, press phone Stop and leave
  both screens untouched. The existing display-0 controller must retain focus
  for 15 seconds after cleanup, including repeated launches of Chrome's task.
- Run this check outside app instrumentation and without opening Settings after
  Stop. Instrumentation/test-app focus results alone do not establish the native
  Chrome behavior reported by the user.
- Preserve the prior cleanup-fault, newer-session, live-owner and passive teardown
  guards. Do not create a controller, restart a stopped desktop, force-stop Chrome,
  close its tabs or manipulate unrelated phone tasks.
- Spec and requirement criterion/gap were updated before production changes.

## Implementation

`SessionService.pauseDesktop` captures its existing controller AppTask, then the
serial desktop worker performs confirmed single/split cleanup, a native window/input
transaction barrier and the return. A metadata-only fence can precede the actual
window handoff and leave JoyUI with a blank, unfocused phone; both focus-only and
full task-front prototypes exposed this in native repeated runs.
Its generation, requested state and live UI-owner reads are volatile. The primary
shell helper validates the requested ID against the sole MainActivity task on
display 0 before calling Android's task-front API and synchronizing its window/input
transition. A restarted helper desktop,
unknown task, non-controller task or shared root is refused. Failure exposes
phone-reopen guidance; a missing task is skipped. No foreground polling loop or
new Activity Intent is introduced. The phone and monitor Stop actions remain
shared; their visual design is unchanged.

The old AppTask call reported no error while Chrome retained phone focus. Native
events show its task being foregrounded by display removal and Chrome recreating
its Activity for the phone configuration. Android's normal AppTask return path
also checks background-start policy; instrumentation may exempt the caller.
See the primary [Android 11 AppTask implementation](https://android.googlesource.com/platform/frameworks/base/+/android-11.0.0_r48/services/core/java/com/android/server/wm/AppTaskImpl.java).

`tools/check_stop_focus.py` drives real phone Apps/Chrome/Stop actions through ADB,
verifies external placement and observes display-0 focus plus desktop absence.
It records installed/local APK hashes and rejects a mismatch by default. Its
explicit diagnostic override is for comparisons against older products. It runs
separately from instrumentation and the existing `--suite all` runner.

## Acceptance and evidence

Current results, raw commands/configuration, negative comparison, source/APK
fingerprints, captures and limits are retained in
[the Chrome Stop evidence](../../testresults/README.md).

- Base: `a077c445e422049071adcfc95d020b2253cb0cae` plus the recorded working tree.
- The earlier commit was rebuilt in a detached local worktree for the native
  negative comparison. Cycle 1 passed; cycle 2 failed with Chrome focused. Its
  installed APK differs from the current local APK; both hashes are retained.
- The shared acceptance suite now also rejects unknown/non-controller task
  returns and a return while the helper has a running desktop.
- Formatting/build/lint use `qualityCheck`; the lint baseline is unchanged.
  Requirement coverage and tooling outcomes are recorded separately from device
  pass rates. Native focus samples are correctness evidence, not physical touch
  latency measurements or performance certification.
- Android 16/17/OEM native return, absent-controller/notification paths, actual
  OS metadata denial/timeout, Binder stalls, process death, physical dependency
  loss and broad accessibility/peripheral combinations remain unverified.
- This is an unreleased debug follow-up; app version remains 0.12.0 (33). No push,
  PR, tag or release is authorized by this fix. Exact-candidate approvals live in
  `reviews/approvals.json`; publication would require the protected PR/CI gates.
