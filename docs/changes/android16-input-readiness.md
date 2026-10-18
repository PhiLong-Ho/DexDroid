# Android 16 input readiness and menu Back

## Contract and preflight

Request: fix and test with the Android 16 Samsung connected to its physical monitor.
Branch: `fix/android16-input-readiness`, baseline `7b1b120` (0.12.4).
Requirements: FR-006 / DX-KEYBOARD-001; FR-033 / DX-WINDOWS-003.

Architecture expert `/root/android16_input_arch` approved the bounded approach on
2026-10-18: modern diagnosis only after rejected initial DOWN, stable owned
task/component/UID/display around the input-window query, positive pending
evidence only, whole-contact discard without replay. Unknown/ready/identity-race
rejections remain explicit failures. No modern focus stealing, per-touch dump,
polling service, readiness cache, IPC or persistence change. The existing task
observer handler emits fresh authoritative occupancy after successful retry.
Missing display sections remain unknown. A rejection that recovers before the
diagnosis can conservatively remain fatal; this change does not claim otherwise.

UI expert `/root/android16_back_ui` approved the exact correction: retain both
panes and existing concise retry status; first valid Back hides local keys and
served system IME, focuses a non-editor and preserves query/results. Next Back
navigates one level, without reaching the app underneath. No layout redesign or
material user decision. Proposal communicated to the user before implementation;
the user's requested fix authorizes this correction. No approved per-state UI
reference catalog exists; current rendered evidence must still be inspected.

## Diagnosis

Private baseline emulator evidence under `testresults/0.12.4/runs/` records one
split-navigation initial-DOWN rejection followed by a passing retry, two repeated
keyboard second-Back failures, and passing input/monitor runs. Full split was
interrupted by a host ADB daemon restart and remains unverified. These are
diagnostic evidence, not passes for this candidate. The Back trace shows Gboard
processing a second Back after local keys were hidden; root clearFocus allowed
an editor to regain focus. Do not attribute this to predictive Back without proof.

## Verification plan and current limitations

Run host metadata/identity tests, focused emulator keyboard and split navigation,
then physical Samsung monitor checks with synthetic fixtures. Preserve installed
settings and identity; production signing/install requires the repository's fresh
architecture and two final-review gates. Android 11 legacy readiness remains
separate and needs regression. Current candidate verification is pending.
Raw captures, personal device metadata and logs stay ignored/private, never public.

## Follow-up diagnosis and verification

The first deterministic delayed-window test (20261018T041943.677278Z) confirmed
pending recovery but exposed retained Android 16 verifier state on the next DOWN.
Architecture expert `/root/android16_input_arch` approved exact rejected-contact
CANCEL cleanup after inspecting that failure. CANCEL rejection remains fatal;
unknown/ready-window rejection never enters recovery. No app input is replayed.
The subsequent split-navigation run 20261018T042124.967534Z passed both panes and
four rotations (112 seconds). That was an intermediate build; final checks will
use the versioned candidate and the strengthened held-contact continuation test.
The intermediate keyboard run 20261018T041825.024532Z passed the previously failing
second-Back cases. Host lint found a redundant modern SDK check, removed without
changing the lint baseline. Tooling initially caught coverage fingerprints stale
after formatter output; regeneration follows formatting, then checks rerun.

Both affected Mermaid diagrams rendered with local Mermaid CLI/Chrome and were
visually inspected. Source-linked diagrams describe the conditional modern path,
verifier cancellation, suppression, fresh retry and explicit failure branches.
Raw renders remain private under `testresults/0.12.5/runs/diagrams/`.

## Predevice verification checkpoint

- `host-harness-ready`: qualityCheck PASS,143 JVM cases with no failures/errors/skips;
  formatting/lint/build pass, lint baseline unchanged. Requirements/coverage/docs/
  privacy/Ruff pass. Tooling-ready:160 pass,one platform-specific skip of161.
- Android16 emulator keyboard20261018T042426.182773Z PASS, including query/non-editor
  focus, systemIME hidden on both screens and next Back navigation. Product bytes
  identical to current; subsequent test edits affect only split navigation.
- Final split-navigation20261018T042853.294502Z PASS112s, both panes/four rotations,
  pending DOWN, verifier cancellation, restoring window during held contact,
  suppressed MOVE/UP, fresh retry and native child/Up/Back/root-exit/relaunch.
- Earlier strengthened run20261018T042538.908700Z FAILED a normal child-Up click
  before its window admitted input. Desktop retained and pending retry reported.
  Normal navigation harness now waits for the specific current activity window;
  deliberate rejected-DOWN injection remains direct. No product change for this.
- Android11 emulator keyboard20261018T042753.441647Z FAILED rotation1 during output
  update/rollback before hardware Back cases. Regression remains unverified;
  no baseline comparison establishes this as preexisting. Physical Android11
  production untouched. This gap is not counted as a pass.

Normal product SHA256`d6e10cde074d39f0c8d1fa3fefad4a7448f2325173f95b197c3a1c66d6c5fc51`;
current test APK`82bae0f7b3d8c64050ac22fb73d7e59a4a51c119db7ddbc5a5d3a6ed4d734ebc`.
The private bundle retains source fingerprints and both current APKs; keyboard
used the identical product with an earlier test APK. Source binding is scoped.

Rendered expert `/root/android16_back_ui` approved phone portrait and monitor
fixture panes at four rotations on2026-10-18. No clipping/overlap/stale idle cover
in those inspected states. No keyboard/menu/pending-feedback/Fit/Samsung pixel
approval or golden/reference promotion. The approved reference catalog is absent.

Private predevice archive:211 files,SHA256
`704ce96fd6673186bacb2cc4f5d3ec962500d033d2e7b05385ebb85e2c4fed81`,
`artifacts/evidence/android16-input-0.12.5-predevice/evidence.zip`.
[Summary and descriptor](../../testresults/0.12.5/README.md).
Local-only, owner-controlled private retention; reviewers have original/bundle
access. No upload or public device evidence publication authorized.

