# Change: Compact controls and predictable menu navigation

## Contract (written before implementation)

- Requirements: DX-UI-001 / FR-001, DX-PHONE-001 / FR-002,
  DX-KEYBOARD-001 / FR-006, DX-NAV-002 / FR-008, DX-DISPLAY-002 / FR-015
  and DX-WINDOWS-001 / FR-031. Existing gesture rejection, style and lifecycle
  obligations remain in force.
- Keep one equal Apps/Rotate/Layout row. Put Start/Stop in shared status and
  Save/Reset/Scroll in current-app options. Phone Scroll sits at the left of the
  touchpad header, its label is centered, and remote Back/Info sit at the right.
  Header contacts do not reach the pad. Icon targets remain at least 48 dp.
- Nested Settings, Layout/picker and Apps/orientation menus return to the actual
  visited parent through leading Up or system Back. Back first hides a visible
  keyboard; a separate X closes the whole flow. Outside/Fit dismissal consumes
  the monitor contact and closes the flow. Root shortcuts discard old ancestry.
- Preserve Settings scroll and Apps query; skip invalid picker parents rather
  than reopening a stale workspace/session. Monitor orientation/Pointer speed
  choices return to their parent; multi-setting/action pages stay open for several
  adjustments before Up/Back. Keep/Revert stays reachable after a display preview.
- Both phone and monitor use the same rules. Four monitor rotations, local
  keyboard, input ownership, split selection and background/Stop remain relevant.
- This change does not add predictive platform Back animations, a new keyboard,
  or a new display/input transport. Other Android/OEM/font/peripheral coverage
  requires its own evidence; injected input is not a physical hardware report.
- Updated [baseline](../../SPEC.md), [design](../DESIGN.md),
  [controller UI](../specs/controller-ui.md),
  [input/navigation](../specs/input-and-navigation.md) and
  [split view](../specs/split-view.md) before/alongside the contract implementation.

## Implementation

- `MenuNavigation` owns a shared visited stack and valid-parent filtering.
  Surface owners retain view state and exact rendering/token ownership.
- `MainActivity` owns phone routes, dialog cleanup, Android IME-first Back,
  Settings scroll/Apps query restoration, the compact row and pad header.
- `MonitorShell` owns monitor routes, local-keyboard-first Back, fresh edge roots,
  parent scroll/query and confirmation placement. Pointer speed now uses the same
  submenu and apply/return behavior as the phone.
- `ControllerStyle.StatusCard` owns shared desktop/app actions. App settings
  contains scrolling, Save and Reset. Scroll restoration runs after local layout;
  its one-shot view listener cannot remain registered on a detached root observer.
- Existing service state, generation checks and input arbitration remain the
  authoritative runtime model. No lint baseline changes or large refactor.
- Performance measurements use existing catalog first-draw and native dispatch
  probes. They exclude display scanout, real finger/peripheral latency and frame
  pacing; navigation await limits are not timing evidence.

## Acceptance

Current results, configuration, exact source/APK hashes, raw attempts, screenshots
and remaining gaps are recorded in the
[versioned evidence (evidence withdrawn)](../../testresults/README.md).

| Criterion/scenario | Test/review | Scope |
| --- | --- | --- |
| Compact row/status/pad targets and gesture isolation | UiAcceptance, PhoneMenuAcceptance | Phone portrait, Stop/Start, real injected header touches |
| Parent navigation, scroll/query, keyboard, Close-all, preview Revert | PhoneMenuAcceptance | Native dialogs/IME, Settings children and Apps orientation |
| Shared controls, hierarchy, pointer choice, scroll/query and Close | MonitorAcceptance | Four monitor rotations, touch/pointer/edge/hardware-key injection |
| Keyboard-first Back, cancelled release and no underlying Back | MonitorSearchAcceptance, MonitorProfilesAcceptance | Search and profile keyboards, four-rotation search/Fit/lifecycle scope |
| Picker Up versus outside/Close and fresh edge roots | SplitAcceptance | Single/split modes, obsolete selection and pending configuration guards |
| Valid visited parents, roots, duplicate routes and replacement | MenuNavigationTest | Six deterministic JVM cases |
| Rendering and timing | Separate evidence records | Explicit reviewed captures and measured probes only |

- Host/device commands and outcomes: versioned evidence; failed attempts remain
  failures and are excluded from current pass denominators.
- Requirement checks were reconciled for every modified/added test;
  [coverage report](../coverage.md) and its snapshot are mapping evidence only.
- Human visual review is requested separately; do not infer it from automation or
  independent code approval. No pixel goldens are created or silently accepted.
- Final staged contents require two fresh independent agents under
  [the review protocol](../REVIEWS.md). Actual identities/digest/verdicts belong in
  `reviews/approvals.json`; no previous approval applies to this changed tree.
- Local implementation/testing is authorized. No push, PR, release, version bump
  or release report is part of this task.

The first review pair did not jointly approve: one identified overbroad wording
that implied every immediate change closed a page. The contract now distinguishes
single-value orientation/pointer choices from combined Scrolling/Layout/Settings/
App settings pages. Explicit native checks require mode and speed to apply while
Scrolling remains open. No product source changed for this clarification. Actual
prior verdicts are retained with the evidence; a changed candidate needs two new
independent reviews.
