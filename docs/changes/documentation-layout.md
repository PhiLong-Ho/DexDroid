# Documentation layout and specification rewrite

## Contract

DX-DOC-001 / NFR-004: README is concise setup/install/quick use; release changes
are in CHANGELOG; logs/screenshots are under versioned testresults; the current
spec follows GitHub Spec Kit's feature template. The contract was added to SPEC
before the rewrite and implementation. App behavior and release versions are unchanged.

## Implementation

- Replaced the repeated history/contract/prototype spec with eight prioritized
  user stories, Given/When/Then scenarios, edge cases, numbered requirements,
  entities, success criteria, assumptions and explicit deferred features.
- Preserved stable DX IDs and linked them to FR/NFR/DFR numbers. Added a reusable
  Spec Kit-style feature template with a pinned upstream attribution.
- Moved release changes to CHANGELOG and kept build/install/use in README.
- Relocated 25 root release logs without altering bytes; retained 8 prior host
  maintenance logs and 13 selected 0.10 phone/monitor screenshots. Version summaries
  and a 46-file SHA-256/provenance manifest label these as historical evidence.
- Added version/timestamp output for future device runs and versioned CI evidence.
  New generated runs are ignored until explicitly retained. Raw logs are exempt
  from Git line-ending conversion. Screenshots are not approved golden baselines.
- Added documentation link/anchor, specification-section and evidence-hash checks;
  requirement validation now resolves numbered spec requirements as well as headings.

## Acceptance

| Criterion | Evidence/check | Result |
| --- | --- | --- |
| Historical release logs preserved | Validate 25 pre-move hashes and original Git text; all 46 imported hashes resolve | Passed |
| Local references resolve | `python tools/check_documentation.py` | Passed |
| Stable DX mappings retained | `python tools/check_requirements.py`: 24 IDs, including the new documentation requirement | Passed |
| Tooling handles moved evidence | Version-folder runner fixtures, path validation, link/anchor and changed-byte negative cases | 32 tooling tests passed |
| Consistent formatting/lint/build | `gradlew --no-daemon qualityCheck`; Ruff check/format | Passed; 72 JVM cases, app/test APK build and lint |
| Concise entry point | README contains requirements, build/install, quick steps and documentation links | Reviewed |

Actual logs for this change are retained in the versioned documentation run linked
from [unreleased evidence](../../testresults/unreleased/README.md). Environment:
Windows/JBR 21.0.4, Python 3.13, Gradle 8.10.2, AGP 8.8.0; base revision `3599178`.
Runner tests use fake ADB and synthetic APKs, not physical-device passes. No app
code/resources changed, no device suite was rerun, and no new screenshots were taken.

Four older working logs used CRLF while their Git blobs were LF-normalized.
Their raw working-file bytes were preserved; both hashes and the line-ending
distinction are recorded in the provenance manifest. Text matches the original
Git records after line-ending normalization; no result content changed.

The rewrite adapts the template's document structure only; it does not install
Spec Kit CLI, overwrite agent configuration or claim its commands are installed.
