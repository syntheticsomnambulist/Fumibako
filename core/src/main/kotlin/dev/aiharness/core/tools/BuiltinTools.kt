package dev.aiharness.core.tools

import dev.aiharness.core.image.ImageService
import dev.aiharness.core.image.PicturePrompt
import dev.aiharness.core.llm.ToolSpec
import dev.aiharness.core.memory.FeelingChange
import dev.aiharness.core.memory.MemoryService
import dev.aiharness.core.memory.RecallMode
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.MemoryLevel
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.format.DateTimeFormatter

private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")

class MemorySaveTool(private val memory: MemoryService) : AgentTool {
    override val spec = ToolSpec(
        name = "memory_save",
        description = "Keep something in your long-term memory so you can recall it in future conversations: facts about the user, " +
            "preferences, promises, things that happened, and your own feelings or thoughts. Write one self-contained statement per " +
            "memory. Near-duplicates are merged automatically. Small things fade over time unless they come up again; things that " +
            "matter deeply stay.",
        parameters = schema {
            string("content", "The memory, written as a standalone statement (e.g. 'Sam's sister is called Ria').", required = true)
            string(
                "weight",
                "How much it matters. in_passing: a passing detail, fine if it fades (what they had for lunch). " +
                    "worth_keeping (default): worth remembering. matters_deeply: changes how you see them or the two of you; you'd " +
                    "be upset to forget it (a loss, a confession, a big decision). Keep matters_deeply rare.",
                enum = listOf("in_passing", "worth_keeping", "matters_deeply"),
            )
            string("mood", "How you feel about it, in a word or two (e.g. 'proud', 'a bit worried'). Defaults to your mood in this reply.")
            string(
                "kind",
                "fact (default), reflection (your own thought or conclusion), or episode (something that happened).",
                enum = listOf("fact", "reflection", "episode"),
            )
            boolean("core", "True to keep it always in mind (identity, the relationship, defining facts). Use sparingly.")
            string("replaces", "The id of an older memory this one replaces, when a fact has changed (e.g. they moved house). The old one is kept as history.")
            stringArray("tags", "A few short topic tags.")
            boolean("about_user_persona", "True if this is specifically about the user's current persona.")
        },
    )

    override fun isAvailable(settings: AppSettings) = settings.memory.enabled && settings.memory.memoryToolsEnabled

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val kind = when (args.string("kind")?.lowercase()) {
            "reflection" -> MemoryKind.REFLECTION
            "episode" -> MemoryKind.EPISODE
            else -> MemoryKind.FACT
        }
        val replaces = args.string("replaces")?.let { resolve(memory, context.persona.id, it)?.id }
        val mood = args.string("mood")
        val result = memory.save(
            personaId = context.persona.id,
            content = args.requireString("content"),
            kind = kind,
            level = MemoryLevel.parse(args.string("weight") ?: args.string("importance")) ?: MemoryLevel.WORTH_KEEPING,
            tags = args.stringList("tags"),
            pinned = args.bool("core") == true,
            aboutUserPersonaId = if (args.bool("about_user_persona") == true) context.userPersona?.id else null,
            sourceConversationId = context.conversation.id,
            sourceMessageId = context.messageId,
            feltMood = mood,
            feltMoodChosen = mood != null,
            replaces = replaces,
        )
        val id = result.entry.id.take(8)
        val text = buildString {
            append(if (result.mergedWithExisting) "You already remembered this; updated it (id $id)." else "Kept (id $id).")
            result.replaced?.let { append(" Marked ${it.id.take(8)} as replaced; it stays as history.") }
        }
        return ToolOutcome(text)
    }
}

class MemorySearchTool(private val memory: MemoryService) : AgentTool {
    override val spec = ToolSpec(
        name = "memory_search",
        description = "Search your long-term memory, including things that have faded. Use when something feels familiar or you " +
            "need details you may have kept before. Finding a memory refreshes it.",
        parameters = schema {
            string("query", "What to look for, in natural language or keywords.", required = true)
            integer("limit", "Maximum results (default 8).", min = 1, max = 20)
        },
    )

    override fun isAvailable(settings: AppSettings) = settings.memory.enabled && settings.memory.memoryToolsEnabled

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val results = memory.retrieve(
            personaId = context.persona.id,
            userPersonaId = context.userPersona?.id,
            query = args.requireString("query"),
            topK = args.int("limit") ?: 8,
            minRelevance = 0.05,
            mode = RecallMode.SEARCH,
        )
        if (results.isEmpty()) return ToolOutcome("No matching memories.")
        return ToolOutcome(results.joinToString("\n") { describe(it.entry) })
    }

    private fun describe(e: MemoryEntry): String {
        val date = DATE.format(Instant.ofEpochMilli(e.createdAt).atZone(java.time.ZoneOffset.UTC))
        val notes = buildList {
            add("id ${e.id.take(8)}")
            add(date)
            add(e.kind.name.lowercase())
            add(e.level.label.lowercase())
            feelingNote(e)?.let { add(it) }
            if (e.isCore) add("always in mind")
            if (e.isReplaced) add("replaced by ${e.replacedById?.take(8)}")
        }
        return "- [${notes.joinToString(" | ")}] ${e.content}"
    }
}

