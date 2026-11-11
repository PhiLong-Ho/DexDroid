# Change: Native monitor keyboard

## Contract

FR-041/DX-IME-001 adds actual Android IME placement for managed apps without a
phone keyboard mirror or typed-content relay. FR-006 retains local shell-search
keys. Preserve input/rotation/Fit/split/lifecycle and privacy contracts. User asks
implementation and is preparing Xiaomi14 Android16 with a monitor. No new release
publication is inferred from this request; final publication gates still apply.

Spec first: [baseline](../../SPEC.md) and [feature](../specs/native-monitor-keyboard.md).
Runtime/device/performance results remain unverified until recorded below.

## Architecture preflight

Impact: shell/window-manager API, per-display policy ownership and cleanup,
primary/secondary helper lifetime, asynchronous status and shared input/geometry.
Fresh expert `/root/native_ime_arch_preflight` is reviewing actual owners and
source-linked diagrams before dependent code changes. Android12+ trusted display
support exists; Android11 native placement is deliberately unavailable, without
new display flags or permission grants. A setter/readback is not rendered acceptance.
No global keyboard/default-IME or developer-option changes are authorized.

Expert `/root/native_ime_arch_preflight`,2026-11-11T15:46:34Z, approves managed-app
native IME with per-display leases and independent optional failure. Follow-up
requires all WM calls, including restoration, on a separate policy worker;
ACTIVE/RETIRING/RELEASED phases, exact display revalidation after blocking reads
and before writes, finite off-main cleanup acknowledgement and unconditional
display release. A blocked worker retains at most one replacement intent. An
already-submitted Binder setter cannot be revoked; cleanup remains uncertain if
the finite deadline expires. This corrected approach was explicitly approved.
No permission grants, new Android11 display flags or physical-host policy changes.
Existing local launcher/name keyboards remain unchanged.

## UI preflight

Fresh expert `/root/native_ime_ui_preflight` reviews complete touch-monitor typing,
shared option location/current values, recovery and local search coexistence.
New main-row controls are excluded. Material defaults/placement remain pending;
backend specification and read-only investigation may continue independently.

Expert `/root/native_ime_ui_preflight` recommends the remembered switch before
Pointer speed in Settings → INPUT, shared current request and separate outcome,
native IME for managed apps only and existing local shell keys. No new main row
or touchpad shrink. Current setting labels distinguish configured policy from
rendered success. Retry is limited to explicit transient failures. Question
R-1794411987-0-31971736551424 auto-defaulted after two minutes and is not human
approval. Required default/upgrade decision was reissued without an auto-default
as R-1794412410-0-80247168958464; dependent default/UI remains pending. The current
Xiaomi native-keyboard trial itself is authorized by the implementation request.

## Test plan

- Pure host policy/lease/coalescing/stale/rollback cases; missing API/permission,
  readback mismatch and partial split outcomes. Preserve concrete requirement checks.
- Separate-UID native editor and actual selected Android keyboard, monitor key
  touches/editor text/selection/delete/Back, four rotations and Fit, both split panes.
- Focused existing keyboard/input/split/lifecycle regression where affected.
- Private synthetic Chrome address-bar smoke on requested Xiaomi when available.
- Full required quality/tooling/requirements/coverage/docs/privacy checks; current
  source/APK bindings, private evidence bundles, rendered review and final reviewers.
- Android11 behavior remains a gap unless its hardware or clearly scoped emulator
  check runs. No universal Android/OEM/keyboard or ordinary-typing latency claim.

## Implementation and acceptance

Backend implemented: a per-display policy worker/lease in each helper, one bounded
app-side dispatch worker and current-owner/serial status. The shared Settings row
can be tested using an explicit stored preference without choosing the pending
new-install/upgrade default. Original unrelated dirty workflow/tooling files remain
outside this change. Development version0.18.0-dev/build46 forces helper protocol
reload and identifies the installed candidate; no production release was requested.

