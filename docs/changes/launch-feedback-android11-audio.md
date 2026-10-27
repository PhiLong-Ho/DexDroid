# Change: Launch feedback and Android 11 audio diagnosis

## Scope and decisions

Based on published0.16.0/28a58fe, branch `fix/launch-feedback-android11-audio`.
Development test build0.16.1-dev/code44 distinguishes the candidate in Settings;
no official publication is authorized by this change request.
The user revoked the two-action redesign and requested only launch feedback plus
Android11 device acceptance and diagnosis of an Audio menu showing only System.
No automatic Apps navigation, changed initial screen or retained-picker flow.

UI expert `/root/launch_friction_ui` approved immediate private-entry starting UI,
the existing phone status cue and a top-center noninteractive monitor notice using
shared monochrome style. Preserve menu dismissal on acceptance, main controls and
touchpad geometry. Monitor notice follows the existing host; during replacement
feedback is phone-only until that host returns. No material UI choice remains.

Architecture expert `/root/launch_feedback_arch` approved a separate capacity-one
displayed operation, preserving native request/completion ownership and retaining
callbacks/guards through the pending slot. Superseding displayed feedback must not
cancel earlier accepted native work. Real guarded callbacks settle pending state;
`!busy` and timers never infer success. Native entry content precedes binding;
saved request still precedes opening MainActivity. Existing250ms resize settling,
confirmed cleanup, placement checks and host lifecycle remain unchanged.

Audio expert `/root/android11_audio_arch` found that policy initialization precedes
output discovery. Any permission/API/strategy/read failure returns no endpoints;
the shared picker still paints System. That does not establish missing hardware.
Capability diagnosis and truthful feedback are approved; compatibility repair is
conditional on actual Android11 shell/API observations. Do not grant extra privilege,
invent endpoints or weaken policy ownership/restore checks. Existing capable-device
audio acceptance stays strict; unavailable-path testing needs separate assertions.

## Acceptance and verification plan

- Stable requirement DX-FEEDBACK-001/FR-039: pure operation/pending-record
  tests, then real Single/Split accepted/waiting/failure/Stop/stale behavior.
- Render phone/monitor pending/result states, four monitor rotations and Fit;
  verify a contact beneath the monitor notice reaches the fixture and keyboard/
  edge/menu behavior remains unchanged. Cold entry acknowledgment is a separate
  first-feedback timing boundary from native completion/useful output.
- DX-AUDIO-001/FR-036: inspect real shell permission/API availability independently
  of sink discovery; verify actual supported selection or explicit unsupported
  result. Prefer synthetic media; the user explicitly authorized Musicolet for the
  current real-app check. Raw device evidence remains private.
- Run required host checks/coverage/docs/privacy and targeted native suites. Other
  OS/OEM/full-matrix tests are outside this user-requested Android11 scope.

## Current evidence and exclusions

Android11 Black Shark4/JoyUI is connected to the real1920x1200 HDMI monitor.
The installed test build is0.16.1-dev/code44, updated in place with the matching
release certificate. The debug APK SHA256 is
`e3ca4af7d902c0c5c3cdcb817e2435a529e85739fa62c6ba7c04090f5cd74def`.
The latest instrumentation APK SHA256 is
`d3462fd1c55433b9612f07553df03a913719e31c9fb4d1ac7078789fdc712943`.
Signed/unsigned non-signature payloads match. A152-file app/build source inventory
matches current files; APK identities, inventory and actual outcomes are retained
with private evidence. This is a dirty feature branch based on28a58fe, not a
published or final-reviewed candidate. No release requested. Unrelated edits to
architecture skill/CODING_PRACTICE/default-tooling remain outside this change.

### Host checks

Final `spotlessApply qualityCheck` passes in6s;240 JVM cases have zero failures,
errors or skips. `python tools/run_tool_tests.py --output .../tool-tests-final.json`
runs211 cases in19.021s:210 pass and one Windows symlink-privilege skip.
Requirements54, generated coverage, documentation, privacy-path and Ruff checks
pass. Eight affected Mermaid blocks rendered earlier (`mvqrow9i.log`);
system/launch/audio diagrams were inspected against source. These host/source
checks are separate from device acceptance or latency guarantees.

