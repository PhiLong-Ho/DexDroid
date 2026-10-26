# Change: One saved workspace shortcut

## Contract and decisions

0.16.0/code43, `feature/saved-launch-shortcut`, based on published 0.15.0
(`e5670c5be9ea6bb16fb08fe7c2cdb2533edb94c8`). The user approved Settings as one-time
setup, clarified saving current apps plus arrangement, then instructed implementation
and Xiaomi14 acceptance. FR-038 / DX-SHORTCUT-001 owns the contract.

One explicit Save/Update stores public launcher entries, occupied/empty panes,
Single/Split direction, ratio, mapping, selection, maximization and whole-output
rotation. A restore tap authorizes replacement. Monitor profiles retain resolution/
DPI ownership; audio and scroll preferences remain independent. App-owned pages,
documents, history and unsaved contents are outside scope. Multiple workspaces,
Tasker/MacroDroid APIs, arbitrary commands and plugins remain deferred.

## Architecture and UI preflight

Fresh approach experts `/root/workspace_shortcut_arch` (2026-10-24 15:30 UTC) and
`/root/workspace_shortcut_ui` approved stable capture, off-main canonicalization,
one transient ordered restore and shared Settings setup. Existing Up/Close,
scrolling, outside dismissal and main controls remain. The UI expert also approved
semantic restore feedback in the existing collapsed cue, with a ten-second terminal
notice and full result retained independently of routine status reports.

`WorkspaceSnapshot` owns versioned immutable values; session-owned `WorkspaceShortcut`
owns persistence, bounded eligibility work, cancellation and pinning; `WorkspaceRestore`
owns main-thread step identity/completions. The private Activity accepts fixed actions
without external target/command extras. SessionService and SplitWorkspace retain
display, launch, geometry and input ownership. The capture correction below adds
one read-only owned-display task query to AIDL; input/geometry algorithms remain
unchanged. The source-linked [overview/sequence](../architecture/features/workspace-shortcut.md),
[system map](../architecture/README.md) and [feature spec](../specs/saved-launch-shortcut.md)
describe failure/cancellation boundaries; onboarding includes the new owner.

Source follow-up `/root/workspace_guard_recheck` approved revalidation after waiting,
epoch guards before native dispatch/results, retained-resource release and cleanup
barrier wakeup. `/root/workspace_cleanup_confirmation` approved using final verified
Stop as cleanup authority: log provisional close warnings, preserve sticky secondary/
final failures, and require retired-display/task/WindowManager confirmation.
These preflights do not replace release architecture or final review.

## Results and evidence

Release architecture review `/root/workspace_release_architecture` at2026-10-26
16:39:02 UTC withheld approval for tree`bafce0fa900312bc2d9b679b98a60da60ecccc42`,
digest`2462acec9744f5beb23804a558822eb08636d251e53ad3cf045a38f7d9eba922`.
Its P1 finding withdraws the earlier four-rotation workspace claim: app launch reset
the loop to0°, and comparison against the captured snapshot did not catch that.
The corrected fixture rotates after launch and asserts independently requested
rotation before save/after restore, plus native app window dimensions. Its P2
finding requires actionable missing/disabled-app feedback instead of raw
NameNotFoundException component identifiers. These local contract corrections
are covered by renewed native/render evidence below; a fresh release architecture
review remains required.
The prior raw runs/renders remain retained; filenames are not proof of rotation.
The new independent configuration checks also exposed fixture assumptions about
remembered split swap/fullscreen values. The fixture now sets each desired value
explicitly rather than blindly toggling inherited state; the strict expectations
remain unchanged and product split-preference persistence is preserved.

See [scoped summary and verified private archives](../../testresults/0.16.0/workspace/README.md)
and its structured JSON for exact run/APK/source bindings, timing and remaining gaps.

