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
import androidx.navigation.NavController
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

/** 十 Planner: your typical week, the week page, the seasons, public holidays (more comes with busy times and phone calendars). */
internal fun SettingsCards.plannerPage(nav: NavController, s: AppSettings, update: Update) {
    val p = s.planner
    fun planner(transform: (dev.aiharness.core.model.PlannerSettings) -> dev.aiharness.core.model.PlannerSettings) = update { it.copy(planner = transform(it.planner)) }
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
            listOf(ClockStyle.PHONE to "Phone's setting", ClockStyle.H12 to "12-hour", ClockStyle.H24 to "24-hour"),
            s.theme.clock,
            { v -> update { it.copy(theme = it.theme.copy(clock = v)) } },
            maxLines = 2,
        )
    }
    card {
        CardTitle("Seasons and special days")
        SwitchRow(
            "The season band", p.seasonBand, { v -> planner { it.copy(seasonBand = v) } },
            subtitle = "The 24 solar terms and 72 microseasons on the planner's pages, worked out on the phone. A letter is told each new one as it begins.",
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
        Hint("Fetched once a year from Nager.Date, a free public list, then kept on the phone. Only the country and the year are sent.")
    } else if (h.show && h.country != null) {
        val where = listOfNotNull(countryName(h.country), h.region?.let(Holidays::regionName) ?: "nationwide holidays").joinToString(" · ")
        Text(where, style = PaperType.entryTitle, color = paper.sumi)
        val years = h.years.filter { it.country == h.country }.map { it.year }
        Hint(
            if (years.isEmpty()) "Not fetched yet; they'll come when you're next online."
            else "Kept on the phone for ${years.joinToString(" and ")}, and fetched again from Nager.Date now and then. A holiday calendar from your phone will be able to stand in for these, under Phone calendars.",
        )
        PaperButton("Change") { country = h.country; region = h.region; choosing = true }
    }
    problem?.let { Text(it, style = PaperType.postscript, color = paper.usuzumi1, modifier = Modifier.padding(top = 2.dp)) }
}

private fun countryName(code: String?): String = code?.let { Locale("", it).getDisplayCountry(Locale.US).ifBlank { it } } ?: ""
