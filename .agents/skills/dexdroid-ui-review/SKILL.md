---
name: dexdroid-ui-review
description: Review proposed DexDroid controls, navigation, layout, feedback or setup for usability before implementation, then present a concrete proposal and resolve material user decisions. Does not replace rendered acceptance or final code review; skip for changes without a user-facing interaction impact.
---

Read `docs/WORKFLOW.md`, `docs/DESIGN.md`, relevant `SPEC.md` and the owning feature
contract. Keep the user's stated outcome and earlier decisions authoritative.

For UI changes, create one focused UI expert agent with `fork_turns: none`. Provide
the requested outcome, affected entry points/surfaces, existing flow, relevant
contracts, technical constraints and actual captures when available. Label missing
or historical evidence; do not supply the implementer's preferred conclusion as
proof. Keep the advisory agent read-only unless a separately scoped task allows
proposal artifacts. This review precedes dependent UI implementation.

Have the expert compare complete tasks, not isolated buttons: actions and
discoverability, preserved capabilities, navigation/close/cancel/recovery, current
values and loading/error/disabled states, keyboard/focus/accessibility, touchpad
space and portrait-phone/rotated-monitor/Fit constraints. Flag likely latency or
layout tradeoffs without inventing measurements.

Present the user a recommendation with before/after actions, important states,
tradeoffs and unresolved choices. Use an annotated wireframe for placement or
navigation changes; a short text before/after suffices for an exact small fix.
Keep intentional surface differences explicit. Avoid heavyweight mockups for
every Cartesian configuration; actual verification follows `docs/QUALITY.md`.

The user chose to wait for material UI decisions. A precisely specified correction
or previously approved/delegated choice can proceed after the brief proposal.
If a material requirement is still unclear after expert review, ask the user
through the available clarification channel and wait before dependent work.
Continue independent work. Silence and the expert's preference are not a decision.
Do not request the same approval twice or introduce speculative approval gates.

Record the expert identity/scope, proposal, user decision or exact authorizing
instruction, unresolved items and resulting acceptance criteria in the existing
change record. Reconcile spec/design before code. Re-review material departures
from the chosen flow. Proposal acceptance is separate from actual rendered review,
golden approval and measured performance, and never substitutes for either of the
two fresh final code reviews.
