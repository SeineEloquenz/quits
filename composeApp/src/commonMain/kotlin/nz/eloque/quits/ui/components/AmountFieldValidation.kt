package nz.eloque.quits.ui.components

import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.LocaleNumberFormat
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.NumberFormatSymbols

/**
 * Whether [text] is an acceptable amount for [currency] right now. A blank field counts as "not
 * entered yet" rather than invalid, so fields don't flash red before the person has typed
 * anything (matches how these fields already treat blank as "skip this row" on save). Anything
 * else must parse via [Money.parse] — locale-aware, so "1.234,56" is fine under a de-DE locale
 * just as "1,234.56" is under en-US — and, if [requirePositive], be greater than zero.
 */
fun isValidAmountInput(
    text: String,
    currency: Currency?,
    requirePositive: Boolean = true,
    format: NumberFormatSymbols = LocaleNumberFormat,
): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return true
    val money = currency?.let { Money.parse(trimmed, it, format) } ?: return false
    return !requirePositive || money.isPositive
}