Private Samsung verification plan: after fresh architecture and two final reviews,
create same-certificate debuggable APK/test APK, verify package/version/certificate
and identical non-signature payload against host-checked normal APKs. Stop managed
displays before replacing product; preserve UID/settings, back up preferences
before suites. Run sequential synthetic keyboard/split-navigation/full-split and
monitor checks as applicable. Production replacement/removal of test fixtures is
mandatory afterward; new source findings reopen review. Physical Samsung, full
split/Fit/resize/lifecycle, Android17, Bluetooth and touch-to-photon remain
unverified at this predevice checkpoint. Cleanup-failure/device fault breadth also
remains a gap. This checkpoint authorizes no public release/tag/dispatch.

## Private-verification architecture attestation

Fresh reviewer `/root/input125_private_arch`,2026-10-18T04:33:59Z, explicitly
APPROVED the bounded private debug signing/installation checkpoint for0.12.5/code38.
Inspected pre-attestation tree`7ce07522c7f53f08001af1b7e2e41d8e58bf17e5`,
digest`e205c72e6192eb30a6f09f91cf2bac3137456b1db9e84d9cc5a4a8342d46bc86`.
Cumulative baseline publishedv0.12.3/`7ce8a42a9a2c2143284630d6d20c477ead5e5b34`;
current task baseline7b1b120. Expert inspected CRG, cumulative source/spec/tests,
system/component/affected feature diagrams and renders, signing override/guard,
actual private failure/pass evidence and delivery constraints.

No architectural blockers: modern diagnosis is reactive, stable owned target
identity gates recovery, checked native cancellation precedes stream suppression,
unknown/ready-window/reflection failures remain explicit and no input replays.
Authoritative refresh stays on the existing handler; shell synchronization,
stale ownership, pane boundaries, teardown/rollback and persistence remain intact.
No new IPC, polling service, persisted identity or UI-thread wait.

Independently verified archive SHA/CRC/all211 members,all109 source fingerprints,
normal APK hashes/runtime bindings,143 JVM passes and160 tooling passes+one skip;
Android16 keyboard/split-navigation passes and Android11 rotation/rollback failure
before hardwareBack. Original Samsung APK certificate matches expected release
certificate. Delivery guard finding resolved before verdict: only actual signature
metadata is excluded from payload equality, normal APK hashes are pinned and
ZIP duplicates rejected. Reviewed ignored guard hash
`7809090dc113997a09831dc3cf9dbffa67e21ca5c0ce69d12d62ae59b6e88efe`;
signing override`bc9d8332429bf2e91d790aa8429275fefd6f3fda4c682f0fc283c7d17acc9072`.

Scope ONLY private same-certificate debug verification after two fresh final
approvals: validate artifact identity/payload first, stop/verify removal of owned
displays, preserve UID10513/preferences on in-place update, privately back up
preferences before sequential suites, restore production/remove fixtures afterward.
Physical outcomes and guards remain pending. No production signing/delivery,
public release, push, tag, dispatch or publication approval. Samsung/full split/
Fit/resize/lifecycle, Android11 hardwareBack, Android17/Bluetooth/physical latency
and cleanup-fault breadth remain gaps; generic queue/framework-stall limits stay.
Expert performed no mutations and is not either final reviewer. Only this faithful
attestation follows the inspected snapshot.

## OEM fixture prerequisite correction

The first approved private build was signed and installed in place after guards:
UID10513 preserved, original preferences backed up privately before suites.
Samsung keyboard20261018T043705.367153Z PASS52.7s and monitor
20261018T044136.446851Z PASS107.2s on that exact product/test pair. Monitor reused
retained installed APK paths so runner identities remain faithful after local
normal test outputs changed. Source files for those suites/product are unchanged.

Samsung split-navigation20261018T043802.764884Z FAILED expecting rejected-DOWN
retry. A native system SPY-only window accepted the deliberate NOT_TOUCHABLE-app
contact; no app target or shutdown was observed. This platform prerequisite was
not met, so it does not certify rejected-DOWN recovery. Local test-only correction:
recognize a positive system-owned SPY-only target for exact display/current downTime
with no foreground/other target, report rejected-DOWN recovery and status-clear as
SKIP, retain all held-contact/no-app-action/fresh-retry/pane/navigation assertions.
Unknown states still fail. No manufacturer-wide exemption or product change.
Actual-snapshot extracted Java diagnostic probe plus five negatives passes; this
is not counted as a formal JVM/device case. Architecture preflight N/A for this
local test-observation correction; product ownership/UI/behavior unchanged.

Host-oem-final qualityCheck PASS143 JVM cases; tooling-oem160pass/one platform skip
of161. Coverage regenerated after formatting. Source fingerprints/normal APKs are
in normal-inputs-v4: product`b4d80c6b305006cea2095e47cc26da0fbe3c816f614d4ed815e85ab2dc4e8e33`,
test`65ac9830f97b81272fe746d4502badc994206f9d28709e4a1876c4d916af48cf`.
Product source and every non-signature payload member equal installed private
product`d8cbbde28d5c01c69781bc01bbd52040c2bb2ce62b83bfa67bbf547b3b227477`;
ZIP/signing-container bytes differ from earlier normal APK, explicitly verified.
Updated ignored guard hash`d77a30abd4bf4db1d3c30a35e284b7302290b6de1e25049a95d8e11510a34b86`
pins current normal inputs. It requires renewed architecture/two fresh final
reviews before signing/installing changed tests. Previous approvals remain
historical and are not reused. New split suites/production delivery still pending.

[OEM checkpoint descriptor](../../testresults/0.12.5/oem/archive.json):330 private
files,SHA256`6fc361757c11251ab7439d9e44b36b81b8cf0231d2e605fdfd765ba63eaeccff`,
local`artifacts/evidence/android16-input-0.12.5-oem-predevice/evidence.zip`.
Includes prior failures/current host/provenance and Samsung outcomes. No upload;
owner-controlled private retention. Existing predevice archive stays unchanged.

## OEM private-verification architecture attestation

