# Release candidate: 0.17.0

Version0.17.0/code45; preceding published release v0.16.0 at28a58fe.
User authorizes Xiaomi14 Android16 and one Android17 emulator testing before
release of Device capabilities. The cumulative candidate also contains the
previously authorized launch-feedback/audio-capability correction, whose source
has not yet been published. Unrelated existing architecture-skill,
CODING_PRACTICE and default-tooling edits remain excluded from this candidate.

## Scope and architecture impact

FR-040/DX-CAPABILITY-001 supplies the shared passive report; FR-039 supplies scoped
launch feedback. Required audio API signatures are resolved before editability.
No new public command endpoint, permission, active probe or OEM allowlist.
The [capability change](device-capabilities.md) and
[launch change](launch-feedback-android11-audio.md) retain source preflights,
current/historical checks and limits. This release preparation changes metadata
and verification scope; implementation impact is unchanged. A fresh cumulative
release architecture expert is still required after checks/evidence, followed by
two fresh independent final reviewers of the frozen staged candidate.

## Selected release checks

- Xiaomi14 Android16 with its real1920×1200 monitor: focused capabilities and
  synthetic launch-feedback acceptance, rendered review and relevant failed paths.
- Existing Android17/API37 emulator with a simulated secondary display: the same
  feature checks where supported, plus a bounded app/child smoke if necessary.
  Emulated displays do not certify USB-C hardware, physical peripherals or audio.
  Preserve experimental support and known platform limits unless evidence warrants
  a scoped revision; retain failures rather than weakening tests.
- Full required host build/lint/JVM/tooling, requirements/coverage/docs/privacy/
  fixture checks; reuse only fingerprint-valid evidence and record exclusions.
- Verify private evidence bundles and latest source-bound rendered references.
- Freeze/stage, fresh release architecture approval, exactly two fresh final
  approvals, gated PR/host check, merge, tag/release workflow and asset verification.

No additional Android12–15 or full-suite device matrix is planned. Earlier
Android11 development evidence stays historical/scoped, not a new0.17.0 run.
No publication result is claimed by this plan.

## Device validation

Xiaomi Android16 and API37 emulator capability suites passed on the same product
APK. First Xiaomi launch-feedback attempt170440 failed an immediate expanded-status
assertion: the existing disclosure posts its refresh to the next main-thread turn.
The test now waits boundedly for the real expanded/collapsed view, retaining its
visible-text and inside-bounds assertions. This is test orchestration only;
production code is unchanged and the failed raw run is retained. Earlier capability
evidence may be reused only with its original APK/harness binding disclosed.

The second feedback attempt170649 reached injected contact, then failed because
the point beneath the notice was on the native fixture's action bar rather than
its Button. A dedicated androidTest-only fullscreen subclass now removes that
fixture mismatch, and the test first checks the contact against the native
reported clickable bounds. Real DOWN/UP injection, child navigation and Back
assertions remain. No production input or feedback behavior was changed. This
local test-fixture correction has no product architecture impact; the existing
navigation fixture and other suites retain their themes and behavior.

Final source-bound checks use the same product APK SHA256
`c59c404a93315e9c1876a977c9236a2ade2ca187181e84b7f42dd37489d8c0bc`
and final test APK SHA256
`e2b90ef5cd647da60276625321d72ef257613d7da53af12fc94e4173221879b0`.
The capability suite was repeated once on this final harness to avoid mixing
its build binding with the earlier test-only revisions.

| Target | Capabilities | Launch feedback |
| --- | --- | --- |
| Xiaomi14 Android16, real1920×1200 monitor | Run171346.037211Z,550 assertions/15.310s | Run171235.508528Z,147 assertions/11.370s |
| API37 Android17 emulator, simulated1920×1200 display | Run171402.909355Z,550 assertions/16.857s | Run171255.722715Z,147 assertions/12.056s |

Run date2026-10-27 UTC; host local date2026-10-28. Capabilities checks cover six
shared rows, passive Refresh without setup mutation, diagnostic Copy and page
navigation, all four rotations and1920×1080@240 Fit. Feedback checks cover pending
phone/monitor, expanded text bounds, controlled native rejection/recovery,
four-rotation and split notice touch-through, Stop and deferred startup completion.
Controlled worker holds make pending state observable; timings are instrumentation
observations, not normal app-launch latency or a faster-launch claim.

Android17's boot identity stayed unchanged across these runs. This does not
resolve the historical framework display-teardown issue or establish universal
Android17 support; retain experimental status. Physical USB-C/peripheral/audio
behavior is outside emulator coverage. Hardware keyboard, large fonts/screen
reader, physical disconnect/helper death, acoustic routing, shortcut first paint
and latency distributions remain unverified. Android12–15/full device suites were
not rerun. Earlier Android11 checks are historical, not current release passes.

