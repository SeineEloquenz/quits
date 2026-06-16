package nz.eloque.quits.data.fx

import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.ExchangeRate

/** A fetched rate plus where it came from, so the UI can flag stale/cached values. */
sealed interface RateResult {
    val rate: ExchangeRate

    /** Freshly fetched from the live provider. */
    data class Live(
        override val rate: ExchangeRate,
    ) : RateResult

    /** Served from the local cache because the live fetch failed; [asOf] is when it was cached. */
    data class Cached(
        override val rate: ExchangeRate,
        val asOf: Long,
    ) : RateResult
}

/** Cache-aware FX lookup for the UI: live when possible, cached otherwise; throws if neither exists. */
interface FxRates {
    suspend fun fetch(
        from: Currency,
        to: Currency,
    ): RateResult
}
