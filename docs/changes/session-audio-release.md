# Release: Session audio 0.14.0

## Scope and authorization

The user requested real-device audio testing and publication before implementing
the separate debugging-off shortcut. Baseline is published v0.13.0 / 2539b7b;
audio implementation was merged as PR #7 / 8765bc9. This candidate increments
versionName to 0.14.0 and versionCode to 40 without changing the audio engine or
tests. FR-036 / DX-AUDIO-001 and FR-034 / DX-SETUP-001 remain the contract.

## Architecture and UI impact

The cumulative feature changes primary-helper audio ownership, phone-wide MEDIA
policy, asynchronous session cleanup and shared phone/monitor controls. The
[implementation preflight](session-audio.md) and
[source-linked diagrams](../architecture/features/session-audio.md) apply. A fresh
release architecture expert must inspect completed current evidence before final
review. Version packaging itself introduces no new runtime ownership boundary.
The existing approved Audio layout is retained; current renders must be inspected
against its scoped reference catalog. There is no new material UI design choice.

## Acceptance plan and limits

Run current qualityCheck, tooling and requirement/coverage/document/privacy gates.
Run the focused audio hardware suite on the connected Xiaomi 14 / Android 15 with
physical HDMI output; verify routing, restoration, shared UI, rotations/Fit and
controlled-worker cases covered by that suite. Preserve all raw evidence privately
and record exact APK hashes, outcomes and archive availability before review.
Reuse earlier evidence only for unchanged source with its original scope explicit.
Do not repeat the broad Android compatibility campaign.

Silent PCM route observation is not an acoustic test. Bluetooth, other OS/OEM
privileges, endpoint connection permutations, physical hotplug/helper death,
complete accessibility/large-font coverage and latency distributions remain gaps.
The debugging-off action and bank testing follow publication and are excluded.

## Outcomes

Current hardware run `20261020T183443.577604Z` passed 170 assertions in 18.364s,
including separate-UID silent PCM HDMI/speaker routing, owned-policy release,
phone/monitor menus, rotations/Fit and controlled-worker cases. Controller
restoration and evidence collection completed. The installed development build
is version 0.14.0 / 40, not yet a published production build.

Current qualityCheck passed in 34s; 211 valid JVM outcomes have no failures,
errors or skips, with Gradle up-to-date reuse disclosed. Tooling passed 210/211
cases with one Windows symlink privilege skip in 23.768s. Requirements, coverage,
fixture definitions, documentation, privacy and Ruff checks passed. A local debug
signer verification first failed because its verification subprocess lacked the
JDK environment; rerunning with JAVA_HOME set produced verified signed payloads.
No product source was changed for that environment correction.

[Acceptance/archive index](../../testresults/0.14.0/session-audio/README.md)
contains private current device and host bundles with exact APK and source
bindings. No personal device captures or signed debug inputs will be uploaded.
Actual UI reviewer `/root/audio_014_render_review` approved all nine current
captures within pictured scope at2026-10-20T18:36:15Z, after comparing historical
references and verifying72 unchanged source fingerprints. The scoped reference
catalog now points to the current private archive's verified members. Broader
visual/accessibility/performance gaps remain explicit in the acceptance index.
Release architecture and final candidate approvals are recorded separately when
they occur. Publication remains pending those gates.
