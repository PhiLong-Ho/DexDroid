# Architecture and maintenance map

The app has two processes and two UI surfaces. Do not put shell privileges in
the phone Activity or duplicate authoritative desktop state in monitor widgets.

| Owner | Responsibility |
| --- | --- |
| `MonitorActivity` | Independent physical-monitor host on eligible API 36+ displays; generation/display checked attachment to service-owned content |
| `MainActivity` | Phone controller, dialogs, touchpad recognizers; binds to service |
| `SessionService` | Foreground session, display discovery, Shizuku connection, preferences, input arbitration, presentation and cursor |
| `DeskService` / `IDeskService.aidl` | Shizuku shell user service (`:desktop`); virtual display, targeted launch and input injection |
| `MonitorShell` | Native monitor menu tree, edge navigation, modal input and transitions |
| `ControllerStyle` | Shared phone/monitor widget surfaces, typography and status card |
| `MonitorSearchKeyboard` | Local low-latency keyboard for monitor launcher search |
| `IdleDesktop` / `EdgeFeedback` | Idle launch surface and transient navigation feedback |
| `DisplayTasks` | Pure legacy Stack / modern RootTask placement and fullscreen evidence |
| `ScreenInsets` | Phone system-bar, cutout and IME-safe padding shared by native dialogs/controller |
| `Geometry` | Pure rotation/Fit/input mapping and DPI recommendation |
| `MonitorProfiles` / `MonitorProfilePreferences` | Pure named snapshots/bindings/working settings and isolated atomic preference persistence |
| `MonitorProfilesPanel` | Shared phone/monitor management view and monitor-local name keyboard |
| `AppCatalog` | Service-owned asynchronous discovery, shared cache and package invalidation |
| `DiagnosticLog` | Bounded session event log; no typed/screen content |
| `InputTestActivity` | Observable target for native tap/scroll/pinch/Back checks |

These classes live under `app/src/main/java/dev/dexdroid`. The architecture is
small but `SessionService`, `MainActivity` and `MonitorShell` have accumulated
multiple responsibilities. Formatting makes them readable; it does not finish
their decomposition. Extract one tested responsibility at a time when changing
it (candidate boundaries: preference profiles, display session, menu construction,
gesture recognition). A wholesale rewrite alongside UI changes is hard to audit.

## Invariants

- Android display IDs are session-scoped. Rediscover after disconnect/recreation;
  do not persist a display ID as a monitor identity.
- Phone orientation is portrait. External rotation is 0/90/180/270 independently
  of phone rotation and the monitor's fixed physical landscape signal.
- Geometry maps pixels both ways through Fit and rotation. Bars reject contacts.
  Widgets/hit regions and the desktop must use the same mapping.
- Physical monitor touch wins over phone input until release/cancel. Preserve
  pointer IDs/down times and cancel before navigation takes ownership.
- Monitor touch hides the cursor; touchpad input restores it. Modal menus consume
  contacts and outside dismissal never clicks through to the external app.
- Activity teardown is not session Stop. Explicit Stop, disconnect, Shizuku loss
  and process death need lifecycle cleanup, contact cancellation and recovery.
- Resolution preview is reversible and rolls back after 15 seconds unless kept.
- Service/preferences are the source of truth for both menus, including per-app
  rotation/scroll mode/speed and live setting values.

## Test ownership

`app/src/test` runs pure JVM tests without device privileges. `app/src/androidTest`
uses the custom `DeviceChecks` instrumentation entry point. `UiAcceptance` calls
`PhoneMenuAcceptance`; monitor, search, idle and icon suites have their own
entry classes. Read `docs/requirements.json` for mappings and unclosed gaps.

The desktop uses Android hidden APIs under shell privileges. Reflection and
Android 11/JoyUI behavior are compatibility risks. The shell bridge selects
`InputManagerGlobal` when available and the Android 11 singleton otherwise;
verified fullscreen roots avoid the removed legacy task-mode API. The app targets
API 36 with a declared special-use foreground session. Notification permission
denial must not stop desktop input. Phone recovery explicitly targets display 0. A general emulator does not
prove physical HDMI touch, Shizuku or multi-display behavior. On modern Android, monitors eligible for Activity tasks use their own Activity
to avoid Presentation host-task removal; presentation-only OEM monitors use the
existing Presentation window with an explicit diagnostic limitation. Android 11 retains Presentation. Both build the
same service-owned monitor content and route identical input; the modern host
closes on Stop/disconnect and cannot attach to an obsolete session. With an independent Activity host, the phone task can be destroyed independently. A five-second host-attachment deadline reports recovery if
Android blocks opening the monitor host from background. The deadline captures
monitor identity and generation, tears down failed startup/wake lock, and tells
the user to reopen on the phone. Attachment/Stop removes it; obsolete callbacks
and late hosts cannot adopt or stop the next session. Background reconnect
on modern OEM builds remains a verification gap. Native Android IME
on the desktop display is not enabled by DexDroid; the high-latency mirrored
IME experiment was removed. Keep launcher search local.

