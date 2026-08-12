package nz.eloque.quits.ui.groups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_create
import nz.eloque.quits.resources.action_join
import nz.eloque.quits.resources.groups_base_currency
import nz.eloque.quits.resources.label_name
import nz.eloque.quits.resources.label_share_code
import nz.eloque.quits.ui.components.CurrencyPickerRow
import nz.eloque.quits.ui.components.ListFieldCard
import nz.eloque.quits.ui.components.ListRowDivider
import nz.eloque.quits.ui.components.ListTextRow
import org.jetbrains.compose.resources.stringResource

/** Name + base-currency + Create. Resets the name field after submitting. */
@Composable
fun CreateGroupForm(
    onCreate: (name: String, currency: Currency) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(Currency.of("USD")) }

    Column(modifier.padding(horizontal = 16.dp)) {
        ListFieldCard {
            ListTextRow(
                icon = Icons.Default.Groups,
                label = stringResource(Res.string.label_name),
                value = name,
                onValueChange = { name = it },
            )
            ListRowDivider()
            CurrencyPickerRow(
                icon = Icons.Default.Payments,
                fieldLabel = stringResource(Res.string.groups_base_currency),
                selected = currency,
                onSelected = { currency = it },
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onCreate(name, currency)
                name = ""
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
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

    Column(modifier.padding(horizontal = 16.dp)) {
        ListFieldCard {
            ListTextRow(
                icon = Icons.Default.Key,
                label = stringResource(Res.string.label_share_code),
                value = joinCode,
                onValueChange = {
                    joinCode = it.uppercase()
                    onInput()
                },
                isError = error != null,
                supporting = error,
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onJoin(joinCode) },
            enabled = joinCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.action_join))
        }
    }
}
