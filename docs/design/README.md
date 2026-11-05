# Design direction

Working notes for the app's visual design. Nothing here is implemented in the app yet; the current
Compose UI is still stock Material 3. Open `letter-studies.html` in a browser to see the interactive
mockup these notes describe (it is also published as a private claude.ai artifact).

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
  about 82% opaque, with zigzag torn ends and a slight hand-placed tilt.
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

## Typography (to revisit)

The mockup uses Cormorant Garamond (display), Literata (letter text), LINE Seed JP (UI) and
Yuji Syuku (kanji). **These are placeholders: the current choice isn't loved, and we'll circle back
to fonts later.**

## Next

- Conversation list and persona screens in this style.
- Then an implementation plan: theme tokens in Compose, letter and note components, the sticker data
  model and tools, NovelAI sticker-sheet generation, and how sticker events reach the persona's context.
