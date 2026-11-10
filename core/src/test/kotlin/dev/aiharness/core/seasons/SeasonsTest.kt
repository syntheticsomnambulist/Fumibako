package dev.aiharness.core.seasons

import dev.aiharness.core.TestEnv
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.model.Hemisphere
import dev.aiharness.core.model.Season
import dev.aiharness.core.model.SeasonsFollow
import dev.aiharness.core.model.ThemeSettings
import dev.aiharness.core.theme.ThemeClock
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** v0.4 step 3: the solar terms and microseasons, worked out from the sun. */
class SeasonsTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val newYork = ZoneId.of("America/New_York")
    private val sydney = ZoneId.of("Australia/Sydney")

    private fun firstDays(year: Int, zone: ZoneId, hemisphere: Hemisphere = Hemisphere.NORTH) =
        Seasons.termsBeginning(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), zone, hemisphere).associate { (day, term) -> term.kanji to day.toString() }

    @Test
    fun theTermsFallWhereJapansCalendarsPutThem() {
        // 2026 in Japan's time: the equinoxes and solstices, and the terms around November.
        val terms = firstDays(2026, tokyo)
        assertEquals(24, terms.size)
        assertEquals("2026-02-04", terms["立春"])
        assertEquals("2026-03-20", terms["春分"])
        assertEquals("2026-06-21", terms["夏至"])
        assertEquals("2026-09-23", terms["秋分"])
        assertEquals("2026-10-23", terms["霜降"])
        assertEquals("2026-11-07", terms["立冬"])
        assertEquals("2026-11-22", terms["小雪"])
        assertEquals("2026-12-22", terms["冬至"])
    }

    @Test
    fun aDayIsInWhicheverBeganByItsEndInYourOwnTime() {
        // The December solstice is at 20:50 UTC on the 21st: the 22nd in Tokyo, still the 21st in New York.
        assertEquals("2026-12-21", firstDays(2026, newYork)["冬至"])
        assertEquals("立冬", Seasons.termOn(LocalDate.of(2026, 11, 7), tokyo, Hemisphere.NORTH).kanji)
        assertEquals("霜降", Seasons.termOn(LocalDate.of(2026, 11, 6), tokyo, Hemisphere.NORTH).kanji)
    }

    @Test
    fun aMicroseasonKnowsItsTermItsDaysAndWhatComesNext() {
        val now = Seasons.on(LocalDate.of(2026, 11, 4), tokyo, Hemisphere.NORTH)
        assertEquals("楓蔦黄", now.micro.kanji)
        assertEquals("Maples and ivy turn yellow", now.micro.english)
        assertEquals("末候", now.micro.partKanji)
        assertEquals("霜降", now.term.kanji)
        assertEquals("2026-11-02", now.microFirst.toString())
        assertEquals("2026-11-06", now.microLast.toString())
        assertEquals("2026-10-23", now.termFirst.toString())
        assertEquals("立冬", now.next.kanji)
        assertEquals("2026-11-07", now.nextFirst.toString())
        assertEquals("山茶始開", Seasons.microOn(LocalDate.of(2026, 11, 7), tokyo, Hemisphere.NORTH).kanji)
        assertEquals(72, Seasons.MICROSEASONS.size)
        assertEquals(24, Seasons.TERMS.size)
    }

    @Test
    fun southOfTheEquatorTheYearIsTurnedHalfway() {
        // 立春 in early August, and camellias as autumn ends (late May).
        val south = firstDays(2026, sydney, Hemisphere.SOUTH)
        assertEquals("2026-08-07", south["立春"])
        assertEquals("2026-05-05", south["立冬"])
        assertEquals("山茶始開", Seasons.microOn(LocalDate.of(2026, 5, 6), sydney, Hemisphere.SOUTH).kanji)
        assertEquals(Hemisphere.SOUTH, Seasons.hemisphereOf(sydney))
        assertEquals(Hemisphere.SOUTH, Seasons.hemisphereOf(ZoneId.of("Pacific/Auckland")))
        assertEquals(Hemisphere.SOUTH, Seasons.hemisphereOf(ZoneId.of("America/Argentina/Buenos_Aires")))
        assertEquals(Hemisphere.NORTH, Seasons.hemisphereOf(newYork))
        assertEquals(Hemisphere.NORTH, Seasons.hemisphereOf(tokyo))
    }

    @Test
    fun aPersonaHearsOfTheSeasonOnceAsEachMicroseasonBegins() = runTest {
        // Thursday 28 May 2026 (UTC): 小満, safflowers bloom.
        val env = TestEnv().apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        fun told() = (env.provider.requests.last().messages.last { it is LlmMessage.User } as LlmMessage.User)
            .parts.filterIsInstance<LlmPart.Text>().joinToString("\n") { it.text }.lines().firstOrNull { it.startsWith("Season:") }

        env.provider.then(LlmTurnResult(text = "Hello."))
        env.harness.agent.send(conversation.id, "Hi!").toList()
        assertEquals("Season: 小満 (grain fills), safflowers bloom.", told())

        // The same microseason: not again.
        env.clock.advanceMinutes(60)
        env.provider.then(LlmTurnResult(text = "Still here."))
        env.harness.agent.send(conversation.id, "Again").toList()
        assertNull(told())

        // A few days on, the next one, once.
        env.clock.advanceMinutes(4 * 24 * 60)
        env.provider.then(LlmTurnResult(text = "Wheat!"))
        env.harness.agent.send(conversation.id, "Later").toList()
        assertEquals("Season: 小満 (grain fills), wheat ripens.", told())

        // The band turned off: nothing.
        env.settingsRepo.update { it.copy(planner = it.planner.copy(seasonBand = false)) }
        env.clock.advanceMinutes(6 * 24 * 60)
        env.provider.then(LlmTurnResult(text = "Ok."))
        env.harness.agent.send(conversation.id, "Off").toList()
        assertNull(told())
    }

    @Test
    fun theThemeFollowsTheWeatherOrTheSolarTerms() {
        val byTerms = ThemeSettings(seasonsFollow = SeasonsFollow.SOLAR_TERMS)
        assertEquals(Season.AKI, ThemeClock.seasonFor(LocalDate.of(2026, 11, 6), byTerms, tokyo))
        assertEquals(Season.FUYU, ThemeClock.seasonFor(LocalDate.of(2026, 11, 7), byTerms, tokyo))
        assertEquals(Season.HARU, ThemeClock.seasonFor(LocalDate.of(2026, 2, 4), byTerms, tokyo))
        // By the weather, November is still autumn.
        assertEquals(Season.AKI, ThemeClock.seasonFor(LocalDate.of(2026, 11, 7), ThemeSettings(), tokyo))
        // South of the equator, worked out from the time zone with no location saved, or as set.
        assertEquals(Season.HARU, ThemeClock.seasonFor(LocalDate.of(2026, 11, 7), ThemeSettings(), sydney))
        assertEquals(Season.AKI, ThemeClock.seasonFor(LocalDate.of(2026, 11, 7), ThemeSettings(hemisphere = Hemisphere.NORTH), sydney))
        assertEquals(Season.NATSU, ThemeClock.seasonFor(LocalDate.of(2026, 11, 7), byTerms.copy(hemisphere = Hemisphere.SOUTH), tokyo))
    }
}
