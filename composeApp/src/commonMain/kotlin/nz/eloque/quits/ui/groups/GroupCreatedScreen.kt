package nz.eloque.quits.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.group_created_later
import nz.eloque.quits.resources.group_created_question
import nz.eloque.quits.resources.group_created_share_body
import nz.eloque.quits.resources.group_created_share_title
import nz.eloque.quits.resources.group_created_solo_body
import nz.eloque.quits.resources.group_created_solo_title
import nz.eloque.quits.resources.group_created_title
import org.jetbrains.compose.resources.stringResource

/** Asks whether a freshly created, local-only group should be shared. */
@Composable
fun GroupCreatedScreen(
    name: String,
    onSolo: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(Res.string.group_created_title, name),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(Res.string.group_created_question),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceCard(
                icon = Icons.Default.Smartphone,
                title = stringResource(Res.string.group_created_solo_title),
                body = stringResource(Res.string.group_created_solo_body),
                onClick = onSolo,
            )
            ChoiceCard(
                icon = Icons.Default.Groups,
                title = stringResource(Res.string.group_created_share_title),
                body = stringResource(Res.string.group_created_share_body),
                onClick = onShare,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(Res.string.group_created_later),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}
