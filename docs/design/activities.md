# Activities and the writing desk (v0.6)

Agreed 2026-11-06 in the design session. v0.6 brings the desk, today's activities moved onto it, at
least one game, and pen pals ([`pen-pals.md`](pen-pals.md)); v0.6.5 is planned as another batch of
activities. The list of possible activities is in [`ideas.md`](ideas.md) (*Activities*); this note
is the framework they run in. Quoted app text follows the app's American spelling.

## Today

Each heartbeat runs upkeep (episodes, embeddings, sorting feeling words), then one private moment
in the thread with you: the whole conversation, a hidden `<heartbeat>` note with what's due
(reflecting, revisiting, letting memories settle, drawing stickers), the persona's memory tools,
and how many slots it has left. It tends its memories (free), may draw stickers (a slot), and
writes to you (a slot) or replies `[SILENT]`. What happened is logged for "While you were away"
and the persona's heartbeat page.

## Decisions (agreed)

- **Every persona has a writing desk.** For the persona, it's where it does things of its own, in
  a small context of its own rather than the thread with you. For you, it's a page you can visit,
  with its things laid out on it.
- **The whole heartbeat runs at the desk,** because it's cheap: the full thread is no longer sent
  just to decide what to do. **Writing to you is a desk activity** whose context includes your
  recent letters.
- **Most things on the desk are open to you.** A persona can seal individual diary entries as it
  likes, and sealed things follow the sealed-letter rules from pen pals: by default you can open
  them, closing shows the seal broken (and the persona is told) while resealing leaves it
  untouched, and settings can stop you opening sealed things or stop the persona sealing them.
- **You can leave things on a persona's desk,** such as a link to read, a riddle or a game
  challenge. It finds them at its next heartbeat, as an activity, and they don't become part of
  your letters.
- **Each desk has a token limit,** adjustable per persona: 40,000 tokens a day to start, to tune
  with use. When it's reached the desk closes until the next day, but the persona still sits down
  to decide whether to write to you.
- **Two calls are fine:** sitting down, then one call per thing taken up.
- **The first game is shiritori** (below).
- **The look:** sharp corners, ink outlines, flat cel-shaded colour with hard-edged shadows,
  halftone screentone instead of blurred overlays, and a vintage 1990s Japanese feel. No soft
  gradients or blurred shadows (see *The look* below).
- **Carried over:** a few activity slots per heartbeat (3 by default); making, playing, going out,
  and writing to you or a pen pal use a slot, keeping and tending memories are free; every
  activity can be switched on or off per persona; expensive ones start off for new personas.

## A heartbeat at the desk (proposed)

1. **Upkeep,** as today, with no model call of the persona's own.
2. **Sitting down** (one small call). The persona gets:
   - its description, voice, mood and what it's wearing;
   - the time, and one line about you: "Sam last wrote 5 hours ago. You've written once since
     without a reply." With the planner shared, its line too ("Sam is at work until 17:30");
   - what has happened at the desk lately, so it doesn't repeat itself;
   - **the menu:** each activity that's switched on and has something to offer now, with its
     reason ("Juno's letter is waiting", "Sam played 'teapot'", "You haven't drawn in a week"),
     and anything you left on the desk;
   - its slots left, and that writing to you is one of them;
   - what's due for its memories (reflecting, revisiting, settling), with its memory tools, so
     tending them happens here, free, as in today's private moment.

   It picks activities with one tool, `take_up(activity, note)`, where the note carries what it
   has in mind ("draw the window cat Sam mentioned") into the activity's own turn. Picking an
   activity that uses a slot takes the slot then. It can pick nothing.
3. **Doing** (one call per picked activity), each in its own context: the persona's description
   and mood, the note from sitting down, a few memories recalled for this activity, the
   activity's materials (the board, the correspondence, the page it's reading, its last diary
   entries), and only that activity's tools.
4. **Afterwards:** what it made is kept on the desk, the log gets a line for each thing, and
   "While you were away" lines lead to what was made.

The desk is also where an activity's tools live, so the thread with you keeps a short tool list:
what a letter needs, and nothing for games.

