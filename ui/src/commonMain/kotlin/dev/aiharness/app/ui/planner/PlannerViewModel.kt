package dev.aiharness.app.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aiharness.app.AppContainer
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.EntryState
import dev.aiharness.core.model.Hemisphere
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.PlannerEntry
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.planner.Occurrence
import dev.aiharness.core.planner.Planner
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import dev.aiharness.core.theme.ThemeClock
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

    /** A day in the week (or month) shown. */
    val shownDay = MutableStateFlow(today())

    /** The week page or the month page (the 週 and 月 index tabs). */
    val view = MutableStateFlow(PlannerView.WEEK)

    private val phoneTick = MutableStateFlow(0)

    /** Reads the phone's calendars again (when the planner comes back into view: they may have changed). */
    fun refreshPhone() {
        phoneTick.value++
    }

    /** What the phone's calendars put in the open planner, for the month shown and a week or two either side. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val phone: StateFlow<Planner.PhoneRead> = combine(
        you,
        settings.map { it.activeUserPersonaId to it.planner.phone }.distinctUntilChanged(),
        shownDay.map { java.time.YearMonth.from(it) }.distinctUntilChanged(),
        phoneTick,
    ) { u, _, month, _ -> u to month }
        .mapLatest { (u, month) -> if (u == null) Planner.PhoneRead.NONE else h.planner.phoneRead(u.id, settings.value, month.atDay(1).minusDays(7), month.atEndOfMonth().plusDays(14)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Planner.PhoneRead.NONE)

    /** The public holidays on [day]: a shown phone holiday calendar's, else Nager.Date's. */
    fun holidaysOn(day: LocalDate): List<String> = h.planner.holidaysOn(settings.value, phone.value, day)

    /** Every persona, for the marks of who wrote where. */
    val personas: StateFlow<List<Persona>> = h.personas.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Public holidays, when they're shown: fetched once a year, so usually nothing to do.
        viewModelScope.launch { h.holidays.refresh() }
    }

    /** The day the ribbon marks (today until it's moved). */
    fun bookmark(s: AppSettings): LocalDate = s.planner.bookmark?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today()

    /** Moves the ribbon to [day]'s page; to today's, it follows today again. */
    fun moveBookmark(day: LocalDate) = viewModelScope.launch {
        h.settings.update { it.copy(planner = it.planner.copy(bookmark = day.takeIf { d -> d != today() }?.toString())) }
    }

    /** Your side of the equator, for the seasons. */
    fun hemisphere(s: AppSettings): Hemisphere = ThemeClock.hemisphere(s.theme, zone)

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

    /** A persona's suggestion becomes your plan, in your ink, with its slip kept beside it; the persona hears it was added. */
    fun addSuggestion(suggestion: PlannerEntry) = viewModelScope.launch {
        val now = now()
        h.plannerEntries.upsert(
            suggestion.copy(
                id = newId(), byPersonaId = null, kind = EntryKind.PLAN, state = EntryState.OPEN, fromSuggestion = suggestion.id,
                conversationId = null, messageId = null, createdAt = now, updatedAt = now,
            ),
        )
        h.plannerEntries.upsert(suggestion.copy(state = EntryState.ADDED, updatedAt = now))
    }

    /** "Not this time": the slip is crossed through gently, and the persona hears so on its next turn. */
    fun declineSuggestion(suggestion: PlannerEntry) = save(suggestion.copy(state = EntryState.DECLINED))

    /** "Put it back": a suggestion just taken down is waiting again, as if it hadn't been. */
    fun putBackSuggestion(suggestion: PlannerEntry) = save(suggestion.copy(state = EntryState.OPEN))

    /** Stamps [entry] [state] (done, cancelled), or lifts the stamp when it already has it. */
    fun stamp(entry: PlannerEntry, state: EntryState) {
        if (entry.phone != null) stampPhone(entry, state) else save(entry.copy(state = if (entry.state == state) EntryState.OPEN else state))
    }

    /** A phone event's stamp (done, not going) is kept here, as nothing is written back to the phone; kept three months. */
    private fun stampPhone(entry: PlannerEntry, state: EntryState) = viewModelScope.launch {
        val (eventId, start) = dev.aiharness.core.planner.PhoneEntries.parse(entry.id) ?: return@launch
        val now = now()
        h.settings.update { s ->
            val p = s.planner.phone
            val kept = p.stamps.filter { !(it.eventId == eventId && it.start == start) && it.start > now - 90L * 86_400_000L }
            val stamps = if (entry.state == state) kept else kept + dev.aiharness.core.model.PhoneStamp(eventId, start, state, now)
            s.copy(planner = s.planner.copy(phone = p.copy(stamps = stamps)))
        }
    }

    /**
     * Moves [entry] to [day], keeping its time; it carries its 延 there. It remembers the day it
     * started on (moving it on again keeps that), and moving it back there makes it a plan again.
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

enum class PlannerView { WEEK, MONTH }
