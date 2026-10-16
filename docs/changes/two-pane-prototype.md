# Two managed panes feasibility experiment

Contract: [feature spec](../specs/two-pane-prototype.md), SPEC FR-030 / DX-SPLIT-001.
Branch: `prototype/two-managed-panes`; base `df41f1f4bbbacbd034ab11c9915fcf250b3bbca2`.
No product Java/AIDL/resources, app version, normal launcher or release workflow change.
This is an explicitly launched debug experiment, not a published feature.

## Ownership and implementation

`src/debug/SplitProbeActivity` pauses the product session through its existing
service API, waits for its display to disappear, then hosts two TextureViews on
an external Presentation. Each pane binds its own shell process/component
(`SplitProbeService.Left` / `.Right`) and reuses DeskService's real virtual
display creation, launch verification and resize/teardown paths. Each pane gets
an actual display ID and real display dimensions, not a cropped screenshot.
The phone uses ControllerStyle buttons and status; selecting a pane adds a white
monitor border. Two ratios (50:50, 35:65) bound the resizing experiment.

The debug shell extension accepts complete KeyEvent and MotionEvent parcels.
Keys preserve action, code, modifiers, repeats and source. Key releases follow
the pane that received the corresponding down, even if selection changes.
The final experiment queries only the owned display's current task and requests
task focus before key-down. This runs in the shell input worker, with no timer
polling or task reparenting. It improves some sequences but the repeated typing
scenario still fails; it must not be represented as a completed focus solution.
Pointer streams retain their original pane through release/cancel. Wheel axes
and mouse button/source values survive forwarding. Child-view local offsets
must become a fresh display coordinate system before native injection: recreate
the motion sample from its local pointer properties/coordinates to rebase raw
and mouse cursor coordinates as well. Merely forwarding an offset event can
silently miss the right-hand display. Coalesced historical samples, advanced
touchpad gestures, pointer capture and system-reserved shortcuts are not certified.

Android 11 can move global focus while an app is pressing a native button.
The prototype uses the product's existing approach: defer initial contact when
the physical host lacks focus, release on its focus callback or a 100 ms fallback.
This bounded pending list includes subsequent keys, retains original event times,
and stops the experiment at 32 events rather than accumulating stale input.
The worker queue is separately bounded at 128 operations. Neither launch nor
resize/injection Binder calls block the UI thread. Stop discards queued input,
orders display stop behind in-flight work, then releases surfaces and shell binds.
This settling fallback is a latency limitation, not an instant-input claim.

TextureView does not support foreground drawables on Android 11. The selected
border belongs to a containing FrameLayout. A failed experimental run exposed
this restriction and is retained. Fixture Activities use native EditText/Button
widgets under the test APK's different UID and save observations across legitimate
configuration recreation; a resize can restart an app, not just relayout a View.

## Run locally

Build through `gradlew.bat spotlessApply qualityCheck`. Install the debug app and
test APK in-place with ADB; do not uninstall or change signatures/preferences.
The normal product suites are unchanged. Run this extra experiment explicitly:

```powershell
adb -s <serial> shell am instrument -w -r -e splitProbeOnly true dev.dexdroid.test/dev.dexdroid.DeviceChecks
adb -s <serial> pull /sdcard/Android/data/dev.dexdroid/files/split-probe-acceptance <run-folder>
```

Require final `INSTRUMENTATION_CODE: -1`, no FAIL/crash markers and observable
fixture assertions; ADB exit zero alone does not prove a pass. Retain raw output,
the source/build hashes and actual device/configuration in the run folder.

For manual experimentation (choose two different apps):

```powershell
adb -s <serial> shell am start --display 0 -n dev.dexdroid/.SplitProbeActivity --es left com.android.chrome/com.google.android.apps.chrome.Main --es right eu.kanade.tachiyomi/.ui.main.MainActivity
```

Tap a monitor pane to select it; phone Left/Right app buttons choose replacements.
Split ratio changes both app display sizes. The phone hardware-pointer area maps
absolute phone coordinates into the monitor workspace; this is an experimental
fallback for a peripheral routed to display 0, not the product's relative touchpad.
Stop ends both panes; reopen the normal DexDroid launcher to resume its desktop.
The prototype intentionally has no background service or automatic reconnect.

The phone-only debug manifest intentionally keeps the controller portrait,
matching the existing product geometry. Its two local lint suppressions document
that bounded form-factor scope; the shared lint baseline is unchanged. A guarded
legacy receiver helper suppresses the Android 13+ receiver-flag diagnostic only
on its explicitly pre-33 branch, matching the existing acceptance convention.

## Validation and decision boundary

See [retained results](../../testresults/0.11.1/two-pane-prototype/README.md).
Native injected framework mouse/keyboard/touch events are integration evidence,
not physical peripheral evidence. Keep Android 16/17, full rotation/Fit/DPI,
keyboard composition/layouts, background/reconnect, failure injection, stress and
latency/frame-pacing distributions unverified until separately exercised.
Two-display feasibility does not make arbitrary apps or hardware universally
compatible. Do not promote this experiment into the product without a new
product contract and the normal full review/release workflow.
