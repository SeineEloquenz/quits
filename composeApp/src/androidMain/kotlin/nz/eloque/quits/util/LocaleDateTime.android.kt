package nz.eloque.quits.util

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.text.format.DateFormat as AndroidDateFormat

private val utc = TimeZone.getTimeZone("UTC")

actual fun formatUtcDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).apply { timeZone = utc }.format(Date(epochMillis))

actual fun formatUtcTime(epochMillis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).apply { timeZone = utc }.format(Date(epochMillis))

// Locale picks the field order (day-first vs month-first, etc.) from the skeleton.
private fun formatSkeleton(
    skeleton: String,
    epochMillis: Long,
): String {
    val locale = Locale.getDefault()
    val pattern = AndroidDateFormat.getBestDateTimePattern(locale, skeleton)
    return SimpleDateFormat(pattern, locale).apply { timeZone = utc }.format(Date(epochMillis))
}

actual fun formatUtcMonthAbbrev(epochMillis: Long): String = formatSkeleton("LLL", epochMillis)

actual fun formatUtcMonthYear(epochMillis: Long): String = formatSkeleton("LLLLy", epochMillis)

actual fun formatUtcDayMonth(epochMillis: Long): String = formatSkeleton("dLLL", epochMillis)
