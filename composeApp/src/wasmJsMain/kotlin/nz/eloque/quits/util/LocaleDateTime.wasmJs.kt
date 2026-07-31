package nz.eloque.quits.util

// Intl.DateTimeFormat with the runtime's default locale gives locale-aware date and (12/24h) time;
// timeZone: 'UTC' keeps the caller-supplied wall-clock instant from being re-shifted by the runtime.
actual fun formatUtcDate(epochMillis: Long): String = jsFormatDate(epochMillis.toDouble())

actual fun formatUtcTime(epochMillis: Long): String = jsFormatTime(epochMillis.toDouble())

private fun jsFormatDate(millis: Double): String =
    js("new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeZone: 'UTC' }).format(new Date(millis))")

private fun jsFormatTime(millis: Double): String =
    js("new Intl.DateTimeFormat(undefined, { timeStyle: 'short', timeZone: 'UTC' }).format(new Date(millis))")
