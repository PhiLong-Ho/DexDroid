# Session audio 0.14.0 acceptance

Baseline: main 8765bc9 (audio PR #7), following published v0.13.0 / 2539b7b.
The candidate changes version metadata and release documentation; audio code and
acceptance tests are unchanged from the reviewed implementation.

## Current outcomes

- Xiaomi 14 / Android 15 with physical 1920x1200 HDMI monitor:
  `20261020T183443.577604Z`, PASS170 assertions in18.364s, Android result -1,
  controller restoration and collection pass. Separate app UID and silent PCM
  verify HDMI/speaker routing; no acoustic playback claim.
- Shared phone/monitor controls and audio choices, four monitor rotations, Fit
  at r0, Refresh bounds/activation, System/Stop/new session and controlled worker
  cases are exercised by the focused audio suite. This is not a full regression.
- qualityCheck passed in34s, 211 valid current JVM outcomes with zero failures,
  errors or skips; some Gradle tasks reuse valid up-to-date outcomes. Tooling
  passed210/211 in23.768s with one Windows symlink privilege skip. All seven
  requirement/coverage/fixture/document/privacy/Ruff checks passed.
- Signed development product SHA256
  `da0925e438583b56d7fbce00e88166915c992793531dc0f30dd75a2bbf0d3920`;
  test `1602e826114160ef5b96c3ec6d05eea010d3f153dc6d8a5ea0b499ce3601c066`.
  The host bundle retains both, unsigned hashes, signature/payload equality proof
  and168 current build/test source fingerprints. They are not release assets.

## Evidence availability

- [Device descriptor](device/archive.json),24files, ZIP SHA256
  `13a32417b2e57017fca2bb0748f01e2939ebcff1ab97d1a90070790b8ef40679`.
- [Host descriptor](host/archive.json),36files, ZIP SHA256
  `afc3e3150d0d79963af8f7747e8d67f74cbdf94c9596188b736efbf75fb730fc`.

Both archives are verified private local bundles under artifacts/evidence,
available to reviewers until removed by the owner. No upload is claimed or
authorized for these device captures, diagnostics or signed debug inputs.
Earlier implementation failures and corrected native evidence remain available
through the [historical acceptance index](../../session-audio-dev/acceptance/README.md).
Its six rendered source-linked diagrams are reused because their code/docs are
unchanged; this release does not claim a new diagram-rendering campaign.

## Review and gaps

Actual reviewer `/root/audio_014_render_review` approved all nine current layouts
within pictured scope at2026-10-20T18:36:15Z, comparing all nine historical images
and verifying72 unchanged UI-source fingerprints. The
[current scoped catalog](../../../docs/ui-references/session-audio.json) records
literal current ZIP members and hashes. The runner's original raw visual_review
value remains unreviewed; this separate attestation records actual later review.
No complete golden comparison is claimed. Activation assertions use performClick;
they do not prove coordinate hit accuracy for every target.
Bluetooth/acoustic output, other OS/OEM privileges, physical hotplug/helper
death, broad endpoint connection permutations, full accessibility/large-font
coverage and latency distributions remain unverified. No debugging switches or
banking apps were changed/tested by this audio release run.
