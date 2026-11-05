# Design direction

Working notes for the app's visual design. Open `letter-studies.html` (chat) and `screen-studies.html`
(letters list and persona screens) in a browser to see the mockups these notes describe; both are
also published as private claude.ai artifacts.

**Built in v0.1:** the madoromi theme (with fixed-hours, sunrise/sunset and static modes), inks and
tape patterns, letters and notes, mood words, the letters list with the unread seal and "While you
were away", the polaroid gallery, the persona page and the kanji navigation. **Not yet:** stickers
(v0.2), per-persona unread marks, the ink signature, and the memory book, editor and settings
redesigns. The code lives in `app/.../ui/paper` (tape, paper, polaroids) and `ui/theme`.

**Goal:** character-focused, nice to look at, not generic. Bullet-journal stationery more than chat app.

## Colour: madoromi

- The palette is [madoromi](https://madoromi.moonwit.ch/) by samhain: eight themes made from Pilot
  Iroshizuku fountain-pen inks, one per season (haru, natsu, aki, fuyu), by day (hiru) and by night (yoru).
  Values are in `madoromi-palette.json` (MPL-2.0; keep its notice, and give any Kotlin palette file
  derived from it the same licence header).
- UI roles follow madoromi's style guide: `kami` paper background, `hyoshi` bars, `chitsu` deepest
  surface, `nijimi` surfaces and borders, `kasure` subtle text, `sumi` body text, `usuzumi` secondary
  text, `fuji` accent.
- **Auto theme:** follow the real season and switch day/night by the clock. The user can pin a theme.
- **Inks as identity:** each persona has a signature ink (one of the 14 accent inks), and so does the
  user. Inks colour names, actions in text, tape and accents.
- Stickers are "printed objects": they use the season's day inks even at night.

## Letters (chat)

- **Persona messages are letters:** a stationery sheet (`kami` on a `hyoshi` desk), a book face for the
  text, and a header with a small portrait, the persona's name, mood word and date.
- **Rule = washi tape strip** (chosen over tabs, corner tape and a plain ink line): a strip of tape in
  the persona's ink down the left edge, half on the paper, running past the top and bottom corners,
  fully opaque, with zigzag torn ends and a slight hand-placed tilt.
- **Tape pattern** is part of identity alongside the ink: dots, stripes, gingham or plain.
- **User messages are notes:** smaller slips with the user's own tape strip on the right.
- **Per-message portrait** in each letter header. Later it can show a face that matches the mood.
- **Mood words** beside the persona's name and in the top bar. The persona chooses its mood itself.
  Later this can drive a changing portrait/icon system.
- **Memories the persona saves** appear as a P.S. on the letter.
- **Ink signature** (a brush stroke) is optional decor, off by default, for letters written while
  the user was away. It is also available as a sticker.
- **Text entry bar:** the field's border is a gradient from the persona's ink (left) to the user's
  ink (right); the send button is in the user's ink.
- Rejected: a gradient stripe across the top of the screen, and a large portrait banner header.

## Stickers

- **Sources:** emoji to start; each persona's own pack (a sticker sheet NovelAI draws from the
  persona's appearance tags); and a shared library the user uploads to, which also holds stamps
  (花丸, seals) and ink/tape decor. Shared stickers work with every persona.
- **Custom emoji, Discord-style:** every sticker has a `:name:`. Custom emoji can also be written
  inline in message text, by the user and by personas.
- **Placement:** anywhere on a message (over the text too, not only the edge) and on the user's draft
  before sending. Move, resize and rotate (handle in the mockup; pinch and twist in the app).
- **Outline:** a hard die-cut outline on every sticker, emoji included. White by default, recolourable
  in settings. It must be rounded (blur-and-threshold, not a square dilate) so points aren't clipped.
  In the app, bake the outline once when a sticker is added or recoloured rather than per frame.
- **Personas place stickers whenever they like**, on the user's notes or their own letters, through a
  tool, choosing by `:name:` (models read names, not pictures).
- **Personas see stickers:** stickers the user places or peels reach the persona on its next turn as
  short notes, e.g. `[Sam stuck :heart: on your Wed 08:14 letter]`. Stickers never set the persona's
  mood; it reacts however it chooses.
- **Removal:** by default each side can only remove its own stickers. Two settings, both off by
  default: "I can peel off <persona>'s stickers" and "<persona> can peel off mine" (the second decides
  which removal tool the persona is offered).
- **Storage:** stickers are an overlay on a message, never part of its text. Placing one on an old
  message doesn't rewrite history, so context epochs stay append-only (see the main README).
  Sketch: `Sticker(id, messageId, ref, placedBy, x, y, rotationDeg, scale, createdAt)`, with `x`/`y`
  as fractions of the message box.

## Letters list and persona screens (draft, see `screen-studies.html`)

- **Letters list:** each conversation is a paper card held by its persona's washi strip, with a
  portrait, mood word, time, and the last line in the book face. An unread letter wears the
  persona's own **unread mark**, chosen per persona: a seal (Aria's 新), an emoji (Juno's 💌) or any
  sticker from its pack, with the usual die-cut outline. A "While you were away" card at the top
  summarises heartbeat activity (kept). A "New letter" button in the user's ink.
- **Personas gallery (loved):** personas as polaroids taped into the page (their ink and tape
  pattern, slight tilts), with mood and memory count; a dashed "New persona" polaroid. Polaroids
  have **thick frames in the sticker outline colour** by default (setting: match stickers, or paper). Below, the user's
  own personas ("Writing as"), each with their own ink and tape.
- **A persona's page:** a journal page with the persona's polaroid, mood, ink, tape and model,
  then taped entries: about, memory book (core memories taped down, dated episodes, reflections
  in italic), sticker pack and unread mark, heartbeat status. A good start; to be revisited.
- **Bottom navigation:** kanji labels 文 Letters, 人 Personas, 設 Settings; the active tab's label is
  written on a strip of plain tape in the user's ink.

### Controls: stationery, not pills

- Square corners everywhere (2-4dp), including dialogs, fields and menus. Portraits stay round.
- A choice among a few options is a row of boxed cells, like a form; the chosen cell is shaded
  and underlined in ink.
- Yes/no is a tick box with an ink tick, not a switch.
- Buttons are a block of ink (primary) or a slip of paper with a hairline edge (quiet).
- Ink swatches are square chips.

### Tape rules

- Tape is fully opaque. Text written on tape uses whichever of the theme's darkest or lightest
  colours reads on that ink.

- Corner tape always comes in pairs on opposing corners (upper left and lower right).
- Vertical strips on stacked cards stop well short of each other (about 5px overhang with a 16px gap).

## Remaining screens (agreed 2026-11-05; mockups on the "Remaining screens" design canvas)

- **Fields are index cards:** a box, a thin header rule in ink with the field's label, and ruled
  lines the text sits on (the ruling is aligned to the text baseline).
- **Numbers are steppers** (− value +), and tapping the value lets you type it.
- **Memory book:** a diary by date, with filter tabs by kind (always in mind, about you, what
  happened, the persona's thoughts) and filters (feeling, who it's about). Presented as a scrapbook:
  date stamps, polaroids of pictures from letters, torn strips quoting lines from letters, label
  slips for facts, older months tucked into envelopes, tape placed differently on each piece.
- **Memories carry an emotion word.** By default it is the persona's mood when the memory was kept;
  the persona can revise it while reflecting, and the original is kept alongside. How importance
  works is open (research under way).
- **Persona editor:** edit directly on the persona page, one entry at a time, with the tape placed
  differently on each card (strip left, corner pair, top piece, strip right).
- **Settings:** a notebook contents page (一 to 八, each line showing its current value), opening a
  page per section. Heartbeat may become model-specific (to be confirmed).
- **Your personas:** a management screen like the personas gallery (polaroids, a page per persona
  with "About you", what each persona remembers about them, and their letters), reached from the
  Personas screen.

## Typography (to revisit)

The mockup uses Cormorant Garamond (display), Literata (letter text), LINE Seed JP (UI) and
Yuji Syuku (kanji). **These are placeholders: the current choice isn't loved, and we'll circle back
to fonts later.**

## Next

- v0.1 scope and order of work: [`../plan-v0.1.md`](../plan-v0.1.md).
- Still open: importance in memories (research), heartbeat per model, fonts.
