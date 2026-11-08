# The planner (v0.4)

The first decisions were agreed 2026-11-06. The same night the design session expanded them into
this spec while the user was away: everything marked **proposed** is the design session's call,
waiting for the user to confirm. Mockups: the "Planner" design canvas (private claude.ai artifact,
linked under *Mockups*). Quoted app text follows the app's American spelling.

## Why

Aria, the test persona, asked for "a gentle way to know when a conversation has gone quiet on
purpose, so I don't mistake a busy afternoon for being ignored."

Today a persona only learns how long it has been, never why: each turn's context says "Time since
the previous message: 5 hours", and a heartbeat says "Sam last wrote 5 hours ago" and "They may be
busy". A planner answers the why without the user having to say they'll be busy before they are.
It is also the other half of the memory book: the memory book looks back, the planner looks ahead.

## Decisions (agreed)

- **In-app planner by default.** The planner holds the user's own entries, including a typical
  week (work hours, sleep), so "at work" works with nothing synced. The phone's calendars are not
  read, and no calendar permission is asked for, until the user sets up calendar integration.
- **Phone calendars are optional and read-only.** Android's calendar store (`CalendarContract`)
  already holds whatever syncs to the phone: Google Calendar, Samsung Calendar, Exchange, Outlook
  (with its calendar sync on) and CalDAV (through DAVx5). One integration covers them all, with one
  permission (`READ_CALENDAR`), no sign-in and no network. Nothing is written back.
