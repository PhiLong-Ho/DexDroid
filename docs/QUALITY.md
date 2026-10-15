# Quality strategy and evidence

`SPEC.md` defines behavior. `requirements.json` gives stable IDs, observable
criteria, test mappings, scenario dimensions and remaining gaps. It is a coverage
inventory, not a generated coverage percentage. An assertion count can include
looped checks and says nothing about untested states.

## Gates

| Gate | What it proves | Command/evidence |
| --- | --- | --- |
| Format | Deterministic Java/Gradle and text hygiene | `gradlew spotlessCheck` |
| Compile | App and native test APKs build | `gradlew qualityCheck` |
| Static analysis | No new Android lint findings beyond explicit debt | `gradlew :app:lintDebug` and reports |
| Pure logic | Rotation/Fit inverse mapping and DPI properties on JVM | `gradlew :app:testDebugUnitTest` |
| Tool integrity | Failure detection, device selection, trace links, pixel comparator | `python -m unittest discover -s tools/tests -v` |
| Traceability | Valid IDs, real spec headings, suite files and explicit gaps | `python tools/check_requirements.py` |
| Coverage freshness | Concrete test entries mapped, source fingerprints and report current | `python tools/coverage.py check` |
| Publication review | Two fresh-agent attestations match staged/committed contents | `python tools/review_gate.py check` or `--revision HEAD` |
| Release reporting | Case-derived pass rates, coverage/gaps and version-specific links | `python tools/release_report.py --evidence <run-folder>` after signed packaging |
| Documentation | Local links, Spec Kit sections and preserved historical evidence | `python tools/check_documentation.py` |
| Device behavior | Actual native interactions on selected hardware | `python tools/run_device_checks.py --suite <name>` |
| Visual | Rendered layout agrees with design, reviewed states and optional pixel diff | Screenshots plus review record |
| Performance | Recorded timing/frame distributions under declared conditions | Raw data plus protocol below |

CI runs host gates and retains reports under `testresults/unreleased/runs/ci-<run>/`.
Device runs use `testresults/<version>/runs/<UTC-run>/` and raw
`am instrument -w -r` output so the runner can require Android's final result code
independently of ADB's exit code. Archived release evidence
is indexed in [testresults](../testresults/README.md). Device, visual and performance gates
remain separate until their actual evidence is recorded. `qualityCheck` passing
does not certify a release. A skipped test or unavailable monitor is unverified.

Every release attaches a Markdown report and JSON evidence snapshot. The report
separates requirement mapping counts from actual host test outcomes, includes
skips/expected failures in the pass-rate denominator and lists unrun device,
visual and performance verification. [Release instructions](RELEASING.md) define
generation, source binding, retention and publication gates.

## Scenario selection

[Requirement coverage](coverage.md) expands the map into concrete test entries,
scenario dimensions and known gaps. After **every** test addition/edit/deletion,
reconcile `checks` in `requirements.json`, run `python tools/coverage.py update`
and inspect/commit both generated files. `coverage.py check` rejects changed test
sources (including helpers), stale/missing references, unmapped test entries and
stale generated reports. Python classes expand into individual test methods;
native run/onStart entries contain multiple assertions, not individually mapped
cases. Mapping completeness is distinct from execution or scenario completeness.
Keep run/configuration evidence in the change record and versioned test results;
never promote manual/partial coverage to passed because a source hash matches.
Before publication follow [the two-agent review protocol](REVIEWS.md).

Select dimensions from the requirement map before coding. For affected UI/input,
cover both surfaces where present and all four monitor rotations. Include native
1920x1200, 1280x800 and a mismatched 1920x1080 Fit case when geometry is affected;
exercise supported DPI choices (160/240/320) when layout is affected. Include
touchpad vs direct monitor touch, keyboard shown/hidden, empty/populated/long
content, selected/default/disabled states, and background/Stop/reconnect when
applicable. Phone orientation remains portrait; monitor rotation is independent.

Use a risk-based pairwise subset for independent dimensions; cross every coupled
dimension (for example rotation x Fit x coordinate input). List the combinations
actually tested and explain exclusions. Do not multiply the entire matrix and
claim it ran. Accessibility font scales, cable/Shizuku fault injection, cold
process persistence, third-party app versions and alternative hardware remain
explicit gaps in the current inventory. Expand coverage incrementally per change.

