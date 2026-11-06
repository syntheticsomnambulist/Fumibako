# v0.3 plan: pictures, then stickers

Started 2026-11-06, after v0.2.2 (the Fumibako build for testing with friends).

## Progress

- **Step 1, persona prompts and outfits: done.** Self, usual outfit, current outfit and own style
  on the persona ("In pictures" card; "Wearing" on its page); a default style in Settings,
  Pictures; `change_outfit` for the persona, logged and shown as a P.S.; the persona is told what
  it's wearing while it's out of its usual clothes; pictures are built from the three parts
  (`PicturePrompt`, tags or sentences), and its style applies even when it isn't in the picture.
- **Step 2, image profiles and hosts: done.** Image profiles in the settings (a default, and a
  persona can choose another on its "In pictures" card); a page per profile that starts from a
  host and shows the options that host has, with "Draw a test picture". Hosts: NovelAI, Forge or
  Automatic1111 (`/sdapi/v1/txt2img`), ComfyUI (a default workflow or your own, with
  placeholders), OpenAI (`/v1/images/generations`) and Google (Gemini `generateContent`, Imagen
  `predict`), each with request tests against a mock server. The model ids offered for OpenAI
  and Google are a starting list; any id can be typed. Old NovelAI settings don't carry over:
  add an image profile.
- **Step 3, picture dialog: done.** In a letter: how the persona looks, what it's wearing and its
  drawing style can each be left out, and with more than one profile, which one draws.
  The ruled fields now keep only their baselines. (Under Robolectric, any dialog with a text
  field on a phone-sized screen never goes idle, a stock one included, so the screenshot tour
  shoots the picture dialog on the small default screen.)
