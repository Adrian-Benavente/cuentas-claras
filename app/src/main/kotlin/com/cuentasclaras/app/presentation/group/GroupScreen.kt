package com.cuentasclaras.app.presentation.group

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuentasclaras.app.presentation.components.FullScreenLoading
import com.cuentasclaras.app.presentation.components.FullScreenMessage
import com.cuentasclaras.app.presentation.components.GroupAvatarImage
import com.cuentasclaras.app.presentation.components.OfflineBanner
import com.cuentasclaras.app.presentation.components.UiState
import com.cuentasclaras.app.ui.CategoryIcons
import com.cuentasclaras.app.ui.theme.GroupThemes
import com.cuentasclaras.app.ui.theme.GroupThemed
import com.cuentasclaras.app.util.DateFormatter
import com.cuentasclaras.app.util.InviteShare
import com.cuentasclaras.app.util.MoneyFormatter
import com.cuentasclaras.domain.finance.CategoryGate
import com.cuentasclaras.domain.finance.ExpenseLabels
import com.cuentasclaras.domain.finance.PeriodGate
import com.cuentasclaras.domain.model.CategoryIcon
import com.cuentasclaras.domain.model.Expense
import com.cuentasclaras.domain.model.ExpenseCategory
import com.cuentasclaras.domain.model.ExpenseCategoryId
import com.cuentasclaras.domain.model.GroupMember
import com.cuentasclaras.domain.model.GroupThemeId
import com.cuentasclaras.domain.model.MemberBalance
import com.cuentasclaras.domain.model.MemberRole
import com.cuentasclaras.domain.model.SuggestedTransfer
import kotlinx.coroutines.launch
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private data class GroupTab(
    val label: String,
    val icon: ImageVector,
)

