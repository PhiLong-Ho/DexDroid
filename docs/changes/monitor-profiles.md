# Change: named monitor profiles

## Contract (before implementation)

Baseline FR-020/021/022 and DX-MONITOR-001/002/003 define multiple named resolution,
UI DPI and default-rotation snapshots, explicit switching, remembered monitor
bindings, safe preview/rollback and one-time legacy import. The
[feature specification](../features/monitor-profiles.md) records journeys and limits.

## Implementation

- `MonitorProfiles` owns pure snapshots, name validation, CRUD, canonical identity,
  bindings, confirmed working overrides and import markers. `MonitorProfilePreferences` atomically persists only
  its own namespace, leaving app overrides and other settings intact.
- `SessionService` applies profile settings through the existing geometry/resize
  path. Keep commits the binding; Revert/timeout/Stop discard it. Rotation/input
  cancellation, stale worker generation guards and queued app launches preserve
  the preview boundary. Reconnect/startup restores the selected monitor default;
  per-app and one-time launch rotation still wins on app launch. Manual default
  rotation updates the working configuration only after successful resize. Stop
  retains the texture until the worker has detached the shell display.
- `MonitorProfilesPanel` shares list/editor/actions across phone and monitor with
  Settings row tokens, vector icons, current binding/modified status, scrolling
  and explicit delete confirmation. Monitor naming reuses local keyboard input.
  The local keyboard places its caret after the characters accepted by input
  filters, so typing/replacing text at the name limit remains safe.
- Ordinary Resolution/DPI Keep saves a per-monitor working override separately
  from named snapshots. Restart restores it even after Forget or migration.
  Applying/saving a profile clears it; updating the bound profile clears only the
  current monitor's override. Forget/delete remove affected overrides.
- Reflection tries Android's physical identity without bypassing hidden-API
  policy. Capability matching remains available and disclosed when inaccessible.
  Identical identities share a binding; there is no promise to distinguish them.

Architecture references: [Android display identification](https://source.android.com/docs/core/display/multi_display/displays)
describes stable physical identifiers subject to hardware support. This app treats
identity access as optional and never stores Android's temporary display ID.

## Acceptance (after implementation)

| Criterion | Test | Current result |
| --- | --- | --- |
| CRUD, names, UTF-8, corruption and configuration bounds | MonitorProfilesTest | PASS; 18 JVM cases |
| Distinct bindings, canonical identity, import/forget/delete persistence | MonitorProfilesTest | PASS |
| Confirmed working settings after binding/Forget and override reset | MonitorProfilesTest | PASS |
| Geometry/input calculations | GeometryTest | PASS; 72 JVM cases |
| Phone/monitor profile menus, local names/name-limit boundary, four rotations | MonitorProfilesAcceptance | PASS on Black Shark 4 |
| Real display apply/Revert/Keep/timeout/restart/Stop and manual-default persistence | MonitorProfilesAcceptance | PASS on Black Shark 4 |
| Formatting, lint, app/test builds and tooling | Host checks | PASS; 83 tooling tests |
| Rendered captures | Agent inspection | Agent inspected; user approved both layouts |
| Physical two-monitor reconnects | Device procedure | Unverified |

[Host evidence (evidence withdrawn)](../../testresults/README.md)
retains commands and result files for the working candidate. Requirement coverage
and source fingerprints are refreshed with tests. Lint baseline is unchanged.

[Native profile run (evidence withdrawn)](../../testresults/README.md)
retains real-device outcomes and the inspected phone/monitor captures. The
[full connected run (evidence withdrawn)](../../testresults/README.md)
also passed all seven suites and controller restoration on those APKs. The
[final stopped-rotation run (evidence withdrawn)](../../testresults/README.md)
passed after the subsequent service-only rotation refinement. The
[final full connected run (evidence withdrawn)](../../testresults/README.md)
then passed all seven suites on the final APKs. Wireless
ADB was enabled through authorized USB; Shizuku and the debug build were started.
JoyUI denies physical-ID reflection; disclosed capabilities matching is active.

Two earlier runs are retained as failures in the [failure record (evidence withdrawn)](../../testresults/README.md).
The first exposed missing final status in formatted Android instrumentation;
raw `-r` output now retains it without weakening success detection. The second
passed assertions but controller restoration timed out following WindowManager's
invalid-surface error/system-service restart. Stop now keeps the backing texture
until shell display teardown; native cleanup waits for virtual-display removal.
The subsequent complete profile runner passed, including controller restoration.

Human approval of both layouts is [recorded with capture hashes (evidence withdrawn)](../../testresults/README.md).
The stopped-rotation regression uses different remembered and runtime geometry
to ensure Start preserves the detected monitor configuration. Physical monitor
switching remains a separate gap. Push/merge/release only after two fresh reviews
and required CI; release artifacts/report are generated from the merged commit.
General raw system logs remain local; selected
feature evidence is retained without republishing unrelated application logs.

Still unverified: resize failure injection, physical cable removal during preview,
two real monitors/identical adapters, process death, third-party continuity, large
font/profile-list combinations and measured latency/frame distributions.
