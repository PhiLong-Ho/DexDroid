"""Synthetic release report fixtures; never production execution evidence."""

import hashlib
import json
import shutil
from unittest import mock

import release
import release_report


def raw_sources(metadata):
    return {
        "tooling": (
            "tool-tests.json",
            json.dumps(
                {
                    "schema_version": 1,
                    "source_commit": metadata["source_commit"],
                    "cases": [{"id": "tooling.fixture", "status": "passed"}],
                }
            ).encode(),
        ),
        "jvm": (
            "TEST-fixture.xml",
            b'<testsuite name="jvm" tests="1"><testcase classname="jvm" name="fixture"/></testsuite>',
        ),
    }


def write_report(output, metadata):
    data = {
        key: metadata[key]
        for key in ("version", "version_code", "tag", "repository", "source_commit")
    }
    data.update(
        schema_version=1,
        workflow_url=f"https://github.com/{metadata['repository']}/actions/runs/42",
        tests={
            name: {
                "cases": [
                    {
                        "id": "jvm::jvm::fixture" if name == "jvm" else "tooling.fixture",
                        "status": "passed",
                    }
                ],
                "sources": [
                    {
                        "name": raw_sources(metadata)[name][0],
                        "sha256": hashlib.sha256(raw_sources(metadata)[name][1]).hexdigest(),
                    }
                ],
            }
            for name in ("jvm", "tooling")
        },
        requirements=[
            {
                "id": "DX-FIXTURE-001",
                "criterion": "Synthetic fixture",
                "coverage": "mapped",
                "gap": "",
            }
        ],
    )
    (output / "release-report.json").write_text(json.dumps(data), encoding="utf-8")
    (output / "release-report.md").write_text(release_report.render(data), encoding="utf-8")
    rehash(output)
    return data


def write_archive(output, metadata):
    source = output.parent / "fixture-evidence"
    (source / "host/test-results/testDebugUnitTest").mkdir(parents=True)
    (source / "source-commit.txt").write_text(metadata["source_commit"] + "\n", encoding="utf-8")
    (source / "host/quality.txt").write_bytes(b"Synthetic fixture only\r\n")
    for suite, (name, content) in raw_sources(metadata).items():
        prefix = "host" if suite == "tooling" else "host/test-results/testDebugUnitTest"
        (source / prefix / name).write_bytes(content)
    for name in release_report.FILES:
        shutil.copyfile(output / name, source / name)
    with mock.patch.object(release, "run", return_value=metadata["source_commit"]):
        release.archive_release(output.parent, output, source)


def rehash(output):
    names = [
        path.name
        for path in output.iterdir()
        if path.suffix in {".apk", ".aab"}
        or path.name in {"build.json", *release_report.FILES, *release.EVIDENCE_FILES}
    ]
    (output / "SHA256SUMS.txt").write_text(
        "".join(
            f"{hashlib.sha256((output / name).read_bytes()).hexdigest()}  {name}\n"
            for name in sorted(names)
        ),
        encoding="utf-8",
    )
