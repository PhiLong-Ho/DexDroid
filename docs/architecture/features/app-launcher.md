# App launcher, discovery and task handoff

[Code map and atlas](../README.md) | [Owning contract](../../specs/app-launcher.md)

## Scope and source

AppCatalog is service-owned, prewarmed and revisioned. Each menu owns a subscription and captured launch choice. DesktopTasks observes only its owned display and does not reparent phone tasks.

The [saved workspace shortcut](workspace-shortcut.md) resolves top/private child
identities to public launcher entries before saving, validates them before
replacement and sequences the existing launch path with explicit completion.
Single/Split launch callbacks expose success/failure to that service-owned sequence;
ordinary picker behavior and shell placement verification remain shared.

Source entry points: [AppCatalog.kt](../../../app/src/main/kotlin/dev/dexdroid/AppCatalog.kt); [LauncherControls.kt](../../../app/src/main/kotlin/dev/dexdroid/LauncherControls.kt); [SessionService.kt](../../../app/src/main/kotlin/dev/dexdroid/SessionService.kt); [LaunchScope.kt](../../../app/src/main/kotlin/dev/dexdroid/LaunchScope.kt); [DeskService.kt](../../../app/src/main/kotlin/dev/dexdroid/DeskService.kt); [ForegroundLaunch.kt](../../../app/src/main/kotlin/dev/dexdroid/ForegroundLaunch.kt); [DesktopTasks.kt](../../../app/src/main/kotlin/dev/dexdroid/DesktopTasks.kt). Read current symbols and requirement checks
before changing behavior; this page is a high-level map, not an exhaustive call graph.

## Component overview

```mermaid
flowchart LR
    UI[Phone or monitor picker] --> Catalog[AppCatalog cache and worker]
    Packages[Package broadcasts] --> Catalog
    Debug[Debug source-set input diagnostic only] --> Catalog
    UI --> Choice[LauncherControls captured destination]
    Choice --> Session[SessionService]
    Session --> Scope[LaunchScope generation display bridge and mode]
    Scope --> Delivery[Current main-thread success or failure only]
    Session --> Shell[DeskService verified launch]
    Shell --> Tasks[Android task placement]
    Shell --> Eligibility[Strict sole hidden managed task and native capability]
    Eligibility --> Completion[ForegroundLaunch per-call native completion]
    Completion --> Tasks
    Tasks --> Observer[DesktopTasks]
    Observer --> Idle[Session occupancy and idle visibility]
    Tasks --> Native[Android composition]
    Native --> Buffer[Owned VirtualDisplay output Surface]
    Buffer --> Host[TextureView and physical host window]
    Host --> Output[Monitor or emulator display output]
```

## Representative sequence

```mermaid
sequenceDiagram
    participant UI as Picker
    participant C as AppCatalog
    participant S as SessionService
    participant D as Shell helper
    participant N as Android transition
    UI->>UI: Show menu shell immediately
    UI->>C: Observe cache and current query
    alt Cold or invalidated catalog
        C->>C: One worker scan and coalesce newer revision
        opt Debug build type only
            C->>C: Add debug InputTestActivity component
        end
        C-->>UI: Current rows or retryable error
    else Warm catalog
        C-->>UI: Searchable cached rows
    end
    UI->>S: Launch using captured picker generation and pane
    alt Current token and valid destination
        S->>S: Hold per-launch capture token and queue work
        Note over S: Single view captures generation, display, bridge before configuration
        S-->>UI: Accepted request closes picker
        S->>D: Explicit targeted launch on owned virtual display
        alt API31+ confirmed sole exact hidden fullscreen task and capability
            D->>N: Entry Intent NEW_TASK with custom task transition
            N-->>D: Per-launch finish callback on owned handler
            D->>D: Bounded worker wait and fresh owner/task/display check
            alt Callback missing or identity changed
                D-->>S: Diagnose failure, no retry or task movement
            else Same visible fullscreen task after finish
                D-->>S: Native foreground completion verified
            end
        else Other launch path or unsupported capability
            D->>N: Existing am placement and guarded fallback
        end
        D-->>S: Placement verification outcome
        S->>D: Fresh owned-display task snapshot after success/failure/discard
        D-->>S: Actual occupancy or unknown
        S->>S: Release only this launch capture token
        S->>S: Recheck generation, display, bridge and single-view ownership on main
        alt Same current owner
            S-->>UI: Publish success and occupancy, or current failure
        else Stop, restart, replacement or split
            S->>S: Discard both obsolete success and failure
        end
    else Closed or replaced picker
        S->>S: Discard obsolete continuation
    end
    D-->>S: One-way task occupancy callback
    S->>S: Validate generation and display before idle update
    Note over S,D: Occupancy describes task ownership, not rendered pixels
```

## Change and verification boundaries

Catalog errors retain previous rows and permit retry; dismissed subscribers cannot
reopen a menu. Phone task handoff is observed, never stolen back automatically.
Samsung forced root-task migration remains an unresolved privileged diagnostic:
logical placement can disagree with rendered composition. Ordinary exported
app-entry phone launch is the acceptance trigger. Historical source/device-bound
passes do not waive failed attempts, including the API34/35 remaining-app failure
also reproduced from Java source on API34. The narrow foregrounding
candidate uses a separate strict task/component/visibility snapshot, a per-call
callback and finite worker wait. Its callback never acquires the service monitor
or queries tasks. Timeout, interruption or moved ownership closes its callback
thread and terminates without falling back after the start RPC. Already-visible,
fresh, phone-fallback and API30 launches retain their existing route; unsupported
native capability is diagnosed before the original route is used. Raw pre-completion
phone overlap remains a failing platform diagnostic, not a completed-launch pass.
The original child scenario and its fresh remaining-app contact pass on API34/35
with frozen generationv4. Native input/Stop overlap remains separately unverified.
Single-view launch success/failure checks LaunchScope before its RPC and at the
actual main-thread status mutation; generic reporting is unchanged. Split uses
its existing current-workspace guard. Task/window metadata alone
does not verify composition; independent emulator output and monitor-window
capture must identify their actual targets. See the
[handoff investigation](../../changes/handoff-rendering.md) and release evidence
for their separate scopes.

See [requirements and explicit gaps](../../requirements.json). Diagram arrows
describe current responsibilities/intended paths, not successful runtime verification.

Each accepted Single launch also retains an independent main-owned capture token
through configuration, dispatch and fresh owned-display reconciliation on the serial
worker. Requested components are published only after native work; failure/discard
queries actual occupancy. Generation teardown retires tokens, and an earlier
completion cannot unlock capture while a later request is outstanding. Saved-workspace
capture independently queries both owned helpers before launcher canonicalization;
see its [capture sequence](workspace-shortcut.md#representative-sequence).


The input diagnostic Activity and manifest declaration live in `src/debug`.
Shared discovery adds its string-identified component only when DEBUG is true
and the build type is debug; official and release-variant diagnostic builds omit
that row. Phone/monitor pickers consume the same list. Legacy task-parser identity
strings do not load the class and remain for safe owned-display cleanup.
