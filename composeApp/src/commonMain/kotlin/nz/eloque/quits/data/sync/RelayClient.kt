package nz.eloque.quits.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Talks to the relay over HTTP. Payloads are JSON, base64-encoded on the wire. */
class RelayClient(
    engine: HttpClientEngine,
    private val settings: SyncSettings,
) : Relay {
    private val client =
        HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    private val baseUrl: String get() = settings.relayUrl.trimEnd('/')

    override suspend fun createGroup(lookupId: String): GroupHandle =
        relayCall {
            val response =
                client.post("$baseUrl/v1/groups") {
                    contentType(ContentType.Application.Json)
                    settings.instanceSecret?.let { header("X-Quits-Instance", it) }
                    setBody(GroupLookupRequest(lookupId))
                }
            // 507 here means the instance holds all the groups it will; the same status from a
            // push means one group is full. Only the caller knows which endpoint it hit.
            if (response.status == HttpStatusCode.InsufficientStorage) throw SyncError.RelayFull
            val body: CreateGroupResponse = response.decode()
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
            val body: JoinGroupResponse = response.decode()
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
            val body: PushResponseDto = response.decode()
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
            val body: PullResponseDto = response.decode()
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
    private suspend inline fun <reified T> HttpResponse.decode(): T {
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
