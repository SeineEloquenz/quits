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
    fun caches_each_successful_fetch() =
        runTest {
            val delegate = FakeProvider(0.9)
            val caching = CachingFxRateProvider(delegate, db.fxRateDao(), now = { 100 })

            assertEquals(0.9, caching.rate(usd, eur).rate)
            assertEquals(0.9, db.fxRateDao().get("USD", "EUR")?.rate)
            assertEquals(100, db.fxRateDao().get("USD", "EUR")?.asOf)
        }

    @Test
    fun falls_back_to_cache_when_offline() =
        runTest {
            val delegate = FakeProvider(0.9)
            val caching = CachingFxRateProvider(delegate, db.fxRateDao(), now = { 100 })
            caching.rate(usd, eur) // primes the cache

            delegate.rate = null // go "offline"
            assertEquals(0.9, caching.rate(usd, eur).rate) // served from cache, no throw
        }

    @Test
    fun throws_when_offline_with_no_cache() =
        runTest {
            val caching = CachingFxRateProvider(FakeProvider(null), db.fxRateDao(), now = { 100 })
            assertFailsWith<IllegalStateException> { caching.rate(usd, eur) }
        }

    @Test
    fun same_currency_short_circuits_without_delegate_or_cache() =
        runTest {
            val delegate = FakeProvider(null) // would throw if called
            val caching = CachingFxRateProvider(delegate, db.fxRateDao(), now = { 100 })
            assertEquals(1.0, caching.rate(usd, usd).rate)
            assertEquals(0, delegate.calls)
        }
}
