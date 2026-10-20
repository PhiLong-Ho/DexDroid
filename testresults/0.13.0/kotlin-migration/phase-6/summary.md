# Kotlin migration phase 6 checkpoint

0.13.0 / code 39, based on published Java 0.12.5. All 82 maintained Android
sources and three Gradle scripts now use Kotlin/Kotlin DSL. Generated AIDL Java,
XML, Python, shell and assets retain their formats. Physical and release gates
remain pending; this checkpoint does not authorize signing or publication.

## Host and binary evidence

- Clean Kotlin DSL quality/build: 148 JVM cases pass, no new active lint findings.
- Current tooling: 209 cases, 208 passes and one Windows symlink-privilege skip.
- Strict six-scalar metadata reader: 21 cases; four isolated signing-fallback
  configurations use fake values only. Real key use has not occurred.
- Actual compiled equivalence: all 139 product outputs unchanged; only DeviceChecks
  and its connection container differ after the reviewed readiness correction.
  Connection executable javap output is identical. Product APK remains exactly
  `359426...b4dff9`; current test APK is `b7b917...0394`.
- Unsigned APK/AAB metadata and production fixture exclusion pass. Independent
  actual signature tools confirm unsigned output; no credentials are used.
- Formatting, requirements, coverage, documentation/privacy and Ruff checks pass.

## Selected emulator outcomes

| API | Newly executed | Explicit source-bound retained evidence |
| --- | --- | --- |
| 30 / AOSP software | Corrected ordinary input | Ten unaffected suites; legacy/split navigation, disconnected controller, shell |
| 36 / host GPU | Eleven separate ordinary suites; focused split navigation, disconnected controller, shell | None |
| 37 / experimental software | Corrected ordinary input | Six phase5 UI/monitor/keyboard/profiles/child/idle suites |

There are 34 selected instrumentation outcomes: 15 new and 19 retained, plus two
shell observations (one new, one retained). This is not 34 fresh runs or complete
Android17 compatibility. The private selection manifest retains exact original
APKs, timestamps, fingerprints, run hashes and scope. Reuse received independent
source/compiled/raw-outcome review at 2026-10-18T15:58:44Z.

All original failures remain: the first API36 input fails because Android's
transition snapshot blocks its lower-preview contact, leaving ten tails unrun in
that invocation. A test-only settled-window prerequisite plus conservative
preview phase/deadline guards preserves all original contacts, delivery bounds
and rollback assertions. Fresh input passes on API30/36/37. Compiler-format and
stale-coverage host failures are retained with their diagnosed corrections.

Twelve actual final renders receive bounded root inspection: phone Settings,
monitor App settings at four rotations, empty-fullscreen background on each
API30/36. Four architecture diagrams are rendered and inspected. Neither is a
full UI catalog, golden, font/DPI/Fit matrix or animation approval.

Both full native dispatch sample arrays/history are retained before focused work:
API30 p50/p95 56/75 ms; API36 11/25 ms, 100 samples after ten warmups each.
These exclude rendering and physical fingers; differing renderers prohibit an
improvement claim. Physical peripheral and fault-injection gaps remain explicit.

## Remaining gates and storage

The [current tracked operation contract](../../../../docs/changes/kotlin-migration-operations.json) binds exact scripts,
original artifacts and current source. It preserves UID/signature/data, captures
current preferences after debug replacement before deliberate launch, checks
execution/clock/path/state admission, and restores production in place. Its final
signing reviews and full Android11 campaign have not run. The user will install
the Samsung stable build independently and is available for the later real cable
check. No production-key or phone action has occurred in this phase.

The [admission correction](../phase-6-signing-guard/summary.md) supersedes the
ignored manifest and restore procedure archived at this checkpoint. The original
archive remains immutable; current contract/script bytes are in the correction
bundle.

The [archive descriptor](archive.json) identifies the private local bundle and
SHA-256. Reviewers can access it in this workspace; retention lasts until the
owner removes it. It is not publicly uploaded. Prior source-bound API37 evidence
also remains in the independently verified [phase5 archive](../phase-5/archive.json).
See [the migration record](../../../../docs/changes/kotlin-migration.md) for exact
commands, approvals, original failures, source bindings and outstanding gates.
