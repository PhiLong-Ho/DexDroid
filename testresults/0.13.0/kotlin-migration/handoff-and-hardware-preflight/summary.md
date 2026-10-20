# Handoff repair and hardware preflight checkpoint

Baseline: `v0.12.5`, commit `dbadbf9dce7b23bfb111a153a03cfd71037325a9`.
Branch: `chore/kotlin-migration`; working tree remains uncommitted. This is not
final migration acceptance or publication approval.

The [change record](../../../../docs/changes/handoff-rendering.md) describes the
contracts, source-linked preflights, individual failures and verification limits.
The [hardware contract](../../../../docs/changes/kotlin-migration-hardware-operations.json)
binds the separately reviewed private test procedure. Its exact operations pair
and live device admission are still pending.

## Source and outcomes

All82 baseline Android Java sources are converted; the current inventory has88
maintained Kotlin sources and three Kotlin DSL build scripts. Generated AIDL Java
and other source formats retain their roles.

| Identified scope | Actual outcome |
| --- | --- |
| Original child/phone handoff, four rotations/Fit and fresh remaining-app contact | API34/35 pass on earlier frozen generations; source-bound reuse is documented, not fresh v8 execution |
| Normal input | API34/35 pass on v4 |
| Dedicated protection | API34 v5 and API35 v7 pass |
| API34 v7 full split, `20261019T105631.956408Z` | PASS239.993s; earlier private180s wrapper timeout remains a timeout |
| API35 v7 split, `20261019T110059.517273Z` | FAIL after rejected managed DOWN and product teardown; missing DisplayInfo before-input evidence is not its consumed reactive sample |
| API35 v8 full split, `20261019T112107.884969Z` | Native PASS245.752s, including both controlled pending-window cases; ordinary controller return succeeds |
| V8 rejected-DOWN capture | UNVERIFIED; correct primary owner exists but no admitted InputTest sample; wrapper fails collection and disables its exact request |
| Current host validation | Eight gates pass;169 JVM cases, zero failures/errors; tooling209 reported =208 passes/one Windows skip |
| Private hardware procedure |155 synthetic actual-function cases pass; mocked transports do not establish signing or device acceptance |
| Current Android11 hardware campaign | NOT RUN; phone remains production38, pending exact approvals and live unlock/admission |

Frozen v8 binds137 curated source files. App APK:
`8313438cb4a80afdddc85a4204dba8fe79cd5ab5577a364d7c2e770f08d3ba7d`;
test APK: `3913c71d02b2cae3ccfc09ec338cf3d90b47c4ecf2e0ffb836d82b5513485170`.
These are emulator/debug inputs, not production artifacts. The latest current
host run is `handoff-render-hardware-host-validation`; unchanged Gradle outputs
may be reused. No blanket fresh-execution claim follows from its case count.

API34/35 split synthetic input samples have100 contacts after10 warmups, with
p50/p95 of14/35ms and20/40ms respectively, from injected host DOWN to the
separate-UID app callback. Rendering and physical peripherals are excluded.
Author inspection covers selected split/protected/idle/Help captures and affected
diagram renders; no user/golden approval or whole visual catalog is claimed.

## Remaining gaps and evidence

The intermittent API35 missing-DisplayInfo/UNKNOWN rejection remains unresolved.
No classifier/cancellation relaxation is made. Actual launch-completion/input/Stop
contention, other recorded API31/33/36/37 failures, final Android11 acceptance,
production upgrade and fresh release architecture/final reviews remain pending.

[Archive descriptor](archive.json):4,881 files,187,144,743 compressed bytes,
SHA-256 `14f40a4613811bc5cf0541d75b70a1a6d63d37e06ac77cdaf6d59e9c2ace4987`.
The ZIP and member hashes are verified locally. Storage is private local only,
available to local reviewers until the owner removes it; no upload or automatic
expiry is configured. Git retains this summary and descriptor, not raw captures,
APKs or logs. Withdrawn historical evidence is not restored.

The packet retains failed setup/calibration attempts and the list-shaped-result
packaging failure. Finalization resumes the copied packet without test reruns.
Emulator startup logs are point-in-time copies. JWT-like bytes and unrelated
credential files are excluded by admission scanning. The original freeze helper
was formatted after execution without preserving its prior bytes; its execution
hash is historical and unavailable, while frozen source/APK bytes remain verified.
The duplicate-publication probe's exit is checked but not stored numerically;
command timeouts may lack complete journal triplets. Missing evidence stays
unverified and never converts a native failure into a pass.

Correction to the archived narrative: "No credentials/signing" refers to private
release-key operations for phone delivery. Normal Gradle debug signing occurs,
and build configuration may load local release properties. No private operations
release-key signing or physical-device update has been invoked in this checkpoint.
