package nz.eloque.quits.domain

import platform.Foundation.NSLocale
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.currentLocale

private val formatter: NSNumberFormatter by lazy {
    NSNumberFormatter().apply {
        locale = NSLocale.currentLocale
        numberStyle = NSNumberFormatterDecimalStyle
    }
}

actual object LocaleNumberFormat : NumberFormatSymbols {
    actual override fun decimalSeparator(): Char = formatter.decimalSeparator?.firstOrNull() ?: '.'

    actual override fun groupingSeparator(): Char? = formatter.groupingSeparator?.firstOrNull()
}
