# Reserved monitor taskbar

[Contract](../../specs/monitor-taskbar.md) | [Code map](../README.md)

## Scope and source

FR-045 / DX-TASKBAR-001 adds monitor navigation without edge gestures. User chose
reserved bottom space. Fresh expert `/root/reserved_taskbar_arch` approved this
contract at 2026-11-13T16:42:30Z; this is architecture preflight, not acceptance.

[DesktopViewport](../../../app/src/main/kotlin/dev/dexdroid/DesktopViewport.kt)
partitions full output into top-aligned app content and a bottom strip when edges
are Off: round(4% × min(output width,output height)), independent of app DPI.
MonitorShell passes this pixel height to icon-only controls; targets, glyphs and
spacing use that same scale, including on reappearance after geometry changes;
the partition and transaction ownership stay unchanged.
[SessionService](../../../app/src/main/kotlin/dev/dexdroid/SessionService.kt)
owns committed preferences and native display resize. `width()/height()` retain
full-output semantics for Geometry/Fit, shell, cursor and phone normalization;
`appWidth()/appHeight()` define native content. No derived dimensions persist in
monitor profiles. [MonitorShell](../../../app/src/main/kotlin/dev/dexdroid/MonitorShell.kt)
owns [MonitorTaskbar](../../../app/src/main/kotlin/dev/dexdroid/MonitorTaskbar.kt)
and all shell input. [SplitWorkspace](../../../app/src/main/kotlin/dev/dexdroid/SplitWorkspace.kt)
divides only content, while mapping physical points through full output first.
[IdleDesktop](../../../app/src/main/kotlin/dev/dexdroid/IdleDesktop.kt) hides its
Apps child independently from its background using one shared preference.

## Component overview

```mermaid
flowchart TB
    Settings[Phone and monitor Settings] --> Session[SessionService committed preference]
    Session --> Viewport[DesktopViewport: full output and app partition]
    Viewport --> Host[Monitor full-output rotation and Fit]
    Host --> Texture[Single native texture: top app area]
    Host --> Split[Split panes: divide app area only]
    Viewport --> Shell[MonitorShell full-output UI and hit routing]
    Shell --> Bar[Reserved bottom Apps and Controls]
    Bar --> Menus[Existing launcher or Controls and screen-on recovery]
    Physical[Touch and mouse] --> Mapping[Physical to full logical output]
    Mapping --> Shell
    Shell -->|Not shell-owned and inside app area| Native[Current native app or pane input]
    Pad[Phone pointer: full output] --> Shell
```

## Representative sequence

```mermaid
sequenceDiagram
    participant U as Edge setting
    participant S as SessionService
    participant H as Monitor and split surfaces
    participant D as Owned native displays
    U->>S: Request edges Off
    S->>S: Reject busy or preview conflict
    S->>S: Capture old/new viewport and cancel held input
    S->>H: Stage full output and smaller native app surfaces
    H-->>S: Measured layout ready
    S->>D: Resize primary and secondary on existing workers
    alt Success for current generation
        S->>S: Commit preference and content geometry
        S->>H: Clear staging and show reserved taskbar
    else Resize failure
        S->>H: Stage old native bounds
        S->>D: Restore old dimensions
        S->>S: Keep old preference or stop if rollback fails
    end
    U->>H: Tap taskbar Controls
    H->>H: Capture full stream and open existing modal
    Note over H,D: No app click-through<br/>Focus changes only on menu entry
```

## Change and verification boundaries

Native texture height and native display height must agree; cropping a full-height
buffer is outside this design. Staging separates full-output transform dimensions
from native content measurements. Edge changes use the existing fenced resize,
generation guards and rollback; stopped preference changes apply on next create.

Physical inverse mapping always uses full output. Shell routing precedes app-touch
enablement and app admission. App contacts crossing out of content cancel and
cannot resume without a fresh Down; taskbar contacts retain shell ownership through
release/cancel. The entire strip consumes input, including blank space. Generic
mouse/wheel paths also enter shell routing before the split path. Menus remain
modal with existing keyboard-first Back, Up, Close and outside dismissal.

Phone-screen restore remains in Controls even when its optional button preference
is Off. Taskbar visibility depends on edge navigation alone. No Power interception,
new IPC/worker, root permission or global keyboard shortcut is introduced.

Required evidence: exact native content sizes, actual bottom app input, single and
split/maximum layout, four rotations/Fit, touch/pointer/mouse and cancelled streams,
toggle rollback, empty backgrounds, keyboard/focus and screen-on recovery. Unrun
dimensions remain gaps; source diagrams do not establish runtime correctness.
