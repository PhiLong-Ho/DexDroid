# 0.20.0 verification index

[Release scope](../../docs/changes/release-0.20.0.md) separates verified behavior,
source/build reuse and gaps. No mapping count or screenshot implies all scenarios.

| Scope | Evidence index |
| --- | --- |
| Physical Android11 Black Shark taskbar/input/phone UI/monitor; safe power-unavailable | [Physical compatibility](android11-physical/summary.md) |
| Android11 emulator taskbar/input/phone UI/monitor and safe power-unavailable | [Compatibility](android11-compat/summary.md) |
| Current318 JVM/210 tooling pass, one Windows skip | [Local host](release-host/summary.md) |
| Physical Android16 phone panel off/restore and process-loss checks | [Phone screen](summary.md) |
| Physical Android16 fixed4% taskbar, DPI equality, rotations/Fit | [Fixed ratio](taskbar-ratio/summary.md) |
| Earlier compact/centered iterations and retained failures | [Compact](taskbar-compact/summary.md), [centered](taskbar/summary.md) |

Each local index links verified private archive descriptors and states exact
source/test APK scope. Retained until separately removed, no raw device upload.
The release workflow generates its own current host reports, checksums and
evidence ZIP as public release assets; it does not ingest these private bundles.
