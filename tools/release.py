"""Prepare, verify and publish signed DexDroid APK/AAB releases."""

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import zipfile
from pathlib import Path

import release_report

ROOT = Path(__file__).resolve().parents[1]


def run(argv, cwd=ROOT):
    result = subprocess.run(
        argv, cwd=cwd, capture_output=True, text=True, encoding="utf-8", errors="replace"
    )
    if result.returncode:
        raise RuntimeError(f"Command failed: {argv[0]}\n{result.stdout}{result.stderr}")
    return result.stdout


def app_version(root):
    text = (root / "app/build.gradle").read_text(encoding="utf-8")
    name = re.search(r"^\s*versionName\s+['\"]([^'\"]+)['\"]\s*$", text, re.M)
    code = re.search(r"^\s*versionCode\s+(\d+)\s*$", text, re.M)
    if not name or not code or int(code[1]) < 1:
        raise ValueError(
            "Expected literal versionName and positive versionCode in app/build.gradle"
        )
    return name[1], int(code[1])


def validate_tag(tag, version):
    if not re.fullmatch(r"v\d+\.\d+(?:\.\d+)?(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?", tag):
        raise ValueError("Use a version tag such as v0.10 or v0.10.1")
    if tag != f"v{version}":
        raise ValueError(f"Tag {tag} does not match app versionName {version}")


def change_summary(text, version):
    sections = re.split(r"(?m)^## ", text)
    matching = []
    for section in sections[1:]:
        title, _, body = section.partition("\n")
        if re.fullmatch(r"\[" + re.escape(version) + r"\](?: - \d{4}-\d{2}-\d{2})?", title.strip()):
            matching.append(body)
    if len(matching) != 1:
        raise ValueError(f"Expected exactly one CHANGELOG entry for {version}")
    bullets = []
    for line in matching[0].splitlines():
        if line.startswith("- "):
            bullets.append(line[2:].strip())
        elif line.startswith("  ") and bullets:
            bullets[-1] += " " + line.strip()
    if not bullets or not all(bullets):
        raise ValueError("Release changelog entry needs at least one change bullet")
    return bullets[:5]


def release_notes(root, version, repository, commit):
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Expected owner/repository")
    if not re.fullmatch(r"[a-f0-9]{40}", commit):
        raise ValueError("Expected a full source commit SHA")
    bullets = change_summary((root / "CHANGELOG.md").read_text(encoding="utf-8"), version)
    base = f"https://github.com/{repository}/blob/{commit}"
    notes = "## What's changed\n\n" + "\n".join(f"- {line}" for line in bullets)
    anchor = version.replace(".", "").lower()
    notes += f"\n\n[Full changelog]({base}/CHANGELOG.md#{anchor})\n"
    notes += (
        f"\n[Release report](https://github.com/{repository}/releases/download/"
        f"v{version}/release-report.md) - requirement coverage, host pass rates and unrun checks.\n"
    )
    notes += "\n**Downloads:** APK for installation; AAB for distribution tooling.\n"
    if (root / "testresults" / version / "README.md").is_file():
        notes += f"\n[Version evidence and limitations]({base}/testresults/{version}/README.md). "
        notes += "This workflow verifies host checks and artifact signatures; it does not rerun device tests.\n"
    return notes


def write_json(path, data):
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def prepare(root, output, tag, repository):
    version, code = app_version(root)
    validate_tag(tag, version)
    commit = run(["git", "rev-parse", "HEAD"], root).strip()
    tagged = run(["git", "rev-parse", "--verify", f"refs/tags/{tag}^{{commit}}"], root).strip()
    if commit != tagged:
        raise ValueError("Checked-out source does not match the requested tag")
    notes = release_notes(root, version, repository, commit)
    output.mkdir(parents=True, exist_ok=True)
    metadata = {
        "tag": tag,
        "version": version,
        "version_code": code,
        "repository": repository,
        "source_commit": commit,
        "prerelease": "-" in version,
        "verified": False,
    }
    write_json(output / "build.json", metadata)
    (output / "release-notes.md").write_bytes(notes.encode("utf-8"))
    if os.environ.get("GITHUB_OUTPUT"):
        with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as stream:
            stream.write(
                f"tag={tag}\nversion={version}\nprerelease={str(metadata['prerelease']).lower()}\n"
            )
    print(f"Prepared {tag} from {commit}")
    return metadata


