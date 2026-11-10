// buildSrc is a build of its own, so it lists its plugins' repositories here. The kotlin-dsl plugin
// comes from the Gradle Plugin Portal, which sends its Kotlin dependencies on to Maven Central: the
// mirror first, as in the main build's settings.gradle.kts.
pluginManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
    }
}
