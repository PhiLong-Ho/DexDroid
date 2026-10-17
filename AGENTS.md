# DexDroid development workflow

Read `SPEC.md` for product behavior, `docs/DESIGN.md` for shared UI rules,
`docs/ARCHITECTURE.md` for ownership, and `CONTRIBUTING.md` for runnable commands.
Use `.agents/skills/dexdroid-change/SKILL.md` for feature and bug-fix work.
Follow `docs/CODING_PRACTICE.md` for test stages, rerun decisions, evidence reuse
and candidate freezing; retain all required checks and two fresh final approvals.
Use `docs/templates/spec.md` (GitHub Spec Kit structure) for new feature specs;
keep the baseline's stories, numbered requirements and stable DX map reconciled.
README stays concise for setup/install/quick use; release changes go in CHANGELOG.
When user-facing controls or setup change, reconcile README's quick guide in the
same change; keep detailed instructions in the in-app User guide and feature docs.
Follow `docs/WORKFLOW.md` for UI preflight/user decisions and `docs/EVIDENCE.md`
for storage. Raw logs/screenshots stay in ignored versioned runs and verified
external bundles; commit concise summaries, archive descriptors/locations and
release reports. Do not newly commit raw trees or ZIPs, use `git add -f` for them,
or copy whole attempts into tracked feature folders. Existing history stays intact.
Record run/configuration/review scope and archive availability/retention. Run `python tools/check_documentation.py`
after moving documentation or evidence.

## Change contract

`main` is protected. Start changes on a feature/chore branch and merge through a
PR only after its required `host` check succeeds. This check includes the
exact-candidate two-agent approval gate. Never bypass protection or push directly
to `main`; re-review if an update from `main` changes candidate contents.

1. Update `SPEC.md` first. Add or update stable requirement IDs and scenario gaps
   in `docs/requirements.json`. State behavior and observable acceptance criteria.
2. For user-facing interaction changes, use
   `.agents/skills/dexdroid-ui-review/SKILL.md`: create a UI expert agent, review
   complete task usability/friction, and present a concrete proposal before
   implementation. Wait for material UI decisions; exact already-authorized or
   delegated corrections may proceed after a brief proposal. If any consequential
   requirement remains unclear after expert review, clarify with the user and
   pause only dependent work. Record the expert/decision and reconcile spec/design.
   Re-review material departures. This is separate from actual rendered acceptance
   and the two final code reviews; non-UI work does not require a UI expert.
3. Implement against that contract. Reuse `ControllerStyle`, geometry and service
   state; phone and monitor controls must not invent different behavior/styles.
4. After implementation, add/update behavior-focused acceptance/E2E tests. Cover
   affected surfaces, four monitor rotations, Fit, input methods, keyboard and
   lifecycle states as applicable. Explain exclusions rather than pretending
   every Cartesian combination ran. Fix the cause; do not weaken tests to pass.
   Whenever any test is added, modified or deleted, reconcile concrete `checks`
   in `docs/requirements.json`, run `python tools/coverage.py update`, inspect
   `docs/coverage.md` and commit both generated coverage files with the test.
5. Run `qualityCheck`, tooling tests and requirement validation. Run applicable
   device suites for input/UI/lifecycle changes. Review rendered screenshots for
   UI changes; record visual approval and performance evidence separately.
6. Reconcile docs and record actual commands, commit/tree, device/configuration,
   results, artifacts and gaps. Maintain the affected high-level architecture and
   feature-flow diagrams with readable Mermaid source and inspect their rendered
   output; keep `docs/ONBOARDING.md` current when setup/ownership/workflow changes.
   Archive raw evidence per `docs/EVIDENCE.md`, verify hashes, and give reviewers
   access before staging the compact descriptor/summary. A past release or test mapping is not current
   verification. Unrun/blocked checks stay unverified.
   Every release also generates a Markdown release report and structured JSON
   from its own host outcomes/current coverage, retains a versioned copy, attaches
   both with checksums, and links the report to changelog/release/workflow evidence.
   Releases also attach the current host evidence ZIP/descriptor with checksums;
   verify source/report/member bindings before publication. Missing/invalid report
   or archive evidence blocks publication; follow `docs/RELEASING.md`.
   Routine Actions artifacts contain only their current run, with stated retention.
7. Before pushing or running a release, create exactly two fresh independent
   review agents (`fork_turns: none`). Give each the candidate diff, spec, tests,
   evidence and review instructions; do not give them the author's conclusions
   or the other review. Each must inspect and explicitly approve the final tree.
   Resolve blocking findings, rerun affected checks and create two fresh reviewers
   for any changed candidate. Never invent or reuse approvals for changed content.
8. Follow `docs/REVIEWS.md`: stage all candidate content, obtain its digest, record
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

## Architecture preflight

Every task records architectural impact before implementation. Follow
`.agents/skills/architecture-review/SKILL.md`: source-linked system/component and
feature overview/sequence diagrams are required; possible boundary, ownership,
lifecycle, concurrency, persistence, shared behavior or delivery-contract changes
need a fresh architecture expert before dependent work. Uncertain impact needs
review; record N/A with reason for clearly local work. Resolve material unknowns
and re-review material departures. This role does not replace UI or final reviews.

## Code Review Rules

Flag missing requirement coverage for behavior changes, phone/monitor divergence,
unbounded input queues, stale display IDs, swallowed failure results, UI-thread
blocking, input/cursor regressions, missing UI expert/user decisions, outdated
architecture/feature diagrams, newly committed raw evidence or unavailable archives, and unsupported claims of visual/performance
verification. Formatting and lint belong to automated checks.

## Evidence privacy

Read docs/PRIVACY.md. Personal app/device captures and dumps stay private local;
testing does not authorize publication. Use synthetic fixtures for routine tests.
Run tools/check_privacy.py; public host input must pass its --evidence guard before
packaging/upload. Never restore withdrawn private evidence or merge tainted history.
Privacy cleanup overrides historical raw-evidence retention, not integrity checks
for retained records. History/protection exceptions require explicit user approval.

## Architecture approval for every release

Every new version candidate (patch, prerelease and docs-only included) needs a
fresh architecture expert after checks/evidence and before two final reviewers.
Task N/A never waives it. Follow docs/RELEASING.md#release-architecture-review:
record exact pre-attestation snapshot, actual reviewer/verdict, cumulative release
scope/findings/gaps; missing approval/blockers prohibit tag/push/dispatch/sign/publish.
Only faithful attestation may follow that inspected snapshot; final reviewers
approve its inclusion in the final canonical digest, with no new exclusions.
