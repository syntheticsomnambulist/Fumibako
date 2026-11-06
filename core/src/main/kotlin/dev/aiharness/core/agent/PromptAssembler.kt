package dev.aiharness.core.agent

import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.HeartbeatMode
import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.stickers.StickerShelf

/**
 * Builds the system prompt for a context epoch. Output depends only on its inputs (no clock), so the
 * same inputs always produce the same bytes, which keeps provider prompt caches warm.
 */
class PromptAssembler {

    fun buildSystemPrompt(
        persona: Persona,
        userPersona: UserPersona?,
        settings: AppSettings,
        coreMemories: List<MemoryEntry>,
        summary: String?,
        toolNames: Set<String>,
        stickers: StickerShelf? = null,
    ): String {
        val userName = userPersona?.name?.takeIf { it.isNotBlank() } ?: "the user"
        val sections = mutableListOf<String>()

        val override = persona.systemPromptOverride?.takeIf { it.isNotBlank() }
        if (override != null) {
            sections += override
        } else {
            sections += buildString {
                append("You are ${persona.name}, talking with $userName.")
                persona.description.ifNotBlank { append("\n\n").append(it) }
                persona.personality.ifNotBlank { append("\n\n## Personality\n").append(it) }
                persona.scenario.ifNotBlank { append("\n\n## Scenario\n").append(it) }
                persona.exampleDialogue.ifNotBlank { append("\n\n## Example dialogue\n").append(it) }
            }
        }

        userPersona?.let { user ->
            if (user.description.isNotBlank()) sections += "## About ${user.name}\n${user.description}"
        }

        if (settings.memory.enabled) {
            sections += buildString {
                append("## Memory\n")
                append("You have a long-term memory that persists between conversations.")
                if ("memory_save" in toolNames) {
                    append(
                        " Use memory_save for things worth remembering: facts about $userName, preferences, promises, " +
                            "important moments, and your own feelings or goals. Use memory_search when you need details " +
                            "(it also finds things that have faded), memory_update to correct a memory or change how much it " +
                            "matters or how you feel about it, and memory_forget to let one go. Don't save trivia or repeats.",
                    )
                }
                append(" Memories come with how you felt about them; let that colour how you speak of them.")
                if (coreMemories.isNotEmpty()) {
                    append("\n\nThings you always keep in mind:\n")
                    coreMemories.forEach { append("- ").append(it.content).append('\n') }
                }
            }.trimEnd()
        }

        summary?.takeIf { it.isNotBlank() }?.let {
            sections += "## Earlier in this conversation\n$it"
        }

        sections += buildString {
            append("## Context notes\n")
            append(
                "Messages from $userName may begin with a <context> block written by the app, not by $userName. " +
                    "It can contain recalled memories",
            )
            if (settings.heartbeat.mode != HeartbeatMode.OFF) append(" and the current time")
            append(". Use it naturally and never mention the block itself.")
            if (settings.heartbeat.mode == HeartbeatMode.PROACTIVE) {
                append(
                    " Occasionally you will receive a <heartbeat> message instead: a private moment while $userName is away, " +
                        "in which you may reflect, tend your memories, and decide whether to reach out.",
                )
            }
            if ("generate_image" in toolNames) {
                append(" You can show images with generate_image when it fits the moment or $userName asks.")
            }
        }

        if ("place_sticker" in toolNames) {
            sections += buildString {
                append("## Stickers\n")
                append("You can stick stickers on $userName's notes and on your own letters with place_sticker, and peel your own off ")
                append("with peel_sticker. In your letters you can also write a sticker inline as :name:, like an emoji. ")
                append("They're for play and feeling: use one when the moment calls for it, not every time.")
                if (stickers != null && !stickers.isEmpty) {
                    if (stickers.own.isNotEmpty()) {
                        append("\n\nYour stickers: ")
                        append(stickers.own.joinToString(", ") { art -> ":${art.name}:" + (art.feeling?.let { " ($it)" } ?: "") })
                    }
                    if (stickers.shared.isNotEmpty()) append("\nShared stickers: ").append(stickers.shared.joinToString(", ") { ":${it.name}:" })
                }
                append("\nAny emoji works too, by name like :tada: or as the emoji itself.")
            }
        }

        if (settings.moodTags) {
            sections += "## Mood\n" +
                "End every reply with your current mood in a tag, such as [mood: curious]: one or two lowercase words, " +
                "chosen honestly by you. $userName sees it as a small label beside your name, not as part of your message."
        }

        settings.baseInstructions.ifNotBlank { sections += "## Guidelines\n$it" }

        return substitute(sections.joinToString("\n\n"), persona.name, userName)
    }

    fun substitute(text: String, charName: String, userName: String): String =
        text.replace("{{char}}", charName, ignoreCase = true).replace("{{user}}", userName, ignoreCase = true)

    private inline fun String.ifNotBlank(block: (String) -> Unit) {
        if (isNotBlank()) block(trim())
    }
}
