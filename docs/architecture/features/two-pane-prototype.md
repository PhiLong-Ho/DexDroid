# Debug-only two-pane feasibility experiment

[Code map and atlas](../README.md) | [Owning contract](../../specs/two-pane-prototype.md)

## Scope and source

This is the historical debug-source-set prototype, not the product SplitWorkspace. It has an explicit Activity lifetime and two authorized shell bindings. Synthetic repeated key injection failed in retained runs; the separate Bluetooth user report does not turn those runs into passes.

Source entry points: [SplitProbeActivity](../../../app/src/debug/java/dev/dexdroid/SplitProbeActivity.java); [SplitProbeChecks](../../../app/src/androidTest/java/dev/dexdroid/SplitProbeChecks.java). Read current symbols and requirement checks
before changing behavior; this page is a high-level map, not an exhaustive call graph.

## Component overview

```mermaid
flowchart LR
    Explicit[Explicit debug prototype launch] --> Activity[SplitProbeActivity]
    Activity --> Primary[First shell display]
    Activity --> Secondary[Second shell display]
    Activity --> Fixture[Cross-UID probe fixtures]
    Activity --> Cleanup[Explicit bounded prototype cleanup]
    Release[Release source set] -.-> Excluded[Prototype excluded]
```

## Representative sequence

```mermaid
sequenceDiagram
    actor Developer
    participant A as Debug SplitProbeActivity
    participant D as Two shell helpers
    participant F as Cross-UID fixtures
    Developer->>A: Explicitly start prototype
    A->>D: Bind and create two displays
    A->>F: Launch probe targets on distinct displays
    Developer->>A: Exercise pointer and keyboard switching
    A->>D: Relay to current target with owned stream
    F-->>A: Observable dispatch state
    Developer->>A: End or Activity teardown
    A->>D: Cancel input and release both owners
    Note over A,F: Historical failed synthetic keyboard gate remains failed
```

## Change and verification boundaries

Use src/debug SplitProbeActivity and src/androidTest SplitProbeChecks/fixture sources; none ship as product release behavior. Keep experimental assertions/evidence separate from product compatibility. Do not infer current performance from a prototype or bypass the main session invariants.

See [requirements and explicit gaps](../../requirements.json). Diagram arrows
describe current responsibilities/intended paths, not successful runtime verification.