Initial JVM/build iteration passed. Xiaomi physical-monitor attempts are private:

| Run under ignored testresults/0.18.0-dev/runs | Outcome and rerun reason |
| --- | --- |
| 20261111T160648.701527Z | Failed waiting for IME after an incomplete test touchscreen event. Added actual finger pointer properties and contact/focus observations. |
| 20261111T161312.148870Z | Actual IME visible; outer key contact rejected. Added key bounds diagnostics. |
| 20261111T161531.245728Z | Confirmed opening-animation key bounds below1920×1200 viewport. Test now waits for bounded stable visible key bounds; no product focus change. |
| 20261111T161740.741900Z | Real Gboard key edited separate-UID text to `a` at rotation0. Back counter assertion failed; its immediate first-Back observation was insufficient. Replaced custom Back override with ordinary Activity navigation and a post-hide observation. Back remains unverified. |
| 20261111T162230.645640Z | Ordinary editor Activity destroyed after the first product Back while IME was visible. Confirmed keyboard-first failure; no longer merely a counter ambiguity. Standard key metadata comparison is a diagnostic, not acceptance. |

Read-only architecture follow-up approved the test key-bounds correction and
found no evidence of host occlusion. It explicitly did not approve typing across
rotations/split or Back/performance. Policy configured is not rendered success.
All failed runs are retained; later results must bind their own source/APK inputs.

Back investigation: standard `input keyboard -d OWNED_DISPLAY keyevent BACK` also
destroys the editor (20261111T162650.431204Z); guessed event flags are rejected.
The same ordinary fixture on phone display0 retains its Activity after the first
standard Back with IME shown (private phone-back-control). This does not prove a
specific OEM cause. Fresh expert `/root/native_back_arch`,2026-11-11T16:29:49Z,
approves only an isolated debug API36 exact-fixture native-callback diagnostic:
capture display object/task/component/UID/global focus, resolve complete native
animation/callback/finish APIs before start, revalidate before every submission,
cancel/finish in finally, no manufactured focus or fallback key, no blocking
DeskService lock and no effect on product key/relay paths. One primary probe at
a time, fixed optional outcomes and no retry. Product adoption requires separate
evidence/review, input ordering and split ownership/cancellation checks.

Later evidence revises the Back hypothesis: before the native probe submitted any
Back, run20261111T163606.585274Z reports text `a`, editor still alive, but IME hidden;
IME client is DexDroid's physical display2 and global focused display is2. The
keyboard disappears after physical key contact, so a subsequent app navigation
does not establish an independently broken Back implementation. Probe was not
adopted; its owner guard also needs a post-blocking-read check before further use.
The fresh expert approves a narrow API36 configured-native-mode host-focus trial:
lease only FLAG_NOT_FOCUSABLE on the current physical host while no shell menu
owns input, restore before menus and on teardown, never apply to Android11 or an
unavailable/pending policy. Key/button focus changes require explicit split and
hardware regression; final product adoption remains subject to this evidence.

The focus implementation is service-owned and validates the exact current
MonitorShell/window, active managed app and closed/nonanimating shell. The public
flag changes only its leased bit on main and restores at Stop, disable, idle,
menu open and host replacement. Callback probe/RPC removed; original product Back
paths are unchanged. `/root/native_ime_ui_preflight` approves this correction
within the existing authorized continuous-typing flow. It flags native hardware
ownership and actionable closed-shell notices/empty panes as acceptance risks.
No UI appearance, accessibility or peripheral approval is inferred from source review.

Compatibility follow-up `/root/native_back_arch` approves API31+ eligibility for
the same capability/active-app/exact-window/closed-shell focus lease. The public
flag has no API36-specific contract; withholding it while reporting LOCAL on
other eligible versions would create inconsistent behavior. Android11 remains
excluded. API31–35/API37+, Presentation hosts, other OEMs/IMEs and actual Bluetooth
hardware remain unverified, rather than being labelled unsupported without evidence.
ALT_FOCUSABLE_IM was considered but not adopted: it changes Single host key routing
and is unnecessary for the observed correction. AOSP global display changes cancel
display-unspecified held nonpointer keys; display-targeted relay cancellation is
separate. The candidate retains existing relay paths and tests native global keys.

