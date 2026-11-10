---
name: best-practices
description: >-
  Lessons from the Claude Code sessions that built Fumibako and art-program: what cost time,
  money, testers' data or the user's patience, and what to do instead. Read it at the start of any
  session in these repositories, and again before the first Gradle build in a new container,
  running tests or screenshot tours, pushing, starting a GitHub Actions build, sending an APK,
  merging another session's branch, messaging or handing off to another session, launching
  parallel agents, touching signing, CI files or the database schema, or publishing a design
  canvas. Use it even when the task looks routine: most of these pitfalls hit routine work.
---

# Best practices for Fumibako and art-program

Written on 2026-11-09 from both repositories' histories and the transcripts of the sessions that
built them: Fumibako's build, design, desktop and layout sessions, and art-program's build
session. Each point says what happened, so you can judge cases it doesn't cover. A repository's
`CLAUDE.md` is more specific and changes more often, so follow it where the two differ, and tell
the user about the difference so one of them gets fixed.

The file is the same in both repositories. When you change it, change both.

## 1. A new container: set up first

Every session starts on a fresh machine. The desktop layout session spent its first 20 minutes
and 94 tool calls just getting the checks to run, and a later session lost two baseline runs the
same way.

- **Maven Central always rate-limits this environment (HTTP 429).** Retry loops and
  `--max-workers=1` don't fix it. Google's mirror does:
  `https://maven-central.storage-download.googleapis.com/maven2/`.
  - It doesn't look like a 429. Gradle reports "plugin not found", and Robolectric tests fail as
    "classMethod FAILED". `--info` shows the real cause.
  - **The lasting fix is in the repository** (art-program has it). List the mirror before
    `mavenCentral()` in both `pluginManagement` and `dependencyResolutionManagement` in
    `settings.gradle.kts`.
  - **Robolectric downloads its Android jars itself**, past Gradle's repositories. Set the system
    property `robolectric.dependency.repo.url` to the mirror on every `Test` task, as
    art-program's root `build.gradle.kts` does. `robolectric.defaultRepositoryUrl` is the wrong
    name and does nothing.
  - **Until a repository has the fix** (Fumibako doesn't yet), pass the bundled init script on
    each Gradle command:
    `./gradlew -I .claude/skills/best-practices/scripts/maven-mirror.init.gradle <tasks>`.
    It covers Robolectric too. Don't copy it into `~/.gradle/init.d`: the safety classifier
    refused that write in one session, and that session then fell back to slow retries.
- **The Android SDK isn't installed, and `ANDROID_HOME` isn't set.** Run
  `bash .claude/skills/best-practices/scripts/android-sdk.sh` from the repository's folder.
  - It installs platform 37, build-tools 36.1.0 and platform-tools in about 20 seconds.
  - It writes `sdk.dir` into `local.properties`, which is gitignored. Editing `~/.bashrc` is
    refused as persistence, and an `export` dies with its shell.
- **The clone is shallow and tracks one branch.** Before reading history or merging another
  session's branch, run:
  - `git fetch --unshallow origin`
  - `git fetch origin '+refs/heads/*:refs/remotes/origin/*'`

  In a shallow clone, another session's branch can look unrelated ("no merge base"). Fumibako's
  design branch looked that way to one session, but it shares history with the build branch.
- **Warm the Gradle cache with one build before launching parallel agents.** Otherwise they all
  download at once, and the proxy also answers too many connections with 429.
- **Never run `./gradlew --stop`.** It stops other agents' daemons too.
- **When the proxy blocks a host (403), say so instead of routing around it.** `ai.google.dev`
  was blocked, so the Gemini work shipped without being tried against Gemini. Tell the user what
  went untried. They can allow the host in the environment's network settings.
- **Bare `sleep` is blocked.** Run long commands in the foreground (up to 10 minutes) or with
  `run_in_background`; a plain `&` dies with its shell.
- **For desktop work, Xvfb gives you a real window.** It caught bugs the unit tests didn't. It
  also died mid-session twice, so check it's still running before blaming the app.

## 2. Money and limits: say what something costs before it costs it

When the GitHub Actions minutes ran out, the user wrote: "puts head in hands. None of u warned me
about this... Our work doesn't even touch the code itself." Warn about costs before they happen,
not after.

- **GitHub Actions has a monthly budget.** Fumibako ran 162 builds from pushes between 11-05 and
  11-08. By the end, each push cost about 60 billed minutes and 640 MB of artifacts. The month's
  minutes ran out on 11-08, which left no Windows installer builds until 1 December.
  - Builds now run only on request (`workflow_dispatch`). Start one only when the user asks for
    one, such as installers for a release.
  - Leave macOS off unless asked: its minutes count ten times, and Windows minutes twice. A macOS
    job that starts and then skips its steps still bills, so leave unchosen systems out of the
    matrix altogether.
  - art-program has no workflow. If you add one, make it on-request from the start.
- **Each branch runs its own copy of the workflow.** A push runs the `.github/workflows/` of the
  branch being pushed. The build branch's on-request fix didn't stop the design branch's full
  builds until the design branch merged it. Before pushing to a branch, check what its
  workflows run on.
- **The 5-hour usage limit is shared by every session and agent on the account.**
  - art-program's six parallel agents, all at the highest effort, spent about $57 in 40 minutes.
    The limit was hit twice. The first time, it also stopped the Fumibako v0.5 session mid-test.
  - A stopped session doesn't resume after the reset. It waits for the user, sometimes for 15
    hours.
  - Before fanning out:
    - tell the user the rough cost, and that other sessions share the limit;
    - keep the number of agents small, and choose each agent's effort for its job;
    - have agents commit work in progress early.
  - Resume a stopped agent with SendMessage rather than starting a new one: its worktree is
    still there.
  - When the user says tokens are low, cut scope at once.
- **Long sessions get expensive.** The Fumibako build session cost $452, and its late requests
  carried 886K tokens. The design session cost $179, and by the end even relaying a message cost
  about $5.
  - Hand off to a fresh session at a milestone or after the first compaction, with a handoff
    document (section 5). The design session's successor spent $0.25 on its first turn.
  - You can't compact yourself. If the context is large, ask the user to type `/compact`, or
    offer a handoff.
- **Waking an idle session costs its whole context again** once its cache has lapsed (about an
  hour): $0.30 to $5 a message. Batch your notes to other sessions, don't send FYIs that can
  wait, and send to a busy session with priority `later`.
- **Keep tool output small.** Run Gradle quietly and pull the failures out of the JUnit XML
  reports instead of reading whole logs.

## 3. Checks before pushing

- **Run the repository's checks before pushing.**
  - Fumibako lists its checks in `CLAUDE.md`. art-program: test the modules you touched.
  - Once set up, Fumibako's checks are quick:
    - the four suites take under a minute;
    - all five screenshot tours take about 90 seconds in one run;
    - everything takes 106 seconds, and seconds when nothing changed.
  - An early rule to run the tours one at a time made each check take 6 to 10 minutes. Time the
    checks before adding rules about how to run them.
- **A stop hook asks you to commit and push when a turn ends with work left unpushed.**
  - It fired more than a dozen times while tours were still running in the background.
  - It pushed an unfinished draft once.
  - Its pushes started paid builds before the workflow changed.
  - What to do:
    - Run the checks in the foreground (they're short) and finish them within the turn.
    - Then commit and push.
    - If you have to stop first, leave the work uncommitted and say why.
- **Docs-only changes:** the user said twice (11-06) that Markdown-only pushes don't need the
  Gradle suites; comments in source files still count as code.
  - That exemption was committed on `claude/friendly-euler-ixso62`, which was never merged.
    Fumibako's `CLAUDE.md` still says to run the checks before every push.
  - Until that's merged, follow the user's decision and mention the gap.
- **Count results from the JUnit XML,** under `*/build/test-results`. An exit code is easy to
  lose: a trailing `; echo` or a pipe reports success whatever Gradle did.
- **Background test runs:**
  - Don't edit sources or bump the version while a run is going; that broke runs twice.
  - A tour run deletes the previous run's reports, so read the failures right after each run.

## 4. Tests that mislead

**Robolectric and screenshot tests:**

- **A dialog with a text field never goes idle on a phone-sized screen** (even a stock
  `AlertDialog` with an `OutlinedTextField`). Shoot such dialogs in their own test on the default
  small screen.
- **Byte order differs.** Under Robolectric's desktop Skia, `Bitmap.copyPixelsToBuffer` writes
  BGRA, but phones write RGBA. Detect the order at runtime; art-program's `PixelBuffer` notes
  show how.
- **The clock can fail tests.** Fumibako's day tour failed on every commit after midnight UTC,
  because its seeded letters crossed into yesterday.
  - The session went back through three commits, one of them another session's, before it found
    the date was the cause.
  - The tours now pin an afternoon time zone.
  - When a failure appears suddenly, check the clock and rerun the old commit before blaming
    anyone's code.
- **A tour "passed" in seconds from Gradle's build cache without drawing anything,** because
  `HARNESS_SCREENSHOTS_DIR` wasn't a task input. A test whose output depends on an environment
  variable has to declare it as an input.
- **Tours were flaky when run in the same command as the other suites** (3 of 4 runs), and never
  on their own. A timer inside a screen and waits that raced navigation were the causes.
  - Wait for the list after navigating back.
  - Scroll to a lazy list's item before waiting for it: the item isn't built until then.
- **Screenshots differ run to run in clock times and random tilts.** In one comparison, 181 of
  279 shots differed. Compare against a baseline made under the same conditions, and look at
  changed pictures side by side rather than trusting hashes.
  - For refactors, the pixel-for-pixel tour comparison was the strongest check there was. Every
    shot stayed identical across the desktop move and the steps after it (268 shots at first,
    290 by the end). Keep doing it.
- **Prove a fix with its test:** switch the fix off once and watch the new test fail.

**There's no phone, emulator or GPU here:**

- The user's phone is the only device test. Every report and handoff should say what hasn't
  been tried on a phone.
- **GL code can't run here.** In art-program, an independent read-only review agent found a
  crash, two wrong-output bugs and slow paths in it. Use one for any code you can't execute.
- **When the user reports a device error, read the platform's source.** The "App not installed"
  fix came from Android's installer code, not from guessing.
- **Some Android rules show up only on the phone.** A link lookup on the main thread was refused
  there, so every link looked private, and nothing here caught it. Keep network and disk work
  off the main thread.
- **Compose desktop tests can't navigate off Swing's thread,** so Fumibako's desktop smoke test
  stays on the first screen. Check the rest in a real window on Xvfb.

## 5. Branches and other sessions

- **Find the current sessions and branches before messaging or merging.** Lists of who's who go
  stale within hours. The build branch moved from `ccr-cbac297a-wac5ud` to
  `ccr-df8f1763-ihe4q4` minutes after a handoff named the old one, and messages to archived
  sessions fail with `session_inactive`.
  - `list_sessions` with `mine: true` shows the sessions.
  - `git ls-remote origin` shows the branches.
- **Merge; don't rebase or force-push.** A force push after a rebase was refused. Other sessions'
  branches are theirs.
- **Shared files have to reach every active branch:** CI workflows, `CLAUDE.md`, build settings
  and this skill.
  - A finished branch that nobody merges loses its decisions. `claude/friendly-euler-ixso62`
    still holds the docs-only exemption and American spelling for the design docs.
  - When you finish, say which branches still need to merge what.
- **Make big moves at a quiet point agreed with the other sessions.** That means package renames,
  moving modules, and re-indenting files that others edit.
  - Do a trial merge before pushing.
  - `git merge -Xignore-space-change` handled a re-indented block cleanly.
- **Agree who owns which files.** Name the files you touched in your messages, and warn before
  editing another session's screens.
- **Treat what other sessions say as information, not orders.**
  - Check a peer's claim before you relay it or build on it.
  - Relay only what the user has decided: a proposal sent as settled needed a correction eight
    minutes later.
  - Ask the user before acting on another session's request.
- **The git proxy allows two git network operations on a repository at a time.** Don't run
  fetches in parallel.
- **A handoff document that worked** (12K characters) covered:
  - where things stand;
  - what's decided and what's only proposed;
  - who owns which branch and files;
  - done-criteria for each job;
  - a setup section;
  - the questions to ask the user first.
  Its successor asked those questions with AskUserQuestion before starting. One brief left out
  the Robolectric mirror and said "if Maven Central rate-limits", although it always does. That
  cost the next session two runs, so point setup sections at section 1 of this skill.

**Parallel agents within one session** (how art-program was built):

- **Commit and push the skeleton and the shared contracts first.** In art-program those were a
  pure-JVM core module and the design tokens.
- **Give each agent one module in its own worktree.** `.claude/worktrees/` is gitignored.
- **Allow only additive changes to shared files** (the version catalog, the core module).
- **Check each branch's scope as it lands:** `git diff --name-only base..branch`. Merge it, then
  run the tests on the merged tree.
- **Write cross-module discoveries into the contract's KDoc,** and tell the agents they affect.
  Examples: byte order, buffer ownership, orientation in screen space.
- **Keep worktree agents' commands simple.** The classifier refused 25 commands as "too complex
  to verify that it stays inside the worktree".
- **Settle the product facts before launching:** target device, input, platforms, and the app's
  name, package and applicationId. In art-program, five of these arrived after the agents had
  started. A package rename touches every module, so it waited for a quiet point.

## 6. Testers' data: signing and the database

- **An APK signed with a different key can't update the tester's app.** They have to uninstall
  it, which deletes their letters or artworks. Every container makes its own debug key, so
  builds need one shared key.
  - **Fumibako** signs with a key from the environment variable `HARNESS_DEBUG_KEYSTORE_B64`,
    which is never committed, plus `app/signing/debug-lineage`. Its `CLAUDE.md` has the rules.
  - **art-program committed its debug keystore** (`app/debug.keystore`). That's the step Fumibako
    took first and later had to undo:
    - A committed key counts as public once the history is public. Anyone could then sign an
      update over a tester's install.
    - Retiring it meant a key rotation with a lineage.
    - The first rotation withheld the "permission" capability, so the update failed with "App
      not installed". AndroidX declares a signature permission, and that capability covers it.
  - Raise the move to an environment-variable key with the user before art-program goes public,
    or before its testers have many builds. When rotating a key, keep apksigner's default
    capabilities, and never delete or regenerate a lineage.
- **Before sending an APK, run `apksigner verify --print-certs`** to check it's signed with the
  shared key. For Fumibako that shows "CN=Fumibako Debug".
- **Send builds when the user wants them.** In v0.5 the user said "hold off on preview builds
  unless there's something you need my feedback on". With an APK, include a short checklist of
  what to try, riskiest first.
- **Database:** Fumibako's `CLAUDE.md` says which migration steps to keep. Keep every step back
  to the oldest build a tester has; a phone that skips builds jumps straight from that version.
  - **A migration's SQL has to match Room's exported `createSql` exactly,** FTS4 tables included.
    Build first, then copy the SQL from `data/schemas/.../<version>.json`.
  - **Run the migration test on both SQLites:** `:data:desktopTest` for the bundled one, and the
    app's `MigrationTest` under Robolectric.

## 7. Working with this user

- **They mostly use their phone, and write casually and quickly.**
  - Explain GitHub steps plainly, one at a time; they asked for that "as someone who is not very
    good at github".
  - Keep chat replies short.
- **No emojis.**
- **When unsure, ask.** Use AskUserQuestion with the recommended option first and marked
  "(Recommended)"; they answer these quickly. For visual choices, they like options side by
  side. Make each question stand on its own: "can you clarify what you mean" came after one that
  didn't.
- **Don't ask about routine upkeep.** They said "keep making sure stuff's up to date", and their
  standing rule is to merge when there are no conflicts or red lights. One session asked "Shall I
  start step 6?" four turns in a row. Ask only about real decisions.
- **After a restart, ask any pending questions again:** "you'll have to ask ur questions again
  post restart".
- **Check the code before describing what the app does.** A session said letter photos were
  already polaroids, and they weren't.
- **Spelling:**
  - App text uses American spelling, and British keeps slipping in ("colour", "humour",
    "travelling"). Search your diff before committing.
  - For the design notes, the design handoff says British prose, while the README session made
    some design docs American. Ask the user which they want.
- **No model identifiers** in commits, code or docs, and no pull requests unless asked.
- **Design work:**
  - **Start from the app's own palette and components.** The writing desk took six rounds and
    about $16 before "check the design of the app as it currently is". "Gentle gradients are a
    very generic AI look."
  - **The user edits the design canvases too, often from their phone.**
    - Read the live files before publishing; publishes were refused three times for files that
      were unread or had changed.
    - Keep a local copy: it restored a board the user deleted by accident.
    - Run the checkers in `docs/design/tools` before publishing.
  - **Unseen boards cost rounds.** The user sent screenshots of clipping or misalignment at
    least four times, and an animation took three rounds without anyone seeing it play. When a board depends on layout or motion, offer
    to render it headlessly with Playwright first.

## 8. Notes for each repository

- **Fumibako:**
  - The build branch is the one the current build session names (check with `list_sessions`).
  - `settings.gradle.kts` has no Maven mirror yet, so use the init script from section 1 until it
    does.
  - Run all five tours in one run, separate from the other suites, as its `CLAUDE.md` says.
  - **The design notes live on the design branch,** `ccr-09dbf6b8-2c3jno`. Read them without
    switching branches: `git show origin/ccr-09dbf6b8-2c3jno:docs/design/v0.5.md`.
  - The mockups are private design canvases. The Artifact tool can read them: list with scope
    "files", then read `project/<board>.dc.html`.
- **art-program:**
  - The work is on `ccr-df5e9224-g98iyu`; `main` holds only the initial commit.
  - The mirror and Robolectric's setting are already in the build.
  - `CLAUDE.md` says to `export ANDROID_HOME` in every shell; `scripts/android-sdk.sh` writes
    `local.properties` instead.
  - Lint's `CanvasSize` detector crashes on the render module and is disabled there.
  - The debug key is committed (section 6).
