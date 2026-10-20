# Touchpad, monitor input, gestures and search

[Code map and atlas](../README.md) | [Owning contract](../../specs/input-and-navigation.md)

## Scope and source

The phone Pad recognizes gestures; SessionService arbitrates phone and physical input. MonitorShell owns modal/edge navigation. Complete native events cross the shell boundary; local monitor search edits its own field rather than mirroring arbitrary-app IME.

Source entry points: [MainActivity.kt](../../../app/src/main/kotlin/dev/dexdroid/MainActivity.kt); [SessionService.kt](../../../app/src/main/kotlin/dev/dexdroid/SessionService.kt); [MonitorShell.kt](../../../app/src/main/kotlin/dev/dexdroid/MonitorShell.kt); [Geometry.kt](../../../app/src/main/kotlin/dev/dexdroid/Geometry.kt); [InputEvents.kt](../../../app/src/main/kotlin/dev/dexdroid/InputEvents.kt); [MonitorSearchKeyboard.kt](../../../app/src/main/kotlin/dev/dexdroid/MonitorSearchKeyboard.kt). Read current symbols and requirement checks
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

[LegacyInputWindow.kt](../../../app/src/main/kotlin/dev/dexdroid/LegacyInputWindow.kt)
focuses an existing task without Recents activation. Native child/Up/Back navigation
must preserve phone foreground choice; root exit clears only that pane. Readiness
queries remain off the UI thread. Legacy key admission does not require the ROM
FocusedWindows list to include the native display: targeted keys can be delivered
while it names only the phone. Keyboard input still focuses and revalidates the
owned task. Cancellation ownership uses the separate strict focused() predicate. The polling deadline is not a strict end-to-end
latency limit because native queries have their own bounds.

[ModernInputWindow.kt](../../../app/src/main/kotlin/dev/dexdroid/ModernInputWindow.kt)
and [ModernInputWindowState.kt](../../../app/src/main/kotlin/dev/dexdroid/ModernInputWindowState.kt)
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

## Debug-only rejected-DOWN capture

[ModernInputWindow](../../../app/src/main/kotlin/dev/dexdroid/ModernInputWindow.kt)
optionally reports its exact consumed target/window/target tuple to
[ModernInputDiagnostics](../../../app/src/debug/kotlin/dev/dexdroid/ModernInputDiagnostics.kt).
The latter is a development source, reflectively attached only in debug builds.
It binds one primary shell helper to one explicit synthetic API35 request and
accepts only the InputTest target, or partial null/exception paths. It never
changes classification, focuses, retries input or queries a second report.
The request/owner files are private local emulator diagnostics, not a product UI,
public evidence or persistent service. Other helpers/builds are unarmed. The
exclusive owner claim happens at create, before input; report I/O happens after
the relay's original routing/cancellation decisions. This may delay RPC return,
Stop or subsequent UP and is not timing-equivalence evidence. An expired,
missing, truncated or wrong-generation sample is inconclusive.

```mermaid
flowchart TB
    R[Private shell request: exact build and boot, nonce, owner, expiry] --> A[Debug-only primary helper attach]
    A --> C[Exclusive one-owner claim before input]
    D[Original rejected DOWN] --> Q[Original target, windows, inspect, target]
    C -.-> Q
    Q --> P[Original pending or fatal decision]
    Q -.-> M[One immutable sample, bounded report and sixteen events]
    P --> X[Original checked cancellation or original failure]
    X --> F[Relay finally after recycle]
    M -.-> F
    F --> O[Exclusive private sample and flush duration files]
    O --> H[Host binds source, APK, request, owner and outcome]
```

```mermaid
sequenceDiagram
    participant H as Private host wrapper
    participant D as Primary shell helper
    participant W as Original window source
    participant S as Debug-only sink
    H->>H: Guard exact emulator, boot, UID, source and APK
    H->>S: Stage owned finite request and nonce directory
    D->>S: Attach at create and claim sole owner
    D->>W: Original rejected DOWN then target, windows, target
    W-->>D: Original values or primary exception
    D->>S: Immutable consumed sample, no I/O
    D->>D: Original classification and checked cancellation, if pending
    D->>D: Recycle event objects
    D->>S: Finally flush without replacing primary result
    S-->>H: Sample, owner, request hash and flush interval
    H->>H: Collect and disable exact request
```

## Debug-only held-key diagnosis

[KeyDiagnostics](../../../app/src/main/kotlin/dev/dexdroid/KeyDiagnostics.kt)
records only synthetic fixture key sequences in debug builds, with128bounded
records including an explicit truncation marker. No text or unrelated window
names are recorded. This observes the existing routing; it does not fix the
Android11 hardware cancellation failure. Legacy focus diagnosis reuses the
existing dump; modern shell focus tracing is N/A. App queue and independent
fixture observations also run on the Android16 emulator.

```mermaid
flowchart TB
    C[Existing cancellation request] --> Q[Capture immutable pane and key ownership]
    Q --> W[Existing bounded worker queue]
    W --> D[Shell evaluates original routing guards]
    D -->|Legacy cancelled UP| F[Reuse existing focus dump and strict predicate]
    F -->|False| S[Record actual suppression and return]
    F -->|True| I[Original asynchronous injection]
    D -->|Other accepted event| I
    I --> A[Independent native fixture callback]
    A --> R[Existing report with instance and serial]
    R --> T[Original delivery assertions and deadline]
    T -->|Failure| E[Latch failure then independent late-release cleanup]
    Note[Missing or truncated trace is inconclusive<br/>Submission is not observed delivery]
```

```mermaid
sequenceDiagram
    participant M as App owner
    participant Q as Input worker
    participant D as Shell helper
    participant A as Native fixture
    participant T as Acceptance
    M->>M: Capture workspace, pane, display and held-key tuple
    M->>Q: Original cancellation enqueue
    Note over M,Q: Accepted cancellations still drain during detach
    Q->>D: Existing relayKey call
    opt Android11 cancelled UP
        D->>D: Original focused call once, reuse its dump
        D->>D: Record actual strict result
    end
    alt Existing guard suppresses
        D-->>Q: Original return, record suppression
    else Original route injects
        D->>A: Original async injection once
        D-->>Q: Record submission result or original error
        A-->>T: Existing report, instance/task/display/UID/serial and callbacks
    end
    T->>T: Original15second delivery assertion
    opt Assertion fails
        T->>T: Latch original failure and deadline report
        T->>M: Original close-menu and one late physical UP
        T->>Q: Existing fence and150ms no-duplicate observation
        T->>T: Rethrow primary failure, retain independent cleanup errors
    end
```

The fixture caches one initial task identity query in onCreate; later task freshness
still belongs to existing nativeReady checks. Lifecycle logs activate at the synthetic
key sequence and include independently available destroy callbacks. The fixture
retains the last32content-free lifecycle/focus/key callbacks per
instance. Sequence down-times and each event-time are fresh. During key acceptance,
reports from a different instance/task/display/UID or older serial cannot replace
the bound observation; these reported fields are diagnostic metadata, not sender
authentication. Callback retention is checked against the sequence baseline.
Missing/truncated evidence prevents a conclusive diagnosis, never converts a
failed assertion into a pass. Instrumented performance is diagnostic only.
