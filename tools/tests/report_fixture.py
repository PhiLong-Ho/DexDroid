"""Synthetic release report fixtures; never production execution evidence."""

import hashlib
import json

import release_report


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
                "cases": [{"id": f"{name}.fixture", "status": "passed"}],
                "sources": [{"name": f"{name}.fixture", "sha256": "a" * 64}],
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


def rehash(output):
    names = [
        path.name
        for path in output.iterdir()
        if path.suffix in {".apk", ".aab"} or path.name in {"build.json", *release_report.FILES}
    ]
    (output / "SHA256SUMS.txt").write_text(
        "".join(
            f"{hashlib.sha256((output / name).read_bytes()).hexdigest()}  {name}\n"
            for name in sorted(names)
        ),
        encoding="utf-8",
    )
