package nz.eloque.quits.data.sync

import com.russhwolf.settings.Settings

/**
 * Sync configuration. Read fresh on each request so edits take effect
 * immediately without rebuilding the [RelayClient].
 */
interface SyncSettings {
    var relayUrl: String
    var instanceSecret: String?

    companion object {
        // Android emulator -> dev host loopback. Override on a physical device.
        const val DEFAULT_RELAY_URL = "http://10.0.2.2:8080"
    }
}

class InMemorySyncSettings(
    override var relayUrl: String = SyncSettings.DEFAULT_RELAY_URL,
    override var instanceSecret: String? = null,
) : SyncSettings

/** Settings persisted via multiplatform-settings (SharedPreferences / NSUserDefaults). */
class PersistentSyncSettings(
    private val settings: Settings,
) : SyncSettings {
    override var relayUrl: String
        get() = settings.getString(KEY_RELAY_URL, SyncSettings.DEFAULT_RELAY_URL)
        set(value) = settings.putString(KEY_RELAY_URL, value)

    override var instanceSecret: String?
        get() = settings.getStringOrNull(KEY_INSTANCE_SECRET)?.ifBlank { null }
        set(value) {
            if (value.isNullOrBlank()) settings.remove(KEY_INSTANCE_SECRET) else settings.putString(KEY_INSTANCE_SECRET, value)
        }

    private companion object {
        const val KEY_RELAY_URL = "relay_url"
        const val KEY_INSTANCE_SECRET = "instance_secret"
    }
}