Fresh expert `/root/input125_oem_private_arch`,2026-10-18T04:45:49Z, APPROVED
bounded renewed private test signing/guarded Samsung synthetic verification only,
after two fresh final approvals. Version0.12.5/code38; mainbaseline7b1b120,
cumulative published baselinev0.12.3/7ce8a42. Exact inspected pre-attestation tree
`fc9d33427e8039473091a752185a6af98aac219f`, digest
`85f33d93ea9746edf7a8fb3c693982f82f7ffe9635ccbb1921e06290f6900d15`.

Inspected CRG/cumulative source, whole-product/component and input/split diagrams,
rendered input diagrams, spec/tests, override/guard, actual archive/outcomes.
No architectural blockers: precise current system-SPY-only acceptance condition
explicitly skips rejection recovery/status assertions; unknown states still fail,
held continuation/no-app/no-replay/fresh retry/native navigation remain. Product
ownership, cancel, queues, teardown and persistence are unchanged. Verified
archive SHA/CRC/all330 members,all109 source fingerprints, normal APK hashes and
product payload equality, host success,160 tooling passes/one skip, Samsung
keyboard/monitor passes and retained prerequisite failure/emulator recovery pass.
Probe remains diagnostic only. Validate artifact identities/payload and stopped
managed displays before replacement; preserve installed product/UID/preferences.
Production/fixture cleanup remains mandatory. Renewed split/full-split/Fit/resize/
lifecycle, Samsung rejection recovery and Android11 Back remain unverified.
No production signing, push/tag/dispatch/publication/release approval. Expert made
no mutations and is not a final reviewer. Only this attestation follows snapshot.

## Samsung validation and focus drawing correction

Samsung16 focused split navigation20261018T044900.272271Z PASS90.912s,
both panes/all four rotations. Explicit OEM-SPY prerequisite skips do not certify
rejected-DOWN recovery; held continuation/no replay/fresh tap and navigation pass.
Full split20261018T045104.021512Z is pending on retained approved d8cbbde/f5eed32
APKs. These binaries predate the following drawing-only correction.

UI expert `/root/android16_back_ui` inspected private Samsung search/first/second
Back captures and found gray fullscreen default focus feedback. Exact authorized
correction: disable default focus highlight only on the noninteractive shell root;
retain root focus for IME dismissal and normal interactive control feedback.
Architecture impact N/A for this incremental correction: one View drawing property,
no routing, ownership, lifecycle, threading or persistence change. Existing input
architecture approval still describes the underlying behavior; delivery needs fresh
architecture/final reviews. No material layout decision or new user choice.

FR-006/DX-KEYBOARD-001 updated before implementation. Acceptance draws only the
synthetic shell onto a cleared bitmap: open-menu corner pixels must match before/
after keyboard dismissal, then closed occupied-desktop corners must be transparent.
This preserves intentional menu scrim alpha and catches fullscreen focus tint
without capturing user app content. Emulator and current Samsung renders pending.

## Completed pre-delivery checks

Samsung full split20261018T045104.021512Z PASS, including four rotations,
Fit/resize, native input, ownership, lifecycle/cleanup within suite scope. It used
retained approved pre-drawing-correction d8cbbde/f5eed32 APKs; no routing source
changed afterward. Explicit system-SPY rejected-DOWN prerequisite skips remain.
Current emulator keyboard20261018T045144.278881Z PASS63.536s includes the new
scrim/transparent-margin assertions and functional focus/IME/Back checks.
Normal final APK SHA72ca6885ce7944d439d60832b60bcb0a32e0e88f1f54bbd7f7e8063877123557;
testSHA5bd5e722314d6e96a536cd5cb7ee60630c3e82f0cba521703ce54520d7ae51a5.
All source fingerprints and retained normal APKs are in normal-final.

UI expert `/root/android16_back_ui` APPROVED bounded emulator renders
emulator-focus-render/{keys,first-back,second-back}.png: query/results preserved,
no gray canvas after first/second Back, normal idle desktop. This is the supplied
landscape state only, not Samsung/Fit/four-rotation visual approval. No approved
golden catalog exists; old gray Samsung renders remain unapproved. Corrected
Samsung production captures and native smoke are mandatory after delivery.

Host-focus qualityCheck PASS143 JVM tests; tooling-focus160pass/one platform skip
of161. Privacy, documentation, requirement/coverage and Ruff checks PASS. Lint
baseline unchanged. A mistaken validate_requirements.py command found no file;
the actual check_requirements.py subsequently passed49 mapped requirements.
No Android17/modern OEM breadth, physical Bluetooth/USB, frame pacing or
touch-to-photon verification claimed. Android11 emulator rotation/rollback failure
remains unresolved/unverified, with no evidence that it predates this change.

Delivery scope requested: guarded local production0.12.5/code38 install on Samsung
only after fresh architecture/two final approvals. Inspect ignored
.local/input125/{verify-production,install-production}.py: verify expected
certificate/nondebuggable/fixture-free APK+AAB, stop managed displays, restore
exact original preferences before in-place update, preserve UID10513, uninstall
test package, refuse fixture launch and restore phone controller. Native monitor
search/Back/render smoke follows installation; failures reopen candidate. No
public release/tag/push/dispatch is requested or claimed by this checkpoint.

## Local production delivery architecture attestation

Fresh expert `/root/input125_delivery_arch`,2026-10-18T04:56:12Z, APPROVE bounded
local production delivery after two fresh independent final approvals.
Version0.12.5/code38; taskbaseline7b1b120; publishedbaselinev0.12.3/7ce8a42.
Inspected pre-attestation tree`08ba0ac50c1ea1a26316eea543a42497436e226f`,
digest`e098f5cda9b2482daa8310b4446a32f5ad766287ba3cf56ce4a9f5010a0c37d3`.

