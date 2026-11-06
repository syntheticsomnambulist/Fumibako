package dev.aiharness.app

import android.content.Context
import android.util.Log
import dev.aiharness.app.data.DataStoreSettingsRepository
import dev.aiharness.app.data.FileBlobStore
import dev.aiharness.app.data.KeystoreSecretStore
import dev.aiharness.app.data.RoomActivityRepository
import dev.aiharness.app.data.RoomConversationRepository
import dev.aiharness.app.data.RoomCredentialRepository
import dev.aiharness.app.data.RoomMemoryRepository
import dev.aiharness.app.data.RoomModelProfileRepository
import dev.aiharness.app.data.RoomPersonaRepository
import dev.aiharness.app.data.RoomStickerRepository
import dev.aiharness.app.data.RoomUserPersonaRepository
import dev.aiharness.app.data.db.HarnessDatabase
import dev.aiharness.app.engine.GenerationManager
import dev.aiharness.app.heartbeat.HeartbeatScheduler
import dev.aiharness.app.heartbeat.Notifier
import dev.aiharness.app.ui.stickers.PlainBackgroundCutter
import dev.aiharness.core.Harness
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.util.newId
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.util.HarnessJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Manual dependency container: one instance per process, owned by [HarnessApp]. */
class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val secrets = KeystoreSecretStore(context)
    val blobStore = FileBlobStore(context)
    private val database = HarnessDatabase.create(context, onWiped = ::forgetEverythingElse)

    val harness = Harness(
        personas = RoomPersonaRepository(database.dao()),
        userPersonas = RoomUserPersonaRepository(database.dao()),
        profiles = RoomModelProfileRepository(database.dao()),
        credentials = RoomCredentialRepository(database.dao()),
        secrets = secrets,
        conversations = RoomConversationRepository(database.dao()),
        memories = RoomMemoryRepository(database.dao()),
        settings = DataStoreSettingsRepository(context),
        blobs = blobStore,
        activity = RoomActivityRepository(database.dao()),
        stickers = RoomStickerRepository(database.dao()),
        stickerCutter = PlainBackgroundCutter,
        log = { Log.d("Harness", it) },
    )

    val generation = GenerationManager(harness, appScope)

    private val appState = context.getSharedPreferences("app_state", Context.MODE_PRIVATE)

    /** When the user last left the app; the letters list sums up background activity since then. */
    val awaySince = MutableStateFlow(Long.MAX_VALUE)

    /** Stickers you've used lately, newest first (for the tray's Recent page). Kept on this phone only. */
    val recentStickers = MutableStateFlow(
        runCatching { HarnessJson.decodeFromString<List<StickerRef>>(appState.getString(KEY_RECENT_STICKERS, null) ?: "[]") }.getOrDefault(emptyList()),
    )

    fun usedSticker(ref: StickerRef) {
        val list = (listOf(ref) + recentStickers.value.filterNot { it == ref }).take(15)
        recentStickers.value = list
        appState.edit().putString(KEY_RECENT_STICKERS, HarnessJson.encodeToString(list)).apply()
    }

    fun onForeground() {
        val left = appState.getLong(KEY_LAST_SEEN, 0L)
        awaySince.value = if (left == 0L) System.currentTimeMillis() else left
    }

    fun onBackground() = appState.edit().putLong(KEY_LAST_SEEN, System.currentTimeMillis()).apply()

    /** Hides the "While you were away" card until the next time the user leaves. */
    fun dismissAway() {
        awaySince.value = System.currentTimeMillis()
    }
    val notifier = Notifier(context)

    fun start() {
        appScope.launch { seedDefaultsOnce() }
        appScope.launch {
            // Reschedule when the heartbeat settings, or any persona's own rhythm, change.
            combine(harness.settings.settings.map { it.heartbeat }, harness.personas.observeAll()) { heartbeat, personas ->
                heartbeat to harness.heartbeat.shortestIntervalMinutes(personas, heartbeat.rhythm)
            }.distinctUntilChanged().collect { (heartbeat, interval) ->
                runCatching { HeartbeatScheduler.apply(context, heartbeat, interval) }
                    .onFailure { Log.w("Harness", "Couldn't schedule heartbeat", it) }
            }
        }
    }

    /**
     * A database older than the kept migration steps starts empty (see [HarnessDatabase]). Everything
     * that pointed into it goes too, so the app starts over as if freshly installed: keys, images, app
     * state, and (when [seedDefaultsOnce] runs next) settings.
     */
    private fun forgetEverythingElse() {
        appState.edit().clear().commit()
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
                createdAt = now,
                updatedAt = now,
            ),
        )
        harness.settings.update { AppSettings(activeUserPersonaId = user.id) }
        appState.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    private companion object {
        const val KEY_LAST_SEEN = "last_seen_at"
        const val KEY_SEEDED = "seeded"
        const val KEY_RECENT_STICKERS = "recent_stickers"
    }
}
