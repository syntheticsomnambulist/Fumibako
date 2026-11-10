package dev.aiharness.app.ui

import dev.aiharness.app.ui.chat.letterStamp
import dev.aiharness.app.ui.planner.AppClock
import dev.aiharness.app.ui.planner.ClockFormat
import dev.aiharness.core.model.ClockStyle
import org.junit.After
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals

/** Times of day written anywhere in the app (a letter's stamp, a sticker's slip, the letters list) follow the clock setting. */
class ClockSettingTest {
    private val friday = LocalDateTime.of(2026, 11, 6, 14, 10).toInstant(ZoneOffset.UTC).toEpochMilli()

    @After
    fun reset() {
        AppClock.format = ClockFormat(ClockStyle.PHONE, true)
    }

    @Test
    fun stampsAndListTimesFollowTheClock() {
        AppClock.format = ClockFormat(ClockStyle.H12, phoneIs24 = true)
        assertEquals("FRI 2:10 pm", letterStamp(friday, ZoneOffset.UTC))
        assertEquals("2:10 pm", letterTime(friday, now = friday, zone = ZoneOffset.UTC))
        AppClock.format = ClockFormat(ClockStyle.H24, phoneIs24 = false)
        assertEquals("FRI 14:10", letterStamp(friday, ZoneOffset.UTC))
        // The phone's own setting, when that's chosen.
        AppClock.format = ClockFormat(ClockStyle.PHONE, phoneIs24 = false)
        assertEquals("FRI 2:10 pm", letterStamp(friday, ZoneOffset.UTC))
    }
}
