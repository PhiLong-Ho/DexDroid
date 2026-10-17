# Release acceptance: DexDroid 0.12.1

## Contract and authorization

The user requested current Android 16 testing with its physical monitor and
wireless ADB, followed by publication if the release gates pass and installation
of the production APK on the Android 16 and Android 11 phones.

This release packages the previously implemented transparent status action,
confirmed Stop/controller return, direct split-mode choices and development
workflow/monitoring improvements, plain menu headers and architecture documentation.
Acceptance diagnosis also hardens queued phone Back ownership without changing
its navigation or visual design. Existing FR-001/DX-UI-001, FR-002/DX-PHONE-001,
FR-017/DX-LIFE-002,
FR-031/DX-WINDOWS-001 and NFR-005/DX-RELEASE-001 remain the acceptance owners.

## Verification plan

- Build version 0.12.1/code 34 with the reviewed behavior and lifecycle corrections; run required host,
  requirement, coverage, documentation and formatting/lint gates.
- On the selected Android 16 phone, record device/monitor/APK identity and run
  the eleven monitor-connected suites sequentially. Run native Chrome Stop
  outside instrumentation. Inspect actual phone/monitor captures for the changed
  status/split controls. The no-monitor controller suite requires a separate setup.
- Preserve failures and diagnose them before retrying. Physical keyboard/mouse,
  broader OEM hardware, physical latency and golden-pixel equivalence are separate
  unverified scopes unless actually exercised and recorded.
- Recheck relevant Android 11 release behaviors where its monitor setup permits;
  historical results remain labeled historical. Keep both phones usable afterward.
- Archive current raw evidence externally with checked hashes; track compact
  summaries/descriptors, source/build identity and actual coverage/gaps.
- Freeze the combined candidate relative to protected main, obtain exactly two
  fresh independent approvals, merge through its passing PR and tag the merged
  tree. Release CI must validate signed APK/AAB, reports and evidence before publish.
- Download and verify the published production APK. Preserve existing settings
  before changing signing identities; never silently discard a phone's app data.
  Verify the installed version, package/signature, non-debuggable state and basic
  launch/readiness on each phone. An install failure is not a pass.

## Initial failed attempts

**Initial acceptance blocked publication.** The full run
`20261017T120317.075161Z` passed input, UI, monitor, keyboard, idle, icon and
profiles, then failed the child suite at `rendered remaining-app rotation 0`.
The focused unchanged-source reproduction `20261017T121132.958381Z` fails at
the same assertion. The native task listing places the parent on phone display 0
and InputTestActivity on the owned desktop, while the monitor PixelCopy frame
retains the parent's phone-sized image over part of the remaining app. This is a
rendered acceptance failure; correct task metadata is not a visual pass. Its cause
is unresolved and the assertion has not been weakened.

Independent catalog/protected suites pass; the split suite
`20261017T121350.978026Z` fails `one phone target tap opens app picker` when opening
pane 2. Later split scenarios are unrun. Thus nine distinct Android 16 suites pass
and two fail across these runs, not a successful full-run result. The native
Chrome Stop attempt completes no Stop cycles: its unscoped UI dump selected the
external Chrome window although the phone capture shows DexDroid with Stop.
That native check remains unverified; no focus-restoration claim follows from it.

Host quality checks and 126 tooling cases pass. Android 11's current no-monitor
controller suite passes. Current raw evidence and final scope are indexed under
`testresults/0.12.1/`. Production migration, fresh final approvals, push, protected
PR, tag and publication have not been performed because the user's passing-test
condition is unmet. Both phones retain the debug acceptance build; private
settings backups are available.

## Android 16 evidence compatibility

Samsung Android 16 writes a multi-display warning before PNG bytes when screencap
has no explicit display. The native Chrome Stop driver must resolve the unique
HWC display 0 physical ID, retain its metadata and pass it explicitly to screencap.
It must reject missing/ambiguous primary metadata rather than silently capturing
the external monitor. This is an evidence-tool correction, not an app UI change.

The first Android 16 UI run also exposed a fixture race: rotation readiness became
true before the queued app launch completed. Logs show the Back press/idle rejection
before the app-open result. Before testing exact-one native Back, the fixture must
observe its target Activity on the owned display with a shown/measured window and
completed app launch. No sleep or relaxed Back count substitutes for this state.

The full runner exposed a namespace collision between supervisor logs and the
existing monitor suite. Supervisor evidence uses its own `supervisor/` directory;
the native monitor suite retains `monitor/`. A host full-suite fixture must exercise
all eleven suites and both directories before a release can rely on this runner.

