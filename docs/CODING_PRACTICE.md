# Coding practice

Use this guide for the daily implementation and debugging loop. The
[development workflow](WORKFLOW.md) owns spec/UI decisions and delivery;
[review policy](REVIEWS.md) owns independent approval. Existing required checks,
coverage, visual acceptance and protected-main rules continue to apply.

## Start with a bounded change

Record the outcome, stable requirement IDs, acceptance scenarios, affected files
and test plan in the existing change record. Keep the brief proportional to the
change; do not create another report for each tool call. Consult current CRG and
verify source/tests. Graph misses and uncertain impact require scoped inspection
and broader checks; graph neighbors alone cannot justify excluding a scenario.
Graphify remains release-only.

Read the [code map and feature atlas](architecture/README.md) first. Record
architectural impact; use the [architecture preflight](WORKFLOW.md#architecture-before-implementation)
for possible or uncertain impact before dependent work. Preserve the expert
approach decision through iteration and revisit material departures.

Reproduce a bug before fixing it when feasible. Use a narrow reproduction during
iteration, implement the cause, then add/update behavior-focused regression tests.
Keep unrelated cleanup separate. Preserve ownership, lifecycle and failure-handling
invariants. Complete the existing UI expert/material-decision step before dependent
UI work; actual renders and visual approval remain separate.

## Three verification stages

| Stage | Required practice | Exit condition |
| --- | --- | --- |
| Iteration | Run the reproduction, affected unit/tooling checks and relevant format/lint checks after meaningful changes. | Enough evidence to continue; this is not final acceptance. |
| Completed candidate | Run all required host gates and applicable device/E2E/visual/performance checks. Reconcile coverage and current UI references where configured. | Results bind to the candidate/configuration; gaps and exclusions are explicit. |
| Authorized release | Run the full regression/compatibility scope required by the release plan and existing release gates. | Current report, evidence and artifacts pass release validation. |

Select affected scenarios using requirements, changed ownership and actual failure
risk. Shared geometry, input, lifecycle, build or workflow changes warrant broader
checks. A docs-only change does not require phone installation or product UI captures;
it still runs the host/documentation gates required by repository policy. Never
treat a focused run as a full-suite pass or weaken an assertion to make it faster.
Refresh requirement coverage for every test change as usual.

Reuse valid evidence only when its source/content fingerprints, test/harness inputs,
tool versions and relevant configuration still apply. A commit ID alone is
insufficient for a dirty tree. Disclose reused evidence and its original execution;
it is not a new run. Rerun after relevant changes, failures or unresolved concerns.
Release gates still require their own current outcomes. Restore shared mutable
targets before the next test; device suites on one phone remain sequential.

## Keep independent work parallel

Batch independent reads and read-only checks. Host checks can overlap device work
only against frozen inputs, with separate output locations and no build/install or
source mutation races. Use bounded agents where independent investigation helps;
avoid multiple agents editing the same files or rebuilding the same outputs.
Do not add agents solely to increase concurrency. Keep task briefs small and return
findings/artifact locations instead of flooding the main conversation with logs.

## Freeze once, then review

Before staging the final candidate, finish spec, implementation, tests, generated
coverage, README/feature docs, changelog, affected diagrams/reference approvals and
evidence packaging. Check links and file scope. Defer unrelated cosmetic cleanup
to a separate change instead of editing an approved candidate.

Then follow REVIEWS.md: exactly two fresh independent reviewers inspect the full
same candidate concurrently. Supply factual raw evidence and source bindings,
not the author's conclusions or the other review. Each independently checks scope,
evidence validity and failure cases, and runs useful focused checks; automatic
duplication of every successful suite is unnecessary. Missing/contradictory evidence
or unresolved risk requires additional checking. Never restrict a reviewer from
running a justified full check.

Any candidate content change except its approval record still invalidates both
approvals and requires affected rechecks and a new pair. Evidence reuse does not
preserve stale approvals. Do not push early to obtain CI ahead of this gate.

## Measure and improve the loop

Use existing runner durations/log timestamps where available. In the change record
or ignored raw timing log, record available command/suite/review/evidence durations
and each rerun reason: relevant source/test change, failed check, environment issue
or unresolved concern. Note timer boundaries and unavailable timing; do not infer
model/review/user-wait time from test runtime or promise an unmeasured speedup.
Archive raw bytes under the existing evidence policy. Summarize once per candidate;
unchanged evidence does not need another bundle just because another check ran.

Reusable coding/workflow improvements must be reconciled in agent-dev-template in
the same work or recorded as an explicit pending companion change. Keep platform
commands and adapters in the adopting project. Each repository has its own checks,
candidate digest and publication approvals; one approval does not cover both.

## Planned tooling, not implemented by this guide

- Add scenario-level selection to large device suites; run broader suites at the
  completed-candidate boundary. Preserve setup/cleanup and selection coverage.
- Remove duplicate branch-push/PR quality runs while retaining required PR, main
  and manual checks; validate required-check behavior before changing triggers.
- Cache dependencies using actual dependency inputs and benchmark incremental
  daemon/build-cache use. Cache hits never substitute for required test outcomes.
- Add per-command/scenario timing and rerun-reason records to runners and summaries.

[Local monitoring](LOCAL_MONITORING.md) now supplies live streams, command durations,
bounded deadlines and independent observation. Remaining items need separate
implementation: scenario selectors, richer rerun analysis, CI deduplication and
caches. Use the printed attach command for quiet work; do not infer test progress
from a live heartbeat.

## DexDroid application

Use CONTRIBUTING.md and TESTING.md for current commands. During iteration select
the existing affected suite, such as UI or split; finer scenario filtering is
pending. The completed candidate still runs qualityCheck, tooling/requirement/
coverage/document checks and applicable real-device suites. Shared geometry/input
changes cover phone/monitor, four rotations, Fit, keyboard and lifecycle as applicable.
Record compatibility exclusions and restore the usable phone controller afterward.

## Release freeze

For every new version candidate, completed checks/evidence are followed by the
[mandatory release architecture review](RELEASING.md#release-architecture-review).
Freeze its inspected snapshot, record the actual verdict without other input
changes, then freeze the full candidate for the two fresh final reviewers. Task
N/A never waives a release review. Re-review changed inputs; reuse only a recorded
same-version byte-identical transport retry under existing release rules.
