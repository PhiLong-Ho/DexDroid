# Legacy app navigation and phone foreground: 0.12.4

## Contract and preflight

User reports Android11 split Clock/Settings Back closes DexDroid. Current production
0.12.3/build36, native Settings/Clock reproduction on the unlocked BlackShark with
1920x1200 physical monitor. Device dumps/captures remain private.

UI expert `/root/split_settings_back_ui` approves exact existing-contract correction:
native app Up/Back follows that pane history, root exit clears only its pane, Clock
remains usable, with phone/menu/Stop behavior unchanged. No new material UI choice.
Architecture expert `/root/split_settings_back_arch` approves removing Recents from
legacy input readiness after isolated causal probes. Preserve shell-only focus,
bounded worker wait, exact display/task and component/UID revalidation, stale-input
drop and existing cancellation/lifecycle owners. No restore/reparent loop, new IPC,
persistence or menu geometry. SPEC updated before implementation.

## Baseline diagnosis

PID6165 stays alive and tasks2365/2363 remain on owned displays1503/1504. Physical
monitor1502; phone0. Settings root/child transitions lead to phone Home, rather than
a crash/session teardown. Initial crash buffer contains historical Oct16 prototype
failures and does not establish the new report's cause. Native Up can work if the
cached component already matches; the next input after a component change triggers
activation. Direct VD child/Up preserves phone Main; first host tap after component
change moves Home, repeated same-component tap preserves Main after restoration.

Private shell-only diagnostic probe checks task/display ownership, then compares
setFocusedTask alone versus startActivityFromRecents(task,null). Focus preserves
Main; Recents alone returns2 and moves Home, while both pane tasks survive. No app
APK/source was changed during diagnosis. The probe initially needed JAVA_HOME set
for d8; retained private build output. Native Clock/Settings content is private,
necessary only for this user-reported reproduction, not public evidence.

## Implementation and verification plan

Focus existing owned tasks for readiness and recheck native window evidence and
identity before committing cache. Preserve launch ownership in explicit app launch
paths. Add synthetic same-task child Up/Back/root exit acceptance, surviving other
pane fresh input, controller foreground and intentional phone Home preservation.
Verify first/new/revealed task, repeats, single/split native input, keyboard,
rotations/Fit and lifecycle with explicit scope. No old release is current proof.

This is a lifecycle/input fix, not the earlier cosmetic release; broader applicable
regression is warranted. Physical production settings/certificate must be preserved;
no uninstall/data clear or unreviewed official signing. Current checks, source-bound
private evidence and eventual production verification remain pending. Android-specific
fix requires no template workflow change. Mandatory release architecture and two
fresh final approvals precede publication/signing.

## Current candidate checks and limits

The product diff is confined to Android 11 readiness and a test observation
accessor. `monitorInputView()` now delegates to `monitorWindow()`, so instrumentation
inspects the attached MonitorActivity rather than an unused Presentation window.
Architecture expert `/root/split_settings_back_arch` approved this local observation
correction after checking that callers are instrumentation only. No host/lifecycle
or UI geometry ownership changed. README quick use remains accurate; no control or
setup change requires a new user-guide instruction. No golden catalog is configured.

`qualityCheck` passes after Spotless, including build/instrumentation compilation,
lint and 140 JVM cases. Tooling has 161 cases: 160 pass and one platform-specific
skip. Requirement/coverage/documentation/privacy and Ruff checks pass. Both updated
input architecture diagrams were rendered and inspected; no timing gain is claimed.

Final focused API 30 emulator run `20261018T025054.353387Z` passes native child entry,
Up/remote Back, root exit, relaunch/first contact, intentional phone backgrounding
and teardown through the real product host/helper. It excludes resizing/full split.
An earlier full-split API 30 run is blocked before the regression by the existing
compositor guard: AOSP's SurfaceFlinger dump lacks the required Composition Display
State header. The guard is not weakened. Physical Android 11 split verification
remains outstanding before treating this fix as device accepted.

API 36 focused split run `20261018T024928.036620Z` passes both pane assignments in
four rotations, native child/Up/Back/root exit, surviving-pane input, empty Apps,
relaunch and foreground/background preservation. Duration 96.332 seconds; overall
runner about 99.55 seconds. It verifies shared split behavior, not the SDK 30 helper.
Its four captures were pulled manually because the new suite lacked the runner's
capture-folder mapping; original metadata remains unchanged. The mapping is now
added. UI expert `/root/split_settings_back_ui` found no pane overlap/crop/divider
blocker, but the restored fixture was still in splash transition. This is limited
visual approval only, not settled app readability. A subsequent run explicitly waits
for existing native-frame pixel evidence before capturing; its outcome is recorded
in the evidence index when complete.

