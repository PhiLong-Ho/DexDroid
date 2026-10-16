# Change: Compact status disclosure

## Contract

FR-001 / DX-UI-001 was updated before implementation. The user approved a small
bottom-center chevron inside existing status padding, with the monitor-status area
as its tap target. Remove the separate row/circle while preserving independent
Start/Stop and exceptional actions. The normal collapsed card must fit within
80 dp; large text may grow. Preserve remembered shared expansion, accessible
state/action and native keyboard activation. README and the offline guide describe
the new interaction. The prior pending README/AGENTS maintenance edits are included.

## Implementation

StatusCard uses a shared FrameLayout with a clickable, focusable vertical body
and a decorative vector ImageView over its bottom padding. The body contains
status text and action buttons; child buttons keep their own native event handling.
The chevron is neither a second hit target nor an accessibility node. Native
click/focus/key behavior, ripple, description and expansion state belong to the
body. Display/input transport and service lifecycle ownership are unchanged.

The normal layout uses a 48 dp header plus 12/18 dp padding, removing the old
48 dp arrow row and reclaiming 42 dp after reserving six more dp for the chevron.
This is layout geometry, not a measured performance improvement. Actual geometry
and rendered results belong in [the evidence record](../../testresults/0.12.0/compact-disclosure/README.md).

## Acceptance

| Criterion | Checks | Scope |
| --- | --- | --- |
| Compact height, centered decoration and single accessible target | UiAcceptance / MonitorAcceptance via StatusDisclosureAssertions | Phone and four monitor rotations; normal size, existing suite Fit scope |
| Expand/collapse, preference and pad height | UiAcceptance | Native accessibility click and focused View Enter handler; general physical-key routing is unchanged and not inferred |
| Text/chevron tap and child recovery isolation | PhoneMenuAcceptance | Real phone touch injection; recovery native button keeps details collapsed |
| Monitor chevron tap and existing menus/input/lifecycle | MonitorAcceptance | Physical display event injection across four rotations, existing Fit/Stop/background checks |
| Protection notice/help actions stay reachable | ProtectionAcceptance | Existing protected fixture suite; real OEM DRM is outside fixture scope |

Concrete runner entries remain mapped in requirements.json; the assertion helper
is included in their calls and inventory, not counted as an independent suite.
Coverage is refreshed after each test revision. The initial keyboard test tried
requestFocus while Android was in touch mode; the corrected driver explicitly
requests focus from touch mode before testing the View's native Enter handler.
Its original failed run is retained. A second run exposed the phone tap helper's
unrotated-coordinate assumption on the upward chevron. A follow-up exposed the
separate dialog-window origin. The helper now converts rendered global visible
bounds through the root window's screen origin and records actual bounds for real
taps. All original failed runs are retained. This does not claim an end-to-end physical
keyboard focus traversal or TalkBack session.

Final commands, source/APK identities, current outcomes, visual inspection, attempts,
gaps and two fresh independent reviews are recorded with the candidate. No lint
baseline change, publication or release is requested. Human/golden review and
broader Android/font/DPI, held-input/fault and physical performance checks remain
separate from mapped coverage and assertion totals.
