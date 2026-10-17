import copy
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_documentation
import check_requirements
import run_device_checks
import visual_compare


class InstrumentationResultTests(unittest.TestCase):
    def test_success_requires_android_success_code_and_actual_pass(self):
        output = (
            "INSTRUMENTATION_RESULT: stream=PASS ready\nPASS gesture\nINSTRUMENTATION_CODE: -1\n"
        )
        self.assertTrue(run_device_checks.instrumentation_passed(output, 0))

    def test_adb_success_does_not_hide_android_failure(self):
        output = "INSTRUMENTATION_RESULT: stream=PASS ready\nFAIL broken\nINSTRUMENTATION_CODE: 0\n"
        self.assertFalse(run_device_checks.instrumentation_passed(output, 0))

    def test_missing_final_status_crash_and_no_checks_fail(self):
        for output in [
            "PASS ready\n",
            "Process crashed\nINSTRUMENTATION_CODE: -1\n",
            "INSTRUMENTATION_CODE: -1\n",
            "INSTRUMENTATION_FAILED: missing runner",
        ]:
            with self.subTest(output=output):
                self.assertFalse(run_device_checks.instrumentation_passed(output, 0))

    def test_nonzero_adb_exit_fails_even_with_success_text(self):
        self.assertFalse(
            run_device_checks.instrumentation_passed("PASS ready\nINSTRUMENTATION_CODE: -1", 1)
        )

    def test_requires_exact_final_code(self):
        self.assertFalse(
            run_device_checks.instrumentation_passed("PASS ready\nINSTRUMENTATION_CODE: -10", 0)
        )


class DeviceSelectionTests(unittest.TestCase):
    def test_single_ready_device_is_selected(self):
        self.assertEqual(
            "phone",
            run_device_checks.select_device("List of devices attached\nphone\tdevice\n", None),
        )

    def test_multiple_devices_need_explicit_selection(self):
        output = "List of devices attached\none\tdevice\ntwo\tdevice\n"
        with self.assertRaises(RuntimeError):
            run_device_checks.select_device(output, None)
        self.assertEqual("two", run_device_checks.select_device(output, "two"))

    def test_unauthorized_offline_missing_and_wrong_device_fail(self):
        for state in ["unauthorized", "offline", ""]:
            with self.subTest(state=state), self.assertRaises(RuntimeError):
                run_device_checks.select_device(
                    f"List of devices attached\nphone\t{state}\n", "phone"
                )


