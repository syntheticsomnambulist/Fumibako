package dev.aiharness.app.data

import androidx.room.useReaderConnection
import dev.aiharness.app.data.db.HarnessDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What the letters' files take up, and which of them nothing refers to any more: a deleted
 * letter's video, a picture from a reply that was written again, something taken out of a note
 * before it went. Nothing is deleted until [clear] is called (Settings, Storage).
 *
 * A file is in use while its ref (`file:<id>.<ext>`, as [FileBlobStore] makes them) appears in
 * any text column of any table, or in [elsewhere] (drafts, settings). The text is searched rather
 * than the fields that hold refs being listed, so a field or table added later is covered without
 * a change here: missing one would delete a file that's still in use. (Keepsakes, for one, share
 * their letter's file: a deleted letter's photo can still be a memory's.) Only a ref's id is
 * matched, so whatever follows it in the text doesn't matter.
 *
 * A file changed in the last [GRACE_MS] is never unused: something just picked, drawn or being
 * made ready (a video shrinking, a sticker not yet kept, an avatar not yet saved) lives only in
 * memory until it's saved. Nor is one whose name isn't a ref's.
 */
class BlobSweep(
    private val database: HarnessDatabase,
    private val blobs: FileBlobStore,
    /** Text kept outside the database that can hold refs (drafts, settings). */
    private val elsewhere: suspend () -> List<String>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** What the files take up, by kind, and what the unused ones do. Sizes in bytes. */
    data class Report(
        val bytes: Long = 0,
        val files: Int = 0,
        val videos: Long = 0,
        val songs: Long = 0,
        val pictures: Long = 0,
        val other: Long = 0,
        val unusedBytes: Long = 0,
        val unusedFiles: Int = 0,
    )

    /** Looks without changing anything. */
    suspend fun look(): Report = withContext(Dispatchers.IO) {
        val all = blobs.files()
        report(all, unused(all))
    }

    /** Deletes the unused files, and says what's left. */
    suspend fun clear(): Report = withContext(Dispatchers.IO) {
        val all = blobs.files()
        val deleted = unused(all).filter { it.delete() }.toSet()
        val left = all - deleted
        report(left, unused(left))
    }

    private suspend fun unused(files: List<File>): List<File> {
        val cutoff = clock() - GRACE_MS
        val candidates = files.filter { idOf(it) != null && it.lastModified() < cutoff }
        if (candidates.isEmpty()) return emptyList()
        val used = HashSet<String>()
        findIn(databaseText(), used)
        elsewhere().forEach { findIn(sequenceOf(it), used) }
        return candidates.filter { idOf(it) !in used }
    }

    /** Every text value in every table, read a row at a time. */
    private suspend fun databaseText(): Sequence<String> {
        val texts = ArrayList<String>()
        database.useReaderConnection { connection ->
            val tables = connection.usePrepared("SELECT name FROM sqlite_master WHERE type = 'table'") { s ->
                buildList { while (s.step()) add(s.getText(0)) }
            }
            for (table in tables) {
                // Every column but those declared for numbers or bytes (embedding vectors).
                val columns = connection.usePrepared("PRAGMA table_info(${quote(table)})") { s ->
                    buildList {
                        while (s.step()) {
                            val type = if (s.isNull(2)) "" else s.getText(2).uppercase()
                            if (type !in NOT_TEXT) add(s.getText(1))
                        }
                    }
                }
                if (columns.isEmpty()) continue
                connection.usePrepared("SELECT ${columns.joinToString { quote(it) }} FROM ${quote(table)}") { s ->
                    while (s.step()) {
                        for (i in columns.indices) if (!s.isNull(i)) s.getText(i).takeIf { PREFIX in it }?.let(texts::add)
                    }
                }
            }
        }
        return texts.asSequence()
    }

    private fun report(files: List<File>, unused: List<File>): Report {
        var videos = 0L
        var songs = 0L
        var pictures = 0L
        var other = 0L
        files.forEach { f ->
            val size = f.length()
            when (f.extension.lowercase()) {
                in VIDEO -> videos += size
                in SONG -> songs += size
                in PICTURE -> pictures += size
                else -> other += size
            }
        }
        return Report(
            bytes = videos + songs + pictures + other,
            files = files.size,
            videos = videos,
            songs = songs,
            pictures = pictures,
            other = other,
            unusedBytes = unused.sumOf { it.length() },
            unusedFiles = unused.size,
        )
    }

    companion object {
        /** A day: what's newer may be on its way to being saved. */
        const val GRACE_MS = 24 * 60 * 60 * 1000L

        private const val PREFIX = "file:"

        /** A ref's id: the file's name before its extension (a UUID). */
        private val REF = Regex("file:([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})")
        private val ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        private val NOT_TEXT = setOf("INTEGER", "INT", "REAL", "BLOB")
        private val VIDEO = setOf("mp4", "m4v", "mov", "webm", "mkv", "3gp", "avi")
        private val SONG = setOf("m4a", "mp3", "flac", "wav", "aac", "ogg", "oga", "opus", "aif", "aiff", "wma")
        private val PICTURE = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")

        /** The id in a file's name, or null when it isn't named like a ref (then it's never unused). */
        internal fun idOf(file: File): String? = file.name.substringBefore('.').lowercase().takeIf { ID.matches(it) }

        internal fun findIn(texts: Sequence<String>, into: MutableSet<String>) {
            texts.forEach { text -> REF.findAll(text).forEach { into += it.groupValues[1].lowercase() } }
        }

        private fun quote(name: String) = "`" + name.replace("`", "``") + "`"
    }
}
