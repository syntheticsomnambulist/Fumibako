# Working on this repository

- **Data: migrations for the database, three versions back.** Friends are testing, so their letters
  should survive updates.
  - A Room schema change bumps `HarnessDatabase`'s version and adds a step to its `MIGRATIONS`,
    with a test against the exported schema (`app/schemas`). Keep only the last three steps;
    phones on an older database start fresh, so there are never more than three old layouts.
  - Model fields need no migration: rows are JSON, new fields get defaults, and a renamed field
    keeps its stored name with `@SerialName`. Don't write backfills or legacy fallbacks for them.
  - Settings (DataStore JSON) work the same way: defaults, no fallbacks.
- **The repository is private for now.** Before it goes public, follow
  [`docs/going-public.md`](docs/going-public.md): the committed debug signing key must be replaced.
  CI fails on a public repository while `app/debug.keystore` exists.
- **App text uses American spelling** (aging, color, license, summarize), since that's how the app's
  user writes.
- **Where things are:**
  - Plans and progress: [`docs/plan-v0.4.md`](docs/plan-v0.4.md) (current),
    [`docs/plan-v0.3.5.md`](docs/plan-v0.3.5.md), [`docs/plan-v0.3.md`](docs/plan-v0.3.md), [`docs/plan-v0.2.md`](docs/plan-v0.2.md),
    [`docs/plan-v0.1.md`](docs/plan-v0.1.md).
  - Testers' guide: [`docs/testers.md`](docs/testers.md).
  - Design decisions: [`docs/design/README.md`](docs/design/README.md).
  - Architecture: [`README.md`](README.md).
- **Checks:**
  - Before pushing, run `./gradlew :core:test :app:testDebugUnitTest`.
  - For a UI change, also run the screenshot tour:
    `HARNESS_SCREENSHOTS_DIR=<dir> ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTour*'`.
