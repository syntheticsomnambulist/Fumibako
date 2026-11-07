import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Room database and the stores, shared by the phone (androidMain: the phone's own SQLite) and
// the desktop (desktopMain: SQLite bundled with the app). One schema and one MIGRATIONS for both.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    android {
        namespace = "dev.aiharness.app.data"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.androidx.room.runtime)
            api(libs.androidx.datastore.preferences.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.sqlite.framework)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.junit)
            implementation(libs.kotlin.test.junit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Exported schemas: what the migration tests build old databases from (see HarnessDatabase.MIGRATIONS).
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}
