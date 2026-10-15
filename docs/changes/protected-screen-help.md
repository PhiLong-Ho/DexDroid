# Protected-screen notice and offline guidance

## Contract (before implementation)

FR-028 / DX-PROTECTED-001 adds an informative notice when the authorized shell
confirms a visible protected window on the active virtual desktop. It detects
FLAG_SECURE, not incognito intent, window text or black pixels. Phone-only/hidden
windows must not trigger it. Unknown metadata retains the last confirmed state.
Both surfaces offer the same offline instructions and Settings retains manual
access. No browser setting, screenshot permission or capture policy is changed.

## Implementation

Build 24 adds a shell-owned ProtectedWindowObserver with one separate worker,
two-second fixed delay, dumpsys service timeout and a bounded metadata parser.
It skips empty desktops and destroys the process/executor on teardown. Occupancy
changes invalidate in-flight results. The one-way AIDL event contains only display
ID and state; SessionService rejects stale session/display events.

ControllerStyle supplies the monochrome notice and Info/Close icons. Monitor touch
and phone-pointer routing own only its bounds while the menu is closed. The phone
status card exposes Info; both menus use ProtectedScreenGuide and existing shared
Settings geometry/scrolling. Dismissal lasts for the episode and does not alter
Chrome configuration. The offline text explains the optional flag, screenshot/
thumbnail consequence, undo, missing/managed flags and compatibility limits.

## Acceptance and scope

[Build-24 evidence](../../testresults/0.11.1/protected-screen-help/README.md)
records APK/source hashes, host/device outcomes, four-rotation captures and retained
failed attempts. Pure tests cover numeric/symbolic flags, visibility/display
filtering, unknown/timeout/permission behavior. Native acceptance toggles actual
FLAG_SECURE on a different-UID fixture through a test-only broadcast while keeping
the same task/window. Physical monitor and phone-pointer Help, outside dismissal,
new episodes, phone-only exclusion and Stop recovery are checked. Fixture classes
never ship in production artifacts.

The early injected app tap did not reach its newly launched fixture; the fixture
now accepts an explicit test control signal for its security flag, independently
of Help input testing. A later run hung in Instrumentation.startActivitySync when
restoring an existing singleTask controller. It was aborted and retained; cleanup
now launches normally and waits for actual focus. Native input assertions were
not removed or retried to conceal failures.

OEM metadata, Android 11/17 device detection, Fit/DPI/font variants, source failure
injection during an occupied session, manual browser modes and a full performance
study remain separate gaps. Human layout approval/pixel baselines remain pending.
No lint baseline change, push or release occurred; publication still requires two
fresh independent reviews of the final candidate.

## Android 11 follow-up

Build 27 handles JoyUI's missing newline before the window-list header without
loosening window-field validation. The added JVM regression checks protected and
ordinary output plus incomplete-header rejection. Current Android 11 outcomes,
legacy input correction, safe instrumentation exit and retained failures are in
[release validation](../../testresults/0.11.1/android11-release-validation/README.md).
The earlier build-24 scope above remains historical.
