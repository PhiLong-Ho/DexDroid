# DexDroid development workflow

Read `SPEC.md` for product behavior, `docs/DESIGN.md` for shared UI rules,
`docs/ARCHITECTURE.md` for ownership, and `CONTRIBUTING.md` for runnable commands.
Use `.agents/skills/dexdroid-change/SKILL.md` for feature and bug-fix work.
Use `docs/templates/spec.md` (GitHub Spec Kit structure) for new feature specs;
keep the baseline's stories, numbered requirements and stable DX map reconciled.
README stays concise for setup/install/quick use; release changes go in CHANGELOG.
When user-facing controls or setup change, reconcile README's quick guide in the
same change; keep detailed instructions in the in-app User guide and feature docs.
Store logs/screenshots under `testresults/<version>/`, preserve raw evidence bytes,
and record run/configuration/review scope. Run `python tools/check_documentation.py`
after moving documentation or evidence.

## Change contract

`main` is protected. Start changes on a feature/chore branch and merge through a
PR only after its required `host` check succeeds. This check includes the
exact-candidate two-agent approval gate. Never bypass protection or push directly
to `main`; re-review if an update from `main` changes candidate contents.

1. Update `SPEC.md` first. Add or update stable requirement IDs and scenario gaps
   in `docs/requirements.json`. State behavior and observable acceptance criteria.
2. Implement against that contract. Reuse `ControllerStyle`, geometry and service
   state; phone and monitor controls must not invent different behavior/styles.
3. After implementation, add/update behavior-focused acceptance/E2E tests. Cover
   affected surfaces, four monitor rotations, Fit, input methods, keyboard and
   lifecycle states as applicable. Explain exclusions rather than pretending
   every Cartesian combination ran. Fix the cause; do not weaken tests to pass.
   Whenever any test is added, modified or deleted, reconcile concrete `checks`
   in `docs/requirements.json`, run `python tools/coverage.py update`, inspect
   `docs/coverage.md` and commit both generated coverage files with the test.
4. Run `qualityCheck`, tooling tests and requirement validation. Run applicable
   device suites for input/UI/lifecycle changes. Review rendered screenshots for
   UI changes; record visual approval and performance evidence separately.
5. Reconcile docs and record actual commands, commit/tree, device/configuration,
   results, artifacts and gaps. A past release or test mapping is not current
   verification. Unrun/blocked checks stay unverified.
   Every release also generates a Markdown release report and structured JSON
   from its own host outcomes/current coverage, retains a versioned copy, attaches
   both with checksums, and links the report to changelog/release/workflow evidence.
   Missing/invalid report evidence blocks publication; follow `docs/RELEASING.md`.
6. Before pushing or running a release, create exactly two fresh independent
   review agents (`fork_turns: none`). Give each the candidate diff, spec, tests,
   evidence and review instructions; do not give them the author's conclusions
   or the other review. Each must inspect and explicitly approve the final tree.
   Resolve blocking findings, rerun affected checks and create two fresh reviewers
   for any changed candidate. Never invent or reuse approvals for changed content.
7. Follow `docs/REVIEWS.md`: stage all candidate content, obtain its digest, record
   both actual agent identities/conclusions in `reviews/approvals.json`, then
   validate staged approvals. Only after both approve may you commit/push and
   trigger the authorized release. The pre-push hook and CI reject stale records.

## Code and evidence

- Explicit UTF-8 reads/writes; never round-trip source through a Windows code page.
- `spotlessApply` formats Java and Gradle; `spotlessCheck` enforces it. Do not hand
  tune formatter output. New lint findings fail; baseline changes need a concrete
  explanation in the change record. Do not regenerate it to hide regressions.
- Keep app behavior changes separate from large mechanical formatting/refactors.
  Favor small classes with one responsibility. Preserve display-ID, input
  arbitration, rollback and service lifecycle invariants during extraction.
- Prefer deterministic host tests for pure logic and real Android instrumentation
  for visible behavior. Use observable outcomes, not implementation-mirroring tests.
- Never silently approve screenshot baselines or claim all scenarios/performance
  passed from assertion counts. Update evidence only after inspecting results.
- Device suites mutate the desktop. Use the requested device, run sequentially,
  retain failure logs and restore the usable controller afterward.
- Create/activate a Codex Goal only when requested. Use bounded completion
  criteria; `docs/CODEX.md` contains examples. Repository files are durable policy.

## Code Review Rules

Flag missing requirement coverage for behavior changes, phone/monitor divergence,
unbounded input queues, stale display IDs, swallowed failure results, UI-thread
blocking, input/cursor regressions, and unsupported claims of visual/performance
verification. Formatting and lint belong to automated checks.

## Evidence privacy

Read docs/PRIVACY.md. Personal app/device captures and dumps stay private local;
testing does not authorize publication. Use synthetic fixtures for routine tests.
Run tools/check_privacy.py; public host input must pass its --evidence guard before
packaging/upload. Never restore withdrawn private evidence or merge tainted history.
Privacy cleanup overrides historical raw-evidence retention, not integrity checks
for retained records. History/protection exceptions require explicit user approval.
