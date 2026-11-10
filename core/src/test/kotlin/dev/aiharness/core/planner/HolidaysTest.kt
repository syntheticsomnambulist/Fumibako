package dev.aiharness.core.planner

import dev.aiharness.core.TestEnv
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** v0.4 step 3: public holidays from Nager.Date, kept on the phone. The clock reads 28 May 2026. */
class HolidaysTest {
    private val server = MockWebServer()
    private val asked = mutableListOf<String>()

    private fun year(y: Int) = """
        [{"date":"$y-01-01","localName":"New Year's Day","name":"New Year's Day","countryCode":"US","global":true,"counties":null,"types":["Public","Bank"]},
         {"date":"$y-02-12","localName":"Lincoln's Birthday","name":"Lincoln's Birthday","countryCode":"US","global":false,"counties":["US-CA","US-NY"],"types":["Observance"]},
         {"date":"$y-04-20","localName":"Patriots' Day","name":"Patriots' Day","countryCode":"US","global":false,"counties":["US-MA","US-ME"],"types":["Public"]},
         {"date":"$y-05-29","localName":"Memorial Day","name":"Memorial Day","countryCode":"US","global":true,"counties":null,"types":["Public","Bank"]}]
    """.trimIndent()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                asked += path
                return when {
                    path == "/api/v3/AvailableCountries" -> MockResponse().setBody("""[{"countryCode":"US","name":"United States"},{"countryCode":"AU","name":"Australia"}]""")
                    path.startsWith("/api/v3/PublicHolidays/") -> MockResponse().setBody(year(path.split('/')[4].toInt()))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    private fun TestEnv.holidays() = Holidays(settingsRepo, clock, base = server.url("/").toString())

    @Test
    fun chosenTheyreFetchedForThisYearAndNextAndKept() = runTest {
        val env = TestEnv().apply { seed() }
        val holidays = env.holidays()
        assertEquals(listOf("AU" to "Australia", "US" to "United States"), holidays.countries())
        assertEquals(listOf("US-ME", "US-MA"), holidays.regions("US"), "public holidays' regions only, by name (Maine, Massachusetts)")

        assertTrue(holidays.choose("US", "US-MA"))
        val h = env.settingsRepo.current().planner.holidays
        assertEquals(listOf(2026, 2027), h.years.map { it.year })
        // Only public holidays, nationwide or your region's.
        assertEquals(listOf("Memorial Day"), Holidays.on(h, LocalDate.of(2026, 5, 29)).map { it.name })
        assertEquals(listOf("Patriots' Day"), Holidays.on(h, LocalDate.of(2026, 4, 20)).map { it.name })
        assertTrue(Holidays.on(h, LocalDate.of(2026, 2, 12)).isEmpty(), "an observance isn't a public holiday")
        assertTrue(Holidays.on(h.copy(region = null), LocalDate.of(2026, 4, 20)).isEmpty(), "another region's holiday isn't yours")
        assertEquals(listOf("New Year's Day"), Holidays.on(h, LocalDate.of(2027, 1, 1)).map { it.name })

        // Kept: asked again, nothing is fetched.
        val before = asked.size
        assertTrue(holidays.refresh())
        assertEquals(before, asked.size)
        // Turned off, none show.
        assertTrue(Holidays.on(h.copy(show = false), LocalDate.of(2026, 5, 29)).isEmpty())
    }

    @Test
    fun offlineTheyWaitAndOnlyTheCountryAndYearAreSent() = runTest {
        val env = TestEnv().apply { seed() }
        val offline = Holidays(env.settingsRepo, env.clock, base = "http://127.0.0.1:1")
        assertFalse(offline.choose("US", null))
        assertTrue(env.settingsRepo.current().planner.holidays.show)
        assertTrue(env.settingsRepo.current().planner.holidays.years.isEmpty())

        env.holidays().refresh()
        assertEquals(listOf("/api/v3/PublicHolidays/2026/US", "/api/v3/PublicHolidays/2027/US"), asked)
    }

    @Test
    fun aPersonaIsToldOfHolidaysComingUp() = runTest {
        val env = TestEnv().apply { seed() }
        env.holidays().choose("US", "US-MA")
        // Thursday 28 May: Memorial Day is tomorrow.
        val context = env.harness.planner.contextFor(env.persona, env.user, env.settingsRepo.current())!!
        assertTrue("Tomorrow is a public holiday: Memorial Day." in context, context)
    }
}