Expert independently inspected graph/cumulative source/spec/tests, whole-product/
component/input/split diagrams and rendered input diagrams, production guards and
evidence. No architectural blockers. Reactive modern diagnosis retains stable
target/pending checks, checked CANCEL, whole-contact suppression/no replay; unknown
states remain failure. Authoritative existing task-handler refresh; no new IPC,
persistence, queue, polling or UI-thread wait. Back/root drawing correction retains
query/non-editor focus. Guards stop owned displays, restore exact preferences,
preserve UID10513, verify signed fixture-free production and remove test package.
Verified archive SHA/CRC/all496 manifest members, all106 current source fingerprints,
normal APK hashes, byte-identical delivery scripts, host143/0fail/errors/skips,
tooling160pass/one skip and recorded Samsung/emulator outcomes.

Permits APK/AAB production signing and guarded local Samsung install only after
fresh final approvals, followed by mandatory native production search/Back/render
smoke; failure reopens candidate. No push/tag/dispatch/public release/publication.
Gaps: corrected Samsung smoke/render pending, explicit OEM-SPY rejection-prerequisite
skips, unresolved Android11 emulator rotation/rollback, bounded emulator visual
approval/no golden catalog, Android17/OEM/physical keyboard/mouse breadth,
touch-to-photon/frame pacing and cleanup faults. Existing generic queue/framework
stall limits remain. Expert performed no mutations/signing/install and is not a
final reviewer. Only this faithful attestation follows the inspected snapshot.

## Stable Java baseline release continuation

User authorized Android11 regression/Android17 compatibility, fixes and public
release0.12.5 as the final stable Java baseline before Kotlin migration. Earlier
local-only signing approvals do not authorize this expanded release candidate.
Fresh release architecture and two final reviews follow completed checks.

Architecture preflight `/root/stable125_compat_arch` inspected current graph/source,
retained API30 failures and exact modern evidence. API30 fresh keyboard attempts
20261018T055207.592042Z and20261018T055403.683084Z fail the first bottom Apps gesture
before rotation; legacy-navigation20261018T055247.858643Z PASS. Current compositor
header is present, so a missing-header diagnosis is not established for these runs.
Coordinate/system-bar/host-frame diagnosis remains independent.

API37 keyboard20261018T055207.594764Z fails first hardware Back. Private native
api37-ime-native captures show actual SearchEditor EditableInputConnection and
Gboard on phone; first Back hides Gboard while local keys remain visible. These
native images are failure evidence, not passes. Architecture expert and UI expert
`/root/stable125_keyboard_ui` APPROVE exact SearchEditor-only IME detachment:
onCheckIsTextEditor false/onCreateInputConnection null, preserving EditText,
Editable/key listener/selection/accessibility/local keys and Back dispatch. No
input-type/global IME/phone/profile/managed-app changes. No material UI decision;
user's requested compatibility fix authorizes restoring this existing contract.
FR-006 updated before code. Verify hardware editing/modifiers, accessible editing,
no system IME before Back, first/second/third Back, both host types and rendered
rotation/Fit scope. Current UI reference catalog remains absent. Architecture
ownership/threads/persistence unchanged; input diagram records the local editor
without a system input connection. Product changes invalidate previous bindings.

Intermediate no-editor classification failed native Ctrl+A in API37 run
20261018T055621.266529Z. Platform TextView source requires onCheckIsTextEditor for
isTextEditable/native selection. Architecture expert approved retaining inherited
editor classification and only returning null from onCreateInputConnection;
preserve setShowSoftInputOnFocus(false), no custom shortcut implementation. This
revised approach preserves actual native editing/accessibility. Tests still
require absent system IME before Back; runtime outcome pending.

API30 first-edge failure diagnosis: artificial console display flags1739 included
SHOULD_SHOW_SYSTEM_DECORATIONS512, with visible NavigationBar2 touchregion1144..1200;
injected edge start1194 enters SystemUI. Exact host/logical sizes both1920x1200.
Architecture expert approved controlled AVD flags1227(no secondary own system bars),
same1920x1200/160, matching no-own-bars HDMI. Existing tests/edge assertions remain;
retain earlier decorated-display failures and do not claim that configuration passes.

API30 resize cause positively diagnosed in20261018T060230.789305Z: command exit
validation's substring `Error:` matched successful SurfaceFlinger `mError: 0 ns`.
Architecture expert approved anchored horizontal-indented actual Error lines plus
all nonzero exits; retain compositor header and exact output-detachment guards.
ShellCommandResult tests cover metrics/embedded tokens, first/later/indented errors,
nonzero output and bounded diagnostics. Original/rollback/helper exceptions now log
their causes; bounded shell errors no longer contain entire dumps. FR-015 records
the behavior. No compositor bypass, owner/thread/IPC change. Modern smoke and
API30 real resize acceptance remain required.

API37 immediate Back diagnostic20261018T060021.348584Z straddles native child focus
loss/gain. Architecture expert `/root/stable125_back_arch` approved test-only
settled-key prerequisite matching current page/task/UID/pane/display/focus/layout
before one Back and ordinary A; fixture reports focus loss too and logs native
key/focus metadata. No product focus/key flag/readiness change; retain immediate
transition failure as diagnostic, do not claim arbitrary transition Back fixed.
Shift-arrow acceptance now emits real Shift down/up around arrow because native
movement selection reads pressed modifier state; prior meta-only injection is
retained as failed harness attempt. Selection orientation is immaterial; selected
range bounds remain asserted.

Physical BlackShark regression reuses already-signed private d8cbbde/f5eed32 inputs,
with original0.12.4 APK/UID10760 and preference bytes preserved. This verifies that
snapshot, not the later search-connection/parser fixes. New final production/native
phone acceptance is required; no newly signed binary has been substituted secretly.


### Current compatibility results and retained failures

Current normal product SHA `e56419749b9e289742019a3a658c24c5480dbd204e11fead07c1384627f84a18`:
API30 keyboard20261018T060832.640589Z PASS49.626s and full
split20261018T061004.582535Z PASS; API37 keyboard20261018T060842.952430Z
PASS53.555s and monitor20261018T061932.577970Z PASS. Later test-only changes
require their affected tests again; these results remain bound to the recorded
APK/test hashes rather than pretending the evolving tests already ran.

