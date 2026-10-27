# 0.17.0 focused compatibility acceptance

Source baseline28a58fe1b71fc7e643c9426cbf0f547b85dacf9c with dirty candidate;
the private bundle retains the exact162-file tested app/build SHA256 inventory.
Final signed development product APK `c59c404a93315e9c1876a977c9236a2ade2ca187181e84b7f42dd37489d8c0bc`;
test APK `e2b90ef5cd647da60276625321d72ef257613d7da53af12fc94e4173221879b0`. Both are signature-only re-signs using the persistent app
certificate, verified against Gradle debug payloads; these are not production APKs.

## Current device outcomes

| Device/configuration | Suite/run (2026-10-27 UTC) | Result |
| --- | --- | --- |
| Xiaomi14 Android16, real1920×1200 USB-C monitor | capabilities171346.037211Z |550 assertions passed |
| Same Xiaomi | feedback171235.508528Z |147 assertions passed/11.370s |
| Android17/API37 CE2A.260420.019, SwiftShader simulated1920×1200 secondary display | capabilities171402.909355Z |550 assertions passed |
| Same emulator | feedback171255.722715Z |147 assertions passed/12.056s |

Commands: `python .local/capabilities-release/suite.py --serial <selected-device>
--suite capabilities --timeout120` and `--suite feedback --timeout180` (actual
argv retained by runner). Device suites ran sequentially. Capabilities checks
cover both pages, six items, read-only Refresh, export/navigation, four rotations
and Fit. Feedback covers pending/expanded/rejection, four rotations/split actual
native touch-through, Stop/deferred completion. Assertion counts are checks,
not independent scenario counts, pixel approval or performance distributions.
Emulator boot identity remained unchanged; Android17 support remains experimental.

## Retained attempts and limits

170045 preparation failed test-package installation pending Xiaomi's normal
Install-via-USB confirmation; narrowly confirmed the exact test package. Earlier
capability runs170259/170454 passed on the same product and older test APK.
Feedback170440 failed same-turn status expansion; corrected bounded frame wait.
170649 tapped a fixture title bar; a separate fullscreen native fixture now
checks the contact lies inside clickable content before actual injection.
Final capability suites repeated once with the final harness for one source
binding. No product changes were made to hide those test failures.
The intermediate tooling run failed only stale generated coverage after test
formatting; regenerated mappings and fresh final tooling outcome are recorded
in the release record. Raw failures remain private alongside passes.

No new full-suite Android11 or Android12–15 matrix. Historical Android11
development/audio evidence remains separate. Physical emulator USB-C, hardware
keyboard/mouse, acoustics/audio routing, helper death/physical disconnect,
large fonts/assistive services, shortcut first paint and normal-launch timing
distributions are unverified. No universal OEM/platform support claim.

## Evidence and review

Archive descriptor: [archive.json](archive.json); private local-only ZIP, no
GitHub upload. Logs, XML, screen captures and dumps are private. Retention is
local until owner cleanup; a stored descriptor is not public availability.
Rendered-review verdict and current source/image hash catalogs are recorded in
the [release record](../../../docs/changes/release-0.17.0.md). Host results and
release architecture/final reviews are separate gates. Official release host
reports/ZIPs are generated only by that release's own GitHub Actions run.

Verified private ZIP SHA256 `b25cce393b5cc0a22e56ef578a5f3e77ec84954db61cfefbd7ddb1605e676d16`; 284 files.
Rendered expert `/root/release017_render_review`,2026-10-27T17:15:43Z, approves
all46 current pictured captures (24 report/22 feedback), with no visual blockers.
Final host tooling:210 passed/211 total,1 Windows symlink-privilege skip;
267 JVM tests pass. Screenshots do not prove navigation, touch or performance.
