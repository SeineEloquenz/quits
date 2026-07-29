package nz.eloque.quits.util

// Intl.DateTimeFormat with the runtime's default locale gives locale-aware date and (12/24h) time.
actual fun formatLocalDate(epochMillis: Long): String = jsFormatDate(epochMillis.toDouble())

actual fun formatLocalTime(epochMillis: Long): String = jsFormatTime(epochMillis.toDouble())

private fun jsFormatDate(millis: Double): String =
    js("new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(millis))")

private fun jsFormatTime(millis: Double): String = js("new Intl.DateTimeFormat(undefined, { timeStyle: 'short' }).format(new Date(millis))")
