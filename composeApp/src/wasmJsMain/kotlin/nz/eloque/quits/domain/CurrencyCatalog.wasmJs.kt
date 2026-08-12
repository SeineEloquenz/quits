package nz.eloque.quits.domain

actual object CurrencyCatalog {
    actual fun codes(): List<String> = supportedCurrencyCodes().split(",").filter { it.isNotBlank() }

    actual fun nameOf(code: String): String = currencyDisplayName(code).ifBlank { code }

    actual fun decimalDigits(code: String): Int = currencyDecimalDigits(code)

    actual fun symbolOf(code: String): String? = currencySymbol(code).ifBlank { null }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun supportedCurrencyCodes(): String =
    js("(typeof Intl !== 'undefined' && Intl.supportedValuesOf ? Intl.supportedValuesOf('currency') : []).join(',')")

@OptIn(ExperimentalWasmJsInterop::class)
private fun currencyDisplayName(code: String): String = js("(new Intl.DisplayNames(undefined, { type: 'currency' })).of(code) || ''")

@OptIn(ExperimentalWasmJsInterop::class)
private fun currencyDecimalDigits(code: String): Int =
    js("(new Intl.NumberFormat(undefined, { style: 'currency', currency: code }).resolvedOptions().maximumFractionDigits)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun currencySymbol(code: String): String =
    js(
        "(new Intl.NumberFormat(undefined, { style: 'currency', currency: code, currencyDisplay: 'symbol' })" +
            ".formatToParts(0).find(p => p.type === 'currency')?.value || '')",
    )
