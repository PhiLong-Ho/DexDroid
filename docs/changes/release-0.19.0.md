# Release 0.19.0: Readable controls, search and pointer idleness

Requested publication includes the completed menu readability, Settings search /
Turn off Developer Mode label, automatic pointer hiding and illustrated phone
touchpad guide. Version 0.19.0/code 52 follows v0.18.0 at
`c05649ce804f077700db50676827b48a812ac321`. Existing unrelated local workflow and
promotion drafts are excluded and preserved. Agent OS is off; no dashboard
review/plan/doc calls. Use the protected-main PR and existing release workflow.

## Contract and scope

FR-001, FR-035, FR-037, FR-042 and FR-043; stable requirement mapping and diagrams
remain authoritative. Versioning/notes add no product interaction or ownership;
task architecture impact is N/A for this release preparation only. A separate
fresh cumulative release architecture expert remains mandatory after checks and
evidence, before the two independent final candidate reviewers.

Focused native scope is Xiaomi14 Android16 input (including new cursor tests),
phone UI/help/search and monitor four-rotation/Fit/search/readability. Reuse prior
unchanged secondary-panel evidence explicitly; do not claim a full OS regression.
Host quality/tooling, requirement/coverage, fixture runtime, documentation and
privacy guards must pass. Private screenshots/logs remain local, while release
Actions archive only their current public host evidence and reports.
The input runner now retrieves the existing private `submenu-acceptance` folder
where the new pointer acceptance writes its frames; this adds no upload path or
device behavior. Without the mapping, a future input run would leave those frames
on the phone. The one-entry collection correction is local tooling; no new suite
or implementation-mirroring mapping test is added.

## Current verification status

Current development 0.19.0/code52 passed `spotlessApply qualityCheck` in14 seconds:
295 JVM cases, no failures/errors/skips and no lint-baseline changes. Final pinned
tooling passes210/211 with one Windows skip in18.257 seconds (Pillow installed).
Combined local host outcomes:505 passed/506 reported. Ruff check/format, packaged
fixture definitions, coverage/requirements, documentation and privacy guards pass.
An initial `python -m ruff` wrapper path error was resolved by invoking its actual
entry point; no source suppression was used.

Fresh physical Xiaomi14 Android16 manual UI checks cover phone Settings version,
related search, renamed page, child Up query retention, Clear/no-results and
Close/reopen reset; monitor controls/Settings/local keyboard, Audio child-return,
Clear/no-results/outside reset; five-gesture help content and Close/native Back/
outside/reopen; pointer idle, stationary hold, release-idle and first wake tap.
The author inspected selected native captures. Monitor is1920×1200, default
single landscape, logical UI DPI320; phone is portrait at its existing font/density.
No personal app was opened. The timed Xiaomi test-helper Install via USB dialog
was subsequently observed and confirmed for the requested package; no remembered
choice/global security switch changed. Current input, phone UI and monitor
instrumentation all pass on the same final app/test hashes: input59.437s, phone
UI37.250s and monitor125.797s. Input includes real rotations/Fit/takeover/background
and native idle/fade/remaining-finger/wake checks; phone UI includes help/search
and six User guide width/font combinations; monitor includes four rotations,
separate Fit and search/readability. The first launch reported Process crashed
before test results, with Android pid/startSeq problems. Target force-stop before
each suite allowed execution; exact initial cause is unestablished and the failure
is retained. No product/test source changed to obtain these passes.

Author inspected native Help/phone-keyboard and pointer frames, eight monitor
search states and eight touchpad/rotation guide states in normalized derivatives.
This is scoped author review, not a human pixel golden or performance certification.
Pointer idle ran at the input suite's final rotation plus manual landscape, not
every rotation/Fit. Controller/split held-idle, teardown-during-fade, compact-help
large fonts/TalkBack and other OS/OEM cases remain partial. Current native evidence
supersedes the earlier blocked-verification status.

Earlier feature-specific native/manual results and source differences are linked
explicitly as historical evidence. Current three-suite results are distinct. See the
[current release-preparation summary](../../testresults/0.19.0/summary.md) and its
verified private local-only archive. Private device evidence is not uploaded.
CI will produce its own source-bound host report/JSON and guarded archive.

The preceding menu-readability workspace cold-restore test reached the restored
task/display/axes but timed out waiting for input-window admission before contact
injection. Cause is unresolved and no claim of a clean full workspace regression
is made. This release does not claim a fix for that separately retained failure.

Fresh release architecture and final independent approvals are still pending;
record only actual returned verdicts, not planned approvals.

## Release architecture attestation

This actual review supersedes the preparation statement above that architecture
approval was pending. Final code review and publication remain separate gates.

- Target: 0.19.0/code52; preceding v0.18.0 at
  `c05649ce804f077700db50676827b48a812ac321`.
- Exact frozen pre-attestation tree:
  `3d62795633cd96f334122fd0ea2dba450e05b005`.
- Exact inspected canonical digest BEFORE this attestation:
  `8e427d2bdb8c37fba09fe65edea202c6c50b3560801625bbbb82c3503d68ed1a`.
- Fresh read-only expert thread/session:
  `01a1d0da-b5cb-7021-bb92-b1334e5e40e7`.
- Actual review time: `2026-11-12T17:49:44.597Z`.
- Actual verdict: **APPROVE for release architecture; no blocking findings or
  unresolved architectural decisions.**

The expert independently verified all551 staged/export blob entries with canonical
newline handling, reconstructed tree/digest and cumulative baseline diff, and
confirmed the index was unchanged. It inspected whole-product/component/feature
maps and sequences, actual changed source/tests and called lifecycle/input/
shutdown owners, requirements and current/historical evidence. Stale approvals
were not counted; unrelated local drafts were excluded.

Confirmed ownership: bounded independent Settings query/editor state, watcher and
page guards, existing service actions without query IPC/persistence/logging;
main-thread service cursor policy with independent opaque holds and exact-owner
deadline/fade guards; passive shared reading/sketch views; unchanged confirmed
Developer Mode cleanup/audio barriers, primary-only mutation and per-switch
uncertainty. Permissions/AIDL/shell authority/dependencies/CI topology are unchanged.
Private frame collection and guarded host-only public delivery remain separated.

Evidence inspection verified the current253-member archive and four historical
archives,233 source fingerprints, current app/test hashes and all three native
terminal outcomes, plus295 JVM/210 tooling passes/one skip. It inspected selected
native phone/pointer frames, both current monitor contact sheets and source-matched
controller/input/setup diagrams. Original supervisor folders denied read access;
their archived copies passed verification. No builds/device changes, secret reads
or publication were performed by the expert.

Nonblocking inherited findings: debugging architecture calls shutdown “one-way”
although its AIDL returns a synchronous Bundle, and ARCHITECTURE retains a blanket
native-IME-disabled statement inconsistent with the optional documented mode.
These prose inconsistencies remain explicit debt; the implementation/owning optional
keyboard contract is unchanged. The generic session queue remains unbounded with
selective shedding; this change adds no input queue. Residual controller/split idle,
fade-teardown, cross-configuration/font/TalkBack/OS/performance gaps and the old
workspace cold-restore input-admission timeout remain as recorded above.

Read-only report and event trace are retained privately under
`.local/release-0.19.0/architecture-native.*`. Only this faithful attestation follows
the inspected snapshot. Two different fresh final reviewers must approve the full
canonical digest including this record before commit/push/release.
