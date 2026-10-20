import com.diffplug.spotless.LineEnding
import com.diffplug.spotless.extra.wtp.EclipseWtpFormatterStep

plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("com.diffplug.spotless") version "7.2.1"
}

spotless {
    encoding("UTF-8")
    lineEndings = LineEnding.UNIX
    java {
        target("app/src/**/*.java")
        googleJavaFormat("1.24.0").aosp()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlin {
        target("app/src/**/*.kt")
        ktfmt("0.58").kotlinlangStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktfmt("0.58").kotlinlangStyle()
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("xml") {
        target("app/src/**/*.xml")
        eclipseWtp(EclipseWtpFormatterStep.XML, "4.21.0").configFile("gradle/xml-format.properties")
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("text") {
        target(
            "*.md",
            "docs/**/*.md",
            ".agents/**/*.md",
            "testresults/**/README.md",
            "*.properties",
            ".editorconfig",
            ".gitattributes",
            ".gitignore",
            "app/src/**/*.aidl",
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.register("qualityCheck") {
    group = "verification"
    description =
        "Formatting, host tests, APK compilation, and lint. Device acceptance is separate."
    dependsOn(
        "spotlessCheck",
        ":app:testDebugUnitTest",
        ":app:assembleDebug",
        ":app:assembleDebugAndroidTest",
        ":app:lintDebug",
    )
}
