"""Prepare, verify and publish signed DexDroid APK/AAB releases."""

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

import app_metadata
import check_privacy
import evidence as evidence_archive
import release_report

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE_FILES = ("release-evidence.zip", "release-evidence.json")


def run(argv, cwd=ROOT):
    result = subprocess.run(
        argv, cwd=cwd, capture_output=True, text=True, encoding="utf-8", errors="replace"
    )
    if result.returncode:
        raise RuntimeError(f"Command failed: {argv[0]}\n{result.stdout}{result.stderr}")
    return result.stdout


def app_version(root):
    return app_metadata.app_version(root)


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
    notes += f"\n[Raw release evidence](https://github.com/{repository}/releases/download/v{version}/release-evidence.zip) with per-file hashes; scope is recorded in the release report.\n"
    if (root / "testresults" / version / "README.md").is_file():
        notes += f"\n[Version evidence and limitations]({base}/testresults/{version}/README.md). "
        notes += "This workflow verifies host checks and artifact signatures; it does not rerun device tests.\n"
    elif (root / "testresults/manifest.json").is_file():
        manifest = json.loads((root / "testresults/manifest.json").read_text(encoding="utf-8"))
        if manifest.get("withdrawal", {}).get("status") == "privacy-withdrawn":
            notes += f"\n[Evidence withdrawn for privacy]({base}/testresults/README.md). "
            notes += "Historical raw evidence is unavailable; this workflow does not rerun device tests.\n"
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


def development_classes():
    """Source-set identities, including test fixtures; nested classes inherit the boundary."""
    from kotlin_source import development_identities

    identities = set()
    for variant in ("debug", "androidTest"):
        for language in ("java", "kotlin"):
            source = ROOT / "app/src" / variant / language
            for path in source.rglob("*.java"):
                identities.add(".".join(path.relative_to(source).with_suffix("").parts))
            for path in source.rglob("*.kt"):
                identities.update(development_identities(path))
    if not identities:
        raise ValueError("No development source identities available for production exclusion")
    return identities


def verify_production_content(artifact, bundle=False):
    """Reject diagnostic declarations and defined classes, not harmless parser literals.

    SDK dexdump reads class definitions per https://source.android.com/docs/core/runtime/dex-format.
    Manifest identifier scanning covers both binary XML string pools and AAB protobuf UTF-8;
    it conservatively rejects any development class identifier in either manifest representation.
    """
    forbidden = development_classes()
    with zipfile.ZipFile(artifact) as archive, tempfile.TemporaryDirectory() as temporary:
        manifests = [
            name
            for name in archive.namelist()
            if re.fullmatch(
                r"[^/]+/manifest/AndroidManifest\.xml" if bundle else r"AndroidManifest\.xml", name
            )
        ]
        dex_files = [
            name
            for name in archive.namelist()
            if re.fullmatch(r"[^/]+/dex/classes\d*\.dex" if bundle else r"classes\d*\.dex", name)
        ]
        if not manifests or not dex_files:
            raise ValueError("Missing production manifest or DEX content")
        for name in manifests:
            raw = archive.read(name)
            for component in forbidden:
                short = component.rsplit(".", 1)[-1]
                if short.encode("utf-8") in raw or short.encode("utf-16le") in raw:
                    raise ValueError(f"Development component in production manifest: {component}")
        for index, name in enumerate(dex_files):
            path = Path(temporary) / f"classes-{index}.dex"
            path.write_bytes(archive.read(name))
            output = run([sdk_tool("dexdump"), "-f", str(path)])
            defined = re.findall(r"Class descriptor\s*:\s*'L([^']+);'", output)
            if not defined:
                raise ValueError("No class definitions in production DEX inspection")
            for descriptor in defined:
                identity = descriptor.replace("/", ".")
                if any(identity == item or identity.startswith(item + "$") for item in forbidden):
                    raise ValueError(f"Development class in production DEX: {identity}")


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
    verify_production_content(apk)
    verify_production_content(aab, bundle=True)
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


def verify_checksums(output, expected):
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


def asset_names(metadata):
    return {f"DexDroid-{metadata['version']}.{ext}" for ext in ("apk", "aab")} | {
        "build.json",
        *release_report.FILES,
    }


