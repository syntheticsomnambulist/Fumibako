# v0.3.5 plan: media

Started 2026-11-06, after v0.3.1. The design is the design session's `docs/design/media.md` and
`docs/design/keepsakes.md` (on its branch, `ccr-09dbf6b8-2c3jno`), with mockups and prototypes on
its "Media frames" design canvas (private claude.ai artifact:
https://claude.ai/artifact/YRJXX7GbfT5Xpj8szVE6LA).

## Progress

- **Step 1, attachments and the tray: done.** Files, songs, playlists, videos and links are a new
  content part (photos stay pictures), each with a stand-in a model reads until step 3
  (`[cassette: rainy day mix, …]`, `[tag: market-map.pdf, PDF, 3 pages, 2.4 MB]`). The + tray
  opens on five buttons drawn after the design canvas (Photos, Camera, File, Music, Video) and
  Stickers. What you pick is copied into the app; pictures are scaled to 1280 px; songs bring
  their tags, length and cover; PDFs their pages and first page; videos their length and three
  frames. Picked items wait under "Going with your note" with a remove button. Several songs make
  a playlist and open the playlist editor (label, the line beneath, songs moved up and down or
  taken out, more added; down to one, it goes as a ticket). A link written in a note becomes a
  cutting from the page's Open Graph data (public addresses only, redirects checked); on a line
  of its own it leaves the text when sent. A note can be attachments alone.
- **Step 2, frames: done.** In letters and notes: photos as polaroids taped in the sender's ink
  (several as a stack with a count), files as luggage tags on a string, playlists as cassettes
  with the label handwritten in the sender's ink, songs as ticket stubs with notches and a play
  mark, links as newspaper cuttings with a masthead, a greyscale picture and a paperclip, videos
  as film strips with their three frames. Handwriting (Klee One) and newsprint (Old Standard TT)
  faces are bundled, cut down to Latin. Notes draw frames smaller; each frame has a slight tilt of
  its own; at night they're lit by the lamp (photos left alone with "Not photos"). Holding a
  frame offers Save to phone, Share and Copy link; tapping opens it in the phone's own app until
  the players and browser come. Stickers go on frames too, as their own target, and the persona
  hears where (`[Sam stuck :heart: on the cassette in Sam's Fri 00:26 note]`).
- **Step 3, what the persona receives: done.** Per provider (`core/media/MediaSupport.kt`):
  Claude sees pictures and reads PDFs (as documents); Gemini also hears songs and watches videos
  (inline, under its 20 MB request size); OpenAI-compatible servers see pictures when "Sees
  pictures" is on (the switch is now only shown for them: every Claude and Gemini model sees);
  text files go as text everywhere, cut to a third of the context budget. Where a model can't take
  something whole, pictures stand in when it sees (a video's three frames, a PDF's first page,
  now stored at 1280 px), else the stand-in; a cutting's stand-in carries the page's description.
  Anything over a third of the budget (a long video, a 600-page PDF) goes by its stand-in, and
  whole things are chosen newest first within the request size. After each turn a short
  description is written, by the utility model or the persona's own; epochs that begin after it
  carry the description instead (history stays append-only within an epoch). What the persona's
  model can't take in at all, the utility model describes before the persona first reads it, so a
  text-only model hears what's in a photo. Under each frame in your note, a line says what the
  persona can't take in ("Aria can read the label, but can't hear the songs."), and a model's
  settings say what it takes in. Token estimates for compaction count pictures and files.
- **Step 4, players and tapping: done.** Songs and playlists play through Media3 in a
  playback service with a media session (notification, lock screen, headphones), and keep playing
  after leaving the thread. Tapping a cassette or ticket plays it; the mini player docks above the
  composer (a progress line in the sender's ink, a small cassette or ticket, play/pause, next,
  stop) and opens the full player over the thread: the cassette with its hubs turning and tape
  winding through the playlist, a scrubber, shuffle, previous, play/pause, next, repeat and the
  J-card track list; or the ticket with back and forward 10 seconds, repeat and what the persona
  knows of the song. A ticket is punched the first time it plays: a mark kept in a new `marks`
  table (database version 4), apart from the letter. A photo opens the viewer, over a dark ground
  with the rest of its letter's photos to swipe through, pinch or double-tap to look closer, Share
  and Save. A film strip opens the video player (its own Media3 player; the music pauses): tap
  the picture for back 10 seconds, play/pause and forward 10 seconds; below, mute, the scrubber,
  full screen (landscape), the title, who it's from, what the persona sees of it, and the letter's
  other clips.
  The motion, from the design's table: a photo lifts, straightens and grows a little with a
  deeper shadow, then the viewer fades in; several photos fan out on a spring (a pair like two
  cards in a hand) and a tapped one opens; a luggage tag swings on its string around the eyelet
  while Open and Save to phone slide out beneath it; a ticket's punch pops and the chad falls
  the first time it plays; a cutting lifts before opening; a film strip runs through as if in a
  projector while its player opens; frames in a letter that arrives, or a note just sent, drop
  onto the page one after another. With Android's "Remove animations" on, every effect is
  instant.
- **Step 5, links and personas' media: done.** Cuttings open in the app's own browser (Custom
  Tabs, its toolbar in the paper color, sliding up), or the phone's default with Settings, Media,
  "linksInApp" (the page comes in step 6). Personas open links already in the conversation
  (`open_link`): with Claude on Anthropic's API, Anthropic's web fetch (`web_fetch_20260209` on
  current Opus and Sonnet, the basic one on others; three pages a turn, 12,000 tokens each); with
  other models, or Claude through a proxy, the app's own fetcher on the phone (public addresses
  only, redirects checked), which hands the persona the page's readable words with its links kept.
  Either way the persona's 読 stamp lands on the cutting, in its ink (a light tick when it lands
  while you watch), kept as a mark like the punched ticket. Personas share links and songs
  (`share_link`): the app opens each first and doesn't send one that doesn't open, and tells the
  persona what the page really is. Songs: an audio file plays in the app's player (streamed);
  YouTube, SoundCloud and Bandcamp play in their official embedded players in a sheet over the
  thread, only while it's open; Spotify and Apple Music open in their apps; the ticket says
  which. The line under a cutting goes once the persona can open it.
