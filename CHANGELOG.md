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

[Verification](testresults/0.10/README.md)

## [0.9.2]

### Fixed

- Repaired corrupted monitor-menu/session punctuation and enforced UTF-8 Java
  compilation with displayed-text/glyph checks.

[Verification](testresults/0.9.2/README.md)

## [0.9.1]

### Changed

- Replaced the launcher icon with a monochrome Android robot diving into a monitor;
  aligned adaptive/themed layers and transparent eye cutouts.

[Verification](testresults/0.9.1/README.md)

## [0.9]

### Added

- Minimal monitor idle background and centered Apps icon before a successful launch.

### Removed

- Delayed Android-keyboard mirroring prototype; responsive local launcher keys remain.

[Verification](testresults/0.9/README.md)

## [0.8.2]

### Added

- Local monitor search keyboard, selection-aware editing, hardware text/Back handling
  and real input/lifecycle acceptance coverage.

[Verification](testresults/0.8.2/README.md)

## [0.8.1]

### Changed

- Matched monitor Controls/Apps to the phone's monochrome icon menus, removed the
  persistent dock and Resume footer, and added outside dismissal.
- Added animated edge feedback/menu transitions and maintained input isolation.

[Verification](testresults/0.8.1/README.md)

## [0.8]

### Added

- Monitor edge navigation, app launcher, control panel and settings.

[Verification](testresults/0.8/README.md)

## [0.7.1]

### Changed

- Placed Stop/Start on the left and Back on the right of the second control row.
- Added focused layout, Stop/Start/focus and Back acceptance checks.

[Verification](testresults/0.7.1/README.md)

## [0.7]

### Changed

- Introduced a monochrome controller, equal two-row controls, combined Scroll panel,
  grouped live Settings and on-demand touchpad help.
- Preserved phone-controller focus after Stop.

[Verification](testresults/0.7/README.md)

## [0.6]

### Added

- Automatic Shizuku/desktop connection, queued app launching, recovery actions
  and gesture/connection diagnostics.

[Verification](testresults/0.6/README.md)

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
