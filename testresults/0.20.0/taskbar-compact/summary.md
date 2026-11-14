# Compact icon-only taskbar: development verification

User-authorized local correction:48dp reserved strip,24dp white Apps/Controls
icons in48dp targets, centered104dp group with8dp gap, black background and no
visible labels/divider. Accessible names/tooltips and existing actions remain.
UI expert `/root/compact_taskbar_ui` approved the correction; architectural impact
is local size/style through the already-authoritative viewport, with no new owner,
IPC, mapping or lifecycle contract. No release/final-candidate approval claimed.

Native run `20261113T172532.316262Z` passes on Xiaomi14 Android16 with real monitor:
four rotations, separate Fit, exact native content dimensions, bottom app input,
touch/mouse/phone-pointer actions, center preference/shared settings/restart,
both empty split directions, modal/blank strip exclusion and preview/revert.
This is focused acceptance, not the full suite/OS matrix. Host `qualityCheck`
passes with317 JVM tests and no failures/errors/skips. Requirement, generated
coverage, privacy and documentation checks pass separately.

The sealed bundle contains only this current run, screenshots and `host/` logs,
JUnit XML, totals, source/build fingerprints and QA derivative. Baseline
`53bf44a5bb9e04433d143feaa593137ed863f1e8`, dirty feature branch; source hashes and
installed APK hashes are retained in `host/source-build-inputs.json`. The installed
product APK is `8a584f2fd188aef62e30d5d14c2e108f8cf18c975af4a5a9c47ed07d11c8f663`.
Earlier broad device regressions and tooling211 outcomes in the prior
[taskbar summary](../taskbar/summary.md) remain historical; unchanged tooling
source justifies reuse, not a fresh tooling or broader device pass.

Author inspected native landscape and the four-rotation normalized comparison;
`docs/ui-references/monitor-taskbar.json` now points to this revision. No human
golden, comprehensive accessibility or performance approval. Prior held-contact,
failure-injected rollback, occupied split input, native IME/focus and phone-off
taskbar integration gaps remain; other OS/ROM including Android11 are unverified.

[Archive descriptor](archive.json): private local-only, retained until separately
removed, no upload/publication. Raw evidence remains ignored. Test package removed,
development build installed and controller reopened with taskbar enabled. No
commit/push/release was requested or performed.
