# Decisions made while you were away

Overnight on 2026-11-10 you authorized the Architect session, Sashigane, to choose design details
so v0.5's work wouldn't stall. Each choice is listed here, newest last, for you to check. A choice
you'd make differently can be changed: tell Sashigane, and it will pass the change on to the
session that built it.

Each entry has what was asked, who asked, what was chosen and why, and where it landed. Choices
come from `docs/design/v0.5.md` (design branch) and your recorded answers. Anything outside that
authorization (sending a build to friends, a GitHub Actions build, signing, or changing a decision
you'd made) waits for you, and is listed at the end.

## Choices

None of my own yet. Calls other sessions made where the design doesn't settle a detail, for you
to check: Kanna's in v0.5's step 6 (fonts) and step 7 (the planner redrawn), and the desktop
installers session's in desktop step 6 (fonts on the computer).

### Fonts

1. **A Latin-only font file of yours** shows Japanese in the phone's own font, not in Yomogi.
   (In `main` 2026-11-10; `docs/plan-v0.5.md`, Progress, step 6.)
2. **The hand picker** draws only the chosen hand and Yomogi in their own faces; the other hands
   say "comes when chosen", so nothing downloads just from browsing. (Same.)

I looked at both and left them as they are. The second follows the design's "another hand
downloads when it's chosen" (`v0.5.md`, Her hand, Downloads). The design doesn't say what fills in
for a font of yours that has no Japanese; the phone's own font is Kanna's choice, and Yomogi
would be the other.

3. **On the computer, Literata and Old Standard TT keep the computer's own Japanese** (letters
   and cuttings), rather than Zen Old Mincho or Zen Antique behind them as the design has it
   (`v0.5.md`, the faces). The reason is technical: in Compose on the desktop, a font family of a
   Latin face plus a Japanese one doesn't fall back character by character (tried offscreen; the
   kanji came from the system font), which is the phone's behavior before Android 10. The other
   faces (names, interface, hands, seals) come whole and replace their Latin subsets, as on the
   phone. (In `main` 2026-11-10; `docs/plan-desktop.md`, step 6.)

I left this one too: the design's pairing can't be had this way, and the computer's own Japanese
face is a readable fallback. If you want the pairing, it would take drawing text in runs, by
script, which is a bigger change for later.

### The planner redrawn (Kanna, step 7; `docs/plan-v0.5.md`, Progress, step 7)

4. **The header is about 76dp tall**, not the board's 66, so the words and the Week and Month
   switch keep 34 to 36dp tap targets.
5. **The day page has no back arrow.** Week and Month go back up (to that view, with the day
   shown), and the system back works.
6. **A plan with no time, on the day page, works as in メモ:** a tap stamps it done, a long press
   opens it. Before, a tap opened it. *Worth your look:* a tap that stamps can be done by
   accident, though the stamp can be lifted.
7. **Under an ended entry,** the stamps are 済 Done and 止 "Didn't happen" for yours, and Done and
   "Not going" for the phone's events. 延 (move) stays on the week.
8. **"Put it back"** is offered only right after a suggestion is taken down, while the page is
   open; a suggestion declined earlier isn't shown again.
9. **The grid's titles in Yomogi are at 10,** not the board's 12, since Yomogi is wider than the
   mockup's Caveat; the floor of 9.5 holds.
10. **The highlighter is see-through by alpha,** not a multiply blend, so it reads the same on
    night paper.
11. **メモ keeps each plan's day** (WED, FRI) beside it, which the board doesn't show.

I'd keep all eight. Each either follows from a constraint (tap targets, a wider face, night
paper) or matches how the rest of the planner already behaves; number 6 is the one that changes
a habit.

## Waiting for you

None yet.
