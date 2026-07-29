package nz.eloque.quits.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class DateFormatTest {
    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = LocalDateTime(LocalDate(year, month, day), LocalTime(hour, minute)).toInstant(TimeZone.UTC).toEpochMilliseconds()

    @Test
    fun with_picked_date_moves_the_day_but_keeps_the_time() {
        val original = millis(2026, 7, 19, 8, 30)
        val pickedDay = LocalDate(2026, 7, 15).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

        val result = Instant.fromEpochMilliseconds(withPickedDate(original, pickedDay, TimeZone.UTC)).toLocalDateTime(TimeZone.UTC)

        assertEquals(LocalDate(2026, 7, 15), result.date)
        assertEquals(8, result.hour)
        assertEquals(30, result.minute)
    }

    @Test
    fun with_picked_time_sets_the_time_but_keeps_the_day() {
        val result =
            Instant
                .fromEpochMilliseconds(withPickedTime(millis(2026, 7, 19, 8, 30), 21, 5, TimeZone.UTC))
                .toLocalDateTime(TimeZone.UTC)

        assertEquals(LocalDate(2026, 7, 19), result.date)
        assertEquals(21, result.hour)
        assertEquals(5, result.minute)
    }

    @Test
    fun local_date_millis_is_utc_midnight_of_the_local_day() {
        val result = localDateMillisUtc(millis(2026, 7, 19, 23, 45), TimeZone.UTC)

        val dateTime = Instant.fromEpochMilliseconds(result).toLocalDateTime(TimeZone.UTC)
        assertEquals(LocalDate(2026, 7, 19), dateTime.date)
        assertEquals(0, dateTime.hour)
        assertEquals(0, dateTime.minute)
    }
}
