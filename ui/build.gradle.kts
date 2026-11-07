import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Every Compose screen, shared by the phone and the desktop (docs/plan-desktop.md). Shared code is
// in commonMain; it can use the JDK (java.time, java.io), since both targets are JVMs, but not
// Android. What a screen needs from the platform is an expect declaration (ui/platform) with its
// actual in androidMain (the phone's code, as it was) and desktopMain.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "dev.aiharness.app.ui"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        // The bundled fonts are Android resources (R.font), as they were in the app.
        androidResources { enable = true }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":data"))
            api(libs.compose.mp.runtime)
            api(libs.compose.mp.foundation)
            api(libs.compose.mp.ui)
            api(libs.compose.mp.material3)
            implementation(libs.compose.mp.material.icons.extended)
            implementation(libs.compose.mp.components.resources)
            api(libs.jetbrains.lifecycle.runtime.compose)
            api(libs.jetbrains.lifecycle.viewmodel.compose)
            api(libs.jetbrains.navigation.compose)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.browser)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.ui.compose)
            // Cuts the subject out of a photo for a sticker, on the phone (Google Play services).
            implementation(libs.mlkit.subject.segmentation)
        }
        getByName("desktopMain") {
            dependencies {
                implementation(libs.compose.mp.desktop)
                // Dispatchers.Main (view models, lifecycle-aware collecting) on the desktop.
                implementation(libs.kotlinx.coroutines.swing)
            }
            // The same font files the phone reads as R.font, on the desktop's classpath (font/<name>.ttf).
            resources.srcDir("src/androidMain/res")
        }
    }
}

compose.resources {
    packageOfResClass = "dev.aiharness.app.ui.res"
}
