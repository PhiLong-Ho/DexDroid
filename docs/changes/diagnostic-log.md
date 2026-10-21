# Diagnostic log improvement and 0.15.0 release

User authorized BL-01, Xiaomi 14 testing and release on 2026-10-21. This candidate
also includes the pending debugging-off flow relative to published v0.14.0; its
[change record](debugging-off.md) retains separate actual settings/bank gaps.

UI preflight `/root/diagnostics_ui_preflight` approved the small shared Settings
panel: context, Copy/Share/Clear, then history; unchanged menu navigation, actions
ahead of long history, explicit phone chooser, local errors and stable selection.
Parent presented this proposal before implementation. User's BL-01 implementation
authorization covers these controls; no material unresolved design choice.

Architecture preflight `/root/diagnostics_arch_preflight` approved retaining the
service-owned synchronized in-memory ring and current workers/IPC. Require fixed
safe event formatting before storage/logcat, bounded characters/count, separate
current context, immutable exports and no privileged probes or new background
work. Shared presentation owns export feedback; service retains state ownership.
This preflight does not replace the later release architecture or final reviews.

Plan: update FR-019/DX-DIAG-001; implement shared panel and safe report; add host
privacy/eviction tests and focused native phone/monitor/export/navigation tests;
run required host gates and relevant diagnostics/debugging/audio/UI acceptance on
Xiaomi 14; inspect renders and archive privately; freeze cumulative release inputs,
obtain release architecture approval and two fresh final approvals, then PR/release
and install production. No broad Android matrix or unrelated full-suite rerun.

## Verification notes

Host qualityCheck passes with 222 JVM outcomes (six new diagnostic tests), no lint
baseline change. Tooling reports 210 passes and one Windows symlink privilege skip
in 19.074 seconds. Requirement/coverage, documentation, privacy and Ruff checks
pass. These are host outcomes, not device or physical latency certification.

Current Xiaomi 14 is Android 16 / HyperOS 3.0.304.0 after its earlier approved
restart; do not relabel it Android 15. Diagnostics native run
`20261021T081653.481477Z` passes 284 assertions/22.045 seconds. Controlled
debugging-off run `20261021T081727.727007Z` passes 222/25.879 seconds without
changing real debugging flags. `/root/diagnostics_render_review` independently
approved six diagnostic renders at 2026-10-21T08:18:33Z and twelve debugging-off
renders at 08:19:03Z, within pictured phone/rotation/Fit scope. Empty, chooser,
pending/error, large-font and full accessibility/performance renders are not
implied; real bank results remain unverified.

Retained failed attempts are part of the private evidence, not passes:

- First preparation attempt lacked host JAVA_HOME for APK verification; no native
  execution. A subsequent diagnostics attempt was blocked by the secure lock;
  its cleanup originally masked readiness failure. The corrected harness retains
  the primary error, suppresses cleanup errors and always unbinds.
- Initial audio acceptance lost its silent fixture playback before the later
  route assertion. Read-only triage `/root/audio_default_triage` found that the
  fixture started only in onCreate and released in onStop. The test-local fix
  starts on each onStart, retains lifecycle/play-state observations and checks
  advancing frames rather than stale nonzero counts. A polling correction retains
  that two-sample observation across the await helper's final predicate check.
  Independent preferred-policy dumps compare System/Stop to the baseline. No
  production audio policy or API change was required. Test-local lifecycle impact
  was reviewed; product diagrams and ownership are unchanged.
- The broad UI suite still expected raw injected cleanup exception text. Its
  assertion now requires the safe cleanup-unconfirmed category, preserving the
  failure/recovery assertions without retaining the uncontrolled suffix.

Audio run `20261021T082347.529117Z` passes 186 assertions/19.093 seconds, including
live separate-UID HDMI/speaker output and independently observed System/Stop
preferred-policy restoration. UI run `20261021T082421.283872Z` passes
475/32.213 seconds; monitor run `20261021T082456.603005Z` passes
2434/102.745 seconds. Final evidence index records remaining suite/smoke outcomes,
actual package hashes and retained failures. No acoustic/Bluetooth or broad OS/OEM
coverage claim is made.

Keyboard and split-navigation also passed on the same product APK; the
[current evidence index](../../testresults/0.15.0/diagnostics/README.md) records
all seven selected suites, durations, package/source scopes, manual phone/monitor
chooser cancellation, private verified archives and retained failures. No recipient
was selected. Device captures were not uploaded. Current reference catalogs bind
actual reviewed member hashes; earlier sealed bytes remain historical.

Separately edited default source-tooling policy files in the shared workspace
are outside this release candidate: architecture-review SKILL.md,
docs/CODING_PRACTICE.md and docs/changes/default-tooling.md remain unstaged.
They are preserved for their own change. Release reviews inspect the staged tree.
