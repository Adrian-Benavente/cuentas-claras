package com.cuentasclaras.app.presentation.group

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuentasclaras.app.presentation.components.UiState
import com.cuentasclaras.app.util.MoneyFormatter
import com.cuentasclaras.domain.model.Expense
import com.cuentasclaras.domain.model.GroupMember
import com.cuentasclaras.domain.model.MemberBalance
import com.cuentasclaras.domain.model.MemberRole
import com.cuentasclaras.domain.model.SuggestedTransfer
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    groupId: String,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: GroupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Resumen", "Gastos", "Miembros", "Configuración")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text((state as? UiState.Content)?.data?.group?.name ?: "Grupo")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        floatingActionButton = {
            if (tabIndex == 1) {
                FloatingActionButton(
                    onClick = onAddExpense,
                    modifier = Modifier.semantics { contentDescription = "Agregar gasto" },
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        },
    ) { padding ->
        when (val ui = state) {
            UiState.Loading -> CenterLoading(Modifier.padding(padding))
            is UiState.Error -> Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(ui.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::refresh) { Text("Reintentar") }
            }
            UiState.Empty -> Unit
            is UiState.Content -> {
                Column(Modifier.padding(padding).fillMaxSize()) {
                    PrimaryTabRow(selectedTabIndex = tabIndex) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = tabIndex == index,
                                onClick = { tabIndex = index },
                                text = { Text(title) },
                            )
                        }
                    }
                    when (tabIndex) {
                        0 -> SummaryTab(
                            content = ui.data,
                            onPrevious = viewModel::previousPeriod,
                            onNext = viewModel::nextPeriod,
                        )
                        1 -> ExpensesTab(
                            content = ui.data,
                            onOpenExpense = onOpenExpense,
                        )
                        2 -> MembersTab(members = ui.data.members)
                        3 -> SettingsTab(
                            content = ui.data,
                            onRotateCode = viewModel::rotateInviteCode,
                            onLogout = onLogout,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTab(
    content: GroupContent,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val periodLabel = content.period.month
        .getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-AR"))
        .replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-AR")) } +
        " ${content.period.year}"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.semantics { contentDescription = "Mes anterior" },
                ) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null)
                }
                Text(periodLabel.uppercase(), style = MaterialTheme.typography.titleMedium)
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.semantics { contentDescription = "Mes siguiente" },
                ) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                }
            }
        }
        item {
            Text("Total gastado", style = MaterialTheme.typography.labelLarge)
            Text(
                MoneyFormatter.format(content.summary.totalSpent),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        items(content.summary.memberBalances) { balance ->
            MemberBalanceCard(
                balance = balance,
                displayName = content.members.find { it.userId == balance.userId }?.displayName
                    ?: balance.userId.value,
            )
        }
        if (content.summary.suggestedTransfers.isNotEmpty()) {
            item {
                Text("Para saldar", style = MaterialTheme.typography.titleMedium)
            }
            items(content.summary.suggestedTransfers) { transfer ->
                TransferRow(transfer, content.members)
            }
        } else {
            item {
                Text(
                    if (content.summary.totalSpent.isZero()) {
                        "No hay gastos en este período."
                    } else {
                        "Las cuentas están saldadas."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun MemberBalanceCard(balance: MemberBalance, displayName: String) {
    val balanceLabel = when {
        balance.balance.isPositive() -> "Debe recibir ${MoneyFormatter.format(balance.balance.abs())}"
        balance.balance.isNegative() -> "Debe pagar ${MoneyFormatter.format(balance.balance.abs())}"
        else -> "Saldado"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "$displayName. Pagó ${MoneyFormatter.format(balance.amountPaid)}. " +
                        "Le corresponde ${MoneyFormatter.format(balance.amountOwed)}. $balanceLabel"
            },
    ) {
        Text(displayName, style = MaterialTheme.typography.titleMedium)
        Text("Pagó: ${MoneyFormatter.format(balance.amountPaid)}")
        Text("Le corresponde: ${MoneyFormatter.format(balance.amountOwed)}")
        Text(
            text = "Saldo: ${MoneyFormatter.format(balance.balance, withSign = true)}",
            fontWeight = FontWeight.SemiBold,
            color = when {
                balance.balance.isPositive() -> MaterialTheme.colorScheme.primary
                balance.balance.isNegative() -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Text(balanceLabel, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TransferRow(transfer: SuggestedTransfer, members: List<GroupMember>) {
    val from = members.find { it.userId == transfer.fromUserId }?.displayName ?: transfer.fromUserId.value
    val to = members.find { it.userId == transfer.toUserId }?.displayName ?: transfer.toUserId.value
    Text(
        "$from debe ${MoneyFormatter.format(transfer.amount)} a $to",
        modifier = Modifier.semantics {
            contentDescription = "$from debe ${MoneyFormatter.format(transfer.amount)} a $to"
        },
    )
}

@Composable
private fun ExpensesTab(
    content: GroupContent,
    onOpenExpense: (String) -> Unit,
) {
    val periodExpenses = content.expenses.filter { it.period == content.period }
    if (periodExpenses.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No hay gastos en este período.")
            Text("Tocá + para agregar el primero.")
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(periodExpenses, key = { it.id.value }) { expense ->
            ExpenseRow(expense, content.members, onOpenExpense)
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    members: List<GroupMember>,
    onOpenExpense: (String) -> Unit,
) {
    val payer = members.find { it.userId == expense.paidBy }?.displayName ?: "Alguien"
    ListItem(
        headlineContent = { Text(expense.description) },
        supportingContent = { Text("Pagó $payer · ${expense.date}") },
        trailingContent = { Text(MoneyFormatter.format(expense.amount), fontWeight = FontWeight.SemiBold) },
        modifier = Modifier
            .clickable { onOpenExpense(expense.id.value) }
            .semantics {
                contentDescription =
                    "${expense.description}, ${MoneyFormatter.format(expense.amount)}, pagó $payer"
            },
    )
}

@Composable
private fun MembersTab(members: List<GroupMember>) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(members, key = { it.userId.value }) { member ->
            ListItem(
                headlineContent = { Text(member.displayName.ifBlank { member.userId.value }) },
                supportingContent = {
                    Text(if (member.role == MemberRole.OWNER) "Administrador" else "Miembro")
                },
            )
        }
    }
}

@Composable
private fun SettingsTab(
    content: GroupContent,
    onRotateCode: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Código de invitación", style = MaterialTheme.typography.titleMedium)
        Text(
            content.group.inviteCode,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics {
                contentDescription = "Código de invitación ${content.group.inviteCode}"
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Unite a ${content.group.name} en Cuentas Claras con el código ${content.group.inviteCode}",
                        )
                    }
                    context.startActivity(Intent.createChooser(send, "Compartir código"))
                },
                modifier = Modifier.semantics { contentDescription = "Compartir código" },
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Text("  Compartir")
            }
            if (content.isOwner) {
                TextButton(onClick = onRotateCode) { Text("Nuevo código") }
            }
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onLogout) { Text("Cerrar sesión") }
    }
}

@Composable
private fun CenterLoading(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}
