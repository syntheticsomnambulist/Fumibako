# Desktop plan: Fumibako on a computer

Started 2026-11-06, after v0.4.0, by the desktop session (branch `claude/epic-davinci-rpggbc`),
beside the build session's v0.5. **Friends get a desktop build with v0.5**, the next tester build,
so the desktop work runs alongside v0.5 and ships with it. Agreed on 2026-11-07, with your
answers below; the reorganization has started (see *Progress*).

## Your answers (2026-11-07)

- **All three systems:** Windows, macOS and Linux. You have a Windows PC, so Windows is tried
  first and most; the macOS and Linux builds come from CI and get tried more slowly.
- **The window in v0.5:** the phone's layouts in a resizable window. Wider layouts are wanted,
  designed later, for a later version.
- **Phone and computer:** two separate Fumibakos, with v0.5's backups to move a persona or
  everything between them. Sync, if ever, is a feature of its own.
- **The move** lands before the build session starts v0.5.
- **Closing the window** keeps Fumibako in the tray, so heartbeats go on (on by default, and the
  first close says so). Starting at login stays off until you turn it on.

## The aim

- **The same Fumibako on a computer:** the same personas, letters, memory book, planner, stickers
  and heartbeat, in the same look.
- **One set of screens.** The Compose screens move into a module both apps build, with Compose
  Multiplatform, so each screen is written once and v0.5's new screens reach both.
- **The same version on both:** 0.5.0 on the phone and on the computer, from one version number in
  the build. A v0.5 backup opens in its own version or a later one (a backup from a newer version
  is refused until you update), so with matching versions, v0.5's import and export moves a
  persona (or everything) either way between phone and computer.
- **The phone app doesn't change** because of the move: the same app id, database file, settings
  file and key alias, so testers' letters survive the update (see *Data and migrations*).

## What the code allows

- **`core` runs on a computer as it is.** It's plain Kotlin/JVM: the providers (the Anthropic SDK,
  Gemini, OpenAI-compatible, NovelAI), memory, context epochs, the heartbeat engine, the planner,
  seasons, holidays, image hosts, link previews and music search. Its tests run on any JVM.
