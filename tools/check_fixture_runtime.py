"""Check standalone Kotlin fixture APK definitions without launching a device."""

import argparse
import hashlib
import json
import re
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

import release

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNTIME = {
    "kotlin/jvm/internal/Intrinsics",
    "kotlin/Unit",
    "kotlin/jvm/functions/Function0",
    "kotlin/jvm/functions/Function1",
    "kotlin/collections/CollectionsKt",
    "kotlin/text/Regex",
}
ANDROID = "{http://schemas.android.com/apk/res/android}"


def fixture_components(root):
    manifest = root / "app/src/androidTest/AndroidManifest.xml"
    document = ET.parse(manifest).getroot()
    components = set()
    for node in document.iter():
        if node.tag in {"activity", "service", "receiver", "provider", "activity-alias"}:
            attribute = "targetActivity" if node.tag == "activity-alias" else "name"
            name = node.get(ANDROID + attribute)
            if not name:
                raise ValueError("Fixture component lacks a class name")
            if name.startswith("."):
                name = "dev.dexdroid.test" + name
            elif "." not in name:
                name = "dev.dexdroid.test." + name
            components.add(name.replace(".", "/"))
    if not components:
        raise ValueError("No standalone fixture components were declared")
    return components


def verify(apk, root=ROOT):
    required = RUNTIME | fixture_components(root) | {"dev/dexdroid/DeviceChecks"}
    definitions = set()
    with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory() as temporary:
        if "AndroidManifest.xml" not in archive.namelist():
            raise ValueError("Missing fixture APK manifest")
        manifest = archive.read("AndroidManifest.xml")
        if len(manifest) < 8 or manifest[:4] != b"\x03\x00\x08\x00":
            raise ValueError("Malformed fixture APK manifest header")
        dex_files = [n for n in archive.namelist() if re.fullmatch(r"classes\d*\.dex", n)]
        if not dex_files or len(dex_files) != len(set(dex_files)):
            raise ValueError("Missing or duplicate fixture DEX entries")
        for index, name in enumerate(dex_files):
            data = archive.read(name)
            if len(data) < 112 or not re.fullmatch(rb"dex\n0[0-9]{2}\x00", data[:8]):
                raise ValueError("Malformed fixture DEX header")
            path = Path(temporary) / (str(index) + ".dex")
            path.write_bytes(data)
            output = release.run([release.sdk_tool("dexdump"), "-f", str(path)])
            found = set(re.findall(r"Class descriptor\s*:\s*'L([^']+);'", output))
            if not found:
                raise ValueError("No class definitions in fixture DEX inspection")
            definitions.update(found)
    missing = sorted(required - definitions)
    if missing:
        raise ValueError(
            "Missing standalone fixture runtime/component definitions: " + ", ".join(missing)
        )
    return {
        "scope": "Packaged definition sentinel; native separate-UID launch remains required",
        "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "dex_files": len(dex_files),
        "defined_classes": len(definitions),
        "required_definitions": sorted(required),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=APK)
    args = parser.parse_args()
    print(json.dumps(verify(args.apk), indent=2))


if __name__ == "__main__":
    main()
