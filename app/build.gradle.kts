import com.android.apksig.ApkSigner
import com.android.apksig.SigningCertificateLineage
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Base64
import org.gradle.work.DisableCachingByDefault

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val debugKeystore: File? = sharedDebugKeystore()

android {
    namespace = "dev.aiharness.app"
    compileSdk = 37

    defaultConfig {
        // Fumibako. The code keeps its dev.aiharness packages; only the installed app's id changed.
        applicationId = "dev.fumibako.app"
        minSdk = 26
        targetSdk = 36
        // Set once for the phone and the desktop, in gradle.properties.
        versionCode = providers.gradleProperty("fumibako.versionCode").get().toInt()
        versionName = providers.gradleProperty("fumibako.versionName").get()
    }

    signingConfigs {
        // Debug builds from every machine and CI run share one signing key, so a new build installs
        // over the old one. The key is never committed; it comes from (first match):
        //   HARNESS_DEBUG_KEYSTORE      path to a keystore file
        //   HARNESS_DEBUG_KEYSTORE_B64  the keystore, base64 (CI secret / cloud environment variable)
        // With neither, the machine's own Android debug key is used, and its builds won't install
        // over testers' copies. Store and key passwords default to "android"
        // (HARNESS_DEBUG_KEYSTORE_PASSWORD overrides both). See docs/going-public.md.
        debugKeystore?.let { keystore ->
            getByName("debug") {
                storeFile = keystore
                storePassword = debugKeyPassword()
                keyAlias = debugKeyAlias()
                keyPassword = debugKeyPassword()
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

// Key rotation. Builds up to 0.4.0, the v0.2.2 testers have among them, were signed with a key that
// was committed while the repository was private, so it's treated as public: it's kept, retired, in
// app/signing/retired-debug.keystore. Debug builds are now signed with the shared key above plus
// app/signing/debug-lineage, the retired key's signed word that the shared key replaces it. On
// Android 9 and newer, a build installs over one signed with the retired key and keeps its letters,
// and once it has, the retired key can no longer sign an update to that install. Android 8 checks
// only the older signature scheme, which needs the lineage's first key, so the retired key still
// signs that part. Never delete or regenerate the lineage; a later key change extends it
// (apksigner rotate --in app/signing/debug-lineage ...).
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        val keystore = debugKeystore ?: return@onVariants
        val rotate = tasks.register<RotateSigningTask>("rotate${variant.name.replaceFirstChar { it.uppercase() }}Signing") {
            this.keystore.set(keystore)
            retiredKeystore.set(layout.projectDirectory.file("signing/retired-debug.keystore"))
            lineage.set(layout.projectDirectory.file("signing/debug-lineage"))
            alias.set(debugKeyAlias())
            password.set(debugKeyPassword())
        }
        val request = variant.artifacts.use(rotate)
            .wiredWithDirectories(RotateSigningTask::apks, RotateSigningTask::signed)
            .toTransformMany(SingleArtifact.APK)
        rotate.configure { transformation.set(request) }
    }
}


// The notices for the libraries the phone carries (buildSrc/LibraryNotices.kt), in its assets
// for About. The APK leaves out the libraries' own META-INF license files (packaging, above); this
// list carries their texts and notices instead.
androidComponents {
    onVariants { variant ->
        val notices = libraryNotices("${variant.name}LibraryNotices", "${variant.name}RuntimeClasspath") {
            audience.set("the phone")
            appVersion.set(providers.gradleProperty("fumibako.versionName"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(notices, LibraryNotices::outputDir)
    }
}

// The screenshot tours run only with HARNESS_SCREENSHOTS_DIR set (ScreenshotTour). Such a run always
// really runs: never "up to date" and never from the build cache, which would pass it without
// drawing a single picture.
tasks.withType<Test>().configureEach {
    val shots = providers.environmentVariable("HARNESS_SCREENSHOTS_DIR")
    inputs.property("screenshotsDir", shots.orElse(""))
    outputs.upToDateWhen { !shots.isPresent }
    outputs.cacheIf { !shots.isPresent }
}

dependencies {
    implementation(project(":core"))
    // The shared screens (ui) and database (data); see docs/plan-desktop.md.
    implementation(project(":ui"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    // Cuts the subject out of a photo for a sticker, on the phone (Google Play services).
    implementation(libs.mlkit.subject.segmentation)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
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
    testImplementation(libs.okhttp.mockwebserver)
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
    return null
}

fun debugKeyPassword(): String = providers.environmentVariable("HARNESS_DEBUG_KEYSTORE_PASSWORD").orNull ?: "android"

fun debugKeyAlias(): String = providers.environmentVariable("HARNESS_DEBUG_KEY_ALIAS").orNull ?: "androiddebugkey"

/** Re-signs debug APKs with the shared key, the retired key and the lineage between them (see androidComponents above). */
@DisableCachingByDefault(because = "Signing is quick, and the key shouldn't decide cache entries")
abstract class RotateSigningTask : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val apks: DirectoryProperty
    @get:OutputDirectory abstract val signed: DirectoryProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val keystore: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val retiredKeystore: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val lineage: RegularFileProperty
    @get:Input abstract val alias: Property<String>
    @get:Internal abstract val password: Property<String>
    @get:Internal abstract val transformation: Property<ArtifactTransformationRequest<RotateSigningTask>>

    @TaskAction
    fun sign() {
        val lineage = SigningCertificateLineage.readFromFile(lineage.get().asFile)
        val shared = signer("shared", keystore.get().asFile, alias.get(), password.get())
        // The retired key's password is "android"; it's public anyway.
        val retired = signer("retired", retiredKeystore.get().asFile, "androiddebugkey", "android")
        check(lineage.certificatesInLineage.last() == shared.certificates.first()) {
            "The debug key isn't the one app/signing/debug-lineage hands over to. Use the shared key " +
                "(docs/going-public.md), or build with neither HARNESS_DEBUG_KEYSTORE nor HARNESS_DEBUG_KEYSTORE_B64 set."
        }
        check(lineage.certificatesInLineage.first() == retired.certificates.first()) {
            "app/signing/retired-debug.keystore isn't the key app/signing/debug-lineage starts from."
        }
        transformation.get().submit(this) { artifact ->
            val input = File(artifact.outputFile)
            val output = signed.get().file(input.name).asFile
            ApkSigner.Builder(listOf(retired, shared))
                .setInputApk(input)
                .setOutputApk(output)
                .setSigningCertificateLineage(lineage)
                // The shared key counts from Android 9 (v3 signing); Android 8 reads the retired key's v2 signature.
                .setMinSdkVersionForRotation(28)
                .setV1SigningEnabled(false)
                .build()
                .sign()
            output
        }
    }

    private fun signer(name: String, file: File, alias: String, password: String): ApkSigner.SignerConfig {
        val pass = password.toCharArray()
        val store = KeyStore.getInstance(file, pass)
        val key = store.getKey(alias, pass) as PrivateKey
        val certificates = store.getCertificateChain(alias).map { it as X509Certificate }
        return ApkSigner.SignerConfig.Builder(name, key, certificates).build()
    }
}
