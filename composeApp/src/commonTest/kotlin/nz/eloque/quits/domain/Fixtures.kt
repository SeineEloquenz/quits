package nz.eloque.quits.domain

internal val USD = Currency.of("USD")
internal val EUR = Currency.of("EUR")
internal val JPY = Currency.of("JPY")

internal fun usd(cents: Long) = Money(cents, USD)

internal fun mid(value: String) = MemberId(value)

/** Fixed [NumberFormatSymbols] fakes, so parsing tests don't depend on the host machine's locale. */
internal class FakeNumberFormatSymbols(
    private val decimal: Char,
    private val grouping: Char?,
) : NumberFormatSymbols {
    override fun decimalSeparator(): Char = decimal

    override fun groupingSeparator(): Char? = grouping
}

internal val enUs = FakeNumberFormatSymbols(decimal = '.', grouping = ',')
internal val deDe = FakeNumberFormatSymbols(decimal = ',', grouping = '.')
