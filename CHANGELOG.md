# Changelog

Notable release changes are recorded here, newest first. Existing version labels
are preserved; historical release dates were not consistently recorded.
Verification details and raw evidence live in [testresults](testresults/README.md).

## [Unreleased]

### Added

- Protected `main` with required PRs, up-to-date host/agent-review CI, administrator
  enforcement, resolved conversations and blocked force pushes/deletion.

- Spec-driven contribution workflow, requirement-to-test map, shared design and
  architecture guidance, project Codex skill and bounded Goal examples.
- Formatting/lint/build/host-test gates, GitHub Actions, VS Code tasks, device
  evidence runner and optional screenshot comparison.
- Tagged/manual signed APK/AAB release workflow, persistent private signing key,
  verified artifacts/checksums and concise changelog-based release summaries.

### Changed

- Formatted Android sources/resources and tracked 34 existing lint findings as
  explicit debt; new findings fail checks.
- Shortened README to setup/install/quick use; consolidated the product spec using
  the GitHub Spec Kit structure and moved release history here.
- Organized historical logs and selected visual evidence under versioned
  `testresults/` folders; new device runs use version/timestamp directories.

## [0.12.2]

### Removed
- Development input-test screen and synthetic Apps entry from official builds; debug builds retain the diagnostic.

### Changed
- Release APK/AAB checks reject development components and defined classes before packaging.

## [0.12.1] - 2026-10-17

### Added

- Whole-app component and feature diagrams, developer onboarding, and mandatory
  architecture review for every release.
- Local command monitoring with live output/status, deadlines and owned-process
  cleanup, plus staged coding practice and private evidence archives.

### Changed

- Phone and monitor menu headers use plain icons with clear title spacing.
- Status Start/Stop uses a plain white icon with transparent resting background,
  retaining its accessible 48 dp target and ripple.
- Public release evidence is checked before archiving and again before publication;
  personal device captures stay private.
- Native Stop and split acceptance wait for the correct phone/input windows,
  preserve exact one-tap checks, and complete synthetic contact cleanup.

### Fixed

- Explicit Stop restores the existing phone controller after confirmed single/split
  cleanup, including native Chrome handoff; uncertain cleanup reports recovery.
- Left/right and Up/down immediately enable both panes, including restoration from
  fullscreen; Single returns to one pane.
- Phone Back defers navigation beyond old-window key dispatch and ignores closed
  or replaced dialog owners while preserving keyboard-first Back.
- Full device runs separate supervisor logs from monitor evidence; phone handoff
  tests use normal app launch and native screenshots select the phone explicitly.

## [0.12.0]

### Added

- Shared Shizuku install/open/authorize/reconnect guidance with explicit non-root
  startup instructions, and a concise offline User guide in both Settings menus.

- Split view with two managed apps, shared phone/monitor Layout settings,
  left/right or top/bottom arrangement, draggable divider, ratio presets,
  swap and selected-pane fullscreen/restore.
- Selected-pane app launch, Back and scroll preferences, native hardware key/mouse
  relay, empty-pane Apps and independent pane close; service-owned background lifetime.

### Changed

- Removed the status triple-dot shortcut; App settings lives in Settings.
  Replaced the separate arrow row with a tappable status body and small bottom-center
  chevron to reduce collapsed height. Grouped Touchpad with Info
  between Scroll and Back. Clarify ambiguous design choices with the user.

- Status starts collapsed with main actions visible and a remembered expansion
  arrow. Preview confirmation, protected-content help and recovery stay reachable.

- Reduced phone and monitor controls to Apps, Rotate and Layout. Start/Stop is
  in status; App settings is in Settings and phone Scroll is in the touchpad header.
- Added consistent submenu Up navigation, preserved parent scroll/search state,
  keyboard-first Back and a separate Close-all action on both screens. Display
  preview Keep/Revert remains reachable when returning to the parent menu.

