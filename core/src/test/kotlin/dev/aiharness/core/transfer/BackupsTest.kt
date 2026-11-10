package dev.aiharness.core.transfer

import dev.aiharness.core.FakeClock
import dev.aiharness.core.model.ActivityEntry
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.Credential
import dev.aiharness.core.model.CredentialKind
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.Keepsake
import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.PlannerEntry
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.Sticker
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.model.StickerTarget
import dev.aiharness.core.model.StickerTargetKind
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.storage.SecretStore
import dev.aiharness.core.storage.Transactor
import dev.aiharness.core.storage.inmemory.InMemoryActivityRepository
import dev.aiharness.core.storage.inmemory.InMemoryBlobStore
import dev.aiharness.core.storage.inmemory.InMemoryConversationRepository
import dev.aiharness.core.storage.inmemory.InMemoryCredentialRepository
import dev.aiharness.core.storage.inmemory.InMemoryMarkRepository
import dev.aiharness.core.storage.inmemory.InMemoryMemoryRepository
import dev.aiharness.core.storage.inmemory.InMemoryModelProfileRepository
import dev.aiharness.core.storage.inmemory.InMemoryPersonaRepository
import dev.aiharness.core.storage.inmemory.InMemoryPlannerRepository
import dev.aiharness.core.storage.inmemory.InMemorySecretStore
import dev.aiharness.core.storage.inmemory.InMemorySettingsRepository
import dev.aiharness.core.storage.inmemory.InMemoryStickerRepository
import dev.aiharness.core.storage.inmemory.InMemoryUserPersonaRepository
import dev.aiharness.core.util.HarnessJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `.fumibako` backups (v0.5): what goes in, opening them again, the lock, and what's refused. */
class BackupsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val clock = FakeClock()

    /** One phone's worth of stores. */
    private inner class Phone {
        val personas = InMemoryPersonaRepository()
        val userPersonas = InMemoryUserPersonaRepository()
        val profiles = InMemoryModelProfileRepository()
        val credentials = InMemoryCredentialRepository()
        val secrets = InMemorySecretStore()
        val conversations = InMemoryConversationRepository()
        val memories = InMemoryMemoryRepository()
        val stickers = InMemoryStickerRepository()
        val marks = InMemoryMarkRepository()
        val planner = InMemoryPlannerRepository()
        val activity = InMemoryActivityRepository()
        val settings = InMemorySettingsRepository(AppSettings(activeUserPersonaId = "sam"))
        val blobs = InMemoryBlobStore()
        val backups = Backups(personas, userPersonas, profiles, credentials, secrets, conversations, memories, stickers, marks, planner, activity, settings, blobs, Transactor.None, clock)

        suspend fun key(id: String, kind: CredentialKind) {
            credentials.upsert(Credential(id, kind.name, kind))
            secrets.put(SecretStore.credentialKey(id), "secret")
        }

        suspend fun backUp(request: BackupRequest, password: CharArray? = null): File {
            val file = tmp.newFile()
            file.outputStream().use { backups.write(request, it, app = "0.5.0", password = password, iterations = 1_000) }
            return file
        }
    }

    /** A phone with Sam, Aria and Juno, letters, memories with a keepsake, stickers and a planner. */
    private suspend fun phone(): Phone = Phone().apply {
        key("anthropic-key", CredentialKind.ANTHROPIC)
        userPersonas.upsert(UserPersona(id = "sam", name = "Sam", createdAt = 1, updatedAt = 1))
        profiles.upsert(ModelProfile(id = "opus", name = "Claude Opus", provider = ProviderType.ANTHROPIC, modelId = "claude-opus", credentialId = "anthropic-key"))
        val avatar = blobs.put(Png.plain(4, 4, 0xA26480), "png")
        val photo = blobs.put(Png.plain(2, 2, 0x178D9D), "jpg")
        val song = blobs.put(ByteArray(300) { 7 }, "m4a")
        val wave = blobs.put(Png.plain(3, 3, 0xE3940B), "png")
        personas.upsert(Persona(id = ARIA, name = "Aria", avatarRef = avatar, defaultModelProfileId = "opus", ink = "sakura", createdAt = 1, updatedAt = 1))
        personas.upsert(Persona(id = JUNO, name = "Juno", ink = "sora", createdAt = 1, updatedAt = 1))
        stickers.upsertArt(StickerArt(id = WAVE, name = "aria-wave", ownerPersonaId = ARIA, imageRef = wave, createdAt = 1))
        conversations.upsert(Conversation(id = THREAD, personaId = ARIA, userPersonaId = "sam", title = "Aria", createdAt = 1, updatedAt = 1, lastAgentMessageAt = 20))
        conversations.appendMessage(ChatMessage(id = NOTE, conversationId = THREAD, role = MessageRole.USER, parts = listOf(ContentPart.Text("Look at the sea"), ContentPart.Image(photo, "image/jpeg")), createdAt = 10))
        conversations.appendMessage(ChatMessage(id = REPLY, conversationId = THREAD, role = MessageRole.ASSISTANT, parts = listOf(ContentPart.Text("It's lovely!")), createdAt = 20, origin = MessageOrigin.AGENT))
        stickers.upsert(Sticker(id = STUCK, conversationId = THREAD, target = StickerTarget(StickerTargetKind.MESSAGE, NOTE), ref = StickerRef.Art(WAVE), byPersonaId = ARIA, x = 0.5f, y = 0.5f, createdAt = 21))
        memories.upsert(
            MemoryEntry(
                id = MEMORY, personaId = ARIA, content = "Sam sent a photo of the sea", sourceConversationId = THREAD, sourceMessageId = NOTE,
                keepsakes = listOf(
                    Keepsake(messageId = NOTE, part = ContentPart.Image(photo, "image/jpeg"), keptAt = 30),
                    Keepsake(messageId = NOTE, part = ContentPart.Attachment(id = "song", kind = AttachmentKind.SONG, ref = song, mimeType = "audio/mp4"), keptAt = 30),
                ),
                createdAt = 30, updatedAt = 30, lastAccessedAt = 30,
            ),
        )
        memories.upsert(MemoryEntry(id = "juno-memory", personaId = JUNO, content = "Maps", createdAt = 30, updatedAt = 30, lastAccessedAt = 30))
        planner.upsert(PlannerEntry(id = "plan", plannerId = "sam", kind = EntryKind.PLAN, title = "Dentist", day = "2026-11-12", createdAt = 1, updatedAt = 1))
        activity.add(ActivityEntry(id = "act", personaId = ARIA, kind = ActivityKind.values().first(), createdAt = 5))
        settings.update { it.copy(activeUserPersonaId = "sam") }
    }

    @Test
    fun onePersonaGoesWithHerMemoriesAndPicturesButNotYourLetters() = runTest {
        val home = phone()
        val file = home.backUp(BackupRequest.OnePersona(ARIA))
        val manifest = assertIs<BackupPeek.Open>(home.backups.peek(file)).manifest
        assertEquals(listOf("Aria"), manifest.personas.map { it.name })
        assertEquals("Claude Opus", manifest.personas.single().model)
        assertEquals(1, manifest.memories)
        assertEquals(0, manifest.letters)
        assertEquals(2, manifest.pictures, "her portrait and the photo kept with her memory; not her sticker, which is herself")
        val names = ZipFile(file).use { z -> z.entries().asSequence().map { it.name }.toList() }
        assertEquals("manifest.json", names.first())
        assertTrue(names.none { it.startsWith("records/letters/") }, "your letters stay home")
        assertEquals(4, names.count { it.startsWith("files/") }, "portrait, photo, song, sticker")
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("secret"), "never keys")

        // On a new phone, with a key of her model's kind.
        val away = Phone().apply { userPersonas.upsert(UserPersona(id = "kai", name = "Kai", createdAt = 1, updatedAt = 1)); settings.update { it.copy(activeUserPersonaId = "kai") } }
        assertFalse(assertNotNull(away.backups.preview(file).model).hasKey, "no key here yet")
        away.key("new-key", CredentialKind.ANTHROPIC)
        val preview = away.backups.preview(file)
        assertNull(preview.here)
        assertTrue(assertNotNull(preview.model).hasKey)

        val aria = away.backups.restorePersona(file)
        assertEquals(ARIA, aria.id)
        assertEquals("Aria", aria.name)
        assertNotNull(away.blobs.get(assertNotNull(aria.avatarRef)), "her portrait came with her")
        assertEquals("new-key", away.profiles.get("opus")?.credentialId, "her model, with this phone's key")
        val memory = away.memories.listForPersona(ARIA).single()
        assertTrue(BlobRefs.findIn(HarnessJson.encodeToString(MemoryEntry.serializer(), memory)).all { away.blobs.get(it) != null })
        assertNotNull(away.stickers.getArt(WAVE)?.imageRef?.let { away.blobs.get(it) })
        assertTrue(away.conversations.getAll().isEmpty())
    }

    @Test
    fun herHandsFontGoesWithHerWhenItsOneOfYours() = runTest {
        val home = phone()
        val font = home.blobs.put(ByteArray(64) { 1 }, "ttf")
        home.settings.update { it.copy(fonts = listOf(dev.aiharness.core.model.YourFont(id = "f1", name = "Sam's hand", ref = font))) }
        home.personas.upsert(home.personas.get(ARIA)!!.copy(hand = "font:f1"))
        val file = home.backUp(BackupRequest.OnePersona(ARIA, pictures = false))
        val away = Phone()
        val aria = away.backups.restorePersona(file)
        assertEquals("font:f1", aria.hand)
        val came = away.settings.current().fonts.single()
        assertEquals("Sam's hand", came.name)
        assertNotNull(away.blobs.get(came.ref), "the font file came too, even without pictures")
    }

    @Test
    fun withoutPicturesOnlyHerStickersFilesGo() = runTest {
        val home = phone()
        val survey = home.backups.survey(BackupRequest.OnePersona(ARIA))
        val small = BackupRequest.OnePersona(ARIA, pictures = false)
        assertTrue(survey.total(small) < survey.total(BackupRequest.OnePersona(ARIA)))
        assertEquals(2, survey.pictureCount(BackupRequest.OnePersona(ARIA)))
        assertEquals(2, survey.pictureCount(BackupRequest.OnePersona(ARIA, letters = true)), "the photo in the letter is the one kept with her memory: it goes once")
        assertEquals(2, survey.count(BackupPart.LETTERS))
        assertEquals(0, survey.pictureCount(small))

        val file = home.backUp(small)
        val files = ZipFile(file).use { z -> z.entries().asSequence().map { it.name }.filter { it.startsWith("files/") }.toList() }
        assertEquals(2, files.size, "her sticker, and the song kept with her memory")
        assertTrue(files.any { it.endsWith(".m4a") })
    }

    @Test
    fun keepBothBringsHerInBesideHerselfWithAThreadOfHerOwn() = runTest {
        val home = phone()
        val file = home.backUp(BackupRequest.OnePersona(ARIA, letters = true))
        home.memories.upsert(MemoryEntry(id = "later", personaId = ARIA, content = "Kept after the backup", createdAt = 40, updatedAt = 40, lastAccessedAt = 40))
        val preview = home.backups.preview(file)
        assertEquals(ARIA, preview.here?.id)
        assertEquals(2, preview.hereMemories)
        assertEquals(20, preview.hereLastLetterAt)

        val twin = home.backups.restorePersona(file, Backups.WhenHere.KEEP_BOTH)
        assertNotEquals(ARIA, twin.id)
        assertTrue(twin.name.startsWith("Aria ("), twin.name)
        assertEquals("Aria", home.personas.get(ARIA)?.name, "she's as she was")
        assertEquals(2, home.memories.listForPersona(ARIA).size)

        val thread = home.conversations.getAll().single { it.personaId == twin.id }
        assertNotEquals(THREAD, thread.id)
        val letters = home.conversations.getMessages(thread.id)
        assertEquals(2, letters.size)
        val memory = home.memories.listForPersona(twin.id).single()
        assertEquals(thread.id, memory.sourceConversationId, "her memory points at her own thread")
        assertEquals(letters.first { it.role == MessageRole.USER }.id, memory.sourceMessageId)
        val art = home.stickers.art(twin.id).single()
        assertNotEquals(WAVE, art.id)
        val stuck = home.stickers.forConversation(thread.id).single()
        assertEquals(StickerRef.Art(art.id), stuck.ref, "the sticker on her letter is her own copy")
        assertEquals(twin.id, stuck.byPersonaId)
        assertEquals(1, home.stickers.forConversation(THREAD).size, "the first thread keeps its sticker")
    }

    @Test
    fun replacingHerKeepsYourLettersAndTakesTheBackupsMemories() = runTest {
        val home = phone()
        val file = home.backUp(BackupRequest.OnePersona(ARIA))
        home.memories.upsert(MemoryEntry(id = "later", personaId = ARIA, content = "Kept after the backup", createdAt = 40, updatedAt = 40, lastAccessedAt = 40))
        home.personas.upsert(home.personas.get(ARIA)!!.copy(personality = "Changed since"))
        home.conversations.appendMessage(ChatMessage(id = "newer", conversationId = THREAD, role = MessageRole.USER, parts = listOf(ContentPart.Text("Still here?")), createdAt = 50))

        val aria = home.backups.restorePersona(file, Backups.WhenHere.REPLACE)
        assertEquals(ARIA, aria.id)
        assertEquals("Aria", aria.name)
        assertEquals("", home.personas.get(ARIA)?.personality, "her page is the backup's")
        assertEquals(listOf(MEMORY), home.memories.listForPersona(ARIA).map { it.id }, "what she kept since is lost")
        assertEquals(listOf(NOTE, REPLY, "newer"), home.conversations.getMessages(THREAD).map { it.id }, "your letters stay")
        assertEquals(2, home.personas.getAll().size)
    }

    @Test
    fun everythingReplacesAllOfIt() = runTest {
        val home = phone()
        val survey = home.backups.survey(BackupRequest.Everything())
        assertEquals(2, survey.count(BackupPart.PERSONAS))
        assertEquals(1, survey.count(BackupPart.YOUR_PERSONAS))
        assertEquals(2, survey.count(BackupPart.MEMORIES))
        assertEquals(2, survey.count(BackupPart.LETTERS))
        assertEquals(1, survey.count(BackupPart.PLANNER))
        assertTrue(survey.total(BackupRequest.Everything()) > survey.total(BackupRequest.Everything(letters = false, pictures = false)))
        val file = home.backUp(BackupRequest.Everything())
        val manifest = assertIs<BackupPeek.Open>(home.backups.peek(file)).manifest
        assertTrue(manifest.everything)
        assertEquals(listOf("Aria", "Juno"), manifest.personas.map { it.name }.sorted())
        assertEquals(2, manifest.letters)
        assertTrue(manifest.planner && manifest.settings)

        val other = Phone().apply {
            key("their-key", CredentialKind.ANTHROPIC)
            personas.upsert(Persona(id = "kit", name = "Kit", createdAt = 1, updatedAt = 1))
            userPersonas.upsert(UserPersona(id = "kai", name = "Kai", createdAt = 1, updatedAt = 1))
            conversations.upsert(Conversation(id = "kit-thread", personaId = "kit", title = "Kit", createdAt = 1, updatedAt = 1))
            conversations.appendMessage(ChatMessage(id = "kit-note", conversationId = "kit-thread", role = MessageRole.USER, parts = listOf(ContentPart.Text("Hi Kit")), createdAt = 1))
            profiles.upsert(ModelProfile(id = "mini", name = "Mini", provider = ProviderType.GEMINI, modelId = "gemini"))
        }
        val goes = assertNotNull(other.backups.preview(file).goes)
        assertEquals(listOf("Kit"), goes.personas)
        assertEquals(1, goes.letters)
        assertEquals(listOf("Kit"), goes.notInBackup)

        other.backups.restoreEverything(file)
        assertEquals(listOf("Aria", "Juno"), other.personas.getAll().map { it.name }.sorted(), "Kit isn't in the backup, so Kit goes")
        assertEquals(listOf("Sam"), other.userPersonas.observeAll().first().map { it.name })
        assertEquals("sam", other.settings.current().activeUserPersonaId)
        assertEquals(listOf(THREAD), other.conversations.getAll().map { it.id })
        assertEquals(2, other.conversations.getMessages(THREAD).size)
        assertTrue(other.conversations.getMessages("kit-thread").isEmpty())
        assertEquals(listOf("Dentist"), other.planner.forPlanner("sam").map { it.title })
        assertEquals(listOf("opus"), other.profiles.getAll().map { it.id }, "settings and models are the backup's")
        assertEquals("their-key", other.profiles.get("opus")?.credentialId, "with this phone's key")
        assertNotNull(other.credentials.get("their-key"), "keys stay")
        assertEquals(1, other.stickers.forConversation(THREAD).size)
        assertEquals(2, other.memories.listForPersona(ARIA).size + other.memories.listForPersona(JUNO).size)
    }

    @Test
    fun aLockedBackupShowsNothingUntilItsPasswordIsGiven() = runTest {
        val home = phone()
        val file = home.backUp(BackupRequest.OnePersona(ARIA), password = "kinako-on-the-sill".toCharArray())
        val bytes = file.readBytes()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("Aria"), "not even her name shows")
        val locked = assertIs<BackupPeek.Locked>(home.backups.peek(file))
        assertEquals("0.5.0", locked.header.app)
        assertEquals(BackupManifest.FORMAT, locked.header.format)

        val opened = tmp.newFile()
        assertFailsWith<Lock.WrongPassword> { home.backups.unlock(file, "kinako".toCharArray(), opened) }
        home.backups.unlock(file, "kinako-on-the-sill".toCharArray(), opened)
        assertEquals("Aria", assertIs<BackupPeek.Open>(home.backups.peek(opened)).manifest.personas.single().name)

        // A changed header (say, its version) fails as a wrong password would.
        val header = String(bytes, Charsets.ISO_8859_1).substringBefore("}\n")
        val changed = tmp.newFile().apply { writeBytes(String(bytes, Charsets.ISO_8859_1).replaceFirst("\"app\":\"0.5.0\"", "\"app\":\"0.5.1\"").toByteArray(Charsets.ISO_8859_1)) }
        assertContains(header, "\"app\":\"0.5.0\"")
        assertFailsWith<Lock.WrongPassword> { home.backups.unlock(changed, "kinako-on-the-sill".toCharArray(), tmp.newFile()) }
    }

    @Test
    fun aLargeLockedFileComesBackWholeAndOneCutShortIsCaught() {
        val plain = Random(5).nextBytes(Lock.SEGMENT * 2 + 1234)
        val locked = ByteArrayOutputStream().also { Lock.encrypt(plain.inputStream(), it, "pw".toCharArray(), 1, "0.5.0", iterations = 1_000) }.toByteArray()
        val back = ByteArrayOutputStream().also { Lock.decrypt(locked.inputStream(), it, "pw".toCharArray()) }.toByteArray()
        assertTrue(plain.contentEquals(back))

        // Cut at the end of its second segment: what's left looks whole, but its last segment isn't marked last.
        val header = Lock.readHeader(locked.inputStream())!!.second.size
        val cut = locked.copyOf(header + 2 * (Lock.SEGMENT + 16))
        assertFailsWith<Lock.Damaged> { Lock.decrypt(cut.inputStream(), ByteArrayOutputStream(), "pw".toCharArray()) }

        // A header asking for an enormous segment or countless rounds is refused, not obeyed.
        val text = String(locked, Charsets.ISO_8859_1)
        for ((field, value) in listOf("\"segment\":${Lock.SEGMENT}" to "\"segment\":2147483000", "\"iterations\":1000" to "\"iterations\":2000000000")) {
            assertContains(text, field)
            val bad = text.replaceFirst(field, value).toByteArray(Charsets.ISO_8859_1)
            assertFailsWith<Lock.Damaged> { Lock.decrypt(bad.inputStream(), ByteArrayOutputStream(), "pw".toCharArray()) }
        }

        val exact = Random(6).nextBytes(Lock.SEGMENT)
        val lockedExact = ByteArrayOutputStream().also { Lock.encrypt(exact.inputStream(), it, "pw".toCharArray(), 1, "0.5.0", iterations = 1_000) }.toByteArray()
        assertTrue(exact.contentEquals(ByteArrayOutputStream().also { Lock.decrypt(lockedExact.inputStream(), it, "pw".toCharArray()) }.toByteArray()))
    }

    @Test
    fun backupsFromNewerVersionsAndFilesThatArentBackupsAreRefused() = runTest {
        val home = phone()
        val newer = tmp.newFile().apply {
            ZipOutputStream(outputStream()).use { z ->
                z.putNextEntry(ZipEntry("manifest.json"))
                z.write(HarnessJson.encodeToString(BackupManifest.serializer(), BackupManifest(format = BackupManifest.FORMAT + 1, app = "0.7.0", madeAt = 0, everything = false, personas = emptyList())).toByteArray())
                z.closeEntry()
            }
        }
        assertEquals("0.7.0", assertIs<BackupPeek.TooNew>(home.backups.peek(newer)).app)

        val charx = tmp.newFile().apply {
            ZipOutputStream(outputStream()).use { z -> z.putNextEntry(ZipEntry("card.json")); z.write("{}".toByteArray()); z.closeEntry() }
        }
        assertIs<BackupPeek.NotABackup>(home.backups.peek(charx))
        assertIs<BackupPeek.NotABackup>(home.backups.peek(tmp.newFile().apply { writeBytes(Png.plain(1, 1, 0)) }))
        assertEquals("aria-2026-05-28.fumibako", home.backups.fileName(BackupRequest.OnePersona(ARIA), locked = false))
        assertEquals("fumibako-2026-05-28-locked.fumibako", home.backups.fileName(BackupRequest.Everything(), locked = true))
    }

    private companion object {
        const val ARIA = "a1a1a1a1-0000-4000-8000-000000000001"
        const val JUNO = "a1a1a1a1-0000-4000-8000-000000000002"
        const val THREAD = "c0c0c0c0-0000-4000-8000-000000000001"
        const val NOTE = "e0e0e0e0-0000-4000-8000-000000000001"
        const val REPLY = "e0e0e0e0-0000-4000-8000-000000000002"
        const val MEMORY = "f0f0f0f0-0000-4000-8000-000000000001"
        const val WAVE = "b0b0b0b0-0000-4000-8000-000000000001"
        const val STUCK = "d0d0d0d0-0000-4000-8000-000000000001"
    }
}
