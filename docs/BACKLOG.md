# Lightweight product backlog

Updated 2026-10-28. Planning and scoped development status; no release commitments or new acceptance
claims. The [baseline spec](../SPEC.md) remains the current behavior contract.

## Product direction

Expand native Android apps onto external monitors with predictable rotation,
layout and input. Favor a lightweight daily-use tool for non-root phones, including
Android 11, rather than a Linux/terminal workstation. Battery cost, setup friction
and ongoing OEM maintenance count against a feature's value.

User feedback: Extend's UI is cumbersome and its Android 15+ dependencies weaken
its fit for this audience. MagicDesk appears aimed at a broader workstation; the
user found no rotation option. These are positioning inputs, not claims that all
competitor functionality requires Android 15 or that rotation is absent everywhere.
Private smoke tests found Extend rotation working on Android 15/16 and imperfect
MagicDesk native window geometry on one HyperOS build. Do not generalize those
observations into a cross-device comparison or publish the personal test captures.

## Ranked delivery order

Effort means focused solo development days including spec/design, implementation,
targeted tests and review. These are rough planning ranges, not elapsed-time
promises; physical-device availability and OEM failures can expand them. Value is
relative to DexDroid's target users. Within the first tier, take the quick win first.

| Rank / ID | Feature | Value | Effort | Smallest useful delivery |
| --- | --- | --- | --- | --- |
| 1 / BL-01 | Improve deep diagnostic logs | High when troubleshooting; occasional use | Small: 1–2 days | Keep in Settings; readable errors, relevant session context and explicit copy/share with privacy-safe contents |
| 2 / BL-02 | Saved workspace shortcut first; broad automation deferred | High for repeat setups; broader demand unproven | Bounded shortcut in progress; broad API estimate 4–7 days | Explicitly save current apps/layout, restore from one Home icon |
| 3 / BL-03 | Read-only capability report | High for setup and support | Medium: 3–5 days | Explain current display, Shizuku and feature availability, with actionable reasons and unknown states |
| 4 / BL-04 | Multiple physical monitors | Medium; valuable to a smaller hardware-equipped group | Selector: 2–4 days; two active outputs: 3–6 weeks | First assess monitor selection/reconnect; independently active outputs are a separate larger phase |
| 5 / BL-05 | Refresh-rate selection | Low for the current audience | Spike: 0.5–1 day; delivery: 2–4 more days if viable | Offer supported physical modes only, with preview/rollback and actual-mode feedback |

BL-02 is the highest-value new capability; BL-01 leads delivery because the log
already exists and gives users/support better visibility during subsequent work.
BL-02 and BL-03 should share a small status vocabulary, without making a complete
cross-OEM capability framework a prerequisite for automation.

BL-01 is implemented in the 0.15.0 candidate; see its
[change and scoped acceptance](changes/diagnostic-log.md). Publication status is
separate from this planning order. The user narrowed BL-02 to one
[saved workspace shortcut](specs/saved-launch-shortcut.md), now a 0.16.0 candidate;
general automation remains deferred until demand justifies it.

BL-03's minimal read-only report is implemented in the current development tree;
see its [contract](specs/device-capabilities.md) and [change/verification record](changes/device-capabilities.md).
It reuses ordinary operation observations and bounded metadata reads. Guided tests
and broad cross-device certification remain deferred; publication is separate.

## Scope and completion signals

- **BL-01 — Diagnostics:** Extend the existing 250-event in-memory log and
  Copy/Share/Clear flow ([contract](specs/diagnostics.md),
  [ownership](architecture/features/diagnostics.md)). Audit current usability
  before adding controls. Show operation, outcome/reason and relevant display/
  session state; distinguish dispatch from app response. No typed text, URLs,
  screenshots or unbounded/persistent collection by default. Done when a power
  user can find, interpret, copy and clear a safe failure record on either screen.
- **BL-02 — Automation:** First deliver the scoped saved workspace shortcut;
  its spec defines current acceptance. The following API work is deferred.
  Reuse session commands rather than duplicate their
  behavior. Approve the caller authorization/consent model in architecture review
  before exporting endpoints. Use explicit desired values rather than toggles;
  validate inputs, serialize bounded work, report success/failure/pending and
  reject stale targets. Document runnable Tasker and MacroDroid recipes. Do not
  expose arbitrary shell commands, automatically enable debugging, or automate
  the debugging-off action in the first delivery. Next increment: apply profile,
  layout/pane launch and audio; then events for connection/readiness/errors. Done
  when authorized recipes work with the phone controller backgrounded and denied
  callers/missing Shizuku/disconnection produce understandable outcomes.
- **BL-03 — Capability report:** Prefer live Android reports and bounded checks
  over an OEM/model allowlist. Distinguish supported/reported, verified in this
  session, unavailable and unknown. Include physical modes vs virtual resolution,
  logical DPI vs reported physical DPI, current route/input/permission state and
  reasons/actions where reliable. Stay read-only; disruptive probes require an
  explicit future design decision. Done when users can understand a failed setup
  without treating an untested capability as supported or unsupported.
- **BL-04 — Multiple monitors:** First define selection and reconnect behavior
  for multiple detected physical monitors, building on existing named profiles.
  This is separate from two split panes on one monitor. Before two active outputs,
  review per-monitor session/display ownership, keyboard/pointer focus, profiles,
  disconnect cleanup, shared audio policy and resource limits. Test with real
  compatible multi-output hardware; USB-C bandwidth/dock/phone support cannot be
  supplied by the app. Defer active multi-output until hardware and demand justify
  it. Done per phase: correct target/reconnect for selection; independently usable
  output and input on both monitors for the larger phase.
- **BL-05 — Refresh rate:** Time-box feasibility before implementation. Confirm
  whether app mode requests actually change this device's external physical mode;
  render FPS and virtual-display rate are not proof. Expose only OS-advertised
  modes, show unknown/unavailable honestly, preserve resolution/orientation and
  restore the prior mode on timeout/failure. Keep default behavior and make any
  battery/bandwidth tradeoff explicit. Defer if the mode request is ineffective
  or hardware coverage is unavailable; no custom timing or forced overclocking.

## Picking up work

Choose one small delivery, update its spec and stable requirements first, then
follow UI/architecture preflight and targeted acceptance. Re-estimate after source
inspection; test only affected paths plus required candidate checks. No exhaustive
OS/OEM matrix is implied. Record actual hardware gaps and seek community coverage.

Architecture impact of this planning change: N/A. It adds no runtime behavior,
exported interface, persistence or diagram change. Automation and multi-output
implementation require fresh architecture experts; user-facing changes require
the usual UI expert and material design decisions before implementation.
