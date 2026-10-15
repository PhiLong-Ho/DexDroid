"""Run DexDroid instrumentation sequentially and retain auditable evidence."""

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNNER = "dev.dexdroid.test/dev.dexdroid.DeviceChecks"
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
TEST_APK = ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
SCREEN_DIRS = {
    "ui": ["submenu-acceptance"],
    "monitor": ["menu-text-acceptance"],
    "icon": ["icon-acceptance"],
    "profiles": ["monitor-profile-acceptance"],
}


def instrumentation_passed(output, exit_code):
    return (
        exit_code == 0
        and re.search(r"^INSTRUMENTATION_CODE:\s*-1\s*$", output, re.M) is not None
        and re.search(r"(?:^|stream=)PASS ", output, re.M) is not None
        and re.search(
            r"\bFAIL(?:ED|URE)?\b|INSTRUMENTATION_(?:FAILED|ABORTED)|Process crashed", output, re.I
        )
        is None
    )


def command(argv, timeout=60):
    return subprocess.run(
        argv,
        cwd=ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout,
    )


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")


def adb_path(explicit):
    if explicit:
        return explicit
    found = shutil.which("adb")
    if found:
        return found
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk and os.name == "nt":
        sdk = str(Path(os.environ.get("LOCALAPPDATA", "")) / "Android/Sdk")
    candidate = Path(sdk or "") / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
    if candidate.is_file():
        return str(candidate)
    raise RuntimeError("ADB unavailable; set ANDROID_HOME, PATH, or --adb")


def select_device(output, serial):
    rows = [line.split()[:2] for line in output.splitlines()[1:] if line.strip()]
    ready = [row[0] for row in rows if len(row) == 2 and row[1] == "device"]
    if serial:
        if serial not in ready:
            raise RuntimeError(f"Selected device is not connected/authorized: {serial}")
        return serial
    if len(ready) != 1:
        raise RuntimeError(
            "Expected exactly one authorized device; use --serial after adb devices -l"
        )
    return ready[0]


