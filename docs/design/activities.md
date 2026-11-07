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
- **Each desk has a token limit,** adjustable per persona: 40,000 tokens a day to start, to tune
  with use. When it's reached the desk closes until the next day, but the persona still sits down
  to decide whether to write to you.
- **Two calls are fine:** sitting down, then one call per thing taken up.
- **The first game is shiritori** (below).
- **The look:** the app's own, in the madoromi palette: lineless or thin lines, minimal shading,
  ink tints on paper (see *The look* below).
- **You choose which activities use a slot** and which are free, per persona, in her settings
  ("What she does").
  Today's split is the default. **¥¥** marks the expensive ones.
- **Games are separate from other activities,** with their own controls (see *Games* below).
- **Shiritori players can challenge a doubtful word.**
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
     reason ("Juno's letter is waiting", "Sam played 'teapot'", "You haven't drawn in a week"),
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
what a letter needs, and nothing for games.

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

## The desk's token limit

- **A daily limit per desk** (agreed), counted from the usage providers report (estimated from
  length where they don't). It replaces the pen pals' shared daily and weekly ceilings.
- **40,000 tokens a day by default** (agreed, to tune). A sitting down is roughly 1,500 to 3,000
  tokens, an activity 3,000 to 7,000, a letter to you more, depending on how many of your letters
  it reads; a busy heartbeat like the one on the mockup is about 14,000.
- **When it's reached** (agreed), the desk closes until the next day, except that the persona still
  sits down to decide whether to write to you, so a full desk never silences it.
- **Shown in her settings as a stamp card,** like a radio-exercise card: twenty boxes, one stamp per
  twentieth of the limit (2,000 tokens at 40,000), with today's use written underneath.

## The desk page (proposed)

In the **Desk** tab (see *Your desk and the Desk tab*), with the persona's name tab chosen; her
page's "Aria's desk" and a "While you were away" line open the tab at her desk. It replaces the
persona's heartbeat page.

- **The desk:** seen from above, in the persona's paper, with one object for each activity that's
  switched on: a sketchbook (its drawings and stickers), a diary, a game board, a letter tray for
  pen pals, the memory book, cuttings it has read, its planner slips. Tap an object to open it.
- **The in-tray:** "From Sam", holding what you've left until the persona takes it up.
- **Lately at the desk:** the log, each line leading to what was made.
- **The drawer opens on the desk** (agreed, 6 November): tapping the drawer front slides it out
  under the desk; tapping the pull again slides it shut.
- **Putting away and taking out** (agreed): while the drawer is open, tapping something on her desk
  puts it away (what can go is outlined), and tapping something in the drawer takes it out and
  puts it back on her desk.
- **The drawer means "allowed, but not what she's focusing on"** (agreed), and it keeps old things
  too. Put away, she may still do it, just not as often. She doesn't see the drawer when she sits
  down (agreed); she moves things in and out of it in an activity of its own, **rearranging her
  desk** (below). **Kept in the drawer** (agreed), as tags you can open: letters from past months,
  bundled; last month's diary; finished games; the trinkets the dish has no room for.

### Rearranging her desk (agreed)

- **Now and then, in a heartbeat,** she's shown what's on her desk and what's in her drawer
  (only what she may do), with how long since she last used each, and may move things either
  way: one small call with a tool, `rearrange(put_away, take_out)`.
- **When:** at most once a week, and only when something on the desk has gone unused for
  a fortnight or something in the drawer is waiting.
- **What you moved stays put a while:** for a week after you move something she sees
  "Sam put this away on Tuesday" and leaves it.
- **Free by default** in "What she does" (it's short), with Slot | Free like the rest.
- **Logged** in "Lately at the desk" ("Rearranged her desk: put her haiku notebook away"), which
  opens the drawer.
- **What she does lives in her settings** (agreed), and means "may she do this at all": each
  activity with a switch (off: never, gone from her desk and from her drawer) that also says where
  it is ("on her desk", "in her drawer"), and a **Slot | Free** choice, with ¥¥ beside the
  expensive ones; the games, in a section of their own; slots per visit; the stamp card for the
  daily limit; sealed things; when it wakes, and the model for heartbeats. It sits alongside her
  other restrictions. Everything on today's heartbeat page moves there. Free activities still count
  towards the daily limit. Persona settings and the app's Settings get a top-down redesign later
  (v0.95); until then, "What she does" is a page in her settings.
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

## Shiritori by post (the first game)

Agreed as the first game. Proposed rules and shape:

- **The game:** each word starts with the last letter of the one before ("teapot", then
  "tangerine", then a word starting with "e"). No word comes twice. A round ends when one of you
  passes, and a new one starts with any word.
- **Challenges** (agreed): either player can challenge a doubtful word. The challenged
  player explains it on their next turn (a line on what it means), or takes it back and plays
  another; the challenger then accepts it or doesn't, and a word that isn't accepted is struck
  out and the turn goes back. Nothing is scored, so it stays friendly. The persona sees the
  challenge as the reason on its menu ("Sam challenged 'tangerine'").
- **By post:** one word a turn. The persona plays at most one word a heartbeat, and only on its
  turn; it's offered on the menu as "Sam played 'teapot'". You play from the slip on its desk.
- **The app keeps the chain** and checks the first letter and repeats, so the model only has to
  come up with a word. Its turn gets the last few words, the letter it needs, and one tool
  (`play_word(word)` or `pass()`); about 2,000 tokens, so it's cheap and works with any model.
- **Who can play:** you and a persona, and later pen pals with each other.
- **On the desk:** in the games box (below). Opening it shows the whole chain in each player's
  ink with the linking letters marked, a field for your word, and "Challenge" on the last word.
- **Uses a slot** (playing). Its "While you were away" line: "played 'tangerine' in shiritori".
- **Later:** a kana version (where a word ending in ん loses, as in Japanese) for personas and
  people who'd like it.

## Games

Agreed: games are separate from other activities, with controls of their own, as follows (the
games box itself is proposed):

- **A games box on the desk** (遊), with the slips of games in progress peeking out from under its
  lid. Opening it lists the games going, opens the one whose turn it is, and offers to start
  another.
- **In "What she does", a Games section:** each game on or off, with its own Slot | Free choice; "She
  can start a game with you" (on by default; off, she only answers games you start); and "Games
  going at once", adjustable, 2 by default.
- **One slot for all her moves in a visit:** playing several games in one visit uses a single
  slot, so a busy games box doesn't crowd out writing to you.
- **Pen pals can play each other** (v0.6, with pen pals), behind a switch of its own.
- **Candidates for v0.6.5:** renga (linked verse, alternating 5-7-5 and 7-7 lines), twenty
  questions, word ladders, dots and boxes, battleship, and exquisite corpse (¥¥, since it draws).
  Related but not a game: a page of games you're playing yourself, which you tell the persona
  about and it asks after.

## Trinkets

The user's idea (2026-11-06): little things a persona keeps, like buttons, plants and nice
stones. Each is a picture and a description, and can be tied to memories. Personas come by them
when they go out, and can give them to you or to their pen pals. **Planned for v0.6**, with going
out, and open to more in the v0.7 outdoor update. The details below were agreed as proposed:

- **What one is:** a name ("a striped river stone"), a description in the persona's words, where
  and when it came from (found on a walk, from Juno, from you), a picture, and the memories it's
  kept with.
- **On the desk:** a 豆皿 (mamezara, a small dish) holding the newest few; opening it shows them
  all, each with its picture, words, where it came from, and a link to its memory.
- **Coming by them:** mostly while going out (reading a link, an imagined walk, and more with
  the v0.7 outdoor update): the activity may offer "keep something you found", with
  `keep_trinket(name, description, kind)`. Free with the activity it belongs to, not a slot of
  its own.
- **Pictures:** a built-in set of small drawings in the desk's look (buttons, stones, shells,
  acorns, leaves, feathers, keys, marbles, little plants), each tinted from the persona's inks,
  so a trinket costs no picture tokens. Personas with drawing switched on can draw one instead
  (¥¥).
- **Memories:** `memory_save` and `memory_update` can keep a trinket the way they keep an
  attachment (see [`keepsakes.md`](keepsakes.md)); the persona sees "(kept with a trinket: a
  striped river stone)", and the memory book shows it with the memory.
