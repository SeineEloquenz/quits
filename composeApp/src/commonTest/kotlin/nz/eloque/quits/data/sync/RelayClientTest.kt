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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import nz.eloque.quits.BuildInfo
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** Just past the assumed-limits window, so the next call asks the relay again. */
private val ASSUMED_INFO_TTL_TEST = ASSUMED_INFO_TTL + 1.seconds

/** Past the probe's own bound but well inside the client-wide one, so only the probe can end the wait. */
private val HUNG_PROBE_DELAY = INFO_TIMEOUT * 2

@OptIn(ExperimentalEncodingApi::class)
class RelayClientTest {
    private val settings = InMemorySyncSettings(relayUrl = "https://relay.test")

    private val clock = TestTimeSource()

    private fun client(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): RelayClient =
        RelayClient(MockEngine(handler), settings, clock)

    private fun MockRequestHandleScope.json(body: String): HttpResponseData =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun info_is_parsed_and_cached_per_relay() =
        runTest {
            var calls = 0
            val relay =
                client {
                    calls += 1
                    json("""{"max_body_bytes":4096,"max_record_bytes":128,"max_records_per_group":7}""")
                }

            assertEquals(RelayInfo.published(4096, 128, 7), relay.info())
            assertEquals(RelayInfo.published(4096, 128, 7), relay.info())
            assertEquals(1, calls, "info should be fetched once per relay, not once per push")
        }

    @Test
    fun info_carries_the_instance_policies() =
        runTest {
            val relay =
                client {
                    json(
                        """
                        {"max_body_bytes":4096,"max_record_bytes":128,"max_records_per_group":7,

                         "empty_group_ttl_secs":172800,"inactive_group_ttl_secs":15552000,
                         "requires_instance_secret":true}
                        """.trimIndent(),
                    )
                }

            val info = relay.info()

            assertEquals(2.days, info.emptyGroupTtl)
            assertEquals(180.days, info.inactiveGroupTtl)
            assertTrue(info.requiresInstanceSecret)
        }

    /** A relay older than a field must read as no policy at all, never as a deadline. */
    @Test
    fun info_from_a_relay_without_the_policies_reports_none() =
        runTest {
            val relay = client { json("""{"max_body_bytes":4096,"max_record_bytes":128,"max_records_per_group":7}""") }

            val info = relay.info()

            assertEquals(Duration.ZERO, info.emptyGroupTtl)
            assertEquals(Duration.ZERO, info.inactiveGroupTtl)
            assertFalse(info.requiresInstanceSecret)
        }

    @Test
    fun info_falls_back_conservatively_when_the_relay_has_no_endpoint() =
        runTest {
            val relay = client { respond("", HttpStatusCode.NotFound) }
            assertEquals(RelayInfo.CONSERVATIVE, relay.info())
            assertFalse(relay.info().fromRelay, "a guess must never pass as published")
        }

    @Test
    fun info_is_re_read_once_the_endpoint_appears() =
        runTest {
            var missing = true
            val relay =
                client {
                    if (missing) {
                        respond("", HttpStatusCode.NotFound)
                    } else {
                        json("""{"max_body_bytes":1048576,"max_record_bytes":8192,"max_records_per_group":5000}""")
                    }
                }

            assertEquals(RelayInfo.CONSERVATIVE, relay.info())
            missing = false
            clock += ASSUMED_INFO_TTL_TEST
            assertEquals(RelayInfo.published(1048576, 8192, 5000), relay.info())
        }

    @Test
    fun a_hung_info_probe_gives_up_on_its_own_bound() =
        runTest {
            // Needs its own engine. A suspending handler in the shared one lets the virtual clock jump
            // to whichever deadline is nearest, which is the behaviour under test here.
            val relay =
                RelayClient(
                    MockEngine {
                        delay(HUNG_PROBE_DELAY)
                        json("""{"max_body_bytes":4096,"max_record_bytes":128,"max_records_per_group":7}""")
                    },
                    settings,
                    clock,
                )

            // The handler answers inside the client-wide ceiling, so falling back can only mean
            // the probe applied its own, tighter bound.
            assertEquals(RelayInfo.CONSERVATIVE, relay.info())
        }

    @Test
    fun a_cancelled_probe_does_not_open_the_retry_window() =
        runTest {
            var cancelled = true
            val relay =
                client {
                    if (cancelled) throw CancellationException("navigated away")
                    json("""{"max_body_bytes":4096,"max_record_bytes":128,"max_records_per_group":7}""")
                }

            assertFailsWith<CancellationException> { relay.info() }
            cancelled = false

            // No clock movement. The window was never opened, so the next call really asks.
            assertEquals(RelayInfo.published(4096, 128, 7), relay.info())
        }

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
    fun every_request_reports_the_client_version() =
        runTest {
            var reported: String? = null
            val relay =
                client { request ->
                    reported = request.headers[VERSION_HEADER]
                    json("""{"group_id":"g","token":"t"}""")
                }
            relay.createGroup("look-1")
            assertEquals(BuildInfo.VERSION, reported)
        }

