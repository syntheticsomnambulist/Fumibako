package dev.aiharness.core.tools

import dev.aiharness.core.llm.ToolSpec
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.StickerTarget
import dev.aiharness.core.stickers.StickResult
import dev.aiharness.core.stickers.StickerService
import dev.aiharness.core.stickers.StickerShelf
import dev.aiharness.core.stickers.StickerSpot
import dev.aiharness.core.storage.ConversationRepository
import dev.aiharness.core.storage.PersonaRepository
import dev.aiharness.core.stickers.StickerMaker
import dev.aiharness.core.storage.StickerRepository
import kotlinx.serialization.json.JsonObject

private const val ON_HELP =
    "Where: \"this letter\" (the one you're writing now, the default), \"their note\" (the latest note you're answering), " +
        "\"your last letter\", or a time as the app writes it, like \"Fri 00:13 note\" or \"Fri 00:14 letter\"."

private class Target(val id: String, val role: MessageRole)

private fun ChatMessage.asTarget() = Target(id, role)

/** Finds the letter or note a persona means by [on] (see [ON_HELP]); null when there's no such thing. */
private fun findTarget(on: String?, history: List<ChatMessage>, context: ToolContext, service: StickerService): Target? {
    val visible = history.filter { !it.hidden && !it.excludedFromContext && it.role != MessageRole.TOOL }
    val what = on?.trim()?.lowercase().orEmpty()
    return when {
        what.isEmpty() || what == "this letter" || what == "this" || what == "my letter" ->
            context.messageId?.let { Target(it, MessageRole.ASSISTANT) }
        what.contains("note") && !what.any { it.isDigit() } ->
            visible.lastOrNull { it.role == MessageRole.USER && it.origin == MessageOrigin.USER }?.asTarget()
        what.contains("last letter") || what.contains("previous letter") ->
            visible.lastOrNull { it.role == MessageRole.ASSISTANT && it.id != context.messageId }?.asTarget()
        else -> {
            // A time as the app writes it, maybe with "note" or "letter" to say whose.
            val role = when {
                what.endsWith("note") -> MessageRole.USER
                what.endsWith("letter") -> MessageRole.ASSISTANT
                else -> null
            }
            val time = what.removeSuffix("note").removeSuffix("letter").removePrefix("your ").removePrefix("their ").trim()
            visible.lastOrNull { (role == null || it.role == role) && service.label(it.createdAt).lowercase() == time }?.asTarget()
        }
    }
}

private fun names(shelf: StickerShelf): String = buildString {
    if (shelf.own.isNotEmpty()) append("Yours: ").append(shelf.own.joinToString(" ") { ":${it.name}:" }).append(". ")
    if (shelf.shared.isNotEmpty()) append("Shared: ").append(shelf.shared.joinToString(" ") { ":${it.name}:" }).append(". ")
    append("Or any emoji, by name like :tada: or as the emoji itself.")
}

/** Lets a persona stick a sticker on your note or its own letter, or on a day or plan in your planner, by name. */
class PlaceStickerTool(
    private val service: StickerService,
    private val personas: PersonaRepository,
    private val conversations: ConversationRepository,
    private val log: suspend (personaId: String, kind: ActivityKind, conversationId: String, note: String) -> Unit,
    /** The planner, for stickers on its days and plans (made after the tools). */
    private val planner: () -> dev.aiharness.core.planner.Planner? = { null },
    private val clock: dev.aiharness.core.util.Clock = dev.aiharness.core.util.SystemClock,
) : AgentTool {
    override val spec = ToolSpec(
        name = "place_sticker",
        description = "Stick a sticker on the paper, the way someone decorates a letter: on the note you're answering or on " +
            "your own letter. It's seen as a sticker, not as words. Use one when the moment calls for it, not every time.",
        parameters = schema {
            string("sticker", "Which: one of your stickers or a shared one by name, like :name:, or any emoji.", required = true)
            string("on", ON_HELP)
            string("spot", "Where on the paper (default top right).", enum = StickerSpot.entries.map { it.label })
            string("in_planner", "Instead of a letter, a day (today, tomorrow, a weekday, YYYY-MM-DD) or one of their plans (its title) in the planner you share.")
        },
    )

    override fun isAvailableFor(settings: AppSettings, persona: Persona) = persona.stickers.sticks

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val persona = personas.get(context.persona.id) ?: context.persona
        val asked = args.requireString("sticker")
        val shelf = service.shelf(persona.id)
        val ref = service.resolve(shelf, asked) ?: return ToolOutcome("There's no sticker called $asked. ${names(shelf)}", isError = true)
        val spotText = args.string("spot")
        val spot = if (spotText == null) StickerSpot.TOP_RIGHT else StickerSpot.parse(spotText)
            ?: return ToolOutcome("Spot must be one of: ${StickerSpot.entries.joinToString { it.label }}.", isError = true)
        args.string("in_planner")?.let { where -> return inPlanner(where, ref, spot, persona, context) }
        val history = conversations.getMessages(context.conversation.id)
        val target = findTarget(args.string("on"), history, context, service)
            ?: return ToolOutcome("I couldn't find that letter or note. $ON_HELP", isError = true)
        return when (val result = service.stickAt(context.conversation.id, StickerTarget.message(target.id), ref, persona.id, spot)) {
            is StickResult.Refused -> ToolOutcome(result.reason, isError = true)
            is StickResult.Stuck -> {
                val name = service.nameOf(ref)
                val where = if (target.id == context.messageId) "this letter" else if (target.role == MessageRole.USER) "their note" else "your letter"
                if (context.background && target.role == MessageRole.USER) log(persona.id, ActivityKind.STUCK_STICKER, context.conversation.id, name)
                ToolOutcome("Stuck $name on $where (${spot.label}).")
            }
        }
    }

    /** Onto a day or plan in the planner of the person it writes to, when they share it. */
    private suspend fun inPlanner(where: String, ref: dev.aiharness.core.model.StickerRef, spot: StickerSpot, persona: Persona, context: ToolContext): ToolOutcome {
        val planner = planner() ?: return ToolOutcome("There's no planner here.", isError = true)
        val you = context.userPersona
        if (you == null || !planner.shared(persona, you, context.settings)) return ToolOutcome("Their planner isn't shared with you.", isError = true)
        val today = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(clock.nowMillis()), clock.zone)
        val day = dev.aiharness.core.planner.parseDay(where, today)
        val target = if (day != null) StickerTarget.day(you.id, day.toString()) else {
            val plan = planner.days(you.id, today.minusDays(7), today.plusDays(21))
                .firstOrNull { it.entry.byPersonaId == null && it.entry.title.contains(where, ignoreCase = true) }
                ?: return ToolOutcome("I couldn't find a day or a plan called '$where' in their planner.", isError = true)
            StickerTarget.entry(plan.entry.id)
        }
        return when (val result = service.stickAt(StickerTarget.plannerConversation(you.id), target, ref, persona.id, spot)) {
            is StickResult.Refused -> ToolOutcome(result.reason, isError = true)
            is StickResult.Stuck -> ToolOutcome("Stuck ${service.nameOf(ref)} on ${planner.describeTarget(target) ?: where} in ${you.name.ifBlank { "their" }}'s planner.")
        }
    }
}