private val groupTabs = listOf(
    GroupTab("Resumen", Icons.Filled.Assessment),
    GroupTab("Gastos", Icons.Filled.Receipt),
    GroupTab("Miembros", Icons.Filled.Group),
    GroupTab("Configuración", Icons.Filled.Settings),
)

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
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(
        initialPage = if (focusInvite) 3 else 0,
        pageCount = { groupTabs.size },
    )
    val tabScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRotateConfirm by remember { mutableStateOf(false) }
    var showClosePeriodConfirm by remember { mutableStateOf(false) }
    var showReopenPeriodConfirm by remember { mutableStateOf(false) }
    val showOfflineBanner = !isOnline ||
        ((state as? UiState.Content)?.data?.fromCache == true)

    fun goToInviteTab() {
        tabScope.launch { pagerState.animateScrollToPage(3) }
    }

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

    val themeId = (state as? UiState.Content)?.data?.group?.themeId ?: GroupThemeId.FOREST
    GroupThemed(themeId = themeId) {
        Scaffold(
            topBar = {
                val group = (state as? UiState.Content)?.data?.group
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (group != null) {
                                GroupAvatarImage(
                                    avatarUrl = group.avatarUrl,
                                    groupName = group.name,
                                    size = 32.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(group?.name ?: "Grupo")
                        }
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
                val canAddExpenses = content != null &&
                    content.members.size >= 2 &&
                    PeriodGate.showCreateExpenseFab(
                        selectedPeriod = content.period,
                        selectedPeriodClosed = content.isPeriodClosed,
                    )
                if (pagerState.currentPage == 1 && canAddExpenses) {
                    FloatingActionButton(
                        onClick = onAddExpense,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.semantics { contentDescription = "Agregar gasto" },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }
            },
        ) { padding ->
            when (val ui = state) {
                UiState.Loading -> FullScreenLoading(Modifier.padding(padding))
                UiState.Empty -> FullScreenMessage(
                    title = "Sin datos",
                    body = "Este grupo todavía no tiene información para mostrar.",
                    modifier = Modifier.padding(padding),
                )
                is UiState.Error, is UiState.Content -> {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            viewModel.refresh(showLoading = false, showPullIndicator = true)
                        },
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .semantics { contentDescription = "Actualizar grupo" },
                    ) {
                        when (ui) {
                            is UiState.Error -> FullScreenMessage(
                                title = "No pudimos cargar el grupo",
                                body = ui.message,
                                actionLabel = "Reintentar",
                                onAction = { viewModel.refresh(showLoading = true) },
                                isError = true,
                                modifier = Modifier.fillMaxSize(),
                            )
                            is UiState.Content -> {
                                Column(Modifier.fillMaxSize()) {
                                    OfflineBanner(visible = showOfflineBanner)
                                    PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                                        groupTabs.forEachIndexed { index, tab ->
                                            Tab(
                                                selected = pagerState.currentPage == index,
                                                onClick = {
                                                    tabScope.launch {
                                                        pagerState.animateScrollToPage(index)
                                                    }
                                                },
                                                icon = {
                                                    Icon(
                                                        imageVector = tab.icon,
                                                        contentDescription = null,
                                                    )
                                                },
                                                modifier = Modifier.semantics {
                                                    contentDescription = tab.label
                                                },
                                            )
                                        }
                                    }
                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        beyondViewportPageCount = 0,
                                        verticalAlignment = Alignment.Top,
                                    ) { page ->
                                        when (page) {
                                            0 -> SummaryTab(
                                                content = ui.data,
                                                onPrevious = viewModel::previousPeriod,
                                                onNext = viewModel::nextPeriod,
                                                onSelectPeriod = viewModel::setPeriod,
                                                onMarkSettled = viewModel::markSettled,
                                                onUndoPayment = viewModel::undoPayment,
                                                onRequestClosePeriod = {
                                                    showClosePeriodConfirm = true
                                                },
                                                onRequestReopenPeriod = {
                                                    showReopenPeriodConfirm = true
                                                },
                                                onGoToInvite = ::goToInviteTab,
                                            )
                                            1 -> ExpensesTab(
                                                content = ui.data,
                                                onOpenExpense = onOpenExpense,
                                                onAddExpense = onAddExpense,
                                                onGoToInvite = ::goToInviteTab,
                                            )
                                            2 -> MembersTab(
                                                content = ui.data,
                                                onRemoveMember = viewModel::removeMember,
                                            )
                                            3 -> SettingsTab(
                                                content = ui.data,
                                                highlightInvite = focusInvite ||
                                                    ui.data.members.size < 2,
                                                onCodeCopied = {
                                                    snackbarHostState.showSnackbar("Código copiado")
                                                },
                                                onRequestRotateCode = { showRotateConfirm = true },
                                                onPickAvatar = viewModel::setAvatar,
                                                onClearAvatar = viewModel::clearAvatar,
                                                onSetTheme = viewModel::setTheme,
                                                onCreateCategory = viewModel::createCategory,
                                                onUpdateCategory = viewModel::updateCategory,
                                                onDeleteCategory = viewModel::deleteCategory,
                                                onLogout = onLogout,
                                            )
                                        }
                                    }
                                }
                            }
                            else -> Unit
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

        if (showClosePeriodConfirm) {
            AlertDialog(
                onDismissRequest = { showClosePeriodConfirm = false },
                title = { Text("Cerrar período") },
                text = {
                    Text(
                        "No se van a poder cargar, editar ni eliminar gastos de este mes, " +
                            "ni marcar saldados. Podés reabrirlo después.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClosePeriodConfirm = false
                            viewModel.closePeriod()
                        },
                    ) {
                        Text("Cerrar período")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClosePeriodConfirm = false }) {
                        Text("Cancelar")
                    }
                },
            )
        }

        if (showReopenPeriodConfirm) {
            AlertDialog(
                onDismissRequest = { showReopenPeriodConfirm = false },
                title = { Text("Reabrir período") },
                text = {
                    Text("Se van a poder volver a editar gastos y saldos de este mes.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showReopenPeriodConfirm = false
                            viewModel.reopenPeriod()
                        },
                    ) {
                        Text("Reabrir")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReopenPeriodConfirm = false }) {
                        Text("Cancelar")
                    }
                },
            )
        }
    }
}

