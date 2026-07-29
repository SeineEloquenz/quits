package nz.eloque.quits.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.cd_back
import nz.eloque.quits.resources.stats_by_category
import nz.eloque.quits.resources.stats_by_member
import nz.eloque.quits.resources.stats_empty
import nz.eloque.quits.resources.stats_title
import nz.eloque.quits.resources.stats_total
import nz.eloque.quits.resources.stats_uncategorized
import nz.eloque.quits.ui.components.EmptyHint
import nz.eloque.quits.ui.components.LoadingBox
import nz.eloque.quits.ui.components.MemberAvatar
import nz.eloque.quits.ui.components.MoneyText
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    groupId: GroupId,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<StatsViewModel>(key = groupId.value) { parametersOf(groupId) }
    val state by viewModel.state.collectAsState()

    AppScaffold(
        title = {
            Text(stringResource(Res.string.stats_title), style = MaterialTheme.typography.headlineMedium, maxLines = 1)
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
            }
        },
    ) { scrollBehavior ->
        if (!state.loaded) {
            LoadingBox(Modifier.padding(top = 32.dp))
            return@AppScaffold
        }
        if (!state.found || !state.hasExpenses) {
            EmptyHint(stringResource(Res.string.stats_empty), Modifier.padding(top = 32.dp))
            return@AppScaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(Res.string.stats_total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                MoneyText(state.total, style = MaterialTheme.typography.headlineLarge)
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader(stringResource(Res.string.stats_by_category))
            state.byCategory.forEach { StatBarRow(it) }

            Spacer(Modifier.height(16.dp))
            SectionHeader(stringResource(Res.string.stats_by_member))
            state.byMember.forEach { StatBarRow(it) }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

/**
 * One breakdown row: label + value, then a single-hue magnitude bar (length ∝ the section's largest).
 * Colour is not used to distinguish rows — the label carries identity, the bar length the magnitude.
 */
@Composable
private fun StatBarRow(bar: StatBar) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            bar.memberId?.let { id ->
                MemberAvatar(name = bar.label ?: "?", id = id, size = 28.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                bar.label ?: stringResource(Res.string.stats_uncategorized),
                Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MoneyText(bar.amount)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(bar.fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
