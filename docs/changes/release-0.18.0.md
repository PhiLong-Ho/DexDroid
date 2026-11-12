# Release 0.18.0: Native monitor keyboard

## Scope and authorization

User authorized publication with “Great. release this” after Android 16 and
Android 11 acceptance. Candidate0.18.0/code51 follows publishedv0.17.0 at
`5fdd642001e04a663f58cc6e68d288080299b36b` on `feature/native-monitor-keyboard`.
The directive to release the tested build is interpreted as authorization to
preserve its existing behavior: missing preferences default Off, stored values
remain, and On on an incompatible running desktop applies after manual Stop/Start.
No functional default/application change is introduced. The redundant review
R-1794427000-0-82995948027904 was withdrawn; no explicit radio selection, timeout
consent or expert approval on the user's behalf is claimed.

FR-041/DX-IME-001 adds the actual selected Android IME to eligible managed displays,
shared requested value/current outcome, per-display baseline restoration, bounded
workers and exact ownership. API34+ creation-time focus coordination and a guarded
owned-Activity modal handoff preserve split typing and ready-menu key ownership.
Android 11 retains its original display flags/routing and local Apps keys. No root,
new persistent permission, default keyboard, developer-setting or network feature.
Gradle wrapper8.14.7/checksum is patch maintenance; application dependencies are
otherwise unchanged. The [feature change record](native-monitor-keyboard.md) and
[architecture overview/sequences](../architecture/features/native-monitor-keyboard.md)
record implementation preflights and retained failed attempts.

Unrelated pending edits in the architecture skill, CODING_PRACTICE and
default-tooling change record are outside this release and remain untouched.
Global graph tooling was separately removed by the owner; source reads and scoped
searches are used here. This is not a new workflow/tooling-removal publication.

## Device evidence and reuse

- [Android16 development acceptance](../../testresults/0.18.0-dev/native-monitor-keyboard/summary.md):
  native-ime102.875s, local keyboard56.969s and input33.954s. Actual selected Gboard
  key touches, consecutive edits/Delete, four rotations, Fit, repeated split focus,
  native Back, pre-contact modal Window/key ownership, no clickthrough, private child
  navigation, phone handoff, remaining-app input and real exit.
- [Android11 development acceptance](../../testresults/0.18.0-dev/android11-keyboard/summary.md):
  native fallback6.172s, local keyboard87.797s, input33.469s and legacy navigation14.469s.
  Shared unavailable/Off controls, real editor contact and desktop/focus preservation.
  No native monitor IME or successful general phone-IME rendering is claimed there.
- Expert rendered review covers Android16 On/configured Settings on portrait phone
  and landscape split monitor. Android11 On/unavailable captures were author-inspected,
  not approved as human goldens. The [reference catalog](../ui-references/native-monitor-keyboard.json)
  retains exact capture/archive bindings. All actual-device material stays private.

The candidate version/code bump is release metadata; functional device evidence
is reused with its original binaries/source fingerprints and gaps. Final host
checks bind the release inputs separately. This does not relabel development APKs
as production tests or claim a fresh device run for51. Default/application behavior
is unchanged from the tested build.

## Limits

Android12–15/17+ native keyboard, other ROMs/IMEs/Presentation hosts, physical
peripherals, large fonts and unpictured error/pending states remain unverified.
Ready-menu ownership is measured; asynchronous opening, rejected/delayed Activity
starts and broader stale/lifecycle races remain gaps. Earlier Gboard accessibility
and local Hide misses are retained; later passes do not certify universal flake
elimination. Harness key timing includes synthetic contact/IPC/observer delays and
is not physical finger latency. Native placement is optional and may be unavailable
when shell permission, trusted display or required policy methods are missing.

## Release checks, evidence and reviews

Current release-host qualityCheck passes in35s with287 JVM cases and no failures,
errors or skips. Structured tooling211cases has203passes/8skips in18.186s.
Requirement mapping, coverage, packaged fixture definitions, documentation,
privacy and Ruff checks pass. No lint baseline changes. The
[host preparation summary/archive](../../testresults/0.18.0/host-preparation/summary.md)
binds actual app/tool/build inputs and retains raw logs/JUnit. Three unrelated
policy documents remain unstaged; their working-tree text is not released.

CI produces its own current-run public-safe host evidence and Markdown/JSON
release report with checksums; private phone evidence is never uploaded.
No architecture release attestation or final candidate approval is claimed yet.

The final frozen snapshot must receive a fresh release architecture expert,
faithful attestation-only delta and two fresh independent final approvals before
commit/push/tag/signing/publication. Main protection and required host CI remain.

## Release architecture attestation

This actual review supersedes only the earlier preparation statement that no
architecture attestation existed; final code review and publication are separate.

- Target:0.18.0/code51. Preceding publishedv0.17.0:
  `5fdd642001e04a663f58cc6e68d288080299b36b`.
- Exact frozen pre-attestation Git tree:
  `d2dd1ab9fc1a89786e38ab49a3134c7022f95fc7`.
- Exact inspected candidate digest BEFORE this attestation:
  `8ef16f0ff8a90a3d9234ad6edae4487574f1d16ef98221a9202a47a4495433b0`.
- Fresh read-only expert: `/root/release018_architecture`.
  Actual review time: `2026-11-11T20:11:19.9651201Z`.
- Actual verdict: **APPROVE; no blocking findings; no unresolved consequential
  decision identified for retaining the implemented behavior unchanged.**

The expert independently inspected the cumulative staged diff, whole-product and
component/feature architecture, seven rendered diagrams, actual production/debug/
instrumentation source, contract, ownership tests, partial requirement coverage,
manifests, helper compatibility, build/wrapper changes and delivery/privacy gates.
Staged policy text was used where unrelated local edits differed. No source,
index, commit or device was changed by the reviewer.

Confirmed invariants: SessionService authority; helper-owned disposable displays
and policy leases; native Android input connection without typed-content IPC;
bounded latest-intent workers separate from input/audio; exact identity/display/
serial guards; compare/restore cleanup and finite120ms acknowledgement; one shared
creation focus mode; Android11 flag/routing preservation; exact physical-window
focus-bit ownership; bounded modal self-reorder with duplicate/stale guards;
remembered Off default without persisted live display IDs; helper protocol reload
through version increment. No new grant/default-IME/public privileged endpoint.

Evidence inspection verified all archive/member hashes: host36members, Android16
945members, Android11 65members. Host287 JVM cases and203tooling passes/8skips
were independently checked; all209 recorded host input hashes match staged inputs.
Device inventory differences and development50-to-release51 reuse were confirmed.
Diagram inputs match the staged Mermaid source and rendered output was inspected.
The user release directive supports retaining existing Off/stored-choice/manual
application behavior; no explicit radio selection is claimed.

Residual gaps: other OS/OEM/IME/Presentation combinations and physical peripherals;
large fonts/unpictured states; asynchronous or rejected starts and broader races;
retained intermittent failures; physical-finger latency. An already-submitted
native call cannot be revoked. Android11 native placement/general phone-IME
rendering remain uncertified. CI signed packaging, reports and publication were
not performed by this review. Only this faithful attestation follows the inspected
snapshot; two fresh independent final reviewers must approve its inclusion in
the final canonical digest. Local archive paths/access remain in the descriptors.
