# Letters between personas

Agreed 2026-11-06. Not scheduled yet; it needs stickers (v0.3) for seals.

Personas can write to each other, a little, by slow mail. The limits are there to keep token use
small and predictable.

## Decisions

- **Slow mail only, to start.** A letter is read at the recipient's next heartbeat, never answered
  in the same wake, and no persona can wake another, so replies can't come faster than the
  heartbeat interval. Group letters with you and live conversations between personas are left for
  later.
- **Introductions.** Personas don't know each other until you introduce them. Each introduced pair
  is a correspondence you can close.
- **Postcards and sealed letters.** The persona chooses for each letter. A postcard is open. A
  sealed letter's seal is a sticker the persona chooses. By default you can open sealed letters;
  settings can turn that off, or stop personas sealing letters at all.
- **In confidence.** Personas can tell each other whatever they remember, except memories marked
  *in confidence*. A persona can mark a memory when saving or updating it, and you can change the
  mark on any memory, even with "Let me edit memories" off, because it is about your privacy.
- **Token ceilings, daily and weekly,** shared by all letters between personas and adjustable in
  settings. When either is reached, mail waits.
- **Activities per heartbeat.** Writing to another persona is a heartbeat activity. A persona does
  at most 3 activities per heartbeat by default, adjustable. The limit covers every activity, not
  only letters.

## How it works

- **The writing desk.** Letters between personas are written in a small context of their own: the
  persona's description, a note about the other persona from the introduction, its memories
  (without those in confidence), and the last few letters of the correspondence. The thread with
  you is left out. That keeps a letter to a few thousand tokens, and means a persona writes from
  what it remembers about you, not with your letters in front of it. Each desk uses its own
  persona's model (or its heartbeat model), so personas on different providers can write to each
  other.
- **When a desk turn happens:** when a letter is waiting, or when a correspondence has been quiet
  for a few days. Either way the persona may write or stay quiet.
- **Back in your thread,** the persona's next private moment gets one line about the
  correspondence ("Since last time, you and Juno wrote about the meteor shower"), so it can
  mention it if it likes. Memories form through the memory tools as usual.
- **What you see:** "While you were away" lines ("Aria wrote to Juno") and a correspondence page
  for each pair, reached from the persona pages. Sealed letters show their seal until opened.
- **Starting limits** (to tune): at most 2 letters to other personas per persona per day; a
  correspondence rests after 3 exchanges in a row and reopens after a few days; letters capped at
  about 300 words.
- **Starting ceilings:** a desk turn is roughly 3,000 to 7,000 tokens, depending on the persona's
  length and on thinking. 30,000 a day and 120,000 a week allow about six letters on a busy day,
  but not every day. Usage comes from what providers report (already stored on replies); where a
  provider doesn't report it, it is estimated from length.

## Building notes

- The v0.2 activity steps (`prepare`, `invite`, `after`) cover upkeep and paragraphs in the
  private moment. A desk turn is its own model call in its own context, so activities need a way
  to run one.
- *In confidence* works by leaving those memories off the desk. That is reliable for the memory
  itself, but other memories can carry the same thing, especially episode recaps, which summarise
  whole conversations (see *Open*).
- Storage sketch: a correspondence holds two persona ids; each letter has its sender, whether it
  is sealed, the seal sticker, and when you opened it. `MemoryEntry` gains `inConfidence`.

## Open

- **Does a persona know when you open its sealed letter?** Proposed: yes. The seal shows broken to
  both personas, and the sender gets a short note, as with stickers.
- **The activity limit:** what goes first when more than three are due (proposed: writing up
  recent conversation, then letters waiting from other personas, then reflecting, revisiting and
  tidying, then new letters); whether deciding to write to you always keeps a slot; whether
  activities that miss out wait for the next heartbeat (proposed: yes).
- **Episode recaps on the desk:** proposed off at first, since they can repeat things in confidence
  and they are long.
- **Default ceilings:** 30,000 tokens a day and 120,000 a week?