Run20261111T165849.230010Z passes actual consecutive `aa`, Delete, global native
keyboard B, persistent IME and first/second Back at4rotations, plus1920×1080@240 Fit
on1920×1200 output. It then hits the existing prohibition on opening one package
in two panes: two Activity classes in one test APK are not two apps. The split
fixture now uses the existing separate-UID test APK plus a debug-only second
editor in the app APK. This follows the existing two-package fixture boundary;
release artifacts contain neither editor. No product split restriction changes.

Run20261111T170303.782488Z establishes native typing/global B in initial left and
right panes, then fails returning left: WM global focus/input target are left31,
but IME client/token remain right32 and both windows retain own focus. Fresh
`/root/native_back_arch` and UI expert approve a bounded fresh-display trial
omitting OWN_FOCUS only for a captured native-keyboard creation mode. Primary and
secondary must share one immutable service-owned mode; TRUSTED/touch/Android11
flags remain unchanged. Helper protocol/build47 forces reload. No global focus
setting, focus-bounce window, per-key query or text relay. Actual focus/IME
observations decide success; OEM global per-display focus may still prevent it.
This is a diagnostic implementation, not approval of apply-on-start UI semantics.
The working prototype may explicitly enable the preference and restart its empty
test desktop. Default and applying on next desktop start remain material human
decisions; no running user's app is silently recreated to apply them.

Run20261111T164120.942180Z passes rotation0 real key entry, persistent IME,
first-Back hide without Activity destruction, and second-Back ordinary exit.
It then fails a portrait relaunch. The next test explicitly waits for native task
exit plus service occupancy reconciliation before rotating/relaunching, rather
than treating onDestroy as completed Android task removal. Failure remains retained.

Run20261111T164517.563360Z passes focused single-display native typing and ordinary
first/second Back across all four rotations. The expanded consecutive-key check
in164902 fails because the test's text-label matcher selects Gboard's `a` suggestion
instead of its letter key, producing `a `. The matcher now requires the native key
content description, preserving the exact multi-character assertion.

Host tooling:211 cases completed,203passed/8skipped,28.069s. First qualityCheck
fails the new shell WM reflection lint finding (scoped documented PrivateApi
suppression added) and freshly published Gradle8.14.7 availability. Wrapper moves
only within8.14.x with the official binary SHA256 pinned; AGP/Kotlin/JVM/dependency
topology stay unchanged (architecture N/A for this patch maintenance correction).
No lint baseline regeneration or severity suppression. Current version/checksum
source: https://gradle.org/release-checksums/ . Historical tool-version records stay
historical. Full host check will rerun on the final inputs.

## Focus-mode investigation and current result

Own-focus run20261111T172006.140424Z still fails returning to the left editor with
the same ordinary80ms synthetic contact. Standard-focus run20261111T172438.905660Z
passes left/right/left/right typing, global B and persistent IME with identical
contacts. This is evidence for creation-time focus coordination, not a proposed
production delay or retry. Build50 adds static eligibility before flag selection:
API31+, existing INTERNAL_SYSTEM_WINDOW and both policy signatures, with no WM IPC.
Actual API/permission/trust failures precede the apply-next-start status. No new
permission, default keyboard, global focus setting or Back implementation change.

Fresh follow-up expert `/root/native_back_arch` approves this bounded approach
conditional on the human default/application decision and focus/input acceptance.
Both helpers capture the same service-owned request once per desktop start;
recreation cannot reread a changed preference and mix requested focus modes.
No self-Activity launch, task-focus bounce or speculative Back callback probe is
adopted. A proposed explicit menu-focus handoff remains an unimplemented diagnostic.

