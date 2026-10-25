package nz.eloque.quits.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.have_invite_body
import nz.eloque.quits.resources.have_invite_title
import nz.eloque.quits.resources.start_group_body
import nz.eloque.quits.resources.start_group_title
import org.jetbrains.compose.resources.stringResource

/** The two equal ways into a group: start a new one or join with an invite. */
@Composable
fun GroupStartChoices(
    onStartGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceCard(
            icon = Icons.Default.Add,
            title = stringResource(Res.string.start_group_title),
            body = stringResource(Res.string.start_group_body),
            onClick = onStartGroup,
        )
        ChoiceCard(
            icon = Icons.Default.Key,
            title = stringResource(Res.string.have_invite_title),
            body = stringResource(Res.string.have_invite_body),
            onClick = onJoinGroup,
        )
    }
}

@Composable
fun ChoiceCard(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