- Both app displays follow workspace rotation, resolution/DPI preview and rollback.
  Confirmed split on/off, direction, ratio and order survive reopening and Stop/Start;
  ending split keeps pane 1 and closes pane 2. Third-party
  resizing restrictions still apply; broad hardware/Android compatibility is unverified.

- Layout is directly available in the shared control grid; phone Back is at
  the touchpad top-right and Info accompanies the centered Touchpad label. Pane selection no longer outlines running app content.
- Layout offers one Open app target or two spatial Open left/right/up/down targets.
  Apps opens a combined searchable launcher with Single/Left-right/Up-down modes
  and spatial actions beside each app, keeping the touchpad size unchanged.
  Pressed actions gently highlight their destination; pickers opened from Layout
  navigate back there. The divider is thinner with a wider grab area; empty panes share the
  single-screen background, which follows direction/ratio/Swap/fullscreen transitions
  without retained bounds; short panes hide the decorative caption to clear the Apps icon.

### Fixed

- Prevented secondary-pane frame stretching by laying out both views before
  attaching fresh resize surfaces.

## [0.11.1]

### Added

- Protected-screen notices on the monitor and phone status card, with shared
  offline Help explaining Chrome's optional incognito flag, privacy consequence
  and undo steps. Settings retains manual access when detection is unavailable;
  JoyUI window metadata also clears the notice after protection ends.
- Minimal Apps loading/updating and retry states on both screens, backed by shared
  background discovery and cached results; search remains usable while loading.
- Installed version and build number in phone and monitor Settings, with debug
  builds identified so installations can be distinguished.

### Fixed

- Android 11 first-touch clicks lost during cross-display focus handoff, using a
  bounded asynchronous contact buffer and explicit relay diagnostics.
- Apps menu stalls from repeatedly scanning packages, labels and icons on the UI
  thread; cached icons also retain independent bounds for each display density.
- External apps hidden behind the idle desktop on modern Android: recognize
  RootTask placement reports and already-fullscreen tasks before confirming launch.
- Android 17 input initialization through the current input-manager singleton,
  retaining the Android 11 fallback.
- Monitor window survival after phone-task destruction on modern Android, owned
  task cleanup before virtual-display release, and helper-code refresh on upgrade.
- Presentation fallback for Samsung/OEM monitors that reject Activity hosting,
  and bounded recovery when Android silently blocks a monitor Activity launch
  or rejects foreground-service startup.
- Trusted shell desktops on Android 12+ so apps can open their own private reader/detail screens without moving to the phone; Android 14+ keeps independent display focus, and Android 11 retains its existing display flags.
- Restore the idle launcher when the last desktop task moves to the phone or exits, covering stale app frames without stealing the phone task.
- Reuse the phone controller on reopen, retry failed startup through the launcher,
  and keep its service listener attached across Activity replacement/resume.
  Restoring focus after Stop keeps the desktop stopped.
- Monitor hardware Back handling, phone recovery display selection, and phone
  menu padding around system bars, cutouts and the Android keyboard.

### Changed

- Target Android 16 (API 36), declare the foreground desktop service type, and
  request notification permission with desktop operation preserved when denied.
- Add Android 16/17 emulator acceptance and legacy regression evidence; emulator
  coverage does not certify vendor installer warnings or physical HDMI/touch.

## [0.11]

### Added

- Named monitor profiles for resolution, UI DPI and orientation, with matching
  phone/monitor Settings menus to create, rename, update, apply and delete them.
- Per-monitor profile associations that restore automatically on desktop startup
  or reconnect, with stable identity matching where available and a disclosed
  name/capability fallback for indistinguishable monitors.
- Keep/Revert profile previews, safe legacy-setting import and Forget monitor
  without deleting saved profiles or changing app-specific overrides.
- Host persistence/matching tests and a dedicated native profile acceptance suite;
  remaining device, visual and multi-monitor verification gaps are reported.

## [0.10.2]