- **The app** is 78 Kotlin files, about 20,800 lines:
  - **26 files (5,300 lines) don't touch Android** (paper, tape, letters, the thread, the trays,
    the month page, the planner's pieces, the palette, die-cut stickers, the repositories,
    `GenerationManager`). They move as they are.
  - **14 files (5,500 lines) use only lifecycle and navigation** (view models,
    `collectAsStateWithLifecycle`, `NavController`), which have multiplatform builds at the
    versions the app uses (JetBrains' lifecycle 2.11.0 and navigation 2.10.0). They move with
    their imports changed.
  - **26 screen files (8,200 lines) touch Android in a few places each:** `LocalContext`, pickers
    and permission launchers, `android.text.format.DateFormat` for the phone's clock setting,
    `Toast`, bitmaps, the time picker dialog, WebView and Custom Tabs, and fonts and icons from
    `res/`. They move with those calls put behind small interfaces that each platform fills in. A
    few keep an Android part: ML Kit's photo cutter, the video viewer, the in-app browser, the
    burn shader.
  - **12 shell files (1,800 lines) are Android's by nature:** the activity and application, the
    Keystore, DataStore's Android factory, Room's Android setup, WorkManager, notifications and
    their actions, media3 (playback, its session service, the video shrinker) and
    `CalendarContract`. The database and the blob store move to the shared side; the rest stays in
    `app`.
- **The versions line up** (checked 2026-11-06): the app's Compose BOM (2026.09.00) resolves to
  Compose 1.12.1, and Compose Multiplatform 1.12 is built on Compose 1.12 (1.12.2 is its current
  stable release). Room 2.8.5 (and `room-testing`), the bundled SQLite driver and DataStore 1.3.0
  all have JVM builds.

## How it's laid out

```
core/      unchanged: plain Kotlin/JVM, all agent logic
data/      new, multiplatform (Android and desktop): the Room database, its migrations and
           exported schemas (moved from app/), the repositories, the settings store, the blob store
ui/        new, multiplatform: every Compose screen, the theme, the view models, GenerationManager,
           AppContainer (seeding Aria, recent stickers, "While you were away"), and platform/:
           what screens need from the platform, with the phone's code in ui/src/androidMain
           (pickers, the video and web players, ML Kit's cutter, bitmaps, the fonts as R.font)
           and the desktop's in ui/src/desktopMain
app/       the Android shell: MainActivity, AndroidPlatform (the services AppContainer is
           handed), the WorkManager heartbeat, notifications, media3 import and playback, the
           Keystore, phone calendars
desktop/   new, the desktop shell: main(), the window, DesktopPlatform, the desktop's services
```

- **Underneath, Android stays as it is.** On Android, Compose Multiplatform is Jetpack Compose,
  so the phone draws what it draws today. `data` and `ui` build their Android side with AGP 9's
  Android-KMP library plugin; `app` stays a plain Android application.
- **The database is Room on both.** The phone keeps its own SQLite (`AndroidSQLiteDriver`); the
  desktop bundles SQLite (`BundledSQLiteDriver`). One schema and one `MIGRATIONS`, written once
  against `SQLiteConnection`, as multiplatform Room requires.
- **Platform interfaces** (built; `ui/src/commonMain/.../platform`): `AppPlatform`, the services
  the container is handed (folders, secrets, settings, app state, media import, playback,
  heartbeats, About), and expect declarations for what screens draw or launch (pickers, the camera,
  back, permissions, the clock's default, links and files, decoding pictures, the video player, the
  web embed, song previews). The bundled fonts, sticker drawing, the photo cutters and the burn are
  expect declarations beside their screens. Each Android actual is the phone's own code, moved from
  the screen it was in.
- **The package names** stay `dev.aiharness.*`, as the app's do now.

## What's different on a computer

| | On the phone (today) | On a computer (v0.5) |
| --- | --- | --- |
| Where data lives | The app's private storage | The user's app-data folder (`%APPDATA%\Fumibako`, `~/Library/Application Support/Fumibako`, `~/.local/share/Fumibako`), outside the install folder, so updates and reinstalls keep your letters |
| API keys | Encrypted with a Keystore key | Encrypted with a key kept in the system's keychain (Windows Credential Manager, the macOS Keychain, the Linux Secret Service) |
| Heartbeats | WorkManager, even with the app closed | While Fumibako runs. Closing the window keeps it in the tray (the menu bar on macOS), so personas go on writing; **Quit** stops them. A heartbeat missed while the computer slept or Fumibako was quit runs once on waking or at the next launch, not once per missed hour. **Start Fumibako when I log in**, off by default |
| A reminder that wakes a persona | A one-off WorkManager job | A timer in the running app; a missed one runs at launch, or is missed after half a day, as on the phone |
| Notifications | With "Busy for an hour", "Till tonight", "Till tomorrow" | The system's notifications, through the tray; a click opens the letter. "Busy for now" moves to the tray's menu, since notifications from a Java app have no buttons |
| Photos and files | The photo picker, the camera, Files | A file dialog, dragging onto your note, or pasting a picture. No camera |
| PDFs | The first page, from Android's renderer | The first page, from PDFBox (Apache 2.0) |
| Songs | Tags and artwork from the file; ExoPlayer with a media session | Tags from the file; previews and songs play in the app (the player library is chosen in step 1) |
| Videos | Three frames for models that can't watch; shrunk to fit | Sent whole where the model takes them (Gemini), else by their label. No frames and no shrinking in v0.5 |
| Links | Custom Tabs, and a WebView for players | The default browser |
| A sticker from a photo | ML Kit cuts out the subject | The plain-background cutter (a photo on a plain background), which the phone also has |
| Phone calendars | Read-only, through `CalendarContract` | None in v0.5; Settings, Planner leaves the section out |
| Sunrise and sunset | The phone's coarse location, or a latitude and longitude typed in | A latitude and longitude typed in |
| The clock | The phone's 12/24-hour setting | The system's time format, from its locale |
| The burn, in the memory book | A GPU shader on Android 13 and later, else the simpler fallback | The fallback first; the shader ported to Skia later |
| One at a time | One app | One Fumibako per user on a computer: opening it again brings the open window forward |

## Mouse, keyboard and the window

The screens are designed for a phone held upright. For v0.5:

- **The phone's layouts in a resizable window**, about 440 by 860 at first. In a wider window the
  content stays centered at a phone-like width. No new layouts in v0.5.
- **A long press becomes a right-click, or a plain drag** where it starts one: dragging a sticker
  from the tray, moving the bookmark, a plan's stamps.
- **Stickers:** drag to move; the scroll wheel (or the handles) to resize and Shift with the
  wheel to turn, in place of pinch and twist.
- **Writing:** Enter starts a new line, since letters are long; Ctrl+Enter (Cmd+Enter on a Mac)
  sends. Escape goes back, like the phone's back gesture.
- **Scrollbars** on long pages, and a hover state on what can be clicked.
- **Wider layouts** (the letters list beside a thread, a week with room to breathe) come in a
  later version, designed with the design session first.

## v0.5 on both: notes for the build session

v0.5's design (`docs/design/v0.5.md`, on the design session's branch, `ccr-09dbf6b8-2c3jno`) was
written for the phone. Built this way, each part works on the computer too:

