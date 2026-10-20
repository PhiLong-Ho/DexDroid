# Release architecture review: 0.13.0

**Verdict: APPROVE - release architecture only.**

- Target: 0.13.0 / code 39.
- Previous published baseline: v0.12.5,
  `dbadbf9dce7b23bfb111a153a03cfd71037325a9`.
- Frozen inspected pre-attestation Git tree:
  `b6dbbd9acf0514354a235c7450911e964a89770b`.
- Canonical pre-attestation digest:
  `e3913ce6e42d85c51a00bb4e9189ff56a9a43d27d42d4ee213d2c6601cfa3422`.
- Fresh read-only reviewer: `/root/release_arch_kotlin_013`.
- Actual review UTC: 2026-10-20T16:17:53Z.
- Sole canonical digest exclusion: `reviews/approvals.json`; its prior private
  operation record is not final release approval.

## Inspected scope and findings

The expert independently inspected the cumulative published-baseline migration,
current graph, requirements/change records, runtime/test sources, architecture
atlas, build/tooling and private evidence. Worktree, staged tree and digest stayed
unchanged through review. All 82 baseline Java sources plus seven new Kotlin
owners/tests are present:89 maintained Kotlin files and three Kotlin DSL scripts.
Generated AIDL remains outside the maintained conversion inventory.

SessionService retains shared phone/monitor authority; shell-UID DeskService
instances retain distinct virtual displays. AIDL and production manifest
boundaries remain unchanged. Monitor attachment, task/protection callbacks and
launch delivery validate current ownership. Cancellation precedes geometry/
navigation teardown; split keeps its 64-event queue and accepted-event drain.
The generic session queue remains unbounded with selective shedding: an inherited,
explicitly documented risk. Stop invalidates generations before queued cleanup.
No new UI-thread completion wait is introduced.

ForegroundLaunch admits only an eligible existing hidden managed task, waits
finitely on its owned callback thread, closes that thread and revalidates task/
display/owner placement. Failure after the start RPC does not fall back or retry. LaunchScope
guards the actual main-thread result mutation.

Package/component identities, preference namespaces and durable/runtime state
separation remain intact. Phase ABI evidence accounts for 219 original orchestration
and 102 complete native-test descriptors; the debug synthetic outer-field rename
has a narrow retained adjudication. Class monitor locks, JVM bridges/fields and
null-conversion corrections have source/bytecode evidence. Kotlin/runtime versions
are pinned, standalone fixture runtime is aligned and test-only, and production
exclusion checks definitions, nested classes and facades. Lint expansion is
individually explained as inherited diagnostics and duplicate occurrence handling.

The system/component map and session, launcher, input, split, protection and
delivery sequences agree with source. The expert inspects all four late PNGs,
verifies hashes and compares their Mermaid sources with tracked architecture after
newline normalization. They are readable. Earlier renders retain their own
attribution; this is not whole-catalog visual approval.

## Evidence independently verified

Archive checksums and every manifested member verify for final host 192 files,
final acceptance 10,741, phase 4c 1,366, phase 5 2,720 and Xiaomi navigation 3,237.
All 138 final host source bindings match. Raw records confirm nine host gates,
200 valid incremental JVM outcomes,210 tooling passes/one skip, original complete
API35/Xiaomi eleven-suite campaigns, later navigation and final Xiaomi input 273/
keyboard 2,405 assertions with their recorded signed APK identities. Original-artifact
reuse and incremental validity remain explicit. Private device evidence stays
local; public release packaging retains its current-host privacy/report/archive
gates. See [findings](kotlin-migration-findings.md) and the
[evidence index](../../testresults/0.13.0/kotlin-migration/README.md).

## Residual risks and release decision

No blocking architectural finding remains for the bounded release. The expert
accepts these nonblocking gaps under NFR-014 and the recorded user decision:

- Earlier rejected-DOWN/teardown is unexplained; later passes prove only
  nonreproduction. Immediate-transition Back remains unresolved.
- Final Chrome selection is incomplete, with zero Chrome/Stop actions in that
  attempt. Settled-focus checks do not certify early-transition behavior.
- User-selected Xiaomi Android 15 substitutes unavailable Android 11 hardware. Android 12
  capability denial, Android 13/14 readiness failures, Android 12L/unrun tails and
  earlier Android 16/17 limitations remain open. No further Android 12-14 campaign is required.
- Actual callback/input/Stop contention, complete visual acceptance, physical
  peripherals, rendering latency and recovery-data equality remain unverified.

Only this faithful attestation may follow the inspected inputs. Any other changed
candidate input requires renewed fresh architecture review. Two different fresh
final reviewers must approve the complete final digest including this record.
This architecture verdict alone permits no signing, tagging, dispatch or
publication; the protected PR/host and final review gates still apply.
