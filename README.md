# Fumibako (Android)

Fumibako (文箱, a lacquered box for keeping letters) is an Android app for living with AI personas: chat with them, swap models and personas freely, and
let each persona keep its own long-term memory and a "heartbeat" that lets it reflect, tend its
memories, and reach out to you on its own. Image generation uses NovelAI.

Kotlin + Jetpack Compose. All agent logic lives in a plain Kotlin/JVM module (`core`) so it can be
tested without a device and reused elsewhere.

## Features

Paths under `ui/` are in `ui/src/commonMain/kotlin/dev/aiharness/app/`, under `app/` in
`app/src/main/kotlin/dev/aiharness/app/`.

| Feature | Where |
| --- | --- |
| Chat with streaming replies, thought summaries, regenerate, edit/delete | `ui/chat`, `core/agent/AgentRunner.kt` |
| Letters in the post: a reply is written at once and kept back until it arrives (2 to 3 minutes by default, up to 2 or 3 days, or straight away; per persona too), with a postmark meanwhile, an optional watch with its arrival, and a notification when it comes | `core/model/Settings.kt` (`LetterTravel`), `core/agent/AgentRunner.kt`, `ui/chat/InThePost.kt`, `app/heartbeat/LetterArrival.kt` |
| Swappable models: Anthropic (Claude), Google Gemini, any OpenAI-compatible server, NovelAI text | `core/llm/*` |
| Swappable personas, each with its own memories, greeting, pronouns, looks, outfit, drawing style and default model | `ui/personas` |
| Swappable user personas (who *you* are), switchable mid-chat | `ui/settings/KeysAndUsers.kt` |
| Long-term memory: agent-written via tools, hybrid keyword + optional embedding recall, core memories, episode recaps, keepsakes (things kept from letters) in the memory book | `core/memory`, `core/tools/BuiltinTools.kt`, `ui/memory` |
| Heartbeat: **Proactive** (background reflection + optional messages), **Time-aware**, or **Off** | `core/agent/HeartbeatEngine.kt`, `app/heartbeat` |
| Pictures from NovelAI, Stable Diffusion (Forge, Automatic1111, ComfyUI), OpenAI or Google, through image profiles (persona can call it as a tool, or you can request one) | `core/image` |
| Photos, files, songs, playlists, videos and links in letters, framed as stationery, with players and an in-app browser; each model receives what it can take in; personas open links you share (Claude's web fetch, or the app's fetcher) and share links and songs back | `core/media`, `core/tools/LinkTools.kt`, `app/media`, `ui/media` |
| Stickers: emoji, each persona's own sheet (drawn from its looks), and a shared library (from photos, cut out on the phone); placed by you or by personas through tools, inline `:name:` in letters | `core/stickers`, `core/tools/StickerTools.kt`, `ui/stickers` |
| The 帳 planner: a vertical week, a month, a day; your typical week, stamps (done, moved, cancelled), stickers, a movable bookmark; solar terms and microseasons worked out on the phone; public holidays (Nager.Date, or a phone holiday calendar); the phone's calendars read live at a detail level you choose; personas see what you share and write in it themselves (notes, suggestions, reminders that wake them, time away, season haiku); notifications held while you're busy | `core/planner`, `core/seasons`, `core/agent/PlannerActivities.kt`, `ui/planner`, `app/heartbeat/BusyTimes.kt` |
| API keys encrypted with the Android Keystore | `app/data/PlatformStores.kt` |

## Getting started

1. Open the project in Android Studio (or use `./gradlew :app:assembleDebug`). Requires JDK 17+
   and Android SDK platform 37.
2. Install on a device running Android 8.0 (API 26) or later.
3. In the app: **Settings → API keys** → add your key(s). For NovelAI use a *persistent API token*.
4. **Settings → Model profiles → +** → pick a preset (Claude Opus 5.5, Gemini, OpenAI, OpenRouter,
   local Ollama/LM Studio, NovelAI GLM…), select the key, tap **Fetch** to pick a model id if the
   preset left it blank, then **Test**.
5. **Settings → Image generation** → pick your NovelAI key to enable images.
6. Write to a persona from **Letters → New letter**. A sample persona, *Aria*, is created on first launch.
7. **Settings → Look** sets how the theme switches between day and night (set hours, sunrise and
   sunset, or a fixed theme), the polaroid frame and outline colours, and mood words.

### Builds

GitHub Actions builds on request, not on every push (**Actions → Build → Run workflow**): the tests
and the Windows installer by default, and the macOS and Linux installers and the phone's debug APK
when ticked. The files are under the run's **Artifacts** for a week. Pushes run nothing, since each
session runs the tests before it pushes and Actions time is limited while the repository is private.

