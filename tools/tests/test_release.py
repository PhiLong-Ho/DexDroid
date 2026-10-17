import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import release
from report_fixture import rehash, write_report


class ReleaseContractTests(unittest.TestCase):
    def test_matching_two_three_part_and_prerelease_versions(self):
        for version in ["0.10", "0.10.1", "0.11-rc.1"]:
            release.validate_tag("v" + version, version)

    def test_wrong_malformed_or_injected_tags_fail(self):
        for tag in ["v0.9", "0.10", "v0.10;echo x", "v../../x", "v0.10\nother", "v0"]:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release.validate_tag(tag, "0.10")

    def test_summary_is_version_scoped_and_preserves_wrapped_bullets(self):
        text = "# Changelog\n## [Unreleased]\n- Future\n## [0.10]\n### Changed\n- Current\n  continuation\n\n[Evidence](testresults/x)\n## [0.9]\n- Old\n"
        self.assertEqual(["Current continuation"], release.change_summary(text, "0.10"))

    def test_summary_rejects_missing_empty_or_duplicate_entries(self):
        for text in ["# Changelog", "## [0.10]\nNothing", "## [0.10]\n- A\n## [0.10]\n- B"]:
            with self.subTest(text=text), self.assertRaises(ValueError):
                release.change_summary(text, "0.10")

    def test_summary_is_short_and_accepts_dated_sections(self):
        text = "## [0.10] - 2026-10-15\n" + "\n".join(f"- Change {i}" for i in range(9))
        self.assertEqual(5, len(release.change_summary(text, "0.10")))

    def test_wrong_checked_out_commit_cannot_prepare_a_release(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            (root / "app").mkdir()
            (root / "app/build.gradle").write_text(
                "versionName '0.10'\nversionCode 15\n", encoding="utf-8"
            )
            with mock.patch.object(release, "run", side_effect=["a" * 40, "b" * 40]):
                with self.assertRaisesRegex(ValueError, "does not match"):
                    release.prepare(root, root / "out", "v0.10", "owner/repo")
            self.assertFalse((root / "out").exists())

    def test_notes_use_pinned_absolute_links_without_unreleased_changes(self):
        notes = release.release_notes(release.ROOT, "0.10", "owner/repo", "a" * 40)
        self.assertIn("blob/" + "a" * 40 + "/CHANGELOG.md#010", notes)
        self.assertNotIn("Spec-driven contribution", notes)
        self.assertIn("Evidence withdrawn for privacy", notes)
        self.assertIn("blob/" + "a" * 40 + "/testresults/README.md", notes)
        self.assertNotIn("/testresults/0.10/README.md", notes)
        self.assertIn("releases/download/v0.10/release-report.md", notes)

    def test_retained_version_notes_still_link_real_version_summary(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "testresults/0.10").mkdir(parents=True)
            (root / "testresults/0.10/README.md").write_text(
                "Safe retained summary", encoding="utf-8"
            )
            (root / "CHANGELOG.md").write_text("## [0.10]\n- Fixture change\n", encoding="utf-8")
            notes = release.release_notes(root, "0.10", "owner/repo", "a" * 40)
            self.assertIn("Version evidence and limitations", notes)
            self.assertIn("/testresults/0.10/README.md", notes)
            self.assertNotIn("Evidence withdrawn", notes)


class BinaryVerificationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk, self.aab = self.root / "app.apk", self.root / "app.aab"
        with zipfile.ZipFile(self.apk, "w") as z:
            z.writestr("AndroidManifest.xml", b"fixture")
            z.writestr("classes.dex", b"fixture")
        self.write_bundle(signed=True)

    def write_bundle(self, signed):
        with zipfile.ZipFile(self.aab, "w") as z:
            z.writestr("base/manifest/AndroidManifest.xml", b"fixture")
            z.writestr("base/dex/classes.dex", b"fixture")
            if signed:
                z.writestr("META-INF/DEXDROID.SF", b"fixture")

    def verify(self, badging=None, jar="jar verified.", cert=None):
        output = [
            badging or "package: name='dev.dexdroid' versionCode='15' versionName='0.10'",
            "Signer #1 certificate SHA-256 digest: " + "ab" * 32,
            jar,
            "SHA256: " + (cert or ":".join(["AB"] * 32)),
        ]
        with (
            mock.patch.object(release, "sdk_tool", side_effect=lambda x: x),
            mock.patch.object(release, "jdk_tool", side_effect=lambda x: x),
            mock.patch.object(release, "run", side_effect=output),
        ):
            return release.verify_binaries(self.apk, self.aab, "0.10", 15)

    def test_matching_signed_binaries_are_accepted(self):
        self.assertEqual("ab" * 32, self.verify())

    def test_wrong_version_or_debuggable_apk_fails(self):
        for badging in [
            "package: name='dev.dexdroid' versionCode='14' versionName='0.9'",
            "package: name='dev.dexdroid' versionCode='15' versionName='0.10'\napplication-debuggable",
        ]:
            with self.subTest(badging=badging), self.assertRaises(ValueError):
                self.verify(badging=badging)

    def test_unsigned_bundle_is_rejected_even_if_java_returns_success(self):
        with self.assertRaises(ValueError):
            self.verify(jar="jar is unsigned.")
        self.write_bundle(signed=False)
        with self.assertRaisesRegex(ValueError, "unsigned"):
            self.verify()

    def test_different_signing_certificates_fail(self):
        with self.assertRaisesRegex(ValueError, "certificates differ"):
            self.verify(cert=":".join(["CD"] * 32))


class ReleasePublicationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.out = self.root / "out"
        self.out.mkdir()
        for relative in [
            "app/build/outputs/apk/release/app-release.apk",
            "app/build/outputs/bundle/release/app-release.aab",
        ]:
            path = self.root / relative
            path.parent.mkdir(parents=True)
            path.write_bytes(b"synthetic signed artifact")
        metadata = {
            "tag": "v0.10",
            "version": "0.10",
            "version_code": 15,
            "repository": "owner/repo",
            "source_commit": "a" * 40,
            "prerelease": False,
            "verified": False,
        }
        release.write_json(self.out / "build.json", metadata)
        (self.out / "release-notes.md").write_text("Summary", encoding="utf-8")
        with mock.patch.object(release, "verify_binaries", return_value="ab" * 32):
            release.package(self.root, self.out)
        write_report(self.out, metadata)

    def test_packaged_checksums_match_every_asset(self):
        metadata = json.loads((self.out / "build.json").read_text(encoding="utf-8"))
        self.assertIs(True, metadata["verified"])
        self.assertEqual(2, len(metadata["files"]))
        for line in (self.out / "SHA256SUMS.txt").read_text(encoding="utf-8").splitlines():
            digest, name = line.split("  ", 1)
            self.assertEqual(digest, hashlib.sha256((self.out / name).read_bytes()).hexdigest())

    def test_new_release_is_created_as_draft_then_published_with_both_formats(self):
        api = subprocess.CompletedProcess([], 0, "[[]]", "")
        with (
            mock.patch.object(release.subprocess, "run", return_value=api),
            mock.patch.object(release, "run") as commands,
        ):
            release.publish(self.out)
        create, publish = [call.args[0] for call in commands.call_args_list]
        self.assertIn("--draft", create)
        self.assertTrue(any(arg.endswith(".apk") for arg in create))
        self.assertTrue(any(arg.endswith(".aab") for arg in create))
        self.assertTrue(any(arg.endswith("release-report.md") for arg in create))
        self.assertTrue(any(arg.endswith("release-report.json") for arg in create))
        self.assertIn("--draft=false", publish)

    def test_existing_draft_can_recover(self):
        api = subprocess.CompletedProcess([], 0, '[[{"tag_name":"v0.10","draft":true}]]', "")
        with (
            mock.patch.object(release.subprocess, "run", return_value=api),
            mock.patch.object(release, "run") as commands,
        ):
            release.publish(self.out)
        self.assertIn("--clobber", commands.call_args_list[0].args[0])
        self.assertIn("--draft=false", commands.call_args_list[-1].args[0])

    def test_published_release_is_never_overwritten(self):
        api = subprocess.CompletedProcess([], 0, '[[{"tag_name":"v0.10","draft":false}]]', "")
        with (
            mock.patch.object(release.subprocess, "run", return_value=api),
            mock.patch.object(release, "run") as commands,
        ):
            with self.assertRaisesRegex(ValueError, "already published"):
                release.publish(self.out)
        commands.assert_not_called()

    def test_tampered_package_is_rejected_before_github_calls(self):
        (self.out / "DexDroid-0.10.apk").write_bytes(b"tampered")
        with mock.patch.object(release.subprocess, "run") as api:
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                release.publish(self.out)
        api.assert_not_called()

    def test_github_lookup_error_is_not_treated_as_missing_release(self):
        api = subprocess.CompletedProcess([], 1, "", "HTTP 403")
        with (
            mock.patch.object(release.subprocess, "run", return_value=api),
            mock.patch.object(release, "run") as commands,
        ):
            with self.assertRaises(RuntimeError):
                release.publish(self.out)
        commands.assert_not_called()

    def test_incomplete_checksum_manifest_prevents_publication(self):
        (self.out / "SHA256SUMS.txt").write_text("", encoding="utf-8")
        with mock.patch.object(release.subprocess, "run") as api:
            with self.assertRaisesRegex(ValueError, "incomplete"):
                release.publish(self.out)
        api.assert_not_called()

    def test_missing_or_tampered_report_blocks_publication_before_github_calls(self):
        for name in ("release-report.md", "release-report.json"):
            path = self.out / name
            original = path.read_bytes()
            with self.subTest(name=name), mock.patch.object(release.subprocess, "run") as api:
                path.write_bytes(b"tampered")
                with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                    release.publish(self.out)
                path.unlink()
                with self.assertRaises(OSError):
                    release.publish(self.out)
                api.assert_not_called()
            path.write_bytes(original)

    def test_rehashed_mismatched_report_cannot_publish(self):
        path = self.out / "release-report.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        data["source_commit"] = "b" * 40
        path.write_text(json.dumps(data), encoding="utf-8")
        rehash(self.out)
        with mock.patch.object(release.subprocess, "run") as api:
            with self.assertRaisesRegex(ValueError, "source_commit mismatch"):
                release.publish(self.out)
            api.assert_not_called()


if __name__ == "__main__":
    unittest.main()
