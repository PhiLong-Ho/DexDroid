import copy
import hashlib
import json
import stat
import sys
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import evidence


class EvidenceArchiveTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "run"
        (self.source / "phone").mkdir(parents=True)
        self.raw = b"PASS fixture\r\n\xff\x00raw bytes\r\n"
        (self.source / "log.txt").write_bytes(self.raw)
        (self.source / "phone/frame.png").write_bytes(b"\x89PNG\r\nfixture")
        self.output = self.root / "bundle"
        self.context = {"scope": "Fixture only; no device/visual pass claimed"}

    def pack(self):
        self.descriptor = evidence.create_archive(self.source, self.output, self.context)
        self.archive = self.output / "evidence.zip"
        return evidence.verify_archive(self.archive, self.descriptor)

    def replace_members(self, mutate):
        with zipfile.ZipFile(self.archive) as bundle:
            members = [(info, bundle.read(info)) for info in bundle.infolist()]
        members = mutate(members)
        with warnings.catch_warnings(), zipfile.ZipFile(self.archive, "w") as bundle:
            warnings.simplefilter("ignore", UserWarning)
            for info, content in members:
                bundle.writestr(info, content)
        self.descriptor["archive_sha256"] = evidence.digest_file(self.archive)
        self.descriptor["archive_bytes"] = self.archive.stat().st_size

    def test_round_trip_preserves_raw_bytes_scope_and_originals(self):
        manifest = self.pack()
        self.assertEqual(self.descriptor["storage"]["kind"], "local-only")
        self.assertEqual(manifest["context"], self.context)
        self.assertEqual(self.descriptor["file_count"], 2)
        with zipfile.ZipFile(self.archive) as bundle:
            self.assertEqual(bundle.read("data/log.txt"), self.raw)
            self.assertEqual(bundle.read("data/phone/frame.png"), b"\x89PNG\r\nfixture")
        self.assertEqual((self.source / "log.txt").read_bytes(), self.raw)
        self.assertEqual(len(list(self.source.rglob("*"))), 3)
        self.assertEqual(
            json.loads((self.output / "archive.json").read_text(encoding="utf-8")),
            self.descriptor,
        )

    def test_existing_output_is_never_overwritten(self):
        self.pack()
        original = self.archive.read_bytes()
        with self.assertRaises(FileExistsError):
            evidence.create_archive(self.source, self.output, self.context)
        self.assertEqual(self.archive.read_bytes(), original)

    def test_rejects_recursive_output_empty_source_and_missing_scope(self):
        with self.assertRaises(ValueError):
            evidence.create_archive(self.source, self.source / "nested", self.context)
        empty = self.root / "empty"
        empty.mkdir()
        with self.assertRaises(ValueError):
            evidence.create_archive(empty, self.output, self.context)
        for scope in [None, "", " "]:
            with self.subTest(scope=scope), self.assertRaises(ValueError):
                evidence.create_archive(self.source, self.output, {"scope": scope})
        self.assertFalse(self.output.exists())

    def test_changed_archive_or_changed_member_is_rejected(self):
        self.pack()
        original = self.archive.read_bytes()
        self.archive.write_bytes(original + b"tampered")
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            evidence.verify_archive(self.archive, self.descriptor)
        self.archive.write_bytes(original)
        self.replace_members(
            lambda members: [
                (info, b"x" * len(data) if info.filename == "data/log.txt" else data)
                for info, data in members
            ]
        )
        with self.assertRaisesRegex(ValueError, "member checksum mismatch"):
            evidence.verify_archive(self.archive, self.descriptor)

    def test_missing_unexpected_duplicate_and_unsafe_members_are_rejected(self):
        self.pack()
        original = self.archive.read_bytes()
        descriptor = copy.deepcopy(self.descriptor)
        fixtures = [
            lambda items: [item for item in items if item[0].filename != "data/log.txt"],
            lambda items: items + [(zipfile.ZipInfo("data/extra.txt"), b"extra")],
            lambda items: items + [items[-1]],
            lambda items: items + [(zipfile.ZipInfo("data/LOG.TXT"), b"case collision")],
        ]
        for name in [
            "../outside",
            "/absolute",
            "data/../../escape",
            "C:/drive",
            "data\\escape",
            "data//empty",
            "data/NUL.txt",
            "data/file.",
            "data/file ",
            *[f"data/file{char}name.txt" for char in '<>:"|?*'],
        ]:
            fixtures.append(lambda items, name=name: items + [(zipfile.ZipInfo(name), b"unsafe")])
        link = zipfile.ZipInfo("data/link")
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        fixtures.append(lambda items: items + [(link, b"outside")])
        for index, mutate in enumerate(fixtures):
            with self.subTest(index=index):
                self.archive.write_bytes(original)
                self.descriptor = copy.deepcopy(descriptor)
                self.replace_members(mutate)
                with self.assertRaises((ValueError, KeyError)):
                    evidence.verify_archive(self.archive, self.descriptor)
        self.assertFalse((self.root / "outside").exists())

        # A valid manifest and resealed outer hash must not legitimize names that
        # Windows extraction would sanitize onto an existing sibling filename.
        self.archive.write_bytes(original)
        self.descriptor = copy.deepcopy(descriptor)
        with zipfile.ZipFile(self.archive) as bundle:
            manifest = json.loads(bundle.read("manifest.json"))
        unsafe_names = [f"a{char}b.txt" for char in '<>:"|?*']
        devices = ["CON", "PRN", "AUX", "NUL", "CONIN$", "CONOUT$"] + [
            f"{prefix}{n}" for prefix in ("COM", "LPT") for n in "123456789\u00b9\u00b2\u00b3"
        ]
        for device in devices:
            unsafe_names.extend([device, device.lower() + ".txt", device + "  .log"])
        for unsafe_name in unsafe_names:
            alias_name = "a_b.txt"
            data = b"different bytes"
            manifest["files"] = [
                {
                    "path": unsafe_name,
                    "bytes": len(data),
                    "sha256": hashlib.sha256(data).hexdigest(),
                },
                {
                    "path": alias_name,
                    "bytes": len(data),
                    "sha256": hashlib.sha256(data).hexdigest(),
                },
            ]
            with zipfile.ZipFile(self.archive, "w") as bundle:
                bundle.writestr("manifest.json", json.dumps(manifest))
                bundle.writestr("data/" + unsafe_name, data)
                bundle.writestr("data/" + alias_name, data)
            self.descriptor.update(
                archive_sha256=evidence.digest_file(self.archive),
                archive_bytes=self.archive.stat().st_size,
                file_count=2,
                raw_bytes=2 * len(data),
            )
            with self.subTest(windows_alias=unsafe_name), self.assertRaises(ValueError):
                evidence.verify_archive(self.archive, self.descriptor)

    def test_descriptor_scope_timestamp_and_totals_are_bound_to_archive(self):
        self.pack()
        for key, value in [
            ("context", {"scope": "Claim a different run"}),
            ("file_count", 99),
            ("raw_bytes", 0),
            ("created_utc", "invented"),
        ]:
            descriptor = {**self.descriptor, key: value}
            with self.subTest(key=key), self.assertRaises(ValueError):
                evidence.verify_archive(self.archive, descriptor)

    def test_source_mutation_does_not_publish_verified_archive(self):
        original_open = zipfile.ZipFile.open
        changed = False

        def intercept(bundle, name, mode="r", *args, **kwargs):
            nonlocal changed
            if mode == "w" and not changed:
                with (self.source / "log.txt").open("ab") as stream:
                    stream.write(b"ongoing run")
                changed = True
            return original_open(bundle, name, mode, *args, **kwargs)

        with mock.patch.object(zipfile.ZipFile, "open", intercept):
            with self.assertRaisesRegex(ValueError, "changed while archiving"):
                evidence.create_archive(self.source, self.output, self.context)
        self.assertFalse((self.output / "archive.json").exists())
        self.assertFalse((self.output / "evidence.zip").exists())
        self.assertTrue((self.source / "log.txt").exists())

    def test_source_link_is_rejected_before_output_creation(self):
        with mock.patch.object(Path, "is_symlink", return_value=True):
            with self.assertRaisesRegex(ValueError, "must not be a link"):
                evidence.create_archive(self.source, self.output, self.context)
        self.assertFalse(self.output.exists())

    def test_cli_records_workflow_target_without_promoting_run_outcome(self):
        url = "https://github.com/owner/repo/actions/runs/42"
        command = [
            "evidence.py",
            "pack",
            "--source",
            str(self.source),
            "--output",
            str(self.output),
            "--scope",
            "Synthetic failure evidence; no test pass",
            "--workflow-url",
            url,
        ]
        with (
            mock.patch.object(sys, "argv", command),
            mock.patch.object(evidence.subprocess, "check_output", side_effect=["a" * 40, b""]),
        ):
            self.assertEqual(evidence.main(), 0)
        descriptor = json.loads((self.output / "archive.json").read_text(encoding="utf-8"))
        manifest = evidence.verify_archive(self.output / "evidence.zip", descriptor)
        self.assertEqual(descriptor["storage"]["kind"], "github-actions-target")
        self.assertEqual(descriptor["storage"]["url"], url)
        self.assertEqual(manifest["context"]["workflow_url"], url)
        self.assertEqual(manifest["context"]["scope"], "Synthetic failure evidence; no test pass")


if __name__ == "__main__":
    unittest.main()
