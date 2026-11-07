package dev.aiharness.app.media

import dev.aiharness.app.ui.media.PlayerControls
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Track
import kotlinx.coroutines.flow.StateFlow

/** How a playlist or song repeats. */
enum class Repeat { OFF, ALL, ONE }

/**
 * What's playing: a song (ticket) or a playlist (cassette) from a letter, which track, and where.
 * [ink] is the sender's ink (day colours, as ARGB), for the progress line and the label.
 */
data class NowPlaying(
    val attachment: ContentPart.Attachment,
    val conversationId: String?,
    val ink: Int,
    val fromYou: Boolean,
    val index: Int = 0,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Repeat = Repeat.OFF,
) {
    val tracks: List<Track>
        get() = if (attachment.kind == AttachmentKind.PLAYLIST) attachment.tracks
        else listOf(Track(ref = attachment.ref, mimeType = attachment.mimeType, title = attachment.title, artist = attachment.artist, lengthMs = attachment.lengthMs, name = attachment.name, album = attachment.album, year = attachment.year))

    val track: Track? get() = tracks.getOrNull(index)
    val isPlaylist: Boolean get() = attachment.kind == AttachmentKind.PLAYLIST
    val title: String get() = track?.title ?: track?.name?.substringBeforeLast('.') ?: attachment.title ?: "A song"
    val artist: String? get() = track?.artist
    val length: Long get() = durationMs.takeIf { it > 0 } ?: track?.lengthMs ?: 0

    /** How far through the whole playlist: what the cassette's tape packs show. */
    val throughPlaylist: Float
        get() {
            val lengths = tracks.map { it.lengthMs ?: 0L }
            val total = lengths.sum().takeIf { it > 0 } ?: return 0f
            return ((lengths.take(index).sum() + positionMs).toFloat() / total).coerceIn(0f, 1f)
        }
}

/**
 * Plays songs and playlists from letters (on the phone, through its media session). A song's ticket
 * is punched the first time it plays (a mark, kept apart from the letter).
 */
interface Playback : PlayerControls {
    val state: StateFlow<NowPlaying?>

    /** Plays [attachment] from track [startAt]. Songs without a file of their own can't play here. */
    fun play(attachment: ContentPart.Attachment, conversationId: String?, ink: Int, fromYou: Boolean, startAt: Int = 0)

    fun pause()

    /** Shows [state] without playing anything, for the screenshot tour. */
    fun preview(state: NowPlaying?)
}
