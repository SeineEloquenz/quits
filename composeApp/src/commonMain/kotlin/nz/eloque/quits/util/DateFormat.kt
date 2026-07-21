@file:OptIn(ExperimentalTime::class)

package nz.eloque.quits.util

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Local ISO date, e.g. "2026-06-16". */
fun formatDate(epochMillis: Long): String =
    Instant
        .fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString()

/** Local date + 24h time, e.g. "2026-06-16 08:09". */
fun formatDateTime(epochMillis: Long): String {
    val dt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    val hh = dt.hour.toString().padStart(2, '0')
    val mm = dt.minute.toString().padStart(2, '0')
    return "${dt.date} $hh:$mm"
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
