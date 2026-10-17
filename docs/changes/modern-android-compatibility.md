# Change: Android 16/17 compatibility

## Contract before implementation

Baseline FR-023 / DX-COMPAT-001 and the
[feature specification](../features/modern-android-compatibility.md) define visible
launches, modern target/runtime behavior and emulator evidence boundaries.

## Findings and implementation

Android 16 reproduced the reported idle overlay: the app launched, but the old
Stack-only parser rejected the modern RootTask placement report. Session state
never confirmed a visible app, leaving the idle layer covering its pixels until
Controls opened. `DisplayTasks` now parses both formats, preserves display/user
and task identity, and recognizes confirmed fullscreen configuration. It avoids
calling the removed `setTaskWindowingMode` on already-fullscreen roots; uncertain
placement still fails explicitly and shared-root movement remains guarded.

Android 17 additionally removed `InputManager.getInstance`. The shell bridge now
selects `InputManagerGlobal` where present with the Android 11 singleton fallback.
Monitor hardware Back is handled explicitly on key release, so it closes a menu
once and reaches the app only when the menu is closed.

The target/compile SDK moves from 32/35 to 36, retaining minSDK 30. AGP 8.10.1 and
Gradle 8.14.6 support the build. The service declares specialUse foreground type
and permission; Android notification permission is requested once and may be
denied while the desktop remains usable. Phone controls/dialogs apply system-bar,
cutout and IME insets, recovery launches on display 0, and the tested legacy
Activity Back behavior is retained explicitly in the manifest.

Lint baseline bytes are unchanged. The former ExpiredTargetSdkVersion disable is
removed. The legacy shell fullscreen method has a scoped PrivateApi suppression
with its compatibility reason; it is not a global lint exclusion. SDK path syntax
was corrected in ignored local.properties; machine paths are not committed.

## Test corrections and scope

Ten JVM task-report cases cover legacy/modern formats, fullscreen uncertainty,
user/display separation, shared roots, malformed metadata and task selection.
The native idle suite starts a fresh desktop four times, launches Input Test in
each orientation, checks colored rendered pixels before any monitor menu, sends
direct input/Back, and backgrounds/resumes the phone controller. PixelCopy polls
for at most five seconds for a rendered frame and saves the final capture even
on failure; this is an eventual-rendering assertion, not a latency measurement.

Android 16's wm rotation command changed. Phone acceptance probes the supported
command, checks the actual per-display WM rotation (not a stale global setting)
and restores the prior mode. Initial controller launches explicitly target phone
display 0. Reusing Instrumentation.startActivitySync to resume an existing Activity
was replaced by explicit am start plus focus observation.

Input tests await the exact prior contact's release before arbitration and
observe the delivered DOWN before testing Fit cancellation. This removes queue
races without relaxing delivery assertions. During investigation a callback-only
held-contact variant exposed that service state survived while the actual window
was gone. It did not establish usable background touch and was removed.

Android 17's PresentationController associates a Presentation with the globally
focused host task and removes it when that task is destroyed. Android 16 QPR2
contains this policy too. On API 36+, eligible monitors use MonitorActivity to independently host the
same service-owned view tree on the physical monitor; presentation-only OEM
displays use the fallback described below. Legacy Android 11 keeps
Presentation. No overlay permission or window-policy bypass is requested. Host
attachments are generation/display/random-session-token checked; stale teardown cannot stop a newer
session. Stop/disconnect close the host and release the desktop. A bounded startup
failure reports recovery if Android prevents opening the host from background.
The host uses public immersive and side-gesture exclusions so rotated monitor
edges do not become Android Back gestures; mandatory OS gestures remain available.
Modern recognition bands extend to the inner edge beside system bars using
actual physical insets transformed through desktop rotation and Fit. Native edge
swipes start inside those observed bounds and preserve their original travel;
assertions still require the actual menu/app outcome. Reserved OS bars are not
claimed as app-owned gesture areas. Outside taps are clamped into usable monitor
space and independently checked against the measured panel bounds. Fit-bar tests
use the 45px point inside the 60px letterbox gap, below Android's top strip. The tests also retain complete injection
for monitor taps, pinch, Fit cancellation and controller-destruction input.
Instrumentation connects UiAutomation before opening app windows. Setup and
teardown send a monitor CANCEL because Android retains injected pointer state
after an aborted stream; otherwise a later DOWN is rejected as already pressed.
Raw failures remain failures; fixture cleanup never retries a failing assertion.
The input suite also observes the actual target Activity's window bounds after
resize and waits for accessibility/window events to settle before injecting a
new contact; display mode metadata alone does not establish input readiness.
The full injected-input lifecycle test retains held contact across phone task
destruction and verifies a fresh touch afterward; no callback-only substitute
remains. Modern background reconnect policy still needs explicit verification.

