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
- **Each desk has a token limit,** adjustable per persona (defaults to decide).
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
     reason ("Juno's letter is waiting", "Sam moved his knight", "You haven't drawn in a week"),
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
what a letter needs, and nothing for chess.

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

## The desk's token limit (proposed)

- **A daily limit per desk,** counted from the usage providers report (estimated from length where
  they don't). It replaces the pen pals' shared daily and weekly ceilings.
- **Shown in the drawer** as today's use against the limit.
- **When it's reached,** the desk closes until the next day, except that the persona still sits
  down to decide whether to write to you, so a full desk never silences it. (To confirm.)
- **Defaults** to decide once the activities' real costs are known: a sitting down is roughly 1,500
  to 3,000 tokens, an activity 3,000 to 7,000, a letter to you more, depending on how many of your
  letters it reads.

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

v0.6 adds pen pals (the letter tray) and at least one game. The planner's look-ahead (v0.4) runs
in the private moment until then, and moves to the desk in v0.6.

## Open

- **Mockups:** the desk page, the drawer, leaving something, a sealed diary entry, and what the
  persona sees when it sits down.
- **The desk limit:** whether writing to you still happens once the desk is closed for the day,
  and the defaults.
- **Which game** comes first.
