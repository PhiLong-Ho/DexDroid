import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import coverage
import kotlin_source
import release


class KotlinInventoryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def source(self, name, text):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def test_mixed_sources_and_helpers_are_in_fingerprint_inventory(self):
        java = self.source("app/src/test/java/Before.java", "class Before {}")
        kotlin = self.source("app/src/test/kotlin/After.kt", "class After")
        helper = self.source("app/src/androidTest/kotlin/Helper.kt", "fun helper() = 1")
        self.source("app/src/main/kotlin/Product.kt", "class Product")
        self.assertEqual({java, kotlin, helper}, set(coverage.test_sources(self.root)))

    def test_kotlin_junit_and_native_entry_names_survive_conversion(self):
        path = self.source(
            "Checks.kt",
            "@Test\n@Throws(Exception::class)\nopen fun actualCase() {}\n"
            "@org.junit.Test(expected = IllegalArgumentException::class)\nfun fails() {}\n"
            'fun run(): String = ""\noverride fun onStart() {}\nfun helper() {}',
        )
        self.assertEqual(["actualCase", "fails", "onStart", "run"], coverage.cases(path))

    def test_comments_literals_and_nested_templates_do_not_invent_cases(self):
        path = self.source(
            "Checks.kt",
            '''/* outer /* @Test fun wrong() {} */ still comment */
val text = """@Test fun pretend() {}"""
val nested = "${if (true) "${listOf("{", "}").joinToString()}" else "}"}"
// @Test fun anotherWrong() {}
@Test fun actualCase() { val char = '{' }
''',
        )
        self.assertEqual(["actualCase"], coverage.cases(path))

    def test_kotlin_top_level_declarations_and_facades_are_inventoried(self):
        path = self.source(
            "fixtures.kt",
            """package dev.dexdroid.fixture
// class Pretend
class Parent { class Child; companion object }
data class Other(val value: String = "class Fake {}")
object Fixture
fun interface Callback { fun result(): String }
val type = Parent::class
""",
        )
        self.assertEqual(
            {
                "dev.dexdroid.fixture." + name
                for name in ["FixturesKt", "Parent", "Other", "Fixture", "Callback"]
            },
            kotlin_source.development_identities(path),
        )

    def test_custom_multifile_jvm_facade_and_part_are_inventoried(self):
        path = self.source(
            "helpers.kt",
            '@file : kotlin.jvm.JvmName("PrivateHelpers")\n'
            "@file:JvmMultifileClass\npackage privatefixture\nfun helper() = 1\n",
        )
        self.assertEqual(
            {"privatefixture.PrivateHelpers", "privatefixture.PrivateHelpers__HelpersKt"},
            kotlin_source.development_identities(path),
        )

    def test_ambiguous_or_unsupported_source_fails_closed(self):
        for source in [
            '@file:JvmName("One")\n@file:JvmName("Two")\nclass Fixture',
            "@file:JvmName(NAME)\nclass Fixture",
            '@file:[JvmName("Hidden")]\nclass Fixture',
            "import kotlin.jvm.JvmName as Hidden\nclass Fixture",
            "package `escaped`.name\nclass Fixture",
            'class Fixture { val text = "unclosed',
            "class Fixture {",
            "/* unfinished",
        ]:
            with self.subTest(source=source), self.assertRaises(ValueError):
                kotlin_source.development_identities(self.source("Fixture.kt", source))


class KotlinProductionGuardTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        source = self.root / "app/src/debug/kotlin/dev/dexdroid/fixture/Helpers.kt"
        source.parent.mkdir(parents=True)
        source.write_text(
            '@file:JvmName("PrivateHelpers")\n@file:JvmMultifileClass\n'
            "package dev.dexdroid.fixture\nclass Diagnostic { companion object }\n"
            "class OtherDiagnostic\nfun helper() = 1\n",
            encoding="utf-8",
        )

    def verify(self, identity, bundle):
        path = self.root / ("app.aab" if bundle else "app.apk")
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr(
                "base/manifest/AndroidManifest.xml" if bundle else "AndroidManifest.xml", b"fixture"
            )
            archive.writestr("base/dex/classes.dex" if bundle else "classes.dex", b"fixture")
        with (
            mock.patch.object(release, "ROOT", self.root),
            mock.patch.object(release, "sdk_tool", return_value="dexdump"),
            mock.patch.object(release, "run", return_value=f"Class descriptor : 'L{identity};'"),
        ):
            release.verify_production_content(path, bundle)

    def test_kotlin_classes_companions_facades_and_parts_cannot_ship(self):
        for bundle in [False, True]:
            for name in [
                "Diagnostic",
                "Diagnostic$Companion",
                "OtherDiagnostic",
                "PrivateHelpers",
                "PrivateHelpers__HelpersKt$1",
            ]:
                with (
                    self.subTest(bundle=bundle, name=name),
                    self.assertRaisesRegex(ValueError, "Development class"),
                ):
                    self.verify("dev/dexdroid/fixture/" + name, bundle)

    def test_clean_production_definition_is_not_blocked(self):
        for bundle in [False, True]:
            self.verify("dev/dexdroid/MainActivity", bundle)

    def test_missing_development_inventory_blocks_production_validation(self):
        with (
            mock.patch.object(release, "ROOT", self.root / "missing"),
            self.assertRaisesRegex(ValueError, "No development source"),
        ):
            release.development_classes()