    @Test
    fun upgrade_required_reports_the_minimum_version() =
        runTest {
            val relay =
                client {
                    respond(
                        """{"error":"client older than the minimum this relay accepts (0.11.0)","min_version":"0.11.0"}""",
                        HttpStatusCode.UpgradeRequired,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val error = assertFailsWith<SyncError.ClientTooOld> { relay.createGroup("look-1") }
            assertFalse(error.retriable)
            assertEquals("0.11.0", error.minVersion)
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
    fun service_unavailable_maps_to_server_unavailable() =
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
    fun create_group_maps_507_to_relay_full() =
        runTest {
            val relay = client { respond("""{"error":"server has no room for more groups"}""", HttpStatusCode.InsufficientStorage) }
            val error = assertFailsWith<SyncError.RelayFull> { relay.createGroup("look-1") }
            // The reaper frees empty and inactive groups, so the instance makes room on its own.
            assertTrue(error.retriable)
        }

    @Test
    fun push_maps_413_without_ids_to_batch_too_large() =
        runTest {
            val relay = client { respond("", HttpStatusCode.PayloadTooLarge) }
            val record = EncryptedRecord("m1", updatedAt = 1, deviceId = "dev", deleted = false, ciphertext = byteArrayOf(1))
            val error = assertFailsWith<SyncError.BatchTooLarge> { relay.push("rid", "tok", listOf(record)) }
            // The engine resizes and retries within the push, so nothing outside it should.
            assertFalse(error.retriable)
        }

    @Test
    fun push_maps_413_to_record_too_large_with_ids() =
        runTest {
            val relay =
                client {
                    respond(
                        """{"error":"record payload exceeds the size limit","records":["big"]}""",
                        HttpStatusCode.PayloadTooLarge,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val record = EncryptedRecord("big", updatedAt = 1, deviceId = "dev", deleted = false, ciphertext = byteArrayOf(1))
            val error = assertFailsWith<SyncError.RecordTooLarge> { relay.push("rid", "tok", listOf(record)) }
            assertEquals(listOf("big"), error.recordIds)
            assertFalse(error.retriable)
        }

    @Test
    fun push_maps_507_to_group_full() =
        runTest {
            val relay = client { respond("""{"error":"group has reached its record limit"}""", HttpStatusCode.InsufficientStorage) }
            val record = EncryptedRecord("m1", updatedAt = 1, deviceId = "dev", deleted = false, ciphertext = byteArrayOf(1))
            val error = assertFailsWith<SyncError.GroupFull> { relay.push("rid", "tok", listOf(record)) }
            assertFalse(error.retriable)
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

    @Test
    fun info_falls_back_only_for_its_window_when_the_relay_errors() =
        runTest {
            var fail = true
            val relay =
                client {
                    if (fail) {
                        respond("""{"error":"boom"}""", HttpStatusCode.InternalServerError)
                    } else {
                        json("""{"max_body_bytes":1048576,"max_record_bytes":8192,"max_records_per_group":5000}""")
                    }
                }

            assertEquals(RelayInfo.CONSERVATIVE, relay.info())
            fail = false
            clock += ASSUMED_INFO_TTL_TEST
            assertEquals(RelayInfo.published(1048576, 8192, 5000), relay.info(), "the fallback must not outlive its window")
        }

    @Test
    fun a_batch_level_413_leaves_the_published_info_alone() =
        runTest {
            val relay =
                client { request ->
                    if (request.url.encodedPath.endsWith("/v1/info")) {
                        json("""{"max_body_bytes":1048576,"max_record_bytes":0,"max_records_per_group":0}""")
                    } else {
                        respond("", HttpStatusCode.PayloadTooLarge)
                    }
                }
            val record = EncryptedRecord("m1", updatedAt = 1, deviceId = "dev", deleted = false, ciphertext = byteArrayOf(1))

            assertEquals(1048576, relay.info().maxBodyBytes)
            assertFailsWith<SyncError.BatchTooLarge> { relay.push("rid", "tok", listOf(record)) }

            // The working budget is the engine's. A refusal must not restate it as something the
            // relay published, which is what would license refusing records outright.
            assertEquals(1048576, relay.info().maxBodyBytes)
            assertTrue(relay.info().fromRelay)
        }
}
