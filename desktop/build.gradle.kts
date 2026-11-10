import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Fumibako on a computer: the shared screens (ui) in a window, with the desktop's services
// (docs/plan-desktop.md). `./gradlew :desktop:run` opens it; packaging makes an installer for the
// system it runs on (packageDistributionForCurrentOS).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val versionName = providers.gradleProperty("fumibako.versionName").get()
val versionCode = providers.gradleProperty("fumibako.versionCode").get()

dependencies {
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.java.keyring)
    implementation(libs.pdfbox)
    implementation(libs.jaudiotagger)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.compose.mp.ui.test.junit4)
    // The screenshot tour points Dispatchers.Main at its own thread (DesktopScreenshotTour).
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    // Fresh folders per test; screenshots when asked for (as the phone's tour). The screenshot tour
    // (DesktopScreenshotTour) runs only with HARNESS_SCREENSHOTS_DIR set, and such a run always
    // really runs: never "up to date" and never from the build cache, which would pass it without
    // drawing a single picture.
    val shots = providers.environmentVariable("HARNESS_SCREENSHOTS_DIR")
    environment("HARNESS_SCREENSHOTS_DIR", shots.getOrElse(""))
    inputs.property("screenshotsDir", shots.orElse(""))
    outputs.upToDateWhen { !shots.isPresent }
    outputs.cacheIf { !shots.isPresent }
    // About's version, as the installed app's launcher passes it.
    systemProperty("fumibako.version", versionName)
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// The notices for the libraries the app carries (buildSrc/LibraryNotices.kt): in the app, for
// About, and beside it in the installed app's resources folder.
val libraryNotices = libraryNotices("libraryNotices", "runtimeClasspath") {
    audience.set("computers")
    appVersion.set(versionName)
    // Its POM says only "LGPL"; its license file (license.txt in its source) is the LGPL 2.1.
    licenseOf.put("net.jthink:jaudiotagger", "LGPL-2.1")
    notes.put(
        "net.jthink:jaudiotagger",
        "Used unmodified, as its own jar (jaudiotagger-3.0.1-….jar in the app's lib/app or app folder), as the LGPL 2.1 " +
            "allows: it can be replaced with another build of the library. Its source: https://bitbucket.org/ijabz/jaudiotagger",
    )
    extra.add(
        "The installers also carry a Java runtime (OpenJDK, GPL 2.0 with the Classpath Exception), whose own " +
            "notices are in its legal folder in the app's folder: lib/runtime/legal on Linux, runtime\\legal on Windows, " +
            "Contents/runtime/Contents/Home/legal on a Mac.",
    )
}
sourceSets.main { resources.srcDir(libraryNotices.map { it.outputDir }) }
val besideTheApp = tasks.register<Sync>("libraryNoticesBesideTheApp") {
    from(libraryNotices.map { it.outputDir })
    // The packaging's resources folder takes files for every system from common/.
    into(layout.buildDirectory.dir("generated/appResources/common"))
}

compose.desktop {
    application {
        mainClass = "dev.aiharness.desktop.MainKt"
        jvmArgs += listOf("-Dfumibako.version=$versionName")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Fumibako"
            packageVersion = versionName
            description = "Letters with personas who remember, feel, and write to you on their own."
            vendor = "Fumibako"
            // Only the Java modules Fumibako uses, not the whole runtime. Compose adds java.base,
            // java.desktop (the window, the tray, the clipboard; it brings java.xml and java.prefs)
            // and java.logging. The list comes from suggestRuntimeModules, jdeps over every jar's
            // classes (unpacked: a modular jar stops jdeps), and what only shows at run time. A
            // library that needs a module left out fails only when its code runs, so check a new
            // one with both tools.
            modules(
                "java.instrument", // kotlinx-coroutines (its debug probes)
                "java.net.http", // the Anthropic SDK (standardwebhooks)
                "java.sql", // Jackson (the Anthropic SDK), for its date types
                "jdk.unsupported", // sun.misc.Unsafe: JavaFX, DataStore's protobuf, coroutines
                "jdk.jfr", // JavaFX (the songs' player)
                // dbus-java, on Linux (the keychain's Secret Service, and FileKit's dialogs through
                // the desktop portal): logging in to D-Bus as you, and its Unix socket.
                "jdk.security.auth",
                "jdk.net",
                "jdk.crypto.ec", // HTTPS: elliptic-curve keys and key exchange (in java.base from Java 22)
                "jdk.charsets", // song tags and PDFs in older Japanese and Chinese encodings
                "jdk.localedata", // the clock's format and dates in the system's language (beyond US English)
                "jdk.accessibility", // screen readers on Windows (the Java Access Bridge)
            )
            // licenses/libraries.txt beside the app (in its resources folder), as well as inside it.
            appResourcesRootDir.set(layout.dir(besideTheApp.map { it.destinationDir.parentFile }))

            // The installers' icons, drawn by tools/make_icon.py (docs/design/icon.md).
            windows {
                iconFile.set(project.file("icons/fumibako.ico"))
                // Never change: it's how a new installer knows to replace the old one.
                upgradeUuid = "f9afcf80-a171-41b0-bbf3-e843de80eb3f"
                perUserInstall = true
                menu = true
                shortcut = true
            }
            macOS {
                bundleID = "dev.fumibako.desktop"
                iconFile.set(project.file("icons/fumibako.icns"))
                // A macOS package's version can't start at 0: the build number counts instead
                // (the app itself says $versionName).
                packageVersion = "1.0.$versionCode"
            }
            linux {
                packageName = "fumibako"
                iconFile.set(project.file("icons/fumibako.png"))
            }
        }
    }
}
