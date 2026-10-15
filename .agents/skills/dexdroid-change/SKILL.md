---
name: dexdroid-change
description: Implement or maintain DexDroid using its spec-first workflow, requirement coverage, shared phone/monitor design and evidence. Use for feature, bug-fix, test, tooling, documentation and release changes in this repository.
---

Read the repository's `AGENTS.md` and relevant current `SPEC.md` contract.
Use `docs/ARCHITECTURE.md` to locate ownership and `docs/DESIGN.md` for UI changes.

Update the spec before code; map observable acceptance criteria to stable IDs
and scenario gaps in `docs/requirements.json`. Implement using the shared style
and service state. Then add/update behavior-focused acceptance/E2E tests.
For EVERY test addition, edit or deletion, reconcile concrete test references in
`docs/requirements.json`, run `python tools/coverage.py update`, inspect and commit
`docs/coverage.md` and `docs/coverage-snapshot.json` with the change. Run
`python tools/coverage.py check`; preserve explicit manual/visual/performance gaps.
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

Before ANY push or release, follow `docs/REVIEWS.md`: stage the completed candidate
and create exactly two fresh independent agents with no inherited conversation
(`fork_turns: none`). Each reviews the spec, diff, tests and evidence independently.
Both must explicitly approve the same candidate digest. Resolve findings and get
two new reviews after any content changes; never infer approval from silence.
Record actual identities, scope, verdicts and conclusions in
`reviews/approvals.json`, stage it, then run `python tools/review_gate.py check`.
Only both approvals plus passing checks permit the authorized push/release.
The approval record alone is excluded from the candidate digest. Other edits
invalidate both reviews. If agents are unavailable, finish local work and report
that publication is blocked by this explicit project rule.

Format through Spotless. Never regenerate the lint baseline merely to make a
check green. Preserve UTF-8 explicitly. No deployment, publication or Goal is
implied solely by invoking this skill; follow the user's authorized task scope.
