# Working on this repository

- **No data compatibility before 1.0.** Change models, storage and the Room schema freely, and
  don't write migrations, backfills or legacy fallbacks. On a schema change, bump
  `HarnessDatabase`'s version: the database is recreated empty and the app starts as if freshly
  installed. Wiping test data is fine. At 1.0 this changes: export Room schemas and write
  migrations from then on.
- **The repository is private for now.** Before it goes public, follow
  [`docs/going-public.md`](docs/going-public.md): the committed debug signing key must be replaced.
  CI fails on a public repository while `app/debug.keystore` exists.
- **App text uses American spelling** (aging, color, license, summarize), since that's how the app's
  user writes.
- **Where things are:**
  - Plans and progress: [`docs/plan-v0.2.md`](docs/plan-v0.2.md) (current),
    [`docs/plan-v0.1.md`](docs/plan-v0.1.md).
  - Design decisions: [`docs/design/README.md`](docs/design/README.md).
  - Architecture: [`README.md`](README.md).
- **Checks:**
  - Before pushing, run `./gradlew :core:test :app:testDebugUnitTest`.
  - For a UI change, also run the screenshot tour:
    `HARNESS_SCREENSHOTS_DIR=<dir> ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTour*'`.
