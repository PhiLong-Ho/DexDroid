# Feature architecture: <name>

[System/component map](../architecture/README.md) | [Owning contract index](../specs/README.md)

## Scope and source

State implemented, intended, planned or debug-only scope; link the owning feature
contract/requirement IDs and current source files/symbols. List owners, persistent
vs session state, process/thread/trust boundaries and known verification gaps.

## Component overview

```mermaid
flowchart LR
    Entry[Feature entry] --> Owner[State owner]
    Owner --> Dependency[Dependency or external boundary]
    Owner --> Store[Persistent state if applicable]
```

Replace all example nodes with actual owners. Label calls, callbacks, data and
frame paths distinctly; do not draw desired behavior as established implementation.

## Representative sequence

```mermaid
sequenceDiagram
    actor User
    participant Entry
    participant Owner
    participant Dependency
    User->>Entry: Request
    Entry->>Owner: Validated intent
    Owner->>Dependency: Perform operation
    alt Current owner and successful result
        Dependency-->>Owner: Result
        Owner-->>Entry: Commit and show state
    else Failure or obsolete operation
        Owner->>Owner: Cancel, rollback or discard stale result
        Owner-->>Entry: Current recovery state
    end
```

Explain which scenario is illustrated, omitted branches and linked sequences.
Identify cancellation, lifecycle/identity validation, rollback and teardown owners.

## Change and verification boundaries

List affected invariants, tests, compatibility/performance risks, actual evidence
and unverified scenarios. Record material expert decisions in the change plan/ADR.
Render and inspect diagrams; syntax success and diagrams are not runtime proof.
