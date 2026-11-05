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

The database version is bumped once for the schema changes; per the pre-1.0 rule, installed data
is wiped on update.

## Testing

- **Automated:** unit tests for levels and parsing, salience, fading, dormant and replaced,
  revision limits, the word list, per-persona scheduling and each heartbeat activity; Robolectric
  tests for the new screens; screenshot tours.
- **On your phone:** a checklist like v0.1's, plus: memories keep their feelings and show "felt
  then, feels now"; the memory book collage looks right with real letters; burning and undo; a
  persona with its own heartbeat; editing memories off and on.

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
