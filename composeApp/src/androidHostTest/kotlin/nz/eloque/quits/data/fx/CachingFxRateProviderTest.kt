package nz.eloque.quits.data.fx

import kotlinx.coroutines.test.runTest
import nz.eloque.quits.data.db.inMemoryDatabase
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.ExchangeRate
import nz.eloque.quits.domain.FxRateProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CachingFxRateProviderTest {
    private val usd = Currency.of("USD")
    private val eur = Currency.of("EUR")

    private val db = inMemoryDatabase()

    @AfterTest fun tearDown() = db.close()

    /** A delegate whose result/availability the test controls. */
    private class FakeProvider(
        var rate: Double?,
    ) : FxRateProvider {
        var calls = 0

        override suspend fun rate(
            from: Currency,
            to: Currency,
        ): ExchangeRate {
            calls += 1
            val r = rate ?: throw IllegalStateException("offline")
            return ExchangeRate(from, to, r)
        }
    }

    @Test
    fun live_fetch_is_marked_live_and_cached() =
        runTest {
            val delegate = FakeProvider(0.9)
            val caching = CachingFxRateProvider(delegate, db.fxRateDao(), now = { 100 })

            val result = caching.fetch(usd, eur)
            assertIs<RateResult.Live>(result)
            assertEquals(0.9, result.rate.rate)
            assertEquals(0.9, db.fxRateDao().get("USD", "EUR")?.rate)
            assertEquals(100, db.fxRateDao().get("USD", "EUR")?.asOf)
        }

    @Test
    fun falls_back_to_cache_when_offline_with_timestamp() =
        runTest {
            val delegate = FakeProvider(0.9)
            val caching = CachingFxRateProvider(delegate, db.fxRateDao(), now = { 100 })
            caching.fetch(usd, eur) // primes the cache at asOf=100

            delegate.rate = null // go "offline"
            val result = caching.fetch(usd, eur)
            assertIs<RateResult.Cached>(result)
            assertEquals(0.9, result.rate.rate)
            assertEquals(100, result.asOf)
        }

    @Test
    fun throws_when_offline_with_no_cache() =
        runTest {
            val caching = CachingFxRateProvider(FakeProvider(null), db.fxRateDao(), now = { 100 })
            assertFailsWith<IllegalStateException> { caching.fetch(usd, eur) }
        }

    @Test
    fun same_currency_short_circuits_without_delegate_or_cache() =
        runTest {
            val delegate = FakeProvider(null) // would throw if called
            val caching = CachingFxRateProvider(delegate, db.fxRateDao(), now = { 100 })
            val result = caching.fetch(usd, usd)
            assertIs<RateResult.Live>(result)
            assertEquals(1.0, result.rate.rate)
            assertEquals(0, delegate.calls)
        }
}
