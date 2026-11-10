package dev.aiharness.core.transfer

import dev.aiharness.core.model.ActivityEntry
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.AttachmentMark
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.Credential
import dev.aiharness.core.model.CredentialKind
import dev.aiharness.core.model.EmbeddingProviderType
import dev.aiharness.core.model.ImageHost
import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.PlannerEntry
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.Sticker
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.storage.ActivityRepository
import dev.aiharness.core.storage.BlobStore
import dev.aiharness.core.storage.ConversationRepository
import dev.aiharness.core.storage.CredentialRepository
import dev.aiharness.core.storage.MarkRepository
import dev.aiharness.core.storage.MemoryRepository
import dev.aiharness.core.storage.ModelProfileRepository
import dev.aiharness.core.storage.PersonaRepository
import dev.aiharness.core.storage.PlannerRepository
import dev.aiharness.core.storage.SecretStore
import dev.aiharness.core.storage.SettingsRepository
import dev.aiharness.core.storage.StickerRepository
import dev.aiharness.core.storage.Transactor
import dev.aiharness.core.storage.UserPersonaRepository
import dev.aiharness.core.util.Clock
import dev.aiharness.core.util.HarnessJson
import dev.aiharness.core.util.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** A backup's manifest (`manifest.json`, the zip's first entry): what made it, when, and what's inside. */
@Serializable
data class BackupManifest(
    val kind: String = KIND,
    /** The backup format's version: [FORMAT] when this version makes it. */
    val format: Int = FORMAT,
    /** The Fumibako version that made it (0.5.0). */
    val app: String,
    val madeAt: Long,
    /** An Everything backup (it replaces all of Fumibako) rather than one persona's. */
    val everything: Boolean,
    val personas: List<BackupPersona>,
    val userPersonas: List<String> = emptyList(),
    val memories: Int = 0,
    val pictures: Int = 0,
    val letters: Int = 0,
    val planner: Boolean = false,
    val settings: Boolean = false,
    val files: List<BackupFile> = emptyList(),
) {
    companion object {
        const val KIND = "fumibako-backup"

        /** The format this version writes. */
        const val FORMAT = 1

        /**
         * The oldest format this version can bring up to date: the same rule as the database's
         * migrations (the last three, and every version testers were given; see CLAUDE.md).
         */
        const val OLDEST_FORMAT = 1
    }
}

/** A persona in a backup, as its manifest names her. */
@Serializable
data class BackupPersona(val id: String, val name: String, val ink: String, val model: String? = null, val provider: ProviderType? = null)

/** A file in a backup: the ref it had, where it is in the zip, and its size. */
@Serializable
data class BackupFile(val ref: String, val path: String, val size: Long)

/** Everything a backup holds besides its files. */
@Serializable
data class BackupRecords(
    val personas: List<Persona> = emptyList(),
    val userPersonas: List<UserPersona> = emptyList(),
    val profiles: List<ModelProfile> = emptyList(),
    val memories: List<MemoryEntry> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val stickers: List<Sticker> = emptyList(),
    val stickerArt: List<StickerArt> = emptyList(),
    val marks: List<AttachmentMark> = emptyList(),
    val planner: List<PlannerEntry> = emptyList(),
    val activity: List<ActivityEntry> = emptyList(),
    val settings: AppSettings? = null,
    /** Fonts of yours a persona's backup carries: her hand's, when it's one (Everything has them in [settings]). */
    val fonts: List<dev.aiharness.core.model.YourFont> = emptyList(),
)

/** What goes into a backup: the ticks on the Export page. */
sealed interface BackupRequest {
    /** One persona (the default): herself always, her memories and pictures on, your letters with her off. */
    data class OnePersona(val personaId: String, val memories: Boolean = true, val pictures: Boolean = true, val letters: Boolean = false) : BackupRequest

    /** Everything: all personas and yours, and each part on unless unticked. Never keys. */
    data class Everything(val memories: Boolean = true, val pictures: Boolean = true, val letters: Boolean = true, val planner: Boolean = true, val settings: Boolean = true) : BackupRequest
}

/** The parts of a backup, as the Export page's rows have them. */
enum class BackupPart { PERSONAS, YOUR_PERSONAS, MEMORIES, PICTURES, LETTERS, PLANNER, SETTINGS }

/**
 * What a backup of one persona (or of everything) would hold, part by part, measured once so the
 * Export page can say how big the file will be whatever is ticked.
 */