- **Import and export go in `core`:** reading and writing cards (PNG chunks, `.charx`, JSON), the
  `.fumibako` zip and its manifest, lorebooks, and locking backups (AES-256-GCM and
  PBKDF2-HMAC-SHA256 are in the JDK, not only in Android). The app only picks or saves the file:
  **Save the file…** is Android's save dialog on the phone and a save dialog on the computer;
  **Share…** is the phone's alone.
- **Searching letters:** the FTS4 word index lives in Room, and the desktop's bundled SQLite has
  FTS4 as well. Use a tokenizer both SQLites have, and run the search tests on both.
- **Fonts:** the bundled Latin subsets go into Compose resources and serve both. Japanese
  "through Android's downloadable fonts" is the phone's way; the computer downloads the same Google
  Fonts files on first use into its app-data folder, with **Download Japanese fonts now** as on the
  phone. The per-character chain from a Latin face to its Japanese companion
  (`Typeface.CustomFallbackBuilder` on Android) needs its desktop equivalent, checked in step 1.
- **Your own font files** load from a file on both, with the same 20 MB limit and the same plain
  refusal for a file that won't load.
- **The context slip, thinking lines, the model-change notice, the tapes and the month
  envelopes** are shared screens with nothing per platform.
- **New screens go in `ui`** once the move has landed. What a screen needs from the platform, it
  asks for through an interface, never `LocalContext`; `ui`'s shared code can't see Android, so the
  compiler keeps to this.

## Data and migrations

The rules in CLAUDE.md hold through the move:

- **Every kept step stays.** 2→3, 3→4 and 4→5 move to `data`, rewritten against
  `SQLiteConnection` with the same SQL, and stay until friends have v0.5 (their phones still have
  database version 2). The test that opens a version 2 database stays, and runs twice: on Android
  (Robolectric, the phone's SQLite) and on the JVM (the desktop's).
- **The phone's files stay where they are:** the database's name, DataStore's `settings` file, the
  `app_state` and `secrets` preferences and the Keystore alias, so v0.4 to v0.5 is an ordinary
  update over the top.
- **The desktop starts at v0.5's database version,** with nothing older to migrate, and from then
  on takes the same steps as the phone: one `MIGRATIONS` and one set of tests for both.
- **The schemas move** from `app/schemas` to `data/schemas`, with CLAUDE.md and the README
  updated in the same change.
