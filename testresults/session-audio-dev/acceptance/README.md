# Session audio acceptance

Feature branch `feature/session-audio-output`, baseline `2539b7b` (v0.13.0).
This is development verification, not a second release or a production install.

- Final Xiaomi14 Android15 run `20261020T170516.404526Z`: PASS 170 assertions in
  18.565 seconds, final Android code -1; collection and controller cleanup pass.
- Separate fixture app UID10362 versus product UID10361, silent PCM through actual
  default-routed AudioTrack: HDMI -> speaker -> HDMI, System and Stop restore;
  no acoustic listening/volume test. Four monitor rotations, Fit at r0, shared
  picker, one-level returns, equal four-action rows, pad bounds and new-session
  System behavior exercised. Controlled Binder doubles test in-flight Stop,
  1000 coalesced refreshes, stale callbacks and retry of the actual failed old epoch.
- Product signed APK `ed5e2f0ceab412032029a4833719b2a0759403460ffbfa721ababeab1f4c5b39`;
  final signed test `3be0dc0ee2a63ad7a5c70bec513084a1417e8fd3679bcfd99f1533230b386474`. Host bundle preserves both,
  corresponding unsigned hashes, signature/payload proof and160 source bindings.
- qualityCheck passes;211 valid current JVM outcomes (11 new lease tests), zero
  errors/failures/skips. Some final invocations reuse up-to-date outcomes, not
  211 freshly executed tests per invocation. Tooling211 total:210 passed,1 Windows
  symlink privilege skip. Requirement/coverage, fixture definitions, Ruff and
  privacy checks pass; documentation/format rechecked after this record.
- Actual architecture implementation approval `/root/audio_arch_preflight`,
  2026-10-20T16:57:10Z; two restoration blockers fixed and regression-tested.
- UI expert `/root/audio_ui_preflight` approved all nine corrected current captures
  from170516 at17:05:57Z, superseding prior current references. Refresh is visible
  beneath choices; native checks verify enabled accessible bounds/activation in
  phone, every monitor rotation and Fit. [Scoped references](../../../docs/ui-references/session-audio.json)
  use verified literal `data/` ZIP member paths. Earlier missing-button/ghosted
  frames remain historical and are not current approved references.
- All six affected Mermaid diagrams rendered and inspected by root (spec flow,
  feature overview/sequence, system/process overview and component dependencies).

## Retained failures and corrections

Raw attempts164538/164549/164616/164729/164811/164928/165250 are retained.
164538 could not find signed inputs (signing environment corrected).164549 wrongly
asserted RadioButton.performClick's return; its selection executed before teardown
settled.164616/164811 encountered the resulting retained speaker policy.
164729 exposed full-attribute equality versus Android's normalized readback; role,
type,address comparison repaired policy ownership and a host regression covers it.
The known original empty preferred policy was restored twice with guarded private
recovery code, only when the exact retained speaker override matched; receipts kept.
164928 passed phone switching but its unconfigured edge/rotation fixture could
not open controls; explicit touch/navigation setup and shared coordinate mapping
fixed the fixture.165250's controlled Proxy exposed data-class equality crossing
the Binder boundary; reference-identity session targets fixed it. Later complete
passes remain separate. Historical intermediate APK bytes are not all retained;
raw outcomes/hashes are, and only the final pair is certified by this index.
Architecture review additionally caught thrown-setter baseline loss and wrong old-
epoch retirement; current host/controlled-worker regressions cover both fixes.

## Initial final-review rejection and correction

Actual fresh reviewers `/root/audio_final_a` (17:04:31Z) and `/root/audio_final_b`
(17:04:35Z) rejected candidate77bf5667...: invisible zero-width Refresh and missing
`data/` reference prefixes. Reviewer B also caught FR-036 under the NFR heading.
The shared horizontal action row, real visibility/bounds/click assertions, verified
member paths and functional-section placement repair those findings. Native170516
and new render approval cover the changed APK/test. Old approvals are not reused;
two new final reviewers must inspect the corrected frozen candidate before push.

## Availability and limits

[Device archive](device/archive.json): private local ZIP SHA256
`1043879956fe977e65d3c2fa4f3ad99c2d061c816814e77d278835148f882716`,235files.
[Host/probe/recovery archive](host/archive.json): private local ZIP SHA256
`34aafcf0a9d8adc808307a2c5319f97b09516488c520b8eb079b9c7cf9998092`,49files.
Both verified locally, available to reviewers until owner removes them. No upload.
The [previous host/probe/recovery bundle](previous-host/archive.json),101files,
SHA`a9af248ee85c6178d13fc608bfcdd3503b0adf92b0fcefd7122d7bcbd25ba7bb`,
retains read-only capability checks, guarded recovery and the earlier compiled pair.
Tooling results are reused from1659: tool source/build inputs are unchanged; current
requirements/coverage, docs, fixture and privacy gates are rechecked separately.
Never publish real device captures/dumps, endpoint identities or signed debug inputs.

Android11/12 caller privilege, other OEMs/OS versions, Bluetooth/connection order,
physical hotplug/Shizuku/helper death, app-explicit routes/casts, acoustic playback,
keyboard/accessibility/large fonts/long or duplicate endpoints, every Fit/rotation
combination and measured latency distributions remain unverified. Android policy
has no atomic owner/death-token guarantee; helper loss can strand an override.
User stopped broad OS campaigns; no broad or historical result is counted current.
