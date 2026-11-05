package dev.aiharness.core.agent

import dev.aiharness.core.Harness
import dev.aiharness.core.memory.Feeling
import dev.aiharness.core.memory.FeelingFamily
import dev.aiharness.core.memory.FeelingWords
import dev.aiharness.core.memory.Intensity
import dev.aiharness.core.memory.MemoryService
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.HeartbeatRhythm
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.MemoryLevel
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.Persona
import dev.aiharness.core.tools.feelingNote
import dev.aiharness.core.util.HarnessJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One thing a persona can do when it wakes. A heartbeat runs every activity's [prepare] (upkeep,
 * no model voice), then gathers their [invite]s into a single private moment with the persona
 * (only if something is due, or it may write to the user), then calls [after] with what happened.
 *
 * This is where the persona's "home" grows: a new pastime is a new activity, registered on
 * [HeartbeatEngine.activities], with its own conditions and log entries.
 */
interface HeartbeatActivity {
    /** Short id, recorded on the moment's log entry when this activity took part. */
    val name: String

    suspend fun prepare(beat: Beat) {}

    /** A paragraph for the private moment's note, or null when nothing is due. */
    suspend fun invite(beat: Beat): String? = null

    suspend fun after(beat: Beat, moment: Moment?) {}
}

/** What one heartbeat is working with. Activities can update [setup] and [history]. */
class Beat internal constructor(
    val h: Harness,
    val persona: Persona,
    var setup: TurnSetup,
    var history: List<ChatMessage>,
    val now: Long,
    val rhythm: HeartbeatRhythm,
) {
    val userName: String get() = setup.userPersona?.name ?: "the user"

    /** Memory ids put in front of the persona during this beat. */
    val shownMemoryIds = mutableSetOf<String>()

    /** Activities that invited something into the private moment. */
    val invited = mutableListOf<String>()

    /** Recent activity log for this persona (last two weeks), oldest first. */
    var recentActivity: List<dev.aiharness.core.model.ActivityEntry> = emptyList()
        internal set

    fun lastActivity(kind: ActivityKind) = recentActivity.lastOrNull { it.kind == kind }?.createdAt

    /** Last private moment that included [activity], for cooldowns. */
    fun lastInvited(activity: String) =
        recentActivity.lastOrNull { (it.kind == ActivityKind.WROTE_LETTER || it.kind == ActivityKind.STAYED_QUIET) && activity in it.tags }?.createdAt
}

/** The private moment: the turn, and each tool call with its result. */
class Moment(val outcome: TurnOutcome, val wroteLetter: Boolean) {
    val calls: List<Pair<ContentPart.ToolCall, ContentPart.ToolResult?>> = run {
        val results = outcome.messages.flatMap { it.toolResults }.associateBy { it.callId }
        outcome.messages.filter { it.role == MessageRole.ASSISTANT }.flatMap { it.toolCalls }.map { it to results[it.id] }
    }

    /** Successful calls of [tool], with their arguments. */
    fun succeeded(tool: String): List<JsonObject> = calls
        .filter { (call, result) -> call.name == tool && result != null && !result.isError }
        .mapNotNull { (call, _) -> runCatching { HarnessJson.parseToJsonElement(call.argumentsJson.ifBlank { "{}" }).jsonObject }.getOrNull() }

    /** Successful memory_update calls that changed the feeling (the result says "Now feels" or "as it did"). */
    val feelingChanges: Int
        get() = calls.count { (call, result) ->
            call.name == "memory_update" && result != null && !result.isError && (result.content.contains("Now feels") || result.content.contains("as it did at first"))
        }
}

internal fun JsonObject.str(key: String) = runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()

/** Folds new conversation into episode memories, and backfills embeddings. */
class RecordEpisodesActivity : HeartbeatActivity {
    override val name = "episodes"

    override suspend fun prepare(beat: Beat) {
        val h = beat.h
        val setup = beat.setup
        val settings = setup.settings
        runCatching { h.memory.backfillEmbeddings(beat.persona.id) }.onFailure { h.log("Embedding backfill failed: ${it.message}") }
        if (!settings.memory.enabled) return
        val conversation = setup.conversation
        val history = beat.history
        val start = conversation.lastEpisodeMessageId?.let { id -> history.indexOfFirst { it.id == id } + 1 } ?: 0
        val pending = history.drop(start).filter { !it.hidden && !it.excludedFromContext }
        if (pending.size < settings.memory.episodeEveryMessages) return
        val userName = setup.userPersona?.name ?: "User"
        val transcript = ContextBuilder.renderTranscript(pending, setup.persona.name, userName)
        val summary = h.summarizer.summarize(setup.profile, settings, SummaryRequest(setup.persona.name, userName, transcript)) ?: return
        h.memory.save(
            personaId = setup.persona.id,
            content = "Conversation recap: ${summary.trim()}",
            kind = MemoryKind.EPISODE,
            tags = listOf("episode"),
            sourceConversationId = conversation.id,
            sourceMessageId = pending.firstOrNull { it.role == MessageRole.ASSISTANT }?.id,
            feltMood = MemoryService.peakEndMood(pending, settings.learnedFeelings),
        )
        h.logActivity(setup.persona.id, ActivityKind.RECORDED_EPISODE, conversation.id)
        val updated = h.updateConversation(conversation.id) { it.copy(lastEpisodeMessageId = pending.last().id) }
        beat.setup = setup.copy(conversation = updated ?: conversation.copy(lastEpisodeMessageId = pending.last().id))
    }
}