`input`, `ui`, `monitor`, `keyboard`, `idle`, `icon`, `profiles`, `child`, and
`catalog` and `protected` are the ten monitor-connected suites. `controller` specifically expects **no monitor** and must be run in
that separate setup. InputTest gives deterministic observations; Chrome, Firefox,
ReVanced and Tachiyomi supplement it with real-app smoke checks. Record versions.

## Visual regression and review

Instrumentation currently captures phone submenu, monitor menu and icon PNGs.
The device runner archives them and marks visual review **unreviewed**. It does
not declare screenshot success because instrumentation returned success.
Existing [0.10 captures](../testresults/0.10/README.md) are historical;
no reproducible full golden set has yet
been approved. Current coverage gaps remain visible under DX-VISUAL-001.

For a changed screen:

1. Use deterministic input/test fixtures. Fix resolution, DPI, rotation, font
   scale, OS build, IME, locale and app list. Wait for transitions to settle.
2. Inspect screenshots for both surfaces and affected states against `DESIGN.md`.
   Check clipping, line wrap, icon/text legibility, touch targets, panel/header
   bounds, selected/disabled contrast and keyboard/scroll reachability. Native
   geometry and interaction assertions complement this review.
3. For repeatable captures, store approved PNGs and `manifest.json` in
   `tests/visual/<configuration>/`. Store fresh PNGs plus `capture.json` with the
   same `configuration` object in the actual directory. Write reviewer/date and
   source commit in the approved manifest after inspecting it. Do not auto-accept.
4. Run `visual_compare.py`. It fails on missing captures, different configuration
   or dimensions, unapproved baselines and changes beyond declared tolerances.
   Review its self-contained HTML/diff evidence. A passing pixel comparison still
   needs a review note; it cannot prove behavior or animation/frame pacing.

```powershell
python -m pip install -r tools/requirements-visual.txt
python tools/visual_compare.py --baseline tests/visual/<config>/manifest.json `
  --actual testresults/<version>/runs/<run>/<suite>/<capture-directory> `
  --output testresults/<version>/runs/<run>/visual
```

Baseline manifest format (sample values are not an approval):

```json
{
  "approved_by": "reviewer-name",
  "approved_utc": "2026-10-15T00:00:00Z",
  "source_commit": "full-git-commit",
  "configuration": {
    "device": "Black Shark 4", "os_build": "exact fingerprint",
    "resolution": "1920x1200", "dpi": 320, "rotation": 0,
    "font_scale": 1.0, "locale": "en-US", "ime": "exact IME version",
    "fixture": "exact app list and state"
  },
  "images": [
    {"path": "phone-settings.png", "pixel_tolerance": 8,
     "max_changed_ratio": 0.001, "masks": []}
  ]
}
```

`capture.json` contains the same `configuration` object and the capture's source
commit/run. Masks are optional objects with `rect: [left, top, right, bottom]`
(right/bottom exclusive) and a reason, for genuinely dynamic areas such as the
system clock. Review masks/tolerances with the baseline; do not mask the component
under test. Changed-pixel ratio excludes masked pixels. Excessive masks and
baseline updates can hide regressions, so inspect them as part of the diff.

## Performance contract

No controlled end-to-end performance baseline exists yet. Existing await timeouts
prove eventual completion, not instant response. The slow mirrored Android IME
was removed and must not return based on timeout-based tests.

Initial **proposed** regression targets, to confirm after measurement:

| Interaction | Boundary | Proposed p95 |
| --- | --- | --- |
| Local monitor search | Physical key up to visible edited query/results | <=50 ms |
| Menu opening | Recognized edge/action to interactive settled menu | <=150 ms |
| Pointer feedback | Phone input arrival to displayed pointer update | <=50 ms |
| Animations | Rendered frame time at 60 Hz | <=16.7 ms target; report missed frames |

Record physical device/OS/monitor refresh/resolution/DPI, app versions, thermal
state, power state and build variant. Use 10 warmup interactions and at least 100
measured samples per affected path; report p50/p95, sample count and outliers.
Define clock/timing boundaries before collecting. Use Android frame/trace tools
or explicit timestamps for dispatch/render stages; external high-speed video may
be needed for full physical-touch-to-photon latency. Do not conflate IPC duration
with app consumption or screen update. Record raw traces/CSV alongside summaries.
Compare the same fixture to the previous approved baseline, investigate >10%
p95 regressions, and explain tradeoffs. Targets are not verified release promises.
Also stress 30 open/close/rotate/Stop cycles for leaked displays/overlays/stuck
contacts; measure CPU, memory and battery before setting numerical limits.