The API37 full split20261018T061437.257615Z early post-divider contact was
correctly cancelled with retry status while its native input window was absent.
API37 input20261018T062116.244127Z instead touched through a visible system
transition snapshot; Android rejected that obscured contact while its app frame
was still temporarily letterboxed. Architecture expert `/root/stable125_back_arch`
approved test-only settled-input prerequisites: exact current pane/task/component/
UID/display, resized native frame and touch region, revalidated identity, and
modern display-scoped numbered window ordering excluding overlapping snapshots/
unknown occluders. One contact and the existing result assertion remain; no
contact retries, injected focus, new product waits or suppressed failures.
Dedicated early-contact recovery remains a separate assertion. Source helper
parser checks and raw failures remain private. Corrected device checks pending.

Physical BlackShark retained-snapshot input/UI passed before the monitor detached;
after user reconnect, monitor/keyboard/profiles/idle/child/catalog/protected/icon
passed. Controller-only run20261018T061528.048370Z was invalid configuration:
it requires no monitor and is excluded from the standard eleven-suite monitor
scope. Physical split20261018T061629.362410Z FAILED native touchscreen click during
rapid performance contacts. Expert `/root/physical_split_diagnosis` found received
DOWN/UP but native focus lost during the contact; legacy host-focus fallback was
102ms. Nearby Gboard service churn is contributory evidence, not proven sole
cause. Historical ANR windows belong to another UID/display and cannot explain
this current fixture. This remains unresolved until exact-current physical rerun;
no assertion is weakened and no full physical pass is claimed.

UI expert `/root/stable125_keyboard_ui` APPROVED six native API30 and six native
API37 keyboard/first-Back/second-Back monitor+phone renders under
api30-stable-render and api37-stable-render. Local keys appear initially; first
Back retains query/results without keys or full-canvas focus artifact; second
Back reveals idle background; phone has no Gboard. Also approved bounded16 API30
split captures (Layout/combined launcher four rotations, phone modes, rollback,
empty/mixed/protected states). An initial missing-content interpretation of one
rollback PNG was withdrawn after reopening its original single image; no product
change followed. These are bounded actual render reviews, not a golden catalog,
all-state/physical visual approval or performance claim. No approved per-state
reference catalog exists. Root inspected the updated input overview and sequence
Mermaid renders against source; the shell validation edge belongs to the bridge.

### Next guarded private verification checkpoint

Before current physical testing, obtain fresh architecture and two final reviews
of the frozen inputs. Scope permits same-certificate debug APK re-signing and
in-place Android11 test update only, using reviewed local scripts
.local/input125/sign-stable-private.py and install-stable-private.py. Verify exact
normal payload equality except signature metadata, package/version/certificate,
original UID10760 and retained original0.12.4 preferences. Never publish these
debug packages or private device evidence. Full current physical split and
applicable regression remain mandatory; failures reopen the candidate. Public
push/tag/signing/publication still require completed device evidence and fresh
release architecture/two final approvals of the expanded final candidate.


### Android17 shell cleanup cause and native recovery contract

Diagnostic build f05dea7 and test339d049 retained streaming logs before teardown
in api37-cleanup-stream. The exact cause is ApplicationSharedMemory not initialized:
WindowManagerGlobal.getWindowManagerService fails inside the Shizuku app_process
helper before invoking the transaction barrier. A read-only runtime method probe
confirmed syncInputTransactions(boolean) exists; the missing-method hypothesis is
rejected. Architecture expert `/root/physical_split_diagnosis` APPROVE direct
ServiceManager window Binder acquisition and IWindowManager.Stub.asInterface with
explicit null rejection. DesktopTasks retains its boolean/legacy-noargument
transaction barriers, removal timeout/identity checks and propagated failures;
SessionService ordering, generation/controller-return guards and ownership stay
unchanged. SPEC FR017 updated first; desktop-session sequence reconciled and
rendered/inspected. Fresh API30/API37 Stop-to-immediate-launch and current physical
regression remain required; no successful cleanup claim yet.

Idle diagnostic20261018T062931.361526Z separately proves the same resumed visible
bound phone controller and ready desktop, but focus belongs to MonitorActivity.
Native input metadata marks the phone window NOT_FOCUSABLE while touchable.
Architecture expert `/root/stable125_compat_arch` requires observable native phone
Settings/Close and fresh monitor/app input before changing any continuous-focus
assumption. Acceptance now specifies same visible bound owner and actual native
controls on both screens; strict explicit-Stop phone foreground/focus acceptance
is unchanged. Its execution remains pending. No product foreground forcing or
input retries are authorized by this test correction.

API30 settled-window prerequisite initially assumed JoyUI rootTaskId decoration
not present in AOSP. It now binds exact input-window token to native WindowManager
ActivityRecord task/component/display/UID plus AM before/after identity. Retained
extracted-Java parser harnesses cover eight modern occlusion and eight legacy
identity cases; these are not repository JVM/device passes. Split rendered-frame
checks now precede initial rotation contacts and verify current bitmap/TextureView/
fixture dimensions. Native setup admission runs only after ready/launch invalidation
and geometry/owner changes, outside steady latency samples; no focus prerequisite,
contact retry or weaker click result. Immediate-transition input remains separately
bounded by the product's pending-recovery contract.


### Frozen private verification checkpoint results

Normal product `eef7ebf6f569e2785e069ebe5aa213c7dd40f61709bbf044fc60179a717fd64d`;
current test `d59ec76ce4cd9a94c1092085fb07ba027651de79dd55a1ecf6bfe681fd3b52ed`.
Frozen source fingerprints and exact APKs: stable-native-legacy-inputs/provenance.json.
API30 input20261018T063549.354803Z PASS30.128s, including four rotations and
Stop/immediate launch without cleanup failures. API37 input20261018T063443.893846Z
PASS and idle20261018T063516.058883Z PASS51.623s, including observable native
phone/monitor recovery and strict Stop focus. Those modern runs used test25488c5;
only legacy ICU literal-brace escaping changed afterward, which their modern
branch does not execute. API30 initial ICU regex failure063454.651635Z retained;
its corrected native path is exercised by the passing API30 input run.
Current split and remaining emulator suites are running and excluded from this
checkpoint archive; they are not passes. Physical retained-build failure still
requires exact-current regression before public release.

