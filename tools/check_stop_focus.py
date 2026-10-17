"""Chrome single-view Stop regression without app instrumentation/BAL exemptions."""

import argparse
import hashlib
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

from run_device_checks import ROOT, adb_path, command, select_device

CONTROLLER = "dev.dexdroid/.MainActivity"
PACKAGE = "dev.dexdroid"


class ActionNotReady(RuntimeError):
    """The requested label has not appeared in a valid phone snapshot yet."""


def bounds_of(element):
    match = re.fullmatch(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", element.get("bounds", ""))
    if not match:
        raise RuntimeError("Missing or invalid phone UI bounds")
    bounds = tuple(map(int, match.groups()))
    if bounds[2] <= bounds[0] or bounds[3] <= bounds[1]:
        raise RuntimeError("Empty phone UI bounds")
    return bounds


def contains(bounds, point):
    return bounds[0] <= point[0] < bounds[2] and bounds[1] <= point[1] < bounds[3]


def phone_action(xml, label):
    """Select a unique, unobscured native action from a fresh display-0 snapshot."""
    root = ET.fromstring(xml)
    if root.tag != "hierarchy" or root.get("display-id") != "0":
        raise RuntimeError("Native action requires an explicitly identified phone hierarchy")
    windows = list(root)
    ids = [window.get("id") for window in windows]
    if not windows or any(window.tag != "window" for window in windows):
        raise RuntimeError("Phone windows unavailable")
    if None in ids or len(ids) != len(set(ids)):
        raise RuntimeError("Phone window identities missing or ambiguous")
    layers = {}
    for window in windows:
        try:
            layers[window] = int(window.attrib["layer"])
        except (KeyError, ValueError) as error:
            raise RuntimeError("Phone window layer unavailable") from error
        bounds_of(window)
    matches = {}
    labels_found = False
    for window in windows:
        parents = {child: parent for parent in window.iter() for child in parent}
        for node in window.iter("node"):
            if not (
                node.get("content-desc") == label or node.get("text", "").split("\n")[0] == label
            ):
                continue
            labels_found = True
            path = []
            current = node
            while current is not window:
                path.append(current)
                current = parents[current]
            if any(
                item.get("package") != PACKAGE
                or item.get("enabled") != "true"
                or item.get("visible-to-user") != "true"
                for item in path
            ):
                continue
            target = next((item for item in path if item.get("clickable") == "true"), None)
            if target is None:
                continue
            bounds = bounds_of(node)
            point = ((bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2)
            if any(not contains(bounds_of(item), point) for item in [window, *path]):
                raise RuntimeError(f"Action {label} lies outside its visible phone bounds")
            if any(
                other is not window
                and layers[other] >= layers[window]
                and contains(bounds_of(other), point)
                for other in windows
            ):
                raise RuntimeError(f"Action {label} is obscured by another phone window")
            matches[(window, target)] = {
                "display_id": 0,
                "window_id": window.get("id"),
                "package": PACKAGE,
                "label": label,
                "node_bounds": list(bounds),
                "clickable_bounds": list(bounds_of(target)),
                "point": list(point),
            }
    if not matches and not labels_found:
        raise ActionNotReady(f"Phone {label} action has not appeared")
    if len(matches) != 1:
        raise RuntimeError(f"Expected one usable phone {label} action, got {len(matches)}")
    return next(iter(matches.values()))


def phone_focus(dump):
    """Read display 0 specifically; external focus must not produce a pass."""
    for section in re.split(r"(?=^  Display: mDisplayId=)", dump, flags=re.M):
        if re.match(r"  Display: mDisplayId=0\b", section):
            match = re.search(r"^\s*mCurrentFocus=(.*)$", section, re.M)
            return match[1].strip() if match else "unknown"
    raise RuntimeError("Phone display focus metadata unavailable")


def controller_focused(focus):
    return bool(re.search(r"\bdev\.dexdroid/(?:dev\.dexdroid)?\.MainActivity\b", focus))


def desktop_ids(dump):
    """Use the owned virtual display's name, never the physical HDMI display."""
    return set(
        int(match[1])
        for match in re.finditer(
            r'DisplayInfo\{"DexDroid Desktop [^"\n]*", displayId (\d+)\b', dump
        )
    )


def chrome_on_desktop(stacks, owned):
    for section in re.split(r"(?=^(?:Stack|RootTask) id=)", stacks, flags=re.M):
        display = re.search(r"\bdisplayId=(\d+)\b", section)
        if display and int(display[1]) in owned and "com.android.chrome/" in section:
            return True
    return False


def phone_physical_display(dump):
    ids = re.findall(r"^Display (\d+) \(HWC display 0\):", dump, flags=re.M)
    if len(ids) != 1:
        raise RuntimeError("Unique primary physical phone display unavailable")
    return ids[0]


class Check:
    def __init__(self, adb, serial, output):
        self.base = [adb, "-s", serial]
        self.output = output
        self.apk_path = None

    def shell(self, *args):
        result = command(self.base + ["shell", *args], timeout=30)
        if result.returncode:
            raise RuntimeError(f"ADB shell {args[0]} failed: {result.stderr}")
        return result.stdout

    def save(self, path, value):
        (self.output / path).write_text(value, encoding="utf-8")

    def screenshot(self, path):
        displays = self.shell("dumpsys", "SurfaceFlinger", "--display-id")
        self.save(f"{path}.displays.txt", displays)
        display = phone_physical_display(displays)
        result = subprocess.run(
            self.base + ["exec-out", "screencap", "-p", "-d", display],
            capture_output=True,
            timeout=30,
        )
        if result.returncode or not result.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("Phone screenshot unavailable")
        (self.output / path).write_bytes(result.stdout)

    def tap(self, label, prefix):
        if not self.apk_path or not re.fullmatch(r"/[A-Za-z0-9_./=+~\-]+/base\.apk", self.apk_path):
            raise RuntimeError("Validated installed debug APK path required for phone UI dump")
        deadline = time.monotonic() + 10
        attempts = []
        try:
            while True:
                if attempts and time.monotonic() >= deadline:
                    raise RuntimeError(f"Timed out waiting for phone {label} action")
                attempt = {"attempt": len(attempts) + 1, "result": "dump-failed"}
                attempts.append(attempt)
                # Each read-only process disconnects and exits before returning.
                xml = self.shell(
                    "env",
                    f"CLASSPATH={self.apk_path}",
                    "app_process",
                    "/system/bin",
                    "dev.dexdroid.ShellUiDump",
                )
                snapshot = f"{prefix}-controller-attempt-{len(attempts):03d}.xml"
                self.save(snapshot, xml)
                self.save(f"{prefix}-controller.xml", xml)
                attempt.update(snapshot=snapshot, result="selection-failed")
                try:
                    action = phone_action(xml, label)
                except ActionNotReady:
                    attempt["result"] = "not-ready"
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        raise RuntimeError(f"Timed out waiting for phone {label} action") from None
                    time.sleep(min(0.2, remaining))
                    continue
                if time.monotonic() >= deadline:
                    attempt["result"] = "late-ready"
                    raise RuntimeError(f"Timed out waiting for phone {label} action")
                attempt["result"] = "ready"
                break
        finally:
            self.save(f"{prefix}-readiness.json", json.dumps(attempts, indent=2) + "\n")
        self.save(f"{prefix}-action.json", json.dumps(action, indent=2) + "\n")
        self.shell("input", "-d", "0", "tap", *map(str, action["point"]))

    def cycle(self, index, seconds):
        prefix = f"cycle-{index}"
        # The only controller launch in this cycle occurs BEFORE app launch/Stop.
        # No instrumentation, menu, Activity launch or focus command after Stop.
        self.shell("am", "start", "--display", "0", "-n", CONTROLLER)
        deadline = time.monotonic() + 10
        owned = set()
        while time.monotonic() < deadline:
            displays = self.shell("dumpsys", "display")
            owned = desktop_ids(displays)
            if owned:
                break
            time.sleep(0.2)
        if len(owned) != 1:
            raise RuntimeError("Requires single view and exactly one owned desktop display")
        self.tap("Apps", f"{prefix}-apps")
        self.tap("Chrome", f"{prefix}-chrome")
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            stacks = self.shell("am", "stack", "list")
            if chrome_on_desktop(stacks, owned):
                break
            time.sleep(0.2)
        else:
            raise RuntimeError("Chrome did not reach the owned desktop")
        self.save(f"{prefix}-stacks-before.txt", stacks)
        self.save(f"{prefix}-display-before.txt", displays)
        self.tap("Stop desktop", f"{prefix}-stop")
        started = time.monotonic()
        focused_since = None
        samples = []
        try:
            while True:
                windows = self.shell("dumpsys", "window", "displays")
                self.save(f"{prefix}-windows-latest.txt", windows)
                current = desktop_ids(self.shell("dumpsys", "display"))
                focus = phone_focus(windows)
                elapsed = time.monotonic() - started
                samples.append({"elapsed_s": elapsed, "focus": focus, "owned": sorted(current)})
                if focused_since is None:
                    if controller_focused(focus) and not current:
                        focused_since = time.monotonic()
                    elif elapsed > 10:
                        raise RuntimeError("Stop did not restore phone controller/remove desktop")
                elif not controller_focused(focus) or current:
                    raise RuntimeError("Controller lost focus or desktop restarted after Stop")
                elif time.monotonic() - focused_since >= seconds:
                    break
                time.sleep(0.2)
            self.screenshot(f"{prefix}-stopped.png")
            return {"cycle": index, "result": "passed", "samples": len(samples), "watch_s": seconds}
        finally:
            self.save(f"{prefix}-focus.json", json.dumps(samples, indent=2) + "\n")
            self.save(f"{prefix}-stacks-after.txt", self.shell("am", "stack", "list"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb")
    parser.add_argument(
        "--allow-apk-mismatch",
        action="store_true",
        help="Diagnostic comparison only; retain both hashes",
    )
    parser.add_argument("--cycles", type=int, default=3)
    parser.add_argument("--watch-seconds", type=int, default=15)
    args = parser.parse_args()
    if args.cycles < 2 or args.watch_seconds < 15:
        parser.error("At least two cycles and 15 seconds are required")
    adb = adb_path(args.adb)
    select_device(command([adb, "devices"]).stdout, args.serial)
    version = re.search(
        r"versionName '([^']+)'", (ROOT / "app/build.gradle").read_text(encoding="utf-8")
    )[1]
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    output = ROOT / "testresults" / version / "runs" / f"{stamp}-chrome-stop"
    output.mkdir(parents=True)
    check = Check(adb, args.serial, output)
    metadata = {
        "started_utc": stamp,
        "serial": args.serial,
        "git_head": command(["git", "rev-parse", "HEAD"]).stdout.strip(),
        "git_status": command(["git", "status", "--porcelain"]).stdout,
        "instrumentation": False,
        "allow_apk_mismatch": args.allow_apk_mismatch,
        "cycles_requested": args.cycles,
        "watch_seconds": args.watch_seconds,
        "cycles": [],
        "result": "failed",
    }
    try:
        instrumentation = check.shell("dumpsys", "activity")
        if (
            "Active instrumentation:" in instrumentation
            or "mActiveInstrumentation=" in instrumentation
        ):
            raise RuntimeError("End active instrumentation before this native Stop check")
        check.save("device.txt", check.shell("getprop"))
        package = check.shell("pm", "path", "dev.dexdroid")
        path = next(
            line.removeprefix("package:")
            for line in package.splitlines()
            if line.endswith("/base.apk")
        )
        check.apk_path = path
        metadata["installed_apk_sha256"] = check.shell("sha256sum", path).split()[0]
        metadata["local_apk_sha256"] = hashlib.sha256(
            (ROOT / "app/build/outputs/apk/debug/app-debug.apk").read_bytes()
        ).hexdigest()
        if (
            metadata["installed_apk_sha256"] != metadata["local_apk_sha256"]
            and not args.allow_apk_mismatch
        ):
            raise RuntimeError(
                "Installed APK differs from local debug build; install the current APK first"
            )
        chrome = check.shell("dumpsys", "package", "com.android.chrome")
        check.save(
            "chrome-version.txt",
            "\n".join(
                line.strip()
                for line in chrome.splitlines()
                if "versionName=" in line or "versionCode=" in line
            )
            + "\n",
        )
        for index in range(1, args.cycles + 1):
            metadata["cycles"].append(check.cycle(index, args.watch_seconds))
            print(
                f"PASS Chrome Stop cycle {index}: {args.watch_seconds}s idle phone focus",
                flush=True,
            )
        metadata["result"] = "passed"
    except (RuntimeError, OSError, subprocess.SubprocessError, ET.ParseError) as error:
        metadata["error"] = str(error)
        check.screenshot("failure-phone.png")
        print(f"FAIL {error}", flush=True)
    finally:
        check.save("logcat.txt", check.shell("logcat", "-d", "-t", "4000", "-v", "threadtime"))
        check.save(
            "events.txt",
            check.shell("logcat", "-b", "events", "-d", "-t", "2000", "-v", "threadtime"),
        )
        metadata["finished_utc"] = datetime.now(timezone.utc).isoformat()
        check.save("run.json", json.dumps(metadata, indent=2) + "\n")
    print(f"{metadata['result'].upper()}: {output}")
    return int(metadata["result"] != "passed")


if __name__ == "__main__":
    raise SystemExit(main())
