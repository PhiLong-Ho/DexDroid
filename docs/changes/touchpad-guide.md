# Change: Illustrated touchpad help

The user requests that the main-screen Touchpad gesture guide become less text
heavy, matching the existing menu style. Preserve the authorized dirty
menu/search/pointer work. No release or publication is requested. Agent OS is off;
communicate in chat without dashboard plan/review/document calls.

## Contract and preflight

FR-001 / DX-UI-001: retain the one-tap phone Info entry and real gesture meanings,
replace the prose wall with five passive sketches/short native labels, separate
brief Scroll/Diagnostic log/pointer-idle guidance and preserve easy dismissal.
Use the existing shared monochrome reading/menu tokens, with accessible plain
Close and scrollable overflow. No monitor touchpad guide is introduced.

Architecture impact: **N/A, local presentation**. Source inspection of
`MainActivity.touchpadHelp`, `ControllerStyle` and `GuideDiagram` shows a static
help dialog opened after Pad cancellation. Reuse the existing static sketches and
native dialog dismissal; do not change service/input contracts, timer ownership,
navigation state, persistence, IPC, dependencies or lifecycle policy. The owning
controller overview already maps shared reading blocks and passive diagrams.
Overview and sequence gain the existing local help route without changing these
boundaries. No architecture expert is required for this local correction; a release would
still require its separate fresh architecture review.

Fresh read-only UI expert `01a1d0ab-f6ba-7f91-a3b3-5067f510cf07` approved the
compact scrollable dialog approach: reuse the existing five sketches, three short
notes and fixed plain Close header; preserve native dismissal and avoid unrelated
Settings navigation/confirmation controls. It found no material user choice beyond
the explicit requested presentation correction. The concrete proposal was reported
in chat before implementation. Collaboration creation hit the session thread limit;
the independent CLI reviewer recovered from initial Windows read-only shell denial
and inspected sources through its read-only file tools. This is an advisory
preflight, not runtime, rendered or final publication approval.

## Implemented and checked

MainActivity retains the native help dialog and Pad cancellation, reusing the
five GuideDiagram rows and shared reading/menu tokens. Three brief tips replace
the trailing prose. The fixed plain Close has a 48 dp target; the body scrolls.
No menu-history or service state is added. README, spec, design, architecture
overview/sequence and requirement coverage are reconciled; onboarding is unchanged.

Final-product `spotlessApply qualityCheck` passed in 12 seconds. A final
native-test-only layout-readiness guard was compiled/formatted with qualityCheck
again in six seconds. All 295 JVM cases pass; tooling has 203 passes/eight skips.
The existing native help scenario now checks all gesture/tip labels, accessible
Close bounds, scroll/header separation and icon dismissal. Its suite entry remains
the concrete coverage mapping; the private helper is not an independent test case.

The current app was installed in place on Xiaomi 14 Android 16 with its monitor
attached. Manual ADB UI checks pass content, Close's 48 dp bounds, native phone
Back, outside dismissal without toggling underlying status, and reopening. Author
inspected the actual portrait guide and both changed architecture diagrams. An
initial Mermaid note delimiter parse failure was corrected and its log retained.
The guide is left open for user review. No human screenshot-golden approval or
performance certification is claimed.

Instrumentation was not retried after the preceding pointer task's two Xiaomi
test-helper install rejections; the helper remains absent. Current native code
compiles but has not executed. Small-screen/enlarged-font/TalkBack variants and
app-input-count isolation remain unverified. Monitor menus were unchanged, so no
four-rotation/Fit rerun is claimed for this phone-only presentation correction.
[Evidence](../../testresults/0.18.0-touchpad-guide/summary.md) and
[current reference](../ui-references/touchpad-guide.json) bind scope to private
captures/source/build hashes. No raw evidence upload, commit, push or release.