Debug builds share one signing key, so a new build installs over the old one. The key
is never committed: CI and cloud sessions get it from a secret, and without it a build is signed
with the machine's own debug key. Builds also carry `app/signing/debug-lineage`, which hands over
from the key used up to 0.4.0, so they install over those builds too. See
[`docs/going-public.md`](docs/going-public.md).

The desktop installers (`.msi`, `.dmg`, `.deb`) are built on Windows, macOS and Linux runners. Both
apps take their version from `gradle.properties`.

**Stored data:** database changes come with migrations, so letters survive updates. A phone whose
database is older than the migrations kept starts fresh, re-creating the sample persona. Pictures
and attachments are files in the `blobs` folder (`data/.../Stores.kt`). Deleting a letter leaves its
files; Settings, Media, Storage finds the ones nothing refers to and clears them on request
(`data/.../BlobSweep.kt`: it searches every table's text, the drafts and the settings for their
refs). On the phone, videos are kept as a 720p copy and lossless songs as AAC instead of the
originals (`app/media/Shrinker.kt`).

## Architecture

```
core/                         Plain Kotlin/JVM library: no Android dependencies
  Harness.kt                  Composition root: wires everything below
  model/                      Personas, user personas, model profiles, conversations, messages, memory, settings
  storage/                    Repository ports (+ in-memory implementations for tests)
  llm/                        Provider interface, SSE/HTTP helpers, emulated tool calling, registry
    anthropic/                Official Anthropic Java SDK (beta Messages surface)
    gemini/                   Generative Language API, streaming, thought-signature replay
    openai/                   Chat Completions (OpenAI, OpenRouter, Ollama, LM Studio, NovelAI…)
  agent/                      PromptAssembler, TurnContextBuilder, ContextBuilder, AgentRunner, HeartbeatEngine
  memory/                     MemoryService (BM25 + embeddings, dedupe), embedders
  tools/                      AgentTool interface, registry, memory, image, outfit, sticker and link tools
  image/                      Image profiles and hosts (NovelAI, Stable Diffusion, ComfyUI, OpenAI, Google), picture prompts
  stickers/                   StickerService (names, placing, peeling, notes to the persona), StickerMaker, emoji names
  media/                      What each model takes in, the parts it receives, descriptions, link previews
  planner/                    Planner (entries, busy, what a persona is told), its tools, holidays, phone calendars
  seasons/                    The 24 solar terms and 72 microseasons from the sun's position
data/                         Multiplatform (Android + desktop): Room (JSON-document rows, migration steps kept as
                              CLAUDE.md says, schemas in data/schemas), the DataStore settings store, file blobs
ui/                           Multiplatform (Compose Multiplatform): everything both apps show
  AppContainer.kt             The shared container, handed each platform's services (platform/AppPlatform)
  engine/GenerationManager    Runs turns in an app-wide scope so replies survive leaving the screen
  ui/                         Compose screens
  platform/                   What screens need from the platform: expect declarations, with the phone's
                              code in ui/src/androidMain and the desktop's in ui/src/desktopMain
app/                          Android shell
  AndroidPlatform.kt          The phone's services for the shared container
  data/                       Keystore secrets, DataStore's settings file, phone calendars
  heartbeat/                  WorkManager worker, scheduler, notifications, reminders' wakeups, busy times
  media/                      Importing photos, songs and videos; playback (media3); the video shrinker
desktop/                      Desktop shell (docs/plan-desktop.md): the window, the desktop's services
```

The screens are written once, in `ui/src/commonMain`, which can use the JDK but not Android: what a
screen needs from the platform goes through `ui/.../platform` (see [`docs/plan-desktop.md`](docs/plan-desktop.md)).

### Context epochs (why history is append-only)

Model providers increasingly bind their hidden reasoning to the exact conversation that produced it:
Claude's thinking-block signatures and Gemini 3's thought signatures must be sent back unchanged, and
Claude rejects (or drops) them if earlier turns or the system prompt were edited. So
`ContextBuilder` organizes each conversation into **epochs**:

- Within an epoch the **system prompt and tool set are frozen** and history is **append-only**.
  Assistant turns are stored with their provider-native `replay` payload and sent back verbatim.
- Per-turn context (current time, elapsed time, recalled memories) is written into a `<context>`
  block **stored on the user message when it is sent**, so later requests replay identical bytes.
- A new epoch starts when the persona, user persona, base instructions, heartbeat mode or tool set
  change, when you edit/delete a message mid-history, or when history outgrows the profile's
  context budget. Older turns are then sent as plain text and tool calls without replay data, which
  is the documented recovery.
