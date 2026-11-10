# Storage

What Fumibako keeps on the device, what keeps it small, and the optimizations still to do. Started
2026-11-09, when a tester's install reached 5 GB, mostly from videos.

## Where it goes

- **`files/blobs`** (on a computer, `blobs` in the data folder): every picture, song, video and file
  in letters, portraits, stickers and link pictures, one file each (`FileBlobStore` in
  `data/.../Stores.kt`). Rows refer to them as `file:<uuid>.<ext>`. Nearly all the space is here.
- **The database** (`harness.db`): JSON rows (`data/.../db/HarnessDatabase.kt`). Small next to the
  files.
- **`files/drafts`**: unsent notes, one small file per conversation.
- **The cache**: camera shots on their way in, and copies being made. Cleared as it goes.

## In place

- **Clearing unused files** (`data/.../BlobSweep.kt`): Settings, Media, Storage shows what letters'
  files take up by kind, and clears the ones nothing refers to (from deleted letters, replies
  written again, things taken out of a note before it went) when you tap the button. A file is in
  use while its ref appears in any text column of any table, in a draft or in the settings, so a
  table or field added later is covered without a change to the sweep. Keepsakes share their
  letter's file, which is why refs are searched for rather than files deleted with each letter.
  Files changed in the last day are never cleared.
- **Smaller copies kept, on the phone** (`app/.../media/Shrinker.kt`, `AndroidMediaImport.kt`): a
  video you add is kept as a 720p copy at about 2.5 Mbit/s instead of the original, and a lossless
  song (FLAC, WAV, AIFF) as 256 kbit/s AAC. The original stays when the copy can't be made or
  wouldn't save at least a fifth.
- **Leaks closed:** deleting a persona removes its sticker sheet; deleting a conversation from the
  letters list discards its draft; the camera's shot is deleted from the cache once it's brought in.
- **To try on a phone:** the 720p copy of a long video (it can take up to the video's own length
  plus two minutes, then keeps the original), and of an HDR video.

## Decided against, for now

- **Videos already in letters stay as they are** (the user, 2026-11-09): only videos added from
  now on are kept at 720p.
- **The computer keeps videos whole:** making copies there would mean bundling a video converter,
  tens of MB in each installer, and computers have more room.

## Future optimizations

In order of the space they'd save. None of them changes what plays, shows or saves.

1. **Delete the copies made for models once they're no longer needed.** A video or song added on
   the phone also gets a smaller copy for models (`modelRef`: 360p, or 64 kbit/s AAC). Playback,
   the viewer and saving use the kept file (`ref`); the model copy is read only when a model is
   sent the item (`MediaSupport.sentRef`, `MediaParts`) and when it's described on arrival
   (`MediaDescriber`). Once the item has a description and the conversation's context began after
   it was written (`MediaParts.described`), only the description is sent, for good. Then clear
   `modelRef`, `modelMimeType`, `modelSizeBytes` and `modelLengthMs` on that attachment, and on the
   copies of it in keepsakes, and the sweep frees the file. Up to about 14 MB a video and 2 MB a
   song.
   - Only for items with a description: with `modelRef` cleared, `sentRef` falls back to the kept
     file, which is far bigger.
   - Changing a sent letter is safe here because the context it's in already sends the
     description, so what the model is sent doesn't change.
2. **Scale link pictures when they're fetched.** A cutting's picture is stored as the site sends
   it, up to 3 MB (`LinkPreviews`, `MAX_PICTURE`), though it only ever shows small. Scaled to about
   800 px as JPEG it's mostly under 200 KB. `core` can't decode pictures, so it needs a small
   platform hook: the phone's `Bitmap`, the computer's Skia, as the importers already use.
3. **Keep drawn pictures as lossless WebP.** `ImageService` stores what the image host returns
   (NovelAI sends PNGs of 1.5 to 2 MB). Lossless WebP is about 25 to 40% smaller with the same
   pixels, and keeps a sticker cut-out's transparency. NovelAI writes the prompt into the PNG's
   metadata, which would go; the picture's caption keeps it. The same platform hook as 2 (on the
   phone, `Bitmap.CompressFormat.WEBP_LOSSLESS`, Android 11 and later; older phones keep PNG).
4. **Tidy the database.**
   - **Old replay data:** a reply keeps its provider's own record of the turn (`replay`), which is
     sent back only within the context it was written in (`ContextBuilder`: `message.epochId ==
     state.epochId`). In earlier contexts it's never read again, and it repeats the reply's text,
     so it can be cleared.
   - **Old embedding vectors:** vectors for an embedding model no longer chosen (`memory_vectors`
     is keyed by model).
   - **VACUUM after a big clear:** SQLite doesn't shrink its file when rows go.
   - Tens of MB at most. A data change, not a schema change: no migration.
5. **Clear unused files on its own.** Once a tester build or two shows the Storage card's counts
   are right, run `BlobSweep.clear()` in the heartbeat's tidying (`HeartbeatEngine`, beside the
   activity log and peeled stickers), say once a month. The card stays, for the sizes and for
   clearing straight away.
