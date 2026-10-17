# DexDroid 0.12.3 candidate evidence

Baseline: published v0.12.2 / `fae1e801cb6ad6be43aa8d0848efef124f0e5048`.
See the [change record](../../docs/changes/status-details-cue.md) for approved UI,
source boundaries, failures and release gates. Release/install are subsequent.

- Final qualityCheck: 140 JVM cases pass, no new lint; cached JVM results remain
  valid for unchanged host-tested logic. Final tooling: 160 passed, 1 Windows skip.
- Final Android 16 emulator phone UI `20261017T162222.867526Z` passes in 35s and
  monitor `20261017T162314.792169Z` passes in 107s at 1920x1200 emulator second display.
  Four rotations report 72dp collapsed status; cue interactions, expansion state,
  keyboard and independent desktop actions pass within existing suite scope.
- User requested bounded minor-change verification: only these two existing
  affected device suites ran, not all eleven. No new physical/OEM or full
  long-name/font/DPI/Fit/peripheral/accessibility-device/performance claim.
- UI expert `/root/status_disclosure_ui` approves eight selected actual final
  renders at 2026-10-17T16:25Z (minute precision). Exact file/hash sidecar is private.
  Root inspected layouts and both updated architecture renders. No golden promotion.
- Four final sidecars bind 126 current app/tool files. APK/test hashes and configuration
  are in the native run metadata. Earlier failed assertion/setup and host wrapper
  attempts remain retained, not counted as passes. Initial version-name iteration
  under ignored 0.12.2 runs is historical; only final-version runs count above.

[Archive descriptor](archive.json) locates the verified private local bundle.
Available to reviewers until explicit owner deletion; no remote upload/durable
retention implied. Raw captures/logs remain ignored/private. Public CI/release
archives contain only their own privacy-guarded host evidence.