Retained attempts include compile/lint corrections in new test code, a native
window setup race, baseline helper override comparisons and emulator failures.
The API 30 old-helper override is the exact published helper plus new tests; it
passes repeated navigation but drops the final relaunch touch. This is separate
from the proven physical OEM foreground cause, not an emulator reproduction of it.
API 36 broader split attempts initially inspected the wrong host window, then
failed a native phone picker transition. The first focused split attempt injected
before the new native window was drawn and rejected input. The focused driver now
waits for UI idle before contact; no product assertion is removed. These runs are
not full-suite passes. A shell prototype using the candidate helper could not
initialize a MIUI system context; it supplies no candidate device acceptance.

The planned private device-verification build uses the unchanged candidate with
debug signing set to the existing release identity through ignored init script
`.local/settings-back-probe/private-verification.init.gradle`, SHA-256
`74d188914a49e2c4c43b9317c1e4f0e9816163c2a272cac7f79d30f26306864e`.
This preserves the existing UID/settings and permits instrumentation. It must never
be published. Exact-candidate architecture and two independent approvals precede
key use. Physical evidence/new source changes require renewed review before any
subsequent publication or production signing; pending device acceptance is explicit.
No public release is authorized solely by this record. Raw app/device evidence and
all failures remain private local, accessible to reviewers; no personal data uploads.

Settled capture run `20261018T025228.659084Z` passes the same four-rotation split
navigation scope. UI expert `/root/split_settings_back_ui` approved all four actual
settled synthetic monitor renders at `2026-10-18T02:54:13Z`: readable apps, separate
panes/divider and actionable controls, no splash residue/cross-pane overlay/crop.
This is monitor-only bounded visual approval, with no golden promotion or phone,
Fit/font/DPI/accessibility/performance approval. Final API 30 run using the same
APK pair is `20261018T025426.943823Z`; its outcome belongs in the evidence index.

API 30 repeat `20261018T025426.943823Z` caught a post-relaunch input-window
transition before the native child tap. The focused single driver now waits for
accessibility idle before contact, as the split driver already does; no assertion
is removed. Final rerun `20261018T025523.515468Z` passes including teardown. The
product APK is identical across these runs. API36 final split uses the preceding
test APK, differing only in this single-view test's added pre-contact idle wait;
its SplitAcceptance/fixture/product inputs are unchanged. This is compatible
source-bound reuse, not a new full regression or latency claim.

See [private candidate evidence index](../../testresults/0.12.4/README.md). This
pre-device candidate is frozen for independent architecture/code review before
signing the private verification build; physical acceptance and subsequent
production delivery remain pending, not silently certified by emulator counts.

## Pre-device architecture attestation

Fresh read-only expert `/root/navigation124_architecture`, reviewed at
`2026-10-18T02:57:01Z`, explicitly APPROVE for two fresh final reviewers and, after
their approval, same-certificate private debug verification. No architectural
blockers for that bounded step; this does not approve production/public delivery.
Target 0.12.4/code37, published baseline v0.12.3 /
`7ce8a42a9a2c2143284630d6d20c477ead5e5b34`. Exact pre-attestation tree
`c1a2ed62c5dff9c3107e4c9cf0279225a00ce2b3`, canonical digest
`4604a1f00fd3739f3188dabb4ec4a6f1a03ffe96920bece8dd313dfe7e26a56b`.

Inspected cumulative staged diff, system/component/feature diagrams and actual
readiness/input/host source, tests/fixtures, spec/coverage and delivery/privacy
contracts after CRG. Source/diagrams agree: explicit launch owns activation; input
focuses an existing owned task and revalidates task/display/component/UID. Existing
shell synchronization, complete-contact/key rejection, resize invalidation, split
ownership, cancellation and teardown remain intact. No IPC, persistence, task
migration, restore loop, queue or UI-thread wait added. Accessor follows actual
window ownership. Polling budget is not end-to-end latency; existing generic queue
and framework-stall limitations remain.

