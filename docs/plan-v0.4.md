# v0.4 plan: the planner

Started 2026-11-06, after v0.3.5. The design is the design session's `docs/design/calendar.md`
(on its branch, `ccr-09dbf6b8-2c3jno`), with two passes of mockups on its "Planner" design canvas
(private claude.ai artifact: https://claude.ai/artifact/641ZZiMG4rVyRHyP1PDszb). The roadmap is
in its `docs/design/ideas.md`.

## Your answers on what was still open (2026-11-06)

- **Sharing:** as proposed. Every persona's "Sees your planner" starts on, for what you write in
  the app (phone calendars show nothing until set up); your default persona shares its planner.
  Each of your personas has a planner of its own, so a fictional one can have a fictional week; a
  letter sees the planner of whichever of your personas is writing it. Phone calendars belong to
  your default persona's planner.
- **Public holidays:** from Nager.Date by default (only the country and year are sent, once a
  year), and from a phone holiday calendar instead when you've set one up under Phone calendars.
- **Seasons:** a Look setting, "Seasons follow: the weather (default) or the solar terms", so the
  theme can agree with the planner's season band.
- **In as drafted:** "Busy for now" on a persona's notification; your typical week's sleep as the
  heartbeat's quiet hours; a haiku slip on a new solar term or microseason (a making activity).
- **Not in v0.4:** a suggestion using an activity slot. Suggested plans stay (they're agreed) and
  are free, like notes and reminders.
- **"Away", not "resting"** (after step 2): a persona marks itself away, and says in its own
  words what that is (resting, busy, on a trip). `calendar.md`'s "rest" means this.

## What v0.4 includes

From the agreed decisions in `calendar.md`:

1. **The 帳 Planner tab**, between 人 Personas and 設 Settings: a バーチカル (vertical) week of all
   24 hours with your sleep folded, a month page, a day page, and a plan's card.
2. **Your typical week** (work, sleep and the rest), printed faintly on every day.
3. **What the persona sees,** in each turn's context block (never the system prompt): what's on
   now and next, a few notable days ahead, its own notes and suggestions, and the states of
   entries. A heartbeat says why you're quiet instead of "They may be busy".
4. **Personas write in the planner:** notes, reminders and promises (which wake it), suggested
   plans you can add or turn down, and its time away; stickers on days and entries.
5. **Entries have states:** done, moved (with a ghost on the old day), cancelled; a suggestion
   added or not this time; a reminder kept or missed.
6. **Marks per persona:** its signature seal, wake mark and done mark, like the unread mark.
7. **Phone calendars,** optional and read-only (`CalendarContract`, one permission, no sign-in),
   each shown hidden, busy only, with titles, or with titles and places.
8. **Busy times:** notifications held until a busy entry ends (adjustable); "Busy for now".
9. **The season band** (solar terms and microseasons, computed on the phone; flipped for the
   southern hemisphere), public holidays, a 12-hour clock setting, the week's first day.
10. **Settings, Planner** (十 Planner after 九 Media; About moves to 十一).

## Order of work

1. **Engine:** planner entries (a new table: a migration step with its test), each of your
   personas' planner, the typical week, states, the sharing switches, and what the persona sees
   in a letter and a heartbeat.
2. **The 帳 tab:** the vertical week (dot grid, stitching, ribbon, index tabs, sleep folded), the
   day page, a plan's card, your typical week, stamping (done, move, cancel), the メモ area, the
   12-hour clock and the week's first day.
3. **The month and the seasons:** the month page, the season band, public holidays, "Seasons
   follow" on the Look page.
4. **The persona's side:** the planner tools (look, note, suggest, remind, away), slips in its
   ink with its seal, suggestions to add or turn down, reminders and one-off wakeups, its time away,
   its marks, stickers on days and entries, the look-ahead activity and the season haiku.
5. **Phone calendars:** setup, detail levels, pencil boxes, done and not going.
6. **Busy times and Settings, Planner:** held notifications, "Busy for now", sleep as quiet
   hours, and the Planner page.
7. **Polish, tests, an APK.**

## Progress

- **Step 1, the engine: done.** Planner entries are a new table (`planner_entries`, database
  version 5; its migration step has a test, and the last three steps are kept). Every one of
  your personas keeps a planner of its own; an entry knows whose planner it's in, who wrote it
  (you, or a persona), what it is (a plan, part of your typical week, a persona's note,
  suggestion, reminder or time away), when (a time, a day, or the days and hours of a week), where,
  whether it makes you busy, and how it stands (done, moved, cancelled; added or not this time;
  kept or missed). A persona's "Sees your planner" starts on; your default persona shares its
  planner, the others don't until you say. When both are on, each letter's context block says
  what's on now and next, to the end of tomorrow, then up to three notable days ahead, the
  persona's own notes, reminders and suggestions, and how your plans stand ("Sam's planner: at
  work until 17:30. Later today: dinner with Jo, 19:00 to 21:00. Tomorrow: work, 09:00 to 17:30;
  dentist, 10:00."). A heartbeat says why you're quiet instead of "They may be busy" ("Sam is
  asleep until 07:00. A letter written now will be read after that."), with busy stretches that
  run into one another taken as one. Deleting one of your personas takes its planner with it;
  deleting a persona takes what it wrote. Nothing shows in the app yet: that's step 2.
- **Step 2, the 帳 tab: done** (bar the week's first day, which comes with Settings, Planner in
  step 6). 帳 Planner sits between 人 Personas and 設 Settings; the four tabs share the bar
  equally. The week is a バーチカル page on dot-grid paper, stitched down the left, with the
  ribbon in your ink (back to today) and a 週 Week index tab; the month's traditional name heads
  it (霜月, "frost month · 2–8"), with arrows to the weeks either side. The days (kanji, Saturday
  blue, Sunday red, today ringed) and the all-day row stay put while every hour scrolls below;
  it opens at the current hour. The hours you're asleep on every day shown fold to a thin band
  ("12:00 am–7:00 am asleep"; a night shift's fold sits mid-column), and a tap opens them. Your
  typical week is printed faintly (WORK), your plans are highlighter in your ink, two at once
  share their day, and a line marks now. A plan's tap brings its stamps: 済 Done, 延 Move (the
  seven days lined up with the columns; a ghost stays where it was, with 延 and the day it went
  to), 止 Cancel, and its card; stamped, they lift. Below the grid, メモ: the week's plans with no
  time, which stamp done with a tap. A day's page lists everything on it in time order; "Add a
  plan" (or one tapped) opens a plan's card, sliding up: what, the day, at a time, any time or
  all day, from and to (the phone's time picker), where, busy, and every week (which makes it
  part of your typical week). Your typical week has its own page: work, sleep or something else,
  which days, from and to, busy. Settings, Look has a clock: the phone's, 12-hour or 24-hour.
  The grid's small type grows with large text only as far as words still fit a column.
- **After 0.4.0-preview.1 (your notes from the phone):** the ribbon sat under the status bar,
  where a tap didn't reach it; it hangs below it now, with a bigger place to tap. The arrows are
  paper buttons under the title instead: "‹ Week before", "Today" (pressed while you're on this
  week) and "Week after ›". Type keeps off the ruled lines where it can: an hour's label sits
  just under its line, plans have paper under their highlighter (so no line runs through a
  title), WORK has a paper chip, the line for now runs under plans, the all-day row is taller,
  and the day page's times stay on one line. **Stickers on the planner,** from step 4 for your
  side: a Stickers button on the week (onto today, or the week's first day) and on a day's page
  opens the tray (recent, shared, emoji); a tap sticks one onto that day, a hold and drag onto
  any day or plan. Tap one to move, turn, resize or peel it, as on a letter. They're kept with
  the planner, not a letter, and a day holds twice a letter's stickers; a plan's go when it's
  deleted, and a planner's when that persona of yours is. A persona's stickers on days and plans
  are still step 4.
- **After 0.4.0-preview.2:** the ghost a moved plan left behind looked like a mistake, so it's
  gone: a moved plan carries its 延 seal to its new day (moved back, it's just a plan again), and
  the seals sit over a plan's bottom corner rather than over its title. A plan takes taps a little
  above and below itself (40dp at least), so a near miss no longer opens its day. Any of your
  stickers, on letters and the planner, can be held and dragged to move it without selecting it
  (a tap still selects it, to peel, turn or resize).
- **Step 3, the month and the seasons: done.** The season engine: the 24 solar terms
  and 72 microseasons are worked out on the phone from the sun's position (no network, no data
  file), in the phone's own time zone (so the December solstice is the 22nd in Tokyo and the 21st
  in New York), and turned half a year round south of the equator. Your side of the equator comes
  from your saved location, else your time zone, and can be set. The theme's seasons can follow
  the weather (as before, now flipped in the south without a saved location too) or the solar
  terms. A letter is told the season once as each microseason begins ("Season: 立冬 (winter
  begins), camellias open."), with time awareness on. Public holidays come from Nager.Date once
  you choose whose (only the country and year are sent; this year's and next year's lists are
  kept, and fetched again after three months): public ones only, nationwide or your region's. A
  persona that sees your planner is told of the week's ("Thursday is a public holiday:
  Thanksgiving.").

  On the pages: the 週 Week and 月 Month index tabs at the right edge switch between the week and
  the **month page**, which has the month's traditional name (霜月, "November 2026 · frost
  month"), the same buttons ("Month before", "Today", "Month after") and kanji month tabs (十月,
  十一月, 十二月). Its band names the month's solar terms ("7日 立冬 winter begins | 22日 小雪 light
  snow"); each day shows its date (Saturday blue, Sunday and holidays red, today ringed), a term's
  name on its first day, a holiday's name or its first plan, ink dots for who wrote there (yours
  now; personas' with step 4), 済 when everything on it is stamped, and its stickers (smaller
  than on the week). A legend says what the marks mean, and "Show public holidays" leads to their
  setup until there are some. The week has the **season band**: this microseason and the next
  term, each with a motif (plum, cherry, hydrangea, morning glory, the moon over pampas grass,
  maple, camellia, the snow ring), opening in full with a tap ("霜降 末候 楓蔦黄: Frost falls, the
  last microseason: maples and ivy turn yellow. 2 to 6 November."). A holiday's date is red on
  the week, with its name in the all-day row, where a term's first day has its kanji too; a
  day's page says its holiday, term and microseason. The week's Stickers button moved up beside
  the title, clear of the index tabs.

  **Settings, 十 Planner** (About moves to 十一) came forward from step 6 to hold these: your
  typical week, the week page (fold my sleep hours, the week starts on Monday or Sunday, the
  clock), seasons and special days (the band, your side of the equator), and public holidays
  (whose: a country, suggested from the phone's region, and a region where they differ;
  "Change"). Look has **Seasons**: what the theme's seasons follow, and your side of the equator.
  Busy times, quiet hours and phone calendars join the Planner page in their steps.

  Chosen along the way (say if you'd rather otherwise): terms and microseasons fall on the day
  they begin in your own time zone, not Japan's (so in the US a term can begin a day earlier
  than a Japanese diary shows); public holidays stay off until you choose whose, since turning
  them on sends a request to Nager.Date.

## Noted for later

- **Explaining the utility model in setup:** what it's for, with examples, so you know you can
  pair models (a Gemini utility model listens to songs and mixes and describes them for a
  Claude-powered persona that can't hear).
