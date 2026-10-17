# Change: focused coding practice

The user requested recording the turnaround-time recommendations as a dedicated
coding workflow and keeping reusable guidance in agent-dev-template. SPEC NFR-011 /
DX-PRACTICE-001 was written before the guide. No app, test runner, CI trigger or
global configuration changes are included; no product UI decision is introduced.

CODING_PRACTICE.md owns the daily coding loop. WORKFLOW.md, AGENTS.md, the change
skill, onboarding, concise README and change template route work to it. The template
contains matching reusable guidance with its own WF-PRACTICE-001 contract.

Manual acceptance walkthrough:

| Scenario | Required documented outcome |
| --- | --- |
| Iterating on a bug | Narrow reproduction and affected checks; full required checks still run for the completed candidate. |
| Ambiguous impact | Inspect current source and broaden checks; graph hints cannot justify omission. |
| Existing successful evidence | Verify source/test/tool/configuration scope and disclose original execution; release gates still use current outcomes. |
| Two reviewers | Independent full-candidate review in parallel; useful focused checks without a compulsory duplicate full run. |
| Candidate edit after approval | Affected rechecks and two new fresh approvals. |
| One connected phone | Device suites stay sequential; overlapping host work requires frozen inputs and separate outputs. |
| Future automation | Selectors, CI deduplication, caches and timing instrumentation are explicitly pending. |

Inspect these outcomes in the guide and validate documentation, requirements,
coverage and skills, plus required host gates. Coverage marks this policy manual;
link checks do not establish behavioral compliance or a measured speedup. No new
tests mirror document wording. No device run, screenshot approval, release or
publication is claimed by this documentation change.

The companion [local monitoring change](local-monitoring.md) implements command
observation in the same pending candidate; its acceptance and scope are separate.
