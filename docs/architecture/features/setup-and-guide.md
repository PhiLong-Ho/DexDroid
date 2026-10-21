# Shizuku setup and offline guide

[Code map and atlas](../README.md) | [Owning contract](../../specs/setup-and-guide.md)

## Scope and source

ShizukuSetup classifies missing/stopped/permission/connection states and builds shared guidance. SessionService owns permission/binding and retry. UserGuide supplies offline content to both Settings menus.

Source entry points: [ShizukuSetup.kt](../../../app/src/main/kotlin/dev/dexdroid/ShizukuSetup.kt); [UserGuide.kt](../../../app/src/main/kotlin/dev/dexdroid/UserGuide.kt); [SessionService.kt](../../../app/src/main/kotlin/dev/dexdroid/SessionService.kt); [MainActivity.kt](../../../app/src/main/kotlin/dev/dexdroid/MainActivity.kt); [MonitorShell.kt](../../../app/src/main/kotlin/dev/dexdroid/MonitorShell.kt). Read current symbols and requirement checks
before changing behavior; this page is a high-level map, not an exhaustive call graph.

## Component overview

```mermaid
flowchart LR
    UI[Phone or monitor Settings] --> Setup[ShizukuSetup state classifier]
    Setup --> Action[Install, open, authorize or retry guidance]
    Action --> Phone[External setup destination on display 0]
    Broker[Shizuku permission and binding callbacks] --> Session[SessionService]
    Session --> Setup
    Guide[UserGuide offline sections] --> UI
```

## Representative sequence

```mermaid
sequenceDiagram
    participant UI as Settings or status
    participant P as ShizukuSetup
    participant S as SessionService
    participant X as External Shizuku app on phone
    UI->>P: Probe current package, binder and permission state
    P-->>UI: Supported action and current guidance
    alt Missing or stopped service
        UI->>X: Explicit install/open destination on display 0
        Note over UI,X: User completes supported Shizuku startup and no silent root/start bypass
    else Authorization needed
        UI->>S: Request supported permission flow
    else Ready but disconnected
        UI->>S: Retry binding
    end
    S-->>UI: Current binding/permission outcome
    opt Open User guide
        UI->>UI: Render shared offline sections
    end
```

## Change and verification boundaries

Startup after reboot still depends on supported Shizuku startup. No automatic permission bypass, APK download/install or unrestricted shell access is implied by opening guidance. Setup on the monitor must not accidentally launch an external settings app into a managed desktop.

See [requirements and explicit gaps](../../requirements.json). Diagram arrows
describe current responsibilities/intended paths, not successful runtime verification.

## Explicit shutdown exception

The separately confirmed [Turn off debugging](debugging-off.md) page reuses this
Settings entry surface but owns a distinct mutation flow. SessionService holds
started/foreground ownership through serial display cleanup, an audio-worker
acknowledgement and durable pending/terminal receipt. Only the primary helper can
turn the three fixed flags off. Setup still never enables flags or starts Shizuku.

```mermaid
flowchart LR
    Settings --> Confirm[Explicit debugging-off confirmation]
    Confirm --> Owner[Retained session operation owner]
    Owner --> Display[Serial display cleanup]
    Owner --> Audio[Audio release acknowledgement]
    Display --> Gate[Both confirmed and attempt current]
    Audio --> Gate
    Gate --> Helper[Primary fixed disabling IPC]
    Helper --> Receipt[Verified or partial phone receipt]
    Owner -->|Loss or timeout| Unknown[Unknown receipt and manual recovery]
```
