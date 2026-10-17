# Plain menu headers

## Contract and preflight

User requests seamless plain icons in Settings and submenus on phone and monitor.
DX-UI-001 / FR-001 and the shared controller spec own the change. Spec/design were
updated before implementation. UI expert `/root/menu_header_ui` inspected shared
ControllerStyle and both header builders read-only: black disks come from
iconAction's shade-8 background against shade-18 menus. User explicitly authorized
the small styling correction; no placement/navigation decision is introduced.
Proposal: header-only transparent resting background, bounded interaction ripple,
24 dp white glyphs inside independent 48 dp targets, 8 dp title padding and single
line end ellipsis retaining full accessible text. Other icons retain their style.

Architecture impact: N/A. ControllerStyle still constructs each surface's views;
no state, IPC, lifecycle, input arbitration, persistence or ownership changes.
Existing component/sequence diagrams remain accurate. A header-only style helper
avoids changing unrelated touchpad/protection controls. No template workflow change.

## Acceptance and evidence plan

- Shared native assertions inspect rendered transparent background, icon bounds,
  accessible targets, title clearance, single-line ellipsis, disabled tint and bounded
  feedback masks. Actual pressed/focused animation captures remain outside this run.
- Existing phone UI/monitor suites preserve Up/Close, scroll, keyboard, rotations
  and Fit scenarios. Select the connected Android16 monitor device sequentially.
- Required host quality/tooling/coverage/requirements/docs checks; record actual
  outcomes and exclusions before claiming completion. No release is requested.
- No approved per-state catalog/golden set exists in this repository; QUALITY.md
  explicitly records this gap. Compare actual captures with DESIGN and historical
  agent-inspected captures, never silently promote them to approved goldens.
- Preserve prior dirty release/tests/docs. Android16 handoff/pane-picker failures
  remain unrelated blockers; this correction does not approve or release them.

## Recorded outcome

Host qualityCheck passes (140 JVM tests, zero failures/errors/skips); 126 tooling
cases pass. Requirements/coverage/documentation/Ruff and diff checks pass.
Android16/Samsung SM-S928B (1080x2340 phone, 450 DPI, font scale 1.0), external
1920x1200 at 240 UI DPI: phone UI run 20261017T124708.727016Z passed in 26.821s;
monitor run 20261017T124750.436474Z passed in 105.327s on the identical APK pair.
These are 2 selected suite outcomes, not 2,865 independent scenarios. Existing
suites exercise navigation, keyboard, scroll and rotated monitor/Fit behavior.

UI reviewer `/root/menu_header_ui` explicitly approved inspected phone Settings,
rotation/scroll/Layout, nested app settings/rotation/diagnostics/setup/guide,
confirmation and Apps+IME; monitor Controls/Settings/rotation/scroll/display/Apps,
app rotation, app settings in all four rotations and selected guide/setup/log states.
Settled native phone captures supplement the instrumentation snapshots. An apparent
missing-X issue in the full-height image preview was disproved by exact-pixel header
crops: all four raw settled images contained the same 501 white X pixels in its
fixed region. Preserve both originals and crops; no product fix or passing-test
rerun was justified. Faint controller imagery in some initial nested snapshots
was not treated as settled opacity proof. Final native app-settings header is crisp.

[Evidence and limitations](../../testresults/README.md)
record hashes, run scope and local-only archive retention. No human/golden approval,
full font/DPI matrix, pressed/focused frame timing, Android11 device rerun, or
hardware input/performance benchmark is claimed. Source/style ownership and
existing diagrams remain accurate. No reusable workflow changed, so the template
needs no companion update. No push, app release or publication approvals were
performed; unrelated blocked candidate changes remain preserved locally.

Final docs format check caught CRLF introduced by Windows text writes. SpotlessApply
normalized the three Markdown files; final qualityCheck passes. Both outputs are
retained in the final archive. App/test input hashes stayed identical, so device
evidence remains applicable without an unrelated rerun.
