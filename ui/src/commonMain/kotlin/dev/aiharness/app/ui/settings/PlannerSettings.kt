package dev.aiharness.app.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.navigation.NavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.SwitchRow
import dev.aiharness.app.ui.paper.CardTitle
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.paper.PaperChoice
import dev.aiharness.app.ui.paper.PaperSelect
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.ClockStyle
import dev.aiharness.core.model.Hemisphere
import dev.aiharness.core.model.SeasonsFollow
import dev.aiharness.core.planner.Holidays
import dev.aiharness.core.theme.ThemeClock
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale
import dev.aiharness.app.platform.deviceNoun
import dev.aiharness.app.platform.hasDeviceCalendars

/** 十 Planner: your typical week, the week page, the seasons, public holidays (more comes with busy times and phone calendars). */
internal fun SettingsCards.plannerPage(nav: NavController, s: AppSettings, update: Update) {
    val p = s.planner
    fun planner(transform: (dev.aiharness.core.model.PlannerSettings) -> dev.aiharness.core.model.PlannerSettings) = update { it.copy(planner = transform(it.planner)) }
    if (hasDeviceCalendars) card { PhoneCalendarsCard(s, update) }
    card {
        CardTitle("Your typical week")
        Hint("Work, sleep and the rest of your week, printed faintly on every day. Sleep folds the week page.")
        PaperButton("Edit your week") { nav.navigate(Routes.TYPICAL_WEEK) }
    }
    card {
        CardTitle("The week page")
        SwitchRow(
            "Fold my sleep hours", p.foldSleep, { v -> planner { it.copy(foldSleep = v) } },
            subtitle = "The hours you're asleep on every day shown fold to a thin band; tap it to open them.",
        )
        Text("THE WEEK STARTS ON", style = PaperType.meta, color = paper.kasure2)
        PaperChoice(listOf(false to "Monday", true to "Sunday"), p.weekStartsSunday, { v -> planner { it.copy(weekStartsSunday = v) } })
        Text("CLOCK", style = PaperType.meta, color = paper.kasure2)
        PaperChoice(
            listOf(ClockStyle.PHONE to "${deviceNoun.replaceFirstChar { it.uppercase() }}'s setting", ClockStyle.H12 to "12-hour", ClockStyle.H24 to "24-hour"),
            s.theme.clock,
            { v -> update { it.copy(theme = it.theme.copy(clock = v)) } },
            maxLines = 2,
        )
    }
    card {
        CardTitle("During busy times")
        PaperChoice(dev.aiharness.core.model.DuringBusy.entries.map { it to it.label }, p.duringBusy, { v -> planner { it.copy(duringBusy = v) } }, maxLines = 2)
        Hint(
            when (p.duringBusy) {
                dev.aiharness.core.model.DuringBusy.HOLD -> "While your planner says you're busy (work, sleep, a busy plan${if (hasDeviceCalendars) " or phone event" else ""}), a persona's letter arrives quietly; when you're free, one notification says who wrote."
                dev.aiharness.core.model.DuringBusy.NOTIFY -> "Letters notify as they come, busy or not."
                dev.aiharness.core.model.DuringBusy.SILENT -> "While you're busy, letters arrive with no notification at all; they're in your letters when you look."
            },
        )
        Hint("A persona's notification also has \"Busy for now\" (an hour, till tonight, till tomorrow), which adds a busy plan without opening the app.")
        SwitchRow(
            "Use my sleep as quiet hours", p.sleepQuietHours, { v -> planner { it.copy(sleepQuietHours = v) } },
            subtitle = "Heartbeats don't write while your typical week has you asleep, as well as in their own quiet hours.",
        )
    }
    card { WhoSees(s, update) }
    card {
        CardTitle("Seasons and special days")
        SwitchRow(
            "The solar terms and microseasons", p.seasonBand, { v -> planner { it.copy(seasonBand = v) } },
            subtitle = "Each of the 24 solar terms marked on its first day, and a day's microseason on its page, worked out on the $deviceNoun. A letter is told each new microseason as it begins.",
        )
        Hemispheres(s, update)
    }
    card { HolidaysCard(s, update) }
}

/** Look's seasons: what the theme follows, and your side of the equator. */
@Composable
internal fun LookSeasons(s: AppSettings, update: Update) {
    CardTitle("Seasons")
    Text("THE THEME'S SEASONS FOLLOW", style = PaperType.meta, color = paper.kasure2)
    PaperChoice(
        listOf(SeasonsFollow.WEATHER to SeasonsFollow.WEATHER.label, SeasonsFollow.SOLAR_TERMS to SeasonsFollow.SOLAR_TERMS.label),
        s.theme.seasonsFollow,
        { v -> update { it.copy(theme = it.theme.copy(seasonsFollow = v)) } },
    )
    Hint(
        when (s.theme.seasonsFollow) {
            SeasonsFollow.WEATHER -> "Spring from March, summer from June, autumn from September, winter from December (half a year round south of the equator)."
            SeasonsFollow.SOLAR_TERMS -> "As the planner's season band has them: spring from 立春 in early February, summer from 立夏 in early May, autumn from 立秋 in early August, winter from 立冬 in early November."
        },
    )
    Hemispheres(s, update)
}

