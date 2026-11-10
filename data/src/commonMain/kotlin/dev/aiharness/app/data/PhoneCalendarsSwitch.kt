package dev.aiharness.app.data

import dev.aiharness.core.planner.PhoneCalendars

/** The phone's calendars the app reads, through [source] (the screenshot tour gives it a fake). */
class PhoneCalendarsSwitch(@Volatile var source: PhoneCalendars) : PhoneCalendars {
    override fun readable() = source.readable()
    override suspend fun calendars() = source.calendars()
    override suspend fun events(from: Long, to: Long, calendarIds: Set<String>) = source.events(from, to, calendarIds)
}