- **Compaction** summarizes the oldest turns (cut at a user-turn boundary, never mid tool round)
  into a running summary in the new system prompt, and records the chunk as an *episode* memory.
- **Pictures and files** you send go whole where the model takes them (`core/media/MediaSupport.kt`:
  Claude reads pictures and PDFs, Gemini also songs and videos, OpenAI-compatible servers pictures
  with "Sees pictures"; text files go as text everywhere), else as pictures standing in (a video's
  three frames, a PDF's first page) or a stand-in line. A video may take half the context budget
  (other things a third); a longer one goes as its first part (a long video's smaller copy is its
  first four minutes, and Gemini cuts it further with `videoMetadata`). Gemini also watches YouTube
  links (cuttings and tickets) by their address, up to that share, and if it refuses one (a private
  video) the request goes again without it. The line under a frame says why something isn't taken
  in whole. After a turn a short description is written
  (`MediaDescriber`); epochs that begin after it carry the description instead of the file. One the
  persona's model can't take in at all is described by the utility model before it first reads it.

This also keeps prompt caches warm: Anthropic requests mark the system prompt and conversation
prefix with `cache_control`.

### Memory

Each persona owns its memories (`MemoryEntry`): facts, reflections, episodes, and **core** memories
that always sit in the system prompt. Memories about you can be scoped to one user persona.

- Personas manage memory themselves through tools: `memory_save`, `memory_search`,
  `memory_update`, `memory_forget`. Near-duplicates are merged.
- Every user turn recalls the top memories by `0.65·relevance + 0.20·importance + 0.15·recency`.
  Relevance is BM25 keyword overlap, mixed with embedding cosine similarity when you configure an
  embedding endpoint (OpenAI-compatible or Gemini) under Settings → Memory.
- Models without tool calling (e.g. NovelAI GLM) get prompt-based tools (`<tool_call>` tags); if
  tools are off entirely, memory still forms through compaction and heartbeat episode recaps.
- Everything is browsable and editable in **Personas → (brain icon)**.

### Heartbeat

Pick a mode in **Settings → Heartbeat**:

- **Proactive** (default): WorkManager wakes the app at the shortest interval any persona needs
  (15-minute minimum); each persona keeps its own rhythm (the usual one, or its own: interval,
  quiet hours, limits, and optionally its own model for heartbeats). A heartbeat runs a list of
  **activities** (`core/agent/HeartbeatActivities.kt`): writing up recent conversation, inviting a
  reflection when strong feelings pile up, offering recently recalled memories back to revisit how
  they feel, keeping "matters deeply" rare, and sorting unknown mood words. If anything is due, or
  the persona may write to you right now (not quiet hours, not too soon, under the daily limit, not
  waiting for a reply), it gets one private `<heartbeat>` moment with its memory tools, built from
  those invitations. It may then write you a letter (posted and notified) or reply `[SILENT]`;
  quiet moments are discarded from the history, but what it did with its memories stays.
  Meaningful actions in the persona's own voice (writing to you, for now) share a few slots per
  heartbeat, 3 by default (**Activities per heartbeat**); the persona is told how many it has and
  chooses, and the engine counts them. Reflecting and tending memories are free. With the planner
  shared, a persona also looks at your week ahead (free) and may write a haiku on a new
  season's first day (one slot); a reminder it set itself wakes it at its time (a one-off job),
  and while it's away, in its own words, heartbeats leave it be. Every
  wakeup is logged for "While you were away" and the persona's page. New activities are how the
  persona's "home" will grow.
- **Time-aware**: no background work; each turn includes the current time and elapsed time.
- **Off**: no background work and no time information.

**Run a heartbeat now** in Settings triggers one immediately for every persona, whatever their pace.

### Providers

- **Anthropic**: uses the official `com.anthropic:anthropic-java` SDK (beta Messages surface).
  Adaptive thinking with summarized thoughts, `effort` instead of temperature (current Claude
  models reject sampling parameters), prompt caching, and **server-side refusal fallback
  (`fallbacks: "default"`) enabled by default** for Claude Opus 5.5 / Opus 5 / Sonnet 5.5 /
  Fable 5.1 on the first-party API. You can turn it off per profile. The SDK is large; release
  builds shrink it with R8 (unsigned release APK is about 11 MB).
- **Gemini**: streams via `streamGenerateContent?alt=sse`; model turns (including
  `thoughtSignature`s) are replayed verbatim within an epoch, as Gemini 3 function calling requires.
- **OpenAI-compatible**: any Chat Completions server. Options for `max_completion_tokens` vs
  `max_tokens`, usage reporting, `reasoning_effort`, and arbitrary extra JSON fields (`min_p`, …).
- **NovelAI text**: NovelAI's OpenAI-compatible endpoint (`https://text.novelai.net/oa/v1`), string
  content only, prompt-based tools by default.

### NovelAI images

`NovelAiImageGenerator` posts `params_version: 4` requests as `multipart/form-data` to
`https://image.novelai.net/ai/generate-image`, with V4+ structured captions and the web client's
quality tags and undesired-content presets for V4, V4.5 and V5 models. Responses are unzipped (or
read as a bare image). Personas add their **appearance tags** to self-portraits, and the persona
editor can generate an avatar.

## Extending

- **New model backend**: implement `LlmProvider` (`stream` + `listModels`) and register it:
  `harness.providers.register(MyProvider())`. Add a `ProviderType` if it needs its own UI options.
- **New agent tool**: implement `AgentTool` (a `ToolSpec` with a JSON Schema built by `schema { }`,
  `isAvailable`, `execute`) and `harness.tools.register(MyTool())`. Tools are offered natively,
  through the prompt protocol, or not at all, depending on each model profile's tool mode.
- **New image backend**: implement `ImageGenerator` and pass it to `Harness(imageGenerator = …)`.
- **Different storage**: implement the interfaces in `core/storage/Repositories.kt`.
- Domain objects are stored as JSON documents, so adding fields with defaults needs no schema change.
  Before 1.0, a schema change simply bumps `HarnessDatabase`'s version, which wipes local data.

## Testing

```
./gradlew :core:test                 # providers (mock servers), context epochs, compaction, agent loop,
                                     # memory recall, heartbeat, NovelAI request format
./gradlew :data:desktopTest          # the migration steps on the desktop's SQLite
./gradlew :app:testDebugUnitTest     # Robolectric: Room repositories + a smoke test of the real app UI
./gradlew :desktop:test              # the desktop app: its first launch offscreen, keys, heartbeats,
                                     # what goes in letters, the player, the tray's helpers
./gradlew :desktop:run               # opens the desktop app (its data in FUMIBAKO_HOME, if set)
```

Live API smoke tests are skipped unless enabled:

```
HARNESS_LIVE_TESTS=1 ANTHROPIC_API_KEY=… NOVELAI_API_KEY=… ./gradlew :core:test --tests '*LiveSmokeTest*'
```

They make a two-turn Claude tool round trip with thinking replay, list NovelAI text models, and
generate one NovelAI image (`NOVELAI_OUTPUT=/path/out.png` saves it).

To render the main screens to PNGs with sample data (for design review, no device needed):

```
HARNESS_SCREENSHOTS_DIR=/path/to/shots ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTour*'
```

## Design

The visual direction (madoromi ink colours, letter-style messages, washi tape, stickers) is written
up in [`docs/design/`](docs/design/README.md), with interactive mockups; v0.1 builds all of it except
stickers. Fonts are bundled placeholders (SIL OFL, see `ui/src/commonMain/composeResources/files/licenses/fonts.txt`). Plans:
[`docs/plan-v0.1.md`](docs/plan-v0.1.md), [`docs/plan-v0.2.md`](docs/plan-v0.2.md), [`docs/plan-v0.3.md`](docs/plan-v0.3.md),
[`docs/plan-v0.3.5.md`](docs/plan-v0.3.5.md), [`docs/plan-v0.4.md`](docs/plan-v0.4.md) (the planner).

## Known limitations / next steps

- Generation runs in the app process; Android may stop a long reply if the app is backgrounded for a
  long time. A foreground service would make this robust.
- Videos and songs larger than a request can carry (about 14 MB for Gemini) go by their frames or
  label; Gemini's file upload API isn't used yet.
- Heartbeat timing is best-effort: WorkManager batches periodic work, and battery optimizations can
  delay wakeups.

## License

Fumibako is free software: you can redistribute it and modify it under the terms of the GNU General
Public License, version 3 or (at your option) any later version. It comes with no warranty. See
[`LICENSE`](LICENSE).

Two parts keep their own licenses:

- **The madoromi palette** (`docs/design/madoromi-palette.json` and
  `ui/src/commonMain/kotlin/dev/aiharness/app/ui/theme/MadoromiPalette.kt`): Mozilla Public License 2.0.
- **The bundled placeholder fonts** (`ui/src/androidMain/res/font/`): SIL Open Font License 1.1, with the
  notice in `ui/src/commonMain/composeResources/files/licenses/fonts.txt`.
