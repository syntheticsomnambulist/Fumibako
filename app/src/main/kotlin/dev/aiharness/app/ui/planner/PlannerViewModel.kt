package dev.aiharness.app.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aiharness.app.AppContainer
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.EntryState
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.PlannerEntry
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.planner.Occurrence
import dev.aiharness.core.util.newId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The 帳 Planner tab: whose planner is open, what's in it, and what you do to it. */
class PlannerViewModel(private val container: AppContainer) : ViewModel() {
    private val h = container.harness
    val zone: ZoneId get() = h.clock.zone

    val settings: StateFlow<AppSettings> = h.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Your personas; the planner open is one of theirs (your default one's until you choose). */
    val yous: StateFlow<List<UserPersona>> = h.userPersonas.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val chosen = MutableStateFlow<String?>(null)

    val you: StateFlow<UserPersona?> = combine(yous, settings, chosen) { list, s, c ->
        list.firstOrNull { it.id == (c ?: s.activeUserPersonaId) } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<PlannerEntry>> = you
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else h.plannerEntries.observe(u.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The personas that see the open planner (for "Aria and Juno can see your planner"). */
    val seenBy: StateFlow<List<Persona>> = combine(h.personas.observeAll(), you, settings) { personas, u, s ->
        if (u == null || !u.sharesPlanner(s)) emptyList() else personas.filter { it.seesPlanner }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** A day in the week shown. */
    val shownDay = MutableStateFlow(today())

    fun today(): LocalDate = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(h.clock.nowMillis()), zone)
    fun now(): Long = h.clock.nowMillis()

    fun choose(youId: String) {
        chosen.value = youId
    }

    fun occurrences(all: List<PlannerEntry>, from: LocalDate, to: LocalDate): List<Occurrence> = h.planner.occurrences(all, from, to)

    /** A new entry in the open planner, written by you. */
    fun blank(kind: EntryKind): PlannerEntry? {
        val u = you.value ?: return null
        val now = now()
        return PlannerEntry(newId(), u.id, null, kind, createdAt = now, updatedAt = now)
    }

    fun save(entry: PlannerEntry) = viewModelScope.launch { h.plannerEntries.upsert(entry.copy(updatedAt = now())) }

    fun delete(entry: PlannerEntry) = viewModelScope.launch { h.plannerEntries.delete(entry.id) }

    /** Stamps [entry] [state] (done, cancelled), or lifts the stamp when it already has it. */
    fun stamp(entry: PlannerEntry, state: EntryState) = save(entry.copy(state = if (entry.state == state) EntryState.OPEN else state))

    /**
     * Moves [entry] to [day], keeping its time. A ghost stays on the day it came from (only the
     * first: moving it on again keeps where it started); moving it back there takes the ghost away.
     */
    fun move(entry: PlannerEntry, from: LocalDate, day: LocalDate) {
        if (day == from) return
        // By calendar days, so a plan keeps its hour across a change of clocks.
        val days = ChronoUnit.DAYS.between(from, day)
        val shifted: (Long) -> Long = { java.time.Instant.ofEpochMilli(it).atZone(zone).plusDays(days).toInstant().toEpochMilli() }
        val origin = entry.movedFrom ?: from.toString()
        save(
            entry.copy(
                start = entry.start?.let(shifted),
                end = entry.end?.let(shifted),
                day = entry.day?.let { day.toString() },
                movedFrom = origin.takeIf { it != day.toString() },
            ),
        )
    }
}
