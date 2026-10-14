# DexDroid development workflow

For each requested change:

1. Update `SPEC.md` first with the requested behavior, current UI/feature contract,
   relevant limitations, and acceptance criteria.
2. Implement the change against that updated specification.
3. After implementation, add or update behavior-focused acceptance/E2E tests.
   Run the relevant checks and record the actual results. Do not claim unrun
   checks passed. Prefer real Android instrumentation for desktop/input behavior
   and visible controller interactions.

Keep the specification reconciled with the current implementation. Distinguish
implemented behavior, verified behavior, and deferred features; remove obsolete
UI descriptions instead of relying on contradictory version-history notes.
