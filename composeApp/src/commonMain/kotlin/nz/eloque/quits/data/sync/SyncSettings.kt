package nz.eloque.quits.data.sync

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sync configuration. Read fresh on each request so edits take effect
 * immediately without rebuilding the [RelayClient].
 */
interface SyncSettings {
    var relayUrl: String
    var instanceSecret: String?

    /** id of the group the home screen last showed, so launch reopens it. */
    var activeGroupId: String?

    val activeGroupIdFlow: StateFlow<String?>

    companion object {
        const val DEFAULT_RELAY_URL = "https://quits.eloque.nz"
    }
}

class InMemorySyncSettings(
    override var relayUrl: String = SyncSettings.DEFAULT_RELAY_URL,
    override var instanceSecret: String? = null,
    activeGroupId: String? = null,
) : SyncSettings {
    private val _activeGroupId = MutableStateFlow(activeGroupId?.ifBlank { null })
    override val activeGroupIdFlow: StateFlow<String?> = _activeGroupId.asStateFlow()

    override var activeGroupId: String?
        get() = _activeGroupId.value
        set(value) {
            _activeGroupId.value = value?.ifBlank { null }
        }
}

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

    private val _activeGroupId = MutableStateFlow(settings.getStringOrNull(KEY_ACTIVE_GROUP)?.ifBlank { null })
    override val activeGroupIdFlow: StateFlow<String?> = _activeGroupId.asStateFlow()

    override var activeGroupId: String?
        get() = _activeGroupId.value
        set(value) {
            val id = value?.ifBlank { null }
            if (id == null) settings.remove(KEY_ACTIVE_GROUP) else settings.putString(KEY_ACTIVE_GROUP, id)
            _activeGroupId.value = id
        }

    private companion object {
        const val KEY_RELAY_URL = "relay_url"
        const val KEY_INSTANCE_SECRET = "instance_secret"
        const val KEY_ACTIVE_GROUP = "active_group_id"
    }
}
