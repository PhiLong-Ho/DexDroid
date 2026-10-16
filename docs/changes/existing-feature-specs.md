# Change: Existing feature contracts and reproducible test instructions

## Contract (write before implementation)

- Requirement: DX-DOC-001 / NFR-004; preserve all existing FR/NFR/DFR and DX IDs.
- Readers can find the spec for an existing feature, its observable scenarios,
  concrete check mappings and remaining gaps before making the next change.
- Readers can reproduce host/device checks with an explicitly selected device,
  understand suite prerequisites and locate actual logs/captures.
- Baseline updated first: maintained status, feature-index/runbook entry points
  and NFR-004 documentation obligations. No new runtime or compatibility claim.

## Implementation

- Nine retrospective Spec Kit-style contracts plus an index cover the existing
  session, launcher/catalog/handoff, input/navigation/search, display/app settings,
  shared UI/version/icon, monitor profiles, protected-content, diagnostics and
  verification/release behavior. The existing split/prototype specs remain linked.
- The index assigns one owning contract to each of all 43 baseline/DX entries;
  the complete baseline remains authoritative and generated coverage owns detailed
  test mappings/gaps. Deferred direct phone gesture mode stays deferred.
- `docs/TESTING.md` documents existing commands, first-use Shizuku authorization,
  wireless serial selection, suite scope, installed APK hash checks, raw output,
  visual/performance review and host-only CI. The current 1920x1200 fixture
  assumptions and absence of automated emulator provisioning are explicit.
- README remains concise; contributor and quality entry points link the new docs.
- No app, test, pipeline or runner implementation changed. No source formatting
  refactor, lint baseline, APK version, device installation or publication change.

## Acceptance (update after implementation)

Evidence: [local documentation validation](../../testresults/0.12.0/spec-backfill/README.md).

| Criterion/scenario | Check | Result/evidence scope |
| --- | --- | --- |
| All 43 baseline/DX entries have one indexed owner | Ownership/section audit | See versioned audit result |
| New specs retain template sections and link concrete gaps | Section/link audit and documentation validator | Documentation only, no app verification |
| Instructions match actual runner, suites and CI | Read script, instrumentation entry points, Gradle and workflows | Current 1920x1200 assumption disclosed |
| Formatting/build/lint/JVM and tooling remain valid | `qualityCheck`, tooling discovery, Ruff, requirement/coverage/doc validators | See actual command logs |

- Base/source: local branch `chore/existing-feature-specs`, base `ea7a31f`.
  This record is written while uncommitted; final canonical candidate binding is
  in the actual review record and Git commit.
- Device suites, screenshots and performance: not rerun; documentation-only change.
  No changed Android UI, input, lifecycle or test source requires a new device run.
  Earlier device results remain historical for this documentation candidate.
- Requirement criterion/report reconciled; test source fingerprints are unchanged.
- Final candidate requires two fresh independent approvals under `docs/REVIEWS.md`.
  No PR, CI workflow run, push or release is attested here; no release report is needed.
