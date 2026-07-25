package nz.eloque.quits.domain

/**
 * A locale's conventions for typing/reading a decimal number: which character separates whole and
 * fractional digits, and which one (if any) groups the whole part into thousands
 * (e.g. "1,234.56" vs "1.234,56" vs "1 234,56").
 */
interface NumberFormatSymbols {
    /** The decimal separator, e.g. '.' for en-US or ',' for de-DE. */
    fun decimalSeparator(): Char

    /** The grouping (thousands) separator, e.g. ',' for en-US, '.' for de-DE, or null if none. */
    fun groupingSeparator(): Char?
}

/** [NumberFormatSymbols] for the current platform locale. */
expect object LocaleNumberFormat : NumberFormatSymbols {
    override fun decimalSeparator(): Char

    override fun groupingSeparator(): Char?
}
