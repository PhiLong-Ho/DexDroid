# Release architecture review: <version>

Complete only after actual read-only expert review. This is an attestation,
not a planned verdict, runtime certification or replacement for final code review.
Follow [release procedure](../RELEASING.md#release-architecture-review).

- Target version and preceding published tag/commit (or first-release baseline):
- Exact frozen inspected Git tree/digest BEFORE this attestation:
- Reviewed source/document/runtime scope and cumulative release diff:
- Fresh expert actual identity and UTC review time:
- Whole-product/component/feature overview and sequence/source consistency:
- Ownership/interfaces/trust, lifecycle/cancel/stale/rollback, queues/concurrency:
- Persistence/migration, compatibility, security/privacy, performance/dependencies:
- Delivery/evidence contracts, known failures and unverified scope:
- Findings, severity, resolutions and explicit remaining blockers:
- Actual verdict (approve / changes-needed / unresolved-questions):
- Residual gaps and actual user decisions where consequential:
- Read-only review/evidence locations (private data stays private):

After recording the verdict, final reviewers verify the only delta from the
inspected snapshot is faithful architecture attestation. They approve the final
canonical digest including this record; approvals.json remains the sole exclusion.
Other content changes require renewed fresh architecture approval. Missing approval
or unresolved blockers prevent release steps. Record identity/reason for any
same-version byte-identical transport retry; never overwrite published releases.
