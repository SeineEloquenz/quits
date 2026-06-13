package nz.eloque.quits.data.fx

import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.Money
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrankfurterFxRateProviderIntegrationTest {
    private val usd = Currency.of("USD")
    private val eur = Currency.of("EUR")
    private val jpy = Currency.of("JPY")

    private val enabled =
        System.getenv("QUITS_RUN_NETWORK_TESTS")?.lowercase() in setOf("1", "true", "yes")

    private fun live(block: suspend (FrankfurterFxRateProvider) -> Unit) {
        if (!enabled) {
            println("Skipping live Frankfurter test; set QUITS_RUN_NETWORK_TESTS=true to enable.")
            return
        }
        val engine = OkHttp.create()
        try {
            runBlocking { block(FrankfurterFxRateProvider(engine)) }
        } finally {
            engine.close()
        }
    }

    @Test
    fun fetches_a_positive_usd_to_eur_rate() =
        live { provider ->
            val rate = provider.rate(usd, eur)
            assertEquals(usd, rate.from)
            assertEquals(eur, rate.to)
            assertTrue(rate.rate > 0.0, "expected a positive USD->EUR rate, got ${rate.rate}")
        }

    @Test
    fun inverse_rates_round_trip_near_one() =
        live { provider ->
            val forward = provider.rate(usd, eur).rate
            val back = provider.rate(eur, usd).rate
            val product = forward * back
            assertTrue(abs(product - 1.0) < 0.02, "USD->EUR->USD should be ~1.0, was $product")
        }

    @Test
    fun converts_a_concrete_amount_across_zero_decimal_currency() =
        live { provider ->
            // 100.00 USD -> JPY (0 decimal digits): sanity-check end-to-end convert against a live rate.
            val rate = provider.rate(usd, jpy)
            val converted = rate.convert(Money(10000, usd))
            assertEquals(jpy, converted.currency)
            assertTrue(converted.minorUnits > 0, "expected a positive JPY amount, got $converted")
        }

    @Test
    fun same_currency_short_circuits_without_a_call() =
        live { provider ->
            assertEquals(1.0, provider.rate(usd, usd).rate)
        }
}
