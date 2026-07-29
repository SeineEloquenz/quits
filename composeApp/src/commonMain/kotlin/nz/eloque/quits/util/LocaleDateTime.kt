package nz.eloque.quits.util

/**
 * Device-locale date/time formatting for the editor's Date and Time fields — respects the user's
 * locale and (for time) their 12/24-hour preference, unlike the ISO helpers used elsewhere. Each
 * platform delegates to its own locale machinery (java.text, NSDateFormatter, Intl).
 */
expect fun formatLocalDate(epochMillis: Long): String

expect fun formatLocalTime(epochMillis: Long): String

/** Locale date and short time together, e.g. "29 Jul 2026, 15:28". */
fun formatLocalDateTime(epochMillis: Long): String = "${formatLocalDate(epochMillis)}, ${formatLocalTime(epochMillis)}"
