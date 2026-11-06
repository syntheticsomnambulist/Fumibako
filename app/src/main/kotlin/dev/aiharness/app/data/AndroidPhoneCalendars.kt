package dev.aiharness.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dev.aiharness.core.planner.PhoneCalendar
import dev.aiharness.core.planner.PhoneCalendars
import dev.aiharness.core.planner.PhoneEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The phone's calendars through `CalendarContract`: read-only (one permission, no sign-in), read
 * live each time and never copied. Repeats come expanded from `Instances`; an all-day event, which
 * the phone keeps in UTC, becomes one occurrence a day.
 */
class AndroidPhoneCalendars(private val context: Context) : PhoneCalendars {
    override fun readable(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    override suspend fun calendars(): List<PhoneCalendar> = withContext(Dispatchers.IO) {
        if (!readable()) return@withContext emptyList()
        val columns = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.OWNER_ACCOUNT,
        )
        val out = mutableListOf<PhoneCalendar>()
        runCatching {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1).orEmpty()
                    val owner = c.getString(4).orEmpty()
                    out += PhoneCalendar(
                        id = c.getLong(0).toString(),
                        name = name.ifBlank { "Calendar" },
                        account = c.getString(2).orEmpty(),
                        color = if (c.isNull(3)) null else c.getInt(3),
                        // Google's holiday calendars are owned by "...#holiday@group.v.calendar.google.com".
                        holidays = "#holiday@" in owner || name.contains("holiday", ignoreCase = true),
                    )
                }
            }
        }
        out.sortedWith(compareBy({ it.account }, { it.name }))
    }

    override suspend fun events(from: Long, to: Long, calendarIds: Set<String>): List<PhoneEvent> = withContext(Dispatchers.IO) {
        if (!readable() || calendarIds.isEmpty()) return@withContext emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val columns = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.AVAILABILITY,
            CalendarContract.Instances.STATUS,
        )
        val ids = calendarIds.mapNotNull { it.toLongOrNull() }
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN (${ids.joinToString(",")})"
        val out = mutableListOf<PhoneEvent>()
        runCatching {
            context.contentResolver.query(uri, columns, selection, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    // A cancelled event isn't on.
                    if (!c.isNull(8) && c.getInt(8) == CalendarContract.Events.STATUS_CANCELED) continue
                    val eventId = c.getLong(0)
                    val calendarId = c.getLong(1).toString()
                    val title = c.getString(2).orEmpty()
                    val place = c.getString(3)
                    val begin = c.getLong(4)
                    val end = c.getLong(5)
                    val allDay = c.getInt(6) == 1
                    // Busy and tentative are busy; only "free" isn't.
                    val busy = c.isNull(7) || c.getInt(7) != CalendarContract.Events.AVAILABILITY_FREE
                    if (allDay) {
                        var day = LocalDate.ofInstant(Instant.ofEpochMilli(begin), ZoneOffset.UTC)
                        val last = LocalDate.ofInstant(Instant.ofEpochMilli(end - 1), ZoneOffset.UTC)
                        while (!day.isAfter(last)) {
                            val dayStart = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                            out += PhoneEvent(eventId, calendarId, title, place, dayStart, dayStart + 86_400_000L, allDay = true, busy = busy, day = day.toString())
                            day = day.plusDays(1)
                        }
                    } else {
                        out += PhoneEvent(eventId, calendarId, title, place, begin, end, busy = busy)
                    }
                }
            }
        }
        out
    }
}

/** The phone's calendars the app reads, through [source] (the screenshot tour gives it a fake). */
class PhoneCalendarsSwitch(@Volatile var source: PhoneCalendars) : PhoneCalendars {
    override fun readable() = source.readable()
    override suspend fun calendars() = source.calendars()
    override suspend fun events(from: Long, to: Long, calendarIds: Set<String>) = source.events(from, to, calendarIds)
}
