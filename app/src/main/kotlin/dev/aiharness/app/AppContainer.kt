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
import dev.aiharness.app.data.RoomUserPersonaRepository
import dev.aiharness.app.data.db.HarnessDatabase
import dev.aiharness.app.engine.GenerationManager
import dev.aiharness.app.heartbeat.HeartbeatScheduler
import dev.aiharness.app.heartbeat.Notifier
import dev.aiharness.core.Harness
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.util.newId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Manual dependency container: one instance per process, owned by [HarnessApp]. */
class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = HarnessDatabase.create(context)
    val blobStore = FileBlobStore(context)

    val harness = Harness(
        personas = RoomPersonaRepository(database.dao()),
        userPersonas = RoomUserPersonaRepository(database.dao()),
        profiles = RoomModelProfileRepository(database.dao()),
        credentials = RoomCredentialRepository(database.dao()),
        secrets = KeystoreSecretStore(context),
        conversations = RoomConversationRepository(database.dao()),
        memories = RoomMemoryRepository(database.dao()),
        settings = DataStoreSettingsRepository(context),
        blobs = blobStore,
        activity = RoomActivityRepository(database.dao()),
        log = { Log.d("Harness", it) },
    )

    val generation = GenerationManager(harness, appScope)
    val notifier = Notifier(context)

    fun start() {
        appScope.launch { seedDefaultsOnce() }
        appScope.launch {
            harness.settings.settings.map { it.heartbeat }.distinctUntilChanged().collect { heartbeat ->
                runCatching { HeartbeatScheduler.apply(context, heartbeat) }
                    .onFailure { Log.w("Harness", "Couldn't schedule heartbeat", it) }
            }
        }
    }

    private suspend fun seedDefaultsOnce() {
        val prefs = context.getSharedPreferences("app_state", Context.MODE_PRIVATE)
        if (prefs.getBoolean("seeded", false)) return
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
                appearanceTags = "1girl, solo, long silver hair, green eyes, soft smile, cozy sweater",
                createdAt = now,
                updatedAt = now,
            ),
        )
        harness.settings.update { it.copy(activeUserPersonaId = user.id) }
        prefs.edit().putBoolean("seeded", true).apply()
    }
}