Phone launcher search uses HIDDEN (rather than ALWAYS_HIDDEN) initial IME state
and a posted insets show request on tap, preserving the first tap across modern
window-focus/input-connection ordering. Settings/read-only menus still stay hidden.

Modern SystemUI also crashed in CompatUIWindowManagerAbstract when late task
compatibility information arrived after its display layout was removed. Modern
shell teardown now removes roots only on the owned virtual display and confirms
their absence before releasing it. Cleanup errors are logged, with display release
still attempted so a failed shutdown cannot leak the virtual display. This is a
mitigation validated by the recorded runs, not a claim to fix vendor SystemUI.
Shizuku service version follows the APK build code so an upgrade refreshes shell
code instead of retaining an old singleton/task parser in a cached user service.

## Installed build identification

FR-024 / DX-VERSION-001 adds a read-only version name/build number immediately
below Settings on both screens. ControllerStyle supplies the shared label from
BuildConfig, including Debug identification. Native phone and monitor acceptance
compare the displayed values with installed PackageInfo metadata. This avoids
confusing emulator-only candidates with the older app still installed on the phone.

## Warning investigation

The user reports "Built for an older Android version." Target 36 addresses the
outdated target metadata. Both stock modern emulator images report minimum target
28, below the previous target 32; they did not reproduce that exact stock warning.
Do not claim the vendor/installer or Play Protect warning is proven resolved.
Fresh target-36 installation/startup, permission denial and runtime checks are
recorded separately from OEM install policy. No root or hidden-API policy bypass
was enabled; Shizuku ran as authorized shell UID 2000.

## Verification

See [versioned evidence (evidence withdrawn)](../../testresults/README.md) for exact APK hashes,
run IDs, host outcomes, failed attempts, visual review and remaining gaps.
Physical HDMI/touch, installer warnings, third-party app breadth and performance
distributions remain outside this emulator result. At the user's request the debug
candidate was installed on the connected Black Shark 4 without uninstall/data
reset, and Settings was opened to review its installed version. This UI check
does not substitute for full physical-monitor input regression.

