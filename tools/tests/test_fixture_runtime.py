import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_fixture_runtime as guard


class FixtureRuntimeTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.apk = self.root / "test.apk"
        manifest = self.root / "app/src/androidTest/AndroidManifest.xml"
        manifest.parent.mkdir(parents=True)
        manifest.write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
            '<application><activity android:name="fixture.Parent" />'
            '<activity android:name="fixture.Child" /></application></manifest>',
            encoding="utf-8",
        )
        self.required = guard.RUNTIME | {
            "fixture/Parent",
            "fixture/Child",
            "dev/dexdroid/DeviceChecks",
        }
        self.addCleanup(mock.patch.stopall)
        mock.patch.object(guard.release, "sdk_tool", return_value="dexdump").start()

    def package(self, members=None):
        if members is None:
            # Header-only fixture: SDK output is mocked in these deterministic unit tests.
            members = {"classes.dex": b"dex\n035\x00" + bytes(104)}
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("AndroidManifest.xml", b"\x03\x00\x08\x00" + bytes(4))
            for name, data in members.items():
                archive.writestr(name, data)

    def definitions(self, values):
        return "\n".join("Class descriptor : 'L" + value + ";'" for value in values)

    def test_accepts_runtime_and_fixture_components_across_secondary_dex(self):
        self.package(
            {
                "classes.dex": b"dex\n035\x00" + bytes(104),
                "classes2.dex": b"dex\n035\x00" + bytes(104),
            }
        )
        with mock.patch.object(
            guard.release,
            "run",
            side_effect=[
                self.definitions(guard.RUNTIME),
                self.definitions(self.required - guard.RUNTIME),
            ],
        ):
            self.assertEqual(2, guard.verify(self.apk, self.root)["dex_files"])

    def test_type_references_cannot_satisfy_required_definitions(self):
        self.package()
        output = self.definitions(self.required - {"kotlin/jvm/internal/Intrinsics"})
        output += "\nType descriptor : 'Lkotlin/jvm/internal/Intrinsics;'"
        with mock.patch.object(guard.release, "run", return_value=output):
            with self.assertRaisesRegex(ValueError, "Missing standalone.*Intrinsics"):
                guard.verify(self.apk, self.root)

    def test_missing_fixture_definition_is_rejected(self):
        self.package()
        with mock.patch.object(
            guard.release, "run", return_value=self.definitions(self.required - {"fixture/Child"})
        ):
            with self.assertRaisesRegex(ValueError, "fixture/Child"):
                guard.verify(self.apk, self.root)

    def test_missing_or_malformed_dex_is_rejected_before_sdk(self):
        for members in [{}, {"classes.dex": b"not dex"}]:
            with self.subTest(members=members):
                self.package(members)
                with mock.patch.object(guard.release, "run") as command:
                    with self.assertRaises(ValueError):
                        guard.verify(self.apk, self.root)
                    command.assert_not_called()

    def test_empty_or_failed_sdk_inspection_fails_closed(self):
        self.package()
        for result in ["", RuntimeError("SDK failure")]:
            with self.subTest(result=result):
                with mock.patch.object(
                    guard.release,
                    "run",
                    side_effect=result if isinstance(result, Exception) else None,
                    return_value=result,
                ):
                    with self.assertRaises((ValueError, RuntimeError)):
                        guard.verify(self.apk, self.root)

    def test_invalid_archive_and_absent_manifest_fail_closed(self):
        self.apk.write_bytes(b"not an APK archive")
        with self.assertRaises(zipfile.BadZipFile):
            guard.verify(self.apk, self.root)
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("classes.dex", b"dex\n035\x00" + bytes(104))
        with self.assertRaisesRegex(ValueError, "manifest"):
            guard.verify(self.apk, self.root)