/**
 * Invites a reflection once enough has happened: three or more strong or deeply-mattering memories
 * since the last one, or a few new memories after three days. At most one invitation per 12 hours.
 */
class ReflectActivity : HeartbeatActivity {
    override val name = "reflect"

    override suspend fun invite(beat: Beat): String? {
        if (!beat.setup.settings.memory.enabled || !beat.setup.settings.memory.memoryToolsEnabled) return null
        val now = beat.now
        if ((beat.lastInvited(name) ?: 0L) > now - 12 * HOUR) return null
        val last = beat.lastActivity(ActivityKind.REFLECTED)
        val since = last ?: (now - 14 * DAY)
        val learned = beat.setup.settings.learnedFeelings
        val fresh = beat.h.memory.all(beat.persona.id).filter { it.createdAt > since && it.kind != MemoryKind.REFLECTION && !it.isReplaced }
        val weighty = fresh.filter { it.level == MemoryLevel.MATTERS_DEEPLY || FeelingWords.lookup(it.currentMood, learned)?.intensity == Intensity.STRONG }
        val daysSince = if (last == null) Double.MAX_VALUE else (now - last).toDouble() / DAY
        val due = weighty.size >= 3 || (daysSince >= 3 && fresh.size >= 2)
        if (!due) return null
        val highlights = (weighty + fresh).distinct().sortedByDescending { it.createdAt }.take(5)
        beat.shownMemoryIds += highlights.map { it.id }
        return buildString {
            append("A lot has happened since you last reflected. Some of it:\n")
            highlights.forEach { m -> append("- ").append(feelingNote(m)?.let { "($it) " }.orEmpty()).append(m.content).append('\n') }
            append("If something has settled into a thought about ${beat.userName}, yourself or the two of you, keep it with ")
            append("memory_save (kind reflection).")
        }
    }

    override suspend fun after(beat: Beat, moment: Moment?) {
        moment ?: return
        val reflections = moment.succeeded("memory_save").count { it.str("kind")?.lowercase() == "reflection" }
        if (reflections > 0) {
            beat.h.logActivity(beat.persona.id, ActivityKind.REFLECTED, beat.setup.conversation.id, reflections)
            // A reflection draws on what it was shown: that counts as real use.
            beat.h.memory.markUsed(beat.shownMemoryIds)
        }
    }
}

/**
 * Offers back a few memories that came up lately, so the persona can revisit how they feel or how
 * much they matter (the moment after recall is when memories can change). Each memory is offered
 * at most once a week, and this activity at most every 12 hours.
 */
class RevisitActivity : HeartbeatActivity {
    override val name = "revisit"

    override suspend fun invite(beat: Beat): String? {
        if (!beat.setup.settings.memory.enabled || !beat.setup.settings.memory.memoryToolsEnabled) return null
        val now = beat.now
        if ((beat.lastInvited(name) ?: 0L) > now - 12 * HOUR) return null
        val candidates = beat.h.memory.all(beat.persona.id)
            .filter { m ->
                !m.isReplaced && m.kind != MemoryKind.REFLECTION &&
                    maxOf(m.lastAccessedAt, m.lastUsedAt) > now - 3 * DAY &&
                    m.createdAt < now - HOUR &&
                    (m.revisitedAt ?: 0L) < now - 7 * DAY
            }
            .sortedByDescending { maxOf(it.lastAccessedAt, it.lastUsedAt) }
            .take(4)
        if (candidates.size < 2) return null
        // Mark them offered (and fresh, so a change of feeling is allowed right now).
        candidates.forEach { m -> beat.h.memories.upsert(m.copy(revisitedAt = now, lastAccessedAt = now)) }
        beat.shownMemoryIds += candidates.map { it.id }
        return buildString {
            append("These memories came up lately. If something has happened since, or you see one differently now, you can change ")
            append("how it feels or how much it matters with memory_update (give the reason), or keep a new fact that replaces one. ")
            append("Feelings often soften as things resolve; leave them be if nothing has changed.\n")
            candidates.forEach { m ->
                val notes = listOfNotNull("id ${m.id.take(8)}", m.level.label.lowercase(), feelingNote(m))
                append("- [").append(notes.joinToString(" | ")).append("] ").append(m.content).append('\n')
            }
        }.trimEnd()
    }