class MemoryUpdateTool(private val memory: MemoryService) : AgentTool {
    override val spec = ToolSpec(
        name = "memory_update",
        description = "Change an existing memory found with memory_search or recalled just now: correct it, change how much it " +
            "matters, or change how you feel about it. Feelings often soften as things resolve; change one only if something " +
            "has happened or you see it differently now, and give the reason. How it felt at first is always kept.",
        parameters = schema {
            string("id", "The memory id (the first 8 characters are enough).", required = true)
            string("content", "The corrected memory text.")
            string("weight", "How much it matters now: in_passing, worth_keeping or matters_deeply.", enum = listOf("in_passing", "worth_keeping", "matters_deeply"))
            string("mood", "How you feel about it now, in a word or two.")
            string("reason", "Why the feeling changed, in one short sentence. Required with mood.")
            boolean("core", "Whether the memory should always be in mind.")
        },
    )

    override fun isAvailable(settings: AppSettings) = settings.memory.enabled && settings.memory.memoryToolsEnabled

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val entry = resolve(memory, context.persona.id, args.requireString("id"))
            ?: return ToolOutcome("No memory with that id.", isError = true)
        val notes = mutableListOf<String>()
        var failed = false
        val content = args.string("content")
        val level = MemoryLevel.parse(args.string("weight") ?: args.string("importance"))
        val core = args.bool("core")
        if (content != null || level != null || core != null) {
            memory.update(entry.id, content = content, level = level, pinned = core)
            notes += "Memory updated."
        }
        args.string("mood")?.let { mood ->
            when (val change = memory.reviseFeeling(entry.id, mood, args.string("reason"))) {
                is FeelingChange.Changed -> notes += if (change.entry.mood == null) "It feels as it did at first again." else "Now feels ${change.entry.mood}."
                FeelingChange.NeedsReason -> { notes += "To change how it feels, give a short reason."; failed = true }
                FeelingChange.NotFresh -> { notes += "You can only change how a memory feels when it has just come up (recalled or searched for)."; failed = true }
                is FeelingChange.TooSoon -> {
                    notes += "You changed how this feels recently; let it rest until ${DATE.format(Instant.ofEpochMilli(change.nextAllowedAt).atZone(java.time.ZoneOffset.UTC))}."
                    failed = true
                }
                FeelingChange.NotFound -> { notes += "No memory with that id."; failed = true }
            }
        }
        if (notes.isEmpty()) return ToolOutcome("Nothing to change: give content, weight, mood or core.", isError = true)
        return ToolOutcome(notes.joinToString(" "), isError = failed && notes.size == 1)
    }
}

class MemoryForgetTool(private val memory: MemoryService) : AgentTool {
    override val spec = ToolSpec(
        name = "memory_forget",
        description = "Let a memory go for good: when it is wrong, or the user asked you to forget it. To record that a fact " +
            "changed, keep the new one with memory_save and 'replaces' instead.",
        parameters = schema {
            string("id", "The memory id (the first 8 characters are enough).", required = true)
        },
    )

    override fun isAvailable(settings: AppSettings) = settings.memory.enabled && settings.memory.memoryToolsEnabled

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val entry = resolve(memory, context.persona.id, args.requireString("id"))
            ?: return ToolOutcome("No memory with that id.", isError = true)
        memory.delete(entry.id)
        return ToolOutcome("Let go.")
    }
}

/** "felt fond", or "felt anxious, now hopeful", for recall and search results. */
fun feelingNote(e: MemoryEntry): String? {
    val felt = e.feltMood
    val now = e.mood
    return when {
        felt != null && now != null && now != felt -> "felt $felt, now $now"
        now != null -> "feels $now"
        felt != null -> "felt $felt"
        else -> null
    }
}

private suspend fun resolve(memory: MemoryService, personaId: String, idOrPrefix: String) =
    memory.all(personaId).firstOrNull { it.id == idOrPrefix } ?: memory.all(personaId).singleOrNull { it.id.startsWith(idOrPrefix) }

class GenerateImageTool(private val images: ImageService, private val personas: dev.aiharness.core.storage.PersonaRepository) : AgentTool {
    override val spec = ToolSpec(
        name = "generate_image",
        description = "Create an image and show it to the user, e.g. a selfie, a scene you are describing, or something they asked to see. " +
            "Write the prompt as comma-separated image tags (subject, appearance, clothing, pose, setting, lighting, style).",
        parameters = schema {
            string("prompt", "Comma-separated tags describing the image.", required = true)
            string("negative_prompt", "Things to avoid in the image (optional).")
            string("orientation", "Image shape.", enum = listOf("portrait", "landscape", "square"))
            boolean("include_self", "True if you appear in the image; how you look and what you're wearing are added automatically.")
            boolean("include_user", "True if the person you're writing to appears in the image (with you, or alone); how they look is added automatically.")
        },
    )

    override fun isAvailable(settings: AppSettings) =
        settings.imageGen.agentToolEnabled && settings.imageGen.ready

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val prompt = args.requireString("prompt")
        val includeSelf = args.bool("include_self") == true
        val includeUser = args.bool("include_user") == true
        // Read fresh: the persona may have changed clothes earlier in this turn.
        val persona = personas.get(context.persona.id) ?: context.persona
        val image = images.generate(
            settings = context.settings.imageGen,
            prompt = prompt,
            negativePrompt = args.string("negative_prompt"),
            orientation = args.string("orientation"),
            // Its drawing style applies whether or not it's in the picture.
            persona = persona,
            parts = (if (includeSelf) PicturePrompt.Parts() else PicturePrompt.Parts.STYLE_ONLY).copy(you = includeUser),
            you = context.userPersona,
        )
        return ToolOutcome(
            content = "The image was generated and shown to ${context.userPersona?.name ?: "the user"}.",
            attachments = listOf(image),
        )
    }
}
