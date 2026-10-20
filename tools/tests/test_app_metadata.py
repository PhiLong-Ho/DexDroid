import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import app_metadata
import check_stop_focus
import release
import run_device_checks


class AppMetadataTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "app").mkdir()

    def script(self, text, kotlin=True, wrap=True, prefix="", keep_other=False):
        path = self.root / ("app/build.gradle.kts" if kotlin else "app/build.gradle")
        if not keep_other:
            (self.root / ("app/build.gradle" if kotlin else "app/build.gradle.kts")).unlink(
                missing_ok=True
            )
        if wrap:
            text = "android {\n defaultConfig {\n" + text + "\n }\n}\n"
        text = prefix + text
        path.write_text(text, encoding="utf-8")
        return path

    def test_kotlin_literal_version_contract(self):
        self.script('versionName = "0.13.0"\nversionCode = 39\n')
        self.assertEqual(("0.13.0", 39), app_metadata.app_version(self.root))

    def test_groovy_literal_version_contract(self):
        self.script("versionName '0.12.5'\nversionCode 38\n", kotlin=False)
        self.assertEqual(("0.12.5", 38), app_metadata.app_version(self.root))

    def test_comments_and_unrelated_strings_do_not_invent_metadata(self):
        self.script(
            """// versionName = "wrong"
/* versionCode = 0 /* nested versionName */ */
versionName /* comment */ = "0.13.0" // note
versionCode = 39 /* trailing comment */
""",
            prefix='fun note() { val text = "versionName versionCode // harmless text" }\n',
        )
        self.assertEqual(("0.13.0", 39), app_metadata.app_version(self.root))

    def test_missing_or_ambiguous_scripts_fail(self):
        with self.assertRaises(ValueError):
            app_metadata.app_version(self.root)
        self.script('versionName = "0.13.0"\nversionCode = 39\n')
        self.script("versionName '0.13.0'\nversionCode 39\n", kotlin=False, keep_other=True)
        with self.assertRaises(ValueError):
            app_metadata.app_version(self.root)

    def test_duplicate_or_computed_declarations_fail(self):
        for text in [
            'versionName = "0.13.0"\nversionName = "0.14.0"\nversionCode = 39',
            'versionName = "0.13.0"\nversionCode = 39\nversionCode = 40',
            'versionName = "0.13." + patch\nversionCode = 39',
            'versionName = "0.13.$patch"\nversionCode = 39',
            "versionName = computed()\nversionCode = 39",
            'versionName = "0.13.0"\nversionCode = base + 1',
        ]:
            with self.subTest(text=text):
                self.script(text)
                with self.assertRaises(ValueError):
                    app_metadata.app_version(self.root)

    def test_missing_invalid_or_unsafe_values_fail(self):
        for text in [
            'versionName = "0.13.0"',
            "versionCode = 39",
            'versionName = "0.13.0"\nversionCode = 0',
            'versionName = "0.13.0"\nversionCode = -1',
            'versionName = "../private"\nversionCode = 39',
            'versionName = ""\nversionCode = 39',
            'versionName = "0.13.0"\nversionCode = 39.0',
        ]:
            with self.subTest(text=text):
                self.script(text)
                with self.assertRaises(ValueError):
                    app_metadata.app_version(self.root)

    def test_unclosed_literals_or_comments_fail(self):
        for suffix in [
            "\n/* unfinished",
            '\nval label = "unfinished',
            '\nval label = """unfinished',
        ]:
            self.script('versionName = "0.13.0"\nversionCode = 39' + suffix)
            with self.subTest(suffix=suffix), self.assertRaises(ValueError):
                app_metadata.app_version(self.root)

    def test_kotlin_requires_literal_assignment_syntax(self):
        self.script("versionName '0.13.0'\nversionCode 39")
        with self.assertRaises(ValueError):
            app_metadata.app_version(self.root)

    def test_multiline_computed_suffixes_fail(self):
        for suffix in ['.replace("0", "1")', '+ "x"', "as String", "[0]", "plus(1)", '? "x" : "y"']:
            self.script('versionName = "0.13.0"\n' + suffix + "\nversionCode = 39")
            with self.subTest(suffix=suffix), self.assertRaises(ValueError):
                app_metadata.app_version(self.root)
        for suffix in [".plus(1)", "+ 1", "or 1", "as Int", "[0]"]:
            self.script('versionName = "0.13.0"\nversionCode = 39\n' + suffix)
            with self.subTest(suffix=suffix), self.assertRaises(ValueError):
                app_metadata.app_version(self.root)

    def test_version_must_belong_to_single_direct_default_config(self):
        body = 'versionName = "0.13.0"\nversionCode = 39\n'
        for source in [
            body,
            "unrelated {\ndefaultConfig {\n" + body + "}\n}",
            "android {\nunrelated {\ndefaultConfig {\n" + body + "}\n}\n}",
            "android {\ndefaultConfig {\nif (true) {\n" + body + "}\n}\n}",
            "android {\ndefaultConfig {\n" + body + "}\n}\nandroid {}",
            "android {\ndefaultConfig {\n" + body + "}\ndefaultConfig {}\n}",
            "android {\ndefaultConfig {\n" + body + "}\n}\nval x = versionName",
        ]:
            self.script(source, wrap=False)
            with self.subTest(source=source), self.assertRaises(ValueError):
                app_metadata.app_version(self.root)

    def test_kotlin_char_raw_and_template_versions_fail(self):
        for value in ["'0.13.0'", '"""0.13.0"""', '"0.13.$patch"', '"${compute()}"']:
            self.script("versionName = " + value + "\nversionCode = 39")
            with self.subTest(value=value), self.assertRaises(ValueError):
                app_metadata.app_version(self.root)

    def test_unbalanced_structure_and_unsupported_template_nesting_fail(self):
        for source in [
            'android {\ndefaultConfig {\nversionName = "0.13.0"\nversionCode = 39',
            'android {\ndefaultConfig (\nversionName = "0.13.0"\nversionCode = 39\n}\n}',
            'val x = "${listOf("quoted")}"\nandroid {\ndefaultConfig {\nversionName = "0.13.0"\nversionCode = 39\n}\n}',
        ]:
            self.script(source, wrap=False)
            with self.subTest(source=source), self.assertRaises(ValueError):
                app_metadata.app_version(self.root)

    def test_unrelated_raw_literals_do_not_invent_braces_or_metadata(self):
        self.script(
            'versionName = "0.13.0"\nversionCode = 39',
            prefix='fun note() { val text = """android { defaultConfig { versionName versionCode } }""" }\n',
        )
        self.assertEqual(("0.13.0", 39), app_metadata.app_version(self.root))

    def test_unbraced_and_expression_contexts_do_not_define_metadata(self):
        body = 'android {\n defaultConfig {\nversionName = "0.13.0"\nversionCode = 39\n }\n}\n'
        for prefix in [
            "if (false)\n",
            "fun never() =\n",
            "receiver.\n",
            "fun never() = ",
            "receiver.",
        ]:
            self.script(prefix + body, wrap=False)
            with self.subTest(prefix=prefix), self.assertRaises(ValueError):
                app_metadata.app_version(self.root)
        for prefix in ["if (false)\n", "val never =\n", "receiver.\n"]:
            self.script(body.replace(" defaultConfig", prefix + " defaultConfig"), wrap=False)
            with self.subTest(prefix=prefix), self.assertRaises(ValueError):
                app_metadata.app_version(self.root)

    def test_default_config_body_is_fully_consumed_and_version_mutations_fail(self):
        for suffix in [
            'setVersionName("0.14.0")',
            "setVersionCode(40)",
            'versionNameSuffix = "-changed"',
            "val unused = 1",
            "someHelper()",
            "if (false)\nminSdk = 30",
            "vectorDrawables {}",
        ]:
            self.script(
                'versionName = "0.13.0"\nversionCode = 39\n'
                'testInstrumentationRunner = "dev.dexdroid.DeviceChecks"\n' + suffix
            )
            with self.subTest(suffix=suffix), self.assertRaises(ValueError):
                app_metadata.app_version(self.root)

    def test_leading_zero_integers_fail_instead_of_changing_groovy_octal_meaning(self):
        for kotlin, code in [(False, "010"), (False, "039"), (True, "039")]:
            self.script(
                (
                    'versionName = "0.13.0"\nversionCode = '
                    if kotlin
                    else "versionName '0.13.0'\nversionCode "
                )
                + code,
                kotlin=kotlin,
            )
            with self.subTest(kotlin=kotlin, code=code), self.assertRaises(ValueError):
                app_metadata.app_version(self.root)

    def test_release_and_device_evidence_agree_for_both_script_forms(self):
        for kotlin in [True, False]:
            source = (
                'versionName = "0.13.0"\nversionCode = 39'
                if kotlin
                else "versionName '0.13.0'\nversionCode 39"
            )
            path = self.script(source, kotlin=kotlin)
            with self.subTest(kotlin=kotlin):
                self.assertEqual(("0.13.0", 39), release.app_version(self.root))
                self.assertEqual("0.13.0", run_device_checks.result_version(self.root))
            path.unlink()

    def test_invalid_release_metadata_fails_before_git_and_output(self):
        self.script("versionName = computed()\nversionCode = 39")
        output = self.root / "output"
        with mock.patch.object(release, "run") as command:
            with self.assertRaises(ValueError):
                release.prepare(self.root, output, "v0.13.0", "owner/repo")
        command.assert_not_called()
        self.assertFalse(output.exists())

    def test_invalid_device_metadata_fails_before_adb_and_output(self):
        self.script("versionName = computed()\nversionCode = 39")
        with (
            mock.patch.object(run_device_checks, "ROOT", self.root),
            mock.patch.object(sys, "argv", ["run_device_checks", "--suite", "input"]),
            mock.patch.object(run_device_checks, "adb_path") as adb,
            mock.patch.object(run_device_checks, "command") as command,
        ):
            with self.assertRaises(RuntimeError):
                run_device_checks.main()
        adb.assert_not_called()
        command.assert_not_called()
        self.assertFalse((self.root / "testresults").exists())

    def test_invalid_native_stop_metadata_fails_before_adb_and_output(self):
        self.script("versionName = computed()\nversionCode = 39")
        with (
            mock.patch.object(check_stop_focus, "ROOT", self.root),
            mock.patch.object(sys, "argv", ["check_stop_focus", "--serial", "synthetic"]),
            mock.patch.object(check_stop_focus, "adb_path") as adb,
            mock.patch.object(check_stop_focus, "command") as command,
        ):
            with self.assertRaises(ValueError):
                check_stop_focus.main()
        adb.assert_not_called()
        command.assert_not_called()
        self.assertFalse((self.root / "testresults").exists())

    def test_groovy_setters_after_intervening_property_fail(self):
        for setter in ["setVersionName('0.14.0')", "setVersionCode(40)"]:
            self.script(
                "versionName '0.13.0'\nversionCode 39\n"
                "testInstrumentationRunner 'dev.dexdroid.DeviceChecks'\n" + setter,
                kotlin=False,
            )
            with (
                self.subTest(setter=setter),
                self.assertRaisesRegex(ValueError, "Alternate version mutation"),
            ):
                app_metadata.app_version(self.root)


if __name__ == "__main__":
    unittest.main()