class RunnerEvidenceTests(unittest.TestCase):
    def run_fixture(
        self,
        output,
        suite="all",
        timeout=False,
        interrupt=False,
        monitor_fault=False,
        restore_interrupt=False,
        async_monitor_fault=False,
    ):
        root = Path(self.temp.name)
        (root / "docs").mkdir(exist_ok=True)
        (root / "docs/requirements.json").write_text(
            (check_requirements.ROOT / "docs/requirements.json").read_text(encoding="utf-8"),
            encoding="utf-8",
        )
        apk = root / "fixture.apk"
        apk.write_bytes(b"synthetic-apk")
        digest = hashlib.sha256(apk.read_bytes()).hexdigest()
        calls = []

        def fake_command(argv, timeout=60, **kwargs):
            calls.append(argv)
            stdout = ""
            if argv[-1] == "devices":
                stdout = "List of devices attached\nfixture\tdevice\n"
            elif "path" in argv and "pm" in argv:
                stdout = "package:/data/app/fixture/base.apk\n"
            elif "sha256sum" in argv:
                stdout = digest + "  /data/app/fixture/base.apk\n"
            elif "instrument" in argv:
                if interrupt:
                    raise KeyboardInterrupt
                if self.timeout_fixture:
                    raise subprocess.TimeoutExpired(
                        argv, timeout, output=b"PASS ready\n", stderr=b"stderr before timeout\n"
                    )
                stdout = output
            elif "dev.dexdroid/.MainActivity" in argv and restore_interrupt:
                raise KeyboardInterrupt
            return subprocess.CompletedProcess(argv, 0, stdout, "")

        self.timeout_fixture = timeout
        original_phase = run_device_checks.run_monitor.Session.phase

        def phase(instance, name):
            if async_monitor_fault and name == "collecting_logcat":
                instance.error = "status_write_failed"
            if monitor_fault and name == "restoring_controller":
                raise OSError("fixture monitor write failure")
            return original_phase(instance, name)

        with (
            mock.patch.object(run_device_checks.run_monitor.Session, "phase", phase),
            mock.patch.object(run_device_checks, "ROOT", root),
            mock.patch.object(run_device_checks, "APK", apk),
            mock.patch.object(run_device_checks, "TEST_APK", apk),
            mock.patch.object(run_device_checks, "adb_path", return_value="fixture-adb"),
            mock.patch.object(run_device_checks, "command", side_effect=fake_command),
            mock.patch.object(
                sys, "argv", ["runner", "--suite", suite, "--version", "0.10-fixture"]
            ),
        ):
            code = run_device_checks.main()
        records = list((root / "testresults/0.10-fixture/runs").glob("*/run.json"))
        self.assertEqual(1, len(records))
        return code, json.loads(records[0].read_text(encoding="utf-8")), calls, records[0].parent

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)

    def test_failed_suite_is_recorded_stops_all_and_restores_controller(self):
        code, report, calls, folder = self.run_fixture(
            "PASS ready\nFAIL contact\nINSTRUMENTATION_CODE: 0\n"
        )
        self.assertEqual(1, code)
        self.assertEqual("failed", report["result"])
        self.assertEqual(1, len(report["results"]))
        self.assertEqual(1, sum("instrument" in call for call in calls))
        self.assertTrue((folder / "input/instrumentation.txt").is_file())
        self.assertTrue((folder / "input/logcat.txt").is_file())
        self.assertTrue(any("dev.dexdroid/.MainActivity" in call for call in calls))

    def test_success_records_hashes_and_keeps_visual_review_unreviewed(self):
        code, report, calls, folder = self.run_fixture(
            "PASS icon\nINSTRUMENTATION_CODE: -1\n", suite="icon"
        )
        self.assertEqual(0, code)
        self.assertEqual("passed", report["result"])
        self.assertEqual("unreviewed", report["visual_review"])
        self.assertEqual("0.10-fixture", report["app_version"])
        self.assertEqual(2, len(report["apk_hashes"]))
        self.assertEqual(1, report["results"][0]["pass_lines"])
        invocation = next(call for call in calls if "instrument" in call)
        self.assertIn("-w", invocation)
        self.assertIn("-r", invocation, "raw output must retain Android's final result code")

    def test_full_suite_and_supervisor_have_distinct_evidence_namespaces(self):
        code, report, calls, folder = self.run_fixture(
            "PASS native fixture\nINSTRUMENTATION_CODE: -1\n", suite="all"
        )
        self.assertEqual(0, code)
        self.assertEqual("passed", report["result"])
        self.assertEqual(11, len(report["results"]))
        self.assertEqual(11, sum("instrument" in call for call in calls))
        self.assertTrue((folder / "monitor/instrumentation.txt").is_file())
        state = run_device_checks.run_monitor.read_status(folder / "supervisor")
        self.assertEqual("completed", state["state"])
        self.assertEqual("completed", state["restoration"])

    def test_timeout_retains_output_and_does_not_run_later_suites(self):
        code, report, calls, folder = self.run_fixture("", timeout=True)
        self.assertEqual(1, code)
        self.assertEqual("timeout", report["results"][0]["result"])
        self.assertIn(
            "PASS ready", (folder / "input/instrumentation.txt").read_text(encoding="utf-8")
        )
        self.assertIn(
            "stderr before timeout",
            (folder / "input/instrumentation.txt").read_text(encoding="utf-8"),
        )
        self.assertEqual(1, sum("instrument" in call for call in calls))
        self.assertTrue(any("force-stop" in call for call in calls))

    def test_interrupt_never_records_pass_and_still_restores_controller(self):
        code, report, calls, folder = self.run_fixture("", interrupt=True)
        self.assertEqual(130, code)
        self.assertEqual("cancelled", report["result"])
        self.assertEqual("cancelled", report["results"][0]["result"])
        self.assertTrue(any("force-stop" in call for call in calls))
        self.assertTrue(any("dev.dexdroid/.MainActivity" in call for call in calls))
        monitor = json.loads((folder / "supervisor/status.json").read_text(encoding="utf-8"))
        self.assertEqual("cancelled", monitor["state"])
        self.assertEqual("completed", monitor["restoration"])

    def test_monitor_failure_does_not_skip_controller_restore_or_record_pass(self):
        code, report, calls, folder = self.run_fixture(
            "PASS icon\nINSTRUMENTATION_CODE: -1\n", suite="icon", monitor_fault=True
        )
        self.assertEqual(1, code)
        self.assertEqual("failed", report["result"])
        self.assertTrue(any("dev.dexdroid/.MainActivity" in call for call in calls))
        state = run_device_checks.run_monitor.read_status(folder / "supervisor")
        self.assertEqual("failed", state["state"])
        self.assertEqual("status_write_failed", state["monitor_error"])

    def test_post_suite_monitor_fault_invalidates_run_but_restores_controller(self):
        code, report, calls, folder = self.run_fixture(
            "PASS icon\nINSTRUMENTATION_CODE: -1\n", suite="icon", async_monitor_fault=True
        )
        self.assertEqual(1, code)
        self.assertEqual("failed", report["result"])
        self.assertEqual("status_write_failed", report["monitor_error"])
        self.assertEqual("passed", report["results"][0]["result"])
        self.assertTrue(any("dev.dexdroid/.MainActivity" in call for call in calls))
        state = run_device_checks.run_monitor.read_status(folder / "supervisor")
        self.assertEqual("failed", state["state"])
        self.assertEqual("completed", state["restoration"])

    def test_live_device_command_deadline_retains_both_byte_streams(self):
        session = run_device_checks.run_monitor.Session(Path(self.temp.name) / "command-monitor")
        try:
            with self.assertRaises(subprocess.TimeoutExpired) as caught:
                run_device_checks.command(
                    [
                        sys.executable,
                        "-c",
                        "import os,time;os.write(1,b'PASS ready\\n');os.write(2,b'partial stderr');time.sleep(30)",
                    ],
                    timeout=0.5,
                    monitor=session,
                )
            self.assertEqual(b"PASS ready\n", caught.exception.stdout)
            self.assertEqual(b"partial stderr", caught.exception.stderr)
        finally:
            session.finish()

    def test_restore_interrupt_cannot_report_completed_restoration(self):
        code, report, calls, folder = self.run_fixture(
            "PASS icon\nINSTRUMENTATION_CODE: -1\n", suite="icon", restore_interrupt=True
        )
        self.assertEqual(130, code)
        self.assertEqual("cancelled", report["result"])
        self.assertEqual("KeyboardInterrupt", report["restore_error"])
        state = run_device_checks.run_monitor.read_status(folder / "supervisor")
        self.assertEqual("failed", state["restoration"])


