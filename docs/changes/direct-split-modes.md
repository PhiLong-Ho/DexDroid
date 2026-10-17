# Change: Apply launcher split choices directly

## Contract

FR-031 / DX-WINDOWS-001: in the shared Apps launcher, Left/right and Up/down
immediately enable two visible panes in that direction, without another toggle or
app selection. Selecting the current split direction from fullscreen restores
both panes. Single immediately ends split using the existing pane-1 retention
contract. Confirmed choices remain saved. Repeating the current visible layout
does not resize or recreate it; readiness and picker/session guards still apply.

The baseline spec, feature spec and requirement criterion/gap were updated before
production changes. No new controls or visual design are introduced.

## Implementation

`LauncherControls.mode` previously treated the selected direction as a no-op even
when one pane was fullscreen. It now permits that selection through the existing
direction transaction, which restores both panes. Direct Single-to-split and
split-to-Single retain the existing service lifecycle and successful-save paths.
Completion also requires a visible split state before resuming picker ownership. If restoration fails and rolls back to fullscreen, the old picker remains inert. Both phone and monitor use this component; no input/geometry logic is duplicated.

README, shared in-app User guide, design rules and architecture describe the direct
choices. The advanced Layout toggle remains available.

## Acceptance and evidence

`SplitAcceptance.run` now exercises Single → Left/right → Single → Up/down →
Left/right on both surfaces, checks live layout, saved enabled/direction state,
selected/operable buttons and repeated-selection stability. It opens a fresh
launcher after maximizing outside it, then selects the same direction to restore
both panes, for both directions at all four monitor rotations. This respects the
existing rule that unrelated geometry changes invalidate an old picker. At rotation zero, phone and monitor captures also assert selected-mode text and app-name pixels after each fullscreen restoration. Other rotations retain raw captures for author inspection; those pixel assertions are not claimed there.

Both surfaces and directions additionally inject the first primary resize
failure, delegate the real rollback resize, and check retained fullscreen
geometry/display IDs/preferences, disabled mode controls, rejected app launch
and recovery by reopening the picker. The proxy is restored in a finally block.
This exercises real native rollback, but does not simulate every partial
secondary failure or a genuine remote Binder death.

The fixture now captures each shown, measured app action once on the UI thread,
avoiding the old find/await/re-find race during catalog/list refresh. The failed
raw run and earlier runner-label rejection remain retained separately; neither is
counted as final verification. Failure-marker detection and behavior assertions
were not relaxed.

Phone mode choices invoke native widget clicks. Monitor mode choices inject
native touch through the existing display driver. Failed-picker app actions invoke
widget clicks on both surfaces; closing monitor setup menus also uses the existing
mouse control helper. These do not certify physical peripheral hardware.

Current commands, APK/source fingerprints, raw results, rendered review and gaps
are retained in [direct-mode evidence](../../testresults/README.md).
Base commit is `0204d85` plus the recorded working tree. Requirement coverage was
refreshed after the changed acceptance assertions. The lint baseline is unchanged.

The first fresh review pair rejected candidate `f7c30a8` because same-direction
rollback could revive the old picker. Their actual findings are retained in the
evidence record. The completion guard and rollback acceptance above address that
finding; the revised candidate requires a fresh pair.

No new release/version, push or PR is requested. Exact-candidate independent
reviews are recorded in `reviews/approvals.json` before commit. Modern Android/OEM,
full configuration/peripheral breadth, human visual approval and physical
performance remain unverified; current evidence is scoped to the connected
Android 11 setup. Other standalone suites are not implied by a Split suite pass.
