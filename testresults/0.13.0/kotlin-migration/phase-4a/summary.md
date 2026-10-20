# Kotlin migration: phase 4a checkpoint

Thirteen shared UI/control leaves are converted: **44/82 maintained Android
sources** now Kotlin. Activity/session/workspace owners and native instrumentation
remain Java for their separate gates. No physical phone was used.

Frozen product: `42799ef2bc6b81350785db88a2f1ee14bb112fb44b26b31ddc50459936b196c5`.
Final test APK: `f85829e8fee729c01e92b2b00f4fec05d9abe11e2256cede19cc98f2805e7090`.
The private `phase-4a-admission-inputs` manifest binds copied source/APK inputs.

| Configuration | Selected passing outcomes |
| --- | --- |
| API30 AOSP/software | ui, monitor, keyboard, catalog, profiles, idle: six source-bound reuses; full split `20261018T111059.452674Z` |
| API36 host | ui `111611`, monitor `111643`, keyboard `111826`, catalog `111920`, profiles `111932`, split `112008`, idle `112338` |
| API37 software, experimental | monitor `112559`, keyboard `112747`, profiles `112850` |

Run abbreviations above share date `20261018` and have full identities in the
private `phase-4a-results/index.json`. These are **17 selected suite outcomes**,
including six reused outcomes, not a complete final matrix. Reuse verifies the
same product, 108 unchanged app-source paths and two runners; the sole difference
among 109 app-source paths is the unused SplitAcceptance in those six branches.
Different test APK bytes are explicitly recorded. API37 is a bounded comparison,
not general compatibility certification; other API37 suites are unrun here.

Host checks pass compilation, formatting, lint and **148 JVM cases**. ABI comparison
passes **23 named owners/112 original non-private descriptors/static shapes**.
Tooling has 169 passes/one platform skip. Compiler failures and the failed lint
attempt remain retained. Fresh lint review permits exactly 13 relocations plus
25 observed inherited diagnostic occurrences; all 22 unrelated raw issue blocks
remain unchanged. No baseline regeneration or source suppression was used.

Two native attempts remain failed:

- `105805`: exact touchpad rectangle comparison failed; its baseline values are
  unknown and later scenarios were unrun. Source review found baseline capture
  preceded controller restoration and ignored rectangle validity.
- `110243`: diagnostic bounds were valid/equal, then phone return after reader
  handoff failed. Cause remains unknown. The same-AOSP composed Java-UI control
  `110456` passes the full tail; no AOSP exception extension is approved.

Fresh architecture review approves a stricter measurement prerequisite: restore
the controller, admit completed main-thread root/decor/pad layout and nonempty
baseline, then require one immediate valid post-dismiss rectangle and exact
equality. No equality polling, state reset or deadline extension. The final current
API30 split passes all 3,047 labels/255.760s including handoff and the full tail.
This verifies that run, without proving either earlier cause fixed. The fifth
harness delta is ported to an ignored Kotlin draft with source-parity approval;
phase5 compiler/ABI/runtime acceptance remains pending.

Eight actual phone/monitor renders were inspected across API30/36, including four
monitor rotations, native phone IME and two-pane output. This is bounded inspection,
not a full golden catalog, pixel-baseline promotion or physical visual approval.
API30 dispatch sample: 100 injected DOWN events after 10 warmups, p50 **57ms**,
p95 **63ms**; excludes rendering/broadcast/await and physical-finger latency.

See the [migration plan](../../../../docs/changes/kotlin-migration.md) for actual
reviewers, scope, remaining phases and final obligations. Raw failures, controls,
source/APK bindings, timings and captures stay in the verified private
[archive descriptor](archive.json), available locally until owner removal; no
public upload or Actions retention is claimed. Final complete emulator/hardware,
upgrade, production exclusion and fresh release/final reviews remain required.
