"""Bundle completed evidence runs without committing raw files or deleting originals."""

import argparse
import hashlib
import json
import re
import stat
import subprocess
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHUNK = 1024 * 1024


def digest_file(path):
    with path.open("rb") as stream:
        return digest_stream(stream)


def digest_stream(stream):
    digest = hashlib.sha256()
    while chunk := stream.read(CHUNK):
        digest.update(chunk)
    return digest.hexdigest()


def safe_name(name):
    if not isinstance(name, str) or not name or any(char in name for char in '\\<>:"|?*'):
        raise ValueError("Unsafe evidence member name")
    if any(part in {"", ".", ".."} or ":" in part for part in name.split("/")) or any(
        ord(char) < 32 for char in name
    ):
        raise ValueError("Unsafe evidence member name")
    # Match portable Windows device-name rules even on Python 3.10/Linux:
    # https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file
    reserved = {"CON", "PRN", "AUX", "NUL", "CONIN$", "CONOUT$"} | {
        f"{prefix}{n}" for prefix in ("COM", "LPT") for n in "123456789\u00b9\u00b2\u00b3"
    }
    if any(
        part.rstrip(" .") != part or part.split(".")[0].rstrip(" ").upper() in reserved
        for part in name.split("/")
    ):
        raise ValueError("Unsafe Windows evidence member name")
    return name


def snapshot(source):
    files = {}
    folded = set()
    for path in sorted(source.rglob("*")):
        if path.is_symlink() or getattr(path, "is_junction", lambda: False)():
            raise ValueError("Evidence must not contain symbolic links or junctions")
        if not path.resolve().is_relative_to(source):
            raise ValueError("Evidence path leaves its run folder")
        if path.is_dir():
            continue
        if not path.is_file():
            raise ValueError("Evidence contains a non-regular file")
        name = safe_name(path.relative_to(source).as_posix())
        if name.casefold() in folded:
            raise ValueError("Evidence names collide on a case-insensitive filesystem")
        folded.add(name.casefold())
        info = path.stat()
        files[name] = (info.st_size, info.st_mtime_ns)
    if not files:
        raise ValueError("Evidence run is empty")
    return files


def verify_archive(archive, descriptor):
    if descriptor.get("schema_version") != 1:
        raise ValueError("Unsupported evidence descriptor")
    if digest_file(archive) != descriptor.get("archive_sha256"):
        raise ValueError("Evidence archive checksum mismatch")
    if archive.stat().st_size != descriptor.get("archive_bytes"):
        raise ValueError("Evidence archive size mismatch")
    with zipfile.ZipFile(archive) as bundle:
        members = bundle.infolist()
        names = [safe_name(item.filename) for item in members]
        if len({name.casefold() for name in names}) != len(names):
            raise ValueError("Duplicate evidence member")
        for item in members:
            kind = stat.S_IFMT(item.external_attr >> 16)
            if kind not in {0, stat.S_IFREG} or item.flag_bits & 1:
                raise ValueError("Unsafe or encrypted evidence member")
        manifest_info = bundle.getinfo("manifest.json")
        if manifest_info.file_size > 8 * 1024 * 1024:
            raise ValueError("Evidence manifest is too large")
        manifest = json.loads(bundle.read("manifest.json"))
        if manifest.get("schema_version") != 1:
            raise ValueError("Unsupported evidence manifest")
        records = manifest.get("files")
        if not isinstance(records, list) or not records:
            raise ValueError("Evidence manifest has no files")
        expected = {"manifest.json"}
        raw_bytes = 0
        for record in records:
            name = "data/" + safe_name(record["path"])
            if name in expected or not re.fullmatch(r"[a-f0-9]{64}", record["sha256"]):
                raise ValueError("Invalid or duplicate evidence record")
            expected.add(name)
            if type(record["bytes"]) is not int or record["bytes"] < 0:
                raise ValueError("Invalid evidence byte count")
            info = bundle.getinfo(name)
            if info.file_size != record["bytes"]:
                raise ValueError("Evidence member size mismatch")
            with bundle.open(name) as stream:
                if digest_stream(stream) != record["sha256"]:
                    raise ValueError("Evidence member checksum mismatch")
            raw_bytes += record["bytes"]
        if set(names) != expected:
            raise ValueError("Unexpected evidence members")
        if descriptor.get("file_count") != len(records) or descriptor.get("raw_bytes") != raw_bytes:
            raise ValueError("Evidence descriptor totals mismatch")
        context = manifest.get("context")
        if (
            not isinstance(context, dict)
            or not isinstance(context.get("scope"), str)
            or not context["scope"].strip()
        ):
            raise ValueError("Evidence scope is missing")
        if descriptor.get("context") != context or descriptor.get("created_utc") != manifest.get(
            "created_utc"
        ):
            raise ValueError("Evidence context mismatch")
    return manifest


