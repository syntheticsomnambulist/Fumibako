// Build logic shared by the phone (app) and the desktop: the libraries' notices (LibraryNotices.kt).
plugins {
    `kotlin-dsl`
}

repositories {
    // The Maven Central mirror first, as in the main build's settings.gradle.kts.
    maven("https://maven-central.storage-download.googleapis.com/maven2/")
    mavenCentral()
}
