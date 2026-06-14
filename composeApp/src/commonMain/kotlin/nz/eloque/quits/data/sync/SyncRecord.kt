package nz.eloque.quits.data.sync

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

/** JSON for sync payloads — `type` discriminator, defaults encoded so nullable fields round-trip. */
object SyncJson {
    val json =
        Json {
            classDiscriminator = "type"
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    fun encode(payload: SyncPayload): String = json.encodeToString(payload)

    fun decode(text: String): SyncPayload = json.decodeFromString(text)
}
