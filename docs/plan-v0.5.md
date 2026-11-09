# v0.5 plan: in a letter, search, import and export, stationery, fonts, the planner redrawn

Started 2026-11-09 by the build session, Kanna (鉋, the plane that smooths a box's wood), on
branch `ccr-df8f1763-ihe4q4`, now the build branch. v0.5 is the next tester build (friends last
had v0.2.2), and the desktop version ships with it (`docs/plan-desktop.md`).

The design is the design session's `docs/design/v0.5.md` (on its branch, `ccr-09dbf6b8-2c3jno`),
with its boards on the v0.5 design canvas (private claude.ai artifact, which only the repository's
owner can open: https://claude.ai/artifact/CxEEtDk9BqTBYh9KvYqLE2). The planner's redraw is
*Third pass* in that branch's `docs/design/calendar.md`, with boards under "Third pass" on the
planner canvas (https://claude.ai/artifact/641ZZiMG4rVyRHyP1PDszb). The Fumibako boxes are in
`docs/plan-desktop.md` and [`docs/design/fumibako-studies.html`](design/fumibako-studies.html).

## Your answers (2026-11-09)

- **Scope:** the design's six parts, and the updated Fumibako boxes (the letters list as little
  boxes, on the phone too), which the desktop layout session is drawing.
- **Agreed as designed:** letter search, import and export, and most of the stationery update.
- **To revisit: thinking lines ("While she writes").** Letters in the post (0.4.4) wait two to
  three minutes by default, and the reply is written behind the 〒 postmark, so lines shown while
  a persona writes would seldom be seen. They wait for a second look with the design session; the
  rest of *In a letter* goes ahead.
- **The wide layouts merged first** (`claude/sharp-darwin-o3pk1g`; done, see *Progress*).
- **No preview builds** unless something needs your eyes on the phone.
- **The build session tells the other sessions** about the build branch.

## What v0.5 includes

From `v0.5.md`, in short:

1. **In a letter:** two paper tabs at a letter's top right open its slips: an index card of what
   the persona had in mind (the turn's context block, in sections, with the turn's tokens) and a
   squared scrap of its thoughts. A change-of-address notice in the thread when a persona's model
   changes, a line in its next context ("Your model changed..."), a tick to not tell it, and "Since
   today, 15:40 · was ..." on its model row. Thinking lines: see above.
2. **Searching letters:** a word index (FTS4, prefix matches) over letters, your notes and what's
   in pictures. A Search letters page (a loupe by the Letters title; ⋯ › Search these letters in a
   thread), results as slips with the word highlighted in the writer's ink, and the thread opened
   at a match with a strip to step through the others. For personas, `letter_search` beside
   `memory_search`, a P.S. naming the top result, the index card's "Letters she looked up", and a
   switch beside Memory tools (on).
3. **Import and export:** SillyTavern cards in (V2 and V3; PNG, JSON, `.charx`) with ticks for what
   comes in, and out (a V3 PNG with a V2 copy). Lorebooks become lore memories. "Won't fade" on any
   memory, set only by you. `.fumibako` backups of one persona (the default: with its memories and
   pictures) or everything (which replaces what's on the phone), optionally locked with a password
   (AES-256-GCM, PBKDF2-HMAC-SHA256). Opening a backup: keep both or replace; older backups
   brought up to date by the same rule as the database's migrations.
4. **The stationery update:** thirteen tape patterns, each in a light and a dark shade (with the
   time of day, or picked for good); patterns centered on the tape; longer corner pieces; three new
   placements (four corners, top and bottom, photo corners), and every placement turns; the memory
   book's earlier months in envelopes with their traditional names.
5. **Fonts:** Zen Old Mincho (names), Literata (letters, yours too), Zen Kaku Gothic New
   (interface), Yuji Syuku (seals), Old Standard TT (cuttings), and a hand for each persona and each
   of your personas (Yomogi by default; Klee One, Zen Kurenaido, Hachi Maru Pop, Yusei Magic; or a
   font file you add). No italics: the P.S., mood words, thoughts and feelings are in the persona's
   hand. Latin subsets bundled; Japanese downloaded on first use. An aligned scale of about 12
   sizes.
6. **The planner, redrawn:** header B on the week, month and day (the month on masking tape,
   navigation in words under it, a Week and Month switch circled in ink; the edge tabs and kanji
   month tabs go, the ribbon is the way back to today, stickers open from a sheet's corner); a
   hand-ruled line under the days; the all-day bar with a perforated edge; no season band on the
   week or day; your entries in your hand (plans in highlighter, phone events in sketched pencil
   boxes, no fills); busy hours as a wash ending at a bracket; a declined suggestion peels off;
   nothing smaller than 9.5.
7. **The Fumibako boxes:** the letters list as shut boxes, each persona's own (finish, lid, tie),
   opening when tapped. Drawn and built by the desktop layout session; they come in by merge.

## Who does what

- **This session:** parts 1 to 6, written once in `ui` and `core`, so they reach the desktop.
- **The desktop session** (`claude/epic-davinci-rpggbc`, with step 7 on `claude/desktop-step7`):
  the computer's side of each part as it lands (save and open dialogs, Japanese fonts downloaded
  to the app-data folder).
- **The desktop layout session** (`claude/sharp-darwin-o3pk1g`): the wide layouts and the boxes.
- **The design session** (from `docs/design/handoff.md` on the design branch): thinking lines, and
  any questions found while building.

## Data and migrations

- **Letter search adds a table:** the word index, database version 6, with its migration step and
  a test on both SQLites (the phone's and the desktop's bundled one). The index is filled from the
  letters already there, so old letters can be found.
- **Everything else is model fields** (lore memories, Won't fade, a persona's hand and tape shade,
  the model-change record): defaults, no migrations, as CLAUDE.md says.
- **Kept steps:** 2→3 onward, and the test that opens a version 2 database, until friends have
  v0.5. After that, steps older than v0.5's database version can go (keeping the last three).
- **Backups** carry a format version and follow the same rule.

## Order of work

0. **Merge the wide layouts.** Done.
1. **Engine:** the letter index and `letter_search` (with its P.S. and switch), the model-change
   record and its context line, lore memories and Won't fade (recall, fading, the memory book's
   data), and what the index card shows (the context block already stored, and the turn's tokens).
2. **In a letter:** the two tabs and their slips, the model-change notice, the model row and its
   tick. Thinking lines once they're settled.
3. **Searching letters, on screen:** the Search letters page, the thread's match strip, ⋯ › Search
   these letters.
4. **Import and export:** cards, lorebooks, the `.fumibako` zip and locking in `core` first, with
   tests; then the pages (Import a card, Export, Open a backup), the save dialog and share sheet,
   and opening a file shared into the app.
5. **The stationery update:** patterns, shades and the Shade row, centered patterns, placements and
   turns, the month envelopes.
6. **Fonts:** the faces, the scale, each persona's hand (Her hand), your own font files, Japanese on
   first use and "Download Japanese fonts now".
7. **The planner, redrawn,** on the new tapes and hands.
8. **Polish, tests and 0.5.0:** the boxes merged in, the testers' guide and README, the screenshot
   tours, and 0.5.0 for the phone and the computer together.

## Progress

- **Step 0, the wide layouts: merged** (2026-11-09) from `claude/sharp-darwin-o3pk1g`, cleanly,
  with the desktop plan's latest from `claude/epic-davinci-rpggbc`.