stable-private-host qualityCheck PASS146 JVM cases, zero failure/error/skip;
stable-private-tooling PASS160 cases plus one platform skip of161. Requirement/
coverage, documentation, privacy and Ruff checks pass. The old private signing
approvals are stale for these inputs. Owner-only archive stable-private/archive.json
retains1176 files, SHA1f2460e5b66254ccb042bfb2ea35a33587bce78dd60ff960b89f096ad067538c,
including source bindings, completed outcomes, actual failures, renders and exact
local signing/install plans. Hash/member verification completed during packaging.
Availability is local to this workspace until owner deletion; no public upload or
retention promise. Reviewers can access the original runs and verified bundle.
Pending device outcomes reopen the candidate if they expose a product defect.


Private checkpoint source supersession before review: current normal test
2244e8725ef20172e9c9fe51e57d7efa515477eedcd464ef78a2b249db19e077,
producteef7ebf unchanged. API37 split063607.757370Z reached phone-pointer controls
then failed because its top gesture started inside the system statusbar. All
intended split edge-gesture paths now use existing measured system-bar safe-area
support; app coordinate mapping, contacts and result assertions are unchanged.
This is test-driver correction under the existing modern edge contract, not a
product navigation redesign. Current API37 rerun and API30 fullsplit remain pending.
Supplement stable-edge/archive.json SHA
f9eb9fc4025303b0a51fe31a5e881cbd41a3c14b3cb18a67a0552830b921ca8f
retains52 exact current inputs/plans, source fingerprints, current qualityCheck
outcome and prior failure. The earlier1176-file archive remains valid historical
bytes. Signing plan now selects stable-edge-inputs, not the superseded APKs.
Only fresh scoped private architecture/final approvals may permit its Android11
in-place test delivery. Publication still blocked pending current physical tests.


### Superseding current private candidate

Fresh architecture expert `/root/stable_private_arch` approved only private
verification at2026-10-18T06:42:00Z, tree3c984db2f6ef59e2c6c64f31756e66780d09c4f7,
digest4d1dfd15b09f123bfe7e03d95106c245ba03de516d7a7b6d177b16bc1ee41926.
No signing followed: API30 idle exposed a test-only base InputEvent reflection
lookup absent on that platform. The native phone test now uses MotionEvent's
setter, as the existing production path already does. That content change
invalidated the prior verdict; it will not be reused. API30 idle064315.873918Z
then PASS. Earlier JVM-only parser success does not prove Android ICU compatibility.

API30 fullsplit063619.556262Z PASS246.225s, keyboard064025.856048Z PASS and
API37 fullsplit063944.840294Z PASS222.180s. API37 measured100 injected native DOWN
samples after10warmups: p50=8ms,p95=11ms, rotation0,1920x1200,DPI240. This excludes
rendering/broadcast-await and is not physical-finger latency. UI expert
`/root/stable125_keyboard_ui` approved bounded current API37 monitor Layout/combined
four rotations, phone combined/Layout-off and both rollback modes. Phone Layout-on
capture remains a mid-enter-alpha visual gap, not proven persistent defect;
the later native settled capture is Layout-off and does not close that On gap.
Prior local keyboard renders reuse unchanged MonitorShell source, not a new APK run.

API37 child064327.090506Z FAILED because its pre-run task434 already held Reader
above Parent on phone. Product correctly resumed preserved task state. Architecture
expert `/root/stable125_compat_arch` approved fixture-only test isolation: DEBUG-only
FixtureTaskIsolation supplements package-broad parser with exact Parent/Reader,
sole task/current-user roots; ambiguous/shared/malformed ownership refuses setup.
Re-snapshot exact root/task/display before am stack remove, verify removal before
first product launch; no package force-stop/data clear or later state-reset.
SPEC updated first; two JVM guard cases mapped. API30 child064811.486899Z PASS;
API37 new isolation acceptance remains pending. Release runtime code is unchanged
since producteef; the debug helper changes only the debug APK payload.

Current normal private inputs: product
a5667b09b83952349d564d45c4eae94ea1f3f1b76d72ec3aa4def62fd4c53622,
test97161a7bfb838f7882c6ed28848517bb2b57b297603830044e59bac55c788f5b.
116 exact source/build/runner fingerprints: stable-isolation-inputs/provenance.json.
stable-isolation-build qualityCheck PASS148 JVM cases, zero failure/error/skip;
unchanged tooling remains160pass/one platform skip. Coverage regenerated/inspected.
Verified private stable-verification/archive.json binds409files, SHA
63498362fac39e57b8095f336db9daaa135c7e84b34b772010c08a0b9071106a,
including current inputs/plans, source, completed checks and failures. Prior
1176/52-file bundles remain available. Local-only until owner deletion, never
upload. Current physical full regression remains required before public release.
Fresh architecture and two fresh final reviewers of this superseding snapshot
must approve private re-signing/in-place test install; no old approval is reused.


### Current private verification architecture attestation

Fresh independent reviewer `/root/stable_private_arch_v2`,2026-10-18T06:50:42Z,
APPROVE limited private verification only for0.12.5/code38. Published baseline
v0.12.3/7ce8a42a9a2c2143284630d6d20c477ead5e5b34; taskbase7b1b120.
Inspected pre-attestation treef0ba42a0ebe79b15a5c0a82963c483dffed5df9f,
canonicaldigest400b39c857885528e77bf9f26acc57b89f94e27ee0662f0583288ba4c820d654.

