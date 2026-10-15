# Change: requirement coverage and two-agent publication review

## Contract (written before implementation)

NFR-006 / DX-COVERAGE-001 requires concrete test mappings, scenarios, gaps and a
coverage refresh with every test source addition/edit/deletion. NFR-007 /
DX-REVIEW-001 requires two actual fresh independent agents to approve the same
completed candidate before pushing or releasing. SPEC story 8 and SC-011/012
define observable acceptance. DX-MAINT-001, DX-DOC-001 and DX-RELEASE-001 are affected.

## Implementation

`docs/requirements.json` now includes concrete checks. `tools/coverage.py` expands
them into a [report](../coverage.md) and fingerprints all Java/Python test sources,
including helpers. Native run/onStart entry points remain aggregate checks;
scenario dimensions and partial/manual/deferred gaps are not claimed as executed.

`tools/review_gate.py` hashes canonical staged/committed Git contents, excluding
only its approval record. The local pre-push hook validates actual pushed refs;
Quality CI checks the head candidate, and both release jobs check the tagged
candidate before signing/publication. [Review protocol](../REVIEWS.md), AGENTS,
project skill and contribution/release instructions require actual fresh agents.
Attestations cannot cryptographically authenticate agent identity or independence.

No Android behavior or visual design changes; only version metadata advances to
0.10.1 / 16 for the authorized release. No lint baseline changes.

## Acceptance (after implementation)

| Criterion | Check | Evidence/result |
| --- | --- | --- |
| Test edits/additions/removals require current mappings/inventory | CoverageTests | PASS |
| Missing entries, unmapped tests and stale report fail | CoverageTests | PASS |
| Explicit scenarios/gaps remain separate from execution | CoverageTests and report | PASS |
| Missing/duplicate/rejected/stale approvals fail | ReviewGateTests | PASS |
| Both actual pushed branch/tag candidates are checked | ReviewGateTests | PASS; annotated tag and real local push |
| Two real independent reviews precede publication | Actual agent reviews | Recorded only after both approve in the approval record |
| No unintended Android regressions in host checks | qualityCheck | PASS; 72 JVM cases, lint, format, app/test builds |

Full commands, environment, 68 tooling results and raw logs are in
[versioned host evidence](../../testresults/0.10.1/runs/20261015T053015Z-coverage-review/README.md).
Candidate digest and actual reviewer conclusions are in `reviews/approvals.json`.
The local hook is installed for this clone. Fresh device, visual and performance
verification remain unrun: this change affects host maintenance/publication only.
No new pixel baseline or numerical performance claim is approved.
