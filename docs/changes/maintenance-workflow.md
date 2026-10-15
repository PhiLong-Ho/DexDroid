# Maintenance workflow foundation

## Contract

Requirement IDs: DX-MAINT-001, DX-VISUAL-001, DX-PERF-001, plus traceability for
the existing feature families. `SPEC.md` was updated before tooling/code changes.
The change makes development reproducible and reviewable; it does not add app
features or claim complete product coverage.

## Implementation

- Repository instructions and `dexdroid-change` skill implement spec first,
  implementation second, acceptance/E2E afterward. Goal examples are templates;
  no active Goal was created and no global Codex permissions/settings changed.
- Human documentation covers ownership, shared design tokens, setup, scenario
  selection, evidence, visual approval, performance protocol and lint debt.
- Spotless formats Java (AOSP), Gradle, Android XML and text. EditorConfig/LF/
  UTF-8 rules are checked in. Ruff formats/lints Python tooling. VS Code tasks
  and a GitHub Actions host workflow use the same commands.
- Android lint fails new warnings beyond the initial, explicit 34-finding
  baseline. No existing warning was represented as fixed. Target-SDK expiry
  remains the previously disabled check for the Android 11 sideload scope.
- A 23-entry requirement inventory maps spec headings, existing suites, scenario
  dimensions and gaps. Nine are mapped, twelve partial, one manual and one deferred;
  these labels describe coverage inventory, never execution success.
- Device runner verifies installed APK hashes, executes selected suites in order,
  records metadata/logs/screenshots, detects instrumentation failure independent
  of ADB exit code, stops after failure and attempts controller restoration.
- Optional screenshot comparator checks approved configuration and PNGs, emits
  a self-contained HTML/diff report, and leaves human visual approval unreviewed.
  No real baseline was fabricated or auto-approved.

## Validation on 2026-10-15

Environment: Windows, Android Studio JBR 21.0.4, Gradle 8.10.2, AGP 8.8.0,
Python 3.13. Base revision: `5c5d812`; working tree includes this maintenance
change. Mechanical Java/XML changes are isolated in their own commit.

| Check | Result |
| --- | --- |
| `gradlew --no-daemon qualityCheck` | Passed: format, app/test APK compilation, lint and 72 JVM parameterized test cases |
| `python -m unittest discover -s tools/tests -v` | 24 passed, including synthetic runner failures/timeouts and pixel-diff/report cases |
| `python tools/check_requirements.py` | Passed: 23 IDs, spec headings and suite entries resolve; gaps retained |
| `python -m ruff check tools` and `format --check tools` | Passed |
| Skill Creator `quick_validate.py .agents/skills/dexdroid-change` | Passed |
| Intentional formatting/lint probe | Both gates failed as expected; temporary source removed |
| Java token comparison, excluding imports/comments/whitespace | All 20 existing Java files unchanged |
| XML tree/attribute/text comparison | All existing Android XML semantics unchanged |
| Real device runner | Preflight correctly failed: no authorized ADB device available |

Local negative-gate evidence: `artifacts/maintenance/negative-gates.txt` (ignored
artifact, not part of the source tree). Normal build/lint/JVM reports are under
`app/build/reports` and `app/build/test-results`; GitHub CI retains host reports.
Synthetic runner tests use a temporary directory and fake ADB outputs; they are
not physical-device passes.

Physical visual review and device regressions were **not rerun** for this
behavior-preserving maintenance change: the old wireless port refused connection
and ADB/mDNS showed no device. The v0.10 device results in the spec remain
historical. No new APK was installed; versionName/versionCode remain 0.10/15.

## Remaining work

Establish reproducible approved phone/monitor screenshot fixtures on the connected
device; close the explicit requirement-map gaps over subsequent changes; measure
performance distributions before adopting proposed latency budgets as release
limits. Refactor the largest classes in separate behavior-preserving changes with
their own acceptance evidence. Reduce lint baseline categories incrementally.