def result_version(root, override=None):
    if override is None:
        source = (root / "app/build.gradle").read_text(encoding="utf-8")
        match = re.search(r"^\s*versionName\s+['\"]([^'\"]+)['\"]\s*$", source, re.M)
        if not match:
            raise RuntimeError("Cannot read literal app versionName; use --version")
        override = match[1]
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+-]*", override):
        raise RuntimeError("Version must be a safe directory label, for example 0.10")
    return override


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial")
    parser.add_argument("--adb")
    parser.add_argument("--version", help="Evidence version; defaults to app versionName")
    parser.add_argument(
        "--suite",
        choices=[
            "all",
            "input",
            "ui",
            "monitor",
            "keyboard",
            "idle",
            "icon",
            "profiles",
            "controller",
        ],
        default="ui",
    )
    parser.add_argument("--install", action="store_true", help="Install both built APKs first")
    parser.add_argument("--timeout", type=int, default=900, help="Seconds per suite")
    args = parser.parse_args()
    if args.timeout <= 0:
        parser.error("--timeout must be positive")
    version = result_version(ROOT, args.version)
    adb = adb_path(args.adb)
    serial = select_device(command([adb, "devices"]).stdout, args.serial)
    prefix = [adb, "-s", serial]
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    out = ROOT / "testresults" / version / "runs" / stamp
    out.mkdir(parents=True)
    specs = json.loads((ROOT / "docs/requirements.json").read_text(encoding="utf-8"))["suites"]
    names = (
        ["input", "ui", "monitor", "keyboard", "idle", "icon", "profiles"]
        if args.suite == "all"
        else [args.suite]
    )
    metadata = {
        "started_utc": stamp,
        "app_version": version,
        "device": serial,
        "suites": names,
        "git_commit": command(["git", "rev-parse", "HEAD"]).stdout.strip(),
        "git_status": command(["git", "status", "--porcelain"]).stdout,
        "results": [],
        "visual_review": "unreviewed",
    }
    write_json(out / "run.json", metadata)
    failed = False
    try:
        for prop in ["ro.product.model", "ro.build.version.release", "ro.build.fingerprint"]:
            metadata[prop] = command(prefix + ["shell", "getprop", prop]).stdout.strip()
        for label, remote in [
            ("display", ["dumpsys", "display"]),
            ("window", ["dumpsys", "window"]),
            ("app", ["dumpsys", "package", "dev.dexdroid"]),
        ]:
            capture = command(prefix + ["shell"] + remote)
            (out / f"{label}-before.txt").write_text(
                capture.stdout + capture.stderr, encoding="utf-8"
            )
        metadata["apk_hashes"] = {}
        for apk, package in [(APK, "dev.dexdroid"), (TEST_APK, "dev.dexdroid.test")]:
            if not apk.is_file():
                raise RuntimeError(f"Build APK first: {apk}")
            local_hash = hashlib.sha256(apk.read_bytes()).hexdigest()
            metadata["apk_hashes"][package] = local_hash
            if args.install:
                install = command(prefix + ["install", "-r", str(apk)], timeout=240)
                (out / f"install-{package}.txt").write_text(
                    install.stdout + install.stderr, encoding="utf-8"
                )
                if install.returncode or "Success" not in install.stdout:
                    raise RuntimeError(f"Install failed for {package}")
            paths = command(prefix + ["shell", "pm", "path", package]).stdout.splitlines()
            if not paths or not paths[0].startswith("package:/"):
                raise RuntimeError(f"Missing installed {package}; rerun with --install")
            remote_path = paths[0].removeprefix("package:").strip()
            if not re.fullmatch(r"/[A-Za-z0-9_./+=~-]+", remote_path):
                raise RuntimeError("Unexpected installed APK path")
            actual_hash = command(prefix + ["shell", "sha256sum", remote_path]).stdout.split()
            if not actual_hash or actual_hash[0] != local_hash:
                raise RuntimeError(f"Installed {package} differs from build; rerun with --install")
        for name in names:
            folder = out / name
            folder.mkdir()
            # Only clear known test-owned PNG outputs, never application/user data.
            for directory in SCREEN_DIRS.get(name, []):
                command(
                    prefix
                    + [
                        "shell",
                        "rm",
                        "-f",
                        f"/sdcard/Android/data/dev.dexdroid/cache/{directory}/*.png",
                    ]
                )
            argv = prefix + ["shell", "am", "instrument", "-w", "-r"]
            flag = specs[name]["flag"]
            if flag:
                argv += ["-e", flag, "true"]
            argv.append(RUNNER)
            started = time.monotonic()
            status = {"suite": name, "command": argv, "result": "failed"}
            print(f"Running {name}; evidence: {folder}", flush=True)
            try:
                run = command(argv, timeout=args.timeout)
                output = run.stdout + run.stderr
                passed = instrumentation_passed(output, run.returncode)
                status.update(
                    result="passed" if passed else "failed",
                    exit_code=run.returncode,
                    pass_lines=len(re.findall(r"(?:^|stream=)PASS ", output, re.M)),
                )
            except subprocess.TimeoutExpired as error:
                output = error.stdout or b""
                if isinstance(output, bytes):
                    output = output.decode("utf-8", errors="replace")
                output += "\nFAIL instrumentation timed out\n"
                passed = False
                status["result"] = "timeout"
                command(prefix + ["shell", "am", "force-stop", "dev.dexdroid"], timeout=30)
            (folder / "instrumentation.txt").write_text(output, encoding="utf-8")
            status["duration_seconds"] = round(time.monotonic() - started, 3)
            logs = command(prefix + ["logcat", "-d", "-v", "threadtime", "-t", "2500"])
            (folder / "logcat.txt").write_text(logs.stdout + logs.stderr, encoding="utf-8")
            status["screenshots"] = []
            for directory in SCREEN_DIRS.get(name, []):
                pull = command(
                    prefix
                    + [
                        "pull",
                        f"/sdcard/Android/data/dev.dexdroid/cache/{directory}",
                        str(folder / directory),
                    ]
                )
                status["screenshots"].append({"folder": directory, "exit_code": pull.returncode})
                (folder / f"pull-{directory}.txt").write_text(
                    pull.stdout + pull.stderr, encoding="utf-8"
                )
            metadata["results"].append(status)
            write_json(out / "run.json", metadata)
            if not passed:
                failed = True
                break
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        failed = True
        metadata["error"] = str(error)
        print(f"FAIL {error}", file=sys.stderr)
    finally:
        try:
            restore = command(
                prefix
                + [
                    "shell",
                    "am",
                    "start",
                    "-W",
                    "--display",
                    "0",
                    "-n",
                    "dev.dexdroid/.MainActivity",
                ],
                timeout=30,
            )
            (out / "restore-controller.txt").write_text(
                restore.stdout + restore.stderr, encoding="utf-8"
            )
            if restore.returncode or "Error:" in restore.stdout + restore.stderr:
                metadata["restore_error"] = (
                    "MainActivity did not restore; inspect restore-controller.txt"
                )
                failed = True
        except (OSError, subprocess.SubprocessError) as error:
            metadata["restore_error"] = str(error)
            failed = True
        metadata["result"] = "failed" if failed else "passed"
        metadata["finished_utc"] = datetime.now(timezone.utc).isoformat()
        write_json(out / "run.json", metadata)
    print(f"{metadata['result'].upper()}: {out}")
    return int(failed)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        print(f"FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