Source follow-up caught expanded pending feedback omitted from the body and then
potentially clipped by its three-line cap. Current feedback is now first and
uncapped while pending; native assertions check laid-out text within visible
bounds. `/root/launch_feedback_arch` confirms those source blockers resolved.
Audio follow-up required independent MEDIA count/read checks and honest unknown
diagnostic output; `/root/android11_audio_arch` confirms that correction. Neither
source verdict establishes device capability or rendered acceptance.

### Actual UI-driven checks

The user requested Settings, Clock, Chrome, Calculator and Musicolet. A private
bounded ADB/UI script selects actual visible picker rows, observes the completion
cue and independently checks native package/task display placement. All five
launch in Single. Chrome uses a new `about:blank` tab. Settings and Clock also
occupy separate split displays. Stop then opening Musicolet verifies deferred
desktop startup returns to real completion. Navigation/action counts remain
unchanged. Captures include actual pending and completion states at saved
rotation90; they are not public screenshot baselines.

`/root/launch_friction_ui` inspected the actual captures and approves shown settled
phone completion and monitor pending/completion styling. The phone pending image
catches the existing picker/controller crossfade: it establishes a pending cue,
not settled pending readability or an animation defect. No claim of improved
launch speed, first useful app frame, accessibility or native input-through follows
from that review. Capture-loop elapsed times include screenshot/UI dumping overhead
and are not performance measurements. Public synthetic master references are not
updated from these private app screens.

### Audio diagnosis and playback

A fresh standalone debug-only read-only probe under actual shell UID2000/API30
reports `routingPermission=false`, output types1/2/9/18 (including speaker and HDMI),
all required getter/setter/remover/attributes/identity APIs present, one MEDIA
strategy and a preferred-read RemoteException. Permission denial independently
explains the product capability rejection; the read exception alone does not
prove its cause. AudioRouter checks permission before discovery, so System-only
does not mean the hardware endpoints are missing. Refresh retains the visible
"Android did not grant shell audio-routing permission" explanation.

Musicolet monitor play/pause changes its media-session state2→3→2 and advances
playback position. AudioFlinger maps its active client track to I/O29,
`AUDIO_DEVICE_OUT_AUX_DIGITAL`; Android selected the digital/HDMI route.
Playback is returned to paused. This establishes system-routed playback and
monitor input for that control, not audible sound or DexDroid route selection.
An initial local playback check used an obsolete logical display ID and failed;
the corrected check resolves the current physical monitor before each tap. That
failed attempt is not counted as product input failure or a pass.
`/root/android11_audio_arch` independently confirms this scoped interpretation.
No extra privilege, routing workaround, bank/debugging or volume change was made.

### Retained failures and remaining gaps

Five audio-capability instrumentation attempts failed by timeout, retained as
separate ignored runs:20261027T142429.144816Z (90s),143005.201078Z (75s),
143610.214998Z (30s),144805.298584Z (45s),145202.292143Z (45s).
The latest observed progress is controller callback installation; the exact blocked
operation after that progress is not isolated. Earlier helper code did contain
unbounded shell/start waits. A source-reviewed focused helper now posts a display0
start and uses finite acquisition deadlines, while existing suites stay unchanged;
that correction did not produce a completed native run. The direct controller
launch/UI checks work, but do not convert instrumentation timeouts into passes.
No further broad reruns were used to mask this gap.

Current `feedback`/`apps-smoke`/`audio-capability` instrumentation acceptance remains
unverified. Also unverified: settled phone pending, shortcut first paint, controlled
failure/cancel/expiry on-device, notice input-through, four rotations/Fit, keyboard,
large fonts/accessibility, disconnection/helper death and latency distributions.
Other OS/OEM/connection-order/Bluetooth/acoustic tests are outside this narrow run.
The separate capable-device audio suite remains strict and was not run on this
permission-denied device. The separate test APK is removed after checks; the
development controller remains usable with the monitor attached. No publication.

Raw captures/dumps/logs and the verified evidence bundle stay local/private.
See the [evidence summary](../../testresults/0.16.1-dev/launch-feedback-android11/summary.md)
for availability, source binding and archive checksum; never upload its private ZIP.

## Release0.17.0 follow-up

Current Xiaomi Android16 and Android17 simulated-display checks, retained
test-fixture failures, final source/APK bindings and release review gates
are recorded separately in [the release record](release-0.17.0.md).
The development results and gaps above describe their original runs;
they are not silently promoted to new-version passes.
