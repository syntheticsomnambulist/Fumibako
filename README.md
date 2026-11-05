# AI Harness (Android)

An Android app for living with AI personas: chat with them, swap models and personas freely, and
let each persona keep its own long-term memory and a "heartbeat" that lets it reflect, tend its
memories, and reach out to you on its own. Image generation uses NovelAI.

Kotlin + Jetpack Compose. All agent logic lives in a plain Kotlin/JVM module (`core`) so it can be
tested without a device and reused elsewhere.

## Features

| Feature | Where |
| --- | --- |
| Chat with streaming replies, thought summaries, regenerate, edit/delete | `app/ui/chat`, `core/agent/AgentRunner.kt` |
| Swappable models: Anthropic (Claude), Google Gemini, any OpenAI-compatible server, NovelAI text | `core/llm/*` |
| Swappable personas, each with its own memories, greeting, appearance tags and default model | `app/ui/personas` |
| Swappable user personas (who *you* are), switchable mid-chat | `app/ui/settings/KeysAndUsers.kt` |
| Long-term memory: agent-written via tools, hybrid keyword + optional embedding recall, core memories, episode recaps | `core/memory`, `core/tools/BuiltinTools.kt` |
| Heartbeat: **Proactive** (background reflection + optional messages), **Time-aware**, or **Off** | `core/agent/HeartbeatEngine.kt`, `app/heartbeat` |
| Image generation via NovelAI (persona can call it as a tool, or you can request one) | `core/image` |
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

Every push runs the tests and builds a debug APK in GitHub Actions (**Actions → Build → the run →
Artifacts**). Debug builds share one signing key, so a new build installs over the old one. While
the repository is private that key is committed (`app/debug.keystore`). It must be replaced before
the repository goes public: see [`docs/going-public.md`](docs/going-public.md), which CI enforces.

**Stored data before 1.0:** there are no migrations. A database schema change wipes the app's data
and it starts fresh, re-creating the sample persona.

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
  tools/                      AgentTool interface, registry, memory and image tools
  image/                      NovelAI image generation
app/                          Android shell
  data/                       Room (JSON-document rows), Keystore secrets, DataStore settings, file blobs
  engine/GenerationManager    Runs turns in an app-wide scope so replies survive leaving the screen
  heartbeat/                  WorkManager worker, scheduler, notifications
  ui/                         Compose screens
```

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

- **Proactive** (default): WorkManager wakes the app every N minutes (15-minute minimum). For each
  persona with heartbeats enabled, it folds new conversation into episode memories, backfills
  embeddings, and, unless blocked by quiet hours, recency, the daily limit or an unanswered
  message, gives the persona a private `<heartbeat>` turn with its memory tools. The persona may
  reflect, then either write you a message (posted to the chat and as a notification) or reply
  `[SILENT]`. Silent heartbeats are discarded and never enter the history.
- **Time-aware**: no background work; each turn includes the current time and elapsed time.
- **Off**: no background work and no time information.

**Run a heartbeat now** in Settings triggers one immediately for testing.

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
./gradlew :app:testDebugUnitTest     # Robolectric: Room repositories + a smoke test of the real app UI
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
stickers. Fonts are bundled placeholders (SIL OFL, see `app/src/main/assets/licenses/fonts.txt`). Plans:
[`docs/plan-v0.1.md`](docs/plan-v0.1.md), [`docs/plan-v0.2.md`](docs/plan-v0.2.md).

## Known limitations / next steps

- Generation runs in the app process; Android may stop a long reply if the app is backgrounded for a
  long time. A foreground service would make this robust.
- Images can be viewed but not yet saved to the gallery or shared.
- Vision input (sending your own photos) is supported by the providers but not yet exposed in the
  chat input.
- Heartbeat timing is best-effort: WorkManager batches periodic work, and battery optimizations can
  delay wakeups.
