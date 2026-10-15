# Android 11 focus handoff and JoyUI protected-state clearing

## Contract before implementation

FR-029 / DX-FOCUS-001 requires semantic monitor taps across legacy global focus
changes, bounded asynchronous buffering, no stray UP or automatic tap retry.
FR-028 / DX-PROTECTED-001 requires complete metadata despite an OEM missing newline,
secure-to-ordinary notice clearing, conservative unknown state and safe teardown.
SPEC.md and requirement mapping were updated before the corresponding fixes.

## Implementation

SessionService.Monitor holds API-30 initial app contacts until host focus or a
100 ms settling fallback. Sixteen events maximum, adjacent MOVE coalescing,
original timestamps/order, main-handler scheduling, lifecycle/edge/overflow discard.
Other OS paths and display flags are unchanged. Diagnostics state release reason
and delay. Phone/monitor styles and actions continue sharing service ownership.
ProtectedWindows accepts the complete header at the line end when JoyUI omits the
preceding newline; other malformed metadata remains unknown.

## Acceptance and limitations

[Android 11 validation](../../testresults/0.11.1/android11-release-validation/README.md)
records inputs, outcomes, preserved failures, screenshots and timing boundaries.
ChildLaunchAcceptance retains different-UID semantic clicks, actual rendered
placement, four rotations, background/reopen, handoff and Back checks, and asserts
no focus loss during each reader contact. ProtectionAcceptance still toggles the
actual window flag and requires both transitions. The host parser test reproduces
the exact joined-header shape and rejects an incomplete header.

DeviceChecks now tears down and fences pending display work before instrumentation
kills its target; failures fail acceptance. No broad production watchdog fix is
claimed. No lint-baseline changes. Historical Samsung/Android 17 runs are scoped
separately, including unresolved Android 17 child input. Human visual approval,
full performance protocol, alternate hardware and deterministic overflow/stale
callback injection remain gaps. Review attestations and release report are owned
by the final candidate gate and CI; neither is inferred from local packaging.

## Independent review correction

The [build-27 review cycle](../../testresults/0.11.1/android11-release-validation/review-cycle-27.json)
did not authorize publication: one reviewer approved and one rejected missing
buffered-contact ownership/shared cancellation. Build 28 uses the authoritative
physical-input predicate during focus wait and discards pending contacts/timer in
shared monitor cancellation. Release detaches its bounded batch before normal
DOWN initialization. New MonitorAcceptance component instrumentation first gives
the real phone window focus with a native header tap, then dispatches monitor
contacts in one main turn to control the wait boundary. It attempts phone clicks,
wheel and pointer motion, then shared/navigation/touch cancellation or valid focus
release, and checks real external Input Test counters after the timeout. This
controlled callback test complements the existing native-dispatch semantic reader
and monitor suites; it is not physical-finger or touch-to-photon evidence.
Two fresh reviewers are required for the revised final tree.
