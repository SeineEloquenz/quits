package nz.eloque.quits.data.sync

import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives the RelayClient against a real running relay to verify the wire format matches the server.
 * Opt-in: needs QUITS_RUN_NETWORK_TESTS set and a relay reachable at QUITS_RELAY_URL
 * (default http://127.0.0.1:8080). See crates/quits-server.
 */
class RelayClientIntegrationTest {
    private val enabled = System.getenv("QUITS_RUN_NETWORK_TESTS")?.lowercase() in setOf("1", "true", "yes")
    private val baseUrl = System.getenv("QUITS_RELAY_URL") ?: "http://127.0.0.1:8080"

    private fun live(block: suspend (RelayClient) -> Unit) {
        if (!enabled) {
            println("Skipping live relay test; set QUITS_RUN_NETWORK_TESTS=true (relay at $baseUrl).")
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
    fun create_push_pull_and_join_round_trip() =
        live { relay ->
            val handle = relay.createGroup()
            assertTrue(handle.remoteId.isNotEmpty())
            assertTrue(handle.code.isNotEmpty())

            val record =
                SyncRecord("m1", updatedAt = 1, deviceId = "devX", deleted = false, payload = SyncPayload.Member("m1", "Alice", null))
            val push = relay.push(handle.remoteId, handle.token, listOf(record))
            assertEquals(listOf("m1"), push.applied)
            assertTrue(push.seq > 0)

            val pull = relay.pull(handle.remoteId, handle.token, since = 0)
            val pulled = pull.records.single { it.id == "m1" }
            assertEquals(SyncPayload.Member("m1", "Alice", null), pulled.payload)

            // Joining by code lands on the same container and sees the same data.
            val joined = relay.joinGroup(handle.code)!!
            assertEquals(handle.remoteId, joined.remoteId)
            val viaJoin = relay.pull(joined.remoteId, joined.token, since = 0)
            assertTrue(viaJoin.records.any { it.id == "m1" })
        }

    @Test
    fun last_write_wins_rejects_stale_pushes() =
        live { relay ->
            val handle = relay.createGroup()
            val newer =
                SyncRecord("m1", updatedAt = 10, deviceId = "devX", deleted = false, payload = SyncPayload.Member("m1", "New", null))
            val older = SyncRecord("m1", updatedAt = 5, deviceId = "devX", deleted = false, payload = SyncPayload.Member("m1", "Old", null))
            assertEquals(listOf("m1"), relay.push(handle.remoteId, handle.token, listOf(newer)).applied)
            val stale = relay.push(handle.remoteId, handle.token, listOf(older))
            assertEquals(listOf("m1"), stale.rejected)

            val current = relay.pull(handle.remoteId, handle.token, 0).records.single { it.id == "m1" }
            assertEquals(SyncPayload.Member("m1", "New", null), current.payload)
        }

    @Test
    fun join_unknown_code_returns_null() =
        live { relay ->
            assertNull(relay.joinGroup("ZZZZZZZZZ"))
        }
}
