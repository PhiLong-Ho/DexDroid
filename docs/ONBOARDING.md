# Developer onboarding

This guide takes a new maintainer from checkout to a first reviewable change.
Use [coding practice](CODING_PRACTICE.md) for daily implementation and test selection.
Attach [local monitoring](LOCAL_MONITORING.md) to observe long commands independently.
The [README](../README.md) remains the short install/user guide;
[CONTRIBUTING](../CONTRIBUTING.md) is the command reference.

## 1. Understand the two screens

Read the [architecture overview and diagrams](ARCHITECTURE.md), then the owning
[feature spec](specs/README.md). The portrait phone is a controller; the monitor
has independently rotated content. `SessionService` owns session state and input
arbitration. Shizuku shell services own the managed Android displays and inject
input. Apps remain separate Android tasks/UIDs. The shared widgets do not own a
second copy of session state.

| Start here | What to learn |
| --- | --- |
| [SPEC](../SPEC.md) and [requirements inventory](requirements.json) | Observable contract, stable IDs and gaps |
| [DESIGN](DESIGN.md) and [workflow](WORKFLOW.md) | Shared style, navigation and UI expert/user decisions |
| [MainActivity](../app/src/main/java/dev/dexdroid/MainActivity.java) | Phone controller/menu entry points |
| [SessionService](../app/src/main/java/dev/dexdroid/SessionService.java) | State, display lifecycle, input and persistence |
| [MonitorShell](../app/src/main/java/dev/dexdroid/MonitorShell.java) | Monitor overlays, search and edge navigation |
| [LauncherControls](../app/src/main/java/dev/dexdroid/LauncherControls.java), [ControllerStyle](../app/src/main/java/dev/dexdroid/ControllerStyle.java) | Shared actions and appearance |
| [SplitWorkspace](../app/src/main/java/dev/dexdroid/SplitWorkspace.java), [Geometry](../app/src/main/java/dev/dexdroid/Geometry.java) | Two-pane state, rollback and coordinate transforms |
| [DeskService](../app/src/main/java/dev/dexdroid/DeskService.java) | Shell bridge and compatibility boundary |
| [Testing runbook](TESTING.md), [coverage](coverage.md) | Test selection and what has not been verified |

