# Two managed panes and transactional layout

[Code map and atlas](../README.md) | [Owning contract](../../specs/split-view.md)

## Scope and source

One physical host contains two TextureViews and one monitor shell. SplitWorkspace owns the second helper/display, staged layout and pane input. SplitLayout is pure geometry; confirmed enabled/direction/ratio/order are durable, native IDs and picker/contact state are transient.

Source entry points: [SplitWorkspace.java](../../../app/src/main/java/dev/dexdroid/SplitWorkspace.java); [SplitLayout.java](../../../app/src/main/java/dev/dexdroid/SplitLayout.java); [SplitLayoutPreferences.java](../../../app/src/main/java/dev/dexdroid/SplitLayoutPreferences.java); [LayoutPanel.java](../../../app/src/main/java/dev/dexdroid/LayoutPanel.java); [LauncherControls.java](../../../app/src/main/java/dev/dexdroid/LauncherControls.java); [SecondaryDeskService.java](../../../app/src/main/java/dev/dexdroid/SecondaryDeskService.java); [SessionService.java](../../../app/src/main/java/dev/dexdroid/SessionService.java). Read current symbols and requirement checks
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

Callbacks and picker continuations validate workspace, generation and operation serial. Actual surface waits, Android 11 compositor-detachment fencing and fresh secondary binding tags are detailed in ARCHITECTURE.md. Contact/key target ownership survives selection changes. The initial Android 16 pane-2 picker failure is retained privately. Current source-bound Android 11/16 consolidated runs pass the full split suite, including the native picker, ordinary phone handoff and four-rotation input. Forced privileged root migration remains a separate unresolved Samsung diagnostic; physical hardware latency and during-Fit static appearance remain gaps. This sequence is not a compatibility guarantee.

See [requirements and explicit gaps](../../requirements.json). Diagram arrows
describe current responsibilities/intended paths, not successful runtime verification.
