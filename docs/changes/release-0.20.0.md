# Release 0.20.0: phone panel power and reserved taskbar

## Candidate scope

User requested Android11 compatibility and publication after successful checks.
Version0.20.0/code53 follows published0.19.0/code52 at
`53bf44a5bb9e04433d143feaa593137ed863f1e8`. Work remains on the feature branch;
no release approval or publication outcome is claimed by this preparation record.

FR-044 adds optional true internal-panel power control with explicit monitor
restoration, guarded input, typed ownership/receipts and independent expiring
recovery. FR-045 adds the reserved Apps/Controls taskbar when edges are Off, at
4% of the output's shorter edge, with proportional icon-only controls independent
of app DPI. FR-005 adds optional centered Apps visibility for single/empty split
desktops. Preserve existing native app placement, input, rotation/Fit and split.

## Architecture and UI decisions

The [phone-screen change](phone-screen-power.md) and [taskbar change](monitor-taskbar.md)
record real UI/architecture preflights, settled user decisions and source-linked
diagrams. This release requires a separate fresh architecture review after checks,
then two fresh independent final approvals of the frozen candidate. No earlier
preflight is reused as release approval. Unrelated existing workflow/promotion
drafts remain outside the release candidate.

## Compatibility and current evidence

Physical Android11 Black Shark taskbar, input, phone UI and monitor suites pass on
the final development APK pair with the real1920×1200 HDMI monitor. Its ROM reports
phone-panel control unverified/unavailable and disables Off; no OFF was attempted.
The corresponding Android11/API30 emulator suites also pass with a software
secondary display. Neither suite scope certifies every peripheral/OEM behavior or
selective physical-panel power on Android11.
Physical Xiaomi14/Android16 feature evidence and the fixed-ratio DPI/rotation/Fit
checks are retained privately under0.20.0, with their own exact source/APK scope.
Unchanged evidence reuse is explicit, not a new run or an all-OS claim.

See the [physical Android11 outcomes and private archives](../../testresults/0.20.0/android11-physical/summary.md),
[Android11 emulator outcomes](../../testresults/0.20.0/android11-compat/summary.md),
[current local host results](../../testresults/0.20.0/release-host/summary.md),
[physical phone-screen evidence](../../testresults/0.20.0/summary.md) and
[fixed-ratio taskbar evidence](../../testresults/0.20.0/taskbar-ratio/summary.md).

The first Android11 taskbar run reached the fixture before its native input window
was ready. The test now observes one captured task/window/geometry before its
original contact, without replay. The legacy monitor cancellation fixture now
waits for the new asynchronous reserved-area resize before asserting resumed
phone input and restores geometry before its next case. Both affected suites pass
on rerun; input/UI evidence reuse is scoped to unaffected paths on the same app
APK. Product code was unchanged by these test corrections. The emulator reports
phone-panel control unverified/unavailable with Off disabled; no OFF was attempted.

Emulator product/Shizuku execution remains app/shellUID2000. Android11 scoped
storage denied both ordinary pull and app run-as capture retrieval; a separate
read-only emulator su0 operation recovered completed artifacts after tests.
It did not inject input, change app privileges or restart adbd. These retrieval
privileges do not constitute rooted product execution or physical-phone evidence.
The later physical runs used ordinary shellUID2000 Shizuku and capture retrieval,
without root. Existing phone data was preserved by in-place installation.

Host quality, structured tooling results, requirement/coverage/document/privacy
checks pass and current outcomes bind to the frozen candidate before review.
Raw logs/screenshots/APKs stay ignored/private; public CI archives only
its current guarded host run and generates its own release reports/checksums.

## Limits and delivery

Physical Power retains normal Android lock behavior; no double-tap wake or black
screen substitute ships. Native hidden display control depends on the ROM;
unknown/unavailable states cannot claim success. A native compositor stall or
simultaneous loss of shell processes precludes a finite restoration guarantee.
Fixed-ratio taskbar targets may be smaller than48dp, by explicit user choice.
Native IME/focus combinations, failure injection, accessibility/performance and
other unrun dimensions remain scoped gaps in the requirement inventory.

Only a PR with successful required host checks may merge to protected main.
Publication uses the existing signed APK/AAB release workflow after reviewed
merge/tag; no protection or production CI/CD adjustment is authorized here.

## Fresh release architecture attestation

Actual verdict: **APPROVE**, no blocking architecture findings. Fresh read-only
expert `/root/release020_arch_final` completed the review at
`2026-11-14T03:57:14.789818+00:00` for0.20.0/code53 against published0.19.0,
baseline `53bf44a5bb9e04433d143feaa593137ed863f1e8`.
The exact inspected pre-attestation Git tree was
`ecc230d9dc74825ef0364a9a75ed3402036424d9`, canonical candidate SHA256
`006c227a7f8f21a48163587dfec0f0bf2b58baff45b002824120f1911f0f7f55`.
This attestation is not part of that inspected digest.

The expert independently inspected the cumulative92-path release diff, current
whole-product/component maps, affected feature overview/sequence diagrams and
actual Kotlin/AIDL/build/test sources. Scope included panel actuator/lease/guard,
restoration and reconnect; reserved viewport, native resize/rollback and shared
input; adjacent split/IME/audio/workspace/persistence owners; privacy and delivery.
Rendered phone-screen/taskbar diagrams matched their retained Mermaid sources and
current ownership. No graph connector was available; native source inspection and
current source-linked diagrams were used.

The review confirmed SessionService ownership, bounded new panel work, immutable
recovery identity, terminal absolute-deadline recovery and conservative unknown
states. One derived viewport preserves full-output transforms while reserving
native app space; generation-fenced resize commits only after success and rolls
back or stops on failure. Only visibility/settings persist; runtime IDs and OFF
intent do not. No new permission, dependency, public privileged endpoint, root
requirement or production CI/CD change was identified. Hidden panel APIs remain
capability-gated. Fixed4% targets retain the user's explicit48dp exception.

Independent integrity checks covered609 exported blobs,252 current host input
fingerprints,318 passing JVM cases,210 passing tooling cases plus one skip,
17 current-version and two historical private archives, and final physical
Android11 APK/run bindings. Requirement/coverage/privacy/documentation/whitespace
checks passed. Device/visual claims retain their own source and reviewer scope;
integrity checks do not establish exhaustive behavior or semantic privacy.

Residual nonblocking gaps include successful physical Android11 panel OFF/ON,
broker/compositor/cable failure combinations, held-input boundary crossing and
failure-injected resize/rollback, occupied split/native IME/audio combinations,
taskbar-to-phone-OFF recovery integration, accessibility, real human peripherals,
broader OEM/app coverage and sustained performance. The inherited generic session
worker's unbounded queue and large controller classes remain disclosed risks;
the new bounded panel path does not resolve them. No consequential architecture
decision remained unresolved. Earlier superseded snapshots supply no approval.

The private read-only report is retained at
`.local/release-0.20.0/architecture-review-final.md`; raw device context stays local.
The expert changed no source/index/tracked document and performed no device,
build, signing or publication action. Only this faithful attestation may follow
the inspected snapshot. Two different fresh final reviewers must verify that
delta and approve the complete final digest including this record. Production
build/signature checks, protected PR host checks and generated release evidence
remain downstream gates; this verdict does not claim their completion.
