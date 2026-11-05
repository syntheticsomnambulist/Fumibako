package dev.aiharness.core

import dev.aiharness.core.llm.LlmEvent
import dev.aiharness.core.llm.LlmProvider
import dev.aiharness.core.llm.LlmRequest
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.llm.ProviderRegistry
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.storage.inmemory.InMemoryBlobStore
import dev.aiharness.core.storage.inmemory.InMemoryConversationRepository
import dev.aiharness.core.storage.inmemory.InMemoryCredentialRepository
import dev.aiharness.core.storage.inmemory.InMemoryMemoryRepository
import dev.aiharness.core.storage.inmemory.InMemoryModelProfileRepository
import dev.aiharness.core.storage.inmemory.InMemoryPersonaRepository
import dev.aiharness.core.storage.inmemory.InMemorySecretStore
import dev.aiharness.core.storage.inmemory.InMemorySettingsRepository
import dev.aiharness.core.storage.inmemory.InMemoryUserPersonaRepository
import dev.aiharness.core.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.ZoneId
import java.time.ZoneOffset

/** Starts at 10:00 UTC so the default quiet hours (23:00-08:00) don't interfere. */
class FakeClock(var millis: Long = 1_779_962_400_000L, override val zone: ZoneId = ZoneOffset.UTC) : Clock {
    override fun nowMillis(): Long = millis
    fun advanceMinutes(minutes: Long) {
        millis += minutes * 60_000
    }
}

/** Replays scripted turns and records every request it receives. */
class ScriptedProvider(
    override val type: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    private val script: MutableList<(LlmRequest) -> LlmTurnResult> = mutableListOf(),
    /** Answers requests once the script is exhausted. */
    var fallback: (LlmRequest) -> LlmTurnResult = { LlmTurnResult(text = "ok") },
) : LlmProvider {
    val requests = mutableListOf<LlmRequest>()

    fun then(result: LlmTurnResult) = apply { script += { result } }
    fun then(block: (LlmRequest) -> LlmTurnResult) = apply { script += block }

    override fun stream(request: LlmRequest): Flow<LlmEvent> = flow {
        requests += request
        val next = if (script.isEmpty()) fallback(request) else script.removeAt(0)(request)
        if (next.text.isNotEmpty()) emit(LlmEvent.TextDelta(next.text))
        emit(LlmEvent.Completed(next))
    }

    override suspend fun listModels(profile: ModelProfile, apiKey: String?) = listOf("scripted")
}

class TestEnv(
    val provider: ScriptedProvider = ScriptedProvider(),
    settings: AppSettings = AppSettings(),
) {
    val clock = FakeClock()
    val personas = InMemoryPersonaRepository()
    val userPersonas = InMemoryUserPersonaRepository()
    val profiles = InMemoryModelProfileRepository()
    val conversations = InMemoryConversationRepository()
    val memories = InMemoryMemoryRepository()
    val settingsRepo = InMemorySettingsRepository(settings)
    val logs = mutableListOf<String>()

    val harness = Harness(
        personas = personas,
        userPersonas = userPersonas,
        profiles = profiles,
        credentials = InMemoryCredentialRepository(),
        secrets = InMemorySecretStore(),
        conversations = conversations,
        memories = memories,
        settings = settingsRepo,
        blobs = InMemoryBlobStore(),
        clock = clock,
        providers = ProviderRegistry(listOf(provider)),
        embedderFactory = null,
        log = { logs += it },
    )

    val persona = Persona(
        id = "p1",
        name = "Mira",
        description = "A curious botanist.",
        personality = "Warm, teasing.",
        greeting = "Hi {{user}}!",
        createdAt = clock.millis,
        updatedAt = clock.millis,
    )
    val user = UserPersona(id = "u1", name = "Sam", description = "Likes hiking.", createdAt = clock.millis, updatedAt = clock.millis)
    val profile = ModelProfile(
        id = "m1",
        name = "Test model",
        provider = provider.type,
        modelId = "test-model",
        maxOutputTokens = 1000,
        contextWindowTokens = 100_000,
        contextBudgetTokens = 20_000,
    )

    suspend fun seed() {
        personas.upsert(persona)
        userPersonas.upsert(user)
        profiles.upsert(profile)
        settingsRepo.update { it.copy(activeUserPersonaId = user.id, defaultModelProfileId = profile.id) }
    }
}
