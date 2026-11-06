package dev.aiharness.core.seasons

import dev.aiharness.core.model.Hemisphere
import dev.aiharness.core.model.Season
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.sin

/** One of the 二十四節気, the 24 solar terms: 立冬, "Winter begins". [index] counts from 立春 (0). */
data class SolarTerm(val index: Int, val kanji: String, val english: String)

/**
 * One of the 七十二候, the 72 microseasons, three to a solar term and about five days each:
 * 楓蔦黄, "Maples and ivy turn yellow". [index] counts from 立春's first (0).
 */
data class Microseason(val index: Int, val kanji: String, val english: String) {
    val term: SolarTerm get() = Seasons.TERMS[index / 3]

    /** 初候, 次候 or 末候: the first, middle or last of its term. */
    val partKanji: String get() = PARTS[index % 3].first
    val part: String get() = PARTS[index % 3].second

    private companion object {
        val PARTS = listOf("初候" to "first", "次候" to "middle", "末候" to "last")
    }
}

/** Where a day stands in the year's seasons: its microseason and solar term, when they began and end, and the next term. */
data class SeasonDay(
    val day: LocalDate,
    val micro: Microseason,
    val microFirst: LocalDate,
    val microLast: LocalDate,
    val termFirst: LocalDate,
    val next: SolarTerm,
    val nextFirst: LocalDate,
) {
    val term: SolarTerm get() = micro.term
}

/**
 * The solar terms and microseasons (docs/design/calendar.md on the design branch), worked out on
 * the phone from where the sun is: a term begins as the sun's longitude reaches each 15 degrees
 * (立春 at 315), a microseason each 5. A day belongs to whichever began by its end, in the phone's
 * own time zone. South of the equator everything moves half a year (立春 in early August), so
 * "camellias open" still comes as autumn ends there.
 */
object Seasons {
    /** Which term or microseason is in effect at [millis], counted from 立春 in [hemisphere]. */
    fun termIndexAt(millis: Long, hemisphere: Hemisphere): Int = (fromRisshun(millis, hemisphere) / 15.0).toInt().coerceIn(0, 23)

    fun microIndexAt(millis: Long, hemisphere: Hemisphere): Int = (fromRisshun(millis, hemisphere) / 5.0).toInt().coerceIn(0, 71)

    /** The microseason [day] is in: the one in effect as the day ends. */
    fun microOn(day: LocalDate, zone: ZoneId, hemisphere: Hemisphere): Microseason = MICROSEASONS[microIndexAt(endOf(day, zone), hemisphere)]

    fun termOn(day: LocalDate, zone: ZoneId, hemisphere: Hemisphere): SolarTerm = TERMS[termIndexAt(endOf(day, zone), hemisphere)]

    /** [day]'s microseason and term, when each began, and when the next term begins. */
    fun on(day: LocalDate, zone: ZoneId, hemisphere: Hemisphere): SeasonDay {
        val micro = microOn(day, zone, hemisphere)
        fun microIs(d: LocalDate) = microIndexAt(endOf(d, zone), hemisphere) == micro.index
        fun termIs(d: LocalDate) = termIndexAt(endOf(d, zone), hemisphere) == micro.index / 3
        var first = day
        while (microIs(first.minusDays(1))) first = first.minusDays(1)
        var last = day
        while (microIs(last.plusDays(1))) last = last.plusDays(1)
        var termFirst = first
        while (termIs(termFirst.minusDays(1))) termFirst = termFirst.minusDays(1)
        var nextFirst = last.plusDays(1)
        while (termIs(nextFirst)) nextFirst = nextFirst.plusDays(1)
        return SeasonDay(day, micro, first, last, termFirst, TERMS[(micro.index / 3 + 1) % 24], nextFirst)
    }

    /** The solar terms whose first day falls from [from] to [to], in order. */
    fun termsBeginning(from: LocalDate, to: LocalDate, zone: ZoneId, hemisphere: Hemisphere): List<Pair<LocalDate, SolarTerm>> {
        val out = mutableListOf<Pair<LocalDate, SolarTerm>>()
        var before = termIndexAt(endOf(from.minusDays(1), zone), hemisphere)
        var d = from
        while (!d.isAfter(to)) {
            val now = termIndexAt(endOf(d, zone), hemisphere)
            if (now != before) out += d to TERMS[now]
            before = now
            d = d.plusDays(1)
        }
        return out
    }

    /** The theme's season by the solar terms: spring from 立春, summer from 立夏, autumn from 立秋, winter from 立冬. */
    fun themeSeason(day: LocalDate, zone: ZoneId, hemisphere: Hemisphere): Season = when (termOn(day, zone, hemisphere).index / 6) {
        0 -> Season.HARU
        1 -> Season.NATSU
        2 -> Season.AKI
        else -> Season.FUYU
    }

