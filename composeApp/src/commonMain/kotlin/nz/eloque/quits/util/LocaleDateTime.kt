package nz.eloque.quits.util

/**
 * Device-locale date/time formatting. Each platform delegates to its own locale machinery
 * (java.text, NSDateFormatter, Intl) and formats the instant as a **UTC** wall-clock — the common
 * [formatLocalDate]/[formatLocalTime] wrappers shift the instant into the desired offset first, so
 * locale (month names, 12/24h) follows the reader while the calendar day/time follows the offset the
 * value was captured in.
 */
expect fun formatUtcDate(epochMillis: Long): String

expect fun formatUtcTime(epochMillis: Long): String

/** Localized abbreviated standalone month, e.g. "Jul" / "Juli". */
expect fun formatUtcMonthAbbrev(epochMillis: Long): String

/** Localized month and year, e.g. "July 2026" / "Juli 2026". */
expect fun formatUtcMonthYear(epochMillis: Long): String

/** Localized day and abbreviated month, e.g. "21 Jul". */
expect fun formatUtcDayMonth(epochMillis: Long): String

private fun shift(
    epochMillis: Long,
    offsetMinutes: Int,
): Long = epochMillis + offsetMinutes * 60_000L

/** Locale date for [epochMillis] rendered in [offsetMinutes] (defaults to the device's current offset). */
fun formatLocalDate(
    epochMillis: Long,
    offsetMinutes: Int = currentOffsetMinutes(epochMillis),
): String = formatUtcDate(shift(epochMillis, offsetMinutes))

/** Locale short time for [epochMillis] rendered in [offsetMinutes] (defaults to the device's current offset). */
fun formatLocalTime(
    epochMillis: Long,
    offsetMinutes: Int = currentOffsetMinutes(epochMillis),
): String = formatUtcTime(shift(epochMillis, offsetMinutes))

/** Locale date and short time together, e.g. "29 Jul 2026, 15:28". */
fun formatLocalDateTime(
    epochMillis: Long,
    offsetMinutes: Int = currentOffsetMinutes(epochMillis),
): String = "${formatLocalDate(epochMillis, offsetMinutes)}, ${formatLocalTime(epochMillis, offsetMinutes)}"
