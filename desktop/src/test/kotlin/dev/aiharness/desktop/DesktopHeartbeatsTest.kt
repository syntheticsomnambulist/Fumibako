package dev.aiharness.desktop

import dev.aiharness.app.platform.Wake
import dev.aiharness.core.model.HeartbeatMode
import dev.aiharness.core.model.HeartbeatSettings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The desktop's heartbeats keep to the wall clock: the first a minute after starting, then every
 * interval, and a minute after the computer wakes if one came due while it slept; reminders at
 * their time, or on waking.
 */
class DesktopHeartbeatsTest {
    private var now = 1_000_000_000L
    private val runs = mutableListOf<String?>()
    private val heartbeats = DesktopHeartbeats(clock = { now }, autoStart = false).apply {
        run = { _, persona -> runs += persona; emptyList() }
    }
    private val on = HeartbeatSettings(mode = HeartbeatMode.PROACTIVE)

    /** Time passing while the app is awake: a check every tick, as the running loop does. */
    private fun awake(ms: Long) = runBlocking {
        val until = now + ms
        while (now < until) {
            now = minOf(now + DesktopHeartbeats.TICK, until)
            heartbeats.check()
        }
    }

    /** The computer asleep: no checks, then the first one on waking. */
    private fun asleep(ms: Long) = runBlocking {
        now += ms
        heartbeats.check()
    }

    @Test
    fun firstAMinuteAfterStartingThenEveryInterval() {
        heartbeats.apply(on, intervalMinutes = 30)
        awake(50_000)
        assertEquals(emptyList(), runs)
        awake(20_000)
        assertEquals(listOf<String?>(null), runs)
        awake(29 * 60_000L)
        assertEquals(1, runs.size)
        awake(2 * 60_000L)
        assertEquals(2, runs.size)
    }

    @Test
    fun aHeartbeatDueInSleepComesAMinuteAfterWaking() {
        heartbeats.apply(on, intervalMinutes = 30)
        awake(2 * 60_000L)
        assertEquals(1, runs.size)
        asleep(5 * 60 * 60_000L)
        // Not straight away: the network may not be back yet.
        assertEquals(1, runs.size)
        awake(70_000)
        assertEquals(2, runs.size)
        // Then the usual pace again, not one per missed interval.
        awake(20 * 60_000L)
        assertEquals(2, runs.size)
    }

    @Test
    fun aShortSleepBeforeTheNextHeartbeatChangesNothing() {
        heartbeats.apply(on, intervalMinutes = 60)
        awake(2 * 60_000L)
        val next = heartbeats.nextBeat()
        asleep(10 * 60_000L)
        assertEquals(next, heartbeats.nextBeat())
    }

    @Test
    fun remindersWakeTheirPersonaAtTheirTimeOrOnWaking() {
        heartbeats.apply(on, intervalMinutes = 120)
        awake(2 * 60_000L)
        runs.clear()
        heartbeats.wakeFor(on, setOf(Wake("e1", "aria", now + 10 * 60_000L), Wake("e2", "ren", now + 3 * 60 * 60_000L)))
        awake(10 * 60_000L)
        assertEquals(emptyList(), runs, "half a minute late, as on the phone")
        awake(40_000)
        assertEquals(listOf<String?>("aria"), runs)
        // Ren's falls while the computer sleeps (and so does the heartbeat): one heartbeat on waking covers it.
        asleep(4 * 60 * 60_000L)
        awake(70_000)
        assertEquals(listOf<String?>("aria", null), runs)
    }

    @Test
    fun aReminderAloneOnWakingWakesJustItsPersona() {
        heartbeats.apply(on, intervalMinutes = 24 * 60)
        awake(2 * 60_000L)
        runs.clear()
        heartbeats.wakeFor(on, setOf(Wake("e1", "aria", now + 30 * 60_000L)))
        asleep(60 * 60_000L)
        awake(70_000)
        assertEquals(listOf<String?>("aria"), runs)
    }

    @Test
    fun offMeansNoHeartbeatsOrReminders() {
        val off = HeartbeatSettings(mode = HeartbeatMode.TIME_AWARENESS)
        heartbeats.apply(off, intervalMinutes = 30)
        heartbeats.wakeFor(off, setOf(Wake("e1", "aria", now + 60_000L)))
        awake(3 * 60 * 60_000L)
        assertEquals(emptyList(), runs)
        assertNull(heartbeats.nextBeat())
    }
}