Independently inspected current graph, cumulative runtime/spec/tests, whole-product/
component ownership, affected feature/actual rendered input/session diagrams,
failure/cancellation, legacy compatibility, rollback/teardown, privacy and plans.
No architectural blockers for same-certificate debug re-signing and guarded
Android11 in-place private verification after two fresh final approvals.
Modern recovery remains rejected-DOWN-only with stable owned identities, checked
cancel, whole-contact suppression/no replay; unknown evidence/failed cancel remain
explicit failures. Legacy focus/readiness and window Binder cleanup retain owners,
barriers and failure propagation. Diagnosis/cleanup stays off UI; existing queue/
framework-stall limits remain. Search suppression is local; debug-only fixture
isolation permits only recognized sole roots with immediate identity revalidation.

Verified all hashes/members of1176/52/409-file bundles, latest exact APK hashes,
all116 source fingerprints, byte-identical latest signing/install plans and
host148 cases with zero failures/errors/skips plus successful qualityCheck.
No source/device/build/signing mutations by reviewer; not a final reviewer.
Only this faithful attestation follows that inspected snapshot.

Scope excludes public push/tag, production signing, release dispatch/publication.
Required residual gaps: exact-current physical regression, remaining current API37
verification, bounded visual gaps including phone Layout-on, no approved reference
catalog, physical keyboard/OEM breadth and input-to-photon performance. Retained
failures and emulator8/11ms injected-DOWN figures do not establish physical
acceptance. Product defects or substantive input changes invalidate this verdict.
Final private reviewers must approve full final digest including this attestation;
public release requires completed evidence and fresh release gates later.


### Completed stable candidate device verification (2026-10-18)

This continuation supersedes pending-check statements in the historical private
checkpoints above; those snapshots, failures and limited approvals remain intact.
The private final pair `/root/stable_private_final_a` (06:52:10Z) and
`/root/stable_private_final_b` (06:52:39Z) independently approved tree
`d96d23c600975faa1d13f7662c6a2089d8abd822`, digest
`4b41b4eae35bece3ecd615b7ec808359e5b64d283475e46629fdab5752dad38d`,
for private signing/install/testing only. Guarded same-certificate signature-only
re-signing and Android 11 update followed those approvals. Signed product
`4c249118af37d1d250ab147f0fd91cf50eb8d162883ae0484d9a762c7419057f`
and test `db30394989ac325dc23677ee8faa29de7ad20a9ebf4aaba1bef0fd1a65283486`
have identical non-signature payloads to the retained normal a566/9716 inputs.
The 116 source fingerprints remain unchanged. Original production preferences
are retained for exact restoration before the official update; product UID10760
was preserved, with no uninstall/data clear.

**Android 11 hardware:** Black Shark 4/JoyUI, attached 1920x1200 touchscreen,
Shizuku shell UID2000, current private inputs, run20261018T065333.686894Z:
all eleven suites PASS (input, ui, monitor, keyboard, idle, icon, profiles, child,
catalog, protected, split). Full split298.185s includes four rotations, sizing,
Fit, navigation, fault/rollback and teardown checks. Its injected native DOWN
dispatch distribution, n100 after10warmups, p50=59ms/p95=82ms, excludes rendering
and physical-finger latency. The previous rapid-focus failure is retained; this
is the current successful whole regression, not an assertion-count extrapolation.
Passive split log capture began after suite start and does not cover its beginning.

**Android 16:** the user explicitly accepted emulator verification instead of
reconnecting/retesting the Samsung. Current normal inputs are under test on
DexDroid_API_36 with1920x1200/160 secondary display flags1739 and Shizuku2000.
The Samsung's earlier physical results remain historical and source-bounded,
not current-device verification. Final production delivery still targets both
phones in place, preserving their settings and removing test packages.

**Android 17 diagnosis:** this tested image is release-labelled API37,
`google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.019/15611780:userdebug/dev-keys`,
with codename REL and preview_sdk0. Earlier 'preview' descriptions were incorrect.
Current isolated child065149.398183Z failed a black PixelCopy assertion atrotation1.
Fresh default host renderer child065919.617910Z then aborted with system-server
crash. Its settled native console capture visibly shows the correct red portrait
Parent, so black PixelCopy alone does not establish black physical output.

Read-only architecture diagnosis `/root/stable125_compat_arch` matched the stack
against [AOSP Android17 display compatibility policy](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/wm/AppCompatDisplayCompatPolicy.java):
SelfKillStateMachine queues recovery using a captured destination display without
revalidating existence or catching the rejected activity launch. Retained log:
Back-to-Parent06:59:42.845, task disappearance42.902, idle43.042, Stop43.178,
display4 removed43.313, framework recovery targets absent display43.323,
uncaught SecurityException/system_server fatal43.324. This is a stale framework
recovery callback, not evidence of missing PUBLIC/TRUSTED flags. No arbitrary Stop
delay, obsolete-display retention or swallowed cleanup failure was introduced.

Controlled same-image fresh software renderer (`-gpu swiftshader_indirect`)
child070426.777095Z PASS28.767s; current idle070527.181475Z also PASS.
Both keep normal a566/9716 inputs and secondary1739. The fresh-host failure and
software pass are separate configuration results; no platform/product fix or
universal Android17 support is claimed. Existing source-bound host-renderer passes
include input/split/keyboard/UI/monitor/profiles/catalog/protected/icon; capture,
renderer and teardown instability remain a compatibility limitation under review.

UI reviewer `/root/stable125_keyboard_ui` inspected actual settled
api37-settled-layout-on-render/phone-layout-on.png and explicitly approved readable
On-state controls and opaque panel without the earlier transient ghosting. This
closes that bounded screenshot gap only; no baseline promotion, broad performance
or accessibility approval. Actual current physical renders have separate review.

The final public architecture and two fresh final approvals remain required;
private approvals do not authorize public push, production signing or release.


Current emulator16 all eleven suites PASS using normal a566/9716 inputs:
065655.336377Z input;065727.335169Z keyboard;065822.556084Z child;
065849.419927Z idle;065940.785208Z split;070334.934648Z ui;
070408.792917Z monitor;070609.065744Z profiles;070649.809859Z catalog;
070702.435266Z protected;070753.382843Z icon (all 20261018T prefixes).
This is synthetic emulator secondary-display coverage, not Samsung hardware proof.