## Writing to you (proposed)

- **An activity like the others** (it uses a slot), offered when the heartbeat's usual rules allow
  (the quiet after your last letter, the unanswered limit, quiet hours).
- **Its context:** the persona, the note from sitting down, your recent letters (those since the
  latest conversation recap, with that recap, up to a cap), the usual turn context (memories
  recalled for the letter, the time, the planner line), and things on its desk it might send you.
- **The letter goes into your thread** after a hidden heartbeat note, as today, so the
  conversation reads naturally on your next turn. It can carry things from the desk: a drawing as
  a polaroid, a haiku on a slip, a move in a game you're playing.
- **Building note:** where a provider caches prompts (Claude), writing in the full thread with a
  warm cache can cost about the same as a desk turn; the app could choose per provider.

## Back in your thread (proposed)

When you next write, the turn's context block carries one line about the desk since your last
letter ("At your desk since Sam last wrote: you drew the window cat; Juno's reply is waiting"), so
the persona can bring it up if it likes. It's in the context block, not the system prompt, so the
prompt cache is unaffected.

## The desk's token limit

- **A daily limit per desk** (agreed), counted from the usage providers report (estimated from
  length where they don't). It replaces the pen pals' shared daily and weekly ceilings.
- **40,000 tokens a day by default** (agreed, to tune). A sitting down is roughly 1,500 to 3,000
  tokens, an activity 3,000 to 7,000, a letter to you more, depending on how many of your letters
  it reads; a busy heartbeat like the one on the mockup is about 14,000.
- **When it's reached** (agreed), the desk closes until the next day, except that the persona still
  sits down to decide whether to write to you, so a full desk never silences it.
- **Shown in the drawer** as today's use against the limit: a row of twenty blocks that fill in.

## The desk page (proposed)

Reached from the persona's page ("Aria's desk") and by tapping a "While you were away" line. It
replaces the persona's heartbeat page.

- **The desk:** seen from above, in the persona's paper, with one object for each activity that's
  switched on: a sketchbook (its drawings and stickers), a diary, a game board, a letter tray for
  pen pals, the memory book, cuttings it has read, its planner slips. Tap an object to open it.
- **The in-tray:** "From Sam", holding what you've left until the persona takes it up.
- **Lately at the desk:** the log, each line leading to what was made.
- **The drawer:** what the persona can do (each activity on or off; switched off, its object is
  put away in the drawer), activities per heartbeat, the desk's token limit and today's use, when
  it wakes, and the model for heartbeats. Everything on today's heartbeat page moves here.
- **Night:** lamplight, like the other objects on the desk.

## Leaving something on the desk (proposed)

- **What you can leave:** a link (the persona reads it and keeps a cutting with its notes), a photo
  (to look at, or draw from), a question or a riddle, a game challenge, or a prompt ("draw
  something rainy").
- **How:** from the in-tray on the desk page, from Android's share sheet ("Leave on Aria's desk"),
  or by holding a frame in a letter.
- **At its next heartbeat** it's first on the menu. Doing something with it costs whatever that
  activity costs (reading a link is going out, so a slot). The persona can leave it for later or
  set it aside, and can write to you about it, but it isn't part of your letters.

## Shiritori by post (the first game)

Agreed as the first game. Proposed rules and shape:

- **The game:** each word starts with the last letter of the one before ("teapot", then
  "tangerine", then a word starting with "e"). No word comes twice. A round ends when one of you
  passes, and a new one starts with any word.
- **By post:** one word a turn. The persona plays at most one word a heartbeat, and only on its
  turn; it's offered on the menu as "Sam played 'teapot'". You play from the slip on its desk.
- **The app keeps the chain** and checks the first letter and repeats, so the model only has to
  come up with a word. Its turn gets the last few words, the letter it needs, and one tool
  (`play_word(word)` or `pass()`); about 2,000 tokens, so it's cheap and works with any model.
- **Who can play:** you and a persona, and later pen pals with each other.
- **On the desk:** a slip with a mint band (しりとり), the last two words in each player's ink, and
  the letter that's next in a box. Opening it shows the whole chain, the linking letters marked,
  and a field for your word.
- **Uses a slot** (playing). Its "While you were away" line: "played 'tangerine' in shiritori".
- **Later:** a kana version (where a word ending in ん loses, as in Japanese) for personas and
  people who'd like it.

## The look

From the user's notes on the first mockups: lean into the theme, and away from the generic look
of soft gradients.

- **Sharp corners** everywhere except round things (seals, portraits).
- **Ink outlines** (2dp, a dark warm brown) on objects, cards and buttons.
- **Cel shading:** flat fills, with a single hard-edged darker shape for shade and a lighter sliver
  for a highlight. Objects on the desk cast flat, offset shadows.
- **Hard drop shadows** (a few dp, no blur) under cards and buttons; a pressed button or a chosen
  cell moves into its shadow.
- **Screentone** (halftone dots) behind sheets and for shading, instead of blurred or translucent
  overlays.
- **1990s Japanese touches:** small pixel lettering (DotGothic16 in the mockups) for labels, times
  and counters; section labels on coloured tabs; a dark status strip; four-pointed sparkles for
  new things; motion in a few steps rather than smooth easing.
- **Fonts otherwise as the app's** (the book face, the handwriting face and the brush face), all
  placeholders as before.
- Whether this look goes beyond the desk is a question for the v0.5 stationery update.

## What every activity defines (for builders)

| | |
| --- | --- |
| Object | What stands for it on the desk |
| Offered when | Its conditions, and the one-line reason shown on the menu |
| Slot | Whether doing it uses a slot |
| Cost | Cheap or expensive; expensive ones start off for new personas |
| Context | Its materials and the query its memories are recalled with |
| Tools | The tools it adds for its own turn |
| Makes | What it leaves on the desk, and whether it can be sealed |
| Sharing | Whether it can go to you in a letter, and as what |
| Away line | Its "While you were away" phrase |
| How often | Its cooldown |

Today's `HeartbeatActivity` (`prepare`, `invite`, `after`, `usesSlot`) is most of this; doing an
activity in its own turn is the new part (pen pals already needed it).

## Today's activities on the desk

| Activity | Where it happens | Slot |
| --- | --- | --- |
| Recording episodes, embeddings, sorting feeling words | Upkeep | No |
| Reflecting, revisiting, letting memories settle | Sitting down | No |
| Drawing stickers | Its own turn; the stickers go in the sketchbook and on its sheet | Yes |
| Writing to you, with stickers on your note and a change of clothes | Its own turn | Yes |

v0.6 adds pen pals (the letter tray) and shiritori. The planner's look-ahead (v0.4) runs
in the private moment until then, and moves to the desk in v0.6.

## Mockups

The "Writing desk" design canvas (private claude.ai artifact:
https://claude.ai/artifact/834NiGLU9vt32k1Wxc9eH4), drawn 2026-11-06 and redrawn the same day in the look above:

- **Aria's desk** (interactive): her desk from above with a diary (one entry sealed), a
  sketchbook, shiritori by post with you, a letter tray with Juno's letter, the memory book, a
  cutting she read, and the "From Sam" in-tray. Tap any of them to open it; the sealed diary entry opens,
  then closes (seal broken) or reseals. Below, "Lately at the desk", with which lines used a slot.
- **The drawer** (interactive): what she does (switching one off drops it into the drawer, and
  tapping it there puts it back), things of her own per visit, the desk's daily limit with
  today's use as a row of blocks, sealed things, and when she comes to her desk.
- **Leaving something on the desk** (interactive): a link, a photo, a riddle, a first word for
  shiritori, or something to draw, with a note; leaving it drops it into her in-tray.
- **What Aria sees at her desk:** one heartbeat's three calls (sitting down, her sketchbook,
  writing to you), what each is given, roughly what each costs, and the line that reaches your
  thread afterwards.

## Open

- **The look beyond the desk:** whether the v0.5 stationery update takes it across the app.
- **Shiritori's rules:** whether words are checked against a dictionary, and whether either player
  can challenge a word.
