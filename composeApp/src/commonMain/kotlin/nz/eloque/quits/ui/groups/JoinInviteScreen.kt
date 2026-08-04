package nz.eloque.quits.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_cancel
import nz.eloque.quits.resources.action_join
import nz.eloque.quits.resources.cd_back
import nz.eloque.quits.resources.invite_join_body
import nz.eloque.quits.resources.invite_join_title
import nz.eloque.quits.resources.label_share_code
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Confirmation for an invite link
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinInviteScreen(
    code: String,
    onCancel: () -> Unit,
    onJoined: () -> Unit,
) {
    val viewModel = koinViewModel<GroupsViewModel>()
    val error by viewModel.error.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                GroupsEvent.GroupReady -> onJoined()
            }
        }
    }

    AppScaffold(
        title = { Text(stringResource(Res.string.invite_join_title), style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(Res.string.invite_join_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.label_share_code), style = MaterialTheme.typography.labelMedium)
            Text(code, style = MaterialTheme.typography.titleMedium)
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.action_cancel))
                }
                Button(onClick = { viewModel.join(code) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.action_join))
                }
            }
        }
    }
}
