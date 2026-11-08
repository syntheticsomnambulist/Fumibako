package dev.aiharness.desktop

import dev.aiharness.app.data.FileBlobStore
import dev.aiharness.app.media.Repeat
import dev.aiharness.app.platform.AudioEngine
import dev.aiharness.app.platform.AudioEvents
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.AttachmentMark
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.MarkKind
import dev.aiharness.core.model.Track
import dev.aiharness.core.storage.MarkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Songs and playlists in the desktop's player, with a stand-in for JavaFX's. */
class DesktopPlaybackTest {
    /** Plays nothing: remembers what it was asked, and lets the test say when a track ends or fails. */
    private class Speaker : AudioEngine {
        val loaded = mutableListOf<String>()
        var events: AudioEvents? = null
        override var playing = false
        override var positionMs = 0L
        var stopped = 0

        override fun load(uri: String, events: AudioEvents) {
            loaded += uri
            this.events = events
            positionMs = 0
            playing = true
            events.ready(180_000)
        }

        override fun play() {
            playing = true
        }

        override fun pause() {
            playing = false
        }

        override fun stop() {
            playing = false
            stopped++
        }

        override fun seekTo(ms: Long) {
            positionMs = ms
        }

        fun end() = events!!.ended()

        fun fail() = events!!.failed("unknown format")
    }

    private class Marks : MarkRepository {
        val added = mutableListOf<AttachmentMark>()
        override fun observeAll(): Flow<List<AttachmentMark>> = flowOf(added)
        override suspend fun forAttachment(attachmentId: String) = added.filter { it.attachmentId == attachmentId }
        override suspend fun add(mark: AttachmentMark) {
            added += mark
        }

        override suspend fun deleteForConversation(conversationId: String) = Unit
    }

    private val dir: File = Files.createTempDirectory("fumibako-playback").toFile()
    private val blobs = FileBlobStore(File(dir, "blobs"))
    private val speaker = Speaker()
    private val marks = Marks()
    private val outside = mutableListOf<File?>()
    private val playback = DesktopPlayback(blobs, marks, speaker, CoroutineScope(Dispatchers.Unconfined)) { file, _ -> outside += file }

    private fun song(name: String): String = runBlocking { blobs.put(name.toByteArray(), "mp3") }

    private fun uriOf(ref: String) = blobs.existingFile(ref)!!.toURI().toString()

    private fun mix(vararg refs: String?) = ContentPart.Attachment(
        "mix", AttachmentKind.PLAYLIST, title = "A mix",
        tracks = refs.mapIndexed { i, ref -> Track(ref = ref, title = "Track $i", lengthMs = 180_000) },
    )

    @Test
    fun aSongPlaysAndItsTicketIsPunched() {
        val ref = song("one")
        val ticket = ContentPart.Attachment("t1", AttachmentKind.SONG, ref = ref, title = "One")
        playback.play(ticket, "c1", ink = 0, fromYou = false)
        assertEquals(listOf(uriOf(ref)), speaker.loaded)
        val now = playback.state.value!!
        assertTrue(now.playing)
        assertEquals(180_000, now.durationMs)
        assertEquals(listOf(MarkKind.PUNCHED), marks.added.map { it.kind })
        // Played again from its frame: it goes on, it doesn't start over.
        playback.pause()
        playback.play(ticket, "c1", ink = 0, fromYou = false)
        assertEquals(1, speaker.loaded.size)
        assertTrue(playback.state.value!!.playing)
    }

    @Test
    fun aPlaylistGoesOnToTheNextTrackAndStopsAtTheEnd() {
        val refs = listOf(song("a"), song("b"))
        playback.play(mix(*refs.toTypedArray()), null, ink = 0, fromYou = true)
        speaker.end()
        assertEquals(1, playback.state.value!!.index)
        assertEquals(uriOf(refs[1]), speaker.loaded.last())
        speaker.end()
        // The end: still there, paused at the start of the last track.
        val now = playback.state.value!!
        assertEquals(false, now.playing)
        assertEquals(1, now.index)
        assertEquals(2, speaker.loaded.size)
    }

    @Test
    fun tracksWithoutAFileAreSkipped() {
        val refs = listOf(song("a"), song("c"))
        playback.play(mix(refs[0], null, refs[1]), null, ink = 0, fromYou = true)
        playback.next()
        assertEquals(2, playback.state.value!!.index)
    }

    @Test
    fun repeatAllGoesAroundAndRepeatOneStays() {
        val refs = listOf(song("a"), song("b"))
        playback.play(mix(*refs.toTypedArray()), null, ink = 0, fromYou = true)
        playback.cycleRepeat()
        assertEquals(Repeat.ALL, playback.state.value!!.repeat)
        playback.next()
        speaker.end()
        assertEquals(0, playback.state.value!!.index)
        playback.cycleRepeat()
        assertEquals(Repeat.ONE, playback.state.value!!.repeat)
        speaker.positionMs = 170_000
        speaker.end()
        assertEquals(0, playback.state.value!!.index)
        assertEquals(0, speaker.positionMs)
        assertEquals(3, speaker.loaded.size, "repeating one track doesn't load it again")
    }

    @Test
    fun backGoesToTheStartWhenPartWayThrough() {
        val refs = listOf(song("a"), song("b"))
        playback.play(mix(*refs.toTypedArray()), null, ink = 0, fromYou = true, startAt = 1)
        speaker.positionMs = 30_000
        playback.previous()
        assertEquals(1, playback.state.value!!.index)
        assertEquals(0, speaker.positionMs)
        playback.previous()
        assertEquals(0, playback.state.value!!.index)
    }

    @Test
    fun shuffleKeepsTheTrackPlayingAndPlaysEveryOther() {
        val refs = (1..6).map { song("s$it") }
        playback.play(mix(*refs.toTypedArray()), null, ink = 0, fromYou = true, startAt = 2)
        playback.shuffle()
        assertTrue(playback.state.value!!.shuffle)
        assertEquals(2, playback.state.value!!.index)
        val heard = mutableListOf(2)
        repeat(5) {
            playback.next()
            heard += playback.state.value!!.index
        }
        assertEquals((0..5).toSet(), heard.toSet())
    }

    @Test
    fun oneThePlayerCantPlayOpensInTheComputersPlayer() {
        val ref = song("flac")
        playback.play(ContentPart.Attachment("t2", AttachmentKind.SONG, ref = ref), null, ink = 0, fromYou = false)
        speaker.fail()
        assertNull(playback.state.value)
        assertEquals(listOf(blobs.existingFile(ref)), outside)
    }

    @Test
    fun stopClearsWhatsPlaying() {
        playback.play(ContentPart.Attachment("t3", AttachmentKind.SONG, ref = song("x")), null, ink = 0, fromYou = false)
        playback.stop()
        assertNull(playback.state.value)
        assertEquals(1, speaker.stopped)
        // A late word from the player about the old song changes nothing.
        speaker.end()
        assertNull(playback.state.value)
    }

    @Test
    fun aLinkWithoutAnAudioFileDoesntPlayHere() {
        playback.play(ContentPart.Attachment("t4", AttachmentKind.SONG, url = "https://www.youtube.com/watch?v=x"), null, ink = 0, fromYou = false)
        assertNull(playback.state.value)
        assertEquals(emptyList(), speaker.loaded)
    }
}
