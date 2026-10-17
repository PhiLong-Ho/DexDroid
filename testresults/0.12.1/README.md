# DexDroid 0.12.1 acceptance

Current candidate: version 0.12.1/build 34 on sanitized main `7f36581`, with
uncommitted source-bound changes. Release, production installation and branch
cleanup remain gated on completed device checks and fresh architecture/final reviews.

Raw device logs, XML, screenshots and phone settings backups remain private in
ignored local storage. They are not release/Actions assets. Public CI archives
contain only that CI run's guarded host evidence.

## Current execution

- Android 16 Samsung SM-S928B with 1920x1200 physical monitor: focused ordinary
  phone-handoff child run `20261017T142942.752126Z`, split
  `20261017T143106.928706Z` and expanded phone UI
  `20261017T143921.496828Z` pass. Native Chrome Stop
  `20261017T143711.511677Z-chrome-stop` passes three 15-second cycles before
  the subsequent phone Back lifecycle correction; it is not represented as a new
  run of that later APK. Consolidated corrected-build run
  `20261017T144007.019810Z` passes all eleven suites in 567 seconds on app
  `bf5e572fa31c3e76636c2c231a41e7577881abf29777e88008a1f419c16ce308`
  and test APK `91e3336c8dce791a6f78636956d9fb989dedfa5f885a06b2379451678baba6ab`.
  Native Stop `20261017T144958.866907Z-chrome-stop` also passes three
  15-second cycles on that exact corrected APK.
- Host quality: 140 JVM tests pass; tooling reports 155 passed and one Windows
  platform skip. App/tool inputs are source-identical to both completed device runs; final documentation/coverage integrity is checked separately before freeze.
- Android 11 Black Shark with 1920x1200 monitor: run `20261017T145640.141526Z` passes all eleven suites in 671 seconds on the same APK pair. Native run `20261017T151018.648476Z-chrome-stop` passes three 15-second Stop cycles. Previous split setting is restored; private current settings backups for both phones are verified. Production installation remains post-publication.

## Retained failures and limits

Forced root-task migration leaves logical and rendered placement inconsistent on
Samsung, including after a framework transaction barrier. Ordinary phone app launch
uses a different Android path. Retain the old failure as a privileged platform
diagnostic rather than claiming it repaired. Split fixture failures exposed a root
screen-offset error, window/frame readiness and an unreleased synthetic host
contact. Original attempts remain private. Consolidated `20261017T143524.967243Z`
passed input then crashed on native Back during dialog replacement. The queued
owner-guard correction passes focused native/race/IME checks; exact Samsung decor
failure causality remains unproved. Consolidated corrected-build validation now
passes; the original failure remains retained.

No universal OEM/app, physical-finger/keyboard latency, full DPI/font matrix or
approved golden-image coverage is claimed. Current actual 16 phone and 28 monitor captures passed separate scoped visual
review by `/root/menu_back_crash_ui`, including four-rotation selected monitor
states. Static captures do not establish animation timing or every immediate
phone submenu's settled opacity; no golden baseline was promoted.

## Documentation render evidence

- [Architecture atlas](architecture-atlas/README.md): original source-based expert
  review and 36 diagrams, historical pre-migration packaging identity.
- [Release architecture policy](release-architecture-review/README.md): policy
  walkthrough and eight diagrams; this is not an actual release approval.
- [Privacy/native Stop boundaries](release-boundaries/archive.json): four current
  rendered diagrams, author and root inspected; private verified local ZIP.
- [Queued Back lifetime](back-lifetime/archive.json): current controller/desktop
  diagrams, author and root inspected; private verified local ZIP.

Changed diagrams supersede the matching older atlas renders. All descriptors record
actual local locations and checksums; availability is local-only until explicit
owner deletion. Reviewers have local access. No raw evidence is committed.

## Current Android 16 private archives

- [Consolidated run](android16/archive.json): verified ZIP SHA-256 `8dc15f3e6c4ce89fda1a73bdc4ff45c1b3d2cac5c502d315a14f6c20ef170947`, original raw logs/captures plus explicit post-run source/review sidecars.
- [Native Chrome Stop](android16/native-stop/archive.json): verified ZIP SHA-256 `9db9959b52b472cca4e85471363c218ebc3b0c3f4234b72e511731cc02eb3450`, three current-APK cycles.

Both ZIPs stay private/local-only at the descriptor locations. No GitHub upload is implied. The scoped visual reviewer approved 50 of 77 split captures in addition to all 44 UI/monitor captures; split Fit appearance remains unverified because that state was not captured.


## Current Android 11 and host private archives

- [Android 11 consolidated](android11/archive.json): verified ZIP SHA-256 `78a8138b5246e25379e91b57666f4debfad1f8393fed50092faa0368e1426023`.
- [Android 11 native Stop](android11/native-stop/archive.json): verified ZIP SHA-256 `d91a04e8a7f573a2991979b59438d55ea49013a6c1d7a1e909b23f71eb478ec5`.
- [Final source-bound host checks](host/archive.json): verified ZIP SHA-256 `07cfadb0c34f0323795aa757ada802476ff8308016281f9693db9002f6b7f9bf`.
- [Retained failed attempts](retained-attempts/archive.json): twelve failed setup/fixture/runtime attempts plus privileged handoff diagnostics; none is counted as a passing check.

The same expert approves all 44 current Android 11 phone/monitor captures and the
same 50-of-77 split selection, with split review at 2026-10-17T15:10:11Z.
Selection manifest SHA-256 `d1dff873d6085aa42e98e0bea2b0a376eb443531defa79d919b5899efd6dc41c`.
Android 11 monitor UI DPI is 320 versus Samsung 240; broader DPI/font and during-Fit
static appearance remain gaps. Original native setup failures include split enabled,
a wrong manual Close label leaving the picker open, and Settings visible before a
third cycle after two passing cycles. They remain retained; a fresh three-cycle run
passes without driver/source changes. No post-Stop helper/input/focus is used.

Production migration preserves verified preferences using the private release-signed
bridge described in the release change record. Only observable settings/readiness
and package/signature/version/non-debuggable checks are possible after final hardening.
This pre-release record does not claim later publication or production deployment.
