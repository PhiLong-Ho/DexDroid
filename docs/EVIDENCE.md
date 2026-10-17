# Evidence storage and retrieval

Keep source review small while retaining inspectable results. New evidence uses
the following policy; existing tracked historical records remain unchanged.

| Material | Location | Retention and meaning |
| --- | --- | --- |
| Raw local logs, PNGs, traces and run metadata | Ignored `testresults/<version>/runs/<run>/` | Local until explicitly archived/uploaded; preserve originals |
| Routine CI and failed CI evidence | Actions artifact for that current run | Workflow requests 14 days, subject to repository limits/run deletion |
| Current release host evidence | `release-evidence.zip` and `release-evidence.json` on that release | Durable beyond Actions retention, until asset/release removal |
| Scope, outcomes, gaps, source/build references and archive location/hash | Concise tracked `summary.md`/`README.md` and `archive.json` | Source-controlled evidence index; not raw execution or availability proof |
| Reproducible approved visual fixtures | `tests/visual/` | Small intentional review assets; never auto-approve |

GitHub's [artifact guidance](https://docs.github.com/en/actions/how-tos/writing-workflows/choosing-what-your-workflow-does/storing-and-sharing-data-from-a-workflow)
explains configured retention. [Deleting a run also removes its artifacts](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/remove-workflow-artifacts).
An Actions link is not permanent evidence. If evidence must outlive its retention,
preserve the bundle before expiry through an authorized release/archive operation.

## Local runs

Finish a run, inspect logs and rendered images, and write its actual source/build,
configuration, commands, outcomes and gaps. Retain relevant failed attempts as
separate completed run folders; never recast them as passes. Do not duplicate raw
trees under tracked feature folders or use `git add -f` to add them.

Bundle a completed folder into a **new** ignored output directory:

```powershell
python tools/evidence.py pack --source testresults/0.12.0/runs/<run> `
  --output artifacts/evidence/<run> --scope "Actual suites/configuration; review status and gaps"
python tools/evidence.py verify --archive artifacts/evidence/<run>/evidence.zip `
  --descriptor artifacts/evidence/<run>/archive.json
```

Replace the placeholders with the actual printed run ID. Packing preserves raw
bytes and originals, records every file hash and a ZIP checksum, and verifies its
own output. It refuses an existing output directory, recursive output, empty
source, links, or source changes during capture. An interrupted/rejected capture
can leave an ignored `.partial` file; it has no verified descriptor. Finish the
source run and retry to a fresh output directory.

Copy only the compact descriptor to `testresults/<version>/<change>/archive.json`
and add a short summary with the real outcomes and review scope. A descriptor's
`packaging_commit`/dirty state identifies packaging, not proof of the tested build;
retain the runner's actual APK/source provenance inside the bundle and reference
it in the summary. Changing the descriptor's availability/location does not change
the sealed archive contents or test outcomes.

The pack command performs **no upload**. Its descriptor says `local-only`. Record
the actual Actions run/artifact URL and expiry, or the approved release asset URL,
when uploaded; retain the archive hash. A proposed future URL is a target, not a
successful upload. Local device runs do not automatically appear in GitHub-hosted
CI. Their transfer and retention remain explicit operations, within the user's
authorized publication scope. The release workflow archives its own current host
run, and does not thereby certify or ingest local phone testing.

## Review and retrieval

Before final candidate review, give both reviewers access to the bundle and its
tracked descriptor. Each verifies the ZIP/member hashes, inspects relevant raw
results/renders, and checks source/build/configuration against the claim. A hash
attests byte consistency, not identity, correctness or a passed test.

For a downloaded bundle, run `tools/evidence.py verify` with the trusted tracked
descriptor and explicit downloaded ZIP path. Verification checks member names,
duplicates, missing/unexpected files, link entries, checksums, byte totals and
scope/timestamp binding **without extraction**. After successful verification,
extract with an archive viewer into an ignored review directory to inspect logs
and PNGs. Do not commit the extracted files. If an archive is unavailable or expired,
report that gap; a stored SHA cannot recreate lost bytes.

Avoid including unrelated device content in public artifacts. If redaction is
needed, retain originals separately and describe the transformation and both hashes.
Do not silently rewrite previously retained raw evidence.

## CI and releases

Both workflows bundle only their current `${EVIDENCE_DIR}` and upload that ZIP
and descriptor to Actions, including available failure evidence. Descriptors record
the target run URL and requested retention; upload status/expiry must still be checked. Host reports are collected before release archiving.
The release ZIP includes the generated report, raw host logs, structured tooling
outcomes, JUnit sources and source-commit marker. It excludes the signing-key and
binary-output folders. The source tree is never modified by report generation.

`release.py archive --evidence <run-folder>` verifies the existing package/report,
seals the current host folder, checks report/test-source/commit bindings, and adds
the ZIP/descriptor to `SHA256SUMS.txt`. `release.py publish` checks all of this
again **before** any GitHub API call. Missing, changed, mismatched or unsafe evidence
blocks publication. The immutable ZIP can include the report; its descriptor and
outer checksums are outside the ZIP, avoiding circular hashes.

Future release reports link the raw archive. Existing releases/history are not
rewritten. This change alone does not upload bundles or run a release. See
[RELEASING](RELEASING.md), [QUALITY](QUALITY.md) and [test results](../testresults/README.md).