class BackupSurvey internal constructor(
    private val counts: Map<BackupPart, Int>,
    private val recordBytes: Map<BackupPart, Long>,
    /** Each part's files that aren't pictures (songs, videos, the card she came from). */
    private val files: Map<BackupPart, Set<String>>,
    /** Each part's pictures. */
    private val pictures: Map<BackupPart, Set<String>>,
    /** Files that go whatever is ticked: her stickers. */
    private val always: Set<String>,
    private val sizes: Map<String, Long>,
) {
    fun count(part: BackupPart): Int = counts[part] ?: 0

    /** The pictures that go with [request]: those of the parts it takes. */
    fun pictureCount(request: BackupRequest): Int = picturesFor(request).size

    /** What a part adds to the file, in bytes (pictures counted under [BackupPart.PICTURES]). */
    fun bytes(part: BackupPart, request: BackupRequest): Long = when (part) {
        BackupPart.PICTURES -> picturesFor(request).sumOf { sizes[it] ?: 0L }
        else -> (recordBytes[part] ?: 0L) + files[part].orEmpty().sumOf { sizes[it] ?: 0L } +
            if (part == BackupPart.PERSONAS) always.sumOf { sizes[it] ?: 0L } else 0L
    }

    /** About how big the file will be. */
    fun total(request: BackupRequest): Long = partsOf(request).sumOf { bytes(it, request) }

    private fun picturesFor(request: BackupRequest): Set<String> {
        val parts = partsOf(request)
        if (BackupPart.PICTURES !in parts) return emptySet()
        return parts.flatMap { pictures[it].orEmpty() }.toSet() - always
    }

    companion object {
        /** The parts [request] takes (pictures as its own part when ticked). */
        fun partsOf(request: BackupRequest): Set<BackupPart> = when (request) {
            is BackupRequest.OnePersona -> setOfNotNull(
                BackupPart.PERSONAS,
                BackupPart.MEMORIES.takeIf { request.memories },
                BackupPart.LETTERS.takeIf { request.letters },
                BackupPart.PICTURES.takeIf { request.pictures },
            )
            is BackupRequest.Everything -> setOfNotNull(
                BackupPart.PERSONAS,
                BackupPart.YOUR_PERSONAS,
                BackupPart.MEMORIES.takeIf { request.memories },
                BackupPart.LETTERS.takeIf { request.letters },
                BackupPart.PLANNER.takeIf { request.planner },
                BackupPart.SETTINGS.takeIf { request.settings },
            ) + setOfNotNull(BackupPart.PICTURES.takeIf { request.pictures })
        }
    }
}

/** What a file turned out to be, when opened as a backup. */
sealed interface BackupPeek {
    /** A backup this version can open. */
    class Open(val manifest: BackupManifest, val size: Long) : BackupPeek

    /** Locked: nothing but its versions shows until it's unlocked. */
    class Locked(val header: Lock.Header, val size: Long) : BackupPeek

    /** Made by a version older than this one can bring up to date. */
    class TooOld(val app: String) : BackupPeek

    /** Made by a newer version: update to open it. */
    class TooNew(val app: String) : BackupPeek

    object NotABackup : BackupPeek
}

/** What the Open a backup page says before anything changes. */
class RestorePreview(
    val manifest: BackupManifest,
    /** The backup's persona (one-persona backups). */
    val persona: Persona?,
    /** The persona already here who seems to be her (the same one, or one of the same name). */
    val here: Persona?,
    val hereMemories: Int,
    val hereLastLetterAt: Long?,
    /** Her model, and whether this phone has a key for it. */
    val model: ModelNeed?,
    /** For an Everything backup: what's here now, all of which goes. */
    val goes: Goes?,
) {
    data class ModelNeed(val name: String, val provider: ProviderType, val hasKey: Boolean)

    data class Goes(val personas: List<String>, val letters: Int, val notInBackup: List<String>)
}

/**
 * `.fumibako` backups: a zip holding a manifest, the records as JSON (as the database keeps them),
 * and the pictures and other files, optionally locked with a password ([Lock]). Never API keys.
 *
 * A record from an older version comes in as the database's rows do: its fields have defaults, so
 * a backup from any version this one can migrate from opens without a step of its own; [BackupManifest.FORMAT]
 * changes only when the file's layout does.
 */
