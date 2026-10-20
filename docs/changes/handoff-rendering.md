# Remaining-app rendering after phone handoff

## Contract and scope

FR-025 / DX-HANDOFF-001 requires the underlying monitor app to remain visible and
interactive after the foreground app's existing task moves to the phone. The
user authorizes emulator diagnosis and repair. Android11 phones and saved
preferences remain untouched. This is part of the pending0.13.0 migration candidate;
the observed failure also exists in reconstructed Java code38.

The original API34/35 child campaigns fail remaining-app rendering after four
rotations and Fit. The API34 Java/Kotlin failure PNGs are byte-identical, while
native metadata reports the remaining app drawn. That observation alone did not
identify the failed rendering boundary. Emulator console display1 now provides
an independently calibrated1920×1200 output; Android's physical logical display2
and each session's managed virtual display are separate identities. The earlier
HWC-display1 and stretched gRPC attempts remain invalid capture attempts.

## Architecture preflight

Impact is rendering/lifecycle/input and verification identity. Fresh read-only
expert /root/handoff_render_arch_preflight approves diagnosis at
2026-10-19T00:37Z after graph/source/spec/diagram/evidence inspection. Rendering
and occupancy have separate owners: Android composition/VirtualDisplay Surface/
TextureView/host versus task observer/coalesced query/generation-guarded state.
The initial diagnostic/parser changes add no controls,
layout or navigation. A subsequent launch-animation candidate has a separate UI
preflight by /root/handoff_launch_ui_preflight; its proposal changes only the
managed app entrance transition, preserving existing edge-gesture animations.

First run a separately named minimal synthetic two-app/phone-launch sequence,
retaining ordinary launch/task reuse and the original full suite. Resolve emulator
display identity before independent capture; collect bounded native composition
and paired frame evidence before cleanup. After rendering, verify one native
contact to the remaining task. Do not extend deadlines, retry input, force phone
tasks back, restart apps or recreate the desktop. A concrete runtime change needs
expert re-review after diagnosis, especially Surface detach/resize ordering.

UI expert /root/handoff_launch_ui_preflight approves the bounded launch-transition
proposal at2026-10-19T01:07:16Z: same Apps → choose app → monitor flow, potentially
more abrupt managed entrance transition, no new layout/navigation/edge animation.
No material decision requires another user answer under the existing repair
authorization. The approved reference catalog remains absent; this does not grant
visual acceptance or promote baselines.

After two animation-only experiments still fail fast overlap, architecture expert
/root/handoff_render_arch_preflight approves a narrower completion-boundary
candidate. Only an API31+ confirmed hidden/sole/exact/fullscreen managed task with
native capability takes this path. Other routes remain unchanged. A per-call
callback/latch on an owned handler thread gates success, followed by fresh owner,
root/task/display/component/fullscreen verification. Missing or interrupted
completion terminates without another start, movement or fallback. The helper's
synchronized launch monitor is held during the finite worker wait; input/Stop
contention remains a required measurement. The native callback only releases its
own latch. New strict evidence is separate from legacy package matching and must
reject extra/malformed task rows and ambiguous user/visibility tokens.

After the focused API34/35 passes, the same architecture expert approves two
related bounded corrections. Android14's protected-window dump joins a complete
`isOnScreen` field to its seamless-rotation diagnostic; the inherited parser
treated it as unknown. The correction accepts standalone/known joined fields,
retaining sticky unknown for malformed/duplicate/conflicting visibility. It adds
no inspection, permissions, timing or secure-capture capability.

The single-view launch result previously called unguarded `report` after its
RPC. `LaunchScope` now captures generation/display/bridge after configuration,
checks before submission and checks ownership plus single-view mode at the actual
main-thread success/failure mutation. Stop/restart/split discard obsolete results.
Generic queue/report behavior is unchanged. The expert inspects parser78daccbf,
scopef3410ca8 and SessionService54aa73d9 with their actual passing host checks.
This is implementation preflight, not final/release approval.

