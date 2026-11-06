# Ideas

Agreed directions from the design session (2026-11-06). Bigger features get a note of their own
when we work through them.

## Roadmap

- **v0.3** (being built): pictures, then stickers. See `docs/plan-v0.3.md` on the build branch.
- **v0.3.5: media**, in [`media.md`](media.md).
- **v0.4: the planner**, in [`calendar.md`](calendar.md).
- **After stickers, not scheduled:** letters between personas, in [`pen-pals.md`](pen-pals.md).
- Everything else below is unscheduled.

## Name and icon

- **The app is called Fumibako** (文箱, a lacquered box for keeping letters), "Fumi" for short. 文
  is already the Letters tab. A search found no app by that name, only a physical tray. Renamed in
  v0.2.2, with the icon below (see `docs/design/icon.md` on the build branch).
- **Icon (agreed 2026-11-06):** a flat wax seal with an uneven edge, 文 cut out of it in the
  paper colour inside a thin ring (hakubun), in Yuji Syuku, raised slightly so it sits centred in
  the ring. The seal closes an envelope: the flap's V lines run in from the top corners and meet
  behind it, and the flap is a shade darker than the envelope. No tape. Beni wax in the mockups.
  The themed (single-colour) icon is the seal alone. The embossed wax and corner-tape versions
  were dropped: the 文 didn't read, and the tape unbalanced it. Mockups: the "Fumibako icon"
  design canvas (private claude.ai artifact:
  https://claude.ai/artifact/7VkaN8Dfk8qQhJYgLgv9Ns).

## Media

Moved to [`media.md`](media.md), planned for v0.3.5.

## Import and export

- **SillyTavern cards** (Character Card V2: JSON in a PNG `chara` chunk; V3: a `ccv3` chunk or a
  `.charx` zip). `name`, `description`, `personality` and `scenario` map to the same fields;
  `first_mes` to the greeting; `alternate_greetings` to a new list you pick from when starting a
  letter; `mes_example` to example dialogue; the PNG to the portrait; V3 `emotion` assets to mood
  portraits; `creator_notes` are shown on import and never sent. `extensions` are kept untouched
  (the spec requires it), and ours (ink, tape, picture prompts) go under a namespaced key.
- **Lorebooks become memories** of a new **lore** kind, which doesn't fade by default. Entries
  marked `constant` become "always in mind".
- **Any memory can be made unfadeable**, whatever its kind. That is separate from "matters deeply"
  (which fades slowly) and "always in mind" (which is always in the prompt): an unfadeable memory
  never weakens or goes dormant, but is still recalled only when relevant.
- **A card's own prompts** (`system_prompt`, `post_history_instructions`) are offered on import,
  not applied silently; cards often carry prompts written for other setups.
- **Export** writes a V3 PNG that also carries the V2 chunk. A separate full backup holds the
  persona, its memories and pictures; it asks before including memories, leaves out those in
  confidence by default, and never includes keys.
- **Open:** who can mark a memory unfadeable (the persona, you with editing on, or both).

## From Fieldwork

Ideas only: [Fieldwork](https://github.com/4thkira/fieldworkharness) is GPL-3.0, so no code.

- **"What she had in mind":** a folded slip under each letter showing what the persona was given
  (recalled memories, the time note, later the calendar line). Mockups before committing.
- **"Your model changed":** a one-line note to the persona after its model is switched.
- **The persona's own rest:** it can set its own quiet hours or take a break, shown with a
  resting sign and in its planner. The mirror of the user's busy calendar.
- **Free time and a daily postcard:** a visit with no task (staying quiet leaves no trace), and a
  postcard about its day at a time you choose. Both are activities.
- **Collections and belongings:** small things a persona keeps (plants, recipes, a dream log,
  pictures). Some belong in the scrapbook, others at the writing desk.
- **Promises and reminders with dates:** memories that wait until a date or come due. They show
  in the persona's planner.
- **Thinking lines:** the persona chooses what shows while it writes ("Aria is chewing her pen").
- **Searching past letters:** exact wording across conversations, not only memories.
- **Notes to the builder** (the persona reports something broken, confusing or missing): useful
  for debugging, probably not in the final build.
- **A Discord bridge**, at some point. Not Telegram.
- **Not taking:** the outside reviewer and the undo history.

## Activities

Things a persona can do in its heartbeats, beyond what v0.2 has:

- **Making:** a picture for its sketchbook or for you, a poem or a haiku, its own stickers (v0.3).
- **Playing, by post:** correspondence chess, shiritori, twenty questions, a riddle left in a
  letter. The app keeps the board and the model only makes a move. Pen pals can play each other.
- **Keeping:** a diary (which can be sealed), collections, the memory book.
- **Going out:** reading a link you shared and writing notes; looking up something it's curious
  about; postcards from an imagined trip.
- **Seasonal:** moon-viewing in autumn, New Year cards, first snow, following the madoromi seasons
  and the calendar.

Rules:

- **Every activity can be switched on or off per persona.** Expensive ones (pictures, web
  lookups) are meant for a small core cast, so they start off for new personas.
- **Slots:** making, playing and going out use activity slots; keeping is free (see
  [`pen-pals.md`](pen-pals.md)).

## Keeping tool tokens down

Researched 2026-11-06. Today a conversation's tool set is frozen for each epoch, because changing
`tools` invalidates Claude's cache and its saved reasoning, so every feature and activity would
add schemas to every turn or force a new epoch.

- **Claude: mid-conversation tool changes** (beta `mid-conversation-tool-changes-2026-07-01`;
  Claude Opus 5.5, Opus 5, Opus 4.8, Fable 5 and 5.1, Sonnet 5.5; not Sonnet 5). Every tool is
  declared up front, rarely used ones with `defer_loading`, which keeps them out of the model's
  context. The app then adds or removes tools between turns with a system message, and the cached
  prefix survives. Turning on the calendar or an activity would no longer start a new epoch.
- **Claude: tool search** (server-side, regex or BM25). For a long tool list: Claude searches the
  deferred tools and loads what it needs; loaded schemas are appended, so the cache survives.
- **Other providers:** no equivalent we know of. Choose the tool set per context (a letter, a
  heartbeat moment, the pen pals' writing desk) and change it only at epoch boundaries.
- **Cost versus attention.** Once cached, schemas are cheap on Claude (cache reads are a twentieth
  of the input price on Opus 5.5). The stronger reason to keep the list short is that smaller
  models stop noticing tools in a long list, which Fieldwork also found.
