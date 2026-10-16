# Change: Direct spatial app targets and remembered split layout

## Contract (write before implementation)

- DX-WINDOWS-001 / FR-031: one Open app target in single view; two left/right or
  up/down targets in split, matching positions through Swap. Main Apps opens Layout
  in split. Targets directly open app selection with a temporary gentle destination
  highlight. A 2 dp divider retains a separate 12 dp grab band. Empty pane backgrounds
  reuse the single-screen canvas and icon.
- DX-WINDOWS-002 / FR-032 invariants remain: physical ownership, keys/contacts and
  modal routing cannot regress. Picker destinations are captured, never redirected
  by current selection or stale session callbacks.
- DX-WINDOWS-003 / FR-033: confirmed enabled/direction/ratio/order survive normal
  closure, Stop/Start, recreation and reconnect. End saves single view. Transient
  selection/fullscreen, tasks, IDs, highlights and contacts are excluded.
- Baseline, existing split feature spec, requirement criteria/gaps and design were
  updated before implementation. Unsupported modern/OEM/peripheral combinations
  remain unverified until their own evidence exists.

## Implementation

- Shared `LayoutPanel` shows spatial targets and hides split-only controls in single
  view. Phone and monitor use one target callback and the same preference/state rules.
- Service-owned app-choice tokens capture workspace/generation/display/pane.
  Completion/cancel/replacement/configuration/Stop clear only appropriate ownership;
  obsolete choices cannot launch into a new pane/session. The monitor shell draws
  subtle destination feedback beneath menu panels, with no input ownership.
- Both empty panes reuse `IdleDesktop`. Divider native geometry uses a 2 dp gap;
  the pure grab-band model retains a clipped 12 dp minimum input target.
- `SplitLayoutPreferences` serializes only four durable choices. Successful remote
  configuration commits persist; rollback/failed setup/rejected requests do not.
  Desktop creation restores enabled split before pending launches drain.
- Instrumentation explicitly snapshots/clears/restores those four split preference
  keys to establish single-view fixtures even when the user's remembered mode is
  split. Product startup preferences are unchanged by this test-only fixture.

## Acceptance (update after implementation)

- New JVM cases cover spatial target labels/order, thin-line grab geometry, empty
  defaults, persisted geometry, End retention, transient exclusion and invalid
  types/non-finite/out-of-range preferences.
- Updated native split checks cover one/two targets, actual phone target tap,
  captured-pane launch after selection changes, highlight lifecycle, replacement/
  stale-session rejection, swapped vertical targets, monitor targets in four rotations,
  wider grab band, shared empty backgrounds and Stop/Start restore with fresh IDs.
- Existing native input, keyboard, rendering, Fit/rollback, private-child, handoff,
  protection and obsolete-helper scenarios remain in the split suite.
- Initial attempt `20261016T130525.670725Z` checked phone dialog cleanup before
  Android delivered its posted OnDismiss callback. The assertion now awaits that
  observable lifecycle completion; raw failure evidence is retained.
- A later ownership audit added rejection of a long press from a superseded phone
  picker before it can create a new orientation-choice token. Acceptance explicitly
  replays that stale long press and requires the replacement destination to survive.
- Monitor Back skips cancelled picker pages after moving from app selection to
  Controls, returning to Layout instead of reopening an unusable app list. The
  final split acceptance checks this navigation and cleared destination highlight.
- Current verification: 128 JVM cases, 84 tooling cases and final affected UI, monitor,
  keyboard and split device suites passed on Android 11. Earlier eleven-suite runs
  used intermediate APKs. Commands, source/APK hashes, raw attempts, rendered review
  and exclusions are retained in the [evidence record](../../testresults/0.12.0/layout-launcher/README.md).
  Final human visual approval, modern Android and physical peripherals remain gaps.
- No lint baseline change, release/version bump, push or publication is implied.
