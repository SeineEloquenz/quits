package nz.eloque.quits.data.fx

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import nz.eloque.quits.domain.Currency
import kotlin.test.Test
import kotlin.test.assertEquals

class FrankfurterFxRateProviderTest {
    private val usd = Currency.of("USD")
    private val eur = Currency.of("EUR")

    private fun providerReturning(
        json: String,
        capture: (path: String) -> Unit = {},
    ): FrankfurterFxRateProvider {
        val engine =
            MockEngine { request ->
                capture(request.url.fullPath)
                respond(
                    content = json,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        return FrankfurterFxRateProvider(engine, baseUrl = "https://example.test/v1")
    }

    @Test
    fun parses_rate_from_response() =
        runTest {
            val provider =
                providerReturning(
                    """{"amount":1.0,"base":"USD","date":"2026-06-12","rates":{"EUR":0.86453}}""",
                )
            val rate = provider.rate(usd, eur)
            assertEquals(usd, rate.from)
            assertEquals(eur, rate.to)
            assertEquals(0.86453, rate.rate)
        }

    @Test
    fun requests_base_and_symbols() =
        runTest {
            var path = ""
            val provider =
                providerReturning(
                    """{"amount":1.0,"base":"USD","date":"2026-06-12","rates":{"EUR":0.9}}""",
                ) { path = it }
            provider.rate(usd, eur)
            assertEquals("/v1/latest?base=USD&symbols=EUR", path)
        }

    @Test
    fun resolves_rate_regardless_of_vendor_key_casing() =
        runTest {
            val provider =
                providerReturning(
                    """{"amount":1.0,"base":"USD","date":"2026-06-12","rates":{"eur":0.9}}""",
                )
            assertEquals(0.9, provider.rate(usd, eur).rate)
        }

    @Test
    fun same_currency_short_circuits_without_a_request() =
        runTest {
            var called = false
            val provider = providerReturning("{}") { called = true }
            val rate = provider.rate(usd, usd)
            assertEquals(1.0, rate.rate)
            assertEquals(false, called)
        }
}