- Final workspace run `20261026T171832.601816Z` passes522 assertions on Xiaomi14
  Android16 with real1920×1200 monitor: four rotations, Single/Split, ratios/swap/
  selection/maximization, empty panes, physical touch/Back, private-child launcher
  canonicalization, unavailable apps, notice retention/expiry, Fit preview,
  provisional/final cleanup, cancellation/retry handoff and queued-launch guards.
- Final monitor run `20261026T172011.361030Z` passes2434 affected launch/input assertions.
- Source-bound reused outcomes: phone UI475, split-navigation1149,
  audio186. Later changes are setup text/launcher labels, workspace eligibility
  error normalization and strengthened workspace-only rotation/configuration/bounds
  tests. Final workspace acceptance covers those feature changes. Shared
  pane input/geometry/audio algorithms are unchanged; ordinary Single launch state
  and save confirmation changed as described below. Pin smoke is reused from before
  eligibility and capture changes; pin rendering and launcher entry remain unchanged.
  An isolated AudioWorker test assertion also changed after reused UI/split;
  those suite flags never execute it. Unchanged evidence is not called a rerun.
- Actual Home page-2 icon cold-process smoke restores stock Settings to an owned
  virtual display, with controller on phone0 and retained Workspace restored result.
  The app-level shortcut permission was Deny and was allowed manually while
  troubleshooting. Page location and permission were not independently isolated
  as the initial cause. No confirmation/cancel dialog appeared on this launcher path.
- `/root/workspace_render_review` approved visible scope of12 renewed menu captures
  at2026-10-26 17:21:04 UTC, reading current/saved0/90/180/270 and requested pane
  arrangements. Pin-request phone image approval16:22:02 UTC is separate reused
  scope. Short/long labels both
  read Restore workspace; optional confirmation and manual permission recovery are
  documented in shared feedback, User guide and README. No new layout decision.
- Host:234 JVM passes;210 tooling passes and one Windows symlink-privilege skip.
  Final quality/debug/test build `mvpis2sa.log` passes in10s;
  tooling `mvpip5e4.log` passes in17.923s.
  Formatting/lint baseline is not regenerated.
  Coverage, requirements, docs, privacy-path guard and Ruff checks pass. Four
  current workspace/system plus two launcher Mermaid blocks are rendered/source-bound
  in `mvpioa8p.log` and inspected (six total).

Private archives retain raw command output, reports, device attempts/captures,
source hashes and exact development binary bindings. No device dumps/captures or
development ZIPs are uploaded. CI produces its own current public host evidence
and release reports after its privacy guard. Local bundle packaging identifies the
dirty baseline; it does not pretend that baseline commit is the tested final tree.

## Findings and rerun reasons

The original eight-minute attempt stalled in unbounded test orchestration. The
focused harness now emits progress, bounds main-thread waits and observes the
single-task controller lifecycle rather than waiting indefinitely in startActivitySync.
All original failure bytes are retained.

Split restore exposed asynchronous app-close reporting on Xiaomi. Primary replacement
had incorrectly made an intermediate warning permanently fatal even after verified
Stop. It now matches secondary cleanup and keeps final failure blocking. A real
Stop following an injected intermediate warning and failed-Stop retry rejection
are both observed by native acceptance.

The empty-pane refill fixture needed native task-removal confirmation rather than
UI occupancy alone; rapid close/reopen still safely rejects changed placement and
is not claimed repaired. Disabling the test Activity required its own UID via
debug-only run-as because this phone denies shell component-state edits. The test
verifies disabled state and re-enables it in finally. A passing assertion containing
the runner-reserved word failed caused one outer classification rejection; its label
was renamed, without changing assertions or failure detection, and the suite reran.

Audio regression found an existing controlled-test race: Stop and replacement may
be observed separately. The assertion now captures one/two initial old-epoch attempts
and requires exactly one additional confirmed retry, with no never-started-epoch
cleanup. Product audio code is unchanged; targeted audio acceptance passes.

