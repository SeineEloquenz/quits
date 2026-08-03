package nz.eloque.quits.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.input.AbbreviatingText
import nz.eloque.compose_kit.input.SubmittableTextField
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.cd_back
import nz.eloque.quits.resources.settings_advanced
import nz.eloque.quits.resources.settings_advanced_desc
import nz.eloque.quits.resources.settings_instance_secret
import nz.eloque.quits.resources.settings_relay_url
import nz.eloque.quits.resources.settings_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsState()

    AppScaffold(
        title = {
            AbbreviatingText(
                stringResource(Res.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
            }
        },
        contentHorizontalPadding = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            AdvancedSection {
                Text(
                    text = stringResource(Res.string.settings_advanced_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Column(Modifier.padding(16.dp)) {
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
                        hidden = true,
                        onSubmit = viewModel::applyInstanceSecret,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedSection(content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(16.dp),
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(Res.string.settings_advanced),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    content()
                }
            }
        }
    }
}