/** Lets a persona peel a sticker off: its own always, yours only if you've allowed it. */
class PeelStickerTool(
    private val service: StickerService,
    private val personas: PersonaRepository,
    private val conversations: ConversationRepository,
) : AgentTool {
    override val spec = ToolSpec(
        name = "peel_sticker",
        description = "Peel a sticker off a letter or note. You can always peel off your own; the other person's only if they've allowed it.",
        parameters = schema {
            string("sticker", "Which, by name like :name: or as the emoji.", required = true)
            string("on", ON_HELP)
        },
    )

    override fun isAvailableFor(settings: AppSettings, persona: Persona) = persona.stickers.sticks

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val persona = personas.get(context.persona.id) ?: context.persona
        val asked = args.requireString("sticker")
        val ref = service.resolve(persona.id, asked) ?: return ToolOutcome("There's no sticker called $asked.", isError = true)
        val history = conversations.getMessages(context.conversation.id)
        val target = findTarget(args.string("on"), history, context, service)
            ?: return ToolOutcome("I couldn't find that letter or note. $ON_HELP", isError = true)
        val matching = service.on(context.conversation.id, StickerTarget.message(target.id)).filter { it.ref == ref }
        if (matching.isEmpty()) return ToolOutcome("There's no ${service.nameOf(ref)} on that one.", isError = true)
        val peelable = matching.lastOrNull { service.personaMayPeel(it, persona.id, persona.stickers.canPeelYours) }
            ?: return ToolOutcome("That one isn't yours to peel off.", isError = true)
        service.peel(peelable, persona.id)
        return ToolOutcome("Peeled ${service.nameOf(ref)} off.")
    }
}

/**
 * Lets a persona draw a new sticker of itself for its sheet. In a heartbeat it's one of the things
 * the persona can choose to do, and drawing several takes one slot between them.
 */
class DrawStickerTool(
    private val maker: StickerMaker,
    private val personas: PersonaRepository,
    private val stickers: StickerRepository,
) : AgentTool {
    override val spec = ToolSpec(
        name = "draw_sticker",
        description = "Draw a new sticker of yourself for your sticker sheet: an expression, a pose or a little moment you'd " +
            "like to stick on letters. It's drawn with how you look and your drawing style, and you can use it straight away.",
        parameters = schema {
            string("shows", "What it shows, as short image tags, e.g. \"sleepy, hugging a pillow\" or \"waving, big smile\".", required = true)
            string("name", "Its name, like :yourname-sleepy: (short, lowercase, dashes).")
            string("feeling", "The feeling it stands for, in a word, e.g. sleepy, proud, fond (optional).")
        },
    )

    override fun isAvailable(settings: AppSettings) = settings.imageGen.ready

    override fun isAvailableFor(settings: AppSettings, persona: Persona) = isAvailable(settings) && persona.stickers.draws

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val persona = personas.get(context.persona.id) ?: context.persona
        if (stickers.art(persona.id).size >= StickerMaker.SHEET_SIZE) {
            return ToolOutcome("Your sheet is full (${StickerMaker.SHEET_SIZE} stickers).", isError = true)
        }
        if (context.slots?.take(ACTIVITY) == false) return ToolOutcome("You have no activities left this time.", isError = true)
        val shows = args.requireString("shows").take(200)
        val ref = maker.draw(context.settings.imageGen, persona, shows)
        val art = maker.keep(ref, persona.id, args.string("name") ?: maker.suggestName(persona, shows), args.string("feeling"), prompt = shows)
        return ToolOutcome("Drew :${art.name}: and put it on your sheet. You can stick it now.")
    }

    companion object {
        /** The heartbeat activity drawing belongs to (it takes that activity's slot). */
        const val ACTIVITY = "stickers"
    }
}