    /**
     * Which side of the equator the phone is likely on, from its time zone (a guess a setting
     * corrects): the zones of the southern hemisphere's countries are listed; everything else is north.
     */
    fun hemisphereOf(zone: ZoneId): Hemisphere {
        val id = zone.id
        val south = SOUTHERN_PREFIXES.any { id.startsWith(it) } || id in SOUTHERN_ZONES
        return if (south) Hemisphere.SOUTH else Hemisphere.NORTH
    }

    /**
     * The sun's apparent longitude along the ecliptic at [millis], in degrees (Meeus, *Astronomical
     * Algorithms*, ch. 25, to about 0.01 degrees: a term's moment to within about a quarter of an hour).
     */
    fun sunLongitude(millis: Long): Double {
        // Julian centuries from J2000, in terrestrial time (about 69 s ahead of the clock these years).
        val jd = (millis + DELTA_T_MILLIS) / 86_400_000.0 + 2440587.5
        val t = (jd - 2451545.0) / 36525.0
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sinD(m) +
            (0.019993 - 0.000101 * t) * sinD(2 * m) +
            0.000289 * sinD(3 * m)
        val omega = 125.04 - 1934.136 * t
        return norm(l0 + c - 0.00569 - 0.00478 * sinD(omega))
    }

    private fun fromRisshun(millis: Long, hemisphere: Hemisphere): Double {
        val shift = if (hemisphere == Hemisphere.SOUTH) 180.0 else 0.0
        return norm(sunLongitude(millis) + shift - 315.0)
    }

    /** The last moment of [day]. */
    private fun endOf(day: LocalDate, zone: ZoneId): Long = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    private fun norm(deg: Double) = deg - 360.0 * floor(deg / 360.0)
    private fun sinD(deg: Double) = sin(Math.toRadians(deg))

    private const val DELTA_T_MILLIS = 69_000L

    val TERMS: List<SolarTerm> = listOf(
        "立春" to "Spring begins", "雨水" to "Rain water", "啓蟄" to "Insects awaken",
        "春分" to "Spring equinox", "清明" to "Clear and bright", "穀雨" to "Grain rain",
        "立夏" to "Summer begins", "小満" to "Grain fills", "芒種" to "Grain in ear",
        "夏至" to "Summer solstice", "小暑" to "Lesser heat", "大暑" to "Greater heat",
        "立秋" to "Autumn begins", "処暑" to "Heat ends", "白露" to "White dew",
        "秋分" to "Autumn equinox", "寒露" to "Cold dew", "霜降" to "Frost falls",
        "立冬" to "Winter begins", "小雪" to "Light snow", "大雪" to "Heavy snow",
        "冬至" to "Winter solstice", "小寒" to "Lesser cold", "大寒" to "Greater cold",
    ).mapIndexed { i, (kanji, english) -> SolarTerm(i, kanji, english) }