The monitor fixture must snapshot service-owned display state on the main thread.
An Android 16 run read `external` midway through main-thread display discovery,
where it is temporarily null, and crashed between gesture events. Capturing the
settled display on its owning thread prevents that false disconnect; missing or
invalid displays and failed native injection must still fail the check.

An interrupted monitor gesture left synthetic pointer 7 down in Android 16's
injected-stream verifier. The old shell CANCEL used pointer 0 and could not clear
that stream. Test support must track accepted physical touchscreen contacts and
cancel their exact remaining pointer IDs, source, display and down-time before
desktop teardown. Cleanup errors must fail acceptance. A live nonzero-pointer
cancel followed by a fresh accepted tap covers recovery; no global touch reset,
rejected-event retry or relaxed input assertion is used. These Android-specific
fixture fixes do not change the reusable template supervisor.

## Clean-history release continuation

The owner authorized branch cleanup, a current release and production installation
on both phones on 2026-10-17. Source-only changes are transplanted onto sanitized
main `7f36581`; no old commits or withdrawn testresults are imported. Private old
history/source backups remain local. Architecture preflight `/root/release121_preflight`
approved scoped three-way migration while retaining privacy guards, schema-2
withdrawal, current-host archive integrity and every-release architecture review.
Existing DX IDs remain stable; unpublished development NFR labels become 011/012/013
to avoid collision with published privacy NFR-010. Public packaging checks host
input before sealing and archived members again before publication.

Current acceptance must close the Android 16 rendered-handoff/picker blockers and
verify native Stop without driver-induced focus. Keep real-app/device evidence
private. Final release architecture approval precedes the two fresh final code
reviews; clean up archived branches only after preserving their unique work.

Native driver preflight proposes a debug-only shell-UID `ShellUiDump` main that
reads display-0 accessibility windows, preserving installed accessibility services
and disconnecting/joining its thread before returning. It adds no production
manifest, Binder input API, injection, permission adoption or target instrumentation.
Python must reject ambiguous/occluded/non-DexDroid actions before one explicit
phone input tap. Stop remains observed without post-Stop launch/focus assistance.

## Handoff and input fixture diagnosis

Architecture preflight `/root/release121_preflight` approved replacing the
privileged root-move simulation with ordinary exported app-entry launch on display
0. Scoped Samsung diagnostics show forced root migration leaves task 6102's SF
surface under display 361 after its logical move to 0, including after a framework
transaction barrier. Ordinary phone start reparents it under Display 0. The forced
operation remains an unresolved platform diagnostic, not a repaired product path.
Fresh acceptance must preserve task/state, real pixels, input, rotation/Fit and
independent-pane assertions; private readers are never shell-launched.

Split fixture taps now wait for the current dialog's laid-out focused native window
and convert its clipped bounds by the root screen offset. Native app contacts wait
for the actual rendered fixture frame, rather than task metadata. Each contact
remains one event sequence; no retries or synthetic view-click substitution.

The split ownership fixture previously canceled the relayed app stream while leaving shell-injected host pointer 3 held, causing the next pointer-7 DOWN to be rejected on Android 16. Release that exact remaining physical pointer after preserving the one downstream-cancel assertion, and assert no duplicate cancellation. This is fixture stream cleanup; injection rejection remains a failure and is never retried. Architecture impact: existing debug input lifecycle only, no production ownership or API change.

## Native snapshot readiness

Samsung attempts 143036 (SystemUI-only XML) and 143434 (empty window list causing
helper exception/exit before any tap) show asynchronous accessibility discovery.
Source preflight `/root/native_stop_phone_scope` inspected Android 16 AOSP
`AbstractAccessibilityServiceConnection`: setServiceInfo does not await populated
windows, and getWindows can return an empty list. Approved local driver boundary:
read-only same-connection window discovery bounded to two seconds after registering
flags, with separately bounded traversal/cleanup, then action-specific absence
polling in Python. Unsafe/ambiguous/occluded targets and helper failures stay fatal;
only one native tap follows complete cleanup. No helper runs after Stop. No
production session, IPC or app input ownership changes are introduced.

## Phone Back lifecycle blocker

