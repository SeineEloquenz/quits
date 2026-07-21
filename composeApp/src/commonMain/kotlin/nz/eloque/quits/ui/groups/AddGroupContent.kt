package nz.eloque.quits.ui.groups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.add_group_create_link
import nz.eloque.quits.resources.add_group_join_hint
import nz.eloque.quits.resources.add_group_new_here
import nz.eloque.quits.resources.add_group_or
import nz.eloque.quits.resources.groups_new_group
import org.jetbrains.compose.resources.stringResource

/**
 * Join-first: the share-code field is the only thing above the fold. Creating a group is a
 * single low-emphasis link below a divider that expands [CreateGroupForm] inline — it stays
 * reachable in one tap without competing with the primary path.
 */
@Composable
fun AddGroupContent(
    onCreate: (name: String, currencyCode: String) -> Unit,
    onJoin: (code: String) -> Unit,
    error: String?,
    onJoinInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var creating by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        JoinGroupForm(onJoin = onJoin, error = error, onInput = onJoinInput, modifier = Modifier.fillMaxWidth())
        Text(
            stringResource(Res.string.add_group_join_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f))
            Text(
                stringResource(Res.string.add_group_or),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            HorizontalDivider(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.add_group_new_here) + " ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { creating = !creating }) {
                Text(stringResource(Res.string.add_group_create_link))
            }
        }

        AnimatedVisibility(visible = creating) {
            Column {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Text(
                    stringResource(Res.string.groups_new_group),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                CreateGroupForm(onCreate = onCreate, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