class TraceabilityTests(unittest.TestCase):
    def setUp(self):
        self.root = check_requirements.ROOT
        self.data = json.loads((self.root / "docs/requirements.json").read_text(encoding="utf-8"))

    def test_real_requirement_links_resolve(self):
        self.assertEqual([], check_requirements.validate(self.data, self.root))

    def test_duplicate_requirements_fail(self):
        self.data["requirements"].append(copy.deepcopy(self.data["requirements"][0]))
        self.assertTrue(
            any("duplicate" in error for error in check_requirements.validate(self.data, self.root))
        )

    def test_stale_suite_and_spec_links_fail(self):
        self.data["suites"]["ui"]["entry"] = "no-longer-exists.java"
        self.data["requirements"][0]["spec_section"] = "Removed contract"
        errors = check_requirements.validate(self.data, self.root)
        self.assertTrue(any("suite entry" in error for error in errors))
        self.assertTrue(any("spec heading" in error for error in errors))

    def test_partial_coverage_requires_explicit_gap(self):
        self.data["requirements"][0]["gap"] = ""
        self.assertTrue(
            any(
                "explicit gap" in error
                for error in check_requirements.validate(self.data, self.root)
            )
        )

    def test_unknown_dimension_and_suite_fail(self):
        self.data["requirements"][0]["dimensions"] = ["unknown"]
        self.data["requirements"][0]["suites"] = ["unknown"]
        self.assertEqual(2, len(check_requirements.validate(self.data, self.root)))

    def test_execution_status_cannot_masquerade_as_coverage(self):
        self.data["requirements"][0]["coverage"] = "verified"
        self.assertTrue(check_requirements.validate(self.data, self.root))

    def test_missing_numbered_spec_requirement_fails(self):
        self.data["requirements"][0]["spec_requirement"] = "FR-999"
        self.assertTrue(
            any(
                "numbered spec" in error
                for error in check_requirements.validate(self.data, self.root)
            )
        )