def sdk_tool(name):
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk and os.name == "nt":
        sdk = str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk")
    suffix = ".bat" if name == "apksigner" else ".exe"
    path = Path(sdk or "") / "build-tools/35.0.0" / (name + (suffix if os.name == "nt" else ""))
    if not path.is_file():
        raise ValueError(f"Missing Android build tool: {name}; set ANDROID_HOME")
    return str(path)


def jdk_tool(name):
    if os.environ.get("JAVA_HOME"):
        path = Path(os.environ["JAVA_HOME"]) / "bin" / (name + (".exe" if os.name == "nt" else ""))
        if path.is_file():
            return str(path)
    found = shutil.which(name)
    if not found:
        raise ValueError(f"Missing JDK tool: {name}")
    return found


def verify_binaries(apk, aab, version, version_code):
    with zipfile.ZipFile(apk) as archive:
        if (
            "AndroidManifest.xml" not in archive.namelist()
            or "classes.dex" not in archive.namelist()
        ):
            raise ValueError("APK is missing application content")
    with zipfile.ZipFile(aab) as archive:
        names = archive.namelist()
        if "base/manifest/AndroidManifest.xml" not in names or "base/dex/classes.dex" not in names:
            raise ValueError("AAB is missing application content")
        if not any(name.startswith("META-INF/") and name.endswith(".SF") for name in names):
            raise ValueError("AAB is unsigned")
    badging = run([sdk_tool("aapt"), "dump", "badging", str(apk)])
    package = re.search(
        r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging
    )
    if not package or package.groups() != ("dev.dexdroid", str(version_code), version):
        raise ValueError("Built APK package/version does not match release metadata")
    if "application-debuggable" in badging:
        raise ValueError("Refusing a debuggable release APK")
    apk_check = run([sdk_tool("apksigner"), "verify", "--verbose", "--print-certs", str(apk)])
    fingerprint = re.search(r"Signer #1 certificate SHA-256 digest:\s*([a-fA-F0-9]{64})", apk_check)
    if not fingerprint:
        raise ValueError("APK signing certificate was not verified")
    aab_check = run([jdk_tool("jarsigner"), "-verify", str(aab)])
    if "jar verified." not in aab_check:
        raise ValueError("AAB signature verification failed")
    certificate = run([jdk_tool("keytool"), "-printcert", "-jarfile", str(aab)])
    aab_fingerprint = re.search(r"SHA256:\s*([A-Fa-f0-9:]+)", certificate)
    if not aab_fingerprint or aab_fingerprint[1].replace(":", "").lower() != fingerprint[1].lower():
        raise ValueError("APK and AAB signing certificates differ")
    return fingerprint[1].lower()


def package(root, output):
    metadata = json.loads((output / "build.json").read_text(encoding="utf-8"))
    apk = root / "app/build/outputs/apk/release/app-release.apk"
    aab = root / "app/build/outputs/bundle/release/app-release.aab"
    fingerprint = verify_binaries(apk, aab, metadata["version"], metadata["version_code"])
    names = []
    for source, extension in [(apk, "apk"), (aab, "aab")]:
        name = f"DexDroid-{metadata['version']}.{extension}"
        shutil.copyfile(source, output / name)
        names.append(name)
    metadata.update(
        verified=True,
        signing_certificate_sha256=fingerprint,
        files={name: hashlib.sha256((output / name).read_bytes()).hexdigest() for name in names},
    )
    write_json(output / "build.json", metadata)
    checksums = "".join(
        f"{hashlib.sha256((output / name).read_bytes()).hexdigest()}  {name}\n"
        for name in [*names, "build.json"]
    )
    (output / "SHA256SUMS.txt").write_bytes(checksums.encode("utf-8"))
    print(f"Verified and packaged {metadata['tag']} APK/AAB with certificate {fingerprint}")