- **Model fields and settings** work as now: defaults, no fallbacks. Settings that mean nothing on
  a computer (phone calendars and their stamps) stay in `AppSettings` and go unused there, so an
  Everything backup keeps them whichever way it travels.

## Tests and builds

- **Checks before pushing** gain the desktop's, for example
  `./gradlew :core:test :data:jvmTest :ui:jvmTest :app:testDebugUnitTest :desktop:test` (the task
  names settle in step 1), and CLAUDE.md lists them.
- **Much of what Robolectric runs today can run on a plain JVM:** the Room repositories and the
  migration test with the bundled driver, and UI tests with Compose's desktop test rules, which
  are quicker. The Robolectric tests stay for what's Android's own.
- **A desktop screenshot tour** renders offscreen (no display needed), visiting the phone tour's
  screens at the desktop window's size. The phone's tour carries on.
- **The move has its own check:** the phone's screenshot tour renders the same before and after it,
  compared pixel by pixel, and every test passes.
- **CI:** today's job, plus a desktop job on Windows, macOS and Linux runners that runs the desktop
  tests and packages an installer for each (`.msi`, `.dmg`, `.deb`), uploaded beside the APK.
- **Unsigned for friends:** Windows SmartScreen and macOS Gatekeeper warn about unsigned apps, and
  [`docs/testers.md`](testers.md) gets the steps past them on each system, Windows first. Signing (an Apple
  Developer ID with notarization, a Windows code-signing certificate) costs money and can wait for
  more testers. To check in step 1: whether macOS asks for the keychain password after each
  unsigned update.
- **Versions:** set once and read by both builds. A macOS package can't have a version starting
  at 0, so the `.dmg` carries its own build number; the app itself says 0.5.0 everywhere.

## Order of work

1. **A spike on this branch,** merged nowhere: a desktop window showing one shared screen (the
   letters list over an in-memory harness) with this repo's toolchain (Compose Multiplatform 1.12,
   AGP 9.5, Kotlin 2.3.21, KSP), and multiplatform Room opening a version 2 database through the
   kept migrations with both drivers. It also settles:
   - whether code shared by just the two JVM targets can call `java.time` and `java.io` directly
     (the screens use `java.time` about 70 times), or needs small helpers;
   - the font chain from Latin to Japanese faces on the desktop;
   - the tray's notifications on each system, and the keychain library;
   - the player for songs and previews, with a license that fits the app's GPL-3.0-or-later;
   - whether the 26 Material icons the app uses come from Compose Multiplatform's icon artifact
     (it stopped at 1.7.3) or are copied in as vector resources.
2. **The move,** as one change, timed with the build session (see below): the `data` and `ui`
   modules, the files moved with `git mv` so their history follows, the platform interfaces in
   place of the Android calls, and `R` resources as Compose resources. The phone app behaves
   exactly as before.
3. **The desktop shell:** the window, the tray and its menu, the app-data folder, keys in the
   keychain, the database and settings, one Fumibako at a time, and the first launch (Aria).
4. **The desktop's services:** heartbeats and reminders while running, with catch-up;
   notifications; file dialogs, drag and drop, and pasting; pictures; PDFs; songs and previews;
   opening links; the plain-background sticker cutter.
5. **Mouse and keyboard:** right-clicks and drags for holds, the wheel for stickers, Ctrl+Enter to
   send, Escape for back, scrollbars, the window's size.
6. **v0.5 on the computer,** part by part as the build session lands it: checking each on the
   desktop and adding the desktop's side (file dialogs for import and export, the Japanese font
   download).
7. **Polish, tests and the installers:** the desktop screenshot tour; a desktop section in
   `docs/testers.md` (installing, what keeps heartbeats running, where your letters live, what's not
   there yet) and in the README; installers from CI; **0.5.0 for both**.

## Working beside the build session

