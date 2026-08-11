package com.cuentasclaras.app.presentation.group

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuentasclaras.app.presentation.components.FullScreenLoading
import com.cuentasclaras.app.presentation.components.FullScreenMessage
import com.cuentasclaras.app.presentation.components.UiState
import com.cuentasclaras.app.util.InviteShare
import com.cuentasclaras.app.util.MoneyFormatter
import com.cuentasclaras.domain.model.Expense
import com.cuentasclaras.domain.model.GroupMember
import com.cuentasclaras.domain.model.MemberBalance
import com.cuentasclaras.domain.model.MemberRole
import com.cuentasclaras.domain.model.SuggestedTransfer
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    groupId: String,
    focusInvite: Boolean = false,
    flashMessage: String? = null,
    onFlashConsumed: () -> Unit = {},
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: GroupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tabIndex by rememberSaveable { mutableIntStateOf(if (focusInvite) 3 else 0) }
    val tabs = listOf("Resumen", "Gastos", "Miembros", "Configuración")
    val snackbarHostState = remember { SnackbarHostState() }
    var showRotateConfirm by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh(showLoading = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(flashMessage) {
        val message = flashMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onFlashConsumed()
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val content = (state as? UiState.Content)?.data
            val canAddExpenses = content != null && content.members.size >= 2
            if (tabIndex == 1 && canAddExpenses) {
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
            UiState.Loading -> FullScreenLoading(Modifier.padding(padding))
            is UiState.Error -> FullScreenMessage(
                title = "No pudimos cargar el grupo",
                body = ui.message,
                actionLabel = "Reintentar",
                onAction = { viewModel.refresh(showLoading = true) },
                isError = true,
                modifier = Modifier.padding(padding),
            )
            UiState.Empty -> FullScreenMessage(
                title = "Sin datos",
                body = "Este grupo todavía no tiene información para mostrar.",
                modifier = Modifier.padding(padding),
            )
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
                            onMarkSettled = viewModel::markSettled,
                            onUndoPayment = viewModel::undoPayment,
                            onGoToInvite = { tabIndex = 3 },
                        )
                        1 -> ExpensesTab(
                            content = ui.data,
                            onOpenExpense = onOpenExpense,
                            onAddExpense = onAddExpense,
                            onGoToInvite = { tabIndex = 3 },
                        )
                        2 -> MembersTab(
                            content = ui.data,
                            onRemoveMember = viewModel::removeMember,
                        )
                        3 -> SettingsTab(
                            content = ui.data,
                            highlightInvite = focusInvite || ui.data.members.size < 2,
                            onCodeCopied = { snackbarHostState.showSnackbar("Código copiado") },
                            onRequestRotateCode = { showRotateConfirm = true },
                            onLogout = onLogout,
                        )
                    }
                }
            }
        }
    }

    if (showRotateConfirm) {
        AlertDialog(
            onDismissRequest = { showRotateConfirm = false },
            title = { Text("Nuevo código") },
            text = {
                Text(
                    "Se invalidará el código actual. Quien todavía no se unió va a necesitar el nuevo.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRotateConfirm = false
                        viewModel.rotateInviteCode()
                    },
                ) {
                    Text("Generar nuevo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRotateConfirm = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun SummaryTab(
    content: GroupContent,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMarkSettled: (SuggestedTransfer) -> Unit,
    onUndoPayment: (com.cuentasclaras.domain.model.SettlementPaymentId) -> Unit,
    onGoToInvite: () -> Unit,
) {
    val periodLabel = content.period.month
        .getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-AR"))
        .replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-AR")) } +
        " ${content.period.year}"
    val needsInvite = content.members.size < 2

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
        if (needsInvite) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Para cargar gastos necesitás al menos otra persona en el grupo.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = onGoToInvite,
                        modifier = Modifier.semantics {
                            contentDescription = "Ir a invitar miembro"
                        },
                    ) {
                        Text("Invitar desde Configuración")
                    }
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
        if (content.expensesMissingMembers > 0) {
            item {
                Text(
                    text = if (content.expensesMissingMembers == 1) {
                        "Hay 1 gasto de este período que no se reparte entre todos los miembros " +
                            "(suele pasar si se cargó antes de que alguien se uniera). " +
                            "Abrilo, editá y guardá de nuevo para redistribuirlo."
                    } else {
                        "Hay ${content.expensesMissingMembers} gastos de este período que no se reparten " +
                            "entre todos los miembros (suele pasar si se cargaron antes de que alguien se uniera). " +
                            "Entrá a cada uno, editá y guardá de nuevo para redistribuirlos."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        items(content.summary.memberBalances) { balance ->
            MemberBalanceCard(
                balance = balance,
                displayName = displayNameFor(balance.userId, content.members),
            )
        }
        if (content.summary.suggestedTransfers.isNotEmpty()) {
            item {
                Text("Para saldar", style = MaterialTheme.typography.titleMedium)
            }
            items(
                content.summary.suggestedTransfers,
                key = { "${it.fromUserId.value}-${it.toUserId.value}-${it.amount.amountMinor}" },
            ) { transfer ->
                TransferRow(
                    transfer = transfer,
                    members = content.members,
                    onMarkSettled = { onMarkSettled(transfer) },
                )
            }
        } else {
            item {
                Text(
                    when {
                        content.summary.totalSpent.isZero() &&
                            content.summary.recordedPayments.isEmpty() ->
                            "No hay gastos en este período."
                        content.summary.recordedPayments.isNotEmpty() ->
                            "No queda nada pendiente por saldar."
                        else -> "Las cuentas están saldadas."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (content.summary.recordedPayments.isNotEmpty()) {
            item {
                Text("Saldados", style = MaterialTheme.typography.titleMedium)
            }
            items(
                content.summary.recordedPayments,
                key = { it.id.value },
            ) { payment ->
                SettledPaymentRow(
                    payment = payment,
                    members = content.members,
                    onUndo = { onUndoPayment(payment.id) },
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
private fun TransferRow(
    transfer: SuggestedTransfer,
    members: List<GroupMember>,
    onMarkSettled: () -> Unit,
) {
    val from = displayNameFor(transfer.fromUserId, members)
    val to = displayNameFor(transfer.toUserId, members)
    val label = "$from debe ${MoneyFormatter.format(transfer.amount)} a $to"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = label },
        )
        TextButton(
            onClick = onMarkSettled,
            modifier = Modifier.semantics {
                contentDescription = "Marcar saldado: $label"
            },
        ) {
            Text("Marcar saldado")
        }
    }
}

@Composable
private fun SettledPaymentRow(
    payment: com.cuentasclaras.domain.model.SettlementPayment,
    members: List<GroupMember>,
    onUndo: () -> Unit,
) {
    val from = displayNameFor(payment.fromUserId, members)
    val to = displayNameFor(payment.toUserId, members)
    val label = "✓ $from pagó ${MoneyFormatter.format(payment.amount)} a $to"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = label },
        )
        TextButton(
            onClick = onUndo,
            modifier = Modifier.semantics { contentDescription = "Deshacer: $label" },
        ) {
            Text("Deshacer")
        }
    }
}

@Composable
private fun ExpensesTab(
    content: GroupContent,
    onOpenExpense: (String) -> Unit,
    onAddExpense: () -> Unit,
    onGoToInvite: () -> Unit,
) {
    val periodExpenses = content.expenses.filter { it.period == content.period }
    val canAddExpenses = content.members.size >= 2
    if (periodExpenses.isEmpty()) {
        if (canAddExpenses) {
            FullScreenMessage(
                title = "No hay gastos en este período",
                body = "Tocá + para registrar el primero. El resumen se actualiza al guardar.",
                actionLabel = "Agregar gasto",
                onAction = onAddExpense,
            )
        } else {
            FullScreenMessage(
                title = "Invitá a alguien para empezar",
                body = "Los gastos se reparten entre los miembros del grupo. " +
                    "Compartí el código de invitación desde Configuración y, cuando se una " +
                    "al menos una persona más, vas a poder cargar gastos.",
                actionLabel = "Ver código de invitación",
                onAction = onGoToInvite,
            )
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        if (!canAddExpenses) {
            item {
                Text(
                    "Para agregar nuevos gastos necesitás al menos otra persona en el grupo. " +
                        "Podés seguir editando gastos existentes.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                TextButton(onClick = onGoToInvite) {
                    Text("Ver código de invitación")
                }
            }
        }
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
    val payer = displayNameFor(expense.paidBy, members)
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
private fun MembersTab(
    content: GroupContent,
    onRemoveMember: (com.cuentasclaras.domain.model.UserId) -> Unit,
) {
    val members = content.members
    if (members.isEmpty()) {
        FullScreenMessage(
            title = "Sin miembros",
            body = "Compartí el código de invitación desde Configuración para sumar personas.",
        )
        return
    }
    var memberToRemove by remember { mutableStateOf<GroupMember?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text(
                "Los gastos previos de alguien eliminado siguen contando en el resumen del período.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(members, key = { it.userId.value }) { member ->
            val canRemove = content.isOwner &&
                member.role == MemberRole.MEMBER &&
                member.userId != content.currentUserId
            ListItem(
                headlineContent = { Text(member.displayName.ifBlank { member.userId.value }) },
                supportingContent = {
                    Text(if (member.role == MemberRole.OWNER) "Administrador" else "Miembro")
                },
                trailingContent = {
                    if (canRemove) {
                        TextButton(
                            onClick = { memberToRemove = member },
                            modifier = Modifier.semantics {
                                contentDescription = "Eliminar a ${member.displayName}"
                            },
                        ) {
                            Text("Eliminar")
                        }
                    }
                },
            )
        }
    }
    val pending = memberToRemove
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Eliminar miembro") },
            text = {
                Text(
                    "¿Eliminar a ${pending.displayName.ifBlank { "este miembro" }} del grupo? " +
                        "Sus gastos previos seguirán en el historial y el resumen.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveMember(pending.userId)
                        memberToRemove = null
                    },
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

private fun displayNameFor(
    userId: com.cuentasclaras.domain.model.UserId,
    members: List<GroupMember>,
): String {
    val member = members.find { it.userId == userId }
    return when {
        member != null -> member.displayName.ifBlank { "Ex-miembro" }
        else -> "Ex-miembro"
    }
}

@Composable
private fun SettingsTab(
    content: GroupContent,
    highlightInvite: Boolean,
    onCodeCopied: suspend () -> Unit,
    onRequestRotateCode: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (highlightInvite) {
            Text(
                "Invitá a alguien para poder cargar gastos.",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
        Text("Código de invitación", style = MaterialTheme.typography.titleMedium)
        Text(
            content.group.inviteCode,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics {
                contentDescription = "Código de invitación ${content.group.inviteCode}"
            },
        )
        Text(
            "Compartilo o copialo. Quien se una tiene que abrir la app y tocar \"Unirme a un grupo\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("Código de invitación", content.group.inviteCode),
                    )
                    scope.launch { onCodeCopied() }
                },
                modifier = Modifier.semantics { contentDescription = "Copiar código" },
            ) {
                Text("Copiar")
            }
            TextButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            InviteShare.shareText(content.group.name, content.group.inviteCode),
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
                TextButton(
                    onClick = onRequestRotateCode,
                    modifier = Modifier.semantics { contentDescription = "Nuevo código" },
                ) {
                    Text("Nuevo código")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onLogout) { Text("Cerrar sesión") }
    }
}