UI re-review /root/handoff_launch_ui_preflight at2026-10-19T01:45:24Z approves
the exact corrections: restore the existing protected-screen Help notice, and
keep Stop/replacement authoritative over stale launch results. The existing user
repair authorization resolves these without a new material design choice.
Existing controls/style/navigation and Chrome privacy choices stay as specified;
rendered/native acceptance remains separate.

## Planned verification

- Frozen matching APKs/source and dedicated API34/35 images, sequential native
  actions, ordinary shell/app identities and private synthetic evidence.
- Independent emulator output versus monitor-window capture, with explicit target,
  timing, geometry, native task/window and substantive composition observations.
- Original child four-rotation/Fit/exit suite and fresh remaining-app input after
  repair; affected API30/36/37 paths with known unrelated failures kept separate.
- Required host gates, concrete coverage reconciliation, rendered diagram review
  and external private archive. Physical acceptance and release approval remain
  separate; no speculative runtime workaround or publication is implied.

## Outcomes

- The test-only optional native-bounds parser correction passes host quality,
  coverage, requirements, documentation, privacy and fixture-runtime gates;
  150 JVM cases pass. The emulator retained fixture task55 across the in-place
  update, and native20261019T010045.672372Z executes its identity/removal assertions.
- Frozen generationv2 app811e0f5e720e90c5867836f0d7a1f6e60d30dfa340b3082d3a749ee964b890b1
  and unchanged test2063a4352ba4efbc1d3bfc69f55a4df085176bcec9217025e7bab66ea8af2be5
  reproduce the original remaining-app failure after rotations/Fit. Native console
  frames021-025 and PixelCopy show the same red-left/black-right departed-parent
  frame before cleanup. The frame hash remains ab9ac11100e88aaea6293cca7d8741653f16a3da81b89240152837d64f25e5ab.
- The original failure metadata places task59 on phone0 while SurfaceFlinger still lists it under
  managed display6. Phone launch at01:01:06.888 precedes managed transition50's
  finish at01:01:07.333; transition49 was also still animating. This supports a
  platform transition-reparent race hypothesis. Subsequent paired probes below
  isolate that boundary.
- The fresh-task reduced diagnostic redraws correctly. Later reused-task ADB
  reductions remain inconclusive: ordinary managed `am start` did not establish
  desktop placement, so their blue frames do not verify the failing path.
- Paired reused-task probes establish the second app on the managed display
  before foregrounding the parent. Both routes use the same12-argument native
  launch RPC and ordinary phone launch before diagnostic JVM teardown. Intent
  `NO_ANIMATION` and zero custom-task animation alone still fail. Waiting for the
  actual native finish callback produces six fresh full remaining-app frames.
  The callback occurs about42ms after launch in that successful reduced sample;
  an already-top result3 has no callback and is excluded from eligibility.
- Native transition finish restores a captured Surface parent. A phone move
  before that finish can therefore leave task metadata on phone0 but the old
  Surface under the managed display. ForegroundLaunch gates its narrow success
  boundary on native finish plus fresh strict placement. Arbitrary external phone
  movement before completion remains an unresolved platform-overlap case.
- Frozen generationv4 has134 source bindings, app
  `4acc07ca388bb8247de7afe1da493f264bbfea81f283e6af4ef346b676a300c5`
  and test`b7a61be7f82c7369098d0ad1b7dae4b0a70a8a4bd319178ac815c847ed36c85b`.
  Original child runs pass on API34 `20261019T013235.902913Z` and API35
  `20261019T014041.676441Z`: all four rotations, private child/Back, ordinary phone
  handoff, Fit/revert, remaining pixels and last-app exit. The new tail sends one
  native monitor DOWN/UP, verifies fresh exact event counts/timestamps/coordinates,
  and proves the remaining task stays on the managed display while the parent
  stays on the phone. Normal input also passes on both APIs at
  `20261019T013523.504986Z` / `20261019T014125.833311Z`.
