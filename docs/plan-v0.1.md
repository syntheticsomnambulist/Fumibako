# Plan: v0.1

Goal: a first version you can install on your phone and use day to day, with the new look and the
core loop (chat, personas, memory, heartbeat, images) verified against the real APIs.

## Where things stand

- **Engine (`core/`): built and unit-tested.** Providers (Anthropic SDK, Gemini, OpenAI-compatible,
  NovelAI text), the tool loop, append-only context epochs with compaction, memory recall and tools,
  heartbeat engine, NovelAI images. 34 core tests, all passing (the 3 live ones skip without keys).
- **App (`app/`): working, stock Material 3 look.** Room storage, Keystore secrets, WorkManager
  heartbeat and notifications, all screens. Debug and release builds succeed; a Robolectric smoke
  test drives the real UI.
- **Not yet done:** the app has never run on a real device, and the engine has never called a real
  API. The design in `docs/design/` exists only as mockups.

## What v0.1 includes

**In:**

1. Everything that exists today, verified on a device and against Anthropic and NovelAI.
2. **The madoromi theme:** auto season by date, day/night by clock, with a manual override.
3. **Identity:** each persona and user persona gets an ink and a tape pattern.
4. **Letters (chat):** persona letters on paper with the washi strip, the per-letter portrait, mood
   words, memory saves as a P.S., and "written while you were away" on heartbeat letters. Your notes
   carry your tape on the right. The entry bar's border runs from the persona's ink to yours.
5. **Mood words** chosen by the persona (engine change, below).
6. **Letters list:** taped cards, last line, mood, time, a "While you were away" card, and the
   unread mark (the default 新 seal for every persona).
7. **Personas gallery:** polaroids with thick frames in the outline colour, and "Writing as" cards.
8. **Persona page:** polaroid, mood, ink and tape, about, memory-book preview, heartbeat status.
   "Edit" opens the existing editor with light restyling and the new identity fields.
9. **Bottom nav** with kanji labels on tape.

**Proposed for v0.2 (decision 1 below):** stickers. That covers emoji, the shared library and
uploads, custom `:name:` emoji inline, placement with move, resize and rotate, the die-cut outline,
persona sticker tools and removal permissions, and per-persona unread marks.

**Later (v0.3+):** NovelAI sticker sheets, mood-matched portrait faces, the memory book screen, the
persona editor redesign, the settings redesign, and the final fonts.

## Engine changes the design needs

- **Persona and user persona identity:** add `ink`, `tapePattern` and `unreadMark` (a sticker
  reference, defaulting to the 新 seal) to `Persona`, and `ink` and `tapePattern` to `UserPersona`.
  Rows are stored as JSON documents, so new fields with defaults need no database migration.
- **Mood:** the persona ends a reply with a tag such as `[mood: curious]`. The tag stays in the
  stored text, so history is still replayed unchanged and context epochs stay append-only. The app
  strips the tag for display, while streaming too, much as it already hides `<tool_call>` blocks.
  The mood is saved on the message (`ChatMessage.mood`) and as the conversation's current mood. The
  system prompt explains the tag in one line. If a reply has no tag, the previous mood is kept.
  This works with every provider and costs no extra tool round.
- **Unread:** add `Conversation.lastReadAt`. Opening a chat marks it read, and a letter newer than
  that shows the unread mark.
- **"While you were away":** add a small activity log, written by the heartbeat: "wrote you a
  letter", "saved N memories", "stayed quiet", each with a time. The list card shows entries since
  you last opened the app.
- **Memory P.S.:** pair each `memory_save` result with the letter that triggered it, so the UI can
  show it as a postscript. This is a UI-side join; storage doesn't change.
- **Day/night by clock:** the theme controller picks day or night from the local time. Decision 2
  covers how.

## Compose building blocks

Each part is built once and reused across screens:

- `MadoromiPalette.kt`: generated from `docs/design/madoromi-palette.json`, with an MPL-2.0 header.
  Holds role colours (kami, hyoshi, chitsu, nijimi, kasure, usuzumi, sumi, fuji) and the 14 inks for
  all 8 themes.
- `HarnessTheme`: provides the role colours and inks through a CompositionLocal, maps them onto
  Material 3 so stock components fit, and switches themes by season and time.
- `WashiTape`: a vertical strip or horizontal piece drawn on a Canvas, with zigzag ends as a path,
  dots, stripes or gingham drawn in the DrawScope, about 82% opacity and a slight tilt. There's also
  a paired corner-tape helper.
