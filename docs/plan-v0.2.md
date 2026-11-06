# Plan: v0.2

Goal: the remaining screens in the stationery style, and a memory system that behaves more like
memory: feelings attached to what is remembered, things that matter staying put, small things
fading, and nothing rewritten behind your back. Designs are on the "Remaining screens" canvas and
in [`design/README.md`](design/README.md); the background research is
[`research/memory-systems.md`](research/memory-systems.md).

Stickers (including per-persona unread marks and emotion stickers) move to v0.3.

## Progress

- **Step 1, memory engine: done.** Three levels, felt and current feelings with revision limits,
  the nine-family word list, derived salience and strength, dormant and replaced memories, links
  to the source letter, the updated tools, and recall that tells the persona how it felt. Tested
  with unit tests and a four-month fixture.
- **Step 2, heartbeat: done.** A list of activities (episodes, reflect, revisit, settle, sort
  feelings) feeding one private moment that only happens when something is due or the persona may
  write; per-persona rhythm and heartbeat model; scheduling at the shortest interval; every wakeup
  logged (including why it rested). The heartbeat settings now live under a "rhythm", so the
  installed app's heartbeat timings reset to the defaults once.
- **Steps 3 to 5, controls, settings, your personas, persona pages: done.**
  - Controls: index-card fields with ruled lines under the text, steppers you can type into,
    square selects, and tape placements that never repeat on neighbouring cards.
  - Settings is a contents page (一 to 八) with a page per section.
  - Personas edit in place: **Edit** on the page, **Done** to keep. Back while editing leaves
    edit mode, asking first if anything changed. Each persona has a heartbeat page of its own.
  - Your personas have a gallery and a page each: about you, what each persona remembers about
    you, letters written as you, and the default for new letters. Tapping a "Writing as" card
    opens its page; switching happens there or inside a letter.
  - The old one-screen editors are gone. The keys screen keeps its old look until step 7.
- **Step 6, memory book, memory card, ageing, burn: done.**
  - The memory book is a scrapbook. Tabs by kind; filters by feeling family and by who a memory
    is about; search. The always-in-mind memories sit on an index card at the top. Each day gets
    a date stamp and a small collage: the day's picture, a line quoted from the letter a memory
    came from, label slips for facts, torn notes for thoughts, taped pages for what happened.
    Earlier months are tucked into envelopes (only the V flap shows).
  - Strong feelings get an ink stamp: the family's icon in a double ring, pressed a little
    unevenly. Mild ones get no mark; the card shows the word either way.
  - Ageing: fading memories go pale, worn (dormant) ones go brown with a coffee ring, and
    replaced ones are struck through with what replaced them on a slip.
  - One memory opens as a card: how it felt when kept and how it feels now (with the persona's
    reason), how much it matters, who it's about, where it came from, how often it came up.
    With editing allowed, **Edit** opens the same card for editing, and it can be forgotten:
    it burns away and **Undo** stays until you leave the page.
  - The burn runs as an AGSL shader on Android 13 and later and on the CPU below that, both
    from one definition (`BurnShape`) that the unit tests check.
  - Not yet: a memory the persona lets go on its own doesn't burn in front of you; the
    heartbeat log counts it ("let a memory go").
- **Step 7, polish: done.**
  - The memory book's tabs are pieces of washi tape folded over the page edge, one ink each
    (yours for About you); the chosen one stands up further and shows its pattern.
  - Envelopes open: the flap folds up and back, a few notes peek out, and the month's days slide
    out of the envelope one after another.
  - The model profile and keys screens are stationery pages now; a new model profile starts from
    a preset (it opened a blank page before). "Manage models" goes to Settings, Models and keys.
  - Memory slips on the persona page open their memory; the last old-style text fields are gone.
  - Version 0.2.0.

## What v0.2 includes