class EvidenceVersionTests(unittest.TestCase):
    def test_literal_app_version_selects_version_folder(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            (root / "app").mkdir()
            (root / "app/build.gradle").write_text("    versionName '0.10'\n", encoding="utf-8")
            self.assertEqual("0.10", run_device_checks.result_version(root))

    def test_override_accepts_version_and_rejects_path_escape(self):
        self.assertEqual("0.11-dev", run_device_checks.result_version(Path("."), "0.11-dev"))
        for value in ["../secret", "a/b", "a\\b", ".", "", "/tmp"]:
            with self.subTest(value=value), self.assertRaises(RuntimeError):
                run_device_checks.result_version(Path("."), value)


class DocumentationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_relative_links_anchors_and_html_images_resolve(self):
        page = self.root / "README.md"
        page.write_text(
            '[Spec](SPEC.md#requirements)\n<img src="icon.png">\n[Web](https://example.com)\n',
            encoding="utf-8",
        )
        (self.root / "SPEC.md").write_text("# Spec\n## Requirements\n", encoding="utf-8")
        (self.root / "icon.png").write_bytes(b"image-fixture")
        self.assertEqual([], check_documentation.check_links(self.root, [page]))

    def test_missing_file_anchor_and_path_escape_fail(self):
        page = self.root / "README.md"
        page.write_text(
            "[Missing](gone.md)\n[Anchor](SPEC.md#gone)\n[Outside](../outside.md)\n",
            encoding="utf-8",
        )
        (self.root / "SPEC.md").write_text("# Spec\n", encoding="utf-8")
        self.assertEqual(3, len(check_documentation.check_links(self.root, [page])))

    def test_example_code_does_not_create_false_document_links(self):
        page = self.root / "README.md"
        page.write_text("```text\n[Example](not-a-real-file.md)\n```\n", encoding="utf-8")
        self.assertEqual([], check_documentation.check_links(self.root, [page]))

    def test_raw_evidence_changes_are_detected(self):
        folder = self.root / "testresults"
        folder.mkdir()
        log = folder / "record.txt"
        original = b"PASS native\r\n"
        log.write_bytes(original)
        manifest = {
            "files": [
                {
                    "path": "record.txt",
                    "sha256": hashlib.sha256(original).hexdigest(),
                    "status": "historical",
                }
            ]
        }
        (folder / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        self.assertEqual([], check_documentation.check_evidence(self.root))
        log.write_bytes(b"PASS native\n")
        self.assertTrue(
            any("hash changed" in error for error in check_documentation.check_evidence(self.root))
        )

    def test_real_spec_and_historical_evidence_are_valid(self):
        self.assertEqual([], check_documentation.check_spec(check_documentation.ROOT))
        self.assertEqual([], check_documentation.check_evidence(check_documentation.ROOT))


try:
    from PIL import Image
except ImportError:
    Image = None


@unittest.skipIf(Image is None, "Install tools/requirements-visual.txt for image tests")
class VisualComparisonTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.before, self.after = self.root / "before.png", self.root / "after.png"
        Image.new("RGB", (10, 10), "black").save(self.before)
        Image.new("RGB", (10, 10), "black").save(self.after)
        self.diff = self.root / "diff.png"

    def test_identical_images_pass_and_real_changes_fail(self):
        self.assertEqual(
            "passed", visual_compare.compare_image(self.before, self.after, {}, self.diff)["result"]
        )
        with Image.open(self.after) as img:
            img.putpixel((5, 5), (255, 255, 255))
            img.save(self.after)
        report = visual_compare.compare_image(self.before, self.after, {}, self.diff)
        self.assertEqual("failed", report["result"])
        self.assertAlmostEqual(0.01, report["changed_ratio"])

    def test_missing_or_wrong_size_capture_fails(self):
        with self.assertRaises(OSError):
            visual_compare.compare_image(self.before, self.root / "missing.png", {}, self.diff)
        Image.new("RGB", (20, 10), "black").save(self.after)
        with self.assertRaises(ValueError):
            visual_compare.compare_image(self.before, self.after, {}, self.diff)

    def test_mask_excludes_only_declared_dynamic_pixels(self):
        with Image.open(self.after) as img:
            img.putpixel((0, 0), (255, 255, 255))
            img.save(self.after)
        spec = {"masks": [{"rect": [0, 0, 1, 1], "reason": "clock"}]}
        report = visual_compare.compare_image(self.before, self.after, spec, self.diff)
        self.assertEqual("passed", report["result"])
        self.assertEqual(99, report["considered_pixels"])

    def test_total_mask_and_unexplained_mask_fail(self):
        for masks in [[{"rect": [0, 0, 10, 10], "reason": "all"}], [{"rect": [0, 0, 1, 1]}]]:
            with self.subTest(masks=masks), self.assertRaises(ValueError):
                visual_compare.compare_image(self.before, self.after, {"masks": masks}, self.diff)

    def test_unapproved_baseline_and_configuration_mismatch_fail(self):
        baseline = self.root / "manifest.json"
        baseline.write_text("{}", encoding="utf-8")
        with self.assertRaises(ValueError):
            visual_compare.compare(baseline, self.root, self.root / "out")
        data = {
            "approved_by": "test",
            "approved_utc": "test",
            "source_commit": "test",
            "configuration": {
                key: "test"
                for key in [
                    "device",
                    "os_build",
                    "resolution",
                    "dpi",
                    "rotation",
                    "font_scale",
                    "locale",
                    "ime",
                    "fixture",
                ]
            },
            "images": [{"path": "before.png"}],
        }
        baseline.write_text(json.dumps(data), encoding="utf-8")
        (self.root / "capture.json").write_text('{"configuration": {}}', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "configuration differs"):
            visual_compare.compare(baseline, self.root, self.root / "out")

    def test_path_escape_fails(self):
        with self.assertRaises(ValueError):
            visual_compare.image_path(self.root, "../private.png")

    def test_complete_report_is_self_contained_and_remains_unreviewed(self):
        approved = self.root / "approved"
        actual = self.root / "actual"
        approved.mkdir()
        actual.mkdir()
        Image.new("RGB", (10, 10), "black").save(approved / "menu.png")
        Image.new("RGB", (10, 10), "black").save(actual / "menu.png")
        config = {
            key: "test-fixture"
            for key in [
                "device",
                "os_build",
                "resolution",
                "dpi",
                "rotation",
                "font_scale",
                "locale",
                "ime",
                "fixture",
            ]
        }
        manifest = {
            "approved_by": "fixture-test",
            "approved_utc": "test",
            "source_commit": "test",
            "configuration": config,
            "images": [{"path": "menu.png"}],
        }
        (approved / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        (actual / "capture.json").write_text(
            json.dumps({"configuration": config}), encoding="utf-8"
        )
        output = self.root / "report"
        report = visual_compare.compare(approved / "manifest.json", actual, output)
        self.assertEqual("passed", report["result"])
        self.assertEqual("unreviewed", report["visual_review"])
        for path in ["index.html", "report.json", "0-before.png", "0-after.png", "0-diff.png"]:
            self.assertTrue((output / path).is_file())


if __name__ == "__main__":
    unittest.main()
