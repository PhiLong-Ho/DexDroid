# Change: Monitor taskbar and optional center launcher

## Request and current status

2026-11-14: add a taskbar when monitor edge navigation is Off, containing Apps
and the menu currently opened from the top edge. Allow hiding the centered Apps
launcher. The user selected **Reserved bottom** on 2026-11-14. Implementation
follows that decision. Focused native acceptance passes; broader coverage stays
partial as recorded below.
The uncommitted phone-screen feature and unrelated drafts remain intact.

### Fixed-ratio correction

The user selected a fixed4% ratio with no override, following the proposed shorter
output-edge basis. Fresh UI expert `/root/ratio_taskbar_ui` and architecture expert
`/root/ratio_taskbar_arch` approved: round(0.04 × min(outputW,outputH)), square
targets of that height, half-height glyphs and one-sixth-height gap; no app-DPI
scaling. At1920×1200 this is48px at both240/320DPI; portrait swaps preserve it.
The fixed ratio explicitly supersedes the earlier48dp taskbar target minimum.
Other controls and native app DPI keep their existing behavior. No override UI.

DesktopViewport remains authoritative and the existing native resize transaction
is retained. MonitorShell passes its exact pixel height to the taskbar on sync,
including hidden-to-visible transitions. Pixel padding plus FIT_CENTER avoids
intrinsic drawable DPI limiting glyph scaling. The existing component/sequence
diagrams keep their structure; update size annotations. Targeted host/native
acceptance covers both DPI settings, rotations and Fit; earlier runs are historical.

Fixed-ratio native run `20261114T022621.471520Z` passes on Xiaomi14 Android16,
including explicit landscape240/320/120DPI equality and four rotations/Fit.
qualityCheck passes318 JVM tests. Author reviewed DPI/rotation comparisons and
updated the master reference. The [current evidence summary](../../testresults/0.20.0/taskbar-ratio/summary.md)
supersedes the older sizing references; no unrelated broad suites were repeated.

### Compact icon-only correction

The user requested a smaller icon-only Android-navigation style. Fresh UI expert
`/root/compact_taskbar_ui` approved48dp reserved height,24dp white glyphs in48dp
targets, one104dp centered group with8dp gap, black chrome without labels/divider,
and retained accessible Apps/Controls names, tooltips and shared focus/ripple.
This exact correction is authorized without another material decision.

Architectural impact: local styling and one size value in DesktopViewport; no
ownership, mapping, persistence, lifecycle, queue or IPC changes. All consumers
continue deriving content dimensions through the existing approved viewport and
resize/rollback transaction. A new architecture preflight is N/A for that reason;
this is not a release candidate or release waiver. Prior56dp captures remain
historical. Run host quality and focused taskbar acceptance for this revision;
reuse unchanged tooling evidence and omit unrelated broad device suites.

Compact acceptance `20261113T172532.316262Z` passes on Xiaomi14 Android16;
qualityCheck passes317 JVM tests. Author reviewed the new landscape output and
normalized four-rotation comparison; updated source-bound master references and
[private evidence](../../testresults/0.20.0/taskbar-compact/summary.md) supersede the
older labeled reference. No unrelated broad device suites were repeated.

## UI preflight

Read-only expert `/root/monitor_taskbar_ui` reviewed MonitorShell, IdleDesktop,
SplitWorkspace, SessionService, PhoneScreenButton and shared design/navigation
contracts. Source-based proposal only, not current rendered acceptance:

- Bottom monitor taskbar, visible exactly when Monitor edge navigation is Off.
- Apps opens the existing combined launcher; Controls opens the existing top-edge
  Controls menu, preserving its status/actions/settings and screen-on recovery.
- Two monochrome, labeled actions with independent 48dp targets. No extra Back
  action or independent taskbar toggle is implied.
- Show center Apps button defaults On and persists in shared Settings → Display.
  Off hides the center launcher on the single idle desktop and both empty split
  panes, preserving backgrounds and layout. It does not hide taskbar Apps.
- Toggling edges updates taskbar immediately. Menus retain existing Up, Close,
  keyboard-first Back and outside dismissal without click-through.
- Phone pointer, mouse and monitor touch must reach shell actions even when app
  touch forwarding is disabled. Taskbar existence must not steal app focus.

**Settled decision:** reserve a logical bottom strip (initially56dp, reduced to48dp
by the compact correction above), retaining unobstructed
app controls. Placement rotates/Fits with the desktop; Android-owned bars stay
excluded. The compact overlay alternative is rejected. New architecture preflight
`/root/reserved_taskbar_arch` reviews the concrete viewport contract before code.