1. **Memory book** as a scrapbook: a diary by date with tabs by kind (always in mind, about you,
   what happened, thoughts) and filters (feeling, who it's about). Each day is a small collage:
   a date stamp, polaroids of pictures from that day's letters, torn strips quoting the letters,
   label slips for facts, torn notes for thoughts. Older months are tucked into envelopes.
2. **One memory:** a read-only card (felt when kept, feels now and why, how much it matters, where
   it came from, a link to the letter). With editing allowed, an **Edit** button opens the same
   card for editing (**Done** to finish), including "forget".
3. **Visible ageing:** fresh, fading, worn (dormant), replaced. **Letting go** plays a burn, and
   **Undo** stays until you leave the screen.
4. **Persona page editing** in place (Edit, then each entry becomes index-card fields; Done).
5. **A persona's own heartbeat** page, and **Settings › Heartbeat** as the usual settings, with a
   list of personas that have their own.
6. **Settings** as a contents page (一 to 八) with a page per section, including the new
   **Memory** page (long-term memory, memories per letter, "let me edit memories" off by default,
   ageing and burning on, recall by meaning).
7. **Your personas:** a gallery like the personas gallery, and a page for each (about you, what
   each persona remembers about you, letters written as them, default for new letters), reached
   from the Personas screen.
8. **Controls:** index-card fields (ruled lines the text sits on), steppers whose number can be
   typed into, and tape placement that varies from screen to screen.

## Memory: engine changes

From the research recommendations, adjusted to your answers:

- **Importance becomes three levels:** in passing, worth keeping, matters deeply (stored as a
  small enum). Personas choose it in words; old numbers and common synonyms are accepted
  (1 to 3, 4 to 7, 8 to 10). "Matters deeply" is rationed per persona; when over the limit, a
  heartbeat asks the persona which to let settle. On a merge, the level only rises to "matters
  deeply" when the new save says so.
- **Salience is derived**, not asked for: the level, plus a little for strong feelings, plus a
  little for memories you wrote or confirmed. It drives ranking (a small term, as now), how fast a
  memory fades, and what can go dormant.
- **Feelings:** `feltMood` (the persona's mood when the memory was kept, never changed) and `mood`
  (how it feels now), with a one-line reason and date when revised. Revisions are limited: at
  most once a week per memory, only for memories that just came up or were reviewed in a
  heartbeat, original always kept. Whether the persona mentions a change of heart is up to it.
- **A built-in word list** maps mood words to one of nine families (bright, fond, proud, calm,
  sad, uneasy, cross, wonder, wistful) and to mild or strong. Unknown words are sorted once by the
  utility model during a heartbeat and remembered. No model is asked for numbers.
- **Feelings change how memories are told, not which are recalled.** Recalled memories reach the
  persona with their feeling ("felt anxious, now hopeful: ..."). No mood-matched recall.
- **Fading:** each memory has a strength that halves over a time set by its level (about a week
  for "in passing", a month for "worth keeping", half a year for "matters deeply"), lengthened by
  strong feeling and by real use. Real use means the persona searched for it, a reflection cited
  it, or you edited or confirmed it. Being pulled into a letter automatically doesn't count, which
  ends today's rich-get-richer loop.
- **Dormant:** weak, old, not "matters deeply" and not "always in mind". Skipped by automatic
  recall, still found by search and shown (worn) in the memory book. Nothing is deleted except by
  the persona letting go, or by you with editing on.
- **Replaced:** a newer fact can mark an older one as replaced instead of overwriting it; replaced
  memories leave recall and stay as history.
- **Where it came from:** memories keep the id of the letter they came from, for the card's link
  and the scrapbook's quotes and polaroids.
- **Tools:** `memory_save` gains `weight` and an optional `mood` (defaulting to the reply's mood
  tag); `memory_update` gains `mood` with `reason`, and `replaced_by`; `memory_search` counts as
  real use.
- **Your permission:** `MemorySettings.userCanEdit`, off by default. The app's own screens enforce
  it; personas always manage their own memories.

## Heartbeat: engine changes

- **Per persona:** a persona can use the usual settings or its own (interval, quiet hours, wait
  after the last letter, daily limit, unanswered limit) and, optionally, a different model for its
  heartbeats. The background job wakes at the shortest interval any persona needs, and each
  persona keeps track of its own last heartbeat.
- **Activities, built to grow:** the heartbeat becomes a list of activities, each a small
  self-contained step with its own conditions, tools and log entry:
  1. write up recent conversation (exists today);
  2. reflect, when enough emotional weight has built up since the last reflection;
  3. revisit a few recently touched memories (may revise feelings or levels, or mark something
     replaced);
  4. tidy (merge near-duplicates, keep "matters deeply" within its limit);
  5. decide whether to write to you (exists today).

  **Activity limit:** a heartbeat may take at most 3 meaningful actions in the persona's own
  voice (`HeartbeatRhythm.activityLimit`, adjustable in the usual settings and a persona's own).
  Writing to you uses one; reflecting, revisiting, tidying and upkeep (episodes, embeddings,
  sorting feeling words) are free. There's no fixed order: the persona is told how many it has
  left and chooses, and the engine counts them (`Beat.takeSlot`). An activity that acts outside
  the private moment (pen pals, later) takes its slot the same way. In v0.2 only the letter uses
  one, so the limit starts to matter with the next such activity.

  New activities can be added later without touching the rest. This is where the persistent
  "agent home" grows: more things personas do while you're away, each showing up in "While you
  were away" and on their page. The activity log becomes per-activity rather than a fixed list.
- Chapter roll-ups (summarising many old episodes into one) wait until there is enough history
  to need them.

## Order of work

Each step ends with its tests green and a push.

1. **Memory engine:** model fields, levels and parsing, word list, salience, fading, dormant,
   replaced, revisions and their limits, tools, recall text, source links. Unit tests, plus a
   fixture test: months of scripted letters with planted facts, a changed fact, emotional events
   and trivia, checked for what is recalled and what fades.
2. **Heartbeat:** activities, per-persona settings and scheduling, the reflect and revisit
   activities, the heartbeat model, the generalised activity log. Unit tests.
3. **Controls:** index-card field, stepper, the tape layout chooser (deterministic per screen,
   never the same placement twice in a row).
4. **Settings** contents page and its pages, including Memory; **your personas** gallery and page.
5. **Persona page** editing in place; **a persona's heartbeat** page.
6. **Memory book** scrapbook, the memory card (read and edit), ageing looks, and the burn
   (a GPU shader on Android 13 and later, the same effect drawn on the CPU before that).
7. **Polish and test pass:** screenshot tours (day, night, large text) for every new screen, the
   smoke test, a release build, and an APK to your phone with a checklist.

No table changed in v0.2 (rows are JSON, and new fields have defaults), so the database version
stays and installed data is kept on update; only the heartbeat timings reset once (step 2).

## Testing

- **Automated:** unit tests for levels and parsing, salience, fading, dormant and replaced,
  revision limits, the word list, per-persona scheduling and each heartbeat activity; Robolectric
  tests for the new screens; screenshot tours.
- **On your phone (v0.2.0):**
  1. Install over v0.1; letters, personas and memories are still there.
  2. Persona page: Edit, change the look and words, Done; back while editing asks before
     discarding. Change the picture from the polaroid.
  3. A persona's heartbeat page: switch to its own rhythm, change it, "Run one now", and see the
     result under Recent heartbeats.
  4. Settings: each contents line opens its page; steppers take a typed number; Activities per
     heartbeat is in the Rhythm card.
  5. Your personas: add one, write a letter as it, make it the default.
  6. Memory book with real letters: tabs, the feeling filter, search; days look right; open and
     close an earlier month's envelope (only once a month has passed).
  7. A memory's card: read-only at first; turn on "Let me edit memories" (Settings, Memory), then
     Edit, change the feeling, Done; Forget, watch it burn, Undo.
  8. A heartbeat that writes: the letter arrives and the log says so.
  9. Models and keys: add a profile from a preset, Fetch models, Send a test message; add a key.

## Risks

- **Weaker models and the new tools.** Levels and mood words are designed to be easy, and
  everything falls back to sensible defaults, but small local models may ignore them.
- **The memory book collage** depends on real data (pictures, quotes) that test fixtures only
  approximate; expect a round of tuning after you use it.
- **The burn on older phones** runs on the CPU; it should be fine for one slip at a time, but is
  untested on low-end devices.

## Decisions (made 2026-11-05)

1. **Emotion marks:** B, small ink stamps with an icon per feeling family, on strong feelings
   only. Make them look more like real rubber stamps when building the memory book. C (the
   persona's own sticker) comes with stickers in v0.3.
2. **Level names:** "In passing", "Worth keeping", "Matters deeply".
3. **The burn:** the canvas prototype is the one to build.
4. **Heartbeat activity limit** (2026-11-06): at most 3 activities per heartbeat by default,
   adjustable. Only meaningful actions in the persona's own voice use a slot; in v0.2 that is
   writing to you. Reflecting, revisiting, tidying and upkeep are free. The persona sees what is
   due and how many slots it has left, and chooses; the engine enforces the count. The setting
   lives on the rhythm, so a persona with its own heartbeat can change it too.
