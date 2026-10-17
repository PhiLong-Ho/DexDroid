# Change: Combined launcher and stable split backgrounds

## Contract (before implementation)

- DX-WINDOWS-001 / FR-031: Apps opens one combined searchable launcher directly.
  Single / Left-right / Up-down changes mode in place; split app rows launch to
  explicit spatial destinations. Preserve the main grid and full touchpad bounds.
  Highlight only a pressed launch action; release/cancel removes it.
- Apps opened from Layout returns there on header/system Back; root Close dismisses.
  Successful app launch closes the launcher. Nested orientation returns to Apps.
- DX-WINDOWS-003 / FR-033: empty backgrounds follow changing pane bounds through
  direction/ratio/Swap/fullscreen/rotation, including mixed occupancy.
- Preserve session tokens, input arbitration, display cleanup, durable settings,
  loading/search/keyboard and shared phone/monitor style.

## Implementation and acceptance

- `LauncherControls` shares mode buttons, spatial app rows, native pressed-state
  feedback and scoped choice ownership. Mode revisions reject callbacks from old
  rows after in-place configuration; obsolete picker callbacks cannot adopt a new token.
- First independent review rejected a lifetime hole: pending mode completion could
  reacquire a new-generation token after Stop. Completion now validates the origin
  generation/display, latest picker serial and expected workspace/mode. Stop,
  failed mode changes and any newer picker leave the old launcher inert, including
  after the newer picker closes. Real-device acceptance stops a pending phone mode
  operation and exercises replacement ownership before completion refresh.
- A subsequent independent review found hidden-fullscreen destination capture
  started a resize before launch, causing short taps/accessibility clicks to fail.
  Launcher capture no longer selects/resizes. A completed launch reveals the target
  fullscreen pane and launches once from the successful configuration callback;
  generation/workspace guards discard it after Stop/failure. Native acceptance covers
  both surfaces, normal-order accessibility/keyboard clicks, swapped-order short
  widget taps, press/cancel without selection/resize, actual task placement/input,
  and Stop during the accepted pending launch. Fullscreen feedback covers the output.
- The original main grid/touchpad geometry is unchanged. Main Apps opens the picker
  directly. Search, catalog loading and keyboard paths remain on their respective
  native/shared surfaces. Header/system Back return to Layout when opened there;
  orientation Back returns to Apps and a completed launch dismisses the picker.
- Background regression reproduced: after resize/Swap, native buffers and divider
  used new bounds while decoration/background frames retained previous measurements.
  Explicit child sizes alone were insufficient. Decoration and visible idle canvases
  now participate in the same pre-draw geometry fence as the native textures, using
  staged dimensions, DPI and rollback layout. Hidden/GONE canvases are not awaited.
- Native acceptance checks one-list mode changes, spatial launch buttons, swapped
  destinations, stale row/token rejection, pressed/released/cancelled feedback,
  parent navigation, exact touchpad bounds and both surfaces/four rotations.
- Empty backgrounds are checked through repeated ratio/direction/Swap/fullscreen
  transitions with actual child/parent bounds, centered icons and captured pixels
  compared to a fresh same-size IdleDesktop; mixed occupancy is checked separately.
- Final completion refreshes launcher rows/layout and new captured-pixel checks verify
  actual mode/app label ink, beyond strings and hit targets. The apparent blank
  labels in a scaled preview were not reproduced in original full-resolution pixels;
  no production missing-label failure is claimed. Short panes now hide the decorative
  caption when it would crowd the centered Apps action.
- Current host outcomes: 128 JVM cases, 84 tooling tests and quality/requirement/
  coverage/documentation gates. Six final-build affected suites (split, UI, monitor,
  keyboard, input and idle) passed sequentially on Android 11 with identical product
  and instrumentation APK hashes. Earlier broad results used a previous product APK
  and are historical; the other five suites were not rerun after the final mode-lifetime
  fencing and hidden-fullscreen launch regression changes. See the [evidence record (evidence withdrawn)](../../testresults/README.md)
  for commands, hashes, raw attempts, rendered review, timings and exclusions.
- No lint baseline change, version/release bump or publication. Modern Android,
  physical peripherals, actual fault injection, exhaustive configurations, physical
  latency/frame pacing and final human/pixel-golden approval remain unverified.
