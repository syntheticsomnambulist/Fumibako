package dev.aiharness.core.image

import dev.aiharness.core.llm.HarnessHttp
import dev.aiharness.core.llm.LlmException
import dev.aiharness.core.llm.executeForBytes
import dev.aiharness.core.llm.executeForString
import dev.aiharness.core.llm.toRequestBody
import dev.aiharness.core.model.ImageHost
import dev.aiharness.core.util.HarnessJson
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

/** Sends each request to the generator for its host. */
class HostImageGenerator(
    private val novelAi: ImageGenerator = NovelAiImageGenerator(),
    private val stableDiffusion: ImageGenerator = StableDiffusionImageGenerator(),
    private val comfyUi: ImageGenerator = ComfyUiImageGenerator(),
    private val openAi: ImageGenerator = OpenAiImageGenerator(),
    private val google: ImageGenerator = GoogleImageGenerator(),
) : ImageGenerator {
    override suspend fun generate(request: ImageRequest, apiKey: String?): List<GeneratedImage> = when (request.host) {
        ImageHost.NOVELAI -> novelAi
        ImageHost.STABLE_DIFFUSION -> stableDiffusion
        ImageHost.COMFYUI -> comfyUi
        ImageHost.OPENAI -> openAi
        ImageHost.GOOGLE -> google
    }.generate(request, apiKey)
}

private fun decode(base64: String): ByteArray = Base64.getDecoder().decode(base64.substringAfter("base64,").trim())

private fun mimeOf(bytes: ByteArray): String = when {
    bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
    bytes.size > 11 && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
    else -> "image/png"
}

private fun server(request: ImageRequest, host: String): String =
    request.baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: throw LlmException("$host: set the server's address in the image profile")

/**
 * Forge or Automatic1111, through their web UI's API (`--api`): `POST /sdapi/v1/txt2img`, which
 * answers with base64 images. The checkpoint, when given, is switched for this request only.
 */
