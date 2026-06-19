package nz.eloque.quits.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.components.Section
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_reset
import nz.eloque.quits.resources.action_save
import nz.eloque.quits.resources.cd_back
import nz.eloque.quits.resources.settings_relay_hint
import nz.eloque.quits.resources.settings_relay_url
import nz.eloque.quits.resources.settings_saved
import nz.eloque.quits.resources.settings_sync
import nz.eloque.quits.resources.settings_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
            }
            Text(stringResource(Res.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        }

        Spacer(Modifier.height(8.dp))

        Section(heading = stringResource(Res.string.settings_sync)) {
            Column(Modifier.padding(8.dp)) {
                OutlinedTextField(
                    value = state.relayUrl,
                    onValueChange = viewModel::setRelayUrl,
                    label = { Text(stringResource(Res.string.settings_relay_url)) },
                    singleLine = true,
                    isError = state.relayUrl.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(Res.string.settings_relay_hint, viewModel.defaultRelayUrl),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = viewModel::save, enabled = state.relayUrl.isNotBlank()) {
                        Text(stringResource(Res.string.action_save))
                    }
                    TextButton(onClick = viewModel::resetToDefault) { Text(stringResource(Res.string.action_reset)) }
                    if (state.saved) {
                        Text(stringResource(Res.string.settings_saved), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
