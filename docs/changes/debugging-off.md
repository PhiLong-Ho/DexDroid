# Change: Explicit debugging shutdown

## Contract (before implementation)

- FR-037 / DX-DEBUGGING-001; preserves FR-017 Stop and FR-036 audio release.
- Separate Settings confirmation on phone/monitor; stops desktop, then disables
  only wireless debugging, Developer options and USB debugging. Pairing retained.
- No persistent product WRITE_SECURE_SETTINGS, embedded ADB or re-enable path.
- Per-flag shell readback; unknown transport/process outcomes are not success.
- Tests: pure policy and operation-token cases; shared menu/cancel/failure native
  acceptance and affected lifecycle/audio checks; explicit destructive real-device
  flag/bank checks after ordinary tests. Bank evidence stays private, no login.
- Full unrelated device matrix excluded; device/install prerequisite pending.

## UI preflight

- `/root/debugging_off_ui` reviewed complete task flow read-only and recommended
  Settings beside Shizuku setup, shared explanation, explicit Stop & turn off,
  unchanged ordinary Stop, phone result after monitor disappears, manual recovery.
- User authorized implementation after real-device audio testing/release in their
  2026-10-21 request. No unresolved material layout decision; bank device/install
  choice is pending and does not block implementation or synthetic testing.
- Cancel/Up/Close before confirmation mutate nothing. No extra main-row controls.

## Architecture preflight

- Boundary impact: primary shell IPC, service ownership, serial display cleanup,
  independent audio worker, navigation-only phone handoff and interrupted receipt.
- `/root/debugging_off_arch` approved bounded one-way approach with cleanup barrier,
  explicit unknown result, stale-token checks and no persistent permission grant.
- Fresh follow-up `/root/debugging_off_lifecycle_arch` explicitly APPROVED the
  approach after source inspection: own started/foreground lifetime; independent
  operation token; real audio-worker drain acknowledgement (including in-flight
  begin); serial display barrier; no writes after stale/timeout/failed cleanup;
  durable pending/terminal receipt; fixed primary-helper operation. These are
  implementation conditions, not evidence of runtime success.
- [Feature overview/sequence](../architecture/features/debugging-off.md).

## Acceptance

Implemented fixed primary-only shell IPC (`DebuggingPolicy` / `DebuggingControl`),
shared `DebuggingOffPanel`, one-way service coordination and navigation-only phone
result route. Version0.15.0-dev/code41 forces Shizuku to load the changed helper
AIDL; it is not an official release candidate. Product manifest adds no permission.

Follow-up expert `/root/debugging_off_lifecycle_arch` identified stale pending
persistence and early timeout teardown; both were fixed. Pending/terminal writes
share one receipt executor, early failures request local teardown, and picker/
Start/launch paths share the guard. Tests reproduce early timeout in one main turn
and a blocked desktop worker, then confirm zero late helper mutations and durable
terminal receipt. The expert's remaining picker-guard correction was applied;
this preflight/follow-up is not final candidate or release approval.

[Current scoped acceptance and private archive index](../../testresults/0.15.0-dev/debugging-off/README.md):
128 native assertions/19.616s plus173 audio assertions/18.508s on Xiaomi14 Android15
and1920x1200 monitor.216 JVM outcomes pass;210 tooling passes/1Windows privilege skip.
qualityCheck9s; mapping/fixture/docs/privacy/Ruff checks pass. The native settings
transport is controlled and non-mutating; no actual debugging flags changed.

Actual rendered reviewer `/root/debugging_off_render_review` approved seven settled
current states. Earlier250ms captures showed transition ghosting;1000ms phone
capture settling resolved it. Four new/changed Mermaid diagrams rendered and
were inspected (all six extracted diagrams rendered). No product latency claim.
No lint baseline change; the durable off-main-thread preference commit has an
explicit justified suppression, and manual fallback setText uses a resource.

