package dev.aiharness.core.tools

import dev.aiharness.core.image.ImageService
import dev.aiharness.core.llm.ToolSpec
import dev.aiharness.core.memory.MemoryService
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.MemoryKind
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.format.DateTimeFormatter

private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")

class MemorySaveTool(private val memory: MemoryService) : AgentTool {
    override val spec = ToolSpec(
        name = "memory_save",
        description = "Save something to your long-term memory so you can recall it in future conversations. " +
            "Use for facts about the user, preferences, promises, important events, and your own feelings or goals. " +
            "Write one self-contained statement per memory. Near-duplicates are merged automatically.",
        parameters = schema {
            string("content", "The memory, written as a standalone statement (e.g. 'Sam's sister is called Ria').", required = true)
            integer("importance", "How much this matters, 1 (trivial) to 10 (life-defining). Default 5.", min = 1, max = 10)
            stringArray("tags", "A few short topic tags.")
            boolean("core", "True if this should always be in mind (identity, relationship, defining facts). Use sparingly.")
            string(
                "kind",
                "fact (default), reflection (your own thought or conclusion), or episode (something that happened).",
                enum = listOf("fact", "reflection", "episode"),
            )
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
        val result = memory.save(
            personaId = context.persona.id,
            content = args.requireString("content"),
            kind = kind,
            importance = args.int("importance") ?: 5,
            tags = args.stringList("tags"),
            pinned = args.bool("core") == true,
            aboutUserPersonaId = if (args.bool("about_user_persona") == true) context.userPersona?.id else null,
            sourceConversationId = context.conversation.id,
        )
        val verb = if (result.mergedWithExisting) "Updated an existing memory" else "Saved"
        return ToolOutcome("$verb (id ${result.entry.id.take(8)}).")
    }
}

class MemorySearchTool(private val memory: MemoryService) : AgentTool {
    override val spec = ToolSpec(
        name = "memory_search",
        description = "Search your long-term memory. Use when something feels familiar or you need details you may have saved before.",
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
        )
        if (results.isEmpty()) return ToolOutcome("No matching memories.")
        return ToolOutcome(
            results.joinToString("\n") { scored ->
                val e = scored.entry
                val date = DATE.format(Instant.ofEpochMilli(e.createdAt).atZone(java.time.ZoneOffset.UTC))
                "- [id ${e.id.take(8)} | $date | ${e.kind.name.lowercase()} | importance ${e.importance}${if (e.isCore) " | core" else ""}] ${e.content}"
            },
        )
    }
}

class MemoryUpdateTool(private val memory: MemoryService) : AgentTool {
    override val spec = ToolSpec(
        name = "memory_update",
        description = "Correct or change an existing memory, found with memory_search. Use when facts change.",
        parameters = schema {
            string("id", "The memory id (the first 8 characters are enough).", required = true)
            string("content", "The corrected memory text.")
            integer("importance", "New importance, 1 to 10.", min = 1, max = 10)
            boolean("core", "Whether the memory should always be in mind.")
        },
    )

    override fun isAvailable(settings: AppSettings) = settings.memory.enabled && settings.memory.memoryToolsEnabled

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val entry = resolve(memory, context.persona.id, args.requireString("id"))
            ?: return ToolOutcome("No memory with that id.", isError = true)
        memory.update(entry.id, content = args.string("content"), importance = args.int("importance"), pinned = args.bool("core"))
        return ToolOutcome("Memory updated.")
    }
}

class MemoryForgetTool(private val memory: MemoryService) : AgentTool {
    override val spec = ToolSpec(
        name = "memory_forget",
        description = "Delete a memory that is wrong or that the user asked you to forget.",
        parameters = schema {
            string("id", "The memory id (the first 8 characters are enough).", required = true)
        },
    )

    override fun isAvailable(settings: AppSettings) = settings.memory.enabled && settings.memory.memoryToolsEnabled

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val entry = resolve(memory, context.persona.id, args.requireString("id"))
            ?: return ToolOutcome("No memory with that id.", isError = true)
        memory.delete(entry.id)
        return ToolOutcome("Memory deleted.")
    }
}

private suspend fun resolve(memory: MemoryService, personaId: String, idOrPrefix: String) =
    memory.all(personaId).firstOrNull { it.id == idOrPrefix } ?: memory.all(personaId).singleOrNull { it.id.startsWith(idOrPrefix) }

class GenerateImageTool(private val images: ImageService) : AgentTool {
    override val spec = ToolSpec(
        name = "generate_image",
        description = "Create an image and show it to the user, e.g. a selfie, a scene you are describing, or something they asked to see. " +
            "Write the prompt as comma-separated image tags (subject, appearance, clothing, pose, setting, lighting, style).",
        parameters = schema {
            string("prompt", "Comma-separated tags describing the image.", required = true)
            string("negative_prompt", "Things to avoid in the image (optional).")
            string("orientation", "Image shape.", enum = listOf("portrait", "landscape", "square"))
            boolean("include_self", "True if you appear in the image; your appearance tags are added automatically.")
        },
    )

    override fun isAvailable(settings: AppSettings) =
        settings.imageGen.enabled && settings.imageGen.agentToolEnabled && settings.imageGen.credentialId != null

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val prompt = args.requireString("prompt")
        val includeSelf = args.bool("include_self") == true
        val image = images.generate(
            settings = context.settings.imageGen,
            prompt = prompt,
            negativePrompt = args.string("negative_prompt"),
            orientation = args.string("orientation"),
            persona = if (includeSelf) context.persona else null,
        )
        return ToolOutcome(
            content = "The image was generated and shown to ${context.userPersona?.name ?: "the user"}.",
            attachments = listOf(image),
        )
    }
}
