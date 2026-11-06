# Pictures (start of v0.3)

Agreed 2026-11-06. Built first in v0.3, before stickers, because stickers are drawn from the same
persona prompts.

## Decisions

- **Three prompts per persona instead of one:**
  - **Self:** the body and the traits that never change: hair, eyes, build, glasses, a scar.
  - **Outfit:** what the persona is wearing now. Each persona has a default outfit; the current
    one can differ, and "back to default" restores it.
  - **Style:** artist and style tags. A default for everyone in Settings, Pictures; each persona
    can use its own instead.
- **Outfits change persona-wide.** You can change one on the persona's page, and the persona can
  change clothes itself with a tool (getting ready for an outing, say). A change lasts until the
  next one, in every conversation.
- **Image hosts besides NovelAI:** a local Stable Diffusion server (ComfyUI, Forge or
  Automatic1111), OpenAI images (gpt-image) and Google images (Gemini and Imagen).
- **Prompts are written once.** Tags go to NovelAI and Stable Diffusion as written; for OpenAI and
  Google, which work from descriptions, the harness joins them into sentences ("Drawn in the
  style of …. <self>. Wearing <outfit>. <the scene>").

## How it works (sketch)

- **Persona:** `selfPrompt` (replacing `appearanceTags`), `defaultOutfit`, `outfit` (null means the
  default), `stylePrompt` (null means the Settings default). Edited on the persona page's
  "In pictures" card.
- **Image profiles,** like model profiles: the host, its key or server address, the model, the
  size, and the host's own options (NovelAI's sampler, steps and undesired content; the local
  server's checkpoint or workflow; OpenAI's quality; Google's aspect ratio). One is the default,
  and a persona can use another.
- **A `change_outfit` tool** with the new outfit (or "default") and a short reason. It shows in
  "While you were away" ("Aria changed into a raincoat") and as "Wearing …" on the persona page.
- **Drawing** builds the prompt from style, self, outfit and the scene (plus NovelAI's quality
  tags). The picture dialog in a letter can leave any of the three out.
- **Stickers** use the same prompts, so a persona's sticker pack looks like the persona.

## Open

- **Mockups** for the "In pictures" card (self, outfit, style) and the image profile page.
- **Local Stable Diffusion:** which API first. Forge and Automatic1111 share a simple `txt2img`
  call; ComfyUI needs a workflow, so it would come with a default one you can replace.