Expert verified archive ZIP/hash/all 368 members and current source-manifest, final
API30 pass in 8.945s, API36 split scope with disclosed compatible source reuse, host
success and retained failures. Physical Android11 candidate, full-suite/Fit,
hardware keyboard/mouse, lifecycle/OEM/fault breadth and controlled latency remain
unverified. Earlier atlas runs are historical. The private init hash matches and
changes debug signing only; verify package/version/certificate/debuggable status
before in-place install, preserve UID/settings and never publish that artifact.
Only this faithful attestation follows the inspected snapshot. Two different fresh
final reviewers must approve its inclusion. Device evidence or source changes need
renewed exact-content architecture and final review before production signing or
publication. No keys were used during review.

## Private signing configuration correction

The reviewed first private build stayed ordinary debug-signed: its init callback
ran after Android variants had captured signing. The pre-install certificate guard
rejected it; no device update or release-key signing occurred. The existing
approvals remain historical and do not approve this changed delivery input.

The ignored init now registers `androidComponents.finalizeDsl` from the Android
plugin callback before variant creation. New script SHA-256
`bc9d8332429bf2e91d790aa8429275fefd6f3fda4c682f0fc283c7d17acc9072`.
Read-only `signingReport` verifies debug/debugAndroidTest now select release config
and the expected certificate; it does not produce a signed candidate. Product and
test code are unchanged. Renew exact-content architecture/two-agent review before
the corrected private verification build. Earlier source manifest/archive represent
the first transport; current private inputs are in `source-manifest-v2.json`.

## Corrected private-verification architecture attestation

Fresh expert `/root/navigation124_architecture_v2` reviewed at
`2026-10-18T03:01:18Z`: APPROVE for two fresh final reviewers, then corrected
same-certificate private debug verification. No architecture blockers for that
bounded step; production signing/publication remains unapproved. Target
0.12.4/code37, baseline v0.12.3@7ce8a42a9a2c2143284630d6d20c477ead5e5b34.
Inspected exact pre-attestation tree `2946e5364419fa44cfa2b32f3f5f551ecb2de3fd`,
canonical digest `5434ef828d5f2ed5129ffe18bc2bdd2b5cec384793888a58770eb1d7a8a06480`.

After CRG, reviewed cumulative source/spec/tests, system/component/feature diagrams,
input ownership/cancellation, corrected private signing and evidence boundaries.
Owned-task focus and fresh identity checks preserve shell synchronization, complete
contact/key rejection and lifecycle owners; no new IPC/persistence/migration loop
or UI-thread wait. Actual finalizeDsl script hash matches recorded V2; signingReport
selects expected debug/test configuration/certificate, not proof of signing/install.
V2 ZIP hash/size/CRC/all 382 payload hashes and current source-manifest-v2 match;
V1 is historical. Host/API30/API36 outcomes and compatible split reuse verified.
Physical candidate, full-suite/Fit, hardware keyboard/mouse, broader lifecycle/OEM/
fault and latency gaps remain explicit. Preserve preinstall identity/debuggable
guards and UID/settings; never publish private debug artifacts. New evidence or
changed inputs needs renewed review before production delivery. This faithful
attestation is the only delta after the inspected snapshot; no keys/signing/
installation/push occurred during review.

## Physical full-suite follow-up (in progress)

The initial native-navigation focused run passed on physical Android 11, but the
full split suite failed its unchanged root remote Back after Home/controller/Up.
Do not treat the focused pass as full split certification. The ROM omits the
virtual display from current FocusedWindows even when product-identical
SOURCE_KEYBOARD/device -1/flags 0/asynchronous events reach its native app.
Private fullscreen fixture captures show abc becoming abcd while phone Settings
stays open; the post-focus/pre-injection dump still names only the phone. A
fullscreen native root after Home/controller/Up also exits on the exact Back event,
preserving Main and Clock. Repeated setFocusedTask, setFocusedStack and moveTaskToFront
do not repair the report; Recents with an explicit display still backgrounds Main
and was rejected.

Architecture expert `/root/split_settings_back_arch` inspected the probe, dump and
rendered evidence and approved only changing newly-ready admission to the owned
visible native-window predicate. Keyboard-triggered focus, fresh task/component/UID
checks, bounded worker polling, dropped failed sequences and strict focused() for
cancelled-key ownership remain. The full root Back assertion stays intact; the
regression also observes ordinary A delivery after controller return/Up in both
panes. Existing full-suite native EditText/modifier/held-key tests remain.

The second private-signing validation command was incorrectly chained in PowerShell:
a future-dated review timestamp initially failed validation but the build continued.
Both actual approvals existed. Validation was rerun separately and passed before
artifact identity checks and device installation. Subsequent dependent gates/builds
run as separate calls; no failed gate is treated as permission.