- **Step 4, stickers: done.** Ten boards on the "Fumibako stickers" design canvas (private
  claude.ai artifact: https://claude.ai/artifact/PA4VQXGG1rWc375566n4de): stickers on letters, the
  tray's sticker page, moving and peeling, typing a `:name:`, a persona sticking one (with a P.S.),
  what the persona reads and its tools, a persona's sheet, the shared library, the unread mark and
  memory book, and settings. The decisions taken with them are in
  [design/README.md](design/README.md#stickers).
  **The engine is in:** sticker settings (size, outline width, six on a letter) and lamplight;
  each persona's switches (draws, sticks, and who may peel whose); emoji names; `place_sticker`
  and `peel_sticker`, by name at one of nine spots; the persona told what you stuck or peeled
  since its last turn (`[Sam stuck :tada: on your Fri 00:14 letter]`), and its stickers named in
  its instructions; stickers on your draft going with the note; a sticker for a heartbeat letter
  that wasn't sent going with it.
  **In the app:** stickers on letters and notes with a die-cut outline baked once (blur and
  threshold), lamplight at night; the + tray (Recent, the persona's own, Shared, Emoji), tap onto
  your draft or hold and drag onto a letter; moving, pinching and twisting your own, peeling with a
  slip that says whose a sticker is; a persona's sticker page (its sheet, drawing a new one from how
  it looks, its switches) and `draw_sticker`, with a heartbeat activity that takes one slot however
  many it draws; the shared library (from a photo, cut out by ML Kit, or drawn); Settings,
  Stickers, and Lamplight on Look; `:name:` stickers inline in letters with suggestions as you
  type; the persona's hello sticker as its unread mark; its stickers standing in for feeling
  stamps in the memory book; P.S. lines for sticking and drawing. Emoji newer than Unicode 13 are
  left out of the tray, since older phones draw them as boxes.
- **Step 5: icons, pronouns, polish.** The letter's top bar has stationery icons: a pen nib for
  the model, a rubber stamp for who you're writing as, a polaroid for drawing a picture, three ink
  dots for more. Personas have pronouns (she, he, they, it, name only, or their own), told to the
  persona and used in the app's sentences about it; until chosen, the name is used. Settings shows
  the real version; the testers' guide covers pictures and stickers. Version 0.3.0.
  **Storage is in:** the database is at version 3 through its first migration, which adds a
  `stickers` table (what's stuck where, by whom; peeled ones stay until the persona has heard) and
  a `sticker_art` table (persona sheets and the shared library). The migration is tested from a
  database built with the exported version 2 schema, opened the way the app opens it.

## What v0.3 includes

### Pictures (first; see [design/pictures.md](design/pictures.md))

1. **Three prompts per persona:** self (body and fixed traits), outfit (a default and the current
   one, with "back to default") and style (a default for everyone in Settings, Pictures, or the
   persona's own). Edited on the persona page's "In pictures" card; the page shows what the
   persona is wearing.
2. **Outfits change persona-wide,** by you or by the persona with a `change_outfit` tool. A change
   shows in "While you were away" and on the persona's page.
3. **Image profiles,** like model profiles: the host, its key or server, the model, the size and
   the host's options. One is the default; a persona can use another.
4. **Hosts:** NovelAI (as now), a local Stable Diffusion server (Forge or Automatic1111's
   `txt2img`, and ComfyUI with a default workflow you can replace), OpenAI images and Google
   images (Gemini, and Imagen).
5. **Prompts written once:** tags go to NovelAI and Stable Diffusion as written; for OpenAI and
   Google they're joined into sentences. The picture dialog in a letter can leave out the self,
   outfit or style.

### Stickers (second; see [design/README.md](design/README.md#stickers))

6. **Sources:** emoji; each persona's own pack, drawn from its self and style prompts; and a
   shared library you add to (stamps and decor too).
7. **Placement** anywhere on a letter or note, and on your draft: move, resize, rotate. A hard
   die-cut outline, baked once per sticker.
8. **Personas place and see stickers:** a tool that places one by `:name:`; stickers you place
   or peel reach the persona on its next turn as a short note. Custom emoji inline as `:name:`.
9. **Removal settings** (each side peels only its own, by default).
10. **Stickers elsewhere:** a persona's unread mark, and its own stickers as feeling marks in the
    memory book (option C of the v0.2 emotion marks).
11. **Anchors:** a sticker points at a target (a kind and an id), not only a message, so the
    v0.4 planner can take stickers too (see the design session's `calendar.md`).
12. **The + button** left of the text field arrives with stickers: in v0.3 it opens the tray on
    its sticker page. v0.3.5 adds the media buttons beside it (photos, camera, file, music, video,
    link).

### Also

13. **Stationery icons** for the letter's top bar (persona, writing as, picture, menu).
14. **Pronouns for personas** (in step 5): he, she, they, it, none (the name is used instead), or
    your own. Told to the model and used in the app's text about the persona.

## Storage

- Image profiles and the default style live in the app settings, not the database: a new table
  would mean a new database version, which wipes installed letters, and friends are testing.
- The persona's `appearanceTags` becomes `selfPrompt`, keeping its stored name, so existing
  appearance tags carry over. The old NovelAI settings don't: add an image profile again.
- Stickers get their own table, through the first migration (decided 2026-11-06): testers keep
  their letters. From now on a schema change adds a migration step, and only the last three are
  kept; older databases start fresh. The schema is exported to `app/schemas` for the tests.

## Order of work

1. **Persona prompts and outfits:** the model, the "In pictures" card, "Wearing", the default
   style in Settings, `change_outfit` and its log line, and prompts built from the three parts.
2. **Image profiles and hosts:** the profile model and page, NovelAI moved onto it, then local
   Stable Diffusion, OpenAI and Google, each with request tests.
3. **Picture dialog:** choose the profile and which parts to include.
4. **Stickers:** design pass first (mockups for placement, the sticker tray and the pack page),
   then the table, the overlay, the tools and the outline.
5. **Top bar icons, pronouns, polish, tests, an APK.**

## After v0.3

- **v0.3.5: media.** Sending photos, files, music, video and links from the + tray, shown as
  stationery (polaroids, luggage tags, cassettes, ticket stubs, cuttings, film strips), players
  for music and video, links in an in-app browser, and what each provider can take in, with
  stand-ins and short descriptions for later epochs. The design is the design session's
  `media.md`, with mockups on its "Media frames" canvas.
- **v0.4: the planner** (the design session's `calendar.md`).
- **Not scheduled:** letters between personas (pen pals).
