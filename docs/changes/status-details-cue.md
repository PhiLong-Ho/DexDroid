# Status details text cue: 0.12.3

## Contract and decision

User finds both bottom-centered and inline chevrons visually awkward beside Play.
UI expert `/root/status_disclosure_ui` reviewed actual 0.12.2 phone capture and
shared StatusCard source and proposed leading-aligned Show details / Hide details
beneath the summary, retaining an independent trailing Start/Stop. User approved
with "Proceed". SPEC/DX-UI-001 updated before implementation. No new popup or tap.

No approved per-state reference catalog exists. Latest inspected 0.12.2 capture
is a historical reference for this change, not current visual acceptance. Keep
new raw evidence private; review actual updated phone/monitor renders separately.

## Impact and bounded verification

Local shared view presentation only: same StatusCard, preference/action owners,
service/IPC/input queues, display geometry and settings schema. Architecture
preflight N/A for unchanged boundaries; every-release architecture review remains
mandatory. Do not change underlying monitor rotation/input behavior.

User requested no all-suite rerun for this minor release. Run the two existing
affected UI/monitor suites (including their status/Stop, keyboard and four-rotation
checks) instead of all eleven device suites. Preserve mandatory host CI and final
review gates. Update assertion coverage for the new cue and retain failures/gaps.
No performance improvement or full OEM/physical/font/Fit matrix claimed.
Android-specific UI change requires no reusable template workflow update.

## Outcomes

Shared StatusCard now puts the 12sp secondary-text cue below the monitor summary
inside its existing header, with 12dp bottom padding. Removed the obsolete arrow
resource after lint identified it as unused. Body action/state and independent
48dp desktop action are unchanged. README, offline guide and architecture agree.

Final qualityCheck passes (140 JVM cases, no new lint); final tooling has 160
passes and one Windows skip out of 161 cases. Coverage/requirements/docs/Ruff pass.
Final phone UI `20261017T162222.867526Z` passes in 35 seconds; monitor
`20261017T162314.792169Z` passes in 107 seconds on the Android 16 emulator with
1920x1200 emulator second display. Monitor reports 72dp collapsed cards in all
four rotations. App/tool fingerprints bind 126 files; exact APK identities and
raw outcomes remain private. Two affected suites ran, not all eleven.

Initial monitor setup failed before assertions (Shizuku client unattached); opening
the controller restored attachment. The next run passed landscape then caught our
new assertion comparing widths against rotated screen axes at 90 degrees. Corrected
the assertion to compare descendant rectangles in card coordinates; no product
logic change or weakened bounds. Reran quality/phone UI/monitor and final tooling.
Retain original failures. An initial monitor wrapper could not directly launch a
Windows batch file; invoking cmd/c fixed launch. An initial diagram note used a
Mermaid-reserved semicolon; corrected prose and inspected both successful renders.
Initial phone iterations used versionName 0.12.2/build 36 before a missed version
literal was corrected; their ignored 0.12.2 run is not counted as final acceptance.

UI expert `/root/status_disclosure_ui` explicitly approves eight final phone/monitor
captures at 2026-10-17T16:25Z (minute precision): three phone states and controls plus
expanded monitor states at all four rotations. Root inspected current layouts and
both updated architecture renders. No golden catalog was promoted. Physical/OEM,
broad long-name/font/DPI/Fit matrix, accessibility-device and hardware latency gaps
remain unverified. Existing suite outcomes are not universal scenario coverage.

See [candidate evidence](../../testresults/0.12.3/README.md) for private storage and
scope. Official signing/publication and in-place phone updates remain after actual
release architecture and two fresh final code approvals.

## Release architecture attestation

- Target 0.12.3 / build 36; published baseline v0.12.2,
  `fae1e801cb6ad6be43aa8d0848efef124f0e5048`.
- Frozen pre-attestation tree `23d8877748b84eb518f585c2a6591bc874095c91`;
  digest `f635d7ca66e975b43c3274bc9e6ea9e344f18d3c4ad583682c24f9e61b71845f`.
- Fresh read-only expert `/root/release123_architecture` explicitly **APPROVE** at
  `2026-10-17T16:27:28Z`. Inspected cumulative 21-file diff, whole-product/component
  atlas, controller/session/input/profiles/split/release owners, actual source,
  tests, requirements/coverage, diagrams/renders and private evidence after CRG query.
- Findings: phone/monitor share cue construction and service-owned expansion;
  independent desktop action retained. No IPC, privilege, queue, lifecycle, global
  display geometry, persistence schema or dependency change. Stop cleanup, stale
  generation/host rejection, trust and rollback ownership remain intact. Existing
  generic unbounded session queue remains disclosed; this patch adds no queue.
- Both changed controller diagrams agree with source; no material drift found.
  Four final sidecars match all 126 current source files; final UI/monitor metadata
  bind the same APK pair and successful outcomes. Archive integrity, eight reviewed
  render hashes, privacy, coverage, requirements and documentation checks pass.
- No blockers. Two affected device suites only; physical/OEM/Android 11 reruns,
  broad long-name/font/DPI/Fit matrix, accessibility-device/golden/hardware performance
  remain unverified. Historical failures are separate. No measured speedup claimed.
- Protected PR/host, release signing/report/archive validation, publication and
  production installs remain subsequent gates. This faithful attestation is the
  only change after the architecture snapshot; two different fresh final reviewers
  must approve the full final digest including it.
