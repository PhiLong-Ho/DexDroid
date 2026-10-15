# Change: release coverage and pass-rate reports

## Contract (before implementation)

SPEC story 9, NFR-008 / DX-REPORT-001 and SC-013 require a versioned Markdown
release report with requirement coverage, measured test pass rates, explicit gaps,
and changelog/release/source/workflow links. Missing or invalid evidence blocks
publication. DX-RELEASE-001 and DX-MAINT-001 are also affected. Existing published
releases are preserved; new releases adopt the reporting contract.

## Implementation

`tools/run_tool_tests.py` records actual unittest case outcomes, including skips,
expected failures and subtest failures aggregated to their parent case.
`tools/release_report.py` reads those source-bound results, validates Gradle JUnit
XML and current requirement coverage, and writes Markdown plus JSON. The report
has per-suite/combined host rates and requirement mapping counts/gaps; unrun
device/visual/performance checks stay unverified. Gradle cache reuse is disclosed.

The release workflow generates reports after verified signed packaging, copies
them into its versioned evidence, adds a job summary and includes both release
assets in checksums. Publication validates report identity/content and refuses
missing/tampered/mismatched reports before GitHub mutations. Release notes link
the report and exact changelog. Project policy/skill now requires this every release.
CI does not mutate the reviewed Git tree. Version advances to 0.10.2 / 17;
Android behavior, UI design and lint baseline are unchanged.

## Acceptance (after implementation)

| Criterion | Test | Result |
| --- | --- | --- |
| Rates derive from actual outcomes with explicit denominators | ReleaseReportTests, StructuredToolResultsTests | PASS |
| Requirement totals/gaps and version-specific links are preserved | ReleaseReportTests | PASS |
| Empty, malformed, failed or mismatched evidence blocks reports | ReleaseReportTests | PASS |
| Missing/tampered/mismatched reports block publication | ReleasePublicationTests | PASS |
| Generated reports flow through checksummed publication | ReleaseReportTests | PASS; GitHub mutations mocked |
| Format/lint/build/JVM and tooling remain valid | Host checks | PASS; 72 JVM cases and 83 tooling tests |

[Versioned host evidence](../../testresults/0.10.2/runs/20261015T054351Z-release-report/README.md)
records commands, environment and raw results. Coverage inventory/snapshot were
refreshed with every test edit. Actual fresh reviewer identities/verdicts and the
candidate digest are recorded in `reviews/approvals.json` before push/release.
Fresh physical-device, rendered visual and performance verification remain unrun;
no numerical performance or universal scenario claim is made.
