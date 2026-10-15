# Codex workflow for this project

Open Codex with **this repository** as the workspace. `AGENTS.md` holds durable
project instructions. `.agents/skills/dexdroid-change/SKILL.md` is the reusable
change workflow; invoke `$dexdroid-change` explicitly if it is not selected.
Codex discovers repository skills from `.agents/skills`; if it does not appear,
restart Codex in this repository. No external skill marketplace is required.
Feature specifications follow [the project Spec Kit-style template](templates/spec.md).
This is a document convention; Spec Kit CLI/slash commands are not installed by it.

Use normal requests for small changes. Use a Goal when completing the work needs
multiple investigate/change/measure cycles. Goals belong to a thread; they are
not a replacement for source-controlled requirements, tests or design guidance.
These are **templates**, not active Goals:

```text
/goal Close DX-UI-001 and DX-PHONE-001 coverage gaps for the named submenu change.
Update the spec first, implement, then update acceptance/E2E. Finish when
qualityCheck and the affected physical-device suites pass, phone/monitor visual
evidence has been reviewed, and the change record maps each criterion to evidence.
Preserve four-way rotation, Fit mapping, touch arbitration and background operation.
Use only this repository and the connected test device. If hardware is unavailable,
complete independent host work and report exactly which evidence remains unverified.
```

```text
/goal Establish the DX-PERF-001 baseline on Black Shark 4 / Android 11 at
1920x1200 with the declared DPI and app set. Define timing boundaries, capture
at least 100 post-warmup samples for local search feedback and menu readiness,
and report p50/p95 and missed frames with raw evidence. Keep behavior and visual
acceptance green. Do not call it verified using timeout assertions. If measurements
cannot be made, report the missing instrumentation and leave performance unverified.
```

Review scope and success conditions before submitting a Goal. `/goal` shows it;
`/goal pause`, `/goal resume`, and `/goal clear` manage its lifecycle in supporting
Codex builds. Do not use an open-ended "make the whole app perfect" objective.
No Goal has been activated by this setup.

Every change now requires two fresh independent agents before pushing or releasing.
Follow [the review protocol](REVIEWS.md): inspect the final spec, diff, tests,
coverage and evidence without treating the author's conclusions as proof. Both
agents must approve the same candidate; content changes require two fresh reviews.
A reviewer must not approve baselines from filenames or test counts. This durable
project instruction authorizes these review agents for subsequent changes.

Official references: [Goals](https://developers.openai.com/cookbook/examples/codex/using_goals_in_codex),
[skills](https://learn.chatgpt.com/docs/build-skills), and
[project instructions](https://learn.chatgpt.com/docs/agent-configuration/agents-md).
