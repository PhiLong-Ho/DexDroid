# Two managed panes and transactional layout

[Code map and atlas](../README.md) | [Owning contract](../../specs/split-view.md)

## Scope and source

One physical host contains two TextureViews and one monitor shell. SplitWorkspace owns the second helper/display, staged layout and pane input. SplitLayout is pure geometry; confirmed enabled/direction/ratio/order are durable, native IDs and picker/contact state are transient.

Source entry points: [SplitWorkspace.kt](../../../app/src/main/kotlin/dev/dexdroid/SplitWorkspace.kt); [SplitLayout.kt](../../../app/src/main/kotlin/dev/dexdroid/SplitLayout.kt); [SplitLayoutPreferences.kt](../../../app/src/main/kotlin/dev/dexdroid/SplitLayoutPreferences.kt); [LayoutPanel.kt](../../../app/src/main/kotlin/dev/dexdroid/LayoutPanel.kt); [LauncherControls.kt](../../../app/src/main/kotlin/dev/dexdroid/LauncherControls.kt); [SecondaryDeskService.kt](../../../app/src/main/kotlin/dev/dexdroid/SecondaryDeskService.kt); [SessionService.kt](../../../app/src/main/kotlin/dev/dexdroid/SessionService.kt). Read current symbols and requirement checks
before changing behavior; this page is a high-level map, not an exhaustive call graph.

## Component overview

```mermaid
flowchart TB
    Shared[LayoutPanel and LauncherControls] --> S[SessionService]
    S --> W[SplitWorkspace]
    W --> Geometry[SplitLayout and shared Geometry]
    W --> Prefs[SplitLayoutPreferences]
    W --> Primary[Primary DeskService display]
    W --> Secondary[SecondaryDeskService display]
    Geometry --> Host[Two textures in one rotated physical host]
    W --> Input[Bounded pane input and contact owners]
```

## Representative sequence

```mermaid
sequenceDiagram
    participant UI as Shared mode or layout action
    participant S as SessionService
    participant W as SplitWorkspace
    participant H as Host staged layout
    participant D as Two shell display owners
    UI->>S: Direction, Single, ratio, swap or fullscreen
    S->>W: Configure captured workspace
    W->>W: Invalidate pickers and fence pane input
    W->>H: Stage geometry and await measured native bounds
    H-->>W: Surface preparation completes
    W->>D: Worker resizes and attaches native surfaces
    alt Current transaction succeeds
        W->>W: Commit layout and saved preferences
        W-->>UI: Ready and resume only latest valid picker
    else Resize fails
        W->>D: Restore previous native layout
        alt Rollback succeeds
            W-->>UI: Report failure and rejected picker stays inert
        else Rollback fails
            W->>S: Stop current session and report recovery
        end
    end
    Note over S,W: Workspace and generation guards<br/>reject stale callbacks
```

## Change and verification boundaries

Native navigation stays within the selected app's Android task history. The
[legacy readiness helper](../../../app/src/main/kotlin/dev/dexdroid/LegacyInputWindow.kt)
focuses the existing owned task without Recents activation and validates its
display, top task, component and UID before accepting readiness. App root exit
does not release either pane display or restore the phone controller over an app
the user intentionally opened there.

```mermaid
sequenceDiagram
    participant Host as Phone or physical monitor input
    participant W as SessionService and SplitWorkspace
    participant D as Captured pane shell helper
    participant A as Android app task history
    Host->>W: Native Up contact or targeted Back key
    W->>D: Route to captured pane and owned display
    opt Android 11 readiness changes
        D->>A: Focus existing task without Recents
        D->>D: Revalidate task, display, component and UID
    end
    alt Legacy target stale or input window unavailable
        D-->>W: Drop complete contact or key and report pending
    else Current target ready
        D->>A: Inject native input
        A->>A: Navigate child, return to parent or finish root
        A-->>W: Observer reports current task or empty pane
        alt Underlying task remains
            W-->>Host: Keep its app visible and controllable
        else Pane empty
            W-->>Host: Show pane-local Apps launcher
        end
    end
    Note over Host,A: Other pane, display identities and phone foreground choice remain intact
```

Callbacks and picker continuations validate workspace, generation and operation serial. Actual surface waits, Android 11 compositor-detachment fencing and fresh secondary binding tags are detailed in ARCHITECTURE.md. Contact/key target ownership survives selection changes. Earlier Android 11/16 consolidated full-suite runs are historical context; the current navigation candidate's outcomes and gaps are in the [change record](../../changes/legacy-navigation-focus.md). Forced privileged root migration remains a separate unresolved Samsung diagnostic; physical hardware latency and during-Fit static appearance remain gaps. These sequences are not a compatibility guarantee.

See [requirements and explicit gaps](../../requirements.json). Diagram arrows
describe current responsibilities/intended paths, not successful runtime verification.

Modern initial-DOWN rejection uses the same per-pane DeskService ownership. The
[input sequence](input-and-navigation.md) details stable target/window diagnosis,
checked verifier cancellation, complete-stream suppression and fresh retries.
Unknown failures retain explicit teardown rather than claiming safe recovery.

The migration's failed Android11 held-key case uses the bounded debug-only
[diagnostic sequence](input-and-navigation.md#debug-only-held-key-diagnosis).
SplitWorkspace captures mutable key/pane state on its app owner before enqueue;
worker records use that immutable tuple. The original64event queue and accepted
cancellation drain remain intact. DeskService records its actual suppression or
injection decision without retries/focus changes, and the separate fixture reports
its own delivery callbacks. The original deadline failure is retained through
late-release cleanup; this instrumentation does not establish a product fix.
