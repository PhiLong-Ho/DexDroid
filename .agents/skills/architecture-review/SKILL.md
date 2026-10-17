---
name: architecture-review
description: Review architectural impact before implementation when a change may affect ownership, interfaces, lifecycle, concurrency, persistence, shared behavior or delivery boundaries. Also use for every new version release candidate, source-based architecture documentation and design reviews.
---

Read AGENTS.md, docs/ARCHITECTURE.md, docs/architecture/README.md and the owning
spec/feature architecture page. Consult current code-review-graph, then inspect
actual source and tests. Graphify is a release snapshot, not current truth.

Every task records an architecture-impact note. A clearly local wording, style
or implementation correction can record N/A with its source-based reason. If the
change may affect boundaries, state ownership, IPC/API contracts, lifecycle,
threading/queues, persistence/migration, shared input/geometry, security/privacy,
dependencies/build topology or release/evidence bindings, create a fresh read-only
architecture expert before dependent implementation. Uncertain impact needs review.

Give the expert the request, constraints, source/diff, current diagrams, requirements,
known failures and test/evidence gaps. Ask for:
- affected owners, trust/process/thread boundaries and invariants;
- current/proposed overview and sequence, including failure, cancellation, stale
  work, rollback and teardown; viable alternatives and consequential tradeoffs;
- compatibility/migration impact and affected verification, with exclusions;
- explicit approve-approach, changes-needed or unresolved-questions conclusion.

Record the expert identity, inspected scope, findings, chosen approach and decision
in the existing change record. Resolve blockers before dependent work. Clarify
consequential unclear requirements with the user; proceed on authorized routine
technical choices without repeated permission. Re-review material departures.

Maintain source-linked system/component and feature overview/sequence diagrams.
Use docs/templates/feature-architecture.md; render and inspect changed diagrams
against actual source. Label intended contracts, historical/deferred/debug scope
and known failures; a diagram does not establish runtime correctness. Add an ADR
only for a material lasting decision, recording alternatives and consequences.

This preflight does not approve rendered UI or replace either of the two fresh
final-candidate reviewers. It creates no unattended service or CI-authenticated
expert gate; the agent session must perform and record the real review.

For EVERY new version release candidate, create a fresh read-only architecture
expert even for patch/prerelease/docs-only or task N/A. Inspect cumulative changes
since the previous published release and the current whole-product/component/
feature architecture, actual source/tests, invariants, diagrams, risks and gaps.
Record explicit approval/blockers plus actual identity/UTC time/version/baseline
and exact frozen pre-attestation tree/digest before final code review. No approval
or unresolved blockers means no release tag/push/dispatch/signing/publication.
Only faithful attestation may be added afterward; two different fresh final agents
verify that delta and approve the full final digest including the record. Any other
changed input requires renewed fresh architecture review. Do not add digest
exclusions or claim self-containing hash approval. Same-version byte-identical
transport retries may reuse the actual verdict with recorded reason/identity,
subject to existing no-overwrite/retry rules. See docs/RELEASING.md.
