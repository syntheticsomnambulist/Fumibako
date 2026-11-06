# Calendar (planned for v0.4)

Agreed 2026-11-06. Stickers come first (v0.3); the planner uses them.

## Why

Aria, the test persona, asked for "a gentle way to know when a conversation has gone quiet on
purpose, so I don't mistake a busy afternoon for being ignored."

Today a persona only learns how long it has been, never why: each turn's context says "Time since
the previous message: 5 hours", and a heartbeat says "Sam last wrote 5 hours ago" and "They may be
busy". A calendar answers the why without the user having to say they'll be busy before they are.
It is also the other half of the memory book: the memory book looks back, the planner looks ahead.

## Decisions

- **In-app calendar by default.** The planner holds the user's own entries, including a typical
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
  calendar, and each of your personas has a switch for whether it shares one (user personas can
  be fictional; Sam is a test user). The calendar reaches a letter only when both are on.
- **The persona chooses whether to write during a busy period.** It is told the user is busy and
  until when, and that a letter written now will probably be read later. It may write anyway or
  stay quiet.
- **Notifications are held during known busy periods** by default: the letter lands in the chat,
  and its notification is shown when the period ends. Adjustable in settings.
- **Personas write in the planner:** notes and reminders on dates, in their own ink and visible to
  the user, and suggested plans ("meteor shower Saturday?") that show up as entries.
- **Stickers in the planner**, placed by the user and by personas, as on letters.

## How it fits the harness

- **What the persona sees** goes in each turn's `<context>` block, for example: "Sam is at work
  until 17:30. Later today: dinner with Jo, 19:00. Tomorrow: dentist, 10:00." It must not go in the
  system prompt: the context block is stored on the message when it is sent, so later requests
  replay the same bytes, whereas in the system prompt every calendar change would start a new epoch
  and lose the prompt cache. How far ahead to look (a day and a half, plus notable entries later in
  the week?) is to be tuned.
- **Heartbeats:** "They may be busy" is replaced by what the calendar says. A new heartbeat
  activity, *look ahead*, puts upcoming entries in front of the persona and lets it write notes on
  dates, suggest plans, or ask to be woken after something ends.
- **Waking after an event:** heartbeats run every N minutes (15 at least, and Android batches
  them), so "right after the interview" needs a one-off job scheduled near the event's end.
- **Tools** (names to be decided): view a date range, write a note or reminder on a date, suggest
  a plan. Stickers use the v0.3 sticker tool.
- **Stationery:** a bullet-journal week spread and a month view for further ahead. Synced events
  are written in pencil (read-only here), the user's entries in the user's ink, and each persona's
  notes in its own ink.

## For v0.3 (stickers)

The sticker sketch in [`README.md`](README.md) anchors a sticker to a message
(`Sticker(id, messageId, …)`). Planner stickers need other anchors (a day, an entry), so the v0.3
sticker should point at a target (kind and id) rather than a bare `messageId`. That keeps the
sticker tool and the notes personas receive ("[Sam stuck :star: on Saturday's meteor shower]")
the same when the planner arrives.

## Open

- **Mockups** for the planner page.

## Parked ideas

- **Mechanical stickers:** stickers the harness itself acts on, not only the persona (for example
  an "away" stamp that holds letters).
- **A "Busy, I'll write later" button** on persona notifications, for busy stretches that aren't on
  any calendar.
- **Do Not Disturb and Android's modes** as a busy signal. Check first whether an app can read
  them.
