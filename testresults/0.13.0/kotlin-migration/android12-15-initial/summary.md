# Android 12–15 initial acceptance

Executed 2026-10-18 UTC on `chore/kotlin-migration`, dirty migration candidate
based on published Java `dbadbf9dce7b23bfb111a153a03cfd71037325a9`.
These are failed initial campaigns, not full compatibility passes.

| Android / API | Native run | Complete suite outcomes | Failure |
| --- | --- | --- | --- |
| 12 / 31 | `20261018T184508.406281Z` | Input fails; ten suites unrun | Shell UID2000 lacks `ADD_TRUSTED_DISPLAY`; desktop creation denied |
| 13 / 33 | `20261018T184845.229532Z` | Input fails; ten suites unrun | Composite native input prerequisite after rotation 1 |
| 14 / 34 | `20261018T185022.845451Z` | Input and UI pass; monitor fails; eight suites unrun | Monitor touch does not return On after the second injected tap |
| 15 / 35 | `20261018T185334.209129Z` | Input passes; UI fails; nine suites unrun | Second repeated native Back does not satisfy Settings-page observation |

Three of seven executed suites pass; four fail. Thirty-seven of the planned
44 full-run suite invocations and all eight focused navigation/controller tails
remain unrun in these attempts. Passing assertion prefixes are not suite passes.
Native failures, private collection, visual inspection and performance are separate.
Original native archives and the consolidated [archive descriptor](archive.json)
verify; 307 retained members, archive SHA256
`3ed7501d87c322849fdf6647637e0ed28ca6295b2249ca53ed11656fce6d63cc`.
Four native ZIPs remain sealed subarchives inside the private bundle.

All images use the same frozen debug APK
`1e76ec598fdb04d46b622385d878dce76912654bf6d5fa21b95afbe8ac2afada`
and test APK
`a05e18814577a8155c044b1f12b41034777a2f2d4c1891d9e71c7f42800602ae`.
The 132-entry source snapshot differs from its original producer only in requirement
metadata; no rebuild is claimed. The bundle retains producer provenance, exact
AVD/image file hashes/revisions, fingerprints/boot IDs, pre/post-start configuration,
runner/helper hashes, clocks, calibration and original outcomes. Dedicated Google
APIs x86_64 emulators run sequentially: 1080x2400/420 phone, 1920x1200/160 secondary,
software GPU and ordinary vsync. Secondary infrastructure flags are 1739.
App, Shizuku and instrumentation remain app/shell UID; scoped `su 0 tar` only reads
private emulator evidence. No physical phone mutation occurs.

Fresh `/root/android12_15_acceptance_preflight` approves the approach at18:36:06Z
and corrected implementation at18:44:40Z. The first review's configuration/restore
blockers and corrections are retained. Fifteen prior extractor cases and nine new
restore-fault cases pass; later source-bound reuse is not another execution.
API31's supplemental verified startup record preserves the original configuration
provenance error rather than overwriting it.

Source review finds the API31 flag threshold identical in published Java; this is
an inherited platform capability failure. API33's failure-time input state is
missing. API34's log shows a fullscreen tutorial between toggle taps, but does not
prove the receiving window. API35's failure PNG shows Settings after its last
predicate; the inherited off-main UI observations lack owner identity. None of
these establishes a Kotlin-specific root cause.

Root inspects five API34 renders: phone touchpad, scrolled Settings, search keyboard,
monitor Controls and Settings. Text/icons are readable, keyboard visible and no
obvious overlap appears in these states. This is bounded inspection, not golden,
full rotation/catalog, user or performance approval. The two new verification
diagrams are also rendered and inspected. Follow-up attempts have separate records.

Raw logs, APKs, captures, dumps and SDK setup remain **private local only** under
ignored runs and `artifacts/evidence/kotlin-migration-android12-15-initial` until the
owner removes them; no upload, remote retention or release approval is implied.
Android12L/API32, real cable/OEM/peripheral behavior, native Chrome Stop and the
unresolved Android11 physical regression are outside these emulator outcomes.
