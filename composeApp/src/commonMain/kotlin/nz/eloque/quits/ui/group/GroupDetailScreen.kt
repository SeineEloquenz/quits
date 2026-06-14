package nz.eloque.quits.ui.group

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.components.Section
import nz.eloque.compose_kit.input.SubmittableTextField
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.Money
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private fun Money.display(): String = "${toDecimalString()} ${currency.code}"

@Composable
fun GroupDetailScreen(
    groupId: GroupId,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (ExpenseId) -> Unit,
) {
    val viewModel = koinViewModel<GroupDetailViewModel> { parametersOf(groupId) }
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                state.name.ifEmpty { "Group" },
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            if (state.shareCode != null) {
                IconButton(onClick = viewModel::sync) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Section(heading = "Sharing") {
            Column(Modifier.padding(8.dp)) {
                val code = state.shareCode
                if (code == null) {
                    Text("This group is on this device only.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = viewModel::share) { Text("Share group") }
                } else {
                    Text("Share code", style = MaterialTheme.typography.labelMedium)
                    Text(code, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Others join with this code to sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Section(heading = "Balances") {
            Column(Modifier.padding(8.dp)) {
                if (state.members.isEmpty()) {
                    Text("Add members to start splitting.")
                } else {
                    state.members.forEach { member ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(member.name, Modifier.weight(1f))
                            Text(
                                member.net.display(),
                                color =
                                    when {
                                        member.net.isPositive -> MaterialTheme.colorScheme.primary
                                        member.net.isNegative -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        }
                    }
                    if (state.transfers.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Settle up", fontWeight = FontWeight.Bold)
                        state.transfers.forEach { row ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${row.from} → ${row.to}: ${row.transfer.amount.display()}",
                                    Modifier.weight(1f),
                                )
                                TextButton(onClick = { viewModel.record(row.transfer) }) {
                                    Text("Record")
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Section(heading = "Members") {
            Column(Modifier.padding(8.dp)) {
                state.members.forEach { Text(it.name, Modifier.padding(vertical = 2.dp)) }
                Spacer(Modifier.height(8.dp))
                SubmittableTextField(
                    label = "Add member",
                    imageVector = Icons.Default.Add,
                    onSubmit = viewModel::addMember,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Section(heading = "Expenses") {
            Column(Modifier.padding(8.dp)) {
                if (state.expenses.isEmpty()) {
                    Text("No expenses yet.")
                } else {
                    state.expenses.forEach { expense ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onEditExpense(expense.id) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(expense.title)
                                Text(
                                    "paid by ${expense.paidBy}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            Text(expense.total.display())
                            IconButton(onClick = { viewModel.deleteExpense(expense.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete ${expense.title}",
                                    tint = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onAddExpense,
                    enabled = state.members.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.members.isEmpty()) "Add members first" else "Add expense")
                }
            }
        }
    }
}
