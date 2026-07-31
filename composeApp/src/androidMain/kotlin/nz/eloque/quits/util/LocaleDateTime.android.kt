package nz.eloque.quits.util

import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

private val utc = TimeZone.getTimeZone("UTC")

actual fun formatUtcDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).apply { timeZone = utc }.format(Date(epochMillis))

actual fun formatUtcTime(epochMillis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).apply { timeZone = utc }.format(Date(epochMillis))