- `PaperCard`, `Polaroid` (thick frame in the outline colour or paper), `InkPortrait` (the ink-wash
  stand-in until there's real art) and `MoodWord`.
- `LetterSheet`, `NoteSlip`, `EntryBar`, `TapeNavBar`.
- Fonts: the placeholder faces through Google's downloadable fonts, so swapping them later is a
  one-line change. Decision 4 covers this.

## Order of work

Each step ends with its tests green and a push.

1. **Live check of the engine.** This needs a new session so the API keys you added are loaded. Run
   the live smoke tests and fix what the real APIs disagree with. Known questions:
   - whether the Anthropic SDK's auth header form works as sent;
   - whether NovelAI accepts the image request format;
   - whether NovelAI text model names are current.
2. **First device run.** I send you today's debug APK (stock look) so we can catch device-only
   problems early: Keystore, notifications permission, WorkManager, Anthropic SDK size and start-up
   time. You do a quick chat, a memory save, an image and a "Run a heartbeat now".
3. **Engine changes:** identity fields, mood tags, `lastReadAt`, the activity log, with unit tests.
4. **Theme and building blocks:** palette, theme controller, tape, cards, polaroids. Screenshot
   tours in light and dark across seasons, so you can review them without installing anything.
5. **Letters screen** in the new style, then the **Letters list**, **Personas gallery** and
   **persona page**, then the nav. Screenshot tour after each.
6. **Polish and test pass:** update the Robolectric smoke test for the new UI, run the full unit
   suite, the live smoke tests and a release build.
7. **v0.1 build to your phone** with a manual test checklist (below). Fix what you find, bump
   `versionName` to 0.1.0, and push.

## Testing

- **Automated, every step:** core unit tests, Robolectric app tests and screenshot tours, plus a
  release build to catch R8 problems.
- **Live API smoke tests** (`HARNESS_LIVE_TESTS=1`): Claude tool round trip with thinking replay,
  NovelAI text model list, one NovelAI image. These run in a session that has your keys.
- **On your phone:** I send the debug APK through the Claude app (decision 3). Checklist:
  - First launch: notification permission, the sample persona, theme matches season and time.
  - Add an Anthropic key and a model profile; Test succeeds.
  - Chat: streaming reply, mood word appears and changes, a memory save shows as a P.S.
  - Image: generate one from the chat, and let the persona send one with its tool.
  - Switch model and user persona mid-chat; edit and delete a message; regenerate.
  - Heartbeat: "Run a heartbeat now", get a notification, and the letter shows as unread with the
    away label; the "While you were away" card fills in.
  - Leave the chat mid-reply and come back; rotate the phone; kill the app and reopen it.
  - Day and night themes, and a manual season override.

## Risks

- **First device run may surface crashes** the Robolectric tests can't: Keystore quirks on some
  phones, notification permission flows, WorkManager timing. That's why the device check comes
  second.
- **The Anthropic SDK is large** (about 36 MB debug APK, 11 MB release). Start-up time on older
  phones is unmeasured.
- **Third-party request formats:** NovelAI image parameters were taken from a maintained client
  library, not official docs, so the live check matters.
- **Gemini is untested live.** No Gemini key is set up, and current model ids weren't confirmed.
- **Mood tags depend on the model following the instruction.** Weaker models may skip them; the
  fallback keeps the last mood.
- **Canvas-drawn tape and polaroids in scrolling lists** need care: cache the drawn tape, and keep
  portraits as bitmaps rather than redrawing them every frame.

## Decisions for you

1. **Stickers in v0.1 or v0.2?** My recommendation is v0.2. Stickers are a full system of their own
   (library, gestures, outline rendering, persona tools, context notes), and leaving them out gets
   v0.1 onto your phone sooner. The entry bar would leave room for the sticker button.
2. **Day/night switching:** fixed hours you can adjust (recommended: day 06:00 to 18:00), or sunrise
   and sunset for your location (needs a location permission)?
3. **How you get builds:** I send the APK file to you here in the Claude app (works today), and/or a
   GitHub Actions workflow builds an APK on every push for you to download. The workflow may need
   extra GitHub permission to push.
4. **Fonts for v0.1:** keep the placeholder faces (downloadable, easy to swap), or use the phone's
   system fonts until we choose real ones?