def validate_evidence(output, metadata):
    descriptor = json.loads((output / EVIDENCE_FILES[1]).read_text(encoding="utf-8"))
    manifest = evidence_archive.verify_archive(output / EVIDENCE_FILES[0], descriptor)
    for key in ("source_commit", "version", "version_code", "tag", "repository"):
        if manifest["context"].get(key) != metadata[key]:
            raise ValueError(f"Release evidence {key} mismatch")
    records = {item["path"]: item for item in manifest["files"]}
    required = {
        "source-commit.txt",
        "host/quality.txt",
        "host/tool-tests.json",
        *release_report.FILES,
    }
    if not required <= records.keys():
        raise ValueError("Release evidence is missing required host files")
    report = json.loads((output / "release-report.json").read_text(encoding="utf-8"))
    if manifest["context"].get("workflow_url") != report["workflow_url"]:
        raise ValueError("Release evidence workflow mismatch")
    for suite, prefix in [("tooling", "host/"), ("jvm", "host/test-results/testDebugUnitTest/")]:
        for source in report["tests"][suite]["sources"]:
            name = evidence_archive.safe_name(source["name"])
            if records.get(prefix + name, {}).get("sha256") != source["sha256"]:
                raise ValueError("Release evidence test source mismatch")
    with zipfile.ZipFile(output / EVIDENCE_FILES[0]) as bundle:
        for name in records:
            with bundle.open("data/" + name) as stream:
                errors = check_privacy.public_member_errors(
                    name, stream.read(check_privacy.PUBLIC_PREFIX_BYTES)
                )
            if errors:
                raise ValueError("Public release evidence rejected: " + "; ".join(errors))
        for name in release_report.FILES:
            if bundle.read("data/" + name) != (output / name).read_bytes():
                raise ValueError("Archived release report differs from published report")
        if (
            bundle.read("data/source-commit.txt").decode("utf-8").strip()
            != metadata["source_commit"]
        ):
            raise ValueError("Archived source commit mismatch")


def archive_release(root, output, source):
    errors = check_privacy.public_errors(source)
    if errors:
        raise ValueError("Public release evidence rejected: " + "; ".join(errors))
    metadata = json.loads((output / "build.json").read_text(encoding="utf-8"))
    if metadata.get("verified") is not True:
        raise ValueError("Archive requires verified release artifacts")
    if run(["git", "rev-parse", "HEAD"], root).strip() != metadata["source_commit"]:
        raise ValueError("Archive source commit mismatch")
    verify_checksums(output, asset_names(metadata))
    release_report.validate_package(output, metadata)
    report = json.loads((output / "release-report.json").read_text(encoding="utf-8"))
    context = {
        key: metadata[key]
        for key in ("source_commit", "version", "version_code", "tag", "repository")
    }
    context.update(
        scope="Current release host checks only; device/visual/performance are not rerun.",
        workflow_url=report["workflow_url"],
    )
    destination = output.parent / "release-evidence"
    descriptor = evidence_archive.create_archive(source, destination, context)
    if any((output / name).exists() for name in EVIDENCE_FILES):
        raise ValueError("Release evidence assets already exist")
    descriptor["archive"] = EVIDENCE_FILES[0]
    descriptor["storage"] = {
        "kind": "github-release-target",
        "url": f"https://github.com/{metadata['repository']}/releases/download/{metadata['tag']}/{EVIDENCE_FILES[0]}",
        "availability": "Only available after successful authorized publication",
    }
    shutil.copyfile(destination / "evidence.zip", output / EVIDENCE_FILES[0])
    write_json(output / EVIDENCE_FILES[1], descriptor)
    validate_evidence(output, metadata)
    names = asset_names(metadata) | set(EVIDENCE_FILES)
    (output / "SHA256SUMS.txt").write_text(
        "".join(
            f"{evidence_archive.digest_file(output / name)}  {name}\n" for name in sorted(names)
        ),
        encoding="utf-8",
        newline="\n",
    )
    print("PASS release evidence sealed and included in publication checksums")


def publish(output):
    metadata = json.loads((output / "build.json").read_text(encoding="utf-8"))
    if metadata.get("verified") is not True:
        raise ValueError("Only verified packages can be published")
    validate_tag(metadata["tag"], metadata["version"])
    verify_checksums(output, asset_names(metadata) | set(EVIDENCE_FILES))
    release_report.validate_package(output, metadata)
    validate_evidence(output, metadata)
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
        str(output / name)
        for name in ("SHA256SUMS.txt", "build.json", *release_report.FILES, *EVIDENCE_FILES)
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
    parser.add_argument("action", choices=["prepare", "package", "archive", "publish"])
    parser.add_argument("--tag")
    parser.add_argument(
        "--repository", default=os.environ.get("GITHUB_REPOSITORY", "PhiLong-Ho/DexDroid")
    )
    parser.add_argument("--output", type=Path, default=ROOT / "artifacts/release")
    parser.add_argument("--evidence", type=Path)
    args = parser.parse_args()
    try:
        if args.action == "prepare":
            if not args.tag:
                parser.error("prepare requires --tag")
            prepare(ROOT, args.output, args.tag, args.repository)
        elif args.action == "package":
            package(ROOT, args.output)
        elif args.action == "archive":
            if not args.evidence:
                parser.error("archive requires --evidence")
            archive_release(ROOT, args.output, args.evidence)
        else:
            publish(args.output)
    except (ValueError, OSError, RuntimeError, KeyError, zipfile.BadZipFile) as error:
        print(f"FAIL {error}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
