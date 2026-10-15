# Contributing to DexDroid

Start with [SPEC.md](SPEC.md), [architecture](docs/ARCHITECTURE.md),
[design contract](docs/DESIGN.md), and [quality strategy](docs/QUALITY.md).
This is a Java Android 11+ sideloaded application using Shizuku, not a generic
emulator-only application. The desktop uses privileged shell IPC without root.

## Local setup

Install Android Studio's SDK (platform 35, build tools 35.0.0), platform-tools,
JDK 17+ (Android Studio's bundled JDK 21 works), and Python 3.10+.
Set `JAVA_HOME` and `ANDROID_HOME` for your machine, or set `sdk.dir` in an ignored
`local.properties`. Use the checked-in Gradle wrapper, not a global Gradle.
Windows example from the repository root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat spotlessApply
.\gradlew.bat --no-daemon qualityCheck
python tools/check_requirements.py
python tools/check_documentation.py
python -m unittest discover -s tools/tests -v
```

For Python tooling development, install `tools/requirements-dev.txt`, then run
`python -m ruff check tools` and `python -m ruff format --check tools`.
Use `python -m ruff format tools` to format it. CI enforces both.

On macOS/Linux use `./gradlew` in place of `.\gradlew.bat`. No Python packages
are needed for requirement validation or the device runner. Optional image
comparison needs `python -m pip install -r tools/requirements-visual.txt`.

`qualityCheck` enforces Java/Gradle/XML/text formatting, runs JVM tests, builds the app
and instrumentation APKs, and runs Android lint. Existing warnings are recorded
in `app/lint-baseline.xml`; new findings fail. The baseline is debt, not a clean
bill of health. See [quality strategy](docs/QUALITY.md) before changing it.

EditorConfig provides UTF-8/LF/indent defaults. Spotless is authoritative for Java
(Google Java Format AOSP, 4 spaces) and Gradle; run it before committing. VS Code
can run the checked-in tasks through **Terminal > Run Task**. Android Studio users
should run the same Gradle tasks; generic IDE reformatting can differ.

## Spec-driven changes

1. Write the requested behavior/acceptance in `SPEC.md` first. Create a stable
   `DX-*` entry in `docs/requirements.json`, or update an existing entry. Capture
   edge cases and explicit exclusions. For a larger change copy
   `docs/templates/change.md` to `docs/changes/<short-name>.md`.
   New feature specs use [the Spec Kit-style template](docs/templates/spec.md):
   prioritized stories, Given/When/Then scenarios, requirements, entities,
   measurable outcomes and assumptions. Technical plans stay in architecture/
   change documents. Release-facing changes go in [CHANGELOG](CHANGELOG.md).
2. Implement using shared visual components and authoritative service state.
3. Add/update acceptance/E2E tests after implementation. Use JVM tests for pure
   calculations, instrumentation for UI/input/lifecycle, screenshots for rendered
   review, and measured distributions for performance-sensitive changes.
4. Run host checks, then selected device suites. Review visual artifacts. Record
   measured results and gaps in the change record; update spec verification only
   with actual evidence. Do not count a historical release as a rerun.

## Real-device acceptance

Unlock the phone, connect the physical monitor, start Shizuku and authorize
DexDroid. Ensure the screen stays awake. Use `adb devices -l` to identify it.
Wireless ports can change; `adb mdns services` can discover advertised endpoints.
Do not hard-code a development IP into scripts or checked-in configuration.

```powershell
# Builds must already be current; this installs both APKs and records evidence.
python tools/run_device_checks.py --serial <adb-serial> --suite ui --install
python tools/run_device_checks.py --serial <adb-serial> --suite monitor
python tools/run_device_checks.py --serial <adb-serial> --suite keyboard
# Full sequential regression also includes input, idle, and icon suites.
python tools/run_device_checks.py --serial <adb-serial> --suite all
```

Evidence goes to `testresults/<version>/runs/<UTC-run>/`, including metadata, actual APK
hashes, instrumentation output, logcat, status and available screenshot folders.
Version defaults to the app's literal `versionName`; `--version` can provide a safe
explicit label. See [testresults](testresults/README.md) for retention, archived
release logs/screenshots and provenance. Generated runs are ignored until selected
for retention; raw text logs keep their original bytes/line endings in Git.
ADB can return exit code 0 when instrumentation fails; the runner requires
`INSTRUMENTATION_CODE: -1`, at least one `PASS`, and no failure markers.
No connected device is a failure, never a skipped success. `controller` is the
separate **no-monitor** suite and is intentionally excluded from `all`.

The runner returns to MainActivity on display 0 after running. For a timeout,
inspect logs before retrying. Removing the test APK or force-stopping the target
can terminate/recreate the desktop; coordinate this with live use.

## Review and release

Use the pull-request template even for a direct change: requirement IDs, evidence,
visual review, performance impact and gaps. Keep behavior and mechanical cleanup
reviewable separately. CI does host checks only; physical-monitor tests and
visual approval must be recorded before claiming a device-verified release.
Changing versionName/versionCode or installing on a phone is a release action,
not needed for documentation or formatting alone.
