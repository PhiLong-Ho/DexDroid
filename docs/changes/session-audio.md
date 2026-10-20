# Change: Session media output and Shizuku boundaries

## Contract (before implementation)

FR-036 / DX-AUDIO-001; FR-034 / DX-SETUP-001. See
[feature contract](../specs/session-audio.md). Both surfaces gain Audio as a fourth
same-height action and Settings entry. Choose a current endpoint or System for
the whole desktop session; no automatic cross-session override. This changes the
phone-wide default media route; calls, mic, volume, casts and app-explicit routes
are excluded. Android 11+ compatibility is capability-gated, not guaranteed.

## UI preflight

Actual expert `/root/audio_ui_preflight` inspected current owners and historical
0.13.0 Xiaomi captures (design context only; no new render approval). Initially
proposed Auto/Monitor/Phone & accessories. Architecture cannot promise a generic
nonmonitor exclusion policy; expert re-reviewed the actual-endpoint picker.
User requested best expert design and to follow it, authorizing this routine
choice. Four actions preserve touchpad height; System releases the owned policy,
separate Android anticipated route label; pending/error/unavailable explicit.
No complete approved per-state reference catalog currently exists. Rendered
acceptance remains required. No material unresolved user decision presently.

## Architecture preflight

Actual expert `/root/audio_arch_preflight`, 2026-10-20 16:36:47 UTC, inspected
HEAD2539b7b, source/graph/contracts and official AOSP sources. Approves a
capability-gated primary-owned approach/read-only probe; no universal permission
or playback approval. Strategy APIs exist on Android 11 but shell permission is
not generally present in AOSP 11/12; probe actual helper UID and getters.
Primary helper alone owns baseline/override; separate audio epoch and owner token
must survive per-display create/stop and split. Secondary helper cannot mutate.
Dedicated bounded/coalescing work, main-thread snapshots, identity/epoch guards,
conflict-aware best-effort cleanup. Platform has no death-token/atomic restoration;
helper crash can strand policy. No hidden call-mode/SCO hacks or device spoofing.

## Implementation and acceptance

Implemented shared AudioPanel and state, SessionAudio bounded/coalescing worker,
primary-only AudioRouter IPC and pure AudioPolicyLease. System/Stop restore only
owned original preference; no UI/input-thread privileged waits. Scope/compatibility
and helper-death uncertainty remain as specified. README, guide, setup text,
troubleshooting, design, atlas and source-linked feature diagrams updated.
The Kotlin SC-002 historical acceptance wording now reflects the user's actual
Xiaomi substitution; no old Android11 test/release outcome is rewritten.

Actual architecture follow-up `/root/audio_arch_preflight` approved implementation
at2026-10-20T16:57:10Z after two concrete restoration blockers were fixed:
retain both prior/attempted policies across thrown writes, and retain the actual
old epoch until cleanup acknowledgement. New regression tests cover both.
Inspected hashes: lease74d07788..., client02c7fc4a...; no subsequent behavior edit.

[Actual acceptance and archive index](../../testresults/session-audio-dev/acceptance/README.md)
records final Xiaomi PASS170 in18-second-range,211 current valid JVM outcomes,
210 tooling passes/1skip, exact APK/source bindings, retained failures and gaps.
Use its exact measured duration, not this rounded description. Six changed
Mermaid diagrams rendered and inspected. Targeted native tests only; no broad OS
matrix. Native UI/performance/acoustic evidence remain separate.

UI expert `/root/audio_ui_preflight` approved all nine corrected current renders
from170516 at17:05:57Z, superseding earlier current references. Initial final
reviewers `/root/audio_final_a` and `/root/audio_final_b` rejected77bf5667...:
zero-width Refresh, literal archive paths without `data/`, and FR-036 placement.
The exact authorized visibility correction uses the existing shared button inside
a horizontal row; visible >=48dp bounds/activation are now exercised on phone,
every rotation and Fit. No new architecture boundary or material user decision.
Native170516 passes170 assertions/18.565s on the new product/test pair. Historical
failures/previous render approvals remain separate. Scoped reference metadata now
resolves all nine literal ZIP members/hashes; the full golden/reference gate gap
remains explicit. Two NEW final reviews are required for the corrected candidate.
No reusable workflow change requiring template synchronization is introduced.
This implementation was approved by fresh reviewers
`/root/audio_corrected_final_a` and `/root/audio_corrected_final_b` for candidate
f7d1c539..., then merged through PR #7 after required CI passed. Their actual
records remain in the implementation commit; later content needs fresh reviews.
The subsequently authorized [0.14.0 release](session-audio-release.md) has its own
current hardware evidence and release architecture/publication gates.

## Shizuku research

Official [introduction](https://shizuku.rikka.app/introduction/) describes an
ADB/root-started app_process service. [Setup manual](https://shizuku.rikka.app/guide/setup/)
separates one-time pairing from startup after every reboot and warns against
disabling USB debugging/Developer options. A smaller broker still needs elevated
bootstrap; normal app permission dialogs cannot confer shell/system privileges.
No bank-specific behavior has been tested. No debug flag changed by this task.
