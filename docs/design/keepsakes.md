# Keepsakes: media in the memory book

Agreed 2026-11-06 in the design session. Builds on [`media.md`](media.md) (v0.3.5) and the v0.2
memory book. Mockup: the "Keepsakes" board on the "Media frames" design canvas
(https://claude.ai/artifact/YRJXX7GbfT5Xpj8szVE6LA).

## Today

Each day in the memory book gets a collage: a date stamp; one polaroid, the first picture from
that day's persona letters, whichever memory it belongs to (tapping it opens the letter); a line
quoted from the letter a memory came from; and paper pieces (label slips for facts, torn notes for
thoughts, taped pages for what happened), aged and burnable. Nothing else a letter carried shows,
and no clipping belongs to a particular memory.

## Decisions

- **A memory can keep attachments** from the letter or note it came from: "Sam made me a rainy day
  mix" keeps the cassette.
- **The persona chooses.** `memory_save` and `memory_update` gain an optional `keep`, naming
  attachments by the short handles the persona sees in its context (`[cassette: rainy day mix]`,
  `[photo: the window cat]`). If it names nothing, the source message's first attachment is kept,
  since weaker models will often skip the parameter.
- **Tapping a keepsake plays or opens it in place:** a song starts and the mini player docks at the
  bottom of the book, so you can keep scrolling; a cutting opens the page; photos and videos open
  their viewers. The memory card still links to the letter.
- **You can paste keepsakes into a memory** only with "Let me edit memories" on, like any other
  change to a persona's memories.
- **Stickers go on media too:** on frames in letters and notes (the mixtape first of all), and on
  keepsakes in the book.
- **Stickers stay stuck** (decided 2026-11-06, relayed from the build session): a sticker belongs
  to the attachment it's on, so media kept as a keepsake keeps its stickers, and one stuck on
  either the letter's frame or the book's keepsake shows on both.

## How each object looks in the book

The book holds what you'd actually glue into a scrapbook:

| In a letter | In the book |
| --- | --- |
| Polaroid | A smaller polaroid (as today) |
| A stack of photos | One polaroid with the edges of the others behind it |
| Luggage tag | The tag, tied through a punched hole in the page |
| Ticket stub | The stub, glued in; punched if you've played it |
| Cassette | Its J-card: the paper insert with the track list in the sender's hand |
| Newspaper cutting | A smaller cutting |
| Film strip | Two or three frames snipped from the strip |

## On the page

- **Calm while scrolling:** nothing moves. Reels don't turn and tags don't swing; motion only
  happens on a tap.
- **Compact:** keepsakes are about two-thirds of their size in letters, at most two a day, with a
  small "+2" leading to the rest on the memory cards.
- **Light:** each keepsake gets a small thumbnail when it's kept, so pages load quickly as they
  scroll in.
- **Ageing:** keepsakes age with their memory. Fading lightens and yellows them (newsprint
  fastest, then polaroids); dormant ones crease and lose a corner; replaced ones stay as history.
- **Letting go** burns the memory's keepsakes with it. The attachment itself stays in the letter.
- **Envelopes:** a closed month can show a ticket stub or the corner of a J-card peeking out among
  the notes.
- **Night:** lamplight applies, with the same setting.
- **A "Keepsakes" filter** shows only memories with something kept.

## The persona's side

When a memory with a keepsake is recalled, the persona gets the attachment's stored description,
not the file: "(kept with a ticket: October Walk by Hana Sato)". That keeps tokens down and tells
the persona what it pasted in.

## Building notes

- `MemoryEntry` gains a list of keepsakes, each a message id and an attachment id, plus its
  thumbnail ref. Memories are JSON rows, so this needs no migration.
- The day's picture (today's first-picture-of-the-day polaroid) stays only for days where no
  memory has a keepsake.
- **Sticker anchors** need an attachment as a target kind. A keepsake shows its attachment's
  stickers, placed at the same spot relative to the object (the cassette's stickers sit on its
  J-card), so a keepsake needs no target kind of its own.

## Later: a scrapbook of your own

You might want a book too: pasting photos, songs, cuttings and lines from letters into your own
scrapbook, with stickers. This is now part of a desk of your own, with your scrapbooks and
trinkets (see "Your desk" in [`activities.md`](activities.md)). Not designed yet.

## Open

- **When (settled):** shipped with media in v0.3.5 (see `docs/plan-v0.3.5.md` on the build branch).
- **Your scrapbook:** what it holds and where it lives.