Consolidated run `20261017T143524.967243Z` passes input, then UI crashes on native
Back from Pointer speed after resolution Preview/Revert. The old dialog key
listener synchronously calls menuBack/dismiss/new Dialog; PhoneWindow throws while
inflating framework decor before app content attaches. Exact Samsung framework
cause is unproved; no theme mutation or test inflater override was found.
Architecture preflight `/root/release121_preflight` approved a bounded lifecycle
correction: queue Back after current key dispatch, guard current showing dialog
and live Activity at receipt/execution, coalesce pending Back per owner and capture
IME-hide versus navigation at receipt. No exception swallowing, decor retry or
sleep. UI usability review and focused native regression remain required.

UI expert `/root/menu_back_crash_ui` explicitly approved the queued Back approach, unchanged first-Back IME/parent/root semantics and proposed native/replacement-race checks. No material user decision or layout change is introduced. Native Pointer speed Back repeats three times; deterministic same-main-turn duplicate/Close/replacement/stale-dialog checks supplement that native path. Activity destruction and broader OEM fault timing remain explicit gaps.

Rendered acceptance reviewer `/root/menu_back_crash_ui` approved all 16 phone and 28 monitor captures from corrected-build run `20261017T144007.019810Z` against DESIGN/plain-header proposal, including four-rotation selected monitor states. No clipping/glyph/contrast/header blocker was found. Static captures do not certify timing, accessibility operation, full Fit/DPI/font coverage, settled opacity of every immediate phone capture or an approved golden catalog. No visual baseline was promoted. The reusable public archive guard is also merged into agent-dev-template PR #7 with its own two fresh reviews and passing CI; native Android fixture/dialog changes stay in DexDroid.

Split rendered reviewer `/root/menu_back_crash_ui` separately approved 50 of 77 current split PNGs, covering both launch directions/four rotations, Layout, empty/occupied/mixed backgrounds, Swap/fullscreen, private-reader/handoff and protection confinement. Split Fit preview/input/revert is asserted but no Fit-state screenshot exists; its rendered appearance remains unverified. The stale split-spec sentence was reconciled to centered Touchpad + Info and Back at right. No baseline promotion or physical performance claim.

Host final validation: 140 JVM cases pass; structured tooling reports 155 passed and one Windows symlink/platform skip. A formatter launch stalled before formatting in Gradle native filesystem-watch detection; thread stacks were retained and only the diagnosed owned wrapper/daemon were stopped. Monitored retries with `--no-daemon --no-watch-fs` complete formatting and qualityCheck; no app/tool source bytes changed. Current Android 11 attempt `20261017T145412.081685Z` failed while the secure keyguard was showing (Asleep) and controller restore timed out; waking alone could not unlock it. Owner unlocked the phone; the source-identical rerun `20261017T145640.141526Z` now has keyguard showing=false and a temporary 30-minute screen timeout with its original value retained for restoration. No device setup failure is relabeled as a pass.


## Completed current acceptance and deployment scope

Source-bound final debug APK `bf5e572fa31c3e76636c2c231a41e7577881abf29777e88008a1f419c16ce308`
and test APK `91e3336c8dce791a6f78636956d9fb989dedfa5f885a06b2379451678baba6ab`
pass all eleven sequential suites on Samsung Android 16 (`20261017T144007.019810Z`,
567 seconds) and Black Shark Android 11 (`20261017T145640.141526Z`, 671 seconds).
The Android 11 split suite takes 238.293 seconds, compared with 188.864 on Samsung;
these are suite durations, not controlled input latency measurements. All 127 app/tool
input hashes remain identical across the two runs and final host checks.

Actual UI reviewer `/root/menu_back_crash_ui` approves 16 phone, 28 monitor and 50
of 77 split captures on each OS. Android 11 split verdict is 2026-10-17T15:10:11Z,
selection manifest `d1dff873d6085aa42e98e0bea2b0a376eb443531defa79d919b5899efd6dc41c`.
Fit static renders, universal OEM/app/IME/hardware coverage, Android 17 current
validation, physical latency and approved golden catalog remain unverified.
Verified private archives and descriptors are indexed in
[testresults](../../testresults/0.12.1/README.md); raw phone evidence stays local.

Production deployment is a separate post-publication smoke check, not another
claim about debug acceptance. Before replacing a debug signature, retain verified
original APK and durable settings with unique safe regular-file/directory archive
members, nonempty desktop preferences and recorded Android user/UID/grants.
Use a temporary release-variant APK signed with the production key and debug enabled
solely to restore settings under its new UID. Verify absence of debug-source helpers,
restore without old ownership, compare restored file hashes before launch, then
replace it with the checksum/signature-verified published non-debuggable APK.
Recheck version/signature/UID, observable settings, shell connection and production
launch; final run-as must fail. Private file hashes cannot be reread after production
hardening. Keep original backups for rollback. Reauthorize only DexDroid through
Shizuku's actual permission/manager flow, not a standalone pm grant. Restore only
previous notification/battery exemptions and the recorded temporary phone timeout.
No production install is claimed by this pre-release record.

