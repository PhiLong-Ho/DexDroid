# Local command supervision and observation

[Code map and atlas](../README.md) | [Owning contract](../../specs/local-monitoring.md)

## Scope and source

The runner owns acceptance and device restoration. run_monitor.Session owns launched child lifetime, raw output, bounded extraction/status and deadlines; its read-only viewer does not launch, retry or cancel work. Device suite data and supervisor data occupy separate namespaces.

Source entry points: [Supervisor](../../../tools/run_monitor.py); [Device runner](../../../tools/run_device_checks.py). Read current symbols and requirement checks
before changing behavior; this page is a high-level map, not an exhaustive call graph.

## Component overview

```mermaid
flowchart LR
    Runner[Host or Android runner] --> Supervisor[run_monitor.Session]
    Supervisor --> Child[Owned child process tree]
    Child --> Raw[Byte-preserving stdout and stderr]
    Raw --> Progress[Bounded semantic extraction]
    Supervisor --> Status[Atomic status and events]
    Progress --> Status
    Status --> Viewer[Independent read-only viewer]
    Supervisor --> Result[Actual child and cleanup outcome]
    Result --> Runner
```

## Representative sequence

```mermaid
sequenceDiagram
    participant R as Owning runner
    participant S as Supervisor
    participant C as Child tree
    participant V as Read-only viewer
    R->>S: Launch argument-array command with deadline
    S->>C: Start owned process tree
    C-->>S: Raw stdout and stderr
    S->>S: Persist bytes and bounded progress and heartbeat
    V->>S: Read atomic status only
    S-->>V: Activity, progress, quiet/stale state or outcome
    alt Exit
        C-->>S: Actual exit status
    else Deadline or cancellation
        S->>C: Bounded owned-tree cleanup
    end
    S-->>R: Child outcome plus capture/finalization faults
    R->>R: Restore device controller where applicable
    R->>S: Final acceptance and restoration state
    Note over R,V: Heartbeat is not semantic progress or test acceptance
```

## Change and verification boundaries

Sources: tools/run_monitor.py and tools/run_device_checks.py. Legacy suites may buffer assertions; unknown progress stays unknown. Capture/finalization/restore faults cannot certify a pass. This is local observation, not a paid monitoring backend, model telemetry collector or automatic retry loop.

See [requirements and explicit gaps](../../requirements.json). Diagram arrows
describe current responsibilities/intended paths, not successful runtime verification.
