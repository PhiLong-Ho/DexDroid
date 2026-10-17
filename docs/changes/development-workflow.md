# Change: development workflow and evidence storage

## Contract

The user requested upstream usability review, smaller Git evidence history,
architecture and feature diagrams, developer onboarding, and clarification of
unclear requirements after expert review. SPEC NFR-002 through NFR-005 and
DX-DOC-001, DX-MAINT-001, DX-VISUAL-001 and DX-RELEASE-001 were reconciled before
tooling implementation. Existing app behavior and historical evidence stay intact.

## Expert review and decisions

Fresh read-only advisor `/root/workflow_ui_expert` reviewed the requested workflow,
current policies and the missing upstream usability gate. It recommended reviewing
the whole user task before implementation, with proportionate proposals, task
steps, sketches where placement/navigation changes, meaningful states and tradeoffs.
The expert role is separate from rendered acceptance and the final two reviewers.

The user selected **Actions artifacts + release archives** for future raw evidence,
leaving existing archives untouched. The user also selected **Wait for material UI
decisions**: consequential unresolved choices wait for a user answer; exact already
specified corrections or delegated choices do not need repeated approval.
Independent work may continue while dependent decisions wait. This task changes
development tooling and documentation; it introduces no app UI needing mockups.

## Implementation

- [Workflow](../WORKFLOW.md), repository policy and both local skills route future
  UI work through the upstream expert and material-decision gate.
- [Evidence policy](../EVIDENCE.md), ignore rules and byte-preserving ZIP tooling
  keep future raw runs outside Git. Descriptors bind archive/member hashes and
  provenance; location metadata distinguishes local files from upload targets.
- Quality CI bundles only its current run with 14-day requested artifact retention.
  Release publication requires a verified current host archive, descriptor,
  reports and checksums, bound to source/version and reported test fingerprints.
- [Architecture](../ARCHITECTURE.md), all twelve feature specs, the spec template
  and workflow contain seventeen Mermaid diagrams in total, checked against
  ownership and state transitions. No generated class dump is introduced.
- [Onboarding](../ONBOARDING.md) covers checkout, prerequisites, first build,
  source ownership, optional device suites and a first reviewed change.

## Acceptance and limits

See the [current evidence summary](../../testresults/README.md)
and its archive descriptor for actual commands, source fingerprints and raw files.
Host checks, tooling acceptance, rendering and the two final candidate reviews
have distinct scopes. Synthetic publication tests mock GitHub and signing tools;
their printed publication messages are fixture output, not real releases.

No phone/emulator suite, app UI acceptance, timing benchmark, novice onboarding
exercise, real Actions upload, retention expiry or hosted publication was performed
for this tooling change. Those scenarios remain explicit requirement gaps.
Existing device evidence is historical. Renderer syntax success does not establish
runtime app behavior or performance. No lint baseline or app source was changed.

The first review pair rejected the candidate because Windows-forbidden filename
characters could pass ZIP verification and sanitize onto another member when
extracted. A subsequent independent pair identified additional reserved device
forms, including console devices, superscript COM/LPT digits and spaces before
extensions. The verifier rejects these names portably without depending on Python
3.13 APIs. The unsafe-member test includes complete correctly hashed collision and
device-name fixtures. Rejected candidates' verdicts do not authorize this changed
candidate; final review requires two new agents.

A later diagram review corrected monitor reconnect selection to show confirmed
working settings before a bound snapshot, monitor recommendations as fallback,
and shared capability fingerprints. The correction documents existing behavior;
no profile implementation changed. The corrected diagram was rendered again.

Final candidate identities, digest and verdicts are recorded in
[approvals](../../reviews/approvals.json), whose exclusion from the digest avoids a
circular attestation. Delivery is a local commit; this task does not publish a
release or push a branch.