## Corrected key admission: private verification checkpoint

`host-key-admission` qualityCheck passed (format/lint/build, 140 JVM tests with no
failures/skips). Tooling rerun: 161 total, 160 pass, one platform-specific skip.
Requirements, coverage, documentation, privacy and Ruff checks passed after final
formatting/coverage regeneration. Current source fingerprints are in
`runs/source-manifest-v3.json`; previous source manifests remain historical.
Updated input/navigation diagrams were rendered and inspected; the UI expert also
inspected all three split overview/sequence renders against Markdown with no
blocking readability/source-text mismatch (not architecture/runtime approval).

Verified local-only private archive `legacy-navigation-0.12.4-key-admission-predevice`
contains 524 files, SHA-256
`5e2d0eb940d6d55e9051a18c608ccfe7742668ff1d6e0efdc903dbd524d40583`.
It retains real-device evidence privately; never upload this bundle. Corrected
physical acceptance, production signing/installation and publication are pending.
This checkpoint seeks only renewed same-certificate private debugging verification
using the unchanged reviewed finalizeDsl override and identity/UID/settings guards.

### Corrected-admission private architecture attestation

Fresh architecture expert `/root/navigation124_key_arch` at
`2026-10-18T03:23:13Z` explicitly APPROVED the bounded same-production-certificate
private debug build/install after two fresh final approvals, preserving UID/settings.
Version 0.12.4/code37; previous release baseline
`7ce8a42a9a2c2143284630d6d20c477ead5e5b34`; inspected pre-attestation tree
`71b9fa75d7df682e53e14b113cd951fd44ebf3b0`, canonical digest
`380f12be41cb307291930c2ee8911b8f76f7dff4d35b599b89d63c1269f993b5`.
The expert inspected cumulative source/spec/tests, system/component/feature diagrams,
relay/readiness/cancellation boundaries and private evidence. No architectural
blockers for this step: owned focus, fresh identity/native-window admission, strict
cancellation ownership, failed-sequence rejection and worker/lifecycle ownership
remain; no new IPC/persistence/queue or UI-thread wait. Archive CRC/all524 hashes
and all106 source-manifest fingerprints independently verified, as were passing
host/tooling outcomes and unchanged signing override. Corrected physical E2E is
pending; retained full-split failure and Fit/hardware/lifecycle/OEM/latency gaps
remain. Enforce package/version/certificate/debuggable guards, in-place UID/settings
preservation, private retention and never publish debug artifacts. Production
signing/publication remains unapproved until renewed review after physical evidence.
Only this faithful attestation follows the inspected snapshot; no build/signing/
installation/push occurred during the review.

## Corrected physical candidate acceptance

Same-certificate private APK identities in `key-admission-private-identity/verified.json`
passed package/version/debuggable/certificate guards and all non-signature payload
hashes matched the host-checked normal debug APKs. Product SHA-256
`c8e16e1b2a109c9c3e8da33216268c1aec4d50a939440fb1767b9cf8bd265812`;
test SHA-256 `6ec20d625f3feb1c5c90b4d15e5cc1ddf9db78f08d7a05abae009d47d8343936`.
Two fresh final agents `/root/navigation124_key_final_a` (03:24:19Z) and
`/root/navigation124_key_final_b` (03:24:21Z), 2026-10-18 UTC, explicitly approved
the private-verification candidate digest
`5f1b27b7b96a426b335a872f1fa51796cc69d8b4fbe4a2b5110f0317c06e8832`.
The approval validator passed separately before signing; package replacement
preserved UID, firstInstallTime and exact preference bytes. These approvals were
private-testing scope only and are historical after this evidence update.

Android11 Black Shark, wireless ADB, physical 1920?1200 monitor, 320 UI DPI:

- `20261018T032506.459947Z`: full `split` PASS, 289.804 seconds including runner
  collection/restoration. The unchanged root Back assertion and ordinary A after
  Home/controller/Up pass in both panes. Native EditText shifted input, captured
  key target, phone-menu held-modifier cancellation/flags/fresh timestamp, touch
  ownership, four rotations, functional Fit/resolution/DPI preview, task handoff,
  protected fixture, explicit Stop/restart, stale helper and picker ownership pass.
- Input-dispatch measurement: 10 warmups, 100 injected physical-host DOWN events
  to different-UID dispatchTouchEvent, p50 57 ms, p95 77 ms. This excludes rendering,
  broadcast/await time and physical finger latency; no touch-to-photon claim.
