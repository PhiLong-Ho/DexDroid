"""Check the real debug shell snapshot process without Chrome or input injection."""

import argparse
import hashlib
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

from run_device_checks import APK, ROOT, adb_path, result_version, select_device


def validate_snapshot(data):
    if not data or len(data) > 8 * 1024 * 1024:
        raise RuntimeError("Missing or oversized shell snapshot")
    try:
        tree = ET.fromstring(data)
    except ET.ParseError as error:
        raise RuntimeError("Malformed shell snapshot XML") from error
    windows = list(tree)
    nodes = []
    valid_structure = True
    for window in windows:
        roots = list(window)
        if window.tag != "window" or len(roots) != 1 or roots[0].tag != "node":
            valid_structure = False
            break
        pending = [(roots[0], 0)]
        while pending:
            node, depth = pending.pop()
            if node.tag != "node" or depth > 64 or len(nodes) >= 10000:
                valid_structure = False
                break
            nodes.append(node)
            pending.extend((child, depth + 1) for child in node)
        if not valid_structure:
            break
    if (
        tree.tag != "hierarchy"
        or tree.get("display-id") != "0"
        or not valid_structure
        or not 1 <= len(windows) <= 64
        or not 1 <= len(nodes) <= 10000
        or not any(node.get("package") == "dev.dexdroid" for node in nodes)
    ):
        raise RuntimeError("Snapshot must contain the display-0 controller")
    return {"window_count": len(windows), "node_count": len(nodes)}


def byte_output(value):
    return value.encode("utf-8") if isinstance(value, str) else value or b""


def observe_snapshot(prefix, remote_apk, folder, *, run=subprocess.run):
    """One raw-process attempt; preserve partial output and independent diagnostics."""
    argv = prefix + [
        "shell",
        "env",
        "CLASSPATH=" + remote_apk,
        "app_process",
        "/system/bin",
        "dev.dexdroid.ShellUiDump",
    ]
    record = {
        "command": argv,
        "result": "failed",
        "diagnostics": [],
        "evidence_errors": [],
        "files_sha256": {},
    }

    def save(name, data):
        try:
            path = folder / name
            path.write_bytes(data)
            actual = path.read_bytes()
            if actual != data:
                raise OSError("Saved evidence differs from captured bytes")
            record["files_sha256"][name] = hashlib.sha256(actual).hexdigest()
        except OSError as error:
            record["evidence_errors"].append({"path": name, "error": str(error)})

    started = time.monotonic()
    stdout, stderr = b"", b""
    try:
        result = run(argv, capture_output=True, timeout=25)
        stdout, stderr = byte_output(result.stdout), byte_output(result.stderr)
        record["exit_code"] = result.returncode
        if result.returncode:
            raise RuntimeError("Shell snapshot process failed")
        record.update(validate_snapshot(stdout), result="passed")
    except subprocess.TimeoutExpired as error:
        stdout, stderr = byte_output(error.stdout), byte_output(error.stderr)
        record.update(result="timeout", error="Shell snapshot process timed out")
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        record["error"] = str(error)
    finally:
        record["duration_seconds"] = round(time.monotonic() - started, 3)
        save("stdout.xml", stdout)
        save("stderr.txt", stderr)
        for name, args in [
            ("logcat.txt", ["logcat", "-d", "-b", "all", "-v", "threadtime", "-t", "6000"]),
            ("window.txt", ["shell", "dumpsys", "window"]),
            ("processes.txt", ["shell", "ps", "-A", "-o", "UID,PID,NAME,ARGS"]),
        ]:
            entry = {"path": name, "command": prefix + args}
            try:
                result = run(prefix + args, capture_output=True, timeout=20)
                save(name, byte_output(result.stdout) + byte_output(result.stderr))
                entry["exit_code"] = result.returncode
            except (OSError, subprocess.SubprocessError) as error:
                entry["error"] = str(error)
            record["diagnostics"].append(entry)
        # This final record cannot hash itself. Return its write failure even when
        # the destination refuses the record, so the caller cannot report success.
        try:
            (folder / "observation.json").write_text(
                json.dumps(record, indent=2) + "\n", encoding="utf-8"
            )
        except OSError as error:
            record["evidence_errors"].append({"path": "observation.json", "error": str(error)})
    return record


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", type=Path, default=APK)
    parser.add_argument("--version", help="Safe evidence label; defaults to app version")
    args = parser.parse_args(argv)
    # Invalid metadata/labels or a missing local APK cannot create output or touch ADB.
    version = result_version(ROOT, args.version)
    if not args.apk.is_file():
        raise RuntimeError("Build or select the expected debug APK first")
    expected = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    adb = adb_path(None)
    prefix = [adb, "-s", args.serial]
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    folder = ROOT / "testresults" / version / "runs" / (stamp + "-shell-snapshot")
    folder.mkdir(parents=True)
    meta = {
        "scope": "Standalone debug shell observer only; no input, install, reset or Chrome Stop coverage.",
        "started_utc": stamp,
        "device": args.serial,
        "expected_apk_sha256": expected,
        "runner_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "result": "failed",
    }
    try:

        def command(arguments):
            result = subprocess.run(arguments, capture_output=True, timeout=20)
            if result.returncode:
                raise RuntimeError(
                    "ADB preflight failed: "
                    + byte_output(result.stderr).decode("utf-8", errors="replace")
                )
            return result.stdout.decode("utf-8")

        select_device(command([adb, "devices"]), args.serial)
        paths = command(prefix + ["shell", "pm", "path", "dev.dexdroid"]).splitlines()
        if len(paths) != 1 or not paths[0].startswith("package:"):
            raise RuntimeError("Expected one installed controller APK")
        remote = paths[0].removeprefix("package:").strip()
        if not re.fullmatch(r"/data/app/[A-Za-z0-9_./+=~-]+/base.apk", remote):
            raise RuntimeError("Unexpected installed APK path")
        actual = command(prefix + ["shell", "sha256sum", remote]).split()
        if not actual or actual[0] != expected:
            raise RuntimeError("Installed APK differs from the selected debug APK")
        if command(prefix + ["shell", "id", "-u"]).strip() != "2000":
            raise RuntimeError("Snapshot requires authorized shell UID 2000")
        meta["installed_apk_sha256"] = actual[0]
        observation = observe_snapshot(prefix, remote, folder)
        meta["result"] = observation["result"]
        meta["observation_result"] = observation["result"]
        if any(item.get("exit_code") != 0 for item in observation["diagnostics"]):
            meta["evidence_error"] = "One or more diagnostic captures are unavailable"
            meta["result"] = "failed"
        if observation.get("evidence_errors"):
            meta["evidence_errors"] = observation["evidence_errors"]
            meta["evidence_error"] = "One or more evidence files could not be retained"
            meta["result"] = "failed"
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        meta["error"] = str(error)
    finally:
        meta["finished_utc"] = datetime.now(timezone.utc).isoformat()
        (folder / "run.json").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    print(meta["result"].upper() + ": " + str(folder))
    return int(meta["result"] != "passed" or "evidence_error" in meta)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, ValueError, OSError, subprocess.SubprocessError) as error:
        print("FAIL " + str(error), file=sys.stderr)
        raise SystemExit(1)
