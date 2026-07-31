@file:OptIn(ExperimentalTime::class)

package nz.eloque.quits.util

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The device's current UTC offset in minutes at [atMillis] (defaults to now). */
fun currentOffsetMinutes(atMillis: Long = nowMillis()): Int =
    TimeZone.currentSystemDefault().offsetAt(Instant.fromEpochMilliseconds(atMillis)).totalSeconds / 60

/** A fixed-offset timezone for [offsetMinutes] — used to render a captured wall-clock consistently. */
fun offsetZone(offsetMinutes: Int): TimeZone = UtcOffset(minutes = offsetMinutes).asTimeZone()

/** [spentAt]'s local hour and minute — used to seed the time picker. */
fun localHourMinute(
    spentAt: Long,
    timeZone: TimeZone,
): Pair<Int, Int> {
    val dt = Instant.fromEpochMilliseconds(spentAt).toLocalDateTime(timeZone)
    return dt.hour to dt.minute
}

/** Replaces [spentAt]'s time-of-day with [hour]:[minute], keeping its calendar day. */
fun withPickedTime(
    spentAt: Long,
    hour: Int,
    minute: Int,
    timeZone: TimeZone,
): Long {
    val date = Instant.fromEpochMilliseconds(spentAt).toLocalDateTime(timeZone).date
    return LocalDateTime(date, LocalTime(hour, minute)).toInstant(timeZone).toEpochMilliseconds()
}

/**
 * The UTC-midnight millis of [spentAt]'s *local* calendar day — the form Material's DatePicker wants
 * as its selected date, so the picker highlights the day the user actually sees for this entry.
 */
fun localDateMillisUtc(
    spentAt: Long,
    timeZone: TimeZone,
): Long =
    Instant
        .fromEpochMilliseconds(spentAt)
        .toLocalDateTime(timeZone)
        .date
        .atStartOfDayIn(TimeZone.UTC)
        .toEpochMilliseconds()

/**
 * Replaces the calendar day of [spentAt] with the day picked in the DatePicker ([pickedUtcMidnight]
 * is its UTC-midnight value), keeping the original local time-of-day. So a backdate moves the day
 * without disturbing the time, and the result's local date is exactly the day the user tapped.
 */
fun withPickedDate(
    spentAt: Long,
    pickedUtcMidnight: Long,
    timeZone: TimeZone,
): Long {
    val pickedDay = Instant.fromEpochMilliseconds(pickedUtcMidnight).toLocalDateTime(TimeZone.UTC).date
    val time = Instant.fromEpochMilliseconds(spentAt).toLocalDateTime(timeZone).time
    return LocalDateTime(pickedDay, time).toInstant(timeZone).toEpochMilliseconds()
}

enum class DayBucket { TODAY, YESTERDAY, OTHER }

/**
 * Which relative-day bucket [epochMillis] falls into. The record's day is read in its captured
 * [offsetMinutes] (so it matches the date shown), while "today" is the viewer's own current day.
 */
fun dayBucket(
    epochMillis: Long,
    offsetMinutes: Int = currentOffsetMinutes(epochMillis),
): DayBucket {
    val day = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(offsetZone(offsetMinutes)).date
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return when (day) {
        today -> DayBucket.TODAY
        today.minus(DatePeriod(days = 1)) -> DayBucket.YESTERDAY
        else -> DayBucket.OTHER
    }
}
