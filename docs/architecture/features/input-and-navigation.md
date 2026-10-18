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
    Bridge --> Legacy[Android 11 owned-task focus and readiness]
    Legacy --> App[Native app input window]
    Bridge -->|Modern Android| App
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
        opt Android 11 target changed or keyboard input
            D->>D: Focus existing owned task without Recents
            D->>D: Validate display, top task, component and UID
            D->>D: Check owned visible native window on worker
            alt Identity changed or window unavailable
                D->>D: Invalidate readiness and drop complete contact
            else Current target ready
                D->>D: Cache identity and inject input
            end
        end
        D-->>S: Injection outcome
    end
    opt Resize, navigation, dependency loss or Stop
        S->>D: Cancel held contacts and keys before handoff
        S->>S: Invalidate stale input ownership
    end
```

## Change and verification boundaries

Fit bars reject touches; physical contact wins over phone input and hides the pointer. Preserve pointer IDs, down-times, key ownership and cancellation ordering. Android 11 focus arbitration is separate from modern independent focus. The generic session queue is not globally bounded; only the split queue has an explicit 64-event bound.

[LegacyInputWindow.java](../../../app/src/main/java/dev/dexdroid/LegacyInputWindow.java)
focuses an existing task without Recents activation. Native child/Up/Back navigation
must preserve phone foreground choice; root exit clears only that pane. Readiness
queries remain off the UI thread. Legacy key admission does not require the ROM
FocusedWindows list to include the native display: targeted keys can be delivered
while it names only the phone. Keyboard input still focuses and revalidates the
owned task. Cancellation ownership uses the separate strict focused() predicate. The polling deadline is not a strict end-to-end
latency limit because native queries have their own bounds.

See [requirements and explicit gaps](../../requirements.json). Diagram arrows
describe current responsibilities/intended paths, not successful runtime verification.
