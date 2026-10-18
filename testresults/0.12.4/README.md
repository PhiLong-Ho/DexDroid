# 0.12.4 Android 11 navigation acceptance

[Contract, cause, implementation, review checkpoints and gaps](../../docs/changes/legacy-navigation-focus.md).
Baseline v0.12.3 / `7ce8a42a9a2c2143284630d6d20c477ead5e5b34`. Current product/test
fingerprints: private archive `source-manifest-v3.json`; earlier manifests are historical.

| Verification | Outcome and scope |
| --- | --- |
| `host-key-admission` qualityCheck | Pass: format/build/lint, 140 JVM cases, no failures/skips |
| Tooling rerun | 160 pass, one platform-specific skip of 161; requirements/coverage/docs/privacy/Ruff pass |
| Physical Android11 full split `20261018T032506.459947Z` | Pass: native navigation/root Back, ordinary keys, EditText/modifier cancellation, input ownership, four rotations, functional Fit/DPI preview, handoff and lifecycle |
| Physical Android11 split-navigation `20261018T033011.556307Z` | Pass: both panes/four rotations, Home/controller/Up then ordinary key/root Back, empty Apps/relaunch |
| Synthetic monitor renders | Bounded four-rotation acceptance by `/root/split_settings_back_ui`, 2026-10-18T03:31:58Z; no golden promotion |
| Real Clock/Settings | Three child/native Up cycles, intentional Home/controller return and product root Back pass; Clock/Main survive |
| Injected input dispatch | 100 samples after 10 warmups: p50 57 ms, p95 77 ms; excludes rendering and physical finger |
| Earlier emulator outcomes/failures | Retained historical runs; not current corrected-binary verification |
| Production delivery | Pending final architecture/two-agent reviews, signing, identity checks and in-place installation |

Android11 Black Shark with physical 1920?1200 monitor and 320 UI DPI. Corrected
private debug product SHA-256 `c8e16e1b2a109c9c3e8da33216268c1aec4d50a939440fb1767b9cf8bd265812`;
package/certificate/payload guards and UID/preferences preservation passed. Never
publish that debug artifact. Current Android16/17, physical Bluetooth shortcuts,
fault injection, frame pacing, touch-to-photon and unreviewed phone/Fit/font/DPI/
accessibility appearance remain gaps. Full split assertions are not blanket
compatibility or visual/performance certification.

[Archive descriptor](archive.json): 666 private files at
`artifacts/evidence/legacy-navigation-0.12.4-accepted/evidence.zip`, SHA-256
`079c691e15aae63a0112b3654c01140db53d33620e85bef5ec492d6b26929775`.
Local-only retention until explicitly archived privately or removed by owner.
Reviewers in this workspace can inspect verified archive/original ignored runs.
Real app/device captures and dumps remain private; never upload this bundle.
Historical predevice V1/V2/key-admission archives stay local and are documented
in the change record. No public release was requested. This is the pre-production
delivery checkpoint; subsequent install smoke stays ignored/external.