- **Giving:** the persona can enclose one in a letter to you, where it arrives in a small glassine
  envelope (a new frame), or post one to a pen pal. A given trinket moves to its new owner and
  remembers where it came from.
- **Yours:** the trinkets you're given live on a desk of your own (below). You might also give
  trinkets: a photo of a real stone, left on a persona's desk, becoming a trinket.
- **Limits:** at most one find per going-out, and a dish that holds a set number before older
  ones are kept in the drawer.
- **Later, maybe:** plants that grow a little over time.
- **Storage:** trinkets are a new table (owner, name, description, kind, tint or picture, origin,
  given-by, created), a migration step.

## Your desk and the Desk tab

The user's idea (2026-11-06): a desk of your own, beside your personas'. It takes over "a
scrapbook of your own" from [`keepsakes.md`](keepsakes.md), and gives the trinkets personas give
you somewhere to land.

### Agreed

- **A fifth tab, 机 Desk,** between Personas and Planner (文 Letters, 人 Personas, 机 Desk,
  帳 Planner, 設 Settings), where you look between all the desks: yours and your personas'.
- **Name tabs along the top** switch desks, like folder tabs, each with its owner's portrait and
  a band of their tape: each of your personas' desks first, a tab each ("Sam, your desk"), a
  small gap, then the personas' (Aria, Juno, Haru) in the Letters list's order, most recent
  first. The chosen tab joins the page.