**Exact placement correction:** the user then requested "Make the icon center
align instead of 2 corner". `/root/monitor_taskbar_ui` approved a WRAP_CONTENT
horizontal Apps/Controls group centered in the strip, with an8dp gap and existing
48dp targets/icons/labels. This is directly authorized, with no new material
decision. Full-strip shell ownership and the reserved viewport remain unchanged.

When edge navigation is Off, side-edge Back is also Off. Existing app navigation,
phone remote Back and supported hardware Back remain. If the phone is dark,
Controls → Turn phone screen on provides access to the phone controller. Do not
silently add a Back shortcut or claim a new global keyboard shortcut.

## Architecture preflight

Read-only expert `/root/monitor_taskbar_arch` inspected shared routing/geometry.
An overlay can remain MonitorShell-owned with shared hit bounds and complete
stream cancellation; a visible widget or hit() edit alone is insufficient.
SessionService.hardwarePointer currently bypasses the closed shell into split
input, so generic mouse and wheel paths also require shell-first hit routing.
Only bar-owned contacts may be consumed; app/Fit-bar behavior must remain intact.

Reserving a strip is materially broader: native texture, shell, cursor, input
inverse mapping, split pane/divider, normalized phone input, Fit and resolution
preview currently share full session width/height. It requires one authoritative
workspace viewport and separate full-output shell coordinates. Cropping or simply
reducing a texture is unsafe. The concrete viewport contract received renewed
architecture approval before dependent implementation, recorded below.

## Planned acceptance and documentation

Fresh `/root/reserved_taskbar_arch` approved the concrete viewport approach and
then re-reviewed its implementation with no source blockers: width/height remain
full output; one DesktopViewport supplies native app space; both helpers resize
before edge preference commit; rollback uses captured old geometry. Full-output
shell/cursor/input inverse stay unchanged, with app admission above the strip.
Source review is not runtime, rendered or release approval. Remaining checks
include boundary-crossing/held-input cancellation, failure-injected rollback,
occupied split native input and native keyboard focus/insets.

Reconcile FR-005 and the monitor navigation contract/stable DX map after selection;
update the design rule currently forbidding permanent controls over apps, README,
in-app guide, shared settings/search, and source-linked input/geometry diagrams.
Tests must cover occupied/empty single and split layouts, center preference,
edge-toggle persistence, shell access while app touch is disabled, phone-off
restore, four rotations and Fit, touch/phone pointer/mouse, keyboard focus and
modal dismissal. Reserved geometry also needs app-bottom hit/coordinate checks.
Regenerate coverage whenever tests change; current prior evidence is not a pass
for this proposed feature. No release or publication requested.

## Current verification

Centered taskbar suite `20261113T165823.605192Z` passes on physical Xiaomi14
Android16: four rotations, Fit1920×1080/UI240, native display/texture content
dimensions, lower-boundary app input, taskbar/modal isolation, app-touch Off,
phone pointer and native mouse/wheel, shared center setting/persistence, both
empty split directions and preview rejection/revert. No full Android matrix ran.
Selected native frames/rotation crop and five source-derived architecture diagrams
were author-inspected; no human pixel golden or performance certification.

The [taskbar evidence summary](../../testresults/0.20.0/taskbar/summary.md) records
current host/regression outcomes, source/APK bindings, retained failures and the
private local-only archive. Earlier two test-targeting/hidden-view assertions
were corrected without changing product outcomes. The old phone-guard fixture
needed explicit display0 injection and an owned inspect-only preparation state:
unrelated UI refresh correctly dismisses an unowned guard. The existing monitor
edge-toggle test now awaits committed resize before delivering its next gesture.
One command used repeated single-value suite options and selected only the last;
the actual later sequential suites are individually indexed. An APK signature
preflight rejection after a rebuild is retained, not counted as a native pass.

Current implementation review by `/root/reserved_taskbar_arch` found no concrete
source blockers; centered placement was separately approved by the UI expert as
the user's exact correction. These are not final-candidate/release attestations.
No commit/push/release is performed. Raw app/device evidence remains private.

Remaining gaps: held-input boundary crossing, resize-failure injection, occupied
split native input, native IME/focus/insets, phone-off taskbar restoration as one
combined scenario, large fonts/TalkBack, other OS/ROM and measured performance.
