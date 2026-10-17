# Named monitor profiles and remembered bindings

[Code map and atlas](../README.md) | [Owning contract](../../specs/monitor-profiles.md)

## Scope and source

MonitorProfiles is the pure profile/binding model; MonitorProfilePreferences applies its namespace in one SharedPreferences editor operation; disk persistence is asynchronous. SessionService resolves discovered monitor identity, working configuration and apply/preview lifecycle. Display IDs are never persistent monitor identity.

Source entry points: [MonitorProfiles.java](../../../app/src/main/java/dev/dexdroid/MonitorProfiles.java); [MonitorProfilePreferences.java](../../../app/src/main/java/dev/dexdroid/MonitorProfilePreferences.java); [MonitorProfilesPanel.java](../../../app/src/main/java/dev/dexdroid/MonitorProfilesPanel.java); [SessionService.java](../../../app/src/main/java/dev/dexdroid/SessionService.java). Read current symbols and requirement checks
before changing behavior; this page is a high-level map, not an exhaustive call graph.

## Component overview

```mermaid
flowchart LR
    Discovery[Monitor identity and signal modes] --> Session[SessionService profile resolution]
    Panel[Shared MonitorProfilesPanel] --> Model[MonitorProfiles model]
    Store[MonitorProfilePreferences] <--> Model
    Model --> Session
    Session --> Preview[Display preview and confirmation]
    Preview --> Store
```

## Representative sequence

```mermaid
sequenceDiagram
    participant S as SessionService
    participant M as MonitorProfiles
    participant P as MonitorProfilePreferences
    participant UI as Profile panel
    participant D as Display configuration
    S->>M: Resolve stable monitor identity
    M-->>S: Working override, valid binding or recommended defaults
    UI->>M: Create, rename, update or choose profile
    UI->>S: Apply selected configuration to current monitor
    S->>D: Use reversible preview transaction
    alt Confirmed current preview
        alt Named profile apply
            S->>M: Bind profile to monitor and clear working override
        else Manual display edit
            S->>M: Remember working configuration
        end
        M->>P: Apply one namespace editor update
        P->>P: SharedPreferences.apply with asynchronous disk write
    else Revert, timeout or failed preview
        S->>D: Restore prior native configuration
        S->>S: Preserve stored profile association
    end
    Note over S,M: Reconnect rediscovery uses monitor identity, not old display ID
```

## Change and verification boundaries

Validate names and missing/deleted bindings; rejected previews retain the stored association. SharedPreferences.apply does not acknowledge durable disk success, so this sequence does not promise synchronous persistence-failure reporting. Monitor identity fallback and port/adapter changes can require reassignment. Profile confirmation uses existing display preview behavior rather than inventing another resize path.

See [requirements and explicit gaps](../../requirements.json). Diagram arrows
describe current responsibilities/intended paths, not successful runtime verification.
