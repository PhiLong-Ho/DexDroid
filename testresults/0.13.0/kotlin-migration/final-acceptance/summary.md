# Migration final acceptance checkpoint

Scope:0.13.0/code39, branch `chore/kotlin-migration`, based on published
0.12.5 (`dbadbf9dce7b23bfb111a153a03cfd71037325a9`). Source conversion is
complete:82 baseline Java files,89 maintained Kotlin sources and3 Kotlin DSL
scripts. Generated AIDL Java is excluded.

## User-directed verification scope

The original Android11 device became unavailable. The user substituted
Xiaomi14 Android15, stopped further Android12/13/14 campaigns on2026-10-20,
and selected the real Xiaomi for the final focused check. Prior failures stay
retained for community verification. No universal Android11-17 claim is made.

## Source and artifact attribution

The complete API35 emulator campaign uses original runtime44ab and test1b735
SHA prefixes, with full hashes/source/image/boot bindings in its provenance.
The original Xiaomi all-eleven campaign and later navigation keep their own
release-certificate debug/test bindings. Final readiness corrections leave runtime44ab unchanged. The test-only
corrections first produce test0af6/signedf859, then a success-description-only
correction produces test4c4e/signed059736 for the final verified runs.
Full SHA-256 values are retained in the frozen manifests and signing records.
The138-entry readiness snapshot changes5test/tool/requirement entries and
preserves133others. Its source-level early-route comparison establishes which
old suite dependencies are unchanged, not byte-identical test APK execution.

## Actual results

| Check | Actual outcome and attribution |
| --- | --- |
| Current host | `release-readiness-host-v5`:9gates pass,200 JVM outcomes incrementally valid;210tooling passes/one Windows symlink skip |
| Complete API35 emulator | `20261020T151205.778163Z`:11suites pass; focused legacy `20261020T152321.468543Z`, split navigation `20261020T152338.252998Z` and disconnected controller `20261020T152543.591117Z` pass; collection and owned teardown pass |
| Complete Xiaomi | `20261019T143258.560520Z`:11suites pass with fresh collection |
| Xiaomi normal split repeatability | `20261020T143750.205335Z`, `20261020T143933.676982Z`, `20261020T144116.446881Z`:1149assertions each, collection/cleanup pass |
| Xiaomi warm predecessor | `20261020T152622.628379Z` legacy126assertions then `20261020T152641.796025Z` split1149assertions pass without intermediate Stop; collection/cleanup pass |
| Final native Chrome attempt | `xiaomi-release-readiness-v1`:test-only update succeeds; Chrome selector times out before launch, zero cycles, no Chrome/Stop action sent. Cleanup conservatively refuses Stop. This is incomplete, not a product Stop failure |
| Final focused Xiaomi input | `20261020T160518.012469Z`:273assertions pass in31.993s on unsigned test4c4e/signed059736; runtime unchanged |
| Final focused Xiaomi keyboard | `20261020T160845.434782Z`:2405assertions pass in55.654s; collection and final guarded cleanup pass |
| Interrupted outer owner | Raw running metadata preserved; independent observation proves no active instrumentation/no keyboard invocation. Only the unstarted keyboard resumes |

The earlier Xiaomi rejected-DOWN shutdown is unresolved despite later
nonreproduction. Diagnostic capture was absent/inconclusive; do not infer the
consumed reason. Android12 actual shell-caller capability is denied;
Android13 latest input readiness and Android14 latest keyboard readiness fail.
Their tails and all Android12L checks are unrun. Earlier Android11/16/17,
OEM/peripheral, immediate-transition Back, full visual catalog, rendering-latency
and recovery-data-equality gaps remain explicit.

## Reviews, evidence and privacy

`/root/emulator_final_matrix_arch` approves the physical adapter at
2026-10-20T15:55:47Z (SHAa0ad722f), following6host protocol cases and9host gates.
Its native result is retained separately. A subsequent approved approach permits
independent input/keyboard after the exact known picker is closed and its first
guarded Stop completes. The remaining adapter is approved at16:00:49Z; its initial input result remains
failed due to reserved success-description wording. The wording-only generation
is approved at16:04:09Z and the interrupted keyboard-only continuation at16:08:04Z.
Those local procedure approvals do not approve release publication.

Private operation contracts bind exact helper/input hashes. Original failed
attempts, native results, collection and cleanup are separate. All device images,
dumps, logs and APKs remain private local; only this summary and verified archive
descriptor are tracked. No device bundle goes to public GitHub/Actions/releases.
The public release pipeline generates its own current host-only report/evidence.
The [verified archive descriptor](archive.json) records the private ZIP hash,
member manifest and availability. Local retention
lasts until the owner removes the archive. Release architecture and two fresh
final reviews remain separate publication gates.

Current final host/source evidence is separately archived in the
[final host checkpoint](../final-host/summary.md).
