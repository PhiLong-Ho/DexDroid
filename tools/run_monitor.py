"""Local bounded command supervisor. Raw streams are independent of progress extraction."""

import argparse
import hashlib
import json
import math
import os
import re
import signal
import subprocess
import threading
import time
import uuid
from contextlib import suppress
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

STATES = {"completed", "failed", "timeout", "cancelled"}


class AssertionProgress:
    """Bounded line adapter; assertion labels are not case counts or a known total."""

    def __init__(self):
        self.buffers = {}

    def __call__(self, stream, chunk):
        text = self.buffers.get(stream, b"") + chunk
        lines = text.split(b"\n")
        self.buffers[stream] = lines.pop()[-4096:]
        found = None
        for line in lines:
            match = re.search(rb"(?:^|stream=)(PASS [^\r\n]+|MONITOR_PROGRESS [^\r\n]+)", line)
            if match:
                found = match.group(1).decode("utf-8", errors="replace")[:200]
        return found


def positive(value):
    if isinstance(value, bool):
        raise ValueError("Time budgets must be numbers")
    value = float(value)
    if not math.isfinite(value) or value <= 0:
        raise ValueError("Time budgets must be finite and positive")
    return value


def utc():
    return datetime.now(timezone.utc).isoformat()


class ProcessTree:
    """Own ordinary descendants; not a containment boundary for hostile processes."""

    def __init__(self):
        self.process = None
        self.job = None
        if os.name == "nt":
            import ctypes
            from ctypes import wintypes

            class Basic(ctypes.Structure):
                _fields_ = [
                    ("per_process", ctypes.c_int64),
                    ("per_job", ctypes.c_int64),
                    ("flags", wintypes.DWORD),
                    ("min_ws", ctypes.c_size_t),
                    ("max_ws", ctypes.c_size_t),
                    ("active", wintypes.DWORD),
                    ("affinity", ctypes.c_size_t),
                    ("priority", wintypes.DWORD),
                    ("scheduling", wintypes.DWORD),
                ]

            class Limits(ctypes.Structure):
                _fields_ = [
                    ("basic", Basic),
                    ("io", ctypes.c_uint64 * 6),
                    ("memory", ctypes.c_size_t * 4),
                ]

            self.api = ctypes.WinDLL("kernel32", use_last_error=True)
            self.api.CreateJobObjectW.argtypes = [ctypes.c_void_p, wintypes.LPCWSTR]
            self.api.CreateJobObjectW.restype = wintypes.HANDLE
            self.api.SetInformationJobObject.argtypes = [
                wintypes.HANDLE,
                ctypes.c_int,
                ctypes.c_void_p,
                wintypes.DWORD,
            ]
            self.api.AssignProcessToJobObject.argtypes = [wintypes.HANDLE, wintypes.HANDLE]
            self.api.CloseHandle.argtypes = [wintypes.HANDLE]
            self.job = self.api.CreateJobObjectW(None, None)
            if not self.job:
                raise ctypes.WinError(ctypes.get_last_error())
            limits = Limits()
            limits.basic.flags = 0x2000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
            if not self.api.SetInformationJobObject(
                self.job, 9, ctypes.byref(limits), ctypes.sizeof(limits)
            ):
                self.close()
                raise ctypes.WinError(ctypes.get_last_error())

    def attach(self, process):
        self.process = process
        if self.job and not self.api.AssignProcessToJobObject(self.job, int(process._handle)):
            process.kill()
            process.wait(timeout=2)
            raise OSError("Cannot assign owned process to Windows job")
        if self.job:
            self._resume_primary(process.pid)

    def _resume_primary(self, pid):
        """Popen closes its thread handle. Locate the suspended primary via Toolhelp."""
        import ctypes
        from ctypes import wintypes

        class Entry(ctypes.Structure):
            _fields_ = [
                ("size", wintypes.DWORD),
                ("usage", wintypes.DWORD),
                ("thread_id", wintypes.DWORD),
                ("process_id", wintypes.DWORD),
                ("base_priority", wintypes.LONG),
                ("delta_priority", wintypes.LONG),
                ("flags", wintypes.DWORD),
            ]

        self.api.CreateToolhelp32Snapshot.argtypes = [wintypes.DWORD, wintypes.DWORD]
        self.api.CreateToolhelp32Snapshot.restype = wintypes.HANDLE
        self.api.Thread32First.argtypes = [wintypes.HANDLE, ctypes.POINTER(Entry)]
        self.api.Thread32Next.argtypes = [wintypes.HANDLE, ctypes.POINTER(Entry)]
        self.api.OpenThread.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        self.api.OpenThread.restype = wintypes.HANDLE
        self.api.ResumeThread.argtypes = [wintypes.HANDLE]
        self.api.ResumeThread.restype = wintypes.DWORD
        snapshot = self.api.CreateToolhelp32Snapshot(4, 0)  # TH32CS_SNAPTHREAD
        if snapshot == ctypes.c_void_p(-1).value:
            raise ctypes.WinError(ctypes.get_last_error())
        try:
            entry = Entry()
            entry.size = ctypes.sizeof(entry)
            available = self.api.Thread32First(snapshot, ctypes.byref(entry))
            for _ in range(131072):
                if not available:
                    break
                if entry.process_id == pid:
                    thread = self.api.OpenThread(2, False, entry.thread_id)  # THREAD_SUSPEND_RESUME
                    if not thread:
                        raise ctypes.WinError(ctypes.get_last_error())
                    try:
                        if self.api.ResumeThread(thread) != 1:
                            raise OSError("Unexpected primary thread suspension state")
                        return
                    finally:
                        self.api.CloseHandle(thread)
                entry.size = ctypes.sizeof(entry)
                available = self.api.Thread32Next(snapshot, ctypes.byref(entry))
            raise OSError("Suspended primary thread not found within snapshot budget")
        finally:
            self.api.CloseHandle(snapshot)

    def close(self):
        if self.job:
            self.api.CloseHandle(self.job)
            self.job = None
        elif self.process is not None and os.name != "nt":
            with suppress(ProcessLookupError):
                os.killpg(self.process.pid, signal.SIGKILL)
        self.process = None


