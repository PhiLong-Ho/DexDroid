# Running DexDroid tests on another device

The reusable entry point is [run_device_checks.py](../tools/run_device_checks.py).
It runs Android instrumentation through ADB. Assertions observe native widgets,
app dispatch, task placement,
rendered pixels and service state. Some scenarios enter the service directly or
use an isolated debug-only shell driver to inject framework events. Hardware
keyboard/mouse/touchpad and human visual tests remain separate.

## What runs where

Use `python tools/run_device_checks.py --serial <serial> --suite diagnostics --install`
for focused diagnostic context, Copy/Share/Clear, selection/navigation and four
monitor rotations/Fit. Sharing is intercepted by native acceptance to inspect the
immutable payload without sending it to another app. Real chooser open/cancel and
the phone display destination are separate bounded smoke checks. This suite does not
change debugging flags; `--suite debugging` uses the existing non-mutating helper.

For the Android 11 native child/Up/Back foreground regression, run
`python tools/run_device_checks.py --serial <serial> --suite legacy-navigation --install`.
This focused suite uses the initial single output without resizing. It supplements
the split suite's two-pane navigation cases; it does not certify split resizing.
Use `--suite split-navigation` for both pane assignments and four rotations without
the broader launcher's menu scenarios. This is additional targeted coverage and
does not replace `--suite split` for a full split regression.

| Layer | Command or pipeline | Device needed | Result |
| --- | --- | --- | --- |
| Format, build, lint, JVM | `gradlew qualityCheck` | No | Gradle reports and JVM XML |
| Tooling/requirements/coverage/docs | Python commands below | No | Host results and explicit mapping gaps |
| Android acceptance/E2E | `tools/run_device_checks.py` | Selected ADB device, usually with monitor | Per-run logs, metadata and available captures |
| Visual comparison/review | `tools/visual_compare.py`, then rendered review | Matching captures/configuration | Diff report plus separate reviewer decision |
| Quality CI | [quality.yml](../.github/workflows/quality.yml), job `host` | GitHub-hosted Linux runner | Host checks and `host-quality-evidence` artifact |
| Release CI | [release.yml](../.github/workflows/release.yml) | No physical Android device | Gated signed APK/AAB and release reports |

Quality CI runs on branch pushes, PRs and manual dispatch; it checks the exact
candidate's two-agent approval record before the build. Local host/device tests
can run before review. Neither workflow currently provisions an Android emulator
or runs the physical-monitor suites. A green CI result alone is not a device pass.

Official builds omit the development input diagnostic, probes and instrumentation
fixtures. Use a debug APK plus its matching androidTest APK for these acceptance
suites. Release APK/AAB packaging checks manifests and SDK-defined classes for
accidental development content; parsing legacy task names does not ship a fixture.

## Prepare the host and build