Native Android11 Chrome Stop070713.085134Z PASS three cycles, each maintaining
phone controller focus and no owned display for15seconds, no instrumentation or
post-Stop activity launch/focus command. The tool used its diagnostic mismatch
option because installed APK is signature-only re-signed; both exact hashes and
verified payload equality are retained. Initial070644.669375Z attempt failed its
single-view prerequisite because the preceding split suite leaves split enabled;
this is retained. Native Apps > Single setup preceded the successful full check.

UI expert `/root/stable125_keyboard_ui` approved16 current physical11 split images:
phone-layout-on/off,phone-pane-apps; monitor-layout-r0..r3 and combined-r0..r3;
monitor-restored-empty-panes; phone/monitor-mode-rollback-m1/m2. Text and spatial
labels readable, On panel opaque, rotations in bounds, fixed header and scrollable
overflow coherent. No approval of other files, animations, accessibility, hit
regions, interaction execution, performance or golden promotion is inferred.

A user decision is pending on release treatment of the documented Android17
framework crash: mark experimental for0.12.5, or hold publication for a separate
investigation. No public release gate is claimed while that decision is pending.


Completed evidence is privately bundled and member-hash verified:
[1361-file current verification descriptor](../../testresults/0.12.5/stable-completed/archive.json),
SHA256 d44cab381711fb64ba659b36c73251d1c2fbbcb356c13357fced629c5c9d0b4f;
[83-file native Stop descriptor](../../testresults/0.12.5/stable-native-stop/archive.json),
SHA2561745c748e8d2e3aebbbc1efbfba6690e8417a622c20b7c172d2f755f70eea605.
Current116 source fingerprints checked against working bytes during collection;
archive retains normal/private APKs, actual limited gates, source, completed runs,
failed attempts, native renders, software/host configuration and diagnostic logs.
Local storage availability is until owner deletion. No upload; public release
host ZIP remains a separate privacy-guarded CI product. Packaging is not acceptance.

Post-documentation `qualityCheck --no-daemon --no-watch-fs` PASS8.603s (cached JVM
outcomes reused after documentation-only edits); check_documentation, privacy,
coverage check and49 requirement mappings PASS. No test result changed by this
rerun; mapping35partial/9mapped/1deferred/4manual still exposes scenario gaps.

### Stable publication decision and final review scope

On 2026-10-18 the user explicitly approved publication, then full Kotlin migration.
This resolves the preceding pending decision: release 0.12.5/code38 with Android17
explicitly experimental and its known framework crash retained. SPEC, README,
changelog and troubleshooting now state that limitation. No runtime/test inputs
changed after the completed hardware/emulator runs. The migration is a separate
branch and single subsequent release; its approved plan remains an ignored draft
until this stable release is published.

Current public candidate checks/evidence: qualityCheck148 JVM pass, tooling160pass
and one platform skip, requirement/coverage/documentation/privacy validation;
all eleven current Android11 physical and Android16 emulator suites plus three
native Android11 Chrome Stop cycles. Exact configurations, retained failures,
private archive descriptors, bounded visual approvals and performance exclusions
are recorded above. Prior private-only approvals do not approve this public tree.
Fresh release architecture and exactly two fresh final reviewers are required
before commit/push/sign/tag/publication. Personal device archives stay local.

### Public release architecture attestation

Fresh reviewer `/root/stable125_public_arch`,2026-10-18T08:20:07Z:
**APPROVE** public0.12.5/code38 architecture; no blocking findings.
Published baselinev0.12.3/7ce8a42a9a2c2143284630d6d20c477ead5e5b34,
taskbase7b1b1203b51ad8921d9176b7c08dfac624c8982b. Inspected staged
pre-attestation tree46342bd967cf89c1a1c5fc93d31069105a86a720,
canonicaldigest77df8518c85b46eef21354f6241d9acdfb36595165dfbf0ab26317612938b886.

Independently queried current graph and inspected cumulative release changes,
whole-product/component/feature architecture, actual runtime/tests, persistence,
manifest/build, release/privacy/evidence tooling and workflows. Verified the
input/session diagram sources match documentation and inspected their renders.
Service state and shell privilege ownership, phone/monitor parity, generation/
workspace guards, rollback, captured-controller return and runtime identities
remain intact. Modern rejected-DOWN recovery requires stable owned identities and
recognized pending metadata, checked cancellation, whole-contact suppression and
no replay; unknown/ready/cancel failures remain explicit. Legacy existing-task
readiness and direct window Binder acquisition preserve identity and teardown
barriers/failure propagation. No new IPC/schema/dependency/polling service or UI
wait. Monitor IME detachment stays local; debug fixture isolation is guarded and
revalidated. Production guards inspect manifests and defined DEX identities.

Verified all archive/member hashes, exact membership/counts/sizes for1361/83/409
files, all116 current source/build/runner fingerprints, normal/private APK hashes,
duplicate-free members and equal non-signature payloads. Actual records confirm
Android11physical11/11, Android16emulator11/11 and native Chrome Stop3x15seconds.
Retained JUnit148pass/zero fail/error/skip, tooling160pass/one platform skip and
successful qualityCheck inspected. API37 fatal stack/AOSP recovery source is
consistent with documented stale-display framework failure; software child/idle
passes remain separate configuration results. User accepted experimental scope.

Residual gaps: Android17 instability; Samsung hardware retest replaced by accepted
emulator coverage; physical peripheral/OEM breadth, full visual catalog,
accessibility, input-to-photon/frame pacing, exhaustive cleanup/stale-work faults,
existing unbounded generic session queue and framework/Binder stall limits.
Reviewer performed no edits/build/device/sign/release actions and is not a final
reviewer. Only this faithful attestation follows the inspected snapshot. Two fresh
final reviewers must approve its inclusion in the full final digest, then the
protected PR/host/signing/package/report/privacy/publication gates still apply.
Old private-only approvals are stale; personal archives must remain local.
