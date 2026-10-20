# Kotlin migration: phase 4c checkpoint

SessionService and SplitWorkspace are converted. **49/82 maintained Android
sources, including all 38 production sources**, now use Kotlin. Native tests
remain Java until this checkpoint is accepted. No physical phone was used.

Frozen product: `f29c8c203cfcc57aad7b0d8ef53956b93575e5bbd319da3cc635bcd2d51b2ea8`.
Test APK: `d5473b5160260331fd9370bad19fe70312ce27d9de84fb9a4defc4efc70bd419`.
All 121 bound source/tooling paths and both freshly built APKs match their
admission manifest after testing.

| Configuration | Selected current passing outcomes |
| --- | --- |
| API30 AOSP/software | All eleven monitor suites; focused legacy navigation, focused split navigation, disconnected controller |
| API36 host | Complete current eleven-suite comparison; focused split navigation, disconnected controller |
| API37 software, experimental | input, ui, monitor, keyboard, profiles, child, idle |

There are **34 selected current passing outcomes**, without source-bound reuse.
Exact runs, commands, timings and scope are in private `phase-4c-results/index.json`.
API37's other scenarios remain unrun. Legacy navigation is Android11-only under
its existing contract; API36 legacy coverage is N/A, never passed.

Two failed native attempts remain failed:

- API36 `122817` passed ten suites then failed split current-window admission:
  a visible fixture's current input row remained NOT_TOUCHABLE. Its later split
  tail was unrun. No cause, product fix or new exception is established.
- Author mistakenly scheduled the Android11-only legacy suite on API36 in
  `125348`. Its legacy parser cannot accept modern inputConfig rows. It failed
  before native contact; later navigation assertions were not executed. The
  retained diagnostic snapshot is post-cleanup, not failure-time touchability.

A separately bound original phase4b Java-orchestration control `123855` passes
full split, but lacks the original ten predecessor suites. Fresh architecture
review authorized ONE unchanged current comparison in the complete original
eleven-suite order. `124328` passes all eleven, including split's full tail.
Intervening control/install/restoration are retained. These outcomes do not erase
or explain the initial failure, and no repeat-until-green or failure waiver is used.

Host quality/APK builds and **148 JVM cases** pass; lint has no active Error or
Warning. Compiler warnings remain. ABI comparison passes **10 owners/219 original
non-private descriptors/static shapes**; separate bytecode inspection retains
outer constructors, reflected fields, mutable fault-injection callback, local
returns, deferred Runnables and the 64-bit key identity. Tooling records
**169 passes/one platform skip**. Requirements, coverage, documentation, privacy
and Ruff checks pass. Compiler/lint failures and their repairs remain archived.

The manual lint delta relocates two inherited records without additions; all 61
other raw blocks are byte-identical and the total remains 63. Existing cursor
allocation and wake-lock debt remain unresolved; no suppression or regeneration.

Nine actual API30/36 renders were inspected across four monitor rotations,
including native phone IME and two-pane output. This is bounded parity review,
without whole-catalog, golden-baseline, animation or hardware approval. API30 full
split's printed 100 DOWN samples/10 warmups measure **49ms p50/62ms p95**, excluding
rendering/broadcast/await/finger latency. Its raw performance JSON and history
snapshot were deleted by the next focused suite before recovery; that collection
gap prevents raw-sample reproducibility. Complete instrumentation output and
fresh captures remain; future full runs are collected before focused invocations.

See the [migration record](../../../../docs/changes/kotlin-migration.md) for actual
review identities, decisions and remaining obligations. Fresh `/root/kotlin_split_native_window_arch` approves checkpoint continuation
at 2026-10-18 13:09:59 UTC. Raw evidence is retained in the verified private
[archive descriptor](archive.json), locally available until owner removal; no
public upload or Actions retention is claimed. Final full ordinary-emulator and
Android11 physical acceptance, upgrade/signing retention, production APK/AAB
exclusion and fresh release/final reviews remain required.
