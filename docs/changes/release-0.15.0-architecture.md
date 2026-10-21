# Release architecture review: 0.15.0

This records an actual read-only review after checks/evidence, before the two
fresh final candidate reviews. It does not certify complete runtime compatibility.

- Version: 0.15.0 / code 42.
- Published baseline: v0.14.0, commit
  `2e9c5afeb3dbb9cc9b7d2e1ffd6f2d86ddee3638`.
- Exact inspected pre-attestation Git tree:
  `916d2870fae3a52d10a115e419d79584a039c954`.
- Pre-attestation canonical digest:
  `64df062da88b738b534a46362c7d80687b5a077e8e9ac72dbac8086ffa80fb0c`.
- Fresh expert: `/root/release015_architecture`.
- Actual review time: 2026-10-21T08:37:58Z.
- Verdict: **APPROVE; no blocking architecture findings**.

Reviewed all 60 staged files and cumulative debugging-off, diagnostics and
test-local audio changes. Three unrelated unstaged source-tooling policy edits
were preserved outside the candidate. No device/source mutation, signing or
publication was performed by the reviewer.

Shared panels remain presentation; SessionService owns state/operations. The
primary helper validates caller identity, released displays and exact helper
class before fixed zero writes to three permitted flags, clearing/restoring Binder
identity. No new product permission, arbitrary shell endpoint or auto-enable path.
Started/foreground ownership, durable pending/terminal receipts, startup exclusion,
stale guards and interrupted results remain coherent. Display/split cleanup and
actual serial audio-worker acknowledgement precede helper mutation. Existing
generation/display/input cancellation invariants remain intact.

DiagnosticLog formats safe fields before storage/logcat, bounds count/length,
synchronizes and caches snapshots. DiagnosticsPanel shares explicit immutable
Copy/Share/Clear and phone chooser routing without new probes or background
collection. Version change refreshes the helper/AIDL implementation. Whole-product,
component and affected feature diagrams agree with source; diagnostic/debugging
overview and sequence Mermaid inputs exactly match staged blocks and all four
rendered PNGs were inspected. Audio lifecycle corrections remain test-local.

Reviewer verified current private ZIPs and all 303 device / 33 host member hashes,
seven historical development bundles, 134 device / 136 host source fingerprints,
actual package hashes and payload equality with Gradle outputs. Host XML contains
222 passes with no failure/error/skip; tooling contains 210 passes and one Windows
privilege skip. Successful/failed native outputs and real chooser cancellation
metadata were inspected. See the [evidence index](../../testresults/0.15.0/diagnostics/README.md)
and [change record](diagnostic-log.md) for exact scope and private availability.

Residual nonblocking gaps: complete actual debugging flag/daemon shutdown and
HSBC/PayLah acceptance, broad OS/OEM/permission/process-death/disconnect cases,
Bluetooth/acoustic behavior, exhaustive accessibility/font/input configurations
and performance/latency. Prior master-only metadata left USB/WiFi enabled/adbd
running; the full observer remained READY, consistent with the recorded unknown.
Timeout invalidates queued continuations but cannot roll back already executing
remote/system work. The inherited generic session queue remains unbounded; this
change introduces no new queue. No lint baseline change or runtime completeness
claim is implied by this verdict.

Only this faithful attestation follows the inspected inputs. Final reviewers must
verify that delta and approve the resulting full canonical digest including this
record; approvals.json remains the sole digest exclusion. Any other input change
requires renewed architecture review. Protected PR/host and current release
report/evidence validation remain required before publication.
