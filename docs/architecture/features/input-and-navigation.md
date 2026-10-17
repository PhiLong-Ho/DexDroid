# Touchpad, monitor input, gestures and search

[Code map and atlas](../README.md) | [Owning contract](../../specs/input-and-navigation.md)

## Scope and source

The phone Pad recognizes gestures; SessionService arbitrates phone and physical input. MonitorShell owns modal/edge navigation. Complete native events cross the shell boundary; local monitor search edits its own field rather than mirroring arbitrary-app IME.

Source entry points: [MainActivity.java](../../../app/src/main/java/dev/dexdroid/MainActivity.java); [SessionService.java](../../../app/src/main/java/dev/dexdroid/SessionService.java); [MonitorShell.java](../../../app/src/main/java/dev/dexdroid/MonitorShell.java); [Geometry.java](../../../app/src/main/java/dev/dexdroid/Geometry.java); [InputEvents.java](../../../app/src/main/java/dev/dexdroid/InputEvents.java); [MonitorSearchKeyboard.java](../../../app/src/main/java/dev/dexdroid/MonitorSearchKeyboard.java). Read current symbols and requirement checks
before changing behavior; this page is a high-level map, not an exhaustive call graph.

## Component overview

```mermaid
flowchart TB
    Pad[Phone Pad recognizer] --> Owner[Session input ownership]
    Monitor[Monitor touch, mouse and keyboard] --> Modal{Modal or edge navigation?}
    Modal -->|Yes| Shell[MonitorShell and local search keyboard]
    Modal -->|No| Owner
    Owner --> Geometry[Rotation, Fit and pane mapping]
    Geometry --> Dispatch[Session dispatch or bounded split input]
    Dispatch --> Bridge[Shell event relay]
    Bridge --> App[Native app input window]
```

## Representative sequence

```mermaid
sequenceDiagram
    participant Physical as Physical monitor input
    participant M as MonitorShell
    participant S as SessionService
    participant D as Owned shell helper
    Physical->>M: DOWN or native key
    alt Menu or edge owns stream
        M->>S: Cancel competing app input before navigation
        M->>M: Consume contact through release and no click-through
        opt Search editor active
            M->>M: Local keyboard edits query
        end
    else App contact
        M->>S: Route physical contact
        S->>S: Cancel phone contact and capture target and geometry
        S->>D: Mapped DOWN then MOVE and UP or CANCEL
        D-->>S: Injection outcome
    end
    opt Resize, navigation, dependency loss or Stop
        S->>D: Cancel held contacts and keys before handoff
        S->>S: Invalidate stale input ownership
    end
```

## Change and verification boundaries

Fit bars reject touches; physical contact wins over phone input and hides the pointer. Preserve pointer IDs, down-times, key ownership and cancellation ordering. Android 11 focus arbitration is separate from modern independent focus. The generic session queue is not globally bounded; only the split queue has an explicit 64-event bound.

See [requirements and explicit gaps](../../requirements.json). Diagram arrows
describe current responsibilities/intended paths, not successful runtime verification.
