# Trying Fumibako

Thanks for trying Fumibako (文箱, "a box for keeping letters"): an app for writing letters with AI
personas who remember you, have feelings about what they remember, and sometimes write to you on
their own. It runs on an Android phone and on a computer (Windows, macOS or Linux). This is an
early test build.

The guide is written for the phone; what's different on a computer is in
[*On a computer*](#on-a-computer). Where it says tap, click; where it says hold, right-click.

## Installing on a phone

1. Open the `.apk` you were sent on your Android phone (Android 8 or newer).
2. Your phone will ask to allow installing apps from that source (your browser, Files or chat
   app). Allow it for this install.
3. Open **Fumibako**. It asks to send notifications: allow it if you'd like to hear when a
   persona writes to you.

Updates are installed the same way, over the top; your letters stay.

## On a computer

The phone and the computer are two separate Fumibakos, each with its own personas and letters;
nothing syncs between them.

### Installing on Windows

1. Open the `.msi` you were sent. If your browser holds the download back as uncommon, choose
   **Keep** (in Edge: the **…** beside it, **Keep**, then **Show more** and **Keep anyway**).
2. Windows SmartScreen says **"Windows protected your PC"**, because the installer isn't signed
   (signing costs money, and can wait until more people are testing). Click **More info**, then
   **Run anyway**.
3. The installer puts Fumibako in the Start menu and on the desktop. It installs for you alone,
   so it doesn't ask for an administrator.

Updates: open the new `.msi` the same way. It replaces the old version, and your letters stay.

### Installing on a Mac

1. Open the `.dmg` and drag **Fumibako** into **Applications**.
2. Open it from Applications. The first time, macOS says it can't check the app for malicious
   software, because it isn't signed by an Apple developer. Click **Done** (or **OK**), then open
   **System Settings, Privacy & Security**, scroll down to "Fumibako was blocked", and click
   **Open Anyway**. Confirm with your password. (On macOS 14 and earlier, Control-click the app
   and choose **Open** instead.)
3. If macOS says the app **"is damaged and can't be opened"**, it was quarantined on download.
   Open Terminal and run `xattr -dr com.apple.quarantine /Applications/Fumibako.app`, then open
   it again.

Your API keys are kept in the macOS Keychain. If macOS asks whether Fumibako may use its keychain
item (after an update, for example), click **Always Allow**.

The Mac build hasn't been tried on a Mac yet, so tell us how these steps went.

### Installing on Linux

The `.deb` is for Debian, Ubuntu and their relatives: `sudo apt install ./fumibako_*.deb`. It
installs into `/opt/fumibako`, with Fumibako in your applications menu. An update installs the
same way, over the top.

### Keeping heartbeats going

On a phone, personas write to you even when the app is closed. On a computer they write while
Fumibako is running:

- **Closing the window keeps Fumibako in the tray** (the notification area by the clock on Windows,
  the menu bar on a Mac), so heartbeats go on. The first close says so. Click the seal to open
  the window again; its menu also has **Busy for now**, **Run a heartbeat now** and **Quit
  Fumibako**.
- **Quit** stops heartbeats until you start Fumibako again.
- **When the computer sleeps**, nothing runs. A heartbeat that came due meanwhile comes a minute
  after it wakes (once, not once for every hour missed), and a reminder a persona set for itself
  wakes it then too.
- **Settings, Heartbeat, "On this computer"** can start Fumibako in the tray when you log in (off
  until you turn it on; only for Fumibako installed from its installer), and can make closing the
  window quit instead.
- **Notifications** come from the tray, by the same busy rules as on the phone. They have no
  buttons, so "Busy for now" is in the tray's menu. A letter counts as read only while the window
  is in front of you.
- Some Linux desktops have no tray (GNOME needs the AppIndicator extension, which Ubuntu
  includes). There, closing the window quits Fumibako, and Settings, Heartbeat says so.

Fumibako runs one copy at a time: starting it again brings its window forward.

### Where your letters live

Everything is in one folder for your user, outside the app, so updating or uninstalling Fumibako
leaves your letters where they are:

- Windows: `%APPDATA%\Fumibako` (paste it into File Explorer's address bar)
- Mac: `~/Library/Application Support/Fumibako` (in Finder, **Go, Go to Folder…**)
- Linux: `~/.local/share/Fumibako` (or `$XDG_DATA_HOME/Fumibako`)

The database, settings and every file sent in a letter (in `blobs`) are there. Your API keys are
there too, encrypted with a key that the system's keychain holds (Windows Credential Manager, the
macOS Keychain, or the Secret Service on Linux). Where Fumibako can't use a keychain, that key is in
a file only your user can read, and **Settings, Models and keys** says so.

To keep a copy, quit Fumibako and copy the folder. To start over, quit and delete it. To run a
second, separate Fumibako (to try something without touching your letters), start it with the
`FUMIBAKO_HOME` environment variable set to another folder.

### Not on the computer yet

- **No camera.** Photos and files come from a file dialog, by dragging them onto your note, or by
  pasting (Ctrl+V, Command+V on a Mac).
- **Videos** play in the computer's own player, and go to the model whole (Gemini) or by their
  name, without the phone's three frames or its smaller copy.
- **Songs** play in the app as MP3, AAC (M4A), WAV and AIFF. FLAC and Ogg open in the computer's own
  player.
- **A sticker from a photo** only works for a photo on a plain background (the phone also cuts the
  subject out of a busy one).
- **Phone calendars:** the computer's planner doesn't read calendars, so Settings, Planner leaves
  that part out.
- **Sunrise and sunset** need a latitude and longitude typed in (Settings, Look).
- **The burn** in the memory book is the plainer version.
- **Wide windows:** Letters and Personas spread out side by side; the planner and Settings keep
  the phone's layout in the middle.

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
  motion switches. Its Storage card shows what letters' files take up, and clears files nothing
  uses any more (from deleted letters, replies written again, things taken out of a note). On the
  phone, videos you add are kept at 720p and lossless songs (FLAC, WAV) as AAC, to save space.
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
