# Independent review before publication

Final reviewers also inspect the architecture-impact note, applicable expert
approach decision and source-consistent component/feature diagrams. The upstream
architecture expert cannot serve as either fresh final reviewer for that change.

Every push and release requires **two fresh independent review agents**. This
implements NFR-007 / DX-REVIEW-001. Complete spec, implementation, acceptance,
coverage, formatting and evidence first. Read [coverage](coverage.md) for gaps.
Work on a branch; [main protection](BRANCH_PROTECTION.md) requires a PR and its
up-to-date `host` check. No direct push or administrator bypass is permitted.

1. Stage all intended files. Check `git diff` for unintended unstaged edits and
   `git diff --cached` for the full candidate. Include new files and retained logs.
2. Run `python tools/review_gate.py snapshot` to obtain the staged candidate digest.
   Create exactly two new agents with `fork_turns: none`; provide the repository,
   base revision, candidate digest, changed requirements and review scope. Ask each
   to independently inspect the spec, diff, tests, scenario gaps and evidence,
   run useful checks, and return explicit approve/reject with findings. Reviews
   must be read-only. Do not prime them with implementation claims or each other.
3. Resolve blocking findings. Any candidate content change requires updated
   checks/evidence and two newly created reviewers of the new digest.
4. After both explicitly approve, write `reviews/approvals.json` using the shape
   below, with actual identities, UTC review timestamps, scope and conclusions.
   Never copy the example identities or claim approval that did not occur.
5. Stage the record; run `python tools/review_gate.py check`. Commit without
   changing other contents. Push only after this succeeds. Tagged/manual release
   also checks approvals before signing and before publishing.
6. Push the reviewed branch and open a PR. Wait for required `host` checks to pass
   and resolve conversations, then merge through GitHub. If updating from `main`
   changes contents, repeat the two-fresh-agent reviews for the new candidate.
   Create release tags from the merged reviewed contents on `main`.

```json
{
  "schema_version": 1,
  "candidate_sha256": "<python tools/review_gate.py snapshot output>",
  "reviews": [
    {
      "agent_id": "<first actual fresh agent ID>",
      "fresh_agent": true,
      "verdict": "approve",
      "reviewed_at": "<ISO 8601 UTC timestamp>",
      "scope": "<files, requirements, checks and evidence reviewed>",
      "summary": "<actual conclusion, including residual nonblocking gaps>"
    },
    {
      "agent_id": "<second actual fresh agent ID>",
      "fresh_agent": true,
      "verdict": "approve",
      "reviewed_at": "<ISO 8601 UTC timestamp>",
      "scope": "<files, requirements, checks and evidence reviewed>",
      "summary": "<actual conclusion, including residual nonblocking gaps>"
    }
  ]
}
```

Install the local hook once per clone:

```sh
git config core.hooksPath .githooks
```

Python must be on PATH. The hook checks each pushed branch/tag's actual commit,
including annotated tags. CI checks the PR head (not GitHub's synthetic merge),
branch head or release tag. The digest hashes sorted Git blob IDs, paths and file
modes, so canonical Git line endings agree on Windows/Linux. Only the approval
record is excluded to avoid a circular hash. Exclusion does not exempt the record
from validation. New/modified/deleted source, tests, spec, coverage, docs, workflows
or evidence invalidate approvals. Ignored build outputs are not source content.

These records attest that the orchestrator performed actual reviews; JSON cannot
authenticate an agent or prove independence. Hooks can be bypassed by maintainers;
CI prevents stale/missing records in the normal quality/release workflows. GitHub
branch protection and authenticated human review are separate repository settings.
Do not describe this mechanism as tamper-proof. If fresh agents are unavailable or
either rejects, keep local work reviewable and do not push or release.

## Release architecture prerequisite

Every new version candidate first needs the fresh architecture approval specified
in [RELEASING](RELEASING.md#release-architecture-review), including patches,
prereleases and docs-only. The architecture expert cannot be either final reviewer.
Inspect its actual verdict, baseline/version/scope and pre-attestation snapshot.
Verify that only faithful architecture attestation was added to that inspected
tree; any other content change needs renewed architecture review. Then approve
the final canonical digest including that record. Keep approvals.json as the sole
exclusion. This supplements, and does not replace, the existing two-agent gate.
