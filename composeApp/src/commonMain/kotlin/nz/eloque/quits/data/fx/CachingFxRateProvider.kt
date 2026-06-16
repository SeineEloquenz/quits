package nz.eloque.quits.data.fx

import kotlinx.coroutines.CancellationException
import nz.eloque.quits.data.db.FxRateDao
import nz.eloque.quits.data.db.FxRateEntity
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.ExchangeRate
import nz.eloque.quits.domain.FxRateProvider

/**
 * Wraps a live [FxRateProvider] with a local cache. Network-first so the rate captured at entry is
 * current; each success is cached, and when the fetch fails we fall back to the last cached rate.
 * The cache is local-only (never synced). Throws if offline with no cached rate for the pair.
 */
class CachingFxRateProvider(
    private val delegate: FxRateProvider,
    private val dao: FxRateDao,
    private val now: () -> Long,
) : FxRateProvider {
    override suspend fun rate(
        from: Currency,
        to: Currency,
    ): ExchangeRate {
        if (from == to) return ExchangeRate.identity(from)
        return try {
            val live = delegate.rate(from, to)
            dao.put(FxRateEntity(from.code, to.code, live.rate, asOf = now()))
            live
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cached = dao.get(from.code, to.code) ?: throw e
            ExchangeRate(from, to, cached.rate)
        }
    }
}