/** Your side of the equator: worked out (from a saved location, else the time zone), or set. */
@Composable
private fun Hemispheres(s: AppSettings, update: Update) {
    val worked = ThemeClock.hemisphere(s.theme.copy(hemisphere = null), ZoneId.systemDefault())
    Text("YOUR SIDE OF THE EQUATOR", style = PaperType.meta, color = paper.kasure2)
    PaperChoice(
        listOf(null to "Worked out", Hemisphere.NORTH to "Northern", Hemisphere.SOUTH to "Southern"),
        s.theme.hemisphere,
        { v -> update { it.copy(theme = it.theme.copy(hemisphere = v)) } },
    )
    if (s.theme.hemisphere == null) {
        val from = if (s.theme.latitude != null) "your saved location" else "your time zone"
        Hint("From $from: ${if (worked == Hemisphere.SOUTH) "southern" else "northern"}.")
    }
}

/**
 * Public holidays: off until you choose whose (suggesting the phone's region), then fetched from
 * Nager.Date and kept. Where a country's holidays differ by region (a US state, a German Land),
 * you can choose one; otherwise only the nationwide ones show.
 */
@Composable
private fun HolidaysCard(s: AppSettings, update: Update) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val h = s.planner.holidays
    var choosing by remember { mutableStateOf(false) }
    var countries by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var regions by remember { mutableStateOf<List<String>?>(null) }
    var country by remember { mutableStateOf(h.country ?: Holidays.suggestedCountry()) }
    var region by remember { mutableStateOf(h.region) }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun holidays(transform: (dev.aiharness.core.model.HolidaySettings) -> dev.aiharness.core.model.HolidaySettings) =
        update { it.copy(planner = it.planner.copy(holidays = transform(it.planner.holidays))) }

    CardTitle("Public holidays")
    SwitchRow(
        "Show public holidays", h.show,
        { v -> if (v && h.country == null) choosing = true else { choosing = false; holidays { it.copy(show = v) } } },
        subtitle = "In red, like Sundays. Personas that see your planner are told of them too. They leave your typical week as it is.",
    )
    if (choosing) {
        LaunchedEffect(Unit) {
            countries = harness.holidays.countries()
            if (countries == null) problem = "The list of countries couldn't be fetched just now. Try again when you're online."
        }
        LaunchedEffect(country) {
            regions = null
            regions = country?.let { harness.holidays.regions(it) }
            if (region != null && regions?.contains(region) != true) region = null
        }
        Text("WHOSE HOLIDAYS?", style = PaperType.meta, color = paper.kasure2)
        countries?.let { list ->
            PaperSelect(
                label = "Country",
                options = list.map { it.first },
                selected = country?.takeIf { c -> list.any { it.first == c } },
                optionLabel = { code -> list.firstOrNull { it.first == code }?.second ?: code },
                onSelect = { country = it },
            )
        }
        regions?.takeIf { it.isNotEmpty() }?.let { list ->
            PaperSelect(
                label = "Where in ${countryName(country)}",
                options = list,
                selected = region,
                optionLabel = Holidays::regionName,
                onSelect = { region = it },
                noneLabel = "Nationwide holidays only",
            )
        }
        PaperButton("Use these holidays", enabled = country != null && !busy) {
            val c = country ?: return@PaperButton
            busy = true
            scope.launch {
                val fetched = harness.holidays.choose(c, region)
                busy = false
                choosing = false
                problem = if (fetched) null else "Chosen; they'll be fetched when you're next online."
            }
        }
        Hint("Fetched once a year from Nager.Date, a free public list, then kept on the $deviceNoun. Only the country and the year are sent.")
    } else if (h.show && h.country != null) {
        val where = listOfNotNull(countryName(h.country), h.region?.let(Holidays::regionName) ?: "nationwide holidays").joinToString(" · ")
        Text(where, style = PaperType.entryTitle, color = paper.sumi)
        val years = h.years.filter { it.country == h.country }.map { it.year }
        Hint(
            if (years.isEmpty()) "Not fetched yet; they'll come when you're next online."
            else "Kept on the $deviceNoun for ${years.joinToString(" and ")}, and fetched again from Nager.Date now and then." +
                if (hasDeviceCalendars) " A holiday calendar from your phone will be able to stand in for these, under Phone calendars." else "",
        )
        PaperButton("Change") { country = h.country; region = h.region; choosing = true }
    }
    problem?.let { Text(it, style = PaperType.quiet, color = paper.usuzumi1, modifier = Modifier.padding(top = 2.dp)) }
}

