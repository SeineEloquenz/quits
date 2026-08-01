package nz.eloque.quits.util

// Intl.DateTimeFormat with the runtime's default locale gives locale-aware date and (12/24h) time;
// timeZone: 'UTC' keeps the caller-supplied wall-clock instant from being re-shifted by the runtime.
actual fun formatUtcDate(epochMillis: Long): String = jsFormatDate(epochMillis.toDouble())

actual fun formatUtcTime(epochMillis: Long): String = jsFormatTime(epochMillis.toDouble())

private fun jsFormatDate(millis: Double): String =
    js("new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeZone: 'UTC' }).format(new Date(millis))")

private fun jsFormatTime(millis: Double): String =
    js("new Intl.DateTimeFormat(undefined, { timeStyle: 'short', timeZone: 'UTC' }).format(new Date(millis))")

actual fun formatUtcMonthAbbrev(epochMillis: Long): String = jsMonthAbbrev(epochMillis.toDouble())

actual fun formatUtcMonthYear(epochMillis: Long): String = jsMonthYear(epochMillis.toDouble())

actual fun formatUtcDayMonth(epochMillis: Long): String = jsDayMonth(epochMillis.toDouble())

private fun jsMonthAbbrev(millis: Double): String =
    js("new Intl.DateTimeFormat(undefined, { month: 'short', timeZone: 'UTC' }).format(new Date(millis))")

private fun jsMonthYear(millis: Double): String =
    js("new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(millis))")

private fun jsDayMonth(millis: Double): String =
    js("new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', timeZone: 'UTC' }).format(new Date(millis))")
