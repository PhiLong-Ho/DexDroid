# Change: Service-owned split view

## Contract (written before implementation)

- FR-031/032/033 and DX-WINDOWS-001/002/003; [feature spec](../specs/split-view.md).
- Promote the two-managed-display experiment after the user's Epomaker Split 65
  Bluetooth report: Chrome search followed by calendar event text reached the
  correct field and updated the screen. This is prototype evidence, not a new-build pass.
- Shared Layout on phone/monitor: two apps, direction, 25-75% ratio/divider, swap,
  selected-pane fullscreen/restore, close and explicit End semantics.
- Preserve four rotations/Fit, per-app scroll state, selected/contact/key ownership,
  background service lifetime, handoff and cleanup. Both panes use workspace rotation.
- Duplicate app instances, freeform/overlapping windows, saved pairs and general
  Android IME mirroring are excluded. Third-party/OEM placement/resizing can restrict apps.

## Implementation

- `SessionService` owns `SplitWorkspace`; existing primary helper and separate
  `SecondaryDeskService` each own one virtual display. The existing physical host
  contains both surfaces; there is no Activity-owned second desktop.
- `SplitLayout` handles logical bounds; `LayoutPanel` is the shared current-state
  phone/monitor view. Both app choosers identify their target. Empty-pane controls
  follow workspace DPI, and protected guidance stays inside its protected pane.
- Actual native displays resize together with rollback. Input is fenced before
  geometry changes. Divider movement previews locally and commits release coordinates.
  Fullscreen keeps the hidden app/display; End keeps pane 1 and closes pane 2.
  Secondary resize creates/releases a fresh Surface wrapper like the primary path:
  both views are measured at their new bounds before shell attachment, preventing
  a later TextureView layout from resetting the producer size. Frames previously
  stretched/cropped despite correct native dimensions and input coordinates. A rendered circle check caught that defect;
  it preserves legitimate pinch zoom and accounts for the antialiased scroll ruler.
  Fresh wrappers and pre-draw ordering alone still failed in a full regression.
  Task callbacks could reapply old dimensions during remote resize; staged texture
  bounds now stay authoritative until commit or rollback. Rotation acceptance
  forces that status relayout before completion and checks native rendered pixels.
  The shared single-view path also exposed an old-size compositor crop. Both
  single and split views now measure textures at native app dimensions and rotate
  or Fit their parent workspace. The existing single-view child/reader suite
  passes with that geometry. Detach-only and programmatic SurfaceTexture
  replacement attempts failed and were removed; raw failures remain retained.
  Later visual review rejected a passing run because only pane 2 had a pixel
  oracle: pane 1 retained an old-height crop. The new first-pane edge/content
  assertion covers all four rotations. Android 11 now waits on the worker for
  its uniquely named compositor output to detach before resizing/reattaching;
  missing/unknown/timeout state fails into rollback or Stop. See the retained
  pre-fix visual rejection separately from its unchanged raw PASS result.
- Complete key metadata and rebased native pointer samples cross shell IPC. Held contacts
  and keys retain their pane. Phone/monitor menus cancel app-held modifiers.
  Monitor contacts win over phone input. The 64-event input queue stops on overflow;
  accepted cancellations drain during teardown. Typed content is never logged by product code.
- Secondary setup has a ten-second deadline. Display callbacks are fenced by
  workspace identity/primary ID; secondary display removal stops both. Native Surface
  parcel references are released on replacement/Stop. Close verifies task removal.
- Android 11 focus changes need one asynchronous frame of settling before split
  DOWN forwarding (16 ms when the host is focused; existing 100 ms bounded fallback
  otherwise). Repeated native clicks catch focus-loss interruptions. Menu release
  avoids duplicating Android's cancellation after native focus has already left.
- Screenshot review caught a background phone-menu test invoking invisible views.
  It now brings the real phone controller forward; visible captures are inspected.
  Native app tests await actual layout/placement rather than global focus or status
  strings, then require native click/reader/protection outcomes unchanged.
- Regression exposed Android 11 Presentation cancellation on phone rotation with
  a physical-monitor forced density override (213 physical/320 overridden).
  The host retains a session only when actual output size/density/rotation are
  unchanged; removal/output changes and explicit Stop still tear down. AOSP's
  [Android 11 Presentation implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-11.0.0_r48/core/java/android/app/Presentation.java)
  checks app/resource metrics, which can differ when insets/configuration change.

- Task handoff now follows an older app revealed beneath the departing task.
  Legacy task visibility alone did not imply an input window: diagnostics showed
  only a stale system-owned letterbox. Android 11 activates the currently owned
  task and waits on the shell input worker for its usable window, with bounded
  polling and a visible retry status. This is separate from event flag normalization.
- Motion events clear source-window/vendor routing bits (JoyUI observed `0x20000`),
  retaining public Android obscuration/gesture flags. Device IDs are virtualized;
  source, pointer IDs, buttons, axes and key metadata are preserved.
