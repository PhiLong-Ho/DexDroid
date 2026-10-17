# Shared controls, menus, icon and version

[Code map and atlas](../README.md) | [Owning contract](../../specs/controller-ui.md)

## Scope and source

Phone and monitor construct separate native views using shared style/actions backed by one service. MenuNavigation owns visited destinations; each surface retains its own editor/scroll/menu lifetime. The launcher icon is a packaged resource; Settings reads installed BuildConfig version.

Source entry points: [MainActivity.java](../../../app/src/main/java/dev/dexdroid/MainActivity.java); [MonitorShell.java](../../../app/src/main/java/dev/dexdroid/MonitorShell.java); [ControllerStyle.java](../../../app/src/main/java/dev/dexdroid/ControllerStyle.java); [MenuNavigation.java](../../../app/src/main/java/dev/dexdroid/MenuNavigation.java); [LauncherControls.java](../../../app/src/main/java/dev/dexdroid/LauncherControls.java); [ScreenInsets.java](../../../app/src/main/java/dev/dexdroid/ScreenInsets.java). Read current symbols and requirement checks
before changing behavior; this page is a high-level map, not an exhaustive call graph.
`MainActivity.beginMenu` owns each phone dialog's native Back listener and pending
flag. It captures the keyboard-visible intent at receipt and posts one pending
action to that dialog's decor/main loop, leaving the old key dispatch before
`menuBack` can replace a window. MenuNavigation remains the shared route owner;
the queue stores no new destination or service state.

## Component overview

```mermaid
flowchart TB
    Phone[MainActivity phone views] --> Commands[SessionService commands and state]
    Monitor[MonitorShell monitor views] --> Commands
    Style[ControllerStyle shared construction] -.-> Phone
    Style -.-> Monitor
    Nav[MenuNavigation visited destinations] --> Phone
    Nav --> Monitor
    Resources[Packaged icons and BuildConfig] --> Phone
    Resources --> Monitor
    subgraph PhoneBack[Phone native Back dispatch]
        Receipt[Current showing dialog and live Activity] --> Intent[Capture IME intent and coalesce pending Back]
        Intent --> Queue[Post on captured dialog decor main loop]
        Queue --> Owner[Recheck dialog owner and Activity lifetime]
        Owner -->|Current| Apply{IME visible at receipt?}
        Apply -->|Yes| Hide[Hide local keyboard]
        Apply -->|No| Back[Call existing menuBack]
        Owner -->|Stale or closed| Discard[Discard queued action]
    end
    Phone --> Receipt
    Back --> Nav
```

## Representative sequence

```mermaid
sequenceDiagram
    actor User
    participant UI as Current surface menu
    participant Q as Phone decor main loop
    participant N as MenuNavigation
    participant S as SessionService
    User->>UI: Open Settings then child page
    UI->>N: Record visited destination and valid parent
    UI->>S: Read current status and settings
    S-->>UI: Authoritative state for shared widgets
    User->>UI: Up or Back
    alt Phone native Back
        UI->>UI: Require current showing dialog and live Activity
        alt Uncancelled key UP and no Back pending for owner
            UI->>UI: Capture IME-visible intent and mark pending
            UI->>Q: Post action bound to captured dialog
            Note over UI,Q: Old dialog key dispatch returns before replacement
            Q->>UI: Clear pending and recheck owner and Activity lifetime
            alt Dialog dismissed or replaced, or Activity ending
                UI->>UI: Discard without navigation or reopening
            else Captured owner still live
                alt IME was visible at receipt
                    UI->>UI: Request keyboard hide using captured decor
                else Navigate Back
                    UI->>N: Resolve valid visited parent
                    N-->>UI: Parent or no target
                    UI->>UI: Rebuild parent with retained state or close root
                end
            end
        else Invalid owner, cancelled event or duplicate pending Back
            UI->>UI: Consume native Back without another queued action
        end
    else Existing Up or monitor Back
        alt Monitor Back with local keyboard visible
            UI->>UI: Hide local keyboard first
        else Navigate to parent
            UI->>N: Resolve valid visited parent
            N-->>UI: Parent or no target
            UI->>UI: Rebuild parent with retained state or close root
        end
    end
    User->>UI: Close or outside
    UI->>N: Discard whole journey
    UI->>S: End own picker/subscription only
    Note over UI,S: Replaced menu cannot apply a stale picker continuation
    Note over UI,Q: Queued phone Back becomes inert<br/>when its owner closes or is replaced
```

## Change and verification boundaries

Phone controls stay portrait/IME-inset safe; monitor omits the phone pad. Keep status/action semantics shared without sharing mutable view instances or icon bounds. Architecture diagrams do not approve actual renders. The installed icon/version paths are static build resources and do not require shell IPC.

The native phone Back guard checks exact dialog identity, showing state and
Activity finishing/destroyed state both before scheduling and before execution.
Repeated key-UP events while that owner has a pending action are coalesced.
Dismissal, Close, replacement and Activity teardown invalidate execution through
these lifetime checks; they do not redirect a stale callback to a newer dialog.
This is guarded main-loop deferral, not a new background worker or a separate
navigation stack. Header Up retains its existing parent action; Close/outside
retain whole-journey dismissal. Keyboard-first Back, parent filtering, picker
ownership, parent query/scroll restoration and layout remain unchanged.

Architecture impact: documentation reconciliation of the approved phone Back
dispatch/owner-lifetime approach. The shared navigation and service ownership
boundaries remain intact. The observed Samsung PhoneWindow missing-content crash
motivates the dispatch change; its exact OEM cause is not established. Native
Back and deterministic duplicate/Close/replacement checks in
[PhoneMenuAcceptance](../../../app/src/androidTest/java/dev/dexdroid/PhoneMenuAcceptance.java)
require their own current test reports; this page claims no runtime pass or fix.

See [requirements and explicit gaps](../../requirements.json). Diagram arrows
describe current responsibilities/intended paths, not successful runtime verification.
