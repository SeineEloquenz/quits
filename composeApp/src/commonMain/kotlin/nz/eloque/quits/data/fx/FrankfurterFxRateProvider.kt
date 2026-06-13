package nz.eloque.quits.data.fx

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.ExchangeRate
import nz.eloque.quits.domain.FxRateProvider

/**
 * [FxRateProvider] backed by Frankfurter (https://frankfurter.dev) — keyless ECB reference rates.
 * `GET /v1/latest?base=USD&symbols=EUR` -> `{"base":"USD","date":"…","rates":{"EUR":0.86}}`.
 */
class FrankfurterFxRateProvider(
    engine: HttpClientEngine,
    private val baseUrl: String = "https://api.frankfurter.dev/v1",
) : FxRateProvider {
    private val client =
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

    override suspend fun rate(
        from: Currency,
        to: Currency,
    ): ExchangeRate {
        if (from == to) return ExchangeRate.identity(from)

        val response: LatestResponse =
            client
                .get("$baseUrl/latest") {
                    parameter("base", from.code)
                    parameter("symbols", to.code)
                }.body()

        val value =
            response.rateFor(to)
                ?: error("Frankfurter returned no rate for ${from.code}->${to.code}")
        return ExchangeRate(from, to, value)
    }

    @Serializable
    private data class LatestResponse(
        val rates: Map<String, Double>,
    ) {
        /** Looks up [currency]'s rate independent of how the vendor cased its keys. */
        fun rateFor(currency: Currency): Double? =
            rates.entries
                .firstOrNull { it.key.equals(currency.code, ignoreCase = true) }
                ?.value
    }
}