The first phone capture copied the underlying controller rather than the intended
menu; final captures use actual window PixelCopy and bounded layout readiness.
Earlier images are retained without claiming workspace visual approval. Reused
tests, each failed attempt and their rerun reasons are explicit in the archive.

## Limits and delivery gate

This is Xiaomi14-only scoped acceptance. Other Android/OEM/launchers, monitor pin
handoff, confirmation cancellation, offscreen Fit-bottom pixels, AT/large fonts,
full keyboard/peripheral and non-default-audio restore combinations, acoustic
verification and ordinary input/frame latency remain gaps. Nine warm-service
activation observations are descriptive, not universal performance guarantees.
App-internal state is excluded by contract.

Fresh release architecture attestation and exactly two fresh final approvals are
still required before commit/push/PR/release. Publication and production installation
are separate subsequent outcomes. Unrelated existing edits to the architecture
skill, CODING_PRACTICE and `docs/changes/default-tooling.md` are preserved outside
this candidate.

## Release architecture attestation

Fresh read-only expert `/root/workspace_release_architecture_v2` explicitly
**APPROVED** at **2026-10-26T17:03:16Z**, with no blocking findings or unresolved
architectural questions, for 0.16.0/code43 against published baseline
`e5670c5be9ea6bb16fb08fe7c2cdb2533edb94c8`.
Its exact inspected pre-attestation tree was
`bfb3c64fff585aadd7522f45b86c6884ce10c646`, canonical digest
`09b86409b7db501e24f5b202a30367340b0fb467dc4161f1aecad7c033f643b9`.
This paragraph records that review; it does not claim that snapshot included itself.

The expert inspected all44 cumulative candidate files, FR-038, feature/system/
component/sequence documents and rendered diagrams against actual source: immutable
snapshot serialization, private entry/eligibility/pinning, shared panels/status,
restore sequencing, service startup/cleanup, split lifecycle/layout/launch, existing
shell removal/placement confirmation and audio ownership. No material diagram drift.
Service authority, off-main eligibility, capacity-one restoration, epoch/step/
generation guards, ordered cleanup/confirmed Stop, retained-resource cancellation
and failed-cleanup retry barriers were accepted. Resolution/DPI, audio and scroll
ownership remain unchanged; partial restore correctly avoids promising rollback
of already closed app contents. Both previous blockers were independently verified
resolved, including renewed native checks and inspected90°/270° captures.

The reviewer verified both current private archives and all67 host/489 device
members,145 tested-source fingerprints, UI source bindings, final APK hashes and
recorded234 JVM passes/210 tooling passes plus one skip/workspace461 assertions.
Reused outcomes and withdrawn attempts remain explicit. Broader OS/OEM/launchers,
monitor pin handoff, confirmation cancellation, Fit-bottom pixels, accessibility/
large fonts, peripheral/non-default-audio combinations, acoustic and general input/
frame-latency coverage remain unverified. The inherited generic input queue remains
a baseline limitation. Graph tooling was unavailable; the review used direct source.

Only this faithful attestation follows the inspected snapshot. The two fresh final
reviewers must verify that delta and approve its inclusion in the final digest;
protected PR/host and CI release-package/report/privacy gates remain required.

## Final-review capture correction

Final reviewer `/root/workspace_final_b` rejected digest
`0d047f99b96ad962054b50c3b2d623b585d5af67d8db5d58cf93bbbc6d703405`
at2026-10-26T17:06:37Z: ordinary Single launch published a speculative component
before native dispatch, allowing Save while the worker was blocked. The other
reviewer approved that snapshot; the pair therefore did not authorize publication.
The preceding architecture attestation is historical for its exact snapshot and
does not approve this correction. New release architecture and two fresh final
reviews are required after corrected checks/evidence.

