# Diagnostics and status visibility

[Code map and atlas](../README.md) | [Owning contract](../../specs/diagnostics.md)

## Scope and source

DiagnosticLog keeps at most 250 timestamped in-memory events. SessionService records outcomes and exposes a snapshot. Phone/monitor menus share readable status and explicit copy/share/clear actions.

Source entry points: [DiagnosticLog.java](../../../app/src/main/java/dev/dexdroid/DiagnosticLog.java); [SessionService.java](../../../app/src/main/java/dev/dexdroid/SessionService.java); [MainActivity.java](../../../app/src/main/java/dev/dexdroid/MainActivity.java); [MonitorShell.java](../../../app/src/main/java/dev/dexdroid/MonitorShell.java). Read current symbols and requirement checks
before changing behavior; this page is a high-level map, not an exhaustive call graph.

## Component overview

```mermaid
flowchart LR
    Recognizer[Gesture, launch and lifecycle outcomes] --> Session[SessionService.log]
    Session --> Ring[DiagnosticLog: 250-event memory ring]
    Ring --> Snapshot[Snapshot for current diagnostic page]
    Snapshot --> UI[Phone or monitor view]
    UI --> Export[Explicit copy or share]
    UI --> Clear[Clear in-memory events]
```

## Representative sequence

```mermaid
sequenceDiagram
    participant S as Session operation
    participant L as DiagnosticLog
    participant UI as Diagnostics page
    S->>L: Record content-free outcome
    L->>L: Timestamp and evict oldest above 250
    UI->>L: Request snapshot
    L-->>UI: Current text or empty message
    alt Explicit copy or share
        UI->>UI: Use platform clipboard/share action
    else Clear
        UI->>L: Clear memory ring
    end
    Note over S,L: No typed text<br/>or screen content
```

## Change and verification boundaries

Do not expand logs to typed/screen content or unbounded storage. A registered gesture event differs from successful remote effect; retain failure results and correlate source/configuration externally for acceptance. Debug raw ADB evidence is separate from the content-free product log.

See [requirements and explicit gaps](../../requirements.json). Diagram arrows
describe current responsibilities/intended paths, not successful runtime verification.
