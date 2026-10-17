# Display configuration and per-app settings

[Code map and atlas](../README.md) | [Owning contract](../../specs/display-and-app-settings.md)

## Scope and source

SessionService owns selected output geometry, reversible display preview and per-app rotation/scroll preferences. Geometry maps both pixels and input; physical signal modes, rendering dimensions and UI DPI are distinct.

Source entry points: [SessionService.java](../../../app/src/main/java/dev/dexdroid/SessionService.java); [Geometry.java](../../../app/src/main/java/dev/dexdroid/Geometry.java); [DeskService.java](../../../app/src/main/java/dev/dexdroid/DeskService.java); [ControllerStyle.java](../../../app/src/main/java/dev/dexdroid/ControllerStyle.java). Read current symbols and requirement checks
before changing behavior; this page is a high-level map, not an exhaustive call graph.

## Component overview

```mermaid
flowchart LR
    UI[Shared display and app settings] --> Session[SessionService state]
    Session --> Prefs[Confirmed SharedPreferences]
    Session --> Geometry[Geometry and staged host bounds]
    Geometry --> Worker[Native resize on worker]
    Worker --> Preview[Keep or Revert preview]
    Preview --> Commit[Commit or restore previous configuration]
```

## Representative sequence

```mermaid
sequenceDiagram
    participant UI as Settings
    participant S as SessionService
    participant H as Host layout
    participant D as Shell display
    UI->>S: Preview resolution and UI DPI
    S->>S: Cancel input and stage geometry
    S->>H: Request measured native bounds
    H-->>S: Pre-draw preparation completes
    S->>D: Worker resize and Surface attachment
    alt Resize succeeds for current operation
        S-->>UI: Show Keep or Revert with 15-second timeout
        alt Keep
            UI->>S: Confirm current preview
            S->>S: Persist confirmed configuration
        else Revert or timeout
            S->>D: Restore previous native geometry
            S->>S: Restore previous state
        end
    else Resize or rollback failure
        S->>S: Roll back and stop if recovery cannot restore resources
        S-->>UI: Report current failure
    end
```

## Change and verification boundaries

Persist only confirmed settings; configuration changes cancel pending picker/input work. Keep native buffer dimensions, transformed layout and hit mapping consistent. Signal-mode support remains OEM-dependent; resolution/Fit combinations require explicit verification, not inference from a mode list.

See [requirements and explicit gaps](../../requirements.json). Diagram arrows
describe current responsibilities/intended paths, not successful runtime verification.
