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
- **Agreed as designed:** letter search, import and export, and the stationery update (all of
  it, as of 2026-11-10).
- **To revisit: thinking lines ("While she writes").** Letters in the post (0.4.4) wait two to
  three minutes by default, and the reply is written behind the 〒 postmark, so lines shown while
  a persona writes would seldom be seen. They wait for a second look with the design session; the
  rest of *In a letter* goes ahead.
- **The wide layouts merged first** (`claude/sharp-darwin-o3pk1g`; done, see *Progress*).
- **No preview builds** unless something needs your eyes on the phone.
- **The build session tells the other sessions** about the build branch.

Later answers (2026-11-10):

- **Japanese search** (only from the start of a phrase, see step 1): fine for v0.5; the Architect
  has it to put in the roadmap for later.
- **The stationery update in full,** as designed. No changes to it until the test build has been
  installed and explored.

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

Updated 2026-11-10 (later).

- **This session (Kanna):** parts 1 to 6, written once in `ui` and `core`, so they reach the desktop.
  When a part needs something from the platform, its declaration goes in `ui/.../platform` as part
  of the step, with a minimal computer side so the desktop still builds.
- **The Architect session (Sashigane):** `main`, the roadmap (`docs/roadmap.md`), and merging.
  `main` follows the build branch a step at a time: when a step is reported done and checked, the
  Architect runs the checks on it and moves `main` there.
- **The computer's side of each part** (desktop step 6 in `docs/plan-desktop.md`): the desktop
  installers session (`claude/desktop-installers`), once the part is in `main`, starting with import
  and export. It asks this session before adding or changing a declaration in `ui/.../platform`.
  It also fixes the Keys page's "on this phone" subtitle (`ui/.../settings/Keys.kt`) and the Linux
  keychain.
- **The desktop layout session** (`claude/sharp-darwin-o3pk1g`, archived): the wide layouts and the
  Fumibako boxes, both merged.
- **The design session, Shitae** (the design branch): settled thinking lines (her lines move to
  the postmark; see *To build next* below), and any questions found while building.

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
   data).
2. **In a letter:** the two tabs and their slips (what the index card shows: the context block
   already stored, in sections, and the turn's tokens), the model-change notice, the model row and
   its tick. Thinking lines once they're settled.
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
  - **Checked:** every suite passes (core 184, data 4, app 55, desktop 55). The tours, run before
    and after the merge, show the phone's screens laid out the same (279 shots; they differ only
    in the clock's minutes and the random tilts of tape and pictures, which change between runs),
    and the 11 new wide shots render.
- **Step 1, the engine: done.**
  - **The word index:** database version 6 adds `letter_words`, an FTS4 index (unicode61, so case
    and accents don't matter) keyed by each message's rowid and kept as letters are written,
    edited and deleted. Letters from before it are indexed when the app starts. The 5→6 step has
    its test on the phone's SQLite and the desktop's; 2→3 onward stay.
  - **What's searched:** a letter's words and its pictures' captions and descriptions; never
    thoughts, context or hidden prompts, and a reply in the post only once it's arrived. A
    search's words match the start of a word ("cat" finds "cats"). Japanese has no spaces, so a run
    of it is one word, found by its start: searching the middle of a Japanese phrase finds nothing
    yet.
  - **Your search** (for step 3's page): every word, newest first, by persona and kind (theirs,
    yours, pictures), with the sentences around the first match and the words to mark.
  - **`letter_search`:** a persona's own letters with you only; any of the words (small ones left
    out), more of them first, then newer; `from` you, me or either; up to 10 passages with who wrote
    them and when. The tool result keeps the letters found, for the P.S. and the index card. A
    memory switch, "Letter search", is on by default (its place on the Memory page comes with
    step 3).
  - **Model changes:** each reply records the model it was written with. When it differs from
    the model about to answer, the letter's context says "Your model changed: you were ... until
    now and are ... from here. Your memories and letters are the same." (once; not with the
    persona's "Tell her when her model changes" off). The thread's notices are worked out from the
    same record; only replies to your notes count, since heartbeat letters can have a model of
    their own. Letters from before v0.5 recorded no model, so the first change shows from v0.5's
    letters on.
  - **Lore and Won't fade:** a lore kind of memory (not felt, never offered back to revisit, not
    counted among what matters deeply), recall words that bring a memory to mind whenever a letter
    has them, and Won't fade, which only you set (a memory never weakens or goes dormant, but is
    recalled only when relevant). The memory book's lore label is in; the rest of its look comes
    with import (step 4).
