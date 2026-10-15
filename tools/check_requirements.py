"""Validate requirement traceability; never treat a mapping as execution proof."""

import argparse
import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def validate(data, root):
    errors = []
    if data.get("schema_version") != 1:
        errors.append("Unsupported schema_version")
    spec = (root / "SPEC.md").read_text(encoding="utf-8")
    headings = {line.lstrip("# ").strip() for line in spec.splitlines() if line.startswith("#")}
    spec_ids = re.findall(r"^- \*\*((?:FR|NFR|DFR)-\d{3})\*\*:", spec, re.M)
    if len(spec_ids) != len(set(spec_ids)):
        errors.append("Duplicate numbered specification requirement")
    suites = data.get("suites", {})
    dimensions = data.get("dimensions", {})
    for name, suite in suites.items():
        entry = (root / suite.get("entry", "")).resolve()
        if not entry.is_relative_to(root.resolve()) or not entry.is_file():
            errors.append(f"{name}: missing/outside-repo suite entry")
    seen = set()
    for req in data.get("requirements", []):
        ident = req.get("id", "")
        if not re.fullmatch(r"DX-[A-Z]+-\d{3}", ident) or ident in seen:
            errors.append(f"Invalid/duplicate requirement: {ident}")
        seen.add(ident)
        if not req.get("criterion", "").strip():
            errors.append(f"{ident}: missing observable criterion")
        if req.get("spec_section") not in headings:
            errors.append(f"{ident}: spec heading missing")
        if req.get("spec_requirement") not in spec_ids:
            errors.append(f"{ident}: numbered spec requirement missing")
        coverage = req.get("coverage")
        if coverage not in {"mapped", "partial", "manual", "deferred"}:
            errors.append(f"{ident}: invalid coverage status (not a test result)")
        if coverage != "mapped" and not req.get("gap", "").strip():
            errors.append(f"{ident}: explicit gap required")
        if coverage in {"mapped", "partial"} and not req.get("suites"):
            errors.append(f"{ident}: automated mapping needs a suite")
        for suite in req.get("suites", []):
            if suite not in suites:
                errors.append(f"{ident}: unknown suite {suite}")
        if not req.get("dimensions"):
            errors.append(f"{ident}: scenario dimensions required")
        for dim in req.get("dimensions", []):
            if dim not in dimensions:
                errors.append(f"{ident}: unknown scenario dimension {dim}")
    if not seen:
        errors.append("No requirements")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-no-gaps", action="store_true")
    args = parser.parse_args()
    data = json.loads((ROOT / "docs/requirements.json").read_text(encoding="utf-8"))
    errors = validate(data, ROOT)
    counts = Counter(req["coverage"] for req in data["requirements"])
    if args.require_no_gaps and any(req["coverage"] != "mapped" for req in data["requirements"]):
        errors.append("Coverage gaps remain; consult requirement gap fields")
    for error in errors:
        print(f"FAIL {error}")
    print(f"{len(data['requirements'])} requirements: {dict(counts)}")
    print("A valid mapping is not evidence that every scenario passed.")
    return bool(errors)


if __name__ == "__main__":
    raise SystemExit(main())