Real flag switching/master Settings cascade/ADB shutdown and HSBC/PayLah warning
screens remain pending the user's device/install choice: neither app is installed
on Xiaomi. No login is needed or authorized. These gaps prevent claiming complete
feature acceptance. Private evidence is local-only; no captures/dumps uploaded.

Audio v0.14.0 is separately published and installed before this development build;
it is not debugging-off evidence. No new release is requested for this feature
yet; no push before exact-candidate independent approvals and required checks.
No reusable workflow change requires agent-dev-template synchronization.

## Xiaomi recovery follow-up (2026-10-21)

User reported that Developer options cannot open until enabled manually. Fresh
UI expert `/root/debugging_hidden_options_ui` approved retaining the shortcut and
adding always-visible conditional setup/recovery instructions in the existing
shared page, with no extra controls or material decision. This is deliberate
re-enabling for a later desktop session, not a required banking turn-off step.
Preserve the last flag receipt when a navigation launch fails. Architecture
impact N/A for this local guidance/feedback correction: no change to the reviewed
mutation IPC, service lifetime, token/receipt ownership or privilege contract.
Actual flag testing uses the preceding build; raw evidence and source attribution
remain separate from this later guidance-only delta.

Current guidance native run20261021T031430.668248Z passes152assertions/24.358s;
qualityCheck40s/216JVM outcomes pass. UI reviewer `/root/debugging_hidden_options_ui`
approved all12 pictured confirmation/result/recovery states at2026-10-21T03:15:49Z.
Updated catalog is bound to current source/APK/archive members. Current and earlier
[archive/result scopes](../../testresults/0.15.0-dev/debugging-off/README.md) remain
separate; old sealed bytes are unchanged. No exception-state or User guide render
approval is inferred. Actual bank observation is pending after second full attempt;
first actual attempt is an honest unknown due helper/observer loss, not a bank pass.

## Generic recovery follow-up (2026-10-21)

User rejected OEM-specific default copy and the hidden Developer options shortcut.
UI expert `/root/debugging_generic_recovery_ui` proposed Open phone settings,
generic manual switch checks and initially collapsed Use the desktop again help;
OEM paths remain optional guide examples. User approved with "proceed with this
direction". Implement the same shared panel on both screens, phone-targeted main
Settings, accessible expansion state, no flag writes when opening help/Settings,
and separate launch feedback preserving the receipt. No material decision remains.

Architecture impact N/A for this scoped delta: DebuggingOffPanel owns only local
disclosure visibility and navigation; service/IPC, privilege, cleanup, token,
receipt and persistence ownership are unchanged. No WRITE_SECURE_SETTINGS grant
or automatic re-enable path is added. Existing feature overview/sequence still
describes the unchanged boundaries; prose documents the recovery UI.
The earlier Xiaomi renders become historical, not approval of this new layout.

Verification plan: affected non-destructive native page checks, phone and four
monitor rotations including expanded/collapsed help and main-Settings navigation;
qualityCheck and required host gates. Real bank outcome stays separately pending;
no phone settings are changed during the UI correction. Full unrelated suites
and a new OS matrix are excluded for this local navigation/copy delta.

Completed API35 emulator run20261021T052414.494285Z passes222assertions/22.957s
with1920x1200 simulated monitor. Tests exercise help expansion/collapse and
intercepted ACTION_SETTINGS on both surfaces/all4rotations, preserving receipt/
session; no real settings writes. All12 pictured renders approved by
`/root/debugging_generic_recovery_ui` at2026-10-21T05:25:23Z. qualityCheck20s with
216JVM outcomes passes; tooling210passes/1Windows privilege skip in19.560s.
See the [current acceptance scope/archive](../../testresults/0.15.0-dev/debugging-off/README.md).
Physical phone remains unavailable pending the user's bank observation; no new
phone installation, actual OEM Settings launch or bank pass is claimed. Test
mapping retains the executable run entry; private helper methods are not separate
discoverable coverage entry points. No final candidate push/release requested here.
