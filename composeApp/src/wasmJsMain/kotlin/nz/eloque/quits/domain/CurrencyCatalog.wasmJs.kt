package nz.eloque.quits.domain

actual object CurrencyCatalog {
    // Browser Intl APIs: supportedValuesOf('currency') for the ISO-4217 list, DisplayNames for names.
    actual fun codes(): List<String> = supportedCurrencyCodes().split(",").filter { it.isNotBlank() }

    actual fun nameOf(code: String): String = currencyDisplayName(code).ifBlank { code }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun supportedCurrencyCodes(): String =
    js("(typeof Intl !== 'undefined' && Intl.supportedValuesOf ? Intl.supportedValuesOf('currency') : []).join(',')")

@OptIn(ExperimentalWasmJsInterop::class)
private fun currencyDisplayName(code: String): String = js("(new Intl.DisplayNames(undefined, { type: 'currency' })).of(code) || ''")
