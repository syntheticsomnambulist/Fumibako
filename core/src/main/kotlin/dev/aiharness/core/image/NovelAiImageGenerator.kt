package dev.aiharness.core.image

import dev.aiharness.core.llm.HarnessHttp
import dev.aiharness.core.llm.LlmException
import dev.aiharness.core.llm.executeForBytes
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.random.Random

data class ImageRequest(
    val prompt: String,
    val negativePrompt: String = "",
    val model: String,
    val width: Int,
    val height: Int,
    val steps: Int,
    val scale: Double,
    val sampler: String,
    val noiseSchedule: String,
    val qualityToggle: Boolean,
    val ucPreset: String,
    val seed: Long? = null,
    val host: dev.aiharness.core.model.ImageHost = dev.aiharness.core.model.ImageHost.NOVELAI,
    /** A local server's address, or another endpoint for a hosted one. */
    val baseUrl: String? = null,
    /** OpenAI's quality: auto, low, medium, high. */
    val quality: String = "auto",
    /** ComfyUI workflow (API format) with placeholders; null uses the default. */
    val workflow: String? = null,
)

data class GeneratedImage(val bytes: ByteArray, val mimeType: String, val seed: Long)

/** Image backend. [HostImageGenerator] picks one per request's host; tests can supply their own. */
interface ImageGenerator {
    /** [apiKey] is null for hosts that don't need one (local servers). */
    suspend fun generate(request: ImageRequest, apiKey: String?): List<GeneratedImage>
}

/**
 * NovelAI Diffusion via `POST https://image.novelai.net/ai/generate-image`.
 *
 * Requests use `params_version` 4, sent as `multipart/form-data` with the JSON in a `request` part,
 * as NovelAI's web client does. V4+ models get structured `v4_prompt` captions. The response is a ZIP
 * of images, or a bare image for some formats. Quality tags and negative-prompt presets mirror the
 * web client so results match what users see on novelai.net.
 */
