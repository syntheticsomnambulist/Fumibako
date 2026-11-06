# Media (planned for v0.3.5)

Agreed 2026-11-06 in the design session. After v0.3 (pictures, then stickers) and before the v0.4
planner ([`calendar.md`](calendar.md)). Mockups and tappable prototypes are on the "Media frames"
design canvas (private claude.ai artifact: https://claude.ai/artifact/YRJXX7GbfT5Xpj8szVE6LA);
its boards are named below. Quoted app text follows the app's American spelling.

## Scope

- **Sending** photos (one or several), camera shots, files (PDF, text, anything), music (a single
  song or a playlist), video and links, from a tray beside the text field.
- **Showing** each as a stationery object on the letter or note.
- **Playing** music and video in the app, and opening links in an in-app browser.
- **What the persona receives**, per provider, with stand-ins where it can't take something in,
  and a token plan for attachments in an append-only history.
- **Media from personas:** pictures (they already draw them), links, and songs (music links made
  playable where possible).
- **Night:** lamplight, with a setting.

## The tray ("Adding something" board, interactive)

- A **+** button to the left of the text field: a paper slip with a hairline edge, 44dp. It opens
  a drawer of six paper buttons, each with a small drawing of its object: Photos, Camera, File,
  Music, Video, Link.
- **+** turns into × (a 45° turn), the drawer slides up, and its buttons arrive in a quick
  stagger.
- Picked items wait above the text field under "GOING WITH YOUR NOTE", as small versions of their
  frames, each with a remove button (a 44dp target around a small × chip). A new item pops in, and
  the drawer closes after a pick.

## The frames ("In a letter" and "Every frame" boards)

Whose it is shows in the **sender's ink**: tape, paperclip, labels and tints. Frames sit at a
slight tilt (about ±2°), chosen per message so it stays the same between visits, like tape
placement.

| What | Frame | Shows | Sender's ink |
| --- | --- | --- | --- |
| Photo | Polaroid: the polaroid frame setting's colour (white by default), a piece of tape at the top | The picture; a caption when there is one (the persona's own for its pictures) | Tape, in the sender's pattern |
| Several photos | A stack of up to three polaroids, with a count | The top picture | Tape on the top one |
| File | Luggage tag with an eyelet and string | Name; kind and size; pages for a PDF | Tag tint, eyelet ring, string |
| Playlist | Cassette, side A badge | Title and who it's from, handwritten on the label; total length | Label handwriting, badge |
| Single song | Ticket stub with a perforated stub and a play mark | "SIDE A · TRACK 1", title, artist, length | Ticket tint, stub mark |
| Link | Newspaper cutting with uneven cut edges | Site name as a masthead, title, the page's picture in greyscale, a snippet | Paperclip |
| Video | Film strip with sprocket holes | Three frames (the ones a model that can't watch video receives), length on a small label | None |

- **Paperclip:** a three-bend clip near the top right of the cutting, mostly above the paper's
  edge, with its inner loop hidden behind the paper as if gripping it. It stays clear of the
  masthead text and the title.
- **読 stamp:** when the persona opens a link you shared, its "read" stamp lands on the cutting, in
  the persona's ink.
- **What the persona can't take in** is said in an app-written line under the frame, in the
  postscript style: "Aria can read the label, but can't hear the songs." and "Aria sees three
  frames from each video."
- Photos in letters become polaroids. Today a letter's pictures are plain images, and the polaroid
  frame is only used in the personas gallery.
- Considered and not chosen: a record sleeve for songs.
- Typefaces on the frames (a handwriting face for labels, a newsprint serif for cuttings) are
  placeholders, like the app's other fonts.
- **Sizes** in the mockups, in dp: polaroid 236 wide (picture 210), stack 272×300, tag 252×96,
  cassette 240×150, ticket 280×108, cutting 288 wide, film strip 300×132. Notes are narrower (78%
  of the width), so frames scale down to fit them (the ticket to about 86%, the film strip to 82%).
- **Long-press** any frame for Save to phone and Share (and Copy link on a cutting).

## Tapping and motion ("Tapping photos and files", "Tapping music and video", "Links, read stamps and arrivals")

Timings are the prototypes'; tune them on a phone.

| What | Tap | Motion |
| --- | --- | --- |
| Photo | Opens the viewer | Lifts 10dp, straightens, scales to 1.05 with a deeper shadow (280ms, ease-out); the viewer fades in after 220ms |
| Several photos | Fans the stack out; tap one to open it | 440ms spring with a little overshoot; the back photos go to ±15°, about 75dp out |
| File | Shows Open and Save to phone | Swings on its string around the eyelet, damped (−10°, 7°, −4°, 2°, 0 over 900ms); the actions slide out beneath |
| Playlist | Plays; the mini player docks | Hubs turn (1.4s a turn) and the tape packs wind from one reel to the other over the playlist's length |
| Single song | Plays; the mini player docks | Punched the first time it plays (a 260ms pop, the chad falls and fades over 700ms); it stays punched, so you can see which songs you've played |
| Link | Opens the page straight away | Lifts 6dp and scales to 1.03 (160ms), then the browser sheet slides up (340ms) |
| Video | Opens the player | The strip runs through as if in a projector (one set of frames per 1.1s) while the player opens |

- **Read stamp:** 460ms, from 1.9× and −26° to 1× and −12° with a slight bounce, and a light
  haptic tick.
- **Arriving:** when a letter arrives or a note is sent, its attachments drop about 46dp onto the
  page and settle (640ms, with overshoot), 160ms apart.
- **Reduced motion:** with Android's "Remove animations" setting on, every effect is instant.

## Players ("Listening" and "Watching" boards, interactive)

- **Mini player**, docked above the composer while something plays: a thin progress line in the
  sender's ink along its top edge, a small cassette or ticket, title and artist, play/pause, next
  (playlists only) and stop. Tapping it opens the full player. You can keep reading and writing.
- **Full player** (a sheet over most of the screen; a tap above it or the chevron shrinks it):
  - **Playlist:** the cassette with its reels turning; title, artist, "Track 2 of 5 · rainy day
    mix"; a scrubber with elapsed and remaining time; shuffle, previous, play/pause, next, repeat;
    the track list on the cassette's J-card, in the sender's handwriting, with the current song
    marked. Tapping a song plays it. Previous restarts the song when more than 3 seconds in.
  - **Single song:** the punched ticket; a scrubber; back 10 seconds, play/pause, forward 10
    seconds, repeat; a line on what the persona knows ("Aria knows its title and artist, but can't
    hear it.").
- **Video**, full screen: tapping the picture shows or hides back 10 seconds, play/pause and
  forward 10 seconds over it. Below: mute, a scrubber with times, full screen; the title, who it's
  from and the persona-frames line; previous and next clip when the letter has several.
- **On Android:** Media3 (ExoPlayer) with a MediaSession, so the notification, lock screen,
  headphone buttons and car controls work as in other players, and a media playback service so
  music carries on after leaving the thread.

## Links

- **Tap opens the page** in an in-app browser (Custom Tabs, toolbar in the paper colour). A setting
  opens links in the default browser instead.
- **Cuttings** are built from the page's Open Graph data (site name, title, picture, description),
  fetched once when the link is attached and stored with the message. A page without a picture
  gets a text-only cutting.
- **The persona opening links:** Claude's web fetch runs on Anthropic's servers and only opens
  links already in the conversation. For other providers the app's own fetcher runs on the phone
  (sites see your address) and refuses private and local network addresses.

## Songs from personas

Music links become playable tickets where possible; otherwise tapping opens the link. To try, in
order, each checked against the service's terms:

1. Direct audio files play in our player.
2. Services with an official embeddable player (YouTube, SoundCloud, Bandcamp) play inside the
   player sheet.
3. Others (Spotify, Apple Music) get a ticket with the song's details that opens in their app.

A persona may also send real audio one day, through a music generation tool like the image tool.

## What the persona receives

| | Claude | Gemini | OpenAI-compatible |
| --- | --- | --- | --- |
| Pictures | Yes | Yes | Vision models only |
| PDFs | Yes (up to 600 pages) | Yes | Varies |
| Text files | As text | As text | As text |
| Audio, music | No | Yes | A few audio models |
| Video | No | Yes | No |

The Gemini column is from memory and needs checking. Today the providers send pictures already
(`ContentPart.Image`: Anthropic base64 images, Gemini `inlineData`, OpenAI `image_url`), but the
chat input doesn't offer them.

- **Stand-ins** where a model can't take something: a song's title, artist and album from its
  tags; three frames of a video (the ones the film strip shows); a file's name, kind and size.
- **Tokens:** history is append-only, so an attachment would be re-sent with every turn until the
  epoch ends; a photo is about 1,200 tokens at 1280×720 and up to about 4,800 at full size. The
  persona sees the real thing when it arrives, a short description is written then, and later
  epochs carry the description instead. Pictures are scaled to about 1280px by default.

## Night: lamplight ("Day and night" board)

- Frames are objects on the desk, so, like stickers, they keep their day colours at night, lit as
  if by a desk lamp: light objects dimmed to about 85% and warmed (white polaroid frames read as
  warm off-white), shadows deeper, and dark objects (the cassette shell, film strips) catch the
  lamp with a lit edge and a faint sheen.
- Holes (ticket notches, punch holes, sprocket holes) show the night paper behind.
- Ink on an object stays day ink; anything written on the letter uses night inks.
- The players turn to night paper with night-ink controls; the video player stays near-black; the
  browser sheet takes the night toolbar colour and asks sites for their dark version.
- **Setting, "Lamplight at night"** (proposed for Settings, Look): "Dim and warm everything"
  (default), "Leave photos as they are", "Leave everything as it is".

## Storage sketch

- An **attachment** on a message: kind, blob ref and MIME type, display data (title, artist,
  length, pages, size, site, headline, snippet, picture ref, URL, a playlist's tracks), and the
  short description written for later epochs. Messages are JSON rows, so new fields need no
  migration (see `CLAUDE.md`).
- **State that changes after sending** (a ticket punched, the persona's read stamp) stays out of
  the parts sent to providers, so history remains append-only, like stickers. If it needs its own
  table, that's a migration step.

## Open

- **The Link button:** a field to paste a link into, or should a link pasted into the note become
  a cutting by itself?
- **Where "Open links in" lives:** Look, or a new page for letters.
- **Playlists:** several songs picked at once, or a playlist file? Is there a length limit?
- **Notes:** scale frames down to fit, or draw smaller variants?
