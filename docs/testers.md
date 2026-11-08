# Trying Fumibako

Thanks for trying Fumibako (文箱, "a box for keeping letters"): an Android app for writing letters
with AI personas who remember you, have feelings about what they remember, and sometimes write
to you on their own. This is an early test build.

## Installing

1. Open the `.apk` you were sent on your Android phone (Android 8 or newer).
2. Your phone will ask to allow installing apps from that source (your browser, Files or chat
   app). Allow it for this install.
3. Open **Fumibako**. It asks to send notifications: allow it if you'd like to hear when a
   persona writes to you.

Updates are installed the same way, over the top; your letters stay.

## Setting up (about two minutes)

The **Getting started** card on the Letters page walks through it:

1. **Your name**, so personas know what to call you.
2. **An API key** for a model provider. Any one of these works:
   - Anthropic (Claude): console.anthropic.com, API keys. Claude Haiku is cheap and good.
   - Google (Gemini): aistudio.google.com, "Get API key". Has a free tier.
   - OpenAI, or OpenRouter (many models behind one key).
   - A model running on your own computer (Ollama or LM Studio), on the same Wi-Fi.
3. **A model**: pick a preset; the key you added is filled in. "Send a test message" checks it.

Then tap **New letter** and write to Aria, the persona who comes with the app, or make your own
on the Personas page.

## What it costs

The app is free; the model provider charges for what it uses, usually fractions of a cent per
letter. Two things use it in the background:

- **Heartbeats:** about once an hour a persona may wake to tidy its memories, reflect, or write
  to you. It only calls the model when something is due, and stays quiet at night (23:00 to
  08:00). Settings, Heartbeat changes the pace, or turns it off.
- **Memory upkeep:** longer conversations are summarized now and then.

Set a spending limit with your provider if you'd like to be sure.

## What stays on your phone

Everything: letters, personas, memories, pictures and the files you send are stored only on your
phone, and your key is encrypted there. Your letters (with their photos and files) are sent to the
model provider you chose. Two things also reach websites: a link you write becomes a cutting
with the page's title and picture, which means visiting the page; and a persona can open a link
you shared (Claude does that on Anthropic's servers; other models through your phone). Both can
be turned off in Settings, Media. Searching for a song sends your search words to Apple's catalog,
and so does opening a song in Apple Music when the link is from elsewhere. There are no accounts,
ads or analytics.

The planner stays on your phone too. A persona that sees your planner is told what's on in each
letter (so it goes to that persona's model provider), at the detail you chose for each phone
calendar; attendees and event notes never go. Public holidays, once you choose whose, are
fetched from Nager.Date: only the country and the year are sent, about once a season. Your
phone's calendars are read, never changed or copied.

## Things to try

- **Letters take a little while:** a persona's reply is written as soon as you send your note, then
  reaches you a few minutes later (a 〒 postmark says it's on its way, and a notification says
  when it's come). Settings, Heartbeat, "Letters in the post" sets how long, from straight away to
  two or three days, and can show a little watch with when it arrives; a persona's heartbeat page
  can give it its own time. Letters a persona writes on its own come when they come.
- Write a few letters, then look at the persona's **memory book** (Personas, a persona, Memories).
- Leave the app for a few hours; the Letters page shows what personas did while you were away.
- Change how a persona looks or writes: its page, **Edit**. Give it pronouns there too.
- **Pictures:** add an image profile in Settings, Pictures (NovelAI, your own Stable Diffusion,
  OpenAI or Google). Personas can then draw pictures, and you can from the picture icon in a
  letter. A persona's page has how it looks, what it usually wears and its drawing style.
- **Stickers:** the **+** beside the text field opens the sticker tray. Tap one to put it on your
  note, or hold it and drag it onto any letter. Tap a sticker you stuck to move, turn, resize or
  peel it. Personas stick their own, and can draw stickers of themselves (their page, Stickers).
  Shared stickers, stamps and decor are in Settings, Stickers; one can come from a photo.
- Type `:` and a few letters to write a sticker into your note.
- **Sending things:** the **+** also has Photos, Camera, File, Music and Video. Music searches
  Apple's catalog: tap a song's play button to hear a preview, tap the song to send it as a ticket with its preview
  and a link to the whole song; "From your phone" picks files. Several songs (picked together,
  or one after another) make a mixtape (a cassette) you can name and reorder. Google Photos,
  behind Video, gives videos numbers for names; pick one with File to keep its name. A link in your note becomes a newspaper cutting with the
  page's title; a Spotify, Apple Music, SoundCloud or Bandcamp link becomes a ticket, and a
  YouTube video can be either (tap it under "Going with your note"). Each arrives as stationery, and a line under it says what the persona can't take in
  with its model, and why (Claude reads PDFs; Gemini also hears songs, watches videos, and watches YouTube links; a long video is
  watched as its first few minutes).
- **Tapping things:** songs and mixtapes play (a small player docks above where you write, and
  music carries on outside the app); photos open to swipe and zoom; videos play; a file's tag
  swings and offers Open and Save; a cutting opens the page. When a persona opens a link you
  shared, its 読 ("read") stamp lands on the cutting.
- **From personas:** they can share links and songs back. Songs from YouTube, SoundCloud or
  Bandcamp play in their own player; Spotify and Apple Music open in their apps.
- **Keepsakes:** a memory can keep something from its letter (the persona chooses), shown in
  the memory book as a scrapbook piece; tap it to play or open it, or hold it to stick a sticker
  on it. The book's Keepsakes tab shows just those.
- **Settings, Media** has where links open, which app songs open in, picture size, playback and
  motion switches.
- **The planner (帳):** a week of all 24 hours, your sleep folded to a band. Add a plan, and set
  your typical week (work, sleep). Tap a plan for its stamps: 済 done, 延 move, 止 cancel. The
  tabs at the right edge go to the month (月), the week or month before (‹), today (今) and
  after (›), and the stickers. Hold the ribbon to put the bookmark on the page you're on; tap it
  to come back. Settings, Planner has your phone's calendars, public holidays, the solar terms,
  who sees your planner, and what happens to letters while you're busy (they can wait until
  you're free). A persona's notification has "Busy for now".
- **What personas do with it:** in letters and heartbeats, a persona that sees your planner
  knows what's on, and can leave notes, suggest plans (add them, or "Not this time"), set
  itself reminders (it wakes then to write), mark itself away, stick stickers, and write a haiku
  when a new season begins. Its marks are on its Stickers page.

## If something goes wrong

- Take a screenshot and say what you were doing.
- If the app closed unexpectedly, Settings, About shows a crash report: **Copy the report** and
  send it along. Nothing is sent on its own.