    override suspend fun after(beat: Beat, moment: Moment?) {
        val changed = moment?.feelingChanges ?: 0
        if (changed > 0) beat.h.logActivity(beat.persona.id, ActivityKind.REVISITED_FEELINGS, beat.setup.conversation.id, changed)
    }
}

/** Keeps "matters deeply" rare: over the limit, the persona is asked which memories can settle. */
class SettleActivity : HeartbeatActivity {
    override val name = "settle"

    override suspend fun invite(beat: Beat): String? {
        val settings = beat.setup.settings.memory
        if (!settings.enabled || !settings.memoryToolsEnabled) return null
        if ((beat.lastInvited(name) ?: 0L) > beat.now - DAY) return null
        val deep = beat.h.memory.all(beat.persona.id).filter { it.level == MemoryLevel.MATTERS_DEEPLY && !it.isReplaced && !it.isCore }
        val over = deep.size - settings.deepLimit
        if (over <= 0) return null
        val oldest = deep.sortedBy { maxOf(it.lastUsedAt, it.createdAt) }.take((over + 2).coerceAtMost(6))
        beat.shownMemoryIds += oldest.map { it.id }
        return buildString {
            append("You hold ${deep.size} memories that matter deeply; keep it to about ${settings.deepLimit}. ")
            append("Choose $over that can settle to worth_keeping (memory_update weight). They won't be lost.\n")
            oldest.forEach { m -> append("- [id ${m.id.take(8)}] ").append(m.content).append('\n') }
        }.trimEnd()
    }

    override suspend fun after(beat: Beat, moment: Moment?) {
        val settled = moment?.succeeded("memory_update")?.count { MemoryLevel.parse(it.str("weight")) == MemoryLevel.WORTH_KEEPING } ?: 0
        if (settled > 0) beat.h.logActivity(beat.persona.id, ActivityKind.SETTLED_MEMORIES, beat.setup.conversation.id, settled)
    }
}

/**
 * Sorts mood words the built-in list doesn't know into a family, once, with the utility model, so
 * stamps and weights work for any word. Runs after a private moment (when a model call is being
 * made anyway), a dozen words at a time.
 */
class SortFeelingsActivity : HeartbeatActivity {
    override val name = "feelings"

    override suspend fun after(beat: Beat, moment: Moment?) {
        moment ?: return
        val h = beat.h
        val settings = h.settings.current()
        val learned = settings.learnedFeelings
        val words = buildList {
            h.memory.all(beat.persona.id).forEach { addAll(listOfNotNull(it.feltMood, it.mood)) }
            beat.history.takeLast(60).mapNotNullTo(this) { it.mood }
            addAll(moment.outcome.messages.mapNotNull { it.mood })
        }.map { FeelingWords.normalize(it) }.filter { it.isNotEmpty() && FeelingWords.lookup(it, learned) == null }.distinct().take(12)
        if (words.isEmpty()) return
        val answer = runCatching {
            h.summarizer.ask(
                beat.setup.profile,
                settings,
                system = "You sort feeling words. Answer only in the requested format.",
                prompt = "Sort each mood word into one family and say whether it is mild or strong.\n" +
                    "Families: bright (happy, amused), fond (warm, grateful), proud, calm (content, relieved, sleepy), sad, " +
                    "uneasy (worried, nervous, embarrassed), cross (annoyed, angry), wonder (curious, surprised), " +
                    "wistful (nostalgic, longing).\n" +
                    "Reply with one line per word, exactly like: word: family, mild\nWords: ${words.joinToString(", ")}",
            )
        }.getOrNull() ?: return
        val sorted = parse(answer, words.toSet())
        if (sorted.isNotEmpty()) h.settings.update { it.copy(learnedFeelings = (it.learnedFeelings + sorted).entries.take(500).associate { e -> e.key to e.value }) }
    }

    companion object {
        /** Parses "word: family, mild" lines, keeping only the words that were asked about. */
        fun parse(answer: String, asked: Set<String>): Map<String, Feeling> = answer.lines().mapNotNull { line ->
            val (word, rest) = line.split(':', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: return@mapNotNull null
            val key = FeelingWords.normalize(word.trim().trimStart('-', '*', ' '))
            if (key !in asked) return@mapNotNull null
            val parts = rest.lowercase().split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
            val family = parts.firstNotNullOfOrNull { p -> FeelingFamily.entries.firstOrNull { it.name.lowercase() == p } } ?: return@mapNotNull null
            val intensity = if (parts.any { it == "strong" }) Intensity.STRONG else Intensity.MILD
            key to Feeling(family, intensity)
        }.toMap()
    }
}

private const val HOUR = 3_600_000L
private const val DAY = 86_400_000L
