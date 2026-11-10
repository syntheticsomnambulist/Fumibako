package dev.aiharness.core.image

import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.UserPersona

/**
 * Builds a picture's prompt from a persona's three parts (self, the outfit it's wearing, its
 * drawing style), you when you're in it too (how you look and what you wear), and the scene.
 * Written once: tag-based models (NovelAI, Stable Diffusion) get comma-separated tags; models that
 * work from descriptions (OpenAI, Google) get sentences.
 */
object PicturePrompt {
    enum class Form { TAGS, SENTENCES }

    /** Which of the persona's parts go into the picture, and whether [you] are in it. */
    data class Parts(val self: Boolean = true, val outfit: Boolean = true, val style: Boolean = true, val you: Boolean = false) {
        companion object {
            /** The persona isn't in the picture: only its drawing style applies. */
            val STYLE_ONLY = Parts(self = false, outfit = false, style = true)
        }
    }

    /** The style a persona draws in: its own, or the default for everyone. */
    fun styleOf(persona: Persona?, defaultStyle: String): String =
        persona?.stylePrompt?.takeIf { it.isNotBlank() } ?: defaultStyle

    fun build(scene: String, persona: Persona?, defaultStyle: String, parts: Parts = Parts(), form: Form = Form.TAGS, you: UserPersona? = null): String {
        val self = persona?.selfPrompt?.trim().orEmpty().takeIf { parts.self }.orEmpty()
        val outfit = persona?.currentOutfit?.trim().orEmpty().takeIf { parts.outfit }.orEmpty()
        val yourSelf = you?.selfPrompt?.trim().orEmpty().takeIf { parts.you }.orEmpty()
        val yourOutfit = you?.outfit?.trim().orEmpty().takeIf { parts.you }.orEmpty()
        val style = styleOf(persona, defaultStyle).trim().takeIf { parts.style }.orEmpty()
        val what = scene.trim()
        return when (form) {
            // Subjects first (the persona, then you), then the scene, then the style, as tag models expect.
            Form.TAGS -> listOf(self, outfit, yourSelf, yourOutfit, what, style).filter { it.isNotEmpty() }.joinToString(", ")
            Form.SENTENCES -> buildList {
                if (style.isNotEmpty()) add("Drawn in the style of ${style.trimEnd('.')}.")
                if (self.isNotEmpty()) add("${persona?.name ?: "The character"}: ${self.trimEnd('.')}.")
                if (outfit.isNotEmpty()) add("Wearing ${outfit.trimEnd('.')}.")
                if (yourSelf.isNotEmpty() || yourOutfit.isNotEmpty()) {
                    val name = you?.name?.takeIf { it.isNotBlank() } ?: "The other person"
                    val lead = if (self.isNotEmpty() || outfit.isNotEmpty()) "With $name" else name
                    add(
                        listOfNotNull(
                            yourSelf.takeIf { it.isNotEmpty() }?.let { "$lead: ${it.trimEnd('.')}." },
                            yourOutfit.takeIf { it.isNotEmpty() }?.let { (if (yourSelf.isEmpty()) "$lead, w" else "W") + "earing ${it.trimEnd('.')}." },
                        ).joinToString(" "),
                    )
                }
                if (what.isNotEmpty()) add(what.replaceFirstChar { it.uppercase() }.let { if (it.endsWith('.')) it else "$it." })
            }.joinToString(" ")
        }
    }
}
