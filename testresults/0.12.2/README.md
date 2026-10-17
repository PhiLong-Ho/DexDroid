# DexDroid 0.12.2 candidate evidence

Source baseline: published v0.12.1 / `6fa1b519330f8362d32552a637d1f99e019f40de`.
See the [change record](../../docs/changes/production-diagnostics.md) for contract,
expert decisions and release gates. Publication/physical update are post-review.

## Current verification

- Clean quality/build: 140 JVM tests pass, no new lint issues. Existing lint
  baseline has 31 filtered issues and 3 stale entries. Fresh release-variant APK
  and AAB pass SDK-defined-class/manifest production exclusion. Debug APK is
  correctly rejected by that production guard.
- Tooling: 160 passed, 1 Windows platform skip (161 cases). Coverage, requirements,
  documentation and formatting checks pass separately. App/tool bytes across five
  source sidecars match 127 current files; documentation changes are checked at
  candidate freeze rather than claimed as earlier tested source.
- Android 16 emulator (`DexDroid_API_36`, 1920x1200 emulator second display): debug
  catalog `20261017T154100.566617Z` and native input
  `20261017T154155.683753Z` pass, including four-rotation existing scope and new
  exactly-one debug diagnostic/component assertions.
- Non-debuggable release variant signed with standard development certificate,
  not official key: phone input-test search has no rows, old explicit component
  launch fails Error type 3, normal Apps/Close/reopen and Clock desktop launch work.
  UI expert `/root/production_test_entry_ui` approves five named phone/monitor
  captures at 2026-10-17T15:46Z (minute precision); hashes are in private sidecar.
- Root inspected four updated architecture overview/sequence renders against
  source; source/render bindings are in the private diagram review sidecar.

## Failures retained and scope limits

Initial init-script BOM build failure, stale incremental InputTest class rejection,
first tooling stale-coverage failure and initial Shizuku-binder setup failure are
retained alongside successful unchanged or corrected reruns. The class guard
caught actual orphaned build output; a clean rebuild resolved it without weakening
validation. A debug shell hierarchy helper was killed on this emulator.

Monitor native filtered search was not established (attempt dismissed menu;
stock hierarchy bounds clipped). It remains unverified. No physical USB-C/OEM
production regression, full rotation/Fit/DPI/font/IME matrix, hardware latency,
accessibility sweep or golden-image promotion is claimed. Previous 0.12.1 device
results remain historical. This patch does not modify routing or geometry.

## Private storage

[Archive descriptor](archive.json): verified SHA-256
`08244d615e4ad00821cdd62e7e86c321992a27a6b76cdf5cb910f7103c5d09ba`,
127 files, including current source-bound host/device evidence, dev-key candidate
artifacts, screenshots, original failures and diagram renders. Available locally
to reviewers at the descriptor path until explicit owner deletion; no GitHub
upload or durable remote retention is implied. Public Actions/release evidence
contains only that workflow's own privacy-guarded host outcomes.
