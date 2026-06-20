package nz.eloque.quits.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import nz.eloque.quits.data.sync.SyncSettings

data class SettingsUiState(
    val relayUrl: String = "",
    val instanceSecret: String = "",
)

class SettingsViewModel(
    private val settings: SyncSettings,
) : ViewModel() {
    val defaultRelayUrl: String = SyncSettings.DEFAULT_RELAY_URL

    private val _state =
        MutableStateFlow(
            SettingsUiState(
                relayUrl = settings.relayUrl,
                instanceSecret = settings.instanceSecret.orEmpty(),
            ),
        )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /** Persists immediately; blank input is ignored. */
    fun applyRelayUrl(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        settings.relayUrl = trimmed
        _state.update { it.copy(relayUrl = trimmed) }
    }

    /** Persists immediately; a blank value clears the stored secret. */
    fun applyInstanceSecret(value: String) {
        val trimmed = value.trim()
        settings.instanceSecret = trimmed.ifEmpty { null }
        _state.update { it.copy(instanceSecret = trimmed) }
    }
}
