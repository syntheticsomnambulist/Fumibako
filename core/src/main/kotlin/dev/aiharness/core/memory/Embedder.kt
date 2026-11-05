package dev.aiharness.core.memory

import dev.aiharness.core.llm.HarnessHttp
import dev.aiharness.core.llm.executeForString
import dev.aiharness.core.llm.toRequestBody
import dev.aiharness.core.model.EmbeddingProviderType
import dev.aiharness.core.model.EmbeddingSettings
import dev.aiharness.core.util.HarnessJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.sqrt

/** Turns text into vectors for semantic memory recall. Optional: recall falls back to keywords. */
interface Embedder {
    /** Identifies the vector space; vectors from different keys are never compared. */
    val modelKey: String
    suspend fun embed(texts: List<String>): List<FloatArray>
}

class OpenAiCompatibleEmbedder(
    private val baseUrl: String,
    private val apiKey: String?,
    private val model: String,
    private val http: OkHttpClient = HarnessHttp.client,
) : Embedder {
    override val modelKey = "openai:$model"

    override suspend fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val body = buildJsonObject {
            put("model", model)
            put("input", buildJsonArray { texts.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
        }
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/embeddings")
            .post(body.toRequestBody())
            .apply { apiKey?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
            .build()
        val root = HarnessJson.parseToJsonElement(http.executeForString(request, "Embeddings")).jsonObject
        val data = root["data"] as? JsonArray ?: return emptyList()
        return data.sortedBy { (it as JsonObject)["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0 }
            .map { item -> (item.jsonObject["embedding"] as JsonArray).toFloatArray() }
    }
}

class GeminiEmbedder(
    private val apiKey: String?,
    private val model: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    private val http: OkHttpClient = HarnessHttp.client,
) : Embedder {
    override val modelKey = "gemini:$model"

    override suspend fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        val modelPath = "models/${model.removePrefix("models/")}"
        val body = buildJsonObject {
            put(
                "requests",
                buildJsonArray {
                    texts.forEach { text ->
                        add(
                            buildJsonObject {
                                put("model", modelPath)
                                put("content", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", text) }) }) })
                            },
                        )
                    }
                },
            )
        }
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/$modelPath:batchEmbedContents")
            .post(body.toRequestBody())
            .apply { apiKey?.takeIf { it.isNotBlank() }?.let { header("x-goog-api-key", it) } }
            .build()
        val root = HarnessJson.parseToJsonElement(http.executeForString(request, "Gemini embeddings")).jsonObject
        return (root["embeddings"] as? JsonArray).orEmpty().map { (it.jsonObject["values"] as JsonArray).toFloatArray() }
    }
}

private fun JsonArray.toFloatArray(): FloatArray = FloatArray(size) { i -> this[i].jsonPrimitive.double.toFloat() }

fun cosine(a: FloatArray, b: FloatArray): Double {
    if (a.size != b.size || a.isEmpty()) return 0.0
    var dot = 0.0
    var na = 0.0
    var nb = 0.0
    for (i in a.indices) {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
    }
    val denom = sqrt(na) * sqrt(nb)
    return if (denom == 0.0) 0.0 else dot / denom
}

/** Builds an [Embedder] from settings, resolving the API key lazily. */
fun interface EmbedderFactory {
    suspend fun create(settings: EmbeddingSettings): Embedder?

    companion object {
        fun default(resolveKey: suspend (credentialId: String?) -> String?) = EmbedderFactory { settings ->
            val key = resolveKey(settings.credentialId)
            when (settings.provider) {
                EmbeddingProviderType.OPENAI_COMPATIBLE -> OpenAiCompatibleEmbedder(
                    baseUrl = settings.baseUrl?.takeIf { it.isNotBlank() } ?: "https://api.openai.com/v1",
                    apiKey = key,
                    model = settings.modelId,
                )
                EmbeddingProviderType.GEMINI -> GeminiEmbedder(apiKey = key, model = settings.modelId)
            }
        }
    }
}