- **One desk for each of your personas,** like their planners: what a persona gives Sam lands on
  Sam's desk.
- **In v0.6:** the trinket dish and one scrapbook. More scrapbooks and other objects later.
- **Scrapbook pages:** adding is quick, things flowing into the next free spot; while arranging,
  anything can go anywhere.
- **Telling her:** when you paste something of a persona's (her drawing, a line from her letter),
  she hears in her next context ("Sam pasted your window cat into their scrapbook"), with a switch
  on each scrapbook to keep it private. Only the one whose it is hears.

### Proposed

- **Your desk** (Sam's, in your ink and tape): the scrapbook, the trinket dish, your sticker sheet
  (agreed: the one in Settings, Stickers, also on your desk) and a pencil, on a desk pad like
  hers, with room for more later. Below: **Lately on your desk** ("Aria gave you an acorn, in a glassine
  envelope"; "You pasted the window cat into your scrapbook. Aria heard.").
- **Your trinket dish** opens a sheet: each trinket's picture, its name in the giver's hand, what
  it is, who gave it and in which letter (**Open the letter it came in**).
- **The scrapbook:** a page in its book (a cover in your ink with a label, pasted things peeking
  out). Its pieces are what the memory book shows for keepsakes (a polaroid, a ticket stub, a
  J-card, a cutting, a film strip's frames) plus **a line cut from a letter** (the words in the
  letters face on a scissor-cut strip, with who wrote it and when), your own **photos**, **notes
  in your hand**, and **stickers** from your sheet.
- **Adding:** in a letter, select words and **Cut out for your scrapbook**, or hold a picture,
  cutting, ticket or cassette and **Paste in your scrapbook**; or on the page, **From a letter**,
  **A photo**, **A note**, **A sticker**. Each lands in the next free spot ("What you add next lands
  here"), with "Aria will hear about it" when she will.
- **Arranging:** **Arrange** outlines every piece; tap one, then move it, turn it, change its tape
  (corners, top, none) or take it off the page; **Done** keeps it where you put it. Pieces keep
  their place; new ones keep flowing after the last.
- **Storage:** scrapbooks, pages and pieces (kind, what it points at, position, turn, tape),
  pieces pointing at the original attachment or message so stickers stay stuck and "open the
  letter it came from" works.

## The look

Worked out over four rounds of mockups (and still being tuned): draw the desk the way the app
already looks, in madoromi (https://madoromi.moonwit.ch/), the palette the app's colours come
from. The rest of the app is right as it is; the desk should match it.

- **The desk is the app's desk:** madoromi's `hyoshi` ("the desk under the paper") is the page,
  with a desk pad in `chitsu`, the deepest surface, and darker corner pieces. No wood.
- **Objects are lineless or thinly lined:** flat shapes in ink tints, each an ink mixed into the
  paper colour the way the app already mixes them (`lerp(kami, ink, t)`, at about 12, 25, 40, 55
  and 70%). Wire, ruled lines and seals can be thin lines; nothing gets a dark outline.
- **Minimal shading:** at most one deeper tint for a spine or an edge. No gradients.
- **The app's own soft shadow** (its 2dp elevation), under objects and cards alike. Chosen on
  6 November after a side-by-side in the v0.5 pass (see [`v0.5.md`](v0.5.md)): it replaced the
  hard-edged 2dp offset the desk mockups were first drawn with (redrawn the same day), a touch
  deeper for things on the desk pad and in the drawer. The heavier blurred shadow of the first
  desk mockups stays dropped.
- **Shapes, not boxes:** fewer plain rectangles. Tape with zigzag torn ends; sticky tabs and
  keepsakes poking out of books; a dog-eared page; an arched window in the sketchbook; die-cut
  stickers on their sheet; a scalloped postage stamp and a postmark; a luggage tag; an index tab
  rising from the in-tray; a paperclip; a cup pull on the drawer.
- **Depth:** trays and the drawer are drawn as boxes seen from above, with a darker back wall,
  side walls, a floor and a front lip. The drawer's front has only a cup pull (its labels were
  taken off, 6 November). **The drawer slides open:** opening it, the drawer comes out from under
  the desk's front edge with a small overshoot, as if just pulled; tapping the pull slides it
  shut and out again; on her desk, the drawer front gives a little tug when pressed.
- **Tape is opaque,** as in the app: a solid tint of the owner's ink with its pattern in the
  paper colour.
- **Everything else is the app's existing stationery:** `kami` cards with a hairline `nijimi0`
  border; section headings with an ink underline; boxed-cell choices, the chosen cell shaded and
  underlined in ink; tick boxes; text in `sumi` and `usuzumi`; washi tape in the persona's ink and
  pattern.
- **New stationery pieces that fit:** the "last visit" line on the persona's washi tape, and the
  daily limit as a radio-exercise stamp card.
- **Tried and dropped:** thick dark outlines (too much like icons), a pixel font (too much like a
  video game), heavier offset shadows, hard offset shadows of any weight, index-tab labels on
  cards, screentone, a wooden desk, heavy blurred shadows, and translucent tape.
- **Fonts** are the ones chosen in the v0.5 font audit: Zen Old Mincho for names, Literata for
  letters, Zen Kaku Gothic New for labels, Yuji Syuku for seals, and the persona's own hand
  (Yomogi by default) for her diary, sketchbook titles and anything she writes by hand. No
  italics: the desk's explanations are upright interface text.
- Whether anything from this reaches the rest of the app is a question for the v0.5 stationery
  update.

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
| Rearranging her desk (new in v0.6) | Its own short turn | No, by default |

v0.6 adds pen pals (the letter tray) and shiritori. The planner's look-ahead (v0.4) runs
in the private moment until then, and moves to the desk in v0.6.

## Mockups

The "Writing desk" design canvas (private claude.ai artifact:
https://claude.ai/artifact/834NiGLU9vt32k1Wxc9eH4), drawn 2026-11-06 and redrawn four times the same day, ending in the look above:

- **Aria's desk** (interactive): her desk from above with a diary (one entry sealed), a
  sketchbook, the games box (shiritori with you, with a challenge you can make and withdraw), a
  letter tray with Juno's letter, the memory book, a cutting she read, and the "From Sam"
  in-tray, plus her sticker sheet, a pencil and her trinket dish (a stone, a button from Juno, an
  acorn), on a desk pad. Tap any of them to open it; the
  sealed diary entry opens, then closes (seal broken) or reseals. The "last visit" line is on
  washi tape. Under the desk, tapping the drawer front slides the drawer out in place, showing
  what's put away (her haiku notebook) and what's kept (Juno's letters from September and October,
  September's diary, a finished game of shiritori). While it's open, tap something on her desk to
  put it away, or a slip in the drawer to take it out; tap the pull to close. Below, "Lately at
  the desk", with which lines used a slot.
- **What she does** (interactive; her settings, first drawn as "The drawer"): each activity with a
  switch for whether she may do it at all (saying whether it's on her desk or in her drawer) and
  Slot | Free, ¥¥ on the expensive ones; the Games section; slots per visit; the daily limit as a
  stamp card; sealed things; and when she comes by.
- **Leaving something on the desk** (interactive): a link, a photo, a riddle, a first word for
  shiritori, or something to draw, with a note; leaving it drops it into her in-tray.
- **What Aria sees at her desk:** one heartbeat's three calls (sitting down, her sketchbook,
  writing to you), what each is given, roughly what each costs, and the line that reaches your
  thread afterwards.
- **The Desk tab** (added after the font audit): **Aria's desk** now sits in it, with the name tabs
  along the top. **Sam's desk** has the scrapbook, the trinket dish (tap it for the sheet), the
  sticker sheet and "Lately on your desk". **Your scrapbook**: add a note, photo or sticker and
  watch it land in the next free spot; **Arrange**, tap a piece, then move, turn, re-tape or remove
  it; the switch for telling personas. **Pasting from a letter**: cut out a line, or paste the
  polaroid, from Aria's letter.

## Open

- **Your desk:** the proposed details in *Your desk and the Desk tab* (the pieces, arranging).

- **The look beyond the desk:** mostly settled in v0.5 (soft shadows, the chosen faces, tapes in
  two shades, all across the app); drawn objects in the desk's style elsewhere are still to come.