    /** The 七十二候 as Japan has kept them since 1874, three to each term, from 立春's first. */
    val MICROSEASONS: List<Microseason> = listOf(
        // 立春
        "東風解凍" to "East winds melt the ice", "黄鶯睍睆" to "Bush warblers sing in the hills", "魚上氷" to "Fish rise from the ice",
        // 雨水
        "土脉潤起" to "Rain moistens the soil", "霞始靆" to "Mist starts to linger", "草木萌動" to "Grass sprouts, trees bud",
        // 啓蟄
        "蟄虫啓戸" to "Hibernating insects come out", "桃始笑" to "First peach blossoms", "菜虫化蝶" to "Caterpillars become butterflies",
        // 春分
        "雀始巣" to "Sparrows start to nest", "櫻始開" to "First cherry blossoms", "雷乃発声" to "Distant thunder",
        // 清明
        "玄鳥至" to "Swallows return", "鴻雁北" to "Wild geese fly north", "虹始見" to "First rainbows",
        // 穀雨
        "葭始生" to "First reeds sprout", "霜止出苗" to "Last frost, rice seedlings grow", "牡丹華" to "Peonies bloom",
        // 立夏
        "蛙始鳴" to "Frogs start singing", "蚯蚓出" to "Worms surface", "竹笋生" to "Bamboo shoots sprout",
        // 小満
        "蚕起食桑" to "Silkworms feast on mulberry leaves", "紅花栄" to "Safflowers bloom", "麦秋至" to "Wheat ripens",
        // 芒種
        "蟷螂生" to "Praying mantises hatch", "腐草為螢" to "Fireflies rise from the damp grass", "梅子黄" to "Plums turn yellow",
        // 夏至
        "乃東枯" to "Self-heal withers", "菖蒲華" to "Irises bloom", "半夏生" to "Crow-dipper sprouts",
        // 小暑
        "温風至" to "Warm winds blow", "蓮始開" to "First lotus blossoms", "鷹乃学習" to "Young hawks learn to fly",
        // 大暑
        "桐始結花" to "Paulownia trees set seed", "土潤溽暑" to "The earth is damp, the air humid", "大雨時行" to "Great rains sometimes fall",
        // 立秋
        "涼風至" to "Cool winds blow", "寒蝉鳴" to "Evening cicadas sing", "蒙霧升降" to "Thick fog descends",
        // 処暑
        "綿柎開" to "Cotton bolls open", "天地始粛" to "The heat starts to ease", "禾乃登" to "Rice ripens",
        // 白露
        "草露白" to "Dew glistens white on the grass", "鶺鴒鳴" to "Wagtails sing", "玄鳥去" to "Swallows leave",
        // 秋分
        "雷乃収声" to "Thunder falls quiet", "蟄虫坏戸" to "Insects close up their burrows", "水始涸" to "Fields are drained",
        // 寒露
        "鴻雁来" to "Wild geese return", "菊花開" to "Chrysanthemums bloom", "蟋蟀在戸" to "Crickets chirp by the door",
        // 霜降
        "霜始降" to "First frost", "霎時施" to "Light rains sometimes fall", "楓蔦黄" to "Maples and ivy turn yellow",
        // 立冬
        "山茶始開" to "Camellias open", "地始凍" to "The land starts to freeze", "金盞香" to "Daffodils bloom",
        // 小雪
        "虹蔵不見" to "Rainbows hide", "朔風払葉" to "North winds blow the leaves down", "橘始黄" to "Tachibana citrus turns yellow",
        // 大雪
        "閉塞成冬" to "Skies turn cold, winter sets in", "熊蟄穴" to "Bears go into their dens", "鱖魚群" to "Salmon gather and swim upstream",
        // 冬至
        "乃東生" to "Self-heal sprouts", "麋角解" to "Deer shed their antlers", "雪下出麦" to "Wheat sprouts under the snow",
        // 小寒
        "芹乃栄" to "Water dropwort flourishes", "水泉動" to "Springs thaw", "雉始雊" to "Pheasants start to call",
        // 大寒
        "款冬華" to "Butterburs bud", "水沢腹堅" to "Ice thickens on the streams", "鶏始乳" to "Hens start laying",
    ).mapIndexed { i, (kanji, english) -> Microseason(i, kanji, english) }

    private val SOUTHERN_PREFIXES = listOf("Australia/", "Antarctica/", "America/Argentina/", "Brazil/", "Chile/")
    private val SOUTHERN_ZONES = setOf(
        // The Americas
        "America/Sao_Paulo", "America/Santiago", "America/Punta_Arenas", "America/Montevideo", "America/Asuncion",
        "America/La_Paz", "America/Lima", "America/Buenos_Aires", "America/Cordoba", "America/Mendoza", "America/Catamarca",
        "America/Jujuy", "America/Rosario", "America/Campo_Grande", "America/Cuiaba", "America/Bahia", "America/Maceio",
        "America/Recife", "America/Fortaleza", "America/Araguaina", "America/Porto_Velho", "America/Rio_Branco",
        "America/Manaus", "America/Eirunepe", "America/Noronha", "America/Belem", "America/Santarem", "America/Guayaquil",
        // Africa
        "Africa/Johannesburg", "Africa/Maseru", "Africa/Mbabane", "Africa/Windhoek", "Africa/Gaborone", "Africa/Harare",
        "Africa/Lusaka", "Africa/Maputo", "Africa/Blantyre", "Africa/Lubumbashi", "Africa/Luanda", "Africa/Dar_es_Salaam",
        "Africa/Kinshasa", "Africa/Bujumbura", "Africa/Kigali", "Africa/Brazzaville",
        // The Indian Ocean
        "Indian/Mauritius", "Indian/Reunion", "Indian/Antananarivo", "Indian/Mayotte", "Indian/Comoro", "Indian/Kerguelen",
        "Indian/Cocos", "Indian/Christmas", "Indian/Chagos",
        // The Atlantic
        "Atlantic/Stanley", "Atlantic/St_Helena", "Atlantic/South_Georgia",
        // Asia and the Pacific
        "Asia/Jakarta", "Asia/Makassar", "Asia/Jayapura", "Asia/Dili",
        "Pacific/Auckland", "Pacific/Chatham", "Pacific/Fiji", "Pacific/Tongatapu", "Pacific/Apia", "Pacific/Noumea",
        "Pacific/Efate", "Pacific/Port_Moresby", "Pacific/Bougainville", "Pacific/Guadalcanal", "Pacific/Norfolk",
        "Pacific/Rarotonga", "Pacific/Tahiti", "Pacific/Marquesas", "Pacific/Gambier", "Pacific/Pitcairn", "Pacific/Easter",
        "Pacific/Galapagos", "Pacific/Niue", "Pacific/Fakaofo", "Pacific/Funafuti", "Pacific/Wallis", "Pacific/Pago_Pago",
        "NZ", "NZ-CHAT",
    )
}
