# Ideas

Agreed directions from the design session (2026-11-06). Bigger features get a note of their own
when we work through them.

## Roadmap

Updated 2026-11-06, with the user's rough plan to v0.7.

- **Shipped on the build branch:** v0.3 (pictures, then stickers); v0.3.1 (you in pictures, and a
  NovelAI key turns pictures on); v0.3.5 (media in letters, keepsakes in the memory book, Settings
  Media, several pronoun sets per persona). See `docs/plan-v0.3.md` and `docs/plan-v0.3.5.md` on
  the build branch.
- **v0.4: the planner,** in [`calendar.md`](calendar.md), with two passes of mockups. Not started
  on the build branch yet.
- **v0.5: import and export, the stationery update, and quality of life:**
  - import and export (below);
  - the stationery update: Japanese stationery across the app (below);
  - from Fieldwork (below): the context slip ("What she had in mind"), the note after a model
    switch, thinking lines the persona chooses, and searching past letters. The context slip sits
    beside the "Show thoughts" slip under a letter, each with an icon of its own (to design).
- **v0.6: activities,** in [`activities.md`](activities.md): the writing desk, today's activities
  moved onto it, at least one game, and pen pals ([`pen-pals.md`](pen-pals.md)). **v0.6.5:**
  probably another batch of activities.
- **v0.7: the outdoor update:** seasons, and what it means for a persona to live in a place of its
  own (often fictional) with its own weather; weather events for personas (below). It will feed
  back into the "going out" activities.
- **Not designed or scheduled:** a scrapbook of your own (see [`keepsakes.md`](keepsakes.md)), a
  Discord bridge, and the rest of this page.

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

Moved to [`media.md`](media.md); shipped in v0.3.5.

## Japanese stationery across the app (v0.5)

From the planner's second pass ([`calendar.md`](calendar.md)); these would reach beyond the
planner:

- **More tape patterns** (agreed, including hearts, with more to come): 市松 ichimatsu (checks),
  七宝 shippō (linked rings), 青海波 seigaiha (waves), 麻の葉 asanoha (hemp leaf), 矢絣 yagasuri
  (arrow feathers), hearts, 鱗 uroko (scales), 亀甲 kikkō (tortoiseshell) and 籠目 kagome (basket
  weave), as new `TapePattern` choices beside Plain, Dots, Stripes and Gingham, in the owner's
  ink like the others. Drawn on the "Pieces of the planner" board.
- **A 12-hour clock** (agreed): "Phone's setting", "12-hour" or "24-hour" on the Look page,
  applying to letters, notifications and the planner alike.
- **Seasons that follow the solar terms:** a Look setting, "Seasons change with: the months"
  (today's meteorological seasons) or "the solar terms" (spring from 立春 in early February, summer
  from 立夏 in early May, autumn from 立秋 in early August, winter from 立冬 in early November),
  so the theme and the planner's season band agree. In the southern hemisphere both would flip
  (see "Local seasons" in [`calendar.md`](calendar.md)).
- **Persona marks** (agreed per persona; see [`calendar.md`](calendar.md)): besides the unread
  mark, a signature seal, a wake mark and a done mark. The signature seal could also sign letters
  or sit by the persona's name.
- **Traditional month names** (霜月, 師走) on the memory book's month envelopes, as on the
  planner's month page.

## Weather and places (v0.7)

Raised 2026-11-06, separately from the planner; the v0.7 outdoor update goes into it in depth:

- **Weather events for personas:** notable events reach the persona with the time note in each
  turn's context: events in the sky (meteor showers, eclipses, full moons, solstices), first frost
  and first snow, and weather warnings where you are. Events only, not a daily forecast, to keep
  tokens down. Needs your approximate location (a permission, or a town you type) and a weather
  service.
- **A place of a persona's own:** a persona can have a place (a real town or an imagined one), so
  it can talk about the weather where it is. A real town uses the same weather service; an
  imagined place's weather could be made up to suit the season.
- **Other ways to make the seasons local**, from the planner's second pass, not chosen for now:
  microseasons written for your region (72 short lines per region), or a persona naming the
  week's microseason as an activity.

## Import and export (v0.5)

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

- **"What she had in mind"** (v0.5): a folded slip under each letter showing what the persona was
  given (recalled memories, the time note, later the planner line). It sits beside the "Show
  thoughts" slip, each with an icon of its own. Mockups before committing.
- **"Your model changed"** (v0.5): a one-line note to the persona after its model is switched.
- **The persona's own rest:** it can set its own quiet hours or take a break, shown with a
  resting sign and in its planner. The mirror of the user's busy calendar. (Proposed for v0.4, in
  [`calendar.md`](calendar.md).)
- **Free time and a daily postcard:** a visit with no task (staying quiet leaves no trace), and a
  postcard about its day at a time you choose. Both are activities (see
  [`activities.md`](activities.md)).
- **Collections and belongings:** small things a persona keeps (plants, recipes, a dream log,
  pictures). Some belong in the scrapbook, others on the writing desk.
- **Promises and reminders with dates:** memories that wait until a date or come due. They show
  in the persona's planner. (Proposed for v0.4, in [`calendar.md`](calendar.md).)
- **Thinking lines** (v0.5): the persona chooses what shows while it writes ("Aria is chewing her
  pen").
- **Searching past letters** (v0.5): exact wording across conversations, not only memories.
- **Notes to the builder** (the persona reports something broken, confusing or missing): useful
  for debugging, probably not in the final build.
- **A Discord bridge**, at some point. Not Telegram.
- **Not taking:** the outside reviewer and the undo history.

## Activities

Things a persona can do in its heartbeats, beyond what v0.2 has. They run at the persona's
writing desk, in v0.6 and after ([`activities.md`](activities.md)):

- **Making:** a picture for its sketchbook or for you, a poem or a haiku, its own stickers (v0.3).
- **Playing, by post:** correspondence chess, shiritori, twenty questions, a riddle left in a
  letter. The app keeps the board and the model only makes a move. Pen pals can play each other.
- **Keeping:** a diary (the persona can seal entries), collections, the memory book.
- **Going out:** reading a link you shared and writing notes; looking up something it's curious
  about; postcards from an imagined trip.
- **Seasonal:** moon-viewing in autumn, New Year cards, first snow, following the madoromi seasons
  and the calendar; a haiku on the first day of a solar term or microseason (see
  [`calendar.md`](calendar.md)).

Rules:

- **Every activity can be switched on or off per persona.** Expensive ones (pictures, web
  lookups) are meant for a small core cast, so they start off for new personas.
- **Slots:** making, playing and going out use activity slots; keeping is free (see
  [`pen-pals.md`](pen-pals.md)).
- **Things you leave on the desk** (a link, a riddle, a game challenge) are offered first at the
  persona's next heartbeat.

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