class NovelAiImageGenerator(
    private val http: OkHttpClient = HarnessHttp.client,
    private val endpoint: String = DEFAULT_ENDPOINT,
) : ImageGenerator {

    override suspend fun generate(request: ImageRequest, apiKey: String?): List<GeneratedImage> {
        if (apiKey.isNullOrBlank()) throw LlmException("No NovelAI key for this image profile")
        val seed = request.seed ?: Random.nextLong(1, MAX_SEED)
        val json = buildRequestJson(request, seed)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("request", "blob", json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val httpRequest = Request.Builder()
            .url("${(request.baseUrl?.takeIf { it.isNotBlank() } ?: endpoint).trimEnd('/')}/ai/generate-image")
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        val (bytes, contentType) = http.executeForBytes(httpRequest, "NovelAI")
        val images = extractImages(bytes, contentType)
        if (images.isEmpty()) throw LlmException("NovelAI returned no images")
        return images.map { (data, mime) -> GeneratedImage(data, mime, seed) }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://image.novelai.net"
        private const val MAX_SEED = 4_294_967_295L - 7

        val MODELS = listOf(
            "nai-diffusion-4-5-full",
            "nai-diffusion-4-5-curated",
            "nai-diffusion-5-full",
            "nai-diffusion-5-full-medium",
            "nai-diffusion-5-curated",
            "nai-diffusion-4-full",
            "nai-diffusion-3",
        )
        val SAMPLERS = listOf("k_euler_ancestral", "k_euler", "k_dpmpp_2s_ancestral", "k_dpmpp_2m_sde", "k_dpmpp_2m", "k_dpmpp_sde")
        val NOISE_SCHEDULES = listOf("karras", "exponential", "polyexponential")
        val UC_PRESETS = listOf("heavy", "light", "human", "none")

        private enum class Family { V5, V45_FULL, V45_CURATED, V4, V3 }

        private fun familyOf(model: String) = when {
            model.startsWith("nai-diffusion-5") -> Family.V5
            model.startsWith("nai-diffusion-4-5-curated") -> Family.V45_CURATED
            model.startsWith("nai-diffusion-4-5") -> Family.V45_FULL
            model.startsWith("nai-diffusion-4") -> Family.V4
            else -> Family.V3
        }

        private fun qualitySuffix(family: Family) = when (family) {
            Family.V5 -> ", very aesthetic, masterpiece, no text"
            Family.V45_FULL -> ", location, very aesthetic, masterpiece, no text"
            Family.V45_CURATED -> ", location, masterpiece, no text, -0.8::feet::, rating:general"
            Family.V4 -> ", no text, best quality, very aesthetic, absurdres"
            Family.V3 -> ", best quality, amazing quality, very aesthetic, absurdres"
        }

        /** (preset id, negative prompt) in the web client's index order for each family. */
        private fun ucPresets(family: Family): List<Pair<String, String>> = when (family) {
            Family.V5 -> listOf(
                "heavy" to "nsfw, lowres, artistic error, film grain, scan artifacts, worst quality, bad quality, jpeg artifacts, very displeasing, chromatic aberration, dithering, halftone, screentone, multiple views, logo, too many watermarks, negative space, blank page",
                "light" to "nsfw, lowres, bad hands, bad anatomy, artistic error, sepia, white haze, worst quality, very displeasing, jpeg artifacts, 0::ai-generated::",
                "furry" to "nsfw, {worst quality}, distracting watermark, unfinished, bad quality, {widescreen}, upscale, {sequence}, {{grandfathered content}}, blurred foreground, chromatic aberration, sketch, everyone, [sketch background], simple, [flat colors], ych (character), outline, multiple scenes, [[horror (theme)]], comic",
                "human" to "nsfw, lowres, artistic error, film grain, scan artifacts, worst quality, bad quality, jpeg artifacts, very displeasing, chromatic aberration, dithering, halftone, screentone, multiple views, logo, too many watermarks, negative space, blank page, @_@, mismatched pupils, glowing eyes, bad anatomy",
                "none" to "",
            )
            Family.V45_CURATED -> listOf(
                "heavy" to "blurry, lowres, upscaled, artistic error, film grain, scan artifacts, worst quality, bad quality, jpeg artifacts, very displeasing, chromatic aberration, halftone, multiple views, logo, too many watermarks, negative space, blank page",
                "light" to "blurry, lowres, upscaled, artistic error, scan artifacts, jpeg artifacts, logo, too many watermarks, negative space, blank page",
                "human" to "blurry, lowres, upscaled, artistic error, film grain, scan artifacts, bad anatomy, bad hands, worst quality, bad quality, jpeg artifacts, very displeasing, chromatic aberration, halftone, multiple views, logo, too many watermarks, @_@, mismatched pupils, glowing eyes, negative space, blank page",
                "none" to "",
            )
            else -> listOf(
                "heavy" to "nsfw, lowres, artistic error, film grain, scan artifacts, worst quality, bad quality, jpeg artifacts, very displeasing, chromatic aberration, dithering, halftone, screentone, multiple views, logo, too many watermarks, negative space, blank page",
                "light" to "nsfw, lowres, artistic error, scan artifacts, worst quality, bad quality, jpeg artifacts, multiple views, very displeasing, too many watermarks, negative space, blank page",
                "furry" to "nsfw, {worst quality}, distracting watermark, unfinished, bad quality, {widescreen}, upscale, {sequence}, {{grandfathered content}}, blurred foreground, chromatic aberration, sketch, everyone, [sketch background], simple, [flat colors], ych (character), outline, multiple scenes, [[horror (theme)]], comic",
                "human" to "nsfw, lowres, artistic error, film grain, scan artifacts, worst quality, bad quality, jpeg artifacts, very displeasing, chromatic aberration, dithering, halftone, screentone, multiple views, logo, too many watermarks, negative space, blank page, @_@, mismatched pupils, glowing eyes, bad anatomy",
                "none" to "",
            )
        }

        internal fun buildRequestJson(request: ImageRequest, seed: Long): JsonObject {
            val family = familyOf(request.model)
            val prompt = if (request.qualityToggle) request.prompt.trim().trimEnd(',') + qualitySuffix(family) else request.prompt.trim()
            val presets = ucPresets(family)
            val presetIndex = presets.indexOfFirst { it.first == request.ucPreset }.takeIf { it >= 0 } ?: presets.lastIndex
            val presetText = presets[presetIndex].second
            var negative = listOf(presetText, request.negativePrompt.trim()).filter { it.isNotEmpty() }.joinToString(", ")
            if (Regex("\\bnsfw\\b").containsMatchIn(prompt)) {
                negative = negative.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != "nsfw" }.joinToString(", ")
            }
            val isV4Plus = family != Family.V3

            return buildJsonObject {
                put("input", prompt)
                put("model", request.model)
                put("action", "generate")
                put(
                    "parameters",
                    buildJsonObject {
                        put("params_version", 4)
                        put("width", request.width)
                        put("height", request.height)
                        put("scale", request.scale)
                        put("sampler", request.sampler)
                        put("steps", request.steps)
                        put("n_samples", 1)
                        put("ucPreset", presetIndex)
                        put("ucPresetId", presets[presetIndex].first)
                        put("qualityToggle", request.qualityToggle)
                        put("qualityPresetId", if (request.qualityToggle) "standard" else "none")
                        put("autoSmea", false)
                        put("dynamic_thresholding", false)
                        put("controlnet_strength", 1.0)
                        put("legacy", false)
                        put("add_original_image", true)
                        put("cfg_rescale", 0.0)
                        put("noise_schedule", request.noiseSchedule)
                        put("legacy_v3_extend", false)
                        put("skip_cfg_above_sigma", JsonNull)
                        put("use_coords", false)
                        put("legacy_uc", false)
                        put("normalize_reference_strength_multiple", true)
                        put("inpaintImg2ImgStrength", 1.0)
                        put("seed", seed)
                        put("negative_prompt", negative)
                        put("deliberate_euler_ancestral_bug", false)
                        put("prefer_brownian", true)
                        put("characterPrompts", buildJsonArray { })
                        put("image_format", "png")
                        if (family == Family.V5 || family == Family.V45_FULL || family == Family.V45_CURATED) {
                            put("tag_hint_qt", 1)
                            put("tag_hint_uc_preset", 2)
                        }
                        if (family == Family.V5) put("straight_alpha", true)
                        if (isV4Plus) {
                            put(
                                "v4_prompt",
                                buildJsonObject {
                                    put(
                                        "caption",
                                        buildJsonObject {
                                            put("base_caption", prompt)
                                            put("char_captions", buildJsonArray { })
                                        },
                                    )
                                    put("use_coords", false)
                                    put("use_order", true)
                                },
                            )
                            put(
                                "v4_negative_prompt",
                                buildJsonObject {
                                    put(
                                        "caption",
                                        buildJsonObject {
                                            put("base_caption", negative)
                                            put("char_captions", buildJsonArray { })
                                        },
                                    )
                                    put("legacy_uc", false)
                                },
                            )
                        }
                    },
                )
            }
        }

        internal fun extractImages(bytes: ByteArray, contentType: String?): List<Pair<ByteArray, String>> {
            sniffImage(bytes)?.let { return listOf(bytes to it) }
            val images = mutableListOf<Pair<ByteArray, String>>()
            try {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (!entry.isDirectory) {
                            val data = zip.readBytes()
                            sniffImage(data)?.let { images += data to it }
                        }
                    }
                }
            } catch (e: Exception) {
                throw LlmException("NovelAI returned an unreadable response (${contentType ?: "unknown type"}): ${e.message}")
            }
            return images
        }

        private fun sniffImage(data: ByteArray): String? = when {
            data.size >= 8 && data[0] == 0x89.toByte() && data[1] == 'P'.code.toByte() && data[2] == 'N'.code.toByte() -> "image/png"
            data.size >= 3 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() && data[2] == 0xFF.toByte() -> "image/jpeg"
            data.size >= 12 && String(data, 0, 4, Charsets.US_ASCII) == "RIFF" && String(data, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
            else -> null
        }
    }
}