- Root inspects the passing API34 monitor capture and console frame020 (before
  display teardown), plus API35 full blue1920×1200 remaining-app capture. These
  show the intended app rather than the departed parent. Sampled console capture
  may affect timing; it is no latency or golden-reference approval.
- API34 v4 split `20261019T013629.511406Z` fails at protected-pane detection,
  after native fixture input. Its secure surfaced display18 window has the native
  joined visibility line. The parser is byte-identical to pre-candidatev1; that
  fixture uses the ordinary fresh launch path, not ForegroundLaunch. The failure
  is retained independently of the child/input passes.
- The follow-up observer candidate adds four parser and three scope checks.
  Host quality,165 JVM cases, coverage, requirements, documentation, privacy and
  fixture-runtime checks pass. Tooling209 reported cases retain208 passes and
  one Windows skip from unchanged sources. Frozenv5 has136 bindings and app
  `3fafa6a63813a9ab375acbc8f3454f558ce1279a0fbeac91e2f47a2aa6d8c504`;
  test APK is unchanged. Corrected-parser native split/protection and current
  child regression remain pending at this checkpoint.
- Dedicated API34 v5 protection acceptance passes at
  `20261019T014738.233059Z`, including notice/Help/dismissal across four rotations.
  Root inspects the r0 notice, r1 monitor Help and phone Help captures: icons/text
  are readable and the intended rotated guide is contained. No golden/catalog or
  user visual approval follows. The earlier v5 split run
  `20261019T014528.159595Z` fails before that protected tail: it correctly restores
  retained synthetic reader task95, while its first-page precondition expects
  a fresh parent. That failed run remains separate.
- The architecture expert approves extracting the child suite's exact initial
  isolation to DeviceTestSupport and using it once before the full split suite's
  receiver/controller launches. It verifies only known Parent/Reader roots,
  immediately revalidates root/task/display, enforces accepted removal itself and
  retains the original five-second absence check. No mid-scenario reset, broader
  task removal or relaxed navigation/state assertion is introduced. The
  navigation-only suite skips this setup. Exact inspected helper`ec0d0dad`,
  child`d429ad00` and split`4f216687` receive implementation preflight approval.
- A mapping attempt names that helper as an independent case; coverage rejects it.
  The failed host attempt is retained. The corrected map names real Child/Split
  run entry points and fingerprints the shared helper without changing coverage
  rules. All seven host gates and165 JVM cases pass in
  `handoff-render-shared-fixture-host-validation-v2`. Root and the architecture
  expert inspect all four updated app-launcher/protection diagram renders and
  their source bindings.
- Frozenv6 retains the same136 bindings and product`3fafa6a6…a6d8c504`, with
  test`7f053106789e8602fe023298392bc25ac09c48304bd39bb28c4f2f1d53a2d2d5`.
  The original API34 child/fresh-contact scenario passes after the shared
  extraction; its native run is bound by `handoff-render-api34-full-observed-v6`.
  Full split and API35 follow-up are still pending at this checkpoint.
- API35 v6 child/fresh-contact also passes at`20261019T015327.831094Z`.
  The v6 API34 split run`20261019T015212.866760Z` establishes the fresh fixture
  successfully, then fails a separate menu-observation NPE. Its `launchButton`
  traversed root/search off-main despite an off-main page/animation check while
  MonitorShell can rebuild children. This failed native run remains retained.
- Architecture preflight approves a test-observer correction inside the existing
  helper. One original15s await captures ready session/menu/root/search together
  on main, sets one query, rejects owner/search replacement and discovers the
  current enabled/measured row. Each poll assigns the current row or null;
  an intermediate draft's stale cached View is rejected during expert review
  before native use. No menu tap, input, product focus or contact is retried;
  intentional stale-button scenarios are unchanged.