- **Step 6, Settings: Media: done.** 九 Media (About moves to 十): where links open; link
  previews (off, a cutting is just the address); whether personas open links you share; pictures
  you send, smaller (about 1280 px) or full size; describing what arrives; keeping music playing
  after leaving the letters; starting videos muted; and switches for the frames' motion and the
  haptic ticks, apart from Android's "Remove animations". The app also keeps itself through
  rotation now, so the video player's full screen doesn't close it.
- **Step 6, several pronoun sets: done.** A persona can have more sets beside its first (up to
  four, any of them its own), added with "+ Another set" on its page; its chip reads "she/they".
  It's told all of them ("Your pronouns are she/her and they/them/their; others may use any of
  them."), and the app's sentences about it take turns, sentence by sentence, or pick at random
  (the same all day, so nothing changes as you read). The first set stays where it was (a second
  field holds the rest), so nothing needed migrating.
- **Step 7, keepsakes: done** (`keepsakes.md`). A memory keeps copies of things from its letter
  or note (so they outlast the letter, and keep their stickers by the attachment's id). The
  persona chooses with `keep` on `memory_save` and `memory_update`, naming them by the handles it
  sees ("[cassette: rainy day mix]", "the photo"); naming nothing keeps the first thing in the
  note being answered, and "nothing" keeps nothing. When a memory comes up, the persona reads
  what was kept, not the file ("(kept with [ticket: October Walk by Hana Sato, 3:42])"). In the
  book, a day's keepsakes take the place of its picture: at most two, then "+2"; each is a
  scrapbook object about two-thirds size (a polaroid, a tag tied through a punched hole, the
  stub, punched if played, a cassette's J-card with its songs in the sender's hand, a smaller
  cutting with its read stamp, two frames snipped from a film strip), still while you scroll,
  aged with its memory (fading yellows them, newsprint fastest; worn ones crease and lose a
  corner), and tapped to play or open in place, with the mini player docked at the bottom of
  the book. A "Keepsakes" tab shows only memories with something kept; a closed month shows a
  kept ticket stub or J-card peeking out of its envelope. The memory card shows them under the
  card, so letting it go burns them with it; with "Let me edit memories" on, you can keep more
  from its letter or take them out.
- **Step 8, polish and the APK: done.** 0.3.5 (code 7). The testers' guide says what reaches
  websites now (link previews, personas opening links) and what to try. Checks, and all four
  screenshot tours, pass. Not yet tried on a phone: picking photos, files, songs and videos;
  the camera; playback in the background and its notification; the video player's full screen;
  Custom Tabs; the YouTube, SoundCloud and Bandcamp players (YouTube's in a web view may refuse
  without the right origin); Claude's web fetch and Gemini's audio and video with live keys.

## What v0.3.5 includes

1. **Sending media** from the + tray beside the field: Photos (one or several), Camera, File,
   Music (several songs make one playlist, edited in the playlist editor), Video, and the sticker
   page from v0.3. Picked items wait above the field under "Going with your note". A link pasted
   into a note becomes a cutting; on a line of its own it leaves the note's text.
2. **Frames:** each attachment as stationery, in the sender's ink: a polaroid (a stack for
   several), a luggage tag for files, a cassette for a playlist, a ticket stub for a song, a
   newspaper cutting for a link (from the page's Open Graph data, fetched once), a film strip for
   video. Notes scale frames down to fit. A tilt per message, long-press for Save and Share (and
   Copy link). Lamplight at night, with its setting.
3. **Tapping and motion:** the viewer for photos, a fanned stack, a swinging tag with Open and
   Save, reels turning, a ticket punched on first play, the cutting lifting into the in-app
   browser, the film strip running; arrivals dropping onto the page; the read stamp landing.
   Android's "Remove animations" makes every effect instant.
4. **Players** (Media3 with a MediaSession and a playback service): a mini player above the
   composer, the full player for a playlist (cassette, J-card track list), a song (ticket) and
   video.
5. **Links** open in an in-app browser (Custom Tabs) by default. Personas can open links you
   share: Claude's web fetch, or the app's own fetcher for other providers (on the phone, refusing
   private and local addresses). Its read stamp lands on the cutting.
6. **What the persona receives,** per provider: pictures, PDFs, text files, audio and video where
   the model takes them, stand-ins where it doesn't (a song's title and artist, three frames of a
   video, a file's name, kind and size). A short description is written when something arrives,
   and later context epochs carry the description instead of the file. Pictures are scaled to
   about 1280 px by default.
7. **Media from personas:** pictures (as now, as polaroids), links, and songs, made playable where
   possible (direct audio, then official embeds, else opening the service's app).
8. **Settings, Media:** where links open, link previews, picture size, playback, paper motion and
   haptics.
9. **Stickers on attachments:** a new sticker target kind. Stickers stay stuck: a sticker belongs
   to the attachment it's on, wherever that shows.
10. **Several pronoun sets per persona** (e.g. she/they): the persona is told all of them, and the
    app's own sentences alternate between them.
11. **Keepsakes** (`keepsakes.md`): memories keep attachments from their letter; the persona
    chooses with `keep` on `memory_save` and `memory_update`; the book shows them as scrapbook
    objects. Confirmed for v0.3.5, after the rest.

## Storage

- Attachments are a new content part on messages (JSON rows: no migration). Each holds its kind,
  its blob and MIME type, what its frame shows (title, artist, length, pages, size, site, headline,
  snippet, picture, address, a playlist's tracks), a small thumbnail, and the description written
  for later epochs.
- What changes after sending (a ticket punched, the persona's read stamp) is kept apart from the
  parts sent to providers, so history stays append-only. If it needs a table, that's a migration
  step (`CLAUDE.md`).
- Files you send are copied into the app's own storage, so they stay when the original moves.

## Order of work

1. **Attachments and the tray:** the content part, copying files in, thumbnails and metadata
   (pictures scaled, songs' tags and length, PDFs' pages, videos' frames), the tray's buttons,
   "Going with your note", pasted links becoming cuttings (with the Open Graph fetch), the
   playlist editor.
2. **Frames** in letters and notes, with lamplight, long-press, and stickers on them.
3. **What the persona receives:** provider capabilities, stand-ins, descriptions for later epochs.
4. **Players and tapping:** Media3, the playback service, the mini and full players, the viewer,
   and the motion.
5. **Links and personas' media:** Custom Tabs, the app's fetcher, the read stamp, personas
   sharing links and songs.
6. **Settings, Media; several pronoun sets.**
7. **Keepsakes.**
8. **Polish, tests, an APK.**
