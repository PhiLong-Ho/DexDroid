# Explicit debugging shutdown

[Architecture atlas](../README.md) | [Contract](../../specs/debugging-off.md)

## Scope and owners

Intended contract for the in-progress implementation. Settings shares one panel;
SessionService owns the attempt and service lifetime. SessionAudio acknowledges
actual worker cleanup. The primary DeskService exposes only a fixed no-argument
one-way disabling operation. No general settings or shell executor is exposed.

Source: [SessionService](../../../app/src/main/kotlin/dev/dexdroid/SessionService.kt),
[SessionAudio](../../../app/src/main/kotlin/dev/dexdroid/SessionAudio.kt),
[DeskService](../../../app/src/main/kotlin/dev/dexdroid/DeskService.kt),
[MainActivity](../../../app/src/main/kotlin/dev/dexdroid/MainActivity.kt),
[MonitorShell](../../../app/src/main/kotlin/dev/dexdroid/MonitorShell.kt).

## Overview

```mermaid
flowchart LR
    UI[Shared Settings confirmation] --> Owner[SessionService attempt owner]
    Owner --> Displays[Serial input and pane cleanup]
    Owner --> Audio[Audio worker release acknowledgement]
    Displays --> Gate[Cleanup and live-token barrier]
    Audio --> Gate
    Gate --> Focus[Existing phone controller handoff]
    Focus --> Helper[Primary shell helper fixed operation]
    Helper --> Flags[Three allowed global flags to zero]
    Helper --> Reads[Shell readback and daemon observation]
    Reads --> Receipt[Private phone attempt receipt]
    Owner -->|Interrupted or uncertain| Manual[Unknown result and manual Developer options]
```

## Sequence and failure boundaries

```mermaid
sequenceDiagram
    participant UI as Phone or monitor Settings
    participant S as SessionService
    participant A as Audio worker
    participant W as Desktop serial worker
    participant H as Primary shell helper
    UI->>S: Explicit confirmation
    S->>S: Persist pending receipt and retain operation lifetime
    S->>S: Block Start and cancel input and monitor UI
    S->>A: End owned audio epoch
    S->>W: Stop both displays and confirm cleanup
    A-->>S: Confirmed release or uncertainty
    W-->>S: Confirmed cleanup or uncertainty
    alt Current token and all cleanup confirmed
        S->>W: Focus existing phone controller
        W->>H: Fixed disable-debugging operation
        H->>H: Wireless off then master off then USB off
        H->>H: Read each flag independently and inspect daemon
        H-->>S: Per-switch observations
        S->>UI: Phone receipt with no automatic restart
    else Cleanup uncertain or stale or helper lost
        S->>UI: Unknown or blocked with manual phone recovery
    end
    Note over S,H: Timeout invalidates queued work and completed writes are never reversed
```

## Invariants and verification boundaries

Retain foreground/start ownership until terminal result or bounded timeout; bound
UI alone is insufficient. Recheck operation token immediately before helper entry.
Desktop/audio uncertainty prevents writes. Helper loss after writes is unknown,
not success. A direct master-flag write is not the Android Settings UI controller
cascade. Legacy TCP ADB may exist while adb_wifi_enabled is zero; report daemon
observations separately. Product never enables a switch or clears remembered
pairing/authorizations. Another app or later Shizuku startup may enable flags.

Diagrams describe the intended contract, not current runtime verification. See
[change record](../../changes/debugging-off.md) for actual checks and gaps.

DebuggingOffPanel keeps recovery disclosure state local to the page. Generic
manual checks link to the phone's main Settings with ACTION_SETTINGS; the collapsed
Use the desktop again section describes deliberate later setup. Neither navigation
nor disclosure calls the privileged helper. Named OEM examples stay in UserGuide.
No permission, service or persisted-state boundary changes in this UI follow-up.
