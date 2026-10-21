# 0.15.0 Xiaomi acceptance

Candidate: 0.15.0/build 42, cumulative debugging-off and diagnostic changes after
v0.14.0. Xiaomi 14 / Android 16 / HyperOS 3.0.304.0, real 1920×1200 monitor,
non-root Shizuku shell. No broad OS/OEM regression matrix was run.

| Selected suite | UTC run | Assertions | Instrumentation seconds | Result |
| --- | --- | --- | --- | --- |
| Diagnostics | 20261021T081653.481477Z | 284 | 22.045 | Pass |
| Controlled debugging-off | 20261021T081727.727007Z | 222 | 25.879 | Pass |
| Audio | 20261021T082347.529117Z | 186 | 19.093 | Pass |
| Phone UI | 20261021T082421.283872Z | 475 | 32.213 | Pass |
| Monitor menus | 20261021T082456.603005Z | 2434 | 102.745 | Pass |
| Keyboard | 20261021T082643.833462Z | 2405 | 60.108 | Pass |
| Split navigation | 20261021T082745.290843Z | 1149 | 86.088 | Pass |

Assertions describe selected suites, not requirement coverage or product latency.
Durations exclude review/user waits. Real phone and monitor Share actions opened
Android ChooserActivity on display 0; both were canceled without a recipient.
Outside dismissal restored the idle desktop. The private manual folder is a
descriptive label; smoke.json records actual completion, not action duration.

All passes use product APK
`eb1f5849797ca4240012c34e32fcd526b20db8ca12de94795f7988c6c3021b42`.
Diagnostics/debugging-off used instrumentation
`b2a1fc7272d8a0365c0cf4f2fa1d6006db2be001def4ea42f25fce0c1dfbac38`.
Audio/UI/monitor/keyboard/split-navigation used
`2cc1b8038f5e11134c0763240200cd066df1dbdbdf49ab9c2e8691633a106453`.
Intervening changes were test-only AudioFixture lifecycle/live observations and
the safe Stop cleanup assertion. Diagnostic/Debugging acceptance sources and
product payload did not change. Private inputs verify certificate and payload
equality with Gradle debug outputs. This is development acceptance, not a
production APK execution claim.

Host qualityCheck passed with 222 JVM outcomes; tooling 210 passed/one Windows
symlink privilege skip out of 211 reported cases. Some Gradle outputs were cached.
Requirement/coverage/documentation/fixture/privacy/Ruff gates passed. Coverage is
an inventory. CI release reports use their own current host run.

## Visual scope and archive availability

Fresh reviewer `/root/diagnostics_render_review` inspected six diagnostic images
at 2026-10-21T08:18:33Z and twelve debugging-off images at 08:19:03Z, explicitly
approving pictured Xiaomi phone/four monitor rotations and diagnostic Fit layouts.
Source/APK/member-bound catalogs: [diagnostics](../../../docs/ui-references/diagnostics.json)
and [debugging-off](../../../docs/ui-references/debugging-off.json). This is not
a pixel-golden, human signoff, unpictured state or performance approval.

[Device descriptor](device/archive.json) binds 303 original files including
successful/failed attempts, manual observations and source/package fingerprints.
[Host descriptor](host/archive.json) binds 33 files. Both ZIP/member/provenance
hash sets were verified. ZIPs remain private local at descriptor paths until the
owner removes them; no device evidence was uploaded. Git retains concise records
and hashes. Older sealed development bundles remain historical.

## Failed attempts retained

- 075030: APK verification lacked JAVA_HOME; no native execution.
- 075042: secure lock blocked readiness; cleanup masking corrected before pass.
- 081757: audio fixture playback stopped before a later route assertion.
- 081932: UI expected raw injected exception text; now requires safe cleanup
  uncertainty while preserving failure/recovery assertions.
- 082212: await immediately re-evaluated the frame-progress predicate; the
  successful two-sample observation is now retained.

These remain failed attempts. See the [change record](../../../docs/changes/diagnostic-log.md).

## Gaps

No complete actual debugging shutdown/readback or HSBC/PayLah acceptance was
established. Controlled tests never changed real debugging flags. No Bluetooth,
acoustic playback, physical keyboard/mouse diversity, exhaustive accessibility,
large-font/DPI, all disconnect/process-death/permission cases, broad OS/OEM matrix
or input-to-photon claim. Controller and monitor were left usable.