- **The move touches every screen file,** so it should land at a quiet point: after v0.4.0 and
  before the build session starts v0.5 (agreed). Later than that, v0.5's work and the move would
  collide in the same files.
- **After the move,** the build session writes each screen once in `ui` and needs to know little
  about the desktop. When a v0.5 part needs a desktop side, the desktop session adds it.
- **This branch** carries the desktop work. The move goes into the build branch as one change, so
  both sessions carry on from it.
- **Signing stays the build session's:** the move leaves `app/signing/` and the debug APK's
  re-signing in `app/build.gradle.kts` as they are.

## Progress

- **2026-11-07:** your answers are in; the build and design sessions have been told.
- **Step 1, the spike: done.** A throwaway project with this repo's versions built and ran:
  - Shared code can use `java.time` and `java.io`: with only JVM targets, Kotlin compiles the
    shared code once per target.
  - Multiplatform Room migrates a version 2 database on the bundled SQLite.
  - Compose resources reach the APK; the desktop renders offscreen for tests.
  - Still open, for steps 3 and 4: the font chain from Latin to Japanese faces, the tray's
    notifications on each system, the keychain library, and the in-app media player.
  - The extended icons come from Compose Multiplatform's last build of them (1.7.3).
- **Step 2, the move: done, on this branch** (not yet in the build branch). `data` and `ui` hold the
  database and every screen; `app` is the Android shell; `desktop` opens the shared screens.
  - **The phone is unchanged.** The 268 screenshots of the phone's tour are identical before and
    after, pixel for pixel, and no AndroidX library changed version. The debug and release APKs
    build; the release APK grew about 20 KB (14.1 MB).
  - **Testers' letters are safe.** The kept steps (2→3, 3→4, 4→5) are the same SQL, and Room's
    identity hash is unchanged, so an existing database opens as before. The phone's
    MigrationTest reads `data/schemas`, and `DesktopMigrationTest` runs the same steps on the
    desktop's SQLite.
  - **Tests:** everything passes, except two things that were already there. One is
    `ScreenshotTour.largeTextTour`, which fails before the move too: it waits for "THE WEEK STARTS
    ON" after opening Planner settings. The other is `nightTour`, which once in five runs read
    ExoPlayer off the main thread under load (unchanged code; it passed the other four).
  - **The version** is set once, in `gradle.properties`, for both apps.
  - **CI** also builds the desktop on Windows, macOS and Linux runners: its tests and an installer
    for each. The Linux `.deb` builds here (164 MB with the whole Java runtime, trimmed later).
- **Step 3 begun: a first desktop shell.**
  - It opens the shared screens in a 440 by 860 window, with a fresh database and Aria seeded.
    Letters, Personas, Planner, Settings and a persona's page were checked in a real window.
  - First versions of its services: heartbeats while it runs, photos and files in letters,
    links and files opening outside, saving through a dialog.
  - Not yet: the tray, the keychain (API keys are in a file only you can read, unencrypted until
    the keychain comes), songs and videos playing in the app (they open in the computer's own
    player), PDFs' pages, and the desktop's own wording ("on this phone" still shows).

## Noted for later

- Sync between the phone and the computer.
- Calendars on the computer: a calendar's private iCal link (Google, Outlook and iCloud each offer
  one), read-only like the phone's.
- Cutting a sticker's subject out of a photo on the computer (an on-device model).
- Video frames and shrinking on the computer; a camera.
- The burn shader on Skia.
- Wider layouts, a menu bar, and more keyboard shortcuts.
- Signing, notarization, and updates that install themselves.
- Found during the move:
  - Photos from a phone camera may come in turned on the computer: Skia doesn't apply the EXIF
    orientation.
  - The installer carries the whole Java runtime (`includeAllModules`); trim it to the modules
    needed.
  - The desktop test composes on its own thread, so it can't navigate yet (navigation checks it's
    on Swing's). The desktop screenshot tour will need to run on that thread.
  - The camera item in the letter tray should hide on the desktop.