@dataclass
class CommandResult:
    returncode: int
    state: str
    stdout_path: Path
    stderr_path: Path
    combined_path: Path
    duration_seconds: float
    error: str | None = None


class Session:
    """One run directory; no queues, network, automatic retries or model calls."""

    def __init__(self, folder, *, context=None, heartbeat=2, max_events=20000):
        if len(json.dumps(context or {}, ensure_ascii=True).encode("utf-8")) > 8192:
            raise ValueError("Monitor context exceeds eight KiB")
        self.folder = Path(folder).resolve()
        self.heartbeat = positive(heartbeat)
        self.max_events = int(max_events)
        if self.max_events < 8:
            raise ValueError("Event limit must be at least eight")
        self.folder.mkdir(parents=True, exist_ok=False, mode=0o700)
        self.lock = threading.RLock()
        self.stop = threading.Event()
        self.started = time.monotonic()
        self.event_count = 0
        self.error = None
        self.sequence = 0
        self.failed = False
        self.state = {
            "schema_version": 1,
            "run_id": uuid.uuid4().hex,
            "scope": "owned commands; owning runner decides test acceptance",
            "context": context or {},
            "started_utc": utc(),
            "state": "running",
            "phase": "preparing",
            "command": None,
            "warning": None,
            "result": None,
            "progress": None,
            "total": None,
            "failed_commands": 0,
        }
        self.emit("run_started")
        self.thread = threading.Thread(target=self._heartbeat, daemon=True)
        self.thread.start()

    def _snapshot(self):
        self.state.update(
            heartbeat_utc=utc(), elapsed_seconds=round(time.monotonic() - self.started, 3)
        )
        target = self.folder / "status.json"
        temporary = self.folder / "status.tmp"
        temporary.write_text(json.dumps(self.state, ensure_ascii=True) + "\n", encoding="utf-8")
        # Windows readers/AV may briefly hold the old file without delete sharing.
        for attempt in range(10):
            try:
                os.replace(temporary, target)
                break
            except PermissionError:
                if attempt == 9:
                    raise
                time.sleep(0.01)

    def emit(self, event, **fields):
        with self.lock:
            self.event_count += 1
            if self.event_count > self.max_events:
                self.error = "event_limit_exceeded"
                return
            row = {
                "schema_version": 1,
                "run_id": self.state["run_id"],
                "sequence": self.event_count,
                "utc": utc(),
                "event": event,
                **fields,
            }
            with (self.folder / "events.jsonl").open("a", encoding="utf-8", newline="\n") as stream:
                stream.write(json.dumps(row, ensure_ascii=True) + "\n")
            self._snapshot()

    def phase(self, name):
        with self.lock:
            self.state.update(
                phase=name,
                command=None,
                pid=None,
                deadline_seconds=None,
                command_elapsed_seconds=None,
            )
            self.emit("phase_changed", phase=name)

    def _heartbeat(self):
        while not self.stop.wait(self.heartbeat):
            try:
                with self.lock:
                    self._snapshot()
            except OSError:
                self.error = "status_write_failed"
                return

    def finish(self, state=None, **fields):
        self.stop.set()
        self.thread.join(timeout=self.heartbeat + 1)
        with self.lock:
            outcome = state or ("failed" if self.failed or self.error else "completed")
            if outcome not in STATES:
                raise ValueError("Invalid final state")
            if (self.failed or self.error) and outcome == "completed":
                outcome = "failed"
            self.state.update(state=outcome, result=outcome, phase="finished", **fields)
            # Reserve a final snapshot even if the bounded event stream filled.
            self.emit("run_completed", result=outcome)
            # The final event itself can reach the cap. Seal only after all
            # background writers have stopped and finalization faults are known.
            if self.error:
                self.state["monitor_error"] = self.error
                if outcome == "completed":
                    outcome = "failed"
                    self.state.update(state=outcome, result=outcome)
            self._snapshot()
            return outcome

    def run(
        self,
        argv,
        *,
        label="command",
        cwd=None,
        timeout=900,
        idle_warning=60,
        max_output_bytes=64 * 1024 * 1024,
        cancel=None,
        extractor=None,
        combined_path=None,
    ):
        timeout, idle_warning = positive(timeout), positive(idle_warning)
        if not re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}", label):
            raise ValueError("Portable command label required")
        if max_output_bytes < 1:
            raise ValueError("Positive output limit required")
        self.sequence += 1
        logs = self.folder / f"{self.sequence:03d}-{label}"
        logs.mkdir()
        stdout, stderr = logs / "stdout.bin", logs / "stderr.bin"
        combined = Path(combined_path) if combined_path else logs / "combined.log"
        started = time.monotonic()
        last_output = [started]
        byte_count = [0]
        fault = []
        readers = []
        capture_closed = threading.Event()
        process = None
        tree = None
        merged = None
        result, code, message = "failed", 127, None
        with self.lock:
            self.state.update(
                phase=label,
                command=label,
                state="running",
                result=None,
                command_result="pending",
                command_elapsed_seconds=0,
                error=None,
                pid=None,
                deadline_seconds=timeout,
                idle_warning_seconds=idle_warning,
                command_started_utc=utc(),
                last_output_utc=None,
                last_progress_utc=None,
                progress=None,
                warning=None,
                stdout_path=str(stdout),
                stderr_path=str(stderr),
                combined_path=str(combined),
            )
            self.emit(
                "command_started",
                label=label,
                argv_sha256=hashlib.sha256(json.dumps(argv).encode()).hexdigest(),
            )

        def reader(pipe, raw, merged, stream_name):
            try:
                with raw.open("xb") as output:
                    while not capture_closed.is_set() and (chunk := pipe.read1(4096)):
                        with self.lock:
                            output.write(chunk)
                            output.flush()
                            merged.write(chunk)
                            merged.flush()
                            byte_count[0] += len(chunk)
                            last_output[0] = time.monotonic()
                            self.state["last_output_utc"] = utc()
                            self.emit(
                                "output_chunk",
                                stream=stream_name,
                                bytes=len(chunk),
                                total_bytes=byte_count[0],
                            )
                            if byte_count[0] > max_output_bytes:
                                fault.append("output_limit_exceeded")
                                return
                        if extractor:
                            try:
                                progress = extractor(stream_name, chunk)
                                if progress is not None and not capture_closed.is_set():
                                    with self.lock:
                                        self.state.update(
                                            progress=str(progress)[:200], last_progress_utc=utc()
                                        )
                                        self.emit("progress", label=self.state["progress"])
                            except Exception:
                                # Raw capture is independent from extraction success.
                                if "extraction_failed" not in fault:
                                    fault.append("extraction_failed")
            except (OSError, ValueError):
                fault.append("capture_failed")
            finally:
                pipe.close()

        try:
            merged = combined.open("xb")
            tree = ProcessTree()
            process = subprocess.Popen(
                argv,
                cwd=cwd,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                start_new_session=os.name != "nt",
                creationflags=(subprocess.CREATE_NEW_PROCESS_GROUP | 0x00000004)
                if os.name == "nt"
                else 0,  # CREATE_SUSPENDED until job ownership
            )
            tree.attach(process)
            with self.lock:
                self.state["pid"] = process.pid
                self.emit("process_started", pid=process.pid)
            for pipe, path, name in (
                (process.stdout, stdout, "stdout"),
                (process.stderr, stderr, "stderr"),
            ):
                thread = threading.Thread(
                    target=reader, args=(pipe, path, merged, name), daemon=True
                )
                thread.start()
                readers.append(thread)
            while True:
                now = time.monotonic()
                if cancel is not None and cancel.is_set():
                    result, code = "cancelled", 130
                    break
                if now - started >= timeout:
                    result, code = "timeout", 124
                    break
                if self.error or any(f != "extraction_failed" for f in fault):
                    result, code = "failed", 74
                    break
                if process.poll() is not None:
                    code = process.returncode
                    result = "completed" if code == 0 else "failed"
                    break
                with self.lock:
                    self.state.update(
                        command_elapsed_seconds=round(now - started, 3),
                        warning="quiet_output" if now - last_output[0] >= idle_warning else None,
                    )
                time.sleep(0.05)
        except KeyboardInterrupt:
            result, code = "cancelled", 130
        except (OSError, subprocess.SubprocessError) as error:
            result, code, message = "failed", 127, type(error).__name__
        finally:
            if tree:
                tree.close()
            if process:
                try:
                    process.wait(timeout=2)
                except subprocess.TimeoutExpired:
                    fault.append("process_cleanup_failed")
                    process.kill()
                    with suppress(subprocess.TimeoutExpired):
                        process.wait(timeout=1)
                for thread in readers:
                    thread.join(timeout=2)
                if any(thread.is_alive() for thread in readers):
                    fault.append("incomplete_stream_cleanup")
                    self.error = "incomplete_stream_cleanup"
                if not any(thread.is_alive() for thread in readers):
                    for pipe in (process.stdout, process.stderr):
                        if pipe:
                            pipe.close()
            capture_closed.set()
            if merged:
                merged.close()
            for path in (stdout, stderr):
                path.touch(exist_ok=True)
        if fault or self.error:
            message = ",".join(sorted(set(fault + ([self.error] if self.error else []))))
            if result == "completed":
                result, code = "failed", 74
        self.failed |= result != "completed"
        elapsed = round(time.monotonic() - started, 3)
        with self.lock:
            if result != "completed":
                self.state["failed_commands"] += 1
                self.state["last_failure"] = {"label": label, "result": result, "exit_code": code}
            self.state.update(
                phase="between_commands",
                command_result=result,
                command_elapsed_seconds=elapsed,
                error=message,
                warning=None,
            )
            self.emit(
                "command_exited",
                label=label,
                result=result,
                exit_code=code,
                duration_seconds=elapsed,
                error=message,
            )
        return CommandResult(code, result, stdout, stderr, combined, elapsed, message)


