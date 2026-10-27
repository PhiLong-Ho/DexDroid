# Change: Minimal read-only device capabilities

Created2026-10-28 on `fix/launch-feedback-android11-audio`, based on28a58fe.
Earlier authorized launch-feedback/audio work remains in the dirty tree; unrelated
architecture-skill/CODING_PRACTICE/default-tooling edits are excluded from this task.
No publication requested.

## Decisions and preflight

User approved the read-only first increment of BL-03: six current-state items,
truthful Available/Confirmed this session/Unavailable/Not checked, optional guided
test deferred. SPEC FR-040 and DX-CAPABILITY-001 are updated before implementation.

UI expert `/root/capabilities_ui_preflight` approves Settings General → Device
capabilities beside Diagnostic log, existing fixed plain icon header, scroll body,
Refresh/Diagnostic log actions and passive sections. Same semantics on both
surfaces; no main-row/touchpad change. User-approved scope covers this proposal;
no consequential unresolved UI choice. Rendered acceptance is separate.

Architecture impact: app-local state projection and bounded read collection plus
scoped ordinary-operation observations; lifecycle/concurrency/shared export need
review. `/root/capabilities_arch_preflight` approves service ownership, a pure
model, memory-only bounded data and owner guards. Void modern input returns are
not accepted-dispatch evidence. Audio policy readback is not audible output.
Report refresh consumes SessionAudio.state; it must not call audioQuery/refresh,
which can reconcile/release a policy. No new AIDL/probe/settings grant/persistence.

## Verification plan and current limits

Add pure reducer/ownership/refresh/privacy tests, then targeted shared-page
acceptance for both surfaces/navigation/invalidation/four monitor rotations/Fit.
Run qualityCheck, tooling, requirement/coverage/docs/privacy/format checks.
Inspect actual renders, record failures and unavailable hardware honestly.
Prior focused instrumentation timeouts remain failures, not current passes.
This plan is followed by the actual scoped results below.

## Implementation and source follow-up

The report uses `DeviceCapabilities`, a pure six-row projection, with an
identity-ticketed three-fact evidence ledger. `SessionService` supplies a complete
owner key and a capacity-one touch/scroll completion mailbox; modern void relay
returns are not promoted. Configuration captures its post-commit ticket before
the existing250ms settle callback. Metadata and evidence use separate owner tokens.

`CapabilityReader` owns one dedicated read worker and a capacity-one tail handoff,
not the input/audio workers. `CapabilityRefresh` bounds active/pending intent,
page leases, a2s UI deadline and stale-result rejection. An expired Binder read
cannot create replacement workers; its slot remains occupied until actual exit.
Read-only Shizuku classification preserves Unknown on failure; display stages
remain independent. The report consumes only published audio state.

Architecture follow-up found and corrected uncertain audio mapped as known
unavailable, readiness loss/recovery resurrecting old facts, and delayed
configuration confirmation minting a new ticket. Production audio projection and
readiness tests cover the corrections. `/root/capabilities_arch_preflight` inspected
the final synchronous readiness retirement and explicitly approved those source
invariants. This is separate from release/final-candidate approval.

## Actual checks and evidence

Development build remains0.16.1-dev/code44. Current installed APK SHA256:
`2a04f6a698b3f4f151dfde460517536446b79e5d8faa1b2a6bcdf7d374cdfda0`.
Test APK SHA256:
`fe824a23f2590a78462955750409a00b5f99236f660a7b6e54d7ed7bc5985d25`.
161 app/build source hashes match the candidate; private signing verifies
certificate and non-signature payload equality. No official production execution
or publication claim.

| Check | Current outcome |
| --- | --- |
| `spotlessApply qualityCheck` | Pass in11s,267 JVM cases, zero failures/errors/skips; no lint baseline change |
| `python tools/run_tool_tests.py --output .local/capabilities/tool-tests.json` | 210 passed,1 Windows symlink privilege skip,18.820s; reused unchanged Python/tooling source after final view/test-only correction |
| `--suite capabilities --install --timeout 90` | Android11 Black Shark/JoyUI, real1920×1200 monitor; final run20261027T164815.004628Z,550 assertions in15.022s |
| Native scope | Actual phone/monitor Settings entry, six passive items, wrapped/glyph-safe text, Refresh invariants, diagnostic Copy/Up/Close, four monitor rotations and1920×1080@240 Fit with rollback/cleanup |
| Rendered review | `/root/capabilities_render_review`,2026-10-27T16:49:34Z: approved all12 final phone/four-rotation/Fit top/bottom captures; no remaining visual findings |
| Diagram review | Seven current source-bound Mermaid blocks render; feature overview/sequence, system/component/thread maps inspected |
| Validation | Requirement/coverage, docs, privacy-path, Ruff and diff checks pass; coverage remains partial |

Native opening and Refresh preserve current display, layout, rotation, audio
selection and configuration. Copy remains immutable while later context changes.
On this ROM the page shows working desktop creation separately from known audio
routing denial; unreliable physical DPI stays unknown. No personal apps were
launched by this suite. The test APK was removed afterward and the phone is left
on the new report with a usable desktop.

Retained attempts:163906 failed the first monitor Refresh viewport assertion
after phone and monitor text checks. Test-owned smooth bottom scrolling competed
with immediate reveal; bounded native rectangle scrolling/frame synchronization
corrected the orchestration without removing reachability checks. Exact timing
was not captured in the first attempt, so the race remains an inference.
164356 passed546 assertions; rendered review found a nonblocking scrollbar/text
overlap. The final shared8dp passive-text inset fixes it and the new Fit-bottom
capture closes the prior pictured-state gap. One host formatting-expectation test
failed after readable refresh-rate formatting changed60.0 to60; its expectation
was reconciled while preserving export immutability assertions. Raw failures remain.

The report is not universal capability certification. Missing/dependency-loss,
unknown, timeout, stale results, independent audio and policy mapping have host
coverage; physical disconnect/Shizuku death during a read and real input observation
transitions were not device-stressed. Unpictured refresh/error/disconnected states,
large fonts, hardware keyboard/screen reader, otherOS/OEM and performance/acoustic
behavior remain unverified. The guided test screen remains deferred. No full
regression or release gates were run.

The [private evidence summary](../../testresults/0.16.1-dev/device-capabilities/summary.md)
records verified archive availability. [Current UI reference catalog](../ui-references/device-capabilities.json)
stores image/source hashes and the actual reviewer, not raw screenshots. Device
ZIPs/captures/dumps stay local only; no GitHub upload is authorized.

## Release0.17.0 follow-up

Current Xiaomi Android16 and Android17 simulated-display checks, retained
test-fixture failures, final source/APK bindings and release review gates
are recorded separately in [the release record](release-0.17.0.md).
The development results and gaps above describe their original runs;
they are not silently promoted to new-version passes.
