"""Check local documentation links, Spec Kit sections and retained evidence hashes."""

import hashlib
import json
import re
import subprocess
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]


def headings(text):
    counts = {}
    anchors = set()
    for heading in re.findall(r"^#{1,6}\s+(.+?)\s*#*\s*$", text, re.M):
        slug = re.sub(r"[^\w -]", "", heading.lower().strip()).replace(" ", "-")
        count = counts.get(slug, 0)
        counts[slug] = count + 1
        anchors.add(slug if count == 0 else f"{slug}-{count}")
    return anchors


def check_links(root, files):
    errors = []
    root = root.resolve()
    for path in files:
        if not path.is_file():
            errors.append(f"Missing documentation file: {path}")
            continue
        text = path.read_text(encoding="utf-8")
        # Example commands/JSON contain template paths; only rendered prose links count.
        text = re.sub(r"```[^\n]*\n[\s\S]*?```|~~~[^\n]*\n[\s\S]*?~~~", "", text)
        targets = re.findall(r"!?\[[^\]\n]+\]\(([^)\n]+)\)", text)
        targets += re.findall(r"<(?:img|a)\b[^>]*\b(?:src|href)=[\"']([^\"']+)[\"']", text)
        for target in targets:
            target = target.strip()
            if target.startswith("<") and target.endswith(">"):
                target = target[1:-1]
            parts = urlsplit(target)
            if parts.scheme or parts.netloc:
                continue
            destination = (path.parent / unquote(parts.path)).resolve() if parts.path else path
            if not destination.is_relative_to(root) or not destination.exists():
                errors.append(f"{path.relative_to(root)}: missing/outside local link {target}")
                continue
            if parts.fragment and destination.suffix.lower() == ".md":
                if unquote(parts.fragment) not in headings(destination.read_text(encoding="utf-8")):
                    errors.append(f"{path.relative_to(root)}: missing heading link {target}")
    return errors


def check_evidence(root):
    errors = []
    folder = root / "testresults"
    manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    seen = set()
    for record in manifest.get("files", []):
        relative = record.get("path", "")
        path = (folder / relative).resolve()
        if relative in seen:
            errors.append(f"Duplicate evidence path: {relative}")
        seen.add(relative)
        if not path.is_relative_to(folder.resolve()) or not path.is_file():
            errors.append(f"Missing/outside evidence: {relative}")
            continue
        if hashlib.sha256(path.read_bytes()).hexdigest() != record.get("sha256"):
            errors.append(f"Evidence hash changed: {relative}")
        if record.get("status") != "historical":
            errors.append(f"Imported evidence must retain historical status: {relative}")
    if not seen:
        errors.append("No retained evidence records")
    for pattern in ["*checks*.txt", "*acceptance*.txt"]:
        for path in root.glob(pattern):
            errors.append(f"Test log remains at repository root: {path.name}")
    return errors


def check_spec(root):
    text = (root / "SPEC.md").read_text(encoding="utf-8")
    required = [
        "User Scenarios & Testing",
        "Requirements",
        "Functional Requirements",
        "Key Entities",
        "Success Criteria",
        "Measurable Outcomes",
        "Assumptions",
    ]
    actual = headings(text)
    errors = [
        f"Spec Kit section missing: {name}"
        for name in required
        if re.sub(r"[^\w -]", "", name.lower()).replace(" ", "-") not in actual
    ]
    if "## Implementation history" in text:
        errors.append("Release history belongs in CHANGELOG.md")
    return errors


def main():
    listing = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "--", "*.md"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        check=True,
    )
    files = [ROOT / name for name in sorted(set(listing.stdout.splitlines()))]
    errors = check_links(ROOT, files) + check_evidence(ROOT) + check_spec(ROOT)
    for error in errors:
        print(f"FAIL {error}")
    print(
        f"Checked {len(files)} Markdown documents, Spec Kit sections and retained evidence hashes."
    )
    print("Documentation integrity is separate from device/visual verification.")
    return int(bool(errors))


if __name__ == "__main__":
    raise SystemExit(main())
