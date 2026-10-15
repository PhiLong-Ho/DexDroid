# DexDroid design contract

Phone and monitor use one monochrome language. The service provides the same
state and actions; the monitor omits the phone touchpad. Use `ControllerStyle`
for shared components rather than copying color/spacing constants per menu.
This document describes current tokens, not a new visual redesign.

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
| Icon actions | Vector icon, meaningful content description; Close 48 dp target |
| Selection | Real checked state, white checked/gray unchecked; never color alone |
| Main controls | Two equal rows: Apps/Rotate/Scroll, then Stop-or-Start/Save app/Back |
| Status | One shared status card; monitor state slightly emphasized, current settings readable |

Phone menus fill the app window, keep header/Close fixed and scroll overflow.
Apps resizes around Android IME without hiding results or Close. Monitor menus
float with outside dismissal; they rotate/scale with the desktop and fit within
its bounds. Settings and Close are icon buttons in the established positions.
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
