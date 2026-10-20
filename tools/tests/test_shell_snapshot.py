import contextlib
import hashlib
import io
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_shell_snapshot

VALID = b'<hierarchy display-id="0"><window><node package="dev.dexdroid"/></window></hierarchy>'


class ShellSnapshotTests(unittest.TestCase):
    def test_valid_display_zero_controller_snapshot_is_accepted(self):
        self.assertEqual(
            {"window_count": 1, "node_count": 1}, check_shell_snapshot.validate_snapshot(VALID)
        )

    def test_controller_must_belong_to_a_valid_window_node_tree(self):
        invalid = [
            b'<hierarchy display-id="0"><window/><node package="dev.dexdroid"/></hierarchy>',
            b'<hierarchy display-id="0"><window><other><node package="dev.dexdroid"/></other></window></hierarchy>',
            b'<hierarchy display-id="0"><window><node/><node package="dev.dexdroid"/></window></hierarchy>',
        ]
        for data in invalid:
            with self.subTest(data=data), self.assertRaises(RuntimeError):
                check_shell_snapshot.validate_snapshot(data)

    def test_external_empty_foreign_and_malformed_responses_fail(self):
        for data in [
            b"",
            b"not XML",
            b"<hierarchy",
            b'<hierarchy display-id="0"/>',
            VALID.replace(b'"0"', b'"1"'),
            VALID.replace(b"dev.dexdroid", b"other.app"),
            VALID.replace(b"hierarchy", b"other"),
        ]:
            with self.subTest(data=data), self.assertRaises(RuntimeError):
                check_shell_snapshot.validate_snapshot(data)

    def observe(self, result=None, error=None, diagnostic_failure=False):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        folder = Path(temp.name)
        calls = []

        def run(argv, **kwargs):
            calls.append(argv)
            if "app_process" in argv:
                self.assertEqual(25, kwargs["timeout"])
                if error:
                    raise error
                return result
            return subprocess.CompletedProcess(
                argv, 1 if diagnostic_failure and "logcat" in argv else 0, b"diagnostic", b""
            )

        record = check_shell_snapshot.observe_snapshot(
            ["adb", "-s", "device"], "/data/app/owned/base.apk", folder, run=run
        )
        self.assertEqual(1, sum("app_process" in command for command in calls))
        self.assertFalse(
            any(
                token in command
                for command in calls
                for token in ["input", "install", "settings", "force-stop"]
            )
        )
        return folder, record, calls

    def test_failed_process_cannot_pass_with_xml_and_retains_diagnostics(self):
        folder, record, calls = self.observe(subprocess.CompletedProcess([], 137, VALID, b"Killed"))
        self.assertEqual("failed", record["result"])
        self.assertEqual(137, record["exit_code"])
        self.assertEqual(VALID, (folder / "stdout.xml").read_bytes())
        self.assertEqual(b"Killed", (folder / "stderr.txt").read_bytes())
        self.assertEqual(4, len(calls))
        self.assertEqual(3, len(record["diagnostics"]))

    def test_timeout_retains_partial_output_without_retry(self):
        error = subprocess.TimeoutExpired([], 25, output=b"<partial", stderr=b"partial error")
        folder, record, calls = self.observe(error=error)
        self.assertEqual("timeout", record["result"])
        self.assertEqual(b"<partial", (folder / "stdout.xml").read_bytes())
        self.assertEqual(b"partial error", (folder / "stderr.txt").read_bytes())
        self.assertEqual(4, len(calls))

    def test_invalid_xml_preserves_primary_error_and_failed_diagnostic(self):
        folder, record, calls = self.observe(
            subprocess.CompletedProcess([], 0, b"broken", b""), diagnostic_failure=True
        )
        self.assertEqual("failed", record["result"])
        self.assertIn("Malformed", record["error"])
        self.assertEqual(1, record["diagnostics"][0]["exit_code"])
        self.assertEqual(4, len(calls))
        self.assertEqual(
            "failed",
            json.loads((folder / "observation.json").read_text(encoding="utf-8"))["result"],
        )

    def test_success_requires_one_completed_process_and_hashes_saved_output(self):
        folder, record, calls = self.observe(subprocess.CompletedProcess([], 0, VALID, b""))
        self.assertEqual("passed", record["result"])
        self.assertEqual(0, record["exit_code"])
        self.assertIn("stdout.xml", record["files_sha256"])
        self.assertEqual(4, len(calls))
        self.assertTrue((folder / "logcat.txt").is_file())

    def test_invalid_metadata_prevents_adb_and_evidence_creation(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "app").mkdir()
            (root / "app/build.gradle").write_text("invalid metadata", encoding="utf-8")
            with (
                mock.patch.object(check_shell_snapshot, "ROOT", root),
                mock.patch.object(check_shell_snapshot, "adb_path") as adb,
            ):
                with self.assertRaises((RuntimeError, ValueError)):
                    check_shell_snapshot.main(["--serial", "device"])
                adb.assert_not_called()
                self.assertFalse((root / "testresults").exists())

    def test_failed_evidence_marks_overall_run_failed_after_raw_success(self):
        for observation in [
            {"result": "passed", "diagnostics": [{"exit_code": 1}]},
            {
                "result": "passed",
                "diagnostics": [{"exit_code": 0}],
                "evidence_errors": [{"path": "stdout.xml", "error": "unwritable"}],
            },
        ]:
            with self.subTest(observation=observation):
                self.assert_overall_evidence_failure(observation)

    def assert_overall_evidence_failure(self, observation):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            apk = root / "debug.apk"
            apk.write_bytes(b"synthetic APK")
            digest = hashlib.sha256(apk.read_bytes()).hexdigest()

            def run(argv, **kwargs):
                if argv == ["adb", "devices"]:
                    value = b"List of devices attached\ndevice\tdevice\n"
                elif "pm" in argv:
                    value = b"package:/data/app/owned/base.apk\n"
                elif "sha256sum" in argv:
                    value = (digest + "  /data/app/owned/base.apk\n").encode()
                else:
                    self.assertEqual(["adb", "-s", "device", "shell", "id", "-u"], argv)
                    value = b"2000\n"
                return subprocess.CompletedProcess(argv, 0, value, b"")

            output = io.StringIO()
            with (
                mock.patch.object(check_shell_snapshot, "ROOT", root),
                mock.patch.object(check_shell_snapshot, "adb_path", return_value="adb"),
                mock.patch.object(check_shell_snapshot.subprocess, "run", side_effect=run),
                mock.patch.object(
                    check_shell_snapshot, "observe_snapshot", return_value=observation
                ),
                contextlib.redirect_stdout(output),
            ):
                code = check_shell_snapshot.main(
                    ["--serial", "device", "--apk", str(apk), "--version", "test"]
                )
            self.assertEqual(1, code)
            self.assertTrue(output.getvalue().startswith("FAILED:"))
            meta = json.loads(
                next((root / "testresults").rglob("run.json")).read_text(encoding="utf-8")
            )
            self.assertEqual("failed", meta["result"])
            self.assertEqual("passed", meta["observation_result"])
            self.assertIn("evidence_error", meta)

    def test_identity_guards_prevent_raw_process(self):
        for failure in ["device", "apk", "uid"]:
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                apk = root / "debug.apk"
                apk.write_bytes(b"synthetic APK")
                digest = hashlib.sha256(apk.read_bytes()).hexdigest()
                calls = []

                def run(argv, **kwargs):
                    calls.append(argv)
                    if argv == ["adb", "devices"]:
                        value = (
                            b"List of devices attached\n"
                            if failure == "device"
                            else b"List of devices attached\ndevice\tdevice\n"
                        )
                    elif "pm" in argv:
                        value = b"package:/data/app/owned/base.apk\n"
                    elif "sha256sum" in argv:
                        value = (
                            ("0" * 64 if failure == "apk" else digest)
                            + "  /data/app/owned/base.apk\n"
                        ).encode()
                    else:
                        self.assertEqual(["adb", "-s", "device", "shell", "id", "-u"], argv)
                        value = b"0\n" if failure == "uid" else b"2000\n"
                    return subprocess.CompletedProcess(argv, 0, value, b"")

                with (
                    mock.patch.object(check_shell_snapshot, "ROOT", root),
                    mock.patch.object(check_shell_snapshot, "adb_path", return_value="adb"),
                    mock.patch.object(check_shell_snapshot.subprocess, "run", side_effect=run),
                    mock.patch.object(check_shell_snapshot, "observe_snapshot") as observe,
                    contextlib.redirect_stdout(io.StringIO()),
                ):
                    self.assertEqual(
                        1,
                        check_shell_snapshot.main(
                            ["--serial", "device", "--apk", str(apk), "--version", "test"]
                        ),
                    )
                observe.assert_not_called()
                self.assertFalse(any("app_process" in command for command in calls))
                meta = json.loads(
                    next((root / "testresults").rglob("run.json")).read_text(encoding="utf-8")
                )
                self.assertEqual("failed", meta["result"])

    def test_missing_local_apk_prevents_adb_and_output(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            with (
                mock.patch.object(check_shell_snapshot, "ROOT", root),
                mock.patch.object(check_shell_snapshot, "adb_path") as adb,
            ):
                with self.assertRaises(RuntimeError):
                    check_shell_snapshot.main(
                        [
                            "--serial",
                            "device",
                            "--apk",
                            str(root / "missing.apk"),
                            "--version",
                            "test",
                        ]
                    )
                adb.assert_not_called()
                self.assertFalse((root / "testresults").exists())

    def test_write_failure_preserves_primary_error_and_other_diagnostics(self):
        write = Path.write_bytes

        def fail_one(path, data):
            if path.name == "stdout.xml":
                raise OSError("Synthetic write failure")
            return write(path, data)

        with mock.patch.object(Path, "write_bytes", fail_one):
            folder, record, calls = self.observe(
                subprocess.CompletedProcess([], 137, b"partial", b"Killed")
            )
        self.assertEqual("failed", record["result"])
        self.assertEqual("Shell snapshot process failed", record["error"])
        self.assertEqual("stdout.xml", record["evidence_errors"][0]["path"])
        self.assertEqual(4, len(calls))
        self.assertTrue((folder / "stderr.txt").is_file())
        self.assertTrue((folder / "logcat.txt").is_file())
        self.assertTrue((folder / "window.txt").is_file())
        self.assertTrue((folder / "processes.txt").is_file())


if __name__ == "__main__":
    unittest.main()
