---
name: dexdroid-change
description: Implement or fix DexDroid Android desktop features using its spec-first workflow, shared phone/monitor design, and real-device acceptance evidence. Use within the DexDroid repository for behavior, UI, input, or lifecycle changes.
---

Read the repository's `AGENTS.md` and relevant current `SPEC.md` contract.
Use `docs/ARCHITECTURE.md` to locate ownership and `docs/DESIGN.md` for UI changes.

Update the spec before code; map observable acceptance criteria to stable IDs
and scenario gaps in `docs/requirements.json`. Implement using the shared style
and service state. Then add/update behavior-focused acceptance/E2E tests.
Follow `docs/templates/spec.md` for new feature specs. Keep setup/quick use in
the concise README, release changes in CHANGELOG and evidence under
`testresults/<version>/`. Validate local links and archived hashes with
`python tools/check_documentation.py` when reorganizing documents.

Use `CONTRIBUTING.md` for host/device commands and `docs/QUALITY.md` for scenario
selection, visual review and performance evidence. Run applicable suites
sequentially on the selected device; a device gap is unverified, not a pass.
Preserve the two-screen/rotation/Fit/input/background invariants in architecture.

For UI, inspect rendered phone and monitor screenshots and record human review;
do not silently approve new pixel baselines. Performance-sensitive work needs
measured distributions, not await timeouts. Existing assertions count only for
the behaviors they actually exercise. Keep changes small and record actual
commands, artifacts, configuration, results and remaining gaps using
`docs/templates/change.md` for substantial work.

Format through Spotless. Never regenerate the lint baseline merely to make a
check green. Preserve UTF-8 explicitly. No deployment, publication or Goal is
implied solely by invoking this skill; follow the user's authorized task scope.
