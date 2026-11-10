---
name: best-practices-android
description: >-
  Android, Gradle, and Compose (Android and Desktop) practices for Claude Code containers: Maven
  Central mirror, Android SDK setup, the shared Gradle daemon and cache, Robolectric, Compose and
  screenshot testing, Xvfb desktop UI tests, hardware and device gaps, debug signing and keystores,
  apksigner verification, preview APK delivery, and database migration preservation. Read before
  running Gradle builds or Android, Robolectric, or screenshot tests, touching signing or keystores,
  building APKs for testers, or changing database schemas. Use together with best-practices.
---

# Android Best Practices

This skill extends `best-practices`; its precedence rules and verification and reporting standards
apply here too. These lessons come from Claude Code containers, which have no physical device,
emulator, or GPU, and share one Gradle daemon across sessions. Where a rule names a condition, the
condition is part of the rule.

## 1. Environment setup

- **Maven Central mirror (when needed).** Direct Maven Central requests from containers often get
  HTTP 429 rate limits that retries and `--max-workers=1` don't fix. When that happens, first check
  whether the repo already configures a mirror; don't rewrite repository configuration that works.
  If it doesn't, use Google's mirror:
  `https://maven-central.storage-download.googleapis.com/maven2/`.
  - **Repository-level (preferred, permanent):** declare the mirror before `mavenCentral()` in both
    `pluginManagement` and `dependencyResolutionManagement` in `settings.gradle.kts`. This is a
    shared build change, so it needs to reach every active branch.
  - **Robolectric runtime downloads** bypass Gradle's repositories. Set the system property
    `robolectric.dependency.repo.url` to the mirror on every `Test` task in the root
    `build.gradle.kts`. (`robolectric.defaultRepositoryUrl` is unsupported and has no effect.)
  - **Fallback without touching the repo:** pass the bundled init script on every invocation:
    `./gradlew -I .claude/skills/best-practices-android/scripts/maven-mirror.init.gradle <tasks>`.
    Don't write to `~/.gradle/init.d`, since container policies may reject writes to the home
    directory.
- **Android SDK.** If `ANDROID_HOME` is unset, run the repo's SDK setup script if it has one,
  otherwise `bash .claude/skills/best-practices-android/scripts/android-sdk.sh`, from the repo
  root. It installs platforms, build-tools, and platform-tools and records `sdk.dir` in
  `local.properties`, which must stay gitignored. Don't edit `~/.bashrc` or rely on shell `export`.
- **Warm the Gradle cache before launching parallel agents.** Run an initial build in the primary
  session first so dependencies and plugins are cached, instead of every agent downloading at once
  and tripping proxy rate limits.
- **Never run `./gradlew --stop` in a shared container.** The daemon is shared by all concurrent
  sessions and subagents, so stopping it kills their builds too. If a daemon seems wedged, ask the
  user.
- **Gradle output.** The general compact-output rule applies: use `-q` or `--console=plain`, and
  read failures from `build/test-results/**/*.xml`.

## 2. Testing

### Deterministic test environments

Tests should explicitly control everything that varies between runs: time, time zone, screen size,
environment-dependent inputs, and rendering.

- **Pin a fixed time zone and clock** in fixtures. Tests on relative timestamps or date-bucketed
  records fail across midnight UTC. When a test fails unexpectedly, check whether it also fails on
  the previous commit before assuming a code regression.
- **Declare environment variables as task inputs.** Any test task whose behavior or output
  directory depends on an environment variable (such as `HARNESS_SCREENSHOTS_DIR`) must declare it
  as a Gradle task input; otherwise the build cache reports a pass without running or producing
  output.
- **Generate screenshot baselines under identical conditions,** with clocks frozen and animations
  or random jitter removed, so diffs reflect real changes.

### Screenshot comparisons

- **Make sure the comparator can see what you're testing.** A comparator that checks only
  transparency passes every color change *(observed)*. Before trusting a screenshot test, you may need to confirm
  it compares color channels: deliberately change a color and check that the test fails.
- **Detect pixel byte order at runtime.** Under Robolectric's desktop Skia pipeline,
  `Bitmap.copyPixelsToBuffer` produces BGRA, while physical devices produce RGBA. Don't assume a
  fixed channel order.

### Robolectric and Compose

- **Text-field dialogs** (such as an `AlertDialog` containing an `OutlinedTextField`) can fail to
  reach idle on compact phone sizes. Test them in dedicated cases at default screen dimensions.
- **Wait for navigation to finish** before asserting on the destination, and wait on something
  unique to it. Waiting for text that also appears on the previous screen can pass before the
  transition completes *(observed in tour steps)*; wait for a destination-only element or for the
  source screen to disappear.
- **Scroll lazy lists** (such as `LazyColumn`) to the target item before asserting on it, since
  off-screen items aren't composed.
- **Keep disk and network I/O off the main thread.** StrictMode and main-thread restrictions that
  devices enforce may not trigger in Robolectric or desktop JVM tests, so passing tests prove
  nothing here. Run database, preferences, and network work on background dispatchers.

### Compose Desktop

- **Run multi-screen desktop flows under `Xvfb`.** Compose Desktop unit tests can't reliably
  navigate across screens off Swing's Event Dispatch Thread. If UI tests fail unexpectedly, check
  that the `Xvfb` process is still running.

### Hardware and device gaps

- **Report what the container couldn't test.** Note untested device-specific behavior in status
  reports and handoffs.
- **Static review is not runtime verification.** For code that can't run in the container (OpenGL
  shaders, RenderScript, hardware-accelerated rendering), a careful review, optionally by an
  independent read-only review agent, can catch crashes, logic errors, and performance problems.
  Report it as reviewed, not tested, and name the remaining validation gap.
- **Investigate device-only failures from the platform, not by guessing.** When a tester reports
  an error you can't reproduce, especially a vague one like "App not installed," read the relevant
  Android platform source (PackageManager, installer routines) and ask for logcat or `adb` output
  to pin down the cause.

## 3. Signing and distribution

- **Why signing continuity matters.** Android rejects an update signed with a different
  certificate, forcing an uninstall that wipes tester data. Each fresh container generates its own
  ephemeral debug keystore, so debug and preview APKs must be signed with a shared keystore.
- **Supply the shared keystore via an environment variable** (such as
  `HARNESS_DEBUG_KEYSTORE_B64`), never from the repo. Never echo, log, or message its contents or
  password; if it must be decoded to a file for a build, keep that file outside the repo.
- **Preserve signing lineage.** If migrating away from a previously committed keystore, keep the
  lineage file in the repo and preserve default `apksigner` capabilities (especially `permission`,
  which AndroidX signature permissions need). Never delete or recreate an established lineage, and
  never replace a signing key without explicit user approval.
- **Verify every APK before delivering it.** Run `apksigner verify --print-certs <apk>` and confirm
  the certificate matches the shared debug certificate. Also confirm the application ID and that
  `versionCode` hasn't decreased, since either would block an in-place update.
- **Preview APKs on request,** with a concise verification checklist ordered from highest-risk
  change to lowest.

## 4. Tester data preservation

- **Keep the complete migration history** (Room, SQLDelight, or SQLite) back to the earliest
  schema on any tester device. Testers may skip preview builds, so the app must migrate directly from
  old versions.
- **Test migrations from the oldest supported version,** not just the previous one (for Room, with
  `MigrationTestHelper`).
- **No destructive migration fallbacks** (such as `fallbackToDestructiveMigration`) in builds
  testers receive, unless the user explicitly approves. They silently wipe tester data.
