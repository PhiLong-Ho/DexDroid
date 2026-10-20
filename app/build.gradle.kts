import java.nio.file.Files
import java.util.Properties
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Standalone different-UID fixtures cannot use the target app's classloader.
val fixtureKotlinVersion = "2.2.21"
val fixtureKotlinRuntime =
    configurations.create("standaloneFixtureKotlinRuntime") {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(
                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                objects.named(LibraryElements.JAR),
            )
            attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
        }
    }
val fixtureKotlinJar =
    layout.buildDirectory.file(
        "standalone-fixture-runtime/standalone-fixture-kotlin-stdlib-" +
            fixtureKotlinVersion +
            ".jar"
    )
val appKotlinRuntimeVersion =
    providers.provider {
        val modules =
            configurations
                .getByName("debugRuntimeClasspath")
                .incoming
                .resolutionResult
                .allComponents
                .map { it.id }
                .filterIsInstance<ModuleComponentIdentifier>()
                .filter { it.group == "org.jetbrains.kotlin" && it.module == "kotlin-stdlib" }
        check(modules.size == 1) { "Expected one app Kotlin runtime" }
        modules.single().version
    }
val prepareFixtureKotlinRuntime =
    tasks.register<Copy>("prepareStandaloneFixtureKotlinRuntime") {
        from(fixtureKotlinRuntime)
        into(layout.buildDirectory.dir("standalone-fixture-runtime"))
        // A unique basename prevents aliasing AGP's path-normalized DEX transform cache.
        rename { "standalone-fixture-kotlin-stdlib-" + fixtureKotlinVersion + ".jar" }
        inputs.property("appKotlinRuntimeVersion", appKotlinRuntimeVersion)
        doFirst {
            check(
                appKotlinRuntimeVersion.get() == fixtureKotlinVersion &&
                    fixtureKotlinRuntime.singleFile.name ==
                        "kotlin-stdlib-" + fixtureKotlinVersion + ".jar"
            ) {
                "Standalone fixture Kotlin runtime must match the app"
            }
        }
        doLast {
            check(
                Files.mismatch(
                    fixtureKotlinRuntime.singleFile.toPath(),
                    fixtureKotlinJar.get().asFile.toPath(),
                ) == -1L
            ) {
                "Standalone fixture runtime copy differs from its pinned input"
            }
        }
    }

dependencies {
    add(fixtureKotlinRuntime.name, "org.jetbrains.kotlin:kotlin-stdlib:" + fixtureKotlinVersion)
    androidTestImplementation(files(fixtureKotlinJar).builtBy(prepareFixtureKotlinRuntime))
}

// Local backup is ignored; CI supplies the same values through repository secrets.
val localSigning = Properties()
val localSigningFile = rootProject.file(".local/signing/release.properties")

if (localSigningFile.isFile) {
    localSigningFile.reader(Charsets.UTF_8).use { localSigning.load(it) }
}

fun signingValue(name: String): String? {
    return System.getenv(name)?.takeIf { it.isNotEmpty() } ?: localSigning.getProperty(name)
}

android {
    namespace = "dev.dexdroid"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.dexdroid"
        minSdk = 30
        targetSdk = 36
        versionCode = 40
        versionName = "0.14.0"
        testInstrumentationRunner = "dev.dexdroid.DeviceChecks"
    }
    lint {
        baseline = file("lint-baseline.xml")
        warningsAsErrors = true
        abortOnError = true
        textReport = true
        htmlReport = true
        xmlReport = true
    }
    buildFeatures {
        aidl = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        create("release") {
            val keyPath = signingValue("DEXDROID_KEYSTORE_PATH")
            if (!keyPath.isNullOrEmpty()) {
                storeFile = rootProject.file(keyPath)
                storePassword = signingValue("DEXDROID_KEYSTORE_PASSWORD")
                keyAlias = signingValue("DEXDROID_KEY_ALIAS")
                keyPassword = signingValue("DEXDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("release") } }
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}

// Windows locale must not reinterpret UTF-8 UI labels or instrumentation checks.
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
