package nz.eloque.quits.ui.groups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.add_group_import_link
import nz.eloque.quits.resources.add_group_title
import nz.eloque.quits.resources.cd_back
import nz.eloque.quits.ui.components.rememberTextFilePicker
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The drawer's way to add another group: the start-or-join choice plus importing from a file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGroupScreen(
    onBack: () -> Unit,
    onStartGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onDone: () -> Unit,
) {
    val viewModel = koinViewModel<GroupsViewModel>()
    val importError by viewModel.importError.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GroupsEvent.GroupReady -> onDone()
            }
        }
    }

    AppScaffold(
        title = { Text(stringResource(Res.string.add_group_title), style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            GroupStartChoices(onStartGroup = onStartGroup, onJoinGroup = onJoinGroup)
            Spacer(Modifier.height(16.dp))
            val pickFile = rememberTextFilePicker("application/json", viewModel::importFile)
            TextButton(onClick = pickFile, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(Res.string.add_group_import_link))
            }
            importError?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        }
    }
}
