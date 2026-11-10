package dev.aiharness.desktop

import dev.aiharness.app.heartbeat.Arrivals
import dev.aiharness.app.heartbeat.BusyRules
import dev.aiharness.app.heartbeat.Delivery
import dev.aiharness.app.heartbeat.HeldLetter
import dev.aiharness.app.heartbeat.OnItsWay
import dev.aiharness.app.platform.AppState
import dev.aiharness.core.Harness
import dev.aiharness.core.agent.HeartbeatOutcome
import dev.aiharness.core.util.HarnessJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One notification: a persona's name, what it says, and the letters it opens. */
data class Notice(val title: String, val message: String, val conversationId: String)

/**
 * Heartbeat letters' notifications on a computer, by the same busy rules as the phone
 * ([BusyRules]): notified now, held while your planner says you're busy (kept in [state], so a
 * restart doesn't lose them) and released when you're free, or not notified. [notify] shows one.
 * A reply in the post is told the same way when it arrives ([Arrivals]).
 */
class DesktopNotices(
    private val harness: Harness,
    private val state: AppState,
    private val notify: (Notice) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    /**
     * Checks each minute whether held letters can be told now, and for replies that arrived
     * without their own wake-up (while Fumibako was closed, or the computer asleep).
     */
    fun start() {
        scope.launch {
            while (isActive) {
                delay(60_000L)
                runCatching { arrive() }
                runCatching { release() }
            }
        }
    }

    /**
     * A reply went into the post: told when it arrives [at] (unless it's read as it comes). Kept
     * in [state] until then, so quitting Fumibako doesn't lose it.
     */
    suspend fun expect(conversationId: String, at: Long) {
        lock.withLock { writePost(readPost() + OnItsWay(conversationId, at)) }
        // A moment after it arrives, so a thread open in front of you has marked it read.
        scope.launch {
            delay((at - harness.clock.nowMillis()).coerceAtLeast(0L) + SETTLE)
            runCatching { arrive() }
        }
    }

    /** Replies that have arrived by now: each told as a heartbeat letter is, unless it's been read. */
    suspend fun arrive() {
        val now = harness.clock.nowMillis()
        val came = lock.withLock {
            val (came, coming) = readPost().partition { it.at <= now }
            if (came.isNotEmpty()) writePost(coming)
            came
        }
        if (came.isNotEmpty()) deliver(came.mapNotNull { Arrivals.letter(harness, it.conversationId, it.at) })
    }

    suspend fun deliver(outcomes: List<HeartbeatOutcome>) = lock.withLock {
        outcomes.filterIsInstance<HeartbeatOutcome.Sent>().forEach { outcome ->
            when (val delivery = BusyRules.delivery(harness)) {
                Delivery.Notify -> notify(Notice(outcome.persona.name, outcome.message.displayText.take(MAX_TEXT), outcome.conversation.id))
                Delivery.Silent -> Unit
                is Delivery.Hold -> write(BusyRules.hold(read(), outcome, delivery.doing))
            }
        }
    }

    /** At the end of a busy stretch: one notification per persona that wrote. */
    suspend fun release() = lock.withLock {
        val held = read().takeIf { it.isNotEmpty() } ?: return@withLock
        if (BusyRules.stillHoldingUntil(harness) != null) return@withLock
        held.forEach { notify(Notice(it.personaName, BusyRules.heldText(it), it.conversationId)) }
        write(emptyList())
    }

    private fun read(): List<HeldLetter> =
        runCatching { HarnessJson.decodeFromString<List<HeldLetter>>(state.getString(KEY) ?: "[]") }.getOrDefault(emptyList())

    private fun write(held: List<HeldLetter>) = state.putString(KEY, HarnessJson.encodeToString(held))

    private fun readPost(): List<OnItsWay> =
        runCatching { HarnessJson.decodeFromString<List<OnItsWay>>(state.getString(KEY_POST) ?: "[]") }.getOrDefault(emptyList())

    private fun writePost(post: List<OnItsWay>) = state.putString(KEY_POST, HarnessJson.encodeToString(post))

    private companion object {
        const val KEY = "held_letters"
        const val KEY_POST = "letters_on_their_way"
        const val MAX_TEXT = 240
        /** After a reply arrives, how long its notification waits (as the phone's job does). */
        const val SETTLE = 5_000L
    }
}
