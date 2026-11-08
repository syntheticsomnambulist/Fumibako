# v0.4 plan: the planner

Started 2026-11-06, after v0.3.5. The design is the design session's `docs/design/calendar.md`
(on its branch, `ccr-09dbf6b8-2c3jno`), with two passes of mockups on its "Planner" design canvas
(private claude.ai artifact, which only the repository's owner can open: https://claude.ai/artifact/641ZZiMG4rVyRHyP1PDszb). The roadmap is
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

- **After 0.4.0-preview.4:**
  - **Side tabs:** the ribbon and the kanji month tabs repeated "Before / Today / After", so the
    navigation moved to the side: ‹ Before, 今 Today and › After are tabs at the right edge,
    under 週 and 月, on both pages. The button row is gone, and the week's grid leaves room for
    the tabs.
  - **The ribbon** stays as a bookmark to look at, no longer a button (your answer, 2026-11-06),
    unless it finds another job.
  - **Big stickers:** a sticker grown big hid under the next day (or letter), handle and all.
    Now the day, plan or letter holding the sticker you've picked or are moving comes to the
    front.
- **Step 4, the persona's side: done.**
  - **Two tools,** not five (to keep the tool list short):
    - `planner_look` reads days further ahead than a letter's context shows.
    - `planner_write` writes a note, a suggestion, a reminder (at a time, or after one of your
      plans), its time away (in its own words), a haiku, or its seal, and erases what it
      wrote.
    They're offered to a persona that sees your planner, and only work when the planner of
    whoever it writes to is shared.
  - **Time away:** heartbeats leave the persona alone while it's away, except for its own
    reminders. Your letters still reach it.
  - **Reminders:** a reminder that comes due wakes the persona.
    - The app sets a one-off wakeup for its time; any heartbeat after that does too.
    - The persona is reminded of what it meant to do, and the minimum wait after a letter
      doesn't hold it back.
    - Writing keeps the reminder. One left waiting half a day (its wakeup never came) is
      missed, and the persona is told so.
  - **Two new heartbeat activities:**
    - **The look-ahead:** free, at most once a day. It puts your week before the persona, to
      leave a note, suggest something or set a reminder.
    - **The season haiku:** on a new microseason's first day it takes a slot, and it can be
      switched off.
  - **The seal:** a persona that hasn't picked a seal is asked to, once its planner is
    shared.
  - **On the pages:**
    - Notes and haiku are 一筆箋 slips in its ink with the season's motif, signed with its seal
      (the first letter of its name until it picks one).
    - Suggestions are dashed 付箋 tabs with "Add to my planner" (it becomes your plan, the slip
      kept beside it) and "Not this time". A small tab also sits on the grid at the
      suggestion's time.
    - Reminders show the persona's wake mark on the grid, with a line saying when it will wake
      and whether it kept or missed it.
    - Time away is a band across the days it's away, and a line in its words.
    - All of this goes in the week's メモ and on the day's page; the month shows dots in its ink.
  - **A persona's heartbeat page** has "The planner": "Sees my planner", and "Writes a haiku
    as each season begins".
  - **Marks:** a persona's stickers page has "Its marks in your planner": its seal (one
    character), where it means to wake (its seal by default) and a reminder kept (済). A mark is
    a character, an emoji or one of its stickers, and the persona can choose them too
    (`planner_write`, kinds `wake_mark` and `done_mark`).
  - **Stickers:** `place_sticker` can stick onto a day or one of your plans in the planner you
    share. Its stickers show there like yours. A letter is told of stickers you stick or peel in
    the planner since its last turn ("[Sam stuck :star: on Saturday in their planner]").
- **The ribbon is a movable bookmark** (your idea, 2026-11-06). A long press puts it on the
  week (or month) shown, and a tap goes back to that page from anywhere. It hangs on its page,
  and only its end shows elsewhere; until it's moved, it marks today.
- **Times of day follow the clock setting everywhere**, not only in the planner: letter stamps,
  sticker slips ("stuck fri 2:10 pm"), the letters list, and sunrise and sunset on the Look page.

- **After 0.4.0-preview.6:** there wasn't room for the side tabs' sideways words ("Mont",
  "Toda"), so the tabs show only their glyph (週, 月, ‹, 今, ›), with the words in their spoken
  labels; they're shorter too. The season bar is off every page (your call, 2026-11-06): a
  solar term's kanji still marks its first day on the week and the month, a day's page still
  names its term and microseason, and letters are still told each new one. The Settings switch
  is now "The solar terms and microseasons". The headers drop the months' translations ("frost
  month"): the week reads 霜月 2–8, the month 霜月, November 2026. The Stickers button left the
  header for a tab at the right edge, a little apart under ‹ 今 › (your choice).

- **Step 5, phone calendars: done.**
  - **Read-only:** through `CalendarContract`, with one permission and no sign-in. Read live
    (repeats come expanded) and never copied.
  - **Detail levels,** in Settings, Planner, Phone calendars: each calendar is hidden (the
    default), busy only, titles, or titles and places. The planner shows that much, and that's
    what a persona that sees it is told.
  - **Whose planner:** only your default persona's has them.
  - **On the pages:** events are drawn in pencil, graphite and read-only. You can stamp one
    done or not going; the stamps are kept in the settings for three months, since nothing is
    written back. A tap opens it in the phone's calendar app.
  - **Busy:** busy and tentative events keep you busy.
  - **Holidays:** a shown holiday calendar's days are your public holidays, in place of
    Nager.Date's.
