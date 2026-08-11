package com.cuentasclaras.app.presentation.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuentasclaras.app.presentation.components.ExpenseDateField
import com.cuentasclaras.app.ui.CategoryIcons
import com.cuentasclaras.app.ui.theme.GroupThemed
import com.cuentasclaras.app.util.MoneyFormatter
import com.cuentasclaras.domain.finance.ExpenseLabels
import com.cuentasclaras.domain.finance.InstallmentPlanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorScreen(
    groupId: String,
    expenseId: String?,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: ExpenseEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }

    GroupThemed(themeId = state.themeId) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (expenseId == null) "Nuevo gasto" else "Editar gasto") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                )
            },
        ) { padding ->
            if (state.isLoading) {
                Column(
                    Modifier.padding(padding).fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            var categoryExpanded by remember { mutableStateOf(false) }
            val selectedCategory = state.selectedCategory
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "Elegí una categoría",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Categoría") },
                    leadingIcon = selectedCategory?.let { category ->
                        {
                            Icon(
                                imageVector = CategoryIcons.imageVector(category.icon),
                                contentDescription = null,
                            )
                        }
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                        .semantics { contentDescription = "Categoría del gasto" },
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false },
                ) {
                    if (state.categories.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No hay categorías. Creálas en Configuración.") },
                            onClick = { categoryExpanded = false },
                        )
                    } else {
                        state.categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = CategoryIcons.imageVector(category.icon),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    viewModel.onCategoryChange(category.id)
                                    categoryExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Nota (opcional)") },
                singleLine = true,
                supportingText = {
                    Text("Ej.: factura marzo. Se muestra junto al nombre de la categoría.")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Nota del gasto" },
            )
            OutlinedTextField(
                value = state.amountInput,
                onValueChange = viewModel::onAmountChange,
                label = {
                    Text(
                        if (state.isInstallment && expenseId == null) {
                            "Monto total (${state.currency.code})"
                        } else {
                            "Monto (${state.currency.code})"
                        },
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Monto del gasto" },
            )

            if (expenseId == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "En cuotas",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics { contentDescription = "Gasto en cuotas" },
                    )
                    Switch(
                        checked = state.isInstallment,
                        onCheckedChange = viewModel::onInstallmentEnabledChange,
                    )
                }
                if (state.isInstallment) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = state.installmentStartIndexInput,
                            onValueChange = viewModel::onInstallmentStartIndexChange,
                            label = { Text("Cuota") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "Número de cuota actual" },
                        )
                        OutlinedTextField(
                            value = state.installmentCountInput,
                            onValueChange = viewModel::onInstallmentCountChange,
                            label = { Text("De") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            supportingText = {
                                Text(
                                    "Entre ${InstallmentPlanner.MIN_COUNT} y ${InstallmentPlanner.MAX_COUNT}",
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "Cantidad total de cuotas" },
                        )
                    }
                    state.installmentPreview?.let { preview ->
                        Text(
                            preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "La fecha es la de esta cuota. Se crean solo las cuotas que faltan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            var payerExpanded by remember { mutableStateOf(false) }
            val selectedPayer = state.members.find { it.userId == state.paidBy }
            ExposedDropdownMenuBox(
                expanded = payerExpanded,
                onExpandedChange = { payerExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedPayer?.displayName ?: "Elegí quién pagó",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Quién pagó") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = payerExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                        .semantics { contentDescription = "Quién pagó" },
                )
                ExposedDropdownMenu(
                    expanded = payerExpanded,
                    onDismissRequest = { payerExpanded = false },
                ) {
                    state.members.forEach { member ->
                        DropdownMenuItem(
                            text = { Text(member.displayName) },
                            onClick = {
                                viewModel.onPaidByChange(member.userId)
                                payerExpanded = false
                            },
                        )
                    }
                }
            }

            ExpenseDateField(
                date = state.date,
                onDateChange = viewModel::onDateChange,
            )

            if (state.fromCache) {
                Text(
                    "Sin conexión · mostrando datos guardados",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.isMutationBlocked) {
                Text(
                    "Este período está cerrado. Reabrilo desde el resumen del grupo para guardar cambios.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                text = when {
                    expenseId == null && state.members.size < 2 ->
                        "Necesitás al menos otra persona en el grupo para cargar un gasto. " +
                            "Invitala con el código desde Configuración."
                    state.members.size <= 1 ->
                        "Solo hay un miembro en el grupo. Podés editar este gasto, " +
                            "pero para crear uno nuevo necesitás invitar a alguien."
                    else ->
                        "Se divide en partes iguales entre ${state.members.size} miembros: " +
                            state.members.joinToString { it.displayName }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(8.dp))
            if (state.isSaving) {
                CircularProgressIndicator()
            } else {
                val canCreate = expenseId != null || state.members.size >= 2
                Button(
                    onClick = viewModel::save,
                    enabled = canCreate && !state.isMutationBlocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Guardar gasto" },
                ) {
                    Text("Guardar")
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    groupId: String,
    expenseId: String,
    flashMessage: String? = null,
    onFlashConsumed: () -> Unit = {},
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    viewModel: ExpenseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteSeriesConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }

    LaunchedEffect(flashMessage) {
        val message = flashMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onFlashConsumed()
    }

    GroupThemed(themeId = state.themeId) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Detalle del gasto") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            val expense = state.expense
            if (state.isLoading) {
                Column(Modifier.padding(padding).padding(24.dp)) { CircularProgressIndicator() }
                return@Scaffold
            }
            if (expense == null) {
                Column(Modifier.padding(padding).padding(24.dp)) {
                    Text(state.errorMessage ?: "Gasto no encontrado.")
                    TextButton(onClick = onBack) { Text("Volver") }
                }
                return@Scaffold
            }

            val payer = state.members.find { it.userId == expense.paidBy }?.displayName ?: "Alguien"
            val splitUserIds = expense.splits.map { it.userId }.toSet()
            val memberIds = state.members.map { it.userId }.toSet()
            val missingMembers = state.members.size > 1 && splitUserIds != memberIds
            Column(
                modifier = Modifier.padding(padding).padding(24.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    expense.categoryIcon?.let { icon ->
                        Icon(
                            imageVector = CategoryIcons.imageVector(icon),
                            contentDescription = expense.categoryName,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        ExpenseLabels.title(expense),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                Text(MoneyFormatter.format(expense.amount), style = MaterialTheme.typography.headlineMedium)
                if (expense.isInstallment) {
                    Text(
                        "Cuota ${expense.installmentIndex}/${expense.installmentCount}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text("Pagó: $payer")
                Text("Fecha: ${expense.date}")
                Spacer(Modifier.height(8.dp))
                Text("Reparto", style = MaterialTheme.typography.titleMedium)
                expense.splits.forEach { split ->
                    val name = state.members.find { it.userId == split.userId }?.displayName
                        ?: split.userId.value
                    Text("$name: ${MoneyFormatter.format(split.share)}")
                }
                if (missingMembers) {
                    Text(
                        "Este gasto no incluye a todos los miembros actuales. " +
                            "Editá y guardá de nuevo para redistribuirlo en partes iguales.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.isPeriodClosed) {
                    Text(
                        "Este período está cerrado. Reabrilo desde el resumen del grupo para editar o eliminar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.fromCache) {
                    Text(
                        "Sin conexión · mostrando datos guardados",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))
                if (state.canEdit) {
                    Button(
                        onClick = onEdit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Editar gasto" },
                    ) {
                        Text("Editar")
                    }
                    OutlinedButton(
                        onClick = {
                            if (expense.isInstallment) {
                                showDeleteSeriesConfirm = true
                            } else {
                                showDeleteConfirm = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Eliminar gasto" },
                    ) {
                        Text("Eliminar")
                    }
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Eliminar gasto") },
                text = { Text("¿Seguro que querés eliminar este gasto? Los saldos se recalcularán.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            viewModel.delete()
                        },
                    ) { Text("Eliminar") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
                },
            )
        }

        if (showDeleteSeriesConfirm) {
            val expense = state.expense
            AlertDialog(
                onDismissRequest = { showDeleteSeriesConfirm = false },
                title = { Text("Eliminar cuota") },
                text = {
                    Text(
                        "Este gasto es la cuota ${expense?.installmentIndex}/${expense?.installmentCount}. " +
                            "¿Querés borrar solo esta o toda la serie?",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteSeriesConfirm = false
                            viewModel.delete()
                        },
                    ) { Text("Solo esta") }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                showDeleteSeriesConfirm = false
                                viewModel.deleteSeries()
                            },
                        ) { Text("Toda la serie") }
                        TextButton(onClick = { showDeleteSeriesConfirm = false }) {
                            Text("Cancelar")
                        }
                    }
                },
            )
        }
    }
}
