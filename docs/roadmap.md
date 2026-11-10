# Roadmap

Kept by the Architect session, Sashigane, since 2026-11-10; before that it was the Roadmap section
of the design session's `docs/design/ideas.md`. Other sessions tell the Architect what changed
rather than editing this file, so it has one writer. The user's decisions are dated.

Design notes marked *(design branch)* live on `ccr-09dbf6b8-2c3jno` under `docs/design/`; read them
with `git show origin/ccr-09dbf6b8-2c3jno:docs/design/<file>`.

Updated 2026-11-10.

## Shipped

- **v0.1 to v0.3.5,** and **v0.4, the planner** (0.4.0; 0.4.1 with a new signing key and the
  GPL-3.0-or-later license; 0.4.2 to 0.4.5, including letters in the post in 0.4.4). See
  [`plan-v0.4.md`](plan-v0.4.md) and the earlier plans.
- **Testers:** friends last had **v0.2.2** (database version 2). The next tester build is **v0.5**.
  [`testers.md`](testers.md) is their guide.

## In progress

- **v0.5: in a letter, letter search, import and export, the stationery update, fonts, the planner
  redrawn, and the Fumibako boxes** (added 2026-11-09). Built by the build session, Kanna, on
  `ccr-df8f1763-ihe4q4`; the plan and progress are in [`plan-v0.5.md`](plan-v0.5.md), the design in
  `v0.5.md` and the planner's third pass in `calendar.md` *(design branch)*.
  - Done: the wide layouts (merged first), step 1 (the engine: letter search, model changes, lore
    memories, Won't fade), step 2 (in a letter, bar thinking lines), the Fumibako boxes, the
    gallery's folded corner for the open persona, step 3 (searching letters on screen), and step 4
    (import and export: character cards in and out, `.fumibako` backups, optionally locked), and
    her lines on the postmark (below), and step 5 (the stationery update, in full: thirteen
    patterns in light and dark shades, the new tape picker, eight placements that turn, the months'
    envelopes), and step 6 (fonts: the six faces and the aligned scale, a hand for each persona and
    each of yours, your own font files, Japanese on first use).
  - Next: steps 7 and 8 (the planner redrawn, then polish, tests and 0.5.0 for the phone and the
    computer).
  - Found, for step 8: the large-text phone tour fails now and then at different steps (3 of 7 runs
    on 2026-11-10), and on a computer the Export and Open a backup pages light the side rail's
    Settings tab even when reached from Personas. The desktop tour doesn't visit the import and
    export pages yet: in the test harness, Open a backup's preview card failed to show in 2 or 3
    of 5 runs (it shows every time in a real window), so that part waits.
  - The stationery update goes in whole, as designed, with no changes until friends have installed
    and explored the test build (the user, 2026-11-10, relayed by the build session).
  - Thinking lines became her lines on the postmark (the user, 2026-11-10; `v0.5.md`, *design
    branch*): while her reply is in the post, the postmark and her card in the letters list carry
    one of her own lines, which she can rewrite when she reflects, at most once a week. Built
    (2026-11-10), with her page's On its way card and a plain "Aria is writing…" straight away.
- **Desktop, shipping with v0.5** ([`plan-desktop.md`](plan-desktop.md)): Fumibako on Windows, macOS
  and Linux, with the wide layouts (the user, 2026-11-08). Steps 1 to 5 (the spike, the move, the
  shell, the desktop's services, mouse and keyboard) are done.
  - Step 6, v0.5 on the computer: the computer's side of each v0.5 part as it lands (save and open
    dialogs for import and export, Japanese fonts in the app-data folder). Taken by the desktop
    installers session, part by part after Kanna lands each (the user, 2026-11-10). Import and
    export: done (a backup or card opened with Fumibako goes to its page; `.fumibako` registered
    with each installer). Fonts: done (the Japanese faces and the other hands from Google Fonts on
    first use, into the app-data folder; Literata and Old Standard TT keep the computer's own
    Japanese, `architect-decisions.md`).
  - Step 7, done: the libraries' notices, from the build (About, and beside the installed app);
    smaller installers (the Java runtime trimmed to the modules Fumibako uses); a desktop section
    in `testers.md` and the README; and a desktop screenshot tour (2026-11-10, the desktop
    installers session). Windows and macOS packaging are still to check, from a CI build.
  - Fixed (2026-11-10, the desktop installers session): the Linux keychain works, through the
    Secret Service on the dbus-java FileKit brings; and the Keys page says "this computer" on a
    computer. KDE's Secret Service and a real desktop login are still to try.
- **Storage** ([`storage.md`](storage.md)): in place since 2026-11-10 (in `main`, reaching v0.5 by
  merge): clearing unused files (Settings, Media, Storage), and on the phone, new videos kept at
  720p and lossless songs as AAC.

## Planned

- **v0.6: activities** (`activities.md`, *design branch*): the writing desk in a 机 Desk tab with
  your desk beside it (trinket dish, a scrapbook), today's activities moved onto the desk,
  shiritori, trinkets, pen pals (`pen-pals.md`), the drawer and rearranging her desk, "What she
  does" in her settings, and labels, stickers you can write on (designed in `v0.5.md`). Being
  designed.
- **v0.6.5:** another batch of activities, with more games (candidates in `activities.md`).
- **v0.7: the outdoor update:** seasons, and what it means for a persona to live in a place of its
  own (often fictional) with its own weather; weather events for personas (`ideas.md`, "Weather and
  places"). It will feed back into the "going out" activities.
- **v0.95: the settings, redesigned top-down** (the user, 2026-11-06): persona settings (where "What
  she does" moves in v0.6) and the app's Settings, as menus, before 1.0.

## Unscheduled

- **Storage optimizations** (the user, 2026-11-10): taken up when storage needs it, between
  versions. In order of the space saved: deleting the copies made for models once they're
  described, scaling link pictures, drawn pictures as lossless WebP, tidying the database, and
  clearing unused files on its own. None of them changes what plays, shows or saves. Details in
  [`storage.md`](storage.md).
- **The margins beside a wide letter** (the user, 2026-11-10): in a wide window a letter keeps a
  reading width of about 640, and the room on each side is wanted for things. Ideas drawn in the
  wide-layout study ([`design/wide-studies.html`](design/wide-studies.html)): the day beside its
  first letter, what she kept as a slip, her index card of what she had in mind, and today's
  planner page. Which ones is still to choose.
- **Letter search in Japanese** (the user, 2026-11-10, relayed by the build session: fine as it
  is for v0.5, to fix later). The word index's tokenizer (SQLite's unicode61) splits only at
  spaces and punctuation, so a Japanese sentence is one long word, and a search, which matches
  from a word's start, misses a word in the middle of a phrase. The build session's suggested fix:
  in `core/letters/LetterWords.kt`, index CJK runs as single characters and search them as a
  phrase ("東 京"), which works on every phone's SQLite (the trigram tokenizer needs SQLite 3.34,
  newer than older Androids carry). It needs a migration step that rebuilds `letter_words`.
- **Desktop, noted for later** ([`plan-desktop.md`](plan-desktop.md), "Noted for later"): sync
  between the phone and the computer, among others.
