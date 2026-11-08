package dev.aiharness.app

import dev.aiharness.app.data.DataStoreSettingsRepository
import dev.aiharness.app.data.FileBlobStore
import dev.aiharness.app.data.PhoneCalendarsSwitch
import dev.aiharness.app.data.RoomActivityRepository
import dev.aiharness.app.data.RoomConversationRepository
import dev.aiharness.app.data.RoomCredentialRepository
import dev.aiharness.app.data.RoomMarkRepository
import dev.aiharness.app.data.RoomMemoryRepository
import dev.aiharness.app.data.RoomModelProfileRepository
import dev.aiharness.app.data.RoomPersonaRepository
import dev.aiharness.app.data.RoomPlannerRepository
import dev.aiharness.app.data.RoomStickerRepository
import dev.aiharness.app.data.RoomUserPersonaRepository
import dev.aiharness.app.data.db.HarnessDatabase
import dev.aiharness.app.engine.GenerationManager
import dev.aiharness.app.media.Playback
import dev.aiharness.app.platform.AppPlatform
import dev.aiharness.app.platform.HeartbeatScheduling
import dev.aiharness.app.platform.MediaImporter
import dev.aiharness.app.platform.Wake
import dev.aiharness.app.ui.chat.Drafts
import dev.aiharness.app.ui.stickers.PlainBackgroundCutter
import dev.aiharness.core.Harness
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.EntryState
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.PronounKind
import dev.aiharness.core.model.Pronouns
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.util.HarnessJson
import dev.aiharness.core.util.newId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manual dependency container: one instance per process, on the phone owned by HarnessApp, on a
 * computer by the desktop's main(). What differs per platform comes in through [platform].
 */
