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
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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

    override suspend fun createGroup(): GroupHandle {
        val response: CreateGroupResponse =
            client
                .post("$baseUrl/v1/groups") {
                    settings.instanceSecret?.let { header("X-Quits-Instance", it) }
                }.body()
        return GroupHandle(response.groupId, response.code, response.token)
    }

    override suspend fun joinGroup(code: String): GroupHandle? {
        val response: HttpResponse =
            client.post("$baseUrl/v1/groups/join") {
                contentType(ContentType.Application.Json)
                setBody(JoinGroupRequest(code))
            }
        if (response.status == HttpStatusCode.NotFound) return null
        val body: JoinGroupResponse = response.body()
        return GroupHandle(body.groupId, code, body.token)
    }

    override suspend fun push(
        remoteId: String,
        token: String,
        records: List<SyncRecord>,
    ): PushResult {
        val response: PushResponseDto =
            client
                .post("$baseUrl/v1/groups/$remoteId/changes") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(PushRequestDto(records.map { it.toWire() }))
                }.body()
        return PushResult(response.seq, response.applied, response.rejected)
    }

    override suspend fun pull(
        remoteId: String,
        token: String,
        since: Long,
    ): PullResult {
        val response: PullResponseDto =
            client
                .get("$baseUrl/v1/groups/$remoteId/changes") {
                    bearerAuth(token)
                    parameter("since", since)
                }.body()
        return PullResult(response.records.map { it.toRecord() }, response.seq)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun SyncRecord.toWire(): WireRecordIn =
        WireRecordIn(
            id = id,
            updatedAt = updatedAt,
            deleted = deleted,
            deviceId = deviceId,
            payload = Base64.encode(SyncJson.encode(payload).encodeToByteArray()),
        )

    @OptIn(ExperimentalEncodingApi::class)
    private fun WireRecordOut.toRecord(): SyncRecord =
        SyncRecord(
            id = id,
            updatedAt = updatedAt,
            deviceId = deviceId,
            deleted = deleted,
            payload = SyncJson.decode(Base64.decode(payload).decodeToString()),
        )

    @Serializable
    private data class CreateGroupResponse(
        @SerialName("group_id") val groupId: String,
        val code: String,
        val token: String,
    )

    @Serializable
    private data class JoinGroupRequest(
        val code: String,
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