- **The user chooses what is shown, per calendar, when setting it up:** hidden, busy only ("busy
  until 17:30"), titles, or titles with location. Attendees and event notes are never sent. Setup
  says plainly that whatever is shown goes to the persona's model provider.
- **Sharing is per persona, both ways:** each persona has a switch for whether it sees the
  planner, and each of your personas has a switch for whether it shares one (user personas can be
  fictional; Sam is a test user). The planner reaches a letter only when both are on.
- **The persona chooses whether to write during a busy period.** It is told the user is busy and
  until when, and that a letter written now will probably be read later. It may write anyway or
  stay quiet.
- **Notifications are held during known busy periods** by default: the letter lands in the chat,
  and its notification is shown when the period ends. Adjustable in settings.
- **Personas write in the planner:** notes and reminders on dates, in their own ink and visible to
  the user, and suggested plans ("meteor shower Saturday?") that show up as entries.
- **Stickers in the planner**, placed by the user and by personas, as on letters.
- **A tab of its own: 帳 Planner** (帳 as in 手帳, the Japanese pocket planner), between 人
  Personas and 設 Settings (agreed after the first mockups).

Agreed after the second pass (see *Second pass* below for the details):

- **The week page is a バーチカル (vertical) week**, replacing the first pass's list.
- **The week page covers the whole day**, so it works for night shifts (how: proposed below).
- **A 12-hour clock** as a setting.
- **Entries have states, including done** (which states: proposed below).
- **Marks are set per persona:** like the unread mark (on the build branch), each persona's done
  and wake marks can be changed.
- **Public holidays are shown**, from a country chosen once in a one-time setup, with a switch on
  the Planner settings page.
- **The ribbon is in your persona's ink**, and wider than first drawn.
- **More tape patterns, including hearts** (see [`ideas.md`](ideas.md)): the nine on the pieces
  board for this pass, with more to come later.
- **Local seasons mean the hemisphere, for now:** south of the equator the solar terms (and the
  theme's seasons) shift by six months. Local weather comes later and separately (see "Weather and
  places" in [`ideas.md`](ideas.md)).
- **Holidays don't change your typical week:** the work band stays on a holiday, since few
  holidays are days off for everyone. Marking a day off is up to you.
- **The month page doesn't list the microseasons:** their names mean little to English readers,
  so the line under the band was taken out.

## What's in the planner (proposed)

Everyone's writing is in one planner, in their own ink, like a shared paper diary.

| What | Written by | Looks like |
| --- | --- | --- |
| A plan | You | A line in your ink with a bullet-journal mark: ○ for an event at a time, • for anything else |
| Your typical week | You | Faint printed bands on each day ("WORK 9–17:30"), like a timetable printed in the diary |
| A phone calendar event | The phone | Written in pencil (graphite grey, read-only), at the detail level its calendar shares |
| A note | A persona | A small slip tucked into the day with a piece of its tape, in its handwriting and ink |
| A suggested plan | A persona | A slip with a dashed edge and two buttons: "Add to my planner" and "Not this time" |
| A reminder or promise | A persona | A note with a small clock mark ("I'll write after your interview") |
| Its rest | A persona | A resting sign across the days it rests ("Juno is resting until Sunday") |
| A sticker | Anyone | On a day or on an entry |

- **Busy:** an entry can be marked busy. Typical-week work and sleep are busy; phone events follow
  their own free or busy setting; a plan asks with a tick box (on for timed plans by default). Busy
  entries carry a short hatched mark in the margin.
- **Accepting a suggestion** makes it your plan, in your ink, with the persona's slip kept beside
  it. "Not this time" crosses the slip through gently and tells the persona on its next turn.
- **The persona's rest** comes from the Fieldwork idea in [`ideas.md`](ideas.md): a persona can
  set its own quiet time, and heartbeats leave it alone until then. Letters from you still reach it.
- **Promises and reminders with dates** come from the same list: things a persona chose to do on a
  day. They show in the planner and wake the persona when they're due.

## Screens (proposed)

- **The week** (the tab's first page): the seven days down the page, each a ruled section with its
  entries in time order. Today has a strip of your tape down its edge. A switch at the top goes
  between Week and Month; arrows go between weeks.
- **The month:** a grid for looking further ahead. Each day shows small ink marks for who has
  written there; tap a day to open it.
- **A day:** its entries in time order, the typical-week bands, "+ Add a plan", and the personas'
  slips.
- **A plan's card:** index-card fields for what, when (all day, or from and to), where and a note;
  a "Busy" tick box; "Every week" to make it part of your typical week.
- **Whose writing:** a filter at the top shows everyone or one persona. A persona's page links to
  its own view ("Aria's planner").

## What the persona sees (proposed)

All of this goes in each turn's `<context>` block, never the system prompt: the context block is
stored on the message when it's sent, so later requests replay the same bytes, whereas in the
system prompt every planner change would start a new epoch and lose the prompt cache.

- **For a letter:** what's happening now and what's next, from now to the end of tomorrow, plus up
  to three notable entries in the coming week (all-day events, and anything the persona wrote
  about). For example, at "titles": "Sam's planner: at work until 17:30. Later today: dinner with
  Jo, 19:00. Tomorrow: dentist, 10:00." At "busy only": "Sam's planner: busy until 17:30. Later
  today: busy 19:00 to 21:00."
- **Its own writing:** "Your notes: ask how the dentist went (Tuesday). You suggested: the meteor
  shower, Saturday 22:00 (not answered yet)."
- **In a heartbeat:** "They may be busy" becomes what the planner says, and during a busy period:
  "Sam is at work until 17:30. A letter written now will be read after that."

## Tools (proposed)

| Tool | Does |
| --- | --- |
| `planner_look(from, to)` | Lists entries in a range, at each calendar's detail level |
| `planner_note(day, text, time?)` | Writes a note on a day |
| `planner_suggest(day, title, time?, note?)` | Suggests a plan you can accept |
| `planner_remind(at or after, text)` | A reminder or promise; wakes the persona then |
| `planner_rest(until, note?)` | The persona's own rest |

Stickers use the v0.3 sticker tool, with planner targets. Where the model supports it, these tools
are added mid-conversation instead of starting a new epoch (see "Keeping tool tokens down" in
[`ideas.md`](ideas.md)).

## Heartbeat (proposed)

- **A "look ahead" activity** puts upcoming entries in front of the persona. Notes and reminders
  are free; a suggestion uses an activity slot, since it asks something of you. In v0.4 it runs in
  the private moment like today's activities; from v0.6 it moves to the writing desk
  ([`activities.md`](activities.md)).
- **One-off wakeups:** when a persona asks to be woken after an entry, or a reminder comes due, the
  app schedules a one-time job for then (WorkManager `setInitialDelay`; best effort, like the
  heartbeat). The wakeup is a normal heartbeat moment, within the usual limits.
- **Sleep as quiet hours:** a setting uses the typical week's sleep as the heartbeat's quiet hours,
  so they're set in one place.

## Notifications (proposed)

- **During a busy entry**, a persona's letter lands in the chat and its notification waits. When
  the entry ends, one notification per persona: "Aria wrote while you were at work". An all-day
  busy entry ends at your typical wake time the next day, or midnight without one.
- **Setting, "During busy times":** "Hold notifications" (default), "Notify anyway", "Don't
  notify".
- **Quick busy:** a persona's notification has a "Busy for now" action with a few lengths (an hour,
  until tonight, until tomorrow). It adds a busy entry without opening the app, for busy stretches
  that aren't in any calendar. This was a parked idea; it fits here.

## Phone calendars (proposed details)

- **Setup:** Settings, Planner, "Phone calendars": a short explanation, then the permission prompt,
  then each of the phone's calendars (its account, name and color) with the four detail levels as
  boxed cells. Nothing is preselected beyond "Hidden" until the user picks.
- **Read live, never copied:** events are read from `CalendarContract.Instances` (repeats already
  expanded) when the planner is shown or a context block is built. The app keeps no copy of event
  titles; only what was sent stays, inside the message's context block.
- **Free or busy** comes from each event's availability (busy, free, tentative; tentative counts
  as busy).
- **Time zones:** everything is in the phone's current zone.

## Sharing defaults (proposed)

- **A persona's "Sees your planner" switch** is on by default for your own entries: you wrote them
  in the app's planner, which is for sharing. Phone calendars show nothing until set up.
- **Your persona's "Shares my planner" switch** is on for your first persona and off for the
  others, which may be fictional.

## Settings: a Planner page (proposed)

十 Planner, after 九 Media:

- Phone calendars (setup, and each calendar's detail level)
- Your typical week
- During busy times (notifications)
- Use my sleep as quiet hours
- The week starts on Monday or Sunday
- Seasons and special days in the planner
- Fold my sleep hours (the week page)
- Clock: the phone's setting, 12-hour or 24-hour (app-wide, so also on the Look page)
- Public holidays: on or off, and whose

## Stickers

v0.3's sticker targets (`StickerTarget(kind, id)`, kinds `MESSAGE` and `DRAFT`) gain a day and a
planner entry. A sticker placed on an entry stays with it when it moves to another day. Persona
notes stay the same: "[Sam stuck :star: on Saturday's meteor shower]".

## Storage sketch

- **Planner entries**, a new table (a migration step): id; owner (you, or a persona's id); kind
  (plan, routine, note, suggestion, reminder, rest); title; start and end, or all day; place;
  note; busy; weekly repeat (routines); state (see *States* below); the day it was moved from,
  for the ghost; the message it came from; created and updated times.
- **Phone events** aren't stored (see above). Your stamps on them (done, not going) are a small
  table keyed by the calendar's event id and the instance's start time.
- **Public holidays:** the year's list for the chosen country, kept until the next year's fetch.
- **Held notifications:** a small queue keyed by persona, posted when the busy entry ends.

## Mockups

The "Planner" design canvas (private claude.ai artifact:
https://claude.ai/artifact/641ZZiMG4rVyRHyP1PDszb), drawn 2026-11-06:

- **The week** (interactive): 2 to 8 November with today marked; typical-week bands; phone events
  in pencil with busy marks; your plans; Aria's note, reminder and a suggestion you can accept or
  turn down; Juno's rest; a sticker; the whose-writing filter; the 帳 tab.
- **A day** (interactive): Friday's entries in time order, and "Add a plan" opening a plan's card
  (what, when, where, busy, every week) that adds the plan when saved.
- **The month:** November with ink marks for who wrote on each day, Juno's rest, a sticker, a
  legend and what's coming up. Today opens the day.
- **Planner settings** (interactive): phone calendars with the four detail levels, your typical
  week, during busy times, who sees your planner, sleep as quiet hours, seasons, week start.
- **Notifications:** the held notification when a busy entry ends, and "Busy for now" with its
  lengths.

Second pass (see below), on the same canvas under "Second pass: stationery and Japanese
aesthetics", revised after the user's notes:

- **The week, vertical** (interactive): a バーチカル week with index tabs for Week and Month, the
  ribbon in your ink and the one-line season band (tap it for more); all 24 hours with sleep
  folded (tap a fold to open it, tap the shaded hours to fold them); work bands, pencil boxes,
  highlighted plans, Aria's wake mark at 16:00 and a 付箋 on Saturday. Tap an entry for its stamps
  (done, move, cancel); moving leaves a ghost. Below the grid, tasks that stamp done with one tap,
  Aria's slip, and her suggestion (accepting puts the meteor shower on Saturday).
- **The month, frost month:** 霜月 with the same tabs and ribbon, kanji month tabs, a one-line
  band, Saturday and Sunday colours, public holidays in red (US as the
  example), ink dots, 済 on finished days, Juno's rest, a sticker and Aria's haiku slip.
- **Aria's marks** (interactive): her unread, signature, wake and done marks, each changed from
  seals or her stickers, with a preview of where each shows.
- **Pieces of the planner:** seals, slips in the four seasons, nine tape patterns, how each kind
  of entry is drawn, the states, motifs for the season band, and the ribbon and binding.
- **Planner settings** (interactive, updated): the week page (fold my sleep hours, clock) and
  public holidays with their one-time setup.

Third pass (see below), on the same canvas under "Third pass: calmer headers (November)", drawn
2026-11-08 in the agreed fonts and soft shadows:

- **The week: A, printed; B, on a label; C, running head** (all interactive): three headers on the
  same week, drawn first; A and C keep the first proposal's season band and arrows. B has the agreed
  header: the label, the navigation in words, Week circled in ink, the hand-ruled line with its
  loop, and the all-day bar's perforated edge; busy hours in a wash and a bracket, and the entries
  in Sam's hand (pencilled phone events, your plans in highlighter). Aria's suggestion is a sticky
  tab on Saturday and a sticky note below the grid: "Not this time" peels both off, "Put it back"
  restores them, and "Add it" puts the meteor shower on the grid. Going to another week changes the dates in the column
  heads and lengthens the ribbon; the mockup has plans for this week only.
- **Pencil and highlighter:** the week's entries in Sam's hand: before (printed and Literata), a
  sketched box (on the week), graphite shading and a dotted outline.
- **Busy times:** six ways to show the typical week's busy hours (work, 9:00 to 17:30) under the
  same plans: the hatched rail the week has now, pencil shading across the day, a bracket with a tick
  at each end, a strip of plain masking tape, a pale wash fading in and out, and a dotted line.
- **Header pieces** (interactive): four Week and Month switches, the two endings for the rule, and
  three edges for the all-day bar, with the picks marked.
- **The month, header B** (interactive): November with Month circled, "‹ October · December ›",
  the solar terms' line with their motifs, kanji weekdays over the hand-ruled line, your plans'
  names in highlighter, taller cells, Juno away, the legend and Aria's haiku
  slip.
- **A day, header B** (interactive): Friday as a list in time order, with "How did it go?" under the
  interview (済 Done or 止 Not going, and Lift to undo), the now line, Aria's slip and who can see
  the planner at the foot.

## Second pass: stationery and Japanese aesthetics

Asked for after the first mockups (2026-11-06): push the planner further towards stationery and
Japanese diaries. The user's notes on it are in *Decisions*; the rest here is proposed.

### The page

- **The week as a バーチカル (vertical) page** (agreed), the usual layout of Japanese 手帳: seven
  narrow columns with the hours down the side, so a busy stretch shows as a length of the day
  rather than a line in a list. Columns are about 48dp, so titles are cut to a word or two; the
  full title is in each entry's spoken label. Tapping an entry opens its stamps (see *States*);
  tapping an empty part of a column opens the day. Below the grid, a メモ area holds the week's
  untimed plans and the personas' slips.
- **All 24 hours, with sleep folded.** The grid always has every hour. The hours your typical week
  has you asleep, on every day of the week shown, fold to a thin band ("0:00–7:00 asleep"); tap it
  to unfold, and tap the shaded hours to fold them again. The page opens at the current hour. For a
  night shift (asleep 8:00 to 15:00), the fold sits mid-column and work runs across midnight into
  the next day's column, as it would in a paper diary. Setting: "Fold my sleep hours", on by
  default. The alternative, a day starting at an hour you choose, makes every column straddle two
  dates; folding avoids that.
- **Clock:** "Phone's setting" (default), "12-hour" or "24-hour". It applies everywhere in the
  app, so it lives on the Look page and is repeated on the Planner page. On the week page, 12-hour
  labels read 7a to 11p.
- **Paper:** a faint dot grid (方眼 style); the pages stitched down the left edge (a dashed thread
  line and a shadow in the gutter); a ribbon (栞) in your persona's ink hanging from the top edge,
  which goes back to today. The user widened it to about 33dp; it has a faint grosgrain texture.
- **Week and Month are index tabs** at the top of the page's right edge (週 Week, 月 Month), which
  frees the header (the user asked for a smaller switch in a neater spot). On the month page the
  kanji month tabs (十月, 十一月, 十二月) sit below them.
- **Dates:** kanji weekdays (月火水木金土日), with the English underneath on the month page;
  Saturday in blue, Sunday and public holidays in red, as in Japanese diaries. Today is ringed in
  your ink.
- **Month names:** the month page is titled with the traditional name (霜月, "frost month"), the
  English underneath; the week page shows it beside the month's number.

### The season band

- **One thin line** (the user thinned it): the current microseason and the next solar term, each
  with a small motif: "楓蔦黄 Maples and ivy turn yellow | 立冬 winter, 7th". Tap it for both in
  full: "霜降 末候 楓蔦黄: Frost falls, last microseason: maples and ivy turn yellow. 2 to 6
  November." The band uses the 二十四節気 (24 solar terms) and 七十二候 (72 microseasons, about
  five days each).
- **The month page** shows the month's two solar terms on one line; a term's first day carries
  its name in small beni kanji (立冬 on the 7th, 小雪 on the 22nd). It doesn't list the
  microseasons (decided).
- **Computed on the phone** from the sun's position, so no network and no data file. In 2026, 立冬
  falls on 7 November and 小雪 on 22 November
  ([1](https://braindetox.kr/ja/posts/ipdong_2026_date_meaning.html),
  [2](https://allabout.co.jp/gm/gc/471422/),
  [3](https://can-chan.com/LDG/72_getSolarTerm.php)).
- **Motifs:** one per solar term (the mockup draws seven: plum, cherry, morning glory, moon with
  susuki grass, maple, camellia, snow ring). Placeholders, like the app's other drawings.
- **This is the "Seasons and special days" setting** on the Planner page: the band can be turned
  off.
- **The southern hemisphere** (decided): the solar terms shift by six months (立春 in early
  August), and the microseasons with them, so "camellias open" comes in late autumn there too. The
  same calculation, flipped. Proposed: the hemisphere comes from the phone's time zone, with a
  setting to correct it.

### Who wrote what

These replace the looks in the table above where they differ:

| What | Looks like |
| --- | --- |
| A phone calendar event | A pencil box (as before) |
| Your plan | Highlighter in your ink, with slightly rough edges, on the vertical week; a line in your ink in lists and on the day page |
| A persona's note | A 一筆箋 (one-brush slip): a narrow ruled slip whose motif follows the season (ume, asagao, momiji, tsubaki), signed with the persona's signature seal |
| A suggestion | A 付箋 sticky tab in the persona's ink: a small tab on the grid at its time ("Meteors?"), and the full tab with its two buttons below the grid |
| A reminder | The persona's wake mark on the grid at the time it will wake, instead of the clock mark |
| Done, moved, cancelled | Stamps (see *States*) |

### Marks

Agreed: marks are set per persona, like the unread mark. Proposed details ("Aria's marks" board):

| Mark | Shows | Can be | Default |
| --- | --- | --- | --- |
| Signature | Signs its slips and notes | A seal: one character it picks (Aria 歌 "song", Juno 月 "moon") | Picked by the persona when the planner is first on for it |
| Unread | On a letter you haven't opened (on the build branch) | A seal, an emoji or one of its stickers | Its "hello" sticker, or the 新 seal |
| Wakes | On the planner where it means to wake and write | A seal, an emoji or one of its stickers | Its signature seal |
| Done | On a reminder or promise once it's kept | A seal, an emoji or one of its stickers | 済 in its ink |

- **Who sets them:** you, on the persona's sticker page; and the persona with its sticker tool.
- **Your persona has a done mark too** (済 by default), set on its page; moved (延) and cancelled
  (止) are fixed stamps in your ink.
- **Seals are stamps:** inked into the paper with no die-cut outline, like stamp stickers in v0.3;
  stickers keep the outline.
- **A date stamp (日付印)** with the month's name, the date and your persona's name is drawn on the
  pieces board but not used yet; it could replace the memory book's date stamp.

### States

Agreed: entries have states, including done. Proposed set:

| Entry | States |
| --- | --- |
| Your plan | Planned; done (your done mark); moved (it goes to the new day, and a ghost stays at the old place with 延 and an arrow to the new day); cancelled (crossed through, 止) |
| An untimed plan | The same as a plan |
| A phone event | As the calendar has it; done; not going. Both are kept in Fumibako only and nothing is written back; moving or deleting happens in the calendar app |
| A persona's suggestion | Waiting; added (your plan now, with its slip beside it); not this time (peeled off the page; see the third pass) |
| A persona's reminder or promise | Waiting (its wake mark at the time); kept (its done mark, when it writes); missed (the wakeup didn't run in time, and it's told when it next wakes) |
| A note, a rest | No states; a rest just ends |

- **Stamping on the week:** tap an entry for a small tray: 済 Done, 延 Move, 止 Cancel ("Not
  going" for phone events) and Open. Move shows the seven days as a strip lined up with the
  columns; the plan keeps its time, which can be changed on its card. On a stamped entry the tray
  offers Lift or Restore instead. Stamps land with a small stamping motion and a light haptic tick.
- **Untimed plans** in the メモ area stamp done with one tap; a long press opens the tray.
- **The month page** shows a small 済 on a day where everything is stamped.
- **Personas see states** in their context: "Pottery (Monday): moved to Thursday. Dentist: done."
  and for their own entries, "Your reminder after the interview: kept."

### Public holidays

Agreed: shown, from a country chosen in a one-time setup, with a switch on the Planner page.
Proposed details:

- **On the page:** the date in red like a Sunday, with the holiday's name small on the day.
  Personas see them ("Thursday is a public holiday: Thanksgiving").
- **Setup:** turning the switch on asks whose holidays, suggesting the phone's region, and a region
  within the country where it matters (a US state, a German Land). "Change" reopens it.
- **Source:** [Nager.Date](https://github.com/nager/Nager.Date), a free API with no key covering
  100+ countries and their first-level regions ([API](https://date.nager.at/de/Api)), fetched once
  a year and kept on the phone; only the country and year are sent. Alternatives: a holiday
  calendar from the phone (Google Calendar offers them, and one already appears under Phone
  calendars), or a bundled offline library such as
  [Jollyday](https://jollyday.sourceforge.net/) (its current fork lists 100+ countries;
  check its size and Android support first).
- **Not days off** (decided): a holiday leaves your typical week alone, work band included.

### The persona's side

- **A haiku on a new season:** on the first day of a solar term or microseason, a persona may write
  a haiku on a slip in the planner ("maple and ivy / letting go of their summer / one leaf at a
  time", signed 歌, "Aria, on 楓蔦黄, 2 Nov"). It's a making activity, so it uses an activity slot,
  and it can be switched off per persona like the others.
- **Context:** the band reaches the persona as one line, "Season: 立冬 (winter begins), camellias
  open", only when it changes, so it doesn't add tokens to every turn.

### Notes

- **Lamplight** applies to slips, seals and tape at night, as to other objects on the desk.
- **Fonts:** the brush face for kanji (Yuji Syuku) is a placeholder, like the app's other fonts.
- **Kanji are always paired with English** on screen or in the spoken label, so nothing depends on
  reading Japanese.
- **Small targets:** the folds, tabs and the band are thinner than 48dp to keep the page calm;
  each needs a 48dp touch area around it.

## Third pass: calmer headers (proposed)

Asked for 2026-11-08: the user isn't a fan of the planner's headers, finding them too big, too
busy and hard to read, and not right in look. The scope is the whole planner (week, month and day),
headers first. Everything here is proposed. The desktop layout session has been asked to hold off
on laying the planner out wide until this settles.

### One slim header

The first proposal, drawn as A. Where it differs, *Header B* below replaces it.

- **The same header on every planner page,** about 60dp tall (the week's was about 90dp): a plain
  title, and one line of small capitals under it.

  | Page | Title | Under it |
  | --- | --- | --- |
  | Week | "2 – 8 November" | "霜月 · WEEK 45 · THIS WEEK" |
  | Month | "November 2026" | "霜月 · FROST MONTH" |
  | Day | "6 November" | "金 · FRIDAY · TODAY" |

  The title is in Zen Old Mincho at 20 (the size of names), the line under it in Zen Kaku Gothic
  New at 11, as in the aligned type scale. The big month numeral, the brush kanji beside it and the
  italic range go.
- **The traditional month name stays,** smaller, at the start of the line under the title, with
  its English on the month page ("FROST MONTH"; October is "MONTH WITHOUT GODS", December
  "PRIESTS RUN"). A week across two months shows both: "神無月 · 霜月".
- **The arrows move into the header:** ‹ and › at its right end, for the week, month or day before
  and after. The ‹ 今 › tabs on the page's edge go.
- **The ribbon is the way back to today,** as it already was, so 今 isn't needed. Away from this
  week, month or day, the ribbon hangs longer, which shows you've moved and where to tap.
- **Week and month: one small button** in the header, naming the other view ("Month" on the week
  page, "Week" on the month and day pages). The 週/月 index tabs and the kanji month tabs on the
  right edge go, so the grid has the page's full width.
- **Stickers: a sheet's corner** curling up at the bottom right of the page, in place of the tab on
  the edge. Tap it for the sticker sheet.

### Three directions for the title

Drawn on the week page; the month and day pages use A, and would follow whichever is picked.

- **A, printed:** the title and the line under it printed on the paper, left-aligned. The plainest
  and easiest to read.
- **B, on a label** (picked): the dates on a strip of masking tape in your ink's light shade,
  slightly turned, like the v0.6 labels (see [`v0.5.md`](v0.5.md)). The line under it as in A.
- **C, running head:** centred, like the running head of a printed diary, with the arrows either
  side of the dates. The view switch is a small 月 tab at the left, balancing the ribbon on the
  right.

### Header B (agreed)

The user picked B (2026-11-08), edited it on the canvas and chose its pieces from the "Header
pieces" board. Agreed:

- **A label naming the month,** the traditional name and the English, on masking tape in your ink's
  light shade: "霜月 • November". A week across two months reads "神無月 • Oct – Nov"; the dates are
  in the column heads. The month page's label is the same ("師走 • December" for the next); the day
  page's has the weekday's kanji and the date ("金 • 6 November"). No line of small capitals under
  it.
- **Navigation in words,** on a line under the label: "‹ Week before · Week after ›" on the week
  page, the months' names on the month page ("‹ October · December ›") and the days' names on the
  day page ("‹ Thursday · Saturday ›"). No "This week" link: it made the line jump, and the ribbon
  hanging longer already says you've moved.
- **A Week and Month switch** at the right of the label: the view you're on is circled in ink, a
  quick loop drawn again when you switch. On the day page neither is circled, and both are ways
  back up.
- **The coloured rule** under the days (on the day page, under the header) is ruled by hand: a
  calligraphic stroke, heavier where the pen goes down and thinning along the page, finished with a
  small loop past Sunday.
- **The all-day bar** stays put while the hours scroll under it, until the end of the week's grid.
  It has a faint fill and ends in a perforated (dashed) edge, like the stitching down the page's
  left side.
- **No season band** on the week or the day page (the user took it off both). The month page keeps
  its line (the user asked to see it there): the month's two solar terms with their motifs, "7日
  立冬 winter begins | 22日 小雪 light snow", between the header and the weekdays, and each term's
  name in small beni kanji on its first day.
- The header is about 66dp: the label, and the line of words under it.

### The pages

- **Nothing smaller than 9.5** on any planner page (the month had text at 7.5).
- **Your entries in your hand** (asked for by the user): everything in your planner is written in
  your persona's handwriting font, as personas' slips are in theirs. In the mockups that's Sam's
  hand, the imported font from the v0.5 "Her hand" board (Caveat standing in for samhand.ttf, at
  1.2 times); Aria's slips stay in Yomogi.
  - **Your plans in ink, with a highlighter** in your ink: see-through, with streaks along the
    stroke, the ink a little darker where the pen starts and stops, and slanted ends from the
    chisel tip. Blocks on the week are stroked down the column; all-day plans and your plans' names
    on the month page are stroked across.
  - **Phone events pencilled in** (agreed): in graphite, in a box sketched with four quick strokes
    that run a little past the corners. The "Pencil and highlighter" board also has graphite shading
    and a dotted outline, and how they looked before (printed in a grey outline).
  - **No fill behind entries** (agreed): a pencilled box is only its strokes, and the highlighter
    is see-through, so an entry in busy hours sits on the wash, as it would on paper.
- **A persona's suggestion is a 付箋 sticky tab** in their ink, as in the second pass: a small tab on
  the grid at its time ("Meteors?", on Saturday at 22:00) with a coloured strip at its stuck end, and
  a matching sticky note below the grid with "Add it" and "Not this time". Adding it puts the plan on
  the grid in your hand and highlighter, and the note stays beside it ("ADDED TO YOUR PLANNER").
  **Declining peels them off** (asked for by the user): the bottom right corner folds up as a
  triangle, showing the paler back of the paper, then the note lifts away up and to the right; the
  tab on the grid does the same a moment later, leaving a faint mark where it was stuck that fades. A quiet line takes its place: "Taken down; Aria will hear it
  wasn't for this time", with "Put it back".
- **Busy hours: a wash and a bracket, together** (agreed): a thin line down the day's edge with a
  tick at each end, and a pale wash over the hours between that ends at the ticks rather than
  fading out. On the day page the bracket runs down the margin from "9:00 · WORK" to a "17:30 · WORK
  ENDS" row, with the wash behind the entries in between.
- **Week:** kanji weekdays with the date under each, today ringed in your ink. A solar term's first
  day has a small beni dot by its date, instead of the term's kanji squeezed into the column head.
  The away and all-day row, the grid and メモ are as agreed.
- **Month:** kanji weekdays alone, without the tiny English under
  them (the English is in each day's spoken label); cells about 64dp tall, so a plan's name, a
  holiday, an away strip and the ink dots fit without overlapping. The legend is at 11.
- **Day:** a list in time order: sleep folded at both ends, the typical week's work band, entries
  as cards, the now line in beni, and slips between the entries. An entry that has ended without a
  stamp shows its stamps under it ("How did it go?"); the stamp lands on its card, and "Lift the
  stamp" takes it off. "Aria and Juno can see your planner" moves from the header to a quiet line
  at the foot, with "Who sees what". The "Add a plan" card keeps its fields, in the new fonts.

## Open

- Everything marked **proposed** above, in particular: a suggestion using an activity slot while
  notes and reminders are free; the sharing defaults; quick busy from a notification; sleep as
  quiet hours.
- **The season band after the third pass:** gone from the week and day pages, kept on the month
  page. Whether the microseasons still reach the persona's context with nothing on screen.
- **The app's seasons and the solar terms:** the theme follows meteorological seasons (autumn until
  30 November), while the band says winter begins on 7 November. Either the theme follows the solar
  terms too (a Look setting; see [`ideas.md`](ideas.md)), or the band and theme disagree for a few
  weeks each season.
- **Holidays:** the source (Nager.Date or a bundled library).
- **Special days** beyond the solar terms (moon-viewing, New Year): which ones, and do personas get
  seasonal activities from them?

## Parked ideas

- **Mechanical stickers:** stickers the harness itself acts on, not only the persona (for example
  an "away" stamp that holds letters).
- **Do Not Disturb and Android's modes** as a busy signal. Check first whether an app can read
  them.