- The exact final split source`537ce093…b26d75a` and all seven host gates in
  `handoff-render-picker-observer-host-validation-v2` receive bounded native-use
  approval.165 JVM cases still pass. Frozenv7 changes only SplitAcceptance and
  requirement metadata againstv6; the product remains`3fafa6a6…a6d8c504`, test
  becomes`3913c71d02b2cae3ccfc09ec338cf3d90b47c4ecf2e0ffb836d82b5513485170`.
  Native split/protection follow-up is pending at this checkpoint. The v6 child
  outcomes are source-bound retained evidence for its unchanged callers/helper,
  not fresh executions from the v7 test APK.

## Remaining limits

### Rejected-DOWN diagnostic preflight, 2026-10-19

The unchanged v7 full split suite passes on API34 at
`20261019T105631.956408Z` (239.993s). Its earlier 180s wrapper timeout remains a
timeout, not an assertion result. The maintained 900s budget and 1200s outer
envelope leave native assertion deadlines unchanged. API35 protection passes at
`20261019T105525.996238Z`; full split fails at `20261019T110059.517273Z`.
Physical-host DOWN reaches the Presentation. Managed-display DOWN is rejected,
then product teardown removes the host before physical UP. The before-input dump
shows `No DisplayInfo found!` and an owned NOT_TOUCHABLE app window; it is not the
exact reactive sample consumed after rejection. No classifier relaxation follows.

The passing API34 split run measures100 synthetic contacts after10 warmups:
14ms p50 and35ms p95 from injected physical-host DOWN eventTime to the separate-UID
fixture's dispatchTouchEvent, excluding rendering/broadcast/await time. This is
neither physical-peripheral latency nor native-completion/Stop contention proof.
Root inspects `monitor-two-apps-r0`, `monitor-two-apps-r1`, `monitor-top-bottom`
and `monitor-restored-empty-panes`: both fixture buffers, intended rotation,
thin divider and per-pane idle artwork are present. No golden/user approval is
inferred, and these are v7 captures, not a v8 diagnostic execution.

Architecture expert `/root/handoff_render_arch_preflight` approves a diagnostic-only
approach: preserve query order/count, primary failures and mandatory cancellation;
capture partial terminal paths and immutable event tuples; bind one bounded attempt
to a validated debug request/owner; isolate sink failures. Output runs after the
decision, records flush duration and may change later timing. The exact patch and
wrapper require review before one original API35 split run. This is neither
release approval nor approval to change recovery/classification.

The exact diagnostic implementation corrects two expert findings before native
use: bind the primary DeskService (the failed relay owner) and admit only the
exact synthetic InputTest component, while retaining null/source-exception
partial outcomes. Its four host cases verify consumed query order/count, original
return/exception preservation, partial paths and deferred flush with throwing
observers. All seven host gates and169 JVM cases pass. Frozen v8 has137 source
bindings, product `8313438cb4a80afdddc85a4204dba8fe79cd5ab5577a364d7c2e770f08d3ba7d`
and unchanged test `3913c71d02b2cae3ccfc09ec338cf3d90b47c4ecf2e0ffb836d82b5513485170`.
The freeze producer was formatted after execution without preserving its prior
bytes: its recorded execution-helper hash cannot be reproduced from the current
helper. Frozen source/APK bytes remain independently verified; this limitation
does not establish exact producer-byte availability.

The Android11 read-only admission probe sees the historical monitor fingerprint
and stable 0.12.5/code38 installation. Its later probe reports a showing lock
screen. No key access, update, app launch or input has occurred in this checkpoint.

### Diagnostic native outcome

