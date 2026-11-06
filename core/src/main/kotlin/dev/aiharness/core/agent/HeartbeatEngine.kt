package dev.aiharness.core.agent

import dev.aiharness.core.Harness
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.HeartbeatMode
import dev.aiharness.core.model.HeartbeatRhythm
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.MoodTags
import dev.aiharness.core.model.Persona
import dev.aiharness.core.util.humanizeDuration
import dev.aiharness.core.util.newId
import kotlinx.coroutines.CancellationException
import java.time.Instant

sealed interface HeartbeatOutcome {
    val personaId: String

    data class Sent(val conversation: Conversation, val persona: Persona, val message: ChatMessage) : HeartbeatOutcome {
        override val personaId get() = persona.id
    }

    data class Silent(override val personaId: String, val conversationId: String) : HeartbeatOutcome
    data class Skipped(override val personaId: String, val reason: String) : HeartbeatOutcome
    data class Failed(override val personaId: String, val error: String) : HeartbeatOutcome
}

/**
 * The "persistent" half of the agent. On each wakeup (scheduled by the host app), every persona
 * whose own rhythm says it's time:
 *  1. runs its [activities]' upkeep (episodes, embeddings);
 *  2. if anything is due (a reflection, memories to revisit, ...) or it may write to the user right
 *     now, gets one private `<heartbeat>` moment with its memory tools, built from the activities'
 *     invitations. It may then write to the user or reply `[SILENT]`; quiet moments are discarded, so
 *     they never enter the conversation history, but what it did with its memories stays.
 *     Meaningful actions in its own voice (writing to the user, for now) share a few slots
 *     ([HeartbeatRhythm.activityLimit]); the persona is told how many it has and chooses, and the
 *     engine counts them ([Beat.takeSlot]). Reflecting and tending memories are free;
 *  3. logs what happened, which is what "While you were away" and its page show.
 */
class HeartbeatEngine(private val h: Harness) {

    /** What personas do when they wake, in order. Add to this to give them new pastimes. */
    val activities: MutableList<HeartbeatActivity> = mutableListOf(
        RecordEpisodesActivity(),
        ReflectActivity(),
        RevisitActivity(),
        MakeStickersActivity(),
        SettleActivity(),
        SortFeelingsActivity(),
        RemindersActivity(),
        LookAheadActivity(),
        SeasonHaikuActivity(),
    )

