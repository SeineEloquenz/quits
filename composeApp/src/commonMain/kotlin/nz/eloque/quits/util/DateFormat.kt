@file:OptIn(ExperimentalTime::class)

package nz.eloque.quits.util

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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
 * as its selected date, so the picker highlights the day the user actually sees for this expense.
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

/** Which relative-day bucket [epochMillis] falls into, in the device's local timezone. */
fun dayBucket(epochMillis: Long): DayBucket {
    val tz = TimeZone.currentSystemDefault()
    val day = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(tz).date
    val today = Clock.System.now().toLocalDateTime(tz).date
    return when (day) {
        today -> DayBucket.TODAY
        today.minus(DatePeriod(days = 1)) -> DayBucket.YESTERDAY
        else -> DayBucket.OTHER
    }
}
