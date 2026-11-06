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

### Also

12. **Stationery icons** for the letter's top bar (persona, writing as, picture, menu).

## Storage

- Image profiles and the default style live in the app settings, not the database: a new table
  would mean a new database version, which wipes installed letters, and friends are testing.
- The persona's `appearanceTags` becomes `selfPrompt`, keeping its stored name, so existing
  appearance tags carry over. The old NovelAI settings don't: add an image profile again.
- Stickers will need their own table; that step bumps the database version, and the plan for it
  should say so to testers first.

## Order of work

1. **Persona prompts and outfits:** the model, the "In pictures" card, "Wearing", the default
   style in Settings, `change_outfit` and its log line, and prompts built from the three parts.
2. **Image profiles and hosts:** the profile model and page, NovelAI moved onto it, then local
   Stable Diffusion, OpenAI and Google, each with request tests.
3. **Picture dialog:** choose the profile and which parts to include.
4. **Stickers:** design pass first (mockups for placement, the sticker tray and the pack page),
   then the table, the overlay, the tools and the outline.
5. **Top bar icons, polish, tests, an APK.**