### Added

- A Markdown release report and structured JSON for every release, attached with
  checksums and linked from release notes to the changelog, release and workflow.
- Per-suite and combined host pass rates from actual case outcomes, alongside
  requirement coverage, explicit gaps and unrun device/visual/performance checks.
- Structured tooling test outcomes and publication gates for missing, mismatched
  or invalid release reports.

## [0.10.1]

### Added

- Generated requirement coverage with concrete test entries, scenario dimensions,
  explicit gaps and a freshness check for every test source change.
- Two fresh independent agent reviews required before push/release, with candidate
  digests, recorded approvals, a local pre-push hook and CI/release gates.

### Changed

- Updated the project skill and contribution flow to maintain coverage alongside
  tests and invalidate review approvals whenever candidate contents change.

## [0.10]

### Changed

- Unified Apps, Rotate, Scroll and one-time orientation menus with Settings on
  phone and monitor, including rounded rows, subtitles and icon Close actions.
- Made phone Settings/submenus full height with fixed headers, scrolling and
  keyboard-safe app results. Resolution, pointer and diagnostic pages use the frame.
- Kept the phone controller portrait and protected touchpad input from page scrolling.

[Verification (evidence withdrawn)](testresults/README.md)

## [0.9.2]

### Fixed

- Repaired corrupted monitor-menu/session punctuation and enforced UTF-8 Java
  compilation with displayed-text/glyph checks.

[Verification (evidence withdrawn)](testresults/README.md)

## [0.9.1]

### Changed

- Replaced the launcher icon with a monochrome Android robot diving into a monitor;
  aligned adaptive/themed layers and transparent eye cutouts.

[Verification (evidence withdrawn)](testresults/README.md)

## [0.9]

### Added

- Minimal monitor idle background and centered Apps icon before a successful launch.

### Removed

- Delayed Android-keyboard mirroring prototype; responsive local launcher keys remain.

[Verification (evidence withdrawn)](testresults/README.md)

## [0.8.2]

### Added

- Local monitor search keyboard, selection-aware editing, hardware text/Back handling
  and real input/lifecycle acceptance coverage.

[Verification (evidence withdrawn)](testresults/README.md)

## [0.8.1]

### Changed

- Matched monitor Controls/Apps to the phone's monochrome icon menus, removed the
  persistent dock and Resume footer, and added outside dismissal.
- Added animated edge feedback/menu transitions and maintained input isolation.

[Verification (evidence withdrawn)](testresults/README.md)

## [0.8]

### Added

- Monitor edge navigation, app launcher, control panel and settings.

[Verification (evidence withdrawn)](testresults/README.md)

## [0.7.1]

### Changed

- Placed Stop/Start on the left and Back on the right of the second control row.
- Added focused layout, Stop/Start/focus and Back acceptance checks.

[Verification (evidence withdrawn)](testresults/README.md)

## [0.7]

### Changed

- Introduced a monochrome controller, equal two-row controls, combined Scroll panel,
  grouped live Settings and on-demand touchpad help.
- Preserved phone-controller focus after Stop.

[Verification (evidence withdrawn)](testresults/README.md)

## [0.6]

### Added

- Automatic Shizuku/desktop connection, queued app launching, recovery actions
  and gesture/connection diagnostics.

[Verification (evidence withdrawn)](testresults/README.md)

## [0.5.1]

### Added

- Adaptive launcher icon and Android 13+ monochrome themed layer.

## [0.5]

### Added

- Per-app rotation/scroll-mode/speed saving and reset; Tachiyomi defaults to Swipe.
- Compact current-app status panel.

## [0.4]

### Changed

- Hid the cursor during monitor touch and restored it for phone-pad input.
- Added per-app scroll speed and reduced reader scrolling sensitivity.

## [0.3]

### Added

- External monitor touchscreen forwarding with four-way rotation/Fit mapping.
- Background desktop session ownership independent of the phone controller.
