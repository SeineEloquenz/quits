package nz.eloque.quits.data.sync

import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import nz.eloque.quits.data.db.inMemoryDatabase
import nz.eloque.quits.data.repository.GroupRepository
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.Expense
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.Group
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.Member
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Payment
import nz.eloque.quits.domain.Split
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two devices converging through the *real* relay over HTTP. Opt-in via QUITS_RUN_NETWORK_TESTS,
 * relay at QUITS_RELAY_URL (default http://127.0.0.1:8080).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncEngineIntegrationTest {
    private val enabled = System.getenv("QUITS_RUN_NETWORK_TESTS")?.lowercase() in setOf("1", "true", "yes")
    private val baseUrl = System.getenv("QUITS_RELAY_URL") ?: "http://127.0.0.1:8080"

    private val usd = Currency.of("USD")
    private val a = MemberId("a")
    private val b = MemberId("b")

    private fun live(block: suspend (Relay) -> Unit) {
        if (!enabled) {
            println("Skipping live sync test; set QUITS_RUN_NETWORK_TESTS=true (relay at $baseUrl).")
            return
        }
        val engine = OkHttp.create()
        try {
            runBlocking { block(RelayClient(engine, SyncConfig(baseUrl = baseUrl))) }
        } finally {
            engine.close()
        }
    }

    @Test
    fun two_devices_converge_via_real_relay() =
        live { relay ->
            var clock = 1000L
            val db1 = inMemoryDatabase()
            val repo1 = GroupRepository(db1, deviceId = "dev1", now = { clock })
            val engine1 = SyncEngine(db1, relay, deviceId = "dev1")
            val db2 = inMemoryDatabase()
            val repo2 = GroupRepository(db2, deviceId = "dev2", now = { clock })
            val engine2 = SyncEngine(db2, relay, deviceId = "dev2")

            try {
                val g = GroupId("g-local")
                repo1.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"), Member(b, "Bob"))))
                repo1.upsertExpense(
                    g,
                    Expense(ExpenseId("e1"), "Dinner", listOf(Payment(a, Money(3000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 1,
                )
                val handle = engine1.share(g)

                clock = 1500L
                val joined = engine2.join(handle.code)!!
                assertEquals(2, repo2.load(joined)!!.members.size)
                assertEquals(1, repo2.load(joined)!!.expenses.size)

                clock = 2000L
                repo2.upsertExpense(
                    joined,
                    Expense(ExpenseId("e2"), "Taxi", listOf(Payment(b, Money(1000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 2,
                )
                engine2.sync(joined)
                engine1.sync(g)

                val device1After = repo1.load(g)!!
                assertEquals(2, device1After.expenses.size)
                assertTrue(device1After.expenses.any { it.id == ExpenseId("e2") })
                assertEquals(
                    device1After.balances().net.mapKeys { it.key.value },
                    repo2.load(joined)!!.balances().net.mapKeys { it.key.value },
                )
            } finally {
                db1.close()
                db2.close()
            }
        }
}
