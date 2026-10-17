import json
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import run_monitor


class MonitorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.session = run_monitor.Session(self.root / "monitor", heartbeat=0.05)
        self.addCleanup(self.finish)

    def finish(self):
        if not self.session.stop.is_set():
            self.session.finish()

    def wait_for(self, condition):
        deadline = time.monotonic() + 6
        while time.monotonic() < deadline:
            if condition():
                return
            time.sleep(0.02)
        self.fail("Observable fixture state did not arrive within six seconds")

    def test_streams_bytes_before_exit_with_readonly_observation_and_quiet_warning(self):
        self.session.run([sys.executable, "-c", "pass"], label="earlier")
        release = self.root / "release"
        payload = b"utf8:\xc3\xa9\x00\xff\r\n"
        code = (
            "import os,time,pathlib; os.write(1,"
            + repr(payload)
            + "); os.write(2,b'error-no-newline'); "
            f"p=pathlib.Path({str(release)!r}); "
            "\nwhile not p.exists(): time.sleep(.02)\n"
        )
        results = []
        cancel = threading.Event()
        thread = threading.Thread(
            target=lambda: results.append(
                self.session.run(
                    [sys.executable, "-c", code], timeout=6, idle_warning=0.1, cancel=cancel
                )
            ),
            daemon=True,
        )
        thread.start()
        try:
            stdout = self.root / "monitor/002-command/stdout.bin"
            stderr = self.root / "monitor/002-command/stderr.bin"
            self.wait_for(
                lambda: stdout.exists()
                and stdout.read_bytes() == payload
                and stderr.exists()
                and stderr.read_bytes() == b"error-no-newline"
            )
            self.assertTrue(thread.is_alive(), "Output must be available before child exit")
            self.assertEqual(
                "pending", run_monitor.read_status(self.session.folder)["command_result"]
            )
            self.wait_for(
                lambda: run_monitor.read_status(self.session.folder).get("warning")
                == "quiet_output"
            )
            before = stdout.read_bytes()
            viewer = subprocess.run(
                [
                    sys.executable,
                    str(Path(run_monitor.__file__)),
                    "status",
                    str(self.session.folder),
                ],
                capture_output=True,
                timeout=4,
            )
            self.assertIn(b"RUNNING", viewer.stdout)
            self.assertEqual(before, stdout.read_bytes())
            self.assertTrue(thread.is_alive(), "Observation must not stop work")
            self.assertLess(
                run_monitor.read_status(self.session.folder)["heartbeat_age_seconds"], 2
            )
            release.touch()
            thread.join(4)
            self.assertFalse(thread.is_alive())
            self.assertEqual("completed", results[0].state)
            self.assertEqual(payload, stdout.read_bytes())
        finally:
            cancel.set()
            thread.join(4)

    def test_timeout_and_cancellation_preserve_both_partial_streams(self):
        for index, cancelled in enumerate((False, True)):
            with self.subTest(cancelled=cancelled):
                cancel = threading.Event()
                timer = threading.Timer(0.5, cancel.set) if cancelled else None
                if timer:
                    timer.start()
                try:
                    result = self.session.run(
                        [
                            sys.executable,
                            "-c",
                            "import os,time;os.write(1,b'out');os.write(2,b'err');time.sleep(30)",
                        ],
                        label=f"partial-{index}",
                        timeout=2 if cancelled else 0.5,
                        cancel=cancel,
                    )
                    self.assertEqual("cancelled" if cancelled else "timeout", result.state)
                    self.assertEqual(130 if cancelled else 124, result.returncode)
                    self.assertEqual(b"out", result.stdout_path.read_bytes())
                    self.assertEqual(b"err", result.stderr_path.read_bytes())
                    self.assertLess(result.duration_seconds, 5)
                finally:
                    if timer:
                        timer.cancel()

    def test_owned_descendant_is_stopped_when_leader_exits(self):
        sentinel = self.root / "unexpected-descendant"
        child = f"import time,pathlib;time.sleep(1);pathlib.Path({str(sentinel)!r}).touch()"
        parent = (
            "import subprocess,sys;subprocess.Popen([sys.executable,'-c',"
            + repr(child)
            + "]);print('spawned',flush=True)"
        )
        result = self.session.run([sys.executable, "-c", parent], timeout=4)
        self.assertEqual("completed", result.state)
        self.assertLess(result.duration_seconds, 3)
        time.sleep(1.1)
        self.assertFalse(sentinel.exists(), "Owned background descendant survived command cleanup")

    def test_scheduling_delay_before_ownership_cannot_leave_live_descendant(self):
        sentinel = self.root / "unowned-descendant"
        child = f"import time,pathlib;time.sleep(.9);pathlib.Path({str(sentinel)!r}).touch()"
        parent = (
            "import subprocess,sys,time;subprocess.Popen([sys.executable,'-c',"
            + repr(child)
            + "]);print('spawned',flush=True);time.sleep(30)"
        )
        original = run_monitor.ProcessTree.attach

        def delayed(tree, process):
            time.sleep(0.2)
            return original(tree, process)

        with mock.patch.object(run_monitor.ProcessTree, "attach", delayed):
            result = self.session.run([sys.executable, "-c", parent], timeout=0.5)
        self.assertEqual("timeout", result.state)
        self.assertIn(b"spawned", result.stdout_path.read_bytes())
        time.sleep(1)
        self.assertFalse(
            sentinel.exists(), "Ordinary child escaped while supervisor was descheduled"
        )

    def test_slow_extraction_does_not_delay_child_deadline(self):
        sentinel = self.root / "deadline-missed"
        script = f"import os,time,pathlib;os.write(1,b'ready');time.sleep(.45);pathlib.Path({str(sentinel)!r}).touch();time.sleep(30)"

        def slow(stream, chunk):
            time.sleep(0.8)

        result = self.session.run([sys.executable, "-c", script], timeout=0.2, extractor=slow)
        self.assertEqual("timeout", result.state)
        self.assertFalse(sentinel.exists(), "Progress extraction blocked deadline enforcement")

    def test_nonzero_launch_failure_and_output_limit_never_pass(self):
        bad = self.session.run([sys.executable, "-c", "raise SystemExit(7)"], label="bad")
        missing = self.session.run([str(self.root / "missing-executable")], label="missing")
        flood = self.session.run(
            [sys.executable, "-c", "import os;os.write(1,b'x'*100000)"],
            label="flood",
            max_output_bytes=1000,
        )
        self.assertEqual(7, bad.returncode)
        self.assertEqual(127, missing.returncode)
        self.assertEqual("failed", flood.state)
        self.assertIn("output_limit_exceeded", flood.error)
        self.assertLessEqual(flood.stdout_path.stat().st_size, 1000 + 8192)
        self.session.finish("completed")
        self.assertEqual("failed", run_monitor.read_status(self.session.folder)["state"])

    def test_extraction_failure_keeps_complete_raw_bytes_and_fails(self):
        def broken(stream, chunk):
            raise ValueError("fixture extractor error")

        result = self.session.run(
            [sys.executable, "-c", "import os;os.write(1,b'first');os.write(2,b'second')"],
            extractor=broken,
        )
        self.assertEqual("failed", result.state)
        self.assertIn("extraction_failed", result.error)
        self.assertEqual(b"first", result.stdout_path.read_bytes())
        self.assertEqual(b"second", result.stderr_path.read_bytes())

    def test_capture_failure_stops_command_and_refuses_success(self):
        original = Path.open

        def faulty(path, *args, **kwargs):
            if path.name == "stdout.bin" and args == ("xb",):
                raise OSError("fixture disk failure")
            return original(path, *args, **kwargs)

        with mock.patch.object(Path, "open", faulty):
            result = self.session.run(
                [sys.executable, "-c", "import time;time.sleep(30)"], timeout=2
            )
        self.assertEqual("failed", result.state)
        self.assertIn("capture_failed", result.error)
        self.assertLess(result.duration_seconds, 4)

    def test_status_is_unknown_for_missing_invalid_or_stale_supervisor(self):
        self.assertEqual("unknown", run_monitor.read_status(self.root / "absent")["state"])
        self.session.stop.set()
        self.session.thread.join(1)
        path = self.session.folder / "status.json"
        for value in ([], {}, {"schema_version": 1, "state": "completed"}):
            path.write_text(json.dumps(value), encoding="utf-8")
            self.assertEqual("unknown", run_monitor.read_status(self.session.folder)["state"])
        path.write_bytes(b" " * 65537)
        self.assertEqual("unknown", run_monitor.read_status(self.session.folder)["state"])
        path.write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "state": "running",
                    "heartbeat_utc": "2000-01-01T00:00:00+00:00",
                }
            ),
            encoding="utf-8",
        )
        state = run_monitor.read_status(self.session.folder)
        self.assertEqual("unknown", state["state"])
        self.assertEqual("stale_supervisor", state["warning"])

    def test_progress_adapter_handles_split_lines_and_unknown_totals(self):
        adapter = run_monitor.AssertionProgress()
        self.assertIsNone(adapter("stdout", b"stream=PASS spl"))
        self.assertEqual("PASS split assertion", adapter("stdout", b"it assertion\n"))
        self.assertIsNone(adapter("stderr", b"unrelated\n"))
        self.assertIsNone(run_monitor.read_status(self.session.folder)["total"])

    def test_invalid_budgets_and_labels_do_not_launch(self):
        for value in (0, -1, float("nan"), float("inf"), True):
            with self.subTest(value=value), self.assertRaises(ValueError):
                self.session.run([sys.executable], timeout=value)
        with self.assertRaises(ValueError):
            self.session.run([sys.executable], label="../escape")

    def test_event_limit_keeps_final_failure_snapshot(self):
        self.session.max_events = 8
        result = self.session.run(
            [
                sys.executable,
                "-c",
                "import os,time\nfor i in range(100):os.write(1,b'x');time.sleep(.01)",
            ],
            timeout=3,
        )
        self.assertEqual("failed", result.state)
        self.session.finish()
        row = run_monitor.read_status(self.session.folder)
        self.assertEqual("failed", row["state"])
        self.assertEqual("event_limit_exceeded", row["monitor_error"])
        self.assertEqual(
            8, len((self.session.folder / "events.jsonl").read_text(encoding="utf-8").splitlines())
        )

    def test_final_event_limit_cannot_seal_a_completed_run(self):
        result = self.session.run([sys.executable, "-c", "pass"])
        self.assertEqual(0, result.returncode)
        self.session.max_events = self.session.event_count
        self.assertEqual("failed", self.session.finish(result.state))
        row = run_monitor.read_status(self.session.folder)
        self.assertEqual("failed", row["state"])
        self.assertEqual("event_limit_exceeded", row["monitor_error"])

    def test_cli_refuses_success_for_post_command_monitor_fault(self):
        original = self.session.run

        def run(*args, **kwargs):
            result = original(*args, **kwargs)
            self.session.error = "status_write_failed"
            return result

        with (
            mock.patch.object(run_monitor, "Session", return_value=self.session),
            mock.patch.object(self.session, "run", side_effect=run),
            mock.patch.object(
                sys,
                "argv",
                [
                    "monitor",
                    "run",
                    "--output",
                    str(self.session.folder),
                    "--",
                    sys.executable,
                    "-c",
                    "pass",
                ],
            ),
        ):
            self.assertEqual(74, run_monitor.main())
        self.assertEqual("failed", run_monitor.read_status(self.session.folder)["state"])

    def test_both_observers_distinguish_unknown_from_observed_failure(self):
        invalid = self.root / "invalid"
        invalid.mkdir()
        (invalid / "status.json").write_bytes(b"{")
        stale = self.root / "stale"
        stale.mkdir()
        (stale / "status.json").write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "state": "running",
                    "heartbeat_utc": "2000-01-01T00:00:00+00:00",
                }
            ),
            encoding="utf-8",
        )
        for action in ("status", "watch"):
            for folder in (self.root / "missing", invalid, stale):
                with self.subTest(action=action, folder=folder.name):
                    result = subprocess.run(
                        [sys.executable, run_monitor.__file__, action, str(folder)],
                        capture_output=True,
                        timeout=5,
                    )
                    self.assertEqual(2, result.returncode, result.stderr)
                    self.assertIn(b"UNKNOWN", result.stdout)

    def test_viewer_interrupt_detaches_without_control_action(self):
        with (
            mock.patch.object(run_monitor.time, "sleep", side_effect=KeyboardInterrupt),
            mock.patch.object(sys, "argv", ["monitor", "watch", str(self.session.folder)]),
        ):
            self.assertEqual(0, run_monitor.main())
        self.assertEqual("running", run_monitor.read_status(self.session.folder)["state"])


if __name__ == "__main__":
    unittest.main()