class Backups(
    private val personas: PersonaRepository,
    private val userPersonas: UserPersonaRepository,
    private val profiles: ModelProfileRepository,
    private val credentials: CredentialRepository,
    private val secrets: SecretStore,
    private val conversations: ConversationRepository,
    private val memories: MemoryRepository,
    private val stickers: StickerRepository,
    private val marks: MarkRepository,
    private val planner: PlannerRepository,
    private val activity: ActivityRepository,
    private val settings: SettingsRepository,
    private val blobs: BlobStore,
    private val transactor: Transactor,
    private val clock: Clock,
) {
    /** One part's records, with the files they refer to. */
    private class Part(val records: BackupRecords, val count: Int)

    /** Measures what [request]'s kind of backup would hold, whatever is ticked. */
    suspend fun survey(request: BackupRequest): BackupSurvey = withContext(Dispatchers.IO) {
        val parts = gather(request, everyPart = true)
        val sizes = HashMap<String, Long>()
        val files = HashMap<BackupPart, Set<String>>()
        val pictures = HashMap<BackupPart, Set<String>>()
        val recordBytes = HashMap<BackupPart, Long>()
        val always = BlobRefs.findIn(encode(BackupRecords(stickerArt = parts[BackupPart.PERSONAS]?.records?.stickerArt.orEmpty())))
        parts.forEach { (part, p) ->
            val text = encode(p.records)
            recordBytes[part] = text.length.toLong()
            val refs = BlobRefs.findIn(text)
            refs.forEach { ref -> if (ref !in sizes) blobs.sizeOf(ref)?.let { sizes[ref] = it } }
            val here = refs.filter { it in sizes }
            pictures[part] = here.filter { BlobRefs.isPicture(it) }.toSet()
            files[part] = here.filterNot { BlobRefs.isPicture(it) || it in always }.toSet()
        }
        BackupSurvey(parts.mapValues { it.value.count }, recordBytes, files, pictures, always.filter { it in sizes }.toSet(), sizes)
    }

    /** The file's name: `aria-2026-11-06.fumibako`, or `fumibako-…` for everything; "-locked" when it is. */
    suspend fun fileName(request: BackupRequest, locked: Boolean): String {
        val day = DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(clock.nowMillis()).atZone(clock.zone))
        val who = when (request) {
            is BackupRequest.OnePersona -> personas.get(request.personaId)?.name?.let(::slug)?.ifEmpty { null } ?: "persona"
            is BackupRequest.Everything -> "fumibako"
        }
        return "$who-$day${if (locked) "-locked" else ""}.$EXTENSION"
    }

    /**
     * Writes [request]'s backup into [out], locked with [password] when there is one. [app] is this
     * version's name (0.5.0). Returns its manifest.
     */
    suspend fun write(request: BackupRequest, out: OutputStream, app: String, password: CharArray? = null, iterations: Int = Lock.ITERATIONS): BackupManifest =
        withContext(Dispatchers.IO) {
            if (password == null) return@withContext writeZip(request, out, app)
            val plain = File.createTempFile("backup", ".zip")
            try {
                val manifest = plain.outputStream().buffered().use { writeZip(request, it, app) }
                plain.inputStream().use { Lock.encrypt(it, out, password, BackupManifest.FORMAT, app, iterations) }
                manifest
            } finally {
                plain.delete()
            }
        }

    private suspend fun writeZip(request: BackupRequest, out: OutputStream, app: String): BackupManifest {
        val parts = gather(request, everyPart = false)
        val records = merge(parts.values.map { it.records })
        val takesPictures = BackupPart.PICTURES in BackupSurvey.partsOf(request)
        val sticky = BlobRefs.findIn(encode(BackupRecords(stickerArt = records.stickerArt)))
        val refs = BlobRefs.findIn(encode(records)).filter { takesPictures || it in sticky || !BlobRefs.isPicture(it) }
        val files = refs.mapNotNull { ref -> blobs.sizeOf(ref)?.let { BackupFile(ref, "files/" + ref.substringAfter(':'), it) } }
        val profile = { p: Persona -> p.defaultModelProfileId?.let { id -> records.profiles.firstOrNull { it.id == id } } }
        val manifest = BackupManifest(
            app = app,
            madeAt = clock.nowMillis(),
            everything = request is BackupRequest.Everything,
            personas = records.personas.map { BackupPersona(it.id, it.name, it.ink, profile(it)?.name, profile(it)?.provider) },
            userPersonas = records.userPersonas.map { it.name },
            memories = records.memories.count { !it.isReplaced },
            pictures = files.count { BlobRefs.isPicture(it.ref) && it.ref !in sticky },
            letters = letterCount(records.messages),
            planner = records.planner.isNotEmpty() || (request as? BackupRequest.Everything)?.planner == true,
            settings = records.settings != null,
            files = files,
        )
        ZipOutputStream(out).use { zip ->
            fun json(name: String, text: String) {
                zip.setLevel(Deflater.DEFAULT_COMPRESSION)
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            json(MANIFEST, HarnessJson.encodeToString(BackupManifest.serializer(), manifest))
            json("records/personas.json", list(Persona.serializer(), records.personas))
            json("records/user-personas.json", list(UserPersona.serializer(), records.userPersonas))
            json("records/profiles.json", list(ModelProfile.serializer(), records.profiles))
            json("records/memories.json", list(MemoryEntry.serializer(), records.memories))
            json("records/conversations.json", list(Conversation.serializer(), records.conversations))
            records.messages.groupBy { it.conversationId }.forEach { (id, messages) -> json("records/letters/$id.json", list(ChatMessage.serializer(), messages)) }
            json("records/stickers.json", list(Sticker.serializer(), records.stickers))
            json("records/sticker-art.json", list(StickerArt.serializer(), records.stickerArt))
            json("records/marks.json", list(AttachmentMark.serializer(), records.marks))
            json("records/planner.json", list(PlannerEntry.serializer(), records.planner))
            json("records/activity.json", list(ActivityEntry.serializer(), records.activity))
            json("records/fonts.json", list(dev.aiharness.core.model.YourFont.serializer(), records.fonts))
            records.settings?.let { json("records/settings.json", HarnessJson.encodeToString(AppSettings.serializer(), it)) }
            zip.setLevel(Deflater.BEST_SPEED)
            files.forEach { f ->
                val input = blobs.open(f.ref) ?: return@forEach
                zip.putNextEntry(ZipEntry(f.path))
                input.use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return manifest
    }

    /** What [file] is: a backup to open, a locked one, one too old or too new, or not a backup. */
    fun peek(file: File): BackupPeek {
        val start = ByteArray(Lock.MAGIC.length)
        val read = file.inputStream().use { input -> input.read(start) }
        if (read == start.size && Lock.isLocked(start)) {
            val header = file.inputStream().buffered().use { Lock.readHeader(it) }?.first ?: return BackupPeek.NotABackup
            return versionCheck(header.format, header.app) ?: BackupPeek.Locked(header, file.length())
        }
        if (read < 4 || !CardReader.isZip(start)) return BackupPeek.NotABackup
        val manifest = runCatching {
            ZipFile(file).use { zip ->
                zip.getEntry(MANIFEST)?.let { e -> zip.getInputStream(e).use { HarnessJson.decodeFromString(BackupManifest.serializer(), it.readBytes().decodeToString()) } }
            }
        }.getOrNull()?.takeIf { it.kind == BackupManifest.KIND } ?: return BackupPeek.NotABackup
        return versionCheck(manifest.format, manifest.app) ?: BackupPeek.Open(manifest, file.length())
    }

    /** Unlocks [file] into [into] (a zip), or throws [Lock.WrongPassword] or [Lock.Damaged]. */
    suspend fun unlock(file: File, password: CharArray, into: File) = withContext(Dispatchers.IO) {
        try {
            file.inputStream().use { input -> into.outputStream().buffered().use { Lock.decrypt(input, it, password) } }
        } catch (e: Exception) {
            into.delete()
            throw e
        }
    }

    /** What opening [zip] would bring, and what's here now that it touches. */
    suspend fun preview(zip: File): RestorePreview = withContext(Dispatchers.IO) {
        val (manifest, records) = read(zip)
        if (manifest.everything) {
            val here = personas.getAll()
            val letters = conversations.getAll().sumOf { letterCount(conversations.getMessages(it.id)) }
            val names = manifest.personas.map { it.name.lowercase() }.toSet()
            return@withContext RestorePreview(
                manifest, null, null, 0, null, null,
                RestorePreview.Goes(here.map { it.name }, letters, here.filter { it.id !in manifest.personas.map { p -> p.id } && it.name.lowercase() !in names }.map { it.name }),
            )
        }
        val persona = records.personas.firstOrNull()
        val here = persona?.let { findHere(it) }
        val hereConversations = here?.let { h -> conversations.getAll().filter { it.personaId == h.id } }.orEmpty()
        val model = persona?.defaultModelProfileId?.let { id -> records.profiles.firstOrNull { it.id == id } }?.let { p ->
            val mapped = bringProfile(p, profiles.getAll(), credentials.observeAll().first())
            RestorePreview.ModelNeed(p.name, p.provider, hasKey(mapped.credentialId))
        }
        RestorePreview(
            manifest = manifest,
            persona = persona,
            here = here,
            hereMemories = here?.let { h -> memories.listForPersona(h.id).count { !it.isReplaced } } ?: 0,
            hereLastLetterAt = hereConversations.mapNotNull { it.lastAgentMessageAt }.maxOrNull(),
            model = model,
            goes = null,
        )
    }

    /** How a one-persona backup comes in when she's already here. */
    enum class WhenHere {
        /** The backup's persona comes in beside her, as "Aria (6 Nov)", with a thread of her own. */
        KEEP_BOTH,

        /** Her page, memories and pictures become the backup's; your letters with her stay. */
        REPLACE,
    }

    /**
     * Brings a one-persona backup in: beside the persona already here ([WhenHere.KEEP_BOTH], also
     * when she isn't here at all) or in her place. Returns the persona as she is now.
     */
    suspend fun restorePersona(zip: File, whenHere: WhenHere = WhenHere.KEEP_BOTH): Persona = withContext(Dispatchers.IO) {
        val (manifest, records) = read(zip)
        require(!manifest.everything) { "An Everything backup replaces everything" }
        val backup = records.personas.single()
        val here = findHere(backup)
        val refs = restoreFiles(zip, manifest)
        val ids = HashMap<String, String>()
        val replacing = here != null && whenHere == WhenHere.REPLACE
        if (replacing) {
            if (here!!.id != backup.id) ids[backup.id] = here.id
        } else if (here != null) {
            // Beside her: everything the backup brings gets an id of its own.
            idsOf(records).forEach { ids[it] = newId() }
        }
        // Your persona she wrote to may not be on this phone: she writes to the one you write as.
        val active = settings.current().activeUserPersonaId
        val yours = userPersonas.observeAll().first().map { it.id }.toSet()
        (records.conversations.mapNotNull { it.userPersonaId } + records.memories.mapNotNull { it.aboutUserPersonaId })
            .filter { it !in yours }.forEach { missing -> if (active != null) ids[missing] = active }
        val keys = credentials.observeAll().first()
        val profileIds = bringProfiles(records.profiles)
        val r = remap(records, ids + profileIds, refs)
        val imageProfiles = settings.current().imageGen.profiles.map { it.id }.toSet()
        val dated = DateTimeFormatter.ofPattern("d MMM").format(Instant.ofEpochMilli(manifest.madeAt).atZone(clock.zone))
        val persona = r.personas.single().let { p ->
            p.copy(
                name = if (here != null && !replacing) "${p.name} ($dated)" else p.name,
                imageProfileId = p.imageProfileId?.takeIf { it in imageProfiles },
                updatedAt = clock.nowMillis(),
            )
        }
        transactor.inTransaction {
            if (replacing) memories.listForPersona(persona.id).forEach { memories.delete(it.id) }
            r.profiles.filter { profiles.get(it.id) == null }.forEach { profiles.upsert(withKey(it, keys)) }
            personas.upsert(persona)
            r.memories.forEach { memories.upsert(it) }
            r.stickerArt.forEach { stickers.upsertArt(it) }
            addLetters(r)
        }
        // Her hand's font, when it's one of yours and isn't here yet.
        if (r.fonts.isNotEmpty()) settings.update { s -> s.copy(fonts = s.fonts + r.fonts.filter { f -> s.fonts.none { it.id == f.id } }) }
        persona
    }

    /**
     * Replaces all of Fumibako with an Everything backup: every persona and yours, memories,
     * pictures, letters, the planner and (when it has them) settings and models. The phone's keys
     * stay, so everyone can write straight away. All of it changes, or (if something fails) none.
     */
    suspend fun restoreEverything(zip: File) = withContext(Dispatchers.IO) {
        val (manifest, records) = read(zip)
        require(manifest.everything) { "Not an Everything backup" }
        val refs = restoreFiles(zip, manifest)
        val keys = credentials.observeAll().first()
        val r = remap(records, emptyMap(), refs).let { r ->
            r.copy(profiles = r.profiles.map { withKey(it, keys) })
        }
        transactor.inTransaction {
            val here = conversations.getAll()
            here.forEach { c -> conversations.delete(c.id); marks.deleteForConversation(c.id) }
            stickers.all().forEach { stickers.delete(it.id) }
            stickers.observeArt().first().forEach { stickers.deleteArt(it.id) }
            personas.getAll().forEach { p ->
                memories.listForPersona(p.id).forEach { memories.delete(it.id) }
                personas.delete(p.id)
            }
            userPersonas.observeAll().first().forEach { u ->
                planner.deletePlanner(u.id)
                userPersonas.delete(u.id)
            }
            activity.deleteBefore(Long.MAX_VALUE)
            if (r.settings != null) profiles.getAll().forEach { profiles.delete(it.id) }

            r.profiles.forEach { profiles.upsert(it) }
            r.userPersonas.forEach { userPersonas.upsert(it) }
            r.personas.forEach { personas.upsert(it) }
            r.memories.forEach { memories.upsert(it) }
            r.stickerArt.forEach { stickers.upsertArt(it) }
            r.planner.forEach { planner.upsert(it) }
            r.activity.forEach { activity.add(it) }
            addLetters(r)
        }
        r.settings?.let { backup -> settings.update { withKeys(backup, keys) } }
    }

    // --- Reading ------------------------------------------------------------------------------

    /** The manifest and records of [zip] (an unlocked backup). */
    fun read(zip: File): Pair<BackupManifest, BackupRecords> = ZipFile(zip).use { z ->
        fun text(name: String): String? = z.getEntry(name)?.let { e -> z.getInputStream(e).use { it.readBytes().decodeToString() } }
        fun <T> list(name: String, s: KSerializer<T>): List<T> = text(name)?.let { HarnessJson.decodeFromString(ListSerializer(s), it) }.orEmpty()
        val manifest = HarnessJson.decodeFromString(BackupManifest.serializer(), text(MANIFEST) ?: error("Not a backup"))
        versionCheck(manifest.format, manifest.app)?.let { error("This backup can't be opened by this version") }
        val letters = z.entries().asSequence().filter { it.name.startsWith("records/letters/") && it.name.endsWith(".json") }.map { it.name }.toList()
        manifest to BackupRecords(
            personas = list("records/personas.json", Persona.serializer()),
            userPersonas = list("records/user-personas.json", UserPersona.serializer()),
            profiles = list("records/profiles.json", ModelProfile.serializer()),
            memories = list("records/memories.json", MemoryEntry.serializer()),
            conversations = list("records/conversations.json", Conversation.serializer()),
            messages = letters.flatMap { list(it, ChatMessage.serializer()) },
            stickers = list("records/stickers.json", Sticker.serializer()),
            stickerArt = list("records/sticker-art.json", StickerArt.serializer()),
            marks = list("records/marks.json", AttachmentMark.serializer()),
            planner = list("records/planner.json", PlannerEntry.serializer()),
            activity = list("records/activity.json", ActivityEntry.serializer()),
            fonts = list("records/fonts.json", dev.aiharness.core.model.YourFont.serializer()),
            settings = text("records/settings.json")?.let { HarnessJson.decodeFromString(AppSettings.serializer(), it) },
        )
    }

    private fun versionCheck(format: Int, app: String): BackupPeek? = when {
        format > BackupManifest.FORMAT -> BackupPeek.TooNew(app)
        format < BackupManifest.OLDEST_FORMAT -> BackupPeek.TooOld(app)
        else -> null
    }

    /** The backup's files, back in the store; returns the refs that came back under another name. */
    private suspend fun restoreFiles(zip: File, manifest: BackupManifest): Map<String, String> {
        val moved = HashMap<String, String>()
        ZipFile(zip).use { z ->
            manifest.files.forEach { f ->
                val entry = z.getEntry(f.path) ?: return@forEach
                val stored = blobs.restore(f.ref, z.getInputStream(entry))
                if (stored != f.ref) moved[f.ref] = stored
            }
        }
        return moved
    }

    // --- Gathering ----------------------------------------------------------------------------

    /** The records of each part [request] takes ([everyPart]: every part it could take). */
    private suspend fun gather(request: BackupRequest, everyPart: Boolean): Map<BackupPart, Part> {
        val parts = LinkedHashMap<BackupPart, Part>()
        val wants = { part: BackupPart -> everyPart || part in BackupSurvey.partsOf(request) }
        when (request) {
            is BackupRequest.OnePersona -> {
                val p = personas.get(request.personaId) ?: error("No such persona")
                val threads = conversations.getAll().filter { it.personaId == p.id }
                val profileIds = (listOfNotNull(p.defaultModelProfileId, p.heartbeatModelProfileId) + threads.mapNotNull { it.modelProfileId }).toSet()
                parts[BackupPart.PERSONAS] = Part(
                    BackupRecords(
                        personas = listOf(p),
                        profiles = profiles.getAll().filter { it.id in profileIds },
                        stickerArt = stickers.art(p.id),
                        fonts = listOfNotNull(dev.aiharness.core.model.Hands.yours(p.hand, settings.current().fonts)),
                    ),
                    1,
                )
                if (wants(BackupPart.MEMORIES)) memories.listForPersona(p.id).let { parts[BackupPart.MEMORIES] = Part(BackupRecords(memories = it), it.count { m -> !m.isReplaced }) }
                if (wants(BackupPart.LETTERS)) parts[BackupPart.LETTERS] = letters(threads)
            }
            is BackupRequest.Everything -> {
                val all = personas.getAll()
                val yours = userPersonas.observeAll().first()
                val threads = conversations.getAll()
                parts[BackupPart.PERSONAS] = Part(BackupRecords(personas = all, stickerArt = stickers.observeArt().first(), activity = activity.observeSince(0).first()), all.size)
                parts[BackupPart.YOUR_PERSONAS] = Part(BackupRecords(userPersonas = yours), yours.size)
                if (wants(BackupPart.MEMORIES)) all.flatMap { memories.listForPersona(it.id) }.let { parts[BackupPart.MEMORIES] = Part(BackupRecords(memories = it), it.count { m -> !m.isReplaced }) }
                if (wants(BackupPart.LETTERS)) parts[BackupPart.LETTERS] = letters(threads)
                if (wants(BackupPart.PLANNER)) {
                    val entries = yours.flatMap { planner.forPlanner(it.id) }
                    parts[BackupPart.PLANNER] = Part(BackupRecords(planner = entries, stickers = stickers.all().filter { it.conversationId == null }), entries.size)
                }
                if (wants(BackupPart.SETTINGS)) parts[BackupPart.SETTINGS] = Part(BackupRecords(profiles = profiles.getAll(), settings = settings.current()), 1)
            }
        }
        if (everyPart) parts[BackupPart.PICTURES] = Part(BackupRecords(), 0)
        return parts
    }

    private suspend fun letters(threads: List<Conversation>): Part {
        val ids = threads.map { it.id }.toSet()
        val messages = threads.flatMap { conversations.getMessages(it.id) }
        return Part(
            BackupRecords(
                conversations = threads,
                messages = messages,
                stickers = threads.flatMap { stickers.forConversation(it.id) },
                marks = marks.observeAll().first().filter { it.conversationId in ids },
            ),
            letterCount(messages),
        )
    }

    // --- Bringing in --------------------------------------------------------------------------

    /** Letters that aren't here yet (by id), with their stickers and marks. */
    private suspend fun addLetters(r: BackupRecords) {
        r.conversations.forEach { c -> if (conversations.get(c.id) == null) conversations.upsert(c) }
        r.conversations.map { it.id }.distinct().forEach { id ->
            val here = conversations.getMessages(id).map { it.id }.toSet()
            r.messages.filter { it.conversationId == id && it.id !in here }.sortedBy { it.createdAt }.forEach { conversations.appendMessage(it) }
        }
        r.stickers.forEach { s -> if (stickers.get(s.id) == null) stickers.upsert(s) }
        r.marks.forEach { marks.add(it) }
    }

    /** The persona here who's the backup's [p]: the same one, or else one of the same name. */
    private suspend fun findHere(p: Persona): Persona? =
        personas.get(p.id) ?: personas.getAll().firstOrNull { it.name.trim().equals(p.name.trim(), ignoreCase = true) }

    /**
     * The backup's model profiles, as this phone will have them: one here already (the same, or
     * the same model at the same place) or the backup's, with a key from here when there's one of
     * its kind. Returns the ids to change.
     */
    private suspend fun bringProfiles(backup: List<ModelProfile>): Map<String, String> {
        val here = profiles.getAll()
        val keys = credentials.observeAll().first()
        return backup.mapNotNull { p ->
            val mapped = bringProfile(p, here, keys)
            (p.id to mapped.id).takeIf { mapped.id != p.id }
        }.toMap()
    }

    private fun bringProfile(p: ModelProfile, here: List<ModelProfile>, keys: List<Credential>): ModelProfile =
        here.firstOrNull { it.id == p.id }
            ?: here.firstOrNull { it.provider == p.provider && it.modelId == p.modelId && it.baseUrl == p.baseUrl }
            ?: withKey(p, keys)

    private fun withKey(p: ModelProfile, keys: List<Credential>): ModelProfile =
        p.copy(credentialId = keyFor(p.credentialId, kindOf(p.provider), keys))

    private fun withKeys(s: AppSettings, keys: List<Credential>): AppSettings = s.copy(
        memory = s.memory.copy(
            embedding = s.memory.embedding?.let { e ->
                e.copy(credentialId = keyFor(e.credentialId, if (e.provider == EmbeddingProviderType.GEMINI) CredentialKind.GEMINI else CredentialKind.OPENAI_COMPATIBLE, keys))
            },
        ),
        imageGen = s.imageGen.copy(profiles = s.imageGen.profiles.map { it.copy(credentialId = keyFor(it.credentialId, kindOf(it.host), keys)) }),
    )

    /** [id] when it's a key here, else this phone's key of [kind], else none. */
    private fun keyFor(id: String?, kind: CredentialKind?, keys: List<Credential>): String? =
        id?.takeIf { want -> keys.any { it.id == want } } ?: kind?.let { k -> keys.firstOrNull { it.kind == k }?.id }

    private suspend fun hasKey(credentialId: String?): Boolean =
        credentialId != null && credentials.get(credentialId) != null && secrets.get(SecretStore.credentialKey(credentialId)) != null

    private fun idsOf(r: BackupRecords): Set<String> =
        (r.personas.map { it.id } + r.memories.map { it.id } + r.conversations.map { it.id } + r.messages.map { it.id } +
            r.stickers.map { it.id } + r.stickerArt.map { it.id } + r.activity.map { it.id }).toSet()

    /**
     * [r] with [ids] and [refs] changed wherever they appear (in every field, nested ones too: a
     * sticker's target, a P.S.'s letter, a memory's source), as the records' JSON text. Ids and refs
     * are UUIDs, so nothing else can match.
     */
    private fun remap(r: BackupRecords, ids: Map<String, String>, refs: Map<String, String>): BackupRecords {
        if (ids.isEmpty() && refs.isEmpty()) return r
        var text = encode(r)
        if (refs.isNotEmpty()) text = BlobRefs.PATTERN.replace(text) { refs[it.value] ?: it.value }
        if (ids.isNotEmpty()) text = UUID.replace(text) { ids[it.value] ?: it.value }
        return HarnessJson.decodeFromString(BackupRecords.serializer(), text)
    }

    private fun merge(parts: List<BackupRecords>): BackupRecords = BackupRecords(
        personas = parts.flatMap { it.personas },
        userPersonas = parts.flatMap { it.userPersonas },
        profiles = parts.flatMap { it.profiles }.distinctBy { it.id },
        memories = parts.flatMap { it.memories },
        conversations = parts.flatMap { it.conversations },
        messages = parts.flatMap { it.messages },
        stickers = parts.flatMap { it.stickers }.distinctBy { it.id },
        stickerArt = parts.flatMap { it.stickerArt },
        marks = parts.flatMap { it.marks },
        planner = parts.flatMap { it.planner },
        activity = parts.flatMap { it.activity },
        settings = parts.firstNotNullOfOrNull { it.settings },
        fonts = parts.flatMap { it.fonts }.distinctBy { it.id },
    )

    private fun encode(r: BackupRecords) = HarnessJson.encodeToString(BackupRecords.serializer(), r)
    private fun <T> list(s: KSerializer<T>, items: List<T>) = HarnessJson.encodeToString(ListSerializer(s), items)

    companion object {
        const val EXTENSION = "fumibako"
        private const val MANIFEST = "manifest.json"
        private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        /** Letters you'd count: yours and theirs that show, not tool rounds or hidden prompts. */
        fun letterCount(messages: List<ChatMessage>): Int =
            messages.count {
                !it.hidden && (it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT) &&
                    (it.text.isNotBlank() || it.images.isNotEmpty() || it.attachments.isNotEmpty())
            }

        fun kindOf(provider: ProviderType): CredentialKind = when (provider) {
            ProviderType.ANTHROPIC -> CredentialKind.ANTHROPIC
            ProviderType.OPENAI_COMPATIBLE -> CredentialKind.OPENAI_COMPATIBLE
            ProviderType.GEMINI -> CredentialKind.GEMINI
            ProviderType.NOVELAI_TEXT -> CredentialKind.NOVELAI
        }

        fun kindOf(host: ImageHost): CredentialKind? = when (host) {
            ImageHost.NOVELAI -> CredentialKind.NOVELAI
            ImageHost.OPENAI -> CredentialKind.OPENAI_COMPATIBLE
            ImageHost.GOOGLE -> CredentialKind.GEMINI
            else -> null
        }

        private fun slug(name: String) = name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-')
    }
}

/** Blob refs in text (`file:<uuid>.<ext>`, or `mem:` in tests), the way [dev.aiharness.core.storage.BlobStore]s make them. */
object BlobRefs {
    val PATTERN = Regex("(?:file|mem):[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.[A-Za-z0-9]{1,10}")
    private val PICTURES = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")

    fun findIn(text: String): Set<String> = PATTERN.findAll(text).map { it.value }.toCollection(LinkedHashSet())

    fun isPicture(ref: String): Boolean = ref.substringAfterLast('.').lowercase() in PICTURES
}
