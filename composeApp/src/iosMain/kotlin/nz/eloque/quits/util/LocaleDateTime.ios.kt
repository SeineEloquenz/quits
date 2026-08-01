package nz.eloque.quits.util

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.timeZoneWithAbbreviation

private fun nsDate(epochMillis: Long): NSDate = NSDate(timeIntervalSinceReferenceDate = epochMillis / 1000.0 - 978307200.0)

private val utc = NSTimeZone.timeZoneWithAbbreviation("UTC")!!

actual fun formatUtcDate(epochMillis: Long): String =
    NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        timeZone = utc
        dateStyle = NSDateFormatterMediumStyle
        timeStyle = NSDateFormatterNoStyle
    }.stringFromDate(nsDate(epochMillis))

actual fun formatUtcTime(epochMillis: Long): String =
    NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        timeZone = utc
        dateStyle = NSDateFormatterNoStyle
        timeStyle = NSDateFormatterShortStyle
    }.stringFromDate(nsDate(epochMillis))

// The template gives the fields; the locale reorders them and localizes the month name.
private fun formatTemplate(
    template: String,
    epochMillis: Long,
): String =
    NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        timeZone = utc
        setLocalizedDateFormatFromTemplate(template)
    }.stringFromDate(nsDate(epochMillis))

actual fun formatUtcMonthAbbrev(epochMillis: Long): String = formatTemplate("LLL", epochMillis)

actual fun formatUtcMonthYear(epochMillis: Long): String = formatTemplate("LLLLy", epochMillis)

actual fun formatUtcDayMonth(epochMillis: Long): String = formatTemplate("dLLL", epochMillis)