def publish(output):
    metadata = json.loads((output / "build.json").read_text(encoding="utf-8"))
    if metadata.get("verified") is not True:
        raise ValueError("Only verified packages can be published")
    validate_tag(metadata["tag"], metadata["version"])
    expected = {
        f"DexDroid-{metadata['version']}.apk",
        f"DexDroid-{metadata['version']}.aab",
        "build.json",
        *release_report.FILES,
    }
    seen = set()
    for line in (output / "SHA256SUMS.txt").read_text(encoding="utf-8").splitlines():
        digest, name = line.split("  ", 1)
        if name not in expected or name in seen or not re.fullmatch(r"[a-f0-9]{64}", digest):
            raise ValueError("Invalid release checksum manifest")
        seen.add(name)
        path = (output / name).resolve()
        if (
            not path.is_relative_to(output.resolve())
            or hashlib.sha256(path.read_bytes()).hexdigest() != digest
        ):
            raise ValueError("Release package checksum mismatch")
    if seen != expected:
        raise ValueError("Release checksum manifest is incomplete")
    release_report.validate_package(output, metadata)
    repository, tag = metadata["repository"], metadata["tag"]
    lookup = subprocess.run(
        ["gh", "api", f"repos/{repository}/releases?per_page=100", "--paginate", "--slurp"],
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    existing = None
    if lookup.returncode:
        raise RuntimeError(f"Cannot inspect releases: {lookup.stderr}")
    matching = [
        item for page in json.loads(lookup.stdout) for item in page if item.get("tag_name") == tag
    ]
    if len(matching) > 1:
        raise ValueError("Multiple releases use this tag; resolve the ambiguity before publishing")
    if matching:
        existing = matching[0]
        if existing.get("draft") is not True:
            raise ValueError(
                "Release is already published; bump version instead of overwriting assets"
            )
    assets = [
        str(output / f"DexDroid-{metadata['version']}.{extension}") for extension in ["apk", "aab"]
    ]
    assets += [
        str(output / name) for name in ("SHA256SUMS.txt", "build.json", *release_report.FILES)
    ]
    common = ["--repo", repository]
    if existing:
        run(["gh", "release", "upload", tag, *assets, "--clobber", *common])
        run(
            [
                "gh",
                "release",
                "edit",
                tag,
                "--notes-file",
                str(output / "release-notes.md"),
                *common,
            ]
        )
    else:
        command = [
            "gh",
            "release",
            "create",
            tag,
            *assets,
            "--draft",
            "--verify-tag",
            "--title",
            f"DexDroid {metadata['version']}",
            "--notes-file",
            str(output / "release-notes.md"),
            *common,
        ]
        if metadata["prerelease"]:
            command.append("--prerelease")
        run(command)
    run(
        [
            "gh",
            "release",
            "edit",
            tag,
            "--draft=false",
            f"--prerelease={str(metadata['prerelease']).lower()}",
            *common,
        ]
    )
    print(f"Published https://github.com/{repository}/releases/tag/{tag}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["prepare", "package", "publish"])
    parser.add_argument("--tag")
    parser.add_argument(
        "--repository", default=os.environ.get("GITHUB_REPOSITORY", "PhiLong-Ho/DexDroid")
    )
    parser.add_argument("--output", type=Path, default=ROOT / "artifacts/release")
    args = parser.parse_args()
    try:
        if args.action == "prepare":
            if not args.tag:
                parser.error("prepare requires --tag")
            prepare(ROOT, args.output, args.tag, args.repository)
        elif args.action == "package":
            package(ROOT, args.output)
        else:
            publish(args.output)
    except (ValueError, OSError, RuntimeError, zipfile.BadZipFile) as error:
        print(f"FAIL {error}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
