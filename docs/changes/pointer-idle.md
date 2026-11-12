# Change: Automatic pointer hiding

The user requests pointer hiding while idle for watching movies. Existing
menu-readability/Settings-search work is the authorized dirty base; no release
or history modification is requested. Agent OS is off: communication stays in
chat, with no dashboard plan/review/document calls.

## Contract and preflight

FR-043 / DX-CURSOR-001 specifies five idle seconds and a 200 ms fade, immediate
restoration and first-input delivery, held-gesture protection, physical-touch
precedence and cancelled stale lifecycle work. Automatic behavior adds no menu
setting or permission. Scope is DexDroid's custom touchpad/controller pointer;
Android's native mouse cursor remains platform-owned. Timing is a sensible
default within the explicit requested behavior, not a new layout decision.

The architecture impact is timer/fade ownership in SessionService and held-input
notifications from the phone Pad and relayed gestures. Fresh independent read-only
architecture/UI CLI preflights completed before dependent implementation;
collaboration worker creation was unavailable due to the session thread limit.
The CLI reviewers recovered from an initial Windows read-only shell denial and
returned the findings below. Existing
input paths, display IDs, event payloads, arbitration and shell queues must retain
their semantics. No video-app detection, service authority, IPC or persistent
pointer-state expansion is intended.

Architecture reviewer `01a1d098-7366-7f01-bb28-3e363de4f74c` returned
**approve-approach**, requiring separate raw Pad/contact leases, a main-thread
service timer, activity/session/monitor stale guards, centralized visibility and
transition cleanup. UI reviewer `01a1d098-7366-7770-a893-63f8c4e43b29` recommends
automatic five-second hiding, immediate wake/first-tap delivery and held-contact
protection; no material human choice remains. It suggested a roughly 180 ms fade;
the documented 200 ms duration is the same short transition and a routine timing
default. No new setting or hardware-cursor promise is selected. These are actual
independent preflights, not runtime, final code or release approvals.

## Implementation and verification

`PointerIdle` is a pure monotonic-time policy. SessionService owns one main-thread
deadline and cursor animation; both callbacks validate activity revision, service
generation, display, monitor and cursor identity. Opaque per-source hold identities
protect raw Pad, relayed and controller contacts independently, including across
service recreation. Intermediate finger lifts retain the raw hold. Cancellation
cannot release another owner's contact. Existing Pad pause/destroy cancellation is
reused; an initial duplicate override was removed after a compile failure.

Final `spotlessApply qualityCheck` passed in 13 seconds: 295 JVM tests passed,
including five policy tests. Tooling: 203 passed, eight Windows skips. No lint
baseline change. The native acceptance compiles and exercises real Pad events,
composed pixels and animation/wake races, but Xiaomi rejected the separate test
APK twice with `INSTALL_FAILED_USER_RESTRICTED`; no instrumentation suite ran.

The final development app was installed in place on the physical Xiaomi 14,
Android 16, with a 1920×1200 monitor. A separate manual ADB test through the real
phone touchpad and synthetic input fixture confirmed idle hiding, visibility
during a stationary hold past the timeout, hiding after release and immediate
wake. Author-inspected native screenshots show zero taps before and exactly one
tap after the first wake tap. Pixel counts distinguish the cursor from the five
constant synthetic targets. This is a bounded manual check, not the blocked
instrumentation suite or a performance certification.

The two modified architecture diagram blocks were rendered and inspected;
four unchanged blocks were syntax-rendered only. Setup/onboarding is unchanged.
The spec, README, User guide, design rules and coverage map are reconciled.
[Evidence summary](../../testresults/0.18.0-pointer-idle/summary.md) records exact
source/build inputs, private archive, commands and remaining gaps. No final
publication approval, commit, push or release is claimed or requested.
