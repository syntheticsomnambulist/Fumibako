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
    objects. To be confirmed: in v0.3.5 or right after.

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
7. **Keepsakes** (if confirmed for v0.3.5).
8. **Polish, tests, an APK.**
