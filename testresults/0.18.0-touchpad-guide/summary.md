# Illustrated touchpad guide

2026-11-13 Singapore-time development check (raw captures UTC 2026-11-12), physical
Xiaomi 14 `23127PN0CG`, Android 16/API 36, HyperOS `OS3.0.305.0.WNCMIXM`, portrait
phone at its existing font/density with 1920×1200 monitor attached. Installed
0.18.0/build 51 Debug in place; user data preserved. Dirty branch
`feature/menu-readability`, baseline `c05649ce804f077700db50676827b48a812ac321`.
Archive source hashes identify the tested inputs, not that baseline alone.

| Check | Actual outcome |
| --- | --- |
| Final product `spotlessApply qualityCheck` | Pass, 12 s; 96 tasks, 25 executed/71 up-to-date |
| After test-only rendered-layout readiness guard | Pass, 6 s; 96 tasks, 14 executed/82 up-to-date; unchanged product APK |
| JVM | 295 passed, zero failures/skips |
| Tooling | 203 passed, eight Windows skips; 211 total in 18.137 s |
| Manual actual phone UI | Content, Close/48 dp bounds, native Back, outside dismissal and reopen passed |
| Native instrumentation | Not run; helper absent after prior Xiaomi installation rejections, not retried here |

Combined host outcomes are 498 passed/eight skipped, not 506 passes. Existing
Gradle/future-Kotlin warnings remain; no lint-baseline change. Native acceptance
compiles, but compilation and manual UI checks are not instrumentation passes.

Installed product SHA-256:
`780b7b2112d0b4eced4d74b3b5009952bd0c87cdc9554e0fae2d55f9f071b1ce`.
The installed base APK hash matches the final signed app. Development signatures
use the existing certificate; non-signature payload equality was checked. Final
host rerun changes only a native test wait, not product behavior. The final compiled
test APK hash and all source/build inputs are in `20261113-review`.

Manual check uses normal phone Info, Close and Android `input -d 0` events; UI
hierarchies confirm all five gesture labels and three brief tips. Native Close
bounds are 144×144 pixels at density 3 (48 dp). Back returns to the controller.
An outside tap over controller status dismisses help without toggling that status;
help reopens. The temporary hierarchy was removed, and the guide is left open for
user review. No personal app was opened. Exact commands/observed coordinates are
retained in the manual run; rediscover coordinates for another device.

Author inspected the actual portrait capture: white gesture sketches beside
short native labels, separated tips and plain Close, with all content fitting the
normal viewport. [Current reference](../../docs/ui-references/touchpad-guide.json)
records its source/build/capture hashes. Two changed controller diagrams were
rendered/inspected. The live index may advance in later releases; this run's
original captures/hashes remain in its sealed archive. An initial note-delimiter
parse error was fixed and retained.
There is no human pixel-golden approval, measured performance, small-screen or
enlarged-font matrix, TalkBack or other OS/OEM verification. Input-event-count
isolation is in compiled acceptance only. Monitor controls are unchanged; no
four-rotation/Fit rerun or full regression is claimed.

[Change record](../../docs/changes/touchpad-guide.md) records actual UI expert
identity and local architectural N/A. No new release, commit or push is requested.
[Archive descriptor](archive.json) points to verified ignored **local-only** raw
evidence, retained until separately removed. No logs/images/ZIPs are uploaded or
newly committed. Hash verification establishes byte consistency, not correctness.

```powershell
./gradlew.bat spotlessApply qualityCheck
python tools/run_tool_tests.py --output testresults/0.18.0-touchpad-guide/runs/20261113-host/host/tool-tests.json
python tools/coverage.py update
python tools/coverage.py check
python tools/check_requirements.py
python tools/check_documentation.py
python tools/check_privacy.py
python tools/evidence.py verify --archive artifacts/evidence/touchpad-guide-20261113-final/evidence.zip --descriptor testresults/0.18.0-touchpad-guide/archive.json
```

Private manual scripts, preflight, raw outcomes, signing-input hashes, JUnit XML
and diagram sources/renders are retained in the bundle. The current host run
contains no instrumented device-suite result. Prior helper-install failures belong
to [the preceding pointer change](../0.18.0-pointer-idle/summary.md).
