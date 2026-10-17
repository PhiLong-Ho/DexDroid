"""Block raw tracked evidence and contaminated public host input; local capture stays private."""

import argparse
import subprocess
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[1]
PUBLIC_PREFIX_BYTES = 16
SUMMARY_NAMES = {
    "readme.md",
    "summary.md",
    "archive.json",
    "release-report.md",
    "release-report.json",
    "manifest.json",
}
MEDIA = {
    ".png",
    ".jpg",
    ".jpeg",
    ".gif",
    ".webp",
    ".mp4",
    ".webm",
    ".capture-raw",
    ".zip",
    ".7z",
    ".tar",
    ".gz",
}
DEVICE_PARTS = {
    "submenu-acceptance",
    "monitor-acceptance",
    "menu-text-acceptance",
    "screenshots",
    "device",
    "devices",
}


def tracked_errors(names):
    errors = []
    for name in names:
        path = Path(name)
        if (
            path.parts
            and path.parts[0].lower() == "testresults"
            and path.name.lower() not in SUMMARY_NAMES
        ):
            errors.append(f"Raw evidence must not be tracked: {name}")
    return errors


def repository_errors(root):
    try:
        top = subprocess.check_output(
            ["git", "rev-parse", "--show-toplevel"],
            cwd=root,
            text=True,
            encoding="utf-8",
            stderr=subprocess.PIPE,
        )
        if Path(top.strip()).resolve() != root.resolve():
            return ["Privacy guard must inspect the repository root"]
        names = subprocess.check_output(
            ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"], cwd=root
        )
        return tracked_errors(names.decode("utf-8").split("\0")[:-1])
    except (OSError, subprocess.CalledProcessError, UnicodeError):
        return ["Cannot inspect repository privacy scope"]


def public_member_errors(name, prefix):
    """Inspect a relative POSIX member name and bounded uncompressed bytes."""
    path = PurePosixPath(name)
    parts = {part.lower() for part in path.parts[:-1]}
    if (
        path.suffix.lower() in MEDIA
        or parts & DEVICE_PARTS
        or path.name.lower() in {"run.json", "capture.json", "logcat.txt", "device.json"}
        or path.name.lower().startswith(("window-", "display-", "device-", "app-before"))
    ):
        return [f"Private/device or nested binary evidence rejected: {name}"]
    if prefix.startswith(
        (b"\x89PNG", b"\xff\xd8\xff", b"GIF8", b"PK\x03\x04", b"7z\xbc\xaf\x27\x1c")
    ) or (prefix[:4] == b"RIFF" and prefix[8:12] == b"WEBP"):
        return [f"Capture/archive signature rejected: {name}"]
    return []


def public_errors(folder):
    errors = []
    if (
        not folder.is_dir()
        or folder.is_symlink()
        or getattr(folder, "is_junction", lambda: False)()
    ):
        return ["Public evidence input must be a real current-run directory"]
    count = 0
    for path in sorted(folder.rglob("*")):
        if path.is_symlink() or getattr(path, "is_junction", lambda: False)():
            errors.append("Public evidence cannot contain links/junctions")
            continue
        if not path.is_file():
            continue
        count += 1
        with path.open("rb") as stream:
            prefix = stream.read(PUBLIC_PREFIX_BYTES)
        errors.extend(public_member_errors(path.relative_to(folder).as_posix(), prefix))
    if not count:
        errors.append("Public evidence input is empty")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence", type=Path)
    args = parser.parse_args()
    errors = public_errors(args.evidence) if args.evidence else repository_errors(ROOT)
    for error in errors:
        print(f"FAIL {error}")
    if not errors:
        print("PASS privacy path/signature guard (not semantic content approval)")
    return int(bool(errors))


if __name__ == "__main__":
    raise SystemExit(main())