Fresh architecture preflight `/root/workspace_capture_race_arch` approved the
approach: per-launch main-owned tokens, no speculative occupancy, serial terminal
native-state reconciliation and independent fresh owned-display queries during
Save. The narrow read-only RPC reuses DesktopTasks' existing root parsing for both
helpers; no movement, fallback, permission or persistence migration is introduced.
An unavailable query preserves the previous record and allows retry. Save retains
its mutation epoch and ownership/layout comparison, so stale callbacks cannot
commit even when later edits return to equal values. Existing controls/styles are
unchanged; this corrects FR-038's already-authorized stable-save contract and needs
no new UI decision. Tests cover delayed overlapping ordinary launches, first/second
failure completion, unknown query preservation and explicit recovery.

The preflight follow-up caught pending-Save cancellation that excluded `working`
state and empty reconciliation that could bypass observer input cleanup. Both are
corrected: busy-based invalidation plus a final generation/display/helper/Split
guard, and complete occupied→empty cleanup. The expert explicitly approved the
resulting source approach; runtime and release/final review are separate. Native
tests also hold eligibility after confirmation, then launch the same app or Stop/
recreate/relaunch, requiring cancellation and preservation rather than an equal-value
overwrite. Two initial491-assertion native streams completed but the outer runner
rejected reserved `failed`/`failure` words in passing labels; labels were corrected
without changing assertions or runner detection. Raw outcomes remain retained.

Final correction verification: workspace522/88.555s and monitor2434 pass on final
development binaries (monitor104.311s). All12 renewed originals were
approved2026-10-26T17:21:04Z. Delayed Save tests confirm cancellation after equal-value
launch and Stop/recreate. Source approach review is approved; fresh release architecture
and final pair remain required for the corrected candidate.

## Corrected release architecture attestation

Fresh read-only expert `/root/workspace_release_architecture_v3` explicitly
**APPROVED** at **2026-10-26T17:25:29Z**, with no blocking findings or unresolved
architectural questions. Target0.16.0/code43, published baseline
`e5670c5be9ea6bb16fb08fe7c2cdb2533edb94c8`; exact inspected pre-attestation tree
`3a7e1170def9458695676b82be3ebbeb279f98ca`, canonical digest
`43b3d8d5105eb3a7e8e168c92f6f52814588a92f771e0c588f5df77e829782d7`.
This attestation follows that inspected snapshot and is not part of its hash.

The expert reviewed all47 cumulative files, FR-038/DX-SHORTCUT-001, actual source/
tests, whole-product/component/feature architecture, six source-bound rendered
diagrams and all12 final capture originals. No material diagram drift. It accepted
service/shell/display/input ownership, bounded value persistence, private fixed
entry, off-main read-only owned-display query and versionCode helper binding.
It independently verified the rotation, actionable recovery, per-launch stability,
fresh Save capture, epoch/ownership cancellation and empty-cleanup corrections.
Restore sequencing, confirmed final Stop, retained-resource cancellation and failed
cleanup barriers preserve existing invariants; partial outcomes do not promise
rollback of closed app contents.

Both private archives and all88 host/611 device members,145 source fingerprints,
final development binary/payload bindings and13 UI states match. Recorded234 JVM
passes,210 tooling passes/one skip, successful builds, workspace522 and monitor2434
outcomes were inspected. UI/split/audio/Home smoke reuse remains explicit. Other
OS/OEM/launchers, monitor pin handoff, absent confirmation cancellation, Fit-bottom/
collapsed-cue pixels, accessibility/large fonts, peripheral/keyboard/non-default-
audio combinations, acoustics and general input/frame latency remain gaps. Nine
warm-service samples are descriptive only; the inherited generic unbounded session
queue remains a baseline limitation. Graph tooling was unavailable; direct source
inspection supplied the review.

Only this faithful attestation follows the inspected tree. Two different fresh
final agents must verify that delta and approve the complete final digest before
protected PR/host and CI signing/privacy/package/report/archive/publication gates.
