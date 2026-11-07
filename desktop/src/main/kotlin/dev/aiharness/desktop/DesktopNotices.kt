package dev.aiharness.desktop

import dev.aiharness.app.heartbeat.BusyRules
import dev.aiharness.app.heartbeat.Delivery
import dev.aiharness.app.heartbeat.HeldLetter
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
 */
class DesktopNotices(
    private val harness: Harness,
    private val state: AppState,
    private val notify: (Notice) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    /** Checks each minute whether held letters can be told now. */
    fun start() {
        scope.launch {
            while (isActive) {
                delay(60_000L)
                runCatching { release() }
            }
        }
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

    private companion object {
        const val KEY = "held_letters"
        const val MAX_TEXT = 240
    }
}
