# Production diagnostic exclusion: 0.12.2

## Contract and authorization

User requests removal of input-test/test apps from official builds and both phones.
Official phone/monitor Apps omit synthetic diagnostic rows; the Activity/code must
be absent, including explicit component launch. Debug fixtures remain available.
Extends FR-018/DX-LAUNCH-001 and NFR-005/DX-RELEASE-001; SPEC was updated first.

## Preflight and impact

UI expert `/root/production_test_entry_ui` approves exact removal from both shared
menus and production launchability; no material placement/navigation decision.
Architecture expert `/root/production_test_entry_arch` approves moving the unchanged
class/manifest into debug, removing main class linkage and preserving legacy task
identity/cleanup sentinels. No IPC, queue, geometry, persistence or lifecycle owner
change. Build/source-set and artifact validation boundaries change; the standard
release architecture review remains required after completed evidence.

AppCatalog gates its shared synthetic row by DEBUG and debug build type and uses
a string component. Release-variant development-key acceptance therefore also
omits the row even if its signing identity differs from official distribution.
No new library is introduced: SDK35 dexdump enumerates actual class definitions;
manifest scans conservatively reject development identifiers in UTF8/UTF16 pools.
Harmless parser literals remain legal. AAB secondary modules and multidex must
be checked. SDK format reference: https://source.android.com/docs/core/runtime/dex-format.

InputTestActivity's existing ClickableViewAccessibility lint-baseline location is
relocated main to debug with identical issue/source, not regenerated or expanded.
Its body remains unchanged. Instrumentation remains targeted at the debug app.
Device inventory confirms no standalone project development package remains;
OS keyboard/vendor factory test packages are unrelated and untouched.

## Verification plan

Run required quality/tooling/requirement/coverage/document gates and debug catalog
acceptance including exactly-one diagnostic and component availability. Build a
release-variant candidate signed only with the development key for emulator UI
acceptance: no official key/signing before actual release reviews. Inspect APK/AAB
manifests and SDK-defined classes, verify phone/monitor Apps show no input-test
search result while installed apps/search/navigation remain usable. Render and
inspect changed ownership diagrams. Full unrelated input/geometry/lifecycle matrices
are not rerun for this packaging-only change; prior results stay historical.

After fresh release architecture and two final code reviews, merge passing PR,
release 0.12.2 and install the verified official APK over each existing production
install. Same release certificate allows settings-preserving install-r without
uninstall/data migration. Verify old component absence/launch failure, normal Apps
and installed version/settings. Archive raw evidence privately; public CI retains
only current host outcomes. No publication/install result is claimed until executed.

## Outcomes

Current qualityCheck succeeds after a clean app rebuild: 140 JVM tests, no new lint
findings; existing 31 filtered issues/3 stale entries remain. Tooling final run has
160 passed and 1 Windows platform skip (161 cases). The initial tooling attempt
failed stale coverage after formatter changed the test fingerprint; regenerated
coverage, preserved the failure and reran successfully.

Initial release output was rejected by the real class guard: old Oct 15 InputTest
class files survived in the migrated workspace output. A clean rebuild removes
them; both fresh APK/AAB pass actual SDK manifest/defined-class exclusion, and the
debug APK is correctly rejected. An initial private init script BOM build failure
was corrected with UTF8 without BOM; no production key was used.

Android 16 emulator debug catalog `20261017T154100.566617Z` passes, including exactly
one diagnostic/registered component and shared phone/monitor loading, search,
cache/retry/Close across four rotations. Debug native input suite
`20261017T154155.683753Z` passes. The first post-install catalog attempt
`20261017T154024.411006Z` failed Shizuku binder setup before assertions; the ready
app rerun passes unchanged. Original failures stay private. A debug shell UI dump
helper was killed on this emulator; it was not used as passing acceptance.

Release-variant/non-debuggable candidate uses the standard development certificate,
not the official key. Phone native input-test search returns zero rows; normal
phone/monitor Apps renders contain installed apps and no diagnostic. Close/reopen
and native phone Clock launch reach the owned desktop with actual visible pixels.
Explicit old diagnostic launch fails Error type 3. Monitor native filtered search
was not established: the attempt dismissed its menu, and stock hierarchy clipped
secondary bounds to primary dimensions. That scope remains unverified, not a pass.
This packaging change leaves monitor routing/geometry code untouched; current debug
catalog/input passes and shared catalog/package checks supply separate evidence.

Actual UI reviewer `/root/production_test_entry_ui` approves five named candidate
captures at 2026-10-17T15:46Z (minute precision): phone-main, phone-apps,
phone-search-empty, monitor-apps, monitor-clock. Actual hashes/scope are in the
private sidecar. No golden, hardware latency, full rotation/Fit/DPI/font/IME,
physical USB-C or broad OEM production matrix is claimed. Root inspects all four
updated architecture renders. Unrelated full 11-suite physical regressions from
0.12.1 remain historical, not rerun or relabeled.

This change is Android source-set/artifact specific; it adds no reusable template
workflow rule or service. Publication and device update remain pending required
fresh release architecture and final reviews. The next official APK uses the
existing certificate for settings-preserving install-r on both phones.

## Evidence availability

See the [current candidate evidence index](../../testresults/0.12.2/README.md).
Raw captures, APKs and logs remain private/local-only in the verified archive;
public CI evidence contains only its own guarded host results.

## Release architecture attestation

- Target 0.12.2 / versionCode 35; preceding published v0.12.1 baseline
  `6fa1b519330f8362d32552a637d1f99e019f40de`.
- Frozen pre-attestation Git tree `845a2d76ec518b4982c5be72295b65eb863d1879`;
  canonical digest `b8d238d9f59e2a5d835a420d558f5a45d050fe9dbae604026f324e235fa4ec5c`.
- Fresh read-only expert `/root/release122_architecture` explicitly **APPROVE**
  at `2026-10-17T15:51:37.4047016Z` after cumulative 24-file diff, current whole-product
  atlas, owning launcher/release diagrams, four renders, actual source/tests,
  requirement mappings, release path and private archive inspection.
- Findings: unchanged diagnostic moved into debug establishes production boundary;
  shared catalog gating preserves both-surface/debug behavior. Service/cache worker,
  stale-result/teardown, display/input/rollback and settings owners remain intact.
  Legacy strings remain cleanup/parser sentinels without executable linkage.
- SDK-defined-class/manifest guard covers multidex and AAB modules, fails closed
  before verified packaging; signing/privacy/report/archive bindings remain.
  Expert independently verified archive SHA-256, CRC/member hashes, 127 current
  source inputs, raw successful catalog/input and final tooling outcomes.
- No architecture blockers or material diagram drift. Residual monitor filtered-query,
  physical/OEM, broad Fit/rotation/font/IME/accessibility/performance gaps remain
  explicit. Official signing/publication and both production updates are later gates;
  development-certificate acceptance does not establish official install success.
- This attestation is the only delta after the inspected snapshot. Two different
  fresh final code reviewers must approve the complete final digest including it.