References: [Android 16 target behavior](https://developer.android.com/about/versions/16/behavior-changes-16),
[foreground-service types](https://developer.android.com/about/versions/14/changes/fgs-types-required),
[AGP 8.10](https://developer.android.com/build/releases/agp-8-10-0-release-notes),
[warning guidance](https://developers.google.com/android/play-protect/warning-dev-guidance),
[AOSP Presentation policy](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/services/core/java/com/android/server/wm/PresentationController.java),
[AOSP input singleton](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/hardware/input/InputManagerGlobal.java),
[AOSP injection normalization](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/main/services/inputflinger/dispatcher/InputDispatcher.cpp).

## Physical Samsung Android 16 follow-up

The user's SM-S928B (API 36), connected over Wi-Fi ADB with shell Shizuku,
detects its FHD HDMI monitor at 1920x1200. The first physical run rejected
MonitorActivity: ActivityTaskManager reported that this display cannot host tasks.
See the failed [startup run (evidence withdrawn)](../../testresults/README.md).
SessionService now checks public Activity launch eligibility before selecting the
modern host, using the existing Presentation content on presentation-only displays.

The first fallback [input run (evidence withdrawn)](../../testresults/README.md)
passed rotation/input/background assertions but failed a new test's assumption
that Samsung necessarily removes a Presentation after controller destruction.
Its window actually survived. The corrected lifecycle check tests both permitted
outcomes: retained windows must accept fresh injected input, while removed windows
must release resources and recover on controller reopen. No app hidden-API policy,
desktop-mode setting, Secure Folder/user data, or display permission was changed.
The failed raw run remains a failure; it is not counted as a completed suite pass.

## Private reader navigation follow-up

The user reported Tachiyomi reader navigation moving to the phone and blanking
its external desktop on Samsung Android 16. Event logs show task 5653 moved from
desktop display 40 to display 0 when its private singleTask ReaderActivity opened.
There was no corresponding application crash in the inspected evidence. The
existing seven suites exercised DexDroid's own same-UID Input Test and did not
cover this third-party Activity transition.

A separate-UID parent/private-reader fixture in the test APK reproduces the same
failure using the previous build-19 APK. The shell now requests the framework
TRUSTED display flag on Android 12+, using its existing ADD_TRUSTED_DISPLAY
permission. Android 11 keeps its legacy display flags. This avoids a task watcher,
relaunching the parent, losing child extras, or stealing unrelated phone tasks.
Build 21 refreshes Shizuku's helper code and requests OWN_FOCUS on Android 14+
for independent phone/desktop input focus. The fixture remains in androidTest only.

On the real Samsung, a fresh history selection opened Tachiyomi's reader on the
same external task/display after the fix. Back returned to Tachiyomi there.
Screenshots were inspected locally but are not included in shareable evidence
because they contain the user's manga/library. Sanitized task evidence and the
scoped test report are retained in the versioned results folder.

Trusted-display regression also exposed a Back key canceled during physical
MOVE/UP focus changes. Edge navigation now commits after release on the next UI
frame, and canceled recognized gestures do not navigate. Both physical monitor
hosts apply side-gesture exclusions; modern Presentation bounds use the same
inner-edge rules as the Activity host. Mandatory Android gestures remain reserved.

UIAutomation's global animation synchronization introduced approximately five
seconds between synthetic gesture events in observed failures, exceeding the
unchanged 1.5-second gesture timeout. Display input now goes through an isolated
debug-only Shizuku Binder driver and the real InputDispatcher. It preserves
multi-contact timing and targets the external monitor only, rejecting default
and DexDroid desktop display targets. It is absent from release builds and does
not alter the instrumented app's hidden-API policy. Local menu-key tests wait for
settled measured layout and assert every editor update instead of retrying taps.
The driver's reserved Shizuku destroy transaction terminates its own process.

Primary policy references: [AOSP Activity launch policy](https://source.android.com/docs/core/display/multi_display/activity-launch),
[display flags](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/hardware/display/DisplayManager.java),
and [Shizuku user-service lifecycle](https://github.com/RikkaApps/Shizuku-API/blob/master/api/src/main/java/rikka/shizuku/Shizuku.java).

Final reader-navigation evidence and the remaining controller-resume failure are
recorded in the [build-21 report (evidence withdrawn)](../../testresults/README.md).
Earlier seven-suite successes are build-19 evidence, not build-21 verification.

## Host-attachment deadline follow-up

Build-28 independent review found that the previously documented startup timeout
was absent. Build 29 adds a five-second attachment deadline scoped to the pending
monitor and session generation. Failure runs normal teardown, releases the wake
lock and displays phone-reopen guidance. Attachment/Stop cancels the callback;
late hosts retain generation/token rejection. IdleAcceptance fault-injects the
post-request unattached state, then observes real timer cleanup, normal restart,
late-host rejection and harmless stale-timeout execution. This is controlled
component fault injection, not a reproduced OS background-launch denial. Raw
outcomes and retained setup failures are in the current Android 11 report.

Build-29 review additionally found platform foreground setup outside the recovery
boundary. Build 30 catches notification/service/promotion/wake-lock/host setup
failures through normal teardown and phone-reopen guidance. A narrow platform
promotion adapter permits controlled rejection on the actual bound service;
IdleAcceptance verifies no exception escapes, no pending/busy/wake state remains,
and normal foreground retry succeeds. Actual OS policy denial remains unverified.

### Launcher recovery follow-up (build 31)

Build-30 review identified that direct service retry acceptance did not exercise
the singleTask controller reopening path. Launcher intents now request startup;
resume retries pending requests while preserving explicit Stop. Acceptance uses
real launcher reopening after both controlled startup failures and verifies Stop
focus restoration followed by a visible-controller launcher request. Device
verification passes in targeted build-31 lifecycle and full ten-suite Android 11
runs linked from the current report; build-30 evidence is historical.

### Portable coverage inventory follow-up

PR #3 Linux host quality compiled successfully, then rejected the coverage
snapshot: native Path sorting differs by case between Windows and Linux. Coverage
now orders relative POSIX path strings explicitly. The new mixed-case portability
case runs with simulated case-insensitive comparisons and asserts identical
serialized output and canonical ordering. All 84 tooling cases pass; clean Git
export checks pass. Android sources and the tested build-31 APKs are unchanged.
See [tooling evidence (evidence withdrawn)](../../testresults/README.md); Linux
CI rerun and two fresh final-tree reviews are required before another push.
