import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.aiharness.app"
    compileSdk = 37

    defaultConfig {
        // Fumibako. The code keeps its dev.aiharness packages; only the installed app's id changed.
        applicationId = "dev.fumibako.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.3.1"
    }

    signingConfigs {
        // Debug builds from every machine and CI run share one signing key, so a new build installs
        // over the old one. The key comes from (first match):
        //   HARNESS_DEBUG_KEYSTORE      path to a keystore file
        //   HARNESS_DEBUG_KEYSTORE_B64  the keystore, base64 (CI secret / cloud environment variable)
        //   app/debug.keystore          committed while the repo is private; see docs/going-public.md
        // With none of these, the machine's own Android debug key is used. Store and key passwords
        // default to "android" (HARNESS_DEBUG_KEYSTORE_PASSWORD overrides both).
        sharedDebugKeystore()?.let { keystore ->
            getByName("debug") {
                storeFile = keystore
                storePassword = providers.environmentVariable("HARNESS_DEBUG_KEYSTORE_PASSWORD").orNull ?: "android"
                keyAlias = providers.environmentVariable("HARNESS_DEBUG_KEY_ALIAS").orNull ?: "androiddebugkey"
                keyPassword = providers.environmentVariable("HARNESS_DEBUG_KEYSTORE_PASSWORD").orNull ?: "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            // Duplicate metadata pulled in by the Anthropic SDK's Jackson / Kotlin deps.
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/versions/9/module-info.class",
                "META-INF/FastDoubleParser-*",
            )
        }
    }
}


ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    // Cuts the subject out of a photo for a sticker, on the phone (Google Play services).
    implementation(libs.mlkit.subject.segmentation)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}

/** The shared debug signing key, if one is configured (see the signingConfigs block). */
fun sharedDebugKeystore(): File? {
    providers.environmentVariable("HARNESS_DEBUG_KEYSTORE").orNull?.takeIf { it.isNotBlank() }?.let { return file(it) }
    providers.environmentVariable("HARNESS_DEBUG_KEYSTORE_B64").orNull?.takeIf { it.isNotBlank() }?.let { encoded ->
        val out = layout.buildDirectory.file("signing/debug.keystore").get().asFile
        out.parentFile.mkdirs()
        out.writeBytes(Base64.getMimeDecoder().decode(encoded.trim()))
        return out
    }
    return file("debug.keystore").takeIf { it.exists() }
}
