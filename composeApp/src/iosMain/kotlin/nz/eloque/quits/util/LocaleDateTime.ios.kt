package nz.eloque.quits.util

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

private fun nsDate(epochMillis: Long): NSDate = NSDate(timeIntervalSinceReferenceDate = epochMillis / 1000.0 - 978307200.0)

actual fun formatLocalDate(epochMillis: Long): String =
    NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        dateStyle = NSDateFormatterMediumStyle
        timeStyle = NSDateFormatterNoStyle
    }.stringFromDate(nsDate(epochMillis))

actual fun formatLocalTime(epochMillis: Long): String =
    NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        dateStyle = NSDateFormatterNoStyle
        timeStyle = NSDateFormatterShortStyle
    }.stringFromDate(nsDate(epochMillis))
