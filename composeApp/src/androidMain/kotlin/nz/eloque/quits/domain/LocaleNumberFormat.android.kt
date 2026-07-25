package nz.eloque.quits.domain

import java.text.DecimalFormatSymbols
import java.util.Locale

actual object LocaleNumberFormat : NumberFormatSymbols {
    private fun symbols(): DecimalFormatSymbols = DecimalFormatSymbols.getInstance(Locale.getDefault())

    actual override fun decimalSeparator(): Char = symbols().decimalSeparator

    actual override fun groupingSeparator(): Char? = symbols().groupingSeparator
}
