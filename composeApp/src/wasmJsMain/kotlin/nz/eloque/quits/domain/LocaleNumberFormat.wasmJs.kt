package nz.eloque.quits.domain

actual object LocaleNumberFormat : NumberFormatSymbols {
    actual override fun decimalSeparator(): Char = localeDecimalSeparator().firstOrNull() ?: '.'

    actual override fun groupingSeparator(): Char? = localeGroupingSeparator().firstOrNull()
}

// Intl.NumberFormat().formatToParts is the standard way to discover a locale's decimal/grouping
// symbols in JS: format a number that has both a fractional and a grouped part, then read back
// which characters it used for each.
@OptIn(ExperimentalWasmJsInterop::class)
private fun localeDecimalSeparator(): String =
    js(
        "(new Intl.NumberFormat(undefined,{useGrouping:true}).formatToParts(1234.5).find(p=>p.type==='decimal')||{value:'.'}).value",
    )

@OptIn(ExperimentalWasmJsInterop::class)
private fun localeGroupingSeparator(): String =
    js(
        "(new Intl.NumberFormat(undefined,{useGrouping:true}).formatToParts(1234.5).find(p=>p.type==='group')||{value:''}).value",
    )