Run20261111T173333.393210Z passes typing4rotations, Fit, repeated split switching,
native Settings Off/On contacts, held global F12 cancellation and close/retap.
It times out in nested ChildLaunchAcceptance because startActivitySync waits on
the already-open singleTask controller. The combined test now reuses its controller
and streams progress; no product launch behavior changed.

Run20261111T174821.941060Z then reaches phone handoff but incorrectly expects idle
while the new native editor fixture remains underneath. Child regression now starts
on an explicitly fresh test desktop, retaining native creation mode. This setup is
outside the handoff scenario; no in-scenario reparenting/repair was introduced.
Run20261111T175046.749721Z passes the full native typing checks and the nested
existing child/navigation/handoff/Fit/remaining-app/real-exit regression.

Rendered UI expert `/root/native_ime_ui_preflight` inspected174821 phone/monitor
captures: entire row and lower border visible, readable title/status/switch,
consistent monochrome styling and no collision. Approval is scoped to this
On/configured state, portrait phone and landscape split-monitor Settings.
The earlier unimplemented hide-on-menu proposal is reconciled to the existing
modal contract: a dimmed app IME may remain behind the scrim; it receives no menu
contacts. The expert requires outside-key no-clickthrough, local menu keyboard
and close/retap evidence, and does not infer global focus from window flags.

Required human choice reissued as R-1794419209-0-69509750718464: Off initially or
On when supported, with explicit apply-on-next-Stop/Start guidance and no forced
restart. No answer/auto-default is treated as approval. Default remains provisional
Off; trial preference is explicitly enabled by the acceptance test. No release or
push is authorized by this record.

Host check20261111T1751 qualityCheck passes (25s,91tasks); JVM XML287tests,0failures,
0errors,0skipped. Tooling211cases completes203passed/8skipped in21.860s. This predates
the final added outside-key acceptance check; test build/check binding and final
validators will be recorded separately. Baseline lint counts remain historical;
no baseline content changed.

## Immediate modal keyboard ownership trial

The held-key test previously established cancellation only after a real Settings
contact. Its own pre-contact assertion showed the native app still held the key
after programmatic menu opening; focusability alone does not satisfy modal global
keyboard ownership. This remains a concrete gap, not a physical-Bluetooth pass.
The earlier `/root/native_back_arch` preflight permits a bounded Activity-host
self-reorder trial: REORDER_TO_FRONT/NO_ANIMATION without SINGLE_TOP, once after
closed→open input dispatch, exact host/window/task/display/generation/token/serial
guards, unexpected marked duplicate finishing before binding, no intent adoption
or reopening from onNewIntent, cancellation on Close/Stop/replacement and no retry.
Acceptance requires global B not reaching either editor before a menu contact,
held-key cancellation, unchanged host/task and close/retap recovery. Presentation
hosts remain outside this trial. This is not publication or final approach approval.

The trial is now implemented with the public Activity API and no new permission.
Run20261111T182043.529757Z passes the actual owned Window focus assertion at menu
readiness (308ms in this run), unchanged host/task identity, F12 cancellation and
global B isolation before any menu contact. Android hides the app IME when that
menu takes focus. The outside-contact case now accepts hidden or dimmed native IME
states, asserts unchanged text AND contact counts in both apps, then retaps and
types a real native key. Hidden-keyboard runs explicitly exclude exposed-key
coverage rather than claiming that invisible keys were tested.

`/root/native_back_arch` inspected the current source and this evidence and
explicitly approves the owned-Activity approach. This establishes ready-menu
ownership, not atomic focus at the opening gesture. Denied/delayed navigation,
broader stale races, Presentation/OEM breadth and actual Bluetooth remain gaps;
the verdict is not release or final-candidate approval.

