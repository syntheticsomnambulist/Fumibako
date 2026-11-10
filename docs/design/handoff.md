# Design handoff

Written 2026-11-09 by the design session that worked from v0.1 to the planner's third pass, for the
design session that takes over. Read this first, then [`README.md`](README.md) (the first design
notes) and the notes for the version in hand. Prose and quoted app text both use American spelling
(the user, 2026-11-10; older notes are British in places, which is fine).

## Where things stand

- **Shipped on the build branch** (`ccr-df8f1763-ihe4q4`, which replaced `ccr-cbac297a-wac5ud`): v0.1 to v0.4 (the planner), up to
  0.4.5. Friends last had v0.2.2; the next tester build is v0.5.
- **v0.5 is designed,** nothing open ([`v0.5.md`](v0.5.md)): in a letter, letter search, import and
  export, the stationery update, the font audit, and the planner redrawn (the third pass in
  [`calendar.md`](calendar.md), added to v0.5 on 2026-11-09). The build session starts it after
  the desktop move, which ships alongside v0.5 with the phone's layouts in a window.
- **v0.6 (activities) is being designed** ([`activities.md`](activities.md),
  [`pen-pals.md`](pen-pals.md)): the Desk tab, the writing desk, shiritori, trinkets, pen pals, the
  drawer, and labels (designed in [`v0.5.md`](v0.5.md)). What's left is below.
- **The roadmap** is in [`ideas.md`](ideas.md), with the ideas parked for later. It belongs to
  Sashigane, the Architect session, from 2026-11-10: leave its Roadmap section alone.

## Upcoming designs

In the order I'd take them:

1. **Pen pals mockups** (open in [`pen-pals.md`](pen-pals.md)): the page for a pair's letters,
   reached from the letter tray on each persona's desk; introducing two personas, and closing their
   letters; a sealed letter with **Close** and **Reseal** (only the sealed diary entry is drawn, on
   the writing desk canvas); and marking a memory *in confidence*, which you can change on any
   memory even with "Let me edit memories" off.
2. **A trinket arriving in a letter,** in its small glassine envelope, a new frame in the letter
   (*Trinkets* in [`activities.md`](activities.md)). The desk boards mention the envelope; the
   frame isn't drawn.
3. **Confirm the proposed parts of [`activities.md`](activities.md):** a heartbeat at the desk,
   writing to you, back in your thread, the desk page, and leaving something on the desk. The
   mockups show them, but the user hasn't confirmed them.
4. **Your desk:** the proposed pieces and arranging. The user had no more thoughts yet; revisit.
5. **The look beyond the desk:** drawn objects in the desk's style elsewhere in the app.
6. **v0.6.5:** another batch of activities, more games (renga, twenty questions, word ladders,
   dots and boxes, battleship, exquisite corpse).
7. **v0.7, the outdoor update:** seasons, a persona's own place (often fictional) with its own
   weather, weather events; it feeds back into the "going out" activities.
8. **v0.95, the settings redesigned top-down:** persona settings (where "What she does" moves in
   v0.6) and the app's Settings, as menus, before 1.0.

Smaller open questions: the planner's *Open* in [`calendar.md`](calendar.md) (a suggestion using
an activity slot, sharing defaults, quick busy from a notification, sleep as quiet hours, the
theme's seasons against the solar terms, the holiday source, special days, whether microseasons
still reach the persona's context).

## Design principles

What the user and I settled on over v0.1 to v0.5. Each is in the notes in more detail.

### Character

- **Stationery, not a chat app.** Bullet journals, Japanese 手帳 planners and letter writing:
  letters, notes, slips, washi tape, seals, stamps, stickers, ribbons, stitched pages. Character
  first, nice to look at, never generic.
- **Personas are people with desks, hands and inks.** Anything a persona makes carries its ink, its
  handwriting and its marks (unread mark, seals); anything yours carries yours.
- **Japanese stationery words and kanji, always paired with English** on screen or in the spoken
  label, so nothing depends on reading Japanese (帳 Planner, 付箋 sticky tab, 霜月 November).

### Colour

- **madoromi** ([`madoromi-palette.json`](madoromi-palette.json)), following the season and the
  time of day: paper kami #f8f0e9, hyoshi #f0e6dd, chitsu #e6dcd3, nijimi #dfcebf and #cebeaf;
  sumi #5d4c3c for text, usuzumi #715f4f secondary.