- **Step 6, busy times: done.**
  - **During busy times** (Settings, Planner): while you're busy (work, sleep, a busy plan or
    phone event), a persona's heartbeat letter arrives quietly. When the stretch ends, one
    notification per persona says it wrote ("Aria wrote while you were at work"). The choices
    are hold (the default), notify anyway, and don't notify.
  - **Busy for now:** a persona's notification has "Busy for an hour", "Till tonight" and "Till
    tomorrow" (until you wake). Each adds a busy plan without opening the app.
  - **Use my sleep as quiet hours:** heartbeats don't write while your typical week has you
    asleep.
  - **Who sees your planner:** each persona's switch, and each of your personas' sharing, are
    on the Planner page.
  - **Not built:** an all-day busy entry doesn't hold notifications (only timed busy stretches
    do).

- **Step 7, polish, tests and the APK: done; v0.4.0** (version code 22).
  - **Docs:** the testers' guide has the planner (what to try, and what it sends where); the
    README has it in the features, the map and the heartbeat.
  - **Tests:** 222 unit tests, and the four screenshot tours, which visit:
    - the week (with the bookmark, stickers, stamps, a persona's slips, pencil events and a
      suggestion added);
    - the month;
    - a day;
    - the Planner settings with phone calendars;
    - a persona's marks.
  - **Fixed along the way:** a lazy-list crash at first launch, seen twice in the tours; three
    runs since were clean.
- **0.4.1 (version code 23), getting ready to go public:** a new debug signing key, with key
  rotation so builds still install over the old key's (v0.2.2 included) and keep their letters;
  the GPL-3.0-or-later license. See `docs/going-public.md`.
  - **The first 0.4.1 build wouldn't install** ("App not installed"): its lineage withheld the old
    key's "permission" right, which the update needs because AndroidX declares a signature
    permission. The lineage now keeps apksigner's default rights, all but rollback.
  - **Confirmed on your phone:** the fixed 0.4.1 installed over 0.4.0 and kept the letters.
- **0.4.2 (version code 24), after 0.4.1 (your notes from the phone):**
  - **The all-day row** has a line under it too, so the hours scrolling beneath it don't look cut
    off.
  - **An all-day plan** takes a tap for its stamps (済, 延, 止, its card), like a plan in the hours,
    and shows its stamp; a long press opens its card.
  - **The ribbon** runs up behind the status bar to the page's top edge; only its end takes taps.
  - **The week and month headers:** you're rethinking them on the design side.
  - **Tests:** the tour stamps an all-day plan done and lifts it again; the large-text tour now
    scrolls to the week page's settings, which sit below the screen at that size, rather than
    waiting for them to appear.
- **0.4.3 (version code 25), videos and YouTube for Gemini (your notes):** Pidge sent a 3:47 video
  as three frames and couldn't hear a YouTube ticket, though Gemini takes both. The reason was the
  share of the context one thing may take (a third of "Summarize after", so about 2:40 of video
  at the Gemini preset's 48,000), and a pasted link having no sound file.
  - **A video may take half** the budget (four minutes at the preset); other things keep a third.
  - **A longer one is watched as its first part:** a video over four minutes keeps a smaller copy
    of its first four (all that fits one request at its bitrate), and Gemini cuts a video to what
    fits (`videoMetadata` offsets), so the persona is told "(its first 4:00:)".
  - **YouTube links:** Gemini personas watch a YouTube cutting or ticket by its address (Gemini
    fetches it; Google calls this a preview), up to the same share. Its length is usually unknown,
    so Gemini is told to stop at that point. If Gemini refuses the request (a private video, say),
    it goes again once without the link. A watched link is described for later epochs like a video.
  - **The line under a frame says why:** "Aria sees three frames from the video; Claude can't watch
    video", "Pidge watches its first 4:00; the whole of it is too much to send at once", "Pidge
    watches up to its first 4:00, from YouTube", "…but a link brings no sound to send".
  - The model screen lists YouTube links among what Gemini takes in.
- **0.4.4 (version code 26), letters in the post (your idea):** a reply takes a while to reach
  you, to build some anticipation.
  - **Written at once, kept back:** the persona writes as soon as you send (so a bad key or a
    refusal shows straight away), and the reply and anything it did with tools carry a `deliverAt`
    and stay hidden until then. A second reply never arrives before the first.
  - **How long:** Settings, Heartbeat, "Letters in the post": straight away, minutes (2 to 3, the
    default), half an hour, hours (2 to 4), a day (20 to 28 hours), days (2 to 3), each at a
    random point in its range. A persona's heartbeat page can give it its own. Heartbeat letters
    come when they come.
  - **Meanwhile:** the thread shows a 〒 postmark ("Aria's reply is on its way") instead of the
    letter being written; with "Show when letters arrive", a little pocket watch says when. The
    letters list says it's on its way (not the reply's first words) and shows it unread only once
    it's come. An arrived letter is dated and placed by its arrival.
  - **When it comes:** a notification, as a heartbeat letter's (held the same way during busy
    times), unless you read it in the app as it came (WorkManager on the phone; the desktop's
    hook is a no-op for now).
- **0.4.5 (version code 27):** back (the phone's gesture, Escape on a computer) puts a letter's tray
  away before it leaves the letter: from the sticker or song page back to the tray, then the tray
  closed. The desktop session noticed it.

## Noted for later

- **Explaining the utility model in setup:** what it's for, with examples, so you know you can
  pair models (a Gemini utility model listens to songs and mixes and describes them for a
  Claude-powered persona that can't hear).