Display API references: [Display.Mode](https://developer.android.com/reference/android/view/Display.Mode),
[DisplayMetrics](https://developer.android.com/reference/android/util/DisplayMetrics),
and [VirtualDisplay](https://developer.android.com/reference/android/hardware/display/VirtualDisplay).

### Presentation-only OEM monitors

Before choosing the modern Activity host, SessionService asks the public
ActivityManager launch-eligibility API about the actual monitor and host Intent.
Some Samsung Android 16 HDMI displays allow Presentation windows but cannot host
Activity tasks. These retain the same service-owned content through Presentation;
no display policy or root exemption is enabled. Diagnostics identify the fallback.
Ordinary background use and phone-task destruction are separate lifecycle checks.
If the OEM keeps the window, fresh external input must still work; if Android
removes it, resources must stop cleanly and reopening the controller must recover.

### Private Activity navigation

On Android 12+, DeskService requests the framework TRUSTED virtual-display flag
(1 << 10) using the authorized shell's existing ADD_TRUSTED_DISPLAY permission.
Normal third-party applications can then launch their own non-exported child
Activities on that desktop instead of Android resolving them onto display 0.
Android 14+ also requests OWN_FOCUS so the virtual desktop and phone can retain
independent input focus. Android 11 retains its legacy display flags. No periodic task reparenting or phone-task stealing is used. Rejected creation is reported by the existing failure path. The app and
third-party apps are not granted privileged display permissions.

The child device suite runs ordinary Activities from the test APK under its own
application UID. Its exported parent opens a private singleTask reader through
normal startActivity, then the test verifies actual display/task identity,
intent and parent state, PixelCopy pixels, real input and Back in four rotations.
These fixtures do not ship in production APK/AAB artifacts.

TestInputDriver lives in src/debug, not main or release. A separate authorized
shell process validates external presentation targets and injects Motion/Key
parcels through InputDispatcher without UiAutomation's animation flush. Native
phone-display tests keep the public default-display UiAutomation path. Binding
is established before event timestamps are generated, and the reserved Shizuku
destroy transaction exits the test process after unbinding. Continuous event
streams still traverse the actual monitor window, widgets and product relay.

### Task handoff and controller resume

The phone controller is a singleTask Activity. Each instance owns its service UI
listener; a destroyed older instance cannot clear a newer listener, and resume
reattaches it. Launcher/notification recovery targets display 0. Acceptance waits
for the background transition to finish before reopening, then requires actual
phone Settings/Close input and fresh external touch, not visibility alone.

`DesktopTasks` lives in the shell helper and registers the platform task listener
through reflection, resolving notification transaction codes on the running OS.
It queries current RootTaskInfo (API 31+) or StackInfo (API 30) on a dedicated
HandlerThread, coalescing events at 150ms and retaining at most one pending query.
There is no timer polling or task movement. Only the owned virtual display's
occupancy is returned; unrelated app names/snapshots are not sent to the UI.
Duplicate occupancy is suppressed. Query errors are reported without treating
unknown state as empty. The observer unregisters and stops on teardown.

`IDesktopListener` returns occupancy to SessionService. Main-thread application
checks the captured session generation and current display ID before changing
state. When the last task departs, the service cancels held contacts, hides the
pointer, clears the absent selected app and exposes the existing IdleDesktop
over retained TextureView pixels. A remaining task keeps idle hidden. Reopening
through DexDroid still uses explicit verified placement; phone launches are never
forced back to the desktop. Build 22 refreshes the changed helper/AIDL contract.

Platform references: [task notifications](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/app/ITaskStackListener.aidl),
[root task metadata](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/app/ActivityTaskManager.java),
[legacy stack metadata](https://android.googlesource.com/platform/frameworks/base/+/android-11.0.0_r1/core/java/android/app/ActivityManager.java).

### Apps discovery and menu acknowledgment

SessionService owns AppCatalog for its lifetime and prewarms it at service start.
One worker scans launcher packages, labels and icons; no package discovery runs
on the UI thread. Package broadcasts invalidate a revision. At most one scan runs,
and stale completions coalesce into one current scan. Errors keep the previous
list and expose an explicit retry. Service teardown unregisters the receiver and
ignores late results.

Phone and monitor open their shared-style menu shell before observing the cache.
Cold discovery shows a monochrome progress row; refresh retains searchable rows.
Completion filters using the current editor text without replacing the editor or
keyboard. Dismissal, replacement and monitor destruction remove subscriptions;
callbacks also check their owning dialog/editor. App rows clone cached drawables
per surface so different display densities never share mutable icon bounds.
Non-cloneable icons are rasterized on the worker before publication.

CatalogAcceptance gates a real worker scan to test pending, refresh, error/retry,
search retention, coalescing and dismissal. Timings measure the accepted native
Apps action to first OnDraw, with separate cold acknowledgment and warm samples
in four monitor rotations. They exclude physical input delay, animation completion
and frame pacing. Screenshots wait separately for presentation to settle.

### Protected-window guidance

DeskService owns ProtectedWindowObserver alongside the task observer. While a
current desktop task exists, a separate single worker checks visible-app window
metadata using shell dumpsys with a two-second service timeout and two-second
fixed delay. It never runs on input/task/UI workers, queues no overlapping scans,
and skips idle desktops. ProtectedWindows parses surfaced, on-screen FLAG_SECURE
windows for the owned virtual display only. The complete window-list header may
follow a JoyUI display diagnostic without a newline; incomplete headers/fields
remain unknown. Phone/other-display and hidden windows
are excluded; unsupported output is unknown. Metadata is streamed and discarded;
no window titles, URLs, page contents or screenshots are retained or sent over IPC.

The one-way listener carries only display ID and a three-state result. Main-thread
application checks session generation and display ID, retains the last confirmed
state on unknown, and clears state on empty desktop/Stop/disconnect. Teardown
terminates in-flight inspection and its executor. The monitor notice occupies only
its own hit area, so other app touches and edge navigation remain available.
Help/outside dismissal own their full contact stream and never click through.
Notice acknowledgment is local to that protected episode and survives menu/rotation
rebuilds. A subsequent clear state re-arms it.

ControllerStyle shares notice styling and ProtectedScreenGuide supplies identical
offline text to both menus. Settings always exposes Help, independently of metadata
availability. This detects protection, not Chrome incognito; no browser flags or
privileged capture policy are changed. Build 24 refreshes the shell/AIDL contract.

### Android 11 touch and global focus

A physical DOWN can move legacy global focus away from the virtual app after its
relayed DOWN has already pressed a View. The resulting focus-loss notification
clears that pressed state before UP, losing the semantic click even though both
events reach the right coordinates. The different-UID reader fixture records that
sequence; it is distinct from missing input injection or a protected window.

On API 30, Monitor holds an initial app contact until its Window focus callback.
OEM Presentation hosts that do not gain focus use a 100 ms main-handler settling
fallback. This is asynchronous; there is no sleep, global animation flush or
synthetic retry. At most 16 MotionEvents are held, adjacent moves coalesce, and
original ordering/timestamps survive. Busy/stale/removed hosts, edge takeover and
overflow discard pending input. Teardown recycles events/removes the timeout.
Other Android versions retain their existing relay. The diagnostic records actual
relay delay and which path released it. Reader acceptance retains semantic clicks
and separately asserts no focus loss during contact, with native event-to-dispatch
samples distinguished from physical-touch-to-photon measurements.

Buffered focus-wait contacts participate in the same physical-input ownership
predicate as relayed contacts and shell gestures, so phone touch/pointer/wheel
cannot interleave. Shared monitor cancellation clears queued events and removes
the timer as well as canceling relayed contacts. Release detaches its bounded
batch before feeding DOWN through normal initialization, so that initialization's
cancellation cannot recycle a still-valid buffered UP. Dispatch is one main-thread
turn; newer contacts cannot interleave with the detached batch.

Notification creation, platform service start/foreground promotion, wake-lock
acquisition and monitor launch share one recovery boundary. ForegroundPromotion
is the narrow platform adapter; controlled instrumentation rejects that adapter
on the real bound service and checks cleared state, guidance and a normal retry.
This fault injection does not claim an actual vendor background-policy denial.

Phone controller launcher intents explicitly request startup on the existing
singleTask Activity. Resume retries an existing pending request; explicit Stop
clears that request, so controller focus restoration does not restart a session.
