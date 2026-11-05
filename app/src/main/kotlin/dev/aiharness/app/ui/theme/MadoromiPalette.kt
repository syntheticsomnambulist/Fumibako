/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Colour values from madoromi by samhain (https://madoromi.moonwit.ch/,
 * https://github.com/the-moonwitch/madoromi). Generated from docs/design/madoromi-palette.json;
 * this file stays under MPL-2.0. Keep this notice with it.
 */
package dev.aiharness.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The madoromi accent inks, in the palette's order. Personas and the user each pick one. */
val InkNames: List<String> = listOf("sakura", "nadeshiko", "botan", "fuji", "kikyo", "beni", "akane", "kaki", "yamabuki", "wakaba", "tokiwa", "sora", "ruri", "ai")

/** Kanji for each ink (its Iroshizuku name), for labels and pickers. */
val InkKanji: Map<String, String> = mapOf(
    "sakura" to "桜", "nadeshiko" to "撫子", "botan" to "牡丹", "fuji" to "藤", "kikyo" to "桔梗",
    "beni" to "紅", "akane" to "茜", "kaki" to "柿", "yamabuki" to "山吹", "wakaba" to "若葉",
    "tokiwa" to "常盤", "sora" to "空", "ruri" to "瑠璃", "ai" to "藍",
)

/** One madoromi theme: the UI roles from madoromi's style guide plus its 14 inks. */
@Immutable
data class MadoromiColors(
    val key: String,
    /** Paper. */
    val kami: Color,
    /** Bars and the desk under the paper. */
    val hyoshi: Color,
    /** Deepest surface. */
    val chitsu: Color,
    val nijimi0: Color,
    val nijimi1: Color,
    val nijimi2: Color,
    val kasure0: Color,
    val kasure1: Color,
    val kasure2: Color,
    /** Body text. */
    val sumi: Color,
    val usuzumi0: Color,
    val usuzumi1: Color,
    val fuji: Color,
    val inks: Map<String, Color>,
) {
    val isNight: Boolean get() = key.endsWith("yoru")

    /** An ink by name, falling back to the accent for unknown names. */
    fun ink(name: String?): Color = name?.let { inks[it] } ?: fuji
}

object Madoromi {

    val HARU_HIRU = MadoromiColors(
        key = "haru-hiru",
        kami = Color(0xFFF6EFF8),
        hyoshi = Color(0xFFECE5EF),
        chitsu = Color(0xFFE2DBE5),
        nijimi0 = Color(0xFFD9CCDD),
        nijimi1 = Color(0xFFC8BCCD),
        nijimi2 = Color(0xFFB8ACBD),
        kasure0 = Color(0xFFA99CAD),
        kasure1 = Color(0xFF998D9E),
        kasure2 = Color(0xFF877B8C),
        sumi = Color(0xFF584A5C),
        usuzumi0 = Color(0xFF77697C),
        usuzumi1 = Color(0xFF6B5D70),
        fuji = Color(0xFF6928BD),
        inks = mapOf(
            "sakura" to Color(0xFFAA6E89),
            "nadeshiko" to Color(0xFFD64891),
            "botan" to Color(0xFFB25D9D),
            "fuji" to Color(0xFF6928BD),
            "kikyo" to Color(0xFF4760C0),
            "beni" to Color(0xFFCE2700),
            "akane" to Color(0xFFEE3244),
            "kaki" to Color(0xFFFD5217),
            "yamabuki" to Color(0xFFBDA343),
            "wakaba" to Color(0xFF5E8933),
            "tokiwa" to Color(0xFF009162),
            "sora" to Color(0xFF008BAE),
            "ruri" to Color(0xFF2678AF),
            "ai" to Color(0xFF1955BF),
        ),
    )

    val NATSU_HIRU = MadoromiColors(
        key = "natsu-hiru",
        kami = Color(0xFFE9F4F8),
        hyoshi = Color(0xFFDDEAF0),
        chitsu = Color(0xFFD3E1E6),
        nijimi0 = Color(0xFFBED6DF),
        nijimi1 = Color(0xFFAEC5CE),
        nijimi2 = Color(0xFF9FB5BE),
        kasure0 = Color(0xFF8FA6AE),
        kasure1 = Color(0xFF80969F),
        kasure2 = Color(0xFF6F848D),
        sumi = Color(0xFF3B545E),
        usuzumi0 = Color(0xFF57717A),
        usuzumi1 = Color(0xFF4E6872),
        fuji = Color(0xFF6526B9),
        inks = mapOf(
            "sakura" to Color(0xFFAA6E89),
            "nadeshiko" to Color(0xFFCE37A6),
            "botan" to Color(0xFFB0599B),
            "fuji" to Color(0xFF6526B9),
            "kikyo" to Color(0xFF455CBD),
            "beni" to Color(0xFFB53753),
            "akane" to Color(0xFFD64891),
            "kaki" to Color(0xFFFF5735),
            "yamabuki" to Color(0xFFE3940B),
            "wakaba" to Color(0xFF00915C),
            "tokiwa" to Color(0xFF028E7F),
            "sora" to Color(0xFF008BAE),
            "ruri" to Color(0xFF2E79B6),
            "ai" to Color(0xFF526CCB),
        ),
    )

    val AKI_HIRU = MadoromiColors(
        key = "aki-hiru",
        kami = Color(0xFFF8F0E9),
        hyoshi = Color(0xFFF0E6DD),
        chitsu = Color(0xFFE6DCD3),
        nijimi0 = Color(0xFFDFCEBF),
        nijimi1 = Color(0xFFCEBEAF),
        nijimi2 = Color(0xFFBEAE9F),
        kasure0 = Color(0xFFAE9E90),
        kasure1 = Color(0xFF9F8F81),
        kasure2 = Color(0xFF8D7D6F),
        sumi = Color(0xFF5D4C3C),
        usuzumi0 = Color(0xFF7D6B5A),
        usuzumi1 = Color(0xFF715F4F),
        fuji = Color(0xFF581CA7),
        inks = mapOf(
            "sakura" to Color(0xFFA26480),
            "nadeshiko" to Color(0xFFC63368),
            "botan" to Color(0xFFA1488D),
            "fuji" to Color(0xFF581CA7),
            "kikyo" to Color(0xFF6928BD),
            "beni" to Color(0xFFA43B3B),
            "akane" to Color(0xFFD80638),
            "kaki" to Color(0xFFFF5635),
            "yamabuki" to Color(0xFFE3940B),
            "wakaba" to Color(0xFF7E8418),
            "tokiwa" to Color(0xFF008B57),
            "sora" to Color(0xFF178D9D),
            "ruri" to Color(0xFF2973B0),
            "ai" to Color(0xFF384DAD),
        ),
    )

    val FUYU_HIRU = MadoromiColors(
        key = "fuyu-hiru",
        kami = Color(0xFFF0F1F8),
        hyoshi = Color(0xFFE5E7F3),
        chitsu = Color(0xFFDBDDE9),
        nijimi0 = Color(0xFFCCD0E4),
        nijimi1 = Color(0xFFBCC0D3),
        nijimi2 = Color(0xFFACB0C3),
        kasure0 = Color(0xFF9CA0B3),
        kasure1 = Color(0xFF8D91A4),
        kasure2 = Color(0xFF7B7F91),
        sumi = Color(0xFF4A4E63),
        usuzumi0 = Color(0xFF696D83),
        usuzumi1 = Color(0xFF5E6277),
        fuji = Color(0xFF5E21B0),
        inks = mapOf(
            "sakura" to Color(0xFFAA6E89),
            "nadeshiko" to Color(0xFFB560A0),
            "botan" to Color(0xFFA74F93),
            "fuji" to Color(0xFF5E21B0),
            "kikyo" to Color(0xFF3D53B4),
            "beni" to Color(0xFFBB355A),
            "akane" to Color(0xFFE1376F),
            "kaki" to Color(0xFFFF5635),
            "yamabuki" to Color(0xFFE3940B),
            "wakaba" to Color(0xFF618C32),
            "tokiwa" to Color(0xFF028E7F),
            "sora" to Color(0xFF2678AF),
            "ruri" to Color(0xFF2266A4),
            "ai" to Color(0xFF4A63C3),
        ),
    )

    val HARU_YORU = MadoromiColors(
        key = "haru-yoru",
        kami = Color(0xFF241C26),
        hyoshi = Color(0xFF1C151F),
        chitsu = Color(0xFF160F18),
        nijimi0 = Color(0xFF392E3C),
        nijimi1 = Color(0xFF4E4352),
        nijimi2 = Color(0xFF615665),
        kasure0 = Color(0xFF756A7A),
        kasure1 = Color(0xFF8A7E8F),
        kasure2 = Color(0xFF9F93A4),
        sumi = Color(0xFFE1D1E7),
        usuzumi0 = Color(0xFFB7A8BD),
        usuzumi1 = Color(0xFFCDBED3),
        fuji = Color(0xFFDDB3FF),
        inks = mapOf(
            "sakura" to Color(0xFFFFC5DA),
            "nadeshiko" to Color(0xFFFFADDA),
            "botan" to Color(0xFFFE8DC0),
            "fuji" to Color(0xFFDDB3FF),
            "kikyo" to Color(0xFF99B2F5),
            "beni" to Color(0xFFFF8B79),
            "akane" to Color(0xFFFFA2B5),
            "kaki" to Color(0xFFFFBB74),
            "yamabuki" to Color(0xFFFFC849),
            "wakaba" to Color(0xFFC1DD4A),
            "tokiwa" to Color(0xFF7BD6B6),
            "sora" to Color(0xFF88D9EB),
            "ruri" to Color(0xFF60C9F3),
            "ai" to Color(0xFF6EADFF),
        ),
    )

    val NATSU_YORU = MadoromiColors(
        key = "natsu-yoru",
        kami = Color(0xFF132227),
        hyoshi = Color(0xFF0E1A1F),
        chitsu = Color(0xFF091317),
        nijimi0 = Color(0xFF23363D),
        nijimi1 = Color(0xFF374B53),
        nijimi2 = Color(0xFF4A5F66),
        kasure0 = Color(0xFF5D737B),
        kasure1 = Color(0xFF728790),
        kasure2 = Color(0xFF869DA5),
        sumi = Color(0xFFC0DDE8),
        usuzumi0 = Color(0xFF97B3BE),
        usuzumi1 = Color(0xFFADCAD4),
        fuji = Color(0xFFAEA8FF),
        inks = mapOf(
            "sakura" to Color(0xFFFFBDE2),
            "nadeshiko" to Color(0xFFDD9FBF),
            "botan" to Color(0xFFF894D8),
            "fuji" to Color(0xFFAEA8FF),
            "kikyo" to Color(0xFF87A7FF),
            "beni" to Color(0xFFFF8C9F),
            "akane" to Color(0xFFFAA7CA),
            "kaki" to Color(0xFFFFB66D),
            "yamabuki" to Color(0xFFEEC307),
            "wakaba" to Color(0xFFB9D740),
            "tokiwa" to Color(0xFF6CD2CC),
            "sora" to Color(0xFF5CD7F5),
            "ruri" to Color(0xFF3EC7E8),
            "ai" to Color(0xFF4AB8FF),
        ),
    )

    val AKI_YORU = MadoromiColors(
        key = "aki-yoru",
        kami = Color(0xFF271D14),
        hyoshi = Color(0xFF20160E),
        chitsu = Color(0xFF181008),
        nijimi0 = Color(0xFF3D3024),
        nijimi1 = Color(0xFF534538),
        nijimi2 = Color(0xFF66584B),
        kasure0 = Color(0xFF7B6C5E),
        kasure1 = Color(0xFF908072),
        kasure2 = Color(0xFFA59587),
        sumi = Color(0xFFE8D3C1),
        usuzumi0 = Color(0xFFBEAA98),
        usuzumi1 = Color(0xFFD4C0AE),
        fuji = Color(0xFFD3A1FF),
        inks = mapOf(
            "sakura" to Color(0xFFFFB6CC),
            "nadeshiko" to Color(0xFFDFA3C0),
            "botan" to Color(0xFFF68FD6),
            "fuji" to Color(0xFFD3A1FF),
            "kikyo" to Color(0xFFAEA8FF),
            "beni" to Color(0xFFFF8672),
            "akane" to Color(0xFFFF9CB5),
            "kaki" to Color(0xFFFF9B8F),
            "yamabuki" to Color(0xFFFFAE1E),
            "wakaba" to Color(0xFFBAC105),
            "tokiwa" to Color(0xFF58C59F),
            "sora" to Color(0xFF5AC6F1),
            "ruri" to Color(0xFF5EA3D4),
            "ai" to Color(0xFF7F9BEA),
        ),
    )

    val FUYU_YORU = MadoromiColors(
        key = "fuyu-yoru",
        kami = Color(0xFF1C1E2A),
        hyoshi = Color(0xFF151722),
        chitsu = Color(0xFF0F111B),
        nijimi0 = Color(0xFF2F3241),
        nijimi1 = Color(0xFF434757),
        nijimi2 = Color(0xFF565A6B),
        kasure0 = Color(0xFF6A6E7F),
        kasure1 = Color(0xFF7E8294),
        kasure2 = Color(0xFF9397AA),
        sumi = Color(0xFFD2D6EB),
        usuzumi0 = Color(0xFFA8ADC4),
        usuzumi1 = Color(0xFFBEC3DB),
        fuji = Color(0xFFBBB8FF),
        inks = mapOf(
            "sakura" to Color(0xFFFFBEE1),
            "nadeshiko" to Color(0xFFDFA3C0),
            "botan" to Color(0xFFD18BB7),
            "fuji" to Color(0xFFBBB8FF),
            "kikyo" to Color(0xFFAAA4FF),
            "beni" to Color(0xFFFF8799),
            "akane" to Color(0xFFFFA3D0),
            "kaki" to Color(0xFFFF9B8F),
            "yamabuki" to Color(0xFFECBA09),
            "wakaba" to Color(0xFFAECE34),
            "tokiwa" to Color(0xFF5ECCC5),
            "sora" to Color(0xFF78D5FC),
            "ruri" to Color(0xFF8AC3FF),
            "ai" to Color(0xFF829EEB),
        ),
    )

    val all: List<MadoromiColors> = listOf(HARU_HIRU, NATSU_HIRU, AKI_HIRU, FUYU_HIRU, HARU_YORU, NATSU_YORU, AKI_YORU, FUYU_YORU)


    fun byKey(key: String): MadoromiColors = all.firstOrNull { it.key == key } ?: HARU_HIRU
}