@Composable
private fun SummaryTab(
    content: GroupContent,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectPeriod: (YearMonth) -> Unit,
    onMarkSettled: (SuggestedTransfer) -> Unit,
    onUndoPayment: (com.cuentasclaras.domain.model.SettlementPaymentId) -> Unit,
    onRequestClosePeriod: () -> Unit,
    onRequestReopenPeriod: () -> Unit,
    onGoToInvite: () -> Unit,
) {
    val periodLabel = content.period.month
        .getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-AR"))
        .replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-AR")) } +
        " ${content.period.year}"
    val needsInvite = content.members.size < 2
    val periodClosed = content.isPeriodClosed
    var showPeriodPicker by remember { mutableStateOf(false) }

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
                Text(
                    text = periodLabel.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .clickable { showPeriodPicker = true }
                        .semantics { contentDescription = "Elegir período" },
                )
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.semantics { contentDescription = "Mes siguiente" },
                ) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            if (periodClosed) "Cerrado" else "Abierto",
                        )
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (periodClosed) {
                            "Período cerrado"
                        } else {
                            "Período abierto"
                        }
                    },
                )
                if (content.isOwner) {
                    if (periodClosed) {
                        OutlinedButton(
                            onClick = onRequestReopenPeriod,
                            modifier = Modifier.semantics {
                                contentDescription = "Reabrir período"
                            },
                        ) {
                            Text("Reabrir período")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onRequestClosePeriod,
                            modifier = Modifier.semantics {
                                contentDescription = "Cerrar período"
                            },
                        ) {
                            Text("Cerrar período")
                        }
                    }
                }
            }
        }
        if (periodClosed) {
            item {
                Text(
                    "Este período está cerrado. No se pueden editar gastos ni marcar saldados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
        if (content.summary.memberBalances.isNotEmpty()) {
            item {
                MemberBalanceSection(
                    balances = content.summary.memberBalances,
                    members = content.members,
                )
            }
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
                    settlementsEnabled = !periodClosed,
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
                    undoEnabled = !periodClosed,
                    onUndo = { onUndoPayment(payment.id) },
                )
            }
        }
    }

    if (showPeriodPicker) {
        PeriodMonthPickerDialog(
            selectedPeriod = content.period,
            onDismiss = { showPeriodPicker = false },
            onConfirm = { period ->
                showPeriodPicker = false
                onSelectPeriod(period)
            },
        )
    }
}

