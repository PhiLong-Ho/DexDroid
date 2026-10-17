import contextlib
import copy
import io
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import coverage
import release
import release_report
import run_tool_tests


class ReleaseReportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.output, self.evidence = (
            self.root / "out",
            self.root / "testresults/0.10.2/runs/fixture",
        )
        self.output.mkdir()
        (self.evidence / "host").mkdir(parents=True)
        (self.root / "docs").mkdir()
        (self.root / "tools/tests").mkdir(parents=True)
        (self.root / "tools/tests/test_example.py").write_text(
            "class Example:\n    def test_case(self): pass\n", encoding="utf-8"
        )
        (self.root / "SPEC.md").write_text(
            "## Functional Requirements\n"
            + "".join(f"- **FR-{i:03}**: Example\n" for i in range(1, 5)),
            encoding="utf-8",
        )
        requirements = [
            {
                "id": f"DX-EXAMPLE-{i:03}",
                "criterion": f"Example {i}",
                "spec_requirement": f"FR-{i:03}",
                "spec_section": "Functional Requirements",
                "coverage": status,
                "gap": "" if status == "mapped" else "Unrun | device\nscenario",
                "dimensions": ["surface"],
                "suites": ["example"] if i < 3 else [],
                "checks": ["tools/tests/test_example.py::Example"] if i < 3 else [],
            }
            for i, status in enumerate(("mapped", "partial", "manual", "deferred"), 1)
        ]
        (self.root / "docs/requirements.json").write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "dimensions": {"surface": ["phone", "monitor"]},
                    "suites": {"example": {"entry": "tools/tests/test_example.py"}},
                    "requirements": requirements,
                }
            ),
            encoding="utf-8",
        )
        coverage.update(self.root)
        self.metadata = {
            "version": "0.10.2",
            "version_code": 17,
            "tag": "v0.10.2",
            "repository": "owner/repo",
            "source_commit": "a" * 40,
            "verified": True,
        }
        (self.output / "build.json").write_text(json.dumps(self.metadata), encoding="utf-8")
        for ext in ("apk", "aab"):
            (self.output / f"DexDroid-0.10.2.{ext}").write_bytes(b"fixture")
        self.tool_path = self.evidence / "host/tool-tests.json"
        self.tooling = {
            "schema_version": 1,
            "source_commit": "a" * 40,
            "cases": [
                {"id": name, "status": status}
                for name, status in [
                    ("pass", "passed"),
                    ("skip", "skipped"),
                    ("expected", "expected_failure"),
                ]
            ],
        }
        self.write_tools()
        self.xml = self.root / "app/build/test-results/testDebugUnitTest/TEST-example.xml"
        self.xml.parent.mkdir(parents=True)
        self.xml.write_text(
            '<testsuite name="Example" tests="3" failures="0" errors="0" skipped="1">'
            '<testcase classname="Example" name="one"/><testcase classname="Example" name="two"/>'
            '<testcase classname="Example" name="three"><skipped/></testcase></testsuite>',
            encoding="utf-8",
        )

    def write_tools(self):
        self.tool_path.write_text(json.dumps(self.tooling), encoding="utf-8")

    def generate(self):
        return release_report.generate(
            self.root, self.output, self.evidence, "https://github.com/owner/repo/actions/runs/42"
        )

    def test_report_has_measured_rates_coverage_gaps_and_pinned_links(self):
        data = self.generate()
        report = (self.output / "release-report.md").read_text(encoding="utf-8")
        self.assertIn("| Combined host | 6 | 3 | 0 | 0 | 2 | 1 | 0 | 50.0% |", report)
        self.assertIn("1 mapped, 1 partial, 1 manual, 1 deferred", report)
        self.assertIn("Unrun &#124; device<br>scenario", report)
        for path in [
            "releases/tag/v0.10.2",
            "CHANGELOG.md#0102",
            "actions/runs/42",
            "release-report.json",
        ]:
            self.assertIn(path, report)
        self.assertIn("blob/" + "a" * 40, report)
        self.assertIn("Physical phone/monitor acceptance | Not run", report)
        self.assertEqual(4, len(data["requirements"]))
        self.assertEqual(report, (self.evidence / "release-report.md").read_text(encoding="utf-8"))
        self.assertEqual(
            5, len((self.output / "SHA256SUMS.txt").read_text(encoding="utf-8").splitlines())
        )
        release_report.validate_package(self.output, self.metadata)

    def test_missing_wrong_commit_or_invalid_tool_evidence_fails(self):
        self.tool_path.unlink()
        with self.assertRaises(OSError):
            self.generate()
        self.tooling["source_commit"] = "b" * 40
        self.write_tools()
        with self.assertRaisesRegex(ValueError, "source/schema mismatch"):
            self.generate()
        self.tooling["source_commit"] = "a" * 40
        self.tooling["cases"][0]["status"] = "unknown"
        self.write_tools()
        with self.assertRaisesRegex(ValueError, "Invalid test outcome"):
            self.generate()

    def test_empty_duplicate_failed_or_all_skipped_required_suites_fail(self):
        data = self.generate()
        for cases in [
            [],
            [{"id": "x", "status": "passed"}] * 2,
            [{"id": "x", "status": status} for status in ["failed"]],
            [{"id": "x", "status": "error"}],
            [{"id": "x", "status": "skipped"}],
            [{"id": "x", "status": "unexpected_success"}],
        ]:
            for suite in ("jvm", "tooling"):
                changed = copy.deepcopy(data)
                changed["tests"][suite]["cases"] = cases
                with self.subTest(cases=cases, suite=suite), self.assertRaises(ValueError):
                    release_report.validate(changed, self.metadata)

    def test_missing_or_malformed_junit_cannot_be_a_zero_case_pass(self):
        original = self.xml.read_bytes()
        for content in [
            b"<bad/>",
            b"<testsuite tests='0'/>",
            b"<broken",
            original.replace(b'tests="3"', b'tests="4"'),
        ]:
            self.xml.write_bytes(content)
            with (
                self.subTest(content=content),
                self.assertRaises((ValueError, release_report.ET.ParseError)),
            ):
                self.generate()
        self.xml.unlink()
        with self.assertRaisesRegex(ValueError, "Missing/duplicate test cases"):
            self.generate()

    def test_junit_failure_error_skip_counts_are_derived_and_validated(self):
        self.xml.write_text(
            '<testsuite name="suite" tests="3" failures="1" errors="1" skipped="1">'
            '<testcase name="fail"><failure/></testcase><testcase name="error"><error/></testcase>'
            '<testcase name="skip"><skipped/></testcase></testsuite>',
            encoding="utf-8",
        )
        cases = release_report.junit_results([self.xml])["cases"]
        self.assertEqual(["failed", "error", "skipped"], [case["status"] for case in cases])
        with self.assertRaisesRegex(ValueError, "Unsuccessful required jvm"):
            self.generate()
        self.xml.write_bytes(self.xml.read_bytes().replace(b'failures="1"', b'failures="0"'))
        with self.assertRaisesRegex(ValueError, "outcome count mismatch"):
            self.generate()

    def test_stale_coverage_or_unverified_artifacts_prevent_report(self):
        (self.root / "tools/tests/test_example.py").write_text(
            "class Example:\n    def test_case(self): pass\n# changed\n", encoding="utf-8"
        )
        with self.assertRaisesRegex(ValueError, "Stale"):
            self.generate()
        self.metadata["verified"] = False
        (self.output / "build.json").write_text(json.dumps(self.metadata), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "verified release"):
            self.generate()

    def test_mismatched_versions_workflow_or_rendered_report_are_rejected(self):
        data = self.generate()
        for key, value in [
            ("version", "0.9"),
            ("source_commit", "b" * 40),
            ("workflow_url", "https://example.com/42"),
        ]:
            changed = copy.deepcopy(data)
            changed[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                release_report.validate(changed, self.metadata)
        for suite in ("jvm", "tooling"):
            changed = copy.deepcopy(data)
            changed["tests"][suite]["sources"] = []
            with (
                self.subTest(suite=suite),
                self.assertRaisesRegex(ValueError, "source fingerprints"),
            ):
                release_report.validate(changed, self.metadata)
        (self.output / "release-report.md").write_text("100% device coverage", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "does not match"):
            release_report.validate_package(self.output, self.metadata)

    def test_release_workflow_persists_structured_outcomes_then_generates_report(self):
        workflow = (release_report.ROOT / ".github/workflows/release.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn('run_tool_tests.py --output "$EVIDENCE_DIR/host/tool-tests.json"', workflow)
        self.assertLess(
            workflow.index("release.py package"), workflow.index("release_report.py --evidence")
        )
        self.assertLess(
            workflow.index("release_report.py --evidence"), workflow.index("name: android-release")
        )
        self.assertIn("GITHUB_STEP_SUMMARY", workflow)

    def test_generated_report_flows_through_publication_with_checksums_and_note_link(self):
        self.metadata["prerelease"] = False
        (self.output / "build.json").write_text(json.dumps(self.metadata), encoding="utf-8")
        self.generate()
        (self.output / "release-notes.md").write_text("fixture notes", encoding="utf-8")
        (self.evidence / "source-commit.txt").write_text(
            self.metadata["source_commit"] + "\n", encoding="utf-8"
        )
        (self.evidence / "host/quality.txt").write_bytes(b"Synthetic fixture only")
        junit = self.evidence / "host/test-results/testDebugUnitTest"
        junit.mkdir(parents=True)
        shutil.copyfile(self.xml, junit / self.xml.name)
        with mock.patch.object(release, "run", return_value=self.metadata["source_commit"]):
            release.archive_release(self.root, self.output, self.evidence)
        api = subprocess.CompletedProcess([], 0, "[[]]", "")
        with (
            mock.patch.object(release.subprocess, "run", return_value=api),
            mock.patch.object(release, "run") as commands,
        ):
            release.publish(self.output)
        create = commands.call_args_list[0].args[0]
        for name in (
            "release-report.md",
            "release-report.json",
            "SHA256SUMS.txt",
            *release.EVIDENCE_FILES,
        ):
            self.assertIn(str(self.output / name), create)
        self.assertIn("--draft=false", commands.call_args_list[-1].args[0])


class StructuredToolResultsTests(unittest.TestCase):
    def run_fixture(self, case_type):
        with tempfile.TemporaryDirectory() as name, contextlib.redirect_stderr(io.StringIO()):
            path = Path(name) / "results.json"
            code = run_tool_tests.run_tests(
                unittest.defaultTestLoader.loadTestsFromTestCase(case_type), path, "a" * 40
            )
            return code, json.loads(path.read_text(encoding="utf-8"))

    def test_pass_skip_expected_and_unexpected_success_are_distinct(self):
        class Fixture(unittest.TestCase):
            def test_pass(self):
                pass

            @unittest.skip("fixture")
            def test_skip(self):
                pass

            @unittest.expectedFailure
            def test_expected(self):
                self.fail("fixture")

            @unittest.expectedFailure
            def test_unexpected(self):
                pass

        code, data = self.run_fixture(Fixture)
        self.assertEqual(1, code)
        self.assertEqual(
            {"passed", "skipped", "expected_failure", "unexpected_success"},
            {case["status"] for case in data["cases"]},
        )
        self.assertEqual("a" * 40, data["source_commit"])

    def test_multiple_failed_subtests_count_one_parent_case(self):
        class Fixture(unittest.TestCase):
            def test_subtests(self):
                for number in range(2):
                    with self.subTest(number=number):
                        self.fail("fixture")

        code, data = self.run_fixture(Fixture)
        self.assertEqual(1, code)
        self.assertEqual(1, len(data["cases"]))
        self.assertEqual("failed", data["cases"][0]["status"])

    def test_empty_discovery_fails_instead_of_reporting_success(self):
        class Fixture(unittest.TestCase):
            pass

        code, data = self.run_fixture(Fixture)
        self.assertEqual(1, code)
        self.assertEqual([], data["cases"])

    def test_successful_fixture_returns_zero_and_does_not_launch_external_commands(self):
        class Fixture(unittest.TestCase):
            def test_pass(self):
                pass

        with mock.patch.object(run_tool_tests.subprocess, "check_output") as command:
            code, data = self.run_fixture(Fixture)
        self.assertEqual(0, code)
        self.assertEqual("passed", data["cases"][0]["status"])
        command.assert_not_called()
