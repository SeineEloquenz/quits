package nz.eloque.quits.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A client-side sync record: the relay envelope ([id], [updatedAt], [deviceId], [deleted]) plus the
 * decoded [payload].
 */
data class SyncRecord(
    val id: String,
    val updatedAt: Long,
    val deviceId: String,
    val deleted: Boolean,
    val payload: SyncPayload,
)

/**
 * The schema version stamped on every encoded payload. Bump this whenever a payload's *meaning*
 * changes in a way [ignoreUnknownKeys] additive back-compat can't cover (a reinterpreted or removed
 * field). It travels inside the encrypted blob, so a future client can read [VersionedPayload.v] and
 * decide how to handle a record written by a newer version — the same read-only downgrade the
 * unsupported-split-type rule already uses — instead of silently mis-reading it.
 */
const val CURRENT_PAYLOAD_VERSION = 3

/** A [SyncPayload] together with the schema [v]ersion it was written with (see [CURRENT_PAYLOAD_VERSION]). */
@Serializable
data class VersionedPayload(
    val v: Int = 1,
    val payload: SyncPayload,
)

/** JSON for sync payloads — `type` discriminator, defaults encoded so nullable fields round-trip. */
object SyncJson {
    val json =
        Json {
            classDiscriminator = "type"
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    /** Encodes [payload] wrapped in a [VersionedPayload] stamped with [version]. */
    fun encode(
        payload: SyncPayload,
        version: Int = CURRENT_PAYLOAD_VERSION,
    ): String = json.encodeToString(VersionedPayload(version, payload))

    /** Decodes just the payload; use [decodeVersioned] when the schema version matters. */
    fun decode(text: String): SyncPayload = decodeVersioned(text).payload

    fun decodeVersioned(text: String): VersionedPayload = json.decodeFromString(text)
}