private fun countryName(code: String?): String = code?.let { Locale("", it).getDisplayCountry(Locale.US).ifBlank { it } } ?: ""

/**
 * The phone's calendars (docs/design/calendar.md, "Phone calendars"): an explanation, the one
 * permission, then each calendar with its detail level. Nothing shows until you choose.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PhoneCalendarsCard(s: AppSettings, update: Update) {
    val phone = LocalAppContainer.current.phoneCalendars
    var readable by remember { mutableStateOf(phone.readable()) }
    var calendars by remember { mutableStateOf<List<dev.aiharness.core.planner.PhoneCalendar>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val ask = dev.aiharness.app.platform.rememberCalendarPermission { granted ->
        readable = granted
        problem = if (granted) null else "Calendar permission declined. Nothing is read without it."
    }
    LaunchedEffect(readable) { if (readable) calendars = phone.calendars() }
    val levels = s.planner.phone
    fun level(id: String, to: dev.aiharness.core.model.PhoneDetail) = update {
        it.copy(planner = it.planner.copy(phone = it.planner.phone.copy(levels = it.planner.phone.levels + (id to to))))
    }

    CardTitle("Phone calendars")
    Hint(
        "Read from your phone, never changed or copied. Each calendar shows in your default persona's planner as much as you choose here, " +
            "and that's what a persona that sees your planner sees (it goes to the persona's model provider). Attendees and event notes never do.",
    )
    if (!readable) {
        if (ask != null) PaperButton("Use my phone's calendars") { ask() }
    } else {
        val list = calendars
        when {
            list == null -> Text("Looking for your calendars…", style = PaperType.quiet, color = paper.usuzumi1)
            list.isEmpty() -> Text("No calendars on this phone.", style = PaperType.quiet, color = paper.usuzumi1)
            else -> list.forEach { cal ->
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.padding(end = 8.dp).size(10.dp)
                            .background(cal.color?.let { androidx.compose.ui.graphics.Color(it) } ?: paper.kasure2, androidx.compose.foundation.shape.CircleShape),
                    )
                    androidx.compose.foundation.layout.Column {
                        Text(cal.name, style = PaperType.entryTitle, color = paper.sumi)
                        if (cal.account.isNotBlank()) Text(cal.account, style = PaperType.meta, color = paper.kasure2)
                    }
                }
                PaperChoice(
                    dev.aiharness.core.model.PhoneDetail.entries.map { it to it.label },
                    levels.level(cal.id),
                    { v -> level(cal.id, v) },
                    columns = 2,
                    maxLines = 2,
                )
                if (cal.holidays) Hint("A holiday calendar: shown, its days are your public holidays, in place of Nager.Date's.")
            }
        }
        if (levels.levels.values.any { it != dev.aiharness.core.model.PhoneDetail.HIDDEN }) {
            PaperButton("Hide them all") { update { it.copy(planner = it.planner.copy(phone = it.planner.phone.copy(levels = emptyMap()))) } }
        }
        Hint("To stop the app reading your calendars at all, turn off its Calendar permission in your phone's settings.")
    }
    problem?.let { Text(it, style = PaperType.quiet, color = paper.usuzumi1) }
}

/**
 * Who sees your planner: each persona's "Sees my planner", and each of your personas' "Shares my
 * planner" (your default persona shares by default; the others, which may be fictional, don't).
 */
@Composable
private fun WhoSees(s: AppSettings, update: Update) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val personas by androidx.compose.runtime.remember { harness.personas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val yous by androidx.compose.runtime.remember { harness.userPersonas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    CardTitle("Who sees your planner")
    Hint("A persona sees the planner of whichever persona of yours it's writing to, when that one shares it and the persona's own switch is on.")
    Text("YOUR PERSONAS", style = PaperType.meta, color = paper.kasure2)
    yous.forEach { u ->
        SwitchRow(
            "${u.name} shares their planner", u.sharesPlanner(s),
            { v -> scope.launch { harness.userPersonas.upsert(u.copy(sharesPlanner = v, updatedAt = System.currentTimeMillis())) } },
            subtitle = if (u.id == s.activeUserPersonaId) "Your default persona" else null,
        )
    }
    Text("PERSONAS", style = PaperType.meta, color = paper.kasure2)
    personas.forEach { p ->
        SwitchRow("${p.name} sees it", p.seesPlanner, { v -> scope.launch { harness.personas.upsert(p.copy(seesPlanner = v, updatedAt = System.currentTimeMillis())) } })
    }
}