Follow [CONTRIBUTING](../CONTRIBUTING.md#local-setup) for Android SDK 36,
build-tools 35.0.0, platform-tools, JDK 17+ and Python 3.10+. Run from the repository
root. Windows/PowerShell example:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:PATH = "$env:ANDROID_HOME\platform-tools;$env:PATH"

python -m pip install -r tools/requirements-dev.txt
.\gradlew.bat --no-daemon qualityCheck
if ($LASTEXITCODE -ne 0) { throw 'Fix the host build before device testing' }
python tools/check_requirements.py
python tools/coverage.py check
python tools/check_documentation.py
python -m ruff check tools
python -m ruff format --check tools
python -m unittest discover -s tools/tests -v
```

Run `spotlessApply` when formatting needs fixing, then rerun `qualityCheck`.
Use `./gradlew` on macOS/Linux. `qualityCheck` builds both the app and instrumentation
APKs; the device runner **does not build them**. Rebuild after source changes.
The runner checks installed APK hashes against the local build to reject stale
installations. It does not prove that those local APKs came from the latest source;
the build step and recorded source/commit establish that provenance.

## Native Chrome Stop regression

After installing the current debug APK, unlock the Android 11 phone, attach its
monitor, authorize Shizuku and select Single view. Install Chrome and keep it in
the Apps list (the driver expects the visible `Chrome` entry). Run separately
from instrumentation; do not interact with either screen during the check:

```powershell
python tools/check_stop_focus.py --serial <adb-serial>
```

This performs three real phone UI Chrome launches/Stops, verifies Chrome reached
the owned virtual display, then watches display-0 controller focus and absence of
owned displays for 15 seconds per cycle. Repeated cycles exercise reused Chrome
tasks. No controller launch, menu opening or focus injection occurs after Stop.
Raw focus samples, task/display metadata, logcat, screenshots and installed/local
APK hashes go under `testresults/<version>/runs/<UTC>-chrome-stop/`.

This is a separate physical-device check, outside `run_device_checks.py --suite all`
and host CI. Instrumentation alone cannot establish native background-return
behavior because it can grant background-activity-start privileges. The CLI
leaves the successful phone stopped; after a failure inspect its evidence before
reopening DexDroid. It does not clear Chrome data, force-stop it or close tabs.

## Prepare and select the phone

1. Use an unlocked Android 11+ device in the primary Android user with working
   external-video support, USB/Wi-Fi
   ADB authorization and the intended monitor connected. Keep it awake. Start
   Shizuku and authorize DexDroid. This setup is required even though host checks
   need no device. Installing Shizuku/starting it is a separate setup operation.
2. Run `adb devices -l`. Choose the intended serial, especially when an emulator,
   USB connection and wireless connection are all listed. A serial can be a USB
   identifier, emulator identifier or wireless `host:port`.
3. If USB-C is occupied by the monitor, pair/connect wireless ADB first. In Android
   Developer options, enable Wireless debugging and use its pairing and connection
   addresses. `adb pair HOST:PAIRING_PORT` prompts for the displayed code;
   `adb connect HOST:CONNECTION_PORT` connects afterward. Those ports differ and
   can change. `adb mdns services` can discover advertised endpoints. Never store
   an old development IP/port as a device default in the repository.
4. Install/open the debug app once if it is new to that device; accept DexDroid's
   Shizuku authorization, then confirm desktop readiness before starting tests.

```powershell
adb devices -l
$adbSerial = Read-Host 'Exact authorized serial from adb devices -l'
adb -s $adbSerial install -r app/build/outputs/apk/debug/app-debug.apk
adb -s $adbSerial shell am start -W --display 0 -n dev.dexdroid/.MainActivity
# Complete first-use Shizuku authorization on the phone before continuing.
python tools/run_device_checks.py --serial $adbSerial --suite all --install
```

`--install` installs both APKs with replacement, including the separate test-package
fixtures. Existing release/debug signatures can differ: an update signature error
is a setup failure, not an instruction to delete user app data. Use a matching
build or a test device; do not silently uninstall an existing installation.

Without `--serial`, the runner accepts exactly one authorized ADB device. Explicit
selection is clearer for repeatable tests. It accepts `--adb PATH` if ADB is outside
PATH/SDK discovery, `--timeout SECONDS` for each instrumentation suite (default
900), and a safe `--version LABEL` for evidence grouping. `--version` changes the
output directory label, not the installed app or a release version.

## Fixture and mutation scope

The current input acceptance includes hard-coded physical 1920x1200 coordinates
and expected default desktop dimensions in `DeviceChecks.kt`. The last full
physical setup used a 1920x1200 monitor and 320 UI DPI. Other suites use measured
bounds and/or explicitly preview virtual 1920x1080 and 1280x800 configurations.
Choosing another phone does not automatically generalize those fixture assumptions.
For full reproduction use the same monitor geometry and document its DPI/settings.
For another monitor geometry, identify/adapt the affected tests through the normal
spec/test/coverage workflow and record the gap; do not classify every harness
assumption failure as an app defect or force the phone's system display settings.

Suites rotate/resize the desktop, change preferences for scenarios, open/close/move
fixture tasks, background/reopen the controller and stop sessions. Run sequentially,
with no competing test runner or live desktop use. Scenario cleanup restores what
it explicitly snapshots; do not assume every failed/interrupted test restores all
settings or third-party task state.
Instrumentation snapshots the four saved split-layout keys, clears them for a
repeatable single-view starting fixture and restores them at completion. The split
suite then exercises persistence explicitly through the product controls.
The runner attempts to reopen MainActivity
on display 0 afterward and records restoration errors. Preserve failures before
retrying and check the controller is usable.

## Select suites

```powershell
# Install both freshly built APKs and run just one suite.
python tools/run_device_checks.py --serial $adbSerial --suite split --install
# Reuse the same matching installation for another suite.
python tools/run_device_checks.py --serial $adbSerial --suite monitor
```

| Suite | Main observable scope |
| --- | --- |
| `input` | Phone/monitor contacts, mapping, arbitration, preferences and lifecycle |
| `ui` | Phone menus, portrait/scroll/keyboard layout and shared controls |
| `monitor` | Monitor menus, navigation, modal isolation, rotations and focus boundary |
| `keyboard` | Monitor-local search keyboard and hardware key handling |
| `idle` | Launch/idle visibility, compatibility and session recovery |
| `icon` | Launcher icon/adaptive mask captures |
| `profiles` | Named monitor profiles, confirmation, persistence and shared menus |
| `child` | Different-UID app private child, task handoff, input and Fit |
| `catalog` | Loading/cache/refresh/error states and scoped menu timings |
| `protected` | Secure-window fixture detection, notices/help and safe teardown |
| `split` | Two native apps, resize/rotation/Fit, pane input/keyboard/modal ownership and cleanup |
| `controller` | No-monitor dependency/queued-launch/diagnostic behavior |
| `all` | Eleven sequential suites above, excluding `controller` |

For `controller`, disconnect/disable the external monitor first, keep the phone
unlocked and Shizuku authorized, then run it separately:

```powershell
python tools/run_device_checks.py --serial $adbSerial --suite controller --install
```

The historical debug-only feasibility probe is not a normal `all` suite or a
hardware certification. See [its spec](specs/two-pane-prototype.md).

## Read and retain results

Each invocation prints its directory:

```text
testresults/<version>/runs/<UTC timestamp>/
  run.json
  supervisor/                         # live supervisor logs; separate from monitor suite
  display-before.txt, window-before.txt, app-before.txt
  install-*.txt                       # when --install was used
  <suite>/instrumentation.txt
  <suite>/logcat.txt
  <suite>/<capture-directory>/*.png    # suites that capture images
  restore-controller.txt
```

`run.json` records actual serial, OS/model/fingerprint, Git commit/dirty state,
local/installed APK checks, commands, per-suite outcome/duration and screenshot
pull status. Monitor diagnostics capture the attached output configuration.
Record external keyboard/mouse/touchpad model/connection and third-party app
versions separately; those are not automatically certified by the runner.

A suite passes only when the ADB command succeeds, Android emits final
`INSTRUMENTATION_CODE: -1`, at least one PASS appears and no failure/crash marker
appears. An ADB zero exit code alone is insufficient. The runner stops on its first
failed suite; later suites are **unrun**, not passes. An authorization/install/hash
error or failed controller restoration also fails the invocation.

Inspect `run.json`, instrumentation failures, logcat and screenshot pull results;
missing images cannot support a visual claim. The metadata starts with
`visual_review: unreviewed`: neither instrumentation success nor successful pulls
mean the screenshots look correct. Follow [visual/performance protocols](QUALITY.md)
for rendered review, matching approved baselines and scoped timing distributions.
PASS line counts are assertions, not independent scenario counts. Injected dispatch
latency excludes physical touch-to-photon latency and frame pacing.

Generated raw runs remain ignored. Follow [the evidence policy](EVIDENCE.md):
archive completed runs and relevant failures with raw bytes/per-file hashes intact,
and track only a concise summary, archive descriptor/location and explicit
source/build, commands, settings, outcomes, review scope and exclusions. Do not
force-add new raw trees or ZIPs. Local device bundles are not automatically uploaded
to Actions or a release; local-only/expiry states remain explicit. See the
[withdrawn historical evidence](../testresults/README.md) for an example;
its Android 11 results are historical for your next device/build.

## Emulator and coverage limits

An emulator can test Android framework/UI behavior when its secondary display
and Shizuku setup support the scenario. There is no checked-in one-command AVD
provisioning/device CI workflow. Connecting a physical monitor to the desktop does
not turn it into a phone's USB-C display inside an AVD. Emulation does not verify
USB-C video, physical touchscreen-to-display association, OEM behavior or Bluetooth/
USB peripherals. Record software-display and physical-monitor runs separately.

Use the [feature spec index](specs/README.md) and [requirement coverage](coverage.md)
to choose affected scenarios and read remaining gaps before interpreting results.
Testing on Android 16/17 or another OEM is new evidence, not inherited from the
previous phone. This guide changes no device tests or compatibility claims.

Child and full split suites establish their initial Parent/Reader fixture state
through the shared guarded setup in `DeviceTestSupport`. It removes only captured,
unambiguous roots of that exact synthetic family after fresh root/task/display
revalidation and confirms absence within five seconds. Other tasks/data stay
intact. This happens once before controller/scenario launches; later navigation,
state restoration and phone handoff retain their original assertions. Split
navigation-only does not use this family setup. A failed run remains failed;
setup is never used to repair a scenario midway through it.

Split picker checks observe ready page/dialog, root and search coherently on main.
One captured owner receives one query; action discovery uses the same original
fifteen-second await budget and rejects owner/search replacement. Each poll
returns its current ready row rather than retaining a stale View. This observer
does not replay input or change the deliberate stale-action tests.


## Debug shell snapshot

After building and installing the matching debug APK, leave the controller visible
on display0 and run the independent observer:

```powershell
python tools/check_shell_snapshot.py --serial <adb-serial>
```

This binds the selected device, installed APK SHA256 and authorized shell UID2000,
then invokes `ShellUiDump` once with a 25second host timeout. It requires neither
Shizuku readiness nor Chrome and performs no input, install or settings change.
Only exit0 with bounded display0 controller-window XML passes; failure/timeout
output and independent platform diagnostics are retained under ignored versioned
runs and stay private. A host timeout does not prove remote process cleanup;
inspect the saved process/log evidence before continuing. This debug-only observer
is absent from production and does not substitute for the native Chrome Stop test.

## Debugging-off acceptance

`python tools/run_device_checks.py --serial <serial> --suite debugging --install`
runs non-destructive native confirmation/cancellation, shared rotated menus and
controlled settings-transport scenarios. The double delegates real desktop
cleanup but never changes device debugging flags; this is not proof of real flag
shutdown. Audio worker barriers are exercised with controlled in-flight work.

Actual flag-off tests are separate, deliberate final device checks because ADB
may disconnect. Record all three shell/UI observations, daemon availability and
interrupted/unknown receipts without treating connection loss as success. Test
HSBC/PayLah warning screens privately without login; versions, exact warnings and
OEM/OS scope matter. No bank captures or device dumps enter public artifacts.
A phone must be available for manual Developer options recovery afterward.
