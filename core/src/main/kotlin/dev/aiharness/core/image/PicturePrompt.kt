package dev.aiharness.core.image

import dev.aiharness.core.model.Persona

/**
 * Builds a picture's prompt from a persona's three parts (self, the outfit it's wearing, its
 * drawing style) and the scene. Written once: tag-based models (NovelAI, Stable Diffusion) get
 * comma-separated tags; models that work from descriptions (OpenAI, Google) get sentences.
 */
object PicturePrompt {
    enum class Form { TAGS, SENTENCES }

    /** Which of the persona's parts go into the picture. */
    data class Parts(val self: Boolean = true, val outfit: Boolean = true, val style: Boolean = true) {
        companion object {
            /** The persona isn't in the picture: only its drawing style applies. */
            val STYLE_ONLY = Parts(self = false, outfit = false, style = true)
        }
    }

    /** The style a persona draws in: its own, or the default for everyone. */
    fun styleOf(persona: Persona?, defaultStyle: String): String =
        persona?.stylePrompt?.takeIf { it.isNotBlank() } ?: defaultStyle

    fun build(scene: String, persona: Persona?, defaultStyle: String, parts: Parts = Parts(), form: Form = Form.TAGS): String {
        val self = persona?.selfPrompt?.trim().orEmpty().takeIf { parts.self }.orEmpty()
        val outfit = persona?.currentOutfit?.trim().orEmpty().takeIf { parts.outfit }.orEmpty()
        val style = styleOf(persona, defaultStyle).trim().takeIf { parts.style }.orEmpty()
        val what = scene.trim()
        return when (form) {
            // Subject first, then the scene, then the style, as tag models expect.
            Form.TAGS -> listOf(self, outfit, what, style).filter { it.isNotEmpty() }.joinToString(", ")
            Form.SENTENCES -> buildList {
                if (style.isNotEmpty()) add("Drawn in the style of ${style.trimEnd('.')}.")
                if (self.isNotEmpty()) add("${persona?.name ?: "The character"}: ${self.trimEnd('.')}.")
                if (outfit.isNotEmpty()) add("Wearing ${outfit.trimEnd('.')}.")
                if (what.isNotEmpty()) add(what.replaceFirstChar { it.uppercase() }.let { if (it.endsWith('.')) it else "$it." })
            }.joinToString(" ")
        }
    }
}
