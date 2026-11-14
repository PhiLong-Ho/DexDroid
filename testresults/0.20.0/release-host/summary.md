# 0.20.0 local release host checks

Local Windows candidate verification, baseline
`53bf44a5bb9e04433d143feaa593137ed863f1e8`; uncommitted feature branch. Current
authoritative inputs are `host/source-inputs-completed.json`,
`host/quality-android11-fixtures.log`, `host/tool-tests-completed.json`,
`host/tooling-completed.log` and `host/fixture-completed.log` inside the
[private archive](archive.json). Earlier preparation results are retained as
separate historical files, not added to current denominators.

- `qualityCheck`:318 JVM cases,318 pass, zero failures/errors/skips. Build succeeds
  in13s after Android11 fixture corrections; Gradle cache reuse is disclosed by log.
- Current structured Python tooling:211 cases,210 pass and one Windows-only skip
  (99.53% including the skip in the denominator), no failure/error.
- Requirement validation, generated coverage, documentation/archive integrity,
  fixture-runtime definitions, Ruff check/format and privacy/whitespace guards pass.

APK identities are retained with source hashes. Product remains
`fcd28277a380e54a5acbfc863a81afe803117e623a52fbef61f61bc705a2be92`;
final development tests are
`39e6a1afa1121baee644d6224ef425751f950c9b71d7e755e9059135687a12a6`.
Native device outcomes and source reuse are separately indexed in the version
folders; host assertions do not establish full requirement, device, visual or
performance coverage.

This archive is local-only, retained until separately removed; no device data or
logs are uploaded by this operation. CI/release workflows run their own gates,
generate their own source-bound reports and public current-host evidence. This
record does not claim CI, signing, publication or final review approval.