- **Step 2, in a letter: done** (bar thinking lines, which wait for the design session).
  - **Two paper tabs** stand behind a letter's top edge at the right: an index card with a
    paperclip for "What she had in mind", and a scribbled scrap for "Her thoughts" (only when the
    model gave any). Icons only; screen readers get the names (in the persona's pronouns), and a
    long press, or a right-click on a computer, shows them. A tap slides the slip out under the
    name row; the open tab stands taller with its icon in her ink. "Show thoughts" is gone.
  - **The index card** holds what the note it answered carried: the time, her model (when it had
    just changed), your planner, memories that came up with their feelings ("always in mind" for
    core ones), stickers, what she read of the note's attachments (their descriptions), the letters
    she looked up (each opens), anything else (her outfit), and the turn's tokens ("18,000 tokens
    read · 310 written"; Anthropic's cached tokens count as read). A heartbeat letter reads its own
    hidden prompt, and a reply in the post the note before it was written, not one sent while it
    travelled. Letters from before v0.5 have their context too, since it was always stored.
  - **Her thoughts** are a scrap of squared paper, pale yamabuki, torn along the top (still in
    italics until her hand comes with the fonts).
  - **The model-change notice:** a dashed card with a 改 stamp before the first letter with the
    new model: "From here, Aria writes with Claude Opus 5.5", then "Was Claude Sonnet 5.5 ·
    changed 15:40 · she's been told" (the time when it was changed on her page; "she wasn't told"
    with the tick off).
  - **Her page, Behavior:** under Model, "Since today, 15:40 · was Claude Sonnet 5.5" once it's
    been changed there, and "Tell her when her model changes" (on).
  - **`letter_search`'s P.S.:** "Looked back at: your note of Mon 12 Oct" (or "Aria's letter
    of ..."), which opens the thread at that letter, in another thread of hers if need be. A
    persona's search leaves out the note it's answering.
  - **Tests:** the thread's rows (notices, prompts, the P.S.), the context's sections, and the
    tours: the thread with the notice and tabs, the index card out, both slips out, and her model
    card (299 shots). The wide and large-text tours no longer wait for her heart sticker, which
    the tabs and the notice push above their first screen.
