package dev.aiharness.app.data

import dev.aiharness.core.model.CredentialKind
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.ProviderOptions
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.ToolMode
import dev.aiharness.core.util.newId

/** Starting points for new model profiles. Model ids left blank are picked with "Fetch models". */
data class ModelPreset(val label: String, val build: () -> ModelProfile)

object ModelPresets {
    private fun profile(
        name: String,
        provider: ProviderType,
        modelId: String,
        baseUrl: String? = null,
        maxOutput: Int,
        window: Int,
        budget: Int,
        effort: String? = null,
        toolMode: ToolMode = ToolMode.NATIVE,
        options: ProviderOptions = ProviderOptions(),
    ) = ModelProfile(
        id = newId(),
        name = name,
        provider = provider,
        modelId = modelId,
        baseUrl = baseUrl,
        maxOutputTokens = maxOutput,
        contextWindowTokens = window,
        contextBudgetTokens = budget,
        reasoningEffort = effort,
        toolMode = toolMode,
        options = options,
    )

    val all = listOf(
        ModelPreset("Claude Opus 5.5") {
            profile("Claude Opus 5.5", ProviderType.ANTHROPIC, "claude-opus-5-5", maxOutput = 64_000, window = 1_000_000, budget = 48_000, effort = "medium")
        },
        ModelPreset("Claude Sonnet 5.5") {
            profile("Claude Sonnet 5.5", ProviderType.ANTHROPIC, "claude-sonnet-5-5", maxOutput = 64_000, window = 1_000_000, budget = 48_000, effort = "low")
        },
        ModelPreset("Claude Haiku 5.5") {
            profile("Claude Haiku 5.5", ProviderType.ANTHROPIC, "claude-haiku-5-5", maxOutput = 32_000, window = 1_000_000, budget = 32_000, effort = "low")
        },
        ModelPreset("Google Gemini") {
            profile("Gemini", ProviderType.GEMINI, "", maxOutput = 8_192, window = 1_000_000, budget = 48_000)
        },
        ModelPreset("OpenAI") {
            profile(
                "OpenAI", ProviderType.OPENAI_COMPATIBLE, "", baseUrl = "https://api.openai.com/v1",
                maxOutput = 8_192, window = 128_000, budget = 32_000,
                options = ProviderOptions(openAiMaxTokensField = "max_completion_tokens"),
            )
        },
        ModelPreset("OpenRouter") {
            profile("OpenRouter", ProviderType.OPENAI_COMPATIBLE, "", baseUrl = "https://openrouter.ai/api/v1", maxOutput = 8_192, window = 128_000, budget = 32_000)
        },
        ModelPreset("Local server (Ollama / LM Studio)") {
            profile(
                "Local model", ProviderType.OPENAI_COMPATIBLE, "", baseUrl = "http://192.168.1.10:11434/v1",
                maxOutput = 2_048, window = 32_000, budget = 12_000, toolMode = ToolMode.EMULATED,
                options = ProviderOptions(openAiIncludeUsage = false),
            )
        },
        ModelPreset("NovelAI (GLM)") {
            profile("NovelAI GLM", ProviderType.NOVELAI_TEXT, "glm-4-6", maxOutput = 1_024, window = 32_000, budget = 16_000, toolMode = ToolMode.EMULATED)
        },
        ModelPreset("Custom") {
            profile("Custom model", ProviderType.OPENAI_COMPATIBLE, "", maxOutput = 4_096, window = 32_000, budget = 16_000)
        },
    )

    fun credentialKindFor(provider: ProviderType) = when (provider) {
        ProviderType.ANTHROPIC -> CredentialKind.ANTHROPIC
        ProviderType.OPENAI_COMPATIBLE -> CredentialKind.OPENAI_COMPATIBLE
        ProviderType.GEMINI -> CredentialKind.GEMINI
        ProviderType.NOVELAI_TEXT -> CredentialKind.NOVELAI
    }
}
