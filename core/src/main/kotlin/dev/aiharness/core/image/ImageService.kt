package dev.aiharness.core.image

import dev.aiharness.core.llm.LlmException
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Credential
import dev.aiharness.core.model.CredentialKind
import dev.aiharness.core.util.newId
import dev.aiharness.core.model.ImageGenSettings
import dev.aiharness.core.model.ImageHost
import dev.aiharness.core.model.ImageProfile
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.storage.BlobStore

/**
 * Draws pictures with an image profile (one you chose, the persona's own, or the default), builds
 * the prompt from the persona's parts in the form its host takes, and stores the result as a blob.
 */
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
        parts: PicturePrompt.Parts = PicturePrompt.Parts(),
        profile: ImageProfile? = null,
        /** You, when [parts] has you in the picture. */
        you: UserPersona? = null,
    ): ContentPart.Image {
        val chosen = profile ?: settings.profileFor(persona?.imageProfileId)
            ?: throw LlmException("Add an image profile in Settings, Pictures, to draw pictures")
        val key = chosen.credentialId?.let { apiKeyFor(it) }?.takeIf { it.isNotBlank() }
        if (chosen.host.needsKey && key == null) throw LlmException("The image profile \"${chosen.name}\" has no key")
        val (width, height) = sizeFor(chosen.width, chosen.height, orientation)
        val form = if (chosen.host.takesTags) PicturePrompt.Form.TAGS else PicturePrompt.Form.SENTENCES
        val fullPrompt = PicturePrompt.build(prompt, persona, settings.defaultStyle, parts, form, you)
        val images = generator.generate(
            ImageRequest(
                prompt = fullPrompt,
                negativePrompt = listOfNotNull(chosen.negativePrompt.takeIf { it.isNotBlank() }, negativePrompt?.takeIf { it.isNotBlank() })
                    .joinToString(", "),
                model = chosen.model.ifBlank { ImageProfiles.defaultModel(chosen.host) },
                width = width,
                height = height,
                steps = chosen.steps,
                scale = chosen.scale,
                sampler = chosen.sampler,
                noiseSchedule = chosen.noiseSchedule,
                qualityToggle = chosen.qualityToggle,
                ucPreset = chosen.ucPreset,
                host = chosen.host,
                baseUrl = chosen.baseUrl,
                quality = chosen.quality,
                workflow = chosen.workflow,
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
        /** Portrait uses the profile's size; landscape swaps it; square keeps the same pixel budget. */
        fun sizeFor(w: Int, h: Int, orientation: String?): Pair<Int, Int> = when (orientation?.lowercase()) {
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

/** Starting points for a new image profile, one per host. */
object ImageProfiles {
    /**
     * When you add a key that draws pictures (NovelAI's), pictures turn on with it: a profile using
     * the key, the default if there isn't one, and the persona's picture tool on. Returns null when
     * there's nothing to do: the key isn't for pictures, or pictures are already set up.
     */
    fun turnOnWith(settings: ImageGenSettings, credential: Credential): ImageGenSettings? {
        val host = when (credential.kind) {
            CredentialKind.NOVELAI -> ImageHost.NOVELAI
            else -> return null
        }
        if (settings.ready) return null
        val existing = settings.profiles.firstOrNull { it.credentialId == credential.id }
        val profile = existing ?: preset(host, newId()).copy(name = host.label, credentialId = credential.id)
        return settings.copy(
            enabled = true,
            agentToolEnabled = true,
            profiles = if (existing != null) settings.profiles else settings.profiles + profile,
            defaultProfileId = settings.defaultProfileId?.takeIf { id -> settings.profiles.any { it.id == id && it.isUsable } } ?: profile.id,
        )
    }

    fun defaultModel(host: ImageHost): String = when (host) {
        ImageHost.NOVELAI -> "nai-diffusion-4-5-full"
        ImageHost.OPENAI -> OpenAiImageGenerator.DEFAULT_MODEL
        ImageHost.GOOGLE -> GoogleImageGenerator.DEFAULT_MODEL
        // The server's current checkpoint (Forge), or the one ComfyUI's workflow names.
        ImageHost.STABLE_DIFFUSION, ImageHost.COMFYUI -> ""
    }

    fun preset(host: ImageHost, id: String): ImageProfile = when (host) {
        ImageHost.NOVELAI -> ImageProfile(id, "NovelAI", host, model = defaultModel(host))
        ImageHost.STABLE_DIFFUSION -> ImageProfile(id, "Forge", host, width = 832, height = 1216, steps = 28, scale = 6.0, sampler = "Euler a", noiseSchedule = "", qualityToggle = false)
        ImageHost.COMFYUI -> ImageProfile(id, "ComfyUI", host, width = 832, height = 1216, steps = 28, scale = 6.0, sampler = "euler_ancestral", noiseSchedule = "karras", qualityToggle = false)
        ImageHost.OPENAI -> ImageProfile(id, "OpenAI", host, model = defaultModel(host), width = 1024, height = 1536, qualityToggle = false)
        ImageHost.GOOGLE -> ImageProfile(id, "Google", host, model = defaultModel(host), width = 896, height = 1200, qualityToggle = false)
    }
}
