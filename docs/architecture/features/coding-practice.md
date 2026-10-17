# Daily coding and architecture review loop

[Code map and atlas](../README.md) | [Owning coding practice](../../CODING_PRACTICE.md)

## Scope and source

The agent session owns risk screening, expert decisions and evidence reuse. Coding practice selects focused iteration without removing completed-candidate gates. Architecture and UI preflights are upstream roles; neither is a final candidate approval.

Source entry points: [Workflow](../../WORKFLOW.md); [Agent contract](../../../AGENTS.md); [Architecture skill](../../../.agents/skills/architecture-review/SKILL.md). Read current symbols and requirement checks
before changing behavior; this page is a high-level map, not an exhaustive call graph.

## Component overview

```mermaid
flowchart LR
    Request[Spec and architecture-impact note] --> Risk{Possible architectural impact?}
    Risk -->|Yes or uncertain| Expert[Architecture expert approach review]
    Risk -->|No with reason| Iterate[Targeted implementation and tests]
    Expert --> Iterate
    Iterate --> Verify[Complete candidate gates and relevant renders]
    Verify --> Freeze[Source-bound evidence and frozen candidate]
    Freeze --> Review[Two fresh final reviewers]
```

## Representative sequence

```mermaid
sequenceDiagram
    participant A as Author
    participant E as Architecture expert
    participant T as Checks and evidence
    participant F as Fresh final reviewers
    A->>A: Read source map and record scope, spec and impact
    opt Possible or uncertain architecture impact
        A->>E: Current source, diagrams, proposal and gaps
        E-->>A: Owners, invariants, alternatives and explicit verdict
        A->>A: Resolve blockers or consequential user questions
    end
    loop Bounded iteration
        A->>T: Focused reproduction and affected checks
        T-->>A: Actual outcome and rerun reason
    end
    A->>T: Complete required candidate verification
    T-->>A: Current or valid reused source-bound evidence
    A->>A: Freeze source, diagrams, docs and evidence
    A->>F: Request two independent final reviews
    Note over A,F: Material approach departures require expert re-review
```

## Change and verification boundaries

Sources of policy: docs/CODING_PRACTICE.md, WORKFLOW.md and AGENTS.md. Do not reuse evidence after relevant source/configuration changes or reuse final approvals after any candidate change. Efficiency claims require actual timing boundaries; no device rerun is needed for a documentation-only architecture map.

See [requirements and explicit gaps](../../requirements.json). Diagram arrows
describe current responsibilities/intended paths, not successful runtime verification.