def create_archive(source, output, context, storage=None):
    source, output = Path(source), Path(output)
    if source.is_symlink() or getattr(source, "is_junction", lambda: False)():
        raise ValueError("Evidence source must not be a link")
    source, output = source.resolve(strict=True), output.resolve()
    if not source.is_dir() or output.is_relative_to(source):
        raise ValueError("Archive output must be outside the evidence source directory")
    if (
        not isinstance(context, dict)
        or not isinstance(context.get("scope"), str)
        or not context["scope"].strip()
    ):
        raise ValueError("Declare the evidence scope; packaging does not prove tests passed")
    before = snapshot(source)
    output.mkdir(parents=True, exist_ok=False)
    temporary = output / "evidence.zip.partial"
    created = datetime.now(timezone.utc).isoformat()
    records = []
    with zipfile.ZipFile(temporary, "x", compression=zipfile.ZIP_DEFLATED) as bundle:
        for name in before:
            digest, count = hashlib.sha256(), 0
            with (
                (source / name).open("rb") as raw,
                bundle.open("data/" + name, "w", force_zip64=True) as target,
            ):
                while chunk := raw.read(CHUNK):
                    target.write(chunk)
                    digest.update(chunk)
                    count += len(chunk)
            records.append({"path": name, "bytes": count, "sha256": digest.hexdigest()})
        if snapshot(source) != before:
            raise ValueError("Evidence changed while archiving; finish the run before retrying")
        bundle.writestr(
            "manifest.json",
            json.dumps(
                {"schema_version": 1, "created_utc": created, "context": context, "files": records},
                indent=2,
            ).encode("utf-8"),
        )
    descriptor = {
        "schema_version": 1,
        "archive": "evidence.zip",
        "archive_sha256": digest_file(temporary),
        "archive_bytes": temporary.stat().st_size,
        "file_count": len(records),
        "raw_bytes": sum(record["bytes"] for record in records),
        "created_utc": created,
        "context": context,
        "storage": storage or {"kind": "local-only", "path": str(output / "evidence.zip")},
    }
    verify_archive(temporary, descriptor)
    temporary.rename(output / "evidence.zip")
    with (output / "archive.json").open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(descriptor, indent=2) + "\n")
    return descriptor


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    pack = commands.add_parser("pack")
    pack.add_argument("--source", type=Path, required=True)
    pack.add_argument("--output", type=Path, required=True, help="New directory outside source")
    pack.add_argument("--scope", required=True, help="Actual run/review scope and gaps")
    pack.add_argument("--workflow-url", help="Actions target URL; does not claim upload success")
    verify = commands.add_parser("verify")
    verify.add_argument("--archive", type=Path, required=True)
    verify.add_argument("--descriptor", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "pack":
        commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
        dirty = bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT))
        context = {"scope": args.scope, "packaging_commit": commit, "packaging_tree_dirty": dirty}
        storage = None
        if args.workflow_url:
            if not re.fullmatch(
                r"https://github.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/actions/runs/[1-9]\d*",
                args.workflow_url,
            ):
                raise ValueError("Expected a GitHub Actions run URL")
            context["workflow_url"] = args.workflow_url
            storage = {
                "kind": "github-actions-target",
                "url": args.workflow_url,
                "retention_days": 14,
                "availability": "Check artifact upload status and expiry; packaging is not upload confirmation",
            }
        result = create_archive(
            args.source,
            args.output,
            context,
            storage,
        )
        print(f"Archived {result['file_count']} files; SHA-256 {result['archive_sha256']}")
        print("Original files retained. Packaging does not confirm upload or a passed test.")
    else:
        descriptor = json.loads(args.descriptor.read_text(encoding="utf-8"))
        verify_archive(args.archive, descriptor)
        print("PASS archive bytes, member set, provenance context and per-file hashes")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        raise SystemExit(f"FAIL {error}") from error
