package com.coderang.letterboxdwallpaper

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
)

class DeviceCalendarManager(private val context: Context) {
    fun getWritableCalendars(): List<DeviceCalendar> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.SYNC_EVENTS,
        )
        val selection = buildString {
            append(CalendarContract.Calendars.VISIBLE)
            append(" = 1 AND ")
            append(CalendarContract.Calendars.SYNC_EVENTS)
            append(" = 1")
        }

        val calendars = mutableListOf<DeviceCalendar>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameColumn =
                cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountNameColumn =
                cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val accountTypeColumn =
                cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
            val accessColumn =
                cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)

            while (cursor.moveToNext()) {
                val accessLevel = cursor.getInt(accessColumn)
                if (accessLevel < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                    continue
                }

                calendars += DeviceCalendar(
                    id = cursor.getLong(idColumn),
                    displayName = cursor.getString(nameColumn).orEmpty(),
                    accountName = cursor.getString(accountNameColumn).orEmpty(),
                    accountType = cursor.getString(accountTypeColumn).orEmpty(),
                )
            }
        }

        return calendars.sortedWith(
            compareByDescending<DeviceCalendar> { it.accountType == GOOGLE_ACCOUNT_TYPE }
                .thenBy { it.displayName.lowercase(Locale.US) }
                .thenBy { it.accountName.lowercase(Locale.US) },
        )
    }

    fun insertEvent(moviePick: MoviePick, calendarId: Long): Long {
        val startMillis = parseMillis(
            moviePick.event.start.dateTime,
            moviePick.event.start.timeZone,
        )
        val endMillis = parseMillis(
            moviePick.event.end.dateTime,
            moviePick.event.end.timeZone,
        )

        val eventValues = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, moviePick.event.summary)
            put(CalendarContract.Events.DESCRIPTION, moviePick.event.description)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, moviePick.event.start.timeZone)
            put(CalendarContract.Events.EVENT_END_TIMEZONE, moviePick.event.end.timeZone)
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        }

        val eventUri = context.contentResolver.insert(
            CalendarContract.Events.CONTENT_URI,
            eventValues,
        ) ?: error("Calendar insert failed.")

        val eventId = eventUri.lastPathSegment?.toLongOrNull()
            ?: error("Missing calendar event ID.")

        insertReminder(eventId)
        return eventId
    }

    private fun insertReminder(eventId: Long) {
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, DEFAULT_REMINDER_MINUTES)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
    }

    private fun parseMillis(value: String, timeZoneId: String): Long {
        val formatter = SimpleDateFormat(DATE_TIME_PATTERN, Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone(timeZoneId)
        }

        return formatter.parse(value)?.time
            ?: error("Invalid event dateTime: $value")
    }

    companion object {
        private const val DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss"
        private const val DEFAULT_REMINDER_MINUTES = 30
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
