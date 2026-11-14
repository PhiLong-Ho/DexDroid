# Android 11 emulator compatibility — 0.20.0

Taskbar, input, phone UI and monitor suites passed on **2026-11-14 UTC**.
This is Android framework/software-display evidence, not physical Android 11
USB-C, OEM panel, peripheral or touch-to-photon certification.

## Configuration and source

- Device `emulator-5580`, AVD `DexDroid_API_30_AOSP`, Android 11/API30 x86_64.
  Fingerprint: `Android/sdk_phone_x86_64/generic_x86_64:11/RSR1.210722.013.A2/10067904:userdebug/test-keys`.
- Headless emulator, `-gpu swiftshader_indirect`; Google SwiftShader software GLES.
  Primary 1080×2400/420dpi; software secondary display1 1920×1200/160dpi,
  TYPE_VIRTUAL/FLAG_PRESENTATION. Product UI used measured geometry and changed DPI
  during suites; no physical monitor or keyboard/mouse was attached to Android.
- Shizuku 13.6.0.r1086.2650830c server PID2314 was **shell UID2000**;
  product desktop helpers and instrumentation remained non-root. No `adb root`,
  root injection, policy changes or privileged product startup was used.
- Product APK SHA256: `fcd28277a380e54a5acbfc863a81afe803117e623a52fbef61f61bc705a2be92`.
- Initial test APK SHA256: `6a849b5305b5dc260eb2178307693290f2b99d18ff5bf2f8b8cc46ff91ca85f3`.
  Final test APK SHA256: `39e6a1afa1121baee644d6224ef425751f950c9b71d7e755e9059135687a12a6`.
- Runner source context: commit `53bf44a5bb9e04433d143feaa593137ed863f1e8`
  plus the dirty 0.20.0 candidate, recorded verbatim in each `run.json`.
  Parent froze the matching APK pair before execution; product bytes did not change
  for the test-only readiness/focus-fence corrections. These are development APK
  results, not evidence of executing the signed production APK.
- Architectural impact of this verification task: N/A; no app source or product
  contract changed by the verifier. Release architecture approval remains separate.

## Actual outcomes

PASS-line counts below are assertions, not independent scenarios or performance samples.

| Suite | UTC run | Result | Assertions | Seconds | Test APK | Archive |
| --- | --- | --- | ---: | ---: | --- | --- |
| Taskbar, initial | 20261114T031929.319025Z | FAILED: first native contact reached an unsettled window | 229 | 41.141 | initial | [descriptor](taskbar-failed/archive.json) |
| Input | 20261114T032029.989350Z | PASS | 298 | 61.750 | initial | [descriptor](input/archive.json) |
| Phone UI | 20261114T032139.226977Z | PASS | 541 | 42.297 | initial | [descriptor](ui/archive.json) |
| Monitor, initial | 20261114T032240.574382Z | FAILED: pointer visibility after cancellation/resize | 246 | 15.859 | initial | [descriptor](monitor-failed/archive.json) |
| Taskbar, final | 20261114T032608.414795Z | PASS | 309 | 23.719 | final | [descriptor](taskbar/archive.json) |
| Monitor, final | 20261114T032639.451591Z | PASS | 3103 | 132.500 | final | [descriptor](monitor/archive.json) |

Commands were `python tools/run_device_checks.py --adb
C:/Users/longd/AppData/Local/Android/Sdk/platform-tools/adb.exe --serial emulator-5580
--suite SUITE --version 0.20.0 --timeout SECONDS`, using taskbar/input/ui=240 and
monitor=300. Suites ran sequentially; the controller was force-stopped before each
invocation and restored by the runner. The initial installation signature mismatch
and failed separate-data emulator startup are retained locally in ignored setup
notes; only the synthetic emulator's old DexDroid/test packages were backed up and
replaced after explicit parent authorization. Other apps and the AVD were preserved.

The taskbar correction waits for the one actual fixture window before the original
contact. The monitor correction fences the deliberate canceled-contact resize
before pointer assertions. Initial failures remain failures; neither scenario was
repaired by replaying its failed contact. Input/UI results are source-reused because
the product APK and those test paths were unchanged by these two test corrections.

## Rendered review and optional phone-screen control

Verifier `/root/android11_release_check` inspected all 21 final taskbar PNGs and
52 final monitor PNGs in labeled contact sheets, plus eight selected phone UI PNGs,
full-resolution monitor Settings and the phone unavailable-status screenshot.
No visible layout/clipping blocker was found in those states. Scope includes four
monitor rotations, taskbar 120/240/320 UI DPI, 1920×1080 Fit, native app/taskbar
partition and split layout captures. Fine text legibility at every raster size,
physical viewing comfort and performance were not independently certified.
Separate `visual-review.json` records preserve this scope; original `run.json`
values of `unreviewed` were retained unchanged.

ADB screenshot pulls failed with Android11 scoped-storage Permission denied.
Debug `run-as` retrieval also failed. After native suites completed, explicitly
scoped **read-only `adb exec-out su 0 tar`** retrieved completed artifact bytes only;
commands, retrieval identity, tar hashes and capture mtimes are archived separately
from non-root product execution. Input/UI share `submenu-acceptance`; its 26 PNGs
include both runs and are not counted as 26 new captures per suite. Raw errors remain.

A separate [manual capability archive](phone-capability/archive.json) records enabling
Show phone screen button in actual Settings. Feedback became **Could not verify
phone screen control · Retry**; the primary Turn phone screen off action remained
`enabled=false`, while power remained Awake/ON. No OFF command was attempted.
Visibility was restored Off. This establishes safe unavailable feedback on this
emulator; full phone-screen OFF/ON, guard/recovery and watchdog acceptance remain
**unrun/unverified** here. It does not certify a physical Android11 panel.

## Availability and exclusions

Seven per-run archives were packed and independently verified with
`tools/evidence.py pack` / `verify`. Descriptors above bind every member and ZIP
hash. Bundles are private **local-only** under ignored
`artifacts/evidence/android11-020-<suite>-<run>/evidence.zip`, retained until explicit
owner removal; no upload, public URL or expiry guarantee is claimed. Initial failed
native runs are separate bundles. Whole-version runs, AVD/data images, app backups
and unrelated setup data are excluded. Reviewers with this workspace can verify
and inspect these archives before approving the candidate.

Unverified: physical Android11/OEM/USB-C output and panel control, real peripherals,
full split/native-IME/audio/real-app matrices beyond the invoked suite assertions,
real launch latency, frame pacing and touch-to-photon performance. No assertion
count or emulator result is used to claim those checks passed.
