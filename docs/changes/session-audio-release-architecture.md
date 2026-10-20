# Release architecture review: 0.14.0

- Target: 0.14.0 / versionCode40. Published baseline: v0.13.0,
  2539b7b13e7e3c3015b2b8306e837e1fcfa2c5ed. Candidate HEAD: 8765bc9.
- Exact frozen pre-attestation tree:
  `b5162c4f91102f0afd659815b4fae0d68d21aaef`.
- Pre-attestation canonical digest:
  `777311aec35b8f4b67565961026aa2f9f8e142d508b3dcc762335358c9f7e6e4`.
- Actual fresh expert: `/root/audio_014_release_arch`.
- Actual review time: 2026-10-20T18:38:44Z.
- Verdict: **approve; no release architecture blockers found**.

## Inspected scope and findings

The expert inspected cumulative changes since the published baseline, staged
version/release packaging, system/component/feature diagrams, production wiring,
AIDL, audio policy lease and worker, shared picker, fixture/acceptance tests,
requirements, privacy and delivery contracts. All six retained diagram renders
were inspected and Mermaid sources matched current documents without material
drift. The expert also inspected current phone/monitor picker captures; the
separate nine-image UI approval remains scoped rendered acceptance.

Authoritative service state and shared phone/monitor behavior are preserved.
Phone-wide MEDIA policy belongs exclusively to the primary shell helper. A
separate serial coalescing audio worker guards stale results, retires old epochs
before replacement and retains recovery ownership after failed cleanup. Baseline
restoration compares observed policy before writing and retains full original
preferences. Endpoint identities/choices are not persisted. Capability probing,
shell identity, bounded endpoint summaries and private evidence boundaries were
reviewed as appropriate. Existing input-queue limitations are unchanged.

## Independently checked evidence

The expert verified both current private ZIPs and every member hash (host36,
device24 files),168 build/test source fingerprints, signed/unsigned APK hashes
and signature-only payload differences. Raw device instrumentation contains170
PASS assertions, result -1,18.364s duration and successful controller restoration.
Actual JUnit XML totals211 cases with zero failures/errors/skips. Tooling has210
passes/one skip and all seven host gates exit zero. Nine current catalog image
hashes and72 UI-source fingerprints match. See the
[current acceptance/archive index](../../testresults/0.14.0/session-audio/README.md).
The review was read-only, with no device operations or file edits.

## Nonblocking limitations and publication boundary

The authorized focused release retains documented gaps: hidden/OEM audio API
availability, non-atomic external-policy comparison, no guaranteed rollback when
the helper dies first, Bluetooth/acoustic playback, physical hotplug/helper-death
testing, broader OS/OEM coverage, complete accessibility/keyboard/Fit combinations
and latency distributions. Debugging-off controls and banking tests are excluded.

This is a faithful attestation of that inspected snapshot, not runtime or production
certification. Only this record follows the frozen inputs. Two different fresh
final reviewers must approve its inclusion in the full final canonical digest
before commit/push and authorized release. Signing/publication remain subsequent
gates; no new digest exclusions or reused final approvals are introduced.
