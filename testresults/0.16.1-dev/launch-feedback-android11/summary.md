# Android 11 launch feedback and audio diagnosis

2026-10-27; private physical-device checks on Black Shark4/JoyUI/API30 with a
1920×1200 HDMI monitor. Development build0.16.1-dev/code44, branch
`fix/launch-feedback-android11-audio`, dirty source based on28a58fe.
No release or final candidate approval is claimed.

| Check | Actual outcome |
| --- | --- |
| Settings, Clock, Chrome/about:blank, Calculator, Musicolet | UI picker selection, completion cue and native monitor task verified in Single |
| Settings/Clock split | Each package placed on its own managed display; actual two-app output captured |
| Stop then Musicolet | Starting/pending cues captured on both screens, then real completion |
| Rendered feedback | UI expert approves shown settled phone completion and monitor pending/completion at saved rotation90; phone pending capture is a crossfade |
| Audio capability | Actual UID2000 lacks routing permission; speaker/HDMI detected, required APIs present, preferred read rejected |
| Audio UI | Explicit permission-unavailable explanation; Refresh keeps the page/explanation |
| Musicolet playback | Monitor play/pause2→3→2, advancing position and active AudioFlinger AUX_DIGITAL track; returned to paused |
| Focused instrumentation | Five audio-capability timeouts retained; no native suite pass |
| Host | qualityCheck240/240 JVM passes; tooling210 passes,1 Windows privilege skip; requirements/coverage/docs/privacy/Ruff pass |

App APK SHA256:
`e3ca4af7d902c0c5c3cdcb817e2435a529e85739fa62c6ba7c04090f5cd74def`.
Installed bytes match; signed/unsigned payload equivalence and152 app/build source
hashes are retained. Latest test APK SHA256:
`d3462fd1c55433b9612f07553df03a913719e31c9fb4d1ac7078789fdc712943`.
Test APK removed afterward; development controller and monitor remain usable.

Four rotations/Fit, native contact beneath the notice, settled phone pending,
shortcut first paint, controlled errors/cancellation/expiry, keyboard/accessibility,
large text, other OS/OEM, Bluetooth/hotplug, acoustic sound and selectable-route
override are unverified. Capture-loop timing is not launch latency. System-selected
digital playback is not a DexDroid routing override. See the
[change record](../../../docs/changes/launch-feedback-android11-audio.md)
for decisions, actual commands, failed attempts and interpretation.

Private completed run:
`testresults/0.16.1-dev/runs/20261027T150123.010410Z-manual/`.
The bundle includes current app/build inventory, signing inputs, JVM/tooling results,
UI scripts/captures, read-only probe, playback observations and all five failed
native run records. Early Single Settings/Clock capture names were reused by the
split check; fresh Single records are retained in `device/single-retained/`.
An initial playback tap targeted a stale display ID; its unsuccessful result is
not counted as a product failure. The corrected check resolves the current monitor.

[Archive descriptor](archive.json):266 files, verified ZIP/member hashes,
SHA256 `2b431ba5c63fc1e7813205134783b32eefc13f307b16e6aff33af154ce94c491`.
Availability is **private local-only**, retained until the owner deletes it:
`artifacts/evidence/launch-feedback-android11-private/evidence.zip`.
No upload authorized; never attach this personal device bundle to GitHub.
The descriptor records packaging provenance, not a clean tested commit or passes.

```sh
python tools/evidence.py verify --archive artifacts/evidence/launch-feedback-android11-private/evidence.zip --descriptor testresults/0.16.1-dev/launch-feedback-android11/archive.json
```
