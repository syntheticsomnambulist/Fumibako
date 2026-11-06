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
  are free; a suggestion uses an activity slot, since it asks something of you.
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

## Stickers

v0.3's sticker targets (`StickerTarget(kind, id)`, kinds `MESSAGE` and `DRAFT`) gain a day and a
planner entry. A sticker placed on an entry stays with it when it moves to another day. Persona
notes stay the same: "[Sam stuck :star: on Saturday's meteor shower]".

## Storage sketch

- **Planner entries**, a new table (a migration step): id; owner (you, or a persona's id); kind
  (plan, routine, note, suggestion, reminder, rest); title; start and end, or all day; place;
  note; busy; weekly repeat (routines); status (a suggestion's answer, a reminder done); the
  message it came from; created and updated times.
- **Phone events** aren't stored (see above).
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
aesthetics":

- **The week, vertical** (interactive): a バーチカル week with the season band, kanji weekdays,
  work bands, pencil boxes, highlighted plans, Aria's seal at 16:00 and a 付箋 on Saturday; tap a
  plan or task to stamp it 済; Aria's slip and suggestion below the grid (accepting puts the
  meteor shower on Saturday).
- **The month, frost month:** 霜月 with kanji month tabs, the month's sekki and kō, Saturday and
  Sunday colours, ink dots, 済 on finished days, Juno's rest, a sticker and Aria's haiku slip.
- **Pieces of the planner:** seals, slips in the four seasons, the five tape patterns, how each
  kind of entry is drawn, motifs for the season band, and the ribbon and binding.

## Second pass: stationery and Japanese aesthetics (proposed)

Asked for after the first mockups (2026-11-06): push the planner further towards stationery and
Japanese diaries. Everything here is proposed; it changes how things look, not what the planner
does.

### The page

- **The week as a バーチカル (vertical) page**, the usual layout of Japanese 手帳: seven narrow
  columns with the hours down the side (7 to 23 in the mockup), so a busy stretch shows as a length
  of the day rather than a line in a list. Columns are about 48dp, so titles are cut to a word or
  two; the full title is in each entry's spoken label, and tapping a column opens the day. Below
  the grid, a メモ area holds the week's untimed plans and the personas' slips.
- **Paper:** a faint dot grid (方眼 style) instead of plain paper; the pages stitched down the left
  edge (a dashed thread line and a shadow in the gutter); a beni ribbon (栞) hanging from the top
  edge, which goes back to today.
- **Dates:** kanji weekdays (月火水木金土日), with the English underneath on the month page;
  Saturday in blue and Sunday in red, as in Japanese diaries. Today is ringed in your ink.
- **Month names:** the month page is titled with the traditional name (霜月, "frost month"), the
  English underneath; the week page shows it beside the month's number.
- **Index tabs:** kanji month tabs down the month page's right edge (十月, 十一月, 十二月), the
  current one in your ink. They go to other months, like the arrows.

### The season band

- **Under the header, the current season and the next:** the 二十四節気 (24 solar terms) and the
  七十二候 (72 microseasons, about five days each), each with a small motif. On 6 November 2026:
  "霜降 楓蔦黄: Maples and ivy turn yellow, to the 6th" and "立冬 山茶始開: Winter begins,
  camellias open, from the 7th".
- **The month page** shows the month's two solar terms and lists its microseasons by start date;
  a term's first day carries its name in small beni kanji (立冬 on the 7th, 小雪 on the 22nd).
- **Computed on the phone** from the sun's position, so no network and no data file. In 2026, 立冬
  falls on 7 November and 小雪 on 22 November
  ([1](https://braindetox.kr/ja/posts/ipdong_2026_date_meaning.html),
  [2](https://allabout.co.jp/gm/gc/471422/),
  [3](https://can-chan.com/LDG/72_getSolarTerm.php)).
- **Motifs:** one per solar term (the mockup draws seven: plum, cherry, morning glory, moon with
  susuki grass, maple, camellia, snow ring). Placeholders, like the app's other drawings.
- **This is the "Seasons and special days" setting** on the Planner page: the band can be turned
  off.

### Who wrote what

These replace the looks in the table above where they differ:

| What | Looks like |
| --- | --- |
| A phone calendar event | A pencil box (as before) |
| Your plan | Highlighter in your ink, with slightly rough edges, on the vertical week; a line in your ink in lists and on the day page |
| A persona's note | A 一筆箋 (one-brush slip): a narrow ruled slip whose motif follows the season (ume, asagao, momiji, tsubaki), signed with the persona's seal |
| A suggestion | A 付箋 sticky tab in the persona's ink: a small tab on the grid at its time ("Meteors?"), and the full tab with its two buttons below the grid |
| A reminder | The persona's seal on the grid at the time it will wake, instead of the clock mark |
| Done | Your 済 stamp (see below) |

- **Seals (判子):** each persona has a one-character seal it picks for itself (Aria 歌, "song";
  Juno 月, "moon"), drawn in its ink with a slightly uneven stamped edge. It signs the persona's
  slips and marks its reminders. Proposed: the persona picks it when the planner is first turned on
  for it, and you can change it on its page.
- **Your 済 stamp:** tap a plan or an untimed plan to stamp it 済 ("done"), with a small stamping
  motion and a light haptic tick; tap again to lift it. On the month page a day where everything
  is stamped gets a small 済. This adds "done" to your plans: the storage sketch's status field
  covers it. Personas see it in their context ("the library books: done").
- **A date stamp (日付印)** with the month's name, the date and your persona's name is drawn on the
  pieces board but not used yet; it could replace the memory book's date stamp.

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

## Open

- Everything marked **proposed** above, in particular: a suggestion using an activity slot while
  notes and reminders are free; the sharing defaults; quick busy from a notification; sleep as
  quiet hours.
- **The second pass:** whether the vertical week replaces the list or is offered beside it; who
  picks a persona's seal; whether plans should get a "done" state at all.
- **The app's seasons and the solar terms:** the theme follows meteorological seasons (autumn until
  30 November), while the band says winter begins on 7 November. Either the theme follows the solar
  terms too (a Look setting; see [`ideas.md`](ideas.md)), or the band and theme disagree for a few
  weeks each season.
- **Holidays:** Japanese diaries colour public holidays like Sundays. Which holidays (your
  country's, from a holiday calendar on the phone or a built-in list), or none?
- **Special days** beyond the solar terms (moon-viewing, New Year): which ones, and do personas get
  seasonal activities from them?

## Parked ideas

- **Mechanical stickers:** stickers the harness itself acts on, not only the persona (for example
  an "away" stamp that holds letters).
- **Do Not Disturb and Android's modes** as a busy signal. Check first whether an app can read
  them.
