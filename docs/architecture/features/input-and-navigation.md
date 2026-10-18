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
    Shell --> Editor[Apps search: editable selection and native text keys; no system IME input connection]
    Modal -->|No| Owner
    Owner --> Geometry[Rotation, Fit and pane mapping]
    Geometry --> Dispatch[Session dispatch or bounded split input]
    Bridge -.-> Validation[ShellCommandResult validates exit and line-leading errors]
    Dispatch --> Bridge[Shell event relay]
    Bridge --> Legacy[Android 11 owned-task focus and readiness]
    Legacy --> App[Native app input window]
    Bridge -->|Modern Android| App
    App -->|Rejected initial DOWN only| Modern[ModernInputWindow stable owned target and current windows]
    Modern -->|Confirmed pending| Cancel[Cancel injected verifier contact and suppress remaining stream]
    Modern -->|Ready or unknown or stale| Error[Explicit input failure]
    Cancel --> Retry[Existing retry status; fresh contact only]
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
            M->>M: Local keys or native hardware keys edit query without system IME connection
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
        opt Android 12+ rejects initial DOWN
            D->>D: Capture target and current input windows
            D->>D: Verify unchanged owned target identity
            alt Stable identity and positively pending window
                D->>D: Cancel rejected contact with checked outcome
                D-->>S: Existing retry status and keep both panes
                D->>D: Suppress stream through release without replay
                opt Fresh DOWN succeeds later
                    D->>D: Refresh current task state on existing handler
                end
            else Ready, unknown or identity changed
                D-->>S: Explicit injection failure
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

[ModernInputWindow.java](../../../app/src/main/java/dev/dexdroid/ModernInputWindow.java)
and [ModernInputWindowState.java](../../../app/src/main/java/dev/dexdroid/ModernInputWindowState.java)
diagnose only rejected initial DOWN on modern Android. They never focus a task or
cache readiness. Recognized complete display-window metadata plus stable top
task/component/UID allow pending recovery. Unknown/missing metadata stays fatal;
recovery before diagnosis may conservatively stay fatal too. A rejected DOWN can
retain dispatcher verifier state: cancel that exact injected stream before
suppressing its physical continuation, without replaying app input. Successful
fresh DOWN clears retry via the existing observer's current snapshot, not a cached
component. Stop releases per-helper state; resize first cancels app input.

MonitorShell's first Back hides served system IME and local keys and explicitly
focuses the non-editor root. Search query/editor survive; the next Back follows
the existing Activity/Presentation dispatch and menu stack. No global IME setting
or predictive-Back policy is changed. The noninteractive root disables default
focus highlight so retained focus cannot tint the composed desktop; actionable
controls retain normal feedback.

See [requirements and explicit gaps](../../requirements.json). Diagram arrows
describe current responsibilities/intended paths, not successful runtime verification.
