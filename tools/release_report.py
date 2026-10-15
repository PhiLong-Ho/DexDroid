"""Create a release report from structured host results and requirement coverage."""

import argparse
import hashlib
import json
import os
import re
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

import coverage

ROOT = Path(__file__).resolve().parents[1]
FILES = ("release-report.md", "release-report.json")
STATUSES = ("passed", "failed", "error", "skipped", "expected_failure", "unexpected_success")


def summarize(cases):
    if not cases or len({case["id"] for case in cases}) != len(cases):
        raise ValueError("Missing/duplicate test cases")
    if any(not case["id"] or case["status"] not in STATUSES for case in cases):
        raise ValueError("Invalid test outcome")
    counts = {status: 0 for status in STATUSES}
    counts.update(Counter(case["status"] for case in cases))
    return {"total": len(cases), **counts}


def junit_results(paths):
    cases, sources = [], []
    for path in sorted(paths):
        document = ET.fromstring(path.read_bytes())
        if document.tag not in {"testsuite", "testsuites"}:
            raise ValueError("Expected JUnit testsuite(s)")
        suites = [document] if document.tag == "testsuite" else list(document.iter("testsuite"))
        for suite in suites:
            children = list(suite.findall("testcase"))
            if not children:
                if list(suite.findall("testsuite")):
                    continue
                raise ValueError("Empty JUnit suite")
            if int(suite.get("tests", "-1")) != len(children):
                raise ValueError("JUnit case count mismatch")
            local = []
            for case in children:
                status = next(
                    (
                        label
                        for tag, label in [
                            ("error", "error"),
                            ("failure", "failed"),
                            ("skipped", "skipped"),
                        ]
                        if case.find(tag) is not None
                    ),
                    "passed",
                )
                name = case.get("name", "")
                if not name:
                    raise ValueError("Unnamed JUnit case")
                local.append(
                    {
                        "id": f"{suite.get('name', '')}::{case.get('classname', '')}::{name}",
                        "status": status,
                    }
                )
            counts = summarize(local)
            for attribute, status in [
                ("failures", "failed"),
                ("errors", "error"),
                ("skipped", "skipped"),
            ]:
                if int(suite.get(attribute, "0")) != counts[status]:
                    raise ValueError("JUnit outcome count mismatch")
            cases.extend(local)
        sources.append({"name": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    summarize(cases)
    return {"cases": cases, "sources": sources}


def validate(data, metadata):
    if data.get("schema_version") != 1:
        raise ValueError("Invalid release report schema")
    for key in ("version", "version_code", "tag", "repository", "source_commit"):
        if data.get(key) != metadata.get(key):
            raise ValueError(f"Release report {key} mismatch")
    repository, commit = data["repository"], data["source_commit"]
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Invalid report repository")
    if not re.fullmatch(r"[a-f0-9]{40}", commit):
        raise ValueError("Invalid report source commit")
    if data["tag"] != "v" + data["version"]:
        raise ValueError("Invalid report tag")
    if not re.fullmatch(
        re.escape(f"https://github.com/{repository}/actions/runs/") + r"[1-9]\d*",
        data.get("workflow_url", ""),
    ):
        raise ValueError("Missing/invalid release workflow link")
    for name in ("tooling", "jvm"):
        sources = data["tests"][name].get("sources", [])
        if not sources or any(
            not source.get("name") or not re.fullmatch(r"[a-f0-9]{64}", source.get("sha256", ""))
            for source in sources
        ):
            raise ValueError(f"Missing/invalid {name} source fingerprints")
        counts = summarize(data["tests"][name]["cases"])
        if not counts["passed"] or any(
            counts[s] for s in ("failed", "error", "unexpected_success")
        ):
            raise ValueError(f"Unsuccessful required {name} evidence")
    requirements = data["requirements"]
    if not requirements or len({req["id"] for req in requirements}) != len(requirements):
        raise ValueError("Missing/duplicate report requirements")
    for req in requirements:
        if req["coverage"] not in {"mapped", "partial", "manual", "deferred"}:
            raise ValueError("Invalid requirement coverage")
        if req["coverage"] != "mapped" and not req.get("gap"):
            raise ValueError("Missing requirement gap")


def cell(text):
    return str(text).replace("|", "&#124;").replace("\r", "").replace("\n", "<br>")


def render(data):
    repository, commit, version = data["repository"], data["source_commit"], data["version"]
    base = f"https://github.com/{repository}"
    source = f"{base}/blob/{commit}"
    download = f"{base}/releases/download/{data['tag']}"
    counts = Counter(req["coverage"] for req in data["requirements"])
    lines = [
        f"# DexDroid {version} release report",
        "",
        f"[Release]({base}/releases/tag/{data['tag']}) · "
        f"[Changelog]({source}/CHANGELOG.md#{version.replace('.', '').lower()}) · "
        f"[Source]({base}/commit/{commit}) · [Workflow/evidence]({data['workflow_url']}) · "
        f"[Structured report]({download}/release-report.json)",
        "",
        "## Host test results",
        "",
        "Pass rate = passed / all reported cases, including skipped and expected failures.",
        "Rates describe these host suites only; they do not establish device or requirement completeness.",
        "JVM results come from this Gradle invocation and may reuse valid cached test outputs.",
        "",
        "| Suite | Cases | Passed | Failed | Errors | Skipped | Expected failures | Unexpected successes | Pass rate |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for label, cases in [
        ("JVM", data["tests"]["jvm"]["cases"]),
        ("Tooling", data["tests"]["tooling"]["cases"]),
        ("Combined host", data["tests"]["jvm"]["cases"] + data["tests"]["tooling"]["cases"]),
    ]:
        # Suite-specific IDs are namespaced to prevent collisions in aggregation.
        count = Counter(case["status"] for case in cases)
        total = len(cases)
        values = [count[status] for status in STATUSES]
        lines.append(
            f"| {label} | {total} | "
            + " | ".join(map(str, values))
            + f" | {count['passed'] / total:.1%} |"
        )
    lines += [
        "",
        "## Requirement coverage",
        "",
        f"{len(data['requirements'])} requirements: "
        + ", ".join(
            f"{counts[status]} {status}" for status in ("mapped", "partial", "manual", "deferred")
        )
        + ".",
        "Mapping status is not an execution pass rate. Scenario dimensions are obligations, not proof they ran.",
        f"[Full case mappings and scenario dimensions]({source}/docs/coverage.md).",
        "",
        "| Requirement | Criterion | Mapping | Gap |",
        "| --- | --- | --- | --- |",
    ]
    for req in data["requirements"]:
        lines.append(
            f"| [{req['id']}]({source}/docs/coverage.md#{req['id'].lower()}) | "
            f"{cell(req['criterion'])} | {req['coverage']} | "
            f"{cell(req['gap'] or 'Run evidence still required for scenario claims.')} |"
        )
    lines += [
        "",
        "## Unrun and separate verification",
        "",
        "| Area | Current release workflow evidence |",
        "| --- | --- |",
        "| Physical phone/monitor acceptance | Not run by this workflow; unverified |",
        "| Rendered visual review | Not run by this workflow; unverified |",
        "| Performance distributions | Not measured by this workflow; unverified |",
        "",
        f"[Historical evidence index]({source}/testresults/README.md) is not a fresh device run.",
        f"[Candidate review attestations]({source}/reviews/approvals.json) are separate from test results.",
        "",
        "## Provenance",
        "",
        f"Source commit: `{commit}`. Version code: `{data['version_code']}`.",
        "Structured report retains case outcomes, source hashes and requirement snapshots.",
        "Raw host logs and JUnit XML are in the workflow evidence artifact (14-day retention).",
        "",
    ]
    return "\n".join(lines)


def generate(root, output, evidence, workflow_url):
    metadata = json.loads((output / "build.json").read_text(encoding="utf-8"))
    if metadata.get("verified") is not True:
        raise ValueError("Report requires verified release artifacts")
    coverage.check(root)
    tool_path = evidence / "host/tool-tests.json"
    tooling = json.loads(tool_path.read_text(encoding="utf-8"))
    if (
        tooling.get("schema_version") != 1
        or tooling.get("source_commit") != metadata["source_commit"]
    ):
        raise ValueError("Tool test evidence source/schema mismatch")
    data = {
        key: metadata[key]
        for key in ("version", "version_code", "tag", "repository", "source_commit")
    }
    data.update(
        schema_version=1,
        workflow_url=workflow_url,
        tests={
            "tooling": {
                "cases": tooling["cases"],
                "sources": [
                    {
                        "name": tool_path.name,
                        "sha256": hashlib.sha256(tool_path.read_bytes()).hexdigest(),
                    }
                ],
            },
            "jvm": junit_results(
                (root / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml")
            ),
        },
        requirements=json.loads((root / "docs/requirements.json").read_text(encoding="utf-8"))[
            "requirements"
        ],
    )
    validate(data, metadata)
    contents = (render(data), json.dumps(data, indent=2) + "\n")
    for name, content in zip(FILES, contents, strict=True):
        for directory in (output, evidence):
            (directory / name).write_text(content, encoding="utf-8", newline="\n")
    names = [f"DexDroid-{data['version']}.{ext}" for ext in ("apk", "aab")]
    names += ["build.json", *FILES]
    (output / "SHA256SUMS.txt").write_text(
        "".join(
            f"{hashlib.sha256((output / name).read_bytes()).hexdigest()}  {name}\n"
            for name in names
        ),
        encoding="utf-8",
        newline="\n",
    )
    return data


def validate_package(output, metadata):
    data = json.loads((output / FILES[1]).read_text(encoding="utf-8"))
    validate(data, metadata)
    if (output / FILES[0]).read_text(encoding="utf-8") != render(data):
        raise ValueError("Release report Markdown does not match structured evidence")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ROOT / "artifacts/release")
    args = parser.parse_args()
    try:
        repository = os.environ.get("GITHUB_REPOSITORY", "")
        run = os.environ.get("GITHUB_RUN_ID", "")
        generate(
            ROOT, args.output, args.evidence, f"https://github.com/{repository}/actions/runs/{run}"
        )
    except (ValueError, OSError, KeyError, TypeError, ET.ParseError) as error:
        print(f"FAIL {error}")
        return 1
    print("PASS versioned release report generated from structured evidence")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
