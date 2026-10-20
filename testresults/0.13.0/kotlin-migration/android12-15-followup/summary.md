# Android 12–15 acceptance follow-up

The emulator campaigns ran on dedicated Google APIs x86_64 API31/33/34/35 images,
sequentially, with a1920×1200/160DPI simulated external display. Tests/product
helpers use their ordinary app/shell identities; root is used only for scoped
private evidence reads. Fullscreen onboarding is explicitly acknowledged for
the selected API33/34/35 runs; first-launch onboarding is not thereby verified.

| Android | Selected native run | Actual result | Test APK generation |
| --- | --- | --- | --- |
| 12/API31 | 20261018T184508.406281Z | Input fails trusted-display permission; ten later suites unrun | a05e1881…602ae |
| 13/API33 | 20261018T193052.991313Z | Seven pass; child fails to open private reader; three later suites unrun | 2063a435…f2be5 |
| 14/API34 | 20261018T190000.176618Z | Seven pass; child fails remaining-app rendering; three later suites unrun | a05e1881…602ae |
| 15/API35 | 20261018T192037.760108Z | Seven pass; child fails remaining-app rendering; three later suites unrun | 79141535…5a047 |

These selected prefixes have21passes/4failures among25executed of44planned suites:
84% of executed suites pass, with19unrun suites and eight unrun focused tails.
**Zero complete platform passes.** All selected product APKs share SHA256
1e76ec598fdb04d46b622385d878dce76912654bf6d5fa21b95afbe8ac2afada, but test
generations differ. This is not a uniform final-candidate matrix. Initial failed
attempts and subsequent scoped diagnostics remain separate and retained.

## Findings and verification

- Android12's shell cannot create the requested trusted display. The published
  Java source requests the same flag; no root/grant bypass or product fallback
  is added. Android12L/API32 and physical OEM variants are not tested.
- Android13's consumed report identifies lone NOT_FOCUSABLE as the inherited
  touch-prerequisite model's rejection. The caller-only API33 correction preserves
  task/UID/geometry/overlap/final-task guards and native assertions. Input and the
  six subsequent suites pass. The child fixture receives handled UP but never
  reports a click/reader launch; exact pressed/focus timing remains unavailable.
- Android14/15 report the remaining task drawn while captured pixels retain the
  departed parent. Reconstructed code38 Java debug/test artifacts from clean
  dbadbf9 source reproduce Android14's exact assertion in194412.640819Z. Its
  captured PNG is byte-identical to the Kotlin14 failure: SHA256
  ab9ac11100e88aaea6293cca7d8741653f16a3da81b89240152837d64f25e5ab.
  This establishes the observable exists before migration, without identifying
  the stale buffer/composition cause or proving Android15 has that same cause.
- Main-thread phone page/Back observations pass focused UI on API30/35/36.
  The original API35 failure remains; this does not prove its exact cause.
- Latest separate input regression passes API30. API36 fails physical-host input
  after virtual readiness passes: Android reports no touchable target ondisplay2
  before cleanup closes the host. Missing optional readiness evidence independently
  fails collection. Failure-time host state is absent; no automatic rerun follows.
- Java-control preparation failures are retained: unsupported host build argument,
  test-manifest version assumption and CRLF UID parsing. The first control attempt
  leaves native unrun; refined three actual UID parsers pass39private host cases before
  the subsequent first native attempt. The Java native failure is sealed before
  independent cold-boot Kotlin-pair recovery verifies hashes/version/UID and
  requests controller launch. No preference replay or rendered-input approval.

Current checkpoint qualityCheck passes (1s Gradle,96tasks:4executed/92up-to-date).
The148 unchanged JVM results are reused, not newly executed. Tooling's unchanged
source-bound generation retains208passes/one Windows skip. Requirements, coverage,
documentation, privacy and packaged fixture-runtime checks pass. Runtime/test
APKs still match their frozen generation after formatting and final checks.

Root inspects selected API35 phone Settings/search keyboard, API33 monitor
Settings and the Java failure PNG. Text/icons/keyboard are readable in those
states; the scrolled phone Settings clips its preceding row at the viewport.
Both new architecture diagrams render and receive root and expert inspection.
This is bounded author review, not user/golden/full-catalog/performance approval.

Architecture expert /root/android12_15_acceptance_preflight approves each private
procedure before execution and the final diagnostic interpretation/diagrams at
2026-10-18T19:46:24Z. Changed drafts get new exact reviews; prior draft approvals
are not reused. Scope is diagnostic only, with no failure waiver or release approval.

## Evidence and remaining scope

[Archive descriptor](archive.json):1762data files plus manifest,137956504ZIP bytes, SHA256
ab98c8575c95b982813391d09fe8aa53ed691bc7cfe55884375294b83bbb981a.
The archive/member hashes verify locally. Raw logs/captures/APKs/helper source,
configuration/image hashes, per-attempt bindings, native subarchives, calibration,
host results and actual review summaries remain private in
artifacts/evidence/kotlin-migration-android12-15-followup/evidence.zip.
Availability is local-only until the owner removes it; no upload/remote retention.
The prior [initial archive](../android12-15-initial/summary.md) stays retained.

Final Android11 physical-monitor regression, original hardware split failure,
native Chrome/cable/peripheral checks, full visual/performance acceptance and
fresh final release reviews remain pending. No new signing, push or publication.
See the [migration record](../../../../docs/changes/kotlin-migration.md) and
[notable findings](../../../../docs/changes/kotlin-migration-findings.md).