- **The Fumibako boxes: merged** (2026-11-10) from `claude/sharp-darwin-o3pk1g` (748bc54, with
  its plan at 047d127): each letter in the list is the persona's box with a one-line paper strip,
  opening when tapped; a Letter box card on the persona's page; `choose_letter_box` in
  heartbeats. One conflict, in the tour's seed, kept both sides. Every suite passes (core 208,
  data 5, app 59, desktop 57) and the tours' 299 shots render. Seen once in the first run after
  the merge, and not again in two: the app suite stalled in `LettersInThePostScreenTest` (a
  `runBlocking` write on Robolectric's main thread), and one tour run lost the gallery for a
  moment (`topWith("Aria")`). Both are worth watching.
- **The gallery's mark for the open persona** (2026-11-10, 01b282c, a fast-forward): in a wide
  window, the open persona's polaroid has its corner folded down (your choice). Phone screens are
  unchanged. Suites and tours pass (299 shots).
- **Step 3, searching letters on screen: done.**
  - **Search letters:** a loupe at the right of the Letters title (the title stays where it was),
    and "Search these letters" in a thread's ⋯ menu, which starts on that persona's letters. The
    page has an index-card field ("A word, a name, a place"), whose letters (Everyone, or a
    persona with their portrait) and what kind (Everything, Their letters, Your notes, Pictures),
    and recent searches while the field is empty (the last six, kept on the device). Results come
    newest first as slips with the writer's tape, who and when ("You to Aria, Tue 10 Nov"; "Aria's
    picture, ..."), and the sentences around the first match with the words marked like a
    highlighter in the writer's ink; a picture shows a small polaroid. "Nothing in the letters
    says “...”." when nothing does.
  - **Found in the letters:** a result opens its thread at that letter, with a strip under the
    header: “tea” · 1 of 2 with Aria, older and newer, and Done. Every match in the thread is
    marked, the current one darker and ringed in her ink; a match only in a picture says what it
    held ("Found in the picture: “...”").
  - **Settings, Memory:** "Letter search: Personas look through your past letters with them."
  - **Tests:** the tours open the search, type "tea", and open a result (305 shots).
  - **A test fix:** `LettersInThePostScreenTest` stalled now and then because it blocked the main
    thread with `runBlocking` while the open thread was marking letters read on the main
    dispatcher inside `updateConversation`'s lock. Its writes now run off the main thread while
    the looper keeps going. The app never blocks its main thread this way.
- **`main` merged in** (2026-11-10, e72b9a1, from the Architect): the storage work (unused files
  cleared, videos kept at 720p, a Storage card), the libraries' notices, the best-practices skills,
  the README for both apps, the roadmap in `docs/roadmap.md`, and Maven Central's mirror in the
  build itself. No conflicts. Every suite passes (core 208, data 7, app 60, desktop 57) without a
  local init script, and the tours render 305 shots.
- **`main` merged in again** (2026-11-10, 7c0e5d5, from 642a3dc): desktop step 7 (smaller
  installers, the desktop screenshot tour, the testers' guide for computers). No conflicts. Every
  suite passes (core 208, data 7, app 60, desktop 60), and the desktop tour renders its 54 shots,
  the index card and the search loupe among them.
- **Step 4, import and export: done** (2026-11-10).
  - **Cards in** (`core/transfer`): V1, V2 and V3 from PNG (`chara` and `ccv3`; V3 wins), JSON and
    `.charx`, with the portrait and the V3 `emotion` assets (embedded in the zip, in PNG chunks or
    as data URIs; links aren't fetched). SillyTavern World Info files read as lorebooks. What comes
    in follows the ticks: greetings (the first, and the others to pick from), example dialogue, lore
    as lore memories (Won't fade on; `constant` entries always in mind; `keys` as recall words;
    switched-off entries left out and counted), mood portraits, and the card's `system_prompt` and
    `post_history_instructions` (off, readable first). The card's JSON is kept as a file beside
    her (`fromCard.cardRef`), and a Fumibako card brings back her ink, tape, pronouns and picture
    prompts from `extensions.fumibako`. Her ink is suggested from the portrait (its vivid pixels'
    hue against the inks'). Post-history instructions go last in each turn's context, under
    "Your card's instructions:", and the index card shows them.
  - **Cards out:** her portrait (a plain one in her ink when she has none) with a V3 card and a V2
    copy, her lore as the lorebook, her other memories as plain entries when ticked, mood
    portraits as `chara-ext-asset_` chunks. What she came with goes back as it came: other apps'
    extensions, the lorebook's settings, each entry's own fields, and entries the card had off.
  - **Backups:** a zip (`manifest.json` first: format 1, app version, when, what's inside; the
    records as JSON, letters a file per thread; the files under `files/`). One persona (herself,
    her stickers and models always; memories, pictures and letters ticked) or everything. Locked:
    a plain header (format, app, salt, settings), then AES-256-GCM in 1 MB segments (a random
    nonce prefix, the segment's number and a last flag), the header as each segment's associated
    data, the key from PBKDF2-HMAC-SHA256 at 600,000 rounds. A changed header or a wrong password
    fails the first segment; a file cut short fails at its end. Keys never go.
  - **Opening one:** too new (or older than format 1) is refused before the password. One
    persona comes in beside her ("Aria (6 Nov)", every id new, references inside letters,
    stickers and memories changed with them) or in her place (her page, memories and stickers
    become the backup's; your letters stay, and letters the backup has that aren't here are
    added). Everything replaces all of it in one database transaction (`Transactor`; Room's on the
    phone and the computer), keeping the keys. Models come back as the same profile, or the same
    model at the same place, or the backup's with this phone's key of its kind; image profiles'
    and embeddings' keys likewise. Files come back under their own names.
  - **Choices of mine where the design left room** (to look at with the test build):
    - "Her pictures" means every picture file her records point to (portrait, mood portraits,
      memories' photos, and the letters' when they go). Her stickers always go, as part of her.
      Songs and videos go with the memories or letters that hold them.
    - "Already here" means the same persona, or one of the same name.
    - A locked file's name ends in `-locked` (`aria-2026-11-06-locked.fumibako`).
    - Everything without settings keeps this phone's settings and models.
  - **Pages:** Import a card, Export (one persona as a backup or a card, or everything; the lock),
    Open a backup (locked, hers while she's here, everything, too old or new). Reached from
    "Import a card…" beside New persona (the gallery and Write to…), her page's ⋯ (Export Aria…),
    her memory book's ⋯ (Add lore from a lorebook…, which takes a card's lorebook too), and
    Settings › 十一 Import and export (Open a file…, Export…). Files opened or shared into the app
    on the phone (VIEW and SEND) open their pages too. A new thread holding only her first letter
    shows "First letter 1 of 4 · Another" until you write. Her page shows where she came from, the
    creator's notes, her mood portraits, and (when editing) her other first letters and the
    post-history instructions.
  - **Platform:** Save as (Android's save dialog, the computer's), sharing a file (the phone),
    copying a picked file without reading it into memory, and PNG encoding for portraits. The
    computer's sides are minimal (FileKit's save dialog; no sharing): see *Who does what*.
  - **Tests:** cards (8), backups (8, with the Everything survey), greetings (1) and the portrait
    ink (4) in core; on the desktop's SQLite, a failed restore leaving nothing behind and an
    Everything backup coming back on another install with its files and word index (2). The
    tours add the settings page, Export three ways, a card coming in, and four backups opened.
- **Her lines on the way: done** (2026-11-10; settled with the design session, design branch
  90b0856).
  - **The postmark:** while a reply is in the post, "Aria's reply is on its way · she's looking for a
    stamp", her line in her ink; her card in the letters list reads "〒 On its way · …" (her name is
    on the card). One line a letter: picked from the thread and the time of the note it answers, so
    both places show the same one and it holds until the reply arrives.
  - **Straight away:** a plain "Aria is writing", its ellipsis writing itself (all at once with
    reduced motion). The per-tool lines ("Saving a memory") are gone.
  - **Her lines** (`core/letters/WayLines`): the plain ones until there are others ("she's
    addressing the envelope"; without pronouns, no subject, so her name doesn't come twice). Her
    page's **On its way** card: take one out, change one or add your own (marked yours, kept as you
    wrote it), and **Let Aria write her own** / **Ask her for new ones**: a turn in her voice (her
    own role section, under 1,000 tokens).
  - **In a heartbeat:** `WayLinesActivity` shows her lines in a quiet moment that's happening
    anyway, at most weekly; `rewrite_postmark_lines` works only there (refused in letters, and
    within a week of her last), keeps yours, and While you were away says "rewrote 2 lines for the
    postmark". Like the letter box tool, it's always in her tool list, so the context's tools
    don't change between letters and heartbeats.
  - **Tests:** 5 in core (plain lines and pronouns, one line a letter, tidying what she writes,
    writing her own, the weekly rewrite). The tours show her page's card.
