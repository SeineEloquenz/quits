package nz.eloque.quits.data.sync

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Hard ceiling on one relay call, wide enough for a push carrying the relay's whole body limit. */
private val REQUEST_TIMEOUT = 120.seconds

/** Per-request bound on the limits probe, which [RelayClient.limits] holds its lock across. */
internal val LIMITS_TIMEOUT = 10.seconds

/** How long an unreachable relay is assumed unreachable before asking again. */
internal val ASSUMED_LIMITS_TTL = 30.seconds

/** How long a relay's published limits are trusted, so an operator changing them is picked up. */
internal val PUBLISHED_LIMITS_TTL = 30.minutes

/** Talks to the relay over HTTP. Payloads are JSON, base64-encoded on the wire. */
class RelayClient(
    engine: HttpClientEngine,
    private val settings: SyncSettings,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : Relay {
    private val client =
        HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT.inWholeMilliseconds }
        }

    private val baseUrl: String get() = settings.relayUrl.trimEnd('/')

    /** What is known about one relay, discarded wholesale when [baseUrl] changes. */
    private class RelayState(
        val url: String,
    ) {
        var fetched: RelayLimits? = null
        var fetchedAt: TimeMark? = null
        var askedAt: TimeMark? = null
    }

    private var relayState: RelayState? = null

    // Background sync and the screen that shows headroom both ask, on different threads. Every
    // read-modify-write of relayState happens under this.
    private val limitsLock = Mutex()

    private fun currentState(): RelayState = relayState?.takeIf { it.url == baseUrl } ?: RelayState(baseUrl).also { relayState = it }

    override suspend fun limits(): RelayLimits = limitsLock.withLock { readLimits() }

    private suspend fun readLimits(): RelayLimits {
        val state = currentState()
        state.fetched?.let { cached ->
            if (state.fetchedAt?.elapsedNow()?.let { it < PUBLISHED_LIMITS_TTL } == true) return cached
        }

        state.askedAt?.let { asked ->
            if (asked.elapsedNow() < ASSUMED_LIMITS_TTL) return state.fetched ?: RelayLimits.CONSERVATIVE
        }

        val limits =
            try {
                val response: HttpResponse =
                    client.get("${state.url}/v1/limits") {
                        timeout { requestTimeoutMillis = LIMITS_TIMEOUT.inWholeMilliseconds }
                    }
                // A relay that predates the endpoint 404s it, which is not a missing group.
                if (response.status == HttpStatusCode.NotFound) return state.fallbackLimits()
                val body: LimitsResponseDto = response.decode(RelayOperation.Limits)
                RelayLimits.published(body.maxBodyBytes, body.maxRecordBytes, body.maxRecordsPerGroup)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never rethrow. Limits are a sizing hint, and failing here would fail a push that would otherwise have worked.
                Logger.w(e) { "could not read relay limits from ${state.url}" }
                return state.fallbackLimits()
            }
        state.fetched = limits
        state.fetchedAt = timeSource.markNow()
        return limits
    }

    /**
     * The last published limits, or a guess, and opens the window before the relay is asked again.
     */
    private fun RelayState.fallbackLimits(): RelayLimits {
        askedAt = timeSource.markNow()
        return fetched ?: RelayLimits.CONSERVATIVE
    }

    override suspend fun createGroup(lookupId: String): GroupHandle =
        relayCall {
            val response =
                client.post("$baseUrl/v1/groups") {
                    contentType(ContentType.Application.Json)
                    settings.instanceSecret?.let { header("X-Quits-Instance", it) }
                    setBody(GroupLookupRequest(lookupId))
                }
            val body: CreateGroupResponse = response.decode(RelayOperation.CreateGroup)
            GroupHandle(body.groupId, body.token)
        }

    override suspend fun joinGroup(lookupId: String): GroupHandle? =
        relayCall {
            val response: HttpResponse =
                client.post("$baseUrl/v1/groups/join") {
                    contentType(ContentType.Application.Json)
                    setBody(GroupLookupRequest(lookupId))
                }
            // A missing invite code is expected here, not an error.
            if (response.status == HttpStatusCode.NotFound) return@relayCall null
            val body: JoinGroupResponse = response.decode(RelayOperation.JoinGroup)
            GroupHandle(body.groupId, body.token)
        }

    override suspend fun push(
        remoteId: String,
        token: String,
        records: List<EncryptedRecord>,
    ): PushResult =
        relayCall {
            val response =
                client.post("$baseUrl/v1/groups/$remoteId/changes") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(PushRequestDto(records.map { it.toWire() }))
                }
            val body: PushResponseDto = response.decode(RelayOperation.Push)
            PushResult(body.seq, body.applied, body.rejected)
        }

    override suspend fun pull(
        remoteId: String,
        token: String,
        since: Long,
    ): PullResult =
        relayCall {
            val response =
                client.get("$baseUrl/v1/groups/$remoteId/changes") {
                    bearerAuth(token)
                    parameter("since", since)
                }
            val body: PullResponseDto = response.decode(RelayOperation.Pull)
            PullResult(body.records.map { it.toRecord() }, body.seq)
        }

    /** Runs [block] and re-expresses every failure as a [SyncError]. */
    private inline fun <T> relayCall(block: () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SyncError) {
            throw e
        } catch (e: Exception) {
            throw SyncError.Unreachable(e)
        }

    /** Deserializes a 2xx body as [T]; a non-2xx status becomes the matching [SyncError]. */
    private suspend inline fun <reified T> HttpResponse.decode(operation: RelayOperation): T {
        if (status.isSuccess()) {
            return try {
                body()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw SyncError.Protocol(e)
            }
        }
        val error = runCatching { body<RelayErrorResponse>() }.getOrNull()
        val message =
            error?.error
                ?: runCatching { bodyAsText() }.getOrNull()?.take(200)
        throw syncErrorForStatus(
            status.value,
            retryAfterHint(),
            message?.takeIf { it.isNotBlank() },
            error?.records.orEmpty(),
            operation,
        )
    }

    /** Parses the `Retry-After` header (delta-seconds form, as the relay emits) into a [Duration]. */
    private fun HttpResponse.retryAfterHint(): Duration? =
        headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.seconds

    @OptIn(ExperimentalEncodingApi::class)
    private fun EncryptedRecord.toWire(): WireRecordIn =
        WireRecordIn(
            id = id,
            updatedAt = updatedAt,
            deleted = deleted,
            deviceId = deviceId,
            payload = Base64.encode(ciphertext),
        )

    @OptIn(ExperimentalEncodingApi::class)
    private fun WireRecordOut.toRecord(): EncryptedRecord =
        EncryptedRecord(
            id = id,
            updatedAt = updatedAt,
            deviceId = deviceId,
            deleted = deleted,
            ciphertext = Base64.decode(payload),
        )

    @Serializable
    private data class RelayErrorResponse(
        val error: String? = null,
        val records: List<String> = emptyList(),
    )

    @Serializable
    private data class LimitsResponseDto(
        @SerialName("max_body_bytes") val maxBodyBytes: Long,
        @SerialName("max_record_bytes") val maxRecordBytes: Long,
        @SerialName("max_records_per_group") val maxRecordsPerGroup: Long,
    )

    @Serializable
    private data class CreateGroupResponse(
        @SerialName("group_id") val groupId: String,
        val token: String,
    )

    @Serializable
    private data class GroupLookupRequest(
        @SerialName("lookup_id") val lookupId: String,
    )

    @Serializable
    private data class JoinGroupResponse(
        @SerialName("group_id") val groupId: String,
        val token: String,
    )

    @Serializable
    private data class WireRecordIn(
        val id: String,
        @SerialName("updated_at") val updatedAt: Long,
        val deleted: Boolean,
        @SerialName("device_id") val deviceId: String,
        val payload: String,
    )

    @Serializable
    private data class WireRecordOut(
        val id: String,
        @SerialName("updated_at") val updatedAt: Long,
        val deleted: Boolean,
        @SerialName("device_id") val deviceId: String,
        val payload: String,
        @SerialName("server_seq") val serverSeq: Long,
    )

    @Serializable
    private data class PushRequestDto(
        val records: List<WireRecordIn>,
    )

    @Serializable
    private data class PushResponseDto(
        val seq: Long,
        val applied: List<String>,
        val rejected: List<String>,
    )

    @Serializable
    private data class PullResponseDto(
        val records: List<WireRecordOut>,
        val seq: Long,
    )
}