- Implemented the Shizuku helper destroy transaction. A scoped cleanup terminated
  118 verified abandoned DexDroid shell helpers after stopping their displays;
  raw selection/process evidence is retained. End/Stop now assert secondary-helper exit.

## Acceptance

Current evidence and exact results: [validation record (evidence withdrawn)](../../testresults/README.md).

- Base `df41f1f4bbbacbd034ab11c9915fcf250b3bbca2`, local `feature/split-view`,
  version 0.12.0/build 32; source/APK hashes bind the uncommitted candidate.
- Commands: Gradle `spotlessApply qualityCheck`; Python requirement/coverage/doc
  validators, tooling unittest discovery, Ruff check/format; sequential real-device
  `run_device_checks.py --serial <authorized Wi-Fi device> --suite all --install`.
- Android 11 Black Shark 4/JoyUI; physical 1920x1200/60 Hz touchscreen; framework
  injected touch/mouse, phone service input and native cross-UID fixtures. Physical
  keyboard reports remain separate. No Android 16/17 device verification claimed.
- Native fixture geometry is published after layout/pre-draw. The diagnostic
  app frame is also checked for an undistorted circle at its expected pane position. Window-size callbacks
  alone can pair new decor dimensions with old child coordinates. Keyboard checks
  wait for native key-up before taking the next release-count baseline. Failed
  attempts are retained, including the test Activity-reopen hang and actual Back
  source/divider-release bugs. Synthetic prototype failures remain unchanged.
- One latency path uses ten warmups and 100 samples: injected physical-host DOWN
  timestamp to different-UID Activity dispatch. It excludes rendering, broadcast
  delivery, physical-finger latency and touch-to-photon time. No baseline/frame-rate claim.
- Lint baseline unchanged. Scoped RTL suppression documents physical pixel
  coordinates for protected-pane placement; hidden input APIs remain shell-only.
- Coverage inventory/report/snapshot are reconciled with current tests. Scenario
  combinations and remaining gaps are explicit in that map and the evidence record.
- No golden baseline or human visual approval is invented. Publication requires final-tree independent approvals. This local validation
  record does not attest a PR, push, release, approved golden set or broader hardware support.

## User layout follow-up

The user confirmed Layout works and requested removing the pane focus highlight,
putting Layout in the control panel, and placing Back at the phone pad top-right
with the hint beside it. The shared grid now ends in Layout, Settings entries are
removed, phone Back/hint are independent icon targets, and monitor Back moves to
its controls header. Updated acceptance checks placement, direct entry, native
Back delivery, hint dismissal and absence of pad-input leakage/pane outlines.
The earlier review is feedback on the original preview, not final pixel approval.

Monitor-search regression produced one intermittent hardware Back failure that
passed in isolation. The editor now consumes Back before IME processing, preserves
cancelled-release semantics and logs only action/cancellation, never search text.
Acceptance exercises cancelled and valid platform pre-IME callbacks plus real
framework hardware-key injection, requiring no Back in the underlying app.

## Final lifecycle regression

The final ownership audit found that Shizuku removal identifies a user service
by its args, not its old ServiceConnection. A retired workspace's delayed removal
could therefore destroy a newly bound secondary that reused the same identity.
Each split workspace now uses a unique user-service tag. Acceptance replays the
actual Shizuku removal of a retired binding after a fresh split starts, then
requires unchanged current display IDs and a native click in the new secondary.
The counterfactual run without session isolation fails exactly that invariant
(`20261016T093555.057209Z`); raw evidence is retained. Preliminary approvals of
the earlier contents do not approve this update; two fresh final-tree reviews
are required again.

## Divider ownership review follow-up

Independent review rejected candidate
`7c85f50388eb1fc8cac36a8f9a33d834c11a6a175fabfcfb2924dfdba877fcf0`
because a phone-origin divider DOWN was treated as physical contact, dropping its
matching phone UP and blocking further phone input. Divider state now retains
its input origin; matching release/cancel clears it, and physical takeover
cancels the phone preview without committing it. Acceptance covers phone divider
tap, gesture and touchpad drags, cancellation, takeover and subsequent native
app input. The previous candidate's review verdicts do not approve this change.
A reviewer also found local CRLF in the evidence README; Spotless normalizes it
and the complete quality check is rerun.

## Controller mouse ownership review follow-up

Review rejected candidate
`e62fd64215385a4238aa580823cc8234d26338d7989edbd019ded24f67b4e999`
for a phone-controller mouse/touchpad path bypassing shared pending-focus and
physical-menu guards, and routing directly to pane apps behind monitor menus.
Controller pointer mapping now belongs to the service, which checks all physical
ownership before moving the pointer and offers complete mapped pointer/generic
streams to the monitor shell before app delivery. Acceptance drives the actual
Presentation focus buffer and physical menu contact, then uses phone mouse input
to open/scroll/dismiss a monitor modal with no native app click-through.
