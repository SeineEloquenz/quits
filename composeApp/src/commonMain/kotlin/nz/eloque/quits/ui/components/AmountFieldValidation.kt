package nz.eloque.quits.ui.components

import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.LocaleNumberFormat
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.NumberFormatSymbols

/** Whether [text] is an acceptable amount for [currency]: blank counts as not-yet-entered, otherwise it must parse via [Money.parse] (and be positive if [requirePositive]). */
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
