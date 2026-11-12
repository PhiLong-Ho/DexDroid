# Change: Menu readability and visual User guide

## Requested contract

- User requests less text-heavy menus, meaningful icons, separated instructions
  with paragraphs/bullets/spacing, and simple visual explanations in User guide.
- Existing requirements: FR-001 / DX-UI-001, FR-003 / DX-TEXT-001 and
  FR-035 / DX-GUIDE-001; retain FR-002 / DX-PHONE-001 navigation and bounds.
- Scope: matching phone and monitor Settings/submenus and shared offline guidance.
  Preserve monochrome minimalism, current values, main control count, touchpad
  space, stateful recovery, Up/Back/Close and native keyboard ownership.
- Baseline: released 0.18.0 at `c05649ce804f077700db50676827b48a812ac321`.
- Implementation is a presentation-only development build on this feature branch;
  the app remains version 0.18.0/code 51. No release or publication is requested.

## UI preflight

The collaboration tool could not create a worker because its session thread
limit was reached. A fresh independent read-only Codex CLI worker was created
instead: `01a1cff3-081b-7203-8dff-11b44f796ca9`. Its initial request supplies the
user's outcome, current contracts/source and explicitly historical private
phone/monitor Settings captures from the 0.18.0 development run. It has no
inherited author conversation or implementation conclusion. Its actual verdict
recommends a focused formatting pass: leading labeled 24 dp vectors with 12 dp
gap, 16 sp semibold reading headings/14 sp body, separated paragraphs/steps,
and small offline diagrams with textual equivalents. It advises preserving
existing groups/order, panel bounds, continuous guide and action placement.

The user's explicit request for icons, paragraph/bullet spacing and visual guide
instructions authorizes this formatting scope after the proposal. No new topic
chooser/accordion/submenu, monitor width/column change, setup-action reorder or
other material navigation decision is selected. No radio answer or silence is
treated as approval. An illustrative synthetic proposal is retained privately at
`.local/menu-readability-proposal/menu-readability-proposal.png`; it is not to
scale and is not a native app render. The parent inspected it before proceeding.

