# 0.18.0 verification

- [Current release-host preparation](host-preparation/summary.md): source-bound
  local checks, cached JVM results, structured tooling outcomes and verified archive.
- [Android16 feature acceptance](../0.18.0-dev/native-monitor-keyboard/summary.md)
  and [Android11 fallback/regression](../0.18.0-dev/android11-keyboard/summary.md):
  original development binaries/configurations, private device evidence and gaps.
- [Release record](../../docs/changes/release-0.18.0.md): scope, metadata-only
  evidence reuse, review attestations and publication status.

The release workflow generates the actual Markdown/JSON reports and current-run
public host archive on its exact tagged commit and attaches them with checksums.
Local device archives are not uploaded; their availability stays local-only.
