# Architecture atlas and expert preflight

## Contract and scope

The user requested a whole-app/code component overview, feature overview/sequence
diagrams and an architecture expert before future tasks that may affect architecture,
including the same reusable setup in agent-dev-template. DX-ARCH-001 / NFR-013 owns this
manual obligation; spec and requirement mapping were updated before implementation.

This is documentation/policy work: no product code, test source, dependencies,
permission policy, device state or release behavior changes in this task. Prior
DexDroid 0.12.1 fixture/release changes remain separate and uncommitted. Its Android 16
handoff/pane-picker failures and native Stop verification gap are not resolved here.
No DexDroid release, production installation or Graphify refresh is requested.

## Architecture preflight

Expert `/root/architecture_preflight`, fresh context, read-only, inspected current
source and both ownership inventories. The selected approach keeps detailed
architecture rationale, adds a source-linked component/runtime/state map plus a
feature atlas, and links each product contract to its technical diagrams.
DexDroid has 14 feature owners (13 specs plus Coding Practice); the template has
five including its replaceable CLI. Each owns an overview and representative
sequence with failure/cancellation/stale/rollback boundaries where relevant.

Expert findings corrected before acceptance: actual unbounded session queue versus
bounded split queue; same app process versus separate shell helpers; synchronous
requests versus one-way callbacks and Surface frames; Stop host-closure ordering;
empty-occupancy scan pausing versus observer teardown; named/manual profile commit
and asynchronous preference disk persistence; service versus desktop lifetimes.
The expert explicitly approved the corrected approach/documentation semantics.
Ordinary technical documentation choices were authorized by the user's request;
no material product decision or open requirement was introduced. UI preflight is
N/A: no product interaction or visual design changes.

## Workflow and adoption

Every task reads the atlas and records impact. Clearly local work may record N/A
with a source-based reason. Possible or uncertain ownership/interface/process/
concurrency/lifecycle/persistence/shared/security/dependency/build/delivery impact
requires a fresh read-only architecture expert before dependent implementation.
Record owners/invariants, current/proposed diagrams, alternatives/tradeoffs,
failure paths, compatibility and test scope, explicit verdict and settled decisions.
Resolve material unknowns with the user and re-review material departures. This
expert is separate from UI preflight and both fresh final-candidate reviewers.
No new CI service authenticates expert judgment or provides unattended AI review.

Both repositories update AGENTS, change skill, shared architecture-review skill,
workflow/coding practice, spec/requirement coverage, onboarding, README/changelog,
feature owner links and reusable feature-architecture/change/spec templates.
Template adoption guidance includes the new policy. No Android assumptions are
copied into its language-neutral workflow/examples.

## Acceptance and evidence

- Manual inventory: every existing feature owner has linked overview/sequence,
  source owners, lifecycle/failure notes and verification limits.
- Workflow scenarios: local wording correction skips expert with reason; changed
  owner/IPC/queue/persistence contract requires expert before dependent work;
  uncertain impact requires review; material departures reopen it; consequential
  unanswered requirements wait for user; preflight cannot replace final reviewers.
- Diagram syntax/render and source-semantic inspection remain separate checks.
  Rendered review found and repaired clipped notes, alt-caption collisions and
  cluster-title crossings; failed/intermediate render artifacts remain retained.
- Changed tests: none. Coverage regeneration adds a manual requirement and leaves
  concrete existing checks unchanged; no wording-matching tests were invented.
- Commands planned/completed outcomes are indexed in the versioned architecture
  evidence summary. Local raw renders/logs are ignored and archived with hashes;
  only compact summaries/descriptors are tracked. Availability remains local-only
  unless an actual external upload is recorded.

## Delivery limits

DexDroid docs remain in its existing working tree alongside explicitly preserved
blocked acceptance work; this task does not publish that app candidate. The clean
companion template branch can pass its own two fresh final reviews and protected
PR gates independently. Those attestations do not approve a DexDroid release.
A host/document/diagram pass is not runtime, human/golden UI or performance proof.

## Recorded verification

[Current outcomes and verified archive](../../testresults/README.md)
record host results, current diagram inventory, actual rendered reviewers, manual
scenario scope and local-only retention. No device/golden/performance pass is implied.