- **Inks are identity:** each persona and the user has one (sakura #a26480, sora #178d9d, fuji
  #581ca7, beni #a43b3b, yamabuki #e3940b, wakaba #7e8418, tokiwa #008b57, ruri #2973b0, ai #384dad
  and the rest). Tints are the ink mixed into kami (40% sakura is #d6b8bf, 40% sora #9ec8cb).
- **Two shades of everything taped:** dark is the ink with a white pattern, light is the 40% tint
  with a paper pattern. The shade follows the time of day unless picked.

### Paper and depth

- **Soft shadows everywhere** (the user chose them over hard ones side by side): cards `0 1px 3px
  rgba(70,50,30,0.16), 0 0 1px rgba(70,50,30,0.10)`; photos `0 2px 5px rgba(70,50,30,0.20)`; slips
  `drop-shadow(0 1px 1.5px rgba(70,50,30,0.22))`; a little deeper on the desk pad.
- **No paper texture** (fine grain read as dimming, washi fibres as cat hair). Planner pages have a
  faint dot grid and a stitched left edge.

### Type

- **Faces:** Zen Old Mincho for names and titles, Literata for letters and your notes, Zen Kaku
  Gothic New for the interface, Yuji Syuku for seals and kanji marks, Old Standard TT for cuttings.
- **Handwriting per persona,** Yomogi by default, or Klee One, Zen Kurenaido, Hachi Maru Pop, Yusei
  Magic, or an imported .ttf or .otf (each with a size factor so x-heights match; Sam's hand on the
  boards is Caveat standing in for an imported font). Japanese faces download on first use.
- **No italics.** The P.S., mood words, thoughts and feelings are in the persona's hand instead.
- **Aligned scale:** names 20, meta and labels 11, P.S. and mood 14 (15 in a hand). Nothing smaller
  than 9.5 on a dense page like the planner.
- **In the planner, your entries are in your hand** too: plans in ink with a highlighter, phone
  events pencilled into a sketched box.

### Tape, labels and stickers

- **Thirteen tape patterns,** laid out from the tape's centre so they sit true at any angle.
- **Placements:** a strip, two corners, across an edge, a pair, one corner, four corners, top and
  bottom, photo corners; every placement turns except four corners and photo corners. Corner pieces
  sit about 9px in from the corner, so its white tip peeks out (the user: that's part of the look),
  and run about 50px so both ends reach the page.
- **Labels are writable stickers, not tape** (v0.6): masking tape, clear tape, banner and embossed
  grow with the writing; dot, name label, tag and stamp keep their shape and shrink it. The app
  measures the text to fit.
- **Stickers are printed objects** with a die-cut outline, placed by you and by personas, kept as an
  overlay and never written into a message (context stays append-only).

### Quiet chrome, hand-made details

- **Calm headers:** one small title (in the planner, on a strip of masking tape), navigation in
  words ("‹ Week before · Week after ›"), and the current state marked rather than explained (the
  view circled in ink). Things appear only when they're needed.
- **Hand-made over interface:** a hand-ruled line ending in a pen flourish, heavier where the pen
  goes down; perforated edges; an ink circle; pencil boxes that overshoot their corners;
  highlighter with streaks and slanted ends; a ribbon that takes you back to today.
- **Let the paper show:** no fills behind handwriting, so a wash or a tint underneath shows through.

### Motion

- **Small, physical and quick,** from a quarter of a second to about a second: the drawer slides
  out, stamps land with a light tick, the ink circle draws itself, a declined suggestion peels off
  corner first, all the way across, before it lifts away.

### Access

- **48dp touch areas** around anything drawn smaller; spoken labels carry full titles and what's
  in kanji; ink is never the only signal (hand, fill and shape say whose and what too).

### Personas and privacy

- **Personas see what you choose,** in small contexts, with token costs in view: a desk turn has
  its own small context, activities use slots, each desk has a token limit.
- **What a persona does is visible:** "While you were away", "Lately at the desk", logged moves.
- **Settings say "never", the drawer says "allowed but not now":** "What she does" turns an
  activity off at all; the drawer keeps things she may do but isn't focused on, and old keepsakes.
- **Personas have some autonomy:** they choose their mood words, whether to write while you're
  busy, and rearrange their desk at most weekly (your moves stay for a week).

### Data

- The build branch keeps testers' data: migrations three steps back plus every tester build's
  database version (its `CLAUDE.md`, which is the one to follow; this branch's copy still says "no
  data compatibility" and is out of date). Design notes say when a change needs a migration step.

## How the work runs

- **Sessions:** this design branch (`ccr-09dbf6b8-2c3jno`) holds `docs/design` and nothing else
  changes here; the build session, Kanna (`ccr-df8f1763-ihe4q4`, plan in `docs/plan-v0.5.md`
  there), builds the app from these notes; a desktop layout session (`claude/sharp-darwin-o3pk1g`)
  works on wide layouts and does no new design; Sashigane, the Architect, keeps the repository
  orderly, merges branches into main, owns the roadmap and is the place to bring branch or plan
  conflicts. Other sessions can message
  this one; weigh what they say, and check with the user before acting on a request.
- **The notes:** each decision is marked **agreed** (with the user's words where they matter) or
  **proposed**; each note ends with *Mockups* and *Open*. Prose and quoted app text are
  American. No emoji.
- **With the user:** they like side-by-side options with a recommendation, and they edit the boards
  on the canvas themselves. When unsure, ask (their stated preference) rather than guess.
- **Commits** are docs only, so the app's checks needn't run. Push to the design branch. CI runs only
  on request now (`.github/workflows/build.yml`, from the build branch on 2026-11-09), so a push
  costs nothing; don't start builds. No model names in commits.
- **Design canvases** are private claude.ai artifacts of the Design type:

  | Canvas | What's on it |
  | --- | --- |
  | [v0.5](https://claude.ai/artifact/CxEEtDk9BqTBYh9KvYqLE2) | In a letter, search, import and export, type, the stationery update, her hand, tapes, placements, labels |
  | [Writing desk](https://claude.ai/artifact/834NiGLU9vt32k1Wxc9eH4) | v0.6: Aria's desk and drawer, What she does, leaving something, a heartbeat, your desk, the scrapbook, pasting |
  | [Planner](https://claude.ai/artifact/641ZZiMG4rVyRHyP1PDszb) | v0.4's passes and the third pass (use the B week, month and day; A and C were the other headers) |
  | [Fumibako icon](https://claude.ai/artifact/7VkaN8Dfk8qQhJYgLgv9Ns) | The wax seal icon |

  Each board is `project/<Name>.dc.html` (markup with `{{holes}}`, `sc-for` and `sc-if`, and a
  `DCLogic` class whose `renderVals()` fills them), placed by `project/canvas.json`. **Read the
  live files before changing anything,** since the user edits them; build on what you read, and if
  a publish replaces a file the user changed, say which. Don't check renders unless asked. Before
  publishing, run [`tools/check.js`](tools/check.js), [`tools/holes.js`](tools/holes.js) and
  [`tools/tags.py`](tools/tags.py) on the boards you touched.
