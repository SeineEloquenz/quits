package nz.eloque.quits.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.components.Section
import nz.eloque.compose_kit.input.SubmittableTextField
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.cd_back
import nz.eloque.quits.resources.settings_instance_secret
import nz.eloque.quits.resources.settings_instance_secret_hint
import nz.eloque.quits.resources.settings_relay_hint
import nz.eloque.quits.resources.settings_relay_url
import nz.eloque.quits.resources.settings_sync
import nz.eloque.quits.resources.settings_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsState()

    AppScaffold(
        title = stringResource(Res.string.settings_title),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
            }
        },
        contentHorizontalPadding = 0.dp,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Section(heading = stringResource(Res.string.settings_sync)) {
                Column(Modifier.padding(8.dp)) {
                    SubmittableTextField(
                        label = stringResource(Res.string.settings_relay_url),
                        imageVector = Icons.Default.Check,
                        initialValue = state.relayUrl,
                        clearOnSubmit = false,
                        onSubmit = viewModel::applyRelayUrl,
                    )
                    Spacer(Modifier.height(16.dp))
                    SubmittableTextField(
                        label = stringResource(Res.string.settings_instance_secret),
                        imageVector = Icons.Default.Check,
                        initialValue = state.instanceSecret,
                        clearOnSubmit = false,
                        onSubmit = viewModel::applyInstanceSecret,
                    )
                }
            }
        }
    }
}