def read_status(folder, *, stale_after=30):
    try:
        with (Path(folder) / "status.json").open("rb") as stream:
            raw = stream.read(65537)
        if len(raw) > 65536:
            raise ValueError("Status exceeds 64 KiB")
        row = json.loads(raw.decode("utf-8"))
        if (
            not isinstance(row, dict)
            or row.get("schema_version") != 1
            or row.get("state") not in STATES | {"running"}
        ):
            raise ValueError("Invalid status")
        age = (
            datetime.now(timezone.utc) - datetime.fromisoformat(row["heartbeat_utc"])
        ).total_seconds()
        row["heartbeat_age_seconds"] = round(max(0, age), 1)
        if row["state"] == "running" and age > positive(stale_after):
            row.update(state="unknown", warning="stale_supervisor")
        return row
    except (OSError, ValueError, KeyError, TypeError):
        return {"state": "unknown", "warning": "missing_or_invalid_status", "phase": "unknown"}


def format_status(row):
    deadline = row.get("deadline_seconds")
    deadline_text = f"{deadline}s" if deadline is not None else "not active"
    command_time = row.get("command_elapsed_seconds")
    command_text = f"{command_time}s" if command_time is not None else "not active"
    return "\n".join(
        [
            f"{row['state'].upper()}  {row.get('phase', 'unknown')}",
            f"Scope: {row.get('scope', 'unknown')}",
            f"Elapsed {row.get('elapsed_seconds', '?')}s  Command {command_text}  Deadline {deadline_text}",
            f"Heartbeat age {row.get('heartbeat_age_seconds', '?')}s  Warning {row.get('warning') or 'none'}",
            f"Last output {row.get('last_output_utc') or 'unknown'}  Last progress {row.get('last_progress_utc') or 'unknown'}",
            f"Progress {row.get('progress') or 'unknown'}; total unknown",
            f"Command outcome {row.get('command_result', 'pending')}  Error {row.get('error') or 'none'}  Restoration {row.get('restoration', 'not applicable')}",
            f"Failed commands {row.get('failed_commands', 0)}  Last failure {row.get('last_failure') or 'none'}",
            f"stdout {row.get('stdout_path', '?')}\nstderr {row.get('stderr_path', '?')}",
        ]
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest="action", required=True)
    run = subs.add_parser("run", help="Explicitly launch one owned command")
    run.add_argument("--output", type=Path, required=True)
    run.add_argument("--timeout", type=positive, default=900)
    run.add_argument("--idle-warning", type=positive, default=60)
    run.add_argument("argv", nargs=argparse.REMAINDER)
    for name in ("status", "watch"):
        sub = subs.add_parser(name, help="Read-only; never launches/cancels work")
        sub.add_argument("folder", type=Path)
        sub.add_argument("--interval", type=positive, default=2)
    args = parser.parse_args()
    if args.action == "run":
        argv = args.argv[1:] if args.argv[:1] == ["--"] else args.argv
        if not argv:
            parser.error("Supply a command after --")
        positive(args.timeout)
        positive(args.idle_warning)
        session = Session(args.output)
        print(f'Monitor: python tools/run_monitor.py watch "{session.folder}"', flush=True)
        try:
            result = session.run(
                argv,
                timeout=args.timeout,
                idle_warning=args.idle_warning,
                extractor=AssertionProgress(),
            )
            final = session.finish(result.state)
            return result.returncode or (74 if final != "completed" else 0)
        finally:
            session.stop.set()
    positive(args.interval)
    last_key, last_emit = None, 0
    try:
        while True:
            row = read_status(args.folder)
            key = tuple(
                row.get(field)
                for field in ("state", "phase", "warning", "progress", "command_result")
            )
            if key != last_key or time.monotonic() - last_emit >= 30:
                print(format_status(row), flush=True)
                last_key, last_emit = key, time.monotonic()
            if row["state"] == "unknown":
                return 2
            if args.action == "status" or row["state"] in STATES:
                return 0 if row["state"] == "completed" else 1
            time.sleep(args.interval)
    except KeyboardInterrupt:
        print("Observer detached; work continues.")
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
