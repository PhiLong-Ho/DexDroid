# Change: PR-only protected main

## Contract

NFR-009 / DX-BRANCH-001 and SC-014 require PR-only changes to `main`, strict required
GitHub Actions `host` checks (including both candidate-bound agent approvals),
administrator enforcement, resolved conversations and blocked force pushes/deletion.

## Implementation

Applied [the checked-in protection configuration](../../config/github/main-protection.json)
through GitHub's API and independently read back the live state. `main` reports
`protected: true`. Existing Quality already validates two fresh approvals before
host checks, so requiring that job makes the review gate mandatory for PR merges.
Project instructions, skill and contribution/release protocol now require branches
and PRs. No Android runtime or version change; no release tag is needed for this
repository administration change.

Human approval count is zero because agent approvals are CI attestations, not
separate GitHub identities. PRs remain mandatory. The existing attestation and
administrator trust limits are documented in [the protection policy](../BRANCH_PROTECTION.md).

## Acceptance

- Live API configuration independently asserted: PR object present, strict `host`
  with GitHub Actions app ID 15368, administrators enforced, conversations resolved,
  force pushes/deletion disabled, branch protected.
- `qualityCheck`, tooling, coverage and documentation checks passed. No tests were
  modified; the requirement coverage map/report now includes the explicit live
  verification gap instead of claiming an automated rejected-push integration test.
- No direct push was attempted against live `main` as a test. The server settings
  and final policy PR demonstrate the intended flow without a destructive probe.
- Fresh two-agent approvals are recorded before pushing the policy branch.

[Live settings and host evidence](../../testresults/unreleased/runs/20261015T055458Z-main-protection/README.md)
retain the verified configuration, commands and remaining limits.