- `20261018T033011.556307Z`: `split-navigation` PASS, both panes in all four
  rotations with child/Up/Back/root exit, ordinary letters after controller return,
  other-pane input, display identity retention, empty Apps and relaunch.
- `/root/split_settings_back_ui` approved those four settled synthetic monitor
  frames at 2026-10-18T03:31:58Z. No occupied-content idle overlay, splash residue or
  actionable-control clipping; known fixture title/instruction wrapping/clipping
  stays fixture-only. No golden promotion or phone/Fit/font/DPI/accessibility/
  performance approval. Production layout/style is unchanged.
- `clock-settings-corrected/verified.json`: real Clock and Settings launched via
  product Apps picker. Three About phone/native Up cycles preserve Main and Clock;
  monitor child entry while phone Home respects backgrounding; controller return/Up
  works; product Back exits only Settings root and keeps Clock/Main live. No alarm
  or settings values changed. All real-app evidence remains private local.

Current app/test sources still match `source-manifest-v3.json`. Earlier failures
remain retained; earlier emulator outputs are not current corrected-binary claims.
No rerun of all eleven unrelated suites: targeted full split and four-rotation
navigation cover this input/navigation fix. Current Android16/17 binaries, physical
Bluetooth typing/shortcuts, fault injection, frame pacing and touch-to-photon remain
gaps. README quick guide was reviewed; no controls/setup/user steps changed, so
no edit was needed. This Android-specific fix requires no template workflow change.

Production signing/in-place install and protected-branch PR are the next delivery
steps after renewed architecture and two fresh final approvals. No public release
tag/dispatch/publication is requested by this bug report; no debug artifact will
be published. Restore the private pre-test preferences, stop managed displays
before replacement, preserve UID/settings, verify non-debuggable production
package/certificate/version and remove `dev.dexdroid.test`/input fixtures. Delivery
smoke results will be retained externally without changing this frozen source
checkpoint; failures require reopening the candidate.

Final pre-delivery private evidence archive: 666 files, SHA-256
`079c691e15aae63a0112b3654c01140db53d33620e85bef5ec492d6b26929775`,
`artifacts/evidence/legacy-navigation-0.12.4-accepted/evidence.zip`; tracked
[test summary/descriptor](../../testresults/0.12.4/README.md). Local-only reviewer
access and owner-controlled private retention; no upload/publication authorized.

## Production delivery architecture attestation

Fresh architecture expert `/root/navigation124_delivery_arch` at
`2026-10-18T03:34:54Z` explicitly APPROVED local production APK/AAB signing, guarded
Android11 in-place installation preserving preferences, and protected PR delivery
after two fresh final approvals and required host success. No public release/tag/
dispatch/publication is authorized by this verdict. Version 0.12.4/code37; baseline
v0.12.3 / `7ce8a42a9a2c2143284630d6d20c477ead5e5b34`; exact inspected
pre-attestation tree `14b1a9d0a480d1a1c95ce7a9d5fe74ba2b7b8fb7`, digest
`00dae7584e2bcdb2f3a5e0ef0074054132a7dd968f991643cf23c4a50f6dc9d5`.

After CRG consultation the expert inspected cumulative product/test/spec changes,
system/component/feature diagrams, readiness/injection/cancellation, host/split
lifecycle and privacy/delivery contracts. No blockers: explicit launch retains
activation ownership; focus applies to existing owned tasks; fresh identity/native
window checks gate readiness; strict cancelled-key ownership and complete-sequence
rejection remain. Synchronization, resize/generation guards, teardown and pane
ownership stay intact; no new IPC/persistence/restore loop/queue/privilege boundary
or UI-thread wait. Observation follows the actual host window. Independently
verified accepted archive SHA/CRC/all666 payload hashes and all106 source-manifest
fingerprints, identical corrected physical run APK identities, successful host,
full split/four-rotation results and real Clock/Settings verification.

Residual nonblocking gaps: current Android16/17, broader OEM/physical Bluetooth,
stale-work fault breadth, Fit/font/DPI/accessibility appearance, frame pacing and
touch-to-photon. Generic unbounded session queue/framework-query stalls remain
unchanged; worker polling budget is not an end-to-end latency guarantee. Enforce
production package/version/certificate/non-debuggable/development-content guards;
stop displays, restore pre-test preferences, preserve UID/settings, remove test
fixtures and keep delivery smoke private. Failures/changed inputs reopen review.
Only this faithful attestation follows the inspected snapshot; the expert did not
sign or mutate devices.
