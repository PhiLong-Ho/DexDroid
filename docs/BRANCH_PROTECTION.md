# Protected main and agent review

`main` requires a pull request. Its required GitHub Actions `host` check must
succeed and the branch must be up to date. This Quality job validates two fresh
agent approvals against the candidate contents before its build/test/tool gates.
Administrator enforcement is enabled; force pushes and deletion are disabled.
Review conversations must be resolved before merging.

The repeatable configuration is
[main-protection.json](../config/github/main-protection.json). GitHub Actions
app ID 15368 is the required provider for `host`, matching the existing check.

```sh
gh api --method PUT repos/PhiLong-Ho/DexDroid/branches/main/protection --input config/github/main-protection.json
gh api repos/PhiLong-Ho/DexDroid/branches/main/protection
```

Develop on a feature/chore branch; follow [the two-agent review protocol](REVIEWS.md),
push the approved branch, create a PR and wait for required checks before merging.
New candidate contents need new approvals. Do not disable/bypass protection to
merge or push. Release tags come from the merged reviewed contents on `main`.

The required human approving-review count is zero: the two agents are represented
by the CI attestations in `reviews/approvals.json`, not separate GitHub accounts.
PRs are still mandatory. This avoids claiming that agent records are native
GitHub human approvals. Creating a separate authenticated bot/reviewer service
would be a different integration.

The existing attestation limits remain: records cannot authenticate independent
agent identities, repository owners can change protection, and writers can alter
workflows. This policy enforces the project's normal review flow; it is not a
security boundary against a malicious repository administrator. GitHub lets
repository writers set statuses; the provider restriction limits the required
`host` check to GitHub Actions, not to a cryptographically authenticated agent.
See [GitHub branch protection](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)
and [the protection API](https://docs.github.com/en/rest/branches/branch-protection).
