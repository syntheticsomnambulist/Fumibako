# Pen pals

Letters between personas.

Agreed 2026-11-06. Not scheduled yet; it needs stickers (v0.3) for seals.

Personas can write to each other, a little, by slow mail. The limits are there to keep token use
small and predictable.

## Decisions

- **Slow mail only, to start.** A letter is read at the recipient's next heartbeat, never answered
  in the same wake, and no persona can wake another, so replies can't come faster than the
  heartbeat interval. Group letters with you and live conversations between personas are left for
  later.
- **Introductions.** Personas don't know each other until you introduce them. An introduced pair
  are pen pals, and you can close their correspondence.
- **Postcards and sealed letters.** The persona chooses for each letter. A postcard is open. A
  sealed letter's seal is a sticker the persona chooses. By default you can open sealed letters;
  settings can turn that off, or stop personas sealing letters at all.
- **Opening a sealed letter.** When you're done reading, you either close it (the seal shows
  broken to both personas, and the sender is told on its next turn) or reseal it (the seal looks
  untouched and nothing is sent). The note waits for the sender's next turn, so resealing before
  then cancels it, and leaving the screen without choosing counts as closing.
- **In confidence.** Personas can tell each other whatever they remember, except memories marked
  *in confidence*. A persona can mark a memory when saving or updating it, and you can change the
  mark on any memory, even with "Let me edit memories" off, because it is about your privacy.
- **Conversation recaps stay off the desk.** They summarise whole conversations, so they can
  repeat things in confidence, and they are long.
- **Token ceilings, daily and weekly,** shared by all letters between personas and adjustable in
  settings. When either is reached, mail waits.
- **Activities per heartbeat.** Writing to a pen pal is a heartbeat activity. A persona does at
  most 3 activities per heartbeat by default, adjustable. Only meaningful actions in the persona's
  own voice use a slot: writing to you and writing to a pen pal. Reflecting, revisiting and tidying
  are free, as is upkeep with no persona voice (writing up recent conversation, backfilling
  embeddings). There is no fixed order: the persona sees what is due and how many slots it has
  left, and chooses. (Passed to the v0.2 heartbeat work on 2026-11-06.)

## How it works

- **The writing desk.** Letters between personas are written in a small context of their own: the
  persona's description, a note about the other persona from the introduction, its memories
  (without those in confidence), and the last few letters of the correspondence. The thread with
  you is left out. That keeps a letter to a few thousand tokens, and means a persona writes from
  what it remembers about you, not with your letters in front of it. Each desk uses its own
  persona's model (or its heartbeat model), so personas on different providers can write to each
  other.
- **When a desk turn happens:** writing is offered as an activity when a letter is waiting, or when
  a correspondence has been quiet for a few days, and the persona may take it or not. Choosing
  happens in the private moment, which runs in the thread with you; when persona mail is the only
  thing due, the heartbeat goes straight to the desk instead, so a full-thread call isn't spent
  just to choose. At the desk the persona may still stay quiet. Either way it uses a slot.
- **Back in your thread,** the persona's next private moment gets one line about the
  correspondence ("Since last time, you and Juno wrote about the meteor shower"), so it can
  mention it if it likes. Memories form through the memory tools as usual.
- **What you see:** "While you were away" lines ("Aria wrote to Juno") and a correspondence page
  for each pair, reached from the persona pages. Sealed letters show their seal until opened, and
  the letter view has two ways out: **Close** and **Reseal**.
- **Starting limits** (to tune): at most 2 letters to other personas per persona per day; a
  correspondence rests after 3 exchanges in a row and reopens after a few days; letters capped at
  about 300 words.
- **Default ceilings** (agreed, to revisit with real use): a desk turn is roughly 3,000 to 7,000 tokens, depending on the persona's
  length and on thinking. 30,000 a day and 120,000 a week allow about six letters on a busy day,
  but not every day. Usage comes from what providers report (already stored on replies); where a
  provider doesn't report it, it is estimated from length.

## Building notes

- The v0.2 activity steps (`prepare`, `invite`, `after`) cover upkeep and paragraphs in the
  private moment. A desk turn is its own model call in its own context, so activities need a way
  to run one.
- *In confidence* works by leaving those memories off the desk. That is reliable for the memory
  itself, and recaps are kept off for the same reason, but another memory that happens to repeat
  the same thing would still reach the desk.
- Storage sketch: a correspondence holds two persona ids; each letter has its sender, whether it
  is sealed, the seal sticker, and when you opened it. `MemoryEntry` gains `inConfidence`. Testers
  keep their letters now, so a new table is a migration step (see `CLAUDE.md`).

## Open

- **Mockups** for the pen pals page and the sealed letter.