Run20261111T181821.363681Z retains a test accessibility-geometry failure: Gboard
exposes duplicate matching descriptions during virtual-node reuse, including a
larger stale/suggestion target alongside the real compact letter key. The scoped
English-Gboard matcher refreshes matching candidates and selects the compact key;
it still injects one physical contact and requires exact resulting editor text.
No production delay, input retry, direct setText or accessibility click fallback.
Earlier intermittent local Hide failure175340 is retained; subsequent full local
keyboard175829 passes without a product Hide change. Its cause remains unconfirmed.

Final full native run20261111T182137.864755Z passes all four rotations, consecutive
native keys/Delete, native Back, Fit, repeated split switching, pre-contact modal
hardware ownership, shared switch touches, outside isolation, child/private-reader
navigation, phone handoff, remaining-app input and real app exit. Current local
keyboard/input reruns and final evidence bindings are recorded in the summary.

## Development handoff

Final local keyboard run20261111T182322.446341Z passes in56.969s; input
run20261111T182420.742643Z passes in33.954s. qualityCheck passes in25s with287 JVM
tests; tooling203passed/8skipped,211total. Current coverage/static/documentation/
privacy guards and Ruff pass. Full source/APK/run bindings, measured boundaries,
retained failures, private verified archive and scoped UI master references are in
the [acceptance summary](../../testresults/0.18.0-dev/native-monitor-keyboard/summary.md).

The development build remains installed with its explicit trial preference On;
the separate acceptance package is removed and the phone controller is open.
No personal apps were launched. No commit, push or release was performed.
The actual required human default/application question remains unanswered; no
recommendation, timeout or expert preference is substituted for that decision.
Dependent default finalization, fresh final reviews and any requested publication
remain pending. Original unrelated workflow/tooling edits were preserved.

## Android 11 hardware follow-up

User connected the Android 11 phone to the monitor and requested testing.
Architecture impact N/A for this test-only follow-up: reuse the existing policy,
input and menu owners; no product interface/lifecycle/permission change. The
existing native-ime Android 11 branch previously checked only unavailability and
launch readiness. Its strengthened acceptance checks both Settings surfaces and
actual On/Off contacts, exact legacy reason, desktop/focus eligibility preservation
and native monitor contact to the separate-UID editor. Observe keyboard windows
and capture actual phone output privately without inferring successful native
monitor placement. Existing local-keyboard/input/legacy-navigation suites cover
the surrounding behavior. Production-code changes, if needed, require a new
architecture preflight. Device access is pending; no new hardware pass is claimed.

Access was restored using the endpoint supplied by the user. Independent checks
confirmed SHARK PRS-H0/API30, unlocked, a1920×1200 external touchscreen and an
existing running Shizuku server. The prepared development APKs were installed in
place; no production source or permission changes were needed.

All four requested suites pass: native-ime6.172s, local keyboard87.797s,
input33.469s and legacy-navigation14.469s. The native-ime check verifies both
controllers' unavailable explanation, actual monitor Off/phone On contacts,
legacy reason, unchanged desktop/focus eligibility and actual app contact.
The immediate phone capture has no visible IME; general phone-keyboard rendering
is not certified by this run. Native monitor placement remains unavailable on
Android11. Local Apps keys and surrounding input/navigation remain separately
verified. Both Settings images were author-inspected, not approved as new goldens.

See the [Android11 summary](../../testresults/0.18.0-dev/android11-keyboard/summary.md)
for exact run/APK/source bindings, selected private archive and scope gaps. The
separate test package was removed and the phone controller reopened. No commit,
push, release, global debugging/default-IME change or personal-app launch occurred.

## Authorized release preparation

The subsequent user directive “Great. release this” authorizes publishing the tested
build as-is. Release0.18.0/code51 therefore retains the implemented Off initial
default, stored choices and manual Stop/Start application behavior. The extra
default review was withdrawn as redundant; no explicit radio answer or approval
by timeout is claimed. Earlier pending-decision paragraphs describe the development
history, superseded by this release directive and preservation of existing behavior.
See the [release candidate record](release-0.18.0.md) for current checks and gates.
