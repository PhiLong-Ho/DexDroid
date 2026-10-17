import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_privacy


class PrivacyTests(unittest.TestCase):
    def test_raw_screenshots_logs_and_metadata_cannot_be_tracked(self):
        for name in [
            "testresults/old/app.png",
            "testresults/old/logcat.txt",
            "testresults/old/run.json",
        ]:
            with self.subTest(name=name):
                self.assertTrue(check_privacy.tracked_errors([name]))

    def test_safe_summaries_and_application_assets_are_allowed(self):
        self.assertEqual(
            [],
            check_privacy.tracked_errors(
                [
                    "testresults/README.md",
                    "testresults/v1/archive.json",
                    "assets/icon.png",
                    "app/src/main/res/drawable/logo.xml",
                ]
            ),
        )

    def test_public_host_rejects_media_nested_archives_and_device_metadata(self):
        for name, data in [
            ("app.png", b"image"),
            ("host/data.zip", b"archive"),
            ("run.json", b"{}"),
            ("device/log.txt", b"personal"),
        ]:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temp:
                folder = Path(temp)
                path = folder / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
                self.assertTrue(check_privacy.public_errors(folder))

    def test_renamed_capture_is_rejected_by_signature(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            (folder / "host.txt").write_bytes(b"\x89PNG\r\n\x1a\nprivate")
            self.assertTrue(check_privacy.public_errors(folder))

    def test_current_host_reports_pass_without_uploading_other_runs(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            folder = base / "current"
            folder.mkdir()
            (folder / "source-commit.txt").write_text("fixture", encoding="utf-8")
            (base / "private.png").write_bytes(b"private")
            self.assertEqual([], check_privacy.public_errors(folder))

    def test_empty_and_linked_public_inputs_fail(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            self.assertTrue(check_privacy.public_errors(folder))
            try:
                (folder / "linked").symlink_to(folder, target_is_directory=True)
            except OSError as error:
                self.skipTest(f"Symlink privilege unavailable: {error}")
            self.assertTrue(check_privacy.public_errors(folder))

    def test_repository_guard_checks_new_index_paths_and_ignored_local_capture(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            (root / "testresults").mkdir()
            (root / ".gitignore").write_text("private/\n", encoding="utf-8")
            (root / "private").mkdir()
            (root / "private/app.png").write_bytes(b"private")
            self.assertEqual([], check_privacy.repository_errors(root))
            (root / "testresults/app.png").write_bytes(b"private")
            subprocess.run(["git", "add", "testresults/app.png"], cwd=root, check=True)
            self.assertTrue(check_privacy.repository_errors(root))

    def test_review_candidate_and_committed_refs_reject_raw_paths(self):
        import review_gate

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            (root / "testresults").mkdir()
            (root / "testresults/app.png").write_bytes(b"private")
            subprocess.run(["git", "add", "testresults/app.png"], cwd=root, check=True)
            with self.assertRaisesRegex(ValueError, "Raw evidence"):
                review_gate.candidate(root)
            subprocess.run(
                [
                    "git",
                    "-c",
                    "user.name=Fixture",
                    "-c",
                    "user.email=fixture@example.invalid",
                    "commit",
                    "-qm",
                    "fixture",
                ],
                cwd=root,
                check=True,
            )
            with self.assertRaisesRegex(ValueError, "Raw evidence"):
                review_gate.candidate(root, "HEAD")


class WithdrawalTests(unittest.TestCase):
    def inspect(self, manifest, retained=None):
        import json

        import check_documentation

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "testresults").mkdir()
            if retained is not None:
                (root / "testresults/retained.txt").write_bytes(retained)
            (root / "testresults/manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            return check_documentation.check_evidence(root)

    def withdrawal(self):
        return {
            "schema_version": 2,
            "files": [],
            "withdrawal": {
                "status": "privacy-withdrawn",
                "date": "2026-10-17",
                "reason": "owner privacy removal",
                "scope": "historical evidence",
            },
        }

    def test_explicit_withdrawal_allows_empty_without_claiming_retained_evidence(self):
        self.assertEqual([], self.inspect(self.withdrawal()))

    def test_missing_or_malformed_withdrawal_fails(self):
        data = self.withdrawal()
        for key in ["status", "date", "reason", "scope"]:
            value = dict(data, withdrawal=dict(data["withdrawal"]))
            value["withdrawal"].pop(key)
            self.assertTrue(self.inspect(value))
        data["withdrawal"]["date"] = "not-a-date"
        self.assertTrue(self.inspect(data))

    def test_schema_one_empty_remains_invalid(self):
        self.assertTrue(self.inspect({"schema_version": 1, "files": []}))

    def test_retained_hash_missing_duplicate_and_escape_still_fail(self):
        import hashlib

        data = self.withdrawal()
        data["files"] = [
            {
                "path": "retained.txt",
                "status": "historical",
                "sha256": hashlib.sha256(b"safe").hexdigest(),
            }
        ]
        self.assertEqual([], self.inspect(data, b"safe"))
        self.assertTrue(self.inspect(data, b"changed"))
        self.assertTrue(self.inspect(data))
        data["files"] *= 2
        self.assertTrue(self.inspect(data, b"safe"))
        data["files"][0]["path"] = "../outside.txt"
        self.assertTrue(self.inspect(data))
