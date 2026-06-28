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
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
            var instance: String? = "sentinel"
            val relay =
                client { request ->
                    path = request.url.encodedPath
                    instance = request.headers["X-Quits-Instance"]
                    json("""{"group_id":"g1","code":"ABCDEF","token":"tok"}""")
                }
            val handle = relay.createGroup()
            assertEquals("/v1/groups", path)
            assertNull(instance) // no instance secret configured
            assertEquals(GroupHandle("g1", "ABCDEF", "tok"), handle)
        }

    @Test
    fun join_returns_null_on_404() =
        runTest {
            val relay = client { respond("", HttpStatusCode.NotFound) }
            assertNull(relay.joinGroup("NOPE"))
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
