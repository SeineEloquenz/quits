package nz.eloque.quits.data.fx

import kotlinx.coroutines.CancellationException
import nz.eloque.quits.data.db.FxRateDao
import nz.eloque.quits.data.db.FxRateEntity
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.ExchangeRate
import nz.eloque.quits.domain.FxRateProvider

/**
 * Cache-aware FX over a live [delegate]. Network-first so the rate captured at entry is current;
 * each success is cached, and when the fetch fails we serve the last cached rate ([RateResult.Cached]
 * with its timestamp). Throws if offline with no cached rate for the pair. Cache is local-only.
 */
class CachingFxRateProvider(
    private val delegate: FxRateProvider,
    private val dao: FxRateDao,
    private val now: () -> Long,
) : FxRates {
    override suspend fun fetch(
        from: Currency,
        to: Currency,
    ): RateResult {
        if (from == to) return RateResult.Live(ExchangeRate.identity(from))
        return try {
            val live = delegate.rate(from, to)
            dao.put(FxRateEntity(from.code, to.code, live.rate, asOf = now()))
            RateResult.Live(live)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cached = dao.get(from.code, to.code) ?: throw e
            RateResult.Cached(ExchangeRate(from, to, cached.rate), cached.asOf)
        }
    }
}
