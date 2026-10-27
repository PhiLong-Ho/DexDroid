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

import app_metadata
import run_monitor

ROOT = Path(__file__).resolve().parents[1]
RUNNER = "dev.dexdroid.test/dev.dexdroid.DeviceChecks"
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
TEST_APK = ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
SCREEN_DIRS = {
    "capabilities": ["capabilities-acceptance"],
    "apps-smoke": ["launch-feedback-real-apps"],
    "feedback": ["launch-feedback-acceptance"],
    "audio": ["audio-acceptance"],
    "debugging": ["debugging-off-acceptance"],
    "diagnostics": ["diagnostics-acceptance"],
    "workspace": ["workspace-shortcut-acceptance"],
    "split": ["split-acceptance"],
    "split-navigation": ["split-acceptance"],
    "ui": ["submenu-acceptance"],
    "monitor": ["menu-text-acceptance"],
    "icon": ["icon-acceptance"],
    "profiles": ["monitor-profile-acceptance"],
    "idle": ["compatibility-acceptance"],
    "child": ["child-launch-acceptance"],
    "catalog": ["catalog-acceptance"],
    "protected": ["protection-acceptance"],
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


def command(argv, timeout=60, *, monitor=None, label="command"):
    if monitor is not None:
        result = monitor.run(
            argv, cwd=ROOT, label=label, timeout=timeout, extractor=run_monitor.AssertionProgress()
        )
        stdout, stderr = result.stdout_path.read_bytes(), result.stderr_path.read_bytes()
        if result.state == "timeout":
            raise subprocess.TimeoutExpired(argv, timeout, output=stdout, stderr=stderr)
        if result.state == "cancelled":
            raise KeyboardInterrupt
        return subprocess.CompletedProcess(
            argv,
            result.returncode,
            stdout.decode("utf-8", errors="replace"),
            stderr.decode("utf-8", errors="replace"),
        )
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
        try:
            override = app_metadata.app_version(root)[0]
        except (OSError, ValueError) as error:
            raise RuntimeError("Cannot read literal app version metadata; use --version") from error
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
            "audio",
            "debugging",
            "diagnostics",
            "workspace",
            "feedback",
            "audio-capability",
            "apps-smoke",
            "capabilities",
            "input",
            "legacy-navigation",
            "split-navigation",
            "ui",
            "monitor",
            "keyboard",
            "idle",
            "icon",
            "profiles",
            "controller",
            "child",
            "catalog",
            "protected",
            "split",
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
        [
            "input",
            "ui",
            "monitor",
            "keyboard",
            "idle",
            "icon",
            "profiles",
            "child",
            "catalog",
            "protected",
            "split",
        ]
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
    monitor = run_monitor.Session(
        out / "supervisor",
        context={
            "source_commit": metadata["git_commit"],
            "runner_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
            "supervisor_sha256": hashlib.sha256(
                Path(run_monitor.__file__).read_bytes()
            ).hexdigest(),
            "scope": "Android runner; legacy suites may buffer assertion output",
        },
    )
    print(f'Monitor: python tools/run_monitor.py watch "{monitor.folder}"', flush=True)
    failed = False
    cancelled = False
    try:
        monitor.phase("device_preflight")
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
                monitor.phase("installing_apk")
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
        with monitor.lock:
            monitor.state["context"]["apk_hashes"] = metadata["apk_hashes"]
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
            metadata["results"].append(status)
            metadata["current_suite"] = name
            write_json(out / "run.json", metadata)
            print(f"Running {name}; evidence: {folder}", flush=True)
            try:
                run = command(argv, timeout=args.timeout, monitor=monitor, label=name)
                output = run.stdout + run.stderr
                passed = instrumentation_passed(output, run.returncode)
                status.update(
                    result="passed" if passed else "failed",
                    exit_code=run.returncode,
                    pass_lines=len(re.findall(r"(?:^|stream=)PASS ", output, re.M)),
                )
            except subprocess.TimeoutExpired as error:
                streams = [error.stdout or b"", error.stderr or b""]
                output = "".join(
                    s.decode("utf-8", errors="replace") if isinstance(s, bytes) else s
                    for s in streams
                )
                output += "\nFAIL instrumentation timed out\n"
                passed = False
                status["result"] = "timeout"
                command(prefix + ["shell", "am", "force-stop", "dev.dexdroid"], timeout=30)
            except KeyboardInterrupt:
                failed = cancelled = True
                passed = False
                status["result"] = "cancelled"
                combined = monitor.state.get("combined_path")
                output = (
                    Path(combined).read_text(encoding="utf-8", errors="replace") if combined else ""
                )
                command(prefix + ["shell", "am", "force-stop", "dev.dexdroid"], timeout=30)
            (folder / "instrumentation.txt").write_text(output, encoding="utf-8")
            status["duration_seconds"] = round(time.monotonic() - started, 3)
            if cancelled:
                break
            monitor.phase("collecting_logcat")
            logs = command(prefix + ["logcat", "-d", "-v", "threadtime", "-t", "2500"])
            (folder / "logcat.txt").write_text(logs.stdout + logs.stderr, encoding="utf-8")
            status["screenshots"] = []
            monitor.phase("collecting_screenshots")
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
            write_json(out / "run.json", metadata)
            if not passed:
                failed = True
                break
    except KeyboardInterrupt:
        failed = cancelled = True
        metadata["error"] = "Runner interrupted"
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        failed = True
        metadata["error"] = str(error)
        print(f"FAIL {error}", file=sys.stderr)
    finally:
        try:
            monitor.phase("restoring_controller")
        except OSError:
            failed = True
            monitor.error = "status_write_failed"
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
        except (OSError, subprocess.SubprocessError, KeyboardInterrupt) as error:
            metadata["restore_error"] = str(error) or type(error).__name__
            failed = True
            cancelled |= isinstance(error, KeyboardInterrupt)
        try:
            final = monitor.finish(
                "cancelled" if cancelled else "failed" if failed else "completed",
                restoration="failed" if "restore_error" in metadata else "completed",
            )
            failed |= final != "completed"
            if monitor.error:
                metadata["monitor_error"] = monitor.error
        except OSError:
            monitor.stop.set()
            failed = True
            metadata["monitor_error"] = "status_write_failed"
        metadata["result"] = "cancelled" if cancelled else "failed" if failed else "passed"
        metadata["finished_utc"] = datetime.now(timezone.utc).isoformat()
        write_json(out / "run.json", metadata)
    print(f"{metadata['result'].upper()}: {out}")
    return 130 if cancelled else int(failed)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        print(f"FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
