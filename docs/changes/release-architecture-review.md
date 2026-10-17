# Architecture review for every release

## Contract and scope

User requires architecture review on every release. The existing stable
architecture requirement owns this extension; spec/coverage mapping was updated
before implementation. This is documentation/agent-session policy only: no runtime,
test source, executable tool, release generator or CI YAML changes. No new app
release, privacy history replacement or production installation is authorized.
DexDroid's prior dirty work and prepared privacy cleanup candidate stay untouched.

## Architecture expert and decision

Fresh read-only expert `/root/release_arch_preflight` inspected release/workflow/
review/digest ownership in both repositories and explicitly approved the approach.
Require every new version candidate, including patch/prerelease/docs-only, after
checks/evidence and before the two final reviewers. Task N/A cannot waive release
review. Inspect cumulative diff since the prior published release plus current
whole-product/component/features, source/tests/diagrams, invariants, security/
privacy, performance/dependencies/delivery, known failures and verification gaps.

The expert corrected a potential self-containing hash claim: freeze an exact
pre-attestation snapshot first, record actual identity/version/baseline/verdict/
findings/gaps, and allow only faithful attestation before the final reviewers
approve the full canonical digest including that record. No new exclusions;
approvals.json remains the sole exclusion. Other changed inputs need renewed
fresh architecture review, and changed final candidates need a new final pair.
Actual same-version byte-identical transport retries may reuse the verdict with
identity/reason recorded, without relaxing either repo's no-overwrite/retry rules.

Missing approval/blockers prohibit release tag creation/push, dispatch, signing
and publication by the agent. This is not new CI authentication of expert judgment.
UI preflight is N/A: no product interactions change. Exact user request settles
scope; no material unanswered product choice is introduced.

## Reconciliation and verification plan

AGENTS, architecture/change skills, release/workflow/review/coding-practice guides,
onboarding, templates, README/changelog, owning spec/requirement map and delivery
diagrams are reconciled. A reusable release architecture attestation template
makes required scope explicit. No prose-matching test is invented; requirement
remains manual, with actual scenario walkthrough rather than fabricated passes.

Walk through patch/docs-only/prerelease, no-impact task, missing/blocked verdict,
non-attestation changes, same-version transport retry and new-version invalidation.
Render/inspect changed diagrams and run required host/docs/coverage gates. Keep
real app/device evidence private; this task needs no device tests or captures.
Publication of the companion template requires two fresh exact-candidate approvals;
DexDroid docs remain local alongside blocked/pending work. Historical privacy
cleanup authorization is still pending and its reviewed candidate is not changed.

## Recorded verification

- Manual policy walkthrough: patch/prerelease/docs-only and task N/A still require
  architecture approval; missing/blocked verdict prohibits release; only actual
  attestation may follow the inspected snapshot; other changes require renewal;
  transport retry reuse is limited to the identical version/candidate and existing
  retry rules. No actual version release was reviewed or authorized by this task.
- DexDroid qualityCheck and 126 tooling tests pass. All 122 captured runtime/tool/
  build/workflow source fingerprints are unchanged by this documentation task.
- Template configured host gates pass: 102 passed and two platform skips. Host
  execution predates final prose/diagram readability amendments only; executable,
  test and CI inputs are unchanged. Relevant docs/requirements/coverage checks
  are rerun on the completed documents; reuse is not represented as a new run.
- Eight changed Mermaid diagrams rendered and inspected. Long participant/self
  labels were wrapped; initial renders remain retained alongside final versions.
  Diagram semantics distinguish routine changes from mandatory version review.
- No tests added/edited/deleted and no automated review-judgment gate added.
  Existing manual architecture requirement and generated coverage are reconciled.
- Actual command logs, source bindings and renders are sealed in a local-only
  evidence bundle indexed by the versioned summary; no personal app capture is
  required or included. Existing privacy cleanup approvals/history are untouched.