@Composable
private fun PeriodMonthPickerDialog(
    selectedPeriod: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit,
) {
    var displayedYear by remember { mutableIntStateOf(selectedPeriod.year) }
    val locale = Locale.forLanguageTag("es-AR")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir período") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { displayedYear -= 1 },
                        modifier = Modifier.semantics { contentDescription = "Año anterior" },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null)
                    }
                    Text(
                        text = displayedYear.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(
                        onClick = { displayedYear += 1 },
                        modifier = Modifier.semantics { contentDescription = "Año siguiente" },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in 0 until 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            for (col in 1..3) {
                                val monthNumber = row * 3 + col
                                val period = YearMonth.of(displayedYear, monthNumber)
                                val selected = period == selectedPeriod
                                val label = Month.of(monthNumber)
                                    .getDisplayName(TextStyle.SHORT, locale)
                                    .replaceFirstChar { it.titlecase(locale) }
                                FilterChip(
                                    selected = selected,
                                    onClick = { onConfirm(period) },
                                    label = { Text(label) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics {
                                            contentDescription = if (selected) {
                                                "$label $displayedYear, seleccionado"
                                            } else {
                                                "$label $displayedYear"
                                            }
                                        },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun MemberBalanceSection(
    balances: List<MemberBalance>,
    members: List<GroupMember>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Por persona",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        balances.forEachIndexed { index, balance ->
            MemberBalanceBlock(
                balance = balance,
                displayName = displayNameFor(balance.userId, members),
            )
            if (index < balances.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun MemberBalanceBlock(balance: MemberBalance, displayName: String) {
    val balanceLabel = when {
        balance.balance.isPositive() ->
            "Debe recibir ${MoneyFormatter.format(balance.balance.abs())}"
        balance.balance.isNegative() ->
            "Debe pagar ${MoneyFormatter.format(balance.balance.abs())}"
        else -> "Saldado"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .semantics {
                contentDescription =
                    "$displayName. Pagó ${MoneyFormatter.format(balance.amountPaid)}. " +
                        "Le corresponde ${MoneyFormatter.format(balance.amountOwed)}. $balanceLabel"
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(displayName, style = MaterialTheme.typography.titleMedium)
        Text(
            text = balanceLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = when {
                balance.balance.isPositive() -> MaterialTheme.colorScheme.primary
                balance.balance.isNegative() -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Pagó",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    MoneyFormatter.format(balance.amountPaid),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Le toca",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
                Text(
                    MoneyFormatter.format(balance.amountOwed),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun TransferRow(
    transfer: SuggestedTransfer,
    members: List<GroupMember>,
    settlementsEnabled: Boolean,
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
            enabled = settlementsEnabled,
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
    undoEnabled: Boolean,
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
            enabled = undoEnabled,
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
    val canAddExpenses = content.members.size >= 2 &&
        PeriodGate.showCreateExpenseFab(
            selectedPeriod = content.period,
            selectedPeriodClosed = content.isPeriodClosed,
        )
    val periodClosed = content.isPeriodClosed

    if (periodExpenses.isEmpty()) {
        if (canAddExpenses) {
            FullScreenMessage(
                title = "No hay gastos en este período",
                body = "Tocá + para registrar el primero. El resumen se actualiza al guardar.",
                actionLabel = "Agregar gasto",
                onAction = onAddExpense,
            )
        } else if (periodClosed) {
            FullScreenMessage(
                title = "Período cerrado",
                body = "No hay gastos en este mes y el período está cerrado. " +
                    "El administrador puede reabrirlo desde Resumen.",
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
        if (periodClosed) {
            item {
                Text(
                    "Este período está cerrado. Podés ver los gastos, pero no editarlos ni agregar nuevos de este mes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        if (!canAddExpenses && !periodClosed) {
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
    val title = ExpenseLabels.title(expense)
    ListItem(
        leadingContent = {
            expense.categoryIcon?.let { icon ->
                Icon(
                    imageVector = CategoryIcons.imageVector(icon),
                    contentDescription = expense.categoryName,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        headlineContent = { Text(title) },
        supportingContent = { Text("Pagó $payer · ${DateFormatter.format(expense.date)}") },
        trailingContent = { Text(MoneyFormatter.format(expense.amount), fontWeight = FontWeight.SemiBold) },
        modifier = Modifier
            .clickable { onOpenExpense(expense.id.value) }
            .semantics {
                contentDescription =
                    "$title, ${MoneyFormatter.format(expense.amount)}, pagó $payer"
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsTab(
    content: GroupContent,
    highlightInvite: Boolean,
    onCodeCopied: suspend () -> Unit,
    onRequestRotateCode: () -> Unit,
    onPickAvatar: (Uri) -> Unit,
    onClearAvatar: () -> Unit,
    onSetTheme: (GroupThemeId) -> Unit,
    onCreateCategory: (String, CategoryIcon) -> Unit,
    onUpdateCategory: (ExpenseCategoryId, String, CategoryIcon) -> Unit,
    onDeleteCategory: (ExpenseCategoryId) -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var categoryEditor by remember { mutableStateOf<ExpenseCategory?>(null) }
    var showCreateCategory by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<ExpenseCategory?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onPickAvatar(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (highlightInvite) {
            Text(
                "Invitá a alguien para poder cargar gastos.",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }

        Text("Categorías de gastos", style = MaterialTheme.typography.titleMedium)
        Text(
            "Se reutilizan al cargar gastos. Podés editar o eliminar las que creaste. El administrador puede editar o eliminar cualquiera.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (content.categories.isEmpty()) {
            Text(
                "Todavía no hay categorías. Creá la primera para empezar a cargar gastos.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            content.categories.forEach { category ->
                val canEdit = CategoryGate.canManage(
                    createdBy = category.createdBy,
                    currentUserId = content.currentUserId,
                    isOwner = content.isOwner,
                    isUncategorized = category.isUncategorized,
                )
                ListItem(
                    leadingContent = {
                        Icon(
                            imageVector = CategoryIcons.imageVector(category.icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = { Text(category.name) },
                    trailingContent = {
                        if (canEdit) {
                            Row {
                                TextButton(onClick = { categoryEditor = category }) {
                                    Text("Editar")
                                }
                                TextButton(onClick = { categoryToDelete = category }) {
                                    Text("Eliminar")
                                }
                            }
                        }
                    },
                )
            }
        }
        OutlinedButton(
            onClick = { showCreateCategory = true },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Nueva categoría" },
        ) {
            Text("Nueva categoría")
        }

        Text("Foto del grupo", style = MaterialTheme.typography.titleMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GroupAvatarImage(
                avatarUrl = content.group.avatarUrl,
                groupName = content.group.name,
                size = 72.dp,
            )
            if (content.isUpdatingAvatar) {
                CircularProgressIndicator(modifier = Modifier.width(24.dp))
            }
        }
        if (content.isOwner) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    enabled = !content.isUpdatingAvatar,
                    modifier = Modifier.semantics { contentDescription = "Cambiar foto del grupo" },
                ) {
                    Text(if (content.group.avatarUrl.isNullOrBlank()) "Agregar foto" else "Cambiar foto")
                }
                if (!content.group.avatarUrl.isNullOrBlank()) {
                    TextButton(
                        onClick = onClearAvatar,
                        enabled = !content.isUpdatingAvatar,
                        modifier = Modifier.semantics { contentDescription = "Quitar foto del grupo" },
                    ) {
                        Text("Quitar foto")
                    }
                }
            }
            Text(
                "Se recorta al centro en cuadrado y se muestra en círculo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Tema del grupo", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GroupThemes.all.forEach { palette ->
                    val selected = content.group.themeId == palette.id
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(palette.accent())
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape,
                            )
                            .clickable { onSetTheme(palette.id) }
                            .semantics {
                                contentDescription = if (selected) {
                                    "Tema ${palette.label}, seleccionado"
                                } else {
                                    "Tema ${palette.label}"
                                }
                            },
                    )
                }
            }
            Text(
                GroupThemes.of(content.group.themeId).label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

    if (showCreateCategory) {
        CategoryEditorDialog(
            title = "Nueva categoría",
            initialName = "",
            initialIcon = CategoryIcon.CATEGORY,
            onDismiss = { showCreateCategory = false },
            onConfirm = { name, icon ->
                showCreateCategory = false
                onCreateCategory(name, icon)
            },
        )
    }
    categoryEditor?.let { editing ->
        CategoryEditorDialog(
            title = "Editar categoría",
            initialName = editing.name,
            initialIcon = editing.icon,
            onDismiss = { categoryEditor = null },
            onConfirm = { name, icon ->
                categoryEditor = null
                onUpdateCategory(editing.id, name, icon)
            },
        )
    }
    categoryToDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Eliminar categoría") },
            text = {
                Text(
                    "¿Eliminar \"${pending.name}\"? Los gastos que la usen pasarán a Sin categoría.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCategory(pending.id)
                        categoryToDelete = null
                    },
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorDialog(
    title: String,
    initialName: String,
    initialIcon: CategoryIcon,
    onDismiss: () -> Unit,
    onConfirm: (String, CategoryIcon) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var icon by remember(initialIcon) { mutableStateOf(initialIcon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Nombre de categoría" },
                )
                Text("Ícono", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CategoryIcon.entries.forEach { option ->
                        val selected = option == icon
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                )
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                )
                                .clickable { icon = option }
                                .semantics { contentDescription = "Ícono ${option.value}" },
                        ) {
                            Icon(
                                imageVector = CategoryIcons.imageVector(option),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), icon) },
                enabled = name.trim().isNotEmpty(),
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