class StableDiffusionImageGenerator(private val http: OkHttpClient = HarnessHttp.client) : ImageGenerator {
    override suspend fun generate(request: ImageRequest, apiKey: String?): List<GeneratedImage> {
        val seed = request.seed ?: Random.nextLong(1, Int.MAX_VALUE.toLong())
        val httpRequest = Request.Builder()
            .url("${server(request, NAME)}/sdapi/v1/txt2img")
            .post(body(request, seed).toRequestBody())
            .apply { if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey") }
            .build()
        val images = parse(http.executeForString(httpRequest, NAME))
        if (images.isEmpty()) throw LlmException("$NAME returned no images")
        return images.map { GeneratedImage(it, mimeOf(it), seed) }
    }

    companion object {
        private const val NAME = "Stable Diffusion"
        val SAMPLERS = listOf("Euler a", "Euler", "DPM++ 2M", "DPM++ 2M SDE", "DPM++ SDE", "DDIM")

        internal fun body(request: ImageRequest, seed: Long): JsonObject = buildJsonObject {
            put("prompt", request.prompt)
            put("negative_prompt", request.negativePrompt)
            put("width", request.width)
            put("height", request.height)
            put("steps", request.steps)
            put("cfg_scale", request.scale)
            put("sampler_name", request.sampler)
            if (request.noiseSchedule.isNotBlank()) put("scheduler", request.noiseSchedule)
            put("seed", seed)
            put("batch_size", 1)
            if (request.model.isNotBlank()) putJsonObject("override_settings") { put("sd_model_checkpoint", request.model) }
        }

        internal fun parse(json: String): List<ByteArray> =
            HarnessJson.parseToJsonElement(json).jsonObject["images"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull?.let(::decode) }
    }
}

/**
 * ComfyUI: queues a workflow (its API format) with `POST /prompt`, waits for it in `/history`, and
 * fetches the first picture it saved through `/view`. Placeholders in the workflow's strings are
 * filled in: `%prompt%`, `%negative%`, `%checkpoint%`, `%sampler%`, `%scheduler%`, and (as whole
 * values) `%width%`, `%height%`, `%steps%`, `%cfg%`, `%seed%`.
 */
class ComfyUiImageGenerator(
    private val http: OkHttpClient = HarnessHttp.client,
    private val pollMillis: Long = 1_000,
    private val timeoutMillis: Long = 300_000,
) : ImageGenerator {
    override suspend fun generate(request: ImageRequest, apiKey: String?): List<GeneratedImage> {
        val base = server(request, NAME)
        val seed = request.seed ?: Random.nextLong(1, Int.MAX_VALUE.toLong())
        val workflow = fill(request.workflow?.takeIf { it.isNotBlank() } ?: DEFAULT_WORKFLOW, request, seed)
        val queued = http.executeForString(
            Request.Builder().url("$base/prompt").post(buildJsonObject {
                put("prompt", workflow)
                put("client_id", UUID.randomUUID().toString())
            }.toRequestBody()).build(),
            NAME,
        )
        val promptId = HarnessJson.parseToJsonElement(queued).jsonObject["prompt_id"]?.jsonPrimitive?.contentOrNull
            ?: throw LlmException("$NAME didn't queue the picture: ${queued.take(200)}")
        var waited = 0L
        while (true) {
            val history = http.executeForString(Request.Builder().url("$base/history/$promptId").get().build(), NAME)
            firstImage(history, promptId)?.let { (filename, subfolder, type) ->
                val url = "$base/view".toHttpUrl().newBuilder()
                    .addQueryParameter("filename", filename)
                    .addQueryParameter("subfolder", subfolder)
                    .addQueryParameter("type", type)
                    .build()
                val (bytes, contentType) = http.executeForBytes(Request.Builder().url(url).get().build(), NAME)
                return listOf(GeneratedImage(bytes, contentType?.substringBefore(';')?.takeIf { it.startsWith("image/") } ?: mimeOf(bytes), seed))
            }
            if (waited >= timeoutMillis) throw LlmException("$NAME took too long to draw the picture")
            delay(pollMillis)
            waited += pollMillis
        }
    }

    companion object {
        private const val NAME = "ComfyUI"
        val SAMPLERS = listOf("euler_ancestral", "euler", "dpmpp_2m", "dpmpp_2m_sde", "dpmpp_sde", "ddim")
        val SCHEDULERS = listOf("karras", "normal", "exponential", "sgm_uniform", "simple")

        /** A plain text-to-image workflow: a checkpoint, two prompts, a sampler, and a save. */
        val DEFAULT_WORKFLOW = """
            {
              "3": {"class_type": "KSampler", "inputs": {"seed": "%seed%", "steps": "%steps%", "cfg": "%cfg%", "sampler_name": "%sampler%", "scheduler": "%scheduler%", "denoise": 1, "model": ["4", 0], "positive": ["6", 0], "negative": ["7", 0], "latent_image": ["5", 0]}},
              "4": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": "%checkpoint%"}},
              "5": {"class_type": "EmptyLatentImage", "inputs": {"width": "%width%", "height": "%height%", "batch_size": 1}},
              "6": {"class_type": "CLIPTextEncode", "inputs": {"text": "%prompt%", "clip": ["4", 1]}},
              "7": {"class_type": "CLIPTextEncode", "inputs": {"text": "%negative%", "clip": ["4", 1]}},
              "8": {"class_type": "VAEDecode", "inputs": {"samples": ["3", 0], "vae": ["4", 2]}},
              "9": {"class_type": "SaveImage", "inputs": {"filename_prefix": "fumibako", "images": ["8", 0]}}
            }
        """.trimIndent()

        /** Fills the placeholders in every string of [workflow]; whole-value numbers become numbers. */
        internal fun fill(workflow: String, request: ImageRequest, seed: Long): JsonElement {
            val numbers = mapOf(
                "%width%" to JsonPrimitive(request.width),
                "%height%" to JsonPrimitive(request.height),
                "%steps%" to JsonPrimitive(request.steps),
                "%cfg%" to JsonPrimitive(request.scale),
                "%seed%" to JsonPrimitive(seed),
            )
            val texts = mapOf(
                "%prompt%" to request.prompt,
                "%negative%" to request.negativePrompt,
                "%checkpoint%" to request.model,
                "%sampler%" to request.sampler,
                "%scheduler%" to request.noiseSchedule,
            )
            fun walk(e: JsonElement): JsonElement = when (e) {
                is JsonObject -> JsonObject(e.mapValues { walk(it.value) })
                is JsonArray -> JsonArray(e.map(::walk))
                is JsonPrimitive -> if (e.isString) {
                    numbers[e.content] ?: JsonPrimitive(texts.entries.fold(e.content) { acc, (k, v) -> acc.replace(k, v) })
                } else {
                    e
                }
            }
            val parsed = runCatching { HarnessJson.parseToJsonElement(workflow) }.getOrElse { throw LlmException("$NAME: the workflow isn't valid JSON") }
            return walk(parsed)
        }

        /** The first saved picture in a prompt's history: (filename, subfolder, type). */
        internal fun firstImage(history: String, promptId: String): Triple<String, String, String>? {
            val entry = runCatching { HarnessJson.parseToJsonElement(history).jsonObject[promptId]?.jsonObject }.getOrNull() ?: return null
            val outputs = entry["outputs"]?.jsonObject ?: return null
            for (node in outputs.values) {
                val image = node.jsonObject["images"]?.jsonArray?.firstOrNull()?.jsonObject ?: continue
                val filename = image["filename"]?.jsonPrimitive?.contentOrNull ?: continue
                return Triple(filename, image["subfolder"]?.jsonPrimitive?.contentOrNull.orEmpty(), image["type"]?.jsonPrimitive?.contentOrNull ?: "output")
            }
            return null
        }
    }
}

/**
 * OpenAI's Images API: `POST /v1/images/generations`. GPT image models always answer in base64
 * (`b64_json`); older DALL·E models are asked to. Sizes are the nearest the model takes; there's
 * no negative prompt, so things to avoid are said in words.
 */
class OpenAiImageGenerator(
    private val http: OkHttpClient = HarnessHttp.client,
    private val endpoint: String = "https://api.openai.com",
) : ImageGenerator {
    override suspend fun generate(request: ImageRequest, apiKey: String?): List<GeneratedImage> {
        if (apiKey.isNullOrBlank()) throw LlmException("No OpenAI key for this image profile")
        val base = request.baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: endpoint
        val httpRequest = Request.Builder()
            .url("$base/v1/images/generations")
            .header("Authorization", "Bearer $apiKey")
            .post(body(request).toRequestBody())
            .build()
        val images = parse(http.executeForString(httpRequest, NAME))
        if (images.isEmpty()) throw LlmException("$NAME returned no images")
        return images.map { GeneratedImage(it, mimeOf(it), 0) }
    }

    companion object {
        private const val NAME = "OpenAI"
        const val DEFAULT_MODEL = "gpt-image-1"
        val MODELS = listOf("gpt-image-1", "gpt-image-2", "dall-e-3")
        val QUALITIES = listOf("auto", "low", "medium", "high")

        /** The nearest size GPT image models take: square, portrait or landscape. */
        fun sizeFor(width: Int, height: Int): String = when {
            abs(width - height) < minOf(width, height) / 8 -> "1024x1024"
            height > width -> "1024x1536"
            else -> "1536x1024"
        }

        internal fun body(request: ImageRequest): JsonObject = buildJsonObject {
            val model = request.model.ifBlank { DEFAULT_MODEL }
            val avoid = request.negativePrompt.trim().takeIf { it.isNotEmpty() }?.let { " Avoid: $it." }.orEmpty()
            put("model", model)
            put("prompt", request.prompt + avoid)
            put("n", 1)
            if (model.startsWith("dall-e")) {
                put("size", if (request.height > request.width) "1024x1792" else if (request.width > request.height) "1792x1024" else "1024x1024")
                put("response_format", "b64_json")
            } else {
                put("size", sizeFor(request.width, request.height))
                put("quality", request.quality.ifBlank { "auto" })
            }
        }

        internal fun parse(json: String): List<ByteArray> =
            HarnessJson.parseToJsonElement(json).jsonObject["data"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["b64_json"]?.jsonPrimitive?.contentOrNull?.let(::decode) }
    }
}

/**
 * Google's Gemini API. Gemini image models: `models/{model}:generateContent` with images as the
 * response modality and an aspect ratio. Imagen models: `models/{model}:predict`.
 */
class GoogleImageGenerator(
    private val http: OkHttpClient = HarnessHttp.client,
    private val endpoint: String = "https://generativelanguage.googleapis.com",
) : ImageGenerator {
    override suspend fun generate(request: ImageRequest, apiKey: String?): List<GeneratedImage> {
        if (apiKey.isNullOrBlank()) throw LlmException("No Google key for this image profile")
        val base = request.baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: endpoint
        val model = request.model.ifBlank { DEFAULT_MODEL }
        val imagen = model.startsWith("imagen")
        val httpRequest = Request.Builder()
            .url("$base/v1beta/models/$model:${if (imagen) "predict" else "generateContent"}")
            .header("x-goog-api-key", apiKey)
            .post((if (imagen) imagenBody(request) else geminiBody(request)).toRequestBody())
            .build()
        val json = http.executeForString(httpRequest, NAME)
        val images = if (imagen) parseImagen(json) else parseGemini(json)
        if (images.isEmpty()) throw LlmException("$NAME returned no image (it may have declined the prompt)")
        return images.map { (bytes, mime) -> GeneratedImage(bytes, mime ?: mimeOf(bytes), 0) }
    }

    companion object {
        private const val NAME = "Google"
        const val DEFAULT_MODEL = "gemini-2.5-flash-image"
        val MODELS = listOf("gemini-2.5-flash-image", "gemini-3.1-flash-image-preview", "imagen-4.0-generate-001", "imagen-4.0-fast-generate-001")
        private val RATIOS = listOf("1:1" to 1.0, "3:4" to 0.75, "4:3" to 4.0 / 3, "9:16" to 9.0 / 16, "16:9" to 16.0 / 9)

        /** The nearest aspect ratio both Gemini and Imagen take. */
        fun aspectRatio(width: Int, height: Int): String {
            val r = width.toDouble() / height
            return RATIOS.minBy { abs(it.second - r) }.first
        }

        private fun prompt(request: ImageRequest) =
            request.prompt + (request.negativePrompt.trim().takeIf { it.isNotEmpty() }?.let { " Avoid: $it." }.orEmpty())

        internal fun geminiBody(request: ImageRequest): JsonObject = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject { putJsonArray("parts") { add(buildJsonObject { put("text", prompt(request)) }) } })
            }
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add(JsonPrimitive("IMAGE")) }
                putJsonObject("imageConfig") { put("aspectRatio", aspectRatio(request.width, request.height)) }
            }
        }

        internal fun imagenBody(request: ImageRequest): JsonObject = buildJsonObject {
            put("instances", buildJsonArray { add(buildJsonObject { put("prompt", prompt(request)) }) })
            putJsonObject("parameters") {
                put("sampleCount", 1)
                put("aspectRatio", aspectRatio(request.width, request.height))
            }
        }

        internal fun parseGemini(json: String): List<Pair<ByteArray, String?>> =
            HarnessJson.parseToJsonElement(json).jsonObject["candidates"]?.jsonArray.orEmpty().flatMap { candidate ->
                candidate.jsonObject["content"]?.jsonObject?.get("parts")?.jsonArray.orEmpty().mapNotNull { part ->
                    val inline = (part.jsonObject["inlineData"] ?: part.jsonObject["inline_data"])?.jsonObject ?: return@mapNotNull null
                    val data = inline["data"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    decode(data) to (inline["mimeType"] ?: inline["mime_type"])?.jsonPrimitive?.contentOrNull
                }
            }

        internal fun parseImagen(json: String): List<Pair<ByteArray, String?>> =
            HarnessJson.parseToJsonElement(json).jsonObject["predictions"]?.jsonArray.orEmpty().mapNotNull { p ->
                val data = p.jsonObject["bytesBase64Encoded"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                decode(data) to p.jsonObject["mimeType"]?.jsonPrimitive?.contentOrNull
            }
    }
}
