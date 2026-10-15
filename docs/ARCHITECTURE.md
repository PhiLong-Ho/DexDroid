# Architecture and maintenance map

The app has two processes and two UI surfaces. Do not put shell privileges in
the phone Activity or duplicate authoritative desktop state in monitor widgets.

| Owner | Responsibility |
| --- | --- |
| `MainActivity` | Phone controller, dialogs, touchpad recognizers; binds to service |
| `SessionService` | Foreground session, display discovery, Shizuku connection, preferences, input arbitration, presentation and cursor |
| `DeskService` / `IDeskService.aidl` | Shizuku shell user service (`:desktop`); virtual display, targeted launch and input injection |
| `MonitorShell` | Native monitor menu tree, edge navigation, modal input and transitions |
| `ControllerStyle` | Shared phone/monitor widget surfaces, typography and status card |
| `MonitorSearchKeyboard` | Local low-latency keyboard for monitor launcher search |
| `IdleDesktop` / `EdgeFeedback` | Idle launch surface and transient navigation feedback |
| `Geometry` | Pure rotation/Fit/input mapping and DPI recommendation |
| `AppCatalog` | Shared installed-app discovery and ordering |
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
Android 11/JoyUI behavior are compatibility risks. A general emulator does not
prove physical HDMI touch, Shizuku or multi-display behavior. Native Android IME
on the untrusted desktop display is unsupported here; the high-latency mirrored
IME experiment was removed. Keep launcher search local.
