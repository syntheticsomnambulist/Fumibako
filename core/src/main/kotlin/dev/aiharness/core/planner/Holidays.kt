package dev.aiharness.core.planner

import dev.aiharness.core.llm.HarnessHttp
import dev.aiharness.core.model.Holiday
import dev.aiharness.core.model.HolidaySettings
import dev.aiharness.core.model.HolidayYear
import dev.aiharness.core.storage.SettingsRepository
import dev.aiharness.core.util.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * Public holidays for the planner, from Nager.Date (https://date.nager.at), a free service with no
 * key or account. Only a country and a year are sent, once a year; the lists are kept in the
 * settings, so the planner and a letter's context never wait on the network.
 */
class Holidays(
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val client: OkHttpClient = HarnessHttp.client,
    /** Where the lists are asked for (tests point it at a local server). */
    @Volatile var base: String = "https://date.nager.at",
    private val log: (String) -> Unit = {},
) {
    /** The countries there are holidays for, as code and name, by name; null when they couldn't be fetched. */
    suspend fun countries(): List<Pair<String, String>>? = get("/api/v3/AvailableCountries")?.let { json ->
        json.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val code = o["countryCode"]?.jsonPrimitive?.content ?: return@mapNotNull null
            code to (o["name"]?.jsonPrimitive?.content ?: code)
        }.sortedBy { it.second }
    }

    /** The regions of [country] with holidays of their own this year ("US-MA"), in order; null when they couldn't be fetched. */
    suspend fun regions(country: String): List<String>? =
        fetch(country, today().year)?.flatMap { it.regions }?.distinct()?.sortedBy { regionName(it) }

    /** Turns holidays on for [country] (and [region]), and fetches them; false when they couldn't be fetched yet. */
    suspend fun choose(country: String, region: String?): Boolean {
        settings.update { s ->
            val h = s.planner.holidays
            val years = if (h.country == country) h.years else emptyList()
            s.copy(planner = s.planner.copy(holidays = h.copy(show = true, country = country, region = region, years = years)))
        }
        return refresh()
    }

    /**
     * Fetches this year's and next year's lists for the chosen country when they're missing or a
     * season old (holidays are sometimes added); keeps what's here when offline. True when nothing
     * is left to fetch.
     */
    suspend fun refresh(): Boolean {
        val h = settings.current().planner.holidays
        val country = h.country ?: return true
        if (!h.show) return true
        val now = clock.nowMillis()
        val wanted = today().year.let { listOf(it, it + 1) }
        var complete = true
        val fresh = wanted.mapNotNull { year ->
            val kept = h.years.firstOrNull { it.country == country && it.year == year }
            if (kept != null && kept.fetchedAt > now - STALE) return@mapNotNull kept
            val list = fetch(country, year)
            if (list == null) complete = false
            list?.let { HolidayYear(country, year, now, it) } ?: kept
        }
        settings.update { s ->
            val current = s.planner.holidays
            // Chosen again meanwhile: leave it to that.
            if (current.country != country) return@update s
            s.copy(planner = s.planner.copy(holidays = current.copy(years = fresh)))
        }
        return complete
    }

    private suspend fun fetch(country: String, year: Int): List<Holiday>? =
        get("/api/v3/PublicHolidays/$year/$country")?.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            // Public holidays only (the lists also have bank, school and optional days, and observances).
            val types = (o["types"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
            if ("Public" !in types) return@mapNotNull null
            Holiday(
                date = o["date"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                name = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                localName = o["localName"]?.jsonPrimitive?.content?.takeIf { it != o["name"]?.jsonPrimitive?.content },
                regions = (o["counties"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty(),
            )
        }

    private suspend fun get(path: String): JsonArray? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(base.trimEnd('/') + path).header("User-Agent", USER_AGENT).build()).execute().use { r ->
                if (!r.isSuccessful) error("answered ${r.code}")
                Json.parseToJsonElement(r.body?.string().orEmpty()).jsonArray
            }
        }.onFailure { log("Holidays: $path failed: ${it.message}") }.getOrNull()
    }

    private fun today(): LocalDate = LocalDate.ofInstant(Instant.ofEpochMilli(clock.nowMillis()), clock.zone)

    companion object {
        private const val STALE = 90L * 24 * 60 * 60 * 1000
        private const val USER_AGENT = "Fumibako (Android planner)"

        /** The holidays kept on [day] in your region (nationwide ones, and your region's own). */
        fun on(h: HolidaySettings, day: LocalDate): List<Holiday> {
            if (!h.show) return emptyList()
            val iso = day.toString()
            return h.years.firstOrNull { it.country == h.country && it.year == day.year }?.holidays
                .orEmpty().filter { it.date == iso && it.keptIn(h.region) }
                .distinctBy { it.name }
        }

        /** The holidays from [from] to [to] (both included), in order. */
        fun between(h: HolidaySettings, from: LocalDate, to: LocalDate): List<Pair<LocalDate, Holiday>> {
            val out = mutableListOf<Pair<LocalDate, Holiday>>()
            var d = from
            while (!d.isAfter(to)) {
                on(h, d).forEach { out += d to it }
                d = d.plusDays(1)
            }
            return out
        }

        /** The phone's region, to suggest when holidays are first set up. */
        fun suggestedCountry(): String? = Locale.getDefault().country.takeIf { it.length == 2 }

        /** A region's name ("Massachusetts"), or its code when it isn't one listed here. */
        fun regionName(code: String): String = HolidayRegions.NAMES[code] ?: code.substringAfter('-')
    }
}
