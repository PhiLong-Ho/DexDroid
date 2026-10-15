# Signed Android release automation

## Contract

DX-RELEASE-001 / NFR-005: tagged/manual builds produce signed APK and AAB files,
checksums and short version-specific notes on GitHub Releases after host gates.
Mismatched inputs, unsigned/mismatched artifacts and failed checks prevent
publication. Drafts can recover; published releases are not overwritten.
The specification was updated before implementation; acceptance tests followed.

## Implementation

- Added `Release Android` for pushed `v*` tags and manual existing-tag dispatch.
  Source/tag/app version/changelog are validated before building. Release tags
  run the quality gates themselves; the separate Quality workflow covers branches/PRs.
- Configured release signing from private environment inputs or ignored local
  properties. Generated one persistent JKS and configured four repository secrets;
  private backup stays in `.local/signing/`, excluded from Git.
- Built `assembleRelease` and `bundleRelease`. Packaging checks actual APK package,
  version and non-debuggable status, both signatures and matching certificates.
  Assets include APK, AAB, SHA256SUMS and public build/signature metadata.
- Notes use at most five bullets from the matching changelog section, with pinned
  full-changelog and historical verification links. No commit-log dump or invented
  device verification is added.
- Publishing is a separate, minimally privileged job: upload to draft, then publish.
  Only drafts can be overwritten on recovery. Missing/API-error/published states
  are handled distinctly; a published tag requires a new version.

## Acceptance

| Criterion | Check/evidence | Result |
| --- | --- | --- |
| Version/notes and safe publication behavior | 18 release-focused tests for tag mismatch, missing/duplicate notes, version isolation, signatures, checksums, drafts, published releases and API errors | Passed |
| Existing tooling regression | Full tooling suite | 50 passed |
| Host quality and binaries | `qualityCheck :app:assembleRelease :app:bundleRelease` | Passed; 72 JVM cases and lint/build checks |
| Real signed artifacts | APK package/code/name, non-debuggable state, apksigner, jarsigner and matching SHA-256 certificate | Passed locally |
| Documentation/traceability | Link/evidence and requirement validators; Ruff checks | Passed; 25 stable requirements |
| GitHub end to end | Tag workflow, published assets, notes and downloaded hashes | To be recorded after the hosted run |

Local evidence is linked from [unreleased results](../../testresults/unreleased/README.md).
Environment: Windows/JBR 21.0.4, Gradle 8.10.2, AGP 8.8.0, Python 3.13; base
revision `43e46e1` plus this release-workflow change. Signature fingerprint is
public in the evidence; no private values are committed. Existing lint debt
remains tracked. No phone install or fresh physical-device suite ran.

Release APKs use a dedicated certificate, different from the current development
debug install. In-place replacement across those certificates is unsupported;
future release builds reuse this same key. Backup/migration details are in
[RELEASING.md](../RELEASING.md).