Read the [code map and feature diagram atlas](architecture/README.md) before
choosing files to change. Every task records architecture impact; use the
[expert preflight](WORKFLOW.md#architecture-before-implementation) for possible or
uncertain architectural changes. Add the new feature to both inventories and
maintain its overview/sequence with its source, failure paths and verification gaps.

## 2. Set up and build without a device

Install Git, Android Studio/SDK platform 36 and build-tools 35.0.0, JDK 17+ and
Python 3.10+. Use the repository Gradle wrapper. The bundled Android Studio JDK
also works; do not commit SDK paths or signing material.

```powershell
git clone https://github.com/PhiLong-Ho/DexDroid.git
cd DexDroid
git config core.hooksPath .githooks
git switch -c chore/my-first-change
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
python -m pip install -r tools/requirements-dev.txt
.\gradlew.bat qualityCheck
python tools/check_requirements.py
python tools/coverage.py check
python tools/check_documentation.py
python tools/run_tool_tests.py --output artifacts/onboarding/tool-tests.json
python -m ruff check tools
python -m ruff format --check tools
```

On macOS/Linux use `./gradlew` and your local JDK/SDK paths. Android Studio can
open the checkout root; VS Code tasks expose common checks. Success means the
app/test APKs compile, formatting/lint/JVM/tooling/traceability checks pass. It
does not mean a monitor, third-party app or physical keyboard has been tested.
The existing lint baseline is documented debt, not permission to add warnings.

For a changed Mermaid diagram, use a Mermaid-capable preview or CLI and inspect
its labels/edges. GitHub supports [Mermaid fenced blocks](https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-diagrams).
Optional local rendering with Node 22.12+ (separate from the Android build):

```powershell
npm exec --yes --package @mermaid-js/mermaid-cli@12.0.0 -- mmdc -i docs/ARCHITECTURE.md -o artifacts/architecture.md
```

This may install the renderer/browser dependencies. Keep generated previews under
ignored `artifacts/`; record the renderer/version and reviewed scope. A renderer
pass is not proof the architecture is accurate.

## 3. Optional physical-device setup

Use a dedicated authorized Android 11+ phone, a working external-video cable and
monitor, and running Shizuku with DexDroid authorized. Unlock the phone and select
its actual ADB serial. Wireless addresses/ports can change; `adb mdns services`
can discover them. SDK `platform-tools` contains ADB if it is not on PATH.

Build current APKs before running:

```powershell
adb devices -l
python tools/run_device_checks.py --serial <actual-serial> --suite ui --install
```

Replace the serial and choose suites from [TESTING](TESTING.md). Suites mutate the
desktop, apps and configuration; run them sequentially and restore a usable
controller. Some fixtures assume a 1920x1200 monitor; emulation does not prove
USB-C video, OEM touch association or real peripherals. Keep unavailable hardware
checks explicitly unverified. Debug and release certificates differ; do not
replace a user's installed signing identity casually.

## 4. Complete one small change

1. Find its stable requirement and feature owner. Write expected behavior/scenarios
   in the spec before code. A docs-only first PR is also useful.
2. For an interaction change, obtain the UI expert's proposal and resolve material
   user decisions first. Exact authorized corrections need no repeated approval.
3. Change the owning component; reuse shared state/style/geometry. Maintain the
   affected architecture and feature-flow diagram when its flow changes.
4. Update behavior tests after implementation. Reconcile checks and run
   `python tools/coverage.py update`; inspect both generated coverage files.
5. Run `spotlessApply` for Java/Gradle formatting, then applicable host/device gates.
   Inspect actual rendered states; record performance only with measurements.
6. Follow [EVIDENCE](EVIDENCE.md): raw runs/ZIPs stay ignored; track a concise summary
   and archive descriptor/location. Do not copy hundreds of log/PNG files into Git.
7. Follow [REVIEWS](REVIEWS.md): stage the final tree, obtain two fresh independent
   agent approvals for its digest, record and validate them, then commit. A human
   maintainer using the repository without an agent runtime needs access to two
   actual review agents before publication; do not fabricate JSON approvals.
8. Push the reviewed branch/open a PR only when authorized. Protected `main` needs
   its current `host` check and resolved conversations. Release instructions are
   separate; a successful debug build does not request publication.

## Common setup problems

| Symptom | Next check |
| --- | --- |
| Wrong Java/Gradle compatibility | `java -version`, `JAVA_HOME`, wrapper usage |
| Missing Android platform/build tools | SDK 36/35.0.0 installation, `ANDROID_HOME` or ignored `local.properties` |
| ADB unauthorized/offline | Unlock/accept debugging, inspect actual serial and wireless port |
| Shizuku unavailable after reboot | Complete its supported ADB/wireless startup and authorize DexDroid |
| New lint/format failure | Format with Spotless; fix findings rather than regenerate the baseline |
| Stale coverage after test/helper edit | Reconcile requirement checks, regenerate both coverage files |
| ADB exits zero but suite fails | Read final instrumentation status and retained failure logs |
| Stale review approval | Stage the final candidate and obtain two fresh reviewers again |

For agent operation see [CODEX](CODEX.md). For raw evidence retrieval, retention
and release archives see [EVIDENCE](EVIDENCE.md). First-time developer usability
of this guide remains an explicit manual verification task.

Before any version release, complete the
[release architecture checkpoint](RELEASING.md#release-architecture-review), even
for patches or docs-only. A fresh expert approves inspected inputs before the two
final reviewers; a task-level no-impact note cannot waive this release obligation.