Material placement/grouping/guide navigation decisions must be presented before
dependent code changes under [UI preflight](../WORKFLOW.md#before-implementation).
The baseline used text-only Settings rows and put each guide topic's heading/body
into one TextView, including lengthy workspace, keyboard and debugging guidance.
The implementation replaces that construction with explicit static blocks. Actual output must be inspected after changes;
historical captures and proposal sketches are not rendered acceptance.

## Architecture preflight

Fresh read-only Codex CLI expert `01a1cff7-e4c3-7152-884d-bd14fae5ae4a` returned
**approve-approach**, with no consequential architectural unknown blocking this
bounded presentation implementation. Actual advisory review is retained locally
in `.local/menu-readability-proposal/arch-review.md`. It is separate from release
architecture and final code approvals. Current owners: ControllerStyle supplies visual
construction; MainActivity and MonitorShell own surface/menu lifetimes;
UserGuide supplies shared static offline content. No shell, service authority,
input routing, persistent state or navigation changes are requested. A proposal
that changes shared ownership, navigation/lifetime or adds stateful content
requires renewed architecture review before dependent implementation. The reviewed
approach uses explicit icon resource arguments, independent views/drawables,
typed static guide blocks and passive diagram/native-label rows. It preserves
tagged native control types, RadioGroup membership, existing action/lease/refresh
ownership, text selection, scroll/focus, stale-owner Back guards and outside
contact ownership. No label-to-icon inference or service-aware renderer is added.
Existing
diagrams: [controller](../architecture/features/controller-ui.md) and
[setup/guide](../architecture/features/setup-and-guide.md).

## Implementation

- Shared Settings rows use explicit 24 dp native vectors, a 12 dp label gap and
  vector chevrons. Icons decorate existing labeled actions and add no focus stop.
  Monitor switches retain their original native type/tag and state callbacks;
  their labels and current values now use the shared two-line presentation.
- `ControllerStyle` provides reading headings, paragraphs and native bullet/step
  spans. Shared setup, audio, diagnostics, capability, workspace, debugging,
  keyboard, profile and protected-screen panels reuse these presentation helpers.
- `UserGuide` keeps all 13 topics in the existing continuous page. Typed static
  blocks separate headings, steps, facts, warnings and recovery. `GuideDiagram`
  draws passive gesture/rotation/split shapes beside wrapping native text labels.
  There is no WebView, bitmap asset, new navigation, asynchronous renderer or IPC.
- Phone/monitor menu hierarchy, touchpad area, control count and action ownership
  stay with the existing owners. README, shared design rules and source-linked
  component/sequence diagrams are reconciled. Five Mermaid diagrams were rendered
  and author-inspected; their renders are private supporting documentation evidence.

## Acceptance selection

- Both surfaces: meaningful labeled vectors, live values and disabled/pending/error
  text retained; explanatory groups visibly separated from action rows.
- Guide: concise topic/step hierarchy and labeled gesture/rotation/split visuals;
  no important setup, recovery, compatibility or privacy caveat removed.
- Font enlargement/narrow bounds: wrapped readable text, no clipping or overlap,
  fixed reachable header controls and bottom content reachable by scrolling.
- Navigation/input: Up/Back/Close, parent scroll, keyboard-first Back, monitor
  outside dismissal and no clickthrough preserved. Main controller/pad unchanged.
- Actual rendered phone and four monitor rotations, Fit and representative font/DPI
  variants as applicable. Use synthetic fixtures; captures stay private by default.
- After implementation, add behavior-focused acceptance, update concrete requirement
  checks and regenerate coverage. Record exclusions and visual/performance outcomes
  separately. No broad OS certification or screenshot golden is implied.

Unrelated existing policy edits and the prior promotion guide remain outside this
feature's intended change. No push or publication is requested by this UI task.

## Test findings and iteration

- Native guide checks construct all 13 topics at 240/360 dp widths and font scales
  1.0/1.3/2.0. This is native view measurement, not whole-app large-font certification.
  Actual phone and monitor interaction/capture checks are separate.
- Initial lint exposed drawing-time `RectF` allocations and missing Context-only
  custom-view constructors. Those causes were fixed; the lint baseline was untouched.
- The phone's installed app uses the persistent signing certificate. Default debug
  signing was rejected; development app/test APKs were signed with that existing
  certificate, with equal non-signature ZIP payloads verified. No app-data uninstall
  was used. An initial test-helper install was refused; after the user granted
  installation, both packages installed and native instrumentation ran.
- The initial narrow/200% guide assertion used `Layout.getLineRight`, a horizontal
  scrolling extent. On API 36 one line reported 627 px while its actual glyphs ended
  at 620 px within a 624 px column. API 35+ checks now use actual glyph bounds and
  TextView's start-overhang adjustment, retaining the same 2 px tolerance plus
  independent height/ellipsis checks. Production text was not changed to pass this
  assertion. See Android's [Layout reference](https://developer.android.com/reference/android/text/Layout)
  and [TextView reference](https://developer.android.com/reference/android/widget/TextView).
- Monitor assertions were reconciled with vector chevrons, split title/value labels
  and the existing workspace-operation status notice. The disclosure check still
  requires exact current text; it now includes the supplied operation notice rather
  than assuming no notice. These failures and diagnostics remain in private runs.
- Render review found guide capture names could refer to the preceding topic:
  `waitForIdleSync` did not ensure a submitted frame after programmatic scrolling.
  Guide captures now wait for three animation callbacks and require the requested
  diagram to be fully visible before PixelCopy/screenshot. The earlier captures
  remain retained but are not the current visual references.
- The additional workspace suite `20261112T142243.500660Z` failed after saving and
  cold-restoring a single test app at rotation 0. The task/display/axes restoration
  assertions passed, then `WorkspaceShortcutAcceptance.touchFixture` timed out on
  `settledInputWindow` before injecting a contact. Cause is not established and no
  assertion was weakened or product input code changed to mask it. This prevents
  a clean full-workspace-regression claim; its empty-state phone layout was inspected
  separately. The bounded menu-readability task does not claim to resolve it.

## Completed physical verification

The user's requested Xiaomi physical test used model `23127PN0CG` (Xiaomi 14)
running Android 16, with its actual 1920×1200 monitor and logical UI DPI 240.
Phone UI and four-rotation monitor/Fit acceptance pass after the capture correction;
diagnostics, capabilities, audio and non-destructive debugging suites also pass.
The additional workspace suite fails as recorded above. Host quality passes:
287 JVM cases, 203 tooling cases and 8 Windows skips; mappings, documentation and
privacy guards pass. See the [versioned result summary](../../testresults/0.18.0-menu-readability/summary.md)
for exact run IDs, timing, APK/source/reuse bindings and the verified private archive.

The author inspected current phone/monitor renders; the [reference index](../ui-references/menu-readability.json)
identifies 33 captures and a four-by-four monitor guide contact sheet. This is
scoped author inspection, not human pixel-golden or performance approval. Larger
fonts were measured in native guide views, not certified throughout the app.
Other OS/OEMs, full DPI/accessibility matrices and the unresolved workspace input
failure remain explicit gaps. The separate test APK was removed and the updated
development app left installed with its phone controller reopened. There is no
publication request; final commit/push/release reviews have not been claimed.