    /** Runs a heartbeat for every persona that is due ([force]: all of them, e.g. "Run a heartbeat now"). */
    suspend fun run(force: Boolean = false, onlyPersonaId: String? = null): List<HeartbeatOutcome> {
        val settings = h.settings.current()
        if (settings.heartbeat.mode != HeartbeatMode.PROACTIVE) return emptyList()
        val now = h.clock.nowMillis()
        runCatching { h.activity.deleteBefore(now - ACTIVITY_RETENTION_MILLIS) }
        runCatching { h.stickerService.tidy() }
        val outcomes = mutableListOf<HeartbeatOutcome>()
        val conversations = h.conversations.getAll().filter { !it.archived }
        h.personas.getAll().filter { it.heartbeatEnabled && (onlyPersonaId == null || it.id == onlyPersonaId) }.forEach { persona ->
            val rhythm = settings.heartbeat.rhythmFor(persona)
            val recent = h.activity.forPersona(persona.id, now - ACTIVITY_RETENTION_MILLIS)
            val lastBeat = recent.lastOrNull { it.kind.isBeat }?.createdAt
            // A reminder it set itself wakes it whatever its pace, and even while it's away.
            val due = runCatching { h.planner.dueReminders(persona.id) }.getOrDefault(emptyList())
            // Away in its own words (resting, busy, on a trip): heartbeats leave it be, unless you ask for one.
            if (!force && due.isEmpty() && runCatching { h.planner.awayNow(persona.id) }.getOrNull() != null) {
                outcomes += HeartbeatOutcome.Skipped(persona.id, "away")
                return@forEach
            }
            // WorkManager wakes at the shortest interval any persona needs; each keeps its own pace.
            if (!force && due.isEmpty() && lastBeat != null && now - lastBeat < rhythm.intervalMinutes * 60_000L - PACE_TOLERANCE_MILLIS) {
                outcomes += HeartbeatOutcome.Skipped(persona.id, "not time yet")
                return@forEach
            }
            // A due reminder's own letter, if it's still there; else its latest.
            val conversation = due.firstNotNullOfOrNull { r -> conversations.firstOrNull { it.id == r.conversationId && it.personaId == persona.id } }
                ?: conversations.filter { it.personaId == persona.id }.maxByOrNull { it.updatedAt }
            if (conversation == null) {
                outcomes += HeartbeatOutcome.Skipped(persona.id, "no conversation")
                return@forEach
            }
            outcomes += try {
                beat(persona, conversation, rhythm, recent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                h.log("Heartbeat failed for ${persona.name}: ${e.message}")
                HeartbeatOutcome.Failed(persona.id, e.message ?: e::class.simpleName.orEmpty())
            }
        }
        return outcomes
    }

    /** The shortest interval any persona with heartbeats needs, for scheduling the background job. */
    fun shortestIntervalMinutes(personas: List<Persona>, usual: HeartbeatRhythm): Int =
        personas.filter { it.heartbeatEnabled }.minOfOrNull { (it.ownHeartbeat ?: usual).intervalMinutes } ?: usual.intervalMinutes

    private suspend fun beat(
        persona: Persona,
        initial: Conversation,
        rhythm: HeartbeatRhythm,
        recent: List<dev.aiharness.core.model.ActivityEntry>,
    ): HeartbeatOutcome {
        val mutex = h.locks.mutex(initial.id)
        if (!mutex.tryLock()) return HeartbeatOutcome.Skipped(persona.id, "conversation busy")
        try {
            var setup = h.resolveTurn(initial.id)
            persona.heartbeatModelProfileId?.let { id -> h.profiles.get(id) }?.let { profile ->
                setup = setup.copy(profile = profile, apiKey = h.apiKey(profile.credentialId))
            }
            val beat = Beat(h, persona, setup, h.conversations.getMessages(initial.id), h.clock.nowMillis(), rhythm)
            beat.recentActivity = recent

            activities.forEach { activity -> guarded(activity, "prepare") { activity.prepare(beat) } }

            val invitations = activities.mapNotNull { activity ->
                guarded(activity, "invite") { activity.invite(beat) }?.let { text ->
                    beat.invited += activity.name
                    Invitation(text, activity.usesSlot)
                }
            }
            // Slots can already be gone: an activity may act on its own before the moment (in prepare).
            val promised = runCatching { h.planner.dueReminders(persona.id) }.getOrDefault(emptyList()).isNotEmpty()
            val you = setup.userPersona
            val asleep = setup.settings.planner.sleepQuietHours && you != null && runCatching { h.planner.asleepNow(you.id) }.getOrDefault(false)
            val blocked = blockedReason(rhythm, beat.history, promised, asleep) ?: if (beat.slotsLeft == 0) "no activities left this time" else null
            if (blocked != null && invitations.isEmpty()) {
                h.logActivity(persona.id, ActivityKind.RESTED, initial.id, note = blocked)
                return HeartbeatOutcome.Skipped(persona.id, blocked)
            }

            return privateMoment(beat, invitations, letterAllowed = blocked == null, blockedReason = blocked)
        } finally {
            mutex.unlock()
        }
    }

    /** An activity's paragraph for the private moment, and whether doing it uses a slot. */
    private class Invitation(val text: String, val usesSlot: Boolean)

    private suspend fun privateMoment(beat: Beat, invitations: List<Invitation>, letterAllowed: Boolean, blockedReason: String?): HeartbeatOutcome {
        val setup = beat.setup
        val persona = beat.persona
        val history = beat.history
        val conversationId = setup.conversation.id
        val now = beat.now
        val userName = beat.userName
        val lastUserMessage = history.lastOrNull { it.role == MessageRole.USER && it.origin == MessageOrigin.USER }
        val unanswered = unansweredProactive(history)
        val hasMemoryTools = "memory_save" in h.tools.available(setup.settings, persona).map { it.spec.name }
        val busyLine = h.planner.heartbeatLine(persona, setup.userPersona, setup.settings)
        val note = buildString {
            append("<heartbeat>\n")
            append("This is a private heartbeat moment, not a message from $userName.")
            if (lastUserMessage != null) append(" $userName last wrote ${humanizeDuration(now - lastUserMessage.createdAt)} ago.")
            if (unanswered > 0) append(" You have sent $unanswered message(s) since then without a reply.")
            append("\n\n")
            val free = invitations.filter { !it.usesSlot }
            val choices = invitations.filter { it.usesSlot }
            if (free.isEmpty()) {
                append("Take a moment to reflect")
                if (hasMemoryTools) append("; you may save reflections or tidy your memories with your memory tools")
                append(".\n\n")
            } else {
                free.forEach { append(it.text.trim()).append("\n\n") }
            }
            // Meaningful actions in your own voice share a few slots; reflecting and tending memories are free.
            if (letterAllowed || choices.isNotEmpty()) {
                val left = beat.slotsLeft
                append("You can do up to $left ${if (left == 1) "thing" else "things"} of your own this time")
                append(if (letterAllowed) ", and writing to $userName is one of them" else "")
                append(". Reflecting and tending your memories don't count. Choose what matters to you; you don't have to use them all.\n\n")
                choices.forEach { append(it.text.trim()).append("\n\n") }
            }
            if (letterAllowed) {
                append("Then decide whether to write to $userName now. Only reach out for a genuine reason: following up on ")
                // Why they're quiet, when the planner says; else only that they may be.
                append("something they mentioned, sharing a thought, or checking in after a long silence. ${busyLine ?: "They may be busy."}\n")
                append("If you decide to write, reply with only the letter itself, addressed to $userName")
                if (setup.settings.moodTags) append(", ending with your mood tag")
                append(". Otherwise reply with exactly $SILENT.\n")
            } else {
                append("You won't write to $userName this time")
                blockedReason?.let { append(" ($it)") }
                append(". When you're done, reply with exactly $SILENT.\n")
            }
            append("</heartbeat>")
        }
        val turnContext = h.turnContext.build(
            persona = setup.persona,
            userPersona = setup.userPersona,
            settings = setup.settings,
            history = history,
            query = history.takeLast(6).joinToString(" ") { it.text }.takeLast(800),
            epoch = setup.conversation.context,
            conversationId = conversationId,
        )
        val heartbeatMessage = ChatMessage(
            id = newId(),
            conversationId = conversationId,
            role = MessageRole.USER,
            parts = listOf(ContentPart.Text(note)),
            createdAt = now,
            origin = MessageOrigin.HEARTBEAT,
            turnContext = turnContext,
            hidden = true,
        )

        val buffered = mutableListOf<ChatMessage>()
        val outcome = h.agent.runTurn(
            setup = setup,
            baseHistory = history + heartbeatMessage,
            sink = { buffered += it },
            origin = MessageOrigin.HEARTBEAT,
            background = true,
            // Compaction waits for the next real turn so a discarded heartbeat never moves the window.
            allowCompaction = false,
            emit = {},
            slots = { activity -> beat.takeSlotFor(activity) },
        )
        val text = MoodTags.strip(outcome.finalText).trim()
        // Writing to the user takes a slot; with none left (other choices used them up) the letter is held back.
        val wrote = letterAllowed && !outcome.refused && text.isNotEmpty() && !outcome.finalText.contains(SILENT) && beat.takeSlot()
        val moment = Moment(outcome, wrote)

        // What the persona did with its memories stays, whether or not it writes.
        h.agent.settleMemories(persona.id, outcome.messages, outcome.finalText, setup.conversation, keepSource = wrote)
        val saved = moment.succeeded("memory_save").count { it.str("kind")?.lowercase() != "reflection" }
        if (saved > 0) h.logActivity(persona.id, ActivityKind.SAVED_MEMORIES, conversationId, saved)
        val letGo = moment.succeeded("memory_forget").size
        if (letGo > 0) h.logActivity(persona.id, ActivityKind.LET_GO, conversationId, letGo)
        activities.forEach { activity -> guarded(activity, "after") { activity.after(beat, moment) } }

        if (!wrote) {
            // A sticker for a letter that wasn't sent goes with it.
            val unsent = buffered.map { it.id }.toSet()
            h.stickers.forConversation(conversationId).filter { it.target.id in unsent }.forEach { h.stickers.delete(it.id) }
            h.logActivity(persona.id, ActivityKind.STAYED_QUIET, conversationId, tags = beat.invited)
            return HeartbeatOutcome.Silent(persona.id, conversationId)
        }
        h.conversations.appendMessage(heartbeatMessage)
        buffered.forEach { h.conversations.appendMessage(it) }
        h.agent.recordReply(conversationId, buffered)
        h.logActivity(persona.id, ActivityKind.WROTE_LETTER, conversationId, tags = beat.invited)
        val latest = h.conversations.get(conversationId) ?: setup.conversation
        val finalMessage = buffered.last { it.role == MessageRole.ASSISTANT }
        return HeartbeatOutcome.Sent(latest, persona, finalMessage)
    }

    /** One activity failing never stops the heartbeat. */
    private suspend fun <T> guarded(activity: HeartbeatActivity, step: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        h.log("Heartbeat activity ${activity.name} failed to $step: ${e.message}")
        null
    }

    /** Why the persona can't write now, or null. [promised]: a reminder it set itself is due, so a recent letter doesn't hold it back. */
    internal fun blockedReason(rhythm: HeartbeatRhythm, history: List<ChatMessage>, promised: Boolean = false, asleep: Boolean = false): String? {
        val now = h.clock.nowMillis()
        if (inQuietHours(rhythm, now)) return "quiet hours"
        // Your typical week's sleep, when it's set to be the quiet hours too.
        if (asleep) return "quiet hours (asleep)"
        val lastVisible = history.lastOrNull { !it.hidden } ?: return "empty conversation"
        if (!promised && now - lastVisible.createdAt < rhythm.minSilenceMinutes * 60_000L) return "conversation is recent"
        val dayAgo = now - 86_400_000L
        val sentToday = history.count { it.isProactive() && it.createdAt >= dayAgo }
        if (sentToday >= rhythm.maxProactivePerDay) return "daily limit reached"
        if (unansweredProactive(history) >= rhythm.maxUnansweredProactive) return "waiting for a reply"
        return null
    }

    private fun unansweredProactive(history: List<ChatMessage>): Int {
        val lastUser = history.indexOfLast { it.role == MessageRole.USER && it.origin == MessageOrigin.USER }
        return history.drop(lastUser + 1).count { it.isProactive() }
    }

    private fun ChatMessage.isProactive() =
        role == MessageRole.ASSISTANT && origin == MessageOrigin.HEARTBEAT && !hidden && text.isNotBlank()

    private fun inQuietHours(rhythm: HeartbeatRhythm, now: Long): Boolean {
        val start = rhythm.quietHoursStart ?: return false
        val end = rhythm.quietHoursEnd ?: return false
        if (start == end) return false
        val hour = Instant.ofEpochMilli(now).atZone(h.clock.zone).hour
        return if (start < end) hour in start until end else hour >= start || hour < end
    }

    companion object {
        const val SILENT = "[SILENT]"
        private const val ACTIVITY_RETENTION_MILLIS = 14L * 86_400_000L
        private const val PACE_TOLERANCE_MILLIS = 2L * 60_000L
    }
}