Obsolete remote branches have exact-ref backups in a verified private sanitized
bundle; old-history/source backups also remain private. Delete only the matching
obsolete refs after reviewed publication, preserving main and historical tags.
The owner authorized this cleanup and production migration; no unrelated work
or device apps are removed.


Native Chrome Stop passes three 15-second cycles per OS on the corrected APK:
Samsung `20261017T144958.866907Z-chrome-stop`, Black Shark
`20261017T151018.648476Z-chrome-stop`. Android 11 setup failures are retained:
split was initially enabled, a manual Close label was wrong, and a later third-cycle
setup found Settings visible after two passed cycles. A fresh three-cycle run passes
without driver/source changes. Restore the prior split layout afterward; current
private settings backups on both phones are verified. Final approval/publication
and production smoke are subsequent steps, not retroactively asserted here.


Initial frozen architecture reviewer `/root/release121_architecture_frozen`
requested changes to stale current-failure prose in split-view and verification-
and-release architecture pages, plus desktop-session diagnosis wording. Reconcile
these to completed acceptance while preserving the unresolved forced-migration
diagnostic and remaining gaps. No source/test/diagram-block changes are required;
prior host/device/render evidence remains source-identical. This changes-needed
review is not release approval; obtain a different fresh expert on the corrected
frozen candidate.


## Release architecture attestation

Actual fresh expert `/root/release121_architecture_corrected` **APPROVE**, no
blocking findings, at `2026-10-17T15:17:12Z`, for 0.12.1/versionCode 34.
Published baseline: `v0.12.0`, commit `12b28ac0297aba3b24ea54b15927fd18d2423dd4`;
sanitized migration base `7f36581e39097e467a4636a204476df49ba29dd8`.
Exact inspected frozen tree BEFORE this attestation:
`0491152972a244da8948a39c87344ba51cb80b26`; canonical digest
`fe6cb498efedb4341bb96da1764b608c386ea6c5c7cdc9a5ce25a61b87cbc462`.

The read-only expert independently inspected cumulative source/tests/workflows,
whole-product and all feature architecture pages, graph-guided source, current
rendered controller-Back/desktop-session/release sequences and actual private
archive/source bindings. Service state, separate shell display ownership, shared
controls, runtime display identities, picker/generation guards, transactional
rollback and queued dialog Back ownership remain consistent. Explicit Stop
serializes secondary/primary cleanup before narrowly validated captured-controller
return; cleanup failure or stale ownership suppresses restoration. Debug snapshots
finish cleanup before input and are excluded from production source sets.

Actual archives/member hashes and contexts, 127 staged app/tool hashes in both
OS and host sidecars, eleven-suite raw outcomes per OS, both native three-cycle
15-second Stop records and 140 successful JVM cases were independently checked.
The expert ran bounded documentation/privacy/coverage checks. Corrected narrative
preserves failed forced privileged migration as a distinct unresolved Samsung
diagnostic; ordinary phone-entry acceptance retains state/pixels/native input.
No new dependency or persisted schema change was found. Public archive/privacy and
source/report bindings precede publication; semantic privacy is not established
by a bounded path/signature guard.

The private verified-backup/release-signed bridge plan and operator were inspected:
owner-independent restoration, prelaunch file hashes, published-byte/UID/signature/
non-debuggable checks and original-build rollback are acceptable. This approves
the approach, not completed signing/installation/Shizuku authorization or smoke.

Residual nonblocking gaps: broader OEM/app/IME/hardware and Android 17 coverage,
during-Fit static appearance, full DPI/font matrix, physical latency, golden
references, exact Samsung decor-crash causality, existing selectively shed but
unbounded generic session queue and hidden OEM framework IPC/transaction behavior.
No new material user decision was identified. Current private evidence remains
accessible via the descriptors in the versioned acceptance index.

Only this faithful attestation follows the inspected tree. Two different fresh
final reviewers must verify that delta and approve the complete final digest;
protected PR/host, release CI and production smoke remain subsequent gates. This
expert is not either final code reviewer. No release/publication/install outcome
is asserted by the attestation.