## Host, rendered and evidence verification

`spotlessApply qualityCheck` passes on the final fixture/source:267 JVM tests,
formatting, lint and build; no lint baseline change. Final tooling run reports
210 passed/211 total, one Windows symlink-privilege skip (18.765s). An intermediate
tooling run failed its repository-coverage freshness check after test formatting;
coverage was regenerated before the final passing run. Requirement validation
reports55 requirements (41 partial,9 mapped,1 deferred,4 manual); these are mappings,
not a universal pass rate. Coverage/docs/privacy/Ruff/fixture-runtime/diff checks pass.

Seven current capability/system diagrams and eight launch/audio/shortcut diagram
blocks were rendered and inspected in their source preflights. Their source blocks
are unchanged by version/test-only preparation; exact Mermaid inputs and rendered
outputs are retained with this release evidence. Task architecture and UI expert
preflights remain in the feature change records. This reuses diagram inspection,
not a release architecture verdict.

Rendered expert `/root/release017_render_review`,2026-10-27T17:15:43Z, inspected
all46 current PNGs using image viewing:24 capabilities (phone/top/bottom,
four monitor rotations/Fit) and22 feedback (collapsed/expanded/error phone and
rotation/split/error monitor). Explicit approval for pictured scope, no visual
blockers; no clipping/glyph/spacing issue. The emulator pending-r0 splash proves
notice rendering over that state, not settled application pixels. Accessibility,
hit behavior, timing and unpictured states are separate from this visual verdict.
Latest catalogs: [capabilities](../ui-references/device-capabilities.json) and
[feedback](../ui-references/launch-feedback.json).

The [compact private evidence summary](../../testresults/0.17.0/compatibility/summary.md)
and [descriptor](../../testresults/0.17.0/compatibility/archive.json) bind284 files
to verified ZIP SHA256
`b25cce393b5cc0a22e56ef578a5f3e77ec84954db61cfefbd7ddb1605e676d16`.
Archive/member/context hashes verified. Includes passes, failed preparation/tests,
source/APK inventory, signed debug/test payloads, host reports and diagram inputs.
Raw device evidence is local-only/private until owner cleanup; no GitHub upload.
Official host release reports/ZIP are generated from that release's own Actions
run; none are invented locally. Production signing/publication remains gated on
fresh architecture and two final approvals.

## Release architecture attestation

Fresh read-only expert `/root/release017_architecture`,2026-10-27T17:19:34Z:
**APPROVE, no architectural release blockers** for0.17.0/code45, cumulative from
published v0.16.0/`28a58fe1b71fc7e643c9426cbf0f547b85dacf9c`.
Exact inspected pre-attestation tree:
`f07aff72eee79590e5b56b2433035255fea2d8ad`; canonical digest:
`d46804b0477c6e09e819960d012e640107c94abcc3b30a0cd8507ba186ab7c14`.
This attestation was not part of that inspected snapshot.

The expert inspected the staged cumulative diff, whole-product/component and
affected capability/launcher/audio/shortcut diagrams against source, FR-039/040,
requirements/tests, privacy/delivery contracts and private evidence. Service/shell
ownership, display identities and shared surfaces remain consistent; no public
privileged endpoint, production dependency or persistence migration. Passive
refresh uses separate bounded work, leases/deadlines/owner tickets and stale-result
retirement. Launch tickets are separate from native/picker ownership and retain
deferred callbacks/guards. Audio editability resolves required APIs while existing
primary-only policy/epoch/restoration ownership remains authoritative. Diagrams
agree with source. A blocked framework read may retain its isolated worker.

Independent evidence verification: current ZIP and284 members, all162 tested
app/build source hashes against staged inputs with canonical newline normalization,
APK hashes,550 capability and147 feedback checks per target,267 passing JVM cases,
210 passing/one skipped tooling case, and both historical Android11 archives.
Gradle cache reuse and retained failures were disclosed. No files were edited.

Residual nonblocking gaps: historical Android17 teardown; physical USB-C/audio/
peripheral compatibility outside emulator scope; disconnect/helper death during
reads, actual capability-input transitions, shortcut first paint, expiry on-device,
accessibility/hardware-input matrix, acoustics and latency distributions. Android
12–15/full device suites were not rerun. Inherited generic session-input queue is
unbounded and helper-first audio death can strand an override; this release does
not repair or claim to resolve them. No unresolved release architecture decision
or blocker was identified. Final reviewers must verify this faithful attestation
is the only delta from the inspected snapshot and approve the full final digest.