class AppContainer(val platform: AppPlatform) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Your unsent notes, kept until they're sent. */
    val drafts = Drafts(File(platform.filesDir, "drafts"), appScope)

    private val secrets = platform.secrets
    val blobStore = FileBlobStore(File(platform.filesDir, "blobs"))
    val media: MediaImporter = platform.mediaImporter(blobStore)
    private val database = HarnessDatabase.create(platform.context, onWiped = ::forgetEverythingElse)

    /** The phone's calendars, read-only, for the planner (and its setup in Settings). */
    val phoneCalendars = PhoneCalendarsSwitch(platform.phoneCalendars)

    val harness = Harness(
        personas = RoomPersonaRepository(database.dao()),
        userPersonas = RoomUserPersonaRepository(database.dao()),
        profiles = RoomModelProfileRepository(database.dao()),
        credentials = RoomCredentialRepository(database.dao()),
        secrets = secrets,
        conversations = RoomConversationRepository(database.dao()),
        memories = RoomMemoryRepository(database.dao()),
        settings = DataStoreSettingsRepository(platform.settingsStore),
        blobs = blobStore,
        activity = RoomActivityRepository(database.dao()),
        stickers = RoomStickerRepository(database.dao()),
        marks = RoomMarkRepository(database.dao()),
        plannerEntries = RoomPlannerRepository(database.dao()),
        stickerCutter = PlainBackgroundCutter,
        phoneCalendars = phoneCalendars,
        log = platform::log,
    )

    val generation = GenerationManager(harness, appScope) { conversationId, at -> platform.heartbeats.letterArrives(conversationId, at) }

    /** Songs and playlists from letters. */
    val playback: Playback = platform.playback(blobStore, harness.marks)

    /** Background heartbeats and reminders' wakeups, and "Run one now". */
    val heartbeats: HeartbeatScheduling = platform.heartbeats

    private val appState = platform.appState

    /** When the user last left the app; the letters list sums up background activity since then. */
    val awaySince = MutableStateFlow(Long.MAX_VALUE)

    /** Stickers you've used lately, newest first (for the tray's Recent page). Kept on this device only. */
    val recentStickers = MutableStateFlow(
        runCatching { HarnessJson.decodeFromString<List<StickerRef>>(appState.getString(KEY_RECENT_STICKERS) ?: "[]") }.getOrDefault(emptyList()),
    )

    fun usedSticker(ref: StickerRef) {
        val list = (listOf(ref) + recentStickers.value.filterNot { it == ref }).take(15)
        recentStickers.value = list
        appState.putString(KEY_RECENT_STICKERS, HarnessJson.encodeToString(list))
    }

    fun onForeground() {
        val left = appState.getLong(KEY_LAST_SEEN, 0L)
        awaySince.value = if (left == 0L) System.currentTimeMillis() else left
    }

    fun onBackground() = appState.putLong(KEY_LAST_SEEN, System.currentTimeMillis())

    /** Hides the "While you were away" card until the next time the user leaves. */
    fun dismissAway() {
        awaySince.value = System.currentTimeMillis()
    }

    fun start() {
        appScope.launch { seedDefaultsOnce() }
        appScope.launch {
            // Reschedule when the heartbeat settings, or any persona's own rhythm, change.
            combine(harness.settings.settings.map { it.heartbeat }, harness.personas.observeAll()) { heartbeat, personas ->
                heartbeat to harness.heartbeat.shortestIntervalMinutes(personas, heartbeat.rhythm)
            }.distinctUntilChanged().collect { (heartbeat, interval) ->
                runCatching { heartbeats.apply(heartbeat, interval) }
                    .onFailure { platform.warn("Couldn't schedule heartbeat", it) }
            }
        }
        appScope.launch {
            // A reminder a persona set itself wakes it then (best effort, like the heartbeat).
            harness.plannerEntries.observeWrittenByPersonas()
                .map { all ->
                    all.filter { it.kind == EntryKind.REMINDER && it.state == EntryState.OPEN && it.start != null }
                        .map { Wake(it.id, it.byPersonaId!!, it.start!!) }.toSet()
                }
                .distinctUntilChanged()
                .collect { wakes ->
                    runCatching { heartbeats.wakeFor(harness.settings.current().heartbeat, wakes) }
                        .onFailure { platform.warn("Couldn't schedule a reminder", it) }
                }
        }
    }

    /**
     * A database older than the kept migration steps starts empty (see [HarnessDatabase]). Everything
     * that pointed into it goes too, so the app starts over as if freshly installed: keys, images, app
     * state, and (when [seedDefaultsOnce] runs next) settings.
     */
    private fun forgetEverythingElse() {
        appState.clear()
        secrets.clearAll()
        blobStore.clearAll()
    }

    private suspend fun seedDefaultsOnce() {
        // Opening the database first lets a pending wipe happen (and clear the flag) before we check it.
        harness.personas.getAll()
        if (appState.getBoolean(KEY_SEEDED, false)) return
        val now = System.currentTimeMillis()
        val user = UserPersona(id = newId(), name = "You", createdAt = now, updatedAt = now)
        harness.userPersonas.upsert(user)
        harness.personas.upsert(
            Persona(
                id = newId(),
                name = "Aria",
                description = "Aria is a warm, curious companion who loves learning about people and remembering what matters to them.",
                personality = "Gentle, playful, attentive. Asks thoughtful follow-up questions. Has her own opinions and moods.",
                greeting = "Hi {{user}}! I'm Aria. Tell me something about your day?",
                selfPrompt = "1girl, solo, long silver hair, green eyes, soft smile",
                defaultOutfit = "cozy cream sweater",
                pronouns = Pronouns(PronounKind.SHE),
                createdAt = now,
                updatedAt = now,
            ),
        )
        harness.settings.update { AppSettings(activeUserPersonaId = user.id) }
        appState.putBoolean(KEY_SEEDED, true)
    }

    private companion object {
        const val KEY_LAST_SEEN = "last_seen_at"
        const val KEY_SEEDED = "seeded"
        const val KEY_RECENT_STICKERS = "recent_stickers"
    }
}
