# Change: <name>

## Contract (write before implementation)

- Requirement IDs:
- Trigger and expected observable result:
- Affected phone/monitor states and edge cases:
- Explicit exclusions and compatibility limits:
- Spec sections updated:
- Test plan: focused iteration checks, completed-candidate gates and exclusions:

## UI preflight (before dependent implementation; N/A for non-UI changes)

- Expert identity and reviewed request/current evidence:
- Recommended before/after task, sketch when needed, states and tradeoffs:
- Material questions and user decision or exact prior authorizing instruction:
- Remaining ambiguity and independent work that may continue:
- Settled spec/design/acceptance consequences; material departure review:

## Architecture preflight

- Impact: affected owners/contracts/invariants, or N/A with source-based reason.
- Fresh expert identity, source/diagram scope and explicit approach verdict.
- Current/proposed overview and sequence; alternatives and failure/cancel/stale/rollback paths.
- Settled approach, material questions/user decision, compatibility/migration and tests.
- Changed diagrams/render inspection; material departure re-review.

## Implementation

- Ownership/components changed; architecture/feature diagrams reconciled and rendered:
- Shared design/state impact:
- Performance impact and intended measurement:

## Acceptance (update after implementation)

| Criterion/scenario | Test/review | Evidence | Result/gap |
| --- | --- | --- | --- |
| <observable behavior> | <suite/case or manual procedure> | <artifact path> | unverified |

- Commit/tree (including uncommitted changes if applicable):
- Concise tracked archive descriptor, ZIP/member hash verification and actual location:
- Storage/retention/local-only or pending status; reviewers' access to raw evidence:
- Commands and exit/results:
- Available command/suite/review/evidence durations and rerun reasons:
- Reused evidence: source/test/configuration bindings and original execution:
- Reusable workflow changes reconciled with agent-dev-template or explicit pending scope:
- Device/OS/monitor/resolution/DPI/rotation/input/keyboard/app versions:
- Visual comparison result, reviewer, date and reviewed artifacts:
- Performance sample count, warmup, timing boundaries, p50/p95 and raw data:
- Lint baseline changes with justification:
- Remaining unrun/deferred scenarios:
- Requirement coverage refreshed and checked (report/snapshot):
- Candidate digest and two fresh independent review identities/verdicts:
- Push/release gate result (both approvals required; see `docs/REVIEWS.md`):
- Release report Markdown/JSON, checksum and changelog/release/workflow links:

## Release architecture (required for every new version, not task N/A)

- Exact pre-attestation tree/digest and version/previous release baseline:
- Fresh expert identity/UTC, actual scope/findings/verdict/blockers/gaps:
- Attestation-only delta verified by two different fresh final reviewers:
- Final canonical digest includes the record; no extra exclusions:
- Any changed input/re-review or byte-identical same-version transport retry reason:
- Release authorization blocked until actual approval and blockers resolved:
