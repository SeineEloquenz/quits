package nz.eloque.quits.ui.settings

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.input.AbbreviatingText
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.data.sync.RelayInfo
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.cd_back
import nz.eloque.quits.resources.relay_info_access
import nz.eloque.quits.resources.relay_info_day
import nz.eloque.quits.resources.relay_info_days
import nz.eloque.quits.resources.relay_info_empty_ttl
import nz.eloque.quits.resources.relay_info_hour
import nz.eloque.quits.resources.relay_info_hours
import nz.eloque.quits.resources.relay_info_inactive_ttl
import nz.eloque.quits.resources.relay_info_limits
import nz.eloque.quits.resources.relay_info_max_body
import nz.eloque.quits.resources.relay_info_max_record
import nz.eloque.quits.resources.relay_info_never
import nz.eloque.quits.resources.relay_info_no_limit
import nz.eloque.quits.resources.relay_info_records_per_group
import nz.eloque.quits.resources.relay_info_requires_secret
import nz.eloque.quits.resources.relay_info_retention
import nz.eloque.quits.resources.relay_info_retention_desc
import nz.eloque.quits.resources.relay_info_retry
import nz.eloque.quits.resources.relay_info_silent
import nz.eloque.quits.resources.relay_info_title
import nz.eloque.quits.resources.value_no
import nz.eloque.quits.resources.value_yes
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayInfoScreen(onBack: () -> Unit) {
    val viewModel = koinViewModel<RelayInfoViewModel>()
    val state by viewModel.state.collectAsState()

    AppScaffold(
        title = {
            AbbreviatingText(
                stringResource(Res.string.relay_info_title),
                style = MaterialTheme.typography.titleLarge,
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
            Text(
                text = state.relayUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(16.dp))

            val info = state.info
            when {
                state.loading && info == null -> CircularProgressIndicator(Modifier.padding(32.dp))
                info == null || !info.fromRelay -> Unreachable(onRetry = viewModel::refresh)
                else -> Reported(info)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Unreachable(onRetry: () -> Unit) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(Res.string.relay_info_silent),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.relay_info_retry)) }
        }
    }
}

@Composable
private fun Reported(info: RelayInfo) {
    Section(stringResource(Res.string.relay_info_limits)) {
        Field(stringResource(Res.string.relay_info_max_body), formatBytes(info.maxBodyBytes))
        Field(stringResource(Res.string.relay_info_max_record), byteLimit(info.maxRecordBytes))
        Field(stringResource(Res.string.relay_info_records_per_group), countLimit(info.maxRecordsPerGroup))
    }
    Spacer(Modifier.height(16.dp))
    Section(stringResource(Res.string.relay_info_retention)) {
        Text(
            text = stringResource(Res.string.relay_info_retention_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Field(stringResource(Res.string.relay_info_empty_ttl), retention(info.emptyGroupTtl))
        Field(stringResource(Res.string.relay_info_inactive_ttl), retention(info.inactiveGroupTtl))
    }
    Spacer(Modifier.height(16.dp))
    Section(stringResource(Res.string.relay_info_access)) {
        Field(
            stringResource(Res.string.relay_info_requires_secret),
            stringResource(if (info.requiresInstanceSecret) Res.string.value_yes else Res.string.value_no),
        )
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun byteLimit(bytes: Long): String = if (bytes <= 0) stringResource(Res.string.relay_info_no_limit) else formatBytes(bytes)

@Composable
private fun countLimit(count: Long): String = if (count <= 0) stringResource(Res.string.relay_info_no_limit) else count.toString()

@Composable
private fun retention(ttl: Duration): String =
    when {
        ttl <= Duration.ZERO -> stringResource(Res.string.relay_info_never)
        ttl.inWholeDays >= 1 ->
            stringResource(
                if (ttl.inWholeDays == 1L) Res.string.relay_info_day else Res.string.relay_info_days,
                ttl.inWholeDays.toInt(),
            )
        else ->
            stringResource(
                if (ttl.inWholeHours == 1L) Res.string.relay_info_hour else Res.string.relay_info_hours,
                ttl.inWholeHours.toInt(),
            )
    }

private const val KIB = 1024L
private const val MIB = KIB * KIB

private fun formatBytes(bytes: Long): String =
    when {
        bytes >= MIB -> "${scaled(bytes, MIB)} MiB"
        bytes >= KIB -> "${scaled(bytes, KIB)} KiB"
        else -> "$bytes B"
    }

/** One decimal, and only when there is one, so a limit is never rounded into a different number. */
private fun scaled(
    bytes: Long,
    unit: Long,
): String {
    val whole = bytes / unit
    val tenths = (bytes % unit) * 10 / unit
    return if (tenths == 0L) "$whole" else "$whole.$tenths"
}
