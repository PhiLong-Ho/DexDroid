import copy
import json
import shlex
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import coverage
import review_gate


class CoverageTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "docs").mkdir()
        (self.root / "tools/tests").mkdir(parents=True)
        (self.root / "SPEC.md").write_text(
            "## Functional Requirements\n- **FR-001**: Example\n", encoding="utf-8"
        )
        self.test = self.root / "tools/tests/test_example.py"
        self.test.write_text(
            "class ExampleTests:\n    def test_result(self):\n        assert True\n",
            encoding="utf-8",
            newline="\n",
        )
        self.data = {
            "schema_version": 1,
            "dimensions": {"surface": ["phone", "monitor"]},
            "suites": {"example": {"entry": "tools/tests/test_example.py"}},
            "requirements": [
                {
                    "id": "DX-EXAMPLE-001",
                    "criterion": "Observable example",
                    "spec_section": "Functional Requirements",
                    "spec_requirement": "FR-001",
                    "suites": ["example"],
                    "dimensions": ["surface"],
                    "coverage": "partial",
                    "gap": "Physical monitor not run",
                    "checks": ["tools/tests/test_example.py::ExampleTests"],
                }
            ],
        }
        self.save_map()

    def save_map(self):
        (self.root / "docs/requirements.json").write_text(json.dumps(self.data), encoding="utf-8")

    def test_report_retains_cases_dimensions_gaps_without_claiming_execution(self):
        coverage.update(self.root)
        coverage.check(self.root)
        report = (self.root / coverage.REPORT).read_text(encoding="utf-8")
        for text in [
            "DX-EXAMPLE-001",
            "ExampleTests.test_result",
            "surface: phone, monitor",
            "Physical monitor not run",
            "not execution results",
        ]:
            self.assertIn(text, report)

    def test_changed_assertion_requires_refresh_even_when_case_name_is_unchanged(self):
        coverage.update(self.root)
        self.test.write_text(self.test.read_text(encoding="utf-8") + "# new assertion\n")
        with self.assertRaisesRegex(ValueError, "Stale"):
            coverage.check(self.root)
        coverage.update(self.root)
        coverage.check(self.root)

    def test_added_test_requires_mapping_before_refresh(self):
        coverage.update(self.root)
        (self.root / "tools/tests/test_new.py").write_text(
            "class NewTests:\n    def test_added(self): pass\n", encoding="utf-8"
        )
        with self.assertRaisesRegex(ValueError, "Unmapped test entry"):
            coverage.update(self.root)

    def test_deleted_or_renamed_test_rejects_stale_reference(self):
        coverage.update(self.root)
        self.test.unlink()
        with self.assertRaisesRegex(ValueError, "missing test case"):
            coverage.check(self.root)

    def test_new_case_in_mapped_class_changes_inventory(self):
        coverage.update(self.root)
        with self.test.open("a", encoding="utf-8") as stream:
            stream.write("    def test_second(self): pass\n")
        with self.assertRaisesRegex(ValueError, "Stale"):
            coverage.check(self.root)
        coverage.update(self.root)
        self.assertIn("ExampleTests.test_second", coverage.generate(self.root)[0])

    def test_removed_helper_source_invalidates_snapshot(self):
        helper = self.root / "tools/tests/helper.py"
        helper.write_text("def helper(): pass\n", encoding="utf-8")
        coverage.update(self.root)
        helper.unlink()
        with self.assertRaisesRegex(ValueError, "Stale"):
            coverage.check(self.root)

    def test_inventory_handles_java_entrypoints_and_junit_without_helpers(self):
        path = self.root / "Example.java"
        path.write_text(
            '@Test\npublic void actualCase() {}\nString run() {return "";}\n'
            "public void onStart() {}\nprivate void helper() {}",
            encoding="utf-8",
        )
        self.assertEqual(["actualCase", "onStart", "run"], coverage.cases(path))

    def test_windows_line_endings_do_not_create_false_staleness(self):
        coverage.update(self.root)
        self.test.write_bytes(self.test.read_bytes().replace(b"\n", b"\r\n"))
        coverage.check(self.root)

    def test_inventory_is_identical_with_case_insensitive_path_comparison(self):
        class CaseInsensitivePath(type(self.root)):
            def __lt__(self, other):
                return self.as_posix().casefold() < other.as_posix().casefold()

        for name in ["Z_helper.py", "a_helper.py"]:
            (self.root / "tools/tests" / name).write_text("# Helper\n", encoding="utf-8")
        report, snapshot = coverage.generate(self.root)
        portable_report, portable_snapshot = coverage.generate(CaseInsensitivePath(self.root))
        self.assertEqual(report, portable_report)
        self.assertEqual(snapshot, portable_snapshot)
        self.assertEqual(
            ["tools/tests/Z_helper.py", "tools/tests/a_helper.py", "tools/tests/test_example.py"],
            list(json.loads(snapshot)["test_sources"]),
        )

    def test_real_repository_coverage_is_current(self):
        coverage.check(coverage.ROOT)


class ReviewGateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git("init", "-q")
        self.git("config", "user.email", "test@example.invalid")
        self.git("config", "user.name", "Test")
        (self.root / "source.txt").write_text("candidate\n", encoding="utf-8")
        self.git("add", ".")

    def git(self, *args):
        result = subprocess.run(
            ["git", *args], cwd=self.root, capture_output=True, text=True, encoding="utf-8"
        )
        self.assertEqual(0, result.returncode, result.stderr)
        return result.stdout.strip()

    def record(self):
        return {
            "schema_version": 1,
            "candidate_sha256": review_gate.candidate(self.root),
            "reviews": [
                {
                    "agent_id": ident,
                    "fresh_agent": True,
                    "verdict": "approve",
                    "scope": "Spec, implementation, tests and evidence",
                    "summary": "No blocking findings",
                    "reviewed_at": "2026-01-01T00:00:00Z",
                }
                for ident in ["agent-a", "agent-b"]
            ],
        }

    def commit_approved(self):
        path = self.root / review_gate.RECORD
        path.parent.mkdir()
        path.write_text(json.dumps(self.record()), encoding="utf-8")
        self.git("add", ".")
        self.git("commit", "-qm", "candidate")
        return self.git("rev-parse", "HEAD")

    def test_missing_single_duplicate_rejected_or_nonfresh_reviews_fail(self):
        good = self.record()
        invalid = []
        for reviews in [[], good["reviews"][:1], good["reviews"] + good["reviews"][:1]]:
            item = copy.deepcopy(good)
            item["reviews"] = reviews
            invalid.append(item)
        for key, value in [
            ("agent_id", "agent-a"),
            ("fresh_agent", False),
            ("verdict", "reject"),
            ("scope", ""),
            ("summary", ""),
            ("reviewed_at", "2999-01-01T00:00:00Z"),
            ("reviewed_at", "2026-01-01"),
        ]:
            item = copy.deepcopy(good)
            item["reviews"][1][key] = value
            invalid.append(item)
        for item in invalid:
            with self.subTest(item=item), self.assertRaises(ValueError):
                review_gate.validate(item, good["candidate_sha256"])

    def test_two_approvals_match_staged_and_committed_candidate(self):
        commit = self.commit_approved()
        review_gate.check(self.root)
        review_gate.check(self.root, commit)
        self.assertEqual(review_gate.candidate(self.root), review_gate.candidate(self.root, commit))

    def test_source_add_edit_delete_and_filemode_changes_invalidate_approvals(self):
        self.commit_approved()
        initial = review_gate.candidate(self.root)
        source = self.root / "source.txt"
        source.write_text("different\n", encoding="utf-8")
        self.git("add", ".")
        with self.assertRaisesRegex(ValueError, "stale"):
            review_gate.check(self.root)
        self.git("reset", "--hard", "HEAD")
        self.git("update-index", "--chmod=+x", "source.txt")
        self.assertNotEqual(initial, review_gate.candidate(self.root))
        self.git("reset", "--hard", "HEAD")
        source.unlink()
        self.git("add", ".")
        with self.assertRaisesRegex(ValueError, "stale"):
            review_gate.check(self.root)
        (self.root / "new.txt").write_text("new", encoding="utf-8")
        self.git("add", ".")
        self.assertNotEqual(initial, review_gate.candidate(self.root))

    def test_only_approval_record_is_excluded_from_digest(self):
        self.commit_approved()
        initial = review_gate.candidate(self.root)
        (self.root / review_gate.RECORD).write_text("{}", encoding="utf-8")
        self.git("add", ".")
        self.assertEqual(initial, review_gate.candidate(self.root))
        with self.assertRaises(ValueError):
            review_gate.check(self.root)
        (self.root / "reviews/other.json").write_text("{}", encoding="utf-8")
        self.git("add", ".")
        self.assertNotEqual(initial, review_gate.candidate(self.root))

    def test_pre_push_checks_actual_refs_including_annotated_tag(self):
        commit = self.commit_approved()
        self.git("tag", "-a", "v1.0", "-m", "release")
        tag = self.git("rev-parse", "v1.0")
        zeros = "0" * 40
        review_gate.pre_push(
            self.root,
            [
                f"refs/heads/main {commit} refs/heads/main {zeros}\n",
                f"refs/tags/v1.0 {tag} refs/tags/v1.0 {zeros}\n",
            ],
        )
        (self.root / "source.txt").write_text("unreviewed\n", encoding="utf-8")
        self.git("add", ".")
        self.git("commit", "-qm", "unreviewed")
        bad = self.git("rev-parse", "HEAD")
        with self.assertRaisesRegex(ValueError, "stale"):
            review_gate.pre_push(self.root, [f"refs/heads/main {bad} refs/heads/main {commit}"])
        # Checking the approved older ref is valid regardless of current HEAD.
        review_gate.pre_push(self.root, [f"refs/tags/v1.0 {tag} refs/tags/v1.0 {zeros}"])

    def test_missing_record_and_invalid_push_input_fail_closed(self):
        with self.assertRaises(ValueError):
            review_gate.check(self.root)
        for line in ["bad", "ref injected ref remote"]:
            with self.subTest(line=line), self.assertRaises(ValueError):
                review_gate.pre_push(self.root, [line])

    def test_ref_deletion_does_not_require_candidate(self):
        with mock.patch.object(review_gate, "check") as check:
            review_gate.pre_push(self.root, [f"ref {'0' * 40} ref {'a' * 40}"])
            check.assert_not_called()

    def test_installed_hook_blocks_real_push_then_allows_approved_candidate(self):
        # Match the real repository: imported helper bytecode is not candidate content.
        (self.root / ".gitignore").write_text("__pycache__/\n", encoding="utf-8")
        script = self.root / "tools/review_gate.py"
        script.parent.mkdir()
        script.write_bytes(Path(review_gate.__file__).read_bytes())
        script.with_name("check_privacy.py").write_bytes(
            Path(review_gate.check_privacy.__file__).read_bytes()
        )
        hook = self.root / ".git/hooks/pre-push"
        executable = shlex.quote(Path(sys.executable).as_posix())
        hook.write_text(
            f"#!/bin/sh\nexec {executable} tools/review_gate.py pre-push\n",
            encoding="utf-8",
            newline="\n",
        )
        hook.chmod(0o755)
        self.git("add", ".")
        self.git("commit", "-qm", "unreviewed candidate")
        with tempfile.TemporaryDirectory() as remote:
            self.git("init", "--bare", "-q", remote)
            self.git("remote", "add", "origin", remote)

            def push():
                return subprocess.run(
                    ["git", "push", "origin", "HEAD:refs/heads/candidate"],
                    cwd=self.root,
                    capture_output=True,
                    text=True,
                    encoding="utf-8",
                )

            denied = push()
            self.assertNotEqual(0, denied.returncode)
            self.assertIn("FAIL", denied.stderr)
            commit = self.commit_approved()
            allowed = push()
            self.assertEqual(0, allowed.returncode, allowed.stderr)
            self.assertEqual(
                commit, self.git("ls-remote", "origin", "refs/heads/candidate").split()[0]
            )

    def test_workflows_gate_quality_and_both_release_jobs(self):
        root = review_gate.ROOT
        quality = (root / ".github/workflows/quality.yml").read_text(encoding="utf-8")
        release = (root / ".github/workflows/release.yml").read_text(encoding="utf-8")
        self.assertIn("python tools/coverage.py check", quality)
        self.assertIn("python tools/coverage.py check", release)
        self.assertIn("python tools/review_gate.py check --revision HEAD", quality)
        self.assertEqual(2, release.count("python tools/review_gate.py check --revision HEAD"))
        self.assertLess(release.index("review_gate.py"), release.index("Restore private"))
        self.assertLess(release.rindex("review_gate.py"), release.index("release.py publish"))
