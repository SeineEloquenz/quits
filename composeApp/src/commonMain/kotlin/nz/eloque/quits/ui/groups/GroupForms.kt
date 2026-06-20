package nz.eloque.quits.ui.groups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_create
import nz.eloque.quits.resources.action_join
import nz.eloque.quits.resources.groups_base_currency
import nz.eloque.quits.resources.label_name
import nz.eloque.quits.resources.label_share_code
import nz.eloque.quits.ui.components.CurrencyPicker
import org.jetbrains.compose.resources.stringResource

/** Name + base-currency + Create. Resets the name field after submitting. */
@Composable
fun CreateGroupForm(
    onCreate: (name: String, currencyCode: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(Currency.of("USD")) }

    Column(modifier.padding(16.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(Res.string.label_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        CurrencyPicker(
            label = stringResource(Res.string.groups_base_currency),
            selected = currency,
            onSelected = { currency = it },
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onCreate(name, currency.code)
                name = ""
            },
            enabled = name.isNotBlank(),
        ) {
            Text(stringResource(Res.string.action_create))
        }
    }
}

/** Share-code field + Join, with an inline error message. */
@Composable
fun JoinGroupForm(
    onJoin: (code: String) -> Unit,
    error: String?,
    onInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var joinCode by remember { mutableStateOf("") }

    Column(modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = joinCode,
                onValueChange = {
                    joinCode = it.uppercase()
                    onInput()
                },
                label = { Text(stringResource(Res.string.label_share_code)) },
                singleLine = true,
                isError = error != null,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { onJoin(joinCode) },
                enabled = joinCode.isNotBlank(),
            ) {
                Text(stringResource(Res.string.action_join))
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