## Existing lint debt

The initial baseline contains **34 existing findings**, not 34 fixed problems.
Warnings become errors for new code. Target SDK is now 36; the former
`ExpiredTargetSdkVersion` disable has been removed. The baseline is unchanged.
The shell-only legacy fullscreen reflection has a scoped `PrivateApi` suppression
with its Android 11 fallback rationale; current fullscreen roots avoid that API.
Do not regenerate the baseline as a routine response to a failing build.

| Findings | Count | Maintenance action |
| --- | --- | --- |
| SetTextI18n | 14 | Extract strings/format resources when touching status/menu text |
| PrivateApi | 3 | Intentional shell compatibility risk; isolate and test supported ROMs |
| ViewConstructor | 3 | Programmatic views; assess scope rather than adding unused XML constructors |
| BatteryLife | 2 | Explicit background exemption flow; reconsider for store-target work |
| DrawAllocation | 2 | Preallocate drawing objects in a separate tested performance change |
| UnusedResources | 2 | Verify icon/resource callers before removal |
| ClickableViewAccessibility | 2 | Preserve gesture semantics while providing accessible actions |
| LockedOrientationActivity / DiscouragedApi | 1 each | Portrait phone requirement; separate tablet/layout work |
| DataExtractionRules | 1 | Define backup/device-transfer policy for newer Android |
| WakelockTimeout | 1 | Review session cleanup/timeout without breaking background monitor input |
| ObsoleteSdkInt / MonochromeLauncherIcon | 1 each | Reconcile resource qualifiers with minSDK and icon strategy |

Fix one category with behavior checks, remove only resolved baseline entries and
record the change. The full XML remains auditable. Formatting/refactoring itself
must not change user-visible behavior.

The child suite launches the test APK as a normal different-UID application. Its
public parent opens its own non-exported singleTask reader. Check child/parent
display and task identity, state, monitor pixels, input and Back in four rotations;
odd rotations also background the phone controller. This prevents same-UID-only
Input Test coverage from masking Android's untrusted-display launch restrictions.
Fixture components live exclusively in androidTest and are not launcher entries.

The child suite also moves its isolated different-UID task to the phone to model
platform task reuse, checks restored idle pixels/Apps input and explicit relaunch
in four rotations, checks Fit preview/queued relaunch, preserves another remaining
app, and exits the last app. This deterministic shell movement is distinct from
manual real-app phone-launch evidence. Idle acceptance repeats completed phone
background/reopen, same-controller focus, real Settings/Close taps and fresh monitor
input in four rotations. Never use a fixed background sleep as proof of lifecycle
completion, or a menu object's existence as proof its hit target has been measured.

The catalog suite deliberately holds a real background scan pending while opening
both menus, then checks cache/search retention, refresh, retry and dismissal. Its
JSON records native-action-to-first-OnDraw distributions, separate from actual
worker scan durations. These measurements exclude physical input latency,
animation completion and frame pacing. Visual captures settle separately after
the timing boundary. A test reading service-owned display state must do so on the
main thread; reading midway through discovery is not a valid disconnect assertion.

The protected suite changes real FLAG_SECURE on a different-UID test window through
an explicit test-only broadcast; it does not enable Chrome flags or infer protection
from black pixels. It verifies same-window detection, notices, phone/monitor Help,
physical/phone-pointer input, dismissal without click-through, repeated episodes,
phone-only exclusion and Stop recovery. Pure parser tests cover numeric/symbolic
flags, hidden/other-display windows, malformed output, timeout and permission denial.
OEM metadata, query-load distributions and real-browser variants remain separate
compatibility/performance checks. Protection fixtures never ship in release APKs.

Instrumentation exit kills its target process. DeviceChecks now pauses the desktop,
fences its serialized display-worker operations and waits for the actual virtual
display to disappear before finishing. It never recreates a TextureView producer
just before target death. Android 11 JoyUI watchdog evidence exposed a system-server
setDisplaySurface/setAsyncMode stall during the prior cleanup race; preserve that
failed recovery run and rerun device suites after this test-harness change.

Legacy input acceptance records a different-UID reader's semantic tap and checks
that focus is not lost during contact. Injected physical DOWN-to-reader-dispatch
samples include focus settling/relay but exclude rendering. Joined-header parser
coverage reproduces JoyUI output without retaining real window titles or content.
