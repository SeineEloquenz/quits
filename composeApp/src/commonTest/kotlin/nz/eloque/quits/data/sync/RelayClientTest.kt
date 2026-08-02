package nz.eloque.quits.data.sync

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalEncodingApi::class)
class RelayClientTest {
    private val settings = InMemorySyncSettings(relayUrl = "https://relay.test")

    private fun client(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): RelayClient =
        RelayClient(MockEngine(handler), settings)

    private fun MockRequestHandleScope.json(body: String): HttpResponseData =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun create_group_posts_and_parses_handle() =
        runTest {
            var path = ""
            var body = ""
            var instance: String? = "sentinel"
            val relay =
                client { request ->
                    path = request.url.encodedPath
                    body = (request.body as TextContent).text
                    instance = request.headers["X-Quits-Instance"]
                    json("""{"group_id":"g1","token":"tok"}""")
                }
            val handle = relay.createGroup("look-1")
            assertEquals("/v1/groups", path)
            assertTrue(body.contains("\"lookup_id\":\"look-1\""), body)
            assertNull(instance) // no instance secret configured
            assertEquals(GroupHandle("g1", "tok"), handle)
        }

    @Test
    fun join_returns_null_on_404() =
        runTest {
            val relay = client { respond("", HttpStatusCode.NotFound) }
            assertNull(relay.joinGroup("NOPE"))
        }

    @Test
    fun bad_request_carries_server_detail() =
        runTest {
            val relay =
                client {
                    respond(
                        """{"error":"group already exists"}""",
                        HttpStatusCode.BadRequest,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val error = assertFailsWith<SyncError.BadRequest> { relay.createGroup("look-1") }
            assertEquals("group already exists", error.detail)
        }

    @Test
    fun forbidden_maps_to_unauthorized() =
        runTest {
            val relay = client { respond("""{"error":"forbidden"}""", HttpStatusCode.Forbidden) }
            assertFailsWith<SyncError.Unauthorized> { relay.createGroup("look-1") }
        }

    @Test
    fun rate_limited_parses_retry_after() =
        runTest {
            val relay =
                client {
                    respond(
                        "Too Many Requests! Wait for 30s",
                        HttpStatusCode.TooManyRequests,
                        headersOf(HttpHeaders.RetryAfter, "30"),
                    )
                }
            val error = assertFailsWith<SyncError.RateLimited> { relay.createGroup("look-1") }
            assertEquals(30.seconds, error.retryAfter)
        }

    @Test
    fun capacity_maps_to_server_unavailable() =
        runTest {
            val relay = client { respond("""{"error":"server at capacity"}""", HttpStatusCode.ServiceUnavailable) }
            assertFailsWith<SyncError.ServerUnavailable> { relay.createGroup("look-1") }
        }

    @Test
    fun transport_failure_maps_to_unreachable() =
        runTest {
            val relay = client { throw RuntimeException("connection refused") }
            assertFailsWith<SyncError.Unreachable> { relay.createGroup("look-1") }
        }

    @Test
    fun push_surfaces_group_gone_on_404() =
        runTest {
            val relay = client { respond("""{"error":"not found"}""", HttpStatusCode.NotFound) }
            val record = EncryptedRecord("m1", updatedAt = 1, deviceId = "dev", deleted = false, ciphertext = byteArrayOf(1))
            assertFailsWith<SyncError.GroupGone> { relay.push("rid", "tok", listOf(record)) }
        }

    @Test
    fun push_sends_bearer_and_base64_payload() =
        runTest {
            var auth: String? = null
            var body = ""
            val relay =
                client { request ->
                    auth = request.headers[HttpHeaders.Authorization]
                    body = (request.body as TextContent).text
                    json("""{"seq":5,"applied":["m1"],"rejected":[]}""")
                }
            val ciphertext = byteArrayOf(1, 2, 3, 4)
            val record = EncryptedRecord("m1", updatedAt = 7, deviceId = "dev", deleted = false, ciphertext = ciphertext)
            val result = relay.push("rid", "tok", listOf(record))

            assertEquals("Bearer tok", auth)
            assertTrue(body.contains("\"device_id\":\"dev\""), body)
            assertTrue(body.contains(Base64.encode(ciphertext)), "expected base64 payload in body")
            assertEquals(PushResult(5, listOf("m1"), emptyList()), result)
        }

    @Test
    fun pull_decodes_records_and_passes_since() =
        runTest {
            val ciphertext = byteArrayOf(9, 8, 7)
            val payload = Base64.encode(ciphertext)
            var since = -1L
            var auth: String? = null
            val relay =
                client { request ->
                    since = request.url.parameters["since"]?.toLong() ?: -1L
                    auth = request.headers[HttpHeaders.Authorization]
                    json(
                        """{"records":[{"id":"m1","updated_at":9,"deleted":false,"device_id":"dev","payload":"$payload","server_seq":3}],"seq":3}""",
                    )
                }
            val result = relay.pull("rid", "tok", since = 2)

            assertEquals(2, since)
            assertEquals("Bearer tok", auth)
            assertEquals(3, result.seq)
            val record = result.records.single()
            assertEquals("m1", record.id)
            assertContentEquals(ciphertext, record.ciphertext)
        }
}
