# Protected-content detection and guidance

[Code map and atlas](../README.md) | [Owning contract](../../specs/protected-content.md)

## Scope and source

The shell observes visible secure-window metadata only while its desktop is occupied. It emits a three-state result through IDesktopListener. SessionService owns notice state; shared guide text neither identifies Chrome mode nor changes protection flags.

Source entry points: [ProtectedWindowObserver.kt](../../../app/src/main/kotlin/dev/dexdroid/ProtectedWindowObserver.kt); [ProtectedWindows.kt](../../../app/src/main/kotlin/dev/dexdroid/ProtectedWindows.kt); [DeskService.kt](../../../app/src/main/kotlin/dev/dexdroid/DeskService.kt); [SessionService.kt](../../../app/src/main/kotlin/dev/dexdroid/SessionService.kt); [ProtectedScreenGuide.kt](../../../app/src/main/kotlin/dev/dexdroid/ProtectedScreenGuide.kt). Read current symbols and requirement checks
before changing behavior; this page is a high-level map, not an exhaustive call graph.

## Component overview

```mermaid
flowchart LR
    Occupancy[DesktopTasks occupancy] --> Observer[ProtectedWindowObserver isolated worker]
    Observer --> Metadata[Bounded shell window metadata]
    Metadata --> Fields[Complete standalone or native joined visibility field]
    Fields --> Parser[ProtectedWindows tri-state parser]
    Fields --> Unknown[Malformed or duplicate fields stay unknown]
    Parser --> Callback[One-way display and state callback]
    Callback --> Guard[Session generation and display guard]
    Guard --> Notice[Episode notice and shared Help]
```

## Representative sequence

```mermaid
sequenceDiagram
    participant T as Task observer
    participant P as Protection worker
    participant S as SessionService main
    participant UI as Monitor notice and Help
    T->>P: Occupied desktop state
    loop Nonoverlapping scan while occupied
        P->>P: Bounded metadata read and parse
        Note over P: Accept standalone isOnScreen or known seamless-rotation joined line
        Note over P: Missing, malformed or duplicate visibility stays unknown
        P-->>S: One-way protected, clear or unknown result
        alt Current known result
            S->>S: Update current protection episode
            S-->>UI: Show or clear notice
        else Unknown or obsolete callback
            S->>S: Keep confirmed state or discard obsolete result
        end
    end
    UI->>UI: Help or dismiss consumes full contact
    opt Empty desktop
        T->>P: occupied false and skip scans while worker remains alive
        S->>S: Clear current episode state
    end
    opt Stop, disconnect or helper teardown
        S->>P: Close observer through shell helper teardown
        S->>S: Clear current episode state
    end
```

## Change and verification boundaries

Never retain window content, titles, URLs or screenshots in product detection. Metadata denial is unknown, not proof of no protection. The notice owns only its hit region; modal Help/outside dismissal cannot click through. Browser flags remain a user-controlled troubleshooting choice.

Android14's native dump can join `isOnScreen` to its seamless-rotation diagnostic.
The parser accepts that complete field and the legacy standalone form. It requires
one unambiguous field per window; malformed/truncated/duplicate/conflicting tokens
stay unknown. Current-display, surfaced-window and secure-flag checks remain
independent. This does not identify incognito or enable secure capture.

See [requirements and explicit gaps](../../requirements.json). Diagram arrows
describe current responsibilities/intended paths, not successful runtime verification.