The exact reviewed noclobber wrapper `1d20f1d6…18e1cb45` runs one original
API35 full split at `20261019T112107.884969Z`: native PASS245.752s,3136 assertion
lines, normal display0 controller return. Its100-contact synthetic callback
latency sample is20ms p50/40ms p95 after10 warmups, excluding rendering. These
assertion lines are not independent scenarios or proof of exhaustive coverage.
The existing controlled pending-window case passes in both panes: native DOWN
rejection reports retry, retains both displays, suppresses the held stream and
admits only the later fresh contact. This does not exercise missing DisplayInfo.

The one-shot diagnostic itself is **unverified**: the primary helper claims the
correct request as PID10228/display20, but no admitted InputTest rejection sample
is written. The history snapshot now has logicalSize959x1200 and an admissible
owned InputTest window. The previous v7 failure is intermittent, not retrospectively
fixed or converted to a pass. Wrapper collection fails explicitly and disables
its exact request; raw fresh cache/native outcome remain retained separately.
No classifier or cancellation behavior is changed on this evidence.

Root inspects v8 `monitor-two-apps-r0` and `monitor-protected-pane`: independent
buffers remain visible; the protected pane is black with readable notice actions.
The final diagnostic overview/sequence renders are inspected with no cropped labels.
This is bounded author inspection, not user/golden or release approval. The
publication duplicate probe's stderr is retained and its failure is asserted, but
the numeric probe return code was not saved. Command timeouts may precede a full
journal triplet. Do not claim complete failure-path command journaling.

### New physical verification preparation

Architecture reviewer `/root/migration_android11_ops_preflight` at
2026-10-19T11:22:43Z approves the separately versioned debug/test-only operations
approach. Exact source review identifies and resolves device-configuration hash
binding, pre-update test UID continuity, shell Shizuku/helper identity, unknown
display-type absence and invariant review-status gaps. The actual configured
test helper name is `dev.dexdroid:test-input`, not its component class name.
Read-only native process metadata also contains unrelated NAME values with
spaces; bounded three-column parsing preserves that last field while rejecting
unknown related roles. These are procedure corrections, not app routing changes.

The latest synthetic actual-procedure calibration passes155 cases against helper
`31bb702c…af3f437`. It includes source/artifact/review gates, two signing operations,
installed test identity, root/unknown process rejection, controlled producer UID
violation, exact ordered eleven-suite results, missing/duplicate/order failures,
phase order, locked admission without an operation, disconnected controller and
primary/secondary failures. Mocks do not certify real signing or hardware.
Earlier117/135-case results retain their own source snapshots; an intermediate
calibrator's expected-success-list error remains a failed calibration, not a
device result. A generator indentation error occurs before calibration execution.
At11:41:27Z the same expert approves the corrected concrete implementation for
contract/freeze completion, verifying the preserved executed v5 source/result and
helper hashes. The executed v5 source is `6124c4e8…7aec8d0`; the earlier base
calibrator has different bytes and is not its execution source.

Read-only phone facts confirm the stable38 hash/certificate, product UID10760,
existing test UID10763, original monitor and shell Shizuku server. The lock screen
is showing. No private release-key re-signing, installation, app launch, input, downgrade,
uninstall, data clear or preference replay has occurred. A fresh exact operations
pair and passing staged gate are still required before the admitted test actions;
final release architecture and final review remain separate.

Signing statements in this checkpoint refer to private release-key operations
for physical test delivery. Ordinary Gradle debug/test builds use debug signing;
Gradle may load local release properties during configuration. The archived
snapshot's broader "No credentials" wording is imprecise on that point. No
private operations signing/installation procedure has been invoked.

Normal input and Stop suites do not establish actual overlap with the new finite
callback wait. A bounded contention campaign must classify observed overlap and
separate immediate main-thread Stop from queued native cleanup. API31 trusted
display permission, API33 private-child click, API36 physical-host routing and
API37 experimental behavior remain separately recorded migration gaps. The final
Android11 physical campaign, release architecture and two fresh final approvals
remain pending. Nothing here authorizes broader compatibility claims or converts
an earlier failed run to a pass.
