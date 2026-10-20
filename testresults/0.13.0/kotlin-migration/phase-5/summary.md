# Kotlin migration: phase 5 checkpoint

All **82/82 maintained Android sources** now use Kotlin, including all debug,
JVM and instrumentation/standalone fixture owners. There is no maintained Java
under app/src. Three build scripts remain Groovy until phase6. No physical phone
was used in this phase.

Current repaired product is `1c927d1f2c85b038198a62f56cf9ddf8664593e2a3c932511c63011c2d79b527`;
test APK is `e688b7a7df9aa5f3529fab86c8926531345fcc44d13489deec0540fb1b77873a`.
Prior runtime product `1db421f83297c6b83796c4568b7d7025d67f6be0456ef02c7388be79d984a004`
executed all eleven suites on API30 and36, focused split-navigation/controller on
both and Android11-only legacy navigation. These are **27 source-bound reused
outcomes**, not new repaired-product executions. Concrete proof independently
rehashed all139compiled files: only debug ShellUiDump/Companion differ,137match,
no additions; testAPK is identical, and old127source-manifest deltas are exactly
the helper and requirement metadata. Current129input bindings are preserved.

Current repaired pair separately passes seven API37/software experimental suites:
input, ui, monitor, keyboard, profiles, child and idle. Other preview scenarios
remain unrun. Total selected native outcomes are34:27reused and7new. Immediate
private cache recovery retains16UI/28monitor/6profile/18child/13idle fresh files;
three stale child members are excluded. Exact commands/timings, inputs and all
failed histories remain in private phase-5-results/index.json and raw runs.

Host quality/lint and **148 JVM cases** pass. Normal tooling discovery records
**187 passes/one platform symlink-privilege skip**. All12 new shell-check tests and
six standalone-runtime sentinel tests are concretely mapped; coverage, requirements,
documentation, privacy and Ruff checks pass. Native ABI covers29owners/102original
nonprivate members. Debug ABI retains the explicit generated outer-field naming
exception; strict missing-field output was retained rather than weakened. Static
shell main remains callable. Fixture/runtime definitions are independently checked
in packaged test DEX and actual different-UID reader launches pass. A dedicated
uniquely named nontransitive copy fixes the test-APK Kotlin runtime omission while
retaining source-set/UID boundaries and app/runtime version/byte alignment.

Manual lint relocation concerns one inherited debug ClickableViewAccessibility
record; total63 remains,62 other raw blocks are byte-identical. No baseline
regeneration or new suppression. Compiler warnings and inherited debt remain.
Fresh unsigned production APK24210e6/AABb5bdb260 are byte-identical to the prior
probe and omit62development identities in manifest/defined classes. This used
no production key and is not signed-delivery verification.

Twelve actual API30/36 renders across four rotations, native phone IME and split
output were inspected for bounded parity; no fullgolden/catalog/animation or human
peripheral approval. Complete raw split metrics/history were recovered before
focused suites. API30 p50/p95 **59/80ms**, API36 **9/21ms**, each100frameworkDOWN
samples after10warmups measuring eventTime to different-UID dispatch only. Excludes
rendering/broadcast/await/finger latency; configurations differ, so no speed claim.

Retain failed attempts and unrun tails separately: null String.valueOf port sites,
standalone fixture runtime omission, API30 first-Controls MOVE timeout, original
API36 rawCLI137 without platform logs, logged replay and Java-helper-only control
with identical inherited framework main-Looper null dereference, and initial
API30 repaired exit0/SystemUI-only XML. The corrected strict check rejected that
initial snapshot. Fresh expert authorized one unchanged observation after fresh
independent controller focus/shown-surface/resumed-task evidence; it passes and
does not explain or erase the first failure. Initial CRLF readiness-parser miss
occurred before any extra helper invocation. No repeat loop or weakened XML.

Current maintained CLI passes on36(exit0,2windows56nodes) and settled30(exit0,
3windows72nodes), with all diagnostic bytes/hashes. Fresh shell architect approves
bounded repair14:56:27UTC, not universal cold-start discovery or Chrome Stop.
The separate monitor diagnostic prefix emitted zero signals/errors; one ordinary
consolidation was expressly approved instead of duplicating tail8 plus laterall11.
Original134627 remains failed/unexplained; no new timeout waiver was granted.

Excluded: historical debug split-prototype owners were source/ABI reviewed but
not exercised by all/focused suites; nativeChromeStop is reserved for final real
hardware; modern legacy-navigation is N/A; whole UI catalog, physical peripherals,
final upgrade/signing and release remain unverified. Full phase6 ordinary-emulator
and physicalAndroid11 campaigns and final release gates are still required.

Fresh `/root/kotlin_phase5_checkpoint_arch` APPROVES-PHASE5-CONTINUATION at
2026-10-18T15:05:53Z after independent raw34outcome, source/139compiled-file,
ABI, host, diagram and contract reconciliation review.128/129executed source
bindings remain current; only faithful coverage-gap metadata changed. This is
continuation only. Exact decisions are in the [migration record](../../../../docs/changes/kotlin-migration.md).
Private bytes are retained in the verified [archive descriptor](archive.json),
locally available until owner removal; no public upload or Actions retention.
