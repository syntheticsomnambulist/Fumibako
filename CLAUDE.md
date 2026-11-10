# Working on this repository

- **Data: migrations for the database, three versions back.** Friends are testing, so their letters
  should survive updates.
  - A Room schema change bumps `HarnessDatabase`'s version and adds a step to its `MIGRATIONS`,
    with a test against the exported schema (`data/schemas`). Keep the last three steps; phones on
    an older database start fresh, so there are usually no more than three old layouts.
  - **But never drop a step a tester's phone still needs:** keep every step from the database
    version of the oldest build friends were given, even if that's more than three. Phones that
    skip builds jump straight from that version, and dropping the step would wipe their letters.
  - Tester builds so far (the user says when one goes out): **v0.2.2** (database version 2) is
    the last one sent; **v0.5** is planned as the next. Until friends have v0.5, keep the steps
    from version 2 (2→3 onward), and keep the test that opens a version 2 database. Once they
    have it, steps older than v0.5's database version can go (subject to the three-step rule).
  - Model fields need no migration: rows are JSON, new fields get defaults, and a renamed field
    keeps its stored name with `@SerialName`. Don't write backfills or legacy fallbacks for them.
  - Settings (DataStore JSON) work the same way: defaults, no fallbacks.
- **The repository is private for now.** Before it goes public, follow
  [`docs/going-public.md`](docs/going-public.md). The code is GPL-3.0-or-later (`LICENSE`); the
  palette (MPL-2.0) and fonts (OFL) keep their own licenses.
- **Signing: testers' phones need the key chain.** Debug builds are signed with a shared key from
  `HARNESS_DEBUG_KEYSTORE_B64` (never committed) and carry `app/signing/debug-lineage`, which hands
  over from the retired key (`app/signing/retired-debug.keystore`) that signed builds up to 0.4.0.
  Never delete or regenerate either file, and never change the debug key without extending the
  lineage: a phone that can't verify the chain must uninstall, which wipes its letters. The
  retired key keeps every right in the lineage except rollback: AndroidX declares a signature
  permission, and without the permission right an update fails with "App not installed". Without the
  variable, builds use the machine's own debug key; send the user an APK only when it's signed with
  the shared key (`apksigner verify --print-certs` shows "CN=Fumibako Debug").
- **Screens are shared with the desktop:** they live in `ui/src/commonMain`, which can't use Android;
  what a screen needs from the platform goes through `ui/.../platform` (expect declarations, with
  the phone's code in `ui/src/androidMain`). The database is `data/`. See
  [`docs/plan-desktop.md`](docs/plan-desktop.md).
- **App text uses American spelling** (aging, color, license, summarize), since that's how the app's
  user writes.
- **Where things are:**
  - Plans and progress: [`docs/plan-v0.5.md`](docs/plan-v0.5.md) (current),
    [`docs/plan-v0.4.md`](docs/plan-v0.4.md), [`docs/plan-v0.3.5.md`](docs/plan-v0.3.5.md), [`docs/plan-v0.3.md`](docs/plan-v0.3.md), [`docs/plan-v0.2.md`](docs/plan-v0.2.md),
    [`docs/plan-v0.1.md`](docs/plan-v0.1.md).
  - The desktop version (alongside v0.5): [`docs/plan-desktop.md`](docs/plan-desktop.md).
  - Storage, and its future optimizations: [`docs/storage.md`](docs/storage.md).
  - Testers' guide: [`docs/testers.md`](docs/testers.md).
  - Design decisions: [`docs/design/README.md`](docs/design/README.md).
  - Architecture: [`README.md`](README.md).
- **Checks:**
  - Before pushing, run `./gradlew :core:test :data:desktopTest :app:testDebugUnitTest :desktop:test`.
    Gradle re-runs only the suites whose code changed (with nothing changed it takes seconds), so
    there's no need to pick suites by hand. These checks are the only ones a push gets: GitHub
    Actions doesn't run on pushes (its minutes and storage are limited while the repository is
    private). A build there runs on request (workflow `build.yml`, inputs `tests`, `windows`,
    `macos`, `linux`, `phone`; tests and Windows by default); start one only when the user asks
    for one, such as installers for a release, and leave `macos` off unless asked (its minutes
    count ten times).
  - When a change can alter what a screen shows (`ui/`, `app/src/main`, or text from `core` that
    screens show), also run all five screenshot tours, in one run (about 1.5 minutes):
    `HARNESS_SCREENSHOTS_DIR=<dir> ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTour*'`.
    Not a tour at a time (each run pays Robolectric's and Gradle's startup again), and not
    together with the other suites: in the same test process as the other app tests they fail
    now and then (3 of 4 runs once; alone, none of 5). Look over the pictures for what changed.
