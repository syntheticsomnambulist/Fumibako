package dev.aiharness.core.image

import dev.aiharness.core.llm.LlmException
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.ImageGenSettings
import dev.aiharness.core.model.Persona
import dev.aiharness.core.storage.BlobStore

/** Generates images with the configured backend and stores them as blobs for the chat UI. */
class ImageService(
    private val generator: ImageGenerator,
    private val blobs: BlobStore,
    private val apiKeyFor: suspend (credentialId: String?) -> String?,
) {
    suspend fun generate(
        settings: ImageGenSettings,
        prompt: String,
        negativePrompt: String? = null,
        orientation: String? = null,
        persona: Persona? = null,
    ): ContentPart.Image {
        val key = apiKeyFor(settings.credentialId)?.takeIf { it.isNotBlank() }
            ?: throw LlmException("No NovelAI API key configured for image generation")
        val (width, height) = sizeFor(settings, orientation)
        val fullPrompt = listOfNotNull(persona?.appearanceTags?.takeIf { it.isNotBlank() }, prompt.trim())
            .joinToString(", ")
        val images = generator.generate(
            ImageRequest(
                prompt = fullPrompt,
                negativePrompt = listOfNotNull(settings.negativePrompt.takeIf { it.isNotBlank() }, negativePrompt?.takeIf { it.isNotBlank() })
                    .joinToString(", "),
                model = settings.model,
                width = width,
                height = height,
                steps = settings.steps,
                scale = settings.scale,
                sampler = settings.sampler,
                noiseSchedule = settings.noiseSchedule,
                qualityToggle = settings.qualityToggle,
                ucPreset = settings.ucPreset,
            ),
            key,
        )
        val image = images.first()
        val ext = when (image.mimeType) {
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            else -> "png"
        }
        val ref = blobs.put(image.bytes, ext)
        return ContentPart.Image(ref = ref, mimeType = image.mimeType, caption = fullPrompt)
    }

    companion object {
        /** Portrait uses the configured size; landscape swaps it; square keeps the same pixel budget. */
        fun sizeFor(settings: ImageGenSettings, orientation: String?): Pair<Int, Int> {
            val w = settings.width
            val h = settings.height
            return when (orientation?.lowercase()) {
                "landscape" -> maxOf(w, h) to minOf(w, h)
                "square" -> {
                    val side = (Math.sqrt((w * h).toDouble()) / 64).toInt() * 64
                    side to side
                }
                "portrait" -> minOf(w, h) to maxOf(w, h)
                else -> w to h
            }
        }
    }
}
