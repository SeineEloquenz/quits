package nz.eloque.quits.domain

internal val USD = Currency.of("USD")
internal val EUR = Currency.of("EUR")
internal val JPY = Currency.of("JPY")

internal fun usd(cents: Long) = Money(cents, USD)

internal fun mid(value: String) = MemberId(value)
