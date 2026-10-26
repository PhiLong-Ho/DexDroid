# DexDroid design contract

Phone and monitor use one monochrome language. The service provides the same
state and actions; the monitor omits the phone touchpad. Use `ControllerStyle`
for shared components rather than copying color/spacing constants per menu.
This document describes current tokens, not a new visual redesign.

Settings → Workspace shortcut sits beside App settings on both surfaces. Its
shared scroll body has current summary, Save/Update, saved summary, Add to phone
Home screen, Clear, replacement/content warning and live operation feedback.
Actions are stacked; values wrap and update without rebuilding the page or
resetting scroll/focus. Save is disabled for empty/unstable/unknown occupancy and
transitions. Pin requires a saved snapshot and reports requested versus confirmed
honestly. Main controls and touchpad retain their space. One explicit shortcut
tap authorizes replacing the current arrangement; no repeated confirmation is added.
The phone launcher may add the icon immediately or ask for confirmation. Pin feedback
points to Home pages and manual app shortcut permissions if no icon appears. Both
launcher labels read "Restore workspace"; optional OEM paths belong in User guide.
Before changing the interaction, use the [UI preflight](WORKFLOW.md#before-implementation)
and [UI review skill](../.agents/skills/dexdroid-ui-review/SKILL.md). Present the expert's
concrete task/layout proposal before implementation and wait for material user
decisions. Exact authorized corrections may proceed after a brief review. A
proposal is not rendered acceptance, a golden approval or performance evidence.

When a requested design leaves placement, grouping or action accessibility
ambiguous, clarify with the user before choosing the dependent layout unless the
user explicitly delegates that choice. Preserve useful actions when removing a
shortcut; confirm their new home if it is unclear. Group a help icon with the
label it explains and balance opposing header actions around the centered group.

| Token/component | Current contract |
| --- | --- |
| App canvas | Near-black `#080808`; native monitor content may belong to another app |
| Panel/card | `#121212`, white foreground; ForceDark disabled for shell panels |
| Panel border/radius | `#2D2D2D`, 1 dp stroke, 22 dp radius |
| Option row | `#282828` border, 8 dp radius, at least 62 dp height |
| Panel padding | 18 dp horizontal; 12 dp top, 18 dp bottom |
| Row spacing/padding | 3 dp vertical margins; 12 dp horizontal, 10 dp vertical padding |
| Menu title | 22 sp; phone fixed 54 dp header |
| Row title/subtitle | 14 sp white; 12 sp `#9B9B9B` subtitle |
| App row | At least 72 dp; 36 dp app icon, 12 dp text gap |
| Search | At least 50 dp; single line; rounded row surface |
| Menu header icons | Plain 24 dp white vector, transparent resting fill, no circle/border; bounded ripple/focus, meaningful content description and independent 48 dp target |
| Selection | Real checked state, white checked/gray unchecked; never color alone |
| Main controls | One equal row: Apps/Rotate/Layout/Audio; desktop action in status, App settings in Settings |
| Status | One shared status card; monitor state slightly emphasized, current settings readable |

Phone menus fill the app window, keep header/Close fixed and scroll overflow.
Apps resizes around Android IME without hiding results or Close. Monitor menus
float with outside dismissal; they rotate/scale with the desktop and fit within
its bounds. Header Back, Settings and Close are plain icons in the established positions.
Titles have 8 dp horizontal padding between icon targets, one line with end
ellipsis and their complete accessible text. Keep a fixed 54 dp header and
48 dp icon targets; do not reclaim touch space when removing the visual fill.
There is no persistent dock over an external app. Match geometry and state, while
preserving these intentional surface differences.

Verify state changes immediately on both surfaces. All toggles show their current
value. Save/reset/one-time app rotation use the same profile rules. No text may
show encoding fragments, replacement glyphs, clipping or accidental emoji in
place of native icons. App-provided icons/content are outside our monochrome rule.

For every UI change inspect actual screenshots for both surfaces: default/empty,
selected and disabled states, long app names, populated/empty search, keyboard,
scroll bottom, portrait phone and four monitor orientations. Include Fit and DPI
variants where layout changes. Assert bounds, hit targets, selected state and
outside-tap behavior in instrumentation. Pixel comparisons add regression evidence;
they do not verify interactions or replace human visual review.

Animations must settle without lingering overlays, duplicate navigation or stolen
app touches. Document the intended duration/easing in a changed component rather
than changing it independently on each screen. Performance-sensitive changes need
timings under the controlled setup in [QUALITY.md](QUALITY.md).

Protected-screen guidance uses a compact monochrome notice with native Info/Close
icons on the otherwise blank monitor, plus an Info action in the phone status card.
It never automatically replaces an open menu. Help follows the shared Settings
panel/scroll/header pattern on both screens and is also available in Settings.
Dismissed notices remain hidden until protection clears and a new episode starts.

Split view adds Control panel > Layout on both surfaces with shared icon rows, current
values, a 25-75% slider and equal/one-third/two-thirds presets. Single view shows
one Open app target; split shows two spatial targets side by side or stacked,
labeled Open left/right or Open up/down, mapping actual positions after Swap.
Targets directly open their pickers. Hide split-only settings while single view
is active. The main Apps action directly opens a combined launcher overlay with a
Single / Left-right / Up-down selector and one search/list. Each choice applies
immediately: split directions enable and reveal both panes (also from fullscreen),
and Single ends split. No second enable toggle is required. Repeating the current
visible layout does not resize it. Split rows have two
48 dp minimum spatial launch actions; single rows retain normal tap/hold behavior.
Opening the launcher or changing its mode does not resize the main controller
or touchpad. Advanced layout controls stay
in Layout. Picker Up and system Back return to Layout when opened there;
Close dismisses the whole flow, including nested pickers. Successful launch dismisses the menu.
Pane selection is shown in status/menu values without a permanent app outline.
Fullscreen destination feedback covers the resulting full output. Press/cancel
does not resize or change selection; launch reveals the hidden fullscreen pane
and opens its app after successful resize, in the same action.

Only a pressed launch action softly highlights its destination (white at low
opacity, thin gray border), below menu panels; release/cancel/replacement clears it.
Empty backgrounds redraw within current pane bounds through direction/ratio/swap
and fullscreen transitions, including mixed empty/occupied panes. Hide the decorative
caption if it would overlap/crowd the centered Apps icon or exceed available width.
Mode completion refreshes launcher rows and layout; verify actual contrasting text
pixels in addition to view strings and hit regions.
The gray divider is 2 logical dp, with a centered 12 dp minimum grab band.
Empty panes reuse IdleDesktop's gradient, sparse corner lines, title and centered
Apps icon with the same single-view styling. Menus remain workspace-level overlays and dismiss outside;
no permanent controls cover a running app. App pickers name their destination
pane. Both apps use workspace orientation, preserving saved single-view choices.
Protected-screen guidance is confined to a visible protected pane.

Phone touchpad header has a 48 dp Scroll action at the left, a centered group of
Touchpad followed immediately by its 48 dp Info action, and a 48 dp remote Back
action at the right. Center the complete label/help group between the balanced
edge actions, rather than centering the label alone. The header consumes its own contact
sequences independently from pad gestures. Save/Reset app and a Scroll shortcut live
in Settings > App settings on both surfaces; desktop Start/Stop lives in status with a
plain white stateful icon with no resting fill, circle or border; retain its
independent 48 dp target, bounded ripple and accessible action name. Keep Settings in the app header.

Menu navigation follows [Android navigation principles](https://developer.android.com/guide/navigation/principles):
leading Up/Back returns one visited valid menu; a separate trailing X closes the
whole modal flow. Root menus have X only. System/hardware Back first hides an open
keyboard, then returns one level; a root dismisses. Outside/Fit dismissal closes the
whole monitor flow without click-through. Preserve parent Settings scroll and Apps
query. Main shortcuts start a fresh root. Selecting Monitor orientation or Pointer
speed returns to the parent after applying the value. Multi-setting/action pages
(Scrolling, Layout, Settings and App settings) stay open for successive changes;
Up/Back returns when finished. Use the same rule on both screens and skip
invalidated picker destinations.

Status cards start collapsed: monitor state and desktop Start/Stop share the top
row. Remove the triple-dot app-options shortcut. The status body is the shared
expand/collapse target, at least 48 dp high, with keyboard/accessibility actions.
Start/Stop and exceptional buttons consume their own contacts. A secondary-text
"Show details" / "Hide details" cue sits directly beneath and leading-aligned with
the monitor summary, inside the existing header. It shares the status-body target;
there is no chevron, separate disclosure button or extra action row. Start/Stop is
the sole trailing icon. A normal collapsed card fits within 80 dp; long names and
enlarged text may grow. Keep readable contrast, focus feedback and accessible
expansion state. This exact text-cue proposal was approved after UI expert review.
Workspace restoration may prefix the collapsed cue with a short semantic notice
and " · "; active progress remains visible and the terminal notice lasts 10 seconds.
Expansion still reads "Hide details" and retains the full last shortcut result.
This uses the existing cue and target, without adding a control or action row.
Announce changed restore phases/results politely, not periodic status refreshes.
Expansion is a shared remembered preference. Details hide without blank space.
Keep/Revert, protection and Shizuku
recovery actions remain visible when needed. Shared offline User guide and
Shizuku setup pages use Settings typography/cards, fixed Up/Close and scrollable
content. External setup launches are explicit user actions on the phone.

Turn off debugging is a separate shared Settings page beside Shizuku setup.
Keep ordinary Stop and the main row unchanged. Explain the three switches and
other ADB/Shizuku app interruption before an explicit Stop & turn off confirmation.
Cancel/Up/Close before confirmation do not mutate settings. Pending disables
confirmation and duplicate startup; results remain on the phone after monitor
teardown. Per-switch observations distinguish Off verified, Still on and Could
not verify, with last-attempt time and separate daemon status. Manual phone
Settings opens the phone's main Settings, never a hidden Developer options
shortcut or automatic permission grant. Keep manual switch checks generic and
Use the desktop again collapsed initially; its shared Settings-style disclosure
exposes Expanded/Collapsed state and hides detail without blank space. Named OEM
examples belong in the guide, not the default shutdown instructions.

Diagnostic log stays in Settings with the same plain Up/title/Close header on both
screens. Shared content order is current context, Copy log / Share log / Clear log
Settings rows, feedback, then selectable 14sp history. Actions precede long history;
no additional main-screen shortcut or collection toggle. Share explicitly says it
opens Android sharing on the phone. Current context is separate from old events.
Refresh only changed text and defer history replacement during selection; retain
scroll. Clear keeps context and explains that new events may appear.
