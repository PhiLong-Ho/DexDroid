# Physical Android 11 compatibility — 0.20.0

All four targeted suites passed on the physical Black Shark on **2026-11-14 UTC**.
Phone screen power control could not be verified; its OFF action safely stayed
disabled. Full panel OFF/ON acceptance was therefore not run.

## Candidate, device and execution

- Physical `SHARK PRS-H0`, Android 11/API 30; fingerprint
  `blackshark/PRS-H0/penrose:11/PROS2203060OS00MP5/V12.0.1.0.RBICNBS:user/release-keys`.
  Wireless ADB selected explicitly for every command; HDMI Screen detected as
  EXTERNAL, 1920×1200/60 Hz, reported physical density 213 with logical override 320.
  Phone panel 1080×2400/density 440. The suites changed UI DPI/rotation/Fit as scoped.
- Product APK SHA256: `fcd28277a380e54a5acbfc863a81afe803117e623a52fbef61f61bc705a2be92`.
  Test APK SHA256: `39e6a1afa1121baee644d6224ef425751f950c9b71d7e755e9059135687a12a6`.
  Both match the final frozen development pair; installed hashes are in each run.
- Source context: commit `53bf44a5bb9e04433d143feaa593137ed863f1e8` plus dirty
  release candidate, retained in `run.json`. The verifier changed no app/test source.
  This is development APK execution, not execution of the signed production APK.
- Installed 0.18.0-dev/code 50 upgraded with `install -r`; no uninstall or data clear.
  Existing Shizuku started through its installed arm64 `libshizuku.so` because
  start.sh was absent. Shizuku server PID 7054 and desktop helper ran as
  **shell UID2000**. No root operation, adbd restart or policy change was used.
- Architecture impact of this task: N/A, execution/setup/evidence only. Fresh
  release architecture and final code approvals remain separate.

## Actual results

| Suite | UTC run | Verdict | PASS assertions | Seconds | Archive |
| --- | --- | --- | ---: | ---: | --- |
| Taskbar | 20261114T033931.201878Z | PASS | 309 | 27.125 | [descriptor](taskbar/archive.json) |
| Input | 20261114T034006.192267Z | PASS | 298 | 60.343 | [descriptor](input/archive.json) |
| Phone UI | 20261114T034114.842503Z | PASS | 541 | 41.562 | [descriptor](ui/archive.json) |
| Monitor | 20261114T034312.765614Z | PASS | 3103 | 146.922 | [descriptor](monitor/archive.json) |

These are first-attempt physical passes with the final pair. Counts are assertions,
not independent scenarios or performance samples. Every invocation used
`python tools/run_device_checks.py --adb C:/Users/longd/AppData/Local/Android/Sdk/platform-tools/adb.exe
--serial 192.168.4.24:41017 --suite SUITE --version 0.20.0 --timeout SECONDS`;
timeouts were 240 for taskbar/input/ui and 300 for monitor. Suites ran sequentially,
with an ordinary app force-stop before input/ui/monitor and runner restoration.

## Rendered review, capability and restoration

Verifier `/root/android11_release_check` inspected 21 taskbar, 3 input, 23 phone UI
and 52 monitor PNGs in labeled contact sheets, plus full-resolution phone Settings
with keyboard and phone-screen unavailable status. No visible layout/clipping
blocker was found in the inspected states. Scope includes four monitor rotations,
taskbar UI DPI 120/240/320, 1920×1080 Fit, native app reservation and split layout
captures, menus, guidance, keyboard/search and focus states. Fine text at every
raster scale and physical viewing comfort were not independently certified.
Separate `visual-review.json` records bind image hashes and local file mtimes;
original runner `visual_review: unreviewed` fields remain unchanged.

All four original screenshot pulls succeeded through ordinary ADB shell UID 2000;
no root or fallback retrieval was used. Screenshots, device dumps, app catalog
content and logs remain private local evidence.

The [manual phone-screen capability archive](phone-capability/archive.json) records
actual Settings enablement followed by **Could not verify phone screen control ·
Retry**. The primary Turn phone screen off control had `enabled=false`; panel
state remained Awake/ON. No OFF command was attempted. This is safe unavailable
feedback on this OS/device/build, not proof that its hardware can never support
panel control. Full OFF/ON, guard, recovery and watchdog behavior remain unverified
on physical Android 11; this is not a phone-screen suite pass.

After testing, the two original shared-preference files were restored through
ordinary debug `run-as` while the app was stopped, and verified byte-equal before
controller restart. No other app/data was removed. The controller reopened;
restoration evidence records focus, Awake/ON and shell UID 2000 Shizuku/helper.
The original preference backup stays outside the archived manual evidence.

## Availability and limits

Five separate private per-run archives were packed and independently verified
with `tools/evidence.py pack` and `verify`; all passed ZIP/member hash validation.
The descriptors above locate ignored
`artifacts/evidence/android11-physical-020-<suite>-<run>/evidence.zip` bundles.
They are **local-only**, retained until explicit owner removal; no upload or public
availability is claimed. Only these runs were packaged, excluding whole-version
runs, preference backups, AVD data and unrelated setup. Local reviewers can inspect
the bundles before final approval. No physical native test failed in these runs.

Unverified: full phone-screen power acceptance, human-operated touch/keyboard/mouse
peripherals, broad third-party app compatibility, complete split/native-IME/audio
matrices beyond invoked assertions, launch latency, frame pacing and
physical touch-to-photon performance. Native framework injection on physical
hardware does not certify a human input peripheral or every cable/OEM combination.
